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
let committedEnvelope=null;let malformedReadback=false;
let held = null;
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
 const path=request.url?.split('?')[0];requests.push({path,atMs:Date.now()});
 if(path==='/auth/v1/token'){
  const body=JSON.parse(Buffer.concat(chunks).toString('utf8'));
  if(body.refresh_token){assert.equal(body.refresh_token,'synthetic-refresh')}else{assert.equal(body.auth_code,'nfai-synthetic-windows-account-code')}
  response.writeHead(200,{'content-type':'application/json'});
  response.end(JSON.stringify({access_token:invalidIdentity?'synthetic-rejected-access':'synthetic-original-access',refresh_token:'synthetic-refresh',expires_in:3600,user:{id:invalidIdentity?'invalid/user':'00000000-0000-4000-8000-000000000071',email:'identity-owner@example.invalid',user_metadata:{full_name:'合成身份校验'}}}));
 }else if(path==='/rest/v1/rpc/nanfeng_sync_read_document'){
 response.writeHead(200,{'content-type':'application/json'});response.end(JSON.stringify(committedEnvelope?{envelope:malformedReadback?{format:'unknown-format',revision:committedEnvelope.revision,payloadHash:committedEnvelope.payloadHash}:committedEnvelope}:{missing:true}));
 }else if(path==='/rest/v1/rpc/nanfeng_sync_commit_document'){

 committedEnvelope=JSON.parse(Buffer.concat(chunks).toString('utf8')).p_envelope;
 response.writeHead(200,{'content-type':'application/json'});response.end(JSON.stringify({revision:committedEnvelope.revision,payload_hash:committedEnvelope.payloadHash}));
 }else{response.writeHead(200,{'content-type':'application/json'});response.end(JSON.stringify({documentCount:1,documents:[{envelope:makeEnvelope()}]}));}
});
await listen(provider);
const providerPort=provider.address().port;
let launch = null;
let root = '';
const stoppedPids = [];
async function start(reopen = false) {
  const debugPort = await reservePort();
  const code = `[Console]::OutputEncoding=[Text.UTF8Encoding]::new($false); & ${quote(join(desktopRoot, 'scripts/start-windows-acceptance.ps1'))} -RootParent ${quote(rootParent)} -StartupMode normal -Visible -DebugPort ${debugPort} -AccountSyncTestEndpoint ${quote(`http://127.0.0.1:${providerPort}`)}${reopen ? ` -Root ${quote(root)}` : ''}`;
  const args = ['-NoProfile', '-Command', code];
  const result = await new Promise((done, fail) => {
    const child = spawn('pwsh', args, { stdio: ['ignore', 'pipe', 'pipe'], windowsHide:true });
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
  launch = result;
  root = resolve(result.root);
  assert.equal(result.sha256, receipt.sha256);
  assert.equal(resolve(result.executable), running);
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
const startedAt = Date.now();
const startedMonotonic = process.hrtime.bigint();
let statusPath;
let baseline;
let finalReceipt;
const stamp = () => ({ utc: new Date().toISOString(), elapsedMs: Number(process.hrtime.bigint() - startedMonotonic) / 1e6 });
try {
 committedEnvelope=makeEnvelope();
 let port=await start();
 statusPath=join(root,'account-real-clock-status.json');
 await until(()=>evaluate(port,'Boolean(window.__TAURI__?.core?.invoke && globalThis.__nanfengDesktopWorkState?.runtimeInfo?.dataAreasReady)'),'原生就绪');
 const first=await invoke(port,'sign_in_desktop_google_account');assert.equal(first.email,'identity-owner@example.invalid');
 const list=await invoke(port,'restore_all_desktop_cloud_conversations');assert.equal(list.restored.length,1);
 const restored=list.restored[0];
 const initial=await invoke(port,'sync_selected_desktop_conversation',{workspaceId:restored.workspaceId,conversationId:restored.conversationId,continuation:false});assert.equal(initial.status,'SYNCED');
 await invoke(port,'set_desktop_periodic_sync',{enabled:true});
 const snapshot=()=>JSON.parse(execFileSync('python',['-c',"import sqlite3,json,sys;c=sqlite3.connect('file:'+sys.argv[1].replace('\\\\','/')+'?mode=ro',uri=True);r=c.execute('SELECT remote_revision,payload_hash,last_synced_at_ms FROM desktop_selected_conversation_sync').fetchone();w=json.loads(c.execute('SELECT exchange_json FROM workspace_exchange WHERE workspace_id=?',(sys.argv[2],)).fetchone()[0]);print(json.dumps({'receipt':r,'conversation':w['conversations'][0],'jobs':c.execute('SELECT stage FROM desktop_sync_jobs').fetchall()}));c.close()",join(root,'workspace.sqlite3'),restored.workspaceId],{encoding:'utf8'}));
 const original=snapshot();
 baseline=requests.length;
 assert.equal(requests.filter(x=>x.path==='/rest/v1/rpc/nanfeng_sync_commit_document').length,1);
 committedEnvelope.revision+=1;committedEnvelope.payload.revision=committedEnvelope.revision;
 const remoteRecord=committedEnvelope.payload.records[0];remoteRecord.revision+=1;
 remoteRecord.content.messages.push({id:'remote-user-real-clock',parentId:'answer',ordinal:2,role:'user',delivery:'COMPLETE',createdAt:'1970-01-01T00:00:04Z',revision:1,blocks:[{kind:'TEXT',text:'Synthetic real clock followup'}]});
 remoteRecord.content.currentLeafId='remote-user-real-clock';
 const bytes=Buffer.from(canonical(committedEnvelope.payload));committedEnvelope.payloadByteCount=bytes.length;committedEnvelope.payloadHash=createHash('sha256').update(bytes).digest('hex');
 const armed={phase:'ARMED_12H_REAL_CLOCK',root,pid:Number(launch.pid),monitorPid:process.pid,candidateSha256:receipt.sha256,startedUtc:new Date(startedAt).toISOString(),earliestDueUtc:new Date(startedAt+43200000).toISOString(),deadlineUtc:new Date(startedAt+43800000).toISOString(),debugPort:port,providerPort,baselineRequests:baseline,initialRevision:original.receipt[0],synthetic:true,realOnlineService:false,probeArmWritten:false,clockChanged:false,intervalChanged:false};
 await writeFile(join(root,'account-real-clock-armed.json'),JSON.stringify(armed,null,2));
 console.log(JSON.stringify(armed));
 while(Date.now()<startedAt+43800000){
  const elapsed=stamp();
  const newReads=requests.slice(baseline).filter(x=>x.path==='/rest/v1/rpc/nanfeng_sync_read_document');
  if(newReads.length){
   assert.ok(newReads[0].atMs>=startedAt+43200000,'禁止提前的周期或其它caller冒充12小时timer');
   assert.ok(elapsed.elapsedMs>=43200000,'必须经过12小时单调实钟');
   await until(()=>{const s=snapshot();return s.conversation.messages.some(m=>m.id==='remote-user-real-clock')&&s.jobs.every(j=>j[0]==='COMPLETED')},'实钟周期并集合并');
   const changed=snapshot();assert.equal(changed.receipt[0],4);assert.equal(changed.conversation.messages.find(m=>m.id==='answer').chargeMicros,18700);
   assert.equal(requests.filter(x=>x.path==='/rest/v1/rpc/nanfeng_sync_commit_document').length,2);
   assert.ok(requests.filter(x=>x.path==='/auth/v1/token').length>=2,'过期合成会话应刷新');
   finalReceipt={...armed,phase:'REAL_CLOCK_TIMER_SYNTHETIC_PASSED',...elapsed,firstCycleRequestUtc:new Date(newReads[0].atMs).toISOString(),revision:changed.receipt[0],commitRequests:2,authRequests:requests.filter(x=>x.path==='/auth/v1/token').length};
   await writeFile(join(root,'account-real-clock-result.json'),JSON.stringify(finalReceipt,null,2));
   console.log(JSON.stringify(finalReceipt));break;
  }
  assert.ok(evaluate,'CDP helper available');
  const alive=ps(`$p=Get-Process -Id ${Number(launch.pid)} -ErrorAction SilentlyContinue; if($p){[pscustomobject]@{pid=$p.Id;responding=$p.Responding;title=$p.MainWindowTitle}|ConvertTo-Json -Compress}else{throw 'Real clock candidate exited'}`);
  await writeFile(statusPath,JSON.stringify({...armed,phase:'WAITING',...elapsed,requestCount:requests.length,native:JSON.parse(alive)},null,2));
  await new Promise(done=>setTimeout(done,60000));
 }
 assert.ok(finalReceipt,'12小时周期未在限定窗口触发');
}catch(error){
 if(root)await writeFile(join(root,'account-real-clock-failure.json'),JSON.stringify({phase:'FAILED',...stamp(),candidateSha256:receipt.sha256,error:String(error),baselineRequests:baseline,requests},null,2));
 throw error;
}finally{stop();provider.closeAllConnections();await new Promise(done=>provider.close(done));}
