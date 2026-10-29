// Windows product presentation only. Stored domains, migrations and backend
// owners remain available for preservation; this policy never deletes data.
export function workspaceUiEnabled(runtimeInfo = {}) {
  const platform = String(runtimeInfo.platform || (globalThis.window ? globalThis.navigator?.platform : '') || '').toLowerCase();
  return !platform.startsWith('win');
}

const workspacePanes = new Set(['work', 'projects', 'knowledge', 'memory', 'p8-inspect']);
const workspacePages = new Set(['workspace', 'development']);
const workspaceActions = new Set([
  'show-work', 'show-workspace', 'show-projects', 'show-knowledge', 'show-memory',
  'show-p8-inspect', 'show-connections', 'select-workspace',
  'reference-message-to-other-area', 'start-import', 'start-export',
  'select-nanfeng-knowledge-export', 'review-history-knowledge',
  'confirm-nanfeng-knowledge-conflict', 'skip-nanfeng-knowledge-conflict',
]);

export function chatUiRoute(pane, settingsPage, runtimeInfo) {
  if (workspaceUiEnabled(runtimeInfo)) return { pane, settingsPage };
  return {
    pane: workspacePanes.has(pane) ? 'chat' : pane,
    settingsPage: workspacePages.has(settingsPage) ? 'personalization' : settingsPage,
  };
}

export function chatUiActionAllowed(action, data = {}, runtimeInfo) {
  if (workspaceUiEnabled(runtimeInfo)) return true;
  if (workspaceActions.has(action)) return false;
  if (action === 'open-settings-page' && workspacePages.has(data.page)) return false;
  if (action === 'select-local-backup-area' && data.area === 'WORK') return false;
  if (action === 'select-privacy-cleanup-scope' && data.scope === 'KNOWLEDGE_MEMORY_TRASH') return false;
  if (action === 'toggle-product-setting' && data.key === 'historyLibraryEnabled') return false;
  return true;
}

const escapeHtml = value => String(value ?? '').replace(/[&<>"']/g,
  char => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#039;' })[char]);

// Preserve access to imported chat collections without exposing domain tools.
export function renderChatHistorySources(data, sources = []) {
  if (sources.length < 2) return '';
  const sourceTitle = item => String(item.id || '').startsWith('workspace-local-') && item.title === '本机工作区' ? '本机聊天' : item.title;
  return `<div class="workspace-choices chat-history-sources" aria-label="聊天记录来源"><p>聊天记录来源</p>${sources.map(item => `<button type="button" data-action="select-chat-source" data-id="${escapeHtml(item.id)}" class="${item.id === data?.summary?.id ? 'selected' : ''}" aria-current="${item.id === data?.summary?.id ? 'true' : 'false'}" title="${escapeHtml(sourceTitle(item))}">${escapeHtml(sourceTitle(item))}</button>`).join('')}</div>`;
}
