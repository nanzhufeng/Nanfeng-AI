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


const noReopen=process.argv.includes('--no-reopen');const requests=[],serverErrors=[];
const firstText='这是退出等待期间已经收到的合成内容。';const tailText='这是等待撤销后完成的合成内容。';
let releaseFirst;const firstGate=new Promise(done=>releaseFirst=done);
const provider=http.createServer(async(req,res)=>{try{
 let raw='';for await(const chunk of req)raw+=chunk.toString('utf8');const body=JSON.parse(raw);
 assert.equal(req.headers.authorization,'Bearer local-acceptance-mock-only');assert.equal(body.model,'openai/gpt-6.1-sol');requests.push({model:body.model});assert.ok(requests.length<=2);
 res.writeHead(200,{'Content-Type':'text/event-stream; charset=utf-8'});
 const event=value=>res.write(`data: ${JSON.stringify(value)}\n\n`);
 if(requests.length===1){event({model:body.model,choices:[{delta:{content:firstText}}]});res.flushHeaders();await firstGate;event({model:body.model,choices:[{delta:{content:tailText}}]});}
 else event({model:body.model,choices:[{delta:{content:'重新进入后提交的新工作正常完成。'}}]});
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







function watchExit() {
 let readyResolve,readyReject,doneResolve,doneReject;const ready=new Promise((r,j)=>{readyResolve=r;readyReject=j;});const done=new Promise((r,j)=>{doneResolve=r;doneReject=j;});ready.catch(()=>{});done.catch(()=>{});
 const child=spawn('pwsh',['-NoProfile','-File',join(desktopRoot,'scripts/wait-windows-owned-process-exit.ps1'),'-ProcessId',String(launch.pid),'-Executable',runExecutable,'-Sha256',receipt.sha256,'-StartedAt',launch.processStartedAt],{windowsHide:true,stdio:['ignore','pipe','pipe']});
 let buffer='',errors='',code=null;const timer=setTimeout(()=>{child.kill();readyReject(new Error('exit observer timeout'));doneReject(new Error('exit observer timeout'));},70000);
 child.stdout.on('data',data=>{buffer+=data.toString('utf8');const lines=buffer.split(/\r?\n/);buffer=lines.pop();for(const line of lines){if(!line.trim())continue;try{const value=JSON.parse(line);if(value.ready)readyResolve();if(value.exitCode!==undefined)code=value.exitCode;}catch{}}});
 child.stderr.on('data',data=>{errors=(errors+data.toString('utf8')).slice(-2048);});
 child.on('error',error=>{clearTimeout(timer);readyReject(error);doneReject(error);});child.on('exit',status=>{clearTimeout(timer);if(status===0&&code!==null)doneResolve(code);else{const error=new Error(`exit observer failed ${status}: ${errors}`);readyReject(error);doneReject(error);}});
 return {ready,done};
}
async function reopenHiddenInstance() {
 const env={};for(const name of ['SystemRoot','WINDIR','PATH','USERPROFILE','LOCALAPPDATA','APPDATA'])if(process.env[name])env[name]=process.env[name];
 Object.assign(env,{NANFENG_AI_WINDOWS_ACCEPTANCE_ROOT:root,WEBVIEW2_USER_DATA_FOLDER:launch.webviewProfile,NANFENG_AI_WINDOWS_ORDINARY_CHAT_TEST_ENDPOINT:`http://127.0.0.1:${providerPort}`,TEMP:rootParent,TMP:rootParent});
 const child=spawn(runExecutable,['--windows-isolated-normal-startup'],{env,windowsHide:true,stdio:['ignore','pipe','pipe']});let errorText='';child.stderr.on('data',data=>{errorText=(errorText+data.toString('utf8')).slice(-2048);});child.stdout.resume();
 const code=await new Promise((done,reject)=>{const timer=setTimeout(()=>reject(new Error('second instance did not exit')),15000);child.on('exit',code=>{clearTimeout(timer);done(code);});child.on('error',error=>{clearTimeout(timer);reject(error);});});
 assert.equal(code,0,`second instance: ${errorText}`);return {pid:child.pid,exitCode:code};
}
const readStages=async()=>JSON.parse(await readFile(join(root,'generation-continuity-v1.json'),'utf8')).map(e=>e.stage);
const authorization=p=>({approvedAtMs:Date.now(),disclosureVersion:'normal-chat-egress-v2',clientSubmissionId:randomUUID(),draftFingerprint:p.draftFingerprint,recipientChoiceId:p.recipientChoiceId,recipientProviderId:p.recipientProviderId,recipientModelId:p.recipientModelId,retrievalRecipient:p.retrievalRecipient});
const textOf=m=>(m.blocks||[]).filter(b=>b.kind==='TEXT').map(b=>b.text||'').join('');

const result={candidateSha256:receipt.sha256,noReopen,realService:false,backgroundOnly:true};let port=0,observer=null;
try{
 port=await start();result.root=root;await ready(port);
 const ws=(await invoke(port,'CHAT','ensure_desktop_local_workspace')).summary.id;
 const setting=(await invoke(port,'CHAT','read_desktop_model_service_settings')).find(p=>p.providerId==='OPENROUTER');
 await invoke(port,'CHAT','save_desktop_model_service_settings',{args:{providerId:'OPENROUTER',enabled:true,presetId:'GPT_6_1_SOL',expectedRevision:setting.revision,apiKey:'nfai-synthetic-exit-drain'}});
 const conv=(await invoke(port,'CHAT','read_desktop_workspace',{workspaceId:ws})).exchange.conversations[0];
 const selection=await invoke(port,'CHAT','read_desktop_p6g_selection',{workspaceId:ws,conversationId:conv.id});
 await invoke(port,'CHAT','set_desktop_p6g_conversation_override',{args:{workspaceId:ws,conversationId:conv.id,expectedRevision:selection.conversationOverride.revision,modelId:'GPT_6_1_SOL'}});
 const current=await invoke(port,'CHAT','read_desktop_p6g_selection',{workspaceId:ws,conversationId:conv.id});
 await invoke(port,'CHAT','set_desktop_conversation_preferences',{args:{workspaceId:ws,conversationId:conv.id,expectedRevision:current.conversationOverride.revision,toneOverride:null,webSearchOverride:false}});
 const text='退出等待合成问题';const preview=await invoke(port,'CHAT','preview_desktop_ordinary_chat_egress',{args:{workspaceId:ws,conversationId:conv.id,text,attachmentIds:[],modelId:null,webSearchOverride:null}});
 const args={args:{workspaceId:ws,conversationId:conv.id,expectedRevision:conv.revision,text,attachmentIds:[],modelId:null,egressAuthorization:authorization(preview)}};
 await evaluate(port,`(()=>{window.__nfaiExitResult=null;window.__TAURI__.core.invoke('submit_desktop_ordinary_chat',${JSON.stringify(args)},{headers:{'x-nanfeng-data-area':'CHAT'}}).then(value=>window.__nfaiExitResult=value,error=>window.__nfaiExitError=String(error));return true;})()`);
 await until(async()=>{if(requests.length!==1)return false;const data=await invoke(port,'CHAT','read_desktop_workspace',{workspaceId:ws});return data.exchange.conversations.find(c=>c.id===conv.id).messages.some(m=>textOf(m)===firstText);},'实际在途生成');result.phase='prefix-persisted';
 const closed=JSON.parse(ps(['-File',join(desktopRoot,'scripts/close-windows-hidden-acceptance.ps1'),'-ProcessId',String(launch.pid),'-Executable',runExecutable,'-Sha256',receipt.sha256,'-StartedAt',launch.processStartedAt]));assert.equal(closed.closePosted,true);result.phase="close-posted";
 await new Promise(done=>setTimeout(done,500));
 assert.equal(await evaluate(port,'Boolean(window.__TAURI__?.core?.invoke)'),true,'active close must retain the WebView');
 await until(async()=>(await readStages()).includes('EXIT_DRAIN_BEGIN'),'实际退出等待登记');result.retainedWindow=true;
 observer=watchExit();await observer.ready;
 if(!noReopen){
   result.reopen=await reopenHiddenInstance();await until(async()=>(await readStages()).includes('EXIT_DRAIN_REVOKED'),'实际单实例撤销退出');
 }
 releaseFirst();
 if(noReopen){
   result.exitCode=await observer.done;assert.equal(result.exitCode,0);pids.push(launch.pid);launch=null;
 }else{
   await until(()=>evaluate(port,"window.__nfaiExitResult?.state==='COMPLETED'"),'旧任务正常完成');
   await new Promise(done=>setTimeout(done,500));assert.equal(await evaluate(port,'Boolean(window.__TAURI__?.core?.invoke)'),true);
   const live=(await invoke(port,'CHAT','read_desktop_workspace',{workspaceId:ws})).exchange.conversations.find(c=>c.id===conv.id);
   const next='重新进入后的新问题';const p=await invoke(port,'CHAT','preview_desktop_ordinary_chat_egress',{args:{workspaceId:ws,conversationId:conv.id,text:next,attachmentIds:[],modelId:null,webSearchOverride:null}});
   const nextResult=await invoke(port,'CHAT','submit_desktop_ordinary_chat',{args:{workspaceId:ws,conversationId:conv.id,expectedRevision:live.revision,text:next,attachmentIds:[],modelId:null,egressAuthorization:authorization(p)}});assert.equal(nextResult.state,'COMPLETED');result.newWorkCompleted=true;
   result.close=await normalClose();result.exitCode=await observer.done;assert.equal(result.exitCode,0);
 }
 port=await start(true);await ready(port);const cold=(await invoke(port,'CHAT','read_desktop_workspace',{workspaceId:ws})).exchange.conversations.find(c=>c.id===conv.id);
 assert.ok(cold.messages.some(m=>textOf(m)===firstText+tailText));assert.equal(requests.length,noReopen?1:2);result.coldClose=await normalClose();assert.equal(serverErrors.length,0);result.pass=true;
}catch(error){result.pass=false;result.error=String(error);process.exitCode=1;}
finally{releaseFirst();if(launch){result.forcedCleanup=true;try{stop();}catch(error){result.cleanupError=String(error);}}if(observer)await observer.done.catch(()=>{});provider.closeAllConnections();await new Promise(done=>provider.close(done));result.requests=requests;result.serverErrors=serverErrors;result.pids=pids;if(root)await writeFile(join(root,'a6-exit-drain-native-evidence.json'),JSON.stringify(result,null,2),{flag:'wx'});}
console.log(JSON.stringify({root,pass:result.pass,error:result.error,phase:result.phase,requests:requests.length,pids}));
