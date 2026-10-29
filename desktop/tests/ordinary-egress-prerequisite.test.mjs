import test from 'node:test';
import assert from 'node:assert/strict';
import vm from 'node:vm';
import { readFileSync } from 'node:fs';
import { RELAYCORE_MODELS, relayCoreModel, relayCoreFooterName } from '../src/relaycore-models.mjs';
const source=readFileSync(new URL('../src/app.mjs',import.meta.url),'utf8').replaceAll('\r\n','\n');
const fn=name=>{const a=source.indexOf(`function ${name}(`);return a<0?'':source.slice(a,source.indexOf('\nfunction ',a+1));};
const missing='请启用OpenRouter并保存API Key，供GPT-6 Luna检索；未发送';
const preview={recipientChoiceId:'RELAYCORE_OPUS_5_5',recipientProviderId:'RELAYCORE_CLAUDE',recipientModelId:'claude-opus-5.5',recipientDisplayName:'Opus 5.5 · Kiro正价',retrievalRecipient:{providerId:'OPENROUTER',presetId:'GPT_6_LUNA',modelId:'openai/gpt-6-luna'}};
function fixture(){
 const owner={dataArea:'CHAT',current:{summary:{id:'w'}},selectedConversationId:'c',composerDraft:'合成待发送草稿',composerAttachments:[],conversationPreferences:{webSearchOverride:true},p6gSelection:{},p6gGlobalDefault:{},openrouterRegistryStatus:{},modelServiceSettings:[{providerId:'OPENROUTER',providerDisplayName:'OpenRouter',enabled:false,credentialStored:true,revision:1},{providerId:'RELAYCORE_CLAUDE',providerDisplayName:'RelayCore Claude',enabled:true,credentialStored:true,revision:1}]};
 const timers=[],calls=[],line={dataset:{},textContent:'',remove(){}},send={disabled:false},span={textContent:''},trigger={querySelector:()=>span,setAttribute(){}};
 const controls={querySelector:s=>s==='.chat-composer-route'?line:s.includes('toggle-p6g')?trigger:s.includes('save-local-message')?send:null};
 let context;
 context=vm.createContext({native:true,state:owner,ordinaryEgressPreviews:new WeakMap(),document:{querySelector:()=>controls},resolveConversation:()=>({id:'c',revision:1}),newConversationModelId:()=>null,RELAYCORE_MODELS,relayCoreModel,relayCoreFooterName,tauriBridge:{},setTimeout:f=>(timers.push(f),timers.length),clearTimeout(){},invokeForAuthorizedDataAreaOperation:()=>new Promise((resolve,reject)=>calls.push({resolve,reject})),render(){send.disabled=false;context.updateOrdinaryEgressDisclosure();}});
 vm.runInContext(['ordinaryEgressPreviewInput','ordinaryEgressPreviewErrorCopy','scheduleOrdinaryEgressPreview','updateOrdinaryEgressDisclosure'].map(fn).join('\n'),context);
 const start=()=>{context.scheduleOrdinaryEgressPreview();context.updateOrdinaryEgressDisclosure();return timers.shift()();};
 return {owner,context,calls,line,send,start};
}
test('missing Luna service retains its specific setup action and disables Send',async()=>{
 const f=fixture(),run=f.start();f.calls[0].reject(new Error(missing));await run;
 assert.ok(f.line.textContent.includes('OpenRouter'),'identify the retrieval service, not only the selected RC model');
 assert.ok(f.line.textContent.includes('API Key'));assert.ok(f.line.textContent.includes('设置'));
 assert.equal(f.line.dataset.routeReady,'false');assert.equal(f.send.disabled,true);
});
test('unavailable verified Luna catalog gives a catalog-specific recovery path',async()=>{
 const f=fixture(),run=f.start();f.calls[0].reject(new Error('GPT-6 Luna不在已核验OpenRouter目录；未发起检索'));await run;
 assert.ok(f.line.textContent.includes('GPT-6 Luna'));assert.ok(f.line.textContent.includes('目录'));assert.equal(f.send.disabled,true);
});
test('late prerequisite rejection cannot overwrite a newer ready preview',async()=>{
 const f=fixture(),old=f.start();f.owner.modelServiceSettings[0].revision++;f.owner.modelServiceSettings[0].enabled=true;
 const next=f.start();f.calls[1].resolve(preview);await next;const ready=f.line.textContent;
 f.calls[0].reject(new Error(missing));await old;
 assert.equal(f.line.textContent,ready);assert.equal(f.line.dataset.routeReady,'true');assert.equal(f.send.disabled,false);
});
test('restoring settings invalidates the failure and enables a newly disclosed route',async()=>{
 const f=fixture(),first=f.start();f.calls[0].reject(new Error(missing));await first;assert.equal(f.send.disabled,true);
 f.owner.modelServiceSettings[0].enabled=true;f.owner.modelServiceSettings[0].revision++;
 const next=f.start();f.calls[1].resolve(preview);await next;
 assert.equal(f.line.dataset.routeReady,'true');assert.equal(f.send.disabled,false);assert.ok(f.line.textContent.includes('GPT-6 Luna'));assert.ok(f.line.textContent.includes('Opus 5.5'));
});
test('unsupported attachment retains the existing model-or-material action',async()=>{
 const f=fixture(),run=f.start();f.calls[0].reject(new Error('不能处理本次附件'));await run;
 assert.equal(f.line.textContent,'当前模型不能处理本次附件，请更换模型或移除附件');assert.equal(f.send.disabled,true);
});

test('unsupported audio and video retain text-conversion guidance without suggesting a model switch',async()=>{
 const f=fixture();
 f.owner.composerAttachments=[{id:'synthetic-audio',sha256:'a'.repeat(64),mimeType:'audio/wav',byteCount:16044}];
 const before=JSON.stringify({draft:f.owner.composerDraft,attachments:f.owner.composerAttachments,selection:f.owner.p6gSelection});
 const run=f.start();f.calls[0].reject(new Error('当前选择的模型不能处理本次附件：普通聊天暂不支持直接发送音频或视频，请先转成文字后再发送；未发起请求'));await run;
 assert.ok(f.line.textContent.includes('转成文字'));
 assert.ok(f.line.textContent.includes('音频或视频'));
 assert.ok(!f.line.textContent.includes('更换模型'));
 assert.equal(f.line.dataset.routeReady,'false');assert.equal(f.send.disabled,true);
 assert.equal(JSON.stringify({draft:f.owner.composerDraft,attachments:f.owner.composerAttachments,selection:f.owner.p6gSelection}),before);
});
test('unknown errors keep private diagnostics out of the disclosure',async()=>{
 const f=fixture(),run=f.start();f.calls[0].reject(new Error('synthetic-private-diagnostic-value'));await run;
 assert.equal(f.line.textContent,'当前接收方不可用，请检查模型设置');assert.equal(f.send.disabled,true);
});

test('same-session registry publication invalidates a ready recipient and rejects its late result',async()=>{
 const f=fixture();f.owner.openrouterRegistryStatus={status:'VERIFIED',catalogVersion:'old'};
 const old=f.start();
 f.owner.openrouterRegistryStatus={status:'VERIFIED',catalogVersion:'new'};
 const next=f.start();assert.equal(f.send.disabled,true);assert.equal(f.line.dataset.routeReady,'false');
 f.calls[1].resolve({...preview,recipientDisplayName:'新目录接收方'});await next;
 const ready=f.line.textContent;assert.equal(f.send.disabled,false);
 f.calls[0].resolve({...preview,recipientDisplayName:'旧目录迟到接收方'});await old;
 assert.equal(f.line.textContent,ready);assert.ok(!f.line.textContent.includes('旧目录'));
});

test('a stable-fallback transition invalidates the same catalog version and retains refusal',async()=>{
 const f=fixture();f.owner.openrouterRegistryStatus={status:'VERIFIED',catalogVersion:'same'};
 const first=f.start();f.calls[0].resolve(preview);await first;assert.equal(f.send.disabled,false);
 f.owner.openrouterRegistryStatus={status:'STABLE_FALLBACK',catalogVersion:'same'};
 const fallback=f.start();assert.equal(f.send.disabled,true);
 f.calls[1].reject(new Error('不能处理本次附件'));await fallback;
 assert.equal(f.line.dataset.routeReady,'false');assert.equal(f.send.disabled,true);
});
