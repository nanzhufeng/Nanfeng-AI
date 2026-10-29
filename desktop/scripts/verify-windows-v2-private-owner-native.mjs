import { execFileSync } from 'node:child_process';
import { join } from 'node:path';
import { canonicalJson, semanticHash, sha256, validateExchangeV2 } from '../../protocol/scripts/exchange-v2-lib.mjs';
import assert from 'node:assert/strict';
// Synthetic diagnostic roots only; delay delivery after actual Rust completion.
import { readFile, writeFile } from 'node:fs/promises';
import { createHash } from 'node:crypto';

const [port, mode, output, root] = process.argv.slice(2);
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
function createPackage(path, exchange) {
  const asset = Buffer.from('a');
  const entries = { 'exchange.json': Buffer.from(canonicalJson(exchange)), [`assets/${sha256(asset)}`]: asset };
  const manifest = { format: 'nfai.exchange.package', packageVersion: 2, exchangeVersion: 2,
    export: exchange.export, files: Object.entries(entries).map(([entry, bytes]) => ({ path: entry, byteCount: bytes.length, sha256: sha256(bytes) })) };
  const payload = { manifest: canonicalJson(manifest), entries: Object.fromEntries(Object.entries(entries).map(([name, bytes]) => [name, bytes.toString('base64')])) };
  const python = 'import base64,json,sys,zipfile\np=json.load(sys.stdin)\nwith zipfile.ZipFile(sys.argv[1],"w",compression=zipfile.ZIP_DEFLATED) as z:\n z.writestr("manifest.json",p["manifest"])\n for name,data in p["entries"].items(): z.writestr(name,base64.b64decode(data))';
  execFileSync('python', ['-c', python, path], { input: JSON.stringify(payload), encoding: 'utf8', timeout: 30000 });
}
function inspectPackage(path) {
  const python = 'import base64,json,sys,zipfile\nwith zipfile.ZipFile(sys.argv[1]) as z:\n names=z.namelist()\n print(json.dumps({"names":names,"entries":{n:base64.b64encode(z.read(n)).decode("ascii") for n in names}}))';
  const { names, entries } = JSON.parse(execFileSync('python', ['-c', python, path], { encoding: 'utf8', timeout: 30000 }));
  assert.equal(names.length, new Set(names).size);
  const bytes = Object.fromEntries(Object.entries(entries).map(([name, encoded]) => [name, Buffer.from(encoded, 'base64')]));
  const manifest = JSON.parse(bytes['manifest.json'].toString('utf8'));
  const exchange = JSON.parse(bytes['exchange.json'].toString('utf8'));
  assert.equal(manifest.packageVersion, 2);
  assert.equal(manifest.exchangeVersion, 2);
  assert.equal(manifest.files.length + 1, names.length);
  for (const item of manifest.files) {
    assert.equal(bytes[item.path]?.length, item.byteCount);
    assert.equal(sha256(bytes[item.path]), item.sha256);
  }
  assert.equal(semanticHash(exchange), exchange.export.semanticHash);
  validateExchangeV2(exchange);
  return { exchange, bytes };
}

try {
await until(()=>evaluate(`Boolean(${S}?.runtimeInfo?.dataAreasReady)`),'ready');record.runtime=await invoke('CHAT','read_desktop_runtime_info');assert.equal(record.runtime.startupMode,'UI_SCHEMA_DIAGNOSTIC_ACCEPTANCE');assert.equal(record.runtime.automaticWorkSuppressed,true);delete record.error;
if(mode==='run') {
for(const area of ['CHAT','WORK']) {const w=await invoke(area,'ensure_desktop_local_workspace');record.areas[area]={workspaceId:w.summary.id};}
const exchange=JSON.parse(await readFile(new URL('../../protocol/fixtures/nfai.exchange.v2.golden.json',import.meta.url),'utf8'));const assetHash=sha256(Buffer.from('a'));for(const attachment of exchange.knowledge[0].attachments){attachment.entry=`assets/${assetHash}`;attachment.sha256=assetHash;}exchange.export.semanticHash=semanticHash(exchange);validateExchangeV2(exchange);record.input=join(root,'synthetic-v2.nfai-exchange');createPackage(record.input,exchange);record.expected=exchange;
await request('Debugger.enable');const source=await readFile(new URL('../src/app.mjs',import.meta.url),'utf8');record.sourceSha256=sha256(Buffer.from(source));const bp=await request('Debugger.setBreakpointByUrl',{urlRegex:'/app\\.mjs$',lineNumber:source.slice(0,source.indexOf('const tauriBridge =')).split('\n').length-1});
const paused=async event=>{const m=JSON.parse(event.data);if(m.method!=='Debugger.paused')return;try{await request('Debugger.evaluateOnCallFrame',{callFrameId:m.params.callFrames[0].callFrameId,expression:'window.__TAURI__.core=Object.assign({},window.__TAURI__.core,{invoke:window.__TAURI__.core.invoke});window.__v2BridgeInstalled=true;window.__v2Pick=pickV2WorkspaceExchange;window.__v2Export=reexportV2WorkspaceExchange'});}finally{await request('Debugger.resume');}};
socket.addEventListener('message',paused);await request('Page.reload');await until(()=>evaluate(`window.__v2BridgeInstalled&&${S}?.runtimeInfo?.dataAreasReady`),'bridge');await request('Debugger.removeBreakpoint',{breakpointId:bp.breakpointId});socket.removeEventListener('message',paused);await request('Debugger.disable');
await evaluate(`(()=>{const core=window.__TAURI__.core;window.__v2Original=core.invoke;window.__v2Calls=[];core.invoke=async function(command,args,options){window.__v2Calls.push({command,args,area:options?.headers?.['x-nanfeng-data-area']});if(command.startsWith('plugin:dialog|')){window.__v2Picker={command,args};if(window.__v2DelayPicker)return new Promise(resolve=>window.__v2Picker.resolve=resolve);return window.__v2Path;}const result=await window.__v2Original.call(this,command,args,options);if(command===window.__v2GateCommand){window.__v2Gate={command,args,area:options.headers['x-nanfeng-data-area'],result};await new Promise((resolve,reject)=>Object.assign(window.__v2Gate,{resolve,reject}));}return result;};return true})()`);
const baseline=hash((await read('WORK')).exchange);const snapshot=()=>evaluate(`({error:${S}.error,status:${S}.status,exchange:JSON.stringify(${S}.current.exchange),committed:JSON.stringify(${S}.v2CommittedExchanges),global:JSON.stringify(globalThis.__nanfengV2CommittedExchanges)})`);
for(const kind of ['import','export']) {
await select('CHAT');await evaluate(`window.__v2DelayPicker=true;window.__v2Picker=null;window.__v2Calls=[];void ${kind==='import'?'window.__v2Pick()':"window.__v2Export('synthetic-not-used')"};true`);await until(()=>evaluate('Boolean(window.__v2Picker?.resolve)'),'pending picker');await select('WORK');const before=await snapshot();await evaluate(`window.__v2Picker.resolve(${js(record.input)});true`);await new Promise(r=>setTimeout(r,200));check(`${kind} stale picker no business IPC`,await evaluate(`!window.__v2Calls.some(c=>c.command===${js(kind==='import'?'import_desktop_workspace_exchange_v2_selected':'reexport_desktop_workspace_exchange_v2_selected')})`));check(`${kind} stale picker preserves WORK`,hash(await snapshot())===hash(before));}
for(const kind of ['import','export'])for(const reject of [false,true]) {
await select('CHAT');const command=kind==='import'?'import_desktop_workspace_exchange_v2_selected':'reexport_desktop_workspace_exchange_v2_selected';const path=kind==='import'?record.input:join(root,`synthetic-v2-${reject?'rejected-delivery':'success'}.nfai-exchange`);
await evaluate(`window.__v2DelayPicker=false;window.__v2Path=${js(path)};window.__v2GateCommand=${js(command)};window.__v2Gate=null;void ${kind==='import'?'window.__v2Pick()':`window.__v2Export(${js(record.importedId)})`};true`);await until(()=>evaluate('Boolean(window.__v2Gate?.resolve)'),'actual Rust receipt');const gate=await evaluate('({command:window.__v2Gate.command,area:window.__v2Gate.area,result:window.__v2Gate.result})');check(`${kind} ${reject} real Rust bound CHAT`,gate.area==='CHAT'&&gate.result.semanticHash===exchange.export.semanticHash,gate);if(kind==='import')record.importedId=gate.result.workspaceId;else{const exported=inspectPackage(path);check(`${kind} ${reject} all IR fields and asset`,hash(exported.exchange)===hash(exchange)&&sha256(exported.bytes[`assets/${assetHash}`])===assetHash);}
await select('WORK');const before=await snapshot();await evaluate(`window.__v2Gate.${reject?"reject(new Error('SYNTHETIC_AFTER_REAL_RECEIPT'))":"resolve()"};true`);await new Promise(r=>setTimeout(r,250));check(`${kind} ${reject} late receipt preserves WORK`,hash(await snapshot())===hash(before));check(`${kind} ${reject} WORK complete exchange`,hash((await read('WORK')).exchange)===baseline);check(`${kind} ${reject} WORK private v2 empty`,(await invoke('WORK','list_desktop_workspace_exchange_v2_committed')).length===0);
}
await evaluate('window.__v2GateCommand=null;true');await select('CHAT');await evaluate(`window.__v2Path=${js(record.input)};window.__v2Done=false;void window.__v2Pick().then(()=>window.__v2Done=true);true`);await until(()=>evaluate('window.__v2Done'),'same-area full import');check('same-area owner import readback',await evaluate(`${S}.v2CommittedExchanges.some(x=>x.workspaceId===${js(record.importedId)})&&${S}.error===''`));
for(const area of ['CHAT','WORK']){record.areas[area].rawHash=hash((await read(area)).exchange);record.areas[area].privateRecords=await invoke(area,'list_desktop_workspace_exchange_v2_committed');}record.scope='Real private application functions; controlled dialog bridge and late real Rust delivery; not system picker or visible entry';
} else {
for(const area of ['CHAT','WORK']){await select(area);check(`cold ${area} complete exchange`,hash((await read(area)).exchange)===record.areas[area].rawHash);check(`cold ${area} private v2`,hash(await invoke(area,'list_desktop_workspace_exchange_v2_committed'))===hash(record.areas[area].privateRecords));}const path=join(root,'synthetic-v2-cold.nfai-exchange');await invoke('CHAT','reexport_desktop_workspace_exchange_v2_selected',{workspaceId:record.importedId,selectedPath:path});check('cold all IR fields reexport',hash(inspectPackage(path).exchange)===hash(record.expected));
}
}catch(error){record.error=String(error);process.exitCode=1;}finally{await writeFile(output,js(record,null,2));socket.close();}
