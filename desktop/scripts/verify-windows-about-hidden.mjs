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
const cdpSessions=new Map();
async function cdp(port,method,params={},which='main') {
 const key=`${port}:${which}`;let session=cdpSessions.get(key);
 if(!session){const tabs=await fetch(`http://127.0.0.1:${port}/json/list`,{signal:AbortSignal.timeout(10000)}).then(r=>r.json());const tab=tabs.find(t=>t.type==='page'&&t.url===(which==='island'?'http://tauri.localhost/island.html':'http://tauri.localhost/'));assert.ok(tab);
  const socket=new WebSocket(tab.webSocketDebuggerUrl);await new Promise((done,fail)=>{socket.addEventListener('open',done,{once:true});socket.addEventListener('error',fail,{once:true});});session={socket,next:0,pending:new Map()};cdpSessions.set(key,session);
  socket.addEventListener('message',event=>{const message=JSON.parse(event.data);const pending=session.pending.get(message.id);if(!pending)return;clearTimeout(pending.timer);session.pending.delete(message.id);if(message.error||message.result?.exceptionDetails)pending.fail(new Error(message.result?.exceptionDetails?.exception?.description||message.error?.message||'CDP expression failed'));else pending.done(message.result);});
  socket.addEventListener('close',()=>{for(const pending of session.pending.values()){clearTimeout(pending.timer);pending.fail(new Error('Owned CDP target closed'));}session.pending.clear();cdpSessions.delete(key);});
 }
 const id=++session.next;return await new Promise((done,fail)=>{const timer=setTimeout(()=>{session.pending.delete(id);fail(new Error('Owned CDP request timed out'));},30000);session.pending.set(id,{done,fail,timer});session.socket.send(JSON.stringify({id,method,params}));});
}
function closeCdpSessions(){for(const session of cdpSessions.values())session.socket.close();cdpSessions.clear();}
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






const result={candidateSha256:receipt.sha256,backgroundOnly:true,realService:false};let port;
async function about(){await clickUi(port,'[data-action=show-settings]');await clickUi(port,'[data-action=open-settings-page][data-page=about]');await until(()=>evaluate(port,'!!document.querySelector(".android-settings-about")'),'About page');}
try{
 port=await start();await ready(port);result.runtimeInfo=await invoke(port,'CHAT','read_desktop_runtime_info');await about();
 result.links=await evaluate(port,'[...document.querySelectorAll(".android-settings-about a")].map(a=>({href:a.getAttribute("href"),label:a.getAttribute("aria-label"),hint:a.title}))');
 assert.match(result.runtimeInfo.sourceInputSha256||'',/^[0-9a-f]{64}$/);assert.ok(result.runtimeInfo.sourceInputFileCount>0);assert.equal(result.runtimeInfo.sourceInputRecipe,'nfai.desktop.source-input.v1');assert.equal(result.runtimeInfo.buildProfile,'debug');
 const expected=['https://github.com/nanzhufeng/Nanfeng-AI/releases',`https://github.com/nanzhufeng/Nanfeng-AI/commit/${result.runtimeInfo.gitCommit}`,'https://github.com/nanzhufeng/Nanfeng-AI'];assert.deepEqual(result.links.map(a=>a.href),expected);assert.ok(result.links.every(a=>a.label?.includes('默认浏览器')&&a.hint.includes('默认浏览器')));assert.ok(result.links[0].label.includes(result.runtimeInfo.version));assert.ok(result.links[1].label.includes(result.runtimeInfo.gitCommit.slice(0,8)));assert.equal(await evaluate(port,'document.querySelector(".android-settings-about").textContent.includes("构建时间")'),true);
 const shown=await evaluate(port,'document.querySelector("[data-source-input-sha256]")?.dataset.sourceInputSha256');assert.equal(shown,result.runtimeInfo.sourceInputSha256);
 result.interceptor=await evaluate(port,`(()=>{window.__aboutLinkCalls=[];window.__aboutCaptureProbes=0;window.addEventListener('click',event=>{const target=event.target?.closest?.('a[data-action="open-source-link"],button[data-about-link-probe]');if(!target)return;if(target.matches('[data-about-link-probe]')){event.preventDefault();event.stopImmediatePropagation();window.__aboutCaptureProbes++;return;}if(!target.closest('.android-settings-about'))return;event.preventDefault();event.stopImmediatePropagation();window.__aboutLinkCalls.push({href:target.getAttribute('href'),dataHref:target.dataset.href,defaultPrevented:event.defaultPrevented});},{capture:true});const probe=document.createElement('button');probe.type='button';probe.dataset.aboutLinkProbe='true';probe.hidden=true;document.body.append(probe);probe.click();probe.remove();return {installed:window.__aboutCaptureProbes===1,mode:'capture-before-app-handler'};})()`);
 assert.equal(result.interceptor.installed,true,'No link keyboard/click before window capture is proven');
 result.focusEmulation=await cdp(port,'Emulation.setFocusEmulationEnabled',{enabled:true});
 result.keyProbes=[];
 for(let index=0;index<3;index++){
  await evaluate(port,`(()=>{document.querySelectorAll('.android-settings-about a')[${index}].focus({preventScroll:true});return true})()`);
  await cdp(port,'Input.dispatchKeyEvent',{type:'keyDown',key:'Enter',code:'Enter',windowsVirtualKeyCode:13,nativeVirtualKeyCode:13});await cdp(port,'Input.dispatchKeyEvent',{type:'keyUp',key:'Enter',code:'Enter',windowsVirtualKeyCode:13,nativeVirtualKeyCode:13});
  result.keyProbes.push(await evaluate(port,'({focused:document.hasFocus(),activeTag:document.activeElement?.tagName,href:document.activeElement?.getAttribute("href"),calls:window.__aboutLinkCalls.length})'));
  await until(()=>evaluate(port,`window.__aboutLinkCalls.length===${index+1}`),'keyboard link command');
 }
 result.keyboardCalls=await evaluate(port,'window.__aboutLinkCalls');assert.deepEqual(result.keyboardCalls.map(c=>c.href),expected);assert.ok(result.keyboardCalls.every(c=>c.defaultPrevented&&c.dataHref===c.href));
 result.geometry=[];
 for(const theme of ['orange','blue','black','green','yellow','pink','purple'])for(const mode of ['light','dark'])for(const scale of ['standard','large'])for(const width of [640,1100]){
  await cdp(port,'Emulation.setDeviceMetricsOverride',{width,height:850,deviceScaleFactor:1,mobile:false});
  const measured=await evaluate(port,`(async()=>{const module=await import('/desktop-theme-owner.mjs');module.applyDesktopThemeToRoot(document.documentElement,{mode:${JSON.stringify(mode)},fontSize:${JSON.stringify(scale)},themeColor:${JSON.stringify(theme)}},false);const card=document.querySelector('.android-settings-about');const a=card.querySelector('a');a.focus({preventScroll:true});const r=card.getBoundingClientRect(),s=getComputedStyle(a);return {scrollWidth:card.scrollWidth,clientWidth:card.clientWidth,cardWidth:r.width,radius:s.borderRadius,focusVisible:a.matches(':focus-visible'),outlineColor:s.outlineColor,outlineWidth:s.outlineWidth,outlineStyle:s.outlineStyle,underline:s.textDecorationThickness,foreground:s.color,background:s.backgroundColor,links:[...card.querySelectorAll('a')].map(n=>{const r=n.getBoundingClientRect();return {left:r.left,right:r.right,width:r.width}})};})()`);
  assert.ok(measured.focusVisible);assert.equal(measured.outlineStyle,'none');assert.ok(parseFloat(measured.underline)>=3);const luminance=value=>[...value.matchAll(/[0-9.]+/g)].slice(0,3).map(m=>Number(m[0])/255).reduce((sum,c,i)=>sum+[.2126,.7152,.0722][i]*(c<=.04045?c/12.92:((c+.055)/1.055)**2.4),0);const foreground=luminance(measured.foreground),background=luminance(measured.background);measured.contrast=(Math.max(foreground,background)+.05)/(Math.min(foreground,background)+.05);assert.ok(measured.contrast>=4.5,JSON.stringify({theme,mode,contrast:measured.contrast}));
  assert.ok(measured.scrollWidth<=measured.clientWidth+1);assert.ok(measured.links.every(r=>r.width>0&&r.right<=width+1&&r.left>=-1));result.geometry.push({theme,mode,scale,width,...measured});
 }
 await cdp(port,'Emulation.clearDeviceMetricsOverride');
 await clickUi(port,'[data-action=open-settings-page][data-page=feature-review]');result.featureReview=await until(()=>evaluate(port,'document.body.textContent.includes("后台标题整理")&&document.body.textContent.includes("已检索不等于已核实")'),'feature review current behavior');
 result.close=await normalClose();port=await start(true);await ready(port);assert.deepEqual(await invoke(port,'CHAT','read_desktop_runtime_info'),result.runtimeInfo);await about();assert.equal(await evaluate(port,'document.querySelector("[data-source-input-sha256]")?.dataset.sourceInputSha256'),result.runtimeInfo.sourceInputSha256);result.coldClose=await normalClose();result.pass=true;
}catch(error){result.error=error.stack||String(error);process.exitCode=1;}
finally{if(launch)try{result.failureClose=await normalClose();}catch(error){result.closeError=String(error);stop();}result.root=root;result.pids=pids;if(root)await writeFile(join(root,'about-native-evidence.json'),JSON.stringify(result,null,2)+'\n');}
closeCdpSessions();
console.log(JSON.stringify(result,null,2));
