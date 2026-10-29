import test from 'node:test';
import assert from 'node:assert/strict';
import { MODEL_SERVICE_PREVIEW_SETTINGS, renderAndroidSettingsShell } from '../src/android-settings-shell.mjs';
import { renderChatFirstShell, transcriptMetadata } from '../src/chat-shell.mjs';
import { projectedCost } from '../src/desktop-cost-estimator.mjs';

const services = () => MODEL_SERVICE_PREVIEW_SETTINGS.map(s => ({...s, enabled:true, credentialStored:true}));
test('Terra and Mercury can be selected from the existing Windows daily picker and settings', () => {
  const settings = services();
  const page = renderAndroidSettingsShell({page:'model-configuration',modelProviderId:'OPENROUTER',modelServiceSettings:settings});
  const picker = renderChatFirstShell({native:true,runtimeInfo:{platform:'windows'},data:{summary:{id:'synthetic'},exchange:{conversations:[],projects:[],knowledge:[],memory:[],relations:[]}},modelServiceSettings:settings,p6gModelPickerOpen:true,p6gSelection:{pickerTier:'DAILY'}});
  for (const id of ['GPT_6_1_TERRA','MERCURY_3']) {
    assert.ok(settings[0].presets.some(p=>p.id===id), id);
    assert.ok(page.includes(`data-preset-id="${id}"`),id);
    assert.ok(picker.includes(`data-model-id="${id}"`),id);
  }
  assert.ok(picker.indexOf('data-model-id="GPT_6_1_TERRA"') < picker.indexOf('data-model-id="GPT_6_LUNA"'));
  assert.ok(picker.indexOf('data-model-id="GPT_6_LUNA"') < picker.indexOf('data-model-id="MERCURY_3"'));
});
test('persisted Mercury without a display snapshot has a readable model name',()=>{
  assert.equal(transcriptMetadata({role:'assistant',actualModelId:'inception/mercury-3'}).model,'Mercury 3');
});
test('the exact Sonnet response alias is also recognized in the persisted footer',()=>{
  const modelSnapshot={providerId:'RELAYCORE_CLAUDE',modelId:'claude-sonnet-5-5',displayName:'Sonnet 5.5 · Kiro正价'};
  assert.equal(transcriptMetadata({role:'assistant',modelSnapshot,actualModelId:'claude-sonnet-5.5'}).modelVerification,'matched');
  assert.equal(transcriptMetadata({role:'assistant',modelSnapshot,actualModelId:'claude-sonnet-5.6'}).modelVerification,'mismatch');
});
test('new model estimates retain cache rates, unknown usage and authoritative provider amounts', () => {
  const common={inputTokens:1000,outputTokens:100,cachedInputTokens:500,occurredAtMs:Date.UTC(2026,9,28)};
  assert.equal(projectedCost({...common,modelId:'openai/gpt-6.1-terra'})?.chargeMicros,1025);
  assert.equal(projectedCost({...common,modelId:'inception/mercury-3'})?.chargeMicros,90);
  for(const modelId of ['openai/gpt-6.1-terra','inception/mercury-3']) {
    assert.equal(projectedCost({modelId,inputTokens:1000}),null);
    assert.deepEqual(projectedCost({...common,modelId,chargeMicros:7,currencyCode:'CNY'}),{chargeMicros:7,currencyCode:'CNY',costSource:'PROVIDER_RESPONSE'});
  }
});
