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
  const args = ['-File', join(desktopRoot, 'scripts/start-windows-acceptance.ps1'), '-RootParent', rootParent,
    '-ExecutablePath',source,'-StartupMode', 'normal', '-DebugPort', String(port), '-OrdinaryChatTestEndpoint', `http://127.0.0.1:${providerPort}`];
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







const requests=[];
const provider=http.createServer((req,res)=>{requests.push(req.method);res.writeHead(500);res.end('No provider request authorized in this fixture');});
await new Promise(done=>provider.listen(0,'127.0.0.1',done));
const providerPort=provider.address().port;
const result={privateDesktopGuard,candidateSha256:receipt.sha256,backgroundOnly:true,realService:false};
let port,ws,conv,originalExchange,baselinePreference;
const python='C:/Users/Administrator/.cache/codex-runtimes/codex-primary-runtime/dependencies/python/python.exe';
function sql(statement){
 const code="import sys,json,sqlite3;from pathlib import Path;r=Path(sys.argv[1]);m=json.loads((r/'.nanfeng-windows-acceptance.json').read_text(encoding='utf-8-sig'));assert m['identifier']=='com.nanzhufeng.ai.desktop.windowsacceptance';c=sqlite3.connect(r/'workspace.sqlite3');c.execute(sys.argv[2]);c.commit();c.close()";
 execFileSync(python,['-X','utf8','-c',code,root,statement],{encoding:'utf8',windowsHide:true,timeout:10000});
}
async function selectTarget(){
 if(await evaluate(port,'__nanfengDesktopWorkState.p6gModelPickerOpen'))await clickUi(port,'[data-action="toggle-p6g-model-picker"]');
 await until(()=>evaluate(port,`Boolean(document.querySelector('[data-action="select-chat"][data-id="${conv.id}"]'))`),'target sidebar');
 await clickUi(port,`[data-action="select-chat"][data-id="${conv.id}"]`);
 await until(()=>evaluate(port,`__nanfengDesktopWorkState.selectedConversationId===${JSON.stringify(conv.id)}&&__nanfengDesktopWorkState.p6gSelection?.conversationOverride?.modelId==='CLAUDE_HAIKU_5_5'`),'target choice readback');
}
async function newChat(){if(await evaluate(port,'__nanfengDesktopWorkState.p6gModelPickerOpen'))await clickUi(port,'[data-action="toggle-p6g-model-picker"]');await clickUi(port,'.chat-header-content-actions [data-action="new-chat"]');await until(()=>evaluate(port,'__nanfengDesktopWorkState.selectedConversationId===null'),'new chat route');}
async function autoChoice(){
 if(!await evaluate(port,'__nanfengDesktopWorkState.p6gModelPickerOpen'))await clickUi(port,'[data-action="toggle-p6g-model-picker"]');
 await until(()=>evaluate(port,'Boolean(document.querySelector("[data-action=select-p6g-auto]"))'),'Auto option');
 await clickUi(port,'[data-action="select-p6g-auto"]');
}

function installDelayedModelFailure(){
 window.__modelSaveOriginalFetch=window.fetch.bind(window);
 window.__modelSaveHold=true;window.__modelProbeCalls=[];
 const wrapped=(input,init)=>{
  const url=typeof input==='string'?input:input.url;
  const command=decodeURIComponent(new URL(url,location.href).pathname).split('/').filter(Boolean).join('/');
  window.__modelProbeCalls.push(command);
  const response=window.__modelSaveOriginalFetch(input,init);
  if(command==='set_desktop_new_conversation_model'&&window.__modelSaveHold){
   window.__modelSaveHold=false;
   return response.then(reply=>{
    if(reply.headers.get('Tauri-Response')==='ok')throw new Error('Expected real SQLite failure');
    return new Promise(resolve=>{window.__modelSaveRejectedByStore=true;window.__releaseModelFailure=()=>resolve(reply);});
   });
  }
  return response;
 };
 window.fetch=wrapped;
 if(window.fetch!==wrapped)throw new Error('Fetch observer not installed');
}
async function scenario(){
 port=await start();await ready(port);result.runtimeInfo=await invoke(port,'CHAT','read_desktop_runtime_info');
 if(process.argv.includes('--inspect-bridge')){result.bridgeDescriptors=await evaluate(port,`(()=>{const shape=(o,k)=>{if(!o)return null;const d=Object.getOwnPropertyDescriptor(o,k);return {own:!!d,writable:d?.writable,configurable:d?.configurable,getter:!!d?.get,setter:!!d?.set,type:typeof o[k],frozen:Object.isFrozen(o)}};return {core:shape(window.__TAURI__?.core,'invoke'),internal:shape(window.__TAURI_INTERNALS__,'invoke'),internalIpc:shape(window.__TAURI_INTERNALS__,'ipc'),ipcPost:shape(window.ipc,'postMessage'),webviewPost:shape(window.chrome?.webview,'postMessage'),runCallback:shape(window.__TAURI_INTERNALS__,'runCallback')};})()`);result.inspectClose=await normalClose();result.pass=true;return;}
 ws=(await invoke(port,'CHAT','ensure_desktop_local_workspace')).summary.id;
 const service=(await invoke(port,'CHAT','read_desktop_model_service_settings')).find(s=>s.providerId==='OPENROUTER');
 await invoke(port,'CHAT','save_desktop_model_service_settings',{args:{providerId:'OPENROUTER',enabled:true,presetId:'CLAUDE_HAIKU_5_5',expectedRevision:service.revision,apiKey:'nfai-synthetic-model-save-only'}});
 const workspace=await invoke(port,'CHAT','read_desktop_workspace',{workspaceId:ws});conv=workspace.exchange.conversations[0];originalExchange=workspace.exchange;
 const selection=await invoke(port,'CHAT','read_desktop_p6g_selection',{workspaceId:ws,conversationId:conv.id});
 await invoke(port,'CHAT','set_desktop_p6g_conversation_override',{args:{workspaceId:ws,conversationId:conv.id,expectedRevision:selection.conversationOverride.revision,modelId:'CLAUDE_HAIKU_5_5'}});
 baselinePreference=await invoke(port,'CHAT','read_desktop_p6g_global_default');
 result.seedClose=await normalClose();port=await start(true);await ready(port);await selectTarget();await newChat();
 sql("CREATE TRIGGER reject_new_model_late_error BEFORE UPDATE ON p6g_composer_default BEGIN SELECT RAISE(ABORT,'synthetic model save ownership'); END");result.sqlFaultInjected=true;
 await evaluate(port,`(${installDelayedModelFailure.toString()})()`);
 await autoChoice();await until(()=>evaluate(port,'window.__modelSaveRejectedByStore===true'),'real SQLite failure held');
 await selectTarget();
 await evaluate(port,`(()=>{const s=__nanfengDesktopWorkState;s.error='目的会话原提示';s.status='目的会话原状态';const t=document.querySelector('#chat-composer');t.value='目的会话保留草稿';t.dispatchEvent(new InputEvent('input',{bubbles:true}));return true})()`);
 await clickUi(port,'[data-action="toggle-p6g-model-picker"]');
 await evaluate(port,'window.__releaseModelFailure();new Promise(resolve=>setTimeout(resolve,200))');
 const observed=await evaluate(port,`({error:__nanfengDesktopWorkState.error,status:__nanfengDesktopWorkState.status,id:__nanfengDesktopWorkState.selectedConversationId,picker:__nanfengDesktopWorkState.p6gModelPickerOpen,draft:document.querySelector('#chat-composer')?.value})`);
 assert.equal(observed.error,'目的会话原提示');assert.equal(observed.status,'目的会话原状态');assert.equal(observed.id,conv.id);assert.equal(observed.picker,true);assert.equal(observed.draft,'目的会话保留草稿');
 assert.deepEqual(await invoke(port,'CHAT','read_desktop_p6g_global_default'),baselinePreference);result.lateFailureKeptDestination=true;
 await newChat();await autoChoice();await until(()=>evaluate(port,"__nanfengDesktopWorkState.error.includes('型号选择未保存')"),'current failure remains visible');
 assert.deepEqual(await invoke(port,'CHAT','read_desktop_p6g_global_default'),baselinePreference);result.currentFailureVisible=true;
 sql('DROP TRIGGER reject_new_model_late_error');result.sqlFaultRemoved=true;
 await autoChoice();await until(async()=> (await invoke(port,'CHAT','read_desktop_p6g_global_default')).modelId===null,'same Auto entry recovery');
 result.savedPreference=await invoke(port,'CHAT','read_desktop_p6g_global_default');assert.equal(result.savedPreference.composerRevision,baselinePreference.composerRevision+1);result.sameOwnerRecovery=true;
 await evaluate(port,'window.fetch=window.__modelSaveOriginalFetch');
 assert.deepEqual((await invoke(port,'CHAT','read_desktop_workspace',{workspaceId:ws})).exchange,originalExchange);assert.equal((await invoke(port,'CHAT','read_desktop_usage_ledger')).records.length,0);
 result.closeAfterRecovery=await normalClose();port=await start(true);await ready(port);await selectTarget();
 await until(()=>evaluate(port,"document.querySelector('#chat-composer')?.value==='目的会话保留草稿'"),'destination draft cold restore');
 assert.deepEqual(await invoke(port,'CHAT','read_desktop_p6g_global_default'),result.savedPreference);assert.equal((await invoke(port,'CHAT','read_desktop_usage_ledger')).records.length,0);assert.equal(requests.length,0);
 result.coldDraftAndChoicePreserved=true;result.coldClose=await normalClose();result.pass=true;
}
try{await scenario();}catch(error){result.error=error.stack||String(error);process.exitCode=1;if(launch)try{result.failureProbe=await evaluate(port,'({calls:window.__modelProbeCalls,error:__nanfengDesktopWorkState.error,held:window.__modelSaveRejectedByStore,selected:__nanfengDesktopWorkState.selectedConversationId,picker:__nanfengDesktopWorkState.p6gModelPickerOpen})');}catch{}}
finally{
 if(root&&!result.sqlFaultRemoved)try{sql('DROP TRIGGER IF EXISTS reject_new_model_late_error');result.sqlFaultRemoved=true;}catch(error){result.sqlCleanupError=String(error);process.exitCode=1;}
 if(launch)try{result.failureClose=await normalClose();}catch(error){result.closeError=String(error);result.forcedCleanup=true;stop();}
 provider.closeAllConnections();await new Promise(done=>provider.close(done));result.root=root;result.requests=requests.length;result.pids=pids;if(root)await writeFile(join(root,'new-model-late-error-evidence.json'),JSON.stringify(result,null,2)+'\n');
}
console.log(JSON.stringify(result,null,2));
