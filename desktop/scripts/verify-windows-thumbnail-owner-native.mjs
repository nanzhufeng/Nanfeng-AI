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


import { deflateSync } from 'node:zlib';
function syntheticPng(rgb) {
  const crc32=bytes=>{let crc=0xffffffff;for(const byte of bytes){crc^=byte;for(let i=0;i<8;i++)crc=(crc>>>1)^((crc&1)?0xedb88320:0);}return (crc^0xffffffff)>>>0;};
  const chunk=(type,data)=>{const name=Buffer.from(type),body=Buffer.concat([name,data]),length=Buffer.alloc(4),crc=Buffer.alloc(4);length.writeUInt32BE(data.length);crc.writeUInt32BE(crc32(body));return Buffer.concat([length,body,crc]);};
  const header=Buffer.alloc(13);header.writeUInt32BE(8,0);header.writeUInt32BE(8,4);header[8]=8;header[9]=2;
  const pixels=Buffer.concat(Array.from({length:8},()=>Buffer.from([0,...Array.from({length:8},()=>rgb).flat()])));
  return Buffer.concat([Buffer.from('89504e470d0a1a0a','hex'),chunk('IHDR',header),chunk('IDAT',deflateSync(pixels)),chunk('IEND',Buffer.alloc(0))]);
}

const record=mode==='prepare'?{checks:[],areas:{},layer:'diagnostic native; bridge delay after real Rust receipt',productionStream:false}:JSON.parse(await readFile(output,'utf8'));
const check=(name,pass,detail)=>{record.checks.push({name,pass,detail});assert.ok(pass,name+': '+js(detail));};
const click=s=>evaluate(`(()=>{const e=document.querySelector(${js(s)});if(!e)throw Error('missing '+${js(s)});e.click();return true})()`);
const select=async area=>{await click(`[data-action=show-${area==='CHAT'?'chat':'work'}]`);await until(()=>evaluate(`globalThis.__nanfengDesktopWorkState.dataArea===${js(area)}`),'area');const a=record.areas[area];await until(()=>evaluate(`Boolean(document.querySelector('[data-action=select-chat][data-id="${a.conversationId}"]'))`),'list');await click(`[data-action=select-chat][data-id="${a.conversationId}"]`);await until(()=>evaluate('Boolean(document.querySelector("[data-image-thumbnail]"))'),'image card');await evaluate('document.querySelector("[data-image-thumbnail]").scrollIntoView({block:"center"});true');};
try{
await until(()=>evaluate('Boolean(globalThis.__nanfengDesktopWorkState?.runtimeInfo?.dataAreasReady)'),'ready');
record.runtime=await invoke('CHAT','read_desktop_runtime_info');assert.equal(record.runtime.startupMode,'UI_SCHEMA_DIAGNOSTIC_ACCEPTANCE');assert.equal(record.runtime.automaticWorkSuppressed,true);
delete record.error;
if(mode==='prepare'){
const {packageExchange,semanticHash,sha256,preflightPackage}=await import('../../protocol/scripts/exchange-lib.mjs');
for(const [area,rgb] of [['CHAT',[40,160,80]],['WORK',[240,130,40]]]){
const exchange=JSON.parse(await readFile(new URL('../../protocol/fixtures/nfai.exchange.v1.golden.json',import.meta.url),'utf8'));const asset=syntheticPng(rgb),assetHash=sha256(asset);
const blocks=exchange.conversations[0].messages[1].blocks;Object.assign(blocks[0].asset,{entry:`assets/${assetHash}`,mimeType:'image/png',displayName:`synthetic-${area}.png`,byteCount:asset.length,sha256:assetHash});
exchange.export.semanticHash=semanticHash(exchange);const packaged=packageExchange(exchange,new Map([[`assets/${assetHash}`,asset]]));preflightPackage(packaged);const fixture=output+'.'+area+'.nfai-exchange';await writeFile(fixture,packaged);
const stage=await invoke(area,'stage_preflight_selected_exchange',{selectedPath:fixture});const imported=await invoke(area,'import_staged_exchange_as_new_workspace',{args:{stagingId:stage.stagingId,workspaceTitle:`Synthetic ${area} thumbnail owner`}});record.areas[area]={workspaceId:imported.summary.id,conversationId:imported.exchange.conversations[0].id,attachmentId:blocks[0].asset.id,fixture,rawHash:hash(imported.exchange)};
}
}else if(mode==='run'){
await request('Debugger.enable');const source=await readFile(new URL('../src/app.mjs',import.meta.url),'utf8');record.sourceSha256=createHash('sha256').update(source).digest('hex');
const breakpoint=await request('Debugger.setBreakpointByUrl',{urlRegex:'/app\.mjs$',lineNumber:source.slice(0,source.indexOf('const tauriBridge =')).split('\n').length-1});
const paused=async event=>{const m=JSON.parse(event.data);if(m.method!=='Debugger.paused')return;try{await request('Debugger.evaluateOnCallFrame',{callFrameId:m.params.callFrames[0].callFrameId,expression:'window.__TAURI__.core=Object.assign({},window.__TAURI__.core,{invoke:window.__TAURI__.core.invoke});window.__thumbnailBridgeInstalled=true'});}finally{await request('Debugger.resume');}};socket.addEventListener('message',paused);await request('Page.reload');await until(()=>evaluate('window.__thumbnailBridgeInstalled && globalThis.__nanfengDesktopWorkState?.runtimeInfo?.dataAreasReady'),'bridge ready');await request('Debugger.removeBreakpoint',{breakpointId:breakpoint.breakpointId});socket.removeEventListener('message',paused);await request('Debugger.disable');
await evaluate(`(()=>{const core=window.__TAURI__.core;window.__thumbnailOriginal=core.invoke;window.__thumbnailGates=[];core.invoke=async function(command,args,options){if(command!=='read_desktop_image_preview')return window.__thumbnailOriginal.call(this,command,args,options);const gate={area:options.headers['x-nanfeng-data-area'],args,owner:globalThis.__nanfengDesktopWorkState};window.__thumbnailGates.push(gate);try{gate.result=await window.__thumbnailOriginal.call(this,command,args,options);gate.readDone=true;await new Promise((resolve,reject)=>Object.assign(gate,{resolve,reject}));return gate.result;}catch(error){gate.error=String(error);gate.readDone=true;throw error;}};return true})()`);
for(const fail of [process.argv.includes("--reject")]){
await evaluate('window.__thumbnailGates=[];globalThis.__nanfengDesktopWorkState.imageThumbnails={};true');
await select('CHAT');await until(()=>evaluate('window.__thumbnailGates.some(g=>g.area==="CHAT"&&g.readDone)'),'CHAT real read');
await select('WORK');await until(()=>evaluate('window.__thumbnailGates.some(g=>g.area==="WORK"&&g.readDone)'),'WORK real read');
const snap=()=>evaluate(`(()=>{const a=window.__thumbnailGates.find(g=>g.area==='CHAT'),b=window.__thumbnailGates.find(g=>g.area==='WORK'),key=JSON.stringify([b.args.args.workspaceId,b.args.args.attachmentId]);return {aPending:a.owner.imageThumbnailPending.size,bPending:b.owner.imageThumbnailPending.has(key),bCache:!!b.owner.imageThumbnails[key],aResult:!!a.result?.dataUrl,bResult:!!b.result?.dataUrl,sameKey:a.args.args.workspaceId===b.args.args.workspaceId&&a.args.args.attachmentId===b.args.args.attachmentId}})()`);
let s=await snap();check(`${fail?'reject':'success'} real dual-area Rust receipts`,s.aResult&&s.bResult,s);check('same ID collision',s.sameKey,s);
await evaluate(`(()=>{const g=window.__thumbnailGates.find(g=>g.area==='CHAT');${fail?"g.reject(new Error('SYNTHETIC_AFTER_REAL_RECEIPT'))":"g.resolve()"};return true})()`);await until(async()=>!(await snap()).aPending,'old pending drain');s=await snap();check(`${fail?'reject':'success'} old receipt preserves new pending/cache`,s.bPending&&!s.bCache,s);
await evaluate('window.__thumbnailGates.find(g=>g.area==="WORK").resolve();true');await until(async()=>!(await snap()).bPending,'new pending drain');await until(()=>evaluate('Boolean(document.querySelector("[data-image-thumbnail] img")?.naturalWidth)'),'new image decode');s=await snap();check(`${fail?'reject':'success'} new receipt populates own cache`,s.bCache,s);

}
await evaluate('window.__TAURI__.core.invoke=window.__thumbnailOriginal;true');
}else{
for(const area of ['CHAT','WORK']){await select(area);await until(()=>evaluate('Boolean(document.querySelector("[data-image-thumbnail] img")?.naturalWidth)'),'cold decode');const pixel=await evaluate('(()=>{const i=document.querySelector("[data-image-thumbnail] img"),c=document.createElement("canvas");c.width=c.height=1;const x=c.getContext("2d");x.drawImage(i,0,0,1,1);return [...x.getImageData(0,0,1,1).data]})()');check(`cold ${area} private image decoded`,true);check(`cold ${area} own pixels`,js(pixel)===js(area==='CHAT'?[40,160,80,255]:[240,130,40,255]),pixel);}
}
if(mode!=='prepare')for(const area of ['CHAT','WORK']){const a=record.areas[area];const e=(await invoke(area,'read_desktop_workspace',{workspaceId:a.workspaceId})).exchange;check(`${mode} ${area} full exchange preserved`,hash(e)===a.rawHash,{actual:hash(e),expected:a.rawHash});}
}catch(error){record.error=String(error);process.exitCode=1;}finally{await writeFile(output,js(record,null,2));socket.close();}
