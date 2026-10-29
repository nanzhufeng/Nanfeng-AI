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
const inputPath=inputIndex<0 ? join(desktopRoot,'../docs/review/20261017-windows-title-double-open/source-input-manifest-release-v1.json') : resolve(process.argv[inputIndex+1]);
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



const result={sha256:receipt.sha256,backgroundOnly:true,realService:false};let port=0;
const original='TITLE_DOUBLE_CLICK_SYNTHETIC';const renamed='双击改名保留草稿';
try{
 port=await start();result.root=root;await ready(port);result.runtimeInfo=await invoke(port,'CHAT','read_desktop_runtime_info');assert.equal(result.runtimeInfo.sourceInputSha256,sourceInputSha256);
 const ws=(await invoke(port,'CHAT','ensure_desktop_local_workspace')).summary.id;
 const created=await invoke(port,'CHAT','mutate_desktop_domain',{args:{workspaceId:ws,intentId:randomUUID(),entity:'conversation',action:'create',fields:{title:original,firstMessage:'SYNTHETIC TITLE SOURCE',projectId:null}}});const id=created.objectId;result.workspaceId=ws;result.conversationId=id;
 const read=async()=> (await invoke(port,'CHAT','read_desktop_workspace',{workspaceId:ws})).exchange.conversations.find(c=>c.id===id);
 result.before=await read();await evaluate(port,'location.reload();true');await ready(port);
 await until(()=>evaluate(port,'globalThis.__nanfengDesktopWorkState?.entryResolved===true'),'entry completed before title gesture');
 const selector='[data-conversation-row][data-id="'+id+'"] .chat-history-title';await until(()=>evaluate(port,'Boolean(document.querySelector('+JSON.stringify(selector)+'))'),'real sidebar title');
 await evaluate(port,`(()=>{const target=document.querySelector(${JSON.stringify(selector)});target.dispatchEvent(new MouseEvent('click',{detail:2,bubbles:true,cancelable:true}));target.dispatchEvent(new MouseEvent('dblclick',{detail:2,bubbles:true,cancelable:true}));return true})()`);
 await until(()=>evaluate(port,'Boolean(document.querySelector("[data-inline-conversation-rename]"))'),'one real title editor');assert.equal(await evaluate(port,'document.querySelector("[data-inline-conversation-rename]").value'),original);
 assert.equal(await evaluate(port,'document.querySelectorAll("[data-inline-conversation-rename]").length'),1);
 await evaluate(port,`(()=>{const input=document.querySelector('[data-inline-conversation-rename]');input.value=${JSON.stringify(renamed)};input.dispatchEvent(new Event('input',{bubbles:true}));input.dispatchEvent(new KeyboardEvent('keydown',{key:'Enter',bubbles:true,cancelable:true}));input.dispatchEvent(new FocusEvent('focusout',{bubbles:true}));return true})()`);
 await until(async()=> (await read()).title===renamed,'SQLite title saved');await until(()=>evaluate(port,'!document.querySelector("[data-inline-conversation-rename]")'),'editor closed after save');
 result.after=await read();assert.equal(result.after.revision,result.before.revision+1);assert.deepEqual(result.after.messages,result.before.messages);
 result.persistedRevisionDelta=result.after.revision-result.before.revision;assert.equal(result.persistedRevisionDelta,1);
 assert.equal((await invoke(port,'CHAT','read_desktop_usage_ledger')).records.length,0);await normalClose();
 port=await start(true);await ready(port);result.cold=await read();assert.deepEqual(result.cold,result.after);await until(()=>evaluate(port,'document.body.textContent.includes('+JSON.stringify(renamed)+')'),'cold sidebar title');assert.equal((await invoke(port,'CHAT','read_desktop_usage_ledger')).records.length,0);await normalClose();result.pass=true;
}catch(error){try{result.failureUi=await evaluate(port,`(()=>{const s=globalThis.__nanfengDesktopWorkState;return {entryResolved:s?.entryResolved,workspaceId:s?.current?.summary?.id,selected:s?.selectedConversationId,pane:s?.pane,dialog:s?.dialog?.kind,readOnly:s?.settingsConversationReturn?.readOnly,error:s?.error,edit:s?.inlineConversationRename?{id:s.inlineConversationRename.id,workspaceId:s.inlineConversationRename.workspaceId,draft:s.inlineConversationRename.draft,surface:s.inlineConversationRename.surface,error:s.inlineConversationRename.error}:null,inputs:[...document.querySelectorAll('[data-inline-conversation-rename]')].map(i=>({value:i.value,connected:i.isConnected}))}})()`);}catch{}result.pass=false;result.error=error.stack||String(error);process.exitCode=1;}
finally{if(launch){try{result.failureClose=await normalClose();}catch(error){result.forcedCleanup=true;result.closeError=String(error);stop();}}result.pids=pids;if(root)await writeFile(join(root,'title-double-open-native-evidence.json'),JSON.stringify(result,null,2),{flag:'wx'});}
console.log(JSON.stringify({root,pass:result.pass,error:result.error,pids}));
