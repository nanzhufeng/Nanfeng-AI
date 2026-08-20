import { p8InspectCanvas } from './p8-inspect.mjs';
import { activeConversations, clampDesktopSidebarWidth, messagePlainText, renderChatFirstShell, resolveConversation, resolveConversationMenuAnchor } from './chat-shell.mjs';
import { beginConversationRecycle, completeConversationRecycle, failConversationRecycle } from './recycle-confirmation.mjs';

const app = document.querySelector('#app');
const tauriBridge = window.__TAURI__?.core ?? window.__TAURI_INTERNALS__;
const native = Boolean(tauriBridge?.invoke);
const fixture = { summary: { id: 'workspace-preview-01', title: 'P6-D 本地预览', semanticHash: 'local-preview…', packageHash: 'read-only…', projectCount: 1, conversationCount: 1, knowledgeCount: 1, memoryCount: 1, relationCount: 0, assetCount: 0, assetByteCount: 0, highSensitive: false }, exchange: { projects: [{ id: 'project-preview-01', title: '本地项目', description: '浏览器预览不会写入 Desktop SQLite。', pinned: true, archived: false, revision: 1 }], conversations: [{ id: 'conversation-preview-01', title: '本地会话', revision: 1, messages: [{ id: 'message-preview-01', role: 'user', createdAt: '2026-08-13T00:00:00Z', blocks: [{ kind: 'TEXT', text: '本地文本记录；不会执行 Markdown、HTML 或代码。' }] }] }], knowledge: [{ id: 'knowledge-preview-01', title: '安全知识', body: '编辑、撤销与软删除只在 Tauri Desktop 的 Rust SQLite 中执行。', tags: ['local'], status: 'ACTIVE', scope: 'GLOBAL', revision: 1, classification: 'NORMAL' }], memory: [{ id: 'memory-preview-01', body: '本地 Memory 仅作文本 IR。', scope: 'GLOBAL', status: 'ACTIVE', revision: 1 }], relations: [] } };
const previewConnection = { connection: 'ONLINE_CONFIGURATION_REQUIRED', providerConfiguration: 'NOT_CONFIGURED', credentialPresence: 'MISSING', catalogFreshness: 'NOT_AVAILABLE', egressConsent: 'REQUIRED_PER_INTENT', syncCapability: 'ENCRYPTED_SYNC_NOT_CONFIGURED', degradedReasons: ['NO_CREDENTIAL', 'CATALOG_UNAVAILABLE', 'NETWORK_UNVERIFIED', 'EGRESS_CONSENT_REQUIRED', 'SYNC_NOT_CONFIGURED'] };
const settingsLocalKey = 'nanfeng-ai.desktop.settings.sidebar-width.v1';
function readSidebarWidth() { try { return clampDesktopSidebarWidth(Number(localStorage.getItem(settingsLocalKey)), window.innerWidth); } catch { return 256; } }
function persistSidebarWidth(width) { try { localStorage.setItem(settingsLocalKey, String(width)); } catch { /* browser preview may deny local storage */ } }
const state = { workspaces: [], current: null, pane: 'chat', selectedConversationId: null, chatgptTask: null, claudeTask: null, p6kTask: null, composerDraft: '', composerAttachments: [], temporaryConversation: null, profileOpen: false, sidebarOpen: false, railCollapsed: false, sidebarWidth: readSidebarWidth(), settingsSection: 'data', settingsSearch: '', showArchived: false, showDeleted: false, contextMenu: null, composerAddOpen: false, temporaryModelOpen: false, p6gModelPickerOpen: false, compareLongPressTriggered: false, p6gCatalog: null, p6gGlobalDefault: { revision: 0, tier: null }, p6gSelection: null, chatScrollPositions: new Map(), chatAtLatest: true, transcriptRailTrackingConversationId: null, pendingChatScrollToLatestId: null, pendingChatSendScrollToLatestId: null, scrollToLatestAnimationId: null, focusComposerAfterScrollToLatest: false, inspectorOpen: true, treeOpen: false, preflight: null, dialog: null, history: { canUndo: false, canRedo: false, recycleBin: [], modelMetadata: [] }, agentRuns: [], connection: previewConnection, p6eAcceptance: { enabled: false, receipt: null }, status: native ? '本地工作区已就绪；联网模型尚未配置。' : 'Web 预览不会读写 Desktop 数据库。', error: '', scale: 1, searchResults: [], searchPanel: false, searchAnchorMessageId: null, searchHistory: [], searchHistoryOpen: false, suppressSearchHistoryFocus: false, imageThumbnails: {}, imageThumbnailPending: new Set(), imagePreview: null, pdfPreview: null, videoPreview: null, audioPreview: null, textPreview: null };
let compareLongPressTimer = null;
let p6kManualLink = { assetOrdinal: null, conversationId: null, messageId: null };
globalThis.__nanfengP6kManualLink = p6kManualLink;

function cancelCompareLongPress() {
  if (compareLongPressTimer !== null) window.clearTimeout(compareLongPressTimer);
  compareLongPressTimer = null;
}

function executeDesktopCompare() {
  state.p6gModelPickerOpen = false;
  state.dialog = null;
  state.error = 'Compare 已直接提交执行请求，但当前 Desktop 没有可用执行 owner；未读取 Key 或发送内容。';
  state.status = 'Compare 未执行：Desktop execution owner 不可用。';
  render();
}

app.addEventListener('pointerdown', event => {
  const trigger = event.target.closest?.('[data-compare-long-press]');
  if (!trigger || trigger.disabled) return;
  cancelCompareLongPress();
  compareLongPressTimer = window.setTimeout(() => {
    compareLongPressTimer = null;
    state.compareLongPressTriggered = true;
    executeDesktopCompare();
  }, 550);
}, true);
app.addEventListener('pointerup', cancelCompareLongPress, true);
app.addEventListener('pointercancel', cancelCompareLongPress, true);
state.nanfengKnowledgeTask = null;
let p6hDiagnosticsEnabled = false;
function p6hDiagnosticMarker(event) {
  if (!p6hDiagnosticsEnabled) return;
  const divider = event.target.closest?.('.chat-sidebar-divider');
  if (!divider && !event.target.closest?.('.chat-profile-menu')) return;
  const target = event.target.closest('[data-action]');
  let marker = document.querySelector('#p6h-dom-event-marker');
  if (!marker) { marker = document.createElement('p'); marker.id = 'p6h-dom-event-marker'; marker.setAttribute('role', 'status'); marker.style.cssText = 'position:fixed;z-index:9999;left:12px;bottom:12px;padding:6px;background:#173;color:#fff;font-size:12px'; document.body.append(marker); }
  marker.textContent = divider
    ? `P6-H acceptance divider ${event.type}: ${state.sidebarWidth}px`
    : `P6-H acceptance DOM ${event.type}: ${target?.dataset.action || event.target.tagName}`;
}
app.addEventListener('pointerdown', p6hDiagnosticMarker, true);
app.addEventListener('click', p6hDiagnosticMarker, true);
app.addEventListener('keydown', p6hDiagnosticMarker, true);
state.chatSearch = '';
let overlayFocusReturn = null;
function rememberOverlayTrigger(target) { overlayFocusReturn = target?.dataset?.action || null; }
function restoreOverlayFocus() { const action = overlayFocusReturn; overlayFocusReturn = null; if (action) queueMicrotask(() => document.querySelector(`[data-action="${action}"]`)?.focus()); }
/** Single owner for app-owned transient layers; native system pickers intentionally remain outside it. */
function closeTopOverlay({ restoreFocus = true } = {}) {
  if (state.contextMenu) state.contextMenu = null;
  else if (state.temporaryModelOpen) state.temporaryModelOpen = false;
  else if (state.p6gModelPickerOpen) state.p6gModelPickerOpen = false;
  else if (state.composerAddOpen) state.composerAddOpen = false;
  else if (state.profileOpen) state.profileOpen = false;
  else if (state.dialog) state.dialog = null;
  else if (state.imagePreview) state.imagePreview = null;
  else if (state.pdfPreview) state.pdfPreview = null;
  else if (state.sidebarOpen) state.sidebarOpen = false;
  else return false;
  render();
  if (restoreFocus) restoreOverlayFocus();
  return true;
}
function openTransientOverlay(kind, target, value = true) {
  state.contextMenu = kind === 'context' ? value : null;
  state.composerAddOpen = kind === 'composer-add';
  state.temporaryModelOpen = kind === 'temporary-model';
  state.p6gModelPickerOpen = kind === 'p6g-model-picker';
  state.profileOpen = kind === 'profile';
  rememberOverlayTrigger(target);
  render();
}
app.addEventListener('click', event => {
  const target = event.target.closest?.('[data-action]');
  if (event.target.classList?.contains('scrim')) {
    // A scrim is always cancel-only. It never invokes a destructive confirmation action.
    closeTopOverlay();
    return;
  }
  if (target?.dataset.action === 'toggle-profile' && !state.profileOpen) {
    // The legacy profile trigger remains a simple toggle, but it must replace any lower overlay.
    state.contextMenu = null;
    state.composerAddOpen = false;
    rememberOverlayTrigger(target);
  }
}, true);
app.addEventListener('click', event => {
  const action = event.target.closest('[data-action]')?.dataset.action;
  if (!state.temporaryConversation || !['new-chat', 'select-chat', 'show-work', 'show-settings'].includes(action)) return;
  event.preventDefault(); event.stopImmediatePropagation();
  state.status = '临时聊天与普通导航隔离；点击右上角“临时聊天”返回普通聊天。';
  render();
}, true);
app.addEventListener('click', event => {
  const target = event.target.closest('[data-action="select-chat"]');
  if (!target || state.pane !== 'work') return;
  // Work is a Conversation scope, not a route to the former work-home dashboard.
  event.stopImmediatePropagation();
  state.selectedConversationId = target.dataset.id;
  state.profileOpen = false;
  state.error = '';
  state.composerDraft = readChatDraft();
  render();
}, true);
const invoke = (command, args = {}) => tauriBridge.invoke(command, args);
const dialogInvoke = (command, options) => invoke(`plugin:dialog|${command}`, { options });
const escape = value => String(value ?? '').replace(/[&<>"']/g, char => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#039;' }[char]));
const short = value => value?.length > 14 ? `${value.slice(0, 12)}…` : value || '—';
const bytes = value => value < 1024 ? `${value} B` : `${(value / 1024).toFixed(1)} KiB`;
const intent = prefix => `${prefix}-${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 10)}`;
const icon = (path, label) => `<svg aria-hidden="true" viewBox="0 0 24 24"><title>${label}</title><path d="${path}"/></svg>`;
async function submitLocalSearch() {
  const query = String(state.chatSearch || '').trim();
  if (!query) return;
  if (!native || !state.current) { state.searchResults = []; state.searchPanel = true; state.status = 'Web 预览不读取 Desktop SQLite 搜索索引。'; render(); return; }
  try {
    state.searchResults = await invoke('search_desktop_local_index', { workspaceId: state.current.summary.id, query }); state.searchHistory = await invoke('read_desktop_local_search_history', { workspaceId: state.current.summary.id });
    state.searchPanel = true; state.error = ''; state.status = '已在本机安全索引中完成搜索。'; render();
  } catch (error) { state.error = `本地搜索未完成：${String(error)}`; render(); }
}
const icons = { workspace: 'M3 5.5A2.5 2.5 0 0 1 5.5 3H10l2 2h6.5A2.5 2.5 0 0 1 21 7.5v10a2.5 2.5 0 0 1-2.5 2.5h-13A2.5 2.5 0 0 1 3 17.5z', conversation: 'M4 5.5A2.5 2.5 0 0 1 6.5 3h11A2.5 2.5 0 0 1 20 5.5v8a2.5 2.5 0 0 1-2.5 2.5H11L7 20v-4H6.5A2.5 2.5 0 0 1 4 13.5z', knowledge: 'M5 3.5h11A3 3 0 0 1 19 6.5v13l-6.5-3-6.5 3v-13a3 3 0 0 1 3-3z', import: 'M12 3v12m0 0 4-4m-4 4-4-4M5 17v3h14v-3', export: 'M12 15V3m0 0 4 4m-4-4L8 7M5 17v3h14v-3', close: 'M6 6l12 12M18 6 6 18', undo: 'M9 7 4 12l5 5M5 12h9a5 5 0 1 1 0 10', redo: 'm15 7 5 5-5 5m4-5h-9a5 5 0 1 0 0 10', trash: 'M4 7h16M10 11v6m4-6v6M9 7l1-2h4l1 2M6 7l1 14h10l1-14', plus: 'M12 5v14M5 12h14', edit: 'm4 16 9-9 3 3-9 9H4zM14 6l2-2 3 3-2 2', info: 'M12 17v-6m0-3.5v.01M12 3a9 9 0 1 0 0 18 9 9 0 0 0 0-18z', link: 'M10 13a5 5 0 0 0 7.1.1l2-2a5 5 0 0 0-7.1-7.1l-1.1 1.1m3.1 5.9a5 5 0 0 0-7.1-.1l-2 2A5 5 0 0 0 9 20l1.1-1.1' };
function workspace() { return state.current || (!native ? fixture : null); }
function selectWorkspaceDefaultConversation(data = workspace()) {
  state.selectedConversationId = data ? activeConversations(data)[0]?.id || null : null;
}
function active(data, key) { return data.exchange[key].filter(item => key === 'relations' ? item.status === 'ACTIVE' : item.status !== 'DELETED' && !item.archived && !item.deleted); }
function dialog() {
  if (!state.dialog) return '';
  if (state.dialog === 'import') return `<div class="scrim"><section class="dialog" role="dialog" aria-modal="true" aria-labelledby="dialog-title"><button class="icon-button close" data-action="close-dialog" aria-label="关闭">${icon(icons.close, '关闭')}</button><p class="overline">严格预检已通过</p><h2 id="dialog-title">导入为新的独立工作区</h2><p>不会合并或覆盖。正文仅作为不执行的文本 IR。</p><label>新工作区名称<input id="workspace-title" maxlength="120" value="导入工作区"></label><div class="dialog-actions"><button data-action="close-dialog">取消</button><button class="primary" data-action="confirm-import">导入</button></div></section></div>`;
  if (state.dialog.kind === 'project') { const item = state.dialog.item; return `<div class="scrim"><section class="dialog edit-dialog" role="dialog" aria-modal="true" aria-labelledby="dialog-title"><button class="icon-button close" data-action="close-dialog" aria-label="取消">${icon(icons.close, '关闭')}</button><p class="overline">本地 revision 写入</p><h2 id="dialog-title">${item ? '编辑 Project' : '新建 Project'}</h2><p>保存由 Rust 检查 expected revision；冲突不会覆盖现有对象。</p><label>名称<input id="project-title" maxlength="120" value="${escape(item?.title || '')}"></label><label>说明<textarea id="project-description" maxlength="2000">${escape(item?.description || '')}</textarea></label><div class="dialog-actions"><button data-action="close-dialog">取消</button><button class="primary" data-action="save-project">保存 ⌘S</button></div></section></div>`; }
  if (state.dialog.kind === 'knowledge') { const item = state.dialog.item; return `<div class="scrim"><section class="dialog edit-dialog" role="dialog" aria-modal="true" aria-labelledby="dialog-title"><button class="icon-button close" data-action="close-dialog" aria-label="取消">${icon(icons.close, '关闭')}</button><p class="overline">本地 revision 写入</p><h2 id="dialog-title">${item ? '编辑 Knowledge' : '新建 Knowledge'}</h2><p>保存时由 Rust 检查 expected revision；冲突不会覆盖当前记录。</p><label>标题<input id="knowledge-title" maxlength="120" value="${escape(item?.title || '')}"></label><label>正文<textarea id="knowledge-body" maxlength="2000000">${escape(item?.body || '')}</textarea></label><label>标签（逗号分隔）<input id="knowledge-tags" value="${escape((item?.tags || []).join(', '))}"></label><div class="dialog-actions"><button data-action="close-dialog">取消</button><button class="primary" data-action="save-knowledge">保存 ⌘S</button></div></section></div>`; }
  if (state.dialog.kind === 'memory') { const item = state.dialog.item; return `<div class="scrim"><section class="dialog edit-dialog" role="dialog" aria-modal="true" aria-labelledby="dialog-title"><button class="icon-button close" data-action="close-dialog" aria-label="取消">${icon(icons.close, '关闭')}</button><p class="overline">本地 revision 写入</p><h2 id="dialog-title">${item ? '编辑 Memory' : '新建 Memory'}</h2><p>Memory 仅是本地文本 IR，不会进入 Prompt 或网络。</p><label>正文<textarea id="memory-body" maxlength="2000000">${escape(item?.body || '')}</textarea></label><div class="dialog-actions"><button data-action="close-dialog">取消</button><button class="primary" data-action="save-memory">保存 ⌘S</button></div></section></div>`; }
  if (state.dialog.kind === 'relation') { const data = workspace(); const items = active(data, 'knowledge'); const options = items.map(item => `<option value="${escape(item.id)}">${escape(item.title)} · r${item.revision}</option>`).join(''); return `<div class="scrim"><section class="dialog" role="dialog" aria-modal="true" aria-labelledby="dialog-title"><button class="icon-button close" data-action="close-dialog" aria-label="取消">${icon(icons.close, '关闭')}</button><p class="overline">显式本地 relation</p><h2 id="dialog-title">建立 Knowledge 关系</h2><p>仅同一活动 scope 的两条 Knowledge 可建立；不会自动关联或去重。</p><label>来源<select id="relation-from">${options}</select></label><label>目标<select id="relation-to">${options}</select></label><label>类型<select id="relation-kind"><option value="RELATED">RELATED（对称）</option><option value="DERIVED_FROM">DERIVED_FROM</option><option value="REFERENCES">REFERENCES</option></select></label><div class="dialog-actions"><button data-action="close-dialog">取消</button><button class="primary" data-action="save-relation" ${items.length < 2 ? 'disabled' : ''}>建立关系</button></div></section></div>`; }
  if (state.dialog === 'metadata') return `<div class="scrim"><section class="dialog edit-dialog" role="dialog" aria-modal="true" aria-labelledby="dialog-title"><button class="icon-button close" data-action="close-dialog" aria-label="关闭">${icon(icons.close, '关闭')}</button><p class="overline">仅本地安全 metadata</p><h2 id="dialog-title">模型与成本 metadata</h2><p>不存 Key、不联网取 catalog；价格仅标记为 fixture、手工或未知，绝不当作真实费用。</p><label>Provider ID<input id="provider-id" value="provider-local"></label><label>Model ID<input id="model-id" value="model-manual"></label><label>价格版本<input id="price-version" value="manual-v1"></label><label>币种<input id="price-currency" value="CNY"></label><div class="dialog-actions"><button data-action="close-dialog">取消</button><button class="primary" data-action="save-metadata">保存 metadata</button></div></section></div>`;
  if (state.dialog === 'recycle') return `<div class="scrim"><section class="dialog" role="dialog" aria-modal="true" aria-labelledby="dialog-title"><button class="icon-button close" data-action="close-dialog" aria-label="关闭">${icon(icons.close, '关闭')}</button><p class="overline">本地软删除</p><h2 id="dialog-title">回收站</h2><p>永久删除在 P6-C 未开放。恢复将生成新 revision。</p><div class="recycle-list">${state.history.recycleBin.length ? state.history.recycleBin.map(item => `<div><span>${escape(item.entity)} · ${escape(item.title)}</span><button data-action="restore" data-entity="${escape(item.entity)}" data-id="${escape(item.id)}" data-revision="${item.revision}">恢复</button></div>`).join('') : '<p class="empty-copy">暂无可恢复对象。</p>'}</div><div class="dialog-actions"><button data-action="close-dialog">关闭</button></div></section></div>`;
  if (state.dialog === 'about') return `<div class="scrim"><section class="dialog" role="dialog" aria-modal="true" aria-labelledby="dialog-title"><button class="icon-button close" data-action="close-dialog" aria-label="关闭">${icon(icons.close, '关闭')}</button><p class="overline">Desktop 本机交付摘要</p><h2 id="dialog-title">关于南枫 AI Desktop</h2><dl class="about-list"><dt>版本</dt><dd>0.6.0-p6d-dev</dd><dt>账号与同步</dt><dd>尚未配置 · 离线可用；无浏览器登录、无 HTTP、无同步队列</dd><dt>签名</dt><dd>ad-hoc 开发签名，未 notarized</dd><dt>最低系统</dt><dd>macOS 11 或更高</dd><dt>本地数据</dt><dd>仅 app-private 容器；此处不显示路径或数据库文件</dd><dt>更新</dt><dd>本地静态状态；未检查网络</dd></dl><p>卸载应用不会主动删除用户本地数据；清除数据必须通过未来独立的安全流程，不暴露 SQLite 文件。</p><div class="dialog-actions"><button data-action="close-dialog">关闭</button></div></section></div>`;
  if (state.dialog?.kind === 'conversation-rename') return `<div class="scrim"><section class="dialog" role="dialog" aria-modal="true"><label>会话标题<input id="conversation-rename" maxlength="120" value="${escape(state.dialog.title)}"></label><div class="dialog-actions"><button data-action="close-dialog">取消</button><button class="primary" data-action="save-conversation-rename" data-id="${escape(state.dialog.id)}" data-revision="${state.dialog.revision}">保存</button></div></section></div>`;
  if (state.dialog?.kind === 'conversation-project') { const projects = active(workspace(), 'projects'); const options = [`<option value="">不归入项目</option>`, ...projects.map(project => `<option value="${escape(project.id)}" ${project.id === state.dialog.projectId ? 'selected' : ''}>${escape(project.title)}</option>`)].join(''); return `<div class="scrim"><section class="dialog" role="dialog" aria-modal="true"><h2>项目归属</h2><p>${projects.length ? '选择现有本地项目；切换即为移动，取消不变更。' : '当前没有可选项目。'}</p><label>项目<select id="conversation-project">${options}</select></label><div class="dialog-actions"><button data-action="close-dialog">取消</button><button class="primary" data-action="save-conversation-project" data-id="${escape(state.dialog.id)}" data-revision="${state.dialog.revision}">保存</button></div></section></div>`; }
  if (state.dialog?.kind === 'conversation-delete') { const busy = state.dialog.submitting; return `<div class="scrim"><section class="dialog" role="dialog" aria-modal="true" aria-busy="${busy}"><h2>移入回收站？</h2><p>删除不同于归档：消息树不会物理删除，可从回收站恢复。</p>${state.dialog.failure ? `<p class="dialog-error" role="alert">${escape(state.dialog.failure)}</p>` : ''}<div class="dialog-actions"><button data-action="close-dialog" ${busy ? 'disabled' : ''}>取消</button><button class="primary" data-action="confirm-conversation-delete" data-id="${escape(state.dialog.id)}" data-revision="${state.dialog.revision}" ${busy ? 'disabled' : ''}>${busy ? '正在移入…' : '移入回收站'}</button></div></section></div>`; }
  return '';
}
function tree(data) { if (!data) return '<div class="tree-empty">还没有本地工作区。<br>先从受控交换包导入。</div>'; const workspaceRows = state.workspaces.map(item => `<button class="tree-row ${item.id === data.summary.id ? 'selected' : ''}" data-action="select-workspace" data-id="${escape(item.id)}"><span>${icon(icons.workspace, '工作区')}</span><span>${escape(item.title)}</span><small>${item.id === data.summary.id ? '当前' : ''}</small></button>`).join(''); return `<div class="tree-section"><p>Workspace</p>${workspaceRows}</div><div class="tree-section"><p>Project <button class="small-add" data-action="new-project" aria-label="新建 Project">+</button></p>${data.exchange.projects.map(item => `<button class="tree-row" data-action="edit-project" data-id="${escape(item.id)}"><span>${icon(icons.workspace, '项目')}</span><span>${escape(item.title)}</span><small>r${item.revision || 0}</small></button>`).join('') || '<span class="tree-muted">暂无 Project</span>'}</div><div class="tree-section"><p>Conversation</p>${data.exchange.conversations.map(item => `<button class="tree-row ${state.pane === 'conversation' ? 'selected' : ''}" data-action="show-conversation"><span>${icon(icons.conversation, '会话')}</span><span>${escape(item.title)}</span><small>${item.messages.length}</small></button>`).join('')}</div><div class="tree-section"><p>Knowledge</p><button class="tree-row ${state.pane === 'knowledge' ? 'selected' : ''}" data-action="show-knowledge"><span>${icon(icons.knowledge, '知识')}</span><span>知识与记忆</span><small>${data.exchange.knowledge.length}</small></button></div>`; }
function conversationCanvas(data) { const conversation = data.exchange.conversations[0]; if (!conversation) return `<section class="canvas empty-canvas"><h2>暂无会话</h2><p>新建 Conversation 会在 Rust transaction 中同时建立明确的本地 root 节点。</p></section>`; return `<section class="canvas conversation-canvas"><div class="canvas-header"><div><p class="overline">Conversation · 本地树</p><h1>${escape(conversation.title)}</h1><p>追加节点只接受明确本地文本，不构造 Prompt 或 RunSpec。</p></div><button class="subtle" data-action="show-knowledge">知识工作台</button></div><div class="message-list">${conversation.messages.map(message => `<article class="message ${escape(message.role)}"><header><span>${escape(message.role)}</span><time>${escape(short(message.createdAt))}</time></header>${message.blocks.map(block => `<pre>${escape(block.text || `附件引用 · ${block.asset?.displayName || ''}`)}</pre>`).join('')}</article>`).join('')}</div></section>`; }
function knowledgeCanvas(data) { const knowledge = active(data, 'knowledge'); const memory = active(data, 'memory'); const relationItems = active(data, 'relations'); const name = id => data.exchange.knowledge.find(item => item.id === id)?.title || short(id); return `<section class="canvas knowledge-canvas"><div class="canvas-header"><div><p class="overline">Knowledge / Memory · 文本 IR</p><h1>知识与记忆</h1><p>保存、取消、冲突、撤销与软删除均由 Rust SQLite 真值驱动。</p></div><div class="canvas-actions"><button class="primary" data-action="new-knowledge">${icon(icons.plus, '新建')}新建 Knowledge</button><button data-action="new-memory">${icon(icons.plus, '新建')}新建 Memory</button><button data-action="new-relation" ${knowledge.length < 2 ? 'disabled' : ''}>${icon(icons.link, '关系')}建立关系</button></div></div><div class="knowledge-list">${knowledge.map(item => `<article class="knowledge-item"><header><div><p class="overline">${escape(item.status)} · r${item.revision} · ${escape(item.classification)}</p><h2>${escape(item.title)}</h2></div><span>${escape((item.tags || []).join(' · '))}</span></header><pre>${escape(item.body)}</pre><div class="item-actions"><button data-action="edit-knowledge" data-id="${escape(item.id)}">${icon(icons.edit, '编辑')}编辑</button><button data-action="delete-knowledge" data-id="${escape(item.id)}" data-revision="${item.revision}">${icon(icons.trash, '软删除')}软删除</button></div></article>`).join('') || '<p class="empty-copy">暂无活动 Knowledge。</p>'}</div><section class="memory-rail"><div class="section-head"><h2>Memory</h2><button data-action="new-memory">新建</button></div><ul>${memory.map(item => `<li><span>${escape(item.scope)} · r${item.revision}</span><button class="memory-button" data-action="edit-memory" data-id="${escape(item.id)}">${escape(item.body)}</button><button data-action="delete-memory" data-id="${escape(item.id)}" data-revision="${item.revision}">软删除</button></li>`).join('') || '<li>暂无活动 Memory。</li>'}</ul></section><section class="memory-rail"><div class="section-head"><h2>Knowledge relation</h2><button data-action="new-relation" ${knowledge.length < 2 ? 'disabled' : ''}>建立</button></div><ul>${relationItems.map(item => `<li><span>${escape(item.kind)} · r${item.revision}</span>${escape(name(item.fromId))} → ${escape(name(item.toId))}<button data-action="delete-relation" data-id="${escape(item.id)}" data-revision="${item.revision}">撤销</button></li>`).join('') || '<li>暂无活动 relation。</li>'}</ul></section></section>`; }
function inspector(data) { if (!data) return `<aside class="inspector"><div class="inspector-head"><h2>Inspector</h2></div><p class="empty-copy">导入后显示本地工作区。</p></aside>`; const meta = state.history.modelMetadata; return `<aside class="inspector"><div class="inspector-head"><div><p class="overline">本地事实</p><h2>Inspector</h2></div><button class="icon-button" data-action="toggle-inspector" aria-label="收起">${icon(icons.close, '收起')}</button></div><section><h3>revision 与恢复</h3><div class="inspector-actions"><button data-action="undo" ${state.history.canUndo && native ? '' : 'disabled'}>${icon(icons.undo, '撤销')}撤销</button><button data-action="redo" ${state.history.canRedo && native ? '' : 'disabled'}>${icon(icons.redo, '重做')}重做</button><button data-action="recycle">${icon(icons.trash, '回收站')}回收站 ${state.history.recycleBin.length}</button></div><p class="security-note">动作栈持久化；重启后仍可恢复。冲突会保留当前 revision，绝不静默覆盖。</p></section><section><h3>模型与成本 metadata</h3>${meta.length ? meta.map(item => `<p class="metadata-row"><b>${escape(item.providerId)} / ${escape(item.modelId)}</b><br>${escape(item.metadata?.pricingStatus || 'UNKNOWN')} · ${escape(item.metadata?.priceVersion || '未配置')} · ${escape(item.metadata?.currency || '—')}</p>`).join('') : '<p class="empty-copy">未配置。无网络 catalog、无 Key、无真实费用。</p>'}<button data-action="metadata">${icon(icons.plus, '配置')}配置本地 metadata</button></section><section><h3>工作区</h3><dl><dt>状态</dt><dd>离线 · Rust SQLite</dd><dt>语义 hash</dt><dd title="${escape(data.summary.semanticHash)}">${escape(short(data.summary.semanticHash))}</dd><dt>项目 / 知识</dt><dd>${data.summary.projectCount} / ${data.summary.knowledgeCount}</dd><dt>关系 / 资产</dt><dd>${data.summary.relationCount} / ${data.summary.assetCount}</dd><dt>资产字节</dt><dd>${bytes(data.summary.assetByteCount)}</dd></dl></section><section class="security-note"><h3>安全边界</h3><p>无账号、无网络、无 Provider、无 Prompt/RunSpec。正文不执行。</p></section></aside>`; }
function render() { const data = workspace(); if (state.pane === 'chat' || state.pane === 'connections') { app.className = 'app-shell chat-first'; app.innerHTML = renderChatFirstShell({ data, native, selectedConversationId: state.selectedConversationId, composerDraft: state.composerDraft, profileOpen: state.profileOpen, pane: state.pane, status: state.status, error: state.error, connection: state.connection }); return; } const canvas = state.pane === 'p8-inspect' ? p8InspectCanvas({ agentRuns: state.agentRuns, native, escape, short }) : data ? (state.pane === 'conversation' ? conversationCanvas(data) : knowledgeCanvas(data)) : `<section class="canvas empty-canvas"><p class="overline">P6-D · 离线 Desktop</p><h1>从受控交换包开始</h1><p>导入后可在 Rust 本地领域链中明确创建、编辑、撤销与软删除。</p><button class="primary" data-action="start-import">选择交换包</button></section>`; app.className = `app-shell scale-${state.scale === 2 ? '2' : '1'}`; app.innerHTML = `<aside class="sidebar ${state.treeOpen ? 'mobile-open' : ''}"><header class="brand"><span class="brand-mark">南</span><span><strong>南枫 AI</strong><small>本地工作台</small></span><button class="icon-button mobile-close" data-action="toggle-tree" aria-label="关闭导航">${icon(icons.close, '关闭')}</button></header><nav class="primary-nav"><button data-action="show-chat"><span>${icon(icons.conversation, '聊天')}</span>返回聊天</button><button class="nav-active"><span>${icon(icons.workspace, '工作区')}</span>工作区</button><button data-action="show-conversation"><span>${icon(icons.conversation, '会话')}</span>会话</button><button data-action="show-knowledge"><span>${icon(icons.knowledge, '知识')}</span>知识</button><button data-action="show-p8-inspect"><span>${icon(icons.info, '本地受控记录')}</span>本地受控记录</button></nav><section class="tree"><div class="tree-label"><span>Workspace</span><button class="icon-button" data-action="start-import" aria-label="导入">${icon(icons.import, '导入')}</button></div>${tree(data)}</section><footer><span class="offline-dot"></span>本地可用 · 联网需配置</footer></aside><section class="main-area"><header class="topbar"><button class="icon-button tree-toggle" data-action="toggle-tree" aria-label="打开导航">${icon(icons.workspace, '导航')}</button><div class="topbar-status"><span class="offline-dot"></span><span>${native ? 'Rust SQLite 本地所有权' : 'Web 预览（不写入）'}</span></div><div class="topbar-actions"><button data-action="undo" ${data && state.history.canUndo && native ? '' : 'disabled'}>${icon(icons.undo, '撤销')}撤销</button><button data-action="redo" ${data && state.history.canRedo && native ? '' : 'disabled'}>${icon(icons.redo, '重做')}重做</button><button data-action="start-export" ${data && native ? '' : 'disabled'}>${icon(icons.export, '导出')}导出</button><button data-action="toggle-scale" aria-label="切换应用缩放">${state.scale.toFixed(1)}×</button><button class="icon-button" data-action="about" aria-label="关于">${icon(icons.info, '关于')}</button><button class="icon-button" data-action="toggle-inspector" aria-label="${state.inspectorOpen ? '收起' : '展开'} Inspector">${icon(icons.knowledge, 'Inspector')}</button></div></header><p class="status ${state.error ? 'error' : ''}" role="status">${escape(state.error || state.status)}</p><div class="workbench ${state.inspectorOpen ? '' : 'inspector-collapsed'}">${canvas}${state.inspectorOpen ? inspector(data) : ''}</div></section>${dialog()}`; if (state.dialog) queueMicrotask(() => document.querySelector('.dialog input, .dialog textarea, .dialog select, .dialog button')?.focus()); }
async function refresh() { if (!native) { state.workspaces = [fixture.summary]; state.current = fixture; state.p6gCatalog = { revision: 0, snapshot: { catalogVersion: 'web-preview-unconfigured', policyVersion: 1, candidates: [] } }; state.p6gGlobalDefault = { revision: 0, tier: null }; state.p6gSelection = null; state.agentRuns = []; state.p6eAcceptance = { enabled: false, receipt: null }; if (!state.selectedConversationId) selectWorkspaceDefaultConversation(fixture); render(); return; } state.workspaces = await invoke('list_desktop_workspaces'); state.current = state.workspaces.length ? await invoke('read_desktop_workspace', { workspaceId: state.current?.summary?.id || state.workspaces[0].id }) : null; if (state.current && !resolveConversation(state.current, state.selectedConversationId)) selectWorkspaceDefaultConversation(state.current); state.p6gCatalog = await invoke('read_desktop_p6g_catalog'); state.p6gGlobalDefault = await invoke('read_desktop_p6g_global_default'); state.p6gSelection = state.current && state.selectedConversationId ? await invoke('read_desktop_p6g_selection', { workspaceId: state.current.summary.id, conversationId: state.selectedConversationId }) : null; state.history = state.current ? await invoke('read_desktop_workbench_history', { workspaceId: state.current.summary.id }) : { canUndo: false, canRedo: false, recycleBin: [], modelMetadata: [] }; state.agentRuns = await invoke('inspect_p8_agent_runs'); state.p6eAcceptance = await invoke('read_p6e_temporary_maintenance_acceptance_status'); if (state.p6eAcceptance.enabled && state.pane === 'chat') state.pane = 'connections'; render(); }

async function runP6eTemporaryMaintenanceAcceptance() {
  if (!state.p6eAcceptance.enabled) return;
  try {
    state.p6eAcceptance = { enabled: true, receipt: await invoke('run_p6e_temporary_maintenance_acceptance') };
    state.status = 'P6-E 固定 23h59 / 24h owner 维护已运行；回执保存在 acceptance 私有根目录，重启后可读。';
    state.error = '';
  } catch (error) {
    state.error = `P6-E 验收维护失败：${String(error)}`;
  }
  render();
}

app.addEventListener('click', event => {
  if (event.target.closest('[data-action]')?.dataset.action !== 'run-p6e-temporary-maintenance-acceptance') return;
  event.preventDefault();
  event.stopImmediatePropagation();
  void runP6eTemporaryMaintenanceAcceptance();
}, true);
async function pickExchange() { if (!native) { state.status = 'Web 预览不会请求文件；请在 Tauri Desktop 开发包中使用系统 picker。'; render(); return; } const selectedPath = await dialogInvoke('open', { multiple: false, directory: false, filters: [{ name: '南枫 AI 交换包', extensions: ['nfai-exchange'] }] }); if (!selectedPath) return; try { state.preflight = await invoke('stage_preflight_selected_exchange', { selectedPath }); state.current = await invoke('import_staged_exchange_as_new_workspace', { args: { stagingId: state.preflight.stagingId, workspaceTitle: '导入工作区' } }); state.preflight = null; state.dialog = null; state.status = '交换包已通过严格预检并直接导入为新的独立工作区。'; state.error = ''; await refresh(); } catch (error) { state.error = `导入被拒绝：${String(error)}`; } render(); }
function p6hImportLifecycleMarker(phase) { if (!p6hDiagnosticsEnabled) return; let marker = document.querySelector('#p6h-dom-event-marker'); if (!marker) { marker = document.createElement('p'); marker.id = 'p6h-dom-event-marker'; marker.setAttribute('role', 'status'); marker.style.cssText = 'position:fixed;z-index:9999;left:12px;bottom:12px;padding:6px;background:#173;color:#fff;font-size:12px'; document.body.append(marker); } marker.textContent = `P6-H acceptance import: ${phase}`; }
async function commitSelectedImport({ taskKey, stageCommand, readCommand, confirmCommand, selectedPath, label }) {
  if (!state.current) throw new Error('请先导入或创建一个本地工作区。');
  const staged = await invoke(stageCommand, { selectedPath });
  let task = await invoke(readCommand, { taskId: staged.id });
  let rejected = 0;
  for (const item of task.items.filter(candidate => candidate.status === 'PENDING_CONFIRMATION')) {
    try {
      await invoke(confirmCommand, { args: { workspaceId: state.current.summary.id, taskId: task.id, itemId: item.id } });
    } catch {
      // The item stays independently recoverable in its persisted task; selection remains one command.
      rejected += 1;
    }
  }
  task = await invoke(readCommand, { taskId: staged.id });
  state[taskKey] = task;
  state.dialog = null;
  if (state.current) await refresh();
  state.status = `${label} 已直接执行导入；失败或拒绝 ${rejected} 项可在回执中查看。`;
  state.error = '';
  return task;
}
async function commitPendingImport({ taskKey, readCommand, confirmCommand, label }) {
  if (!state.current || !state[taskKey]) throw new Error('需要一个本地工作区和可恢复的导入任务。');
  let task = await invoke(readCommand, { taskId: state[taskKey].id });
  let rejected = 0;
  for (const item of task.items.filter(candidate => candidate.status === 'PENDING_CONFIRMATION')) {
    try { await invoke(confirmCommand, { args: { workspaceId: state.current.summary.id, taskId: task.id, itemId: item.id } }); } catch { rejected += 1; }
  }
  task = await invoke(readCommand, { taskId: task.id });
  state[taskKey] = task;
  await refresh();
  state.status = `${label} 已直接续作；失败或拒绝 ${rejected} 项保留独立回执。`;
  state.error = '';
}
async function pickChatGptExport() { if (!native) { state.status = 'Web 预览不会请求文件；请在 Tauri Desktop 开发包中使用系统 picker。'; render(); return; } p6hImportLifecycleMarker('picker-open'); const selectedPath = await dialogInvoke('open', { multiple: false, directory: false, filters: [{ name: 'ChatGPT conversations.json', extensions: ['json'] }] }); if (!selectedPath) { p6hImportLifecycleMarker('picker-cancel'); return; } p6hImportLifecycleMarker('picker-returned'); try { await commitSelectedImport({ taskKey: 'chatgptTask', stageCommand: 'stage_chatgpt_export_selected', readCommand: 'read_chatgpt_import_task', confirmCommand: 'confirm_chatgpt_import_item', selectedPath, label: 'ChatGPT' }); p6hImportLifecycleMarker('direct-commit-succeeded'); } catch (error) { p6hImportLifecycleMarker('stage-error'); state.error = `ChatGPT 导入被拒绝：${String(error)}`; state.status = state.error; } render(); }
async function pickClaudeExport() { if (!native) { state.status = 'Web 预览不会请求文件；请在 Tauri Desktop 开发包中使用系统 picker。'; render(); return; } const selectedPath = await dialogInvoke('open', { multiple: false, directory: false, filters: [{ name: 'Claude conversations.json', extensions: ['json'] }] }); if (!selectedPath) return; try { await commitSelectedImport({ taskKey: 'claudeTask', stageCommand: 'stage_claude_export_selected', readCommand: 'read_claude_import_task', confirmCommand: 'confirm_claude_import_item', selectedPath, label: 'Claude' }); } catch (error) { state.error = `Claude 导入被拒绝：${String(error)}`; state.status = state.error; } render(); }
async function pickP6kZip(provider) { if (!native) { state.status = 'Web 预览不会请求文件；请在 Tauri Desktop 开发包中使用系统 picker。'; render(); return; } if (!state.current) { state.error = '先导入或创建一个本地工作区。'; render(); return; } const selectedPath = await dialogInvoke('open', { multiple: false, directory: false, filters: [{ name: `${provider} data export ZIP`, extensions: ['zip'] }] }); if (!selectedPath) return; try { state.p6kTask = await invoke('stage_p6k_zip_import_selected', { args: { workspaceId: state.current.summary.id, provider, selectedPath } }); state.status = `${provider} ZIP 已完成私有校验并直接导入：${state.p6kTask.importedCount} 个会话；失败 ${state.p6kTask.failedCount}，未关联媒体 ${state.p6kTask.unmappedAssetCount}。`; state.error = ''; await refresh(); } catch (error) { state.error = `ZIP 导入被拒绝：${String(error)}`; state.status = state.error; } render(); }
async function pickNanfengKnowledgeExport() { if (!native) { state.status = 'Web 预览不会请求文件；请在 Tauri Desktop 开发包中使用系统 picker。'; render(); return; } const selectedPath = await dialogInvoke('open', { multiple: false, directory: false, filters: [{ name: '南枫知识库完整 JSON', extensions: ['json'] }] }); if (!selectedPath) return; try { await commitSelectedImport({ taskKey: 'nanfengKnowledgeTask', stageCommand: 'stage_nanfeng_knowledge_export_selected', readCommand: 'read_nanfeng_knowledge_import_task', confirmCommand: 'confirm_nanfeng_knowledge_import_item', selectedPath, label: '南枫知识库' }); } catch (error) { state.error = `知识库导入被拒绝：${String(error)}`; state.status = state.error; } render(); }
app.addEventListener('click', event => { const target = event.target.closest?.('[data-action="select-chatgpt-export"]'); if (!target) return; event.preventDefault(); event.stopImmediatePropagation(); pickChatGptExport(); }, true);
app.addEventListener('click', event => { const target = event.target.closest?.('[data-action="select-claude-export"]'); if (!target) return; event.preventDefault(); event.stopImmediatePropagation(); pickClaudeExport(); }, true);
app.addEventListener('click', event => { const target = event.target.closest?.('[data-action="select-p6k-chatgpt-zip"],[data-action="select-p6k-claude-zip"]'); if (!target) return; event.preventDefault(); event.stopImmediatePropagation(); pickP6kZip(target.dataset.action === 'select-p6k-chatgpt-zip' ? 'CHATGPT' : 'CLAUDE'); }, true);
app.addEventListener('click', async event => { const target = event.target.closest?.('[data-action="retry-p6k-zip"],[data-action="skip-p6k-zip-failures"],[data-action="delete-p6k-zip-batch"]'); if (!target || !state.p6kTask) return; event.preventDefault(); event.stopImmediatePropagation(); try { if (target.dataset.action === 'retry-p6k-zip') state.p6kTask = await invoke('retry_p6k_zip_import_task', { taskId: state.p6kTask.id }); else if (target.dataset.action === 'skip-p6k-zip-failures') state.p6kTask = await invoke('skip_p6k_zip_import_failures', { taskId: state.p6kTask.id }); else { await invoke('delete_p6k_zip_import_batch', { taskId: state.p6kTask.id }); state.p6kTask = null; } state.status = target.dataset.action === 'delete-p6k-zip-batch' ? '已软删除该导入批次并移除其回执；私有副本已清除。' : 'ZIP 导入状态已更新。'; state.error = ''; if (state.current) await refresh(); } catch (error) { state.error = `ZIP 导入状态未更新：${String(error)}`; } render(); }, true);
app.addEventListener('click', async event => { const target = event.target.closest?.('[data-action="select-p6k-manual-asset"],[data-action="select-p6k-manual-target"],[data-action="link-p6k-manual-asset"]'); if (!target || !state.p6kTask) return; event.preventDefault(); event.stopImmediatePropagation(); if (target.dataset.action === 'select-p6k-manual-asset') { p6kManualLink = { assetOrdinal: Number(target.dataset.assetOrdinal), conversationId: null, messageId: null }; globalThis.__nanfengP6kManualLink = p6kManualLink; render(); return; } if (target.dataset.action === 'select-p6k-manual-target') { p6kManualLink = { ...p6kManualLink, conversationId: target.dataset.conversationId, messageId: target.dataset.messageId }; globalThis.__nanfengP6kManualLink = p6kManualLink; render(); return; } if (p6kManualLink.assetOrdinal === null || !p6kManualLink.conversationId || !p6kManualLink.messageId || !state.current) return; try { state.p6kTask = await invoke('link_p6k_zip_manual_asset', { args: { taskId: state.p6kTask.id, assetOrdinal: p6kManualLink.assetOrdinal, workspaceId: state.current.summary.id, conversationId: p6kManualLink.conversationId, messageId: p6kManualLink.messageId } }); p6kManualLink = { assetOrdinal: null, conversationId: null, messageId: null }; globalThis.__nanfengP6kManualLink = p6kManualLink; state.status = '已按所选媒体与消息建立精确关联；附件现在只在该消息位置呈现。'; state.error = ''; await refresh(); } catch (error) { state.error = `媒体关联未完成：${String(error)}`; } render(); }, true);
app.addEventListener('click', event => { const target = event.target.closest?.('[data-action="select-nanfeng-knowledge-export"]'); if (!target) return; event.preventDefault(); event.stopImmediatePropagation(); pickNanfengKnowledgeExport(); }, true);
app.addEventListener('click', event => { const target = event.target.closest?.('[data-action="show-settings"]'); if (!target) return; event.preventDefault(); event.stopImmediatePropagation(); state.pane = 'settings'; state.settingsSection = 'data'; state.profileOpen = false; state.error = ''; render(); }, true);
app.addEventListener('click', event => { const target = event.target.closest?.('[data-action="select-settings-section"]'); if (!target) return; event.preventDefault(); event.stopImmediatePropagation(); state.settingsSection = target.dataset.section || 'data'; state.error = ''; render(); }, true);
app.addEventListener('click', event => { const target = event.target.closest?.('[data-action="confirm-import"]'); if (!target) return; event.preventDefault(); event.stopImmediatePropagation(); importPreflighted(); }, true);
app.addEventListener('click', event => { const target = event.target.closest?.('[data-action="resume-chatgpt-import"],[data-action="resume-claude-import"],[data-action="resume-nanfeng-knowledge-import"]'); if (!target) return; event.preventDefault(); event.stopImmediatePropagation(); state.dialog = null; state.status = '已保留历史导入回执；新的文件选择会直接执行，历史待处理项不会要求二次确认。'; state.error = ''; render(); }, true);
app.addEventListener('click', async event => { const target = event.target.closest?.('[data-action="retry-chatgpt-task"]'); if (!target || !state.chatgptTask) return; event.preventDefault(); event.stopImmediatePropagation(); try { await invoke('retry_chatgpt_import_task', { taskId: state.chatgptTask.id }); await commitPendingImport({ taskKey: 'chatgptTask', readCommand: 'read_chatgpt_import_task', confirmCommand: 'confirm_chatgpt_import_item', label: 'ChatGPT' }); } catch (error) { state.error = `ChatGPT 重试未完成：${String(error)}`; } render(); }, true);
async function importPreflighted() { try { state.current = await invoke('import_staged_exchange_as_new_workspace', { args: { stagingId: state.preflight.stagingId, workspaceTitle: document.querySelector('#workspace-title')?.value || '' } }); state.dialog = null; state.status = '导入完成：stable ID、revision、关系与资产 hash 已保真保存。'; await refresh(); } catch (error) { state.error = `导入失败：${String(error)}`; render(); } }
async function mutate(args, success) { if (!native || !state.current) return; try { await invoke('mutate_desktop_domain', { args: { ...args, intentId: intent('intent'), workspaceId: state.current.summary.id } }); state.status = success; state.error = ''; state.dialog = null; await refresh(); } catch (error) { state.error = `写入被拒绝：${String(error)}`; render(); } }
function saveProject() { const item = state.dialog.item; mutate({ entity: 'project', action: item ? 'update' : 'create', objectId: item?.id, expectedRevision: item?.revision, fields: { title: document.querySelector('#project-title')?.value || '', description: document.querySelector('#project-description')?.value || '' } }, item ? 'Project 已保存为新的本地 revision。' : 'Project 已创建；可撤销。'); }
function saveKnowledge() { const item = state.dialog.item; const tags = (document.querySelector('#knowledge-tags')?.value || '').split(',').map(value => value.trim()).filter(Boolean); const fields = { title: document.querySelector('#knowledge-title')?.value || '', body: document.querySelector('#knowledge-body')?.value || '', ...(item ? { tags } : { tags, scope: 'GLOBAL', projectId: null }) }; mutate({ entity: 'knowledge', action: item ? 'update' : 'create', objectId: item?.id, expectedRevision: item?.revision, fields }, item ? 'Knowledge 已保存为新的本地 revision。' : 'Knowledge 已创建；可撤销。'); }
function saveMemory() { const item = state.dialog.item; mutate({ entity: 'memory', action: item ? 'update' : 'create', objectId: item?.id, expectedRevision: item?.revision, fields: item ? { body: document.querySelector('#memory-body')?.value || '' } : { body: document.querySelector('#memory-body')?.value || '', scope: 'GLOBAL', scopeId: null } }, item ? 'Memory 已保存为新的本地 revision。' : 'Memory 已创建；可撤销。'); }
function saveRelation() { mutate({ entity: 'relation', action: 'create', fields: { fromId: document.querySelector('#relation-from')?.value || '', toId: document.querySelector('#relation-to')?.value || '', kind: document.querySelector('#relation-kind')?.value || '' } }, 'Knowledge relation 已显式建立；可撤销。'); }
async function undo(redo = false) { if (!native || !state.current) return; try { await invoke(redo ? 'redo_desktop_domain' : 'undo_desktop_domain', { args: { intentId: intent(redo ? 'redo' : 'undo'), workspaceId: state.current.summary.id } }); state.status = redo ? '已重做为新 revision。' : '已撤销为新 revision。'; state.error = ''; await refresh(); } catch (error) { state.error = `历史动作不可应用：${String(error)}`; render(); } }
async function saveMetadata() { if (!native || !state.current) return; try { const providerId = document.querySelector('#provider-id')?.value || ''; const modelId = document.querySelector('#model-id')?.value || ''; const existing = state.history.modelMetadata.find(item => item.providerId === providerId && item.modelId === modelId); await invoke('upsert_desktop_model_metadata', { args: { intentId: intent('metadata'), workspaceId: state.current.summary.id, expectedRevision: existing?.revision || 0, providerId, modelId, metadata: { capabilities: ['TEXT'], priceVersion: document.querySelector('#price-version')?.value || '', currency: document.querySelector('#price-currency')?.value || '', source: '用户手工本地配置', updatedAt: '2026-08-13T00:00:00Z', pricingStatus: 'MANUAL', safeUsage: { knownCostMicros: null } } } }); state.status = '本地 metadata 已保存；不是实时价格或真实费用。'; state.dialog = null; await refresh(); } catch (error) { state.error = `metadata 被拒绝：${String(error)}`; render(); } }
async function exportCurrent() { if (!native || !state.current) return; const selectedPath = await dialogInvoke('save', { defaultPath: `${state.current.summary.id}.nfai-exchange`, filters: [{ name: '南枫 AI 交换包', extensions: ['nfai-exchange'] }] }); if (!selectedPath) return; try { const receipt = await invoke('export_desktop_workspace_to_selected_path', { workspaceId: state.current.summary.id, selectedPath }); state.status = `导出完成并严格回读：${short(receipt.semanticHash)} · ${bytes(receipt.byteCount)}`; } catch (error) { state.error = `导出失败：${String(error)}`; } render(); }
async function saveLocalMessage() { const text = state.composerDraft.trim(); if (!text) { state.error = '请输入非空内容。'; render(); return; } if (!native || !state.current) { state.error = native ? '先导入一个本地工作区。' : 'Web 预览不会写入 Desktop SQLite。'; render(); return; } const conversation = resolveConversation(state.current, state.selectedConversationId); try { if (conversation) { await invoke('mutate_desktop_domain', { args: { intentId: intent('chat-message'), workspaceId: state.current.summary.id, entity: 'conversation', action: 'appendMessage', objectId: conversation.id, expectedRevision: conversation.revision, fields: { text, role: 'user' } } }); } else { const receipt = await invoke('mutate_desktop_domain', { args: { intentId: intent('chat-create'), workspaceId: state.current.summary.id, entity: 'conversation', action: 'create', fields: { title: text.replace(/\s+/g, ' ').slice(0, 36), projectId: null, firstMessage: text } } }); state.selectedConversationId = receipt.objectId; } state.composerDraft = ''; state.error = ''; state.status = '已保存为本地记录；没有调用模型。'; await refresh(); } catch (error) { state.error = `本地保存被拒绝：${String(error)}`; render(); } }
app.addEventListener('click', async event => { const target = event.target.closest('[data-action]'); const action = target?.dataset.action; if (!action) return; const data = workspace(); if (action === 'new-chat') { state.pane = 'chat'; state.selectedConversationId = null; state.composerDraft = ''; state.profileOpen = false; state.error = ''; state.status = '新对话将先保存到本地；联网回答需另行配置并确认。'; render(); } else if (action === 'select-chat') { state.pane = 'chat'; state.selectedConversationId = target.dataset.id; state.profileOpen = false; state.error = ''; render(); } else if (action === 'show-chat') { state.pane = 'chat'; state.profileOpen = false; render(); } else if (action === 'show-work') { state.pane = 'knowledge'; state.profileOpen = false; render(); } else if (action === 'show-connections') { state.pane = 'connections'; state.profileOpen = false; render(); } else if (action === 'toggle-profile') { state.profileOpen = !state.profileOpen; render(); } else if (action === 'save-local-message') saveLocalMessage(); else if (action === 'start-import') pickExchange(); else if (action === 'confirm-import') importPreflighted(); else if (action === 'close-dialog') { state.dialog = null; render(); } else if (action === 'select-workspace') { state.current = await invoke('read_desktop_workspace', { workspaceId: target.dataset.id }); state.history = await invoke('read_desktop_workbench_history', { workspaceId: target.dataset.id }); state.selectedConversationId = null; state.status = '已切换到独立本地工作区。'; state.error = ''; render(); } else if (action === 'show-conversation') { state.pane = 'conversation'; state.treeOpen = false; render(); } else if (action === 'show-knowledge') { state.pane = 'knowledge'; state.treeOpen = false; render(); } else if (action === 'toggle-inspector') { state.inspectorOpen = !state.inspectorOpen; render(); } else if (action === 'toggle-tree') { state.treeOpen = !state.treeOpen; render(); } else if (action === 'toggle-scale') { state.scale = state.scale === 1 ? 2 : 1; state.status = `应用缩放已切换为 ${state.scale.toFixed(1)}×。`; render(); } else if (action === 'about') { state.dialog = 'about'; render(); } else if (action === 'new-project') { state.dialog = { kind: 'project', item: null }; render(); } else if (action === 'edit-project') { state.dialog = { kind: 'project', item: data.exchange.projects.find(item => item.id === target.dataset.id) }; render(); } else if (action === 'save-project') saveProject(); else if (action === 'new-knowledge') { state.dialog = { kind: 'knowledge', item: null }; render(); } else if (action === 'edit-knowledge') { state.dialog = { kind: 'knowledge', item: data.exchange.knowledge.find(item => item.id === target.dataset.id) }; render(); } else if (action === 'save-knowledge') saveKnowledge(); else if (action === 'new-memory') { state.dialog = { kind: 'memory', item: null }; render(); } else if (action === 'edit-memory') { state.dialog = { kind: 'memory', item: data.exchange.memory.find(item => item.id === target.dataset.id) }; render(); } else if (action === 'save-memory') saveMemory(); else if (action === 'new-relation') { state.dialog = { kind: 'relation' }; render(); } else if (action === 'save-relation') saveRelation(); else if (action === 'delete-knowledge') mutate({ entity: 'knowledge', action: 'softDelete', objectId: target.dataset.id, expectedRevision: Number(target.dataset.revision), fields: {} }, 'Knowledge 已软删除；可在回收站恢复。'); else if (action === 'delete-memory') mutate({ entity: 'memory', action: 'softDelete', objectId: target.dataset.id, expectedRevision: Number(target.dataset.revision), fields: {} }, 'Memory 已软删除；可在回收站恢复。'); else if (action === 'delete-relation') mutate({ entity: 'relation', action: 'softDelete', objectId: target.dataset.id, expectedRevision: Number(target.dataset.revision), fields: {} }, 'relation 已撤销；可在回收站恢复。'); else if (action === 'restore') mutate({ entity: target.dataset.entity, action: 'restore', objectId: target.dataset.id, expectedRevision: Number(target.dataset.revision), fields: {} }, '对象已恢复为新的 revision。'); else if (action === 'undo') undo(); else if (action === 'redo') undo(true); else if (action === 'recycle') { state.dialog = 'recycle'; render(); } else if (action === 'metadata') { state.dialog = 'metadata'; render(); } else if (action === 'save-metadata') saveMetadata(); else if (action === 'start-export') exportCurrent(); });
app.addEventListener('click', event => {
  const action = event.target.closest('[data-action]')?.dataset.action;
  if (action === 'show-work') {
    state.status = '已选择 LOCAL_OFFLINE / LOCAL_ONLY：继续本地工作；没有调用模型或同步。';
    state.error = '';
    render();
  } else if (action === 'show-online-guidance') {
    state.pane = 'settings';
    state.status = 'ONLINE_PROVIDER 未启动：需要单独完成配置、模型选择、费用确认与本次外发同意；本轮没有读取 Key 或发出请求。';
    state.error = '';
    render();
  }
});
app.addEventListener('input', event => { if (event.target.id === 'chat-composer') { state.composerDraft = event.target.value; const send = document.querySelector('[data-action="save-local-message"]'); const attachmentCount = state.temporaryConversation ? (state.temporaryConversation.draftAttachmentIds || []).length : state.composerAttachments.length; if (send) send.disabled = (!state.composerDraft.trim() && !attachmentCount) || !native || (!state.current && !state.temporaryConversation); } else if (event.target.id === 'settings-search') { state.settingsSearch = event.target.value; render(); } });
function applyTranscriptRailPosition(rail, target, { emphasize = false } = {}) {
  const currentSlot = Number(target?.dataset.railSlot);
  if (!rail || !Number.isInteger(currentSlot)) return;
  rail.querySelectorAll('button[data-rail-slot]').forEach(item => {
    const distance = Math.abs(Number(item.dataset.railSlot) - currentSlot);
    if (emphasize) item.dataset.railDistance = String(Math.min(6, distance));
    else delete item.dataset.railDistance;
    item.classList.toggle('is-active', emphasize && item === target);
    if (item === target) item.setAttribute('aria-current', 'true');
    else item.removeAttribute('aria-current');
  });
}
function clearTranscriptRailEmphasis(rail) {
  rail?.querySelectorAll('button[data-rail-slot]').forEach(item => {
    delete item.dataset.railDistance;
    item.classList.remove('is-active');
  });
}
function syncTranscriptRailToScroll(scroll) {
  const rail = scroll.parentElement?.querySelector('.chat-transcript-rail');
  const messages = [...scroll.querySelectorAll('.chat-message')];
  if (!rail || !messages.length) return;
  const viewportCenter = scroll.scrollTop + scroll.clientHeight / 2;
  const nearestIndex = messages.reduce((best, message, index) => Math.abs(message.offsetTop + message.offsetHeight / 2 - viewportCenter) < Math.abs(messages[best].offsetTop + messages[best].offsetHeight / 2 - viewportCenter) ? index : best, 0);
  const target = [...rail.querySelectorAll('button[data-index]')].reduce((best, item) => !best || Math.abs(Number(item.dataset.index) - nearestIndex) < Math.abs(Number(best.dataset.index) - nearestIndex) ? item : best, null);
  applyTranscriptRailPosition(rail, target);
}
app.addEventListener('pointerover', event => {
  const target = event.target.closest?.('[data-action="jump-transcript-position"]');
  if (target) applyTranscriptRailPosition(target.closest('.chat-transcript-rail'), target, { emphasize: true });
});
app.addEventListener('pointerout', event => {
  const rail = event.target.closest?.('.chat-transcript-rail');
  if (rail && !rail.contains(event.relatedTarget)) clearTranscriptRailEmphasis(rail);
});
app.addEventListener('focusin', event => {
  const target = event.target.closest?.('[data-action="jump-transcript-position"]');
  if (target) applyTranscriptRailPosition(target.closest('.chat-transcript-rail'), target, { emphasize: true });
});
app.addEventListener('focusout', event => {
  const rail = event.target.closest?.('.chat-transcript-rail');
  if (rail && !rail.contains(event.relatedTarget)) clearTranscriptRailEmphasis(rail);
});
app.addEventListener('scroll', event => {
  const scroll = event.target.closest?.('.chat-scroll[data-conversation-id]');
  if (!scroll) return;
  if (scroll.scrollTop > 1 || state.transcriptRailTrackingConversationId === scroll.dataset.conversationId) {
    state.transcriptRailTrackingConversationId = scroll.dataset.conversationId;
    syncTranscriptRailToScroll(scroll);
  }
  const atLatest = scroll.scrollHeight - scroll.scrollTop - scroll.clientHeight <= 24;
  if (state.scrollToLatestAnimationId === scroll.dataset.conversationId) {
    if (atLatest) {
      state.scrollToLatestAnimationId = null;
      state.chatScrollPositions.set(scroll.dataset.conversationId, scroll.scrollTop);
    }
    return;
  }
  if (atLatest !== state.chatAtLatest) {
    state.chatAtLatest = atLatest;
    render();
  }
}, true);

let sidebarDragPointerId = null;
function updateSidebarWidth(width) {
  state.sidebarWidth = clampDesktopSidebarWidth(width, window.innerWidth);
  persistSidebarWidth(state.sidebarWidth);
  app.style.setProperty('--chat-sidebar-width', `${state.sidebarWidth}px`);
  const divider = document.querySelector('.chat-sidebar-divider');
  divider?.setAttribute('aria-valuenow', String(state.sidebarWidth));
}
app.addEventListener('pointerdown', event => {
  const divider = event.target.closest?.('.chat-sidebar-divider');
  if (!divider || window.innerWidth <= 900) return;
  // WebKit does not consistently focus a tabindex separator after a pointer capture.
  // Keep pointer and keyboard adjustment on this single, visible control.
  divider.focus({ preventScroll: true });
  sidebarDragPointerId = event.pointerId;
  divider.setPointerCapture?.(event.pointerId);
  divider.classList.add('dragging');
  event.preventDefault();
});
app.addEventListener('pointermove', event => { if (sidebarDragPointerId === event.pointerId) updateSidebarWidth(event.clientX); });
app.addEventListener('pointerup', event => { if (sidebarDragPointerId !== event.pointerId) return; sidebarDragPointerId = null; document.querySelector('.chat-sidebar-divider')?.classList.remove('dragging'); });
app.addEventListener('dblclick', event => { if (!event.target.closest?.('.chat-sidebar-divider')) return; updateSidebarWidth(256); render(); });
app.addEventListener('keydown', event => {
  const divider = event.target.closest?.('.chat-sidebar-divider');
  if (!divider || window.innerWidth <= 900) return;
  const step = event.shiftKey ? 32 : 12;
  if (event.key === 'ArrowLeft') updateSidebarWidth(state.sidebarWidth - step);
  else if (event.key === 'ArrowRight') updateSidebarWidth(state.sidebarWidth + step);
  else if (event.key === 'Home') updateSidebarWidth(220);
  else if (event.key === 'End') updateSidebarWidth(440);
  else return;
  event.preventDefault();
});
app.addEventListener('click', event => {
  if (event.target.closest('[data-action]')?.dataset.action !== 'scroll-to-latest') return;
  const scroll = document.querySelector('.chat-scroll[data-conversation-id]');
  if (!scroll) return;
  state.pendingChatScrollToLatestId = scroll.dataset.conversationId;
  // The replacement scroll owner can emit an initial at-top event before
  // restoreChatScroll starts its smooth jump; reserve the owner first.
  state.scrollToLatestAnimationId = scroll.dataset.conversationId;
  state.focusComposerAfterScrollToLatest = true;
  state.chatAtLatest = true;
  scroll.scrollTo({ top: scroll.scrollHeight, behavior: 'smooth' });
  render();
});
app.addEventListener('click', event => {
  const target = event.target.closest?.('[data-action="jump-transcript-position"]');
  if (!target) return;
  const scroll = document.querySelector('.chat-scroll[data-conversation-id]');
  const index = Number(target.dataset.index);
  if (!scroll || !Number.isInteger(index)) return;
  const rail = target.closest('.chat-transcript-rail');
  state.transcriptRailTrackingConversationId = scroll.dataset.conversationId;
  applyTranscriptRailPosition(rail, target);
  const message = scroll.querySelectorAll('.chat-message')[index];
  message?.scrollIntoView({ block: 'center', behavior: 'smooth' });
  // Pointer activation must leave only the compact current-position line;
  // retain focus for keyboard activation so its preview remains discoverable.
  if (event.detail === 0) target.focus({ preventScroll: true });
  else target.blur();
});
function openConversationContextMenu(row) {
  // The render below replaces this row. Keep only its identity, then measure
  // the replacement title after the new root DOM exists.
  openTransientOverlay('context', row, { id: row.dataset.id, revision: Number(row.dataset.revision), pinned: row.dataset.pinned === 'true', archived: row.dataset.archived === 'true' });
}

function positionConversationContextMenu() {
  const menu = document.querySelector('.chat-context-menu');
  if (!menu || !state.contextMenu) return;
  const row = [...document.querySelectorAll('[data-conversation-row]')].find(item => item.dataset.id === state.contextMenu.id);
  if (!row) return;
  // The title, rather than date/action controls or a stale pre-render row,
  // is the only visual anchor for a conversation menu.
  const anchor = row.querySelector('.chat-history-select > span')?.getBoundingClientRect() ?? row.getBoundingClientRect();
  const sidebar = row.closest('.chat-sidebar')?.getBoundingClientRect();
  const appBounds = app.getBoundingClientRect();
  const viewportWidth = Math.max(window.innerWidth || 0, document.documentElement.clientWidth || 0, Math.ceil(appBounds.right));
  const viewportHeight = Math.max(window.innerHeight || 0, document.documentElement.clientHeight || 0, Math.ceil(appBounds.bottom));
  const menuBounds = menu.getBoundingClientRect();
  const position = resolveConversationMenuAnchor(anchor, {
    viewportWidth,
    viewportHeight,
    sidebarRect: sidebar,
    menuWidth: menuBounds.width || 192,
    menuHeight: menuBounds.height || 204,
  });
  menu.style.left = `${position.x}px`;
  menu.style.top = `${position.y}px`;
  menu.dataset.positioned = 'true';
}
function closeConversationContextMenu() {
  if (!state.contextMenu) return;
  state.contextMenu = null;
  render();
}
document.addEventListener('pointerdown', event => {
  if (event.target.closest?.('.dialog, .chat-context-menu, .chat-profile-menu, .composer-add-popover, .p6g-model-popover, [data-overlay-trigger]')) return;
  if (state.videoPreview) { void closeVideoPreview(); return; }
  if (state.audioPreview) { void closeAudioPreview(); return; }
  if (state.textPreview) { state.textPreview = null; render(); return; }
  closeTopOverlay();
}, true);
document.addEventListener('keydown', event => { if (event.key === 'Escape' && state.videoPreview) { event.preventDefault(); void closeVideoPreview(); } else if (event.key === 'Escape' && state.audioPreview) { event.preventDefault(); void closeAudioPreview(); } else if (event.key === 'Escape' && state.textPreview) { event.preventDefault(); state.textPreview = null; render(); } else if (event.key === 'Escape' && closeTopOverlay()) event.preventDefault(); }, true);
window.addEventListener('keydown', event => { const modifier = event.metaKey || event.ctrlKey; const editing = ['INPUT', 'TEXTAREA', 'SELECT'].includes(document.activeElement?.tagName); const row = event.target.closest?.('[data-conversation-row]'); if ((event.key === 'ContextMenu' || (event.shiftKey && event.key === 'F10')) && row) { event.preventDefault(); openConversationContextMenu(row); } else if (event.target.id === 'chat-composer' && event.key === 'Enter' && !event.shiftKey && !event.isComposing) { event.preventDefault(); saveLocalMessage(); } else if (modifier && event.key.toLowerCase() === 's' && state.dialog?.kind) { event.preventDefault(); if (state.dialog.kind === 'project') saveProject(); else if (state.dialog.kind === 'knowledge') saveKnowledge(); else if (state.dialog.kind === 'memory') saveMemory(); } else if (modifier && event.key.toLowerCase() === 'z' && !editing) { event.preventDefault(); undo(event.shiftKey); } else if (modifier && event.key.toLowerCase() === 'o') { event.preventDefault(); pickExchange(); } else if (modifier && event.key.toLowerCase() === 'e') { event.preventDefault(); exportCurrent(); } else if (modifier && event.key === '\\') { event.preventDefault(); state.inspectorOpen = !state.inspectorOpen; render(); } else if (event.key === 'Escape' && (state.contextMenu || state.dialog || state.treeOpen || state.profileOpen || state.sidebarOpen)) { state.contextMenu = null; state.dialog = null; state.treeOpen = false; state.profileOpen = false; state.sidebarOpen = false; render(); } });
window.addEventListener('resize', () => { if (state.contextMenu) { state.contextMenu = null; render(); } });
window.addEventListener('scroll', () => { if (state.contextMenu) { state.contextMenu = null; render(); } }, true);
restoreTemporaryChat().then(refresh).catch(error => { state.error = `无法打开本地工作区：${String(error)}`; render(); });
app.addEventListener('click', event => {
  if (event.target.closest('[data-action]')?.dataset.action !== 'show-p8-inspect') return;
  state.pane = 'p8-inspect';
  state.treeOpen = false;
  state.status = '已读取本机 P8 安全账本 metadata；无 production executor。';
  render();
});

app.addEventListener('click', async event => {
  const target = event.target.closest('[data-action="select-workspace"]');
  if (!target || !native) return;
  event.stopImmediatePropagation();
  state.current = await invoke('read_desktop_workspace', { workspaceId: target.dataset.id });
  state.history = await invoke('read_desktop_workbench_history', { workspaceId: target.dataset.id });
  state.pane = 'work';
  selectWorkspaceDefaultConversation(state.current);
  state.status = '已切换到独立本地工作区，并打开该工作区最近对话。';
  state.error = '';
  render();
}, true);

function connectionCanvas() { const item = state.connection || previewConnection; return `<section class="canvas knowledge-canvas"><div class="canvas-header"><div><p class="overline">双路径 · 状态与配置边界</p><h1>连接与数据路径</h1><p>本地离线是安全底座；Provider 与加密同步必须分别配置、分别同意。</p></div></div><section class="knowledge-grid"><section><h2>本地离线</h2><p><b>LOCAL_OFFLINE / LOCAL_ONLY</b></p><p>始终可用。断网、取消或联网失败时，本地工作不丢失。</p></section><section><h2>联网模型</h2><p>配置：<b>${escape(item.providerConfiguration)}</b></p><p>Key presence：<b>${escape(item.credentialPresence)}</b>（仅存在性，不读取或显示 Key）</p><p>目录：${escape(item.catalogFreshness)} · consent：${escape(item.egressConsent)}</p><p>当前未配置或未验证，联网请求不会被标为本地成功。</p></section><section class="memory-rail"><h2>账号同步</h2><p><b>ENCRYPTED_SYNC</b> · ${escape(item.syncCapability)}</p><p>同步与 Provider 独立；不会因模型设置自动上传本地数据。</p><p>当前原因：${item.degradedReasons.map(escape).join(' · ')}</p></section></section></section>`; }

if (native) invoke('read_dual_path_status').then(value => { state.connection = value; render(); }).catch(() => {});

function workHomeCanvas(data) {
  if (!data) return `<section class="canvas empty-canvas"><p class="overline">本地工作</p><h1>先导入一个工作区</h1><p>工作模式仍在同一浅色外壳中运行；导入不会合并或覆盖既有本地数据。</p><button class="primary" data-action="start-import">选择交换包</button></section>`;
  return `<section class="canvas work-home-canvas"><div class="canvas-header"><div><p class="overline">本地工作区</p><h1>${escape(data.summary.title)}</h1><p>项目、知识、记忆和本地受控记录都在这里管理；没有 Provider、同步或隐式外发。</p></div><button class="primary" data-action="start-import">导入工作区</button></div><div class="work-summary-grid"><article><span>项目</span><strong>${data.summary.projectCount}</strong><button data-action="show-projects">管理项目</button></article><article><span>知识</span><strong>${data.summary.knowledgeCount}</strong><button data-action="show-knowledge">打开知识</button></article><article><span>记忆</span><strong>${data.summary.memoryCount}</strong><button data-action="show-memory">打开记忆</button></article></div></section>`;
}

function projectsCanvas(data) {
  const projects = data?.exchange?.projects ?? [];
  return `<section class="canvas work-projects-canvas"><div class="canvas-header"><div><p class="overline">项目</p><h1>本地项目</h1><p>项目指令、会话归属与知识范围继续由现有 Rust 本地领域链拥有。</p></div><button class="primary" data-action="new-project">新建 Project</button></div><div class="knowledge-list">${projects.map(item => `<article class="knowledge-item"><header><div><p class="overline">${item.archived ? 'ARCHIVED' : 'ACTIVE'} · r${item.revision}</p><h2>${escape(item.title)}</h2></div></header><p>${escape(item.description || '暂无说明。')}</p><div class="item-actions"><button data-action="edit-project" data-id="${escape(item.id)}">编辑</button></div></article>`).join('') || '<p class="empty-copy">暂无本地 Project。</p>'}</div></section>`;
}

function knowledgeOnlyCanvas(data) {
  const knowledge = active(data || { exchange: { knowledge: [] } }, 'knowledge');
  const relations = active(data || { exchange: { relations: [] } }, 'relations');
  const name = id => data?.exchange?.knowledge?.find(item => item.id === id)?.title || short(id);
  return `<section class="canvas work-knowledge-canvas"><div class="canvas-header"><div><p class="overline">知识</p><h1>本地知识</h1><p>知识编辑、关系、撤销与软删除继续由 Rust SQLite 真值驱动；记忆在左侧“记忆”入口单独管理。</p></div><div class="canvas-actions"><button class="primary" data-action="new-knowledge">新建 Knowledge</button><button data-action="new-relation" ${knowledge.length < 2 ? 'disabled' : ''}>建立关系</button></div></div><div class="knowledge-list">${knowledge.map(item => `<article class="knowledge-item"><header><div><p class="overline">${escape(item.status)} · r${item.revision} · ${escape(item.classification)}</p><h2>${escape(item.title)}</h2></div><span>${escape((item.tags || []).join(' · '))}</span></header><pre>${escape(item.body)}</pre><div class="item-actions"><button data-action="edit-knowledge" data-id="${escape(item.id)}">编辑</button><button data-action="delete-knowledge" data-id="${escape(item.id)}" data-revision="${item.revision}">软删除</button></div></article>`).join('') || '<p class="empty-copy">暂无活动 Knowledge。</p>'}</div><section class="memory-rail"><div class="section-head"><h2>Knowledge relation</h2><button data-action="new-relation" ${knowledge.length < 2 ? 'disabled' : ''}>建立</button></div><ul>${relations.map(item => `<li><span>${escape(item.kind)} · r${item.revision}</span>${escape(name(item.fromId))} → ${escape(name(item.toId))}<button data-action="delete-relation" data-id="${escape(item.id)}" data-revision="${item.revision}">撤销</button></li>`).join('') || '<li>暂无活动 relation。</li>'}</ul></section></section>`;
}

function memoryCanvas(data) {
  const memory = active(data || { exchange: { memory: [] } }, 'memory');
  return `<section class="canvas work-memory-canvas"><div class="canvas-header"><div><p class="overline">记忆</p><h1>本地 Memory</h1><p>仅用户明确创建、编辑、暂停或软删除；不会自动进入对话上下文或联网请求。</p></div><button class="primary" data-action="new-memory">新建 Memory</button></div><section class="memory-rail"><ul>${memory.map(item => `<li><span>${escape(item.scope)} · r${item.revision}</span><button class="memory-button" data-action="edit-memory" data-id="${escape(item.id)}">${escape(item.body)}</button><button data-action="delete-memory" data-id="${escape(item.id)}" data-revision="${item.revision}">软删除</button></li>`).join('') || '<li>暂无活动 Memory。</li>'}</ul></section></section>`;
}

function rememberChatScroll() {
  const scroll = document.querySelector('.chat-scroll[data-conversation-id]');
  const conversationId = scroll?.dataset.conversationId;
  if (conversationId) {
    if (state.pendingChatScrollToLatestId === conversationId || state.pendingChatSendScrollToLatestId === conversationId) return;
    state.chatScrollPositions.set(conversationId, scroll.scrollTop);
    state.chatAtLatest = scroll.scrollHeight - scroll.scrollTop - scroll.clientHeight <= 24;
  }
}

function restoreChatScroll() {
  const conversation = resolveConversation(workspace(), state.selectedConversationId);
  const scroll = document.querySelector('.chat-scroll[data-conversation-id]');
  if (conversation && scroll?.dataset.conversationId === conversation.id) {
    const restoreSubmittedLatest = state.pendingChatSendScrollToLatestId === conversation.id;
    const restoreLatest = state.pendingChatScrollToLatestId === conversation.id;
    if (restoreSubmittedLatest) {
      // A successful Composer send always returns the transcript directly to its
      // newest message, even when the user had been inspecting older history.
      scroll.scrollTop = scroll.scrollHeight;
      state.chatScrollPositions.set(conversation.id, scroll.scrollHeight);
      state.chatAtLatest = true;
      state.pendingChatSendScrollToLatestId = null;
    } else if (restoreLatest) {
      state.scrollToLatestAnimationId = conversation.id;
      scroll.scrollTo({ top: scroll.scrollHeight, behavior: 'smooth' });
      state.chatScrollPositions.set(conversation.id, scroll.scrollHeight);
      state.pendingChatScrollToLatestId = null;
    } else {
      scroll.scrollTop = state.chatScrollPositions.get(conversation.id) || 0;
    }
    if (state.focusComposerAfterScrollToLatest) {
      state.focusComposerAfterScrollToLatest = false;
      document.querySelector('#chat-composer')?.focus({ preventScroll: true });
    }
  }
}

function renderUnified() {
  rememberChatScroll();
  const data = workspace();
  const visiblePane = state.p6eAcceptance.enabled ? 'settings' : state.pane;
  document.title = state.p6eAcceptance.enabled ? '南枫 AI Desktop · P6-E 验收' : '南枫 AI Desktop';
  if (state.pane === 'chat' && !state.temporaryConversation && !state.composerDraft) state.composerDraft = readChatDraft();
  if (state.pane === 'chat' && !state.composerAttachments.length) state.composerAttachments = readComposerAttachments();
  const workPanes = new Set(['work', 'projects', 'knowledge', 'memory', 'p8-inspect']);
  // Work is a scope of the selected conversation, not a dashboard.  Let the shared
  // chat shell render its transcript and Composer; only the selected mode changes.
  const workPanel = state.pane === 'projects' ? projectsCanvas(data)
    : state.pane === 'knowledge' ? knowledgeOnlyCanvas(data)
        : state.pane === 'memory' ? memoryCanvas(data)
          : state.pane === 'p8-inspect' ? p8InspectCanvas({ agentRuns: state.agentRuns, native, escape, short })
            : null;
  app.className = `app-shell chat-first ${visiblePane === 'settings' ? 'settings-mode' : ''} ${workPanes.has(state.pane) ? 'work-mode' : ''} ${state.sidebarOpen ? 'compact-sidebar-open' : ''}`;
  app.innerHTML = renderChatFirstShell({ data, native, selectedConversationId: state.selectedConversationId, composerDraft: state.composerDraft, composerAttachments: state.composerAttachments, temporaryConversation: state.temporaryConversation, chatSearch: state.chatSearch, searchResults: state.searchResults, searchPanel: state.searchPanel, searchHistory: state.searchHistory, searchHistoryOpen: state.searchHistoryOpen, profileOpen: state.profileOpen, sidebarOpen: state.sidebarOpen, railCollapsed: state.railCollapsed, sidebarWidth: state.sidebarWidth, settingsSection: state.settingsSection, settingsSearch: state.settingsSearch, showArchived: state.showArchived, showDeleted: state.showDeleted, contextMenu: state.contextMenu, composerAddOpen: state.composerAddOpen, temporaryModelOpen: state.temporaryModelOpen, p6gModelPickerOpen: state.p6gModelPickerOpen, p6gCatalog: state.p6gCatalog, p6gGlobalDefault: state.p6gGlobalDefault, p6gSelection: state.p6gSelection, p6kTask: state.p6kTask, workMode: workPanes.has(visiblePane), workspaces: state.workspaces, workPanel, pane: visiblePane, status: state.status, error: state.error, connection: state.connection, p6eAcceptance: state.p6eAcceptance, imageThumbnails: state.imageThumbnails, showScrollToLatest: !state.chatAtLatest });
  app.style.setProperty('--chat-sidebar-width', `${state.sidebarWidth}px`);
  positionConversationContextMenu();
  const modal = dialog() || imagePreviewDialog() || pdfPreviewDialog() || videoPreviewDialog() || audioPreviewDialog() || textPreviewDialog();
  if (modal) app.insertAdjacentHTML('beforeend', modal);
  if (state.dialog?.kind === 'chatgpt-import' && !document.querySelector('[data-action="retry-chatgpt-task"]')) {
    document.querySelector('.dialog-actions')?.insertAdjacentHTML('afterbegin', '<button data-action="retry-chatgpt-task">重试解析</button>');
  }
  queueMicrotask(() => {
    restoreChatScroll();
    if (state.dialog) document.querySelector('.dialog input, .dialog textarea, .dialog select, .dialog button')?.focus();
    scheduleImageThumbnailReads();
  });
}

function imagePreviewDialog() {
  const preview = state.imagePreview;
  if (!preview) return '';
  const zoom = Math.max(1, Math.min(4, Number(preview.zoom) || 1));
  const content = preview.loading
    ? '<p class="image-preview-error">正在从本机私有副本读取原图；不会外发。</p>'
    : preview.error
      ? `<p class="image-preview-error">${escape(preview.error)}</p>`
      : `<div class="image-preview-tools" aria-label="图片视口控制"><button data-action="image-zoom-out" ${zoom <= 1 ? 'disabled' : ''}>缩小</button><button data-action="image-zoom-reset" ${zoom === 1 ? 'disabled' : ''}>适应</button><button data-action="image-zoom-in" ${zoom >= 4 ? 'disabled' : ''}>放大</button><small>缩放和滚动只改变当前视口</small></div><figure><img src="${escape(preview.dataUrl)}" alt="${escape(preview.displayName)} 原图预览"${zoom > 1 ? ` style="width:${Math.round(preview.width * zoom)}px;max-width:none;max-height:none"` : ''}><figcaption>${escape(preview.displayName)} · ${preview.width} × ${preview.height} · ${bytes(preview.byteCount)} · 仅本地原图</figcaption></figure>`;
  return `<div class="scrim"><section class="dialog image-preview-dialog" role="dialog" aria-modal="true" aria-label="本地图片预览"><button class="icon-button close" data-action="close-image-preview" aria-label="关闭图片预览">${icon(icons.close, '关闭')}</button><h2>本地图片预览</h2>${content}</section></div>`;
}

function pdfPreviewDialog() {
  const preview = state.pdfPreview;
  if (!preview) return '';
  const content = preview.loading
    ? '<p class="image-preview-error">正在从本机私有副本验证 PDF；不会外发。</p>'
    : preview.error
      ? `<p class="image-preview-error">${escape(preview.error)}</p>`
      : `<div class="pdf-preview-tools" aria-label="PDF 页码控制"><button data-action="pdf-page-previous" ${preview.pageNumber <= 1 ? 'disabled' : ''}>上一页</button><small>第 ${preview.pageNumber} / ${preview.pageCount} 页</small><button data-action="pdf-page-next" ${preview.pageNumber >= preview.pageCount ? 'disabled' : ''}>下一页</button></div><iframe class="pdf-preview-frame" sandbox="" referrerpolicy="no-referrer" src="${escape(preview.dataUrl)}#page=${preview.pageNumber}" title="${escape(preview.displayName)} 本地 PDF 第 ${preview.pageNumber} 页"></iframe><p class="pdf-preview-note">仅从本机私有副本读取；脚本、表单动作、外部资源和自动链接均不执行。</p>`;
  return `<div class="scrim"><section class="dialog image-preview-dialog" role="dialog" aria-modal="true" aria-label="本地 PDF 阅读"><button class="icon-button close" data-action="close-pdf-preview" aria-label="关闭本地 PDF 阅读">${icon(icons.close, '关闭')}</button><h2>本地 PDF 阅读</h2>${content}</section></div>`;
}

function videoPreviewDialog() {
  const preview = state.videoPreview;
  if (!preview) return '';
  const content = preview.loading
    ? '<p class="image-preview-error">正在从本机私有副本验证视频；不会外发。</p>'
    : preview.error
      ? `<p class="image-preview-error">${escape(preview.error)}</p>`
      : `<div class="pdf-preview-tools" aria-label="本地视频播放控制"><button data-action="start-video-preview">开始本地播放</button><small>仅在明确点击后播放</small></div><video class="video-preview-frame" controls preload="metadata" data-video-preview src="${escape(preview.objectUrl)}" aria-label="${escape(preview.displayName)} 本地视频播放器"></video>${preview.positionMillis > 0 ? `<p class="pdf-preview-note">将从 ${formatDuration(preview.positionMillis)} 继续播放</p>` : ''}<p class="pdf-preview-note">${escape(preview.displayName)} · ${bytes(preview.byteCount)} · ${formatDuration(preview.durationMillis)}。仅从本机私有副本播放；不会自动播放、上传或外发。</p>`;
  return `<div class="scrim"><section class="dialog image-preview-dialog" role="dialog" aria-modal="true" aria-label="本地视频预览"><button class="icon-button close" data-action="close-video-preview" aria-label="关闭本地视频预览">${icon(icons.close, '关闭')}</button><h2>本地视频预览</h2>${content}</section></div>`;
}

function audioPreviewDialog() {
  const preview = state.audioPreview;
  if (!preview) return '';
  const content = preview.loading ? '<p class="image-preview-error">正在从本机私有副本验证音频；不会外发。</p>'
    : preview.error ? `<p class="image-preview-error">${escape(preview.error)}</p>`
      : `<div class="pdf-preview-tools" aria-label="本地音频播放控制"><button data-action="start-audio-preview">开始本地播放</button><small>仅在明确点击后播放</small></div><audio controls preload="metadata" data-audio-preview src="${escape(preview.dataUrl)}" aria-label="${escape(preview.displayName)} 本地音频播放器"></audio>${preview.positionMillis > 0 ? `<p class="pdf-preview-note">将从 ${formatDuration(preview.positionMillis)} 继续播放</p>` : ''}<p class="pdf-preview-note">${escape(preview.displayName)} · ${escape(preview.mimeType)} · ${bytes(preview.byteCount)} · 时长由本地播放器读取。仅从本机私有副本播放；不会自动播放、上传或外发。</p>`;
  return `<div class="scrim"><section class="dialog image-preview-dialog" role="dialog" aria-modal="true" aria-label="本地音频播放"><button class="icon-button close" data-action="close-audio-preview" aria-label="关闭本地音频播放">${icon(icons.close, '关闭')}</button><h2>本地音频播放</h2>${content}</section></div>`;
}

function textPreviewDialog() {
  const preview = state.textPreview;
  if (!preview) return '';
  const content = preview.loading ? '<p class="image-preview-error">正在从本机私有副本验证 UTF-8 文本；不会执行内容。</p>'
    : preview.error ? `<p class="image-preview-error">${escape(preview.error)}</p>`
      : `<p class="pdf-preview-note">${escape(preview.displayName)} · ${escape(preview.mimeType)} · ${bytes(preview.byteCount)}${preview.truncated ? ' · 仅显示前 128 KiB' : ''}</p><pre class="local-text-preview">${escape(preview.text)}</pre><p class="pdf-preview-note">内容按 inert 纯文本显示；不会渲染 HTML、执行链接、脚本或 Markdown 指令。</p>`;
  return `<div class="scrim"><section class="dialog image-preview-dialog" role="dialog" aria-modal="true" aria-label="本地安全文本预览"><button class="icon-button close" data-action="close-text-preview" aria-label="关闭本地安全文本预览">${icon(icons.close, '关闭')}</button><h2>本地安全文本预览</h2>${content}</section></div>`;
}

function formatDuration(milliseconds) { const seconds = Math.max(0, Math.floor((Number(milliseconds) || 0) / 1000)); return `${Math.floor(seconds / 60)}:${String(seconds % 60).padStart(2, '0')}`; }

function scheduleImageThumbnailReads() {
  if (!native || !state.current || state.imagePreview) return;
  document.querySelectorAll('[data-image-thumbnail]').forEach(element => {
    const attachmentId = element.dataset.imageThumbnail;
    if (!attachmentId || state.imageThumbnails[attachmentId] || state.imageThumbnailPending.has(attachmentId)) return;
    state.imageThumbnailPending.add(attachmentId);
    invoke('read_desktop_image_preview', { args: { workspaceId: state.current.summary.id, attachmentId, fullSize: false } })
      .then(preview => { state.imageThumbnails = { ...state.imageThumbnails, [attachmentId]: preview }; })
      .catch(() => { state.imageThumbnails = { ...state.imageThumbnails, [attachmentId]: { error: '本地缩略图不可用' } }; })
      .finally(() => { state.imageThumbnailPending.delete(attachmentId); render(); });
  });
}

async function openImagePreview(attachmentId) {
  if (!native || !state.current || !attachmentId) return;
  state.imagePreview = { loading: true }; render();
  try {
    state.imagePreview = { ...await invoke('read_desktop_image_preview', { args: { workspaceId: state.current.summary.id, attachmentId, fullSize: true } }), zoom: 1 };
  } catch (error) {
    state.imagePreview = { error: '本地原图不可用或校验失败。' };
  }
  render();
}

async function openPdfPreview(attachmentId, pageNumber = undefined) {
  if (!native || !state.current || !attachmentId) return;
  state.pdfPreview = { loading: true }; render();
  try {
    state.pdfPreview = await invoke('read_desktop_pdf_preview', { args: { workspaceId: state.current.summary.id, attachmentId, pageNumber } });
  } catch (error) {
    state.pdfPreview = { error: '本地 PDF 不可用或校验失败。' };
  }
  render();
}

async function openVideoPreview(attachmentId, positionMillis = undefined) {
  if (!native || !state.current || !attachmentId) return;
  state.videoPreview = { loading: true }; render();
  try {
    const preview = await invoke('read_desktop_video_preview', { args: { workspaceId: state.current.summary.id, attachmentId, positionMillis } });
    state.videoPreview = { ...preview, objectUrl: localVideoBlobUrl(preview.dataUrl) };
  }
  catch (error) { state.videoPreview = { error: '本地视频不可用、已损坏或校验失败。' }; }
  render();
  const restorePosition = Math.max(0, Number(state.videoPreview?.positionMillis) || 0) / 1000;
  if (restorePosition > 0) queueMicrotask(() => {
    const video = document.querySelector('[data-video-preview]');
    const seek = () => { if (video) video.currentTime = Math.min(restorePosition, Math.max(0, video.duration || restorePosition)); };
    if (video?.readyState >= 1) seek(); else video?.addEventListener('loadedmetadata', seek, { once: true });
  });
  queueMicrotask(() => {
    const video = document.querySelector('[data-video-preview]');
    video?.addEventListener('timeupdate', () => {
      const milliseconds = Math.round(video.currentTime * 1000);
      if (milliseconds > 0 && milliseconds < Math.round(video.duration * 1000)) {
        state.videoPreview = { ...state.videoPreview, lastPlaybackPositionMillis: milliseconds };
      }
    });
  });
}

function localVideoBlobUrl(dataUrl) {
  const encoded = String(dataUrl || '').split(',', 2)[1];
  if (!encoded) throw new Error('本地视频 payload 无效');
  const raw = atob(encoded);
  const bytes = Uint8Array.from(raw, value => value.charCodeAt(0));
  return URL.createObjectURL(new Blob([bytes], { type: 'video/mp4' }));
}

async function closeVideoPreview() {
  const video = document.querySelector('[data-video-preview]');
  const preview = state.videoPreview;
  if (!preview?.attachmentId || !state.current) return;
  const currentPosition = Math.round((video?.currentTime || 0) * 1000);
  const positionMillis = preview.lastPlaybackPositionMillis || currentPosition;
  try {
    const owner = await invoke('read_desktop_video_preview', {
      args: { workspaceId: state.current.summary.id, attachmentId: preview.attachmentId, positionMillis },
    });
    if (!owner || Number(owner.positionMillis) !== positionMillis) throw new Error('本地视频位置未确认');
    state.videoPreview = null;
    if (preview.objectUrl) URL.revokeObjectURL(preview.objectUrl);
    render();
  } catch (_) {
    state.error = '本地视频位置未保存；预览保持打开，未丢弃当前进度。';
    render();
  }
}

async function openAudioPreview(attachmentId, positionMillis = undefined) {
  if (!native || !state.current || !attachmentId) return;
  state.audioPreview = { loading: true }; render();
  try { state.audioPreview = await invoke('read_desktop_audio_preview', { args: { workspaceId: state.current.summary.id, attachmentId, positionMillis } }); }
  catch (_) { state.audioPreview = { error: '本地音频不可用、已损坏或校验失败。' }; }
  render();
  queueMicrotask(() => {
    const audio = document.querySelector('[data-audio-preview]');
    const seek = () => { if (audio) audio.currentTime = Math.min(Math.max(0, Number(state.audioPreview?.positionMillis) || 0) / 1000, Math.max(0, audio.duration || 0)); };
    if (audio?.readyState >= 1) seek(); else audio?.addEventListener('loadedmetadata', seek, { once: true });
    audio?.addEventListener('timeupdate', () => { const ms = Math.round(audio.currentTime * 1000); if (ms > 0 && ms < Math.round(audio.duration * 1000)) state.audioPreview = { ...state.audioPreview, lastPlaybackPositionMillis: ms }; });
  });
}

async function closeAudioPreview() {
  const audio = document.querySelector('[data-audio-preview]'); const preview = state.audioPreview;
  if (!preview?.attachmentId || !state.current) return;
  const positionMillis = preview.lastPlaybackPositionMillis || Math.round((audio?.currentTime || 0) * 1000);
  try { const owner = await invoke('read_desktop_audio_preview', { args: { workspaceId: state.current.summary.id, attachmentId: preview.attachmentId, positionMillis } }); if (!owner || Number(owner.positionMillis) !== positionMillis) throw new Error('本地音频位置未确认'); state.audioPreview = null; render(); }
  catch (_) { state.error = '本地音频位置未保存；预览保持打开，未丢弃当前进度。'; render(); }
}

async function openTextPreview(attachmentId) {
  if (!native || !state.current || !attachmentId) return;
  state.textPreview = { loading: true }; render();
  try { state.textPreview = await invoke('read_desktop_text_preview', { args: { workspaceId: state.current.summary.id, attachmentId } }); }
  catch (_) { state.textPreview = { error: '本地文本不可用、编码无效或校验失败。' }; }
  render();
}

function focusSearchAfterHistoryDismissal() {
  queueMicrotask(() => {
    document.querySelector('#chat-search')?.focus();
    window.setTimeout(() => { state.suppressSearchHistoryFocus = false; }, 150);
  });
}

function focusSearchAfterHistoryClear() {
  state.searchHistoryOpen = true;
  state.suppressSearchHistoryFocus = false;
  render();
  window.requestAnimationFrame(() => {
    const search = document.querySelector('#chat-search');
    search?.focus({ preventScroll: true });
    window.setTimeout(() => {
      if (document.activeElement !== search) search?.focus({ preventScroll: true });
    }, 0);
  });
}

render = renderUnified;

function chatDraftKey() {
  const workspaceId = workspace()?.summary?.id;
  if (!workspaceId) return null;
  return `nanfeng-ai.desktop.chat-draft.v1:${workspaceId}:${state.selectedConversationId || 'new'}`;
}

function readChatDraft() {
  const key = chatDraftKey();
  if (!key) return '';
  try { return window.localStorage.getItem(key) || ''; } catch { return ''; }
}
function attachmentDraftKey() { const key = chatDraftKey(); return key ? `${key}:attachments` : null; }
function readComposerAttachments() { const key = attachmentDraftKey(); if (!key) return []; try { const value = JSON.parse(window.localStorage.getItem(key) || '[]'); return Array.isArray(value) ? value.filter(item => item?.id && item?.sha256 && item?.mimeType && item?.displayName) : []; } catch { return []; } }
function writeComposerAttachments() { const key = attachmentDraftKey(); if (!key) return; try { if (state.composerAttachments.length) window.localStorage.setItem(key, JSON.stringify(state.composerAttachments)); else window.localStorage.removeItem(key); } catch {} }

function writeChatDraft(value) {
  const key = chatDraftKey();
  if (!key) return;
  try {
    if (value) window.localStorage.setItem(key, value);
    else window.localStorage.removeItem(key);
  } catch {}
}

async function enterTemporaryChat() {
  if (!native) { state.error = 'Web 预览不保存临时聊天；请在 Tauri Desktop 开发包中使用。'; render(); return; }
  try {
    state.temporaryConversation = await invoke('enter_or_restore_desktop_temporary_conversation', { temporaryId: state.temporaryConversation?.temporaryId || null });
    state.composerDraft = state.temporaryConversation.draft || '';
    state.composerAttachments = [];
    state.selectedConversationId = null;
    state.pane = 'chat'; state.error = ''; state.status = '已进入临时聊天：仅本地隔离恢复，最长保留 24 小时。'; render();
  } catch (error) { state.error = `临时聊天未能打开：${String(error)}`; render(); }
}

async function restoreTemporaryChat() {
  if (!native) return;
  const recovered = await invoke('read_desktop_temporary_conversation');
  if (!recovered) return;
  // Startup only prunes and discovers recoverability. It must not switch the user out of NORMAL.
  state.status = '发现可恢复的临时聊天；点击右上角“临时聊天”可在 24 小时内继续。';
}

async function updateTemporaryDraft(value) {
  if (!state.temporaryConversation) return;
  state.composerDraft = value;
  try {
    state.temporaryConversation = await invoke('update_desktop_temporary_conversation', { args: { temporaryId: state.temporaryConversation.temporaryId, draft: value, modelOverrideId: state.temporaryConversation.modelOverrideId || null } });
  } catch (error) { state.error = `临时草稿未保存：${String(error)}`; render(); }
}

async function updateTemporaryModelOverride(value) {
  if (!state.temporaryConversation) return;
  const modelOverrideId = String(value || '').trim() || null;
  try {
    state.temporaryConversation = await invoke('update_desktop_temporary_conversation', { args: { temporaryId: state.temporaryConversation.temporaryId, draft: state.composerDraft, modelOverrideId } });
    state.temporaryModelOpen = false;
    state.error = '';
    state.status = modelOverrideId ? '已保存临时会话的本地模型选择；没有读取 Key、没有调用 Provider。' : '临时会话已切回自动；仍只保存在隔离恢复记录。';
    render();
  } catch (error) { state.error = `临时模型选择未保存：${String(error)}`; render(); }
}

async function updateP6GConversationOverride(modelId) {
  if (!native || !state.current || !state.selectedConversationId || state.temporaryConversation || !state.p6gSelection) return;
  try {
    const args = { workspaceId: state.current.summary.id, conversationId: state.selectedConversationId, expectedRevision: state.p6gSelection.conversationOverride.revision };
    if (modelId) await invoke('set_desktop_p6g_conversation_override', { args: { ...args, modelId } });
    else await invoke('clear_desktop_p6g_conversation_override', args);
    await invoke('evaluate_desktop_p6g_auto_route', { args: { workspaceId: args.workspaceId, conversationId: args.conversationId, request: { tier: 'BALANCED', requiredCapabilities: ['TEXT'], exactHistoricalCacheHit: false, localSafeRequired: false, unknownCostConfirmed: false, contextTokens: null, budgetMicros: null } } });
    state.p6gModelPickerOpen = false;
    state.status = modelId ? '已保存当前会话的本地手动模型选择；未读取 Key、未调用 Provider。' : '已切回自动；当前会话恢复本地策略。';
    state.error = '';
    await refresh();
  } catch (error) { state.error = `模型选择未保存：${String(error)}`; state.p6gModelPickerOpen = false; render(); }
}

async function saveP6GGlobalDefault() {
  if (!native) return;
  const tier = document.querySelector('#p6g-global-default')?.value || null;
  try {
    await invoke('set_desktop_p6g_global_default', { args: { expectedRevision: state.p6gGlobalDefault.revision, tier } });
    state.status = tier ? `已保存本地全局默认：${tier}。` : '已清除本地全局默认；自动策略将使用当前请求档位。';
    state.error = '';
    await refresh();
  } catch (error) { state.error = `全局默认未保存：${String(error)}`; render(); }
}

async function installP6GLocalFixture() {
  if (!native || !state.p6gCatalog) return;
  const snapshot = state.p6gCatalog.snapshot;
  try {
    await invoke('upsert_desktop_p6g_catalog_candidate', { args: {
      expectedRevision: state.p6gCatalog.revision,
      catalogVersion: 'local-fixture-catalog-v1',
      policyVersion: Number(snapshot?.policyVersion || 0) + 1,
      candidate: {
        providerFamily: 'LOCAL', providerId: 'local-fixture', modelId: 'local-p6g-fixture-balanced-v1',
        displayName: '本地确定性 fixture（仅验收）', tiers: ['BALANCED'], capabilities: ['TEXT'],
        available: true, knownCostMicros: 0, latencyRank: 0, contextWindowTokens: 32768,
      },
    } });
    state.status = '已保存本地确定性 fixture catalog；仅用于验收，不读取 Key、不配置 Provider、不会调用网络。';
    state.error = '';
    await refresh();
  } catch (error) { state.error = `本地 fixture catalog 未保存：${String(error)}`; render(); }
}

function leaveTemporaryChat() {
  if (!state.temporaryConversation) return;
  state.temporaryConversation = null;
  state.composerDraft = readChatDraft();
  state.composerAttachments = [];
  state.temporaryModelOpen = false;
  state.selectedConversationId = null;
  state.pane = 'chat';
  state.status = '已返回普通聊天；临时内容仍在隔离恢复记录中，24 小时内可继续。';
  state.error = '';
  render();
}

async function sendLocalMessage() {
  const text = state.composerDraft.trim();
  if (!text && !state.composerAttachments.length) {
    state.error = '请输入文字或保留附件后再发送。';
    render();
    return;
  }
  if (state.temporaryConversation) {
    try {
      await updateTemporaryDraft(state.composerDraft);
      state.temporaryConversation = await invoke('append_desktop_temporary_message', { args: { temporaryId: state.temporaryConversation.temporaryId } });
      state.composerDraft = ''; state.composerAttachments = []; state.error = ''; state.status = '临时消息已保存到隔离恢复记录；没有调用模型。'; render();
    } catch (error) { state.error = `临时消息未保存：${String(error)}`; render(); }
    return;
  }
  if (!native || !state.current) {
    state.error = native ? '先导入一个本地工作区。' : 'Web 预览不会写入 Desktop SQLite。';
    render();
    return;
  }
  const draftKey = chatDraftKey();
  const conversation = resolveConversation(state.current, state.selectedConversationId);
  try {
    if (conversation) {
      await invoke('mutate_desktop_domain', { args: { intentId: intent('chat-message'), workspaceId: state.current.summary.id, entity: 'conversation', action: 'appendMessage', objectId: conversation.id, expectedRevision: conversation.revision, fields: { text, role: 'user', attachmentIds: state.composerAttachments.map(item => item.id) } } });
    } else {
      const receipt = await invoke('mutate_desktop_domain', { args: { intentId: intent('chat-create'), workspaceId: state.current.summary.id, entity: 'conversation', action: 'create', fields: { title: (text || state.composerAttachments[0]?.displayName || '本地附件').replace(/\s+/g, ' ').slice(0, 36), projectId: null, firstMessage: text, attachmentIds: state.composerAttachments.map(item => item.id) } } });
      state.selectedConversationId = receipt.objectId;
    }
    if (draftKey) window.localStorage.removeItem(draftKey);
    state.composerAttachments = []; writeComposerAttachments();
    state.composerDraft = '';
    state.error = '';
    state.status = '消息已本地记录。';
    state.pendingChatSendScrollToLatestId = state.selectedConversationId;
    await refresh();
  } catch (error) {
    state.error = `消息本地记录被拒绝：${String(error)}`;
    render();
  }
}

saveLocalMessage = sendLocalMessage;

async function pickComposerAttachment(kind) {
  if (!native || (!state.current && !state.temporaryConversation)) { state.error = native ? '先进入临时聊天或导入本地工作区。' : 'Web 预览不能私有复制附件。'; render(); return; }
  const currentAttachmentCount = state.temporaryConversation ? (state.temporaryConversation.draftAttachmentIds || []).length : state.composerAttachments.length;
  if (currentAttachmentCount >= 4) { state.error = '每条本地消息最多保留 4 个附件。'; render(); return; }
  const image = kind === 'image';
  // The native dialog must not silently gray out a valid WAV/M4A on a host-specific UTI map.
  // Import remains fail-closed in Rust: this picker grants no read capability beyond one explicit selection.
  const selectedPath = await dialogInvoke('open', image
    ? { multiple: false, directory: false, filters: [{ name: '图片', extensions: ['jpg', 'jpeg', 'png', 'webp'] }] }
    : { multiple: false, directory: false },
  );
  if (!selectedPath || Array.isArray(selectedPath)) return;
  try {
    if (state.temporaryConversation) {
      state.temporaryConversation = await invoke('import_desktop_temporary_attachment', { args: { temporaryId: state.temporaryConversation.temporaryId, selectedPath } });
      state.composerDraft = state.temporaryConversation.draft || state.composerDraft;
      state.status = '临时附件已私有复制并绑定 TEMPORARY_SESSION；不会进入普通会话。';
      state.composerAddOpen = false; state.error = ''; render(); return;
    }
    const attachment = await invoke('import_desktop_conversation_attachment', { args: { workspaceId: state.current.summary.id, selectedPath } });
    if (state.composerAttachments.some(item => item.sha256 === attachment.sha256)) { state.status = '相同附件已在当前草稿中；未重复添加。'; }
    else { state.composerAttachments = [...state.composerAttachments, attachment]; writeComposerAttachments(); state.status = '附件已私有复制并完成内容校验；未外发。'; }
    state.composerAddOpen = false; state.error = ''; render();
  } catch (error) { state.error = `附件未加入草稿：${String(error)}`; render(); }
}

async function mutateConversationLifecycle(target, action, fields, success) {
  if (!native || !state.current) return;
  try {
    await invoke('mutate_desktop_domain', { args: {
      intentId: intent('conversation-lifecycle'),
      workspaceId: state.current.summary.id,
      entity: 'conversation',
      action,
      objectId: target.dataset.id,
      expectedRevision: Number(target.dataset.revision),
      fields,
    } });
    if (action === 'archive' && state.selectedConversationId === target.dataset.id) state.selectedConversationId = null;
    state.error = '';
    state.status = success;
    state.dialog = null;
    await refresh();
  } catch (error) {
    state.error = `会话操作被拒绝：${String(error)}`;
    render();
  }
}

/** FB-P6-037: dialog state follows the typed receipt, never a CSS-only hide. */
async function confirmConversationDelete(target) {
  const dialog = state.dialog;
  const pending = beginConversationRecycle(dialog, target.dataset.id);
  if (!native || !state.current || !pending) return;
  state.dialog = pending;
  render();
  try {
    await invoke('mutate_desktop_domain', { args: {
      intentId: intent('conversation-lifecycle'), workspaceId: state.current.summary.id,
      entity: 'conversation', action: 'softDelete', objectId: dialog.id,
      expectedRevision: dialog.revision, fields: {},
    } });
    if (state.selectedConversationId === dialog.id) state.selectedConversationId = null;
    const completed = completeConversationRecycle();
    state.dialog = completed.dialog;
    state.error = '';
    state.status = completed.status;
    await refresh();
    document.querySelector(`[data-action="${completed.focusAction}"]`)?.focus({ preventScroll: true });
  } catch (error) {
    if (state.dialog?.kind === 'conversation-delete' && state.dialog.id === dialog.id) {
      state.dialog = failConversationRecycle(dialog, error);
    }
    render();
  }
}

app.addEventListener('contextmenu', event => {
  const row = event.target.closest('[data-conversation-row]');
  if (!row) return;
  event.preventDefault();
  openConversationContextMenu(row);
});

app.addEventListener('click', async event => {
  const target = event.target.closest('[data-action]');
  const action = target?.dataset.action;
  if (state.contextMenu && !target?.closest('.chat-context-menu')) closeConversationContextMenu();
  if (action === 'open-image-preview') { event.preventDefault(); await openImagePreview(target.dataset.attachmentId); return; }
  if (action === 'open-pdf-preview') { event.preventDefault(); await openPdfPreview(target.dataset.attachmentId); return; }
  if (action === 'open-video-preview') { event.preventDefault(); await openVideoPreview(target.dataset.attachmentId); return; }
  if (action === 'open-audio-preview') { event.preventDefault(); await openAudioPreview(target.dataset.attachmentId); return; }
  if (action === 'open-text-preview') { event.preventDefault(); await openTextPreview(target.dataset.attachmentId); return; }
  if (action === 'start-video-preview') {
    event.preventDefault();
    const video = document.querySelector('[data-video-preview]');
    if (!video) return;
    try { await video.play(); }
    catch (_) { state.error = '本地视频无法开始播放；私有副本仍未外发。'; render(); }
    return;
  }
  if (action === 'start-audio-preview') {
    event.preventDefault(); const audio = document.querySelector('[data-audio-preview]'); if (!audio) return;
    try { await audio.play(); } catch (_) { state.error = '本地音频无法开始播放；私有副本仍未外发。'; render(); }
    return;
  }
  if (action === 'close-image-preview') { event.preventDefault(); state.imagePreview = null; render(); return; }
  if (action === 'close-pdf-preview') { event.preventDefault(); state.pdfPreview = null; render(); return; }
  if (action === 'close-video-preview') { event.preventDefault(); await closeVideoPreview(); return; }
  if (action === 'close-audio-preview') { event.preventDefault(); await closeAudioPreview(); return; }
  if (action === 'close-text-preview') { event.preventDefault(); state.textPreview = null; render(); return; }
  if (action === 'pdf-page-previous' || action === 'pdf-page-next') { event.preventDefault(); if (!state.pdfPreview?.attachmentId) return; await openPdfPreview(state.pdfPreview.attachmentId, state.pdfPreview.pageNumber + (action === 'pdf-page-next' ? 1 : -1)); return; }
  if (action === 'image-zoom-in' || action === 'image-zoom-out' || action === 'image-zoom-reset') { event.preventDefault(); if (!state.imagePreview?.dataUrl) return; const current = Number(state.imagePreview.zoom) || 1; const zoom = action === 'image-zoom-in' ? Math.min(4, current + 0.25) : action === 'image-zoom-out' ? Math.max(1, current - 0.25) : 1; state.imagePreview = { ...state.imagePreview, zoom }; render(); return; }
  if (action === 'toggle-composer-add') {
    if (state.composerAddOpen) closeTopOverlay(); else openTransientOverlay('composer-add', target);
    return;
  }
  if (action === 'copy-message') {
    const message = resolveConversation(state.current, state.selectedConversationId)?.messages
      ?.find(item => item.id === target.dataset.messageId);
    const payload = messagePlainText(message);
    if (!payload) { state.error = '该消息没有可复制的安全正文。'; render(); return; }
    try {
      await navigator.clipboard.writeText(payload);
      state.status = '已复制纯文本；未包含路径、URI 或隐藏 metadata。';
      state.error = '';
    } catch (error) {
      state.error = `复制失败：${String(error)}`;
    }
    render();
    return;
  }
  if (action === 'share-message') {
    const message = resolveConversation(state.current, state.selectedConversationId)?.messages
      ?.find(item => item.id === target.dataset.messageId);
    const payload = messagePlainText(message);
    if (!payload) { state.error = '该消息没有可分享的安全正文。'; render(); return; }
    try {
      // WebKit may expose navigator.share without a macOS share owner. In a Tauri
      // bundle that can silently resolve without presenting any user choice. The
      // guaranteed local Desktop action is therefore the verified safe payload copy;
      // browser preview retains its real Web Share path.
      if (!native && typeof navigator.share === 'function') await navigator.share({ text: payload });
      else {
        await navigator.clipboard.writeText(payload);
        state.status = '已复制可分享内容；请在目标应用中通过系统粘贴发送。';
      }
      state.error = '';
    } catch (error) {
      if (error?.name !== 'AbortError') state.error = '系统分享不可用，且未复制分享内容。';
    }
    render();
    return;
  }
  if (action === 'message-provenance') {
    state.status = '该消息来源已保存在本地审计记录中；不会显示 URI、文件路径或密钥。';
    state.error = '';
    render();
    return;
  }
  if (action === 'branch-from-message') {
    const conversation = resolveConversation(state.current, state.selectedConversationId);
    if (!native || !state.current || !conversation) { state.error = '当前没有可分支的本地会话。'; render(); return; }
    try {
      const receipt = await invoke('mutate_desktop_domain', { args: {
        intentId: intent('message-branch'), workspaceId: state.current.summary.id, entity: 'conversation',
        action: 'branchFromMessage', objectId: conversation.id, expectedRevision: conversation.revision,
        fields: { messageId: target.dataset.messageId },
      } });
      state.selectedConversationId = receipt.objectId;
      state.status = '已从该持久化消息创建本地分支；保留前缀与来源，不连接 Provider。';
      state.error = '';
      await refresh();
    } catch (error) { state.error = `创建分支被拒绝：${String(error)}`; render(); }
    return;
  }
  if (action === 'toggle-temporary-chat') { if (state.temporaryConversation) leaveTemporaryChat(); else await enterTemporaryChat(); return; }
  if (action === 'pick-composer-image' || action === 'pick-composer-file') { await pickComposerAttachment(action === 'pick-composer-image' ? 'image' : 'file'); return; }
  if (action === 'remove-composer-attachment') {
    if (state.temporaryConversation) {
      try { state.temporaryConversation = await invoke('remove_desktop_temporary_attachment', { args: { temporaryId: state.temporaryConversation.temporaryId, attachmentId: target.dataset.id } }); state.error = ''; render(); } catch (error) { state.error = `临时附件未移除：${String(error)}`; render(); }
    } else { state.composerAttachments = state.composerAttachments.filter(item => item.id !== target.dataset.id); writeComposerAttachments(); render(); }
    return;
  }
  if (action === 'context-menu-pin') {
    await mutateConversationLifecycle(target, 'setPinned', { pinned: target.dataset.pinned !== 'true' }, target.dataset.pinned === 'true' ? '会话已取消置顶。' : '会话已置顶。');
    state.contextMenu = null;
    return;
  }
  if (action === 'context-menu-archive') {
    await mutateConversationLifecycle(target, target.dataset.archived === 'true' ? 'restore' : 'archive', {}, target.dataset.archived === 'true' ? '会话已恢复。' : '会话已归档。');
    state.contextMenu = null;
    return;
  }
  if (action === 'context-menu-rename') {
    const conversation = workspace()?.exchange?.conversations?.find(item => item.id === target.dataset.id);
    state.contextMenu = null;
    state.dialog = { kind: 'conversation-rename', id: target.dataset.id, revision: Number(target.dataset.revision), title: conversation?.title || '' };
    render();
    return;
  }
  if (action === 'context-menu-project') {
    const conversation = workspace()?.exchange?.conversations?.find(item => item.id === target.dataset.id);
    state.contextMenu = null;
    state.dialog = { kind: 'conversation-project', id: target.dataset.id, revision: Number(target.dataset.revision), projectId: conversation?.projectId || null };
    render();
    return;
  }
  if (action === 'context-menu-delete') {
    state.contextMenu = null;
    state.dialog = { kind: 'conversation-delete', id: target.dataset.id, revision: Number(target.dataset.revision) };
    render();
    return;
  }
  if (action === 'save-conversation-rename') {
    await mutateConversationLifecycle(target, 'update', { title: document.querySelector('#conversation-rename')?.value || '' }, '会话已重命名。');
    return;
  }
  if (action === 'save-conversation-project') {
    const projectId = document.querySelector('#conversation-project')?.value || null;
    await mutateConversationLifecycle(target, 'setProject', { projectId }, projectId ? '会话已移动到所选项目。' : '会话已移出当前项目。');
    return;
  }
  if (action === 'confirm-conversation-delete') {
    await confirmConversationDelete(target);
    return;
  }
  if (action === 'toggle-rail') {
    state.railCollapsed = !state.railCollapsed;
    render();
    return;
  }
  if (action === 'set-conversation-pinned') {
    await mutateConversationLifecycle(target, 'setPinned', { pinned: target.dataset.pinned === 'true' }, target.dataset.pinned === 'true' ? '会话已置顶。' : '会话已取消置顶。');
    return;
  }
  if (action === 'archive-conversation') {
    await mutateConversationLifecycle(target, 'archive', {}, '会话已归档；消息和本地历史仍保留。');
    return;
  }
  if (action === 'restore-conversation') {
    await mutateConversationLifecycle(target, 'restore', {}, '会话已恢复到活动列表。');
    return;
  }
  if (action === 'restore-deleted-conversation') {
    await mutateConversationLifecycle(target, 'restoreDeleted', {}, '会话已从回收站恢复到活动列表。');
    return;
  }
  if (action === 'toggle-chat-sidebar') {
    state.sidebarOpen = !state.sidebarOpen;
    state.profileOpen = false;
    render();
    return;
  }
  if (action === 'show-work') {
    state.pane = 'work';
    state.profileOpen = false;
    state.error = '';
    selectWorkspaceDefaultConversation();
  } else if (action === 'show-workspace') {
    state.pane = 'work';
    state.profileOpen = false;
    selectWorkspaceDefaultConversation();
  } else if (action === 'show-projects') {
    state.pane = 'projects';
    state.profileOpen = false;
  } else if (action === 'show-knowledge') {
    state.pane = 'knowledge';
    state.profileOpen = false;
  } else if (action === 'show-memory') {
    state.pane = 'memory';
    state.profileOpen = false;
  } else if (action === 'show-conversation') {
    state.pane = 'chat';
    state.profileOpen = false;
  }
  if (['show-work', 'show-workspace', 'show-projects', 'show-knowledge', 'show-memory', 'show-conversation'].includes(action)) {
    state.sidebarOpen = false;
    render();
  }
});

app.addEventListener('click', event => {
  const action = event.target.closest('[data-action]')?.dataset.action;
  if (action === 'new-chat' || action === 'select-chat') {
    state.composerDraft = readChatDraft();
    state.composerAttachments = readComposerAttachments();
    if (action === 'new-chat') state.status = '';
    state.sidebarOpen = false;
    render();
  }
});

app.addEventListener('click', event => {
  if (event.target.closest('[data-action]')?.dataset.action !== 'show-settings') return;
  state.sidebarOpen = false;
  render();
});

app.addEventListener('click', event => {
  const target = event.target.closest('[data-action]');
  const action = target?.dataset.action;
  if (action === 'submit-search') { event.preventDefault(); void submitLocalSearch(); return; }
  if (action === 'fill-search-history') { event.preventDefault(); state.chatSearch = target.dataset.query || ''; state.searchHistoryOpen = false; state.suppressSearchHistoryFocus = true; render(); focusSearchAfterHistoryDismissal(); return; }
  if (action === 'close-search-history') { event.preventDefault(); state.searchHistoryOpen = false; state.suppressSearchHistoryFocus = true; render(); focusSearchAfterHistoryDismissal(); return; }
  if (action === 'clear-search-history') { event.preventDefault(); if (native && state.current) invoke('clear_desktop_local_search_history', { workspaceId: state.current.summary.id }).then(() => { state.searchHistory = []; focusSearchAfterHistoryClear(); }).catch(() => { state.error = '本地搜索历史清除未完成。'; render(); }); return; }
  if (action === 'close-search') { event.preventDefault(); state.searchPanel = false; render(); return; }
  if (action === 'open-search-result') { event.preventDefault(); state.selectedConversationId = target.dataset.id; state.searchAnchorMessageId = target.dataset.messageId || null; state.searchPanel = false; state.pane = 'chat'; render(); queueMicrotask(() => document.querySelector(`[data-message-id="${CSS.escape(state.searchAnchorMessageId || '')}"]`)?.scrollIntoView({ block: 'center' })); return; }
  if (action === 'toggle-temporary-model') {
    event.preventDefault();
    if (!state.temporaryConversation) return;
    if (state.temporaryModelOpen) closeTopOverlay(); else openTransientOverlay('temporary-model', target);
  } else if (action === 'open-compare-confirmation') {
    event.preventDefault();
    state.p6gModelPickerOpen = false;
    state.dialog = null;
    state.error = 'Compare 尚未接入 Desktop 执行 owner，未读取 Key 或发送内容。';
    state.status = 'Compare 已直接提交执行请求，但当前 Desktop 没有可用执行 owner。';
    render();
  } else if (action === 'toggle-p6g-model-picker') {
    event.preventDefault();
    if (state.compareLongPressTriggered) {
      state.compareLongPressTriggered = false;
      return;
    }
    if (state.p6gModelPickerOpen) closeTopOverlay(); else openTransientOverlay('p6g-model-picker', target);
  } else if (action === 'select-p6g-auto') {
    event.preventDefault();
    void updateP6GConversationOverride(null);
  } else if (action === 'select-p6g-model') {
    event.preventDefault();
    void updateP6GConversationOverride(target.dataset.modelId || null);
  } else if (action === 'select-temporary-auto') {
    event.preventDefault();
    void updateTemporaryModelOverride(null);
  } else if (action === 'select-temporary-model') {
    event.preventDefault();
    void updateTemporaryModelOverride(target.dataset.modelId || null);
  } else if (action === 'save-p6g-global-default') {
    event.preventDefault();
    void saveP6GGlobalDefault();
  } else if (action === 'install-p6g-local-fixture') {
    event.preventDefault();
    void installP6GLocalFixture();
  }
});

app.addEventListener('input', event => {
  if (event.target.id === 'chat-composer') {
    if (state.temporaryConversation) updateTemporaryDraft(state.composerDraft); else writeChatDraft(state.composerDraft);
  }
  if (event.target.id === 'chat-search') {
    state.chatSearch = event.target.value;
  }
});

app.addEventListener('change', event => {
  if (event.target.id === 'p6g-conversation-model') void updateP6GConversationOverride(event.target.value || null);
});

app.addEventListener('focusin', event => {
  if (event.target.id !== 'chat-search') return;
  if (state.suppressSearchHistoryFocus) return;
  if (state.searchHistoryOpen) return;
  if (!native || !state.current) { state.searchHistoryOpen = true; render(); return; }
  invoke('read_desktop_local_search_history', { workspaceId: state.current.summary.id }).then(history => { state.searchHistory = history; state.searchHistoryOpen = true; render(); queueMicrotask(() => document.querySelector('#chat-search')?.focus()); }).catch(() => {});
});

app.addEventListener('pointerdown', event => {
  if (event.target.id === 'chat-search') state.suppressSearchHistoryFocus = false;
});

app.addEventListener('keydown', event => {
  if (event.target.id === 'chat-search' && event.key === 'Enter') { event.preventDefault(); void submitLocalSearch(); }
  if (event.key === 'Escape' && state.searchHistoryOpen) { state.searchHistoryOpen = false; state.suppressSearchHistoryFocus = true; render(); focusSearchAfterHistoryDismissal(); }
  else if (event.key === 'Escape' && state.searchPanel) { state.searchPanel = false; render(); }
});

app.addEventListener('click', event => {
  if (!state.searchHistoryOpen || event.target.closest('.chat-search-wrap')) return;
  state.searchHistoryOpen = false;
  state.suppressSearchHistoryFocus = true;
  render();
  focusSearchAfterHistoryDismissal();
});

let wasCompactChatViewport = window.matchMedia('(max-width: 900px)').matches;
window.addEventListener('resize', () => {
  const isCompactChatViewport = window.matchMedia('(max-width: 900px)').matches;
  if (wasCompactChatViewport && !isCompactChatViewport && state.sidebarOpen) {
    state.sidebarOpen = false;
    render();
  }
  wasCompactChatViewport = isCompactChatViewport;
});

if (native) {
  invoke('read_latest_chatgpt_import_task').then(task => { state.chatgptTask = task; }).catch(() => { /* Settings remains usable; errors surface on explicit resume. */ });
  invoke('read_latest_claude_import_task').then(task => { state.claudeTask = task; }).catch(() => { /* Settings remains usable; errors surface on explicit resume. */ });
  invoke('read_latest_p6k_zip_import_task').then(task => { state.p6kTask = task; }).catch(() => { /* P6-K is native-only. */ });
  invoke('read_p6h_diagnostics_status').then(enabled => { p6hDiagnosticsEnabled = Boolean(enabled); }).catch(() => {});
}

async function bootDesktopShell() {
  try {
    await refresh();
  } catch {
    state.error = '本地工作区启动读取未完成；请重新打开应用后再试。';
    state.status = '启动读取失败；未修改本地数据。';
    render();
  }
}

void bootDesktopShell();
