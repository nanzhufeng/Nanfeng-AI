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
let requests=0;const responses=[];const closes=[];const requestChecks=[];
const textA='SYNTHETIC_SWITCH_A_PARTIAL';const tailA=' 后台完成A';const textB='SYNTHETIC_SWITCH_B_FINAL';
let held;
const provider=http.createServer(async(request,response)=>{
 let body='';for await(const chunk of request)body+=chunk;const parsed=JSON.parse(body);requests++;requestChecks.push({ordinal:requests,messagesSha256:createHash('sha256').update(JSON.stringify(parsed.messages)).digest('hex'),hasA:JSON.stringify(parsed.messages).includes('SYNTHETIC_SWITCH_A_QUESTION'),hasB:JSON.stringify(parsed.messages).includes('SYNTHETIC_SWITCH_B_DRAFT'),userHasA:JSON.stringify(parsed.messages.filter(m=>m.role==='user')).includes('SYNTHETIC_SWITCH_A_QUESTION'),userHasB:JSON.stringify(parsed.messages.filter(m=>m.role==='user')).includes('SYNTHETIC_SWITCH_B_DRAFT'),userMessageCount:parsed.messages.filter(m=>m.role==='user').length});
 response.writeHead(200,{'Content-Type':'text/event-stream'});responses.push(response);
 if(requests===1){held=response;response.write(`data: ${JSON.stringify({choices:[{delta:{content:textA},finish_reason:null}]})}\n\n`);}
 else{response.write(`data: ${JSON.stringify({choices:[{delta:{content:textB},finish_reason:null}]})}\n\n`);response.end(`data: ${JSON.stringify({choices:[{delta:{},finish_reason:'stop'}],usage:{prompt_tokens:5,completion_tokens:7,total_tokens:12}})}\n\ndata: [DONE]\n\n`);}
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
const ui=async selector=>{await until(()=>evaluate(`Boolean(document.querySelector(${JSON.stringify(selector)}))`),'UI '+selector);await evaluate(`document.querySelector(${JSON.stringify(selector)}).click();true`);};
async function webOff(){await ui('[data-action=toggle-composer-add]');await until(()=>evaluate('Boolean(document.querySelector("[data-action=toggle-conversation-web-search]"))'),'web setting');if(await evaluate('document.querySelector("[data-action=toggle-conversation-web-search]").getAttribute("aria-checked")')!=='false')await ui('[data-action=toggle-conversation-web-search]');await ui('[data-action=close-composer-add]');}
async function type(text){await until(()=>evaluate('Boolean(document.querySelector("#chat-composer"))'),'composer');await evaluate(`(()=>{let f=document.querySelector('#chat-composer');f.value=${JSON.stringify(text)};f.dispatchEvent(new Event('input',{bubbles:true}));return true})()`);}
const textOf=m=>m.blocks.filter(b=>b.kind==='TEXT').map(b=>b.text).join('');
try{
 await start();const workspaceId=(await invoke('ensure_desktop_local_workspace')).summary.id;await ui('[data-action=new-chat]');await webOff();await type('SYNTHETIC_SWITCH_A_QUESTION');await ui('[data-action=save-local-message]:not([disabled])');await until(()=>requests===1,'request A');
 const running=await until(async()=>{const w=await invoke('read_desktop_workspace',{workspaceId});return w.exchange.conversations.find(c=>c.messages.some(m=>textOf(m)===textA));},'persisted A partial');
 await until(()=>evaluate(`document.querySelector('.chat-scroll')?.textContent.includes(${JSON.stringify(textA)}) && Boolean(document.querySelector('[data-action=stop-ordinary-chat]'))`),'actual A stream');
 await ui('[data-action=new-chat]');await type('SYNTHETIC_SWITCH_B_DRAFT');
 await until(()=>evaluate('document.querySelector("#chat-composer")?.value==="SYNTHETIC_SWITCH_B_DRAFT" && !document.querySelector(".chat-scroll")?.textContent.includes("SYNTHETIC_SWITCH_A_PARTIAL")'),'new chat draft without A');
 const draftBefore=await until(async()=>{const d=await invoke('read_desktop_conversation_draft',{workspaceId});return d?.text==='SYNTHETIC_SWITCH_B_DRAFT'?d:null;},'persisted B draft');
 held.write(`data: ${JSON.stringify({choices:[{delta:{content:tailA},finish_reason:null}]})}\n\n`);held.end(`data: ${JSON.stringify({choices:[{delta:{},finish_reason:'stop'}],usage:{prompt_tokens:5,completion_tokens:7,total_tokens:12}})}\n\ndata: [DONE]\n\n`);
 const completedA=await until(async()=>{const a=await invoke('read_latest_desktop_ordinary_chat_attempt',{workspaceId,conversationId:running.id});return a?.state==='COMPLETED'?a:null;},'A completes in background');
 assert.equal(await evaluate('document.querySelector("#chat-composer")?.value'),'SYNTHETIC_SWITCH_B_DRAFT');assert.equal(await evaluate('document.querySelector(".chat-scroll")?.textContent.includes("SYNTHETIC_SWITCH_A_PARTIAL")||false'),false);assert.deepEqual(await invoke('read_desktop_conversation_draft',{workspaceId}),draftBefore);
 await ui(`[data-action="select-chat"][data-id="${running.id}"]`);await until(()=>evaluate(`document.querySelector('.chat-scroll')?.textContent.includes(${JSON.stringify(textA+tailA)}) && !document.querySelector('[data-action=stop-ordinary-chat]')`),'actual completed A');
 await ui('[data-action=new-chat]');await until(()=>evaluate('document.querySelector("#chat-composer")?.value==="SYNTHETIC_SWITCH_B_DRAFT"'),'B draft restored');assert.deepEqual(await invoke('read_desktop_conversation_draft',{workspaceId}),draftBefore);
 const beforeCold=await invoke('read_desktop_workspace',{workspaceId});await normalClose();await start(true);assert.deepEqual(await invoke('read_desktop_workspace',{workspaceId}),beforeCold);assert.deepEqual(await invoke('read_desktop_conversation_draft',{workspaceId}),draftBefore);
 await ui('[data-action=new-chat]');await until(()=>evaluate('document.querySelector("#chat-composer")?.value==="SYNTHETIC_SWITCH_B_DRAFT"'),'cold B draft');await webOff();await ui('[data-action=save-local-message]:not([disabled])');await until(()=>requests===2,'request B');
 const after=await until(async()=>{const w=await invoke('read_desktop_workspace',{workspaceId});return w.exchange.conversations.some(c=>c.messages.some(m=>textOf(m)===textB))?w:null;},'B final persists');const b=after.exchange.conversations.find(c=>c.messages.some(m=>textOf(m)===textB));assert.notEqual(b.id,running.id);const completedB=await until(async()=>{const a=await invoke('read_latest_desktop_ordinary_chat_attempt',{workspaceId,conversationId:b.id});return a?.state==='COMPLETED'?a:null;},'B complete');assert.ok(completedA.attemptId);assert.ok(completedB.attemptId);assert.notEqual(completedA.attemptId,completedB.attemptId);assert.equal(requestChecks[0].hasA,true);assert.equal(requestChecks[0].hasB,false);assert.equal(requestChecks[1].hasB,true);assert.equal(requestChecks[0].userHasA,true);assert.equal(requestChecks[0].userHasB,false);assert.equal(requestChecks[1].userHasA,false);assert.equal(requestChecks[1].userHasB,true);assert.equal(requestChecks[1].userMessageCount,1);
 const a=after.exchange.conversations.find(c=>c.id===running.id);assert.equal(textOf(a.messages.find(m=>m.id===completedA.assistantMessageId)),textA+tailA);await until(()=>evaluate('document.querySelector(".chat-scroll")?.textContent.includes("SYNTHETIC_SWITCH_B_FINAL")'),'actual B final');const contextRecords=await invoke('read_desktop_ordinary_chat_context_records');const bContext=contextRecords.find(x=>x.attemptId===completedB.attemptId);assert.ok(bContext);if(requestChecks[1].hasA){assert.ok(bContext.selectedSources.some(x=>x.kind==='历史对话'));}const ledger=await invoke('read_desktop_usage_ledger');await normalClose();await start(true);assert.deepEqual(await invoke('read_desktop_workspace',{workspaceId}),after);assert.deepEqual(await invoke('read_desktop_usage_ledger'),ledger);assert.equal(requests,2);await normalClose();
 const result={root,candidateSha256:receipt.sha256,workspaceId,conversationA:running.id,conversationB:b.id,completedA,completedB,draftBefore,after,ledger,requestChecks,contextRecords,requests,pids,windows,closes,coldExact:true,backgroundDidNotOverwriteDraft:true};await writeFile(join(root,'chat-stream-switch-native.json'),JSON.stringify(result,null,2));console.log(JSON.stringify({root,candidateSha256:receipt.sha256,conversationA:running.id,conversationB:b.id,requests,requestChecks,closes,coldExact:true,backgroundDidNotOverwriteDraft:true}));
}catch(error){if(root)await writeFile(join(root,'chat-stream-switch-failure.json'),JSON.stringify({root,pids,windows,closes,requestChecks,error:String(error)},null,2));throw error;}finally{stop();for(const response of responses)response.destroy();provider.closeAllConnections();await new Promise(done=>provider.close(done));}
