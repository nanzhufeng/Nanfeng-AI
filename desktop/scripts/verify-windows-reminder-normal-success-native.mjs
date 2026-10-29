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
const area = process.argv[3] || 'WORK';
assert.ok(['CHAT','WORK'].includes(area));
const missingSources = process.argv.includes('--missing-sources');
const denyUsage = process.argv.includes('--deny-usage');
const receipt = JSON.parse(await readFile(join(desktop, 'output/windows-acceptance/current.json'), 'utf8'));
assert.equal(receipt.identifier, 'com.nanzhufeng.ai.desktop.windowsacceptance');
assert.equal(createHash('sha256').update(await readFile(receipt.executable)).digest('hex').toUpperCase(), receipt.sha256);
const executable = resolve(desktop, 'output/windows-acceptance-binaries', receipt.sha256, 'nanfeng-ai-desktop-spike.exe');
const helper = join(desktop, 'scripts/windows-acceptance-cdp-eval.mjs');
let requests = 0;
const provider = http.createServer(async (request, response) => {
  const chunks=[];
  for await (const chunk of request) { chunks.push(chunk); /* Never log body. */ }
  const body=JSON.parse(Buffer.concat(chunks).toString('utf8'));
  requests++;
  response.writeHead(200, { 'Content-Type': 'text/event-stream' });
  const annotations=missingSources ? [] : [{url_citation:{title:'合成公开来源（未抓取）',url:'https://example.com/synthetic-monitor'}}];
  response.write(`data: ${JSON.stringify({model:body.model,choices:[{delta:{content:'synthetic',annotations}}]})}\n\n`);
  response.end(`data: ${JSON.stringify({choices:[{delta:{},finish_reason:'stop'}],usage:{prompt_tokens:20,completion_tokens:8,cost:0.000007}})}\n\ndata: [DONE]\n\n`);
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
async function until(read, label, retryErrors = true) {
  const deadline = Date.now() + 90000;
  while (Date.now() < deadline) {
    try { const value = await read(); if (value) return value; } catch (error) { if (!retryErrors) throw error; }
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
  const value = await evaluate(`window.__TAURI__.core.invoke(${JSON.stringify(command)},${JSON.stringify(args)},{headers:{'x-nanfeng-data-area':'${area}'}}).catch(error=>({__error:String(error)}))`);
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
async function stop() {
  if (!launch) return;
  const pid = Number(launch.pid);
  const closed = JSON.parse(ps(`$p=Get-Process -Id ${pid} -ErrorAction Stop;if([IO.Path]::GetFullPath($p.Path) -ne ${quote(executable)} -or (Get-FileHash $p.Path).Hash -ne ${quote(receipt.sha256)}){throw 'PID identity mismatch'};$p.CloseMainWindow()|ConvertTo-Json`));
  assert.equal(closed, true);
  await until(() => ps(`($null -eq (Get-Process -Id ${pid} -ErrorAction SilentlyContinue))|ConvertTo-Json`) === 'true', 'normal Close did not exit');
  pids.push(pid); launch = null;
}
try {
  await start();
  const workspace = await invoke('ensure_desktop_local_workspace');
  const drafts = await invoke('create_desktop_manual_reminder_draft', { args: { workspaceId: workspace.summary.id, conversationId: null, timezoneId: 'UTC' } });
  const draft = drafts.drafts.find(item => item.status === 'PENDING_REVIEW');
  assert.ok(draft);
  const nextMinute = Math.ceil((Date.now() + 15000) / 60000) * 60000;
  const confirmed = await invoke('confirm_desktop_reminder_draft', { args: { draftId: draft.draftId, title: '合成正常定时成功', instruction: '只核对合成公开材料', scheduleKind: 'ONCE', anchorLocal: new Date(nextMinute).toISOString().slice(0, 16), timezoneId: 'UTC', missedPolicy: 'RUN_ONCE' } });
  const plan = confirmed.plans.find(item => item.sourceDraftId === draft.draftId);
  assert.ok(plan);
  assert.equal(plan.status, 'ACTIVE'); assert.equal(requests, 0);
  if(denyUsage) {
    await invoke('read_desktop_usage_ledger');
    execFileSync('python',[join(desktop,'scripts/set-windows-reminder-usage-fault.py'),root,area,'deny']);
  }
  console.log(JSON.stringify({ stage: 'NORMAL production timer armed by confirmed plan', root, area, candidate: receipt.sha256, planId: plan.planId, waitMilliseconds: nextMinute-Date.now() }));
  // No run_due IPC and no SQL clock injection: production timer must execute.
  await until(async () => {
    const currentPlan=(await invoke('read_desktop_reminders')).plans.find(item => item.planId === plan.planId);
    if(missingSources && currentPlan?.lastSafeErrorCode === 'WEB_SEARCH_NO_SOURCES') return true;
    if(['FAILED','UNKNOWN'].includes(currentPlan?.status)) throw new Error(`terminal ${currentPlan.status}: ${currentPlan.lastSafeErrorCode}`);
    return currentPlan?.status === 'COMPLETED';
  }, 'production timer success missing', false);
  const saved = (await invoke('read_desktop_reminders')).plans.find(item => item.planId === plan.planId);
  if(missingSources) {
    assert.equal(saved.latestResult, null);
    assert.equal(saved.lastSafeErrorCode, 'WEB_SEARCH_NO_SOURCES');
  } else {
    assert.ok(saved.latestResult.startsWith('synthetic\n\n来源：\n'));
    assert.ok(saved.latestResult.includes('https://example.com/synthetic-monitor'));
    assert.equal(saved.lastSafeErrorCode, null);
  }
  assert.equal(saved.lastInputTokens,20); assert.equal(saved.lastOutputTokens,8); assert.equal(saved.lastChargeMicros,7);
  let usage=(await invoke('read_desktop_usage_ledger')).records;
  if(denyUsage) {
    assert.equal(usage.length,0);
    await stop();
    execFileSync('python',[join(desktop,'scripts/set-windows-reminder-usage-fault.py'),root,area,'allow']);
    await start(true);
    const recovered=(await invoke('read_desktop_reminders')).plans.find(item=>item.planId===plan.planId);
    assert.equal(recovered.status,saved.status); assert.equal(recovered.latestResult,saved.latestResult); assert.equal(requests,1);
    usage=(await invoke('read_desktop_usage_ledger')).records;
  }
  assert.equal(usage.length,1); assert.equal(usage[0].inputTokens,20); assert.equal(usage[0].outputTokens,8); assert.equal(usage[0].chargeMicros,7);
  assert.equal(usage[0].modelId,'fixture-reminder-gpt_5_6_terra');
  assert.equal(requests, 1);
  const audit = JSON.parse(execFileSync('python', [join(desktop, 'scripts/read-windows-acceptance-reminder-run.py'), root, area], { encoding: 'utf8' }));
  const peer = JSON.parse(execFileSync('python', [join(desktop, 'scripts/read-windows-acceptance-reminder-run.py'), root, area === 'WORK' ? 'CHAT' : 'WORK'], { encoding: 'utf8' }));
  assert.equal(audit.runs.length, 1); assert.equal(audit.runs[0].runState, missingSources ? 'FAILED' : 'SUCCEEDED'); assert.equal(peer.runs.length, 0);
  assert.equal(peer.usage.length,0); assert.equal(audit.usage.length,1);
  const fact=audit.usage[0], run=audit.runs[0];
  assert.equal(fact.executionId,plan.planId); assert.equal(fact.attemptId,run.attemptId);
  for(const key of ['requestedModelId','actualModelId','inputTokens','outputTokens','chargeMicros']) assert.equal(fact[key],run[key]);
  assert.equal(fact.kind,'FINAL_MEASURED'); assert.equal(fact.factGrade,'PROVIDER_REPORTED'); assert.equal(fact.costSource,'PROVIDER_RESPONSE');
  await stop(); await start(true);
  const cold = (await invoke('read_desktop_reminders')).plans.find(item => item.planId === plan.planId);
  assert.equal(cold.status, saved.status); assert.equal(cold.latestResult, saved.latestResult); assert.equal(cold.lastSafeErrorCode,saved.lastSafeErrorCode); assert.equal(requests, 1);
  assert.deepEqual((await invoke('read_desktop_usage_ledger')).records,usage);
  await stop();
  const result={ layer:'NORMAL_PRODUCTION_TIMER_SYNTHETIC_LOOPBACK_PROVIDER', candidate:receipt.sha256, root, area, missingSources, denyUsage, usageColdReadback:true, planId:plan.planId, providerRequests:requests, runStatus:audit.runs[0].runState, coldStatus:cold.status, peerRunCount:peer.runs.length, pids, windows, realProviderConfirmed:false, headlessSchedulerConfirmed:false, full13Complete:false };
  await writeFile(join(root, 'reminder-normal-success.json'), JSON.stringify(result,null,2), {flag:'wx'});
  console.log(JSON.stringify(result));
} finally { await stop(); await new Promise(done => provider.close(done)); }
