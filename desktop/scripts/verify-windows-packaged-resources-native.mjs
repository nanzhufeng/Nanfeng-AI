import assert from 'node:assert/strict';
import { readFile, writeFile, readdir } from 'node:fs/promises';
import { createHash } from 'node:crypto';
import { fileURLToPath } from 'node:url';

const [port, output] = process.argv.slice(2);

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

const record={layer:'isolated diagnostic packaged resources',resources:[],pages:[],exceptions:[],networkFailures:[]};
socket.addEventListener('message',event=>{const m=JSON.parse(event.data);if(m.method==='Runtime.exceptionThrown')record.exceptions.push(m.params.exceptionDetails.text);if(m.method==='Network.loadingFailed')record.networkFailures.push({error:m.params.errorText,type:m.params.type,canceled:m.params.canceled});});
try {
await until(()=>evaluate('Boolean(globalThis.__nanfengDesktopWorkState?.runtimeInfo?.dataAreasReady)'),'initial ready before reload');await request('Runtime.enable');await request('Network.enable');await request('Page.reload');
await until(()=>evaluate('Boolean(globalThis.__nanfengDesktopWorkState?.runtimeInfo?.dataAreasReady)'),'runtime ready');
record.runtime=await invoke('CHAT','read_desktop_runtime_info');assert.equal(record.runtime.startupMode,'UI_SCHEMA_DIAGNOSTIC_ACCEPTANCE');assert.equal(record.runtime.automaticWorkSuppressed,true);
for(const name of (await readdir(new URL('../dist/',import.meta.url))).filter(n=>/\.(mjs|css|html)$/.test(n)).sort()){
const resource=await evaluate(`(async()=>{const r=await fetch('/'+${js(name)});const b=new Uint8Array(await r.arrayBuffer());let s='';for(const c of b)s+=String.fromCharCode(c);return {status:r.status,type:r.headers.get('content-type'),base64:btoa(s)}})()`);
const actual=createHash('sha256').update(Buffer.from(resource.base64,'base64')).digest('hex');const expected=createHash('sha256').update(await readFile(new URL('../dist/'+name,import.meta.url))).digest('hex');
record.resources.push({name,status:resource.status,type:resource.type,sha256:actual,distSha256:expected});assert.equal(resource.status,200,name);if(name!=='index.html')assert.equal(actual,expected,name+' packaged bytes');else record.htmlRuntimeInjection=true;
}
for(const area of ['CHAT','WORK']){
await evaluate(`document.querySelector('[data-action="show-${area==='CHAT'?'chat':'work'}"]').click()`);await until(()=>evaluate(`globalThis.__nanfengDesktopWorkState.dataArea===${js(area)}`),'area');await new Promise(r=>setTimeout(r,500));
await evaluate('document.querySelector("[data-action=show-settings]").click()');await new Promise(r=>setTimeout(r,300));
const pages=await evaluate('[...document.querySelectorAll("[data-action=open-settings-page]")].map(e=>e.dataset.page)');
const queue=pages.map(page=>({page,parent:null}));const seen=new Set(pages);for(const entry of queue){const {page,parent}=entry;if(parent){await evaluate(`document.querySelector('[data-action="open-settings-page"][data-page="${parent}"]').click()`);await new Promise(r=>setTimeout(r,350));}await evaluate(`document.querySelector('[data-action="open-settings-page"][data-page="${page}"]').click()`);await new Promise(r=>setTimeout(r,500));const view=await evaluate('({section:globalThis.__nanfengDesktopWorkState.settingsSection,error:globalThis.__nanfengDesktopWorkState.error,text:document.querySelector(".android-settings-page")?.innerText||"",github:document.querySelector("[data-action=open-source-link]")?.href})');record.pages.push({area,page,parent,...view});const children=await evaluate('[...document.querySelectorAll("[data-action=open-settings-page]")].map(e=>e.dataset.page)');for(const child of children)if(!seen.has(child)){seen.add(child);queue.push({page:child,parent:page});}assert.equal(view.section,page);assert.equal(view.error,'',area+' '+page);assert.ok(view.text.length,area+' '+page+' content');if(page==='about'){for(const label of ['产品信息','版本信息','开发者信息',record.runtime.version,record.runtime.gitCommit.slice(0,8),'含未提交改动'])assert.ok(view.text.includes(label),label);assert.equal(view.github,'https://github.com/nanzhufeng/Nanfeng-AI');}}
await evaluate('document.querySelector("[data-action=show-chat]").click()');await new Promise(r=>setTimeout(r,250));
}
assert.equal(record.exceptions.length,0);assert.equal(record.networkFailures.filter(x=>!x.canceled).length,0);
}catch(error){record.error=String(error);process.exitCode=1;}finally{await writeFile(output,js(record,null,2));socket.close();}
