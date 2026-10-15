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

 test('Gemini 4 Argon is a manual OpenRouter deep choice immediately above GLM', () => {
  const models = desktopComposerModelCandidates(MODEL_SERVICE_PREVIEW_SETTINGS, [], true);
  const deep = models.filter(m => m.tiers.includes('DEEP')).map(m => m.modelId);
  assert.equal(deep[deep.indexOf('GLM_5_3') - 1], 'GEMINI_4_ARGON');
  assert.equal(models.find(m => m.modelId === 'GEMINI_4_ARGON').providerId, 'OPENROUTER');
});

 test('Argon cost projection includes its cache tariff and keeps provider money authoritative', async () => {
  const { projectedCost } = await import('../src/desktop-cost-estimator.mjs');
  const facts = { modelId:'google/gemini-4-argon', inputTokens:1000, outputTokens:100, cachedInputTokens:500, occurredAtMs:Date.parse('2026-10-15T00:00:00Z') };
  assert.equal(projectedCost(facts).chargeMicros,2050);
  assert.equal(projectedCost(facts).priceVersion,'openrouter-gemini-4-argon-2026-10-15-v1');
  assert.equal(projectedCost({...facts,chargeMicros:3000,currencyCode:'USD'}).chargeMicros,3000);
});
