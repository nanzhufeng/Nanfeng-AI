import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import { MODEL_SERVICE_PREVIEW_SETTINGS } from '../src/android-settings-shell.mjs';
const read = path => fs.readFileSync(new URL(path, import.meta.url), 'utf8');

test('Windows current Composer order follows the fixed latest Mac catalog without changing local Android', () => {
  const expected = ['CLAUDE_OPUS_5_5', 'GPT_6_1_SOL', 'DEEPSEEK_V4_PRO', 'MUSE_WONDER', 'GEMINI_4_ARGON', 'GLM_5_3', 'QWEN_3_8_MAX'];
  const source = read('../src/chat-shell.mjs').split('const DEEP_COMPOSER_MODEL_IDS = Object.freeze([')[1].split(']);')[0];
  assert.deepEqual([...source.matchAll(/'([A-Z0-9_]+)'/g)].map(m => m[1]), expected);
});

test('retired choices retain their native descriptors while new settings use current identities', () => {
  const native = read('../src-tauri/src/desktop_model_service_v1.rs');
  for (const id of ['CLAUDE_FABLE_5_1', 'GPT_6_ASTRA']) {
    assert.ok(native.includes(`id: "${id}"`));
    assert.ok(!MODEL_SERVICE_PREVIEW_SETTINGS[0].presets.some(p => p.id === id));
  }
  for (const [id, wire] of [['CLAUDE_OPUS_5_5', 'anthropic/claude-opus-5.5'], ['GPT_6_1_SOL', 'openai/gpt-6.1-sol'], ['MUSE_WONDER', 'meta/muse-wonder']]) {
    assert.equal(native.match(new RegExp(`id: "${id}",[\\s\\S]*?model_id: "([^\"]+)"`))[1], wire);
    assert.ok(MODEL_SERVICE_PREVIEW_SETTINGS[0].presets.some(p => p.id === id));
  }
});
