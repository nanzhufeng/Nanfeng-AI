import { canonicalJson, sha256 } from '../../protocol/scripts/exchange-lib.mjs';
import assert from 'node:assert/strict';
import { createHash, randomUUID } from 'node:crypto';
import { execFileSync, spawn } from 'node:child_process';
import { mkdir, readFile, readdir, writeFile } from 'node:fs/promises';
import http from 'node:http';
import { dirname, join, resolve, sep } from 'node:path';
import { fileURLToPath } from 'node:url';

if (process.platform !== 'win32') throw new Error('仅在 Windows 隔离候选上验收。');
const desktopRoot = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const rootParent = resolve(process.argv[2] || 'D:\\CodexTemp');
assert.ok(process.execArgv.includes('--max-old-space-size=2048'));
const receipt = JSON.parse(await readFile(join(desktopRoot, 'output/windows-acceptance/current.json'), 'utf8'));
assert.equal(receipt.identifier, 'com.nanzhufeng.ai.desktop.windowsacceptance');
const source = resolve(desktopRoot, 'output/windows-acceptance', receipt.sha256, 'NanfengAI-Windows-Acceptance.exe');
assert.equal(resolve(receipt.executable), source);
assert.equal(createHash('sha256').update(await readFile(source)).digest('hex').toUpperCase(), receipt.sha256);
const runExecutable = resolve(desktopRoot, 'output/windows-acceptance-binaries', receipt.sha256, 'nanfeng-ai-desktop-spike.exe');
const ps = (args) => execFileSync('pwsh', ['-NoProfile', ...args], { encoding: 'utf8', timeout: 60000 }).trim();
const launchPowerShell = (args) => new Promise((resolveLaunch, reject) => {
  const child = spawn('pwsh', ['-NoProfile', ...args], { stdio: ['ignore', 'pipe', 'pipe'] });
  let stdout = '';
  let stderr = '';
  const timer = setTimeout(() => { child.kill(); reject(new Error(`隔离候选启动超时：${stderr}`)); }, 30000);
  child.stdout.on('data', data => {
    stdout += data.toString();
    const line = stdout.split(/\r?\n/).find(item => item.trim().startsWith('{') && item.includes('"pid"'));
    if (!line) return;
    clearTimeout(timer);
    try { const emitted=JSON.parse(line); readFile(emitted.launchReceipt, 'utf8').then(data=>resolveLaunch(JSON.parse(data)),reject); } catch (error) { reject(error); }
    child.stdout.destroy();
    child.stderr.destroy();
    child.unref();
  });
  child.stderr.on('data', data => { stderr += data.toString(); });
  child.on('error', error => { clearTimeout(timer); reject(error); });
  child.on('exit', code => {
    if (!stdout.includes('"pid"')) { clearTimeout(timer); reject(new Error(`隔离候选启动失败 ${code}：${stderr}`)); }
  });
});
const reservePort = async () => {
  const server = http.createServer();
  await new Promise(resolveReady => server.listen(0, '127.0.0.1', resolveReady));
  const port = server.address().port;
  await new Promise(resolveClose => server.close(resolveClose));
  return port;
};


const requests=[],serverErrors=[];
const accountEmail='cloud-atomic@example.invalid', syntheticToken='synthetic-cloud-atomic-access';
const vectors=JSON.parse(await readFile(join(desktopRoot,'../protocol/fixtures/data-area-sync-v1.json'),'utf8'));
const conversationId='same-area-conversation';
function remoteDocument(area){
 const content=structuredClone(vectors.find(v=>v.platform==='DESKTOP'&&v.area===area).content);
 content.title=`SYNTHETIC_${area}_BRANCH_MARKDOWN`;content.currentLeafId='m5';content.pinned=true;
 const model=content.messages[0];
 content.messages=[['m1',null,0,'user',`SYNTHETIC_${area}_ROOT 中文🙂`],['m2','m1',0,'assistant',`SYNTHETIC_${area}_ANSWER\n\n## 保留标题\n\n| 列 | 值 |\n| --- | --- |\n| 中文 | 42 |\n\n<synthetic-literal> & "quote"`],['m3','m2',0,'user',`SYNTHETIC_${area}_MAIN_BRANCH`],['m4','m2',1,'user',`SYNTHETIC_${area}_SIBLING_BRANCH`],['m5','m3',0,'assistant',`SYNTHETIC_${area}_LEAF\n\n- 条目一\n- 条目二\n\n\`inline-code\`\n\n尾段`]].map(([id,parentId,ordinal,role,text],index)=>({...structuredClone(model),id,parentId,ordinal,role,createdAt:`1970-01-01T00:00:0${index+1}Z`,blocks:[{kind:'TEXT',text}]}));
 content.updatedAt='1970-01-01T00:00:05Z';
 const documentId='conversation-'+sha256(Buffer.from(area==='WORK'?`WORK:${conversationId}`:conversationId)).slice(0,40);
 const payload={format:'nfai.sync.payload',protocolVersion:1,schemaVersion:1,appId:'com.nanzhufeng.ai',documentId,revision:1,records:[{kind:'conversation',id:conversationId,revision:1,classification:'NORMAL',content}]};
 const plain=Buffer.from(canonicalJson(payload));
 return {format:'nfai.sync.direct',protocolVersion:1,schemaVersion:1,appId:payload.appId,documentId,revision:1,payloadHash:sha256(plain),payloadByteCount:plain.length,payload};
}
const remoteStore=new Map();
let documents=['CHAT','WORK'].map(remoteDocument);
assert.notEqual(documents[0].documentId,documents[1].documentId);
const server=http.createServer(async(request,response)=>{
 try{
  const chunks=[];for await(const chunk of request)chunks.push(chunk);const bytes=Buffer.concat(chunks);
  const body=bytes.length?JSON.parse(bytes):{},path=request.url?.split('?')[0];
  requests.push({path,bodyBytes:bytes.length,bodySha256:sha256(bytes)});let value;
  if(path==='/auth/v1/token'){assert.equal(body.auth_code,'nfai-synthetic-windows-account-code');response.writeHead(200,{'content-type':'application/json'});response.end(JSON.stringify({access_token:syntheticToken,refresh_token:'synthetic-cloud-pair-refresh',expires_in:3600,user:{id:'00000000-0000-4000-8000-000000000118',email:accountEmail,user_metadata:{full_name:'合成云列表交互账号'}}}));return;}
   assert.equal(request.headers.authorization,`Bearer ${syntheticToken}`);assert.equal(body.p_app_id,'com.nanzhufeng.ai');requests.at(-1).documentId=body.p_document_id??null;requests.at(-1).expectedRevision=body.p_expected_revision??null;
   if(path==='/rest/v1/rpc/nanfeng_sync_inventory')value={documentCount:remoteStore.size,documents:[...remoteStore.values()].map(envelope=>({envelope}))};
   else if(path==='/rest/v1/rpc/nanfeng_sync_read_document'){const doc=remoteStore.get(body.p_document_id);value=doc?{envelope:doc}:[];}
   else if(path==='/rest/v1/rpc/nanfeng_sync_commit_document'){
    assert.ok(['cloud-conversation-list-v1','cloud-work-conversation-list-v1',...documents.map(d=>d.documentId)].includes(body.p_document_id));const old=remoteStore.get(body.p_document_id);assert.equal(body.p_expected_revision,old?.revision??0);const doc=body.p_envelope;assert.equal(doc.format,'nfai.sync.direct');assert.equal(doc.documentId,body.p_document_id);assert.equal(doc.revision,body.p_expected_revision+1);const bytes=Buffer.from(canonicalJson(doc.payload));assert.equal(doc.payloadHash,sha256(bytes));assert.equal(doc.payloadByteCount,bytes.length);if(body.p_document_id.startsWith('cloud-')){assert.equal(doc.payload.records[0].kind,'safe_settings');assert.equal(doc.payload.records[0].content.type,'CLOUD_CONVERSATION_LIST_V1');assert.ok([JSON.stringify([]),JSON.stringify([conversationId])].includes(JSON.stringify(doc.payload.records[0].content.pinnedConversationIds)));}else{const original=documents.find(d=>d.documentId===body.p_document_id);assert.equal(doc.payload.records.length,1);assert.equal(doc.payload.records[0].kind,'conversation');assert.equal(doc.payload.records[0].id,conversationId);assert.equal(doc.payload.records[0].content.surface,original.payload.records[0].content.surface);assert.equal(doc.payload.records[0].content.title,original.payload.records[0].content.title);assert.deepEqual(doc.payload.records[0].content.messages,original.payload.records[0].content.messages);}remoteStore.set(doc.documentId,doc);value={revision:doc.revision,payload_hash:doc.payloadHash};
   }else if(path==='/rest/v1/rpc/nanfeng_sync_delete_document'){
    assert.equal(body.p_document_id,documents[1].documentId);const doc=remoteStore.get(body.p_document_id);assert.ok(doc);assert.equal(body.p_expected_revision,doc.revision);remoteStore.delete(doc.documentId);value=[{deleted:true}];
   }else throw new Error('Unexpected synthetic RPC '+path);
   response.writeHead(200,{'content-type':'application/json'});response.end(JSON.stringify(value));
 }catch(error){serverErrors.push(String(error));response.writeHead(500);response.end('{"message":"synthetic protocol failure"}');}
});
await new Promise(done=>server.listen(0,'127.0.0.1',done));const serverPort=server.address().port;

let launch = null;
let root = '';
const pids = [];
const stop = () => {
  if (!launch) return;
  const pid = Number(launch.pid);
  const literal = value => `'${String(value).replaceAll("'", "''")}'`;
  const script = '$expected=' + literal(runExecutable) + '; $hash=' + literal(receipt.sha256) + '; '
    + '$p=Get-CimInstance Win32_Process -Filter "ProcessId=' + pid + '"; '
    + 'if ($p) { if ([IO.Path]::GetFullPath([string]$p.ExecutablePath) -ne $expected '
    + '-or (Get-FileHash -LiteralPath $expected -Algorithm SHA256).Hash -ne $hash) { throw "PID identity mismatch" }; '
    + 'Stop-Process -Id ' + pid + ' -Force -ErrorAction Stop }';
  ps(['-Command', script]);
  pids.push(pid);
  launch = null;
};
async function start(reopen = false) {
  const port = await reservePort();
  const args = ['-File', join(desktopRoot, 'scripts/start-windows-acceptance.ps1'), '-RootParent', rootParent,
    '-ExecutablePath', source, '-StartupMode', 'normal', '-Visible', '-DebugPort', String(port),'-AccountSyncTestEndpoint',`http://127.0.0.1:${serverPort}`];
  if (reopen) args.push('-Root', root);
  launch = await launchPowerShell(args);
  assert.equal(launch.sha256, receipt.sha256);
  assert.equal(resolve(launch.executable), runExecutable);
  root = resolve(launch.root);
  assert.ok(root.startsWith(rootParent + sep));
  const marker = JSON.parse(await readFile(join(root, '.nanfeng-windows-acceptance.json'), 'utf8'));
  assert.equal(marker.identifier, receipt.identifier);
  return port;
}
async function cdp(port, method, params = {}) {
  const tabs = await fetch(`http://127.0.0.1:${port}/json/list`,{signal:AbortSignal.timeout(10000)}).then(response => response.json());
  const tab = tabs.find(item => item.type === 'page' && item.url === 'http://tauri.localhost/');
  assert.ok(tab, '验收 WebView2 页面未就绪');
  const socket = new WebSocket(tab.webSocketDebuggerUrl);
  try {
    await new Promise((resolveReady, reject) => {
      socket.addEventListener('open', resolveReady, { once: true });
      socket.addEventListener('error', reject, { once: true });
    });
    return await new Promise((resolveResult, reject) => {
      const timer = setTimeout(() => reject(new Error('CDP evaluation timed out')), 20000);
      socket.addEventListener('message', event => {
        const message = JSON.parse(event.data);
        if (message.id !== 1) return;
        clearTimeout(timer);
        if (message.error || message.result?.exceptionDetails) reject(new Error(
          message.result?.exceptionDetails?.exception?.description || String(message.result?.exceptionDetails?.exception?.value||'').slice(0,500) || message.result?.exceptionDetails?.text || message.error?.message));
        else resolveResult(message.result);
      });
      socket.send(JSON.stringify({ id: 1, method, params }));
    });
  } finally { socket.close(); }
}
async function evaluate(port, expression) {
  return (await cdp(port, 'Runtime.evaluate', { expression, awaitPromise: true, returnByValue: true }))?.result?.value;
}
async function invoke(port, area, command, args = {}) {
  const expression = `window.__TAURI__.core.invoke(${JSON.stringify(command)},${JSON.stringify(args)},${JSON.stringify({ headers: { 'x-nanfeng-data-area': area } })}).catch(error=>({__error:String(error)}))`;
  const result = await evaluate(port, expression);
  if (result?.__error) throw new Error(`${command}: ${result.__error}`);
  return result;
}
async function ready(port) {
  const deadline = Date.now() + 30000;
  while (Date.now() < deadline) {
    try { if (await evaluate(port, 'Boolean(window.__TAURI__?.core?.invoke)')) return; } catch { /* Startup is still active. */ }
    await new Promise(resolveWait => setTimeout(resolveWait, 250));
  }
  throw new Error('原生 IPC 未就绪');
}
async function until(check, label) {
  const deadline = Date.now() + 30000;
  while (Date.now() < deadline) {
    try { const value = await check(); if (value) return value; } catch { /* Visible page may be refreshing. */ }
    await new Promise(resolveWait => setTimeout(resolveWait, 200));
  }
  throw new Error(`${label} 未在原生界面出现`);
}
async function clickUi(port, selector) {
  const result = await evaluate(port, `(()=>{const node=document.querySelector(${JSON.stringify(selector)});if(!node)return false;node.click();return true})()`);
  assert.equal(result, true, `找不到可点击控件 ${selector}`);
}
async function normalClose() {
  const pid=Number(launch.pid);
  const quote=value=>`'${String(value).replaceAll("'", "''")}'`;
  const info=JSON.parse(ps(['-Command',`$p=Get-CimInstance Win32_Process -Filter "ProcessId=${pid}";if(!$p -or [IO.Path]::GetFullPath([string]$p.ExecutablePath) -ne ${quote(runExecutable)} -or (Get-FileHash -LiteralPath ${quote(runExecutable)}).Hash -ne ${quote(receipt.sha256)}){throw 'PID identity mismatch'};$n=Get-Process -Id ${pid};@{title=$n.MainWindowTitle;responding=$n.Responding;accepted=$n.CloseMainWindow()}|ConvertTo-Json -Compress`]));
  assert.ok(info.title && info.responding && info.accepted);
  await until(()=>ps(['-Command',`@(Get-CimInstance Win32_Process -Filter "ProcessId=${pid}").Count`])==='0','自然关闭退出');
  await until(()=>ps(['-Command',`@(Get-CimInstance Win32_Process -Filter "Name='msedgewebview2.exe'" | Where-Object {$_.CommandLine -and $_.CommandLine.Contains(${quote(launch.webviewProfile)})}).Count`])==='0','本根WebView退出');
  pids.push(pid);launch=null;return info;
}




const result={candidateSha256:receipt.sha256,realService:false,cases:[],closes:[],scope:'Actual settings and area navigation on marked fake root; 5-message branch/Markdown pure-text payload; no provider, physical-input or Mac proof'};
let stage='start';
const inspect=()=>JSON.parse(execFileSync('python',[join(desktopRoot,'../docs/review/20261003-work-cloud-restore/inspect-marked-root.py'),root],{encoding:'utf8',timeout:30000}));
async function checkpoint(){await writeFile(join(root,'work-cloud-pin-delete-progress.json'),JSON.stringify(result,null,2));}
async function uiUntil(port,expression,label){await until(()=>evaluate(port,expression),label);}
async function openAccount(port){
 await uiUntil(port,'Boolean(globalThis.__nanfengDesktopWorkState?.runtimeInfo?.dataAreasReady)&&Boolean(document.querySelector("[data-action=show-settings]"))','account navigation ready');
 if(await evaluate(port,'globalThis.__nanfengDesktopWorkState.pane==="settings"&&globalThis.__nanfengDesktopWorkState.settingsSection==="account"&&Boolean(document.querySelector("[data-action=load-account-cloud-documents]"))'))return;
 await uiUntil(port,'Boolean(document.querySelector("[data-action=show-settings]"))','settings entry');
 await clickUi(port,'[data-action="show-settings"]');await uiUntil(port,'Boolean(document.querySelector("[data-action=open-settings-page][data-page=account]"))','account page');
 await clickUi(port,'[data-action="open-settings-page"][data-page="account"]');await uiUntil(port,'globalThis.__nanfengDesktopWorkState.pane==="settings"&&globalThis.__nanfengDesktopWorkState.settingsSection==="account"&&Boolean(document.querySelector("[data-action=sign-in-google-account]"))','account controls');
}
async function switchArea(port,area){
 const action=area==='WORK'?'show-work':'show-chat';
 await uiUntil(port,`Boolean(document.querySelector('[data-action="${action}"]'))`,`${area} route`);
 await clickUi(port,`[data-action="${action}"]`);
 await uiUntil(port,`globalThis.__nanfengDesktopWorkState.dataArea===${JSON.stringify(area)}&&globalThis.__nanfengDesktopWorkState.runtimeInfo?.dataAreasReady&&Boolean(document.querySelector('.chat-sidebar'))`,`${area} ready`);
}
async function inspectUi(port,area){
 await uiUntil(port,`globalThis.__nanfengDesktopWorkState.dataArea===${JSON.stringify(area)}&&globalThis.__nanfengDesktopWorkState.accountSync?.email===${JSON.stringify(accountEmail)}&&globalThis.__nanfengDesktopWorkState.cloudConversations.length===1&&globalThis.__nanfengDesktopWorkState.accountSync.selectedConversationCount===1`,`${area} cloud projection`);
 await clickUi(port,'[data-action="show-cloud-conversation-list"]');await evaluate(port,'new Promise(r=>requestAnimationFrame(()=>requestAnimationFrame(r)))');
 const ui=await evaluate(port,`(()=>{const state=globalThis.__nanfengDesktopWorkState,rows=[...document.querySelectorAll('.chat-cloud-list [data-conversation-row]')],entry=state.cloudConversations[0],cache=JSON.parse(localStorage.getItem('nanfeng-ai.desktop.sync-cloud-list.v1'+(state.dataArea==='WORK'?'.WORK':''))||'null');return {area:state.dataArea,cloudCount:state.cloudConversations.length,selected:state.accountSync.selectedConversationCount,rowCount:rows.length,ids:rows.map(r=>r.dataset.id),titles:rows.map(r=>r.querySelector('.chat-history-title')?.textContent),workspaceId:entry.workspaceId,conversationId:entry.id,cacheCount:cache?.entries.length,cacheAccountExact:cache?.email===state.accountSync.email,cacheIdExact:cache?.entries[0]?.conversationId===entry.id&&cache?.entries[0]?.workspaceId===entry.workspaceId};})()`);
 assert.equal(ui.area,area);assert.equal(ui.cloudCount,1);assert.equal(ui.rowCount,1);assert.deepEqual(ui.ids,[conversationId]);assert.deepEqual(ui.titles,[`SYNTHETIC_${area}_BRANCH_MARKDOWN`]);assert.equal(ui.cacheCount,1);assert.equal(ui.cacheAccountExact,true);assert.equal(ui.cacheIdExact,true);
 await clickUi(port,`.chat-cloud-list [data-action="select-chat"][data-id="${conversationId}"]`);
 await uiUntil(port,`globalThis.__nanfengDesktopWorkState.selectedConversationId===${JSON.stringify(conversationId)}&&globalThis.__nanfengDesktopWorkState.current?.summary?.id===${JSON.stringify(ui.workspaceId)}&&!globalThis.__nanfengDesktopWorkState.cloudConversationOpening`,`${area} conversation open`);
 const model=await evaluate(port,`(()=>{const s=globalThis.__nanfengDesktopWorkState,c=s.current.exchange.conversations.find(c=>c.id===s.selectedConversationId);return {area:s.dataArea,id:c.id,title:c.title,currentLeafId:c.currentLeafId,messages:c.messages.map(m=>({id:m.id,parentId:m.parentId,ordinal:m.ordinal,role:m.role,createdAt:m.createdAt,revision:m.revision,blocks:m.blocks})),visibleMainText:document.querySelector('.chat-content')?.textContent||document.querySelector('.chat-main')?.textContent||''};})()`);
 const expected=documents.find(d=>d.payload.records[0].content.surface===area).payload.records[0].content;
 assert.equal(model.title,expected.title);assert.equal(model.currentLeafId,expected.currentLeafId);assert.deepEqual(model.messages,expected.messages.map(m=>({id:m.id,parentId:m.parentId,ordinal:m.ordinal,role:m.role,createdAt:m.createdAt,revision:m.revision,blocks:m.blocks})));
 const dom=await evaluate(port,`(()=>{const messages=[...document.querySelectorAll('article.chat-message[data-message-id]')],table=document.querySelector('article.chat-message[data-message-id="m2"] table'),leaf=document.querySelector('article.chat-message[data-message-id="m5"]');return {ids:messages.map(m=>m.dataset.messageId),tableCells:table?[...table.querySelectorAll('th,td')].map(e=>e.textContent):[],leafItems:[...(leaf?.querySelectorAll('li')||[])].map(e=>e.textContent),leafCode:leaf?.querySelector('code')?.textContent,syntheticHtmlElementCount:document.querySelectorAll('synthetic-literal').length};})()`);
 assert.deepEqual(dom.ids,['m1','m2','m3','m5']);assert.deepEqual(dom.tableCells,['列','值','中文','42']);assert.deepEqual(dom.leafItems,['条目一','条目二']);assert.equal(dom.leafCode,'inline-code');assert.equal(dom.syntheticHtmlElementCount,0);
 return {ui,dom,model:{...model,messages:undefined,visibleMainText:undefined,messageCount:model.messages.length,messageTreeAndBlocksExact:true,completeModelSha256:sha256(Buffer.from(canonicalJson(model.messages)))}};
}
async function readArea(port,area){
 await openAccount(port);await uiUntil(port,`globalThis.__nanfengDesktopWorkState.accountSync?.email===${JSON.stringify(accountEmail)}&&Boolean(document.querySelector('[data-action=load-account-cloud-documents]:not(:disabled)'))`,'authenticated account UI');const peer=area==='CHAT'?'WORK':'CHAT',before=inspect(),requestBefore=requests.length,started=Date.now();
 await clickUi(port,'[data-action="load-account-cloud-documents"]');
 await uiUntil(port,'Boolean(globalThis.__nanfengDesktopWorkState.accountSyncProgress?.result)',`${area} complete feedback`);
 const feedback=await evaluate(port,'({area:globalThis.__nanfengDesktopWorkState.dataArea,progress:globalThis.__nanfengDesktopWorkState.accountSyncProgress.result,error:globalThis.__nanfengDesktopWorkState.error})');
 assert.equal(feedback.area,area);assert.equal(feedback.progress.kind,'success');assert.equal(feedback.error,'');
 assert.equal(requests.length-requestBefore,1);const after=inspect();assert.equal(after[peer].businessSha256,before[peer].businessSha256);
 assert.equal(after[area].selectedCount,1);assert.equal(after[area].restoredCount,1);assert.equal(after[area].messageCount,5);assert.equal(after[area].treeAndBlocksExact,true);
 await switchArea(port,area);const rendered=await inspectUi(port,area);const item={area,elapsedMs:Date.now()-started,scope:'Settings completion plus exact model and actual row/open verification; not capacity first UI clock',feedback,peerBusinessExact:true,sql:after,rendered};result.cases.push(item);await checkpoint();return item;
}
async function contextMenu(port){await clickUi(port,'[data-action="show-cloud-conversation-list"]');await evaluate(port,'new Promise(r=>requestAnimationFrame(()=>requestAnimationFrame(r)))');assert.equal(await evaluate(port,`(()=>{const row=document.querySelector('.chat-cloud-list [data-conversation-row][data-id="${conversationId}"]');if(!row)return false;const r=row.getBoundingClientRect();row.dispatchEvent(new MouseEvent('contextmenu',{bubbles:true,cancelable:true,clientX:r.left+10,clientY:r.top+10}));return true})()`),true);await uiUntil(port,'Boolean(document.querySelector(".chat-context-menu [data-action=context-menu-pin]"))','actual cloud context menu');return await evaluate(port,'({area:globalThis.__nanfengDesktopWorkState.dataArea,sidebar:globalThis.__nanfengDesktopWorkState.sidebarConversationList,menu:globalThis.__nanfengDesktopWorkState.contextMenu})');}

const evidence=resolve(desktopRoot,'../docs/review/20261004-cloud-pin-cancel-current');
async function emptyArea(port,area){
 await uiUntil(port,`globalThis.__nanfengDesktopWorkState.dataArea===${JSON.stringify(area)}&&globalThis.__nanfengDesktopWorkState.accountSync?.email===${JSON.stringify(accountEmail)}&&globalThis.__nanfengDesktopWorkState.accountSync.selectedConversationCount===0&&globalThis.__nanfengDesktopWorkState.cloudConversations.length===0`,`${area} empty receipt and cloud`);
 const ui=await evaluate(port,`(()=>{const s=globalThis.__nanfengDesktopWorkState,c=JSON.parse(localStorage.getItem('nanfeng-ai.desktop.sync-cloud-list.v1'+(s.dataArea==='WORK'?'.WORK':''))||'null');return {area:s.dataArea,selected:s.accountSync.selectedConversationCount,cloudCount:s.cloudConversations.length,error:s.error,cacheCount:c?.entries?.length}})()`);assert.equal(ui.cacheCount,0);assert.equal(ui.error,'');return ui;
}
async function pin(port,area,value){
 await switchArea(port,area);await inspectUi(port,area);const before=inspect();const trigger=await contextMenu(port);assert.equal(trigger.area,area);assert.equal(trigger.sidebar,'cloud');
 await clickUi(port,'.chat-context-menu [data-action="context-menu-pin"]');await uiUntil(port,`globalThis.__nanfengDesktopWorkState.cloudConversations[0]?.cloudPinned===${value}&&!globalThis.__nanfengDesktopWorkState.contextMenu&&!globalThis.__nanfengDesktopWorkState.error`,'cloud pin receipt');
 const after=inspect();for(const a of ['CHAT','WORK'])assert.equal(after[a].businessSha256,before[a].businessSha256);
 const local=await evaluate(port,'({pinned:Boolean(globalThis.__nanfengDesktopWorkState.current.exchange.conversations.find(c=>c.id===globalThis.__nanfengDesktopWorkState.selectedConversationId).pinned),cloudPinned:globalThis.__nanfengDesktopWorkState.cloudConversations[0].cloudPinned})');assert.equal(local.pinned,true);assert.equal(local.cloudPinned,value);
 result.cases.push({kind:'cloud-pin',area,value,localPositiveIndependent:true,businessExact:true,trigger});await checkpoint();
}
async function readMissing(port,area){
 await openAccount(port);await uiUntil(port,`globalThis.__nanfengDesktopWorkState.accountSync?.email===${JSON.stringify(accountEmail)}&&Boolean(document.querySelector('[data-action=load-account-cloud-documents]:not(:disabled)'))`,'authenticated cloud read');
 const before=inspect(),count=requests.length;await clickUi(port,'[data-action="load-account-cloud-documents"]');await uiUntil(port,'Boolean(globalThis.__nanfengDesktopWorkState.accountSyncProgress?.result)','completed cloud read');
 const feedback=await evaluate(port,'globalThis.__nanfengDesktopWorkState.accountSyncProgress.result');assert.equal(feedback.kind,'success');assert.equal(requests.length-count,1);const after=inspect();const peer=area==='CHAT'?'WORK':'CHAT';assert.equal(after[peer].businessSha256,before[peer].businessSha256);assert.equal(after[area].localCoreSha256,before[area].localCoreSha256);assert.equal(after[area].selectedCount,0);assert.equal(after[area].messageCount,5);assert.equal(after[area].treeAndBlocksExact,true);result.cases.push({kind:'complete-inventory-missing',area,feedback,business:after});await checkpoint();return after;
}
try{
 for(const doc of documents)remoteStore.set(doc.documentId,structuredClone(doc));let port=await start();result.root=root;await ready(port);
 await writeFile(join(root,'synthetic-work-cloud-documents.json'),JSON.stringify(documents,null,2));await invoke(port,'CHAT','sign_in_desktop_google_account');await cdp(port,'Page.reload',{ignoreCache:true});await ready(port);
 stage='restore-CHAT';await readArea(port,'CHAT');await switchArea(port,'WORK');stage='restore-WORK';await readArea(port,'WORK');
 result.workspaceIds={};for(const area of ['CHAT','WORK']){await switchArea(port,area);const ui=await inspectUi(port,area);result.workspaceIds[area]=ui.ui.workspaceId;assert.equal(await evaluate(port,'Boolean(globalThis.__nanfengDesktopWorkState.current.exchange.conversations.find(c=>c.id===globalThis.__nanfengDesktopWorkState.selectedConversationId).pinned)'),true);}
 result.localPinFixture=inspect();await checkpoint();
 stage='pin-CHAT';await pin(port,'CHAT',true);stage='pin-WORK';await pin(port,'WORK',true);result.pinned=inspect();result.closes.push(await normalClose());
 stage='cold-pin';port=await start(true);await ready(port);for(const area of ['CHAT','WORK']){await switchArea(port,area);await inspectUi(port,area);assert.equal(await evaluate(port,'globalThis.__nanfengDesktopWorkState.cloudConversations[0].cloudPinned'),true);}result.coldPinned=inspect();for(const area of ['CHAT','WORK'])assert.equal(result.coldPinned[area].businessSha256,result.pinned[area].businessSha256);
 stage='unpin-WORK';await pin(port,'WORK',false);stage='unpin-CHAT';await pin(port,'CHAT',false);
 stage='cancel-WORK';await switchArea(port,'WORK');await inspectUi(port,'WORK');const before=inspect();await contextMenu(port);await clickUi(port,'.chat-context-menu [data-action="context-menu-cancel-sync"]');result.cancelUi=await emptyArea(port,'WORK');result.cancelled=inspect();assert.equal(result.cancelled.WORK.localCoreSha256,before.WORK.localCoreSha256);assert.equal(result.cancelled.CHAT.businessSha256,before.CHAT.businessSha256);assert.equal(remoteStore.has(documents[1].documentId),false);assert.equal(remoteStore.has(documents[0].documentId),true);await checkpoint();
 stage='after-cancel-read';await readMissing(port,'WORK');
 stage='remote-missing-CHAT';assert.equal(inspect().CHAT.selectedCount,1);remoteStore.delete(documents[0].documentId);await switchArea(port,'CHAT');result.missing=await readMissing(port,'CHAT');
 stage='continuation-refusal';for(const area of ['CHAT','WORK']){const count=requests.length;const response=await evaluate(port,`window.__TAURI__.core.invoke('sync_selected_desktop_conversation',{workspaceId:${JSON.stringify(result.workspaceIds[area])},conversationId:${JSON.stringify(conversationId)},continuation:true},{headers:{'x-nanfeng-data-area':${JSON.stringify(area)}}}).then(value=>({accepted:true})).catch(error=>({accepted:false,error:String(error)}))`);assert.equal(response.accepted,false);assert.ok(response.error.includes('该对话已停止云端同步，未重新上传'));assert.equal(requests.length,count);result.cases.push({kind:'continuation-refusal',area,response,httpDelta:0});}
 result.beforeClose=inspect();result.closes.push(await normalClose());stage='cold-empty';port=await start(true);await ready(port);const beforeColdRequests=requests.length;for(const area of ['CHAT','WORK']){await switchArea(port,area);await emptyArea(port,area);}result.cold=inspect();for(const area of ['CHAT','WORK']){assert.equal(result.cold[area].businessSha256,result.beforeClose[area].businessSha256);assert.equal(result.cold[area].messageCount,5);assert.equal(result.cold[area].treeAndBlocksExact,true);}assert.equal(requests.length,beforeColdRequests);result.closes.push(await normalClose());assert.equal(serverErrors.length,0);result.requests=requests;result.pids=pids;result.forcedCleanup=false;result.pass=true;await writeFile(join(root,'cloud-pin-cancel-current-native.json'),JSON.stringify(result,null,2));await writeFile(join(evidence,'native-current.json'),JSON.stringify(result,null,2)+'\n');console.log(JSON.stringify({root,pids,pass:true,normalCloses:result.closes.length,cases:result.cases.length}));
}catch(error){result.stage=stage;result.error=String(error);result.requests=requests;result.serverErrors=serverErrors;if(launch){result.failedPid=launch.pid;result.forcedCleanup=true;stop();}result.pids=pids;await writeFile(join(evidence,`native-failed-${Date.now()}.json`),JSON.stringify(result,null,2));if(root)await writeFile(join(root,`cloud-pin-cancel-failed-${randomUUID()}.json`),JSON.stringify(result,null,2));throw error;}
finally{server.closeAllConnections();await new Promise(done=>server.close(done));}
