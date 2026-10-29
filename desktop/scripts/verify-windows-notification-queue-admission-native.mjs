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
const closeDrainMode = process.argv.includes('--close-drain');
const sendOutcomeMode = process.argv.includes('--send-outcome') || closeDrainMode;
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
const provider=http.createServer(async(request,response)=>{
 try {
  const chunks=[];for await(const chunk of request)chunks.push(chunk);
  const body=JSON.parse(Buffer.concat(chunks).toString('utf8'));requests.push({model:body.model});
  response.writeHead(200,{'content-type':'text/event-stream'});
  response.write(`data: ${JSON.stringify({model:body.model,choices:[{delta:{content:'合成公开材料已核对完成。',annotations:[{url_citation:{title:'合成来源（未抓取）',url:'https://example.com/synthetic-monitor'}}]}}]})}\n\n`);
  response.end(`data: ${JSON.stringify({choices:[{delta:{},finish_reason:'stop'}],usage:{prompt_tokens:20,completion_tokens:8}})}\n\ndata: [DONE]\n\n`);
 }catch{response.writeHead(400);response.end();}
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
let queued=false;
let lockProcess=null;
try{
 const port=await start();parentPid=Number(launch.pid);console.log(JSON.stringify({phase:'started',candidateSha256:receipt.sha256,root,pid:parentPid}));
 await until(()=>evaluate(port,'Boolean(window.__TAURI__?.core?.invoke && globalThis.__nanfengDesktopWorkState?.runtimeInfo?.dataAreasReady)'),'原生就绪');
 await evaluate(port,`(()=>{window.__nfNotifyInvoke=window.__TAURI__.core.invoke.bind(window.__TAURI__.core);const callbacks=window.__TAURI_INTERNALS__.callbacks;const original=callbacks.get;window.__nfHeldReminderEvents=0;callbacks.get=function(id){const entry=original.call(this,id);const callback=typeof entry==='function'?entry:entry?.callback;if(typeof callback!=='function')return entry;const wrapped=value=>{if(value?.event==='desktop-reminder-runtime-v1'){window.__nfHeldReminderEvents++;return;}return callback(value)};return typeof entry==='function'?wrapped:{...entry,callback:wrapped};};return callbacks.get!==original})()`);

 const workspaceId=(await invoke(port,'ensure_desktop_local_workspace')).summary.id;
 const created=await invoke(port,'create_desktop_manual_reminder_draft',{args:{workspaceId,conversationId:null,timezoneId:'UTC'}});
 const draft=created.drafts.find(x=>x.status==='PENDING_REVIEW');assert.ok(draft);
 const nextMinute=(Math.floor(Date.now()/60000)+1)*60000;
 await invoke(port,'confirm_desktop_reminder_draft',{args:{draftId:draft.draftId,title:'合成通知准入',instruction:'只核对合成公开材料',scheduleKind:'ONCE',anchorLocal:new Date(nextMinute).toISOString().slice(0,16),timezoneId:'UTC',missedPolicy:'RUN_ONCE'}});
 console.log(JSON.stringify({phase:'waiting-due',waitMilliseconds:nextMinute-Date.now()}));
 await until(()=>Date.now()>=nextMinute,'合成计划到期',65000);
 await invoke(port,'run_desktop_reminders_due');
 const pending=await evaluate(port,`window.__nfNotifyInvoke('read_pending_desktop_reminder_notifications',{}, {headers:{'x-nanfeng-data-area':'CHAT'}})`);
 assert.equal(pending.length,1);assert.ok(await evaluate(port,'window.__nfHeldReminderEvents>0'),'fixture must suppress only the reminder completion frontend callback'); const runId=pending[0].runId;
 if(sendOutcomeMode){
  let send;
  let duplicatePort=port;
  if(closeDrainMode){
   const lockCode="import sqlite3,sys; c=sqlite3.connect(sys.argv[1]); c.execute('BEGIN EXCLUSIVE'); print('LOCKED',flush=True); sys.stdin.readline(); c.rollback(); c.close()";
   lockProcess=spawn('python',['-u','-c',lockCode,join(root,'workspace.sqlite3')],{stdio:['pipe','pipe','pipe']});
   await new Promise((done,fail)=>{let value='';const timer=setTimeout(()=>fail(new Error('合成通知回执锁未就绪')),10000);lockProcess.stdout.on('data',chunk=>{value+=chunk;if(value.includes('LOCKED')){clearTimeout(timer);done();}});lockProcess.on('error',fail);});
   await evaluate(port,`(()=>{window.__nfSendResult=null;window.__nfNotifyInvoke('send_pending_desktop_reminder_notification',{runId:${JSON.stringify(runId)}},{headers:{'x-nanfeng-data-area':'CHAT'}}).then(value=>window.__nfSendResult={ok:true,value},error=>window.__nfSendResult={ok:false,error:String(error)});return true})()`);
   await new Promise(done=>setTimeout(done,300));
   assert.equal(ps(`$p=Get-Process -Id ${parentPid} -ErrorAction Stop; if ([IO.Path]::GetFullPath([string]$p.Path) -ne ${quote(running)}) { throw 'PID identity mismatch' }; $p.CloseMainWindow()`),'True');
   await new Promise(done=>setTimeout(done,500));
   assert.equal(ps(`@(Get-Process -Id ${parentPid} -ErrorAction SilentlyContinue).Count`),'1','通知回执被阻塞时正常关闭提前退出');
   const lockExit=new Promise((done,fail)=>lockProcess.on('exit',code=>code===0?done():fail(new Error('通知锁释放失败'))));lockProcess.stdin.end('\n');await lockExit;lockProcess=null;
   await until(()=>ps(`@(Get-Process -Id ${parentPid} -ErrorAction SilentlyContinue).Count`)==='0','通知正常关闭排空',20000);launch=null;
   send={ok:true,value:'SENT',observedViaPersistedReceipt:true};
  }else{
   send=await evaluate(port,`window.__nfNotifyInvoke('send_pending_desktop_reminder_notification',{runId:${JSON.stringify(runId)}},{headers:{'x-nanfeng-data-area':'CHAT'}}).then(value=>({ok:true,value}),error=>({ok:false,error:String(error)}))`);
  }
  const audit=()=>JSON.parse(execFileSync('python',[join(desktopRoot,'scripts/read-windows-acceptance-reminder-run.py'),root,'CHAT'],{encoding:'utf8'}));
  const before=audit();assert.equal(before.runs.length,1);const run=before.runs[0];assert.equal(run.runId,runId);assert.equal(run.runState,'SUCCEEDED');
  if(send.ok){assert.equal(send.value,'SENT');assert.equal(run.notificationState,'SENT');assert.equal(run.safeCode,null);}
  else{assert.match(send.error,/提醒通知发送失败|提醒通知发送结果未知/);assert.equal(run.notificationState,'SUPPRESSED');assert.ok(['NOTIFICATION_SEND_FAILED','NOTIFICATION_SEND_UNKNOWN'].includes(run.safeCode));}
  if(closeDrainMode){duplicatePort=await start(true);await until(()=>evaluate(duplicatePort,'Boolean(window.__TAURI__?.core?.invoke && globalThis.__nanfengDesktopWorkState?.runtimeInfo?.dataAreasReady)'),'关闭排空首次冷启');parentPid=Number(launch.pid);}
  const duplicate=await evaluate(duplicatePort,`window.__TAURI__.core.invoke('send_pending_desktop_reminder_notification',{runId:${JSON.stringify(runId)}},{headers:{'x-nanfeng-data-area':'CHAT'}}).then(value=>({ok:true,value}),error=>({ok:false,error:String(error)}))`);
  assert.equal(duplicate.ok,false);assert.match(duplicate.error,/已处理或不存在/);assert.deepEqual(audit(),before);
  stop();const coldPort=await start(true);parentPid=Number(launch.pid);
  await until(()=>evaluate(coldPort,'Boolean(window.__TAURI__?.core?.invoke && globalThis.__nanfengDesktopWorkState?.runtimeInfo?.dataAreasReady)'),'通知回执冷启');
  assert.deepEqual(audit(),before);assert.equal((await invoke(coldPort,'read_pending_desktop_reminder_notifications')).length,0);assert.equal(requests.length,1);
  console.log(JSON.stringify({phase:'send-outcome-cold-reopen',candidateSha256:receipt.sha256,root,send,run,closeDrain:closeDrainMode,duplicateRejected:true,coldReceiptPreserved:true,providerRequests:requests.length,layer:'NATIVE_SYNTHETIC_WINDOWS_SEND_RESULT_NOT_VISIBLE_TOAST_OR_CLICK_ACCEPTANCE'}));
 }else{
 const result=await evaluate(port,`(async()=>{const invoke=window.__nfNotifyInvoke;let admitted=false;for(let n=0;n<50&&!admitted;n++){try{const p=await invoke('preview_desktop_privacy_deletion',{args:{scope:'ALL_LOCAL_BUSINESS_DATA'}});await invoke('delete_desktop_privacy_data',{args:{scope:p.scope,previewFingerprint:p.fingerprint,confirmationPhrase:'删除全部本地业务数据',selectedTaskIds:[]}});admitted=true;}catch(error){if(!/运行中的任务|重新预览/.test(String(error)))throw error;await new Promise(done=>setTimeout(done,100));}}if(!admitted)return {queued:false};return await Promise.race([invoke('send_pending_desktop_reminder_notification',{runId:${JSON.stringify(runId)}}).then(value=>({queued:true,ok:true,value}),error=>({queued:true,ok:false,error:String(error)})),new Promise(done=>setTimeout(()=>done({queued:true,pending:true}),100))]);})()`);
 queued=result.queued;
 console.log(JSON.stringify({candidateSha256:receipt.sha256,root,runId,pendingBeforeQueue:1,afterQueue:result,providerRequests:requests.length,deletionExecuted:queued,layer:'NATIVE_SYNTHETIC_OS_SEND_ATTEMPT_NOT_VISIBLE_TOAST_ACCEPTANCE'}));
 assert.equal(queued,true);assert.match(result.error||'',/全部删除已进入退出队列/,'退出队列后仍进入有效通知发送owner');
 }
}finally{
 if(lockProcess){lockProcess.stdin.end('\n');lockProcess.kill();}
 if(held){held.destroy();held=null;}
 if(queued)await new Promise(done=>setTimeout(done,2500));
 const child=ps(`@(Get-CimInstance Win32_Process -Filter "ParentProcessId=${parentPid}" | Where-Object {[IO.Path]::GetFullPath([string]$_.ExecutablePath) -eq ${quote(running)}} | Select-Object -ExpandProperty ProcessId) | ConvertTo-Json -Compress`);
 stop();if(child){const ids=JSON.parse(child);for(const pid of(Array.isArray(ids)?ids:[ids])){launch={pid};stop();}}
 provider.closeAllConnections();await new Promise(done=>provider.close(done));
}
