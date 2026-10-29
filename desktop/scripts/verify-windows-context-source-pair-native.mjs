import { packageExchange, preflightPackage, semanticHash, sha256 } from '../../protocol/scripts/exchange-lib.mjs';
import assert from 'node:assert/strict';
import { createHash } from 'node:crypto';
import { execFileSync, spawn } from 'node:child_process';
import { readFile, writeFile } from 'node:fs/promises';
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
  const exchange = JSON.parse(await readFile(resolve(desktopRoot, '../protocol/fixtures/nfai.exchange.v1.golden.json'), 'utf8'));
  const original = structuredClone(exchange.conversations[0]);
  const sharedId = 'knowledge-shared-message-id';
  const knowledgeBody = '协议资料'.repeat(70);
  const historyBody = '协议历史'.repeat(500);
  const conversation = (id, title, messageId, text) => {
    const value = structuredClone(original);
    value.id = id; value.title = title; value.projectId = null; value.currentLeafId = messageId;
    const message = structuredClone(original.messages[0]);
    message.id = messageId; message.parentId = null;
    message.blocks = [{kind:'TEXT',ordinal:0,text}];
    value.messages = [message];
    return value;
  };
  exchange.projects = []; exchange.memory = []; exchange.relations = [];
  exchange.conversations = [conversation('conversation-budget-current','预算来源问答','message-budget-start','原始问题'),
    conversation('conversation-budget-history','协议历史',sharedId,historyBody)];
  const knowledge = exchange.knowledge[0];
  knowledge.id = sharedId; knowledge.title = '协议资料'; knowledge.body = knowledgeBody;
  knowledge.contentHash = sha256(Buffer.from(knowledgeBody));
  exchange.export.id = 'export-budget-source-pair'; exchange.export.semanticHash = semanticHash(exchange);
  const bytes = packageExchange(exchange); preflightPackage(bytes);
  const selectedPath = join(root, 'synthetic-budget-source-pair.nfai-exchange');
  await writeFile(selectedPath, bytes);
  const staged = await invoke(port,'stage_preflight_selected_exchange',{selectedPath});
  const imported = await invoke(port,'import_staged_exchange_as_new_workspace',{args:{stagingId:staged.stagingId,workspaceTitle:'预算来源合成工作区'}});
  const workspaceId = imported.summary.id;
  const conversationId = 'conversation-budget-current';
  assert.ok(imported.exchange.knowledge.some(item=>item.id===sharedId));
  assert.ok(imported.exchange.conversations.find(item=>item.id==='conversation-budget-history').messages.some(item=>item.id===sharedId));
  const settings = await invoke(port,'read_desktop_app_settings');
  await invoke(port,'save_desktop_app_settings',{args:{appearance:settings.appearance,product:{...settings.product,historyLibraryEnabled:true},expectedRevision:settings.revision}});
  const catalog = await invoke(port,'read_desktop_p6g_catalog');
  await invoke(port,'upsert_desktop_p6g_catalog_candidate',{args:{expectedRevision:catalog.revision,catalogVersion:'bg32-budget',policyVersion:1,
    candidate:{providerFamily:'OPENAI',providerId:'OPENROUTER',modelId:'GPT_5_6_TERRA',displayName:'GPT-5.6 Terra',tiers:['BALANCED'],capabilities:['TEXT'],available:true,knownCostMicros:null,latencyRank:1,contextWindowTokens:1024,maxOutputTokens:256}}});
  await evaluate(port,'globalThis.__bg32Old=true;location.reload();true');
  await until(()=>evaluate(port,'globalThis.__bg32Old!==true && Boolean(globalThis.__nanfengDesktopWorkState?.runtimeInfo?.dataAreasReady)'),'新文档就绪');
  await until(()=>evaluate(port,`Boolean(document.querySelector('[data-action="select-workspace"][data-id="${workspaceId}"]'))`),'导入工作区入口');
  assert.equal(await click(port, `[data-action="select-workspace"][data-id="${workspaceId}"]`),true);
  await until(()=>evaluate(port,`Boolean(document.querySelector('[data-action="select-chat"][data-id="${conversationId}"]'))`),'导入会话入口');
  assert.equal(await click(port,`[data-action="select-chat"][data-id="${conversationId}"]`),true);
  await until(()=>evaluate(port,`Boolean(document.querySelector('.chat-scroll[data-conversation-id="${conversationId}"]'))`),'问答正文');
  assert.equal(await click(port,'[data-action="toggle-composer-add"]'),true);
  await until(()=>evaluate(port,'Boolean(document.querySelector("[data-action=toggle-conversation-web-search]"))'),'联网偏好');
  if(await evaluate(port,'document.querySelector("[data-action=toggle-conversation-web-search]")?.getAttribute("aria-checked")')!=='false') assert.equal(await click(port,'[data-action="toggle-conversation-web-search"]'),true);
  await until(()=>evaluate(port,'document.querySelector("[data-action=toggle-conversation-web-search]")?.getAttribute("aria-checked")==="false"'),'关闭合成联网');
  assert.equal(await click(port,'[data-action="close-composer-add"]'),true);
  await evaluate(port,'(()=>{const field=document.querySelector("#chat-composer");field.value="协议";field.dispatchEvent(new Event("input",{bubbles:true}));return true})()');
  await until(()=>evaluate(port,'Boolean(document.querySelector("[data-action=save-local-message]:not([disabled])"))'),'发送可用');
  assert.equal(await click(port,'[data-action="save-local-message"]'),true);
  const sent = await until(()=>requests.find(item=>JSON.stringify(item.messages).includes(knowledgeBody)),'实际资料请求');
  assert.equal(JSON.stringify(sent.messages).includes(historyBody),false);
  assert.equal(sent.max_tokens,256);
  const completed = await until(async()=>{
    const projection=await invoke(port,'read_desktop_workspace',{workspaceId});
    const value=projection.exchange.conversations.find(item=>item.id===conversationId);
    return value?.messages.some(item=>item.role==='assistant'&&item.delivery==='COMPLETE') ? value : null;
  },'完整回答');
  const records = await invoke(port,'read_desktop_ordinary_chat_context_records');
  const record = records.find(item=>item.conversationId===conversationId);
  assert.ok(record);assert.ok(record.selectedSources.some(item=>item.kind==='资料库'&&item.title==='协议资料'));
  assert.equal(record.selectedSources.some(item=>item.kind==='历史对话'&&item.title==='协议历史'),false);
  const readPersistedSources = attemptId => JSON.parse(execFileSync('python', ['-c',
    'import sqlite3,json,sys,pathlib; p=pathlib.Path(sys.argv[1]); c=sqlite3.connect(p.as_uri()+"?mode=ro",uri=True); print(json.dumps(c.execute("SELECT source_kind,source_id,title FROM desktop_ordinary_chat_context_sources WHERE attempt_id=? ORDER BY ordinal",(sys.argv[2],)).fetchall())); c.close()',
    join(root,'workspace.sqlite3'),attemptId],{encoding:'utf8',timeout:10000}));
  const persistedSources=readPersistedSources(record.attemptId);
  assert.ok(persistedSources.some(item=>item[0]==='资料库'&&item[1]===sharedId));
  assert.equal(persistedSources.some(item=>item[0]==='历史对话'&&item[1]===sharedId),false);
  assert.equal(requests.length,1);
  stop();port=await start(true);
  await until(()=>evaluate(port,'Boolean(window.__TAURI__?.core?.invoke)'),'冷启 IPC');
  const cold = (await invoke(port,'read_desktop_ordinary_chat_context_records')).find(item=>item.attemptId===record.attemptId);
  assert.deepEqual(cold,record);
  assert.deepEqual(readPersistedSources(record.attemptId),persistedSources);
  const coldWorkspace = await invoke(port,'read_desktop_workspace',{workspaceId});
  assert.deepEqual(coldWorkspace.exchange.conversations.find(item=>item.id===conversationId).messages,completed.messages);
  await until(()=>evaluate(port,`Boolean(document.querySelector('[data-action="select-workspace"][data-id="${workspaceId}"]'))`),'冷启工作区入口');
  assert.equal(await click(port,`[data-action="select-workspace"][data-id="${workspaceId}"]`),true);
  await until(()=>evaluate(port,`Boolean(document.querySelector('[data-action="select-chat"][data-id="${conversationId}"]'))`),'冷启会话入口');
  assert.equal(await click(port,`[data-action="select-chat"][data-id="${conversationId}"]`),true);
  await until(()=>evaluate(port,`Boolean(document.querySelector('[data-action="open-assistant-message-menu"][data-message-id="${cold.assistantMessageId}"]'))`),'冷启回答菜单');
  assert.equal(await click(port,`[data-action="open-assistant-message-menu"][data-message-id="${cold.assistantMessageId}"]`),true);
  await until(()=>evaluate(port,`Boolean(document.querySelector('[data-action="show-assistant-answer-information"][data-message-id="${cold.assistantMessageId}"]'))`),'回答信息入口');
  assert.equal(await click(port,`[data-action="show-assistant-answer-information"][data-message-id="${cold.assistantMessageId}"]`),true);
  await until(()=>evaluate(port,'document.querySelector(".answer-information-sources")?.textContent.includes("协议资料")'),'冷启实际资料来源');
  assert.equal(await evaluate(port,'document.querySelector(".answer-information-sources")?.textContent.includes("协议历史")'),false);
  assert.equal(requests.length,1);
  result={candidateSha256:receipt.sha256,root,workspaceId,conversationId,sharedId,validNativeImport:true,visibleSend:true,
    retainedKnowledge:true,trimmedHistory:true,sourcePairPreserved:true,coldRecordPreserved:true,coldVisibleSources:true,requestCount:requests.length,stoppedPids};
} finally {
  stop();await new Promise(done=>provider.close(done));
}
console.log(JSON.stringify(result));
