import test from 'node:test';
import assert from 'node:assert/strict';
import vm from 'node:vm';
import {readFile} from 'node:fs/promises';
import {DesktopAppEntry} from '../src/desktop-app-entry.mjs';
import {hasRunningGeneration} from '../src/generation-continuity.mjs';
const src=await readFile(new URL('../src/app.mjs',import.meta.url),'utf8');
const code=src.slice(src.indexOf('function rememberDesktopExit()'),src.indexOf("window.addEventListener('pagehide', () => {",src.indexOf('function rememberDesktopExit()')));
function harness({flush=async()=>{}}={}){
 let now=1800000000000,resets=0,renders=0;const handlers={},appHandlers={};const records=new Map();
 const state={dataArea:'CHAT',pane:'settings',entryResolved:true,current:{summary:{id:'ws'},exchange:{conversations:[]}},selectedConversationId:'old',conversationPreferences:{modelId:'old'},composerDraft:'retained body'};
 const target={addEventListener:(name,fn)=>{(handlers[name]??=[]).push(fn)}};
 const context=vm.createContext({native:true,state,dataAreaStates:new Map([['CHAT',state]]),desktopEntryRouteGeneration:0,desktopEntryExitRoute:null,
 desktopAppEntry:new DesktopAppEntry({setItem:(k,v)=>records.set(k,v),getItem:k=>records.get(k)},()=>now),hasRunningGeneration,
 // Media checkpointing has its own owner tests; this harness isolates entry events.
 checkpointVideoOnExit(){},checkpointAudioOnExit(){},flushOrdinaryComposerDraft:flush,resetNewConversationComposer:()=>resets++,recomputeConversationFind(){},render(){renders++;},window:target,document:{...target,hidden:false},app:{addEventListener(name,fn){(appHandlers[name]??=[]).push(fn);}},});
 vm.runInContext(code,context);
 return {state,records,context,get resets(){return resets;},get renders(){return renders;},advance:ms=>now+=ms,fire:name=>{for(const fn of handlers[name]||[])fn();},visibility:hidden=>{context.document.hidden=hidden;},action:name=>{for(const fn of appHandlers.click||[])fn({target:{closest:()=>({dataset:{action:name}})}});}};
}

test('a newer accepted navigation owns the route when the old background deadline expires',()=>{
 const h=harness();h.fire('blur');h.advance(900000);h.action('select-chat');
 h.state.selectedConversationId='new-target';h.state.composerDraft='new-target draft';h.state.pane='chat';
 h.fire('focus');h.fire('focus');
 assert.equal(h.state.selectedConversationId,'new-target');assert.equal(h.state.composerDraft,'new-target draft');assert.equal(h.resets,0);
});

test('explicitly reopening the same conversation consumes the older leave deadline',()=>{
 const h=harness();h.fire('blur');h.advance(900000);h.action('select-chat');h.fire('focus');
 assert.equal(h.state.selectedConversationId,'old');assert.equal(h.resets,0);
 h.fire('blur');h.advance(900000);h.fire('focus');assert.equal(h.state.selectedConversationId,null);assert.equal(h.resets,1);
});

for(const change of ['workspace','conversation','pane','dialog','area'])test(`old idle expiry cannot replace a newer ${change} context`,()=>{
 const h=harness();h.fire('blur');h.advance(900000);
 if(change==='workspace')h.state.current={summary:{id:'new-workspace'},exchange:{conversations:[]}};
 if(change==='conversation')h.state.selectedConversationId='new-conversation';
 if(change==='pane')h.state.pane='new-pane';
 if(change==='dialog')h.state.dialog={kind:'new-dialog'};
 if(change==='area')h.context.state={...h.state,dataArea:'WORK',pane:'work',selectedConversationId:'work-target'};
 const owner=h.context.state,expected={pane:owner.pane,conversation:owner.selectedConversationId,dialog:owner.dialog};
 h.fire('focus');assert.equal(owner.pane,expected.pane);assert.equal(owner.selectedConversationId,expected.conversation);assert.equal(owner.dialog,expected.dialog);assert.equal(h.resets,0);
});
test('public blur/focus expires once at fifteen minutes and preserves the draft owner',()=>{
 const h=harness();h.fire('blur');h.advance(900000);h.fire('focus');h.fire('focus');
 assert.equal(h.state.selectedConversationId,null);assert.equal(h.state.pane,'chat');assert.equal(h.resets,1);
 assert.equal(h.state.composerDraft,'retained body');assert.equal(h.context.desktopEntryRouteGeneration,1);
 assert.deepEqual(Object.keys(JSON.parse([...h.records.values()][0])),['workspaceId','conversationId','backgroundAt','hadRunningGeneration']);
});
for(const phase of ['ordinarySendPreparing','pendingChatSubmission'])test(`public foreground preserves ${phase} before first delta`,()=>{
 const h=harness();h.state[phase]=true;h.fire('blur');h.advance(900000);h.fire('focus');
 assert.equal(h.state.selectedConversationId,'old');assert.equal(h.resets,0);
});
test('a duplicate leave cannot extend the retained navigation deadline',()=>{
 const h=harness();h.fire('blur');h.advance(899999);h.fire('blur');h.advance(1);h.fire('focus');assert.equal(h.state.selectedConversationId,null);
});

test('a running generation in the other area keeps the current entry route',()=>{
 const h=harness();h.context.dataAreaStates.set('WORK',{pendingChatSubmission:{id:'other-area'}});
 h.fire('blur');h.advance(900000);h.fire('focus');assert.equal(h.state.selectedConversationId,'old');
});

test('expired read-only conversation returns to an editable new route without the old find target',()=>{
 const h=harness();h.state.settingsConversationReturn={page:'archived',readOnly:true,label:'已归档'};
 h.state.conversationFindOpen=true;h.state.conversationFindQuery='old archived text';h.state.profileOpen=true;
 h.state.error='old read-only error';h.state.status='old conversation status';
 h.fire('blur');h.advance(900000);h.fire('focus');
 assert.equal(h.state.selectedConversationId,null);assert.equal(h.state.settingsConversationReturn,null);
 assert.equal(h.state.conversationFindOpen,false);assert.equal(h.state.conversationFindQuery,'');assert.equal(h.state.profileOpen,false);
 assert.equal(h.state.error,'');assert.equal(h.state.status,'');
 assert.equal(h.state.composerDraft,'retained body');
});

test('an exit draft failure cannot become an error on a new route, area, or dialog',async()=>{
 for(const change of [h=>{h.advance(900000);h.fire('focus');},h=>{h.context.state={dataArea:'WORK',pane:'work',error:'WORK status'};},h=>h.state.dialog={kind:'new-dialog'}]){
  let reject;const h=harness({flush:()=>new Promise((_,fail)=>reject=fail)});h.fire('blur');change(h);
  const owner=h.context.state,renders=h.renders,error=owner.error;reject(new Error('old draft write rejected'));await new Promise(resolve=>setImmediate(resolve));
  assert.equal(owner.error,error);assert.equal(h.renders,renders);
 }
});

test('preparing a generation keeps the original read-only route and its find context after fifteen minutes',()=>{
 const h=harness();h.state.ordinarySendPreparing=true;const view={page:'archived',readOnly:true};
 h.state.settingsConversationReturn=view;h.state.conversationFindOpen=true;h.state.conversationFindQuery='original query';
 h.fire('blur');h.advance(900000);h.fire('focus');
 assert.equal(h.state.selectedConversationId,'old');assert.equal(h.state.settingsConversationReturn,view);
 assert.equal(h.state.conversationFindOpen,true);assert.equal(h.state.conversationFindQuery,'original query');assert.equal(h.resets,0);
});

test('a current exit draft failure is reported, while explicit stale-area errors do not repaint',async()=>{
 for(const stale of [false,true]){
  let reject;const h=harness({flush:()=>new Promise((_,fail)=>reject=fail)});h.fire('blur');
  reject(Object.assign(new Error('draft write rejected'),stale?{code:'STALE_DATA_AREA'}:{}));await new Promise(resolve=>setImmediate(resolve));
  if(stale){assert.equal(h.state.error,undefined);assert.equal(h.renders,0);}
  else{assert.match(h.state.error,/草稿未保存.*draft write rejected/);assert.equal(h.renders,1);}
 }
});


test('a focus event while still hidden preserves the deadline until the actual visible return',()=>{
 const h=harness();h.visibility(true);h.fire('visibilitychange');h.advance(900000);
 h.fire('focus');h.fire('focus');
 assert.equal(h.state.selectedConversationId,'old');assert.equal(h.resets,0);
 assert.ok(h.context.desktopAppEntry.background);
 h.visibility(false);h.fire('visibilitychange');
 assert.equal(h.state.selectedConversationId,null);assert.equal(h.resets,1);
 assert.equal(h.state.composerDraft,'retained body');
 h.fire('focus');assert.equal(h.resets,1);
});

test('an early hidden focus cannot start a new short-exit interval',()=>{
 const h=harness();h.visibility(true);h.fire('visibilitychange');h.advance(899999);h.fire('focus');
 assert.ok(h.context.desktopAppEntry.background);
 h.advance(1);h.visibility(false);h.fire('visibilitychange');
 assert.equal(h.state.selectedConversationId,null);assert.equal(h.resets,1);
});

test('short hidden focus and visible return keep the exact conversation and draft',()=>{
 const h=harness();h.visibility(true);h.fire('visibilitychange');h.advance(899999);h.fire('focus');
 h.visibility(false);h.fire('visibilitychange');h.fire('focus');
 assert.equal(h.state.selectedConversationId,'old');assert.equal(h.resets,0);
 assert.equal(h.state.composerDraft,'retained body');assert.equal(h.context.desktopAppEntry.background,null);
});

for(const phase of ['ordinarySendPreparing','pendingChatSubmission'])test(`generation protection survives hidden focus for ${phase}`,()=>{
 const h=harness();h.visibility(true);h.fire('visibilitychange');h.advance(900000);h.fire('focus');
 h.state[phase]=true;h.visibility(false);h.fire('visibilitychange');
 assert.equal(h.state.selectedConversationId,'old');assert.equal(h.resets,0);
 assert.equal(h.state.composerDraft,'retained body');
});

for(const event of ['blur','pagehide','visibilitychange'])test(`an early ${event} before entry restoration cannot replace the retained cold-start route`,()=>{
 const h=harness();const retained={workspaceId:'saved-source',conversationId:'saved-last',backgroundAt:1800000000000,hadRunningGeneration:false};h.records.set('nanfeng-ai.desktop.app-entry.v1.CHAT',JSON.stringify(retained));h.state.entryResolved=false;h.state.current=null;h.state.selectedConversationId=null;h.visibility(true);h.fire(event);
 assert.deepEqual(JSON.parse(h.records.get('nanfeng-ai.desktop.app-entry.v1.CHAT')),retained);assert.equal(h.context.desktopAppEntry.background,null);assert.equal(h.context.desktopEntryExitRoute,null);
 h.state.entryResolved=true;h.state.current={summary:{id:'saved-source'}};h.state.selectedConversationId='saved-last';h.fire('blur');assert.equal(h.context.desktopAppEntry.background.conversationId,'saved-last');
});

test('a clock rollback on return retains the original draft and permits a fresh later idle interval',()=>{
 const h=harness();h.fire('blur');h.advance(-1);h.fire('focus');assert.equal(h.state.selectedConversationId,'old');assert.equal(h.state.composerDraft,'retained body');assert.equal(h.resets,0);
 h.fire('blur');h.advance(900000);h.fire('focus');assert.equal(h.state.selectedConversationId,null);assert.equal(h.resets,1);
});
test('generation recorded on leave still protects the first return after stop, then a fresh idle interval expires',()=>{
 const h=harness();h.state.pendingChatSubmission={id:'synthetic-run'};h.fire('blur');h.state.pendingChatSubmission=null;h.advance(900000);h.fire('focus');assert.equal(h.state.selectedConversationId,'old');assert.equal(h.state.composerDraft,'retained body');assert.equal(h.resets,0);
 h.fire('blur');h.advance(900000);h.fire('focus');assert.equal(h.state.selectedConversationId,null);assert.equal(h.resets,1);
});
