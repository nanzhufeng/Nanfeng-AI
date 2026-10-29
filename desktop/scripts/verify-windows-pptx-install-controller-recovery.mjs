import { completeLaunchReceipt } from './windows-launch-receipt.mjs';
import assert from 'node:assert/strict';
import { createHash, randomUUID } from 'node:crypto';
import { execFileSync, spawn } from 'node:child_process';
import { readFile, writeFile, stat, mkdir, readdir } from 'node:fs/promises';
import http from 'node:http';
import { createReadStream } from 'node:fs';
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
const source = resolve(desktopRoot, 'output/windows-acceptance-release', receipt.sha256, 'NanfengAI-Windows-Acceptance.exe');
assert.equal(resolve(receipt.executable), source);
assert.equal(createHash('sha256').update(await readFile(source)).digest('hex').toUpperCase(), receipt.sha256);
const immutableExecutable = resolve(desktopRoot, 'output/windows-acceptance-binaries', receipt.sha256, 'nanfeng-ai-desktop-spike.exe');
let runExecutable='';
const ps = (args) => execFileSync('pwsh', ['-NoProfile', ...(args[0]==='-Command'?['-Command',"$env:PSModulePath=[IO.Path]::Combine($PSHOME,'Modules');[Console]::OutputEncoding=[Text.UTF8Encoding]::new($false);"+args[1]]:args)], { encoding: 'utf8', timeout: 60000, windowsHide: true }).trim();
const privateDesktopGuard=JSON.parse(ps(['-File',join(desktopRoot,'scripts/assert-windows-private-desktop.ps1')]));
assert.equal(privateDesktopGuard.verified,true);
const launchPowerShell = (args,program='pwsh') => new Promise((resolveLaunch, reject) => {
  const child = spawn(program, program==='cmd.exe'?args:['-NoProfile', ...args], { windowsVerbatimArguments:program==='cmd.exe', windowsHide: true, stdio: ['ignore', 'pipe', 'pipe'] });
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
    '-StartupMode', 'normal', '-DebugPort', String(port), '-ExecutablePath',runExecutable,'-RunInPlace'];
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
      const hidden=JSON.parse(ps(['-File',join(desktopRoot,'scripts',runExecutable===immutableExecutable?'close-windows-hidden-acceptance.ps1':'close-windows-installed-hidden-acceptance.ps1'),
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
  const info=JSON.parse(ps(['-File', join(desktopRoot,'scripts',runExecutable===immutableExecutable?'close-windows-hidden-acceptance.ps1':'close-windows-installed-hidden-acceptance.ps1'),
    '-ProcessId',String(pid),'-Executable',runExecutable,'-Sha256',receipt.sha256,
    '-StartedAt',launch.processStartedAt]));
  assert.equal(info.visibleWindows,0);assert.equal(info.closePosted,true);
  await until(()=>ps(['-Command',`@(Get-CimInstance Win32_Process -Filter "ProcessId=${pid}").Count`])==='0','自然关闭退出');
  await until(()=>ps(['-Command',`@(Get-CimInstance Win32_Process -Filter "Name='msedgewebview2.exe'" | Where-Object {$_.CommandLine -and $_.CommandLine.Contains(${quote(launch.webviewProfile)})}).Count`])==='0','本根WebView退出');
  pids.push(pid);launch=null;return info;
}







const quote=value=>`'${String(value).replaceAll("'","''")}'`;
const hash=async path=>{const h=createHash('sha256');for await(const bytes of createReadStream(path))h.update(bytes);return h.digest('hex').toUpperCase();};
assert.ok(rootParent.startsWith(resolve(desktopRoot,'../.runtime-qa')+sep),'Only owned repository QA parent');

const previousEvidence=JSON.parse(await readFile(join(rootParent,'native-delivery-evidence.json'),'utf8'));
assert.equal(previousEvidence.phase,'installed-bat-started');assert.equal(previousEvidence.candidateSha256,receipt.sha256);assert.equal(previousEvidence.installExit,0);assert.equal(previousEvidence.occupiedUninstallExit,2);
root=resolve(previousEvidence.root);assert.ok(root.startsWith(rootParent+sep));assert.equal(JSON.parse(await readFile(join(root,'.nanfeng-windows-acceptance.json'),'utf8')).identifier,receipt.identifier);
const installDir=resolve(previousEvidence.installDir);assert.equal(dirname(installDir),rootParent);
const installedExecutable=join(installDir,'nanfeng-ai-desktop-spike.exe');runExecutable=installedExecutable;
assert.equal(await hash(installedExecutable),receipt.sha256);
const packageReceipt=JSON.parse(await readFile(join(desktopRoot,'output/windows-alignment-acceptance/current-release.json'),'utf8'));
assert.equal(packageReceipt.applicationIdentifier,receipt.identifier);assert.equal(packageReceipt.sha256,receipt.sha256);
const packageDir=dirname(packageReceipt.executable);
assert.ok(packageDir.startsWith(resolve(desktopRoot,'output/windows-alignment-acceptance')+sep));
assert.equal(await hash(packageReceipt.executable),receipt.sha256);
assert.equal(await hash(packageReceipt.installer),packageReceipt.installerSha256);
const newKey=String.raw`HKCU:\Software\Microsoft\Windows\CurrentVersion\Uninstall\南枫 AI Windows 对齐验收`;
const registration=()=>JSON.parse(ps(['-Command',`if(Test-Path -LiteralPath ${quote(newKey)}){Get-ItemProperty -LiteralPath ${quote(newKey)}|Select-Object DisplayName,DisplayVersion,InstallLocation,MainBinaryName|ConvertTo-Json -Compress}else{'null'}`]));
const shortcuts=()=>JSON.parse(ps(['-Command',`$shell=New-Object -ComObject WScript.Shell;$paths=@((Join-Path ([Environment]::GetFolderPath('Programs')) '南枫 AI Windows 对齐验收.lnk'),(Join-Path ([Environment]::GetFolderPath('Desktop')) '南枫 AI Windows 对齐验收.lnk'));$items=@($paths|ForEach-Object{if(Test-Path -LiteralPath $_){$s=$shell.CreateShortcut($_);@{path=$_;target=$s.TargetPath;arguments=$s.Arguments;icon=$s.IconLocation}}});ConvertTo-Json -InputObject $items -Compress`]) || '[]');
const oldSnapshot=()=>JSON.parse(ps(['-Command',String.raw`$keys=@('HKCU:\Software\Microsoft\Windows\CurrentVersion\Uninstall\南枫 AI','HKCU:\Software\Microsoft\Windows\CurrentVersion\Uninstall\南枫 AI Windows 独立验收','HKCU:\Software\Classes\nanfeng-ai-windows-acceptance-reminder\shell\open\command','HKCU:\Software\Classes\nanfeng-ai-reminder\shell\open\command','HKCU:\Software\Classes\AppUserModelId\com.nanzhufeng.ai.desktop.windowsacceptance');$r=@{};foreach($key in $keys){if(Test-Path -LiteralPath $key){$item=Get-Item -LiteralPath $key;$values=@{};foreach($n in $item.GetValueNames()){$values[$n]=$item.GetValue($n)};$r[$key]=$values}else{$r[$key]=$null}};$old='D:\CodexTemp\nfai-installed-acceptance.7d2cb30e-9606-4d88-bd91-bbc0fec53c92\nanfeng-ai-desktop-spike.exe';$r.oldExecutable=if(Test-Path -LiteralPath $old){(Get-FileHash -LiteralPath $old).Hash}else{$null};$r|ConvertTo-Json -Depth 8 -Compress`]));

const result={...previousEvidence,pass:false,resumedAfterControllerCrash:true,recoveryNodeVersion:process.version};let port;
const firstReceipts=JSON.parse(await readFile(resolve(desktopRoot,'../docs/review/20261017-windows-pptx-package/first-launch-identities.json'),'utf8'));
for(const old of firstReceipts){assert.equal(resolve(old.root),root);assert.equal(old.sha256,receipt.sha256);assert.ok(!pids.includes(old.pid));pids.push(old.pid);}
result.forcedStoppedBeforeRecovery=firstReceipts.filter(r=>!previousEvidence.pids.includes(r.pid)).map(r=>r.pid);
const checkpoint=async phase=>{result.phase=phase;result.root=root;result.pids=pids;await writeFile(join(rootParent,'native-delivery-recovery-evidence.json'),JSON.stringify(result,null,2)+'\n');};
const silent=(path,occupied=false)=>Number(ps(['-Command',`$p=Start-Process -FilePath ${quote(path)} -ArgumentList ${quote(occupied?'/S _?='+installDir:'/S')} -WindowStyle Hidden -Wait -PassThru;$p.ExitCode`]));
const pathProcesses=()=>JSON.parse(ps(['-Command',`$items=@(Get-CimInstance Win32_Process -Filter "Name='nanfeng-ai-desktop-spike.exe'"|Where-Object {$_.ExecutablePath -eq ${quote(installedExecutable)}}|Select-Object ProcessId,ExecutablePath);ConvertTo-Json -InputObject $items -Compress`]));
const python=String.raw`C:\Users\Administrator\.cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe`;
async function compareTree(relative){let count=0;async function walk(part){for(const item of await readdir(join(packageDir,part),{withFileTypes:true})){assert.ok(!item.isSymbolicLink());const child=join(part,item.name);if(item.isDirectory())await walk(child);else{assert.equal(await hash(join(installDir,child)),await hash(join(packageDir,child)),child);count++;}}}await walk(relative);return count;}
async function coldDraft(){await ready(port);await until(()=>evaluate(port,'__nanfengDesktopWorkState?.entryResolved===true'),'cold entry');await until(()=>evaluate(port,`document.querySelector('#chat-composer')?.value==='A9_CURRENT_DELIVERY_SYNTHETIC_DRAFT'`),'synthetic draft restored');return await invoke(port,'CHAT','read_desktop_runtime_info');}
async function batLaunch(path,expected,releaseRepo=false){const next=await reservePort();runExecutable=expected;launch=await launchPowerShell(['/d','/s','/c',`""${path}" -Hidden -Root "${root}" -DebugPort ${next} -PassThru${releaseRepo?" -PackageProfile release":""}"`],'cmd.exe');assert.equal(resolve(launch.executable),runExecutable);assert.equal(launch.sha256,receipt.sha256);assert.equal(launch.visible,false);return next;}
const business=()=>JSON.parse(execFileSync(python,['-X','utf8','-c',String.raw`import hashlib,json,sqlite3,sys;from pathlib import Path;p=Path(sys.argv[1]).resolve();assert p.parent==Path(sys.argv[2]).resolve();r={}
for n,v in [('workspace.sqlite3',58),('work-area/workspace.sqlite3',58),('usage-ledger/usage-ledger-v1.sqlite3',3)]:
 f=p/n;assert f.is_file();c=sqlite3.connect(f.as_uri()+'?mode=ro',uri=True);version=c.execute('pragma user_version').fetchone()[0];quick=c.execute('pragma quick_check').fetchall();fk=c.execute('pragma foreign_key_check').fetchall();c.close();assert version==v and quick==[('ok',)] and not fk;r[n]={'sha256':hashlib.sha256(f.read_bytes()).hexdigest(),'schema':version,'quick':'ok','foreignKeyErrors':len(fk)}
print(json.dumps(r))`,root,rootParent],{encoding:'utf8',windowsHide:true,timeout:15000}));
try{
 result.businessAfterControllerCrash=business();
 port=await batLaunch(join(installDir,'alignment-launcher/Start-NanfengAI-Acceptance.bat'),installedExecutable);await checkpoint('installed-bat-started');result.installedBatRuntime=await coldDraft();result.installedBatClose=await normalClose();result.checks.push('installed BAT hidden cold draft');
 const manifestPath=join(installDir,'alignment-launcher/executable.json'),original=await readFile(manifestPath);
 try{const invalid=JSON.parse(original);invalid.sha256='0'.repeat(64);await writeFile(manifestPath,JSON.stringify(invalid));let rejected=false;try{execFileSync('powershell.exe',['-NoProfile','-NonInteractive','-ExecutionPolicy','Bypass','-File',join(installDir,'alignment-launcher/Start.ps1'),'-Hidden','-Root',root,'-PassThru'],{encoding:'utf8',windowsHide:true,timeout:10000});}catch(error){assert.notEqual(error.status,0);assert.ok(!error.killed);rejected=true;}assert.ok(rejected);assert.deepEqual(pathProcesses(),[]);result.tamperedManifestRejected=true;}finally{await writeFile(manifestPath,original);}
 port=await batLaunch(join(desktopRoot,'../启动南枫AI-当前对齐验收版.bat'),immutableExecutable,true);await checkpoint('repository-bat-started');result.repositoryBatRuntime=await coldDraft();result.repositoryBatClose=await normalClose();result.checks.push('repository BAT follows explicit Release candidate via immutable runner');
 result.businessBeforeUninstall=business();await checkpoint('pre-uninstall');result.uninstallExit=silent(join(installDir,'uninstall.exe'));assert.equal(result.uninstallExit,0);await until(()=>registration()===null,'registry removal');await until(async()=>!(await stat(installedExecutable).catch(()=>null)),'executable removal');await until(()=>shortcuts().length===0,'shortcut removal');
 result.businessAfterUninstall=business();assert.deepEqual(result.businessAfterUninstall,result.businessBeforeUninstall);result.previousAfter=oldSnapshot();assert.deepEqual(result.previousAfter,result.previousInstallation);result.pass=true;await checkpoint('complete');
}catch(error){result.pass=false;result.error=error.stack||String(error);process.exitCode=1;}
finally{if(launch)try{result.failureClose=await normalClose();}catch(error){result.closeError=String(error);stop();}closeCdpSessions();result.root=root;result.pids=pids;await checkpoint(result.pass?'complete':'failed-preserved');}
console.log(JSON.stringify({pass:result.pass,error:result.error,installDir,root,pids,evidence:join(rootParent,'native-delivery-recovery-evidence.json')},null,2));
