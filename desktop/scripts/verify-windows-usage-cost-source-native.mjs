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
      const input=JSON.stringify(body.messages.filter(m=>m.role==='user').at(-1)?.content);
      const actualModel=input.includes('未知费用')?'synthetic-unknown-model':body.model;
      const usage={prompt_tokens:100,completion_tokens:8};
      if(input.includes('实报费用'))usage.cost=0.000017;
      const failed=input.includes('失败费用');if(failed)usage.cost=0.000007;
      response.writeHead(200, { 'content-type': 'text/event-stream; charset=utf-8' });
      response.end(`data: ${JSON.stringify({ model:actualModel, choices: [{ delta: { content: 'W12 合成长路径回答' }, finish_reason: failed?'length':'stop' }], usage })}\n\ndata: [DONE]\n\n`);
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
async function showCost(port){
 await until(()=>evaluate(port,'Boolean(document.querySelector("[data-action=show-settings]"))'),'设置入口');
 assert.equal(await click(port,'[data-action="show-settings"]'),true);
 await until(()=>evaluate(port,'Boolean(document.querySelector("[data-action=open-settings-page][data-page=model]"))'),'模型与联网入口');
 assert.equal(await click(port,'[data-action="open-settings-page"][data-page="model"]'),true);
 await until(()=>evaluate(port,'Boolean(document.querySelector("[data-action=open-settings-page][data-page=conversation-cost]"))'),'费用入口');
 assert.equal(await click(port,'[data-action="open-settings-page"][data-page="conversation-cost"]'),true);
 await until(()=>evaluate(port,'document.querySelectorAll(".android-settings-ledger-row").length===4'),'四条费用明细');
 const rows=await evaluate(port,'[...document.querySelectorAll(".android-settings-ledger-row")].map(n=>n.textContent)');
 assert.equal(rows.filter(x=>x.includes('服务商报告')).length,2);
 assert.equal(rows.filter(x=>x.includes('本地估算')).length,1);
 assert.equal(rows.filter(x=>x.includes('费用来源未确认')&&x.includes('金额未知')).length,1);
 assert.ok(rows.filter(x=>!x.includes('金额未知')).every(x=>x.includes('约 ¥')));
 assert.equal(await evaluate(port,'[...document.querySelectorAll(".android-settings-helper")].filter(n=>n.textContent.includes("2026-08-27")).length'),1);
 assert.ok((await evaluate(port,'document.querySelector(".android-settings-ledger-summary")?.textContent')).includes('1 笔金额未知'));
 assert.equal(rows.filter(x=>x.includes('失败 · OUTPUT_LIMIT')&&x.includes('模型耗时未记录')).length,1);
 assert.equal(rows.filter(x=>x.includes('已完成')&&!x.includes('模型耗时未记录')).length,3);
 return rows;
}
try{
 let port=await start();await until(()=>evaluate(port,'Boolean(window.__TAURI__?.core?.invoke && globalThis.__nanfengDesktopWorkState?.runtimeInfo?.dataAreasReady)'),'原生就绪');
 const workspaceId=(await invoke(port,'ensure_desktop_local_workspace')).summary.id;
 const conversationIds=[];
 for(const text of ['合成实报费用','合成本地估算费用','合成未知费用','合成失败费用']){
  const preview=await invoke(port,'preview_desktop_ordinary_chat_egress',{args:{workspaceId,conversationId:null,text,attachmentIds:[],modelId:null,webSearchOverride:false}});
  const egressAuthorization={approvedAtMs:Date.now(),disclosureVersion:'normal-chat-egress-v2',clientSubmissionId:'usage-cost-'+Date.now(),draftFingerprint:preview.draftFingerprint,recipientChoiceId:preview.recipientChoiceId,recipientProviderId:preview.recipientProviderId,recipientModelId:preview.recipientModelId};
  const completed=await invoke(port,'submit_desktop_ordinary_chat',{args:{workspaceId,conversationId:null,expectedRevision:null,text,attachmentIds:[],webSearchOverride:false,egressAuthorization}});
  assert.equal(completed.state,text.includes('失败费用')?'FAILED':'COMPLETED');conversationIds.push(completed.conversationId);
 }
 const ledger=await invoke(port,'read_desktop_usage_ledger');assert.equal(ledger.records.length,4);
 assert.equal(ledger.records.filter(x=>x.chargeMicros===17).length,1);assert.equal(ledger.records.filter(x=>x.chargeMicros===null).length,2);
 assert.equal(ledger.records.filter(x=>x.chargeMicros===7&&x.requestState==='FAILED'&&x.safeErrorCode==='OUTPUT_LIMIT'&&x.modelElapsedMs===null).length,1);
 assert.equal(ledger.records.filter(x=>x.requestState==='COMPLETED'&&Number.isSafeInteger(x.modelElapsedMs)&&x.modelElapsedMs>=0).length,3);
 const rows=await showCost(port);assert.equal(requests.length,4);
 stop();port=await start(true);await until(()=>evaluate(port,'Boolean(window.__TAURI__?.core?.invoke && globalThis.__nanfengDesktopWorkState?.runtimeInfo?.dataAreasReady)'),'冷启');
 assert.deepEqual(await invoke(port,'read_desktop_usage_ledger'),ledger);assert.deepEqual(await showCost(port),rows);assert.equal(requests.length,4);
 result={candidateSha256:receipt.sha256,root,workspaceId,conversationIds,requestPreparation:'native IPC',visibleCostPage:true,providerReportedRows:2,estimatedRows:1,unknownRows:1,completedDurationRows:3,failedMeasuredRows:1,failedDurationAbsent:true,rawLedgerPreserved:true,coldVisibleReadback:true,providerRequests:requests.length,stoppedPids};
}finally{stop();await new Promise(done=>provider.close(done));}
console.log(JSON.stringify(result));
