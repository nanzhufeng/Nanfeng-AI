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
      if (body.stream === false) {
        if (body.model === 'deepseek-flash') {
          if (process.argv.includes('--explicit-failure')) { response.writeHead(429); response.end('{}'); }
          else request.socket.destroy();
        } else { response.writeHead(200, {'content-type':'application/json'}); response.end(JSON.stringify({model:body.model,choices:[{message:{content:'{"title":"合成会话标题"}'},finish_reason:'stop'}],usage:{prompt_tokens:3,completion_tokens:4}})); }
        return;
      }
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
const explicitFailure=process.argv.includes('--explicit-failure');
function rows(id) {
 const query='import sqlite3,json,sys; c=sqlite3.connect("file:"+sys.argv[1]+"?mode=ro",uri=True); print(json.dumps(c.execute("SELECT provider_id,state,safe_error_code FROM desktop_conversation_title_attempts WHERE conversation_id=? ORDER BY rowid",(sys.argv[2],)).fetchall())); c.close()';
 return JSON.parse(execFileSync('python',['-c',query,join(root,'workspace.sqlite3'),id],{encoding:'utf8',timeout:10000}));
}
try {
 let port=await start();
 await until(()=>evaluate(port,'Boolean(window.__TAURI__?.core?.invoke && globalThis.__nanfengDesktopWorkState?.runtimeInfo?.dataAreasReady)'),'原生就绪');
 const workspaceId=(await invoke(port,'ensure_desktop_local_workspace')).summary.id;
 for(const [providerId,presetId] of [['DEEPSEEK','DEEPSEEK_V4_FLASH'],['QWEN','QWEN_3_6_FLASH']]) {
  const settings=await invoke(port,'read_desktop_model_service_settings');
  await invoke(port,'save_desktop_model_service_settings',{args:{providerId,presetId,enabled:true,expectedRevision:settings.find(x=>x.providerId===providerId).revision,apiKey:'nfai-synthetic-title-chain-'+providerId}});
 }
 await evaluate(port,'globalThis.__titleOld=true;location.reload()');
 await until(()=>evaluate(port,'!globalThis.__titleOld && globalThis.__nanfengDesktopWorkState?.runtimeInfo?.dataAreasReady && Boolean(document.querySelector("#chat-composer"))'),'配置重载');
 await click(port,'[data-action="new-chat"]');
 await until(()=>evaluate(port,'Boolean(document.querySelector("#chat-composer"))'),'输入框');
 assert.equal(await click(port,'[data-action="toggle-composer-add"]'),true);
 await until(()=>evaluate(port,'Boolean(document.querySelector("[data-action=toggle-conversation-web-search]"))'),'联网开关');
 if(await evaluate(port,'document.querySelector("[data-action=toggle-conversation-web-search]")?.getAttribute("aria-checked")==="true"')) await click(port,'[data-action="toggle-conversation-web-search"]');
 await until(()=>evaluate(port,'document.querySelector("[data-action=toggle-conversation-web-search]")?.getAttribute("aria-checked")==="false"'),'搜索关闭');
 await click(port,'[data-action="close-composer-add"]');
 await evaluate(port,'(()=>{const n=document.querySelector("#chat-composer");n.value="合成标题链故障验证";n.dispatchEvent(new Event("input",{bubbles:true}))})()');
 await until(()=>evaluate(port,'Boolean(document.querySelector("[data-action=save-local-message]:not([disabled])"))'),'发送可用');
 assert.equal(await click(port,'[data-action="save-local-message"]'),true);
 const completed=await until(async()=>{const w=await invoke(port,'read_desktop_workspace',{workspaceId});return w.exchange.conversations.find(c=>c.id!=='conversation-p6a-01'&&c.messages.some(m=>m.role==='assistant'&&m.delivery==='COMPLETE'))},'回答完成');
 const conversationId=completed.id;
 await until(()=>{const r=rows(conversationId);return explicitFailure?r.length===2&&r[1][1]==='COMPLETED':r.length&&r[0][1]==='UNKNOWN'},'标题终态');
 await new Promise(done=>setTimeout(done,1500));
 const savedRows=rows(conversationId);
 assert.deepEqual(savedRows.map(r=>r.slice(0,2)),explicitFailure?[['DEEPSEEK','FAILED'],['QWEN','COMPLETED']]:[['DEEPSEEK','UNKNOWN']]);
 assert.equal(requests.filter(r=>r.stream===false).length,explicitFailure?2:1);
 const before=(await invoke(port,'read_desktop_workspace',{workspaceId})).exchange.conversations.find(c=>c.id===conversationId);const requestCount=requests.length;
 stop();port=await start(true);await until(()=>evaluate(port,'Boolean(window.__TAURI__?.core?.invoke)'),'冷启');
 const cold=(await invoke(port,'read_desktop_workspace',{workspaceId})).exchange.conversations.find(c=>c.id===conversationId);
 assert.deepEqual(cold.messages,before.messages);assert.deepEqual(rows(conversationId),savedRows);assert.equal(cold.title,before.title);assert.equal(cold.autoTitlePending,!explicitFailure);assert.equal(requests.length,requestCount);
 result={candidateSha256:receipt.sha256,root,workspaceId,conversationId,visibleSend:true,explicitFailure,titleRows:savedRows,requestCount,coldReadback:true,stoppedPids};
} finally {stop();await new Promise(done=>provider.close(done));}
console.log(JSON.stringify(result));
