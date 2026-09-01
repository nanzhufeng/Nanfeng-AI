import test from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { resolve } from 'node:path';
import { activeConversations, renderChatFirstShell } from '../src/chat-shell.mjs';

const conversation = (id, updatedAt, extra = {}) => ({
  id,
  title: id,
  revision: 1,
  createdAt: updatedAt,
  updatedAt,
  messages: [],
  ...extra,
});

const conversations = [
  conversation('pinned-recent', '2026-09-01T10:00:00Z', { pinned: true }),
  conversation('pinned-manual', '2026-09-01T08:00:00Z', { pinned: true }),
  conversation('recent-newer', '2026-09-01T11:00:00Z'),
  conversation('recent-manual-old', '2026-09-01T07:00:00Z'),
];
const data = { summary: { id: 'workspace-unread' }, exchange: { conversations } };
const manualUnreadAtMs = new Map([
  ['pinned-manual', 200],
  ['recent-manual-old', 100],
]);

test('manual unread sorts first only inside the pinned or recent partition', () => {
  assert.deepEqual(
    activeConversations(data, manualUnreadAtMs).map(item => item.id),
    ['pinned-manual', 'pinned-recent', 'recent-manual-old', 'recent-newer'],
  );
});

test('conversation rows render one theme dot and stable pin-dot-progress-title order', () => {
  const runningData = {
    ...data,
    exchange: {
      conversations: conversations.map(item => item.id === 'pinned-manual'
        ? { ...item, messages: [{ id: 'assistant-running', role: 'assistant', delivery: 'PARTIAL' }] }
        : item),
    },
  };
  const html = renderChatFirstShell({
    data: runningData,
    native: true,
    selectedConversationId: 'recent-newer',
    pane: 'chat',
    status: '',
    error: '',
    connection: {},
    unreadConversationIds: new Set(['pinned-manual']),
    manualUnreadAtMs,
  });
  const row = html.match(/<div class="chat-history-row[^>]*data-id="pinned-manual"[\s\S]*?<\/div>\s*<\/div>/)?.[0] || '';
  assert.ok(row);
  assert.equal((row.match(/chat-history-unread/g) || []).length, 1);
  const order = ['class="chat-history-pin"', 'class="chat-history-unread"', 'class="chat-history-running"', 'class="chat-history-title"'];
  for (let index = 1; index < order.length; index += 1) {
    assert.ok(row.indexOf(order[index - 1]) < row.indexOf(order[index]), `${order[index - 1]} must precede ${order[index]}`);
  }
});

test('conversation menu places unread directly after pin and settings enable its real consumer', () => {
  const html = renderChatFirstShell({
    data,
    native: true,
    selectedConversationId: 'recent-newer',
    pane: 'chat',
    status: '',
    error: '',
    connection: {},
    contextMenu: { id: 'recent-newer', revision: 1, pinned: false, favorite: false, archived: false },
  });
  assert.ok(html.indexOf('context-menu-pin') < html.indexOf('context-menu-unread'));
  assert.ok(html.indexOf('context-menu-unread') < html.indexOf('context-menu-favorite'));

  const settings = renderChatFirstShell({
    data,
    native: true,
    pane: 'settings',
    settingsSection: 'reminders',
    status: '',
    error: '',
    connection: {},
    productSettings: { unreadIndicators: true },
    settingsCapabilities: { unreadIndicators: true },
  });
  assert.match(settings, /在左侧对话列表显示主题色未读标记/);
  assert.doesNotMatch(settings, /Desktop 尚无未读水位与列表消费者/);
});

test('runtime wiring preserves the route and keeps read markers device-local', async () => {
  const [appSource, backupSource, syncSource] = await Promise.all([
    readFile(resolve(import.meta.dirname, '../src/app.mjs'), 'utf8'),
    readFile(resolve(import.meta.dirname, '../src-tauri/src/desktop_local_backup_v1.rs'), 'utf8'),
    readFile(resolve(import.meta.dirname, '../src-tauri/src/desktop_account_sync_v1.rs'), 'utf8'),
  ]);
  assert.doesNotMatch(appSource, /state\.selectedConversationId\s*=\s*payload\.conversationId\s*\|\|\s*state\.selectedConversationId/);
  for (const token of [
    'read_desktop_conversation_read_state',
    'mark_desktop_conversation_opened',
    'mark_desktop_conversation_unread',
    'pendingCreatedConversationRouteWorkspaceId',
    'routeStillOwned',
  ]) assert.ok(appSource.includes(token));
  assert.match(backupSource, /PRESERVED_DEVICE_TABLES[\s\S]*desktop_conversation_read_markers_v1/);
  assert.doesNotMatch(syncSource, /desktop_conversation_read_markers_v1/);
});
