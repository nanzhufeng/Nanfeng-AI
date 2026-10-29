import { packageExchange, preflightPackage, semanticHash, sha256 } from '../../protocol/scripts/exchange-lib.mjs';
import assert from 'node:assert/strict';
import { createHash, randomUUID } from 'node:crypto';
import { execFileSync, spawn } from 'node:child_process';
import { mkdir, readFile, writeFile } from 'node:fs/promises';
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

const accountEmail='temporary-consumer-sync@example.invalid';
const syntheticToken='synthetic-temporary-consumer-access';
const markers=['TEMP_CONSUMER_PRIVATE_MESSAGE','TEMP_CONSUMER_PRIVATE_DRAFT','TEMP_CONSUMER_PRIVATE_BYTES','TEMP_CONSUMER_PRIVATE_NAME','ORDINARY_UNSENT_DO_NOT_EXPORT'];
const documents=new Map(),requests=[],serverErrors=[];
const server=http.createServer(async(request,response)=>{
 try {
  const chunks=[];for await(const chunk of request)chunks.push(chunk);
  const bytes=Buffer.concat(chunks),body=bytes.length?JSON.parse(bytes.toString('utf8')):{};
  const path=request.url?.split('?')[0];
  requests.push({path,documentId:body.p_document_id??null,bodyBytes:bytes.length,bodySha256:createHash('sha256').update(bytes).digest('hex'),privateMarkerPresent:markers.some(marker=>bytes.includes(Buffer.from(marker)))});
  let value;
  if(path==='/auth/v1/token'){
   assert.equal(body.auth_code,'nfai-synthetic-windows-account-code');
   value={access_token:syntheticToken,refresh_token:'synthetic-temporary-consumer-refresh',expires_in:3600,user:{id:'00000000-0000-4000-8000-000000000093',email:accountEmail,user_metadata:{full_name:'合成临时隔离账号'}}};
  } else {
   assert.equal(request.headers.authorization,`Bearer ${syntheticToken}`);assert.equal(body.p_app_id,'com.nanzhufeng.ai');
   if(path==='/rest/v1/rpc/nanfeng_sync_read_document')value=documents.has(body.p_document_id)?[{envelope:documents.get(body.p_document_id)}]:[];
   else if(path==='/rest/v1/rpc/nanfeng_sync_commit_document'){
    const expected=documents.get(body.p_document_id)?.revision??0;assert.equal(body.p_expected_revision,expected);
    assert.equal(body.p_envelope.format,'nfai.sync.direct');assert.equal(body.p_envelope.documentId,body.p_document_id);assert.equal(body.p_envelope.revision,expected+1);
    documents.set(body.p_document_id,body.p_envelope);value=[{revision:expected+1,payload_hash:body.p_envelope.payloadHash}];
   }else if(path==='/rest/v1/rpc/nanfeng_sync_inventory')value={documentCount:documents.size,documents:[...documents.values()].map(envelope=>({envelope}))};
   else throw new Error(`unexpected RPC ${path}`);
  }
  response.writeHead(200,{'content-type':'application/json'});response.end(JSON.stringify(value));
 } catch(error){serverErrors.push(String(error));response.writeHead(500);response.end('{"message":"synthetic protocol failure"}');}
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




const selectedRoot=resolve(process.argv[3]||'');assert.ok(selectedRoot.startsWith(rootParent+sep));
assert.equal(ps(['-Command','@(Get-CimInstance Win32_Process -Filter "ProcessId=158772").Count']),'0','previous exact PID still live');
root=selectedRoot;
const observations=[],result={candidateSha256:receipt.sha256,root,previousPid:158772,changes:'no product change; observer only'};
const output=resolve(desktopRoot,'../docs/review/20261002-native-startup-diagnosis/observations.json');
let port,phase='launch';
try {
 port=await start(true);result.pid=Number(launch.pid);result.debugPort=port;phase='observe';
 const begun=Date.now(),deadline=begun+30000;
 while(Date.now()<deadline){
  const observation={elapsedMs:Date.now()-begun};
  try {
   const response=await fetch(`http://127.0.0.1:${port}/json/list`,{signal:AbortSignal.timeout(1500)});
   observation.httpStatus=response.status;const tabs=await response.json();observation.tabs=tabs.map(({id,type,url})=>({id,type,url}));
   const tab=tabs.find(tab=>tab.type==='page'&&tab.url.startsWith('http://tauri.localhost'));
   if(tab){
    const socket=new WebSocket(tab.webSocketDebuggerUrl);
    try {
     await new Promise((done,fail)=>{const timer=setTimeout(()=>fail(new Error('observer socket open timeout')),1500);socket.addEventListener('open',()=>{clearTimeout(timer);done();},{once:true});socket.addEventListener('error',error=>{clearTimeout(timer);fail(error);},{once:true});});
     const evaluated=await new Promise((done,fail)=>{const timer=setTimeout(()=>fail(new Error('observer frame timed out')),1500);socket.addEventListener('message',event=>{const message=JSON.parse(event.data);if(message.id!==1)return;clearTimeout(timer);done(message);});socket.send(JSON.stringify({id:1,method:'Runtime.evaluate',params:{expression:'({readyState:document.readyState,tauriPresent:Boolean(window.__TAURI__),coreInvokeType:typeof window.__TAURI__?.core?.invoke,appStatePresent:Boolean(globalThis.__nanfengDesktopWorkState),dataAreasReady:Boolean(globalThis.__nanfengDesktopWorkState?.runtimeInfo?.dataAreasReady),pane:globalThis.__nanfengDesktopWorkState?.pane,workspaceCount:globalThis.__nanfengDesktopWorkState?.workspaces?.length,dialogKind:globalThis.__nanfengDesktopWorkState?.dialog?.kind,bodyChars:document.body?.innerText.length,workspaceButtons:document.querySelectorAll("[data-action=select-workspace]").length,composerPresent:Boolean(document.querySelector("#chat-composer"))})',returnByValue:true}}));});
     observation.frame=evaluated.result?.result?.value;observation.exception=Boolean(evaluated.result?.exceptionDetails||evaluated.error);
    }finally{socket.close();}
   }
  }catch(error){observation.error=String(error);}
  observations.push(observation);await writeFile(output,JSON.stringify({...result,observations},null,2));
  if(observation.frame?.coreInvokeType==='function')break;
  await new Promise(done=>setTimeout(done,500));
 }
 phase='window';const pid=Number(launch.pid);result.window=JSON.parse(ps(['-Command',`$p=Get-Process -Id ${pid};@{pid=$p.Id;title=$p.MainWindowTitle;handle=[int64]$p.MainWindowHandle;responding=$p.Responding}|ConvertTo-Json -Compress`]));
 result.requests=requests;phase='close';result.close=await normalClose();result.outcome='observer-completed';
}catch(error){result.failure={phase,error:String(error)};}
finally{await writeFile(output,JSON.stringify({...result,observations},null,2));stop();server.closeAllConnections();await new Promise(done=>server.close(done));console.log(JSON.stringify({candidateSha256:receipt.sha256,root:result.root,pid:result.pid,observationCount:observations.length,lastObservation:observations.at(-1),window:result.window,close:result.close,failure:result.failure}));}
