import test from 'node:test';
import assert from 'node:assert/strict';
import vm from 'node:vm';
import {readFile} from 'node:fs/promises';
const source=await readFile(new URL('../src/app.mjs',import.meta.url),'utf8');
const start=source.indexOf("app.addEventListener('click', async event => { const target = event.target.closest('[data-action]'); const action = target?.dataset.action; if (!action) return; const data = workspace();");
const handler=source.slice(start,source.indexOf('\n',start));
const helperStart=source.indexOf('async function selectOrdinaryConversation(target) {');
const helper=helperStart<0?'':source.slice(helperStart,source.indexOf("\napp.addEventListener('click', async event => { const target",helperStart));
function harness(){
 let click;const pending=[];const state={dataArea:'CHAT',pane:'chat',current:{summary:{id:'ws'}},selectedConversationId:'original',conversationPreferences:{modelId:'original'},chatScrollPositions:new Map()};
 const ctx=vm.createContext({state,native:true,desktopEntryRouteGeneration:0,cloudOpenAreaGeneration:0,workspace:()=>state.current,
 app:{addEventListener:(_,fn)=>click=fn},render(){},recomputeConversationFind(){},markConversationOpened:async()=>{},
 applyConversationChoiceProjection:p=>{ctx.state.p6gSelection=p;ctx.state.conversationPreferences=p.conversationOverride;ctx.state.p6gGlobalDefault=p.globalDefault;},
 invoke:(command,args,owner)=>new Promise((resolve,reject)=>pending.push({command,args,owner,resolve,reject}))});
 vm.runInContext(helper+handler,ctx);
 return {state,ctx,pending,click:action=>click({target:{closest:()=>({dataset:{action:action==='new-chat'?action:'select-chat',id:action},closest:()=>null})}})};
}
const projection=id=>({conversationOverride:{modelId:id},globalDefault:{modelId:id}});
test('late conversation preferences cannot replace a newly selected conversation',async()=>{
 const h=harness();const a=h.click('a');const b=h.click('b');h.pending[1].resolve(projection('b'));await b;
 h.state.composerDraft='new draft';h.pending[0].resolve(projection('a'));await a;
 assert.equal(h.state.selectedConversationId,'b');assert.equal(h.state.conversationPreferences.modelId,'b');assert.equal(h.state.composerDraft,'new draft');
});
test('late conversation preferences cannot replace new-chat preferences',async()=>{
 const h=harness();const a=h.click('a');await h.click('new-chat');h.state.pendingNewConversationModelId='Sol';
 h.pending[0].resolve(projection('a'));await a;assert.equal(h.state.selectedConversationId,null);assert.equal(h.state.conversationPreferences.modelId,undefined);assert.equal(h.state.pendingNewConversationModelId,'Sol');
});
test('same-ID route in another area never receives the old reply',async()=>{
 const h=harness();const a=h.click('a');const next={dataArea:'WORK',current:{summary:{id:'ws'}},selectedConversationId:'a',conversationPreferences:{modelId:'work'}};h.ctx.state=next;
 h.pending[0].resolve(projection('a'));await a;assert.equal(next.conversationPreferences.modelId,'work');
});
test('rejection after navigation does not report an error on the destination',async()=>{
 const h=harness();const a=h.click('a');await h.click('new-chat');h.state.error='destination';h.pending[0].reject(new Error('old read failed'));await a;assert.equal(h.state.error,'destination');
});
test('CHAT to WORK to CHAT cannot revive a read from the earlier area visit',async()=>{
 const h=harness();const a=h.click('a');h.ctx.cloudOpenAreaGeneration+=2;
 h.state.conversationPreferences={modelId:'returned-route'};h.pending[0].resolve(projection('a'));await a;
 assert.equal(h.state.conversationPreferences.modelId,'returned-route');
});

test('failure on the active route is visible and keeps its neutral preference projection',async()=>{
 const h=harness();const a=h.click('a');h.pending[0].reject(new Error('active read failed'));await a;
 assert.match(h.state.error,/会话偏好未读取.*active read failed/);assert.equal(h.state.p6gSelection,null);
});

test('opening a model tier during preference loading keeps that menu on readback',async()=>{
 const h=harness();const a=h.click('a');h.state.p6gSelection={pickerTier:'RELAYCORE'};
 h.pending[0].resolve(projection('a'));await a;assert.equal(h.state.p6gSelection.pickerTier,'RELAYCORE');
});
