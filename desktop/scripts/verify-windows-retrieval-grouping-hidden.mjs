import assert from 'node:assert/strict';
import { createHash, randomUUID } from 'node:crypto';
import { execFileSync, spawn } from 'node:child_process';
import { readFile, writeFile, stat } from 'node:fs/promises';
import http from 'node:http';
import { dirname, join, resolve, sep } from 'node:path';
import { fileURLToPath } from 'node:url';

if (process.platform !== 'win32') throw new Error('仅在 Windows 隔离候选上验收。');
const desktopRoot = resolve(dirname(fileURLToPath(import.meta.url)), '..');
assert.ok(process.argv[2], 'Explicit synthetic root parent required');
const rootParent = resolve(process.argv[2]);
assert.ok(process.execArgv.includes('--max-old-space-size=2048'));
if (process.argv.includes('--screenshot')) throw new Error('后台验收不支持前台截图');
const receipt = JSON.parse(await readFile(join(desktopRoot, 'output/windows-acceptance-release/current.json'), 'utf8'));
assert.equal(receipt.identifier, 'com.nanzhufeng.ai.desktop.windowsacceptance');
assert.equal(receipt.buildProfile,'release');
const inputIndex=process.argv.indexOf('--input-manifest');
const inputPath=inputIndex<0 ? join(desktopRoot,'../docs/review/20261018-windows-retrieval-physical-count/source-input-manifest-release-v1.json') : resolve(process.argv[inputIndex+1]);
assert.ok(inputPath.startsWith(resolve(desktopRoot,'../docs/review')+sep));

const inputBytes=await readFile(inputPath);
const sourceInputSha256=createHash('sha256').update(inputBytes).digest('hex');
const source = resolve(desktopRoot, 'output/windows-acceptance-release', receipt.sha256, 'NanfengAI-Windows-Acceptance.exe');
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
    const line = stdout.split(/\r?\n/).find(item => item.trim().startsWith('{') && item.includes('"pid"'));
    if (!line) return;
    clearTimeout(timer);
    try { const emitted=JSON.parse(line); readFile(emitted.launchReceipt, 'utf8').then(data=>resolveLaunch(JSON.parse(data)),reject); } catch (error) { reject(error); }
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
    '-ExecutablePath',source,'-StartupMode', 'normal', '-DebugPort', String(port),'-OrdinaryChatTestEndpoint',`http://127.0.0.1:${providerPort}`];
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


const singleQuestion=process.argv.includes('--single-question');
const questionCount=singleQuestion?1:2;
const requests=[],providerErrors=[];
const wireModel='openai/gpt-6.1-sol';
const provider=http.createServer(async(req,res)=>{try{
 let raw='';for await(const bytes of req)raw+=bytes.toString('utf8');const body=JSON.parse(raw);
 assert.equal(req.headers.authorization,'Bearer local-acceptance-mock-only');assert.equal(body.model,wireModel);
 const retrieval=Array.isArray(body.tools);
 const ids=retrieval?[...new Set([...JSON.stringify(body.messages).matchAll(/\[(A[1-9][0-9]*)\] /g)].map(m=>m[1]))]:[];
 if(retrieval){assert.ok(body.tools.some(t=>t.type==='openrouter:web_search'));assert.equal(ids.length,4);}
 else assert.ok(JSON.stringify(body.messages).includes('<retrieved-evidence>'));
 requests.push({role:retrieval?'RETRIEVAL':'ANSWER',model:body.model,facts:ids,idempotencyKey:req.headers['idempotency-key']});
 const text=retrieval?ids.map(id=>`[${id}] 已核实\n公开机构在2026年发布该项数据。\n来源：https://example.test/${id}`).join('\n'):'合成多批检索回答';
 res.writeHead(200,{'Content-Type':'text/event-stream; charset=utf-8'});
 res.write('data: '+JSON.stringify({model:body.model,choices:[{delta:{content:text}}]})+'\n\n');
 res.end('data: '+JSON.stringify({model:body.model,choices:[{delta:retrieval?{annotations:ids.map(id=>({type:'url_citation',url_citation:{url:`https://example.test/${id}`,title:'Synthetic evidence'}}))}:{},finish_reason:'stop'}],usage:{prompt_tokens:5,completion_tokens:2,cost:0.000004}})+'\n\n');
}catch(error){providerErrors.push(String(error));res.destroy(error);}});
await new Promise(done=>provider.listen(0,'127.0.0.1',done));const providerPort=provider.address().port;
const result={candidateSha256:receipt.sha256,realService:false,backgroundOnly:true,questionCount};let port=0;
async function clickUi(selector){
 const value=await evaluate(port,`(()=>{const n=[...document.querySelectorAll(${JSON.stringify(selector)})].filter(n=>n.getClientRects().length&&!n.disabled);if(n.length!==1)return n.length;n[0].click();return 1})()`);
 assert.equal(value,1,`Unique enabled UI entry: ${selector}`);
}
async function billingUi(expectedRows){
 await until(()=>evaluate(port,'Boolean(document.querySelector(\'[data-action="show-settings"]\'))'),'settings entry');
 await clickUi('[data-action="show-settings"]');
 await until(()=>evaluate(port,'Boolean(document.querySelector(\'[data-action="open-settings-page"][data-page="model"]\'))'),'model settings entry');
 await clickUi('[data-action="open-settings-page"][data-page="model"]');
 await until(()=>evaluate(port,'Boolean(document.querySelector(\'[data-page="conversation-cost"]\'))'),'cost settings entry');
 await clickUi('[data-page="conversation-cost"]');
 return await until(()=>evaluate(port,`(()=>{const el=document.querySelector('.android-settings-ledger');if(!el||el.querySelectorAll('.android-settings-ledger-row').length!==${expectedRows})return null;return {rows:[...el.querySelectorAll('.android-settings-ledger-row')].map(n=>({text:n.textContent,amount:n.querySelector('b')?.textContent})),summary:[...el.querySelectorAll('.android-settings-ledger-summary > div')].map(n=>({label:n.querySelector('span')?.textContent,value:n.querySelector('b')?.textContent})),categories:[...el.querySelectorAll('.android-settings-ledger-grid > div')].map(n=>({label:n.querySelector('strong')?.textContent,values:[...n.querySelectorAll('b')].map(n=>n.textContent)}))}})()`),'rendered grouped ledger');
}
function assertUi(ui){
 const count=questionCount;
 assert.equal(ui.rows.filter(r=>r.text.includes('2 次检索')).length,count);
 assert.equal(ui.rows.filter(r=>r.text.includes('主回答')).length,count);
 assert.equal(ui.rows.filter(r=>r.amount==='约 ¥0.000054').length,count);
 assert.equal(ui.rows.filter(r=>r.amount==='约 ¥0.000027').length,count);
 assert.equal(ui.summary.find(r=>r.label==='输入 Token')?.value,String(count*15));
 assert.equal(ui.summary.find(r=>r.label==='输出 Token')?.value,String(count*6));
 assert.equal(ui.summary.find(r=>r.label==='费用')?.value,count===1?'约 ¥0.000081':'约 ¥0.000162');
 assert.equal(ui.categories.find(r=>r.label==='会话')?.values[0],`${count*3} 次`,'grouped display rows must not reduce physical call count');
}
try{
 port=await start();result.root=root;await ready(port);
 result.runtimeInfo=await invoke(port,'CHAT','read_desktop_runtime_info');assert.equal(result.runtimeInfo.sourceInputSha256,sourceInputSha256);
 const ws=(await invoke(port,'CHAT','ensure_desktop_local_workspace')).summary.id;
 const setting=(await invoke(port,'CHAT','read_desktop_model_service_settings')).find(s=>s.providerId==='OPENROUTER');
 await invoke(port,'CHAT','save_desktop_model_service_settings',{args:{providerId:'OPENROUTER',enabled:true,presetId:'GPT_6_1_SOL',expectedRevision:setting.revision,apiKey:'nfai-synthetic-retrieval-count'}});
 const bytes=Buffer.from(Array.from({length:8},(_,i)=>`- 公开事项${i+1}：机构在2026年发布第${i+1}项数据。`).join('\n'));
 const attachment=await invoke(port,'CHAT','import_desktop_conversation_clipboard_attachment',{args:{workspaceId:ws,displayName:'synthetic-eight-facts.txt',bytesBase64:bytes.toString('base64')}});
 const initial=await invoke(port,'CHAT','read_desktop_workspace',{workspaceId:ws});const conversationId=initial.exchange.conversations[0].id;
 result.conversationId=conversationId;result.attempts=[];
 let selection=await invoke(port,'CHAT','read_desktop_p6g_selection',{workspaceId:ws,conversationId});
 await invoke(port,'CHAT','set_desktop_p6g_conversation_override',{args:{workspaceId:ws,conversationId,expectedRevision:selection.conversationOverride.revision,modelId:'GPT_6_1_SOL'}});
 selection=await invoke(port,'CHAT','read_desktop_p6g_selection',{workspaceId:ws,conversationId});
 await invoke(port,'CHAT','set_desktop_conversation_preferences',{args:{workspaceId:ws,conversationId,expectedRevision:selection.conversationOverride.revision,toneOverride:null,webSearchOverride:true}});
 for(let i=0;i<questionCount;i++){
  const conversation=(await invoke(port,'CHAT','read_desktop_workspace',{workspaceId:ws})).exchange.conversations.find(c=>c.id===conversationId);
  const text=`第${i+1}次核验附件中的全部八个公开事项`;
  const preview=await invoke(port,'CHAT','preview_desktop_ordinary_chat_egress',{args:{workspaceId:ws,conversationId,text,attachmentIds:[attachment.id],modelId:null,webSearchOverride:null}});
  const auth={approvedAtMs:Date.now(),disclosureVersion:'normal-chat-egress-v2',clientSubmissionId:randomUUID(),draftFingerprint:preview.draftFingerprint,recipientChoiceId:preview.recipientChoiceId,recipientProviderId:preview.recipientProviderId,recipientModelId:preview.recipientModelId,retrievalRecipient:preview.retrievalRecipient};
  const before=requests.length;
  const attempt=await invoke(port,'CHAT','submit_desktop_ordinary_chat',{args:{workspaceId:ws,conversationId,expectedRevision:conversation.revision,text,attachmentIds:[attachment.id],modelId:null,egressAuthorization:auth}});
  assert.equal(attempt.state,'COMPLETED');const calls=requests.slice(before);
  assert.equal(calls.length,3);assert.equal(calls.filter(r=>r.role==='RETRIEVAL').length,2);assert.equal(calls.filter(r=>r.role==='ANSWER').length,1);
  assert.deepEqual(calls.filter(r=>r.role==='RETRIEVAL').map(r=>r.facts.length),[4,4]);
  result.attempts.push(attempt);
 }
 assert.deepEqual(providerErrors,[]);
 result.ledger=await invoke(port,'CHAT','read_desktop_usage_ledger');
 const ids=new Set(result.attempts.map(a=>a.attemptId));const records=result.ledger.records;
 assert.equal(records.length,questionCount*3);assert.ok(records.every(r=>ids.has(r.executionId)&&r.kind==='FINAL_MEASURED'));
 assert.equal(new Set(records.map(r=>r.invocationId)).size,questionCount*3);
 assert.ok(records.every(r=>r.chargeMicros===4&&r.currencyCode==='USD'&&r.inputTokens===5&&r.outputTokens===2));
 result.beforeCold=await invoke(port,'CHAT','read_desktop_workspace',{workspaceId:ws});
 result.billingUi=await billingUi(questionCount*2);assertUi(result.billingUi);
 result.close=await normalClose();const requestCount=requests.length;
 port=await start(true);await ready(port);
 result.coldLedger=await invoke(port,'CHAT','read_desktop_usage_ledger');assert.deepEqual(result.coldLedger,result.ledger);
 result.coldWorkspace=await invoke(port,'CHAT','read_desktop_workspace',{workspaceId:ws});assert.deepEqual(result.coldWorkspace.exchange,result.beforeCold.exchange);
 result.coldBillingUi=await billingUi(questionCount*2);assertUi(result.coldBillingUi);assert.deepEqual(result.coldBillingUi,result.billingUi);
 assert.equal(requests.length,requestCount);result.coldClose=await normalClose();result.pass=true;
}catch(error){result.pass=false;result.error=error.stack||String(error);process.exitCode=1;}
finally{if(launch){try{result.failureClose=await normalClose();}catch(error){result.forcedCleanup=true;result.closeError=String(error);stop();}}provider.closeAllConnections();await new Promise(done=>provider.close(done));result.requests=requests;result.providerErrors=providerErrors;result.pids=pids;if(root)await writeFile(join(root,'retrieval-grouping-native-evidence.json'),JSON.stringify(result,null,2),{flag:'wx'});}
console.log(JSON.stringify({root,pass:result.pass,error:result.error,requests:requests.length,pids}));
