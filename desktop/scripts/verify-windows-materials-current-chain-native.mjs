import assert from 'node:assert/strict';
import { createHash, randomUUID } from 'node:crypto';
import { execFileSync, spawn } from 'node:child_process';
import { mkdir, readFile, writeFile } from 'node:fs/promises';
import http from 'node:http';
import { dirname, join, resolve, sep } from 'node:path';
import { fileURLToPath } from 'node:url';

assert.ok(process.execArgv.includes('--max-old-space-size=2048'));
if (process.platform !== 'win32') throw new Error('仅在 Windows 隔离候选上验收。');
const desktopRoot = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const rootParent = resolve(process.argv[2] || 'D:\\CodexTemp');
const autoAttachmentKind = process.argv[3];
const autoRetryOnly = autoAttachmentKind === 'auto-retry';
const qwenAttachmentRetry = autoAttachmentKind === 'qwen-attachment-retry-ui';
const autoRetryUi = autoAttachmentKind === 'auto-retry-ui' || qwenAttachmentRetry;
const autoRoutePreview = autoAttachmentKind === 'auto-route-preview';
const autoAttachmentOnly = ['auto-attachment', 'auto-pdf', 'auto-image', 'auto-mixed'].includes(autoAttachmentKind);
const receipt = JSON.parse(await readFile(join(desktopRoot, 'output/windows-acceptance/current.json'), 'utf8'));
assert.equal(receipt.identifier, 'com.nanzhufeng.ai.desktop.windowsacceptance');
const source = resolve(desktopRoot, 'output/windows-acceptance', receipt.sha256, 'NanfengAI-Windows-Acceptance.exe');
assert.equal(resolve(receipt.executable), source);
assert.equal(createHash('sha256').update(await readFile(source)).digest('hex').toUpperCase(), receipt.sha256);
const runExecutable = resolve(desktopRoot, 'output/windows-acceptance-binaries', receipt.sha256, 'nanfeng-ai-desktop-spike.exe');
const ps = (args) => execFileSync('pwsh', ['-NoProfile', ...args], { encoding: 'utf8', timeout: 60000 }).trim();
const quote = value => `'${String(value).replaceAll("'", "''")}'`;
const launchPowerShell = (args) => new Promise((resolveLaunch, reject) => {
  const child = spawn('pwsh', ['-NoProfile', ...args], { stdio: ['ignore', 'pipe', 'pipe'] });
  let stdout = '';
  let stderr = '';
  const timer = setTimeout(() => { child.kill(); reject(new Error(`隔离候选启动超时：${stderr}`)); }, 30000);
  child.stdout.on('data', data => {
    stdout += data.toString();
    const line = stdout.slice(0, stdout.lastIndexOf('\n') + 1).split(/\r?\n/)
      .find(item => item.trim().startsWith('{') && item.includes('"pid"'));
    if (!line) return;
    clearTimeout(timer);
    try { const emitted = JSON.parse(line); resolveLaunch(JSON.parse(execFileSync('pwsh',['-NoProfile','-Command',`Get-Content -LiteralPath ${quote(emitted.launchReceipt)} -Raw`],{encoding:'utf8'}))); } catch (error) { reject(error); }
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
        idempotencyKey: request.headers['idempotency-key'] || '' });
      if ((autoRetryOnly || autoRetryUi) && index === 1) {
        response.writeHead(503, { 'content-type': 'application/json' });
        response.end(JSON.stringify({ error: { message: 'synthetic retry trigger' } }));
        return;
      }
      if (body.stream === false) {
        response.writeHead(200, { 'content-type': 'application/json' });
        response.end(JSON.stringify({ model: body.model,
          choices: [{ message: { content: '{"title":"附件联网重试验收"}' }, finish_reason: 'stop' }],
          usage: { prompt_tokens: 8, completion_tokens: 4, cost: 0.000004 } }));
        return;
      }
      response.writeHead(200, { 'content-type': 'text/event-stream; charset=utf-8', 'cache-control': 'no-cache' });
      if (qwenAttachmentRetry) response.write(`data: ${JSON.stringify({ web_search: [{ url: 'https://example.com/synthetic-source', title: 'Synthetic source' }] })}\n\n`);
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
const closes = [];
let failed = false;
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
    '-ExecutablePath', source, '-StartupMode', 'normal', '-Visible', '-DebugPort', String(port), '-OrdinaryChatTestEndpoint', `http://127.0.0.1:${providerPort}`];
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
      const timer = setTimeout(() => reject(new Error('CDP evaluation timed out')), 20000);
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
const pdfBytes = Buffer.from('%PDF-1.4\n1 0 obj\n<< /Type /Catalog >>\nendobj\n%%EOF\n', 'utf8');
const imageBytes = Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAIAAAACCAIAAAD91JpzAAAAFElEQVR4nGP8z8DAwMDAxMDAAAANHQEDasKb6QAAAABJRU5ErkJggg==', 'base64');
const textBytes = Buffer.from('W06 合成 TXT 正文，仅用于回环验收。\n', 'utf8');
async function seedSyntheticImageRegistry(root) {
  const model = (id, image) => ({ id, displayName: id, contextWindowTokens: 8192,
    maxOutputTokens: null, inputModalities: image ? ['image', 'text'] : ['text'],
    outputModalities: ['text'], supportedParameters: [], inputMicrosPerToken: null,
    outputMicrosPerToken: null, cachedInputMicrosPerToken: null });
  const models = [model('anthropic/claude-opus-5', false), model('google/gemini-3.8-flash', false),
    model('openai/gpt-5.6-sol', true), model('openai/gpt-5.6-terra', false)];
  const presetMappings = [
    { presetId: 'CLAUDE_OPUS_5', modelId: 'anthropic/claude-opus-5' },
    { presetId: 'GPT_5_6_SOL', modelId: 'openai/gpt-5.6-sol' },
    { presetId: 'GPT_5_6_TERRA', modelId: 'openai/gpt-5.6-terra' },
    { presetId: 'GEMINI_3_8_FLASH', modelId: 'google/gemini-3.8-flash' },
  ];
  const catalogSha256 = createHash('sha256').update(JSON.stringify([models, presetMappings])).digest('hex');
  const catalog = { catalogVersion: catalogSha256.slice(0, 16), catalogSha256,
    sourceEtag: null, models, presetMappings };
  await writeFile(join(root, 'model-registry-openrouter-current-v1.json'), JSON.stringify({
    format: 'nfai.openrouter-registry', version: 1, verifiedAtMs: Date.now(), catalog,
  }));
  return catalog.catalogVersion;
}
const extraTextFixtures = [
  { extension: 'xml', mimeType: 'application/xml', text: '<record>W06 合成 XML 正文</record>' },
  { extension: 'yaml', mimeType: 'text/yaml', text: 'record: W06 合成 YAML 正文' },
  { extension: 'html', mimeType: 'text/html', text: '<p>W06 合成 HTML 正文</p>' },
];
const officeFixtures = [
  { extension: 'docx', mimeType: 'application/vnd.openxmlformats-officedocument.wordprocessingml.document', marker: 'W06 合成 DOCX 完整正文', files: {
    'word/document.xml': '<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"><w:body><w:p><w:r><w:t>W06 合成 DOCX 完整正文</w:t></w:r></w:p></w:body></w:document>',
  } },
  { extension: 'xlsx', mimeType: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet', marker: 'W06 合成 XLSX 单元格', files: {
    'xl/workbook.xml': '<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets><sheet name="Sheet1" sheetId="1" r:id="rId1"/></sheets></workbook>',
    'xl/_rels/workbook.xml.rels': '<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/></Relationships>',
    'xl/worksheets/sheet1.xml': '<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetData><row r="1"><c r="A1" t="inlineStr"><is><t>W06 合成 XLSX 单元格</t></is></c></row></sheetData></worksheet>',
  } },
  { extension: 'pptx', mimeType: 'application/vnd.openxmlformats-officedocument.presentationml.presentation', marker: 'W06 合成 PPTX 幻灯片', files: {
    'ppt/presentation.xml': '<p:presentation xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><p:sldIdLst><p:sldId id="256" r:id="rId1"/></p:sldIdLst></p:presentation>',
    'ppt/_rels/presentation.xml.rels': '<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/slide" Target="slides/slide1.xml"/></Relationships>',
    'ppt/slides/slide1.xml': '<p:sld xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main" xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main"><p:cSld><p:spTree><p:sp><p:txBody><a:p><a:r><a:t>W06 合成 PPTX 幻灯片</a:t></a:r></a:p></p:txBody></p:sp></p:spTree></p:cSld></p:sld>',
  } },
];
const bodyText = request => JSON.stringify(request.body.messages || []);
async function normalClose(){
 const pid=Number(launch.pid);
 const info=JSON.parse(ps(['-Command',`$p=Get-CimInstance Win32_Process -Filter "ProcessId=${pid}";if(!$p -or [IO.Path]::GetFullPath([string]$p.ExecutablePath) -ne ${quote(runExecutable)} -or (Get-FileHash -LiteralPath ${quote(runExecutable)}).Hash -ne ${quote(receipt.sha256)}){throw 'PID identity mismatch'};$n=Get-Process -Id ${pid};@{title=$n.MainWindowTitle;responding=$n.Responding;accepted=$n.CloseMainWindow()}|ConvertTo-Json -Compress`]));
 assert.ok(info.title&&info.responding&&info.accepted);
 await until(()=>ps(['-Command',`@(Get-CimInstance Win32_Process -Filter "ProcessId=${pid}").Count`])==='0','owned natural exit');
 await until(()=>ps(['-Command',`@(Get-CimInstance Win32_Process -Filter "Name='msedgewebview2.exe'" | Where-Object {$_.CommandLine -and $_.CommandLine.Contains(${quote(launch.webviewProfile)})}).Count`])==='0','owned profile exit');
 pids.push(pid);launch=null;return info;
}
let result;
try {
  let port = await start();
  await ready(port);
  const workspaceId = (await invoke(port, 'CHAT', 'ensure_desktop_local_workspace')).summary.id;
  const fixtures = join(root, 'synthetic-material-inputs');
  await mkdir(fixtures, { recursive: true });
  const txtPath = join(fixtures, 'material.txt');
  const pdfPath = join(fixtures, 'material.pdf');
  const imagePath = join(fixtures, 'material.png');
  await writeFile(txtPath, textBytes);
  await writeFile(pdfPath, pdfBytes);
  if (['auto-image', 'auto-mixed'].includes(autoAttachmentKind)) await writeFile(imagePath, imageBytes);
  const txt = await invoke(port, 'CHAT', 'import_desktop_conversation_attachment', {
    args: { workspaceId, selectedPath: txtPath },
  });
  const pdf = await invoke(port, 'CHAT', 'import_desktop_conversation_attachment', {
    args: { workspaceId, selectedPath: pdfPath },
  });
  assert.equal(txt.mimeType, 'text/plain');
  assert.equal(pdf.mimeType, 'application/pdf');
  const image = ['auto-image', 'auto-mixed'].includes(autoAttachmentKind)
    ? await invoke(port, 'CHAT', 'import_desktop_conversation_attachment', {
      args: { workspaceId, selectedPath: imagePath },
    }) : null;
  if (image) assert.equal(image.mimeType, 'image/png');
  async function send(label, attachmentId, presetId, webSearchOverride = false) {
    const attachmentIds = Array.isArray(attachmentId) ? attachmentId : attachmentId == null ? [] : [attachmentId];
    const text = `W06 ${label} 合成发送`;
    const preview = await invoke(port, 'CHAT', 'preview_desktop_ordinary_chat_egress', { args: {
      workspaceId, conversationId: null, text, attachmentIds, modelId: presetId,
      toneOverride: null, webSearchOverride,
    } });
    return invoke(port, 'CHAT', 'submit_desktop_ordinary_chat', { args: {
      workspaceId, conversationId: null, projectId: null, expectedRevision: null,
      text, attachmentIds, modelId: presetId,
      toneOverride: null, webSearchOverride,
      egressAuthorization: { approvedAtMs: Date.now(), disclosureVersion: 'normal-chat-egress-v2',
        clientSubmissionId: randomUUID(), draftFingerprint: preview.draftFingerprint,
        recipientChoiceId: preview.recipientChoiceId, recipientProviderId: preview.recipientProviderId,
        recipientModelId: preview.recipientModelId },
    } });
  }
  if (autoAttachmentKind === 'registry-public') {
    const before = await invoke(port, 'CHAT', 'read_desktop_openrouter_registry_status');
    assert.equal(before.status, 'NOT_VERIFIED');
    const services = await invoke(port, 'CHAT', 'read_desktop_model_service_settings');
    assert.ok(services.every(item => !item.enabled && !item.credentialStored));
    await until(() => evaluate(port, 'Boolean(document.querySelector("[data-action=show-settings]"))'), '设置入口');
    await clickUi(port, '[data-action="show-settings"]');
    await clickUi(port, '[data-action="open-settings-page"][data-page="model"]');
    await clickUi(port, '[data-action="open-settings-page"][data-page="model-configuration"]');
    await clickUi(port, '[data-action="select-model-service-provider"][data-provider-id="OPENROUTER"]');
    await clickUi(port, '[data-action="refresh-openrouter-registry"]');
    await until(() => evaluate(port, 'document.querySelector(".android-settings-page")?.textContent.includes("公开模型目录已核验并保存")'), '公开目录核验可见成功');
    const verified = await invoke(port, 'CHAT', 'read_desktop_openrouter_registry_status');
    assert.equal(verified.status, 'VERIFIED');
    assert.ok(verified.mappedPresetCount >= 4);
    assert.equal(requests.length, 0, '公开目录核验不调用推理回环');
    closes.push(await normalClose());
    port = await start(true);
    await ready(port);
    const cold = await invoke(port, 'WORK', 'read_desktop_openrouter_registry_status');
    assert.deepEqual(cold, verified, '配置目录共享且冷启身份保持');
    assert.deepEqual(await invoke(port, 'CHAT', 'read_desktop_model_service_settings'), services);
    assert.equal(requests.length, 0);
    result = { candidateSha256: receipt.sha256, root, workspaceId, autoTask: 'registry-public-visible',
      catalogVersion: verified.catalogVersion, mappedPresetCount: verified.mappedPresetCount,
      sourceUrl: verified.sourceUrl, inferenceRequestCount: requests.length, credentialsUnchanged: true, pids };
  } else if (autoRoutePreview) {
    const services = await invoke(port, 'CHAT', 'read_desktop_model_service_settings');
    for (const [providerId, presetId] of [['DEEPSEEK', 'DEEPSEEK_V4_FLASH'], ['OPENROUTER', 'GPT_6_ASTRA']]) {
      const current = services.find(item => item.providerId === providerId);
      assert.ok(current && !current.enabled && !current.credentialStored);
      await invoke(port, 'CHAT', 'save_desktop_model_service_settings', { args: {
        providerId, enabled: true, presetId, expectedRevision: current.revision,
        apiKey: `nfai-synthetic-route-${providerId}-${randomUUID()}`,
      } });
    }
    closes.push(await normalClose());
    port = await start(true);
    await ready(port);
    const routes = [];
    const cases = [
      { prompt: 'W06 合成日常问候', expectedProvider: 'DeepSeek 官方直连', expectedModel: 'DeepSeek V4.1 Flash', apiModel: 'deepseek-flash' },
      { prompt: 'W06 请做完整架构审计与根因权衡', expectedProvider: 'OpenRouter', expectedModel: 'GPT-6 Astra', apiModel: 'openai/gpt-6-astra' },
    ];
    for (const item of cases) {
      await until(() => evaluate(port,
        '!globalThis.__nanfengDesktopWorkState?.pendingChatSubmission && !globalThis.__nanfengDesktopWorkState?.ordinarySendPreparing'), '上次发送已结算');
      await until(() => evaluate(port, 'Boolean(document.querySelector("[data-action=new-chat]"))'), '新对话按钮');
      await clickUi(port, '[data-action="new-chat"]');
      await until(() => evaluate(port, 'globalThis.__nanfengDesktopWorkState?.selectedConversationId === null'), '新对话选择');
      await clickUi(port, '[data-action="toggle-composer-add"]');
      await until(() => evaluate(port, 'Boolean(document.querySelector("[data-action=toggle-conversation-web-search]"))'), '联网开关');
      if (await evaluate(port, 'document.querySelector("[data-action=toggle-conversation-web-search]")?.getAttribute("aria-checked")') === 'false') {
        await clickUi(port, '[data-action="toggle-conversation-web-search"]');
        await until(() => evaluate(port,
          'document.querySelector("[data-action=toggle-conversation-web-search]")?.getAttribute("aria-checked") === "true"'), '联网先开启');
      }
      await clickUi(port, '[data-action="toggle-conversation-web-search"]');
      await clickUi(port, '[data-action="close-composer-add"]');
      await until(async () => (await invoke(port, 'CHAT', 'read_desktop_conversation_draft', {
        workspaceId, conversationId: null,
      }))?.webSearchOverride === false, '新对话联网关闭落盘');
      await evaluate(port, `(()=>{const field=document.querySelector('#chat-composer');field.value=${JSON.stringify(item.prompt)};field.dispatchEvent(new Event('input',{bubbles:true}));return true})()`);
      const disclosed = await until(() => evaluate(port,
        'document.querySelector(".chat-composer-route[data-route-ready=true]")?.textContent'), '本次接收方');
      assert.ok(disclosed.includes(item.expectedProvider) && disclosed.includes(item.expectedModel), disclosed);
      assert.equal(await evaluate(port, 'document.querySelector("[data-action=save-local-message]")?.disabled'), false);
      const visibleRouteState = await evaluate(port,
        '({selectedConversationId:globalThis.__nanfengDesktopWorkState?.selectedConversationId,webSearchOverride:globalThis.__nanfengDesktopWorkState?.conversationPreferences?.webSearchOverride,globalSearch:globalThis.__nanfengDesktopWorkState?.productSettings?.webSearchEnabled,draft:globalThis.__nanfengDesktopWorkState?.composerDraft})');
      assert.equal(visibleRouteState.selectedConversationId === null && visibleRouteState.webSearchOverride === false,
        true, JSON.stringify(visibleRouteState));
      routes.push(disclosed);
      await clickUi(port, '[data-action="save-local-message"]');
      await until(() => requests.length === routes.length, 'Auto 原生请求');
      assert.equal(requests.at(-1).model, item.apiModel);
      await until(() => evaluate(port, 'document.querySelector(".chat-scroll")?.textContent?.includes("合成回答")'), 'Auto 回答');
      await until(() => evaluate(port,
        '!globalThis.__nanfengDesktopWorkState?.pendingChatSubmission && !globalThis.__nanfengDesktopWorkState?.ordinarySendPreparing'), '本次发送结算');
    }
    assert.equal(requests.length, 2);
    closes.push(await normalClose());
    port = await start(true);
    await ready(port);
    assert.equal(requests.length, 2, '同根冷启不得重发 Auto 请求');
    result = { candidateSha256: receipt.sha256, root, workspaceId, autoTask: 'route-preview',
      routes, models: requests.map(item => item.model), requestCount: requests.length, pids };
  } else if (autoRetryOnly || autoRetryUi) {
    const retryProvider = qwenAttachmentRetry ? 'QWEN' : 'DEEPSEEK';
    const retryPreset = qwenAttachmentRetry ? 'QWEN_3_7_PLUS' : 'DEEPSEEK_V4_FLASH';
    const current = (await invoke(port, 'CHAT', 'read_desktop_model_service_settings'))
      .find(item => item.providerId === retryProvider);
    assert.ok(current && !current.enabled && !current.credentialStored);
    await invoke(port, 'CHAT', 'save_desktop_model_service_settings', { args: {
      providerId: retryProvider, enabled: true, presetId: retryPreset,
      expectedRevision: current.revision, apiKey: `nfai-synthetic-retry-${randomUUID()}`,
    } });
    const failed = await send('AUTO-RETRY', qwenAttachmentRetry ? txt.id : null, retryPreset, qwenAttachmentRetry);
    assert.equal(failed.state, 'FAILED');
    assert.equal(requests.length, 1);
    if (autoRetryUi) {
      closes.push(await normalClose());
      port = await start(true);
      await ready(port);
      assert.equal(requests.length, 1, '失败后冷启不得自动重发');
      const conversationRow = `[data-action="select-chat"][data-id="${failed.conversationId}"]`;
      await until(() => evaluate(port, `Boolean(document.querySelector(${JSON.stringify(conversationRow)}))`), '失败会话列表');
      await clickUi(port, conversationRow);
      const retryButton = `[data-action="retry-ordinary-chat"][data-attempt-id="${failed.attemptId}"]`;
      await until(() => evaluate(port, `Boolean(document.querySelector(${JSON.stringify(retryButton)}))`), '失败回答重试按钮');
      await clickUi(port, retryButton);
      await until(async () => {
        const conversation = await currentConversation(port, workspaceId, failed.conversationId);
        return conversation?.messages.find(item => item.id === conversation.currentLeafId)?.delivery === 'COMPLETE';
      }, '界面重试完成');
    } else {
      const preview = await invoke(port, 'CHAT', 'preview_desktop_ordinary_chat_retry_egress', {
        args: { attemptId: failed.attemptId },
      });
      assert.equal(preview.recipientChoiceId, 'RETRY_ORIGINAL');
      assert.equal(preview.recipientProviderId, 'DEEPSEEK');
      const retried = await invoke(port, 'CHAT', 'retry_desktop_ordinary_chat', { args: {
        attemptId: failed.attemptId,
        egressAuthorization: { approvedAtMs: Date.now(), disclosureVersion: 'normal-chat-egress-v2',
          clientSubmissionId: randomUUID(), draftFingerprint: preview.draftFingerprint,
          recipientChoiceId: preview.recipientChoiceId, recipientProviderId: preview.recipientProviderId,
          recipientModelId: preview.recipientModelId },
      } });
      assert.equal(retried.state, 'COMPLETED');
      assert.equal(retried.retryCount, 1);
    }
    await until(() => requests.some(item => item.body.stream === false), '重试完成后的独立标题请求');
    const expectedRequests = 3;
    assert.equal(requests.length, expectedRequests);
    assert.equal(requests.filter(item => item.body.stream === true).length, 2);
    assert.equal(requests.filter(item => item.body.stream === false).length, 1);
    assert.equal(requests[1].model, requests[0].model);
    assert.equal(requests[1].idempotencyKey, requests[0].idempotencyKey);
    assert.ok(requests[2].idempotencyKey.startsWith('conversation-title-'));
    if (qwenAttachmentRetry) {
      assert.equal(failed.webSearchRoute, 'QWEN_CHAT_COMPLETIONS');
      assert.equal(requests.filter(item => item.body.stream === true).length, 2);
      assert.equal(requests[2].model, 'qwen3.6-flash');
      assert.ok(requests[2].idempotencyKey.startsWith('conversation-title-'));
      for (const request of requests.filter(item => item.body.stream === true)) {
        assert.equal(request.path, requests[0].path);
        const sentText = request.body.messages.flatMap(message =>
          typeof message.content === 'string' ? [message.content] : message.content.map(block => block.text || '')).join('\n');
        assert.ok(sentText.includes(textBytes.toString('utf8')));
        assert.equal(request.body.enable_search, true);
      }
    }
    closes.push(await normalClose());
    port = await start(true);
    await ready(port);
    const cold = await currentConversation(port, workspaceId, failed.conversationId);
    assert.equal(cold.messages.find(item => item.id === cold.currentLeafId)?.delivery, 'COMPLETE');
    assert.equal(requests.length, expectedRequests, '同根冷启不得再次发送重试请求');
    result = { candidateSha256: receipt.sha256, root, workspaceId, autoTask: qwenAttachmentRetry ? 'qwen-attachment-retry-v2-ui' : autoRetryUi ? 'retry-v2-ui' : 'retry-v2',
      attemptId: failed.attemptId, conversationId: failed.conversationId,
      requestCount: requests.length, ordinaryRequestCount: 2, titleRequestCount: 1,
      requestFacts: requests.map(item => ({ model: item.model, method: item.method, path: item.path,
        stream: item.body.stream, idempotencyKey: item.idempotencyKey })),
      idempotencyKeyReused: true, pids };
  } else if (autoAttachmentOnly) {
    const services = await invoke(port, 'CHAT', 'read_desktop_model_service_settings');
    const providerPresets = [['DEEPSEEK', 'DEEPSEEK_V4_FLASH'], ['QWEN', 'QWEN_3_7_PLUS']];
    if (autoAttachmentKind === 'auto-pdf') providerPresets.push(['OPENROUTER', 'GPT_5_6_TERRA']);
    if (['auto-image', 'auto-mixed'].includes(autoAttachmentKind)) {
      providerPresets.pop();
      providerPresets.push(['OPENROUTER', 'GPT_5_6_TERRA']);
    }
    for (const [providerId, presetId] of providerPresets) {
      const current = services.find(item => item.providerId === providerId);
      assert.ok(current && !current.enabled && !current.credentialStored);
      await invoke(port, 'CHAT', 'save_desktop_model_service_settings', { args: {
        providerId, enabled: true, presetId, expectedRevision: current.revision,
        apiKey: `nfai-synthetic-auto-attachment-${providerId}-${randomUUID()}`,
      } });
    }
    const pdfAuto = autoAttachmentKind === 'auto-pdf';
    const imageAuto = ['auto-image', 'auto-mixed'].includes(autoAttachmentKind);
    const mixedAuto = autoAttachmentKind === 'auto-mixed';
    let registryVersion = null;
    if (imageAuto) {
      registryVersion = await seedSyntheticImageRegistry(root);
      const status = await invoke(port, 'CHAT', 'read_desktop_openrouter_registry_status');
      assert.equal(status.catalogVersion, registryVersion);
      assert.equal(status.status, 'VERIFIED');
    }
    let autoReceipt;
    let visibleRecipient=null;
    if(mixedAuto){
      await cdp(port,'Page.reload',{ignoreCache:true});await ready(port);
      await until(()=>evaluate(port,`globalThis.__nanfengDesktopWorkState?.current?.summary?.id===${JSON.stringify(workspaceId)}`),'workspace UI ready');
      await clickUi(port,'[data-action="new-chat"]');await until(()=>evaluate(port,'Boolean(document.querySelector("[data-action=toggle-composer-add]"))'),'composer ready');await clickUi(port,'[data-action="toggle-composer-add"]');
      await until(()=>evaluate(port,'Boolean(document.querySelector("[data-action=toggle-conversation-web-search]"))'),'web search switch');
      if(await evaluate(port,'document.querySelector("[data-action=toggle-conversation-web-search]").getAttribute("aria-checked")==="true"'))await clickUi(port,'[data-action="toggle-conversation-web-search"]');
      await clickUi(port,'[data-action="close-composer-add"]');
      const materials=[{bytes:pdfBytes,name:'material.pdf',mime:'application/pdf'},{bytes:imageBytes,name:'material.png',mime:'image/png'}];
      for(const [i,m] of materials.entries()){
        assert.equal(await evaluate(port,`(()=>{const n=document.querySelector('#chat-composer');const d=new DataTransfer();const bytes=Uint8Array.from(atob(${JSON.stringify(m.bytes.toString('base64'))}),x=>x.charCodeAt(0));d.items.add(new File([bytes],${JSON.stringify(m.name)},{type:${JSON.stringify(m.mime)}}));const e=new Event('paste',{bubbles:true,cancelable:true});Object.defineProperty(e,'clipboardData',{value:d});n.dispatchEvent(e);return e.defaultPrevented})()`),true);
        await until(()=>evaluate(port,`document.querySelectorAll('.chat-composer-attachments [data-action=remove-composer-attachment]').length===${i+1}`),'mixed attachment cards');
      }
      const prompt='W06 AUTO-MIXED 合成发送';
      await evaluate(port,`(()=>{const n=document.querySelector('#chat-composer');n.value=${JSON.stringify(prompt)};n.dispatchEvent(new Event('input',{bubbles:true}));return true})()`);
      visibleRecipient=await until(()=>evaluate(port,'document.querySelector(".chat-composer-route[data-route-ready=true]")?.textContent'),'exact recipient');
      assert.ok(visibleRecipient.includes('OpenRouter')&&visibleRecipient.includes('GPT-5.6 Sol'),visibleRecipient);
      assert.equal(requests.length,0,'paste and route disclosure do not authorize egress');
      await until(()=>evaluate(port,'Boolean(document.querySelector("[data-action=save-local-message]:not([disabled])"))'),'send enabled');
      await clickUi(port,'[data-action="save-local-message"]');
      const done=await until(async()=>{const w=await invoke(port,'CHAT','read_desktop_workspace',{workspaceId});return w.exchange.conversations.find(x=>x.messages.some(m=>m.role==='user'&&m.blocks.some(b=>b.text===prompt))&&x.messages.some(m=>m.role==='assistant'&&m.delivery==='COMPLETE'));},'mixed UI answer completed');
      autoReceipt={conversationId:done.id};
    }else{
      autoReceipt=await send(pdfAuto?'AUTO-PDF':imageAuto?'AUTO-IMAGE':'AUTO-TXT',pdfAuto?pdf.id:imageAuto?image.id:txt.id,null);
    }
    if(mixedAuto)await until(()=>requests.some(x=>x.body.stream===false),'separate title request');
    const expectedRequests=mixedAuto?2:1;
    assert.equal(requests.length,expectedRequests);
    assert.equal(requests.filter(x=>x.body.stream===true).length,1);
    const request = requests.find(x=>x.body.stream===true);
    assert.equal(request.model, pdfAuto ? 'openai/gpt-5.6-terra' : imageAuto ? 'openai/gpt-5.6-sol' : 'qwen3.7-plus');
    assert.equal(request.method, 'POST');
    if (pdfAuto) {
      const parts = request.body.messages.flatMap(message => Array.isArray(message.content) ? message.content : []);
      const part = parts.find(item => item.type === 'file');
      assert.equal(part?.file?.filename, 'material.pdf');
      assert.equal(part?.file?.file_data, `data:application/pdf;base64,${pdfBytes.toString('base64')}`);
      assert.ok(!JSON.stringify(request.body).includes(pdfPath));
    } else if (imageAuto) {
      assert.ok(JSON.stringify(request.body).includes(`data:image/png;base64,${imageBytes.toString('base64')}`));
      assert.ok(!JSON.stringify(request.body).includes(imagePath));
      if (mixedAuto) {
        const parts = request.body.messages.flatMap(message => Array.isArray(message.content) ? message.content : []);
        const part = parts.find(item => item.type === 'file');
        assert.equal(part?.file?.file_data, `data:application/pdf;base64,${pdfBytes.toString('base64')}`);
        assert.ok(!JSON.stringify(request.body).includes(pdfPath));
      }
    } else {
      assert.ok(JSON.stringify(request.body).includes(textBytes.toString('utf8').trim()));
      assert.ok(!JSON.stringify(request.body).includes(txtPath));
    }
    const conversation = await currentConversation(port, workspaceId, autoReceipt.conversationId);
    assert.equal(conversation.messages.find(item => item.id === conversation.currentLeafId)?.delivery, 'COMPLETE');
    closes.push(await normalClose());
    port = await start(true);
    await ready(port);
    const cold = await currentConversation(port, workspaceId, autoReceipt.conversationId);
    assert.equal(cold.messages.find(item => item.id === cold.currentLeafId)?.delivery, 'COMPLETE');
    assert.equal(requests.length, expectedRequests, '同根冷启不得重发附件或标题');
    result = { candidateSha256: receipt.sha256, root, workspaceId,
      autoTask: pdfAuto ? 'pdf' : mixedAuto ? 'mixed' : imageAuto ? 'image' : 'attachment',
      conversationId: autoReceipt.conversationId, actualModel: request.model, requestCount: requests.length, ordinaryRequestCount:1,titleRequestCount:mixedAuto?1:0,
      materialHash: createHash('sha256').update(pdfAuto ? pdfBytes : mixedAuto ? Buffer.concat([pdfBytes, imageBytes]) : imageAuto ? imageBytes : textBytes).digest('hex'),
      registryVersion, visibleRecipient, actualSendUi:mixedAuto, pasteUi:mixedAuto, pathIsolated: true, pids };
  } else {
  const txtReceipt = await send('TXT', txt.id, 'GPT_5_6_TERRA');
  assert.equal(requests.length, 1);
  assert.equal(requests[0].model, 'openai/gpt-5.6-terra');
  assert.ok(bodyText(requests[0]).includes(textBytes.toString('utf8').trim()));
  assert.ok(!bodyText(requests[0]).includes(txtPath));
  const txtConversation = await currentConversation(port, workspaceId, txtReceipt.conversationId);
  assert.equal(txtConversation.messages.find(item => item.id === txtConversation.currentLeafId)?.delivery, 'COMPLETE');
  const pdfReceipt = await send('PDF', pdf.id, 'GPT_5_6_TERRA');
  assert.equal(requests.length, 2);
  const pdfParts = requests[1].body.messages.flatMap(message => Array.isArray(message.content) ? message.content : []);
  const pdfPart = pdfParts.find(part => part.type === 'file');
  assert.equal(pdfPart?.file?.filename, 'material.pdf');
  assert.equal(pdfPart?.file?.file_data, `data:application/pdf;base64,${pdfBytes.toString('base64')}`);
  assert.ok(!bodyText(requests[1]).includes(pdfPath));
  const pdfConversation = await currentConversation(port, workspaceId, pdfReceipt.conversationId);
  assert.equal(pdfConversation.messages.find(item => item.id === pdfConversation.currentLeafId)?.delivery, 'COMPLETE');
  let rejected = '';
  try { await send('PDF-QWEN-REJECT', pdf.id, 'QWEN_3_7_PLUS'); }
  catch (error) { rejected = String(error); }
  assert.match(rejected, /当前选择的模型不能处理本次附件；未发起请求/);
  assert.equal(requests.length, 2, '不支持的模型不得收到 PDF 原文或替代图');
  const extraConversations = [];
  for (const fixture of extraTextFixtures) {
    const path = join(fixtures, `material.${fixture.extension}`);
    await writeFile(path, fixture.text, 'utf8');
    const attachment = await invoke(port, 'CHAT', 'import_desktop_conversation_attachment', {
      args: { workspaceId, selectedPath: path },
    });
    assert.equal(attachment.mimeType, fixture.mimeType);
    const preview = await invoke(port, 'CHAT', 'read_desktop_text_preview', {
      args: { workspaceId, attachmentId: attachment.id },
    });
    assert.equal(preview.text, fixture.text);
    const receipt = await send(fixture.extension.toUpperCase(), attachment.id, 'GPT_5_6_TERRA');
    const request = requests.at(-1);
    assert.ok(bodyText(request).includes(fixture.text), `${fixture.extension} 原文未传给所选模型`);
    assert.ok(!bodyText(request).includes(path), `${fixture.extension} 本机路径泄漏`);
    const conversation = await currentConversation(port, workspaceId, receipt.conversationId);
    assert.equal(conversation.messages.find(item => item.id === conversation.currentLeafId)?.delivery, 'COMPLETE');
    extraConversations.push(receipt.conversationId);
  }
  assert.equal(requests.length, 5);
  const officeAttachments = [];
  for (const fixture of officeFixtures) {
    const packageDir = join(fixtures, `office-${fixture.extension}`);
    const path = join(fixtures, `material.${fixture.extension}`);
    for (const [name, contents] of Object.entries(fixture.files)) {
      const target = join(packageDir, ...name.split('/'));
      await mkdir(dirname(target), { recursive: true });
      await writeFile(target, contents, 'utf8');
    }
    ps(['-Command', `Add-Type -AssemblyName System.IO.Compression.FileSystem; [IO.Compression.ZipFile]::CreateFromDirectory(${quote(packageDir)}, ${quote(path)})`]);
    const attachment = await invoke(port, 'CHAT', 'import_desktop_conversation_attachment', {
      args: { workspaceId, selectedPath: path },
    });
    assert.equal(attachment.mimeType, fixture.mimeType);
    const preview = await invoke(port, 'CHAT', 'read_desktop_text_preview', {
      args: { workspaceId, attachmentId: attachment.id },
    });
    assert.ok(preview.text.includes(fixture.marker), `${fixture.extension} 本机预览不完整`);
    officeAttachments.push({ ...fixture, id: attachment.id, path });
  }
  const officeReceipt = await send('OFFICE-MIX', [txt.id, ...officeAttachments.map(item => item.id)], 'GPT_5_6_TERRA');
  assert.equal(requests.length, 6);
  const mixedRequest = requests.at(-1);
  assert.ok(bodyText(mixedRequest).includes(textBytes.toString('utf8').trim()));
  for (const fixture of officeAttachments) {
    assert.ok(bodyText(mixedRequest).includes(fixture.marker), `${fixture.extension} 没有完整进入同一次请求`);
    assert.ok(!bodyText(mixedRequest).includes(fixture.path), `${fixture.extension} 本机路径泄漏`);
  }
  const officeConversation = await currentConversation(port, workspaceId, officeReceipt.conversationId);
  assert.equal(officeConversation.messages.find(item => item.id === officeConversation.currentLeafId)?.delivery, 'COMPLETE');
  const ledger = await invoke(port, 'CHAT', 'read_desktop_usage_ledger');
  for (const conversationId of [txtReceipt.conversationId, pdfReceipt.conversationId, ...extraConversations, officeReceipt.conversationId]) {
    const records = ledger.records.filter(item => item.conversationId === conversationId);
    assert.equal(records.length, 1);
    assert.equal(records[0].chargeMicros, 4);
  }
  const latestPdf = await currentConversation(port, workspaceId, pdfReceipt.conversationId);
  const followupPreview = await invoke(port, 'CHAT', 'preview_desktop_ordinary_chat_egress', { args: {workspaceId,conversationId:pdfReceipt.conversationId,text:'W06 PDF 合成文字追问',attachmentIds:[],modelId:null,toneOverride:null,webSearchOverride:null} });
  await invoke(port, 'CHAT', 'submit_desktop_ordinary_chat', { args: {
    workspaceId, conversationId: pdfReceipt.conversationId, projectId: null,
    expectedRevision: latestPdf.revision, text: 'W06 PDF 合成文字追问', attachmentIds: [],
    modelId: null, toneOverride: null, webSearchOverride: null,
    egressAuthorization: { approvedAtMs:Date.now(),disclosureVersion:'normal-chat-egress-v2',clientSubmissionId:randomUUID(),draftFingerprint:followupPreview.draftFingerprint,recipientChoiceId:followupPreview.recipientChoiceId,recipientProviderId:followupPreview.recipientProviderId,recipientModelId:followupPreview.recipientModelId },
  } });
  assert.equal(requests.length, 7);
  const followupRequest = requests.at(-1);
  assert.ok(bodyText(followupRequest).includes('W06 PDF 合成文字追问'));
  assert.ok(!bodyText(followupRequest).includes(pdfBytes.toString('base64')), '旧 PDF 字节不得随追问重发');
  assert.ok(!followupRequest.body.messages.flatMap(message => Array.isArray(message.content) ? message.content : [])
    .some(part => part.type === 'file'), '追问不得再携带旧文件 Part');
  const followupLedger = await invoke(port, 'CHAT', 'read_desktop_usage_ledger');
  assert.equal(followupLedger.records.filter(item => item.conversationId === pdfReceipt.conversationId).length, 2);
  await clickUi(port, '[data-action="show-settings"]');
  await until(() => evaluate(port, 'Boolean(document.querySelector("[data-action=open-settings-page][data-page=feature-review]"))'), '功能审阅入口');
  await clickUi(port, '[data-action="open-settings-page"][data-page="feature-review"]');
  assert.equal(await evaluate(port, 'document.querySelector(".android-settings-page")?.textContent.includes("XML／YAML／HTML 本机文本附件")'), true);
  const beforeRestartRequests = requests.length;
  closes.push(await normalClose());
  port = await start(true);
  await ready(port);
  for (const conversationId of [txtReceipt.conversationId, pdfReceipt.conversationId, ...extraConversations, officeReceipt.conversationId]) {
    const cold = await currentConversation(port, workspaceId, conversationId);
    assert.equal(cold.messages.find(item => item.id === cold.currentLeafId)?.delivery, 'COMPLETE');
  }
  assert.equal(requests.length, beforeRestartRequests);
  result = { candidateSha256: receipt.sha256, root, workspaceId,
    textConversationId: txtReceipt.conversationId, pdfConversationId: pdfReceipt.conversationId,
    textHash: createHash('sha256').update(textBytes).digest('hex'),
    pdfHash: createHash('sha256').update(pdfBytes).digest('hex'),
    extraTextFormats: extraTextFixtures.map(item => item.extension),
    officeCombination: officeFixtures.map(item => item.extension),
    requestCount: requests.length, chargedMicros: 28, oldPdfNotResent: true,
    unsupportedPdfRejected: Boolean(rejected), pids };
  }
} catch (error) {
  failed=true;
  await writeFile(join(root || rootParent, 'ordinary-materials-failure.json'), JSON.stringify({
    root, candidateSha256: receipt.sha256, mode: autoAttachmentKind,
    error: String(error), requests, pids, activePid: launch?.pid,
  }, null, 2));
  console.error(JSON.stringify({ root, mode: autoAttachmentKind, error: String(error) }));
  throw error;
} finally {
  if(launch){if(failed)stop();else closes.push(await normalClose());}
  await new Promise(resolveClose => provider.close(resolveClose));
}
Object.assign(result,{pass:true,startupMode:'NORMAL',realService:false,forcedCleanup:false,closes,requestFacts:requests.map(x=>({model:x.model,method:x.method,path:x.path,idempotencyKeyPresent:Boolean(x.idempotencyKey)}))});
await writeFile(join(root,'ordinary-materials-current-native.json'),JSON.stringify(result,null,2));
await writeFile(join(desktopRoot,'../docs/review/20261004-materials-current-chain',`native-${autoAttachmentKind||'mixed-local'}.json`),JSON.stringify(result,null,2));
console.log(JSON.stringify(result));
