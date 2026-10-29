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
let cdpSocket=null,cdpPort=null,cdpSequence=0;
const cdpPending=new Map();
async function cdp(port,method,params={}){
 if(!cdpSocket||cdpPort!==port||cdpSocket.readyState!==WebSocket.OPEN){
  if(cdpSocket)cdpSocket.close();
  const tabs=await fetch(`http://127.0.0.1:${port}/json/list`,{signal:AbortSignal.timeout(10000)}).then(r=>r.json());
  const tab=tabs.find(t=>t.type==='page'&&t.url==='http://tauri.localhost/');assert.ok(tab);
  cdpSocket=new WebSocket(tab.webSocketDebuggerUrl);cdpPort=port;
  cdpSocket.addEventListener('message',event=>{const m=JSON.parse(event.data),p=cdpPending.get(m.id);if(!p)return;cdpPending.delete(m.id);clearTimeout(p.timer);if(m.error||m.result?.exceptionDetails)p.reject(new Error(m.result?.exceptionDetails?.exception?.description||m.error?.message||'CDP evaluation failed'));else p.resolve(m.result);});
  await new Promise((done,fail)=>{cdpSocket.addEventListener('open',done,{once:true});cdpSocket.addEventListener('error',fail,{once:true});});
 }
 const id=++cdpSequence;
 return new Promise((resolve,reject)=>{const timer=setTimeout(()=>{cdpPending.delete(id);reject(new Error(`${method} timed out`));},30000);cdpPending.set(id,{resolve,reject,timer});cdpSocket.send(JSON.stringify({id,method,params}));});
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





const luminance=c=>{const a=c.match(/[\d.]+/g).slice(0,3).map(Number).map(v=>{v/=255;return v<=.04045?v/12.92:((v+.055)/1.055)**2.4});return .2126*a[0]+.7152*a[1]+.0722*a[2]};
const contrast=(a,b)=>{const x=luminance(a),y=luminance(b);return (Math.max(x,y)+.05)/(Math.min(x,y)+.05)};
const layoutProbe=process.argv.includes('--layout-probe');const catalogProbe=process.argv.includes('--catalog-probe');
const providerPort=await reservePort();
const result={candidateSha256:receipt.sha256,backgroundOnly:true,realService:false,cases:[],failures:[]};let port;
const style = async (selector,pseudo=[])=>{
 const {root:doc}=await cdp(port,'DOM.getDocument');const node=await cdp(port,'DOM.querySelector',{nodeId:doc.nodeId,selector});assert.ok(node.nodeId,selector);
 await cdp(port,'CSS.forcePseudoState',{nodeId:node.nodeId,forcedPseudoClasses:pseudo});
 return evaluate(port,`(()=>{const n=document.querySelector(${JSON.stringify(selector)}),s=getComputedStyle(n),r=n.getBoundingClientRect();return {bg:s.backgroundColor,image:s.backgroundImage,color:s.color,strongColor:n.querySelector('strong')&&getComputedStyle(n.querySelector('strong')).color,smallColor:n.querySelector('small')&&getComputedStyle(n.querySelector('small')).color,radius:s.borderRadius,outline:s.outlineStyle,shadow:s.boxShadow,font:s.fontSize,width:r.width,height:r.height,left:r.left,right:r.right,client:n.clientWidth,scroll:n.scrollWidth}})()`);
};
try{
 port=await start();await ready(port);await until(()=>evaluate(port,'globalThis.__nanfengDesktopWorkState?.runtimeInfo?.dataAreasReady===true'),'runtime');
 await clickUi(port,'[data-action=show-settings]');await clickUi(port,'[data-action=open-settings-page][data-page=model]');await clickUi(port,'[data-action=open-settings-page][data-page=model-configuration]');
 await until(()=>evaluate(port,'!!document.querySelector(".model-settings-platform-rail")'),'model settings');
 await clickUi(port,'.model-settings-platform-rail [data-provider-id=RELAYCORE_CLAUDE]');
 await until(()=>evaluate(port,'!!document.querySelector(".android-settings-relay-tabs button.selected")'),'RelayCore tabs');
 await cdp(port,'DOM.enable');await cdp(port,'CSS.enable');
 for(const mode of (catalogProbe?[]:['light','dark']))for(const themeColor of (layoutProbe?['orange']:['orange','blue','black','green','yellow','pink','purple']))for(const fontSize of (layoutProbe?['standard','large']:['small','standard','large']))for(const width of (layoutProbe?[640]:[1440,900,640])){
  await cdp(port,'Emulation.setDeviceMetricsOverride',{width,height:900,deviceScaleFactor:1,mobile:false});
  await evaluate(port,`(async()=>{const appearance=${JSON.stringify({mode,themeColor,fontSize})};globalThis.__nanfengDesktopWorkState.appearance=appearance;const m=await import('./desktop-theme-owner.mjs');m.applyDesktopThemeToRoot(document.documentElement,appearance,false);return true})()`);
  const record={mode,themeColor,fontSize,width,controls:{}};
  for(const [key,selector] of Object.entries({provider:'.model-settings-platform-rail button.selected',tab:'.model-settings-model-pane .android-settings-relay-tabs button.selected',model:'.model-settings-model-option.selected'})){
   const base=await style(selector),hover=await style(selector,['hover']),active=await style(selector,['active']),focus=await style(selector,['focus','focus-visible']);await style(selector);
   record.controls[key]={base,hover,active,focus};
   for(const [state,value] of Object.entries({base,hover,active,focus}))if(!value.bg.startsWith('rgba'))for(const color of [value.color,value.strongColor,value.smallColor].filter(Boolean))if(contrast(color,value.bg)<4.5)result.failures.push({mode,themeColor,fontSize,width,key,state,reason:'control text contrast below 4.5',contrast:contrast(color,value.bg)});
   const rgb=base.bg.match(/[\d.]+/g)?.map(Number)||[];if(rgb.length<3||(rgb.length===4&&rgb[3]<1)||Math.max(...rgb.slice(0,3))-Math.min(...rgb.slice(0,3))>1)result.failures.push({mode,themeColor,fontSize,width,key,reason:'selected surface is tinted',actual:base.bg});
   if(base.bg===focus.bg&&base.image===focus.image&&base.shadow===focus.shadow&&focus.outline==='none')result.failures.push({mode,themeColor,fontSize,width,key,reason:'selected keyboard focus invisible'});
   if([hover,active,focus].some(s=>s.radius!==base.radius))result.failures.push({mode,themeColor,fontSize,width,key,reason:'interaction contour changed'});
   if(base.scroll>base.client+1)result.failures.push({mode,themeColor,fontSize,width,key,reason:'control horizontal overflow',client:base.client,scroll:base.scroll});
  }
  record.layout=await evaluate(port,`(()=>{const n=document.querySelector('.model-settings-browser'),r=n.getBoundingClientRect(),small=document.querySelector('.model-settings-model-option small');return {width:innerWidth,rootClient:document.documentElement.clientWidth,rootScroll:document.documentElement.scrollWidth,left:r.left,right:r.right,client:n.clientWidth,scroll:n.scrollWidth,smallFont:small&&getComputedStyle(small).fontSize}})()`);
  if(record.layout.rootScroll>record.layout.rootClient+1||record.layout.scroll>record.layout.client+1||record.layout.right>width+1)result.failures.push({mode,themeColor,fontSize,width,reason:'model browser overflows viewport',layout:record.layout});
  if(parseFloat(record.layout.smallFont)<12*({small:.8,standard:1,large:1.24}[fontSize])-.05)result.failures.push({mode,themeColor,fontSize,width,reason:'model description below readable desktop baseline',font:record.layout.smallFont});
  record.listReachability=await evaluate(port,`(()=>{const list=document.querySelector('.model-settings-model-list'),browser=document.querySelector('.model-settings-browser'),last=list.lastElementChild;list.scrollTop=list.scrollHeight;const l=list.getBoundingClientRect(),b=browser.getBoundingClientRect(),r=last.getBoundingClientRect(),s=getComputedStyle(list);const result={count:list.children.length,overflow:s.overflowY,client:list.clientHeight,scroll:list.scrollHeight,listBottom:l.bottom,browserBottom:b.bottom,lastTop:r.top,lastBottom:r.bottom,scrollTop:list.scrollTop,visible:r.top>=l.top-1&&r.bottom<=l.bottom+1&&r.bottom<=b.bottom+1};list.scrollTop=0;return result})()`);
  if(!record.listReachability.visible)result.failures.push({mode,themeColor,fontSize,width,reason:'last model is clipped or cannot be reached',list:record.listReachability});
  record.brands=await evaluate(port,`[...document.querySelectorAll('.model-settings-browser .model-provider-brand')].map(n=>({brand:n.dataset.modelBrand,width:n.naturalWidth,complete:n.complete,filter:getComputedStyle(n).filter}))`);
  if(record.brands.some(b=>!b.complete||!b.width))result.failures.push({mode,themeColor,fontSize,width,reason:'model brand asset missing'});
  const gpt=record.brands.find(b=>b.brand==='gpt');if(!gpt||gpt.filter!==(mode==='dark'?'invert(1)':'none'))result.failures.push({mode,themeColor,fontSize,width,reason:'GPT icon mode filter mismatch',gpt});
  result.cases.push(record);
 }
 result.catalogCases=[];
 for(const provider of ['OPENROUTER','RELAYCORE_CLAUDE','RELAYCORE_GPT','RELAYCORE_GEMINI','DEEPSEEK','ZHIPU','QWEN'])for(const fontSize of ['small','standard','large'])for(const width of [1440,640]){
  await cdp(port,'Emulation.setDeviceMetricsOverride',{width,height:900,deviceScaleFactor:1,mobile:false});
  await evaluate(port,`(async()=>{const a={mode:'light',themeColor:'orange',fontSize:${JSON.stringify(fontSize)}};__nanfengDesktopWorkState.appearance=a;(await import('./desktop-theme-owner.mjs')).applyDesktopThemeToRoot(document.documentElement,a,false);return true})()`);
  if(provider.startsWith('RELAYCORE_')){await clickUi(port,'.model-settings-platform-rail [data-provider-id=RELAYCORE_CLAUDE]');await until(()=>evaluate(port,'!!document.querySelector(".android-settings-relay-tabs")'),'relay tabs');await clickUi(port,`.android-settings-relay-tabs [data-provider-id="${provider}"]`);}
  else await clickUi(port,`.model-settings-platform-rail [data-provider-id="${provider}"]`);
  await until(()=>evaluate(port,`__nanfengDesktopWorkState.modelProviderId===${JSON.stringify(provider)}&&!!document.querySelector('.model-settings-model-option')`),'provider catalog');
  const check=await evaluate(port,`(()=>{const list=document.querySelector('.model-settings-model-list'),browser=document.querySelector('.model-settings-browser');list.scrollTop=list.scrollHeight;const a=list.lastElementChild.getBoundingClientRect(),b=list.getBoundingClientRect(),c=browser.getBoundingClientRect();const r={count:list.children.length,client:list.clientHeight,scroll:list.scrollHeight,overflow:getComputedStyle(list).overflowY,lastTop:a.top,lastBottom:a.bottom,listTop:b.top,listBottom:b.bottom,browserBottom:c.bottom,visible:a.top>=b.top-1&&a.bottom<=b.bottom+1&&a.bottom<=c.bottom+1};list.scrollTop=0;return r})()`);
  result.catalogCases.push({provider,fontSize,width,...check});if(!check.visible)result.failures.push({provider,fontSize,width,reason:'provider last model is clipped',check});
 }
 await cdp(port,'Emulation.setEmulatedMedia',{features:[{name:'forced-colors',value:'active'}]});
 result.forcedColorsFocus=await style('.model-settings-model-option.selected',['focus','focus-visible']);if(result.forcedColorsFocus.outline==='none')result.failures.push({reason:'forced-colors keyboard focus missing'});
 await cdp(port,'Emulation.setEmulatedMedia',{features:[]});
 result.close=await normalClose();result.pass=result.failures.length===0;
}catch(error){result.error=String(error);throw error;}
finally{if(cdpSocket)cdpSocket.close();if(launch)try{result.failureClose=await normalClose();}catch(error){result.closeError=String(error);stop();}result.root=root;result.pids=pids;if(root)await writeFile(join(root,'a8-model-geometry-evidence.json'),JSON.stringify(result,null,2)+'\n');}
console.log(JSON.stringify({root,pass:result.pass,cases:result.cases.length,catalogCases:result.catalogCases?.length,failures:result.failures.length,pids}));assert.ok(result.pass,'Model geometry and interaction checks failed; inspect owned evidence');
