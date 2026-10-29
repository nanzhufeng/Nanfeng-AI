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
assert.ok(rootParent.startsWith(resolve('D:/CodexTemp')+sep));assert.ok(rootParent.split(sep).at(-1).startsWith('nfai-draft-navigation-'));
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


const result={candidateSha256:receipt.sha256,backgroundOnly:true,realService:false};let port;
const textA='A_DRAFT_FIRST\n  preserve spaces  ',textB='B_DRAFT_SECOND',textNew='NEW_UNBOUND_DRAFT';
async function select(id,text){await until(()=>evaluate(port,`!!document.querySelector('[data-action="select-chat"][data-id="${id}"]')`),'会话入口');await clickUi(port,`[data-action="select-chat"][data-id="${id}"]`);await until(()=>evaluate(port,`__nanfengDesktopWorkState?.selectedConversationId===${JSON.stringify(id)}&&document.querySelector('#chat-composer')?.value===${JSON.stringify(text)}`),'准确会话草稿');}
try{
 port=await start();await ready(port);result.runtimeInfo=await invoke(port,'CHAT','read_desktop_runtime_info');
 const workspaceId=(await invoke(port,'CHAT','ensure_desktop_local_workspace')).summary.id;result.workspaceId=workspaceId;
 const create=title=>invoke(port,'CHAT','mutate_desktop_domain',{args:{intentId:randomUUID(),workspaceId,entity:'conversation',action:'create',fields:{title,firstMessage:'仅用于草稿导航的合成本机记录',projectId:null}}});
 const idA=(await create('导航合成会话A')).objectId,idB=(await create('导航合成会话B')).objectId;result.ids={idA,idB};
 const save=(conversationId,text,modelId=null)=>invoke(port,'CHAT','save_desktop_conversation_draft',{args:{workspaceId,conversationId,text,attachmentIds:[],modelId,toneOverride:conversationId===null?'professional':null,webSearchOverride:conversationId===null?false:null}});
 await save(idA,textA);await save(idB,textB);await save(null,textNew,'DEEPSEEK_V4_PRO');result.seedClose=await normalClose();
 port=await start(true);await ready(port);await select(idA,textA);await select(idB,textB);
 const order=[idA,idB,idA,idB,idA,idB];
 assert.equal(await evaluate(port,`(()=>{for(const id of ${JSON.stringify(order)}){const b=document.querySelector('[data-action="select-chat"][data-id="'+id+'"]');if(!b||!b.getClientRects().length||b.disabled)return false;b.click();}return true})()`),true);
 await until(()=>evaluate(port,`__nanfengDesktopWorkState.selectedConversationId===${JSON.stringify(idB)}&&document.querySelector('#chat-composer')?.value===${JSON.stringify(textB)}`),'快速导航最终B');result.rapidLatestDestination=true;
 await clickUi(port,'.chat-header-content-actions [data-action="new-chat"]');
 await until(()=>evaluate(port,`__nanfengDesktopWorkState.selectedConversationId===null&&document.querySelector('#chat-composer')?.value===${JSON.stringify(textNew)}&&__nanfengDesktopWorkState.pendingNewConversationModelId==='DEEPSEEK_V4_PRO'`),'新会话草稿及原精确型号');
 result.unboundModelPreserved=true;
 await select(idA,textA);await select(idB,textB);
 for(const [id,text] of [[idA,textA],[idB,textB],[null,textNew]]){const d=await invoke(port,'CHAT','read_desktop_conversation_draft',{workspaceId,conversationId:id});assert.equal(d.text,text);if(id===null)assert.equal(d.modelId,'DEEPSEEK_V4_PRO');}
 result.ledger=await invoke(port,'CHAT','read_desktop_usage_ledger');assert.equal(result.ledger.records.length,0);result.close=await normalClose();
 port=await start(true);await ready(port);await select(idA,textA);await select(idB,textB);await clickUi(port,'.chat-header-content-actions [data-action="new-chat"]');
 await until(()=>evaluate(port,`document.querySelector('#chat-composer')?.value===${JSON.stringify(textNew)}&&__nanfengDesktopWorkState.pendingNewConversationModelId==='DEEPSEEK_V4_PRO'`),'冷启三草稿与型号');
 assert.deepEqual(await invoke(port,'CHAT','read_desktop_usage_ledger'),result.ledger);result.coldReadback=true;result.coldClose=await normalClose();result.pass=true;
}catch(error){result.pass=false;result.error=String(error);process.exitCode=1;if(launch)try{result.diagnostic=await evaluate(port,'({text:document.body.innerText.slice(0,600),error:__nanfengDesktopWorkState?.error})');}catch{}}
finally{if(launch)try{result.failureClose=await normalClose();}catch(error){result.closeError=String(error);try{stop();result.forcedCleanup=true;}catch(cleanupError){result.cleanupError=String(cleanupError);}}result.root=root;result.pids=pids;if(root)await writeFile(join(root,'draft-navigation-native-evidence.json'),JSON.stringify(result,null,2),{flag:'wx'});}
console.log(JSON.stringify({pass:result.pass,error:result.error,root,pids}));
