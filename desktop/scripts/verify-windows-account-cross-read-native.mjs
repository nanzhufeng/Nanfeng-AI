import assert from 'node:assert/strict';
import { createHash } from 'node:crypto';
import { execFileSync, spawn } from 'node:child_process';
import { readFile, writeFile, unlink } from 'node:fs/promises';
import http from 'node:http';
import { dirname, join, resolve, sep } from 'node:path';
import { fileURLToPath } from 'node:url';

if (process.platform !== 'win32') throw new Error('仅供 Windows 独立候选验收');
const desktopRoot = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const rootParent = resolve(process.argv.slice(2).find(value => !value.startsWith('--')) || 'D:\\CodexTemp');
const narrowWindow = process.argv.includes('--narrow-window');
const exitDrainWork = process.argv.includes('--exit-drain-work');
const requestArea = 'CHAT';
const directMode=process.argv.includes('--direct');
const pinMode=process.argv.includes('--pin');
const pinSuccessMode=process.argv.includes('--pin-success');
const pinReturnReadMode=process.argv.includes('--pin-return-read');
assert.ok(!pinReturnReadMode || (pinMode && process.argv.includes('--hold-commit')));
const cancelMode=process.argv.includes('--cancel');
const reconcileMode=process.argv.includes('--reconcile');
assert.ok([pinMode,cancelMode,reconcileMode,directMode].filter(Boolean).length<=1);
assert.ok(!pinSuccessMode || pinMode);
const responsivenessMode=process.argv.includes('--responsiveness');
assert.ok(!responsivenessMode || directMode);
const contextWindowTokens = narrowWindow ? 1024 : 4096;
const requestedOutputTokens = narrowWindow ? 256 : 1024;
const inputBudgetTokens = contextWindowTokens - requestedOutputTokens;
const receipt = JSON.parse(await readFile(join(desktopRoot, 'output/windows-acceptance/current.json'), 'utf8'));
assert.equal(receipt.identifier, 'com.nanzhufeng.ai.desktop.windowsacceptance');
const source = resolve(desktopRoot, 'output/windows-acceptance', receipt.sha256, 'NanfengAI-Windows-Acceptance.exe');
const running = resolve(desktopRoot, 'output/windows-acceptance-binaries', receipt.sha256, 'nanfeng-ai-desktop-spike.exe');
assert.equal(resolve(receipt.executable), source);
assert.equal(createHash('sha256').update(await readFile(source)).digest('hex').toUpperCase(), receipt.sha256);
const quote = value => `'${String(value).replaceAll("'", "''")}'`;
const ps = script => execFileSync('pwsh', ['-NoProfile', '-Command', script], { encoding: 'utf8', timeout: 60000 }).trim();
const listen = async server => new Promise(done => server.listen(0, '127.0.0.1', done));
const reservePort = async () => {
  const server = http.createServer();
  await listen(server);
  const port = server.address().port;
  await new Promise(done => server.close(done));
  return port;
};
const requests = [];
let committedEnvelope=null;let cloudListEnvelope=null;let malformedReadback=false;let corruptNextCommitReadback=false;
let held = null; let holdRead=false; let holdCommit=false; let newAccount=false;
const commitMode=process.argv.includes('--hold-commit');
let invalidIdentity=false;
const canonical=value=>Array.isArray(value)?`[${value.map(canonical).join(',')}]`:value&&typeof value==='object'?`{${Object.keys(value).sort().map(key=>`${JSON.stringify(key)}:${canonical(value[key])}`).join(',')}}`:JSON.stringify(value);
const fixture=JSON.parse(await readFile(new URL('../../protocol/fixtures/data-area-sync-v1.json',import.meta.url),'utf8')).find(item=>item.platform==='DESKTOP'&&item.area==='CHAT');
const usage=(amount,source)=>({modelId:'synthetic-model',modelDisplayName:'Synthetic Model',inputTokens:12,outputTokens:4,totalTokens:16,cachedInputTokens:null,reasoningTokens:null,costPriceVersion:'synthetic-v1',costCurrencyCode:'CNY',costTotalMicros:amount,costSource:source});
fixture.content.currentLeafId='answer';
fixture.content.messages.push({id:'answer',parentId:'m1',ordinal:1,role:'assistant',delivery:'COMPLETE',createdAt:'1970-01-01T00:00:02Z',revision:1,blocks:[{kind:'TEXT',text:'Synthetic completed answer'}],modelUsage:usage(18700,'PROVIDER_RESPONSE')});
let wireRevision=1; let semanticRevision=1;
const makeEnvelope=()=>{
 const payload={format:'nfai.sync.payload',protocolVersion:1,schemaVersion:1,appId:'com.nanzhufeng.ai',documentId:fixture.documentId,revision:wireRevision,records:[{kind:'conversation',id:fixture.conversationId,revision:semanticRevision,classification:'NORMAL',content:fixture.content}]};
 const bytes=Buffer.from(canonical(payload));
 return {format:'nfai.sync.direct',protocolVersion:1,schemaVersion:1,appId:payload.appId,documentId:payload.documentId,revision:wireRevision,payloadHash:createHash('sha256').update(bytes).digest('hex'),payloadByteCount:bytes.length,payload};
};
const provider=http.createServer(async(request,response)=>{
 const chunks=[];for await(const chunk of request)chunks.push(chunk);
 const body=JSON.parse(Buffer.concat(chunks).toString('utf8') || '{}');
 const path=request.url?.split('?')[0];requests.push({path,identity:request.headers.authorization==='Bearer synthetic-original-access'?'OLD':'NEW'});
 if(path==='/auth/v1/token'){
  assert.equal(body.auth_code,'nfai-synthetic-windows-account-code');
  response.writeHead(200,{'content-type':'application/json'});
  response.end(JSON.stringify({access_token:newAccount?'synthetic-new-access':'synthetic-original-access',refresh_token:'synthetic-refresh',expires_in:3600,user:{id:newAccount?'00000000-0000-4000-8000-000000000072':'00000000-0000-4000-8000-000000000071',email:newAccount?'new-owner@example.invalid':'identity-owner@example.invalid',user_metadata:{full_name:'合成身份校验'}}}));
 }else if(path==='/rest/v1/rpc/nanfeng_sync_read_document'){
 const send=()=>{const envelope=body.p_document_id==='cloud-conversation-list-v1'?cloudListEnvelope:committedEnvelope;response.writeHead(200,{'content-type':'application/json'});response.end(JSON.stringify(envelope?{envelope:malformedReadback?{format:'unknown-format',revision:envelope.revision,payloadHash:envelope.payloadHash}:envelope}:{missing:true}));};if(holdRead){held=send}else{send()}
 }else if(path==='/rest/v1/rpc/nanfeng_sync_commit_document'){

 const envelope=body.p_envelope;
 if(body.p_document_id==='cloud-conversation-list-v1')cloudListEnvelope=envelope;else committedEnvelope=envelope;
 if(corruptNextCommitReadback){malformedReadback=true;corruptNextCommitReadback=false;}
 const send=()=>{response.writeHead(200,{'content-type':'application/json'});response.end(JSON.stringify({revision:envelope.revision,payload_hash:envelope.payloadHash}));};if(holdCommit){held=send}else{send()}
 }else{const envelopes=[committedEnvelope || makeEnvelope(),...(cloudListEnvelope?[cloudListEnvelope]:[])];response.writeHead(200,{'content-type':'application/json'});response.end(JSON.stringify({documentCount:envelopes.length,documents:envelopes.map(envelope=>({envelope}))}));}
});
await listen(provider);
const providerPort=provider.address().port;
let launch = null;
let root = '';
const stoppedPids = [];
async function start(reopen = false) {
  const debugPort = await reservePort();
  const args = ['-NoProfile', '-File', join(desktopRoot, 'scripts/start-windows-acceptance.ps1'),
    '-RootParent', rootParent, '-StartupMode', 'normal', '-Visible', '-DebugPort', String(debugPort),
    '-AccountSyncTestEndpoint', `http://127.0.0.1:${providerPort}`];
  if (reopen) args.push('-Root', root);
  const result = await new Promise((done, fail) => {
    const child = spawn('pwsh', args, { stdio: ['ignore', 'pipe', 'pipe'] });
    let output = '';
    let error = '';
    const timer = setTimeout(() => { child.kill(); fail(new Error(`启动超时：${error}`)); }, 30000);
    child.stdout.on('data', chunk => {
      output += chunk.toString();
      const line = output.split(/\r?\n/).slice(0,-1).find(value => value.trim().startsWith('{') && value.includes('"pid"'));
      if (!line) return;
      clearTimeout(timer);
      try { done(JSON.parse(line)); } catch (cause) { fail(cause); }
      child.stdout.destroy(); child.stderr.destroy(); child.unref();
    });
    child.stderr.on('data', chunk => { error += chunk.toString(); });
    child.on('error', cause => { clearTimeout(timer); fail(cause); });
    child.on('exit', code => { if (!output.includes('"pid"')) { clearTimeout(timer); fail(new Error(`启动失败 ${code}：${error}`)); } });
  });
  assert.equal(result.sha256, receipt.sha256);
  assert.equal(resolve(result.executable), running);
  launch = result;
  root = resolve(result.root);
  assert.ok(root.startsWith(rootParent + sep));
  assert.equal(JSON.parse(await readFile(join(root, '.nanfeng-windows-acceptance.json'), 'utf8')).identifier, receipt.identifier);
  return debugPort;
}
function stop() {
  if (!launch) return;
  const pid = Number(launch.pid);
  ps(`$p=Get-CimInstance Win32_Process -Filter "ProcessId=${pid}"; if ($p) { if ([IO.Path]::GetFullPath([string]$p.ExecutablePath) -ne ${quote(running)} -or (Get-FileHash -LiteralPath ${quote(running)} -Algorithm SHA256).Hash -ne ${quote(receipt.sha256)}) { throw 'PID identity mismatch' }; Stop-Process -Id ${pid} -Force -ErrorAction Stop }`);
  stoppedPids.push(pid);
  launch = null;
}
async function cdp(port, method, params = {}) {
  const tabs = await fetch(`http://127.0.0.1:${port}/json/list`).then(response => response.json());
  const tab = tabs.find(item => item.type === 'page' && item.url === 'http://tauri.localhost/');
  assert.ok(tab, 'WebView2 页面未就绪');
  const socket = new WebSocket(tab.webSocketDebuggerUrl);
  try {
    await new Promise((done, fail) => { socket.addEventListener('open', done, { once: true }); socket.addEventListener('error', fail, { once: true }); });
    return await new Promise((done, fail) => {
      const timer = setTimeout(() => fail(new Error(`${method} 超时`)), 30000);
      socket.addEventListener('message', event => {
        const reply = JSON.parse(event.data);
        if (reply.id !== 1) return;
        clearTimeout(timer);
        if (reply.error || reply.result?.exceptionDetails) fail(new Error(reply.result?.exceptionDetails?.text || reply.error?.message));
        else done(reply.result?.result?.value);
      });
      socket.send(JSON.stringify({ id: 1, method, params }));
    });
  } finally { socket.close(); }
}
const evaluate = (port, expression) => cdp(port, 'Runtime.evaluate', { expression, awaitPromise: true, returnByValue: true });
async function invoke(port, command, args = {}) {
  const value = await evaluate(port, `window.__TAURI__.core.invoke(${JSON.stringify(command)},${JSON.stringify(args)},${JSON.stringify({ headers: { 'x-nanfeng-data-area': requestArea } })}).catch(error=>({__error:String(error)}))`);
  if (value?.__error) throw new Error(`${command}: ${value.__error}`);
  return value;
}
async function until(read, label, timeout = 30000) {
  const deadline = Date.now() + timeout;
  while (Date.now() < deadline) {
    try { const value = await read(); if (value) return value; } catch { /* WebView reload. */ }
    await new Promise(done => setTimeout(done, 150));
  }
  throw new Error(`${label} 未出现`);
}
try{
 committedEnvelope=makeEnvelope();
 let port=await start();console.log(JSON.stringify({phase:'started',root,pid:Number(launch.pid),candidateSha256:receipt.sha256}));
 await until(()=>evaluate(port,'Boolean(window.__TAURI__?.core?.invoke && globalThis.__nanfengDesktopWorkState?.runtimeInfo?.dataAreasReady)'),'原生就绪');
 const first=await invoke(port,'sign_in_desktop_google_account');assert.equal(first.email,'identity-owner@example.invalid');
 const list=await invoke(port,'restore_all_desktop_cloud_conversations');assert.equal(list.restored.length,1);
 const restored=list.restored[0];
 const initial=await invoke(port,'sync_selected_desktop_conversation',{workspaceId:restored.workspaceId,conversationId:restored.conversationId,continuation:false});assert.equal(initial.status,'SYNCED');
 await invoke(port,'set_desktop_periodic_sync',{enabled:true});
 const snapshot=()=>JSON.parse(execFileSync('python',['-c',"import sqlite3,json,sys;c=sqlite3.connect(sys.argv[1]);r=c.execute('SELECT remote_revision,payload_hash,last_synced_at_ms FROM desktop_selected_conversation_sync').fetchone();w=json.loads(c.execute('SELECT exchange_json FROM workspace_exchange WHERE workspace_id=?',(sys.argv[2],)).fetchone()[0]);print(json.dumps({'receipt':r,'conversation':w['conversations'][0],'jobs':c.execute('SELECT stage FROM desktop_sync_jobs').fetchall()}));c.close()",join(root,'workspace.sqlite3'),restored.workspaceId],{encoding:'utf8'}));
 const probe=async()=>{
  if(cancelMode || reconcileMode){
   const command=cancelMode?'cancel_desktop_conversation_sync':'reconcile_desktop_conversation_sync';
   return evaluate(port,`window.__TAURI__.core.invoke(${JSON.stringify(command)},{workspaceId:${JSON.stringify(restored.workspaceId)},conversationId:${JSON.stringify(restored.conversationId)}},{headers:{'x-nanfeng-data-area':'CHAT'}}).then(value=>({ok:true,value}),error=>({ok:false,error:String(error)}))`);
  }
  if(pinMode){
   return evaluate(port,`window.__TAURI__.core.invoke('set_desktop_cloud_list_pinned',{conversationId:${JSON.stringify(restored.conversationId)},pinned:true},{headers:{'x-nanfeng-data-area':'CHAT'}}).then(value=>({ok:true,value}),error=>({ok:false,error:String(error)}))`);
  }
  if(directMode){
   return evaluate(port,`window.__TAURI__.core.invoke('sync_selected_desktop_conversation',{workspaceId:${JSON.stringify(restored.workspaceId)},conversationId:${JSON.stringify(restored.conversationId)},continuation:true},{headers:{'x-nanfeng-data-area':'CHAT'}}).then(value=>({ok:true,value}),error=>({ok:false,error:String(error)}))`);
  }
  await unlink(join(root,'.account-periodic-probe-completed')).catch(e=>{if(e.code!=='ENOENT')throw e});
  await writeFile(join(root,'.account-periodic-probe-arm'),'periodic');
  await until(()=>readFile(join(root,'.account-periodic-probe-completed'),'utf8').then(v=>v==='periodic').catch(()=>false),'生产周期执行完成',45000);
 };
 if(reconcileMode){
  await invoke(port,'set_desktop_conversation_favorite',{workspaceId:restored.workspaceId,conversationId:restored.conversationId,favorite:true});
  corruptNextCommitReadback=true;
  const unknown=await invoke(port,'sync_selected_desktop_conversation',{workspaceId:restored.workspaceId,conversationId:restored.conversationId,continuation:false});
  assert.equal(unknown.status,'UNKNOWN');
  malformedReadback=false;
  assert.ok(snapshot().jobs.some(job=>job[0]==='UNKNOWN'));
 }
 let beforeSnapshot=snapshot();
 if(pinSuccessMode){
  const pin=await invoke(port,'set_desktop_cloud_list_pinned',{conversationId:restored.conversationId,pinned:true});
  assert.deepEqual(pin,[restored.conversationId]);assert.deepEqual(snapshot(),beforeSnapshot);
  assert.deepEqual(cloudListEnvelope.payload.records[0].content.pinnedConversationIds,pin);
  stop();port=await start(true);await until(()=>evaluate(port,'Boolean(window.__TAURI__?.core?.invoke && globalThis.__nanfengDesktopWorkState?.runtimeInfo?.dataAreasReady)'),'云端置顶冷启');
  assert.deepEqual(snapshot(),beforeSnapshot);
  const unpin=await invoke(port,'set_desktop_cloud_list_pinned',{conversationId:restored.conversationId,pinned:false});
  assert.deepEqual(unpin,[]);assert.equal(cloudListEnvelope.revision,2);assert.deepEqual(snapshot(),beforeSnapshot);
  console.log(JSON.stringify({phase:'cloud-list-pin-success-cold-unpin',root,candidateSha256:receipt.sha256,localSnapshotUnchanged:true,cloudListRevision:2,commitRequests:requests.filter(x=>x.path==='/rest/v1/rpc/nanfeng_sync_commit_document').length,layer:'NATIVE_CLOUD_LIST_PIN_CALLER'}));
 }else{
 committedEnvelope.revision+=1;committedEnvelope.payload.revision=committedEnvelope.revision;
 const remoteRecord=committedEnvelope.payload.records[0];remoteRecord.revision+=1;
 remoteRecord.content.messages.push({id:'old-account-late-message',parentId:'answer',ordinal:2,role:'user',delivery:'COMPLETE',createdAt:'1970-01-01T00:00:04Z',revision:1,blocks:[{kind:'TEXT',text:'Synthetic old account addition'}]});
 remoteRecord.content.currentLeafId='old-account-late-message';
 const bytes=Buffer.from(canonical(committedEnvelope.payload));committedEnvelope.payloadByteCount=bytes.length;committedEnvelope.payloadHash=createHash('sha256').update(bytes).digest('hex');
 holdRead=!commitMode;holdCommit=commitMode;
 const completion=probe().catch(error=>({transportError:String(error)}));
 await until(()=>held,'旧账号生产周期云读挂起',40000);
 console.log(JSON.stringify({phase:'cloud-request-held',root,directMode,commitMode}));
 if(responsivenessMode){
  const began=Date.now();const releaseTimer=setTimeout(()=>{holdRead=false;holdCommit=false;held?.();held=null;},2000);
  let latency;
  try{assert.equal(await evaluate(port,'1+1'),2);latency=Date.now()-began;}finally{clearTimeout(releaseTimer);holdRead=false;holdCommit=false;held?.();held=null;}
  const completed=await completion;
  console.log(JSON.stringify({phase:'window-responsiveness-during-direct-network',root,candidateSha256:receipt.sha256,latencyMilliseconds:latency,providerHoldMilliseconds:2000,completed,layer:'NATIVE_WINDOW_THREAD_RESPONSIVENESS'}));
  assert.ok(latency<1000,'显式同步网络等待阻塞窗口线程');
 }else{
 const safeRelease=setTimeout(()=>{console.log(JSON.stringify({phase:'bounded-fixture-release',root}));holdRead=false;holdCommit=false;held?.();held=null;},12000);
 try{
 if(commitMode && !pinMode){beforeSnapshot=snapshot();assert.ok(beforeSnapshot.jobs.some(j=>j[0]==='COMMITTING'));}
 console.log(JSON.stringify({phase:'signout-start',root}));
 await invoke(port,'sign_out_desktop_google_account');
 console.log(JSON.stringify({phase:'signout-completed',root}));
 newAccount=true;
 const changedAccount=await invoke(port,'sign_in_desktop_google_account');assert.equal(changedAccount.email,'new-owner@example.invalid');
 console.log(JSON.stringify({phase:'new-account-signin-completed',root}));
 const beforeRelease=requests.length;
 assert.ok(held,'fixture released before account switch; not a valid cross-account test');
 holdRead=false;holdCommit=false;held();held=null;clearTimeout(safeRelease);
 const completed=await completion;
 if(directMode || pinMode || cancelMode || reconcileMode){assert.equal(completed.ok,false,'跨账号云端操作不应报成功');console.log(JSON.stringify({phase:cancelMode?'cancel-caller-return':reconcileMode?'reconcile-caller-return':pinMode?'pin-caller-return':'direct-caller-return',completed}));}
 const after=snapshot();
 console.log(JSON.stringify({phase:'cross-account-periodic-late-read',root,candidateSha256:receipt.sha256,lateMessage:after.conversation.messages.some(m=>m.id==='old-account-late-message'),commitRequests:requests.filter(x=>x.path==='/rest/v1/rpc/nanfeng_sync_commit_document').length,postReleaseRequests:requests.slice(beforeRelease),receiptRevision:after.receipt[0],layer:cancelMode?'NATIVE_CANCEL_SYNC_CALLER':reconcileMode?'NATIVE_EXPLICIT_RECONCILE_CALLER':pinMode?'NATIVE_CLOUD_LIST_PIN_CALLER':directMode?'NATIVE_EXPLICIT_SELECTED_SYNC_CALLER':'ONE_SHOT_PRODUCTION_PERIODIC_CALLER_NOT_12H_TIMER'}));
 const expectedCommits=commitMode || reconcileMode?2:1;
 assert.equal(requests.filter(x=>x.path==='/rest/v1/rpc/nanfeng_sync_commit_document').length,expectedCommits,'账号变化后不得继续旧账号提交');
 assert.deepEqual(after,beforeSnapshot,'旧账号迟到读取不得应用正文/receipt/jobs');
 stop();port=await start(true);await until(()=>evaluate(port,'Boolean(window.__TAURI__?.core?.invoke && globalThis.__nanfengDesktopWorkState?.runtimeInfo?.dataAreasReady)'),'跨账号冷启');
 const cold=await invoke(port,'read_desktop_account_sync');assert.equal(cold.email,'new-owner@example.invalid');assert.equal(cold.selectedConversationCount,0);
 assert.deepEqual(snapshot(),beforeSnapshot);
 console.log(JSON.stringify({phase:'cross-account-cold',root,candidateSha256:receipt.sha256,email:cold.email,authRequests:requests.filter(x=>x.path==='/auth/v1/token').length}));
 if(pinReturnReadMode){
  await invoke(port,'sign_out_desktop_google_account');newAccount=false;
  assert.equal((await invoke(port,'sign_in_desktop_google_account')).email,first.email);
  const listed=await invoke(port,'restore_all_desktop_cloud_conversations');
  assert.equal(listed.failedCount,0);assert.equal(listed.cloudListPresentationPresent,true);
  assert.deepEqual(listed.cloudPinnedConversationIds,[restored.conversationId]);
  assert.equal(requests.filter(x=>x.path==='/rest/v1/rpc/nanfeng_sync_commit_document').length,expectedCommits);
  console.log(JSON.stringify({phase:'pin-unknown-original-account-explicit-list-read',root,candidateSha256:receipt.sha256,pinnedIds:listed.cloudPinnedConversationIds,commitRequests:expectedCommits,noResubmit:true}));
 }
 if(commitMode && !pinMode){
  await invoke(port,'sign_out_desktop_google_account');newAccount=false;
  const original=await invoke(port,'sign_in_desktop_google_account');assert.equal(original.email,first.email);
  const beforeRetry=requests.length;
  const unknown=await invoke(port,'sync_selected_desktop_conversation',{workspaceId:restored.workspaceId,conversationId:restored.conversationId,continuation:true});
  assert.equal(unknown.status,'UNKNOWN');assert.equal(requests.length,beforeRetry);
  const reconciled=await invoke(port,'reconcile_desktop_conversation_sync',{workspaceId:restored.workspaceId,conversationId:restored.conversationId});
  assert.equal(reconciled.status,'SYNCED_AFTER_RECONCILE');assert.equal(snapshot().receipt[0],4);
  assert.equal(requests.filter(x=>x.path==='/rest/v1/rpc/nanfeng_sync_commit_document').length,expectedCommits);
  console.log(JSON.stringify({phase:'original-account-explicit-reconciliation',root,candidateSha256:receipt.sha256,status:reconciled.status,commitRequests:expectedCommits}));
 }
 }finally{clearTimeout(safeRelease);holdRead=false;holdCommit=false;held?.();held=null;}
 }
 }
}finally{stop();provider.closeAllConnections();await new Promise(done=>provider.close(done));}
