import assert from 'node:assert/strict';
import { readFile, writeFile } from 'node:fs/promises';
import { createHash } from 'node:crypto';

const [port, mode, output] = process.argv.slice(2);
assert.ok(['run', 'cold'].includes(mode));
const tabs = await fetch(`http://127.0.0.1:${Number(port)}/json/list`).then(r => r.json());
const tab = tabs.find(t => t.type === 'page' && t.url === 'http://tauri.localhost/');
assert.ok(tab);
const socket = new WebSocket(tab.webSocketDebuggerUrl);
let serial = 0;
const pending = new Map();
socket.addEventListener('message', event => {
  const message = JSON.parse(event.data), item = pending.get(message.id);
  if (!item) return;
  pending.delete(message.id); clearTimeout(item.timer);
  if (message.error) item.reject(new Error(message.error.message)); else item.resolve(message.result);
});
await new Promise((resolve, reject) => { socket.addEventListener('open', resolve, { once: true }); socket.addEventListener('error', reject, { once: true }); });
const request = (method, params = {}) => new Promise((resolve, reject) => {
  const id = ++serial, timer = setTimeout(() => { pending.delete(id); reject(new Error(`${method} timeout`)); }, 10000);
  pending.set(id, { resolve, reject, timer }); socket.send(JSON.stringify({ id, method, params }));
});
const js = JSON.stringify;
const evaluate = async expression => {
  const result = await request('Runtime.evaluate', { expression, awaitPromise: true, returnByValue: true });
  if (result.exceptionDetails) throw new Error(result.exceptionDetails.exception?.description || result.exceptionDetails.text);
  return result.result.value;
};
const invoke = (area, command, args = {}) => evaluate(`window.__TAURI__.core.invoke(${js(command)},${js(args)},{headers:{'x-nanfeng-data-area':${js(area)}}})`);
const until = async (check, label) => { const end = Date.now() + 15000; while (Date.now() < end) { if (await check()) return; await new Promise(r => setTimeout(r, 100)); } throw new Error(label); };
const hash = value => createHash('sha256').update(js(value)).digest('hex');
const key = async (name, code, virtual, modifiers = 0) => {
  await request('Input.dispatchKeyEvent', { type: name === 'Enter' ? 'keyDown' : 'rawKeyDown', key: name, code, windowsVirtualKeyCode: virtual, nativeVirtualKeyCode: virtual, modifiers, ...(name === 'Enter' ? { text: '\r', unmodifiedText: '\r' } : {}) });
  await request('Input.dispatchKeyEvent', { type: 'keyUp', key: name, code, windowsVirtualKeyCode: virtual, nativeVirtualKeyCode: virtual, modifiers });
};
const click = selector => evaluate(`document.querySelector(${js(selector)}).click();true`);
const focus = selector => evaluate(`document.querySelector(${js(selector)}).focus();true`);
const snapshot=()=>evaluate(`(()=>{const l=document.querySelector('.composer-transient-sheet');const items=l?[...l.querySelectorAll('button:not(:disabled)')].filter(e=>e.getClientRects().length&&!e.closest('[inert]')):[];return {layer:!!l,inside:!!l?.contains(document.activeElement),index:items.indexOf(document.activeElement),count:items.length,tag:document.activeElement.tagName,action:document.activeElement.dataset.action||null,tier:document.activeElement.dataset.tier||null,actions:items.map(e=>e.dataset.action),page:globalThis.__nanfengDesktopWorkState.composerAddPage}})()`);
const record=mode==='cold'?JSON.parse(await readFile(output,'utf8')):{checks:[],areas:{},injection:'WebView2 CDP',physicalKeyboard:false,normalStartup:false};
const check=(name,pass,detail)=>{record.checks.push({name,pass,detail});assert.ok(pass,name+': '+js(detail));};
const tick=()=>evaluate('new Promise(r=>requestAnimationFrame(()=>requestAnimationFrame(r)))');
const cycle=async(area,label)=>{
 let s=await snapshot();check(`${area} ${label} initial focus`,s.inside,s);
 // Start at the first visible control, then verify both boundaries and every control.
 await evaluate(`(()=>{const l=document.querySelector('.composer-transient-sheet');[...l.querySelectorAll('button:not(:disabled)')].filter(e=>e.getClientRects().length&&!e.closest('[inert]'))[0].focus();return true})()`);
 await key('Tab','Tab',9,8);s=await snapshot();check(`${area} ${label} ShiftTab wrap`,s.inside&&s.index===s.count-1,s);
 await key('Tab','Tab',9);s=await snapshot();check(`${area} ${label} Tab wrap`,s.inside&&s.index===0,s);
 for(let i=0;i<s.count;i++){await key('Tab','Tab',9);const t=await snapshot();check(`${area} ${label} Tab ${i}`,t.inside,t);}
 await evaluate(`globalThis.__composerKeyLeaks=[];globalThis.__composerLeakHandler=e=>{if(e.ctrlKey||e.key==='Escape')globalThis.__composerKeyLeaks.push(e.key)};window.addEventListener('keydown',globalThis.__composerLeakHandler);true`);
 await key('o','KeyO',79,2);const leaks=await evaluate('globalThis.__composerKeyLeaks');await evaluate('window.removeEventListener("keydown",globalThis.__composerLeakHandler);true');check(`${area} ${label} CtrlO consumed`,leaks.length===0,leaks);
};
try{
 await until(()=>evaluate('Boolean(window.__TAURI__?.core&&globalThis.__nanfengDesktopWorkState?.runtimeInfo?.dataAreasReady)'), 'ready');
 const runtime=await invoke('CHAT','read_desktop_runtime_info');assert.equal(runtime.platform,'windows');assert.equal(runtime.startupMode,'UI_SCHEMA_DIAGNOSTIC_ACCEPTANCE');assert.equal(runtime.automaticWorkSuppressed,true);record.runtime=runtime;
 if(mode==='run'){
  for(const area of ['CHAT','WORK'])await invoke(area,'ensure_desktop_local_workspace');
  await request('Page.reload');await until(()=>evaluate('Boolean(document.querySelector("#chat-composer"))'), 'reloaded');
 }
 for(const area of ['CHAT','WORK']){
  if(mode==='cold'){
   const s=record.areas[area];assert.deepEqual(await invoke(area,'read_desktop_conversation_draft',{workspaceId:s.workspaceId,conversationId:null}),s.draft);assert.equal(hash((await invoke(area,'read_desktop_workspace',{workspaceId:s.workspaceId})).exchange),s.exchangeHash);continue;
  }
  await click(`[data-action=show-${area==='CHAT'?'chat':'work'}]`);await until(()=>evaluate(`globalThis.__nanfengDesktopWorkState.dataArea===${js(area)}&&Boolean(document.querySelector('#chat-composer'))`),'area composer');
  const workspaceId=(await invoke(area,'ensure_desktop_local_workspace')).summary.id;const before=(await invoke(area,'read_desktop_workspace',{workspaceId})).exchange;
  await focus('#chat-composer');await evaluate(`(()=>{const e=document.querySelector('#chat-composer');e.setSelectionRange(0,e.value.length);return true})()`);await request('Input.insertText',{text:`${area} composer transient synthetic draft`});
  await until(async()=>(await invoke(area,'read_desktop_conversation_draft',{workspaceId,conversationId:null}))?.text===`${area} composer transient synthetic draft`,'draft persisted');
  const draft=await invoke(area,'read_desktop_conversation_draft',{workspaceId,conversationId:null});
  for(const action of ['toggle-composer-add','toggle-p6g-model-picker']){
   await focus(`[data-action=${action}]`);await key('Enter','Enter',13);await until(async()=>(await snapshot()).layer,'keyboard open');await cycle(area,action);
   if(action==='toggle-composer-add'){
    await focus('[data-action=open-composer-tone-picker]');await key('Enter','Enter',13);await until(async()=>(await snapshot()).page==='tone','tone');await cycle(area,'tone');
    await key('Escape','Escape',27);await tick();let s=await snapshot();check(`${area} tone Escape returns root`,s.layer&&s.page==='root'&&s.action==='open-composer-tone-picker',s);
   }else{
    await focus('[data-action=select-p6g-tier][data-tier=DAILY]');await key('Enter','Enter',13);await until(()=>evaluate('Boolean(document.querySelector("[data-action=p6g-picker-back]"))'),'tier');await cycle(area,'daily model tier');
    await key('Escape','Escape',27);await tick();let s=await snapshot();check(`${area} tier Escape returns group`,s.layer&&s.action==='close-p6g-model-picker',s);
   }
   await evaluate(`globalThis.__composerEscapeLeaks=0;globalThis.__composerEscapeHandler=e=>{if(e.key==='Escape')globalThis.__composerEscapeLeaks++};window.addEventListener('keydown',globalThis.__composerEscapeHandler);true`);
   await key('Escape','Escape',27);await tick();const s=await snapshot(),leaks=await evaluate('globalThis.__composerEscapeLeaks');await evaluate('window.removeEventListener("keydown",globalThis.__composerEscapeHandler);true');check(`${area} ${action} Escape restores exact trigger`,!s.layer&&s.action===action&&leaks===0,{s,leaks});
   // Space activation uses native default button behavior as a separate route.
   await key(' ','Space',32);await until(async()=>(await snapshot()).layer,'Space open');check(`${area} ${action} Space focus`,(await snapshot()).inside,await snapshot());await key('Escape','Escape',27);await tick();check(`${area} ${action} Space close trigger`,(await snapshot()).action===action,await snapshot());
  }
  for(const action of ['toggle-composer-add','toggle-p6g-model-picker']){
   await focus(`[data-action=${action}]`);await key('Enter','Enter',13);await until(async()=>(await snapshot()).inside,'pointer test opened');
   await evaluate(`globalThis.__composerPointerLeaks=[];globalThis.__composerPointerHandler=e=>globalThis.__composerPointerLeaks.push(e.type);for(const type of ['pointerdown','pointerup','click'])window.addEventListener(type,globalThis.__composerPointerHandler);true`);
   const point=await evaluate(`(()=>{const r=document.querySelector('[data-action=show-settings]').getBoundingClientRect();const x=r.left+r.width/2,y=r.top+r.height/2;return {x,y,hit:document.elementFromPoint(x,y).dataset.action}})()`);
   check(`${area} ${action} backdrop covers background`,point.hit=== (action==='toggle-composer-add'?'close-composer-add':'close-p6g-model-picker'),point);
   await request('Input.dispatchMouseEvent',{type:'mousePressed',x:point.x,y:point.y,button:'left',buttons:1,clickCount:1});await request('Input.dispatchMouseEvent',{type:'mouseReleased',x:point.x,y:point.y,button:'left',buttons:0,clickCount:1});await tick();
   const after=await snapshot(),leaks=await evaluate('globalThis.__composerPointerLeaks'),pane=await evaluate('globalThis.__nanfengDesktopWorkState.pane');await evaluate(`for(const type of ['pointerdown','pointerup','click'])window.removeEventListener(type,globalThis.__composerPointerHandler);true`);
   check(`${area} ${action} pointer dismiss consumed`,!after.layer&&after.action===action&&leaks.length===0&&pane!=='settings',{after,leaks,pane});
  }
  check(`${area} full draft unchanged`,js(await invoke(area,'read_desktop_conversation_draft',{workspaceId,conversationId:null}))===js(draft),null);check(`${area} exchange unchanged`,hash((await invoke(area,'read_desktop_workspace',{workspaceId})).exchange)===hash(before),null);
  record.areas[area]={workspaceId,draft,exchangeHash:hash(before)};
 }
 if(mode==='cold')record.cold={pass:true,areas:['CHAT','WORK']};
}catch(error){record.error=String(error);process.exitCode=1;}finally{await writeFile(output,js(record,null,2));socket.close();}
