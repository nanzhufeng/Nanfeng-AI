import assert from 'node:assert/strict';
import { createHash } from 'node:crypto';
import { execFileSync, spawn } from 'node:child_process';
import { readFile, writeFile } from 'node:fs/promises';
import http from 'node:http';
import { dirname, join, resolve, sep } from 'node:path';
import { fileURLToPath } from 'node:url';

assert.ok(process.execArgv.includes('--max-old-space-size=2048'));
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
const awaitReceipt = path => ps(`Get-Content -LiteralPath ${quote(path)} -Raw`);
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
const closes = [];
const launches = [];
async function start(reopen = false) {
  const debugPort = await reservePort();
  const args = ['-NoProfile', '-File', join(desktopRoot, 'scripts/start-windows-acceptance.ps1'),
    '-RootParent', rootParent, '-ExecutablePath', source, '-StartupMode', 'normal', '-Visible', '-DebugPort', String(debugPort),
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
      try { const stub=JSON.parse(line); done(JSON.parse(awaitReceipt(stub.launchReceipt))); } catch (cause) { fail(cause); }
      child.stdout.destroy(); child.stderr.destroy(); child.unref();
    });
    child.stderr.on('data', chunk => { error += chunk.toString(); });
    child.on('error', cause => { clearTimeout(timer); fail(cause); });
    child.on('exit', code => { if (!output.includes('"pid"')) { clearTimeout(timer); fail(new Error(`启动失败 ${code}：${error}`)); } });
  });
  assert.equal(result.sha256, receipt.sha256);
  assert.equal(resolve(result.executable), running);
  launch = result; launches.push(result);
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
async function normalClose() {
  const pid = Number(launch.pid);
  const literal = value => `'${String(value).replaceAll("'", "''")}'`;
  const script = `$expected=${literal(running)}; $hash=${literal(receipt.sha256)}; `
    + `$p=Get-CimInstance Win32_Process -Filter "ProcessId=${pid}"; `
    + 'if(!$p -or [IO.Path]::GetFullPath($p.ExecutablePath) -ne $expected -or (Get-FileHash -LiteralPath $expected -Algorithm SHA256).Hash -ne $hash){throw "candidate identity mismatch"}; '
    + `$process=Get-Process -Id ${pid}; $handle=$process.Handle; $title=$process.MainWindowTitle; $responding=$process.Responding; if(!$title -or !$responding){throw \"window unavailable\"}; if(!$process.CloseMainWindow()){throw "close rejected"}; `
    + 'if(!$process.WaitForExit(10000)){throw "normal exit timeout"}; if($process.ExitCode -ne 0){throw "normal exit code"}; '
    + '[pscustomobject]@{pid=$process.Id;exitCode=$process.ExitCode;title=$title;responding=$responding;accepted=$true}|ConvertTo-Json -Compress';
  const close = JSON.parse(ps(script));
  await until(()=>ps(`@(Get-CimInstance Win32_Process -Filter "Name='msedgewebview2.exe'" | Where-Object {$_.CommandLine -and $_.CommandLine.Contains(${quote(launch.webviewProfile)})}).Count`)==='0','owned profile exit');
  stoppedPids.push(pid); launch = null;
  return close;
}

async function cdp(port, method, params = {}) {
  const tabs = await fetch(`http://127.0.0.1:${port}/json/list`).then(response => response.json());
  const tab = tabs.find(item => item.type === 'page' && item.url === 'http://tauri.localhost/');
  assert.ok(tab, 'WebView2 页面未就绪');
  const socket = new WebSocket(tab.webSocketDebuggerUrl);
  try {
    await new Promise((done, fail) => { socket.addEventListener('open', done, { once: true }); socket.addEventListener('error', fail, { once: true }); });
    return await new Promise((done, fail) => {
      const timer = setTimeout(() => fail(new Error(`${method} 超时`)), 20000);
      socket.addEventListener('message', event => {
        const reply = JSON.parse(event.data);
        if (reply.id !== 1) return;
        clearTimeout(timer);
        if (reply.error || reply.result?.exceptionDetails) fail(new Error(reply.result?.exceptionDetails?.exception?.description || reply.result?.exceptionDetails?.text || reply.error?.message));
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

const seedMode=process.argv.includes('--seed');
const existing=process.argv.find(x=>x.startsWith('--existing-root='))?.slice('--existing-root='.length);
const call=async(port,area,command,args={})=>{const r=await evaluate(port,`window.__TAURI__.core.invoke(${JSON.stringify(command)},${JSON.stringify(args)},${JSON.stringify({headers:{'x-nanfeng-data-area':area}})}).catch(error=>({__error:String(error)}))`);if(r?.__error)throw new Error(`${area} ${command}: ${r.__error}`);return r;};
async function snapshots(port,ids){const value={};for(const area of ['CHAT','WORK'])value[area]=await call(port,area,'read_desktop_workspace',{workspaceId:ids[area]});return value;}
async function ui(port,selector){await until(()=>evaluate(port,`Boolean(document.querySelector(${JSON.stringify(selector)}))`),selector);assert.equal(await click(port,selector),true);}
let result;
try{
 if(existing)root=resolve(existing);
 let port=await start(Boolean(existing));await until(()=>evaluate(port,'Boolean(window.__TAURI__?.core?.invoke && globalThis.__nanfengDesktopWorkState?.runtimeInfo?.dataAreasReady)'),'NORMAL ready');
 const seedPath=join(root,'chat-ui-preservation-seed.json');
 if(seedMode){
  const ids={};
  for(const area of ['CHAT','WORK'])ids[area]=(await call(port,area,'ensure_desktop_local_workspace')).summary.id;
  const mutate=(area,entity,fields)=>call(port,area,'mutate_desktop_domain',{args:{intentId:`chat-ui-preserve-${area.toLowerCase()}-${entity}-${Date.now()}`,workspaceId:ids[area],entity,action:'create',objectId:null,expectedRevision:null,fields}});
  await mutate('WORK','project',{title:'合成旧项目保留',description:'UI隐藏不删除代码数据'});
  await mutate('WORK','knowledge',{title:'合成旧知识保留',body:'WORK_KNOWLEDGE_PRESERVED',scope:'GLOBAL',projectId:null,tags:[]});
  await mutate('WORK','memory',{title:'合成旧WORK记忆保留',body:'WORK_MEMORY_PRESERVED',scope:'GLOBAL',scopeId:null});
  await mutate('CHAT','memory',{title:'合成聊天记忆保留',body:'CHAT_MEMORY_PRESERVED',scope:'GLOBAL',scopeId:null});
  const conversation=await mutate('CHAT','conversation',{title:'聊天记录保留',firstMessage:'CHAT_MESSAGE_PRESERVED',projectId:null});
  const seed={candidateSha256:receipt.sha256,root,ids,conversationId:conversation.objectId,snapshots:await snapshots(port,ids)};
  closes.push(await normalClose());seed.normalCloseReceipts=closes;seed.launches=launches;await writeFile(seedPath,JSON.stringify(seed,null,2));result=seed;
 }else{
  assert.ok(existing,'verify requires the exact seeded marked root');const seed=JSON.parse(await readFile(seedPath,'utf8'));
  assert.notEqual(receipt.sha256,seed.candidateSha256,'must use rebuilt UI candidate');
  assert.deepEqual(await snapshots(port,seed.ids),seed.snapshots);
  const observations=[];
  for(let round=0;round<2;round++){
   if(round){port=await start(true);await until(()=>evaluate(port,'Boolean(window.__TAURI__?.core?.invoke && globalThis.__nanfengDesktopWorkState?.runtimeInfo?.dataAreasReady)'),'cold ready');}
   await until(()=>evaluate(port,'Boolean(document.querySelector("#chat-composer"))'),'chat surface');
   assert.equal(await evaluate(port,'document.querySelectorAll("[data-action=show-work],[data-action=show-projects],[data-action=show-knowledge],[data-action=show-p8-inspect],[data-action=select-workspace]").length'),0);
   await ui(port,`[data-action="select-chat"][data-id="${seed.conversationId}"]`);await until(()=>evaluate(port,'document.querySelector(".chat-scroll")?.textContent.includes("CHAT_MESSAGE_PRESERVED")'),'preserved chat visible');
   await ui(port,'[data-action="show-settings"]');await until(()=>evaluate(port,'Boolean(document.querySelector(".android-settings-main"))'),'settings');
   for(const page of ['personalization','memory-overview','reminders','data','privacy','about','feature-review']){
    await ui(port,`[data-action="open-settings-page"][data-page="${page}"]`);
    await until(()=>evaluate(port,`globalThis.__nanfengDesktopWorkState?.settingsSection===${JSON.stringify(page)}`),'settings destination');
    assert.equal(await evaluate(port,'document.querySelectorAll("[data-page=workspace],[data-page=development],[data-action=show-projects],[data-action=show-knowledge],[data-action=show-p8-inspect],[data-action=start-import],[data-action=start-export],[data-action=select-nanfeng-knowledge-export],[data-action=select-local-backup-area][data-area=WORK]").length'),0);
    if(page==='memory-overview')await until(()=>evaluate(port,'document.querySelector(".android-settings-secondary")?.textContent.includes("CHAT_MEMORY_PRESERVED")'),'chat memory visible');
    if(page==='about')assert.ok((await evaluate(port,'document.querySelector(".android-settings-secondary")?.textContent')).includes('聊天工具'));
    observations.push({round,page,retiredControls:0});
   }
   assert.equal(await evaluate(port,`(()=>{const app=document.querySelector('#app');const node=document.createElement('button');node.dataset.action='show-work';app.append(node);node.click();node.remove();return globalThis.__nanfengDesktopWorkState.dataArea==='CHAT'&&globalThis.__nanfengDesktopWorkState.pane==='settings'})()`),true,'old action cannot revive work UI');
   assert.deepEqual(await snapshots(port,seed.ids),seed.snapshots);closes.push(await normalClose());
  }
  result={candidateSha256:receipt.sha256,previousCandidateSha256:seed.candidateSha256,root,ids:seed.ids,snapshotsPreserved:true,observations,normalCloseReceipts:closes,launches,providerRequests:requests.length,retiredRouteBlocked:true};
  assert.equal(requests.length,0);
 }
}catch(error){if(root)await writeFile(join(root,`chat-ui-failed-${Date.now()}.json`),JSON.stringify({error:String(error),launch,launches,closes},null,2));throw error;}finally{stop();await new Promise(done=>provider.close(done));}
console.log(JSON.stringify(result));
