import test from 'node:test';
import assert from 'node:assert/strict';
import vm from 'node:vm';
import { readFile } from 'node:fs/promises';
import { DesktopAppEntry,APP_ENTRY_RETENTION_MS } from '../src/desktop-app-entry.mjs';
import { workspaceUiEnabled } from '../src/chat-ui-policy.mjs';
const source=await readFile(new URL('../src/app.mjs',import.meta.url),'utf8');
const code=source.slice(source.indexOf('async function openGenerationIslandTask('),source.indexOf('const tauriEvents ='));
const target={dataArea:'CHAT',workspaceId:'target-workspace',conversationId:'target-conversation',assistantMessageId:'target-answer'};
const projection={summary:{id:target.workspaceId},exchange:{conversations:[{id:target.conversationId,messages:[{id:target.assistantMessageId}]}]}};
function harness(){
 const events=[];let release,readReached;const reading=new Promise(done=>readReached=done),pending=new Promise(done=>release=done);
 const entry=new DesktopAppEntry({setItem(){},getItem(){return null;}},()=>10000000);entry.background={backgroundAt:10000000-APP_ENTRY_RETENTION_MS-1,hadRunningGeneration:false};
 const context=vm.createContext({native:true,workspaceUiEnabled,state:{dataArea:'CHAT',current:{summary:{id:'old-workspace'}},selectedConversationId:'old-conversation',temporaryConversation:{text:'keep temporary'}},desktopEntryRouteGeneration:0,cloudOpenAreaGeneration:0,composerDraftRouteGeneration:0,conversationSelectionGeneration:0,desktopAppEntry:entry,
  openCloudConversationRoute:{cancel:()=>events.push('cancel-other-route')},flushOrdinaryComposerDraft:async()=>events.push('draft'),flushTemporaryDraft:async()=>events.push('temporary'),
  invoke:async(command,args,owner)=>{assert.equal(command,'read_desktop_workspace');assert.equal(args.workspaceId,target.workspaceId);events.push(`read:${owner.dataArea}`);readReached();return pending;},
  resetComposerPresentation:()=>events.push('reset'),loadConversationReadState:async()=>events.push('read-state'),loadOrdinaryComposerDraft:async()=>events.push('load-draft'),loadConversationSelectionProjection:async()=>events.push('selection'),render:()=>events.push('paint'),showTransientToast:()=>events.push('error'),
 });context.switchDataArea=async area=>{context.cloudOpenAreaGeneration++;context.state={dataArea:area,current:{summary:{id:'previous-area-workspace'}},selectedConversationId:'old-work'};};vm.runInContext(code,context);
 return {context,events,reading,release,open:task=>context.openGenerationIslandTask(task??target)};
}
test('explicit island entry saves both drafts and survives the expired generic entry reset',async()=>{
 const h=harness();const opened=h.open();await h.reading;h.release(projection);await opened;
 assert.equal(h.context.state.current.summary.id,target.workspaceId);assert.equal(h.context.state.selectedConversationId,target.conversationId);assert.equal(h.context.state.temporaryConversation,null);assert.equal(h.context.desktopAppEntry.enter(false),false);
 assert.deepEqual(h.events.slice(0,4),['cancel-other-route','draft','temporary','read:CHAT']);assert.ok(h.events.includes('load-draft'));
});
test('late island workspace response cannot override a newer manual conversation choice',async()=>{
 const h=harness();const opened=h.open();await h.reading;h.context.desktopEntryRouteGeneration++;h.context.state.selectedConversationId='manual-choice';h.release(projection);await opened;
 assert.equal(h.context.state.current.summary.id,'old-workspace');assert.equal(h.context.state.selectedConversationId,'manual-choice');assert.ok(!h.events.includes('load-draft'));assert.ok(!h.events.includes('error'));
});
test('WORK navigation uses its owner and a later area generation invalidates the reply',async()=>{
 const h=harness();const opened=h.open({...target,dataArea:'WORK'});await h.reading;assert.ok(h.events.includes('read:WORK'));h.context.cloudOpenAreaGeneration++;h.release(projection);await opened;assert.equal(h.context.state.current.summary.id,'previous-area-workspace');
});

test('Windows rejects a retained WORK view before changing routes or flushing the current draft',async()=>{
 const h=harness();h.context.state.runtimeInfo={platform:'windows'};h.release(projection);
 await h.open({...target,dataArea:'WORK'});
 assert.equal(h.context.state.dataArea,'CHAT');assert.equal(h.context.state.selectedConversationId,'old-conversation');
 assert.equal(h.context.desktopEntryRouteGeneration,0);assert.deepEqual(h.events,['error']);
});
test('deleted or missing target never restores or guesses a different conversation',async()=>{
 for(const deleted of [true,false]){const h=harness();const opened=h.open();await h.reading;h.release(deleted?{...projection,exchange:{conversations:[{...projection.exchange.conversations[0],deleted:true}]}}:{...projection,exchange:{conversations:[]}});await opened;assert.equal(h.context.state.selectedConversationId,'old-conversation');assert.ok(h.events.includes('error'));assert.ok(!h.events.includes('load-draft'));}
});
