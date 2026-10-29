import { completeLaunchReceipt } from './windows-launch-receipt.mjs';
import assert from 'node:assert/strict';
import { createHash, randomUUID } from 'node:crypto';
import { execFileSync, spawn } from 'node:child_process';
import { readFile, writeFile, stat } from 'node:fs/promises';
import http from 'node:http';
import { dirname, join, resolve, sep } from 'node:path';
import { fileURLToPath } from 'node:url';

if (process.platform !== 'win32') throw new Error('仅在 Windows 隔离候选上验收。');
const desktopRoot = resolve(dirname(fileURLToPath(import.meta.url)), '..');
assert.ok(process.argv[2], 'Explicit owned synthetic parent required');
const rootParent = resolve(process.argv[2]);
assert.ok(process.execArgv.includes('--max-old-space-size=2048'));
if (process.argv.includes('--screenshot')) throw new Error('后台验收不支持前台截图');
const receipt = JSON.parse(await readFile(join(desktopRoot, 'output/windows-acceptance/current.json'), 'utf8'));
assert.equal(receipt.identifier, 'com.nanzhufeng.ai.desktop.windowsacceptance');
const source = resolve(desktopRoot, 'output/windows-acceptance', receipt.sha256, 'NanfengAI-Windows-Acceptance.exe');
assert.equal(resolve(receipt.executable), source);
assert.equal(createHash('sha256').update(await readFile(source)).digest('hex').toUpperCase(), receipt.sha256);
const runExecutable = resolve(desktopRoot, 'output/windows-acceptance-binaries', receipt.sha256, 'nanfeng-ai-desktop-spike.exe');
const ps = (args) => execFileSync('pwsh', ['-NoProfile', ...args], { encoding: 'utf8', timeout: 60000, windowsHide: true }).trim();
const launchPowerShell = (args) => new Promise((resolveLaunch, reject) => {
  const child = spawn('pwsh', ['-NoProfile', ...args], { windowsHide: true, stdio: ['ignore', 'pipe', 'pipe'] });
  let stdout = '';
  let stderr = '';
  const timer = setTimeout(() => { child.kill(); reject(new Error(`隔离候选启动超时：${stderr}`)); }, 30000);
  child.stdout.on('data', data => {
    stdout += data.toString();
    let emitted;
    try { emitted=completeLaunchReceipt(stdout); } catch(error) { clearTimeout(timer); reject(error); return; }
    if (!emitted) return;
    clearTimeout(timer);
    readFile(emitted.launchReceipt, 'utf8').then(data=>resolveLaunch(JSON.parse(data)),reject);
    child.stdout.destroy();
    child.stderr.destroy();
    child.unref();
  });
  child.stderr.on('data', data => { stderr += data.toString(); });
  child.on('error', error => { clearTimeout(timer); reject(error); });
  child.on('exit', code => {
    if (!stdout.includes('"pid"')) { clearTimeout(timer); reject(new Error(`隔离候选启动失败 ${code}：${stderr}`)); }
  });
});
const reservePort = async () => {
  const server = http.createServer();
  await new Promise(resolveReady => server.listen(0, '127.0.0.1', resolveReady));
  const port = server.address().port;
  await new Promise(resolveClose => server.close(resolveClose));
  return port;
};



let launch = null;
let root = '';
const pids = [];
const stop = () => {
  if (!launch) return;
  const pid = Number(launch.pid);
  const literal = value => `'${String(value).replaceAll("'", "''")}'`;
  const script = '$started=[datetime]' + literal(launch.processStartedAt) + '; $expected=' + literal(runExecutable) + '; $hash=' + literal(receipt.sha256) + '; '
    + '$p=Get-CimInstance Win32_Process -Filter "ProcessId=' + pid + '"; '
    + 'if ($p) { if ([IO.Path]::GetFullPath([string]$p.ExecutablePath) -ne $expected '
    + '-or (Get-FileHash -LiteralPath $expected -Algorithm SHA256).Hash -ne $hash) { throw "PID identity mismatch" }; '
    + '$owned=Get-Process -Id ' + pid + ' -ErrorAction Stop; if ([Math]::Abs(($owned.StartTime.ToUniversalTime()-$started.ToUniversalTime()).TotalMilliseconds) -gt 20) { throw "PID start time mismatch" }; ' 
    + 'Stop-Process -Id ' + pid + ' -Force -ErrorAction Stop }';
  ps(['-Command', script]);
  pids.push(pid);
  launch = null;
};
async function start(reopen = false) {
  const port = await reservePort();
  const args = ['-File', join(desktopRoot, 'scripts/start-windows-acceptance.ps1'), '-RootParent', rootParent,
    '-StartupMode', 'normal', '-DebugPort', String(port), '-OrdinaryChatTestEndpoint', `http://127.0.0.1:${providerPort}`];
  if (reopen) args.push('-Root', root);
  launch = await launchPowerShell(args);
  assert.equal(launch.visible, false);
  assert.equal(launch.sha256, receipt.sha256);
  assert.equal(resolve(launch.executable), runExecutable);
  root = resolve(launch.root);
  assert.ok(root.startsWith(rootParent + sep));
  const marker = JSON.parse(await readFile(join(root, '.nanfeng-windows-acceptance.json'), 'utf8'));
  assert.equal(marker.identifier, receipt.identifier);
  return port;
}
async function cdp(port, method, params = {}, which = "main") {
  const tabs = await fetch(`http://127.0.0.1:${port}/json/list`,{signal:AbortSignal.timeout(10000)}).then(response => response.json());
  const tab = tabs.find(item => item.type === 'page' && item.url === (which==='island'?'http://tauri.localhost/island.html':'http://tauri.localhost/'));
  assert.ok(tab, '验收 WebView2 页面未就绪');
  const socket = new WebSocket(tab.webSocketDebuggerUrl);
  try {
    await new Promise((resolveReady, reject) => {
      socket.addEventListener('open', resolveReady, { once: true });
      socket.addEventListener('error', reject, { once: true });
    });
    return await new Promise((resolveResult, reject) => {
      const timer = setTimeout(() => reject(new Error('CDP evaluation timed out')), 30000);
      socket.addEventListener('message', event => {
        const message = JSON.parse(event.data);
        if (message.id !== 1) return;
        clearTimeout(timer);
        if (message.error || message.result?.exceptionDetails) reject(new Error(
          message.result?.exceptionDetails?.exception?.description || message.result?.exceptionDetails?.text || message.error?.message));
        else resolveResult(message.result);
      });
      socket.send(JSON.stringify({ id: 1, method, params }));
    });
  } finally { socket.close(); }
}
async function evaluate(port, expression) {
  return (await cdp(port, 'Runtime.evaluate', { expression, awaitPromise: true, returnByValue: true }))?.result?.value;
}
async function invoke(port, area, command, args = {}) {
  const expression = `window.__TAURI__.core.invoke(${JSON.stringify(command)},${JSON.stringify(args)},${JSON.stringify({ headers: { 'x-nanfeng-data-area': area } })}).catch(error=>({__error:String(error)}))`;
  const result = await evaluate(port, expression);
  if (result?.__error) throw new Error(`${command}: ${result.__error}`);
  return result;
}
async function ready(port) {
  const deadline = Date.now() + 30000;
  let lastError="IPC not initialized";
  while (Date.now() < deadline) {
    try { if (await evaluate(port, 'Boolean(window.__TAURI__?.core?.invoke)')) {
      const hidden=JSON.parse(ps(['-File',join(desktopRoot,'scripts/close-windows-hidden-acceptance.ps1'),
        '-ProcessId',String(launch.pid),'-Executable',runExecutable,'-Sha256',receipt.sha256,
        '-StartedAt',launch.processStartedAt,'-InspectOnly']));
      assert.equal(hidden.visibleWindows,0);result.startupHidden=hidden;return;
    } } catch(error) { lastError=String(error); }
    await new Promise(resolveWait => setTimeout(resolveWait, 250));
  }
  throw new Error(`原生 IPC 未就绪: ${lastError}`);
}
async function until(check, label) {
  const deadline = Date.now() + 30000;
  while (Date.now() < deadline) {
    try { const value = await check(); if (value) return value; } catch { /* Hidden page may be refreshing. */ }
    await new Promise(resolveWait => setTimeout(resolveWait, 200));
  }
  throw new Error(`${label} 未在原生界面出现`);
}
async function clickUi(port, selector) {
  const clicked = await evaluate(port, `(()=>{const nodes=[...document.querySelectorAll(${JSON.stringify(selector)})].filter(n=>n.getClientRects().length && !n.disabled);if(nodes.length!==1)return {count:nodes.length};nodes[0].click();return {count:1}})()`);
  assert.equal(clicked?.count, 1, `控件必须可见、唯一且可用: ${selector}`);
}
async function normalClose() {
  const pid=Number(launch.pid);
  const quote=value=>`'${String(value).replaceAll("'", "''")}'`;
  const info=JSON.parse(ps(['-File', join(desktopRoot,'scripts/close-windows-hidden-acceptance.ps1'),
    '-ProcessId',String(pid),'-Executable',runExecutable,'-Sha256',receipt.sha256,
    '-StartedAt',launch.processStartedAt]));
  assert.equal(info.visibleWindows,0);assert.equal(info.closePosted,true);
  await until(()=>ps(['-Command',`@(Get-CimInstance Win32_Process -Filter "ProcessId=${pid}").Count`])==='0','自然关闭退出');
  await until(()=>ps(['-Command',`@(Get-CimInstance Win32_Process -Filter "Name='msedgewebview2.exe'" | Where-Object {$_.CommandLine -and $_.CommandLine.Contains(${quote(launch.webviewProfile)})}).Count`])==='0','本根WebView退出');
  pids.push(pid);launch=null;return info;
}






const requests=[],serverErrors=[];const releases=[];let releaseTitle,releaseSecond;
const manual=process.argv.includes('--manual'),shutdown=process.argv.includes('--shutdown'),timeoutTitle=process.argv.includes('--timeout');let answers=0;
const provider=http.createServer(async(req,res)=>{try{
 let raw='';for await(const chunk of req){raw+=chunk.toString('utf8');assert.ok(raw.length<1024*1024);}const body=JSON.parse(raw);
 assert.ok(['Bearer local-acceptance-mock-only','Bearer nfai-synthetic-title-only'].includes(req.headers.authorization));
 const title=body.model==='qwen3.6-flash';requests.push({model:body.model,title,stream:body.stream,maxTokens:body.max_tokens,idempotencyKey:req.headers['idempotency-key']});assert.ok(requests.length<=3);
 if(title){assert.equal(body.stream,false);assert.equal(body.max_tokens,256);assert.equal(body.tools,undefined);assert.equal(requests.filter(r=>r.title).length,1);
   await new Promise(done=>{releaseTitle=done;releases.push(done);res.on('close',done);});if(res.destroyed)return;
   res.writeHead(200,{'Content-Type':'application/json'});res.end(JSON.stringify({model:body.model,choices:[{message:{content:'{"title":"KFK资料整理方案"}'},finish_reason:'stop'}],usage:{prompt_tokens:12,completion_tokens:4,cost:.000023}}));return;
 }
 assert.equal(body.model,'openai/gpt-6.1-sol');assert.equal(body.stream,true);answers++;res.writeHead(200,{'Content-Type':'text/event-stream; charset=utf-8'});res.write(`data: ${JSON.stringify({model:body.model,choices:[{delta:{content:'Synthetic primary answer '+answers}}]})}\n\n`);
 if(answers===2){await new Promise(done=>{releaseSecond=done;releases.push(done);res.on('close',done);});if(res.destroyed)return;}
 res.end(`data: ${JSON.stringify({model:body.model,choices:[{delta:{},finish_reason:'stop'}],usage:{prompt_tokens:100,completion_tokens:20,cost:.0003}})}\n\ndata: [DONE]\n\n`);
}catch(error){serverErrors.push(String(error));res.destroy(error);}});
await new Promise(done=>provider.listen(0,'127.0.0.1',done));const providerPort=provider.address().port;
const result={candidateSha256:receipt.sha256,manual,shutdown,timeoutTitle,backgroundOnly:true,realService:false};let port,ws,conv;
function titleStates(){return JSON.parse(execFileSync('C:/Users/Administrator/.cache/codex-runtimes/codex-primary-runtime/dependencies/python/python.exe',['-c',"import json,sqlite3,sys;from pathlib import Path;c=sqlite3.connect((Path(sys.argv[1])/'workspace.sqlite3').as_uri()+'?mode=ro',uri=True);print(json.dumps(c.execute('SELECT state,safe_error_code FROM desktop_conversation_title_attempts').fetchall()))",root],{encoding:'utf8',windowsHide:true}));}
async function readConversation(){return (await invoke(port,'CHAT','read_desktop_workspace',{workspaceId:ws})).exchange.conversations.find(c=>c.id===conv);}
async function ledger(){return (await invoke(port,'CHAT','read_desktop_usage_ledger')).records.filter(r=>r.conversationId===conv);}
async function args(text,conversationId=null){const preview=await invoke(port,'CHAT','preview_desktop_ordinary_chat_egress',{args:{workspaceId:ws,conversationId,text,attachmentIds:[],modelId:conversationId?null:'GPT_6_1_SOL',webSearchOverride:conversationId?null:false}});return {workspaceId:ws,conversationId,expectedRevision:conversationId?(await readConversation()).revision:null,text,attachmentIds:[],modelId:conversationId?null:'GPT_6_1_SOL',webSearchOverride:conversationId?null:false,egressAuthorization:{approvedAtMs:Date.now(),disclosureVersion:'normal-chat-egress-v2',clientSubmissionId:randomUUID(),draftFingerprint:preview.draftFingerprint,recipientChoiceId:preview.recipientChoiceId,recipientProviderId:preview.recipientProviderId,recipientModelId:preview.recipientModelId,retrievalRecipient:preview.retrievalRecipient}};}
async function island(){return (await cdp(port,'Runtime.evaluate',{expression:'window.__TAURI__.core.invoke("read_generation_island")',awaitPromise:true,returnByValue:true},'island')).result.value;}
try{
 port=await start();await ready(port);ws=(await invoke(port,'CHAT','ensure_desktop_local_workspace')).summary.id;
 const settings=await invoke(port,'CHAT','read_desktop_model_service_settings');for(const [providerId,presetId] of [['OPENROUTER','GPT_6_1_SOL'],['QWEN','QWEN_3_6_FLASH']]){const setting=settings.find(s=>s.providerId===providerId);await invoke(port,'CHAT','save_desktop_model_service_settings',{args:{providerId,enabled:true,presetId,expectedRevision:setting.revision,apiKey:'nfai-synthetic-title-only'}});}
 await evaluate(port,'(async()=>{window.__titleRuntimeEvents=[];await window.__TAURI__.event.listen("desktop-ordinary-chat-runtime-v1",e=>window.__titleRuntimeEvents.push(e.payload));return true})()');
 const started=Date.now();const first=await invoke(port,'CHAT','submit_desktop_ordinary_chat',{args:await args('请整理KFK公开资料')});result.primaryReturnMs=Date.now()-started;assert.equal(first.state,'COMPLETED');conv=first.conversationId;await until(()=>!!releaseTitle,'held title request');assert.ok(result.primaryReturnMs<10000);assert.equal(requests.length,2);result.first=first;
 if(shutdown){const closeStarted=Date.now();result.shutdownClose=await normalClose();result.shutdownWaitMs=Date.now()-closeStarted;releaseTitle();port=await start(true);await ready(port);const count=requests.length;const current=await readConversation();assert.equal(current.autoTitlePending,true);assert.equal(current.messages.find(m=>m.id===first.assistantMessageId).delivery,'COMPLETE');assert.equal(requests.length,count);result.shutdownTitleStates=titleStates();assert.deepEqual(result.shutdownTitleStates,[['UNKNOWN','TIMEOUT']]);result.shutdownPreservedReply=true;result.finalClose=await normalClose();}
 else {
  const secondArgs=await args('继续整理公开资料',conv);await evaluate(port,`(()=>{window.__pendingSecond=null;window.__TAURI__.core.invoke('submit_desktop_ordinary_chat',{args:${JSON.stringify(secondArgs)}},{headers:{'x-nanfeng-data-area':'CHAT'}}).then(r=>window.__pendingSecond={ok:r}).catch(e=>window.__pendingSecond={error:String(e)});return true})()`);await until(()=>!!releaseSecond,'second answer running');
  const before=await readConversation();result.islandBeforeTitle=await island();const secondTask=result.islandBeforeTitle.find(t=>t.conversationId===conv&&t.state==='RUNNING');assert.ok(secondTask);assert.notEqual(secondTask.attemptId,first.attemptId);
  if(manual){await invoke(port,'CHAT','mutate_desktop_domain',{args:{workspaceId:ws,intentId:randomUUID(),entity:'conversation',action:'update',objectId:conv,expectedRevision:before.revision,fields:{title:'用户手动保留标题'}}});}
  const firstEvents=await evaluate(port,`window.__titleRuntimeEvents.filter(e=>e.attemptId===${JSON.stringify(first.attemptId)}).length`);if(!timeoutTitle)releaseTitle();
  if(timeoutTitle){await until(()=>titleStates().some(r=>r[0]==='UNKNOWN'),'title 15s UNKNOWN');result.titleUnknown=titleStates();}else await until(async()=> (await ledger()).some(r=>r.requestedModelId==='qwen3.6-flash'),'title measured receipt');
  if(!manual&&!timeoutTitle)await until(()=>evaluate(port,`window.__titleRuntimeEvents.filter(e=>e.attemptId===${JSON.stringify(first.attemptId)}).length>${firstEvents}`),'late title metadata event');
  const after=await readConversation();assert.equal(after.title,manual?'用户手动保留标题':timeoutTitle?before.title:'KFK资料整理方案');assert.equal(after.updatedAt,before.updatedAt);result.islandAfterTitle=await island();const still=result.islandAfterTitle.find(t=>t.attemptId===secondTask.attemptId);assert.equal(still.state,'RUNNING');assert.equal(still.assistantMessageId,secondTask.assistantMessageId);result.lateTitleDidNotReplaceNewReply=true;
  releaseSecond();const second=await until(()=>evaluate(port,'window.__pendingSecond'),'second completed');assert.ok(second.ok,JSON.stringify(second));assert.equal(second.ok.state,'COMPLETED');assert.equal(requests.length,3);
  result.ledger=await ledger();const titles=result.ledger.filter(r=>r.requestedModelId==='qwen3.6-flash');assert.equal(titles.length,timeoutTitle?0:1);if(!timeoutTitle){assert.equal(titles[0].currencyCode,'CNY');assert.equal(titles[0].chargeMicros,23);}result.title=after.title;result.close=await normalClose();port=await start(true);await ready(port);assert.deepEqual(await ledger(),result.ledger);assert.equal((await readConversation()).title,result.title);assert.equal(requests.length,3);result.finalClose=await normalClose();
 }
 assert.deepEqual(serverErrors,[]);result.pass=true;
}catch(error){result.error=error.stack||String(error);process.exitCode=1;}
finally{for(const release of releases)release();if(launch)try{result.failureClose=await normalClose();}catch(error){result.closeError=String(error);stop();}provider.closeAllConnections();await new Promise(done=>provider.close(done));result.root=root;result.requests=requests;result.serverErrors=serverErrors;result.pids=pids;if(root)await writeFile(join(root,'title-background-native-evidence.json'),JSON.stringify(result,null,2)+'\n');}
console.log(JSON.stringify(result,null,2));
