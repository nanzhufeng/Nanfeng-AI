import { verifyVisibleUsageOutcomes } from './verify-visible-usage-outcomes.mjs';
import assert from 'node:assert/strict';
import { createHash } from 'node:crypto';
import { execFileSync, spawn } from 'node:child_process';
import { mkdir, readFile, writeFile } from 'node:fs/promises';
import { dirname, join, resolve, sep } from 'node:path';
import { fileURLToPath } from 'node:url';
import http from 'node:http';

if (process.platform !== 'win32') throw new Error('仅在 Windows 独立候选上验收。');
const desktopRoot = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const rootParent = resolve(process.argv[2] || 'D:\\CodexTemp');
assert.ok(process.execArgv.includes('--max-old-space-size=2048'));
assert.ok(process.argv.includes('--kill-pending'));
assert.ok(process.argv.slice(3).every(arg => ['--kill-pending', '--work-area'].includes(arg)));
const taskArea = process.argv.includes('--work-area') ? 'WORK' : 'CHAT';
const peerArea = taskArea === 'WORK' ? 'CHAT' : 'WORK';
const failureUsageMode = process.argv.includes('--http-failure-usage');
const failureMode = process.argv.includes('--http-failure') || failureUsageMode;
const retryEmptyMode = process.argv.includes('--retry-after-empty');
const retryMode = process.argv.includes('--retry-after-429') || retryEmptyMode;
const oversizedResponseMode = process.argv.includes('--oversized-response');
const emptyMarkdownUsageMode = process.argv.includes('--empty-markdown-usage');
const cancelMode = process.argv.includes('--cancel-pending');
const killPendingMode = process.argv.includes('--kill-pending');
const activeBackupMode = process.argv.includes('--backup-active');
const activePrivacyMode = process.argv.includes('--privacy-active');
const cancelledPrivacyMode = process.argv.includes('--privacy-cancelled');
const closePrivacyMode = process.argv.includes('--privacy-close');
const backupMode = process.argv.includes('--backup-roundtrip');
const deleteMode = process.argv.includes('--delete-completed') || backupMode;
const crossAreaMode = process.argv.includes('--cross-area');
const sendChatMode = process.argv.includes('--send-chat');
const usageUiMode = process.argv.includes('--usage-ui');
const continueChatMode = process.argv.includes('--continue-chat') || sendChatMode;
const pdfMode = process.argv.includes('--pdf-picker');
const pickerMode = process.argv.includes('--system-picker') || pdfMode;
const receipt = JSON.parse(await readFile(join(desktopRoot, 'output/windows-acceptance/current.json'), 'utf8'));
assert.equal(receipt.identifier, 'com.nanzhufeng.ai.desktop.windowsacceptance');
const source = resolve(desktopRoot, 'output/windows-acceptance', receipt.sha256, 'NanfengAI-Windows-Acceptance.exe');
assert.equal(resolve(receipt.executable), source);
assert.equal(createHash('sha256').update(await readFile(source)).digest('hex').toUpperCase(), receipt.sha256);
const runningExecutable = resolve(desktopRoot, 'output/windows-acceptance-binaries', receipt.sha256, 'nanfeng-ai-desktop-spike.exe');
const markdown = '# 合成 OCR 正文\n\n第二行仅用于隔离验收。';
const png = Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+/6XcAAAAASUVORK5CYII=', 'base64');
function twoPagePdf() {
  const streams = ['BT /F1 16 Tf 72 720 Td (OCR synthetic page one) Tj ET', 'BT /F1 16 Tf 72 720 Td (OCR synthetic page two) Tj ET'];
  const objects = [
    '<< /Type /Catalog /Pages 2 0 R >>',
    '<< /Type /Pages /Kids [3 0 R 4 0 R] /Count 2 >>',
    '<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] /Resources << /Font << /F1 7 0 R >> >> /Contents 5 0 R >>',
    '<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] /Resources << /Font << /F1 7 0 R >> >> /Contents 6 0 R >>',
    ...streams.map(stream => `<< /Length ${Buffer.byteLength(stream)} >>\nstream\n${stream}\nendstream`),
    '<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>',
  ];
  let body = '%PDF-1.4\n';
  const offsets = [];
  for (const [index, object] of objects.entries()) {
    offsets.push(Buffer.byteLength(body));
    body += `${index + 1} 0 obj\n${object}\nendobj\n`;
  }
  const xref = Buffer.byteLength(body);
  body += `xref\n0 ${objects.length + 1}\n0000000000 65535 f \n`;
  for (const offset of offsets) body += `${String(offset).padStart(10, '0')} 00000 n \n`;
  body += `trailer\n<< /Size ${objects.length + 1} /Root 1 0 R >>\nstartxref\n${xref}\n%%EOF\n`;
  return Buffer.from(body);
}
const materialBytes = pdfMode ? twoPagePdf() : png;
const requests = [];
const chatRequests = [];
let releaseResponse;
const responseGate = new Promise(resolve => { releaseResponse = resolve; });
const provider = http.createServer(async (request, response) => {
  const chunks = [];
  for await (const chunk of request) chunks.push(chunk);
  const body = Buffer.concat(chunks).toString('utf8');
  if (request.url !== '/api/paas/v4/layout_parsing') {
    const payload = JSON.parse(body);
    const userText = (payload.messages || []).filter(message => message.role === 'user')
      .flatMap(message => Array.isArray(message.content) ? message.content.map(part => part.text || '') : [message.content || ''])
      .join('\n');
    chatRequests.push({ path: request.url, model: payload.model,
      markdownPresent: userText.includes(markdown),
      resultNamePresent: userText.includes('synthetic-OCR.md'),
      rawSourcePresent: userText.includes(materialBytes.toString('base64')),
      idempotencyKey: request.headers['idempotency-key'] || '' });
    response.writeHead(200, { 'Content-Type': 'text/event-stream; charset=utf-8', 'Cache-Control': 'no-cache' });
    response.write(`data: ${JSON.stringify({ model: payload.model, choices: [{ delta: { content: '合成 OCR 续聊回答' } }] })}\n\n`);
    response.write(`data: ${JSON.stringify({ choices: [{ delta: {}, finish_reason: 'stop' }], usage: { prompt_tokens: 9, completion_tokens: 4, cost: 0.000005 } })}\n\n`);
    response.end('data: [DONE]\n\n');
    return;
  }
  requests.push({ path: request.url, method: request.method, authorization: request.headers.authorization,
    model: JSON.parse(body).model, sourcePresent: body.includes(materialBytes.toString('base64')) });
  if (requests.length === 1) await responseGate;
  if (oversizedResponseMode) {
    response.writeHead(200, { 'Content-Type': 'application/json; charset=utf-8' });
    response.end(Buffer.alloc(32 * 1024 * 1024 + 1, 65));
    return;
  }
  const rejectRequest = failureMode || (retryMode && !retryEmptyMode && requests.length === 1);
  response.writeHead(rejectRequest ? 429 : 200, { 'Content-Type': 'application/json; charset=utf-8' });
  response.end(JSON.stringify(rejectRequest ? { error: { code: 'synthetic-rate-limit' },
      ...(failureUsageMode ? { usage: { prompt_tokens: 12, completion_tokens: 8 }, request_id: 'nfai-synthetic-failed-ocr' } : {}) }
    : { md_results: (emptyMarkdownUsageMode || retryEmptyMode && requests.length === 1) ? '' : markdown, data_info: { num_pages: pdfMode ? 2 : 1 },
      usage: { prompt_tokens: 12, completion_tokens: 8 }, request_id: 'nfai-synthetic-ocr-request' }));
});
await new Promise(done => provider.listen(0, '127.0.0.1', done));
const endpoint = `http://127.0.0.1:${provider.address().port}`;
const ps = script => execFileSync('pwsh', ['-NoProfile', '-Command', script], { encoding: 'utf8', timeout: 60000 }).trim();
const quote = value => `'${String(value).replaceAll("'", "''")}'`;
async function reservePort() {
  const server = http.createServer();
  await new Promise(done => server.listen(0, '127.0.0.1', done));
  const port = server.address().port;
  await new Promise(done => server.close(done));
  return port;
}
let launch = null;
let root = '';
const pids = [];
function startProcess(args) {
  return new Promise((done, fail) => {
    const child = spawn('pwsh', ['-NoProfile', '-File', join(desktopRoot, 'scripts/start-windows-acceptance.ps1'), ...args],
      { stdio: ['ignore', 'pipe', 'pipe'] });
    let out = '';
    let err = '';
    const timer = setTimeout(() => { child.kill(); fail(new Error(`启动超时：${err}`)); }, 30000);
    child.stdout.on('data', chunk => {
      out += chunk.toString();
      const line = out.split(/\r?\n/).find(item => item.trim().startsWith('{') && item.includes('"pid"'));
      if (!line) return;
      clearTimeout(timer);
      try { done(JSON.parse(line)); } catch (error) { fail(error); }
      child.stdout.destroy(); child.stderr.destroy(); child.unref();
    });
    child.stderr.on('data', chunk => { err += chunk.toString(); });
    child.on('error', error => { clearTimeout(timer); fail(error); });
    child.on('exit', code => { if (!out.includes('"pid"')) { clearTimeout(timer); fail(new Error(`启动失败 ${code}：${err}`)); } });
  });
}
async function start(reopen = false) {
  const port = await reservePort();
  const args = ['-RootParent', rootParent, '-StartupMode', 'normal', '-DebugPort', String(port), '-OcrTestEndpoint', endpoint];
  if (sendChatMode) args.push('-OrdinaryChatTestEndpoint', endpoint);
  args.push('-Visible');
  if (reopen) args.push('-Root', root);
  launch = await startProcess(args);
  assert.equal(launch.sha256, receipt.sha256);
  assert.equal(resolve(launch.executable), runningExecutable);
  root = resolve(launch.root);
  assert.ok(root.startsWith(rootParent + sep));
  const marker = JSON.parse(await readFile(join(root, '.nanfeng-windows-acceptance.json'), 'utf8'));
  assert.equal(marker.identifier, receipt.identifier);
  await until(() => evaluate(port, 'Boolean(window.__TAURI__?.core?.invoke)'), '冷启动 WebView2 IPC');
  return port;
}
function stop() {
  if (!launch) return;
  const pid = Number(launch.pid);
  ps(`$p=Get-CimInstance Win32_Process -Filter "ProcessId=${pid}"; if ($p) { if ([IO.Path]::GetFullPath([string]$p.ExecutablePath) -ne ${quote(runningExecutable)} -or (Get-FileHash -LiteralPath ${quote(runningExecutable)} -Algorithm SHA256).Hash -ne ${quote(receipt.sha256)}) { throw 'PID identity mismatch' }; Stop-Process -Id ${pid} -Force -ErrorAction Stop }`);
  pids.push(pid);
  launch = null;
}
async function evaluate(port, expression) {
  const tabs = await fetch(`http://127.0.0.1:${port}/json/list`, { signal: AbortSignal.timeout(10000) }).then(response => response.json());
  const tab = tabs.find(item => item.type === 'page' && item.url === 'http://tauri.localhost/');
  assert.ok(tab, 'WebView2 页面未就绪');
  const socket = new WebSocket(tab.webSocketDebuggerUrl);
  try {
    await new Promise((done, fail) => {
      socket.addEventListener('open', done, { once: true });
      socket.addEventListener('error', fail, { once: true });
    });
    return await new Promise((done, fail) => {
      const timer = setTimeout(() => fail(new Error('CDP evaluation timed out')), 30000);
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
async function invoke(port, command, args = {}, area = taskArea) {
  const value = await evaluate(port, `window.__TAURI__.core.invoke(${JSON.stringify(command)},${JSON.stringify(args)},${JSON.stringify({ headers: { 'x-nanfeng-data-area': area } })}).catch(error=>({__error:String(error)}))`);
  if (value?.__error) throw new Error(`${command}: ${value.__error}`);
  return value;
}
async function until(read, label) {
  const deadline = Date.now() + 30000;
  while (Date.now() < deadline) {
    try { const value = await read(); if (value) return value; } catch { /* Startup or redraw in progress. */ }
    await new Promise(done => setTimeout(done, 200));
  }
  throw new Error(`${label} 未在原生界面出现`);
}
const click = (port, selector) => evaluate(port, `(()=>{const node=document.querySelector(${JSON.stringify(selector)});if(!node)return false;node.click();return true})()`);

async function selectArea(port) {
  if (taskArea === 'WORK') {
    await until(() => evaluate(port, 'Boolean(document.querySelector("[data-action=show-work]"))'), 'WORK入口');
    assert.equal(await click(port, '[data-action="show-work"]'), true);
  }
  await until(() => evaluate(port, `globalThis.__nanfengDesktopWorkState?.dataArea === ${JSON.stringify(taskArea)} && ((${JSON.stringify(taskArea)} === 'WORK') || Boolean(document.querySelector('[data-action="show-transcription"]')))`), '原区就绪');
}
async function normalClose() {
  const pid = Number(launch.pid);
  const closed = JSON.parse(ps(`$p=Get-CimInstance Win32_Process -Filter "ProcessId=${pid}"; if (!$p -or [IO.Path]::GetFullPath([string]$p.ExecutablePath) -ne ${quote(runningExecutable)}) {throw 'PID identity mismatch'}; $n=Get-Process -Id ${pid}; @{title=$n.MainWindowTitle;responding=$n.Responding;accepted=$n.CloseMainWindow()} | ConvertTo-Json -Compress`));
  assert.ok(closed.responding && closed.title && closed.accepted);
  await until(() => ps(`@(Get-CimInstance Win32_Process -Filter "ProcessId=${pid}").Count`) === '0', '自然窗口关闭进程退出');
  await until(() => ps(`@(Get-CimInstance Win32_Process -Filter "Name='msedgewebview2.exe'" | Where-Object { $_.CommandLine -and $_.CommandLine.Contains(${quote(launch.webviewProfile)}) }).Count`) === '0', '本根WebView退出');
  pids.push(pid); launch = null;
  return closed;
}
const result = { candidateSha256: receipt.sha256, dataArea: taskArea, startupMode: 'normal', realService: false, invocationScope: taskArea === 'CHAT' ? 'actual DOM execution/retry' : 'explicit existing IPC; no WORK transcription UI entry' };
let stage = 'start';
try {
  let port = await start(); result.root = root;
  await selectArea(port);
  const workspaceId = (await invoke(port, 'ensure_desktop_local_workspace')).summary.id;
  const fixtureDir = join(root, 'synthetic-ocr-interruption-inputs');
  await mkdir(fixtureDir, { recursive: true });
  const selectedPath = join(fixtureDir, 'synthetic.png'); await writeFile(selectedPath, png);
  const task = await invoke(port, 'import_desktop_ocr_source', { args: { workspaceId, selectedPath } });
  result.taskId = task.id; result.workspaceId = workspaceId; result.sourceAttachmentId = task.sourceAttachmentId;
  assert.equal(task.state, 'QUEUED'); assert.equal(task.modelId, 'glm-ocr');
  const areaRoot = taskArea === 'WORK' ? join(root, 'work-area') : root;
  assert.deepEqual(await readFile(join(areaRoot, 'assets', createHash('sha256').update(png).digest('hex'))), png);
  const peerBefore = await invoke(port, 'read_desktop_transcription_state', { workspaceId: null }, peerArea);
  assert.equal(peerBefore.tasks.length, 0);
  if (taskArea === 'CHAT') {
    assert.equal(await click(port, '[data-action="show-transcription"]'), true);
    await until(() => evaluate(port, `Boolean(document.querySelector('[data-action="run-transcription-task"][data-task-id="${task.id}"]'))`), '实际执行入口');
    assert.equal(await click(port, `[data-action="run-transcription-task"][data-task-id="${task.id}"]`), true);
  } else {
    await evaluate(port, `(()=>{globalThis.__ocrHarnessPendingRun=window.__TAURI__.core.invoke('run_desktop_ocr_task',${JSON.stringify({taskId: task.id})},{headers:{'x-nanfeng-data-area':'WORK'}}).catch(error=>({error:String(error)}));return true})()`);
  }
  await until(() => requests.length === 1, '首个在途OCR请求');
  result.active = (await invoke(port, 'read_desktop_transcription_state', { workspaceId: null })).tasks.find(x => x.id === task.id);
  assert.equal(result.active.state, 'TRANSCRIBING');
  stage = 'kill-in-flight'; stop(); releaseResponse();
  port = await start(true); await selectArea(port);
  result.recovered = (await invoke(port, 'read_desktop_transcription_state', { workspaceId: null })).tasks.find(x => x.id === task.id);
  assert.equal(result.recovered.state, 'RECOVERY_REQUIRED'); assert.equal(result.recovered.errorCode, 'APP_INTERRUPTED');
  assert.equal(result.recovered.attemptCount, 1); assert.equal(result.recovered.resultAttachmentId, null);
  assert.equal(requests.length, 1);
  assert.equal((await invoke(port, 'read_desktop_usage_ledger')).records.filter(x => x.conversationId === task.id).length, 0);
  assert.deepEqual(await invoke(port, 'read_desktop_transcription_state', { workspaceId: null }, peerArea), peerBefore);
  result.recoveredAttemptRows = JSON.parse(execFileSync('python', ['-c', "import sqlite3,json,sys,pathlib;c=sqlite3.connect(pathlib.Path(sys.argv[1]).as_uri()+'?mode=ro',uri=True);c.row_factory=sqlite3.Row;print(json.dumps([dict(r) for r in c.execute('SELECT * FROM desktop_ocr_attempts_v1 WHERE task_id=? ORDER BY attempt_number',(sys.argv[2],))]));c.close()", join(areaRoot, 'workspace.sqlite3'), task.id], { encoding: 'utf8', timeout: 10000 }));
  assert.equal(result.recoveredAttemptRows.length, 1); assert.equal(result.recoveredAttemptRows[0].state, 'UNKNOWN');
  assert.equal(result.recoveredAttemptRows[0].safe_error_code, 'APP_INTERRUPTED');
  if (taskArea === 'CHAT') {
    await until(() => evaluate(port, 'Boolean(document.querySelector("[data-action=show-transcription]"))'), '冷启转写入口');
    assert.equal(await click(port, '[data-action="show-transcription"]'), true);
    await until(() => evaluate(port, `Boolean(document.querySelector('[data-action="retry-transcription-task"][data-task-id="${task.id}"]'))`), '恢复后的实际重试入口');
  }
  assert.equal(requests.length, 1, '冷启及切页不自动重发未知计费请求');
  stage = 'explicit-retry';
  if (taskArea === 'CHAT') {
    assert.equal(await click(port, `[data-action="retry-transcription-task"][data-task-id="${task.id}"]`), true);
  } else {
    await invoke(port, 'retry_desktop_transcription_task', {taskId:task.id});
    await evaluate(port, `(()=>{globalThis.__ocrHarnessPendingRun=window.__TAURI__.core.invoke('run_desktop_ocr_task',${JSON.stringify({taskId:task.id})},{headers:{'x-nanfeng-data-area':'WORK'}}).catch(error=>({error:String(error)}));return true})()`);
  }
  result.completed = await until(async () => (await invoke(port, 'read_desktop_transcription_state', { workspaceId: null })).tasks.find(x => x.id === task.id && x.state === 'COMPLETED'), '显式重试成功');
  assert.equal(requests.length, 2); assert.equal(result.completed.attemptCount, 2);
  assert.equal(result.completed.providerRequestCount, 2); assert.equal(result.completed.segments[0].text, markdown);
  assert.equal(result.completed.inputTokens, 12); assert.equal(result.completed.outputTokens, 8); assert.equal(result.completed.estimatedChargeMicros, 4);
  assert.ok(result.completed.resultAttachmentId);
  const preview = await invoke(port, 'read_desktop_text_preview', { args: { workspaceId, attachmentId: result.completed.resultAttachmentId } });
  assert.equal(preview.text, markdown);
  result.ledgerBeforeCold = (await invoke(port, 'read_desktop_usage_ledger')).records.filter(x => x.conversationId === task.id);
  assert.equal(result.ledgerBeforeCold.length, 1); assert.equal(result.ledgerBeforeCold[0].chargeMicros, 4);
  assert.equal(result.ledgerBeforeCold[0].ocrDetails.attemptNumber, 2);
  result.normalClose = await normalClose();
  stage = 'successful-cold'; port = await start(true); await selectArea(port);
  result.cold = (await invoke(port, 'read_desktop_transcription_state', { workspaceId: null })).tasks.find(x => x.id === task.id);
  assert.deepEqual(result.cold, result.completed);
  result.ledgerAfterCold = (await invoke(port, 'read_desktop_usage_ledger')).records.filter(x => x.conversationId === task.id);
  assert.deepEqual(result.ledgerAfterCold, result.ledgerBeforeCold);
  assert.deepEqual(await invoke(port, 'read_desktop_transcription_state', { workspaceId: null }, peerArea), peerBefore);
  assert.equal(requests.length, 2);
  result.finalNormalClose = await normalClose();
  result.integrity = JSON.parse(execFileSync('python', [join(desktopRoot, 'scripts/check-stability-synthetic-sqlite.py'), root, '--closed-browser'], { encoding: 'utf8', timeout: 60000 }));
  assert.ok(result.integrity.allOk); result.providerRequests = requests.length; result.pass = true;
} catch (error) { result.error = String(error.stack || error); result.stage = stage; process.exitCode = 1; }
finally {
  releaseResponse(); if (launch) { result.forcedCleanup = true; stop(); }
  await new Promise(done => provider.close(done)); result.pids = pids;
  if (root) await writeFile(join(root, 'ocr-interruption-evidence.json'), JSON.stringify(result, null, 2), { flag: 'wx' });
}
console.log(JSON.stringify({ candidateSha256: receipt.sha256, root, dataArea: taskArea, pass: result.pass, providerRequests: result.providerRequests, stage: result.stage, error: result.error, pids }));
