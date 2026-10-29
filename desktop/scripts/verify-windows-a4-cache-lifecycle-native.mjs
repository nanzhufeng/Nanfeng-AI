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
const qwen=process.argv.includes('--qwen'),deepseek=process.argv.includes('--deepseek'),zhipu=process.argv.includes('--zhipu');const providerId=qwen?'QWEN':deepseek?'DEEPSEEK':zhipu?'ZHIPU':'OPENROUTER';const choiceId=qwen?'QWEN_3_7_PLUS':deepseek?'DEEPSEEK_V4_PRO':zhipu?'GLM_5_3_FLASH':'GPT_6_1_SOL';const wireModel=qwen?'qwen3.7-plus':deepseek?'deepseek-v4-pro':zhipu?'glm-5.3-flash':'openai/gpt-6.1-sol';const currency=providerId==='OPENROUTER'?'USD':'CNY';const expectedRetrievals=zhipu?8:2;
const unknownSecond=process.argv.includes('--unknown-second');
const requests=[];const serverErrors=[];const wireContents=[];
const provider=http.createServer(async(req,res)=>{
 try {
  let raw='';for await(const chunk of req)raw+=chunk.toString('utf8');const body=JSON.parse(raw);wireContents.push(JSON.stringify(body));
  assert.equal(req.headers.authorization==='Bearer local-acceptance-mock-only',true);
  const retrieval=body.model===wireModel && (Array.isArray(body.tools)||body.enable_search===true);
  requests.push({model:body.model,role:retrieval?'RETRIEVAL':'ANSWER',idempotencyKey:req.headers['idempotency-key'],grounded:JSON.stringify(body.messages||[]).includes('<retrieved-evidence>')});
  if(deepseek){
   assert.equal(req.headers['x-api-key'],'local-acceptance-mock-only');assert.equal(req.headers['anthropic-version'],'2023-06-01');
   const ids=retrieval?[...new Set([...JSON.stringify(body.messages).matchAll(/\[(A[1-9][0-9]*)\] /g)].map(m=>m[1]))]:[];
   if(retrieval){assert.ok(ids.length>0&&ids.length<=4);requests.at(-1).facts=ids;if(unknownSecond&&requests.filter(r=>r.role==='RETRIEVAL').length===2){res.destroy();return;}}
   const content=[{type:'text',text:retrieval?ids.map(id=>`[${id}] 已核实\n公开机构在2026年发布该项数据。\n来源：https://example.test/${id}`).join('\n'):'合成原模型回答'}];
   if(retrieval)content.unshift({type:'web_search_tool_result',content:ids.map(id=>({type:'web_search_result',url:`https://example.test/${id}`,title:'Synthetic evidence'}))});
   res.writeHead(200,{'Content-Type':'application/json'});res.end(JSON.stringify({type:'message',model:body.model,stop_reason:'end_turn',content,usage:{input_tokens:5,output_tokens:2,cost:0.000004}}));return;
  }
  res.writeHead(200,{'Content-Type':'text/event-stream; charset=utf-8'});const event=value=>res.write(`data: ${JSON.stringify(value)}\n\n`);
  if(retrieval) {
   if(qwen){assert.equal(body.enable_search,true);assert.equal(body.search_options.forced_search,true);}else if(zhipu){assert.ok(body.tools.some(t=>t.type==='web_search'));}else{assert.ok(body.tools.some(t=>t.type==='openrouter:web_search'));}
   const ids=[...new Set([...JSON.stringify(body.messages).matchAll(/\[(A[1-9][0-9]*)\] /g)].map(m=>m[1]))];
   assert.ok(ids.length>0 && ids.length<=4);requests[requests.length-1].facts=ids;
   if(unknownSecond && requests.filter(r=>r.role==='RETRIEVAL').length===2){res.destroy();return;}
   const rows=ids.map(id=>`[${id}] 已核实\n公开机构在2026年发布该项数据。\n来源：https://example.test/${id}`).join('\n');
   event({model:body.model,choices:[{delta:{content:rows}}]});
   res.end(`data: ${JSON.stringify({model:body.model,choices:[{delta:{annotations:ids.map(id=>({type:'url_citation',url_citation:{url:`https://example.test/${id}`,title:'Synthetic evidence'}}))},finish_reason:'stop'}],usage:{prompt_tokens:5,completion_tokens:2,cost:0.000004}})}`);
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





const result={candidateSha256:receipt.sha256,providerId,wireModel,currency,realService:false};let port=0;
try {
 port=await start();result.root=root;await ready(port);const ws=(await invoke(port,'CHAT','ensure_desktop_local_workspace')).summary.id;
 const settings=await invoke(port,'CHAT','read_desktop_model_service_settings');for(const configuredProvider of [providerId]){const providerId=configuredProvider;const setting=settings.find(s=>s.providerId===providerId);await invoke(port,'CHAT','save_desktop_model_service_settings',{args:{providerId,enabled:true,presetId:choiceId,expectedRevision:setting.revision,apiKey:`nfai-synthetic-fact-${providerId}`}});}
 const ids=[];for(const [name,text] of [['source-one.txt',Array.from({length:8},(_,i)=>`- 公开事项${i+1}：机构在2026年发布第${i+1}项数据。`).join('\n')]]){const a=await invoke(port,'CHAT','import_desktop_conversation_clipboard_attachment',{args:{workspaceId:ws,displayName:name,bytesBase64:Buffer.from(text).toString('base64')}});ids.push(a.id);}
 const data=await invoke(port,'CHAT','read_desktop_workspace',{workspaceId:ws});const conv=data.exchange.conversations[0];
 let selection=await invoke(port,'CHAT','read_desktop_p6g_selection',{workspaceId:ws,conversationId:conv.id});await invoke(port,'CHAT','set_desktop_p6g_conversation_override',{args:{workspaceId:ws,conversationId:conv.id,expectedRevision:selection.conversationOverride.revision,modelId:choiceId}});
 selection=await invoke(port,'CHAT','read_desktop_p6g_selection',{workspaceId:ws,conversationId:conv.id});await invoke(port,'CHAT','set_desktop_conversation_preferences',{args:{workspaceId:ws,conversationId:conv.id,expectedRevision:selection.conversationOverride.revision,toneOverride:null,webSearchOverride:true}});
 const text='核验附件中的全部八个公开事项';const preview=await invoke(port,'CHAT','preview_desktop_ordinary_chat_egress',{args:{workspaceId:ws,conversationId:conv.id,text,attachmentIds:ids,modelId:null,webSearchOverride:null}});
 assert.equal(preview.retrievalRecipient,null);assert.equal(preview.recipientModelId,wireModel);
 const auth={approvedAtMs:Date.now(),disclosureVersion:'normal-chat-egress-v2',clientSubmissionId:randomUUID(),draftFingerprint:preview.draftFingerprint,recipientChoiceId:preview.recipientChoiceId,recipientProviderId:preview.recipientProviderId,recipientModelId:preview.recipientModelId,retrievalRecipient:preview.retrievalRecipient};
 const attempt=await invoke(port,'CHAT','submit_desktop_ordinary_chat',{args:{workspaceId:ws,conversationId:conv.id,expectedRevision:conv.revision,text,attachmentIds:ids,modelId:null,egressAuthorization:auth}});if(unknownSecond){
 assert.notEqual(attempt.state,'COMPLETED');assert.equal(requests.length,2);assert.ok(requests.every(r=>r.role==='RETRIEVAL'));
 const rows=(await invoke(port,'CHAT','read_desktop_usage_ledger')).records.filter(r=>r.executionId===attempt.attemptId);
 assert.equal(rows.filter(r=>r.callMetadata?.role==='RETRIEVAL' && r.kind==='FINAL_MEASURED').length,1);
 assert.equal(rows.filter(r=>r.callMetadata?.role==='RETRIEVAL' && r.kind==='STREAM_PENDING').length,1);
 assert.equal(rows.filter(r=>r.callMetadata?.role==='ANSWER').length,0);result.unknownStoppedBeforeAnswer=true;
}else{
assert.equal(attempt.state,'COMPLETED');assert.equal(serverErrors.length,0);
 const answer=requests.find(r=>r.role==='ANSWER');assert.ok(answer?.grounded);assert.equal(requests.filter(r=>r.role==='RETRIEVAL').length,expectedRetrievals);assert.equal(requests.filter(r=>r.role==='ANSWER').length,1);
 const finalBody=wireContents.find(body=>body.includes(wireModel)&&body.includes('<retrieved-evidence>'));assert.ok(finalBody);assert.ok(finalBody.includes('8 / 8'));for(let i=1;i<=8;i++){assert.ok(finalBody.includes(`公开事项${i}`));assert.ok(finalBody.includes(`https://example.test/A${i}`));}
 const ledger=(await invoke(port,'CHAT','read_desktop_usage_ledger')).records.filter(r=>r.executionId===attempt.attemptId && r.kind==='FINAL_MEASURED');const retrievals=ledger.filter(r=>r.callMetadata?.role==='RETRIEVAL');assert.equal(retrievals.length,expectedRetrievals);assert.equal(new Set(retrievals.map(r=>r.invocationId)).size,expectedRetrievals);assert.ok(retrievals.every(r=>r.currencyCode===currency&&r.requestedModelId===wireModel&&r.actualModelId===wireModel&&!r.callMetadata.modelDisplayName.includes('Luna')));assert.equal(ledger.filter(r=>r.callMetadata?.role==='ANSWER').length,1);const all=(await invoke(port,'CHAT','read_desktop_usage_ledger')).records.filter(r=>r.executionId===attempt.attemptId);const known=new Set(all.filter(r=>r.kind==='FINAL_MEASURED').map(r=>r.invocationId));assert.equal(all.filter(r=>r.kind==='STREAM_PENDING'&&!known.has(r.invocationId)).length,0,'成功调用不能残留虚假未知费用');result.batchFacts=requests.filter(r=>r.role==='RETRIEVAL').map(r=>r.facts);result.finalRecords=ledger.length;result.originalMaterialsAndEvidencePreserved=true;
}
 const readOwned=()=>JSON.parse(execFileSync('python',['-c',`import sqlite3,sys,json,hashlib
from pathlib import Path
root=Path(sys.argv[1]);assert root.parent==Path('D:/CodexTemp');assert json.loads((root/'.nanfeng-windows-acceptance.json').read_text())['identifier']=='com.nanzhufeng.ai.desktop.windowsacceptance'
c=sqlite3.connect('file:'+str(root/'workspace.sqlite3')+'?mode=ro',uri=True);ledger=sqlite3.connect('file:'+str(root/'usage-ledger/usage-ledger-v1.sqlite3')+'?mode=ro',uri=True)
rows=ledger.execute('SELECT * FROM usage_ledger_entries ORDER BY entry_id').fetchall();meta=ledger.execute('SELECT * FROM usage_ledger_call_metadata ORDER BY entry_id').fetchall()
print(json.dumps({'cache':c.execute('SELECT count(*) FROM desktop_attachment_search_checkpoints').fetchone()[0],'coverage':c.execute('SELECT count(*) FROM desktop_attachment_search_coverage').fetchone()[0],'attempts':c.execute('SELECT count(*) FROM desktop_ordinary_chat_attempts').fetchone()[0],'ledger':hashlib.sha256(json.dumps([rows,meta]).encode()).hexdigest(),'assets':{p.name:hashlib.sha256(p.read_bytes()).hexdigest() for p in (root/'assets').iterdir() if p.is_file()}}))`,root],{encoding:'utf8',timeout:10000}));
 result.beforeDelete=readOwned();assert.equal(result.beforeDelete.cache,8);assert.equal(result.beforeDelete.coverage,1);
 const current=(await invoke(port,'CHAT','read_desktop_workspace',{workspaceId:ws})).exchange.conversations.find(c=>c.id===conv.id);
 const sqlOwned=code=>execFileSync('python',['-c',code,root],{encoding:'utf8',timeout:10000});
 sqlOwned(`import sqlite3,sys;from pathlib import Path;c=sqlite3.connect(Path(sys.argv[1])/'workspace.sqlite3');c.execute("CREATE TRIGGER reject_evidence_cleanup BEFORE DELETE ON desktop_attachment_search_checkpoints BEGIN SELECT RAISE(ABORT,'synthetic cache cleanup');END");c.commit()`);
 await assert.rejects(invoke(port,'CHAT','mutate_desktop_domain',{args:{workspaceId:ws,intentId:randomUUID(),entity:'conversation',action:'softDelete',objectId:conv.id,expectedRevision:current.revision,fields:{}}}));
 result.failedCleanupPreserved=readOwned();assert.deepEqual(result.failedCleanupPreserved,result.beforeDelete);
 const unchanged=(await invoke(port,'CHAT','read_desktop_workspace',{workspaceId:ws})).exchange.conversations.find(c=>c.id===conv.id);assert.notEqual(unchanged.deleted,true);assert.equal(unchanged.revision,current.revision);
 sqlOwned(`import sqlite3,sys;from pathlib import Path;c=sqlite3.connect(Path(sys.argv[1])/'workspace.sqlite3');c.execute('DROP TRIGGER reject_evidence_cleanup');c.commit()`);
 const deleted=await invoke(port,'CHAT','mutate_desktop_domain',{args:{workspaceId:ws,intentId:randomUUID(),entity:'conversation',action:'softDelete',objectId:conv.id,expectedRevision:current.revision,fields:{}}});
 result.afterDelete=readOwned();assert.equal(result.afterDelete.cache,0);assert.equal(result.afterDelete.coverage,0);assert.equal(result.afterDelete.attempts,result.beforeDelete.attempts);assert.equal(result.afterDelete.ledger,result.beforeDelete.ledger);assert.deepEqual(result.afterDelete.assets,result.beforeDelete.assets);
 await invoke(port,'CHAT','mutate_desktop_domain',{args:{workspaceId:ws,intentId:randomUUID(),entity:'conversation',action:'restoreDeleted',objectId:conv.id,expectedRevision:deleted.revision,fields:{}}});
 result.afterRestore=readOwned();assert.equal(result.afterRestore.cache,0);assert.equal(result.afterRestore.coverage,0);assert.equal(result.afterRestore.ledger,result.beforeDelete.ledger);assert.deepEqual(result.afterRestore.assets,result.beforeDelete.assets);
 const restored=(await invoke(port,'CHAT','read_desktop_workspace',{workspaceId:ws})).exchange.conversations.find(c=>c.id===conv.id);
 const branch=await invoke(port,'CHAT','mutate_desktop_domain',{args:{workspaceId:ws,intentId:randomUUID(),entity:'conversation',action:'branchFromMessage',objectId:conv.id,expectedRevision:restored.revision,fields:{messageId:attempt.assistantMessageId}}});
 const beforePurge=(await invoke(port,'CHAT','read_desktop_workspace',{workspaceId:ws})).exchange.conversations.find(c=>c.id===conv.id);
 const again=await invoke(port,'CHAT','mutate_desktop_domain',{args:{workspaceId:ws,intentId:randomUUID(),entity:'conversation',action:'softDelete',objectId:conv.id,expectedRevision:beforePurge.revision,fields:{}}});
 result.purge=await invoke(port,'CHAT','permanently_delete_desktop_conversation',{args:{workspaceId:ws,conversationId:conv.id,expectedRevision:again.revision}});
 result.afterPurge=readOwned();assert.equal(result.afterPurge.cache,0);assert.equal(result.afterPurge.coverage,0);assert.equal(result.afterPurge.ledger,result.beforeDelete.ledger);assert.deepEqual(result.afterPurge.assets,result.beforeDelete.assets);
 const survivors=(await invoke(port,'CHAT','read_desktop_workspace',{workspaceId:ws})).exchange.conversations;assert.ok(survivors.some(c=>c.id===branch.objectId));assert.ok(!survivors.some(c=>c.id===conv.id));result.sharedBranchPreserved=true;
 result.close=await normalClose();result.pass=true;
}catch(error){result.pass=false;result.error=String(error);process.exitCode=1;}
finally{if(launch){result.forcedCleanup=true;stop();}provider.closeAllConnections();await new Promise(done=>provider.close(done));result.requests=requests;result.serverErrors=serverErrors;result.pids=pids;if(root)await writeFile(join(root,'cache-lifecycle-native-evidence.json'),JSON.stringify(result,null,2),{flag:'wx'});}
console.log(JSON.stringify({root,pass:result.pass,error:result.error,requests:requests.length,serverErrors,pids}));
