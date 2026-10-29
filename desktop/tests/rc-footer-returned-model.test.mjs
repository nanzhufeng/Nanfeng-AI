import test from 'node:test';
import assert from 'node:assert/strict';
import {transcriptMetadata,compactModelName,renderChatFirstShell} from '../src/chat-shell.mjs';
import {renderAndroidSettingsShell} from '../src/android-settings-shell.mjs';

const message=(actual,extra={})=>({id:'footer-a',parentId:'footer-u',role:'assistant',delivery:'COMPLETE',source:'PROVIDER',createdAt:'2026-10-21T06:00:00Z',modelSnapshot:{providerId:'RELAYCORE_GPT',modelId:'gpt-6.1-sol',displayName:'GPT-6.1 Sol · Pro3'},actualModelId:actual,chargeMicros:7,currencyCode:'CNY',costSource:'LOCAL_ESTIMATE',blocks:[{kind:'TEXT',text:'synthetic answer remains unchanged'}],...extra});
const render=value=>renderChatFirstShell({data:{summary:{id:'footer-workspace'},exchange:{projects:[],knowledge:[],memory:[],relations:[],conversations:[{id:'footer-conversation',title:'synthetic footer',revision:1,currentLeafId:value.id,messages:[{id:'footer-u',role:'user',blocks:[{kind:'TEXT',text:'synthetic question'}]},value]}]}},native:true,pane:'chat',selectedConversationId:'footer-conversation',composerDraft:'',chatSearch:'',status:'',error:'',connection:{}});
const footer=html=>html.match(/<article\b[^>]*data-message-id="footer-a"[\s\S]*?<p class="chat-message-metadata"[^>]*>([\s\S]*?)<\/p>/)?.[1];

for(const [providerId,modelId,expected] of [
 ['RELAYCORE_CLAUDE','claude-opus-5.5','Opus 5.5 · RC'],
 ['RELAYCORE_CLAUDE','claude-sonnet-5-5','Sonnet 5.5 · RC'],
 ['RELAYCORE_GPT','gpt-6.1-sol','6.1 Sol · RC'],
 ['RELAYCORE_GPT','gpt-6-astra','6 Astra · RC'],
 ['RELAYCORE_GEMINI','gemini-3.8-flash','Gemini 3.8 · RC'],
 ['RELAYCORE_GEMINI','gemini-3.1-pro','Gemini 3.1 · RC'],
])test(`RC message footer projects its persisted returned identity: ${modelId}`,()=>{
 const value=message(modelId,{modelSnapshot:{providerId,modelId,displayName:'original frozen request label'}}),before=structuredClone(value),meta=transcriptMetadata(value);
 assert.equal(meta.model,expected);assert.equal(meta.modelVerification,'matched');assert.deepEqual(value,before);assert.ok(footer(render(value)).includes(expected));
});
test('different returned RC identity is visible beside the message while frozen request and cost remain unchanged',()=>{
 const value=message('gpt-6-astra'),before=structuredClone(value),meta=transcriptMetadata(value),html=render(value),text=footer(html);
 assert.equal(meta.model,'6 Astra · RC');assert.equal(meta.modelVerification,'mismatch');assert.match(text,/返回模型不一致/);assert.ok(!text.includes('6.1 Sol'));assert.ok(text.includes('chat-message-metadata-cost'));assert.deepEqual(value,before);
 assert.match(html,/<p class="chat-message-metadata" tabindex="0" aria-keyshortcuts="Control\+A Meta\+A"/);
});
test('absent returned RC identity is unverified rather than the requested model',()=>{
 const value=message(null);assert.equal(transcriptMetadata(value).model,'返回模型未验证 · RC');assert.equal(transcriptMetadata(value).modelVerification,'not_returned');assert.ok(!footer(render(value)).includes('Sol'));
});
test('response-internal identity conflict is a readable independent state without leaking the sentinel as a model',()=>{
 const value=message('inconsistent-response-model'),before=structuredClone(value);assert.equal(transcriptMetadata(value).modelVerification,'conflicting');const text=footer(render(value));assert.match(text,/响应内型号不一致/);assert.ok(!text.includes('inconsistent-response-model'));assert.deepEqual(value,before);
});
for(const actual of ['', '  ','null','bad\nmodel','x'.repeat(201),'汉'.repeat(67)])test(`invalid legacy returned identity stays unverified: ${JSON.stringify(actual).slice(0,30)}`,()=>{
 const value=message(actual);assert.equal(transcriptMetadata(value).model,'返回模型未验证 · RC');assert.equal(transcriptMetadata(value).modelVerification,'not_returned');
});
test('known same-provider Opus wire alias compares as the same identity without rewriting either wire value',()=>{
 const value=message('claude-opus-5-5',{modelSnapshot:{providerId:'RELAYCORE_CLAUDE',modelId:'claude-opus-5.5',displayName:'Opus 5.5 · Kiro正价'}}),before=structuredClone(value);assert.equal(transcriptMetadata(value).model,'Opus 5.5 · RC');assert.equal(transcriptMetadata(value).modelVerification,'matched');assert.deepEqual(value,before);
});
test('known returned identity with missing requested identity does not invent a mismatch',()=>{
 const value=message('gpt-6-astra',{modelSnapshot:{providerId:'RELAYCORE_GPT',displayName:'legacy request without model id'}}),meta=transcriptMetadata(value);assert.equal(meta.model,'6 Astra · RC');assert.equal(meta.modelVerification,'request_not_recorded');assert.match(footer(render(value)),/请求型号未记录/);assert.ok(!footer(render(value)).includes('返回模型不一致'));
});
test('valid long returned identity remains complete and escaped in selectable footer text',()=>{
 const actual='汉'.repeat(66)+'xx',value=message(actual);assert.equal(new TextEncoder().encode(actual).length,200);assert.equal(transcriptMetadata(value).model,actual+' · RC');assert.ok(footer(render(value)).includes(actual));
 const unsafe=message('<img src=x onerror=alert(1)>'),html=render(unsafe);assert.ok(footer(html).includes('&lt;img src=x onerror=alert(1)&gt;'));assert.ok(!html.includes('<img src=x'));
});
test('portable historical channel label remains distinguishable without inventing a returned identity',()=>{
 const value=message(null,{modelSnapshot:{modelId:'gpt-6.1-sol',displayName:'GPT-6.1 Sol · Pro1 · 0.12×'}});assert.equal(transcriptMetadata(value).model,'6.1 Sol · RC');assert.equal(transcriptMetadata(value).modelVerification,undefined);
});
test('non-RC and explicit user-message snapshots retain their frozen model semantics',()=>{
 const ordinary=message('different',{modelSnapshot:{providerId:'OPENROUTER',modelId:'openai/gpt-6.1-sol',displayName:'frozen ordinary name'}});assert.equal(transcriptMetadata(ordinary).model,'frozen ordinary name');assert.equal(transcriptMetadata(ordinary).modelVerification,undefined);
 const user=message(null,{role:'user'});assert.equal(transcriptMetadata(user).model,'GPT-6.1 Sol · Pro3');assert.equal(transcriptMetadata(user).modelVerification,undefined);
});
test('compact RC names preserve known old channel labels as presentation facts',()=>{
 for(const [value,label]of [['GPT-6.1 Sol · Pro2','6.1 Sol · RC'],['GPT-6 Astra · Pro3 · 0.25×','6 Astra · RC'],['Opus 5.5 · Kiro正价','Opus 5.5 · RC'],['Gemini 3.8 Flash · Gemini','Gemini 3.8 · RC']])assert.equal(compactModelName(value),label);
});
test('feature review names RC returned footer identity and keeps request/return distinction in existing usage entry',()=>{
 const html=renderAndroidSettingsShell({page:'feature-review',runtimeInfo:{platform:'windows'}});assert.match(html,/RC.*页脚/);assert.match(html,/请求型号.*返回型号/);assert.match(html,/费用与用量/);
});
