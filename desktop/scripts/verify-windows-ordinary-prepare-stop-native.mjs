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
const compare = process.argv.includes('--compare');
const visible = process.argv.includes('--visible');
assert.ok(!(visible && compare));
const receipt = JSON.parse(await readFile(join(desktop, 'output/windows-acceptance/current.json'), 'utf8'));
assert.equal(receipt.identifier, 'com.nanzhufeng.ai.desktop.windowsacceptance');
assert.equal(createHash('sha256').update(await readFile(receipt.executable)).digest('hex').toUpperCase(), receipt.sha256);
const executable = resolve(desktop, 'output/windows-acceptance-binaries', receipt.sha256, 'nanfeng-ai-desktop-spike.exe');
const helper = join(desktop, 'scripts/windows-acceptance-cdp-eval.mjs');
let requests = 0;
const provider = http.createServer(async (request, response) => {
  for await (const _ of request) { /* Never log body. */ }
  requests++;
  response.writeHead(200, { 'Content-Type': 'text/event-stream' });
  response.end('data: {"choices":[{"delta":{"content":"synthetic"},"finish_reason":"stop"}]}\n\ndata: [DONE]\n\n');
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
  const deadline = Date.now() + 90000;
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
  const code = `[Console]::OutputEncoding=[Text.UTF8Encoding]::new($false); & ${quote(join(desktop, 'scripts/start-windows-acceptance.ps1'))} -RootParent ${quote(parent)} -StartupMode normal -Visible -DebugPort ${port} -OrdinaryChatTestEndpoint ${quote(endpoint)}${reopen ? ` -Root ${quote(root)}` : ''}`;
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
try {
  await start();
  const workspaceId = (await invoke('ensure_desktop_local_workspace')).summary.id;
  await writeFile(join(root, '.ordinary-registration-arm'), '', { flag: 'wx' });
  const args = {workspaceId, conversationId:null, expectedRevision:null, text:'SYNTHETIC_PREPARE_STOP',attachmentIds:[],modelId:null,webSearchOverride:false,egressAuthorization:{approvedAtMs:Date.now(),disclosureVersion:'normal-chat-egress-v1'}};
  const command = compare ? 'submit_desktop_compare' : 'submit_desktop_ordinary_chat';
  if(visible){
    await until(()=>evaluate('Boolean(document.querySelector("#chat-composer"))'),'visible composer missing');
    await evaluate(`(()=>{const field=document.querySelector('#chat-composer');field.value='SYNTHETIC_PREPARE_STOP';field.dispatchEvent(new Event('input',{bubbles:true}));return true})()`);
    await until(()=>evaluate('Boolean(document.querySelector("[data-action=save-local-message]:not([disabled])"))'),'visible Send unavailable');
    await evaluate('document.querySelector("[data-action=save-local-message]").click();true');
  } else {
  await evaluate(`globalThis.__ordinaryGapResult=null;window.__TAURI__.core.invoke(${JSON.stringify(command)},${JSON.stringify({args})},{headers:{'x-nanfeng-data-area':'CHAT'}}).then(value=>globalThis.__ordinaryGapResult={ok:true,value}).catch(error=>globalThis.__ordinaryGapResult={ok:false,error:String(error)});true`);
  }
  if(compare){
    const denied=await until(()=>evaluate('globalThis.__ordinaryGapResult'),'retired Compare response missing');
    assert.equal(denied.ok,false);assert.match(denied.error,/not allowed.*Command not found/);
    assert.equal(requests,0);stop();
    console.log(JSON.stringify({stage:'retired Compare gate',root,candidateSha256:receipt.sha256,denied,providerRequests:requests,pids,windows}));
  } else {
  const reached = await until(async()=>{const result=visible?null:await evaluate('globalThis.__ordinaryGapResult');if(result)return {result};return await stat(join(root,'.ordinary-registration-reached'));},'registration checkpoint missing');
  if(reached.result){console.log(JSON.stringify({stage:'preparation rejected',root,compare,result:reached.result}));throw new Error('preparation returned before checkpoint');}
  const workspace = await invoke('read_desktop_workspace',{workspaceId});
  const conversation = workspace.exchange.conversations.find(item=>item.messages.some(message=>message.role==='assistant' && message.delivery==='PARTIAL'));
  assert.ok(conversation);
  const pending = conversation.messages.find(message=>message.role==='assistant' && message.delivery==='PARTIAL');
  assert.equal(pending.delivery,'PARTIAL'); assert.equal(pending.runtimeState,'RUNNING'); assert.equal(requests,0);
  const latest = await invoke('read_latest_desktop_ordinary_chat_attempt',{workspaceId,conversationId:conversation.id});
  let accepted;
  if(visible){
    await until(()=>evaluate('Boolean(document.querySelector("[data-action=stop-ordinary-chat]"))'),'visible Stop unavailable');
    await evaluate('document.querySelector("[data-action=stop-ordinary-chat]").click();true');
    await until(async()=> (await invoke('read_latest_desktop_ordinary_chat_attempt',{workspaceId,conversationId:conversation.id}))?.state==='CANCELLED','visible Stop did not persist cancellation');
    accepted=true;
  } else {
  accepted = compare ? await invoke('cancel_desktop_compare',{args:{executionId:latest.compareExecutionId}}) : await invoke('cancel_desktop_ordinary_chat',{args:{conversationId:conversation.id}});
  }
  await writeFile(join(root,'.ordinary-registration-continue'),'',{flag:'wx'});
  const result = visible ? {ok:true,value:await invoke('read_latest_desktop_ordinary_chat_attempt',{workspaceId,conversationId:conversation.id})} : await until(()=>evaluate('globalThis.__ordinaryGapResult'),'submission not finished');
  if(visible) await new Promise(done=>setTimeout(done,750));
  console.log(JSON.stringify({stage:'prepared stop result',visible,compare,root,candidateSha256:receipt.sha256,accepted,providerRequests:requests,result,windows}));
  assert.equal(accepted,true,'visible running attempt must accept stop before registration');
  assert.equal(requests,0,'stopped prepared request must not reach Provider');
  assert.equal(result.ok,true);
  if(compare){assert.equal(result.value.branches.length,2);for(const branch of result.value.branches){assert.equal(branch.state,'CANCELLED');assert.equal(branch.safeErrorCode,'CANCELLED');}} else assert.equal(result.value.safeErrorCode,'CANCELLED');
  stop(); await start(true);
  const cold = await invoke('read_latest_desktop_ordinary_chat_attempt',{workspaceId,conversationId:conversation.id});
  assert.equal(cold.safeErrorCode,'CANCELLED'); assert.equal(requests,0);
  if(compare){const execution=await invoke('read_desktop_compare_execution',{executionId:latest.compareExecutionId});assert.equal(execution.state,'CANCELLED');assert.ok(execution.branches.every(branch=>branch.state==='CANCELLED'));}
  stop(); console.log(JSON.stringify({stage:'cold cancelled',root,candidateSha256:receipt.sha256,cold,pids,windows,providerRequests:requests}));
  }
} finally { stop(); await new Promise(done=>provider.close(done)); }

