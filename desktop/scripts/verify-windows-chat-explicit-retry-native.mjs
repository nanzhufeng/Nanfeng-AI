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
const parent = resolve(process.argv[2] || 'D:/CodexTemp');
const compare = false;assert.ok(process.execArgv.includes('--max-old-space-size=2048'));
const visible = true;
assert.ok(!(visible && compare));
const receipt = JSON.parse(await readFile(join(desktop, 'output/windows-acceptance/current.json'), 'utf8'));
assert.equal(receipt.identifier, 'com.nanzhufeng.ai.desktop.windowsacceptance');
assert.equal(createHash('sha256').update(await readFile(receipt.executable)).digest('hex').toUpperCase(), receipt.sha256);
const executable = resolve(desktop, 'output/windows-acceptance-binaries', receipt.sha256, 'nanfeng-ai-desktop-spike.exe');
const helper = join(desktop, 'scripts/windows-acceptance-cdp-eval.mjs');
let requests = 0;const responses=[];const closes=[];const partialText='SYNTHETIC_EXPLICIT_RETRY_ANSWER 完整重试回答';const requestFacts=[];
const provider=http.createServer(async(request,response)=>{
 const chunks=[];for await(const chunk of request)chunks.push(chunk);const body=JSON.parse(Buffer.concat(chunks).toString('utf8'));requests++;requestFacts.push({messagesSha256:createHash('sha256').update(JSON.stringify(body.messages)).digest('hex'),model:body.model,stream:body.stream,questionPresent:JSON.stringify(body.messages).includes('SYNTHETIC_EXPLICIT_RETRY_QUESTION'),attachmentPresent:JSON.stringify(body.messages).includes('Non-sensitive exchange fixture attachment.')});
 if(requests===1){response.writeHead(503,{'content-type':'application/json'});response.end(JSON.stringify({error:{message:'synthetic service unavailable',type:'server_error'}}));return;}
 response.writeHead(200,{'content-type':'text/event-stream; charset=utf-8'});response.end(`data: ${JSON.stringify({model:body.model,choices:[{delta:{content:partialText},finish_reason:'stop'}],usage:{prompt_tokens:5,completion_tokens:7,cost:0.000009}})}\n\ndata: [DONE]\n\n`);
});
await new Promise(done => provider.listen(0, '127.0.0.1', done));
const endpoint = `http://127.0.0.1:${provider.address().port}`;
let launch, root, port;
const pids = [];
const windows = [];
const quote = value => `'${String(value).replaceAll("'", "''")}'`;
const ps = code => execFileSync('pwsh', ['-NoProfile', '-Command', `[Console]::OutputEncoding=[Text.UTF8Encoding]::new($false);${code}`], { encoding: 'utf8' }).trim();
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
  const code = `[Console]::OutputEncoding=[Text.UTF8Encoding]::new($false); & ${quote(join(desktop, 'scripts/start-windows-acceptance.ps1'))} -RootParent ${quote(parent)} -ExecutablePath ${quote(receipt.executable)} -StartupMode normal -Visible -DebugPort ${port} -OrdinaryChatTestEndpoint ${quote(endpoint)}${reopen ? ` -Root ${quote(root)}` : ''}`;
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
  assert.equal(launch.sha256, receipt.sha256);
  assert.equal(resolve(launch.executable), executable);
  root = resolve(launch.root);
  assert.ok(root.startsWith(parent + sep));
  assert.equal(JSON.parse(await readFile(join(root, '.nanfeng-windows-acceptance.json'), 'utf8')).identifier, receipt.identifier);
  await until(() => evaluate('Boolean(window.__TAURI__?.core?.invoke)'), 'native IPC unavailable');
  await until(() => evaluate('globalThis.__nanfengDesktopWorkState?.runtimeInfo?.dataAreasReady===true'), 'native data areas unavailable');
  const window = JSON.parse(ps(`$p=Get-Process -Id ${Number(launch.pid)} -ErrorAction Stop;@{title=$p.MainWindowTitle;responding=$p.Responding}|ConvertTo-Json -Compress`));
  assert.ok(window.title.includes('南枫 AI'), 'native window title mismatch');
  assert.equal(window.responding, true); windows.push(window);
}
function stop() {
  if (!launch) return;
  const pid = Number(launch.pid);
  ps(`$p=Get-CimInstance Win32_Process -Filter 'ProcessId=${pid}';if($p){if([IO.Path]::GetFullPath([string]$p.ExecutablePath) -ne ${quote(executable)} -or (Get-FileHash -LiteralPath ${quote(executable)} -Algorithm SHA256).Hash -ne ${quote(receipt.sha256)}){throw 'PID mismatch'};Stop-Process -Id ${pid} -Force}`);
  pids.push(pid); launch = null;
}
async function normalClose(){
 const pid=Number(launch.pid);const value=JSON.parse(ps(`$p=Get-CimInstance Win32_Process -Filter 'ProcessId=${pid}';if(!$p -or [IO.Path]::GetFullPath($p.ExecutablePath) -ne ${quote(executable)} -or (Get-FileHash -LiteralPath ${quote(executable)} -Algorithm SHA256).Hash -ne ${quote(receipt.sha256)}){throw 'identity'};$v=Get-Process -Id ${pid};$handle=$v.Handle;$title=$v.MainWindowTitle;$responding=$v.Responding;if(!$title -or !$responding -or !$v.CloseMainWindow()){throw 'window close'};if(!$v.WaitForExit(10000) -or $v.ExitCode -ne 0){throw 'normal exit'};@{title=$title;responding=$responding;pid=${pid};exitCode=$v.ExitCode;accepted=$true}|ConvertTo-Json -Compress`));
 await until(()=>ps(`@(Get-CimInstance Win32_Process -Filter "Name='msedgewebview2.exe'" | Where-Object {$_.CommandLine -and $_.CommandLine.Contains(${quote(launch.webviewProfile)})}).Count`)==='0','owned profile exit');pids.push(pid);launch=null;closes.push(value);
}
try{
 await start();const workspaceId=(await invoke('ensure_desktop_local_workspace')).summary.id;
 const fixture=await invoke('read_desktop_workspace',{workspaceId});const origin=fixture.exchange.conversations.find(c=>c.messages.some(m=>m.blocks.some(b=>b.kind==='ASSET_REF')));assert.ok(origin);const asset=origin.messages.flatMap(m=>m.blocks).find(b=>b.kind==='ASSET_REF').asset;
 await invoke('save_desktop_conversation_draft',{args:{workspaceId,conversationId:origin.id,text:'SYNTHETIC_EXPLICIT_RETRY_QUESTION',attachmentIds:[asset.id]}});
 await evaluate('location.reload();true');await until(()=>evaluate('globalThis.__nanfengDesktopWorkState?.runtimeInfo?.dataAreasReady===true'),'reload ready');await until(()=>evaluate(`Boolean(document.querySelector('[data-action="select-chat"][data-id="${origin.id}"]'))`),'origin row');await evaluate(`document.querySelector('[data-action="select-chat"][data-id="${origin.id}"]').click();true`);await until(()=>evaluate(`globalThis.__nanfengDesktopWorkState.composerAttachments?.length===1 && globalThis.__nanfengDesktopWorkState.composerAttachments[0].id===${JSON.stringify(asset.id)}`),'actual draft attachment');
 await until(()=>evaluate('Boolean(document.querySelector("#chat-composer"))'),'composer');
 await evaluate('document.querySelector("[data-action=toggle-composer-add]").click();true');await until(()=>evaluate('Boolean(document.querySelector("[data-action=toggle-conversation-web-search]"))'),'web preference');
 if(await evaluate('document.querySelector("[data-action=toggle-conversation-web-search]").getAttribute("aria-checked")')!=='false')await evaluate('document.querySelector("[data-action=toggle-conversation-web-search]").click();true');
 await until(()=>evaluate('document.querySelector("[data-action=toggle-conversation-web-search]")?.getAttribute("aria-checked")==="false"'),'web off');await evaluate('document.querySelector("[data-action=close-composer-add]").click();true');
 await evaluate(`(()=>{const f=document.querySelector('#chat-composer');f.value='SYNTHETIC_EXPLICIT_RETRY_QUESTION';f.dispatchEvent(new Event('input',{bubbles:true}));return true})()`);
 await until(()=>evaluate('Boolean(document.querySelector("[data-action=save-local-message]:not([disabled])"))'),'send enabled');await evaluate('document.querySelector("[data-action=save-local-message]").click();true');
 await until(()=>requests===1,'first loopback request');
 const failedConversation=await until(async()=>{const w=await invoke('read_desktop_workspace',{workspaceId});return w.exchange.conversations.find(c=>c.messages.some(m=>m.role==='assistant'&&m.delivery==='FAILED'))||null;},'failed assistant');
 const failed=await invoke('read_latest_desktop_ordinary_chat_attempt',{workspaceId,conversationId:failedConversation.id});assert.equal(failed.state,'FAILED');assert.equal(failed.safeErrorCode,'SERVICE_UNAVAILABLE');const failedMessage=failedConversation.messages.find(m=>m.id===failed.assistantMessageId);assert.equal(failedMessage.delivery,'FAILED');
 await until(()=>evaluate(`Boolean(document.querySelector('[data-action="retry-ordinary-chat"][data-attempt-id="${failed.attemptId}"]'))`),'actual explicit Retry entry');const before=await invoke('read_desktop_workspace',{workspaceId});await normalClose();await start(true);assert.deepEqual(await invoke('read_latest_desktop_ordinary_chat_attempt',{workspaceId,conversationId:failedConversation.id}),failed);assert.deepEqual(await invoke('read_desktop_workspace',{workspaceId}),before);assert.equal(requests,1);
 await until(()=>evaluate(`Boolean(document.querySelector('[data-action="select-chat"][data-id="${failedConversation.id}"]'))`),'failed cold conversation');await evaluate(`document.querySelector('[data-action="select-chat"][data-id="${failedConversation.id}"]').click();true`);
 await until(()=>evaluate(`Boolean(document.querySelector('[data-action="retry-ordinary-chat"][data-attempt-id="${failed.attemptId}"]'))`),'cold explicit retry');await evaluate(`document.querySelector('[data-action="retry-ordinary-chat"][data-attempt-id="${failed.attemptId}"]').click();true`);
 const completed=await until(async()=>{const a=await invoke('read_latest_desktop_ordinary_chat_attempt',{workspaceId,conversationId:failedConversation.id});return a?.state==='COMPLETED'?a:null;},'explicit retry completed');assert.equal(completed.attemptId,failed.attemptId);assert.equal(completed.retryCount,1);assert.equal(completed.chargeMicros,9);assert.equal(completed.inputTokens,5);assert.equal(completed.outputTokens,7);assert.equal(requests,2);assert.equal(requestFacts[0].messagesSha256,requestFacts[1].messagesSha256);assert.equal(requestFacts[0].model,requestFacts[1].model);assert.ok(requestFacts.every(r=>r.questionPresent&&r.attachmentPresent));
 const after=await invoke('read_desktop_workspace',{workspaceId});const c=after.exchange.conversations.find(c=>c.id===failedConversation.id);assert.deepEqual(c.messages.find(m=>m.id===failedMessage.id),failedMessage);const answer=c.messages.find(m=>m.id===completed.assistantMessageId);assert.equal(answer.parentId,failedMessage.parentId);assert.notEqual(answer.id,failedMessage.id);assert.equal(answer.blocks.filter(b=>b.kind==='TEXT').map(b=>b.text).join(''),partialText);assert.equal(c.currentLeafId,answer.id);
 await until(()=>evaluate(`document.querySelector('.chat-scroll')?.textContent.includes(${JSON.stringify(partialText)})`),'actual complete reply');const ledger=await invoke('read_desktop_usage_ledger');assert.equal(ledger.records.length,1);await normalClose();await start(true);assert.deepEqual(await invoke('read_desktop_workspace',{workspaceId}),after);assert.deepEqual(await invoke('read_latest_desktop_ordinary_chat_attempt',{workspaceId,conversationId:failedConversation.id}),completed);assert.deepEqual(await invoke('read_desktop_usage_ledger'),ledger);assert.equal(requests,2);
 await until(()=>evaluate(`Boolean(document.querySelector('[data-action="select-chat"][data-id="${failedConversation.id}"]'))`),'completed cold conversation');await evaluate(`document.querySelector('[data-action="select-chat"][data-id="${failedConversation.id}"]').click();true`);await until(()=>evaluate(`document.querySelector('.chat-scroll')?.textContent.includes(${JSON.stringify(partialText)})`),'cold reply');await normalClose();
 const result={candidateSha256:receipt.sha256,root,workspaceId,conversationId:c.id,partialText,asset,failed,failedMessage,completed,exchangeAfter:after.exchange,ledger,requestFacts,providerRequests:requests,closes,pids,failureColdExact:true,completedColdExact:true};await writeFile(join(root,'ordinary-explicit-retry-native.json'),JSON.stringify(result,null,2));console.log(JSON.stringify({root,candidateSha256:receipt.sha256,providerRequests:requests,closes,pids,failureColdExact:true,completedColdExact:true}));
}finally{stop();for(const response of responses)response.destroy();provider.closeAllConnections();await new Promise(done=>provider.close(done));}
