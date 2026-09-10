import test from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { resolve } from 'node:path';
import { renderChatFirstShell } from '../src/chat-shell.mjs';
import { DesktopParityPreferences, THEME_COLORS } from '../src/desktop-parity-preferences.mjs';

const data = {
  summary: { id: 'workspace-c11-settings' },
  exchange: { conversations: [] },
};

const renderSettings = overrides => renderChatFirstShell({
  data,
  native: false,
  pane: 'settings',
  settingsSection: 'personalization',
  status: '',
  error: '',
  connection: {},
  appearance: { mode: 'system', fontSize: 'standard', themeColor: 'orange' },
  productSettings: {
    memoryEnabled: true,
    historyLibraryEnabled: false,
    tone: 'professional',
    nickname: '南烛枫',
    occupation: '产品与开发',
    customInstructions: '先说结论。',
  },
  personalizationDraft: {
    memoryEnabled: true,
    historyLibraryEnabled: false,
    tone: 'professional',
    nickname: '南烛枫',
    occupation: '产品与开发',
    customInstructions: '先说结论。',
  },
  ...overrides,
});

test('C11 settings home keeps the current Android four groups and one appearance owner', () => {
  const html = renderSettings();
  const groups = [...html.matchAll(/<section class="android-settings-group"><h2>([^<]+)<\/h2>/g)].map(match => match[1]);
  assert.deepEqual(groups, ['对话', '外观', '数据管理', '工作区']);

  const appearance = html.slice(html.indexOf('<h2>外观</h2>'), html.indexOf('<h2>数据管理</h2>'));
  for (const value of ['外观', '系统（默认）', '字体大小', '标准', '主题色', '橙色']) assert.ok(appearance.includes(value), value);
  assert.ok(appearance.indexOf('外观') < appearance.indexOf('字体大小'));
  assert.ok(appearance.indexOf('字体大小') < appearance.indexOf('主题色'));
});

test('C11 personalization renders the implemented owner without engineering blockers in Browser QA', () => {
  const html = renderSettings();
  for (const forbidden of [
    'Desktop 普通发送尚未接入个性化上下文',
    'Desktop SQLite',
    '当前本机对话只保存文本',
  ]) assert.ok(!html.includes(forbidden), forbidden);

  for (const control of [
    /data-key="memoryEnabled"[^>]*role="switch"/,
    /data-key="historyLibraryEnabled"[^>]*role="switch"/,
    /id="personalization-nickname"[^>]*>/,
    /id="personalization-occupation"[^>]*>/,
    /id="personalization-instructions"[^>]*>/,
  ]) {
    const markup = html.match(control)?.[0] || '';
    assert.ok(markup, String(control));
    assert.ok(!markup.includes('disabled'), markup);
  }
  assert.match(html, /id="personalization-instructions"[^>]*maxlength="8000"/);
  assert.ok(html.includes('5 / 8000 字'));
});

test('C11 every settings switch uses the switch state contract that the visual control reads', async () => {
  const shell = await readFile(resolve(import.meta.dirname, '../src/android-settings-shell.mjs'), 'utf8');
  const css = await readFile(resolve(import.meta.dirname, '../src/chat-shell.css'), 'utf8');

  for (const action of ['toggle-product-setting', 'toggle-periodic-account-sync', 'toggle-model-service-enabled']) {
    const start = shell.indexOf(`data-action="${action}"`);
    assert.ok(start >= 0, action);
    assert.match(shell.slice(Math.max(0, start - 120), start + 180), /type="button"[\s\S]*role="switch"[\s\S]*aria-checked=/);
  }
  assert.doesNotMatch(shell, /aria-pressed="\$\{Boolean\((?:account\.periodicEnabled|draft\.enabled)\)\}" role="switch"/);
  assert.match(css, /\.android-settings-switch\[aria-checked="true"\]/);
  assert.doesNotMatch(css, /\.android-settings-switch\[aria-pressed="true"\]/);
});

test('C11 theme picker consumes literal preview colors from the shared theme catalog', async () => {
  const html = renderSettings({ settingsPicker: 'themeColor' });
  const css = await readFile(resolve(import.meta.dirname, '../src/chat-shell.css'), 'utf8');

  for (const color of THEME_COLORS) {
    assert.match(html, new RegExp(`data-value="${color.id}"[^>]*>[\\s\\S]*?android-settings-color-preview-${color.id}`));
    assert.match(css, new RegExp(`\\.android-settings-color-preview-${color.id} \\{ background-color: ${color.accent} !important; \\}`));
  }
  assert.match(css, /\.android-settings-color-preview \{[^}]*background-image: none !important;/s);
});

test('C11 appearance preferences round-trip through the single browser persistence seam', () => {
  const values = new Map();
  const storage = {
    getItem: key => values.get(key) ?? null,
    setItem: (key, value) => values.set(key, value),
    removeItem: key => values.delete(key),
  };
  const preferences = new DesktopParityPreferences(storage);
  preferences.writeAppearance({ mode: 'dark', fontSize: 'large', themeColor: 'purple' });
  assert.deepEqual(preferences.readAppearance(), { mode: 'dark', fontSize: 'large', themeColor: 'purple' });
});

test('C11 appearance changes do not expose persistence implementation as a success notice', async () => {
  const source = await readFile(resolve(import.meta.dirname, '../src/app.mjs'), 'utf8');
  const updateAppearance = source.slice(source.indexOf('async function updateAppearance'), source.indexOf('async function toggleFavoriteConversation'));
  assert.ok(!updateAppearance.includes('Desktop SQLite'));
  assert.match(updateAppearance, /await persistNativeAppSettings\(\{ \[field\]: value \}, \{\}\);\s*state\.status = '';/s);
});
