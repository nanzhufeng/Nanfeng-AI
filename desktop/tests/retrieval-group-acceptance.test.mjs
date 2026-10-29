import test from 'node:test';
import assert from 'node:assert/strict';
import {groupRetrievalUsageRecords,usageRecordPhysicalFacts} from '../src/usage-ledger-display.mjs';
import {renderAndroidSettingsShell} from '../src/android-settings-shell.mjs';
const row=(id,extra={})=>({entryId:`scope-${id}`,sourceRootId:'root-one',executionId:'question-one',attemptId:'parent-one',invocationId:id,conversationId:'conversation-one',requestedModelId:'openai/gpt-6-luna',actualModelId:'openai/gpt-6-luna',modelId:'openai/gpt-6-luna',kind:'FINAL_MEASURED',inputTokens:7,outputTokens:3,cachedInputTokens:0,chargeMicros:6,currencyCode:'USD',costSource:'PROVIDER_RESPONSE',occurredAtMs:1791590400000,requestState:'COMPLETED',callMetadata:{role:'RETRIEVAL',modelDisplayName:'Luna · OpenRouter'},...extra});
const render=records=>renderAndroidSettingsShell({page:'conversation-cost',usageLedger:{records},data:{exchange:{conversations:[]}}});
for(const key of ['sourceRootId','conversationId','executionId','requestedModelId'])test(`retrieval group separates a different ${key} without dropping physical calls`,()=>{
 const records=[row('a'),row('b'),row('c',{[key]:'different'})],before=structuredClone(records),groups=groupRetrievalUsageRecords(records);
 assert.equal(groups.length,2);assert.deepEqual(groups.flatMap(usageRecordPhysicalFacts).map(r=>r.entryId),records.map(r=>r.entryId));assert.equal(groups[0].groupedCalls.length,2);assert.deepEqual(records,before);
});
for(const key of ['sourceRootId','conversationId','executionId','requestedModelId','modelDisplayName'])test(`missing ${key} never creates an invented retrieval identity`,()=>{
 for(const missing of [null,undefined,'']){
  const missingRow=id=>key==='modelDisplayName'?row(id,{callMetadata:{role:'RETRIEVAL',modelDisplayName:missing}}):row(id,{[key]:missing});
  const records=[missingRow('a'),missingRow('b')];assert.equal(groupRetrievalUsageRecords(records).length,2);assert.doesNotMatch(render(records),/2 次检索/);
 }
});
test('channel and independent search service identities cannot be merged into a model group',()=>{
 const records=[row('model'),row('model-two'),row('channel',{callMetadata:{role:'RETRIEVAL',modelDisplayName:'Luna · Other channel'}}),row('service',{callMetadata:{role:'RETRIEVAL',modelDisplayName:'Luna · OpenRouter',serviceId:'ZHIPU_WEB_SEARCH',serviceEngineId:'search_pro'}})];
 const groups=groupRetrievalUsageRecords(records);assert.equal(groups.length,3);assert.equal(groups[0].groupedCalls.length,2);assert.equal(groups.flatMap(usageRecordPhysicalFacts).length,4);
});
test('one conversation with two questions keeps two retrieval groups and a separate answer',()=>{
 const records=[row('q1a'),row('q1b'),row('q1-answer',{callMetadata:{role:'ANSWER',modelDisplayName:'Answer model'}}),row('q2a',{executionId:'question-two',requestState:'FAILED',safeErrorCode:'SERVICE_UNAVAILABLE'}),row('q2b',{executionId:'question-two',requestState:'UNKNOWN',safeErrorCode:'NETWORK',inputTokens:null,outputTokens:null,cachedInputTokens:null,chargeMicros:null,currencyCode:null,costSource:null})];
 const before=structuredClone(records),groups=groupRetrievalUsageRecords(records),html=render(records);assert.equal(groups.length,3);assert.equal(groups.flatMap(usageRecordPhysicalFacts).length,5);
 assert.equal((html.match(/2 次检索/g)||[]).length,2);assert.equal((html.match(/主回答/g)||[]).length,1);assert.match(html,/<strong>会话<\/strong><span>次数 <b>5 次<\/b>/);assert.match(html,/失败 1 次/);assert.match(html,/结果未知 1 次/);assert.match(html,/输入 7 \+ 未知 \/ 输出 3 \+ 未知 Token/);assert.deepEqual(records,before);
});
test('material reading and background roles never enter a retrieval group',()=>{
 const records=[row('r1'),row('r2'),row('read',{callMetadata:{role:'MATERIAL_READ',modelDisplayName:'Read model'}}),row('background',{callMetadata:{role:'BACKGROUND',modelDisplayName:'Background model'}})];
 const groups=groupRetrievalUsageRecords(records);assert.equal(groups.length,3);assert.equal(groups.flatMap(usageRecordPhysicalFacts).length,4);const html=render(records);assert.equal((html.match(/2 次检索/g)||[]).length,1);assert.equal((html.match(/材料读取/g)||[]).length,1);
});
test('group outcome keeps failure unknown cancellation and unknown quantities independent of order',()=>{
 const records=[row('completed'),row('failed',{requestState:'FAILED',safeErrorCode:'OUTPUT_LIMIT'}),row('unknown',{requestState:'UNKNOWN',safeErrorCode:'NETWORK',inputTokens:null,outputTokens:null,cachedInputTokens:null,chargeMicros:null,currencyCode:null,costSource:null}),row('cancelled',{requestState:'CANCELLED',safeErrorCode:'CANCELLED',inputTokens:0,outputTokens:0,chargeMicros:0})];
 const html=render(records);assert.equal(html,render([...records].reverse()));for(const s of ['4 次检索','已完成 1 次','失败 1 次','结果未知 1 次','已停止 1 次','输入 14 + 未知 / 输出 6 + 未知 Token','另有 1 笔金额未知'])assert.ok(html.includes(s),s);
});
