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
const rootParent = resolve(process.argv[2] || 'D:\\CodexTemp');
assert.ok(process.execArgv.includes('--max-old-space-size=2048'));
if (process.argv.includes('--screenshot')) throw new Error('后台验收不支持前台截图');
const candidateNamespace=process.argv.includes('--release')?'windows-acceptance-release':'windows-acceptance';
const expectedBuildProfile=process.argv.includes('--release')?'release':'debug';
const receipt = JSON.parse(await readFile(join(desktopRoot, 'output',candidateNamespace,'current.json'), 'utf8'));
assert.equal(receipt.buildProfile,expectedBuildProfile);
assert.equal(receipt.identifier, 'com.nanzhufeng.ai.desktop.windowsacceptance');
const source = resolve(desktopRoot, 'output',candidateNamespace, receipt.sha256, 'NanfengAI-Windows-Acceptance.exe');
assert.equal(resolve(receipt.executable), source);
assert.equal(createHash('sha256').update(await readFile(source)).digest('hex').toUpperCase(), receipt.sha256);
const runExecutable = resolve(desktopRoot, 'output/windows-acceptance-binaries', receipt.sha256, 'nanfeng-ai-desktop-spike.exe');
const ps = (args) => execFileSync('pwsh', ['-NoProfile', ...args], { encoding: 'utf8', timeout: 60000, windowsHide:true }).trim();
const privateDesktopGuard=JSON.parse(ps(['-File',join(desktopRoot,'scripts/assert-windows-private-desktop.ps1')]));assert.equal(privateDesktopGuard.verified,true);
const launchPowerShell = (args) => new Promise((resolveLaunch, reject) => {
  const child = spawn('pwsh', ['-NoProfile', ...args], { windowsHide:true, stdio: ['ignore', 'pipe', 'pipe'] });
  let stdout = '';
  let stderr = '';
  const timer = setTimeout(() => { child.kill(); reject(new Error(`隔离候选启动超时：${stderr}`)); }, 30000);
  child.stdout.on('data', data => {
    stdout += data.toString();
    let emitted;try {emitted=completeLaunchReceipt(stdout);}catch(error){clearTimeout(timer);reject(error);return;}
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

const echoOnly=process.argv.includes('--echo-only');
const partialEcho=process.argv.includes('--partial-echo');if(partialEcho)assert.ok(echoOnly);
const cadenceProbe=process.argv.includes('--cadence-probe');
const traceProbe=process.argv.includes('--trace-probe');
const traceArea=process.argv.includes('--work-trace')?'WORK':'CHAT';if(traceArea==='WORK')assert.ok(traceProbe);
const budgetBound=process.argv.includes('--budget-bound');
const originalMaterial='NATIVE_ORIGINAL_MATERIAL_START'+'m'.repeat(30000)+'NATIVE_ORIGINAL_MATERIAL_END';
const interruptContinuation=process.argv.includes('--interrupt-continuation')||budgetBound;if(interruptContinuation)assert.ok(!echoOnly||partialEcho);
const retryAfterEcho=process.argv.includes('--retry-after-echo');if(retryAfterEcho)assert.ok(echoOnly);
if(budgetBound)assert.ok(!echoOnly&&!retryAfterEcho&&!cadenceProbe&&!traceProbe);
let onEchoStream=null;
const prefixText='这是已经持久保存的原始回答段落，包含足够长的中文断点，续写必须保留这段文字。';
const suffixText='这是本次明确续写新增的内容。';
const requests=[],serverErrors=[];
const provider=http.createServer(async(req,res)=>{try{
 let raw='';for await(const chunk of req)raw+=chunk.toString('utf8');const body=JSON.parse(raw);
 assert.equal(req.headers.authorization,'Bearer local-acceptance-mock-only');assert.equal(body.model,'openai/gpt-6.1-sol');
 requests.push({model:body.model,idempotencyKey:req.headers['idempotency-key'],...(budgetBound?{originalMaterialPresent:JSON.stringify(body.messages).includes(originalMaterial)}:{})});assert.ok(requests.length<=(retryAfterEcho?3:2));
 res.writeHead(200,{'Content-Type':'text/event-stream; charset=utf-8'});
 const event=v=>res.write(`data: ${JSON.stringify(v)}

`);
 if(budgetBound)assert.ok(JSON.stringify(body.messages).includes(originalMaterial));
 if(requests.length===1){event({model:body.model,choices:[{delta:{content:prefixText}}]});res.flushHeaders();setTimeout(()=>res.destroy(),100);return;}
 assert.equal(body.messages.at(-1).content,'上一段回答因连接中断而未完成。请只从上面已生成内容的末尾自然继续，不要重复、改写或概述已有文字；继续完成原问题。');
 assert.ok(body.messages.some(m=>m.role==='assistant'&&m.content===prefixText));
 result.distinctRequestKeys=requests[0].idempotencyKey!==requests[1].idempotencyKey;
 if(budgetBound){event({model:body.model,choices:[{delta:{content:suffixText}}]});res.flushHeaders();setTimeout(()=>res.destroy(),100);return;}
 event({model:body.model,choices:[{delta:{content:partialEcho&&requests.length===2?[...prefixText].slice(0,18).join(''):prefixText}}]});
 if(onEchoStream)await onEchoStream();
 await new Promise(done=>setTimeout(done,100));
 if(!echoOnly||requests.length===3)event({model:body.model,choices:[{delta:{content:suffixText}}]});
 if(interruptContinuation){await new Promise(done=>setTimeout(done,100));res.destroy();return;}
 res.end(`data: ${JSON.stringify({model:body.model,choices:[{delta:{},finish_reason:'stop'}],usage:{prompt_tokens:8,completion_tokens:5,cost:0.000004}})}`);
}catch(error){serverErrors.push(String(error));res.destroy(error);}});
await new Promise(done=>provider.listen(0,'127.0.0.1',done));const providerPort=provider.address().port;
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
    '-ExecutablePath',source,'-StartupMode', 'normal', '-DebugPort', String(port), '-OrdinaryChatTestEndpoint', `http://127.0.0.1:${providerPort}`];
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
async function cdp(port, method, params = {}) {
  const tabs = await fetch(`http://127.0.0.1:${port}/json/list`,{signal:AbortSignal.timeout(10000)}).then(response => response.json());
  const tab = tabs.find(item => item.type === 'page' && item.url === 'http://tauri.localhost/');
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
    try { const value = await check(); if (value) return value; } catch { /* Visible page may be refreshing. */ }
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






const result={privateDesktopGuard,budgetBound,candidateSha256:receipt.sha256,echoOnly,partialEcho,interruptContinuation,realService:false,backgroundOnly:true};let port=0;
const textOf=m=>(m.blocks||[]).filter(b=>b.kind==='TEXT').map(b=>b.text||'').join('');
const authorization=p=>({approvedAtMs:Date.now(),disclosureVersion:'normal-chat-egress-v2',clientSubmissionId:randomUUID(),draftFingerprint:p.draftFingerprint,recipientChoiceId:p.recipientChoiceId,recipientProviderId:p.recipientProviderId,recipientModelId:p.recipientModelId,retrievalRecipient:p.retrievalRecipient});
try{
 port=await start();result.root=root;await ready(port);result.runtimeInfo=await invoke(port,'CHAT','read_desktop_runtime_info');assert.equal(result.runtimeInfo.buildProfile,expectedBuildProfile);
 if(cadenceProbe)result.nativeVisibility=await evaluate(port,'Promise.all([window.__TAURI__.window.getCurrentWindow().isVisible(),window.__TAURI__.window.getCurrentWindow().isMinimized()])');
 if(cadenceProbe)await evaluate(port,`(()=>{
   window.__nfaiCadence=[];const original=window.setTimeout;
   window.setTimeout=function(callback,delay,...args){
     if(!String(new Error().stack).includes('scheduleOrdinaryChatRuntimeRefresh'))return original.call(this,callback,delay,...args);
     const record={delay,hidden:document.hidden,scheduledAt:performance.now()};window.__nfaiCadence.push(record);
     return original.call(this,function(...values){record.firedAt=performance.now();return callback.apply(this,values);},delay,...args);
   };return true;
 })()`);
 const ws=(await invoke(port,traceArea,'ensure_desktop_local_workspace')).summary.id;
 const setting=(await invoke(port,traceArea,'read_desktop_model_service_settings')).find(p=>p.providerId==='OPENROUTER');
 await invoke(port,traceArea,'save_desktop_model_service_settings',{args:{providerId:'OPENROUTER',enabled:true,presetId:'GPT_6_1_SOL',expectedRevision:setting.revision,apiKey:'nfai-synthetic-continuation'}});
 let conv=(await invoke(port,traceArea,'read_desktop_workspace',{workspaceId:ws})).exchange.conversations[0];
 if(!conv){
   const created=await invoke(port,traceArea,'mutate_desktop_domain',{args:{workspaceId:ws,intentId:randomUUID(),entity:'conversation',action:'create',objectId:null,expectedRevision:null,fields:{title:'合成连续性诊断',projectId:null,firstMessage:'合成工作区会话'}}});
   conv=(await invoke(port,traceArea,'read_desktop_workspace',{workspaceId:ws})).exchange.conversations.find(c=>c.id===created.objectId);assert.ok(conv);
 }

 const selection=await invoke(port,traceArea,'read_desktop_p6g_selection',{workspaceId:ws,conversationId:conv.id});
 await invoke(port,traceArea,'set_desktop_p6g_conversation_override',{args:{workspaceId:ws,conversationId:conv.id,expectedRevision:selection.conversationOverride.revision,modelId:'GPT_6_1_SOL'}});
 const current=await invoke(port,traceArea,'read_desktop_p6g_selection',{workspaceId:ws,conversationId:conv.id});
 await invoke(port,traceArea,'set_desktop_conversation_preferences',{args:{workspaceId:ws,conversationId:conv.id,expectedRevision:current.conversationOverride.revision,toneOverride:null,webSearchOverride:false}});
 const attachmentIds=[];if(budgetBound){const path=join(root,'budget-original.txt');await writeFile(path,originalMaterial);const imported=await invoke(port,traceArea,'import_desktop_conversation_attachment',{args:{workspaceId:ws,selectedPath:path}});attachmentIds.push(imported.id);}
 const text='说明这一段合成问题';const preview=await invoke(port,traceArea,'preview_desktop_ordinary_chat_egress',{args:{workspaceId:ws,conversationId:conv.id,text,attachmentIds,modelId:null,webSearchOverride:null}});
 const failed=await invoke(port,traceArea,'submit_desktop_ordinary_chat',{args:{workspaceId:ws,conversationId:conv.id,expectedRevision:conv.revision,text,attachmentIds,modelId:null,egressAuthorization:authorization(preview)}});
 assert.notEqual(failed.state,'COMPLETED');
 let read=(await invoke(port,traceArea,'read_desktop_workspace',{workspaceId:ws})).exchange.conversations.find(c=>c.id===conv.id);assert.equal(textOf(read.messages.find(m=>m.id===failed.assistantMessageId)),prefixText);
 const retryPreview=await invoke(port,traceArea,'preview_desktop_ordinary_chat_retry_egress',{args:{attemptId:failed.attemptId}});
 onEchoStream=async()=>{
   const streamRead=(await invoke(port,traceArea,'read_desktop_workspace',{workspaceId:ws})).exchange.conversations.find(c=>c.id===conv.id);
   const leaf=streamRead.messages.find(m=>m.id===streamRead.currentLeafId);assert.ok(leaf);assert.equal(textOf(leaf),'');result.echoWasNotCheckpointed=true;
 };
 const retry=await invoke(port,traceArea,'retry_desktop_ordinary_chat',{args:{attemptId:failed.attemptId,egressAuthorization:authorization(retryPreview)}});
 assert.equal(retry.state,interruptContinuation?'UNKNOWN':echoOnly?'FAILED':'COMPLETED');if(echoOnly&&!interruptContinuation)assert.equal(retry.safeErrorCode,'RESPONSE_INCOMPLETE');
 read=(await invoke(port,traceArea,'read_desktop_workspace',{workspaceId:ws})).exchange.conversations.find(c=>c.id===conv.id);
 assert.equal(textOf(read.messages.find(m=>m.id===failed.assistantMessageId)),prefixText);
 assert.equal(textOf(read.messages.find(m=>m.id===retry.assistantMessageId)),echoOnly?'':suffixText);
 assert.equal(requests.length,2);assert.equal(result.distinctRequestKeys,true);assert.equal(serverErrors.length,0);result.originalPreserved=true;result.onlyNewText=true;result.retry={attemptId:retry.attemptId,state:retry.state,safeErrorCode:retry.safeErrorCode};
 if(budgetBound){
   const catalog=await invoke(port,traceArea,'read_desktop_p6g_catalog');
   await invoke(port,traceArea,'upsert_desktop_p6g_catalog_candidate',{args:{expectedRevision:catalog.revision,catalogVersion:'native-continuation-budget',policyVersion:1,candidate:{providerFamily:'OPENAI',providerId:'OPENROUTER',modelId:'GPT_6_1_SOL',displayName:'Sol',tiers:['DEEP'],capabilities:['TEXT'],available:true,knownCostMicros:null,latencyRank:1,contextWindowTokens:8192,maxOutputTokens:4096}}});
   const updated=await invoke(port,traceArea,'read_desktop_p6g_catalog');assert.equal(updated.snapshot.candidates.find(c=>c.modelId==='GPT_6_1_SOL').contextWindowTokens,8192);
   const before=(await invoke(port,traceArea,'read_desktop_workspace',{workspaceId:ws})).exchange.conversations.find(c=>c.id===conv.id);
   const beforeLedger=await invoke(port,traceArea,'read_desktop_usage_ledger');let rejected=false;
   try{const p=await invoke(port,traceArea,'preview_desktop_ordinary_chat_retry_egress',{args:{attemptId:retry.attemptId}});await invoke(port,traceArea,'retry_desktop_ordinary_chat',{args:{attemptId:retry.attemptId,egressAuthorization:authorization(p)}});}catch(error){assert.match(String(error),/本次问题与附件超过当前模型上下文窗口/);result.budgetError=String(error);rejected=true;}
   assert.ok(rejected,'budget must reject without sending a payload missing the original material');assert.equal(requests.length,2);
   const after=(await invoke(port,traceArea,'read_desktop_workspace',{workspaceId:ws})).exchange.conversations.find(c=>c.id===conv.id);assert.deepEqual(after,before);assert.deepEqual(await invoke(port,traceArea,'read_desktop_usage_ledger'),beforeLedger);
   const assetHash=createHash('sha256').update(originalMaterial).digest('hex');assert.equal(await readFile(join(root,'assets',assetHash),'utf8'),originalMaterial);
   result.budgetRejected=true;result.noThirdRequest=true;result.attemptConversationUnchanged=true;result.ledgerUnchanged=true;result.originalMaterialPreserved=true;
   await until(()=>evaluate(port,`!!document.querySelector('[data-action="select-chat"][data-id="${conv.id}"]')`),'budget conversation navigation');
   await clickUi(port,`[data-action="select-chat"][data-id="${conv.id}"]`);
   await until(()=>evaluate(port,`window.__nanfengDesktopWorkState?.selectedConversationId===${JSON.stringify(conv.id)}`),'budget conversation selected');
   const retrySelector=`article[data-message-id="${retry.assistantMessageId}"] [data-action="retry-ordinary-chat"]`;
   await until(()=>evaluate(port,`!!document.querySelector(${JSON.stringify(retrySelector)})`),'actual retry button');await clickUi(port,retrySelector);
   await until(()=>evaluate(port,"document.body.textContent.includes('本次问题与附件超过当前模型上下文窗口')"),'budget rejection visible in actual UI');
   await until(()=>evaluate(port,'window.__nanfengDesktopWorkState?.ordinarySendPreparing===false'),'retry preparation released');
   assert.equal(requests.length,2);assert.deepEqual(await invoke(port,traceArea,'read_desktop_usage_ledger'),beforeLedger);result.actualRetryButtonRejected=true;

   result.close=await normalClose();port=await start(true);await ready(port);
   const cold=(await invoke(port,traceArea,'read_desktop_workspace',{workspaceId:ws})).exchange.conversations.find(c=>c.id===conv.id);assert.deepEqual(cold.messages,before.messages);assert.equal(cold.currentLeafId,before.currentLeafId);assert.deepEqual(await invoke(port,traceArea,'read_desktop_usage_ledger'),beforeLedger);assert.equal(requests.length,2);
   result.coldBudgetStatePreserved=true;result.coldClose=await normalClose();result.pass=true;
 }else{
 if(cadenceProbe){
   await until(()=>evaluate(port,'window.__nfaiCadence?.some(r=>r.firedAt)'), '后台真实刷新计时器');
   result.cadence=await evaluate(port,'window.__nfaiCadence');assert.ok(result.cadence.length>0);
   assert.ok(result.cadence.every(r=>r.delay===((r.hidden||result.nativeVisibility[0]===false||result.nativeVisibility[1]===true)?1000:80)));assert.ok(result.cadence.some(r=>r.firedAt-r.scheduledAt>=r.delay-20));
 }
 if(traceProbe){
   const path=join(root,...(traceArea==='WORK'?['work-area']:[]),'generation-continuity-v1.json');assert.ok((await stat(path)).size<=32768);
   const raw=await readFile(path,'utf8');const events=JSON.parse(raw);assert.ok(events.length>0&&events.length<=64);
   assert.ok(events.every(e=>JSON.stringify(Object.keys(e).sort())===JSON.stringify(['age_ms','at_ms','stage','task'])&&/^[a-f0-9]{12}$/.test(e.task)&&/^[A-Z0-9_]{1,80}$/.test(e.stage)));
   assert.ok(events.some(e=>e.stage==='NETWORK'));assert.ok(events.some(e=>e.stage==='TRANSPORT_COMPLETED'));
   assert.ok(!raw.includes(prefixText)&&!raw.includes(suffixText)&&!raw.includes('nfai-synthetic-continuation'));
   result.trace={area:traceArea,count:events.length,stages:events.map(e=>e.stage),contentFree:true};
   if(traceArea==='WORK')assert.equal(await stat(join(root,'generation-continuity-v1.json')).then(()=>false,error=>error.code==='ENOENT'),true);
 }
 result.close=await normalClose();port=await start(true);await ready(port);
 const cold=(await invoke(port,traceArea,'read_desktop_workspace',{workspaceId:ws})).exchange.conversations.find(c=>c.id===conv.id);
 assert.equal(textOf(cold.messages.find(m=>m.id===retry.assistantMessageId)),echoOnly?'':suffixText);assert.equal(requests.length,2);
 if(retryAfterEcho){
   const p=await invoke(port,traceArea,'preview_desktop_ordinary_chat_retry_egress',{args:{attemptId:retry.attemptId}});
   const third=await invoke(port,traceArea,'retry_desktop_ordinary_chat',{args:{attemptId:retry.attemptId,egressAuthorization:authorization(p)}});
   assert.equal(third.state,'COMPLETED');
   const finalRead=(await invoke(port,traceArea,'read_desktop_workspace',{workspaceId:ws})).exchange.conversations.find(c=>c.id===conv.id);
   assert.equal(textOf(finalRead.messages.find(m=>m.id===third.assistantMessageId)),suffixText);
   assert.equal(textOf(finalRead.messages.find(m=>m.id===failed.assistantMessageId)),prefixText);
   assert.equal(new Set(requests.map(r=>r.idempotencyKey)).size,3);result.retryAfterEchoCompleted=true;
 }
 const ledger=(await invoke(port,traceArea,'read_desktop_usage_ledger')).records.filter(r=>r.executionId===retry.attemptId&&r.kind==='FINAL_MEASURED');
 assert.equal(ledger.length,interruptContinuation?0:retryAfterEcho?2:1);assert.equal(new Set(ledger.map(r=>r.invocationId)).size,ledger.length);
 assert.ok(ledger.every(r=>r.chargeMicros===4));result.finalMeasured=ledger.length;
 result.coldClose=await normalClose();result.pass=true;
 }
}catch(error){result.pass=false;result.error=String(error);process.exitCode=1;}
finally{if(launch){try{result.failureClose=await normalClose();}catch(error){result.closeError=String(error);try{stop();result.forcedCleanup=true;}catch(error){result.cleanupError=String(error);}}}provider.closeAllConnections();await new Promise(done=>provider.close(done));result.requests=requests;result.serverErrors=serverErrors;result.pids=pids;if(root)await writeFile(join(root,'a6-continuation-native-evidence.json'),JSON.stringify(result,null,2),{flag:'wx'});}
console.log(JSON.stringify({root,pass:result.pass,error:result.error,requests:requests.length,pids}));
