import test from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { resolve } from 'node:path';
import {
  activeConversations,
  conversationLocalDate,
  archivedConversations,
  deriveDesktopDualPathState,
  localMessagePathState,
  messagePlainText,
  transcriptLocalDateKey,
  pinnedConversations,
  renderChatFirstShell,
  resolveConversationMenuAnchor,
  resolveConversation,
  assistantWorkDuration,
  transcriptMetadata,
} from '../src/chat-shell.mjs';
import { beginConversationRecycle, completeConversationRecycle, failConversationRecycle } from '../src/recycle-confirmation.mjs';

const root = resolve(import.meta.dirname, '..');
const [source, shell, css, build, temporaryPermission, capability, tauriConfig, previewOwner] = await Promise.all([
  readFile(resolve(root, 'src/app.mjs'), 'utf8'),
  readFile(resolve(root, 'src/chat-shell.mjs'), 'utf8'),
  readFile(resolve(root, 'src/chat-shell.css'), 'utf8'),
  readFile(resolve(root, 'scripts/build.mjs'), 'utf8'),
  readFile(resolve(root, 'src-tauri/permissions/default.toml'), 'utf8'),
  readFile(resolve(root, 'src-tauri/capabilities/default.json'), 'utf8'),
  readFile(resolve(root, 'src-tauri/tauri.conf.json'), 'utf8'),
  readFile(resolve(root, 'src/desktop-attachment-preview-owner.mjs'), 'utf8'),
]);
const icons = await readFile(resolve(root, 'src/icon-source.mjs'), 'utf8');

const fixture = {
  summary: { id: 'workspace-one' },
  exchange: {
    conversations: [
      { id: 'old', title: '旧会话', archived: false, updatedAt: '2026-08-12T00:00:00Z', revision: 1, messages: [] },
      { id: 'new', title: '新会话', archived: false, updatedAt: '2026-08-13T00:00:00Z', revision: 2, messages: [] },
      { id: 'hidden', title: '已归档', archived: true, updatedAt: '2026-08-14T00:00:00Z', revision: 1, messages: [] },
    ],
  },
};

test('chat-first navigation lists active local conversations without auto-selecting one', () => {
  assert.deepEqual(activeConversations(fixture).map(item => item.id), ['new', 'old']);
  assert.equal(resolveConversation(fixture, null), null);
  assert.equal(resolveConversation(fixture, 'new')?.title, '新会话');
  assert.equal(resolveConversation(fixture, 'missing'), null);
});

test('sidebar distinguishes pinned and recent conversations while keeping them in one scrolling list', () => {
  const sidebarFixture = {
    ...fixture,
    exchange: {
      conversations: [
        { id: 'ordinary', title: '普通会话', archived: false, pinned: false, updatedAt: '2026-08-12T00:00:00Z', revision: 1, messages: [] },
        { id: 'pinned', title: '置顶会话', archived: false, pinned: true, updatedAt: '2026-08-11T00:00:00Z', revision: 2, messages: [] },
        { id: 'archived', title: '归档会话', archived: true, pinned: false, updatedAt: '2026-08-13T00:00:00Z', revision: 3, messages: [] },
      ],
    },
  };
  assert.deepEqual(activeConversations(sidebarFixture).map(item => item.id), ['pinned', 'ordinary']);
  assert.deepEqual(pinnedConversations(sidebarFixture).map(item => item.id), ['pinned']);
  assert.deepEqual(archivedConversations(sidebarFixture).map(item => item.id), ['archived']);
  const active = renderChatFirstShell({ data: sidebarFixture, native: true, selectedConversationId: 'ordinary', composerDraft: '', chatSearch: '', profileOpen: false, sidebarOpen: false, railCollapsed: false, showArchived: false, pane: 'chat', status: '', error: '', connection: {} });
  for (const token of ['chat-sidebar-scroll', 'chat-sidebar-functions', 'chat-history', 'chat-history-group', 'aria-label="置顶会话"', 'aria-label="最近会话"', '<p class="chat-history-label">置顶</p>', '<p class="chat-history-label">最近</p>', '普通会话', '置顶会话', 'set-conversation-pinned', 'archive-conversation', 'chat-row-action-icon', 'title="置顶会话"', 'aria-label="归档会话"']) assert.ok(active.includes(token));
  assert.ok(!active.includes('class="chat-pinned"'));
  assert.ok(!active.includes('data-action="toggle-archived-conversations"'));
  assert.ok(!active.includes('data-action="toggle-deleted-conversations"'));
  assert.ok(!active.includes('>归档</button>'));
  assert.ok(!active.includes('>置顶</button>'));
  assert.ok(active.indexOf('chat-search-wrap') < active.indexOf('chat-sidebar-functions'));
  const settings = renderChatFirstShell({ data: sidebarFixture, native: true, selectedConversationId: null, composerDraft: '', chatSearch: '', profileOpen: false, sidebarOpen: false, railCollapsed: false, showArchived: false, pane: 'settings', settingsSection: 'archived', status: '', error: '', connection: {} });
  for (const token of ['android-settings-main', 'aria-label="返回应用"', '对话管理', '已归档', 'data-lifecycle-action-disclosure="archived"', 'restore-conversation', '<title>恢复</title>']) assert.ok(settings.includes(token));
  for (const token of ['chat-row-actions', ':focus-within', 'toggle-rail', 'rail-collapsed', "'setPinned'", "'archive'", 'mutateConversationLifecycle']) assert.ok(`${shell}\n${css}\n${source}`.includes(token));
  assert.ok(!source.includes("action === 'toggle-archived-conversations'"));
  assert.ok(!source.includes("action === 'toggle-deleted-conversations'"));
});

test('pinned conversations share the single sidebar scroll flow above the bottom actions', () => {
  for (const token of ['.chat-sidebar-scroll { display: flex;', 'overflow-y: auto;', 'padding-bottom: 68px;', 'scrollbar-gutter: stable;', '.chat-history { flex: 0 0 auto;', '.chat-sidebar-footer { position: absolute;', 'pointer-events: none;', '.chat-sidebar-footer button { pointer-events: auto;']) assert.ok(css.includes(token), token);
  for (const forbidden of ['.chat-pinned {', 'max-height: 156px', '置顶</p>', '最近</p>']) assert.ok(!css.includes(forbidden));
});

test('P6-E acceptance settings renders the fixed no-argument maintenance card and receipt', () => {
  const rendered = renderChatFirstShell({
    data: null,
    native: true,
    pane: 'settings',
    settingsSection: 'development',
    status: '',
    error: '',
    connection: {},
    p6eAcceptance: {
      enabled: true,
      receipt: {
        retainedAt23h59: true,
        removedAt24h: true,
        attachmentRemovedAt24h: true,
        messagePresentBeforeExpiry: true,
        modelOverridePresentBeforeExpiry: true,
        ordinarySurfacesClean: true,
      },
    },
  });
  for (const token of ['P6-E 验收维护', '仅 acceptance 启动显示', 'run-p6e-temporary-maintenance-acceptance', '23h59 保留=true', '24h 清理=true']) assert.ok(rendered.includes(token));
  const production = renderChatFirstShell({ data: null, native: true, pane: 'settings', status: '', error: '', connection: {}, p6eAcceptance: { enabled: false, receipt: null } });
  assert.ok(!production.includes('run-p6e-temporary-maintenance-acceptance'));
});

test('desktop compact actions use the shared pushpin and archive glyphs, never ambiguous text symbols', () => {
  for (const token of ['pushPin:', 'pushPinOff:', 'archive:', 'restore:', "import { icon, icons, settingsIcons } from './icon-source.mjs'"]) assert.ok(`${icons}\n${shell}`.includes(token));
  for (const forbidden of ['>⌁<', '>▤<', '>•<']) assert.ok(!shell.includes(forbidden));
});

test('drawer footer has only the settings entry, never a profile settings menu', () => {
  const rendered = renderChatFirstShell({ data: fixture, native: true, selectedConversationId: null, composerDraft: '', chatSearch: '', profileOpen: false, sidebarOpen: false, pane: 'chat', status: '', error: '', connection: {} });
  for (const token of ['settings:', 'data-action="show-settings"', 'aria-label="设置"']) assert.ok(`${icons}\n${rendered}`.includes(token));
  assert.ok(!rendered.includes('本机数据与默认行为'));
  for (const forbidden of ['chat-profile-menu', '模型与联网', '账号与加密同步']) assert.ok(!rendered.includes(forbidden));
});

test('FB-P6-038 and FB-P6-048 keep Settings independent while removing duplicated headings and decorative sidebar copy', () => {
  const rendered = renderChatFirstShell({ data: fixture, native: true, selectedConversationId: null, composerDraft: '', chatSearch: '', profileOpen: false, sidebarOpen: false, pane: 'settings', settingsSection: 'data', status: '', error: '', connection: {} });
  for (const token of ['android-settings-main', 'android-settings-primary', 'android-settings-secondary', 'data-action="show-chat"', '导入 ChatGPT JSON']) assert.ok(rendered.includes(token));
  for (const forbidden of ['chat-sidebar-footer', 'chat-sidebar-divider', 'chat-history']) assert.ok(!rendered.includes(forbidden));
  assert.ok(!rendered.includes('settings-center-main'));
  assert.ok(!rendered.includes('本机数据与默认行为'));
  assert.ok(source.includes("visiblePane === 'settings' ? 'settings-mode'"));
  assert.ok(css.includes('.app-shell.chat-first.settings-mode { display: block; min-height: 0; }'));
});

test('settings keeps the current Android workspace import and export surface', () => {
  const rendered = renderChatFirstShell({ data: fixture, native: true, selectedConversationId: null, composerDraft: '', chatSearch: '', profileOpen: false, sidebarOpen: false, railCollapsed: false, showArchived: false, pane: 'settings', settingsSection: 'data', status: '', error: '', connection: {} });
  for (const token of ['工作区', '导入工作区', 'data-action="start-import"', '导出工作区', 'data-action="start-export"']) assert.ok(rendered.includes(token));
  for (const forbidden of ['导入为独立工作区', '私有归档 v2 交换包', '导出当前工作区']) assert.ok(!rendered.includes(forbidden));
  assert.ok(rendered.indexOf('data-action="start-import"') < rendered.indexOf('data-action="start-export"'));
  assert.ok(!rendered.includes('data-action="select-v2-workspace-exchange"'));
  assert.ok(!rendered.includes('恢复备份'));
});

test('current Android data hierarchy presents JSON, ZIP, workspace, and backup groups in order', () => {
  const rendered = renderChatFirstShell({ data: fixture, native: true, selectedConversationId: null, composerDraft: '', chatSearch: '', profileOpen: false, sidebarOpen: false, railCollapsed: false, showArchived: false, pane: 'settings', settingsSection: 'data', status: '', error: '', connection: {} });
  const labels = ['导入 ChatGPT JSON', '导入 Claude JSON', '导入 ChatGPT ZIP', '导入 Claude ZIP', '导入工作区', '导出工作区', '本机备份与恢复', '备份', '恢复'];
  labels.forEach(label => assert.ok(rendered.includes(label)));
  for (let index = 1; index < labels.length; index += 1) assert.ok(rendered.indexOf(labels[index - 1]) < rendered.indexOf(labels[index]));
});

test('local backup and restore use the native owner, strict preflight, replacement gate, cancellation, and restart boundary', async () => {
  const base = { data: fixture, native: true, pane: 'settings', settingsSection: 'data', status: '', error: '', connection: {} };
  const ready = renderChatFirstShell(base);
  for (const token of ['data-action="export-local-backup"', 'data-action="import-local-backup"']) assert.ok(ready.includes(token));
  assert.ok(!ready.match(/data-action="(?:export|import)-local-backup"[^>]*disabled/));
  const preflight = { format: 'nanfeng-ai.local-backup', version: 1, schemaVersion: 22, tableCounts: { workspaces: 2 }, assetBytes: 4096, conflicts: ['本地已有业务数据；只能明确选择替换本地或取消，不支持合并。'], fingerprint: 'a'.repeat(64) };
  const gated = renderChatFirstShell({ ...base, localBackup: { preflight, replaceLocal: false } });
  for (const token of ['预检：格式 nanfeng-ai.local-backup v1', '取消，不替换本地', '选择替换本地（强确认）', '恢复并要求重启', '取消此次恢复']) assert.ok(gated.includes(token));
  assert.match(gated, /data-action="restore-local-backup" disabled/);
  const confirmed = renderChatFirstShell({ ...base, localBackup: { preflight, replaceLocal: true } });
  assert.ok(!confirmed.match(/data-action="restore-local-backup" disabled/));

  const appSource = await readFile(resolve(import.meta.dirname, '../src/app.mjs'), 'utf8');
  for (const token of ['export_desktop_local_backup', 'preflight_desktop_local_backup', 'restore_desktop_local_backup', 'cancel_desktop_local_restore', 'read_desktop_local_backup_status', "kind: 'local-backup-restart-required'", '请手动完全退出并重新打开 App；不会自动继续任何任务。']) assert.ok(appSource.includes(token));
  const nativeSource = await readFile(resolve(import.meta.dirname, '../src-tauri/src/desktop_local_backup_v1.rs'), 'utf8');
  for (const token of ['VACUUM INTO', 'manifest_sha256', 'PRESERVED_DEVICE_TABLES', 'INTERRUPTED', 'RESTORED_RESTART_REQUIRED', 'recover_interrupted_switch', 'checkpoint']) assert.ok(nativeSource.includes(token));
  const capability = await readFile(resolve(import.meta.dirname, '../src-tauri/capabilities/default.json'), 'utf8');
  const permission = await readFile(resolve(import.meta.dirname, '../src-tauri/permissions/default.toml'), 'utf8');
  assert.ok(capability.includes('allow-desktop-local-backup-restore'));
  assert.ok(permission.includes('commands.allow = ["export_desktop_local_backup", "preflight_desktop_local_backup", "restore_desktop_local_backup", "cancel_desktop_local_restore", "read_desktop_local_backup_status"]'));
});

test('P6 v2 picker preserves the content-free native rejection instead of replacing it with a generic success-like status', () => {
  const picker = source.slice(source.indexOf('async function pickV2WorkspaceExchange'), source.indexOf('async function pickNanfengKnowledgeExport'));
  assert.ok(picker.includes("extensions: ['nfai-exchange', 'zip']"));
  assert.ok(picker.includes('state.status = state.error;'));
  assert.ok(!picker.includes("state.status = '未创建可见工作区或导入记录。'"));
});

test('P6 v2 re-export owner remains private while the latest Android settings surface does not invent a second row', () => {
  const rendered = renderChatFirstShell({ data: fixture, native: true, selectedConversationId: null, composerDraft: '', chatSearch: '', profileOpen: false, sidebarOpen: false, railCollapsed: false, showArchived: false, pane: 'settings', settingsSection: 'data', status: '', error: '', connection: {}, v2CommittedExchanges: [{ workspaceId: 'workspace-v2-safe', rootCounts: { projects: 1 }, assetCount: 0 }] });
  assert.ok(!rendered.includes('reexport-v2-workspace-exchange'));
  const reexport = source.slice(source.indexOf('async function reexportV2WorkspaceExchange'), source.indexOf('async function pickNanfengKnowledgeExport'));
  for (const token of ["dialogInvoke('save'", 'reexport_desktop_workspace_exchange_v2_selected', "extensions: ['nfai-exchange']", 'state.status = state.error']) assert.ok(reexport.includes(token));
  for (const forbidden of ['Composer', 'show-chat', 'start-export']) assert.ok(!reexport.includes(forbidden));
});

test('P6-K exposes current ZIP imports and keeps recovery controls inside ZIP import results', () => {
  const dataPage = renderChatFirstShell({ data: fixture, native: true, pane: 'settings', settingsSection: 'data', status: '', error: '', connection: {} });
  for (const token of ['data-action="select-p6k-chatgpt-zip"', 'data-action="select-p6k-claude-zip"', 'data-page="zip-import-results"']) assert.ok(dataPage.includes(token));
  const results = renderChatFirstShell({ data: fixture, native: true, pane: 'settings', settingsSection: 'zip-import-results', status: '', error: '', connection: {}, p6kTask: { provider: 'CHATGPT', status: 'PARTIAL', importedCount: 2, failedCount: 1, skippedCount: 0 } });
  for (const token of ['ZIP 导入结果', '已导入 2', '失败 1', '按 ChatGPT / Claude 官方身份去重', '不会复活', 'data-action="retry-p6k-zip"', 'data-action="skip-p6k-zip-failures"', 'data-action="delete-p6k-zip-batch"']) assert.ok(results.includes(token));
  for (const token of ["kind: 'p6k-batch-delete'", '删除标记会长期保留', 'data-action="confirm-delete-p6k-zip-batch"', "invoke('delete_p6k_zip_import_batch'"]) assert.ok(source.includes(token));
  assert.match(temporaryPermission, /identifier = "allow-p6k-zip-import"[\s\S]*commands\.allow = \[[^\]]*"link_p6k_zip_manual_asset"/);
});

test('settings primary menu is the latest Android hierarchy and contains no retired desktop registry', () => {
  const rendered = renderChatFirstShell({ data: fixture, native: true, pane: 'settings', settingsSection: 'personalization', status: '', error: '', connection: {} });
  const labels = ['个性化', '模型与联网', '提醒', '对话管理', '外观', '字体大小', '主题色', 'Google 账号与同步', '导入与导出', '本机数据', '关于', '工作区', '项目与知识', '开发与诊断'];
  labels.forEach(label => assert.ok(rendered.includes(label)));
  for (const retired of ['搜索设置', '功能审阅', 'AI 模型服务', '基础</p>']) assert.ok(!rendered.includes(retired));
});

test('current workspace settings route preserves existing project and knowledge owners', () => {
  const control = renderChatFirstShell({ data: fixture, native: true, pane: 'settings', settingsSection: 'workspace', status: '', error: '', connection: {} });
  for (const token of ['项目与知识', 'data-action="show-projects"', 'data-action="show-knowledge"', '管理 Projects', '管理知识库']) assert.ok(control.includes(token));
  for (const forbidden of ['chat-composer', 'data-action="save-local-message"']) assert.ok(!control.includes(forbidden));
});

test('FB-P6-039 keeps attachment previews as role-aligned siblings of text surfaces', () => {
  const data = {
    summary: { id: 'workspace-attachments' },
    exchange: { conversations: [{
      id: 'attachment-conversation', title: '附件布局', archived: false, updatedAt: '2026-08-14T00:00:00Z', revision: 1,
      messages: [
        { id: 'user-mixed', role: 'user', createdAt: '2026-08-14T00:00:00Z', blocks: [{ kind: 'TEXT', text: '用户正文' }, { kind: 'ASSET_REF', asset: { id: 'user-pdf', displayName: '用户文件.pdf', mimeType: 'application/pdf' } }] },
        { id: 'assistant-mixed', role: 'assistant', createdAt: '2026-08-14T00:01:00Z', blocks: [{ kind: 'TEXT', text: '助手正文' }, { kind: 'ASSET_REF', asset: { id: 'assistant-pdf', displayName: '助手文件.pdf', mimeType: 'application/pdf' } }] },
        { id: 'user-text-only', role: 'user', createdAt: '2026-08-14T00:02:00Z', blocks: [{ kind: 'TEXT', text: '仅用户文本' }] },
        { id: 'assistant-attachment-only', role: 'assistant', createdAt: '2026-08-14T00:03:00Z', blocks: [{ kind: 'ASSET_REF', asset: { id: 'assistant-image', displayName: '助手图片.png', mimeType: 'image/png' } }, { kind: 'ASSET_REF', asset: { id: 'assistant-audio', displayName: '助手音频.mp3', mimeType: 'audio/mpeg' } }] },
      ],
    }] },
  };
  const rendered = renderChatFirstShell({ data, native: true, selectedConversationId: 'attachment-conversation', composerDraft: '', chatSearch: '', profileOpen: false, sidebarOpen: false, railCollapsed: false, showArchived: false, pane: 'chat', status: '', error: '', connection: {} });
  const user = rendered.slice(rendered.indexOf('data-message-id="user-mixed"'), rendered.indexOf('data-message-id="assistant-mixed"'));
  const assistant = rendered.slice(rendered.indexOf('data-message-id="assistant-mixed"'), rendered.indexOf('data-message-id="user-text-only"'));
  const userTextOnly = rendered.slice(rendered.indexOf('data-message-id="user-text-only"'), rendered.indexOf('data-message-id="assistant-attachment-only"'));
  const assistantAttachmentOnly = rendered.slice(rendered.indexOf('data-message-id="assistant-attachment-only"'));
  for (const segment of [user, assistant]) {
    assert.ok(segment.includes('chat-message-bubble'));
    assert.ok(segment.includes('chat-message-attachments'));
    assert.ok(segment.indexOf('chat-message-attachments') < segment.indexOf('chat-message-bubble'));
    assert.ok(!segment.slice(segment.indexOf('chat-message-attachments'), segment.indexOf('chat-message-bubble')).includes('chat-message-body'));
  }
  assert.ok(rendered.includes('<article class="chat-message user"'));
  assert.ok(rendered.includes('<article class="chat-message assistant"'));
  assert.ok(userTextOnly.includes('chat-message-bubble'));
  assert.ok(!userTextOnly.includes('chat-message-attachments'));
  assert.ok(!assistantAttachmentOnly.includes('chat-message-bubble'));
  assert.ok(assistantAttachmentOnly.includes('chat-message-attachments'));
  assert.equal((assistantAttachmentOnly.match(/chat-(?:image|audio)-attachment/g) || []).length, 2);
  for (const token of ['.chat-message.user .chat-message-attachments { align-self: flex-end', '.chat-message.assistant .chat-message-attachments { justify-content: flex-start']) assert.ok(css.includes(token));
  assert.ok(css.includes('margin-bottom: 8px'));
  assert.ok(shell.includes('chat-attachment-info-overlay'));
});

test('desktop composer keeps the textarea visually borderless in every interaction state', () => {
  for (const token of ['.chat-composer {', 'border: 0;', '.chat-composer:focus-within { border-color: transparent; outline: 0;', '.chat-composer #chat-composer,', '.chat-composer #chat-composer:hover,', '.chat-composer #chat-composer:focus,', '.chat-composer #chat-composer:focus-visible {', 'resize: none;', 'border: 0 !important;', 'outline: 0 !important;', 'box-shadow: none !important;', 'appearance: none;', '-webkit-appearance: none;']) assert.ok(css.includes(token));
  for (const forbidden of ['.chat-composer:focus-within { border-color: var(--accent-orange)', '.chat-composer:focus-within { border-color: var(--accent-subtle-border)', '0 0 0 3px', 'border: 5px']) assert.ok(!css.includes(forbidden));
});

test('desktop composer add control keeps a circular interactive surface in hover, press and open states', () => {
  for (const token of ['.chat-composer-icon {', 'border-radius: 50% !important;', '.chat-composer-add:hover', '.chat-composer-add:active', '.chat-composer-add[aria-expanded="true"]']) assert.ok(css.includes(token), token);
});

test('desktop send glyph is optically reduced inside its unchanged circular target', () => {
  assert.ok(css.includes('.chat-composer-actions .chat-send svg { position: relative; z-index: 1; width: 16px; height: 16px;'), 'send glyph should be 20 percent smaller than its former 20px size');
});

test('desktop accent tokens route primary interaction without recoloring success semantics', () => {
  for (const token of ['--accent-orange: #e97128', '--accent-orange-hover: #d86520', '--accent-orange-pressed: #c2581a', '--accent-subtle-border: #efbd94', '--accent-orange-soft: #fff1e5', '--accent-disabled: #f4c8aa', 'rgb(233 113 40 / 22%)', 'rgb(233 113 40 / 18%)', 'data-action="new-chat"', 'background: var(--accent-orange)', 'background: var(--accent-orange-soft)']) assert.ok(css.includes(token));
  assert.ok(!css.includes('--chat-green'));
});

test('FB-P6-028 desktop conversation rows are one-line absolute dates with keyboard-revealed actions', () => {
  for (const token of ['.chat-history-label', 'position: relative', 'grid-template-columns: minmax(0, 1fr) auto', '.chat-history-select', 'font-size: 12px', 'text-overflow: ellipsis', '.chat-history-date', 'justify-self: end', 'text-align: right', 'white-space: nowrap', '.chat-row-actions { position: absolute', '.chat-history-row:focus-within .chat-history-date', 'visibility: hidden']) assert.ok(css.includes(token));
  assert.equal(conversationLocalDate({ updatedAt: '2026-08-14T00:00:00Z' }), '2026/08/14');
  assert.equal(conversationLocalDate({ updatedAt: '2026-08-15T00:00:00Z' }), '2026/08/15');
  assert.equal(conversationLocalDate({ createdAt: '2026-08-10T00:00:00Z', updatedAt: '2026-08-15T00:00:00Z' }), '2026/08/10');
  for (const forbidden of ["return '昨天'", "hour: '2-digit'", '<small>${escapeHtml(conversationLocalDate(item))}</small>']) assert.ok(!shell.includes(forbidden));
  assert.ok(!css.includes('.chat-history-select { display: block !important; min-width: 0; flex: 1 1 auto; overflow: hidden; padding: 7px !important; font-size: 14px'));
});

test('FB-P6-026 keeps divider diagnostics acceptance-only while exposing pointer, keyboard and reset owners', () => {
  assert.match(shell, /<button type="button" class="chat-sidebar-divider" role="separator"[\s\S]*aria-orientation="vertical"[\s\S]*aria-valuemin="220"[\s\S]*aria-valuemax="440"/);
  for (const token of ['p6hDiagnosticsEnabled', "event.target.closest?.('.chat-sidebar-divider')", 'P6-H acceptance divider ${event.type}: ${state.sidebarWidth}px', 'divider.focus({ preventScroll: true })', 'setPointerCapture', 'pointermove', "event.key === 'ArrowRight'", "event.key === 'Home'", "event.key === 'End'", 'dblclick', 'persistSidebarWidth']) assert.ok(source.includes(token), token);
  assert.ok(!source.includes('P6-H acceptance divider ${event.type}: ${state.sidebarWidth}px`\n;'));
});

test('FB-P6-036 keeps the user-requested 2px divider with an 8px interaction target', () => {
  for (const token of ['--chat-sidebar-divider-hit: 8px', '--chat-sidebar-divider-active-stroke: 2px', 'width: var(--chat-sidebar-divider-hit)', 'width: var(--chat-sidebar-divider-active-stroke)', 'background: transparent !important', 'outline: 0 !important', 'box-shadow: none !important', 'setPointerCapture', "event.key === 'ArrowRight'", 'dblclick', 'persistSidebarWidth']) assert.ok(`${css}\n${source}`.includes(token), token);
  assert.match(css, /chat-sidebar-divider:hover::after[\s\S]*chat-sidebar-divider\.dragging::after \{ width: var\(--chat-sidebar-divider-active-stroke\)/);
});

test('desktop supplements hover actions with the same compact context-menu format as Android and registered local attachment picker', () => {
  const rendered = renderChatFirstShell({ data: fixture, native: true, selectedConversationId: 'new', composerDraft: '草稿不应丢失', chatSearch: '', profileOpen: false, sidebarOpen: false, railCollapsed: false, showArchived: false, showDeleted: false, contextMenu: { id: 'new', revision: 2, pinned: false, archived: false }, composerAddOpen: true, pane: 'chat', status: '', error: '', connection: {} });
  for (const token of ['chat-context-menu', 'context-menu-pin', 'context-menu-share', 'context-menu-find', 'context-menu-delete', 'chat-menu-action-icon', 'chat-context-menu-item danger', 'composer-add-sheet', 'composer-add-anchor', 'pick-composer-image', 'pick-composer-file', '添加图片', '添加文件']) assert.ok(rendered.includes(token));
  for (const forbidden of ['危险操作', 'chat-context-menu-danger']) assert.ok(!rendered.includes(forbidden));
  for (const forbidden of ['选择后立即私有复制', '添加到草稿</strong><p>']) assert.ok(!rendered.includes(forbidden));
  assert.ok(rendered.includes('context-menu-archive'));
  for (const token of ['contextmenu', 'createConversationLongPressController', "closest?.('[data-conversation-row]')", 'conversationLongPress.pointerMove', 'conversationLongPress.pointerUp', 'conversationLongPress.pointerCancel', 'conversationLongPress.consumeClick', "conversationLongPress.cancel('scroll')", 'setProject', 'softDelete', 'restoreDeleted', 'conversation-delete', 'data-positioned="false"']) assert.ok(`${source}\n${shell}`.includes(token), token);
});

test('desktop composer gives draft images the same in-composer thumbnail treatment as Android', () => {
  const rendered = renderChatFirstShell({ data: fixture, native: true, selectedConversationId: 'new', composerDraft: '', composerAttachments: [{ id: 'image-1', displayName: '示例图片.png', mimeType: 'image/png', sha256: 'a'.repeat(64) }], imageThumbnails: { 'image-1': { dataUrl: 'data:image/png;base64,AA==' } }, chatSearch: '', profileOpen: false, sidebarOpen: false, railCollapsed: false, showArchived: false, pane: 'chat', status: '', error: '', connection: {} });
  for (const token of ['chat-composer-image-preview', 'chat-composer-image-open', 'data-action="open-image-preview"', 'data-attachment-id="image-1"', 'data-image-thumbnail="image-1"', 'aria-label="预览图片：示例图片.png"', '示例图片.png 草稿缩略图', 'remove-composer-attachment']) assert.ok(rendered.includes(token), token);
  for (const token of ['.chat-composer-image-preview', '.chat-composer-image-open', 'width: 62px', 'object-fit: cover', '.chat-composer-image-name']) assert.ok(css.includes(token), token);
});

test('desktop composer camera is a real private-capture path before image and file pickers', async () => {
  const rendered = renderChatFirstShell({ data: fixture, native: true, selectedConversationId: 'new', composerDraft: '', composerAddOpen: true, chatSearch: '', profileOpen: false, sidebarOpen: false, railCollapsed: false, showArchived: false, pane: 'chat', status: '', error: '', connection: {} });
  const camera = rendered.indexOf('data-action="open-composer-camera"');
  const image = rendered.indexOf('data-action="pick-composer-image"');
  const file = rendered.indexOf('data-action="pick-composer-file"');
  assert.ok(camera >= 0 && camera < image && image < file, 'camera/image/file order must match Android');
  assert.match(rendered, />相机<\/span>/);

  const source = await readFile(resolve(import.meta.dirname, '../src/app.mjs'), 'utf8');
  for (const token of [
    'navigator.mediaDevices.getUserMedia',
    "video: { facingMode: 'environment' }",
    "canvas.toDataURL('image/png')",
    'import_desktop_camera_capture',
    'import_desktop_temporary_camera_capture',
    '停止相机',
  ]) assert.ok(source.includes(token), token);
});

test('context menu is measured from the replacement title after render, never from a discarded row', () => {
  for (const token of ['function positionConversationContextMenu()', "[...document.querySelectorAll('[data-conversation-row]')].find", "item.dataset.id === state.contextMenu.id", "menu.style.left = `${position.x}px`", "menu.style.top = `${position.y}px`", "menu.dataset.positioned = 'true'", 'positionConversationContextMenu();']) assert.ok(source.includes(token));
  for (const token of ['.chat-context-menu {', 'visibility: hidden;', '.chat-context-menu[data-positioned="true"] { visibility: visible; }']) assert.ok(css.includes(token));
  assert.ok(!shell.includes('style="left:${menu.x}px;top:${menu.y}px"'));
});

test('FB-P6-067 desktop rename dialog keeps the field and actions without a redundant heading', () => {
  for (const token of ["state.dialog?.kind === 'conversation-rename'", '会话标题<input id="conversation-rename"', 'save-conversation-rename', 'data-action="close-dialog">取消']) assert.ok(source.includes(token));
  assert.ok(!source.includes("kind === 'conversation-rename') return `<div class=\"scrim\"><section class=\"dialog\" role=\"dialog\" aria-modal=\"true\"><h2>重命名会话</h2>"));
});

test('FB-P6-024 gives only user messages an orange bounded bubble while assistant stays open', () => {
  for (const token of ['.chat-message.user .chat-message-content', 'max-width: min(82%, 530px)', 'background: var(--accent-orange-soft)', '.chat-message.assistant .chat-message-content { max-width: 100%; }', '.chat-message.system .chat-message-content', '.chat-message.tool .chat-message-content', '.chat-attachment-chip']) assert.ok(css.includes(token));
  assert.ok(!css.includes('.chat-message.assistant { background: var(--assistant-surface)'));
});

test('FB-P6-034 keeps USER tools low-noise while assistant controls and factual metadata stay visibly outside the body', () => {
  const local = transcriptMetadata({ id: 'local', createdAt: '2026-08-14T00:00:00Z', role: 'assistant', blocks: [{ kind: 'TEXT', text: '本地内容' }] });
  assert.equal(local.source, null);
  assert.equal(local.model, null);
  assert.ok(local.timestamp);
  const provider = transcriptMetadata({
    id: 'provider', role: 'assistant', createdAt: '2026-08-14T00:00:00Z', source: 'PROVIDER',
    modelSnapshot: { displayName: '已冻结模型', modelId: 'model-v1', providerId: 'provider-a', registrySnapshotId: 'registry-v1' },
    run: { startedAt: '2026-08-14T00:00:00Z', completedAt: '2026-08-14T00:00:02Z' },
  });
  assert.equal(provider.model, '已冻结模型');
  assert.equal(provider.workDuration, '用时 2 秒');
  assert.equal(messagePlainText({ blocks: [{ kind: 'TEXT', text: '可复制正文' }, { kind: 'ASSET_REF', asset: { displayName: '附件.txt', mimeType: 'text/plain', privatePath: '/not-visible' } }] }), '可复制正文\n附件.txt · text/plain');
  assert.ok(transcriptLocalDateKey({ createdAt: '2026-08-14T00:00:00Z' }).includes('2026'));
  const rendered = renderChatFirstShell({ data: { summary: { id: 'one' }, exchange: { conversations: [{ id: 'one', title: 'P6-F', messages: [{ id: 'message-one', role: 'assistant', createdAt: '2026-08-14T00:00:00Z', blocks: [{ kind: 'TEXT', text: '本地内容' }] }] }] } }, native: true, selectedConversationId: 'one', composerDraft: '', chatSearch: '', profileOpen: false, sidebarOpen: false, pane: 'chat', status: '', error: '', connection: {} });
  for (const token of ['data-action="copy-message"', 'data-action="share-message"', 'data-action="open-assistant-message-menu"', 'chat-date-divider', 'tabindex="0"']) assert.ok(rendered.includes(token));
  assert.ok(!rendered.includes('export-assistant-markdown'));
  for (const token of ['chat-message-bubble', 'chat-message-tools']) assert.ok(rendered.includes(token));
  for (const forbidden of ['LOCAL_RECORD', '模型未知', '用时未知', 'GMT+8', 'chat-message-role']) assert.ok(!rendered.includes(forbidden));
  for (const token of ['.chat-message:hover .chat-message-tools', '.chat-message:focus-within .chat-message-tools', '.chat-message.assistant .chat-message-tools { opacity: 1; pointer-events: auto; }', '.chat-message.user .chat-message-bubble', '.chat-message.user .chat-message-tools { justify-content: flex-end;', 'navigator.share', 'navigator.clipboard.writeText']) assert.ok(`${css}\n${source}`.includes(token));
  assert.ok(!css.includes('.chat-message.user .chat-message-tools { opacity: 1'));
  assert.ok(source.includes("action === 'copy-message'"));
  assert.ok(source.includes('navigator.clipboard.writeText'));
  assert.ok(source.includes("action: 'branchFromMessage'"));
});

test('FB-P6-044 renders only persisted assistant run or import duration above its open body', () => {
  assert.equal(assistantWorkDuration({ role: 'user', run: { durationMs: 2000 } }), null);
  assert.equal(assistantWorkDuration({ role: 'assistant', createdAt: '2026-08-14T00:00:00Z' }), null);
  assert.equal(assistantWorkDuration({ role: 'assistant', run: { durationMs: 0 } }), null);
  assert.equal(assistantWorkDuration({ role: 'assistant', import: { durationMs: 65000 } }), '用时 1 分 5 秒');
  const rendered = renderChatFirstShell({ data: { summary: { id: 'duration' }, exchange: { conversations: [{ id: 'duration', title: 'Duration', messages: [
    { id: 'assistant-duration', role: 'assistant', createdAt: '2026-08-14T00:00:00Z', run: { startedAt: '2026-08-14T00:00:00Z', completedAt: '2026-08-14T00:00:02Z' }, blocks: [{ kind: 'TEXT', text: '有真实时长的正文' }] },
    { id: 'user-duration', role: 'user', createdAt: '2026-08-14T00:00:03Z', run: { durationMs: 2000 }, blocks: [{ kind: 'TEXT', text: '用户消息' }] },
  ] }] } }, native: true, selectedConversationId: 'duration', composerDraft: '', chatSearch: '', profileOpen: false, sidebarOpen: false, pane: 'chat', status: '', error: '', connection: {} });
  assert.ok(rendered.includes('chat-message-work-duration'));
  assert.ok(rendered.indexOf('用时 2 秒') < rendered.indexOf('有真实时长的正文'));
  assert.equal((rendered.match(/chat-message-work-duration/g) || []).length, 1);
  assert.ok(css.includes('.chat-message-work-duration'));
});

test('context menu anchors to the selected row, flips, clamps, and closes on scroll or keyboard dismissal', () => {
  assert.deepEqual(resolveConversationMenuAnchor({ left: 22, right: 250, top: 120, bottom: 150 }, { viewportWidth: 1200, viewportHeight: 800, sidebarRect: { left: 0, top: 0, right: 256, bottom: 800 }, menuWidth: 192, menuHeight: 204 }), { x: 22, y: 156 });
  const flipped = resolveConversationMenuAnchor({ left: 220, right: 252, top: 690, bottom: 720 }, { viewportWidth: 1200, viewportHeight: 800, sidebarRect: { left: 0, top: 0, right: 256, bottom: 800 }, menuWidth: 192, menuHeight: 204 });
  assert.equal(flipped.x, 58);
  assert.equal(flipped.y, 480);
  assert.deepEqual(resolveConversationMenuAnchor({ left: 22, right: 250, top: 120, bottom: 150 }, { viewportWidth: 1200, viewportHeight: 800, sidebarRect: { left: 900, top: 0, right: 900, bottom: 0 }, menuWidth: 192, menuHeight: 204 }), { x: 22, y: 156 });
  for (const token of ['resolveConversationMenuAnchor', 'getBoundingClientRect()', "row.querySelector('.chat-history-select > span')", 'document.documentElement.clientWidth', 'document.documentElement.clientHeight', "event.key === 'ContextMenu'", "event.shiftKey && event.key === 'F10'", "window.addEventListener('scroll'", "window.addEventListener('resize'", "document.addEventListener('pointerdown'", "document.addEventListener('keydown'", 'closeConversationContextMenu']) assert.ok(`${shell}\n${source}`.includes(token));
});

test('desktop app-owned overlays share a topmost cancel-only dismiss owner', () => {
  for (const token of ['function closeTopOverlay', 'function openTransientOverlay', 'pointerdown', 'event.target.classList?.contains(\'scrim\')', 'A scrim is always cancel-only', 'restoreOverlayFocus', 'document.addEventListener(\'keydown\'', 'event.key === \'Escape\' && closeTopOverlay({ navigateComposerLayerBack: true })', 'state.contextMenu = kind === \'context\' ? value : null']) assert.ok(source.includes(token));
  for (const forbidden of ['clear_desktop_temporary_conversation();', 'confirm-conversation-delete();']) assert.ok(!source.includes(forbidden));
});

test('FB-P6-037 recycle confirmation state machine guards duplicates, keeps failures recoverable, and clears only after receipt', () => {
  const original = { kind: 'conversation-delete', id: 'conversation-1', revision: 7 };
  assert.deepEqual(beginConversationRecycle(original, 'conversation-1'), { ...original, submitting: true, failure: null });
  assert.equal(beginConversationRecycle({ ...original, submitting: true }, 'conversation-1'), null, 'double-submit is blocked');
  assert.equal(beginConversationRecycle(original, 'conversation-other'), null, 'stale target is blocked');
  assert.deepEqual(failConversationRecycle(original, 'revision conflict'), { ...original, submitting: false, failure: '操作未完成：revision conflict' });
  assert.deepEqual(completeConversationRecycle(), { dialog: null, status: '会话已移入回收站；消息树未物理删除。', focusAction: 'new-chat' });
  for (const token of ['async function confirmConversationDelete', 'beginConversationRecycle', 'action: \'softDelete\'', 'state.selectedConversationId === dialog.id', 'completeConversationRecycle', 'state.dialog = null;', 'await refresh()', 'focusAction', 'failConversationRecycle']) assert.ok(source.includes(token), token);
  assert.match(source, /data-action="confirm-conversation-delete"[\s\S]*\$\{busy \? 'disabled' : ''\}/);
  assert.match(source, /data-action="close-dialog" \$\{busy \? 'disabled' : ''\}/);
});

test('local composer truthfully distinguishes native, missing workspace and web preview', () => {
  assert.equal(localMessagePathState({ native: true, hasWorkspace: true }).enabled, true);
  assert.equal(localMessagePathState({ native: true, hasWorkspace: false }).label, '需要本地工作区');
  assert.equal(localMessagePathState({ native: false, hasWorkspace: true }).label, 'Web 预览');
});

test('dual-path status remains separate and consumes the native capability snapshot', () => {
  const blocked = deriveDesktopDualPathState({ connection: { providerConfiguration: 'NOT_CONFIGURED', syncCapability: 'ENCRYPTED_SYNC_NOT_CONFIGURED' } });
  assert.equal(blocked.local.label, '本地可用');
  assert.equal(blocked.provider.label, '联网未配置');
  assert.equal(blocked.sync.label, '加密同步未配置');
  const configured = deriveDesktopDualPathState({ connection: { providerConfiguration: 'READY_FOR_GUARD', syncCapability: 'ENCRYPTED_SYNC_READY' } });
  assert.equal(configured.provider.label, '已配置，仍需逐次确认');
  assert.equal(configured.sync.label, '配置已发现，等待已验证账号');
});

test('default desktop shell is chat-first and local save uses the existing typed Rust mutation', () => {
  for (const token of ["pane: 'chat'", 'renderChatFirstShell', 'saveLocalMessage', "action: 'appendMessage'", "action: 'create'", "role: 'user'"]) assert.ok(source.includes(token));
  assert.ok(!source.includes("navigation.insertAdjacentHTML('beforeend'"));
  assert.ok(!source.includes("pane: 'connection'"));
});

test('rendered first screen follows the lightweight sidebar, single canvas and fixed composer contract', () => {
  const html = renderChatFirstShell({ data: fixture, native: true, selectedConversationId: null, composerDraft: '', profileOpen: false, pane: 'chat', status: '本地就绪', error: '', connection: {} });
  for (const token of ['chat-sidebar', '新对话', 'chat-main', 'chat-empty-canvas', 'chat-composer', '对话', '工作']) assert.ok(html.includes(token));
  assert.ok(!html.includes('今天想一起做什么？'));
  for (const token of ['title="选择模型 · DS V4"', 'aria-label="发送消息"', 'title="发送消息"']) assert.ok(html.includes(token));
  assert.ok(!html.match(/data-action="toggle-p6g-model-picker"[^>]*disabled/));
  assert.ok(!html.includes('open-compare-confirmation'));
  for (const removed of ['发送时自动保存到当前工作区', '不调用模型', '配置模型后可生成回答']) assert.ok(!html.includes(removed));
  assert.ok(!html.includes('>发送</button>'));
  assert.ok(!html.includes('>保存</button>'));
  const settings = renderChatFirstShell({ data: fixture, native: true, selectedConversationId: null, composerDraft: '', profileOpen: false, pane: 'settings', settingsSection: 'data', status: '导入任务待处理', error: '', connection: {} });
  for (const token of ['android-settings-primary', 'aria-label="返回应用"', '设置一级菜单', '导入与导出', '导入 ChatGPT JSON', '导入任务待处理']) assert.ok(settings.includes(token));
  const settingsError = renderChatFirstShell({ data: fixture, native: true, selectedConversationId: null, composerDraft: '', profileOpen: false, pane: 'settings', settingsSection: 'personalization', status: '旧状态', error: '设置 revision 冲突', connection: {} });
  assert.ok(settingsError.includes('设置 revision 冲突'));
  assert.ok(!settingsError.includes('旧状态'));
  for (const token of ['grid-template-columns: var(--chat-sidebar-width', 'chat-sidebar-divider', '.chat-composer-wrap', '#fff', 'overflow: hidden']) assert.ok(css.includes(token));
  for (const token of ['chat-shell.mjs', 'conversation-long-press.mjs', 'chat-shell.css', 'nanfeng-ai-icon.png']) assert.ok(build.includes(token));
  assert.ok(!build.includes('desktop-compare-execution-owner.mjs'));
});

test('FB-P6-070 removes persistent Composer implementation copy while preserving send semantics', () => {
  const html = renderChatFirstShell({ data: fixture, native: true, selectedConversationId: 'ordinary', composerDraft: '', profileOpen: false, pane: 'chat', status: '', error: '', connection: {} });
  for (const removed of ['发送时自动保存到当前工作区', '不调用模型', '配置模型后可生成回答']) assert.ok(!html.includes(removed));
  assert.ok(html.includes('aria-label="发送消息"'));
});

test('FB-P6-071 removes the redundant Desktop sidebar brand subtitle', () => {
  const html = renderChatFirstShell({ data: fixture, native: true, selectedConversationId: 'ordinary', composerDraft: '', profileOpen: false, pane: 'chat', status: '', error: '', connection: {} });
  assert.ok(html.includes('<button data-action="show-chat"><strong>南枫 AI</strong></button>'));
  assert.ok(!html.includes('对话与工作'));
});

test('P6-G composer mirrors the Android Auto Daily Deep hierarchy while preserving the selected model fact', () => {
  const selection = { catalog: { snapshot: { candidates: [{ providerFamily: 'ANTHROPIC', modelId: 'anthropic.fixture', displayName: 'Anthropic fixture', available: true, tiers: ['DEEP'] }] } }, conversationOverride: { revision: 2, modelId: 'anthropic.fixture' }, lastRoute: { reason: 'MANUAL_OVERRIDE', displayName: 'Anthropic fixture' } };
  const html = renderChatFirstShell({ data: fixture, native: true, selectedConversationId: 'ordinary', composerDraft: '', profileOpen: false, pane: 'chat', status: '', error: '', connection: {}, p6gSelection: selection, p6gModelPickerOpen: true });
  for (const token of ['选择模型 · Anthropic fixture', 'composer-model-sheet', 'composer-model-sheet-scrim', 'select-p6g-auto', 'select-p6g-tier', '日常问答与轻量任务', '复杂推理与专业分析', 'Anthropic fixture']) assert.ok(html.includes(token));
  for (const forbidden of ['p6g-conversation-model', '当前会话模型', 'MANUAL_OVERRIDE', '最近路由']) assert.ok(!html.includes(forbidden));
  assert.ok(!html.includes('temporary-model-override'));
});

test('Composer trigger uses the Android compact model name while the picker keeps the full catalog name', () => {
  const selection = { catalog: { snapshot: { candidates: [{ providerId: 'deepseek', modelId: 'deepseek-v4-flash', displayName: 'DeepSeek V4 Flash', available: true, tiers: ['FAST'] }] } }, conversationOverride: { revision: 1, modelId: null }, lastRoute: { displayName: 'DeepSeek V4 Flash' } };
  const html = renderChatFirstShell({ data: fixture, native: true, selectedConversationId: 'new', composerDraft: '', pane: 'chat', status: '', error: '', connection: {}, p6gModelPickerOpen: true, p6gSelection: selection });
  assert.match(html, /aria-label="选择模型：DS V4"/);
  assert.match(html, /Auto · DeepSeek V4 Flash/);
});

test('empty new-chat canvas keeps the Android Auto Daily Deep picker available before a conversation exists', () => {
  assert.ok(source.includes('pendingNewConversationModelId: state.pendingNewConversationModelId'));
  const root = renderChatFirstShell({ data: fixture, native: true, selectedConversationId: null, composerDraft: '', pane: 'chat', status: '', error: '', connection: {}, p6gModelPickerOpen: true });
  for (const token of ['title="选择模型 · DS V4"', 'Auto · DeepSeek V4 Flash', 'select-p6g-auto', 'select-p6g-tier', '日常', '深度']) assert.ok(root.includes(token), token);
  assert.ok(!root.match(/data-action="toggle-p6g-model-picker"[^>]*disabled/));

  const daily = renderChatFirstShell({ data: fixture, native: true, selectedConversationId: null, composerDraft: '', pane: 'chat', status: '', error: '', connection: {}, p6gModelPickerOpen: true, p6gSelection: { pickerTier: 'DAILY' } });
  for (const token of ['DeepSeek V4 Flash', 'Claude Sonnet 5', 'GPT-5.6 Terra', 'GLM-5.3 Flash', 'Qwen3.7-Plus', 'Gemini 3.7 Flash']) assert.ok(daily.includes(token), token);
  for (const token of ['选择具体模型', 'OpenRouter · 未联网', '智谱 · 未联网', '千问 · 未联网']) assert.ok(daily.includes(token), token);
  for (const removed of ['高效处理日常工作。', '适合快速问答与高频文本任务。', '能力与成本更均衡。', '智谱官方直连的快速文本任务。', '日常问答与轻量多媒体任务。', '快速处理文字、图片和文件任务。', '<small>OPENROUTER</small>', '<small>DEEPSEEK</small>', '<small>ZHIPU</small>', '<small>QWEN</small>', '本地确定性 fixture']) assert.ok(!daily.includes(removed), removed);
  assert.ok(css.includes('.composer-model-sheet-header > strong {'));
  assert.ok(css.includes('.composer-model-sheet-card small {'));
});

test('P6-G Daily and Deep groups open a complete manual candidate list instead of silently picking the first model', () => {
  const selection = { catalog: { snapshot: { candidates: [
    { providerId: 'deepseek', modelId: 'deepseek-v4-flash', displayName: 'DeepSeek V4 Flash', available: true, tiers: ['FAST', 'BALANCED'] },
    { providerId: 'openrouter', modelId: 'gpt-5.6-terra', displayName: 'GPT-5.6 Terra', available: true, tiers: ['BALANCED'] },
    { providerId: 'openrouter', modelId: 'gpt-5.6-sol', displayName: 'GPT-5.6 Sol', available: true, tiers: ['DEEP'] },
  ] } }, conversationOverride: { revision: 1, modelId: null }, lastRoute: { displayName: 'DeepSeek V4 Flash' }, pickerTier: 'DAILY' };
  const html = renderChatFirstShell({ data: fixture, native: true, selectedConversationId: 'new', composerDraft: '', pane: 'chat', status: '', error: '', connection: {}, p6gModelPickerOpen: true, p6gSelection: selection });
  for (const token of ['p6g-picker-back', 'DeepSeek V4 Flash', 'GPT-5.6 Terra', 'select-p6g-model', 'data-model-id="deepseek-v4-flash"']) assert.ok(html.includes(token), token);
  assert.ok(!html.includes('data-model-id="gpt-5.6-sol"'));
  for (const token of ["state.p6gSelection = { ...(state.p6gSelection || {}), pickerTier:", "action === 'p6g-picker-back'", 'updateP6GConversationOverride(target.dataset.modelId']) assert.ok(source.includes(token), token);
});

test('Compare execution is absent while historical native Compare branches remain readable', async () => {
  const html = renderChatFirstShell({ data: fixture, native: true, selectedConversationId: 'ordinary', composerDraft: '对比内容', profileOpen: false, pane: 'chat', status: '', error: '', connection: {}, p6gModelPickerOpen: true });
  assert.equal((html.match(/open-compare-confirmation/g) || []).length, 0);
  for (const token of ['对比 ChatGPT 与 Claude', 'OpenRouter · GPT-5.6 Terra + Claude Sonnet 5']) assert.ok(!html.includes(token));
  assert.ok(!html.includes('data-compare-long-press'));
  const appSource = await readFile(resolve(root, 'src/app.mjs'), 'utf8');
  for (const token of ['async function executeDesktopCompare()', "invoke('submit_desktop_compare'", "invoke('retry_desktop_compare_branch'", 'open-compare-confirmation']) assert.ok(!appSource.includes(token), token);
  for (const removed of ['DesktopCompareExecutionOwner', 'desktopCompareExecutionOwner.requestDirectCompare', 'compareLongPressTimer']) assert.ok(!appSource.includes(removed));
  assert.ok(!appSource.includes("kind: 'compare'"));
});

test('FB-P6-040 keeps the single model selector immediately left of send with a reduced hover-only pill and unchanged hit target', () => {
  const html = renderChatFirstShell({ data: fixture, native: true, selectedConversationId: 'ordinary', composerDraft: '', profileOpen: false, pane: 'chat', status: '', error: '', connection: {} });
  const actions = html.substring(html.indexOf('<div class="chat-composer-actions">'), html.indexOf('</div>\n      </div>\n    </section>', html.indexOf('<div class="chat-composer-actions">')));
  assert.ok(actions.includes('<div class="chat-composer-primary-actions">'));
  assert.ok(actions.indexOf('p6g-model-trigger') < actions.indexOf('chat-send'));
  for (const token of ['.chat-composer-primary-actions { display: flex; align-items: center; gap: 4px; margin-left: auto; }', 'min-width: 80px', 'width: 40px', 'min-height: 40px', 'width: 80%', 'height: 32px', 'border-radius: 999px', 'background: #f1f3f1', 'border: 1px solid transparent', 'opacity: 0', 'opacity: 1', 'scale(.96)', 'font-size: 12px', '.p6g-model-trigger:focus-visible::before']) assert.ok(css.includes(token));
  assert.ok(shell.includes('p6g-model-trigger" data-action="toggle-p6g-model-picker" data-overlay-trigger aria-label="选择模型：${escapeHtml(p6gLabel)}" title="选择模型 · ${escapeHtml(p6gLabel)}" aria-expanded="${p6gModelPickerOpen}" ${data && p6gCandidates.length ? \'\' : \'disabled\'}><span'));
});

test('FB-P6-056 keeps Desktop composer menus minimal, button-anchored and visually truthful', () => {
  const html = renderChatFirstShell({ data: fixture, native: true, selectedConversationId: 'ordinary', composerDraft: '', profileOpen: false, pane: 'chat', status: '', error: '', connection: {}, composerAddOpen: true, conversationPreferences: { revision: 2, toneOverride: 'professional', webSearchOverride: true } });
  for (const token of ['composer-add-anchor', 'composer-add-sheet', '添加图片', '添加文件', '基础风格和语气', '专业可靠', '实时网页搜索', 'aria-checked="true"']) assert.ok(html.includes(token), token);
  for (const token of ['p6g-model-anchor', 'composer-model-sheet', 'composer-model-sheet-scrim', 'select-p6g-auto', 'select-p6g-tier', 'icons.image', 'icons.file', 'icons.globe', 'icons.check', 'open-composer-tone-picker', 'toggle-conversation-web-search']) assert.ok(shell.includes(token));
  for (const forbidden of ['选择后立即私有复制', '当前会话模型', '手动选择只影响当前普通会话', '最近路由', '<strong>添加到草稿</strong>']) assert.ok(!html.includes(forbidden));
  for (const token of ['.composer-add-anchor, .p6g-model-anchor { position: relative; display: inline-flex; }', 'bottom: calc(100% + 8px)', '.composer-add-sheet { left: 0;', '.composer-transient-sheet { position: absolute;', '.composer-model-sheet-card {', '.composer-model-sheet-card[aria-selected="true"] { background: var(--accent-orange-soft) !important;']) assert.ok(css.includes(token));
});

test('composer tone picker exposes default before the five explicit Android styles', () => {
  const html = renderChatFirstShell({ data: fixture, native: true, selectedConversationId: 'ordinary', composerDraft: '保留草稿', profileOpen: false, pane: 'chat', status: '', error: '', connection: {}, composerAddOpen: true, composerAddPage: 'tone', conversationPreferences: { revision: 3, toneOverride: 'efficient', webSearchOverride: null }, productSettings: { tone: 'friendly', webSearchEnabled: false } });
  for (const [id, label] of [['default', '默认'], ['direct', '直言不讳'], ['professional', '专业可靠'], ['friendly', '亲和友善'], ['efficient', '高效务实'], ['humorous', '风趣搞笑']]) {
    assert.match(html, new RegExp(`data-action="select-conversation-tone" data-tone="${id}"`));
    assert.ok(html.includes(label), label);
  }
  assert.equal((html.match(/data-action="select-conversation-tone"/g) || []).length, 6);
  assert.ok(html.includes('data-tone="default"'));
  for (const token of ["action === 'open-composer-tone-picker'", "action === 'select-conversation-tone'", "action === 'toggle-conversation-web-search'", "invoke('set_desktop_conversation_preferences'", 'state.composerAddPage =']) assert.ok(source.includes(token), token);
});

test('FB-P6-052 makes the conversation and work switch a single segmented pill', () => {
  for (const token of ['.chat-mode-switch { position: absolute;', 'padding: 3px', 'border-radius: 999px', 'background: #f1f3f1', '.chat-mode-switch button { min-height: 34px', '.chat-mode-switch button.selected { background: var(--foreground-surface, #fff)']) assert.ok(css.includes(token));
  assert.ok(shell.includes('class="${activeWorkMode ? \'\' : \'selected\'}" data-action="show-chat"'));
  assert.ok(shell.includes('class="${activeWorkMode ? \'selected\' : \'\'}" data-action="show-work"'));
});

test('latest model settings shows the Android provider order without exposing the retired fixture control', () => {
  const html = renderChatFirstShell({ data: fixture, native: true, selectedConversationId: 'ordinary', composerDraft: '', profileOpen: false, pane: 'settings', settingsSection: 'model', status: '', error: '', connection: {}, p6gCatalog: { revision: 4, snapshot: { catalogVersion: 'local-unconfigured-v1', policyVersion: 1, candidates: [] } } });
  for (const token of ['OpenAI / Claude / Gemini', 'Qwen', 'DeepSeek', '智谱 GLM', '实时网页搜索', '模型设置', '费用与用量', '上下文记录', '运行诊断']) assert.ok(html.includes(token));
  assert.ok(!html.includes('install-p6g-local-fixture'));
  for (const removed of ["async function installP6GLocalFixture", "upsert_desktop_p6g_catalog_candidate", "providerFamily: 'LOCAL'", "knownCostMicros: 0", "action === 'install-p6g-local-fixture'"]) assert.ok(!source.includes(removed), removed);
  for (const forbidden of ['endpoint:', 'requestBody:']) assert.ok(!source.includes(forbidden));
  for (const token of ['read_desktop_model_service_settings', 'save_desktop_model_service_settings', 'reveal_desktop_model_service_credential', 'test_desktop_model_service_connection']) assert.ok(source.includes(token));
});

test('model configuration uses the current Android provider segmented layout', () => {
  const html = renderChatFirstShell({ data: fixture, native: true, pane: 'settings', settingsSection: 'model-configuration', status: '', error: '', connection: {}, p6gCatalog: { snapshot: { candidates: [{ modelId: 'safe-model' }] } } });
  for (const token of ['android-settings-segments', 'OpenRouter', 'DeepSeek', '智谱', 'Qwen', 'GPT-5.6 Terra', '测试连接', 'API Key', '显示 API Key', '保存']) assert.ok(html.includes(token));
  for (const forbidden of ['Grok 4.1', 'Grok 4.5', 'Grok 4.6', 'Authorization', 'save-api-key', '检查钥匙串']) assert.ok(!html.includes(forbidden));
  assert.match(css, /\.android-settings-segments > button \{[^}]*display: flex;[^}]*align-items: center;[^}]*justify-content: center;[^}]*text-align: center;/s);
});

test('C15 work modules stay in the shared shell with the Android-derived work navigation', () => {
  const workPanel = '<section>工作面板内容</section>';
  const html = renderChatFirstShell({ data: fixture, native: true, selectedConversationId: null, composerDraft: '', profileOpen: false, workPanel, pane: 'knowledge', status: '', error: '', connection: {} });
  for (const token of ['chat-sidebar', 'chat-main work-main', '工作面板内容', 'chat-work-nav', '>项目</button>', '>知识</button>', '>记忆</button>']) assert.ok(html.includes(token));
  assert.ok(html.includes('chat-sidebar-scroll'));
  assert.ok(!html.includes('chat-pinned'));
  assert.ok(html.includes('aria-pressed="true">工作'));
  assert.ok(shell.includes('function workNavigation'));
  assert.ok(!shell.includes('navigation.insertAdjacentHTML'));
});

test('C15 work mode rejects an unscoped ordinary conversation and opens its own empty state', () => {
  const html = renderChatFirstShell({ data: fixture, native: true, selectedConversationId: 'new', composerDraft: '', chatSearch: '', profileOpen: false, sidebarOpen: false, workMode: true, workspaces: [fixture.summary], workPanel: null, pane: 'work', status: '', error: '', connection: {} });
  for (const token of ['南枫 AI', '还没有项目工作对话', '从左侧项目中创建或打开一条对话。', 'chat-work-nav']) assert.ok(html.includes(token));
  for (const forbidden of ['chat-transcript-stage', 'data-scroll-owner="message-list"', 'id="chat-composer"', '>新会话<']) assert.ok(!html.includes(forbidden));
  assert.ok(!html.includes('工作面板内容'));
  for (const token of ["state.pane = 'work'", 'selectedWorkProjectId', 'resolveWorkConversationState']) assert.ok(`${source}\n${shell}`.includes(token));
  assert.ok(!source.includes("state.pane = 'work-home'"));
});

test('minimum-width chat fallback uses a recoverable drawer without defining a second product layout', () => {
  const html = renderChatFirstShell({ data: fixture, native: true, selectedConversationId: null, composerDraft: '', chatSearch: '', profileOpen: false, sidebarOpen: true, pane: 'chat', status: '', error: '', connection: {} });
  const wideHtml = renderChatFirstShell({ data: fixture, native: true, selectedConversationId: null, composerDraft: '', chatSearch: '', profileOpen: false, sidebarOpen: false, pane: 'chat', status: '', error: '', connection: {} });
  for (const token of ['toggle-chat-sidebar', 'chat-sidebar-close', 'chat-sidebar-scrim', 'aria-expanded="true"', 'aria-label="关闭导航"', 'data-action="toggle-chat-sidebar"']) assert.ok(html.includes(token));
  assert.ok(!html.includes('>关闭</button>'));
  assert.ok(!wideHtml.includes('chat-sidebar-close'));
  for (const token of ['@media (max-width: 900px)', '.fallback-sidebar-open .chat-sidebar', '.fallback-sidebar-open .chat-sidebar-close', 'position: absolute', 'right: 0', '.fallback-sidebar-open .chat-sidebar-scrim', 'pointer-events: auto']) assert.ok(css.includes(token));
  for (const token of ['sidebarOpen: false', "action === 'toggle-chat-sidebar'", 'state.sidebarOpen = false', 'state.sidebarOpen = !state.sidebarOpen']) assert.ok(source.includes(token));
});

test('long conversation keeps its own scroll owner and restoring a wide window closes the sidebar fallback', () => {
  const longConversation = {
    ...fixture,
    exchange: {
      conversations: [{ id: 'long', title: '长会话', archived: false, revision: 1, messages: Array.from({ length: 48 }, (_, index) => ({ id: `message-${index}`, role: index % 2 ? 'assistant' : 'user', blocks: [{ kind: 'TEXT', text: `第 ${index + 1} 条本地消息` }] })) }],
    },
  };
  const html = renderChatFirstShell({ data: longConversation, native: true, selectedConversationId: 'long', composerDraft: '', chatSearch: '', profileOpen: false, sidebarOpen: true, pane: 'chat', status: '', error: '', connection: {} });
  assert.equal((html.match(/<article class="chat-message /g) || []).length, 48);
  for (const token of ['chat-scroll', 'data-scroll-owner="message-list"', 'data-conversation-id="long"', 'tabindex="0"']) assert.ok(html.includes(token));
  for (const token of ['min-height: 0; overflow: auto', 'scrollbar-gutter: stable', 'function rememberChatScroll', 'function restoreChatScroll', 'chatScrollPositions', 'function rememberSidebarScroll', 'function restoreSidebarScroll', 'state.sidebarScrollTop', "window.addEventListener('resize'", 'wasSidebarFallbackViewport && !isSidebarFallbackViewport', 'state.sidebarOpen = false']) assert.ok(`${css}\n${source}`.includes(token));
});

test('conversation title click restores its saved waterline or opens at latest, never implicit top', () => {
  const rendered = renderChatFirstShell({ data: fixture, native: true, selectedConversationId: null, composerDraft: '', chatSearch: '', profileOpen: false, sidebarOpen: true, railCollapsed: false, showArchived: false, pane: 'chat', status: '', error: '', connection: {} });
  const title = rendered.indexOf('<span class="chat-history-title">');
  const selector = rendered.lastIndexOf('data-action="select-chat"', title);
  assert.ok(selector >= 0 && selector < title, 'drawer title must be inside the real conversation selector');
  for (const token of [
    'if (!state.chatScrollPositions.has(target.dataset.id)) state.pendingChatScrollToLatestId = target.dataset.id',
    'state.chatScrollPositions.set(conversationId, scroll.scrollTop)',
    "scroll.scrollTo({ top: scroll.scrollHeight, behavior: 'smooth' })",
    'scroll.scrollTop = state.chatScrollPositions.get(conversation.id) || 0',
  ]) assert.ok(source.includes(token), token);
});

test('chat-first work mode directly imports a selected typed exchange after strict preflight', () => {
  for (const token of ['stage_preflight_selected_exchange', 'import_staged_exchange_as_new_workspace', "workspaceTitle: '导入工作区'", '严格预检并直接导入']) assert.ok(source.includes(token));
  assert.ok(!source.includes('预检已通过；请明确确认导入。'));
});

test('ordinary send durably submits one native streaming attempt and retains the draft only on pre-submit rejection', () => {
  for (const token of ['function chatDraftKey', 'window.localStorage', 'function sendLocalMessage', "invoke('submit_desktop_ordinary_chat'", 'egressAuthorization', "disclosureVersion: 'normal-chat-egress-v1'", 'sentDraft', 'sentAttachments', '正在等待模型回复', "result.state === 'UNKNOWN'", '未自动重发']) assert.ok(source.includes(token), token);
  for (const token of ['点击发送即授权', '费用：服务商按实际用量计费', 'chat-composer-egress-disclosure']) assert.ok(`${shell}\n${css}`.includes(token), token);
  for (const forbidden of ["action: 'appendMessage'", "action: 'create'", '消息已本地记录。']) assert.ok(!source.slice(source.indexOf('async function sendLocalMessage()'), source.indexOf('saveLocalMessage = sendLocalMessage')).includes(forbidden), forbidden);
});

test('FB-P6-068 Desktop composer send restores the transcript directly to its newest message', () => {
  for (const token of ['pendingChatSendScrollToLatestId', 'state.pendingChatSendScrollToLatestId = result.conversationId', 'const restoreSubmittedLatest = state.pendingChatSendScrollToLatestId === conversation.id', 'scroll.scrollTop = scroll.scrollHeight', 'state.pendingChatSendScrollToLatestId = null']) assert.ok(source.includes(token));
  assert.ok(!source.includes("if (restoreSubmittedLatest) {\n      scroll.scrollTo({ top: scroll.scrollHeight, behavior: 'smooth' })"));
});

test('ordinary assistant messages expose persisted running stop failure unknown retry and provider cost facts', () => {
  const rendered = renderChatFirstShell({ data: { ...fixture, exchange: { ...fixture.exchange, conversations: [{ id: 'runtime', title: '流式', revision: 2, messages: [{ id: 'assistant-runtime', role: 'assistant', delivery: 'UNKNOWN', attemptId: 'attempt-runtime', source: 'PROVIDER', modelSnapshot: { displayName: 'GPT-5.6 Terra' }, chargeMicros: 8, blocks: [{ kind: 'TEXT', text: '已保留增量' }] }] }] } }, native: true, selectedConversationId: 'runtime', composerDraft: '', chatSearch: '', profileOpen: false, sidebarOpen: false, pane: 'chat', status: '', error: '', connection: {} });
  for (const token of ['chat-runtime-state unknown', '连接结果未知', 'retry-ordinary-chat', 'attempt-runtime', '5.6 Terra', '¥0.0001']) assert.ok(rendered.includes(token), token);
  for (const token of ["action === 'stop-ordinary-chat'", "invoke('cancel_desktop_ordinary_chat'", "action === 'retry-ordinary-chat'", "invoke('retry_desktop_ordinary_chat'"]) assert.ok(source.includes(token), token);
  for (const token of ['allow-desktop-ordinary-chat', 'submit_desktop_ordinary_chat', 'retry_desktop_ordinary_chat', 'cancel_desktop_ordinary_chat', 'read_latest_desktop_ordinary_chat_attempt']) assert.ok(`${temporaryPermission}\n${capability}`.includes(token), token);
});

test('only a live Attempt can show generating controls; stale PARTIAL is rendered as an honest unknown result', () => {
  const running = renderChatFirstShell({ data: { ...fixture, exchange: { ...fixture.exchange, conversations: [{ id: 'live-runtime', title: '实时生成', revision: 2, messages: [{ id: 'assistant-live', role: 'assistant', delivery: 'PARTIAL', runtimeState: 'RUNNING', attemptId: 'attempt-live', source: 'PROVIDER', blocks: [{ kind: 'TEXT', text: '已收到增量' }] }] }] } }, native: true, selectedConversationId: 'live-runtime', composerDraft: '', chatSearch: '', profileOpen: false, sidebarOpen: false, pane: 'chat', status: '', error: '', connection: {} });
  for (const token of ['正在生成回复', '正在接收模型输出', '停止生成', 'chat-runtime-spinner', 'chat-send-stop', 'chat-send-stop-mark', 'data-action="stop-ordinary-chat"']) assert.ok(running.includes(token), token);
  const stale = renderChatFirstShell({ data: { ...fixture, exchange: { ...fixture.exchange, conversations: [{ id: 'stale-runtime', title: '旧生成', revision: 2, messages: [{ id: 'assistant-stale', role: 'assistant', delivery: 'PARTIAL', source: 'PROVIDER', blocks: [{ kind: 'TEXT', text: '旧增量' }] }] }] } }, native: true, selectedConversationId: 'stale-runtime', composerDraft: '', chatSearch: '', profileOpen: false, sidebarOpen: false, pane: 'chat', status: '', error: '', connection: {} });
  for (const token of ['生成已中断', 'LOCAL_RUNTIME_STATE_MISSING']) assert.ok(stale.includes(token), token);
  assert.ok(!stale.includes('正在生成回复'));
  assert.ok(!stale.includes('chat-send-stop'));
  for (const token of ['runtimeState === \'RUNNING\'', 'chat-runtime-spin', 'ordinaryChatUnknownCopy']) assert.ok(`${shell}\n${css}`.includes(token) || source.includes(token), token);
});

test('failed ordinary replies remain as an actionable answer-incomplete card after reload', () => {
  const rendered = renderChatFirstShell({ data: { ...fixture, exchange: { ...fixture.exchange, conversations: [{ id: 'failed-runtime', title: '联网失败', revision: 2, messages: [{ id: 'assistant-failed', role: 'assistant', delivery: 'FAILED', attemptId: 'attempt-failed', source: 'PROVIDER', safeErrorCode: 'PROVIDER_NOT_ENABLED', blocks: [{ kind: 'TEXT', text: '' }] }] }] } }, native: true, selectedConversationId: 'failed-runtime', composerDraft: '', chatSearch: '', profileOpen: false, sidebarOpen: false, pane: 'chat', status: '', error: '', connection: {} });
  for (const token of ['chat-runtime-state failed', '回答未完成', '本次实际接收服务商未启用', '重试原 Attempt', 'attempt-failed']) assert.ok(rendered.includes(token), token);
  assert.ok(!rendered.includes('>PROVIDER_NOT_ENABLED<'));
});

test('temporary chat uses the isolated owner and the Ghost directly toggles back to NORMAL', () => {
  const temporary = {
    temporaryId: 'temporary-fixture-01',
    draft: '仅本地草稿',
    messages: [],
    attachments: [],
    draftAttachmentIds: [],
  };
  const html = renderChatFirstShell({ data: fixture, native: true, selectedConversationId: 'new', composerDraft: temporary.draft, temporaryConversation: temporary, chatSearch: '', profileOpen: false, sidebarOpen: false, pane: 'chat', status: '', error: '', connection: {} });
  for (const token of ['临时聊天', 'toggle-temporary-chat', 'aria-pressed="true"']) assert.ok(html.includes(token));
  assert.ok(!html.includes('context-menu-pin'));
  for (const token of ['enter_or_restore_desktop_temporary_conversation', 'read_desktop_temporary_conversation', 'restoreTemporaryChat', 'update_desktop_temporary_conversation', 'append_desktop_temporary_message', 'import_desktop_temporary_attachment', 'import_desktop_temporary_clipboard_attachment', 'TEMPORARY_SESSION', 'function leaveTemporaryChat()', "action === 'toggle-temporary-chat'", '发现可恢复的临时聊天']) assert.ok(source.includes(token));
  for (const forbidden of ['show-temporary-menu', 'temporary-exit-confirm', 'confirm-temporary-clear', 'confirm-temporary-exit', 'clear-temporary-now', '确认退出']) assert.ok(!source.includes(forbidden));
  for (const forbidden of ['ConversationRepository', 'workspace exchange']) assert.ok(!source.includes(forbidden));
  for (const token of ['allow-desktop-temporary-conversation', 'enter_or_restore_desktop_temporary_conversation', 'read_desktop_temporary_conversation', 'update_desktop_temporary_conversation', 'append_desktop_temporary_message', 'import_desktop_temporary_attachment', 'import_desktop_temporary_clipboard_attachment', 'clear_desktop_temporary_conversation', 'remove_desktop_temporary_attachment']) assert.ok(temporaryPermission.includes(token));
  assert.ok(capability.includes('allow-desktop-temporary-conversation'));
});

test('Composer accepts pasted and dropped concrete files through private attachment commands', () => {
  for (const token of ['pastedComposerFiles', 'clipboardFileBase64', 'importPastedComposerClipboardFiles', 'PREFERRED_CLIPBOARD_IMAGE_TYPES', 'navigator.clipboard?.read', 'importComposerClipboardFiles', "import_desktop_conversation_clipboard_attachment", "import_desktop_temporary_clipboard_attachment", "app.addEventListener('paste'", "app.addEventListener('dragover'", "app.addEventListener('drop'", 'event.preventDefault()', 'Web 预览不能私有复制粘贴附件']) assert.ok(source.includes(token), token);
  for (const token of ['allow-import-desktop-conversation-attachment', 'import_desktop_conversation_clipboard_attachment', 'allow-desktop-temporary-conversation', 'import_desktop_temporary_clipboard_attachment']) assert.ok(`${temporaryPermission}\n${capability}`.includes(token), token);
  assert.ok(css.includes('.chat-composer.composer-file-drop-active'));
  assert.ok(shell.includes('可直接粘贴或拖入图片、PDF、视频和文件'));
});

test('temporary model override remains a local recovery field while using the normal model picker form', () => {
  const temporary = { temporaryId: 'temporary-fixture-02', draft: '', messages: [], attachments: [], draftAttachmentIds: [], modelOverrideId: 'local.fixture-v1' };
  const html = renderChatFirstShell({ data: fixture, native: true, selectedConversationId: null, composerDraft: '', temporaryConversation: temporary, temporaryModelOpen: true, chatSearch: '', profileOpen: false, sidebarOpen: false, pane: 'chat', status: '', error: '', connection: {} });
  for (const token of ['toggle-temporary-model', 'p6g-model-trigger', 'composer-model-sheet', 'select-temporary-auto', '临时模型标识']) assert.ok(html.includes(token));
  for (const token of ['temporaryModelOpen', 'updateTemporaryModelOverride', 'modelOverrideId', 'closeTopOverlay', 'select-temporary-auto', 'select-temporary-model']) assert.ok(source.includes(token));
  for (const forbidden of ['temporary-model-popover', 'temporary-model-override', 'save-temporary-model', 'clear-temporary-model', 'p6g-model-popover']) assert.ok(!`${shell}\n${source}\n${html}`.includes(forbidden));
  assert.ok(!source.includes('OpenRouterInferenceAdapter'));
});

test('composer keeps shared semantic glyphs and unchanged Desktop interaction surfaces', () => {
  const temporary = { temporaryId: 'temporary-fixture-03', draft: '', messages: [], attachments: [], draftAttachmentIds: [], modelOverrideId: 'local.fixture-v1' };
  const html = renderChatFirstShell({ data: fixture, native: true, selectedConversationId: null, composerDraft: '', temporaryConversation: temporary, chatSearch: '', profileOpen: false, sidebarOpen: false, pane: 'chat', status: '', error: '', connection: {} });
  for (const token of ['icons.plus', 'icons.send', 'p6g-model-trigger', '选择模型：local.fixture-v1']) assert.ok(`${shell}\n${html}`.includes(token));
  for (const token of ['width: 26px', 'height: 26px', 'width: 40px', 'min-height: 40px', 'width: 30px', 'height: 30px', '.chat-composer-icon:disabled']) assert.ok(css.includes(token));
  for (const forbidden of ['data-action="toggle-composer-add" data-overlay-trigger aria-label="添加到草稿" title="添加到草稿" aria-expanded="${composerAddOpen}"><span', 'data-action="toggle-temporary-model" data-overlay-trigger aria-label="临时模型标识"><span', 'data-action="save-local-message" aria-label="发送消息" title="发送消息" ${path.enabled && (String(composerDraft || \'\').trim() || visibleAttachments.length) ? \'\' : \'disabled\'}><span']) assert.ok(!shell.includes(forbidden));
});

test('FB-P6-076 normal work and temporary modes share the transcript and composer geometry', () => {
  const temporary = {
    temporaryId: 'temporary-fixture-04',
    draft: '',
    messages: [{ id: 'temporary-message', text: '临时消息使用同一右侧气泡', attachmentIds: ['temporary-file'] }],
    attachments: [{ id: 'temporary-file', displayName: 'temporary.md', mimeType: 'text/markdown' }],
    draftAttachmentIds: [],
  };
  const workFixture = {
    ...fixture,
    exchange: {
      ...fixture.exchange,
      projects: [{ id: 'project-work', title: '工作项目', archived: false, revision: 1 }],
      conversations: fixture.exchange.conversations.map(item => item.id === 'new' ? { ...item, projectId: 'project-work' } : item),
    },
  };
  const normal = renderChatFirstShell({ data: fixture, native: true, selectedConversationId: 'new', composerDraft: '', pane: 'chat', status: '', error: '', connection: {} });
  const work = renderChatFirstShell({ data: workFixture, native: true, selectedConversationId: 'new', selectedWorkProjectId: 'project-work', composerDraft: '', workMode: true, pane: 'work', status: '', error: '', connection: {} });
  const temporaryHtml = renderChatFirstShell({ data: fixture, native: true, selectedConversationId: null, composerDraft: '', temporaryConversation: temporary, pane: 'chat', status: '', error: '', connection: {} });
  for (const html of [normal, work, temporaryHtml]) {
    for (const token of ['chat-main-header', 'chat-transcript-stage', 'chat-scroll', 'chat-thread', 'chat-composer-wrap', 'chat-composer']) assert.ok(html.includes(token));
  }
  for (const token of ['chat-message user', 'chat-message-bubble', 'chat-message-attachments', 'data-temporary-transcript="true"']) assert.ok(temporaryHtml.includes(token));
  for (const forbidden of ['chat-empty-stage', 'chat-work-panel', 'chat-attachment-chip']) assert.ok(!temporaryHtml.includes(forbidden));
  for (const token of ['function temporaryTranscript', 'function messageList', 'interactive: !temporaryConversation', "const workPanel = state.pane === 'projects'"]) assert.ok(`${shell}\n${source}`.includes(token));
});

test('P6-F2-A renders a local-only history overlay, shared result panel and stable local date', () => {
  const hit = { entryId: 'search-ordinary-message-1', workspaceId: 'workspace-local', conversationId: 'ordinary', messageId: 'message-1', branchLeafId: 'message-1', title: '普通会话', snippet: '本地 fixture', contentKind: 'TEXT', timestamp: '2026-08-14T01:02:00Z', byteCount: 12, conversationRevision: 1 };
  const html = renderChatFirstShell({ data: fixture, native: true, selectedConversationId: 'ordinary', composerDraft: '', chatSearch: 'fixture', searchHistory: ['fixture'], searchHistoryOpen: true, searchHistoryHighlighted: 'fixture', searchPage: { hits: [hit], textCount: 1, attachmentCount: 0, truncated: false }, searchPanel: true, profileOpen: false, sidebarOpen: false, pane: 'chat', status: '', error: '', connection: {} });
  for (const token of ['最近搜索', 'clear-search-history', '全屏搜索', 'open-search-result', 'read_desktop_local_search_history', 'clear_desktop_local_search_history', 'query_desktop_local_index', 'searchHistoryOpen']) assert.ok(`${html}\n${source}`.includes(token));
  assert.match(source, /document\.querySelector\('#full-search-input, #chat-search'\)/);
  assert.match(source, /state\.searchHistory = \[\]; state\.searchHistoryHighlighted = null; focusSearchAfterHistoryClear\(\);/);
  assert.equal(conversationLocalDate({ updatedAt: '2026-08-14T01:02:00Z' }, new Date('2026-08-14T02:00:00Z')).length > 0, true);
  for (const forbidden of ['content://', 'storageKey']) assert.ok(!html.includes(forbidden));
});

test('P6-F2-B renders a lazy local image thumbnail and keeps original reads behind an explicit preview action', async () => {
  const imageFixture = {
    summary: { id: 'workspace-image' },
    exchange: { conversations: [{ id: 'conversation-image', title: '图片会话', archived: false, messages: [{ id: 'message-image', role: 'user', createdAt: '2026-08-14T00:00:00Z', blocks: [{ kind: 'ASSET_REF', asset: { id: 'attachment-image', mimeType: 'image/png', displayName: 'local.png', byteCount: 68, sha256: 'a'.repeat(64) } }] }] }] },
  };
  const html = renderChatFirstShell({ data: imageFixture, native: true, selectedConversationId: 'conversation-image', composerDraft: '', chatSearch: '', profileOpen: false, sidebarOpen: false, pane: 'chat', status: '', error: '', connection: {}, imageThumbnails: { 'attachment-image': { dataUrl: 'data:image/png;base64,AA==' } } });
  for (const token of ['data-action="open-image-preview"', 'data-image-thumbnail="attachment-image"', 'data-attachment-id="attachment-image"', 'data:image/png;base64,AA==']) assert.ok(html.includes(token));
  for (const token of ['read_desktop_image_preview', 'fullSize: false', 'fullSize: true', 'allow-read-desktop-image-preview', 'state.imagePreview', 'closeTopOverlay', 'image-zoom-in', '缩放和滚动只改变当前视口']) assert.ok(`${source}\n${temporaryPermission}\n${capability}`.includes(token));
  for (const forbidden of ['privatePath', 'content://', 'file://', 'selectedPath']) assert.ok(!html.includes(forbidden));
  for (const token of ['chat-attachment-info-overlay', 'opacity:0', 'chat-image-attachment:hover .chat-attachment-info-overlay']) assert.ok(`${html}\n${await readFile(resolve(root, 'src/image-preview.css'), 'utf8')}`.includes(token));
});

test('FB-P6-030 separates attachment disclosure from message tools and uses a mature preview glyph', async () => {
  const localFile = { id: 'file-one', displayName: 'private-notes.txt', mimeType: 'application/octet-stream', byteCount: 9 };
  const rendered = renderChatFirstShell({ data: { summary: { id: 'workspace-one' }, exchange: { conversations: [{ id: 'one', title: '附件', messages: [{ id: 'message-file', role: 'user', blocks: [{ kind: 'ASSET_REF', asset: localFile }] }] }] } }, native: true, selectedConversationId: 'one', composerDraft: '', chatSearch: '', profileOpen: false, sidebarOpen: false, pane: 'chat', status: '', error: '', connection: {} });
  assert.match(rendered, /chat-document-attachment[\s\S]*chat-attachment-preview-glyph[\s\S]*chat-attachment-info-overlay/);
  assert.ok(!rendered.includes('private-notes.txt · application/octet-stream'));
  assert.ok(!shell.includes('<strong>PDF</strong>'));
  assert.ok(!shell.includes('<strong>▶</strong>'));
  for (const token of ['chat-attachment-preview-glyph', 'chat-attachment-info-overlay{position:absolute', 'opacity:0', 'chat-sidebar-divider::after']) assert.ok(`${shell}\n${css}\n${await readFile(resolve(root, 'src/image-preview.css'), 'utf8')}`.includes(token));
});

test('FB-P6-031 keeps document previews square, media unsquared, and attachment overlay compact', async () => {
  const fixture = type => ({ id: `${type}-one`, displayName: `private.${type}`, mimeType: type === 'pdf' ? 'application/pdf' : type === 'video' ? 'video/mp4' : type === 'audio' ? 'audio/mpeg' : 'application/vnd.openxmlformats-officedocument.wordprocessingml.document' });
  const render = asset => renderChatFirstShell({ data: { summary: { id: 'workspace-one' }, exchange: { conversations: [{ id: 'one', title: '附件', messages: [{ id: 'message-file', role: 'user', blocks: [{ kind: 'ASSET_REF', asset }] }] }] } }, native: true, selectedConversationId: 'one', composerDraft: '', chatSearch: '', profileOpen: false, sidebarOpen: false, pane: 'chat', status: '', error: '', connection: {} });
  assert.match(render(fixture('pdf')), /chat-document-attachment[\s\S]*open-pdf-preview/);
  assert.match(render(fixture('docx')), /chat-document-attachment[\s\S]*chat-attachment-info-overlay/);
  assert.match(render(fixture('video')), /chat-video-attachment[\s\S]*open-video-preview/);
  assert.match(render(fixture('audio')), /chat-audio-attachment[\s\S]*open-audio-preview/);
  const imageCss = await readFile(resolve(root, 'src/image-preview.css'), 'utf8');
  for (const token of ['.chat-document-attachment .chat-attachment-preview-glyph', 'width:92px;height:92px', '.chat-image-attachment img', 'height:auto;max-height:180px', '.chat-audio-attachment .chat-attachment-preview-glyph', 'width:176px;height:42px', 'background:rgb(24 35 28 / .43)', 'font-size:8px']) assert.ok(imageCss.includes(token));
});

test('FB-P6-032 places message actions before metadata in DOM and keyboard order', () => {
  const actionStart = shell.indexOf('<div class="chat-message-actions">');
  const metadataStart = shell.indexOf('<p class="chat-message-metadata">');
  assert.ok(actionStart >= 0 && metadataStart > actionStart);
  const actions = shell.slice(actionStart, metadataStart);
  assert.ok(actions.indexOf('copy-message') < actions.indexOf('share-message'));
  assert.ok(actions.indexOf('share-message') < actions.indexOf('open-assistant-message-menu'));
  const toolsCss = css.slice(css.indexOf('.chat-message-tools'), css.indexOf('.chat-scroll-to-latest'));
  assert.ok(!toolsCss.includes('order:'));
});

test('Desktop shares assistant replies through the same Markdown save path as Android', () => {
  assert.match(source, /String\(message\?\.role \|\| ''\)\.trim\(\)\.toLocaleLowerCase\(\) === 'assistant'/);
  assert.match(source, /assistantMessageMarkdown\(conversation, message\.id\)/);
  assert.match(source, /safeMarkdownName\(`\$\{conversation\.title \|\| '南枫 AI'\}-\$\{sequence\}`\)/);
  assert.match(source, /if \(!native && typeof navigator\.share === 'function'\) await navigator\.share\(\{ text: payload \}\);/);
});

test('assistant more menu owns answer information and branching without a duplicate Markdown export', () => {
  for (const token of ['open-assistant-message-menu', "kind: 'assistant-message-actions'", 'show-assistant-answer-information', '本次回答信息', '创建分支', '未记录（旧回答）']) assert.ok(source.includes(token), token);
  assert.ok(!source.includes("action === 'export-assistant-markdown'"));
});

test('P6-F2-C opens only workspace-owned PDFs in a sandboxed app reader with durable page navigation', () => {
  const localPdf = { id: 'pdf-one', displayName: 'local.pdf', mimeType: 'application/pdf' };
  const rendered = renderChatFirstShell({ data: { summary: { id: 'workspace-one' }, exchange: { conversations: [{ id: 'one', title: 'PDF', messages: [{ id: 'message-pdf', role: 'user', blocks: [{ kind: 'ASSET_REF', asset: localPdf }] }] }] } }, native: true, selectedConversationId: 'one', composerDraft: '', chatSearch: '', profileOpen: false, sidebarOpen: false, pane: 'chat', status: '', error: '', connection: {} });
  for (const token of ['open-pdf-preview', '阅读 PDF', 'PDF']) assert.ok(rendered.includes(token));
  for (const token of ['read_desktop_pdf_preview', 'pdfPreviewDialog', 'sandbox=""', 'referrerpolicy="no-referrer"', 'pdf-page-previous', 'pdf-page-next', '本机私有副本读取', '脚本、表单动作、外部资源和自动链接均不执行']) assert.ok(source.includes(token));
  assert.ok(source.includes("event.target.closest?.('.dialog, .chat-context-menu"));
  assert.ok(!source.includes('tauri-plugin-http'));
});

test('P6-F2-D keeps MP4 preview local, explicit-play and attachment-ID scoped', async () => {
  for (const token of ['read_desktop_video_preview', 'openVideoPreview', 'closeVideoPreview', 'data-video-preview', 'start-video-preview', '开始本地播放', 'video.play()', '不会自动播放、上传或外发', 'positionMillis', 'lastPlaybackPositionMillis']) assert.ok(source.includes(token), token);
  assert.match(source, /timeupdate[\s\S]+lastPlaybackPositionMillis/);
  assert.match(source, /if \(state\.videoPreview\) \{ void closeVideoPreview\(\); return; \}/);
  assert.match(source, /event\.key === 'Escape' && state\.videoPreview[\s\S]+closeVideoPreview\(\)/);
  assert.match(source, /await invoke\('read_desktop_video_preview'[\s\S]+positionMillis/);
  assert.match(tauriConfig, /media-src 'self' data: blob:/);
  for (const token of ['video/mp4', 'open-video-preview', '播放视频']) assert.ok(`${shell}\n${previewOwner}`.includes(token), token);
  const rust = await readFile(new URL('../src-tauri/src/lib.rs', import.meta.url), 'utf8');
  for (const token of ['DesktopVideoPreviewArgs', 'desktop_video_preview_positions', 'mp4_duration_millis', 'video_preview']) assert.ok(rust.includes(token), token);
});

test('P6-F2-E keeps audio explicit and text bounded/inert behind attachment-ID owners', async () => {
  for (const token of ['read_desktop_audio_preview', 'openAudioPreview', 'closeAudioPreview', 'data-audio-preview', 'start-audio-preview', 'audio.play()', 'lastPlaybackPositionMillis', 'read_desktop_text_preview', 'openTextPreview', 'local-text-preview', '不会渲染 HTML、执行链接、脚本或 Markdown 指令', 'copy-text-preview', 'save-text-preview', 'share-text-preview', "invoke('export_desktop_attachment_to_selected_path'"]) assert.ok(source.includes(token), token);
  for (const token of ['audio/mpeg', 'audio/wav', 'audio/mp4', 'open-audio-preview', 'open-text-preview', '预览文本']) assert.ok(`${shell}\n${previewOwner}`.includes(token), token);
  for (const token of ['allow-read-desktop-audio-preview', 'allow-read-desktop-text-preview', 'allow-export-desktop-attachment-to-selected-path']) assert.ok(`${temporaryPermission}\n${capability}`.includes(token), token);
  const rust = await readFile(new URL('../src-tauri/src/lib.rs', import.meta.url), 'utf8');
  for (const token of ['DesktopAudioPreviewArgs', 'desktop_audio_preview_positions', 'DesktopTextPreviewArgs', 'MAX_INERT_TEXT_PREVIEW_BYTES', 'from_utf8', 'audio_preview', 'text_preview']) assert.ok(rust.includes(token), token);
});

test('search previews restore the exact list offset instead of recentering the prior card', () => {
  for (const token of ['entryOffset', 'target.offsetTop - state.searchScrollSnapshot.entryOffset', 'owner.scrollTop = state.searchScrollSnapshot.top']) assert.ok(source.includes(token), token);
  assert.ok(!source.includes("target.scrollIntoView({ block: 'center' })"));
});

 test('utility pages retain the originating sidebar instead of switching to projects', () => {
  for (const pane of ['reminders', 'transcription']) {
    for (const workMode of [false, true]) {
      const html = renderChatFirstShell({ data: fixture, native: true, pane, workMode, workPanel: '<div>utility-content</div>' });
      assert.ok(html.includes('utility-content'));
      assert.equal(html.includes('aria-label="本地会话列表"'), !workMode);
      assert.ok(html.includes(`aria-label="${workMode ? '工作导航' : '对话导航'}"`));
    }
  }
});

test('header and sidebar menus both expose all eight mobile conversation actions for the target row', () => {
  for (const menuSource of ['header', 'sidebar']) {
    const rendered = renderChatFirstShell({ data: fixture, native: true, selectedConversationId: 'new', pane: 'chat', connection: {}, contextMenu: { id: 'old', revision: 3, title: '目标对话', source: menuSource } });
    const menu = rendered.slice(rendered.indexOf('<div class="chat-context-menu"'));
    for (const action of ['pin', 'unread', 'favorite', 'share', 'sync', 'find', 'archive', 'delete']) assert.ok(menu.includes(`data-action="context-menu-${action}"`), `${menuSource}: ${action}`);
    assert.ok(menu.includes('data-id="old"'));
    assert.ok(menu.includes('目标对话'));
  }
});
