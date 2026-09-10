import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import { MODEL_SERVICE_PREVIEW_SETTINGS } from '../src/android-settings-shell.mjs';
const read = path => fs.readFileSync(new URL(path, import.meta.url), 'utf8');
test('desktop deep model selection follows current Android order', () => {
  const mobile = read('../../app/src/main/java/com/nanzhufeng/ai/domain/ChatModelRouting.kt').split('val deep = listOf(')[1].split('val groups:')[0];
  const expected = [...mobile.matchAll(/listOf\(ModelPresetId\.(\w+)\)/g)].map(m => m[1]);
  const desktop = read('../src/chat-shell.mjs').split('const DEEP_COMPOSER_MODEL_IDS = Object.freeze([')[1].split(']);')[0];
  assert.deepEqual([...desktop.matchAll(/'([A-Z0-9_]+)'/g)].map(m => m[1]), expected);
});
test('new model UI and native request IDs match Android resolved model owner', () => {
  const native = read('../src-tauri/src/desktop_model_service_v1.rs');
  const mobile = read('../../app/src/main/java/com/nanzhufeng/ai/domain/ResolvedModel.kt');
  for (const id of ['CLAUDE_FABLE_5_1', 'GPT_6_ASTRA']) {
    const model = mobile.match(new RegExp(`ModelPresetId\\.${id} to "([^"]+)"`))[1];
    const preset = native.match(new RegExp(`id: "${id}",[\\s\\S]*?model_id: "([^"]+)"`));
    assert.equal(preset[1], model);
    assert.ok(MODEL_SERVICE_PREVIEW_SETTINGS[0].presets.some(p => p.id === id));
  }
});
