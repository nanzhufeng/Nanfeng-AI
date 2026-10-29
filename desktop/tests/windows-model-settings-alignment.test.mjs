import test from 'node:test';
import assert from 'node:assert/strict';
import { spawnSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';
import { readFileSync } from 'node:fs';
import { runInNewContext } from 'node:vm';
import { renderAndroidSettingsShell } from '../src/android-settings-shell.mjs';

const service = { providerId: 'DEEPSEEK', providerDisplayName: 'DeepSeek', enabled: true,
  presetId: 'DEEPSEEK_V4_PRO', presetDisplayName: 'DeepSeek V4 Pro', revision: 3,
  credentialStored: true, credentialStatus: 'Stored',
  presets: [{ id: 'DEEPSEEK_V4_PRO', displayName: 'DeepSeek V4 Pro', chatSelectable: true }],
  nonChatCapabilities: [] };
const control = (html, action) => html.match(new RegExp(`<button\\b[^>]*data-action="${action}"[^>]*>`))?.[0];

test('the complete desktop entry can be parsed before native packaging', () => {
  const result = spawnSync(process.execPath, ['--max-old-space-size=2048', '--check',
    fileURLToPath(new URL('../src/app.mjs', import.meta.url))], { encoding: 'utf8', timeout: 15000 });
  assert.equal(result.status, 0, result.stderr || result.error?.message);
});

test('typing a Key enables save and blocks probes without replacing the focused input', () => {
  const source = readFileSync(new URL('../src/app.mjs', import.meta.url), 'utf8');
  const start = source.indexOf("  if (event.target.id === 'model-service-api-key') {");
  const end = source.indexOf('  const previewRange =', start);
  assert.ok(start > 0 && end > start);
  const save = { disabled: true }, probe = { disabled: false }, reveal = { disabled: true };
  const feedback = { dataset: { providerId: 'QWEN' }, hidden: true,
    classList: { remove() {} }, setAttribute() {} };
  const controls = { '[data-model-provider-feedback]': feedback,
    '[data-action="save-model-service-settings"]': save,
    '[data-action="test-model-service-connection"]': probe,
    '[data-action="reveal-model-service-credential"]': reveal };
  const state = { modelProviderId: 'QWEN', modelCredentialEdited: false,
    modelCredentialVisible: false, modelSettingsSaving: false, modelSettingsTesting: false };
  const event = { target: { id: 'model-service-api-key', value: 'synthetic-only' } };
  const context = { state, event, app: { querySelector: selector => controls[selector] },
    selectedModelService: () => ({ credentialStored: false }) };
  runInNewContext(source.slice(start, end), context);
  assert.equal(save.disabled, false); assert.equal(probe.disabled, true);
  assert.equal(reveal.disabled, false); assert.equal(feedback.hidden, false);
  assert.equal(event.target.value, 'synthetic-only');
  event.target.value = '  ';
  runInNewContext(source.slice(start, end), context);
  assert.equal(save.disabled, true); assert.equal(probe.disabled, true);
});

test('an edited Key blocks a connection probe while the independent switch stays usable', () => {
  const html = renderAndroidSettingsShell({ page: 'model-configuration',
    modelServiceSettings: [service], modelProviderId: service.providerId,
    modelServiceDraft: { ...service }, modelCredentialDraft: 'SYNTHETIC_UNSAVED_KEY',
    modelCredentialEdited: true, modelSettingsSaving: true });
  assert.match(control(html, 'test-model-service-connection') || '', /\bdisabled\b/);
  assert.ok(control(html, 'toggle-model-service-enabled'));
  assert.doesNotMatch(control(html, 'toggle-model-service-enabled'), /\bdisabled\b/);
});
