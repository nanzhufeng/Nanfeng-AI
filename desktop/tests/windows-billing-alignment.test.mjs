import test from 'node:test';
import assert from 'node:assert/strict';
import { groupRetrievalUsageRecords, usageRecordPhysicalFacts } from '../src/usage-ledger-display.mjs';
import { projectedCost, messageFooterCostLabel, USD_TO_CNY } from '../src/desktop-cost-estimator.mjs';
import { renderAndroidSettingsShell } from '../src/android-settings-shell.mjs';
import { renderChatFirstShell } from '../src/chat-shell.mjs';
const row=(id,extra={})=>({entryId:id,sourceRootId:'owned-root',executionId:'question',attemptId:id,invocationId:id,
 conversationId:'conversation',requestedModelId:'openai/gpt-6-luna',actualModelId:'openai/gpt-6-luna',modelId:'openai/gpt-6-luna',
 kind:'FINAL_MEASURED',inputTokens:2,outputTokens:1,cachedInputTokens:0,occurredAtMs:1791590400000,
 chargeMicros:1000000,currencyCode:'USD',costSource:'PROVIDER_RESPONSE',
 callMetadata:{role:'RETRIEVAL',modelDisplayName:'GPT-6 Luna · OpenRouter'},...extra});
test('retrieval grouping is scoped to root and question; answer and unknown identities remain separate',()=>{
 const records=[row('r1'),row('r2'),row('foreign',{sourceRootId:'other-root'}),row('answer',{callMetadata:{role:'ANSWER',modelDisplayName:'Opus · RC'}}),row('legacy',{executionId:null})];
 const original=structuredClone(records);const grouped=groupRetrievalUsageRecords(records);
 assert.equal(grouped.length,4);assert.equal(usageRecordPhysicalFacts(grouped[0]).length,2);
 assert.equal(grouped[0].chargeMicros,null);assert.equal(grouped[0].currencyCode,null);assert.deepEqual(records,original);
});
test('mixed-currency footer converts USD once and retains unknown rather than zero',()=>{
 const calls=[{...row('usd'),callId:'usd'},{...row('cny',{currencyCode:'CNY',chargeMicros:2000000}),callId:'cny'},
 {...row('unknown',{modelId:'unlisted',requestedModelId:'unlisted',actualModelId:null,inputTokens:null,chargeMicros:null,currencyCode:null}),callId:'unknown'}];
 const actual=messageFooterCostLabel({accountingCalls:calls});
 assert.equal(actual,`¥${(USD_TO_CNY+2).toFixed(4)} + 未知`);
 assert.equal(messageFooterCostLabel({accountingCalls:[calls[2]]}),null);
});
test('stored legacy USD estimate is not reinterpreted from current Relay model names',()=>{
 const value=projectedCost({modelId:'gpt-6.1-sol',chargeMicros:123,currencyCode:'USD',costSource:'LOCAL_ESTIMATE',priceVersion:'relaycore-verified-group-old'});
 assert.equal(value.currencyCode,'USD');assert.equal(value.chargeMicros,123);
});
test('settings show grouped retrieval and distinct answer while keeping physical totals',()=>{
 const records=[row('r1'),row('r2'),row('answer',{requestedModelId:'claude-opus-5.5',actualModelId:'claude-opus-5.5',modelId:'claude-opus-5.5',currencyCode:'CNY',chargeMicros:1000000,costSource:'LOCAL_ESTIMATE',callMetadata:{role:'ANSWER',modelDisplayName:'Opus 5.5 · RC'}})];
 const html=renderAndroidSettingsShell({page:'conversation-cost',usageLedger:{records,inputTokens:6,outputTokens:3}});
 assert.match(html,/联网检索/);assert.match(html,/2 次检索/);assert.match(html,/主回答/);
 assert.match(html,/GPT-6 Luna/);assert.match(html,/Opus 5.5/);
 assert.match(html,/<strong>会话<\/strong><span>次数 <b>3 次<\/b>/,'two retrievals plus the answer remain three physical calls');
});

test('late ledger facts refresh the rendered reply footer without rewriting the transcript',()=>{
 const conversation={id:'conversation',title:'合成费用',revision:1,currentLeafId:'reply',messages:[{id:'reply',attemptId:'question',role:'assistant',delivery:'COMPLETE',createdAt:'2026-10-14T00:00:00Z',blocks:[{kind:'TEXT',text:'合成回答'}]}]};
 const data={summary:{id:'owned-workspace'},exchange:{conversations:[conversation]}};
 const original=structuredClone(data);
 const render=records=>renderChatFirstShell({data,native:true,pane:'chat',selectedConversationId:'conversation',composerDraft:'',chatSearch:'',connection:{},usageLedger:{records}});
 assert.match(render([]),/金额未知/);
 const records=[row('r1'),row('answer',{currencyCode:'CNY',chargeMicros:2000000,callMetadata:{role:'ANSWER',modelDisplayName:'Opus · RC'}}),row('other-question',{executionId:'different-question',chargeMicros:900000000})];
 assert.ok(render(records).includes(`¥${(USD_TO_CNY+2).toFixed(4)}`));
 assert.deepEqual(data,original);
});

test('category call counts preserve ungrouped identities and keep automatic categories separate',()=>{
 const records=[row('r1'),row('r2'),row('foreign-root',{sourceRootId:'other-root'}),row('answer',{callMetadata:{role:'ANSWER',modelDisplayName:'Answer'}}),row('legacy',{executionId:null}),
  row('title-one',{callMetadata:{role:'BACKGROUND',modelDisplayName:'Title'}}),row('history-one',{callMetadata:{role:'BACKGROUND',modelDisplayName:'History'}})];
 const before=structuredClone(records);
 const html=renderAndroidSettingsShell({page:'conversation-cost',usageLedger:{records}});
 assert.match(html,/<strong>会话<\/strong><span>次数 <b>5 次<\/b>/);
 assert.match(html,/<strong>会话标题整理<\/strong><span>次数 <b>1 次<\/b>/);
 assert.match(html,/<strong>历史资料整理<\/strong><span>次数 <b>1 次<\/b>/);
 assert.match(html,/2 次检索/);
 assert.deepEqual(records,before);
});

test('grouped retrieval totals retain unknown amounts and never count display aggregates again',()=>{
 const records=[row('r1'),row('r2',{modelId:'unlisted',actualModelId:null,requestedModelId:'openai/gpt-6-luna',chargeMicros:null,currencyCode:null,inputTokens:null,outputTokens:null})];
 const html=renderAndroidSettingsShell({page:'conversation-cost',usageLedger:{records}});
 assert.match(html,/2 次检索/);assert.match(html,/另有 1 笔金额未知/);
 assert.ok(html.includes(`约 ¥${USD_TO_CNY.toFixed(6)}`));
});

test('cancelled partial reported tokens remain visible while unknown output and aggregate remain unknown',()=>{
 const record=row('cancel-partial',{requestState:'CANCELLED',safeErrorCode:'CANCELLED',inputTokens:6,outputTokens:null,cachedInputTokens:1,chargeMicros:null,currencyCode:null,costSource:null,callMetadata:{role:'ANSWER',modelDisplayName:'Synthetic model'}});
 const before=structuredClone(record);
 const html=renderAndroidSettingsShell({page:'conversation-cost',usageLedger:{records:[record],inputTokens:6,outputTokens:0}});
 assert.match(html,/输入 6 \/ 输出 未知 Token/);assert.match(html,/缓存 1 Token/);
 assert.match(html,/输出 Token<\/span><b>未知<\/b>/);assert.match(html,/金额未知/);assert.match(html,/已停止/);
 assert.deepEqual(record,before);
 const zero=renderAndroidSettingsShell({page:'conversation-cost',usageLedger:{records:[{...record,inputTokens:0,cachedInputTokens:0}],inputTokens:0,outputTokens:0}});
 assert.match(zero,/输入 0 \/ 输出 未知 Token/);assert.match(zero,/缓存 0 Token/);
});

test('grouped retrieval keeps known token subtotals beside an interrupted call with no quantities',()=>{
 const records=[row('measured',{requestState:'COMPLETED',modelElapsedMs:12000,inputTokens:17,outputTokens:5,cachedInputTokens:3,chargeMicros:6}),
  row('interrupted',{requestState:'UNKNOWN',safeErrorCode:'PROCESS_INTERRUPTED',actualModelId:'openai/gpt-6-luna',inputTokens:null,outputTokens:null,cachedInputTokens:null,chargeMicros:null,currencyCode:null,costSource:null})];
 const original=structuredClone(records);const html=renderAndroidSettingsShell({page:'conversation-cost',usageLedger:{records,inputTokens:17,outputTokens:5}});
 assert.match(html,/输入 17 \+ 未知 \/ 输出 5 \+ 未知 Token · 缓存 3 \+ 未知 Token/);
 assert.match(html,/已完成 1 次/);assert.match(html,/结果未知 1 次/);assert.match(html,/PROCESS_INTERRUPTED/);
 assert.doesNotMatch(html,/模型耗时 12 秒/,'a grouped row cannot inherit the first child duration');
 assert.deepEqual(records,original,'presentation never changes physical entries');
});
test('grouped retrieval outcome and mixed cost provenance are independent of member order',()=>{
 const records=[row('done',{requestState:'COMPLETED',modelElapsedMs:12000}),row('failed',{requestState:'FAILED',safeErrorCode:'OUTPUT_LIMIT',costSource:'LOCAL_ESTIMATE',currencyCode:'CNY',chargeMicros:2000000})];
 const render=records=>renderAndroidSettingsShell({page:'conversation-cost',usageLedger:{records,inputTokens:4,outputTokens:2}});
 assert.equal(render(records),render([...records].reverse()),'group meaning cannot depend on the first physical row');
 assert.match(render(records),/服务商报告与本地估算/);assert.match(render(records),/失败 1 次/);assert.match(render(records),/已完成 1 次/);
 const group=groupRetrievalUsageRecords(records)[0];assert.equal(group.requestState,null);assert.equal(group.safeErrorCode,null);assert.equal(group.modelElapsedMs,null);
});
test('grouped retrieval preserves explicit zero and treats invalid or oversized token totals as unavailable',()=>{
 const render=records=>renderAndroidSettingsShell({page:'conversation-cost',usageLedger:{records,inputTokens:0,outputTokens:0}});
 const records=[row('zero',{inputTokens:0,outputTokens:0,cachedInputTokens:0,requestState:'CANCELLED'}),row('unknown',{inputTokens:null,outputTokens:null,cachedInputTokens:null,requestState:null})];
 assert.match(render(records),/输入 0 \+ 未知 \/ 输出 0 \+ 未知 Token · 缓存 0 \+ 未知 Token/);
 assert.match(render(records),/已停止 1 次/);assert.match(render(records),/状态未记录 1 次/);
 assert.match(render([row('large',{inputTokens:Number.MAX_SAFE_INTEGER}),row('extra',{inputTokens:1})]),/输入 合计不可用/);
 assert.match(render([row('large',{inputTokens:Number.MAX_SAFE_INTEGER}),row('extra',{inputTokens:1})]),/输入 Token<\/span><b>合计不可用<\/b>/);
 assert.match(render([row('negative',{inputTokens:-1,outputTokens:null,cachedInputTokens:null}),row('safe',{inputTokens:7,outputTokens:null,cachedInputTokens:null})]),/输入 7 \+ 未知 \/ 输出 未知 Token/);
});

test('material reading is a separate physical role, never grouped or counted as web search',()=>{
 const records=[row('read',{callMetadata:{role:'MATERIAL_READ',modelDisplayName:'GPT-6.1 Sol'},inputTokens:11,outputTokens:2}),row('search',{inputTokens:17,outputTokens:5}),row('answer',{callMetadata:{role:'ANSWER',modelDisplayName:'GPT-6.1 Sol'},inputTokens:23,outputTokens:7})];
 const original=structuredClone(records);assert.equal(groupRetrievalUsageRecords(records).length,3);
 const html=renderAndroidSettingsShell({page:'conversation-cost',usageLedger:{records}});
 assert.equal((html.match(/材料读取/g)||[]).length,1);assert.equal((html.match(/联网检索/g)||[]).length,1);assert.equal((html.match(/主回答/g)||[]).length,1);assert.doesNotMatch(html,/2 次检索/);assert.deepEqual(records,original);
});
