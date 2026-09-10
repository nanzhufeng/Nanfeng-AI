import test from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { resolve } from 'node:path';
import { renderChatFirstShell } from '../src/chat-shell.mjs';

const fixture = {
  summary: { id: 'workspace-safe' },
  exchange: { conversations: [{ id: 'conversation-safe', title: '安全对话', revision: 4, messages: [] }] },
};

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
  for (const token of ['Google 账号与同步', '安全账号，您好！', 'safe@example.invalid', '账号管理', '切换 Google 账号', '退出登录', '选择首次同步方向', 'data-action="choose-selected-local-sync-start"', '对话同步', '仅包含你手动同步过的对话。', '恢复码更换与丢失处理', 'data-action="create-account-recovery-rotation"', '从南枫云恢复', 'data-action="restore-account-cloud-conversation"', '<span>恢复码</span><input']) assert.ok(rendered.includes(token));
  for (const token of ['同步通知', '运行诊断', 'SYNC_RECONCILE', '已选 1 个']) assert.ok(!rendered.includes(token));
  for (const forbidden of ['access_token', 'refresh_token', 'privatePath', '恢复码明文']) assert.ok(!rendered.includes(forbidden));
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

test('recovery code is rendered once with an explicit save confirmation action', () => {
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
  for (const token of ['恢复码只显示这一次', 'NF-ONLY-ONCE-SAFE', 'data-action="confirm-account-recovery"', 'data-confirmation-hash="hash-safe"']) assert.ok(rendered.includes(token));
  assert.equal(rendered.match(/NF-ONLY-ONCE-SAFE/g)?.length, 1);
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
  assert.ok(source.includes("NotificationOwner.permission !== 'granted'"));
  assert.ok(source.includes("tag: 'nanfeng-account-sync-v1'"));
});
