import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import test from 'node:test';
import { renderAndroidSettingsShell } from '../src/android-settings-shell.mjs';

const favoriteId = 'conversation-c13-favorite';
const archivedId = 'conversation-c13-archived';
const recycleId = 'conversation-c13-recycle';
const conversations = [
  {
    id: favoriteId,
    title: 'C13 Favorite',
    createdAt: '2026-09-03T18:10:00+08:00',
    updatedAt: '2026-09-03T18:20:00+08:00',
    revision: 2,
    archived: false,
    deleted: false,
  },
  {
    id: archivedId,
    title: 'C13 Archived',
    createdAt: '2026-09-03T18:21:00+08:00',
    updatedAt: '2026-09-03T18:31:00+08:00',
    revision: 3,
    archived: true,
    deleted: false,
  },
  {
    id: recycleId,
    title: 'C13 Recycle',
    createdAt: '2026-09-03T18:22:00+08:00',
    updatedAt: '2026-09-03T18:32:00+08:00',
    revision: 4,
    archived: true,
    deleted: true,
  },
];

const render = page => renderAndroidSettingsShell({
  page,
  data: { summary: { id: 'workspace-c13' }, exchange: { conversations } },
  favoriteConversationIds: new Set([favoriteId]),
});

test('C13 management root preserves the current Android order and copy', () => {
  const html = render('conversations');
  const labels = ['收藏', '已归档', '回收站'];
  let previous = -1;
  for (const label of labels) {
    const index = html.indexOf(label);
    assert.ok(index > previous, label);
    previous = index;
  }
  for (const copy of [
    '查看并管理已收藏的本地对话。',
    '查看并恢复暂时收起的会话；归档不会删除消息或附件。',
    '查看并恢复已移入回收站的会话；消息树仍保留在本机。',
  ]) assert.ok(html.includes(copy), copy);
});

test('C13 favorites keep the title-only mobile list and cancel-favorite disclosure', () => {
  const html = render('favorites');
  assert.ok(html.includes('C13 Favorite'));
  assert.ok(!html.includes('收藏的会话保存在本机；取消收藏不会删除消息或附件。'));
  assert.ok(!html.includes('更新于 2026-09-03 18:20'));
  assert.match(html, /<details[^>]*data-lifecycle-action-disclosure="conversation-c13-favorite"[\s\S]*data-action="toggle-conversation-favorite"/);
  assert.ok(!html.includes('还没有收藏会话。'));
});

test('C13 archived and recycle rows use created time with restore and delete disclosures', () => {
  const archived = render('archived');
  assert.ok(archived.includes('归档会话不会出现在日常列表；恢复后会回到普通对话列表。'));
  assert.ok(archived.includes('创建于 2026-09-03 18:21'));
  assert.match(archived, /data-lifecycle-action-disclosure="conversation-c13-archived"[\s\S]*data-action="restore-conversation"[\s\S]*data-action="open-archived-conversation-delete"/);

  const recycle = render('recycle');
  assert.ok(recycle.includes('会话消息树尚未物理删除；恢复后会回到普通对话列表。'));
  assert.ok(recycle.includes('创建于 2026-09-03 18:22'));
  assert.match(recycle, /data-lifecycle-action-disclosure="conversation-c13-recycle"[\s\S]*data-action="restore-deleted-conversation"[\s\S]*data-action="open-conversation-permanent-delete"/);
});

test('C13 keeps destructive confirmation and list-return scroll owners intact', async () => {
  const source = await readFile(new URL('../src/app.mjs', import.meta.url), 'utf8');
  for (const token of [
    'settingsConversationReturn',
    'settingsScrollPositions',
    'return-to-settings-conversation-list',
    '”将移入回收站，可恢复。',
    '将永久删除“${escape(state.dialog.title)}”，无法恢复。',
    'confirm-conversation-permanent-delete',
    'confirm-conversation-bulk-cleanup',
  ]) assert.ok(source.includes(token), token);
});

test('C13 and memory transient menus reset browser Popover viewport geometry before placement', async () => {
  const [layer, css] = await Promise.all([
    readFile(new URL('../src/modal-layer-owner.mjs', import.meta.url), 'utf8'),
    readFile(new URL('../src/chat-shell.css', import.meta.url), 'utf8'),
  ]);
  for (const token of [
    "const selector = '.conversation-lifecycle-actions, .memory-reference-menu'",
    "panel.style.inset = 'auto'",
    "panel.style.bottom = 'auto'",
    "panel.style.height = 'fit-content'",
    "panel.style.gridAutoRows = 'max-content'",
  ]) assert.ok(layer.includes(token), token);
  assert.match(css, /\.conversation-lifecycle-actions > span \{ position: absolute;/);
  assert.match(css, /\.memory-reference-menu > div \{ position: absolute;/);
  // The manual Popover UA stylesheet otherwise contributes a thick CanvasText
  // border around the memory menu. The menu has a white surface and soft
  // shadow only; its visible edge must come from its own component rule.
  assert.match(css, /\.memory-reference-menu > div \{[^}]*border: 0;[^}]*outline: 0;/);
});

test('C13 lifecycle primary rows do not inherit the broad generic press fill', async () => {
  const css = await readFile(new URL('../src/chat-shell.css', import.meta.url), 'utf8');
  assert.match(css, /\.conversation-lifecycle-row \.android-settings-lifecycle-open:is\(:hover, :active\) \{ background: transparent !important; \}/);
  assert.match(css, /\.conversation-lifecycle-row \.android-settings-lifecycle-open:focus-visible \{[^}]*border-radius: 14px;[^}]*outline: 2px solid var\(--accent-subtle-border\);/);
});

test('C13 native fixture is diagnostic-only and requires a unique temporary root', async () => {
  const source = await readFile(new URL('../src-tauri/src/lib.rs', import.meta.url), 'utf8');
  for (const token of [
    'NANFENG_AI_DESKTOP_C13_ACCEPTANCE',
    'NANFENG_AI_DESKTOP_C13_ACCEPTANCE_ROOT',
    '/tmp/nanfeng-ai-desktop-c13-acceptance.',
    'seed_c13_conversation_lifecycle_acceptance',
    'DesktopStartupMode::UiSchemaDiagnostic',
  ]) assert.ok(source.includes(token), token);
});
