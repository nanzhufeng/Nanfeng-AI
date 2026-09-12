import test from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { resolve } from 'node:path';

const desktopRoot = resolve(import.meta.dirname, '..');
const repoRoot = resolve(desktopRoot, '..');
const [icons, settings, css, viewportShell, buildSource, settingsContract] = await Promise.all([
  readFile(resolve(desktopRoot, 'src/icon-source.mjs'), 'utf8'),
  readFile(resolve(desktopRoot, 'src/android-settings-shell.mjs'), 'utf8'),
  readFile(resolve(desktopRoot, 'src/chat-shell.css'), 'utf8'),
  readFile(resolve(desktopRoot, 'src/viewport-shell.css'), 'utf8'),
  readFile(resolve(desktopRoot, 'scripts/build.mjs'), 'utf8'),
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
    '.android-settings-main { display: block; height: 100%; min-height: 0; overflow: hidden; overscroll-behavior: none; background: var(--settings-page-background); }',
    '.android-settings-card { overflow: hidden; margin: 0; border: 0; border-radius: 20px; background: #fff;',
    ':root { --settings-card-shadow: 0 2px 8px rgb(0 0 0 / 4%); }',
    '.android-settings-row.selected { background: var(--accent-orange-soft) !important;',
  ]) assert.ok(css.includes(token), token);
});

test('settings scrolling stays inside a surface-owned viewport instead of exposing the WebView edge', () => {
  assert.ok(buildSource.includes("'viewport-shell.css'"));
  assert.match(viewportShell, /html,\s*body,\s*#app \{[\s\S]*height: 100%;[\s\S]*overflow: hidden;/);
  assert.match(viewportShell, /body \{[\s\S]*overscroll-behavior: none;[\s\S]*background: var\(--page-background, #f7f7f7\);/);
  assert.match(css, /\.android-settings-layout \{[^}]*height: 100%;[^}]*min-height: 0;/s);
  assert.match(css, /\.android-settings-scroll \{[^}]*overflow: auto;[^}]*overscroll-behavior: none;[^}]*background: var\(--settings-page-background\);/s);
});

test('current settings contract exposes default plus five explicit styles', () => {
  assert.match(settingsContract, /固定展示六个选项/);
  assert.match(settingsContract, /“默认”排在首位/);
  assert.doesNotMatch(settingsContract, /只保留为无法识别旧持久值的内部中性回退/);
});
