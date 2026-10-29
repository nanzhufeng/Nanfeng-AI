import { retainNewerConversationSelection } from '../src/composer-draft-recovery.mjs';
import test from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { resolve } from 'node:path';
import vm from 'node:vm';

const appSource = await readFile(resolve(import.meta.dirname, '../src/app.mjs'), 'utf8');
const start = appSource.indexOf('let conversationSelectionGeneration = 0;');
const end = appSource.indexOf('\nconst invoke = ', start);
assert.ok(start >= 0 && end > start, 'WORK conversation route is present');
const routeSource = appSource.slice(start, end);
const chatStart = appSource.indexOf('// Local CHAT rows own their route here');
const chatEnd = appSource.indexOf('\nasync function saveLocalMessage()', chatStart);
assert.ok(chatStart >= 0 && chatEnd > chatStart, 'local CHAT route is present');
const chatSource = appSource.slice(chatStart, chatEnd);

test('WORK conversation selection reads its own model preferences and ignores a late earlier selection', async () => {
  const listeners = [];
  const pending = new Map();
  const state = {
    pane: 'work', current: { summary: { id: 'workspace-1' } }, selectedConversationId: null,
    p6gSelection: { conversationOverride: { modelId: 'old' } },
    conversationPreferences: { modelId: 'old' },
    chatScrollPositions: new Map(),
  };
  const context = { retainNewerConversationSelection,
    app: { addEventListener: (name, listener) => { if (name === 'click') listeners.push(listener); } },
    state, native: true, desktopEntryRouteGeneration:0,cloudOpenAreaGeneration:0,
    invoke: (command, args) => {
      assert.equal(command, 'read_desktop_p6g_selection');
      return new Promise(resolveRequest => pending.set(args.conversationId, resolveRequest));
    },
    markConversationOpened: async () => {},
    loadOrdinaryComposerDraft: async () => {},
    resetComposerPresentation: () => {},
    recomputeConversationFind: () => {},
    render: () => {},
  };
  vm.runInNewContext(routeSource, context);
  const route = listeners[0];
  assert.equal(typeof route, 'function');
  const click = id => route({ target: { closest: () => ({ dataset: { id } }) }, stopImmediatePropagation() {} });
  click('conversation-a');
  click('conversation-b');
  pending.get('conversation-b')({ conversationOverride: { modelId: 'model-b' } });
  await new Promise(resolveTick => setImmediate(resolveTick));
  pending.get('conversation-a')({ conversationOverride: { modelId: 'model-a' } });
  await new Promise(resolveTick => setImmediate(resolveTick));
  assert.equal(state.selectedConversationId, 'conversation-b');
  assert.equal(state.p6gSelection.conversationOverride.modelId, 'model-b');
  assert.equal(state.conversationPreferences.modelId, 'model-b');
});

test('CHAT local selection keeps the latest model projection and yields cloud rows to their owner', async () => {
  const listeners = [];
  const pending = new Map();
  const state = {
    pane: 'chat', dataArea: 'CHAT', current: { summary: { id: 'workspace-1' } },
    selectedConversationId: null, settingsSection: 'conversations', chatScrollPositions: new Map(),
    p6gSelection: null, conversationPreferences: { revision: 0 },
  };
  const context = { retainNewerConversationSelection,
    app: { addEventListener: (name, listener) => { if (name === 'click') listeners.push(listener); } },
    state, native: true, desktopEntryRouteGeneration:0,cloudOpenAreaGeneration:0,
    invoke: (command, args) => {
      assert.equal(command, 'read_desktop_p6g_selection');
      return new Promise(resolveRequest => pending.set(args.conversationId, resolveRequest));
    },
    markConversationOpened: async () => {}, loadOrdinaryComposerDraft: async () => {},
    resetComposerPresentation: () => {}, recomputeConversationFind: () => {}, render: () => {},
  };
  vm.runInNewContext(`${routeSource}\n${chatSource}`, context);
  const [workRoute, chatRoute] = listeners;
  const target = (id, workspaceId = undefined) => ({
    dataset: { id, workspaceId },
    closest: selector => selector === '.android-settings-main' ? null : { dataset: { id, workspaceId } },
  });
  let stopped = false;
  const event = row => ({ target: { closest: () => row }, preventDefault() {}, stopImmediatePropagation() { stopped = true; } });
  chatRoute(event(target('cloud-conversation', 'remote-workspace')));
  assert.equal(stopped, false);
  assert.equal(state.selectedConversationId, null);
  state.pane = 'work';
  workRoute(event(target('cloud-conversation', 'remote-workspace')));
  assert.equal(stopped, false);
  state.pane = 'chat';
  chatRoute(event(target('conversation-a')));
  chatRoute(event(target('conversation-b')));
  pending.get('conversation-b')({ conversationOverride: { modelId: 'model-b' } });
  await new Promise(resolveTick => setImmediate(resolveTick));
  pending.get('conversation-a')({ conversationOverride: { modelId: 'model-a' } });
  await new Promise(resolveTick => setImmediate(resolveTick));
  assert.equal(state.selectedConversationId, 'conversation-b');
  assert.equal(state.p6gSelection.conversationOverride.modelId, 'model-b');
  assert.equal(state.conversationPreferences.modelId, 'model-b');
});

test('actual local capture route keeps an opened model tier when its read completes',async()=>{
 const listeners=[];let finish;const state={pane:'chat',dataArea:'CHAT',current:{summary:{id:'w'}},chatScrollPositions:new Map()};
 const context={retainNewerConversationSelection,state,native:true,desktopEntryRouteGeneration:0,cloudOpenAreaGeneration:0,
 app:{addEventListener:(_,fn)=>listeners.push(fn)},invoke:()=>new Promise(resolve=>finish=resolve),
 markConversationOpened:async()=>{},loadOrdinaryComposerDraft:async()=>{},resetComposerPresentation(){},recomputeConversationFind(){},render(){}};
 vm.runInNewContext(`${routeSource}\n${chatSource}`,context);
 listeners[1]({target:{closest:()=>({dataset:{id:'c'},closest:()=>null})},preventDefault(){},stopImmediatePropagation(){}});
 state.p6gSelection={pickerTier:'RELAYCORE'};finish({conversationOverride:{modelId:'Astra'},globalDefault:{modelId:'Astra'}});
 await new Promise(setImmediate);assert.equal(state.p6gSelection.pickerTier,'RELAYCORE');assert.equal(state.p6gGlobalDefault.modelId,'Astra');
});

for(const pane of ['chat','work']) {
 test(`${pane} capture ignores a reply after same-ID area round trip`,async()=>{
  const listeners=[];let finish;const state={pane,dataArea:pane==='work'?'WORK':'CHAT',current:{summary:{id:'w'}},chatScrollPositions:new Map()};
  const context={retainNewerConversationSelection,state,native:true,desktopEntryRouteGeneration:0,cloudOpenAreaGeneration:0,
   app:{addEventListener:(_,fn)=>listeners.push(fn)},invoke:()=>new Promise(resolve=>finish=resolve),markConversationOpened:async()=>{},loadOrdinaryComposerDraft:async()=>{},resetComposerPresentation(){},recomputeConversationFind(){},render(){}};
  vm.runInNewContext(`${routeSource}\n${chatSource}`,context);
  listeners[pane==='work'?0:1]({target:{closest:()=>({dataset:{id:'c'},closest:()=>null})},preventDefault(){},stopImmediatePropagation(){}});
  context.cloudOpenAreaGeneration+=2;state.p6gSelection={pickerTier:'RELAYCORE',conversationOverride:{modelId:'returned'}};
  finish({conversationOverride:{modelId:'old'},globalDefault:{modelId:'old'}});await new Promise(setImmediate);
  assert.equal(state.p6gSelection.conversationOverride.modelId,'returned');
 });
 test(`${pane} capture cannot report a stale failed read on a new chat`,async()=>{
  const listeners=[];let reject;const state={pane,dataArea:pane==='work'?'WORK':'CHAT',current:{summary:{id:'w'}},chatScrollPositions:new Map()};
  const context={retainNewerConversationSelection,state,native:true,desktopEntryRouteGeneration:0,cloudOpenAreaGeneration:0,
   app:{addEventListener:(_,fn)=>listeners.push(fn)},invoke:()=>new Promise((_,no)=>reject=no),markConversationOpened:async()=>{},loadOrdinaryComposerDraft:async()=>{},resetComposerPresentation(){},recomputeConversationFind(){},render(){}};
  vm.runInNewContext(`${routeSource}\n${chatSource}`,context);
  listeners[pane==='work'?0:1]({target:{closest:()=>({dataset:{id:'c'},closest:()=>null})},preventDefault(){},stopImmediatePropagation(){}});
  context.desktopEntryRouteGeneration++;state.selectedConversationId=null;state.error='new chat';reject(new Error('old read'));await new Promise(setImmediate);
  assert.equal(state.error,'new chat');
 });
}
