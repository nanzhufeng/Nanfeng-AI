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

test('Partial reported usage survives cancellation without borrowing complete local estimates', () => {
  for (const usage of [{inputTokens:6,cachedInputTokens:1,outputTokens:null},{inputTokens:0,outputTokens:null},{inputTokens:null,outputTokens:2}]) {
    const message={delivery:'CANCELLED',actualModelId:'openai/gpt-5.6-terra',createdAt:'2026-09-11T15:18:22.234Z',usage,
      estimatedUsage:{inputTokens:120,outputTokens:36,cachedInputTokens:0,source:'LOCAL_TEXT_ESTIMATE'}};
    const before=structuredClone(message);assert.equal(projectedMessageCost(message),null);assert.deepEqual(message,before);
    message.chargeMicros=9;message.currencyCode='USD';
    assert.deepEqual(projectedMessageCost(message),{chargeMicros:9,currencyCode:'USD',costSource:'PROVIDER_RESPONSE'});
  }
});

for (const totalTokens of [0,9]) test(`total-only reported usage ${totalTokens} cannot borrow local estimate in a persisted footer`, async () => {
 const { messageFooterCostLabel }=await import('../src/desktop-cost-estimator.mjs');
 const { renderChatFirstShell }=await import('../src/chat-shell.mjs');
 for(const delivery of ['COMPLETE','FAILED','CANCELLED','UNKNOWN']){
  const message={id:'answer',role:'assistant',parentId:'question',delivery,createdAt:'2026-10-19T02:00:00.000Z',
   modelSnapshot:{providerId:'QWEN',modelId:'qwen3.7-plus',displayName:'Qwen3.7 Plus'},usage:{totalTokens},
   estimatedUsage:{inputTokens:1000,outputTokens:100,totalTokens:1100,cachedInputTokens:0,source:'LOCAL_TEXT_ESTIMATE'},blocks:[{kind:'TEXT',text:'Synthetic sparse report',ordinal:0}]};
  const before=structuredClone(message);assert.equal(projectedMessageCost(message),null);assert.equal(messageFooterCostLabel(message),null);
  const html=renderChatFirstShell({native:true,appearance:{},productSettings:{},data:{summary:{id:'w'},exchange:{conversations:[{id:'c',title:'Synthetic sparse report',currentLeafId:'answer',messages:[{id:'question',role:'user',blocks:[{kind:'TEXT',text:'Synthetic question'}]},message]}]}},selectedConversationId:'c'});
  assert.match(html,/金额未知/);assert.doesNotMatch(html,/¥[0-9]/);assert.deepEqual(message,before);
  for(const chargeMicros of [0,7]){const settled={...message,chargeMicros,currencyCode:'CNY',costSource:'PROVIDER_RESPONSE'};assert.deepEqual(projectedMessageCost(settled),{chargeMicros,currencyCode:'CNY',costSource:'PROVIDER_RESPONSE'});assert.match(messageFooterCostLabel(settled),/^¥/);}
 }
});
