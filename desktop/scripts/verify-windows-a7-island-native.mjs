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



const requests=[],serverErrors=[];const prefixes=['第一项已持久保存的合成正文。','第二项已持久保存的合成正文。'];const suffix='后续合成正文正常完成。';
const releases=[];const gates=[0,1].map(i=>new Promise(done=>releases[i]=done));
const provider=http.createServer(async(req,res)=>{try{
 let raw='';for await(const chunk of req)raw+=chunk.toString('utf8');const body=JSON.parse(raw);assert.equal(req.headers.authorization,'Bearer local-acceptance-mock-only');assert.equal(body.model,'openai/gpt-6.1-sol');
 const index=requests.length;assert.ok(index<2);requests.push({model:body.model});res.on('error',()=>{});
 res.writeHead(200,{'Content-Type':'text/event-stream; charset=utf-8'});const event=value=>res.write(`data: ${JSON.stringify(value)}\n\n`);event({model:body.model,choices:[{delta:{content:prefixes[index]}}]});res.flushHeaders();await gates[index];if(res.destroyed)return;
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
async function assertHidden(){const data=JSON.parse(ps(['-File',join(desktopRoot,'scripts/inspect-windows-island-acceptance.ps1'),'-ProcessId',String(launch.pid),'-Executable',runExecutable,'-Sha256',receipt.sha256,'-StartedAt',launch.processStartedAt]));result.lastWindowInspection=data;assert.equal(data.foregroundOwned,false);assert.equal(data.windows.length,2);assert.ok(data.windows.every(w=>!w.visible));const island=data.windows.find(w=>w.title==='南枫岛');assert.ok(island.noActivate&&island.toolWindow&&!island.appWindow);return data;}
async function clickTask(port,key,action){const selector=`[data-island-action="${action}"][data-island-key="${key}"]`;await until(async()=>await islandEval(port,`(()=>{let button=document.querySelector(${JSON.stringify(selector)});if(!button){document.querySelector('.island-summary')?.click();button=document.querySelector(${JSON.stringify(selector)});}if(!button)return false;button.click();return true;})()`),'状态岛实际任务按键');}

const result={candidateSha256:receipt.sha256,backgroundOnly:true,realService:false};let port=0;
try{
 port=await start();result.root=root;await ready(port);result.phase='main-ready';
 const deadline=Date.now()+8000;let islandReady=false;while(Date.now()<deadline){try{await islandInvoke(port,'read_generation_island');islandReady=true;break;}catch{}await new Promise(done=>setTimeout(done,200));}assert.ok(islandReady,'ISLAND_NOT_AVAILABLE');result.phase='island-ready';
 result.initialWindows=await assertHidden();assert.deepEqual(await islandInvoke(port,'read_generation_island'),[]);
 const ws=(await invoke(port,'CHAT','ensure_desktop_local_workspace')).summary.id;
 let denied=false;try{await invoke(port,'CHAT','read_generation_island');}catch(error){denied=/not allowed|permission|denied|只允许/i.test(String(error));result.mainRejection=String(error);}assert.equal(denied,true);result.mainDeniedIslandMetadata=true;
 denied=false;try{await islandInvoke(port,'read_desktop_workspace',{workspaceId:ws});}catch(error){denied=/not allowed|permission|denied|只允许/i.test(String(error));result.islandRejection=String(error);}assert.equal(denied,true);result.islandDeniedBusinessRead=true;
 const setting=(await invoke(port,'CHAT','read_desktop_model_service_settings')).find(p=>p.providerId==='OPENROUTER');await invoke(port,'CHAT','save_desktop_model_service_settings',{args:{providerId:'OPENROUTER',enabled:true,presetId:'GPT_6_1_SOL',expectedRevision:setting.revision,apiKey:'nfai-synthetic-island-only'}});
 const conv=(await invoke(port,'CHAT','read_desktop_workspace',{workspaceId:ws})).exchange.conversations[0];const selection=await invoke(port,'CHAT','read_desktop_p6g_selection',{workspaceId:ws,conversationId:conv.id});await invoke(port,'CHAT','set_desktop_p6g_conversation_override',{args:{workspaceId:ws,conversationId:conv.id,expectedRevision:selection.conversationOverride.revision,modelId:'GPT_6_1_SOL'}});
 const current=await invoke(port,'CHAT','read_desktop_p6g_selection',{workspaceId:ws,conversationId:conv.id});await invoke(port,'CHAT','set_desktop_conversation_preferences',{args:{workspaceId:ws,conversationId:conv.id,expectedRevision:current.conversationOverride.revision,toneOverride:null,webSearchOverride:false}});
 async function submit(index){const live=(await invoke(port,'CHAT','read_desktop_workspace',{workspaceId:ws})).exchange.conversations.find(c=>c.id===conv.id);const text=`状态岛合成问题${index}`;const p=await invoke(port,'CHAT','preview_desktop_ordinary_chat_egress',{args:{workspaceId:ws,conversationId:conv.id,text,attachmentIds:[],modelId:null,webSearchOverride:null}});const args={args:{workspaceId:ws,conversationId:conv.id,expectedRevision:live.revision,text,attachmentIds:[],modelId:null,egressAuthorization:auth(p)}};
  await evaluate(port,`(()=>{window.__nfaiIslandResult${index}=null;window.__TAURI__.core.invoke('submit_desktop_ordinary_chat',${JSON.stringify(args)},{headers:{'x-nanfeng-data-area':'CHAT'}}).then(value=>window.__nfaiIslandResult${index}=value,error=>window.__nfaiIslandError${index}=String(error));return true;})()`);
  return await until(async()=>{const tasks=await islandInvoke(port,'read_generation_island');return tasks.find(t=>t.state==='RUNNING'&&t.savedCharacters===Array.from(prefixes[index]).length)||false;},'实际生成的已保存字数');
 }
 const first=await submit(0);result.phase='first-running';assert.equal(first.dataArea,'CHAT');assert.equal(first.workspaceId,ws);assert.equal(first.conversationId,conv.id);assert.ok(first.assistantMessageId&&first.attemptId);assert.equal(Object.keys(first).length,11);assert.ok(!JSON.stringify(first).includes(prefixes[0]));result.first=first;
 result.hiddenOutsideClick=await islandInvoke(port,'generation_island_visibility',{visible:true,height:420,expanded:true});
 assert.deepEqual(result.hiddenOutsideClick,{outsideClick:'inactive'},'隐藏验收不得启用用户桌面的全局鼠标监听');
 result.runningWindows=await assertHidden();
 await clickTask(port,first.key,'stop');await until(()=>evaluate(port,"window.__nfaiIslandResult0?.state==='CANCELLED'"),'只停止选中任务');releases[0]();result.phase='first-cancelled';
 const second=await submit(1);assert.notEqual(second.key,first.key);result.second=second;
 let oldStopRejected=false;try{await islandInvoke(port,'generation_island_action',{key:first.key,action:'stop'});}catch{oldStopRejected=true;}assert.equal(oldStopRejected,true);assert.equal((await islandInvoke(port,'read_generation_island')).find(t=>t.key===second.key).state,'RUNNING');result.oldStopRejected=true;result.phase='second-running';
 await evaluate(port,`(()=>{const button=[...document.querySelectorAll('[data-action="new-chat"]')].find(n=>n.getClientRects().length&&!n.disabled);if(!button)throw new Error('new-chat missing');button.click();return true;})()`);
 await until(()=>evaluate(port,`!document.body.textContent.includes(${JSON.stringify(prefixes[1])})`),'离开任务对话');
 const closePosted=JSON.parse(ps(['-File',join(desktopRoot,'scripts/close-windows-hidden-acceptance.ps1'),'-ProcessId',String(launch.pid),'-Executable',runExecutable,'-Sha256',receipt.sha256,'-StartedAt',launch.processStartedAt]));assert.equal(closePosted.closePosted,true);result.activeClose=closePosted;
 await until(async()=>JSON.parse(await readFile(join(root,'generation-continuity-v1.json'),'utf8')).some(e=>e.stage==='EXIT_DRAIN_BEGIN'),'活跃关闭登记退出等待');
 await clickTask(port,second.key,'open');await until(()=>evaluate(port,`document.body.textContent.includes(${JSON.stringify(prefixes[1])})`),'状态岛回到准确本机对话');result.openedExactConversation=true;result.openWindows=await assertHidden();result.phase='opened';
 await until(async()=>JSON.parse(await readFile(join(root,'generation-continuity-v1.json'),'utf8')).some(e=>e.stage==='EXIT_DRAIN_REVOKED'),'状态岛查看撤销旧退出等待');result.oldDrainRevoked=true;
 releases[1]();await until(()=>evaluate(port,"window.__nfaiIslandResult1?.state==='COMPLETED'"),'新运行完成');const tasks=await islandInvoke(port,'read_generation_island');const done=tasks.find(t=>t.key===second.key);assert.ok(['COMPLETED','COMPLETED_ACCOUNTING_PENDING','RESPONSE_COMMITTED'].includes(done.state));assert.equal(done.savedCharacters,Array.from(prefixes[1]+suffix).length);result.finalTasks=tasks;
 const stored=(await invoke(port,'CHAT','read_desktop_workspace',{workspaceId:ws})).exchange.conversations.find(c=>c.id===conv.id);assert.ok(stored.messages.some(m=>textOf(m)===prefixes[0]));assert.ok(stored.messages.some(m=>textOf(m)===prefixes[1]+suffix));assert.equal(requests.length,2);assert.equal(serverErrors.length,0);result.close=await normalClose();result.phase='closed';
 port=await start(true);await ready(port);await until(async()=>{try{return Array.isArray(await islandInvoke(port,'read_generation_island'));}catch{return false;}},'冷启状态窗口');assert.deepEqual(await islandInvoke(port,'read_generation_island'),[]);const cold=(await invoke(port,'CHAT','read_desktop_workspace',{workspaceId:ws})).exchange.conversations.find(c=>c.id===conv.id);assert.ok(cold.messages.some(m=>textOf(m)===prefixes[1]+suffix));result.coldWindows=await assertHidden();result.coldClose=await normalClose();result.pass=true;
}catch(error){result.pass=false;result.error=String(error);process.exitCode=1;}
finally{releases.forEach(done=>done());if(launch){if(requests.length===0){try{result.failureClose=await normalClose();}catch{}}if(launch){result.forcedCleanup=true;try{stop();}catch(error){result.cleanupError=String(error);}}}provider.closeAllConnections();await new Promise(done=>provider.close(done));result.requests=requests;result.serverErrors=serverErrors;result.pids=pids;if(root)await writeFile(join(root,'a7-island-native-evidence.json'),JSON.stringify(result,null,2),{flag:'wx'});}
console.log(JSON.stringify({root,pass:result.pass,error:result.error,phase:result.phase,requests:requests.length,pids}));
