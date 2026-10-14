import { renderConfiguredChatShell as renderChatFirstShell } from './helpers/enabled-model-catalog.mjs';
import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { RELAYCORE_MODELS, relayCoreCompactName, relayCoreUiModelName, relayCoreFooterName, relayCorePickerName, relayCoreEgressVersion, createLunaRetrievalGrant } from '../src/relaycore-models.mjs';
import { MODEL_SERVICE_PREVIEW_SETTINGS, renderAndroidSettingsShell } from '../src/android-settings-shell.mjs';
import { compactModelName, desktopComposerModelCandidates, composerModelRuntimeDetail, transcriptMetadata } from '../src/chat-shell.mjs';

test('Relay six entries preserve identity with model and rate selection labels', () => {
  const fixture = JSON.parse(readFileSync(new URL('../../protocol/fixtures/relaycore-models-20261011.json', import.meta.url)));
  assert.deepEqual(RELAYCORE_MODELS, fixture.models);
  const candidates = desktopComposerModelCandidates(MODEL_SERVICE_PREVIEW_SETTINGS, [], true);
  for (const model of RELAYCORE_MODELS) {
    const candidate = candidates.find(item => item.modelId === model.presetId);
    assert.equal(candidate.providerId, model.providerId);
    assert.equal(candidate.displayName, relayCoreCompactName(model));
    assert.equal(compactModelName(relayCorePickerName(model)), relayCoreFooterName(model));
    const prefix = '';
    assert.equal(relayCorePickerName(model), `${relayCoreUiModelName(model)} · ${model.multiplier}×`);
    assert.equal(composerModelRuntimeDetail(candidate, false).text, `${prefix}未联网`);
    assert.equal(composerModelRuntimeDetail(candidate, true).text, `${prefix}${['RELAYCORE_CLAUDE', 'RELAYCORE_GEMINI'].includes(candidate.providerId) ? 'Luna · OpenRouter 检索' : '实时联网'}`);
    assert.equal(candidate.tiers[0], model.tier === 'DAILY' ? 'BALANCED' : 'DEEP');
  }
  for (const model of RELAYCORE_MODELS.filter(model => model.providerId === 'RELAYCORE_GEMINI')) {
    assert.equal(relayCoreCompactName(model), model.modelName);
    assert.equal(relayCorePickerName(model), `${model.modelName} · 0.28×`);
    assert.equal(compactModelName(`${model.modelName} · Gemini`), `${model.modelName.replace(/ Flash$| Pro$/,'')} · RC`);
    assert.equal(model.channel, 'Gemini');
  }
  const data = { summary: { id: 'relay-isolated' }, exchange: { conversations: [], projects: [], knowledge: [], memory: [], relations: [] } };
  for (const model of RELAYCORE_MODELS) {
    const html = renderChatFirstShell({ data, native: true, pane: 'chat', connection: {}, pendingNewConversationModelId: model.presetId });
    assert.ok(html.includes(`aria-label="选择模型：${relayCorePickerName(model)}"`));
    assert.equal((html.match(/class="composer-provider-mark"/g) || []).length, 1);
    assert.ok(html.includes('aria-description="RelayCore"'));
    assert.ok(html.includes('<sup class="composer-provider-mark" aria-hidden="true">RC</sup>'));
    const temporary = renderChatFirstShell({ data, native: true, pane: 'chat', connection: {}, temporaryConversation: { temporaryId: 'relay-temporary', modelOverrideId: model.presetId, messages: [], attachments: [], draftAttachmentIds: [] }, temporaryModelOpen: true });
    assert.ok(temporary.includes(`aria-label="选择模型：${relayCorePickerName(model)}"`));
    assert.equal((temporary.match(/class="composer-provider-mark"/g) || []).length, 1);
    assert.ok(temporary.includes('aria-description="RelayCore"'));
    assert.ok(temporary.includes(`<span>${relayCorePickerName(model)}</span>`));
  }

  for (const modelId of [null, 'CLAUDE_SONNET_5_5', 'GPT_6_1_SOL', 'DEEPSEEK_V4_FLASH']) {
    const html = renderChatFirstShell({ data, native: true, pane: 'chat', connection: {}, pendingNewConversationModelId: modelId });
    assert.ok(!html.includes('composer-provider-mark'));
    const temporary = renderChatFirstShell({ data, native: true, pane: 'chat', connection: {}, temporaryConversation: { temporaryId: 'other-temporary', modelOverrideId: modelId, messages: [], attachments: [], draftAttachmentIds: [] } });
    assert.ok(!temporary.includes('composer-provider-mark'));
  }
  assert.ok(!candidates.some(item => ['CLAUDE_FABLE_5_1', 'GPT_6_ASTRA'].includes(item.modelId)));
  assert.ok(candidates.some(item => item.modelId === 'RELAYCORE_GPT_6_ASTRA'));
});

test('Relay transcript follows returned model and frozen channel, with no rate or platform prefix', () => {
  const message = { modelSnapshot: { providerId: 'RELAYCORE_CLAUDE', modelId: 'claude-opus-5-5', displayName: 'Opus 5.5 · Kiro' }, actualModelId: 'claude-sonnet-5-5' };
  assert.equal(transcriptMetadata(message).model, 'Sonnet 5.5 · RC');
  assert.equal(message.modelSnapshot.displayName, 'Opus 5.5 · Kiro');
  assert.equal(transcriptMetadata(message).modelVerification, 'mismatch');
  assert.equal(transcriptMetadata({ ...message, actualModelId: null }).modelVerification, 'not_returned');
  assert.equal(transcriptMetadata({ ...message, actualModelId: null }).model, '返回模型未验证 · RC');
  assert.equal(transcriptMetadata({ ...message, modelSnapshot: { providerId: 'OPENROUTER', displayName: 'Claude Opus 5.5' } }).model, 'Claude Opus 5.5');
  assert.equal(transcriptMetadata({ importedModel: 'GPT-6.1 Sol · Pro1', source: 'IMPORTED' }).model, '6.1 Sol · RC');
  assert.equal(transcriptMetadata({ importedModel: 'Gemini 3.1 Pro · Gemini', source: 'IMPORTED' }).model, 'Gemini 3.1 · RC');
  assert.equal(transcriptMetadata({ modelSnapshot: { providerId: 'OPENROUTER', displayName: 'Gemini 3.8 Flash' } }).model, 'Gemini 3.8 Flash');
});

test('actual picker and composer share model and rate labels with channel only in captions', () => {
  const data = { summary: { id: 'relay-isolated' }, exchange: { conversations: [], projects: [], knowledge: [], memory: [], relations: [] } };
  const selection = 'RELAYCORE_OPUS_5_5';
  const base = { data, native: true, selectedConversationId: null, composerDraft: '', pane: 'chat', status: '', error: '', connection: {}, productSettings: { webSearchEnabled: false }, pendingNewConversationModelId: selection };
  const picker = renderChatFirstShell({ ...base, p6gModelPickerOpen: true, p6gSelection: { pickerTier: 'RELAYCORE' } });
  assert.match(picker, /<span>Opus 5\.5 · 0\.35×<\/span>/);
  assert.match(picker, /<small>未联网<\/small>/);
  assert.match(picker, /<small>未联网<\/small>/);
  const composer = renderChatFirstShell({ ...base, p6gModelPickerOpen: false });
  assert.match(composer, /<span>Opus 5\.5 · 0\.35×<\/span>/);
  const online = renderChatFirstShell({ ...base, pendingNewConversationModelId: 'RELAYCORE_SONNET_5_5', productSettings: { webSearchEnabled: true }, p6gModelPickerOpen: true, p6gSelection: { pickerTier: 'RELAYCORE' } });
  assert.ok(online.includes('Luna · OpenRouter 检索'));
  assert.ok(!online.includes('联网将用 GPT-6.1 Sol'));
  assert.ok(!online.includes('chat-retrieval-disclosure'));
  for (const [, caption] of online.matchAll(/<small[^>]*>([\s\S]*?)<\/small>/g)) {
    assert.ok(!caption.includes('RelayCore'));
  }
  const nativeSearch = renderChatFirstShell({ ...base, pendingNewConversationModelId: 'RELAYCORE_GPT_6_ASTRA', productSettings: { webSearchEnabled: true }, p6gModelPickerOpen: false });
  assert.ok(!nativeSearch.includes('chat-retrieval-disclosure'));
  assert.ok(!nativeSearch.includes('联网将用 GPT-6.1 Sol'));
});

test('temporary Relay picker discloses local recovery without claiming web search', () => {
  const data = { summary: { id: 'relay-isolated' }, exchange: { conversations: [], projects: [], knowledge: [], memory: [], relations: [] } };
  for (const enabled of [false, true]) {
    const html = renderChatFirstShell({ data, native: true, pane: 'chat', connection: {}, productSettings: { webSearchEnabled: enabled }, temporaryConversation: { temporaryId: 'relay-temporary', modelOverrideId: 'RELAYCORE_OPUS_5_5', messages: [], attachments: [], draftAttachmentIds: [] }, temporaryModelOpen: true });
    for (const model of RELAYCORE_MODELS) {
      const card = html.match(new RegExp(`data-model-id="${model.presetId}"[\\s\\S]*?</button>`))[0];
      const detail = '本地恢复标识';
      assert.ok(card.includes(`<small>${detail}</small>`), `${model.presetId} must disclose local-only meaning`);
      assert.ok(!card.includes('实时联网') && !card.includes('Sol 检索'));
      assert.ok(card.includes(`<strong>${relayCorePickerName(model)}</strong>`));
    }
  }
});

test('model settings keeps five platforms in one rail with RelayCore second and profiles nested', () => {
  const html = renderAndroidSettingsShell({ page: 'model-configuration', modelProviderId: 'RELAYCORE_GPT', native: true });
  const row = html.match(/aria-label="模型平台">([\s\S]*?)<\/div>/)[1];
  const ids = [...row.matchAll(/data-provider-id="([^"]+)"/g)].map(item => item[1]);
  assert.deepEqual(ids, ['OPENROUTER', 'RELAYCORE_CLAUDE', 'DEEPSEEK', 'ZHIPU', 'QWEN']);
  assert.equal([...row.matchAll(/role="tab"/g)].length, 5);
  assert.match(row, /data-provider-id="RELAYCORE_CLAUDE" class="selected"/);
  const sub = html.match(/aria-label="RelayCore 模型分组">([\s\S]*?)<\/div>/)[1];
  assert.equal([...sub.matchAll(/role="tab"/g)].length, 3);
  assert.match(html, /class="android-settings-segments android-settings-relay-tabs"/);
  assert.ok(!html.includes('三个分组独立保存'));
  assert.ok(!html.includes('请使用当前渠道对应'));
  assert.ok(!html.includes('0.12×'));
});

 test('Relay submenu partitions six entries once and leaves ordinary tiers unchanged', () => {
  const data = { summary: { id: 'relay-isolated' }, exchange: { conversations: [], projects: [], knowledge: [], memory: [], relations: [] } };
  const base = { data, native: true, pane: 'chat', connection: {}, p6gModelPickerOpen: true };
  for (const tier of ['DAILY', 'DEEP']) {
    const html = renderChatFirstShell({ ...base, p6gSelection: { pickerTier: tier } });
    for (const model of RELAYCORE_MODELS) assert.ok(!html.includes(`data-model-id="${model.presetId}"`));
  }
  const html = renderChatFirstShell({ ...base, pendingNewConversationModelId: 'RELAYCORE_OPUS_5_5', p6gSelection: { pickerTier: 'RELAYCORE' } });
  assert.match(html, /composer-model-sheet-section">日常/);
  assert.match(html, /composer-model-sheet-section">深度/);
  for (const model of RELAYCORE_MODELS) assert.equal(html.split(`data-model-id="${model.presetId}"`).length - 1, 1);
  assert.ok(html.indexOf('data-model-id="RELAYCORE_SONNET_5_5"') < html.indexOf('data-model-id="RELAYCORE_OPUS_5_5"'));
  assert.match(html, /data-model-id="RELAYCORE_OPUS_5_5"[^>]*aria-selected="true"/);
});

test('search disclosure follows the selected recipient and preserves ordinary native authorization', () => {
  for (const preset of ['RELAYCORE_OPUS_5_5', 'RELAYCORE_SONNET_5_5', 'RELAYCORE_GEMINI_3_8_FLASH']) {
    assert.equal(relayCoreEgressVersion(preset, true), 'normal-chat-luna-search-v1');
    assert.equal(relayCoreEgressVersion(preset, false), 'normal-chat-egress-v1');
  }
  for (const preset of [null, 'CLAUDE_SONNET_5_5', 'RELAYCORE_GPT_6_1_SOL', 'RELAYCORE_GPT_6_ASTRA']) assert.equal(relayCoreEgressVersion(preset, true), 'normal-chat-egress-v1');
});

test('changing current GPT channel preserves historical frozen channel names', () => {
  const old = { modelSnapshot: { providerId:'RELAYCORE_GPT',modelId:'gpt-6.1-sol',displayName:'GPT-6.1 Sol · Pro1' },actualModelId:'gpt-6.1-sol' };
  assert.equal(compactModelName(transcriptMetadata(old).model),'6.1 Sol · RC');
  assert.equal(old.modelSnapshot.displayName,'GPT-6.1 Sol · Pro1');
  assert.equal(compactModelName('GPT-6.1 Sol · Pro1 · 0.12×'),'6.1 Sol · RC');
  assert.equal(relayCoreCompactName(RELAYCORE_MODELS.find(model=>model.presetId==='RELAYCORE_GPT_6_1_SOL')),'GPT-6.1 Sol · Pro3');
});

test('Claude previous channel stays frozen after switching current Key to standard Kiro', () => {
  assert.equal(compactModelName('Opus 5.5 · Kiro · 0.08×'), 'Opus 5.5 · RC');
  assert.equal(relayCoreCompactName(RELAYCORE_MODELS[0]), 'Opus 5.5 · Kiro正价');
});

test('an explicit non-Relay provider does not gain RC from a legacy channel label',()=>{
 const snapshot={providerId:'OPENROUTER',modelId:'anthropic/claude-sonnet-5.5',displayName:'Sonnet 5.5 · Kiro正价'};
 assert.equal(transcriptMetadata({modelSnapshot:snapshot}).model,'Sonnet 5.5');
 assert.equal(snapshot.displayName,'Sonnet 5.5 · Kiro正价');
});

test('Astra uses version 6 in the selector and footer while keeping RC and its identity',()=>{
 const model=RELAYCORE_MODELS.find(m=>m.presetId==='RELAYCORE_GPT_6_ASTRA');
 assert.equal(relayCorePickerName(model),'6 Astra · 0.25×');
 assert.equal(compactModelName('GPT-6 Astra · Pro3'),'6 Astra · RC');
 assert.equal(compactModelName('Astra · RC'),'6 Astra · RC');
 assert.equal(model.modelId,'gpt-6-astra');
});

test('Luna consent fingerprint matches the Rust golden and changes with retry material', async () => {
  const fixture=JSON.parse(readFileSync(new URL('../../protocol/fixtures/luna-retrieval-grant-v1.json',import.meta.url)));
  const receipt=await createLunaRetrievalGrant(fixture.question,fixture.attachmentIds);
  assert.equal(receipt.materialFingerprint,fixture.fingerprint);
  assert.equal(receipt.providerId,'OPENROUTER');assert.equal(receipt.modelId,'openai/gpt-6-luna');
  assert.notEqual((await createLunaRetrievalGrant('old retry question',fixture.attachmentIds)).materialFingerprint,receipt.materialFingerprint);
  assert.notEqual((await createLunaRetrievalGrant(fixture.question,['file-a'])).materialFingerprint,receipt.materialFingerprint);
});
