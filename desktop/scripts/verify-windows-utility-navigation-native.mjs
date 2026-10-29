import assert from 'node:assert/strict';
import { readFile, writeFile } from 'node:fs/promises';
import { createHash } from 'node:crypto';
import { fileURLToPath } from 'node:url';

const [port, mode, output] = process.argv.slice(2);
assert.ok(['run', 'cold'].includes(mode));
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
const record=(mode==='cold'||process.argv.includes('--resume'))?JSON.parse(await readFile(output,'utf8')):{checks:[],areas:{},layer:'isolated diagnostic native',delayLayer:'frontend bridge after real IPC',physicalInput:false};
const checks=mode==='cold'?(record.coldChecks=[]):(record.checks=[]);
const check=(name,pass,detail)=>{checks.push({name,pass,detail});assert.ok(pass,name+': '+js(detail));};
const click=selector=>evaluate(`(()=>{const e=document.querySelector(${js(selector)});if(!e)throw Error('missing '+${js(selector)});e.click();return true})()`);
const settle=()=>new Promise(r=>setTimeout(r,100));
const current=()=>evaluate('({area:globalThis.__nanfengDesktopWorkState.dataArea,pane:globalThis.__nanfengDesktopWorkState.pane,error:globalThis.__nanfengDesktopWorkState.error})');
try {
await until(()=>evaluate('Boolean(globalThis.__nanfengDesktopWorkState?.runtimeInfo?.dataAreasReady)'),'initial native ready before reload');
if(mode==='run'){
 await request('Debugger.enable');
 const source=await readFile(new URL('../src/app.mjs',import.meta.url),'utf8');
 const lineNumber=source.slice(0,source.indexOf('const tauriBridge =')).split('\n').length-1;
 assert.ok(source.includes('const tauriBridge ='));
 record.appSourceSha256=createHash('sha256').update(source).digest('hex');
 const breakpoint=await request('Debugger.setBreakpointByUrl',{urlRegex:'/app\.mjs$',lineNumber});
 const paused=async event=>{const message=JSON.parse(event.data);if(message.method!=='Debugger.paused')return;try{await request('Debugger.evaluateOnCallFrame',{callFrameId:message.params.callFrames[0].callFrameId,expression:'window.__TAURI__.core=Object.assign({},window.__TAURI__.core,{invoke:window.__TAURI__.core.invoke});window.__utilityBridgeInstalled=true'});}finally{await request('Debugger.resume');}};
 socket.addEventListener('message',paused);
 await request('Page.reload');
}
await until(()=>evaluate('Boolean(globalThis.__nanfengDesktopWorkState?.runtimeInfo?.dataAreasReady)'),'ready');
record.runtime=await invoke('CHAT','read_desktop_runtime_info');assert.equal(record.runtime.startupMode,'UI_SCHEMA_DIAGNOSTIC_ACCEPTANCE');assert.equal(record.runtime.automaticWorkSuppressed,true);delete record.error;
if(mode==='run'){
 if(!Object.keys(record.areas).length){
 const fixture=fileURLToPath(new URL('../../protocol/artifacts/nfai.exchange.v1.golden.nfai-exchange',import.meta.url));
 for(const area of ['CHAT','WORK']){const staged=await invoke(area,'stage_preflight_selected_exchange',{selectedPath:fixture});const w=await invoke(area,'import_staged_exchange_as_new_workspace',{args:{stagingId:staged.stagingId,workspaceTitle:`Synthetic ${area} utility navigation`}});const settings=await invoke(area,'read_desktop_app_settings');await invoke(area,'save_desktop_app_settings',{args:{...settings,product:{...settings.product,unreadIndicators:false},expectedRevision:settings.revision}});record.areas[area]={workspaceId:w.summary.id,conversationId:w.exchange.conversations[0].id,exchangeHash:hash(w.exchange)};}
 }
 await request('Page.reload');await until(()=>evaluate('Boolean(globalThis.__nanfengDesktopWorkState?.runtimeInfo?.dataAreasReady)'),'reload');
 for(const area of ['CHAT','WORK']){
  await click(`[data-action="show-${area==='CHAT'?'chat':'work'}"]`);await until(async()=> (await current()).area===area,'area');await new Promise(r=>setTimeout(r,500));
  for(const [page,command,loadingText] of [['reminders','read_desktop_reminders','正在读取定时任务'],['transcription','read_desktop_transcription_state','正在读取转写任务']].filter(()=>area==='CHAT')){
   await evaluate(`(()=>{const core=window.__TAURI__.core;window.__utilityOriginal=core.invoke;window.__utilityCalls=0;window.__utilityStacks=[];window.__utilityGate={};core.invoke=async function(command,...args){if(command!==${js(command)})return window.__utilityOriginal.call(this,command,...args);window.__utilityCalls++;window.__utilityStacks.push(new Error().stack);const result=await window.__utilityOriginal.call(this,command,...args);window.__utilityGate.readDone=true;await new Promise((resolve,reject)=>Object.assign(window.__utilityGate,{resolve,reject}));return result;};return true})()`);
   await click(`[data-action="show-${page}"]`);await until(()=>evaluate('Boolean(window.__utilityGate.reject)'),'real read finished and held');
   check(`${area} ${page} loading before completion`,await evaluate(`document.body.innerText.includes(${js(loadingText)})`),null);
   await click(`[data-action="show-${page}"]`);await settle();check(`${area} ${page} repeated entry merged`,await evaluate('window.__utilityCalls===1'),await evaluate('window.__utilityStacks'));
   await click(`[data-action="show-${area==='CHAT'?'chat':'work'}"]`);await until(async()=> (await current()).pane!==page,'leave utility');
   await evaluate('window.__TAURI__.core.invoke=window.__utilityOriginal;window.__utilityGate.reject(Error("SYNTHETIC_LATE_UTILITY_READ"));true');await settle();
   check(`${area} ${page} late error did not replace destination`,!(await current()).error.includes('SYNTHETIC_LATE_UTILITY_READ')&&(await current()).pane!==page,await current());
   await click(`[data-action="show-${page}"]`);await until(()=>evaluate(`!document.body.innerText.includes(${js(loadingText)})`),'new read settles');check(`${area} ${page} retry destination`,(await current()).pane===page,await current());
   await click('[data-action="show-chat"]');await new Promise(r=>setTimeout(r,500));
   await evaluate(`(()=>{const core=window.__TAURI__.core;window.__utilityOriginal=core.invoke;window.__utilityCalls=0;window.__utilityGate={};core.invoke=async function(command,...args){if(command!==${js(command)})return window.__utilityOriginal.call(this,command,...args);window.__utilityCalls++;const result=await window.__utilityOriginal.call(this,command,...args);await new Promise((resolve,reject)=>Object.assign(window.__utilityGate,{resolve,reject}));return result;};return true})()`);
   await click(`[data-action="show-${page}"]`);await until(()=>evaluate('Boolean(window.__utilityGate.reject)'),'cross area read held');
   await click('[data-action="show-chat"]');await until(()=>evaluate('Boolean(document.querySelector("[data-action=show-work]"))'),'cross area navigation entry');
   await click('[data-action="show-work"]');await until(async()=>(await current()).area==='WORK','cross area destination');
   await evaluate('window.__TAURI__.core.invoke=window.__utilityOriginal;window.__utilityGate.reject(Error("SYNTHETIC_CROSS_AREA_UTILITY_READ"));true');await settle();
   check(`${page} late CHAT error cannot project into WORK`,(await current()).area==='WORK'&&!(await current()).error.includes('SYNTHETIC_CROSS_AREA_UTILITY_READ'),await current());
   await click('[data-action="show-chat"]');await until(async()=>(await current()).area==='CHAT','return CHAT');await new Promise(r=>setTimeout(r,500));
   await click(`[data-action="show-${page}"]`);await until(()=>evaluate(`!document.body.innerText.includes(${js(loadingText)})`),'cross area return fresh read');
   check(`${page} CHAT return starts fresh read`,(await current()).pane===page&&!(await current()).error.includes('SYNTHETIC_CROSS_AREA_UTILITY_READ'),await current());
  }
  await click(`[data-action="show-${area==='CHAT'?'chat':'work'}"]`);await until(()=>evaluate('Boolean(document.querySelector("[data-action=open-conversation-row-menu]"))'),'sidebar');
  const saved=record.areas[area];await click(`.chat-history-row[data-id="${saved.conversationId}"] [data-action="open-conversation-row-menu"]`);await click('[data-action="context-menu-unread"]');
  await until(()=>evaluate(`globalThis.__nanfengDesktopWorkState.manualUnreadAtMs.has(${js(saved.conversationId)})`),'manual mark');
  const projection=await invoke(area,'read_desktop_conversation_read_state',{workspaceId:saved.workspaceId,observedConversationId:null});check(`${area} manual unread native persistence`,projection.conversations.some(c=>c.conversationId===saved.conversationId&&c.manualUnreadAtMs>0),projection);
  check(`${area} automatic unread disabled`,await evaluate('globalThis.__nanfengDesktopWorkState.productSettings.unreadIndicators===false'),null);
  check(`${area} manual dot visible`,await evaluate(`Boolean(document.querySelector('.chat-history-row[data-id="${saved.conversationId}"] .chat-history-unread'))`),null);
  check(`${area} full exchange unchanged`,hash((await invoke(area,'read_desktop_workspace',{workspaceId:saved.workspaceId})).exchange)===saved.exchangeHash,null);
  saved.draft=await invoke(area,'read_desktop_conversation_draft',{workspaceId:saved.workspaceId,conversationId:saved.conversationId});
 }
 record.routeCoverage={CHAT:['reminders','transcription'],WORK:'No utility entry for this empty reminder root; manual unread only. Cross-area delayed CHAT reads verified.'};
}else{
 for(const area of ['CHAT','WORK']){const saved=record.areas[area];const w=await invoke(area,'read_desktop_workspace',{workspaceId:saved.workspaceId});check(`${area} cold full exchange unchanged`,hash(w.exchange)===saved.exchangeHash,null);const settings=await invoke(area,'read_desktop_app_settings');check(`${area} cold automatic disabled`,settings.product.unreadIndicators===false,null);const projection=await invoke(area,'read_desktop_conversation_read_state',{workspaceId:saved.workspaceId,observedConversationId:null});check(`${area} cold manual mark persisted`,projection.conversations.some(c=>c.conversationId===saved.conversationId&&c.manualUnreadAtMs>0),projection);check(`${area} cold full draft unchanged`,js(await invoke(area,'read_desktop_conversation_draft',{workspaceId:saved.workspaceId,conversationId:saved.conversationId}))===js(saved.draft),null);await click(`[data-action="show-${area==='CHAT'?'chat':'work'}"]`);await until(async()=>(await current()).area===area,'cold area');await until(()=>evaluate(`Boolean(document.querySelector('.chat-history-row[data-id="${saved.conversationId}"] .chat-history-unread'))`),'cold visible manual mark');check(`${area} cold manual dot visible`,true,null);}
}
}catch(error){record.error=String(error);process.exitCode=1;}finally{await evaluate('(()=>{if(window.__utilityOriginal)window.__TAURI__.core.invoke=window.__utilityOriginal;window.__utilityGate?.resolve?.();return true})()').catch(()=>{});await writeFile(output,js(record,null,2));await request('Debugger.disable').catch(()=>{});socket.close();}
