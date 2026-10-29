import { verifyVisibleUsageOutcomes } from './verify-visible-usage-outcomes.mjs';
import assert from 'node:assert/strict';
import { createHash } from 'node:crypto';
import { execFileSync, spawn } from 'node:child_process';
import { readFile, writeFile } from 'node:fs/promises';
import { dirname, join, resolve, sep } from 'node:path';
import { fileURLToPath } from 'node:url';
import http from 'node:http';

if (process.platform !== 'win32') throw new Error('仅限 Windows 合成验收。');
const desktopRoot = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const rootParent = resolve(process.argv[2] || 'D:\\CodexTemp');
const receipt = JSON.parse(await readFile(join(desktopRoot, 'output/windows-acceptance/current.json'), 'utf8'));
assert.equal(receipt.identifier, 'com.nanzhufeng.ai.desktop.windowsacceptance');
const source = resolve(desktopRoot, 'output/windows-acceptance', receipt.sha256, 'NanfengAI-Windows-Acceptance.exe');
assert.equal(resolve(receipt.executable), source);
assert.equal(createHash('sha256').update(await readFile(source)).digest('hex').toUpperCase(), receipt.sha256);
const installedArg = process.argv.find(arg=>arg.startsWith('--installed-exe='));
const installedHashArg = process.argv.find(arg=>arg.startsWith('--installed-sha256='));
assert.equal(Boolean(installedArg),Boolean(installedHashArg));
const installedExecutable=installedArg?resolve(installedArg.slice('--installed-exe='.length)):null;
const candidateHash=installedHashArg?installedHashArg.slice('--installed-sha256='.length).toUpperCase():receipt.sha256;
assert.match(candidateHash,/^[A-F0-9]{64}$/);
if(installedExecutable){
 assert.ok(dirname(installedExecutable).startsWith(rootParent+'\\nfai-installed-acceptance.'));
 assert.equal(createHash('sha256').update(await readFile(installedExecutable)).digest('hex').toUpperCase(),candidateHash);
}
const runningExecutable=installedExecutable || resolve(desktopRoot,'output/windows-acceptance-binaries',receipt.sha256,'nanfeng-ai-desktop-spike.exe');
const notificationsMode=process.argv.includes('--notifications');

const lifecycleMode = process.argv.includes('--lifecycle');
assert.ok(lifecycleMode && !process.argv.some(a => /^--(installed|notifications|disposition|draft-accounting|close-drain|run|bad-proposal|cost-status)/.test(a)), '仅 CHAT 当前可见生命周期模式');
const closeDrainMode = process.argv.includes('--close-drain');
assert.ok(!closeDrainMode || (lifecycleMode && !notificationsMode && !installedExecutable), '关闭排空仅限独立合成生命周期模式');
let releaseReminder = null;
let holdTimedOut = false;
assert.ok(!notificationsMode || (lifecycleMode && installedExecutable), '通知模式仅限安装态生命周期验收');
const dispositionMode = process.argv.includes('--disposition');
const draftAccountingMode = process.argv.includes('--draft-accounting');
const draftAccountingVisibleMode = process.argv.includes('--draft-accounting-visible');
assert.ok(!draftAccountingVisibleMode || (draftAccountingMode && !process.argv.includes('--success') && !process.argv.includes('--visible')));
const expectOldRetryGap = process.argv.includes('--expect-old-retry-gap');
assert.ok(!expectOldRetryGap || draftAccountingMode);
const visibleMode = process.argv.includes('--visible') || lifecycleMode;
const successMode = process.argv.includes('--success') || visibleMode;
const runMode = process.argv.includes('--run');
const badProposalMode = process.argv.includes('--bad-proposal');
assert.ok(!(runMode&&successMode),'成功模式只用于草稿相邻回归');
const requests=[];
const provider=http.createServer(async(request,response)=>{
 const chunks=[];for await(const chunk of request)chunks.push(chunk);
 const body=JSON.parse(Buffer.concat(chunks).toString('utf8'));
 const reminder=body.model.startsWith('fixture-reminder');
 const serialized=JSON.stringify(body);
 requests.push({model:body.model,reminder,editedInstructionPresent:serialized.includes('仅跟踪编辑后的合成材料'),sourceConversationPresent:serialized.includes('明天提醒我核对这个合成任务的公开资料，只用于本机隔离验收。')});
 response.writeHead(200,{'Content-Type':'text/event-stream'});
 const reminderOrdinal=requests.filter(x=>x.reminder).length;
 if(closeDrainMode&&reminder&&reminderOrdinal>1)await new Promise(done=>{
  const timer=setTimeout(()=>{holdTimedOut=true;releaseReminder=null;done()},60000);
  releaseReminder=()=>{clearTimeout(timer);releaseReminder=null;done()};
 });
 const proposalSuccess=successMode||(dispositionMode&&reminderOrdinal>1);
 const text=reminder?(lifecycleMode&&reminderOrdinal>1?'合成监控结果：编辑后的公开材料已核对完成。':proposalSuccess?JSON.stringify({eligible:true,title:'合成正常草稿',instruction:'仅核对合成公开材料',scheduleKind:'ONCE',anchorLocal:new Date(Date.now()+86400000).toISOString().slice(0,16),missedPolicy:'RUN_ONCE'}):'invalid synthetic proposal'):'已确认这是一条仅用于合成验收的未来提醒要求。';
 const annotations=lifecycleMode&&reminder&&requests.filter(x=>x.reminder).length>1?[{url_citation:{title:'合成公开来源（未抓取）',url:'https://example.com/synthetic-monitor'}}]:[];
 response.write(`data: ${JSON.stringify({model:body.model,choices:[{delta:{content:text,annotations}}]})}\n\n`);
 response.end(`data: ${JSON.stringify({choices:[{delta:{},finish_reason:reminder&&!badProposalMode&&!proposalSuccess?'length':'stop'}],usage:{prompt_tokens:20,completion_tokens:8,cost:0.000007}})}\n\ndata: [DONE]\n\n`);
});
await new Promise(done=>provider.listen(0,'127.0.0.1',done));
const endpoint=`http://127.0.0.1:${provider.address().port}`;
const ps = script => execFileSync('pwsh', ['-NoProfile', '-Command', script], { encoding: 'utf8', timeout: 60000 }).trim();
const quote = value => `'${String(value).replaceAll("'", "''")}'`;
async function reservePort() {
  const server = http.createServer();
  await new Promise(done => server.listen(0, '127.0.0.1', done));
  const port = server.address().port;
  await new Promise(done => server.close(done));
  return port;
}
let launch = null;
let root = '';
assert.ok(process.execArgv.includes('--max-old-space-size=2048'));
const pids = [];const normalCloses=[];const forcedPids=[];
function startProcess(args) {
  return new Promise((done, fail) => {
    const child = spawn('pwsh', ['-NoProfile', '-File', join(desktopRoot, 'scripts/start-windows-acceptance.ps1'), ...args],
      { stdio: ['ignore', 'pipe', 'pipe'] });
    let out = '';
    let err = '';
    const timer = setTimeout(() => { child.kill(); fail(new Error(`启动超时：${err}`)); }, 30000);
    child.stdout.on('data', chunk => {
      out += chunk.toString();
      const line = out.split(/\r?\n/).find(item => item.trim().startsWith('{') && item.includes('"pid"'));
      if (!line) return;
      clearTimeout(timer);
      try { done(JSON.parse(line)); } catch (error) { fail(error); }
      child.stdout.destroy(); child.stderr.destroy(); child.unref();
    });
    child.stderr.on('data', chunk => { err += chunk.toString(); });
    child.on('error', error => { clearTimeout(timer); fail(error); });
    child.on('exit', code => { if (!out.includes('"pid"')) { clearTimeout(timer); fail(new Error(`启动失败 ${code}：${err}`)); } });
  });
}
async function start(reopen = false) {
  const port = await reservePort();
  const args = ['-RootParent', rootParent, '-StartupMode', 'normal', '-DebugPort', String(port), '-OrdinaryChatTestEndpoint', endpoint];
  args.push('-Visible');
  if(installedExecutable)args.push('-ExecutablePath',installedExecutable,'-RunInPlace');else args.push('-ExecutablePath',source);
  if (reopen) args.push('-Root', root);
  launch = await startProcess(args);
  assert.equal(launch.sha256, candidateHash);
  assert.equal(resolve(launch.executable), runningExecutable);
  root = resolve(launch.root);
  assert.ok(root.startsWith(rootParent + sep));
  const marker = JSON.parse(await readFile(join(root, '.nanfeng-windows-acceptance.json'), 'utf8'));
  assert.equal(marker.identifier, receipt.identifier);
  return port;
}
function stop() {
  if (!launch) return;
  const pid = Number(launch.pid);
  ps(`$p=Get-CimInstance Win32_Process -Filter "ProcessId=${pid}"; if ($p) { if ([IO.Path]::GetFullPath([string]$p.ExecutablePath) -ne ${quote(runningExecutable)} -or (Get-FileHash -LiteralPath ${quote(runningExecutable)} -Algorithm SHA256).Hash -ne ${quote(candidateHash)}) { throw 'PID identity mismatch' }; Stop-Process -Id ${pid} -Force -ErrorAction Stop }`);forcedPids.push(pid);
  pids.push(pid);
  launch = null;
}
async function closeNormally(){
 const pid=Number(launch.pid);const profile=launch.webviewProfile;
 const info=JSON.parse(ps(`$p=Get-CimInstance Win32_Process -Filter "ProcessId=${pid}";if(!$p -or [IO.Path]::GetFullPath([string]$p.ExecutablePath) -ne ${quote(runningExecutable)} -or (Get-FileHash -LiteralPath ${quote(runningExecutable)}).Hash -ne ${quote(candidateHash)}){throw 'identity mismatch'};$n=Get-Process -Id ${pid};@{title=$n.MainWindowTitle;responding=$n.Responding;accepted=$n.CloseMainWindow()}|ConvertTo-Json -Compress`));
 assert.ok(info.title&&info.responding&&info.accepted);
 await until(()=>ps(`@(Get-CimInstance Win32_Process -Filter "ProcessId=${pid}").Count`)==='0','自然退出');
 await until(()=>ps(`@(Get-CimInstance Win32_Process -Filter "Name='msedgewebview2.exe'"|Where-Object {$_.CommandLine -and $_.CommandLine.Contains(${quote(profile)})}).Count`)==='0','本根WebView退出');
 normalCloses.push(info);pids.push(pid);launch=null;
}
async function evaluate(port, expression) {
  const tabs = await fetch(`http://127.0.0.1:${port}/json/list`).then(response => response.json());
  const tab = tabs.find(item => item.type === 'page' && item.url === 'http://tauri.localhost/');
  assert.ok(tab, 'WebView2 页面未就绪');
  const socket = new WebSocket(tab.webSocketDebuggerUrl);
  try {
    await new Promise((done, fail) => {
      socket.addEventListener('open', done, { once: true });
      socket.addEventListener('error', fail, { once: true });
    });
    return await new Promise((done, fail) => {
      const timer = setTimeout(() => fail(new Error('CDP evaluation timed out')), 20000);
      socket.addEventListener('message', event => {
        const message = JSON.parse(event.data);
        if (message.id !== 1) return;
        clearTimeout(timer);
        if (message.error || message.result?.exceptionDetails) fail(new Error(message.result?.exceptionDetails?.text || message.error?.message));
        else done(message.result?.result?.value);
      });
      socket.send(JSON.stringify({ id: 1, method: 'Runtime.evaluate', params: { expression, awaitPromise: true, returnByValue: true } }));
    });
  } finally { socket.close(); }
}
async function invoke(port, command, args = {}, area = 'CHAT') {
  const value = await evaluate(port, `window.__TAURI__.core.invoke(${JSON.stringify(command)},${JSON.stringify(args)},${JSON.stringify({ headers: { 'x-nanfeng-data-area': area } })}).catch(error=>({__error:String(error)}))`);
  if (value?.__error) throw new Error(`${command}: ${value.__error}`);
  return value;
}
async function until(read, label) {
  const deadline = Date.now() + 30000;
  while (Date.now() < deadline) {
    try { const value = await read(); if (value) return value; } catch { /* Startup or redraw in progress. */ }
    await new Promise(done => setTimeout(done, 200));
  }
  throw new Error(`${label} 未在原生界面出现`);
}
const click = (port, selector) => evaluate(port, `(()=>{const node=document.querySelector(${JSON.stringify(selector)});if(!node)return false;node.click();return true})()`);
async function verifyDraftAccounting(port,workspaceId) {
 const call=(command,args={})=>invoke(port,command,args,'CHAT');
 const count=()=>requests.filter(x=>x.reminder).length;
 const measured=async()=> (await call('read_desktop_usage_ledger')).records.filter(x=>x.modelId.startsWith('fixture-reminder'));
 const fault=action=>execFileSync('python',[join(desktopRoot,'scripts/set-windows-reminder-usage-fault.py'),root,'CHAT',action],{encoding:'utf8'});
 const closeNormal=async()=>{
  const pid=Number(launch.pid);
  assert.equal(ps(`$p=Get-Process -Id ${pid};if([IO.Path]::GetFullPath($p.Path) -ne ${quote(runningExecutable)} -or (Get-FileHash $p.Path).Hash -ne ${quote(candidateHash)}){throw 'identity mismatch'};$p.CloseMainWindow()|ConvertTo-Json`),'true');
  await until(()=>ps(`($null -eq (Get-Process -Id ${pid} -ErrorAction SilentlyContinue))|ConvertTo-Json`)==='true','normal Close');
  assert.doesNotMatch(await readFile(join(root,'startup.stderr.log'),'utf8'),/panicked|null pointer/i);
  pids.push(pid);launch=null;
 };
 const attempt=await call('submit_desktop_ordinary_chat',{args:{workspaceId,conversationId:null,expectedRevision:null,text:'明天提醒我核对这个合成任务的公开资料，只用于本机隔离验收。',attachmentIds:[],webSearchOverride:false,egressAuthorization:{approvedAtMs:Date.now(),disclosureVersion:'normal-chat-egress-v1'}}});
 assert.equal(attempt.state,'COMPLETED');
 await call('read_desktop_usage_ledger');fault('deny');
 const conversation=(await call('read_desktop_workspace',{workspaceId})).exchange.conversations.find(x=>x.id===attempt.conversationId);
 const args={workspaceId,conversationId:conversation.id,userMessageId:conversation.messages.find(x=>x.role==='user').id,assistantMessageId:conversation.messages.find(x=>x.role==='assistant').id,timezoneId:'UTC'};
 const projection=await call('generate_desktop_reminder_draft',{args});
 const draft=projection.drafts.find(x=>x.conversationId===conversation.id);
 assert.equal(draft.status,successMode?'PENDING_REVIEW':'FAILED');assert.equal(draft.inputTokens,20);assert.equal(draft.outputTokens,8);assert.equal(draft.chargeMicros,7);
 assert.equal((await measured()).length,0);assert.equal(count(),1);
 const evidence={candidateSha256:candidateHash,root,draftId:draft.draftId,expectOldRetryGap,successMode,deniedUsageCount:0,full13Complete:false};
 if(expectOldRetryGap){
  await call('retry_desktop_reminder_draft',{draftId:draft.draftId});
  assert.equal(count(),2);assert.equal((await measured()).length,0);
  evidence.oldRetryOverwroteUnaccountedAttempt=true;
  await closeNormal();
 }else{
  await closeNormal();port=await start(true);
  await until(()=>evaluate(port,'globalThis.__nanfengDesktopWorkState?.runtimeInfo?.dataAreasReady===true'),'denied cold data areas');
  assert.deepEqual((await call('read_desktop_reminders')).drafts.find(x=>x.draftId===draft.draftId),draft);
  assert.equal((await measured()).length,0);assert.equal(count(),1);
  if(!successMode){
   if(draftAccountingVisibleMode){
    assert.equal(await click(port,'[data-action="show-chat"]'),true);
    await until(()=>evaluate(port,'globalThis.__nanfengDesktopWorkState?.dataArea === "CHAT"'),'重试工作区');
    await until(()=>evaluate(port,'Boolean(document.querySelector("[data-action=show-reminders]"))'),'提醒列表入口');
    assert.equal(await click(port,'[data-action="show-reminders"]'),true);
    const selector=`[data-action="retry-reminder-draft"][data-id="${draft.draftId}"]`;
    await until(()=>evaluate(port,`(()=>{const n=document.querySelector(${JSON.stringify(selector)});return Boolean(n&&!n.disabled&&n.getBoundingClientRect().width>0&&n.getBoundingClientRect().height>0)})()`),'可见失败重试按钮');
    assert.equal(await click(port,selector),true);
    await until(()=>evaluate(port,'String(globalThis.__nanfengDesktopWorkState?.error).includes("旧计量尚未保存") && document.body.innerText.includes("旧计量尚未保存")'),'可见拒写反馈');
    evidence.visibleRetryDenied=true;
    evidence.visibleError=await evaluate(port,'globalThis.__nanfengDesktopWorkState.error');
    assert.equal(await evaluate(port,'Boolean(document.getElementById("reminder-title"))'),false);
   }else await assert.rejects(()=>call('retry_desktop_reminder_draft',{draftId:draft.draftId}),/旧计量尚未保存/);
   assert.deepEqual((await call('read_desktop_reminders')).drafts.find(x=>x.draftId===draft.draftId),draft);
   assert.equal(count(),1);evidence.deniedRetryPreservedDraft=true;
  }
  fault('allow');
  if(!successMode){await call('retry_desktop_reminder_draft',{draftId:draft.draftId});assert.equal(count(),2);assert.equal((await measured()).length,2);}
  await closeNormal();port=await start(true);
  await until(()=>evaluate(port,'globalThis.__nanfengDesktopWorkState?.runtimeInfo?.dataAreasReady===true'),'recovered cold data areas');
  assert.equal((await measured()).length,successMode?1:2);assert.equal(count(),successMode?1:2);
  await closeNormal();port=await start(true);
  await until(()=>evaluate(port,'globalThis.__nanfengDesktopWorkState?.runtimeInfo?.dataAreasReady===true'),'second cold data areas');
  assert.equal((await measured()).length,successMode?1:2);assert.equal(count(),successMode?1:2);
  if(successMode)assert.deepEqual((await call('read_desktop_reminders')).drafts.find(x=>x.draftId===draft.draftId),draft);
  assert.equal((await invoke(port,'read_desktop_reminders',{},'CHAT')).drafts.length,0);
  await closeNormal();evidence.coldReadbackWithoutResend=true;evidence.finalMeasuredCount=successMode?1:2;
 }
 evidence.reminderRequests=count();evidence.pids=pids;
 await writeFile(join(root,'reminder-draft-accounting.json'),JSON.stringify(evidence,null,2),{flag:'wx'});
 return evidence;
}
async function verifyDisposition(port,workspaceId){
 const call=(command,args={})=>invoke(port,command,args,'CHAT');
 const attempt=await call('submit_desktop_ordinary_chat',{args:{workspaceId,conversationId:null,expectedRevision:null,text:'明天提醒我核对这个合成任务的公开资料，只用于本机隔离验收。',attachmentIds:[],webSearchOverride:false,egressAuthorization:{approvedAtMs:Date.now(),disclosureVersion:'normal-chat-egress-v1'}}});assert.equal(attempt.state,'COMPLETED');
 const settings=await call('read_desktop_app_settings');await call('save_desktop_app_settings',{args:{appearance:settings.appearance,product:{...settings.product,reminderSuggestions:true,...(notificationsMode?{monitorNotifications:true}:{})},expectedRevision:settings.revision}});
 await evaluate(port,'globalThis.__reminderReloadOld=true;location.reload();true');
 await until(()=>evaluate(port,'globalThis.__reminderReloadOld!==true && globalThis.__nanfengDesktopWorkState?.runtimeInfo?.dataAreasReady===true'),'新页面双区');
 assert.equal(await click(port,'[data-action="show-chat"]'),true);
 const selector=`[data-action="select-chat"][data-id="${attempt.conversationId}"]`;await until(()=>evaluate(port,`Boolean(document.querySelector(${JSON.stringify(selector)}))`),'来源对话');assert.equal(await click(port,selector),true);
 await until(()=>evaluate(port,'Boolean(document.querySelector("[data-action=generate-reminder-draft]"))'),'提醒入口');assert.equal(await click(port,'[data-action="generate-reminder-draft"]'),true);
 const failed=await until(async()=>(await call('read_desktop_reminders')).drafts.find(x=>x.status==='FAILED'),'失败草稿');assert.equal(failed.safeErrorCode,'OUTPUT_LIMIT');assert.equal((await call('read_desktop_reminders')).plans.length,0);
 await until(()=>evaluate(port,`Boolean(document.querySelector('[data-action="retry-reminder-draft"][data-id="${failed.draftId}"]'))`),'失败重试入口');
 assert.equal(await click(port,`[data-action="retry-reminder-draft"][data-id="${failed.draftId}"]`),true);
 await until(()=>evaluate(port,'Boolean(document.getElementById("reminder-title"))'),'重试审阅');
 const retried=(await call('read_desktop_reminders')).drafts.find(x=>x.draftId===failed.draftId);assert.equal(retried.status,'PENDING_REVIEW');assert.equal(retried.retryCount,1);assert.equal((await call('read_desktop_reminders')).plans.length,0);
 assert.equal(await click(port,'[data-action="cancel-reminder-editor"]'),true);
 await until(()=>evaluate(port,`Boolean(document.querySelector('[data-action="reject-reminder-draft"][data-id="${failed.draftId}"]'))`),'拒绝入口');assert.equal(await click(port,`[data-action="reject-reminder-draft"][data-id="${failed.draftId}"]`),true);
 await until(async()=>(await call('read_desktop_reminders')).drafts.find(x=>x.draftId===failed.draftId)?.status==='REJECTED','明确拒绝保存');
 assert.equal(await click(port,'[data-action="create-manual-reminder-draft"]'),true);await until(()=>evaluate(port,'Boolean(document.getElementById("reminder-title"))'),'手动计划审阅');
 await evaluate(port,`(()=>{for(const [id,value] of [['reminder-title','合成删除计划'],['reminder-instruction','仅用于合成删除验收'],['reminder-anchor-local','2027-10-01T12:30'],['reminder-timezone','UTC'],['reminder-missed-policy','SKIP']]){const n=document.getElementById(id);n.value=value;n.dispatchEvent(new Event('input',{bubbles:true}));}return true})()`);
 assert.equal(await click(port,'[data-action="confirm-reminder-draft"]'),true);const plan=await until(async()=>(await call('read_desktop_reminders')).plans.find(x=>x.title==='合成删除计划'),'手动确认保存');
 const deleteSelector=`[data-action="delete-reminder-plan"][data-id="${plan.planId}"]`;await until(()=>evaluate(port,`Boolean(document.querySelector(${JSON.stringify(deleteSelector)}))`),'计划删除入口');assert.equal(await click(port,deleteSelector),true);
 await until(()=>evaluate(port,'Boolean(document.querySelector("[data-action=confirm-delete-reminder-plan]"))'),'删除确认');assert.equal(await click(port,'.dialog [data-action="close-dialog"]'),true);assert.ok((await call('read_desktop_reminders')).plans.some(x=>x.planId===plan.planId),'取消删除保留计划');
 assert.equal(await click(port,deleteSelector),true);await until(()=>evaluate(port,'Boolean(document.querySelector("[data-action=confirm-delete-reminder-plan]"))'),'再次删除确认');assert.equal(await click(port,'[data-action="confirm-delete-reminder-plan"]'),true);
 await until(async()=>(await call('read_desktop_reminders')).plans.length===0,'确认删除保存');
 const records=(await call('read_desktop_usage_ledger')).records.filter(x=>x.modelId.startsWith('fixture-reminder'));assert.equal(records.length,2);for(const record of records){assert.equal(record.inputTokens,20);assert.equal(record.outputTokens,8);assert.equal(record.chargeMicros,7);}
 stop();port=await start(true);await until(()=>evaluate(port,'globalThis.__nanfengDesktopWorkState?.runtimeInfo?.dataAreasReady===true'),'冷启双区');
 assert.equal(await click(port,'[data-action="show-chat"]'),true);await until(()=>evaluate(port,'globalThis.__nanfengDesktopWorkState?.reminders?.drafts.some(x=>x.status==="REJECTED")'),'冷启工作投影');
 const cold=await call('read_desktop_reminders');assert.equal(cold.plans.length,0);assert.equal(cold.drafts.find(x=>x.draftId===failed.draftId).status,'REJECTED');assert.equal((await call('read_desktop_usage_ledger')).records.filter(x=>x.modelId.startsWith('fixture-reminder')).length,2);assert.equal(requests.filter(x=>x.reminder).length,2);
 const outcomes=(await call('read_desktop_usage_ledger')).records.filter(x=>x.modelId.startsWith('fixture-reminder'));
 assert.equal(outcomes.filter(x=>x.requestState===null).length,1);assert.equal(outcomes.filter(x=>x.requestState==='COMPLETED').length,1);
 if(process.argv.includes('--cost-status')) await verifyVisibleUsageOutcomes({port,evaluate,click,until,modelId:'fixture-reminder',labels:['状态未记录','已完成']});
 return {candidateSha256:receipt.sha256,root,visibleCostOutcomes:process.argv.includes('--cost-status'),oldAttemptNotReattributed:true,dispositionMode:true,visibleRetryRejectDelete:true,coldReadback:true,reminderRequests:2,pids};
}
let result;
let finalAnchor = '2027-10-01T12:30';
const finalTitle = lifecycleMode ? '合成已编辑计划' : '合成审阅计划';
const finalInstruction = lifecycleMode ? '仅跟踪编辑后的合成材料' : '只检查用户审阅的合成公开材料';
try{
 let port=await start();await until(()=>evaluate(port,'Boolean(window.__TAURI__?.core?.invoke)'),'IPC');
 const invokeWork=(command,args={})=>invoke(port,command,args,'CHAT');
 const workspaceId=(await invokeWork('ensure_desktop_local_workspace')).summary.id;
  const otherId=(await invoke(port,'ensure_desktop_local_workspace',{},'WORK')).summary.id;
  const otherBefore=await invoke(port,'read_desktop_workspace',{workspaceId:otherId},'WORK');
 if(draftAccountingMode){result=await verifyDraftAccounting(port,workspaceId);}else if(dispositionMode){result=await verifyDisposition(port,workspaceId);}else{
 let entity,projection;
 if(!runMode){
  const attempt=await invokeWork('submit_desktop_ordinary_chat',{args:{workspaceId,conversationId:null,expectedRevision:null,text:'明天提醒我核对这个合成任务的公开资料，只用于本机隔离验收。',attachmentIds:[],webSearchOverride:false,egressAuthorization:{approvedAtMs:Date.now(),disclosureVersion:'normal-chat-egress-v1'}}});
  assert.equal(attempt.state,'COMPLETED');
  const conversation=(await invokeWork('read_desktop_workspace',{workspaceId})).exchange.conversations.find(x=>x.id===attempt.conversationId);
  const user=conversation.messages.find(x=>x.role==='user');const assistant=conversation.messages.find(x=>x.role==='assistant');
  if(visibleMode){
   const settings=await invokeWork('read_desktop_app_settings');
   await invokeWork('save_desktop_app_settings',{args:{appearance:settings.appearance,product:{...settings.product,reminderSuggestions:true,...(notificationsMode?{monitorNotifications:true}:{})},expectedRevision:settings.revision}});
   if(notificationsMode)assert.equal((await invokeWork('read_desktop_app_settings')).product.monitorNotifications,true,'synthetic notification setting must be persisted');
   await evaluate(port,'globalThis.__reminderReloadOld=true;location.reload();true');
   await until(()=>evaluate(port,'globalThis.__reminderReloadOld!==true && globalThis.__nanfengDesktopWorkState?.runtimeInfo?.dataAreasReady===true'),'新页面双区');
   assert.equal(await click(port,'[data-action="show-chat"]'),true);
   const selector=`[data-action="select-chat"][data-id="${conversation.id}"]`;
   await until(()=>evaluate(port,`Boolean(document.querySelector(${JSON.stringify(selector)}))`),'来源对话');
   assert.equal(await click(port,selector),true);
   console.log(JSON.stringify({stage:'visible-source',root,pid:launch.pid,ui:await evaluate(port,'(()=>{const s=globalThis.__nanfengDesktopWorkState;return {pane:s.pane,selected:s.selectedConversationId,settings:s.productSettings?.reminderSuggestions,capability:s.settingsCapabilities?.reminderSuggestions,messages:document.querySelectorAll(".chat-message").length,error:s.error}})()')}));
   await until(()=>evaluate(port,'Boolean(document.querySelector("[data-action=generate-reminder-draft]"))'),'聊天提醒入口');
   assert.equal(await click(port,'[data-action="generate-reminder-draft"]'),true);
   await until(()=>evaluate(port,'Boolean(globalThis.__nanfengDesktopWorkState?.reminderEditor)'),'草稿回执');
   assert.equal(await evaluate(port,'Boolean(document.getElementById("reminder-title"))'),true,'聊天生成后必须显示审阅编辑器');
   await evaluate(port,`(()=>{for(const [id,value] of [['reminder-title','合成审阅计划'],['reminder-instruction','只检查用户审阅的合成公开材料'],['reminder-anchor-local','2027-10-01T12:30'],['reminder-timezone','UTC'],['reminder-missed-policy','SKIP']]){const n=document.getElementById(id);n.value=value;n.dispatchEvent(new Event('input',{bubbles:true}));}globalThis.__reminderBefore=document.getElementById('reminder-instruction');globalThis.__nanfengDesktopWorkState.contextMenu={syntheticAcceptance:true};window.dispatchEvent(new Event('resize'));return true})()`);
   await until(()=>evaluate(port,'document.getElementById("reminder-instruction")!==globalThis.__reminderBefore'),'编辑器实际重绘');
   assert.deepEqual(await evaluate(port,'["reminder-title","reminder-instruction","reminder-anchor-local","reminder-timezone","reminder-missed-policy"].map(id=>document.getElementById(id).value)'),['合成审阅计划','只检查用户审阅的合成公开材料','2027-10-01T12:30','UTC','SKIP']);
   assert.equal(await click(port,'[data-action="confirm-reminder-draft"]'),true);
   await until(()=>evaluate(port,'Boolean(document.querySelector("[data-reminder-plan-id]"))'),'确认后计划列表');
   projection=await invokeWork('read_desktop_reminders');
  }else projection=await invokeWork('generate_desktop_reminder_draft',{args:{workspaceId,conversationId:conversation.id,userMessageId:user.id,assistantMessageId:assistant.id,timezoneId:'UTC'}});
  entity=projection.drafts.find(x=>x.conversationId===conversation.id);
  assert.equal(entity.status,visibleMode?'CONFIRMED':successMode?'PENDING_REVIEW':'FAILED');assert.equal(entity.safeErrorCode,successMode?null:badProposalMode?'REMINDER_DRAFT_FORMAT':'OUTPUT_LIMIT');assert.equal(entity.inputTokens,20);assert.equal(entity.outputTokens,8);assert.equal(entity.chargeMicros,7);assert.equal(projection.plans.length,visibleMode?1:0);
  if(lifecycleMode){
   const planId=projection.plans[0].planId;
   assert.equal(await click(port,`[data-action="edit-reminder-plan"][data-id="${planId}"]`),true);
   await until(()=>evaluate(port,'Boolean(document.querySelector("[data-action=confirm-edit-reminder-plan]"))'),'计划编辑器');
   const nextMinute=(Math.floor(Date.now()/60000)+1+(60000-Date.now()%60000<12000?1:0))*60000;
   finalAnchor=new Date(nextMinute).toISOString().slice(0,16);
   await evaluate(port,`(()=>{for(const [id,value] of ${JSON.stringify([['reminder-title',finalTitle],['reminder-instruction',finalInstruction],['reminder-anchor-local',finalAnchor],['reminder-timezone','UTC'],['reminder-missed-policy','RUN_ONCE']])}){const n=document.getElementById(id);n.value=value;n.dispatchEvent(new Event('input',{bubbles:true}));}return true})()`);
   assert.equal(await click(port,'[data-action="confirm-edit-reminder-plan"]'),true);
   await until(async()=>{const p=(await invokeWork('read_desktop_reminders')).plans.find(x=>x.planId===planId);return p?.title===finalTitle&&p.nextRunAtMs===nextMinute},'计划编辑保存');
   await until(()=>evaluate(port,`Boolean(document.querySelector('[data-action="set-reminder-paused"][data-id="${planId}"][data-paused="true"]'))`),'暂停按钮');
   assert.equal(await click(port,`[data-action="set-reminder-paused"][data-id="${planId}"][data-paused="true"]`),true);
   await until(async()=>{const p=(await invokeWork('read_desktop_reminders')).plans.find(x=>x.planId===planId);return p?.status==='PAUSED'&&p.nextRunAtMs===nextMinute},'暂停持久状态');
   assert.equal(requests.filter(x=>x.reminder).length,1,'暂停前后未执行计划');
   await until(()=>evaluate(port,`Boolean(document.querySelector('[data-action="set-reminder-paused"][data-id="${planId}"][data-paused="false"]'))`),'恢复按钮');
   assert.equal(await click(port,`[data-action="set-reminder-paused"][data-id="${planId}"][data-paused="false"]`),true);
   await until(async()=>{const p=(await invokeWork('read_desktop_reminders')).plans.find(x=>x.planId===planId);return p?.status==='ACTIVE'&&p.nextRunAtMs===nextMinute},'恢复重新计算');
   console.log(JSON.stringify({stage:'visible plan edited paused resumed; waiting for native timer',root,pid:launch.pid,waitMilliseconds:nextMinute-Date.now()}));while(Date.now()<nextMinute){await new Promise(done=>setTimeout(done,250));}
   if(closeDrainMode){
    await until(()=>releaseReminder,'提醒生产请求已抵达回环端');
    const pid=Number(launch.pid);
    assert.equal(ps(`$p=Get-Process -Id ${pid} -ErrorAction Stop;if([IO.Path]::GetFullPath($p.Path) -ne ${quote(runningExecutable)} -or (Get-FileHash -LiteralPath ${quote(runningExecutable)} -Algorithm SHA256).Hash -ne ${quote(candidateHash)}){throw '关闭身份不符'};$p.CloseMainWindow()`),'True');
    await new Promise(done=>setTimeout(done,1000));
    assert.equal(ps(`if(Get-Process -Id ${pid} -ErrorAction SilentlyContinue){'ALIVE'}else{'EXITED'}`),'ALIVE');
    assert.equal(holdTimedOut,false);releaseReminder();
    await until(()=>ps(`if(Get-Process -Id ${pid} -ErrorAction SilentlyContinue){'ALIVE'}else{'EXITED'}`)==='EXITED','提醒排空后自然退出');
    pids.push(pid);launch=null;port=await start(true);
    await until(()=>evaluate(port,'Boolean(window.__TAURI__?.core?.invoke)'),'提醒排空后冷启IPC');
    await until(()=>evaluate(port,'globalThis.__nanfengDesktopWorkState?.runtimeInfo?.dataAreasReady===true'),'提醒排空后冷启双区');
    assert.equal(await click(port,'[data-action="show-chat"]'),true);
    await until(()=>evaluate(port,'Boolean(document.querySelector("[data-action=show-reminders]"))'),'提醒排空后任务入口');
    await until(async()=>{
     if(await evaluate(port,'globalThis.__nanfengDesktopWorkState?.pane === "reminders" && Boolean(document.querySelector(".desktop-reminder-result"))'))return true;
     await click(port,'[data-action="show-reminders"]');return false;
    },'冷启工作区完成切换后打开原计划结果');
   }
   const completed=await until(async()=>(await invokeWork('read_desktop_reminders')).plans.find(x=>x.planId===planId&&x.status==='COMPLETED'),'原生自然到期结果');
   assert.ok(completed.latestResult.startsWith('合成监控结果：编辑后的公开材料已核对完成。'));assert.ok(completed.latestResult.includes('https://example.com/synthetic-monitor'));assert.equal(completed.lastInputTokens,20);assert.equal(completed.lastOutputTokens,8);assert.equal(completed.lastChargeMicros,7);
   const executionRequest=requests.filter(x=>x.reminder)[1];assert.equal(executionRequest.editedInstructionPresent,true);assert.equal(executionRequest.sourceConversationPresent,false,'计划执行不外发来源完整对话');
   await until(()=>evaluate(port,'document.querySelector(".desktop-reminder-result")?.textContent.includes("编辑后的公开材料已核对完成")'),'运行事件刷新可见结果');
  }
 }else{
  const created=await invokeWork('create_desktop_manual_reminder_draft',{args:{workspaceId,conversationId:null,timezoneId:'UTC'}});
  const draft=created.drafts.find(x=>x.status==='PENDING_REVIEW');assert.ok(draft);
  const nextMinute=(Math.floor(Date.now()/60000)+1)*60000;
  projection=await invokeWork('confirm_desktop_reminder_draft',{args:{draftId:draft.draftId,title:'合成失败计量计划',instruction:'只核对合成公开材料',scheduleKind:'ONCE',anchorLocal:new Date(nextMinute).toISOString().slice(0,16),timezoneId:'UTC',missedPolicy:'RUN_ONCE'}});
  entity=projection.plans.find(x=>x.sourceDraftId===draft.draftId);assert.ok(entity);assert.equal(entity.nextRunAtMs,nextMinute);
  console.log(JSON.stringify({stage:'confirmed synthetic due time',waitMilliseconds:Math.max(0,nextMinute-Date.now())}));
  await until(()=>Date.now()>=nextMinute,'确认计划到期');
  projection=await invokeWork('run_desktop_reminders_due');
  entity=await until(async()=>(await invokeWork('read_desktop_reminders')).plans.find(x=>x.planId===entity.planId&&x.lastSafeErrorCode==='OUTPUT_LIMIT'),'计划失败计量');
  assert.equal(entity.lastInputTokens,20);assert.equal(entity.lastOutputTokens,8);assert.equal(entity.lastChargeMicros,7);assert.equal(entity.latestResult,null);
 }
 const expectedReminderRequests=lifecycleMode?2:1;
 const reminderCount=()=>requests.filter(x=>x.reminder).length;assert.equal(reminderCount(),expectedReminderRequests);
 let protocolHotActivation = null;
 if(notificationsMode){
  const plan=(await invokeWork('read_desktop_reminders')).plans.find(x=>x.sourceDraftId===entity.draftId);
  assert.ok(plan?.planId);
  const audit=JSON.parse(execFileSync('python',[join(desktopRoot,'scripts/read-windows-acceptance-reminder-run.py'),root],{encoding:'utf8'}));
  assert.equal(audit.runs.length,1);
  const run=audit.runs[0];assert.equal(run.notificationState,'SENT');
  const url=`nanfeng-ai-windows-acceptance-reminder://open/WORK/${run.runId}`;
  assert.equal(await click(port,'[data-action="show-chat"]'),true);
  await until(()=>evaluate(port,'globalThis.__nanfengDesktopWorkState?.dataArea === "CHAT"'),'CHAT before system protocol activation');
  ps(`Start-Process -FilePath ${quote(url)} -WindowStyle Hidden`);
  await until(()=>evaluate(port,`globalThis.__nanfengDesktopWorkState?.dataArea === "CHAT" && globalThis.__nanfengDesktopWorkState?.selectedReminderPlanId === ${JSON.stringify(plan.planId)} && globalThis.__nanfengDesktopWorkState?.pane === "settings" && globalThis.__nanfengDesktopWorkState?.settingsSection === "reminders"`),'Windows registered protocol returns to original WORK plan');
  protocolHotActivation={runId:run.runId,planId:plan.planId,osRegisteredProtocol:true,returnedFromChatToWork:true};
 }
 const records=(await invokeWork('read_desktop_usage_ledger')).records.filter(x=>x.modelId.startsWith('fixture-reminder'));
 assert.equal(records.length,expectedReminderRequests);for(const record of records){assert.equal(record.inputTokens,20);assert.equal(record.outputTokens,8);assert.equal(record.chargeMicros,7);}
 await closeNormally();port=await start(true);await until(()=>evaluate(port,'Boolean(window.__TAURI__?.core?.invoke)'),'冷启IPC');
 const cold=await invokeWork('read_desktop_reminders');
 if(runMode){const plan=cold.plans.find(x=>x.planId===entity.planId);assert.equal(plan.lastSafeErrorCode,'OUTPUT_LIMIT');assert.equal(plan.lastInputTokens,20);assert.equal(plan.lastChargeMicros,7);assert.equal(plan.latestResult,null);}
 else{const draft=cold.drafts.find(x=>x.draftId===entity.draftId);assert.equal(draft.status,visibleMode?'CONFIRMED':successMode?'PENDING_REVIEW':'FAILED');assert.equal(draft.inputTokens,20);assert.equal(draft.chargeMicros,7);if(visibleMode){const plan=cold.plans.find(x=>x.sourceDraftId===entity.draftId);assert.ok(plan);assert.equal(plan.title,finalTitle);assert.equal(plan.instruction,finalInstruction);assert.equal(plan.anchorLocal,finalAnchor);assert.equal(plan.timezoneId,'UTC');assert.equal(plan.missedPolicy,lifecycleMode?'RUN_ONCE':'SKIP');if(lifecycleMode){assert.equal(plan.status,'COMPLETED');assert.ok(plan.latestResult.startsWith('合成监控结果：编辑后的公开材料已核对完成。'));assert.ok(plan.latestResult.includes('https://example.com/synthetic-monitor'));}}}
 if(lifecycleMode){
  await until(()=>evaluate(port,'globalThis.__nanfengDesktopWorkState?.runtimeInfo?.dataAreasReady===true'),'冷启双区');
  assert.equal(await click(port,'[data-action="show-chat"]'),true);
  await until(()=>evaluate(port,'globalThis.__nanfengDesktopWorkState?.dataArea === "CHAT"'),'冷启工作区切换');
  await until(()=>evaluate(port,'Boolean(document.querySelector("[data-action=show-reminders]"))'),'冷启任务入口');
  assert.equal(await click(port,'[data-action="show-reminders"]'),true);
  await until(()=>evaluate(port,'document.querySelector(".desktop-reminder-result")?.textContent.includes("编辑后的公开材料已核对完成")'),'冷启可见计划结果');
 }
 assert.equal((await invokeWork('read_desktop_usage_ledger')).records.filter(x=>x.modelId.startsWith('fixture-reminder')).length,expectedReminderRequests);assert.equal(reminderCount(),expectedReminderRequests);
 if(process.argv.includes('--cost-status')) {const expected=lifecycleMode?['已完成','已完成']:successMode?['已完成']:['失败 · OUTPUT_LIMIT'];await verifyVisibleUsageOutcomes({port,evaluate,click,until,modelId:'fixture-reminder',labels:expected});}

 let deletion=null;
 if(lifecycleMode){
 const beforeDelete=await invokeWork('read_desktop_reminders');const plan=beforeDelete.plans.find(p=>p.sourceDraftId===entity.draftId);assert.ok(plan);
 const beforeLedger=await invokeWork('read_desktop_usage_ledger');
 const sourceBefore=await invokeWork('read_desktop_workspace',{workspaceId});
 assert.equal(await click(port,`[data-action="delete-reminder-plan"][data-id="${plan.planId}"]`),true);
 await until(()=>evaluate(port,'Boolean(document.querySelector("[data-action=confirm-delete-reminder-plan]"))'),'删除确认');
 assert.equal(await click(port,'[data-action="close-dialog"]'),true);
 assert.deepEqual(await invokeWork('read_desktop_reminders'),beforeDelete,'取消删除精确保留');
 assert.equal(await click(port,`[data-action="delete-reminder-plan"][data-id="${plan.planId}"]`),true);
 await until(()=>evaluate(port,'Boolean(document.querySelector("[data-action=confirm-delete-reminder-plan]"))'),'再次确认');
 assert.equal(await click(port,'[data-action="confirm-delete-reminder-plan"]'),true);
 await until(async()=>!(await invokeWork('read_desktop_reminders')).plans.some(p=>p.planId===plan.planId),'计划删除');
 const deleted=await invokeWork('read_desktop_reminders');assert.deepEqual(await invokeWork('read_desktop_usage_ledger'),beforeLedger);assert.deepEqual(await invokeWork('read_desktop_workspace',{workspaceId}),sourceBefore);
 await closeNormally();port=await start(true);await until(()=>evaluate(port,'globalThis.__nanfengDesktopWorkState?.runtimeInfo?.dataAreasReady===true'),'删除冷启');
 assert.deepEqual(await invokeWork('read_desktop_reminders'),deleted);assert.deepEqual(await invokeWork('read_desktop_usage_ledger'),beforeLedger);assert.deepEqual(await invokeWork('read_desktop_workspace',{workspaceId}),sourceBefore);assert.equal(reminderCount(),2);
 assert.equal((await invoke(port,'read_desktop_reminders',{},'WORK')).plans.length,0);
  assert.deepEqual(await invoke(port,'read_desktop_workspace',{workspaceId:otherId},'WORK'),otherBefore);
  assert.equal(await evaluate(port,`document.querySelectorAll('[data-action=show-work],[data-action=show-projects],[data-action=show-knowledge],[data-action=show-p8-inspect],[data-action=select-workspace]').length`),0);
  assert.equal(await evaluate(port,'globalThis.__nanfengDesktopWorkState.dataArea'), 'CHAT');
 deletion={planId:plan.planId,cancelExact:true,confirmedAbsentAfterCold:true,ledgerExact:true,sourceExact:true,workPlans:0,otherBefore,beforeDelete,deleted,beforeLedger,sourceBefore};
 }
 result={candidateSha256:candidateHash,root,notificationsMode,protocolHotActivation,runMode,badProposalMode,successMode,visibleMode,lifecycleMode,closeDrainMode,entityId:entity.planId||entity.draftId,usagePersisted:true,coldReadback:true,reminderRequests:reminderCount(),pids,normalCloses,forcedPids,deletion};
 }
}finally{if(releaseReminder)releaseReminder();if(launch){if(result)await closeNormally();else stop();}provider.closeAllConnections();await new Promise(done=>provider.close(done));}
await writeFile(join(root,'chat-reminder-current-lifecycle.json'),JSON.stringify(result,null,2),{flag:'wx'});console.log(JSON.stringify({candidateSha256:result.candidateSha256,root,lifecycleMode:result.lifecycleMode,normalCloseCount:normalCloses.length,forcedPids,pids,pass:true}));

