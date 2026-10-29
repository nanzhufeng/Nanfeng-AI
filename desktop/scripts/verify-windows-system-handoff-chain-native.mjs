import assert from 'node:assert/strict';
import { createHash } from 'node:crypto';
import { execFileSync, spawn } from 'node:child_process';
import { copyFile, mkdir, readFile, writeFile } from 'node:fs/promises';
import { dirname, join, resolve, sep } from 'node:path';
import { fileURLToPath } from 'node:url';

if (process.platform !== 'win32') throw new Error('仅供 Windows 隔离候选验收。');
assert.ok(process.execArgv.includes('--max-old-space-size=2048'));
const desktopRoot = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const rootParent = resolve(process.argv.slice(2).find(arg => !arg.startsWith('--')) || 'D:\\CodexTemp');
const receipt = JSON.parse(await readFile(join(desktopRoot, 'output/windows-acceptance/current.json'), 'utf8'));
assert.equal(receipt.identifier, 'com.nanzhufeng.ai.desktop.windowsacceptance');
const executable = resolve(desktopRoot, 'output/windows-acceptance', receipt.sha256, 'NanfengAI-Windows-Acceptance.exe');
const runningExecutable = resolve(desktopRoot, 'output/windows-acceptance-binaries', receipt.sha256, 'nanfeng-ai-desktop-spike.exe');
assert.equal(resolve(receipt.executable), executable);
assert.equal(createHash('sha256').update(await readFile(executable)).digest('hex').toUpperCase(), receipt.sha256);
const quote = value => `'${String(value).replaceAll("'", "''")}'`;
const ps = script => execFileSync('pwsh', ['-NoProfile', '-Command', script], { encoding: 'utf8', timeout: 60000 }).trim();
async function reservePort() {
  const server = (await import('node:http')).createServer();
  await new Promise(done => server.listen(0, '127.0.0.1', done));
  const port = server.address().port;
  await new Promise(done => server.close(done));
  return port;
}
let launch = null;
let root = '';
const stoppedPids = [];
async function start(reopen = false) {
  const port = await reservePort();
  const args = ['-ExecutablePath', executable, '-RootParent', rootParent, '-StartupMode', 'normal', '-Visible', '-DebugPort', String(port)];
  if (reopen) args.push('-Root', root);
  const result = await new Promise((done, fail) => {
    const child = spawn('pwsh', ['-NoProfile', '-File', join(desktopRoot, 'scripts/start-windows-acceptance.ps1'), ...args], { stdio: ['ignore', 'pipe', 'pipe'] });
    let output = '';
    let error = '';
    const timer = setTimeout(() => { child.kill(); fail(new Error(`启动超时：${error}`)); }, 30000);
    child.stdout.on('data', chunk => {
      output += chunk.toString();
      const line = output.split(/\r?\n/).find(item => item.trim().startsWith('{') && item.includes('"pid"'));
      if (!line) return;
      clearTimeout(timer);
      try { done(JSON.parse(line)); } catch (cause) { fail(cause); }
      child.stdout.destroy();
      child.stderr.destroy();
      child.unref();
    });
    child.stderr.on('data', chunk => { error += chunk.toString(); });
    child.on('error', cause => { clearTimeout(timer); fail(cause); });
    child.on('exit', code => { if (!output.includes('"pid"')) { clearTimeout(timer); fail(new Error(`启动失败 ${code}：${error}`)); } });
  });
  assert.equal(result.sha256, receipt.sha256);
  assert.equal(resolve(result.executable), runningExecutable);
  launch = result;
  root = resolve(result.root);
  assert.ok(root.startsWith(rootParent + sep));
  assert.equal(JSON.parse(await readFile(join(root, '.nanfeng-windows-acceptance.json'), 'utf8')).identifier, receipt.identifier);
  return port;
}
function stop() {
  if (!launch) return;
  const pid = Number(launch.pid);
  ps(`$p=Get-CimInstance Win32_Process -Filter "ProcessId=${pid}"; if ($p) { if ([IO.Path]::GetFullPath([string]$p.ExecutablePath) -ne ${quote(runningExecutable)} -or (Get-FileHash -LiteralPath ${quote(runningExecutable)} -Algorithm SHA256).Hash -ne ${quote(receipt.sha256)}) { throw 'PID identity mismatch' }; Stop-Process -Id ${pid} -Force -ErrorAction Stop }`);
  stoppedPids.push(pid);
  launch = null;
}
async function evaluate(port, expression) {
  const tabs = await fetch(`http://127.0.0.1:${port}/json/list`).then(response => response.json());
  const tab = tabs.find(item => item.type === 'page' && item.url === 'http://tauri.localhost/');
  assert.ok(tab, 'WebView2 页面未就绪');
  const socket = new WebSocket(tab.webSocketDebuggerUrl);
  try {
    await new Promise((done, fail) => { socket.addEventListener('open', done, { once: true }); socket.addEventListener('error', fail, { once: true }); });
    return await new Promise((done, fail) => {
      const timer = setTimeout(() => fail(new Error('CDP evaluation timed out')), 20000);
      socket.addEventListener('message', event => {
        const message = JSON.parse(event.data);
        if (message.id !== 1) return;
        clearTimeout(timer);
        if (message.error || message.result?.exceptionDetails) fail(new Error(message.result?.exceptionDetails?.text || message.error?.message));
        else done(message.result?.result?.value);
      });
      socket.send(JSON.stringify({ id: 1, method: 'Runtime.evaluate', params: { expression, awaitPromise: true, returnByValue: true } }));
    });
  } finally { socket.close(); }
}
async function invoke(port, area, command, args = {}) {
  const value = await evaluate(port, `window.__TAURI__.core.invoke(${JSON.stringify(command)},${JSON.stringify(args)},${JSON.stringify({ headers: { 'x-nanfeng-data-area': area } })}).catch(error=>({__error:String(error)}))`);
  if (value?.__error) throw new Error(`${command}: ${value.__error}`);
  return value;
}
async function until(read, label) {
  const deadline = Date.now() + 30000;
  while (Date.now() < deadline) {
    try { const value = await read(); if (value) return value; } catch { /* Startup or redraw. */ }
    await new Promise(done => setTimeout(done, 200));
  }
  throw new Error(`${label} 未出现`);
}
const click = (port, selector) => evaluate(port, `(()=>{const node=document.querySelector(${JSON.stringify(selector)});if(!node)return false;node.click();return true})()`);
const dialogOpen = () => {
  const raw = ps(`& ${quote(join(desktopRoot, 'scripts/inspect-windows-file-dialog.ps1'))} -ProcessId ${Number(launch.pid)}`);
  const windows = raw ? JSON.parse(raw) : [];
  return windows.some(item => item.children?.some(child => child.class === '#32770'));
};


const normalCloses=[],checkpoints=[];
async function snapshot(port){const out={};for(const area of ['CHAT','WORK']){out[area]=[];for(const ws of await invoke(port,area,'list_desktop_workspaces'))out[area].push(await invoke(port,area,'read_desktop_workspace',{workspaceId:ws.id}));}return out;}
async function normalClose(port){
 checkpoints.push(await snapshot(port));const pid=Number(launch.pid);
 const info=JSON.parse(ps(`$p=Get-CimInstance Win32_Process -Filter "ProcessId=${pid}";if(!$p -or [IO.Path]::GetFullPath([string]$p.ExecutablePath) -ne ${quote(runningExecutable)} -or (Get-FileHash -LiteralPath ${quote(runningExecutable)}).Hash -ne ${quote(receipt.sha256)}){throw 'PID identity mismatch'};$n=Get-Process -Id ${pid};@{title=$n.MainWindowTitle;responding=$n.Responding;accepted=$n.CloseMainWindow()}|ConvertTo-Json -Compress`));
 assert.ok(info.title&&info.responding&&info.accepted);await until(()=>ps(`@(Get-CimInstance Win32_Process -Filter "ProcessId=${pid}").Count`)==='0','自然关闭');await until(()=>ps(`@(Get-CimInstance Win32_Process -Filter "Name='msedgewebview2.exe'"|Where-Object {$_.CommandLine -and $_.CommandLine.Contains(${quote(launch.webviewProfile)})}).Count`)==='0','本根WebView退出');stoppedPids.push(pid);normalCloses.push({...info,pid});launch=null;
}

const diagnosticRoot=resolve(desktopRoot,'../docs/review/20261004-system-handoff-chain');const facts={refusals:[],shareEvents:[]};let stage='start';const inspectOnly=process.argv.includes('--inspect-only');
function focusOwnedNative(){return JSON.parse(ps(`Add-Type 'using System;using System.Runtime.InteropServices;public static class NFAOwnedQueueFocus{[DllImport("user32.dll")]public static extern IntPtr GetForegroundWindow();[DllImport("user32.dll")]public static extern uint GetWindowThreadProcessId(IntPtr h,out uint p);[DllImport("kernel32.dll")]public static extern uint GetCurrentThreadId();[DllImport("user32.dll")]public static extern bool AttachThreadInput(uint a,uint b,bool attach);[DllImport("user32.dll")]public static extern bool SetForegroundWindow(IntPtr h);[DllImport("user32.dll")]public static extern bool BringWindowToTop(IntPtr h);}';$p=Get-Process -Id ${Number(launch.pid)};if([IO.Path]::GetFullPath($p.Path) -ne ${quote(runningExecutable)} -or (Get-FileHash $p.Path).Hash -ne ${quote(receipt.sha256)} -or !$p.Responding){throw 'Native activation identity mismatch'};$prior=[NFAOwnedQueueFocus]::GetForegroundWindow();[uint32]$priorPid=0;$thread=[NFAOwnedQueueFocus]::GetWindowThreadProcessId($prior,[ref]$priorPid);$current=[NFAOwnedQueueFocus]::GetCurrentThreadId();$attached=$false;try{if($thread -ne $current){$attached=[NFAOwnedQueueFocus]::AttachThreadInput($current,$thread,$true);if(!$attached){throw 'Scoped input queue attachment denied'}};[NFAOwnedQueueFocus]::BringWindowToTop($p.MainWindowHandle)|Out-Null;[NFAOwnedQueueFocus]::SetForegroundWindow($p.MainWindowHandle)|Out-Null}finally{if($attached -and ![NFAOwnedQueueFocus]::AttachThreadInput($current,$thread,$false)){throw 'Input queue detach failed'}};Start-Sleep -Milliseconds 300;$foreground=[NFAOwnedQueueFocus]::GetForegroundWindow();if($foreground -ne $p.MainWindowHandle){throw 'Owned scoped activation did not establish foreground'};@{pid=$p.Id;priorForegroundPid=$priorPid;foregroundHwnd=$foreground.ToInt64();inputQueueDetached=$true}|ConvertTo-Json -Compress`));}
async function events(){return (await readFile(join(root,'startup.stderr.log'),'utf8')).split(/\r?\n/).filter(l=>l.startsWith('windows-share:'));}
async function noUnexpectedCopies(){const out=JSON.parse(ps(`$out=@();foreach($name in @('share-outbox','system-open')){$path=Join-Path ${quote(root)} $name;if(Test-Path -LiteralPath $path){$out+=@(Get-ChildItem -LiteralPath $path -File -Recurse|Select-Object @{n='path';e={$_.FullName}},Length)}};ConvertTo-Json -InputObject @($out) -Compress`));return out;}
try{
 let port=await start();facts.root=root;await until(()=>evaluate(port,'Boolean(window.__TAURI__?.core?.invoke&&globalThis.__nanfengDesktopWorkState?.current)'),'Native ready');const workspaceId=(await invoke(port,'CHAT','ensure_desktop_local_workspace')).summary.id;facts.workspaceId=workspaceId;
 const payloadCode="import zipfile,hashlib,base64,sys\nz=zipfile.ZipFile(sys.argv[1]);found=[z.read(n) for n in z.namelist() if not n.endswith('/') and hashlib.sha256(z.read(n)).hexdigest()=='3f3fa3e4842fd4d54be489e980058c1aaf1bd89951dd0dd521e29f0b81639969'];assert len(found)==1 and len(found[0])==43;print(base64.b64encode(found[0]).decode())";
 const bytes=Buffer.from(execFileSync('python',['-c',payloadCode,resolve(desktopRoot,'../protocol/artifacts/nfai.exchange.v1.golden.nfai-exchange')],{encoding:'utf8'}).trim(),'base64');const path=join(root,'合成系统分享.txt');await writeFile(path,bytes);const attachment=await invoke(port,'CHAT','import_desktop_conversation_attachment',{args:{workspaceId,selectedPath:path}});facts.attachment=attachment;
 const created=await invoke(port,'CHAT','mutate_desktop_domain',{args:{intentId:'synthetic-handoff-source',workspaceId,entity:'conversation',action:'create',fields:{title:'合成系统入口来源',projectId:null,firstMessage:'只验证公开合成附件，取消系统分享',attachmentIds:[attachment.id]}}});facts.conversationId=created.objectId;const before=await snapshot(port);facts.before=before;
 stage='invalid references';for(const area of ['CHAT','WORK']){for(const command of ['share_desktop_attachment','open_desktop_attachment_with_system']){const args={workspaceId:area==='CHAT'?'synthetic-missing-workspace':workspaceId,attachmentId:attachment.id};await assert.rejects(()=>invoke(port,area,command,{args}),/本地附件不可用/);facts.refusals.push({area,command,invalidWorkspaceOrArea:true});}}
 await assert.rejects(()=>invoke(port,'CHAT','open_desktop_attachment_with_system',{args:{workspaceId,attachmentId:attachment.id}}),/受支持的系统打开/);facts.unsupportedTxtOpenRejected=true;
 for(const href of ['javascript:alert(1)','file:///C:/synthetic-private','mailto:synthetic@example.invalid','https://user:pass@example.invalid/private','https://','not a url']){await assert.rejects(()=>invoke(port,'CHAT','open_desktop_source_link',{args:{href}}),/来源网站地址/);facts.refusals.push({command:'open_desktop_source_link',href,rejected:true});}
 assert.deepEqual(await snapshot(port),before);assert.deepEqual(await noUnexpectedCopies(),[]);
 stage='actual preview share';await evaluate(port,'location.reload();true');await until(()=>evaluate(port,`Boolean(document.querySelector('[data-action="select-chat"][data-id="${created.objectId}"]'))`),'source visible');assert.equal(await click(port,`[data-action="select-chat"][data-id="${created.objectId}"]`),true);
 await until(()=>evaluate(port,`Boolean(document.querySelector('[data-action="open-text-preview"][data-attachment-id="${attachment.id}"]'))`),'actual attachment card');assert.equal(await click(port,`[data-action="open-text-preview"][data-attachment-id="${attachment.id}"]`),true);await until(()=>evaluate(port,'Boolean(document.querySelector("[data-action=share-preview-attachment]:not([disabled])"))'),'ready TXT preview');facts.previewVisible=await evaluate(port,'document.querySelector(".file-preview-top-actions")?.parentElement?.innerText');
 if(inspectOnly){facts.nativeWindowAudit=JSON.parse(ps(`Add-Type -AssemblyName UIAutomationClient;Add-Type -AssemblyName UIAutomationTypes;Add-Type 'using System;using System.Runtime.InteropServices;public static class NFWindowInspect{[DllImport("user32.dll")]public static extern IntPtr GetForegroundWindow();[DllImport("user32.dll")]public static extern bool IsWindowVisible(IntPtr h);[DllImport("user32.dll")]public static extern bool IsIconic(IntPtr h);[DllImport("user32.dll")]public static extern bool IsWindowEnabled(IntPtr h);[DllImport("user32.dll")]public static extern uint GetWindowThreadProcessId(IntPtr h,out uint pid);}';$p=Get-Process -Id ${Number(launch.pid)};$u=[System.Windows.Automation.AutomationElement]::FromHandle($p.MainWindowHandle);$fg=[NFWindowInspect]::GetForegroundWindow();[uint32]$fgPid=0;[NFWindowInspect]::GetWindowThreadProcessId($fg,[ref]$fgPid)|Out-Null;@{pid=$p.Id;hwnd=$p.MainWindowHandle.ToInt64();title=$p.MainWindowTitle;visible=[NFWindowInspect]::IsWindowVisible($p.MainWindowHandle);minimized=[NFWindowInspect]::IsIconic($p.MainWindowHandle);enabled=[NFWindowInspect]::IsWindowEnabled($p.MainWindowHandle);uiaFocusable=$u.Current.IsKeyboardFocusable;uiaOffscreen=$u.Current.IsOffscreen;foregroundPid=$fgPid;foregroundProcess=(Get-Process -Id $fgPid).ProcessName;foregroundHwnd=$fg.ToInt64()}|ConvertTo-Json -Compress`));console.log(JSON.stringify(facts.nativeWindowAudit));await writeFile(join(diagnosticRoot,'native-window-audit.json'),JSON.stringify(facts.nativeWindowAudit,null,2)+'\n');await normalClose(port);facts.sha256=receipt.sha256;facts.normalCloses=normalCloses;facts.shareSkippedForFocusAudit=true;await writeFile(join(diagnosticRoot,'native-focus-audit.json'),JSON.stringify(facts,null,2)+'\n');}
 if(!inspectOnly){facts.ownedActivation=focusOwnedNative();
 await writeFile(join(root,'share-ui-current-session.json'),JSON.stringify({launch,candidate:receipt.sha256}));assert.equal(await click(port,'[data-action="share-preview-attachment"]'),true);await until(async()=> (await events()).includes('windows-share:package-ready'),'real system package ready');facts.shareEvents=await events();
 stage='cancel exact system host';facts.cancel=JSON.parse(ps(`& ${quote(join(desktopRoot,'scripts/invoke-windows-share-local-target.ps1'))} -Root ${quote(root)} -Action Cancel -SessionPath ${quote(join(root,'share-ui-current-session.json'))}`));assert.equal(facts.cancel.action,'Cancel');assert.equal(facts.cancel.leaseBytes,43);assert.equal(facts.cancel.sourcePid,launch.pid);await until(()=>evaluate(port,'Boolean(document.querySelector("[data-action=close-text-preview]"))'),'source window responsive');assert.equal(await click(port,'[data-action="close-text-preview"]'),true);
 const after=await snapshot(port);for(const area of ['CHAT','WORK'])for(let i=0;i<before[area].length;i++)assert.deepEqual(after[area][i].exchange,before[area][i].exchange);facts.after=after;
 await normalClose(port);facts.afterNormalCloseCopies=await noUnexpectedCopies();assert.equal(facts.afterNormalCloseCopies.length,0,'cancelled share leases released after owner close');}
 stage='normal cold';port=await start(true);await until(()=>evaluate(port,'Boolean(window.__TAURI__?.core?.invoke)'),'cold Native');assert.deepEqual(await snapshot(port),checkpoints[0]);assert.deepEqual(await noUnexpectedCopies(),[]);facts.coldExchangeExact=true;await normalClose(port);facts.normalCloses=normalCloses;facts.sha256=receipt.sha256;facts.pass=!inspectOnly;facts.shareSkippedForFocusAudit=inspectOnly;
 await mkdir(diagnosticRoot,{recursive:true});await writeFile(join(diagnosticRoot,'native-current.json'),JSON.stringify(facts,null,2)+'\n');console.log(JSON.stringify({ok:facts.pass,shareSkippedForFocusAudit:inspectOnly,sha256:receipt.sha256,root,normalCloses:normalCloses.length,receiverSelected:false}));
}catch(error){await mkdir(diagnosticRoot,{recursive:true});await writeFile(join(diagnosticRoot,`native-failed-${Date.now()}.json`),JSON.stringify({...facts,stage,error:String(error),stack:error.stack,sha256:receipt.sha256,normalCloses},null,2)+'\n');throw error;}finally{stop();}
