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
const readConversation=async(port,workspaceId,conversationId)=>(await invoke(port,'read_desktop_workspace',{workspaceId})).exchange.conversations.find(c=>c.id===conversationId);
function persisted(executionId){
 const query='import sqlite3,json,sys; c=sqlite3.connect("file:"+sys.argv[1]+"?mode=ro",uri=True); print(json.dumps(c.execute("SELECT state FROM desktop_compare_executions WHERE execution_id=?",(sys.argv[2],)).fetchall())); c.close()';
 return JSON.parse(execFileSync('python',['-c',query,join(root,'workspace.sqlite3'),executionId],{encoding:'utf8',timeout:10000}));
}
try {
 let port=await start();await until(()=>evaluate(port,'Boolean(window.__TAURI__?.core?.invoke && globalThis.__nanfengDesktopWorkState?.runtimeInfo?.dataAreasReady)'),'原生就绪');
 await invoke(port,'ensure_desktop_local_workspace');stop();
 let fixtureOutput;
 try { fixtureOutput=execFileSync('cargo',['test','--manifest-path',join(desktopRoot,'src-tauri/Cargo.toml'),'--lib','tests::emit_marked_windows_historical_compare_fixture','--','--ignored','--exact','--test-threads=1'],{cwd:desktopRoot,env:{...process.env,NANFENG_AI_SYNTHETIC_COMPARE_FIXTURE_ROOT:root},encoding:'utf8',timeout:120000}); } catch(error) { await writeFile(join(root,'synthetic-historical-compare-fixture-test.txt'),String(error.stdout || '')); throw new Error('Synthetic fixture failed; see '+root); }
 await writeFile(join(root,'synthetic-historical-compare-fixture-test.txt'),fixtureOutput);
 const {workspaceId,conversationId,executionId}=JSON.parse(await readFile(join(root,'synthetic-historical-compare-receipt.json'),'utf8'));
 port=await start(true);await until(()=>evaluate(port,'Boolean(window.__TAURI__?.core?.invoke && globalThis.__nanfengDesktopWorkState?.runtimeInfo?.dataAreasReady)'),'历史正常启动');
 const current=await readConversation(port,workspaceId,conversationId);
 const branches=current.messages.filter(m=>m.compareExecutionId===executionId);
 assert.equal(branches.length,2);assert.equal(branches.find(m=>m.compareLogicalModel==='CHATGPT').delivery,'COMPLETE');assert.equal(branches.find(m=>m.compareLogicalModel==='CHATGPT').chargeMicros,17);
 const interrupted=branches.find(m=>m.compareLogicalModel==='CLAUDE');assert.equal(interrupted.delivery,'UNKNOWN');assert.equal(interrupted.safeErrorCode,'PROCESS_INTERRUPTED');
 assert.deepEqual(persisted(executionId),[['UNKNOWN']]);
 const ledger=await invoke(port,'read_desktop_usage_ledger');assert.equal(ledger.records.filter(r=>r.conversationId===conversationId&&r.chargeMicros===17).length,1);
 await assert.rejects(invoke(port,'submit_desktop_compare',{args:{}}),/not allowed|Command not found/);
 await assert.rejects(invoke(port,'retry_desktop_compare_branch',{args:{}}),/not allowed|Command not found/);
 assert.equal(await invoke(port,'cancel_desktop_compare',{args:{executionId}}),false);
 await until(()=>evaluate(port,`Boolean(document.querySelector('[data-action="select-chat"][data-id="${conversationId}"]'))`),'历史会话');
 assert.equal(await click(port,`[data-action="select-chat"][data-id="${conversationId}"]`),true);
 await until(()=>evaluate(port,`Boolean(document.querySelector('[data-action="open-assistant-message-menu"][data-message-id="${current.currentLeafId}"]'))`),'当前历史分支菜单');
 if(current.currentLeafId!==interrupted.id) {
  await click(port,`[data-action="open-assistant-message-menu"][data-message-id="${current.currentLeafId}"]`);
  await click(port,'.assistant-message-menu [data-action="show-conversation-branches"]');
  await until(()=>evaluate(port,`Boolean(document.querySelector('[data-action="select-conversation-branch"][data-leaf-id="${interrupted.id}"]'))`),'中断历史叶');
  assert.equal(await click(port,`[data-action="select-conversation-branch"][data-leaf-id="${interrupted.id}"]`),true);
 }
 await until(()=>evaluate(port,'document.querySelector(".chat-scroll")?.textContent.includes("合成Claude中断前部分正文")'),'历史中断正文可见');
 const visible=await evaluate(port,'document.querySelector(".chat-scroll")?.textContent');
 for(const text of ['Claude','连接结果未知，未自动重发','历史 Compare 不再提供重试'])assert.ok(visible.includes(text),text);
 assert.equal(await evaluate(port,'document.querySelectorAll("[data-action=open-compare-confirmation],[data-action=retry-desktop-compare-branch],[data-action=stop-desktop-compare]").length'),0);
 const completed=branches.find(m=>m.compareLogicalModel==='CHATGPT');
 assert.equal(await click(port,`[data-action="open-assistant-message-menu"][data-message-id="${interrupted.id}"]`),true);
 await until(()=>evaluate(port,'Boolean(document.querySelector(".assistant-message-menu [data-action=show-conversation-branches]"))'),'历史分支菜单');
 await click(port,'.assistant-message-menu [data-action="show-conversation-branches"]');
 await until(()=>evaluate(port,`Boolean(document.querySelector('[data-action="select-conversation-branch"][data-leaf-id="${completed.id}"]'))`),'完成分支入口');
 assert.equal(await click(port,`[data-action="select-conversation-branch"][data-leaf-id="${completed.id}"]`),true);
 await until(()=>evaluate(port,'document.querySelector(".chat-scroll")?.textContent.includes("合成ChatGPT已完成分支")'),'完成分支可见');
 const completedVisible=await evaluate(port,'document.querySelector(".chat-scroll")?.textContent');
 assert.ok(completedVisible.includes('ChatGPT'));assert.ok(completedVisible.includes('¥0.0001'));
 assert.equal(await click(port,`[data-action="open-assistant-message-menu"][data-message-id="${completed.id}"]`),true);
 await click(port,'.assistant-message-menu [data-action="show-conversation-branches"]');
 await until(()=>evaluate(port,`Boolean(document.querySelector('[data-action="select-conversation-branch"][data-leaf-id="${interrupted.id}"]'))`),'中断分支入口');
 assert.equal(await click(port,`[data-action="select-conversation-branch"][data-leaf-id="${interrupted.id}"]`),true);
 await until(()=>evaluate(port,'document.querySelector(".chat-scroll")?.textContent.includes("合成Claude中断前部分正文")'),'恢复中断分支');
 stop();port=await start(true);await until(()=>evaluate(port,'Boolean(window.__TAURI__?.core?.invoke)'),'再次冷启');
 assert.deepEqual((await readConversation(port,workspaceId,conversationId)).messages,current.messages);assert.deepEqual(await invoke(port,'read_desktop_usage_ledger'),ledger);assert.deepEqual(persisted(executionId),[['UNKNOWN']]);assert.equal(requests.length,0);
 assert.equal((await readConversation(port,workspaceId,conversationId)).currentLeafId,interrupted.id);
 await until(()=>evaluate(port,`Boolean(document.querySelector('[data-action="select-chat"][data-id="${conversationId}"]'))`),'冷启历史导航');
 assert.equal(await click(port,`[data-action="select-chat"][data-id="${conversationId}"]`),true);
 await until(()=>evaluate(port,'document.querySelector(".chat-scroll")?.textContent.includes("合成Claude中断前部分正文")'),'冷启可见历史正文');
 result={candidateSha256:receipt.sha256,root,workspaceId,conversationId,executionId,storageOwnerFixture:true,completedBranchChargeMicros:17,interruptedUnknown:true,visibleHistory:true,retiredAclDenied:true,inactiveCancel:false,coldReadback:true,providerRequests:requests.length,stoppedPids};
}finally{stop();await new Promise(done=>provider.close(done));}
console.log(JSON.stringify(result));
