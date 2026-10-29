import assert from 'node:assert/strict';
import test from 'node:test';
import { createServer } from 'node:http';
import { spawn } from 'node:child_process';
import { existsSync } from 'node:fs';
import { mkdtemp, readFile, writeFile } from 'node:fs/promises';
import { join, resolve } from 'node:path';
import { tmpdir } from 'node:os';
import { renderAndroidSettingsShell } from '../src/android-settings-shell.mjs';
import { FONT_SIZES, THEME_COLORS } from '../src/desktop-parity-preferences.mjs';

const rgb = hex => `rgb(${[1, 3, 5].map(i => parseInt(hex.slice(i, i + 2), 16)).join(', ')})`;

test('Windows settings render every color, font and resolved appearance with opaque picker surfaces', {
  timeout: 30_000,
  skip: process.platform !== 'win32' ? 'Windows-specific rendered gate; Mac appearance requires its own native evidence' : false,
}, async () => {
  const chrome = [process.env.PROGRAMFILES, process.env['PROGRAMFILES(X86)'], process.env.LOCALAPPDATA]
    .filter(Boolean).map(base => join(base, 'Google/Chrome/Application/chrome.exe')).find(existsSync);
  assert.ok(chrome, 'This Windows rendered gate requires the installed Chrome executable');
  const root = resolve(import.meta.dirname, '..');
  const css = await readFile(join(root, 'src/chat-shell.css'), 'utf8');
  const markup = renderAndroidSettingsShell({ page: 'appearance', picker: 'themeColor', runtimeInfo: { platform: 'windows' } });
  const cases = THEME_COLORS.flatMap(theme => FONT_SIZES.flatMap(font => [
    { mode: 'light', prefersDark: false, dark: false },
    { mode: 'dark', prefersDark: false, dark: true },
    { mode: 'system', prefersDark: false, dark: false },
    { mode: 'system', prefersDark: true, dark: true },
  ].map(mode => ({ ...mode, themeColor: theme.id, fontSize: font.id, scale: font.scale }))));
  const html = `<!doctype html><meta charset="utf-8"><style>${css}</style>
    <body><div class="app-shell chat-first settings-mode">${markup}</div><script type="module">
    import { applyDesktopThemeToRoot } from '/src/desktop-theme-owner.mjs';
    try {
      const rows = ${JSON.stringify(cases)}.map(item => {
        applyDesktopThemeToRoot(document.documentElement, item, item.prefersDark);
        const panel = document.querySelector('.android-settings-picker');
        const style = getComputedStyle(panel);
        const label = getComputedStyle(panel.querySelector('strong'));
        return { ...item, resolvedMode: document.documentElement.dataset.appearanceMode,
          background: style.backgroundColor, opacity: style.opacity, font: label.fontSize,
          previews: [...panel.querySelectorAll('.android-settings-color-preview')].map(node => ({
            color: getComputedStyle(node).backgroundColor, image: getComputedStyle(node).backgroundImage })),
          pausedActions: document.querySelectorAll('[data-action="show-work"], [data-action="show-projects"], [data-action="show-knowledge"], [data-page="workspace"]').length };
      });
      document.body.dataset.renderedReceipt = btoa(JSON.stringify({ rows }));
    } catch (error) { document.body.dataset.renderedReceipt = btoa(JSON.stringify({ error: String(error) })); }
    </script></body>`;
  const modules = new Map(await Promise.all(['desktop-theme-owner.mjs', 'desktop-parity-preferences.mjs', 'conversation-message-order.mjs']
    .map(async name => [`/src/${name}`, await readFile(join(root, 'src', name), 'utf8')])));
  const server = createServer((request, response) => {
    const body = request.url === '/' ? html : modules.get(request.url);
    response.writeHead(body === undefined ? 404 : 200, { 'Content-Type': request.url === '/' ? 'text/html; charset=utf-8' : 'text/javascript; charset=utf-8' });
    response.end(body ?? 'Not found');
  });
  const profile = await mkdtemp(join(tmpdir(), 'nfai-rendered-appearance.'));
  let child;
  try {
    await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
    const url = `http://127.0.0.1:${server.address().port}/`;
    const output = await new Promise((resolve, reject) => {
      child = spawn(chrome, ['--headless=new', '--disable-gpu', '--no-first-run', '--no-default-browser-check',
        '--disable-background-networking', '--disable-sync', '--metrics-recording-only',
        `--user-data-dir=${profile}`, '--virtual-time-budget=3000', '--dump-dom', url], { windowsHide: true, signal: AbortSignal.timeout(20_000) });
      let stdout = '', stderr = '';
      child.stdout.setEncoding('utf8'); child.stderr.setEncoding('utf8');
      child.stdout.on('data', data => { stdout += data; if (stdout.length > 2_000_000) { child.kill(); reject(new Error('Rendered receipt exceeds output budget')); } });
      child.stderr.on('data', data => { stderr = (stderr + data).slice(-1500); });
      child.on('error', reject);
      child.on('exit', code => code === 0 ? resolve(stdout) : reject(new Error(`Isolated Chrome exit ${code}: ${stderr}`)));
    });
    await writeFile(join(profile, 'rendered-dom.html'), output);
    const encoded = output.match(/data-rendered-receipt="([^"]+)"/)?.[1];
    assert.ok(encoded, 'No rendered receipt; refusing to treat static markup as a browser pass');
    const receipt = JSON.parse(Buffer.from(encoded, 'base64').toString('utf8'));
    assert.equal(receipt.error, undefined);
    assert.equal(receipt.rows.length, 84);
    for (const row of receipt.rows) {
      const identity = `${row.themeColor}/${row.fontSize}/${row.mode}/${row.prefersDark}`;
      assert.equal(row.resolvedMode, row.dark ? 'dark' : 'light', identity);
      assert.equal(row.background, row.dark ? 'rgb(72, 72, 72)' : 'rgb(255, 255, 255)', identity);
      assert.equal(row.opacity, '1', identity);
      assert.ok(Math.abs(parseFloat(row.font) - 14 * row.scale) < 0.02, `${identity}: rendered font ${row.font}`);
      assert.deepEqual(row.previews.map(item => item.color), THEME_COLORS.map(item => rgb(item.accent)), identity);
      assert.ok(row.previews.every(item => item.image === 'none'), identity);
      assert.equal(row.pausedActions, 0, identity);
    }
    console.log(`Rendered Windows appearance combinations: ${receipt.rows.length}; isolated profile: ${profile}`);
  } finally {
    if (child && child.exitCode === null) child.kill();
    await new Promise(resolve => server.close(resolve));
  }
});
