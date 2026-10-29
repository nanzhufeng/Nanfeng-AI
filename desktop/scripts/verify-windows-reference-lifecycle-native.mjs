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
const {createHash,randomUUID}=await import('node:crypto');
await until(()=>evaluate('Boolean(window.__TAURI__?.core?.invoke)'), 'native IPC ready');
const seed=JSON.parse(await readFile(process.argv[4],'utf8'));
const digest=value=>createHash('sha256').update(JSON.stringify(value)).digest('hex');
const workspace=async area=>invoke(area,'read_desktop_workspace',{workspaceId:seed[area].workspaceId});
const draft=async area=>invoke(area,'read_desktop_conversation_draft',{workspaceId:seed[area].workspaceId,conversationId:null});
const source=async area=>(await workspace(area)).exchange.conversations.find(c=>c.id===seed[area].conversationId);
const mutate=async(area,action,conversation,fields={})=>invoke(area,'mutate_desktop_domain',{args:{intentId:`ref-life-${randomUUID()}`,workspaceId:seed[area].workspaceId,entity:'conversation',action,objectId:conversation.id,expectedRevision:conversation.revision,fields}});
const reference=async(area,conversation,messageId)=>invoke(area,'reference_desktop_message_to_other_area',{workspaceId:seed[area].workspaceId,conversationId:conversation.id,messageId,expectedConversationRevision:conversation.revision,targetWorkspaceId:seed[area==='CHAT'?'WORK':'CHAT'].workspaceId});
if(mode==='cold-lifecycle'){
 const expected=JSON.parse(await readFile(process.argv[5],'utf8'));
 for(const area of ['CHAT','WORK']){
  assert.equal(digest(await source(area)),expected[area].finalSourceHash);
  assert.equal((await draft(area)).text,expected[area].finalDraft);
  const target=(await workspace(area)).exchange.conversations.find(c=>c.id===expected[area].deletedTargetId);
  assert.equal(target.deleted,true);
 }
 console.log(JSON.stringify({coldLifecycleReadback:true}));
}else{
 const results={};
 for(const area of ['CHAT','WORK']){
  const target=area==='CHAT'?'WORK':'CHAT';
  const before=await source(area);const user=before.messages.find(m=>m.role==='user');assert.ok(user);
  const previous=(await draft(target)).text;
  const changed=`${seed[area].marker} explicitly edited ${randomUUID()}`;
  await mutate(area,'editUserMessage',before,{messageId:user.id,text:changed});
  const edited=await source(area);assert.ok(edited.revision>before.revision);
  assert.equal((await draft(target)).text,previous,'editing source cannot change selected target snapshot');
  await assert.rejects(()=>reference(area,before,user.id),/来源已变化/);
  assert.equal((await draft(target)).text,previous,'stale selection cannot append');
  const editedMessage=edited.messages.find(m=>m.id===edited.currentLeafId);
  assert.ok(editedMessage.blocks.some(b=>b.text===changed));
  await reference(area,edited,editedMessage.id);
  const appended=(await draft(target)).text;
  assert.ok(appended.startsWith(previous));assert.ok(appended.includes(changed));
  assert.ok(appended.includes(digestText(changed)),'reference includes exact text SHA256');
  assert.equal(digest(await source(area)),digest(edited),'reference leaves complete source unchanged');
  const targetCreated=await invoke(target,'mutate_desktop_domain',{args:{intentId:`ref-target-${randomUUID()}`,workspaceId:seed[target].workspaceId,entity:'conversation',action:'create',fields:{title:'Synthetic referenced target lifecycle',projectId:null,firstMessage:appended}}});
  let targetConversation=(await workspace(target)).exchange.conversations.find(c=>c.id===targetCreated.objectId);
  await mutate(target,'softDelete',targetConversation);
  assert.equal(digest(await source(area)),digest(edited),'target conversation deletion preserves source');
  const savedDraft=await draft(target);
  await invoke(target,'save_desktop_conversation_draft',{args:{...savedDraft,workspaceId:seed[target].workspaceId,conversationId:null,text:'',attachmentIds:[]}});
  assert.equal(digest(await source(area)),digest(edited),'target draft clearing preserves source');
  await reference(area,edited,editedMessage.id);
  const finalDraft=(await draft(target)).text;assert.ok(finalDraft.includes(changed));assert.ok(!finalDraft.includes(seed[area].draft));
  await mutate(area,'softDelete',edited);
  const deletedSource=await source(area);
  await assert.rejects(()=>reference(area,deletedSource,editedMessage.id),/会话已不可用/);
  assert.equal((await draft(target)).text,finalDraft,'deleted source rejection preserves target');
  await mutate(area,'restoreDeleted',deletedSource);
  results[area]={...results[area],finalSourceHash:digest(await source(area)),sourceEditSnapshotStable:true,staleVersionRejected:true,editedVersionHashVerified:true,targetDeleteSourceStable:true,draftClearSourceStable:true,deletedSourceRejected:true};
  results[target]={...results[target],finalDraft,deletedTargetId:targetCreated.objectId};
 }
 // Both directions edit their own source; bind final snapshots only after both operations.
 for(const area of ['CHAT','WORK']){results[area].finalSourceHash=digest(await source(area));results[area].finalDraft=(await draft(area)).text;}
 await writeFile(process.argv[5],JSON.stringify(results,null,2));
 console.log(JSON.stringify({lifecycle:true,areas:results}));
}
function digestText(text){return createHash('sha256').update(text).digest('hex');}
