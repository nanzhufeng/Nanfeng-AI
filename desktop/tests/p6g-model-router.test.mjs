import assert from 'node:assert/strict';
import test from 'node:test';
import { routeP6G } from '../src/p6g-model-router.mjs';

const candidate = (providerFamily, modelId, knownCostMicros, available = true, capabilities = ['TEXT']) => ({ providerFamily, providerId: providerFamily.toLowerCase(), modelId, displayName: `fixture ${modelId}`, tiers: ['BALANCED'], capabilities, available, knownCostMicros, latencyRank: 1 });

test('P6-G cache and local safety gates precede catalog selection', () => {
  const catalog = [candidate('ANTHROPIC', 'anthropic.fixture', 3)];
  assert.equal(routeP6G({ tier: 'BALANCED', exactHistoricalCacheHit: true }, catalog).reason, 'EXACT_CACHE_HIT');
  assert.equal(routeP6G({ tier: 'BALANCED', localSafeRequired: true }, catalog).reason, 'LOCAL_SAFETY_GATE');
});
test('P6-G manual override never falls through to auto', () => {
  const result = routeP6G({ tier: 'BALANCED', manualModelId: 'missing' }, [candidate('ANTHROPIC', 'anthropic.fixture', 3)]);
  assert.equal(result.source, 'REJECTED'); assert.equal(result.candidate, null);
});
test('P6-G auto prefers Anthropic and unknown cost fails closed', () => {
  const catalog = [candidate('OPENAI', 'openai.fixture', 1), candidate('ANTHROPIC', 'anthropic.fixture', 9)];
  assert.equal(routeP6G({ tier: 'BALANCED' }, catalog).candidate.modelId, 'anthropic.fixture');
  assert.equal(routeP6G({ tier: 'BALANCED' }, [candidate('ANTHROPIC', 'unknown.fixture', null)]).reason, 'UNKNOWN_COST_REQUIRES_CONFIRMATION');
});
