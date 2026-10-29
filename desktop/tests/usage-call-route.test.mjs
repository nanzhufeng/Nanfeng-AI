import test from 'node:test';
import assert from 'node:assert/strict';
import {renderAndroidSettingsShell} from '../src/android-settings-shell.mjs';
const row=(route,extra={})=>({entryId:'route-row',sourceRootId:'root',executionId:'question',invocationId:'call',attemptId:'attempt',conversationId:'conversation',modelId:'gpt-6.1-sol',requestedModelId:'gpt-6.1-sol',actualModelId:'gpt-6-astra',kind:'FINAL_MEASURED',requestState:'COMPLETED',inputTokens:3,outputTokens:2,cachedInputTokens:0,chargeMicros:4,currencyCode:'USD',costSource:'PROVIDER_RESPONSE',callMetadata:{role:'ANSWER',modelDisplayName:'GPT-6.1 Sol · Pro3',route},...extra});
const render=records=>renderAndroidSettingsShell({page:'conversation-cost',usageLedger:{records},data:{exchange:{conversations:[]}}});
const routeText=html=>html.match(/<p[^>]*data-usage-call-route[^>]*>([\s\S]*?)<\/p>/)?.[1];
for(const [providerId,channelId,label] of [['RELAYCORE_GPT','PRO3','RelayCore GPT · Pro3'],['RELAYCORE_CLAUDE','KIRO_STANDARD','RelayCore Claude · Kiro正价'],['RELAYCORE_GEMINI','GEMINI','RelayCore Gemini · Gemini'],['OPENROUTER',null,'OpenRouter'],['QWEN',null,'通义千问'],['DEEPSEEK',null,'DeepSeek'],['ZHIPU',null,'智谱']])test(`usage route comes from immutable ${providerId}/${channelId} independently of returned model`,()=>{
 const records=[row({providerId,channelId})],before=structuredClone(records),html=render(records);assert.equal(routeText(html),`请求线路：${label}`);assert.match(html,/返回型号：gpt-6-astra/);assert.deepEqual(records,before);
});
for(const route of [null,undefined,{providerId:'https://secret.invalid/key',channelId:null},{providerId:'OPENROUTER',channelId:'PRO3'},{providerId:'RELAYCORE_GPT',channelId:'<script>'}])test(`absent or untrusted legacy route stays unknown: ${JSON.stringify(route)}`,()=>{
 const html=render([row(route)]);assert.equal(routeText(html),'请求线路：未记录');assert.ok(!html.includes('secret.invalid'));assert.ok(!html.includes('<script>'));
});
test('known relay provider with absent channel is not assigned the advertised model suffix',()=>assert.equal(routeText(render([row({providerId:'RELAYCORE_GPT',channelId:null})])),'请求线路：RelayCore GPT · 渠道未记录'));
test('grouped retrieval lists each physical route including unknown without rewriting inputs',()=>{
 const routes=[{providerId:'RELAYCORE_GPT',channelId:'PRO3'},{providerId:'RELAYCORE_GPT',channelId:'PRO2'},null];
 const records=routes.map((route,i)=>row(route,{entryId:`r-${i}`,callMetadata:{role:'RETRIEVAL',modelDisplayName:'同一冻结名称',route}})),before=structuredClone(records);
 assert.equal(routeText(render(records)),'请求线路：RelayCore GPT · Pro3（1次）、RelayCore GPT · Pro2（1次）、未记录（1次）');assert.deepEqual(records,before);
});
test('feature review explains advertised request route and downstream identity boundary without new action',()=>{
 const html=renderAndroidSettingsShell({page:'feature-review',runtimeInfo:{platform:'windows'}});assert.match(html,/调用线路：建议保留/);assert.match(html,/发送前.*服务商.*渠道/);assert.match(html,/下游实际模型/);
});
