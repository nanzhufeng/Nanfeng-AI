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
const namespace=process.argv.includes('--release')?'windows-acceptance-release':'windows-acceptance';
const receipt = JSON.parse(await readFile(join(desktopRoot, 'output',namespace,'current.json'), 'utf8'));
assert.equal(receipt.buildProfile,process.argv.includes('--release')?'release':'debug');
assert.equal(receipt.identifier, 'com.nanzhufeng.ai.desktop.windowsacceptance');
const source = resolve(desktopRoot, 'output',namespace, receipt.sha256, 'NanfengAI-Windows-Acceptance.exe');
assert.equal(resolve(receipt.executable), source);
assert.equal(createHash('sha256').update(await readFile(source)).digest('hex').toUpperCase(), receipt.sha256);
const runExecutable = resolve(desktopRoot, 'output/windows-acceptance-binaries', receipt.sha256, 'nanfeng-ai-desktop-spike.exe');
const ps = (args) => execFileSync('pwsh', ['-NoProfile', ...args], { encoding: 'utf8', timeout: 60000, windowsHide: true }).trim();
const privateDesktopGuard=JSON.parse(ps(['-File',join(desktopRoot,'scripts/assert-windows-private-desktop.ps1')]));assert.equal(privateDesktopGuard.verified,true);
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
  const args = ['-File', join(desktopRoot, 'scripts/start-windows-acceptance.ps1'), '-RootParent', rootParent, '-ExecutablePath',source,
    '-StartupMode', 'normal', '-DebugPort', String(port)];
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






const python=String.raw`C:\Users\Administrator\.cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe`;
const fixture=JSON.parse(await readFile(join(desktopRoot,'tests/fixtures/legacy-independent-search-ledger-v1.json'),'utf8'));
const result={privateDesktopGuard,candidateSha256:receipt.sha256,backgroundOnly:true,realService:false,providerEndpoint:false,checks:[]};let port;
const checkpoint=async phase=>{result.phase=phase;result.root=root;result.pids=pids;await writeFile(join(rootParent,'native-legacy-search-evidence.json'),JSON.stringify(result,null,2)+'\n');};
const pythonRead=(code,args=[])=>JSON.parse(execFileSync(python,['-X','utf8','-c',code,...args],{encoding:'utf8',windowsHide:true,timeout:15000}));
const facts=()=>pythonRead(String.raw`import json,sqlite3,sys;from pathlib import Path;p=Path(sys.argv[1]).resolve();assert p.is_relative_to(Path(sys.argv[2]).resolve());c=sqlite3.connect((p/'usage-ledger/usage-ledger-v1.sqlite3').as_uri()+'?mode=ro',uri=True);assert c.execute('pragma user_version').fetchone()[0]==3;rows=c.execute('select e.*,m.metadata_json from usage_ledger_entries e join usage_ledger_call_metadata m on m.entry_id=e.entry_id order by e.entry_id').fetchall();c.close();print(json.dumps(rows))`,[root,rootParent]);
function checkProjection(projection){
 assert.equal(projection.records.length,3);assert.deepEqual(projection.records.map(row=>row.entryId),fixture.expectedDisplayIds);assert.equal(projection.inputTokens,5);assert.equal(projection.outputTokens,2);assert.equal(projection.cachedInputTokens,0);
 for(const row of projection.records.filter(row=>row.callMetadata?.serviceId)){
  assert.equal(row.callMetadata.serviceId,'ZHIPU_WEB_SEARCH');assert.equal(row.callMetadata.serviceEngineId,'search_pro');assert.equal(row.callMetadata.modelDisplayName,'智谱网页搜索 · 高级版');assert.equal(row.requestedModelId,'');assert.equal(row.actualModelId,null);assert.equal(row.modelId,'');assert.equal(row.inputTokens,null);assert.equal(row.outputTokens,null);assert.equal(row.cachedInputTokens,null);assert.equal(row.chargeMicros,null);assert.equal(row.currencyCode,null);assert.equal(row.costSource,null);assert.equal(row.callMetadata.priceVersion,null);
 }
 const service=projection.records.filter(row=>row.callMetadata?.serviceId);assert.equal(service.length,2);assert.deepEqual(service.map(row=>row.kind),['STREAM_PENDING','FINAL_MEASURED']);assert.ok(projection.records.every(row=>/^[a-f0-9]{64}$/.test(row.sourceRootId)));
 const model=projection.records.find(row=>!row.callMetadata?.serviceId);assert.equal(model.requestedModelId,'openai/gpt-6-luna');assert.equal(model.chargeMicros,4);assert.equal(model.currencyCode,'USD');
}
async function readUi(){
 await clickUi(port,'[data-action=show-settings]');await clickUi(port,'[data-action=open-settings-page][data-page=model]');await clickUi(port,'[data-action=open-settings-page][data-page=conversation-cost]');
 const cards=await until(()=>evaluate(port,`(()=>{const cards=[...document.querySelectorAll('.android-settings-ledger-row')];return cards.length===3&&cards.map(node=>node.textContent)})()`),'actual read-only usage rows');
 const service=cards.filter(card=>card.includes('智谱网页搜索 · 高级版'));assert.equal(service.length,2);for(const card of service){assert.ok(card.includes('金额未知'));assert.ok(card.includes('Token 用量未返回'));assert.ok(card.includes('联网检索'));assert.ok(!card.includes('GPT-6 Luna'));assert.ok(!card.includes('¥0'));}
 return {rows:cards.length,services:service.length,unknownServiceMoney:true,unknownServiceTokens:true};
}
try{
 assert.ok(rootParent.startsWith(resolve(desktopRoot,'../.runtime-qa')+sep));await import('node:fs/promises').then(fs=>fs.mkdir(rootParent,{recursive:false}));
 port=await start();await checkpoint('empty-start');await ready(port);const empty=await invoke(port,'CHAT','read_desktop_usage_ledger');assert.equal(empty.records.length,0);result.runtimeInfo=await invoke(port,'CHAT','read_desktop_runtime_info');result.initialClose=await normalClose();
 result.seed=JSON.parse(execFileSync(python,['-X','utf8',join(desktopRoot,'scripts/seed-legacy-search-ledger-fixture.py'),root],{encoding:'utf8',windowsHide:true,timeout:15000}));assert.equal(result.seed.seededRows,4);result.beforeFacts=facts();await checkpoint('owned-fixture-seeded');
 port=await start(true);await checkpoint('legacy-start');await ready(port);result.firstProjection=await invoke(port,'CHAT','read_desktop_usage_ledger');checkProjection(result.firstProjection);result.firstUi=await readUi();result.legacyClose=await normalClose();result.checks.push('actual EXE IPC and settings preserve legacy service receipts without an LLM identity or settlement');
 port=await start(true);await checkpoint('legacy-cold-start');await ready(port);result.coldProjection=await invoke(port,'CHAT','read_desktop_usage_ledger');checkProjection(result.coldProjection);assert.deepEqual(result.coldProjection,result.firstProjection);result.coldUi=await readUi();result.coldClose=await normalClose();result.afterFacts=facts();assert.deepEqual(result.afterFacts,result.beforeFacts);result.pass=true;
}catch(error){result.pass=false;result.error=error.stack||String(error);process.exitCode=1;}
finally{if(launch)try{result.failureClose=await normalClose();}catch(error){result.closeError=String(error);stop();}await checkpoint(result.pass?'complete':'failed-preserved');}
console.log(JSON.stringify({pass:result.pass,error:result.error,root,pids,evidence:join(rootParent,'native-legacy-search-evidence.json')},null,2));
