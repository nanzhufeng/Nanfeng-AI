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
const held=[];
const provider=http.createServer(async(request,response)=>{
  let body='';for await(const chunk of request)body+=chunk.toString('utf8');
  const payload=JSON.parse(body);
  requests.push({path:request.url,model:payload.model,smallProbeOnly:payload.messages?.length===1 && payload.messages[0].content==='hi' && payload.max_tokens===1 && payload.stream===false,syntheticBearerOnly:String(request.headers.authorization||'').startsWith('Bearer nfai-synthetic-model-')});
  held.push(response);
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
    '-StartupMode', 'normal', '-Visible', '-DebugPort', String(port), '-ModelServiceTestEndpoint', `http://127.0.0.1:${providerPort}`];
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
let stage='start', activePort;
const key=provider=>`nfai-synthetic-model-${provider}`;
async function openModel(port,providerId){
 await until(()=>evaluate(port,'Boolean(document.querySelector("[data-action=show-settings]"))'),'设置入口');
 await clickUi(port,'[data-action="show-settings"]');
 await until(()=>evaluate(port,'Boolean(document.querySelector("[data-action=open-settings-page][data-page=model]"))'),'模型与联网入口');
 await clickUi(port,'[data-action="open-settings-page"][data-page="model"]');
 await until(()=>evaluate(port,'Boolean(document.querySelector("[data-action=open-settings-page][data-page=model-configuration]"))'),'模型设置子页');
 await clickUi(port,'[data-action="open-settings-page"][data-page="model-configuration"]');
 await until(()=>evaluate(port,`Boolean(document.querySelector('[data-action="select-model-service-provider"][data-provider-id="${providerId}"]'))`),'服务商选择');
 await clickUi(port,`[data-action="select-model-service-provider"][data-provider-id="${providerId}"]`);
 await until(()=>evaluate(port,`globalThis.__nanfengDesktopWorkState.modelProviderId===${JSON.stringify(providerId)} && globalThis.__nanfengDesktopWorkState.modelServiceSettings.find(s=>s.providerId===${JSON.stringify(providerId)})?.credentialStored && !document.querySelector('[data-action="test-model-service-connection"]').disabled`),'已保存合成凭据的测试按钮');
 assert.equal(await evaluate(port,'document.querySelector("#model-service-api-key")?.type'),'password');
}
function release(index){const response=held[index];assert.ok(response);response.writeHead(200,{'content-type':'application/json'});response.end('{}');}
try{
 let port=await start();activePort=port;result.root=root;await ready(port);
 for(const [providerId,presetId] of [['DEEPSEEK','DEEPSEEK_V4_FLASH'],['QWEN','QWEN_3_7_PLUS']]){
  const settings=await invoke(port,'CHAT','read_desktop_model_service_settings');const service=settings.find(s=>s.providerId===providerId);
  await invoke(port,'CHAT','save_desktop_model_service_settings',{args:{providerId,presetId,enabled:true,expectedRevision:service.revision,apiKey:key(providerId)}});
 }
 await openModel(port,'DEEPSEEK');await evaluate(port,'(()=>{globalThis.__modelChatOwner=globalThis.__nanfengDesktopWorkState;return true})()');
 await clickUi(port,'[data-action="test-model-service-connection"]');await until(()=>held.length===1,'CHAT真实测试请求在途');
 assert.equal(await evaluate(port,'globalThis.__modelChatOwner.modelSettingsTesting'),true);
 console.log(JSON.stringify({stage:'settings-navigation-observed',root,actions:await evaluate(port,"[...new Set([...document.querySelectorAll('[data-action]')].map(n=>n.dataset.action))].slice(0,50)")}));
 await clickUi(port,'[data-action="show-chat"]');
 await until(()=>evaluate(port,'Boolean(document.querySelector("[data-action=show-work]"))'),'返回会话列表后的WORK入口');
 await clickUi(port,'[data-action="show-work"]');
 await until(()=>evaluate(port,'globalThis.__nanfengDesktopWorkState.dataArea==="WORK"'),'WORK当前区');
 await openModel(port,'QWEN');await evaluate(port,'(()=>{globalThis.__modelWorkOwner=globalThis.__nanfengDesktopWorkState;return true})()');
 await clickUi(port,'[data-action="test-model-service-connection"]');await until(()=>held.length===2,'WORK独立测试请求在途');
 const before=await evaluate(port,'({chatBusy:globalThis.__modelChatOwner.modelSettingsTesting,workBusy:globalThis.__modelWorkOwner.modelSettingsTesting,workNotice:globalThis.__modelWorkOwner.modelSettingsNotice,workError:globalThis.__modelWorkOwner.modelSettingsError,workProvider:globalThis.__modelWorkOwner.modelProviderId})');
 assert.ok(before.chatBusy&&before.workBusy);
 stage='release-chat-while-work-pending';release(0);
 await until(()=>evaluate(port,'!globalThis.__modelChatOwner.modelSettingsTesting || !globalThis.__modelWorkOwner.modelSettingsTesting'),'首个真实连接测试回执处理');
 const after=await evaluate(port,'({chatBusy:globalThis.__modelChatOwner.modelSettingsTesting,workBusy:globalThis.__modelWorkOwner.modelSettingsTesting,workNotice:globalThis.__modelWorkOwner.modelSettingsNotice,workError:globalThis.__modelWorkOwner.modelSettingsError,workProvider:globalThis.__modelWorkOwner.modelProviderId})');
 result.overlap={before,after};console.log(JSON.stringify({stage:'connection-overlap-observed',candidateSha256:receipt.sha256,root,before,after,requestCount:requests.length}));
 assert.equal(after.chatBusy,false,'CHAT完成必须结束自己的测试状态');assert.equal(after.workBusy,true,'CHAT回执不得解锁仍在测试的WORK');
 assert.equal(after.workNotice,before.workNotice);assert.equal(after.workError,before.workError);assert.equal(after.workProvider,'QWEN');
 release(1);await until(()=>evaluate(port,'!globalThis.__modelWorkOwner.modelSettingsTesting'),'WORK真实测试完成');
 await until(()=>evaluate(port,'globalThis.__modelWorkOwner.dialog?.kind==="model-settings-feedback"'),'WORK连接测试反馈');
 const feedback=await evaluate(port,'({failed:globalThis.__modelWorkOwner.dialog.failed,message:globalThis.__modelWorkOwner.dialog.message})');
 assert.equal(feedback.failed,false);assert.match(feedback.message,/Qwen/);result.workConnectionFeedback=feedback;
 await clickUi(port,'[data-action="close-dialog"]');
 stage='explicit-credential-edit';
 await clickUi(port,'[data-action="reveal-model-service-credential"]');
 await until(()=>evaluate(port,`document.querySelector('#model-service-api-key')?.type==='text' && document.querySelector('#model-service-api-key')?.value===${JSON.stringify(key('QWEN'))}`),'只在原编辑器显示合成凭据');
 await clickUi(port,'[data-action="reveal-model-service-credential"]');assert.equal(await evaluate(port,'document.querySelector("#model-service-api-key").type'),'password');
 const updatedKey=key('QWEN-UPDATED');
 await evaluate(port,`(()=>{const field=document.querySelector('#model-service-api-key');field.value=${JSON.stringify(updatedKey)};field.dispatchEvent(new Event('input',{bubbles:true}));return true})()`);
 await clickUi(port,'[data-action="save-model-service-settings"]');
 await until(()=>evaluate(port,'globalThis.__nanfengDesktopWorkState.dialog?.kind==="model-credential-saved" && !globalThis.__nanfengDesktopWorkState.modelSettingsSaving'),'实际保存合成凭据反馈');
 await clickUi(port,'[data-action="close-dialog"]');
 assert.equal(await evaluate(port,'globalThis.__nanfengDesktopWorkState.modelCredentialDraft.length'),0);
 await clickUi(port,'[data-action="reveal-model-service-credential"]');
 await until(()=>evaluate(port,`document.querySelector('#model-service-api-key')?.value===${JSON.stringify(updatedKey)}`),'显式读取更新后的合成凭据');
 await clickUi(port,'[data-action="reveal-model-service-credential"]');
 await clickUi(port,'[data-action="ask-delete-model-service-credential"]');
 await until(()=>evaluate(port,'globalThis.__nanfengDesktopWorkState.dialog?.kind==="model-credential-delete"'),'针对服务商的删除确认');
 await clickUi(port,'[data-action="confirm-delete-model-service-credential"]');
 await until(()=>evaluate(port,'globalThis.__nanfengDesktopWorkState.dialog?.kind==="model-settings-feedback" && !globalThis.__nanfengDesktopWorkState.modelSettingsSaving'),'实际删除合成凭据反馈');
 await clickUi(port,'[data-action="close-dialog"]');
 const settings=await invoke(port,'WORK','read_desktop_model_service_settings');
 assert.equal(settings.find(s=>s.providerId==='QWEN').credentialStored,false);assert.equal(settings.find(s=>s.providerId==='DEEPSEEK').credentialStored,true);
 result.settingsAfterDelete=settings.map(s=>({providerId:s.providerId,credentialStored:s.credentialStored,enabled:s.enabled,presetId:s.presetId,revision:s.revision}));
 result.firstNormalClose=await normalClose();port=await start(true);activePort=port;await ready(port);
 const cold=await invoke(port,'CHAT','read_desktop_model_service_settings');
 assert.deepEqual(cold.map(s=>({providerId:s.providerId,credentialStored:s.credentialStored,enabled:s.enabled,presetId:s.presetId,revision:s.revision})),result.settingsAfterDelete);
 assert.equal(requests.length,2,'冷启不自动连接测试');
 result.secondNormalClose=await normalClose();result.requests=requests;assert.ok(requests.every(r=>r.smallProbeOnly&&r.syntheticBearerOnly));result.pass=true;
}catch(error){
 result.error=String(error.stack||error);result.stage=stage;process.exitCode=1;
 if(launch&&activePort)try{result.failureUi=await evaluate(activePort,`(()=>{const s=globalThis.__nanfengDesktopWorkState,b=document.querySelector('[data-action="test-model-service-connection"]');return {dataArea:s?.dataArea,pane:s?.pane,settingsSection:s?.settingsSection,providerId:s?.modelProviderId,services:s?.modelServiceSettings?.map(x=>({providerId:x.providerId,revision:x.revision,credentialStored:x.credentialStored,enabled:x.enabled,presetId:x.presetId})),draft:s?.modelServiceDraft?{providerId:s.modelServiceDraft.providerId,revision:s.modelServiceDraft.revision,enabled:s.modelServiceDraft.enabled,presetId:s.modelServiceDraft.presetId}:null,saving:s?.modelSettingsSaving,testing:s?.modelSettingsTesting,testButtonPresent:Boolean(b),testButtonDisabled:b?.disabled,actions:[...new Set([...document.querySelectorAll('[data-action]')].map(n=>n.dataset.action))].slice(0,45)}})()`);console.log(JSON.stringify({stage:'failure-ui',snapshot:result.failureUi}));}catch{result.failureUiUnavailable=true;}
}
finally{
 for(const response of held)if(!response.writableEnded)response.destroy();if(launch){result.forcedCleanup=true;stop();}
 provider.closeAllConnections();await new Promise(done=>provider.close(done));result.pids=pids;
 if(root)await writeFile(join(root,'model-service-owner-evidence.json'),JSON.stringify(result,null,2),{flag:'wx'});
}
console.log(JSON.stringify({candidateSha256:receipt.sha256,root,pass:result.pass,stage:result.stage,error:result.error,pids}));
