import { verifyVisibleUsageOutcomes } from './verify-visible-usage-outcomes.mjs';
import assert from 'node:assert/strict';
import { createHash } from 'node:crypto';
import { execFileSync, spawn } from 'node:child_process';
import { mkdir, readFile, writeFile } from 'node:fs/promises';
import { dirname, join, resolve, sep } from 'node:path';
import { fileURLToPath } from 'node:url';
import http from 'node:http';

assert.ok(process.execArgv.includes('--max-old-space-size=2048'));
if (process.platform !== 'win32') throw new Error('仅在 Windows 独立候选上验收。');
const desktopRoot = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const rootParent = resolve(process.argv[2] || 'D:\\CodexTemp');
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
const requests = [];const serverErrors=[];
const chatRequests = [];
let releaseResponse;
const responseGate = new Promise(resolve => { releaseResponse = resolve; });
const provider = http.createServer(async (request, response) => {
 try{
  const chunks = [];
  for await (const chunk of request) chunks.push(chunk);
  const body = Buffer.concat(chunks).toString('utf8');
  if (request.url !== '/api/paas/v4/layout_parsing') {
    assert.equal(request.url,'/','current explicit mock endpoint is a full URL');
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
  requests.push({ path: request.url, method: request.method, authorizationMatchesSynthetic: request.headers.authorization==='Bearer nfai-synthetic-windows-ocr',
    model: JSON.parse(body).model, sourcePresent: body.includes(materialBytes.toString('base64')) });
  if (cancelMode || crossAreaMode || activeBackupMode || activePrivacyMode || cancelledPrivacyMode || closePrivacyMode || killPendingMode) await responseGate;
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
 }catch(error){serverErrors.push({path:request.url,message:String(error)});response.writeHead(500,{'Content-Type':'application/json'});response.end(JSON.stringify({error:{code:'synthetic-mock-rejection'}}));}
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
      try { const emitted=JSON.parse(line);readFile(emitted.launchReceipt,'utf8').then(data=>done(JSON.parse(data)),fail); } catch (error) { fail(error); }
      child.stdout.destroy(); child.stderr.destroy(); child.unref();
    });
    child.stderr.on('data', chunk => { err += chunk.toString(); });
    child.on('error', error => { clearTimeout(timer); fail(error); });
    child.on('exit', code => { if (!out.includes('"pid"')) { clearTimeout(timer); fail(new Error(`启动失败 ${code}：${err}`)); } });
  });
}
async function start(reopen = false) {
  const port = await reservePort();
  const args = ['-RootParent', rootParent, '-ExecutablePath', source, '-Visible', '-StartupMode', 'normal', '-DebugPort', String(port), '-OcrTestEndpoint', endpoint];
  if (sendChatMode) args.push('-OrdinaryChatTestEndpoint', endpoint);
  // All current chain windows are visible for owned natural-close checks.
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
  const tabs = await fetch(`http://127.0.0.1:${port}/json/list`).then(response => response.json());
  const tab = tabs.find(item => item.type === 'page' && item.url === 'http://tauri.localhost/');
  assert.ok(tab, 'WebView2 页面未就绪');
  const socket = new WebSocket(tab.webSocketDebuggerUrl);
  try {
    await new Promise((done, fail) => {
      socket.addEventListener('open', done, { once: true });
      socket.addEventListener('error', fail, { once: true });
    });
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
async function invoke(port, command, args = {}, area = 'CHAT') {
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
const closes=[];let failed=false;
async function normalClose(){
 const pid=Number(launch.pid);const info=JSON.parse(ps(`$p=Get-CimInstance Win32_Process -Filter "ProcessId=${pid}";if(!$p -or [IO.Path]::GetFullPath([string]$p.ExecutablePath) -ne ${quote(runningExecutable)} -or (Get-FileHash -LiteralPath ${quote(runningExecutable)}).Hash -ne ${quote(receipt.sha256)}){throw 'PID identity mismatch'};$n=Get-Process -Id ${pid};@{title=$n.MainWindowTitle;responding=$n.Responding;accepted=$n.CloseMainWindow()}|ConvertTo-Json -Compress`));assert.ok(info.title&&info.responding&&info.accepted);
 await until(()=>ps(`@(Get-CimInstance Win32_Process -Filter "ProcessId=${pid}").Count`)==='0','owned native natural exit');await until(()=>ps(`@(Get-CimInstance Win32_Process -Filter "Name='msedgewebview2.exe'" | Where-Object {$_.CommandLine -and $_.CommandLine.Contains(${quote(launch.webviewProfile)})}).Count`)==='0','owned profile exit');pids.push(pid);launch=null;return info;
}
let result;
let stage = "startup";
try {
  let port = await start();
  await until(() => evaluate(port, 'Boolean(window.__TAURI__?.core?.invoke)'), '原生 IPC');
  stage = 'workspace';
  const workspaceId = (await invoke(port, 'ensure_desktop_local_workspace')).summary.id;
  const fixtureDir = join(root, 'synthetic-ocr-inputs');
  await mkdir(fixtureDir, { recursive: true });
  const selectedPath = join(fixtureDir, pdfMode ? 'synthetic.pdf' : 'synthetic.png');
  await writeFile(selectedPath, materialBytes);
  let task;
  if (pickerMode) {
    await until(() => evaluate(port, 'Boolean(document.querySelector("[data-action=show-transcription]"))'), '南枫转写入口');
    assert.equal(await click(port, '[data-action="show-transcription"]'), true);
    await until(() => evaluate(port, 'Boolean(document.querySelector("[data-action=pick-transcription-document]"))'), '选择图片或 PDF');
    const dialogOpen = () => {
      const raw = ps(`& ${quote(join(desktopRoot, 'scripts/inspect-windows-file-dialog.ps1'))} -ProcessId ${Number(launch.pid)}`);
      const windows = raw ? JSON.parse(raw) : [];
      return windows.some(item => item.children?.some(child => child.class === '#32770'));
    };
    const operate = (action, path = '') => {
      const command = `& ${quote(join(desktopRoot, 'scripts/operate-windows-file-dialog.ps1'))} -ProcessId ${Number(launch.pid)} -Action ${quote(action)}`
        + (path ? ` -SelectedPath ${quote(path)} -AcceptanceRoot ${quote(root)}` : '');
      return JSON.parse(ps(command));
    };
    assert.equal(await click(port, '[data-action="pick-transcription-document"]'), true);
    await until(dialogOpen, 'OCR 系统选择对话框');
    assert.equal(operate('cancel').action, 'cancel');
    await until(() => !dialogOpen(), '取消选择');
    assert.equal((await invoke(port, 'read_desktop_transcription_state', { workspaceId: null })).tasks.length, 0);
    assert.equal(await click(port, '[data-action="pick-transcription-document"]'), true);
    await until(dialogOpen, '再次打开 OCR 系统选择对话框');
    assert.equal(operate('select', selectedPath).action, 'select');
    await until(() => !dialogOpen(), '选入合成 PNG');
    task = await until(async () => {
      const state = await invoke(port, 'read_desktop_transcription_state', { workspaceId: null });
      return state.tasks.find(item => item.sourceDisplayName === (pdfMode ? 'synthetic.pdf' : 'synthetic.png')) || null;
    }, '系统选择后持久任务');
  } else {
    task = await invoke(port, 'import_desktop_ocr_source', { args: { workspaceId, selectedPath } });
    await until(() => evaluate(port, 'Boolean(document.querySelector("[data-action=show-transcription]"))'), '南枫转写入口');
    assert.equal(await click(port, '[data-action="show-transcription"]'), true);
  }
  assert.equal(task.state, 'QUEUED');
  assert.equal(task.modelId, 'glm-ocr');
  assert.equal(task.sourceMimeType, pdfMode ? 'application/pdf' : 'image/png');
  assert.equal(JSON.stringify(task).includes(selectedPath), false);
  assert.deepEqual(await readFile(join(root, 'assets', createHash('sha256').update(materialBytes).digest('hex'))), materialBytes);
  if (pdfMode) {
    await until(() => evaluate(port, `Boolean(document.querySelector('[data-action="preview-transcription-source"][data-task-id="${task.id}"]'))`), 'PDF 原件预览入口');
    assert.equal(await click(port, `[data-action="preview-transcription-source"][data-task-id="${task.id}"]`), true);
    await until(() => evaluate(port, 'document.querySelector(".pdf-preview-frame")?.getAttribute("src")?.startsWith("data:image/png;base64,")'), 'PDF 原件首页');
    assert.equal(await click(port, '[data-action="close-pdf-preview"]'), true);
  }
  await until(() => evaluate(port, `Boolean(document.querySelector('[data-action="run-transcription-task"][data-task-id="${task.id}"]'))`), '开始转写');
  assert.equal(await click(port, `[data-action="run-transcription-task"][data-task-id="${task.id}"]`), true);
  if (crossAreaMode) {
    await until(() => requests.length === 1, 'OCR 已请求并暂停响应');
    assert.equal(await click(port, '[data-action="show-chat"]'), true);
    await until(() => evaluate(port, 'Boolean(document.querySelector("[data-action=show-work]"))'), '工作区入口');
    assert.equal(await click(port, '[data-action="show-work"]'), true);
    await until(() => evaluate(port, 'globalThis.__nanfengDesktopWorkState?.dataArea === "WORK" && globalThis.__nanfengDesktopWorkState?.pane === "work"'), '切到工作区');
    await until(() => evaluate(port, 'globalThis.__nanfengDesktopWorkState?.status === "本地工作区已就绪；尚未启用联网模型。"'), '工作区配置回读完成');
    const snapshotExpression = 'JSON.stringify((({dataArea,pane,status,error,transcriptionBusyTaskId})=>({dataArea,pane,status,error,transcriptionBusyTaskId}))(globalThis.__nanfengDesktopWorkState))';
    const before = await evaluate(port, snapshotExpression);
    releaseResponse();
    const completed = await until(async () => (await invoke(port, 'read_desktop_transcription_state', { workspaceId: null })).tasks.find(item => item.id === task.id && item.state === 'COMPLETED'), '原聊天区 OCR 完成');
    await new Promise(done => setTimeout(done, 500));
    assert.equal(await evaluate(port, snapshotExpression), before, '原区迟到回执污染工作区');
    assert.equal(completed.segments[0].text, markdown);
    assert.ok(!(await invoke(port, 'read_desktop_transcription_state', { workspaceId: null }, 'WORK')).tasks.some(item => item.id === task.id));
    closes.push(await normalClose());
    port = await start(true);
    await until(() => evaluate(port, 'Boolean(window.__TAURI__?.core?.invoke)'), '跨区 OCR 冷启 IPC');
    const cold = (await invoke(port, 'read_desktop_transcription_state', { workspaceId: null })).tasks.find(item => item.id === task.id);
    assert.equal(cold.state, 'COMPLETED');
    assert.equal(cold.segments[0].text, markdown);
    assert.equal(requests.length, 1);
    assert.ok(!(await invoke(port, 'read_desktop_transcription_state', { workspaceId: null }, 'WORK')).tasks.some(item => item.id === task.id));
    result = { candidateSha256: receipt.sha256, root, taskId: task.id, crossArea: true, originalAreaCompleted: true, workProjectionUnchanged: true, coldReadback: true, requestCount: requests.length };
  } else if (activePrivacyMode || cancelledPrivacyMode || closePrivacyMode) {
    stage = 'privacy-active';
    await until(() => requests.length === 1, 'OCR 请求已到达本地暂停服务');
    const active = (await invoke(port, 'read_desktop_transcription_state', { workspaceId: null })).tasks.find(x => x.id === task.id);
    assert.equal(active.state, 'TRANSCRIBING');
    if (cancelledPrivacyMode) {
      assert.equal((await invoke(port, 'cancel_desktop_transcription_task', { taskId: task.id })).state, 'CANCELLED');
    }
    for (const area of ['CHAT', 'WORK']) {
      let rejected = null;
      try {
        await invoke(port, 'preview_desktop_privacy_deletion', { args: { scope: 'ALL_LOCAL_BUSINESS_DATA' } }, area);
      } catch (error) { rejected = String(error); }
      assert.ok(rejected?.includes('运行中的任务'), `${area} 全部删除预览未拒绝运行中 OCR：${rejected}`);
    }
    if (closePrivacyMode) {
      const pid = Number(launch.pid);
      const closed = JSON.parse(ps(`
        $p=Get-CimInstance Win32_Process -Filter "ProcessId=${pid}"
        if(!$p -or [IO.Path]::GetFullPath([string]$p.ExecutablePath) -ne ${quote(runningExecutable)} -or (Get-FileHash -LiteralPath ${quote(runningExecutable)}).Hash -ne ${quote(receipt.sha256)}){throw 'PID identity mismatch'}
        Add-Type 'using System; using System.Runtime.InteropServices; public static class NfOcrWindow { [DllImport("user32.dll")] public static extern bool PostMessage(IntPtr hwnd, uint message, IntPtr wparam, IntPtr lparam); }'
        Add-Type -AssemblyName UIAutomationClient
        Add-Type -AssemblyName UIAutomationTypes
        $condition=[System.Windows.Automation.PropertyCondition]::new([System.Windows.Automation.AutomationElement]::ProcessIdProperty,${pid})
        $windows=[System.Windows.Automation.AutomationElement]::RootElement.FindAll([System.Windows.Automation.TreeScope]::Children,$condition)
        $main=@($windows | Where-Object {$_.Current.Name -eq '南枫 AI · 隔离测试（非日常版）' -and $_.Current.ClassName -eq 'Tauri Window'})
        if($main.Count -ne 1){throw 'exact acceptance window unavailable'}
        $hwnd=[IntPtr]$main[0].Current.NativeWindowHandle
        @{hwnd=$hwnd.ToInt64();title=$main[0].Current.Name;closePosted=[NfOcrWindow]::PostMessage($hwnd,0x0010,[IntPtr]::Zero,[IntPtr]::Zero)} | ConvertTo-Json -Compress
      `));
      assert.equal(closed.closePosted, true);
      await new Promise(done => setTimeout(done, 2000));
      assert.equal(ps(`[bool](Get-Process -Id ${pid} -ErrorAction SilentlyContinue)`), 'True', 'OCR 工作线程结束前进程不得退出');
      releaseResponse();
      await until(() => ps(`[bool](Get-Process -Id ${pid} -ErrorAction SilentlyContinue)`) === 'False', 'OCR 返回后的自然退出');
      pids.push(pid);
      launch = null;
    } else releaseResponse();
    const expectedState = cancelledPrivacyMode ? 'CANCELLED' : 'COMPLETED';
    if (!closePrivacyMode) {
      const complete = await until(async () => (await invoke(port, 'read_desktop_transcription_state', { workspaceId: null })).tasks.find(x => x.id === task.id && x.state === expectedState), 'OCR 终态');
      if (!cancelledPrivacyMode) assert.equal(complete.segments[0].text, markdown);
      await until(async () => {
        try { return (await invoke(port, 'preview_desktop_privacy_deletion', { args: { scope: 'ALL_LOCAL_BUSINESS_DATA' } })).fingerprint; }
        catch (error) { if (String(error).includes('运行中的任务')) return false; throw error; }
      }, '工作线程结束后预览恢复');
    }
    closes.push(await normalClose());
    port = await start(true);
    const cold = (await invoke(port, 'read_desktop_transcription_state', { workspaceId: null })).tasks.find(x => x.id === task.id);
    assert.equal(cold.state, expectedState);
    if (!cancelledPrivacyMode) assert.equal(cold.segments[0].text, markdown);
    assert.equal(requests.length, 1);
    assert.ok((await invoke(port, 'preview_desktop_privacy_deletion', { args: { scope: 'ALL_LOCAL_BUSINESS_DATA' } })).fingerprint);
    result = { candidateSha256: receipt.sha256, root, taskId: task.id, activePrivacyPreviewRejected: true, cancelledWhileRequestHeld: cancelledPrivacyMode, normalWindowCloseDrained: closePrivacyMode, bothAreas: true, workerEndedPreviewAllowed: true, deletionExecuted: false, coldReadback: true, providerRequests: requests.length, pids };
  } else if (activeBackupMode) {
    await until(()=>requests.length===1,'运行中的 OCR 请求');
    const active = (await invoke(port,'read_desktop_transcription_state',{workspaceId:null})).tasks.find(x=>x.id===task.id);
    assert.equal(active.state,'TRANSCRIBING');
    const backupPath = join(rootParent,`nfai-ocr-active-backup.${task.id}.nfai-backup`);
    await invoke(port,'export_desktop_local_backup',{selectedPath:backupPath});
    closes.push(await normalClose());
    releaseResponse();
    port = await start(true);
    const interrupted = (await invoke(port,'read_desktop_transcription_state',{workspaceId:null})).tasks.find(x=>x.id===task.id);
    assert.equal(interrupted.state,'RECOVERY_REQUIRED');
    const preflight = await invoke(port,'preflight_desktop_local_backup',{selectedPath:backupPath});
    const restored = await invoke(port,'restore_desktop_local_backup',{fingerprint:preflight.fingerprint,replaceLocal:true});
    assert.equal(restored.restartRequired,true);
    closes.push(await normalClose());
    port = await start(true);
    const cold = (await invoke(port,'read_desktop_transcription_state',{workspaceId:null})).tasks.find(x=>x.id===task.id);
    assert.equal(cold.state,'RECOVERY_REQUIRED');
    assert.equal(cold.errorCode,'BACKUP_RESTORED');
    assert.equal(cold.resultAttachmentId,null);
    assert.equal(cold.attemptCount,1);
    assert.equal(requests.length,1);
    assert.equal((await invoke(port,'read_desktop_usage_ledger')).records.filter(x=>x.conversationId===task.id).length,0);
    await until(()=>evaluate(port,'Boolean(document.querySelector("[data-action=show-transcription]"))'),'转写入口');
    await click(port,'[data-action="show-transcription"]');
    await until(()=>evaluate(port,`Boolean(document.querySelector('[data-action="retry-transcription-task"][data-task-id="${task.id}"]'))`),'恢复后的显式重试入口');
    result={candidateSha256:receipt.sha256,root,taskId:task.id,activeBackup:true,backupPath,restoredRecoveryRequired:true,explicitRetryVisible:true,providerRequests:requests.length,pids};
  } else if (killPendingMode) {
    stage = 'kill-pending';
    await until(() => requests.length === 1, 'OCR 请求已经到达服务');
    const active = (await invoke(port, 'read_desktop_transcription_state', { workspaceId: null })).tasks.find(x => x.id === task.id);
    assert.equal(active.state, 'TRANSCRIBING');
    closes.push(await normalClose()); // Exact candidate identity check followed by Stop-Process -Force.
    releaseResponse();
    port = await start(true);
    const recovered = (await invoke(port, 'read_desktop_transcription_state', { workspaceId: null })).tasks.find(x => x.id === task.id);
    assert.equal(recovered.state, 'RECOVERY_REQUIRED');
    assert.equal(recovered.errorCode, 'APP_INTERRUPTED');
    assert.equal(recovered.attemptCount, 1);
    assert.equal(recovered.resultAttachmentId, null);
    assert.equal(requests.length, 1);
    assert.equal((await invoke(port, 'read_desktop_usage_ledger')).records.filter(x => x.conversationId === task.id).length, 0);
    await until(() => evaluate(port, 'Boolean(document.querySelector("[data-action=show-transcription]"))'), '转写入口');
    assert.equal(await click(port, '[data-action="show-transcription"]'), true);
    await until(() => evaluate(port, `Boolean(document.querySelector('[data-action="retry-transcription-task"][data-task-id="${task.id}"]'))`), '中断恢复显式重试入口');
    assert.equal(requests.length, 1);
    assert.equal(await click(port, `[data-action="retry-transcription-task"][data-task-id="${task.id}"]`), true);
    const complete = await until(async () => (await invoke(port, 'read_desktop_transcription_state', { workspaceId: null })).tasks.find(x => x.id === task.id && x.state === 'COMPLETED'), '用户重试完成');
    assert.equal(requests.length, 2);
    assert.equal(complete.attemptCount, 2);
    assert.ok(complete.resultAttachmentId);
    const resultAttachmentId = complete.resultAttachmentId;
    closes.push(await normalClose());
    port = await start(true);
    const cold = (await invoke(port, 'read_desktop_transcription_state', { workspaceId: null })).tasks.find(x => x.id === task.id);
    assert.equal(cold.state, 'COMPLETED');
    assert.equal(cold.attemptCount, 2);
    assert.equal(cold.resultAttachmentId, resultAttachmentId);
    assert.equal(requests.length, 2);
    result = { candidateSha256: receipt.sha256, root, taskId: task.id, killedPending: true,
      recoveryRequiredBeforeUserRetry: true, explicitRetryVisibleAndClicked: true,
      resultAttachmentId, completedAfterExplicitRetry: true, coldReadback: true,
      providerRequests: requests.length, pids };
  } else if (cancelMode) {
    await until(() => requests.length === 1, 'OCR 请求已送达回环端点');
    const cancelled = await invoke(port, 'cancel_desktop_transcription_task', { taskId: task.id });
    assert.equal(cancelled.state, 'CANCELLED');
    releaseResponse();
    await until(async () => {
      const state = await invoke(port, 'read_desktop_transcription_state', { workspaceId: null });
      return state.tasks.find(item => item.id === task.id && item.state === 'CANCELLED') || null;
    }, 'OCR 取消状态');
    await until(() => evaluate(port, 'document.querySelector(".transcription-document-detail")?.textContent.includes("已取消") && !document.querySelector(".transcription-task-row")?.textContent.includes("执行中")'), '迟到响应处理后取消仍可见');
    const afterResponse = (await invoke(port, 'read_desktop_transcription_state', { workspaceId: null })).tasks.find(item => item.id === task.id);
    assert.equal(afterResponse.state, 'CANCELLED');
    assert.equal(afterResponse.resultAttachmentId, null);
    assert.equal(afterResponse.providerRequestCount, 1);
    const cancelledUsage = await until(async () => {
      const ledger = await invoke(port, 'read_desktop_usage_ledger');
      return ledger.records.find(item => item.conversationId === task.id) || null;
    }, '取消后已返回的 OCR 用量');
    assert.equal(cancelledUsage.inputTokens, 12);
    assert.equal(cancelledUsage.outputTokens, 8);
    assert.equal(cancelledUsage.chargeMicros, 4);
    assert.equal(cancelledUsage.requestState,'CANCELLED');assert.equal(cancelledUsage.safeErrorCode,'CANCELLED');assert.equal(cancelledUsage.modelElapsedMs,null);assert.equal(cancelledUsage.ocrDetails.attemptNumber,1);
    closes.push(await normalClose());
    port = await start(true);
    await until(() => evaluate(port, 'Boolean(window.__TAURI__?.core?.invoke)'), '取消冷启 IPC');
    const cold = (await invoke(port, 'read_desktop_transcription_state', { workspaceId: null })).tasks.find(item => item.id === task.id);
    assert.equal(cold.state, 'CANCELLED');
    assert.equal(cold.resultAttachmentId, null);
    assert.equal(requests.length, 1);
    const coldUsage = await invoke(port, 'read_desktop_usage_ledger');
    assert.equal(coldUsage.records.filter(item => item.conversationId === task.id).length, 1);
    assert.deepEqual(coldUsage.records.find(item=>item.conversationId===task.id),cancelledUsage);
    if(usageUiMode)await verifyVisibleUsageOutcomes({port,evaluate,click,until,area:'CHAT',section:'ocr',modelId:'glm-ocr',labels:['已停止 · CANCELLED']});
    result = { candidateSha256: receipt.sha256, root, workspaceId, taskId: task.id,
      cancelledWhileProviderPending: true, responseRejectedAfterCancel: true, returnedUsagePersisted: true,
      coldReadback: true, providerRequests: requests.length, pids };
  } else if (failureMode || oversizedResponseMode || emptyMarkdownUsageMode) {
    const failed = await until(async () => {
      const state = await invoke(port, 'read_desktop_transcription_state', { workspaceId: null });
      return state.tasks.find(item => item.id === task.id && item.state === 'FAILED') || null;
    }, 'OCR 失败状态');
    const expectedError = oversizedResponseMode ? 'RESPONSE_TOO_LARGE' : emptyMarkdownUsageMode ? 'EMPTY_MARKDOWN' : 'RATE_LIMIT';
    assert.equal(failed.errorCode, expectedError);
    assert.equal(failed.providerRequestCount, 1);
    assert.equal(failed.attemptCount, 1);
    assert.equal(failed.resultAttachmentId, null);
    await until(() => evaluate(port, `Boolean(document.querySelector('[data-action="retry-transcription-task"][data-task-id="${task.id}"]'))`), '可见显式重试');
    assert.equal(requests.length, 1);
    if (emptyMarkdownUsageMode || failureUsageMode) {
      assert.equal(failed.inputTokens, 12);
      assert.equal(failed.outputTokens, 8);
      assert.equal(failed.estimatedChargeMicros, 4);
      if (failureUsageMode) assert.equal(failed.providerRequestId, 'nfai-synthetic-failed-ocr');
      const ledger = await invoke(port, 'read_desktop_usage_ledger');
      const records = ledger.records.filter(item => item.conversationId === task.id);
      assert.equal(records.length, 1);
      assert.equal(records[0].inputTokens, 12);
      assert.equal(records[0].outputTokens, 8);
      assert.equal(records[0].chargeMicros, 4);assert.equal(records[0].requestState,'FAILED');assert.equal(records[0].safeErrorCode,expectedError);assert.equal(records[0].modelElapsedMs,null);assert.equal(records[0].ocrDetails.sourceDisplayName,task.sourceDisplayName);assert.equal(records[0].ocrDetails.attemptNumber,1);
    }
    closes.push(await normalClose());
    port = await start(true);
    await until(() => evaluate(port, 'Boolean(window.__TAURI__?.core?.invoke)'), '失败冷启 IPC');
    const cold = (await invoke(port, 'read_desktop_transcription_state', { workspaceId: null })).tasks.find(item => item.id === task.id);
    assert.equal(cold.state, 'FAILED');
    assert.equal(cold.errorCode, expectedError);
    assert.equal(cold.providerRequestCount, 1);
    assert.equal(requests.length, 1);
    if (emptyMarkdownUsageMode || failureUsageMode) {
      assert.equal(cold.inputTokens, 12);
      assert.equal(cold.outputTokens, 8);
      assert.equal(cold.estimatedChargeMicros, 4);
      const ledger = await invoke(port, 'read_desktop_usage_ledger');
      assert.equal(ledger.records.filter(item => item.conversationId === task.id).length, 1);
    }
    if (usageUiMode && (emptyMarkdownUsageMode || failureUsageMode)) await verifyVisibleUsageOutcomes({port,evaluate,click,until,area:'CHAT',section:'ocr',modelId:'glm-ocr',labels:[`失败 · ${expectedError}`]});
    result = { candidateSha256: receipt.sha256, root, workspaceId, taskId: task.id,
      failure: expectedError, usagePersisted: emptyMarkdownUsageMode || failureUsageMode, visibleRetry: true, coldReadback: true, providerRequests: requests.length, pids };
  } else {
  if (retryMode) {
    const failed = await until(async () => {
      const state = await invoke(port, 'read_desktop_transcription_state', { workspaceId: null });
      return state.tasks.find(item => item.id === task.id && item.state === 'FAILED') || null;
    }, '首次 OCR 429');
    assert.equal(failed.errorCode, retryEmptyMode ? 'EMPTY_MARKDOWN' : 'RATE_LIMIT');
    assert.equal(failed.attemptCount, 1);
    assert.equal(failed.providerRequestCount, 1);
    assert.equal(requests.length, 1);
    closes.push(await normalClose());
    port = await start(true);
    await until(() => evaluate(port, 'Boolean(window.__TAURI__?.core?.invoke)'), '失败冷启 IPC');
    assert.equal((await invoke(port, 'read_desktop_transcription_state', { workspaceId: null })).tasks.find(item => item.id === task.id)?.state, 'FAILED');
    assert.equal(requests.length, 1, '冷启不得自动重试未知计费请求');
    await until(() => evaluate(port, 'Boolean(document.querySelector("[data-action=show-transcription]"))'), '冷启南枫转写入口');
    assert.equal(await click(port, '[data-action="show-transcription"]'), true);
    await until(() => evaluate(port, `Boolean(document.querySelector('[data-action="retry-transcription-task"][data-task-id="${task.id}"]'))`), '显式重新转写');
    assert.equal(await click(port, `[data-action="retry-transcription-task"][data-task-id="${task.id}"]`), true);
  }
  const complete = await until(async () => {
    const state = await invoke(port, 'read_desktop_transcription_state', { workspaceId: null });
    return state.tasks.find(item => item.id === task.id && item.state === 'COMPLETED') || null;
  }, 'OCR 完成状态');
  assert.equal(requests.length, retryMode ? 2 : 1);
  assert.deepEqual(requests[0], { path: '/api/paas/v4/layout_parsing', method: 'POST',
    authorizationMatchesSynthetic: true, model: 'glm-ocr', sourcePresent: true });
  assert.equal(complete.providerRequestCount, retryMode ? 2 : 1);
  assert.equal(complete.attemptCount, retryMode ? 2 : 1);
  assert.equal(complete.providerRequestId, 'nfai-synthetic-ocr-request');
  assert.equal(complete.pageCount, pdfMode ? 2 : 1);
  assert.equal(complete.inputTokens, 12);
  assert.equal(complete.outputTokens, 8);
  assert.equal(complete.estimatedChargeMicros, 4);
  assert.ok(complete.resultAttachmentId);
  const completedLedgerBefore=(await invoke(port,'read_desktop_usage_ledger')).records.filter(x=>x.conversationId===task.id);
  const completedUsageBefore=completedLedgerBefore.find(x=>x.ocrDetails?.attemptNumber===complete.attemptCount);
  if(retryEmptyMode){assert.equal(completedLedgerBefore.length,2);assert.equal(completedLedgerBefore.find(x=>x.ocrDetails?.attemptNumber===1)?.requestState,'FAILED');assert.equal(completedLedgerBefore.find(x=>x.ocrDetails?.attemptNumber===1)?.safeErrorCode,'EMPTY_MARKDOWN');}
  assert.equal(completedUsageBefore.requestState,'COMPLETED');assert.ok(Number.isSafeInteger(completedUsageBefore.modelElapsedMs)&&completedUsageBefore.modelElapsedMs>=0);
  assert.equal(completedUsageBefore.ocrDetails.sourceDisplayName,task.sourceDisplayName);assert.equal(completedUsageBefore.ocrDetails.pageCount,pdfMode?2:1);assert.equal(completedUsageBefore.ocrDetails.providerRequestId,'nfai-synthetic-ocr-request');assert.equal(completedUsageBefore.ocrDetails.attemptNumber,retryMode?2:1);
  assert.equal(complete.segments[0].text, markdown);
  await until(() => evaluate(port, `document.querySelector('.transcription-document-result')?.textContent.includes(${JSON.stringify('合成 OCR 正文')})`), '可见 OCR 结果');
  const preview = await invoke(port, 'read_desktop_text_preview', { args: { workspaceId, attachmentId: complete.resultAttachmentId } });
  assert.equal(preview.text, markdown);
  assert.equal(await click(port, `[data-action="preview-transcription-result"][data-task-id="${task.id}"]`), true);
  await until(() => evaluate(port, `document.querySelector('.text-preview-surface .local-text-preview')?.textContent.includes(${JSON.stringify('合成 OCR 正文')})`), 'OCR 结果预览');
  assert.equal(await click(port, '[data-action="close-text-preview"]'), true);
  const exportPath = join(fixtureDir, 'result.md');
  await invoke(port, 'export_desktop_transcription_task', { args: { taskId: task.id, selectedPath: exportPath, format: 'md' } });
  assert.equal((await readFile(exportPath)).toString('utf8'), markdown);
  let sentConversationId = null;
  if (continueChatMode) {
    await invoke(port, 'save_desktop_conversation_draft', { args: {
      workspaceId, conversationId: null, text: '合成已有新对话草稿', attachmentIds: [],
      modelId: null, toneOverride: null, webSearchOverride: false,
    } });
    assert.equal(await click(port, `[data-action="continue-chat-with-transcription"][data-task-id="${task.id}"]`), true);
    await until(() => evaluate(port, `Boolean(document.querySelector('.chat-composer-attachments [data-action="remove-composer-attachment"][data-id="${complete.resultAttachmentId}"]'))`), 'OCR Markdown 进入聊天草稿');
    const draft = await until(async () => {
      const value = await invoke(port, 'read_desktop_conversation_draft', { workspaceId, conversationId: null });
      return value?.attachments?.some(item => item.id === complete.resultAttachmentId) ? value : null;
    }, 'OCR Markdown 持久草稿');
    assert.equal(draft.attachments.length, 1);
    assert.equal(draft.text, '合成已有新对话草稿');
    assert.equal(draft.webSearchOverride, false);
    if (sendChatMode) {
      await until(() => evaluate(port, 'document.querySelector("#chat-composer")?.value === "合成已有新对话草稿"'), '续聊文字');
      await until(() => evaluate(port, 'Boolean(document.querySelector("[data-action=save-local-message]:not([disabled])"))'), '续聊发送');
      assert.equal(await click(port, '[data-action="save-local-message"]'), true);
      const conversation = await until(async () => {
        const current = await invoke(port, 'read_desktop_workspace', { workspaceId });
        return current.exchange.conversations.find(item => item.messages?.some(message =>
          message.role === 'user' && message.blocks?.some(block => block.text === '合成已有新对话草稿')) &&
          item.messages?.find(message => message.id === item.currentLeafId)?.delivery === 'COMPLETE') || null;
      }, 'OCR Markdown 续聊完成');
      sentConversationId = conversation.id;
      const user = conversation.messages.find(message => message.role === 'user' && message.blocks?.some(block => block.text === '合成已有新对话草稿'));
      assert.ok(user.blocks.some(block => block.kind === 'ASSET_REF' && block.asset?.id === complete.resultAttachmentId));
      assert.equal(chatRequests.length, 1);
      assert.ok(chatRequests[0].markdownPresent && chatRequests[0].resultNamePresent && chatRequests[0].idempotencyKey && !chatRequests[0].rawSourcePresent,
        `OCR 续聊外发事实不足：${JSON.stringify(chatRequests[0])}`);
      await until(() => evaluate(port, 'document.querySelector(".chat-scroll")?.textContent.includes("合成 OCR 续聊回答")'), '可见 OCR 续聊回答');
      const ledger = await invoke(port, 'read_desktop_usage_ledger');
      assert.ok(ledger.records.some(item => item.conversationId === conversation.id && item.chargeMicros === 5));
      assert.ok(!(await invoke(port, 'read_desktop_conversation_draft', { workspaceId, conversationId: null }))?.attachments?.length);
    }
  }
  closes.push(await normalClose());
  port = await start(true);
  await until(() => evaluate(port, 'Boolean(window.__TAURI__?.core?.invoke)'), '冷启 IPC');
  const cold = (await invoke(port, 'read_desktop_transcription_state', { workspaceId: null })).tasks.find(item => item.id === task.id);
  assert.equal(cold.state, 'COMPLETED');
  assert.equal(cold.resultAttachmentId, complete.resultAttachmentId);
  assert.equal(cold.providerRequestCount, retryMode ? 2 : 1);
  assert.equal(cold.segments[0].text, markdown);
  assert.equal(requests.length, retryMode ? 2 : 1);
  if (continueChatMode) {
    if (sendChatMode) {
      const workspace = await invoke(port, 'read_desktop_workspace', { workspaceId });
      const conversation = workspace.exchange.conversations.find(item => item.id === sentConversationId);
      assert.ok(conversation?.messages.some(message => message.blocks?.some(block => block.asset?.id === complete.resultAttachmentId)));
      assert.equal(chatRequests.length, 1);
    } else {
      const coldDraft = await invoke(port, 'read_desktop_conversation_draft', { workspaceId, conversationId: null });
      assert.equal(coldDraft?.text, '合成已有新对话草稿');
      assert.equal(coldDraft?.webSearchOverride, false);
      assert.ok(coldDraft?.attachments?.some(item => item.id === complete.resultAttachmentId));
    }
  }
  if (usageUiMode) {
    await until(() => evaluate(port, 'Boolean(document.querySelector("[data-action=show-settings]"))'), '设置入口');
    assert.equal(await click(port, '[data-action="show-settings"]'), true);
    await until(() => evaluate(port, 'Boolean(document.querySelector("[data-action=open-settings-page][data-page=model]"))'), '模型与联网设置');
    assert.equal(await click(port, '[data-action="open-settings-page"][data-page="model"]'), true);
    await until(() => evaluate(port, 'Boolean(document.querySelector("[data-action=open-settings-page][data-page=conversation-cost]"))'), '费用与用量入口');
    assert.equal(await click(port, '[data-action="open-settings-page"][data-page="conversation-cost"]'), true);
    await until(() => evaluate(port, 'Boolean(document.querySelector("[data-action=select-usage-section][data-section=ocr]"))'), '南枫转写费用分类').catch(async error => {
      const snapshot = await evaluate(port, '({page:globalThis.__nanfengDesktopWorkState?.settingsSection, records:globalThis.__nanfengDesktopWorkState?.usageLedger?.records?.map(x=>({entryId:x.entryId,modelId:x.modelId,inputTokens:x.inputTokens,outputTokens:x.outputTokens})), text:document.querySelector(".android-settings-main")?.textContent?.slice(0,400)})');
      throw new Error(`${error.message}: ${JSON.stringify(snapshot)}`);
    });
    assert.equal(await click(port, '[data-action="select-usage-section"][data-section="ocr"]'), true);
    await until(() => evaluate(port, 'document.querySelector(".android-settings-ledger-row")?.textContent.includes("glm-ocr")'), '南枫转写费用明细');
    const row = await evaluate(port, 'document.querySelector(".android-settings-ledger-row")?.textContent');
    assert.equal(await evaluate(port, 'document.querySelectorAll(".android-settings-ledger-row").length'), retryEmptyMode?2:1);
    assert.ok(row.includes('南枫转写任务'), `OCR 费用记录误标为会话：${row}`);
    assert.ok(row.includes('输入 12 / 输出 8 Token'), `OCR 用量未呈现：${row}`);
    assert.ok(row.includes('已完成'));
    for(const text of ['智谱','Attempt transcription-',task.sourceDisplayName,'nfai-synthetic-ocr-request',pdfMode?'2 页':'1 页'])assert.ok(row.includes(text),text);
    assert.ok(!row.includes('耗时未记录'));
    assert.deepEqual((await invoke(port,'read_desktop_usage_ledger')).records.filter(x=>x.conversationId===task.id),completedLedgerBefore);
  }
  let backupPath = null;
  let peerBeforeBackup = null;
  if (backupMode) {
    backupPath = join(rootParent,`nfai-ocr-backup.${task.id}.nfai-backup`);
    peerBeforeBackup = await invoke(port,'read_desktop_transcription_state',{workspaceId:null},'WORK');
    const artifact = await invoke(port,'export_desktop_local_backup',{selectedPath:backupPath});
    assert.ok(artifact);
    assert.ok((await readFile(backupPath)).length>0);
  }
  if (deleteMode) {
    await invoke(port,'delete_desktop_transcription_task',{taskId:task.id});
    assert.ok(!(await invoke(port,'read_desktop_transcription_state',{workspaceId:null})).tasks.some(x=>x.id===task.id));
    const afterDelete = (await invoke(port,'read_desktop_usage_ledger')).records.filter(x=>x.conversationId===task.id);
    assert.equal(afterDelete.length,completedLedgerBefore.length);
    for (const entry of afterDelete) {
      const before = completedLedgerBefore.find(x=>x.entryId===entry.entryId);
      assert.deepEqual({...entry,ocrDetails:null,requestState:null,safeErrorCode:null,modelElapsedMs:null},{...before,ocrDetails:null,requestState:null,safeErrorCode:null,modelElapsedMs:null});
      assert.equal(entry.ocrDetails,null);
      assert.equal(entry.modelElapsedMs,null);
    }
    closes.push(await normalClose());
    port = await start(true);
    assert.ok(!(await invoke(port,'read_desktop_transcription_state',{workspaceId:null})).tasks.some(x=>x.id===task.id));
    assert.deepEqual((await invoke(port,'read_desktop_usage_ledger')).records.filter(x=>x.conversationId===task.id),afterDelete);
    assert.equal(requests.length,retryMode?2:1);
  }
  if (backupMode) {
    const preflight = await invoke(port,'preflight_desktop_local_backup',{selectedPath:backupPath});
    assert.ok(preflight.fingerprint);
    const restored = await invoke(port,'restore_desktop_local_backup',{fingerprint:preflight.fingerprint,replaceLocal:true});
    assert.equal(restored.restartRequired,true);
    assert.ok(restored.checkpointId);
    closes.push(await normalClose());
    port = await start(true);
    const restoredState = await invoke(port,'read_desktop_transcription_state',{workspaceId:null});
    assert.deepEqual(restoredState.tasks.find(x=>x.id===task.id),complete);
    assert.deepEqual((await invoke(port,'read_desktop_usage_ledger')).records.filter(x=>x.conversationId===task.id),completedLedgerBefore);
    assert.deepEqual(await invoke(port,'read_desktop_transcription_state',{workspaceId:null},'WORK'),peerBeforeBackup);
    const backupStatus = await invoke(port,'read_desktop_local_backup_status');
    assert.equal(backupStatus.operation,'COMPLETED');
    assert.equal(requests.length,retryMode?2:1);
    assert.equal((await invoke(port,'read_desktop_text_preview',{args:{workspaceId,attachmentId:complete.resultAttachmentId}})).text,markdown);
  }
  result = { candidateSha256: receipt.sha256, root, workspaceId, taskId: task.id,
    backupRoundtrip:backupMode, backupPath, peerAreaPreserved:backupMode,
    deletedTaskDetails:deleteMode, immutableUsageAfterDelete:deleteMode,
    systemPicker: pickerMode,
    sourceKind: pdfMode ? 'PDF' : 'PNG',
    sourceAttachmentId: task.sourceAttachmentId, resultAttachmentId: complete.resultAttachmentId,
    visibleResult: true, previewed: true, exported: true, continuedToDraft: continueChatMode,
    usageUi: usageUiMode, immutableOcrDetails:true, explicitRetryAfter429: retryMode&&!retryEmptyMode, explicitRetryAfterEmpty:retryEmptyMode,
    sentConversationId, coldReadback: true, providerRequests: requests.length, chatRequests: chatRequests.length, pids };
  }
} catch (error) {
  failed=true;await writeFile(resolve(desktopRoot,'../docs/review/20261004-ocr-current-chain',`native-failed-${Date.now()}.json`),JSON.stringify({candidateSha256:receipt.sha256,stage,root,pids,activePid:launch?.pid,message:String(error),requests,chatRequests,closes,pass:false},null,2));console.error(JSON.stringify({stage,root,pids,message:String(error),stack:error.stack}));
  throw error;
} finally {
  releaseResponse();
  if(launch){if(failed)stop();else closes.push(await normalClose());}
  await new Promise(done => provider.close(done));
}
assert.deepEqual(serverErrors,[]);result={...result,pids,closes,requests,chatRequests,serverErrors,forcedCleanup:false,realService:false,startupMode:'NORMAL',pass:true};await writeFile(join(root,'ocr-current-chain-native.json'),JSON.stringify(result,null,2));await writeFile(resolve(desktopRoot,'../docs/review/20261004-ocr-current-chain',cancelMode?'cancel-native-current.json':'native-current.json'),JSON.stringify(result,null,2)+'\n');console.log(JSON.stringify(result));
