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
const vector=vectors.find(v=>v.platform==='ANDROID'&&v.area==='CHAT');
function remoteDocument(index){
 const id=index===0?vector.conversationId:`synthetic-cloud-valid-${index}`;
 const documentId='conversation-'+sha256(Buffer.from(id)).slice(0,40);
 const content=structuredClone(vector.content);content.title=`SYNTHETIC_CLOUD_VALID_${index}`;
 content.nodes[0].text=[`SYNTHETIC_CLOUD_VALID_BODY_${index}`];
 const payload={format:'nfai.sync.payload',protocolVersion:1,schemaVersion:1,appId:'com.nanzhufeng.ai',documentId,revision:1,
 records:[{kind:'conversation',id,revision:1,classification:'NORMAL',content}]};
 const plain=Buffer.from(canonicalJson(payload));
 return {format:'nfai.sync.direct',protocolVersion:1,schemaVersion:1,appId:payload.appId,documentId,revision:1,
 payloadHash:sha256(plain),payloadByteCount:plain.length,payload};
}
let documents=[remoteDocument(0)];
const server=http.createServer(async(request,response)=>{
 try{
  const chunks=[];for await(const chunk of request)chunks.push(chunk);const bytes=Buffer.concat(chunks);
  const body=bytes.length?JSON.parse(bytes):{},path=request.url?.split('?')[0];
  requests.push({path,bodyBytes:bytes.length,bodySha256:sha256(bytes)});let value;
  if(path==='/auth/v1/token'){
   assert.equal(body.auth_code,'nfai-synthetic-windows-account-code');
   value={access_token:syntheticToken,refresh_token:'synthetic-cloud-atomic-refresh',expires_in:3600,
    user:{id:'00000000-0000-4000-8000-000000000118',email:accountEmail,user_metadata:{full_name:'合成恢复事务账号'}}};
  }else{
   assert.equal(request.headers.authorization,`Bearer ${syntheticToken}`);assert.equal(body.p_app_id,'com.nanzhufeng.ai');
   assert.equal(path,'/rest/v1/rpc/nanfeng_sync_inventory','restore cannot upload or delete');
   value={documentCount:documents.length,documents:documents.map(envelope=>({envelope}))};
  }
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
    '-StartupMode', 'normal', '-Visible', '-DebugPort', String(port),'-AccountSyncTestEndpoint',`http://127.0.0.1:${serverPort}`];
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



const result={candidateSha256:receipt.sha256,realService:false,cases:[],closes:[]};let stage='start';
function check(mode){return JSON.parse(execFileSync('python',[join(desktopRoot,'scripts/check-current-cloud-valid-batch-synthetic.py'),root,mode],{encoding:'utf8',timeout:30000}));}
async function uiUntil(port,expression,label){return until(()=>evaluate(port,expression),label);}
async function openAccount(port){
 if(await evaluate(port,'globalThis.__nanfengDesktopWorkState.pane==="settings"&&globalThis.__nanfengDesktopWorkState.settingsSection==="account"&&Boolean(document.querySelector("[data-action=load-account-cloud-documents]"))'))return;
 await uiUntil(port,'Boolean(document.querySelector("[data-action=show-settings]"))','settings entry after reload');
 await clickUi(port,'[data-action="show-settings"]');await uiUntil(port,'Boolean(document.querySelector("[data-action=open-settings-page][data-page=account]"))','account settings');
 await clickUi(port,'[data-action="open-settings-page"][data-page="account"]');await uiUntil(port,'Boolean(document.querySelector("[data-action=load-account-cloud-documents]"))','cloud read button');
}
async function verifyColdList(port,count){
 await uiUntil(port,'globalThis.__nanfengDesktopWorkState.runtimeInfo?.dataAreasReady','cold area projections');
 await uiUntil(port,`globalThis.__nanfengDesktopWorkState.accountSync?.email==="cloud-atomic@example.invalid" && globalThis.__nanfengDesktopWorkState.cloudConversations.length===${count}`,'complete cold cloud list');
 const inspection=await evaluate(port,`(async()=>{
  const state=globalThis.__nanfengDesktopWorkState,rows=state.cloudConversations;
  const cache=JSON.parse(localStorage.getItem('nanfeng-ai.desktop.sync-cloud-list.v1')||'null');
  const workspaceIds=[...new Set(rows.map(row=>row.workspaceId))];
  const batch=await window.__TAURI__.core.invoke('read_desktop_cloud_workspace_projections',{workspaceIds},{headers:{'x-nanfeng-data-area':'CHAT'}});
  const byWorkspace=new Map(batch.projections.map(item=>[item.workspaceId,item]));
  const bodyAndFavoriteMatch=rows.every(row=>{
   const projection=byWorkspace.get(row.workspaceId),conversation=projection?.workspace?.exchange?.conversations?.find(item=>item.id===row.id);
   if(!conversation)return false;
   const {workspaceId,favorite,cloudPinned,pinned,...visible}=row;
   const {pinned:localPinned,...stored}=conversation;
   return JSON.stringify(visible)===JSON.stringify(stored)&&favorite===projection.favoriteIds.includes(row.id)&&pinned===cloudPinned;
  });
  return {area:state.dataArea,selectedConversationCount:state.accountSync.selectedConversationCount,
   visibleCloudRowCount:rows.length,persistedCacheEntryCount:cache?.entries?.length??null,
   cachedIdsAndPinsMatch:cache?.email===state.accountSync.email&&cache.entries.length===rows.length&&rows.every((row,i)=>{
    const entry=cache.entries[i];return entry.workspaceId===row.workspaceId&&entry.conversationId===row.id&&Boolean(entry.cloudPinned)===row.cloudPinned;
   }),bodyAndFavoriteMatch,failedWorkspaceIds:batch.failedWorkspaceIds};
 })()`);
 assert.equal(inspection.selectedConversationCount,count);assert.equal(inspection.persistedCacheEntryCount,count);
 assert.equal(inspection.cachedIdsAndPinsMatch,true);assert.equal(inspection.bodyAndFavoriteMatch,true);assert.deepEqual(inspection.failedWorkspaceIds,[]);
 return inspection;
}
async function batch(port,count){
 documents=Array.from({length:count},(_,i)=>remoteDocument(i));
 const encoded=JSON.stringify({documentCount:count,documents:documents.map(envelope=>({envelope}))});
 assert.ok(Buffer.byteLength(encoded)<=32*1024*1024);
 await writeFile(join(root,`synthetic-valid-cloud-documents-${count}.json`),JSON.stringify(documents));
 await writeFile(join(root,'synthetic-current-batch.json'),JSON.stringify({count}));
 await openAccount(port);
 const requestBefore=requests.length,started=Date.now();
 await clickUi(port,'[data-action="load-account-cloud-documents"]');await until(()=>requests.length>requestBefore,'inventory response');
 await uiUntil(port,'Boolean(globalThis.__nanfengDesktopWorkState.accountSyncProgress?.result)','valid batch completed feedback');
 const ui=await evaluate(port,'({error:globalThis.__nanfengDesktopWorkState.error,status:globalThis.__nanfengDesktopWorkState.status,progress:globalThis.__nanfengDesktopWorkState.accountSyncProgress.result,rowCount:globalThis.__nanfengDesktopWorkState.cloudConversations.length})');
 const elapsedMs=Date.now()-started;assert.equal(ui.progress.kind,'success');assert.equal(ui.rowCount,count);assert.equal(requests.length-requestBefore,1);
 await uiUntil(port,`globalThis.__nanfengDesktopWorkState.accountSync.selectedConversationCount===${count}`,'valid batch receipt projection');
 const localProjection=await evaluate(port,`(async()=>{
  const rows=globalThis.__nanfengDesktopWorkState.cloudConversations;
  const workspaceIds=[...new Set(rows.map(row=>row.workspaceId))];
  const started=performance.now();
  // Reserve two inputs for duplicate/missing probes inside the existing 10,000-input bound.
  // This is post-restore read-only verification, outside the first-UI elapsed measurement.
  const batch={projections:[],failedWorkspaceIds:[]},readBatches=[];
  for(let offset=0;offset<workspaceIds.length;offset+=9998){
   const ids=workspaceIds.slice(offset,offset+9998),inputs=[...ids,ids[0],'synthetic-missing-workspace'];
   const part=await window.__TAURI__.core.invoke('read_desktop_cloud_workspace_projections',
    {workspaceIds:inputs},{headers:{'x-nanfeng-data-area':'CHAT'}});
   if(part.projections.length!==ids.length||part.failedWorkspaceIds.length!==1||part.failedWorkspaceIds[0]!=='synthetic-missing-workspace')throw new Error('bounded read-only projection contract mismatch');
   batch.projections.push(...part.projections);batch.failedWorkspaceIds.push(...part.failedWorkspaceIds);
   readBatches.push({inputCount:inputs.length,projectionCount:part.projections.length});
  }
  batch.failedWorkspaceIds=[...new Set(batch.failedWorkspaceIds)];
  const durationMs=performance.now()-started;
  const byWorkspace=new Map(batch.projections.map(item=>[item.workspaceId,item]));
  const bodyAndFavoriteMatch=rows.every(row=>{
   const projection=byWorkspace.get(row.workspaceId),conversation=projection?.workspace?.exchange?.conversations?.find(item=>item.id===row.id);
   if(!conversation)return false;
   const {workspaceId,favorite,cloudPinned,pinned,...visible}=row;
   const {pinned:localPinned,...stored}=conversation;
   return JSON.stringify(visible)===JSON.stringify(stored)&&favorite===projection.favoriteIds.includes(row.id)&&pinned===cloudPinned;
  });
  return {scope:'post-restore-warm-read; not a decomposition of first UI read',durationMs,inputCount:readBatches.reduce((sum,item)=>sum+item.inputCount,0),readBatches,
   projectionCount:batch.projections.length,failedWorkspaceIds:batch.failedWorkspaceIds,bodyAndFavoriteMatch};
 })()`);
 assert.equal(localProjection.projectionCount,count);assert.deepEqual(localProjection.failedWorkspaceIds,['synthetic-missing-workspace']);
 assert.equal(localProjection.bodyAndFavoriteMatch,true);
 const sql=check('verify');const item={count,responseBytes:Buffer.byteLength(encoded),elapsedMs,ui,sql,localProjection};
 result.cases.push(item);
 await writeFile(join(root,'cloud-valid-batch-progress.json'),JSON.stringify(result,null,2));
 console.log(JSON.stringify({stage,count,elapsedMs,responseBytes:item.responseBytes,kind:ui.progress.kind}));return item;
}
const resumeRoot=process.argv[3]?resolve(process.argv[3]):null;
const inspectColdCache=process.argv[4]==='--inspect-cold-cache';
if(resumeRoot){
 root=resumeRoot;assert.ok(root.startsWith(rootParent+sep));stage=inspectColdCache?'cold-cache-inspection-only':'timeout-cold-only';
 try{
  result.root=root;result.beforeClose=check('verify-partial');
  const port=await start(true);await ready(port);
  assert.equal((await invoke(port,'CHAT','read_desktop_account_sync')).selectedConversationCount,result.beforeClose.restoredCount);
  if(inspectColdCache){
   await uiUntil(port,'globalThis.__nanfengDesktopWorkState.runtimeInfo?.dataAreasReady','cold area projections');
   await uiUntil(port,'globalThis.__nanfengDesktopWorkState.accountSync?.email==="cloud-atomic@example.invalid" && globalThis.__nanfengDesktopWorkState.cloudConversations.length>0','cold cached rows');
   result.coldCache=await evaluate(port,'(()=>{const cache=JSON.parse(localStorage.getItem("nanfeng-ai.desktop.sync-cloud-list.v1")||"null");return {area:globalThis.__nanfengDesktopWorkState.dataArea,selectedConversationCount:globalThis.__nanfengDesktopWorkState.accountSync.selectedConversationCount,visibleCloudRowCount:globalThis.__nanfengDesktopWorkState.cloudConversations.length,persistedCacheEntryCount:cache?.entries?.length??null}})()');
   result.scope='Cold UI/cache inspection; no inventory or restore retry; application startup may update its presentation cache. DOM counts are not physical-input evidence.';
  }
  result.cold=check('verify-partial');assert.equal(result.cold.businessSha256,result.beforeClose.businessSha256);
  result.closes.push(await normalClose());assert.equal(serverErrors.length,0);assert.equal(requests.length,0,'cold inspection must not repeat the inventory');
  result.pids=pids;result.requests=requests;result.forcedCleanup=false;
  await writeFile(join(root,inspectColdCache?'cloud-valid-batch-cold-cache-inspection.json':'cloud-valid-batch-cold-after-timeout.json'),JSON.stringify(result,null,2));
  console.log(JSON.stringify({root,candidateSha256:receipt.sha256,stage,restoredCount:result.cold.restoredCount,normalCloseCount:1,pids}));
 }catch(error){if(launch)stop();throw error;}finally{server.closeAllConnections();await new Promise(done=>server.close(done));}
}else try{
 let port=await start();result.root=root;await ready(port);await invoke(port,'CHAT','sign_in_desktop_google_account');
 result.baseline=check('baseline');await cdp(port,'Page.reload',{ignoreCache:true});await ready(port);
 await uiUntil(port,'globalThis.__nanfengDesktopWorkState.runtimeInfo?.dataAreasReady','area projections');
 stage='valid256';const first=await batch(port,256);
 result.estimated2048UpperMs=Math.ceil(first.elapsedMs*2048/256*1.3);
 if(result.estimated2048UpperMs<=20000){
  stage='valid2048';const measured=await batch(port,2048);
  result.estimated10000UpperMs=Math.ceil(measured.elapsedMs*10000/2048*1.3);
  if(result.estimated10000UpperMs<=20000){stage='valid10000';await batch(port,10000);}
  else result.capacityDeferred='Measured2048 cost does not justify attempting10000 within the existing30s observation.';
 }else result.capacityDeferred='Measured256 cost does not justify attempting2048 within the existing30s observation; preserve the successful batch and diagnose next.';
 result.beforeClose=check('verify');result.closes.push(await normalClose());
  stage='cold';port=await start(true);await ready(port);const count=result.cases.at(-1).count;
  assert.equal((await invoke(port,'CHAT','read_desktop_account_sync')).selectedConversationCount,count);
  const coldRequestCount=requests.length;result.coldCache=await verifyColdList(port,count);
  assert.equal(requests.length,coldRequestCount,'cold list projection cannot repeat the inventory');
  result.cold=check('verify');assert.equal(result.cold.businessSha256,result.beforeClose.businessSha256);
 result.closes.push(await normalClose());assert.equal(serverErrors.length,0);result.pids=pids;result.requests=requests;result.forcedCleanup=false;
 await writeFile(join(root,'cloud-valid-batch-native.json'),JSON.stringify(result,null,2));
 console.log(JSON.stringify({root,candidateSha256:receipt.sha256,counts:result.cases.map(c=>c.count),normalCloseCount:result.closes.length,pids}));
}catch(error){result.stage=stage;result.error=String(error);result.requests=requests;result.serverErrors=serverErrors;
 if(launch){result.failedPid=launch.pid;result.forcedCleanup=true;stop();}result.pids=pids;
 if(root)await writeFile(join(root,`cloud-valid-batch-failure-${randomUUID()}.json`),JSON.stringify(result,null,2));throw error;
}finally{server.closeAllConnections();await new Promise(done=>server.close(done));}
