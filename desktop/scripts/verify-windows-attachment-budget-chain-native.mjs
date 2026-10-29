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
  const args = ['-File', join(desktopRoot, 'scripts/start-windows-acceptance.ps1'), '-ExecutablePath', source, '-RootParent', rootParent,
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
      const timer = setTimeout(() => reject(new Error('CDP evaluation timed out')), 20000);
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


const result={candidateSha256:receipt.sha256,startupMode:'normal',realService:false,normalCloses:[]};let stage='start';
const text='完整双附件预算问题 CURRENT_KEEP',bodies=['ATTACHMENT_A_BEGIN '+'甲'.repeat(2100)+' ATTACHMENT_A_END','ATTACHMENT_B_BEGIN '+'乙'.repeat(2100)+' ATTACHMENT_B_END'];
async function catalog(port,windowTokens){const c=await invoke(port,'CHAT','read_desktop_p6g_catalog');await invoke(port,'CHAT','upsert_desktop_p6g_catalog_candidate',{args:{expectedRevision:c.revision,catalogVersion:'synthetic-attachment-budget',policyVersion:1,candidate:{providerFamily:'OPENAI',providerId:'OPENROUTER',modelId:'GPT_5_6_TERRA',displayName:'GPT-5.6 Terra',tiers:['BALANCED'],capabilities:['TEXT'],available:true,knownCostMicros:null,latencyRank:1,contextWindowTokens:windowTokens,maxOutputTokens:512}}});}
async function send(port,value){await until(()=>evaluate(port,'Boolean(document.querySelector("#chat-composer"))'),'输入');await evaluate(port,`(()=>{const n=document.querySelector('#chat-composer');n.value=${JSON.stringify(value)};n.dispatchEvent(new Event('input',{bubbles:true}));return true})()`);await until(()=>evaluate(port,'Boolean(document.querySelector("[data-action=save-local-message]:not([disabled])"))'),'发送');await clickUi(port,'[data-action="save-local-message"]');}
try{
 let port=await start();result.root=root;await ready(port);const workspaceId=(await invoke(port,'CHAT','ensure_desktop_local_workspace')).summary.id;result.workspaceId=workspaceId;
 const settings=await invoke(port,'CHAT','read_desktop_app_settings');await invoke(port,'CHAT','save_desktop_app_settings',{args:{appearance:settings.appearance,product:{...settings.product,historyLibraryEnabled:false,memoryEnabled:false,memoryRetrievalEnabled:false},expectedRevision:settings.revision}});await catalog(port,2048);
 await cdp(port,'Page.reload',{ignoreCache:true});await ready(port);await until(()=>evaluate(port,'Boolean(document.querySelector("#chat-composer"))'),'输入区');
 await until(()=>evaluate(port,`globalThis.__nanfengDesktopWorkState?.current?.summary?.id===${JSON.stringify(workspaceId)}`),'实际工作区投影');await clickUi(port,'[data-action="new-chat"]');await clickUi(port,'[data-action="toggle-composer-add"]');await until(()=>evaluate(port,'Boolean(document.querySelector("[data-action=toggle-conversation-web-search]"))'),'搜索');if(await evaluate(port,'document.querySelector("[data-action=toggle-conversation-web-search]").getAttribute("aria-checked")==="true"'))await clickUi(port,'[data-action="toggle-conversation-web-search"]');await clickUi(port,'[data-action="close-composer-add"]');
 stage='double-file-input';for(let i=0;i<2;i++){assert.equal(await evaluate(port,`(()=>{const n=document.querySelector('#chat-composer');const d=new DataTransfer();d.items.add(new File([${JSON.stringify(bodies[i])}],${JSON.stringify('合成预算 '+i+'.txt')},{type:'text/plain'}));const e=new Event('paste',{bubbles:true,cancelable:true});Object.defineProperty(e,'clipboardData',{value:d});n.dispatchEvent(e);return e.defaultPrevented})()`),true);await until(()=>evaluate(port,`document.querySelectorAll('.chat-composer-attachments [data-action=remove-composer-attachment]').length===${i+1}`),'附件卡');}
 await evaluate(port,`(()=>{const n=document.querySelector('#chat-composer');n.value=${JSON.stringify(text)};n.dispatchEvent(new Event('input',{bubbles:true}));return true})()`);
 const draft=await until(async()=>{const d=await invoke(port,'CHAT','read_desktop_conversation_draft',{workspaceId,conversationId:null});return d?.attachments?.length===2&&d.text===text?d:null;},'完整草稿');result.draftBefore=draft;
 const before=await invoke(port,'CHAT','read_desktop_workspace',{workspaceId});stage='reject-over-budget';await send(port,text);await until(()=>evaluate(port,'Boolean(globalThis.__nanfengDesktopWorkState.error?.includes("上下文窗口"))'),'超预算拒绝');assert.equal(requests.length,0);assert.deepEqual(await invoke(port,'CHAT','read_desktop_workspace',{workspaceId}),before);assert.deepEqual(await invoke(port,'CHAT','read_desktop_conversation_draft',{workspaceId,conversationId:null}),draft);result.refusal={error:await evaluate(port,'globalThis.__nanfengDesktopWorkState.error'),httpCount:0,workspaceUnchanged:true,draftExact:true};
 result.normalCloses.push(await normalClose());port=await start(true);await ready(port);stage='cold-refused-draft';assert.deepEqual(await invoke(port,'CHAT','read_desktop_conversation_draft',{workspaceId,conversationId:null}),draft);assert.deepEqual(await invoke(port,'CHAT','read_desktop_workspace',{workspaceId}),before);assert.equal(requests.length,0);await until(()=>evaluate(port,'document.querySelectorAll(".chat-composer-attachments [data-action=remove-composer-attachment]").length===2'),'冷启附件');assert.equal(await evaluate(port,'document.querySelector("#chat-composer").value'),text);
 await catalog(port,16384);stage='explicit-retry';await send(port,text);await until(()=>requests.length===1,'重新发送');const payload=JSON.stringify(requests[0].payload.messages);for(const body of bodies)assert.ok(payload.includes(body),'完整附件');assert.equal(requests[0].payload.max_tokens,512);
 let completed=await until(async()=>{const w=await invoke(port,'CHAT','read_desktop_workspace',{workspaceId});return w.exchange.conversations.find(c=>c.messages.some(m=>m.role==='user'&&m.blocks.some(b=>b.kind==='TEXT'&&b.text===text))&&c.messages.some(m=>m.role==='assistant'&&m.delivery==='COMPLETE'));},'回答完成');result.conversationId=completed.id;result.completedFirst=completed;const user=completed.messages.find(m=>m.role==='user');assert.deepEqual(user.blocks.filter(b=>b.kind==='ASSET_REF').map(b=>b.asset.id).sort(),draft.attachments.map(a=>a.id).sort());
 const consumed=await invoke(port,'CHAT','read_desktop_conversation_draft',{workspaceId,conversationId:completed.id});assert.ok(!consumed?.text&&!consumed?.attachments?.length);result.firstRequest={model:requests[0].payload.model,fullBodies:true,consumedDraft:true};
 stage='continuation';await send(port,'新的普通问题 FOLLOWUP_NO_ATTACHMENTS');await until(()=>requests.length===2,'后续');const following=JSON.stringify(requests[1].payload.messages);for(const marker of ['ATTACHMENT_A_BEGIN','ATTACHMENT_B_BEGIN','data:text/plain','合成预算 0.txt','合成预算 1.txt'])assert.ok(!following.includes(marker));
 completed=await until(async()=>{const w=await invoke(port,'CHAT','read_desktop_workspace',{workspaceId});const c=w.exchange.conversations.find(c=>c.id===result.conversationId);return c?.messages.filter(m=>m.role==='assistant'&&m.delivery==='COMPLETE').length===2?c:null;},'后续完成');result.completedFinal=completed;result.secondRequest={noOldAttachmentBytes:true,model:requests[1].payload.model};result.contextRecords=await invoke(port,'CHAT','read_desktop_ordinary_chat_context_records');result.beforeCold=await invoke(port,'CHAT','read_desktop_workspace',{workspaceId});
 result.normalCloses.push(await normalClose());port=await start(true);await ready(port);stage='cold-completed';assert.deepEqual(await invoke(port,'CHAT','read_desktop_workspace',{workspaceId}),result.beforeCold);assert.deepEqual(await invoke(port,'CHAT','read_desktop_ordinary_chat_context_records'),result.contextRecords);assert.equal(requests.length,2);assert.ok(!(await invoke(port,'WORK','list_desktop_workspaces')).some(w=>w.id===workspaceId));result.normalCloses.push(await normalClose());result.pass=true;
}catch(error){result.error=String(error.stack||error);result.stage=stage;if(launch){try{const tabs=await fetch(`http://127.0.0.1:${launch.debugPort}/json/list`).then(r=>r.json());result.debugTabs=tabs.map(t=>({url:t.url,type:t.type}));}catch{}}process.exitCode=1;}
finally{if(launch){result.forcedCleanup=true;stop();}provider.closeAllConnections();await new Promise(done=>provider.close(done));result.pids=pids;if(root)await writeFile(join(root,'attachment-budget-evidence.json'),JSON.stringify(result,null,2),{flag:'wx'});}
console.log(JSON.stringify({candidateSha256:receipt.sha256,root,pass:result.pass,stage:result.stage,error:result.error,pids}));
