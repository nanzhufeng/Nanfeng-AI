import test from 'node:test';
import assert from 'node:assert/strict';
import { renderAndroidSettingsShell } from '../src/android-settings-shell.mjs';
const render = (records,usageSection='conversation') => renderAndroidSettingsShell({page:'conversation-cost',usageSection,native:true,appearance:{},productSettings:{},modelServiceSettings:[],usageLedger:{records,inputTokens:20,outputTokens:10},data:{exchange:{conversations:[]}}});
const record = {entryId:'usage-conversation-source',conversationId:'conversation-source',modelId:'openai/gpt-5.6-terra',inputTokens:20,outputTokens:10,occurredAtMs:Date.UTC(2026,8,30),chargeMicros:5,currencyCode:'USD',costSource:'PROVIDER_RESPONSE'};
test('OCR costs expose only safe per-Attempt metadata and never invent missing duration',()=>{
 const ocr={...record,entryId:'usage-transcription-synthetic-1',modelId:'glm-ocr',requestState:'COMPLETED',modelElapsedMs:1200,ocrDetails:{providerId:'ZHIPU',attemptId:'transcription-synthetic-1',attemptNumber:1,sourceDisplayName:'<synthetic>.png',pageCount:2,providerRequestId:'synthetic-request'},rawResponse:'PRIVATE_PAYLOAD_MUST_NOT_RENDER'};
 const html=render([ocr],'ocr');
 for(const text of ['智谱','Attempt transcription-synthetic-1','第 1 次','&lt;synthetic&gt;.png','2 页','synthetic-request','耗时 1.20 秒'])assert.ok(html.includes(text),text);
 assert.ok(!html.includes('PRIVATE_PAYLOAD_MUST_NOT_RENDER'));assert.ok(!html.includes('<synthetic>.png'));
 const failed=render([{...ocr,requestState:'FAILED',safeErrorCode:'EMPTY_MARKDOWN'}],'ocr');assert.ok(failed.includes('失败 · EMPTY_MARKDOWN · 耗时未记录'));
 const legacy=render([{...ocr,modelElapsedMs:null,ocrDetails:null}],'ocr');assert.ok(legacy.includes('旧调用详情未记录'));assert.ok(legacy.includes('耗时未记录'));
});
test('cost outcome uses completed Attempt duration and preserves missing or failed boundaries',()=>{
 const completed=render([{...record,requestState:'COMPLETED',modelElapsedMs:1200}]);
 assert.ok(completed.includes('已完成'));assert.ok(completed.includes('模型耗时 1.20 秒'));
 for(const item of [{requestState:'FAILED',modelElapsedMs:1200},{requestState:'COMPLETED',modelElapsedMs:null},{requestState:'COMPLETED',modelElapsedMs:-1},{}]){
  assert.ok(render([{...record,...item}]).includes('模型耗时未记录'));
 }
 const failed=render([{...record,requestState:'FAILED',safeErrorCode:'OUTPUT_LIMIT'}]);
 assert.ok(failed.includes('失败 · OUTPUT_LIMIT'));
 const escaped=render([{...record,requestState:'FAILED',safeErrorCode:'<script>synthetic</script>'}]);
 assert.ok(!escaped.includes('<script>synthetic</script>'));assert.ok(escaped.includes('&lt;script&gt;'));
});
test('cost details distinguish provider reports, local estimates and unavailable costs',()=>{
 const provider=render([record]);assert.ok(provider.includes('服务商报告'));assert.ok(provider.includes('约 ¥'));assert.ok(provider.includes('2026-08-27'));
 const estimate=render([{...record,chargeMicros:null,costSource:null}]);assert.match(estimate,/本地估算/);assert.match(estimate,/约 ¥/);
 const unknown=render([{...record,modelId:'synthetic-unknown-model',chargeMicros:null,costSource:null}]);assert.match(unknown,/金额未知/);assert.doesNotMatch(unknown,/服务商报告/);
});
test('unknown currency and missing costs never become a zero-cost aggregate',()=>{
 const unknown=render([{...record,currencyCode:'EUR'}]);assert.match(unknown,/金额未知/);assert.doesNotMatch(unknown,/约 ¥0(?:<|\s)/);
 const mixed=render([record,{...record,entryId:'usage-conversation-unknown',currencyCode:'EUR'}]);assert.match(mixed,/1 笔金额未知/);
 const zero=render([{...record,chargeMicros:0}]);assert.match(zero,/服务商报告/);assert.match(zero,/约 ¥0/);
});
