import test from 'node:test';
import assert from 'node:assert/strict';
import vm from 'node:vm';
import {readFile} from 'node:fs/promises';
import {readComposerDraftRoute} from '../src/composer-draft-recovery.mjs';
const source=await readFile(new URL('../src/app.mjs',import.meta.url),'utf8');
const argsOwner=source.slice(source.indexOf('function ordinaryComposerDraftArgs('),source.indexOf('function resetComposerPresentation('));
const scheduler=source.slice(source.indexOf('function scheduleOrdinaryComposerDraft('),source.indexOf('async function flushOrdinaryComposerDraft('));
const loader=source.slice(source.indexOf('async function loadOrdinaryComposerDraft('),source.indexOf('function writeComposerAttachments('));
const model=source.slice(source.indexOf('function newConversationModelId('),source.indexOf('async function updateP6GConversationOverride('));
const ASTRA='RELAYCORE_GPT_6_ASTRA',SOL='RELAYCORE_GPT_6_1_SOL';
function deferred(){let resolve;const promise=new Promise(yes=>{resolve=yes;});return{promise,resolve};}
function harness({conversationId=null}={}){
 const read=deferred(),started=deferred(),writes=[];
 const state={dataArea:'CHAT',current:{summary:{id:'synthetic-workspace'}},selectedConversationId:conversationId,temporaryConversation:null,composerDraft:'',composerAttachments:[],pendingNewConversationModelId:undefined,p6gGlobalDefault:{modelId:ASTRA},conversationPreferences:{toneOverride:null,webSearchOverride:null}};
 const context=vm.createContext({state,native:true,composerDraftRouteGeneration:0,desktopEntryRouteGeneration:0,pendingOrdinaryComposerDraftRead:null,readComposerDraftRoute,
  flushOrdinaryComposerDraft:async()=>{},armNewConversationDraftExpiry(){},render(){},
  invoke:async()=>{started.resolve();return read.promise;},
  ordinaryComposerDraftQueue:{save:async args=>{writes.push(structuredClone(args));return{...args,attachments:[]};}},
  setTimeout(){throw Error('The real scheduler must merge into the active read');},clearTimeout(){},
 });
 vm.runInContext(model+'\n'+argsOwner+'\n'+scheduler+'\n'+loader,context);
 return{state,writes,release:read.resolve,started:started.promise,load:()=>context.loadOrdinaryComposerDraft(),
  type(text){state.composerDraft=text;context.scheduleOrdinaryComposerDraft('text');},
  choose(id){state.pendingNewConversationModelId=id;state.p6gGlobalDefault.modelId=id;context.scheduleOrdinaryComposerDraft('model');}};
}
test('typing during an absent new draft read inherits the remembered model instead of inventing Auto',async()=>{
 const h=harness(),loading=h.load();await h.started;h.type('newly typed');h.release(null);await loading;
 assert.equal(h.writes.length,1);assert.equal(h.writes[0].modelId,ASTRA);assert.equal(h.state.pendingNewConversationModelId,ASTRA);assert.equal(h.state.composerDraft,'newly typed');
});
test('an existing draft explicit Auto remains null despite a remembered manual model',async()=>{
 const h=harness(),loading=h.load();await h.started;h.type('newly typed');h.release({text:'old text',attachments:[],modelId:null});await loading;
 assert.equal(h.writes[0].modelId,null);assert.equal(h.state.pendingNewConversationModelId,null);assert.equal(h.state.composerDraft,'newly typed');
});
test('an existing manual draft keeps its own model while newer text is merged',async()=>{
 const h=harness(),loading=h.load();await h.started;h.type('newly typed');h.release({text:'old text',attachments:[],modelId:SOL});await loading;
 assert.equal(h.writes[0].modelId,SOL);assert.equal(h.state.pendingNewConversationModelId,SOL);
});
test('a newer explicit model selection wins while an absent draft read completes',async()=>{
 const h=harness(),loading=h.load();await h.started;h.type('newly typed');h.choose(SOL);h.release(null);await loading;
 assert.equal(h.writes[0].modelId,SOL);assert.equal(h.state.pendingNewConversationModelId,SOL);assert.equal(h.state.composerDraft,'newly typed');
});
test('an existing conversation never copies a new-chat preference into its draft',async()=>{
 const h=harness({conversationId:'existing-conversation'}),loading=h.load();await h.started;h.type('conversation edit');h.release(null);await loading;
 assert.equal(h.writes[0].conversationId,'existing-conversation');assert.equal(h.writes[0].modelId,null);assert.equal(h.state.selectedConversationId,'existing-conversation');
});
