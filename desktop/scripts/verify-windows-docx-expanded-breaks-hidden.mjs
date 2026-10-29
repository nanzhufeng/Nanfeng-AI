import assert from 'node:assert/strict';
import { createHash, randomUUID } from 'node:crypto';
import { execFileSync, spawn } from 'node:child_process';
import { readFile, writeFile, stat } from 'node:fs/promises';
import http from 'node:http';
import { dirname, join, resolve, sep } from 'node:path';
import { fileURLToPath } from 'node:url';

if (process.platform !== 'win32') throw new Error('仅在 Windows 隔离候选上验收。');
const desktopRoot = resolve(dirname(fileURLToPath(import.meta.url)), '..');
assert.ok(process.argv[2], 'Explicit synthetic root parent required');
const rootParent = resolve(process.argv[2]);
assert.ok(process.execArgv.includes('--max-old-space-size=2048'));
if (process.argv.includes('--screenshot')) throw new Error('后台验收不支持前台截图');
const receipt = JSON.parse(await readFile(join(desktopRoot, 'output/windows-acceptance-release/current.json'), 'utf8'));
assert.equal(receipt.identifier, 'com.nanzhufeng.ai.desktop.windowsacceptance');
assert.equal(receipt.buildProfile,'release');
const inputIndex=process.argv.indexOf('--input-manifest');
const inputPath=inputIndex<0 ? join(desktopRoot,'../docs/review/20261018-windows-docx-expanded-breaks/source-input-manifest-release-v1.json') : resolve(process.argv[inputIndex+1]);
assert.ok(inputPath.startsWith(resolve(desktopRoot,'../docs/review')+sep));

const inputBytes=await readFile(inputPath);
const sourceInputSha256=createHash('sha256').update(inputBytes).digest('hex');
const source = resolve(desktopRoot, 'output/windows-acceptance-release', receipt.sha256, 'NanfengAI-Windows-Acceptance.exe');
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
    '-ExecutablePath',source,'-StartupMode', 'normal', '-DebugPort', String(port),'-OrdinaryChatTestEndpoint',`http://127.0.0.1:${providerPort}`];
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


const expected='收入100\n成本60\t净利40\n\n尾段\n';
const requests=[],providerErrors=[];
const provider=http.createServer(async(req,res)=>{try{
 let raw='';for await(const chunk of req)raw+=chunk.toString('utf8');const body=JSON.parse(raw);
 assert.equal(req.headers.authorization,'Bearer local-acceptance-mock-only');assert.equal(body.model,'openai/gpt-6.1-sol');assert.equal(body.stream,true);assert.equal(requests.length,0);
 const texts=body.messages.flatMap(m=>typeof m.content==='string'?[m.content]:Array.isArray(m.content)?m.content.filter(p=>p.type==='text').map(p=>p.text):[]);
 assert.ok(texts.some(t=>t.includes(expected.trimEnd())),'exact DOCX run separators reach the original model');requests.push({model:body.model,runSeparatorsExact:true});
 res.writeHead(200,{'Content-Type':'text/event-stream'});res.end('data: '+JSON.stringify({model:body.model,choices:[{delta:{content:'DOCX_SEPARATORS_CONFIRMED'},finish_reason:null}]})+'\n\ndata: '+JSON.stringify({model:body.model,choices:[{delta:{},finish_reason:'stop'}],usage:{prompt_tokens:8,completion_tokens:5,cost:.000004}})+'\n\ndata: [DONE]\n\n');
}catch(error){providerErrors.push(String(error));res.destroy(error);}});
await new Promise(done=>provider.listen(0,'127.0.0.1',done));const providerPort=provider.address().port;
const result={sha256:receipt.sha256,realService:false,backgroundOnly:true};let port=0;
try{
 port=await start();result.root=root;await ready(port);result.runtimeInfo=await invoke(port,'CHAT','read_desktop_runtime_info');assert.equal(result.runtimeInfo.sourceInputSha256,sourceInputSha256);
 const ws=(await invoke(port,'CHAT','ensure_desktop_local_workspace')).summary.id;
 const bytes=await readFile(resolve(desktopRoot,'../docs/review/20261018-windows-docx-expanded-breaks/expanded-breaks.docx'));
 const attachment=await invoke(port,'CHAT','import_desktop_conversation_clipboard_attachment',{args:{workspaceId:ws,displayName:'synthetic-lines.docx',bytesBase64:bytes.toString('base64')}});result.attachment=attachment;
 result.preview=await invoke(port,'CHAT','read_desktop_text_preview',{args:{workspaceId:ws,attachmentId:attachment.id}});assert.equal(result.preview.text,expected);assert.equal(result.preview.truncated,false);
 const setting=(await invoke(port,'CHAT','read_desktop_model_service_settings')).find(s=>s.providerId==='OPENROUTER');
 await invoke(port,'CHAT','save_desktop_model_service_settings',{args:{providerId:'OPENROUTER',enabled:true,presetId:'GPT_6_1_SOL',expectedRevision:setting.revision,apiKey:'nfai-synthetic-docx-separators'}});
 const text='请按原文档读取收入和成本，保持行及制表分隔。';const preview=await invoke(port,'CHAT','preview_desktop_ordinary_chat_egress',{args:{workspaceId:ws,conversationId:null,text,attachmentIds:[attachment.id],modelId:'GPT_6_1_SOL',webSearchOverride:false}});
 const auth={approvedAtMs:Date.now(),disclosureVersion:'normal-chat-egress-v2',clientSubmissionId:randomUUID(),draftFingerprint:preview.draftFingerprint,recipientChoiceId:preview.recipientChoiceId,recipientProviderId:preview.recipientProviderId,recipientModelId:preview.recipientModelId,retrievalRecipient:preview.retrievalRecipient};
 result.attempt=await invoke(port,'CHAT','submit_desktop_ordinary_chat',{args:{workspaceId:ws,conversationId:null,expectedRevision:null,text,attachmentIds:[attachment.id],modelId:'GPT_6_1_SOL',webSearchOverride:false,egressAuthorization:auth}});
 await until(async()=>{const w=await invoke(port,'CHAT','read_desktop_workspace',{workspaceId:ws});return w.exchange.conversations.some(c=>c.messages.some(m=>JSON.stringify(m.blocks).includes('DOCX_SEPARATORS_CONFIRMED')&&m.delivery==='COMPLETE'));},'completed answer');
 assert.deepEqual(providerErrors,[]);assert.equal(requests.length,1);result.beforeCold=await invoke(port,'CHAT','read_desktop_workspace',{workspaceId:ws});
 await normalClose();port=await start(true);await ready(port);result.coldPreview=await invoke(port,'CHAT','read_desktop_text_preview',{args:{workspaceId:ws,attachmentId:attachment.id}});assert.equal(result.coldPreview.text,expected);
 result.afterCold=await invoke(port,'CHAT','read_desktop_workspace',{workspaceId:ws});assert.deepEqual(result.afterCold.exchange,result.beforeCold.exchange);assert.equal(requests.length,1);await normalClose();result.pass=true;
}catch(error){result.pass=false;result.error=error.stack||String(error);process.exitCode=1;}
finally{if(launch){try{result.failureClose=await normalClose();}catch(error){result.forcedCleanup=true;result.closeError=String(error);stop();}}provider.closeAllConnections();await new Promise(done=>provider.close(done));result.pids=pids;result.requests=requests;result.providerErrors=providerErrors;if(root)await writeFile(join(root,'docx-expanded-breaks-native-evidence.json'),JSON.stringify(result,null,2),{flag:'wx'});}
console.log(JSON.stringify({root,pass:result.pass,error:result.error,requests:requests.length,pids}));
