import test from 'node:test';
import assert from 'node:assert/strict';
import { THEME_COLORS } from '../src/desktop-parity-preferences.mjs';
import { desktopThemeProjection, applyDesktopThemeToRoot } from '../src/desktop-theme-owner.mjs';

const luminance = hex => {
  const channels = hex.slice(1).match(/../g).map(value => parseInt(value, 16) / 255)
    .map(value => value <= .04045 ? value / 12.92 : ((value + .055) / 1.055) ** 2.4);
  return channels[0] * .2126 + channels[1] * .7152 + channels[2] * .0722;
};
const contrast = (a, b) => (Math.max(luminance(a), luminance(b)) + .05)
  / (Math.min(luminance(a), luminance(b)) + .05);

test('every control theme remains readable in selected, hover, pressed and keyboard-focus states', () => {
  for (const mode of ['light', 'dark']) for (const theme of THEME_COLORS) {
    const projection = desktopThemeProjection({ mode, themeColor: theme.id });
    assert.ok(projection.controls, `${mode}/${theme.id} control palette`);
    for (const background of Object.values(projection.controls)) {
      assert.match(background, /^#([a-f\d]{2})\1\1$/i, 'control surfaces stay neutral');
      assert.ok(contrast(projection.controlAccentText, background) >= 4.5, `${mode}/${theme.id} ${background}`);
      assert.ok(contrast(projection.surfaces.body, background) >= 4.5, `ordinary text ${mode}`);
    }
    assert.notEqual(projection.controls.focus, projection.controls.selected, 'focus distinguishes selected controls');
    assert.equal(projection.theme.accent, theme.accent, 'brand swatches retain the actual theme color');
  }
});

test('the actual root owner publishes readable control tokens for system appearance and font sizes', () => {
  for (const dark of [false, true]) for (const fontSize of ['small', 'standard', 'large']) {
    const values = new Map();
    const root = { dataset: {}, style: { setProperty: (key, value) => values.set(key, value) } };
    const projection = applyDesktopThemeToRoot(root, { mode: 'system', themeColor: 'orange', fontSize }, dark);
    assert.ok(projection.controls);
    assert.equal(values.get('--control-accent-text'), projection.controlAccentText);
    for (const [state, color] of Object.entries(projection.controls)) assert.equal(values.get(`--control-${state}-surface`), color);
    assert.equal(values.get('--app-font-scale'), String(projection.fontScale));
  }
});
