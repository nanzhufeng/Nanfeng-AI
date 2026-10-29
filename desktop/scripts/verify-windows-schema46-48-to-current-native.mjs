import assert from 'node:assert/strict';
import { createHash } from 'node:crypto';
import { execFileSync, spawn } from 'node:child_process';
import { copyFile, mkdir, readFile, writeFile } from 'node:fs/promises';
import { dirname, join, resolve, sep } from 'node:path';
import { fileURLToPath } from 'node:url';

if (process.platform !== 'win32') throw new Error('仅供 Windows 隔离候选验收。');
const desktopRoot = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const rootParent = resolve(process.argv[2] || 'D:\\CodexTemp');
const schema46 = process.argv.includes('--schema46');
const schema47 = process.argv.includes('--schema47');
assert.ok(!(schema46 && schema47));
const oldSchema = schema46 ? 46 : schema47 ? 47 : 48;
const provider = oldSchema <= 47 ? 'chatgpt' : 'claude';
const oldSha = schema46
  ? '4C95B808EB4E1E29F9602BAAFBCF95BBE6B924E56AA226E8AA409FF744A718E9'
  : schema47 ? 'CA6426B40DC4BC49CDDDC207E37A07F57832968D4AE7AC23E30585957DBD41E0'
    : '477B485DC1A94BDD2DBE032B194FD34167C2CCE6EDEFE88376CDA971EC6E2480';
const receipt = JSON.parse(await readFile(join(desktopRoot, 'output/windows-acceptance/current.json'), 'utf8'));
assert.equal(receipt.identifier, 'com.nanzhufeng.ai.desktop.windowsacceptance');
const schemaSource = await readFile(join(desktopRoot, 'src-tauri/src/desktop_schema_source.rs'), 'utf8');
const currentSchema = Number(schemaSource.match(/CURRENT_AREA_SCHEMA_VERSION: u32 = (\d+);/)?.[1]);
assert.ok(Number.isSafeInteger(currentSchema) && currentSchema >= oldSchema, '当前源码 Schema 身份无效');
const candidates = [oldSha, receipt.sha256].map(sha => ({
  sha,
  source: resolve(desktopRoot, 'output/windows-acceptance', sha, 'NanfengAI-Windows-Acceptance.exe'),
  running: resolve(desktopRoot, 'output/windows-acceptance-binaries', sha, 'nanfeng-ai-desktop-spike.exe'),
}));
for (const candidate of candidates) assert.equal(createHash('sha256').update(await readFile(candidate.source)).digest('hex').toUpperCase(), candidate.sha);
assert.equal(resolve(receipt.executable), candidates[1].source);
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
let candidate = null;
let root = '';
const stoppedPids = [];
async function start(index, reopen = false) {
  candidate = candidates[index];
  const port = await reservePort();
  const args = ['-RootParent', rootParent, '-StartupMode', 'normal', '-Visible', '-DebugPort', String(port), '-ExecutablePath', candidate.source];
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
      child.stdout.destroy(); child.stderr.destroy(); child.unref();
    });
    child.stderr.on('data', chunk => { error += chunk.toString(); });
    child.on('error', cause => { clearTimeout(timer); fail(cause); });
    child.on('exit', code => { if (!output.includes('"pid"')) { clearTimeout(timer); fail(new Error(`启动失败 ${code}：${error}`)); } });
  });
  assert.equal(result.sha256, candidate.sha);
  assert.equal(resolve(result.executable), candidate.running);
  launch = result;
  root = resolve(result.root);
  assert.ok(root.startsWith(rootParent + sep));
  assert.equal(JSON.parse(await readFile(join(root, '.nanfeng-windows-acceptance.json'), 'utf8')).identifier, receipt.identifier);
  return port;
}
function stop() {
  if (!launch) return;
  const pid = Number(launch.pid);
  ps(`$p=Get-CimInstance Win32_Process -Filter "ProcessId=${pid}"; if ($p) { if ([IO.Path]::GetFullPath([string]$p.ExecutablePath) -ne ${quote(candidate.running)} -or (Get-FileHash -LiteralPath ${quote(candidate.running)} -Algorithm SHA256).Hash -ne ${quote(candidate.sha)}) { throw 'PID identity mismatch' }; Stop-Process -Id ${pid} -Force -ErrorAction Stop }`);
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
        const message = JSON.parse(event.data); if (message.id !== 1) return;
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
  while (Date.now() < deadline) { try { const value = await read(); if (value) return value; } catch { /* Startup. */ } await new Promise(done => setTimeout(done, 200)); }
  throw new Error(`${label} 未出现`);
}
function inspectDatabase(path) {
  const script = 'import sqlite3,pathlib,hashlib,json,sys; p=pathlib.Path(sys.argv[1]); owner=sys.argv[2]; assert owner in ("chatgpt","claude"); c=sqlite3.connect(p); rows=c.execute("select storage_key,package_hash from "+owner+"_import_tasks").fetchall(); result={"schema":c.execute("pragma user_version").fetchone()[0],"integrity":c.execute("pragma integrity_check").fetchone()[0],"tasks":len(rows),"receipts":c.execute("select count(*) from "+owner+"_import_receipts").fetchone()[0],"privateHashesMatch":all(not pathlib.PurePath(k).is_absolute() and hashlib.sha256((p.parent/k).read_bytes()).hexdigest().lower()==h.lower() for k,h in rows)}; print(json.dumps(result)); c.close()';
  return JSON.parse(execFileSync('python', ['-c', script, path, provider], { encoding: 'utf8', timeout: 10000 }));
}

let result;
try {
  let port = await start(0);
  await until(() => evaluate(port, 'Boolean(window.__TAURI__?.core?.invoke)'), 'old native IPC');
  const workspaceId = (await invoke(port, 'CHAT', 'ensure_desktop_local_workspace')).summary.id;
  const fixtureDir = join(root, 'synthetic-schema48-input');
  await mkdir(fixtureDir, { recursive: true });
  const jsonPath = join(fixtureDir, `${provider}-conversations.json`);
  const payload = oldSchema <= 47
    ? [{ id: 'upgrade-chatgpt', title: 'Upgrade ChatGPT', create_time: 1, update_time: 2, mapping: { 'upgrade-message': { parent: null, message: { author: { role: 'user' }, content: { parts: [`synthetic schema${oldSchema} ChatGPT`] } } } } }]
    : [{ uuid: 'upgrade-claude', name: 'Upgrade Claude', created_at: '2026-08-15T08:00:00Z', updated_at: '2026-08-15T08:01:00Z', chat_messages: [{ uuid: 'upgrade-message', sender: 'human', created_at: '2026-08-15T08:00:00Z', text: 'synthetic schema48 Claude' }] }];
  await writeFile(jsonPath, JSON.stringify(payload), 'utf8');
  const itemId = `${provider}-item-0`;
  const first = await invoke(port, 'CHAT', `stage_${provider}_export_selected`, { selectedPath: jsonPath, workspaceId });
  const firstConversationId = await invoke(port, 'CHAT', `confirm_${provider}_import_item`, { args: { workspaceId, taskId: first.id, itemId } });
  const secondExchange = join(fixtureDir, 'second-workspace.nfai-exchange');
  await copyFile(resolve(desktopRoot, '../protocol/artifacts/nfai.exchange.v1.golden.nfai-exchange'), secondExchange);
  const preflight = await invoke(port, 'CHAT', 'stage_preflight_selected_exchange', { selectedPath: secondExchange });
  const imported = await invoke(port, 'CHAT', 'import_staged_exchange_as_new_workspace', { args: { stagingId: preflight.stagingId, workspaceTitle: '升级第二合成工作区' } });
  const secondWorkspaceId = imported.summary.id;
  let second = null;
  let secondConversationId = null;
  if (oldSchema >= 47) {
    second = await invoke(port, 'CHAT', `stage_${provider}_export_selected`, { selectedPath: jsonPath, workspaceId: secondWorkspaceId });
    secondConversationId = await invoke(port, 'CHAT', `confirm_${provider}_import_item`, { args: { workspaceId: secondWorkspaceId, taskId: second.id, itemId } });
    assert.notEqual(firstConversationId, secondConversationId);
  }
  stop();
  const oldChat = inspectDatabase(join(root, 'workspace.sqlite3'));
  const oldWork = inspectDatabase(join(root, 'work-area', 'workspace.sqlite3'));
  assert.equal(oldChat.schema, oldSchema); assert.equal(oldWork.schema, oldSchema);
  assert.equal(oldChat.integrity, 'ok'); assert.equal(oldWork.integrity, 'ok');
  assert.equal(oldChat.tasks, oldSchema >= 47 ? 2 : 1); assert.equal(oldWork.tasks, 0);
  assert.equal(oldChat.receipts, oldSchema >= 47 ? 2 : 1); assert.equal(oldWork.receipts, 0);
  assert.equal(oldChat.privateHashesMatch, true);
  port = await start(1, true);
  await until(() => evaluate(port, 'Boolean(window.__TAURI__?.core?.invoke)'), 'new native IPC');
  const firstCold = await invoke(port, 'CHAT', 'read_desktop_workspace', { workspaceId });
  const secondCold = await invoke(port, 'CHAT', 'read_desktop_workspace', { workspaceId: secondWorkspaceId });
  assert.equal(firstCold.exchange.conversations.filter(item => item.id === firstConversationId && !item.deleted).length, 1);
  if (oldSchema >= 47) assert.equal(secondCold.exchange.conversations.filter(item => item.id === secondConversationId && !item.deleted).length, 1);
  assert.equal((await invoke(port, 'CHAT', `read_latest_${provider}_import_task`, { workspaceId })).items[0].conversationId, firstConversationId);
  if (oldSchema >= 47) assert.equal((await invoke(port, 'CHAT', `read_latest_${provider}_import_task`, { workspaceId: secondWorkspaceId })).items[0].conversationId, secondConversationId);
  else assert.equal(await invoke(port, 'CHAT', `read_latest_${provider}_import_task`, { workspaceId: secondWorkspaceId }), null);
  const replay = await invoke(port, 'CHAT', `stage_${provider}_export_selected`, { selectedPath: jsonPath, workspaceId });
  assert.equal(await invoke(port, 'CHAT', `confirm_${provider}_import_item`, { args: { workspaceId, taskId: replay.id, itemId } }), firstConversationId);
  assert.equal((await invoke(port, 'CHAT', 'read_desktop_workspace', { workspaceId })).exchange.conversations.filter(item => item.id === firstConversationId).length, 1);
  if (oldSchema === 46) {
    second = await invoke(port, 'CHAT', `stage_${provider}_export_selected`, { selectedPath: jsonPath, workspaceId: secondWorkspaceId });
    secondConversationId = await invoke(port, 'CHAT', `confirm_${provider}_import_item`, { args: { workspaceId: secondWorkspaceId, taskId: second.id, itemId } });
    assert.notEqual(secondConversationId, firstConversationId);
    assert.equal((await invoke(port, 'CHAT', 'read_desktop_workspace', { workspaceId: secondWorkspaceId })).exchange.conversations.filter(item => item.id === secondConversationId).length, 1);
  }
  stop();
  const newChat = inspectDatabase(join(root, 'workspace.sqlite3'));
  const newWork = inspectDatabase(join(root, 'work-area', 'workspace.sqlite3'));
  assert.equal(newChat.schema, currentSchema); assert.equal(newWork.schema, currentSchema);
  assert.equal(newChat.integrity, 'ok'); assert.equal(newWork.integrity, 'ok');
  assert.equal(newChat.tasks, 3); assert.equal(newWork.tasks, 0);
  assert.equal(newChat.receipts, 2); assert.equal(newWork.receipts, 0);
  assert.equal(newChat.privateHashesMatch, true);
  result = { provider, oldSchema, currentSchema, oldSha256: oldSha, currentSha256: receipt.sha256, root, oldChat, oldWork, newChat, newWork, workspaceId, secondWorkspaceId, firstTaskId: first.id, secondTaskId: second.id, firstConversationId, secondConversationId, replayTaskId: replay.id, stoppedPids };
} finally { stop(); }
if (result) console.log(JSON.stringify(result));
