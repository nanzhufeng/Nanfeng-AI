import assert from 'node:assert/strict';
import { createHash, randomUUID } from 'node:crypto';
import { spawn, execFileSync } from 'node:child_process';
import { readFile, writeFile, stat } from 'node:fs/promises';
import { dirname, resolve, join, sep } from 'node:path';
import { fileURLToPath } from 'node:url';
import http from 'node:http';
import net from 'node:net';

const nativeCasePath = fileURLToPath(import.meta.url);
const nativeCaseSha256 = createHash('sha256').update(await readFile(nativeCasePath)).digest('hex').toUpperCase();
assert.equal(process.platform, 'win32');
const desktop = resolve('C:/Users/Administrator/Documents/软件开发/Nanfeng-AI/desktop');
assert.equal(process.argv.length, 4, 'Provide isolated root parent and candidate build proof');
const parent = resolve(process.argv[2]);
assert.ok(parent.startsWith(resolve('D:/CodexTemp') + sep));
const compare = false; assert.ok(process.execArgv.includes('--max-old-space-size=2048'));
const visible = true;
// The old installed stability run is terminal FAILED, never accepted by this short GUI case.
const stabilityRoot = 'D:/CodexTemp/nfa-installed-chat-normal-detached-24h-20261004/nanfeng-ai-windows-acceptance.3def69c1-b9fc-400f-9568-1ed5f94b19c9';
const stability = JSON.parse(await readFile(join(stabilityRoot, 'local-stability-status.json'), 'utf8'));
assert.equal(stability.state, 'FAILED'); assert.equal(stability.accepted24h, false);
const hostOutcome = JSON.parse(await readFile(resolve('C:/Users/Administrator/Documents/软件开发/Nanfeng-AI/docs/review/20261004-owned-upgrade-natural-24h-1791121761758/detached-monitor-host-outcome.json'), 'utf8'));
assert.equal(hostOutcome.phase, 'FAILED'); assert.equal(hostOutcome.exitCode, 1);
assert.match(process.env.NFAI_BOUNDED_JOB_NAME || '', /^NanfengAIBounded-[0-9a-f-]{36}$/);
assert.match(parent.split(sep).at(-1), /^nfa-bounded-chat-concurrent\.[0-9a-f-]{36}$/);
const nativeCount = Number(execFileSync('pwsh', ['-NoProfile','-Command', "@(Get-CimInstance Win32_Process -Filter \"Name='NanfengAI-Windows-Acceptance.exe' OR Name='nanfeng-ai-desktop-spike.exe'\").Count"], {encoding:'utf8',windowsHide:true}).trim());
assert.equal(nativeCount, 0, 'Native acceptance must remain serial');
const inputBytes = await readFile(resolve(process.argv[3]));
const buildProof = JSON.parse(inputBytes.toString('utf8').replace(/^\uFEFF/, ''));
assert.equal(buildProof.buildExitCode, 0); assert.equal(buildProof.productAndTestsUnchangedDuringBuild, true); assert.equal(buildProof.boundedBuildJob.exitCode, 0);
for (const [name, hash] of Object.entries(buildProof.repositoryBuildInputs)) assert.equal(createHash('sha256').update(await readFile(resolve(desktop, '..', name))).digest('hex').toUpperCase(), hash, 'Current product input differs: '+name);
const receipt = buildProof.candidate;
assert.equal(receipt.identifier, 'com.nanzhufeng.ai.desktop.windowsacceptance');
assert.equal(receipt.build, 'windows-x64-isolated-acceptance');
assert.equal(receipt.sha256, '15B1319D084BF786576130CE52451D90540753396F2D6106AAA34C684EEC3D06');
const sourceExecutable = resolve(desktop, 'output/windows-acceptance', receipt.sha256, 'NanfengAI-Windows-Acceptance.exe');
assert.equal(resolve(receipt.executable), sourceExecutable);
assert.equal(createHash('sha256').update(await readFile(sourceExecutable)).digest('hex').toUpperCase(), receipt.sha256);
assert.equal(stability.candidateSha256, 'DF51383345FFCF490ED7C5301E79234B6424597A25510E9AF117DAF291C40BFC');
const compiledGuiSha256 = receipt.sha256;
const executable = resolve('D:/CodexTemp/nfai-installed-acceptance.7d2cb30e-9606-4d88-bd91-bbc0fec53c92/nanfeng-ai-desktop-spike.exe');
assert.equal(buildProof.installed.newExeSha256, '6B4133EB211F3432F1C6EC0D02DA8B3A20B214069A198CED99E7BAF94F76F1A5');
receipt.sha256 = buildProof.installed.newExeSha256; receipt.executable = executable;
assert.equal(createHash('sha256').update(await readFile(executable)).digest('hex').toUpperCase(), receipt.sha256);
const candidateBinding = {buildProofPath:resolve(process.argv[3]),buildProofSha256:createHash('sha256').update(inputBytes).digest('hex').toUpperCase(),sourceExecutable:executable,compiledSourceExecutable:sourceExecutable,sha256:receipt.sha256,compiledGuiSha256};
const helper = join(desktop, 'scripts/windows-acceptance-cdp-eval.mjs');
let requests=0;const titleRequests=[];const responses=[];const closes=[];const requestChecks=[];
const textA='SYNTHETIC_SWITCH_A_PARTIAL';const tailA=' 后台完成A';const textB='SYNTHETIC_SWITCH_B_FINAL';
let held, heldB, heldC, heldRegeneration, heldBNext, heldRetry, heldDuringRetry; const overlap = []; const terminalStates = new Set(['COMPLETED','COMPLETED_ACCOUNTING_PENDING','FAILED','CANCELLED','UNKNOWN','RECOVERY_REQUIRED']);
const provider=http.createServer(async(request,response)=>{
 let body='';for await(const chunk of request)body+=chunk;const parsed=JSON.parse(body);
 if(String(request.headers['idempotency-key']||'').startsWith('conversation-title-')){
  titleRequests.push({model:parsed.model,idempotencyKeySha256:createHash('sha256').update(String(request.headers['idempotency-key'])).digest('hex')});
  assert.equal(parsed.stream,false,'Title transport is non-stream JSON');
  response.writeHead(200,{'Content-Type':'application/json'});
  response.end(JSON.stringify({model:parsed.model,choices:[{message:{role:'assistant',content:JSON.stringify({title:'合成聊天并发验证'})},finish_reason:'stop'}],usage:{prompt_tokens:5,completion_tokens:7,total_tokens:12}})); return;
 }
 requests++;requestChecks.push({ordinal:requests,userHasEditedA:JSON.stringify(parsed.messages.filter(m=>m.role==='user')).includes('SYNTHETIC_EDITED_A_QUESTION'),userHasPreEditA:JSON.stringify(parsed.messages.filter(m=>m.role==='user')).includes('SYNTHETIC_WHILE_RETRY_A_QUESTION'),model:parsed.model,idempotencyKeySha256:createHash('sha256').update(String(request.headers['idempotency-key']||'')).digest('hex'),messagesSha256:createHash('sha256').update(JSON.stringify(parsed.messages)).digest('hex'),hasA:JSON.stringify(parsed.messages).includes('SYNTHETIC_SWITCH_A_QUESTION'),hasB:JSON.stringify(parsed.messages).includes('SYNTHETIC_SWITCH_B_DRAFT'),userHasA:JSON.stringify(parsed.messages.filter(m=>m.role==='user')).includes('SYNTHETIC_SWITCH_A_QUESTION'),userHasB:JSON.stringify(parsed.messages.filter(m=>m.role==='user')).includes('SYNTHETIC_SWITCH_B_DRAFT'),userMessageCount:parsed.messages.filter(m=>m.role==='user').length});
 if(requests===6){response.writeHead(429,{'Content-Type':'application/json'});response.end(JSON.stringify({error:{message:'synthetic rate limit',type:'rate_limit_error'}}));return;}
 response.writeHead(200,{'Content-Type':'text/event-stream'});responses.push(response);
 if(requests===1){held=response;response.write(`data: ${JSON.stringify({choices:[{delta:{content:textA},finish_reason:null}]})}\n\n`);}
 else if(requests===2){heldB=response;response.write(`data: ${JSON.stringify({choices:[{delta:{content:textB},finish_reason:null}]})}\n\n`);overlap.push({event:'B_HTTP_ACCEPTED_WHILE_A_HELD',utc:new Date().toISOString(),aResponseOpen:!held.writableEnded});} else if(requests===3){heldC=response;response.write(`data: ${JSON.stringify({choices:[{delta:{content:'SYNTHETIC_STOP_C_PARTIAL'},finish_reason:null}]})}\n\n`);} else if(requests===4){heldRegeneration=response;response.write(`data: ${JSON.stringify({choices:[{delta:{content:'SYNTHETIC_A_REGENERATED_PARTIAL'},finish_reason:null}]})}\n\n`);} else if(requests===5){heldBNext=response;response.write(`data: ${JSON.stringify({choices:[{delta:{content:'SYNTHETIC_B_WHILE_REGENERATING'},finish_reason:null}]})}\n\n`);} else if(requests===7){heldRetry=response;response.write(`data: ${JSON.stringify({choices:[{delta:{content:'SYNTHETIC_RETRY_B_RESTORED'},finish_reason:null}]})}\n\n`);} else if(requests===8){heldDuringRetry=response;response.write(`data: ${JSON.stringify({choices:[{delta:{content:'SYNTHETIC_WHILE_RETRY_A_ANSWER'},finish_reason:null}]})}\n\n`);} else if(requests===9||requests===10){response.end(`data: ${JSON.stringify({choices:[{delta:{content:requests===9?'SYNTHETIC_CHANGE_MODEL_ANSWER':'SYNTHETIC_EDITED_A_ANSWER'},finish_reason:'stop'}],usage:{prompt_tokens:5,completion_tokens:7,total_tokens:12}})}\n\ndata: [DONE]\n\n`);} else {response.end('data: [DONE]\n\n');}
});
await new Promise(done => provider.listen(0, '127.0.0.1', done));
const endpoint = `http://127.0.0.1:${provider.address().port}`;
let launch, root, port;
const pids = []; const jobMembership = [];
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
  const code = `[Console]::OutputEncoding=[Text.UTF8Encoding]::new($false); & ${quote(join(desktop, 'scripts/start-windows-acceptance.ps1'))} -RootParent ${quote(parent)} -ExecutablePath ${quote(receipt.executable)} -RunInPlace -StartupMode normal -Visible -DebugPort ${port} -OrdinaryChatTestEndpoint ${quote(endpoint)}${reopen ? ` -Root ${quote(root)}` : ''}`;
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
  if (process.env.NFAI_BOUNDED_JOB_NAME) {
    assert.match(process.env.NFAI_BOUNDED_JOB_NAME, /^NanfengAIBounded-[0-9a-f-]{36}$/);
    const member = ps(`Add-Type -TypeDefinition @'
using System; using System.ComponentModel; using System.Runtime.InteropServices;
public static class NfaiJobMembership {
 [DllImport("kernel32.dll",CharSet=CharSet.Unicode,SetLastError=true)] static extern IntPtr OpenJobObject(uint access,bool inherit,string name);
 [DllImport("kernel32.dll",SetLastError=true)] static extern IntPtr OpenProcess(uint access,bool inherit,uint pid);
 [DllImport("kernel32.dll",SetLastError=true)] static extern bool IsProcessInJob(IntPtr process,IntPtr job,out bool member);
 [DllImport("kernel32.dll")] static extern bool CloseHandle(IntPtr handle);
 public static bool Contains(uint pid,string name){
  IntPtr job=OpenJobObject(4,false,name),process=OpenProcess(0x1000,false,pid);
  try {if(job==IntPtr.Zero||process==IntPtr.Zero)throw new Win32Exception();bool value;if(!IsProcessInJob(process,job,out value))throw new Win32Exception();return value;}
  finally {if(process!=IntPtr.Zero)CloseHandle(process);if(job!=IntPtr.Zero)CloseHandle(job);}
 }
}
'@; [NfaiJobMembership]::Contains(${Number(launch.pid)},${quote(process.env.NFAI_BOUNDED_JOB_NAME)})`);
    assert.equal(member, 'True', 'owned Native must belong to the bounded Job');
    launch.boundedJobMembershipVerified = true;
    jobMembership.push({pid:Number(launch.pid),verified:true,job:process.env.NFAI_BOUNDED_JOB_NAME});
  }
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
 await start(); const workspaceId=(await invoke('ensure_desktop_local_workspace')).summary.id;
 await ui('[data-action=new-chat]'); await webOff(); await type('SYNTHETIC_SWITCH_A_QUESTION');
 await ui('[data-action=save-local-message]:not([disabled])'); await until(()=>requests===1,'request A');
 const running=await until(async()=>{const w=await invoke('read_desktop_workspace',{workspaceId});return w.exchange.conversations.find(c=>c.messages.some(m=>textOf(m)===textA));},'persisted A partial');
 await until(()=>evaluate(`document.querySelector('.chat-scroll')?.textContent.includes(${JSON.stringify(textA)}) && Boolean(document.querySelector('[data-action=stop-ordinary-chat]'))`),'actual A stream');
 await until(()=>evaluate('globalThis.__nanfengDesktopWorkState?.pendingChatSubmission===null'),'A submission registration');
 await ui('[data-action=new-chat]'); await webOff(); await type('SYNTHETIC_SWITCH_B_DRAFT');
 await ui('[data-action=save-local-message]:not([disabled])'); await until(()=>requests===2,'B request while A unfinished');
 assert.equal(held.writableEnded,false); assert.equal(overlap[0].aResponseOpen,true);
 const b=await until(async()=>{const w=await invoke('read_desktop_workspace',{workspaceId});return w.exchange.conversations.find(c=>c.messages.some(m=>textOf(m)===textB));},'persisted B partial');
 assert.notEqual(b.id,running.id);
 const activeA=await invoke('read_latest_desktop_ordinary_chat_attempt',{workspaceId,conversationId:running.id});
 const activeB=await invoke('read_latest_desktop_ordinary_chat_attempt',{workspaceId,conversationId:b.id});
 assert.ok(!terminalStates.has(activeA.state)); assert.ok(!terminalStates.has(activeB.state)); assert.notEqual(activeA.attemptId,activeB.attemptId);
 overlap.push({event:'BOTH_ATTEMPTS_ACTIVE',utc:new Date().toISOString(),attemptA:activeA.attemptId,attemptB:activeB.attemptId,stateA:activeA.state,stateB:activeB.state});
 await until(()=>evaluate(`document.querySelector('.chat-scroll')?.textContent.includes(${JSON.stringify(textB)}) && !document.querySelector('.chat-scroll')?.textContent.includes(${JSON.stringify(textA)})`),'actual B stream without A UI');
 await type('SYNTHETIC_CONCURRENT_B_NEXT_DRAFT');
 const draftBefore=await until(async()=>{const d=await invoke('read_desktop_conversation_draft',{workspaceId,conversationId:b.id});return d?.text==='SYNTHETIC_CONCURRENT_B_NEXT_DRAFT'?d:null;},'persisted B next draft');
 held.write(`data: ${JSON.stringify({choices:[{delta:{content:tailA},finish_reason:null}]})}\n\n`);
 held.end(`data: ${JSON.stringify({choices:[{delta:{},finish_reason:'stop'}],usage:{prompt_tokens:5,completion_tokens:7,total_tokens:12}})}\n\ndata: [DONE]\n\n`);
 const completedA=await until(async()=>{const a=await invoke('read_latest_desktop_ordinary_chat_attempt',{workspaceId,conversationId:running.id});return a?.state==='COMPLETED'?a:null;},'A completes while B held');
 assert.equal(heldB.writableEnded,false);
 assert.ok(!terminalStates.has((await invoke('read_latest_desktop_ordinary_chat_attempt',{workspaceId,conversationId:b.id})).state));
 assert.equal(await evaluate('document.querySelector("#chat-composer")?.value'),'SYNTHETIC_CONCURRENT_B_NEXT_DRAFT');
 assert.deepEqual(await invoke('read_desktop_conversation_draft',{workspaceId,conversationId:b.id}),draftBefore);
 assert.equal(await evaluate(`document.querySelector('.chat-scroll')?.textContent.includes(${JSON.stringify(textA)})||false`),false);
 heldB.end(`data: ${JSON.stringify({choices:[{delta:{},finish_reason:'stop'}],usage:{prompt_tokens:5,completion_tokens:7,total_tokens:12}})}\n\ndata: [DONE]\n\n`);
 const completedB=await until(async()=>{const a=await invoke('read_latest_desktop_ordinary_chat_attempt',{workspaceId,conversationId:b.id});return a?.state==='COMPLETED'?a:null;},'B completes');
 let after=await invoke('read_desktop_workspace',{workspaceId});
 for(const [id,attempt,text] of [[running.id,completedA,textA+tailA],[b.id,completedB,textB]]){
  const c=after.exchange.conversations.find(x=>x.id===id); assert.equal(textOf(c.messages.find(m=>m.id===attempt.assistantMessageId)),text);
 }
 assert.equal(requestChecks[0].userHasA,true); assert.equal(requestChecks[0].userHasB,false);
 assert.equal(requestChecks[1].userHasA,false); assert.equal(requestChecks[1].userHasB,true); assert.equal(requestChecks[1].userMessageCount,1);
 const contextRecords=await invoke('read_desktop_ordinary_chat_context_records');
 if(requestChecks[1].hasA){assert.ok(contextRecords.find(x=>x.attemptId===completedB.attemptId)?.selectedSources.some(x=>x.kind==='历史对话'));}
 // Both actual DOM actions run synchronously in one task. Stop awaits the
 // native Promise; navigation selects B before that Promise can resume.
 // Tauri's protected invocation object is never replaced.
 await ui('[data-action=new-chat]'); await webOff(); await type('SYNTHETIC_STOP_C_QUESTION');
 await ui('[data-action=save-local-message]:not([disabled])'); await until(()=>requests===3,'C request');
 const c=await until(async()=>{const w=await invoke('read_desktop_workspace',{workspaceId});return w.exchange.conversations.find(x=>x.messages.some(m=>textOf(m)==='SYNTHETIC_STOP_C_PARTIAL'));},'persisted C partial');
 await until(()=>evaluate('globalThis.__nanfengDesktopWorkState?.pendingChatSubmission===null && Boolean(document.querySelector("[data-action=stop-ordinary-chat]"))'),'C stop ready');
 const routeAfterStopAndNavigate=await evaluate(`(()=>{const stop=document.querySelector('[data-action=stop-ordinary-chat]');const destination=document.querySelector('[data-action=select-chat][data-id="${b.id}"]');if(!stop||!destination)throw new Error('Stop/navigation controls missing');stop.click();destination.click();return globalThis.__nanfengDesktopWorkState.selectedConversationId;})()`);
 assert.equal(routeAfterStopAndNavigate,b.id);
 await until(()=>evaluate(`globalThis.__nanfengDesktopWorkState?.selectedConversationId===${JSON.stringify(b.id)} && document.querySelector("#chat-composer")?.value==='SYNTHETIC_CONCURRENT_B_NEXT_DRAFT'`),'B route after C stop');
 const afterStopNavigation=await evaluate('({status:globalThis.__nanfengDesktopWorkState.status,error:globalThis.__nanfengDesktopWorkState.error,draft:document.querySelector("#chat-composer")?.value})');
 assert.doesNotMatch(afterStopNavigation.status,/正在停止|停止失败/);
 assert.doesNotMatch(afterStopNavigation.error,/停止失败/);
 assert.equal(afterStopNavigation.draft,'SYNTHETIC_CONCURRENT_B_NEXT_DRAFT');
 const cancelledC=await until(async()=>{const a=await invoke('read_latest_desktop_ordinary_chat_attempt',{workspaceId,conversationId:c.id});return a?.state==='CANCELLED'?a:null;},'C native cancellation');
 const afterCancellation=await evaluate('({status:globalThis.__nanfengDesktopWorkState.status,error:globalThis.__nanfengDesktopWorkState.error,draft:document.querySelector("#chat-composer")?.value})');
 assert.doesNotMatch(afterCancellation.status,/正在停止|停止失败/);assert.doesNotMatch(afterCancellation.error,/停止失败/);assert.equal(afterCancellation.draft,'SYNTHETIC_CONCURRENT_B_NEXT_DRAFT');
 after=await invoke('read_desktop_workspace',{workspaceId});
 const stoppedMessage=after.exchange.conversations.find(x=>x.id===c.id).messages.find(m=>m.id===cancelledC.assistantMessageId);
 assert.equal(textOf(stoppedMessage),'SYNTHETIC_STOP_C_PARTIAL');
 const stopReceipt={conversationId:c.id,attempt:cancelledC,sameUiTaskStopThenNavigate:true,routeAfterStopAndNavigate,afterNavigation:afterStopNavigation,afterCancellation,bridgeReplaced:false,partialPreserved:true};
 // The first two streams are complete. Regenerate A through its real menu,
 // then send another turn in B before A's regenerated stream is released.
 await ui(`[data-action=select-chat][data-id="${running.id}"]`);
 await until(()=>evaluate(`document.querySelector('.chat-scroll')?.textContent.includes(${JSON.stringify(textA+tailA)})`),'A original completed answer');
 await ui(`[data-action=open-assistant-message-menu][data-message-id="${completedA.assistantMessageId}"]`);
 await ui('[data-action=regenerate-completed-answer]');
 await until(()=>requests===4,'A regenerated native request');
 await until(()=>evaluate('globalThis.__nanfengDesktopWorkState?.pendingChatSubmission===null'),'regeneration admission');
 await ui(`[data-action=select-chat][data-id="${b.id}"]`);
 await until(()=>evaluate(`globalThis.__nanfengDesktopWorkState?.selectedConversationId===${JSON.stringify(b.id)} && document.querySelector("#chat-composer")?.value==='SYNTHETIC_CONCURRENT_B_NEXT_DRAFT'`),'B draft before send during regeneration');
 await type('SYNTHETIC_WHILE_REGENERATING_B_QUESTION');
 await ui('[data-action=save-local-message]:not([disabled])');
 await until(()=>requests===5,'B actual Send accepted while A regeneration streams');
 assert.equal(heldRegeneration.writableEnded,false);
 const regeneratingA=await invoke('read_latest_desktop_ordinary_chat_attempt',{workspaceId,conversationId:running.id});
 const runningBNext=await invoke('read_latest_desktop_ordinary_chat_attempt',{workspaceId,conversationId:b.id});
 assert.ok(!terminalStates.has(regeneratingA.state));assert.ok(!terminalStates.has(runningBNext.state));
 assert.notEqual(regeneratingA.attemptId,completedA.attemptId);assert.notEqual(runningBNext.attemptId,completedB.attemptId);
 await until(()=>evaluate('document.querySelector(".chat-scroll")?.textContent.includes("SYNTHETIC_B_WHILE_REGENERATING")'),'B new streamed answer');
 await type('SYNTHETIC_B_AFTER_REGENERATE_DRAFT');
 let finalDraft=await until(async()=>{const d=await invoke('read_desktop_conversation_draft',{workspaceId,conversationId:b.id});return d?.text==='SYNTHETIC_B_AFTER_REGENERATE_DRAFT'?d:null;},'B final draft persists');
 const generationOverlap={regeneratingAttempt:regeneratingA.attemptId,otherAttempt:runningBNext.attemptId,states:[regeneratingA.state,runningBNext.state],httpOverlap:true,actualUiRegenerateAndSend:true};
 heldRegeneration.end(`data: ${JSON.stringify({choices:[{delta:{},finish_reason:'stop'}],usage:{prompt_tokens:5,completion_tokens:7,total_tokens:12}})}\n\ndata: [DONE]\n\n`);
 const regeneratedA=await until(async()=>{const a=await invoke('read_latest_desktop_ordinary_chat_attempt',{workspaceId,conversationId:running.id});return a?.state==='COMPLETED'?a:null;},'A regeneration completes while B active');
 assert.equal(heldBNext.writableEnded,false);
 assert.equal(await evaluate('document.querySelector("#chat-composer")?.value'),'SYNTHETIC_B_AFTER_REGENERATE_DRAFT');
 heldBNext.end(`data: ${JSON.stringify({choices:[{delta:{},finish_reason:'stop'}],usage:{prompt_tokens:5,completion_tokens:7,total_tokens:12}})}\n\ndata: [DONE]\n\n`);
 const completedBNext=await until(async()=>{const a=await invoke('read_latest_desktop_ordinary_chat_attempt',{workspaceId,conversationId:b.id});return a?.state==='COMPLETED'?a:null;},'B next answer completes');
 after=await invoke('read_desktop_workspace',{workspaceId});
 const finalA=after.exchange.conversations.find(x=>x.id===running.id);
 assert.equal(textOf(finalA.messages.find(m=>m.id===completedA.assistantMessageId)),textA+tailA);
 assert.equal(textOf(finalA.messages.find(m=>m.id===regeneratedA.assistantMessageId)),'SYNTHETIC_A_REGENERATED_PARTIAL');
 assert.equal(textOf(after.exchange.conversations.find(x=>x.id===b.id).messages.find(m=>m.id===completedBNext.assistantMessageId)),'SYNTHETIC_B_WHILE_REGENERATING');
 // Fail one new B turn explicitly; no transport retry is automatic.
 await type('SYNTHETIC_RETRY_B_QUESTION');
 await ui('[data-action=save-local-message]:not([disabled])');
 const failedB=await until(async()=>{const a=await invoke('read_latest_desktop_ordinary_chat_attempt',{workspaceId,conversationId:b.id});return requests===6&&a?.state==='FAILED'?a:null;},'B explicit synthetic 429 failure');
 await until(()=>evaluate('globalThis.__nanfengDesktopWorkState?.pendingChatSubmission===null'),'failed send registration settles');
 await ui(`[data-action=retry-ordinary-chat][data-attempt-id="${failedB.attemptId}"]`);
 await until(()=>requests===7,'explicit UI retry request');
 await until(()=>evaluate('globalThis.__nanfengDesktopWorkState?.pendingChatSubmission===null'),'retry admission');
 const retryingB=await invoke('read_latest_desktop_ordinary_chat_attempt',{workspaceId,conversationId:b.id});
 assert.ok(!terminalStates.has(retryingB.state));assert.equal(retryingB.attemptId,failedB.attemptId);
 assert.equal(retryingB.providerId,failedB.providerId);assert.equal(retryingB.requestedModelId,failedB.requestedModelId);
 assert.equal(retryingB.retryCount,failedB.retryCount+1);
 assert.equal(requestChecks[5].model,requestChecks[6].model);
 assert.notEqual(requestChecks[5].idempotencyKeySha256,createHash('sha256').update('').digest('hex'));
 assert.equal(requestChecks[5].idempotencyKeySha256,requestChecks[6].idempotencyKeySha256);
 await type('SYNTHETIC_B_AFTER_RETRY_DRAFT');
 finalDraft=await until(async()=>{const d=await invoke('read_desktop_conversation_draft',{workspaceId,conversationId:b.id});return d?.text==='SYNTHETIC_B_AFTER_RETRY_DRAFT'?d:null;},'B draft during retry');
 await ui(`[data-action=select-chat][data-id="${running.id}"]`);
 await until(()=>evaluate(`globalThis.__nanfengDesktopWorkState?.selectedConversationId===${JSON.stringify(running.id)} && document.querySelector("#chat-composer")?.value===''`),'A loaded after retry navigation');
 await type('SYNTHETIC_WHILE_RETRY_A_QUESTION');
 await ui('[data-action=save-local-message]:not([disabled])');
 await until(()=>requests===8,'A actual Send while B retry streams');
 assert.equal(heldRetry.writableEnded,false);
 const activeDuringRetry=await invoke('read_latest_desktop_ordinary_chat_attempt',{workspaceId,conversationId:running.id});
 assert.ok(!terminalStates.has(activeDuringRetry.state));assert.notEqual(activeDuringRetry.attemptId,retryingB.attemptId);
 const retryOverlap={failedB,retryingB,otherAttempt:activeDuringRetry.attemptId,httpOverlap:true,explicitUiRetry:true,originalModelAndIdempotency:true};
 heldRetry.end(`data: ${JSON.stringify({choices:[{delta:{},finish_reason:'stop'}],usage:{prompt_tokens:5,completion_tokens:7,total_tokens:12}})}\n\ndata: [DONE]\n\n`);
 const retriedB=await until(async()=>{const a=await invoke('read_latest_desktop_ordinary_chat_attempt',{workspaceId,conversationId:b.id});return a?.state==='COMPLETED'?a:null;},'B explicit retry completed');
 assert.equal(heldDuringRetry.writableEnded,false);assert.equal(retriedB.attemptId,failedB.attemptId);
 assert.deepEqual(await invoke('read_desktop_conversation_draft',{workspaceId,conversationId:b.id}),finalDraft);
 heldDuringRetry.end(`data: ${JSON.stringify({choices:[{delta:{},finish_reason:'stop'}],usage:{prompt_tokens:5,completion_tokens:7,total_tokens:12}})}\n\ndata: [DONE]\n\n`);
 const completedDuringRetry=await until(async()=>{const a=await invoke('read_latest_desktop_ordinary_chat_attempt',{workspaceId,conversationId:running.id});return a?.state==='COMPLETED'?a:null;},'A completes after B retry');
 after=await invoke('read_desktop_workspace',{workspaceId});
 assert.equal(textOf(after.exchange.conversations.find(x=>x.id===b.id).messages.find(m=>m.id===retriedB.assistantMessageId)),'SYNTHETIC_RETRY_B_RESTORED');
 assert.equal(textOf(after.exchange.conversations.find(x=>x.id===running.id).messages.find(m=>m.id===completedDuringRetry.assistantMessageId)),'SYNTHETIC_WHILE_RETRY_A_ANSWER');
 // Explicit model choice requires enabled stored credentials. Only fresh
 // synthetic secrets are written to this isolated root; loopback stays pinned.
 const services=await invoke('read_desktop_model_service_settings');
 for(const [providerId,presetId] of [['OPENROUTER','GPT_5_6_TERRA'],['DEEPSEEK','DEEPSEEK_V4_PRO']]){
  const setting=services.find(item=>item.providerId===providerId);assert.ok(setting);
  await invoke('save_desktop_model_service_settings',{args:{providerId,presetId,enabled:true,expectedRevision:setting.revision,apiKey:`nfai-synthetic-ui-${providerId.toLowerCase()}-${randomUUID()}`}});
 }
 const beforeModelChoice=await invoke('read_desktop_workspace',{workspaceId});
 const beforeModelLedger=await invoke('read_desktop_usage_ledger');
 await normalClose();await start(true);
 assert.deepEqual(await invoke('read_desktop_workspace',{workspaceId}),beforeModelChoice);
 assert.deepEqual(await invoke('read_desktop_usage_ledger'),beforeModelLedger);assert.equal(requests,8);
 await ui(`[data-action=select-chat][data-id="${running.id}"]`);
 await ui(`[data-action=open-assistant-message-menu][data-message-id="${completedDuringRetry.assistantMessageId}"]`);
 await ui('[data-action=change-model-completed-answer]');
 await ui('[data-action=confirm-change-answer-model][data-target-preset-id="DEEPSEEK_V4_PRO"]');
 const changedModel=await until(async()=>{const a=await invoke('read_latest_desktop_ordinary_chat_attempt',{workspaceId,conversationId:running.id});return requests===9&&a?.state==='COMPLETED'?a:null;},'actual explicit model answer');
 assert.equal(changedModel.providerId,'DEEPSEEK');assert.equal(requestChecks[8].model,'deepseek-v4-pro');
 assert.notEqual(changedModel.attemptId,completedDuringRetry.attemptId);
 await until(()=>evaluate('!globalThis.__nanfengDesktopWorkState?.ordinarySendPreparing && !globalThis.__nanfengDesktopWorkState?.pendingChatSubmission'),'model choice UI receipt settles');
 await ui(`[data-action=open-assistant-message-menu][data-message-id="${completedDuringRetry.userMessageId}"]`);
 await ui('[data-action=edit-conversation-user-message]');
 await evaluate(`(()=>{const field=document.querySelector('#conversation-user-message-edit');if(!field)throw new Error('editor missing');field.value='SYNTHETIC_EDITED_A_QUESTION';field.dispatchEvent(new Event('input',{bubbles:true}));return true})()`);
 await ui('[data-action=save-conversation-user-message-edit]');
 const editedUser=await until(async()=>{const w=await invoke('read_desktop_workspace',{workspaceId});const c=w.exchange.conversations.find(x=>x.id===running.id);const leaf=c?.messages.find(m=>m.id===c.currentLeafId);return leaf?.role==='user'&&textOf(leaf)==='SYNTHETIC_EDITED_A_QUESTION'?leaf:null;},'actual edit creates user branch');
 assert.notEqual(editedUser.id,completedDuringRetry.userMessageId);
 await ui(`[data-action=open-assistant-message-menu][data-message-id="${editedUser.id}"]`);
 await ui('[data-action=generate-answer-for-saved-user]');
 const generatedEdited=await until(async()=>{const a=await invoke('read_latest_desktop_ordinary_chat_attempt',{workspaceId,conversationId:running.id});return requests===10&&a?.state==='COMPLETED'?a:null;},'actual saved-user answer');
 assert.equal(generatedEdited.userMessageId,editedUser.id);
 assert.equal(requestChecks[9].userHasEditedA,true);assert.equal(requestChecks[9].userHasPreEditA,false);
 after=await invoke('read_desktop_workspace',{workspaceId});
 const editedConversation=after.exchange.conversations.find(x=>x.id===running.id);
 assert.equal(textOf(editedConversation.messages.find(m=>m.id===completedDuringRetry.userMessageId)),'SYNTHETIC_WHILE_RETRY_A_QUESTION');
 assert.equal(textOf(editedConversation.messages.find(m=>m.id===changedModel.assistantMessageId)),'SYNTHETIC_CHANGE_MODEL_ANSWER');
 assert.equal(textOf(editedConversation.messages.find(m=>m.id===generatedEdited.assistantMessageId)),'SYNTHETIC_EDITED_A_ANSWER');
 const notificationPermission = await invoke('read_desktop_reminder_notification_permission').then(value=>({readable:true,value})).catch(error=>({readable:false,safeError:String(error)}));
 const ledger=await invoke('read_desktop_usage_ledger'); await normalClose(); await start(true);
 assert.deepEqual(await invoke('read_desktop_workspace',{workspaceId}),after); assert.deepEqual(await invoke('read_desktop_usage_ledger'),ledger);
 assert.deepEqual(await invoke('read_desktop_conversation_draft',{workspaceId,conversationId:b.id}),finalDraft);
 assert.equal(requests,10); await normalClose();
 assert.equal(createHash('sha256').update(await readFile(nativeCasePath)).digest('hex').toUpperCase(),nativeCaseSha256,'Native case source changed during run');
 const result={root,verifierSha256:nativeCaseSha256,verifierSourceUnchangedDuringRun:true,candidateSha256:receipt.sha256,compiledGuiSha256,installedExecution:true,candidateBinding,workspaceId,conversationA:running.id,conversationB:b.id,completedA,completedB,stopReceipt,generationOverlap,retryOverlap,retriedB,completedDuringRetry,changedModel,editedUser,generatedEdited,intermediateColdExact:true,regeneratedA,completedBNext,draftBefore,finalDraft,after,ledger,requestChecks,contextRecords,overlap,notificationPermission,requests,titleRequests,totalHttpRequests:requests+titleRequests.length,pids,windows,closes,jobMembership,installedStabilityAccepted:false,coldExact:true,backgroundDidNotOverwriteDraft:true,syntheticProvider:true,realService:false,httpOverlap:true,concurrentModelTasks:true,pass:true};
 await writeFile(join(root,'chat-concurrent-candidate-native.json'),JSON.stringify(result,null,2));
 console.log(JSON.stringify({root,candidateSha256:receipt.sha256,requests,overlap,closes,coldExact:true,pass:true}));
}catch(error){if(root)await writeFile(join(root,'chat-concurrent-candidate-failure.json'),JSON.stringify({root,pids,windows,closes,requestChecks,error:String(error)},null,2));throw error;}finally{stop();for(const response of responses)response.destroy();provider.closeAllConnections();await new Promise(done=>provider.close(done));}
