import test from 'node:test';
import assert from 'node:assert/strict';
import vm from 'node:vm';
import { readFileSync } from 'node:fs';
import { invokeForDataArea, invokeForAuthorizedDataAreaOperation } from '../src/data-area-invoke.mjs';
import { beginConversationRecycle, completeConversationRecycle, failConversationRecycle } from '../src/recycle-confirmation.mjs';
const source=readFileSync(new URL('../src/app.mjs',import.meta.url),'utf8');
const unlink=source.slice(source.indexOf('const cloudUnlinkPending = new WeakMap();'),source.indexOf('async function confirmConversationBatchDelete('));
const single=source.slice(source.indexOf('async function confirmConversationDelete('),source.indexOf('async function confirmConversationPermanentDelete('));
const batch=source.slice(source.indexOf('async function confirmConversationBatchDelete('),source.indexOf('// A cloud row is rendered',source.indexOf('async function confirmConversationBatchDelete(')));
function harness({bulk=false,cloud=false,fail=false,ensureFail=false,syncFail=false}={}){
 let release,ready;const gate=new Promise(r=>release=r),first=new Promise(r=>ready=r);const calls=[],favorites=[],focus=[],scheduled=[];
 const owner={dataArea:'CHAT',current:{summary:{id:'origin'}},dialog:bulk?{kind:'conversation-batch-delete',items:[{id:'one',revision:3,...(cloud?{workspaceId:'cloud-source'}:{})},{id:'two',revision:8,...(cloud?{workspaceId:'cloud-source'}:{})}]}:{kind:'conversation-delete',id:'one',revision:3},selectedConversationId:null,pane:'chat',settingsSection:'home',cloudConversations:[],accountSync:{}};
 const bridge={invoke:async(command,args,options)=>{calls.push({command,args:JSON.parse(JSON.stringify(args)),area:options.headers['x-nanfeng-data-area']});if(calls.length===1){ready();await gate;if(fail)throw Error('REVISION_CONFLICT');}if(syncFail&&command==='cancel_desktop_conversation_sync')throw Error('CANCEL_FAILED');return command==='cancel_desktop_conversation_sync'?{status:'CANCELLED'}:command==='sync_selected_desktop_conversation'?{status:'SYNCED'}:{revision:4};}};
 const context=vm.createContext({state:owner,native:true,tauriBridge:bridge,invokeForAuthorizedDataAreaOperation,beginConversationRecycle,completeConversationRecycle,failConversationRecycle,
 invoke:(command,args,selected=context.state)=>invokeForDataArea(bridge,command,args,selected,()=>context.state),intent:()=> 'test-recycle',render(){},refresh:async()=>{},removeFavoriteConversation:id=>favorites.push([context.state.current.summary.id,id]),
 document:{querySelector:selector=>({focus:()=>focus.push(selector)})},captureCloudSidebarWorkspace:()=>cloud?{workspaceId:'origin',selectedConversationId:null}:null,ensureConversationActionWorkspace:async item=>{if(item.workspaceId)context.state.current={summary:{id:item.workspaceId}};if(ensureFail)throw Error('SOURCE_READ_FAILED');return true;},restoreCloudSidebarWorkspace:async snapshot=>{if(snapshot)context.state.current={summary:{id:snapshot.workspaceId}};},loadDesktopAccountSync:async()=>{},scheduleUnknownSyncReconciliation:(w,id)=>scheduled.push([w,id]),batchConversationKey:item=>`${item.workspaceId||context.state.current.summary.id}:${item.id}`,advanceCloudListRead(){},persistCloudConversationCache(){},finishConversationBatchEditing(){}});
 vm.runInContext(unlink+'\n'+single+'\n'+batch,context);return {owner,context,calls,favorites,focus,first,release,run:()=>bulk?context.confirmConversationBatchDelete():context.confirmConversationDelete({dataset:{id:'one'}})};
}
test('single recycle receipt cannot close a newer dialog or cancel its favorite',async()=>{
 const h=harness();const work=h.run();await h.first;const newer={kind:'conversation-delete',id:'one',title:'new intent'};h.owner.dialog=newer;h.release();await work;
 assert.equal(h.owner.dialog,newer);assert.equal(h.owner.status,undefined);assert.deepEqual(h.favorites,[]);assert.deepEqual(h.focus,[]);
});
test('single recycle failure cannot overwrite a newer same-ID confirmation',async()=>{
 const h=harness({fail:true});const work=h.run();await h.first;const newer={kind:'conversation-delete',id:'one',title:'new intent'};h.owner.dialog=newer;h.release();await work;assert.equal(h.owner.dialog,newer);
});
test('local batch stops remaining writes when its source is no longer current',async()=>{
 const h=harness({bulk:true});const work=h.run();await h.first;h.owner.current={summary:{id:'other'}};const newer={kind:'new-dialog'};h.owner.dialog=newer;h.release();await work;
 assert.equal(h.calls.length,1);assert.equal(h.calls[0].args.args.workspaceId,'origin');assert.equal(h.owner.dialog,newer);assert.deepEqual(h.favorites,[]);
});
test('a confirmed cloud cancellation stays in its original area and never locally recycles after navigation',async()=>{
 const h=harness({bulk:true,cloud:true});const work=h.run();await h.first;const next={dataArea:'WORK',current:{summary:{id:'other'}},dialog:{kind:'new-dialog'},accountSync:{}};h.context.state=next;h.release();await work;
 assert.equal(h.calls.length,1);const cancel=h.calls[0];assert.equal(cancel.command,'cancel_desktop_conversation_sync');assert.equal(cancel.area,'CHAT');assert.equal(cancel.args.workspaceId,'cloud-source');assert.equal(cancel.args.conversationId,'one');
 assert.equal(next.dialog.kind,'new-dialog');assert.equal(next.error,undefined);assert.deepEqual(h.favorites,[]);
});

test('current single recycle succeeds once and retains its recoverable result message',async()=>{
 const h=harness();const work=h.run();await h.first;await h.run();assert.equal(h.calls.length,1);h.release();await work;assert.equal(h.owner.dialog,null);assert.match(h.owner.status,/移入回收站/);assert.equal(h.calls[0].args.args.expectedRevision,3);
});

test('cloud cancellation does not load or replace the local reading workspace',async()=>{
 const h=harness({bulk:true,cloud:true,ensureFail:true});const work=h.run();await h.first;h.release();await work;
 assert.equal(h.calls.length,2);assert.ok(h.calls.every(c=>c.command==='cancel_desktop_conversation_sync'));assert.equal(h.owner.current.summary.id,'origin');assert.equal(h.owner.dialog,null);assert.deepEqual(h.favorites,[]);
});

test('a rejected cloud cancellation leaves local lifecycle untouched and confirmation available',async()=>{
 const h=harness({bulk:true,cloud:true,syncFail:true});const work=h.run();await h.first;h.release();await work;
 assert.equal(h.owner.current.summary.id,'origin');assert.equal(h.owner.dialog.submitting,false);assert.match(h.owner.dialog.failure,/CANCEL_FAILED/);assert.equal(h.calls.filter(c=>c.command==='mutate_desktop_domain').length,0);
});

function partialBatchHarness({failAt=2,holdAt=0,readFail=false,holdRead=false}={}) {
 const mutations=[],favorites=[],owner={dataArea:'CHAT',current:{summary:{id:'source'}},selectedConversationId:'one',batchSelectedConversationKeys:new Set(['source:one','source:two','source:three']),dialog:{kind:'conversation-batch-delete',items:[{id:'one',revision:3},{id:'two',revision:7},{id:'three',revision:11}]}};
 let release,reached;const gate=new Promise(r=>release=r),waiting=new Promise(r=>reached=r);const records=new Map(owner.dialog.items.map(x=>[x.id,{revision:x.revision,deleted:false}]));
 const context=vm.createContext({native:true,state:owner,tauriBridge:{},intent:()=> 'test-partial',render(){},captureCloudSidebarWorkspace:()=>null,ensureConversationActionWorkspace:async()=>true,restoreCloudSidebarWorkspace:async()=>{},refresh:async()=>{},finishConversationBatchEditing(){owner.batchSelectedConversationKeys.clear();owner.batchEditing=false;},removeFavoriteConversation:id=>favorites.push(id),batchConversationKey:item=>'source:'+item.id,
 invokeForAuthorizedDataAreaOperation:async(_,command,{args})=>{assert.equal(command,'mutate_desktop_domain');mutations.push(args.objectId);if(mutations.length===holdAt){reached();await gate;}if(mutations.length===failAt)throw Error('SYNTHETIC_SECOND_WRITE_FAILED');const row=records.get(args.objectId);assert.equal(args.expectedRevision,row.revision,'retry cannot reapply a confirmed deletion');row.revision++;row.deleted=true;return {revision:row.revision};}});
 let releaseRead,readStarted;const readGate=new Promise(r=>releaseRead=r),reading=new Promise(r=>readStarted=r);const reads=[];
 context.invoke=async(command,args,capturedOwner)=>{assert.equal(capturedOwner,owner);assert.equal(args.workspaceId,'source');reads.push(command);readStarted();if(holdRead)await readGate;if(readFail)throw Error('SYNTHETIC_LIST_READ_FAILED');if(command==='read_desktop_workspace')return {summary:{id:'source'},exchange:{conversations:[...records].map(([id,row])=>({id,...row}))}};assert.equal(command,'read_desktop_workbench_history');return {recycleBin:[...records].filter(([,r])=>r.deleted).map(([id])=>({id}))};};
 vm.runInContext(batch,context);return {owner,context,records,mutations,favorites,release,waiting,reading,releaseRead,reads,run:()=>context.confirmConversationBatchDelete()};
}
test('local batch failure retains only unconfirmed rows and explicit retry never reapplies completed work',async()=>{
 const h=partialBatchHarness();await h.run();assert.deepEqual(Array.from(h.owner.dialog.items,x=>x.id),['two','three']);assert.equal(h.owner.dialog.submitting,false);assert.match(h.owner.dialog.failure,/已处理 1 个/);assert.equal(h.owner.selectedConversationId,null);assert.deepEqual([...h.owner.batchSelectedConversationKeys],['source:two','source:three']);
 await h.run();assert.deepEqual(h.mutations,['one','two','two','three']);assert.ok([...h.records.values()].every(x=>x.deleted));assert.equal(h.owner.dialog,null);assert.deepEqual(h.favorites,['one','two','three']);
});
test('a first local batch failure preserves all original revisions and selections for explicit retry',async()=>{
 const h=partialBatchHarness({failAt:1});await h.run();assert.deepEqual(Array.from(h.owner.dialog.items,x=>[x.id,x.revision]),[['one',3],['two',7],['three',11]]);assert.equal(h.owner.selectedConversationId,'one');assert.equal(h.owner.batchSelectedConversationKeys.size,3);assert.ok([...h.records.values()].every(x=>!x.deleted));
});
test('partial local batch completion cannot prune a newer dialog or destination selection',async()=>{
 const h=partialBatchHarness({holdAt:1});const work=h.run();await h.waiting;const dialog={kind:'new-dialog'};h.owner.dialog=dialog;h.owner.selectedConversationId='manual';h.owner.batchSelectedConversationKeys=new Set(['new:manual']);h.release();await work;assert.equal(h.owner.dialog,dialog);assert.equal(h.owner.selectedConversationId,'manual');assert.deepEqual([...h.owner.batchSelectedConversationKeys],['new:manual']);assert.deepEqual(h.mutations,['one']);
});

test('partial local recycle refreshes the actual workspace and recycle projection before the dialog can be closed',async()=>{
 const h=partialBatchHarness();await h.run();assert.equal(h.owner.current.exchange?.conversations.find(c=>c.id==='one')?.deleted,true);assert.equal(h.owner.current.exchange.conversations.filter(c=>!c.deleted).length,2);assert.equal(h.owner.history.recycleBin[0].id,'one');assert.deepEqual(h.reads.sort(),['read_desktop_workbench_history','read_desktop_workspace']);assert.deepEqual(h.mutations,['one','two']);assert.equal(h.owner.dialog.submitting,false);
});
test('failed partial recycle readback keeps exact remaining revisions and a usable failure message',async()=>{
 const h=partialBatchHarness({readFail:true});await h.run();assert.deepEqual(Array.from(h.owner.dialog.items,x=>[x.id,x.revision]),[['two',7],['three',11]]);assert.equal(h.owner.dialog.submitting,false);assert.match(h.owner.dialog.failure,/列表.*刷新|刷新.*列表/);assert.match(h.owner.dialog.failure,/重开软件/);assert.match(h.owner.dialog.failure,/SYNTHETIC_SECOND_WRITE_FAILED/);assert.deepEqual(h.mutations,['one','two']);
});
test('a late partial recycle readback cannot overwrite a newer dialog or source',async()=>{
 const h=partialBatchHarness({holdRead:true});const work=h.run();await Promise.race([h.reading,new Promise((_,reject)=>setTimeout(()=>reject(Error('Readback phase not entered')),1500))]);const current={summary:{id:'other'},marker:'new snapshot'},dialog={kind:'new-dialog'};h.owner.current=current;h.owner.dialog=dialog;h.releaseRead();await work;assert.equal(h.owner.current,current);assert.equal(h.owner.dialog,dialog);assert.equal(h.owner.history,undefined);assert.deepEqual(h.mutations,['one','two']);
});
test('a newer same-source snapshot wins over an older partial recycle projection while retry remains available',async()=>{
 const h=partialBatchHarness({holdRead:true});const work=h.run();await Promise.race([h.reading,new Promise((_,reject)=>setTimeout(()=>reject(Error('Readback phase not entered')),1500))]);const current={summary:{id:'source'},marker:'new same-source snapshot'};h.owner.current=current;h.releaseRead();await work;assert.equal(h.owner.current,current);assert.equal(h.owner.history,undefined);assert.equal(h.owner.dialog.submitting,false);assert.deepEqual(Array.from(h.owner.dialog.items,x=>x.id),['two','three']);
});
