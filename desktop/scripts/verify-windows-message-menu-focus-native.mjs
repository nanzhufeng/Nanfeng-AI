import assert from 'node:assert/strict';
import { readFile, writeFile } from 'node:fs/promises';
import { createHash } from 'node:crypto';
import { fileURLToPath } from 'node:url';

const [port, mode, output] = process.argv.slice(2);
assert.ok(['run', 'extend', 'cold'].includes(mode));
const importedFixture = process.argv.includes('--imported');
const assistantMenu = process.argv.includes('--assistant');
let tabs;
for (let attempt = 0; attempt < 100; attempt++) {
  try {
    tabs = await fetch(`http://127.0.0.1:${Number(port)}/json/list`).then(r => r.json());
    if (tabs.some(t => t.type === 'page' && t.url === 'http://tauri.localhost/')) break;
  } catch { /* The launch receipt precedes WebView2 startup. */ }
  await new Promise(resolve => setTimeout(resolve, 100));
}
assert.ok(tabs, 'WebView2 startup timed out');
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
  if (result.exceptionDetails) throw new Error(JSON.stringify(result.exceptionDetails));
  return result.result.value;
};
const invoke = (area, command, args = {}) => evaluate(`window.__TAURI__.core.invoke(${js(command)},${js(args)},{headers:{'x-nanfeng-data-area':${js(area)}}})`);
const until = async (check, label) => { const end = Date.now() + 15000; while (Date.now() < end) { if (await check()) return; await new Promise(r => setTimeout(r, 100)); } throw new Error(label); };
const hash = value => createHash('sha256').update(js(value)).digest('hex');
const key = async (name, code, virtual, modifiers = 0) => {
  await request('Input.dispatchKeyEvent', { type: name === 'Enter' ? 'keyDown' : 'rawKeyDown', key: name, code, windowsVirtualKeyCode: virtual, nativeVirtualKeyCode: virtual, modifiers, ...(name === 'Enter' ? { text: '\r', unmodifiedText: '\r' } : {}) });
  await request('Input.dispatchKeyEvent', { type: 'keyUp', key: name, code, windowsVirtualKeyCode: virtual, nativeVirtualKeyCode: virtual, modifiers });
};
const record=mode!=='run'?JSON.parse(await readFile(output,'utf8')):{checks:[],areas:{},injection:'WebView2 CDP',physicalKeyboard:false};
const check=(name,pass,detail)=>{record.checks.push({name,pass,detail});assert.ok(pass,name+': '+js(detail));};
const settle=()=>new Promise(r=>setTimeout(r,100));
const snap=()=>evaluate(`(()=>{const l=document.querySelector('.assistant-message-menu'),items=l?[...l.querySelectorAll('[role="menuitem"]')].filter(e=>e.getClientRects().length):[];return {open:!!l,inside:!!l?.contains(document.activeElement),index:items.indexOf(document.activeElement),count:items.length,action:document.activeElement.dataset.action,messageId:document.activeElement.dataset.messageId,tag:document.activeElement.tagName}})()`);
const pointer=async selector=>{const p=await evaluate(`(()=>{const r=document.querySelector(${js(selector)}).getBoundingClientRect();return {x:r.x+r.width/2,y:r.y+r.height/2}})()`);await request('Input.dispatchMouseEvent',{type:'mouseMoved',...p});await settle();await request('Input.dispatchMouseEvent',{type:'mousePressed',...p,button:'left',clickCount:1});await request('Input.dispatchMouseEvent',{type:'mouseReleased',...p,button:'left',clickCount:1});await settle();};
try{
delete record.error;
await until(()=>evaluate('Boolean(document.querySelector("#chat-composer") && globalThis.__nanfengDesktopWorkState?.runtimeInfo?.dataAreasReady)'), 'ready');
const runtime=await invoke('CHAT','read_desktop_runtime_info');assert.equal(runtime.startupMode,'UI_SCHEMA_DIAGNOSTIC_ACCEPTANCE');assert.equal(runtime.automaticWorkSuppressed,true);record.runtime=runtime;
if(mode==='run'){
for(const area of ['CHAT','WORK']){
if (importedFixture) {
const preflight=await invoke(area,'stage_preflight_selected_exchange',{selectedPath:fileURLToPath(new URL('../../protocol/artifacts/nfai.exchange.v1.golden.nfai-exchange',import.meta.url))});
const imported=await invoke(area,'import_staged_exchange_as_new_workspace',{args:{stagingId:preflight.stagingId,workspaceTitle:`Synthetic ${area} imported menu`}});
record.areas[area]={workspaceId:imported.summary.id,conversationId:imported.exchange.conversations[0].id};
continue;
}
const workspaceId=(await invoke(area,'ensure_desktop_local_workspace')).summary.id;
const created=await invoke(area,'mutate_desktop_domain',{args:{intentId:`menu-${crypto.randomUUID()}`,workspaceId,entity:'conversation',action:'create',fields:{title:`Synthetic ${area} menu`,projectId:null,firstMessage:`Synthetic ${area} menu keyboard content`}}});record.areas[area]={workspaceId,conversationId:created.objectId};
}await request('Page.reload');await until(()=>evaluate('Boolean(document.querySelector("#chat-composer"))'),'reload');
}
for(const area of ['CHAT','WORK']){
const saved=record.areas[area];const workspace=()=>invoke(area,'read_desktop_workspace',{workspaceId:saved.workspaceId});const draft=()=>invoke(area,'read_desktop_conversation_draft',{workspaceId:saved.workspaceId,conversationId:saved.conversationId});
if(mode==='cold'){check(`${area} cold full exchange`,hash((await workspace()).exchange)===saved.exchangeHash,null);check(`${area} cold full draft`,js(await draft())===js(saved.draft),null);continue;}
await pointer(`[data-action=show-${area==='CHAT'?'chat':'work'}]`);await until(()=>evaluate(`globalThis.__nanfengDesktopWorkState.dataArea===${js(area)}`),'area');
await pointer(`[data-action="select-chat"][data-id="${saved.conversationId}"]`);await until(()=>evaluate(`Boolean(document.querySelector('[data-action="open-assistant-message-menu"]'))`),'message trigger');
const before=(await workspace()).exchange;saved.draft=await draft();const trigger=(assistantMenu?'.chat-message.assistant ':'')+'[data-action="open-assistant-message-menu"]';await pointer(trigger);let s=await snap();check(`${area} pointer initial focus`,s.open&&s.index===0,s);await key('Escape','Escape',27);await settle();s=await snap();const id=s.messageId;check(`${area} pointer Esc exact trigger`,!s.open&&s.action==='open-assistant-message-menu'&&!!id,s);
for(const [name,code,virtual,modifiers] of [['Enter','Enter',13,0],[' ','Space',32,0],['F10','F10',121,8]]){
await key(name,code,virtual,modifiers);await settle();s=await snap();check(`${area} ${code} opens`,s.open&&s.index===0,s);
const n=s.count;for(let i=1;i<=n;i++){await key('Tab','Tab',9);await settle();s=await snap();check(`${area} ${code} Tab ${i}`,s.inside&&s.index===i%n,s);}
await key('Tab','Tab',9,8);await settle();s=await snap();check(`${area} ${code} reverse boundary`,s.index===n-1,s);
await evaluate(`window.__menuLeaks=[];window.__menuLeakHandler=e=>{if(e.ctrlKey||e.key==='Escape')window.__menuLeaks.push(e.key)};window.addEventListener('keydown',window.__menuLeakHandler);true`);
await key('o','KeyO',79,2);await key('Escape','Escape',27);await settle();s=await snap();const leaks=await evaluate('window.__menuLeaks');await evaluate('window.removeEventListener("keydown",window.__menuLeakHandler);true');check(`${area} ${code} shortcut and Esc consumed`,leaks.length===0,leaks);check(`${area} ${code} exact return`,!s.open&&s.messageId===id&&s.action==='open-assistant-message-menu',s);
}
await key('Enter','Enter',13);await settle();await evaluate(`window.__menuPointerLeaks=[];window.__menuPointerHandler=e=>window.__menuPointerLeaks.push(e.type);for(const t of ['pointerdown','pointerup','click'])window.addEventListener(t,window.__menuPointerHandler);true`);await pointer('#chat-composer');s=await snap();const leaks=await evaluate('window.__menuPointerLeaks');await evaluate(`for(const t of ['pointerdown','pointerup','click'])window.removeEventListener(t,window.__menuPointerHandler);true`);check(`${area} outside full sequence consumed`,leaks.length===0,leaks);check(`${area} outside exact trigger`,!s.open&&s.messageId===id,s);
check(`${area} full exchange unchanged`,hash((await workspace()).exchange)===hash(before),null);check(`${area} full draft unchanged`,js(await draft())===js(saved.draft),null);saved.exchangeHash=hash(before);
}
if(mode==='cold')record.cold={pass:true};
}catch(error){record.error=String(error);process.exitCode=1;}finally{await writeFile(output,js(record,null,2));socket.close();}



