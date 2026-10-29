import { attachModelSettingsController } from './helpers/model-settings-controller-harness.mjs';
import assert from 'node:assert/strict';
import test from 'node:test';
import vm from 'node:vm';
import { readFile } from 'node:fs/promises';
import { createDraftCloseGuard } from '../src/composer-draft-recovery.mjs';
import { createLocalWriteBarrier } from '../src/local-write-barrier.mjs';

const source = await readFile(new URL('../src/app.mjs', import.meta.url), 'utf8');
const closeOwner = source.slice(source.indexOf('async function installComposerDraftCloseGuard()'), source.indexOf('async function updateTemporaryModelOverride('));
const turn = () => new Promise(resolve => setImmediate(resolve));
function deferred() {
  let resolve, reject;
  const promise = new Promise((yes, no) => { resolve = yes; reject = no; });
  return { promise, resolve, reject };
}
async function harness({audioCheckpoint = async () => {}} = {}) {
  const writes = createLocalWriteBarrier();
  let onClose;
  let closed = false;
  let exitRemembered = false;
  const state = {};
  const context = vm.createContext({
    native: true, state, render() {}, createDraftCloseGuard, checkpointVideoOnExit:async()=>{}, checkpointAudioOnExit:audioCheckpoint,
    invoke:async command=>{assert.equal(command,'close_desktop_window_after_flush');closed=true;}, localSettingsWrites: writes,
    rememberDesktopExit() { exitRemembered = true; },
    composerDraftRouteGeneration: 0,
    flushOrdinaryComposerDraft: async () => {}, flushTemporaryDraft: async () => {},
    window: { __TAURI__: { window: { getCurrentWindow: () => ({
      onCloseRequested: callback => { onClose = callback; },
      destroy: async () => { closed = true; },
    }) } } },
  });
  vm.runInContext(closeOwner, context);
  await vm.runInContext('installComposerDraftCloseGuard()', context);
  return { writes, state, context, get closed() { return closed; }, get exitRemembered() { return exitRemembered; }, close: () => onClose({ preventDefault() {} }) };
}

test('actual window close owner waits for a pending local settings write', async () => {
  const h = await harness();
  const save = deferred();
  h.writes.track(save.promise);
  const closing = h.close();
  try {
    await turn();
    assert.equal(h.closed, false, 'window must stay open while settings are saving');
  } finally { save.resolve(); await closing; }
  assert.equal(h.closed, true);
  assert.equal(h.exitRemembered, true);
});

test('actual native window close waits for the audio checkpoint before final shutdown', async () => {
  const save = deferred(), h = await harness({audioCheckpoint: () => save.promise});
  const closing = h.close(); await turn();
  try {
    assert.equal(h.closed, false, 'native close must wait for the audio position');
    assert.equal(h.exitRemembered, false);
  } finally { save.resolve(); await closing; }
  assert.equal(h.closed, true);
});

test('a settings save submitted during audio checkpointing is drained before native close', async () => {
  const audio = deferred(), late = deferred();
  const h = await harness({audioCheckpoint: () => audio.promise});
  const closing = h.close(); await turn(); h.writes.track(late.promise); audio.resolve(); await turn();
  try { assert.equal(h.closed, false, 'a later settings write must survive the checkpoint wait'); }
  finally { late.resolve(); await closing; }
  assert.equal(h.closed, true);
});

test('a draft edit submitted during audio checkpointing is flushed before native close', async () => {
  const audio = deferred(), h = await harness({audioCheckpoint: () => audio.promise});
  let draft = 'before checkpoint'; const saved = [];
  h.context.flushOrdinaryComposerDraft = async () => { saved.push(draft); };
  const closing = h.close(); await turn();
  draft = 'latest user edit'; h.context.composerDraftRouteGeneration++;
  audio.resolve(); await closing; assert.equal(saved.at(-1), 'latest user edit');
});

test('a settings failure during close keeps the window and reports the failure', async () => {
  const h = await harness();
  const save = deferred();
  h.writes.track(save.promise);
  const closing = h.close();
  await turn();
  save.reject(new Error('synthetic settings write failed'));
  await closing;
  assert.equal(h.closed, false);
  assert.equal(h.exitRemembered, false);
  assert.match(h.state.error, /synthetic settings write failed/);
});

test('settings submitted while close is waiting are also drained', async () => {
  const h = await harness();
  const first = deferred(), second = deferred();
  h.writes.track(first.promise);
  const closing = h.close();
  await turn();
  h.writes.track(second.promise);
  first.resolve();
  try { await turn(); assert.equal(h.closed, false); }
  finally { second.resolve(); await closing; }
  assert.equal(h.closed, true);
});

test('completed writes are released and never retried by a later close', async () => {
  const barrier = createLocalWriteBarrier();
  let executions = 0;
  await barrier.track(Promise.resolve().then(() => { executions += 1; }));
  await barrier.flush();
  await barrier.flush();
  assert.equal(executions, 1);
});

test('a failure already settled while other owners drain is still reported once', async () => {
  const barrier = createLocalWriteBarrier();
  await assert.rejects(barrier.track(Promise.reject(new Error('not durable'))), /not durable/);
  await assert.rejects(barrier.flush(), /not durable/);
  await barrier.flush();
});

test('actual app-settings owner tracks queued saves before the IPC begins', async () => {
  const writes = createLocalWriteBarrier();
  const gate = deferred();
  const persistOwner = source.slice(source.indexOf('function persistNativeAppSettings('), source.indexOf('async function persistAppSettingsPatch('));
  const context = vm.createContext({ localSettingsWrites: writes, appSettingsSaveQueue: Promise.resolve(), pendingAppSettingsPatches: [],
    persistAppSettingsPatch: () => gate.promise });
  vm.runInContext(persistOwner, context);
  const saving = vm.runInContext("persistNativeAppSettings({themeColor:'purple'}, {})", context);
  assert.equal(writes.version, 1);
  let drained = false;
  const flush = writes.flush().then(() => { drained = true; });
  await turn();
  assert.equal(drained, false);
  gate.resolve();
  await Promise.all([saving, flush]);
});

test('actual model save owner keeps its local credential transaction inside the barrier', async () => {
  const writes = createLocalWriteBarrier(), gate = deferred();
  const modelOwner = source.slice(source.indexOf('function modelServiceEditorIsCurrent('), source.indexOf('async function revealModelServiceCredential()'));
  const context = vm.createContext({ native: true, localSettingsWrites: writes,
    state: { pane:'settings',settingsSection:'model-configuration',modelProviderId:'test-provider',
      modelServiceSettings: [{providerId:'test-provider',enabled:true,presetId:'preset',revision:1}],
      modelServiceDraft: {providerId:'test-provider',enabled:true,presetId:'preset',revision:1}, modelCredentialDraft:'synthetic-only',modelCredentialEdited:true },
    selectedModelService: () => ({providerId:'test-provider',revision:1}), render() {},
    tauriBridge: {}, invokeForAuthorizedDataAreaOperation: () => gate.promise,
    replaceModelService() {}, modelServiceConfigurationStatus() {},
  });
  attachModelSettingsController(context);
  vm.runInContext(modelOwner, context);
  const saving = vm.runInContext('saveModelServiceSettings()', context);
  assert.equal(writes.version, 1);
  let drained = false;
  const flush = writes.flush().then(() => { drained = true; });
  await turn();
  assert.equal(drained, false);
  gate.resolve({providerId:'test-provider',enabled:true,presetId:'preset',revision:2});
  await Promise.all([saving, flush]);
  assert.equal(context.state.modelCredentialDraft, '');
});

test('model read preserves edits made while the IPC was pending', async () => {
  const loader = source.slice(source.indexOf('async function loadModelServiceSettings('), source.indexOf('async function loadDesktopAppSettings('));
  const gate = deferred();
  const state = { modelProviderId: 'QWEN', modelServiceSettings: [{providerId:'QWEN',enabled:false,presetId:'preset',revision:1}],
    modelServiceDraft: {providerId:'QWEN',enabled:false,presetId:'preset',revision:1}, modelCredentialEdited:false };
  let resets = 0;
  const context = vm.createContext({ native:true,state,invoke:()=>gate.promise,
    selectedModelService:()=>state.modelServiceSettings[0], resetModelServiceDraft(){resets += 1;}, modelServiceConfigurationStatus:()=>'' });
  attachModelSettingsController(context);
  vm.runInContext(loader,context);
  const loading = vm.runInContext('loadModelServiceSettings({preserveUnsavedDraft:true})',context);
  state.modelServiceDraft.enabled = true;
  state.modelCredentialEdited = true;
  gate.resolve([{providerId:'QWEN',enabled:false,presetId:'preset',revision:1}]);
  await loading;
  assert.equal(resets,0);
  assert.equal(state.modelServiceDraft.enabled,true);
});

test('late model settings read cannot change the current data area', async () => {
  const loader = source.slice(source.indexOf('async function loadModelServiceSettings('), source.indexOf('async function loadDesktopAppSettings('));
  const gate = deferred();
  const first = {modelProviderId:'QWEN',modelServiceSettings:[{providerId:'QWEN',revision:1}], modelServiceDraft:null};
  const second = {modelProviderId:'OPENROUTER',modelServiceSettings:[{providerId:'OPENROUTER',revision:3}],status:'new-area'};
  const context = vm.createContext({native:true,state:first,invoke:()=>gate.promise,
    selectedModelService:()=>context.state.modelServiceSettings[0],resetModelServiceDraft(){},modelServiceConfigurationStatus:()=> 'old-area'});
  attachModelSettingsController(context);
  vm.runInContext(loader,context);
  const loading = vm.runInContext('loadModelServiceSettings()',context);
  context.state = second;
  gate.resolve([{providerId:'QWEN',revision:1}]);
  await loading;
  assert.equal(second.modelServiceSettings[0].providerId,'OPENROUTER');
  assert.equal(second.status,'new-area');
});

test('model read cannot roll back a newer saved revision', async () => {
  const loader = source.slice(source.indexOf('async function loadModelServiceSettings('), source.indexOf('async function loadDesktopAppSettings('));
  const gate = deferred();
  const state = {modelProviderId:'QWEN',modelServiceSettings:[{providerId:'QWEN',revision:1}],modelServiceDraft:null};
  const context = vm.createContext({native:true,state,invoke:()=>gate.promise,
    selectedModelService:()=>state.modelServiceSettings[0],resetModelServiceDraft(){},modelServiceConfigurationStatus:()=>''});
  attachModelSettingsController(context);
  vm.runInContext(loader,context);
  const loading = vm.runInContext('loadModelServiceSettings()',context);
  state.modelServiceSettings = [{providerId:'QWEN',enabled:true,revision:2}];
  gate.resolve([{providerId:'QWEN',enabled:false,revision:1}]);
  await loading;
  assert.equal(state.modelServiceSettings[0].revision,2);
  assert.equal(state.modelServiceSettings[0].enabled,true);
});

test('area settings reload keeps an unsaved model edit while refreshing its saved revision', async () => {
  const loader = source.slice(source.indexOf('async function loadModelServiceSettings('), source.indexOf('async function loadDesktopAppSettings('));
  const state = {
    pane: 'settings', settingsSection: 'model-configuration',
    modelProviderId: 'QWEN',
    modelServiceSettings: [{ providerId: 'QWEN', enabled: true, presetId: 'old', revision: 2 }],
    modelServiceDraft: { providerId: 'QWEN', enabled: false, presetId: 'old', revision: 2 },
    modelCredentialEdited: false,
    modelSettingsNotice: '', modelSettingsError: '',
  };
  const context = vm.createContext({ native: true, state,
    selectedModelService: () => state.modelServiceSettings.find(item => item.providerId === state.modelProviderId),
    invoke: async () => [{ providerId: 'QWEN', enabled: true, presetId: 'new', revision: 3 }],
    resetModelServiceDraft: () => { throw new Error('unsaved draft was discarded'); },
    modelServiceConfigurationStatus: () => 'configured',
  });
  attachModelSettingsController(context);
  vm.runInContext(loader, context);
  await vm.runInContext('loadModelServiceSettings({ preserveUnsavedDraft: true })', context);
  assert.equal(state.modelServiceDraft.enabled, false);
  assert.equal(state.modelServiceDraft.revision, 2);
  assert.equal(state.modelServiceSettings[0].revision, 3);
  assert.match(state.modelSettingsError, /重新选择服务商/);
});

test('stale model draft cannot overwrite a newer shared setting', async () => {
  const writes = createLocalWriteBarrier();
  let calls = 0;
  const modelOwner = source.slice(source.indexOf('async function saveModelServiceSettings()'), source.indexOf('async function revealModelServiceCredential()'));
  const state = { modelServiceDraft: { providerId: 'QWEN', enabled: false, presetId: 'old', revision: 2 }, modelSettingsNotice: '', modelSettingsError: '' };
  const context = vm.createContext({ native: true, localSettingsWrites: writes, state,
    selectedModelService: () => ({ providerId: 'QWEN', revision: 3 }), render() {},
    invoke: () => { calls += 1; }, replaceModelService() {}, modelServiceConfigurationStatus() {},
  });
  attachModelSettingsController(context);
  vm.runInContext(modelOwner, context);
  await vm.runInContext('saveModelServiceSettings()', context);
  assert.equal(calls, 0);
  assert.equal(writes.version, 0);
  assert.match(state.modelSettingsError, /重新选择服务商/);
});

test('both data areas receive independent knowledge and reminder state', () => {
  const constructor = source.slice(source.indexOf('function initializeAreaState('), source.indexOf('let state = initializeAreaState('));
  const context = vm.createContext({ TRANSCRIPTION_PREVIEW_STATE: { settings: {} }, DESKTOP_SETTINGS_CAPABILITIES: {} });
  vm.runInContext(constructor, context);
  const chat = vm.runInContext('initializeAreaState({})', context);
  const work = vm.runInContext('initializeAreaState({})', context);
  assert.deepEqual([...work.historyKnowledge.candidates], []);
  assert.deepEqual([...work.reminders.plans], []);
  assert.equal(work.backgroundRuntime.safeCode, 'NOT_READ');
  assert.equal(work.chatSearch, '');
  assert.equal(work.nanfengKnowledgeTask, null);
  assert.deepEqual([...work.v2CommittedExchanges], []);
  chat.historyKnowledge.candidates.push({ candidateId: 'chat-only' });
  chat.reminders.plans.push({ id: 'chat-only' });
  chat.v2CommittedExchanges.push({ id: 'chat-only' });
  assert.equal(work.historyKnowledge.candidates.length, 0);
  assert.equal(work.reminders.plans.length, 0);
  assert.equal(work.v2CommittedExchanges.length, 0);
});


test('successful explicit retry through the actual settings owner clears its old close failure', async () => {
  const h = await harness();
  const failure = new Error('synthetic refused settings save');
  let calls = 0;
  Object.assign(h.context, { appSettingsSaveQueue: Promise.resolve(), pendingAppSettingsPatches: [],
    persistAppSettingsPatch: async () => { if (++calls === 1) throw failure; } });
  const persistOwner = source.slice(source.indexOf('function persistNativeAppSettings('), source.indexOf('async function persistAppSettingsPatch('));
  vm.runInContext(persistOwner, h.context);
  await assert.rejects(vm.runInContext("persistNativeAppSettings({}, {nickname:'draft'})", h.context), error => error === failure);
  await vm.runInContext("persistNativeAppSettings({}, {nickname:'draft'})", h.context);
  await h.close();
  assert.equal(h.closed, true, 'a confirmed retry must not require a second window close');
  assert.equal(calls, 2, 'close must not resend or retry');
});

test('settings retry does not acknowledge a separate credential write failure', async () => {
  const h = await harness();
  Object.assign(h.context, { appSettingsSaveQueue: Promise.resolve(), pendingAppSettingsPatches: [], persistAppSettingsPatch: async () => {} });
  const persistOwner = source.slice(source.indexOf('function persistNativeAppSettings('), source.indexOf('async function persistAppSettingsPatch('));
  vm.runInContext(persistOwner, h.context);
  await assert.rejects(h.writes.track(Promise.reject(new Error('synthetic credential refusal'))), /credential refusal/);
  await vm.runInContext("persistNativeAppSettings({}, {nickname:'draft'})", h.context);
  await h.close();
  assert.equal(h.closed, false);
  assert.match(h.state.error, /credential refusal/);
});


test('successful settings recovery preserves an earlier unrelated failure too', async () => {
  const barrier = createLocalWriteBarrier();
  await assert.rejects(barrier.track(Promise.reject(new Error('credential refusal'))), /credential refusal/);
  await assert.rejects(barrier.track(Promise.reject(new Error('settings refusal')), 'app-settings'), /settings refusal/);
  await barrier.track(Promise.resolve(), 'app-settings');
  await assert.rejects(barrier.flush(), /credential refusal/);
});

test('an older successful write cannot acknowledge a newer failed write by its owner', async () => {
  const barrier = createLocalWriteBarrier();
  const earlier = deferred();
  const saving = barrier.track(earlier.promise, 'app-settings');
  await assert.rejects(barrier.track(Promise.reject(new Error('newer refusal')), 'app-settings'), /newer refusal/);
  earlier.resolve();
  await saving;
  await assert.rejects(barrier.flush(), /newer refusal/);
});
