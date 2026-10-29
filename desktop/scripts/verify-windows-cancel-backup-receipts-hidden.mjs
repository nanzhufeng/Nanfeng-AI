import assert from 'node:assert/strict';
import { createHash, randomUUID } from 'node:crypto';
import { execFileSync, spawn } from 'node:child_process';
import { readFile, writeFile, stat } from 'node:fs/promises';
import http from 'node:http';
import { dirname, join, resolve, sep } from 'node:path';
import { fileURLToPath } from 'node:url';

if (process.platform !== 'win32') throw new Error('仅在 Windows 隔离候选上验收。');
const desktopRoot = resolve(dirname(fileURLToPath(import.meta.url)), '..');
assert.ok(process.argv[2], 'Explicit synthetic root parent required');
const rootParent = resolve(process.argv[2]);
assert.ok(process.execArgv.includes('--max-old-space-size=2048'));
if (process.argv.includes('--screenshot')) throw new Error('后台验收不支持前台截图');
const receipt = JSON.parse(await readFile(join(desktopRoot, 'output/windows-acceptance-release/current.json'), 'utf8'));
assert.equal(receipt.identifier, 'com.nanzhufeng.ai.desktop.windowsacceptance');
assert.equal(receipt.buildProfile,'release');
const inputIndex=process.argv.indexOf('--input-manifest');
const inputPath=inputIndex<0 ? join(desktopRoot,'../docs/review/20261017-windows-cancel-backup-receipts/source-input-manifest-release-v1.json') : resolve(process.argv[inputIndex+1]);
assert.ok(inputPath.startsWith(resolve(desktopRoot,'../docs/review')+sep));

const inputBytes=await readFile(inputPath);
const sourceInputSha256=createHash('sha256').update(inputBytes).digest('hex');
const source = resolve(desktopRoot, 'output/windows-acceptance-release', receipt.sha256, 'NanfengAI-Windows-Acceptance.exe');
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
    '-ExecutablePath',source,'-StartupMode', 'normal', '-DebugPort', String(port)];
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

const kind='cancelled';
const result={kind,sha256:receipt.sha256,areas:{},sourceInputSha256};
const py='C:/Users/Administrator/.cache/codex-runtimes/codex-primary-runtime/dependencies/python/python.exe';
const fixture=(action,...args)=>JSON.parse(execFileSync(py,['-X','utf8',join(desktopRoot,'scripts/cancel-backup-receipts-fixture.py'),action,...args],{encoding:'utf8',windowsHide:true,timeout:20000}));
try{
 let port=await start();result.root=root;await ready(port);await normalClose();
 result.seed=fixture('seed',root,kind);
 port=await start(true);await ready(port);
 const before=fixture('read',root);result.before=before;
 assert.equal(before.CHAT.receipts.desktop_ordinary_chat_attempts.length,1);assert.equal(before.CHAT.receipts.desktop_ordinary_chat_attempts[0].state,'CANCELLED');
 const measured=before.CHAT.ledger.usage_ledger_entries.filter(e=>e.kind==='FINAL_MEASURED');assert.equal(measured.length,1);assert.equal(measured[0].input_tokens,6);assert.equal(measured[0].cached_input_tokens,1);assert.equal(measured[0].output_tokens,null);assert.equal(measured[0].charge_micros,null);
 const areas=['CHAT'];
 for(const area of areas){
  const path=join(root,`synthetic-call-facts-${area}.nfai-backup`);
  const exported=await invoke(port,area,'export_desktop_local_backup',{selectedPath:path});
  const archive=fixture('archive',path,kind+'-'+area.toLowerCase()+'-'+randomUUID());
  assert.equal(archive.archiveSha256.toUpperCase(),exported.sha256.toUpperCase());
  assert.deepEqual(archive.facts,before[area],area+' full archive fields');
  result.areas[area]={path,exported,archive};
 }
 for(const area of areas){
  const item=result.areas[area],peer=area==='CHAT'?'WORK':'CHAT';
  await assert.rejects(()=>invoke(port,peer,'preflight_desktop_local_backup',{selectedPath:item.path}));
  await invoke(port,peer,'cancel_desktop_local_restore');
  assert.deepEqual(fixture('read',root),before,'cross-area refusal preserves business');item.crossAreaRefused=true;
 }
 await normalClose();
 for(const area of areas)result.areas[area].mutation=fixture('mutate',root,area);
 port=await start(true);await ready(port);
 for(const area of areas){
  const item=result.areas[area];
  const workspace=(await invoke(port,area,'ensure_desktop_local_workspace')).summary.id;
  await invoke(port,area,'save_desktop_conversation_draft',{args:{workspaceId:workspace,conversationId:null,text:'SYNTHETIC POST BACKUP '+area,attachmentIds:[],modelId:null,toneOverride:null,webSearchOverride:null}});
 }
 const changed=fixture('read',root);result.changed=changed;
 for(const area of areas){
  const item=result.areas[area],peer=area==='CHAT'?'WORK':'CHAT';
  await invoke(port,area,'preflight_desktop_local_backup',{selectedPath:item.path});
  await invoke(port,area,'cancel_desktop_local_restore');
  assert.deepEqual(fixture('read',root),changed,'cancel preserves changes');item.cancelReadOnly=true;
 }
 for(const area of areas){
  const item=result.areas[area],peer=area==='CHAT'?'WORK':'CHAT';
  const peerBefore=fixture('read',root)[peer];
  const preflight=await invoke(port,area,'preflight_desktop_local_backup',{selectedPath:item.path});
  await invoke(port,area,'restore_desktop_local_backup',{fingerprint:preflight.fingerprint,replaceLocal:true});
  item.beforeRestart=await invoke(port,area,'read_desktop_local_backup_status');
  assert.equal(item.beforeRestart.restartRequired,true);
  assert.deepEqual(fixture('read',root)[peer],peerBefore,'peer untouched by restore');
  await normalClose();port=await start(true);await ready(port);
  item.afterRestart=await invoke(port,area,'read_desktop_local_backup_status');
  assert.equal(item.afterRestart.operation,'COMPLETED');assert.equal(item.afterRestart.restartRequired,false);
  assert.deepEqual(fixture('read',root)[area],before[area],area+' restored fields after cold');
 }
 result.after=fixture('read',root);assert.deepEqual(result.after,before);
 if(kind==='partial')assert.equal(result.after.CHAT.ledger.usage_ledger_entries.length,0,'portable history creates no physical call');
 await normalClose();result.pass=true;
}catch(error){result.pass=false;result.error=error.stack||String(error);process.exitCode=1;}
finally{if(launch){try{result.failureClose=await normalClose();}catch(error){result.forcedCleanup=true;result.closeError=String(error);stop();}}result.pids=pids;if(root)await writeFile(join(root,'cancel-backup-receipts-native-evidence.json'),JSON.stringify(result,null,2),{flag:'wx'});}
console.log(JSON.stringify({root,kind,pass:result.pass,error:result.error,pids}));
