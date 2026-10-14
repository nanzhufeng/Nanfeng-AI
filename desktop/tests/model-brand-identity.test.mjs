import test from 'node:test';
import assert from 'node:assert/strict';
import { composerModelBrand } from '../src/model-settings-browser.mjs';

test('a known RelayCore preset retains its model vendor when optional provider metadata is absent', () => {
  assert.equal(composerModelBrand('RELAYCORE_OPUS_5_5'), 'claude');
  assert.equal(composerModelBrand('RELAYCORE_GPT_6_1_SOL'), 'gpt');
  assert.equal(composerModelBrand('RELAYCORE_GEMINI_3_1_PRO'), 'gemini');
});

test('unknown and local test sources never advertise a real transport platform', () => {
  assert.equal(composerModelBrand('unknown', 'MOCK'), null);
  assert.equal(composerModelBrand('unknown', 'unrecognized'), null);
  assert.equal(composerModelBrand(null, null), null);
  assert.equal(composerModelBrand('unrecognized', 'OPENROUTER'), 'openrouter');
});
