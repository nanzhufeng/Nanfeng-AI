import assert from 'node:assert/strict';
import { createHash } from 'node:crypto';
import { execFileSync, spawn } from 'node:child_process';
import { copyFile, mkdir, readFile, writeFile } from 'node:fs/promises';
import { dirname, join, resolve, sep } from 'node:path';
import { fileURLToPath } from 'node:url';

if (process.platform !== 'win32') throw new Error('仅供 Windows 隔离候选验收。');
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
  const args = ['-RootParent', rootParent, '-StartupMode', 'normal', '-Visible', '-DebugPort', String(port)];
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

let result;
try {
  let port = await start();
  await until(() => evaluate(port, 'Boolean(window.__TAURI__?.core?.invoke)'), 'native IPC');
  const workspaceId = (await invoke(port, 'CHAT', 'ensure_desktop_local_workspace')).summary.id;
  const fixtureDir = join(root, 'synthetic-chatgpt-json-input');
  await mkdir(fixtureDir, { recursive: true });
  const jsonPath = join(fixtureDir, 'conversations.json');
  await writeFile(jsonPath, JSON.stringify([{ id: 'w14-json-synthetic', title: 'W14 JSON synthetic', create_time: 1, update_time: 2,
    mapping: { 'm-user': { parent: null, children: ['m-assistant'], message: { author: { role: 'user' }, content: { parts: ['W14 synthetic user'] } } },
      'm-assistant': { parent: 'm-user', children: [], message: { author: { role: 'assistant' }, content: { parts: ['W14 synthetic assistant'] } } } } },
    { id: 'w14-json-synthetic-second', title: 'W14 JSON second', create_time: 3, update_time: 4,
      mapping: { 'm2-user': { parent: null, children: ['m2-assistant'], message: { author: { role: 'user' }, content: { parts: ['W14 second user'] } } },
        'm2-assistant': { parent: 'm2-user', children: [], message: { author: { role: 'assistant' }, content: { parts: ['W14 second assistant'] } } } } }]), 'utf8');
  const secondExchange = join(fixtureDir, 'second-workspace.nfai-exchange');
  await copyFile(resolve(desktopRoot, '../protocol/artifacts/nfai.exchange.v1.golden.nfai-exchange'), secondExchange);
  const preflight = await invoke(port, 'CHAT', 'stage_preflight_selected_exchange', { selectedPath: secondExchange });
  const imported = await invoke(port, 'CHAT', 'import_staged_exchange_as_new_workspace', { args: { stagingId: preflight.stagingId, workspaceTitle: 'W14 JSON 第二合成工作区' } });
  const secondWorkspaceId = imported.summary.id;
  assert.notEqual(secondWorkspaceId, workspaceId);
  const first = await invoke(port, 'CHAT', 'stage_chatgpt_export_selected', { selectedPath: jsonPath, workspaceId });
  const firstId = await invoke(port, 'CHAT', 'confirm_chatgpt_import_item', { args: { workspaceId, taskId: first.id, itemId: 'chatgpt-item-0' } });
  stop();
  port = await start(true);
  await until(() => evaluate(port, 'Boolean(window.__TAURI__?.core?.invoke)'), 'interrupted task native IPC');
  const resumed = await invoke(port, 'CHAT', 'read_latest_chatgpt_import_task', { workspaceId });
  assert.equal(resumed.id, first.id);
  assert.equal(resumed.items[0].status, 'CONFIRMED');
  assert.equal(resumed.items[1].status, 'PENDING_CONFIRMATION');
  const firstRemainingId = await invoke(port, 'CHAT', 'confirm_chatgpt_import_item', { args: { workspaceId, taskId: first.id, itemId: 'chatgpt-item-1' } });
  assert.notEqual(firstRemainingId, firstId);
  const second = await invoke(port, 'CHAT', 'stage_chatgpt_export_selected', { selectedPath: jsonPath, workspaceId: secondWorkspaceId });
  assert.notEqual(first.id, second.id);
  await assert.rejects(() => invoke(port, 'CHAT', 'skip_chatgpt_import_item', { args: { workspaceId, taskId: second.id, itemId: 'chatgpt-item-0' } }));
  await assert.rejects(() => invoke(port, 'CHAT', 'confirm_chatgpt_import_item', { args: { workspaceId, taskId: second.id, itemId: 'chatgpt-item-0' } }));
  const secondId = await invoke(port, 'CHAT', 'confirm_chatgpt_import_item', { args: { workspaceId: secondWorkspaceId, taskId: second.id, itemId: 'chatgpt-item-0' } });
  assert.notEqual(firstId, secondId);
  const secondRemainingId = await invoke(port, 'CHAT', 'confirm_chatgpt_import_item', { args: { workspaceId: secondWorkspaceId, taskId: second.id, itemId: 'chatgpt-item-1' } });
  assert.notEqual(firstRemainingId, secondRemainingId);
  assert.equal((await invoke(port, 'CHAT', 'read_latest_chatgpt_import_task', { workspaceId })).id, first.id);
  assert.equal((await invoke(port, 'CHAT', 'read_latest_chatgpt_import_task', { workspaceId: secondWorkspaceId })).id, second.id);
  const replay = await invoke(port, 'CHAT', 'stage_chatgpt_export_selected', { selectedPath: jsonPath, workspaceId: secondWorkspaceId });
  assert.equal(await invoke(port, 'CHAT', 'confirm_chatgpt_import_item', { args: { workspaceId: secondWorkspaceId, taskId: replay.id, itemId: 'chatgpt-item-0' } }), secondId);
  assert.equal(await invoke(port, 'CHAT', 'confirm_chatgpt_import_item', { args: { workspaceId: secondWorkspaceId, taskId: replay.id, itemId: 'chatgpt-item-1' } }), secondRemainingId);
  stop();
  port = await start(true);
  await until(() => evaluate(port, 'Boolean(window.__TAURI__?.core?.invoke)'), 'cold native IPC');
  assert.equal((await invoke(port, 'CHAT', 'read_latest_chatgpt_import_task', { workspaceId })).workspaceId, workspaceId);
  assert.equal((await invoke(port, 'CHAT', 'read_latest_chatgpt_import_task', { workspaceId: secondWorkspaceId })).workspaceId, secondWorkspaceId);
  const firstProjection = await invoke(port, 'CHAT', 'read_desktop_workspace', { workspaceId });
  const secondProjection = await invoke(port, 'CHAT', 'read_desktop_workspace', { workspaceId: secondWorkspaceId });
  assert.equal(firstProjection.exchange.conversations.filter(item => item.id === firstId).length, 1);
  assert.equal(firstProjection.exchange.conversations.filter(item => item.id === firstRemainingId).length, 1);
  assert.equal(secondProjection.exchange.conversations.filter(item => item.id === secondId).length, 1);
  assert.equal(secondProjection.exchange.conversations.filter(item => item.id === secondRemainingId).length, 1);
  const exportedPath = join(root, 'chatgpt-json-roundtrip.nfai-exchange');
  const exported = await invoke(port, 'CHAT', 'export_desktop_workspace_to_selected_path', { workspaceId, selectedPath: exportedPath });
  const roundtripPreflight = await invoke(port, 'CHAT', 'stage_preflight_selected_exchange', { selectedPath: exportedPath });
  const roundtrip = await invoke(port, 'CHAT', 'import_staged_exchange_as_new_workspace', { args: { stagingId: roundtripPreflight.stagingId, workspaceTitle: 'ChatGPT JSON 合成回导工作区' } });
  const roundtripProjection = await invoke(port, 'CHAT', 'read_desktop_workspace', { workspaceId: roundtrip.summary.id });
  assert.equal(roundtripProjection.exchange.conversations.length, firstProjection.exchange.conversations.length);
  for (const text of ['W14 synthetic user', 'W14 synthetic assistant', 'W14 second user', 'W14 second assistant']) {
    assert.ok(JSON.stringify(roundtripProjection.exchange.conversations).includes(text), `回导丢失正文：${text}`);
  }
  result = { candidateSha256: receipt.sha256, root, workspaceId, secondWorkspaceId, firstTaskId: first.id, secondTaskId: second.id,
    firstConversationIds: [firstId, firstRemainingId], secondConversationIds: [secondId, secondRemainingId],
    exportedSemanticHash: exported.semanticHash, roundtripWorkspaceId: roundtrip.summary.id, stoppedPids };
} finally {
  stop();
}
if (result) console.log(JSON.stringify(result));
