import assert from 'node:assert/strict';
import { createHash } from 'node:crypto';
import { spawn, execFileSync } from 'node:child_process';
import { readFile, writeFile, stat, mkdir, copyFile } from 'node:fs/promises';
import { dirname, resolve, join, sep } from 'node:path';
import { fileURLToPath } from 'node:url';
import http from 'node:http';
import net from 'node:net';

assert.equal(process.platform, 'win32');
const desktop = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const parent = resolve(process.argv[2] || 'D:/CodexTemp/nfa-chat-pdf-preview-cancel');
const compare = false;assert.ok(process.execArgv.includes('--max-old-space-size=2048'));
const visible = true;
assert.ok(!(visible && compare));
const receipt = JSON.parse(await readFile(join(desktop, 'output/windows-acceptance/current.json'), 'utf8'));
assert.equal(receipt.identifier, 'com.nanzhufeng.ai.desktop.windowsacceptance');
assert.equal(createHash('sha256').update(await readFile(receipt.executable)).digest('hex').toUpperCase(), receipt.sha256);
const executable = resolve(desktop, 'output/windows-acceptance-binaries', receipt.sha256, 'nanfeng-ai-desktop-spike.exe');
const helper = join(desktop, 'scripts/windows-acceptance-cdp-eval.mjs');
let requests = 0;const responses=[];const closes=[];const partialText='SYNTHETIC_STREAM_STOP_PARTIAL 完整保留片段';
let mode='success';const requestFacts=[];
const provider=http.createServer(async(request,response)=>{
 const chunks=[];for await(const chunk of request)chunks.push(chunk);const body=JSON.parse(Buffer.concat(chunks).toString('utf8'));requests++;
 const marker='SYNTHETIC_WEB_SEARCH_'+mode.toUpperCase();const lastUser=body.messages?.filter(m=>m.role==='user').at(-1);requestFacts.push({mode,path:request.url,model:body.model,stream:body.stream,forceSearch:body.tool_choice?.type==='tool'&&body.tool_choice?.name==='web_search',searchTool:body.tools?.some(t=>t.name==='web_search')===true,currentUserMarker:JSON.stringify(lastUser?.content).includes(marker)});
 const usage={input_tokens:42,output_tokens:9,cache_read_input_tokens:2,cost:0.000012};
 if(mode==='offline'){response.writeHead(200,{'Content-Type':'text/event-stream'});response.end(`data: ${JSON.stringify({model:body.model,choices:[{delta:{content:marker+'_ANSWER'},finish_reason:'stop'}],usage:{prompt_tokens:42,completion_tokens:9,prompt_tokens_details:{cached_tokens:2},cost:0.000012}})}\n\ndata: [DONE]\n\n`);return;}
 const sources=mode==='no_sources'?{type:'web_search_tool_result_error',error_code:'unavailable'}:[{type:'web_search_result',url:'https://example.com/synthetic-search-source',title:'合成公开来源（没有抓取网站）'}];response.writeHead(200,{'Content-Type':'application/json'});response.end(JSON.stringify({type:'message',model:body.model,stop_reason:mode==='incomplete'?'max_tokens':'end_turn',content:[{type:'server_tool_use',id:'synthetic-search-tool',name:'web_search',input:{query:'synthetic only'}},{type:'web_search_tool_result',tool_use_id:'synthetic-search-tool',content:sources},{type:'text',text:marker+'_ANSWER'}],usage}));
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
  const code = `[Console]::OutputEncoding=[Text.UTF8Encoding]::new($false); & ${quote(join(desktop, 'scripts/start-windows-acceptance.ps1'))} -RootParent ${quote(parent)} -ExecutablePath ${quote(receipt.executable)} -StartupMode normal -Visible -OrdinaryChatTestEndpoint ${quote(endpoint)} -DebugPort ${port}${reopen ? ` -Root ${quote(root)}` : ''}`;
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
const ui=async selector=>{await until(()=>evaluate(`Boolean(document.querySelector(${JSON.stringify(selector)}))`),'UI '+selector);assert.equal(await evaluate(`document.querySelector(${JSON.stringify(selector)}).click();true`),true);};


try {root=resolve('D:/CodexTemp/nfa-chat-web-search-current/nanfeng-ai-windows-acceptance.cc47ad2a-48fb-4ca3-87d9-76b432fa2b23');await start(true);const workspaceId=(await invoke('ensure_desktop_local_workspace')).summary.id;const w=await invoke('read_desktop_workspace',{workspaceId});let failed;for(const c of w.exchange.conversations){const a=await invoke('read_latest_desktop_ordinary_chat_attempt',{workspaceId,conversationId:c.id});if(a?.safeErrorCode==='WEB_SEARCH_NO_SOURCES'){failed=a;break;}}assert.ok(failed);await ui(`[data-action="select-chat"][data-id="${failed.conversationId}"]`);await ui(`[data-action="open-assistant-message-menu"][data-message-id="${failed.assistantMessageId}"]`);await ui('[data-action=show-assistant-answer-information]');await until(()=>evaluate('Boolean(document.querySelector(".answer-information-dialog"))'),'failure info dialog');const text=await evaluate('document.querySelector(".answer-information-dialog").textContent');const record=await invoke('read_desktop_ordinary_chat_context_record',{workspaceId,conversationId:failed.conversationId,assistantMessageId:failed.assistantMessageId});assert.equal(record,null);assert.ok(text.includes('无法确认（旧记录）'));await ui('[data-action=close-dialog]');await normalClose();const r={root,candidateSha256:receipt.sha256,failed,record,actualText:text,closes,providerRequests:requests};await writeFile(join(root,'chat-web-search-failed-information-red.json'),JSON.stringify(r,null,2));console.log(JSON.stringify({root,candidateSha256:receipt.sha256,record,actualText:text,closes,providerRequests:requests}));}finally{stop();for(const response of responses)response.destroy();provider.closeAllConnections();await new Promise(done=>provider.close(done));}
