import test from 'node:test';
import assert from 'node:assert/strict';
import vm from 'node:vm';
import { readFileSync } from 'node:fs';
import { invokeForDataArea, invokeForAuthorizedDataAreaOperation } from '../src/data-area-invoke.mjs';
import { beginConversationRecycle, completeConversationRecycle, failConversationRecycle } from '../src/recycle-confirmation.mjs';
import { renderChatFirstShell } from '../src/chat-shell.mjs';
const source=readFileSync(new URL('../src/app.mjs',import.meta.url),'utf8');
const single=source.slice(source.indexOf('async function confirmConversationDelete('),source.indexOf('async function confirmConversationPermanentDelete('));
const batch=source.slice(source.indexOf('async function confirmConversationBatchDelete('),source.indexOf('// A cloud row is rendered',source.indexOf('async function confirmConversationBatchDelete(')));
const helperStart=source.indexOf('const cloudUnlinkPending = new WeakMap();');
const helper=helperStart<0?'':source.slice(helperStart,source.indexOf('async function confirmConversationBatchDelete(',helperStart));
const readOwner=source.slice(source.indexOf('const cloudListReadGenerations = new WeakMap();'),source.indexOf('let cloudOpenAreaGeneration = 0;'));
const settle=()=>new Promise(resolve=>setImmediate(resolve));
function harness({bulk=false,failAt=0,unknown=false,hold=false,refreshFail=false,mixed=false}={}) {
 const rows=['one','two','three'].map(id=>({id,workspaceId:'cloud-source',revision:3}));
 const local=new Map(rows.map(row=>[row.id,{...row,title:'本地标题',deleted:false}]));const remote=new Set(rows.map(row=>row.id));
 const state={dataArea:'CHAT',current:{summary:{id:'origin'}},selectedConversationId:'one',pane:'chat',contextMenu:null,
  accountSync:{email:'synthetic@example.invalid',syncedConversationKeys:rows.map(r=>`cloud-source:${r.id}`),selectedConversationCount:3},cloudConversations:rows,
  batchSelectedConversationKeys:new Set(rows.map(r=>`cloud-source:${r.id}`)),
  dialog:bulk?{kind:'conversation-batch-delete',items:rows.map(r=>({...r,...(mixed&&r.id==='three'?{workspaceId:null}:{})}))}:{kind:'conversation-delete',id:'one',revision:3,workspaceId:'cloud-source'}};
 let release,started;const gate=new Promise(resolve=>release=resolve),first=new Promise(resolve=>started=resolve);const calls=[],cache=[],favorites=[];let refreshes=0,finished=0;
 const bridge={invoke:async(command,args,options)=>{
  calls.push({command,args:JSON.parse(JSON.stringify(args)),area:options.headers['x-nanfeng-data-area']});
  if(calls.length===1){started();if(hold)await gate;}
  if(command==='mutate_desktop_domain'){local.get(args.args.objectId).deleted=true;return {revision:4};}
  if(command==='sync_selected_desktop_conversation')return {status:'SYNCED'};
  assert.equal(command,'cancel_desktop_conversation_sync');
  if(calls.length===failAt)throw Error('CLOUD_DELETE_FAILED');
  if(unknown)return {status:'UNKNOWN'};
  remote.delete(args.conversationId);return {status:'CANCELLED'};
 }};
 const context=vm.createContext({state,native:true,tauriBridge:bridge,invokeForAuthorizedDataAreaOperation,beginConversationRecycle,completeConversationRecycle,failConversationRecycle,
  invoke:(command,args,owner=context.state)=>invokeForDataArea(bridge,command,args,owner,()=>context.state),intent:()=> 'synthetic-unlink',render(){},refresh:async()=>{},
  removeFavoriteConversation:id=>favorites.push(id),document:{querySelector:()=>({focus(){}})},captureCloudSidebarWorkspace:()=>({workspaceId:'origin',selectedConversationId:'one'}),
  ensureConversationActionWorkspace:async item=>{if(item.workspaceId)context.state.current={summary:{id:item.workspaceId}};return true;},restoreCloudSidebarWorkspace:async()=>{context.state.current={summary:{id:'origin'}};},
  scheduleUnknownSyncReconciliation(){},batchConversationKey:item=>`${item.workspaceId||context.state.current.summary.id}:${item.id}`,
  persistCloudConversationCache:(rows,owner=context.state)=>cache.push({owner,rows:rows||owner.cloudConversations}),finishConversationBatchEditing(){finished++;},
  loadDesktopAccountSync:async()=>{refreshes++;if(refreshFail)throw Error('ACCOUNT_READ_FAILED');},
 });
 vm.runInContext(readOwner+'\n'+helper+'\n'+single+'\n'+batch,context);
 return {state,context,calls,local,remote,cache,favorites,first,release,get refreshes(){return refreshes;},get finished(){return finished;},run:()=>bulk?context.confirmConversationBatchDelete():context.confirmConversationDelete({dataset:{id:'one'}})};
}
test('single cloud delete cancels only its remote copy and keeps the open local conversation intact',async()=>{
 const h=harness();await h.run();assert.deepEqual(h.calls.map(c=>c.command),['cancel_desktop_conversation_sync']);
 assert.deepEqual(h.calls[0],{command:'cancel_desktop_conversation_sync',args:{workspaceId:'cloud-source',conversationId:'one'},area:'CHAT'});
 assert.equal(h.local.get('one').deleted,false);assert.equal(h.state.selectedConversationId,'one');assert.deepEqual(h.favorites,[]);assert.equal(h.state.current.summary.id,'origin');
 assert.deepEqual(Array.from(h.state.cloudConversations,r=>r.id),['two','three']);assert.equal(h.state.dialog,null);assert.match(h.state.status,/本地.*保留/);
});
test('cloud batch never soft-deletes local data and accepts each exact CANCELLED receipt',async()=>{
 const h=harness({bulk:true});await h.run();assert.equal(h.calls.length,3);assert.ok(h.calls.every(c=>c.command==='cancel_desktop_conversation_sync'));
 assert.ok([...h.local.values()].every(row=>!row.deleted));assert.equal(h.state.selectedConversationId,'one');assert.equal(h.state.cloudConversations.length,0);assert.equal(h.finished,1);
});
test('partial success removes only confirmed rows and retries only remaining items',async()=>{
 const h=harness({bulk:true,failAt:2});await h.run();assert.deepEqual(Array.from(h.state.cloudConversations,r=>r.id),['two','three']);
 assert.deepEqual(Array.from(h.state.dialog.items,r=>r.id),['two','three']);assert.match(h.state.dialog.failure,/已取消 1 个/);assert.equal(h.state.dialog.submitting,false);
 assert.equal(h.state.batchSelectedConversationKeys.has('cloud-source:one'),false);assert.equal(h.finished,0);assert.ok([...h.local.values()].every(row=>!row.deleted));
 await h.run();assert.deepEqual(h.calls.map(c=>c.args.conversationId),['one','two','two','three']);assert.equal(h.state.cloudConversations.length,0);
});
test('closing a confirmation during its first request stops the remainder without closing a new dialog',async()=>{
 const h=harness({bulk:true,hold:true});const running=h.run();await h.first;const next={kind:'new-dialog'};h.state.dialog=next;h.release();await running;
 assert.equal(h.calls.length,1);assert.equal(h.state.dialog,next);assert.equal(h.state.status,undefined);assert.deepEqual(Array.from(h.state.cloudConversations,r=>r.id),['two','three']);
});
test('explicit stop waits for the in-flight receipt and keeps remaining batch items available',async()=>{
 const h=harness({bulk:true,hold:true});const running=h.run();await h.first;h.context.stopCloudConversationUnlink();
 assert.equal(h.state.dialog.stopRequested,true);assert.equal(h.state.dialog.submitting,true);assert.equal(h.state.cloudConversations.length,3);
 h.release();await running;assert.equal(h.calls.length,1);assert.equal(h.state.dialog.submitting,false);assert.deepEqual(Array.from(h.state.dialog.items,r=>r.id),['two','three']);assert.match(h.state.dialog.failure,/已停止后续/);assert.equal(h.finished,0);
});
test('a late CHAT cancellation updates only its original owner/cache and cannot repaint WORK',async()=>{
 const h=harness({bulk:true,hold:true});const running=h.run();await h.first;const work={dataArea:'WORK',accountSync:{email:'synthetic@example.invalid'},cloudConversations:[{id:'one',workspaceId:'cloud-source'}],dialog:{kind:'work-dialog'}};h.context.state=work;
 h.release();await running;assert.equal(h.calls.length,1);assert.equal(h.calls[0].area,'CHAT');assert.equal(work.cloudConversations.length,1);assert.equal(work.dialog.kind,'work-dialog');
 assert.deepEqual(Array.from(h.state.cloudConversations,r=>r.id),['two','three']);assert.ok(h.cache.every(entry=>entry.owner===h.state));
});
test('account switch rejects the old receipt projection even when row IDs match',async()=>{
 const h=harness({bulk:true,hold:true});const running=h.run();await h.first;h.state.accountSync={email:'another@example.invalid'};const rows=h.state.cloudConversations;
 h.release();await running;assert.equal(h.calls.length,1);assert.equal(h.state.cloudConversations,rows);assert.equal(h.cache.length,0);
});
test('UNKNOWN cancellation is not successful deletion and never retries automatically',async()=>{
 const h=harness({bulk:true,unknown:true});await h.run();assert.equal(h.calls.length,1);assert.equal(h.state.cloudConversations.length,3);assert.equal(h.state.dialog.items.length,3);assert.match(h.state.dialog.failure,/尚未确认|未确认/);
});
test('confirmed cancellation remains successful if account metadata readback fails',async()=>{
 const h=harness({refreshFail:true});await h.run();assert.equal(h.state.cloudConversations.length,2);assert.equal(h.state.dialog,null);assert.match(h.state.status,/本地.*保留/);assert.match(h.state.status,/刷新|回读/);
});
test('duplicate confirmation cannot issue another request while cancellation is pending',async()=>{
 const h=harness({bulk:true,hold:true});const running=h.run();await h.first;await h.run();assert.equal(h.calls.length,1);h.release();await running;assert.equal(h.calls.length,3);
});
test('an inconsistent mixed local/cloud batch is rejected before any destructive or remote operation',async()=>{
 const h=harness({bulk:true,mixed:true});await h.run();assert.equal(h.calls.length,0);assert.ok([...h.local.values()].every(row=>!row.deleted));assert.match(h.state.dialog.failure,/分别|混合/);
});
test('cancellation invalidates a cloud-list read already in flight before and during the request',async()=>{
 const h=harness({hold:true});const before=h.context.currentCloudListRead(h.state);const running=h.run();await h.first;const during=h.context.currentCloudListRead(h.state);assert.notEqual(during,before);
 h.release();await running;assert.notEqual(h.context.currentCloudListRead(h.state),during);await settle();
});
test('the actual old cloud-read handler cannot project a cancelled row or leave its progress pending',async()=>{
 const h=harness();let releaseRead,projected=0,completed=0;
 const receipt=new Promise(resolve=>releaseRead=resolve);
 const start=source.indexOf("} else if (action === 'load-account-cloud-documents') {");
 const body=source.slice(start+"} else if (action === 'load-account-cloud-documents') {".length,source.indexOf("} else if (action === 'choose-selected-local-sync-start')",start));
 Object.assign(h.context,{invoke:()=>receipt,beginAccountSyncProgress(){h.state.accountSyncProgress={operation:'read'};},waitForAccountSyncProgressPaint:async()=>{},
  cloudRowsFromLocalEntries:async()=>{projected++;return [{id:'one',workspaceId:'cloud-source'}];},mergeCloudConversationList:(_,rows)=>rows,
  completeAccountSyncProgress:async()=>{completed++;},notifyAccountSync(){}});
 const reading=vm.runInContext(`(async owner=>{${body}})(state)`,h.context);await settle();assert.equal(h.state.accountSyncProgress.operation,'read');
 await h.run();assert.equal(h.state.accountSyncProgress,null);releaseRead({restored:[{conversationId:'one',workspaceId:'cloud-source'}]});await reading;
 assert.equal(projected,0);assert.equal(completed,0);assert.deepEqual(Array.from(h.state.cloudConversations,r=>r.id),['two','three']);assert.equal(h.state.accountSyncProgress,null);
});
test('late feedback completion cannot revive a replaced or cancelled progress owner',async()=>{
 const a=source.indexOf('async function completeAccountSyncProgress('),b=source.indexOf('let composerModelPricingRefreshTimer',a);
 const old={operation:'read',startedAt:0},state={accountSyncProgress:old};let release;let renders=0;
 const context=vm.createContext({state,performance:{now:()=>0},ACCOUNT_SYNC_PROGRESS_MIN_VISIBLE_MS:100,ACCOUNT_SYNC_RESULT_VISIBLE_MS:1,accountSyncProgressResultTimer:null,
  window:{setTimeout:resolve=>{release=resolve;},clearTimeout(){throw Error('old operation must not clear another result timer');}},render(){renders++;}});
 vm.runInContext(source.slice(a,b),context);const finishing=context.completeAccountSyncProgress('过期读取成功');const next={operation:'sync',startedAt:0};state.accountSyncProgress=next;release();await finishing;
 assert.equal(state.accountSyncProgress,next);assert.equal(renders,0);
});
test('actual cache persistence uses the explicit original area and account after a switch',()=>{
 const a=source.indexOf('function areaCloudConversationCacheKey('),b=source.indexOf('const parityPreferences',a);
 const c=source.indexOf('function dedupeCloudConversationEntries('),d=source.indexOf('const cloudConversationEntryLimit',c);
 const e=source.indexOf('function persistCloudConversationCache('),f=source.indexOf('function clearCloudConversationCache(',e);
 const chat={dataArea:'CHAT',accountSync:{email:'chat@example.invalid'},cloudConversations:[{workspaceId:'w',id:'c'}]},work={dataArea:'WORK',accountSync:{email:'work@example.invalid'}};const writes=[];
 const context=vm.createContext({state:work,cloudConversationCacheKey:'cache',window:{localStorage:{setItem:(key,json)=>writes.push({key,value:JSON.parse(json)})}}});
 vm.runInContext(source.slice(a,b)+source.slice(c,d)+source.slice(e,f),context);context.persistCloudConversationCache(chat.cloudConversations,chat);
 assert.equal(writes[0].key,'cache');assert.equal(writes[0].value.email,'chat@example.invalid');assert.deepEqual(writes[0].value.entries,[{workspaceId:'w',conversationId:'c',cloudPinned:false}]);
});
test('real cloud menu and batch labels distinguish cloud copies from the local delete action',()=>{
 const c={id:'c',title:'本地保留',revision:1,createdAt:'2026-01-01',messages:[]};
 const data={summary:{id:'w'},exchange:{conversations:[c],projects:[],knowledge:[],memory:[],relations:[]}};
 const base={data,native:true,selectedConversationId:'c',composerDraft:'',chatSearch:'',pane:'chat',status:'',error:'',connection:{},appearance:{mode:'system',fontSize:'standard',themeColor:'orange'},batchEditing:true,batchSelectedConversationKeys:new Set(['w:c']),modelPickerNow:new Date('2026-01-01T12:00:00Z')};
 const cloud=renderChatFirstShell({...base,sidebarConversationList:'cloud',cloudConversations:[{...c,workspaceId:'w'}],contextMenu:{...c,workspaceId:'w'}});
 assert.match(cloud,/chat-menu-action-label">删除云端副本/);assert.match(cloud,/<span>取消同步 1<\/span>/);
 const local=renderChatFirstShell({...base,sidebarConversationList:'recent',contextMenu:c});assert.match(local,/chat-menu-action-label">删除<\/span>/);assert.match(local,/<span>删除 1<\/span>/);
});
