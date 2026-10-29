import test from 'node:test';
import assert from 'node:assert/strict';
import vm from 'node:vm';
import {readFile} from 'node:fs/promises';

const source=await readFile(new URL('../src/app.mjs',import.meta.url),'utf8');
const resolver=source.slice(source.indexOf('function newConversationModelId('),source.indexOf('async function updateP6GConversationOverride('));
const send=source.slice(source.indexOf('async function sendLocalMessage()'),source.indexOf('saveLocalMessage = sendLocalMessage;'));
function harness(pending,remembered,existing=false,{choiceWait=null}={}){
 const state={dataArea:'CHAT',current:{summary:{id:'synthetic-workspace'}},selectedConversationId:existing?'existing-conversation':null,composerDraft:'synthetic exact send',composerAttachments:[],pendingNewConversationModelId:pending,p6gGlobalDefault:{modelId:remembered},conversationPreferences:{toneOverride:null,webSearchOverride:false},accountSync:{email:null,syncedConversationKeys:[]},pane:'chat'};
 const calls=[];let draftFlushes=0;
 let preview={draftFingerprint:'synthetic-fingerprint',recipientChoiceId:'GPT_6_1_SOL',recipientProviderId:'OPENROUTER',recipientModelId:'openai/gpt-6.1-sol'};
 const context=vm.createContext({state,native:true,tauriBridge:{},resolveConversation:()=>existing?{id:'existing-conversation',revision:1}:null,
  flushConversationChoiceSaves:async()=>{if(choiceWait)await choiceWait;},flushOrdinaryComposerDraft:async()=>{draftFlushes+=1;},
  invoke:async command=>{assert.equal(command,'read_desktop_conversation_draft');return{text:'synthetic exact send'};},
  currentOrdinaryEgressPreview:()=>preview,
  createChatSubmissionId:()=> 'synthetic-client',
  invokeForAuthorizedDataAreaOperation:async(_bridge,command,args)=>{calls.push({command,args});return{conversationId:existing?'existing-conversation':'created-conversation',state:'UNKNOWN'};},
  resetComposerPresentation:()=>{state.composerDraft='';state.composerAttachments=[];},render:()=>{},
  refreshDesktopRuntimeWhilePending:async()=>{},claimSubmittedChatRoute:()=>{},syncPersistedConversationContinuation:async()=>{},
  loadDesktopContextRecords:async()=>{},loadDesktopDiagnosticRecords:async()=>{},refresh:async()=>{},writeComposerAttachments:()=>{},
 });
 vm.runInContext(resolver+'\n'+send+'\nglobalThis.sendMessage=sendLocalMessage;',context);
 return{calls,state,get draftFlushes(){return draftFlushes;},setPreview(value){preview=value;},send:()=>context.sendMessage()};
}
for(const [name,pending,remembered,expected,existing] of [
 ['remembered manual',undefined,'GPT_6_1_SOL','GPT_6_1_SOL',false],
 ['explicit Auto',null,'GPT_6_1_SOL',null,false],
 ['pending manual overrides remembered', 'GPT_6_LUNA','GPT_6_1_SOL','GPT_6_LUNA',false],
 ['existing conversation keeps its owner selection',undefined,'GPT_6_1_SOL',null,true],
])test(`actual ordinary Send binds ${name} into the native request`,async()=>{
 const h=harness(pending,remembered,existing);await h.send();assert.equal(h.calls.length,1);assert.equal(h.calls[0].command,'submit_desktop_ordinary_chat');
 assert.equal(h.calls[0].args.args.modelId,expected);assert.equal(h.calls[0].args.args.text,'synthetic exact send');
});

const turn=()=>new Promise(resolve=>setImmediate(resolve));
function gate(){let resolve,reject;const promise=new Promise((yes,no)=>{resolve=yes;reject=no;});return{promise,resolve,reject};}

test('Send waits for the model write and binds its confirmed selection',async()=>{
 const held=gate(),h=harness(undefined,'RELAYCORE_GPT_6_1_SOL',false,{choiceWait:held.promise});const sending=h.send();await turn();
 assert.equal(h.calls.length,0);assert.equal(h.draftFlushes,0);assert.equal(h.state.ordinarySendPreparing,true);
 h.state.pendingNewConversationModelId='RELAYCORE_GPT_6_ASTRA';h.state.p6gGlobalDefault.modelId='RELAYCORE_GPT_6_ASTRA';
 h.setPreview({draftFingerprint:'confirmed-astra',recipientChoiceId:'RELAYCORE_GPT_6_ASTRA',recipientProviderId:'RELAYCORE_GPT',recipientModelId:'gpt-6-astra'});
 held.resolve();await sending;assert.equal(h.calls.length,1);assert.equal(h.calls[0].args.args.modelId,'RELAYCORE_GPT_6_ASTRA');assert.equal(h.calls[0].args.args.text,'synthetic exact send');assert.equal(h.draftFlushes,1);
});

test('a failed pending model write stops Send and preserves the original draft',async()=>{
 const held=gate(),h=harness(undefined,'RELAYCORE_GPT_6_1_SOL',false,{choiceWait:held.promise});const sending=h.send();await turn();assert.equal(h.calls.length,0);
 held.reject(new Error('synthetic model write failed'));await sending;assert.equal(h.calls.length,0);assert.equal(h.draftFlushes,0);assert.equal(h.state.composerDraft,'synthetic exact send');assert.match(h.state.error,/草稿或会话设置未能确认保存/);assert.equal(h.state.ordinarySendPreparing,false);
});

test('editing material while Send waits for the model never authorizes the newer text',async()=>{
 const held=gate(),h=harness(undefined,'RELAYCORE_GPT_6_1_SOL',false,{choiceWait:held.promise});const sending=h.send();await turn();h.state.composerDraft='newer unsent material';held.resolve();await sending;
 assert.equal(h.calls.length,0);assert.equal(h.state.composerDraft,'newer unsent material');assert.match(h.state.status,/输入内容已变化/);assert.equal(h.state.ordinarySendPreparing,false);
});
