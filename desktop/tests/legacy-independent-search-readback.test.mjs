import test from 'node:test';
import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import {renderAndroidSettingsShell} from '../src/android-settings-shell.mjs';
import {renderChatFirstShell} from '../src/chat-shell.mjs';
import {groupRetrievalUsageRecords,usageRecordPhysicalFacts} from '../src/usage-ledger-display.mjs';
const fixture=JSON.parse(await readFile(new URL('./fixtures/legacy-independent-search-ledger-v1.json',import.meta.url),'utf8'));
const byId=new Map(fixture.rows.map(row=>[row.entry_id,row]));
const projection=row=>({entryId:row.entry_id,executionId:row.execution_id,conversationId:row.conversation_id,attemptId:row.attempt_id,invocationId:row.invocation_id,sourceRootId:'a'.repeat(64),kind:row.kind,factGrade:row.fact_grade,requestedModelId:row.requested_model_id,actualModelId:row.actual_model_id,modelId:row.actual_model_id??row.requested_model_id,inputTokens:row.input_tokens,outputTokens:row.output_tokens,cachedInputTokens:row.cached_input_tokens,chargeMicros:row.charge_micros,currencyCode:row.currency_code,costSource:row.cost_source,occurredAtMs:row.occurred_at_ms,requestState:null,callMetadata:structuredClone(row.callMetadata)});
const records=()=>fixture.expectedDisplayIds.map(id=>projection(byId.get(id)));
const data={summary:{id:'synthetic'},exchange:{conversations:[],projects:[],knowledge:[],memory:[],relations:[]}};
const usage=rows=>renderAndroidSettingsShell({page:'conversation-cost',native:true,data,usageSection:'conversation',usageLedger:{records:rows,inputTokens:5,outputTokens:2,cachedInputTokens:0}});
test('legacy service receipts remain named services with unknown quantities beside a genuine LLM record',()=>{
 const rows=records(),before=JSON.stringify(rows),html=usage(rows);const cards=[...html.matchAll(/<section class="android-settings-card android-settings-ledger-row">[\s\S]*?<\/section>/g)].map(match=>match[0]);assert.equal(cards.length,3);
 const service=cards.filter(card=>card.includes('智谱网页搜索 · 高级版'));assert.equal(service.length,2);
 for(const card of service){assert.ok(card.includes('Token 用量未返回'));assert.ok(card.includes('金额未知'));assert.ok(card.includes('费用来源未确认'));assert.ok(card.includes('联网检索'));assert.ok(!card.includes('GPT-6 Luna'));assert.ok(!card.includes('输入 0 / 输出 0'));assert.ok(!card.includes('¥0'));}
 const model=cards.find(card=>card.includes('GPT-6 Luna'));assert.ok(model.includes('输入 5 / 输出 2 Token'));assert.ok(model.includes('USD')||model.includes('¥0.000027'));assert.equal(JSON.stringify(rows),before);
});
test('read-only grouping never attaches service receipts to an LLM or fills their empty model identity',()=>{
 const rows=records(),before=JSON.stringify(rows),grouped=groupRetrievalUsageRecords(rows);assert.equal(grouped.length,3);assert.deepEqual(grouped.flatMap(usageRecordPhysicalFacts),rows);
 for(const row of grouped.filter(row=>row.callMetadata.serviceId)){assert.equal(row.requestedModelId,'');assert.equal(row.actualModelId,null);assert.equal(row.inputTokens,null);assert.equal(row.chargeMicros,null);assert.equal(row.callMetadata.serviceId,'ZHIPU_WEB_SEARCH');assert.equal(row.callMetadata.serviceEngineId,'search_pro');}
 assert.equal(JSON.stringify(rows),before);
});
test('deleted conversation and unresolved historical service receipts stay reachable without a synthetic success result',()=>{
 const rows=records(),pending=rows.find(row=>row.kind==='STREAM_PENDING');assert.ok(pending);assert.equal(pending.requestState,null);const html=usage(rows);assert.ok(html.includes('智谱网页搜索 · 高级版'));assert.ok(html.includes('状态未记录'));assert.ok(html.includes('金额未知'));assert.ok(!html.includes('已完成，归因待补写'));
});
test('legacy readback does not reintroduce an independent service control into current model settings or the composer',()=>{
 const html=renderAndroidSettingsShell({page:'model-configuration',native:true,data})+renderChatFirstShell({data,native:true,pane:'chat',status:'',error:'',connection:{},p6gModelPickerOpen:true});
 for(const retired of ['ZHIPU_WEB_SEARCH','search_pro','open-independent-search','configure-independent-search','independent-web-search'])assert.ok(!html.includes(retired),retired);
});
