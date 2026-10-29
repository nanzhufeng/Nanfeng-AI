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
try {
await until(()=>evaluate('Boolean(document.querySelector("#chat-composer") && globalThis.__nanfengDesktopWorkState?.runtimeInfo?.dataAreasReady)'), 'ready');
const runtime=await invoke('CHAT','read_desktop_runtime_info');assert.equal(runtime.startupMode,'UI_SCHEMA_DIAGNOSTIC_ACCEPTANCE');assert.equal(runtime.automaticWorkSuppressed,true);record.runtime=runtime;delete record.error;record.productionStream=false;await key("Escape","Escape",27);await settle();
if(mode==='run'){
const {packageExchange,semanticHash,sha256,preflightPackage}=await import('../../protocol/scripts/exchange-lib.mjs');
const exchange=JSON.parse(await readFile(new URL('../../protocol/fixtures/nfai.exchange.v1.golden.json',import.meta.url),'utf8'));
const asset=await readFile(new URL('../../protocol/fixtures/assets/golden-note.txt',import.meta.url));const assetHash=sha256(asset);
const longCode=Array.from({length:90},(_,i)=>`const synthetic_${i} = "${'wide_synthetic_'.repeat(22)}";`).join('\n');
const text=Array.from({length:80},(_,i)=>`## Synthetic section ${i}\n\nSynthetic paragraph ${i} ${'Long native reading content. '.repeat(12)}\n`).join('\n')+'\n```javascript\n'+longCode+'\n```\n\nSynthetic final paragraph.';
const blocks=exchange.conversations[0].messages[1].blocks;blocks[0].ordinal=1;blocks.unshift({kind:'TEXT',ordinal:0,text});exchange.export.semanticHash=semanticHash(exchange);const packaged=packageExchange(exchange,new Map([[`assets/${assetHash}`,asset]]));preflightPackage(packaged);
const fixture=output+'.nfai-exchange';await writeFile(fixture,packaged);record.fixture={path:fixture,hash:sha256(packaged),characters:text.length};
for(const area of ['CHAT','WORK']){const preflight=await invoke(area,'stage_preflight_selected_exchange',{selectedPath:fixture});const imported=await invoke(area,'import_staged_exchange_as_new_workspace',{args:{stagingId:preflight.stagingId,workspaceTitle:`Synthetic ${area} long reading`}});record.areas[area]={workspaceId:imported.summary.id,conversationId:imported.exchange.conversations[0].id};}
await request('Page.reload');await until(()=>evaluate('Boolean(document.querySelector("#chat-composer"))'),'reload');
}
for(const area of ['CHAT','WORK']){
const saved=record.areas[area];const workspace=()=>invoke(area,'read_desktop_workspace',{workspaceId:saved.workspaceId});const draft=()=>invoke(area,'read_desktop_conversation_draft',{workspaceId:saved.workspaceId,conversationId:saved.conversationId});
if(mode==='cold'){check(`${area} cold exchange`,hash((await workspace()).exchange)===saved.exchangeHash,null);check(`${area} cold draft`,js(await draft())===js(saved.draft),null);continue;}
await pointer(`[data-action=show-${area==='CHAT'?'chat':'work'}]`);await until(()=>evaluate(`globalThis.__nanfengDesktopWorkState.dataArea===${js(area)}`),'area');await pointer(`[data-action="select-chat"][data-id="${saved.conversationId}"]`);await until(()=>evaluate('Boolean(document.querySelector(".chat-markdown-code"))'),'code');
const before=(await workspace()).exchange;saved.draft=await draft();
const baseline=await evaluate(`(()=>{const scroll=document.querySelector('.chat-scroll[data-conversation-id]'),code=document.querySelector('.chat-markdown-code'),paragraph=document.querySelector('.chat-message.assistant p');scroll.scrollTop=scroll.scrollHeight-scroll.clientHeight-18;code.scrollLeft=180;window.__longNodes={scroll,code,paragraph,disconnects:[]};window.__longObserver=new MutationObserver(records=>{for(const r of records)for(const n of r.removedNodes)if(n===scroll||n.contains?.(scroll)||n===code||n.contains?.(code))window.__longNodes.disconnects.push(n.nodeName)});window.__longObserver.observe(document.getElementById('app'),{childList:true,subtree:true});return {height:scroll.scrollHeight,viewport:scroll.clientHeight,top:scroll.scrollTop,left:code.scrollLeft,width:code.scrollWidth,codeViewport:code.clientWidth}})()`);
check(`${area} long content and horizontal overflow`,baseline.height>baseline.viewport*5&&baseline.width>baseline.codeViewport&&baseline.left===180,baseline);
const point=await evaluate(`(()=>{const r=window.__longNodes.scroll.getBoundingClientRect();return {x:r.x+r.width/2,y:r.y+r.height/2}})()`);await request('Input.dispatchMouseEvent',{type:'mouseWheel',...point,deltaX:0,deltaY:-100});await new Promise(r=>setTimeout(r,700));
const selected=await evaluate(`(()=>{const p=window.__longNodes.paragraph;const text=[...p.childNodes].find(n=>n.nodeType===3);const range=document.createRange();range.setStart(text,0);range.setEnd(text,Math.min(24,text.length));const selection=getSelection();selection.removeAllRanges();selection.addRange(range);return {text:selection.toString(),top:window.__longNodes.scroll.scrollTop,left:window.__longNodes.code.scrollLeft}})()`);check(`${area} selected text created`,selected.text.length>10,selected);
const retained=async label=>{const state=await evaluate(`(()=>{const n=window.__longNodes;return {scrollSame:n.scroll===document.querySelector('.chat-scroll[data-conversation-id]'),codeSame:n.code===document.querySelector('.chat-markdown-code'),connected:n.scroll.isConnected&&n.code.isConnected,disconnects:n.disconnects,top:n.scroll.scrollTop,left:n.code.scrollLeft,selected:getSelection().toString()}})()`);check(`${area} ${label} retained DOM and reading`,state.scrollSame&&state.codeSame&&state.connected&&state.disconnects.length===0&&Math.abs(state.top-selected.top)<=1&&state.left===selected.left&&state.selected===selected.text,state);};
const trigger='.chat-message.assistant [data-action="open-assistant-message-menu"]';
// Keyboard activation preserves the authored selection; moving focus is intentional.
await evaluate(`document.querySelector(${js(trigger)}).focus({preventScroll:true});true`);await key('Enter','Enter',13);await settle();await retained('message menu open');await key('Escape','Escape',27);await settle();await retained('message menu close');
await pointer('[data-action="toggle-conversation-batch-edit"]');await settle();await retained('sidebar batch open');await pointer('[data-action="toggle-conversation-batch-edit"]');await settle();await retained('sidebar batch close');
check(`${area} full exchange unchanged`,hash((await workspace()).exchange)===hash(before),null);check(`${area} full draft unchanged`,js(await draft())===js(saved.draft),null);await evaluate(`document.querySelector('[data-action="open-conversation-rename"][data-id="${saved.conversationId}"]').focus({preventScroll:true});true`);await key('Enter','Enter',13);await until(()=>evaluate('Boolean(document.querySelector("#conversation-rename"))'),'rename');
await request('Input.insertText',{text:`Synthetic ${area} renamed while reading`});await evaluate(`document.querySelector('[data-action="save-conversation-rename"]').focus({preventScroll:true});true`);await key('Enter','Enter',13);await until(()=>evaluate('!document.querySelector("#conversation-rename")'),'rename saved');
const afterRename=await workspace();check(`${area} title owner readback`,afterRename.exchange.conversations.find(c=>c.id===saved.conversationId).title===`Synthetic ${area} renamed while reading`,null);
const renamed=await evaluate(`(()=>{const n=window.__longNodes;return {same:n.scroll===document.querySelector('.chat-scroll[data-conversation-id]')&&n.code===document.querySelector('.chat-markdown-code'),connected:n.scroll.isConnected&&n.code.isConnected,disconnects:n.disconnects,top:n.scroll.scrollTop,left:n.code.scrollLeft}})()`);check(`${area} title update retained reading DOM`,renamed.same&&renamed.connected&&renamed.disconnects.length===0&&Math.abs(renamed.top-selected.top)<=1&&renamed.left===selected.left,renamed);check(`${area} rename full draft unchanged`,js(await draft())===js(saved.draft),null);await evaluate('window.__longObserver.disconnect();true');saved.exchangeHash=hash(afterRename.exchange);
}
}catch(error){record.error=String(error);process.exitCode=1;}finally{await writeFile(output,js(record,null,2));socket.close();}