import assert from 'node:assert/strict';
import test from 'node:test';
import vm from 'node:vm';
import { readFile } from 'node:fs/promises';
import { attachModelSettingsController } from './helpers/model-settings-controller-harness.mjs';
import { createLocalWriteBarrier } from '../src/local-write-barrier.mjs';
const source = await readFile(new URL('../src/app.mjs', import.meta.url), 'utf8');
const operations = source.slice(source.indexOf('function modelServiceEditorIsCurrent('), source.indexOf('const escape ='));
const tick = () => new Promise(done => setImmediate(done));
function fixture({ failRead = false, deferRead = false } = {}) {
  const old = { providerId: 'ZHIPU', providerDisplayName: '智谱', enabled: true, presetId: 'GLM_5_3', revision: 1, credentialStored: true };
  const committed = { ...old, enabled: false, revision: 2 };
  const owner = { pane: 'settings', settingsSection: 'model-configuration', modelProviderId: 'ZHIPU', dataArea: 'CHAT',
    modelServiceSettings: [old], modelServiceDraft: { ...old, presetId: 'GLM_OCR' }, modelCredentialDraft: 'synthetic-retry-key',
    modelCredentialEdited: true, modelCredentialVisible: false, modelSettingsSaving: false, modelSettingsTesting: false,
    modelSettingsNotice: '', modelSettingsError: '', dialog: null };
  const calls = []; let savedKey = null, releaseRead;
  const barrier = createLocalWriteBarrier(); let context;
  const invoke = async (command, args, areaOwner) => {
    calls.push({ command, args, owner: areaOwner });
    if (command === 'read_desktop_model_service_settings') {
      if (failRead) throw Error('synthetic metadata unavailable');
      if (deferRead) return new Promise(done => { releaseRead = () => done([{ ...committed }]); });
      return [{ ...committed }];
    }
    assert.equal(command, 'save_desktop_model_service_settings');
    if (args.args.expectedRevision !== committed.revision) throw Error('synthetic revision conflict');
    savedKey = args.args.apiKey; return { ...committed, revision: 3 };
  };
  context = vm.createContext({ state: owner, native: true, tauriBridge: {}, localSettingsWrites: barrier,
    render() {}, modelServiceConfigurationStatus: () => 'configured',
    selectedModelService: (current = context.state) => current.modelServiceSettings.find(x => x.providerId === current.modelProviderId),
    invokeForAuthorizedDataAreaOperation: (_bridge, command, args, current) => invoke(command, args, current) });
  attachModelSettingsController(context); vm.runInContext(operations, context);
  return { owner, context, calls, barrier, retry: () => context.saveModelServiceSettings(), get savedKey() { return savedKey; }, get releaseRead() { return releaseRead; } };
}
test('a failed credential save reloads authoritative metadata without losing its key or OCR target, then an explicit retry recovers the same write owner', async () => {
  const h = fixture(); await h.retry();
  assert.equal(h.owner.modelServiceDraft.revision, 2);
  assert.equal(h.owner.modelServiceSettings[0].revision, 2);
  assert.equal(h.owner.modelServiceDraft.presetId, 'GLM_OCR');
  assert.equal(h.owner.modelCredentialDraft, 'synthetic-retry-key');
  assert.equal(h.owner.modelCredentialEdited, true);
  assert.match(h.owner.modelSettingsError, /保存失败.*synthetic revision conflict/);
  assert.equal(h.savedKey, null, 'refresh never retries or writes the key');
  await h.retry(); await h.barrier.flush();
  assert.equal(h.savedKey, 'synthetic-retry-key');
  assert.equal(h.owner.modelCredentialDraft, '');
  assert.equal(h.owner.dialog.kind, 'model-credential-saved');
});
test('a failed metadata refresh keeps the original actionable save error and unsaved key', async () => {
  const h = fixture({ failRead: true }); await h.retry();
  assert.equal(h.calls.filter(x => x.command === 'read_desktop_model_service_settings').length, 1);
  assert.equal(h.owner.modelCredentialDraft, 'synthetic-retry-key');
  assert.equal(h.owner.modelServiceDraft.presetId, 'GLM_OCR');
  assert.match(h.owner.modelSettingsError, /保存失败.*synthetic revision conflict/);
  assert.ok(!h.owner.modelSettingsError.includes('synthetic metadata unavailable'));
  assert.equal(h.owner.modelSettingsSaving, false);
});
test('a failure refresh started in CHAT cannot replace the next area editor or its input', async () => {
  const h = fixture({ deferRead: true }); const running = h.retry(); await tick();
  assert.equal(typeof h.releaseRead, 'function');
  const next = { pane: 'settings', settingsSection: 'model-configuration', modelProviderId: 'QWEN',
    modelCredentialDraft: 'synthetic-new-area-input', modelCredentialEdited: true, modelSettingsError: 'new page error' };
  const before = JSON.stringify(next); h.context.state = next; h.releaseRead(); await running;
  assert.equal(JSON.stringify(next), before);
  assert.equal(h.owner.modelSettingsSaving, false);
});
