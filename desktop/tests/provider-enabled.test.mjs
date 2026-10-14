import test from 'node:test';
import assert from 'node:assert/strict';
import vm from 'node:vm';
import {readFile} from 'node:fs/promises';
import {desktopComposerModelCandidates} from '../src/chat-shell.mjs';
import {MODEL_SERVICE_PREVIEW_SETTINGS} from '../src/android-settings-shell.mjs';
const source=await readFile(new URL('../src/app.mjs',import.meta.url),'utf8');
const toggle=source.slice(source.indexOf('let modelEnabledQueue'),source.indexOf('async function saveModelServiceSettings()'));
test('every provider group directly controls its picker models, without a credential gate',()=>{
  assert.deepEqual(desktopComposerModelCandidates(MODEL_SERVICE_PREVIEW_SETTINGS.map(s=>({...s,enabled:false}))),[]);
  for(const service of MODEL_SERVICE_PREVIEW_SETTINGS){
    const models=desktopComposerModelCandidates(MODEL_SERVICE_PREVIEW_SETTINGS.map(s=>({...s,enabled:s.providerId===service.providerId,credentialStored:false})));
    assert.ok(models.length>0,service.providerId);
    assert.ok(models.every(m=>m.providerId===service.providerId),service.providerId);
  }
});
test('rapid switch writes remain independent of save/probe and preserve unsaved fields',async()=>{
  const calls=[];
  let persisted=true;
  const service={providerId:'DEEPSEEK',enabled:true,presetId:'DEEPSEEK_V4_PRO',revision:0};
  const state={modelServiceSettings:[service],modelServiceDraft:{...service,presetId:'DEEPSEEK_V4_FLASH'},modelCredentialDraft:'unsaved-synthetic-key',modelCredentialEdited:true,modelSettingsSaving:true,modelSettingsTesting:true};
  const ctx=vm.createContext({native:true,state,selectedModelService:()=>state.modelServiceSettings[0],render:()=>{},setModelProviderFeedback:()=>{},
    replaceModelService:s=>{state.modelServiceSettings=[s];},loadModelServiceSettings:async()=>{},
    invoke:async(command,{args})=>{calls.push({command,args});persisted=args.enabled;return {...service,enabled:persisted,revision:calls.length};}});
  vm.runInContext(toggle,ctx);
  await Promise.all([vm.runInContext('setModelServiceEnabled()',ctx),vm.runInContext('setModelServiceEnabled()',ctx),vm.runInContext('setModelServiceEnabled()',ctx)]);
  assert.deepEqual(calls.map(c=>c.args.enabled),[false,true,false]);
  assert.ok(calls.every(c=>c.command==='set_desktop_model_service_enabled' && Object.keys(c.args).sort().join(',')==='enabled,providerId'));
  assert.equal(persisted,false);assert.equal(state.modelServiceDraft.enabled,false);
  assert.equal(state.modelServiceDraft.presetId,'DEEPSEEK_V4_FLASH');assert.equal(state.modelCredentialDraft,'unsaved-synthetic-key');assert.equal(state.modelCredentialEdited,true);
});

const load=source.slice(source.indexOf('async function loadModelServiceSettings('),source.indexOf('async function loadDesktopAppSettings()'));
function switchHarness(invoke){
  const service={providerId:'DEEPSEEK',enabled:true,presetId:'DEEPSEEK_V4_PRO',presetDisplayName:'Pro',credentialStored:true,revision:0,presets:[{id:'DEEPSEEK_V4_PRO',displayName:'Pro'},{id:'DEEPSEEK_V4_FLASH',displayName:'Flash'}]};
  const state={modelServiceSettings:[service],modelProviderId:service.providerId,modelServiceDraft:{...service,presetId:'unsaved-preset'},modelCredentialDraft:'unsaved-synthetic-key'};
  const ctx=vm.createContext({native:true,state,selectedModelService:()=>state.modelServiceSettings[0],render:()=>{},setModelProviderFeedback:()=>{},
    replaceModelService:s=>{state.modelServiceSettings=[s];},resetModelServiceDraft:()=>{state.modelServiceDraft={...state.modelServiceSettings[0]};},modelServiceConfigurationStatus:()=>'',invoke});
  vm.runInContext('let modelServiceReadSequence = 0;',ctx);
  vm.runInContext(toggle+load,ctx);
  return {ctx,state,service};
}
test('failed switch restores persisted state without its optimistic intent leaking into readback',async()=>{
  const {ctx,state,service}=switchHarness(async command=>{
    if(command==='set_desktop_model_service_enabled') throw new Error('synthetic write failure');
    return [{...service}];
  });
  await ctx.setModelServiceEnabled();
  assert.equal(state.modelServiceSettings[0].enabled,true);
  assert.equal(state.modelServiceDraft.enabled,true);
  assert.equal(state.modelServiceDraft.presetId,'unsaved-preset');
  assert.equal(vm.runInContext('modelEnabledIntents.size',ctx),0);
});
test('write and readback failure clear pending intent and leave the next click usable',async()=>{
  let fail=true;
  const {ctx,state,service}=switchHarness(async(command,{args}={})=>{
    if(fail) throw new Error('synthetic storage unavailable');
    return command==='set_desktop_model_service_enabled'?{...service,enabled:args.enabled,revision:1}:[service];
  });
  await ctx.setModelServiceEnabled();
  assert.equal(state.modelServiceDraft.enabled,true);
  assert.equal(vm.runInContext('modelEnabledIntents.size',ctx),0);
  fail=false;await ctx.setModelServiceEnabled();
  assert.equal(state.modelServiceSettings[0].enabled,false);
});
test('a refresh started before a completed toggle cannot restore an older revision',async()=>{
  let finishRead;
  const read=new Promise(resolve=>{finishRead=resolve;});
  const {ctx,state,service}=switchHarness(async(command,{args}={})=>command==='read_desktop_model_service_settings'?read:{...service,enabled:args.enabled,revision:1});
  let captionProviders;
  ctx.modelServiceConfigurationStatus=providers=>{captionProviders=providers;return '';};
  const refresh=ctx.loadModelServiceSettings();
  await ctx.setModelServiceEnabled();
  finishRead([{...service}]);await refresh;
  assert.equal(state.modelServiceSettings[0].enabled,false);
  assert.equal(state.modelServiceSettings[0].revision,1);
  assert.equal(state.modelServiceDraft.enabled,false);
  assert.equal(captionProviders[0].enabled,false);
});

test('settings save and toggle share ordering and use the committed revision',async()=>{
  let completeSave;
  const calls=[];
  const {ctx,state,service}=switchHarness(async(command,{args}={})=>{
    calls.push({command,args});
    if(command==='save_desktop_model_service_settings') return new Promise(resolve=>{completeSave=()=>resolve({...service,presetId:args.preservePreset ? service.presetId : args.presetId,revision:1});});
    return {...service,presetId:'unsaved-preset',enabled:args.enabled,revision:2};
  });
  const save=source.slice(source.indexOf('async function saveModelServiceSettings()'),source.indexOf('async function revealModelServiceCredential()'));
  vm.runInContext(save,ctx);
  state.modelCredentialEdited=true;
  const saving=ctx.saveModelServiceSettings();
  await new Promise(resolve=>setImmediate(resolve));
  const toggling=ctx.setModelServiceEnabled();
  assert.equal(calls.length,1);
  completeSave();await Promise.all([saving,toggling]);
  assert.deepEqual(calls.map(c=>c.command),['save_desktop_model_service_settings','set_desktop_model_service_enabled']);
  assert.equal(calls[0].args.preservePreset,true);
  assert.equal(state.modelServiceSettings[0].enabled,false);
  assert.equal(state.modelSettingsSaving,false);
});
test('queued save follows a toggle with the updated revision instead of failing after key replacement',async()=>{
  const calls=[];
  const {ctx,state,service}=switchHarness(async(command,{args})=>{
    calls.push({command,args});
    return {...service,presetId:command==='save_desktop_model_service_settings'?args.presetId:service.presetId,enabled:false,revision:calls.length};
  });
  vm.runInContext(source.slice(source.indexOf('async function saveModelServiceSettings()'),source.indexOf('async function revealModelServiceCredential()')),ctx);
  state.modelCredentialEdited=true;
  const toggling=ctx.setModelServiceEnabled();
  const saving=ctx.saveModelServiceSettings();
  await Promise.all([toggling,saving]);
  assert.equal(calls[1].args.expectedRevision,1);
  assert.equal(calls[1].args.preserveEnabled,true);
  assert.equal(state.modelServiceSettings[0].enabled,false);
});
test('an old read completing after data-area replacement cannot publish into the new owner',async()=>{
  let complete;
  const {ctx,state,service}=switchHarness(()=>new Promise(resolve=>{complete=resolve;}));
  const loading=ctx.loadModelServiceSettings();
  ctx.state={...state,modelServiceSettings:[{...service,enabled:false,revision:3}]};
  complete([service]);await loading;
  assert.equal(ctx.state.modelServiceSettings[0].enabled,false);
});

test('reversed refresh completion keeps the newest read even at the same revision',async()=>{
  const reads=[];
  const {ctx,state,service}=switchHarness(()=>new Promise(resolve=>reads.push(resolve)));
  const first=ctx.loadModelServiceSettings();const second=ctx.loadModelServiceSettings();
  reads[1]([{...service,credentialStored:true}]);await second;
  reads[0]([{...service,credentialStored:false}]);await first;
  assert.equal(state.modelServiceSettings[0].credentialStored,true);
});
test('uncertain write completion is reconciled from persisted readback',async()=>{
  const {ctx,state,service}=switchHarness(async command=>{
    if(command==='set_desktop_model_service_enabled') throw new Error('synthetic response lost after commit');
    return [{...service,enabled:false,revision:1}];
  });
  await ctx.setModelServiceEnabled();
  assert.equal(state.modelServiceSettings[0].enabled,false);
  assert.equal(state.modelServiceDraft.enabled,false);
  assert.equal(vm.runInContext('modelEnabledIntents.size',ctx),0);
});

// Exercise the real settings owner through its public operations and native receipts.
test('model selection commits immediately without a key write or enable change',async()=>{
  const calls=[];
  const {ctx,state,service}=switchHarness(async(command,{args})=>{
    calls.push({command,args});return {...service,presetId:args.presetId,presetDisplayName:'Flash',revision:1};
  });
  await ctx.setModelServicePreset('DEEPSEEK_V4_FLASH');
  assert.equal(calls.length,1);assert.equal(calls[0].args.apiKey,null);assert.equal(calls[0].args.preserveEnabled,true);
  assert.equal(state.modelServiceSettings[0].presetId,'DEEPSEEK_V4_FLASH');assert.equal(state.modelServiceDraft.presetId,'DEEPSEEK_V4_FLASH');
  assert.equal(state.modelCredentialDraft,'unsaved-synthetic-key');assert.equal(vm.runInContext('modelPresetIntents.size',ctx),0);
});
test('rapid model, key and visibility writes preserve all three independent final choices',async()=>{
  let persisted;const calls=[];
  const {ctx,state,service}=switchHarness(async(command,{args})=>{
    persisted ||= {...service};calls.push({command,args});
    assert.equal(args.expectedRevision ?? persisted.revision,persisted.revision);
    persisted={...persisted,revision:persisted.revision+1};
    if(command==='set_desktop_model_service_enabled')persisted.enabled=args.enabled;
    else if(!args.preservePreset)persisted.presetId=args.presetId;
    return {...persisted};
  });
  vm.runInContext(source.slice(source.indexOf('async function saveModelServiceSettings()'),source.indexOf('async function revealModelServiceCredential()')),ctx);
  state.modelCredentialEdited=true;
  await Promise.all([ctx.setModelServicePreset('DEEPSEEK_V4_FLASH'),ctx.saveModelServiceSettings(),ctx.setModelServiceEnabled(),ctx.setModelServicePreset('DEEPSEEK_V4_PRO')]);
  assert.equal(persisted.presetId,'DEEPSEEK_V4_PRO');assert.equal(persisted.enabled,false);
  assert.equal(state.modelServiceSettings[0].presetId,persisted.presetId);assert.equal(state.modelServiceSettings[0].enabled,false);
  assert.equal(calls.filter(c=>c.args.apiKey).length,1);assert.equal(calls.find(c=>c.args.apiKey).args.preservePreset,true);
});
test('connection test waits for the selected model receipt and uses its committed identity',async()=>{
  let finishWrite;const calls=[];
  const {ctx,state,service}=switchHarness(async(command,{args})=>{
    calls.push({command,args});
    if(command==='save_desktop_model_service_settings')return new Promise(resolve=>{finishWrite=()=>resolve({...service,presetId:args.presetId,revision:1});});
    return {};
  });
  vm.runInContext(source.slice(source.indexOf('async function testModelServiceConnection()'),source.indexOf('const escape =')),ctx);
  const selecting=ctx.setModelServicePreset('DEEPSEEK_V4_FLASH');
  const probing=ctx.testModelServiceConnection();
  await new Promise(resolve=>setImmediate(resolve));assert.equal(calls.length,1);
  finishWrite();await Promise.all([selecting,probing]);
  assert.equal(calls[1].command,'test_desktop_model_service_connection');assert.equal(calls[1].args.presetId,'DEEPSEEK_V4_FLASH');
});
test('failed model write restores the confirmed choice while preserving other edited state',async()=>{
  const {ctx,state,service}=switchHarness(async command=>{
    if(command==='save_desktop_model_service_settings')throw new Error('synthetic model storage failure');
    return [{...service,enabled:false}];
  });
  await ctx.setModelServicePreset('DEEPSEEK_V4_FLASH');
  assert.equal(state.modelServiceDraft.presetId,'DEEPSEEK_V4_PRO');assert.equal(state.modelServiceSettings[0].enabled,false);
  assert.equal(state.modelCredentialDraft,'unsaved-synthetic-key');assert.equal(vm.runInContext('modelPresetIntents.size',ctx),0);
});
test('late connection completion cannot write feedback into a replaced data owner',async()=>{
  let finishProbe;
  const {ctx,state}=switchHarness(()=>new Promise(resolve=>{finishProbe=resolve;}));
  vm.runInContext(source.slice(source.indexOf('async function testModelServiceConnection()'),source.indexOf('const escape =')),ctx);
  const feedback=[];ctx.setModelProviderFeedback=(...args)=>feedback.push(args);
  const probing=ctx.testModelServiceConnection();await new Promise(resolve=>setImmediate(resolve));
  ctx.state={...state,modelSettingsTesting:false};finishProbe({});await probing;
  assert.equal(feedback.length,1);assert.equal(ctx.state.modelSettingsTesting,false);
});

test('selecting and probing OCR never persists it over the chat default or writes a key',async()=>{
 const calls=[];const {ctx,state,service}=switchHarness(async(command,{args})=>{calls.push({command,args});return {};});
 Object.assign(service,{providerId:'ZHIPU',presetId:'GLM_5_3_FLASH',nonChatCapabilities:[{id:'GLM_OCR',displayName:'GLM-OCR'}]});
 state.modelProviderId='ZHIPU';state.modelServiceDraft={...service};
 vm.runInContext(source.slice(source.indexOf('async function testModelServiceConnection()'),source.indexOf('const escape =')),ctx);
 await ctx.setModelServicePreset('GLM_OCR');assert.equal(calls.length,0);
 assert.equal(state.modelServiceDraft.presetId,'GLM_OCR');assert.equal(service.presetId,'GLM_5_3_FLASH');
 await ctx.testModelServiceConnection();assert.equal(calls.length,1);
 assert.equal(calls[0].command,'test_desktop_model_service_connection');assert.equal(calls[0].args.presetId,'GLM_OCR');
 assert.equal(service.presetId,'GLM_5_3_FLASH');assert.equal(calls[0].args.apiKey,undefined);
});

test('an older chat selection failure cannot clear a newer OCR test target',async()=>{
 let rejectWrite;let confirmed;
 const {ctx,state,service}=switchHarness(async command=>{
   if(command==='save_desktop_model_service_settings')return new Promise((resolve,reject)=>{rejectWrite=()=>reject(new Error('synthetic failed selection'));});
   return [confirmed];
 });
 Object.assign(service,{providerId:'ZHIPU',presetId:'GLM_5_3_FLASH',presets:[{id:'GLM_5_3',displayName:'GLM-5.3'},{id:'GLM_5_3_FLASH',displayName:'GLM-5.3 Flash'}],nonChatCapabilities:[{id:'GLM_OCR'}]});
 confirmed={...service};state.modelProviderId='ZHIPU';state.modelServiceDraft={...service};
 const selecting=ctx.setModelServicePreset('GLM_5_3');await new Promise(resolve=>setImmediate(resolve));
 await ctx.setModelServicePreset('GLM_OCR');rejectWrite();await selecting;
 assert.equal(state.modelServiceDraft.presetId,'GLM_OCR');
 assert.equal(state.modelServiceSettings[0].presetId,'GLM_5_3_FLASH');
});
