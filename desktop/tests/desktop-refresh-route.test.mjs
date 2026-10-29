import { retainNewerConversationSelection } from '../src/composer-draft-recovery.mjs';
import test from 'node:test';
import assert from 'node:assert/strict';
import vm from 'node:vm';
import {readFile} from 'node:fs/promises';
const source=await readFile(new URL('../src/app.mjs',import.meta.url),'utf8');
const block=source.slice(source.indexOf('async function refresh() {'),source.indexOf('async function runP6eTemporaryMaintenanceAcceptance()'));
function harness(){
 const pending=[];let hold=true;let paints=0;
 const state={dataArea:'CHAT',runtimeInfo:{},entryResolved:true,current:{summary:{id:'w'}},selectedConversationId:'c',conversationPreferences:{revision:0},pane:'chat'};
 const context=vm.createContext({ retainNewerConversationSelection,state,native:true,desktopEntryRouteGeneration:0,cloudOpenAreaGeneration:0,desktopWorkspaceRefreshGeneration:0,
 desktopAppEntry:{read:()=>null},loadMarkdownKnowledgeTask:async()=>{},loadJsonKnowledgeTask:async()=>{},pdfKnowledge:{load:async()=>{}},webKnowledge:{load:async()=>{}},
 resolveConversation:(_,id)=>id?{id}:null,reloadFavoriteConversationIds(){},loadConversationReadState:async()=>{},workspaceUiEnabled:()=>false,loadTranscriptionState:async()=>({}),recomputeConversationFind(){},render(){paints++;},
 invoke:async command=>{
  if(command==='read_desktop_runtime_info'){if(hold){hold=false;return new Promise(resolve=>pending.push(resolve));}return {version:'new'};}
  if(command==='list_desktop_workspaces')return [{id:'w'}];
  if(command==='read_desktop_workspace')return {summary:{id:'w'}};
  if(command==='read_desktop_p6g_selection')return {conversationOverride:{modelId:'Sol'},globalDefault:{modelId:'Sol'}};
  if(command==='read_desktop_p6g_global_default')return {modelId:'Sol'};
  if(command==='read_p6e_temporary_maintenance_acceptance_status')return {enabled:false};
  return null;
 }});vm.runInContext(block,context);
 return {state,context,pending,get paints(){return paints;},refresh:()=>context.refresh()};
}
test('an earlier refresh cannot overwrite a newer complete refresh',async()=>{
 const h=harness();const old=h.refresh();await h.refresh();h.pending[0]({version:'old'});await old;
 assert.equal(h.state.runtimeInfo.version,'new');assert.equal(h.paints,1);
});
test('an earlier area visit cannot resume refresh after CHAT WORK CHAT',async()=>{
 const h=harness();const old=h.refresh();h.context.cloudOpenAreaGeneration+=2;h.state.runtimeInfo={version:'returned'};
 h.pending[0]({version:'old'});await old;assert.equal(h.state.runtimeInfo.version,'returned');assert.equal(h.paints,0);
});
test('refresh readback preserves a model tier opened while its read was pending',async()=>{
 const h=harness();const read=h.refresh();h.state.p6gSelection={pickerTier:'RELAYCORE'};h.pending[0]({version:'current'});await read;
 assert.equal(h.state.p6gSelection.pickerTier,'RELAYCORE');assert.equal(h.state.conversationPreferences.modelId,'Sol');
});
