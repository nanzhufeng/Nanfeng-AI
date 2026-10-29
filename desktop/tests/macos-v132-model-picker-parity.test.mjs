import test from 'node:test';
import assert from 'node:assert/strict';
import {renderChatFirstShell,composerModelRuntimeDetail} from '../src/chat-shell.mjs';
import {MODEL_SERVICE_PREVIEW_SETTINGS} from '../src/android-settings-shell.mjs';
// Fixed against Mac release 7fc45689117302c01fbb73d0e2eddcaf2014d25d.
const all=MODEL_SERVICE_PREVIEW_SETTINGS.map(s=>s.providerId);
const render=({enabled=all,tier='RELAYCORE',manual=null,web=true}={})=>renderChatFirstShell({
 data:{summary:{id:'owned-model-parity'},exchange:{conversations:[],projects:[],knowledge:[],memory:[],relations:[]}},
 native:true,pane:'chat',connection:{},composerDraft:'',pendingNewConversationModelId:manual,
 modelServiceSettings:MODEL_SERVICE_PREVIEW_SETTINGS.map(s=>({...s,enabled:enabled.includes(s.providerId)})),
 p6gModelPickerOpen:true,p6gSelection:{pickerTier:tier},productSettings:{webSearchEnabled:web},
});
const sheet=html=>html.match(/<section class="composer-transient-sheet composer-model-sheet"[\s\S]*?<\/section>/)[0];
const ids=html=>[...sheet(html).matchAll(/data-model-id="([^"]+)"/g)].map(m=>m[1]);
test('Mac RelayCore catalog is grouped daily before deep with all six exact presets',()=>{
 const html=sheet(render());
 assert.deepEqual(ids(render()),['RELAYCORE_SONNET_5_5','RELAYCORE_GEMINI_3_8_FLASH','RELAYCORE_OPUS_5_5','RELAYCORE_GPT_6_1_SOL','RELAYCORE_GPT_6_ASTRA','RELAYCORE_GEMINI_3_1_PRO']);
 assert.ok(html.indexOf('>日常</small>')<html.indexOf('RELAYCORE_SONNET_5_5'));
 assert.ok(html.indexOf('>深度</small>')<html.indexOf('RELAYCORE_OPUS_5_5'));
 for(const label of ['Sonnet 5.5 · 0.35×','Gemini 3.8 Flash · 0.28×','Opus 5.5 · 0.35×','GPT-6.1 Sol · 0.25×','6 Astra · 0.25×','Gemini 3.1 Pro · 0.28×'])assert.ok(html.includes(label),label);
});
test('only enabled RelayCore families appear; Claude-only remains Sonnet then Opus',()=>{
 assert.deepEqual(ids(render({enabled:['OPENROUTER','RELAYCORE_CLAUDE']})),['RELAYCORE_SONNET_5_5','RELAYCORE_OPUS_5_5']);
 const root=sheet(render({enabled:['OPENROUTER'],tier:null}));
 assert.ok(!root.includes('data-tier="RELAYCORE"'));
});
test('RelayCore search subtitle follows Mac and never replaces the selected answer identity',()=>{
 assert.equal(composerModelRuntimeDetail({providerId:'RELAYCORE_CLAUDE',modelId:'RELAYCORE_OPUS_5_5'},true).text,'Luna · OpenRouter 检索');
 assert.equal(composerModelRuntimeDetail({providerId:'RELAYCORE_GPT',modelId:'RELAYCORE_GPT_6_1_SOL'},true).text,'实时联网');
 assert.equal(composerModelRuntimeDetail({providerId:'RELAYCORE_GEMINI',modelId:'RELAYCORE_GEMINI_3_1_PRO'},false).text,'未联网');
 const html=render({manual:'RELAYCORE_OPUS_5_5'});
 assert.ok(html.includes('aria-label="选择模型：Opus 5.5 · 0.35×"'));
 assert.ok(html.includes('aria-description="RelayCore"'));
 assert.ok(!sheet(html).includes('再由所选 RC 模型回答'));
});
test('Mac ordinary daily and deep catalog order remains separate from RelayCore',()=>{
 assert.deepEqual(ids(render({tier:'DAILY'})),['CLAUDE_SONNET_5_5','CLAUDE_HAIKU_5_5','DEEPSEEK_V4_FLASH','GPT_6_1_TERRA','GPT_6_LUNA','MERCURY_3','GLM_5_3_FLASH','QWEN_3_7_PLUS','GEMINI_3_8_FLASH']);
 assert.deepEqual(ids(render({tier:'DEEP'})),['CLAUDE_OPUS_5_5','GPT_6_1_SOL','DEEPSEEK_V4_PRO','MUSE_WONDER','GEMINI_4_ARGON','GLM_5_3','QWEN_3_8_MAX']);
});
test('automatic RelayCore choice has one visible provider mark',()=>{
 const html=renderChatFirstShell({data:{summary:{id:'owned-auto-caption'},exchange:{conversations:[]}},native:true,pane:'chat',connection:{},composerDraft:'你好',pendingNewConversationModelId:null,
 modelServiceSettings:MODEL_SERVICE_PREVIEW_SETTINGS.map(s=>({...s,enabled:true,credentialStored:true}))});
 const trigger=html.match(/<button[^>]*data-action="toggle-p6g-model-picker"[\s\S]*?<\/button>/)[0];
 const visible=trigger.replace(/<[^>]*>/g,'');assert.equal((visible.match(/RC/g)||[]).length,1);
});
