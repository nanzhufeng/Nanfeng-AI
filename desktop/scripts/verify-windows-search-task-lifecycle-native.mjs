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
    '-StartupMode', 'normal', '-Visible', '-DebugPort', String(port)];
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

const result={candidateSha256:receipt.sha256,startupMode:'normal',realService:false};
let activePort,stage='start';
const png=Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+/6XcAAAAASUVORK5CYII=','base64');
const needle='searchtasksynthetic';
async function search(port){
 await until(()=>evaluate(port,'Boolean(document.querySelector("#chat-search"))'),'会话搜索输入');
 await evaluate(port,`(()=>{const input=document.querySelector('#chat-search');input.value=${JSON.stringify(needle)};input.dispatchEvent(new Event('input',{bubbles:true}));input.dispatchEvent(new KeyboardEvent('keydown',{key:'Enter',bubbles:true}));return true})()`);
 await until(()=>evaluate(port,`globalThis.__nanfengDesktopWorkState.searchPanel && !globalThis.__nanfengDesktopWorkState.searchLoading && globalThis.__nanfengDesktopWorkState.chatSearch===${JSON.stringify(needle)}`),'搜索返回');
}
async function locateTask(port,taskId){
 const entryId=taskId+':source';
 await until(()=>evaluate(port,`Boolean(document.querySelector('[data-search-entry-id="${entryId}"]'))`),'准确源文件搜索卡');
 assert.equal(await evaluate(port,`(()=>{const card=document.querySelector('[data-search-entry-id="${entryId}"]');card.dispatchEvent(new MouseEvent('contextmenu',{bubbles:true,cancelable:true}));return true})()`),true);
 await until(()=>evaluate(port,'Boolean(document.querySelector("[data-action=locate-search-attachment]"))'),'附件定位菜单');
 await clickUi(port,'[data-action="locate-search-attachment"]');
}
try{
 let port=await start();activePort=port;result.root=root;await ready(port);
 const fixtureDir=join(root,'synthetic-search-task-inputs');await mkdir(fixtureDir,{recursive:true});
 const paths={};for(const name of ['deleted','kept']){paths[name]=join(fixtureDir,needle+'-'+name+'.png');await writeFile(paths[name],png);}
 result.tasks={};result.workspaces={};
 for(const area of ['CHAT','WORK']){
  const workspace=(await invoke(port,area,'ensure_desktop_local_workspace')).summary.id;result.workspaces[area]=workspace;
  const names=area==='CHAT'?['kept','deleted']:['deleted'];result.tasks[area]=[];
  for(const name of names){const task=await invoke(port,area,'import_desktop_ocr_source',{args:{workspaceId:workspace,selectedPath:paths[name]}});assert.equal(task.state,'QUEUED');result.tasks[area].push({id:task.id,sourceAttachmentId:task.sourceAttachmentId,name});}
 }
 await clickUi(port,'[data-action="show-transcription"]');
 await until(()=>evaluate(port,'globalThis.__nanfengDesktopWorkState.pane==="transcription" && globalThis.__nanfengDesktopWorkState.transcription?.tasks.length===2'),'任务页读取两真实任务');
 await clickUi(port,'[data-action="show-chat"]');await search(port);
 const kept=result.tasks.CHAT.find(x=>x.name==='kept'),deleted=result.tasks.CHAT.find(x=>x.name==='deleted');
 const beforeHits=await evaluate(port,'globalThis.__nanfengDesktopWorkState.searchPage.hits.map(h=>({entryId:h.entryId,conversationId:h.conversationId,attachmentId:h.attachmentId,workspaceId:h.workspaceId}))');
 assert.deepEqual(new Set(beforeHits.map(x=>x.entryId)),new Set([kept.id+':source',deleted.id+':source']));result.beforeHits=beforeHits;
 stage='live-task-locate';await locateTask(port,deleted.id);
 await until(()=>evaluate(port,`globalThis.__nanfengDesktopWorkState.pane==='transcription' && globalThis.__nanfengDesktopWorkState.selectedTranscriptionTaskId===${JSON.stringify(deleted.id)}`),'准确活任务定位');
 await clickUi(port,'[data-action="show-chat"]');await search(port);
 stage='stale-task-locate';await invoke(port,'CHAT','delete_desktop_transcription_task',{taskId:deleted.id});
 assert.equal(await evaluate(port,`globalThis.__nanfengDesktopWorkState.searchPage.hits.some(h=>h.entryId===${JSON.stringify(deleted.id+':source')})`),true,'保留删除前真实UI结果以验证旧卡');
 await locateTask(port,deleted.id);
 await until(()=>evaluate(port,`(!globalThis.__nanfengDesktopWorkState.transcription.tasks.some(t=>t.id===${JSON.stringify(deleted.id)}) && globalThis.__nanfengDesktopWorkState.selectedTranscriptionTaskId!==${JSON.stringify(deleted.id)}) || Boolean(globalThis.__nanfengDesktopWorkState.searchError)`),'旧卡读取完成或明确失效提示');
 result.staleUi=await evaluate(port,'({pane:globalThis.__nanfengDesktopWorkState.pane,searchPanel:globalThis.__nanfengDesktopWorkState.searchPanel,selectedTaskId:globalThis.__nanfengDesktopWorkState.selectedTranscriptionTaskId,error:globalThis.__nanfengDesktopWorkState.searchError,hits:globalThis.__nanfengDesktopWorkState.searchPage.hits.map(h=>h.entryId)})');
 console.log(JSON.stringify({stage,candidateSha256:receipt.sha256,root,staleUi:result.staleUi,keptTaskId:kept.id,deletedTaskId:deleted.id}));
 assert.equal(result.staleUi.searchPanel,true,'删除后旧卡必须保持搜索画布');assert.notEqual(result.staleUi.pane,'transcription','旧卡不得打开另一个任务');assert.match(result.staleUi.error,/转写任务.*删除/);assert.deepEqual(result.staleUi.hits,[kept.id+':source']);
 stage='remaining-task-locate-and-delete';await clickUi(port,'[data-action="retry-full-search"]');
 await until(()=>evaluate(port,'!globalThis.__nanfengDesktopWorkState.searchError && !globalThis.__nanfengDesktopWorkState.searchLoading'),'显式重试搜索恢复结果画布');
 const cancelled=await invoke(port,'CHAT','cancel_desktop_transcription_task',{taskId:kept.id});
 assert.equal(cancelled.state,'CANCELLED');result.cancelledBeforeVisibleDelete=true;
 await locateTask(port,kept.id);
 await until(()=>evaluate(port,`globalThis.__nanfengDesktopWorkState.selectedTranscriptionTaskId===${JSON.stringify(kept.id)} && globalThis.__nanfengDesktopWorkState.pane==='transcription'`),'剩余准确任务定位');
 await clickUi(port,`[data-action="ask-delete-transcription-task"][data-task-id="${kept.id}"]`);
 await until(()=>evaluate(port,'Boolean(document.querySelector("[data-action=confirm-delete-transcription-task]"))'),'任务删除确认');
 await clickUi(port,'[data-action="confirm-delete-transcription-task"]');
 await until(()=>evaluate(port,'!globalThis.__nanfengDesktopWorkState.dialog && globalThis.__nanfengDesktopWorkState.transcription.tasks.length===0'),'实际删除任务后空投影');
 await clickUi(port,'[data-action="show-chat"]');await search(port);
 await until(()=>evaluate(port,'globalThis.__nanfengDesktopWorkState.searchPage.hits.length===0'),'实际删除后的原生查询替换旧缓存');
 result.beforeCold={};for(const area of ['CHAT','WORK'])result.beforeCold[area]=await invoke(port,area,'read_desktop_transcription_state',{workspaceId:null});
 assert.equal(result.beforeCold.CHAT.tasks.length,0);assert.equal(result.beforeCold.WORK.tasks.length,1);
 const query={args:{query:needle,category:'all',sortMode:'default',fileType:'all',offset:0,recordHistory:false,historyWorkspaceId:null}};
 result.workHits=(await invoke(port,'WORK','query_desktop_local_index',{...query,requestId:700001})).hits;assert.equal(result.workHits.length,1);assert.equal(result.workHits[0].conversationId,'transcription-task:'+result.tasks.WORK[0].id);
 result.firstNormalClose=await normalClose();port=await start(true);activePort=port;await ready(port);stage='cold-read';
 for(const area of ['CHAT','WORK']){assert.deepEqual(await invoke(port,area,'read_desktop_transcription_state',{workspaceId:null}),result.beforeCold[area]);const page=await invoke(port,area,'query_desktop_local_index',{...query,requestId:700002});assert.equal(page.hits.length,area==='CHAT'?0:1);}
 await search(port);await until(()=>evaluate(port,'globalThis.__nanfengDesktopWorkState.searchPage.hits.length===0'),'cold搜索空结果');
 result.secondNormalClose=await normalClose();result.pass=true;
}catch(error){result.error=String(error.stack||error);result.stage=stage;process.exitCode=1;}
finally{if(launch){result.forcedCleanup=true;stop();}result.pids=pids;if(root)await writeFile(join(root,'search-task-lifecycle-evidence.json'),JSON.stringify(result,null,2),{flag:'wx'});}
console.log(JSON.stringify({candidateSha256:receipt.sha256,root,pass:result.pass,stage:result.stage,error:result.error,pids}));

