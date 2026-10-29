import assert from 'node:assert/strict';
import { createHash } from 'node:crypto';
import { execFileSync, spawn } from 'node:child_process';
import { readFile, writeFile } from 'node:fs/promises';
import http from 'node:http';
import { dirname, join, resolve, sep } from 'node:path';
import { fileURLToPath } from 'node:url';
import { packageExchange, preflightPackage, readZip, semanticHash } from '../../protocol/scripts/exchange-lib.mjs';

if (process.platform !== 'win32') throw new Error('仅供 Windows 标记合成根原生验收');
const desktopRoot = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const rootParent = resolve(process.argv[2] || 'D:\\CodexTemp');
const receipt = JSON.parse(await readFile(join(desktopRoot, 'output/windows-acceptance/current.json'), 'utf8'));
assert.equal(receipt.identifier, 'com.nanzhufeng.ai.desktop.windowsacceptance');
const executable = resolve(desktopRoot, 'output/windows-acceptance', receipt.sha256, 'NanfengAI-Windows-Acceptance.exe');
const runningExecutable = resolve(desktopRoot, 'output/windows-acceptance-binaries', receipt.sha256, 'nanfeng-ai-desktop-spike.exe');
assert.equal(resolve(receipt.executable), executable);
assert.equal(createHash('sha256').update(await readFile(executable)).digest('hex').toUpperCase(), receipt.sha256);
const quote = value => `'${String(value).replaceAll("'", "''")}'`;
const ps = script => execFileSync('pwsh', ['-NoProfile', '-Command', script], { encoding: 'utf8', timeout: 30000 }).trim();
const socketServer = http.createServer();
await new Promise(done => socketServer.listen(0, '127.0.0.1', done));
const port = socketServer.address().port;
await new Promise(done => socketServer.close(done));
let launch = null;
let root = '';
const stoppedPids = [];
async function start() {
  const args = ['-RootParent', rootParent, '-StartupMode', 'normal', '-Visible', '-DebugPort', String(port)];
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
      child.stdout.destroy(); child.stderr.destroy(); child.unref();
    });
    child.stderr.on('data', chunk => { error += chunk.toString(); });
    child.on('error', cause => { clearTimeout(timer); fail(cause); });
    child.on('exit', code => { if (!output.includes('"pid"')) { clearTimeout(timer); fail(new Error(`启动失败 ${code}：${error}`)); } });
  });
  assert.equal(result.sha256, receipt.sha256);
  assert.equal(resolve(result.executable), runningExecutable);
  root = resolve(result.root);
  assert.ok(root.startsWith(rootParent + sep));
  assert.equal(JSON.parse(await readFile(join(root, '.nanfeng-windows-acceptance.json'), 'utf8')).identifier, receipt.identifier);
  launch = result;
}
function stop() {
  if (!launch) return;
  const pid = Number(launch.pid);
  ps(`$p=Get-Process -Id ${pid} -ErrorAction SilentlyContinue; if ($p) { if ([IO.Path]::GetFullPath([string]$p.Path) -ne ${quote(runningExecutable)} -or (Get-FileHash -LiteralPath ${quote(runningExecutable)} -Algorithm SHA256).Hash -ne ${quote(receipt.sha256)}) { throw 'PID identity mismatch' }; Stop-Process -Id ${pid} -Force -ErrorAction Stop }`);
  stoppedPids.push(pid);
  launch = null;
}
async function evaluate(expression) {
  const tabs = await fetch(`http://127.0.0.1:${port}/json/list`).then(response => response.json());
  const tab = tabs.find(item => item.type === 'page' && item.url === 'http://tauri.localhost/');
  assert.ok(tab, 'WebView2 页面未就绪');
  const socket = new WebSocket(tab.webSocketDebuggerUrl);
  try {
    await new Promise((done, fail) => { socket.addEventListener('open', done, { once: true }); socket.addEventListener('error', fail, { once: true }); });
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
const invoke = (command, args = {}) => evaluate(`window.__TAURI__.core.invoke(${JSON.stringify(command)},${JSON.stringify(args)},{headers:{'x-nanfeng-data-area':'CHAT'}}).catch(error=>({__error:String(error)}))`);
const click = selector => evaluate(`(()=>{const node=document.querySelector(${JSON.stringify(selector)});if(!node||node.disabled)return false;node.click();return true})()`);
async function until(check, label, timeoutMs = 30000) {
  const deadline = Date.now() + timeoutMs;
  while (Date.now() < deadline) {
    try { if (await check()) return; } catch { /* startup or redraw */ }
    await new Promise(done => setTimeout(done, 200));
  }
  throw new Error(`${label} 未在原生窗口就绪`);
}
async function importWorkspace(path, title) {
  const staged = await invoke('stage_preflight_selected_exchange', { selectedPath: path });
  assert.ok(!staged.__error, JSON.stringify(staged));
  const imported = await invoke('import_staged_exchange_as_new_workspace', { args: { stagingId: staged.stagingId, workspaceTitle: title } });
  assert.ok(!imported.__error, JSON.stringify(imported));
  return imported;
}
const visibleWorkspace = id => `[data-action="select-workspace"][data-id="${id}"]`;
const row = id => `[data-conversation-row][data-id="${id}"]`;
let result;
try {
  await start();
  await until(() => evaluate('Boolean(window.__TAURI__?.core?.invoke)'), 'native IPC');
  const goldenBytes = await readFile(resolve(desktopRoot, '../protocol/artifacts/nfai.exchange.v1.golden.nfai-exchange'));
  const golden = preflightPackage(goldenBytes).exchange;
  const conversationId = golden.conversations[0].id;
  const second = structuredClone(golden);
  second.export.id = 'export-batch-workspace-scope-b';
  second.conversations[0].title = '第二工作区同 ID 会话';
  second.export.semanticHash = semanticHash(second);
  const assets = new Map([...readZip(goldenBytes)].filter(([name]) => name.startsWith('assets/')));
  const firstPath = join(root, 'synthetic-batch-workspace-a.nfai-exchange');
  const secondPath = join(root, 'synthetic-batch-workspace-b.nfai-exchange');
  await writeFile(firstPath, goldenBytes);
  await writeFile(secondPath, packageExchange(second, assets));
  const first = await importWorkspace(firstPath, '批量范围 A');
  const secondImported = await importWorkspace(secondPath, '批量范围 B');
  assert.notEqual(first.summary.id, secondImported.summary.id);
  assert.equal(first.exchange.conversations[0].id, conversationId);
  assert.equal(secondImported.exchange.conversations[0].id, conversationId);
  await evaluate('window.location.reload()').catch(() => {});
  await until(() => evaluate('Boolean(window.__TAURI__?.core?.invoke && document.querySelector("[data-action=select-workspace]"))'), '工作区可见入口');
  await until(() => evaluate(`Boolean(document.querySelector(${JSON.stringify(visibleWorkspace(first.summary.id))}))`), '第一工作区入口');
  assert.equal(await click(visibleWorkspace(first.summary.id)), true);
  await until(() => evaluate(`document.querySelector(${JSON.stringify(visibleWorkspace(first.summary.id))})?.classList.contains('selected')`), '第一工作区选中');
  assert.equal(await click('[data-action="toggle-conversation-batch-edit"]'), true);
  const firstBatch = `${row(conversationId)} [data-action="toggle-batch-conversation"]`;
  await until(() => evaluate(`Boolean(document.querySelector(${JSON.stringify(firstBatch)}))`), '第一工作区批量行');
  assert.equal(await click(firstBatch), true);
  await until(() => evaluate(`document.querySelector(${JSON.stringify(firstBatch)})?.getAttribute('aria-checked') === 'true'`), '第一工作区勾选');
  assert.equal(await click(visibleWorkspace(secondImported.summary.id)), true);
  await until(() => evaluate(`document.querySelector(${JSON.stringify(visibleWorkspace(secondImported.summary.id))})?.classList.contains('selected')`), '第二工作区选中');
  assert.equal(await evaluate('Boolean(document.querySelector(".chat-batch-select, [data-action=confirm-conversation-batch-delete]"))'), false);
  const secondRow = row(conversationId);
  await until(() => evaluate(`Boolean(document.querySelector(${JSON.stringify(secondRow)}))`), '第二工作区会话');
  assert.equal(await click('[data-action="toggle-conversation-batch-edit"]'), true);
  const secondBatch = `${secondRow} [data-action="toggle-batch-conversation"]`;
  await until(() => evaluate(`Boolean(document.querySelector(${JSON.stringify(secondBatch)}))`), '第二工作区批量行');
  assert.equal(await evaluate(`document.querySelector(${JSON.stringify(secondBatch)})?.getAttribute('aria-checked')`), 'false');
  assert.equal(await click(secondBatch), true);
  assert.equal(await click('[data-action="request-batch-conversation-delete"]'), true);
  await until(() => evaluate('Boolean(document.querySelector("[data-action=confirm-conversation-batch-delete]"))'), '第二工作区删除确认');
  assert.equal(await click(visibleWorkspace(first.summary.id)), true);
  await until(() => evaluate(`document.querySelector(${JSON.stringify(visibleWorkspace(first.summary.id))})?.classList.contains('selected')`), '旧删除确认切走');
  assert.equal(await evaluate('Boolean(document.querySelector("[data-action=confirm-conversation-batch-delete]"))'), false);
  assert.notEqual((await invoke('read_desktop_workspace', { workspaceId: first.summary.id })).exchange.conversations[0].deleted, true);
  assert.notEqual((await invoke('read_desktop_workspace', { workspaceId: secondImported.summary.id })).exchange.conversations[0].deleted, true);
  assert.equal(await click(visibleWorkspace(secondImported.summary.id)), true);
  await until(() => evaluate(`document.querySelector(${JSON.stringify(visibleWorkspace(secondImported.summary.id))})?.classList.contains('selected')`), '第二工作区重新打开');
  assert.equal(await click('[data-action="toggle-conversation-batch-edit"]'), true);
  assert.equal(await click(secondBatch), true);
  assert.equal(await click('[data-action="request-batch-conversation-delete"]'), true);
  await until(() => evaluate('Boolean(document.querySelector("[data-action=confirm-conversation-batch-delete]"))'), '明确删除确认');
  assert.equal(await click('[data-action="confirm-conversation-batch-delete"]'), true);
  await until(async () => (await invoke('read_desktop_workspace', { workspaceId: secondImported.summary.id })).exchange.conversations[0].deleted === true, '仅第二工作区软删除');
  assert.notEqual((await invoke('read_desktop_workspace', { workspaceId: first.summary.id })).exchange.conversations[0].deleted, true);
  result = { candidateSha256: receipt.sha256, root, firstWorkspaceId: first.summary.id, secondWorkspaceId: secondImported.summary.id,
    conversationId, firstSelectionClearedOnWorkspaceSwitch: true, staleDeleteDialogDismissed: true,
    explicitSecondWorkspaceDeleteOnly: true, stoppedPids };
} finally { stop(); }
if (result) console.log(JSON.stringify(result));
