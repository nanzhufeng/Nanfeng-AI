import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {AUTO_PLANS,autoCandidate,autoTask,conversationAutoMimeTypes} from '../src/auto-chat-routing.mjs';
const policy=JSON.parse(readFileSync(new URL('../../protocol/fixtures/auto-routing-rc-domestic-20261010.json',import.meta.url)));
const profiles=JSON.parse(readFileSync(new URL('../../app/src/main/assets/model_profiles.json',import.meta.url))).profiles;
const service=(providerId,ids,enabled=true,credentialStored=true)=>({providerId,enabled,credentialStored,presets:ids.map(id=>({id,displayName:id,chatSelectable:true,modelId:profiles.find(p=>p.presetId===id)?.modelId,nativeInputTypes:Object.entries(profiles.find(p=>p.presetId===id)?.capabilities||{}).filter(([k,v])=>v && ["image","pdf","audio","video"].includes(k)).map(([k])=>k==="pdf"?"file":k)}))});
const services=[service('RELAYCORE_GPT',['RELAYCORE_GPT_6_1_SOL','RELAYCORE_GPT_6_ASTRA']),service('RELAYCORE_GEMINI',['RELAYCORE_GEMINI_3_8_FLASH']),service('DEEPSEEK',['DEEPSEEK_V4_FLASH','DEEPSEEK_V4_PRO']),service('QWEN',['QWEN_3_7_PLUS']),service('OPENROUTER',policy.openRouter)];
test('shared Auto plans keep OpenRouter behind RC and domestic candidates',()=>{
 assert.deepEqual(AUTO_PLANS,policy.plans);
 for(const plan of Object.values(AUTO_PLANS)){let fallback=false;for(const id of plan){if(policy.openRouter.includes(id))fallback=true;else assert.equal(fallback,false);}}
});
test('actual available Auto model depends on task and complete credentials',()=>{
 assert.equal(autoCandidate(services).id,'RELAYCORE_GPT_6_1_SOL');
 assert.equal(autoCandidate(services,{text:'架构设计与权衡'}).id,'RELAYCORE_GPT_6_ASTRA');
 assert.equal(autoCandidate(services,{mimeTypes:['image/png']}).id,'QWEN_3_7_PLUS');
 assert.equal(autoCandidate(services,{mimeTypes:['image/png'],webSearchEnabled:true}).providerId,'OPENROUTER');
 assert.equal(autoCandidate(services.filter(s=>s.providerId!=='OPENROUTER'),{mimeTypes:['image/png'],webSearchEnabled:true}),null);
 assert.equal(autoCandidate(services,{mimeTypes:['application/pdf']}).providerId,'OPENROUTER');
 assert.equal(autoCandidate(services.filter(s=>s.providerId!=='OPENROUTER' && s.providerId!=='RELAYCORE_GPT'),{webSearchEnabled:true}).id,'DEEPSEEK_V4_FLASH');
 assert.equal(autoCandidate(services.filter(s=>s.providerId!=='RELAYCORE_GPT')).id,'RELAYCORE_GEMINI_3_8_FLASH');
 assert.equal(autoCandidate(services.map(s=>({...s,credentialStored:false}))),null);
});
test('classifier and branch attachment facts exclude unrelated branches',()=>{
 assert.equal(autoTask('分析一下这家公司'),'DEFAULT_TEXT'); assert.equal(autoTask('debugging'), 'DEFAULT_TEXT'); assert.equal(autoTask('debug this'),'COMPLEX_REASONING');assert.equal(autoTask('a'.repeat(2400)),'COMPLEX_REASONING');
 const conversation={currentLeafId:'a',messages:[{id:'a',parentId:'b',blocks:[]},{id:'b',blocks:[{kind:'ASSET_REF',asset:{mimeType:'image/png'}}]},{id:'other',blocks:[{kind:'ASSET_REF',asset:{mimeType:'video/mp4'}}]}]};
 assert.deepEqual(conversationAutoMimeTypes(conversation,[{mimeType:'application/pdf'}]),['application/pdf','image/png']);
});
