import assert from 'node:assert/strict';
import { createHash } from 'node:crypto';
import { spawn, execFileSync } from 'node:child_process';
import { readFile, writeFile, stat } from 'node:fs/promises';
import { dirname, resolve, join, sep } from 'node:path';
import { fileURLToPath } from 'node:url';
import http from 'node:http';
import net from 'node:net';

assert.equal(process.platform, 'win32');
const desktop = resolve(dirname(fileURLToPath(import.meta.url)), '..');
assert.ok(process.argv[2], 'Explicit owned synthetic parent required');
const parent = resolve(process.argv[2]);
const compare = false;assert.ok(process.execArgv.includes('--max-old-space-size=2048'));
const visible = false;
assert.ok(!(visible && compare));
const receipt = JSON.parse(await readFile(join(desktop, 'output/windows-acceptance-release/current.json'), 'utf8'));
assert.equal(receipt.identifier, 'com.nanzhufeng.ai.desktop.windowsacceptance');
assert.equal(createHash('sha256').update(await readFile(receipt.executable)).digest('hex').toUpperCase(), receipt.sha256);
assert.equal(receipt.buildProfile, 'release');
const executable = resolve(desktop, 'output/windows-acceptance-binaries', receipt.sha256, 'nanfeng-ai-desktop-spike.exe');
const helper = join(desktop, 'scripts/windows-acceptance-cdp-eval.mjs');
let requests = 0;let closedConnections=0;const responses=[];const closes=[];const partialText='SYNTHETIC_STREAM_STOP_PARTIAL 完整保留片段';
const provider = http.createServer(async (request, response) => {
  for await (const _ of request) { /* Never log body. */ }
  requests++;response.on('close',()=>closedConnections++);
  response.writeHead(200, { 'Content-Type': 'text/event-stream' });
  response.write(`data: ${JSON.stringify({model:'fixture-actual',usage:{prompt_tokens:6,prompt_tokens_details:{cached_tokens:1}},choices:[{delta:{content:partialText},finish_reason:null}]})}\n\n`);responses.push(response);
});
await new Promise(done => provider.listen(0, '127.0.0.1', done));
const endpoint = `http://127.0.0.1:${provider.address().port}`;
let launch, root, port;
const pids = [];
const windows = [];
const quote = value => `'${String(value).replaceAll("'", "''")}'`;
const ps = code => execFileSync('pwsh', ['-NoProfile', '-Command', `[Console]::OutputEncoding=[Text.UTF8Encoding]::new($false);${code}`], { encoding: 'utf8', windowsHide: true, timeout: 60000 }).trim();
const privateDesktopGuard=JSON.parse(ps(`& ${quote(join(desktop,'scripts/assert-windows-private-desktop.ps1'))}`));assert.equal(privateDesktopGuard.verified,true);
async function freePort() {
  const server = net.createServer();
  await new Promise(done => server.listen(0, '127.0.0.1', done));
  const value = server.address().port;
  await new Promise(done => server.close(done));
  return value;
}
async function until(read, label) {
  const deadline = Date.now() + 30000;
  while (Date.now() < deadline) {
    try { const value = await read(); if (value) return value; } catch { /* Startup only. */ }
    await new Promise(done => setTimeout(done, 100));
  }
  throw new Error(label);
}
async function evaluate(expression) {
  return await new Promise((done, fail) => {
    const child = spawn(process.execPath, ['--max-old-space-size=2048', helper, String(port)], { windowsHide: true });
    let output = '', errors = '';
    child.stdout.on('data', chunk => output += chunk);
    child.stderr.on('data', chunk => errors += chunk);
    child.on('error', fail);
    child.on('close', code => { try { code === 0 ? done(JSON.parse(output)) : fail(new Error(errors)); } catch (error) { fail(error); } });
    child.stdin.end(expression);
  });
}
async function invoke(command, args = {}) {
  const value = await evaluate(`window.__TAURI__.core.invoke(${JSON.stringify(command)},${JSON.stringify(args)},{headers:{'x-nanfeng-data-area':'CHAT'}}).catch(error=>({__error:String(error)}))`);
  assert.ok(!value?.__error, `${command}: ${value?.__error}`);
  return value;
}
async function start(reopen = false) {
  port = await freePort();
  const code = `[Console]::OutputEncoding=[Text.UTF8Encoding]::new($false); & ${quote(join(desktop, 'scripts/start-windows-acceptance.ps1'))} -RootParent ${quote(parent)} -ExecutablePath ${quote(receipt.executable)} -StartupMode normal -DebugPort ${port} -OrdinaryChatTestEndpoint ${quote(endpoint)}${reopen ? ` -Root ${quote(root)}` : ''}`;
  const args = ['-NoProfile', '-Command', code];
  launch = await new Promise((done, fail) => {
    const child = spawn('pwsh', args, { windowsHide: true });
    let output = '', errors = '';
    child.stdout.setEncoding('utf8'); child.stderr.setEncoding('utf8');
    const timer = setTimeout(() => { child.kill(); fail(new Error(`launcher timeout: ${errors}`)); }, 30000);
    child.stdout.on('data', chunk => {
      output += chunk;
      const line = output.split(/\r?\n/).find(item => item.trim().startsWith('{') && item.includes('"pid"'));
      if (!line) return;
      try { const value = JSON.parse(line); clearTimeout(timer); child.stdout.destroy(); child.stderr.destroy(); child.unref(); done(value); } catch {}
    });
    child.stderr.on('data', chunk => errors += chunk);
    child.on('error', error => { clearTimeout(timer); fail(error); });
    child.on('close', code => { if (!output.includes('"pid"')) { clearTimeout(timer); fail(new Error(`launcher ${code}: ${errors}`)); } });
  });
  assert.equal(launch.visible,false);assert.equal(launch.sha256, receipt.sha256);
  assert.equal(resolve(launch.executable), executable);
  root = resolve(launch.root);
  assert.ok(root.startsWith(parent + sep));
  assert.equal(JSON.parse(await readFile(join(root, '.nanfeng-windows-acceptance.json'), 'utf8')).identifier, receipt.identifier);
  await until(() => evaluate('Boolean(window.__TAURI__?.core?.invoke)'), 'native IPC unavailable');
  await until(() => evaluate('globalThis.__nanfengDesktopWorkState?.runtimeInfo?.dataAreasReady===true'), 'native data areas unavailable');

}
function stop() {
  if (!launch) return;
  const pid = Number(launch.pid);
  ps(`$p=Get-CimInstance Win32_Process -Filter 'ProcessId=${pid}';if($p){if([IO.Path]::GetFullPath([string]$p.ExecutablePath) -ne ${quote(executable)} -or (Get-FileHash -LiteralPath ${quote(executable)} -Algorithm SHA256).Hash -ne ${quote(receipt.sha256)}){throw 'PID mismatch'};$owned=Get-Process -Id ${pid} -ErrorAction Stop;$started=[DateTime]::Parse(${quote(launch.processStartedAt)}).ToUniversalTime();if([Math]::Abs(($owned.StartTime.ToUniversalTime()-$started).TotalMilliseconds) -gt 20){throw 'PID start time mismatch'};Stop-Process -Id ${pid} -Force}`);
  pids.push(pid); launch = null;
}
async function normalClose(){
 const pid=Number(launch.pid);
 const value=JSON.parse(ps(`& ${quote(join(desktop,'scripts/close-windows-hidden-acceptance.ps1'))} -ProcessId ${pid} -Executable ${quote(executable)} -Sha256 ${quote(receipt.sha256)} -StartedAt ${quote(launch.processStartedAt)}`));
 assert.equal(value.visibleWindows,0);assert.equal(value.closePosted,true);
 await until(()=>ps(`@(Get-CimInstance Win32_Process -Filter "ProcessId=${pid}").Count`)==='0','natural app exit');
 await until(()=>ps(`@(Get-CimInstance Win32_Process -Filter "Name='msedgewebview2.exe'" | Where-Object {$_.CommandLine -and $_.CommandLine.Contains(${quote(launch.webviewProfile)})}).Count`)==='0','owned profile exit');
 pids.push(pid);launch=null;closes.push(value);
}
try{
 await start();const workspaceId=(await invoke('ensure_desktop_local_workspace')).summary.id;
 await until(()=>evaluate('Boolean(document.querySelector("#chat-composer"))'),'composer');
 await evaluate('document.querySelector("[data-action=toggle-composer-add]").click();true');await until(()=>evaluate('Boolean(document.querySelector("[data-action=toggle-conversation-web-search]"))'),'web preference');
 if(await evaluate('document.querySelector("[data-action=toggle-conversation-web-search]").getAttribute("aria-checked")')!=='false')await evaluate('document.querySelector("[data-action=toggle-conversation-web-search]").click();true');
 await until(()=>evaluate('document.querySelector("[data-action=toggle-conversation-web-search]")?.getAttribute("aria-checked")==="false"'),'web off');await evaluate('document.querySelector("[data-action=close-composer-add]").click();true');
 await evaluate(`(()=>{const f=document.querySelector('#chat-composer');f.value='SYNTHETIC_STREAM_STOP_QUESTION';f.dispatchEvent(new Event('input',{bubbles:true}));return true})()`);
 await until(()=>evaluate('Boolean(document.querySelector("[data-action=save-local-message]:not([disabled])"))'),'send enabled');await evaluate('document.querySelector("[data-action=save-local-message]").click();true');
 await until(()=>requests===1,'loopback request');
 const running=await until(async()=>{const w=await invoke('read_desktop_workspace',{workspaceId});const c=w.exchange.conversations.find(c=>c.messages.some(m=>m.role==='assistant'&&m.blocks.some(b=>b.text===partialText)));return c||null;},'persisted partial stream');
 await until(()=>evaluate(`document.querySelector('.chat-scroll')?.textContent.includes(${JSON.stringify(partialText)}) && Boolean(document.querySelector('[data-action=stop-ordinary-chat]'))`),'actual partial text and Stop');
 await evaluate('document.querySelector("[data-action=stop-ordinary-chat]").click();true');
 const cancelled=await until(async()=>{const a=await invoke('read_latest_desktop_ordinary_chat_attempt',{workspaceId,conversationId:running.id});return a?.state==='CANCELLED'&&a.inputTokens===6?a:null;},'cancelled state');assert.equal(cancelled.safeErrorCode,'CANCELLED');
 await until(()=>closedConnections===1,'cancelled socket closed');
 const after=await invoke('read_desktop_workspace',{workspaceId});const c=after.exchange.conversations.find(c=>c.id===running.id);const assistant=c.messages.find(m=>m.role==='assistant'&&m.delivery==='CANCELLED');assert.ok(assistant);assert.equal(c.messages.filter(m=>m.role==='assistant'&&m.delivery==='CANCELLED').length,1);assert.equal(assistant.blocks.filter(b=>b.kind==='TEXT').map(b=>b.text).join(''),partialText);assert.equal(requests,1);
 await until(()=>evaluate(`document.querySelector('.chat-scroll')?.textContent.includes(${JSON.stringify(partialText)}) && !document.querySelector('[data-action=stop-ordinary-chat]')`),'stopped partial UI');
 const ledger=await until(async()=>{const value=await invoke('read_desktop_usage_ledger');return value.records.some(r=>r.conversationId===running.id&&r.inputTokens===6)?value:null;},'cancelled measured ledger');
 const rows=ledger.records.filter(r=>r.conversationId===running.id);assert.equal(rows.length,1);
 assert.equal(rows[0].inputTokens,6);assert.equal(rows[0].cachedInputTokens,1);assert.equal(rows[0].outputTokens,null);assert.equal(rows[0].chargeMicros,null);
 const runtimeInfo=await invoke('read_desktop_runtime_info');
 for(const selector of ['[data-action=show-settings]','[data-action=open-settings-page][data-page=model]','[data-action=open-settings-page][data-page=conversation-cost]']) {
  await until(()=>evaluate(`Boolean(document.querySelector(${JSON.stringify(selector)}))`),'cost navigation');
  await evaluate(`document.querySelector(${JSON.stringify(selector)}).click();true`);
 }
 const costUi=await until(()=>evaluate(`document.querySelector('.android-settings-ledger-row')?.textContent||''`),'actual cost row');
 for(const text of ['输入 6 / 输出 未知 Token','缓存 1 Token','金额未知','已停止'])assert.ok(costUi.includes(text),text);
 const costSummary=await evaluate(`document.querySelector('.android-settings-ledger-summary')?.textContent||''`);
 assert.ok(costSummary.includes('输出 Token未知'));await normalClose();await start(true);
 assert.deepEqual(await invoke('read_desktop_workspace',{workspaceId}),after);assert.deepEqual(await invoke('read_latest_desktop_ordinary_chat_attempt',{workspaceId,conversationId:running.id}),cancelled);assert.deepEqual(await invoke('read_desktop_usage_ledger'),ledger);assert.equal(requests,1);
 await until(()=>evaluate(`Boolean(document.querySelector('[data-action="select-chat"][data-id="${running.id}"]'))`),'cold conversation');await evaluate(`document.querySelector('[data-action="select-chat"][data-id="${running.id}"]').click();true`);await until(()=>evaluate(`document.querySelector('.chat-scroll')?.textContent.includes(${JSON.stringify(partialText)}) && !document.querySelector('[data-action=stop-ordinary-chat]')`),'cold stopped body');await normalClose();
 const result={pass:true,costUi,costSummary,runtimeInfo,privateDesktopGuard,backgroundOnly:true,realService:false,closedConnections,candidateSha256:receipt.sha256,root,workspaceId,conversationId:running.id,partialText,cancelled,exchangeAfter:after.exchange,ledger,providerRequests:requests,closes,pids,coldExact:true};await writeFile(join(root,'ordinary-stream-stop-native.json'),JSON.stringify(result,null,2));console.log(JSON.stringify({pass:true,root,candidateSha256:receipt.sha256,providerRequests:requests,closes,pids,coldExact:true}));
}catch(error){
 if(root)await writeFile(join(root,'ordinary-stream-stop-failure.json'),JSON.stringify({pass:false,root,candidateSha256:receipt.sha256,error:String(error.stack||error),pids,closes,providerRequests:requests},null,2));
 console.error(JSON.stringify({pass:false,root,error:String(error)}));throw error;
}finally{if(launch){try{await normalClose();}catch{stop();}}for(const response of responses)response.destroy();provider.closeAllConnections();await new Promise(done=>provider.close(done));}
