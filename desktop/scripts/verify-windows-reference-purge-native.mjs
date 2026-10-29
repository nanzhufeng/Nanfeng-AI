import assert from 'node:assert/strict';
import { readFile, writeFile } from 'node:fs/promises';
import { createHash, randomUUID } from 'node:crypto';

const [port, mode, receiptPath] = process.argv.slice(2);
const hash = value => createHash('sha256').update(JSON.stringify(value)).digest('hex');
async function evaluate(expression) {
  const tabs = await fetch(`http://127.0.0.1:${Number(port)}/json/list`).then(r => r.json());
  const tab = tabs.find(t => t.type === 'page' && t.url === 'http://tauri.localhost/');
  assert.ok(tab, 'isolated Tauri page');
  const socket = new WebSocket(tab.webSocketDebuggerUrl);
  try {
    await new Promise((resolve, reject) => { socket.addEventListener('open', resolve, { once:true }); socket.addEventListener('error', reject, { once:true }); });
    return await new Promise((resolve, reject) => {
      const timer = setTimeout(() => reject(new Error('CDP timeout')), 30000);
      socket.addEventListener('message', event => {
        const message = JSON.parse(event.data);
        if (message.id !== 1) return;
        clearTimeout(timer);
        if (message.error || message.result?.exceptionDetails) reject(new Error(JSON.stringify(message.result?.exceptionDetails || message.error)));
        else resolve(message.result.result.value);
      });
      socket.send(JSON.stringify({id:1,method:'Runtime.evaluate',params:{expression,awaitPromise:true,returnByValue:true}}));
    });
  } finally { socket.close(); }
}
async function invoke(area, command, args={}) {
  const result = await evaluate(`window.__TAURI__.core.invoke(${JSON.stringify(command)},${JSON.stringify(args)},${JSON.stringify({headers:{'x-nanfeng-data-area':area}})}).catch(e=>({__error:String(e)}))`);
  if(result?.__error) throw new Error(`${command}: ${result.__error}`);
  return result;
}
const deadline=Date.now()+30000;
while(true){try{if(await evaluate('Boolean(window.__TAURI__?.core?.invoke)'))break;}catch{} assert.ok(Date.now()<deadline,'native readiness'); await new Promise(r=>setTimeout(r,250));}
const areas=['CHAT','WORK'];
let record=mode==='cold'?JSON.parse(await readFile(receiptPath,'utf8')):{};
const workspace=area=>invoke(area,'read_desktop_workspace',{workspaceId:record[area].workspaceId});
const draft=area=>invoke(area,'read_desktop_conversation_draft',{workspaceId:record[area].workspaceId,conversationId:null});
const mutation=(area,action,c,fields={})=>invoke(area,'mutate_desktop_domain',{args:{intentId:`purge-${randomUUID()}`,workspaceId:record[area].workspaceId,entity:'conversation',action,objectId:c?.id,expectedRevision:c?.revision,fields}});
const history=(area,redo=false)=>invoke(area,redo?'redo_desktop_domain':'undo_desktop_domain',{args:{intentId:`history-${randomUUID()}`,workspaceId:record[area].workspaceId}});
const reference=(area,c)=>invoke(area,'reference_desktop_message_to_other_area',{workspaceId:record[area].workspaceId,conversationId:c.id,messageId:c.messages[0].id,expectedConversationRevision:c.revision,targetWorkspaceId:record[area==='CHAT'?'WORK':'CHAT'].workspaceId});
const find=async(area,id)=>(await workspace(area)).exchange.conversations.find(c=>c.id===id);
const purge=(area,c)=>invoke(area,'permanently_delete_desktop_conversation',{args:{workspaceId:record[area].workspaceId,conversationId:c.id,expectedRevision:c.revision}});
if(mode==='cold'){
  for(const area of areas){assert.equal(hash((await workspace(area)).exchange),record[area].exchangeHash);assert.deepEqual(await draft(area),record[area].draft);assert.equal(await find(area,record[area].sourceId),undefined);assert.equal(await find(area,record[area].targetId),undefined);await assert.rejects(()=>history(area),/没有可 undo/);await assert.rejects(()=>history(area,true),/没有可 redo/);}
  console.log(JSON.stringify({coldReadback:true,areas}));
}else{
  assert.equal(mode,'run');
  for(const area of areas){const projection=await invoke(area,'ensure_desktop_local_workspace');record[area]={workspaceId:projection.summary.id};}
  for(const area of areas){
    const text=`${area} synthetic purge ${randomUUID()}`;
    const created=await mutation(area,'create',null,{title:text,projectId:null,firstMessage:text});
    record[area].sourceId=created.objectId;
  }
  for(const area of areas){
    const target=area==='CHAT'?'WORK':'CHAT';
    let source=await find(area,record[area].sourceId);
    await assert.rejects(()=>purge(area,source),/只能永久删除回收站/);
    const sourceHash=hash(source);
    await Promise.all([reference(area,source),reference(area,source)]);
    const snapshot=await draft(target);const marker=source.messages[0].blocks.find(b=>b.kind==='TEXT').text;
    assert.equal(snapshot.text.split(`> ${marker}\n`).length-1,2,'concurrent references retain both quoted bodies');
    assert.equal(hash(await find(area,source.id)),sourceHash);
    await mutation(area,'softDelete',source);
    await history(area);
    source=await find(area,source.id);assert.ok(source);assert.notEqual(source.deleted,true);assert.ok(source.deletedAt==null);
    assert.deepEqual(await draft(target),snapshot,'undo source deletion preserves snapshot');
    await history(area,true);source=await find(area,source.id);assert.equal(source.deleted,true);
    assert.deepEqual(await draft(target),snapshot,'redo source deletion preserves snapshot');
    const stale={...source,revision:source.revision-1};await assert.rejects(()=>purge(area,stale),/会话已更新/);
    await purge(area,source);
    assert.equal(await find(area,source.id),undefined);
    await assert.rejects(()=>reference(area,source),/会话已不可用/);
    assert.deepEqual(await draft(target),snapshot,'permanent source deletion preserves copied snapshot');
    await assert.rejects(()=>history(area),/没有可 undo/);await assert.rejects(()=>history(area,true),/没有可 redo/);
    const created=await mutation(target,'create',null,{title:'Synthetic copied target',projectId:null,firstMessage:snapshot.text});
    record[target].targetId=created.objectId;
    let copied=await find(target,created.objectId);const otherHash=hash((await workspace(area)).exchange);
    await mutation(target,'softDelete',copied);copied=await find(target,created.objectId);await purge(target,copied);
    assert.equal(await find(target,created.objectId),undefined);assert.equal(hash((await workspace(area)).exchange),otherHash,'target purge does not mutate source area');
    assert.deepEqual(await draft(target),snapshot,'target purge preserves independent unsent draft');
    record[area].checks={concurrentAppend:true,sourceUndoRedoSnapshotStable:true,sourcePurgeSnapshotStable:true,purgedSourceRejected:true,stalePurgeRejected:true,targetPurgeOtherAreaStable:true};
  }
  for(const area of areas){record[area].exchangeHash=hash((await workspace(area)).exchange);record[area].draft=await draft(area);await assert.rejects(()=>history(area),/没有可 undo/);await assert.rejects(()=>history(area,true),/没有可 redo/);}
  await writeFile(receiptPath,JSON.stringify(record,null,2),{flag:'wx'});
  console.log(JSON.stringify({purgeLifecycle:true,record}));
}
