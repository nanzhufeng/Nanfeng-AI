import test from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { resolve } from 'node:path';
import { renderChatFirstShell } from '../src/chat-shell.mjs';

const emptyData = {
  summary: { id: 'workspace-c01-c03' },
  exchange: { conversations: [], projects: [], knowledge: [], memory: [], relations: [] },
};

const activeData = {
  ...emptyData,
  exchange: {
    ...emptyData.exchange,
    conversations: [{
      id: 'conversation-c02',
      title: '桌面端同状态验收',
      createdAt: '2026-09-03T02:00:00Z',
      updatedAt: '2026-09-03T02:01:00Z',
      revision: 2,
      messages: [
        { id: 'message-user', role: 'user', createdAt: '2026-09-03T02:00:00Z', blocks: [{ kind: 'TEXT', text: '请只回答这条本地验收消息。' }] },
        { id: 'message-assistant', role: 'assistant', createdAt: '2026-09-03T02:01:00Z', delivery: 'COMPLETE', blocks: [{ kind: 'TEXT', text: '已使用同一会话画布。' }] },
      ],
    }],
  },
};

const render = overrides => renderChatFirstShell({
  data: emptyData,
  native: true,
  selectedConversationId: null,
  composerDraft: '',
  chatSearch: '',
  profileOpen: false,
  sidebarOpen: false,
  railCollapsed: false,
  pane: 'chat',
  status: '',
  error: '',
  connection: {},
  appearance: { mode: 'system', fontSize: 'standard', themeColor: 'orange' },
  ...overrides,
});

test('C01 empty conversation is a quiet canvas with the shared bottom Composer and no Hero copy', () => {
  const html = render();

  for (const token of [
    'class="chat-empty-canvas"',
    'class="chat-mode-switch"',
    'aria-label="临时聊天"',
    'data-composer-dock="fixed"',
    'class="chat-composer-controls"',
    'placeholder="回复 南枫AI"',
  ]) assert.ok(html.includes(token), token);

  for (const stale of [
    'chat-empty-logo',
    'chat-empty-title',
    '今天想一起做什么？',
    '发送会先原子写入本地消息与 Attempt',
  ]) assert.ok(!html.includes(stale), stale);
});

test('C02 content conversation keeps one header, message scroll owner, transcript, and Composer hierarchy', () => {
  const html = render({ data: activeData, selectedConversationId: 'conversation-c02' });

  for (const token of [
    'class="chat-header-content-actions"',
    'aria-label="新对话"',
    'aria-label="对话更多操作"',
    'data-scroll-owner="message-list"',
    'class="chat-date-divider"',
    'class="chat-message user"',
    'class="chat-message assistant"',
    '请只回答这条本地验收消息。',
    '已使用同一会话画布。',
    'data-composer-dock="fixed"',
    'class="chat-composer-controls"',
  ]) assert.ok(html.includes(token), token);

  assert.ok(!html.includes('class="chat-mode-switch"'));
  assert.ok(html.indexOf('data-scroll-owner="message-list"') < html.indexOf('data-composer-dock="fixed"'));
});

test('C03 sidebar preserves Android information order and uses floating settings/new-chat capsules', () => {
  const html = render({ data: activeData, selectedConversationId: 'conversation-c02' });
  const order = [
    'class="chat-brand"',
    'class="chat-sidebar-fixed-tools"',
    'class="chat-search-wrap"',
    'data-action="show-reminders"',
    'data-action="show-transcription"',
    'class="chat-history-group"',
    'class="chat-sidebar-footer"',
  ].map(token => html.indexOf(token));

  assert.ok(order.every(index => index >= 0), JSON.stringify(order));
  assert.deepEqual(order, [...order].sort((left, right) => left - right));
  for (const token of [
    'class="chat-search-glyph"',
    'aria-label="搜索"',
    'aria-label="定时任务"',
    'aria-label="南枫转写"',
    'chat-sidebar-settings',
    'aria-label="设置"',
    'data-action="new-chat"',
  ]) assert.ok(html.includes(token), token);
});

test('C03 keeps search, reminders, and transcription outside the conversation scroll owner', async () => {
  const html = render({ data: activeData, selectedConversationId: 'conversation-c02' });
  const fixedStart = html.indexOf('class="chat-sidebar-fixed-tools"');
  const scrollStart = html.indexOf('class="chat-sidebar-scroll"');
  assert.ok(fixedStart >= 0 && scrollStart > fixedStart);
  const fixedTools = html.slice(fixedStart, scrollStart);
  for (const token of ['id="chat-search"', 'data-action="show-reminders"', 'data-action="show-transcription"']) assert.ok(fixedTools.includes(token), token);
  assert.ok(!fixedTools.includes('class="chat-history-group"'));
  assert.ok(html.indexOf('class="chat-history-group"') > scrollStart);
  const css = await readFile(resolve(import.meta.dirname, '../src/chat-shell.css'), 'utf8');
  assert.match(css, /\.chat-sidebar-fixed-tools \{[^}]*flex: 0 0 auto;/s);
  assert.match(css, /\.chat-sidebar-scroll \{[^}]*flex: 1 1 auto;[^}]*overflow-y: auto;/s);
});

test('C01-C03 CSS keeps one floating shell geometry owner without bottom trays', async () => {
  const css = await readFile(resolve(import.meta.dirname, '../src/chat-shell.css'), 'utf8');

  for (const token of [
    '.chat-sidebar-bottom-actions .chat-profile.chat-sidebar-settings {',
    '.chat-first .chat-sidebar-function {',
    'width: 96px;',
    '.chat-sidebar-footer { position: absolute;',
    'background: transparent;',
    '.chat-composer-controls {',
    'grid-template-columns: 48px minmax(0, 1fr) auto;',
    '.chat-empty-canvas {',
  ]) assert.ok(css.includes(token), token);

  assert.match(css, /\.app-shell\.chat-first:not\(\.settings-mode\) > \.chat-main:not\(\.work-main\):not\(\.connections-main\) \{[^}]*grid-template-rows: minmax\(0, 1fr\);[^}]*background: var\(--foreground-surface, #fff\);/s);
  assert.match(css, /--chat-sidebar: #f5f5f5;/);
  assert.match(css, /\.chat-sidebar \{[^}]*background: var\(--chat-sidebar\);/s);
  assert.match(css, /> \.chat-main-header \{[^}]*position: absolute;[^}]*background: transparent;[^}]*pointer-events: none;/s);
  assert.match(css, /\.chat-header-content-actions \{[^}]*border-radius: 999px;[^}]*background: var\(--foreground-surface, #fff\);[^}]*box-shadow: 0 3px 54px rgb\(0 0 0 \/ 5\.295%\);/s);
  assert.match(css, /\.chat-header-content-actions > button \{[^}]*border-radius: 999px !important;[^}]*background: transparent !important;[^}]*box-shadow: none;/s);

  assert.ok(!css.includes('.chat-empty-logo'));
  assert.ok(!css.includes('.chat-empty h1'));
  assert.ok(!css.includes('.chat-empty p'));
  assert.match(css, /\.chat-main \{[^}]*background: var\(--foreground-surface, #fff\);/s);
});

test('C02 Browser preview uses the Android local visual fixture and fail-closed Provider state', async () => {
  const app = await readFile(resolve(import.meta.dirname, '../src/app.mjs'), 'utf8');

  for (const token of [
    "text: 'C02-local-visual-fixture'",
    "delivery: 'FAILED'",
    "safeErrorCode: 'PROVIDER_NOT_ENABLED'",
  ]) assert.ok(app.includes(token), token);
});
