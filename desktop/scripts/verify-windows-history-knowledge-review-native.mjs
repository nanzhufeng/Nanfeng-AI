import { verifyVisibleUsageOutcomes } from './verify-visible-usage-outcomes.mjs';
import assert from 'node:assert/strict';
import { createHash } from 'node:crypto';
import { execFileSync, spawn } from 'node:child_process';
import { readFile } from 'node:fs/promises';
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
const runningExecutable = resolve(desktopRoot, 'output/windows-acceptance-binaries', receipt.sha256, 'nanfeng-ai-desktop-spike.exe');
const activeAreaCancelMode = process.argv.includes('--cancel-active-area');
const retryLedgerRepairMode = process.argv.includes('--retry-ledger-repair');
const retryAreaRaceMode = process.argv.includes('--retry-area-race') || activeAreaCancelMode || retryLedgerRepairMode;
const failureMeasurementMode = process.argv.includes('--failed-measurement') || retryAreaRaceMode;
const completionWriteDeniedMode = process.argv.includes('--completion-write-denied');
const closeDrainMode = process.argv.includes('--close-drain') || completionWriteDeniedMode;
assert.ok(!closeDrainMode || !failureMeasurementMode, '关闭排空模式使用确定成功的合成响应');
let releaseHistory = null;
let holdTimedOut = false;
const visibleContextChatMode = process.argv.includes('--visible-context-chat');
const visibleContextSettingsMode = process.argv.includes('--visible-context-settings') || visibleContextChatMode;
const contextUseMode = process.argv.includes('--context-use') || visibleContextSettingsMode;
const requests = [];
const provider = http.createServer(async (request,response) => {
  const chunks=[]; for await(const chunk of request) chunks.push(chunk);
  const body=JSON.parse(Buffer.concat(chunks).toString('utf8'));
  requests.push({model:body.model,stream:body.stream,knowledgePresent:JSON.stringify(body.messages).includes('已审阅合成正文')});
  if(body.model !== 'fixture-history-knowledge'){
    response.writeHead(200,{'Content-Type':'text/event-stream'});
    response.write(`data: ${JSON.stringify({model:body.model,choices:[{delta:{content:'合成已完成回答，长期保留原始资料。'}}]})}\n\n`);
    response.end(`data: ${JSON.stringify({choices:[{delta:{},finish_reason:'stop'}],usage:{prompt_tokens:10,completion_tokens:8}})}\n\ndata: [DONE]\n\n`);return;
  }
  const proposal={eligible:true,confidence:0.95,title:'合成候选原标题',body:'合成候选原正文',tags:['原标签']};
  const historyRequestCount=requests.filter(x=>x.model==='fixture-history-knowledge').length;
  if(closeDrainMode || (retryAreaRaceMode && historyRequestCount===2))await new Promise(done=>{
    const timer=setTimeout(()=>{holdTimedOut=true;releaseHistory=null;done()},60000);
    releaseHistory=()=>{clearTimeout(timer);releaseHistory=null;done()};
  });
  response.writeHead(200,{'Content-Type':'text/event-stream'});
  response.write(`data: ${JSON.stringify({model:body.model,choices:[{delta:{content:JSON.stringify(proposal)}}]})}\n\n`);
  response.end(`data: ${JSON.stringify({choices:[{delta:{},finish_reason:failureMeasurementMode&&(!retryAreaRaceMode||historyRequestCount===1)?'length':'stop'}],usage:{prompt_tokens:20,completion_tokens:8,cost:0.000007}})}\n\ndata: [DONE]\n\n`);
});
await new Promise(done=>provider.listen(0,'127.0.0.1',done));
const endpoint=`http://127.0.0.1:${provider.address().port}`;
const ps = script => execFileSync('pwsh', ['-NoProfile', '-Command', script], { encoding: 'utf8', timeout: 60000 }).trim();
const quote = value => `'${String(value).replaceAll("'", "''")}'`;
function historyUsageRow(root, attemptId){
 const code="import json,sqlite3,sys\nfrom pathlib import Path\np=Path(sys.argv[1]);assert json.loads((p/'.nanfeng-windows-acceptance.json').read_text(encoding='utf-8-sig'))['identifier']=='com.nanzhufeng.ai.desktop.windowsacceptance'\nc=sqlite3.connect((p/'work-area/usage-ledger/usage-ledger-v1.sqlite3').as_uri()+'?mode=ro',uri=True);c.row_factory=sqlite3.Row;r=c.execute('SELECT * FROM usage_ledger_entries WHERE attempt_id=?',(sys.argv[2],)).fetchone();print(json.dumps(dict(r) if r else None));c.close()";
 return JSON.parse(execFileSync('python',['-c',code,root,attemptId],{encoding:'utf8',timeout:10000}));
}
async function reservePort() {
  const server = http.createServer();
  await new Promise(done => server.listen(0, '127.0.0.1', done));
  const port = server.address().port;
  await new Promise(done => server.close(done));
  return port;
}
let launch = null;
let root = '';
const pids = [];
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
  if (reopen) args.push('-Root', root);
  launch = await startProcess(args);
  assert.equal(launch.sha256, receipt.sha256);
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
  ps(`$p=Get-CimInstance Win32_Process -Filter "ProcessId=${pid}"; if ($p) { if ([IO.Path]::GetFullPath([string]$p.ExecutablePath) -ne ${quote(runningExecutable)} -or (Get-FileHash -LiteralPath ${quote(runningExecutable)} -Algorithm SHA256).Hash -ne ${quote(receipt.sha256)}) { throw 'PID identity mismatch' }; Stop-Process -Id ${pid} -Force -ErrorAction Stop }`);
  pids.push(pid);
  launch = null;
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
      const timer = setTimeout(() => fail(new Error('CDP evaluation timed out')), 30000);
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
async function until(read, label, timeout = 30000) {
  const deadline = Date.now() + timeout;
  while (Date.now() < deadline) {
    try { const value = await read(); if (value) return value; } catch { /* Startup or redraw in progress. */ }
    await new Promise(done => setTimeout(done, 200));
  }
  throw new Error(`${label} 未在原生界面出现`);
}
const click = (port, selector) => evaluate(port, `(()=>{const node=document.querySelector(${JSON.stringify(selector)});if(!node)return false;node.click();return true})()`);
async function visibleWorkQuestion(port,workspaceId){
 const before=new Set((await invoke(port,'read_desktop_workspace',{workspaceId},'WORK')).exchange.conversations.map(x=>x.id));
 assert.equal(await click(port,'[data-action="show-chat"]'),true);
 await until(()=>evaluate(port,'globalThis.__nanfengDesktopWorkState?.dataArea === "CHAT" && Boolean(document.querySelector("[data-action=show-work]"))'),'设置返回对话区');
 assert.equal(await click(port,'[data-action="show-work"]'),true);
 await until(()=>evaluate(port,'globalThis.__nanfengDesktopWorkState?.dataArea === "WORK" && Boolean(document.querySelector("[data-action=new-chat]"))'),'工作区新对话入口');
 assert.equal(await click(port,'[data-action="new-chat"]'),true);
 await until(()=>evaluate(port,'Boolean(document.getElementById("chat-composer"))'),'新对话输入');
 assert.equal(await click(port,'[data-action="toggle-composer-add"]'),true);
 await until(()=>evaluate(port,'Boolean(document.querySelector("[data-action=toggle-conversation-web-search]"))'),'本次联网选项');
 if(await evaluate(port,'document.querySelector("[data-action=toggle-conversation-web-search]").getAttribute("aria-checked")')!=='false')assert.equal(await click(port,'[data-action="toggle-conversation-web-search"]'),true);
 await until(()=>evaluate(port,'document.querySelector("[data-action=toggle-conversation-web-search]")?.getAttribute("aria-checked")==="false"'),'关闭合成本次联网');
 assert.equal(await click(port,'[data-action="close-composer-add"]'),true);
 await evaluate(port,'(()=>{const n=document.getElementById("chat-composer");n.value="已审阅合成标题";n.dispatchEvent(new Event("input",{bubbles:true}));return true})()');
 await until(()=>evaluate(port,'Boolean(document.querySelector("[data-action=save-local-message]:not([disabled])"))'),'可见发送已就绪');
 assert.equal(await click(port,'[data-action="save-local-message"]'),true);
 const conversation=await until(async()=>(await invoke(port,'read_desktop_workspace',{workspaceId},'WORK')).exchange.conversations.find(x=>!before.has(x.id)&&x.messages.some(m=>m.role==='user'&&m.blocks?.some(b=>b.text==='已审阅合成标题'))&&x.messages.some(m=>m.role==='assistant'&&m.delivery==='COMPLETE')),'可见发送创建并完成会话');
 await until(()=>evaluate(port,'document.querySelector(".chat-scroll")?.textContent.includes("合成已完成回答，长期保留原始资料")'),'可见回答正文');
 await until(async()=>(await invoke(port,'read_desktop_ordinary_chat_context_records',{},'WORK')).some(x=>x.conversationId===conversation.id),'可见发送持久来源');
 assert.equal(await click(port,'[data-action="show-settings"]'),true);
 await until(()=>evaluate(port,'Boolean(document.querySelector("[data-action=toggle-product-setting][data-key=historyLibraryEnabled]"))'),'返回设置开关');
 return {conversationId:conversation.id};
}
let result;
try {
 let port=await start();
 await until(()=>evaluate(port,'Boolean(window.__TAURI__?.core?.invoke)'), 'IPC');
 await until(()=>evaluate(port,'Boolean(document.querySelector("[data-action=show-work]"))'), '工作入口');
 assert.equal(await click(port,'[data-action="show-work"]'),true);
 await until(()=>evaluate(port,'globalThis.__nanfengDesktopWorkState?.dataArea === "WORK" && Boolean(globalThis.__nanfengDesktopWorkState?.current)'), '工作区完成启动');
 const workspaceId=(await invoke(port,'ensure_desktop_local_workspace',{},'WORK')).summary.id;
 let conversationId=null;
 for(const text of ['只在隔离根中保存的长期开发原则。'.repeat(50),'长期保持可追溯来源，每次先审阅再保存长期知识。']) {
  const conversation=conversationId?(await invoke(port,'read_desktop_workspace',{workspaceId},'WORK')).exchange.conversations.find(x=>x.id===conversationId):null;
  const attempt=await invoke(port,'submit_desktop_ordinary_chat',{args:{workspaceId,conversationId,expectedRevision:conversation?.revision??null,text,attachmentIds:[],webSearchOverride:conversationId?null:false,egressAuthorization:{approvedAtMs:Date.now(),disclosureVersion:'normal-chat-egress-v1'}}},'WORK');
  assert.equal(attempt.state,'COMPLETED'); conversationId=attempt.conversationId;
 }
 const settings=await invoke(port,'read_desktop_app_settings',{},'WORK');
 await invoke(port,'save_desktop_app_settings',{args:{appearance:settings.appearance,product:{...settings.product,historyLibraryEnabled:true},expectedRevision:settings.revision}},'WORK');
 if(retryLedgerRepairMode){
  execFileSync('python',['-c',`import sqlite3,sys\nc=sqlite3.connect(sys.argv[1]);c.execute("CREATE TRIGGER synthetic_history_ledger_append_denied BEFORE INSERT ON usage_ledger_entries WHEN NEW.requested_model_id='fixture-history-knowledge' BEGIN SELECT RAISE(ABORT,'synthetic history ledger denied'); END");c.commit()`,join(root,'work-area','usage-ledger','usage-ledger-v1.sqlite3')],{encoding:'utf8',timeout:10000});
 }
 if(closeDrainMode){
  await evaluate(port,'globalThis.__historyDrainCall=window.__TAURI__.core.invoke("run_desktop_history_knowledge_due",{}, {headers:{"x-nanfeng-data-area":"WORK"}}).then(value=>({ok:true,value}),error=>({ok:false,error:String(error)}));true');
  await until(()=>releaseHistory,'历史整理生产请求已抵达回环端');
  const pid=Number(launch.pid);
  if(completionWriteDeniedMode){
   const database=join(root,'work-area','workspace.sqlite3');
   const triggerCode=`import sqlite3,sys\nc=sqlite3.connect(sys.argv[1]);c.execute("CREATE TRIGGER synthetic_history_completion_denied BEFORE UPDATE ON desktop_history_knowledge_candidates_v1 WHEN NEW.status='PENDING_REVIEW' BEGIN SELECT RAISE(ABORT,'synthetic completion denied'); END");c.commit()`;
   execFileSync('python',['-c',triggerCode,database],{encoding:'utf8',timeout:10000});
   console.log(JSON.stringify({stage:'completion-write-denied',candidateSha256:receipt.sha256,root,pid}));
   assert.equal(holdTimedOut,false);releaseHistory();
   const failed=await evaluate(port,'globalThis.__historyDrainCall');
   assert.equal(failed.ok,false,'完成写入被拒必须向调用方返回失败');
   execFileSync('python',['-c','import sqlite3,sys\nc=sqlite3.connect(sys.argv[1]);c.execute("DROP TRIGGER synthetic_history_completion_denied");c.commit()',database],{encoding:'utf8',timeout:10000});
   const closed=ps(`$p=Get-Process -Id ${pid} -ErrorAction Stop;if ([IO.Path]::GetFullPath($p.Path) -ne ${quote(runningExecutable)} -or (Get-FileHash -LiteralPath ${quote(runningExecutable)} -Algorithm SHA256).Hash -ne ${quote(receipt.sha256)}){throw '关闭身份不符'};$p.CloseMainWindow()`);
   assert.equal(closed,'True');
   await until(()=>ps(`if(Get-Process -Id ${pid} -ErrorAction SilentlyContinue){'ALIVE'}else{'EXITED'}`)==='EXITED','完成失败后正常关闭不得遗留活动信号',5000);
   pids.push(pid);launch=null;port=await start(true);
   await until(()=>evaluate(port,'Boolean(window.__TAURI__?.core?.invoke)'),'写入失败冷启IPC');
   const cold=await invoke(port,'read_desktop_history_knowledge',{},'WORK');
   assert.equal(cold.candidates.length,1);assert.equal(cold.candidates[0].status,'UNKNOWN');
   assert.equal(requests.filter(x=>x.model==='fixture-history-knowledge').length,1,'失败冷启不可隐式重发');
   result={candidateSha256:receipt.sha256,root,completionWriteDenied:true,callerFailed:true,naturalExit:true,coldUnknown:true,historyRequests:1,pids};
  }else{
  const closed=ps(`$p=Get-Process -Id ${pid} -ErrorAction Stop;if ([IO.Path]::GetFullPath($p.Path) -ne ${quote(runningExecutable)} -or (Get-FileHash -LiteralPath ${quote(runningExecutable)} -Algorithm SHA256).Hash -ne ${quote(receipt.sha256)}){throw '关闭身份不符'};$p.CloseMainWindow()`);
  assert.equal(closed,'True');
  await new Promise(done=>setTimeout(done,1000));
  assert.equal(ps(`if(Get-Process -Id ${pid} -ErrorAction SilentlyContinue){'ALIVE'}else{'EXITED'}`),'ALIVE','真实请求未结束时应保持进程');
  assert.equal(holdTimedOut,false);
  releaseHistory();
  await until(()=>ps(`if(Get-Process -Id ${pid} -ErrorAction SilentlyContinue){'ALIVE'}else{'EXITED'}`)==='EXITED','排空后自然退出');
  pids.push(pid);launch=null;
  port=await start(true);
  await until(()=>evaluate(port,'Boolean(window.__TAURI__?.core?.invoke)'),'关闭后冷启IPC');
  const cold=await invoke(port,'read_desktop_history_knowledge',{},'WORK');
  assert.equal(cold.candidates.length,1);
  const candidate=cold.candidates[0];
  assert.equal(candidate.status,'PENDING_REVIEW');assert.equal(candidate.title,'合成候选原标题');assert.equal(candidate.inputTokens,20);assert.equal(candidate.chargeMicros,7);
  assert.equal(requests.filter(x=>x.model==='fixture-history-knowledge').length,1,'冷启不应重发已完成整理');
  result={candidateSha256:receipt.sha256,root,closeDrain:true,aliveWhileProviderHeld:true,naturalExit:true,coldReadback:true,candidateId:candidate.candidateId,historyRequests:1,pids};
  }
 }else{
 const due=await invoke(port,'run_desktop_history_knowledge_due',{},'WORK');
 if(failureMeasurementMode){
  const failed=due.candidates.find(x=>x.status==='FAILED');assert.ok(failed);
  assert.equal(failed.safeErrorCode,'OUTPUT_LIMIT');assert.equal(failed.inputTokens,20);assert.equal(failed.outputTokens,8);assert.equal(failed.chargeMicros,7);assert.equal(failed.body,null);
  const records=(await invoke(port,'read_desktop_usage_ledger',{},'WORK')).records;
  const usage=records.find(x=>x.entryId==='usage-'+failed.attemptId);
  if(retryLedgerRepairMode)assert.equal(usage,undefined,'故障注入必须确实阻止原账本追加');
  else{assert.ok(usage);assert.equal(usage.inputTokens,20);assert.equal(usage.outputTokens,8);assert.equal(usage.chargeMicros,7);assert.equal(usage.requestState,'FAILED');assert.equal(usage.safeErrorCode,'OUTPUT_LIMIT');}
  stop();port=await start(true);await until(()=>evaluate(port,'Boolean(window.__TAURI__?.core?.invoke)'),'失败冷启IPC');
  const cold=(await invoke(port,'read_desktop_history_knowledge',{},'WORK')).candidates.find(x=>x.candidateId===failed.candidateId);
  assert.equal(cold.status,'FAILED');assert.equal(cold.safeErrorCode,'OUTPUT_LIMIT');assert.equal(cold.inputTokens,20);assert.equal(cold.outputTokens,8);assert.equal(cold.chargeMicros,7);
  assert.equal((await invoke(port,'read_desktop_usage_ledger',{},'WORK')).records.filter(x=>x.entryId==='usage-'+failed.attemptId).length,retryLedgerRepairMode?0:1);
  assert.equal(requests.filter(x=>x.model==='fixture-history-knowledge').length,1);
  if(process.argv.includes('--cost-status'))await verifyVisibleUsageOutcomes({port,evaluate,click,until,section:'history',modelId:'fixture-history-knowledge',labels:['失败 · OUTPUT_LIMIT']});
  if(retryAreaRaceMode){
   await until(()=>evaluate(port,'globalThis.__nanfengDesktopWorkState?.runtimeInfo?.dataAreasReady === true'),'重试冷启双区');
   await until(()=>evaluate(port,'Boolean(document.querySelector("[data-action=show-work]"))'),'工作入口');assert.equal(await click(port,'[data-action="show-work"]'),true);
   await until(()=>evaluate(port,'Boolean(document.querySelector("[data-action=show-knowledge]"))'),'知识入口');assert.equal(await click(port,'[data-action="show-knowledge"]'),true);
   const selector=`[data-action="retry-history-knowledge"][data-id="${failed.candidateId}"]`;
   const previousLedgerBefore=historyUsageRow(root,failed.attemptId);
   await until(()=>evaluate(port,`Boolean(document.querySelector(${JSON.stringify(selector)}))`),'失败候选重试入口');assert.equal(await click(port,selector),true);
   if(retryLedgerRepairMode){
    await until(async()=>releaseHistory||await evaluate(port,'String(globalThis.__nanfengDesktopWorkState?.error||"").includes("历史资料库操作未完成")'),'故障下重试阻止或请求观测');
    const blocked=(await invoke(port,'read_desktop_history_knowledge',{},'WORK')).candidates.find(x=>x.candidateId===failed.candidateId);
    console.log(JSON.stringify({stage:'retry-ledger-block-observed',candidateSha256:receipt.sha256,root,workspaceId,candidateId:failed.candidateId,historyRequests:requests.filter(x=>x.model==='fixture-history-knowledge').length,original:failed,blocked,unexpectedNetwork:Boolean(releaseHistory)}));
    assert.equal(Boolean(releaseHistory),false,'旧计量未安全留存前不得发起新网络请求');assert.deepEqual(blocked,cold,'故障下保留完整旧计量候选回执');
    execFileSync('python',['-c','import sqlite3,sys\nc=sqlite3.connect(sys.argv[1]);c.execute("DROP TRIGGER synthetic_history_ledger_append_denied");c.commit()',join(root,'work-area','usage-ledger','usage-ledger-v1.sqlite3')],{encoding:'utf8',timeout:10000});
    assert.equal(await click(port,selector),true);
   }
   await until(()=>releaseHistory,'显式重试抵达回环并暂停');
   if(retryLedgerRepairMode){const repaired=(await invoke(port,'read_desktop_usage_ledger',{},'WORK')).records.filter(x=>x.entryId==='usage-'+failed.attemptId);assert.equal(repaired.length,1);assert.equal(repaired[0].chargeMicros,7);}
   if(activeAreaCancelMode){
    assert.equal(await click(port,'[data-action="show-work"]'),true);
    await until(()=>evaluate(port,'globalThis.__nanfengDesktopWorkState?.dataArea==="WORK" && Boolean(document.getElementById("chat-composer"))'),'等待期间返回本区工作对话');
   }else{
   assert.equal(await click(port,'[data-action="return-workspace-origin"]'),true);
   await until(()=>evaluate(port,'globalThis.__nanfengDesktopWorkState?.dataArea==="CHAT" && Boolean(document.getElementById("chat-composer"))'),'等待期间实际返回CHAT');
   }
   if(activeAreaCancelMode){
    assert.equal(await click(port,'[data-action="show-settings"]'),true);
    await until(()=>evaluate(port,'Boolean(document.querySelector("[data-action=open-settings-page][data-page=personalization]"))'),'WORK个性化入口');assert.equal(await click(port,'[data-action="open-settings-page"][data-page="personalization"]'),true);
    const toggle='[data-action="toggle-product-setting"][data-key="historyLibraryEnabled"]';
    await until(()=>evaluate(port,`document.querySelector(${JSON.stringify(toggle)})?.getAttribute('aria-checked')==='true'`),'本区资料开关显示已启用');assert.equal(await click(port,toggle),true);
    await until(async()=>!(await invoke(port,'read_desktop_app_settings',{},'WORK')).product.historyLibraryEnabled,'本区关闭落盘');
    const workProjection=await invoke(port,'read_desktop_history_knowledge',{},'WORK');const chatProjection=await invoke(port,'read_desktop_history_knowledge',{},'CHAT');
    console.log(JSON.stringify({stage:'active-area-cancel-observed',candidateSha256:receipt.sha256,root,workspaceId,failedCandidateId:failed.candidateId,failedAttemptId:failed.attemptId,workEnabled:workProjection.enabled,workPaused:workProjection.paused,chatEnabled:chatProjection.enabled,chatPaused:chatProjection.paused}));
    assert.equal(workProjection.paused,true,'当前区关闭开关必须暂停其调度');assert.equal(chatProjection.paused,true);
    const cancelled=await until(async()=> (await invoke(port,'read_desktop_history_knowledge',{},'WORK')).candidates.find(x=>x.candidateId===failed.candidateId&&x.status==='FAILED'&&x.safeErrorCode==='CANCELLED'),'当前区活动请求因关闭而取消');
    assert.equal(cancelled.body,null);
    for(const field of ['actualModelId','inputTokens','outputTokens','cachedInputTokens','reasoningTokens','chargeMicros','currencyCode','costSource'])assert.equal(cancelled[field],null,`新取消Attempt没有继承旧${field}`);
    assert.equal(holdTimedOut,false);releaseHistory();
    stop();port=await start(true);await until(()=>evaluate(port,'Boolean(window.__TAURI__?.core?.invoke)'),'本区关闭冷启IPC');
    const coldCancelled=(await invoke(port,'read_desktop_history_knowledge',{},'WORK')).candidates.find(x=>x.candidateId===failed.candidateId);assert.equal(coldCancelled.safeErrorCode,'CANCELLED');assert.equal(coldCancelled.status,'FAILED');
    assert.equal(requests.filter(x=>x.model==='fixture-history-knowledge').length,2);
    result={candidateSha256:receipt.sha256,root,workspaceId,failedCandidateId:failed.candidateId,failedAttemptId:failed.attemptId,cancelledAttemptId:cancelled.attemptId,checkpointId:cancelled.checkpointId,localDisableCancelledActive:true,coldNoAutoRetry:true,historyRequests:2,providerRequests:requests.length,pids};
   }else{
   const before=await evaluate(port,'({error:globalThis.__nanfengDesktopWorkState.error,status:globalThis.__nanfengDesktopWorkState.status,history:globalThis.__nanfengDesktopWorkState.historyKnowledge})');
   assert.equal(holdTimedOut,false);releaseHistory();
   const retried=await until(async()=> (await invoke(port,'read_desktop_history_knowledge',{},'WORK')).candidates.find(x=>x.candidateId===failed.candidateId&&x.status==='PENDING_REVIEW'),'原WORK显式重试完成');
   await evaluate(port,'new Promise(resolve=>requestAnimationFrame(()=>requestAnimationFrame(resolve)))');
   const after=await evaluate(port,'({error:globalThis.__nanfengDesktopWorkState.error,status:globalThis.__nanfengDesktopWorkState.status,history:globalThis.__nanfengDesktopWorkState.historyKnowledge})');
   console.log(JSON.stringify({stage:'retry-area-race-observed',candidateSha256:receipt.sha256,root,workspaceId,failedCandidateId:failed.candidateId,failedAttemptId:failed.attemptId,retriedAttemptId:retried.attemptId,checkpointId:failed.checkpointId,before,after}));
   assert.deepEqual(after,before,'原WORK迟到重试不能改CHAT可见状态');assert.notEqual(retried.attemptId,failed.attemptId);assert.equal(retried.checkpointId,failed.checkpointId);
   stop();port=await start(true);await until(()=>evaluate(port,'Boolean(window.__TAURI__?.core?.invoke)'),'重试完成冷启IPC');
   const final=(await invoke(port,'read_desktop_history_knowledge',{},'WORK')).candidates.find(x=>x.candidateId===failed.candidateId);assert.equal(final.status,'PENDING_REVIEW');assert.equal(final.attemptId,retried.attemptId);assert.equal(requests.filter(x=>x.model==='fixture-history-knowledge').length,2);
   const previousLedgerAfter=historyUsageRow(root,failed.attemptId);
   if(!retryLedgerRepairMode)assert.deepEqual(previousLedgerAfter,previousLedgerBefore,'已有旧计量账本完整行（含时间）不改写');
   result={candidateSha256:receipt.sha256,root,workspaceId,failedCandidateId:failed.candidateId,failedAttemptId:failed.attemptId,retriedAttemptId:retried.attemptId,checkpointId:failed.checkpointId,lateDestinationUnchanged:true,coldNoAutoRetry:true,blockedRetryPreservedCandidate:retryLedgerRepairMode,repairedPreviousLedger:retryLedgerRepairMode,previousLedgerBefore,previousLedgerAfter,historyRequests:2,providerRequests:requests.length,pids};
   }
  }else result={candidateSha256:receipt.sha256,root,failedCandidateId:failed.candidateId,failedUsagePersisted:true,coldReadback:true,providerRequests:requests.length,pids};
 }else{
 assert.ok(due.candidates.some(x=>x.status==='PENDING_REVIEW'),JSON.stringify({requests,candidates:due.candidates.map(x=>({status:x.status,error:x.safeErrorCode}))}));
 const candidate=await until(async()=> (await invoke(port,'read_desktop_history_knowledge',{},'WORK')).candidates.find(x=>x.status==='PENDING_REVIEW'), '待审阅候选');
 assert.equal(requests.filter(x=>x.model==='fixture-history-knowledge').length,1);
 assert.ok(!(await invoke(port,'read_desktop_workspace',{workspaceId},'WORK')).exchange.knowledge.some(x=>x.title==='合成候选原标题'));
 // Reload through the normal startup/read owners to display the durable candidate.
 await evaluate(port,'globalThis.__historyReloadOld=true;location.reload();true');
 await until(()=>evaluate(port,'globalThis.__historyReloadOld !== true && globalThis.__nanfengDesktopWorkState?.runtimeInfo?.dataAreasReady === true'),'新页面双区就绪');
 await until(()=>evaluate(port,'Boolean(document.querySelector("[data-action=show-work]"))'),'重载工作入口');
 assert.equal(await click(port,'[data-action="show-work"]'),true);
 await until(()=>evaluate(port,'Boolean(document.querySelector("[data-action=show-knowledge]"))'),'知识入口');
 assert.equal(await click(port,'[data-action="show-knowledge"]'),true);
 await until(()=>evaluate(port,`Boolean(document.querySelector('[data-action="review-history-knowledge"][data-id="${candidate.candidateId}"]'))`),'候选可见');
 if(process.argv.includes('--reject-delete')){
  assert.equal(contextUseMode,false,'驳回删除采用独立默认路径');
  const rejectSelector=`[data-action="reject-history-knowledge"][data-id="${candidate.candidateId}"]`;
  assert.equal(await click(port,rejectSelector),true);
  await until(async()=> (await invoke(port,'read_desktop_history_knowledge',{},'WORK')).candidates.some(x=>x.candidateId===candidate.candidateId&&x.status==='REJECTED'),'实际驳回落盘');
  assert.equal((await invoke(port,'read_desktop_workspace',{workspaceId},'WORK')).exchange.knowledge.length,0);
  const deleteSelector=`[data-action="delete-history-knowledge"][data-id="${candidate.candidateId}"]`;
  await until(()=>evaluate(port,`Boolean(document.querySelector(${JSON.stringify(deleteSelector)}))`),'驳回候选删除按钮');assert.equal(await click(port,deleteSelector),true);
  await until(async()=>!(await invoke(port,'read_desktop_history_knowledge',{},'WORK')).candidates.some(x=>x.candidateId===candidate.candidateId),'驳回候选删除落盘');
  stop();port=await start(true);await until(()=>evaluate(port,'Boolean(window.__TAURI__?.core?.invoke)'),'驳回删除冷启IPC');
  assert.ok(!(await invoke(port,'read_desktop_history_knowledge',{},'WORK')).candidates.some(x=>x.candidateId===candidate.candidateId));
  assert.equal((await invoke(port,'read_desktop_workspace',{workspaceId},'WORK')).exchange.knowledge.length,0);assert.equal(requests.filter(x=>x.model==='fixture-history-knowledge').length,1);
  result={candidateSha256:receipt.sha256,root,workspaceId,candidateId:candidate.candidateId,rejectedAndDeleted:true,noKnowledgeCreated:true,coldNoAutoRetry:true,providerRequests:requests.length,pids};
 }else{
 assert.equal(await click(port,`[data-action="review-history-knowledge"][data-id="${candidate.candidateId}"]`),true);
 await evaluate(port,`(()=>{for(const [id,value] of [['history-candidate-title','已审阅合成标题'],['history-candidate-body','已审阅合成正文'],['history-candidate-tags','']]){const n=document.getElementById(id);n.value=value;n.dispatchEvent(new Event('input',{bubbles:true}));}const n=document.getElementById('history-candidate-body');n.focus();n.setSelectionRange(3,6);globalThis.__reviewBefore=n;globalThis.__nanfengDesktopWorkState.contextMenu={syntheticAcceptance:true};window.dispatchEvent(new Event('resize'));return true})()`);
 await until(()=>evaluate(port,'document.getElementById("history-candidate-body") !== globalThis.__reviewBefore'),'审阅框实际重绘');
 const edited=await evaluate(port,'({title:document.getElementById("history-candidate-title").value,body:document.getElementById("history-candidate-body").value,tags:document.getElementById("history-candidate-tags").value,focus:document.activeElement.id,start:document.activeElement.selectionStart,end:document.activeElement.selectionEnd})');
 assert.deepEqual(edited,{title:'已审阅合成标题',body:'已审阅合成正文',tags:'',focus:'history-candidate-body',start:3,end:6});
 assert.equal(await click(port,`[data-action="accept-history-knowledge"][data-id="${candidate.candidateId}"]`),true);
 const accepted=await until(async()=> (await invoke(port,'read_desktop_history_knowledge',{},'WORK')).candidates.find(x=>x.candidateId===candidate.candidateId&&x.status==='ACCEPTED'),'审阅入库');
 const knowledge=(await invoke(port,'read_desktop_workspace',{workspaceId},'WORK')).exchange.knowledge.find(x=>x.id===accepted.knowledgeId);
 assert.equal(knowledge.title,'已审阅合成标题'); assert.equal(knowledge.body,'已审阅合成正文');assert.deepEqual(knowledge.tags,[]);
 let contextConversationId=null;
 if(contextUseMode){
  if(visibleContextSettingsMode){
   assert.equal(await click(port,'[data-action="return-workspace-origin"]'),true);
   await until(()=>evaluate(port,'globalThis.__nanfengDesktopWorkState?.dataArea === "CHAT" && Boolean(document.querySelector("[data-action=show-work]"))'),'返回来源对话区');
   assert.equal(await click(port,'[data-action="show-work"]'),true);
   await until(()=>evaluate(port,'globalThis.__nanfengDesktopWorkState?.dataArea === "WORK" && Boolean(document.querySelector("[data-action=show-settings]"))'),'工作区设置就绪');
   await until(()=>evaluate(port,'Boolean(document.querySelector("[data-action=show-settings]"))'),'返回工作设置入口');
   assert.equal(await click(port,'[data-action="show-settings"]'),true);
   await until(()=>evaluate(port,'Boolean(document.querySelector("[data-action=open-settings-page][data-page=personalization]"))'),'个性化入口');
   assert.equal(await click(port,'[data-action="open-settings-page"][data-page="personalization"]'),true);
   await until(()=>evaluate(port,'Boolean(document.querySelector("[data-action=toggle-product-setting][data-key=memoryEnabled]"))'),'启用记忆开关');
   const memoryEnabled=await evaluate(port,'document.querySelector("[data-action=toggle-product-setting][data-key=memoryEnabled]").getAttribute("aria-checked")==="true"');
   if(!memoryEnabled)assert.equal(await click(port,'[data-action="toggle-product-setting"][data-key="memoryEnabled"]'),true);
   await until(async()=>{const s=await invoke(port,'read_desktop_app_settings',{},'WORK');return s.product.memoryEnabled&&s.product.memoryRetrievalEnabled},'可见记忆设置保存');
  }
  for(const enabled of (visibleContextSettingsMode?[false,true,false]:[true,false])){
   const current=await invoke(port,'read_desktop_app_settings',{},'WORK');
   if(visibleContextSettingsMode){
    assert.equal(current.product.historyLibraryEnabled,!enabled,'对照必须实际翻转开关');
    assert.equal(await click(port,'[data-action="toggle-product-setting"][data-key="historyLibraryEnabled"]'),true);
    await until(async()=>(await invoke(port,'read_desktop_app_settings',{},'WORK')).product.historyLibraryEnabled===enabled,'可见历史资料设置保存');
    assert.equal(await evaluate(port,'document.querySelector("[data-action=toggle-product-setting][data-key=historyLibraryEnabled]").getAttribute("aria-checked")==="true"'),enabled);
   }else await invoke(port,'save_desktop_app_settings',{args:{appearance:current.appearance,product:{...current.product,memoryEnabled:true,memoryRetrievalEnabled:true,historyLibraryEnabled:enabled},expectedRevision:current.revision}},'WORK');
   const attempt=visibleContextChatMode?await visibleWorkQuestion(port,workspaceId):await invoke(port,'submit_desktop_ordinary_chat',{args:{workspaceId,conversationId:null,expectedRevision:null,text:'已审阅合成标题',attachmentIds:[],webSearchOverride:false,egressAuthorization:{approvedAtMs:Date.now(),disclosureVersion:'normal-chat-egress-v1'}}},'WORK');
   if(!visibleContextChatMode)assert.equal(attempt.state,'COMPLETED');
   assert.equal(requests.at(-1).knowledgePresent,enabled,'历史资料库开关未按本次请求控制已采纳知识');
   const record=(await invoke(port,'read_desktop_ordinary_chat_context_records',{},'WORK')).find(x=>x.conversationId===attempt.conversationId);
   assert.ok(record);assert.equal(record.selectedSources.some(x=>x.kind==='资料库'&&x.title===knowledge.title),enabled);
   assert.ok(!JSON.stringify(record).includes(knowledge.body),'来源审计不能复制知识正文');
   if(enabled)contextConversationId=attempt.conversationId;
  }
 }
 stop();port=await start(true);
 await until(()=>evaluate(port,'Boolean(window.__TAURI__?.core?.invoke)'),'冷启IPC');
 const cold=(await invoke(port,'read_desktop_workspace',{workspaceId},'WORK')).exchange.knowledge.find(x=>x.id===knowledge.id);
 assert.equal(cold.title,knowledge.title);assert.equal(cold.body,knowledge.body);assert.deepEqual(cold.tags,[]);
 if(contextUseMode){
  const coldRecord=(await invoke(port,'read_desktop_ordinary_chat_context_records',{},'WORK')).find(x=>x.conversationId===contextConversationId);
  assert.ok(coldRecord.selectedSources.some(x=>x.kind==='资料库'&&x.title===knowledge.title));
  await until(()=>evaluate(port,'globalThis.__nanfengDesktopWorkState?.runtimeInfo?.dataAreasReady === true'),'冷启双区');
  await until(()=>evaluate(port,'Boolean(document.querySelector("[data-action=show-work]"))'),'冷启工作入口');
  assert.equal(await click(port,'[data-action="show-work"]'),true);
  await until(()=>evaluate(port,`Boolean(document.querySelector('[data-action="select-chat"][data-id="${contextConversationId}"]'))`),'上下文会话入口');
  assert.equal(await click(port,`[data-action="select-chat"][data-id="${contextConversationId}"]`),true);
  await until(()=>evaluate(port,`Boolean(document.querySelector('[data-action="open-assistant-message-menu"][data-message-id="${coldRecord.assistantMessageId}"]'))`),'回答更多入口');
  assert.equal(await click(port,`[data-action="open-assistant-message-menu"][data-message-id="${coldRecord.assistantMessageId}"]`),true);
  assert.equal(await click(port,`[data-action="show-assistant-answer-information"][data-message-id="${coldRecord.assistantMessageId}"]`),true);
  await until(()=>evaluate(port,'document.querySelector(".answer-information-sources")?.textContent.includes("已审阅合成标题")'),'已关闭检索后的历史回答来源');
  if(visibleContextSettingsMode){
   assert.equal((await invoke(port,'read_desktop_app_settings',{},'WORK')).product.historyLibraryEnabled,false);
   assert.equal(await click(port,'[data-action="close-dialog"]'),true);
   assert.equal(await click(port,'[data-action="show-settings"]'),true);
   await until(()=>evaluate(port,'Boolean(document.querySelector("[data-action=open-settings-page][data-page=personalization]"))'),'冷启个性化入口');
   assert.equal(await click(port,'[data-action="open-settings-page"][data-page="personalization"]'),true);
   await until(()=>evaluate(port,'document.querySelector("[data-action=toggle-product-setting][data-key=historyLibraryEnabled]")?.getAttribute("aria-checked")==="false"'),'冷启资料关闭显示');
  }
 }
 if(process.argv.includes('--cost-status'))await verifyVisibleUsageOutcomes({port,evaluate,click,until,section:'history',modelId:'fixture-history-knowledge',labels:['已完成']});
 assert.equal(requests.filter(x=>x.model==='fixture-history-knowledge').length,1);
 const deleteAcceptedMode=process.argv.includes('--delete-accepted');
 if(deleteAcceptedMode){
  assert.equal(contextUseMode,false,'候选删除验收使用独立默认路径');
  await until(()=>evaluate(port,'globalThis.__nanfengDesktopWorkState?.runtimeInfo?.dataAreasReady === true'),'删除冷启双区');
  await until(()=>evaluate(port,'Boolean(document.querySelector("[data-action=show-work]"))'),'删除前工作入口');assert.equal(await click(port,'[data-action="show-work"]'),true);
  await until(()=>evaluate(port,'Boolean(document.querySelector("[data-action=show-knowledge]"))'),'删除前知识入口');assert.equal(await click(port,'[data-action="show-knowledge"]'),true);
  const deleteSelector=`[data-action="delete-history-knowledge"][data-id="${candidate.candidateId}"]`;
  await until(()=>evaluate(port,`Boolean(document.querySelector(${JSON.stringify(deleteSelector)}))`),'已采纳候选删除入口');assert.equal(await click(port,deleteSelector),true);
  await until(async()=>!(await invoke(port,'read_desktop_history_knowledge',{},'WORK')).candidates.some(x=>x.candidateId===candidate.candidateId),'候选记录已从列表删除');
  assert.deepEqual((await invoke(port,'read_desktop_workspace',{workspaceId},'WORK')).exchange.knowledge.find(x=>x.id===knowledge.id),cold,'删除候选不可修改已采纳Knowledge');
  stop();port=await start(true);await until(()=>evaluate(port,'Boolean(window.__TAURI__?.core?.invoke)'),'候选删除后冷启IPC');
  assert.ok(!(await invoke(port,'read_desktop_history_knowledge',{},'WORK')).candidates.some(x=>x.candidateId===candidate.candidateId));
  assert.deepEqual((await invoke(port,'read_desktop_workspace',{workspaceId},'WORK')).exchange.knowledge.find(x=>x.id===knowledge.id),cold,'删除后冷启Knowledge保留');assert.equal(requests.filter(x=>x.model==='fixture-history-knowledge').length,1);
 }
 result={candidateSha256:receipt.sha256,root,workspaceId,candidateId:candidate.candidateId,knowledgeId:knowledge.id,reviewDraftRetained:true,contextUse:contextUseMode,visibleContextSettings:visibleContextSettingsMode,visibleContextChat:visibleContextChatMode,sourceReadback:contextUseMode,coldReadback:true,deletedAcceptedCandidate:deleteAcceptedMode,providerRequests:requests.length,pids};
 }
}
 }
} finally {if(releaseHistory)releaseHistory();stop(); provider.closeAllConnections();await new Promise(done=>provider.close(done));}
console.log(JSON.stringify(result));





