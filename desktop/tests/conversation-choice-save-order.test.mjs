import { retainNewerConversationSelection } from '../src/composer-draft-recovery.mjs';
import assert from 'node:assert/strict';
import test from 'node:test';
import vm from 'node:vm';
import { readFile } from 'node:fs/promises';
import { createLocalWriteBarrier } from '../src/local-write-barrier.mjs';
import { invokeForDataArea, invokeForAuthorizedDataAreaOperation } from '../src/data-area-invoke.mjs';
import { renderChatFirstShell } from '../src/chat-shell.mjs';

const source = await readFile(new URL('../src/app.mjs', import.meta.url), 'utf8');
const start = source.includes('let conversationChoiceSaveQueue =')
  ? source.indexOf('let conversationChoiceSaveQueue =') : source.indexOf('async function updateP6GConversationOverride(');
const owner = source.slice(start, source.indexOf('async function leaveTemporaryChat()'));
const resetOwner = source.slice(source.indexOf('function resetComposerPresentation()'), source.indexOf('function restoreOrdinaryComposerDraft('));
const turn = () => new Promise(resolve => setImmediate(resolve));
function deferred() { let resolve; const promise = new Promise(yes => { resolve = yes; }); return { promise, resolve }; }

function harness({ newConversation = false } = {}) {
  const record = { revision: 0, modelId: null, toneOverride: null, webSearchOverride: null };
  let disk = structuredClone(record), global = newConversation ? { revision: 0, tier: null, composerRevision: 0, modelId: null } : { revision: 0, tier: null }, held = null, failed = false, failedModel = null, refreshes = 0;
  const writes = createLocalWriteBarrier(), calls = [], areaOwners = [];
  const projection = () => ({ conversationOverride: structuredClone(disk), globalDefault: structuredClone(global) });
  const state = { dataArea: 'CHAT', current: { summary: { id: 'workspace-one' } }, selectedConversationId: newConversation ? null : 'conversation-one',
    p6gSelection: projection(), conversationPreferences: structuredClone(record), p6gGlobalDefault: structuredClone(global),
    p6gModelPickerOpen: true, composerAddOpen: true, composerAddPage: 'style', productSettings: { webSearchEnabled: true } };
  let tier = 'FAST';
  const context = vm.createContext({ retainNewerConversationSelection, state, native: true, localSettingsWrites: writes, desktopEntryRouteGeneration:0, cloudOpenAreaGeneration:0, composerDraftRouteGeneration:0, composerPresentationIdentity:{},
    scheduleOrdinaryComposerDraft() { context.composerDraftRouteGeneration += 1; },
    armNewConversationDraftExpiry() {},
    render() {}, restoreOverlayFocus() {}, document: { querySelector: () => ({ value: tier }) },
    refresh: async () => { refreshes += 1; state.p6gSelection = projection(); state.conversationPreferences = structuredClone(disk); state.p6gGlobalDefault = structuredClone(global); },
    transport: async (command, payload, areaOwner) => {
      calls.push(command); areaOwners.push(areaOwner);
      if (command === 'read_desktop_p6g_selection') return projection();
      if (command === 'read_desktop_p6g_global_default') return structuredClone(global);
      const gate = held; held = null;
      if (gate) await gate.promise;
      if (failed) { failed = false; throw new Error('synthetic disk failure'); }
      const args = payload.args || payload;
      if (command === 'set_desktop_p6g_global_default') {
        if (args.expectedRevision !== global.revision) throw new Error('REVISION_CONFLICT');
        global = { revision: global.revision + 1, tier: args.tier };
        return { revision: global.revision };
      }
      if (command === 'set_desktop_new_conversation_model') {
        if (args.expectedRevision !== global.composerRevision) throw new Error('REVISION_CONFLICT');
        if (args.modelId === failedModel && failedModel !== null) { failedModel = null; throw new Error('synthetic model write failure'); }
        global = { ...global, modelId: args.modelId, composerRevision: global.composerRevision + 1 };
        return { revision: global.composerRevision };
      }
      if (args.expectedRevision !== disk.revision) throw new Error('REVISION_CONFLICT');
      if (command === 'set_desktop_conversation_preferences') {
        disk = { ...disk, toneOverride: args.toneOverride, webSearchOverride: args.webSearchOverride, revision: disk.revision + 1 };
      } else if (command === 'set_desktop_p6g_conversation_override' || command === 'clear_desktop_p6g_conversation_override') {
        disk = { ...disk, modelId: args.modelId || null, revision: disk.revision + 1 };
        global = { ...global, modelId: args.modelId || null, composerRevision: (global.composerRevision || 0) + 1 };
      } else throw new Error(`Unexpected command ${command}`);
      return { revision: disk.revision };
    },
  });
  context.tauriBridge = { invoke: (command, payload, options) => context.transport(command, payload, options.headers['x-nanfeng-data-area'] === 'CHAT' ? state : context.state) };
  context.invoke = (command, payload, areaOwner) => invokeForDataArea(context.tauriBridge, command, payload, areaOwner, () => context.state);
  context.invokeForAuthorizedDataAreaOperation = invokeForAuthorizedDataAreaOperation;
  vm.runInContext(resetOwner, context);
  vm.runInContext(owner, context);
  return { state, writes, calls, areaOwners, context,
    switchArea() { const next = structuredClone(state); next.dataArea = "WORK"; next.status = "destination"; next.error = ""; context.state = next; return next; },
    get disk() { return disk; }, get global() { return global; }, get refreshes() { return refreshes; },
    holdNext() { held = deferred(); return held; }, failNext() { failed = true; }, failModel(id) { failedModel = id; },
    editDraft(text) { state.composerDraft = text; context.composerDraftRouteGeneration += 1; },
    replaceComposer() { context.resetComposerPresentation(); },
    model: id => context.updateP6GConversationOverride(id),
    preferences: patch => context.updateConversationPreferences(patch, { close: true }),
    toggleWeb: () => context.updateConversationPreferences({}, { toggleWebSearch: true }),
    globalDefault: value => { tier = value; return context.saveP6GGlobalDefault(); } };
}

test('new-chat rapid choices apply the last committed model despite earlier draft scheduling', async () => {
  const h=harness({newConversation:true});
  await Promise.all([h.model('RELAYCORE_GPT_6_ASTRA'),h.model('RELAYCORE_GPT_6_1_SOL')]);
  assert.equal(h.global.modelId,'RELAYCORE_GPT_6_1_SOL');assert.equal(h.global.composerRevision,2);
  assert.equal(h.state.pendingNewConversationModelId,'RELAYCORE_GPT_6_1_SOL');
  assert.equal(h.state.p6gGlobalDefault.modelId,'RELAYCORE_GPT_6_1_SOL');
});

test('typing during a new-chat model save preserves text and applies the confirmed choice', async () => {
  const h=harness({newConversation:true}),gate=h.holdNext();const pending=h.model('RELAYCORE_GPT_6_ASTRA');await turn();
  h.editDraft('继续输入的原文');assert.equal(h.state.pendingNewConversationModelId,undefined);
  gate.resolve();await pending;assert.equal(h.global.modelId,'RELAYCORE_GPT_6_ASTRA');
  assert.equal(h.state.pendingNewConversationModelId,'RELAYCORE_GPT_6_ASTRA');assert.equal(h.state.composerDraft,'继续输入的原文');
});

test('a later new-chat save failure keeps the earlier committed model and reports the failure', async () => {
  const h=harness({newConversation:true});h.failModel('RELAYCORE_GPT_6_1_SOL');
  await Promise.all([h.model('RELAYCORE_GPT_6_ASTRA'),h.model('RELAYCORE_GPT_6_1_SOL')]);
  assert.equal(h.global.modelId,'RELAYCORE_GPT_6_ASTRA');assert.equal(h.state.pendingNewConversationModelId,'RELAYCORE_GPT_6_ASTRA');
  assert.match(h.state.error,/型号选择未保存/);assert.equal(h.global.composerRevision,1);
});

test('a confirmed new-chat model save still cannot alter a newly selected conversation', async () => {
  const h=harness({newConversation:true}),gate=h.holdNext();const pending=h.model('RELAYCORE_GPT_6_ASTRA');await turn();
  h.context.desktopEntryRouteGeneration+=1;h.state.selectedConversationId='conversation-two';h.state.status='新会话状态';gate.resolve();await pending;
  assert.equal(h.global.modelId,'RELAYCORE_GPT_6_ASTRA');assert.equal(h.state.pendingNewConversationModelId,undefined);assert.equal(h.state.status,'新会话状态');
});

const rememberedSettings = [{ providerId: 'RELAYCORE_GPT', enabled: true, credentialStored: true, presets: [
  { id: 'RELAYCORE_GPT_6_ASTRA', displayName: 'Astra', chatSelectable: true },
  { id: 'RELAYCORE_GPT_6_1_SOL', displayName: 'Sol', chatSelectable: true },
] }];
function newChat(h, pendingNewConversationModelId = undefined) {
  return renderChatFirstShell({ data: { summary: { id: 'workspace-one' }, exchange: { conversations: [] } },
    native: true, pane: 'chat', selectedConversationId: null, composerDraft: '你好', chatSearch: '', connection: {},
    modelServiceSettings: rememberedSettings, p6gGlobalDefault: h.state.p6gGlobalDefault,
    p6gSelection: h.state.p6gSelection, pendingNewConversationModelId });
}

test('first-use model selection persists before a local chat workspace exists', async () => {
  const h = harness({ newConversation: true }); h.state.current = null;
  await h.model('RELAYCORE_GPT_6_ASTRA');
  assert.equal(h.global.modelId, 'RELAYCORE_GPT_6_ASTRA');
  assert.equal(h.state.pendingNewConversationModelId, 'RELAYCORE_GPT_6_ASTRA');
});

test('first-use rapid model choices survive their own draft workspace initialization', async () => {
  const h = harness({ newConversation: true }); h.state.current = null;
  const gate = h.holdNext(); const first = h.model('RELAYCORE_GPT_6_ASTRA'); await turn();
  const second = h.model('RELAYCORE_GPT_6_1_SOL');
  h.state.current = { summary: { id: 'new-owned-workspace' } };
  gate.resolve(); await Promise.all([first, second]);
  assert.equal(h.state.pendingNewConversationModelId, 'RELAYCORE_GPT_6_1_SOL');
  assert.equal(h.global.modelId, 'RELAYCORE_GPT_6_1_SOL');
});
test('an existing conversation model save is inherited by new chat immediately without reload', async () => {
  const h = harness();
  await h.model('RELAYCORE_GPT_6_ASTRA');
  assert.match(newChat(h), /aria-label="选择模型：6 Astra · 0.25×"/);
  assert.equal(h.refreshes, 0);
  assert.match(newChat(h, 'RELAYCORE_GPT_6_1_SOL'), /aria-label="选择模型：GPT-6.1 Sol · 0.25×"/, 'a restored draft keeps its own explicit choice');
  assert.match(newChat(h, null), /aria-label="选择模型：[^"\n]*Sol[^"\n]*RC"/, 'a restored explicit Auto stays Auto');
});
test('clearing an existing conversation choice immediately remembers explicit Auto', async () => {
  const h = harness();
  await h.model('RELAYCORE_GPT_6_ASTRA');
  await h.model(null);
  assert.equal(h.state.p6gGlobalDefault.modelId, null);
  assert.equal(h.state.p6gGlobalDefault.composerRevision, 2);
  assert.match(newChat(h), /aria-label="选择模型：[^"\n]*Sol[^"\n]*RC"/);
});

test('rapid model, tone and web changes merge with the latest shared revision', async () => {
  const h = harness();
  await Promise.all([h.model('model-new'), h.preferences({ toneOverride: 'professional' }), h.preferences({ webSearchOverride: true })]);
  assert.equal(h.disk.modelId, 'model-new');
  assert.equal(h.disk.toneOverride, 'professional');
  assert.equal(h.disk.webSearchOverride, true);
  assert.equal(h.disk.revision, 3);
});

test('a later automatic model choice wins without erasing tone or web preferences', async () => {
  const h = harness();
  await h.preferences({ toneOverride: 'efficient', webSearchOverride: true });
  await Promise.all([h.model('model-new'), h.model(null)]);
  assert.equal(h.disk.modelId, null);
  assert.equal(h.disk.toneOverride, 'efficient');
  assert.equal(h.disk.webSearchOverride, true);
});

test('a pending conversation choice is registered in normal close protection', async () => {
  const h = harness(), gate = h.holdNext();
  const saving = h.model('model-new');
  assert.equal(h.writes.version, 1);
  let drained = false;
  const closing = h.writes.flush().then(() => { drained = true; });
  await turn();
  assert.equal(drained, false);
  gate.resolve();
  await Promise.all([saving, closing]);
  assert.equal(h.disk.modelId, 'model-new');
});

test('a save finishing after navigation cannot close or refresh the destination picker', async () => {
  const h = harness(), gate = h.holdNext();
  const saving = h.model('model-new');
  await turn();
  h.state.selectedConversationId = 'conversation-two';
  h.state.p6gModelPickerOpen = true;
  gate.resolve();
  await saving;
  assert.equal(h.state.p6gModelPickerOpen, true);
  assert.equal(h.refreshes, 0);
});

for (const routeChange of ['conversation', 'workspace', 'composer-replacement', 'entry-generation']) {
  test(`a new-chat model failure after ${routeChange} cannot replace destination feedback`, async () => {
    const h = harness(), gate = h.holdNext();
    h.state.selectedConversationId = null;
    h.state.error = '';
    h.failNext();
    const saving = h.model('model-new');
    await turn();
    if (routeChange === 'conversation') h.state.selectedConversationId = 'conversation-two';
    if (routeChange === 'workspace') h.state.current = { summary: { id: 'workspace-two' } };
    if (routeChange === 'composer-replacement') h.replaceComposer();
    if (routeChange === 'entry-generation') h.context.desktopEntryRouteGeneration += 1;
    h.state.error = 'destination-specific error';
    h.state.status = 'destination-specific status';
    h.state.p6gModelPickerOpen = true;
    gate.resolve();
    await saving;
    assert.equal(h.state.error, 'destination-specific error');
    assert.equal(h.state.status, 'destination-specific status');
    assert.equal(h.state.p6gModelPickerOpen, true);
    assert.equal(h.state.pendingNewConversationModelId, undefined);
    assert.equal(h.calls.filter(c => c === 'set_desktop_new_conversation_model').length, 1);
    await assert.rejects(h.writes.flush(), /synthetic disk failure/);
  });
}

test('a current new-chat model failure remains visible and blocks normal close', async () => {
  const h = harness();
  h.state.selectedConversationId = null;
  h.failNext();
  await h.model('model-new');
  assert.match(h.state.error, /型号选择未保存.*synthetic disk failure/);
  assert.equal(h.state.pendingNewConversationModelId, undefined);
  await assert.rejects(h.writes.flush(), /synthetic disk failure/);
});

test('global default saves use committed revisions and are covered by close protection', async () => {
  const h = harness();
  await Promise.all([h.globalDefault('FAST'), h.globalDefault('DEEP')]);
  assert.equal(h.global.tier, 'DEEP');
  assert.equal(h.global.revision, 2);
  assert.equal(h.writes.version, 2);
});

test('failed local choices are reported without silently retrying or changing stored values', async () => {
  const h = harness();
  h.failNext();
  await h.preferences({ toneOverride: 'professional' });
  assert.match(h.state.error, /synthetic disk failure/);
  assert.equal(h.disk.toneOverride, null);
  assert.equal(h.calls.filter(c => c === 'set_desktop_conversation_preferences').length, 1);
});

test('two fast web toggles use consecutive committed values, including the global default', async () => {
  const h = harness();
  await Promise.all([h.toggleWeb(), h.toggleWeb()]);
  assert.equal(h.disk.webSearchOverride, true);
  assert.equal(h.disk.revision, 2);
});

for (const operation of ['model', 'preferences', 'globalDefault']) {
  test(`${operation} saves stay in their originating owner and cannot overwrite a new area with the same IDs`, async () => {
    const h = harness(), gate = h.holdNext();
    const saving = operation === 'model' ? h.model('model-new')
      : operation === 'preferences' ? h.preferences({ toneOverride: 'professional' })
      : h.globalDefault('DEEP');
    await turn();
    const next = h.switchArea(), snapshot = structuredClone(next);
    gate.resolve();
    await saving;
    assert.deepEqual(next, snapshot, 'late reply must not modify the destination area');
    assert.ok(h.areaOwners.length >= 3);
    assert.ok(h.areaOwners.every(areaOwner => areaOwner === h.state), 'every read/write must use the starting owner');
  });
}

test('already authorized queued choices finish in the original owner after a same-ID area change', async () => {
  const h = harness(), gate = h.holdNext();
  const first = h.model('model-new');
  const second = h.preferences({ toneOverride: 'professional', webSearchOverride: true });
  await turn();
  const destination = h.switchArea(), snapshot = structuredClone(destination);
  gate.resolve();
  await Promise.all([first, second, h.writes.flush()]);
  assert.equal(h.disk.modelId, 'model-new');
  assert.equal(h.disk.toneOverride, 'professional');
  assert.equal(h.disk.webSearchOverride, true);
  assert.equal(h.disk.revision, 2);
  assert.deepEqual(destination, snapshot);
});

test('an old save cannot reset a picker after leaving and returning to the same conversation',async()=>{
 const h=harness(),gate=h.holdNext();const save=h.model('old-choice');await turn();
 h.context.desktopEntryRouteGeneration+=2;h.state.p6gSelection={pickerTier:'RELAYCORE'};h.state.p6gModelPickerOpen=true;
 gate.resolve();await save;assert.equal(h.state.p6gSelection.pickerTier,'RELAYCORE');assert.equal(h.state.p6gModelPickerOpen,true);
});
