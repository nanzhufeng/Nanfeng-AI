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
let stage='start';
try {
 let port=await start();result.root=root;await ready(port);
 const inputs=join(root,'synthetic-temporary-backup-inputs');await mkdir(inputs,{recursive:true});
 const sharedPath=join(inputs,'ordinary-shared.txt'),privatePath=join(inputs,'temporary-private.txt');
 await writeFile(sharedPath,'synthetic ordinary shared attachment');await writeFile(privatePath,'synthetic temporary-only attachment');
 result.sharedSha256=createHash('sha256').update(await readFile(sharedPath)).digest('hex');
 result.privateSha256=createHash('sha256').update(await readFile(privatePath)).digest('hex');
 stage='ordinary-shared';const workspace=await invoke(port,'CHAT','ensure_desktop_local_workspace');result.workspaceId=workspace.summary.id;
 result.ordinaryAttachment=await invoke(port,'CHAT','import_desktop_conversation_attachment',{args:{workspaceId:result.workspaceId,selectedPath:sharedPath}});
 await until(()=>evaluate(port,'Boolean(document.querySelector("[data-action=toggle-temporary-chat]"))'),'Ghost入口');
 await clickUi(port,'[data-action="toggle-temporary-chat"]');
 const temporary=await until(()=>invoke(port,'CHAT','read_desktop_temporary_conversation'),'Ghost临时owner');result.temporaryId=temporary.temporaryId;
 stage='temporary-fixtures';
 for(const selectedPath of [sharedPath,privatePath])await invoke(port,'CHAT','import_desktop_temporary_attachment',{args:{temporaryId:result.temporaryId,selectedPath}});
 await invoke(port,'CHAT','update_desktop_temporary_conversation',{args:{temporaryId:result.temporaryId,draft:'synthetic temporary private message',modelOverrideId:null}});
 await invoke(port,'CHAT','append_desktop_temporary_message',{args:{temporaryId:result.temporaryId}});
 result.before=await invoke(port,'CHAT','update_desktop_temporary_conversation',{args:{temporaryId:result.temporaryId,draft:'synthetic temporary private draft',modelOverrideId:null}});
 assert.equal(result.before.attachments.length,2);assert.equal(result.before.messages.length,1);
 stage='reject-legacy';
 if(process.argv[3]) {
  const legacy=resolve(process.argv[3]);const legacyRoot=dirname(dirname(legacy));
  assert.ok(legacyRoot.startsWith(resolve('D:/CodexTemp')+sep));
  assert.equal(JSON.parse(await readFile(join(legacyRoot,'.nanfeng-windows-acceptance.json'),'utf8')).identifier,receipt.identifier);
  const copied=join(inputs,'legacy-temporary-isolation.nfai-backup');await writeFile(copied,await readFile(legacy));
  let rejection='';try{await invoke(port,'CHAT','preflight_desktop_local_backup',{selectedPath:copied});}catch(error){rejection=String(error);}
  assert.ok(rejection.includes('备份包含临时聊天恢复记录'));result.legacyRejected=true;
  assert.deepEqual(await invoke(port,'CHAT','read_desktop_temporary_conversation'),result.before);
 }
 stage='export';result.selectedPath=join(inputs,'temporary-isolation.nfai-backup');
 result.artifact=await invoke(port,'CHAT','export_desktop_local_backup',{selectedPath:result.selectedPath});
 assert.deepEqual(await invoke(port,'CHAT','read_desktop_temporary_conversation'),result.before);
 assert.equal(await invoke(port,'WORK','read_desktop_temporary_conversation'),null);
 for(const hash of [result.privateSha256,result.sharedSha256])assert.equal(createHash('sha256').update(await readFile(join(root,'assets',hash))).digest('hex'),hash);
 result.afterExportPreserved=true;
 stage='close';result.closes=[await normalClose()];
 stage='cold';port=await start(true);await ready(port);assert.deepEqual(await invoke(port,'CHAT','read_desktop_temporary_conversation'),result.before);
 assert.equal(await invoke(port,'WORK','read_desktop_temporary_conversation'),null);
 result.coldPreserved=true;result.closes.push(await normalClose());
 stage='restore-new-root';port=await start();result.restoreRoot=root;await ready(port);
 const preflight=await invoke(port,'CHAT','preflight_desktop_local_backup',{selectedPath:result.selectedPath});
 const restored=await invoke(port,'CHAT','restore_desktop_local_backup',{fingerprint:preflight.fingerprint,replaceLocal:true});
 assert.equal(restored.restartRequired,true);result.closes.push(await normalClose());
 stage='restored-cold';port=await start(true);await ready(port);
 await until(async()=>{const status=await invoke(port,'CHAT','read_desktop_local_backup_status');return status.operation==='COMPLETED';},'恢复重启完成');
 assert.equal(await invoke(port,'CHAT','read_desktop_temporary_conversation'),null);
 assert.equal(await invoke(port,'WORK','read_desktop_temporary_conversation'),null);
 assert.equal(createHash('sha256').update(await readFile(join(root,'assets',result.sharedSha256))).digest('hex'),result.sharedSha256);
 let privatePresent=true;try{await readFile(join(root,'assets',result.privateSha256));}catch(error){if(error.code==='ENOENT')privatePresent=false;else throw error;}
 assert.equal(privatePresent,false);result.restoredIsolation=true;result.closes.push(await normalClose());
 await writeFile(join(result.root,'temporary-backup-native.json'),JSON.stringify({...result,pids},null,2));
 console.log(JSON.stringify({...result,before:undefined,pids}));
} catch(error) {console.error(JSON.stringify({stage,root,pid:launch?.pid,error:String(error)}));process.exitCode=1;}
finally {stop();}
