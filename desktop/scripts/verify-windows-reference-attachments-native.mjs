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


const {readFile,writeFile}=await import('node:fs/promises');
const {join,dirname}=await import('node:path');
const {randomUUID,createHash}=await import('node:crypto');
await until(()=>evaluate('Boolean(window.__TAURI__?.core?.invoke)'), 'native IPC ready');
const seed=JSON.parse(await readFile(process.argv[4],'utf8'));
const statePath=process.argv[5];
const workspace=async area=>invoke(area,'read_desktop_workspace',{workspaceId:seed[area].workspaceId});
const draft=async area=>invoke(area,'read_desktop_conversation_draft',{workspaceId:seed[area].workspaceId,conversationId:null});
const hash=x=>createHash('sha256').update(JSON.stringify(x)).digest('hex');
if(mode==='cold-attachments'){
 const state=JSON.parse(await readFile(statePath,'utf8'));
 for(const area of ['CHAT','WORK']){
  const saved=await draft(area);assert.equal(saved.text,state[area].text);assert.deepEqual(saved.attachments,state[area].attachments);
  assert.equal(saved.modelId,state[area].modelId);assert.equal(saved.toneOverride,state[area].toneOverride);assert.equal(saved.webSearchOverride,false);
  const source=(await workspace(area)).exchange.conversations.find(c=>c.id===state[area].sourceId);assert.equal(hash(source),state[area].sourceHash);
 }
 console.log(JSON.stringify({attachmentColdReadback:true}));
}else{
 const state={};
 for(const area of ['CHAT','WORK']){
  const path=join(dirname(statePath),`reference-${area}-${randomUUID()}.txt`);await writeFile(path,`${area} synthetic private attachment only\n`);
  const attachment=await invoke(area,'import_desktop_conversation_attachment',{args:{workspaceId:seed[area].workspaceId,selectedPath:path}});
  const marker=`${area} selected TEXT only ${randomUUID()}`;
  const created=await invoke(area,'mutate_desktop_domain',{args:{intentId:`ref-asset-${randomUUID()}`,workspaceId:seed[area].workspaceId,entity:'conversation',action:'create',fields:{title:marker,projectId:null,firstMessage:marker,attachmentIds:[attachment.id]}}});
  const source=(await workspace(area)).exchange.conversations.find(c=>c.id===created.objectId);
  assert.ok(source.messages[0].blocks.some(b=>b.kind!=='TEXT'),'source really carries attachment');
  await invoke(area,'save_desktop_conversation_draft',{args:{workspaceId:seed[area].workspaceId,conversationId:null,text:`${area} existing draft`,attachmentIds:[attachment.id],modelId:'DEEPSEEK_V4_PRO',toneOverride:'professional',webSearchOverride:false}});
  state[area]={sourceId:created.objectId,sourceHash:hash(source),source,messageId:source.messages[0].id,marker,attachment};
 }
 for(const area of ['CHAT','WORK']){
  const target=area==='CHAT'?'WORK':'CHAT';const before=await draft(target);
  await invoke(area,'reference_desktop_message_to_other_area',{workspaceId:seed[area].workspaceId,conversationId:state[area].sourceId,messageId:state[area].messageId,expectedConversationRevision:state[area].source.revision,targetWorkspaceId:seed[target].workspaceId});
  const after=await draft(target);assert.ok(after.text.startsWith(before.text));assert.ok(after.text.includes(state[area].marker));
  assert.ok(!after.text.includes(state[area].attachment.id));assert.ok(!after.text.includes(state[area].attachment.displayName));
  assert.deepEqual(after.attachments,before.attachments);assert.equal(after.modelId,before.modelId);assert.equal(after.toneOverride,before.toneOverride);assert.equal(after.webSearchOverride,false);
  assert.equal(hash((await workspace(area)).exchange.conversations.find(c=>c.id===state[area].sourceId)),state[area].sourceHash);
 }
 for(const area of ['CHAT','WORK']){const saved=await draft(area);state[area]={...state[area],text:saved.text,attachments:saved.attachments,modelId:saved.modelId,toneOverride:saved.toneOverride,webSearchOverride:saved.webSearchOverride};delete state[area].source;}
 await writeFile(statePath,JSON.stringify(state,null,2));console.log(JSON.stringify({textOnlyReference:true,targetAttachmentsAndSelectionsPreserved:true,sourceSnapshotsUnchanged:true}));
}
