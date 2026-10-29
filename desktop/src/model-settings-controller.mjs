// Transient UI ordering only. SQLite and the private credential owner remain
// authoritative; this module never retains Key values or retries a write.
export function createModelSettingsController({ invoke, currentOwner, render = () => {},
  trackWrite = operation => operation, native = true, updateStatus = () => {} }) {
  const runtimes = new WeakMap();
  const runtime = owner => {
    if (!runtimes.has(owner)) runtimes.set(owner, { tail: Promise.resolve(), read: 0, intents: new Map() });
    return runtimes.get(owner);
  };
  const service = (owner, providerId) => owner.modelServiceSettings.find(item => item.providerId === providerId);
  const editor = (owner, providerId) => currentOwner() === owner && owner.pane === 'settings'
    && owner.settingsSection === 'model-configuration' && owner.modelProviderId === providerId;
  const notify = owner => { if (currentOwner() === owner) render(); };
  function feedback(owner, providerId, notice = '', error = '') {
    owner.modelProviderFeedback ||= {};
    owner.modelProviderFeedback[providerId] = { notice, error };
    if (editor(owner, providerId)) {
      owner.modelSettingsNotice = notice;
      owner.modelSettingsError = error;
    }
  }
  function projectDraft(owner) {
    const selected = service(owner, owner.modelProviderId);
    if (!selected || owner.modelServiceDraft?.providerId !== selected.providerId) return;
    const next = { ...owner.modelServiceDraft, enabled: selected.enabled,
      presetId: selected.presetId, revision: selected.revision };
    for (const field of ['enabled', 'presetId']) {
      const intent = runtime(owner).intents.get(`${selected.providerId}:${field}`);
      if (intent) next[field] = intent.value;
    }
    // An OCR probe target is not a persisted ordinary-chat preset.
    if (owner.modelServiceDraft.presetId === 'GLM_OCR' && selected.providerId === 'ZHIPU') next.presetId = 'GLM_OCR';
    owner.modelServiceDraft = next;
  }
  function replace(owner, next, { project = true } = {}) {
    const previous = service(owner, next.providerId);
    if (!previous || Number(next.revision) < Number(previous.revision)) return;
    owner.modelServiceSettings = owner.modelServiceSettings.map(item => item.providerId === next.providerId ? next : item);
    runtime(owner).read += 1; // Any read begun before this receipt is stale.
    if (project) projectDraft(owner);
    updateStatus(owner);
  }
  function enqueue(owner, operation) {
    const value = runtime(owner);
    const pending = value.tail.then(operation);
    value.tail = pending.catch(() => {});
    return pending;
  }
  async function read(owner, { preserveUnsavedDraft = true } = {}) {
    if (!native) return false;
    const value = runtime(owner);
    const generation = ++value.read;
    const records = await invoke(owner, 'read_desktop_model_service_settings', {});
    if (generation !== value.read || currentOwner() !== owner) return false;
    if (!Array.isArray(records) || !records.length) throw new Error('模型配置回读结果无效；原编辑内容已保留。');
    const previous = service(owner, owner.modelProviderId);
    const draft = owner.modelServiceDraft;
    const untrackedEdit = preserveUnsavedDraft && previous && draft?.providerId === previous.providerId
      && ['enabled', 'presetId'].some(field => !(field === 'presetId' && previous.providerId === 'ZHIPU' && draft.presetId === 'GLM_OCR')
        && draft[field] !== previous[field] && !value.intents.has(`${previous.providerId}:${field}`));
    owner.modelServiceSettings = records.map(next => {
      const previous = service(owner, next.providerId);
      return previous && Number(previous.revision) > Number(next.revision) ? previous : next;
    });
    if (!service(owner, owner.modelProviderId)) owner.modelProviderId = records[0].providerId;
    if (!preserveUnsavedDraft || owner.modelServiceDraft?.providerId !== owner.modelProviderId) {
      const selected = service(owner, owner.modelProviderId);
      owner.modelServiceDraft = { providerId: selected.providerId, enabled: selected.enabled,
        presetId: selected.presetId, revision: selected.revision };
    }
    if (untrackedEdit) {
      // Preserve edits inherited from the previous UI and changes made during
      // this read. A stale draft still cannot overwrite a newer native record.
      if (draft.revision !== service(owner, owner.modelProviderId)?.revision) {
        feedback(owner, owner.modelProviderId, '', '模型配置已在另一数据区更新；请重新选择服务商并确认设置后保存。');
      }
    } else projectDraft(owner);
    updateStatus(owner);
    return true;
  }
  function writeChoice(owner, field, desired) {
    const providerId = owner.modelProviderId;
    if (!service(owner, providerId)) return Promise.resolve(false);
    if (!native) {
      feedback(owner, providerId, '', 'Web 预览不会保存模型配置；请在 Desktop 应用中操作。');
      notify(owner);
      return Promise.resolve(false);
    }
    const value = runtime(owner);
    const key = `${providerId}:${field}`;
    const intent = { value: desired };
    // An explicit ordinary choice ends the temporary OCR target. Unrelated
    // enabled writes and older ordinary receipts must keep that newer target.
    if (field === 'presetId' && providerId === 'ZHIPU'
      && owner.modelServiceDraft?.providerId === providerId && owner.modelServiceDraft.presetId === 'GLM_OCR') {
      owner.modelServiceDraft = { ...owner.modelServiceDraft, presetId: desired };
    }
    value.intents.set(key, intent);
    value.read += 1;
    projectDraft(owner);
    feedback(owner, providerId);
    notify(owner);
    const pending = enqueue(owner, async () => {
        const command = field === 'enabled' ? 'set_desktop_model_service_enabled' : 'set_desktop_model_service_preset';
        const args = field === 'enabled' ? { providerId, enabled: Boolean(desired) } : { providerId, presetId: desired };
        const saved = await invoke(owner, command, { args });
        if (saved?.providerId !== providerId) throw new Error('模型配置回执身份不一致；请重新读取设置。');
        const latest = value.intents.get(key) === intent;
        if (latest) value.intents.delete(key);
        replace(owner, saved);
        if (latest) feedback(owner, providerId, field === 'enabled' ? (saved.enabled ? '已启用。' : '已停用。') : '型号已保存。');
        return true;
    });
    // Register even a queued write immediately, so a close cannot race past it.
    const settled = trackWrite(pending, `model-settings:${providerId}:${field}`).catch(async error => {
        if (value.intents.get(key) === intent) value.intents.delete(key);
        projectDraft(owner);
        try { await read(owner); } catch { /* keep the committed snapshot and typed Key */ }
        projectDraft(owner);
        feedback(owner, providerId, '', `设置未保存：${String(error)}`);
        return false;
    }).finally(() => notify(owner));
    value.tail = settled.then(() => {});
    return settled;
  }
  return { enqueue, read, replace, feedback,
    setEnabled(owner, desired) { return writeChoice(owner, 'enabled', Boolean(desired)); },
    setPreset(owner, presetId) {
      if (presetId === 'GLM_OCR' && owner.modelProviderId === 'ZHIPU') {
        runtime(owner).read += 1; // A read begun before this explicit target is stale.
        owner.modelServiceDraft = { ...owner.modelServiceDraft, presetId };
        feedback(owner, 'ZHIPU'); notify(owner); return Promise.resolve(true);
      }
      return writeChoice(owner, 'presetId', presetId);
    },
  };
}
