import test from 'node:test';
import assert from 'node:assert/strict';
import vm from 'node:vm';
import { readFile } from 'node:fs/promises';
import { createDraftCloseGuard } from '../src/composer-draft-recovery.mjs';
const source=await readFile(new URL('../src/app.mjs',import.meta.url),'utf8');
const code=source.slice(source.indexOf('async function installComposerDraftCloseGuard()'),source.indexOf('async function updateTemporaryModelOverride('));
function harness(){
 const events=[];let listener,finish,fail=false;const saved=new Promise(done=>finish=done);const state={error:''};
 const context=vm.createContext({native:true,state,checkpointVideoOnExit:async()=>events.push('video-checkpoint'),checkpointAudioOnExit:async()=>events.push('audio-checkpoint'),composerDraftRouteGeneration:0,createDraftCloseGuard,
  window:{__TAURI__:{window:{getCurrentWindow:()=>({onCloseRequested:async callback=>listener=callback,destroy:()=>{throw new Error('must retain native ownership');}})}}},
  flushOrdinaryComposerDraft:async()=>events.push('draft'),flushTemporaryDraft:async()=>events.push('temporary'),
  localSettingsWrites:{version:0,flush:async()=>{await saved;events.push('settings');}},
  rememberDesktopExit:()=>events.push('exit-mark'),invoke:async(command,args)=>{events.push(command);assert.deepEqual(JSON.parse(JSON.stringify(args)),{});if(fail)throw new Error('native close denied');return 'HIDDEN_DRAINING';},render:()=>events.push('paint'),
 });vm.runInContext(code,context);
 return {context,events,state,install:()=>context.installComposerDraftCloseGuard(),close:()=>listener({preventDefault:()=>events.push('prevent')}),finish,setFail:()=>fail=true};
}
test('write barrier completes before the native close owner and duplicate closes coalesce',async()=>{
 const h=harness();await h.install();const first=h.close(),second=h.close();await Promise.resolve();assert.ok(!h.events.includes('close_desktop_window_after_flush'));
 h.finish();await Promise.all([first,second]);assert.deepEqual(h.events,['prevent','prevent','draft','temporary','settings','video-checkpoint','audio-checkpoint','exit-mark','close_desktop_window_after_flush']);
});
test('native close refusal keeps the view and reports failure after preserving writes',async()=>{
 const h=harness();h.setFail();await h.install();const closing=h.close();h.finish();await closing;assert.match(h.state.error,/native close denied/);assert.ok(h.events.includes('settings'));assert.equal(h.events.at(-1),'paint');
});

test('native close also waits for the video checkpoint after the settings barrier',async()=>{
 const h=harness();let finishVideo;const video=new Promise(done=>finishVideo=done);h.context.checkpointVideoOnExit=async()=>{h.events.push('video-pending');await video;h.events.push('video-saved');};await h.install();const closing=h.close();h.finish();await new Promise(setImmediate);assert.ok(h.events.includes('video-pending'));assert.ok(!h.events.includes('close_desktop_window_after_flush'));finishVideo();await closing;assert.ok(h.events.indexOf('video-saved')<h.events.indexOf('close_desktop_window_after_flush'));
});
