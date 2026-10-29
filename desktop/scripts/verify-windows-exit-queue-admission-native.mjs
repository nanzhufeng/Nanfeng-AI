import assert from 'node:assert/strict';
import { createHash } from 'node:crypto';
import { execFileSync, spawn } from 'node:child_process';
import { readFile, writeFile } from 'node:fs/promises';
import http from 'node:http';
import { dirname, join, resolve, sep } from 'node:path';
import { fileURLToPath } from 'node:url';

if (process.platform !== 'win32') throw new Error('仅供 Windows 独立候选验收');
const desktopRoot = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const rootParent = resolve(process.argv.slice(2).find(value => !value.startsWith('--')) || 'D:\\CodexTemp');
const narrowWindow = process.argv.includes('--narrow-window');
const exitDrainWork = process.argv.includes('--exit-drain-work');
const completionWriteDenied = process.argv.includes('--completion-write-denied');
const requestArea = 'CHAT';
const contextWindowTokens = narrowWindow ? 1024 : 4096;
const requestedOutputTokens = narrowWindow ? 256 : 1024;
const inputBudgetTokens = contextWindowTokens - requestedOutputTokens;
const receipt = JSON.parse(await readFile(join(desktopRoot, 'output/windows-acceptance/current.json'), 'utf8'));
assert.equal(receipt.identifier, 'com.nanzhufeng.ai.desktop.windowsacceptance');
const source = resolve(desktopRoot, 'output/windows-acceptance', receipt.sha256, 'NanfengAI-Windows-Acceptance.exe');
const running = resolve(desktopRoot, 'output/windows-acceptance-binaries', receipt.sha256, 'nanfeng-ai-desktop-spike.exe');
assert.equal(resolve(receipt.executable), source);
assert.equal(createHash('sha256').update(await readFile(source)).digest('hex').toUpperCase(), receipt.sha256);
const quote = value => `'${String(value).replaceAll("'", "''")}'`;
const ps = script => execFileSync('pwsh', ['-NoProfile', '-Command', script], { encoding: 'utf8', timeout: 60000 }).trim();
const listen = async server => new Promise(done => server.listen(0, '127.0.0.1', done));
const reservePort = async () => {
  const server = http.createServer();
  await listen(server);
  const port = server.address().port;
  await new Promise(done => server.close(done));
  return port;
};
const requests = [];
let held = null;
const provider = http.createServer(async (request, response) => {
 const chunks=[]; for await(const chunk of request) chunks.push(chunk);
 const body=JSON.parse(Buffer.concat(chunks).toString('utf8'));
 if(!['/','/chat/completions'].includes(request.url)){response.writeHead(400);response.end();return;}


 requests.push({model:body.model}); held=response;
});
await listen(provider);
const providerPort=provider.address().port;
let launch = null;
let root = '';
const stoppedPids = [];
async function start(reopen = false) {
  const debugPort = await reservePort();
  const args = ['-NoProfile', '-File', join(desktopRoot, 'scripts/start-windows-acceptance.ps1'),
    '-RootParent', rootParent, '-StartupMode', 'normal', '-Visible', '-DebugPort', String(debugPort),
    '-OrdinaryChatTestEndpoint', `http://127.0.0.1:${providerPort}`];
  if (reopen) args.push('-Root', root);
  const result = await new Promise((done, fail) => {
    const child = spawn('pwsh', args, { stdio: ['ignore', 'pipe', 'pipe'] });
    let output = '';
    let error = '';
    const timer = setTimeout(() => { child.kill(); fail(new Error(`启动超时：${error}`)); }, 30000);
    child.stdout.on('data', chunk => {
      output += chunk.toString();
      const line = output.split(/\r?\n/).find(value => value.trim().startsWith('{') && value.includes('"pid"'));
      if (!line) return;
      clearTimeout(timer);
      try { done(JSON.parse(line)); } catch (cause) { fail(cause); }
      child.stdout.destroy(); child.stderr.destroy(); child.unref();
    });
    child.stderr.on('data', chunk => { error += chunk.toString(); });
    child.on('error', cause => { clearTimeout(timer); fail(cause); });
    child.on('exit', code => { if (!output.includes('"pid"')) { clearTimeout(timer); fail(new Error(`启动失败 ${code}：${error}`)); } });
  });
  assert.equal(result.sha256, receipt.sha256);
  assert.equal(resolve(result.executable), running);
  launch = result;
  root = resolve(result.root);
  assert.ok(root.startsWith(rootParent + sep));
  assert.equal(JSON.parse(await readFile(join(root, '.nanfeng-windows-acceptance.json'), 'utf8')).identifier, receipt.identifier);
  return debugPort;
}
function stop() {
  if (!launch) return;
  const pid = Number(launch.pid);
  ps(`$p=Get-CimInstance Win32_Process -Filter "ProcessId=${pid}"; if ($p) { if ([IO.Path]::GetFullPath([string]$p.ExecutablePath) -ne ${quote(running)} -or (Get-FileHash -LiteralPath ${quote(running)} -Algorithm SHA256).Hash -ne ${quote(receipt.sha256)}) { throw 'PID identity mismatch' }; Stop-Process -Id ${pid} -Force -ErrorAction Stop }`);
  stoppedPids.push(pid);
  launch = null;
}
async function cdp(port, method, params = {}) {
  const tabs = await fetch(`http://127.0.0.1:${port}/json/list`).then(response => response.json());
  const tab = tabs.find(item => item.type === 'page' && item.url === 'http://tauri.localhost/');
  assert.ok(tab, 'WebView2 页面未就绪');
  const socket = new WebSocket(tab.webSocketDebuggerUrl);
  try {
    await new Promise((done, fail) => { socket.addEventListener('open', done, { once: true }); socket.addEventListener('error', fail, { once: true }); });
    return await new Promise((done, fail) => {
      const timer = setTimeout(() => fail(new Error(`${method} 超时`)), 30000);
      socket.addEventListener('message', event => {
        const reply = JSON.parse(event.data);
        if (reply.id !== 1) return;
        clearTimeout(timer);
        if (reply.error || reply.result?.exceptionDetails) fail(new Error(reply.result?.exceptionDetails?.text || reply.error?.message));
        else done(reply.result?.result?.value);
      });
      socket.send(JSON.stringify({ id: 1, method, params }));
    });
  } finally { socket.close(); }
}
const evaluate = (port, expression) => cdp(port, 'Runtime.evaluate', { expression, awaitPromise: true, returnByValue: true });
async function invoke(port, command, args = {}) {
  const value = await evaluate(port, `window.__TAURI__.core.invoke(${JSON.stringify(command)},${JSON.stringify(args)},${JSON.stringify({ headers: { 'x-nanfeng-data-area': requestArea } })}).catch(error=>({__error:String(error)}))`);
  if (value?.__error) throw new Error(`${command}: ${value.__error}`);
  return value;
}
async function until(read, label, timeout = 30000) {
  const deadline = Date.now() + timeout;
  while (Date.now() < deadline) {
    try { const value = await read(); if (value) return value; } catch { /* WebView reload. */ }
    await new Promise(done => setTimeout(done, 150));
  }
  throw new Error(`${label} 未出现`);
}
let parentPid=0;
let admitted=null;
try {
 const port=await start(); parentPid=Number(launch.pid); console.log(JSON.stringify({phase:'started',candidateSha256:receipt.sha256,root,pid:parentPid}));
 await until(()=>evaluate(port,'Boolean(window.__TAURI__?.core?.invoke && globalThis.__nanfengDesktopWorkState?.runtimeInfo?.dataAreasReady)'),'原生就绪');
 const workspaceId=(await invoke(port,'ensure_desktop_local_workspace')).summary.id;
 await invoke(port,'save_desktop_model_service_settings',{args:{providerId:'DEEPSEEK',presetId:'DEEPSEEK_V4_FLASH',enabled:true,expectedRevision:0,apiKey:'nfai-synthetic-connection-only'}});
 const args={workspaceId,conversationId:null,text:'合成退出队列迟到请求',attachmentIds:[],modelId:'DEEPSEEK_V4_FLASH',webSearchOverride:false};
 const egress=await invoke(port,'preview_desktop_ordinary_chat_egress',{args});
 args.egressAuthorization={approvedAtMs:Date.now(),disclosureVersion:'normal-chat-egress-v2',...egress};
 if(completionWriteDenied){
  await evaluate(port,`globalThis.__ordinaryDenied=window.__TAURI__.core.invoke('submit_desktop_ordinary_chat',{args:${JSON.stringify(args)}},{headers:{'x-nanfeng-data-area':'CHAT'}}).then(value=>({ok:true,value}),error=>({ok:false,error:String(error)}));true`);
  await until(()=>held,'普通聊天请求已抵达回环端');
  const database=join(root,'workspace.sqlite3');
  execFileSync('python',['-c',`import sqlite3,sys\nc=sqlite3.connect(sys.argv[1]);c.execute("CREATE TRIGGER synthetic_ordinary_completion_denied BEFORE UPDATE ON desktop_ordinary_chat_attempts WHEN NEW.state='RESPONSE_COMMITTED' BEGIN SELECT RAISE(ABORT,'synthetic completion denied'); END");c.commit()`,database],{encoding:'utf8',timeout:10000});
  held.writeHead(200,{'Content-Type':'text/event-stream'});
  held.write(`data: ${JSON.stringify({model:requests[0].model,choices:[{delta:{content:'合成完成写入失败回归。'}}]})}\n\n`);
  held.end(`data: ${JSON.stringify({choices:[{delta:{},finish_reason:'stop'}],usage:{prompt_tokens:10,completion_tokens:8}})}\n\ndata: [DONE]\n\n`);held=null;
  const outcome=await evaluate(port,'globalThis.__ordinaryDenied');assert.equal(outcome.ok,false);
  execFileSync('python',['-c','import sqlite3,sys\nc=sqlite3.connect(sys.argv[1]);c.execute("DROP TRIGGER synthetic_ordinary_completion_denied");c.commit()',database],{encoding:'utf8',timeout:10000});
  console.log(JSON.stringify({phase:'completion-write-denied',candidateSha256:receipt.sha256,root,pid:parentPid,callerFailed:true}));
  assert.equal(ps(`$p=Get-Process -Id ${parentPid} -ErrorAction Stop;if([IO.Path]::GetFullPath($p.Path) -ne ${quote(running)}){throw '关闭身份不符'};$p.CloseMainWindow()`),'True');
  await until(()=>ps(`if(Get-Process -Id ${parentPid} -ErrorAction SilentlyContinue){'ALIVE'}else{'EXITED'}`)==='EXITED','普通聊天完成失败后自然退出',5000);
  stoppedPids.push(parentPid);launch=null;
  const coldPort=await start(true);await until(()=>evaluate(coldPort,'Boolean(window.__TAURI__?.core?.invoke)'),'普通聊天失败冷启');
  const cold=await invoke(coldPort,'read_desktop_workspace',{workspaceId});
  const conversation=cold.exchange.conversations.find(c=>c.messages.some(m=>m.role==='user'&&m.blocks?.some(b=>b.text===args.text)));
  assert.ok(conversation,'冷启须定位本次提交的会话');
  const assistant=conversation.messages.find(m=>m.role==='assistant');
  assert.equal(assistant.delivery,'UNKNOWN');assert.equal(requests.length,1);
  console.log(JSON.stringify({candidateSha256:receipt.sha256,root,completionWriteDenied:true,naturalExit:true,coldUnknown:true,requests:1,pids:[...stoppedPids,Number(launch.pid)]}));
 }else{
 const result=await evaluate(port,`(async()=>{const invoke=window.__TAURI__.core.invoke;window.__nfQueued=false;let queued=false;for(let n=0;n<50&&!queued;n++){try{window.__nfLateStage='preview';const p=await invoke('preview_desktop_privacy_deletion',{args:{scope:'ALL_LOCAL_BUSINESS_DATA'}});window.__nfLateStage='queue';await invoke('delete_desktop_privacy_data',{args:{scope:p.scope,previewFingerprint:p.fingerprint,confirmationPhrase:'删除全部本地业务数据',selectedTaskIds:[]}});queued=true;window.__nfQueued=true;}catch(error){if(!/运行中的任务|重新预览/.test(String(error)))throw error;await new Promise(done=>setTimeout(done,100));}}if(!queued)throw 'queue remained busy';window.__nfLateStage='send';const outcomes=[];for(const [command,params] of [['submit_desktop_ordinary_chat',{args:${JSON.stringify(args)}}],['run_desktop_history_knowledge_due',{}],['run_desktop_reminders_due',{}]]){const pending=invoke(command,params).then(value=>({ok:true}),error=>({ok:false,error:String(error)}));outcomes.push({command,...await Promise.race([pending,new Promise(done=>setTimeout(()=>done({pending:true}),80))])});}return {queued:true,outcomes};})().catch(error=>({stage:window.__nfLateStage,queued:window.__nfQueued,error:String(error)}))`);

 admitted=result;
 await new Promise(done=>setTimeout(done,1500));
 console.log(JSON.stringify({candidateSha256:receipt.sha256,root,afterQueue:result,requestCount:requests.length,deletionExecuted:result?.stage!=='queue'&&result?.stage!=='preview',layer:'SYNTHETIC_OWNED_ROOT_ONLY'}));
 assert.equal(result?.queued,true,result?.error); for(const outcome of result.outcomes) assert.match(outcome.error||'',/全部删除已进入退出队列/,`${outcome.command} 未在prepare前拒绝`);
 assert.equal(requests.length,0);
 }
} finally {
 if(held){held.destroy();held=null;}
 await new Promise(done=>setTimeout(done,1000));
 const child=ps(`@(Get-CimInstance Win32_Process -Filter "ParentProcessId=${parentPid}" | Where-Object {[IO.Path]::GetFullPath([string]$_.ExecutablePath) -eq ${quote(running)}} | Select-Object -ExpandProperty ProcessId) | ConvertTo-Json -Compress`);
 stop();
 if(child){const ids=JSON.parse(child); for(const pid of(Array.isArray(ids)?ids:[ids])){launch={pid};stop();}}
 provider.closeAllConnections();await new Promise(done=>provider.close(done));
}
