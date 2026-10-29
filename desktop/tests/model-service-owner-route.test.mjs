import { attachModelSettingsController } from './helpers/model-settings-controller-harness.mjs';
import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import vm from 'node:vm';

const source = readFileSync(new URL('../src/app.mjs', import.meta.url), 'utf8');
const operationBegin = source.indexOf('function modelServiceEditorIsCurrent(') >= 0
  ? source.indexOf('function modelServiceEditorIsCurrent(') : source.indexOf('async function saveModelServiceSettings(');
const operationEnd = source.indexOf('const escape =', operationBegin);
const selected = source.slice(source.indexOf('const selectedModelService =')).split('\n')[0];
const replaceBegin = source.indexOf('function replaceModelService(');
const replaceEnd = source.indexOf('async function loadModelServiceSettings(', replaceBegin);

function area(dataArea, providerId) {
  const services = ['DEEPSEEK', 'QWEN'].map(provider => ({ providerId: provider, providerDisplayName: provider,
    presetId: `${provider}_PRESET`, presetDisplayName: `${provider} model`, enabled: true, revision: 1, credentialStored: true }));
  return { dataArea, pane: 'settings', settingsSection: 'model-configuration', modelProviderId: providerId,
    modelServiceSettings: services, modelServiceDraft: { ...services.find(s => s.providerId === providerId) },
    modelSettingsSaving: false, modelSettingsTesting: false, modelSettingsNotice: '', modelSettingsError: '',
    modelCredentialDraft: '', modelCredentialEdited: false, modelCredentialVisible: false, dialog: null, status: 'ready' };
}

function harness() {
  const chat = area('CHAT', 'DEEPSEEK');
  const work = area('WORK', 'QWEN');
  const pending = [];
  const calls = [];
  let context;
  const invoke = (command, args, owner = context.state) => new Promise((resolve, reject) => {
    calls.push({ command, args, owner }); pending.push({ resolve, reject });
  });
  context = vm.createContext({ state: chat, native: true, tauriBridge: {},
    invoke, invokeForAuthorizedDataAreaOperation: (_bridge, command, args, owner) => invoke(command, args, owner),
    localSettingsWrites: { track: promise => promise }, render() {}, modelServiceConfigurationStatus: () => 'configuration ready' });
  attachModelSettingsController(context);
  vm.runInContext(selected + source.slice(replaceBegin, replaceEnd) + source.slice(operationBegin, operationEnd), context);
  return { chat, work, context, pending, calls };
}

const saved = () => ({ providerId: 'DEEPSEEK', providerDisplayName: 'DEEPSEEK', presetId: 'DEEPSEEK_PRESET',
  presetDisplayName: 'DEEPSEEK model', enabled: true, revision: 2, credentialStored: true });

for (const operation of ['saveModelServiceSettings', 'deleteModelServiceCredential', 'revealModelServiceCredential', 'testModelServiceConnection']) {
  for (const outcome of ['resolve', 'reject']) {
    test(`${operation} ${outcome} stays with its initiating area and preserves the WORK editor`, async () => {
      const h = harness();
      if (operation === 'deleteModelServiceCredential') h.chat.dialog = { kind: 'model-credential-delete', providerId: 'DEEPSEEK', providerDisplayName: 'DEEPSEEK', submitting: false };
      const running = h.context[operation]();
      await Promise.resolve();
      assert.equal(h.calls[0].owner, h.chat);
      h.work.modelCredentialDraft = 'SYNTHETIC_WORK_UNSAVED';
      h.work.modelCredentialEdited = true;
      h.work.modelSettingsTesting = true;
      h.work.dialog = { kind: 'unrelated-new-dialog' };
      const before = JSON.stringify(h.work);
      h.context.state = h.work;
      if (outcome === 'reject') h.pending[0].reject(new Error('safe synthetic refusal'));
      else h.pending[0].resolve(operation === 'revealModelServiceCredential' ? 'SYNTHETIC_REVEALED_CHAT_KEY' : saved());
      await running;
      assert.equal(JSON.stringify(h.work), before, 'late response must not write or reveal into the other editor');
      assert.equal(h.chat.modelSettingsSaving, false);
      assert.equal(h.chat.modelSettingsTesting, false);
      assert.equal(h.chat.modelCredentialVisible, false);
    });
  }
}

test('one finished connection test cannot unlock another area still waiting for its own result', async () => {
  const h = harness();
  const first = h.context.testModelServiceConnection();
  h.context.state = h.work;
  const second = h.context.testModelServiceConnection();
  assert.equal(h.pending.length, 2);
  h.pending[0].resolve({ succeeded: true });
  await first;
  assert.equal(h.chat.modelSettingsTesting, false);
  assert.equal(h.work.modelSettingsTesting, true);
  assert.equal(h.work.modelSettingsNotice, '');
  h.pending[1].resolve({ succeeded: true });
  await second;
  assert.equal(h.work.modelSettingsTesting, false);
  assert.match(h.work.modelSettingsNotice, /QWEN/);
});

test('a saved earlier provider cannot replace a newer provider draft or clear its typed key', async () => {
  const h = harness();
  const running = h.context.saveModelServiceSettings();
  await Promise.resolve();
  h.chat.modelProviderId = 'QWEN';
  h.chat.modelServiceDraft = { providerId: 'QWEN', presetId: 'QWEN_NEW', enabled: false, revision: 1 };
  const newer = h.chat.modelServiceDraft;
  h.chat.modelCredentialDraft = 'SYNTHETIC_NEWER_PROVIDER_KEY';
  h.chat.modelCredentialEdited = true;
  h.pending[0].resolve(saved());
  await running;
  assert.equal(h.chat.modelServiceDraft, newer);
  assert.equal(h.chat.modelCredentialEdited, true);
  assert.equal(h.chat.modelCredentialDraft, 'SYNTHETIC_NEWER_PROVIDER_KEY');
  assert.equal(h.chat.modelServiceSettings.find(s => s.providerId === 'DEEPSEEK').revision, 2);
});

test('revealing an earlier stored key preserves a newer typed draft in the same editor', async () => {
  const h = harness();
  const running = h.context.revealModelServiceCredential();
  h.chat.modelCredentialDraft = 'SYNTHETIC_NEWER_TYPED_KEY';
  h.chat.modelCredentialEdited = true;
  h.pending[0].resolve('SYNTHETIC_OLD_STORED_KEY');
  await running;
  assert.equal(h.chat.modelCredentialDraft, 'SYNTHETIC_NEWER_TYPED_KEY');
  assert.equal(h.chat.modelCredentialVisible, false);
});

test('a credential deletion result preserves a later dialog and unsaved replacement key', async () => {
  const h = harness();
  h.chat.dialog = { kind: 'model-credential-delete', providerId: 'DEEPSEEK', providerDisplayName: 'DEEPSEEK', submitting: false };
  const running = h.context.deleteModelServiceCredential();
  const newer = { kind: 'unrelated-new-dialog' };
  h.chat.dialog = newer;
  h.chat.modelCredentialDraft = 'SYNTHETIC_NEWER_REPLACEMENT_KEY';
  h.chat.modelCredentialEdited = true;
  h.pending[0].resolve({ ...saved(), credentialStored: false });
  await running;
  assert.equal(h.chat.dialog, newer);
  assert.equal(h.chat.modelCredentialDraft, 'SYNTHETIC_NEWER_REPLACEMENT_KEY');
  assert.equal(h.chat.modelCredentialEdited, true);
});

function zhipuProbe(h, presetId='GLM_5_3') {
 const service={providerId:'ZHIPU',providerDisplayName:'智谱',enabled:true,presetId:'GLM_5_3',presetDisplayName:'GLM 5.3',revision:1,credentialStored:true,
  presets:[{id:'GLM_5_3',displayName:'GLM 5.3'}],nonChatCapabilities:[{id:'GLM_OCR',displayName:'GLM OCR'}]};
 h.chat.modelServiceSettings=[service];h.chat.modelProviderId='ZHIPU';h.chat.modelServiceDraft={...service,presetId};
 return service;
}
const finishProbe=(h,outcome)=>outcome==='resolve'?h.pending[0].resolve({succeeded:true}):h.pending[0].reject(new Error('SYNTHETIC_CURRENT_PROBE_FAILURE'));

for(const outcome of ['resolve','reject'])test(`a stable current probe still shows its ${outcome} result`,async()=>{
 const h=harness();zhipuProbe(h);const running=h.context.testModelServiceConnection();finishProbe(h,outcome);await running;
 assert.equal(h.calls.length,1);assert.equal(h.calls[0].args.args.presetId,'GLM_5_3');
 if(outcome==='resolve')assert.match(h.chat.modelSettingsNotice,/连接成功.*GLM 5.3/);else assert.match(h.chat.modelSettingsError,/SYNTHETIC_CURRENT_PROBE_FAILURE/);
 assert.equal(h.chat.modelSettingsTesting,false);
});

for(const outcome of ['resolve','reject'])test(`a delayed chat probe ${outcome} cannot become feedback for the newly selected OCR target`,async()=>{
 const h=harness();zhipuProbe(h);const running=h.context.testModelServiceConnection();
 await h.context.modelSettingsController.setPreset(h.chat,'GLM_OCR');finishProbe(h,outcome);await running;
 assert.equal(h.chat.modelServiceDraft.presetId,'GLM_OCR');assert.equal(h.chat.modelServiceSettings[0].presetId,'GLM_5_3');assert.equal(h.chat.modelServiceSettings[0].revision,1);
 assert.equal(h.chat.modelSettingsNotice,'');assert.equal(h.chat.modelSettingsError,'');assert.equal(h.chat.modelSettingsTesting,false);assert.equal(h.calls.length,1);
});

for(const roundtrip of [false,true])for(const outcome of ['resolve','reject'])test(`a delayed ${roundtrip?'roundtrip':'OCR'} probe ${outcome} cannot overwrite a newer chat selection before its write receipt`,async()=>{
 const h=harness();zhipuProbe(h,roundtrip?'GLM_5_3':'GLM_OCR');const running=h.context.testModelServiceConnection();
 if(roundtrip)await h.context.modelSettingsController.setPreset(h.chat,'GLM_OCR');
 const writing=h.context.modelSettingsController.setPreset(h.chat,'GLM_5_3');
 for(let i=0;i<8&&h.calls.length<2;i++)await Promise.resolve();assert.equal(h.calls.length,2);
 finishProbe(h,outcome);await running;const observed={notice:h.chat.modelSettingsNotice,error:h.chat.modelSettingsError};
 h.pending[1].resolve({...h.chat.modelServiceSettings[0],presetId:'GLM_5_3',revision:2});await writing;
 assert.deepEqual(observed,{notice:'',error:''});assert.equal(h.chat.modelServiceDraft.presetId,'GLM_5_3');assert.equal(h.chat.modelServiceSettings[0].revision,2);
});

function credentialTargetHarness() {
 const h=harness();const record={providerId:'ZHIPU',providerDisplayName:'智谱',enabled:true,presetId:'GLM_5_3',presetDisplayName:'GLM-5.3',revision:1,credentialStored:false};
 h.chat.modelProviderId='ZHIPU';h.chat.modelServiceSettings=[record];h.chat.modelServiceDraft={providerId:'ZHIPU',enabled:true,presetId:'GLM_5_3',revision:1};h.chat.modelCredentialDraft='SYNTHETIC_SAVE_TARGET';h.chat.modelCredentialEdited=true;
 h.saved=(revision=2,extra={})=>({...record,revision,credentialStored:true,...extra});return h;
}
const settleCredentialTarget=()=>new Promise(resolve=>setImmediate(resolve));
test('credential save retains an already selected temporary OCR target and refreshes only authoritative metadata',async()=>{
 const h=credentialTargetHarness();await h.context.modelSettingsController.setPreset(h.chat,'GLM_OCR');const saving=h.context.saveModelServiceSettings();await settleCredentialTarget();h.pending[0].resolve(h.saved());await saving;
 assert.equal(h.chat.modelServiceDraft.presetId,'GLM_OCR');assert.equal(h.chat.modelServiceDraft.revision,2);assert.equal(h.chat.modelServiceSettings[0].presetId,'GLM_5_3');assert.equal(h.chat.modelCredentialDraft,'');assert.equal(h.chat.modelCredentialEdited,false);assert.equal(h.chat.dialog.kind,'model-credential-saved');
});
test('credential save receipt cannot reset a newer OCR target selected while its request is pending',async()=>{
 const h=credentialTargetHarness(),saving=h.context.saveModelServiceSettings();await settleCredentialTarget();await h.context.modelSettingsController.setPreset(h.chat,'GLM_OCR');h.pending[0].resolve(h.saved());await saving;
 assert.equal(h.chat.modelServiceDraft.presetId,'GLM_OCR');assert.equal(h.chat.modelServiceDraft.revision,2);assert.equal(h.chat.modelServiceSettings[0].presetId,'GLM_5_3');
});
test('credential save preserves a newer queued ordinary model until that model receives its own result',async()=>{
 const h=credentialTargetHarness(),saving=h.context.saveModelServiceSettings();await settleCredentialTarget();const choice=h.context.modelSettingsController.setPreset(h.chat,'GLM_5_3_FLASH');assert.equal(h.chat.modelServiceDraft.presetId,'GLM_5_3_FLASH');h.pending[0].resolve(h.saved());await saving;await settleCredentialTarget();const projectedBeforeChoice=h.chat.modelServiceDraft.presetId;assert.equal(h.calls.length,2);h.pending[1].resolve(h.saved(3,{presetId:'GLM_5_3_FLASH',presetDisplayName:'Flash'}));await choice;
 assert.equal(projectedBeforeChoice,'GLM_5_3_FLASH','the credential result cannot replace a pending user model choice');assert.equal(h.chat.modelServiceDraft.presetId,'GLM_5_3_FLASH');assert.equal(h.chat.modelServiceSettings[0].presetId,'GLM_5_3_FLASH');
});
test('current ordinary credential save still confirms and clears only its own typed key',async()=>{
 const h=credentialTargetHarness(),saving=h.context.saveModelServiceSettings();await settleCredentialTarget();h.pending[0].resolve(h.saved());await saving;
 assert.equal(h.chat.modelServiceDraft.presetId,'GLM_5_3');assert.equal(h.chat.modelServiceDraft.revision,2);assert.equal(h.chat.modelCredentialDraft,'');assert.equal(h.chat.modelCredentialEdited,false);assert.equal(h.chat.dialog.kind,'model-credential-saved');
});
test('an earlier queued enabled receipt does not cancel the current credential save confirmation',async()=>{
 const h=credentialTargetHarness(),enabled=h.context.modelSettingsController.setEnabled(h.chat,false);await settleCredentialTarget();const saving=h.context.saveModelServiceSettings();h.pending[0].resolve(h.saved(2,{enabled:false,credentialStored:false}));await enabled;await settleCredentialTarget();assert.equal(h.calls.length,2);assert.equal(h.calls[1].args.args.expectedRevision,2);assert.equal(h.calls[1].args.args.enabled,false);h.pending[1].resolve(h.saved(3,{enabled:false}));await saving;
 assert.equal(h.chat.modelServiceDraft.enabled,false);assert.equal(h.chat.modelServiceDraft.revision,3);assert.equal(h.chat.modelCredentialDraft,'');assert.equal(h.chat.modelCredentialEdited,false);assert.equal(h.chat.dialog.kind,'model-credential-saved');
});

function credentialDeleteHarness() {
 const h=credentialTargetHarness();h.chat.modelServiceSettings[0].credentialStored=true;
 h.chat.modelCredentialDraft='';h.chat.modelCredentialEdited=false;
 h.chat.dialog={kind:'model-credential-delete',providerId:'ZHIPU',providerDisplayName:'智谱',submitting:false};
 h.deleted=(revision=1,extra={})=>h.saved(revision,{credentialStored:false,...extra});return h;
}
test('credential deletion retains an already selected temporary OCR target with the committed deletion revision',async()=>{
 const h=credentialDeleteHarness();await h.context.modelSettingsController.setPreset(h.chat,'GLM_OCR');h.chat.modelCredentialDraft='SYNTHETIC_PREVIOUS_REVEALED_KEY';h.chat.modelCredentialVisible=true;
 const deleting=h.context.deleteModelServiceCredential();h.pending[0].resolve(h.deleted());await deleting;
 assert.equal(h.chat.modelServiceDraft.presetId,'GLM_OCR');assert.equal(h.chat.modelServiceDraft.revision,1);assert.equal(h.chat.modelServiceSettings[0].presetId,'GLM_5_3');assert.equal(h.chat.modelServiceSettings[0].credentialStored,false);assert.equal(h.chat.modelCredentialDraft,'');assert.equal(h.chat.modelCredentialVisible,false);assert.equal(h.chat.dialog.kind,'model-settings-feedback');
});
test('a settings read begun before deletion cannot restore the deleted credential presence',async()=>{
 const h=credentialDeleteHarness();const reading=h.context.modelSettingsController.read(h.chat);const deleting=h.context.deleteModelServiceCredential();assert.equal(h.calls.length,2);h.pending[1].resolve(h.deleted());await deleting;h.pending[0].resolve([h.saved(1)]);assert.equal(await reading,false);
 assert.equal(h.chat.modelServiceSettings[0].credentialStored,false);assert.equal(h.chat.modelServiceDraft.revision,1);
});
test('credential deletion metadata preserves a newer ordinary model intent until its own receipt',async()=>{
 const h=credentialDeleteHarness();const deleting=h.context.deleteModelServiceCredential();const selecting=h.context.modelSettingsController.setPreset(h.chat,'GLM_5_3_FLASH');await settleCredentialTarget();assert.equal(h.calls.length,2);h.pending[0].resolve(h.deleted());await deleting;
 assert.equal(h.chat.modelServiceDraft.presetId,'GLM_5_3_FLASH');assert.equal(h.chat.modelServiceDraft.revision,1);assert.equal(h.chat.modelServiceSettings[0].presetId,'GLM_5_3');h.pending[1].resolve(h.deleted(2,{presetId:'GLM_5_3_FLASH'}));await selecting;assert.equal(h.chat.modelServiceDraft.revision,2);assert.equal(h.chat.modelServiceSettings[0].presetId,'GLM_5_3_FLASH');
});
test('a failed current credential deletion retains the OCR target, stored presence and original retry dialog',async()=>{
 const h=credentialDeleteHarness();await h.context.modelSettingsController.setPreset(h.chat,'GLM_OCR');h.chat.modelCredentialDraft='SYNTHETIC_REVEALED_RETAIN';h.chat.modelCredentialVisible=true;const before=JSON.stringify(h.chat.modelServiceSettings);const deleting=h.context.deleteModelServiceCredential();h.pending[0].reject(new Error('SYNTHETIC_DELETE_DENIED'));await deleting;
 assert.equal(h.chat.modelServiceDraft.presetId,'GLM_OCR');assert.equal(h.chat.modelServiceDraft.revision,1);assert.equal(JSON.stringify(h.chat.modelServiceSettings),before);assert.equal(h.chat.modelCredentialDraft,'SYNTHETIC_REVEALED_RETAIN');assert.equal(h.chat.dialog.kind,'model-credential-delete');assert.equal(h.chat.dialog.submitting,false);assert.match(h.chat.dialog.failure,/SYNTHETIC_DELETE_DENIED/);
});
test('ordinary credential deletion still clears only its unchanged current editor and confirms success',async()=>{
 const h=credentialDeleteHarness();h.chat.modelCredentialDraft='SYNTHETIC_PREVIOUS_REVEALED_KEY';h.chat.modelCredentialVisible=true;const deleting=h.context.deleteModelServiceCredential();h.pending[0].resolve(h.deleted());await deleting;
 assert.equal(h.chat.modelServiceDraft.presetId,'GLM_5_3');assert.equal(h.chat.modelServiceDraft.revision,1);assert.equal(h.chat.modelCredentialDraft,'');assert.equal(h.chat.modelCredentialVisible,false);assert.equal(h.chat.modelSettingsSaving,false);assert.equal(h.chat.dialog.kind,'model-settings-feedback');assert.equal(h.chat.dialog.failed,false);
});

for(const operation of ['saveModelServiceSettings','deleteModelServiceCredential'])test(`${operation} invalidates an older settings read without projecting over a newer provider editor`,async()=>{
 const h=harness();const original=JSON.parse(JSON.stringify(h.chat.modelServiceSettings));const reading=h.context.modelSettingsController.read(h.chat);
 if(operation==='deleteModelServiceCredential')h.chat.dialog={kind:'model-credential-delete',providerId:'DEEPSEEK',providerDisplayName:'DEEPSEEK',submitting:false};
 const writing=h.context[operation]();await settleCredentialTarget();assert.equal(h.calls.length,2);h.chat.modelProviderId='QWEN';const draft={...h.chat.modelServiceSettings[1],presetId:'QWEN_UNSAVED_NEW'};h.chat.modelServiceDraft=draft;h.chat.modelCredentialDraft='SYNTHETIC_NEW_PROVIDER_KEY';h.chat.modelCredentialEdited=true;
 h.pending[1].resolve({...saved(),credentialStored:operation==='saveModelServiceSettings'});await writing;h.pending[0].resolve(original);assert.equal(await reading,false);assert.equal(h.chat.modelServiceDraft,draft);assert.equal(h.chat.modelCredentialDraft,'SYNTHETIC_NEW_PROVIDER_KEY');assert.equal(h.chat.modelServiceSettings[0].revision,2);assert.equal(h.chat.modelServiceSettings[0].credentialStored,operation==='saveModelServiceSettings');
});
