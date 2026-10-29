import assert from 'node:assert/strict';
import test from 'node:test';
import vm from 'node:vm';
import { readFile } from 'node:fs/promises';

const source = await readFile(new URL('../src/app.mjs',import.meta.url),'utf8');
const switchSource=source.slice(source.indexOf('async function switchDataArea('),source.indexOf('const dialogInvoke ='));
function harness(flushTemporaryDraft) {
  const chat={dataArea:'CHAT',runtimeInfo:{dataAreasReady:true},temporaryConversation:{temporaryId:'synthetic-temp',draft:''},composerDraft:'latest unsaved',localBackup:{},cloudConversations:[]};
  const work={dataArea:'WORK',runtimeInfo:chat.runtimeInfo,current:{summary:{id:'work'}},localBackup:{},cloudConversations:[]};
  const context=vm.createContext({state:chat,native:true,areaSwitchPending:false,dataAreaStates:new Map([['CHAT',chat],['WORK',work]]),fullSearchResultCache:new Map(),fullSearchCacheScope:'',ordinaryChatRuntimeRefreshPending:null,composerDraftRouteGeneration:0,cloudOpenAreaGeneration:0,app:{},localSettingsWrites:{flush:async()=>{}},render(){},selectWorkspaceDefaultConversation(){},flushTemporaryDraft});
  for(const name of ['flushOrdinaryComposerDraft','stopComposerCamera','supersedeFullSearch','loadDesktopLocalBackupStatus','loadDesktopAppSettings','loadModelServiceSettings','loadDesktopHistoryKnowledge','loadDesktopReminders','refresh','loadDesktopAccountSync','restoreCachedCloudConversationList','loadOrdinaryComposerDraft','drainReminderNotificationActions'])context[name]=async()=>{};
  vm.runInContext(switchSource,context);
  return {context,chat,work,switch:()=>vm.runInContext("switchDataArea('WORK','work')",context)};
}
test('CHAT stays selected until the latest temporary draft has been saved',async()=>{
  let release,called=false;
  const h=harness(()=>{called=true;assert.equal(h.context.state,h.chat);return new Promise(resolve=>{release=resolve;});});
  const switching=h.switch();
  await new Promise(resolve=>setImmediate(resolve));
  assert.equal(called,true);
  assert.equal(h.context.state,h.chat);
  assert.equal(h.context.areaSwitchPending,true);
  release();await switching;
  assert.equal(h.context.state,h.work);
  assert.equal(h.context.areaSwitchPending,false);
});
test('temporary save failure blocks the area switch and retains the draft',async()=>{
  const h=harness(async()=>{throw new Error('synthetic temporary disk failure');});
  await assert.rejects(h.switch(),/synthetic temporary disk failure/);
  assert.equal(h.context.state,h.chat);
  assert.equal(h.chat.composerDraft,'latest unsaved');
  assert.equal(h.context.areaSwitchPending,false);
});
