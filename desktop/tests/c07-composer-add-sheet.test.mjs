import test from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { resolve } from 'node:path';
import { renderChatFirstShell } from '../src/chat-shell.mjs';

const desktopRoot = resolve(import.meta.dirname, '..');
const data = {
  summary: { id: 'workspace-c07' },
  exchange: {
    conversations: [{
      id: 'conversation-c07',
      title: 'C07 同状态会话',
      archived: false,
      revision: 1,
      updatedAt: '2026-09-03T00:00:00Z',
      messages: [],
    }],
    projects: [], knowledge: [], memory: [], relations: [],
  },
};

const renderAdd = ({ page = 'root', tone = 'professional', webSearch = true } = {}) => renderChatFirstShell({
  data,
  native: true,
  selectedConversationId: 'conversation-c07',
  composerDraft: '保留草稿',
  composerAddOpen: true,
  composerAddPage: page,
  conversationPreferences: { revision: 4, toneOverride: tone, webSearchOverride: webSearch },
  productSettings: { tone: 'friendly', webSearchEnabled: false },
  settingsCapabilities: { webSearch: false },
  pane: 'chat',
  status: '',
  error: '',
  connection: {},
});

test('C07 root uses the shared Composer transient-sheet layer instead of the retired add popover', () => {
  const html = renderAdd();
  for (const token of [
    'composer-transient-sheet-layer',
    'composer-transient-sheet-dismiss',
    'composer-transient-sheet composer-add-sheet',
    'composer-transient-sheet-body',
  ]) assert.ok(html.includes(token), token);
  assert.ok(!html.includes('composer-add-popover'));
});

test('C07 root preserves Android order, icon semantics, current style value, and the session web-search switch', () => {
  const html = renderAdd();
  const actions = [
    'open-composer-camera',
    'pick-composer-image',
    'pick-composer-file',
    'open-composer-tone-picker',
    'toggle-conversation-web-search',
  ];
  actions.forEach(action => assert.ok(html.includes(`data-action="${action}"`), action));
  for (let index = 1; index < actions.length; index += 1) {
    assert.ok(html.indexOf(`data-action="${actions[index - 1]}"`) < html.indexOf(`data-action="${actions[index]}"`));
  }
  for (const token of [
    'composer-add-row composer-add-attachment-row',
    'composer-add-icon-surface',
    'composer-add-row composer-add-style-row',
    'composer-add-icon-surface is-accent',
    'composer-add-value',
    '>专业可靠<',
    'role="switch"',
    'aria-checked="true"',
  ]) assert.ok(html.includes(token), token);
  assert.doesNotMatch(html, /data-action="toggle-conversation-web-search"[^>]*aria-pressed/);
  assert.doesNotMatch(html, /data-action="toggle-conversation-web-search"[^>]*disabled/);
});

test('C07 uses Android Rounded composer paths, never the Desktop attachment glyphs', async () => {
  const [shell, iconSource] = await Promise.all([
    readFile(resolve(desktopRoot, 'src/chat-shell.mjs'), 'utf8'),
    readFile(resolve(desktopRoot, 'src/icon-source.mjs'), 'utf8'),
  ]);
  const composerSlice = shell.slice(shell.indexOf('const composerPreferenceRows'), shell.indexOf('const composerAddRoot') + 1200);
  for (const token of ['icons.photoCamera', 'icons.addPhotoAlternate', 'icons.attachFile', 'icons.autoAwesome', 'icons.publicIcon']) {
    assert.ok(composerSlice.includes(token), token);
  }
  for (const token of ['icons.camera', 'icons.image', 'icons.file', 'icons.sparkles', 'icons.globe']) {
    assert.ok(!composerSlice.includes(token), token);
  }
  for (const token of ['photoCamera:', 'addPhotoAlternate:', 'attachFile:', 'autoAwesome:', 'publicIcon:']) {
    assert.ok(iconSource.includes(token), token);
  }
});

test('C07 style child contains default plus the five Android titles and only the selected check', () => {
  const html = renderAdd({ page: 'tone' });
  const labels = ['默认', '直言不讳', '专业可靠', '亲和友善', '高效务实', '风趣搞笑'];
  labels.forEach(label => assert.ok(html.includes(`>${label}<`), label));
  assert.equal((html.match(/data-action="select-conversation-tone"/g) || []).length, 6);
  assert.equal((html.match(/aria-label="当前风格"/g) || []).length, 1);
  assert.ok(html.includes('composer-transient-sheet-header'));
  assert.ok(html.includes('aria-label="返回附件菜单"'));
  assert.ok(html.includes('data-tone="default"'));
  for (const forbidden of ['先说结论', '专业顾问', '温和、耐心', '回答精简', '适度幽默', '完成</button>']) {
    assert.ok(!html.includes(forbidden), forbidden);
  }
});

test('C07 CSS owns one Android-derived add-sheet geometry with transparent dismissal and desktop focus states', async () => {
  const css = await readFile(resolve(desktopRoot, 'src/chat-shell.css'), 'utf8');
  for (const token of [
    '.composer-transient-sheet-layer { display: contents; }',
    '.composer-transient-sheet-dismiss {',
    'background: transparent !important;',
    '.composer-add-sheet {',
    'width: min(280px, calc(100vw - 48px));',
    'border-radius: 22px;',
    'left: 8px;',
    'box-shadow: 0 8px 24px rgb(30 45 35 / 12%);',
    '.composer-add-attachment-row { min-height: 52px; }',
    '.composer-add-style-row, .composer-add-web-row { min-height: 56px;',
    'width: 32px;',
    'height: 32px;',
    'background: var(--system-surface);',
    'width: 18px;',
    'height: 18px;',
    'width: 58px;',
    'height: 28px;',
    'width: 21px;',
    'height: 21px;',
    'translateX(30px)',
    '.composer-add-row:focus-visible',
    '.composer-add-icon-surface.is-accent',
    'color: #1e2925;',
  ]) assert.ok(css.includes(token), token);
  assert.ok(!css.includes('.composer-add-popover'));
});

test('C07 Escape and Back return from style to root before closing the shared Composer layer', async () => {
  const app = await readFile(resolve(desktopRoot, 'src/app.mjs'), 'utf8');
  for (const token of [
    'navigateComposerLayerBack = false',
    "navigateComposerLayerBack && state.composerAddOpen && state.composerAddPage === 'tone'",
    "state.composerAddPage = 'root'",
    'closeTopOverlay({ navigateComposerLayerBack: true })',
    '.composer-transient-sheet',
  ]) assert.ok(app.includes(token), token);
});

test('C07 a new ordinary conversation keeps draft preferences until the first atomic submit', async () => {
  const app = await readFile(resolve(desktopRoot, 'src/app.mjs'), 'utf8');
  for (const token of [
    'if (!state.selectedConversationId)',
    'state.conversationPreferences = { ...next, revision: 0 }',
    'const pendingPreferences = conversation ? null : { ...state.conversationPreferences }',
    'toneOverride: pendingPreferences?.toneOverride ?? null',
    "webSearchOverride: typeof pendingPreferences?.webSearchOverride === 'boolean'",
  ]) assert.ok(app.includes(token), token);
});

test('C07 temporary chat exposes attachment rows without inert ordinary-session preferences', () => {
  const html = renderChatFirstShell({
    data,
    native: true,
    temporaryConversation: { temporaryId: 'temporary-c07', messages: [], attachments: [], draftAttachmentIds: [] },
    composerAddOpen: true,
    composerAddPage: 'root',
    conversationPreferences: { revision: 0, toneOverride: 'professional', webSearchOverride: true },
    productSettings: { tone: 'friendly', webSearchEnabled: false },
    pane: 'chat',
    status: '',
    error: '',
    connection: {},
  });
  for (const action of ['open-composer-camera', 'pick-composer-image', 'pick-composer-file']) {
    assert.ok(html.includes(`data-action="${action}"`), action);
  }
  assert.ok(!html.includes('data-action="open-composer-tone-picker"'));
  assert.ok(!html.includes('data-action="toggle-conversation-web-search"'));
});
