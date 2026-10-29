import assert from 'node:assert/strict';
import { createHash, randomUUID } from 'node:crypto';
import { execFileSync, spawn } from 'node:child_process';
import { readFile, writeFile } from 'node:fs/promises';
import http from 'node:http';
import { dirname, join, resolve, sep } from 'node:path';
import { fileURLToPath } from 'node:url';

if (process.platform !== 'win32') throw new Error('仅在 Windows 隔离候选上验收。');
const desktopRoot = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const rootParent = resolve(process.argv[2] || 'D:\\CodexTemp');
assert.ok(process.execArgv.includes('--max-old-space-size=2048'));
const screenshotMode = process.argv.includes('--screenshot');
const receipt = JSON.parse(await readFile(join(desktopRoot, 'output/windows-acceptance/current.json'), 'utf8'));
assert.equal(receipt.identifier, 'com.nanzhufeng.ai.desktop.windowsacceptance');
const source = resolve(desktopRoot, 'output/windows-acceptance', receipt.sha256, 'NanfengAI-Windows-Acceptance.exe');
assert.equal(resolve(receipt.executable), source);
assert.equal(createHash('sha256').update(await readFile(source)).digest('hex').toUpperCase(), receipt.sha256);
const runExecutable = resolve(desktopRoot, 'output/windows-acceptance-binaries', receipt.sha256, 'nanfeng-ai-desktop-spike.exe');
const ps = (args) => execFileSync('pwsh', ['-NoProfile', ...args], { encoding: 'utf8', timeout: 60000 }).trim();
const launchPowerShell = (args) => new Promise((resolveLaunch, reject) => {
  const child = spawn('pwsh', ['-NoProfile', ...args], { stdio: ['ignore', 'pipe', 'pipe'] });
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
const requests = [];
const serverErrors = [];
const provider = http.createServer(async (request, response) => {
  try {
    let raw=''; for await(const chunk of request) {raw+=chunk.toString('utf8');if(raw.length>2*1024*1024)throw new Error('fixture request too large');}
    const body=JSON.parse(raw); const identity=request.headers['idempotency-key'];
    assert.ok(identity);assert.equal(request.headers.authorization==='Bearer local-acceptance-mock-only',true);
    const messages=body.messages||body.input;
    assert.ok(JSON.stringify(messages).includes('A3 合成全预设问题'));
    const messagesProtocol=Boolean(body.output_config);const responsesProtocol=Boolean(body.input);
    const record={model:body.model,protocol:messagesProtocol?'Messages':responsesProtocol?'Responses':'ChatCompletions',path:request.url,idempotencyKey:identity,stream:body.stream,hasSources:responsesProtocol};
    requests.push(record);assert.equal(body.stream,true);
    response.writeHead(200,{'Content-Type':'text/event-stream; charset=utf-8','Cache-Control':'no-cache'});
    const event=value=>response.write(`data: ${JSON.stringify(value)}\n\n`);
    if(messagesProtocol) {
      assert.equal(request.headers['x-api-key']==='local-acceptance-mock-only',true);assert.equal(request.headers['anthropic-version'],'2023-06-01');
      assert.equal(body.temperature,undefined);
      event({type:'message_start',message:{model:body.model,usage:{input_tokens:4,cache_read_input_tokens:1}}});
      event({type:'content_block_delta',delta:{type:'text_delta',text:'合成原生回答'}});
      event({type:'message_delta',delta:{stop_reason:'end_turn'},usage:{output_tokens:2}});
      response.end(`data: ${JSON.stringify({type:'message_stop'})}`);
    } else if(responsesProtocol) {
      assert.equal(body.tool_choice,'required');assert.equal(body.tools[0].external_web_access,true);assert.ok(body.include.includes('web_search_call.action.sources'));
      event({type:'response.output_text.delta',delta:'合成原生联网回答'});
      response.end(`data: ${JSON.stringify({type:'response.completed',response:{model:body.model,usage:{input_tokens:4,output_tokens:2},output:[{type:'web_search_call',status:'completed',action:{sources:[{title:'Synthetic public evidence',url:'https://example.test/a3-evidence'}]}}]}})}`);
    } else {
      event({model:body.model,choices:[{delta:{content:'合成原生回答'}}]});
      response.end(`data: ${JSON.stringify({model:body.model,choices:[{delta:{},finish_reason:'stop'}],usage:{prompt_tokens:4,completion_tokens:2,prompt_tokens_details:{cached_tokens:1},cost:0.000004}})}`);
    }
  } catch(error) { serverErrors.push(String(error)); response.destroy(error); }
});
await new Promise(resolveReady => provider.listen(0, '127.0.0.1', resolveReady));
const providerPort = provider.address().port;
let launch = null;
let root = '';
const pids = [];
const stop = () => {
  if (!launch) return;
  const pid = Number(launch.pid);
  const literal = value => `'${String(value).replaceAll("'", "''")}'`;
  const script = '$expected=' + literal(runExecutable) + '; $hash=' + literal(receipt.sha256) + '; '
    + '$p=Get-CimInstance Win32_Process -Filter "ProcessId=' + pid + '"; '
    + 'if ($p) { if ([IO.Path]::GetFullPath([string]$p.ExecutablePath) -ne $expected '
    + '-or (Get-FileHash -LiteralPath $expected -Algorithm SHA256).Hash -ne $hash) { throw "PID identity mismatch" }; '
    + 'Stop-Process -Id ' + pid + ' -Force -ErrorAction Stop }';
  ps(['-Command', script]);
  pids.push(pid);
  launch = null;
};
async function start(reopen = false) {
  const port = await reservePort();
  const args = ['-File', join(desktopRoot, 'scripts/start-windows-acceptance.ps1'), '-RootParent', rootParent,
    '-StartupMode', 'normal', '-Visible', '-DebugPort', String(port), '-OrdinaryChatTestEndpoint', `http://127.0.0.1:${providerPort}`];
  if (reopen) args.push('-Root', root);
  launch = await launchPowerShell(args);
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
  while (Date.now() < deadline) {
    try { if (await evaluate(port, 'Boolean(window.__TAURI__?.core?.invoke)')) return; } catch { /* Startup is still active. */ }
    await new Promise(resolveWait => setTimeout(resolveWait, 250));
  }
  throw new Error('原生 IPC 未就绪');
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
  const info=JSON.parse(ps(['-Command',`$p=Get-CimInstance Win32_Process -Filter "ProcessId=${pid}";if(!$p -or [IO.Path]::GetFullPath([string]$p.ExecutablePath) -ne ${quote(runExecutable)} -or (Get-FileHash -LiteralPath ${quote(runExecutable)}).Hash -ne ${quote(receipt.sha256)}){throw 'PID identity mismatch'};$n=Get-Process -Id ${pid};@{title=$n.MainWindowTitle;responding=$n.Responding;accepted=$n.CloseMainWindow()}|ConvertTo-Json -Compress`]));
  assert.ok(info.title && info.responding && info.accepted);
  await until(()=>ps(['-Command',`@(Get-CimInstance Win32_Process -Filter "ProcessId=${pid}").Count`])==='0','自然关闭退出');
  await until(()=>ps(['-Command',`@(Get-CimInstance Win32_Process -Filter "Name='msedgewebview2.exe'" | Where-Object {$_.CommandLine -and $_.CommandLine.Contains(${quote(launch.webviewProfile)})}).Count`])==='0','本根WebView退出');
  pids.push(pid);launch=null;return info;
}



const result={candidateSha256:receipt.sha256,realService:false,cases:[],requests,serverErrors,credentialBoundary:'ordinary-loopback uses fixed mock credential; DPAPI setting path verified separately in A1'};let port=0;let stage='start';
const presets=[
 ['CLAUDE_OPUS_5_5','OPENROUTER','anthropic/claude-opus-5.5'],
 ['CLAUDE_SONNET_5_5','OPENROUTER','anthropic/claude-sonnet-5.5'],
 ['CLAUDE_HAIKU_5_5','OPENROUTER','anthropic/claude-haiku-5.5'],
 ['GPT_6_1_SOL','OPENROUTER','openai/gpt-6.1-sol'],['GPT_6_LUNA','OPENROUTER','openai/gpt-6-luna'],
 ['MUSE_WONDER','OPENROUTER','meta/muse-wonder'],['GEMINI_3_8_FLASH','OPENROUTER','google/gemini-3.8-flash'],
 ['DEEPSEEK_V4_PRO','DEEPSEEK','deepseek-v4-pro'],['DEEPSEEK_V4_FLASH','DEEPSEEK','deepseek-flash'],
 ['GLM_5_3','ZHIPU','glm-5.3'],['GLM_5_3_FLASH','ZHIPU','glm-5.3-flash'],
 ['QWEN_3_7_PLUS','QWEN','qwen3.7-plus'],['QWEN_3_8_MAX','QWEN','qwen3.8-max'],['QWEN_3_6_FLASH','QWEN','qwen3.6-flash'],
 ['RELAYCORE_OPUS_5_5','RELAYCORE_CLAUDE','claude-opus-5.5'],['RELAYCORE_SONNET_5_5','RELAYCORE_CLAUDE','claude-sonnet-5-5'],
 ['RELAYCORE_GPT_6_1_SOL','RELAYCORE_GPT','gpt-6.1-sol'],['RELAYCORE_GPT_6_ASTRA','RELAYCORE_GPT','gpt-6-astra'],
 ['RELAYCORE_GEMINI_3_8_FLASH','RELAYCORE_GEMINI','gemini-3.8-flash'],['RELAYCORE_GEMINI_3_1_PRO','RELAYCORE_GEMINI','gemini-3.1-pro']
];
const auth=()=>({approvedAtMs:Date.now(),disclosureVersion:'normal-chat-egress-v1',clientSubmissionId:randomUUID()});
const currentConversation=async(workspaceId,conversationId)=>(await invoke(port,'CHAT','read_desktop_workspace',{workspaceId})).exchange.conversations.find(item=>item.id===conversationId);
async function searchPreference(workspaceId,conversationId,value) {
 const selection=await invoke(port,'CHAT','read_desktop_p6g_selection',{workspaceId,conversationId});
 await invoke(port,'CHAT','set_desktop_conversation_preferences',{args:{workspaceId,conversationId,expectedRevision:selection.conversationOverride.revision,toneOverride:null,webSearchOverride:value}});
}
try {
 port=await start();result.root=root;await ready(port);
 const workspaceId=(await invoke(port,'CHAT','ensure_desktop_local_workspace')).summary.id;result.workspaceId=workspaceId;
 const settings=await invoke(port,'CHAT','read_desktop_model_service_settings');assert.equal(settings.length,7);
 for(const setting of settings) {
  const preset=presets.find(item=>item[1]===setting.providerId);assert.ok(preset);
  await invoke(port,'CHAT','save_desktop_model_service_settings',{args:{providerId:setting.providerId,enabled:true,presetId:preset[0],expectedRevision:setting.revision,apiKey:`nfai-synthetic-a3-${setting.providerId}`}});
 }
 const created=await invoke(port,'CHAT','mutate_desktop_domain',{args:{intentId:`a3-${randomUUID()}`,workspaceId,entity:'conversation',action:'create',objectId:null,expectedRevision:null,fields:{title:'A3合成目录',projectId:null,firstMessage:'A3 合成全预设问题'}}});
 const conversationId=created.objectId;result.conversationId=conversationId;
 let conversation=await currentConversation(workspaceId,conversationId);const userMessageId=conversation.currentLeafId;
 await searchPreference(workspaceId,conversationId,false);
 await invoke(port,'CHAT','generate_desktop_ordinary_chat_for_saved_user',{args:{workspaceId,conversationId,userMessageId,expectedRevision:conversation.revision,egressAuthorization:auth()}});
 conversation=await currentConversation(workspaceId,conversationId);
 assert.equal(conversation.messages.find(item=>item.id===conversation.currentLeafId)?.delivery,'COMPLETE');
 const first=presets.find(item=>item[2]===requests[0]?.model);assert.ok(first);result.cases.push(first[0]);
 for(const preset of presets.filter(item=>item[0]!==first[0])) {
  stage=preset[0];const before=requests.length;
  await invoke(port,'CHAT','regenerate_desktop_ordinary_chat',{args:{workspaceId,conversationId,assistantMessageId:conversation.currentLeafId,targetPresetId:preset[0],expectedRevision:conversation.revision,egressAuthorization:auth()}});
  conversation=await currentConversation(workspaceId,conversationId);const leaf=conversation.messages.find(item=>item.id===conversation.currentLeafId);
  assert.equal(leaf?.delivery,'COMPLETE',preset[0]);assert.equal(leaf?.parentId,userMessageId);
  assert.equal(requests.length,before+1);assert.equal(requests.at(-1).model,preset[2]);result.cases.push(preset[0]);
 }
 assert.equal(result.cases.length,20);assert.equal(new Set(requests.map(item=>item.idempotencyKey)).size,20);
 const ledger=await invoke(port,'CHAT','read_desktop_usage_ledger');const charges=ledger.records.filter(item=>item.conversationId===conversationId);
 assert.equal(charges.length,20);assert.ok(charges.every(item=>item.inputTokens>=4 && item.outputTokens===2));result.offlineLedgerCount=charges.length;
 stage='rc-native-responses';await searchPreference(workspaceId,conversationId,true);
 for(const presetId of ['RELAYCORE_GPT_6_1_SOL','RELAYCORE_GPT_6_ASTRA']) {
  const attempt=await invoke(port,'CHAT','regenerate_desktop_ordinary_chat',{args:{workspaceId,conversationId,assistantMessageId:conversation.currentLeafId,targetPresetId:presetId,expectedRevision:conversation.revision,egressAuthorization:auth()}});
  conversation=await currentConversation(workspaceId,conversationId);const leaf=conversation.messages.find(item=>item.id===conversation.currentLeafId);
  assert.equal(leaf?.delivery,'COMPLETE');assert.equal(attempt.webSearchRoute,'RELAYCORE_GPT_RESPONSES');assert.equal(attempt.webSearchVerified,true);
  assert.ok(JSON.stringify(leaf.blocks).includes('https://example.test/a3-evidence'));result.cases.push(`${presetId}:search`);
 }
 stage='companion-requires-owner';const before=requests.length;
 const denied=await invoke(port,'CHAT','regenerate_desktop_ordinary_chat',{args:{workspaceId,conversationId,assistantMessageId:conversation.currentLeafId,targetPresetId:'RELAYCORE_OPUS_5_5',expectedRevision:conversation.revision,egressAuthorization:auth()}});
 assert.equal(denied.state,'FAILED');assert.equal(denied.safeErrorCode,'WEB_SEARCH_UNAVAILABLE');assert.equal(requests.length,before);result.companionNoOfflineSubstitution=true;
 conversation=await currentConversation(workspaceId,conversationId);const finalLeaf=conversation.currentLeafId;
 const savedLedger=(await invoke(port,'CHAT','read_desktop_usage_ledger')).records.filter(item=>item.conversationId===conversationId);
 result.close=await normalClose();port=await start(true);await ready(port);
 const cold=await currentConversation(workspaceId,conversationId);assert.equal(cold.currentLeafId,finalLeaf);assert.equal(requests.length,before);
 const coldLedger=(await invoke(port,'CHAT','read_desktop_usage_ledger')).records.filter(item=>item.conversationId===conversationId);
 assert.deepEqual(coldLedger,savedLedger);result.coldLedgerPreserved=true;result.closeAfterReopen=await normalClose();result.pass=true;
} catch(error) {result.pass=false;result.stage=stage;result.error=String(error);process.exitCode=1;}
finally {
 if(launch){result.forcedCleanup=true;stop();}provider.closeAllConnections();await new Promise(done=>provider.close(done));result.pids=pids;
 if(root)await writeFile(join(root,'a3-current-preset-evidence.json'),JSON.stringify(result,null,2),{flag:'wx'});
}
console.log(JSON.stringify({root,candidateSha256:receipt.sha256,pass:result.pass,stage:result.stage,error:result.error,cases:result.cases.length,requestCount:requests.length,pids}));
