import test from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { resolve } from 'node:path';
import { activeCloudConversations, renderChatFirstShell } from '../src/chat-shell.mjs';

const fixture = {
  summary: { id: 'workspace-safe' },
  exchange: { conversations: [{ id: 'conversation-safe', title: '安全对话', revision: 4, messages: [] }] },
};

test('cloud rows use the Android-compatible update order before presentation pin grouping', () => {
  const rows = activeCloudConversations([
    { id: 'z-tie', updatedAt: '2026-09-14T08:00:00Z', pinned: false },
    { id: 'newer', updatedAt: '2026-09-14T09:00:00Z', pinned: true },
    { id: 'a-tie', updatedAt: '2026-09-14T08:00:00Z', pinned: false },
  ]);
  assert.deepEqual(rows.map(row => row.id), ['newer', 'a-tie', 'z-tie']);
  assert.equal(rows[0].pinned, true);
});

test('a confirmed empty cloud presentation cannot retain a cached Desktop pin', async () => {
  const app = await readFile(resolve(import.meta.dirname, '../src/app.mjs'), 'utf8');
  assert.match(app, /receipt\?\.cloudListPresentationPresent\s*\?\s*new Set\(Array\.isArray\(receipt\?\.cloudPinnedConversationIds\) \? receipt\.cloudPinnedConversationIds : \[\]\)\s*:\s*null/);
  assert.match(app, /pinned: pinnedConversationIds\.has\(item\.id\),\s*cloudPinned: pinnedConversationIds\.has\(item\.id\)/);
});

test.skip('retired: recovery replacement accepts one private input instead of generating a code', () => {
  const html = renderChatFirstShell({ data: fixture, native: true, pane: 'settings', settingsSection: 'account', accountSync: { email: 'safe@example.invalid', state: 'READY', recoveryState: 'CONFIRMED' }, accountRecovery: { rotation: true } });
  assert.match(html, /id="new-recovery-code" type="password"/);
  assert.doesNotMatch(html, /confirm-new-recovery-code/);
  assert.doesNotMatch(html, /再次输入新恢复码/);
  assert.match(html, />确认更换<\/button>/);
  assert.doesNotMatch(html, /新恢复码只显示这一次/);
});

test.skip('retired: an unsubmitted recovery replacement is cleared when leaving account settings', async () => {
  const app = await readFile(resolve(import.meta.dirname, '../src/app.mjs'), 'utf8');
  assert.match(app, /nextSettingsSection !== 'account' && state\.accountRecovery\?\.rotation === true\) state\.accountRecovery = null/);
  assert.match(app, /action === 'settings-back'[\s\S]{0,180}state\.accountRecovery\?\.rotation === true\) state\.accountRecovery = null/);
  assert.match(app, /action === 'return-to-settings-conversation-list'[\s\S]{0,220}state\.accountRecovery\?\.rotation === true\) state\.accountRecovery = null/);
});

test.skip('retired: recovery input and account actions use the same gray pill surface as Android', async () => {
  const css = await readFile(resolve(import.meta.dirname, '../src/chat-shell.css'), 'utf8');
  assert.match(css, /\.android-account-recovery > \.android-settings-field > input\s*\{[\s\S]*border-radius:\s*999px[\s\S]*background:\s*#f1f3f1/);
  assert.match(css, /\.android-account-recovery button,[\s\S]*background:\s*#f1f3f1/);
});

test.skip('retired: account settings follow the phone identity, management and selected-only sync hierarchy', () => {
  const rendered = renderChatFirstShell({
    data: fixture,
    native: true,
    pane: 'settings',
    settingsSection: 'account',
    status: '',
    error: '',
    connection: {},
    settingsCapabilities: { googleAccountSync: true },
    accountSync: {
      configured: true,
      state: 'DIRECTION_REQUIRED',
      email: 'safe@example.invalid',
      displayName: '安全账号',
      recoveryState: 'CONFIRMED',
      periodicEnabled: false,
      rotationPending: true,
      selectedConversationCount: 1,
      diagnostics: [{ event: 'SYNC_RECONCILE', outcome: 'SUCCESS', safeCode: null, occurredAtMs: 1 }],
      notifications: [{ kind: 'SUCCESS', message: '已通过回读。', occurredAtMs: 1, unread: true }],
      remoteDocuments: [{ documentId: 'conversation-cloud-safe', revision: 2, payloadHashPrefix: 'abcdef123456' }],
    },
  });
  for (const token of ['Google 账号与同步', '安全账号，您好！', 'safe@example.invalid', '账号管理', '切换 Google 账号', '退出登录', '选择首次同步方向', 'data-action="choose-selected-local-sync-start"', '对话同步', '仅包含你手动同步过的对话。', '继续更换恢复码', 'data-action="retry-account-recovery-rotation"', '从南枫云恢复', 'data-action="restore-account-cloud-conversation"', '<span>原恢复码</span><input']) assert.ok(rendered.includes(token));
  assert.ok(!rendered.includes('data-action="create-account-recovery-rotation"'));
  assert.match(rendered, /android-account-status-card[\s\S]*android-account-status-content[\s\S]*android-account-status-actions/);
  for (const token of ['同步通知', '运行诊断', 'SYNC_RECONCILE', '已选 1 个']) assert.ok(!rendered.includes(token));
  for (const forbidden of ['access_token', 'refresh_token', 'privatePath', '恢复码明文']) assert.ok(!rendered.includes(forbidden));
});

test('account status cards use a compact desktop action column and stack only on narrow windows', async () => {
  const css = await readFile(resolve(import.meta.dirname, '../src/chat-shell.css'), 'utf8');
  assert.match(css, /\.android-account-sync-page \.android-account-status-card\s*\{[\s\S]*grid-template-columns:\s*minmax\(0, 1fr\) auto/);
  assert.match(css, /\.android-account-status-actions\s*\{[\s\S]*justify-content:\s*flex-end/);
  assert.match(css, /@media \(max-width: 680px\)[\s\S]*\.android-account-sync-page \.android-account-status-card\s*\{[\s\S]*grid-template-columns:\s*minmax\(0, 1fr\)/);
});

test('signed-out account page keeps sync controls unavailable until identity and recovery are ready', () => {
  const rendered = renderChatFirstShell({
    data: fixture, native: true, pane: 'settings', settingsSection: 'account', status: '', error: '', connection: {},
    settingsCapabilities: { googleAccountSync: true },
    accountSync: { configured: true, state: 'SIGNED_OUT', recoveryState: 'UNAVAILABLE' },
  });
  for (const token of ['android-account-hero', '未登录', '使用 Google 登录']) assert.ok(rendered.includes(token));
  for (const token of ['android-account-periodic', 'android-account-notifications', 'android-account-diagnostics']) assert.ok(!rendered.includes(token));
});

test('unconfigured account page exposes the configuration reason instead of a disabled pseudo-login', () => {
  const rendered = renderChatFirstShell({
    data: fixture, native: true, pane: 'settings', settingsSection: 'account', status: '', error: '', connection: {},
    settingsCapabilities: { googleAccountSync: true },
    accountSync: { configured: false, state: 'NOT_CONFIGURED', recoveryState: 'UNAVAILABLE' },
  });
  assert.ok(rendered.includes('此 Desktop 尚未写入南枫云地址或公开访问密钥；不会打开浏览器、读取账号或上传数据。'));
  assert.ok(rendered.includes('data-action="show-google-login-requirements"'));
  assert.ok(rendered.includes('查看 Google 登录条件'));
  assert.ok(!rendered.includes('>使用 Google 登录</button>'));
});

test.skip('retired: new recovery code is rendered once with a copy action and centered confirmation action', async () => {
  const rendered = renderChatFirstShell({
    data: fixture,
    native: true,
    pane: 'settings',
    settingsSection: 'account',
    status: '',
    error: '',
    connection: {},
    settingsCapabilities: { googleAccountSync: true },
    accountSync: { configured: true, state: 'AUTHENTICATED_NEEDS_RECOVERY_CONFIRMATION', email: 'safe@example.invalid', recoveryState: 'NEEDS_CONFIRMATION', diagnostics: [], notifications: [] },
    accountRecovery: { recoveryCode: 'NF-ONLY-ONCE-SAFE', confirmationHash: 'hash-safe' },
  });
  for (const token of ['恢复码只显示这一次', 'NF-ONLY-ONCE-SAFE', 'data-action="copy-account-recovery-code"', 'data-copy-action', 'data-action="confirm-account-recovery"', 'data-confirmation-hash="hash-safe"']) assert.ok(rendered.includes(token));
  assert.doesNotMatch(rendered, /android-account-recovery-copy[\s\S]*?<span>复制<\/span>/);
  assert.equal(rendered.match(/NF-ONLY-ONCE-SAFE/g)?.length, 1);
  const [css, app] = await Promise.all([
    readFile(resolve(import.meta.dirname, '../src/chat-shell.css'), 'utf8'),
    readFile(resolve(import.meta.dirname, '../src/app.mjs'), 'utf8'),
  ]);
  assert.match(css, /\.android-account-recovery > button\.primary\s*\{[^}]*justify-content:\s*center/);
  assert.ok(app.includes("navigator.clipboard.writeText(recoveryCode)"));
  assert.ok(app.includes("showCopyIconFeedback(target)"));
});

test.skip('retired: recovery confirmation is single-flight and can resume only from saved private material or an explicit pasted code', async () => {
  const [rendered, owner, command, app] = await Promise.all([
    Promise.resolve(renderChatFirstShell({
      data: fixture, native: true, pane: 'settings', settingsSection: 'account', status: '', error: '', connection: {},
      settingsCapabilities: { googleAccountSync: true },
      accountSync: { configured: true, state: 'AUTHENTICATED_NEEDS_RECOVERY_CONFIRMATION', email: 'safe@example.invalid', recoveryState: 'NEEDS_CONFIRMATION', recoveryConfirmationPending: true, diagnostics: [], notifications: [] },
    })),
    readFile(resolve(import.meta.dirname, '../src-tauri/src/desktop_account_sync_v1.rs'), 'utf8'),
    readFile(resolve(import.meta.dirname, '../src-tauri/src/lib.rs'), 'utf8'),
    readFile(resolve(import.meta.dirname, '../src/app.mjs'), 'utf8'),
  ]);
  assert.ok(rendered.includes('data-action="confirm-pending-account-recovery"'));
  assert.ok(rendered.includes('data-action="open-existing-account-recovery-confirmation"'));
  assert.ok(owner.includes('PENDING_RECOVERY_SERVICE'));
  assert.ok(owner.includes('load_pending_recovery'));
  assert.ok(owner.includes('pending_recovery_from_visible_code'));
  assert.ok(command.includes('recovery_confirmations_in_flight'));
  assert.ok(command.includes('恢复码正在确认，请稍候'));
  assert.ok(app.includes("action === 'confirm-existing-account-recovery'"));
  assert.ok(app.includes("confirmationHash: target.dataset.confirmationHash, recoveryCode"));
});

test.skip('retired: first desktop sync uses the existing phone recovery code against a cloud document instead of creating a second code', () => {
  const rendered = renderChatFirstShell({
    data: fixture, native: true, pane: 'settings', settingsSection: 'account', status: '', error: '', connection: {},
    settingsCapabilities: { googleAccountSync: true },
    accountSync: { configured: true, state: 'AUTHENTICATED_NEEDS_RECOVERY_CONFIRMATION', email: 'safe@example.invalid', recoveryState: 'NEEDS_CONFIRMATION', diagnostics: [], notifications: [], remoteDocuments: [{ documentId: 'conversation-phone-safe', revision: 2, payloadHashPrefix: 'abcdef123456' }] },
  });
  for (const token of ['使用手机端已有恢复码', '输入手机端正在使用的恢复码', '验证并连接此设备', 'data-action="restore-account-cloud-conversation"']) assert.ok(rendered.includes(token));
  assert.ok(!rendered.includes('data-action="create-account-recovery"'));
});

test.skip('retired: a stale confirmed marker without this install private material returns to the existing-code recovery path', () => {
  const rendered = renderChatFirstShell({
    data: fixture, native: true, pane: 'settings', settingsSection: 'account', status: '', error: '', connection: {},
    settingsCapabilities: { googleAccountSync: true },
    accountSync: { configured: true, state: 'AUTHENTICATED_NEEDS_RECOVERY_MATERIAL', email: 'safe@example.invalid', recoveryState: 'NEEDS_EXISTING_CODE', diagnostics: [], notifications: [], remoteDocuments: [{ documentId: 'conversation-phone-safe', revision: 2, payloadHashPrefix: 'abcdef123456' }] },
  });
  for (const token of ['使用手机端已有恢复码', '输入手机端正在使用的恢复码', '读取云端列表']) assert.ok(rendered.includes(token));
  assert.ok(!rendered.includes('android-account-periodic'));
  assert.ok(!rendered.includes('data-action="create-account-recovery"'));
});

test.skip('retired: cloud restore does not claim an empty remote result before the user has requested a read', async () => {
  const rendered = renderChatFirstShell({
    data: fixture, native: true, pane: 'settings', settingsSection: 'account', status: '', error: '', connection: {},
    settingsCapabilities: { googleAccountSync: true },
    accountSync: { configured: true, state: 'AUTHENTICATED_NEEDS_RECOVERY_MATERIAL', email: 'safe@example.invalid', recoveryState: 'NEEDS_EXISTING_CODE', diagnostics: [], notifications: [] },
  });
  assert.ok(rendered.includes('data-action="load-account-cloud-documents"'));
  assert.ok(!rendered.includes('未找到可用云端对话'));
  const app = await readFile(resolve(import.meta.dirname, '../src/app.mjs'), 'utf8');
  assert.match(app, /const remoteDocuments = Array\.isArray\(state\.accountSync\?\.remoteDocuments\)\s*\? state\.accountSync\.remoteDocuments\s*:\s*null/);
});

test.skip('retired: an authenticated empty cloud can safely restart recovery setup after private-material migration', () => {
  const rendered = renderChatFirstShell({
    data: fixture, native: true, pane: 'settings', settingsSection: 'account', status: '', error: '', connection: {},
    settingsCapabilities: { googleAccountSync: true },
    accountSync: { configured: true, state: 'AUTHENTICATED_NEEDS_RECOVERY_MATERIAL', email: 'safe@example.invalid', recoveryState: 'NEEDS_EXISTING_CODE', diagnostics: [], notifications: [], remoteDocuments: [] },
  });
  for (const token of ['云端为空', '不会改动任何本机对话', 'data-action="bootstrap-empty-account-recovery"']) assert.ok(rendered.includes(token));
  assert.ok(!rendered.includes('data-action="create-account-recovery"'));
});

test.skip('retired: empty-cloud recovery bootstrap has an explicit narrow native permission', async () => {
  const permission = await readFile(resolve(import.meta.dirname, '../src-tauri/permissions/default.toml'), 'utf8');
  const accountPermission = permission.split('identifier = "allow-desktop-account-sync"')[1]?.split('[[permission]]')[0] || '';
  assert.match(accountPermission, /bootstrap_desktop_empty_cloud_recovery/);
});

test.skip('retired: a normal first-time signed-in account can create a recovery code without a fake cloud restore', () => {
  const rendered = renderChatFirstShell({
    data: fixture, native: true, pane: 'settings', settingsSection: 'account', status: '', error: '', connection: {},
    settingsCapabilities: { googleAccountSync: true },
    accountSync: { configured: true, state: 'AUTHENTICATED_NEEDS_RECOVERY_CONFIRMATION', email: 'safe@example.invalid', recoveryState: 'NEEDS_CONFIRMATION', diagnostics: [], notifications: [] },
  });
  assert.ok(rendered.includes('data-action="create-account-recovery"'));
  assert.ok(!rendered.includes('data-action="bootstrap-empty-account-recovery"'));
});

test('account settings match the phone recovery-and-cloud card', () => {
  const rendered = renderChatFirstShell({
    data: fixture, native: true, pane: 'settings', settingsSection: 'account', status: '', error: '', connection: {},
    settingsCapabilities: { googleAccountSync: true },
    accountSync: { configured: true, state: 'READY', email: 'safe@example.invalid', remoteDocuments: [{ documentId: 'conversation-cloud-safe', revision: 2, payloadHashPrefix: 'abcdef123456' }] },
  });
  for (const token of ['恢复与安全', '更换恢复码 / 已丢失', 'data-action="create-account-recovery-rotation"', 'data-action="load-account-cloud-documents"', '读取云端列表']) assert.ok(rendered.includes(token));
  for (const forbidden of ['从南枫云恢复', '恢复为新工作区', 'conversation-cloud-safe']) assert.ok(!rendered.includes(forbidden));
});

test('sidebar keeps the local and cloud tabs with an explicit cloud read action', async () => {
  const [chat, icons] = await Promise.all([
    readFile(resolve(import.meta.dirname, '../src/chat-shell.mjs'), 'utf8'),
    readFile(resolve(import.meta.dirname, '../src/icon-source.mjs'), 'utf8'),
  ]);
  assert.match(chat, /data-action="show-recent-conversation-list"/);
  assert.match(chat, /data-action="show-cloud-conversation-list"/);
  assert.match(chat, /aria-label="本地会话"/);
  assert.match(chat, /aria-label="云端会话"/);
  assert.match(chat, /chat-sidebar-list-tabs/);
  assert.match(chat, /class="chat-sidebar-list-utility \$\{batchEditing \? 'selected' : ''\}" data-action="toggle-conversation-batch-edit"/);
  assert.match(chat, /toggle-conversation-batch-edit[^`]*icons\.listChecks/);
  assert.match(icons, /listChecks: `\$\{p\('m3 6 2 2 4-4'\)\}\$\{p\('m3 14 2 2 4-4'\)\}\$\{p\('M13 6h8'\)\}\$\{p\('M13 14h8'\)\}`/);
  assert.doesNotMatch(chat, /toggle-conversation-batch-edit[^`]*icons\.edit/);
  assert.doesNotMatch(chat, /toggle-conversation-batch-edit[^`]*icons\.selectAll/);
  assert.match(chat, /class="chat-sidebar-list-utility chat-sidebar-cloud-read" data-action="load-account-cloud-documents"/);
  assert.match(chat, /icons\.cloudDownloadSolid/);
  assert.match(chat, /data-action="load-account-cloud-documents"/);
  assert.doesNotMatch(chat, /data-action="open-cloud-restore"/);
  const css = await readFile(resolve(import.meta.dirname, '../src/chat-shell.css'), 'utf8');
  for (const token of [
    '.chat-sidebar-list-selector { position: relative; display: flex; width: 100%; min-height: 30px;',
    '.chat-sidebar-list-utility { display: grid; box-sizing: border-box; flex: 0 0 26px;',
    '.chat-first .chat-sidebar-list-utility { flex: 0 0 26px !important; width: 26px !important; min-width: 26px !important; max-width: 26px !important; height: 26px !important; min-height: 26px !important; max-height: 26px !important; padding: 0 !important; border-radius: 50% !important; line-height: 1 !important; }',
    '.chat-first .chat-sidebar-list-utility:hover,',
    '.chat-first .chat-sidebar-list-utility.selected:hover,',
    '.chat-sidebar-list-utility > svg { width: 13px; height: 13px; }',
    'border: 0; border-radius: 999px; background: var(--foreground-surface, #fff); box-shadow: 0 1px 2px rgb(31 46 36 / 5%), 0 8px 20px rgb(31 46 36 / 8%);',
  ]) assert.ok(css.includes(token), token);
});

test('opening a cloud row is a single cancellable route and never waits for model preferences before paint', async () => {
  const app = await readFile(resolve(import.meta.dirname, '../src/app.mjs'), 'utf8');
  assert.match(app, /createCloudConversationOpenRoute/);
  assert.match(app, /cloudConversationOpening/);
  assert.match(app, /reuse: \(\{ workspaceId, conversationId \}\) => \{[\s\S]*alreadySelected: !state\.cloudConversationOpening && state\.pane === 'chat' && state\.selectedConversationId === conversationId,/);
  assert.match(app, /openCloudConversationRoute\(\{ workspaceId, conversationId, title:/);
  assert.doesNotMatch(app, /delete target\.dataset\.workspaceId;\s*target\.click\(\)/);
  const rendered = renderChatFirstShell({
    data: fixture, native: true, pane: 'chat', selectedConversationId: null, status: '', error: '', connection: {},
    cloudConversationOpening: { title: '正在读取的云端会话' },
  });
  assert.match(rendered, /正在打开云端对话/);
  assert.match(rendered, /正在读取的云端会话/);
});

test('cloud empty state has no duplicate read button and the read list survives restart locally', async () => {
  const rendered = renderChatFirstShell({
    data: fixture, native: true, pane: 'chat', sidebarConversationList: 'cloud', cloudConversations: [],
  });
  assert.equal((rendered.match(/data-action="load-account-cloud-documents"/g) || []).length, 1);
  assert.doesNotMatch(rendered, /chat-cloud-empty[\s\S]*data-action="load-account-cloud-documents"/);
  const app = await readFile(resolve(import.meta.dirname, '../src/app.mjs'), 'utf8');
  for (const token of [
    'cloudConversationCacheKey',
    'function persistCloudConversationCache',
    'async function restoreCachedCloudConversationList',
    "startupStage = 'CLOUD_LIST_CACHE_READBACK'",
    'await restoreCachedCloudConversationList()',
  ]) assert.ok(app.includes(token));
  assert.match(app, /cloudRowsFromLocalEntries[\s\S]*read_desktop_workspace[\s\S]*read_desktop_favorite_conversation_ids/);
  assert.match(app, /sign-out-google-account[\s\S]*clearCloudConversationCache/);
});

test('cloud tab renders the restored cloud conversations without deleting local rows', async () => {
  const rendered = renderChatFirstShell({
    data: fixture, native: true, pane: 'chat', selectedConversationId: 'conversation-safe',
    sidebarConversationList: 'cloud', cloudConversations: [{ workspaceId: 'workspace-cloud-safe', id: 'conversation-safe', title: '云端测试会话', revision: 1, createdAt: '2026-09-13T00:00:00Z', updatedAt: '2026-09-13T00:00:00Z', messages: [] }],
  });
  assert.ok(rendered.includes('云端测试会话'));
  assert.match(rendered, /aria-label="本地会话"/);
  assert.match(rendered, /aria-label="云端会话"/);
  assert.ok(rendered.includes('data-action="select-chat" data-id="conversation-safe" data-workspace-id="workspace-cloud-safe"'));
  assert.ok(rendered.includes('data-action="set-conversation-pinned"'));
  assert.ok(rendered.includes('data-action="open-conversation-rename"'));
  assert.ok(rendered.includes('data-action="open-conversation-row-menu"'));
  assert.ok(!rendered.includes('data-action="restore-account-cloud-conversation"'));
  const app = await readFile(resolve(import.meta.dirname, '../src/app.mjs'), 'utf8');
  assert.match(app, /ensureConversationActionWorkspace\(target\)/);
  assert.match(app, /context-menu-cancel-sync/);
  assert.match(app, /sync_selected_desktop_conversation/);
});

test('local synced conversations show a cloud marker before the title', async () => {
  const rendered = renderChatFirstShell({
    data: fixture, native: true, pane: 'chat', sidebarConversationList: 'recent',
    syncedConversationKeys: new Set(['workspace-safe:conversation-safe']),
  });
  assert.match(rendered, /chat-history-cloud-sync" aria-label="已同步到云端"[\s\S]*chat-history-title">安全对话/);
  const [shell, app, owner] = await Promise.all([
    readFile(resolve(import.meta.dirname, '../src/chat-shell.mjs'), 'utf8'),
    readFile(resolve(import.meta.dirname, '../src/app.mjs'), 'utf8'),
    readFile(resolve(import.meta.dirname, '../src-tauri/src/desktop_account_sync_v1.rs'), 'utf8'),
  ]);
  assert.match(shell, /syncedConversationKeys = new Set\(\), localWorkspaceId = null/);
  assert.match(app, /syncedConversationKeys: new Set\(state\.accountSync\?\.syncedConversationKeys \|\| \[\]\)/);
  assert.match(owner, /pub synced_conversation_keys: Vec<String>/);
  assert.match(owner, /SELECT workspace_id,conversation_id FROM desktop_selected_conversation_sync WHERE account_ref=\?1/);
});

test('local synced marker matches the phone filled cloud with a quiet smaller white check', async () => {
  const [icons, css] = await Promise.all([
    readFile(resolve(import.meta.dirname, '../src/icon-source.mjs'), 'utf8'),
    readFile(resolve(import.meta.dirname, '../src/chat-shell.css'), 'utf8'),
  ]);
  assert.match(icons, /const cloudDoneGlyph = '<path fill="currentColor" stroke="none"[\s\S]*stroke-width="1\.7"/);
  assert.match(icons, /cloudDone: cloudDoneGlyph/);
  assert.match(icons, /accountCloudDone: cloudDoneGlyph/);
  assert.match(css, /\.chat-history-cloud-sync svg \{[^}]*fill: currentColor;[^}]*stroke: none;[^}]*\}/);
});

test('cloud-list pin is a presentation action, never mutates the local conversation, and does not pin action controls open', async () => {
  const [app, css] = await Promise.all([
    readFile(resolve(import.meta.dirname, '../src/app.mjs'), 'utf8'),
    readFile(resolve(import.meta.dirname, '../src/chat-shell.css'), 'utf8'),
  ]);
  assert.match(app, /function isCloudListConversationTarget\(target\)/);
  assert.match(app, /async function setCloudListConversationPinned\(target, pinned\)/);
  assert.match(app, /state\.sidebarConversationList === 'cloud'[\s\S]*setCloudListConversationPinned/);
  assert.match(app, /cloudPinned: Boolean\(item\.cloudPinned\)/);
  assert.match(app, /云端列表已置顶；本地列表未改动。/);
  assert.doesNotMatch(css, /\[data-conversation-row\]\[data-pinned="true"\] \.chat-row-actions/);
  assert.doesNotMatch(css, /\[data-conversation-row\]\[data-pinned="true"\] \.chat-history-date/);
});

test('manual sync retains a direct in-place result card instead of relying on a corner toast', async () => {
  const [app, css] = await Promise.all([
    readFile(resolve(import.meta.dirname, '../src/app.mjs'), 'utf8'),
    readFile(resolve(import.meta.dirname, '../src/chat-shell.css'), 'utf8'),
  ]);
  assert.match(app, /const ACCOUNT_SYNC_RESULT_VISIBLE_MS = 2600/);
  assert.match(app, /result: \{ message, kind \}/);
  assert.match(app, /await completeAccountSyncProgress\('', 'success'\)/);
  assert.match(app, /function accountSyncProgressDialog\(\)/);
  assert.match(css, /\.account-sync-progress-dialog\.result\.success svg/);
  assert.match(css, /\.account-sync-progress-dialog\.result\.error svg/);
});

test('sync keeps an uncertain commit out of the failure path and reconciles it in the background', async () => {
  const [app, owner, command] = await Promise.all([
    readFile(resolve(import.meta.dirname, '../src/app.mjs'), 'utf8'),
    readFile(resolve(import.meta.dirname, '../src-tauri/src/desktop_account_sync_v1.rs'), 'utf8'),
    readFile(resolve(import.meta.dirname, '../src-tauri/src/lib.rs'), 'utf8'),
  ]);
  assert.match(app, /function scheduleUnknownSyncReconciliation\(workspaceId, conversationId\)/);
  assert.match(app, /云端提交正在后台核对；不会重复上传。/);
  assert.match(app, /scheduleUnknownSyncReconciliation\(workspaceId, conversationId\)/);
  assert.match(owner, /UPDATE desktop_cloud_account_state SET state='VERIFYING',last_error_code='COMMIT_RESULT_UNKNOWN'/);
  for (const commandName of ['sync_selected_desktop_conversation', 'cancel_desktop_conversation_sync', 'reconcile_desktop_conversation_sync', 'restore_all_desktop_cloud_conversations']) {
    const start = command.indexOf(`fn ${commandName}`);
    const body = command.slice(start, command.indexOf('\n#[tauri::command]', start + 1));
    assert.match(body, /open_desktop_workspace_connection\(\s*&state\.store_paths\.root,\s*&state\.store_paths\.database,?\s*\)/);
    assert.doesNotMatch(body, /state\.store\s*\.lock\(\)/);
  }
});

test('desktop batch edit follows the Android conversation list contract', async () => {
  const rendered = renderChatFirstShell({
    data: fixture, native: true, pane: 'chat', selectedConversationId: 'conversation-safe',
    batchEditing: true, batchSelectedConversationKeys: new Set(['local:conversation-safe']),
  });
  assert.match(rendered, /data-action="toggle-batch-conversation"/);
  assert.match(rendered, /aria-checked="true"/);
  const unselectedRendered = renderChatFirstShell({
    data: fixture, native: true, pane: 'chat', selectedConversationId: 'conversation-safe',
    batchEditing: true, batchSelectedConversationKeys: new Set(),
  });
  assert.match(unselectedRendered, /class="chat-batch-select"[^>]*aria-checked="false"/);
  assert.match(rendered, /data-action="toggle-batch-select-all"/);
  assert.match(rendered, /data-action="request-batch-conversation-delete"/);
  assert.match(rendered, /data-action="toggle-conversation-batch-edit"/);
  assert.match(rendered, /<footer class="chat-sidebar-footer">[\s\S]*chat-sidebar-batch-controls[\s\S]*chat-sidebar-bottom-actions/);
  assert.ok(!rendered.includes('data-action="open-conversation-row-menu"'));
  const app = await readFile(resolve(import.meta.dirname, '../src/app.mjs'), 'utf8');
  assert.match(app, /function currentBatchCandidates\(\)/);
  assert.match(app, /function captureCloudSidebarWorkspace\(\)/);
  assert.match(app, /async function restoreCloudSidebarWorkspace\(snapshot\)/);
  assert.match(app, /await restoreCloudSidebarWorkspace\(sidebarWorkspace\);/);
  assert.match(app, /conversation-batch-delete/);
  assert.match(app, /已将 \$\{completed\} 个会话移入回收站，可恢复。/);
  const css = await readFile(resolve(import.meta.dirname, '../src/chat-shell.css'), 'utf8');
  for (const token of [
    '.chat-sidebar-scroll { display: flex; min-height: 0; flex: 1 1 auto; flex-direction: column; overflow-x: hidden; overflow-y: auto; padding-bottom: 126px;',
    '.chat-sidebar-list-utility.selected { background: var(--accent-orange);',
    '.chat-history-row.batch-editing { grid-template-columns: 20px minmax(0, 1fr) auto;',
    '.chat-first .chat-batch-select { display: grid; width: 16px; height: 16px; align-self: center;',
    'min-width: 16px; min-height: 16px; max-width: 16px; max-height: 16px;',
    'border: 1px solid #aeb8b1; border-radius: 6px; background: #fff;',
    '.chat-batch-select > svg { width: 10px; height: 10px; stroke-width: 2.4; }',
    '.chat-first .chat-batch-select:hover,',
    '.chat-first .chat-batch-select[aria-checked="true"] { border-color: color-mix(in srgb, var(--accent-orange) 54%, #fff); background: color-mix(in srgb, var(--accent-orange) 14%, #fff); }',
    '.chat-sidebar-batch-controls { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 4px; margin: 0 4px 8px;',
  ]) assert.ok(css.includes(token), token);
});

test('desktop cloud read is a single batch action that skips retired direct envelopes', async () => {
  const [owner, command, app, permissions] = await Promise.all([
    readFile(resolve(import.meta.dirname, '../src-tauri/src/desktop_account_sync_v1.rs'), 'utf8'),
    readFile(resolve(import.meta.dirname, '../src-tauri/src/lib.rs'), 'utf8'),
    readFile(resolve(import.meta.dirname, '../src/app.mjs'), 'utf8'),
    readFile(resolve(import.meta.dirname, '../src-tauri/permissions/default.toml'), 'utf8'),
  ]);
  assert.match(owner, /sync_v1::open_with_account_wrapping_material\(/);
  assert.doesNotMatch(owner, /sync_v1::open_direct\(/);
  assert.match(owner, /CLOUD_LIST_LEGACY_SKIPPED/);
  assert.match(owner, /restore_remote_envelope\(connection, credentials, document\)/);
  assert.match(owner, /skipped_legacy_count/);
  assert.match(owner, /if envelope\.get\("format"\).*DIRECT_ENVELOPE/s);
  assert.match(command, /fn restore_all_desktop_cloud_conversations\(/);
  assert.match(command, /restore_all_remote_conversations/);
  assert.match(app, /invoke\('restore_all_desktop_cloud_conversations'\)/);
  assert.match(permissions, /"restore_all_desktop_cloud_conversations"/);
  assert.match(app, /read_desktop_workspace.*read_desktop_favorite_conversation_ids/s);
  assert.match(app, /已读取 \$\{state\.cloudConversations\.length\} 个云端会话，已显示在云端列表。/);
  assert.doesNotMatch(owner, /云端尚未支持完整传输，未同步/);
  assert.match(owner, /Explicitly syncing this local conversation makes its complete current/);
  assert.match(owner, /known\.0 as u64 == current\.revision && known\.1 == current\.payload_hash/);
  assert.doesNotMatch(app, /restore_all_desktop_cloud_conversations[\s\S]{0,160}recoveryCode/);
});

test('conversation context menu owns the selected direct sync entry and app uses only narrow native commands', async () => {
  const rendered = renderChatFirstShell({
    data: fixture,
    native: true,
    selectedConversationId: 'conversation-safe',
    composerDraft: '',
    chatSearch: '',
    pane: 'chat',
    status: '',
    error: '',
    connection: {},
    contextMenu: { id: 'conversation-safe', revision: 4, pinned: false, favorite: false },
    accountSync: { state: 'READY' },
  });
  assert.ok(rendered.includes('data-action="context-menu-sync"'));
  assert.ok(rendered.includes('同步到南枫云'));
  assert.ok(!rendered.includes('data-action="save-local-message">\n'));

  const source = await readFile(resolve(import.meta.dirname, '../src/app.mjs'), 'utf8');
  for (const command of ['sync_selected_desktop_conversation', 'reconcile_desktop_conversation_sync', 'create_desktop_recovery_rotation', 'confirm_desktop_recovery_rotation', 'restore_all_desktop_cloud_conversations']) assert.ok(source.includes(command));
  assert.ok(source.includes('云端提交正在后台核对；不会重复上传。'));
  assert.ok(source.includes('此 Desktop 尚未写入南枫云地址或公开访问密钥'));
  assert.ok(source.includes("'show-google-login-requirements', 'sign-in-google-account'"));
  assert.ok(source.includes("NotificationOwner.permission !== 'granted'"));
  assert.ok(source.includes("tag: 'nanfeng-account-sync-v1'"));
  assert.ok(source.includes('function accountSyncErrorMessage(error)'));
  assert.ok(source.includes('账号与同步操作未完成：${accountSyncErrorMessage(error)}'));
  const syncHandler = source.slice(source.indexOf("if (!['show-google-login-requirements'"));
  const nativePreflightStart = syncHandler.indexOf("if (action === 'context-menu-sync')", syncHandler.indexOf('if (!native)') + 1);
  const preflight = syncHandler.slice(nativePreflightStart, syncHandler.indexOf("const receipt = await invoke('sync_selected_desktop_conversation'", nativePreflightStart));
  assert.match(preflight, /state\.contextMenu = null;[\s\S]*beginAccountSyncProgress\('sync'\);[\s\S]*waitForAccountSyncProgressPaint\(\)/);
  assert.doesNotMatch(preflight, /await loadDesktopAccountSync\(\)/);
  assert.ok(!preflight.includes("state.accountSync.state !== 'READY'"));
  assert.ok(!preflight.includes("state.pane = 'settings'"));
  assert.ok(!preflight.includes("state.settingsSection = 'account'"));
});

test('cloud conversation uses the same menu with a real cancel-sync action', async () => {
  const [rendered, app, owner, permissions, migration] = await Promise.all([
    Promise.resolve(renderChatFirstShell({
      data: fixture, native: true, pane: 'chat', status: '', error: '', connection: {}, accountSync: { state: 'READY' },
      contextMenu: { id: 'conversation-safe', revision: 4, pinned: false, favorite: false, workspaceId: 'workspace-cloud-safe' },
    })),
    readFile(resolve(import.meta.dirname, '../src/app.mjs'), 'utf8'),
    readFile(resolve(import.meta.dirname, '../src-tauri/src/desktop_account_sync_v1.rs'), 'utf8'),
    readFile(resolve(import.meta.dirname, '../src-tauri/permissions/default.toml'), 'utf8'),
    readFile(resolve(import.meta.dirname, '../../supabase/migrations/202609130005_p8_cancel_direct_sync.sql'), 'utf8'),
  ]);
  assert.match(rendered, /data-action="context-menu-cancel-sync"/);
  assert.match(rendered, /取消同步/);
  assert.match(app, /invoke\('cancel_desktop_conversation_sync'/);
  assert.match(owner, /pub fn cancel_remote_conversation/);
  assert.match(permissions, /"cancel_desktop_conversation_sync"/);
  assert.match(migration, /nanfeng_sync_delete_document/);
});

test('Desktop OAuth uses Supabase hosted Google PKCE without bundling a Google secret and never holds SQLite while waiting', async () => {
  const [owner, command, bundler, app] = await Promise.all([
    readFile(resolve(import.meta.dirname, '../src-tauri/src/desktop_account_sync_v1.rs'), 'utf8'),
    readFile(resolve(import.meta.dirname, '../src-tauri/src/lib.rs'), 'utf8'),
    readFile(resolve(import.meta.dirname, '../scripts/bundle-macos.mjs'), 'utf8'),
    readFile(resolve(import.meta.dirname, '../src/app.mjs'), 'utf8'),
  ]);
  assert.ok(owner.includes('pub fn authorize_with_system_browser'));
  assert.ok(owner.includes('pub fn persist_authenticated_session'));
  assert.ok(owner.includes('.set_nonblocking(false)'));
  assert.ok(owner.includes('while !bytes.windows(2).any'));
  const productionOwner = owner.slice(0, owner.indexOf('#[cfg(test)]'));
  assert.ok(productionOwner.includes('.join("auth/v1/authorize")'));
  assert.ok(productionOwner.includes('.join("auth/v1/token?grant_type=pkce")'));
  assert.ok(productionOwner.includes('.append_pair("redirect_to", callback.as_str())'));
  assert.ok(productionOwner.includes('.append_pair("code_challenge_method", "s256")'));
  assert.ok(!productionOwner.includes('oauth2.googleapis.com/token'));
  assert.ok(!productionOwner.includes('client_secret'));
  assert.ok(!bundler.includes("'nanfeng.ai.cloud.googleDesktopClientId'"));
  assert.ok(!bundler.includes('Google Desktop OAuth Client ID'));
  assert.ok(!bundler.includes("'nanfeng.ai.cloud.googleServerClientId'"));
  const signIn = command.slice(command.indexOf('fn sign_in_desktop_google_account('), command.indexOf('\n#[tauri::command]', command.indexOf('fn sign_in_desktop_google_account(') + 1));
  assert.ok(signIn.indexOf('authorize_with_system_browser') < signIn.indexOf('open_desktop_workspace_connection'));
  assert.ok(signIn.indexOf('fetch_avatar') < signIn.indexOf('open_desktop_workspace_connection'));
  assert.ok(signIn.indexOf('persist_authenticated_session') > signIn.indexOf('open_desktop_workspace_connection'));
  assert.ok(app.includes('let accountSignInPending = false;'));
  assert.ok(app.includes('Google 授权未完成；本机数据与云端均未改变，可检查配置后重试。'));
});
