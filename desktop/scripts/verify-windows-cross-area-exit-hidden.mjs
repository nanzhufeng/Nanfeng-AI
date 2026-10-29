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
const inputPath=inputIndex<0 ? join(desktopRoot,'../docs/review/20261017-windows-cross-area-exit/source-input-manifest-release-v1.json') : resolve(process.argv[inputIndex+1]);
assert.ok(inputPath.startsWith(resolve(desktopRoot,'../docs/review')+sep));
const verifyCallWindow=process.argv.includes('--verify-call-window');
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


const noReopen=process.argv.includes('--no-reopen');const requests=[],serverErrors=[];
const prefixes={CHAT:'CHAT_PERSISTED_PREFIX',WORK:'WORK_PERSISTED_PREFIX'};
const tails={CHAT:'CHAT_COMPLETED_TAIL',WORK:'WORK_COMPLETED_TAIL'};
const release={};const gates={};for(const area of ['CHAT','WORK'])gates[area]=new Promise(done=>release[area]=done);
const provider=http.createServer(async(req,res)=>{try{
 let raw='';for await(const chunk of req)raw+=chunk.toString('utf8');const body=JSON.parse(raw);
 assert.equal(req.headers.authorization,'Bearer local-acceptance-mock-only');assert.equal(body.model,'openai/gpt-6.1-sol');
 const text=body.messages.filter(m=>m.role==='user').at(-1)?.content;assert.equal(typeof text,'string');const tag=text.includes('CROSS_AREA_DENIED_CHAT')?'DENIED':text.includes('CROSS_AREA_NEW_CHAT')?'NEW':text.includes('CROSS_AREA_WORK')?'WORK':text.includes('CROSS_AREA_CHAT')?'CHAT':null;assert.ok(tag,'Exact latest synthetic user request tag required');
 assert.ok(!requests.some(r=>r.tag===tag),'No implicit retry');requests.push({tag,model:body.model});assert.ok(requests.length<=3);if(tag==='DENIED')throw new Error('Accounting refusal leaked a Provider request');
 res.writeHead(200,{'Content-Type':'text/event-stream; charset=utf-8'});
 const event=value=>res.write('data: '+JSON.stringify(value)+'\n\n');
 if(tag!=='NEW'){event({model:body.model,choices:[{delta:{content:prefixes[tag]}}]});res.flushHeaders();await gates[tag];event({model:body.model,choices:[{delta:{content:tails[tag]}}]});}
 else event({model:body.model,choices:[{delta:{content:'NEW_CHAT_COMPLETED'}}]});
 res.end('data: '+JSON.stringify({model:body.model,choices:[{delta:{},finish_reason:'stop'}],usage:{prompt_tokens:8,completion_tokens:5,cost:0.000004}}));
}catch(error){serverErrors.push(String(error));res.destroy(error);}});
await new Promise(done=>provider.listen(0,'127.0.0.1',done));const providerPort=provider.address().port;
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
    '-ExecutablePath',source,'-StartupMode', 'normal', '-DebugPort', String(port), '-OrdinaryChatTestEndpoint', `http://127.0.0.1:${providerPort}`];
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







function watchExit() {
 let readyResolve,readyReject,doneResolve,doneReject;const ready=new Promise((r,j)=>{readyResolve=r;readyReject=j;});const done=new Promise((r,j)=>{doneResolve=r;doneReject=j;});ready.catch(()=>{});done.catch(()=>{});
 const child=spawn('pwsh',['-NoProfile','-File',join(desktopRoot,'scripts/wait-windows-owned-process-exit.ps1'),'-ProcessId',String(launch.pid),'-Executable',runExecutable,'-Sha256',receipt.sha256,'-StartedAt',launch.processStartedAt],{windowsHide:true,stdio:['ignore','pipe','pipe']});
 let buffer='',errors='',code=null;const timer=setTimeout(()=>{child.kill();readyReject(new Error('exit observer timeout'));doneReject(new Error('exit observer timeout'));},70000);
 child.stdout.on('data',data=>{buffer+=data.toString('utf8');const lines=buffer.split(/\r?\n/);buffer=lines.pop();for(const line of lines){if(!line.trim())continue;try{const value=JSON.parse(line);if(value.ready)readyResolve();if(value.exitCode!==undefined)code=value.exitCode;}catch{}}});
 child.stderr.on('data',data=>{errors=(errors+data.toString('utf8')).slice(-2048);});
 child.on('error',error=>{clearTimeout(timer);readyReject(error);doneReject(error);});child.on('exit',status=>{clearTimeout(timer);if(status===0&&code!==null)doneResolve(code);else{const error=new Error(`exit observer failed ${status}: ${errors}`);readyReject(error);doneReject(error);}});
 return {ready,done};
}
async function reopenHiddenInstance() {
 const env={};for(const name of ['SystemRoot','WINDIR','PATH','USERPROFILE','LOCALAPPDATA','APPDATA'])if(process.env[name])env[name]=process.env[name];
 Object.assign(env,{NANFENG_AI_WINDOWS_ACCEPTANCE_ROOT:root,WEBVIEW2_USER_DATA_FOLDER:launch.webviewProfile,NANFENG_AI_WINDOWS_ORDINARY_CHAT_TEST_ENDPOINT:`http://127.0.0.1:${providerPort}`,TEMP:rootParent,TMP:rootParent});
 const child=spawn(runExecutable,['--windows-isolated-normal-startup'],{env,windowsHide:true,stdio:['ignore','pipe','pipe']});let errorText='';child.stderr.on('data',data=>{errorText=(errorText+data.toString('utf8')).slice(-2048);});child.stdout.resume();
 const code=await new Promise((done,reject)=>{const timer=setTimeout(()=>reject(new Error('second instance did not exit')),15000);child.on('exit',code=>{clearTimeout(timer);done(code);});child.on('error',error=>{clearTimeout(timer);reject(error);});});
 assert.equal(code,0,`second instance: ${errorText}`);return {pid:child.pid,exitCode:code};
}
const readStages=async()=>JSON.parse(await readFile(join(root,'generation-continuity-v1.json'),'utf8')).map(e=>e.stage);
const authorization=p=>({approvedAtMs:Date.now(),disclosureVersion:'normal-chat-egress-v2',clientSubmissionId:randomUUID(),draftFingerprint:p.draftFingerprint,recipientChoiceId:p.recipientChoiceId,recipientProviderId:p.recipientProviderId,recipientModelId:p.recipientModelId,retrievalRecipient:p.retrievalRecipient});
const textOf=m=>(m.blocks||[]).filter(b=>b.kind==='TEXT').map(b=>b.text||'').join('');

function accountingGate(mode){
 const py='C:/Users/Administrator/.cache/codex-runtimes/codex-primary-runtime/dependencies/python/python.exe';
 const code='import pathlib,sqlite3,json,sys\nr=pathlib.Path(sys.argv[1]);assert json.loads((r/".nanfeng-windows-acceptance.json").read_text(encoding="utf-8"))["identifier"]=="com.nanzhufeng.ai.desktop.windowsacceptance"\nc=sqlite3.connect(r/"usage-ledger/usage-ledger-v1.sqlite3");mode=sys.argv[2]\nif mode=="on":c.execute("CREATE TRIGGER qa_deny_call_window BEFORE INSERT ON usage_ledger_entries BEGIN SELECT RAISE(ABORT, \'synthetic call accounting denial\'); END");c.commit()\nif mode=="off":c.execute("DROP TRIGGER IF EXISTS qa_deny_call_window");c.commit()\ncount=c.execute("SELECT COUNT(*) FROM usage_ledger_entries").fetchone()[0];c.close();c=sqlite3.connect((r/"workspace.sqlite3").as_uri()+"?mode=ro",uri=True);row=c.execute("SELECT state,safe_error_code FROM desktop_ordinary_chat_attempts ORDER BY created_at_ms DESC LIMIT 1").fetchone();c.close();print(json.dumps({"entries":count,"attempt":row}))';
 return JSON.parse(execFileSync(py,['-X','utf8','-c',code,root,mode],{encoding:'utf8',windowsHide:true,timeout:15000}));
}
async function configureWindow(area,window){
 const catalog=await invoke(port,area,'read_desktop_p6g_catalog');
 await invoke(port,area,'upsert_desktop_p6g_catalog_candidate',{args:{expectedRevision:catalog.revision,catalogVersion:'window-fixture-'+window,policyVersion:2,candidate:{providerFamily:'OPENAI',providerId:'OPENROUTER',modelId:'GPT_6_1_SOL',displayName:'Sol window fixture',tiers:['BALANCED'],capabilities:['TEXT'],available:true,knownCostMicros:null,latencyRank:1,contextWindowTokens:window,maxOutputTokens:1024}}});
}
const result={candidateSha256:receipt.sha256,noReopen,realService:false,backgroundOnly:true};let port=0,observer=null;
const contexts={};
async function workspace(area){return (await invoke(port,area,'read_desktop_workspace',{workspaceId:contexts[area].ws})).exchange.conversations.find(c=>c.id===contexts[area].id);}
async function submit(area,text,background=true){
 const ctx=contexts[area];const live=await workspace(area);
 const preview=await invoke(port,area,'preview_desktop_ordinary_chat_egress',{args:{workspaceId:ctx.ws,conversationId:ctx.id,text,attachmentIds:[],modelId:null,webSearchOverride:null}});
 const args={args:{workspaceId:ctx.ws,conversationId:ctx.id,expectedRevision:live.revision,text,attachmentIds:[],modelId:null,egressAuthorization:authorization(preview)}};
 if(!background)return invoke(port,area,'submit_desktop_ordinary_chat',args);
 await evaluate(port,"(()=>{window.__nfaiCrossResults??={};window.__nfaiCrossErrors??={};window.__TAURI__.core.invoke('submit_desktop_ordinary_chat',"+JSON.stringify(args)+",{headers:{'x-nanfeng-data-area':"+JSON.stringify(area)+"}}).then(v=>window.__nfaiCrossResults["+JSON.stringify(area)+"]=v,e=>window.__nfaiCrossErrors["+JSON.stringify(area)+"]=String(e));return true})()");
}
try{
 port=await start();result.root=root;await ready(port);result.runtimeInfo=await invoke(port,'CHAT','read_desktop_runtime_info');assert.equal(result.runtimeInfo.buildProfile,'release');assert.equal(result.runtimeInfo.sourceInputSha256,sourceInputSha256);
 const setting=(await invoke(port,'CHAT','read_desktop_model_service_settings')).find(p=>p.providerId==='OPENROUTER');
 await invoke(port,'CHAT','save_desktop_model_service_settings',{args:{providerId:'OPENROUTER',enabled:true,presetId:'GPT_6_1_SOL',expectedRevision:setting.revision,apiKey:'nfai-synthetic-cross-area-exit'}});
 for(const area of ['CHAT','WORK']){
  const ws=(await invoke(port,area,'ensure_desktop_local_workspace')).summary.id;
  const made=await invoke(port,area,'mutate_desktop_domain',{args:{workspaceId:ws,intentId:randomUUID(),entity:'conversation',action:'create',fields:{title:area+' synthetic cross-area exit',firstMessage:'synthetic local seed',projectId:null}}});
  const conv=(await invoke(port,area,'read_desktop_workspace',{workspaceId:ws})).exchange.conversations.find(c=>c.id===made.objectId);assert.ok(conv);contexts[area]={ws,id:conv.id};
  if(verifyCallWindow){await configureWindow(area,8192);(result.expectedContextWindows??={})[area]=8192;}
  const selection=await invoke(port,area,'read_desktop_p6g_selection',{workspaceId:ws,conversationId:conv.id});
  await invoke(port,area,'set_desktop_p6g_conversation_override',{args:{workspaceId:ws,conversationId:conv.id,expectedRevision:selection.conversationOverride.revision,modelId:'GPT_6_1_SOL'}});
  const current=await invoke(port,area,'read_desktop_p6g_selection',{workspaceId:ws,conversationId:conv.id});
  await invoke(port,area,'set_desktop_conversation_preferences',{args:{workspaceId:ws,conversationId:conv.id,expectedRevision:current.conversationOverride.revision,toneOverride:null,webSearchOverride:false}});
  if(verifyCallWindow&&area==='CHAT'){
   await invoke(port,'CHAT','read_desktop_usage_ledger');assert.equal(accountingGate('on').entries,0);await submit(area,'CROSS_AREA_DENIED_CHAT');
   const denied=await until(()=>{const value=accountingGate('inspect');return value.attempt?.[0]==='FAILED'&&value;},'call accounting refusal completes without egress');
   assert.equal(denied.attempt[1],'LOCAL_CALL_ACCOUNTING');assert.equal(denied.entries,0);assert.equal(requests.length,0);result.accountingDeniedBeforeProvider=denied;
   await evaluate(port,"document.querySelector('[data-action=\"show-chat\"][aria-label=\"返回应用\"]')?.click()");
   await until(()=>evaluate(port,'(()=>{const button=document.querySelector('+JSON.stringify('[data-action="select-chat"][data-id="'+contexts.CHAT.id+'"]')+');if(!button)return false;button.click();return true})()'),'actual failed conversation navigation');
   result.accountingFailureUiText=await until(()=>evaluate(port,'[...document.querySelectorAll(".chat-runtime-state.failed small")].map(n=>n.textContent).find(text=>text.includes("本机调用记录保存失败"))'),'actual Chinese storage refusal');
   assert.ok(result.accountingFailureUiText.includes('请检查存储空间后手动重试'));assert.equal(requests.length,0);accountingGate('off');
  }
  await submit(area,'CROSS_AREA_'+area);
 }
 await until(async()=>requests.length===2&&(await workspace('CHAT')).messages.some(m=>textOf(m)===prefixes.CHAT)&&(await workspace('WORK')).messages.some(m=>textOf(m)===prefixes.WORK),'both areas persist actual stream prefixes');
 result.contexts=contexts;result.bothAreasRunning=true;if(verifyCallWindow){await configureWindow('CHAT',32768);result.catalogChangedWhileInFlight=true;}
 const closed=JSON.parse(ps(['-File',join(desktopRoot,'scripts/close-windows-hidden-acceptance.ps1'),'-ProcessId',String(launch.pid),'-Executable',runExecutable,'-Sha256',receipt.sha256,'-StartedAt',launch.processStartedAt]));assert.equal(closed.closePosted,true);assert.equal(closed.visibleWindows,0);
 await until(async()=>(await readStages()).includes('EXIT_DRAIN_BEGIN'),'actual close drain begins');observer=watchExit();await observer.ready;
 release.CHAT();await until(()=>evaluate(port,"window.__nfaiCrossResults?.CHAT?.state==='COMPLETED'"),'CHAT completes while WORK remains pending');
 await new Promise(done=>setTimeout(done,500));assert.equal(await evaluate(port,'Boolean(window.__TAURI__?.core?.invoke)'),true);
 assert.equal(await evaluate(port,'Boolean(window.__nfaiCrossResults?.WORK)'),false);assert.ok((await workspace('WORK')).messages.some(m=>textOf(m)===prefixes.WORK));result.chatFinishedWorkStillActive=true;
 if(!noReopen){result.reopen=await reopenHiddenInstance();await until(async()=>(await readStages()).includes('EXIT_DRAIN_REVOKED'),'single-instance entry revokes cross-area drain');}
 release.WORK();
 if(noReopen){result.exitCode=await observer.done;assert.equal(result.exitCode,0);pids.push(launch.pid);launch=null;}
 else{
  await until(()=>evaluate(port,"window.__nfaiCrossResults?.WORK?.state==='COMPLETED'"),'WORK completes after reopen');
  await new Promise(done=>setTimeout(done,500));assert.equal(await evaluate(port,'Boolean(window.__TAURI__?.core?.invoke)'),true);
  const fresh=await submit('CHAT','CROSS_AREA_NEW_CHAT',false);assert.equal(fresh.state,'COMPLETED');result.newChatCompleted=true;
  result.close=await normalClose();result.exitCode=await observer.done;assert.equal(result.exitCode,0);
 }
 port=await start(true);await ready(port);result.cold={};
 for(const area of ['CHAT','WORK']){const conv=await workspace(area);assert.ok(conv.messages.some(m=>textOf(m)===prefixes[area]+tails[area]),area+' cold full text');result.cold[area]=conv.messages.filter(m=>m.role==='assistant').map(m=>({attemptId:m.attemptId,text:textOf(m),delivery:m.delivery,usage:m.usage,currencyCode:m.currencyCode,chargeMicros:m.chargeMicros}));}
 if(!noReopen)assert.ok(result.cold.CHAT.some(m=>m.text==='NEW_CHAT_COMPLETED'));
 assert.equal(requests.length,noReopen?2:3);
 if(verifyCallWindow){
  const py='C:/Users/Administrator/.cache/codex-runtimes/codex-primary-runtime/dependencies/python/python.exe';
  const code='import pathlib,json,sqlite3,sys\nr=pathlib.Path(sys.argv[1]);assert json.loads((r/".nanfeng-windows-acceptance.json").read_text(encoding="utf-8"))["identifier"]=="com.nanzhufeng.ai.desktop.windowsacceptance"\nout=[]\nfor area,rel in [("CHAT","usage-ledger/usage-ledger-v1.sqlite3"),("WORK","work-area/usage-ledger/usage-ledger-v1.sqlite3")]:\n c=sqlite3.connect((r/rel).as_uri()+"?mode=ro",uri=True);out.extend({"area":area,"executionId":eid,"metadata":json.loads(meta)} for eid,meta in c.execute("SELECT e.execution_id,m.metadata_json FROM usage_ledger_entries e JOIN usage_ledger_call_metadata m ON e.entry_id=m.entry_id WHERE e.kind=\'FINAL_MEASURED\'"));c.close()\nprint(json.dumps(out))';
  const rows=JSON.parse(execFileSync(py,['-X','utf8','-c',code,root],{encoding:'utf8',windowsHide:true,timeout:15000}));assert.equal(rows.length,noReopen?2:3);
  for(const row of rows){assert.equal(row.metadata.role,'ANSWER');const message=result.cold[row.area].find(m=>m.attemptId===row.executionId);assert.ok(message);const expected=message.text==='NEW_CHAT_COMPLETED'?32768:8192;assert.equal(row.metadata.contextWindowTokens,expected);}
  result.coldCallWindows=rows;
 }
 result.coldClose=await normalClose();assert.deepEqual(serverErrors,[]);result.pass=true;
}catch(error){result.pass=false;result.error=error.stack||String(error);process.exitCode=1;}
finally{if(verifyCallWindow&&root)try{accountingGate('off');}catch(error){result.fixtureGateCleanupError=String(error);}release.CHAT();release.WORK();if(launch){try{result.failureClose=await normalClose();}catch(error){result.forcedCleanup=true;try{stop();}catch(error){result.cleanupError=String(error);}}}if(observer)await observer.done.catch(()=>{});provider.closeAllConnections();await new Promise(done=>provider.close(done));result.requests=requests;result.serverErrors=serverErrors;result.pids=pids;if(root)await writeFile(join(root,'cross-area-exit-native-evidence.json'),JSON.stringify(result,null,2),{flag:'wx'});}
console.log(JSON.stringify({root,pass:result.pass,error:result.error,requests:requests.length,pids}));
