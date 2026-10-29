import assert from 'node:assert/strict';
import { createHash } from 'node:crypto';
import { spawn, execFileSync } from 'node:child_process';
import { readFile, writeFile, stat } from 'node:fs/promises';
import { dirname, resolve, join, sep } from 'node:path';
import { fileURLToPath } from 'node:url';
import http from 'node:http';
import net from 'node:net';

assert.equal(process.platform, 'win32');
const desktop = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const parent = resolve(process.argv[2] || 'D:/CodexTemp');
const receipt = JSON.parse(await readFile(join(desktop, 'output/windows-acceptance/current.json'), 'utf8'));
assert.equal(receipt.identifier, 'com.nanzhufeng.ai.desktop.windowsacceptance');
assert.equal(createHash('sha256').update(await readFile(receipt.executable)).digest('hex').toUpperCase(), receipt.sha256);
const executable = resolve(desktop, 'output/windows-acceptance-binaries', receipt.sha256, 'nanfeng-ai-desktop-spike.exe');
const helper = join(desktop, 'scripts/windows-acceptance-cdp-eval.mjs');
let requests = 0;
const provider = http.createServer(async (request, response) => {
  for await (const _ of request) { /* Never log body. */ }
  requests++;
  response.writeHead(200, { 'Content-Type': 'text/event-stream' });
  response.end('data: {"choices":[{"delta":{"content":"synthetic"},"finish_reason":"stop"}]}\n\ndata: [DONE]\n\n');
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
  const deadline = Date.now() + 90000;
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
async function start(reopen = false) {
  port = await freePort();
  const code = `[Console]::OutputEncoding=[Text.UTF8Encoding]::new($false); & ${quote(join(desktop, 'scripts/start-windows-acceptance.ps1'))} -RootParent ${quote(parent)} -StartupMode normal -Visible -DebugPort ${port} -OrdinaryChatTestEndpoint ${quote(endpoint)}${reopen ? ` -Root ${quote(root)}` : ''}`;
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
try {
  await start();
  const workspace = await invoke('ensure_desktop_local_workspace');
  const drafts = await invoke('create_desktop_manual_reminder_draft', { args: { workspaceId: workspace.summary.id, conversationId: null, timezoneId: 'UTC' } });
  const draft = drafts.drafts.find(item => item.status === 'PENDING_REVIEW');
  assert.ok(draft);
  await writeFile(join(root, '.reminder-registration-arm'), '', { flag: 'wx' });
  const nextMinute = Math.ceil((Date.now() + 15000) / 60000) * 60000;
  const confirmed = await invoke('confirm_desktop_reminder_draft', { args: { draftId: draft.draftId, title: '合成准备暂停竞态', instruction: '只核对合成公开材料', scheduleKind: 'ONCE', anchorLocal: new Date(nextMinute).toISOString().slice(0, 16), timezoneId: 'UTC', missedPolicy: 'RUN_ONCE' } });
  const plan = confirmed.plans.find(item => item.sourceDraftId === draft.draftId);
  assert.ok(plan);
  console.log(JSON.stringify({ stage: 'future plan confirmed', root, candidateSha256: receipt.sha256, planId: plan.planId, waitMilliseconds: nextMinute - Date.now() }));
  await until(() => Date.now() >= nextMinute, 'future plan not due');
  await evaluate(`globalThis.__reminderGapResult=null;window.__TAURI__.core.invoke('run_desktop_reminders_due',{}, {headers:{'x-nanfeng-data-area':'CHAT'}}).then(value=>globalThis.__reminderGapResult={ok:true,value}).catch(error=>globalThis.__reminderGapResult={ok:false,error:String(error)});true`);
  await until(() => stat(join(root, '.reminder-registration-reached')), 'prepared registration checkpoint missing');
  assert.equal((await invoke('read_desktop_reminders')).plans.find(item => item.planId === plan.planId).status, 'RUNNING');
  assert.equal(requests, 0);
  const paused = await invoke('set_desktop_reminder_plan_paused', { planId: plan.planId, paused: true });
  assert.equal(paused.plans.find(item => item.planId === plan.planId).status, 'PAUSED');
  await writeFile(join(root, '.reminder-registration-continue'), '', { flag: 'wx' });
  await until(async () => (await invoke('read_desktop_reminders')).plans.find(item => item.planId === plan.planId)?.lastSafeErrorCode === 'CANCELLED', 'cancelled terminal missing');
  assert.equal(requests, 0);
  const audit = JSON.parse(execFileSync('python', [join(desktop, 'scripts/read-windows-acceptance-reminder-run.py'), root, 'CHAT'], { encoding: 'utf8' }));
  assert.equal(audit.runs.length, 1); assert.equal(audit.runs[0].runState, 'FAILED'); assert.equal(audit.runs[0].safeCode, 'FAILED');
  stop(); await start(true);
  const cold = (await invoke('read_desktop_reminders')).plans.find(item => item.planId === plan.planId);
  assert.equal(cold.status, 'PAUSED'); assert.equal(cold.lastSafeErrorCode, 'CANCELLED');
  await invoke('run_desktop_reminders_due'); assert.equal(requests, 0);
  stop();
  console.log(JSON.stringify({ candidateSha256: receipt.sha256, root, planId: plan.planId, pausedBeforeRegistration: true, providerRequests: requests, runStatus: 'FAILED', safeErrorCode: 'CANCELLED', coldStatus: cold.status, pids, windows }));
} finally { stop(); await new Promise(done => provider.close(done)); }
