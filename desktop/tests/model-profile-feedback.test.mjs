import test from 'node:test';
import assert from 'node:assert/strict';
import vm from 'node:vm';
import { readFileSync } from 'node:fs';
const src = readFileSync(new URL('../src/app.mjs', import.meta.url), 'utf8');
const extract = (a, b) => src.slice(src.indexOf(a), src.indexOf(b, src.indexOf(a)));
function harness() {
  const profiles = ['RELAYCORE_CLAUDE', 'RELAYCORE_GPT', 'RELAYCORE_GEMINI'].map(providerId => ({ providerId, providerDisplayName: providerId, presetId: 'preset', enabled: true, revision: 1, credentialStored: true }));
  const state = { modelProviderId: profiles[0].providerId, modelServiceSettings: profiles, modelCredentialDraft: 'synthetic-test-key', modelCredentialEdited: true };
  let complete;
  const pending = new Promise(resolve => { complete = resolve; });
  const ctx = vm.createContext({ state, nanfengIsland: {feedback: (...args) => {state.islandFeedback=args;}}, native: true, modelEnabledIntents: new Map(), render: () => {}, selectedModelService: () => profiles.find(p => p.providerId === state.modelProviderId), invoke: () => pending, replaceModelService: () => {}, modelServiceConfigurationStatus: () => '' });
  vm.runInContext(extract('function setModelProviderFeedback(', 'function clearModelCredentialDraftOutsideEditor('), ctx);
  vm.runInContext(extract('let modelEnabledQueue', 'async function setModelServicePreset('), ctx);
  vm.runInContext(extract('async function saveModelServiceSettings()', 'async function revealModelServiceCredential()'), ctx);
  vm.runInContext(extract('async function testModelServiceConnection()', 'const escape ='), ctx);
  ctx.resetModelServiceDraft();
  return { state, ctx, complete, profiles };
}
test('each profile retains its own feedback; an untouched group has no success claim', () => {
  const { state, ctx, profiles } = harness();
  ctx.setModelProviderFeedback(profiles[0].providerId, 'API Key 已安全保存在本机。');
  state.modelProviderId = profiles[1].providerId; ctx.resetModelServiceDraft();
  assert.equal(state.modelSettingsNotice, '');
  ctx.setModelProviderFeedback(profiles[1].providerId, '', '连接测试失败');
  state.modelProviderId = profiles[2].providerId; ctx.resetModelServiceDraft();
  assert.equal(state.modelSettingsNotice, ''); assert.equal(state.modelSettingsError, '');
  state.modelProviderId = profiles[0].providerId; ctx.resetModelServiceDraft();
  assert.equal(state.modelSettingsNotice, 'API Key 已安全保存在本机。'); assert.equal(state.modelSettingsError, '');
});
test('late Claude save does not overwrite Gemini draft or display Claude success there', async () => {
  const { state, ctx, complete, profiles } = harness();
  state.modelCredentialDraft = 'synthetic-test-key'; state.modelCredentialEdited = true;
  const save = ctx.saveModelServiceSettings();
  state.modelProviderId = profiles[2].providerId; ctx.resetModelServiceDraft();
  state.modelCredentialDraft = 'other-synthetic-draft'; state.modelCredentialEdited = true;
  complete({ ...profiles[0], revision: 2 }); await save;
  assert.equal(state.modelSettingsNotice, ''); assert.equal(state.dialog, undefined);
  assert.equal(state.modelCredentialDraft, 'other-synthetic-draft');
  assert.equal(state.modelServiceDraft.providerId, profiles[2].providerId);
  assert.equal(state.modelProviderFeedback[profiles[0].providerId].notice, 'API Key 已安全保存在本机。');
});
test('late connection result belongs to the tested profile', async () => {
  const { state, ctx, complete, profiles } = harness();
  const probe = ctx.testModelServiceConnection();
  state.modelProviderId = profiles[1].providerId; ctx.resetModelServiceDraft();
  complete({}); await probe;
  assert.equal(state.modelSettingsNotice, '');
  assert.match(state.modelProviderFeedback[profiles[0].providerId].notice, /连接成功/);
  assert.equal(state.modelSettingsTesting, false);
});

test('delayed credential reveal cannot overwrite the next profile or a newer typed draft', async () => {
  for (const switched of [true, false]) {
    const { state, ctx, complete, profiles } = harness();
    state.pane = 'settings'; state.settingsSection = 'model-configuration';
    vm.runInContext(extract('async function revealModelServiceCredential()', 'async function testModelServiceConnection()'), ctx);
    const reveal = ctx.revealModelServiceCredential();
    if (switched) { state.modelProviderId = profiles[2].providerId; ctx.resetModelServiceDraft(); }
    state.modelCredentialDraft = 'new-synthetic-draft'; state.modelCredentialEdited = true;
    complete('old-synthetic-revealed-value'); await reveal;
    assert.equal(state.modelCredentialDraft, 'new-synthetic-draft');
    assert.equal(state.modelCredentialVisible, false);
  }
});

test('only an edited Key blocks probing; model and switch choices do not need the Save button', async () => {
  for (const kind of ['key', 'preset', 'enabled']) {
    const { state, ctx, complete } = harness();
    if (kind === 'key') state.modelCredentialEdited = true;
    if (kind === 'preset') state.modelServiceDraft.presetId = 'unsaved-preset';
    if (kind === 'enabled') state.modelServiceDraft.enabled = false;
    complete({}); await ctx.testModelServiceConnection();
    if (kind === 'key') { assert.match(state.modelSettingsError, /API Key 尚未保存/); assert.equal(state.modelSettingsTesting, undefined); }
    else { assert.equal(state.modelSettingsError, ''); assert.equal(state.modelSettingsTesting, false); }
  }
});

test('actual settings view presents only current-profile inline status and escapes failure text', async () => {
  const { renderAndroidSettingsShell } = await import('../src/android-settings-shell.mjs');
  const { state, ctx, profiles } = harness();
  ctx.setModelProviderFeedback(profiles[0].providerId, 'API Key 已安全保存在本机。');
  state.modelProviderId = profiles[2].providerId; ctx.resetModelServiceDraft();
  const view = () => renderAndroidSettingsShell({ page: 'model-configuration', modelProviderId: state.modelProviderId, modelSettingsNotice: state.modelSettingsNotice, modelSettingsError: state.modelSettingsError });
  assert.ok(!view().includes('API Key 已安全保存在本机。'));
  state.modelProviderId = profiles[0].providerId; ctx.resetModelServiceDraft();
  assert.ok(view().includes('API Key 已安全保存在本机。'));
  assert.ok(view().includes('尚未测试连接'));
  assert.equal(state.islandFeedback, undefined);
  ctx.setModelProviderFeedback(profiles[0].providerId, '', '<script>failure</script>');
  assert.ok(view().includes('&lt;script&gt;failure&lt;/script&gt;'));
});
