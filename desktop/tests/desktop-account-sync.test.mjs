import test from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { resolve } from 'node:path';
import { renderChatFirstShell } from '../src/chat-shell.mjs';

const fixture = {
  summary: { id: 'workspace-safe' },
  exchange: { conversations: [{ id: 'conversation-safe', title: '安全对话', revision: 4, messages: [] }] },
};

test('recovery replacement accepts one private input instead of generating a code', () => {
  const html = renderChatFirstShell({ data: fixture, native: true, pane: 'settings', settingsSection: 'account', accountSync: { email: 'safe@example.invalid', state: 'READY', recoveryState: 'CONFIRMED' }, accountRecovery: { rotation: true } });
  assert.match(html, /id="new-recovery-code" type="password"/);
  assert.doesNotMatch(html, /confirm-new-recovery-code/);
  assert.doesNotMatch(html, /再次输入新恢复码/);
  assert.match(html, />确认更换<\/button>/);
  assert.doesNotMatch(html, /新恢复码只显示这一次/);
});

test('an unsubmitted recovery replacement is cleared when leaving account settings', async () => {
  const app = await readFile(resolve(import.meta.dirname, '../src/app.mjs'), 'utf8');
  assert.match(app, /nextSettingsSection !== 'account' && state\.accountRecovery\?\.rotation === true\) state\.accountRecovery = null/);
  assert.match(app, /action === 'settings-back'[\s\S]{0,180}state\.accountRecovery\?\.rotation === true\) state\.accountRecovery = null/);
  assert.match(app, /action === 'return-to-settings-conversation-list'[\s\S]{0,220}state\.accountRecovery\?\.rotation === true\) state\.accountRecovery = null/);
});

test('recovery input and account actions use the same gray pill surface as Android', async () => {
  const css = await readFile(resolve(import.meta.dirname, '../src/chat-shell.css'), 'utf8');
  assert.match(css, /\.android-account-recovery > \.android-settings-field > input\s*\{[\s\S]*border-radius:\s*999px[\s\S]*background:\s*#f1f3f1/);
  assert.match(css, /\.android-account-recovery button,[\s\S]*background:\s*#f1f3f1/);
});

test('account settings follow the phone identity, management and selected-only sync hierarchy', () => {
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

test('new recovery code is rendered once with a copy action and centered confirmation action', async () => {
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

test('first desktop sync uses the existing phone recovery code against a cloud document instead of creating a second code', () => {
  const rendered = renderChatFirstShell({
    data: fixture, native: true, pane: 'settings', settingsSection: 'account', status: '', error: '', connection: {},
    settingsCapabilities: { googleAccountSync: true },
    accountSync: { configured: true, state: 'AUTHENTICATED_NEEDS_RECOVERY_CONFIRMATION', email: 'safe@example.invalid', recoveryState: 'NEEDS_CONFIRMATION', diagnostics: [], notifications: [], remoteDocuments: [{ documentId: 'conversation-phone-safe', revision: 2, payloadHashPrefix: 'abcdef123456' }] },
  });
  for (const token of ['使用手机端已有恢复码', '输入手机端正在使用的恢复码', '验证并连接此设备', 'data-action="restore-account-cloud-conversation"']) assert.ok(rendered.includes(token));
  assert.ok(!rendered.includes('data-action="create-account-recovery"'));
});

test('conversation context menu owns the selected encrypted sync entry and app uses only narrow native commands', async () => {
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
  for (const command of ['sync_selected_desktop_conversation', 'reconcile_desktop_conversation_sync', 'create_desktop_recovery_code', 'confirm_desktop_recovery_code', 'confirm_desktop_recovery_rotation', 'restore_desktop_cloud_conversation']) assert.ok(source.includes(command));
  assert.ok(source.includes('提交结果未知；已停止重发'));
  assert.ok(source.includes('此 Desktop 尚未写入南枫云地址或公开访问密钥'));
  assert.ok(source.includes("'show-google-login-requirements', 'sign-in-google-account'"));
  assert.ok(source.includes("NotificationOwner.permission !== 'granted'"));
  assert.ok(source.includes("tag: 'nanfeng-account-sync-v1'"));
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
  assert.ok(signIn.indexOf('authorize_with_system_browser') < signIn.indexOf('.store\n        .lock()'));
  assert.ok(signIn.indexOf('fetch_avatar') < signIn.indexOf('.store\n        .lock()'));
  assert.ok(signIn.indexOf('persist_authenticated_session') > signIn.indexOf('.store\n        .lock()'));
  assert.ok(app.includes('let accountSignInPending = false;'));
  assert.ok(app.includes('Google 授权未完成；本机数据与云端均未改变，可检查配置后重试。'));
});
