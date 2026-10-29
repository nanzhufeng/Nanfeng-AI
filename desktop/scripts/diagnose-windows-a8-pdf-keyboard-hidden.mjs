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
    '-StartupMode', 'normal', '-DebugPort', String(port)];
  if (process.argv.includes('--enabled')) args.push('-OrdinaryChatTestEndpoint',`http://127.0.0.1:${probeEndpointPort}`);
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


function twoPagePdf(){
 const stream='q 0.5 w 0 0 0 RG 20 20 200 200 re S Q\n';const objects=['<< /Type /Catalog /Pages 2 0 R >>','<< /Type /Pages /Kids [3 0 R 5 0 R] /Count 2 >>','<< /Type /Page /Parent 2 0 R /MediaBox [0 0 300 300] /Resources << >> /Contents 4 0 R >>',`<< /Length ${Buffer.byteLength(stream)} >>\nstream\n${stream}endstream`,'<< /Type /Page /Parent 2 0 R /MediaBox [0 0 300 300] /Resources << >> /Contents 6 0 R >>',`<< /Length ${Buffer.byteLength(stream)} >>\nstream\n${stream}endstream`];const parts=[Buffer.from('%PDF-1.4\n')],offsets=[];let length=parts[0].length;for(let i=0;i<objects.length;i++){offsets.push(length);const b=Buffer.from(`${i+1} 0 obj\n${objects[i]}\nendobj\n`);parts.push(b);length+=b.length;}parts.push(Buffer.from(`xref\n0 7\n0000000000 65535 f \n${offsets.map(n=>String(n).padStart(10,'0')+' 00000 n \n').join('')}trailer\n<< /Size 7 /Root 1 0 R >>\nstartxref\n${length}\n%%EOF\n`));return Buffer.concat(parts);
}
const probeEndpointPort=await reservePort();const result={candidateSha256:receipt.sha256,backgroundOnly:true};let port=0;
try{
 port=await start();result.root=root;await ready(port);const ws=(await invoke(port,'CHAT','ensure_desktop_local_workspace')).summary.id;let conversationId=null;if(process.argv.includes('--enabled')){const setting=(await invoke(port,'CHAT','read_desktop_model_service_settings')).find(p=>p.providerId==='OPENROUTER');await invoke(port,'CHAT','save_desktop_model_service_settings',{args:{providerId:'OPENROUTER',enabled:true,presetId:'GPT_6_1_SOL',expectedRevision:setting.revision,apiKey:'nfai-synthetic-pdf-probe'}});const c=(await invoke(port,'CHAT','read_desktop_workspace',{workspaceId:ws})).exchange.conversations[0];conversationId=c.id;const choice=await invoke(port,'CHAT','read_desktop_p6g_selection',{workspaceId:ws,conversationId});await invoke(port,'CHAT','set_desktop_p6g_conversation_override',{args:{workspaceId:ws,conversationId,expectedRevision:choice.conversationOverride.revision,modelId:'GPT_6_1_SOL'}});result.modelEnabled=true;}const asset=await invoke(port,'CHAT','import_desktop_conversation_clipboard_attachment',{args:{workspaceId:ws,displayName:'键盘路径合成.pdf',bytesBase64:twoPagePdf().toString('base64')}});await invoke(port,'CHAT','save_desktop_conversation_draft',{args:{workspaceId:ws,conversationId,text:'合成PDF键盘事件诊断',attachmentIds:[asset.id]}});result.seedClose=await normalClose();port=await start(true);await ready(port);await until(()=>evaluate(port,"!!document.querySelector('[data-action=new-chat]')"),'新聊天');if(conversationId){await until(()=>evaluate(port,`!!document.querySelector('[data-action="select-chat"][data-id="${conversationId}"]')`),'指定会话');await clickUi(port,`[data-action="select-chat"][data-id="${conversationId}"]`);}else await clickUi(port,'[data-action=new-chat]');await until(()=>evaluate(port,`!!document.querySelector('[data-action="open-pdf-preview"][data-attachment-id="${asset.id}"]')`),'PDF卡');if(process.argv.includes('--reload')){const marker=randomUUID();await evaluate(port,`window.__pdfProbeMarker=${JSON.stringify(marker)};location.reload();true`);await until(()=>evaluate(port,`window.__pdfProbeMarker!==${JSON.stringify(marker)}&&globalThis.__nanfengDesktopWorkState?.runtimeInfo?.dataAreasReady===true&&!!document.querySelector('[data-action=new-chat]')`),'probe新文档');if(conversationId){await until(()=>evaluate(port,`!!document.querySelector('[data-action="select-chat"][data-id="${conversationId}"]')`),'指定会话');await clickUi(port,`[data-action="select-chat"][data-id="${conversationId}"]`);}else await clickUi(port,'[data-action=new-chat]');await until(()=>evaluate(port,`!!document.querySelector('[data-action="open-pdf-preview"][data-attachment-id="${asset.id}"]')`),'reload后PDF卡');result.reloaded=true;}await clickUi(port,`[data-action="open-pdf-preview"][data-attachment-id="${asset.id}"]`);await until(()=>evaluate(port,"globalThis.__nanfengDesktopWorkState.pdfPreview?.pageNumber===1&&!globalThis.__nanfengDesktopWorkState.pdfPreview.loading"),'PDF读回');
 result.before=await evaluate(port,"(()=>{window.__pdfTrace=[];window.__pdfErrors=[];window.addEventListener('error',e=>window.__pdfErrors.push(String(e.message)));for(const [name,target,capture] of [['window-capture',window,true],['document-capture',document,true],['app-capture',document.querySelector('#app'),true],['app-bubble',document.querySelector('#app'),false],['document-bubble',document,false],['window-bubble',window,false]])target.addEventListener('keydown',e=>window.__pdfTrace.push({name,key:e.key,prevented:e.defaultPrevented,active:document.activeElement?.tagName,target:e.target.tagName}),capture);const b=document.querySelector('[data-action=pdf-page-next]');b.focus({preventScroll:true});const e=new KeyboardEvent('keydown',{key:'ArrowRight',bubbles:true,cancelable:true});b.dispatchEvent(e);const p=globalThis.__nanfengDesktopWorkState.pdfPreview;return {page:p?.pageNumber,pages:p?.pageCount,id:p?.attachmentId,loading:p?.loading,image:!!globalThis.__nanfengDesktopWorkState.imagePreview,active:document.activeElement?.tagName,buttonDisabled:b.disabled,keyConsumed:e.defaultPrevented};})()");await new Promise(done=>setTimeout(done,1200));result.afterArrow=await evaluate(port,"({page:globalThis.__nanfengDesktopWorkState.pdfPreview?.pageNumber,loading:globalThis.__nanfengDesktopWorkState.pdfPreview?.loading,requestId:globalThis.__nanfengDesktopWorkState.pdfPreview?.request?.id,trace:window.__pdfTrace,errors:window.__pdfErrors})");
 if(result.afterArrow.page===1&&!result.afterArrow.loading){await clickUi(port,'[data-action=pdf-page-next]');await until(()=>evaluate(port,'globalThis.__nanfengDesktopWorkState.pdfPreview?.pageNumber===2&&!globalThis.__nanfengDesktopWorkState.pdfPreview.loading'),'点击对照第二页');}result.afterClick=await evaluate(port,'globalThis.__nanfengDesktopWorkState.pdfPreview.pageNumber');result.close=await normalClose();result.pass=true;
}catch(error){result.pass=false;result.error=String(error);if(launch)try{result.failureBefore=await evaluate(port,"({loading:globalThis.__nanfengDesktopWorkState.pdfPreview?.loading,id:globalThis.__nanfengDesktopWorkState.pdfPreview?.request?.id,page:globalThis.__nanfengDesktopWorkState.pdfPreview?.pageNumber,error:globalThis.__nanfengDesktopWorkState.pdfPreview?.error})");result.rawBegin=await evaluate(port,"(async()=>{const started=performance.now();try{const value=await Promise.race([window.__TAURI__.core.invoke('begin_desktop_pdf_preview',{}, {headers:{'x-nanfeng-data-area':'CHAT'}}),new Promise(resolve=>setTimeout(()=>resolve('TIMEOUT'),2000))]);if(typeof value==='number')await window.__TAURI__.core.invoke('cancel_desktop_pdf_preview',{requestId:value},{headers:{'x-nanfeng-data-area':'CHAT'}});return {value,elapsed:performance.now()-started};}catch(e){return {error:String(e),elapsed:performance.now()-started}}})()");result.failureAfter=await evaluate(port,"({loading:globalThis.__nanfengDesktopWorkState.pdfPreview?.loading,id:globalThis.__nanfengDesktopWorkState.pdfPreview?.request?.id,page:globalThis.__nanfengDesktopWorkState.pdfPreview?.pageNumber,error:globalThis.__nanfengDesktopWorkState.pdfPreview?.error})");}catch(e){result.probeError=String(e);}process.exitCode=1;}
finally{if(launch){try{result.failureClose=await normalClose();}catch(error){result.cleanupError=String(error);try{stop();}catch{}}}result.pids=pids;if(root)await writeFile(join(root,'pdf-keyboard-probe.json'),JSON.stringify(result,null,2),{flag:'wx'});}
console.log(JSON.stringify({root,pass:result.pass,error:result.error,before:result.before,afterArrow:result.afterArrow,afterClick:result.afterClick,failureBefore:result.failureBefore,rawBegin:result.rawBegin,failureAfter:result.failureAfter,pids}));
