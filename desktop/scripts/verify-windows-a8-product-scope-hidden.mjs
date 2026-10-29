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
    '-StartupMode', 'normal', '-DebugPort', String(port), '-OrdinaryChatTestEndpoint', `http://127.0.0.1:${providerPort}`];
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




// All inputs and IPC mutations belong to a freshly marked synthetic root.
const providerPort=await reservePort(); // No server, credentials or generation requests.
const result={candidateSha256:receipt.sha256,backgroundOnly:true,realService:false,closes:[],observations:[]};
let port=0;
const seenErrors=[];
async function openSource(id){
 await clickUi(port,`[data-action="select-chat-source"][data-id="${id}"]`);
 await until(()=>evaluate(port,`globalThis.__nanfengDesktopWorkState.current?.summary?.id===${JSON.stringify(id)} && !!document.querySelector('#chat-composer')`),'accurate history source');
}
async function captureErrors(){await evaluate(port,`(()=>{window.__scopeErrors=[];addEventListener('error',e=>__scopeErrors.push(String(e.message)));addEventListener('unhandledrejection',e=>__scopeErrors.push(String(e.reason)));return true})()`);}
try{
 port=await start();await ready(port);await until(()=>evaluate(port,'globalThis.__nanfengDesktopWorkState?.runtimeInfo?.dataAreasReady===true'),'runtime');await captureErrors();
 const ids={};for(const area of ['CHAT','WORK'])ids[area]=(await invoke(port,area,'ensure_desktop_local_workspace')).summary.id;
 const mutate=(area,entity,fields)=>invoke(port,area,'mutate_desktop_domain',{args:{intentId:`a8-scope-${randomUUID()}`,workspaceId:ids[area],entity,action:'create',objectId:null,expectedRevision:null,fields}});
 await mutate('WORK','project',{title:'合成隐藏项目',description:'KEEP_WORK_PROJECT'});
 await mutate('WORK','knowledge',{title:'合成隐藏知识',body:'KEEP_WORK_KNOWLEDGE',scope:'GLOBAL',projectId:null,tags:[]});
 await mutate('WORK','memory',{title:'合成隐藏记忆',body:'KEEP_WORK_MEMORY',scope:'GLOBAL',scopeId:null});
 const created=await mutate('CHAT','conversation',{title:'合成来源原聊天',firstMessage:'KEEP_CHAT_MESSAGE',projectId:null});
 const fixture=join(root,'a8-synthetic-history.nfai-exchange');
 await invoke(port,'CHAT','export_desktop_workspace_to_selected_path',{workspaceId:ids.CHAT,selectedPath:fixture});
 const preflight=await invoke(port,'CHAT','stage_preflight_selected_exchange',{selectedPath:fixture});
 const imported=await invoke(port,'CHAT','import_staged_exchange_as_new_workspace',{args:{stagingId:preflight.stagingId,workspaceTitle:'合成历史 <来源> & 工作区'}});
 ids.IMPORTED=imported.summary.id;
 await mutate('CHAT','memory',{title:'合成聊天记忆',body:'KEEP_CHAT_MEMORY',scope:'GLOBAL',scopeId:null});
 const before={CHAT:await invoke(port,'CHAT','read_desktop_workspace',{workspaceId:ids.CHAT}),WORK:await invoke(port,'WORK','read_desktop_workspace',{workspaceId:ids.WORK}),IMPORTED:await invoke(port,'CHAT','read_desktop_workspace',{workspaceId:ids.IMPORTED})};
 result.ids=ids;
 result.seedClose=await normalClose();port=await start(true);await ready(port);await until(()=>evaluate(port,'globalThis.__nanfengDesktopWorkState?.runtimeInfo?.dataAreasReady===true'),'seed cold ready');await captureErrors();
 const retired='[data-action=show-work],[data-action=show-projects],[data-action=show-knowledge],[data-action=show-p8-inspect],[data-action=select-workspace]';
 const draft='A8_SCOPE_SOURCE_DRAFT_LOCAL_ONLY';
 for(let round=0;round<2;round++){
  if(round){port=await start(true);await ready(port);await until(()=>evaluate(port,'globalThis.__nanfengDesktopWorkState?.runtimeInfo?.dataAreasReady===true'),'cold ready');await captureErrors();}
  await until(()=>evaluate(port,'!!document.querySelector("#chat-composer")'),'chat');
  assert.equal(await evaluate(port,`document.querySelectorAll(${JSON.stringify(retired)}).length`),0);
  await openSource(ids.CHAT);await clickUi(port,`[data-action="select-chat"][data-id="${created.objectId}"]`);
  await until(()=>evaluate(port,'document.querySelector(".chat-scroll")?.textContent.includes("KEEP_CHAT_MESSAGE")'),'retained transcript');
  if(!round)await evaluate(port,`(()=>{const i=document.querySelector('#chat-composer');i.value=${JSON.stringify(draft)};i.dispatchEvent(new InputEvent('input',{bubbles:true}));return true})()`);
  else await until(()=>evaluate(port,`document.querySelector('#chat-composer')?.value===${JSON.stringify(draft)}`),'cold draft');
  await openSource(ids.IMPORTED);
  const sourceTitle=await evaluate(port,`document.querySelector('[data-action="select-chat-source"][data-id="${ids.IMPORTED}"]')?.textContent`);
  assert.equal(sourceTitle,'合成历史 <来源> & 工作区');
  assert.equal(await evaluate(port,'document.querySelectorAll(".chat-history-sources script").length'),0);
  const importedConversation=imported.exchange.conversations.find(c=>!c.deletedAt);assert.ok(importedConversation);
  await clickUi(port,`[data-action="select-chat"][data-id="${importedConversation.id}"]`);
  await until(()=>evaluate(port,`document.querySelector('.chat-scroll')?.dataset.conversationId===${JSON.stringify(importedConversation.id)}`),'imported transcript');
  await openSource(ids.CHAT);await clickUi(port,`[data-action="select-chat"][data-id="${created.objectId}"]`);
  await until(()=>evaluate(port,`document.querySelector('#chat-composer')?.value===${JSON.stringify(draft)}`),'source-specific draft restored');
  for(const [action,selector] of [['show-reminders','.desktop-reminders-page'],['show-transcription','.transcription-page']]){
   await clickUi(port,`[data-action="${action}"]`);await until(()=>evaluate(port,`!!document.querySelector(${JSON.stringify(selector)})`),'utility body');
   assert.ok(await evaluate(port,`document.querySelector(${JSON.stringify(selector)}).textContent.trim().length>0`));
   result.observations.push({round,action,bodyPresent:true});
  }
  await clickUi(port,'[data-action=show-settings]');
  for(const page of ['personalization','memory-overview','reminders','data','privacy','about','feature-review']){
   await clickUi(port,`[data-action="open-settings-page"][data-page="${page}"]`);
   await until(()=>evaluate(port,`globalThis.__nanfengDesktopWorkState.settingsSection===${JSON.stringify(page)}`),'settings page');
   if(page==='memory-overview')await until(()=>evaluate(port,'document.querySelector(".android-settings-secondary")?.textContent.includes("KEEP_CHAT_MEMORY")'),'chat memory visible');
   assert.equal(await evaluate(port,'document.querySelectorAll("[data-page=workspace],[data-page=development],[data-action=select-local-backup-area][data-area=WORK]").length'),0);
   result.observations.push({round,page,retiredControls:0});
  }
  result.blockedActions=await evaluate(port,`(()=>{const s=__nanfengDesktopWorkState;return ['show-work','show-projects','show-knowledge','show-memory','show-p8-inspect','select-workspace','reference-message-to-other-area'].map(action=>{const n=document.createElement('button');n.dataset.action=action;n.dataset.id='invalid-retired-target';document.querySelector('#app').append(n);const consumed=!n.dispatchEvent(new MouseEvent('click',{bubbles:true,cancelable:true}));n.remove();return {action,consumed,pane:s.pane,area:s.dataArea}})})()`);
  assert.ok(result.blockedActions.every(x=>x.consumed&&x.pane==='settings'&&x.area==='CHAT'));
  const after={CHAT:await invoke(port,'CHAT','read_desktop_workspace',{workspaceId:ids.CHAT}),WORK:await invoke(port,'WORK','read_desktop_workspace',{workspaceId:ids.WORK}),IMPORTED:await invoke(port,'CHAT','read_desktop_workspace',{workspaceId:ids.IMPORTED})};
  assert.deepEqual(after,before,'UI traversal and draft changes preserve all three domain snapshots');
  const saved=await invoke(port,'CHAT','read_desktop_conversation_draft',{workspaceId:ids.CHAT,conversationId:created.objectId});assert.equal(saved.text,draft);
  const errors=await evaluate(port,'window.__scopeErrors');seenErrors.push(...errors);assert.deepEqual(errors,[]);
  result.closes.push(await normalClose());
 }
 result.snapshotsPreserved=true;result.sourceDraftPreserved=true;result.errors=seenErrors;result.pass=true;
}catch(error){result.error=String(error);if(port)try{result.failureState=await evaluate(port,'({pane:__nanfengDesktopWorkState?.pane,page:__nanfengDesktopWorkState?.settingsSection,error:__nanfengDesktopWorkState?.error,errors:window.__scopeErrors})');}catch{}throw error;}
finally{if(launch)try{result.failureClose=await normalClose();}catch(e){result.closeError=String(e);stop();}result.root=root;result.pids=pids;if(root)await writeFile(join(root,'a8-product-scope-native-evidence.json'),JSON.stringify(result,null,2)+'\n');}
console.log(JSON.stringify({root,pass:result.pass,pids,candidate:receipt.sha256}));
