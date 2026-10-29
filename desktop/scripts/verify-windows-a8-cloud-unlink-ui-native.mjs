import { completeLaunchReceipt } from './windows-launch-receipt.mjs';
import assert from 'node:assert/strict';
import { createHash, randomUUID } from 'node:crypto';
import { execFileSync, spawn } from 'node:child_process';
import { readFile, writeFile, stat } from 'node:fs/promises';
import http from 'node:http';
import { dirname, join, resolve, sep } from 'node:path';
import { fileURLToPath } from 'node:url';

if (process.platform !== 'win32') throw new Error('仅在 Windows 隔离候选上验收。');
const desktopRoot = resolve(dirname(fileURLToPath(import.meta.url)), '..');
assert.ok(process.argv[2], 'Explicit owned synthetic parent required');
const rootParent = resolve(process.argv[2]);
assert.ok(process.execArgv.includes('--max-old-space-size=2048'));
if (process.argv.includes('--screenshot')) throw new Error('后台验收不支持前台截图');
const receipt = JSON.parse(await readFile(join(desktopRoot, 'output/windows-acceptance/current.json'), 'utf8'));
assert.equal(receipt.identifier, 'com.nanzhufeng.ai.desktop.windowsacceptance');
const source = resolve(desktopRoot, 'output/windows-acceptance', receipt.sha256, 'NanfengAI-Windows-Acceptance.exe');
assert.equal(resolve(receipt.executable), source);
assert.equal(createHash('sha256').update(await readFile(source)).digest('hex').toUpperCase(), receipt.sha256);
const runExecutable = resolve(desktopRoot, 'output/windows-acceptance-binaries', receipt.sha256, 'nanfeng-ai-desktop-spike.exe');
const ps = (args) => execFileSync('pwsh', ['-NoProfile', ...args], { encoding: 'utf8', timeout: 60000, windowsHide: true }).trim();
const launchPowerShell = (args) => new Promise((resolveLaunch, reject) => {
  const child = spawn('pwsh', ['-NoProfile', ...args], { windowsHide: true, stdio: ['ignore', 'pipe', 'pipe'] });
  let stdout = '';
  let stderr = '';
  const timer = setTimeout(() => { child.kill(); reject(new Error(`隔离候选启动超时：${stderr}`)); }, 30000);
  child.stdout.on('data', data => {
    stdout += data.toString();
    let emitted;
    try { emitted=completeLaunchReceipt(stdout); } catch(error) { clearTimeout(timer); reject(error); return; }
    if (!emitted) return;
    clearTimeout(timer);
    readFile(emitted.launchReceipt, 'utf8').then(data=>resolveLaunch(JSON.parse(data)),reject);
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



let launch = null;
let root = '';
const pids = [];
const stop = () => {
  if (!launch) return;
  const pid = Number(launch.pid);
  const literal = value => `'${String(value).replaceAll("'", "''")}'`;
  const script = '$started=[datetime]' + literal(launch.processStartedAt) + '; $expected=' + literal(runExecutable) + '; $hash=' + literal(receipt.sha256) + '; '
    + '$p=Get-CimInstance Win32_Process -Filter "ProcessId=' + pid + '"; '
    + 'if ($p) { if ([IO.Path]::GetFullPath([string]$p.ExecutablePath) -ne $expected '
    + '-or (Get-FileHash -LiteralPath $expected -Algorithm SHA256).Hash -ne $hash) { throw "PID identity mismatch" }; '
    + '$owned=Get-Process -Id ' + pid + ' -ErrorAction Stop; if ([Math]::Abs(($owned.StartTime.ToUniversalTime()-$started.ToUniversalTime()).TotalMilliseconds) -gt 20) { throw "PID start time mismatch" }; ' 
    + 'Stop-Process -Id ' + pid + ' -Force -ErrorAction Stop }';
  ps(['-Command', script]);
  pids.push(pid);
  launch = null;
};
async function start(reopen = false) {
  const port = await reservePort();
  const args = ['-File', join(desktopRoot, 'scripts/start-windows-acceptance.ps1'), '-RootParent', rootParent,
    '-StartupMode', 'normal', '-DebugPort', String(port), '-AccountSyncTestEndpoint', `http://127.0.0.1:${servicePort}/`];
  if (reopen) args.push('-Root', root);
  launch = await launchPowerShell(args);
  assert.equal(launch.visible, false);
  assert.equal(launch.sha256, receipt.sha256);
  assert.equal(resolve(launch.executable), runExecutable);
  root = resolve(launch.root);
  assert.ok(root.startsWith(rootParent + sep));
  const marker = JSON.parse(await readFile(join(root, '.nanfeng-windows-acceptance.json'), 'utf8'));
  assert.equal(marker.identifier, receipt.identifier);
  return port;
}
async function cdp(port, method, params = {}, which = "main") {
  const tabs = await fetch(`http://127.0.0.1:${port}/json/list`,{signal:AbortSignal.timeout(10000)}).then(response => response.json());
  const tab = tabs.find(item => item.type === 'page' && item.url === (which==='island'?'http://tauri.localhost/island.html':'http://tauri.localhost/'));
  assert.ok(tab, '验收 WebView2 页面未就绪');
  const socket = new WebSocket(tab.webSocketDebuggerUrl);
  try {
    await new Promise((resolveReady, reject) => {
      socket.addEventListener('open', resolveReady, { once: true });
      socket.addEventListener('error', reject, { once: true });
    });
    return await new Promise((resolveResult, reject) => {
      const timer = setTimeout(() => reject(new Error('CDP evaluation timed out')), 30000);
      socket.addEventListener('message', event => {
        const message = JSON.parse(event.data);
        if (message.id !== 1) return;
        clearTimeout(timer);
        if (message.error || message.result?.exceptionDetails) reject(new Error(
          message.result?.exceptionDetails?.exception?.description || message.result?.exceptionDetails?.text || message.error?.message));
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
  let lastError="IPC not initialized";
  while (Date.now() < deadline) {
    try { if (await evaluate(port, 'Boolean(window.__TAURI__?.core?.invoke)')) {
      const hidden=JSON.parse(ps(['-File',join(desktopRoot,'scripts/close-windows-hidden-acceptance.ps1'),
        '-ProcessId',String(launch.pid),'-Executable',runExecutable,'-Sha256',receipt.sha256,
        '-StartedAt',launch.processStartedAt,'-InspectOnly']));
      assert.equal(hidden.visibleWindows,0);result.startupHidden=hidden;return;
    } } catch(error) { lastError=String(error); }
    await new Promise(resolveWait => setTimeout(resolveWait, 250));
  }
  throw new Error(`原生 IPC 未就绪: ${lastError}`);
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
  const clicked = await evaluate(port, `(()=>{const nodes=[...document.querySelectorAll(${JSON.stringify(selector)})].filter(n=>n.getClientRects().length && !n.disabled);if(nodes.length!==1)return {count:nodes.length};nodes[0].click();return {count:1}})()`);
  assert.equal(clicked?.count, 1, `控件必须可见、唯一且可用: ${selector}`);
}
async function normalClose() {
  const pid=Number(launch.pid);
  const quote=value=>`'${String(value).replaceAll("'", "''")}'`;
  const info=JSON.parse(ps(['-File', join(desktopRoot,'scripts/close-windows-hidden-acceptance.ps1'),
    '-ProcessId',String(pid),'-Executable',runExecutable,'-Sha256',receipt.sha256,
    '-StartedAt',launch.processStartedAt]));
  assert.equal(info.visibleWindows,0);assert.equal(info.closePosted,true);
  await until(()=>ps(['-Command',`@(Get-CimInstance Win32_Process -Filter "ProcessId=${pid}").Count`])==='0','自然关闭退出');
  await until(()=>ps(['-Command',`@(Get-CimInstance Win32_Process -Filter "Name='msedgewebview2.exe'" | Where-Object {$_.CommandLine -and $_.CommandLine.Contains(${quote(launch.webviewProfile)})}).Count`])==='0','本根WebView退出');
  pids.push(pid);launch=null;return info;
}


const documents=new Map(),requests=[],serverErrors=[];
const accountEmail='synthetic-unlink@example.invalid',access='synthetic-unlink-access';
let holdInventory=false,releaseInventory=null,pendingRead=null;
const deletes=[];let failDeleteAttempt=0,holdNextDelete=false,releaseDelete=null;
const server=http.createServer(async(request,response)=>{
 try {
  let raw='';for await(const chunk of request)raw+=chunk.toString('utf8');const body=raw?JSON.parse(raw):{},path=request.url?.split('?')[0];
  requests.push({path,documentId:body.p_document_id??null});let value;
  if(path==='/auth/v1/token'){
   assert.equal(body.auth_code,'nfai-synthetic-windows-account-code');
   value={access_token:access,refresh_token:'synthetic-unlink-refresh',expires_in:3600,user:{id:'00000000-0000-4000-8000-000000000151',email:accountEmail,user_metadata:{full_name:'合成取消同步账号'}}};
  }else{
   assert.equal(request.headers.authorization,`Bearer ${access}`);assert.equal(body.p_app_id,'com.nanzhufeng.ai');
   if(path==='/rest/v1/rpc/nanfeng_sync_read_document')value=documents.has(body.p_document_id)?[{envelope:documents.get(body.p_document_id)}]:[];
   else if(path==='/rest/v1/rpc/nanfeng_sync_commit_document'){
    const expected=documents.get(body.p_document_id)?.revision??0;assert.equal(body.p_expected_revision,expected);assert.equal(body.p_envelope.format,'nfai.sync.direct');
    documents.set(body.p_document_id,body.p_envelope);value=[{revision:expected+1,payload_hash:body.p_envelope.payloadHash}];
   }else if(path==='/rest/v1/rpc/nanfeng_sync_inventory'){
    // Capture an authentic pre-cancellation response, then delay its delivery.
    value={documentCount:documents.size,documents:[...documents.values()].map(envelope=>({envelope}))};
    if(holdInventory){holdInventory=false;const snapshot=JSON.stringify(value);await new Promise(done=>{releaseInventory=()=>{response.writeHead(200,{'content-type':'application/json'});response.end(snapshot);done();};});return;}
   }else if(path==='/rest/v1/rpc/nanfeng_sync_delete_document'){
    assert.equal(body.p_expected_revision,documents.get(body.p_document_id)?.revision);
    const attempt={documentId:body.p_document_id,status:'PENDING'};deletes.push(attempt);
    if(deletes.length===failDeleteAttempt){failDeleteAttempt=0;attempt.status='REJECTED';response.writeHead(503,{'content-type':'application/json'});response.end('{"message":"synthetic intentional delete failure"}');return;}
    if(holdNextDelete){holdNextDelete=false;await new Promise(done=>{releaseDelete=done;});}
    value=[{deleted:documents.delete(body.p_document_id)}];attempt.status='CANCELLED';
   }else throw Error(`Unexpected synthetic RPC ${path}`);
  }
  response.writeHead(200,{'content-type':'application/json'});response.end(JSON.stringify(value));
 }catch(error){serverErrors.push(String(error));response.writeHead(500,{'content-type':'application/json'});response.end('{"message":"synthetic fixture failed"}');}
});
await new Promise(done=>server.listen(0,'127.0.0.1',done));const servicePort=server.address().port;

const result={candidateSha256:receipt.sha256,backgroundOnly:true,realService:false,mode:'actual-ui'};
async function uiState(port) {
 return evaluate(port,"(()=>{const s=globalThis.__nanfengDesktopWorkState;return {selected:s?.selectedConversationId,rows:(s?.cloudConversations||[]).map(r=>r.id),dialog:s?.dialog?{kind:s.dialog.kind,submitting:!!s.dialog.submitting,stopRequested:!!s.dialog.stopRequested,count:s.dialog.items?.length,failure:s.dialog.failure}:null,batch:!!s?.batchEditing,progress:!!s?.accountSyncProgress,error:s?.error,status:s?.status};})()");
}
async function openMenu(port,id) {
 assert.equal(await evaluate(port,`(()=>{const row=document.querySelector('[data-conversation-row][data-id="${id}"][data-workspace-id]');if(!row)return false;row.dispatchEvent(new MouseEvent('contextmenu',{bubbles:true,cancelable:true}));return true;})()`),true);
 await until(()=>evaluate(port,"!!document.querySelector('[data-action=\"context-menu-delete\"]')"),'云会话实际菜单');
 assert.equal(await evaluate(port,"document.querySelector('[data-action=\"context-menu-delete\"] .chat-menu-action-label')?.textContent"),'删除云端副本');
}
async function currentLocal(port,workspaceId,area='CHAT') {
 const data=await invoke(port,area,'read_desktop_workspace',{workspaceId});
 return data.exchange.conversations.map(c=>({id:c.id,title:c.title,deleted:!!c.deleted,messages:c.messages.map(m=>({id:m.id,role:m.role,blocks:m.blocks}))})).sort((a,b)=>a.id.localeCompare(b.id));
}
async function cachedCloudIds(port) {
 return evaluate(port,"(()=>{const c=JSON.parse(localStorage.getItem('nanfeng-ai.desktop.sync-cloud-list.v1')||'null');return c?.entries?.map(r=>r.conversationId).sort()||[];})()");
}
try {
 let port=await start();result.root=root;await ready(port);result.phase='seed';
 const workspaceId=(await invoke(port,'CHAT','ensure_desktop_local_workspace')).summary.id;
 const ids=[],seedIntents=[];
 for(let i=0;i<6;i++){
  const seedIntent=randomUUID();seedIntents.push(seedIntent);
  const id=(await invoke(port,'CHAT','mutate_desktop_domain',{args:{intentId:seedIntent,workspaceId,entity:'conversation',action:'create',fields:{title:`合成云取消会话${i}`,firstMessage:`SYNTHETIC_UNLINK_LOCAL_BODY_${i}`,projectId:null}}})).objectId;
  ids.push(id);await invoke(port,'CHAT','set_desktop_conversation_favorite',{workspaceId,conversationId:id,favorite:true});
 }
 await invoke(port,'CHAT','sign_in_desktop_google_account');assert.equal((await invoke(port,'CHAT','read_desktop_account_sync')).email,accountEmail);
 for(const id of ids)assert.equal((await invoke(port,'CHAT','sync_selected_desktop_conversation',{workspaceId,conversationId:id})).status,'SYNCED');
 const workWorkspace=(await invoke(port,'WORK','ensure_desktop_local_workspace')).summary.id;
 const workId=(await invoke(port,'WORK','mutate_desktop_domain',{args:{intentId:seedIntents[0],workspaceId:workWorkspace,entity:'conversation',action:'create',fields:{title:'WORK同ID对照保留',firstMessage:'SYNTHETIC_WORK_SAME_ID_BODY',projectId:null}}})).objectId;
 assert.equal(workId,ids[0]);await invoke(port,'WORK','set_desktop_conversation_favorite',{workspaceId:workWorkspace,conversationId:workId,favorite:true});
 await invoke(port,'WORK','sign_in_desktop_google_account');assert.equal((await invoke(port,'WORK','sync_selected_desktop_conversation',{workspaceId:workWorkspace,conversationId:workId})).status,'SYNCED');
 const workBefore=await currentLocal(port,workWorkspace,'WORK');
 assert.equal(documents.size,7);const localBefore=await currentLocal(port,workspaceId);const favoriteBefore=(await invoke(port,'CHAT','read_desktop_favorite_conversation_ids',{workspaceId})).sort();assert.equal(favoriteBefore.length,6);
 await cdp(port,'Page.reload',{ignoreCache:true});await ready(port);
 await until(()=>evaluate(port,"!!document.querySelector('[data-action=\"show-settings\"]')"),'设置入口');await clickUi(port,'[data-action="show-settings"]');
 await until(()=>evaluate(port,"!!document.querySelector('[data-action=\"open-settings-page\"][data-page=\"account\"]')"),'账号页入口');await clickUi(port,'[data-action="open-settings-page"][data-page="account"]');
 await until(()=>evaluate(port,"!!document.querySelector('[data-action=\"load-account-cloud-documents\"]')"),'读取云列表入口');await clickUi(port,'[data-action="load-account-cloud-documents"]');
 await until(async()=>{const s=await uiState(port);return s.rows.length===6&&!s.progress;},'真实云列表回读');result.phase='cloud-list';
 await clickUi(port,`[data-action="select-chat"][data-id="${ids[0]}"][data-workspace-id]`);await until(async()=>(await uiState(port)).selected===ids[0],'打开本机已有云会话');
 await openMenu(port,ids[0]);await clickUi(port,'[data-action="context-menu-delete"]');
 assert.equal(await evaluate(port,"document.querySelector('.dialog h2')?.textContent"),'取消云同步？');
 assert.match(await evaluate(port,"document.querySelector('.dialog')?.textContent||''"),/本地对话与收藏保留/);
 await clickUi(port,'[data-action="confirm-conversation-delete"]');await until(async()=>{const s=await uiState(port);return s.rows.length===5&&!s.dialog;},'单个取消实际确认回读');
 assert.equal((await uiState(port)).selected,ids[0]);assert.equal(documents.size,6);assert.deepEqual(await currentLocal(port,workspaceId),localBefore);assert.deepEqual((await invoke(port,'CHAT','read_desktop_favorite_conversation_ids',{workspaceId})).sort(),favoriteBefore);assert.equal((await cachedCloudIds(port)).length,5);result.singleCancelPreservedLocal=true;result.phase='single';
 // A new explicit sync is permitted after cancellation. A fresh cloud read
 // must see it; the direct menu action then uses the same cancellation owner.
 assert.equal((await invoke(port,'CHAT','sync_selected_desktop_conversation',{workspaceId,conversationId:ids[0]})).status,'SYNCED');
 assert.equal(documents.size,7);await clickUi(port,'[data-action="load-account-cloud-documents"]');
 await until(async()=>{const s=await uiState(port);return s.rows.length===6&&!s.progress;},'显式重新同步后的新云列表');
 await openMenu(port,ids[0]);await clickUi(port,'[data-action="context-menu-cancel-sync"]');
 await until(async()=>{const s=await uiState(port);return s.rows.length===5&&!s.dialog&&s.status?.includes('已取消同步');},'菜单直接取消回读');
 assert.equal(documents.size,6);assert.deepEqual(await currentLocal(port,workspaceId),localBefore);assert.deepEqual((await invoke(port,'CHAT','read_desktop_favorite_conversation_ids',{workspaceId})).sort(),favoriteBefore);assert.equal((await cachedCloudIds(port)).length,5);result.explicitResyncAndDirectCancel=true;
 await clickUi(port,'[data-action="toggle-conversation-batch-edit"]');await clickUi(port,'[data-action="toggle-batch-select-all"]');
 assert.match(await evaluate(port,"document.querySelector('[data-action=\"request-batch-conversation-delete\"]')?.textContent||''"),/取消同步 5/);
 await clickUi(port,'[data-action="request-batch-conversation-delete"]');
 assert.equal((await uiState(port)).dialog.count,5);assert.match(await evaluate(port,"document.querySelector('.dialog h2')?.textContent||''"),/取消.*同步/);
 failDeleteAttempt=deletes.length+2;await clickUi(port,'[data-action="confirm-conversation-batch-delete"]');
 await until(async()=>{const s=await uiState(port);return s.dialog&&!s.dialog.submitting&&s.dialog.count===4&&s.rows.length===4&&/已取消 1 个/.test(s.dialog.failure);},'批量部分失败只移除已确认项');
 assert.equal(documents.size,5);assert.equal((await cachedCloudIds(port)).length,4);assert.deepEqual(await currentLocal(port,workspaceId),localBefore);result.partialConfirmedOnly=true;result.phase='partial';
 const attemptsBeforeStop=deletes.length;holdNextDelete=true;await clickUi(port,'[data-action="confirm-conversation-batch-delete"]');await until(()=>!!releaseDelete,'所属删除正在等待模拟云');
 await clickUi(port,'[data-action="stop-conversation-cloud-unlink"]');let stopped=await uiState(port);assert.equal(stopped.dialog.submitting,true);assert.equal(stopped.dialog.stopRequested,true);assert.equal(stopped.rows.length,4);assert.equal(documents.size,5);
 releaseDelete();releaseDelete=null;
 await until(async()=>{const s=await uiState(port);return s.dialog&&!s.dialog.submitting&&s.dialog.count===3&&s.rows.length===3&&/已停止后续/.test(s.dialog.failure);},'等在途真实回执后停止后续');
 assert.equal(deletes.length,attemptsBeforeStop+1);assert.equal(documents.size,4);assert.equal((await cachedCloudIds(port)).length,3);result.stoppedRemainderAfterInflightReceipt=true;result.phase='stopped';
 const attemptsBeforeRetry=deletes.length;await clickUi(port,'[data-action="confirm-conversation-batch-delete"]');
 await until(async()=>{const s=await uiState(port);return !s.dialog&&!s.batch&&s.rows.length===0;},'仅剩余条目显式重试');
 assert.equal(deletes.length,attemptsBeforeRetry+3);assert.equal(documents.size,1);assert.equal((await invoke(port,'CHAT','read_desktop_account_sync')).selectedConversationCount,0);assert.deepEqual(await cachedCloudIds(port),[]);
 assert.equal((await invoke(port,'WORK','read_desktop_account_sync')).selectedConversationCount,1);assert.deepEqual(await currentLocal(port,workWorkspace,'WORK'),workBefore);assert.deepEqual(await invoke(port,'WORK','read_desktop_favorite_conversation_ids',{workspaceId:workWorkspace}),[workId]);result.workSameIdUnaffected=true;
 assert.deepEqual(await currentLocal(port,workspaceId),localBefore);assert.deepEqual((await invoke(port,'CHAT','read_desktop_favorite_conversation_ids',{workspaceId})).sort(),favoriteBefore);assert.equal((await uiState(port)).selected,ids[0]);result.remainingOnlyRetry=true;result.localSixAndFavoritesIntact=true;result.phase='completed';
 result.windows=JSON.parse(ps(['-File',join(desktopRoot,'scripts/inspect-windows-island-acceptance.ps1'),'-ProcessId',String(launch.pid),'-Executable',runExecutable,'-Sha256',receipt.sha256,'-StartedAt',launch.processStartedAt]));assert.equal(result.windows.foregroundOwned,false);assert.ok(result.windows.windows.every(w=>!w.visible));
 result.close=await normalClose();port=await start(true);await ready(port);
 assert.equal((await invoke(port,'CHAT','read_desktop_account_sync')).selectedConversationCount,0);assert.deepEqual(await currentLocal(port,workspaceId),localBefore);assert.deepEqual((await invoke(port,'CHAT','read_desktop_favorite_conversation_ids',{workspaceId})).sort(),favoriteBefore);assert.deepEqual(await cachedCloudIds(port),[]);assert.equal(documents.size,1);
 assert.equal((await invoke(port,'WORK','read_desktop_account_sync')).selectedConversationCount,1);assert.deepEqual(await currentLocal(port,workWorkspace,'WORK'),workBefore);assert.deepEqual(await invoke(port,'WORK','read_desktop_favorite_conversation_ids',{workspaceId:workWorkspace}),[workId]);result.coldWorkSameIdUnaffected=true;
 result.coldSixAndFavoritesIntact=true;result.coldClose=await normalClose();assert.equal(serverErrors.length,0);result.pass=true;result.phase='closed';
}catch(error){result.pass=false;result.error=String(error);process.exitCode=1;if(launch){try{result.page=await uiState(launch.debugPort);}catch{}}}
finally{
 if(releaseDelete){releaseDelete();releaseDelete=null;}if(releaseInventory){releaseInventory();releaseInventory=null;}
 if(launch){try{result.failureClose=await normalClose();}catch{result.forcedCleanup=true;try{stop();}catch(error){result.cleanupError=String(error);}}}
 server.closeAllConnections();await new Promise(done=>server.close(done));result.deletes=deletes;result.requests=requests;result.serverErrors=serverErrors;result.pids=pids;
 if(root)await writeFile(join(root,'a8-cloud-unlink-ui-native-evidence.json'),JSON.stringify(result,null,2),{flag:'wx'});
}
console.log(JSON.stringify({root,pass:result.pass,error:result.error,phase:result.phase,deleteAttempts:deletes.length,pids}));
