import test from 'node:test';
import assert from 'node:assert/strict';
import { mountNanfengIsland,IslandPresentation,islandMarkup,taskStage,taskDetail} from '../src/nanfeng-island.mjs';
const task=(key='CHAT:a',state='RUNNING',savedCharacters=123)=>({key,state,savedCharacters,startedAt:1000,updatedAt:3000,modelLabel:'6 Astra · RC',dataArea:'CHAT'});
test('task progress uses saved characters and actual state, never estimates completion',()=>{
  assert.equal(taskStage(task('a','RUNNING',0)),'等待模型输出');
  assert.equal(taskDetail(task(),9000),'已保存 123 字 · 8 秒');
  assert.equal(taskDetail(task('a','UNKNOWN'),9000),'已保存 123 字 · 2 秒');
  const c=new IslandPresentation();c.update([task()],3000);c.toggle(3000);
  const html=islandMarkup(c,3000);assert.match(html,/6 Astra · RC/);assert.match(html,/停止/);assert.doesNotMatch(html,/%|tokens|暂停/);
});

test('Windows disables retained WORK viewing with a reason while keeping its exact stop target',()=>{
 const c=new IslandPresentation();c.update([{...task('WORK:x'),dataArea:'WORK'},task('CHAT:y')],3000);c.toggle(3000);
 const windows=islandMarkup(c,3000,{platform:'windows'});
 assert.match(windows,/<button[^>]*data-island-action="open"[^>]*data-island-key="WORK:x"[^>]*disabled/);
 assert.match(windows,/工作区查看入口已隐藏/);
 assert.match(windows,/data-island-action="stop" data-island-key="WORK:x"/);
 assert.doesNotMatch(windows,/<button[^>]*data-island-action="open"[^>]*data-island-key="CHAT:y"[^>]*disabled/);
 const mac=islandMarkup(c,3000,{platform:'macos'});
 assert.doesNotMatch(mac,/disabled|工作区查看入口已隐藏/);
});
test('expanded results pause dismissal and resume a full countdown on collapse',()=>{
  const c=new IslandPresentation();c.update([task('a','COMPLETED')],4000);c.toggle(4500);
  assert.equal(c.visible(60000).length,1);c.toggle(60000);
  assert.equal(c.visible(62000).length,1);assert.equal(c.visible(63000).length,0);
});
test('settings feedback temporarily presents above running tasks without stopping or losing them',()=>{
  const c=new IslandPresentation();c.update([task()],3000);c.feedback('API Key 已安全保存在本机。','success',3000);
  assert.match(islandMarkup(c,3001),/API Key/);assert.match(islandMarkup(c,6000),/正在生成/);
  c.feedback('设置保存失败','error',7000);c.toggle(7000);assert.match(islandMarkup(c,60000),/设置保存失败/);
});
test('multi-task details preserve identities, escape text, and provide only supported actions',()=>{
  const c=new IslandPresentation();c.update([task('CHAT:a'),{...task('WORK:a'),modelLabel:'<script>secret</script>'}],3000);c.toggle(3000);
  const html=islandMarkup(c,3001);assert.match(html,/2 个对话生成中/);assert.match(html,/data-island-key="WORK:a"/);assert.doesNotMatch(html,/<script>/);
});
test('failed and cancelled tasks remain truthful; dismissal leaves active tasks available',()=>{
  const c=new IslandPresentation();c.update([task('a','UNKNOWN'),task('b','CANCELLED'),task('c')],3000);c.toggle(3000);
  assert.match(islandMarkup(c,3000),/生成中断/);assert.match(islandMarkup(c,3000),/已停止生成/);
  c.dismiss();assert.deepEqual(c.visible(3001).map(t=>t.key),['c']);
  assert.equal(taskStage(task('x','COMPLETED_ACCOUNTING_PENDING')),'回复已完成');
});
test('expanding a newer running task does not resurrect expired results',()=>{
  const c=new IslandPresentation();c.update([task('old','COMPLETED')],3000);
  assert.equal(c.visible(7000).length,0);
  c.update([task('old','COMPLETED'),{...task('new'),startedAt:7000,updatedAt:7000}],7000);c.toggle(7000);
  assert.deepEqual(c.visible(7001).map(t=>t.key),['new']);
});
test('outside collapse changes only presentation and removes the redundant collapse button',()=>{
  const c=new IslandPresentation();c.update([task()],3000);c.toggle(3000);
  assert.equal(c.expanded,true);assert.doesNotMatch(islandMarkup(c,3000),/data-island-action="toggle">收起/);
  c.collapse(4000);assert.equal(c.expanded,false);assert.deepEqual(c.visible(4001).map(t=>t.key),['CHAT:a']);assert.equal(c.tasks[0].state,'RUNNING');
  c.collapse(4002);assert.equal(c.expanded,false);
});

test('foreground presentation never reads generation tasks or displays an island', () => {
  const original = globalThis.document;
  globalThis.document = { addEventListener() {}, removeEventListener() {}, activeElement: null };
  const host = { innerHTML: 'old island', addEventListener() {}, contains: () => false, firstElementChild: null };
  let reads = 0;
  const presenter = mountNanfengIsland({ host, invoke: async () => { reads++; return []; } });
  try {
    assert.equal(reads, 0);
    presenter.feedback('saved'); presenter.update([task()]);
    assert.equal(host.innerHTML, '');
  } finally { presenter.dispose(); globalThis.document = original; }
});

test('interrupted generation expires after three seconds even while expanded and preserves running peers', () => {
  const c = new IslandPresentation();
  c.update([task()], 3000); c.toggle(3000);
  c.update([task('CHAT:a', 'UNKNOWN')], 3000);
  assert.equal(c.visible(5900).length, 1);
  assert.equal(c.visible(6100).length, 0);
  assert.equal(c.expanded, false);
  c.update([task('CHAT:a', 'UNKNOWN'), task('CHAT:peer')], 6200);
  assert.deepEqual(c.visible(6200).map(t => t.key), ['CHAT:peer']);
  assert.equal(islandMarkup(c,6200).includes('生成中断'), false);
});

test('a fresh generation starts compact even if the prior expanded task vanished between polls', () => {
  const c = new IslandPresentation();
  c.update([task('old')],3000);
  assert.equal(c.expanded,false);
  c.toggle(3000);
  c.update([{...task('new'),startedAt:9000,updatedAt:9000}],9000);
  assert.equal(c.expanded,false);
  assert.match(islandMarkup(c,9000),/aria-expanded="false"/);
});


test('dispose releases native-view handlers and ignores a delayed metadata read',async()=>{
 const original=globalThis.document;const listeners=new Map(),hostListeners=new Map();let release;const pending=new Promise(done=>release=done);const calls=[];
 globalThis.document={activeElement:null,addEventListener:(name,fn)=>listeners.set(name,fn),removeEventListener:(name,fn)=>{if(listeners.get(name)===fn)listeners.delete(name);}};
 const host={innerHTML:'',firstElementChild:null,contains:()=>false,querySelector:()=>null,addEventListener:(name,fn)=>hostListeners.set(name,fn),removeEventListener:(name,fn)=>{if(hostListeners.get(name)===fn)hostListeners.delete(name);}};
 const frames=[];
 const presenter=mountNanfengIsland({host,background:true,invoke:async (name,args)=>{calls.push(name);if(args)frames.push(args);return pending;}});
 try{
  assert.ok(listeners.has('pointerdown'));assert.ok(listeners.has('keydown'));assert.ok(hostListeners.has('click'));
  presenter.controller.update([task()],3000);presenter.controller.toggle(3000);let prevented=0;
  listeners.get('keydown')({key:'Escape',preventDefault:()=>prevented++,stopPropagation:()=>{}});assert.equal(prevented,1);assert.equal(presenter.controller.expanded,false);
  assert.equal(frames.length,1);assert.equal(frames[0].expanded,false);
  presenter.dispose();release([task('late')]);await new Promise(done=>setImmediate(done));
  assert.equal(listeners.size,0);assert.equal(hostListeners.size,0);assert.equal(host.innerHTML,'');assert.deepEqual(calls,['read_generation_island','generation_island_visibility']);assert.equal(presenter.controller.tasks[0].key,'CHAT:a');
 }finally{presenter.dispose();release([]);globalThis.document=original;}
});

const settle=()=>new Promise(done=>setImmediate(done));
function nativeView(visibility=async()=>({outsideClick:'active'})) {
 const original={document:globalThis.document,setTimeout:globalThis.setTimeout,clearTimeout:globalThis.clearTimeout};
 const listeners=new Map(),hostListeners=new Map(),timers=new Map(),frames=[];let now=3000,sequence=0;
 globalThis.document={activeElement:null,addEventListener:(name,fn)=>listeners.set(name,fn),removeEventListener:name=>listeners.delete(name)};
 globalThis.setTimeout=fn=>{const id=++sequence;timers.set(id,fn);return id;};globalThis.clearTimeout=id=>timers.delete(id);
 // Geometry only: no browser or native window is created by this fixture.
 const surface={getBoundingClientRect:()=>({height:90,width:300}),getAnimations:()=>[],animate:()=>{}};
 const summary={textContent:''};
 const host={innerHTML:'',get firstElementChild(){return this.innerHTML?surface:null;},contains:()=>false,querySelector:selector=>selector==='[data-island-summary]'?summary:null,querySelectorAll:()=>[],addEventListener:(name,fn)=>hostListeners.set(name,fn),removeEventListener:name=>hostListeners.delete(name)};
 const presenter=mountNanfengIsland({host,background:true,clock:()=>now,invoke:async(name,args)=>{
  if(name==='read_generation_island')return [task()];
  assert.equal(name,'generation_island_visibility');frames.push({...args});return visibility(args);
 }});
 return {presenter,frames,setNow:value=>now=value,
  toggle:()=>hostListeners.get('click')({target:{closest:()=>({dataset:{islandAction:'toggle'}})}}),
  escape:()=>listeners.get('keydown')({key:'Escape',preventDefault(){},stopPropagation(){}}),
  async poll(){const entry=timers.entries().next().value;assert.ok(entry);timers.delete(entry[0]);await entry[1]();await settle();},
  dispose(){presenter.dispose();Object.assign(globalThis,original);}};
}

test('native presentation publishes expand and Escape immediately before the next poll',async()=>{
 const view=nativeView();
 try{
  await settle();assert.equal(view.frames.at(-1).expanded,false);
  const count=view.frames.length;await view.toggle();
  assert.equal(view.frames.length,count+1);assert.equal(view.frames.at(-1).expanded,true);assert.equal(view.frames.at(-1).visible,true);
  view.escape();assert.equal(view.frames.length,count+2);assert.equal(view.frames.at(-1).expanded,false);
 }finally{view.dispose();}
});

test('unavailable outside click reports once without renewing the deadline until a new expansion',async()=>{
 const view=nativeView(async()=>({outsideClick:'unavailable'}));
 try{
  await settle();await view.toggle();await settle();
  assert.match(view.presenter.controller.notice.message,/点击顶部收起/);
  const deadline=view.presenter.controller.notice.until;const count=view.frames.length;
  view.setNow(5000);await view.poll();assert.equal(view.presenter.controller.notice.until,deadline);assert.equal(view.frames.length,count+1);
  view.escape();view.setNow(6000);await view.toggle();await settle();
  assert.equal(view.presenter.controller.notice.until,14000);
 }finally{view.dispose();}
});

test('an old unavailable response cannot replace a newer collapsed presentation',async()=>{
 let release;const view=nativeView(args=>args.expanded?new Promise(done=>release=done):Promise.resolve({outsideClick:'inactive'}));
 try{
  await settle();await view.toggle();assert.equal(typeof release,'function');
  view.escape();await settle();release({outsideClick:'unavailable'});await settle();
  assert.equal(view.presenter.controller.expanded,false);assert.equal(view.presenter.controller.notice,null);
 }finally{view.dispose();}
});

test('a visibility response arriving after disposal cannot display feedback or publish again',async()=>{
 let release;const view=nativeView(args=>args.expanded?new Promise(done=>release=done):Promise.resolve({outsideClick:'inactive'}));
 try{
  await settle();await view.toggle();const count=view.frames.length;view.presenter.dispose();
  release({outsideClick:'unavailable'});await settle();
  assert.equal(view.presenter.controller.notice,null);assert.equal(view.frames.length,count);
 }finally{view.dispose();}
});

function deadlineView(){
 const original={document:globalThis.document,setTimeout:globalThis.setTimeout,clearTimeout:globalThis.clearTimeout};let now=3000,sequence=0,releaseRead,reads=0;const pending=new Promise(done=>releaseRead=done),timers=new Map(),frames=[],listeners=new Map();
 globalThis.document={activeElement:null,addEventListener(){},removeEventListener(){}};globalThis.setTimeout=(fn,delay=0)=>{const id=++sequence;timers.set(id,{fn,due:now+delay});return id;};globalThis.clearTimeout=id=>timers.delete(id);
 const surface={getBoundingClientRect:()=>({height:90,width:300}),getAnimations:()=>[],animate(){}};const summary={textContent:''};const host={innerHTML:'',get firstElementChild(){return this.innerHTML?surface:null;},contains:()=>false,querySelector:selector=>selector==='[data-island-summary]'?summary:null,querySelectorAll:()=>[],addEventListener:(name,fn)=>listeners.set(name,fn),removeEventListener:name=>listeners.delete(name)};
 const presenter=mountNanfengIsland({host,background:true,clock:()=>now,invoke:async(name,args)=>{if(name==='read_generation_island'){reads++;return pending;}assert.equal(name,'generation_island_visibility');frames.push({...args});return {outsideClick:'inactive'};}});
 return {presenter,host,frames,timers,get reads(){return reads;},toggle:()=>listeners.get('click')({target:{closest:()=>({dataset:{islandAction:'toggle'}})}}),async advance(ms){now+=ms;for(let i=0;i<32;i++){const due=[...timers].filter(([,t])=>t.due<=now).sort((a,b)=>a[1].due-b[1].due)[0];if(!due)break;timers.delete(due[0]);await due[1].fn();await settle();}await settle();},async dispose(){presenter.dispose();releaseRead([]);await settle();Object.assign(globalThis,original);}};
}
test('completion expiry does not wait for a pending native metadata read',async()=>{
 const v=deadlineView();try{v.presenter.update([task('completed','COMPLETED')]);await settle();assert.ok(v.host.innerHTML);await v.advance(2500);assert.ok(v.host.innerHTML);await v.advance(100);assert.equal(v.host.innerHTML,'');assert.equal(v.frames.at(-1).visible,false);assert.equal(v.reads,1);}finally{await v.dispose();}
});

for(const state of ['FAILED','UNKNOWN','CANCELLED'])test(`${state} expiry stays at three seconds while expanded and metadata is pending`,async()=>{
 const v=deadlineView();try{v.presenter.update([task('terminal','RUNNING')]);await v.toggle();v.presenter.update([task('terminal',state)]);await settle();assert.equal(v.presenter.controller.expanded,true);await v.advance(2999);assert.ok(v.host.innerHTML);await v.advance(1);assert.equal(v.host.innerHTML,'');assert.equal(v.presenter.controller.expanded,false);assert.equal(v.reads,1);}finally{await v.dispose();}
});
test('completed presentation holds while expanded then receives a full collapse deadline without polling',async()=>{
 const v=deadlineView();try{v.presenter.update([task('held','COMPLETED')]);await v.toggle();await v.advance(20000);assert.ok(v.host.innerHTML);await v.toggle();await v.advance(2599);assert.ok(v.host.innerHTML);await v.advance(1);assert.equal(v.host.innerHTML,'');assert.equal(v.reads,1);}finally{await v.dispose();}
});
test('feedback expires independently from pending metadata without creating another task read',async()=>{
 const v=deadlineView();try{v.presenter.feedback('SYNTHETIC_SAVE_FAILED','error');await v.advance(7999);assert.ok(v.host.innerHTML);await v.advance(1);assert.equal(v.host.innerHTML,'');assert.equal(v.frames.at(-1).visible,false);assert.equal(v.reads,1);}finally{await v.dispose();}
});
test('dispose cancels presentation deadlines and an already queued expiry cannot repaint',async()=>{
 const v=deadlineView();v.presenter.update([task('disposed','COMPLETED')]);await settle();const pending=[...v.timers.values()][0];assert.ok(pending);const count=v.frames.length;await v.dispose();assert.equal(v.timers.size,0);pending.fn();await settle();assert.equal(v.host.innerHTML,'');assert.equal(v.frames.length,count);
});
