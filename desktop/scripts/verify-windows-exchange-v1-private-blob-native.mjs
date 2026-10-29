import assert from 'node:assert/strict';
import { createHash, randomUUID } from 'node:crypto';
import { execFileSync, spawn } from 'node:child_process';
import { copyFile, readFile, unlink, writeFile } from 'node:fs/promises';
import http from 'node:http';
import { dirname, join, resolve, sep } from 'node:path';
import { fileURLToPath } from 'node:url';
import { packageExchange, preflightPackage, readZip, semanticHash } from '../../protocol/scripts/exchange-lib.mjs';

if (process.platform !== 'win32') throw new Error('仅供 Windows 隔离候选验收');
const desktopRoot = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const rootParent = resolve(process.argv[2] || 'D:\\CodexTemp');
const receipt = JSON.parse(await readFile(join(desktopRoot, 'output/windows-acceptance/current.json'), 'utf8'));
assert.equal(receipt.identifier, 'com.nanzhufeng.ai.desktop.windowsacceptance');
const sourceExe = resolve(desktopRoot, 'output/windows-acceptance', receipt.sha256, 'NanfengAI-Windows-Acceptance.exe');
const runningExe = resolve(desktopRoot, 'output/windows-acceptance-binaries', receipt.sha256, 'nanfeng-ai-desktop-spike.exe');
assert.equal(resolve(receipt.executable), sourceExe);
assert.equal(createHash('sha256').update(await readFile(sourceExe)).digest('hex').toUpperCase(), receipt.sha256);
const quote = value => `'${String(value).replaceAll("'", "''")}'`;
const ps = script => execFileSync('pwsh', ['-NoProfile', '-Command', script], { encoding: 'utf8', timeout: 60000 }).trim();
const continuationPrompt = 'W15 合成导入后续写';
const continuationAnswer = 'W15 合成续写完成';
const providerRequests = [];
const provider = http.createServer((request, response) => {
  const chunks = [];
  request.on('data', chunk => chunks.push(chunk));
  request.on('end', () => {
    try {
      const body = JSON.parse(Buffer.concat(chunks).toString('utf8'));
      providerRequests.push(body);
      response.writeHead(200, { 'content-type': 'text/event-stream; charset=utf-8' });
      response.end(`data: ${JSON.stringify({ model: body.model, choices: [{ delta: { content: continuationAnswer }, finish_reason: 'stop' }], usage: { prompt_tokens: 11, completion_tokens: 7, cost: 0.000012 } })}\n\ndata: [DONE]\n\n`);
    } catch (error) { response.writeHead(400); response.end(String(error)); }
  });
});
await new Promise(done => provider.listen(0, '127.0.0.1', done));
const providerPort = provider.address().port;
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
  const args = ['-RootParent', rootParent, '-StartupMode', 'normal', '-Visible', '-DebugPort', String(port),
    '-OrdinaryChatTestEndpoint', `http://127.0.0.1:${providerPort}`];
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
  assert.equal(result.sha256, receipt.sha256);
  assert.equal(resolve(result.executable), runningExe);
  launch = result;
  root = resolve(result.root);
  assert.ok(root.startsWith(rootParent + sep));
  assert.equal(JSON.parse(await readFile(join(root, '.nanfeng-windows-acceptance.json'), 'utf8')).identifier, receipt.identifier);
  return port;
}
function stop() {
  if (!launch) return;
  const pid = Number(launch.pid);
  ps(`$p=Get-CimInstance Win32_Process -Filter "ProcessId=${pid}"; if ($p) { if ([IO.Path]::GetFullPath([string]$p.ExecutablePath) -ne ${quote(runningExe)} -or (Get-FileHash -LiteralPath ${quote(runningExe)} -Algorithm SHA256).Hash -ne ${quote(receipt.sha256)}) { throw 'PID identity mismatch' }; Stop-Process -Id ${pid} -Force -ErrorAction Stop }`);
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
const invoke = (port, command, args = {}) => evaluate(port,
  `window.__TAURI__.core.invoke(${JSON.stringify(command)},${JSON.stringify(args)},${JSON.stringify({ headers: { 'x-nanfeng-data-area': 'CHAT' } })}).catch(error=>({__error:String(error)}))`);
async function until(read) {
  const deadline = Date.now() + 30000;
  while (Date.now() < deadline) {
    try { if (await read()) return; } catch { /* Startup. */ }
    await new Promise(done => setTimeout(done, 200));
  }
  throw new Error('native IPC 未就绪');
}

let result;
try {
  let port = await start();
  await until(() => evaluate(port, 'Boolean(window.__TAURI__?.core?.invoke)'));
  const local = await invoke(port, 'ensure_desktop_local_workspace');
  assert.ok(local.summary?.id);
  const baselineWorkspaces = await invoke(port, 'list_desktop_workspaces');
  const input = join(root, 'synthetic-v1-exchange.nfai-exchange');
  const golden = JSON.parse(await readFile(resolve(desktopRoot, '../protocol/fixtures/nfai.exchange.v1.golden.json'), 'utf8'));
  const secondConversation = structuredClone(golden.conversations[0]);
  secondConversation.id = 'conversation-p6a-02';
  secondConversation.title = '共享附件的第二会话';
  secondConversation.currentLeafId = 'message-p6a-04';
  secondConversation.messages[0].id = 'message-p6a-03';
  secondConversation.messages[0].blocks[0].text = '第二条合成会话。';
  secondConversation.messages[1].id = 'message-p6a-04';
  secondConversation.messages[1].parentId = 'message-p6a-03';
  golden.conversations.push(secondConversation);
  golden.export.id = 'export-w15-synthetic-continuation';
  golden.export.semanticHash = semanticHash(golden);
  const originalEntries = readZip(await readFile(resolve(desktopRoot, '../protocol/artifacts/nfai.exchange.v1.golden.nfai-exchange')));
  const inputBytes = packageExchange(golden, new Map([...originalEntries].filter(([name]) => name.startsWith('assets/'))));
  preflightPackage(inputBytes);
  const invalidExchange = structuredClone(golden);
  invalidExchange.conversations[0].messages[1].chargeMicros = 12;
  invalidExchange.export.semanticHash = semanticHash(invalidExchange);
  const invalidPath = join(root, 'synthetic-invalid-runtime-field.nfai-exchange');
  await writeFile(invalidPath, packageExchange(invalidExchange, new Map([...originalEntries].filter(([name]) => name.startsWith('assets/')))));
  const invalidPreflight = await invoke(port, 'stage_preflight_selected_exchange', { selectedPath: invalidPath });
  assert.match(invalidPreflight.__error || '', /v1 schema 之外/, JSON.stringify(invalidPreflight));
  const invalidRole = structuredClone(golden);
  invalidRole.conversations[0].messages[1].role = 'intruder';
  invalidRole.export.semanticHash = semanticHash(invalidRole);
  const invalidRolePath = join(root, 'synthetic-invalid-role.nfai-exchange');
  await writeFile(invalidRolePath, packageExchange(invalidRole, new Map([...originalEntries].filter(([name]) => name.startsWith('assets/')))));
  const invalidRolePreflight = await invoke(port, 'stage_preflight_selected_exchange', { selectedPath: invalidRolePath });
  assert.match(invalidRolePreflight.__error || '', /v1 schema/, JSON.stringify(invalidRolePreflight));
  await writeFile(input, inputBytes);
  const assetHash = golden.conversations[0].messages[1].blocks[0].asset.sha256;
  const staged = await invoke(port, 'stage_preflight_selected_exchange', { selectedPath: input });
  assert.ok(staged.stagingId, staged.__error || JSON.stringify(staged));
  assert.ok(!baselineWorkspaces.some(item => item.packageHash === staged.packageHash));
  const corrupt = Buffer.from('synthetic corrupt private asset');
  await writeFile(join(root, 'assets', assetHash), corrupt);
  const rejected = await invoke(port, 'import_staged_exchange_as_new_workspace', { args: { stagingId: staged.stagingId, workspaceTitle: '合成导入拒绝' } });
  assert.match(rejected.__error || '', /asset 私有副本无效/);
  assert.deepEqual((await invoke(port, 'list_desktop_workspaces')).map(item => item.id), baselineWorkspaces.map(item => item.id));
  assert.deepEqual(await readFile(join(root, 'assets', assetHash)), corrupt);
  await unlink(join(root, 'assets', assetHash));
  const imported = await invoke(port, 'import_staged_exchange_as_new_workspace', {
    args: { stagingId: staged.stagingId, workspaceTitle: '合成交换工作区' },
  });
  assert.ok(imported.summary?.id && !imported.__error, imported.__error);
  for (const field of ['projects', 'knowledge', 'memory', 'relations', 'settings']) {
    assert.deepEqual(imported.exchange[field], golden[field], `导入后 ${field} 丢失或变形`);
  }
  assert.deepEqual(imported.exchange.conversations, golden.conversations, '导入后会话及附件字段丢失或变形');
  assert.equal(createHash('sha256').update(await readFile(join(root, 'assets', assetHash))).digest('hex'), assetHash);
  const importedConversation = imported.exchange.conversations[0];
  assert.deepEqual(imported.exchange.conversations[1], secondConversation, '第二会话及共享附件导入不一致');
  const selection = await invoke(port, 'read_desktop_p6g_selection', {
    workspaceId: imported.summary.id, conversationId: importedConversation.id,
  });
  const disabledSearch = await invoke(port, 'set_desktop_conversation_preferences', { args: {
    workspaceId: imported.summary.id, conversationId: importedConversation.id,
    expectedRevision: selection.conversationOverride.revision,
    toneOverride: null, webSearchOverride: false,
  } });
  assert.ok(!disabledSearch.__error, disabledSearch.__error);
  const sent = await invoke(port, 'submit_desktop_ordinary_chat', { args: {
    workspaceId: imported.summary.id, conversationId: importedConversation.id, projectId: null,
    expectedRevision: importedConversation.revision, text: continuationPrompt, attachmentIds: [],
    modelId: null, toneOverride: null, webSearchOverride: null,
    egressAuthorization: { approvedAtMs: Date.now(), disclosureVersion: 'normal-chat-egress-v1', clientSubmissionId: randomUUID() },
  } });
  assert.ok(!sent.__error, sent.__error);
  assert.equal(sent.state, 'COMPLETED', JSON.stringify(sent));
  assert.equal(providerRequests.length, 1);
  assert.ok(JSON.stringify(providerRequests[0].messages).includes(continuationPrompt));
  const continued = await invoke(port, 'read_desktop_workspace', { workspaceId: imported.summary.id });
  const continuedConversation = continued.exchange.conversations.find(item => item.id === importedConversation.id);
  assert.ok(continuedConversation.messages.length >= importedConversation.messages.length + 2);
  for (const original of importedConversation.messages) {
    assert.deepEqual(continuedConversation.messages.find(item => item.id === original.id), original);
  }
  assert.ok(continuedConversation.messages.some(item => item.blocks?.some(block => block.text?.includes(continuationAnswer))));
  const measuredReply = continuedConversation.messages.find(item => item.blocks?.some(block => block.text?.includes(continuationAnswer)));
  assert.equal(measuredReply.usage?.inputTokens, 11);
  assert.equal(measuredReply.usage?.outputTokens, 7);
  assert.equal(measuredReply.chargeMicros, 12);
  const output = join(root, 'synthetic-v1-roundtrip.nfai-exchange');
  const exported = await invoke(port, 'export_desktop_workspace_to_selected_path', {
    workspaceId: imported.summary.id, selectedPath: output,
  });
  assert.ok(exported.semanticHash && !exported.__error);
  assert.notEqual(exported.semanticHash, staged.semanticHash);
  const roundtrip = await invoke(port, 'stage_preflight_selected_exchange', { selectedPath: output });
  assert.equal(roundtrip.semanticHash, exported.semanticHash);
  const roundtripExchange = preflightPackage(await readFile(output)).exchange;
  const roundtripEntries = readZip(await readFile(output));
  assert.equal(createHash('sha256').update(roundtripEntries.get(`assets/${assetHash}`)).digest('hex'), assetHash,
    '再导出附件字节不一致');
  for (const field of ['projects', 'knowledge', 'memory', 'relations', 'settings']) {
    assert.deepEqual(roundtripExchange[field], golden[field], `再导出 ${field} 丢失或变形`);
  }
  const exportedConversation = roundtripExchange.conversations.find(item => item.id === importedConversation.id);
  assert.deepEqual(roundtripExchange.conversations.find(item => item.id === secondConversation.id), secondConversation,
    '第二会话及共享附件再导出不一致');
  assert.equal(exportedConversation.messages.length, continuedConversation.messages.length);
  for (const original of importedConversation.messages) {
    assert.deepEqual(exportedConversation.messages.find(item => item.id === original.id), original);
  }
  assert.ok(exportedConversation.messages.some(item => item.blocks?.some(block => block.text?.includes(continuationAnswer))));
  for (const key of ['chargeMicros', 'modelUsage', 'modelSnapshot', 'usage', 'estimatedUsage']) {
    assert.ok(!Object.hasOwn(exportedConversation.messages.find(item => item.blocks?.some(block => block.text?.includes(continuationAnswer))), key),
      `交换包不应包含本机运行字段 ${key}`);
  }
  for (const message of exportedConversation.messages) {
    message.blocks.forEach((block, ordinal) => assert.equal(block.ordinal, ordinal, 'v1 消息块顺序字段缺失'));
  }
  stop();
  port = await start(true);
  await until(() => evaluate(port, 'Boolean(window.__TAURI__?.core?.invoke)'));
  const afterRestart = await invoke(port, 'list_desktop_workspaces');
  assert.equal(afterRestart.length, baselineWorkspaces.length + 1);
  assert.ok(afterRestart.some(item => item.id === imported.summary.id));
  const reopened = await invoke(port, 'read_desktop_workspace', { workspaceId: imported.summary.id });
  for (const field of ['projects', 'knowledge', 'memory', 'relations', 'settings']) {
    assert.deepEqual(reopened.exchange[field], golden[field], `冷启后 ${field} 丢失或变形`);
  }
  for (const originalConversation of golden.conversations) {
    const actualConversation = reopened.exchange.conversations.find(item => item.id === originalConversation.id);
    assert.ok(actualConversation, `冷启后会话 ${originalConversation.id} 丢失`);
    for (const original of originalConversation.messages) {
      assert.deepEqual(actualConversation.messages.find(item => item.id === original.id), original,
        `冷启后消息 ${original.id} 丢失或变形`);
    }
  }
  assert.ok(reopened.exchange.conversations.find(item => item.id === importedConversation.id)
    ?.messages.some(item => item.blocks?.some(block => block.text?.includes(continuationAnswer))));
  const coldReply = reopened.exchange.conversations.find(item => item.id === importedConversation.id)
    ?.messages.find(item => item.id === measuredReply.id);
  assert.equal(coldReply?.usage?.inputTokens, 11);
  assert.equal(coldReply?.usage?.outputTokens, 7);
  assert.equal(coldReply?.chargeMicros, 12);
  assert.equal(providerRequests.length, 1);
  const firstRoot = root;
  stop();
  port = await start();
  const secondRoot = root;
  assert.notEqual(firstRoot, secondRoot);
  await until(() => evaluate(port, 'Boolean(window.__TAURI__?.core?.invoke)'));
  const secondInput = join(secondRoot, 'synthetic-v1-from-first-root.nfai-exchange');
  await copyFile(output, secondInput);
  const secondStaged = await invoke(port, 'stage_preflight_selected_exchange', { selectedPath: secondInput });
  assert.equal(secondStaged.semanticHash, exported.semanticHash, JSON.stringify(secondStaged));
  const secondImported = await invoke(port, 'import_staged_exchange_as_new_workspace', {
    args: { stagingId: secondStaged.stagingId, workspaceTitle: '第二根续写回导' },
  });
  assert.ok(secondImported.summary?.id && !secondImported.__error, JSON.stringify(secondImported));
  assert.deepEqual(secondImported.exchange.conversations, roundtripExchange.conversations,
    '第二根导入后消息树或安全字段与可移植包不同');
  assert.equal(createHash('sha256').update(await readFile(join(secondRoot, 'assets', assetHash))).digest('hex'), assetHash);
  stop();
  port = await start(true);
  await until(() => evaluate(port, 'Boolean(window.__TAURI__?.core?.invoke)'));
  const secondCold = await invoke(port, 'read_desktop_workspace', { workspaceId: secondImported.summary.id });
  for (const message of roundtripExchange.conversations[0].messages) {
    assert.deepEqual(secondCold.exchange.conversations[0].messages.find(item => item.id === message.id), message,
      `第二根冷启后消息 ${message.id} 变形`);
  }
  assert.equal(providerRequests.length, 1, '第二根导入与冷启不得重发 Provider 请求');
  result = { candidateSha256: receipt.sha256, rootA: firstRoot, rootB: secondRoot,
    baselineWorkspaceCount: baselineWorkspaces.length,
    rejected: true, importedWorkspaceId: imported.summary.id, roundtripSemanticHash: exported.semanticHash,
    continuationState: sent.state, providerRequestCount: providerRequests.length,
    coldRestartImportPreserved: true, secondRootImportedRoundtrip: true, stoppedPids };
} finally { stop(); await new Promise(done => provider.close(done)); }
if (result) console.log(JSON.stringify(result));
