import test from 'node:test';
import assert from 'node:assert/strict';
import { projectedCost, projectedMessageCost, cnyCostLabel } from '../src/desktop-cost-estimator.mjs';
import { RELAYCORE_MODELS } from '../src/relaycore-models.mjs';
const at=Date.UTC(2026,9,10);
test('all six Relay models estimate from frozen channel and preserve native currency',()=>{
 for(const model of RELAYCORE_MODELS){
  const cost=projectedCost({modelId:model.modelId,modelDisplayName:`${model.modelName} · ${model.channel}`,inputTokens:1000,outputTokens:100,cachedInputTokens:500,occurredAtMs:at});
  assert.ok(cost?.chargeMicros>=0);assert.equal(cost.currencyCode,'CNY');assert.equal(cost.costSource,'LOCAL_ESTIMATE');
 }
 assert.equal(projectedCost({modelId:'claude-sonnet-5-5',modelDisplayName:'Sonnet 5.5 · Kiro正价',inputTokens:1000,outputTokens:100,cachedInputTokens:500,occurredAtMs:at}).chargeMicros,735);
 assert.equal(projectedCost({modelId:'qwen3.7-plus',inputTokens:1000000,outputTokens:1000000,occurredAtMs:at}).chargeMicros,30000000);
 assert.equal(projectedCost({modelId:'qwen3.6-flash',inputTokens:100000,outputTokens:100000,occurredAtMs:at}).currencyCode,'CNY');
 assert.equal(projectedCost({modelId:'glm-5.3',inputTokens:1,outputTokens:1,occurredAtMs:at}),null);
});
test('helper and main combine once only with complete compatible facts',()=>{
 const main={callId:'answer',role:'ANSWER',status:'FINAL_MEASURED',chargeMicros:10,currencyCode:'USD',costSource:'PROVIDER_RESPONSE'};
 const helper={...main,callId:'retrieval',role:'RETRIEVAL',chargeMicros:5,costSource:'LOCAL_ESTIMATE'};
 assert.equal(projectedMessageCost({retrievalModelId:'RELAYCORE_GPT_6_1_SOL',accountingCalls:[main,helper]}).chargeMicros,15);
 assert.equal(projectedMessageCost({accountingCalls:[main,{...helper,status:'STREAM_PENDING'}]}),null);
 assert.equal(projectedMessageCost({accountingCalls:[main,{...helper,currencyCode:'CNY'}]}),null);
 assert.equal(projectedMessageCost({retrievalModelId:'RELAYCORE_GPT_6_1_SOL'}),null);
 assert.equal(projectedMessageCost({accountingCalls:[{...main,chargeMicros:0},{...helper,chargeMicros:0}]}).chargeMicros,0);
});
test('bare OpenRouter identities retain the requested provider tariff namespace',()=>{
 const message={actualModelId:'gpt-6.1-sol',modelSnapshot:{modelId:'openai/gpt-6.1-sol',displayName:'GPT-6.1 Sol'},createdAt:'2026-10-10T00:00:00.000Z',usage:{inputTokens:1000,outputTokens:100,cachedInputTokens:500}};
 assert.equal(projectedMessageCost(message).chargeMicros,2050);
 assert.equal(projectedMessageCost(message).priceVersion,'openrouter-public-prices-2026-10-09-v1');
});

test('Relay nominal RMB is displayed once and historical USD tags remain immutable',()=>{
 const saved={modelId:'gpt-6.1-sol',chargeMicros:1000000,currencyCode:'USD',costSource:'LOCAL_ESTIMATE',priceVersion:'relaycore-verified-group-2026-10-10-Pro3'};
 assert.equal(cnyCostLabel(projectedCost(saved)),'¥1');
 assert.equal(saved.currencyCode,'USD');
 assert.equal(projectedCost(saved).chargeMicros,1000000);
 const realUsd={...saved,modelId:'openai/gpt-6.1-sol',priceVersion:'openrouter-public-prices-2026-10-09-v1'};
 assert.equal(projectedCost(realUsd).currencyCode,'USD');
 assert.equal(cnyCostLabel(projectedCost(realUsd)),'¥6.7203');
});
