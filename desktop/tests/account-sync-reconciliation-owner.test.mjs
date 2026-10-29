import test from 'node:test';
import assert from 'node:assert/strict';
import vm from 'node:vm';
import { readFileSync } from 'node:fs';
import { invokeForDataArea, invokeForAuthorizedDataAreaOperation } from '../src/data-area-invoke.mjs';
const source=readFileSync(new URL('../src/app.mjs',import.meta.url),'utf8');
const scheduler=source.slice(source.indexOf('function scheduleUnknownSyncReconciliation('),source.indexOf('async function loadTranscriptionState('));
const loader=source.slice(source.indexOf('async function loadDesktopAccountSync('),source.indexOf('async function cloudRowsFromLocalEntries('));
const tick=()=>new Promise(r=>setImmediate(r));
function harness(){
 let release,started;const response=new Promise(r=>release=r),first=new Promise(r=>started=r);const calls=[],timers=[],toasts=[],reads=[];
 const owner={dataArea:'CHAT',current:{summary:{id:'origin'}},pane:'chat',settingsSection:'home',accountSync:{email:'account-a@example.invalid',pendingUnknown:{workspaceId:'origin',conversationId:'one'}},status:'pending'};
 const bridge={invoke:async(command,args,options)=>{calls.push({command,args,area:options.headers['x-nanfeng-data-area']});if(command==='reconcile_desktop_conversation_sync'){started();return response;}return {email:owner.accountSync.email};}};
 const context=vm.createContext({state:owner,native:true,pendingSyncReconciliations:new Set(),invokeForAuthorizedDataAreaOperation,tauriBridge:bridge,window:{setTimeout:fn=>timers.push(fn)},render(){},showTransientToast:message=>toasts.push(message),notifyAccountSync(){},loadDesktopAccountSync:async selected=>reads.push((selected||context.state).dataArea),invoke:(command,args,selected=context.state)=>invokeForDataArea(bridge,command,args,selected,()=>context.state)});
 vm.runInContext(scheduler,context);return {owner,context,calls,timers,toasts,reads,first,release,schedule:()=>context.scheduleUnknownSyncReconciliation('origin','one'),finish:async()=>{release({status:'SYNCED_AFTER_RECONCILE'});await tick();await tick();}};
}
test('deferred reconciliation keeps its original area and expected account when another area is visible',async()=>{
 const h=harness();h.schedule();const next={dataArea:'WORK',current:{summary:{id:'origin'}},accountSync:{email:'account-a@example.invalid'},status:'new status'};h.context.state=next;h.timers.shift()();await h.first;await h.finish();
 assert.equal(h.calls[0].area,'CHAT');assert.equal(h.calls[0].args.expectedAccountEmail,'account-a@example.invalid');assert.equal(next.status,'new status');assert.deepEqual(h.reads,[]);assert.deepEqual(h.toasts,[]);
});
test('equal object IDs in different areas schedule distinct reconciliation jobs',async()=>{
 const h=harness();h.schedule();h.context.state={...h.owner,dataArea:'WORK',accountSync:{...h.owner.accountSync,pendingUnknown:{workspaceId:'origin',conversationId:'one'}}};h.schedule();assert.equal(h.timers.length,2);for(const fn of h.timers)fn();await tick();await h.finish();assert.deepEqual(h.calls.filter(c=>c.command==='reconcile_desktop_conversation_sync').map(c=>c.area),['CHAT','WORK']);
});
test('a late reconciliation receipt cannot overwrite a different chat source',async()=>{
 const h=harness();h.schedule();h.timers.shift()();await h.first;h.owner.current={summary:{id:'next-source'}};h.owner.status='next source';await h.finish();assert.equal(h.owner.status,'next source');assert.ok(h.owner.accountSync.pendingUnknown);assert.deepEqual(h.toasts,[]);assert.deepEqual(h.reads,[]);
});
test('an older result cannot clear a newer unknown submission marker',async()=>{
 const h=harness();h.schedule();h.timers.shift()();await h.first;const newer={workspaceId:'origin',conversationId:'one'};h.owner.accountSync={...h.owner.accountSync,pendingUnknown:newer};h.owner.status='new pending';await h.finish();assert.equal(h.owner.accountSync.pendingUnknown,newer);assert.equal(h.owner.status,'new pending');
});
test('changing account does not send a deferred job with the new account identity',async()=>{
 const h=harness();h.schedule();h.owner.accountSync={email:'account-b@example.invalid'};h.timers.shift()();await h.first;await h.finish();assert.equal(h.calls[0].args.expectedAccountEmail,'account-a@example.invalid');assert.equal(h.owner.accountSync.email,'account-b@example.invalid');assert.equal(h.owner.status,'pending');
});
test('same route and pending marker receive honest completion and release the dedup entry',async()=>{
 const h=harness();h.schedule();h.schedule();assert.equal(h.timers.length,1);h.timers.shift()();await h.first;await h.finish();assert.equal(h.owner.accountSync.pendingUnknown,null);assert.match(h.owner.status,/同步成功/);assert.equal(h.context.pendingSyncReconciliations.size,0);assert.deepEqual(h.reads,['CHAT']);
});
test('account read completion cannot replace a later account result',async()=>{
 let release;const response=new Promise(r=>release=r);const owner={dataArea:'CHAT',accountSync:{email:'account-a@example.invalid',avatarDataUrl:'synthetic'},cloudConversations:[]};
 const context=vm.createContext({state:owner,native:true,accountAvatarPending:false,accountSyncReadGenerations:new WeakMap(),render(){},invoke:async()=>response});vm.runInContext(loader,context);const read=context.loadDesktopAccountSync();const newer={email:'account-b@example.invalid',avatarDataUrl:'synthetic-new'};owner.accountSync=newer;release({email:'account-a@example.invalid',avatarDataUrl:'synthetic-old'});await read;assert.equal(owner.accountSync,newer);
});
