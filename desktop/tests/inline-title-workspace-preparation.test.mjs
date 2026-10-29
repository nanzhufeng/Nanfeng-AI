import test from 'node:test';
import assert from 'node:assert/strict';
import vm from 'node:vm';
import {readFile} from 'node:fs/promises';
import {createInlineTitleEditor} from '../src/inline-conversation-title.mjs';
const source=await readFile(new URL('../src/app.mjs',import.meta.url),'utf8');
const prepareSource=source.slice(source.indexOf('async function ensureConversationActionWorkspace('),source.indexOf('\n// Cloud-list actions may briefly load',source.indexOf('async function ensureConversationActionWorkspace(')));
const titleBinding=source.slice(source.indexOf('inlineTitleEditor = createInlineTitleEditor('));
const prepareBinding=titleBinding.slice(titleBinding.indexOf('\n  prepare:')+11,titleBinding.indexOf('\n  mutate:')).trim().replace(/,$/,'');
function fixture(){
 const original={summary:{id:'local'}};const state={current:original,selectedConversationId:'local-conversation',pane:'chat',dialog:null};
 let release,route=0;const response=new Promise(resolve=>release=resolve);let renders=0;
 const context=vm.createContext({state,native:true,invoke:async command=>{await response;return command==='read_desktop_workspace'?{summary:{id:'remote'}}:[];},reloadFavoriteConversationIds(){},loadConversationReadState:async()=>{}});
 vm.runInContext(prepareSource,context);
 vm.runInContext('globalThis.prepareTitle='+prepareBinding,context);
 const editor=createInlineTitleEditor({host:{addEventListener(){},querySelector(){return null;}},document:{},getState:()=>context.state,routeKey:()=>String(route),native:true,render(){renders++;},mutate:async()=>{throw Error('Opening must not mutate');},
  prepare:(target,options)=>context.prepareTitle(target,options)});
 return {state,original,context,release,editor,navigate(){route++;},get renders(){return renders;},open:()=>editor.begin({id:'remote-conversation',workspaceId:'remote',revision:3,title:'远端标题'})};
}
test('real workspace preparation cannot switch the title page after a new dialog or navigation',async()=>{
 for(const alter of [h=>h.state.dialog={kind:'new-dialog'},h=>h.state.batchEditing=true,h=>h.state.settingsConversationReturn={readOnly:true},h=>h.navigate()]){
  const h=fixture();const opening=h.open();alter(h);h.release();await opening;
  assert.equal(h.state.current,h.original,'stale preparation must not switch the underlying workspace');
  assert.equal(h.state.selectedConversationId,'local-conversation');assert.equal(h.state.inlineConversationRename,undefined);assert.equal(h.renders,0);
 }
});
test('an active title entry can still prepare its exact cloud workspace and open the editor',async()=>{
 const h=fixture();const opening=h.open();h.release();await opening;
 assert.equal(h.state.current.summary.id,'remote');assert.equal(h.state.selectedConversationId,null);
 assert.equal(h.state.inlineConversationRename.id,'remote-conversation');assert.equal(h.state.inlineConversationRename.workspaceId,'remote');assert.equal(h.renders,1);
});
