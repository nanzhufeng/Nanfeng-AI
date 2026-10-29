import test from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import vm from 'node:vm';
import { normalizeProductSettings, CUSTOM_INSTRUCTIONS_MAX_LENGTH } from '../src/desktop-parity-preferences.mjs';
import { renderAndroidSettingsShell } from '../src/android-settings-shell.mjs';
import { createLocalWriteBarrier } from '../src/local-write-barrier.mjs';

const source = await readFile(new URL('../src/app.mjs', import.meta.url), 'utf8');
const owner = source.slice(source.indexOf('let appSettingsSaveQueue'), source.indexOf('async function updateAppearance'));
const nativeValidText = { nickname: '字'.repeat(79) + '🚀', occupation: '𠮷'.repeat(120), interests: '🚀'.repeat(500), customInstructions: '𠮷'.repeat(CUSTOM_INSTRUCTIONS_MAX_LENGTH) };

test('valid native Unicode text survives normalization without truncating scalar values', () => {
  const normalized = normalizeProductSettings(nativeValidText);
  for (const [key, text] of Object.entries(nativeValidText)) assert.equal(normalized[key], text, key);
});

test('strict saves reject overlong text instead of silently saving its prefix', () => {
  assert.throws(() => normalizeProductSettings({ customInstructions: '字'.repeat(8001) }, { rejectInvalidText: true }), /8000/);
});

function fixture(product) {
  let disk = { appearance: { mode: 'light' }, product: normalizeProductSettings({}), revision: 1 };
  Object.assign(disk.product, product);
  const state = { productSettings: { ...disk.product }, personalizationDraft: { ...disk.product }, personalizationDirty: true };
  const writes = [];
  const context = vm.createContext({ createLocalWriteBarrier, normalizeProductSettings, normalizeAppearance: value => value, native: true, state,
    parityPreferences: { clearNativeAppSettingsMigrationSource() {} },
    invoke: async (command, payload) => {
      if (command === 'read_desktop_app_settings') return structuredClone(disk);
      assert.equal(command, 'save_desktop_app_settings');
      writes.push(payload.args); disk = { ...payload.args, revision: disk.revision + 1 }; return structuredClone(disk);
    },
  });
  vm.runInContext(owner, context);
  return { state, writes, save: patch => context.persistNativeAppSettings({}, patch), get stored() { return disk.product; } };
}

test('changing an unrelated switch preserves all valid native personalization including hidden interests', async () => {
  const f = fixture(nativeValidText); await f.save({ memoryEnabled: true });
  assert.equal(f.writes.length, 1);
  for (const [key, text] of Object.entries(nativeValidText)) assert.equal(f.stored[key], text, key);
});

test('overlong save performs no native write and keeps the entire editable draft', async () => {
  const f = fixture({ nickname: '原始昵称' });
  f.state.personalizationDraft.customInstructions = '字'.repeat(8001);
  await assert.rejects(f.save({ customInstructions: f.state.personalizationDraft.customInstructions }), /8000/);
  assert.equal(f.writes.length, 0); assert.equal(f.stored.nickname, '原始昵称');
  assert.equal(f.state.personalizationDraft.customInstructions, '字'.repeat(8001));
});

test('personalization form counts characters and does not impose shorter UTF-16 HTML limits', () => {
  const html = renderAndroidSettingsShell({ native: true, page: 'personalization', personalizationDraft: { customInstructions: '🚀🚀', nickname: '', occupation: '' } });
  assert.match(html, /2 \/ 8000 字/);
  for (const id of ['personalization-nickname', 'personalization-occupation', 'personalization-instructions']) {
    const input = html.match(new RegExp(`<[^>]+id="${id}"[^>]*>`))?.[0]; assert.ok(input); assert.doesNotMatch(input, /\bmaxlength=/);
  }
});

test('redrawing the rejected form keeps every overlong draft character visible for correction', () => {
  const text = '𠮷'.repeat(8001);
  const html = renderAndroidSettingsShell({ native: true, page: 'personalization', personalizationDraft: { customInstructions: text, nickname: '原文' } });
  assert.ok(html.includes(text));
  assert.match(html, /8001 \/ 8000 字/);
});
