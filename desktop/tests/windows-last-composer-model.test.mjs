import test from 'node:test';
import assert from 'node:assert/strict';
import {renderChatFirstShell} from '../src/chat-shell.mjs';
const settings=[{providerId:'RELAYCORE_GPT',enabled:true,credentialStored:true,presets:[{id:'RELAYCORE_GPT_6_ASTRA',displayName:'Astra',chatSelectable:true},{id:'RELAYCORE_GPT_6_1_SOL',displayName:'Sol',chatSelectable:true}]}];
const render=extra=>renderChatFirstShell({data:{summary:{id:'synthetic'},exchange:{conversations:[]}},native:true,pane:'chat',selectedConversationId:null,composerDraft:'你好',chatSearch:'',connection:{},modelServiceSettings:settings,p6gSelection:{conversationOverride:{modelId:'RELAYCORE_GPT_6_ASTRA'}},p6gGlobalDefault:{modelId:'RELAYCORE_GPT_6_ASTRA',composerRevision:1},...extra});
test('explicit null Auto never falls back to an old conversation or remembered manual model',()=>assert.match(render({pendingNewConversationModelId:null}),/aria-label="选择模型：[^"\n]*Sol[^"\n]*RC"/));
test('undefined new-chat choice restores the area-local remembered model with its canonical RelayCore label',()=>assert.match(render({pendingNewConversationModelId:undefined}),/aria-label="选择模型：6 Astra · 0.25×"/));
