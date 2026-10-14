import test from 'node:test';
import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import {renderAndroidSettingsShell, MODEL_SERVICE_PREVIEW_SETTINGS} from '../src/android-settings-shell.mjs';
import {renderChatFirstShell} from '../src/chat-shell.mjs';
import {modelSettingsProviderTarget} from '../src/model-settings-browser.mjs';

test('all provider models remain directly selectable and only RC has family tabs', () => {
  for (const service of MODEL_SERVICE_PREVIEW_SETTINGS) {
    const html = renderAndroidSettingsShell({page:'model-configuration', modelProviderId:service.providerId});
    const rows = [...html.matchAll(/data-preset-id="([^"]+)" role="radio"/g)].map(m=>m[1]);
    assert.deepEqual(new Set(rows), new Set([...service.presets,...(service.nonChatCapabilities || [])].map(p=>p.id)));
    assert.equal(html.includes('aria-label="RelayCore 模型分组"'), service.providerId.startsWith('RELAYCORE_'));
    assert.equal([...html.matchAll(/aria-label="模型平台"/g)].length, 1);
  }
  const openrouter = renderAndroidSettingsShell({page:'model-configuration'});
  assert.equal([...openrouter.matchAll(/role="radio"/g)].length,7);
  assert.ok(openrouter.includes('model-settings-model-list scrollable'));
  assert.match(openrouter,/data-preset-id="MUSE_WONDER"/);
  assert.ok(!openrouter.includes('Claude Fable 5') && !openrouter.includes('Kimi K3'));
});

test('future catalog expansion scrolls without silently dropping new models', () => {
  const initial=MODEL_SERVICE_PREVIEW_SETTINGS[0];
  const extra={id:'FUTURE_PUBLIC_FIXTURE',displayName:'Future model',chatSelectable:true,description:'Fixture only'};
  const settings=[{...initial,presets:[...initial.presets,extra]}];
  const html=renderAndroidSettingsShell({page:'model-configuration',modelServiceSettings:settings});
  assert.equal([...html.matchAll(/role="radio"/g)].length,initial.presets.length+1);
  assert.ok(html.includes('model-settings-model-list scrollable'));
});

test('platform navigation preserves the last RC family while direct Claude tab switches to Claude', () => {
  assert.equal(modelSettingsProviderTarget('RELAYCORE_GPT','RELAYCORE_CLAUDE',true),'RELAYCORE_GPT');
  assert.equal(modelSettingsProviderTarget('QWEN','RELAYCORE_CLAUDE',true,'RELAYCORE_GEMINI'),'RELAYCORE_GEMINI');
  assert.equal(modelSettingsProviderTarget('RELAYCORE_GPT','RELAYCORE_CLAUDE',false),'RELAYCORE_CLAUDE');
});

test('edited fields block probing but never disable the independent switch', () => {
  const services=MODEL_SERVICE_PREVIEW_SETTINGS.map(s=>({...s,enabled:true,credentialStored:true}));
  const html=renderAndroidSettingsShell({page:'model-configuration',modelServiceSettings:services,modelCredentialEdited:true});
  assert.match(html,/data-action="test-model-service-connection" disabled/);
  const toggle=html.match(/<button[^>]+data-action="toggle-model-service-enabled"[^>]+>/)[0];
  assert.ok(!toggle.includes('disabled'));
  const busy=renderAndroidSettingsShell({page:'model-configuration',modelSettingsSaving:true});
  assert.ok(!busy.match(/<button[^>]+data-action="toggle-model-service-enabled"[^>]+>/)[0].includes('disabled'));
});

test('eight provider marks are identical original assets on both platforms and packaged in dist',async()=>{
  for (const name of ['openrouter','relaycore','deepseek','zhipu','qwen','claude','gpt','gemini']) {
    const desktop=await readFile(new URL('../src/assets/model-brands/'+name+'.png',import.meta.url));
    const android=await readFile(new URL('../../app/src/main/res/drawable-nodpi/model_brand_'+name+'.png',import.meta.url));
    assert.ok(desktop.equals(android),name);
    assert.ok(desktop.length>0,name);
  }
  const build=await readFile(new URL('../scripts/build.mjs',import.meta.url),'utf8');
  assert.ok(build.includes('src/assets/model-brands') && build.includes('model-settings-browser.mjs'));
});

test('the real chat shell forwards edited-key state to the model settings action row',async()=>{
  const fixture=JSON.parse(await readFile(new URL('../../protocol/fixtures/nfai.exchange.v1.golden.json',import.meta.url),'utf8'));
  const services=MODEL_SERVICE_PREVIEW_SETTINGS.map(s=>({...s,credentialStored:true}));
  const html=renderChatFirstShell({data:{summary:{id:'settings-fixture'},exchange:fixture},native:true,pane:'settings',settingsSection:'model-configuration',modelServiceSettings:services,modelCredentialEdited:true});
  assert.match(html,/data-action="test-model-service-connection" disabled/);
});

test('future RC family expansion uses its smaller viewport without dropping models',()=>{
  const service=MODEL_SERVICE_PREVIEW_SETTINGS.find(s=>s.providerId==='RELAYCORE_CLAUDE');
  const expanded={...service,presets:[...service.presets,...[1,2,3,4].map(n=>({id:'PUBLIC_FUTURE_'+n,displayName:'Future '+n,chatSelectable:true,description:'Fixture only'}))]};
  const html=renderAndroidSettingsShell({page:'model-configuration',modelProviderId:service.providerId,modelServiceSettings:[expanded]});
  assert.equal([...html.matchAll(/role="radio"/g)].length,expanded.presets.length);
  assert.ok(html.includes('model-settings-model-list scrollable'));
});

test('GLM OCR is selectable as a settings probe target without advertising a chat model',()=>{
 const settings=MODEL_SERVICE_PREVIEW_SETTINGS.find(s=>s.providerId==='ZHIPU');
 const html=renderAndroidSettingsShell({page:'model-configuration',modelProviderId:'ZHIPU',modelServiceDraft:{...settings,presetId:'GLM_OCR'},modelServiceSettings:[{...settings,credentialStored:true}]});
 assert.match(html,/data-preset-id="GLM_OCR" role="radio" aria-checked="true"/);
 assert.ok(!html.match(/data-action="test-model-service-connection" disabled/));
 assert.ok(!html.includes('控制该平台模型显示')&&!html.includes('控制该组模型显示'));
 assert.ok(!settings.presets.some(p=>p.id==='GLM_OCR'));
});
