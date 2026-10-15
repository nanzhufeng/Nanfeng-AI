import test from 'node:test';
import assert from 'node:assert/strict';
import vm from 'node:vm';
import {readFile} from 'node:fs/promises';
const source = await readFile(new URL('../src/app.mjs', import.meta.url), 'utf8');
const choose = source.slice(source.indexOf('function selectWorkspaceDefaultConversation('), source.indexOf('function active(data, key)'));
const refresh = source.slice(source.indexOf('async function refresh()'), source.indexOf('async function runP6eTemporaryMaintenanceAcceptance()'));
const data = id => ({summary:{id},exchange:{conversations:[{id:'pinned',pinned:true},{id:'last-exit'}]}});
function context(invoke) {
  const state={current:data('first'),selectedConversationId:'last-exit',dataArea:'CHAT',entryResolved:true};
  const context=vm.createContext({state,native:true,desktopWorkspaceRefreshGeneration:0,invoke,
    activeConversations:d=>d.exchange.conversations,workspace:()=>state.current,
    resolveConversation:(d,id)=>d?.exchange?.conversations.find(c=>c.id===id),
    reloadFavoriteConversationIds:()=>{},loadConversationReadState:async()=>{},
    loadTranscriptionState:async()=>({tasks:[]}),recomputeConversationFind:()=>{},render:()=>{},
  });
  vm.runInContext(choose+refresh,context);
  return context;
}
test('native default selection never opens the pinned first item',()=>{
  const ctx=context(async()=>null);
  vm.runInContext('selectWorkspaceDefaultConversation(state.current)',ctx);
  assert.equal(ctx.state.selectedConversationId,null);
});
test('a late workspace read cannot replace newer navigation',async()=>{
  let release;
  const ctx=context(async command=>{
    if(command==='read_desktop_runtime_info') return {};
    if(command==='list_desktop_workspaces') return [{id:'first'}];
    if(command==='read_desktop_workspace') return new Promise(resolve=>{release=resolve;});
    return {};
  });
  const loading=vm.runInContext('refresh()',ctx);
  while(!release) await Promise.resolve();
  ctx.state.current=data('new-workspace');
  ctx.state.selectedConversationId='new-selection';
  release(data('first'));
  await loading;
  assert.equal(ctx.state.current.summary.id,'new-workspace');
  assert.equal(ctx.state.selectedConversationId,'new-selection');
});
test('late model metadata cannot replace the new conversation preferences',async()=>{
  let release;
  const ctx=context(async command=>{
    if(command==='read_desktop_runtime_info') return {};
    if(command==='list_desktop_workspaces') return [{id:'first'}];
    if(command==='read_desktop_workspace') return data('first');
    if(command==='read_desktop_p6g_selection') return new Promise(resolve=>{release=resolve;});
    return {};
  });
  const loading=vm.runInContext('refresh()',ctx);
  while(!release) await Promise.resolve();
  ctx.state.selectedConversationId='pinned';
  ctx.state.conversationPreferences={owner:'new'};
  release({conversationOverride:{owner:'old'}});
  await loading;
  assert.equal(ctx.state.conversationPreferences.owner,'new');
});

test('cold entry restores the exact previous ID, not pinned order',async()=>{
  const ctx=context(async command=>{
    if(command==='read_desktop_runtime_info') return {};
    if(command==='list_desktop_workspaces') return [{id:'first'}];
    if(command==='read_desktop_workspace') return data('first');
    return {};
  });
  ctx.state.current=null;ctx.state.selectedConversationId=null;ctx.state.entryResolved=false;
  ctx.desktopAppEntry={read:()=>({workspaceId:'first',conversationId:'last-exit'})};
  await vm.runInContext('refresh()',ctx);
  assert.equal(ctx.state.selectedConversationId,'last-exit');
});
test('missing retained conversation lands on a new conversation',async()=>{
  const ctx=context(async command=>{
    if(command==='read_desktop_runtime_info') return {};
    if(command==='list_desktop_workspaces') return [{id:'first'}];
    if(command==='read_desktop_workspace') return data('first');
    return {};
  });
  ctx.state.current=null;ctx.state.selectedConversationId=null;ctx.state.entryResolved=false;
  ctx.desktopAppEntry={read:()=>({workspaceId:'first',conversationId:'removed'})};
  await vm.runInContext('refresh()',ctx);
  assert.equal(ctx.state.selectedConversationId,null);
});

test('fresh native data without retained entry finishes metadata loading',async()=>{
  const calls=[];
  const ctx=context(async command=>{
    calls.push(command);
    if(command==='list_desktop_workspaces') return [];
    return {};
  });
  ctx.state.current=null;ctx.state.selectedConversationId=null;ctx.state.entryResolved=false;
  ctx.desktopAppEntry={read:()=>null};
  await vm.runInContext('refresh()',ctx);
  assert.equal(ctx.state.current,null);
  assert.equal(ctx.state.selectedConversationId,null);
  assert.equal(ctx.state.entryResolved,true);
  assert.ok(calls.includes('read_desktop_p6g_catalog'));
  assert.ok(calls.includes('read_desktop_p6g_global_default'));
  assert.ok(!calls.includes('read_desktop_p6g_selection'));
  assert.ok(!calls.includes('read_desktop_workspace'));
});
