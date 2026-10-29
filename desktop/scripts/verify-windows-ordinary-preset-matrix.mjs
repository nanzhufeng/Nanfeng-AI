import assert from 'node:assert/strict';
import { createHash, randomUUID } from 'node:crypto';
import { execFileSync, spawn } from 'node:child_process';
import { readFile, writeFile } from 'node:fs/promises';
import http from 'node:http';
import { dirname, join, resolve, sep } from 'node:path';
import { fileURLToPath } from 'node:url';

if (process.platform !== 'win32') throw new Error('仅在 Windows 隔离候选上验收。');
const desktopRoot = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const rootParent = resolve(process.argv[2] || 'D:\\CodexTemp');
const receipt = JSON.parse(await readFile(join(desktopRoot, 'output/windows-acceptance/current.json'), 'utf8'));
assert.equal(receipt.identifier, 'com.nanzhufeng.ai.desktop.windowsacceptance');
const source = resolve(desktopRoot, 'output/windows-acceptance', receipt.sha256, 'NanfengAI-Windows-Acceptance.exe');
assert.equal(resolve(receipt.executable), source);
assert.equal(createHash('sha256').update(await readFile(source)).digest('hex').toUpperCase(), receipt.sha256);
const runExecutable = resolve(desktopRoot, 'output/windows-acceptance-binaries', receipt.sha256, 'nanfeng-ai-desktop-spike.exe');
const ps = (args) => execFileSync('pwsh', ['-NoProfile', ...args], { encoding: 'utf8', timeout: 60000 }).trim();
const launchPowerShell = (args) => new Promise((resolveLaunch, reject) => {
  const child = spawn('pwsh', ['-NoProfile', ...args], { stdio: ['ignore', 'pipe', 'pipe'] });
  let stdout = '';
  let stderr = '';
  const timer = setTimeout(() => { child.kill(); reject(new Error(`隔离候选启动超时：${stderr}`)); }, 30000);
  child.stdout.on('data', data => {
    stdout += data.toString();
    const line = stdout.split(/\r?\n/).find(item => item.trim().startsWith('{') && item.includes('"pid"'));
    if (!line) return;
    clearTimeout(timer);
    try { resolveLaunch(JSON.parse(line)); } catch (error) { reject(error); }
    child.stdout.destroy();
    child.stderr.destroy();
    child.unref();
  });
  child.stderr.on('data', data => { stderr += data.toString(); });
  child.on('error', error => { clearTimeout(timer); reject(error); });
  child.on('exit', code => {
    if (!stdout.includes('"pid"')) { clearTimeout(timer); reject(new Error(`隔离候选启动失败 ${code}：${stderr}`)); }
  });
});
const reservePort = async () => {
  const server = http.createServer();
  await new Promise(resolveReady => server.listen(0, '127.0.0.1', resolveReady));
  const port = server.address().port;
  await new Promise(resolveClose => server.close(resolveClose));
  return port;
};
const requests = [];
const provider = http.createServer((request, response) => {
  const chunks = [];
  request.on('data', chunk => chunks.push(chunk));
  request.on('end', () => {
    try {
      const body = JSON.parse(Buffer.concat(chunks).toString('utf8'));
      const index = requests.length + 1;
      requests.push({ model: body.model, method: request.method, path: request.url, body,
        userTextPresent: JSON.stringify(body.messages).includes('W06 全预设合成问题'),
        idempotencyKey: request.headers['idempotency-key'] || '' });
      response.writeHead(200, { 'content-type': 'text/event-stream; charset=utf-8', 'cache-control': 'no-cache' });
      response.write(`data: ${JSON.stringify({ model: body.model, choices: [{ delta: { content: `合成回答 ${index}` } }] })}\n\n`);
      response.write(`data: ${JSON.stringify({ choices: [{ delta: {}, finish_reason: 'stop' }], usage: { prompt_tokens: 8, completion_tokens: 4, cost: 0.000004 } })}\n\n`);
      response.end('data: [DONE]\n\n');
    } catch (error) { response.writeHead(400); response.end(String(error)); }
  });
});
await new Promise(resolveReady => provider.listen(0, '127.0.0.1', resolveReady));
const providerPort = provider.address().port;
let launch = null;
let root = '';
const pids = [];
const stop = () => {
  if (!launch) return;
  const pid = Number(launch.pid);
  const literal = value => `'${String(value).replaceAll("'", "''")}'`;
  const script = '$expected=' + literal(runExecutable) + '; $hash=' + literal(receipt.sha256) + '; '
    + '$p=Get-CimInstance Win32_Process -Filter "ProcessId=' + pid + '"; '
    + 'if ($p) { if ([IO.Path]::GetFullPath([string]$p.ExecutablePath) -ne $expected '
    + '-or (Get-FileHash -LiteralPath $expected -Algorithm SHA256).Hash -ne $hash) { throw "PID identity mismatch" }; '
    + 'Stop-Process -Id ' + pid + ' -Force -ErrorAction Stop }';
  ps(['-Command', script]);
  pids.push(pid);
  launch = null;
};
async function start(reopen = false) {
  const port = await reservePort();
  const args = ['-File', join(desktopRoot, 'scripts/start-windows-acceptance.ps1'), '-RootParent', rootParent,
    '-StartupMode', 'normal', '-Visible', '-DebugPort', String(port), '-OrdinaryChatTestEndpoint', `http://127.0.0.1:${providerPort}`];
  if (reopen) args.push('-Root', root);
  launch = await launchPowerShell(args);
  assert.equal(launch.sha256, receipt.sha256);
  assert.equal(resolve(launch.executable), runExecutable);
  root = resolve(launch.root);
  assert.ok(root.startsWith(rootParent + sep));
  const marker = JSON.parse(await readFile(join(root, '.nanfeng-windows-acceptance.json'), 'utf8'));
  assert.equal(marker.identifier, receipt.identifier);
  return port;
}
async function cdp(port, method, params = {}) {
  const tabs = await fetch(`http://127.0.0.1:${port}/json/list`).then(response => response.json());
  const tab = tabs.find(item => item.type === 'page' && item.url === 'http://tauri.localhost/');
  assert.ok(tab, '验收 WebView2 页面未就绪');
  const socket = new WebSocket(tab.webSocketDebuggerUrl);
  try {
    await new Promise((resolveReady, reject) => {
      socket.addEventListener('open', resolveReady, { once: true });
      socket.addEventListener('error', reject, { once: true });
    });
    return await new Promise((resolveResult, reject) => {
      const timer = setTimeout(() => reject(new Error('CDP evaluation timed out')), 30000);
      socket.addEventListener('message', event => {
        const message = JSON.parse(event.data);
        if (message.id !== 1) return;
        clearTimeout(timer);
        if (message.error || message.result?.exceptionDetails) reject(new Error(
          message.result?.exceptionDetails?.exception?.description || message.result?.exceptionDetails?.text || message.error?.message));
        else resolveResult(message.result);
      });
      socket.send(JSON.stringify({ id: 1, method, params }));
    });
  } finally { socket.close(); }
}
async function evaluate(port, expression) {
  return (await cdp(port, 'Runtime.evaluate', { expression, awaitPromise: true, returnByValue: true }))?.result?.value;
}
async function invoke(port, area, command, args = {}) {
  const expression = `window.__TAURI__.core.invoke(${JSON.stringify(command)},${JSON.stringify(args)},${JSON.stringify({ headers: { 'x-nanfeng-data-area': area } })}).catch(error=>({__error:String(error)}))`;
  const result = await evaluate(port, expression);
  if (result?.__error) throw new Error(`${command}: ${result.__error}`);
  return result;
}
async function ready(port) {
  const deadline = Date.now() + 30000;
  while (Date.now() < deadline) {
    try { if (await evaluate(port, 'Boolean(window.__TAURI__?.core?.invoke)')) return; } catch { /* Startup is still active. */ }
    await new Promise(resolveWait => setTimeout(resolveWait, 250));
  }
  throw new Error('原生 IPC 未就绪');
}
async function until(check, label) {
  const deadline = Date.now() + 30000;
  while (Date.now() < deadline) {
    try { const value = await check(); if (value) return value; } catch { /* Visible page may be refreshing. */ }
    await new Promise(resolveWait => setTimeout(resolveWait, 200));
  }
  throw new Error(`${label} 未在原生界面出现`);
}
async function clickUi(port, selector) {
  const result = await evaluate(port, `(()=>{const node=document.querySelector(${JSON.stringify(selector)});if(!node)return false;node.click();return true})()`);
  assert.equal(result, true, `找不到可点击控件 ${selector}`);
}
const auth = () => ({ approvedAtMs: Date.now(), disclosureVersion: 'normal-chat-egress-v1', clientSubmissionId: randomUUID() });
const currentConversation = async (port, workspaceId, conversationId) => {
  const projection = await invoke(port, 'CHAT', 'read_desktop_workspace', { workspaceId });
  return projection.exchange.conversations.find(item => item.id === conversationId);
};

// Fixed Mac dafc64d9 chat catalog. Keep expectations independent of the Windows projection.
const presets = [
  ['CLAUDE_FABLE_5_1', 'OPENROUTER', 'anthropic/claude-fable-5.1-20260831'],
  ['GPT_6_ASTRA', 'OPENROUTER', 'openai/gpt-6-astra'],
  ['CLAUDE_FABLE_5', 'OPENROUTER', 'anthropic/claude-fable-5'],
  ['CLAUDE_OPUS_5', 'OPENROUTER', 'anthropic/claude-opus-5'],
  ['CLAUDE_SONNET_5', 'OPENROUTER', 'anthropic/claude-sonnet-5'],
  ['CLAUDE_HAIKU_4_5', 'OPENROUTER', 'anthropic/claude-haiku-4.5'],
  ['GPT_5_6_SOL', 'OPENROUTER', 'openai/gpt-5.6-sol'],
  ['GPT_5_6_TERRA', 'OPENROUTER', 'openai/gpt-5.6-terra'],
  ['GPT_5_6_LUNA', 'OPENROUTER', 'openai/gpt-5.6-luna'],
  ['GEMINI_3_8_FLASH', 'OPENROUTER', 'google/gemini-3.8-flash'],
  ['KIMI_K3', 'OPENROUTER', 'moonshotai/kimi-k3'],
  ['DEEPSEEK_V4_PRO', 'DEEPSEEK', 'deepseek-v4-pro'],
  ['DEEPSEEK_V4_FLASH', 'DEEPSEEK', 'deepseek-flash'],
  ['GLM_5_3', 'ZHIPU', 'glm-5.3'],
  ['GLM_5_3_FLASH', 'ZHIPU', 'glm-5.3-flash'],
  ['QWEN_3_7_PLUS', 'QWEN', 'qwen3.7-plus'],
  ['QWEN_3_8_MAX', 'QWEN', 'qwen3.8-max'],
];
function assertRequest(request, preset) {
  const [presetId, providerId, modelId] = preset;
  assert.equal(request.model, modelId, presetId);
  assert.equal(request.method, 'POST');
  assert.equal(request.path, '/', '验收端点覆盖把请求送到本机回环根路径');
  assert.equal(request.userTextPresent, true);
  assert.equal(request.body.stream, true);
  assert.equal(request.body.stream_options?.include_usage, true);
  assert.ok(request.idempotencyKey);
  assert.ok(!request.body.tools && !request.body.enable_search, '关闭联网后不得附加搜索工具');
  if (presetId === 'QWEN_3_8_MAX') {
    assert.equal(request.body.max_completion_tokens, 16_384);
    assert.equal(request.body.max_tokens, undefined);
    assert.equal(request.body.reasoning_effort, 'low');
    assert.equal(request.body.preserve_thinking, false);
  } else {
    const bundledOutputLimits = {
      DEEPSEEK_V4_PRO: 384000,
      DEEPSEEK_V4_FLASH: 384000,
      GLM_5_3: 131072,
      GLM_5_3_FLASH: 128000,
      QWEN_3_7_PLUS: 65536,
    };
    assert.equal(request.body.max_tokens, bundledOutputLimits[presetId] ?? 8192);
  }
  if (providerId === 'ZHIPU') {
    assert.equal(request.body.thinking?.type, 'enabled');
    assert.equal(request.body.reasoning_effort, 'max');
  }
  if (providerId === 'OPENROUTER') {
    const high = ['CLAUDE_FABLE_5_1', 'CLAUDE_OPUS_5', 'GPT_6_ASTRA', 'GPT_5_6_SOL'].includes(presetId);
    assert.equal(request.body.reasoning?.effort, high ? 'high' : undefined);
  }
}
let result;
try {
  let port = await start();
  await ready(port);
  const workspaceId = (await invoke(port, 'CHAT', 'ensure_desktop_local_workspace')).summary.id;
  const settings = await invoke(port, 'CHAT', 'read_desktop_model_service_settings');
  for (const [providerId, presetId] of [
    ['OPENROUTER', 'GPT_5_6_TERRA'], ['DEEPSEEK', 'DEEPSEEK_V4_PRO'],
    ['ZHIPU', 'GLM_5_3_FLASH'], ['QWEN', 'QWEN_3_7_PLUS'],
  ]) {
    const setting = settings.find(item => item.providerId === providerId);
    assert.ok(setting, `缺少 ${providerId} 设置`);
    await invoke(port, 'CHAT', 'save_desktop_model_service_settings', { args: {
      providerId, enabled: true, presetId, expectedRevision: setting.revision,
      apiKey: `nfai-synthetic-matrix-${providerId.toLowerCase()}-${randomUUID()}`,
    } });
  }
  const tag = randomUUID();
  const created = await invoke(port, 'CHAT', 'mutate_desktop_domain', { args: {
    intentId: `preset-matrix-create-${tag}`, workspaceId, entity: 'conversation', action: 'create',
    objectId: null, expectedRevision: null, fields: { title: '合成全预设', projectId: null, firstMessage: 'W06 全预设合成问题' },
  } });
  const conversationId = created.objectId;
  let conversation = await currentConversation(port, workspaceId, conversationId);
  const userMessageId = conversation.currentLeafId;
  const selection = await invoke(port, 'CHAT', 'read_desktop_p6g_selection', { workspaceId, conversationId });
  await invoke(port, 'CHAT', 'set_desktop_conversation_preferences', { args: {
    workspaceId, conversationId, expectedRevision: selection.conversationOverride.revision,
    toneOverride: null, webSearchOverride: false,
  } });
  await invoke(port, 'CHAT', 'generate_desktop_ordinary_chat_for_saved_user', { args: {
    workspaceId, conversationId, userMessageId, expectedRevision: conversation.revision, egressAuthorization: auth(),
  } });
  conversation = await currentConversation(port, workspaceId, conversationId);
  assert.equal(conversation.messages.find(item => item.id === conversation.currentLeafId)?.delivery, 'COMPLETE');
  assert.equal(requests.length, 1);
  const initialPreset = presets.find(item => item[2] === requests[0].model);
  assert.ok(initialPreset, '初始自动选择不在固定 Mac 聊天目录');
  assertRequest(requests[0], initialPreset);
  const seen = [initialPreset[0]];
  for (const preset of presets.filter(item => item[0] !== initialPreset[0])) {
    const sourceMessageId = conversation.currentLeafId;
    await invoke(port, 'CHAT', 'regenerate_desktop_ordinary_chat', { args: {
      workspaceId, conversationId, assistantMessageId: sourceMessageId,
      targetPresetId: preset[0], expectedRevision: conversation.revision, egressAuthorization: auth(),
    } });
    conversation = await currentConversation(port, workspaceId, conversationId);
    const leaf = conversation.messages.find(item => item.id === conversation.currentLeafId);
    assert.equal(leaf?.delivery, 'COMPLETE', preset[0]);
    assert.equal(leaf?.parentId, userMessageId, preset[0]);
    assert.equal(requests.length, seen.length + 1, preset[0]);
    assertRequest(requests.at(-1), preset);
    seen.push(preset[0]);
  }
  assert.equal(seen.length, presets.length);
  assert.equal(new Set(requests.map(item => item.idempotencyKey)).size, presets.length);
  const ledger = await invoke(port, 'CHAT', 'read_desktop_usage_ledger');
  const charges = ledger.records.filter(item => item.conversationId === conversationId);
  assert.equal(charges.length, presets.length);
  assert.ok(charges.every(item => item.chargeMicros === 4));
  const beforeRestartRequests = requests.length;
  const finalLeafId = conversation.currentLeafId;
  stop();
  port = await start(true);
  await ready(port);
  const cold = await currentConversation(port, workspaceId, conversationId);
  assert.equal(cold.currentLeafId, finalLeafId);
  assert.equal(requests.length, beforeRestartRequests);
  assert.equal((await invoke(port, 'CHAT', 'read_desktop_usage_ledger')).records.filter(item => item.conversationId === conversationId).length, presets.length);
  result = { candidateSha256: receipt.sha256, root, workspaceId, conversationId,
    presetCount: presets.length, presetIds: seen, requestCount: requests.length, chargeCount: charges.length,
    chargedMicros: charges.reduce((sum, item) => sum + item.chargeMicros, 0), finalLeafId, pids };
} finally {
  stop();
  await new Promise(resolveClose => provider.close(resolveClose));
}
console.log(JSON.stringify(result));
