import assert from 'node:assert/strict';
import { createHash } from 'node:crypto';
import { execFileSync, spawn } from 'node:child_process';
import { readFile } from 'node:fs/promises';
import http from 'node:http';
import { dirname, join, resolve, sep } from 'node:path';
import { fileURLToPath } from 'node:url';

if (process.platform !== 'win32') throw new Error('仅供 Windows 独立候选验收');
const desktopRoot = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const rootParent = resolve(process.argv.slice(2).find(value => !value.startsWith('--')) || 'D:\\CodexTemp');
const narrowWindow = process.argv.includes('--narrow-window');
const contextWindowTokens = narrowWindow ? 1024 : 4096;
const requestedOutputTokens = narrowWindow ? 256 : 1024;
const inputBudgetTokens = contextWindowTokens - requestedOutputTokens;
const receipt = JSON.parse(await readFile(join(desktopRoot, 'output/windows-acceptance/current.json'), 'utf8'));
assert.equal(receipt.identifier, 'com.nanzhufeng.ai.desktop.windowsacceptance');
const source = resolve(desktopRoot, 'output/windows-acceptance', receipt.sha256, 'NanfengAI-Windows-Acceptance.exe');
const running = resolve(desktopRoot, 'output/windows-acceptance-binaries', receipt.sha256, 'nanfeng-ai-desktop-spike.exe');
assert.equal(resolve(receipt.executable), source);
assert.equal(createHash('sha256').update(await readFile(source)).digest('hex').toUpperCase(), receipt.sha256);
const quote = value => `'${String(value).replaceAll("'", "''")}'`;
const ps = script => execFileSync('pwsh', ['-NoProfile', '-Command', script], { encoding: 'utf8', timeout: 60000 }).trim();
const listen = async server => new Promise(done => server.listen(0, '127.0.0.1', done));
const reservePort = async () => {
  const server = http.createServer();
  await listen(server);
  const port = server.address().port;
  await new Promise(done => server.close(done));
  return port;
};
const requests = [];
const provider = http.createServer((request, response) => {
  const chunks = [];
  request.on('data', chunk => chunks.push(chunk));
  request.on('end', () => {
    try {
      const body = JSON.parse(Buffer.concat(chunks).toString('utf8'));
      requests.push(body);
      response.writeHead(200, { 'content-type': 'text/event-stream; charset=utf-8' });
      response.end(`data: ${JSON.stringify({ choices: [{ delta: { content: 'W12 合成长路径回答' }, finish_reason: 'stop' }], usage: { prompt_tokens: 100, completion_tokens: 8, cost: 0.000001 } })}\n\ndata: [DONE]\n\n`);
    } catch (error) { response.writeHead(400); response.end(String(error)); }
  });
});
await listen(provider);
const providerPort = provider.address().port;
let launch = null;
let root = '';
const stoppedPids = [];
async function start(reopen = false) {
  const debugPort = await reservePort();
  const args = ['-NoProfile', '-File', join(desktopRoot, 'scripts/start-windows-acceptance.ps1'),
    '-RootParent', rootParent, '-StartupMode', 'normal', '-Visible', '-DebugPort', String(debugPort),
    '-OrdinaryChatTestEndpoint', `http://127.0.0.1:${providerPort}`];
  if (reopen) args.push('-Root', root);
  const result = await new Promise((done, fail) => {
    const child = spawn('pwsh', args, { stdio: ['ignore', 'pipe', 'pipe'] });
    let output = '';
    let error = '';
    const timer = setTimeout(() => { child.kill(); fail(new Error(`启动超时：${error}`)); }, 30000);
    child.stdout.on('data', chunk => {
      output += chunk.toString();
      const line = output.split(/\r?\n/).find(value => value.trim().startsWith('{') && value.includes('"pid"'));
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
  assert.equal(resolve(result.executable), running);
  launch = result;
  root = resolve(result.root);
  assert.ok(root.startsWith(rootParent + sep));
  assert.equal(JSON.parse(await readFile(join(root, '.nanfeng-windows-acceptance.json'), 'utf8')).identifier, receipt.identifier);
  return debugPort;
}
function stop() {
  if (!launch) return;
  const pid = Number(launch.pid);
  ps(`$p=Get-CimInstance Win32_Process -Filter "ProcessId=${pid}"; if ($p) { if ([IO.Path]::GetFullPath([string]$p.ExecutablePath) -ne ${quote(running)} -or (Get-FileHash -LiteralPath ${quote(running)} -Algorithm SHA256).Hash -ne ${quote(receipt.sha256)}) { throw 'PID identity mismatch' }; Stop-Process -Id ${pid} -Force -ErrorAction Stop }`);
  stoppedPids.push(pid);
  launch = null;
}
async function cdp(port, method, params = {}) {
  const tabs = await fetch(`http://127.0.0.1:${port}/json/list`).then(response => response.json());
  const tab = tabs.find(item => item.type === 'page' && item.url === 'http://tauri.localhost/');
  assert.ok(tab, 'WebView2 页面未就绪');
  const socket = new WebSocket(tab.webSocketDebuggerUrl);
  try {
    await new Promise((done, fail) => { socket.addEventListener('open', done, { once: true }); socket.addEventListener('error', fail, { once: true }); });
    return await new Promise((done, fail) => {
      const timer = setTimeout(() => fail(new Error(`${method} 超时`)), 30000);
      socket.addEventListener('message', event => {
        const reply = JSON.parse(event.data);
        if (reply.id !== 1) return;
        clearTimeout(timer);
        if (reply.error || reply.result?.exceptionDetails) fail(new Error(reply.result?.exceptionDetails?.text || reply.error?.message));
        else done(reply.result?.result?.value);
      });
      socket.send(JSON.stringify({ id: 1, method, params }));
    });
  } finally { socket.close(); }
}
const evaluate = (port, expression) => cdp(port, 'Runtime.evaluate', { expression, awaitPromise: true, returnByValue: true });
async function invoke(port, command, args = {}) {
  const value = await evaluate(port, `window.__TAURI__.core.invoke(${JSON.stringify(command)},${JSON.stringify(args)},${JSON.stringify({ headers: { 'x-nanfeng-data-area': 'CHAT' } })}).catch(error=>({__error:String(error)}))`);
  if (value?.__error) throw new Error(`${command}: ${value.__error}`);
  return value;
}
async function until(read, label, timeout = 30000) {
  const deadline = Date.now() + timeout;
  while (Date.now() < deadline) {
    try { const value = await read(); if (value) return value; } catch { /* WebView reload. */ }
    await new Promise(done => setTimeout(done, 150));
  }
  throw new Error(`${label} 未出现`);
}
const click = (port, selector) => evaluate(port, `(()=>{const node=document.querySelector(${JSON.stringify(selector)});if(!node)return false;node.click();return true})()`);
function estimate(text) {
  let units = 0; let run = 0;
  for (const char of text) {
    if (char.charCodeAt(0) <= 127 && !/\s/.test(char)) run++;
    else { units += Math.ceil(run / 4); run = 0; if (!/\s/.test(char)) units++; }
  }
  return units + Math.ceil(run / 4);
}
let result;
try {
  let port = await start();
  await until(() => evaluate(port, 'Boolean(window.__TAURI__?.core?.invoke)'), '原生 IPC');
  const workspaceId = (await invoke(port, 'ensure_desktop_local_workspace')).summary.id;
  const catalog = await invoke(port, 'read_desktop_p6g_catalog');
  await invoke(port, 'upsert_desktop_p6g_catalog_candidate', { args: {
    expectedRevision: catalog.revision, catalogVersion: 'w12-synthetic-window-v1', policyVersion: 1,
    candidate: { providerFamily: 'OPENAI', providerId: 'OPENROUTER', modelId: 'GPT_5_6_TERRA',
      displayName: 'GPT-5.6 Terra', tiers: ['BALANCED'], capabilities: ['TEXT'], available: true,
      knownCostMicros: null, latencyRank: 1, contextWindowTokens, maxOutputTokens: requestedOutputTokens },
  } });
  const savedCatalog = await invoke(port, 'read_desktop_p6g_catalog');
  assert.equal(savedCatalog.snapshot.candidates.find(item => item.modelId === 'GPT_5_6_TERRA')?.contextWindowTokens, contextWindowTokens);
  assert.equal(savedCatalog.snapshot.candidates.find(item => item.modelId === 'GPT_5_6_TERRA')?.maxOutputTokens, requestedOutputTokens);
  const oldText = index => `W12_OLD_${String(index).padStart(2, '0')} ${'x'.repeat(1200)}`;
  let receiptRow = await invoke(port, 'mutate_desktop_domain', { args: {
    intentId: 'w12-long-create-00', workspaceId, entity: 'conversation', action: 'create',
    fields: { title: 'W12 合成长对话', projectId: null, firstMessage: oldText(0) },
  } });
  const conversationId = receiptRow.objectId;
  for (let index = 1; index < 23; index++) {
    receiptRow = await invoke(port, 'mutate_desktop_domain', { args: {
      intentId: `w12-long-append-${String(index).padStart(2, '0')}`, workspaceId,
      entity: 'conversation', action: 'appendMessage', objectId: conversationId,
      expectedRevision: receiptRow.revision, fields: { role: 'user', text: oldText(index) },
    } });
  }
  await cdp(port, 'Page.reload', { ignoreCache: true });
  await until(() => evaluate(port, `Boolean(document.querySelector('[data-action="select-chat"][data-id="${conversationId}"]'))`), '合成长对话可见');
  await until(() => click(port, `[data-action="select-chat"][data-id="${conversationId}"]`), '打开合成长对话');
  await until(() => evaluate(port, `Boolean(document.querySelector('.chat-scroll[data-conversation-id="${conversationId}"]'))`), '长对话页面');
  assert.equal(await click(port, '[data-action="toggle-composer-add"]'), true);
  await until(() => evaluate(port, 'Boolean(document.querySelector("[data-action=toggle-conversation-web-search]"))'), '当前会话联网开关');
  if (await evaluate(port, 'document.querySelector("[data-action=toggle-conversation-web-search]")?.getAttribute("aria-checked")') !== 'false') {
    assert.equal(await click(port, '[data-action="toggle-conversation-web-search"]'), true);
  }
  await until(() => evaluate(port, 'document.querySelector("[data-action=toggle-conversation-web-search]")?.getAttribute("aria-checked") === "false"'), '本次合成请求关闭联网');
  assert.equal(await click(port, '[data-action="close-composer-add"]'), true);
  const latest = 'W12_LATEST_VISIBLE_REQUEST';
  await evaluate(port, `(()=>{const field=document.querySelector('#chat-composer');field.value=${JSON.stringify(latest)};field.dispatchEvent(new Event('input',{bubbles:true}));return true})()`);
  await until(() => evaluate(port, 'Boolean(document.querySelector("[data-action=save-local-message]:not([disabled])"))'), '可见发送按钮');
  assert.equal(await click(port, '[data-action="save-local-message"]'), true);
  const sent = await until(() => requests.find(body => JSON.stringify(body.messages || []).includes(latest)), 'Provider 长路径请求');
  const messages = sent.messages;
  const text = JSON.stringify(messages);
  const turns = messages.filter(item => item.role === 'user' || item.role === 'assistant');
  const estimated = messages.reduce((sum, item) => sum + estimate(typeof item.content === 'string' ? item.content : JSON.stringify(item.content)) + 8, 0);
  assert.equal(sent.max_tokens, requestedOutputTokens);
  assert.ok(estimated <= inputBudgetTokens, `请求估算 ${estimated} 超过 ${inputBudgetTokens}`);
  assert.ok(turns.length < 24 && turns.length >= (narrowWindow ? 1 : 2));
  assert.equal(turns.at(-1).content, latest);
  assert.equal(turns[0].role, 'user');
  assert.ok(!text.includes('W12_OLD_00'));
  if (!narrowWindow) assert.ok(text.includes('W12_OLD_22'));
  const completed = await until(async () => {
    const workspace = await invoke(port, 'read_desktop_workspace', { workspaceId });
    const conversation = workspace.exchange.conversations.find(item => item.id === conversationId);
    return conversation?.messages.some(item => item.role === 'assistant' && item.delivery === 'COMPLETE' && item.blocks?.some(block => block.text === 'W12 合成长路径回答')) ? conversation : null;
  }, '长路径回答落盘');
  assert.equal(completed.messages.filter(item => item.role === 'user').length, 24);
  stop();
  port = await start(true);
  await until(() => evaluate(port, 'Boolean(window.__TAURI__?.core?.invoke)'), '同根冷启 IPC');
  const cold = (await invoke(port, 'read_desktop_workspace', { workspaceId })).exchange.conversations.find(item => item.id === conversationId);
  assert.ok(cold.messages.some(item => item.role === 'assistant' && item.delivery === 'COMPLETE' && item.blocks?.some(block => block.text === 'W12 合成长路径回答')));
  result = { candidateSha256: receipt.sha256, root, workspaceId, conversationId,
    savedUserMessages: 24, sentTurns: turns.length, estimatedInputTokens: estimated,
    requestedOutputTokens: sent.max_tokens, contextWindowTokens,
    latestRetained: true, oldestExcluded: true, coldReadback: true, stoppedPids };
} finally {
  stop();
  await new Promise(done => provider.close(done));
}
console.log(JSON.stringify(result));
