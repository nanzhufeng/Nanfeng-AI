import test from 'node:test';
import assert from 'node:assert/strict';
import {renderAndroidSettingsShell} from '../src/android-settings-shell.mjs';
const row=(actual,extra={})=>({entryId:'identity-row',sourceRootId:'root',executionId:'question',invocationId:'call',attemptId:'attempt',conversationId:'conversation',modelId:'gpt-6.1-sol',requestedModelId:'gpt-6.1-sol',actualModelId:actual,kind:'FINAL_MEASURED',requestState:'COMPLETED',inputTokens:3,outputTokens:2,cachedInputTokens:0,chargeMicros:4,currencyCode:'USD',costSource:'PROVIDER_RESPONSE',callMetadata:{role:'ANSWER',modelDisplayName:'GPT-6.1 Sol · Pro3'},...extra});
const render=records=>renderAndroidSettingsShell({page:'conversation-cost',usageLedger:{records},data:{exchange:{conversations:[]}}});
const identity=html=>html.match(/<p[^>]*data-usage-model-identity[^>]*>([\s\S]*?)<\/p>/)?.[1];
for(const [actual,expected]of [['gpt-6.1-sol','请求型号：gpt-6.1-sol · 返回型号：gpt-6.1-sol'],['gpt-6-astra','请求型号：gpt-6.1-sol · 返回型号：gpt-6-astra（与请求不同）'],[null,'请求型号：gpt-6.1-sol · 返回型号：未提供'],['inconsistent-response-model','请求型号：gpt-6.1-sol · 返回型号：响应内型号不一致']])test(`usage card distinguishes returned identity ${String(actual)} without changing frozen name or physical facts`,()=>{
 const records=[row(actual)],before=structuredClone(records),html=render(records);assert.equal(identity(html),expected);assert.ok(html.includes('GPT-6.1 Sol · Pro3 · 输入 3 / 输出 2 Token'));assert.deepEqual(records,before);
});
for(const actual of ['', '  ', 'null','bad\nmodel','x'.repeat(201)])test(`invalid legacy returned model remains unknown: ${JSON.stringify(actual).slice(0,32)}`,()=>assert.equal(identity(render([row(actual)])),'请求型号：gpt-6.1-sol · 返回型号：未提供'));
test('legacy display model is not invented as a missing requested identity',()=>assert.equal(identity(render([row('returned-only',{requestedModelId:null,modelId:'legacy-display-label'})])),'请求型号：未记录 · 返回型号：returned-only'));
test('grouped retrieval identity includes every physical known unknown and conflicting response',()=>{
 const records=['gpt-6.1-sol','gpt-6-astra',null,'inconsistent-response-model'].map((actual,i)=>row(actual,{entryId:`identity-${i}`,invocationId:`call-${i}`,callMetadata:{role:'RETRIEVAL',modelDisplayName:'原冻结检索模型'}}));const before=structuredClone(records),html=render(records);assert.equal((html.match(/data-usage-model-identity/g)||[]).length,1);assert.equal(identity(html),'请求型号：gpt-6.1-sol（4次） · 返回型号：gpt-6.1-sol（1次）、gpt-6-astra（1次）、未提供（1次）、响应内型号不一致（1次）（1次与请求不同）');assert.deepEqual(records,before);
});
test('raw model text is escaped once and cannot create markup or click handlers',()=>{const html=render([row('<img src=x onerror=alert(1)>')]);assert.match(identity(html),/&lt;img src=x onerror=alert\(1\)&gt;/);assert.ok(!html.includes('<img src=x'));});
test('feature review lists the identity detail inside the existing usage page',()=>{const html=renderAndroidSettingsShell({page:'feature-review',runtimeInfo:{platform:'windows'}});assert.match(html,/模型返回身份：建议保留/);assert.match(html,/请求型号.*返回型号/);assert.match(html,/费用与用量/);});
