import test from 'node:test';
import assert from 'node:assert/strict';
import vm from 'node:vm';
import {readFile} from 'node:fs/promises';
import {readComposerDraftRoute} from '../src/composer-draft-recovery.mjs';
const source=await readFile(new URL('../src/app.mjs',import.meta.url),'utf8');
const loader=source.slice(source.indexOf('async function loadOrdinaryComposerDraft('),source.indexOf('function writeComposerAttachments('));
const resetStart=source.indexOf('function restoreOrdinaryComposerDraft(')>=0?source.indexOf('function restoreOrdinaryComposerDraft('):source.indexOf('function resetNewConversationComposer(');
const reset=source.slice(resetStart,source.indexOf('function scheduleOrdinaryComposerDraft('));
const listener=source.slice(source.indexOf('// Composer drafts have exactly one route owner.'),source.indexOf('const MAX_COMPOSER_ATTACHMENT_BYTES'));
const settle=()=>new Promise(resolve=>setImmediate(resolve));
function fixture(){
 let release,reject,handler,renders=0;const delayed=new Promise((yes,no)=>{release=yes;reject=no;});
 const state={dataArea:'CHAT',pane:'chat',dialog:null,current:{summary:{id:'workspace'}},selectedConversationId:null,temporaryConversation:null,composerDraft:'',composerAttachments:[],conversationPreferences:{},error:''};
 let context;context=vm.createContext({state,native:true,desktopEntryRouteGeneration:0,composerDraftRouteGeneration:0,readComposerDraftRoute,
  ordinaryComposerDraftArgs:()=>({workspaceId:'workspace',conversationId:state.selectedConversationId}),
  flushOrdinaryComposerDraft:async()=>{},armNewConversationDraftExpiry(){},render(){renders++;},
  resetComposerPresentation(){context.composerDraftRouteGeneration++;state.composerDraft='';state.composerAttachments=[];},
  invoke:async(_command,args)=>args.conversationId==='destination'?{text:'destination saved draft',attachments:[]}:delayed,
  app:{addEventListener(_name,run){handler=run;}}});
 vm.runInContext(reset+'\n'+loader+'\n'+listener,context);
 return {state,context,release,reject,get renders(){return renders;},
  select(id){state.selectedConversationId=id;context.desktopEntryRouteGeneration++;handler({target:{closest:()=>({dataset:{action:id?'select-chat':'new-chat'}})}});}};
}
for(const original of [null,'original'])test(`public ${original?'conversation':'new-chat'} read completion cannot repaint a newer destination`,async()=>{
 const h=fixture();h.select(original);await settle();h.select('destination');await settle();
 const renders=h.renders;assert.equal(h.state.composerDraft,'destination saved draft');
 h.release({text:'old saved text',attachments:[]});await settle();
 assert.equal(h.state.composerDraft,'destination saved draft');assert.equal(h.state.selectedConversationId,'destination');assert.equal(h.renders,renders);
});
for(const original of [null,'original'])test(`public ${original?'conversation':'new-chat'} read failure cannot overwrite a newer dialog`,async()=>{
 const h=fixture();h.select(original);await settle();h.state.dialog={kind:'new-dialog'};h.state.error='current dialog status';const renders=h.renders;
 h.reject(new Error('old read failure'));await settle();
 assert.equal(h.state.error,'current dialog status');assert.equal(h.renders,renders);
});
test('the current public draft failure still reports its cause and retains newly typed text',async()=>{
 const h=fixture();h.select(null);await settle();h.state.composerDraft='newer typed text';h.context.composerDraftRouteGeneration++;
 h.reject(new Error('current read failure'));await settle();
 assert.match(h.state.error,/新对话草稿未读取.*current read failure/);assert.equal(h.state.composerDraft,'newer typed text');
});
