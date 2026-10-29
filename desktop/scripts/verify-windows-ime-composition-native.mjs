
import assert from 'node:assert/strict';
import {writeFile} from 'node:fs/promises';
import {createHash} from 'node:crypto';
const port=Number(process.argv[2]);const output=process.argv[3];
let tab;
const deadline=Date.now()+30000;
while(Date.now()<deadline){try{const rows=await fetch(`http://127.0.0.1:${port}/json/list`,{signal:AbortSignal.timeout(2000)}).then(r=>r.json());tab=rows.find(t=>t.type==='page'&&t.url==='http://tauri.localhost/');if(tab)break;}catch{}await new Promise(r=>setTimeout(r,200));}
assert.ok(tab,'native WebView2 ready');
const socket=new WebSocket(tab.webSocketDebuggerUrl);let id=0;const pending=new Map();
socket.addEventListener('message',event=>{const m=JSON.parse(event.data);const item=pending.get(m.id);if(!item)return;pending.delete(m.id);clearTimeout(item.timer);if(m.error)item.reject(new Error(m.error.message));else item.resolve(m.result);});
await new Promise((resolve,reject)=>{socket.addEventListener('open',resolve,{once:true});socket.addEventListener('error',reject,{once:true});});
const request=(method,params={})=>new Promise((resolve,reject)=>{const key=++id;const timer=setTimeout(()=>{pending.delete(key);reject(new Error(`${method} timeout`));},10000);pending.set(key,{resolve,reject,timer});socket.send(JSON.stringify({id:key,method,params}));});
const evaluate=async expression=>{const r=await request('Runtime.evaluate',{expression,awaitPromise:true,returnByValue:true});if(r.exceptionDetails)throw new Error(r.exceptionDetails.exception?.description||r.exceptionDetails.text);return r.result?.value;};
const until=async(check,label)=>{const end=Date.now()+15000;while(Date.now()<end){try{const result=await check();if(result)return result;}catch{}await new Promise(r=>setTimeout(r,100));}throw new Error(label);};
const js=x=>JSON.stringify(x);
const invoke=(area,cmd,args={})=>evaluate(`window.__TAURI__.core.invoke(${js(cmd)},${js(args)},{headers:{'x-nanfeng-data-area':${js(area)}}})`);
const hash=x=>createHash('sha256').update(JSON.stringify(x)).digest('hex');
try{
 await until(()=>evaluate('Boolean(window.__TAURI__?.core?.invoke)'), 'IPC ready');
 const workspaces={};for(const area of ['CHAT','WORK'])workspaces[area]=(await invoke(area,'ensure_desktop_local_workspace')).summary.id;
 await evaluate('location.reload();true');
 await until(()=>evaluate("Boolean(globalThis.__nanfengDesktopWorkState?.current&&document.querySelector('#chat-composer'))"),'composer ready');
 const results={};
 for(const area of ['CHAT','WORK']){
  await evaluate(`document.querySelector('[data-action=show-${area==='CHAT'?'chat':'work'}]').click();true`);
  await until(()=>evaluate(`globalThis.__nanfengDesktopWorkState?.dataArea===${js(area)}&&Boolean(document.querySelector('#chat-composer'))`),'area route');
  const before=await invoke(area,'read_desktop_workspace',{workspaceId:workspaces[area]});
  await evaluate(`(()=>{globalThis.__imeEvents=[];globalThis.__imeInput=document.querySelector('#chat-composer');const e=globalThis.__imeInput;e.focus();e.setSelectionRange(0,e.value.length);for(const type of ['compositionstart','compositionupdate','compositionend','input','keydown'])document.addEventListener(type,event=>{if(event.target.id==='chat-composer')globalThis.__imeEvents.push({type,trusted:event.isTrusted,composing:event.isComposing??null,key:event.key??null})});return document.activeElement===e})()`);
  await request('Input.imeSetComposition',{text:'南枫组合候选',selectionStart:6,selectionEnd:6});
  await until(()=>evaluate("globalThis.__imeEvents.some(e=>e.type==='compositionstart'&&e.trusted)"),'trusted composition started');
  const elementStable=await evaluate('globalThis.__imeInput.isConnected&&document.activeElement===globalThis.__imeInput');assert.equal(elementStable,true);
  await request('Input.dispatchKeyEvent',{type:'rawKeyDown',key:'Enter',code:'Enter',windowsVirtualKeyCode:13,nativeVirtualKeyCode:13});
  await request('Input.dispatchKeyEvent',{type:'keyUp',key:'Enter',code:'Enter',windowsVirtualKeyCode:13,nativeVirtualKeyCode:13});
  assert.equal(hash((await invoke(area,'read_desktop_workspace',{workspaceId:workspaces[area]})).exchange),hash(before.exchange),'candidate Enter cannot create message');
  const text=`${area}南枫中文组合确认`;
  await request('Input.insertText',{text});
  await until(()=>evaluate(`document.querySelector('#chat-composer')?.value===${js(text)}`),'complete committed text');
  await until(async()=>{const d=await invoke(area,'read_desktop_conversation_draft',{workspaceId:workspaces[area],conversationId:null});return d?.text===text;},'committed Chinese draft persisted');
  const events=await evaluate('globalThis.__imeEvents');assert.ok(events.some(e=>e.type==='compositionend'&&e.trusted));
  assert.equal(hash((await invoke(area,'read_desktop_workspace',{workspaceId:workspaces[area]})).exchange),hash(before.exchange));
  results[area]={trustedComposition:true,trustedCompositionEnd:true,candidateEnterNoMessage:true,completeDraftPersisted:true,elementStableDuringComposition:true,events,workspaceId:workspaces[area],draftText:text};
 }
 await writeFile(output,JSON.stringify({engineImeSimulation:true,physicalSystemIme:false,results},null,2));console.log(JSON.stringify({passed:true,areas:['CHAT','WORK'],trustedComposition:true,candidateEnterNoMessage:true}));
}finally{socket.close();}
