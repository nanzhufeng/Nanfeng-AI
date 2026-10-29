import test from 'node:test';
import assert from 'node:assert/strict';
import { renderChatFirstShell, desktopComposerModelCandidates } from '../src/chat-shell.mjs';
import { MODEL_SERVICE_PREVIEW_SETTINGS } from '../src/android-settings-shell.mjs';

const mappings=[
 ['CLAUDE_OPUS_5','CLAUDE_OPUS_5_5'],['CLAUDE_SONNET_5','CLAUDE_SONNET_5_5'],['CLAUDE_HAIKU_4_5','CLAUDE_HAIKU_5_5'],
 ['GPT_5_6_SOL','GPT_6_1_SOL'],['GPT_5_6_TERRA','GPT_6_LUNA'],['GPT_5_6_LUNA','GPT_6_LUNA'],
 ['anthropic/claude-opus-5','CLAUDE_OPUS_5_5'],['claude-opus-5','CLAUDE_OPUS_5_5'],
 ['anthropic/claude-sonnet-5','CLAUDE_SONNET_5_5'],['claude-sonnet-5','CLAUDE_SONNET_5_5'],
 ['anthropic/claude-haiku-4.5','CLAUDE_HAIKU_5_5'],['claude-haiku-4.5','CLAUDE_HAIKU_5_5'],
 ['openai/gpt-5.6-sol','GPT_6_1_SOL'],['gpt-5.6-sol','GPT_6_1_SOL'],
 ['openai/gpt-5.6-terra','GPT_6_LUNA'],['gpt-5.6-terra','GPT_6_LUNA'],
 ['openai/gpt-5.6-luna','GPT_6_LUNA'],['gpt-5.6-luna','GPT_6_LUNA'],
 ['deepseek-v4-flash','DEEPSEEK_V4_FLASH'],['deepseek-v4-flash-vision-exp','DEEPSEEK_V4_FLASH'],
 ['GEMINI_3_7_FLASH','GEMINI_3_8_FLASH'],['google/gemini-3.7-flash','GEMINI_3_8_FLASH'],
];
const services=MODEL_SERVICE_PREVIEW_SETTINGS.map(p=>({...p,enabled:true,credentialStored:true}));
const selected=html=>[...html.matchAll(/<button\b[^>]*data-action="select-p6g-model"[^>]*>/g)].filter(m=>m[0].includes('aria-selected="true"')).map(m=>m[0].match(/data-model-id="([^"]+)"/)[1]);
function options(id,extra={}){return {data:{summary:{id:'legacy-owner'},exchange:{conversations:[{id:'legacy-chat',title:'synthetic',messages:[]}],projects:[],knowledge:[],memory:[],relations:[]}},native:true,pane:'chat',selectedConversationId:'legacy-chat',modelServiceSettings:services,p6gModelPickerOpen:true,p6gSelection:{conversationOverride:{modelId:id,revision:9},pickerTier:['CLAUDE_OPUS_5_5','GPT_6_1_SOL'].includes(mappings.find(x=>x[0]===id)?.[1]||id)?'DEEP':'DAILY'},...extra};}
for(const [oldId,id] of mappings)test(`historical choice ${oldId} displays its current menu identity without rewriting the choice`,()=>{
 const p=options(oldId),before=structuredClone(p),html=renderChatFirstShell(p);
 assert.deepEqual(selected(html),[id]);assert.deepEqual(p,before);
 const label=services.flatMap(s=>s.presets).find(p=>p.id===id).displayName;assert.ok(html.includes(label));
});
test('pending new-chat and saved global historical choices use the same current menu identity',()=>{
 for(const extra of [{pendingNewConversationModelId:'GPT_5_6_SOL'},{p6gGlobalDefault:{modelId:'GPT_5_6_SOL'}}]){
  const p=options(null,{selectedConversationId:null,p6gSelection:{pickerTier:'DEEP'},...extra}),before=structuredClone(p);assert.deepEqual(selected(renderChatFirstShell(p)),['GPT_6_1_SOL']);assert.deepEqual(p,before);
 }
});
test('an exact current RC or non-RC choice is not replaced by a similarly named provider',()=>{
 for(const [id,tier] of [['GPT_6_ASTRA','DEEP'],['RELAYCORE_GPT_6_ASTRA','RELAYCORE'],['GPT_6_1_SOL','DEEP']]){
  const p=options(id,{p6gSelection:{conversationOverride:{modelId:id},pickerTier:tier}}),before=structuredClone(p),html=renderChatFirstShell(p);
  assert.deepEqual(selected(html),id==='GPT_6_ASTRA'?[]:[id]);assert.deepEqual(p,before);
 }
});
test('unknown and OCR choices are never upgraded into a selectable chat model',()=>{
 for(const id of ['UNKNOWN_SYNTHETIC_MODEL','GLM_OCR'])assert.deepEqual(selected(renderChatFirstShell(options(id))),[]);
});
test('historical catalog aliases never reappear beside their current reviewed models',()=>{
 const historical=mappings.map(([modelId])=>({modelId,providerId:modelId.includes('deepseek')?'DEEPSEEK':'OPENROUTER',providerFamily:'OTHER',available:true,tiers:['DEEP'],displayName:'historical synthetic label'}));
 const rows=desktopComposerModelCandidates(services,historical);assert.equal(rows.length,22);assert.equal(new Set(rows.map(x=>x.modelId )).size,22);assert.ok(!rows.some(r=>mappings.some(([old])=>old===r.modelId)));
});
