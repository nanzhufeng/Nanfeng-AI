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
const receipt = JSON.parse(await readFile(join(desktopRoot, 'output/windows-acceptance-release/current.json'), 'utf8'));
assert.equal(receipt.identifier, 'com.nanzhufeng.ai.desktop.windowsacceptance');
assert.equal(receipt.buildProfile,'release');
const inputArgument=process.argv.indexOf('--input-manifest');
const inputManifest=inputArgument<0 ? join(desktopRoot,'../docs/review/20261016-windows-release-compile/source-input-manifest-release-v1.json') : resolve(process.argv[inputArgument+1]||'');
assert.ok(inputManifest.startsWith(resolve(desktopRoot,'../docs/review')+sep),'Frozen input must be repository review evidence');
const frozenInputBytes=await readFile(inputManifest);
const frozenInput=JSON.parse(frozenInputBytes);assert.equal(frozenInput.context.profile,'release');
const sourceInputSha256=createHash('sha256').update(frozenInputBytes).digest('hex');
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
    '-StartupMode', 'normal', '-DebugPort', String(port), '-ExecutablePath', source];
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





// No provider endpoint: unlike ordinary-chat mocks, this leaves business storage empty.
const result={candidateSha256:receipt.sha256,backgroundOnly:true,realService:false,providerEndpoint:false};let port;
async function entryState(){return evaluate(port,`(()=>{const s=__nanfengDesktopWorkState;return {entryResolved:s.entryResolved,currentId:s.current?.summary?.id||null,selected:s.selectedConversationId||null,error:s.error,catalogLoaded:!!s.p6gCatalog,defaultLoaded:!!s.p6gGlobalDefault,composer:!!document.querySelector('#chat-composer')}})()`);}
try{
 const quote=value=>`'${String(value).replaceAll("'", "''")}'`;
 const live=ps(['-Command',`@(Get-CimInstance Win32_Process -Filter "Name='nanfeng-ai-desktop-spike.exe'" | Where-Object {$_.ExecutablePath -and $_.ExecutablePath.StartsWith(${quote(join(desktopRoot,'output/windows-acceptance-binaries'))})}).Count`]);
 assert.equal(live,'0','Do not interfere with an existing acceptance process');
 port=await start();await ready(port);
 result.runtimeInfo=await invoke(port,'CHAT','read_desktop_runtime_info');
 assert.equal(result.runtimeInfo.buildProfile,'release');assert.equal(result.runtimeInfo.sourceInputSha256,sourceInputSha256);
 assert.equal(result.runtimeInfo.sourceInputFileCount,frozenInput.files.length);
 await until(async()=>{const s=await entryState();return s.entryResolved||s.error;},'initial refresh result');
 result.initial=await entryState();const initialSources=await invoke(port,'CHAT','list_desktop_workspaces');result.initialWorkspaces=initialSources.length;result.initialSources=[];for(const source of initialSources){const data=await invoke(port,'CHAT','read_desktop_workspace',{workspaceId:source.id});const counts=Object.fromEntries(['conversations','projects','knowledge','memory','relations'].map(key=>[key,(data.exchange[key]||[]).length]));assert.ok(source.id.startsWith('workspace-local-'),'fresh source must come from ordinary local owner, not acceptance import');assert.ok(Object.values(counts).every(n=>n===0),'fresh source cannot contain retained business records');result.initialSources.push({id:source.id,counts});}
 if(result.initial.entryResolved&&result.initial.catalogLoaded&&result.initial.defaultLoaded&&!result.initial.error){
  await clickUi(port,'[data-action=show-settings]');await clickUi(port,'[data-action=open-settings-page][data-page=model]');await clickUi(port,'[data-action=open-settings-page][data-page=model-configuration]');
  result.modelRows=await until(()=>evaluate(port,'document.querySelectorAll(".model-settings-model-option").length'),'actual global model catalog');
  await clickUi(port,'[data-action=show-chat][aria-label="返回应用"]');await until(()=>evaluate(port,'!!document.querySelector("#chat-composer")'),'new chat');
  const text='RELEASE_FIRST_ENTRY_SYNTHETIC_DRAFT';
  await evaluate(port,`(()=>{const i=document.querySelector('#chat-composer');i.value=${JSON.stringify(text)};i.dispatchEvent(new InputEvent('input',{bubbles:true}));return true})()`);
  const workspaces=await until(async()=>{const list=await invoke(port,'CHAT','list_desktop_workspaces');return list.length===1&&list;},'draft-created local source');result.workspaceId=workspaces[0].id;
  await until(async()=>{const draft=await invoke(port,'CHAT','read_desktop_conversation_draft',{workspaceId:result.workspaceId,conversationId:null});return draft.text===text;},'actual draft persisted');
  result.close=await normalClose();port=await start(true);await ready(port);await until(()=>evaluate(port,'__nanfengDesktopWorkState?.entryResolved===true'),'cold entry resolved');
  await until(()=>evaluate(port,`document.querySelector('#chat-composer')?.value===${JSON.stringify(text)}`),'cold draft restored');
  result.cold=await entryState();assert.equal(result.cold.currentId,result.workspaceId);assert.equal(result.cold.selected,null);assert.equal(result.cold.error,'');
  result.coldClose=await normalClose();result.pass=true;
 }else{result.close=await normalClose();result.pass=false;}
}catch(error){result.failure=String(error);throw error;}
finally{if(launch)try{result.failureClose=await normalClose();}catch(error){result.closeError=String(error);stop();}result.root=root;result.pids=pids;if(root)await writeFile(join(root,'release-fresh-entry-native-evidence.json'),JSON.stringify(result,null,2)+'\n');}
console.log(JSON.stringify({root,pass:result.pass,initial:result.initial,pids}));assert.ok(result.pass,'Fresh entry initialization failed; inspect owned evidence');
