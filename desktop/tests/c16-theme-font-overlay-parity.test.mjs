import test from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { resolve } from 'node:path';
import { appearanceProjection } from '../src/desktop-parity-preferences.mjs';
import {
  C16_ANDROID_SURFACES,
  C16_APPEARANCE_CASES,
  C16_FONT_CASES,
  C16_LAYER_CASES,
  C16_MATRIX_SIZE,
  parseC16Preview,
} from '../src/c16-theme-matrix-fixture.mjs';

const source = path => readFile(resolve(import.meta.dirname, path), 'utf8');

test('C16 freezes the Android-owned appearance, font, and representative overlay matrix', () => {
  assert.equal(C16_MATRIX_SIZE, 84);
  assert.deepEqual([...new Set(C16_APPEARANCE_CASES.map(item => item.mode))], ['system', 'light', 'dark']);
  assert.deepEqual(C16_FONT_CASES.map(item => [item.id, item.scale]), [['small', 0.8], ['standard', 1], ['large', 1.24]]);
  assert.deepEqual([...new Set(C16_LAYER_CASES.map(item => item.group))], ['model', 'add', 'style', 'search', 'settings']);
  assert.deepEqual(parseC16Preview('dark__large__search-history')?.layer, C16_LAYER_CASES[5]);
  assert.equal(parseC16Preview('dark__large__unknown'), null);
});

test('C16 system, explicit light, and explicit dark resolve independently of the host theme', () => {
  for (const item of C16_APPEARANCE_CASES) {
    const projection = appearanceProjection({ mode: item.mode, fontSize: 'standard', themeColor: 'orange' }, item.prefersDark);
    assert.equal(projection.dark ? 'dark' : 'light', item.resolved, item.id);
  }
});

test('C16 production theme owner emits the current Android surface and typography tokens', async () => {
  const owner = await import('../src/desktop-theme-owner.mjs');
  for (const appearance of C16_APPEARANCE_CASES) {
    for (const font of C16_FONT_CASES) {
      const projection = owner.desktopThemeProjection({ mode: appearance.mode, fontSize: font.id, themeColor: 'orange' }, appearance.prefersDark);
      assert.equal(projection.resolvedMode, appearance.resolved, appearance.id);
      assert.equal(projection.fontScale, font.scale, font.id);
      assert.deepEqual(projection.surfaces, C16_ANDROID_SURFACES[appearance.resolved], appearance.id);
    }
  }
});

test('C16 one root application seam owns the DOM mode, font scale, accent, and surfaces', async () => {
  const app = await source('../src/app.mjs');
  const apply = app.slice(app.indexOf('function applyAppearance()'), app.indexOf('function reloadFavoriteConversationIds'));
  assert.match(app, /import \{ applyDesktopThemeToRoot \} from '.\/desktop-theme-owner\.mjs';/);
  assert.match(apply, /const prefersDark = c16PreviewState\?\.appearance\.mode === 'system'/);
  assert.match(apply, /applyDesktopThemeToRoot\(document\.documentElement, state\.appearance, prefersDark\)/);
  assert.ok(!apply.includes("root.style.setProperty"), apply);
});

test('C16 model, add, style, search, and settings layers consume semantic theme variables', async () => {
  const css = await source('../src/chat-shell.css');
  for (const token of [
    '--page-background:', '--settings-page-background:', '--foreground-surface:',
    '--search-page-surface:', '--search-control-surface:', '--body-text:', '--secondary-text:',
  ]) assert.ok(css.includes(token), token);

  assert.match(css, /\.composer-add-sheet \{[^}]*background: var\(--foreground-surface\)/s);
  assert.match(css, /\.composer-model-sheet \{[^}]*background: var\(--foreground-surface\)/s);
  assert.match(css, /\.composer-model-sheet-card \{[^}]*background: var\(--assistant-surface\) !important/s);
  assert.match(css, /\.desktop-search-page \{[^}]*--search-canvas: var\(--search-page-surface\)/s);
  assert.match(css, /\.android-settings-main \{[^}]*background: var\(--settings-page-background\)/s);
  assert.match(css, /\.android-settings-picker \{[^}]*background: var\(--foreground-surface\)/s);
  assert.match(css, /:root\[data-appearance-mode="dark"\] \.composer-model-sheet-card \{ background: var\(--assistant-surface\) !important/);
  assert.match(css, /:root\[data-appearance-mode="dark"\] \.android-settings-picker \{ background: var\(--foreground-surface\) !important/);
  assert.ok(!css.includes('@media (prefers-color-scheme: dark)'), 'explicit app appearance must own search dark mode');
});

test('C16 fixture is opt-in, Browser-only, and covers all representative layer states', async () => {
  const app = await source('../src/app.mjs');
  assert.match(app, /let c16ThemePreview = native \? '' : new URLSearchParams\(window\.location\.search\)\.get\('c16ThemePreview'\)/);
  assert.match(app, /parseC16Preview\(c16ThemePreview\)/);
  for (const layer of C16_LAYER_CASES) assert.ok(app.includes(`'${layer.id}'`), layer.id);
});

test('C16 native matrix is available only through the fail-closed diagnostic acceptance gate', async () => {
  const app = await source('../src/app.mjs');
  const rust = await source('../src-tauri/src/lib.rs');
  const permissions = await source('../src-tauri/permissions/default.toml');
  const capability = await source('../src-tauri/capabilities/default.json');
  assert.match(app, /read_desktop_c16_visual_acceptance_state/);
  assert.match(app, /applyC16Preview\('Tauri 原生'\)/);
  assert.match(rust, /NANFENG_AI_DESKTOP_C16_VISUAL_ACCEPTANCE/);
  assert.match(rust, /NANFENG_AI_DESKTOP_C16_VISUAL_STATE/);
  assert.match(rust, /\/tmp\/nanfeng-ai-desktop-c16-visual-acceptance\./);
  assert.match(rust, /fn read_desktop_c16_visual_acceptance_state/);
  assert.match(rust, /validate_c16_visual_acceptance_state/);
  assert.match(permissions, /commands\.allow = \["read_desktop_c16_visual_acceptance_state"\]/);
  assert.match(capability, /"allow-read-desktop-c16-visual-acceptance-state"/);
});

test('C16 static build ships the shared theme owner required by the native bundle', async () => {
  const build = await source('../scripts/build.mjs');
  assert.match(build, /'desktop-theme-owner\.mjs'/);
  assert.match(build, /'c16-theme-matrix-fixture\.mjs'/);
});
