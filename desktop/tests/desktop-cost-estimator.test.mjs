import test from 'node:test';
import assert from 'node:assert/strict';
import { cnyCostLabel, projectedCost, projectedMessageCost } from '../src/desktop-cost-estimator.mjs';

test('Desktop mirrors Android fallback pricing only when provider settlement is absent', () => {
  const deepSeek = projectedCost({ modelId: 'deepseek-flash', inputTokens: 1063, outputTokens: 157, cachedInputTokens: 0, occurredAtMs: Date.UTC(2026, 8, 10, 2) });
  assert.deepEqual(deepSeek, { chargeMicros: 507, currencyCode: 'USD', costSource: 'LOCAL_ESTIMATE', priceVersion: 'deepseek-v4.1-flash-peak-2026-09-10-v1' });
  assert.equal(cnyCostLabel(deepSeek, { estimatedLabel: true, maximumFractionDigits: 6 }), '¥0.003407');
  const settled = projectedCost({ modelId: 'deepseek-flash', inputTokens: 1063, outputTokens: 157, occurredAtMs: Date.UTC(2026, 8, 10, 2), chargeMicros: 9, currencyCode: 'USD' });
  assert.deepEqual(settled, { chargeMicros: 9, currencyCode: 'USD', costSource: 'PROVIDER_RESPONSE' });
});

test('Desktop chat footer projects the same persisted model, token and timestamp facts without mutating them', () => {
  const message = { actualModelId: 'deepseek-flash', createdAt: '2026-09-10T02:00:00.000Z', usage: { inputTokens: 1063, outputTokens: 157, cachedInputTokens: 0 } };
  assert.equal(projectedMessageCost(message)?.costSource, 'LOCAL_ESTIMATE');
  assert.equal(message.chargeMicros, undefined);
});

test('Desktop estimates a completed reply from explicitly marked local token estimates when provider usage is absent', () => {
  const message = { actualModelId: 'openai/gpt-5.6-terra', createdAt: '2026-09-11T15:18:22.234Z', usage: { inputTokens: null, outputTokens: null }, estimatedUsage: { inputTokens: 120, outputTokens: 36, cachedInputTokens: 0, source: 'LOCAL_TEXT_ESTIMATE' } };
  assert.equal(projectedMessageCost(message)?.costSource, 'LOCAL_ESTIMATE');
  assert.match(cnyCostLabel(projectedMessageCost(message), { maximumFractionDigits: 4, trimTrailingZeros: false }), /^¥\d+\.\d{4}$/);
});
