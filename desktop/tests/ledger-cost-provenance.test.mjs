import test from 'node:test';
import assert from 'node:assert/strict';
import { renderAndroidSettingsShell } from '../src/android-settings-shell.mjs';

const base={entryId:'legacy-fee',kind:'FINAL_MEASURED',conversationId:'conversation',executionId:'attempt',sourceRootId:'owned-root',invocationId:'physical-one',requestedModelId:'openai/gpt-6-luna',actualModelId:'openai/gpt-6-luna',modelId:'openai/gpt-6-luna',inputTokens:20,outputTokens:10,cachedInputTokens:0,chargeMicros:1000000,currencyCode:'USD',occurredAtMs:1791590400000,requestState:null};
const render=records=>renderAndroidSettingsShell({page:'conversation-cost',usageSection:'conversation',native:true,appearance:{},productSettings:{},modelServiceSettings:[],usageLedger:{records},data:{exchange:{conversations:[]}}});
const rows=html=>[...html.matchAll(/<section class="android-settings-card android-settings-ledger-row">([\s\S]*?)<\/section>/g)].map(m=>m[1]);
for(const grade of ['ESTIMATED','PROVIDER_REPORTED','RECONCILED'])for(const source of [null,undefined])test(`stored fee with ${String(source)} source stays unconfirmed despite ${grade} token grade`,()=>{
 const record={...base,factGrade:grade,costSource:source},before=structuredClone(record),html=rows(render([record]))[0];
 assert.match(html,/约 ¥6\.720309/);assert.match(html,/调用 · 费用来源未确认/);assert.doesNotMatch(html,/服务商报告|本地估算/);assert.deepEqual(record,before);
});
test('an explicit zero with missing fee source is known money but not a confirmed provider report',()=>{
 const html=rows(render([{...base,chargeMicros:0,currencyCode:'CNY',costSource:null}]))[0];assert.match(html,/约 ¥0/);assert.match(html,/费用来源未确认/);assert.doesNotMatch(html,/服务商报告/);
});
for(const [source,label,grade] of [['PROVIDER_RESPONSE','服务商报告','ESTIMATED'],['LOCAL_ESTIMATE','本地估算','PROVIDER_REPORTED']])test(`explicit ${source} origin is independent from ${grade} token grade`,()=>{
 const html=rows(render([{...base,costSource:source,factGrade:grade}]))[0];assert.ok(html.includes(label));assert.match(html,/约 ¥6\.720309/);assert.doesNotMatch(html,/费用来源未确认/);
});
test('newly computed missing-amount projection remains explicitly local, without rewriting the record',()=>{
 const record={...base,chargeMicros:null,currencyCode:null,costSource:null},before=structuredClone(record),html=rows(render([record]))[0];assert.match(html,/本地估算/);assert.deepEqual(record,before);
});
const grouped=(id,source)=>({...base,entryId:id,invocationId:id,chargeMicros:500000,costSource:source,callMetadata:{role:'RETRIEVAL',modelDisplayName:'GPT-6 Luna · OpenRouter'}});
for(const [source,label] of [['PROVIDER_RESPONSE','服务商报告'],['LOCAL_ESTIMATE','本地估算']])test(`group keeps an unconfirmed member beside ${source} without changing known money`,()=>{
 const records=[grouped('marked',source),grouped('unmarked',null)],before=structuredClone(records),html=rows(render(records));assert.equal(html.length,1);assert.match(html[0],/2 次检索/);assert.ok(html[0].includes(`${label}；另有 1 笔费用来源未确认`));assert.match(html[0],/约 ¥6\.72031/);assert.deepEqual(records,before);
});
test('a mixed-origin group retains provider, local and unconfirmed members in both orders',()=>{
 const records=[grouped('provider','PROVIDER_RESPONSE'),grouped('local','LOCAL_ESTIMATE'),grouped('missing',null)];for(const ordered of [records,[...records].reverse()]){const html=rows(render(ordered))[0];assert.match(html,/服务商报告与本地估算；另有 1 笔费用来源未确认/);assert.match(html,/3 次检索/);}
});
