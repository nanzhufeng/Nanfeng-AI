import assert from 'node:assert/strict';
import { createHash, randomUUID } from 'node:crypto';
import { execFileSync, spawn } from 'node:child_process';
import { readFile, writeFile } from 'node:fs/promises';
import http from 'node:http';
import { dirname, join, resolve, sep } from 'node:path';
import { fileURLToPath } from 'node:url';

if (process.platform !== 'win32') throw new Error('仅在 Windows 隔离候选上验收。');
const desktopRoot = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const rootParent = resolve(process.argv[2] || 'D:\\CodexTemp');
assert.ok(process.execArgv.includes('--max-old-space-size=2048'));
const screenshotMode = process.argv.includes('--screenshot');
const receipt = JSON.parse(await readFile(join(desktopRoot, 'output/windows-acceptance/current.json'), 'utf8'));
assert.equal(receipt.identifier, 'com.nanzhufeng.ai.desktop.windowsacceptance');
const source = resolve(desktopRoot, 'output/windows-acceptance', receipt.sha256, 'NanfengAI-Windows-Acceptance.exe');
assert.equal(resolve(receipt.executable), source);
assert.equal(createHash('sha256').update(await readFile(source)).digest('hex').toUpperCase(), receipt.sha256);
const runExecutable = resolve(desktopRoot, 'output/windows-acceptance-binaries', receipt.sha256, 'nanfeng-ai-desktop-spike.exe');
const ps = (args) => execFileSync('pwsh', ['-NoProfile', ...args], { encoding: 'utf8', timeout: 60000 }).trim();
const launchPowerShell = (args) => new Promise((resolveLaunch, reject) => {
  const child = spawn('pwsh', ['-NoProfile', ...args], { stdio: ['ignore', 'pipe', 'pipe'] });
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
const requests=[];const serverErrors=[];let scenario='normal';
const provider=http.createServer(async(req,res)=>{
 try {
  let raw='';for await(const chunk of req)raw+=chunk.toString('utf8');const body=JSON.parse(raw);
  assert.equal(req.headers.authorization==='Bearer local-acceptance-mock-only',true);
  const retrieval=body.model==='openai/gpt-6-luna' && Array.isArray(body.tools);
  requests.push({model:body.model,role:retrieval?'RETRIEVAL':'ANSWER',idempotencyKey:req.headers['idempotency-key'],grounded:JSON.stringify(body.messages||[]).includes('<retrieved-evidence>')});
  if(!retrieval && scenario==='answer-http500') {res.writeHead(500,{'Content-Type':'application/json'});res.end(JSON.stringify({error:{message:'synthetic explicit failure'}}));return;}
  if(retrieval && scenario==='primary-changed') await invoke(port,'CHAT','set_desktop_model_service_enabled',{args:{providerId:'RELAYCORE_CLAUDE',enabled:false}});
  res.writeHead(200,{'Content-Type':'text/event-stream; charset=utf-8'});const event=value=>res.write(`data: ${JSON.stringify(value)}\n\n`);
  if(retrieval) {
   assert.equal(body.tool_choice,'required');assert.equal(body.provider.allow_fallbacks,false);
   event({model:body.model,choices:[{delta:{content:'合成检索资料'}}]});
   res.end(`data: ${JSON.stringify({model:body.model,choices:[{delta:{annotations:scenario==='no-sources'?[]:[{type:'url_citation',url_citation:{url:'https://example.test/companion-proof',title:'Synthetic evidence'}}]},finish_reason:'stop'}],usage:{prompt_tokens:5,completion_tokens:2,cost:0.000004}})}`);
  }else if(body.output_config) {
   assert.equal(req.headers['x-api-key']==='local-acceptance-mock-only',true);assert.equal(req.headers['anthropic-version'],'2023-06-01');
   event({type:'message_start',message:{model:body.model,usage:{input_tokens:5,cache_read_input_tokens:1}}});
   event({type:'content_block_delta',delta:{type:'text_delta',text:'合成原模型回答'}});
   event({type:'message_delta',delta:{stop_reason:'end_turn'},usage:{output_tokens:2}});res.end('data: {"type":"message_stop"}');
  }else {
   if(body.model.startsWith('gemini-'))assert.equal(body.max_tokens,undefined);
   event({model:body.model,choices:[{delta:{content:'合成原模型回答'}}]});
   res.end(`data: ${JSON.stringify({model:body.model,choices:[{delta:{},finish_reason:'stop'}],usage:{prompt_tokens:5,completion_tokens:2,cost:0.000004}})}`);
  }
 }catch(error){serverErrors.push(String(error));res.destroy(error);}
});
await new Promise(resolveReady => provider.listen(0, '127.0.0.1', resolveReady));
const providerPort = provider.address().port;
let launch = null;
let root = '';
const pids = [];
const stop = () => {
  if (!launch) return;
  const pid = Number(launch.pid);
  const literal = value => `'${String(value).replaceAll("'", "''")}'`;
  const script = '$expected=' + literal(runExecutable) + '; $hash=' + literal(receipt.sha256) + '; '
    + '$p=Get-CimInstance Win32_Process -Filter "ProcessId=' + pid + '"; '
    + 'if ($p) { if ([IO.Path]::GetFullPath([string]$p.ExecutablePath) -ne $expected '
    + '-or (Get-FileHash -LiteralPath $expected -Algorithm SHA256).Hash -ne $hash) { throw "PID identity mismatch" }; '
    + 'Stop-Process -Id ' + pid + ' -Force -ErrorAction Stop }';
  ps(['-Command', script]);
  pids.push(pid);
  launch = null;
};
async function start(reopen = false) {
  const port = await reservePort();
  const args = ['-File', join(desktopRoot, 'scripts/start-windows-acceptance.ps1'), '-RootParent', rootParent,
    '-StartupMode', 'normal', '-Visible', '-DebugPort', String(port), '-OrdinaryChatTestEndpoint', `http://127.0.0.1:${providerPort}`];
  if (reopen) args.push('-Root', root);
  launch = await launchPowerShell(args);
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
  while (Date.now() < deadline) {
    try { if (await evaluate(port, 'Boolean(window.__TAURI__?.core?.invoke)')) return; } catch { /* Startup is still active. */ }
    await new Promise(resolveWait => setTimeout(resolveWait, 250));
  }
  throw new Error('原生 IPC 未就绪');
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
  const info=JSON.parse(ps(['-Command',`$p=Get-CimInstance Win32_Process -Filter "ProcessId=${pid}";if(!$p -or [IO.Path]::GetFullPath([string]$p.ExecutablePath) -ne ${quote(runExecutable)} -or (Get-FileHash -LiteralPath ${quote(runExecutable)}).Hash -ne ${quote(receipt.sha256)}){throw 'PID identity mismatch'};$n=Get-Process -Id ${pid};@{title=$n.MainWindowTitle;responding=$n.Responding;accepted=$n.CloseMainWindow()}|ConvertTo-Json -Compress`]));
  assert.ok(info.title && info.responding && info.accepted);
  await until(()=>ps(['-Command',`@(Get-CimInstance Win32_Process -Filter "ProcessId=${pid}").Count`])==='0','自然关闭退出');
  await until(()=>ps(['-Command',`@(Get-CimInstance Win32_Process -Filter "Name='msedgewebview2.exe'" | Where-Object {$_.CommandLine -and $_.CommandLine.Contains(${quote(launch.webviewProfile)})}).Count`])==='0','本根WebView退出');
  pids.push(pid);launch=null;return info;
}





const result={candidateSha256:receipt.sha256,realService:false,cases:[],requests,serverErrors};let port=0;let stage='start';
const current=async(ws,id)=>(await invoke(port,'CHAT','read_desktop_workspace',{workspaceId:ws})).exchange.conversations.find(item=>item.id===id);
const faultSql=sql=>execFileSync('python',['-c','import sqlite3,sys; c=sqlite3.connect(sys.argv[1]); c.executescript(sys.argv[2]); c.close()',join(root,'usage-ledger','usage-ledger-v1.sqlite3'),sql],{encoding:'utf8',timeout:10000});
try {
 port=await start();result.root=root;await ready(port);
 await invoke(port,'CHAT','read_desktop_usage_ledger');result.closeBeforeOwnedUpgrade=await normalClose();
 const upgrade=JSON.parse(execFileSync('python',['-c',`import sqlite3,sys,json,hashlib
from pathlib import Path
root=Path(sys.argv[1]); assert root.parent==Path('D:/CodexTemp') and (root/'.nanfeng-windows-acceptance.json').is_file()
source=Path('D:/CodexTemp/nanfeng-ai-windows-acceptance.909e3423-9d95-40ad-bdca-9c1e297cca06/usage-ledger/usage-ledger-v1.sqlite3')
old=sqlite3.connect('file:'+str(source)+'?mode=ro',uri=True); assert old.execute('PRAGMA user_version').fetchone()[0]==2
current=sqlite3.connect(str(root/'usage-ledger/usage-ledger-v1.sqlite3')); saved=sqlite3.connect(str(root/'before-ledger-upgrade.sqlite3'));current.backup(saved);saved.close();old.backup(current);current.close()
rows={t:old.execute('SELECT * FROM '+t+' ORDER BY entry_id').fetchall() for t in ['usage_ledger_entries','usage_ledger_call_metadata']}
print(json.dumps({'version':2,'digest':hashlib.sha256(json.dumps(rows,sort_keys=True).encode()).hexdigest(),'counts':{t:len(r) for t,r in rows.items()}}))`,root],{encoding:'utf8',timeout:10000}));
 port=await start(true);await ready(port);await invoke(port,'CHAT','read_desktop_usage_ledger');
 const upgraded=JSON.parse(execFileSync('python',['-c',`import sqlite3,sys,json,hashlib
c=sqlite3.connect('file:'+sys.argv[1]+'?mode=ro',uri=True)
rows={t:c.execute('SELECT * FROM '+t+' ORDER BY entry_id').fetchall() for t in ['usage_ledger_entries','usage_ledger_call_metadata']}
print(json.dumps({'version':c.execute('PRAGMA user_version').fetchone()[0],'digest':hashlib.sha256(json.dumps(rows,sort_keys=True).encode()).hexdigest()}))`,join(root,'usage-ledger','usage-ledger-v1.sqlite3')],{encoding:'utf8',timeout:10000}));
 assert.equal(upgraded.version,3);assert.equal(upgraded.digest,upgrade.digest);assert.equal(requests.length,0);result.ownedSchema2Upgrade={before:upgrade,after:upgraded,realUserData:false};
 const ws=(await invoke(port,'CHAT','ensure_desktop_local_workspace')).summary.id;result.workspaceId=ws;
 const settings=await invoke(port,'CHAT','read_desktop_model_service_settings');
 for(const setting of settings)await invoke(port,'CHAT','save_desktop_model_service_settings',{args:{providerId:setting.providerId,enabled:true,presetId:setting.presetId,expectedRevision:setting.revision,apiKey:`nfai-synthetic-companion-${setting.providerId}`}});
 for(const name of ['final-retrieval-denied']) {
  stage=name;scenario='normal';
  const made=await invoke(port,'CHAT','mutate_desktop_domain',{args:{intentId:`negative-${randomUUID()}`,workspaceId:ws,entity:'conversation',action:'create',objectId:null,expectedRevision:null,fields:{title:`合成失败 ${name}`,projectId:null,firstMessage:'查询合成公开信息'}}});
  const id=made.objectId;let conv=await current(ws,id);
  const selection=await invoke(port,'CHAT','read_desktop_p6g_selection',{workspaceId:ws,conversationId:id});
  await invoke(port,'CHAT','set_desktop_conversation_preferences',{args:{workspaceId:ws,conversationId:id,expectedRevision:selection.conversationOverride.revision,toneOverride:null,webSearchOverride:false}});
  await invoke(port,'CHAT','generate_desktop_ordinary_chat_for_saved_user',{args:{workspaceId:ws,conversationId:id,userMessageId:conv.currentLeafId,expectedRevision:conv.revision,egressAuthorization:{approvedAtMs:Date.now(),disclosureVersion:'normal-chat-egress-v1',clientSubmissionId:randomUUID()}}});
  conv=await current(ws,id);const prefs=await invoke(port,'CHAT','read_desktop_p6g_selection',{workspaceId:ws,conversationId:id});
  await invoke(port,'CHAT','set_desktop_conversation_preferences',{args:{workspaceId:ws,conversationId:id,expectedRevision:prefs.conversationOverride.revision,toneOverride:null,webSearchOverride:true}});
  const preview=await invoke(port,'CHAT','preview_desktop_existing_ordinary_chat_egress',{args:{workspaceId:ws,conversationId:id,sourceMessageId:conv.currentLeafId,expectedRevision:conv.revision,operation:'changeModel',targetPresetId:'RELAYCORE_OPUS_5_5'}});
  const auth={approvedAtMs:Date.now(),disclosureVersion:'normal-chat-egress-v2',clientSubmissionId:randomUUID(),draftFingerprint:preview.draftFingerprint,recipientChoiceId:preview.recipientChoiceId,recipientProviderId:preview.recipientProviderId,recipientModelId:preview.recipientModelId,retrievalRecipient:preview.retrievalRecipient};
  if(name.startsWith('pending-') || name==='final-retrieval-denied') {
   const role=name==='pending-retrieval-denied'||name==='final-retrieval-denied'?'retrieval':'answer';const kind=name==='final-retrieval-denied'?'FINAL_MEASURED':'STREAM_PENDING';
   faultSql(`CREATE TRIGGER synthetic_reject_pending BEFORE INSERT ON usage_ledger_entries WHEN NEW.kind='${kind}' AND NEW.entry_id LIKE '%-${role}-%' BEGIN SELECT RAISE(ABORT,'synthetic accounting gate'); END;`);
  }
  scenario=name;const before=requests.length;
  const attempt=await invoke(port,'CHAT','regenerate_desktop_ordinary_chat',{args:{workspaceId:ws,conversationId:id,assistantMessageId:conv.currentLeafId,targetPresetId:'RELAYCORE_OPUS_5_5',expectedRevision:conv.revision,egressAuthorization:auth}});
  if(name==='final-retrieval-denied') {
   const staged=JSON.parse(execFileSync('python',['-c','import sqlite3,sys,json; c=sqlite3.connect(sys.argv[1]); rows=c.execute("SELECT entry_json FROM usage_ledger_call_recovery").fetchall(); print(json.dumps([json.loads(r[0]) for r in rows]))',join(root,'usage-ledger','usage-ledger-v1.sqlite3')],{encoding:'utf8',timeout:10000}));
   assert.equal(staged.length,1);assert.equal(staged[0].charge_micros,4);assert.equal(staged[0].currency_code,'USD');result.receivedMeasurementSurvived=staged.map(row=>({entryId:row.entry_id,micros:row.charge_micros,currency:row.currency_code}));
   const backupPath=join(root,'synthetic-recovery.nfai-backup');
   result.backupWithPendingReceipt=await invoke(port,'CHAT','export_desktop_local_backup',{selectedPath:backupPath});
   result.backupPreflight=await invoke(port,'CHAT','preflight_desktop_local_backup',{selectedPath:backupPath});
   const count=requests.length;result.closeWhileFaultBlocked=await normalClose();port=await start(true);await ready(port);
   assert.equal(requests.length,count);const blocked=(await invoke(port,'CHAT','read_desktop_usage_ledger')).records.filter(row=>row.executionId===attempt.attemptId);
   assert.equal(blocked.length,1);assert.equal(blocked[0].kind,'STREAM_PENDING');assert.equal(blocked[0].chargeMicros,null);assert.equal(requests.length,count);result.faultSurvivedColdRead=true;
   faultSql('DROP TRIGGER synthetic_reject_pending;');
  }else if(name.startsWith('pending-'))faultSql('DROP TRIGGER synthetic_reject_pending;');
  if(name==='primary-changed')await invoke(port,'CHAT','set_desktop_model_service_enabled',{args:{providerId:'RELAYCORE_CLAUDE',enabled:true}});
  conv=await current(ws,id);const leaf=conv.messages.find(item=>item.id===conv.currentLeafId);
  const rows=(await invoke(port,'CHAT','read_desktop_usage_ledger')).records.filter(item=>item.executionId===attempt.attemptId);
  const expectedRequests=name==='answer-http500'?2:name==='pending-retrieval-denied'?0:1;
  const info={name,attemptId:attempt.attemptId,conversationId:id,state:attempt.state,safeErrorCode:attempt.safeErrorCode,delivery:leaf.delivery,requests:requests.length-before,roles:rows.map(row=>row.callMetadata?.role),charges:rows.map(row=>({currency:row.currencyCode,micros:row.chargeMicros}))};result.cases.push(info);
  assert.equal(info.requests,expectedRequests);assert.notEqual(leaf.delivery,'COMPLETE');assert.ok(attempt.safeErrorCode);
  assert.equal(rows.filter(row=>row.callMetadata?.role==='RETRIEVAL').length,name==='pending-retrieval-denied'?0:1);
  assert.equal(rows.filter(row=>row.callMetadata?.role==='ANSWER').length,name==='answer-http500'?1:0);
  if(name==='answer-http500'){const answer=rows.find(row=>row.callMetadata?.role==='ANSWER');assert.equal(answer.kind,'STREAM_PENDING');assert.equal(answer.chargeMicros,null);}
  if(name!=='pending-retrieval-denied'){const retrieval=rows.find(row=>row.callMetadata?.role==='RETRIEVAL');assert.equal(retrieval.currencyCode,'USD');assert.equal(retrieval.chargeMicros,4);}
  if(name==='answer-http500') {
   result.closeForFailureUi=await normalClose();port=await start(true);await ready(port);
   await until(()=>evaluate(port,`Boolean(document.querySelector('[data-action="select-chat"][data-id="${id}"]'))`),'失败会话入口');
   await clickUi(port,`[data-action="select-chat"][data-id="${id}"]`);
   const footer=await until(()=>evaluate(port,`(()=>{const value=document.querySelector('article[data-message-id="${leaf.id}"] .chat-message-metadata-cost')?.textContent;return value?.endsWith(' + 未知')?value:null})()`),'指定失败回答费用页脚');info.footer=footer;
   assert.ok(footer.endsWith(' + 未知'));info.footer=footer;
  }
 }
 scenario='normal';const saved=(await invoke(port,'CHAT','read_desktop_usage_ledger')).records;const count=requests.length;
 result.close=await normalClose();port=await start(true);await ready(port);
 assert.deepEqual((await invoke(port,'CHAT','read_desktop_usage_ledger')).records,saved);assert.equal(requests.length,count);
 result.closeAfterReopen=await normalClose();result.pass=true;
}catch(error){result.pass=false;result.stage=stage;result.error=String(error);process.exitCode=1;}
finally{if(launch){result.forcedCleanup=true;stop();}provider.closeAllConnections();await new Promise(done=>provider.close(done));result.pids=pids;if(root)await writeFile(join(root,'failure-matrix-native-evidence.json'),JSON.stringify(result,null,2),{flag:'wx'});}
console.log(JSON.stringify({root,pass:result.pass,stage:result.stage,error:result.error,cases:result.cases,requests:requests.length,serverErrors,pids}));
