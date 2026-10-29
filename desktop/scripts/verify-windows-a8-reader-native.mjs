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
    '-ExecutablePath',source,'-StartupMode', 'normal', '-DebugPort', String(port)];
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


const result={candidateSha256:receipt.sha256,backgroundOnly:true,realService:false,osClipboardSharePicker:'NOT_RUN'};
const q=JSON.stringify;
const emptyCopy=process.argv.includes('--empty-copy');
async function key(port,key){return evaluate(port,`(()=>{const e=new KeyboardEvent('keydown',{key:${q(key)},bubbles:true,cancelable:true});document.activeElement.dispatchEvent(e);return e.defaultPrevented;})()`);}
async function menu(port){await clickUi(port,'.text-preview-menu > summary');await until(()=>evaluate(port,"(()=>{const m=document.querySelector('.text-preview-menu'),p=m?.querySelector('.text-preview-menu-items');return m?.open&&p.getBoundingClientRect().height>0&&getComputedStyle(p).display!=='none';})()"),'实际可用菜单');}

async function open(port,id){await clickUi(port,`[data-action="open-text-preview"][data-attachment-id="${id}"]`);await until(()=>evaluate(port,"!!document.querySelector('.local-text-preview')&&!globalThis.__nanfengDesktopWorkState.textPreview?.loading"),'真实文本读取');}
let port=0;
try {
 port=await start();result.root=root;await ready(port);result.runtimeInfo=await invoke(port,'CHAT','read_desktop_runtime_info');assert.equal(result.runtimeInfo.buildProfile,expectedBuildProfile);
 const workspaceId=(await invoke(port,'CHAT','ensure_desktop_local_workspace')).summary.id;
 const filename='合成阅读器长文件名用于居中省略号与准确文件身份核验'.repeat(6)+'.md';
 const text='普通合成正文\n\n**文件内粗体**\n\n[惰性链接](https://example.invalid)\n\n<script>globalThis.__readerUnsafe=true</script>\n\n'+Array.from({length:180},(_,i)=>`第${i+1}行 合成阅读材料，不发送至任何服务。`).join('\n\n');
 const asset=await invoke(port,'CHAT','import_desktop_conversation_clipboard_attachment',{args:{workspaceId,displayName:filename,bytesBase64:Buffer.from(text).toString('base64')}});
 const headed=await invoke(port,'CHAT','import_desktop_conversation_clipboard_attachment',{args:{workspaceId,displayName:'已有标题.md',bytesBase64:Buffer.from('# 原文标题\n\n合成标题正文。').toString('base64')}});
 const empty=emptyCopy?await invoke(port,'CHAT','import_desktop_conversation_clipboard_attachment',{args:{workspaceId,displayName:'仅BOM的空文本.txt',bytesBase64:Buffer.from([0xef,0xbb,0xbf]).toString('base64')}}):null;
 await invoke(port,'CHAT','save_desktop_conversation_draft',{args:{workspaceId,conversationId:null,text:'合成阅读器原草稿',attachmentIds:[asset.id,headed.id,...(empty?[empty.id]:[])]}});
 result.seedClose=await normalClose();port=await start(true);await ready(port);
 await until(()=>evaluate(port,"!!document.querySelector('[data-action=new-chat]')"),'新聊天入口');await clickUi(port,'[data-action=new-chat]');
 await until(()=>evaluate(port,`!!document.querySelector('[data-action="open-text-preview"][data-attachment-id="${asset.id}"]')`),'原草稿真实附件卡');
 const draft=await invoke(port,'CHAT','read_desktop_conversation_draft',{workspaceId});
 await open(port,asset.id);
 const view=await evaluate(port,`(()=>{const r=document.querySelector('.text-preview-header').getBoundingClientRect(),t=document.querySelector('.text-preview-title'),b=t.getBoundingClientRect(),a=document.querySelector('.local-text-preview');return {name:t.textContent,centerError:Math.abs((r.left+r.right-b.left-b.right)/2),ellipsis:getComputedStyle(t).textOverflow,overflow:t.scrollWidth>t.clientWidth,title:a.querySelector('h1')?.textContent,interactive:a.querySelectorAll('a,button,input,script,iframe').length,colored:a.querySelectorAll('.chat-text-important,.chat-text-caution').length,unsafe:globalThis.__readerUnsafe===true,white:getComputedStyle(document.querySelector('.text-preview-surface')).backgroundColor};})()`);
 result.reader=view;assert.equal(view.name,filename);assert.ok(view.centerError<1);assert.equal(view.ellipsis,'ellipsis');assert.equal(view.overflow,true);assert.equal(view.title,filename.slice(0,-3));assert.equal(view.interactive,0);assert.equal(view.colored,0);assert.equal(view.unsafe,false);result.reader=view;
 await evaluate(port,"(()=>{const a=document.querySelector('.local-text-preview');globalThis.__readerSetupScrolled=false;a.addEventListener('scroll',()=>{globalThis.__readerSetupScrolled=true;},{once:true});a.scrollTop=240;const text=[...a.querySelectorAll('p')].find(p=>p.textContent.includes('合成阅读材料')).firstChild;const r=document.createRange();r.setStart(text,0);r.setEnd(text,3);getSelection().removeAllRanges();getSelection().addRange(r);return true;})()");
 await until(()=>evaluate(port,'globalThis.__readerSetupScrolled===true'),'夹具正文滚动送达后再开菜单');result.setupScrollDelivered=true;
 const retained=await evaluate(port,"({scroll:document.querySelector('.local-text-preview').scrollTop,selection:getSelection().toString()})");
 await menu(port);
 result.menu=await evaluate(port,"(()=>{const p=document.querySelector('.text-preview-menu-items'),b=p.getBoundingClientRect();return {labels:[...p.querySelectorAll('button span')].map(n=>n.textContent.trim()),invalidGlyphs:!!p.querySelector('[d=undefined]'),popoverApi:typeof p.showPopover==='function',hit:!!document.elementFromPoint(b.left+15,b.top+15)?.closest('.text-preview-menu-items'),white:getComputedStyle(p).backgroundColor,left:b.left,right:b.right,top:b.top,bottom:b.bottom,w:innerWidth,h:innerHeight};})()");
 assert.deepEqual(result.menu.labels,['下载文件','分享文件','复制文本']);assert.equal(result.menu.invalidGlyphs,false);assert.equal(result.menu.hit,true);assert.equal(result.menu.white,'rgb(255, 255, 255)');assert.ok(result.menu.left>=7&&result.menu.right<=result.menu.w-7&&result.menu.top>=7&&result.menu.bottom<=result.menu.h-7);
 await cdp(port,'Emulation.setDeviceMetricsOverride',{width:640,height:480,deviceScaleFactor:1,mobile:false});
 await evaluate(port,"document.documentElement.style.setProperty('--app-font-scale','1.4');true");
 await new Promise(done=>setTimeout(done,250));result.viewportChangeClosed=await evaluate(port,"!document.querySelector('.text-preview-menu').open");if(result.viewportChangeClosed)await menu(port);
 result.scaleAndViewport=await until(()=>evaluate(port,"(()=>{const p=document.querySelector('.text-preview-menu-items'),b=p.getBoundingClientRect();if(b.right>innerWidth-7||b.bottom>innerHeight-7||b.left<7||b.top<7)return false;return {left:b.left,right:b.right,top:b.top,bottom:b.bottom,w:innerWidth,h:innerHeight,fontScale:getComputedStyle(document.documentElement).getPropertyValue('--app-font-scale'),hit:!!document.elementFromPoint(b.left+15,b.top+15)?.closest('.text-preview-menu-items')};})()"),'640视口/字体缩放菜单边界');assert.equal(result.scaleAndViewport.hit,true);
 await cdp(port,'Emulation.clearDeviceMetricsOverride');await evaluate(port,"document.documentElement.style.removeProperty('--app-font-scale');true");
 await evaluate(port,"document.querySelector('.text-preview-menu > summary').focus({preventScroll:true});true");
 await key(port,'ArrowDown');assert.equal(await evaluate(port,"document.activeElement.dataset.action"),'save-preview-attachment');
 await key(port,'End');assert.equal(await evaluate(port,"document.activeElement.dataset.action"),'copy-text-preview');
 await key(port,'ArrowDown');assert.equal(await evaluate(port,"document.activeElement.dataset.action"),'save-preview-attachment');
 await key(port,'Home');await key(port,'Tab');
 assert.equal(await evaluate(port,"!document.querySelector('.text-preview-menu').open&&document.activeElement.matches('.text-preview-menu > summary')"),true);
 await menu(port);await key(port,'Escape');assert.equal(await evaluate(port,"!!document.querySelector('.local-text-preview')&&!document.querySelector('.text-preview-menu').open"),true);
 await menu(port);
 const outside=await evaluate(port,"(()=>{const b=document.querySelector('[data-action=close-text-preview]');const consumed=[];for(const type of ['pointerdown','pointerup','click']){const e=type==='click'?new MouseEvent(type,{bubbles:true,cancelable:true,detail:1}):new PointerEvent(type,{bubbles:true,cancelable:true,pointerId:17});b.dispatchEvent(e);consumed.push(e.defaultPrevented);}return {consumed,reader:!!document.querySelector('.local-text-preview'),open:document.querySelector('.text-preview-menu')?.open};})()");
 assert.deepEqual(outside,{consumed:[true,true,true],reader:true,open:false});result.outside=outside;
 assert.deepEqual(await evaluate(port,"({scroll:document.querySelector('.local-text-preview').scrollTop,selection:getSelection().toString()})"),retained);result.scrollSelectionPreserved=true;
 await menu(port);await evaluate(port,"window.dispatchEvent(new Event('blur'));true");assert.equal(await evaluate(port,"document.querySelector('.text-preview-menu').open"),false);result.blurClosed=true;
 await evaluate(port,"document.querySelector('.text-preview-menu > summary').focus({preventScroll:true});true");await key(port,'Escape');await until(()=>evaluate(port,"!document.querySelector('.text-preview-overlay')"),'第二层Escape关闭阅读器');
 await open(port,headed.id);assert.equal(await evaluate(port,"document.querySelector('.local-text-preview').querySelectorAll('h1,h2').length"),1);assert.equal(await evaluate(port,"document.querySelector('.local-text-preview h1,.local-text-preview h2').textContent"),'原文标题');
 await clickUi(port,'[data-action=close-text-preview]');
 if(empty){
  const read=await invoke(port,'CHAT','read_desktop_text_preview',{args:{workspaceId,attachmentId:empty.id}});assert.equal(read.text,'');assert.equal(read.byteCount,3);
  await evaluate(port,"(()=>{globalThis.__readerCopyRequests=[];Object.defineProperty(navigator,'clipboard',{configurable:true,value:{writeText:async text=>{globalThis.__readerCopyRequests.push(text);}}});return true;})()");
  await open(port,empty.id);await menu(port);await clickUi(port,'[data-action=copy-text-preview]');
  assert.deepEqual(await evaluate(port,'globalThis.__readerCopyRequests'),['']);
  await until(()=>evaluate(port,"document.querySelector('.text-preview-menu > summary')?.getAttribute('aria-label')==='已复制'"),'空文本复制反馈');
  result.emptyTextCopy={nativeReadEmpty:true,originalBytes:3,adapterValues:[''],osClipboardTouched:false};
  await clickUi(port,'[data-action=close-text-preview]');
 }
 assert.deepEqual(await invoke(port,'CHAT','read_desktop_conversation_draft',{workspaceId}),draft);
 result.close=await normalClose();port=await start(true);await ready(port);assert.deepEqual(await invoke(port,'CHAT','read_desktop_conversation_draft',{workspaceId}),draft);result.coldDraftPreserved=true;result.coldClose=await normalClose();result.pass=true;
}catch(error){result.pass=false;result.error=String(error);result.stack=error.stack;if(launch&&port)try{result.failureDOM=await evaluate(port,"(()=>{const m=document.querySelector('.text-preview-menu'),p=m?.querySelector('.text-preview-menu-items');return {open:m?.open,panel:p?.getBoundingClientRect().toJSON(),display:p&&getComputedStyle(p).display,w:innerWidth,h:innerHeight};})()");}catch{}process.exitCode=1;}
finally{if(launch){try{result.failureClose=await normalClose();}catch(error){result.cleanupError=String(error);try{stop();result.forcedCleanup=true;}catch(error){result.forceCleanupError=String(error);}}}result.pids=pids;if(root)await writeFile(join(root,'a8-reader-native-evidence.json'),JSON.stringify(result,null,2),{flag:'wx'});}
console.log(JSON.stringify({root,pass:result.pass,error:result.error,pids}));
