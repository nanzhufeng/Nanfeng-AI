import test from 'node:test';
import assert from 'node:assert/strict';
import { projectedCost, projectedMessageCost, cnyCostLabel, messageFooterCostLabel } from '../src/desktop-cost-estimator.mjs';
import { RELAYCORE_MODELS } from '../src/relaycore-models.mjs';
const at=Date.UTC(2026,9,10);
test('all RC prices and Claude aliases match native CNY including cache rounding',()=>{
 for(const [modelId,uncached,cached] of [
  ['claude-opus-5.5',2100,1435],['claude-opus-5-5',2100,1435],
  ['claude-sonnet-5-5',1050,735],['claude-sonnet-5.5',1050,735],
  ['gpt-6.1-sol',750,513],['gpt-6-astra',3750,2625],
  ['gemini-3.8-flash',315,221],['gemini-3.1-pro',896,644],
 ]) {
  for(const [cachedInputTokens,expected] of [[0,uncached],[500,cached]]) {
   const cost=projectedCost({modelId,inputTokens:1000,outputTokens:100,cachedInputTokens,occurredAtMs:at});
   assert.ok(cost,modelId);assert.equal(cost.currencyCode,'CNY',modelId);assert.equal(cost.chargeMicros,expected,modelId);
  }
  const legacy={modelId,chargeMicros:1000000,currencyCode:'USD',costSource:'LOCAL_ESTIMATE'};
  assert.equal(cnyCostLabel(projectedCost(legacy)),'¥1',modelId);
  assert.equal(legacy.currencyCode,'USD');
  const answer={...legacy,callId:'answer',requestedModelId:modelId,actualModelId:modelId};
  const search={callId:'search',requestedModelId:'openai/gpt-6-luna',actualModelId:'gpt-6-luna',chargeMicros:100000,currencyCode:'USD',costSource:'PROVIDER_RESPONSE'};
  assert.equal(messageFooterCostLabel({accountingCalls:[answer,search,search]}),'¥1.6720',modelId);
  assert.equal(messageFooterCostLabel({accountingCalls:[answer,search,{callId:'unknown'}]}),'¥1.6720 + 未知',modelId);
 }
});
test('all six Relay models estimate from frozen channel and preserve native currency',()=>{
 for(const model of RELAYCORE_MODELS){
  if (model.modelId === "gpt-6.2-sol") {
    assert.equal(projectedCost({modelId:model.modelId,inputTokens:1000,outputTokens:100,occurredAtMs:at}),null);
    continue;
  }
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

test('Hydrogen has an independent standard USD tariff', () => {
  const usage = { inputTokens: 1000, outputTokens: 100, cachedInputTokens: 500, occurredAtMs: Date.parse('2026-11-14T00:00:00Z') };
  const hydrogen = projectedCost({ ...usage, modelId: 'google/gemini-4-hydrogen' });
  assert.equal(hydrogen.chargeMicros, 1538);
  assert.equal(hydrogen.currencyCode, 'USD');
  assert.equal(hydrogen.priceVersion, 'openrouter-gemini-4-hydrogen-2026-11-14-v1');
  assert.equal(projectedCost({ ...usage, modelId: 'google/gemini-3.8-flash' }).chargeMicros, 788);
});
