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


root=resolve(process.argv[3]);assert.ok(root.startsWith(rootParent+sep));const result={root,candidateSha256:receipt.sha256,readOnlyCold:true,requests:[],closes:[]};
try{
 const port=await start(true);await ready(port);await evaluate(port,`(()=>{const core=window.__TAURI__.core,original=core.invoke;globalThis.__capacityReadTrace=[];core.invoke=async function(command,...args){const entry={command,start:Date.now()};globalThis.__capacityReadTrace.push(entry);try{return await original.call(this,command,...args)}catch(error){entry.failed=true;throw error}finally{entry.elapsedMs=Date.now()-entry.start}};return true})()`);await until(()=>evaluate(port,'globalThis.__nanfengDesktopWorkState.runtimeInfo?.dataAreasReady'),'cold area projections');
 const inspect=()=>evaluate(port,`(()=>{const s=globalThis.__nanfengDesktopWorkState,c=JSON.parse(localStorage.getItem('nanfeng-ai.desktop.sync-cloud-list.v1')||'null');return {area:s.dataArea,pane:s.pane,section:s.settingsSection,email:s.accountSync?.email,accountState:s.accountSync?.state,selected:s.accountSync?.selectedConversationCount,rows:s.cloudConversations?.length,error:s.error,status:s.status,cacheEmail:c?.email,cacheEntries:c?.entries?.length,actions:[...new Set([...document.querySelectorAll('[data-action]')].map(n=>n.dataset.action))].filter(x=>x.includes('cloud')||x==='show-chat')}})()`);
 result.initial=await inspect();result.timeline=[];const deadline=Date.now()+30000;while(Date.now()<deadline){const point={elapsedMs:30000-(deadline-Date.now()),ui:await inspect(),trace:await evaluate(port,'globalThis.__capacityReadTrace')};result.timeline.push(point);await writeFile(resolve(desktopRoot,'../docs/review/20261004-cloud-capacity-current/cold-timeline-current.json'),JSON.stringify(result,null,2));if(point.ui.email==='cloud-atomic@example.invalid'&&point.ui.rows===10000)break;await new Promise(r=>setTimeout(r,1000));}result.final=await inspect();await writeFile(resolve(desktopRoot,'../docs/review/20261004-cloud-capacity-current/cold-trace-initial.json'),JSON.stringify(result,null,2));
 if(await evaluate(port,'Boolean(document.querySelector("[data-action=show-chat]"))')){await clickUi(port,'[data-action=show-chat]');await evaluate(port,'new Promise(r=>requestAnimationFrame(()=>requestAnimationFrame(r)))');}
 if(await evaluate(port,'Boolean(document.querySelector("[data-action=show-cloud-conversation-list]"))')){await clickUi(port,'[data-action=show-cloud-conversation-list]');await evaluate(port,'new Promise(r=>requestAnimationFrame(()=>requestAnimationFrame(r)))');}
 result.afterNavigation=await inspect();result.accountOwner=await invoke(port,'CHAT','read_desktop_account_sync');result.requests=requests;assert.equal(requests.length,0,'read-only cold must not fetch inventory');
 result.closes.push(await normalClose());result.pids=pids;result.forcedCleanup=false;await writeFile(resolve(desktopRoot,'../docs/review/20261004-cloud-capacity-current/cold-trace-current.json'),JSON.stringify(result,null,2));console.log(JSON.stringify({root,initial:result.initial,after:result.afterNavigation,pids}));
}catch(error){if(launch)stop();throw error;}finally{server.closeAllConnections();await new Promise(done=>server.close(done));}
