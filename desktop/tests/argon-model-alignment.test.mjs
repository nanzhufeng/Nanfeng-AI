import test from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { MODEL_SERVICE_PREVIEW_SETTINGS, renderAndroidSettingsShell } from '../src/android-settings-shell.mjs';
import { renderChatFirstShell } from '../src/chat-shell.mjs';
import { projectedCost } from '../src/desktop-cost-estimator.mjs';
import { autoCandidate } from '../src/auto-chat-routing.mjs';

const id = 'GEMINI_4_ARGON', wire = 'google/gemini-4-argon';
const data = { summary: { id: 'synthetic' }, exchange: { conversations: [], projects: [], knowledge: [], memory: [], relations: [] } };
const services = () => MODEL_SERVICE_PREVIEW_SETTINGS.map(service => ({ ...service, enabled: true, credentialStored: true }));
const settings = registry => renderAndroidSettingsShell({ page: 'model-configuration', modelProviderId: 'OPENROUTER', modelServiceSettings: services(), openrouterRegistryStatus: registry });

test('Argon is offered through the existing settings and deep model picker with Gemini branding', () => {
  assert.ok(services()[0].presets.some(preset => preset.id === id));
  assert.match(settings({ status: 'NOT_VERIFIED' }), /data-preset-id="GEMINI_4_ARGON"/);
  const html = renderChatFirstShell({ data, native: true, pane: 'chat', composerDraft: '', modelServiceSettings: services(), p6gModelPickerOpen: true, p6gSelection: { pickerTier: 'DEEP' } });
  assert.match(html, /data-model-id="GEMINI_4_ARGON"/);
  assert.match(html, /data-model-brand="gemini"/);
  assert.match(html, /Gemini 4 Argon/);
  for (const platform of ['windows', 'macos']) {
    const review = renderAndroidSettingsShell({ page: 'feature-review', runtimeInfo: { platform } });
    assert.match(review, /建议保留：Gemini 4 Argon/, platform);
    assert.match(review, /缓存未收录时不可选/, platform);
  }
});

test('a verified cache without the new deployment cannot silently re-enable it', () => {
  const html = settings({ status: 'VERIFIED', mappedPresetIds: ['GPT_6_1_SOL'] });
  const control = html.match(/<button[^>]*data-preset-id="GEMINI_4_ARGON"[^>]*>/)?.[0];
  assert.ok(control);
  assert.match(control, /\bdisabled\b/);
  const current = settings({ status: 'VERIFIED', mappedPresetIds: [id] }).match(/<button[^>]*data-preset-id="GEMINI_4_ARGON"[^>]*>/)?.[0];
  assert.doesNotMatch(current, /\bdisabled\b/);
});

test('Argon token estimates preserve cached input while stored amounts and currencies remain authoritative', () => {
  const request = { modelId: wire, inputTokens: 1000, outputTokens: 100, cachedInputTokens: 500, occurredAtMs: Date.UTC(2026, 9, 16) };
  const estimate = projectedCost(request);
  assert.equal(estimate?.chargeMicros, 2050);
  assert.equal(estimate.currencyCode, 'USD');
  assert.equal(estimate.priceVersion, 'openrouter-gemini-4-argon-2026-10-15-v1');
  assert.equal(projectedCost({ modelId: wire }), null);
  assert.deepEqual(projectedCost({ ...request, chargeMicros: 777, currencyCode: 'CNY', costSource: 'LOCAL_ESTIMATE' }), { chargeMicros: 777, currencyCode: 'CNY', costSource: 'LOCAL_ESTIMATE' });
  assert.deepEqual(projectedCost({ ...request, chargeMicros: 1234, currencyCode: 'USD', costSource: 'PROVIDER_RESPONSE' }), { chargeMicros: 1234, currencyCode: 'USD', costSource: 'PROVIDER_RESPONSE' });
});

test('the shared deployment and bundled budget use the exact Argon identity', async () => {
  const manifest = JSON.parse(await readFile(new URL('../../protocol/model-service/model-service-v1.json', import.meta.url), 'utf8'));
  const profile = JSON.parse(await readFile(new URL('../../protocol/model-service/model-profiles-v1.json', import.meta.url), 'utf8')).profiles.find(p => p.presetId === id);
  assert.equal(manifest.models.find(model => model.presetId === id)?.modelId, wire);
  assert.ok(manifest.exceptions.find(exception => exception.id === 'OR_GEMINI_SEARCH_INTENT').appliesTo.includes(id));
  assert.equal(profile?.modelId, wire);
  assert.equal(profile.contextWindowTokens, 1048576);
  assert.equal(profile.maxOutputTokens, 65536);
});

test('adding an explicit deep choice does not change the existing Auto fallback order or grant unsupported audio transport', () => {
  const onlyArgon = [{ providerId: 'OPENROUTER', enabled: true, credentialStored: true, presets: [{ id, chatSelectable: true }] }];
  assert.equal(autoCandidate(onlyArgon, { text: '架构设计' }), null);
  assert.equal(autoCandidate(onlyArgon, { mimeTypes: ['audio/wav'] }), null);
});
