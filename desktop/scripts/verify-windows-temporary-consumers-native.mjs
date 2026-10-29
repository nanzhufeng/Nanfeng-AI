import { packageExchange, preflightPackage, semanticHash, sha256 } from '../../protocol/scripts/exchange-lib.mjs';
import assert from 'node:assert/strict';
import { createHash, randomUUID } from 'node:crypto';
import { execFileSync, spawn } from 'node:child_process';
import { mkdir, readFile, writeFile } from 'node:fs/promises';
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
   value={access_token:syntheticToken,refresh_token:'synthetic-temporary-consumer-refresh',expires_in:3600,user:{id:'00000000-0000-4000-8000-000000000093',email:accountEmail,user_metadata:{full_name:'合成临时隔离账号'}}};
  } else {
   assert.equal(request.headers.authorization,`Bearer ${syntheticToken}`);assert.equal(body.p_app_id,'com.nanzhufeng.ai');
   if(path==='/rest/v1/rpc/nanfeng_sync_read_document')value=documents.has(body.p_document_id)?[{envelope:documents.get(body.p_document_id)}]:[];
   else if(path==='/rest/v1/rpc/nanfeng_sync_commit_document'){
    const expected=documents.get(body.p_document_id)?.revision??0;assert.equal(body.p_expected_revision,expected);
    assert.equal(body.p_envelope.format,'nfai.sync.direct');assert.equal(body.p_envelope.documentId,body.p_document_id);assert.equal(body.p_envelope.revision,expected+1);
    documents.set(body.p_document_id,body.p_envelope);value=[{revision:expected+1,payload_hash:body.p_envelope.payloadHash}];
   }else if(path==='/rest/v1/rpc/nanfeng_sync_inventory')value={documentCount:documents.size,documents:[...documents.values()].map(envelope=>({envelope}))};
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


const resumeRoot=process.argv[3]?resolve(process.argv[3]):'';
const resumeReceipt=process.argv[4]||'temporary-consumer-failure.json';assert.ok(['temporary-consumer-failure.json','temporary-consumer-resume-failure.json'].includes(resumeReceipt));
const result=resumeRoot?JSON.parse(await readFile(join(resumeRoot,resumeReceipt),'utf8')):{candidateSha256:receipt.sha256,startupMode:'normal',realService:false};
assert.equal(result.candidateSha256,receipt.sha256);
let stage='start';
const commits=()=>requests.filter(r=>r.path==='/rest/v1/rpc/nanfeng_sync_commit_document').length;
async function typeDraft(port,text){await until(()=>evaluate(port,'Boolean(document.querySelector("#chat-composer"))'),'composer');await evaluate(port,`(()=>{const input=document.querySelector('#chat-composer');input.value=${JSON.stringify(text)};input.dispatchEvent(new Event('input',{bubbles:true}));return true})()`);}
async function selectConversation(port,workspaceId,conversationId){
 // A single current workspace has no switcher by the existing UI contract.
 await until(()=>evaluate(port,`globalThis.__nanfengDesktopWorkState.current?.summary?.id===${JSON.stringify(workspaceId)} || Boolean(document.querySelector('[data-action="select-workspace"][data-id="${workspaceId}"]'))`),'workspace current or switch entry');
 if(await evaluate(port,`globalThis.__nanfengDesktopWorkState.current?.summary?.id!==${JSON.stringify(workspaceId)}`)) await clickUi(port,`[data-action="select-workspace"][data-id="${workspaceId}"]`);
 await until(()=>evaluate(port,`Boolean(document.querySelector('[data-action="select-chat"][data-id="${conversationId}"]'))`),'conversation entry');
 await clickUi(port,`[data-action="select-chat"][data-id="${conversationId}"]`);
 await until(()=>evaluate(port,`Boolean(document.querySelector('.chat-scroll[data-conversation-id="${conversationId}"]'))`),'conversation body');
}
async function search(port,text){
 await until(()=>evaluate(port,'Boolean(document.querySelector("#chat-search"))'),'search input');
 await evaluate(port,`(()=>{const input=document.querySelector('#chat-search');input.value=${JSON.stringify(text)};input.dispatchEvent(new Event('input',{bubbles:true}));input.dispatchEvent(new KeyboardEvent('keydown',{key:'Enter',bubbles:true}));return true})()`);
 await until(()=>evaluate(port,`globalThis.__nanfengDesktopWorkState.searchPanel && !globalThis.__nanfengDesktopWorkState.searchLoading && globalThis.__nanfengDesktopWorkState.chatSearch===${JSON.stringify(text)}`),'actual search completed');
 return invoke(port,'CHAT','query_desktop_local_index',{args:{query:text,category:'all',sortMode:'default',fileType:'all',offset:0,recordHistory:false,historyWorkspaceId:null},requestId:Date.now()});
}
try {
 let port;
 if(resumeRoot){root=resumeRoot;port=await start(true);await ready(port);if(result.temporaryBefore)assert.deepEqual(await invoke(port,'CHAT','read_desktop_temporary_conversation'),result.temporaryBefore);else {assert.equal(result.stage,'start');assert.ok(result.imported);assert.equal(await invoke(port,'CHAT','read_desktop_temporary_conversation'),null);}result.resumedFromFailure=true;}else{port=await start();result.root=root;await ready(port);}
 const inputs=join(root,'synthetic-temporary-consumer-inputs');await mkdir(inputs,{recursive:true});
 const sharedBytes=Buffer.from('ordinary-shared-private-byte-copy');
 if(!resumeRoot){
 const exchange=JSON.parse(await readFile(join(desktopRoot,'../protocol/fixtures/nfai.exchange.v1.golden.json'),'utf8'));
 const conversation=exchange.conversations[0];conversation.id='synthetic-temporary-consumer-conversation';conversation.title='ORDINARY_CONSUMER_VISIBLE';conversation.projectId=null;
 conversation.messages[0].blocks=[{kind:'TEXT',ordinal:0,text:'ORDINARY_CONSUMER_MESSAGE complete original question'}];
 conversation.messages[1].blocks=[{kind:'TEXT',ordinal:0,text:'ORDINARY_CONSUMER_ANSWER complete original answer'}];
 const sharedHash=sha256(sharedBytes);
 const asset={id:'synthetic-consumer-shared-attachment',entry:'assets/'+sharedHash,mimeType:'text/plain',displayName:'ordinary-shared.txt',byteCount:sharedBytes.length,sha256:sharedHash,classification:'NORMAL'};
 conversation.messages[0].blocks.push({kind:'ASSET_REF',ordinal:1,asset});
 exchange.projects=[];exchange.relations=[];exchange.memory[0].scope='GLOBAL';exchange.memory[0].scopeId=null;
 exchange.knowledge[0].body='PRIVATE_LIBRARY_NOT_IN_SELECTED_SYNC';exchange.knowledge[0].contentHash=sha256(Buffer.from(exchange.knowledge[0].body));
 exchange.memory[0].body='PRIVATE_MEMORY_NOT_IN_SELECTED_SYNC';exchange.memory[0].contentHash=sha256(Buffer.from(exchange.memory[0].body));
 exchange.export.id='synthetic-temporary-consumer-export';exchange.export.semanticHash=semanticHash(exchange);
 const bytes=packageExchange(exchange,new Map([[asset.entry,sharedBytes]]));preflightPackage(bytes);
 const selectedPath=join(inputs,'ordinary.nfai-exchange');await writeFile(selectedPath,bytes);
 const preflight=await invoke(port,'CHAT','stage_preflight_selected_exchange',{selectedPath});
 result.imported=await invoke(port,'CHAT','import_staged_exchange_as_new_workspace',{args:{stagingId:preflight.stagingId,workspaceTitle:'合成临时消费者隔离'}});result.workspaceId=result.imported.summary.id;result.conversationId=conversation.id;
 const settings=await invoke(port,'CHAT','read_desktop_app_settings');await invoke(port,'CHAT','save_desktop_app_settings',{args:{appearance:settings.appearance,product:{...settings.product,historyLibraryEnabled:false},expectedRevision:settings.revision}});
 await invoke(port,'CHAT','sign_in_desktop_google_account');
 await cdp(port,'Page.reload',{ignoreCache:true});await ready(port);
 }
 await selectConversation(port,result.workspaceId,result.conversationId);
 if(!result.temporaryBefore){
 stage='new-chat-route';await clickUi(port,'[data-action="new-chat"]');await until(()=>evaluate(port,'Boolean(document.querySelector("[data-action=toggle-temporary-chat]"))'),'blank-chat Ghost entry');
 stage='ordinary-draft';await typeDraft(port,markers[4]);await until(async()=>{const draft=await invoke(port,'CHAT','read_desktop_conversation_draft',{workspaceId:result.workspaceId,conversationId:null});return draft?.text===markers[4]||draft?.draft===markers[4];},'ordinary draft persisted');
 stage='temporary-ui';await clickUi(port,'[data-action="toggle-temporary-chat"]');const temporary=await until(()=>invoke(port,'CHAT','read_desktop_temporary_conversation'),'temporary owner');result.temporaryId=temporary.temporaryId;
 const sharedPath=join(inputs,'ordinary-shared.txt'),privatePath=join(inputs,markers[3]+'.txt');await writeFile(sharedPath,sharedBytes);await writeFile(privatePath,markers[2]);
 for(const path of [sharedPath,privatePath])await invoke(port,'CHAT','import_desktop_temporary_attachment',{args:{temporaryId:result.temporaryId,selectedPath:path}});
 await typeDraft(port,markers[0]);await until(()=>evaluate(port,'globalThis.__nanfengDesktopWorkState.temporaryConversation?.attachments?.length===2'),'temporary attachment projection');
 await clickUi(port,'[data-action="save-local-message"]');await until(async()=>{const t=await invoke(port,'CHAT','read_desktop_temporary_conversation');return t.messages.length===1&&t.messages[0].text===markers[0];},'actual temporary local send');
 await typeDraft(port,markers[1]);result.temporaryBefore=await until(async()=>{const t=await invoke(port,'CHAT','read_desktop_temporary_conversation');return t.draft===markers[1]?t:null;},'temporary draft persisted');
 await clickUi(port,'[data-action="toggle-temporary-chat"]');assert.deepEqual(await invoke(port,'CHAT','read_desktop_temporary_conversation'),result.temporaryBefore);
 await until(()=>evaluate(port,`document.querySelector('#chat-composer')?.value===${JSON.stringify(markers[4])}`),'ordinary draft restored after Ghost');
 stage='search';const privateSearch=await search(port,'TEMP_CONSUMER_PRIVATE');assert.equal(privateSearch.totalCount,0);result.search={TEMP_CONSUMER_PRIVATE:0};
 const positive=await invoke(port,'CHAT','query_desktop_local_index',{args:{query:'ORDINARY_CONSUMER_MESSAGE',category:'all',sortMode:'default',fileType:'all',offset:0,recordHistory:false,historyWorkspaceId:null},requestId:Date.now()});assert.ok(positive.totalCount>0);result.positiveSearchCount=positive.totalCount;for(let level=0;level<2&&await evaluate(port,'globalThis.__nanfengDesktopWorkState.searchPanel');level++){await evaluate(port,"(document.querySelector('#full-search-input')||document.querySelector('#chat-search')).dispatchEvent(new KeyboardEvent('keydown',{key:'Escape',bubbles:true}));true");}await until(()=>evaluate(port,'!globalThis.__nanfengDesktopWorkState.searchPanel'),'search topmost dismissal and exit');
 }
 if(resumeRoot && result.stage==='search'){
  assert.equal((await search(port,'TEMP_CONSUMER_PRIVATE')).totalCount,0);
  for(let level=0;level<2&&await evaluate(port,'globalThis.__nanfengDesktopWorkState.searchPanel');level++)await evaluate(port,"(document.querySelector('#full-search-input')||document.querySelector('#chat-search')).dispatchEvent(new KeyboardEvent('keydown',{key:'Escape',bubbles:true}));true");
  await until(()=>evaluate(port,'!globalThis.__nanfengDesktopWorkState.searchPanel'),'search input Escape dismissal');result.searchExitVerified=true;
 }
 const sharedHash=result.imported.exchange.conversations[0].messages[0].blocks.find(block=>block.kind==='ASSET_REF').asset.sha256;
 stage='exchange-export';result.exportPath=join(result.root,'synthetic-temporary-consumer-inputs','ordinary-reexport.nfai-exchange');await invoke(port,'CHAT','export_desktop_workspace_to_selected_path',{workspaceId:result.workspaceId,selectedPath:result.exportPath});
 const exported=preflightPackage(await readFile(result.exportPath));assert.deepEqual(exported.exchange.conversations,result.imported.exchange.conversations);
 assert.ok(markers.every(marker=>!JSON.stringify(exported.exchange).includes(marker)));result.exchangeIsolation=true;
 stage='explicit-temporary-sync-refusal';const beforeRead=requests.length;let refusal='';try{await invoke(port,'CHAT','sync_selected_desktop_conversation',{workspaceId:result.workspaceId,conversationId:result.temporaryId});}catch(error){refusal=String(error);}
 assert.ok(refusal.includes('未访问云端'));assert.equal(requests.length,beforeRead);result.temporarySyncRefused=true;result.temporarySyncRequestDelta=requests.length-beforeRead;
 stage='actual-normal-sync';
 const selector=`[data-conversation-row][data-id="${result.conversationId}"]`;
 assert.equal(await evaluate(port,`(()=>{const row=document.querySelector(${JSON.stringify(selector)});if(!row)return false;row.dispatchEvent(new MouseEvent('contextmenu',{bubbles:true,cancelable:true}));return true})()`),true);
 await until(()=>evaluate(port,'Boolean(document.querySelector("[data-action=context-menu-sync]"))'),'normal sync menu');await clickUi(port,'[data-action="context-menu-sync"]');
 await until(()=>commits()===1,'actual menu cloud commit');await until(async()=> (await invoke(port,'CHAT','read_desktop_account_sync')).selectedConversationCount===1,'verified selection receipt');
 assert.equal(documents.size,1);const envelope=[...documents.values()][0];
 assert.ok([...markers,'PRIVATE_LIBRARY_NOT_IN_SELECTED_SYNC','PRIVATE_MEMORY_NOT_IN_SELECTED_SYNC',sharedHash].every(marker=>!JSON.stringify(envelope).includes(marker)));
 const conversationRecords=envelope.payload.records.filter(record=>record.kind==='conversation');assert.equal(conversationRecords.length,1);assert.equal(conversationRecords[0].id,result.conversationId);
 assert.ok(conversationRecords[0].content.messages.every(m=>m.blocks.every(b=>b.kind==='TEXT')));
 assert.equal(await invoke(port,'WORK','read_desktop_temporary_conversation'),null);assert.equal((await invoke(port,'WORK','read_desktop_account_sync')).selectedConversationCount,0);
 assert.deepEqual(await invoke(port,'CHAT','read_desktop_temporary_conversation'),result.temporaryBefore);
 result.closes=[await normalClose()];
 stage='source-cold';port=await start(true);await ready(port);assert.deepEqual(await invoke(port,'CHAT','read_desktop_temporary_conversation'),result.temporaryBefore);
 assert.deepEqual((await invoke(port,'CHAT','read_desktop_workspace',{workspaceId:result.workspaceId})).exchange,result.imported.exchange);assert.equal(commits(),1);result.sourceColdExact=true;result.closes.push(await normalClose());
 stage='receiver-restore';port=await start();result.receiverRoot=root;await ready(port);await invoke(port,'CHAT','sign_in_desktop_google_account');
 const restored=await invoke(port,'CHAT','restore_all_desktop_cloud_conversations');assert.equal(restored.restored.length,1);assert.equal(restored.failedCount,0);result.receiverWorkspaceId=restored.restored[0].workspaceId;
 result.receiver=await invoke(port,'CHAT','read_desktop_workspace',{workspaceId:result.receiverWorkspaceId});assert.equal(result.receiver.exchange.conversations[0].id,result.conversationId);
 assert.ok(markers.every(marker=>!JSON.stringify(result.receiver).includes(marker)));assert.equal(await invoke(port,'CHAT','read_desktop_temporary_conversation'),null);
 result.closes.push(await normalClose());stage='receiver-cold';port=await start(true);await ready(port);assert.deepEqual(await invoke(port,'CHAT','read_desktop_workspace',{workspaceId:result.receiverWorkspaceId}),result.receiver);assert.equal(commits(),1);result.receiverColdExact=true;result.closes.push(await normalClose());
 assert.deepEqual(serverErrors,[]);assert.ok(requests.every(request=>!request.privateMarkerPresent));
 result.requests=requests;result.commitCount=commits();result.pids=pids;
 await writeFile(join(result.root,'temporary-consumer-native.json'),JSON.stringify(result,null,2));await writeFile(join(result.root,'synthetic-remote-documents.json'),JSON.stringify([...documents.values()],null,2));
 console.log(JSON.stringify({candidateSha256:receipt.sha256,root:result.root,receiverRoot:result.receiverRoot,temporarySyncRequestDelta:result.temporarySyncRequestDelta,commitCount:result.commitCount,search:result.search,positiveSearchCount:result.positiveSearchCount,exchangeIsolation:result.exchangeIsolation,sourceColdExact:result.sourceColdExact,receiverColdExact:result.receiverColdExact,pids,closes:result.closes}));
 assert.equal(result.temporarySyncRequestDelta,0,'temporary-only identity reached cloud before local rejection');
} catch(error){if(result.root)await writeFile(join(result.root,resumeRoot?`temporary-consumer-resume-failure-${randomUUID()}.json`:'temporary-consumer-failure.json'),JSON.stringify({...result,stage,requests,serverErrors,error:String(error)},null,2));console.error(JSON.stringify({stage,root,pid:launch?.pid,temporarySyncRequestDelta:result.temporarySyncRequestDelta,requestPaths:requests.map(r=>r.path),error:String(error)}));process.exitCode=1;}
finally{stop();server.closeAllConnections();await new Promise(done=>server.close(done));}
