import { completeLaunchReceipt } from './windows-launch-receipt.mjs';
import assert from 'node:assert/strict';
import { createHash, randomUUID } from 'node:crypto';
import { execFileSync, spawn } from 'node:child_process';
import { readFile, writeFile } from 'node:fs/promises';
import http from 'node:http';
import { dirname, join, resolve, sep } from 'node:path';
import { fileURLToPath } from 'node:url';

if (process.platform !== 'win32') throw new Error('仅在 Windows 隔离候选上验收。');
const desktopRoot = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const python=String.raw`C:\Users\Administrator\.cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe`;

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
const ps = (args) => execFileSync('pwsh', ['-NoProfile', ...args], { windowsHide:true,encoding: 'utf8', timeout: 60000 }).trim();
const launchPowerShell = (args) => new Promise((resolveLaunch, reject) => {
  const child = spawn('pwsh', ['-NoProfile', ...args], { windowsHide:true, stdio: ['ignore', 'pipe', 'pipe'] });
  let stdout = '';
  let stderr = '';
  const timer = setTimeout(() => { child.kill(); reject(new Error(`隔离候选启动超时：${stderr}`)); }, 30000);
  child.stdout.on('data', data => {
    stdout += data.toString();
    let emitted;
    try {emitted=completeLaunchReceipt(stdout);} catch(error){clearTimeout(timer);reject(error);return;}
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

const mode=process.argv[3];
assert.ok(['scan-empty','scan-truncated','scan-wrong-model','scan-cancel','scan-oversized'].includes(mode),'Explicit owned test mode required');
const fixtureName=mode==='encrypted-failure'?'encrypted-scanned-material-v1.pdf':'scanned-material-v1.pdf';
const fixture=await readFile(join(desktopRoot,'tests/fixtures',fixtureName));
const wireModel='openai/gpt-6.1-sol';
const requests=[], serverErrors=[];
let inventorySocketClosed=false;
const provider=http.createServer(async(req,res)=>{
 try {
  let raw=''; for await(const part of req)raw+=part.toString('utf8'); const body=JSON.parse(raw);
  assert.equal(req.headers.authorization,'Bearer local-acceptance-mock-only');
  assert.equal(body.model,wireModel);
  const serialized=JSON.stringify(body.messages);
  const inventory=serialized.includes('忠实读取本次每一份材料');
  const retrieval=Array.isArray(body.tools);
  const entry={model:body.model,role:inventory?'INVENTORY':retrieval?'RETRIEVAL':'ANSWER'};requests.push(entry);
  if(inventory){
   assert.ok(serialized.includes(fixture.toString('base64')),'inventory must retain original PDF bytes');
   assert.ok(!serialized.includes('本机 PDF 文本层'),'scan/locked PDF must not fabricate local text');
   assert.ok(!serialized.includes('TXT_TAIL_SENTINEL'),'local TXT must not be unnecessarily sent for media reading');
   if(mode==='scan-cancel'){
    res.writeHead(200,{'Content-Type':'text/event-stream; charset=utf-8'});res.flushHeaders();
    res.on('close',()=>{inventorySocketClosed=true;});return;
   }
  }
  res.writeHead(200,{'Content-Type':'text/event-stream; charset=utf-8'});
  const event=value=>res.write(`data: ${JSON.stringify(value)}\n\n`);
  let content;
  if(inventory)content=mode==='scan-empty'?'':mode==='scan-oversized'?'OVERSIZED_READ_SENTINEL'.repeat(1700):'- WRONG_MODEL_SENTINEL：公开机构在2026年发布第一份公告。';
  else{
   assert.ok(['scan-wrong-model','scan-oversized'].includes(mode),'empty/truncated/cancel must not make a follow-up request');
   assert.equal(entry.role,'ANSWER','mismatched inventory must not enter retrieval');
   assert.ok(serialized.includes('<retrieved-evidence>'));
   assert.ok(serialized.includes('0 / 2'));
   assert.ok(serialized.includes('附件读取结果不完整'));
   assert.ok(serialized.includes(fixture.toString('base64')));
   assert.ok(serialized.includes('TXT_TAIL_SENTINEL'));
   assert.ok(!serialized.includes('WRONG_MODEL_SENTINEL'),'untrusted read must not enter the answer');
   assert.ok(!serialized.includes('OVERSIZED_READ_SENTINEL'),'oversized read must not enter the answer');
   content='合成限定回答：附件读取结果不完整，本轮未完成核查。';
  }
  const responseModel=inventory&&mode==='scan-wrong-model'?'wrong-provider-unexpected-model-v1':body.model;
  event({model:responseModel,choices:[{delta:{content}}]});
  const annotations=entry.facts?.map(id=>({type:'url_citation',url_citation:{url:`https://example.test/${id}`,title:'Synthetic evidence'}}));
  res.end(`data: ${JSON.stringify({model:responseModel,choices:[{delta:annotations?{annotations}:{},finish_reason:mode==='scan-truncated'?'length':'stop'}],usage:{prompt_tokens:5,completion_tokens:2,cost:0.000004}})}`);
 }catch(error){serverErrors.push(String(error));res.destroy(error);}
});
await new Promise(done=>provider.listen(0,'127.0.0.1',done));
const providerPort=provider.address().port;
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
    + '$owned=Get-Process -Id ' + pid + '; if ([Math]::Abs(($owned.StartTime.ToUniversalTime()-$started.ToUniversalTime()).TotalMilliseconds) -gt 20) {throw "PID start time mismatch"}; Stop-Process -Id ' + pid + ' -Force -ErrorAction Stop }';
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






const result={mode,candidateSha256:receipt.sha256,fixtureName,fixtureSha256:createHash('sha256').update(fixture).digest('hex'),realService:false,backgroundOnly:true};
let port=0;
try{
 port=await start();result.root=root;await ready(port);
 result.runtimeInfo=await invoke(port,'CHAT','read_desktop_runtime_info');
 const ws=(await invoke(port,'CHAT','ensure_desktop_local_workspace')).summary.id;
 const settings=await invoke(port,'CHAT','read_desktop_model_service_settings');
 for(const providerId of ['OPENROUTER','QWEN']){
  const s=settings.find(s=>s.providerId===providerId);
  await invoke(port,'CHAT','save_desktop_model_service_settings',{args:{providerId,enabled:true,presetId:providerId==='QWEN'?'QWEN_3_7_PLUS':'GPT_6_1_SOL',expectedRevision:s.revision,apiKey:'nfai-synthetic-pdf-matrix-only'}});
 }
 const importBytes=async(name,bytes)=>(await invoke(port,'CHAT','import_desktop_conversation_clipboard_attachment',{args:{workspaceId:ws,displayName:name,bytesBase64:bytes.toString('base64')}})).id;
 const pdfId=await importBytes(fixtureName,fixture);
 const txtId=await importBytes('mixed-control.txt',Buffer.from('- 本机事项1：第一份合成公告。\n- 本机事项2：TXT_TAIL_SENTINEL。'));
 result.assetIds=[pdfId,txtId];
 if(mode==='text-reject'){
  result.rejections=[];
  const locked=await readFile(join(desktopRoot,'tests/fixtures/encrypted-scanned-material-v1.pdf'));
  const lockedId=await importBytes('encrypted-scanned-material-v1.pdf',locked);
  result.assetIds.push(lockedId);
  for(const [name,id] of [['scanned',pdfId],['encrypted',lockedId]]){
   const before=await invoke(port,'CHAT','read_desktop_usage_ledger');
   let failure='';try{await invoke(port,'CHAT','preview_desktop_ordinary_chat_egress',{args:{workspaceId:ws,conversationId:null,text:'分析完整材料',attachmentIds:[id,txtId],modelId:'QWEN_3_7_PLUS',webSearchOverride:true}});}catch(error){failure=String(error);}
   assert.match(failure,/不能处理本次附件/);
   assert.equal(requests.length,0);assert.deepEqual(await invoke(port,'CHAT','read_desktop_usage_ledger'),before);
   result.rejections.push({name,explicitRejection:true,networkRequests:0,ledgerUnchanged:true});
  }
 }else{
  const data=await invoke(port,'CHAT','read_desktop_workspace',{workspaceId:ws});const conv=data.exchange.conversations[0];
  let selection=await invoke(port,'CHAT','read_desktop_p6g_selection',{workspaceId:ws,conversationId:conv.id});
  await invoke(port,'CHAT','set_desktop_p6g_conversation_override',{args:{workspaceId:ws,conversationId:conv.id,expectedRevision:selection.conversationOverride.revision,modelId:'GPT_6_1_SOL'}});
  selection=await invoke(port,'CHAT','read_desktop_p6g_selection',{workspaceId:ws,conversationId:conv.id});
  await invoke(port,'CHAT','set_desktop_conversation_preferences',{args:{workspaceId:ws,conversationId:conv.id,expectedRevision:selection.conversationOverride.revision,toneOverride:null,webSearchOverride:true}});
  const text='核验本次扫描文件和文字材料的全部事项';
  const ids=[pdfId,txtId];
  const preview=await invoke(port,'CHAT','preview_desktop_ordinary_chat_egress',{args:{workspaceId:ws,conversationId:conv.id,text,attachmentIds:ids,modelId:null,webSearchOverride:null}});
  assert.equal(preview.recipientProviderId,'OPENROUTER');assert.equal(preview.recipientModelId,wireModel);assert.equal(preview.retrievalRecipient,null);
  const auth={approvedAtMs:Date.now(),disclosureVersion:'normal-chat-egress-v2',clientSubmissionId:randomUUID(),draftFingerprint:preview.draftFingerprint,recipientChoiceId:preview.recipientChoiceId,recipientProviderId:preview.recipientProviderId,recipientModelId:preview.recipientModelId,retrievalRecipient:preview.retrievalRecipient};
  const submission=invoke(port,'CHAT','submit_desktop_ordinary_chat',{args:{workspaceId:ws,conversationId:conv.id,expectedRevision:conv.revision,text,attachmentIds:ids,modelId:null,egressAuthorization:auth}});
  if(mode==='scan-cancel'){
   await until(()=>requests.length===1,'inventory request start');
   result.cancelAccepted=await invoke(port,'CHAT','cancel_desktop_ordinary_chat',{args:{conversationId:conv.id}});
   assert.equal(result.cancelAccepted,true);
  }
  const attempt=await submission;
  result.attempt={state:attempt.state,safeErrorCode:attempt.safeErrorCode,attemptId:attempt.attemptId};
  assert.equal(serverErrors.length,0);

  const ledger=(await invoke(port,'CHAT','read_desktop_usage_ledger')).records.filter(r=>r.executionId===attempt.attemptId);
  result.ledger=ledger;
  if(['scan-wrong-model','scan-oversized'].includes(mode)){
   assert.equal(attempt.state,'COMPLETED');
   assert.deepEqual(requests.map(r=>r.role),['INVENTORY','ANSWER']);
   const measured=ledger.filter(r=>r.kind==='FINAL_MEASURED');assert.equal(measured.length,2);
   const read=measured.find(r=>r.callMetadata?.role==='RETRIEVAL');
   const answer=measured.find(r=>r.callMetadata?.role==='ANSWER');
   assert.equal(read.requestedModelId,wireModel);assert.equal(read.actualModelId,mode==='scan-wrong-model'?'wrong-provider-unexpected-model-v1':wireModel);
   assert.equal(answer.requestedModelId,wireModel);assert.equal(answer.actualModelId,wireModel);
   result.incompleteOriginalModelAnswer=true;
  }else{
   assert.notEqual(attempt.state,'COMPLETED');assert.deepEqual(requests.map(r=>r.role),['INVENTORY']);
   assert.equal(ledger.filter(r=>r.callMetadata?.role==='ANSWER').length,0);
   if(mode==='scan-cancel'){
    assert.equal(attempt.state,'CANCELLED');await until(()=>inventorySocketClosed,'cancel closes read socket');result.inventorySocketClosed=inventorySocketClosed;
   }else{
    assert.equal(attempt.state,'FAILED');
    assert.equal(attempt.safeErrorCode,mode==='scan-empty'?'RESPONSE_FORMAT':'OUTPUT_LIMIT');
    const measured=ledger.filter(r=>r.kind==='FINAL_MEASURED');assert.equal(measured.length,1);
    assert.equal(measured[0].requestedModelId,wireModel);assert.equal(measured[0].actualModelId,wireModel);
    assert.equal(measured[0].inputTokens,5);assert.equal(measured[0].outputTokens,2);
   }
  }

 }
 result.beforeColdLedger=await invoke(port,'CHAT','read_desktop_usage_ledger');
 result.beforeColdWorkspace=await invoke(port,'CHAT','read_desktop_workspace',{workspaceId:ws});
 result.close=await normalClose();const count=requests.length;
 port=await start(true);await ready(port);
 assert.deepEqual(await invoke(port,'CHAT','read_desktop_usage_ledger'),result.beforeColdLedger);
 assert.deepEqual(await invoke(port,'CHAT','read_desktop_workspace',{workspaceId:ws}),result.beforeColdWorkspace);
 assert.equal(requests.length,count,'cold start must not retry');result.coldNoRetry=true;
 result.coldClose=await normalClose();result.pass=true;
}catch(error){result.pass=false;result.error=String(error);process.exitCode=1;}
finally{
 if(launch)try{result.failureClose=await normalClose();}catch(error){result.closeError=String(error);result.forcedCleanup=true;stop();}
 provider.closeAllConnections();await new Promise(done=>provider.close(done));
 result.requests=requests;result.serverErrors=serverErrors;result.pids=pids;
 if(root)await writeFile(join(root,'pdf-read-quality-evidence.json'),JSON.stringify(result,null,2),{flag:'wx'});
}
console.log(JSON.stringify({root,mode,pass:result.pass,error:result.error,requests:requests.length,serverErrors,pids}));
