import test from 'node:test';
import assert from 'node:assert/strict';
import vm from 'node:vm';
import { readFileSync } from 'node:fs';
const source=readFileSync(new URL('../src/app.mjs',import.meta.url),'utf8');
const start=source.indexOf('let videoResumeWrite =')>=0?source.indexOf('let videoResumeWrite ='):source.indexOf('async function openVideoPreview(');
const body=source.slice(start,source.indexOf('async function openAudioPreview(',start));
const tick=()=>new Promise(setImmediate);
const deferred=()=>{let resolve,reject;const promise=new Promise((a,b)=>{resolve=a;reject=b;});return {promise,resolve,reject};};
function media({readyState=1,currentTime=0}={}) {
 const events=new Map();return {readyState,currentTime,duration:8,isConnected:true,dataset:{},playCalls:0,released:[],
 addEventListener(name,fn){if(!events.has(name))events.set(name,[]);events.get(name).push(fn);},
 emit(name){for(const fn of events.get(name)||[])fn();},
 async play(){this.playCalls++;},pause(){this.released.push('pause');this.emit('pause');},removeAttribute(name){this.released.push(name);},load(){this.released.push('load');},
 };
}
function fixture({video=media(),readPosition=4000,write=async()=>{}}={}) {
 const chat={dataArea:'CHAT',current:{summary:{id:'workspace'}},videoPreview:null};const calls=[];
 const context=vm.createContext({native:true,state:chat,document:{hidden:false,querySelector:()=>context.video},queueMicrotask,
 Date,Number,Math,Promise,render(){},syncPreviewPlaybackUi(){},
 async invoke(command,args,owner){calls.push({command,args:JSON.parse(JSON.stringify(args)),owner});return {attachmentId:args.args.attachmentId,mediaUrl:'synthetic.mp4',byteCount:20,durationMillis:8000,positionMillis:readPosition};},
 invokePreviewOwner(owner,command,args){calls.push({command,args:JSON.parse(JSON.stringify(args)),owner});return write(args.args.positionMillis);},
 });context.video=video;vm.runInContext(body,context);
 return {context,chat,calls,video,saves:()=>calls.filter(x=>x.command==='save_desktop_video_preview_position'),async open(){await context.openVideoPreview('attachment');await tick();},async settle(){await tick();await tick();}};
}
test('closing after an exact zero seek saves zero rather than the previous nonzero checkpoint',async()=>{
 const f=fixture();await f.open();f.chat.videoPreview.lastPlaybackPositionMillis=3500;f.video.currentTime=0;
 await f.context.closeVideoPreview();assert.equal(f.saves().at(-1).args.args.positionMillis,0);assert.deepEqual(f.video.released,['pause','src','load']);
});
test('closing before metadata restoration does not overwrite the persisted resume point with zero',async()=>{
 const f=fixture({video:media({readyState:0})});await f.open();await f.context.closeVideoPreview();assert.equal(f.saves().length,0);
});
test('pause and seeked persist actual positions including zero while leaving the overlay open',async()=>{
 const f=fixture();await f.open();f.video.currentTime=2.25;f.video.emit('pause');await f.settle();f.video.currentTime=0;f.video.emit('seeked');await f.settle();
 assert.deepEqual(f.saves().map(x=>x.args.args.positionMillis),[2250,0]);assert.ok(f.chat.videoPreview);
});
test('a seek before metadata, including zero, is applied only to the original live player',async()=>{
 const f=fixture({video:media({readyState:0})});await f.open();f.context.seekVideoPreviewPosition(0);assert.equal(f.saves().length,0);
 f.video.readyState=1;f.video.emit('loadedmetadata');await f.settle();assert.equal(f.video.currentTime,0);assert.equal(f.chat.videoPreview.resumeReady,true);
 f.video.emit('seeked');await f.settle();assert.equal(f.saves().at(-1).args.args.positionMillis,0);
});
test('late metadata from a detached same-ID preview cannot play or change a replacement preview after area ABA',async()=>{
 const old=media({readyState:0}),f=fixture({video:old});await f.open();const original=f.chat.videoPreview;
 f.context.state={dataArea:'WORK',videoPreview:null};f.context.state=f.chat;f.context.video=media();old.isConnected=false;
 f.chat.videoPreview={...original,resumeReady:false};old.readyState=1;old.emit('loadedmetadata');await f.settle();
 assert.equal(old.playCalls,0);assert.equal(old.currentTime,0);assert.equal(f.chat.videoPreview.resumeReady,false);assert.equal(f.saves().length,0);
});
test('queued checkpoints retain their original area and serialize older writes before a later zero',async()=>{
 const hold=deferred(),f=fixture({write:pos=>pos===3000?hold.promise:Promise.resolve()});await f.open();f.video.currentTime=3;f.video.emit('seeked');await f.settle();
 f.video.currentTime=0;const close=f.context.closeVideoPreview();f.context.state={dataArea:'WORK',videoPreview:null};await tick();
 assert.deepEqual(f.saves().map(x=>x.args.args.positionMillis),[3000]);hold.resolve();await close;
 assert.deepEqual(f.saves().map(x=>x.args.args.positionMillis),[3000,0]);assert.ok(f.saves().every(x=>x.owner===f.chat));
});
test('reopening waits for an already authorized close checkpoint before reading the same attachment',async()=>{
 const hold=deferred(),f=fixture({write:()=>hold.promise});await f.open();f.video.currentTime=1.5;const closing=f.context.closeVideoPreview();await tick();
 f.context.video=media();const opening=f.context.openVideoPreview('attachment');await tick();assert.equal(f.calls.filter(x=>x.command==='read_desktop_video_preview').length,1);
 hold.resolve();await closing;await opening;await tick();assert.equal(f.calls.filter(x=>x.command==='read_desktop_video_preview').length,2);
});
test('a failed earlier checkpoint does not block a later explicit save',async()=>{
 let first=true;const f=fixture({write:async()=>{if(first){first=false;throw new Error('synthetic failure');}}});await f.open();f.video.currentTime=2;f.video.emit('seeked');await f.settle();
 f.video.currentTime=0;await f.context.closeVideoPreview();assert.deepEqual(f.saves().map(x=>x.args.args.positionMillis),[2000,0]);
});
test('invalid or disconnected playback values never become resume writes',async()=>{
 const f=fixture();await f.open();f.video.currentTime=Infinity;await f.context.closeVideoPreview();assert.ok(f.saves().every(x=>Number.isFinite(x.args.args.positionMillis)));
});

test('exit checkpoint waits for the captured write and pauses without releasing the active source',async()=>{
 const hold=deferred(),f=fixture({write:()=>hold.promise});await f.open();f.video.currentTime=1.25;let done=false;const saving=f.context.checkpointVideoOnExit().then(()=>{done=true;});await tick();assert.equal(done,false);assert.ok(f.saves().every(x=>x.args.args.positionMillis===1250));assert.ok(f.video.released.includes('pause'));assert.ok(!f.video.released.includes('src'));hold.resolve();await saving;assert.equal(done,true);
});
test('timeupdate throttling still captures a zero for the forced final checkpoint',async()=>{
 const f=fixture();await f.open();f.video.currentTime=2;f.video.emit('timeupdate');await f.settle();f.video.currentTime=0;f.video.emit('timeupdate');await f.settle();assert.deepEqual(f.saves().map(x=>x.args.args.positionMillis),[2000]);await f.context.closeVideoPreview();assert.deepEqual(f.saves().map(x=>x.args.args.positionMillis),[2000,0]);
});


test('video: latest open wins while the previous close checkpoint is pending',async()=>{
 const hold=deferred(),f=fixture({write:()=>hold.promise});await f.open();
 const earlier=f.context.openVideoPreview('earlier');await tick();const latest=f.context.openVideoPreview('latest');await tick();
 hold.resolve();await Promise.all([earlier,latest]);await tick();
 assert.equal(f.chat.videoPreview.attachmentId,'latest');assert.equal(f.calls.filter(c=>c.command==='read_desktop_video_preview').length,2,'obsolete open must not read another file');
});

test('video: explicit close cancels an open that is waiting for the prior checkpoint',async()=>{
 const hold=deferred(),f=fixture({write:()=>hold.promise});await f.open();
 const opening=f.context.openVideoPreview('cancelled');await tick();await f.context.closeVideoPreview();
 hold.resolve();await opening;await tick();assert.equal(f.chat.videoPreview,null);assert.equal(f.calls.filter(c=>c.command==='read_desktop_video_preview').length,1);
});

test('video: same attachment ID from a newer workspace is not replaced by an earlier pending open',async()=>{
 const hold=deferred(),f=fixture({write:()=>hold.promise});await f.open();
 const earlier=f.context.openVideoPreview('same-id',undefined,'old-workspace');await tick();
 const latest=f.context.openVideoPreview('same-id',undefined,'new-workspace');hold.resolve();await Promise.all([earlier,latest]);await tick();
 assert.equal(f.chat.videoPreview.workspaceId,'new-workspace');assert.equal(f.calls.filter(c=>c.command==='read_desktop_video_preview').length,2);
});


test('video: production Escape handler cancels an open waiting behind a close checkpoint',async()=>{
 const hold=deferred(),f=fixture({write:()=>hold.promise});await f.open();const opening=f.context.openVideoPreview('cancelled-by-escape');await tick();
 f.context.closeTopOverlay=()=>false;f.context.document.addEventListener=(name,handler)=>{if(name==='keydown')f.context.mediaEscape=handler;};
 const line=source.split(/\r?\n/).find(line=>line.startsWith("document.addEventListener('keydown', event => { if (event.key === 'Escape'"));assert.ok(line);vm.runInContext(line,f.context);
 f.context.mediaEscape({key:'Escape',preventDefault(){}});hold.resolve();await opening;await tick();assert.equal(f.chat.videoPreview,null);
});


test('video: production outside pointer handler invalidates an older pending open',async()=>{
 const hold=deferred(),f=fixture({write:()=>hold.promise});await f.open();const opening=f.context.openVideoPreview('cancelled-by-outside-pointer');await tick();
 f.context.closeTopOverlay=()=>false;f.context.document.addEventListener=(name,handler)=>{if(name==='pointerdown')f.context.mediaPointer=handler;};
 const lines=source.split(/\r?\n/),start=lines.findIndex((line,i)=>line.startsWith("document.addEventListener('pointerdown', event => {")&&lines[i+1]?.includes(".dialog, .chat-context-menu"));assert.ok(start>=0);const end=lines.findIndex((line,i)=>i>start&&line==='}, true);');vm.runInContext(lines.slice(start,end+1).join('\n'),f.context);
 f.context.mediaPointer({target:{closest:()=>null},preventDefault(){},stopImmediatePropagation(){}});hold.resolve();await opening;await tick();assert.equal(f.chat.videoPreview,null);
});


test('video: production Escape still closes an active preview with no pending opening marker',async()=>{
 const f=fixture();await f.open();f.chat.videoPreviewOpening=null;
 f.context.closeTopOverlay=()=>false;f.context.document.addEventListener=(name,handler)=>{if(name==='keydown')f.context.mediaEscape=handler;};
 const line=source.split(/\r?\n/).find(line=>line.startsWith("document.addEventListener('keydown', event => { if (event.key === 'Escape'"));vm.runInContext(line,f.context);
 f.context.mediaEscape({key:'Escape',preventDefault(){}});await tick();await tick();assert.equal(f.chat.videoPreview,null);assert.equal(f.saves().length,1);
});
