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



const first='**首要建议**\n\n';
const rest='**第二建议** **第三建议**\n\n## 注意事项\n\n**注意边界** **风险重复**\n\n> **引用不着色**\n\n| 项目 | 内容 |\n| --- | --- |\n| 标记 | **表格不着色** |\n\n```txt\n**代码不着色**\n```\n\n<script data-name="__literal">window.__unsafeEmphasis=true</script>\n\n[安全来源](https://example.invalid)';
const full=first+rest;const requests=[],serverErrors=[];let releaseFirst;const firstGate=new Promise(done=>releaseFirst=done);
const provider=http.createServer(async(req,res)=>{try{
 let raw='';for await(const chunk of req)raw+=chunk.toString('utf8');const body=JSON.parse(raw);assert.equal(req.headers.authorization,'Bearer local-acceptance-mock-only');assert.equal(body.model,'openai/gpt-6.1-sol');requests.push({model:body.model,index:requests.length+1});assert.ok(requests.length<=2);
 res.writeHead(200,{'Content-Type':'text/event-stream; charset=utf-8'});const emit=value=>res.write(`data: ${JSON.stringify(value)}\n\n`);
 emit({model:body.model,choices:[{delta:{content:first}}]});res.flushHeaders();if(requests.length===1)await firstGate;
 for(const content of [rest.slice(0,38),rest.slice(38)]){emit({model:body.model,choices:[{delta:{content}}]});await new Promise(done=>setTimeout(done,100));}
 res.end(`data: ${JSON.stringify({model:body.model,choices:[{delta:{},finish_reason:'stop'}],usage:{prompt_tokens:8,completion_tokens:32,cost:0.000004}})}\n\ndata: [DONE]\n\n`);
 }catch(error){serverErrors.push(String(error));res.destroy(error);}});
await new Promise(done=>provider.listen(0,'127.0.0.1',done));const providerPort=provider.address().port;
const result={candidateSha256:receipt.sha256,backgroundOnly:true,realService:false};let port=0;
const textOf=m=>(m.blocks||[]).filter(b=>b.kind==='TEXT').map(b=>b.text||'').join('');
async function send(text){await evaluate(port,`(()=>{const i=document.querySelector('#chat-composer');i.value=${JSON.stringify(text)};i.dispatchEvent(new InputEvent('input',{bubbles:true}));return true;})()`);await until(()=>evaluate(port,"!!document.querySelector('[data-action=save-local-message]:not(:disabled)')"),'可用真实发送按钮');await clickUi(port,'[data-action=save-local-message]');}
async function replyNodes(){return evaluate(port,"[...document.querySelectorAll('.chat-message.assistant')].filter(n=>n.textContent.includes('首要建议')).map(n=>({important:n.querySelectorAll('.chat-message-body .chat-text-important').length,caution:n.querySelectorAll('.chat-message-body .chat-text-caution').length,quoteBlocks:n.querySelectorAll('blockquote').length,tableBlocks:n.querySelectorAll('table').length,codeBlocks:n.querySelectorAll('pre.chat-markdown-code').length,quote:n.querySelectorAll('blockquote [class*=chat-text-]').length,table:n.querySelectorAll('table [class*=chat-text-]').length,code:n.querySelectorAll('pre [class*=chat-text-]').length,script:n.querySelectorAll('script').length}))");}
async function styles(mode){return evaluate(port,`(async()=>{const {applyDesktopThemeToRoot}=await import('/desktop-theme-owner.mjs');applyDesktopThemeToRoot(document.documentElement,{...globalThis.__nanfengDesktopWorkState.appearance,mode:${JSON.stringify(mode)}});const sample=kind=>{const n=document.querySelector('.chat-message-body .chat-text-'+kind);let p=n,bg='';while(p){bg=getComputedStyle(p).backgroundColor;if(bg!=='rgba(0, 0, 0, 0)'&&bg!=='transparent')break;p=p.parentElement;}return {color:getComputedStyle(n).color,background:bg};};return {important:sample('important'),caution:sample('caution')};})()`);}
function contrast(a,b){const channels=s=>s.match(/[\d.]+/g).slice(0,3).map(Number).map(v=>{v/=255;return v<=0.04045?v/12.92:((v+0.055)/1.055)**2.4;});const l=s=>{const c=channels(s);return c[0]*0.2126+c[1]*0.7152+c[2]*0.0722;};return (Math.max(l(a),l(b))+0.05)/(Math.min(l(a),l(b))+0.05);}
try{
 port=await start();result.root=root;await ready(port);const ws=(await invoke(port,'CHAT','ensure_desktop_local_workspace')).summary.id;
 const setting=(await invoke(port,'CHAT','read_desktop_model_service_settings')).find(p=>p.providerId==='OPENROUTER');await invoke(port,'CHAT','save_desktop_model_service_settings',{args:{providerId:'OPENROUTER',enabled:true,presetId:'GPT_6_1_SOL',expectedRevision:setting.revision,apiKey:'nfai-synthetic-reply-emphasis'}});
 const conv=(await invoke(port,'CHAT','read_desktop_workspace',{workspaceId:ws})).exchange.conversations[0];const selection=await invoke(port,'CHAT','read_desktop_p6g_selection',{workspaceId:ws,conversationId:conv.id});await invoke(port,'CHAT','set_desktop_p6g_conversation_override',{args:{workspaceId:ws,conversationId:conv.id,expectedRevision:selection.conversationOverride.revision,modelId:'GPT_6_1_SOL'}});
 const current=await invoke(port,'CHAT','read_desktop_p6g_selection',{workspaceId:ws,conversationId:conv.id});await invoke(port,'CHAT','set_desktop_conversation_preferences',{args:{workspaceId:ws,conversationId:conv.id,expectedRevision:current.conversationOverride.revision,toneOverride:null,webSearchOverride:false}});result.seedClose=await normalClose();port=await start(true);await ready(port);
 await until(()=>evaluate(port,`!!document.querySelector('[data-action="select-chat"][data-id="${conv.id}"]')`),'准确合成会话入口');await clickUi(port,`[data-action="select-chat"][data-id="${conv.id}"]`);
 await send('仅本机合成着色验收一，**用户原文**');await until(async()=>{const n=await replyNodes();return n.length===1&&n[0].important===1;},'首段流式重点');result.streaming=await replyNodes();releaseFirst();
 await until(async()=>{const c=(await invoke(port,'CHAT','read_desktop_workspace',{workspaceId:ws})).exchange.conversations.find(c=>c.id===conv.id);return c.messages.some(m=>textOf(m)===full);},'第一回复实际持久化');await until(async()=>{const n=await replyNodes();return n.length===1&&n[0].important===2&&n[0].caution===1;},'第一完整回复预算');
 await until(()=>evaluate(port,"!globalThis.__nanfengDesktopWorkState.pendingChatSubmission"),'首轮发送结束');await send('仅本机合成着色验收二');await until(async()=>{const n=await replyNodes();return n.length===2&&n.every(n=>n.important===2&&n.caution===1);},'第二回复独立预算');
 result.replies=await replyNodes();assert.ok(result.replies.every(n=>n.quoteBlocks===1&&n.tableBlocks===1&&n.codeBlocks===1&&n.quote===0&&n.table===0&&n.code===0&&n.script===0));assert.equal(await evaluate(port,"globalThis.__unsafeEmphasis===true"),false);
 result.styles={light:await styles('light'),dark:await styles('dark')};assert.equal(result.styles.light.important.color,'rgb(44, 101, 93)');assert.equal(result.styles.light.caution.color,'rgb(146, 82, 46)');assert.equal(result.styles.dark.important.color,'rgb(154, 200, 188)');assert.equal(result.styles.dark.caution.color,'rgb(228, 177, 130)');for(const mode of ['light','dark'])for(const tone of ['important','caution']){const sample=result.styles[mode][tone];sample.contrast=contrast(sample.color,sample.background);assert.ok(sample.contrast>=4.5,`${mode}/${tone} contrast ${sample.contrast}`);}await styles('light');
 result.close=await normalClose();port=await start(true);await ready(port);await clickUi(port,`[data-action="select-chat"][data-id="${conv.id}"]`);await until(async()=>{const n=await replyNodes();return n.length===2&&n.every(n=>n.important===2&&n.caution===1);},'冷启两回复预算');const cold=(await invoke(port,'CHAT','read_desktop_workspace',{workspaceId:ws})).exchange.conversations.find(c=>c.id===conv.id);assert.equal(cold.messages.filter(m=>textOf(m)===full).length,2);result.coldSourcePreserved=true;result.coldClose=await normalClose();assert.equal(requests.length,2);assert.deepEqual(serverErrors,[]);result.pass=true;
}catch(error){result.pass=false;result.error=String(error);result.stack=error.stack;process.exitCode=1;}
finally{releaseFirst();if(launch){try{result.failureClose=await normalClose();}catch(error){result.cleanupError=String(error);try{stop();result.forcedCleanup=true;}catch(error){result.forceCleanupError=String(error);}}}provider.closeAllConnections();await new Promise(done=>provider.close(done));result.requests=requests;result.serverErrors=serverErrors;result.pids=pids;if(root)await writeFile(join(root,'a8-reply-emphasis-native-evidence.json'),JSON.stringify(result,null,2),{flag:'wx'});}
console.log(JSON.stringify({root,pass:result.pass,error:result.error,pids}));
