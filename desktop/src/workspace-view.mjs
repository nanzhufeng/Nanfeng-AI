const escapeHtml = value => String(value ?? '').replace(
  /[&<>"']/g,
  char => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#039;' })[char],
);

export const WORKSPACE_NAVIGATION_ITEMS = Object.freeze([
  { pane: 'work', action: 'show-work', label: '工作' },
  { pane: 'projects', action: 'show-projects', label: '项目' },
  { pane: 'knowledge', action: 'show-knowledge', label: '知识' },
  { pane: 'memory', action: 'show-memory', label: '记忆' },
]);

export function renderWorkspaceSidebar({ pane, returnLabel = '返回' }) {
  return `<aside class="workspace-sidebar" aria-label="工作区导航">
    <div class="workspace-sidebar-scroll">
      <div class="workspace-return-group"><button class="workspace-return-chat" data-action="return-workspace-origin" aria-label="${escapeHtml(returnLabel)}"><span aria-hidden="true">←</span><span>${escapeHtml(returnLabel)}</span></button></div>
      <nav class="workspace-sidebar-pages" aria-label="工作区功能">${WORKSPACE_NAVIGATION_ITEMS.map(item => `<button data-action="${item.action}" class="${pane === item.pane ? 'selected' : ''}" aria-current="${pane === item.pane ? 'page' : 'false'}">${item.label}</button>`).join('')}</nav>
    </div>
  </aside>`;
}

export function activeWorkspaceProjects(data) {
  return (data?.exchange?.projects || [])
    .filter(item => !item.archived && !item.deleted)
    .sort((left, right) => Number(Boolean(right.pinned)) - Number(Boolean(left.pinned))
      || String(left.title || '').localeCompare(String(right.title || ''), 'zh-CN'));
}

export function projectWorkConversations(data, projectId) {
  if (!projectId && data?.dataArea !== 'WORK') return [];
  return (data?.exchange?.conversations || [])
    .filter(item => (!projectId || item.projectId === projectId) && !item.archived && !item.deleted)
    .sort((left, right) => String(right.updatedAt || right.createdAt || '').localeCompare(String(left.updatedAt || left.createdAt || '')));
}

export function resolveWorkConversationState(data, selectedWorkProjectId, selectedConversationId) {
  const projects = activeWorkspaceProjects(data);
  const project = projects.find(item => item.id === selectedWorkProjectId) || null;
  const conversations = selectedWorkProjectId && !project ? [] : projectWorkConversations(data, project?.id);
  const conversation = conversations.find(item => item.id === selectedConversationId) || null;
  return { projects, project, conversations, conversation };
}

export function isWorkspaceRoot(data, pane, selectedWorkProjectId, selectedConversationId) {
  if (pane !== 'work') return false;
  const { project, conversation } = resolveWorkConversationState(data, selectedWorkProjectId, selectedConversationId);
  return !project && !conversation;
}

export function renderWorkspaceNavigation({ pane, returnLabel = '返回' }) {
  return `<section class="chat-work-nav" aria-label="工作导航">
    <div class="chat-work-return-group"><button class="chat-work-return" data-action="return-workspace-origin" aria-label="${escapeHtml(returnLabel)}"><span aria-hidden="true">←</span><span>${escapeHtml(returnLabel)}</span></button></div>
    <nav class="chat-work-primary" aria-label="工作区功能">${WORKSPACE_NAVIGATION_ITEMS.map(item => `<button data-action="${item.action}" class="${pane === item.pane ? 'selected' : ''}" aria-current="${pane === item.pane ? 'page' : 'false'}">${item.label}</button>`).join('')}</nav>
  </section>`;
}

export function renderWorkConversationState({ project }) {
  return `<div class="chat-work-empty-stage" role="status" aria-label="暂无项目工作对话">
    <strong>${project ? `${escapeHtml(project.title)}暂无工作对话` : '还没有项目工作对话'}</strong>
  </div>`;
}
