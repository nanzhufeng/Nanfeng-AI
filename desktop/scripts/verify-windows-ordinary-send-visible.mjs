import assert from 'node:assert/strict';
import { createHash, randomUUID } from 'node:crypto';
import { execFileSync, spawn } from 'node:child_process';
import { existsSync, unlinkSync, writeFileSync } from 'node:fs';
import { readFile, writeFile } from 'node:fs/promises';
import http from 'node:http';
import { dirname, join, resolve, sep } from 'node:path';
import { fileURLToPath } from 'node:url';

if (process.platform !== 'win32') throw new Error('仅在 Windows 隔离候选上验收。');
const desktopRoot = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const rootParent = resolve(process.argv.slice(2).find(arg => !arg.startsWith('--')) || 'D:\\CodexTemp');
const withSources = process.argv.includes('--web-search');
const workEditorArg = process.argv.find(arg => arg === '--work-editor-stream' || arg.startsWith('--work-editor-stream='));
const workEditorStream = Boolean(workEditorArg);
const workEditorKind = workEditorArg?.split('=')[1] || 'project';
assert.ok(['project', 'knowledge', 'memory'].includes(workEditorKind), '流式编辑仅支持项目、知识、记忆');
const crossArea = process.argv.includes('--cross-area') || workEditorStream;
const fromWork = process.argv.includes('--from-work') || workEditorStream;
const attachmentPdf = process.argv.includes('--attachment-pdf');
const attachmentOnly = process.argv.includes('--attachment-only');
const attachmentInputs = process.argv.includes('--attachment-inputs') || attachmentPdf || attachmentOnly;
const syntheticPdf = '%PDF-1.4\n1 0 obj\n<< /Type /Catalog >>\nendobj\n%%EOF\n';
const recoverIncompatiblePdf = process.argv.includes('--recover-incompatible-pdf');
const manualIncompatiblePdf = process.argv.includes('--manual-incompatible-pdf') || recoverIncompatiblePdf;
const deepseekSearch = process.argv.includes('--deepseek-search');
const deepseekOffline = process.argv.includes('--deepseek-offline');
const cameraInput = process.argv.includes('--camera-input');
const temporaryCamera = process.argv.includes('--temporary-camera');
const temporaryExpiry = process.argv.includes('--temporary-expiry');
const temporaryAreaDraft = process.argv.includes('--temporary-area-draft');
const temporarySendRoute = process.argv.includes('--temporary-send-route');
const temporarySendEdit = process.argv.includes('--temporary-send-edit');
const historyContext = process.argv.includes('--history-context');
const branchEdit = process.argv.includes('--branch-edit');
const historyTitleUi = process.argv.includes('--history-title-ui');
const historyTitleRace = process.argv.includes('--history-title-race') || historyTitleUi;
const cancelBeforeHeaders = process.argv.includes('--cancel-before-headers');
const imeComposer = process.argv.includes('--ime-composer');
const contextOtherProject = process.argv.includes('--context-other-project');
const contextProject = process.argv.includes('--context-project') || contextOtherProject;
const uiWorkItems = process.argv.includes('--ui-work-items');
const uiMemorySensitive = process.argv.includes('--ui-memory-sensitive');
const contentModeSwitch = process.argv.includes('--content-mode-switch');
const emptyContextQuery = process.argv.includes('--context-empty-query');
const contextEnabled = process.argv.includes('--context-on') || contextProject || emptyContextQuery;
const personalizationRace = process.argv.includes('--personalization-race');
const profileMemoryOff = process.argv.includes('--profile-memory-off') || personalizationRace;
const summaryPause = process.argv.includes('--summary-pause');
const memorySuggestionConflictParallel = process.argv.includes('--memory-suggestion-conflict-parallel');
const memorySuggestionConflict = process.argv.includes('--memory-suggestion-conflict-revise') || memorySuggestionConflictParallel;
const memorySuggestion = process.argv.includes('--memory-suggestion') || memorySuggestionConflict;
const rejectedMemoryCommand = process.argv.includes('--memory-command-rejected');
const memoryAudit = process.argv.includes('--memory-audit');
const explicitMemoryConflictKeep = process.argv.includes('--memory-command-conflict-keep');
const explicitMemoryConflictRevise = process.argv.includes('--memory-command-conflict-revise');
const explicitMemoryConflictRepeat = process.argv.includes('--memory-command-conflict-repeat');
const explicitMemoryConflictBackup = process.argv.includes('--memory-command-conflict-backup');
const explicitMemoryConflict = process.argv.includes('--memory-command-conflict') || explicitMemoryConflictKeep || explicitMemoryConflictRevise || explicitMemoryConflictRepeat || explicitMemoryConflictBackup;
assert.ok(!(explicitMemoryConflictKeep && explicitMemoryConflictRevise), '同主题验收每次只选择一种决议');
assert.ok(!(explicitMemoryConflictBackup && (explicitMemoryConflictRepeat || explicitMemoryConflictKeep || explicitMemoryConflictRevise)), '备份恢复验收单独运行');
const explicitMemoryResolution = explicitMemoryConflictKeep ? 'KEEP_EXISTING' : explicitMemoryConflictRevise ? 'REVISE_EXISTING' : 'CREATE_PARALLEL';
const explicitMemoryCommand = process.argv.includes('--memory-command') || rejectedMemoryCommand || explicitMemoryConflict || memoryAudit;
const profileSettingsFlow = profileMemoryOff || summaryPause;
const contextDisabled = process.argv.includes('--context-off') || profileSettingsFlow;
const contextMode = contextEnabled || contextDisabled;
const configureModelUi = process.argv.includes('--configure-model-ui');
const autoTitleFailed = process.argv.includes('--auto-title-failed');
const autoTitle = process.argv.includes('--auto-title') || autoTitleFailed;
assert.ok(!historyTitleRace || (!autoTitle && !crossArea && !explicitMemoryCommand && !branchEdit), '标题时序验收单独运行普通聊天');
const configuredProviderId = autoTitle ? 'QWEN' : 'OPENROUTER';
const manualTerra = process.argv.includes('--manual-terra');
assert.ok(!manualTerra || configureModelUi, '手选 Terra 验收须经可见模型设置页保存合成凭据');
assert.ok(!autoTitle || (configureModelUi && !manualTerra), '自动标题验收须配置 Qwen 且不手选 Terra');
assert.ok(!(contextEnabled && contextDisabled), '上下文开关模式不能同时启用');
assert.ok(!contextProject || (fromWork && crossArea), '项目上下文需要 WORK 原生切区链');
assert.ok(!uiWorkItems || contextProject, 'WORK 界面资料验收需要项目上下文');
assert.ok(!uiMemorySensitive || uiWorkItems, 'Memory 敏感内容门禁需要 WORK 界面资料链');
assert.ok(!contentModeSwitch || (!fromWork && !crossArea && !contextMode), '已有对话模式切换单独验收 CHAT 链');
assert.ok(!(withSources && crossArea), '联网来源与切区验收分别运行');
assert.ok(!fromWork || crossArea, '工作区发送仅用于切区验收');
assert.ok(!attachmentInputs || (!withSources && !crossArea), '附件输入单独验收普通发送');
assert.ok(!cameraInput || (!withSources && !crossArea && !attachmentInputs), '相机输入单独验收普通发送');
assert.ok(!temporaryCamera || (!withSources && !crossArea && !attachmentInputs && !cameraInput && !historyContext && !branchEdit && !contextMode), '临时相机单独验收');
assert.ok(!historyContext || (!fromWork && !contextMode && !attachmentInputs), '历史上下文单独验收 CHAT 发送');
assert.ok(!branchEdit || (!withSources && !crossArea && !contextMode && !historyContext && !attachmentInputs && !cameraInput), '编辑分支单独验收普通发送');
const originArea = fromWork ? 'WORK' : 'CHAT';
const receipt = JSON.parse(await readFile(join(desktopRoot, 'output/windows-acceptance/current.json'), 'utf8'));
assert.equal(receipt.identifier, 'com.nanzhufeng.ai.desktop.windowsacceptance');
const source = resolve(desktopRoot, 'output/windows-acceptance', receipt.sha256, 'NanfengAI-Windows-Acceptance.exe');
assert.equal(resolve(receipt.executable), source);
assert.equal(createHash('sha256').update(await readFile(source)).digest('hex').toUpperCase(), receipt.sha256);
const installedArgument = process.argv.find(arg => arg.startsWith('--installed-exe='));
const installedHashArgument = process.argv.find(arg => arg.startsWith('--installed-sha256='));
assert.equal(Boolean(installedArgument), Boolean(installedHashArgument), '安装态 EXE 与哈希必须同时指定');
const installedExecutable = installedArgument ? resolve(installedArgument.slice('--installed-exe='.length)) : null;
const expectedHash = installedHashArgument ? installedHashArgument.slice('--installed-sha256='.length).toUpperCase() : receipt.sha256;
assert.match(expectedHash, /^[A-F0-9]{64}$/);
if (installedExecutable) {
  assert.equal(createHash('sha256').update(await readFile(installedExecutable)).digest('hex').toUpperCase(), expectedHash);
}
const runExecutable = installedExecutable || resolve(desktopRoot, 'output/windows-acceptance-binaries', receipt.sha256, 'nanfeng-ai-desktop-spike.exe');
const prompt = attachmentOnly ? '' : emptyContextQuery ? '？' : explicitMemoryCommand ? '记住了：我长期关注跨端开发与数据保全' : memorySuggestion ? '我的南枫项目长期关注 AI 与影视投资' : 'W06 合成新对话流式发送';
const answerFirst = explicitMemoryCommand ? '【记忆主题】工作流程偏好\n' : memorySuggestion ? '我会把这个长期项目偏好与研究方向拆成稳定原则，并在后续建议里优先考虑。'.repeat(2) : '合成流式前半，';
const answerSecond = rejectedMemoryCommand ? '【记忆摘要】验证码：123456，不得保存。' : explicitMemoryCommand ? '【记忆摘要】长期关注跨端开发与数据保全。' : memorySuggestion ? '这些内容仅为本机隔离验收的合成回答。' : '合成流式后半。';
const answer = rejectedMemoryCommand ? '这次没有生成可安全保存的记忆摘要，因此未写入。你可以更明确地说出需要长期记住的内容后再试一次。' : explicitMemoryConflict ? '已生成同主题记忆候选，尚未用于后续回答。请在设置的记忆摘要中选择保留、并存或修订：\n长期关注跨端开发与数据保全。' : explicitMemoryCommand ? '已更新记忆摘要：\n长期关注跨端开发与数据保全。' : answerFirst + answerSecond;
const configuredKey = configureModelUi ? `nfai-synthetic-installed-ui-${randomUUID()}` : null;
const editedPrompt = 'W05 编辑后的合成问题';
const cancelPrompt = 'W06 合成取消后续';
const failPrompt = 'W06 合成失败后续';
const deepseekPrompt = 'W06 DeepSeek 联网合成问题';
const cancelPartial = '合成取消前保留';
const publicSource = 'https://example.test/w06-source';
const ps = args => execFileSync('pwsh', ['-NoProfile', ...args], { encoding: 'utf8', timeout: 60000 }).trim();
const reservePort = async () => {
  const socket = http.createServer();
  await new Promise(resolveReady => socket.listen(0, '127.0.0.1', resolveReady));
  const port = socket.address().port;
  await new Promise(resolveClose => socket.close(resolveClose));
  return port;
};
const providerRequests = [];
let releaseHistoryAnswer;
const historyAnswerGate = new Promise(resolveRelease => { releaseHistoryAnswer = resolveRelease; });
const provider = http.createServer((request, response) => {
  const chunks = [];
  request.on('data', chunk => chunks.push(chunk));
  request.on('end', () => {
    try {
      const body = JSON.parse(Buffer.concat(chunks).toString('utf8'));
      const lastUser = [...(body.messages || [])].reverse().find(item => item.role === 'user');
      const lastUserText = JSON.stringify(lastUser?.content || '');
      const requestText = JSON.stringify(body.messages || []);
      const kind = autoTitle && body.stream === false ? 'title'
        : lastUserText.includes(cancelPrompt) ? 'cancel'
        : lastUserText.includes(failPrompt) ? 'fail'
        : lastUserText.includes(prompt) || lastUserText.includes(editedPrompt) || lastUserText.includes(deepseekPrompt)
          || (recoverIncompatiblePdf && lastUserText.includes('阅读合成 PDF')) ? 'answer' : 'title';
      providerRequests.push({ kind, model: body.model, path: request.url,
        credentialMatched: configuredKey ? request.headers.authorization === `Bearer ${configuredKey}` : null,
        idempotencyKey: request.headers['idempotency-key'] || '',
        stream: body.stream, includeUsage: body.stream_options?.include_usage,
        knowledgePresent: requestText.includes('W12_KNOWLEDGE_ALPHA'),
        memoryPresent: requestText.includes('W12_MEMORY_ALPHA'),
        memoryCommandInstructionPresent: requestText.includes('【记忆主题】') && requestText.includes('【记忆摘要】'),
        profileOccupationPresent: requestText.includes('W12_PROFILE_OCCUPATION'),
        profileInstructionPresent: requestText.includes('W12_PROFILE_INSTRUCTION'),
        knowledgeBeforeMemory: requestText.indexOf('W12_KNOWLEDGE_ALPHA') < requestText.indexOf('W12_MEMORY_ALPHA'),
        historyPresent: requestText.includes('W12_HISTORY_ALPHA'),
        archivedHistoryPresent: requestText.includes('W12_ARCHIVED_SECRET'),
        pastedTextPresent: JSON.stringify(body.messages).includes('W08 synthetic paste'),
        droppedTextPresent: JSON.stringify(body.messages).includes('W08 synthetic drop'),
        pdfFilePresent: requestText.includes('data:application/pdf;base64,' + Buffer.from(syntheticPdf).toString('base64')),
        cameraImagePresent: JSON.stringify(body.messages).includes('data:image/png;base64,') });
      if (kind === 'fail') {
        response.writeHead(429, { 'content-type': 'application/json' });
        response.end(JSON.stringify({ error: { message: 'synthetic-rate-limit' } }));
        return;
      }
      if (kind === 'title') {
        assert.equal(body.stream, false);
        response.writeHead(200, { 'content-type': 'application/json' });
        response.end(JSON.stringify({ model: body.model,
          choices: [{ message: { role: 'assistant', content: autoTitleFailed ? '  ' : JSON.stringify({ title: '合成会话标题' }) }, finish_reason: 'stop' }],
          usage: { prompt_tokens: 3, completion_tokens: 8, cost: autoTitleFailed ? 0.000007 : 0.000002 } }));
        return;
      }
      if (deepseekSearch && kind === 'answer') {
        assert.equal(request.url, '/', '隔离端点只替换网络目的地，路由以请求体核对');
        assert.equal(body.stream, false);
        assert.equal(body.tool_choice?.type, 'tool');
        assert.equal(body.tools?.[0]?.type, 'web_search_20250305');
        response.writeHead(200, { 'content-type': 'application/json' });
        response.end(JSON.stringify({ model: body.model, stop_reason: 'end_turn',
          content: [{ type: 'web_search_tool_result', content: [{ type: 'web_search_result',
            url: 'https://example.test/deepseek-search', title: '合成公开来源' }] },
          { type: 'text', text: 'DeepSeek 检索后的合成回答' }],
          usage: { input_tokens: 13, output_tokens: 7, cost: 0.000006 } }));
        return;
      }
      if (deepseekOffline && kind === 'answer') {
        assert.equal(request.url, '/');
        assert.equal(body.stream, true);
        assert.equal(body.tools, undefined);
        response.writeHead(200, { 'content-type': 'text/event-stream; charset=utf-8' });
        response.end(`data: ${JSON.stringify({ model: body.model,
          choices: [{ delta: { content: 'DeepSeek 未联网的合成回答' }, finish_reason: 'stop' }],
          usage: { prompt_tokens: 9, completion_tokens: 4, cost: 0.000003 } })}\n\ndata: [DONE]\n\n`);
        return;
      }
      if (kind === 'cancel' && cancelBeforeHeaders) {
        setTimeout(() => {
          if (!response.destroyed) response.end('HTTP response arrived after cancellation');
        }, 5000).unref();
        return;
      }
      response.writeHead(200, { 'content-type': 'text/event-stream; charset=utf-8', 'cache-control': 'no-cache' });
      if (kind === 'answer') {
        response.write(`data: ${JSON.stringify({ model: body.model, choices: [{ delta: explicitMemoryCommand ? { content: answerFirst, reasoning_content: 'SYNTHETIC_MEMORY_REASONING_SECRET' } : { content: answerFirst } }] })}\n\n`);
        setTimeout(async () => {
          if (historyTitleRace || workEditorStream) await historyAnswerGate;
          if (response.destroyed) return;
          const delta = { content: answerSecond };
          if (withSources) delta.annotations = [
            { url_citation: { title: 'Official](https://attacker.example)[Fake <script>', url: publicSource } },
            { url_citation: { title: 'Private', url: 'https://user@example.test/private' } },
          ];
          response.write(`data: ${JSON.stringify({ choices: [{ delta, finish_reason: 'stop' }], usage: { prompt_tokens: 9, completion_tokens: 6, cost: 0.000005 } })}\n\n`);
          response.end('data: [DONE]\n\n');
        }, 1600);
      } else if (kind === 'cancel') {
        response.write(`data: ${JSON.stringify({ model: body.model, choices: [{ delta: { content: cancelPartial } }] })}\n\n`);
        setTimeout(() => {
          if (!response.destroyed) response.end(`data: ${JSON.stringify({ choices: [{ delta: { content: '取消后不应出现' }, finish_reason: 'stop' }] })}\n\ndata: [DONE]\n\n`);
        }, 5000).unref();
      } else {
        response.write(`data: ${JSON.stringify({ choices: [{ delta: { content: '合成会话标题' }, finish_reason: 'stop' }], usage: { prompt_tokens: 3, completion_tokens: 2, cost: 0.000002 } })}\n\n`);
        response.end('data: [DONE]\n\n');
      }
    } catch (error) { response.writeHead(400); response.end(String(error)); }
  });
});
await new Promise(resolveReady => provider.listen(0, '127.0.0.1', resolveReady));
const providerPort = provider.address().port;
let launch = null;
let root = '';
let activeDebugPort = 0;
let credentialReleaseTimer = null;
let personalizationDbLock = null;
const pids = [];
async function holdPersonalizationWrite() {
  assert.ok(existsSync(join(root, '.nanfeng-windows-acceptance.json')), '只锁带标记的合成根');
  const program = 'import sqlite3,sys; c=sqlite3.connect(sys.argv[1],timeout=2,isolation_level=None); c.execute("BEGIN IMMEDIATE"); print("LOCK_HELD",flush=True); sys.stdin.readline(); c.execute("ROLLBACK"); c.close()';
  const child = spawn('python', ['-u', '-c', program, join(root, 'workspace.sqlite3')], { stdio: ['pipe', 'pipe', 'pipe'] });
  const ready = await new Promise((resolveReady, rejectReady) => {
    const timer = setTimeout(() => rejectReady(new Error('合成数据库写锁未就绪')), 5000);
    child.stdout.once('data', chunk => {
      clearTimeout(timer);
      if (chunk.toString().includes('LOCK_HELD')) resolveReady(true);
      else rejectReady(new Error('合成数据库写锁回执无效'));
    });
    child.once('error', error => { clearTimeout(timer); rejectReady(error); });
    child.once('exit', code => { clearTimeout(timer); rejectReady(new Error(`合成数据库写锁提前退出 ${code}`)); });
  });
  assert.ok(ready);
  return child;
}
function stop() {
  if (!launch) return;
  const pid = Number(launch.pid);
  const literal = value => `'${String(value).replaceAll("'", "''")}'`;
  const script = '$expected=' + literal(runExecutable) + '; $hash=' + literal(expectedHash) + '; '
    + '$p=Get-Process -Id ' + pid + ' -ErrorAction SilentlyContinue; '
    + 'if ($p) { if ([IO.Path]::GetFullPath([string]$p.Path) -ne $expected '
    + '-or (Get-FileHash -LiteralPath $expected -Algorithm SHA256).Hash -ne $hash) { throw "PID identity mismatch" }; '
    + 'Stop-Process -Id ' + pid + ' -Force -ErrorAction Stop }';
  ps(['-Command', script]);
  pids.push(pid);
  launch = null;
}
const launchPowerShell = args => new Promise((resolveLaunch, reject) => {
  const child = spawn('pwsh', ['-NoProfile', ...args], { stdio: ['ignore', 'pipe', 'pipe'] });
  let stdout = '';
  let stderr = '';
  const timer = setTimeout(() => { child.kill(); reject(new Error(`隔离启动超时：${stderr}`)); }, 30000);
  child.stdout.on('data', data => {
    stdout += data.toString();
    const line = stdout.slice(0, stdout.lastIndexOf('\n') + 1).split(/\r?\n/)
      .find(item => item.trim().startsWith('{') && item.includes('"pid"'));
    if (!line) return;
    clearTimeout(timer);
    try { resolveLaunch(JSON.parse(line)); } catch (error) { reject(error); }
    child.stdout.destroy(); child.stderr.destroy(); child.unref();
  });
  child.stderr.on('data', data => { stderr += data.toString(); });
  child.on('error', error => { clearTimeout(timer); reject(error); });
  child.on('exit', code => { if (!stdout.includes('"pid"')) { clearTimeout(timer); reject(new Error(`隔离启动失败 ${code}：${stderr}`)); } });
});
async function start(reopen = false) {
  const port = await reservePort();
  activeDebugPort = port;
  const args = ['-File', join(desktopRoot, 'scripts/start-windows-acceptance.ps1'), '-RootParent', rootParent,
    '-StartupMode', 'normal', '-Visible', '-DebugPort', String(port), '-OrdinaryChatTestEndpoint', `http://127.0.0.1:${providerPort}`];
  if (installedExecutable) args.push('-ExecutablePath', installedExecutable, '-RunInPlace');
  if (configureModelUi) args.push('-OrdinaryChatCredentialPause');
  if (reopen) args.push('-Root', root);
  if (cameraInput || temporaryCamera) args.push('-FakeCamera');
  launch = await launchPowerShell(args);
  assert.equal(launch.sha256, expectedHash);
  assert.equal(resolve(launch.executable), runExecutable);
  root = resolve(launch.root);
  assert.ok(root.startsWith(rootParent + sep));
  const marker = JSON.parse(await readFile(join(root, '.nanfeng-windows-acceptance.json'), 'utf8'));
  assert.equal(marker.identifier, receipt.identifier);
  if (configureModelUi && !credentialReleaseTimer) {
    const reached = join(root, 'ordinary-chat-before-credential.reached');
    const resume = join(root, 'ordinary-chat-before-credential.resume');
    credentialReleaseTimer = setInterval(() => {
      if (existsSync(reached) && !existsSync(resume)) {
        unlinkSync(reached);
        writeFileSync(resume, 'resume');
      }
    }, 50);
    credentialReleaseTimer.unref();
  }
  return port;
}
async function cdp(port, method, params = {}) {
  const tabs = await fetch(`http://127.0.0.1:${port}/json/list`).then(response => response.json());
  const tab = tabs.find(item => item.type === 'page' && item.url === 'http://tauri.localhost/');
  assert.ok(tab, 'WebView2 页面未就绪');
  const socket = new WebSocket(tab.webSocketDebuggerUrl);
  try {
    await new Promise((resolveReady, reject) => {
      socket.addEventListener('open', resolveReady, { once: true });
      socket.addEventListener('error', reject, { once: true });
    });
    return await new Promise((resolveResult, reject) => {
      const timer = setTimeout(() => reject(new Error(`CDP ${method} timed out`)), 30000);
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
const evaluate = async (port, expression) => (await cdp(port, 'Runtime.evaluate',
  { expression, awaitPromise: true, returnByValue: true }))?.result?.value;
async function invoke(port, command, args = {}, area = originArea) {
  const expression = `window.__TAURI__.core.invoke(${JSON.stringify(command)},${JSON.stringify(args)},${JSON.stringify({ headers: { 'x-nanfeng-data-area': area } })}).catch(error=>({__error:String(error)}))`;
  const value = await evaluate(port, expression);
  if (value?.__error) throw new Error(`${command}: ${value.__error}`);
  return value;
}
async function until(check, label, timeoutMs = 30000) {
  const deadline = Date.now() + timeoutMs;
  let lastError = null;
  while (Date.now() < deadline) {
    try { const value = await check(); if (value) return value; } catch (error) { lastError = String(error); }
    await new Promise(resolveWait => setTimeout(resolveWait, 100));
  }
  throw new Error(`${label} 未在原生界面出现${lastError ? `；最后读取：${lastError}` : ''}`);
}
async function click(port, selector) {
  await until(() => evaluate(port, `(()=>{const node=document.querySelector(${JSON.stringify(selector)});if(!node)return false;node.click();return true})()`), `可点击控件 ${selector}`, 10000);
}
async function selectVisibleDailyModel(port, modelId) {
  await until(() => evaluate(port, 'Boolean(document.querySelector("[data-action=toggle-p6g-model-picker]:not([disabled])"))'),
    '可用模型目录已进入界面');
  const tier = '[data-action="select-p6g-tier"][data-tier="DAILY"]';
  const model = `[data-action="select-p6g-model"][data-model-id="${modelId}"]`;
  for (let attempt = 0; attempt < 12; attempt += 1) {
    if (await evaluate(port, 'globalThis.__nanfengDesktopWorkState?.pendingNewConversationModelId') === modelId) return;
    if (!await evaluate(port, `Boolean(document.querySelector(${JSON.stringify(tier)}) || document.querySelector(${JSON.stringify(model)}))`)) {
      if (!await evaluate(port, 'Boolean(globalThis.__nanfengDesktopWorkState?.p6gModelPickerOpen)')) {
        await click(port, '[data-action="toggle-p6g-model-picker"]');
      }
      await new Promise(resolveWait => setTimeout(resolveWait, 250));
    }
    if (await evaluate(port, `Boolean(document.querySelector(${JSON.stringify(tier)}))`)) await click(port, tier);
    if (await evaluate(port, `Boolean(document.querySelector(${JSON.stringify(model)}))`)) await click(port, model);
    await new Promise(resolveWait => setTimeout(resolveWait, 250));
  }
  const ui = await evaluate(port, '({selected:globalThis.__nanfengDesktopWorkState?.pendingNewConversationModelId,conversationId:globalThis.__nanfengDesktopWorkState?.selectedConversationId,override:globalThis.__nanfengDesktopWorkState?.p6gSelection?.conversationOverride,picker:globalThis.__nanfengDesktopWorkState?.p6gModelPickerOpen,tier:globalThis.__nanfengDesktopWorkState?.p6gSelection?.pickerTier,error:globalThis.__nanfengDesktopWorkState?.error,status:globalThis.__nanfengDesktopWorkState?.status,actions:[...document.querySelectorAll("[data-action]")].map(node=>node.dataset.action).filter(action=>action.includes("p6g"))})');
  throw new Error(`${modelId} 未能从可见模型选择器选中：${JSON.stringify(ui)}`);
}
const selectDeepseekFlash = port => selectVisibleDailyModel(port, 'DEEPSEEK_V4_FLASH');
async function fillField(port, selector, value) {
  const found = await evaluate(port, `(()=>{const node=document.querySelector(${JSON.stringify(selector)});if(!node)return false;node.value=${JSON.stringify(value)};node.dispatchEvent(new Event('input',{bubbles:true}));node.dispatchEvent(new Event('change',{bubbles:true}));return true})()`);
  assert.equal(found, true, `找不到表单字段 ${selector}`);
}
const messageText = item => (item?.blocks || []).filter(block => block.kind === 'TEXT').map(block => block.text || '').join('');
async function fillComposer(port, value) {
  await evaluate(port, `(()=>{const field=document.querySelector('#chat-composer');field.value=${JSON.stringify(value)};field.dispatchEvent(new Event('input',{bubbles:true}));return true})()`);
  await until(() => evaluate(port, 'Boolean(document.querySelector("[data-action=save-local-message]:not([disabled])"))'), '发送按钮');
}
const readConversation = async (port, workspaceId, conversationId) => {
  const workspace = await invoke(port, 'read_desktop_workspace', { workspaceId });
  return workspace.exchange.conversations.find(item => item.id === conversationId);
};
async function captureFailurePage() {
  const tabs = await fetch(`http://127.0.0.1:${activeDebugPort}/json/list`, { signal: AbortSignal.timeout(2000) }).then(response => response.json());
  const pages = tabs.filter(tab => tab.type === 'page');
  const output = { pages: pages.map(({ id, url, title }) => ({ id, url, title })) };
  const tab = pages.find(page => page.url.startsWith('http://tauri.localhost'));
  if (!tab) return output;
  const socket = new WebSocket(tab.webSocketDebuggerUrl);
  try {
    output.runtime = await new Promise((done, fail) => {
      const timer = setTimeout(() => fail(new Error('failure-page diagnostic timeout')), 2000);
      socket.addEventListener('error', () => { clearTimeout(timer); fail(new Error('failure-page socket error')); }, { once: true });
      socket.addEventListener('open', () => socket.send(JSON.stringify({ id: 1, method: 'Runtime.evaluate', params: {
        expression: 'JSON.stringify({url:location.href,ready:document.readyState,tauri:Boolean(window.__TAURI__),core:Boolean(window.__TAURI__?.core?.invoke),area:globalThis.__nanfengDesktopWorkState?.dataArea,error:globalThis.__nanfengDesktopWorkState?.error,bodyLength:document.body?.innerText.length})', returnByValue: true,
      } })), { once: true });
      socket.addEventListener('message', event => { const message = JSON.parse(event.data); if (message.id !== 1) return; clearTimeout(timer); done(message.result || message.error); });
    });
  } finally { socket.close(); }
  return output;
}
let result;
try {
  let port = await start();
  await until(() => evaluate(port, 'Boolean(window.__TAURI__?.core?.invoke)'), '原生 IPC');
  if (temporarySendRoute || temporarySendEdit) {
    const workspaceId=(await invoke(port,'ensure_desktop_local_workspace')).summary.id;
    await until(()=>evaluate(port,'Boolean(document.querySelector("[data-action=new-chat]"))'),'新对话入口');
    await click(port,'[data-action="new-chat"]');
    await until(()=>evaluate(port,'Boolean(document.querySelector("[data-action=toggle-temporary-chat]"))'),'临时入口');
    await click(port,'[data-action="toggle-temporary-chat"]');
    const temporaryId=await until(()=>evaluate(port,'globalThis.__nanfengDesktopWorkState?.temporaryConversation?.temporaryId'),'临时聊天');
    assert.equal(await evaluate(port,`(()=>{const callbacks=window.__TAURI_INTERNALS__.callbacks;const original=callbacks.get;window.__nfTempAppendGate={held:false,returned:false};callbacks.get=function(id){const entry=original.call(this,id);const callback=typeof entry==='function'?entry:entry?.callback;if(typeof callback!=='function')return entry;const wrapped=value=>{if(!window.__nfTempAppendGate.held&&value?.messages?.length===1&&value.messages[0].text==='合成临时发送正文'&&value.draft===''){window.__nfTempAppendGate.held=true;window.__nfTempAppendGate.release=()=>{callback(value);window.__nfTempAppendGate.returned=true;};return;}callback(value);};return typeof entry==='function'?wrapped:{...entry,callback:wrapped};};return callbacks.get!==original})()`),true);
    await fillComposer(port,'合成临时发送正文');
    await until(()=>evaluate(port,'Boolean(document.querySelector("[data-action=save-local-message]:not([disabled])"))'),'临时发送可用');
    await click(port,'[data-action="save-local-message"]');
    await until(()=>evaluate(port,'window.__nfTempAppendGate?.held'),'原生临时提交回执暂停');
    if(temporarySendRoute){
      await click(port,'[data-action="toggle-temporary-chat"]');
      await until(()=>evaluate(port,'globalThis.__nanfengDesktopWorkState?.temporaryConversation===null'),'返回普通聊天');
    }
    const nextDraft=temporarySendEdit?'合成下一条临时草稿':'合成普通聊天新草稿';
    await fillComposer(port,nextDraft);
    await evaluate(port,'window.__nfTempAppendGate.release();true');
    await until(()=>evaluate(port,'window.__nfTempAppendGate.returned'),'原生回执释放');
    assert.equal(await evaluate(port,'document.querySelector("#chat-composer")?.value'),nextDraft);
    assert.equal(await evaluate(port,'Boolean(globalThis.__nanfengDesktopWorkState?.temporaryConversation)'),temporarySendEdit);
    const persisted=await until(async()=>temporarySendEdit?(await invoke(port,'read_desktop_temporary_conversation'))?.draft===nextDraft:(await invoke(port,'read_desktop_conversation_draft',{workspaceId,conversationId:null}))?.text===nextDraft,'新草稿持久保存');
    assert.equal(persisted,true);
    const saved=await invoke(port,'read_desktop_temporary_conversation');
    assert.equal(saved.messages.length,1);assert.equal(saved.messages[0].text,'合成临时发送正文');
    stop();port=await start(true);
    await until(()=>evaluate(port,'Boolean(window.__TAURI__?.core?.invoke)'),'临时发送冷启IPC');
    const cold=await invoke(port,'read_desktop_temporary_conversation');
    assert.equal(cold.messages.length,1);assert.equal(cold.messages[0].text,'合成临时发送正文');
    if(temporarySendEdit)assert.equal(cold.draft,nextDraft);
    else assert.equal((await invoke(port,'read_desktop_conversation_draft',{workspaceId,conversationId:null}))?.text,nextDraft);
    assert.equal(providerRequests.length,0);
    result={candidateSha256:expectedHash,root,workspaceId,temporaryId,lateAppendRouteSafe:temporarySendRoute,lateAppendEditSafe:temporarySendEdit,coldReadback:true,providerRequests:providerRequests.length,pids};
  } else if (temporaryAreaDraft) {
    const workspaceId=(await invoke(port,'ensure_desktop_local_workspace')).summary.id;
    await until(()=>evaluate(port,'Boolean(document.querySelector("[data-action=new-chat]"))'),'新对话入口');
    await click(port,'[data-action="new-chat"]');
    await until(()=>evaluate(port,'Boolean(document.querySelector("[data-action=toggle-temporary-chat]"))'),'临时入口');
    await click(port,'[data-action="toggle-temporary-chat"]');
    const temporaryId=await until(()=>evaluate(port,'globalThis.__nanfengDesktopWorkState?.temporaryConversation?.temporaryId'),'临时聊天');
    const draft='合成临时草稿：输入后立即切工作区';
    await until(()=>evaluate(port,'Boolean(document.querySelector("[data-action=show-work]"))'),'工作区入口');
    assert.equal(await evaluate(port,`(()=>{const input=document.querySelector('#chat-composer');input.value=${JSON.stringify(draft)};input.dispatchEvent(new Event('input',{bubbles:true}));const button=document.querySelector('[data-action="show-work"]');if(!button)return false;button.click();return true})()`),true);
    await until(()=>evaluate(port,'globalThis.__nanfengDesktopWorkState?.dataArea==="WORK" && Boolean(document.querySelector("[data-action=show-chat]"))'),'工作区切换完成');
    const saved=await invoke(port,'read_desktop_temporary_conversation',{},'CHAT');
    assert.equal(saved?.temporaryId,temporaryId);
    assert.equal(saved?.draft,draft);
    assert.equal(await invoke(port,'read_desktop_temporary_conversation',{},'WORK'),null);
    await click(port,'[data-action="show-chat"]');
    await until(()=>evaluate(port,`document.querySelector('#chat-composer')?.value===${JSON.stringify(draft)}`),'返回CHAT保留临时草稿');
    stop();port=await start(true);
    await until(()=>evaluate(port,'Boolean(window.__TAURI__?.core?.invoke)'),'切区草稿冷启IPC');
    assert.equal((await invoke(port,'read_desktop_temporary_conversation',{},'CHAT'))?.draft,draft);
    assert.equal(providerRequests.length,0);
    await until(()=>evaluate(port,'Boolean(document.querySelector("[data-action=toggle-temporary-chat]"))'),'冷启临时恢复入口');
    await click(port,'[data-action="toggle-temporary-chat"]');
    await until(()=>evaluate(port,`document.querySelector('#chat-composer')?.value===${JSON.stringify(draft)}`),'冷启显式恢复草稿');
    result={candidateSha256:expectedHash,root,workspaceId,temporaryId,immediateSwitchDraftRetained:true,coldReadback:true,visibleRecoveredDraft:true,providerRequests:providerRequests.length,pids};
  } else if (temporaryExpiry) {
    const workspaceId = (await invoke(port, 'ensure_desktop_local_workspace')).summary.id;
    await until(() => evaluate(port, 'Boolean(document.querySelector("[data-action=new-chat]"))'), '新对话入口');
    await click(port, '[data-action="new-chat"]');
    await until(() => evaluate(port, 'Boolean(document.querySelector("[data-action=toggle-temporary-chat]"))'), '临时聊天入口');
    await click(port, '[data-action="toggle-temporary-chat"]');
    const temporaryId = await until(() => evaluate(port, 'globalThis.__nanfengDesktopWorkState?.temporaryConversation?.temporaryId'), '临时聊天可见');
    assert.equal((await invoke(port, 'read_desktop_temporary_conversation'))?.temporaryId, temporaryId);
    // Only this marker root is in use. Simulate the native owner pruning the
    // record while the window remains open, then exercise the visible editor.
    assert.equal(await invoke(port, 'clear_desktop_temporary_conversation', { args: { temporaryId } }), null);
    assert.equal(await invoke(port, 'read_desktop_temporary_conversation'), null);
    await evaluate(port, `(()=>{const field=document.querySelector('#chat-composer');field.value='合成到期输入';field.dispatchEvent(new Event('input',{bubbles:true}));return true})()`);
    await until(() => evaluate(port, 'globalThis.__nanfengDesktopWorkState?.temporaryConversation===null && globalThis.__nanfengDesktopWorkState?.status?.includes("临时聊天已到期")'), '到期后返回普通聊天');
    assert.equal(await evaluate(port, 'globalThis.__nanfengDesktopWorkState?.composerDraft'), '');
    assert.equal(await evaluate(port, 'Boolean(document.querySelector("[data-action=toggle-temporary-chat]"))'), true);
    assert.equal((await invoke(port, 'read_desktop_conversation_draft', { workspaceId, conversationId: null }))?.text || '', '');
    assert.equal(await invoke(port, 'read_desktop_temporary_conversation'), null);
    assert.equal(providerRequests.length, 0);
    stop();
    port = await start(true);
    await until(() => evaluate(port, 'Boolean(window.__TAURI__?.core?.invoke)'), '到期同根冷启');
    assert.equal(await invoke(port, 'read_desktop_temporary_conversation'), null);
    assert.equal(await evaluate(port, 'Boolean(globalThis.__nanfengDesktopWorkState?.temporaryConversation)'), false);
    result = { candidateSha256: expectedHash, root, workspaceId, temporaryId,
      expiredWindowReturnedNormal: true, coldReadback: true, providerRequests: providerRequests.length, pids };
  } else if (temporaryCamera) {
    const workspaceId = (await invoke(port, 'ensure_desktop_local_workspace')).summary.id;
    const ordinaryConversationIds = (await invoke(port, 'read_desktop_workspace', { workspaceId }))
      .exchange.conversations.map(item => item.id).sort();
    await until(() => evaluate(port, 'Boolean(document.querySelector("[data-action=new-chat]"))'), '新对话入口');
    await click(port, '[data-action="new-chat"]');
    await until(() => evaluate(port, 'Boolean(document.querySelector("[data-action=toggle-temporary-chat]"))'), '临时聊天入口').catch(async error => {
      const ui = await evaluate(port, '({body:document.body?.textContent?.slice(0,500),area:globalThis.__nanfengDesktopWorkState?.dataArea,pane:globalThis.__nanfengDesktopWorkState?.pane,status:globalThis.__nanfengDesktopWorkState?.status,error:globalThis.__nanfengDesktopWorkState?.error})');
      throw new Error(`${error.message}: ${JSON.stringify(ui)}`);
    });
    await click(port, '[data-action="toggle-temporary-chat"]');
    const temporaryId = await until(() => evaluate(port, 'globalThis.__nanfengDesktopWorkState?.temporaryConversation?.temporaryId'), '临时聊天可见');
    await click(port, '[data-action="toggle-composer-add"]');
    await click(port, '[data-action="open-composer-camera"]');
    await until(() => evaluate(port, 'Boolean(document.querySelector("[data-action=capture-composer-camera]:not([disabled])"))'), '临时合成相机就绪');
    await evaluate(port, 'globalThis.__nanfengAcceptanceCameraStream=document.querySelector("[data-composer-camera-video]").srcObject');
    assert.equal(await evaluate(port, 'globalThis.__nanfengAcceptanceCameraStream.getAudioTracks().length'), 0);
    await click(port, '[data-action="close-composer-camera"]');
    await until(() => evaluate(port, '!document.querySelector("[data-composer-camera-video]")'), '临时相机取消');
    assert.equal(await evaluate(port, 'globalThis.__nanfengAcceptanceCameraStream.getTracks().every(track=>track.readyState==="ended")'), true);
    assert.equal((await invoke(port, 'read_desktop_temporary_conversation'))?.draftAttachmentIds?.length, 0);
    await click(port, '[data-action="toggle-composer-add"]');
    await click(port, '[data-action="open-composer-camera"]');
    await until(() => evaluate(port, 'Boolean(document.querySelector("[data-action=capture-composer-camera]:not([disabled])"))'), '临时相机再次就绪');
    await evaluate(port, 'globalThis.__nanfengAcceptanceCameraStream=document.querySelector("[data-composer-camera-video]").srcObject');
    await click(port, '[data-action="capture-composer-camera"]');
    const captured = await until(async () => {
      const record = await invoke(port, 'read_desktop_temporary_conversation');
      return record?.temporaryId === temporaryId && record.draftAttachmentIds?.length === 1 ? record : null;
    }, '临时图片私有落盘');
    assert.equal(captured.attachments.length, 1);
    assert.equal(captured.attachments[0].mimeType, 'image/png');
    assert.ok(captured.attachments[0].sha256 && captured.attachments[0].byteCount > 0);
    assert.deepEqual(captured.draftAttachmentIds, [captured.attachments[0].id]);
    assert.equal(await evaluate(port, 'globalThis.__nanfengAcceptanceCameraStream.getTracks().every(track=>track.readyState==="ended")'), true);
    assert.equal((await invoke(port, 'read_desktop_conversation_draft', { workspaceId, conversationId: null }))?.attachments?.length || 0, 0);
    assert.deepEqual((await invoke(port, 'read_desktop_workspace', { workspaceId }))
      .exchange.conversations.map(item => item.id).sort(), ordinaryConversationIds);
    assert.equal(providerRequests.length, 0);
    const screenshot = (await cdp(port, 'Page.captureScreenshot', { format: 'png', captureBeyondViewport: false }))?.data;
    assert.ok(screenshot);
    await writeFile(join(root, 'w08a-temporary-camera.png'), Buffer.from(screenshot, 'base64'));
    await until(() => evaluate(port, 'Boolean(document.querySelector("[data-action=save-local-message]:not([disabled])"))'), '纯图片临时发送按钮');
    await click(port, '[data-action="save-local-message"]');
    const sent = await until(async () => {
      const record = await invoke(port, 'read_desktop_temporary_conversation');
      return record?.messages?.length === 1 && record.draftAttachmentIds.length === 0 ? record : null;
    }, '临时图片消息落盘');
    assert.deepEqual(sent.messages[0].attachmentIds, captured.draftAttachmentIds);
    assert.equal(sent.messages[0].text, '');
    assert.equal(providerRequests.length, 0);
    stop();
    port = await start(true);
    await until(() => evaluate(port, 'Boolean(window.__TAURI__?.core?.invoke)'), '临时相机同根冷启');
    const cold = await invoke(port, 'read_desktop_temporary_conversation');
    assert.equal(cold?.temporaryId, temporaryId);
    assert.deepEqual(cold.draftAttachmentIds, []);
    assert.deepEqual(cold.attachments, captured.attachments);
    assert.deepEqual(cold.messages, sent.messages);
    assert.deepEqual((await invoke(port, 'read_desktop_workspace', { workspaceId }))
      .exchange.conversations.map(item => item.id).sort(), ordinaryConversationIds);
    assert.equal(await evaluate(port, 'Boolean(globalThis.__nanfengDesktopWorkState?.temporaryConversation)'), false,
      '冷启后普通界面不能自动切入临时聊天');
    await until(() => evaluate(port, 'Boolean(document.querySelector("[data-action=new-chat]"))'), '冷启新对话入口');
    await click(port, '[data-action="new-chat"]');
    await until(() => evaluate(port, 'Boolean(document.querySelector("[data-action=toggle-temporary-chat]"))'), '冷启临时聊天入口');
    await click(port, '[data-action="toggle-temporary-chat"]');
    await until(() => evaluate(port, `globalThis.__nanfengDesktopWorkState?.temporaryConversation?.temporaryId===${JSON.stringify(temporaryId)}`), '冷启临时聊天恢复可见');
    assert.equal(await evaluate(port, 'document.querySelectorAll(".chat-composer-attachments [data-action=remove-composer-attachment]").length'), 0);
    await until(() => evaluate(port, `Boolean(document.querySelector('.chat-scroll[data-temporary-transcript="true"] .chat-image-attachment[data-attachment-id="${captured.attachments[0].id}"] img'))`), '临时图片消息缩略图');
    await click(port, `.chat-scroll[data-temporary-transcript="true"] .chat-image-attachment[data-attachment-id="${captured.attachments[0].id}"]`);
    await until(() => evaluate(port, 'Boolean(globalThis.__nanfengDesktopWorkState?.imagePreview?.dataUrl?.startsWith("data:image/png;base64,"))'), '临时图片原图预览');
    assert.equal(providerRequests.length, 0);
    result = { candidateSha256: expectedHash, root, workspaceId, temporaryId, attachment: captured.attachments[0],
      cancelledWithoutDraft: true, tracksReleased: true, pictureOnlyLocalMessage: true,
      coldReadback: true, ordinaryIsolation: true,
      providerRequests: providerRequests.length, screenshot: join(root, 'w08a-temporary-camera.png'), pids };
  } else if (deepseekSearch || deepseekOffline) {
    const workspaceId = (await invoke(port, 'ensure_desktop_local_workspace')).summary.id;
    await until(() => evaluate(port, 'Boolean(document.querySelector("#chat-composer"))'), '原生普通输入框');
    await click(port, '[data-action="new-chat"]');
    await selectDeepseekFlash(port);
    await click(port, '[data-action="toggle-composer-add"]');
    const searchToggle = '[data-action="toggle-conversation-web-search"]';
    await until(() => evaluate(port, `Boolean(document.querySelector(${JSON.stringify(searchToggle)}))`), '联网开关');
    const targetSearch = deepseekSearch ? 'true' : 'false';
    if (await evaluate(port, `document.querySelector(${JSON.stringify(searchToggle)})?.getAttribute('aria-checked')`) !== targetSearch) {
      await click(port, searchToggle);
    }
    await until(() => evaluate(port, `document.querySelector(${JSON.stringify(searchToggle)})?.getAttribute('aria-checked') === ${JSON.stringify(targetSearch)}`), '联网选择已生效');
    await click(port, '[data-action="close-composer-add"]');
    await fillComposer(port, deepseekPrompt);
    await until(() => evaluate(port, 'document.querySelector(".chat-composer-route")?.textContent.includes("DeepSeek")'), 'DeepSeek 接收方可见');
    await until(() => evaluate(port, 'Boolean(document.querySelector("[data-action=save-local-message]:not([disabled])"))'), '普通发送可用');
    await click(port, '[data-action="save-local-message"]');
    const expectedAnswer = deepseekSearch ? 'DeepSeek 检索后的合成回答' : 'DeepSeek 未联网的合成回答';
    const completed = await until(async () => {
      const workspace = await invoke(port, 'read_desktop_workspace', { workspaceId });
      return workspace.exchange.conversations.find(conversation => conversation.messages.some(message =>
        message.role === 'assistant' && message.delivery === 'COMPLETE'
        && messageText(message).includes(expectedAnswer)));
    }, 'DeepSeek 回答完成').catch(async error => {
      const workspace = await invoke(port, 'read_desktop_workspace', { workspaceId });
      const ui = await evaluate(port, '({status:globalThis.__nanfengDesktopWorkState?.status,error:globalThis.__nanfengDesktopWorkState?.error,route:document.querySelector(".chat-composer-route")?.textContent})');
      throw new Error(`${error.message}: ${JSON.stringify({ root, requests: providerRequests, conversations: workspace.exchange.conversations.map(item => ({ id: item.id, messages: item.messages.map(message => ({ role: message.role, delivery: message.delivery, text: messageText(message).slice(0,180), errorCode: message.errorCode })) })), ui })}`);
    });
    assert.equal(completed.messages.some(message => message.role === 'assistant'
      && messageText(message).includes('https://example.test/deepseek-search')), deepseekSearch);
    assert.equal(providerRequests.filter(item => item.kind === 'answer').length, 1);
    assert.equal(providerRequests.find(item => item.kind === 'answer')?.model, 'deepseek-flash');
    stop();
    port = await start(true);
    await until(() => evaluate(port, 'Boolean(window.__TAURI__?.core?.invoke)'), 'DeepSeek 联网同根冷启');
    const cold = await readConversation(port, workspaceId, completed.id);
    assert.ok(cold.messages.some(message => message.role === 'assistant'
      && messageText(message).includes(expectedAnswer)));
    assert.equal(providerRequests.filter(item => item.kind === 'answer').length, 1);
    result = { candidateSha256: expectedHash, root, workspaceId, conversationId: completed.id,
      deepseekMessages: deepseekSearch, searchSourcePersisted: deepseekSearch, coldReadback: true,
      ordinaryRequestCount: 1, pids };
  } else if (manualIncompatiblePdf) {
    const workspaceId = (await invoke(port, 'ensure_desktop_local_workspace')).summary.id;
    const originalConversationCount = (await invoke(port, 'read_desktop_workspace', { workspaceId })).exchange.conversations.length;
    const selectedPath = join(root, 'w06-incompatible-synthetic.pdf');
    await writeFile(selectedPath, '%PDF-1.4\n1 0 obj\n<< /Type /Catalog >>\nendobj\n%%EOF\n');
    const attachment = await invoke(port, 'import_desktop_conversation_attachment',
      { args: { workspaceId, selectedPath } });
    assert.equal(attachment.mimeType, 'application/pdf');
    const previewArgs = { workspaceId, conversationId: null, text: '阅读合成 PDF',
      attachmentIds: [attachment.id], modelId: 'DEEPSEEK_V4_FLASH',
      toneOverride: null, webSearchOverride: false };
    await assert.rejects(invoke(port, 'preview_desktop_ordinary_chat_egress', { args: previewArgs }),
      /不能处理本次附件/);
    assert.equal(providerRequests.length, 0);
    assert.equal((await invoke(port, 'read_desktop_workspace', { workspaceId })).exchange.conversations.length,
      originalConversationCount);
    await until(() => evaluate(port, 'Boolean(document.querySelector("#chat-composer"))'), '原生普通输入框');
    await click(port, '[data-action="new-chat"]');
    await selectDeepseekFlash(port);
    if (recoverIncompatiblePdf) {
      await click(port, '[data-action="toggle-composer-add"]');
      await click(port, '[data-action="toggle-conversation-web-search"]');
      await until(() => evaluate(port, 'globalThis.__nanfengDesktopWorkState?.conversationPreferences?.webSearchOverride === false'), '合成 PDF 关闭搜索');
      await click(port, '[data-action="toggle-composer-add"]');
    }
    await fillComposer(port, '阅读合成 PDF');
    const dropped = await evaluate(port, `(()=>{const target=document.querySelector('.chat-composer');const data=new DataTransfer();data.items.add(new File([${JSON.stringify('%PDF-1.4\n1 0 obj\n<< /Type /Catalog >>\nendobj\n%%EOF\n')}],'w06-incompatible-ui.pdf',{type:'application/pdf'}));const event=new Event('drop',{bubbles:true,cancelable:true});Object.defineProperty(event,'dataTransfer',{value:data});target.dispatchEvent(event);return event.defaultPrevented})()`);
    assert.equal(dropped, true);
    await until(() => evaluate(port, 'document.querySelectorAll(".chat-composer-attachments [data-action=remove-composer-attachment]").length===1'), '合成 PDF 卡片');
    await until(() => evaluate(port, 'document.querySelector(".chat-composer-route")?.textContent.includes("不能处理本次附件")'),
      '可见附件能力拒绝', 5000).catch(async error => {
      const ui = await evaluate(port, '({route:document.querySelector(".chat-composer-route")?.textContent,model:globalThis.__nanfengDesktopWorkState?.pendingNewConversationModelId,attachments:globalThis.__nanfengDesktopWorkState?.composerAttachments?.map(item=>item.mimeType),sendDisabled:document.querySelector("[data-action=save-local-message]")?.disabled})');
      throw new Error(`${error.message}: ${JSON.stringify(ui)}`);
    });
    assert.equal(await evaluate(port, 'document.querySelector("[data-action=save-local-message]")?.disabled'), true);
    assert.equal(providerRequests.length, 0);
    const rejectedDraft = await evaluate(port, '({text:globalThis.__nanfengDesktopWorkState.composerDraft,attachments:globalThis.__nanfengDesktopWorkState.composerAttachments.map(item=>({id:item.id,sha256:item.sha256,mimeType:item.mimeType}))})');
    stop();
    port = await start(true);
    await until(() => evaluate(port, 'Boolean(window.__TAURI__?.core?.invoke)'), '同根冷启 IPC');
    await assert.rejects(invoke(port, 'preview_desktop_ordinary_chat_egress', { args: previewArgs }),
      /不能处理本次附件/);
    assert.equal(providerRequests.length, 0);
    let recoveredConversationId = null;
    if (recoverIncompatiblePdf) {
      await until(() => evaluate(port, 'Boolean(globalThis.__nanfengDesktopWorkState?.runtimeInfo?.dataAreasReady)'), '冷启双区就绪');
      await until(() => evaluate(port, 'document.querySelector("#chat-composer")?.value === "阅读合成 PDF"'), '冷启拒绝后草稿');
      assert.deepEqual(await evaluate(port, '({text:globalThis.__nanfengDesktopWorkState.composerDraft,attachments:globalThis.__nanfengDesktopWorkState.composerAttachments.map(item=>({id:item.id,sha256:item.sha256,mimeType:item.mimeType}))})'), rejectedDraft);
      await click(port, '[data-action="toggle-p6g-model-picker"]');
      await click(port, '[data-action="select-p6g-auto"]');
      await until(() => evaluate(port, 'Boolean(document.querySelector("[data-action=save-local-message]:not([disabled])")) && document.querySelector(".chat-composer-route")?.dataset.routeReady === "true"'), '用户改选后接收方就绪');
      assert.equal(providerRequests.length, 0);
      assert.deepEqual(await evaluate(port, '({text:globalThis.__nanfengDesktopWorkState.composerDraft,attachments:globalThis.__nanfengDesktopWorkState.composerAttachments.map(item=>({id:item.id,sha256:item.sha256,mimeType:item.mimeType}))})'), rejectedDraft);
      await click(port, '[data-action="save-local-message"]');
      const completed = await until(async () => {
        const projection = await invoke(port, 'read_desktop_workspace', { workspaceId });
        return projection.exchange.conversations.find(item => item.messages.some(message => message.role === 'assistant' && message.delivery === 'COMPLETE' && messageText(message).includes(answerFirst + answerSecond)));
      }, '兼容模型完整回答');
      recoveredConversationId = completed.id;
      assert.equal(providerRequests.length, 1);
      assert.equal(providerRequests[0].model, 'openai/gpt-5.6-terra');
      assert.equal(providerRequests[0].pdfFilePresent, true);
      stop();
      port = await start(true);
      await until(() => evaluate(port, 'Boolean(window.__TAURI__?.core?.invoke)'), '回答同根冷启');
      assert.deepEqual((await readConversation(port, workspaceId, completed.id)).messages, completed.messages);
      assert.equal(providerRequests.length, 1);
    }
    result = { candidateSha256: expectedHash, root, workspaceId, attachmentId: attachment.id,
      unsupportedModel: previewArgs.modelId, rejectedBeforeEgress: true, coldReadback: true,
      recoveredConversationId, rejectedDraftPreserved: recoverIncompatiblePdf,
      providerRequests: providerRequests.length, pids };
  } else {
  if (fromWork) {
    await invoke(port, 'ensure_desktop_local_workspace', {}, 'CHAT');
    await until(() => evaluate(port, 'Boolean(document.querySelector("#chat-composer"))'), '初始对话输入框');
    await click(port, '[data-action="new-chat"]');
    await until(() => evaluate(port, 'Boolean(document.querySelector("[data-action=show-work]"))'), '工作区入口').catch(async error => {
      const snapshot = await evaluate(port, '({area:globalThis.__nanfengDesktopWorkState?.dataArea,pane:globalThis.__nanfengDesktopWorkState?.pane,ready:globalThis.__nanfengDesktopWorkState?.runtimeInfo?.dataAreasReady,body:document.body?.textContent?.slice(0,350),actions:[...document.querySelectorAll("[data-action]")].map(n=>n.dataset.action).filter(x=>x.includes("work")).slice(0,12)})');
      throw new Error(`${error.message}: ${JSON.stringify(snapshot)}`);
    });
    await click(port, '[data-action="show-work"]');
    await until(() => evaluate(port, 'Boolean(document.querySelector(".chat-sidebar[aria-label=工作导航]"))'), '进入工作区');
  }
  const workspaceId = (await invoke(port, 'ensure_desktop_local_workspace')).summary.id;
  let memoryConflictSeed = null;
  if (memorySuggestionConflict || explicitMemoryConflict) {
    const seedTitle = explicitMemoryConflict ? '工作流程偏好' : '个人项目';
    const seedBody = explicitMemoryConflict ? '旧合成工作偏好' : '合成旧项目偏好';
    memoryConflictSeed = await invoke(port, 'mutate_desktop_domain', { args: {
      intentId: 'synthetic-memory-topic-conflict-seed', workspaceId, entity: 'memory', action: 'create',
      fields: { title: seedTitle, body: seedBody, scope: 'GLOBAL', scopeId: null },
    } });
    await evaluate(port, 'location.reload()');
    await until(() => evaluate(port, `Boolean(globalThis.__nanfengDesktopWorkState?.current?.exchange?.memory?.some(item => item.id === ${JSON.stringify(memoryConflictSeed.objectId)})) && Boolean(globalThis.__nanfengDesktopWorkState?.runtimeInfo?.dataAreasReady)`), '同主题记忆夹具回读');
    await evaluate(port, 'new Promise(resolve => requestAnimationFrame(() => requestAnimationFrame(resolve)))');
  }
  await until(() => evaluate(port, `globalThis.__nanfengDesktopWorkState?.current?.summary?.id === ${JSON.stringify(workspaceId)}`), '真实工作区投影已进入界面');
  let configuredRevision = null;
  if (configureModelUi) {
    const before = (await invoke(port, 'read_desktop_model_service_settings')).find(item => item.providerId === configuredProviderId);
    assert.ok(before);
    await click(port, '[data-action="show-settings"]');
    await click(port, '[data-action="open-settings-page"][data-page="model"]');
    await click(port, '[data-action="open-settings-page"][data-page="model-configuration"]');
    await click(port, `[data-action="select-model-service-provider"][data-provider-id="${configuredProviderId}"]`);
    await until(() => evaluate(port, 'Boolean(document.querySelector("#model-service-api-key"))'), '模型设置输入框');
    if (!before.enabled) await click(port, '[data-action="toggle-model-service-enabled"]');
    assert.equal(await evaluate(port, 'document.querySelector("#model-service-api-key")?.type'), 'password');
    await evaluate(port, `(()=>{const field=document.querySelector('#model-service-api-key');field.value=${JSON.stringify(configuredKey)};field.dispatchEvent(new Event('input',{bubbles:true}));return true})()`);
    await click(port, '[data-action="save-model-service-settings"]');
    const configured = await until(async () => {
      const item = (await invoke(port, 'read_desktop_model_service_settings')).find(value => value.providerId === configuredProviderId);
      return item?.revision > before.revision && item.enabled && item.credentialStored ? item : null;
    }, '设置页保存合成模型').catch(async error => {
      const ui = await evaluate(port, '({error:globalThis.__nanfengDesktopWorkState?.modelSettingsError,notice:globalThis.__nanfengDesktopWorkState?.modelSettingsNotice,saving:globalThis.__nanfengDesktopWorkState?.modelSettingsSaving,status:globalThis.__nanfengDesktopWorkState?.status})');
      throw new Error(`${error.message}: ${JSON.stringify(ui)}`);
    });
    configuredRevision = configured.revision;
    await until(() => evaluate(port, 'Boolean(document.querySelector("[data-action=close-dialog]"))'), '模型保存确认');
    assert.equal(await evaluate(port, 'document.querySelector("#model-service-api-key")?.type'), 'password');
    await click(port, '[data-action="close-dialog"]');
    await click(port, '[data-action="show-chat"]');
    await until(() => evaluate(port, 'Boolean(document.querySelector("#chat-composer"))'), '返回新对话');
  }
  let historyIds = null;
  if (historyContext) {
    const suffix = Date.now().toString(36);
    const active = await invoke(port, 'mutate_desktop_domain', { args: {
      intentId: `w12-history-active-${suffix}`, workspaceId, entity: 'conversation', action: 'create',
      fields: { title: 'W12 合成历史记录', projectId: null, firstMessage: `${prompt} W12_HISTORY_ALPHA 仅为合成历史正文` },
    } });
    const archived = await invoke(port, 'mutate_desktop_domain', { args: {
      intentId: `w12-history-archived-${suffix}`, workspaceId, entity: 'conversation', action: 'create',
      fields: { title: 'W12 合成归档记录', projectId: null, firstMessage: `${prompt} W12_ARCHIVED_SECRET 不得进入请求` },
    } });
    await invoke(port, 'mutate_desktop_domain', { args: {
      intentId: `w12-history-archive-${suffix}`, workspaceId, entity: 'conversation', action: 'archive',
      objectId: archived.objectId, expectedRevision: archived.revision, fields: {},
    } });
    historyIds = { active: active.objectId, archived: archived.objectId };
  }
  let contextIds = null;
  if (contextMode) {
    if (profileSettingsFlow) {
      await click(port, '[data-action="show-settings"]');
      await until(() => evaluate(port, 'Boolean(document.querySelector("#personalization-occupation"))'), '个性化设置页');
      if (personalizationRace) {
        personalizationDbLock = await holdPersonalizationWrite();
        await fillField(port, '#personalization-occupation', 'W12_PROFILE_OLD');
        assert.equal(await evaluate(port, 'document.querySelector("[data-action=save-personalization]")?.disabled'), false, '首次资料编辑须启用保存');
        await click(port, '[data-action="save-personalization"]');
      }
      await fillField(port, '#personalization-occupation', 'W12_PROFILE_OCCUPATION');
      await fillField(port, '#personalization-instructions', 'W12_PROFILE_INSTRUCTION');
      if (personalizationRace) {
        await new Promise(resolveWait => setTimeout(resolveWait, 250));
        personalizationDbLock.stdin.write('\n');
        await new Promise((resolveExit, rejectExit) => personalizationDbLock.once('exit', code => code === 0 ? resolveExit() : rejectExit(new Error(`合成数据库写锁退出 ${code}`))));
        personalizationDbLock = null;
        await until(async () => (await invoke(port, 'read_desktop_app_settings')).product.occupation === 'W12_PROFILE_OLD', '首个设置写入完成');
        await until(() => evaluate(port, `globalThis.__nanfengDesktopWorkState?.productSettings?.occupation === 'W12_PROFILE_OLD'`), '首个保存回执已回到前端');
        assert.equal(await evaluate(port, 'document.querySelector("#personalization-occupation")?.value'), 'W12_PROFILE_OCCUPATION', '迟到的首个保存结果不得覆盖新稿');
        assert.equal(await evaluate(port, 'document.querySelector("[data-action=save-personalization]")?.disabled'), false, '新稿仍可保存');
      }
      await click(port, '[data-action="save-personalization"]');
      await until(async () => {
        const settings = await invoke(port, 'read_desktop_app_settings');
        return settings.product.occupation === 'W12_PROFILE_OCCUPATION'
          && settings.product.customInstructions === 'W12_PROFILE_INSTRUCTION';
      }, '设置页保存合成用户资料');
      if (summaryPause) {
        await click(port, '[data-action="open-settings-page"][data-page="memory-overview"]');
        await click(port, '[data-action="ask-disable-memory-summary"]');
        await click(port, '[data-action="confirm-disable-memory-summary"]');
        await until(async () => {
          const product = (await invoke(port, 'read_desktop_app_settings')).product;
          return product.memoryEnabled && !product.memoryRetrievalEnabled;
        }, '仅关闭记忆摘要');
      } else {
        const memorySwitch = '[data-action="toggle-product-setting"][data-key="memoryEnabled"]';
        if (await evaluate(port, `document.querySelector(${JSON.stringify(memorySwitch)})?.getAttribute('aria-checked') === 'true'`)) {
          await click(port, memorySwitch);
        }
        await until(async () => {
          const product = (await invoke(port, 'read_desktop_app_settings')).product;
          return !product.memoryEnabled && !product.memoryRetrievalEnabled;
        }, '关闭长期 Memory');
      }
      await click(port, '[data-action="show-chat"]');
      await until(() => evaluate(port, 'Boolean(document.querySelector("#chat-composer"))'), '返回聊天发送');
    } else {
      const settings = await invoke(port, 'read_desktop_app_settings');
      await invoke(port, 'save_desktop_app_settings', { args: {
        appearance: settings.appearance,
        product: { ...settings.product, memoryEnabled: contextEnabled, memoryRetrievalEnabled: contextEnabled, historyLibraryEnabled: contextEnabled },
        expectedRevision: settings.revision,
      } });
    }
    const suffix = Date.now().toString(36);
    let projectId = null;
    if (contextProject) {
      if (uiWorkItems) {
        await click(port, '[data-action="show-projects"]');
        await click(port, '[data-action="new-project"]');
        await fillField(port, '#project-title', 'W12 合成项目');
        await fillField(port, '#project-description', '仅用于隔离验收');
        await click(port, '[data-action="save-project"]');
        projectId = await until(async () => (await invoke(port, 'read_desktop_workspace', { workspaceId }))
          .exchange.projects.find(item => item.title === 'W12 合成项目')?.id, '界面项目写入');
      } else {
        const project = await invoke(port, 'mutate_desktop_domain', { args: { intentId: `w12-project-${suffix}`,
          workspaceId, entity: 'project', action: 'create', fields: { title: 'W12 合成项目', description: '仅用于隔离验收' } } });
        projectId = project.objectId;
      }
      assert.ok(projectId);
    }
    let knowledge;
    let memory;
    if (uiWorkItems) {
      await until(() => evaluate(port, 'Boolean(document.querySelector("[data-action=show-knowledge]"))'), '知识库入口');
      await click(port, '[data-action="show-knowledge"]');
      await click(port, '[data-action="new-knowledge"]');
      await until(() => evaluate(port, `Boolean(document.querySelector('#knowledge-project option[value="${projectId}"]'))`), '知识项目选项');
      await fillField(port, '#knowledge-project', projectId);
      await fillField(port, '#knowledge-title', `${prompt} 资料`);
      await fillField(port, '#knowledge-body', 'W12_KNOWLEDGE_ALPHA 仅为合成项目资料。');
      await click(port, '[data-action="save-knowledge"]');
      knowledge = await until(async () => (await invoke(port, 'read_desktop_workspace', { workspaceId }))
        .exchange.knowledge.find(item => item.title === `${prompt} 资料`), '界面项目知识写入');
      await click(port, '[data-action="show-memory"]');
      await until(() => evaluate(port, 'Boolean(document.querySelector("[data-action=new-memory]"))'), '记忆新建入口').catch(async error => {
        const ui = await evaluate(port, '({pane:globalThis.__nanfengDesktopWorkState?.pane,area:globalThis.__nanfengDesktopWorkState?.dataArea,dialog:globalThis.__nanfengDesktopWorkState?.dialog?.kind,error:globalThis.__nanfengDesktopWorkState?.error,body:document.body?.textContent?.slice(0,400)})');
        throw new Error(`${error.message}: ${JSON.stringify(ui)}`);
      });
      if (uiMemorySensitive) {
        const syntheticSensitiveBody = 'api key: synthetic-w12-secret';
        await click(port, '[data-action="new-memory"]');
        await fillField(port, '#memory-project', projectId);
        await fillField(port, '#memory-body', syntheticSensitiveBody);
        await click(port, '[data-action="save-memory"]');
        await until(() => evaluate(port, 'String(globalThis.__nanfengDesktopWorkState?.error || "").includes("高敏内容")'), '界面拒绝高敏 Memory');
        const rejected = await invoke(port, 'read_desktop_workspace', { workspaceId });
        assert.ok(rejected.exchange.memory.every(item => item.body !== syntheticSensitiveBody), '被拒正文不得写入 Memory');
        const safeError = await evaluate(port, 'String(globalThis.__nanfengDesktopWorkState?.error || "")');
        assert.ok(!safeError.includes('synthetic-w12-secret'), '拒绝提示不得回显敏感正文');
        await click(port, '[data-action="close-dialog"]');
      }
      await click(port, '[data-action="new-memory"]');
      await fillField(port, '#memory-project', projectId);
      await fillField(port, '#memory-body', `${prompt} W12_MEMORY_ALPHA 仅为合成记忆。`);
      await click(port, '[data-action="save-memory"]');
      memory = await until(async () => (await invoke(port, 'read_desktop_workspace', { workspaceId }))
        .exchange.memory.find(item => item.body.includes('W12_MEMORY_ALPHA')), '界面项目记忆写入');
      assert.equal(knowledge.projectId, projectId);
      assert.equal(memory.scopeId, projectId);
    } else {
      const createdKnowledge = await invoke(port, 'mutate_desktop_domain', { args: { intentId: `w12-knowledge-${suffix}`,
        workspaceId, entity: 'knowledge', action: 'create', fields: {
          title: `${prompt} 资料`, body: 'W12_KNOWLEDGE_ALPHA 仅为合成项目资料。', tags: [], scope: contextProject ? 'PROJECT' : 'GLOBAL', projectId,
        } } });
      const createdMemory = await invoke(port, 'mutate_desktop_domain', { args: { intentId: `w12-memory-${suffix}`,
        workspaceId, entity: 'memory', action: 'create', fields: {
          body: `${prompt} W12_MEMORY_ALPHA 仅为合成记忆。`, scope: contextProject ? 'PROJECT' : 'GLOBAL', scopeId: projectId,
        } } });
      knowledge = { id: createdKnowledge.objectId };
      memory = { id: createdMemory.objectId };
    }
    let targetProjectId = projectId;
    if (contextOtherProject) {
      const otherProject = await invoke(port, 'mutate_desktop_domain', { args: { intentId: `w12-other-project-${suffix}`,
        workspaceId, entity: 'project', action: 'create', fields: { title: 'W12 另一个合成项目', description: '无项目 A 资料' } } });
      targetProjectId = otherProject.objectId;
      assert.ok(targetProjectId && targetProjectId !== projectId);
    }
    contextIds = { project: projectId, targetProject: targetProjectId, knowledge: knowledge.id, memory: memory.id };
    assert.ok(contextIds.knowledge && contextIds.memory);
    if (contextProject) {
      if (!uiWorkItems) {
        await click(port, '[data-action="show-chat"]');
        await until(() => evaluate(port, 'Boolean(document.querySelector(".chat-sidebar[aria-label=对话导航]"))'), '返回 CHAT');
        await click(port, '[data-action="new-chat"]');
        await until(() => evaluate(port, 'Boolean(document.querySelector("[data-action=show-work]"))'), 'CHAT 工作模式入口');
        await click(port, '[data-action="show-work"]');
        await until(() => evaluate(port, 'Boolean(document.querySelector(".chat-sidebar[aria-label=工作导航]"))'), '刷新 WORK 项目');
      }
      await until(() => evaluate(port, `globalThis.__nanfengDesktopWorkState?.current?.exchange?.projects?.some(item=>item.id===${JSON.stringify(projectId)})`), 'WORK 项目读取完成');
      await click(port, '[data-action="show-projects"]');
      await until(() => evaluate(port, 'globalThis.__nanfengDesktopWorkState?.pane === "projects"'), '项目列表页面');
      await until(() => evaluate(port, `Boolean(document.querySelector('[data-action="new-work-chat"][data-project-id="${targetProjectId}"]'))`), '项目工作对话入口').catch(async error => {
        const snapshot = await evaluate(port, '({area:globalThis.__nanfengDesktopWorkState?.dataArea,pane:globalThis.__nanfengDesktopWorkState?.pane,projects:globalThis.__nanfengDesktopWorkState?.current?.exchange?.projects?.map(x=>({id:x.id,title:x.title})),visible:document.querySelector(".work-projects-canvas")?.textContent?.slice(0,250)})');
        throw new Error(`${error.message}: ${JSON.stringify(snapshot)}`);
      });
      await click(port, `[data-action="new-work-chat"][data-project-id="${targetProjectId}"]`);
      await until(() => evaluate(port, `globalThis.__nanfengDesktopWorkState?.selectedWorkProjectId === ${JSON.stringify(targetProjectId)}`), '项目会话范围');
    }
  }
  await until(() => evaluate(port, 'Boolean(document.querySelector("#chat-composer"))'), '新对话输入框');
  await click(port, '[data-action="new-chat"]');
  await click(port, '[data-action="toggle-composer-add"]');
  await until(() => evaluate(port, 'Boolean(document.querySelector("[data-action=toggle-conversation-web-search]"))'), '联网偏好');
  const checked = await evaluate(port, 'document.querySelector("[data-action=toggle-conversation-web-search]")?.getAttribute("aria-checked")');
  const targetWebSearch = withSources ? 'true' : 'false';
  if (checked === targetWebSearch) {
    await click(port, '[data-action="toggle-conversation-web-search"]');
    await until(() => evaluate(port, `document.querySelector('[data-action=toggle-conversation-web-search]')?.getAttribute('aria-checked') === ${JSON.stringify(checked === 'true' ? 'false' : 'true')}`), '联网搜索先切换');
  }
  await click(port, '[data-action="toggle-conversation-web-search"]');
  await until(() => evaluate(port, `document.querySelector('[data-action=toggle-conversation-web-search]')?.getAttribute('aria-checked') === ${JSON.stringify(targetWebSearch)}`), '联网搜索选择');
  await click(port, '[data-action="close-composer-add"]');
  if (manualTerra) await selectVisibleDailyModel(port, 'GPT_5_6_TERRA');
  if (!attachmentInputs) await until(async () => (await invoke(port, 'read_desktop_conversation_draft',
    { workspaceId, conversationId: null }))?.webSearchOverride === withSources, '新对话搜索选择落盘', 10000).catch(async error => {
    const draft = await invoke(port, 'read_desktop_conversation_draft', { workspaceId, conversationId: null });
    const ui = await evaluate(port, '({area:globalThis.__nanfengDesktopWorkState?.dataArea,selectedConversationId:globalThis.__nanfengDesktopWorkState?.selectedConversationId,setting:globalThis.__nanfengDesktopWorkState?.conversationPreferences?.webSearchOverride,status:globalThis.__nanfengDesktopWorkState?.status,error:globalThis.__nanfengDesktopWorkState?.error})');
    throw new Error(`${error.message}: ${JSON.stringify({ draft, ui })}`);
  });
  let attachmentDraft = null;
  let cameraDraft = null;
  if (attachmentInputs) {
    const pasted = await evaluate(port, `(()=>{const input=document.querySelector('#chat-composer');const data=new DataTransfer();data.items.add(new File(['W08 synthetic paste'],'w08-paste.txt',{type:'text/plain'}));const event=new Event('paste',{bubbles:true,cancelable:true});Object.defineProperty(event,'clipboardData',{value:data});input.dispatchEvent(event);return event.defaultPrevented})()`);
    assert.equal(pasted, true);
    await until(() => evaluate(port, 'document.querySelectorAll(".chat-composer-attachments [data-action=remove-composer-attachment]").length===1'), '粘贴附件卡');
    const dropped = await evaluate(port, `(()=>{const target=document.querySelector('.chat-composer');const data=new DataTransfer();data.items.add(new File([${JSON.stringify(attachmentPdf ? syntheticPdf : 'W08 synthetic drop')}],${JSON.stringify(attachmentPdf ? 'w08-drop.pdf' : 'w08-drop.txt')},{type:${JSON.stringify(attachmentPdf ? 'application/pdf' : 'text/plain')}}));const event=new Event('drop',{bubbles:true,cancelable:true});Object.defineProperty(event,'dataTransfer',{value:data});target.dispatchEvent(event);return event.defaultPrevented})()`);
    assert.equal(dropped, true);
    await until(() => evaluate(port, 'document.querySelectorAll(".chat-composer-attachments [data-action=remove-composer-attachment]").length===2'), '拖放附件卡');
    attachmentDraft = await until(async () => {
      const draft = await invoke(port, 'read_desktop_conversation_draft', { workspaceId, conversationId: null });
      return draft?.attachments?.length === 2 ? draft : null;
    }, '双附件草稿落盘');
    assert.deepEqual(attachmentDraft.attachments.map(item => item.displayName).sort(), [attachmentPdf ? 'w08-drop.pdf' : 'w08-drop.txt', 'w08-paste.txt']);
    assert.equal(attachmentDraft.webSearchOverride, false, '本次明确关闭搜索须随附件草稿保存');
    if (manualTerra) assert.equal(attachmentDraft.modelId, 'GPT_5_6_TERRA', '手选模型须随附件草稿保存');
  }
  if (cameraInput) {
    await click(port, '[data-action="toggle-composer-add"]');
    await click(port, '[data-action="open-composer-camera"]');
    await until(() => evaluate(port, 'Boolean(document.querySelector("[data-composer-camera-video]"))'), '合成相机预览');
    await until(() => evaluate(port, 'Boolean(document.querySelector("[data-action=capture-composer-camera]:not([disabled])"))'), '合成相机画面就绪');
    assert.equal(await evaluate(port, 'document.querySelector("[data-composer-camera-video]")?.srcObject?.getAudioTracks?.().length'), 0);
    await evaluate(port, 'globalThis.__nanfengAcceptanceCameraStream=document.querySelector("[data-composer-camera-video]").srcObject');
    await click(port, '[data-action="close-composer-camera"]');
    await until(() => evaluate(port, '!document.querySelector("[data-composer-camera-video]")'), '取消相机预览');
    assert.equal(await evaluate(port, 'globalThis.__nanfengAcceptanceCameraStream.getTracks().every(track=>track.readyState==="ended")'), true, '取消必须释放合成相机轨道');
    assert.equal((await invoke(port, 'read_desktop_conversation_draft', { workspaceId, conversationId: null }))?.attachments?.length || 0, 0);
    await click(port, '[data-action="toggle-composer-add"]');
    await click(port, '[data-action="open-composer-camera"]');
    await until(() => evaluate(port, 'Boolean(document.querySelector("[data-action=capture-composer-camera]:not([disabled])"))'), '再次打开合成相机');
    await evaluate(port, 'globalThis.__nanfengAcceptanceCameraStream=document.querySelector("[data-composer-camera-video]").srcObject');
    await click(port, '[data-action="capture-composer-camera"]');
    cameraDraft = await until(async () => {
      const draft = await invoke(port, 'read_desktop_conversation_draft', { workspaceId, conversationId: null });
      return draft?.attachments?.length === 1 ? draft : null;
    }, '合成相机私有草稿落盘').catch(async error => {
      const draft = await invoke(port, 'read_desktop_conversation_draft', { workspaceId, conversationId: null });
      const ui = await evaluate(port, '({cameraOpen:globalThis.__nanfengDesktopWorkState?.cameraCaptureOpen,cameraError:globalThis.__nanfengDesktopWorkState?.cameraCaptureError,attachments:globalThis.__nanfengDesktopWorkState?.composerAttachments,status:globalThis.__nanfengDesktopWorkState?.status,error:globalThis.__nanfengDesktopWorkState?.error,dialog:document.querySelector(".camera-capture-dialog")?.textContent?.slice(0,300)})');
      throw new Error(`${error.message}: ${JSON.stringify({ draft, ui })}`);
    });
    assert.equal(cameraDraft.attachments[0].mimeType, 'image/png');
    assert.ok(cameraDraft.attachments[0].sha256 && cameraDraft.attachments[0].byteCount > 0);
    assert.equal(await evaluate(port, 'globalThis.__nanfengAcceptanceCameraStream.getTracks().every(track=>track.readyState==="ended")'), true, '拍照后必须释放合成相机轨道');
  }
  await fillComposer(port, prompt);
  const disclosedRecipient = await evaluate(port, 'document.querySelector(".chat-composer-route[data-route-ready=true]")?.textContent');
  assert.ok(disclosedRecipient?.includes(' · ') && !disclosedRecipient.includes('自动选择'),
    '发送按钮可用前必须持续显示完整接收方与模型');
  if (manualTerra) assert.ok(disclosedRecipient.includes('OpenRouter · GPT-5.6 Terra'),
    '手选模型须在发送前显示精确接收方');
  const editedRoute = await evaluate(port, `(()=>{const field=document.querySelector('#chat-composer');field.value=${JSON.stringify(`${prompt} 再次编辑`)};field.dispatchEvent(new Event('input',{bubbles:true}));return {ready:document.querySelector('.chat-composer-route')?.dataset.routeReady,disabled:document.querySelector('[data-action=save-local-message]')?.disabled}})()`);
  assert.deepEqual(editedRoute, { ready: 'false', disabled: true }, '修改草稿应立即撤销旧接收方授权');
  await fillComposer(port, prompt);
  assert.equal(await evaluate(port, 'document.querySelector(".chat-composer-route[data-route-ready=true]")?.textContent'), disclosedRecipient);
  const routeScreenshot = join(root, 'w06-visible-recipient-before-send.png');
  const routeImage = (await cdp(port, 'Page.captureScreenshot', { format: 'png', captureBeyondViewport: false }))?.data;
  assert.ok(routeImage);
  await writeFile(routeScreenshot, Buffer.from(routeImage, 'base64'));
  await until(async () => {
    const draft = await invoke(port, 'read_desktop_conversation_draft', { workspaceId, conversationId: null });
    return draft?.text === prompt && draft.webSearchOverride === withSources;
  }, '新对话正文与搜索选择配对落盘', 5000);
  if (imeComposer) {
    await evaluate(port, `(()=>{const field=document.querySelector('#chat-composer');field.focus();globalThis.__nfaiComposingField=field;field.dispatchEvent(new CompositionEvent('compositionstart',{bubbles:true}));field.value=${JSON.stringify(prompt.slice(0, -1))};field.dispatchEvent(new InputEvent('input',{bubbles:true,isComposing:true}));return true})()`);
    await until(() => evaluate(port, 'document.querySelector(".chat-composer-route")?.dataset.routeReady === "true"'), '组词中的新接收方预览回读');
    assert.equal(await evaluate(port, 'globalThis.__nfaiComposingField.isConnected && document.querySelector("#chat-composer") === globalThis.__nfaiComposingField'), true, '预览回包不得替换正在组词的输入节点');
    await evaluate(port, `(()=>{const field=globalThis.__nfaiComposingField;field.value=${JSON.stringify(prompt)};field.dispatchEvent(new InputEvent('input',{bubbles:true,isComposing:true}));field.dispatchEvent(new CompositionEvent('compositionend',{bubbles:true}));return true})()`);
    await until(() => evaluate(port, 'document.querySelector(".chat-composer-route")?.dataset.routeReady === "true"'), '完整候选词接收方回读');
    const imeKeys = await evaluate(port, `(()=>{const field=document.querySelector('#chat-composer');field.dispatchEvent(new CompositionEvent('compositionstart',{bubbles:true,data:'合'}));const during=new KeyboardEvent('keydown',{key:'Enter',bubbles:true,cancelable:true});field.dispatchEvent(during);field.dispatchEvent(new CompositionEvent('compositionend',{bubbles:true,data:'合'}));const after=new KeyboardEvent('keydown',{key:'Enter',bubbles:true,cancelable:true});field.dispatchEvent(after);return {duringPrevented:during.defaultPrevented,afterPrevented:after.defaultPrevented,value:field.value}})()`);
    assert.deepEqual(imeKeys, { duringPrevented: false, afterPrevented: false, value: prompt }, '中文输入法确认 Enter 不得触发发送或清稿');
    await new Promise(resolveWait => setTimeout(resolveWait, 150));
    assert.equal(providerRequests.length, 0, '输入法确认不能发起 Provider 请求');
    const imeDraft = await invoke(port, 'read_desktop_conversation_draft', { workspaceId, conversationId: null });
    assert.equal(imeDraft?.text, prompt, '输入法确认后新对话草稿仍在');
  }
  if (imeComposer) {
    const sentByEnter = await evaluate(port, `(()=>{const field=document.querySelector('#chat-composer');const key=new KeyboardEvent('keydown',{key:'Enter',bubbles:true,cancelable:true});field.dispatchEvent(key);return key.defaultPrevented})()`);
    assert.equal(sentByEnter, true, '输入法完成后普通 Enter 应继续发送');
  } else {
    await click(port, '[data-action="save-local-message"]');
  }
  let partialVisible;
  if (withSources || explicitMemoryCommand) {
    await until(() => providerRequests.some(item => item.kind === 'answer'), '联网请求已接收', 5000);
    await new Promise(resolveWait => setTimeout(resolveWait, 250));
    partialVisible = await evaluate(port, `Boolean(document.querySelector(".chat-scroll")?.textContent.includes(${JSON.stringify(answerFirst)}))`);
    assert.equal(partialVisible, false, '记忆结构化原文或未核实来源不得提前显示');
  } else {
    partialVisible = await until(() => evaluate(port, `document.querySelector(".chat-scroll")?.textContent.includes(${JSON.stringify(answerFirst)})`), '流式前半段', 5000);
    assert.equal(partialVisible, true);
  }
  let historyTitleFact = null;
  if (historyTitleRace) {
    const inFlight = await until(async () => {
      const projection = await invoke(port, 'read_desktop_workspace', { workspaceId });
      return projection.exchange.conversations.find(item => item.messages?.some(message => messageText(message) === prompt));
    }, '等待中会话');
    const oldTitle = inFlight.title;
    const newTitle = '合成回答等待中标题';
    if (historyTitleUi) {
      await click(port, `[data-action="open-conversation-rename"][data-id="${inFlight.id}"]`);
      await until(() => evaluate(port, 'Boolean(document.querySelector("#conversation-rename"))'), '可见标题编辑弹窗');
      await evaluate(port, `(()=>{const input=document.querySelector('#conversation-rename');input.value=${JSON.stringify(newTitle)};input.dispatchEvent(new Event('input',{bubbles:true}));return true})()`);
      await click(port, '[data-action="save-conversation-rename"]');
      await until(async () => (await readConversation(port, workspaceId, inFlight.id))?.title === newTitle, '可见重命名保存');
    } else {
      await invoke(port, 'mutate_desktop_domain', { args: {
        intentId: `w05-title-during-answer-${randomUUID()}`, workspaceId, entity: 'conversation',
        action: 'update', objectId: inFlight.id, expectedRevision: inFlight.revision,
        fields: { title: newTitle },
      } });
    }
    historyTitleFact = { oldTitle, newTitle, conversationId: inFlight.id };
    releaseHistoryAnswer();
  }
  let workEditorFact = null;
  if (workEditorStream) {
    await click(port, '[data-action="show-projects"]');
    await until(() => evaluate(port, 'Boolean(document.querySelector(".work-projects-canvas [data-action=new-project]"))'), '流式期间 WORK 项目入口');
    await click(port, '[data-action="new-project"]');
    await until(() => evaluate(port, 'Boolean(document.querySelector("#project-description"))'), '流式期间项目编辑弹窗');
    let projectId = null;
    if (workEditorKind !== 'project') {
      await fillField(port, '#project-title', '流式合成范围项目');
      await fillField(port, '#project-description', '知识与记忆隔离范围');
      await click(port, '[data-action="save-project"]');
      const seed = await until(async () => (await invoke(port, 'read_desktop_workspace', { workspaceId })).exchange.projects.find(item => item.title === '流式合成范围项目'), '流式编辑项目范围');
      projectId = seed.id;
      await click(port, `[data-action="show-${workEditorKind}"]`);
      await until(() => evaluate(port, `Boolean(document.querySelector('.work-${workEditorKind}-canvas [data-action=new-${workEditorKind}]'))`), '流式期间目标编辑入口');
      await click(port, `[data-action="new-${workEditorKind}"]`);
      await until(() => evaluate(port, `Boolean(document.querySelector('#${workEditorKind}-body'))`), '流式期间目标编辑弹窗');
    }
    const title = `流式期间合成${{ project: '项目', knowledge: '知识', memory: '记忆' }[workEditorKind]}`;
    const body = '流式完成后仍保留输入与光标';
    const titleSelector = `#${workEditorKind}-title`;
    const bodySelector = workEditorKind === 'project' ? '#project-description' : `#${workEditorKind}-body`;
    if (projectId) await fillField(port, `#${workEditorKind}-project`, projectId);
    await fillField(port, titleSelector, title);
    await fillField(port, bodySelector, body);
    if (workEditorKind === 'knowledge') await fillField(port, '#knowledge-tags', '流式标签');
    const caret = await evaluate(port, `(()=>{const field=document.querySelector(${JSON.stringify(bodySelector)});field.focus();field.setSelectionRange(4,4);window.__workEditorBefore=field;return field.selectionStart})()`);
    assert.equal(caret, 4);
    releaseHistoryAnswer();
    await until(async () => {
      const projection = await invoke(port, 'read_desktop_workspace', { workspaceId });
      const conversation = projection.exchange.conversations.find(item => item.messages?.some(message => message.role === 'user' && messageText(message) === prompt && (!attachmentOnly || message.blocks?.some(block => block.kind === 'ASSET_REF' && attachmentDraft.attachments.some(attachment => attachment.id === block.asset?.id)))));
      const leaf = conversation?.messages.find(item => item.id === conversation.currentLeafId);
      return leaf?.role === 'assistant' && leaf.delivery === 'COMPLETE' ? conversation : null;
    }, '流式回答完成');
    const editorState = await until(() => evaluate(port, `(()=>{const field=document.querySelector(${JSON.stringify(bodySelector)});if(!field||field===window.__workEditorBefore)return null;return {replaced:true,title:document.querySelector(${JSON.stringify(titleSelector)})?.value,body:field.value,project:document.querySelector(${JSON.stringify(`#${workEditorKind}-project`)})?.value,tags:document.querySelector('#knowledge-tags')?.value,focused:field===document.activeElement,caret:field.selectionStart}})()`), '真实流式完成后的编辑弹窗 DOM 替换');
    assert.equal(editorState.title, title);
    assert.equal(editorState.body, body);
    if (projectId) assert.equal(editorState.project, projectId);
    if (workEditorKind === 'knowledge') assert.equal(editorState.tags, '流式标签');
    assert.equal(editorState.focused, true, '流式结束后正文仍可继续输入');
    assert.equal(editorState.caret, 4, '流式结束后光标位置未改变');
    await click(port, `[data-action="save-${workEditorKind}"]`);
    const collection = { project: 'projects', knowledge: 'knowledge', memory: 'memory' }[workEditorKind];
    const saved = await until(async () => (await invoke(port, 'read_desktop_workspace', { workspaceId })).exchange[collection].find(item => item.title === title), '流式结束后编辑保存');
    assert.equal(workEditorKind === 'project' ? saved.description : saved.body, body);
    if (projectId) assert.equal(workEditorKind === 'memory' ? saved.scopeId : saved.projectId, projectId);
    if (workEditorKind === 'knowledge') assert.deepEqual(saved.tags, ['流式标签']);
    workEditorFact = { kind: workEditorKind, objectId: saved.id, projectId, replacedDuringStream: editorState.replaced, saved: true };
    await click(port, '[data-action="show-work"]');
    await until(() => evaluate(port, 'Boolean(document.querySelector(".chat-sidebar[aria-label=工作导航]"))'), '回到 WORK 对话');
  } else if (crossArea) {
    if (fromWork) {
      await click(port, '[data-action="show-chat"]');
      await until(() => evaluate(port, 'Boolean(document.querySelector(".chat-sidebar[aria-label=对话导航]"))'), '发送中切入对话区');
    } else {
      await click(port, '[data-action="new-chat"]');
      await until(() => evaluate(port, 'Boolean(document.querySelector("[data-action=show-work]"))'), '新对话模式切换');
      await click(port, '[data-action="show-work"]');
      await until(() => evaluate(port, 'Boolean(document.querySelector(".chat-sidebar[aria-label=工作导航]"))'), '发送中切入工作区');
    }
    assert.equal(await evaluate(port, `Boolean(document.querySelector('.chat-scroll')?.textContent.includes(${JSON.stringify(answer)}))`), false);
  }
  const submitted = await until(async () => {
    const projection = await invoke(port, 'read_desktop_workspace', { workspaceId });
    const conversation = projection.exchange.conversations.find(item => item.messages?.some(message => message.role === 'user' && messageText(message) === prompt && (!attachmentOnly || message.blocks?.some(block => block.kind === 'ASSET_REF' && attachmentDraft.attachments.some(attachment => attachment.id === block.asset?.id)))));
    const last = conversation?.messages.find(message => message.id === conversation.currentLeafId);
    return last?.role === 'assistant' && last.delivery === 'COMPLETE' && messageText(last).startsWith(answer) ? conversation : null;
  }, '普通发送完成');
  const conversationId = submitted.id;
  if (historyTitleRace) {
    assert.equal(conversationId, historyTitleFact.conversationId);
    if (historyTitleUi) {
      assert.equal(await evaluate(port, `(()=>{document.activeElement?.blur();const key=new KeyboardEvent('keydown',{key:'z',ctrlKey:true,bubbles:true,cancelable:true});window.dispatchEvent(key);return key.defaultPrevented})()`), true, '可见聊天页 Ctrl+Z 应调用撤销');
      await until(async () => (await readConversation(port, workspaceId, conversationId))?.title === historyTitleFact.oldTitle, '界面撤销标题');
    } else {
      await invoke(port, 'undo_desktop_domain', { args: { intentId: `w05-title-after-answer-undo-${randomUUID()}`, workspaceId } });
    }
    const undone = await readConversation(port, workspaceId, conversationId);
    assert.equal(undone.title, historyTitleFact.oldTitle);
    assert.ok(messageText(undone.messages.find(item => item.id === undone.currentLeafId)).startsWith(answer));
    if (historyTitleUi) {
      assert.equal(await evaluate(port, `(()=>{document.activeElement?.blur();const key=new KeyboardEvent('keydown',{key:'z',ctrlKey:true,shiftKey:true,bubbles:true,cancelable:true});window.dispatchEvent(key);return key.defaultPrevented})()`), true, '可见聊天页 Ctrl+Shift+Z 应调用重做');
      await until(async () => (await readConversation(port, workspaceId, conversationId))?.title === historyTitleFact.newTitle, '界面重做标题');
    } else {
      await invoke(port, 'redo_desktop_domain', { args: { intentId: `w05-title-after-answer-redo-${randomUUID()}`, workspaceId } });
    }
    const redone = await readConversation(port, workspaceId, conversationId);
    assert.equal(redone.title, historyTitleFact.newTitle);
    assert.ok(messageText(redone.messages.find(item => item.id === redone.currentLeafId)).startsWith(answer));
  }
  if (autoTitle) {
    if (autoTitleFailed) {
      await until(async () => (await invoke(port, 'read_desktop_usage_ledger')).records
        .some(item => item.conversationId === conversationId && item.chargeMicros === 7), '失败标题已报告用量入账');
      const untitled = await readConversation(port, workspaceId, conversationId);
      assert.equal(untitled.title, submitted.title, '失败标题不得改写会话标题');
      assert.equal(untitled.autoTitlePending, true, '失败标题保留后续显式标题机会');
      assert.equal(providerRequests.filter(item => item.kind === 'title').length, 1, '首次回答只尝试一次已配置标题 Provider');
    } else {
      const titled = await until(async () => {
        const current = await readConversation(port, workspaceId, conversationId);
        return current?.title === '合成会话标题' && current.autoTitlePending === false ? current : null;
      }, '自动标题写入本机');
      assert.ok(titled.revision > submitted.revision);
      await until(() => evaluate(port, `document.querySelector('.chat-history-select[data-id="${conversationId}"] .chat-history-title')?.textContent === '合成会话标题'`), '侧栏显示自动标题');
      assert.equal(providerRequests.filter(item => item.kind === 'title').length, 1, '成功回答只生成一次标题');
    }
  }
  if (explicitMemoryCommand) {
    const memories = (await invoke(port, 'read_desktop_workspace', { workspaceId })).exchange.memory;
    assert.equal(memories.filter(item => item.body === answerSecond.replace('【记忆摘要】', '') && item.status === (explicitMemoryConflict ? 'PENDING_REVIEW' : 'ACTIVE')).length, rejectedMemoryCommand ? 0 : 1, '显式指令按安全校验写入');
    if (!rejectedMemoryCommand) assert.equal(memories.find(item => item.body === answerSecond.replace('【记忆摘要】', ''))?.title, '工作流程偏好', '显式记忆主题须持久化');
    if (explicitMemoryConflict) {
      assert.equal(memories.find(item => item.id === memoryConflictSeed.objectId)?.body, '旧合成工作偏好', '同主题显式命令仍保留旧正文');
      assert.equal(memories.filter(item => item.status === 'ACTIVE' && item.scope === 'GLOBAL' && item.title === '工作流程偏好').length, 1, '待审阅候选不得自动并存');
    }
    assert.equal(providerRequests.filter(item => item.kind === 'answer')[0]?.memoryCommandInstructionPresent, true, '本次请求必须附带摘要生成合同');
    assert.equal(await evaluate(port, 'document.querySelector(".chat-scroll")?.textContent.includes("【记忆主题】")'), false, '结构化模型原文不得显示');
    assert.equal(await evaluate(port, 'document.querySelector(".chat-scroll")?.textContent.includes("SYNTHETIC_MEMORY_REASONING_SECRET")'), false, '模型思考原文不得显示');
    assert.equal(JSON.stringify(await invoke(port, 'read_desktop_workspace', { workspaceId })).includes('SYNTHETIC_MEMORY_REASONING_SECRET'), false, '模型思考原文不得持久化');
  }
  if (memorySuggestion) {
    assert.equal((await invoke(port, 'read_desktop_workspace', { workspaceId })).exchange.memory.filter(item => item.body === prompt).length, 0, '确认前不得自动保存本轮 Memory');
    await until(() => evaluate(port, 'Boolean(document.querySelector("[data-action=suggest-memory-summary]"))'), '对话尾部记忆建议');
    await click(port, '[data-action="suggest-memory-summary"]');
    await until(() => evaluate(port, 'Boolean(document.querySelector("[data-action=confirm-memory-summary-suggestion]"))'), '记忆保存确认');
    assert.equal((await invoke(port, 'read_desktop_workspace', { workspaceId })).exchange.memory.filter(item => item.body === prompt).length, 0, '弹窗未确认不得保存本轮 Memory');
    await click(port, '[data-action="confirm-memory-summary-suggestion"]');
    if (memorySuggestionConflict) {
      await until(() => evaluate(port, 'Boolean(document.querySelector("[data-action=keep-memory-topic]"))'), '同主题记忆选择');
      const conflictScreenshot = (await cdp(port, 'Page.captureScreenshot', { format: 'png', captureBeyondViewport: false }))?.data;
      assert.ok(conflictScreenshot);
      await writeFile(join(root, 'w12-memory-topic-conflict.png'), Buffer.from(conflictScreenshot, 'base64'));
      assert.equal((await invoke(port, 'read_desktop_workspace', { workspaceId })).exchange.memory.find(item => item.id === memoryConflictSeed.objectId)?.body, '合成旧项目偏好', '冲突弹窗出现前不得覆盖旧记忆');
      await click(port, '[data-action="close-dialog"]');
      assert.equal((await invoke(port, 'read_desktop_workspace', { workspaceId })).exchange.memory.filter(item => item.status === 'ACTIVE' && item.scope === 'GLOBAL').length, 1, '取消不得写入');
      await click(port, '[data-action="suggest-memory-summary"]');
      await click(port, '[data-action="confirm-memory-summary-suggestion"]');
      await until(() => evaluate(port, 'Boolean(document.querySelector("[data-action=keep-memory-topic]"))'), '再次显示同主题选择');
      await click(port, '[data-action="keep-memory-topic"]');
      assert.equal((await invoke(port, 'read_desktop_workspace', { workspaceId })).exchange.memory.filter(item => item.status === 'ACTIVE' && item.scope === 'GLOBAL').length, 1, '保留现有不得写入');
      await click(port, '[data-action="suggest-memory-summary"]');
      await click(port, '[data-action="confirm-memory-summary-suggestion"]');
      await until(() => evaluate(port, 'Boolean(document.querySelector("[data-action=revise-memory-topic]"))'), '主题处理选项');
      await click(port, memorySuggestionConflictParallel ? '[data-action="parallel-memory-topic"]' : '[data-action="revise-memory-topic"]');
    }
    const savedMemory = await until(async () => (await invoke(port, 'read_desktop_workspace', { workspaceId })).exchange.memory.find(item => item.body === prompt), '确认后本机 Memory');
    assert.equal(savedMemory.title, '个人项目', '用户确认的记忆主题须持久化');
    if (memorySuggestionConflict) {
      assert.equal(savedMemory.id === memoryConflictSeed.objectId, !memorySuggestionConflictParallel);
      assert.equal(savedMemory.revision, memorySuggestionConflictParallel ? 1 : 2);
      const old = (await invoke(port, 'read_desktop_workspace', { workspaceId })).exchange.memory.find(item => item.id === memoryConflictSeed.objectId);
      assert.equal(old.body, memorySuggestionConflictParallel ? '合成旧项目偏好' : prompt);
    }
    await until(() => evaluate(port, '!document.querySelector("[data-action=suggest-memory-summary]")'), '已保存正文不再重复建议');
    assert.equal(providerRequests.filter(item => item.kind === 'answer').length, 1, '记忆保存不能再次外发');
  }
  if (contextProject) assert.equal(submitted.projectId, contextIds.targetProject);
  let otherWorkspaceId = null;
  if (crossArea && !workEditorStream) {
    const otherArea = originArea === 'CHAT' ? 'WORK' : 'CHAT';
    otherWorkspaceId = (await invoke(port, 'ensure_desktop_local_workspace', {}, otherArea)).summary.id;
    const otherWorkspace = await invoke(port, 'read_desktop_workspace', { workspaceId: otherWorkspaceId }, otherArea);
    assert.ok(!otherWorkspace.exchange.conversations.some(item => item.id === conversationId), '回答不应写入目标区');
    assert.equal(await evaluate(port, `Boolean(document.querySelector('.chat-scroll')?.textContent.includes(${JSON.stringify(answer)}))`), false,
      '对话区迟到回答不得显示在工作区');
    if (fromWork) {
      await click(port, '[data-action="new-chat"]');
      await until(() => evaluate(port, 'Boolean(document.querySelector("[data-action=show-work]"))'), '对话区模式切换');
    }
    await click(port, fromWork ? '[data-action="show-work"]' : '[data-action="show-chat"]');
    await until(() => evaluate(port, `Boolean(document.querySelector('.chat-sidebar[aria-label=${originArea === 'WORK' ? '工作导航' : '对话导航'}]'))`), '返回原数据区');
    await until(() => evaluate(port, `Boolean(document.querySelector('[data-action="select-chat"][data-id="${conversationId}"]'))`), '原对话在列表');
    await click(port, `[data-action="select-chat"][data-id="${conversationId}"]`);
    await until(() => evaluate(port, `Boolean(document.querySelector('.chat-scroll[data-conversation-id="${conversationId}"]'))`), '返回原对话正文', 10000);
  }
  try {
    await until(() => evaluate(port, explicitMemoryCommand
      ? rejectedMemoryCommand
        ? 'document.querySelector(".chat-scroll")?.textContent.includes("未写入")'
        : `document.querySelector(".chat-scroll")?.textContent.includes(${JSON.stringify(explicitMemoryConflict ? '已生成同主题记忆候选' : '已更新记忆摘要')}) && document.querySelector(".chat-scroll")?.textContent.includes("长期关注跨端开发与数据保全")`
      : `document.querySelector('.chat-scroll')?.textContent.includes(${JSON.stringify(answer)})`), '完成回答可见', crossArea ? 5000 : 30000);
  } catch (error) {
    if (crossArea) console.error('cross-area UI', JSON.stringify(await evaluate(port,
      '({header:document.querySelector(".chat-title")?.textContent, selected:document.querySelector("[data-action=select-chat].selected")?.outerHTML, scroll:document.querySelector(".chat-scroll")?.textContent?.slice(0,500), status:document.querySelector(".chat-status")?.textContent})')));
    throw error;
  }
  if (withSources) {
    const assistant = submitted.messages.find(item => item.id === submitted.currentLeafId);
    assert.ok(messageText(assistant).includes(publicSource));
    assert.ok(!messageText(assistant).includes('https://user@example.test/private'));
    await until(() => evaluate(port, 'Boolean(document.querySelector("[data-action=open-source-links]"))'), '来源入口可见');
    await click(port, '[data-action="open-source-links"]');
    await until(() => evaluate(port, `Boolean(document.querySelector('.source-links-dialog [data-action="open-source-link"][data-href="${publicSource}"]'))`), '公开来源可见');
    assert.equal(await evaluate(port, 'document.querySelectorAll(".source-links-dialog [data-action=open-source-link]").length'), 1);
    assert.equal(await evaluate(port, 'document.querySelector(".source-links-dialog")?.textContent.includes("https://user@example.test/private")'), false);
  }
  const ordinaryRequests = providerRequests.filter(item => item.kind === 'answer');
  assert.equal(ordinaryRequests.length, 1);
  const sentSelection = await invoke(port, 'read_desktop_p6g_selection', { workspaceId, conversationId });
  assert.equal(sentSelection?.lastRoute?.modelId, ordinaryRequests[0].model, '发送后模型归因须可回读');
  if (!manualTerra) {
    await until(() => evaluate(port, `globalThis.__nanfengDesktopWorkState?.p6gSelection?.lastRoute?.modelId === ${JSON.stringify(ordinaryRequests[0].model)}`), '当前会话模型归因刷新');
    await click(port, '[data-action="toggle-p6g-model-picker"]');
    await until(() => evaluate(port, `document.querySelector('[data-action="select-p6g-auto"]')?.textContent.includes(${JSON.stringify(`上次：${sentSelection.lastRoute.displayName}`)})`), '自动模型面显示上次实际模型');
    await click(port, '[data-action="toggle-p6g-model-picker"]');
  }
  if (configureModelUi) assert.equal(ordinaryRequests[0].credentialMatched, true, '请求必须使用设置页刚保存的合成 Key');
  if (manualTerra) assert.equal(ordinaryRequests[0].model, 'openai/gpt-5.6-terra', '手选模型不得被 Auto 改投');
  assert.ok(ordinaryRequests[0].idempotencyKey && ordinaryRequests[0].stream && ordinaryRequests[0].includeUsage);
  if (historyContext) {
    assert.equal(ordinaryRequests[0].historyPresent, true);
    assert.equal(ordinaryRequests[0].archivedHistoryPresent, false);
    const contextRecords = await invoke(port, 'read_desktop_ordinary_chat_context_records');
    const record = contextRecords.find(item => item.conversationId === conversationId);
    assert.ok(record?.selectedSources.some(item => item.kind === '历史对话' && item.title === 'W12 合成历史记录'));
  }
  if (contextMode) {
    const expectedContext = contextEnabled && !contextOtherProject && !emptyContextQuery;
    assert.equal(ordinaryRequests[0].knowledgePresent, expectedContext);
    assert.equal(ordinaryRequests[0].memoryPresent, expectedContext);
    if (profileSettingsFlow) {
      assert.equal(ordinaryRequests[0].profileOccupationPresent, summaryPause);
      assert.equal(ordinaryRequests[0].profileInstructionPresent, true);
    }
    if (expectedContext) assert.equal(ordinaryRequests[0].knowledgeBeforeMemory, true, '资料库必须先于 Memory 占用请求预算');
    const contextRecords = await invoke(port, 'read_desktop_ordinary_chat_context_records');
    const contextRecord = contextRecords.find(item => item.conversationId === conversationId);
    assert.ok(contextRecord, '回答来源记录必须持久化');
    assert.equal(contextRecord.selectedSources.some(item => item.kind === '资料库' && item.title === `${prompt} 资料`), expectedContext);
    const storedMemory = (await invoke(port, 'read_desktop_workspace', { workspaceId }))
      .exchange.memory.find(item => item.id === contextIds.memory);
    assert.ok(storedMemory?.title && storedMemory.body.includes('W12_MEMORY_ALPHA'), '合成 Memory 必须仍在本区');
    assert.equal(contextRecord.selectedSources.some(item => item.kind === 'Memory' && item.title === storedMemory.title), expectedContext);
    if (profileSettingsFlow) {
      assert.equal(contextRecord.selectedSources.some(item => item.kind === '个性化资料' && item.title === '你的职业'), summaryPause);
      assert.ok(contextRecord.selectedSources.some(item => item.kind === '自定义指令' && item.title === '自定义指令'));
    }
  }
  if (attachmentInputs) {
    assert.ok(ordinaryRequests[0].pastedTextPresent);
    assert.ok(attachmentPdf ? ordinaryRequests[0].pdfFilePresent : ordinaryRequests[0].droppedTextPresent);
    if (attachmentPdf) assert.equal(ordinaryRequests[0].model, 'openai/gpt-5.6-terra', 'Auto 混合 PDF 须选择材料桥支持的精确模型');
    const user = submitted.messages.find(item => item.role === 'user' && messageText(item) === prompt);
    assert.deepEqual(user.blocks.filter(block => block.kind === 'ASSET_REF').map(block => block.asset.id).sort(),
      attachmentDraft.attachments.map(item => item.id).sort());
    const afterSendDraft = await invoke(port, 'read_desktop_conversation_draft', { workspaceId, conversationId: null });
    assert.ok(!afterSendDraft?.attachments?.length);
  }
  if (cameraInput) {
    assert.equal(ordinaryRequests[0].cameraImagePresent, true, '相机图片须作为本次模型材料');
    const user = submitted.messages.find(item => item.role === 'user' && messageText(item) === prompt);
    assert.deepEqual(user.blocks.filter(block => block.kind === 'ASSET_REF').map(block => block.asset.id), cameraDraft.attachments.map(item => item.id));
    const afterSendDraft = await invoke(port, 'read_desktop_conversation_draft', { workspaceId, conversationId: null });
    assert.ok(!afterSendDraft?.attachments?.length);
  }
  const ledger = await invoke(port, 'read_desktop_usage_ledger');
  assert.ok(ledger.records.some(item => item.conversationId === conversationId && item.chargeMicros === 5));
  if (contentModeSwitch) {
    const workWorkspaceId = (await invoke(port, 'ensure_desktop_local_workspace', {}, 'WORK')).summary.id;
    const olderWork = await invoke(port, 'mutate_desktop_domain', { args: { intentId: `w00a-work-older-${Date.now()}`, workspaceId: workWorkspaceId,
      entity: 'conversation', action: 'create', fields: { title: '较早工作对话', projectId: null, firstMessage: '合成较早工作问题' } } }, 'WORK');
    await new Promise(resolveWait => setTimeout(resolveWait, 20));
    const newerWork = await invoke(port, 'mutate_desktop_domain', { args: { intentId: `w00a-work-newer-${Date.now()}`, workspaceId: workWorkspaceId,
      entity: 'conversation', action: 'create', fields: { title: '最新工作对话', projectId: null, firstMessage: '合成最新工作问题' } } }, 'WORK');
    await until(() => evaluate(port, `Boolean(document.querySelector('.chat-scroll[data-conversation-id="${conversationId}"]'))`), '已有回答正文');
    assert.equal(await evaluate(port, 'document.querySelector(".chat-mode-switch [data-action=show-chat]")?.getAttribute("aria-pressed")'), 'true');
    await click(port, '.chat-mode-switch [data-action="show-work"]');
    await until(() => evaluate(port, 'globalThis.__nanfengDesktopWorkState?.dataArea === "WORK"'), '已有对话直接切 WORK');
    await until(() => evaluate(port, `globalThis.__nanfengDesktopWorkState?.selectedConversationId === ${JSON.stringify(newerWork.objectId)}`), 'WORK 最近对话默认打开');
    await until(() => evaluate(port, 'Boolean(document.querySelector(".chat-mode-switch [data-action=show-chat]"))'), 'WORK 顶部模式切换');
    assert.equal(await evaluate(port, 'document.querySelector(".chat-mode-switch [data-action=show-work]")?.getAttribute("aria-pressed")'), 'true');
    await until(() => evaluate(port, 'Boolean(document.querySelector("#chat-composer"))'), 'WORK 无会话输入区').catch(async error => {
      const ui = await evaluate(port, '({pane:globalThis.__nanfengDesktopWorkState?.pane,area:globalThis.__nanfengDesktopWorkState?.dataArea,body:document.querySelector(".chat-main")?.textContent?.slice(0,240),html:document.querySelector(".chat-main")?.innerHTML?.slice(-450)})');
      throw new Error(`${error.message}: ${JSON.stringify(ui)}`);
    });
    const composerLayout = await evaluate(port, '(()=>{const n=document.querySelector("#chat-composer");const r=n.getBoundingClientRect();return {top:r.top,bottom:r.bottom,width:r.width,height:r.height,viewport:innerHeight,display:getComputedStyle(n).display,dock:document.querySelector(".chat-composer-dock")?.getBoundingClientRect().toJSON()}})()');
    assert.ok(composerLayout.width > 0 && composerLayout.height > 0 && composerLayout.top >= 0 && composerLayout.bottom <= composerLayout.viewport, `WORK 输入区未处于可见视口：${JSON.stringify(composerLayout)}`);
    await click(port, `[data-action="select-chat"][data-id="${olderWork.objectId}"]`);
    await until(() => evaluate(port, `globalThis.__nanfengDesktopWorkState?.selectedConversationId === ${JSON.stringify(olderWork.objectId)}`), '选择较早工作对话');
    await click(port, '.chat-mode-switch [data-action="show-work"]');
    await until(() => evaluate(port, `globalThis.__nanfengDesktopWorkState?.selectedConversationId === ${JSON.stringify(newerWork.objectId)}`), '再次点击工作回到最近对话');
    await until(() => evaluate(port, `Boolean(document.querySelector('.chat-scroll[data-conversation-id="${newerWork.objectId}"]')) && Boolean(document.querySelector('#chat-composer'))`), '最近工作对话正文和输入区');
    await evaluate(port, 'new Promise(resolve=>requestAnimationFrame(()=>requestAnimationFrame(resolve)))');
    await new Promise(resolveWait => setTimeout(resolveWait, 150));
    const workScreenshot = (await cdp(port, 'Page.captureScreenshot', { format: 'png', captureBeyondViewport: false }))?.data;
    assert.ok(workScreenshot);
    await writeFile(join(root, 'w00a-work-mode-from-content.png'), Buffer.from(workScreenshot, 'base64'));
    await click(port, '.chat-mode-switch [data-action="show-chat"]');
    await until(() => evaluate(port, 'globalThis.__nanfengDesktopWorkState?.dataArea === "CHAT"'), '返回原 CHAT');
    await until(() => evaluate(port, `globalThis.__nanfengDesktopWorkState?.selectedConversationId === ${JSON.stringify(conversationId)}`), '返回原会话');
    await until(() => evaluate(port, `Boolean(document.querySelector('.chat-scroll[data-conversation-id="${conversationId}"]')) && Boolean(document.querySelector('.chat-mode-switch [data-action="show-work"]'))`), '原会话界面重绘');
    assert.equal(providerRequests.filter(item => item.kind === 'answer').length, 1, '模式切换不得重新发送');
  }
  const screenshot = (await cdp(port, 'Page.captureScreenshot', { format: 'png', captureBeyondViewport: false }))?.data;
  assert.ok(screenshot);
  await writeFile(join(root, 'w06-visible-send.png'), Buffer.from(screenshot, 'base64'));
  const beforeRestartRequests = providerRequests.length;
  stop();
  port = await start(true);
  await until(() => evaluate(port, 'Boolean(window.__TAURI__?.core?.invoke)'), '同根冷启 IPC');
  const cold = await invoke(port, 'read_desktop_workspace', { workspaceId });
  if (explicitMemoryConflict) {
    assert.equal(cold.exchange.memory.filter(item => item.status === 'PENDING_REVIEW' && item.title === '工作流程偏好').length, 1, '同根冷启仍保留待审阅候选');
    const candidateId = cold.exchange.memory.find(item => item.status === 'PENDING_REVIEW' && item.title === '工作流程偏好').id;
    if (explicitMemoryConflictBackup) {
      const selectedPath = join(root, 'w12-pending-memory.nfai-backup');
      const artifact = await invoke(port, 'export_desktop_local_backup', { selectedPath });
      assert.ok(artifact.sha256 && existsSync(selectedPath), '待审阅时备份包已落盘');
      const preflight = await invoke(port, 'preflight_desktop_local_backup', { selectedPath });
      assert.ok(preflight.fingerprint, '待审阅备份通过预检');
      const existing = cold.exchange.memory.find(item => item.id === memoryConflictSeed.objectId);
      await invoke(port, 'mutate_desktop_domain', { args: { intentId: `synthetic-pending-backup-change-${Date.now()}`, workspaceId,
        entity: 'memory', action: 'resolvePendingMemoryTopic', objectId: candidateId, expectedRevision: 1,
        fields: { resolution: 'CREATE_PARALLEL', expectedEntries: [{ id: existing.id, revision: existing.revision }] } } });
      assert.equal((await invoke(port, 'read_desktop_workspace', { workspaceId })).exchange.memory.find(item => item.id === candidateId)?.status, 'ACTIVE', '恢复前合成变更已提交');
      const restored = await invoke(port, 'restore_desktop_local_backup', { fingerprint: preflight.fingerprint, replaceLocal: true });
      assert.equal(restored.restartRequired, true, '备份恢复须冷启完成');
      stop();
      port = await start(true);
      await until(() => evaluate(port, 'Boolean(window.__TAURI__?.core?.invoke)'), '备份恢复冷启 IPC');
      const recovered = (await invoke(port, 'read_desktop_workspace', { workspaceId })).exchange.memory;
      assert.equal(recovered.find(item => item.id === candidateId)?.status, 'PENDING_REVIEW', '恢复后候选仍待审阅');
      assert.equal(recovered.find(item => item.id === memoryConflictSeed.objectId)?.body, '旧合成工作偏好', '恢复后旧主题正文不变');
    }
    if (explicitMemoryConflictRepeat) {
      await until(() => evaluate(port, `Boolean(document.querySelector('[data-action="select-chat"][data-id="${conversationId}"]'))`), '重复记忆前原会话');
      await click(port, `[data-action="select-chat"][data-id="${conversationId}"]`);
      await fillComposer(port, prompt);
      await click(port, '[data-action="save-local-message"]');
      await until(() => evaluate(port, 'document.querySelector(".chat-scroll")?.textContent.includes("本次未重复写入")'), '重复记忆未再写入');
      const afterRepeat = (await invoke(port, 'read_desktop_workspace', { workspaceId })).exchange.memory;
      assert.equal(afterRepeat.filter(item => item.id === candidateId && item.status === 'PENDING_REVIEW').length, 1);
      assert.equal(afterRepeat.filter(item => item.title === '工作流程偏好' && item.status === 'PENDING_REVIEW').length, 1);
      assert.equal(providerRequests.filter(item => item.kind === 'answer').length, 2, '重复显式指令仍由模型生成但只保留一条候选');
    }
    await click(port, '[data-action="show-settings"]');
    await click(port, '[data-action="open-settings-page"][data-page="memory-overview"]');
    await until(() => evaluate(port, 'Boolean(document.querySelector("[data-action=review-pending-memory-topic]"))'), '设置页待审阅候选');
    await click(port, '[data-action="review-pending-memory-topic"]');
    await until(() => evaluate(port, `Boolean(document.querySelector('[data-action="resolve-pending-memory-topic"][data-resolution="${explicitMemoryResolution}"]'))`), '待审阅选择');
    await click(port, `[data-action="resolve-pending-memory-topic"][data-resolution="${explicitMemoryResolution}"]`);
    await until(async () => {
      const items = (await invoke(port, 'read_desktop_workspace', { workspaceId })).exchange.memory;
      return items.find(item => item.id === candidateId)?.status === (explicitMemoryConflictKeep || explicitMemoryConflictRevise ? 'DELETED' : 'ACTIVE')
        && items.find(item => item.id === memoryConflictSeed.objectId)?.body === (explicitMemoryConflictRevise ? answerSecond.replace('【记忆摘要】', '') : '旧合成工作偏好');
    }, '明确决议后记录状态正确');
    const resolved = (await invoke(port, 'read_desktop_workspace', { workspaceId })).exchange.memory;
    assert.equal(resolved.filter(item => item.status === 'ACTIVE' && item.scope === 'GLOBAL' && item.title === '工作流程偏好').length, explicitMemoryConflictKeep || explicitMemoryConflictRevise ? 1 : 2);
    await click(port, '[data-action="show-chat"]');
  }
  if (explicitMemoryConflictBackup) {
    result = { candidateSha256: expectedHash, root, workspaceId, conversationId,
      backupRestoredPending: true, reviewedAfterRestore: true,
      providerRequests: providerRequests.length, pids };
  } else {
  const coldConversation = cold.exchange.conversations.find(item => item.id === conversationId);
  assert.ok(coldConversation);
  if (memorySuggestion) assert.equal(cold.exchange.memory.find(item => item.body === prompt)?.title, '个人项目', '重启后保留确认的记忆主题');
  if (explicitMemoryCommand && !rejectedMemoryCommand) assert.equal(cold.exchange.memory.find(item => item.body === answerSecond.replace('【记忆摘要】', ''))?.title, '工作流程偏好', '重启后保留显式记忆主题');
  const coldRouteSelection = await invoke(port, 'read_desktop_p6g_selection', { workspaceId, conversationId });
  assert.equal(coldRouteSelection?.lastRoute?.modelId, explicitMemoryConflictBackup ? undefined : ordinaryRequests[0].model, explicitMemoryConflictBackup ? '本机备份恢复按合同排除设备路由偏好' : '同根冷启后模型归因须保留');
  if (explicitMemoryCommand) {
    const reviewed = explicitMemoryConflict ? await invoke(port, 'read_desktop_workspace', { workspaceId }) : cold;
    assert.equal(reviewed.exchange.memory.filter(item => item.body === answerSecond.replace('【记忆摘要】', '') && item.status === 'ACTIVE').length, rejectedMemoryCommand || explicitMemoryConflictKeep ? 0 : 1, '冷启后显式记忆按选择结果保留');
    assert.equal(providerRequests.filter(item => item.kind === 'answer').length, explicitMemoryConflictRepeat ? 2 : 1, '冷启不得重发记忆生成请求');
  }
  if (memorySuggestion) {
    assert.equal(cold.exchange.memory.filter(item => item.body === prompt && item.status === 'ACTIVE').length, 1, '冷启后全局 Memory 仅有一条');
    assert.equal(providerRequests.filter(item => item.kind === 'answer').length, 1, '冷启不能重发回答');
  }
  if (historyContext) {
    assert.ok(cold.exchange.conversations.some(item => item.id === historyIds.active));
    assert.equal(cold.exchange.conversations.find(item => item.id === historyIds.archived)?.archived, true);
  }
  if (contextMode) {
    if (contextProject) assert.ok(cold.exchange.projects.some(item => item.id === contextIds.project));
    if (contextOtherProject) assert.ok(cold.exchange.projects.some(item => item.id === contextIds.targetProject));
    assert.ok(cold.exchange.knowledge.some(item => item.id === contextIds.knowledge));
    assert.ok(cold.exchange.memory.some(item => item.id === contextIds.memory));
    const coldSettings = await invoke(port, 'read_desktop_app_settings');
    assert.equal(coldSettings.product.memoryEnabled, contextEnabled || summaryPause);
    assert.equal(coldSettings.product.memoryRetrievalEnabled, contextEnabled);
    assert.equal(coldSettings.product.historyLibraryEnabled, contextEnabled);
    if (profileSettingsFlow) {
      assert.equal(coldSettings.product.occupation, 'W12_PROFILE_OCCUPATION');
      assert.equal(coldSettings.product.customInstructions, 'W12_PROFILE_INSTRUCTION');
    }
  }
  if (workEditorStream) {
    const collection = { project: 'projects', knowledge: 'knowledge', memory: 'memory' }[workEditorFact.kind];
    const coldObject = cold.exchange[collection].find(item => item.id === workEditorFact.objectId);
    assert.equal(coldObject?.title, `流式期间合成${{ project: '项目', knowledge: '知识', memory: '记忆' }[workEditorFact.kind]}`);
    assert.equal(workEditorFact.kind === 'project' ? coldObject?.description : coldObject?.body, '流式完成后仍保留输入与光标');
    if (workEditorFact.projectId) assert.equal(workEditorFact.kind === 'memory' ? coldObject?.scopeId : coldObject?.projectId, workEditorFact.projectId);
    if (workEditorFact.kind === 'knowledge') assert.deepEqual(coldObject?.tags, ['流式标签']);
    workEditorFact.coldReadback = true;
  }
  if (crossArea && !workEditorStream) {
    const otherArea = originArea === 'CHAT' ? 'WORK' : 'CHAT';
    const otherCold = await invoke(port, 'read_desktop_workspace', { workspaceId: otherWorkspaceId }, otherArea);
    assert.ok(!otherCold.exchange.conversations.some(item => item.id === conversationId), '冷启后目标区不得出现原区对话');
  }
  assert.ok(messageText(coldConversation.messages.find(item => item.id === coldConversation.currentLeafId)).startsWith(answer));
  if (historyTitleRace) assert.equal(coldConversation.title, historyTitleFact.newTitle, '标题重做后冷启应保留新标题');
  if (attachmentInputs) {
    const coldUser = coldConversation.messages.find(item => item.role === 'user' && messageText(item) === prompt);
    assert.deepEqual(coldUser.blocks.filter(block => block.kind === 'ASSET_REF').map(block => block.asset.id).sort(),
      attachmentDraft.attachments.map(item => item.id).sort());
  }
  if (cameraInput) {
    const coldUser = coldConversation.messages.find(item => item.role === 'user' && messageText(item) === prompt);
    assert.deepEqual(coldUser.blocks.filter(block => block.kind === 'ASSET_REF').map(block => block.asset.id), cameraDraft.attachments.map(item => item.id));
  }
  if (withSources) assert.ok(messageText(coldConversation.messages.find(item => item.id === coldConversation.currentLeafId)).includes(publicSource));
  assert.equal(providerRequests.length, beforeRestartRequests + (explicitMemoryConflictRepeat ? 1 : 0));
  const coldLedger = await invoke(port, 'read_desktop_usage_ledger');
  assert.ok(coldLedger.records.some(item => item.conversationId === conversationId && item.chargeMicros === 5));
  let cancelled = null;
  let failed = null;
  let finalConversation = coldConversation;
  if (branchEdit) {
    await until(() => evaluate(port, `Boolean(document.querySelector('[data-action="select-chat"][data-id="${conversationId}"]'))`), '编辑分支冷启会话列表');
    await click(port, `[data-action="select-chat"][data-id="${conversationId}"]`);
    await until(() => evaluate(port, `Boolean(document.querySelector('.chat-scroll[data-conversation-id="${conversationId}"]'))`), '编辑分支会话正文');
    const oldUser = coldConversation.messages.find(item => item.role === 'user' && messageText(item) === prompt);
    const oldAnswer = coldConversation.messages.find(item => item.id === coldConversation.currentLeafId);
    assert.ok(oldUser && oldAnswer);
    await click(port, `[data-action="open-assistant-message-menu"][data-message-id="${oldUser.id}"]`);
    await until(() => evaluate(port, `Boolean(document.querySelector('[data-action="edit-conversation-user-message"][data-message-id="${oldUser.id}"]'))`), '编辑消息菜单');
    await click(port, `[data-action="edit-conversation-user-message"][data-message-id="${oldUser.id}"]`);
    await until(() => evaluate(port, 'Boolean(document.querySelector("#conversation-user-message-edit"))'), '编辑消息对话框');
    await evaluate(port, `(()=>{const field=document.querySelector('#conversation-user-message-edit');field.value=${JSON.stringify(editedPrompt)};field.dispatchEvent(new Event('input',{bubbles:true}));return true})()`);
    await click(port, '[data-action="save-conversation-user-message-edit"]');
    const edited = await until(async () => {
      const item = await readConversation(port, workspaceId, conversationId);
      const leaf = item?.messages.find(message => message.id === item.currentLeafId);
      return leaf?.role === 'user' && messageText(leaf) === editedPrompt ? item : null;
    }, '编辑后的用户分支落盘');
    assert.ok(edited.messages.some(item => item.id === oldUser.id && messageText(item) === prompt));
    assert.ok(edited.messages.some(item => item.id === oldAnswer.id && messageText(item).startsWith(answer)));
    assert.equal(providerRequests.filter(item => item.kind === 'answer').length, 1, '编辑本身不能发起模型请求');
    const newUserId = edited.currentLeafId;
    await until(() => evaluate(port, `Boolean(document.querySelector('[data-action="open-assistant-message-menu"][data-message-id="${newUserId}"]'))`), '新用户分支可见');
    await click(port, `[data-action="open-assistant-message-menu"][data-message-id="${newUserId}"]`);
    await until(() => evaluate(port, `Boolean(document.querySelector('[data-action="generate-answer-for-saved-user"][data-message-id="${newUserId}"]'))`), '生成回答菜单');
    await click(port, `[data-action="generate-answer-for-saved-user"][data-message-id="${newUserId}"]`);
    finalConversation = await until(async () => {
      const item = await readConversation(port, workspaceId, conversationId);
      const leaf = item?.messages.find(message => message.id === item.currentLeafId);
      return leaf?.role === 'assistant' && leaf.parentId === newUserId && leaf.delivery === 'COMPLETE' && messageText(leaf).startsWith(answer) ? item : null;
    }, '编辑后新分支回答完成');
    assert.equal(providerRequests.filter(item => item.kind === 'answer').length, 2);
    assert.ok(finalConversation.messages.some(item => item.id === oldAnswer.id));
    const firstNewAnswerId = finalConversation.currentLeafId;
    await until(() => evaluate(port, `(()=>{const conversation=globalThis.__nanfengDesktopWorkState?.current?.exchange?.conversations?.find(item=>item.id===${JSON.stringify(conversationId)});const leaf=conversation?.messages?.find(item=>item.id===${JSON.stringify(firstNewAnswerId)});return conversation?.currentLeafId===${JSON.stringify(firstNewAnswerId)} && leaf?.delivery==='COMPLETE' && Boolean(leaf?.attemptId)})()`), '新回答完成态进入可见工作区状态');
    await until(() => evaluate(port, '!globalThis.__nanfengDesktopWorkState?.ordinarySendPreparing'), '前一回答本机回读完成');
    await until(() => evaluate(port, `Boolean(document.querySelector('[data-action="open-assistant-message-menu"][data-message-id="${firstNewAnswerId}"]'))`), '新回答可见菜单');
    await click(port, `[data-action="open-assistant-message-menu"][data-message-id="${firstNewAnswerId}"]`);
    await until(() => evaluate(port, `Boolean(document.querySelector('[data-action="regenerate-completed-answer"][data-message-id="${firstNewAnswerId}"]'))`), '重新生成入口').catch(async error => {
      const ui = await evaluate(port, `({menu:document.querySelector('.assistant-message-menu')?.textContent,leaf:globalThis.__nanfengDesktopWorkState?.current?.exchange?.conversations?.find(item=>item.id===${JSON.stringify(conversationId)})?.currentLeafId,shown:globalThis.__nanfengDesktopWorkState?.assistantMessageMenu})`);
      throw new Error(`${error.message}: ${JSON.stringify(ui)}`);
    });
    await click(port, `[data-action="regenerate-completed-answer"][data-message-id="${firstNewAnswerId}"]`);
    finalConversation = await until(async () => {
      const item = await readConversation(port, workspaceId, conversationId);
      const leaf = item?.messages.find(message => message.id === item.currentLeafId);
      return leaf?.role === 'assistant' && leaf.id !== firstNewAnswerId && leaf.parentId === newUserId
        && leaf.delivery === 'COMPLETE' && messageText(leaf).startsWith(answer) ? item : null;
    }, '重新生成分支回答完成').catch(async error => {
      const ui = await evaluate(port, `({status:globalThis.__nanfengDesktopWorkState?.status,error:globalThis.__nanfengDesktopWorkState?.error,leaf:globalThis.__nanfengDesktopWorkState?.current?.exchange?.conversations?.find(item=>item.id===${JSON.stringify(conversationId)})?.currentLeafId,body:document.querySelector('.chat-scroll')?.textContent?.slice(-600)})`);
      throw new Error(`${error.message}: ${JSON.stringify(ui)}`);
    });
    assert.equal(providerRequests.filter(item => item.kind === 'answer').length, 3);
    assert.ok(finalConversation.messages.some(item => item.id === firstNewAnswerId));
    const regeneratedId = finalConversation.currentLeafId;
    await until(() => evaluate(port, `Boolean(document.querySelector('[data-action="open-assistant-message-menu"][data-message-id="${regeneratedId}"]'))`), '重新生成回答菜单');
    await click(port, `[data-action="open-assistant-message-menu"][data-message-id="${regeneratedId}"]`);
    await click(port, `[data-action="show-conversation-branches"][data-message-id="${regeneratedId}"]`);
    await until(() => evaluate(port, `Boolean(document.querySelector('[data-action="select-conversation-branch"][data-leaf-id="${oldAnswer.id}"]'))`), '旧回答分支切换入口');
    await click(port, `[data-action="select-conversation-branch"][data-leaf-id="${oldAnswer.id}"]`);
    finalConversation = await until(async () => {
      const item = await readConversation(port, workspaceId, conversationId);
      return item?.currentLeafId === oldAnswer.id ? item : null;
    }, '旧回答分支恢复为当前路径');
    assert.ok(finalConversation.messages.some(item => item.id === regeneratedId));
    const branchLedger = await invoke(port, 'read_desktop_usage_ledger');
    assert.equal(branchLedger.records.filter(item => item.conversationId === conversationId && item.chargeMicros === 5).length, 3);
    const archivedReceipt = await invoke(port, 'mutate_desktop_domain', { args: {
      intentId: `w05-archive-branch-${Date.now().toString(36)}`, workspaceId, entity: 'conversation', action: 'archive',
      objectId: conversationId, expectedRevision: finalConversation.revision, fields: {},
    } });
    const archived = await readConversation(port, workspaceId, conversationId);
    assert.equal(archived.archived, true);
    assert.equal(archived.revision, archivedReceipt.revision);
    for (const [action, fields] of [
      ['editUserMessage', { messageId: oldUser.id, text: '归档后不得写入' }],
      ['switchLeaf', { messageId: regeneratedId }],
    ]) {
      await assert.rejects(invoke(port, 'mutate_desktop_domain', { args: {
        intentId: `w05-archived-${action.toLowerCase()}-${Date.now().toString(36)}`, workspaceId,
        entity: 'conversation', action, objectId: conversationId, expectedRevision: archived.revision, fields,
      } }), /归档会话必须先恢复/);
    }
    const afterRejected = await readConversation(port, workspaceId, conversationId);
    assert.equal(afterRejected.revision, archived.revision);
    assert.equal(afterRejected.currentLeafId, oldAnswer.id);
    assert.equal(afterRejected.messages.length, archived.messages.length);
    const branchRequestsBeforeCold = providerRequests.length;
    stop();
    port = await start(true);
    await until(() => evaluate(port, 'Boolean(window.__TAURI__?.core?.invoke)'), '编辑分支再次冷启 IPC');
    finalConversation = await readConversation(port, workspaceId, conversationId);
    assert.equal(finalConversation.archived, true);
    assert.equal(finalConversation.currentLeafId, oldAnswer.id);
    assert.ok(finalConversation.messages.some(item => item.id === regeneratedId && item.parentId === newUserId));
    assert.equal(providerRequests.length, branchRequestsBeforeCold);
  } else if (!withSources && !crossArea && !attachmentInputs && !cameraInput) {
  await until(() => evaluate(port, `Boolean(document.querySelector('[data-action="select-chat"][data-id="${conversationId}"]'))`), '冷启会话列表');
  await click(port, `[data-action="select-chat"][data-id="${conversationId}"]`);
  await until(() => evaluate(port, `Boolean(document.querySelector('.chat-scroll[data-conversation-id="${conversationId}"]'))`), '冷启会话正文');
  await fillComposer(port, cancelPrompt);
  await click(port, '[data-action="save-local-message"]');
  if (cancelBeforeHeaders) {
    await until(() => providerRequests.some(item => item.kind === 'cancel'), '取消请求已到达 Provider', 5000);
  } else {
    await until(() => evaluate(port, `document.querySelector('.chat-scroll')?.textContent.includes(${JSON.stringify(cancelPartial)})`), '取消前部分回答', 5000);
  }
  await until(() => evaluate(port, 'Boolean(document.querySelector("[data-action=stop-ordinary-chat]"))'), '停止生成按钮');
  await click(port, '[data-action="stop-ordinary-chat"]');
  cancelled = await until(async () => {
    const item = await readConversation(port, workspaceId, conversationId);
    const leaf = item?.messages.find(message => message.id === item.currentLeafId);
    return leaf?.role === 'assistant' && leaf.delivery === 'CANCELLED' ? leaf : null;
  }, '取消终态', cancelBeforeHeaders ? 1200 : 30000);
  assert.equal(messageText(cancelled).includes(cancelPartial), !cancelBeforeHeaders);
  assert.ok(!messageText(cancelled).includes('取消后不应出现'));
  assert.equal(providerRequests.filter(item => item.kind === 'cancel').length, 1);
  await fillComposer(port, failPrompt);
  await click(port, '[data-action="save-local-message"]');
  failed = await until(async () => {
    const item = await readConversation(port, workspaceId, conversationId);
    const leaf = item?.messages.find(message => message.id === item.currentLeafId);
    return leaf?.role === 'assistant' && leaf.delivery === 'FAILED' ? leaf : null;
  }, 'HTTP 429 失败终态');
  assert.ok(failed.attemptId);
  assert.equal(providerRequests.filter(item => item.kind === 'fail').length, 1);
  const afterFailureLedger = await invoke(port, 'read_desktop_usage_ledger');
  assert.equal(afterFailureLedger.records.filter(item => item.conversationId === conversationId && item.chargeMicros === 5).length, explicitMemoryConflictRepeat ? 2 : 1);
  const allRequestsBeforeCold = providerRequests.length;
  stop();
  port = await start(true);
  await until(() => evaluate(port, 'Boolean(window.__TAURI__?.core?.invoke)'), '终态同根冷启 IPC');
  finalConversation = await readConversation(port, workspaceId, conversationId);
  assert.ok(finalConversation.messages.some(item => messageText(item) === answer && item.delivery === 'COMPLETE'));
  assert.ok(finalConversation.messages.some(item => item.id === cancelled.id && item.delivery === 'CANCELLED'));
  assert.ok(finalConversation.messages.some(item => item.id === failed.id && item.delivery === 'FAILED'));
  if (autoTitle) {
    assert.equal(finalConversation.title, autoTitleFailed ? submitted.title : '合成会话标题', '标题结果须同根冷启回读');
    assert.equal(finalConversation.autoTitlePending, autoTitleFailed);
    if (autoTitleFailed) {
      const coldTitleLedger = await invoke(port, 'read_desktop_usage_ledger');
      assert.equal(coldTitleLedger.records.filter(item => item.conversationId === conversationId && item.chargeMicros === 7).length, 1, '失败标题用量冷启不得重复');
    }
  }
  assert.equal(providerRequests.length, allRequestsBeforeCold);
  }
  if (configureModelUi) {
    const coldSetting = (await invoke(port, 'read_desktop_model_service_settings')).find(item => item.providerId === configuredProviderId);
    assert.equal(coldSetting?.revision, configuredRevision);
    assert.equal(coldSetting?.credentialStored, true);
    assert.equal(await invoke(port, 'reveal_desktop_model_service_credential', { providerId: configuredProviderId }), configuredKey);
    assert.ok(providerRequests.every(item => item.credentialMatched), '所有已安装实例请求均须使用已保存合成 Key');
  }
  if (manualTerra) {
    const coldSelection = await invoke(port, 'read_desktop_p6g_selection', { workspaceId, conversationId });
    assert.equal(coldSelection?.conversationOverride?.modelId, 'GPT_5_6_TERRA', '手选模型须随会话冷启保留');
  }
  if (memorySuggestion || (explicitMemoryCommand && !rejectedMemoryCommand)) {
    const expectedTitle = memorySuggestion ? '个人项目' : '工作流程偏好';
    await click(port, '[data-action="show-settings"]');
    await click(port, '[data-action="open-settings-page"][data-page="memory-overview"]');
    await until(() => evaluate(port, `Boolean([...document.querySelectorAll('.android-memory-summary-content h2')].find(node => node.textContent === ${JSON.stringify(expectedTitle)}))`), '重启后设置页显示真实记忆主题');
    if (memoryAudit) {
      await click(port, '[data-action="show-memory-audit"]');
      await until(() => evaluate(port, 'document.querySelector("#memory-audit-title")?.textContent.includes("工作流程偏好") && document.querySelector(".dialog")?.textContent.includes("版本 1")'), '记忆来源与修订原生回读');
      assert.equal(await evaluate(port, 'document.querySelector(".dialog ol li p:last-child")?.getBoundingClientRect().height > 0'), true, '历史正文在对话框内可见');
      const auditScreenshot = (await cdp(port, 'Page.captureScreenshot', { format: 'png', captureBeyondViewport: false }))?.data;
      assert.ok(auditScreenshot);
      await writeFile(join(root, 'w12-memory-audit.png'), Buffer.from(auditScreenshot, 'base64'));
      await click(port, '[data-action="close-dialog"]');
      const memory = (await invoke(port, 'read_desktop_workspace', { workspaceId })).exchange.memory.find(item => item.title === '工作流程偏好' && item.status === 'ACTIVE');
      await invoke(port, 'mutate_desktop_domain', { args: { intentId: `synthetic-memory-audit-update-${Date.now()}`, workspaceId,
        entity: 'memory', action: 'update', objectId: memory.id, expectedRevision: memory.revision,
        fields: { body: '合成修订后的工作偏好' } } });
      await evaluate(port, 'location.reload()');
      await until(() => evaluate(port, 'Boolean(window.__TAURI__?.core?.invoke && document.querySelector("[data-action=show-settings]"))'), '修订后界面重载');
      await click(port, '[data-action="show-settings"]');
      await click(port, '[data-action="open-settings-page"][data-page="memory-overview"]');
      await until(() => evaluate(port, 'Boolean(document.querySelector("[data-action=show-memory-audit]"))'), '修订后记忆设置页');
      await click(port, '[data-action="show-memory-audit"]');
      await until(() => evaluate(port, 'document.querySelector(".dialog")?.textContent.includes("版本 2") && document.querySelector(".dialog")?.textContent.includes("版本 1")'), '两版修订可见');
      await click(port, '[data-action="close-dialog"]');
    }
    const memoryScreenshot = (await cdp(port, 'Page.captureScreenshot', { format: 'png', captureBeyondViewport: false }))?.data;
    assert.ok(memoryScreenshot);
    await writeFile(join(root, 'w12-memory-title.png'), Buffer.from(memoryScreenshot, 'base64'));
    await click(port, '[data-action="open-settings-page"][data-page="data"]');
    await until(() => evaluate(port, 'Boolean(document.querySelector("[data-action=start-export][disabled]"))'), '记忆主题工作区导出提前拒绝');
    assert.equal(await evaluate(port, 'document.querySelector(".android-settings-data-page")?.textContent.includes("请使用下方本机备份")'), true);
    const legacyExchange = join(root, 'w12-memory-title-v1.nfai-exchange');
    await assert.rejects(invoke(port, 'export_desktop_workspace_to_selected_path', { workspaceId, selectedPath: legacyExchange }), /无法保留记忆主题/, 'v1 导出不能静默丢失主题');
    assert.equal(existsSync(legacyExchange), false, '拒绝的 v1 导出不得留下交换包');
  }
  let failedTitleAttempt = null;
  if (autoTitleFailed) {
    assert.ok(existsSync(join(root, '.nanfeng-windows-acceptance.json')), '失败标题只读取标记合成根');
    const query = 'import json,sqlite3,sys; c=sqlite3.connect(sys.argv[1]); rows=c.execute("SELECT state,safe_error_code,input_tokens,output_tokens,charge_micros FROM desktop_conversation_title_attempts WHERE conversation_id=?",(sys.argv[2],)).fetchall(); print(json.dumps(rows)); c.close()';
    const rows = JSON.parse(execFileSync('python', ['-c', query, join(root, 'workspace.sqlite3'), conversationId], { encoding: 'utf8', timeout: 10000 }));
    assert.deepEqual(rows, [['FAILED', 'RESPONSE_FORMAT', 3, 8, 7]], '失败标题 Attempt 须保留终态和 Provider 计量');
    failedTitleAttempt = rows[0];
  }
  result = { candidateSha256: expectedHash, root, workspaceId, conversationId,
    configuredModelUi: configureModelUi, configuredRevision, manualTerra,
    disclosedRecipient,
    routeScreenshot,
    ordinaryRequestCount: ordinaryRequests.length, totalProviderRequests: providerRequests.length,
    attachmentInputs: attachmentInputs ? { count: attachmentDraft.attachments.length, attachmentOnly, bothProjected: true, pdfFileProjected: attachmentPdf, coldReadback: true } : null,
    cameraInput: cameraInput ? { count: cameraDraft.attachments.length, sentAsImage: true, coldReadback: true } : null,
    webSearch: withSources, crossArea, originArea, otherWorkspaceId, publicSource: withSources ? publicSource : null,
    contextMode: contextMode ? (emptyContextQuery ? 'empty-query' : contextOtherProject ? 'other-project' : contextProject ? 'project' : contextEnabled ? 'on' : 'off') : null, contextIds,
    historyContext, historyIds, branchEdit, historyTitleRace, historyTitleUi, workEditorStream: workEditorFact,
    historyTitle: historyTitleRace ? { oldTitle: historyTitleFact.oldTitle, newTitle: historyTitleFact.newTitle, coldTitle: coldConversation.title, completedAnswerPreserved: true } : null,
    imeComposer, cancelBeforeHeaders,
    personalizationRace, failedTitleAttempt,
    partialVisible, completedAnswer: answer, chargeMicros: 5, cancelledId: cancelled?.id || null, failedId: failed?.id || null,
    coldLeafId: finalConversation.currentLeafId,
    screenshot: join(root, 'w06-visible-send.png'), pids };
  }
  }
} catch (error) {
  if (root) {
    const failure = { candidateSha256: expectedHash, root, pid: launch?.pid, activeDebugPort, error: String(error.stack || error), providerRequests };
    try { failure.page = await captureFailurePage(); } catch (diagnosticError) { failure.diagnosticError = String(diagnosticError); }
    try { await writeFile(join(root, 'w06-failure-evidence.json'), JSON.stringify(failure, null, 2), { flag: 'wx' }); } catch { /* Preserve the original failure. */ }
  }
  throw error;
} finally {
  releaseHistoryAnswer();
  if (credentialReleaseTimer) clearInterval(credentialReleaseTimer);
  if (personalizationDbLock && personalizationDbLock.exitCode === null) {
    personalizationDbLock.stdin.write('\n');
    personalizationDbLock.kill();
  }
  stop();
  await new Promise(resolveClose => provider.close(resolveClose));
}
console.log(JSON.stringify(result));
