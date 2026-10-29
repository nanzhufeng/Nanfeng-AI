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
assert.ok(process.argv[2]);assert.ok(rootParent.startsWith(resolve('D:/CodexTemp')+sep));
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



const requests=[],serverErrors=[],closed=[];
const prefixes=['CHAT 原始合成断点，保留原文并核对任务身份。','CHAT 续写新增合成片段。','WORK 并行生成合成片段。'];
const suffix='后续合成结尾。';const releases=[];const gates=[0,1].map(i=>new Promise(done=>releases[i]=done));
const provider=http.createServer(async(req,res)=>{try{
 let raw='';for await(const chunk of req)raw+=chunk.toString('utf8');const body=JSON.parse(raw);
 assert.equal(req.headers.authorization,'Bearer local-acceptance-mock-only');assert.equal(body.model,'openai/gpt-6.1-sol');
 const index=requests.length;assert.ok(index<3);requests.push({model:body.model,idempotencyKey:req.headers['idempotency-key']});res.on('error',()=>{});res.on('close',()=>{if(!res.writableEnded)closed.push(index);});
 res.writeHead(200,{'Content-Type':'text/event-stream; charset=utf-8'});const event=v=>res.write(`data: ${JSON.stringify(v)}\n\n`);
 event({model:body.model,choices:[{delta:{content:prefixes[index]}}]});res.flushHeaders();
 if(index===0){setTimeout(()=>res.destroy(),100);return;}
 if(index===1)assert.ok(body.messages.some(m=>m.role==='assistant'&&m.content===prefixes[0]));
 await gates[index-1];if(res.destroyed)return;
 event({model:body.model,choices:[{delta:{content:suffix}}]});res.end(`data: ${JSON.stringify({model:body.model,choices:[{delta:{},finish_reason:'stop'}],usage:{prompt_tokens:8,completion_tokens:12,cost:0.000004}})}`);
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








async function islandEval(port,expression){return(await cdp(port,'Runtime.evaluate',{expression,awaitPromise:true,returnByValue:true},'island'))?.result?.value;}
async function islandInvoke(port,command,args={}){const value=await islandEval(port,`window.__TAURI__.core.invoke(${JSON.stringify(command)},${JSON.stringify(args)},{headers:{"x-nanfeng-data-area":"CHAT"}}).catch(error=>({__error:String(error)}))`);if(value?.__error)throw new Error(value.__error);return value;}
const auth=p=>({approvedAtMs:Date.now(),disclosureVersion:'normal-chat-egress-v2',clientSubmissionId:randomUUID(),draftFingerprint:p.draftFingerprint,recipientChoiceId:p.recipientChoiceId,recipientProviderId:p.recipientProviderId,recipientModelId:p.recipientModelId,retrievalRecipient:p.retrievalRecipient});
const textOf=m=>(m.blocks||[]).filter(b=>b.kind==='TEXT').map(b=>b.text||'').join('');
async function assertHidden(){const data=JSON.parse(ps(['-File',join(desktopRoot,'scripts/inspect-windows-island-acceptance.ps1'),'-ProcessId',String(launch.pid),'-Executable',runExecutable,'-Sha256',receipt.sha256,'-StartedAt',launch.processStartedAt]));result.lastWindowInspection=data;assert.equal(data.foregroundOwned,false);assert.equal(data.unexpectedVisibleOwnedWindowCount,0);assert.equal(data.windows.length,2);assert.ok(data.windows.every(w=>!w.visible));const island=data.windows.find(w=>w.title==='南枫岛');assert.ok(island.noActivate&&island.toolWindow&&!island.appWindow);return data;}
async function clickTask(port,key,action){const selector=`[data-island-action="${action}"][data-island-key="${key}"]`;await until(async()=>await islandEval(port,`(()=>{let button=document.querySelector(${JSON.stringify(selector)});if(!button){document.querySelector('.island-summary')?.click();button=document.querySelector(${JSON.stringify(selector)});}if(!button)return false;button.click();return true;})()`),'状态岛实际任务按键');}

const result={candidateSha256:receipt.sha256,backgroundOnly:true,realService:false};let port=0;
async function seed(area){
 const ws=(await invoke(port,area,'ensure_desktop_local_workspace')).summary.id;
 const setting=(await invoke(port,area,'read_desktop_model_service_settings')).find(p=>p.providerId==='OPENROUTER');
 await invoke(port,area,'save_desktop_model_service_settings',{args:{providerId:'OPENROUTER',enabled:true,presetId:'GPT_6_1_SOL',expectedRevision:setting.revision,apiKey:'nfai-synthetic-island-retry-area'}});
 const made=await invoke(port,area,'mutate_desktop_domain',{args:{workspaceId:ws,intentId:randomUUID(),entity:'conversation',action:'create',fields:{title:area+' 合成任务身份',firstMessage:'仅限本机隔离验收',projectId:null}}});
 const id=made.objectId;const selected=await invoke(port,area,'read_desktop_p6g_selection',{workspaceId:ws,conversationId:id});
 await invoke(port,area,'set_desktop_p6g_conversation_override',{args:{workspaceId:ws,conversationId:id,expectedRevision:selected.conversationOverride.revision,modelId:'GPT_6_1_SOL'}});
 const current=await invoke(port,area,'read_desktop_p6g_selection',{workspaceId:ws,conversationId:id});
 await invoke(port,area,'set_desktop_conversation_preferences',{args:{workspaceId:ws,conversationId:id,expectedRevision:current.conversationOverride.revision,toneOverride:null,webSearchOverride:false}});
 return {area,ws,id};
}
async function submission(target){
 const c=(await invoke(port,target.area,'read_desktop_workspace',{workspaceId:target.ws})).exchange.conversations.find(c=>c.id===target.id);
 const text=target.area+' 独立任务合成问题';const p=await invoke(port,target.area,'preview_desktop_ordinary_chat_egress',{args:{workspaceId:target.ws,conversationId:target.id,text,attachmentIds:[],modelId:null,webSearchOverride:null}});
 return {args:{workspaceId:target.ws,conversationId:target.id,expectedRevision:c.revision,text,attachmentIds:[],modelId:null,egressAuthorization:auth(p)}};
}
async function launchCommand(name,area,command,args){await evaluate(port,`(()=>{window[${JSON.stringify(name)}]=null;window.__TAURI__.core.invoke(${JSON.stringify(command)},${JSON.stringify(args)},{headers:{'x-nanfeng-data-area':${JSON.stringify(area)}}}).then(value=>window[${JSON.stringify(name)}]=value,error=>window[${JSON.stringify(name+'Error')}]=String(error));return true;})()`);}
async function stored(t){return (await invoke(port,t.area,'read_desktop_workspace',{workspaceId:t.ws})).exchange.conversations.find(c=>c.id===t.id);}
try{
 port=await start();result.root=root;await ready(port);result.runtimeInfo=await invoke(port,'CHAT','read_desktop_runtime_info');assert.equal(result.runtimeInfo.buildProfile,expectedBuildProfile);result.phase='ready';
 await until(async()=>{try{return Array.isArray(await islandInvoke(port,'read_generation_island'));}catch{return false;}},'隐藏岛IPC');result.initialWindows=await assertHidden();
 const chat=await seed('CHAT'),work=await seed('WORK');result.targets={chat,work};
 let denied=false;try{await invoke(port,'CHAT','read_generation_island');}catch(e){denied=/not allowed|permission|denied|只允许/i.test(String(e));result.mainDenial=String(e);}assert.ok(denied);
 for(const command of ['read_desktop_workspace','read_desktop_model_service_settings']){denied=false;try{await islandInvoke(port,command,command==='read_desktop_workspace'?{workspaceId:chat.ws}:{});}catch(e){denied=/not allowed|permission|denied|只允许/i.test(String(e));}assert.ok(denied);}
 result.aclDenied=true;
 const first=await invoke(port,'CHAT','submit_desktop_ordinary_chat',await submission(chat));assert.equal(first.state,'UNKNOWN');result.first=first;
 const old=await until(async()=>(await islandInvoke(port,'read_generation_island')).find(t=>t.assistantMessageId===first.assistantMessageId),'原任务终态');assert.equal(old.state,'UNKNOWN');
 const p=await invoke(port,'CHAT','preview_desktop_ordinary_chat_retry_egress',{args:{attemptId:first.attemptId}});
 await launchCommand('__a7Retry','CHAT','retry_desktop_ordinary_chat',{args:{attemptId:first.attemptId,egressAuthorization:auth(p)}});
 const retry=await until(async()=>(await islandInvoke(port,'read_generation_island')).find(t=>t.attemptId===first.attemptId&&t.assistantMessageId!==first.assistantMessageId&&t.state==='RUNNING'&&t.savedCharacters===Array.from(prefixes[1]).length),'同Attempt的新回答运行');
 assert.notEqual(retry.key,old.key);assert.equal(retry.dataArea,'CHAT');assert.equal(retry.workspaceId,chat.ws);assert.equal(retry.conversationId,chat.id);
 await launchCommand('__a7Work','WORK','submit_desktop_ordinary_chat',await submission(work));
 const parallel=await until(async()=>(await islandInvoke(port,'read_generation_island')).find(t=>t.dataArea==='WORK'&&t.workspaceId===work.ws&&t.conversationId===work.id&&t.state==='RUNNING'&&t.savedCharacters===Array.from(prefixes[2]).length),'WORK并行任务');
 for(const task of [old,retry,parallel]){assert.equal(Object.keys(task).length,11);assert.ok(!prefixes.some(text=>JSON.stringify(task).includes(text)));}
 result.tasks={old,retry,parallel};result.phase='parallel-running';
 await assert.rejects(islandInvoke(port,'generation_island_action',{key:old.key,action:'stop'}),/任务已结束|失效/);
 let tasks=await islandInvoke(port,'read_generation_island');assert.equal(tasks.find(t=>t.key===retry.key).state,'RUNNING');assert.equal(tasks.find(t=>t.key===parallel.key).state,'RUNNING');result.oldStopRejected=true;
 await clickTask(port,retry.key,'open');await until(()=>evaluate(port,`window.__nanfengDesktopWorkState.dataArea==='CHAT'&&window.__nanfengDesktopWorkState.selectedConversationId===${JSON.stringify(chat.id)}`),'查看准确CHAT重试回答');result.openedChatExactly=true;result.chatWindows=await assertHidden();
 await evaluate(port,"(()=>{const i=document.querySelector('#chat-composer');i.value='A7_SCOPE_CURRENT_UNSENT';i.dispatchEvent(new InputEvent('input',{bubbles:true}));return true;})()");
 await clickTask(port,retry.key,'stop');await until(()=>evaluate(port,"window.__a7Retry?.state==='CANCELLED'"),'只停止CHAT新回答');await until(()=>closed.includes(1),'CHAT续写连接关闭');
 tasks=await islandInvoke(port,'read_generation_island');assert.equal(tasks.find(t=>t.key===parallel.key).state,'RUNNING');assert.equal(await evaluate(port,'window.__a7Work'),null);result.workSurvivedChatStop=true;result.cancelled=await evaluate(port,'window.__a7Retry');
 await until(()=>islandEval(port,`document.querySelector('[data-island-action="open"][data-island-key="${parallel.key}"]')?.disabled===true&&document.body.textContent.includes('工作区查看入口已隐藏')`),'WORK查看禁用且保留说明');result.workViewDisabled=true;
 await islandInvoke(port,'generation_island_action',{key:parallel.key,action:'open'});
 await until(()=>evaluate(port,"document.body.textContent.includes('工作区查看入口已隐藏，任务和数据仍保留。')"),'陈旧WORK查看明确拒绝');
 assert.equal(await evaluate(port,`window.__nanfengDesktopWorkState.dataArea==='CHAT'&&window.__nanfengDesktopWorkState.selectedConversationId===${JSON.stringify(chat.id)}&&document.querySelector('#chat-composer')?.value==='A7_SCOPE_CURRENT_UNSENT'`),true);result.staleWorkOpenRejected=true;result.currentInputPreserved=true;result.openWindows=await assertHidden();
 releases[1]();await until(()=>evaluate(port,"window.__a7Work?.state==='COMPLETED'"),'WORK正常完成');result.completed=await evaluate(port,'window.__a7Work');
 const a=await stored(chat),b=await stored(work);assert.equal(textOf(a.messages.find(m=>m.id===first.assistantMessageId)),prefixes[0]);assert.equal(textOf(a.messages.find(m=>m.id===retry.assistantMessageId)),prefixes[1]);assert.equal(textOf(b.messages.find(m=>m.id===parallel.assistantMessageId)),prefixes[2]+suffix);
 assert.equal(requests.length,3);assert.equal(serverErrors.length,0);assert.equal(new Set(requests.map(r=>r.idempotencyKey)).size,3);result.close=await normalClose();
 port=await start(true);await ready(port);await until(async()=>{try{return Array.isArray(await islandInvoke(port,'read_generation_island'));}catch{return false;}},'冷启岛IPC');assert.deepEqual(await islandInvoke(port,'read_generation_island'),[]);
 assert.deepEqual((await stored(chat)).messages,a.messages);assert.deepEqual((await stored(work)).messages,b.messages);assert.equal((await invoke(port,'CHAT','read_desktop_conversation_draft',{workspaceId:chat.ws,conversationId:chat.id})).text,'A7_SCOPE_CURRENT_UNSENT');assert.equal(requests.length,3);result.coldReadback=true;result.coldWindows=await assertHidden();result.coldClose=await normalClose();result.pass=true;result.phase='closed';
}catch(error){result.pass=false;result.error=String(error);result.stack=error.stack;process.exitCode=1;}
finally{releases.forEach(done=>done());if(launch){try{result.failureClose=await normalClose();}catch(e){result.closeError=String(e);try{stop();result.forcedCleanup=true;}catch(e){result.cleanupError=String(e);}}}provider.closeAllConnections();await new Promise(done=>provider.close(done));result.requests=requests;result.serverErrors=serverErrors;result.closedRequests=closed;result.pids=pids;if(root)await writeFile(join(root,'a7-retry-area-native-evidence.json'),JSON.stringify(result,null,2),{flag:'wx'});}
console.log(JSON.stringify({pass:result.pass,error:result.error,phase:result.phase,root,pids,requests:requests.length}));
