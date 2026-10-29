// Rust owns durable webpage tasks; timers only read their persisted state.
export function createWebKnowledgeController({ getState, native, invoke, render, refresh, escape, document, setTimer = setTimeout, clearTimer = clearTimeout }) {
  const timers = new WeakMap(), running = new Set();
  const processing = task => ['FETCHING', 'EXTRACTING'].includes(task?.status);
  const owns = (owner, workspaceId) => getState() === owner && owner.current?.summary?.id === workspaceId;
  const selected = (owner, workspaceId, id) => owns(owner, workspaceId) && owner.webKnowledgeTask?.id === id;
  const labels = { QUEUED: '等待明确抓取', FETCHING: '正在抓取', EXTRACTING: '正在提取文字', AWAITING_CONFIRMATION: '等待逐条确认', PARTIALLY_COMPLETED: '部分已处理', COMPLETED: '已处理', FAILED: '提取失败', CANCELLED: '已取消' };
  function stop(owner) { const timer = timers.get(owner); if (timer !== undefined) clearTimer(timer); timers.delete(owner); }
  function watch(owner) {
    stop(owner);
    const workspaceId = owner.current?.summary?.id, id = owner.webKnowledgeTask?.id;
    if (['CANCELLED','COMPLETED'].includes(owner.webKnowledgeTask?.status)) return;
    if (!native || !workspaceId || !id || !owns(owner, workspaceId) || !(processing(owner.webKnowledgeTask) || owner.webKnowledgeStarting === id)) return;
    timers.set(owner, setTimer(async () => {
      timers.delete(owner);
      const generation = owner.webKnowledgeReadGeneration || 0;
      try {
        const task = await invoke('read_desktop_web_knowledge', { workspaceId, taskId: id }, owner);
        if (!selected(owner, workspaceId, id) || generation !== (owner.webKnowledgeReadGeneration || 0)) return;
        if (owner.webKnowledgeTask.generation > task.generation || (owner.webKnowledgeTask.status === 'CANCELLED' && task.status !== 'CANCELLED')) return;
        owner.webKnowledgeTask = task;
        if (owner.pane === 'knowledge') render();
        watch(owner);
      } catch (error) {
        if (selected(owner, workspaceId, id) && error?.code !== 'STALE_DATA_AREA') { owner.error = '网页 进度暂不可读，请重新打开知识页查看保留的任务。'; render(); }
      }
    }, 500));
  }
  async function load(owner = getState()) {
    const workspaceId = owner.current?.summary?.id;
    if (!native || !workspaceId) return;
    const generation = owner.webKnowledgeReadGeneration = (owner.webKnowledgeReadGeneration || 0) + 1;
    const [task, page] = await Promise.all([invoke('read_latest_desktop_web_knowledge', { workspaceId }, owner), invoke('list_desktop_web_knowledge_tasks', { workspaceId, offset: 0 }, owner)]);
    if (!owns(owner, workspaceId) || owner.webKnowledgeReadGeneration !== generation) return;
    owner.webKnowledgeTask = task; owner.webKnowledgeTasks = page.tasks; owner.webKnowledgeHasMore = page.hasMore;
    if (owner.pane === 'knowledge') render();
    watch(owner);
  }
  function card(task) {
    if (!task) return '';
    const owner = getState(), ready = ['AWAITING_CONFIRMATION', 'PARTIALLY_COMPLETED'].includes(task.status);
    const progress = !['CANCELLED','COMPLETED'].includes(task.status) && (processing(task) || owner.webKnowledgeStarting === task.id) ? `<p role="status">${task.status === 'EXTRACTING' ? '私有副本已保存，正在提取正文…' : '正在读取网页，请稍候…'}</p>` : '';
    return `<section class="memory-rail web-knowledge-task" data-task-id="${escape(task.id)}"><div class="section-head"><h2>网页文字快照 · ${escape(labels[task.status] || '待核对')}</h2></div><label>网页任务<select data-action="select-web-knowledge-task">${(owner.webKnowledgeTasks || []).map(item => `<option value="${escape(item.id)}" ${item.id === task.id ? 'selected' : ''}>${escape(item.requestedUrl)} · ${escape(labels[item.status] || item.status)}</option>`).join('')}</select></label>${owner.webKnowledgeHasMore ? '<button data-action="more-web-knowledge-tasks">读取更多网页任务</button>' : ''}<p>${escape(task.requestedUrl)} · 仅保存逐条明确确认的正文。</p>${progress}${task.safeErrorCode ? '<p class="security-note">未能安全完成网页快照，任务和已有来源已保留。重新抓取需要再次输入完整 HTTPS 地址。</p>' : ''}<ul>${task.items.map(item => `<li><span>条目 ${item.ordinal + 1} · ${escape(item.candidate.title)} · ${escape({ PENDING_CONFIRMATION: '待确认', CONFIRMED: '已保存', SKIPPED: '已跳过' }[item.status] || item.status)}</span>${ready && item.status === 'PENDING_CONFIRMATION' ? `<button data-action="review-web-knowledge" data-ordinal="${item.ordinal}">审阅并保存</button><button data-action="skip-web-knowledge" data-ordinal="${item.ordinal}">跳过</button>` : ''}</li>`).join('')}</ul><div class="item-actions">${['FAILED','QUEUED'].includes(task.status) && owner.webKnowledgeStarting !== task.id ? '<button data-action="retry-web-knowledge">重新输入地址并确认抓取</button>' : ''}${!['CANCELLED', 'COMPLETED'].includes(task.status) ? '<button data-action="cancel-web-knowledge">取消此任务</button>' : ''}</div></section>`;
  }

  async function run(owner, workspaceId, taskId, rawUrl, retry) {
    const key = `${owner.dataArea}:${workspaceId}:${taskId}`;
    if (running.has(key)) return;
    running.add(key);
    try {
      const pending = retry ? invoke('retry_desktop_web_knowledge', { workspaceId, taskId, rawUrl }, owner) : invoke('process_desktop_web_knowledge', { workspaceId, taskId, rawUrl }, owner);
      // Polling also starts on explicit retry; failed tasks otherwise stay inert on load.
      if (selected(owner, workspaceId, taskId)) {
        owner.webKnowledgeStarting = taskId;
        watch(owner); render();
      }
      const task = await pending;
      if (!selected(owner, workspaceId, taskId) || owner.webKnowledgeTask.generation > task.generation) return;
      if (owner.webKnowledgeTask?.status === 'CANCELLED' && task.status !== 'CANCELLED') return;
      owner.webKnowledgeTask = task; stop(owner);
      owner.status = task.status === 'FAILED' ? '网页快照未完成；任务和已有私有副本已保留。' : task.status === 'CANCELLED' ? '已取消 网页 提取；已有确认不受影响。' : '网页文字已提取，请逐条审阅后保存。';
      render();
    } catch (error) {
      if (selected(owner, workspaceId, taskId) && error?.code !== 'STALE_DATA_AREA') { stop(owner); owner.error = '网页 处理未完成，请重新读取任务状态后再操作。'; render(); }
    } finally { running.delete(key); if (owner.webKnowledgeStarting === taskId) owner.webKnowledgeStarting = null; if (selected(owner, workspaceId, taskId)) render(); }
  }
  async function cancelCurrent(owner, workspaceId, id) {
    for (let attempt=0;attempt<2;attempt++) {
      const current=await invoke('read_desktop_web_knowledge',{workspaceId,taskId:id},owner);
      if (!selected(owner,workspaceId,id)) throw new Error('TASK_CHANGED');
      if (['CANCELLED','COMPLETED'].includes(current.status)) return current;
      try {return await invoke('cancel_desktop_web_knowledge',{workspaceId,taskId:id,generation:current.generation},owner);}
      catch(error) {if (attempt || !String(error).includes('WEB_TASK_TERMINAL_OR_STALE')) throw error;}
    }
  }
  async function handle(event) {
    const target = event.target.closest?.('[data-action]'), action = target?.dataset.action;
    if (action === 'select-web-knowledge-task' && event.type === 'click') return;
    if (!['select-web-knowledge', 'fetch-web-knowledge', 'select-web-knowledge-task', 'more-web-knowledge-tasks', 'review-web-knowledge', 'confirm-web-knowledge', 'skip-web-knowledge', 'cancel-web-knowledge', 'retry-web-knowledge'].includes(action)) return;
    event.preventDefault(); event.stopImmediatePropagation();
    const owner = getState(), workspaceId = owner.current?.summary?.id, dialog = owner.dialog;
    let startingTaskId = null, urlSubmission = null;
    if (!native || !workspaceId) { owner.error = '请在本机应用中选择当前工作区后导入。'; render(); return; }
    try {
      if (action === 'select-web-knowledge') {
        owner.dialog = { kind: 'web-knowledge-url', workspaceId, retry: false, draft: {} };
      } else if (action === 'fetch-web-knowledge') {
        if (dialog?.kind !== 'web-knowledge-url' || dialog.workspaceId !== workspaceId) throw new Error('DIALOG_CHANGED');
        if (dialog.submitting) return;
        dialog.submitting = true; urlSubmission = dialog;
        const rawUrl = document.querySelector('#web-knowledge-url')?.value?.trim() || '';
        if (!rawUrl) throw new Error('URL_REQUIRED');
        let task;
        if (dialog.taskId) {
          task = owner.webKnowledgeTask;
          if (!task || task.id !== dialog.taskId || task.generation !== dialog.generation || !['FAILED','QUEUED'].includes(task.status)) throw new Error('TASK_CHANGED');
        } else {
          task = await invoke('queue_desktop_web_knowledge', { workspaceId, rawUrl }, owner);
          if (!owns(owner, workspaceId) || owner.dialog !== dialog) return;
          owner.webKnowledgeTasks = [{ id:task.id, requestedUrl:task.requestedUrl, status:task.status, generation:task.generation }, ...(owner.webKnowledgeTasks || [])];
        }
        owner.webKnowledgeReadGeneration = (owner.webKnowledgeReadGeneration || 0) + 1;
        owner.webKnowledgeTask = task; owner.dialog = null; owner.pane = 'knowledge';
        void run(owner,workspaceId,task.id,rawUrl,dialog.retry);
      } else if (action === 'select-web-knowledge-task') {
        stop(owner);
        const generation = owner.webKnowledgeReadGeneration = (owner.webKnowledgeReadGeneration || 0) + 1;
        const task = await invoke('read_desktop_web_knowledge', { workspaceId, taskId: target.value }, owner);
        if (!owns(owner, workspaceId) || generation !== owner.webKnowledgeReadGeneration) return;
        owner.webKnowledgeTask = task; watch(owner);
      } else if (action === 'more-web-knowledge-tasks') {
        const page = await invoke('list_desktop_web_knowledge_tasks', { workspaceId, offset: (owner.webKnowledgeTasks || []).length }, owner);
        if (!owns(owner, workspaceId)) return;
        owner.webKnowledgeTasks.push(...page.tasks); owner.webKnowledgeHasMore = page.hasMore;
      } else {
        const task = owner.webKnowledgeTask; startingTaskId = task?.id;
        if (!task || task.workspaceId !== workspaceId || target.closest?.('[data-task-id]')?.dataset.taskId !== task.id) throw new Error('TASK_CHANGED');
        const ordinal = Number(target.dataset.ordinal ?? dialog?.ordinal);
        if (action === 'review-web-knowledge') {
          const item = task.items.find(item => item.ordinal === ordinal && item.status === 'PENDING_CONFIRMATION');
          if (!item || !['AWAITING_CONFIRMATION', 'PARTIALLY_COMPLETED'].includes(task.status)) throw new Error('ITEM_CHANGED');
          owner.dialog = { kind: 'web-knowledge-review', workspaceId, taskId: task.id, generation: task.generation, ordinal, requestedUrl:task.requestedUrl, item: item.candidate, draft: {} };
        } else if (action === 'retry-web-knowledge') {
          if (!['FAILED','QUEUED'].includes(task.status)) throw new Error('NOT_FAILED');
          owner.dialog = {kind:'web-knowledge-url',workspaceId,taskId:task.id,generation:task.generation,retry:task.status === 'FAILED',draft:{}};
        } else {
          let updated;
          if (action === 'confirm-web-knowledge') {
            if (dialog?.kind !== 'web-knowledge-review' || dialog.workspaceId !== workspaceId || dialog.taskId !== task.id || dialog.generation !== task.generation) throw new Error('GENERATION_CHANGED');
            const candidate = { title: document.querySelector('#web-knowledge-title')?.value || '', body: document.querySelector('#web-knowledge-body')?.value || '', tags: (document.querySelector('#web-knowledge-tags')?.value || '').split(',').map(v => v.trim()).filter(Boolean) };
            const projectId = document.querySelector('#web-knowledge-project')?.value || null;
            updated = await invoke('confirm_desktop_web_knowledge_item', { args: { workspaceId, taskId: task.id, generation: task.generation, ordinal, candidate, projectId } }, owner);
          } else {
            updated = action === 'skip-web-knowledge' ? await invoke('skip_desktop_web_knowledge_item', { workspaceId, taskId: task.id, generation: task.generation, ordinal }, owner) : await cancelCurrent(owner, workspaceId, task.id);
          }
          if (!selected(owner, workspaceId, task.id) || (action !== 'cancel-web-knowledge' && owner.webKnowledgeTask.generation !== task.generation)) return;
          owner.webKnowledgeTask = updated;
          if (owner.dialog === dialog && dialog?.kind === 'web-knowledge-review') owner.dialog = null;
          if (action === 'confirm-web-knowledge') { owner.status = '已保存为本机知识，网页私有副本与来源回执保留。'; await refresh(); if (!owns(owner, workspaceId)) return; }
          watch(owner);
        }
      }
      owner.error = ''; if (owns(owner, workspaceId)) render();
    } catch (error) {
      if (!owns(owner, workspaceId) || (startingTaskId && owner.webKnowledgeTask?.id !== startingTaskId) || error?.code === 'STALE_DATA_AREA') return;
      owner.error = '网页操作未完成，请检查 HTTPS 地址、当前任务或正文；不会自动重新抓取。'; render();
    } finally { if (urlSubmission) urlSubmission.submitting = false; }
  }
  return { load, card, handle, stop };
}
