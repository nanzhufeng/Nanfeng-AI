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
assert.ok(process.execArgv.includes('--max-old-space-size=2048'));
const screenshotMode = process.argv.includes('--screenshot');
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
    try { const emitted=JSON.parse(line); readFile(emitted.launchReceipt, 'utf8').then(data=>resolveLaunch(JSON.parse(data)),reject); } catch (error) { reject(error); }
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
      requests.push({ model: body.model, method: request.method, path: request.url,
        userTextPresent: JSON.stringify(body.messages).includes('合成编辑后的问题'),
        uiEditedTextPresent: JSON.stringify(body.messages).includes('合成界面编辑后的问题'),
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
  const tabs = await fetch(`http://127.0.0.1:${port}/json/list`,{signal:AbortSignal.timeout(10000)}).then(response => response.json());
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
const auth = async (port, workspaceId, conversationId, sourceMessageId, expectedRevision, operation, targetPresetId = null) => {
  const preview = await invoke(port, 'CHAT', 'preview_desktop_existing_ordinary_chat_egress', { args: {
    workspaceId, conversationId, sourceMessageId, expectedRevision, operation, targetPresetId,
  } });
  return { approvedAtMs: Date.now(), disclosureVersion: 'normal-chat-egress-v2', clientSubmissionId: randomUUID(),
    draftFingerprint: preview.draftFingerprint, recipientChoiceId: preview.recipientChoiceId,
    recipientProviderId: preview.recipientProviderId, recipientModelId: preview.recipientModelId };
};
const currentConversation = async (port, workspaceId, conversationId) => {
  const projection = await invoke(port, 'CHAT', 'read_desktop_workspace', { workspaceId });
  return projection.exchange.conversations.find(item => item.id === conversationId);
};
async function normalClose() {
  const pid=Number(launch.pid);
  const quote=value=>`'${String(value).replaceAll("'", "''")}'`;
  const info=JSON.parse(ps(['-Command',`$p=Get-CimInstance Win32_Process -Filter "ProcessId=${pid}";if(!$p -or [IO.Path]::GetFullPath([string]$p.ExecutablePath) -ne ${quote(runExecutable)} -or (Get-FileHash -LiteralPath ${quote(runExecutable)}).Hash -ne ${quote(receipt.sha256)}){throw 'PID identity mismatch'};$n=Get-Process -Id ${pid};@{title=$n.MainWindowTitle;responding=$n.Responding;accepted=$n.CloseMainWindow()}|ConvertTo-Json -Compress`]));
  assert.ok(info.title && info.responding && info.accepted);
  await until(()=>ps(['-Command',`@(Get-CimInstance Win32_Process -Filter "ProcessId=${pid}").Count`])==='0','自然关闭退出');
  await until(()=>ps(['-Command',`@(Get-CimInstance Win32_Process -Filter "Name='msedgewebview2.exe'" | Where-Object {$_.CommandLine -and $_.CommandLine.Contains(${quote(launch.webviewProfile)})}).Count`])==='0','本根WebView退出');
  pids.push(pid);launch=null;return info;
}
let result;
try {
  let port = await start();
  await ready(port);
  const workspaceId = (await invoke(port, 'CHAT', 'ensure_desktop_local_workspace')).summary.id;
  const tag = Date.now();
  const created = await invoke(port, 'CHAT', 'mutate_desktop_domain', { args: {
    intentId: `branch-create-${tag}`, workspaceId, entity: 'conversation', action: 'create',
    objectId: null, expectedRevision: null, fields: { title: '合成回答分支', projectId: null, firstMessage: '合成原问题' },
  } });
  const conversationId = created.objectId;
  let conversation = await currentConversation(port, workspaceId, conversationId);
  const originalUserId = conversation.currentLeafId;
  await invoke(port, 'CHAT', 'mutate_desktop_domain', { args: {
    intentId: `branch-edit-${tag}`, workspaceId, entity: 'conversation', action: 'editUserMessage',
    objectId: conversationId, expectedRevision: conversation.revision,
    fields: { messageId: originalUserId, text: '合成编辑后的问题' },
  } });
  conversation = await currentConversation(port, workspaceId, conversationId);
  const editedUserId = conversation.currentLeafId;
  assert.notEqual(editedUserId, originalUserId);
  const selection = await invoke(port, 'CHAT', 'read_desktop_p6g_selection', { workspaceId, conversationId });
  await invoke(port, 'CHAT', 'set_desktop_conversation_preferences', { args: {
    workspaceId, conversationId, expectedRevision: selection.conversationOverride.revision,
    toneOverride: null, webSearchOverride: false,
  } });
  const generated = await invoke(port, 'CHAT', 'generate_desktop_ordinary_chat_for_saved_user', { args: {
    workspaceId, conversationId, userMessageId: editedUserId, expectedRevision: conversation.revision,
    egressAuthorization: await auth(port, workspaceId, conversationId, editedUserId, conversation.revision, 'savedUser'),
  } });
  conversation = await currentConversation(port, workspaceId, conversationId);
  const firstId = conversation.currentLeafId;
  const firstAnswer = conversation.messages.find(item => item.id === firstId);
  assert.equal(firstAnswer.delivery, 'COMPLETE');
  assert.equal(firstAnswer.parentId, editedUserId);
  const sameModel = await invoke(port, 'CHAT', 'regenerate_desktop_ordinary_chat', { args: {
    workspaceId, conversationId, assistantMessageId: firstId, expectedRevision: conversation.revision,
    egressAuthorization: await auth(port, workspaceId, conversationId, firstId, conversation.revision, 'regenerate'),
  } });
  conversation = await currentConversation(port, workspaceId, conversationId);
  const secondId = conversation.currentLeafId;
  const secondAnswer = conversation.messages.find(item => item.id === secondId);
  assert.equal(secondAnswer.delivery, 'COMPLETE');
  assert.equal(secondAnswer.parentId, editedUserId);
  assert.notEqual(generated.attemptId, sameModel.attemptId);
  assert.equal(secondAnswer.originMessageId, firstId);
  assert.equal(secondAnswer.previousAttemptId, generated.attemptId);
  const changed = await invoke(port, 'CHAT', 'regenerate_desktop_ordinary_chat', { args: {
    workspaceId, conversationId, assistantMessageId: secondId, targetPresetId: 'DEEPSEEK_V4_PRO',
    expectedRevision: conversation.revision,
    egressAuthorization: await auth(port, workspaceId, conversationId, secondId, conversation.revision,
      'changeModel', 'DEEPSEEK_V4_PRO'),
  } });
  conversation = await currentConversation(port, workspaceId, conversationId);
  const thirdId = conversation.currentLeafId;
  const thirdAnswer = conversation.messages.find(item => item.id === thirdId);
  assert.equal(thirdAnswer.delivery, 'COMPLETE');
  assert.notEqual(changed.attemptId, sameModel.attemptId);
  assert.equal(thirdAnswer.originMessageId, secondId);
  assert.equal(thirdAnswer.previousAttemptId, sameModel.attemptId);
  assert.equal(thirdAnswer.parentId, editedUserId);
  await invoke(port, 'CHAT', 'mutate_desktop_domain', { args: {
    intentId: `branch-switch-old-${tag}`, workspaceId, entity: 'conversation', action: 'switchLeaf',
    objectId: conversationId, expectedRevision: conversation.revision, fields: { messageId: firstId },
  } });
  conversation = await currentConversation(port, workspaceId, conversationId);
  assert.equal(conversation.currentLeafId, firstId);
  await invoke(port, 'CHAT', 'mutate_desktop_domain', { args: {
    intentId: `branch-switch-new-${tag}`, workspaceId, entity: 'conversation', action: 'switchLeaf',
    objectId: conversationId, expectedRevision: conversation.revision, fields: { messageId: thirdId },
  } });
  const settings = await invoke(port, 'CHAT', 'read_desktop_model_service_settings');
  for (const [providerId, presetId] of [['OPENROUTER', 'GPT_5_6_TERRA'], ['DEEPSEEK', 'DEEPSEEK_V4_PRO']]) {
    const setting = settings.find(item => item.providerId === providerId);
    assert.ok(setting);
    await invoke(port, 'CHAT', 'save_desktop_model_service_settings', { args: {
      providerId, enabled: true, presetId, expectedRevision: setting.revision,
      apiKey: `nfai-synthetic-ui-${providerId.toLowerCase()}-${randomUUID()}`,
    } });
  }
  stop();
  port = await start(true);
  await ready(port);
  conversation = await currentConversation(port, workspaceId, conversationId);
  assert.equal(conversation.currentLeafId, thirdId);
  for (const id of [originalUserId, editedUserId, firstId, secondId, thirdId]) {
    assert.ok(conversation.messages.some(item => item.id === id));
  }
  const oldUser = conversation.messages.find(item => item.id === originalUserId);
  const editedUser = conversation.messages.find(item => item.id === editedUserId);
  assert.ok(JSON.stringify(oldUser.blocks).includes('合成原问题'));
  assert.ok(JSON.stringify(editedUser.blocks).includes('合成编辑后的问题'));
  const ledger = await invoke(port, 'CHAT', 'read_desktop_usage_ledger');
  const charges = ledger.records.filter(item => item.conversationId === conversationId);
  assert.equal(charges.length, 3);
  assert.deepEqual(charges.map(item => item.chargeMicros), [4, 4, 4]);
  const work = await invoke(port, 'WORK', 'ensure_desktop_local_workspace');
  const workProjection = await invoke(port, 'WORK', 'read_desktop_workspace', { workspaceId: work.summary.id });
  assert.ok(!workProjection.exchange.conversations.some(item => item.id === conversationId));
  assert.equal(requests.length, 3);
  assert.ok(requests.every(item => item.method === 'POST' && item.userTextPresent && item.idempotencyKey));
  assert.equal(new Set(requests.map(item => item.idempotencyKey)).size, 3);
  assert.equal(requests[0].model, requests[1].model);
  assert.notEqual(requests[1].model, requests[2].model);
  const conversationRow = `[data-action="select-chat"][data-id="${conversationId}"]`;
  await until(() => evaluate(port, `Boolean(document.querySelector(${JSON.stringify(conversationRow)}))`), '会话列表');
  await clickUi(port, conversationRow);
  await until(() => evaluate(port, `Boolean(document.querySelector('.chat-scroll[data-conversation-id="${conversationId}"]'))`), '会话正文');
  const menuButton = id => `[data-action="open-assistant-message-menu"][data-message-id="${id}"]`;
  await clickUi(port, menuButton(thirdId));
  await until(() => evaluate(port, 'Boolean(document.querySelector(".assistant-message-menu [data-action=show-conversation-branches]"))'), '消息菜单');
  await clickUi(port, '.assistant-message-menu [data-action="show-conversation-branches"]');
  assert.equal(await evaluate(port, 'document.querySelector("#dialog-title")?.textContent'), '分支历史');
  await clickUi(port, `[data-action="select-conversation-branch"][data-leaf-id="${firstId}"]`);
  await until(async () => (await currentConversation(port, workspaceId, conversationId))?.currentLeafId === firstId, '旧回答分支');
  await until(() => evaluate(port, `Boolean(document.querySelector(${JSON.stringify(menuButton(firstId))}))`), '旧回答正文');
  await clickUi(port, menuButton(firstId));
  await clickUi(port, '.assistant-message-menu [data-action="show-conversation-branches"]');
  await clickUi(port, `[data-action="select-conversation-branch"][data-leaf-id="${thirdId}"]`);
  await until(async () => (await currentConversation(port, workspaceId, conversationId))?.currentLeafId === thirdId, '新回答分支');
  await until(() => evaluate(port, `Boolean(document.querySelector(${JSON.stringify(menuButton(editedUserId))}))`), '当前用户消息');
  await clickUi(port, menuButton(editedUserId));
  await clickUi(port, '.assistant-message-menu [data-action="edit-conversation-user-message"]');
  assert.equal(await evaluate(port, 'document.querySelector("#dialog-title")?.textContent'), '编辑消息');
  await evaluate(port, `(()=>{const field=document.querySelector('#conversation-user-message-edit');field.value='合成界面编辑后的问题';field.dispatchEvent(new Event('input',{bubbles:true}));return true})()`);
  await clickUi(port, '[data-action="save-conversation-user-message-edit"]');
  const uiUserId = await until(async () => {
    const item = await currentConversation(port, workspaceId, conversationId);
    const leaf = item?.messages.find(message => message.id === item.currentLeafId);
    return leaf?.role === 'user' && leaf.id !== editedUserId ? leaf.id : null;
  }, '界面编辑的新用户叶');
  await until(() => evaluate(port, `Boolean(document.querySelector(${JSON.stringify(menuButton(uiUserId))}))`), '界面编辑消息');
  await clickUi(port, menuButton(uiUserId));
  await clickUi(port, '.assistant-message-menu [data-action="generate-answer-for-saved-user"]');
  const uiGeneratedId = await until(async () => {
    const item = await currentConversation(port, workspaceId, conversationId);
    const leaf = item?.messages.find(message => message.id === item.currentLeafId);
    return leaf?.role === 'assistant' && leaf.delivery === 'COMPLETE' && leaf.parentId === uiUserId ? leaf.id : null;
  }, '界面生成回答');
  assert.equal(requests.length, 4);
  assert.ok(requests[3].uiEditedTextPresent && requests[3].idempotencyKey);
  // SQLite may expose the completed answer before the UI finishes its own
  // receipt/context refresh. Wait for that visible action owner to be idle.
  await until(() => evaluate(port, '!globalThis.__nanfengDesktopWorkState?.ordinarySendPreparing && !globalThis.__nanfengDesktopWorkState?.pendingChatSubmission'), '界面回答收尾');
  await until(() => evaluate(port, `Boolean(document.querySelector(${JSON.stringify(menuButton(uiGeneratedId))}))`), '界面生成的回答');
  await clickUi(port, menuButton(uiGeneratedId));
  await clickUi(port, '.assistant-message-menu [data-action="regenerate-completed-answer"]');
  const uiRegeneratedId = await until(async () => {
    const item = await currentConversation(port, workspaceId, conversationId);
    const leaf = item?.messages.find(message => message.id === item.currentLeafId);
    return leaf?.role === 'assistant' && leaf.delivery === 'COMPLETE'
      && leaf.parentId === uiUserId && leaf.originMessageId === uiGeneratedId ? leaf.id : null;
  }, '界面重新生成');
  assert.equal(requests.length, 5);
  assert.ok(requests[4].uiEditedTextPresent && requests[4].idempotencyKey);
  assert.equal(requests[3].model, requests[4].model);
  await until(() => evaluate(port, '!globalThis.__nanfengDesktopWorkState?.ordinarySendPreparing && !globalThis.__nanfengDesktopWorkState?.pendingChatSubmission'), '界面重答收尾');
  await until(() => evaluate(port, `Boolean(document.querySelector(${JSON.stringify(menuButton(uiRegeneratedId))}))`), '界面重答回答');
  await clickUi(port, menuButton(uiRegeneratedId));
  await clickUi(port, '.assistant-message-menu [data-action="change-model-completed-answer"]');
  assert.equal(await evaluate(port, 'document.querySelector("#dialog-title")?.textContent'), '换模型重答');
  assert.equal(await evaluate(port, 'document.querySelector("[role=dialog] p")?.textContent.includes("当前问题")'), true);
  await clickUi(port, '[data-action="confirm-change-answer-model"][data-target-preset-id="DEEPSEEK_V4_PRO"]');
  const uiChangedId = await until(async () => {
    const item = await currentConversation(port, workspaceId, conversationId);
    const leaf = item?.messages.find(message => message.id === item.currentLeafId);
    return leaf?.role === 'assistant' && leaf.delivery === 'COMPLETE'
      && leaf.parentId === uiUserId && leaf.originMessageId === uiRegeneratedId ? leaf.id : null;
  }, '界面换模型重答');
  assert.equal(requests.length, 6);
  assert.ok(requests[5].uiEditedTextPresent && requests[5].idempotencyKey);
  assert.equal(requests[5].model, 'deepseek-v4-pro');
  assert.equal(new Set(requests.map(item => item.idempotencyKey)).size, 6);
  assert.equal(new Set([firstId, secondId, thirdId, uiGeneratedId, uiRegeneratedId, uiChangedId]).size, 6);
  const finalLedger = await invoke(port, 'CHAT', 'read_desktop_usage_ledger');
  const finalCharges = finalLedger.records.filter(item => item.conversationId === conversationId);
  assert.equal(finalCharges.length, 6);
  assert.deepEqual(finalCharges.map(item => item.chargeMicros), [4, 4, 4, 4, 4, 4]);
  await until(() => evaluate(port, `Boolean(document.querySelector(${JSON.stringify(menuButton(uiChangedId))}))`), '界面换模型回答');
  if (screenshotMode) {
    const screenshot = (await cdp(port, 'Page.captureScreenshot', { format: 'png', captureBeyondViewport: false }))?.data;
    assert.ok(screenshot);
    await writeFile(join(root, 'w05-visible-answer-branches.png'), Buffer.from(screenshot, 'base64'));
  }
  const beforeColdConversation = await currentConversation(port, workspaceId, conversationId);
  const beforeColdClose = await normalClose();
  port = await start(true);
  await ready(port);
  const finalConversation = await currentConversation(port, workspaceId, conversationId);
  assert.equal(finalConversation.currentLeafId, uiChangedId);
  assert.ok(finalConversation.messages.some(item => item.id === uiGeneratedId));
  assert.ok(finalConversation.messages.some(item => item.id === uiRegeneratedId));
  assert.deepEqual(finalConversation.messages, beforeColdConversation.messages);
  const coldLedger = await invoke(port, 'CHAT', 'read_desktop_usage_ledger');
  assert.deepEqual(coldLedger.records.filter(item => item.conversationId === conversationId), finalCharges);
  const coldWork = await invoke(port, 'WORK', 'read_desktop_workspace', { workspaceId: work.summary.id });
  assert.ok(!coldWork.exchange.conversations.some(item => item.id === conversationId));
  await until(() => evaluate(port, 'Boolean(globalThis.__nanfengDesktopWorkState?.runtimeInfo?.dataAreasReady)'), '冷启双区就绪');
  await until(() => evaluate(port, `Boolean(document.querySelector(${JSON.stringify(conversationRow)}))`), '冷启会话列表');
  await clickUi(port, conversationRow);
  await until(() => evaluate(port, `Boolean(document.querySelector(${JSON.stringify(menuButton(uiChangedId))}))`), '冷启当前回答');
  await clickUi(port, menuButton(uiChangedId));
  await clickUi(port, '.assistant-message-menu [data-action="show-conversation-branches"]');
  await until(() => evaluate(port, `Boolean(document.querySelector('[data-action="select-conversation-branch"][data-leaf-id="${originalUserId}"]'))`), '冷启原问题分支');
  await clickUi(port, `[data-action="select-conversation-branch"][data-leaf-id="${originalUserId}"]`);
  await until(async () => (await currentConversation(port, workspaceId, conversationId))?.currentLeafId === originalUserId, '冷启切回原问题');
  await until(() => evaluate(port, `Boolean(document.querySelector(${JSON.stringify(menuButton(originalUserId))}))`), '冷启原问题正文');
  await clickUi(port, menuButton(originalUserId));
  await clickUi(port, '.assistant-message-menu [data-action="show-conversation-branches"]');
  await clickUi(port, `[data-action="select-conversation-branch"][data-leaf-id="${uiChangedId}"]`);
  await until(async () => (await currentConversation(port, workspaceId, conversationId))?.currentLeafId === uiChangedId, '冷启恢复新回答');
  assert.equal(requests.length, 6, '冷启与本地分支切换不发送模型请求');

  result = { candidateSha256: receipt.sha256, root, workspaceId, conversationId,
    originalUserId, editedUserId, answerIds: [firstId, secondId, thirdId],
    attemptIds: [generated.attemptId, sameModel.attemptId, changed.attemptId],
    models: requests.map(item => item.model), requestCount: requests.length,
    chargeMicros: charges.map(item => item.chargeMicros), workAreaIsolated: true, visibleBranchSwitch: true,
    visibleEditAndGenerate: { userId: uiUserId, answerId: uiGeneratedId, regeneratedId: uiRegeneratedId, changedModelId: uiChangedId },
    finalChargeMicros: finalCharges.map(item => item.chargeMicros), coldLeafId: finalConversation.currentLeafId,
    coldVisibleBranchSwitch: true, coldLedgerPreserved: true, coldWorkAreaIsolated: true,
    beforeColdClose, screenshot: screenshotMode ? join(root, 'w05-visible-answer-branches.png') : null, requests, pids };
  result.normalClose=await normalClose();
} finally {
  stop();
  await new Promise(resolveClose => provider.close(resolveClose));
}
console.log(JSON.stringify(result));
