import test from 'node:test';
import assert from 'node:assert/strict';
import { MODEL_SERVICE_PREVIEW_SETTINGS } from '../src/android-settings-shell.mjs';
import { desktopComposerModelCandidates, compactModelName } from '../src/chat-shell.mjs';

test('October models replace old choices in both composer groups', () => {
  const models = desktopComposerModelCandidates(MODEL_SERVICE_PREVIEW_SETTINGS, [], true);
  const daily = models.filter(m => m.tiers.includes('BALANCED')).map(m => m.modelId);
  const deep = models.filter(m => m.tiers.includes('DEEP')).map(m => m.modelId);
  for (const id of ['CLAUDE_SONNET_5_5', 'CLAUDE_HAIKU_5_5', 'GPT_6_LUNA', 'GEMINI_3_8_FLASH']) assert.ok(daily.includes(id), id);
  assert.ok(!daily.includes('GPT_5_6_TERRA'));
  assert.equal(deep.indexOf('GPT_6_1_SOL') + 1, deep.indexOf('DEEPSEEK_V4_PRO'));
  assert.ok(deep.includes('CLAUDE_OPUS_5_5'));
  assert.ok(!deep.includes('GPT_5_6_SOL'));
  assert.equal(compactModelName('Claude Opus 5'), 'Opus 5');
  assert.equal(compactModelName('Claude Opus 5.5'), 'Opus 5.5');
});
