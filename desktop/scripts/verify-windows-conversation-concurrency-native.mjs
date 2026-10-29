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
const exitDrainWork = process.argv.includes('--exit-drain-work');
const requestArea = exitDrainWork ? 'WORK' : 'CHAT';
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
const heldResponses = [];
const provider = http.createServer((request, response) => {
  const chunks = [];
  request.on('data', chunk => chunks.push(chunk));
  request.on('end', () => {
    try {
      const body = JSON.parse(Buffer.concat(chunks).toString('utf8'));
      requests.push(body);
      const finish = () => {
      response.writeHead(200, { 'content-type': 'text/event-stream; charset=utf-8' });
      response.end(`data: ${JSON.stringify({ choices: [{ delta: { content: 'W12 合成长路径回答' }, finish_reason: 'stop' }], usage: { prompt_tokens: 100, completion_tokens: 8, cost: 0.000001 } })}\n\ndata: [DONE]\n\n`);
      };
      if (JSON.stringify(body.messages.filter(m=>m.role==='user').at(-1)?.content).includes('合成并行第一题')) heldResponses.push(finish); else finish();
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
  const value = await evaluate(port, `window.__TAURI__.core.invoke(${JSON.stringify(command)},${JSON.stringify(args)},${JSON.stringify({ headers: { 'x-nanfeng-data-area': requestArea } })}).catch(error=>({__error:String(error)}))`);
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
const conversation = async(port,workspaceId,id)=>(await invoke(port,'read_desktop_workspace',{workspaceId})).exchange.conversations.find(c=>c.id===id);
async function sendUi(port,text) {
 await until(()=>evaluate(port,'Boolean(document.querySelector("[data-action=new-chat]"))'),'新对话入口');
 assert.equal(await click(port,'[data-action="new-chat"]'),true);
 await until(()=>evaluate(port,'Boolean(document.querySelector("#chat-composer"))'),'输入框');
 assert.equal(await click(port,'[data-action="toggle-composer-add"]'),true);
 await until(()=>evaluate(port,'Boolean(document.querySelector("[data-action=toggle-conversation-web-search]"))'),'搜索开关');
 if(await evaluate(port,'document.querySelector("[data-action=toggle-conversation-web-search]")?.getAttribute("aria-checked")==="true"')) await click(port,'[data-action="toggle-conversation-web-search"]');
 await until(()=>evaluate(port,'document.querySelector("[data-action=toggle-conversation-web-search]")?.getAttribute("aria-checked")==="false"'),'搜索关闭');
 await click(port,'[data-action="close-composer-add"]');
 await evaluate(port,`(()=>{const n=document.querySelector('#chat-composer');n.value=${JSON.stringify(text)};n.dispatchEvent(new Event('input',{bubbles:true}))})()`);
 await until(()=>evaluate(port,'Boolean(document.querySelector("[data-action=save-local-message]:not([disabled])"))'),'发送可用');
 assert.equal(await click(port,'[data-action="save-local-message"]'),true);
}
try {
 let port=await start();
 await until(()=>evaluate(port,'Boolean(window.__TAURI__?.core?.invoke && globalThis.__nanfengDesktopWorkState?.runtimeInfo?.dataAreasReady)'),'原生就绪');
 if(exitDrainWork) {
  assert.equal(await click(port,'[data-action="show-work"]'),true);
  await until(()=>evaluate(port,'Boolean(document.querySelector(".chat-sidebar[aria-label=工作导航]"))'),'WORK 入口');
 }
 const workspaceId=(await invoke(port,'ensure_desktop_local_workspace')).summary.id;
 await sendUi(port,'合成并行第一题');
 await until(()=>heldResponses.length===1,'首请求已到达服务商');
 const current=await until(async()=>{const w=await invoke(port,'read_desktop_workspace',{workspaceId});return w.exchange.conversations.find(c=>c.id!=='conversation-p6a-01'&&c.messages.some(m=>m.role==='assistant'&&m.delivery==='PARTIAL'))},'首会话生成中');
 const conversationId=current.id;const user=current.messages.find(m=>m.role==='user');const assistant=current.messages.find(m=>m.id===current.currentLeafId);
 const egressAuthorization={approvedAtMs:Date.now(),disclosureVersion:'normal-chat-egress-v2'};
 for(const [command,args] of [
  ['submit_desktop_ordinary_chat',{workspaceId,conversationId,expectedRevision:current.revision,text:'合成重复发送',attachmentIds:[],egressAuthorization}],
  ['generate_desktop_ordinary_chat_for_saved_user',{workspaceId,conversationId,userMessageId:user.id,expectedRevision:current.revision,egressAuthorization}],
  ['regenerate_desktop_ordinary_chat',{workspaceId,conversationId,assistantMessageId:assistant.id,expectedRevision:current.revision,egressAuthorization}],
 ]) await assert.rejects(invoke(port,command,{args}),/当前会话仍在生成/);
 assert.deepEqual(await conversation(port,workspaceId,conversationId),current);
 assert.equal(requests.length,1);
 if(exitDrainWork) {
  const drainingPid=Number(launch.pid);
  const originalWindow=Number(ps(`(Get-Process -Id ${drainingPid}).MainWindowHandle.ToInt64()`));
  assert.ok(originalWindow>0);
  console.log(JSON.stringify({phase:'before-close',root,pid:drainingPid,native:ps(`Get-Process -Id ${drainingPid}|Select-Object Id,MainWindowHandle,MainWindowTitle|ConvertTo-Json -Compress`),webview:await evaluate(port,'(async()=>({title:document.title,error:globalThis.__nanfengDesktopWorkState?.error,visible:await window.__TAURI__.window.getCurrentWindow().isVisible()}))()')}));
  const processState=()=>JSON.parse(ps(`Add-Type -TypeDefinition 'using System;using System.Runtime.InteropServices;public static class NfExitWindow{[DllImport("user32.dll")]public static extern bool IsWindowVisible(IntPtr h);[DllImport("user32.dll")]public static extern bool IsWindow(IntPtr h);}';$p=Get-Process -Id ${drainingPid} -ErrorAction SilentlyContinue; if($p){@{alive=$true;window=[long]$p.MainWindowHandle;originalExists=[NfExitWindow]::IsWindow([IntPtr]${originalWindow});visible=[NfExitWindow]::IsWindowVisible([IntPtr]${originalWindow})}|ConvertTo-Json -Compress}else{@{alive=$false;window=0;originalExists=$false;visible=$false}|ConvertTo-Json -Compress}`));
  assert.equal(ps(`$p=Get-CimInstance Win32_Process -Filter "ProcessId=${drainingPid}"; if(!$p -or [IO.Path]::GetFullPath([string]$p.ExecutablePath) -ne ${quote(running)} -or (Get-FileHash -LiteralPath ${quote(running)}).Hash -ne ${quote(receipt.sha256)}){throw 'PID identity mismatch'}; (Get-Process -Id ${drainingPid}).CloseMainWindow()`),'True');
  try {
   await until(()=>{const state=processState();assert.equal(state.alive,true,'响应释放前进程不得退出');return !state.visible},'关闭窗口后后台等待');
  } catch(error) {
   console.log(JSON.stringify({phase:'close-timeout',root,pid:drainingPid,process:processState(),webview:await evaluate(port,'(async()=>({title:document.title,error:globalThis.__nanfengDesktopWorkState?.error,text:document.body.innerText.slice(-1200),visible:await window.__TAURI__.window.getCurrentWindow().isVisible()}))()').catch(error=>String(error))}));
   throw error;
  }
  await new Promise(done=>setTimeout(done,2000));
  assert.equal(processState().alive,true);
  assert.equal(requests.length,1);
  heldResponses.shift()();
  await until(()=>!processState().alive,'回答提交后自然退出');
  stoppedPids.push(drainingPid);launch=null;
  port=await start(true);await until(()=>evaluate(port,'Boolean(window.__TAURI__?.core?.invoke && globalThis.__nanfengDesktopWorkState?.runtimeInfo?.dataAreasReady)'),'同根冷启');
  const first=await conversation(port,workspaceId,conversationId);
  assert.equal(first.messages.find(m=>m.id===assistant.id).delivery,'COMPLETE');
  assert.ok(first.messages.find(m=>m.id===assistant.id).blocks.some(block=>block.text==='W12 合成长路径回答'));
  const ledger=await invoke(port,'read_desktop_usage_ledger');
  assert.equal(ledger.records.filter(r=>r.conversationId===conversationId).length,1);
  assert.equal(requests.length,1);
  result={candidateSha256:receipt.sha256,root,workspaceId,conversationId,area:requestArea,normalWindowClose:true,heldProcessStayedAlive:true,naturalExitAfterCompletion:true,requestCount:requests.length,coldReadback:true,ledgerRecords:1,stoppedPids};
 } else {
 await sendUi(port,'合成并行第二题');
 const second=await until(async()=>{const w=await invoke(port,'read_desktop_workspace',{workspaceId});return w.exchange.conversations.find(c=>c.id!==conversationId&&c.id!=='conversation-p6a-01'&&c.messages.some(m=>m.role==='assistant'&&m.delivery==='COMPLETE'))},'第二会话独立完成');
 assert.equal(requests.length,2);
 assert.equal((await conversation(port,workspaceId,conversationId)).messages.find(m=>m.id===assistant.id).delivery,'PARTIAL');
 heldResponses.shift()();
 const first=await until(async()=>{const c=await conversation(port,workspaceId,conversationId);return c.messages.find(m=>m.id===assistant.id).delivery==='COMPLETE'?c:null},'首会话释放后完成');
 const ledger=await invoke(port,'read_desktop_usage_ledger');
 assert.equal(ledger.records.filter(r=>[conversationId,second.id].includes(r.conversationId)).length,2);
 stop();port=await start(true);await until(()=>evaluate(port,'Boolean(window.__TAURI__?.core?.invoke)'),'同根冷启');
 assert.deepEqual((await conversation(port,workspaceId,conversationId)).messages,first.messages);
 assert.deepEqual((await conversation(port,workspaceId,second.id)).messages,second.messages);
 assert.deepEqual(await invoke(port,'read_desktop_usage_ledger'),ledger);
 assert.equal(requests.length,2);
 result={candidateSha256:receipt.sha256,root,workspaceId,conversationIds:[conversationId,second.id],visibleConcurrentSend:true,rejectedActiveCommands:3,requestCount:requests.length,coldReadback:true,stoppedPids};
 }
} finally {while(heldResponses.length)heldResponses.shift()();stop();await new Promise(done=>provider.close(done));}
console.log(JSON.stringify(result));
