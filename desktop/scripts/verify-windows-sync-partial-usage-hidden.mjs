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
    '-StartupMode', 'normal', '-AccountSyncTestEndpoint', mockEndpoint, '-DebugPort', String(port)];
  if (reopen) args.push('-Root', root);
  launch = await launchPowerShell(args);
  assert.equal(launch.visible, false);
  assert.equal(launch.sha256, receipt.sha256);
  assert.equal(resolve(launch.executable), runExecutable);
  root = resolve(launch.root);
  (result.roots??=[]).push(root);
  assert.ok(root.startsWith(rootParent + sep));
  const marker = JSON.parse(await readFile(join(root, '.nanfeng-windows-acceptance.json'), 'utf8'));
  assert.equal(marker.identifier, receipt.identifier);
  return port;
}
const cdpSessions=new Map();
async function cdp(port,method,params={},which='main') {
 const key=`${port}:${which}`;let session=cdpSessions.get(key);
 if(!session){const tabs=await fetch(`http://127.0.0.1:${port}/json/list`,{signal:AbortSignal.timeout(10000)}).then(r=>r.json());const tab=tabs.find(t=>t.type==='page'&&t.url===(which==='island'?'http://tauri.localhost/island.html':'http://tauri.localhost/'));assert.ok(tab);
  const socket=new WebSocket(tab.webSocketDebuggerUrl);await new Promise((done,fail)=>{socket.addEventListener('open',done,{once:true});socket.addEventListener('error',fail,{once:true});});session={socket,next:0,pending:new Map()};cdpSessions.set(key,session);
  socket.addEventListener('message',event=>{const message=JSON.parse(event.data);const pending=session.pending.get(message.id);if(!pending)return;clearTimeout(pending.timer);session.pending.delete(message.id);if(message.error||message.result?.exceptionDetails)pending.fail(new Error(message.result?.exceptionDetails?.exception?.description||message.error?.message||'CDP expression failed'));else pending.done(message.result);});
  socket.addEventListener('close',()=>{for(const pending of session.pending.values()){clearTimeout(pending.timer);pending.fail(new Error('Owned CDP target closed'));}session.pending.clear();cdpSessions.delete(key);});
 }
 const id=++session.next;return await new Promise((done,fail)=>{const timer=setTimeout(()=>{session.pending.delete(id);fail(new Error('Owned CDP request timed out'));},30000);session.pending.set(id,{done,fail,timer});session.socket.send(JSON.stringify({id,method,params}));});
}
function closeCdpSessions(){for(const session of cdpSessions.values())session.socket.close();cdpSessions.clear();}
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
    try { const value = await check(); if (value) return value; } catch { /* Hidden page may be refreshing. */ }
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






const fixture=JSON.parse(await readFile(new URL('../../protocol/fixtures/answer-model-facts-v1.json',import.meta.url),'utf8'));
const wire=JSON.parse(await readFile(new URL('../../protocol/fixtures/data-area-sync-v1.json',import.meta.url),'utf8')).find(x=>x.platform==='DESKTOP'&&x.area==='CHAT');
const canonical=value=>Array.isArray(value)?`[${value.map(canonical).join(',')}]`:value&&typeof value==='object'?`{${Object.keys(value).sort().map(key=>`${JSON.stringify(key)}:${canonical(value[key])}`).join(',')}}`:JSON.stringify(value);
let committedEnvelope=null;let revision=1;let remoteFacts=fixture.cases[0].remote;
const portable=facts=>{const u=facts.usage||facts.estimatedUsage||{};return {modelId:facts.modelSnapshot.modelId,modelDisplayName:facts.modelSnapshot.displayName,inputTokens:u.inputTokens??null,outputTokens:u.outputTokens??null,totalTokens:u.totalTokens??null,cachedInputTokens:u.cachedInputTokens??null,reasoningTokens:u.reasoningTokens??null,costPriceVersion:facts.costPriceVersion??null,costCurrencyCode:facts.currencyCode??null,costTotalMicros:facts.chargeMicros??null,costSource:facts.costSource??null};};
const makeEnvelope=()=>{const content=structuredClone(wire.content);content.updatedAt=`1970-01-01T00:00:${String(revision+1).padStart(2,'0')}Z`;content.currentLeafId='answer';content.messages.push({id:'answer',parentId:'m1',ordinal:1,role:'assistant',delivery:'COMPLETE',createdAt:'1970-01-01T00:00:02Z',revision,blocks:[{kind:'TEXT',text:'synthetic completed answer'}],modelUsage:portable(remoteFacts)});const payload={format:'nfai.sync.payload',protocolVersion:1,schemaVersion:1,appId:'com.nanzhufeng.ai',documentId:wire.documentId,revision,records:[{kind:'conversation',id:wire.conversationId,revision,classification:'NORMAL',content}]};const bytes=Buffer.from(canonical(payload));return {format:'nfai.sync.direct',protocolVersion:1,schemaVersion:1,appId:payload.appId,documentId:payload.documentId,revision,payloadHash:createHash('sha256').update(bytes).digest('hex'),payloadByteCount:bytes.length,payload};};
const requests=[];const serverErrors=[];const outbound=[];
const mock=http.createServer(async(req,res)=>{try{const chunks=[];for await(const part of req)chunks.push(part);const path=req.url?.split('?')[0];requests.push(path);res.setHeader('content-type','application/json');
 if(path==='/auth/v1/token'){const body=JSON.parse(Buffer.concat(chunks).toString('utf8'));assert.equal(body.auth_code,'nfai-synthetic-windows-account-code');res.end(JSON.stringify({access_token:'synthetic-a9-access',refresh_token:'synthetic-a9-refresh',expires_in:3600,user:{id:'00000000-0000-4000-8000-000000000071',email:'a9-facts@example.invalid',user_metadata:{full_name:'synthetic a9 facts'}}}));}
 else if(path==='/rest/v1/rpc/nanfeng_sync_inventory')res.end(JSON.stringify({documentCount:1,documents:[{envelope:committedEnvelope||makeEnvelope()}]}));
 else if(path==='/rest/v1/rpc/nanfeng_sync_read_document')res.end(JSON.stringify([{envelope:committedEnvelope||makeEnvelope()}]));
 else if(path==='/rest/v1/rpc/nanfeng_sync_commit_document'){const body=JSON.parse(Buffer.concat(chunks).toString('utf8'));assert.equal(body.p_expected_revision,revision);const envelope=body.p_envelope;assert.equal(envelope.format,'nfai.sync.direct');outbound.push(envelope);committedEnvelope=envelope;revision++;res.end(JSON.stringify([{revision,payload_hash:envelope.payloadHash}]));}
 else{res.statusCode=404;res.end('{}');}
}catch(error){serverErrors.push(String(error));res.statusCode=500;res.end('{}');}});
await new Promise(done=>mock.listen(0,'127.0.0.1',done));const mockEndpoint=`http://127.0.0.1:${mock.address().port}`;
const python='C:/Users/Administrator/.cache/codex-runtimes/codex-primary-runtime/dependencies/python/python.exe';
const sql=(action,workspaceId='',facts={})=>JSON.parse(execFileSync(python,['-c',`import pathlib,json,sqlite3,sys
root=pathlib.Path(sys.argv[1]);mark=json.loads((root/'.nanfeng-windows-acceptance.json').read_text(encoding='utf-8'));assert mark['identifier']=='com.nanzhufeng.ai.desktop.windowsacceptance'
action=sys.argv[2]
if action=='ledger':
 c=sqlite3.connect((root/'usage-ledger/usage-ledger-v1.sqlite3').as_uri()+'?mode=ro',uri=True);print(json.dumps(c.execute('SELECT COUNT(*) FROM usage_ledger_entries').fetchone()[0]));c.close()
else:
 c=sqlite3.connect(root/'workspace.sqlite3') if action=='seed' else sqlite3.connect((root/'workspace.sqlite3').as_uri()+'?mode=ro',uri=True)
 workspace=sys.argv[3];row=c.execute('SELECT exchange_json FROM workspace_exchange WHERE workspace_id=?',(workspace,)).fetchone();v=json.loads(row[0]);message=next(m for conv in v['conversations'] for m in conv['messages'] if m['id']=='answer')
 keys=['modelSnapshot','usage','estimatedUsage','chargeMicros','currencyCode','costPriceVersion','costSource']
 if action=='seed':
  for k in keys:message.pop(k,None)
  message.update(json.loads(sys.argv[4]));c.execute('UPDATE workspace_exchange SET exchange_json=? WHERE workspace_id=?',(json.dumps(v),workspace));c.commit()
 print(json.dumps({k:message[k] for k in keys if k in message}));c.close()
`,root,action,workspaceId,JSON.stringify(facts)],{encoding:'utf8',windowsHide:true,timeout:15000}));
const result={candidateSha256:receipt.sha256,backgroundOnly:true,realService:false,syntheticCredentialOnly:true,osBrowserInvoked:false,cases:[]};let port;let expectedFinal;
try{
 port=await start();await ready(port);await until(()=>evaluate(port,'Boolean(globalThis.__nanfengDesktopWorkState?.runtimeInfo?.dataAreasReady)'),'normal stores ready');result.runtimeInfo=await invoke(port,'CHAT','read_desktop_runtime_info');
 const account=await invoke(port,'CHAT','sign_in_desktop_google_account');assert.equal(account.email,'a9-facts@example.invalid');
 const first=await invoke(port,'CHAT','restore_all_desktop_cloud_conversations');assert.equal(first.restored.length,1);const workspaceId=first.restored[0].workspaceId;
 for(const item of fixture.cases){assert.deepEqual(sql('seed',workspaceId,item.local),item.local);remoteFacts=item.remote;committedEnvelope=null;revision++;const restored=await invoke(port,'CHAT','restore_all_desktop_cloud_conversations');assert.equal(restored.restored.length,1);const actual=sql('read',workspaceId);assert.deepEqual(actual,item.expectedFacts,item.name);const count=outbound.length;const synced=await invoke(port,'CHAT','sync_selected_desktop_conversation',{workspaceId,conversationId:wire.conversationId});assert.equal(synced.status,'SYNCED');assert.equal(outbound.length,count+1);const sent=outbound.at(-1).payload.records[0].content.messages.find(m=>m.id==='answer').modelUsage;assert.deepEqual(sent,item.expectedPortable,item.name+' outgoing wire');assert.deepEqual(sql('read',workspaceId),item.expectedFacts);result.cases.push({name:item.name,facts:actual,portable:sent,status:synced.status});expectedFinal=item.expectedFacts;}
 assert.equal(sql('ledger'),0,'restored answer history must not create physical call entries');assert.equal(outbound.length,fixture.cases.length);
 result.close=await normalClose();port=await start(true);await ready(port);await until(()=>evaluate(port,'Boolean(globalThis.__nanfengDesktopWorkState?.runtimeInfo?.dataAreasReady)'),'cold stores ready');assert.equal((await invoke(port,'CHAT','read_desktop_account_sync')).email,account.email);assert.deepEqual(sql('read',workspaceId),expectedFinal);assert.equal(sql('ledger'),0);result.coldClose=await normalClose();
 port=await start();await ready(port);await until(()=>evaluate(port,'Boolean(globalThis.__nanfengDesktopWorkState?.runtimeInfo?.dataAreasReady)'),'fresh restore stores ready');await invoke(port,'CHAT','sign_in_desktop_google_account');const fresh=await invoke(port,'CHAT','restore_all_desktop_cloud_conversations');assert.equal(fresh.restored.length,1);const freshWorkspace=fresh.restored[0].workspaceId;const freshFacts=sql('read',freshWorkspace);assert.deepEqual(freshFacts.usage,{inputTokens:6});assert.equal(freshFacts.estimatedUsage,undefined);assert.equal(freshFacts.chargeMicros,7);assert.equal(freshFacts.currencyCode,'CNY');assert.equal(sql('ledger'),0);result.restoredFromActualOutbound=freshFacts;result.freshClose=await normalClose();port=await start(true);await ready(port);await until(()=>evaluate(port,'Boolean(globalThis.__nanfengDesktopWorkState?.runtimeInfo?.dataAreasReady)'),'fresh cold stores ready');assert.deepEqual(sql('read',freshWorkspace),freshFacts);assert.equal(sql('ledger'),0);result.freshColdClose=await normalClose();assert.deepEqual(serverErrors,[]);result.pass=true;
}catch(error){result.error=error.stack||String(error);process.exitCode=1;}
finally{if(launch)try{result.failureClose=await normalClose();}catch(error){result.closeError=String(error);stop();}result.root=root;result.pids=pids;result.requests=requests;result.outbound=outbound;result.serverErrors=serverErrors;if(root)await writeFile(join(root,'sync-partial-usage-native-evidence.json'),JSON.stringify(result,null,2)+'\n');closeCdpSessions();mock.closeAllConnections();await new Promise(done=>mock.close(done));}
console.log(JSON.stringify(result,null,2));
