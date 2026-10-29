import {renderChatFirstShell} from '../src/chat-shell.mjs';
import test from 'node:test';
import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import {AUTO_PLANS,autoTask,autoCandidate,conversationAutoMimeTypes} from '../src/auto-chat-routing.mjs';
const fixture=JSON.parse(await readFile(new URL('../../protocol/fixtures/auto-routing-rc-domestic-20261010.json',import.meta.url),'utf8'));
const cases=JSON.parse(await readFile(new URL('../../protocol/fixtures/auto-routing-task-cases-v1.json',import.meta.url),'utf8'));
const service=(providerId,ids,extra={})=>({providerId,enabled:true,credentialStored:true,presets:ids.map(id=>({id,displayName:id,chatSelectable:true})),...extra});
test('Auto plans are exactly the fixed shared policy',()=>assert.deepEqual(AUTO_PLANS,fixture.plans));
test('shared task fixtures preserve UTF16 and exact keyword boundaries',()=>{for(const row of cases)assert.equal(autoTask(row.text??row.glyph.repeat(row.count),row.mimes??[]),row.task);});
test('Auto chooses RC daily/deep and filters disabled credentials before ranking',()=>{
 const rc=service('RELAYCORE_GPT',['RELAYCORE_GPT_6_1_SOL','RELAYCORE_GPT_6_ASTRA']);
 assert.equal(autoCandidate([rc]).id,'RELAYCORE_GPT_6_1_SOL');assert.equal(autoCandidate([rc],{text:'架构设计'}).id,'RELAYCORE_GPT_6_ASTRA');
 assert.equal(autoCandidate([{...rc,credentialStored:false}]),null);assert.equal(autoCandidate([{...rc,enabled:false}]),null);
});
test('companion RC requires an available Luna preset, not merely an OpenRouter group',()=>{
 const rc=service('RELAYCORE_GEMINI',['RELAYCORE_GEMINI_3_8_FLASH']);
 assert.equal(autoCandidate([rc],{webSearchEnabled:true}),null);
 assert.equal(autoCandidate([rc,service('OPENROUTER',['GPT_6_1_SOL'])],{webSearchEnabled:true}).id,'GPT_6_1_SOL');
 assert.equal(autoCandidate([rc,service('OPENROUTER',['GPT_6_LUNA'])],{webSearchEnabled:true}).id,'RELAYCORE_GEMINI_3_8_FLASH');
});
test('attachment task excludes RC, honours image search limits and ancestor branch only',()=>{
 const settings=[service('RELAYCORE_GPT',['RELAYCORE_GPT_6_1_SOL']),service('QWEN',['QWEN_3_7_PLUS']),service('OPENROUTER',['GPT_6_LUNA'])];
 assert.equal(autoCandidate(settings,{mimeTypes:['image/png']}).id,'QWEN_3_7_PLUS');
 assert.equal(autoCandidate(settings,{mimeTypes:['image/png'],webSearchEnabled:true}).id,'GPT_6_LUNA');
 const conversation={currentLeafId:'current',messages:[{id:'root',blocks:[{kind:'ASSET_REF',asset:{mimeType:'text/plain'}}]},{id:'current',parentId:'root',blocks:[]},{id:'unrelated',blocks:[{kind:'ASSET_REF',asset:{mimeType:'image/png'}}]}]};
 assert.deepEqual(conversationAutoMimeTypes(conversation),['text/plain']);
});

test('composer renders the eligible Auto model for this draft, preserving manual selection',()=>{
 const settings=[service('RELAYCORE_GPT',['RELAYCORE_GPT_6_1_SOL','RELAYCORE_GPT_6_ASTRA'])];
 const render=text=>renderChatFirstShell({data:{summary:{id:'fixture'},exchange:{conversations:[]}},native:true,pane:'chat',selectedConversationId:null,composerDraft:text,chatSearch:'',connection:{},modelServiceSettings:settings});
 assert.match(render('你好'),/aria-label="选择模型：[^"\n]*Sol[^"\n]*RC"/);
 assert.match(render('架构设计'),/aria-label="选择模型：[^"\n]*Astra[^"\n]*RC"/);
});

test('declared text-only Qwen Max is not an image Auto recipient while its text/document role remains',async()=>{
 const profile=JSON.parse(await readFile(new URL('../../protocol/model-service/model-profiles-v1.json',import.meta.url),'utf8')).profiles.find(p=>p.presetId==='QWEN_3_8_MAX');
 assert.equal(profile.capabilities.image,false);
 const max=service('QWEN',['QWEN_3_8_MAX']);
 for(const mime of ['image/png','image/jpeg','image/webp','image/gif'])assert.equal(autoCandidate([max],{mimeTypes:[mime]}),null,mime);
 for(const mime of ['text/plain','application/vnd.openxmlformats-officedocument.wordprocessingml.document'])assert.equal(autoCandidate([max],{mimeTypes:[mime]}).id,'QWEN_3_8_MAX');
 assert.equal(autoCandidate([max,service('OPENROUTER',['GEMINI_3_8_FLASH'])],{mimeTypes:['image/png']}).id,'GEMINI_3_8_FLASH');
 for(const id of ['QWEN_3_7_PLUS','QWEN_3_6_FLASH'])assert.equal(autoCandidate([service('QWEN',[id])],{mimeTypes:['image/png']}).id,id);
});

const registryForImage=(status='VERIFIED',image=true)=>({status,mappedPresetIds:['GEMINI_3_8_FLASH','GPT_6_LUNA'],mappedModelIds:['google/gemini-3.8-flash','openai/gpt-6-luna'],inputModalitiesByPreset:{GEMINI_3_8_FLASH:['text'],GPT_6_LUNA:image?['text','image']:['text']}});
test('verified and stable-fallback image modalities constrain Auto while text and unknown behavior stay intact',()=>{
 const settings=[service('OPENROUTER',['GEMINI_3_8_FLASH','GPT_6_LUNA'])];
 for(const status of ['VERIFIED','STABLE_FALLBACK'])assert.equal(autoCandidate(settings,{mimeTypes:['image/png'],openrouterRegistryStatus:registryForImage(status)}).id,'GPT_6_LUNA');
 assert.equal(autoCandidate(settings,{mimeTypes:['image/png'],openrouterRegistryStatus:registryForImage('VERIFIED',false)}),null);
 assert.equal(autoCandidate(settings,{openrouterRegistryStatus:registryForImage()}).id,'GPT_6_LUNA');
 assert.equal(autoCandidate([service('OPENROUTER',['GEMINI_3_8_FLASH'])],{openrouterRegistryStatus:registryForImage()}).id,'GEMINI_3_8_FLASH');
 for(const registry of [null,{status:'NOT_VERIFIED'},registryForImage('NOT_VERIFIED')])assert.equal(autoCandidate(settings,{mimeTypes:['image/png'],openrouterRegistryStatus:registry}).id,'GEMINI_3_8_FLASH');
});
test('real composer renderer displays the same image Auto recipient selected from verified capabilities',()=>{
 const settings=[service('OPENROUTER',['GEMINI_3_8_FLASH','GPT_6_LUNA'])];
 const html=renderChatFirstShell({data:{summary:{id:'fixture'},exchange:{conversations:[]}},native:true,pane:'chat',selectedConversationId:null,composerDraft:'查看合成图片',composerAttachments:[{id:'synthetic',mimeType:'image/png',displayName:'synthetic.png',byteCount:1}],chatSearch:'',connection:{},modelServiceSettings:settings,openrouterRegistryStatus:registryForImage()});
 assert.match(html,/aria-label="选择模型：GPT_6_LUNA"/);
 const unavailable=renderChatFirstShell({data:{summary:{id:'fixture'},exchange:{conversations:[]}},native:true,pane:'chat',selectedConversationId:null,composerDraft:'查看合成图片',composerAttachments:[{id:'synthetic',mimeType:'image/png',displayName:'synthetic.png',byteCount:1}],chatSearch:'',connection:{},modelServiceSettings:settings,openrouterRegistryStatus:registryForImage('VERIFIED',false)});
 assert.match(unavailable,/aria-label="选择模型：自动"/);
 assert.doesNotMatch(unavailable,/aria-label="选择模型：GPT_6_LUNA"/);
 const manual=renderChatFirstShell({data:{summary:{id:'fixture'},exchange:{conversations:[]}},native:true,pane:'chat',selectedConversationId:null,pendingNewConversationModelId:'GEMINI_3_8_FLASH',composerDraft:'查看合成图片',composerAttachments:[{id:'synthetic',mimeType:'image/png',displayName:'synthetic.png',byteCount:1}],chatSearch:'',connection:{},modelServiceSettings:settings,openrouterRegistryStatus:registryForImage()});
 assert.match(manual,/aria-label="选择模型：GEMINI_3_8_FLASH"/);
});
