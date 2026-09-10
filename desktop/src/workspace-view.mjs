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

export function activeWorkspaceProjects(data) {
  return (data?.exchange?.projects || [])
    .filter(item => !item.archived && !item.deleted)
    .sort((left, right) => Number(Boolean(right.pinned)) - Number(Boolean(left.pinned))
      || String(left.title || '').localeCompare(String(right.title || ''), 'zh-CN'));
}

export function projectWorkConversations(data, projectId) {
  if (!projectId) return [];
  return (data?.exchange?.conversations || [])
    .filter(item => item.projectId === projectId && !item.archived && !item.deleted)
    .sort((left, right) => String(right.updatedAt || right.createdAt || '').localeCompare(String(left.updatedAt || left.createdAt || '')));
}

export function resolveWorkConversationState(data, selectedWorkProjectId, selectedConversationId) {
  const projects = activeWorkspaceProjects(data);
  const project = projects.find(item => item.id === selectedWorkProjectId) || null;
  const conversations = projectWorkConversations(data, project?.id);
  const conversation = conversations.find(item => item.id === selectedConversationId) || null;
  return { projects, project, conversations, conversation };
}

export function renderWorkspaceNavigation({ data, pane, selectedWorkProjectId, selectedConversationId }) {
  const { projects } = resolveWorkConversationState(data, selectedWorkProjectId, selectedConversationId);
  const projectRows = projects.map(project => {
    const conversations = projectWorkConversations(data, project.id);
    const selected = project.id === selectedWorkProjectId;
    return `<div class="chat-work-project-group ${selected ? 'selected' : ''}">
      <div class="chat-work-project-row">
        <button data-action="select-work-project" data-project-id="${escapeHtml(project.id)}" aria-pressed="${selected}"><strong>${escapeHtml(project.title || '未命名项目')}</strong><small>${conversations.length} 个工作对话</small></button>
        <button class="chat-work-project-add" data-action="new-work-chat" data-project-id="${escapeHtml(project.id)}" aria-label="在${escapeHtml(project.title || '项目')}中新建工作对话">＋</button>
      </div>
      ${selected ? `<div class="chat-work-conversations">${conversations.map(conversation => `<button data-action="select-work-conversation" data-project-id="${escapeHtml(project.id)}" data-id="${escapeHtml(conversation.id)}" class="${conversation.id === selectedConversationId ? 'selected' : ''}">${escapeHtml(conversation.title || '未命名工作对话')}</button>`).join('') || '<p>在此项目中创建第一条工作对话</p>'}</div>` : ''}
    </div>`;
  }).join('');
  return `<section class="chat-work-nav" aria-label="工作导航">
    <nav class="chat-work-primary" aria-label="工作区功能">${WORKSPACE_NAVIGATION_ITEMS.map(item => `<button data-action="${item.action}" class="${pane === item.pane ? 'selected' : ''}" aria-current="${pane === item.pane ? 'page' : 'false'}">${item.label}</button>`).join('')}</nav>
    <div class="chat-work-projects"><header><strong>项目</strong><button data-action="new-project" aria-label="创建项目">＋</button></header>${projectRows || '<p class="chat-work-empty-projects">还没有项目。创建项目后，可在项目内新建独立工作对话。</p>'}</div>
  </section>`;
}

export function renderWorkConversationState({ project }) {
  return `<div class="chat-work-empty-stage" role="status" aria-label="工作内容为空；请从左侧项目创建或打开工作对话">
    <strong>还没有项目工作对话</strong>
    <span>从左侧项目中创建或打开一条对话。</span>
    ${project ? `<small>${escapeHtml(project.title)} · 0 个工作对话</small>` : ''}
  </div>`;
}
