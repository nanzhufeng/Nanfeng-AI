import test from 'node:test';
import assert from 'node:assert/strict';
import { MODEL_SERVICE_PREVIEW_SETTINGS } from '../src/android-settings-shell.mjs';
import { desktopComposerModelCandidates, transcriptMetadata, compactModelName } from '../src/chat-shell.mjs';
import { projectedCost } from '../src/desktop-cost-estimator.mjs';

test('Mercury is a daily OpenRouter choice immediately after Luna', () => {
  const models = desktopComposerModelCandidates(MODEL_SERVICE_PREVIEW_SETTINGS, [], true);
  const daily = models.filter(m => m.tiers.includes('BALANCED'));
  const mercury = daily[daily.findIndex(m => m.modelId === 'GPT_6_LUNA') + 1];
  assert.equal(mercury.modelId, 'MERCURY_3');
  assert.equal(mercury.providerId, 'OPENROUTER');
  assert.equal(mercury.displayName, 'Mercury 3');
});

test('Mercury token projection includes cache rates and preserves settlement', () => {
  const facts = {modelId:'inception/mercury-3',inputTokens:1000,outputTokens:100,cachedInputTokens:500,occurredAtMs:Date.parse('2026-10-20T00:00:00Z')};
  assert.equal(projectedCost(facts).chargeMicros, 90);
  assert.equal(projectedCost(facts).priceVersion, 'openrouter-mercury-3-2026-10-20-v1');
  assert.equal(projectedCost({...facts,chargeMicros:123,currencyCode:'USD'}).chargeMicros,123);
});

test('Mercury historical route-only records show a human name without changing frozen labels', () => {
  const message={modelSnapshot:{providerId:'OPENROUTER',modelId:'inception/mercury-3'},source:'PROVIDER'};
  assert.equal(transcriptMetadata(message).model,'Mercury 3');
  assert.equal(compactModelName('inception/mercury-3'),'Mercury 3');
  assert.equal(message.modelSnapshot.modelId,'inception/mercury-3');
  assert.equal(transcriptMetadata({...message,modelSnapshot:{...message.modelSnapshot,displayName:'Frozen historical label'}}).model,'Frozen historical label');
});
