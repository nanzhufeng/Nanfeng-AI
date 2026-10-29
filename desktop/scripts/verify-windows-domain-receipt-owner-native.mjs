import assert from 'node:assert/strict';
// Synthetic diagnostic roots only; delay delivery after actual Rust completion.
import { readFile, writeFile } from 'node:fs/promises';
import { createHash } from 'node:crypto';

const [port, mode, output] = process.argv.slice(2);
assert.ok(['run', 'cold'].includes(mode));
let tabs;
for (let attempt = 0; attempt < 100; attempt++) {
  try { tabs = await fetch(`http://127.0.0.1:${Number(port)}/json/list`).then(r => r.json()); if (tabs.some(t => t.type === 'page' && t.url === 'http://tauri.localhost/')) break; } catch {}
  await new Promise(r => setTimeout(r, 100));
}
const tab = tabs?.find(t => t.type === 'page' && t.url === 'http://tauri.localhost/');
assert.ok(tab, 'WebView2 startup');
const socket = new WebSocket(tab.webSocketDebuggerUrl);
const pending = new Map(); let serial = 0;
socket.addEventListener('message', event => { const m = JSON.parse(event.data), p = pending.get(m.id); if (!p) return; pending.delete(m.id); clearTimeout(p.timer); m.error ? p.reject(new Error(m.error.message)) : p.resolve(m.result); });
await new Promise((resolve, reject) => { socket.addEventListener('open', resolve, { once: true }); socket.addEventListener('error', reject, { once: true }); });
const request = (method, params = {}) => new Promise((resolve, reject) => { const id = ++serial, timer = setTimeout(() => { pending.delete(id); reject(new Error(method + ' timeout')); }, 10000); pending.set(id, { resolve, reject, timer }); socket.send(JSON.stringify({ id, method, params })); });
const js = JSON.stringify;
const evaluate = async expression => { const r = await request('Runtime.evaluate', { expression, awaitPromise: true, returnByValue: true }); if (r.exceptionDetails) throw new Error(js(r.exceptionDetails)); return r.result.value; };
const invoke = (area, command, args = {}) => evaluate(`window.__TAURI__.core.invoke(${js(command)},${js(args)},{headers:{'x-nanfeng-data-area':${js(area)}}})`);
const until = async (test, name) => { const end = Date.now() + 15000; while (Date.now() < end) { if (await test()) return; await new Promise(r => setTimeout(r, 100)); } throw new Error(name); };
const canonical=value=>Array.isArray(value)?value.map(canonical):value&&typeof value==='object'?Object.fromEntries(Object.keys(value).sort().map(k=>[k,canonical(value[k])])):value;
const hash = value => createHash('sha256').update(js(canonical(value))).digest('hex');
const pointer = async selector => { const p = await evaluate(`(()=>{const e=document.querySelector(${js(selector)});if(!e)throw Error('missing '+${js(selector)});e.scrollIntoView({block:'center'});const r=e.getBoundingClientRect();return {x:r.x+r.width/2,y:r.y+r.height/2}})()`); await request('Input.dispatchMouseEvent', { type: 'mousePressed', ...p, button: 'left', clickCount: 1 }); await request('Input.dispatchMouseEvent', { type: 'mouseReleased', ...p, button: 'left', clickCount: 1 }); await new Promise(r => setTimeout(r, 150)); };


const record=mode==='run'?{checks:[],areas:{},layer:'diagnostic native application functions for cross-area mutate/undo/redo; actual UI same-area edit; delayed delivery after real Rust receipt',realService:false}:JSON.parse(await readFile(output,'utf8'));
const S='globalThis.__nanfengDesktopWorkState';
const check=(name,pass,detail)=>{record.checks.push({name,pass,detail});assert.ok(pass,name+': '+js(detail));};
const click=s=>evaluate(`(()=>{const e=document.querySelector(${js(s)});if(!e||e.disabled)throw Error('missing/disabled '+${js(s)});e.click();return true})()`);
const select=async area=>{await until(()=>evaluate('Boolean(document.querySelector("[data-action=show-chat],[data-action=return-workspace-origin]"))'),'area navigation ready');if(area==='CHAT'&&!await evaluate('Boolean(document.querySelector("[data-action=show-chat]"))'))await click('[data-action=return-workspace-origin]');else await click(`[data-action=show-${area==='CHAT'?'chat':'work'}]`);await until(()=>evaluate(`${S}.dataArea===${js(area)}&&${S}.current?.summary?.id===${js(record.areas[area].workspaceId)}`),'selected '+area);};const edit=async title=>{await until(()=>evaluate('Boolean(document.querySelector("[data-action=show-projects]"))'),'project nav');await click('[data-action=show-projects]');await until(()=>evaluate('Boolean(document.querySelector("[data-action=new-project]"))'),'project canvas');await click('[data-action=new-project]');await evaluate(`document.querySelector('#project-title').value=${js(title)};document.querySelector('#project-title').dispatchEvent(new Event('input',{bubbles:true}));true`);};
const read=area=>invoke(area,'read_desktop_workspace',{workspaceId:record.areas[area].workspaceId});
try{
await until(()=>evaluate(`Boolean(${S}?.runtimeInfo?.dataAreasReady)`),'ready');record.runtime=await invoke('CHAT','read_desktop_runtime_info');assert.equal(record.runtime.startupMode,'UI_SCHEMA_DIAGNOSTIC_ACCEPTANCE');assert.equal(record.runtime.automaticWorkSuppressed,true);delete record.error;
if(mode==='run'){
for(const area of ['CHAT','WORK']){const w=await invoke(area,'ensure_desktop_local_workspace');record.areas[area]={workspaceId:w.summary.id};}
await request('Debugger.enable');const source=await readFile(new URL('../src/app.mjs',import.meta.url),'utf8');record.sourceSha256=createHash('sha256').update(source).digest('hex');
const bp=await request('Debugger.setBreakpointByUrl',{urlRegex:'/app\\.mjs$',lineNumber:source.slice(0,source.indexOf('const tauriBridge =')).split('\n').length-1});
const paused=async event=>{const m=JSON.parse(event.data);if(m.method!=='Debugger.paused')return;try{await request('Debugger.evaluateOnCallFrame',{callFrameId:m.params.callFrames[0].callFrameId,expression:'window.__TAURI__.core=Object.assign({},window.__TAURI__.core,{invoke:window.__TAURI__.core.invoke});window.__domainBridgeInstalled=true;window.__domainMutate=mutate;window.__domainUndo=undo'});}finally{await request('Debugger.resume');}};
socket.addEventListener('message',paused);await request('Page.reload');await until(()=>evaluate(`window.__domainBridgeInstalled&&${S}?.runtimeInfo?.dataAreasReady`),'bridge');await request('Debugger.removeBreakpoint',{breakpointId:bp.breakpointId});socket.removeEventListener('message',paused);await request('Debugger.disable');
await evaluate(`(()=>{const core=window.__TAURI__.core;window.__domainOriginal=core.invoke;window.__domainGates=[];core.invoke=async function(command,args,options){if(command!==window.__domainGateCommand)return window.__domainOriginal.call(this,command,args,options);const gate={command,args,area:options.headers['x-nanfeng-data-area'],owner:${S}};window.__domainGates.push(gate);gate.result=await window.__domainOriginal.call(this,command,args,options);gate.readDone=true;await new Promise((resolve,reject)=>Object.assign(gate,{resolve,reject}));gate.delivered=true;return gate.result;};return true})()`);
await select('WORK');const baseline=hash((await read('WORK')).exchange);
for(const reject of [false,true])for(const command of ['mutate_desktop_domain','undo_desktop_domain','redo_desktop_domain']){
await select('CHAT');await evaluate(`window.__domainGateCommand=${js(command)};window.__domainGates=[];true`);
if(command==='mutate_desktop_domain')await evaluate(`void window.__domainMutate({entity:'project',action:'create',fields:{title:'Synthetic domain ${reject}',description:'synthetic only'}},'synthetic saved');true`);else await evaluate(`void window.__domainUndo(${command==='redo_desktop_domain'});true`);await until(()=>evaluate('window.__domainGates.some(g=>g.readDone)'),'real domain receipt');check(`${command} ${reject?'reject':'success'} actual Rust committed`,await evaluate('Boolean(window.__domainGates[0].result)'),await evaluate('({area:window.__domainGates[0].area,args:window.__domainGates[0].args,result:window.__domainGates[0].result})'));
await select('WORK');await edit('Synthetic newer WORK editor');const before=await evaluate(`window.__newDomainDialog=${S}.dialog;({error:${S}.error,status:${S}.status,hash:JSON.stringify(${S}.current.exchange)})`);
await evaluate(`window.__domainGates[0].${reject?"reject(new Error('SYNTHETIC_AFTER_REAL_RECEIPT'))":"resolve()"};true`);await new Promise(r=>setTimeout(r,250));
const after=await evaluate(`({error:${S}.error,status:${S}.status,hash:JSON.stringify(${S}.current.exchange),sameDialog:${S}.dialog===window.__newDomainDialog,title:document.querySelector('#project-title')?.value})`);
check(`${command} ${reject?'reject':'success'} preserves new route`,after.error===before.error&&after.status===before.status&&after.hash===before.hash&&after.sameDialog&&after.title==='Synthetic newer WORK editor',after);check(`${command} WORK persistent untouched`,hash((await read('WORK')).exchange)===baseline);await click('[data-action=close-dialog]');await evaluate('window.__domainGateCommand=null;true');
}
await select('WORK');await evaluate("window.__domainGateCommand='mutate_desktop_domain';window.__domainGates=[];true");await edit('Synthetic saved CHAT editor');await pointer('[data-action=save-project]');await until(()=>evaluate('window.__domainGates[0]?.readDone'),'same area receipt');await click('[data-action=close-dialog]');await edit('Synthetic newer CHAT editor');await evaluate(`window.__newDomainDialog=${S}.dialog;window.__domainGates[0].resolve();true`);await until(()=>evaluate('window.__domainGates[0].delivered'),'save delivery');await new Promise(r=>setTimeout(r,250));check('old successful save retains newer same-area editor',await evaluate(`${S}.dialog===window.__newDomainDialog&&document.querySelector('#project-title')?.value==='Synthetic newer CHAT editor'`));await click('[data-action=close-dialog]');await evaluate('window.__domainGateCommand=null;window.__TAURI__.core.invoke=window.__domainOriginal;true');
for(const area of ['CHAT','WORK'])record.areas[area].rawHash=hash((await read(area)).exchange);
}else{for(const area of ['CHAT','WORK']){await select(area);check(`cold ${area} complete exchange`,hash((await read(area)).exchange)===record.areas[area].rawHash);}}
}catch(error){record.error=String(error);process.exitCode=1;}finally{await writeFile(output,js(record,null,2));socket.close();}