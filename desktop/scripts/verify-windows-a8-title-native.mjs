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
const receipt = JSON.parse(await readFile(join(desktopRoot, 'output',candidateNamespace,'current.json'), 'utf8'));
assert.equal(receipt.identifier, 'com.nanzhufeng.ai.desktop.windowsacceptance');
assert.equal(receipt.buildProfile,process.argv.includes('--release')?'release':'debug');
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
    '-StartupMode', 'normal', '-DebugPort', String(port), '-ExecutablePath',source];
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

const result={candidateSha256:receipt.sha256,backgroundOnly:true,realService:false};
async function beginTitle(port, surface='header', id='') {
  const expression=surface==='header' ? "document.querySelector('.chat-title h1')" : `document.querySelector('[data-conversation-row][data-id="${id}"] .chat-history-title')`;
  assert.equal(await evaluate(port,`(()=>{const target=${expression};if(!target)return false;target.dispatchEvent(new MouseEvent('dblclick',{bubbles:true,detail:2}));return true;})()`),true);
  await until(()=>evaluate(port,"!!document.querySelector('[data-inline-conversation-rename]')"),'原位标题输入框');
}
async function typeTitle(port,title,key='Enter') {
  assert.equal(await evaluate(port,`(()=>{const input=document.querySelector('[data-inline-conversation-rename]');if(!input)return false;input.value=${JSON.stringify(title)};input.dispatchEvent(new InputEvent('input',{bubbles:true}));input.dispatchEvent(new KeyboardEvent('keydown',{key:${JSON.stringify(key)},bubbles:true}));return true;})()`),true);
}
async function saved(port,workspaceId,id,title) {
  await until(async()=>{const data=await invoke(port,'CHAT','read_desktop_workspace',{workspaceId});return data.exchange.conversations.find(c=>c.id===id)?.title===title;},'标题真实持久化');
  await until(()=>evaluate(port,"!document.querySelector('[data-inline-conversation-rename]')"),'保存结束退出原位编辑');
}
try {
  let port=await start();result.root=root;await ready(port);result.phase='ready';
  const workspaceId=(await invoke(port,'CHAT','ensure_desktop_local_workspace')).summary.id;
  const create=title=>invoke(port,'CHAT','mutate_desktop_domain',{args:{intentId:randomUUID(),workspaceId,entity:'conversation',action:'create',fields:{title,firstMessage:'仅用于原位标题验收的合成本机记录',projectId:null}}});
  const id=(await create('合成已有会话一')).objectId,otherId=(await create('合成已有会话二')).objectId;
  assert.ok(id);assert.ok(otherId);result.seedClose=await normalClose();
  port=await start(true);await ready(port);
  await until(()=>evaluate(port,`!!document.querySelector('[data-action="select-chat"][data-id="${id}"]')`),'已有合成会话入口');
  await clickUi(port,`[data-action="select-chat"][data-id="${id}"]`);
  await until(()=>evaluate(port,`globalThis.__nanfengDesktopWorkState.selectedConversationId===${JSON.stringify(id)}`),'准确选中已有会话');
  await until(()=>evaluate(port,"!!document.querySelector('.chat-title h1')"),'对话标题');
  await beginTitle(port);result.inputStyle=await evaluate(port,"(()=>{const s=getComputedStyle(document.querySelector('[data-inline-conversation-rename]'));return {outline:s.outlineStyle,shadow:s.boxShadow};})()");
  assert.deepEqual(result.inputStyle,{outline:'none',shadow:'none'});
  await typeTitle(port,'   回车保存合成标题   ');await saved(port,workspaceId,id,'回车保存合成标题');result.enterSaved=true;
  await beginTitle(port,'sidebar',id);await typeTitle(port,'Escape不应写入','Escape');
  assert.equal((await invoke(port,'CHAT','read_desktop_workspace',{workspaceId})).exchange.conversations.find(c=>c.id===id).title,'回车保存合成标题');result.escapePreserved=true;
  await beginTitle(port);await evaluate(port,"(()=>{const i=document.querySelector('[data-inline-conversation-rename]');i.value='失焦保存合成标题';i.dispatchEvent(new InputEvent('input',{bubbles:true}));i.dispatchEvent(new FocusEvent('focusout',{bubbles:true}));})()");
  await saved(port,workspaceId,id,'失焦保存合成标题');result.blurSaved=true;
  await beginTitle(port);const longTitle='长标题'.repeat(35);
  await evaluate(port,`(()=>{const i=document.querySelector('[data-inline-conversation-rename]');i.value=${JSON.stringify(longTitle)};i.dispatchEvent(new InputEvent('input',{bubbles:true}));i.focus();i.setSelectionRange(10,20,'backward');document.querySelector('[data-action="toggle-chat-sidebar"]').click();})()`);
  assert.deepEqual(await evaluate(port,"(()=>{const i=document.querySelector('[data-inline-conversation-rename]');return {value:i.value,start:i.selectionStart,end:i.selectionEnd,direction:i.selectionDirection};})()"),{value:longTitle,start:10,end:20,direction:'backward'});
  await typeTitle(port,longTitle);await saved(port,workspaceId,id,longTitle);result.longSelectionPreserved=true;
  await beginTitle(port);
  await evaluate(port,"(()=>{const i=document.querySelector('[data-inline-conversation-rename]');window.__titleImeInput=i;i.focus();i.value='中文候选中';i.dispatchEvent(new CompositionEvent('compositionstart',{bubbles:true}));i.dispatchEvent(new InputEvent('input',{bubbles:true,isComposing:true}));i.setSelectionRange(1,4,'backward');document.querySelector('[data-action=\"toggle-chat-sidebar\"]').click();i.dispatchEvent(new KeyboardEvent('keydown',{key:'Enter',isComposing:true,bubbles:true}));})()");
  assert.deepEqual(await evaluate(port,"(()=>{const i=document.querySelector('[data-inline-conversation-rename]');return {connected:i===window.__titleImeInput&&i.isConnected,value:i.value};})()"),{connected:true,value:'中文候选中'});
  assert.equal((await invoke(port,'CHAT','read_desktop_workspace',{workspaceId})).exchange.conversations.find(c=>c.id===id).title,longTitle);
  await evaluate(port,"(()=>{const i=document.querySelector('[data-inline-conversation-rename]');i.value='中文最终标题';i.dispatchEvent(new CompositionEvent('compositionend',{bubbles:true}));i.dispatchEvent(new InputEvent('input',{bubbles:true}));})()");
  await typeTitle(port,'中文最终标题');await saved(port,workspaceId,id,'中文最终标题');result.imePreserved=true;
  await beginTitle(port);await typeTitle(port,'   ');
  await until(()=>evaluate(port,"document.querySelector('.chat-inline-title-error')?.textContent.includes('不能为空')"),'空标题保稿');result.emptyDraftPreserved=await evaluate(port,"document.querySelector('[data-inline-conversation-rename]').value==='   '");assert.equal(result.emptyDraftPreserved,true);
  await typeTitle(port,'冲突时未保存的原始草稿');
  // The preceding Enter would save before a conflict is introduced, so finish
  // that independent write and reopen against a captured revision first.
  await saved(port,workspaceId,id,'冲突时未保存的原始草稿');await beginTitle(port);
  const row=(await invoke(port,'CHAT','read_desktop_workspace',{workspaceId})).exchange.conversations.find(c=>c.id===id);
  await invoke(port,'CHAT','mutate_desktop_domain',{args:{intentId:randomUUID(),workspaceId,entity:'conversation',action:'update',objectId:id,expectedRevision:row.revision,fields:{title:'外部并发已保存标题'}}});
  await typeTitle(port,'  冲突草稿须完整保留  ');
  await until(()=>evaluate(port,"/被拒绝|冲突|revision/i.test(document.querySelector('.chat-inline-title-error')?.textContent||'')"),'revision冲突拒写');
  assert.equal(await evaluate(port,"document.querySelector('[data-inline-conversation-rename]').value"),'  冲突草稿须完整保留  ');result.conflictDraftPreserved=true;
  assert.equal((await invoke(port,'CHAT','read_desktop_workspace',{workspaceId})).exchange.conversations.find(c=>c.id===id).title,'外部并发已保存标题');
  await typeTitle(port,'  冲突草稿须完整保留  ','Escape');
  // This runtime exposes a read-only Tauri invoke bridge. The original
  // attempted delay and its failure remain in the 1878 root; do not claim it
  // as a passing native race or weaken the existing production owner guard.
  result.delayedNativeReceipt={status:'NOT_VERIFIED',reason:'Tauri invoke bridge read-only; production-owner behavior tests cover this separately'};
  result.windows=JSON.parse(ps(['-File',join(desktopRoot,'scripts/inspect-windows-island-acceptance.ps1'),'-ProcessId',String(launch.pid),'-Executable',runExecutable,'-Sha256',receipt.sha256,'-StartedAt',launch.processStartedAt]));
  assert.equal(result.windows.foregroundOwned,false);assert.ok(result.windows.windows.every(w=>!w.visible));result.close=await normalClose();
  port=await start(true);await ready(port);
  assert.equal((await invoke(port,'CHAT','read_desktop_workspace',{workspaceId})).exchange.conversations.find(c=>c.id===id).title,'外部并发已保存标题');
  result.coldReadback=true;result.coldClose=await normalClose();result.pass=true;result.phase='closed';
} catch(error) {result.pass=false;result.error=String(error);process.exitCode=1;if(launch){try{result.pageDiagnostic=await evaluate(launch.debugPort,"({body:document.body.innerText.slice(0,500),selected:globalThis.__nanfengDesktopWorkState?.selectedConversationId,error:globalThis.__nanfengDesktopWorkState?.error})");}catch{}}}
finally {
  if(launch){try{result.failureClose=await normalClose();}catch{result.forcedCleanup=true;try{stop();}catch(error){result.cleanupError=String(error);}}}
  result.pids=pids;if(root)await writeFile(join(root,'a8-title-native-evidence.json'),JSON.stringify(result,null,2),{flag:'wx'});
}
console.log(JSON.stringify({root,pass:result.pass,error:result.error,phase:result.phase,pids}));








