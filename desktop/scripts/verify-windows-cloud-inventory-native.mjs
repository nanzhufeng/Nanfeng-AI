import { packageExchange, preflightPackage, semanticHash, sha256 } from '../../protocol/scripts/exchange-lib.mjs';
import assert from 'node:assert/strict';
import { createHash, randomUUID } from 'node:crypto';
import { execFileSync, spawn } from 'node:child_process';
import { mkdir, readFile, readdir, writeFile } from 'node:fs/promises';
import http from 'node:http';
import { dirname, join, resolve, sep } from 'node:path';
import { fileURLToPath } from 'node:url';

if (process.platform !== 'win32') throw new Error('仅在 Windows 隔离候选上验收。');
const desktopRoot = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const rootParent = resolve(process.argv[2] || 'D:\\CodexTemp');
assert.ok(process.execArgv.includes('--max-old-space-size=2048'));
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

const accountEmail='temporary-consumer-sync@example.invalid';
const syntheticToken='synthetic-temporary-consumer-access';
const markers=['TEMP_CONSUMER_PRIVATE_MESSAGE','TEMP_CONSUMER_PRIVATE_DRAFT','TEMP_CONSUMER_PRIVATE_BYTES','TEMP_CONSUMER_PRIVATE_NAME','ORDINARY_UNSENT_DO_NOT_EXPORT'];
let heldDelete=null,releaseDelete=null;
let inventoryMode="valid";const inventoryReplies=[];const LIMIT=32*1024*1024;
const documents=new Map(),requests=[],serverErrors=[];
const server=http.createServer(async(request,response)=>{
 try {
  const chunks=[];for await(const chunk of request)chunks.push(chunk);
  const bytes=Buffer.concat(chunks),body=bytes.length?JSON.parse(bytes.toString('utf8')):{};
  const path=request.url?.split('?')[0];
  requests.push({path,documentId:body.p_document_id??null,bodyBytes:bytes.length,bodySha256:createHash('sha256').update(bytes).digest('hex'),privateMarkerPresent:markers.some(marker=>bytes.includes(Buffer.from(marker)))});
  let value;
  if(path==='/auth/v1/token'){
   assert.equal(body.auth_code,'nfai-synthetic-windows-account-code');
   value={access_token:syntheticToken,refresh_token:'synthetic-temporary-consumer-refresh',expires_in:3600,user:{id:'00000000-0000-4000-8000-000000000117',email:accountEmail,user_metadata:{full_name:'合成临时隔离账号'}}};
  } else {
   assert.equal(request.headers.authorization,`Bearer ${syntheticToken}`);assert.equal(body.p_app_id,'com.nanzhufeng.ai');
   if(path==='/rest/v1/rpc/nanfeng_sync_read_document')value=documents.has(body.p_document_id)?[{envelope:documents.get(body.p_document_id)}]:[];
   else if(path==='/rest/v1/rpc/nanfeng_sync_commit_document'){
    const expected=documents.get(body.p_document_id)?.revision??0;assert.equal(body.p_expected_revision,expected);
    assert.equal(body.p_envelope.format,'nfai.sync.direct');assert.equal(body.p_envelope.documentId,body.p_document_id);assert.equal(body.p_envelope.revision,expected+1);
    documents.set(body.p_document_id,body.p_envelope);value=[{revision:expected+1,payload_hash:body.p_envelope.payloadHash}];
   }else if(path==='/rest/v1/rpc/nanfeng_sync_inventory'){
     const items=[...documents.values()];let rows=items.map(envelope=>({envelope})),count=rows.length;
     if(inventoryMode==='known-bad'){rows=structuredClone(rows);rows[0].envelope.payloadHash='0'.repeat(64);}
     if(inventoryMode==='unknown-identity'){rows=[{envelope:items[0]},{envelope:items[1]},{envelope:'not-json'}];count=3;}
     if(inventoryMode==='count-mismatch')count=rows.length+1;
     if(inventoryMode==='10001-items'){rows=Array.from({length:10001},()=>({envelope:{}}));count=rows.length;}
     if(inventoryMode==='10000-items'){rows=Array.from({length:10000},()=>({envelope:{}}));count=rows.length;}
     if(inventoryMode==='exact-empty'){rows=[];count=0;}
     let encoded=JSON.stringify({documentCount:count,documents:rows});
     if(inventoryMode==='over-bytes'||inventoryMode==='exact-empty'){const target=LIMIT+(inventoryMode==='over-bytes'?1:0);encoded+=' '.repeat(target-Buffer.byteLength(encoded));}
     const bytes=Buffer.byteLength(encoded);inventoryReplies.push({mode:inventoryMode,bytes,declaredCount:count,arrayCount:rows.length,responseSha256:createHash('sha256').update(encoded).digest('hex')});
     response.on('error',()=>{});response.writeHead(200,{'content-type':'application/json','content-length':bytes});response.end(encoded);return;
    }
   else if(path==='/rest/v1/rpc/nanfeng_sync_delete_document'){assert.ok(documents.has(body.p_document_id));assert.equal(body.p_expected_revision,documents.get(body.p_document_id).revision);heldDelete=body.p_document_id;await new Promise(done=>{releaseDelete=done});documents.delete(body.p_document_id);value=[{deleted:true}];}
    else throw new Error(`unexpected RPC ${path}`);
  }
  response.writeHead(200,{'content-type':'application/json'});response.end(JSON.stringify(value));
 } catch(error){serverErrors.push(String(error));response.writeHead(500);response.end('{"message":"synthetic protocol failure"}');}
});
await new Promise(done=>server.listen(0,'127.0.0.1',done));const serverPort=server.address().port;

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
    '-StartupMode', 'normal', '-Visible', '-DebugPort', String(port),'-AccountSyncTestEndpoint',`http://127.0.0.1:${serverPort}`];
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
  const result = await evaluate(port, `(()=>{const node=document.querySelector(${JSON.stringify(selector)});if(!node)return false;node.click();return true})()`);
  assert.equal(result, true, `找不到可点击控件 ${selector}`);
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





const resumeRoot=process.argv[3]?resolve(process.argv[3]):null;
let result;if(resumeRoot){assert.ok(resumeRoot.startsWith(rootParent+sep));const failures=(await readdir(resumeRoot)).filter(n=>n.startsWith('cloud-inventory-failure-')&&n.endsWith('.json'));const selected=process.argv[4]||failures[0];assert.ok(/^cloud-inventory-failure-[a-f0-9-]+\.json$/.test(selected));assert.ok(failures.includes(selected));result=JSON.parse(await readFile(join(resumeRoot,selected),'utf8'));const priorCandidate=result.candidateSha256;if(priorCandidate!==receipt.sha256){assert.equal(priorCandidate,'252310E29B629D8014DA5E29886DEF8A413C3BA6549D86BB768C5B66F54780CF');assert.equal(result.stage,'10000-items');result.priorCandidateSha256=priorCandidate;result.candidateSha256=receipt.sha256;}for(const c of result.cases)c.candidateSha256??=priorCandidate;assert.ok(['known-bad','10001-items','10000-items'].includes(result.stage));result.priorRuns=[...(result.priorRuns||[]),{stage:result.stage,requests:result.requests,cases:result.cases}];result.priorRequests??=result.requests;result.priorCases??=result.cases;for(const doc of JSON.parse(await readFile(join(resumeRoot,'synthetic-remote-documents-before-inventory.json'),'utf8')))documents.set(doc.documentId,doc);root=resumeRoot;result.resumedAfterFixtureFeedbackCorrection=true;}else result={candidateSha256:receipt.sha256,startupMode:'normal',realService:false,cases:[]};let stage='start';
async function uiUntil(port,expression,label){return until(()=>evaluate(port,expression),label);}
async function openAccount(port){if(await evaluate(port,'Boolean(document.querySelector("[data-action=load-account-cloud-documents]"))'))return;await clickUi(port,'[data-action="show-settings"]');await uiUntil(port,'Boolean(document.querySelector("[data-action=open-settings-page][data-page=account]"))','account settings');await clickUi(port,'[data-action="open-settings-page"][data-page="account"]');await uiUntil(port,'Boolean(document.querySelector("[data-action=load-account-cloud-documents]"))','cloud read button');}
async function inspect(port){return evaluate(port,'({area:globalThis.__nanfengDesktopWorkState.dataArea,pane:globalThis.__nanfengDesktopWorkState.pane,error:globalThis.__nanfengDesktopWorkState.error,status:globalThis.__nanfengDesktopWorkState.status,progress:globalThis.__nanfengDesktopWorkState.accountSyncProgress?.result,cloudRows:globalThis.__nanfengDesktopWorkState.cloudConversations.map(r=>({id:r.id,workspaceId:r.workspaceId})),selectedCount:globalThis.__nanfengDesktopWorkState.accountSync.selectedConversationCount})');}
async function readCase(port,mode,expectedKind){const prior=result.cases.find(c=>c.mode===mode);if(prior){assert.equal(prior.ui.progress.kind,expectedKind);return prior.ui;}inventoryMode=mode;const requestBefore=inventoryReplies.length,started=Date.now();await openAccount(port);await clickUi(port,'[data-action="load-account-cloud-documents"]');await until(()=>inventoryReplies.length>requestBefore,'inventory response');await uiUntil(port,'Boolean(globalThis.__nanfengDesktopWorkState.accountSyncProgress?.result)','read completed feedback');const ui=await inspect(port);assert.equal(ui.progress.kind,expectedKind);assert.equal(inventoryReplies.length-requestBefore,1,'malformed list must not automatically retry');const record={candidateSha256:receipt.sha256,mode,elapsedMs:Date.now()-started,response:inventoryReplies.at(-1),ui};result.cases.push(record);console.log(JSON.stringify({stage:'case-completed',mode,elapsedMs:record.elapsedMs,kind:ui.progress.kind,cloudRowCount:ui.cloudRows.length,responseBytes:record.response.bytes}));await writeFile(join(result.root,'cloud-inventory-progress.json'),JSON.stringify(result,null,2));return ui;}
try{
 let port=await start(Boolean(resumeRoot));result.root=root;await ready(port);
 if(!resumeRoot){
 const inputs=join(root,'synthetic-cloud-inventory-inputs');await mkdir(inputs,{recursive:true});
 const exchange=JSON.parse(await readFile(join(desktopRoot,'../protocol/fixtures/nfai.exchange.v1.golden.json'),'utf8'));const base=exchange.conversations[0];exchange.conversations=[];
 for(let n=0;n<3;n++){const c=structuredClone(base),ids=new Map(c.messages.map(m=>[m.id,m.id+'-inventory-'+n]));c.id='synthetic-inventory-conversation-'+n;c.title='SYNTHETIC_INVENTORY_VISIBLE_'+n;c.projectId=null;c.currentLeafId=ids.get(c.currentLeafId)||null;c.messages.forEach((m,i)=>{m.id=ids.get(m.id);if(m.parentId)m.parentId=ids.get(m.parentId);m.blocks=[{kind:'TEXT',ordinal:0,text:'SYNTHETIC_INVENTORY_ORIGINAL_'+n+'_'+i}];});exchange.conversations.push(c);}
 exchange.projects=[];exchange.relations=[];exchange.knowledge=[];exchange.memory=[];exchange.export.id='synthetic-cloud-inventory-export';exchange.export.semanticHash=semanticHash(exchange);
 const bytes=packageExchange(exchange,new Map());preflightPackage(bytes);const selectedPath=join(inputs,'ordinary.nfai-exchange');await writeFile(selectedPath,bytes);const staged=await invoke(port,'CHAT','stage_preflight_selected_exchange',{selectedPath});result.imported=await invoke(port,'CHAT','import_staged_exchange_as_new_workspace',{args:{stagingId:staged.stagingId,workspaceTitle:'合成云列表隔离'}});result.workspaceId=result.imported.summary.id;
 const settings=await invoke(port,'CHAT','read_desktop_app_settings');await invoke(port,'CHAT','save_desktop_app_settings',{args:{appearance:settings.appearance,product:{...settings.product,historyLibraryEnabled:false},expectedRevision:settings.revision}});await invoke(port,'CHAT','sign_in_desktop_google_account');
 for(const c of exchange.conversations)assert.equal((await invoke(port,'CHAT','sync_selected_desktop_conversation',{workspaceId:result.workspaceId,conversationId:c.id})).status,'SYNCED');assert.equal(documents.size,3);await writeFile(join(root,'synthetic-remote-documents-before-inventory.json'),JSON.stringify([...documents.values()],null,2));
 }else assert.deepEqual((await invoke(port,'CHAT','read_desktop_workspace',{workspaceId:result.workspaceId})).exchange,result.imported.exchange);
 await cdp(port,'Page.reload',{ignoreCache:true});await ready(port);await uiUntil(port,'globalThis.__nanfengDesktopWorkState.runtimeInfo?.dataAreasReady','area projections');
 stage='valid';const full=await readCase(port,'valid','success');assert.equal(full.cloudRows.length,3);
 stage='known-bad';const partial=await readCase(port,'known-bad','attention');assert.equal(partial.cloudRows.length,3);assert.equal((await invoke(port,'CHAT','read_desktop_account_sync')).selectedConversationCount,3);
 stage='unknown-identity';const unknown=await readCase(port,'unknown-identity','attention');assert.deepEqual(unknown.cloudRows,full.cloudRows);assert.equal((await invoke(port,'CHAT','read_desktop_account_sync')).selectedConversationCount,3);
 for(const mode of ['count-mismatch','10001-items','10000-items','over-bytes']){stage=mode;const state=await readCase(port,mode,'error');assert.deepEqual(state.cloudRows,full.cloudRows);assert.equal((await invoke(port,'CHAT','read_desktop_account_sync')).selectedConversationCount,3);assert.deepEqual((await invoke(port,'CHAT','read_desktop_workspace',{workspaceId:result.workspaceId})).exchange,result.imported.exchange);}
 stage='exact-empty';documents.clear();const empty=await readCase(port,'exact-empty','success');assert.equal(empty.cloudRows.length,0);await uiUntil(port,'globalThis.__nanfengDesktopWorkState.accountSync.selectedConversationCount===0','empty inventory account metadata');assert.equal((await invoke(port,'CHAT','read_desktop_account_sync')).selectedConversationCount,0);assert.deepEqual((await invoke(port,'CHAT','read_desktop_workspace',{workspaceId:result.workspaceId})).exchange,result.imported.exchange);
 assert.equal((await invoke(port,'WORK','read_desktop_account_sync')).selectedConversationCount,0);result.closes=[await normalClose()];stage='cold';port=await start(true);await ready(port);assert.deepEqual((await invoke(port,'CHAT','read_desktop_workspace',{workspaceId:result.workspaceId})).exchange,result.imported.exchange);assert.equal((await invoke(port,'CHAT','read_desktop_account_sync')).selectedConversationCount,0);result.closes.push(await normalClose());
 assert.equal(requests.filter(r=>r.path==='/rest/v1/rpc/nanfeng_sync_commit_document').length,resumeRoot?0:3);assert.deepEqual(serverErrors,[]);result.coldExact=true;result.requests=requests;result.inventoryReplies=inventoryReplies;result.pids=pids;
 await writeFile(join(result.root,'cloud-inventory-native.json'),JSON.stringify(result,null,2));console.log(JSON.stringify({stage:'completed',candidateSha256:receipt.sha256,root:result.root,cases:result.cases.map(c=>({mode:c.mode,elapsedMs:c.elapsedMs})),coldExact:true,pids,closes:result.closes}));
}catch(error){if(root)await writeFile(join(root,`cloud-inventory-failure-${randomUUID()}.json`),JSON.stringify({...result,stage,requests,inventoryReplies,error:String(error)},null,2));console.error(JSON.stringify({stage,root,pid:launch?.pid,error:String(error)}));process.exitCode=1;}
finally{releaseDelete?.();stop();server.closeAllConnections();await new Promise(done=>server.close(done));}
