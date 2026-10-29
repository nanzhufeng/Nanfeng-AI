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
const namespace=process.argv.includes('--release')?'windows-acceptance-release':'windows-acceptance';
const cacheUsage=process.argv.includes('--cache-usage');
const relayToolProof=process.argv.includes('--relay-tool-proof');
const relayGpt=process.argv.includes('--relay-gpt')||relayToolProof;
assert.ok(!(relayGpt&&cacheUsage),'Use one explicit native case');
const qwenMax=process.argv.includes('--qwen-max')||cacheUsage;
const providerId=relayGpt?'RELAYCORE_GPT':qwenMax?'QWEN':'OPENROUTER',presetId=relayGpt?'RELAYCORE_GPT_6_1_SOL':qwenMax?'QWEN_3_8_MAX':'CLAUDE_HAIKU_5_5',wireModel=relayGpt?'gpt-6.1-sol':qwenMax?'qwen3.8-max':'anthropic/claude-haiku-5.5';
const receipt = JSON.parse(await readFile(join(desktopRoot, 'output',namespace,'current.json'), 'utf8'));
assert.equal(receipt.buildProfile,process.argv.includes('--release')?'release':'debug');
assert.equal(receipt.identifier, 'com.nanzhufeng.ai.desktop.windowsacceptance');
const source = resolve(desktopRoot, 'output',namespace, receipt.sha256, 'NanfengAI-Windows-Acceptance.exe');
assert.equal(resolve(receipt.executable), source);
assert.equal(createHash('sha256').update(await readFile(source)).digest('hex').toUpperCase(), receipt.sha256);
const runExecutable = resolve(desktopRoot, 'output/windows-acceptance-binaries', receipt.sha256, 'nanfeng-ai-desktop-spike.exe');
const ps = (args) => execFileSync('pwsh', ['-NoProfile', ...args], { encoding: 'utf8', timeout: 60000, windowsHide: true }).trim();
const privateDesktopGuard=JSON.parse(ps(['-File',join(desktopRoot,'scripts/assert-windows-private-desktop.ps1')]));assert.equal(privateDesktopGuard.verified,true);
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






const requests=[],serverErrors=[];
let mode='web';
const provider=http.createServer(async(req,res)=>{try{
 let raw='';for await(const chunk of req){raw+=chunk.toString('utf8');assert.ok(raw.length<1024*1024);}
 const body=JSON.parse(raw);assert.equal(req.headers.authorization,'Bearer local-acceptance-mock-only');assert.equal(body.model,wireModel);
 const web=!cacheUsage&&mode!=='off';
 if(qwenMax||relayGpt){
  if(web){
   assert.equal(body.messages,undefined);assert.equal(body.tools?.[0]?.type,'web_search');assert.equal(body.store,false);
   if(relayGpt){
    // Acceptance replaces the whole endpoint with the explicit localhost URL.
    assert.equal(req.url,'/');assert.equal(body.tool_choice,'required');assert.equal(body.include[0],'web_search_call.action.sources');
    assert.ok(body.input.some(m=>m.role==='developer'));assert.ok(body.input.every(m=>m.role!=='system'));
    for(const m of body.input){if(m.role==='assistant')assert.ok(m.content.every(p=>p.type==='output_text'));else if(m.content.every(p=>p.type==='input_text'))assert.equal(m.content.length,1);}
    assert.ok(JSON.stringify(body.input).includes('Synthetic public question'));
    if(mode==='missing')assert.ok(body.input.some(m=>m.role==='assistant'&&JSON.stringify(m.content).includes('Unified synthetic web')));
   }else{assert.equal(body.enable_thinking,true);assert.equal(body.reasoning?.effort,'low');assert.equal(body.max_output_tokens,16384);assert.equal(JSON.stringify(body.input).split('以联网开关为准').length-1,1);assert.ok(JSON.stringify(body.input).includes('不搜索私人信息'));}
  }else{assert.equal(body.tools,undefined);assert.ok(Array.isArray(body.messages));if(!relayGpt){assert.equal(body.max_completion_tokens,16384);assert.equal(body.reasoning_effort,'low');assert.equal(body.preserve_thinking,false);}else{assert.ok(body.messages.some(m=>m.role==='system'));}}
  requests.push({model:body.model,mode,protocol:web?'responses':'chat',idempotencyKey:req.headers['idempotency-key'],...(relayGpt?{inputRoles:(body.input??[]).map(m=>m.role),developerInstruction:web&&body.input.some(m=>m.role==='developer'),historyPreserved:web&&mode==='missing'}:{})});assert.ok(requests.length<=3);
  res.writeHead(200,{'Content-Type':'text/event-stream; charset=utf-8'});
  if(web){
   const proofUrl=relayGpt?'https://example.test/relay-proof':'https://example.test/qwen-proof';
   const hasStructuredSources=mode==='web'||relayToolProof&&mode==='missing';
   const annotations=hasStructuredSources?[{type:'url_citation',url:proofUrl,title:'Synthetic public source'}]:[];
   const response={status:'completed',model:body.model,output:[{type:'web_search_call',status:relayToolProof&&mode==='missing'?'in_progress':'completed',...(relayGpt?{action:{type:'search',sources:hasStructuredSources?[{url:proofUrl,title:'Synthetic public source'}]:[]}}:{})},{type:'message',role:'assistant',content:[{type:'output_text',text:`Unified synthetic ${mode}`,annotations}]}],usage:{input_tokens:100,output_tokens:20,input_tokens_details:{cached_tokens:10}}};
   if(relayToolProof)Object.assign(requests.at(-1),{searchCallStatus:response.output[0].status,structuredCitationCount:annotations.length});
   res.write(`data: ${JSON.stringify({type:'response.created',response:{model:body.model}})}\n\n`);
   res.write(`data: ${JSON.stringify({type:'response.output_text.delta',delta:`Unified synthetic ${mode}`})}\n\n`);
   res.end(`data: ${JSON.stringify({type:'response.completed',response})}\n\ndata: [DONE]\n\n`);
  }else{
   res.write(`data: ${JSON.stringify({model:body.model,choices:[{delta:{content:`Unified synthetic ${mode}`}}]})}\n\n`);
   const cacheFacts=cacheUsage?{[mode==='web'?'prompt_cache_hit_tokens':mode==='off'?'cache_read_input_tokens':'cached_input_tokens']:10}:{prompt_tokens_details:{cached_tokens:10}};
   res.end(`data: ${JSON.stringify({model:body.model,choices:[{delta:{},finish_reason:cacheUsage&&mode==='missing'?'length':'stop'}],usage:{prompt_tokens:100,completion_tokens:20,...cacheFacts}})}\n\ndata: [DONE]\n\n`);
  }
 }else{
 const web=mode!=='off';assert.equal(body.tools,undefined);
 if(web){assert.equal(body.plugins?.[0]?.engine,'exa');assert.equal(JSON.stringify(body.messages).split('以联网开关为准').length-1,1);assert.ok(JSON.stringify(body.messages).includes('不搜索私人信息'));}else{assert.equal(body.plugins,undefined);}
 requests.push({model:body.model,mode,plugin:body.plugins??null,idempotencyKey:req.headers['idempotency-key']});assert.ok(requests.length<=3);
 res.writeHead(200,{'Content-Type':'text/event-stream; charset=utf-8'});
 res.write(`data: ${JSON.stringify({model:body.model,choices:[{delta:{content:`Unified synthetic ${mode}`}}]})}\n\n`);
 const delta=mode==='web'?{annotations:[{type:'url_citation',url_citation:{url:'https://example.test/haiku-proof',title:'Synthetic public source'}}]}:{};
 res.end(`data: ${JSON.stringify({model:body.model,choices:[{delta,finish_reason:'stop'}],usage:{prompt_tokens:100,completion_tokens:20,cost:.0003}})}\n\ndata: [DONE]\n\n`);
 }
}catch(error){serverErrors.push(String(error));res.destroy(error);}});
await new Promise(done=>provider.listen(0,'127.0.0.1',done));const providerPort=provider.address().port;
const result={privateDesktopGuard,qwenMax,relayToolProof,providerId,wireModel,candidateSha256:receipt.sha256,backgroundOnly:true,realService:false};let port,ws,conv;
async function conversation(){return (await invoke(port,'CHAT','read_desktop_workspace',{workspaceId:ws})).exchange.conversations.find(c=>c.id===conv.id);}
async function ledger(){return (await invoke(port,'CHAT','read_desktop_usage_ledger')).records.filter(r=>r.conversationId===conv.id);}
async function preference(enabled){const s=await invoke(port,'CHAT','read_desktop_p6g_selection',{workspaceId:ws,conversationId:conv.id});await invoke(port,'CHAT','set_desktop_conversation_preferences',{args:{workspaceId:ws,conversationId:conv.id,expectedRevision:s.conversationOverride.revision,toneOverride:null,webSearchOverride:enabled}});}
async function selectChat(){await until(()=>evaluate(port,`!!document.querySelector('[data-action="select-chat"][data-id="${conv.id}"]')`),'seed conversation');await clickUi(port,`[data-action="select-chat"][data-id="${conv.id}"]`);await until(()=>evaluate(port,`__nanfengDesktopWorkState.selectedConversationId===${JSON.stringify(conv.id)}`),'selected conversation');}
async function send(){const oldLeaf=(await conversation()).currentLeafId;await evaluate(port,`(()=>{const i=document.querySelector('#chat-composer');i.value='Synthetic public question';i.dispatchEvent(new InputEvent('input',{bubbles:true}));return true})()`);await until(()=>evaluate(port,'!!document.querySelector("[data-action=save-local-message]:not(:disabled)")'),'send enabled');await clickUi(port,'[data-action=save-local-message]');return await until(async()=>{const c=await conversation();const m=c.messages.find(m=>m.id===c.currentLeafId);return m?.id!==oldLeaf&&m?.role==='assistant'&&m.delivery!=='STREAMING'&&m.delivery!=='PENDING'&&!(m.delivery==='PARTIAL'&&m.runtimeState==='RUNNING')&&requests.length>0?m:null;},'terminal assistant');}
try{
 port=await start();await ready(port);result.runtimeInfo=await invoke(port,'CHAT','read_desktop_runtime_info');ws=(await invoke(port,'CHAT','ensure_desktop_local_workspace')).summary.id;
 const setting=(await invoke(port,'CHAT','read_desktop_model_service_settings')).find(s=>s.providerId===providerId);
 await invoke(port,'CHAT','save_desktop_model_service_settings',{args:{providerId,enabled:true,presetId,expectedRevision:setting.revision,apiKey:'nfai-synthetic-unified-only'}});
 conv=(await invoke(port,'CHAT','read_desktop_workspace',{workspaceId:ws})).exchange.conversations[0];const selection=await invoke(port,'CHAT','read_desktop_p6g_selection',{workspaceId:ws,conversationId:conv.id});await invoke(port,'CHAT','set_desktop_p6g_conversation_override',{args:{workspaceId:ws,conversationId:conv.id,expectedRevision:selection.conversationOverride.revision,modelId:presetId}});
 await preference(!cacheUsage);result.seedClose=await normalClose();port=await start(true);await ready(port);await selectChat();
 result.web=await send();assert.equal(result.web.delivery,'COMPLETE');if(!cacheUsage)assert.ok(JSON.stringify(result.web).includes(relayGpt?'https://example.test/relay-proof':qwenMax?'https://example.test/qwen-proof':'https://example.test/haiku-proof'));await until(async()=> (await ledger()).length===1,'web usage');
 mode='off';await preference(false);await selectChat();result.off=await send();assert.equal(result.off.delivery,'COMPLETE');await until(async()=> (await ledger()).length===2,'off usage');
 mode='missing';await preference(!cacheUsage);await selectChat();result.missing=await send();assert.notEqual(result.missing.delivery,'COMPLETE');await until(async()=> (await ledger()).length===3,'failed measured usage');
 if(relayToolProof){
  assert.equal(result.missing.safeErrorCode,'WEB_SEARCH_NO_SOURCES');
  await until(()=>evaluate(port,`document.querySelector('article[data-message-id="${result.missing.id}"]')?.innerText.includes('联网搜索未完成')`),'incomplete native search failure UI');
  const failedText=await evaluate(port,`document.querySelector('article[data-message-id="${result.missing.id}"]')?.innerText||''`);
  assert.ok(failedText.includes('重新生成'));assert.ok(!failedText.includes('Unified synthetic missing'));result.incompleteSearchRejectedInUi=true;
 }
 result.ledger=await ledger();assert.equal(result.ledger.filter(r=>(qwenMax||relayGpt)?r.chargeMicros>0&&r.currencyCode==='CNY':r.chargeMicros===300&&r.currencyCode==='USD').length,3);
 if(cacheUsage){assert.equal(result.missing.delivery,'FAILED');assert.ok(JSON.stringify(result.missing.blocks).includes('Unified synthetic missing'));}
 else assert.ok(!JSON.stringify(result.missing.blocks).includes('Unified synthetic missing'));
 if(cacheUsage){assert.deepEqual(result.ledger.map(r=>r.cachedInputTokens),[10,10,10]);assert.deepEqual(result.ledger.map(r=>r.chargeMicros),[1824,1824,1824]);result.cacheUsage=true;}
 result.close=await normalClose();port=await start(true);await ready(port);assert.deepEqual(await ledger(),result.ledger);const cold=await conversation();assert.deepEqual(cold.messages.find(m=>m.id===result.web.id),result.web);assert.deepEqual(cold.messages.find(m=>m.id===result.missing.id),result.missing);result.coldClose=await normalClose();assert.equal(requests.length,3);assert.deepEqual(serverErrors,[]);result.pass=true;
}catch(error){result.error=error.stack||String(error);process.exitCode=1;}
finally{if(launch)try{result.failureClose=await normalClose();}catch(error){result.closeError=String(error);stop();}await new Promise(done=>provider.close(done));result.root=root;result.requests=requests;result.serverErrors=serverErrors;result.pids=pids;if(root)await writeFile(join(root,'unified-web-native-evidence.json'),JSON.stringify(result,null,2)+'\n');}
console.log(JSON.stringify(result,null,2));
