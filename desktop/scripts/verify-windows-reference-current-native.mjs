import assert from 'node:assert/strict';
const port=Number(process.argv[2]);
const mode=process.argv[3];
async function evaluate(expression) {
  const tabs = await fetch(`http://127.0.0.1:${port}/json/list`).then(response => response.json());
  const tab = tabs.find(item => item.type === 'page' && item.url === 'http://tauri.localhost/');
  if (!tab) throw new Error('isolated Tauri page unavailable');
  const socket = new WebSocket(tab.webSocketDebuggerUrl);
  try {
    await new Promise((resolve, reject) => {
      socket.addEventListener('open', resolve, { once: true });
      socket.addEventListener('error', reject, { once: true });
    });
    return await new Promise((resolve, reject) => {
      const timer = setTimeout(() => reject(new Error('CDP evaluation timed out')), 20000);
      socket.addEventListener('message', event => {
        const message = JSON.parse(event.data);
        if (message.id !== 1) return;
        clearTimeout(timer);
        if (message.error || message.result?.exceptionDetails) reject(new Error(`CDP evaluation failed: ${message.result?.exceptionDetails?.exception?.description || message.result?.exceptionDetails?.text || message.error?.message || 'unknown'}`));
        else resolve(message.result?.result?.value);
      });
      socket.send(JSON.stringify({ id: 1, method: 'Runtime.evaluate', params: {
        expression, awaitPromise: true, returnByValue: true,
      } }));
    });
  } finally {
    socket.close();
  }
}

async function until(check, label) {
  const deadline = Date.now() + 30000;
  while (Date.now() < deadline) {
    try {
      const result = await check();
      if (result) return result;
    } catch { /* WebView2 may still be loading or changing areas. */ }
    await new Promise(resolve => setTimeout(resolve, 250));
  }
  console.error(JSON.stringify(await evaluate("({area:globalThis.__nanfengDesktopWorkState?.dataArea,selected:globalThis.__nanfengDesktopWorkState?.selectedConversationId,error:globalThis.__nanfengDesktopWorkState?.error,text:document.body.innerText.slice(0,2400)})"))); throw new Error(`${label} did not become ready`);
}

const js = value => JSON.stringify(value);
const click = selector => evaluate(`(()=>{const e=document.querySelector(${js(selector)});if(!e)return false;e.click();return true})()`);
async function invoke(area, command, args = {}) {
  const result = await evaluate(`window.__TAURI__.core.invoke(${js(command)},${js(args)},${js({ headers: { 'x-nanfeng-data-area': area } })}).catch(error=>({__error:String(error)}))`);
  if (result?.__error) throw new Error(`${command}: ${result.__error}`);
  return result;
}

await until(()=>evaluate("Boolean(window.__TAURI__?.core?.invoke&&globalThis.__nanfengDesktopWorkState?.current)"),'startup');
if(mode==='cold'){
 const seed=JSON.parse(process.argv[4]);
 for(const area of ['CHAT','WORK']){
  const draft=await invoke(area,'read_desktop_conversation_draft',{workspaceId:seed[area].workspaceId,conversationId:null});
  assert.equal(draft.text,seed[area].draft);
  const workspace=await invoke(area,'read_desktop_workspace',{workspaceId:seed[area].workspaceId});
  const source=workspace.exchange.conversations.find(c=>c.id===seed[area].conversationId);
  assert.ok(JSON.stringify(source).includes(seed[area].marker));
 }
 console.log(JSON.stringify({coldReadback:true}));
}else{
 const tag=`reference-${Date.now()}`;
 const result={};
 for(const area of ['CHAT','WORK']){
  const rows=await invoke(area,'list_desktop_workspaces');
  let id=rows[0]?.id;
  if(!id){await click('[data-action="show-work"]'); await until(()=>evaluate("globalThis.__nanfengDesktopWorkState?.dataArea==='WORK'&&globalThis.__nanfengDesktopWorkState?.current?.summary?.id"),'WORK workspace');id=(await invoke(area,'list_desktop_workspaces'))[0].id;}
  const marker=`${area} synthetic ${tag}`;
  const created=await invoke(area,'mutate_desktop_domain',{args:{intentId:`${tag}-${area.toLowerCase()}`,workspaceId:id,entity:'conversation',action:'create',fields:{title:marker,projectId:null,firstMessage:marker}}});
  result[area]={workspaceId:id,conversationId:created.objectId,marker};
 }
 for(const area of ['CHAT','WORK']){
  const target=area==='CHAT'?'WORK':'CHAT';
  await until(()=>click(`[data-action="show-${area==='CHAT'?'chat':'work'}"]`),"area button");
  await until(()=>evaluate(`globalThis.__nanfengDesktopWorkState?.dataArea===${js(area)}`),`${area} active`);
  // Reload the projection through the existing area route after creating synthetic sources.
  await evaluate('location.reload();true'); await new Promise(r=>setTimeout(r,1200));
  await until(()=>evaluate("Boolean(globalThis.__nanfengDesktopWorkState?.current&&document.querySelector('#chat-search'))"),'reload ready');
  await until(()=>click(`[data-action="show-${area==='CHAT'?'chat':'work'}"]`),"area button");
  await until(async()=>{await click(`[data-action="show-${area==='CHAT'?'chat':'work'}"]`);return evaluate(`globalThis.__nanfengDesktopWorkState?.dataArea===${js(area)}`)}, 'reload area');
  await until(()=>evaluate(`Boolean(document.querySelector('[data-action="select-chat"][data-id="${result[area].conversationId}"]'))`),'source row');
  await until(()=>click(`[data-action="select-chat"][data-id="${result[area].conversationId}"]`),"source click");
  await until(()=>evaluate(`document.querySelector('.chat-main')?.innerText.includes(${js(result[area].marker)})`),'source visible');
  await until(()=>click('[data-action="open-assistant-message-menu"]'),'source menu');
  await until(()=>click('[data-action="reference-message-to-other-area"]'),"reference menu click");
  const visible=await until(()=>evaluate(`(()=>{const s=globalThis.__nanfengDesktopWorkState;const e=document.querySelector('#chat-composer');return s?.dataArea===${js(target)}&&e?.value.includes(${js(result[area].marker)})?e.value:null})()`),'target visible draft');
  const draft=await invoke(target,'read_desktop_conversation_draft',{workspaceId:result[target].workspaceId,conversationId:null});
  assert.equal(draft.text,visible);
  result[target].draft=visible;
 }
 console.log(JSON.stringify(result));
}
