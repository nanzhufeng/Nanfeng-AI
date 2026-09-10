import test from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { resolve } from 'node:path';

const desktopRoot = resolve(import.meta.dirname, '..');
const repoRoot = resolve(desktopRoot, '..');
const [icons, settings, css, settingsContract] = await Promise.all([
  readFile(resolve(desktopRoot, 'src/icon-source.mjs'), 'utf8'),
  readFile(resolve(desktopRoot, 'src/android-settings-shell.mjs'), 'utf8'),
  readFile(resolve(desktopRoot, 'src/chat-shell.css'), 'utf8'),
  readFile(resolve(repoRoot, 'docs/ANDROID_SETTINGS_UI_CURRENT_CONTRACT.md'), 'utf8'),
]);

test('settings rows use the current Android semantic icons from exact Android Rounded paths', () => {
  for (const token of ['appearance:', 'importExport:', 'tune:', 'globe:']) assert.ok(icons.includes(token), token);
  for (const mapping of [
    "glyph: settingsIcons.appearance, title: '外观'",
    "glyph: settingsIcons.importExport, title: '导入与导出'",
    "glyph: settingsIcons.tune, title: '开发与诊断'",
    "icon(icons.globe, '')}<strong>实时网页搜索</strong>",
  ]) assert.ok(settings.includes(mapping), mapping);
});

test('settings keep theme-owned grouped cards on the settings canvas with whole-row selection', () => {
  for (const token of [
    '.android-settings-main { display: block; height: 100vh; overflow: auto; background: var(--settings-page-background); }',
    '.android-settings-card { overflow: hidden; margin: 0; border: 0; border-radius: 20px; background: var(--foreground-surface);',
    '.android-settings-row.selected { background: var(--accent-orange-soft) !important;',
  ]) assert.ok(css.includes(token), token);
});

test('current settings contract exposes default plus five explicit styles', () => {
  assert.match(settingsContract, /固定展示六个选项/);
  assert.match(settingsContract, /“默认”排在首位/);
  assert.doesNotMatch(settingsContract, /只保留为无法识别旧持久值的内部中性回退/);
});
