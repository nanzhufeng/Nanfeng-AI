import test from 'node:test';
import assert from 'node:assert/strict';
import { createModelSettingsController } from '../src/model-settings-controller.mjs';

function harness() {
  const initial = providerId => ({ providerId, enabled: true, presetId: `${providerId}_PRO`, revision: 1, credentialStored: true });
  let disk = [initial('DEEPSEEK'), initial('QWEN')];
  const owner = { dataArea: 'CHAT', pane: 'settings', settingsSection: 'model-configuration',
    modelProviderId: 'DEEPSEEK', modelServiceSettings: structuredClone(disk),
    modelServiceDraft: { ...disk[0] }, modelCredentialDraft: 'SYNTHETIC_UNSAVED_KEY', modelCredentialEdited: true };
  let current = owner, intercept = null;
  const calls = [];
  const controller = createModelSettingsController({ currentOwner: () => current,
    invoke: async (captured, command, payload) => {
      calls.push({ owner: captured, command, payload });
      if (intercept) await intercept(command);
      if (command === 'read_desktop_model_service_settings') return structuredClone(disk);
      const args = payload.args;
      disk = disk.map(row => row.providerId !== args.providerId ? row : { ...row,
        ...(command.endsWith('_enabled') ? { enabled: args.enabled } : { presetId: args.presetId }), revision: row.revision + 1 });
      return structuredClone(disk.find(row => row.providerId === args.providerId));
    } });
  return { owner, controller, calls, disk: () => disk, intercept: value => { intercept = value; }, current: value => { current = value; } };
}

test('rapid toggles and a model selection preserve all choices and the unsubmitted Key', async () => {
  const h = harness();
  await Promise.all([h.controller.setEnabled(h.owner, false), h.controller.setPreset(h.owner, 'DEEPSEEK_FLASH'), h.controller.setEnabled(h.owner, true)]);
  assert.equal(h.disk()[0].enabled, true);
  assert.equal(h.disk()[0].presetId, 'DEEPSEEK_FLASH');
  assert.equal(h.owner.modelServiceDraft.revision, 4);
  assert.equal(h.owner.modelCredentialDraft, 'SYNTHETIC_UNSAVED_KEY');
  assert.ok(h.calls.every(c => !('apiKey' in c.payload.args)));
});

test('a failed switch recovers persisted state and keeps the next explicit click usable', async () => {
  const h = harness(); let failed = false;
  h.intercept(async command => { if (!failed && command.endsWith('_enabled')) { failed = true; throw new Error('synthetic refusal'); } });
  assert.equal(await h.controller.setEnabled(h.owner, false), false);
  assert.equal(h.owner.modelServiceDraft.enabled, true);
  assert.match(h.owner.modelSettingsError, /synthetic refusal/);
  assert.equal(await h.controller.setEnabled(h.owner, false), true);
  assert.equal(h.disk()[0].enabled, false);
  assert.equal(h.owner.modelSettingsError, '');
});

test('a response stays in its provider while another profile is being edited', async () => {
  const h = harness(); let release;
  const wait = new Promise(resolve => { release = resolve; });
  h.intercept(command => command.endsWith('_enabled') ? wait : undefined);
  const writing = h.controller.setEnabled(h.owner, false);
  await Promise.resolve();
  h.owner.modelProviderId = 'QWEN'; h.owner.modelServiceDraft = { ...h.disk()[1] };
  h.owner.modelSettingsNotice = ''; h.owner.modelSettingsError = '';
  release(); await writing;
  assert.equal(h.owner.modelServiceDraft.providerId, 'QWEN');
  assert.equal(h.owner.modelServiceDraft.enabled, true);
  assert.equal(h.owner.modelSettingsNotice, '');
  assert.equal(h.owner.modelProviderFeedback.DEEPSEEK.notice, '已停用。');
  assert.equal(h.owner.modelProviderFeedback.QWEN, undefined);
});

test('an earlier refresh cannot hide a newer committed choice', async () => {
  const h = harness(); let resolveRead;
  const old = structuredClone(h.disk());
  const stale = createModelSettingsController({ currentOwner: () => h.owner,
    invoke: async (_, command) => command.startsWith('read_') ? new Promise(resolve => { resolveRead = resolve; })
      : { ...old[0], enabled: false, revision: 2 } });
  const reading = stale.read(h.owner);
  await stale.setEnabled(h.owner, false);
  resolveRead(old); await reading;
  assert.equal(h.owner.modelServiceSettings[0].enabled, false);
  assert.equal(h.owner.modelServiceDraft.enabled, false);
});

test('late completion cannot change another data-area editor', async () => {
  const h = harness(); let release;
  const wait = new Promise(resolve => { release = resolve; });
  h.intercept(command => command.endsWith('_enabled') ? wait : undefined);
  const writing = h.controller.setEnabled(h.owner, false);
  await Promise.resolve();
  const work = { modelCredentialDraft: 'SYNTHETIC_WORK_KEY', modelSettingsNotice: '' };
  h.current(work); release(); await writing;
  assert.deepEqual(work, { modelCredentialDraft: 'SYNTHETIC_WORK_KEY', modelSettingsNotice: '' });
  assert.equal(h.disk()[0].enabled, false);
});

test('an OCR probe target never becomes a persisted chat-model selection', async () => {
  const h = harness();
  h.owner.modelProviderId = 'ZHIPU'; h.owner.modelServiceDraft = { providerId: 'ZHIPU', presetId: 'GLM_5_3' };
  await h.controller.setPreset(h.owner, 'GLM_OCR');
  assert.equal(h.owner.modelServiceDraft.presetId, 'GLM_OCR');
  assert.equal(h.calls.length, 0);
});


function zhipuHarness() {
 const h=harness();const row={providerId:'ZHIPU',enabled:true,presetId:'GLM_5_3_FLASH',revision:1,credentialStored:true};
 h.disk().push(structuredClone(row));h.owner.modelServiceSettings.push(structuredClone(row));h.owner.modelProviderId='ZHIPU';h.owner.modelServiceDraft={...row};return h;
}

test('OCR target survives an earlier ordinary preset queue and independent enabled toggles',async()=>{
 const h=zhipuHarness();const ordinary=h.controller.setPreset(h.owner,'GLM_5_3');await h.controller.setPreset(h.owner,'GLM_OCR');
 const off=h.controller.setEnabled(h.owner,false),on=h.controller.setEnabled(h.owner,true);await Promise.all([ordinary,off,on]);
 assert.equal(h.owner.modelServiceDraft.presetId,'GLM_OCR');assert.equal(h.owner.modelServiceDraft.enabled,true);
 assert.equal(h.disk().find(x=>x.providerId==='ZHIPU').presetId,'GLM_5_3');assert.equal(h.disk().find(x=>x.providerId==='ZHIPU').enabled,true);
 assert.equal(h.owner.modelCredentialDraft,'SYNTHETIC_UNSAVED_KEY');assert.ok(h.calls.every(c=>!c.payload.args||!('apiKey' in c.payload.args)));
});

test('an earlier settings read cannot replace a later explicit OCR target',async()=>{
 const h=zhipuHarness();let release;const wait=new Promise(done=>release=done);h.intercept(command=>command.startsWith('read_')?wait:undefined);
 const reading=h.controller.read(h.owner,{preserveUnsavedDraft:false});await h.controller.setPreset(h.owner,'GLM_OCR');release();
 assert.equal(await reading,false);assert.equal(h.owner.modelServiceDraft.presetId,'GLM_OCR');
});

test('a fresh read refreshes persisted flags while keeping OCR without a false unsaved-chat conflict',async()=>{
 const h=zhipuHarness();await h.controller.setPreset(h.owner,'GLM_OCR');Object.assign(h.disk().find(x=>x.providerId==='ZHIPU'),{enabled:false,revision:2});
 assert.equal(await h.controller.read(h.owner,{preserveUnsavedDraft:true}),true);
 assert.equal(h.owner.modelServiceDraft.presetId,'GLM_OCR');assert.equal(h.owner.modelServiceDraft.enabled,false);assert.equal(h.owner.modelServiceDraft.revision,2);
 assert.ok(!h.owner.modelSettingsError?.includes('另一数据区'));assert.equal(h.owner.modelCredentialDraft,'SYNTHETIC_UNSAVED_KEY');
});

test('a newer explicit ordinary choice exits temporary OCR and persists only the chat preset',async()=>{
 const h=zhipuHarness();await h.controller.setPreset(h.owner,'GLM_OCR');assert.equal(await h.controller.setPreset(h.owner,'GLM_5_3'),true);
 assert.equal(h.owner.modelServiceDraft.presetId,'GLM_5_3');assert.equal(h.disk().find(x=>x.providerId==='ZHIPU').presetId,'GLM_5_3');assert.ok(h.calls.every(c=>c.payload.args?.presetId!=='GLM_OCR'));
});

test('a rejected earlier ordinary write and recovery read preserve the newer OCR target',async()=>{
 const h=zhipuHarness();let first=true;h.intercept(command=>{if(first&&command.endsWith('_preset')){first=false;throw Error('SYNTHETIC_PRESET_REJECTED');}});
 const ordinary=h.controller.setPreset(h.owner,'GLM_5_3');await h.controller.setPreset(h.owner,'GLM_OCR');const off=h.controller.setEnabled(h.owner,false);
 assert.equal(await ordinary,false);assert.equal(await off,true);assert.equal(h.owner.modelServiceDraft.presetId,'GLM_OCR');
 assert.equal(h.disk().find(x=>x.providerId==='ZHIPU').presetId,'GLM_5_3_FLASH');assert.equal(h.owner.modelCredentialDraft,'SYNTHETIC_UNSAVED_KEY');
});
