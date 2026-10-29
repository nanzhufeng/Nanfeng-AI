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
  let route = null;
  if (launch) {
    try {
      route = await evaluate(launch.debugPort, `({
        pane: globalThis.__nanfengDesktopWorkState?.pane,
        settingsSection: globalThis.__nanfengDesktopWorkState?.settingsSection,
        dataArea: globalThis.__nanfengDesktopWorkState?.dataArea,
        workspaceId: globalThis.__nanfengDesktopWorkState?.current?.summary?.id,
        settingsPages: [...document.querySelectorAll('[data-action="open-settings-page"]')].map(x=>x.dataset.page),
        settingsButtonPresent: Boolean(document.querySelector('[data-action="show-settings"]'))
      })`);
    } catch {}
    await writeFile(join(rootParent, `nfai-import-route.${Number(launch.pid)}.json`), JSON.stringify({ label, root, pid: launch.pid, candidateSha256: receipt.sha256, route }, null, 2));
  }
  throw new Error(`${label} 未出现`);
}
const click = (port, selector) => evaluate(port, `(()=>{const node=document.querySelector(${JSON.stringify(selector)});if(!node)return false;node.click();return true})()`);
const dialogOpen = () => {
  const raw = ps(`& ${quote(join(desktopRoot, 'scripts/inspect-windows-file-dialog.ps1'))} -ProcessId ${Number(launch.pid)}`);
  const windows = raw ? JSON.parse(raw) : [];
  return windows.some(item => item.children?.some(child => child.class === '#32770'));
};
async function selectJsonThroughSettings(port, action, selectedPath, label) {
  if (await evaluate(port, 'Boolean(document.querySelector("[data-action=show-settings]"))')) assert.equal(await click(port, '[data-action="show-settings"]'), true);
  await until(() => evaluate(port, 'Boolean(document.querySelector("[data-action=open-settings-page][data-page=data]"))'), `${label} settings entry`);
  assert.equal(await click(port, '[data-action="open-settings-page"][data-page="data"]'), true);
  await until(() => evaluate(port, `Boolean(document.querySelector(${JSON.stringify(`[data-action="${action}"]`)}))`), `${label} import entry`);
  assert.equal(await click(port, `[data-action="${action}"]`), true);
  await until(dialogOpen, `${label} system picker`);
  const selected = JSON.parse(ps(`& ${quote(join(desktopRoot, 'scripts/operate-windows-file-dialog.ps1'))} -ProcessId ${Number(launch.pid)} -Action 'select' -SelectedPath ${quote(selectedPath)} -AcceptanceRoot ${quote(root)}`));
  assert.equal(selected.action, 'select');
  await until(() => !dialogOpen(), `${label} picker closed`);
}

let result;
try {
  let port = await start();
  await until(() => evaluate(port, 'Boolean(window.__TAURI__?.core?.invoke)'), 'native IPC');
  const workspaceId = (await invoke(port, 'CHAT', 'ensure_desktop_local_workspace')).summary.id;
  const fixtureDir = join(root, 'synthetic-nanfeng_knowledge-json-input');
  await mkdir(fixtureDir, { recursive: true });
  const jsonPath = join(fixtureDir, 'conversations.json');
  await writeFile(jsonPath, JSON.stringify([{ id: 101, title: 'W14 Knowledge JSON synthetic', createdAt: '2026-08-15T08:00:00Z', updatedAt: '2026-08-15T08:01:00Z', sourceText: JSON.stringify({ chat_messages: [
    { sender: 'human', created_at: '2026-08-15T08:00:00Z', text: 'W14 synthetic user' },
    { sender: 'assistant', created_at: '2026-08-15T08:00:01Z', text: 'W14 synthetic assistant' },
  ] }) }]), 'utf8');
  const secondExchange = join(fixtureDir, 'second-workspace.nfai-exchange');
  await copyFile(resolve(desktopRoot, '../protocol/artifacts/nfai.exchange.v1.golden.nfai-exchange'), secondExchange);
  const preflight = await invoke(port, 'CHAT', 'stage_preflight_selected_exchange', { selectedPath: secondExchange });
  const imported = await invoke(port, 'CHAT', 'import_staged_exchange_as_new_workspace', { args: { stagingId: preflight.stagingId, workspaceTitle: 'W14 JSON 第二合成工作区' } });
  const secondWorkspaceId = imported.summary.id;
  assert.notEqual(secondWorkspaceId, workspaceId);
  await evaluate(port, 'window.location.reload()').catch(() => {});
  await new Promise(done => setTimeout(done, 1000));
  await until(() => evaluate(port, 'Boolean(window.__TAURI__?.core?.invoke && document.querySelector("[data-action=show-settings]"))'), 'reloaded workspace UI');
  const firstWorkspaceSelector = `[data-action="select-workspace"][data-id="${workspaceId}"]`;
  await until(() => evaluate(port, `Boolean(document.querySelector(${JSON.stringify(firstWorkspaceSelector)}))`), 'visible first workspace');
  assert.equal(await click(port, firstWorkspaceSelector), true);
  await until(() => evaluate(port, `Boolean(document.querySelector(${JSON.stringify(firstWorkspaceSelector)})?.classList.contains('selected'))`), 'selected first workspace');
  await until(() => evaluate(port, 'Boolean(document.querySelector("[data-action=show-settings]"))'), 'settings button');
  assert.equal(await click(port, '[data-action="show-settings"]'), true);
  await until(() => evaluate(port, 'Boolean(document.querySelector("[data-action=open-settings-page][data-page=data]"))'), 'data settings entry');
  assert.equal(await click(port, '[data-action="open-settings-page"][data-page="data"]'), true);
  await until(() => evaluate(port, 'Boolean(document.querySelector("[data-action=select-nanfeng-knowledge-export]"))'), 'knowledge import entry');
  assert.equal(await click(port, '[data-action="select-nanfeng-knowledge-export"]'), true);
  await until(dialogOpen, 'system knowledge JSON picker');
  const selected = JSON.parse(ps(`& ${quote(join(desktopRoot, 'scripts/operate-windows-file-dialog.ps1'))} -ProcessId ${Number(launch.pid)} -Action 'select' -SelectedPath ${quote(jsonPath)} -AcceptanceRoot ${quote(root)}`));
  assert.equal(selected.action, 'select');
  await until(() => !dialogOpen(), 'knowledge picker closed');
  const first = await until(async () => {
    const task = await invoke(port, 'CHAT', 'read_latest_nanfeng_knowledge_import_task', { workspaceId });
    return task?.status === 'COMPLETED' ? task : null;
  }, 'visible knowledge import completion');
  const firstId = first.items[0].conversationId;
  assert.ok(firstId);
  assert.equal(await click(port, '[data-action="open-settings-page"][data-page="json-import-results"]'), true);
  await until(() => evaluate(port, 'document.querySelector(".android-settings-main")?.textContent?.includes("南枫知识库 JSON")'), 'visible knowledge import result');
  await evaluate(port, 'window.location.reload()').catch(() => {});
  await new Promise(done => setTimeout(done, 1000));
  await until(() => evaluate(port, 'Boolean(window.__TAURI__?.core?.invoke && document.querySelector("[data-action=show-settings]"))'), 'reloaded second workspace UI');
  const secondWorkspaceSelector = `[data-action="select-workspace"][data-id="${secondWorkspaceId}"]`;
  await until(() => evaluate(port, `Boolean(document.querySelector(${JSON.stringify(secondWorkspaceSelector)}))`), 'visible second workspace');
  assert.equal(await click(port, secondWorkspaceSelector), true);
  await until(() => evaluate(port, `Boolean(document.querySelector(${JSON.stringify(secondWorkspaceSelector)})?.classList.contains('selected'))`), 'selected second workspace');
  assert.equal(await click(port, '[data-action="show-settings"]'), true);
  await until(() => evaluate(port, 'Boolean(document.querySelector("[data-action=open-settings-page][data-page=data]"))'), 'second data settings entry');
  assert.equal(await click(port, '[data-action="open-settings-page"][data-page="data"]'), true);
  assert.equal(await click(port, '[data-action="select-nanfeng-knowledge-export"]'), true);
  await until(dialogOpen, 'second system knowledge JSON picker');
  const selectedSecond = JSON.parse(ps(`& ${quote(join(desktopRoot, 'scripts/operate-windows-file-dialog.ps1'))} -ProcessId ${Number(launch.pid)} -Action 'select' -SelectedPath ${quote(jsonPath)} -AcceptanceRoot ${quote(root)}`));
  assert.equal(selectedSecond.action, 'select');
  await until(() => !dialogOpen(), 'second knowledge picker closed');
  const second = await until(async () => {
    const task = await invoke(port, 'CHAT', 'read_latest_nanfeng_knowledge_import_task', { workspaceId: secondWorkspaceId });
    return task?.status === 'COMPLETED' ? task : null;
  }, 'second visible knowledge import completion');
  assert.notEqual(first.id, second.id);
  const secondId = second.items[0].conversationId;
  assert.ok(secondId);
  assert.notEqual(firstId, secondId);
  // Import completion keeps the data settings page. Use its actual return control.
  assert.equal(await click(port, '[data-action="show-chat"]'), true);
  await until(() => evaluate(port, 'document.body.innerText.includes("W14 Knowledge JSON synthetic")'), 'visible second imported conversation');
  assert.equal((await invoke(port, 'CHAT', 'read_latest_nanfeng_knowledge_import_task', { workspaceId })).id, first.id);
  assert.equal((await invoke(port, 'CHAT', 'read_latest_nanfeng_knowledge_import_task', { workspaceId: secondWorkspaceId })).id, second.id);
  const replay = await invoke(port, 'CHAT', 'stage_nanfeng_knowledge_export_selected', { selectedPath: jsonPath, workspaceId: secondWorkspaceId });
  assert.equal(await invoke(port, 'CHAT', 'confirm_nanfeng_knowledge_import_item', { args: { workspaceId: secondWorkspaceId, taskId: replay.id, itemId: 'p6j-item-0' } }), secondId);
  const changed = JSON.parse(await readFile(jsonPath, 'utf8'));
  const changedSource = JSON.parse(changed[0].sourceText);
  changedSource.chat_messages.push({ sender: 'assistant', created_at: '2026-08-15T08:00:02Z', text: 'W14 incremental synthetic answer' });
  changed[0].sourceText = JSON.stringify(changedSource);
  await writeFile(jsonPath, JSON.stringify(changed), 'utf8');
  assert.equal(await click(port, '[data-action="show-settings"]'), true);
  await until(() => evaluate(port, 'Boolean(document.querySelector("[data-action=open-settings-page][data-page=data]"))'), 'incremental data settings entry');
  assert.equal(await click(port, '[data-action="open-settings-page"][data-page="data"]'), true);
  assert.equal(await click(port, '[data-action="select-nanfeng-knowledge-export"]'), true);
  await until(dialogOpen, 'incremental system knowledge JSON picker');
  const selectedIncremental = JSON.parse(ps(`& ${quote(join(desktopRoot, 'scripts/operate-windows-file-dialog.ps1'))} -ProcessId ${Number(launch.pid)} -Action 'select' -SelectedPath ${quote(jsonPath)} -AcceptanceRoot ${quote(root)}`));
  assert.equal(selectedIncremental.action, 'select');
  await until(() => !dialogOpen(), 'incremental knowledge picker closed');
  const conflict = await until(async () => {
    const task = await invoke(port, 'CHAT', 'read_latest_nanfeng_knowledge_import_task', { workspaceId: secondWorkspaceId });
    return task?.id !== replay.id && task?.items?.[0]?.failure === 'CONFLICT_REIMPORT' ? task : null;
  }, 'persisted incremental conflict');
  assert.equal(conflict.items[0].status, 'PENDING_CONFIRMATION');
  await assert.rejects(invoke(port, 'CHAT', 'confirm_nanfeng_knowledge_import_item', { args: { workspaceId: secondWorkspaceId, taskId: conflict.id, itemId: 'p6j-item-0' } }));
  assert.equal((await invoke(port, 'CHAT', 'read_desktop_workspace', { workspaceId: secondWorkspaceId })).exchange.conversations.filter(item => item.importedFrom === 'NANFENG_KNOWLEDGE_EXPORT').length, 1);
  if (!await evaluate(port, 'Boolean(document.querySelector("[data-action=open-settings-page][data-page=json-import-results]"))')) {
    assert.equal(await click(port, '[data-action="show-settings"]'), true);
    await until(() => evaluate(port, 'Boolean(document.querySelector("[data-action=open-settings-page][data-page=data]"))'), 'conflict data settings entry');
    assert.equal(await click(port, '[data-action="open-settings-page"][data-page="data"]'), true);
  }
  assert.equal(await click(port, '[data-action="open-settings-page"][data-page="json-import-results"]'), true);
  await until(() => evaluate(port, 'Boolean(document.querySelector("[data-action=confirm-nanfeng-knowledge-conflict][data-item-id=p6j-item-0]"))'), 'visible incremental conflict confirmation');
  assert.equal(await click(port, '[data-action="confirm-nanfeng-knowledge-conflict"][data-item-id="p6j-item-0"]'), true);
  const committedIncremental = await until(async () => {
    const task = await invoke(port, 'CHAT', 'read_nanfeng_knowledge_import_task', { taskId: conflict.id });
    return task.status === 'COMPLETED' && task.items[0].status === 'CONFIRMED' ? task : null;
  }, 'confirmed incremental import');
  const incrementalId = committedIncremental.items[0].conversationId;
  assert.notEqual(incrementalId, secondId);
  const jsonFormats = [
    {
      name: 'ChatGPT', slug: 'chatgpt', action: 'select-chatgpt-export', itemId: 'chatgpt-item-0', importedFrom: 'CHATGPT_EXPORT',
      initial: [{ id: 'w14-chatgpt-synthetic', title: 'W14 ChatGPT synthetic', create_time: 1, update_time: 2, mapping: { m: { parent: null, children: [], message: { author: { role: 'user' }, content: { parts: ['first ChatGPT version'] } } } } }],
      mutate: data => { data[0].mapping.m.message.content.parts[0] = 'second ChatGPT version'; },
    },
    {
      name: 'Claude', slug: 'claude', action: 'select-claude-export', itemId: 'claude-item-0', importedFrom: 'CLAUDE_EXPORT',
      initial: [{ uuid: 'w14-claude-synthetic', name: 'W14 Claude synthetic', created_at: '2026-08-15T08:00:00Z', updated_at: '2026-08-15T08:01:00Z', chat_messages: [{ uuid: 'w14-claude-message', sender: 'human', created_at: '2026-08-15T08:00:00Z', text: 'first Claude version', parent_message_uuid: null }] }],
      mutate: data => { data[0].chat_messages[0].text = 'second Claude version'; },
    },
  ];
  const formatReceipts = [];
  for (const format of jsonFormats) {
    const path = join(fixtureDir, `${format.slug}-incremental.json`);
    await writeFile(path, JSON.stringify(format.initial), 'utf8');
    await selectJsonThroughSettings(port, format.action, path, format.name);
    const firstFormatTask = await until(async () => {
      const task = await invoke(port, 'CHAT', `read_latest_${format.slug}_import_task`, { workspaceId: secondWorkspaceId });
      return task?.status === 'COMPLETED' && task.items[0].status === 'CONFIRMED' ? task : null;
    }, `${format.name} first import`);
    const originalId = firstFormatTask.items[0].conversationId;
    const changedFormat = structuredClone(format.initial);
    format.mutate(changedFormat);
    await writeFile(path, JSON.stringify(changedFormat), 'utf8');
    await selectJsonThroughSettings(port, format.action, path, `${format.name} changed package`);
    const conflictTask = await until(async () => {
      const task = await invoke(port, 'CHAT', `read_latest_${format.slug}_import_task`, { workspaceId: secondWorkspaceId });
      return task?.id !== firstFormatTask.id && task?.items?.[0]?.failure === 'CONFLICT_REIMPORT' ? task : null;
    }, `${format.name} persisted conflict`);
    await assert.rejects(invoke(port, 'CHAT', `confirm_${format.slug}_import_item`, { args: { workspaceId: secondWorkspaceId, taskId: conflictTask.id, itemId: format.itemId } }));
    assert.equal((await invoke(port, 'CHAT', 'read_desktop_workspace', { workspaceId: secondWorkspaceId })).exchange.conversations.filter(item => item.importedFrom === format.importedFrom).length, 1);
    if (await evaluate(port, 'Boolean(document.querySelector("[data-action=show-settings]"))')) assert.equal(await click(port, '[data-action="show-settings"]'), true);
    await until(() => evaluate(port, 'Boolean(document.querySelector("[data-action=open-settings-page][data-page=json-import-results]"))'), `${format.name} results entry`);
    assert.equal(await click(port, '[data-action="open-settings-page"][data-page="json-import-results"]'), true);
    const selector = `[data-action="confirm-${format.slug}-conflict"][data-item-id="${format.itemId}"]`;
    await until(() => evaluate(port, `Boolean(document.querySelector(${JSON.stringify(selector)}))`), `${format.name} visible conflict`);
    assert.equal(await click(port, selector), true);
    const committedFormat = await until(async () => {
      const task = await invoke(port, 'CHAT', `read_${format.slug}_import_task`, { taskId: conflictTask.id });
      return task?.status === 'COMPLETED' && task.items[0].status === 'CONFIRMED' ? task : null;
    }, `${format.name} explicit confirmation`);
    const changedId = committedFormat.items[0].conversationId;
    assert.notEqual(changedId, originalId);
    formatReceipts.push({ name: format.name, originalId, changedId, firstTaskId: firstFormatTask.id, conflictTaskId: conflictTask.id, importedFrom: format.importedFrom });
  }
  stop();
  port = await start(true);
  await until(() => evaluate(port, 'Boolean(window.__TAURI__?.core?.invoke)'), 'cold native IPC');
  assert.equal((await invoke(port, 'CHAT', 'read_latest_nanfeng_knowledge_import_task', { workspaceId })).workspaceId, workspaceId);
  assert.equal((await invoke(port, 'CHAT', 'read_latest_nanfeng_knowledge_import_task', { workspaceId: secondWorkspaceId })).workspaceId, secondWorkspaceId);
  const firstProjection = await invoke(port, 'CHAT', 'read_desktop_workspace', { workspaceId });
  const secondProjection = await invoke(port, 'CHAT', 'read_desktop_workspace', { workspaceId: secondWorkspaceId });
  assert.equal(firstProjection.exchange.conversations.filter(item => item.id === firstId).length, 1);
  assert.equal(secondProjection.exchange.conversations.filter(item => item.id === secondId).length, 1);
  assert.equal(secondProjection.exchange.conversations.filter(item => item.id === incrementalId).length, 1);
  assert.ok(secondProjection.exchange.conversations.find(item => item.id === incrementalId)?.messages.some(message => message.blocks.some(block => block.text === 'W14 incremental synthetic answer')));
  assert.equal(firstProjection.exchange.conversations.filter(item => item.importedFrom === 'NANFENG_KNOWLEDGE_EXPORT').length, 1);
  for (const format of formatReceipts) {
    assert.equal(secondProjection.exchange.conversations.filter(item => item.importedFrom === format.importedFrom).length, 2);
    assert.equal(secondProjection.exchange.conversations.filter(item => [format.originalId, format.changedId].includes(item.id)).length, 2);
    assert.equal(firstProjection.exchange.conversations.filter(item => item.importedFrom === format.importedFrom).length, 0);
  }
  result = { candidateSha256: receipt.sha256, root, workspaceId, secondWorkspaceId, firstTaskId: first.id, secondTaskId: second.id, incrementalTaskId: conflict.id, firstConversationId: firstId, secondConversationId: secondId, incrementalConversationId: incrementalId, formatReceipts, stoppedPids };
} finally {
  stop();
}
if (result) console.log(JSON.stringify(result));
