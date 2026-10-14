import test from 'node:test';
import assert from 'node:assert/strict';
import vm from 'node:vm';
import { readFile } from 'node:fs/promises';
import { DesktopAppEntry } from '../src/desktop-app-entry.mjs';
import { generationRefreshInterval, hasRunningGeneration } from '../src/generation-continuity.mjs';

const source = await readFile(new URL('../src/app.mjs', import.meta.url), 'utf8');
const handlers = source.slice(source.indexOf('function rememberDesktopExit()'), source.indexOf('const checkpointVideoOnExit'));
const registry = source.match(/^const ordinaryChatAttemptRegistry = .*;$/m)?.[0] || '';
function entryContext(running) {
  let now = 100; const records = new Map(); const refreshed = [];
  const ctx = vm.createContext({native: true, state: {dataArea:'CHAT',current:{summary:{id:'w'},exchange:{conversations:[{messages:running?[{delivery:'PARTIAL',runtimeState:'RUNNING'}]:[]}]}},selectedConversationId:'c'},
    desktopAppEntry:new DesktopAppEntry({getItem:k=>records.get(k),setItem:(k,v)=>records.set(k,v)},()=>now),
    hasRunningGeneration, flushOrdinaryComposerDraft:async()=>{}, queueOrdinaryChatRuntimeRefresh:p=>refreshed.push(p),
    desktopWorkspaceRefreshGeneration:0,resetNewConversationComposer:()=>{},render:()=>{}});
  vm.runInContext(registry + '\n' + handlers, ctx);
  return {ctx,refreshed,advance:()=>{now+=16*60*1000;}};
}
test('real exit and focus handlers retain a generating conversation and reload its saved state',()=>{
  const {ctx,advance,refreshed}=entryContext(true);
  assert.doesNotThrow(()=>vm.runInContext('rememberDesktopExit()',ctx)); advance();
  assert.doesNotThrow(()=>vm.runInContext('resumeDesktopEntry()',ctx));
  assert.equal(ctx.state.selectedConversationId,'c'); assert.equal(refreshed.length,1);
});
test('a native request keeps entry continuity before its first saved delta',()=>{
  const {ctx,advance}=entryContext(false);
  vm.runInContext("ordinaryChatAttemptRegistry.add('request'); rememberDesktopExit()",ctx);advance();
  vm.runInContext('ordinaryChatAttemptRegistry.clear(); resumeDesktopEntry()',ctx);
  assert.equal(ctx.state.selectedConversationId,'c');
});
test('idle long background retains existing fresh-chat policy',()=>{
  const {ctx,advance}=entryContext(false);vm.runInContext('rememberDesktopExit()',ctx);advance();vm.runInContext('resumeDesktopEntry()',ctx);
  assert.equal(ctx.state.selectedConversationId,null);
});
test('preview and hidden cadence affect UI only and terminal records are not running',()=>{
  assert.equal(generationRefreshInterval({}),80);assert.equal(generationRefreshInterval({pdfPreview:{}}),500);assert.equal(generationRefreshInterval({},true),1000);
  for(const delivery of ['COMPLETE','UNKNOWN','CANCELLED']) assert.equal(hasRunningGeneration({current:{exchange:{conversations:[{messages:[{delivery,runtimeState:'RUNNING'}]}]}}}),false);
});
