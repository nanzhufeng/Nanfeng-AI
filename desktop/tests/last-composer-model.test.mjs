import test from 'node:test';
import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import vm from 'node:vm';
import {renderChatFirstShell} from '../src/chat-shell.mjs';
import {MODEL_SERVICE_PREVIEW_SETTINGS} from '../src/android-settings-shell.mjs';
const source = await readFile(new URL('../src/app.mjs', import.meta.url), 'utf8');
const owner = source.slice(source.indexOf('function newConversationModelId()'), source.indexOf('async function updateConversationPreferences('));
function session({modelId='RELAYCORE_SONNET_5_5', fail=false}={}) {
  const state={current:{summary:{id:'workspace'}},selectedConversationId:null,p6gGlobalDefault:{modelId,composerRevision:1},p6gCatalog:null};
  let persisted={...state.p6gGlobalDefault};
  const context=vm.createContext({state,native:true,render:()=>{},refresh:async()=>{},invoke:async(command,args)=>{
    if(command==='set_desktop_new_conversation_model') {
      if(fail) throw new Error('disk unavailable');
      assert.equal(args.expectedRevision,persisted.composerRevision);
      persisted={modelId:args.modelId,composerRevision:persisted.composerRevision+1};
      return {revision:persisted.composerRevision};
    }
    if(command==='read_desktop_p6g_global_default') return {...persisted};
    throw new Error(command);
  }});
  vm.runInContext(owner+'\nthis.api={newConversationModelId,updateP6GConversationOverride};',context);
  return {state,api:context.api};
}
test('new chat inherits durable last choice; explicit Auto is durable and never nullish-falls back',async()=>{
  const {state,api}=session();
  assert.equal(api.newConversationModelId(),'RELAYCORE_SONNET_5_5');
  await api.updateP6GConversationOverride(null);
  assert.equal(api.newConversationModelId(),null);
  assert.equal(state.p6gGlobalDefault.modelId,null);
  state.pendingNewConversationModelId=undefined;
  assert.equal(api.newConversationModelId(),null);
  await api.updateP6GConversationOverride('GLM_5_3');
  state.pendingNewConversationModelId=undefined;
  assert.equal(api.newConversationModelId(),'GLM_5_3');
});
test('failed save preserves last model and reports failure instead of changing selection',async()=>{
  const {state,api}=session({fail:true});
  await api.updateP6GConversationOverride('GLM_5_3');
  assert.equal(api.newConversationModelId(),'RELAYCORE_SONNET_5_5');
  assert.match(state.error,/模型选择未保存/);
});
const base={data:{summary:{id:'workspace'},exchange:{conversations:[]}},native:true,pane:'chat',status:'',error:'',connection:{},modelServiceSettings:MODEL_SERVICE_PREVIEW_SETTINGS};
test('visible Composer uses remembered model only for new conversations and respects explicit Auto',()=>{
  const remembered=renderChatFirstShell({...base,p6gGlobalDefault:{modelId:'RELAYCORE_SONNET_5_5'}});
  assert.match(remembered,/Sonnet 5\.5/);
  const auto=renderChatFirstShell({...base,p6gGlobalDefault:{modelId:'RELAYCORE_SONNET_5_5'},pendingNewConversationModelId:null});
  assert.doesNotMatch(auto,/Sonnet 5\.5/);
  const first=renderChatFirstShell(base);
  assert.match(first,/等待可用模型/);
  assert.equal(first,auto);
});
