import { packageExchange, preflightPackage, semanticHash, sha256 } from '../../protocol/scripts/exchange-lib.mjs';
import assert from 'node:assert/strict';
import { createHash, randomUUID } from 'node:crypto';
import { execFileSync, spawn } from 'node:child_process';
import { mkdir, readFile, writeFile } from 'node:fs/promises';
import http from 'node:http';
import { dirname, join, resolve, sep } from 'node:path';
import { fileURLToPath } from 'node:url';

if (process.platform !== 'win32') throw new Error('仅在 Windows 隔离候选上验收。');
const desktopRoot = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const rootParent = resolve(process.argv[2] || 'D:\\CodexTemp');
assert.ok(process.execArgv.includes('--max-old-space-size=2048'));
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
const requests=[];
const provider=http.createServer(async(request,response)=>{
 let body='';for await(const chunk of request)body+=chunk.toString('utf8');
 const payload=JSON.parse(body);requests.push({payload,path:request.url});
 response.writeHead(200,{'content-type':'text/event-stream; charset=utf-8'});
 response.end('data: '+JSON.stringify({model:'openai/gpt-5.6-terra',choices:[{delta:{content:'合成预算完整回答'},finish_reason:'stop'}],usage:{prompt_tokens:32,completion_tokens:8,cost:0.000002}})+'\n\ndata: [DONE]\n\n');
});
await new Promise(done=>provider.listen(0,'127.0.0.1',done));const providerPort=provider.address().port;
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
  const result = await evaluate(port, `(()=>{const node=document.querySelector(${JSON.stringify(selector)});if(!node)return false;node.click();return true})()`);
  assert.equal(result, true, `找不到可点击控件 ${selector}`);
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

const result={candidateSha256:receipt.sha256,startupMode:'normal',realService:false};
let stage='start',activePort;
const windowTokens=2048;
const latest='预算测试 KEEP_CURRENT_COMPLETE';
const oversized='预算超限 CURRENT_OVERSIZED_BEGIN '+ '超'.repeat(4096)+' CURRENT_OVERSIZED_END';
const knowledgeBody='预算测试 K_SMALL_BEGIN '+'可完整使用资料'.repeat(20)+' K_SMALL_END';
const largeBody='预算测试 K_LARGE_BEGIN '+ '超长资料'.repeat(6000)+' K_LARGE_END';
const memoryBody='预算测试 MEMORY_BEGIN 用户希望完整保留当前问题 MEMORY_END';
function estimate(text){let units=0,run=0;for(const ch of text){if(ch.charCodeAt(0)<=127&&!/\s/.test(ch))run++;else{units+=Math.ceil(run/4);run=0;if(!/\s/.test(ch))units++;}}return units+Math.ceil(run/4);}
async function catalog(port,maxOutputTokens){const c=await invoke(port,'CHAT','read_desktop_p6g_catalog');await invoke(port,'CHAT','upsert_desktop_p6g_catalog_candidate',{args:{expectedRevision:c.revision,catalogVersion:'synthetic-budget-v1',policyVersion:1,candidate:{providerFamily:'OPENAI',providerId:'OPENROUTER',modelId:'GPT_5_6_TERRA',displayName:'GPT-5.6 Terra',tiers:['BALANCED'],capabilities:['TEXT'],available:true,knownCostMicros:null,latencyRank:1,contextWindowTokens:windowTokens,maxOutputTokens}}});}
async function current(port,workspaceId,conversationId){return (await invoke(port,'CHAT','read_desktop_workspace',{workspaceId})).exchange.conversations.find(c=>c.id===conversationId);}
async function send(port,text){await until(()=>evaluate(port,'Boolean(document.querySelector("#chat-composer"))'),'输入正文');await evaluate(port,`(()=>{const input=document.querySelector('#chat-composer');input.value=${JSON.stringify(text)};input.dispatchEvent(new Event('input',{bubbles:true}));return true})()`);await until(()=>evaluate(port,'Boolean(document.querySelector("[data-action=save-local-message]:not([disabled])"))'),'实际发送按钮');await clickUi(port,'[data-action="save-local-message"]');}
try{
 let port=await start();activePort=port;result.root=root;await ready(port);
 const exchange=JSON.parse(await readFile(join(desktopRoot,'../protocol/fixtures/nfai.exchange.v1.golden.json'),'utf8'));
 const original=structuredClone(exchange.conversations[0]),conversation=structuredClone(original);conversation.id='synthetic-budget-conversation';conversation.title='合成预算会话';conversation.projectId=null;conversation.revision=1;
 conversation.messages=Array.from({length:23},(_,i)=>{const m=structuredClone(original.messages[0]);m.id='synthetic-budget-old-'+i;m.parentId=i?'synthetic-budget-old-'+(i-1):null;m.role='user';m.revision=1;m.blocks=[{kind:'TEXT',ordinal:0,text:`OLD_BUDGET_${i}_BEGIN `+'x'.repeat(1200)+` OLD_BUDGET_${i}_END`}];return m;});conversation.currentLeafId=conversation.messages.at(-1).id;
 exchange.conversations=[conversation];exchange.projects=[];exchange.relations=[];
 const k=structuredClone(exchange.knowledge[0]);k.id='synthetic-budget-small-knowledge';k.title='预算测试准确资料';k.body=knowledgeBody;k.contentHash=sha256(Buffer.from(k.body));k.scope='GLOBAL';k.projectId=null;
 const big=structuredClone(k);big.id='synthetic-budget-large-knowledge';big.title='预算测试超长资料';big.body=largeBody;big.contentHash=sha256(Buffer.from(big.body));exchange.knowledge=[k,big];
 const m=structuredClone(exchange.memory[0]);m.id='synthetic-budget-memory';m.body=memoryBody;m.contentHash=sha256(Buffer.from(m.body));m.scope='GLOBAL';m.scopeId=null;exchange.memory=[m];
 exchange.export.id='synthetic-budget-export';exchange.export.semanticHash=semanticHash(exchange);const bytes=packageExchange(exchange);preflightPackage(bytes);
 const selectedPath=join(root,'synthetic-context-budget.nfai-exchange');await writeFile(selectedPath,bytes);const preflight=await invoke(port,'CHAT','stage_preflight_selected_exchange',{selectedPath});const imported=await invoke(port,'CHAT','import_staged_exchange_as_new_workspace',{args:{stagingId:preflight.stagingId,workspaceTitle:'合成上下文预算'}});const workspaceId=imported.summary.id,conversationId=conversation.id;result.workspaceId=workspaceId;result.conversationId=conversationId;
 const settings=await invoke(port,'CHAT','read_desktop_app_settings');await invoke(port,'CHAT','save_desktop_app_settings',{args:{appearance:settings.appearance,product:{...settings.product,historyLibraryEnabled:true,memoryEnabled:true,memoryRetrievalEnabled:true},expectedRevision:settings.revision}});await catalog(port,512);
 await cdp(port,'Page.reload',{ignoreCache:true});await ready(port);await until(()=>evaluate(port,`Boolean(document.querySelector('[data-action="select-workspace"][data-id="${workspaceId}"]'))`),'导入工作区入口');await clickUi(port,`[data-action="select-workspace"][data-id="${workspaceId}"]`);await until(()=>evaluate(port,`Boolean(document.querySelector('[data-action="select-chat"][data-id="${conversationId}"]'))`),'合成会话入口');await clickUi(port,`[data-action="select-chat"][data-id="${conversationId}"]`);await until(()=>evaluate(port,`Boolean(document.querySelector('.chat-scroll[data-conversation-id="${conversationId}"]'))`),'当前会话正文');
 await clickUi(port,'[data-action="toggle-composer-add"]');await until(()=>evaluate(port,'Boolean(document.querySelector("[data-action=toggle-conversation-web-search]"))'),'联网偏好');if(await evaluate(port,'document.querySelector("[data-action=toggle-conversation-web-search]").getAttribute("aria-checked")==="true"'))await clickUi(port,'[data-action="toggle-conversation-web-search"]');await clickUi(port,'[data-action="close-composer-add"]');
 stage='narrow-context-send';await send(port,latest);await until(()=>requests.length===1,'首个实际预算请求');const request=requests[0].payload,turns=request.messages.filter(x=>['user','assistant'].includes(x.role));const rendered=JSON.stringify(request.messages);const estimated=request.messages.reduce((n,x)=>n+estimate(typeof x.content==='string'?x.content:JSON.stringify(x.content))+8,0);assert.equal(request.max_tokens,512);assert.ok(estimated<=windowTokens-512);assert.equal(turns.at(-1).content,latest);assert.equal(turns[0].role,'user');assert.ok(turns.length<24);assert.ok(!rendered.includes('OLD_BUDGET_0_BEGIN'));assert.ok(rendered.includes('K_SMALL_BEGIN')&&rendered.includes('K_SMALL_END'));assert.ok(!rendered.includes('K_LARGE_BEGIN')&&!rendered.includes('K_LARGE_END'));
 let complete=await until(async()=>{const c=await current(port,workspaceId,conversationId);return c.messages.some(x=>x.role==='assistant'&&x.delivery==='COMPLETE')?c:null;},'完整回答落盘');assert.equal(complete.messages.filter(x=>x.role==='user').length,24);
 result.firstRequest={model:request.model,path:requests[0].path,maxOutputTokens:request.max_tokens,estimatedInputTokens:estimated,sentTurns:turns.length,latestWhole:true,oldestRemoved:true,smallKnowledgeWhole:true,largeKnowledgeAbsent:true,memoryWhole:rendered.includes('MEMORY_BEGIN')&&rendered.includes('MEMORY_END')};
 result.recordsFirst=await invoke(port,'CHAT','read_desktop_ordinary_chat_context_records');assert.ok(result.recordsFirst.some(r=>r.conversationId===conversationId&&r.selectedSources.some(s=>s.kind==='资料库'&&s.title===k.title)));assert.ok(!result.recordsFirst.some(r=>r.conversationId===conversationId&&r.selectedSources.some(s=>s.title===big.title)));
 stage='output-reservation-cap';await catalog(port,65536);const secondText='预算测试 OUTPUT_RESERVE_COMPLETE';await send(port,secondText);await until(()=>requests.length===2,'第二个输出保留请求');const second=requests[1].payload;assert.equal(second.max_tokens,windowTokens/2);assert.equal(second.messages.filter(x=>x.role==='user').at(-1).content,secondText);assert.ok(second.messages.reduce((n,x)=>n+estimate(typeof x.content==='string'?x.content:JSON.stringify(x.content))+8,0)<=windowTokens/2);
 complete=await until(async()=>{const c=await current(port,workspaceId,conversationId);return c.messages.filter(x=>x.role==='assistant'&&x.delivery==='COMPLETE').length===2?c:null;},'第二个完整回答');result.secondRequest={model:second.model,maxOutputTokens:second.max_tokens,requestedCatalogOutputTokens:65536,latestWhole:true};
 stage='oversized-current-question';await send(port,oversized);await until(()=>evaluate(port,'Boolean(globalThis.__nanfengDesktopWorkState.error?.includes("上下文窗口"))'),'超预算明确拒绝');assert.equal(requests.length,2,'超预算不得发送HTTP');const afterReject=await current(port,workspaceId,conversationId);assert.deepEqual(afterReject,complete,'超预算拒绝不能丢原历史或追加半提交');assert.equal(await evaluate(port,'globalThis.__nanfengDesktopWorkState.composerDraft'),oversized,'拒绝保留未提交输入');result.refusal={noAdditionalHttp:true,conversationUnchanged:true,draftRetained:true,error:await evaluate(port,'globalThis.__nanfengDesktopWorkState.error')};
 const beforeClosingSettings=await invoke(port,'CHAT','read_desktop_app_settings');
 await invoke(port,'CHAT','save_desktop_app_settings',{args:{appearance:beforeClosingSettings.appearance,product:{...beforeClosingSettings.product,historyLibraryEnabled:false},expectedRevision:beforeClosingSettings.revision}});result.historyLibraryDisabledBeforeCold=true;
 result.beforeCold=await invoke(port,'CHAT','read_desktop_workspace',{workspaceId});result.recordsBeforeCold=await invoke(port,'CHAT','read_desktop_ordinary_chat_context_records');result.firstNormalClose=await normalClose();port=await start(true);activePort=port;await ready(port);stage='cold-read';assert.deepEqual(await invoke(port,'CHAT','read_desktop_workspace',{workspaceId}),result.beforeCold);assert.deepEqual(await invoke(port,'CHAT','read_desktop_ordinary_chat_context_records'),result.recordsBeforeCold);assert.equal(requests.length,2);
 const peer=await invoke(port,'WORK','list_desktop_workspaces');assert.ok(!peer.some(w=>w.id===workspaceId));result.peerWorkspaceAbsent=true;result.secondNormalClose=await normalClose();result.pass=true;
}catch(error){result.error=String(error.stack||error);result.stage=stage;process.exitCode=1;}
finally{if(launch){result.forcedCleanup=true;stop();}provider.closeAllConnections();await new Promise(done=>provider.close(done));result.pids=pids;if(root)await writeFile(join(root,'context-budget-evidence.json'),JSON.stringify(result,null,2),{flag:'wx'});}
console.log(JSON.stringify({candidateSha256:receipt.sha256,root,pass:result.pass,stage:result.stage,error:result.error,pids}));

