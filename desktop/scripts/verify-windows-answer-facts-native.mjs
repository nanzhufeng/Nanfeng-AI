import assert from 'node:assert/strict';
import { readFile, writeFile } from 'node:fs/promises';
import { createHash } from 'node:crypto';

const [port, mode, output] = process.argv.slice(2);
assert.ok(['prepare', 'run', 'cold'].includes(mode));
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

const record = mode === 'prepare' ? {areas:{},checks:[],layer:'persisted synthetic answer facts; diagnostic native',productionStream:false} : JSON.parse(await readFile(output,'utf8'));
const check=(name,pass,detail)=>{record.checks.push({name,pass,detail});assert.ok(pass,name+': '+js(detail));};
try {
await until(()=>evaluate('Boolean(globalThis.__nanfengDesktopWorkState?.runtimeInfo?.dataAreasReady)'),'ready');
record.runtime=await invoke('CHAT','read_desktop_runtime_info');assert.equal(record.runtime.startupMode,'UI_SCHEMA_DIAGNOSTIC_ACCEPTANCE');assert.equal(record.runtime.automaticWorkSuppressed,true);
if(mode==='prepare'){
 const {packageExchange,semanticHash,sha256,preflightPackage}=await import('../../protocol/scripts/exchange-lib.mjs');
 const exchange=JSON.parse(await readFile(new URL('../../protocol/fixtures/nfai.exchange.v1.golden.json',import.meta.url),'utf8'));
 const asset=await readFile(new URL('../../protocol/fixtures/assets/golden-note.txt',import.meta.url));
 exchange.export.semanticHash=semanticHash(exchange);const packaged=packageExchange(exchange,new Map([[`assets/${sha256(asset)}`,asset]]));preflightPackage(packaged);
 record.fixture=output+'.nfai-exchange';await writeFile(record.fixture,packaged);
 for(const area of ['CHAT','WORK']){const p=await invoke(area,'stage_preflight_selected_exchange',{selectedPath:record.fixture});const imported=await invoke(area,'import_staged_exchange_as_new_workspace',{args:{stagingId:p.stagingId,workspaceTitle:`Synthetic ${area} answer facts`}});const c=imported.exchange.conversations[0];record.areas[area]={workspaceId:imported.summary.id,conversationId:c.id,userMessageId:'synthetic-user-answer-facts',assistantMessageId:c.messages.find(m=>m.role.toLowerCase()==='assistant').id,exchangeHash:hash(imported.exchange)};}
}else{
const cases=[['missing','无法确认（旧记录）'],['legacy-false','无法确认（旧记录）'],['disabled','本次未使用'],['requested','联网未完成'],['verified','已实际使用']];
for(const area of ['CHAT','WORK']){
const saved=record.areas[area];const initialExchange=(await invoke(area,'read_desktop_workspace',{workspaceId:saved.workspaceId})).exchange;if(mode==='run')saved.projectedExchangeHash=hash(initialExchange);else check(`cold ${area} complete projected exchange`,hash(initialExchange)===saved.projectedExchangeHash);await pointer(`[data-action=show-${area==='CHAT'?'chat':'work'}]`);await until(()=>evaluate(`globalThis.__nanfengDesktopWorkState.dataArea===${js(area)}`),'area');await until(()=>evaluate(`Boolean(document.querySelector('[data-action="select-chat"][data-id="${saved.conversationId}"]'))`),'conversation list');await pointer(`[data-action="select-chat"][data-id="${saved.conversationId}"]`);
for(const [name,label] of cases){
const id=saved.caseMessageIds[name];const result=await invoke(area,'read_desktop_ordinary_chat_context_record',{workspaceId:saved.workspaceId,conversationId:saved.conversationId,assistantMessageId:id});
const {answerInformation}=await import('../src/answer-information.mjs');check(`${mode} ${area} ${name} Rust projection`,answerInformation(result).networkLabel===label,result);
await until(()=>evaluate(`Boolean(document.querySelector('[data-action="open-assistant-message-menu"][data-message-id="${id}"]'))`),'message trigger');await pointer(`[data-action="open-assistant-message-menu"][data-message-id="${id}"]`);await pointer('[data-action="show-assistant-answer-information"]');await until(()=>evaluate('Boolean(document.querySelector(".answer-information-dialog"))'),'dialog');const view=await evaluate('document.querySelector(".answer-information-dialog").innerText');check(`${mode} ${area} ${name} visible`,view.includes(label),view);
if(name!=='missing'){for(const title of ['直言不讳','协议资料\n第二行😀','<img src=x onerror=synthetic>'])check(`${mode} ${area} ${name} full source ${title}`,view.includes(title));check(`${mode} ${area} ${name} no source HTML`,await evaluate('document.querySelector(".answer-information-dialog img")===null'));}
await pointer('[data-action="close-dialog"]');
}
const exchange=(await invoke(area,'read_desktop_workspace',{workspaceId:saved.workspaceId})).exchange;await writeFile(output+'.'+area+'.exchange.json',js(exchange,null,2));check(`${mode} ${area} full exchange preserved`,hash(exchange)===saved.projectedExchangeHash,{actual:hash(exchange),expected:saved.projectedExchangeHash});
}
}
}catch(error){record.error=String(error);process.exitCode=1;}finally{await writeFile(output,js(record,null,2));socket.close();}
