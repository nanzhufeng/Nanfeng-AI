import { writeFileSync } from 'node:fs';
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
  recordLifecycle('start-request',{reopen});
  if(resumeLaunch && !reopen){
   launch=resumeLaunch;resumeLaunch=null;assert.equal(launch.visible,false);assert.equal(launch.sha256,receipt.sha256);assert.equal(resolve(launch.executable),runExecutable);root=resolve(launch.root);assert.ok(root.startsWith(rootParent+sep));const marker=JSON.parse(await readFile(join(root,'.nanfeng-windows-acceptance.json'),'utf8'));assert.equal(marker.identifier,receipt.identifier);recordLifecycle('existing-launch-attached',{pid:launch.pid});return launch.debugPort;
  }
  const port = await reservePort();
  const args = ['-File', join(desktopRoot, 'scripts/start-windows-acceptance.ps1'), '-RootParent', rootParent,
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






assert.ok(process.argv.includes('--public-metadata-get'),'Explicit fixed unauthenticated public metadata GET gate required');
assert.ok(rootParent.startsWith(resolve(desktopRoot,'../.runtime-qa')+sep),'Only a new owned QA parent');
const events=[];let phase='boot';
const lifecycle=join(rootParent,`catalog-controller-${process.pid}.json`);
function recordLifecycle(event,detail={}){phase=event;events.push({event,...detail});try{writeFileSync(lifecycle,JSON.stringify({phase,root,pids,events},null,2)+'\n');}catch{}}
process.on('beforeExit',code=>recordLifecycle('before-exit',{code}));process.on('exit',code=>recordLifecycle('exit',{code}));process.on('uncaughtExceptionMonitor',error=>recordLifecycle('uncaught-exception',{error:String(error)}));
const resumeIndex=process.argv.indexOf('--resume-launch');let resumeLaunch=resumeIndex>=0?JSON.parse(await readFile(process.argv[resumeIndex+1],'utf8')):null;
recordLifecycle('controller-ready',{resume:Boolean(resumeLaunch)});
const legacyBytes=await readFile(new URL('../../protocol/fixtures/openrouter-registry-legacy-v1.json',import.meta.url));
const legacy=JSON.parse(legacyBytes);
const result={candidateSha256:receipt.sha256,backgroundOnly:true,publicMetadataOnly:true,publicSource:'https://openrouter.ai/api/v1/models',realModelRequests:false,stages:[]};let port;
const registry=()=>invoke(port,'CHAT','read_desktop_openrouter_registry_status');
const current=()=>join(root,'model-registry-openrouter-current-v1.json');
const previous=()=>join(root,'model-registry-openrouter-previous-v1.json');
async function openModels(){await clickUi(port,'[data-action=show-settings]');await clickUi(port,'[data-action=open-settings-page][data-page=model]');await clickUi(port,'[data-action=open-settings-page][data-page=model-configuration]');await clickUi(port,'[data-action=select-model-service-provider][data-provider-id=OPENROUTER]');await until(()=>evaluate(port,'!!document.querySelector("[data-action=refresh-openrouter-registry]")'),'public registry control');}
const argon=stored=>stored.catalog.models.find(model=>model.id==='google/gemini-4-argon');
try{
 port=await start();recordLifecycle('initial-ready-pending');await ready(port);recordLifecycle('initial-ready');await until(()=>evaluate(port,'Boolean(globalThis.__nanfengDesktopWorkState?.runtimeInfo?.dataAreasReady)'),'normal stores ready');result.runtimeInfo=await invoke(port,'CHAT','read_desktop_runtime_info');
 recordLifecycle('legacy-seed');await writeFile(current(),legacyBytes);const old=await registry();assert.equal(old.status,'VERIFIED');assert.equal(old.catalogVersion,legacy.catalog.catalogVersion);assert.ok(old.mappedPresetIds.includes('GEMINI_4_ARGON'));result.stages.push({phase:'legacy-v1-read',status:old.status,version:old.catalogVersion});
 recordLifecycle('public-refresh');await openModels();await clickUi(port,'[data-action=refresh-openrouter-registry]');
 const refreshed=await until(()=>evaluate(port,`(()=>{const s=__nanfengDesktopWorkState;if(s.openrouterRegistryRefreshing)return false;if(s.openrouterRegistryError)return {error:s.openrouterRegistryError};return s.openrouterRegistryStatus?.status==='VERIFIED'&&s.openrouterRegistryStatus?.catalogVersion&&s.openrouterRegistryStatus.catalogVersion!==${JSON.stringify(old.catalogVersion)}?s.openrouterRegistryStatus:false})()`),'explicit public metadata refresh');assert.ok(!refreshed.error,refreshed.error);assert.equal(refreshed.status,'VERIFIED');
 const freshBytes=await readFile(current());assert.ok(freshBytes.length<=4*1024*1024);const fresh=JSON.parse(freshBytes);const model=argon(fresh);assert.ok(model,'Argon public catalog entry');assert.equal(model.cachedInputMicrosPerToken,null);assert.equal(model.cachedInputMicrosPerMillionTokens,100000);assert.equal(model.inputMicrosPerMillionTokens,2000000);assert.equal(model.outputMicrosPerMillionTokens,10000000);
 assert.deepEqual(JSON.parse(await readFile(previous(),'utf8')),legacy);assert.equal(refreshed.catalogVersion,fresh.catalog.catalogVersion);
 result.stages.push({phase:'settings-refresh-and-persist',status:refreshed.status,version:refreshed.catalogVersion,models:fresh.catalog.models.length,cacheBytes:freshBytes.length,inputMicrosPerMillionTokens:model.inputMicrosPerMillionTokens,outputMicrosPerMillionTokens:model.outputMicrosPerMillionTokens,cachedInputMicrosPerMillionTokens:model.cachedInputMicrosPerMillionTokens,legacyCachedInputMicrosPerToken:model.cachedInputMicrosPerToken});
 await writeFile(join(root,'a9-verified-public-catalog-v1.json'),freshBytes);
 recordLifecycle('first-close');result.close=await normalClose();port=await start(true);await ready(port);const cold=await registry();assert.equal(cold.status,'VERIFIED');assert.equal(cold.catalogVersion,fresh.catalog.catalogVersion);assert.equal(argon(JSON.parse(await readFile(current(),'utf8'))).cachedInputMicrosPerMillionTokens,100000);result.stages.push({phase:'precise-cold-read',status:cold.status,version:cold.catalogVersion});result.coldClose=await normalClose();
 const corrupt=structuredClone(fresh);argon(corrupt).cachedInputMicrosPerMillionTokens=0;await writeFile(join(root,'a9-tampered-public-catalog-v1.json'),JSON.stringify(corrupt));await writeFile(current(),JSON.stringify(corrupt));
 port=await start(true);await ready(port);const fallback=await registry();assert.equal(fallback.status,'STABLE_FALLBACK');assert.equal(fallback.catalogVersion,legacy.catalog.catalogVersion);await openModels();await until(()=>evaluate(port,'__nanfengDesktopWorkState.openrouterRegistryStatus?.status==="STABLE_FALLBACK"'),'fallback status in settings');result.stages.push({phase:'tamper-rejected-legacy-fallback',status:fallback.status,version:fallback.catalogVersion});result.fallbackClose=await normalClose();
 await writeFile(current(),freshBytes);port=await start(true);await ready(port);const repaired=await registry();assert.equal(repaired.status,'VERIFIED');assert.equal(repaired.catalogVersion,fresh.catalog.catalogVersion);result.stages.push({phase:'verified-bytes-restored-cold-read',status:repaired.status,version:repaired.catalogVersion});result.repairedClose=await normalClose();result.pass=true;recordLifecycle('verified');
}catch(error){recordLifecycle('caught',{error:String(error)});result.error=error.stack||String(error);process.exitCode=1;}
finally{if(launch)try{result.failureClose=await normalClose();}catch(error){result.closeError=String(error);stop();}result.root=root;result.pids=pids;if(root)await writeFile(join(root,'catalog-precision-native-evidence.json'),JSON.stringify(result,null,2)+'\n');closeCdpSessions();}
console.log(JSON.stringify(result,null,2));
