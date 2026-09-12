import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { desktopComposerModelCandidates, deepSeekPricingLabelAt, millisecondsUntilDeepSeekPricingTransition } from '../src/chat-shell.mjs';

test('Flash current catalog identity and image boundary agree across platforms', () => {
  const profiles = JSON.parse(readFileSync(new URL('../../app/src/main/assets/model_profiles.json', import.meta.url))).profiles;
  const flash = profiles.find(p => p.presetId === 'DEEPSEEK_V4_FLASH');
  assert.equal(flash.modelId, 'deepseek-flash');
  assert.equal(flash.displayName, 'DeepSeek V4.1 Flash');
  assert.equal(flash.capabilities.image, true);
  for (const modality of ['pdf', 'video', 'audio']) assert.equal(flash.capabilities[modality], false);
  const rust = readFileSync(new URL('../src-tauri/src/desktop_model_service_v1.rs', import.meta.url), 'utf8');
  const descriptor = rust.split('id: "DEEPSEEK_V4_FLASH",')[1].split('},')[0];
  assert.ok(descriptor.includes(`model_id: "${flash.modelId}"`));
  assert.ok(descriptor.includes(`display_name: "${flash.displayName}"`));
});

test('DeepSeek pricing uses UTC weekdays and sleeps across the weekend', () => {
  assert.equal(deepSeekPricingLabelAt('2026-09-10T01:00:00Z'), '当前高峰');
  assert.equal(deepSeekPricingLabelAt('2026-09-12T01:00:00Z'), '当前低谷');
  assert.equal(deepSeekPricingLabelAt('2026-09-13T09:00:00Z'), '当前低谷');
  assert.equal(millisecondsUntilDeepSeekPricingTransition('2026-09-11T10:00:00Z'), 63 * 3600000);
});

test('cached composer metadata upgrades its display and stable choice without rewriting history', () => {
  const legacy = {modelId: 'deepseek-v4-flash', displayName: 'DeepSeek V4 Flash', providerId: 'DEEPSEEK'};
  const [current] = desktopComposerModelCandidates([], [legacy]);
  assert.equal(current.modelId, 'DEEPSEEK_V4_FLASH');
  assert.equal(current.displayName, 'DeepSeek V4.1 Flash');
  assert.equal(legacy.displayName, 'DeepSeek V4 Flash');
});
