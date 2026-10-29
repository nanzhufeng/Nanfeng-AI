// Durable PDF tasks are owned by Rust. Timers only read actual persisted page counts.
export function createPdfKnowledgeController({ getState, native, invoke, dialogInvoke, render, refresh, escape, document, setTimer = setTimeout, clearTimer = clearTimeout }) {
  const timers = new WeakMap(), running = new Set();
  const processing = task => ['PRIVATE_COPIED', 'PREFLIGHT', 'EXTRACTING'].includes(task?.status);
  const owns = (owner, workspaceId) => getState() === owner && owner.current?.summary?.id === workspaceId;
  const selected = (owner, workspaceId, id) => owns(owner, workspaceId) && owner.pdfKnowledgeTask?.id === id;
  const labels = { SELECTED: '正在复制', PRIVATE_COPIED: '私有副本已保存', PREFLIGHT: '正在检查', EXTRACTING: '正在提取文字', AWAITING_CONFIRMATION: '等待逐页确认', PARTIALLY_COMPLETED: '部分已处理', COMPLETED: '已处理', FAILED: '提取失败', CANCELLED: '已取消' };
  function stop(owner) { const timer = timers.get(owner); if (timer !== undefined) clearTimer(timer); timers.delete(owner); }
  function watch(owner) {
    stop(owner);
    const workspaceId = owner.current?.summary?.id, id = owner.pdfKnowledgeTask?.id;
    if (!native || !owns(owner, workspaceId) || !processing(owner.pdfKnowledgeTask)) return;
    timers.set(owner, setTimer(async () => {
      timers.delete(owner);
      const generation = owner.pdfKnowledgeReadGeneration || 0;
      try {
        const task = await invoke('read_desktop_pdf_knowledge', { workspaceId, taskId: id }, owner);
        if (!selected(owner, workspaceId, id) || generation !== (owner.pdfKnowledgeReadGeneration || 0)) return;
        owner.pdfKnowledgeTask = task;
        if (owner.pane === 'knowledge') render();
        watch(owner);
      } catch (error) {
        if (selected(owner, workspaceId, id) && error?.code !== 'STALE_DATA_AREA') { owner.error = 'PDF 进度暂不可读，请重新打开知识页查看保留的任务。'; render(); }
      }
    }, 500));
  }
  async function load(owner = getState()) {
    const workspaceId = owner.current?.summary?.id;
    if (!native || !workspaceId) return;
    const generation = owner.pdfKnowledgeReadGeneration = (owner.pdfKnowledgeReadGeneration || 0) + 1;
    const [task, page] = await Promise.all([invoke('read_latest_desktop_pdf_knowledge', { workspaceId }, owner), invoke('list_desktop_pdf_knowledge_tasks', { workspaceId, offset: 0 }, owner)]);
    if (!owns(owner, workspaceId) || owner.pdfKnowledgeReadGeneration !== generation) return;
    owner.pdfKnowledgeTask = task; owner.pdfKnowledgeTasks = page.tasks; owner.pdfKnowledgeHasMore = page.hasMore;
    if (owner.pane === 'knowledge') render();
    watch(owner);
  }
  function card(task) {
    if (!task) return '';
    const owner = getState(), ready = ['AWAITING_CONFIRMATION', 'PARTIALLY_COMPLETED'].includes(task.status);
    const progress = processing(task) ? (task.totalPages > 0 ? `<p>已提取 ${task.completedPages} / ${task.totalPages} 页</p><progress max="${task.totalPages}" value="${task.completedPages}" aria-label="PDF 逐页提取进度"></progress>` : '<p>正在检查文件与页数…</p>') : '';
    return `<section class="memory-rail pdf-knowledge-task" data-task-id="${escape(task.id)}"><div class="section-head"><h2>PDF 文字导入 · ${escape(labels[task.status] || '待核对')}</h2></div><label>导入任务<select data-action="select-pdf-knowledge-task">${(owner.pdfKnowledgeTasks || []).map(item => `<option value="${escape(item.id)}" ${item.id === task.id ? 'selected' : ''}>${escape(item.displayName)} · ${escape(labels[item.status] || item.status)}</option>`).join('')}</select></label>${owner.pdfKnowledgeHasMore ? '<button data-action="more-pdf-knowledge-tasks">读取更多导入任务</button>' : ''}<p>${escape(task.displayName)} · 每个有文字的页面独立审阅；确认前不会写入知识。</p>${progress}${task.safeErrorCode ? '<p class="security-note">未能安全提取文字。只支持可读取文字层的 PDF；不自动扫描识别。私有副本和已有确认已保留，可取消或重新检查。</p>' : ''}<ul>${task.items.map(item => `<li><span>第 ${item.pageNumber} 页 · ${escape(item.candidate.title)} · ${escape({ PENDING_CONFIRMATION: '待确认', CONFIRMED: '已保存', SKIPPED: '已跳过' }[item.status] || item.status)}</span>${ready && item.status === 'PENDING_CONFIRMATION' ? `<button data-action="review-pdf-knowledge" data-ordinal="${item.ordinal}">审阅并保存</button><button data-action="skip-pdf-knowledge" data-ordinal="${item.ordinal}">跳过</button>` : ''}</li>`).join('')}</ul><div class="item-actions">${task.status === 'FAILED' ? '<button data-action="retry-pdf-knowledge">重新检查私有副本</button>' : ''}${!['CANCELLED', 'COMPLETED'].includes(task.status) ? '<button data-action="cancel-pdf-knowledge">取消此导入</button>' : ''}</div></section>`;
  }
  async function run(owner, workspaceId, taskId, retry) {
    const key = `${owner.dataArea}:${workspaceId}:${taskId}`;
    if (running.has(key)) return;
    running.add(key);
    try {
      const pending = retry ? invoke('retry_desktop_pdf_knowledge', { workspaceId, taskId }, owner) : invoke('process_desktop_pdf_knowledge', { workspaceId, taskId }, owner);
      // Polling also starts on explicit retry; failed tasks otherwise stay inert on load.
      if (selected(owner, workspaceId, taskId)) {
        owner.pdfKnowledgeTask = { ...owner.pdfKnowledgeTask, status: 'PREFLIGHT', completedPages: 0, totalPages: 0, items: [], safeErrorCode: null };
        watch(owner); render();
      }
      const task = await pending;
      if (!selected(owner, workspaceId, taskId)) return;
      owner.pdfKnowledgeTask = task; stop(owner);
      owner.status = task.status === 'FAILED' ? 'PDF 提取未完成；任务和私有副本已保留。' : task.status === 'CANCELLED' ? '已取消 PDF 提取；已有确认不受影响。' : 'PDF 文字已在本机提取，请逐页审阅后保存。';
      render();
    } catch (error) {
      if (selected(owner, workspaceId, taskId) && error?.code !== 'STALE_DATA_AREA') { stop(owner); owner.error = 'PDF 处理未完成，请重新读取任务状态后再操作。'; render(); }
    } finally { running.delete(key); }
  }
  async function handle(event) {
    const target = event.target.closest?.('[data-action]'), action = target?.dataset.action;
    if (action === 'select-pdf-knowledge-task' && event.type === 'click') return;
    if (!['select-pdf-knowledge', 'select-pdf-knowledge-task', 'more-pdf-knowledge-tasks', 'review-pdf-knowledge', 'confirm-pdf-knowledge', 'skip-pdf-knowledge', 'cancel-pdf-knowledge', 'retry-pdf-knowledge'].includes(action)) return;
    event.preventDefault(); event.stopImmediatePropagation();
    const owner = getState(), workspaceId = owner.current?.summary?.id, dialog = owner.dialog;
    let startingTaskId = null;
    if (!native || !workspaceId) { owner.error = '请在本机应用中选择当前工作区后导入。'; render(); return; }
    try {
      if (action === 'select-pdf-knowledge') {
        const selectedPath = await dialogInvoke('open', { multiple: false, directory: false, filters: [{ name: 'PDF 文字知识', extensions: ['pdf'] }] });
        if (!selectedPath || !owns(owner, workspaceId) || owner.dialog !== dialog) return;
        const task = await invoke('stage_desktop_pdf_knowledge', { workspaceId, selectedPath }, owner);
        if (!owns(owner, workspaceId) || owner.dialog !== dialog) return;
        owner.pdfKnowledgeReadGeneration = (owner.pdfKnowledgeReadGeneration || 0) + 1;
        owner.pdfKnowledgeTask = task; owner.pdfKnowledgeTasks = [{ id: task.id, displayName: task.displayName, status: task.status }, ...(owner.pdfKnowledgeTasks || [])];
        owner.dialog = null; owner.pane = 'knowledge';
        if (task.status === 'PRIVATE_COPIED') void run(owner, workspaceId, task.id, false);
      } else if (action === 'select-pdf-knowledge-task') {
        stop(owner);
        const generation = owner.pdfKnowledgeReadGeneration = (owner.pdfKnowledgeReadGeneration || 0) + 1;
        const task = await invoke('read_desktop_pdf_knowledge', { workspaceId, taskId: target.value }, owner);
        if (!owns(owner, workspaceId) || generation !== owner.pdfKnowledgeReadGeneration) return;
        owner.pdfKnowledgeTask = task; watch(owner);
      } else if (action === 'more-pdf-knowledge-tasks') {
        const page = await invoke('list_desktop_pdf_knowledge_tasks', { workspaceId, offset: (owner.pdfKnowledgeTasks || []).length }, owner);
        if (!owns(owner, workspaceId)) return;
        owner.pdfKnowledgeTasks.push(...page.tasks); owner.pdfKnowledgeHasMore = page.hasMore;
      } else {
        const task = owner.pdfKnowledgeTask; startingTaskId = task?.id;
        if (!task || task.workspaceId !== workspaceId || target.closest?.('[data-task-id]')?.dataset.taskId !== task.id) throw new Error('TASK_CHANGED');
        const ordinal = Number(target.dataset.ordinal ?? dialog?.ordinal);
        if (action === 'review-pdf-knowledge') {
          const item = task.items.find(item => item.ordinal === ordinal && item.status === 'PENDING_CONFIRMATION');
          if (!item || !['AWAITING_CONFIRMATION', 'PARTIALLY_COMPLETED'].includes(task.status)) throw new Error('ITEM_CHANGED');
          owner.dialog = { kind: 'pdf-knowledge-review', workspaceId, taskId: task.id, generation: task.generation, ordinal, pageNumber: item.pageNumber, item: item.candidate, draft: {} };
        } else if (action === 'retry-pdf-knowledge') {
          if (task.status !== 'FAILED') throw new Error('NOT_FAILED');
          void run(owner, workspaceId, task.id, true);
        } else {
          let updated;
          if (action === 'confirm-pdf-knowledge') {
            if (dialog?.kind !== 'pdf-knowledge-review' || dialog.workspaceId !== workspaceId || dialog.taskId !== task.id || dialog.generation !== task.generation) throw new Error('GENERATION_CHANGED');
            const candidate = { title: document.querySelector('#pdf-knowledge-title')?.value || '', body: document.querySelector('#pdf-knowledge-body')?.value || '', tags: (document.querySelector('#pdf-knowledge-tags')?.value || '').split(',').map(v => v.trim()).filter(Boolean) };
            const projectId = document.querySelector('#pdf-knowledge-project')?.value || null;
            updated = await invoke('confirm_desktop_pdf_knowledge_item', { args: { workspaceId, taskId: task.id, generation: task.generation, ordinal, candidate, projectId } }, owner);
          } else {
            updated = action === 'skip-pdf-knowledge' ? await invoke('skip_desktop_pdf_knowledge_item', { workspaceId, taskId: task.id, generation: task.generation, ordinal }, owner) : await invoke('cancel_desktop_pdf_knowledge', { workspaceId, taskId: task.id }, owner);
          }
          if (!selected(owner, workspaceId, task.id) || owner.pdfKnowledgeTask.generation !== task.generation) return;
          owner.pdfKnowledgeTask = updated;
          if (owner.dialog === dialog && dialog?.kind === 'pdf-knowledge-review') owner.dialog = null;
          if (action === 'confirm-pdf-knowledge') { owner.status = '已保存为本机知识，PDF 私有副本与页来源回执保留。'; await refresh(); if (!owns(owner, workspaceId)) return; }
          watch(owner);
        }
      }
      owner.error = ''; if (owns(owner, workspaceId)) render();
    } catch (error) {
      if (!owns(owner, workspaceId) || (startingTaskId && owner.pdfKnowledgeTask?.id !== startingTaskId) || error?.code === 'STALE_DATA_AREA') return;
      owner.error = 'PDF 操作未完成，请重新读取当前任务并检查正文、标签或敏感内容。'; render();
    }
  }
  return { load, card, handle, stop };
}
