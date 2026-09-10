import assert from 'node:assert/strict';
import { mkdtemp, readFile, rm, writeFile } from 'node:fs/promises';
import { existsSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join, resolve } from 'node:path';
import { pathToFileURL } from 'node:url';
import { spawnSync } from 'node:child_process';
import { THEME_COLORS } from '../src/desktop-parity-preferences.mjs';
import { renderAndroidSettingsShell } from '../src/android-settings-shell.mjs';

const chrome = '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome';
assert.ok(existsSync(chrome), '需要本机 Google Chrome 执行无窗口 computed-style 门禁');
const root = resolve(import.meta.dirname, '..');
const css = await readFile(resolve(root, 'src/chat-shell.css'), 'utf8');
const rendered = renderAndroidSettingsShell({ page: 'appearance', picker: 'themeColor' });
const temporaryRoot = await mkdtemp(join(tmpdir(), 'nanfeng-ai-theme-computed-style.'));
try {
  const htmlPath = join(temporaryRoot, 'index.html');
  await writeFile(htmlPath, `<!doctype html><meta charset="utf-8"><style>${css}</style><body>${rendered}<script>
    const nodes = [...document.querySelectorAll('[data-picker="themeColor"][data-value] .android-settings-color-preview')];
    const values = nodes.map(node => ({ color: getComputedStyle(node).backgroundColor, image: getComputedStyle(node).backgroundImage }));
    document.body.dataset.computedThemeColors = btoa(JSON.stringify(values));
  </script></body>`);
  const result = spawnSync(chrome, [
    '--headless=new', '--disable-gpu', '--no-first-run', '--no-default-browser-check', '--virtual-time-budget=1000',
    '--disable-background-networking', '--disable-sync', '--metrics-recording-only',
    `--user-data-dir=${join(temporaryRoot, 'profile')}`, '--dump-dom', pathToFileURL(htmlPath).href,
  ], { encoding: 'utf8', maxBuffer: 8 * 1024 * 1024, timeout: 4_000, killSignal: 'SIGKILL' });
  const encoded = result.stdout.match(/data-computed-theme-colors="([^"]+)"/)?.[1];
  assert.ok(encoded, result.stderr || '未读到主题色 computed-style 回执');
  const actual = JSON.parse(Buffer.from(encoded, 'base64').toString('utf8'));
  const expected = THEME_COLORS.map(item => {
    const value = item.accent.slice(1);
    return `rgb(${Number.parseInt(value.slice(0, 2), 16)}, ${Number.parseInt(value.slice(2, 4), 16)}, ${Number.parseInt(value.slice(4, 6), 16)})`;
  });
  assert.equal(actual.length, THEME_COLORS.length);
  assert.deepEqual(actual.map(item => item.color), expected);
  assert.ok(actual.every(item => item.image === 'none'), '主题色预览不能被背景图覆盖');
  console.log(`theme computed styles passed: ${actual.map(item => item.color).join(' | ')}`);
} finally {
  await rm(temporaryRoot, { recursive: true, force: true });
}
