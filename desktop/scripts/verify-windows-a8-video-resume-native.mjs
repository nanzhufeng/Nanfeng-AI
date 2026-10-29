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
const candidateNamespace = process.argv.includes('--release') ? 'windows-acceptance-release' : 'windows-acceptance';
const expectedBuildProfile = process.argv.includes('--release') ? 'release' : 'debug';
const receipt = JSON.parse(await readFile(join(desktopRoot, 'output', candidateNamespace, 'current.json'), 'utf8'));
assert.equal(receipt.buildProfile, expectedBuildProfile);
if (process.env.NANFENG_AI_EXPECTED_CANDIDATE_SHA256) assert.equal(receipt.sha256, process.env.NANFENG_AI_EXPECTED_CANDIDATE_SHA256);
assert.equal(receipt.identifier, 'com.nanzhufeng.ai.desktop.windowsacceptance');
const source = resolve(desktopRoot, 'output', candidateNamespace, receipt.sha256, 'NanfengAI-Windows-Acceptance.exe');
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
    '-ExecutablePath', source, '-StartupMode', 'normal', '-DebugPort', String(port)];
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



const result={candidateSha256:receipt.sha256,backgroundOnly:true,realService:false};let port=0,blocker=null;
const q=JSON.stringify;
async function position(ws,id,area='CHAT'){return (await invoke(port,area,'read_desktop_video_preview',{args:{workspaceId:ws,attachmentId:id}})).positionMillis;}
async function open(id,readyState=2){await clickUi(port,`[data-action="open-video-preview"][data-attachment-id="${id}"]`);await until(()=>evaluate(port,`document.querySelector('[data-video-preview]')?.readyState>=${readyState}`),'真实播放器metadata/恢复');await evaluate(port,"document.querySelector('[data-video-preview]').pause();true");}
async function seek(ms){await evaluate(port,`(()=>{const r=document.querySelector('[data-video-preview-range]');r.value=${q(String(ms))};r.dispatchEvent(new Event('input',{bubbles:true}));document.querySelector('[data-video-preview]').pause();return true;})()`);await until(()=>evaluate(port,`Math.abs(document.querySelector('[data-video-preview]').currentTime-${ms/1000})<0.05`),'准确拖动位置');}

try{
 port=await start();result.root=root;await ready(port);result.runtimeInfo=await invoke(port,'CHAT','read_desktop_runtime_info');assert.equal(result.runtimeInfo.buildProfile,expectedBuildProfile);
 const ws=(await invoke(port,'CHAT','ensure_desktop_local_workspace')).summary.id,workWs=(await invoke(port,'WORK','ensure_desktop_local_workspace')).summary.id;
 const file=join(root,'synthetic-resume.mp4');execFileSync(join(receipt.mediaTools,'bin','ffmpeg.exe'),['-hide_banner','-loglevel','error','-f','lavfi','-i','testsrc2=s=320x180:r=24:d=8','-c:v','libopenh264','-pix_fmt','yuv420p',file],{timeout:30000,windowsHide:true});
 const bytesBase64=(await readFile(file)).toString('base64');const asset=await invoke(port,'CHAT','import_desktop_conversation_clipboard_attachment',{args:{workspaceId:ws,displayName:'合成视频进度.mp4',bytesBase64}});
 const workAsset=await invoke(port,'WORK','import_desktop_conversation_clipboard_attachment',{args:{workspaceId:workWs,displayName:'工作区合成视频.mp4',bytesBase64}});assert.equal(asset.id,workAsset.id);result.sameAttachmentId=true;
 await invoke(port,'CHAT','save_desktop_video_preview_position',{args:{workspaceId:ws,attachmentId:asset.id,positionMillis:4500}});await invoke(port,'WORK','save_desktop_video_preview_position',{args:{workspaceId:workWs,attachmentId:workAsset.id,positionMillis:6500}});
 await invoke(port,'CHAT','save_desktop_conversation_draft',{args:{workspaceId:ws,conversationId:null,text:'视频进度合成草稿',attachmentIds:[asset.id]}});result.seedClose=await normalClose();
 port=await start(true);await ready(port);await until(()=>evaluate(port,"!!document.querySelector('[data-action=new-chat]')"),'新聊天入口');await clickUi(port,'[data-action=new-chat]');await until(()=>evaluate(port,`!!document.querySelector('[data-action="open-video-preview"][data-attachment-id="${asset.id}"]')`),'草稿真实视频卡');
 const draft=await invoke(port,'CHAT','read_desktop_conversation_draft',{workspaceId:ws});await open(asset.id);result.restored=await evaluate(port,"(()=>{const v=document.querySelector('[data-video-preview]');return {position:v.currentTime,width:v.videoWidth,height:v.videoHeight,duration:v.duration,error:v.error?.code??null};})()");assert.ok(result.restored.position>=4.4&&result.restored.position<5.5);assert.equal(result.restored.width,320);assert.equal(result.restored.error,null);
 await seek(0);await clickUi(port,'[data-action=close-video-preview]');await until(async()=>await position(ws,asset.id)===0,'精确零点关闭持久化');result.exactZeroClose=true;
 await open(asset.id);result.zeroReopened=await evaluate(port,"document.querySelector('[data-video-preview]').currentTime");assert.ok(result.zeroReopened<1);
 await seek(2750);await until(async()=>await position(ws,asset.id)===2750,'暂停/拖动即写检查点');result.pauseCheckpoint=true;
 await seek(0);await until(async()=>await position(ws,asset.id)===0,'暂停零点检查点');result.pauseZero=true;await clickUi(port,'[data-action=close-video-preview]');
 await invoke(port,'CHAT','save_desktop_video_preview_position',{args:{workspaceId:ws,attachmentId:asset.id,positionMillis:4500}});
 result.unreadyClose=await evaluate(port,`new Promise((resolve,reject)=>{const timer=setTimeout(()=>{o.disconnect();reject(new Error('unready element insertion not observed'))},5000);const o=new MutationObserver(()=>{const v=document.querySelector('[data-video-preview]');if(!v)return;o.disconnect();clearTimeout(timer);const p=globalThis.__nanfengDesktopWorkState.videoPreview;window.__closedSyntheticVideo=v;const observed={readyState:v.readyState,resumeReady:p.resumeReady===true,savedPosition:p.positionMillis};document.querySelector('[data-action=close-video-preview]').click();resolve(observed);});o.observe(document.querySelector('#app'),{childList:true,subtree:true});document.querySelector('[data-action="open-video-preview"][data-attachment-id="${asset.id}"]').click();})`);
 assert.equal(result.unreadyClose.readyState,0);assert.equal(result.unreadyClose.resumeReady,false);await new Promise(done=>setTimeout(done,200));assert.equal(await position(ws,asset.id),4500);result.unreadyPreserved=true;
 await evaluate(port,"window.__closedSyntheticVideo.dispatchEvent(new Event('loadedmetadata'));true");await new Promise(done=>setTimeout(done,100));assert.equal(await evaluate(port,"globalThis.__nanfengDesktopWorkState.videoPreview===null"),true);result.lateSyntheticMetadataStayedClosed=true;
 await open(asset.id);await seek(2000);await until(async()=>await position(ws,asset.id)===2000,'最终播放起点');await evaluate(port,"document.querySelector('[data-video-preview]').play();true");await until(()=>evaluate(port,"document.querySelector('[data-video-preview]').currentTime>=2.4"),'真实播放推进');result.beforeWindowClose=await evaluate(port,"document.querySelector('[data-video-preview]').currentTime");result.windowClose=await normalClose();
 port=await start(true);await ready(port);result.coldPosition=await position(ws,asset.id);assert.ok(result.coldPosition>=Math.round(result.beforeWindowClose*1000)-50&&result.coldPosition<5000);assert.equal(await position(workWs,workAsset.id,'WORK'),6500);result.workUnchanged=true;assert.deepEqual(await invoke(port,'CHAT','read_desktop_conversation_draft',{workspaceId:ws}),draft);result.coldClose=await normalClose();result.pass=true;
}catch(error){result.pass=false;result.error=String(error);result.stack=error.stack;process.exitCode=1;}
finally{if(blocker)try{await blocker.release();}catch(error){result.blockerCleanup=String(error);}if(launch){try{result.failureClose=await normalClose();}catch(error){result.cleanupError=String(error);try{stop();result.forcedCleanup=true;}catch(error){result.forceCleanupError=String(error);}}}result.pids=pids;if(root)await writeFile(join(root,'a8-video-resume-native-evidence.json'),JSON.stringify(result,null,2),{flag:'wx'});}
console.log(JSON.stringify({root,pass:result.pass,error:result.error,pids}));
