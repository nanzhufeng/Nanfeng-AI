import assert from 'node:assert/strict';
import { createHash } from 'node:crypto';
import { spawn, execFileSync } from 'node:child_process';
import { readFile, writeFile, stat, mkdir, copyFile } from 'node:fs/promises';
import { dirname, resolve, join, sep } from 'node:path';
import { fileURLToPath } from 'node:url';
import http from 'node:http';
import net from 'node:net';

assert.equal(process.platform, 'win32');
const desktop = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const sourceEvidence=JSON.parse(await readFile(resolve(desktop,'../docs/review/20261005-chat-submission-claim/installed-reminder-notification-receipt.json'),'utf8'));
const parent=dirname(resolve(sourceEvidence.root));
const compare = false;assert.ok(process.execArgv.includes('--max-old-space-size=2048'));
const visible = true;
assert.ok(!(visible && compare));
const receipt = JSON.parse(await readFile(join(desktop, 'output/windows-acceptance/current.json'), 'utf8'));
assert.equal(receipt.identifier, 'com.nanzhufeng.ai.desktop.windowsacceptance');
assert.equal(createHash('sha256').update(await readFile(receipt.executable)).digest('hex').toUpperCase(), receipt.sha256);
const compiledGuiSha256=receipt.sha256;const executable=resolve('D:/CodexTemp/nfai-installed-acceptance.7d2cb30e-9606-4d88-bd91-bbc0fec53c92/nanfeng-ai-desktop-spike.exe');receipt.executable=executable;receipt.sha256=sourceEvidence.installedSha256;assert.equal(createHash('sha256').update(await readFile(executable)).digest('hex').toUpperCase(),receipt.sha256);assert.equal(receipt.sha256,'48D62E9925FAA10B4E3756FB28110785007B12F142F9837AE0D355C63A47154D');
const helper = join(desktop, 'scripts/windows-acceptance-cdp-eval.mjs');
let requests = 0;const responses=[];const closes=[];const partialText='SYNTHETIC_STREAM_STOP_PARTIAL 完整保留片段';
const provider = http.createServer(async (request, response) => {
  for await (const _ of request) { /* Never log body. */ }
  requests++;
  response.writeHead(200, { 'Content-Type': 'text/event-stream' });
  response.write(`data: ${JSON.stringify({choices:[{delta:{content:partialText},finish_reason:null}]})}\n\n`);responses.push(response);
});
await new Promise(done => provider.listen(0, '127.0.0.1', done));
const endpoint = `http://127.0.0.1:${provider.address().port}`;
let launch, root, port;
const pids = [];
const windows = [];
const quote = value => `'${String(value).replaceAll("'", "''")}'`;
const ps = code => execFileSync('pwsh', ['-NoProfile', '-Command', `[Console]::OutputEncoding=[Text.UTF8Encoding]::new($false);${code}`], { encoding: 'utf8' }).trim();
async function freePort() {
  const server = net.createServer();
  await new Promise(done => server.listen(0, '127.0.0.1', done));
  const value = server.address().port;
  await new Promise(done => server.close(done));
  return value;
}
async function until(read, label) {
  const deadline = Date.now() + 30000;
  while (Date.now() < deadline) {
    try { const value = await read(); if (value) return value; } catch { /* Startup only. */ }
    await new Promise(done => setTimeout(done, 100));
  }
  throw new Error(label);
}
async function evaluate(expression) {
  return await new Promise((done, fail) => {
    const child = spawn(process.execPath, ['--max-old-space-size=2048', helper, String(port)], { windowsHide: true });
    let output = '', errors = '';
    child.stdout.on('data', chunk => output += chunk);
    child.stderr.on('data', chunk => errors += chunk);
    child.on('error', fail);
    child.on('close', code => { try { code === 0 ? done(JSON.parse(output)) : fail(new Error(errors)); } catch (error) { fail(error); } });
    child.stdin.end(expression);
  });
}
async function invoke(command, args = {}) {
  const value = await evaluate(`window.__TAURI__.core.invoke(${JSON.stringify(command)},${JSON.stringify(args)},{headers:{'x-nanfeng-data-area':'CHAT'}}).catch(error=>({__error:String(error)}))`);
  assert.ok(!value?.__error, `${command}: ${value?.__error}`);
  return value;
}
async function start(reopen = false, activationUrl = '') {
  port = await freePort();
  const code = `[Console]::OutputEncoding=[Text.UTF8Encoding]::new($false); & ${quote(join(desktop, 'scripts/start-windows-acceptance.ps1'))} -RootParent ${quote(parent)} -ExecutablePath ${quote(receipt.executable)} -RunInPlace -StartupMode normal -Visible -DebugPort ${port}${reopen ? ` -Root ${quote(root)}` : ''}${activationUrl ? ` -ReminderActivationUrl ${quote(activationUrl)}` : ''}`;
  const args = ['-NoProfile', '-Command', code];
  launch = await new Promise((done, fail) => {
    const child = spawn('pwsh', args, { windowsHide: true });
    let output = '', errors = '';
    child.stdout.setEncoding('utf8'); child.stderr.setEncoding('utf8');
    const timer = setTimeout(() => { child.kill(); fail(new Error(`launcher timeout: ${errors}`)); }, 30000);
    child.stdout.on('data', chunk => {
      output += chunk;
      const line = output.split(/\r?\n/).find(item => item.trim().startsWith('{') && item.includes('"pid"'));
      if (!line) return;
      try { const value = JSON.parse(line); clearTimeout(timer); child.stdout.destroy(); child.stderr.destroy(); child.unref(); done(value); } catch {}
    });
    child.stderr.on('data', chunk => errors += chunk);
    child.on('error', error => { clearTimeout(timer); fail(error); });
    child.on('close', code => { if (!output.includes('"pid"')) { clearTimeout(timer); fail(new Error(`launcher ${code}: ${errors}`)); } });
  });
  assert.equal(launch.sha256, receipt.sha256);
  assert.equal(resolve(launch.executable), executable);
  root = resolve(launch.root);
  assert.ok(root.startsWith(parent + sep));
  assert.equal(JSON.parse(await readFile(join(root, '.nanfeng-windows-acceptance.json'), 'utf8')).identifier, receipt.identifier);
  await until(() => evaluate('Boolean(window.__TAURI__?.core?.invoke)'), 'native IPC unavailable');
  await until(() => evaluate('globalThis.__nanfengDesktopWorkState?.runtimeInfo?.dataAreasReady===true'), 'native data areas unavailable');
  const window = JSON.parse(ps(`$p=Get-Process -Id ${Number(launch.pid)} -ErrorAction Stop;@{title=$p.MainWindowTitle;responding=$p.Responding}|ConvertTo-Json -Compress`));
  assert.ok(window.title.includes('南枫 AI'), 'native window title mismatch');
  assert.equal(window.responding, true); windows.push(window);
}
function stop() {
  if (!launch) return;
  const pid = Number(launch.pid);
  ps(`$p=Get-CimInstance Win32_Process -Filter 'ProcessId=${pid}';if($p){if([IO.Path]::GetFullPath([string]$p.ExecutablePath) -ne ${quote(executable)} -or (Get-FileHash -LiteralPath ${quote(executable)} -Algorithm SHA256).Hash -ne ${quote(receipt.sha256)}){throw 'PID mismatch'};Stop-Process -Id ${pid} -Force}`);
  pids.push(pid); launch = null;
}
async function normalClose(){
 const pid=Number(launch.pid);const value=JSON.parse(ps(`$p=Get-CimInstance Win32_Process -Filter 'ProcessId=${pid}';if(!$p -or [IO.Path]::GetFullPath($p.ExecutablePath) -ne ${quote(executable)} -or (Get-FileHash -LiteralPath ${quote(executable)} -Algorithm SHA256).Hash -ne ${quote(receipt.sha256)}){throw 'identity'};$v=Get-Process -Id ${pid};$handle=$v.Handle;$title=$v.MainWindowTitle;$responding=$v.Responding;if(!$title -or !$responding -or !$v.CloseMainWindow()){throw 'window close'};if(!$v.WaitForExit(10000) -or $v.ExitCode -ne 0){throw 'normal exit'};@{title=$title;responding=$responding;pid=${pid};exitCode=$v.ExitCode;accepted=$true}|ConvertTo-Json -Compress`));
 await until(()=>ps(`@(Get-CimInstance Win32_Process -Filter "Name='msedgewebview2.exe'" | Where-Object {$_.CommandLine -and $_.CommandLine.Contains(${quote(launch.webviewProfile)})}).Count`)==='0','owned profile exit');pids.push(pid);launch=null;closes.push(value);
}
const ui=async selector=>{await until(()=>evaluate(`Boolean(document.querySelector(${JSON.stringify(selector)}))`),'UI '+selector);assert.equal(await evaluate(`document.querySelector(${JSON.stringify(selector)}).click();true`),true);};

root=resolve(sourceEvidence.root);
const previous=JSON.parse(await readFile(join(root,'reminder-normal-success.json'),'utf8'));
assert.equal(previous.notificationAudit.notificationState,'SENT');
const runId=previous.notificationAudit.runId,planId=previous.planId;
const url=`nanfeng-ai-windows-acceptance-reminder://open/CHAT/${runId}`;
const readAudit=()=>JSON.parse(execFileSync('python',[join(desktop,'scripts/read-windows-acceptance-reminder-run.py'),root,'CHAT'],{encoding:'utf8'}));
const before=readAudit();
try {
 await start(true);
 const registeredCommand=JSON.parse(ps("(Get-Item -LiteralPath 'HKCU:\\Software\\Classes\\nanfeng-ai-windows-acceptance-reminder\\shell\\open\\command').GetValue('')|ConvertTo-Json -Compress"));
 assert.equal(registeredCommand,`"${executable}" "%1"`);
 await evaluate('document.querySelector("[data-action=show-chat]")?.click();true');
 await until(()=>evaluate("globalThis.__nanfengDesktopWorkState.pane!=='reminders'"),'leave prior reminder selection before Shell call');
 const originalPid=Number(launch.pid);
 ps(`$env:NANFENG_AI_WINDOWS_ACCEPTANCE_ROOT=${quote(root)};Start-Process -FilePath ${quote(url)} -WindowStyle Hidden`);

 await until(()=>evaluate(`globalThis.__nanfengDesktopWorkState.pane==='reminders'&&globalThis.__nanfengDesktopWorkState.settingsSection==='reminders'&&globalThis.__nanfengDesktopWorkState.selectedReminderPlanId===${JSON.stringify(planId)}&&Boolean(document.querySelector(${JSON.stringify(`[data-reminder-plan-id="${planId}"]`)}))`),'real completed run cold activation targets the correct plan');
 const route=await evaluate('(()=>{const s=globalThis.__nanfengDesktopWorkState;return {pane:s.pane,page:s.settingsSection,planId:s.selectedReminderPlanId,workspaceId:s.current?.summary?.id,dataArea:s.dataArea}})()');
 assert.equal(route.dataArea,'CHAT');assert.equal(route.planId,planId);
 assert.equal((await invoke('read_desktop_reminders')).plans.find(x=>x.planId===planId).status,'COMPLETED');
 assert.deepEqual(await invoke('drain_desktop_reminder_notification_actions'),[]);
 await normalClose();assert.deepEqual(readAudit(),before);assert.equal(requests,0);
 const result={root,installedSha256:receipt.sha256,compiledGuiSha256,runId,planId,url,route,closes,windows,providerRequests:requests,realCompletedRun:true,notificationState:'SENT',databaseAndUsageExact:true,layer:'WINDOWS_SHELL_PROTOCOL_HOT_ACTIVATION_CURRENT_INSTALLED_INSTANCE_NOT_PHYSICAL_CLICK',shellProtocolInvoked:true,registeredCommand,originalPid,physicalNotificationClickVerified:false,pass:true};
 await writeFile(join(root,'installed-shell-notification-activation.json'),JSON.stringify(result,null,2));console.log(JSON.stringify(result));
} catch(error) {await writeFile(join(root,'installed-shell-notification-activation-failure.json'),JSON.stringify({root,error:String(error),pids,closes},null,2));throw error;} finally {stop();provider.closeAllConnections();await new Promise(done=>provider.close(done));}
