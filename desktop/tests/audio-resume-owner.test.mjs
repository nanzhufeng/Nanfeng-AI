import test from 'node:test';
import assert from 'node:assert/strict';
import vm from 'node:vm';
import {readFileSync} from 'node:fs';

const source = readFileSync(new URL('../src/app.mjs', import.meta.url), 'utf8');
const start = source.indexOf('let audioResumeWrite =') >= 0
  ? source.indexOf('let audioResumeWrite =') : source.indexOf('async function openAudioPreview(');
const body = source.slice(start, source.indexOf('function syncPreviewPlaybackUi(', start))
  + source.slice(source.indexOf('async function closeAudioPreview('), source.indexOf('async function openTextPreview('));
const tick = () => new Promise(setImmediate);
const deferred = () => { let resolve; const promise = new Promise(done => { resolve = done; }); return {promise, resolve}; };

function media(readyState = 1) {
  const events = new Map();
  return {readyState, currentTime: 0, duration: 8, isConnected: true, dataset: {}, playCalls: 0, released: [],
    addEventListener(name, handler) { events.set(name, [...(events.get(name) || []), handler]); },
    emit(name) { for (const handler of events.get(name) || []) handler(); },
    async play() { this.playCalls++; }, pause() { this.released.push('pause'); this.emit('pause'); }, removeAttribute(name) { this.released.push(name); }, load() { this.released.push('load'); },
  };
}

function fixture({audio = media(), write = async () => {}} = {}) {
  const chat = {dataArea: 'CHAT', current: {summary: {id: 'workspace'}}, audioPreview: null};
  const calls = [];
  const context = vm.createContext({native: true, state: chat, audio, queueMicrotask, Number, Math, Promise,
    document: {hidden: false, querySelector: () => context.audio}, render() {}, syncPreviewPlaybackUi() {},
    async invoke(command, args, owner) {
      calls.push({command, owner});
      return {attachmentId: args.args.attachmentId, mediaUrl: 'synthetic-silence.wav', positionMillis: 4000};
    },
    invokePreviewOwner(owner, command, args) { calls.push({command, owner, position: args.args.positionMillis}); return write(); },
  });
  vm.runInContext(body, context);
  return {context, chat, audio, calls, saves: () => calls.filter(call => call.command === 'save_desktop_audio_preview_position'),
    async open() { await context.openAudioPreview('attachment'); await tick(); },
  };
}

test('audio close saves an exact zero seek instead of an older nonzero checkpoint', async () => {
  const f = fixture(); await f.open();
  f.chat.audioPreview.lastPlaybackPositionMillis = 3500; f.audio.currentTime = 0;
  await f.context.closeAudioPreview();
  assert.equal(f.saves().at(-1).position, 0);
});

test('audio close before metadata restoration leaves the stored resume point untouched', async () => {
  const f = fixture({audio: media(0)}); await f.open(); await f.context.closeAudioPreview();
  assert.equal(f.saves().length, 0);
});

test('late audio metadata cannot play or rewrite a detached same-ID replacement after area ABA', async () => {
  const old = media(0), f = fixture({audio: old}); await f.open();
  const original = f.chat.audioPreview;
  f.context.state = {dataArea: 'WORK', audioPreview: null}; f.context.state = f.chat;
  f.context.audio = media(); old.isConnected = false;
  f.chat.audioPreview = {...original}; old.readyState = 1; old.emit('loadedmetadata'); await tick();
  assert.equal(old.playCalls, 0); assert.equal(old.currentTime, 0);
  assert.equal(f.chat.audioPreview.lastPlaybackPositionMillis, undefined);
});

test('same-attachment audio reopen waits for the already authorized close write', async () => {
  const hold = deferred(), f = fixture({write: () => hold.promise}); await f.open();
  f.audio.currentTime = 1.5; const closing = f.context.closeAudioPreview(); await tick();
  f.context.audio = media(); const opening = f.context.openAudioPreview('attachment'); await tick();
  assert.equal(f.calls.filter(call => call.command === 'read_desktop_audio_preview').length, 1);
  hold.resolve(); await closing; await opening;
  assert.equal(f.calls.filter(call => call.command === 'read_desktop_audio_preview').length, 2);
  assert.equal(f.saves()[0].position, 1500);
});

test('audio close keeps its captured data area even when the user switches during the write', async () => {
  const hold = deferred(), f = fixture({write: () => hold.promise}); await f.open();
  f.audio.currentTime = 2; const closing = f.context.closeAudioPreview();
  f.context.state = {dataArea: 'WORK', audioPreview: null}; hold.resolve(); await closing;
  assert.equal(f.saves()[0].owner, f.chat); assert.equal(f.saves()[0].position, 2000);
});

test('invalid audio playback time never becomes a non-finite resume write', async () => {
  const f = fixture(); await f.open(); f.audio.currentTime = Infinity; await f.context.closeAudioPreview();
  assert.ok(f.saves().every(call => Number.isFinite(call.position)));
});

test('a failed audio close write does not block reopening or a later explicit zero save', async () => {
  let first = true;
  const f = fixture({write: async () => { if (first) { first = false; throw new Error('synthetic write failure'); } }});
  await f.open(); f.audio.currentTime = 2; await f.context.closeAudioPreview();
  f.context.audio = media(); await f.open(); f.context.audio.currentTime = 0; await f.context.closeAudioPreview();
  assert.deepEqual(f.saves().map(call => call.position), [2000, 0]);
});

test('audio exit checkpoint waits for its captured position write and pauses without releasing the live source', async () => {
  const hold = deferred(), f = fixture({write: () => hold.promise}); await f.open();
  f.audio.currentTime = 1.25; let finished = false;
  const checkpoint = f.context.checkpointAudioOnExit().then(() => { finished = true; });
  await tick(); assert.equal(finished, false);
  assert.deepEqual(f.saves().map(call => call.position), [1250]);
  assert.equal(f.saves()[0].owner, f.chat);
  assert.ok(f.audio.released.includes('pause'));
  assert.ok(!f.audio.released.includes('src') && !f.audio.released.includes('load'));
  hold.resolve(); await checkpoint; assert.equal(finished, true);
});

test('audio exit before metadata restoration does not manufacture a zero checkpoint', async () => {
  const f = fixture({audio: media(0)}); await f.open(); await f.context.checkpointAudioOnExit();
  assert.equal(f.saves().length, 0);
});

test('audio exit drains an already closed preview write without attributing it to the new area', async () => {
  const hold = deferred(), f = fixture({write: () => hold.promise}); await f.open();
  f.audio.currentTime = 2; const closing = f.context.closeAudioPreview();
  f.context.state = {dataArea: 'WORK', audioPreview: null}; let finished = false;
  const checkpoint = f.context.checkpointAudioOnExit().then(() => { finished = true; });
  await tick(); assert.equal(finished, false); hold.resolve(); await closing; await checkpoint;
  assert.deepEqual(f.saves().map(call => call.position), [2000]);
  assert.equal(f.saves()[0].owner, f.chat);
});

test('a noncritical audio exit position failure settles without blocking native window shutdown', async () => {
  const f = fixture({write: async () => { throw new Error('synthetic checkpoint failure'); }}); await f.open();
  f.audio.currentTime = 0; await f.context.checkpointAudioOnExit();
  assert.equal(f.saves()[0].position, 0);
});


test('audio: latest open wins while the previous close checkpoint is pending',async()=>{
 const hold=deferred(),f=fixture({write:()=>hold.promise});await f.open();
 const earlier=f.context.openAudioPreview('earlier');await tick();const latest=f.context.openAudioPreview('latest');await tick();
 hold.resolve();await Promise.all([earlier,latest]);await tick();
 assert.equal(f.chat.audioPreview.attachmentId,'latest');assert.equal(f.calls.filter(c=>c.command==='read_desktop_audio_preview').length,2,'obsolete open must not read another file');
});

test('audio: explicit close cancels an open that is waiting for the prior checkpoint',async()=>{
 const hold=deferred(),f=fixture({write:()=>hold.promise});await f.open();
 const opening=f.context.openAudioPreview('cancelled');await tick();await f.context.closeAudioPreview();
 hold.resolve();await opening;await tick();assert.equal(f.chat.audioPreview,null);assert.equal(f.calls.filter(c=>c.command==='read_desktop_audio_preview').length,1);
});

test('audio: same attachment ID from a newer workspace is not replaced by an earlier pending open',async()=>{
 const hold=deferred(),f=fixture({write:()=>hold.promise});await f.open();
 const earlier=f.context.openAudioPreview('same-id',undefined,'old-workspace');await tick();
 const latest=f.context.openAudioPreview('same-id',undefined,'new-workspace');hold.resolve();await Promise.all([earlier,latest]);await tick();
 assert.equal(f.chat.audioPreview.workspaceId,'new-workspace');assert.equal(f.calls.filter(c=>c.command==='read_desktop_audio_preview').length,2);
});


test('audio: production Escape handler cancels an open waiting behind a close checkpoint',async()=>{
 const hold=deferred(),f=fixture({write:()=>hold.promise});await f.open();const opening=f.context.openAudioPreview('cancelled-by-escape');await tick();
 f.context.closeTopOverlay=()=>false;f.context.document.addEventListener=(name,handler)=>{if(name==='keydown')f.context.mediaEscape=handler;};
 const line=source.split(/\r?\n/).find(line=>line.startsWith("document.addEventListener('keydown', event => { if (event.key === 'Escape'"));assert.ok(line);vm.runInContext(line,f.context);
 f.context.mediaEscape({key:'Escape',preventDefault(){}});hold.resolve();await opening;await tick();assert.equal(f.chat.audioPreview,null);
});


test('audio: production outside pointer handler invalidates an older pending open',async()=>{
 const hold=deferred(),f=fixture({write:()=>hold.promise});await f.open();const opening=f.context.openAudioPreview('cancelled-by-outside-pointer');await tick();
 f.context.closeTopOverlay=()=>false;f.context.document.addEventListener=(name,handler)=>{if(name==='pointerdown')f.context.mediaPointer=handler;};
 const lines=source.split(/\r?\n/),start=lines.findIndex((line,i)=>line.startsWith("document.addEventListener('pointerdown', event => {")&&lines[i+1]?.includes(".dialog, .chat-context-menu"));assert.ok(start>=0);const end=lines.findIndex((line,i)=>i>start&&line==='}, true);');vm.runInContext(lines.slice(start,end+1).join('\n'),f.context);
 f.context.mediaPointer({target:{closest:()=>null},preventDefault(){},stopImmediatePropagation(){}});hold.resolve();await opening;await tick();assert.equal(f.chat.audioPreview,null);
});


test('audio: production Escape still closes an active preview with no pending opening marker',async()=>{
 const f=fixture();await f.open();f.chat.audioPreviewOpening=null;
 f.context.closeTopOverlay=()=>false;f.context.document.addEventListener=(name,handler)=>{if(name==='keydown')f.context.mediaEscape=handler;};
 const line=source.split(/\r?\n/).find(line=>line.startsWith("document.addEventListener('keydown', event => { if (event.key === 'Escape'"));vm.runInContext(line,f.context);
 f.context.mediaEscape({key:'Escape',preventDefault(){}});await tick();await tick();assert.equal(f.chat.audioPreview,null);assert.equal(f.saves().length,1);
});
