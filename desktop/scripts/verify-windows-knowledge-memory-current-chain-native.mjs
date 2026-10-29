import assert from 'node:assert/strict';
import { createHash, randomUUID } from 'node:crypto';
import { execFileSync, spawn } from 'node:child_process';
import { mkdir, readFile, writeFile } from 'node:fs/promises';
import http from 'node:http';
import { dirname, join, resolve, sep } from 'node:path';
import { fileURLToPath } from 'node:url';

assert.ok(process.execArgv.includes('--max-old-space-size=2048'));
if (process.platform !== 'win32') throw new Error('仅在 Windows 隔离候选上验收。');
const desktopRoot = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const rootParent = resolve(process.argv[2] || 'D:\\CodexTemp');
const autoAttachmentKind = undefined;
const autoRetryOnly = autoAttachmentKind === 'auto-retry';
const qwenAttachmentRetry = autoAttachmentKind === 'qwen-attachment-retry-ui';
const autoRetryUi = autoAttachmentKind === 'auto-retry-ui' || qwenAttachmentRetry;
const autoRoutePreview = autoAttachmentKind === 'auto-route-preview';
const autoAttachmentOnly = ['auto-attachment', 'auto-pdf', 'auto-image', 'auto-mixed'].includes(autoAttachmentKind);
const receipt = JSON.parse(await readFile(join(desktopRoot, 'output/windows-acceptance/current.json'), 'utf8'));
assert.equal(receipt.identifier, 'com.nanzhufeng.ai.desktop.windowsacceptance');
const source = resolve(desktopRoot, 'output/windows-acceptance', receipt.sha256, 'NanfengAI-Windows-Acceptance.exe');
assert.equal(resolve(receipt.executable), source);
assert.equal(createHash('sha256').update(await readFile(source)).digest('hex').toUpperCase(), receipt.sha256);
const runExecutable = resolve(desktopRoot, 'output/windows-acceptance-binaries', receipt.sha256, 'nanfeng-ai-desktop-spike.exe');
const ps = (args) => execFileSync('pwsh', ['-NoProfile', ...args], { encoding: 'utf8', timeout: 60000 }).trim();
const quote = value => `'${String(value).replaceAll("'", "''")}'`;
const launchPowerShell = (args) => new Promise((resolveLaunch, reject) => {
  const child = spawn('pwsh', ['-NoProfile', ...args], { stdio: ['ignore', 'pipe', 'pipe'] });
  let stdout = '';
  let stderr = '';
  const timer = setTimeout(() => { child.kill(); reject(new Error(`隔离候选启动超时：${stderr}`)); }, 30000);
  child.stdout.on('data', data => {
    stdout += data.toString();
    const line = stdout.slice(0, stdout.lastIndexOf('\n') + 1).split(/\r?\n/)
      .find(item => item.trim().startsWith('{') && item.includes('"pid"'));
    if (!line) return;
    clearTimeout(timer);
    try { const emitted=JSON.parse(line); resolveLaunch(JSON.parse(ps(['-Command',`Get-Content -LiteralPath ${quote(emitted.launchReceipt)} -Raw`]))); } catch (error) { reject(error); }
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
  activePort=port;return port;
};
const requests = [];
const provider = http.createServer((request, response) => {
  const chunks = [];
  request.on('data', chunk => chunks.push(chunk));
  request.on('end', () => {
    try {
      const body = JSON.parse(Buffer.concat(chunks).toString('utf8'));
      const index = requests.length + 1;
      requests.push({ model: body.model, method: request.method, path: request.url, body,
        idempotencyKey: request.headers['idempotency-key'] || '' });
      if ((autoRetryOnly || autoRetryUi) && index === 1) {
        response.writeHead(503, { 'content-type': 'application/json' });
        response.end(JSON.stringify({ error: { message: 'synthetic retry trigger' } }));
        return;
      }
      if (body.stream === false) {
        response.writeHead(200, { 'content-type': 'application/json' });
        response.end(JSON.stringify({ model: body.model,
          choices: [{ message: { content: '{"title":"附件联网重试验收"}' }, finish_reason: 'stop' }],
          usage: { prompt_tokens: 8, completion_tokens: 4, cost: 0.000004 } }));
        return;
      }
      response.writeHead(200, { 'content-type': 'text/event-stream; charset=utf-8', 'cache-control': 'no-cache' });
      if (qwenAttachmentRetry) response.write(`data: ${JSON.stringify({ web_search: [{ url: 'https://example.com/synthetic-source', title: 'Synthetic source' }] })}\n\n`);
      response.write(`data: ${JSON.stringify({ model: body.model, choices: [{ delta: { content: `合成回答 ${index}` } }] })}\n\n`);
      response.write(`data: ${JSON.stringify({ choices: [{ delta: {}, finish_reason: 'stop' }], usage: { prompt_tokens: 8, completion_tokens: 4, cost: 0.000004 } })}\n\n`);
      response.end('data: [DONE]\n\n');
    } catch (error) { response.writeHead(400); response.end(String(error)); }
  });
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
    '-ExecutablePath', source, '-StartupMode', 'normal', '-Visible', '-DebugPort', String(port), '-OrdinaryChatTestEndpoint', `http://127.0.0.1:${providerPort}`];
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
  const tabs = await fetch(`http://127.0.0.1:${port}/json/list`).then(response => response.json());
  const tab = tabs.find(item => item.type === 'page' && item.url === 'http://tauri.localhost/');
  assert.ok(tab, '验收 WebView2 页面未就绪');
  const socket = new WebSocket(tab.webSocketDebuggerUrl);
  try {
    await new Promise((resolveReady, reject) => {
      socket.addEventListener('open', resolveReady, { once: true });
      socket.addEventListener('error', reject, { once: true });
    });
    return await new Promise((resolveResult, reject) => {
      const timer = setTimeout(() => reject(new Error('CDP evaluation timed out')), 20000);
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
  const pid = Number(launch.pid);
  const literal = value => `'${String(value).replaceAll("'", "''")}'`;
  const script = `$expected=${literal(runExecutable)}; $hash=${literal(receipt.sha256)}; `
    + `$p=Get-CimInstance Win32_Process -Filter "ProcessId=${pid}"; `
    + 'if(!$p -or [IO.Path]::GetFullPath($p.ExecutablePath) -ne $expected -or (Get-FileHash -LiteralPath $expected -Algorithm SHA256).Hash -ne $hash){throw "candidate identity mismatch"}; '
    + `$process=Get-Process -Id ${pid}; $handle=$process.Handle; $title=$process.MainWindowTitle; $responding=$process.Responding; if(!$title -or !$responding){throw \"window unavailable\"}; if(!$process.CloseMainWindow()){throw "close rejected"}; `
    + 'if(!$process.WaitForExit(10000)){throw "normal exit timeout"}; if($process.ExitCode -ne 0){throw "normal exit code"}; '
    + '[pscustomobject]@{pid=$process.Id;exitCode=$process.ExitCode;title=$title;responding=$responding;accepted=$true}|ConvertTo-Json -Compress';
  const close = JSON.parse(ps(['-Command', script]));
  await until(()=>ps(['-Command',`@(Get-CimInstance Win32_Process -Filter "Name='msedgewebview2.exe'" | Where-Object {$_.CommandLine -and $_.CommandLine.Contains(${quote(launch.webviewProfile)})}).Count`])==='0','owned profile exit');
  pids.push(pid); launch = null;
  return close;
}
const records = {};
let duplicates;
const relationUi=[];
let activePort;
let beforeChat;
let chatWorkspace;
const coldSnapshots=[];
const closes = [];
let result;
let workspaceId;
async function fill(port, selector, value) {
  assert.equal(await evaluate(port, `(()=>{const node=document.querySelector(${JSON.stringify(selector)});if(!node)return false;node.value=${JSON.stringify(value)};node.dispatchEvent(new Event('input',{bubbles:true}));node.dispatchEvent(new Event('change',{bubbles:true}));return true})()`),true);
}
async function ui(port, selector) {
  await until(()=>evaluate(port, `Boolean(document.querySelector(${JSON.stringify(selector)}))`),selector);
  await clickUi(port,selector);
}
const read = port => invoke(port,'WORK','read_desktop_workspace',{workspaceId});
const record = async(port,entity,id) => (await read(port)).exchange[entity==='relation'?'relations':entity==='knowledge'?'knowledge':'memory'].find(x=>x.id===id);
async function editor(port, entity, id) {
  await ui(port,`[data-action="show-${entity}"]`);
  await ui(port,`[data-action="${id?'edit':'new'}-${entity}"]${id?`[data-id="${id}"]`:''}`);
  await until(()=>evaluate(port, `Boolean(document.querySelector('#${entity}-title'))`),'editor mounted');
}
try {
  let port=await start();await ready(port);
  await ui(port,'[data-action="show-work"]');
  await until(()=>evaluate(port,'globalThis.__nanfengDesktopWorkState?.dataArea==="WORK" && Boolean(globalThis.__nanfengDesktopWorkState?.current?.summary?.id)'),'WORK owner loaded');
  workspaceId=await evaluate(port,'globalThis.__nanfengDesktopWorkState.current.summary.id');
  chatWorkspace=await invoke(port,'CHAT','ensure_desktop_local_workspace');
  beforeChat=await invoke(port,'CHAT','read_desktop_workspace',{workspaceId:chatWorkspace.summary.id});
  for(const entity of ['knowledge','memory']) {
    await editor(port,entity);
    const title=`合成 ${entity} 生命周期`;
    await fill(port,`#${entity}-title`,title);await fill(port,`#${entity}-body`,`${entity} CREATED_TEXT`);
    if(entity==='knowledge')await fill(port,'#knowledge-tags','合成, 生命周期');
    await ui(port,`[data-action="save-${entity}"]`);
    const created=await until(async()=> (await read(port)).exchange[entity==='knowledge'?'knowledge':'memory'].find(x=>x.title===title),'created in native store');
    await until(()=>evaluate(port,`!document.querySelector('#${entity}-title')`),'create editor dismissed');
    await editor(port,entity,created.id);await fill(port,`#${entity}-body`,`${entity} UNSAVED_STALE_EDIT`);
    const competing=await invoke(port,'WORK','mutate_desktop_domain',{args:{intentId:`synthetic-conflict-${randomUUID()}`,workspaceId,entity,action:'update',objectId:created.id,expectedRevision:created.revision,fields:{title,body:`${entity} COMPETING_REVISION`}}});
    await ui(port,`[data-action="save-${entity}"]`);
    await until(()=>evaluate(port,'String(globalThis.__nanfengDesktopWorkState?.error||"").includes("REVISION_CONFLICT")'),'visible revision conflict');
    const current=await record(port,entity,created.id);assert.equal(current.body,`${entity} COMPETING_REVISION`);
    assert.equal(await evaluate(port,`document.querySelector('#${entity}-body')?.value`),`${entity} UNSAVED_STALE_EDIT`,'conflict preserves edit');
    await ui(port,'[data-action="reload-domain-editor"]');await until(()=>evaluate(port,`document.querySelector('#${entity}-body')?.value===${JSON.stringify(current.body)}`),'explicit latest reload');
    assert.equal(await evaluate(port,`document.querySelector('#${entity}-body')?.value`),current.body,'reopened editor latest revision');
    await fill(port,`#${entity}-body`,`${entity} ACCEPTED_EDIT`);await ui(port,`[data-action="save-${entity}"]`);
    const edited=await until(async()=>{const value=await record(port,entity,created.id);return value.body===`${entity} ACCEPTED_EDIT`&&value;},'new revision accepted');
    assert.ok(edited.revision>current.revision);
    await until(()=>evaluate(port,`!document.querySelector('#${entity}-title')`),'edit dismissed');
    await until(()=>evaluate(port,`document.querySelector('[data-action="delete-${entity}"][data-id="${created.id}"]')?.dataset.revision===${JSON.stringify(String(edited.revision))}`),'latest deletion control revision');
    await ui(port,`[data-action="delete-${entity}"][data-id="${created.id}"]`);
    const deleted=await until(async()=>{const value=await record(port,entity,created.id);return (value.status==='DELETED'||value.deleted===true)&&value;},'soft delete');
    records[entity]={id:created.id,title,createdRevision:created.revision,conflictingRevision:current.revision,editedRevision:edited.revision,deletedRevision:deleted.revision,body:edited.body};
  }
  const endpoints=[];
  for(const suffix of ['第一端','第二端']) {
    await editor(port,'knowledge');await fill(port,'#knowledge-title',`合成关系${suffix}`);await fill(port,'#knowledge-body',`${suffix} ENDPOINT_TEXT`);await ui(port,'[data-action="save-knowledge"]');
    endpoints.push(await until(async()=> (await read(port)).exchange.knowledge.find(x=>x.title===`合成关系${suffix}`),'active relation endpoint'));
    await until(()=>evaluate(port,'!document.querySelector("#knowledge-title")'),'endpoint editor dismissed');
  }
  await editor(port,'knowledge');await fill(port,'#knowledge-title',endpoints[0].title);await fill(port,'#knowledge-body',endpoints[0].body);await ui(port,'[data-action="save-knowledge"]');
  const duplicate=await until(async()=> (await read(port)).exchange.knowledge.find(x=>x.id!==endpoints[0].id&&x.title===endpoints[0].title),'duplicate saved');await until(()=>evaluate(port,'!document.querySelector("#knowledge-title")'),'duplicate editor dismissed');
  await editor(port,'knowledge',endpoints[0].id);await fill(port,'#knowledge-body','UNSAVED_DUPLICATE_QUERY_DRAFT');const beforeQuery=await read(port);
  await ui(port,'[data-action="read-knowledge-duplicates"]');duplicates=await until(()=>evaluate(port,'globalThis.__nanfengDesktopWorkState?.dialog?.duplicateResult'),'duplicates actualUI result');
  assert.ok(duplicates.candidates.some(x=>x.knowledgeId===duplicate.id&&x.reasons.includes('EXACT_CONTENT')));
  assert.equal(await evaluate(port,'document.querySelector("#knowledge-body")?.value'),'UNSAVED_DUPLICATE_QUERY_DRAFT');assert.deepEqual(await read(port),beforeQuery);
  assert.equal(await evaluate(port,'document.querySelector(".knowledge-duplicates")?.textContent.includes("规范化内容相同")'),true);
  await ui(port,'.dialog [data-action="close-dialog"]');await editor(port,'knowledge',endpoints[0].id);assert.equal(await evaluate(port,'globalThis.__nanfengDesktopWorkState.dialog.duplicateResult??null'),null);await ui(port,'.dialog [data-action="close-dialog"]');
  await ui(port,'.workspace-content-actions [data-action="new-relation"]');
  await until(()=>evaluate(port,'Boolean(document.querySelector("#relation-from"))'),'relation editor');
  await fill(port,'#relation-from',endpoints[0].id);await fill(port,'#relation-to',endpoints[1].id);await fill(port,'#relation-kind','RELATED');relationUi.push(await evaluate(port,'({from:document.querySelector("#relation-from")?.value,to:document.querySelector("#relation-to")?.value,kind:document.querySelector("#relation-kind")?.value,error:globalThis.__nanfengDesktopWorkState?.error})'));await ui(port,'[data-action="save-relation"]');
  const relation=await until(async()=> (await read(port)).exchange.relations.find(x=>x.status==='ACTIVE'&&x.kind==='RELATED'),'relation created');
  await until(()=>evaluate(port,'!document.querySelector("#relation-from")'),'relation editor dismissed');
  await ui(port,`[data-action="delete-relation"][data-id="${relation.id}"]`);
  const revoked=await until(async()=>{const value=await record(port,'relation',relation.id);return value.status==='REVOKED'&&value;},'relation revoked');
  records.relation={id:relation.id,kind:relation.kind,fromId:relation.fromId,toId:relation.toId,createdRevision:relation.revision,deletedRevision:revoked.revision};
  assert.deepEqual(await invoke(port,'CHAT','read_desktop_workspace',{workspaceId:chatWorkspace.summary.id}),beforeChat);
  coldSnapshots.push(await read(port));closes.push(await normalClose());port=await start(true);await ready(port);assert.deepEqual(await read(port),coldSnapshots.at(-1));
  await ui(port,'[data-action="show-work"]');await until(()=>evaluate(port,'globalThis.__nanfengDesktopWorkState?.dataArea==="WORK" && Boolean(globalThis.__nanfengDesktopWorkState?.current?.summary?.id)'),'cold WORK owner');
  await ui(port,'[data-action="show-settings"]');await ui(port,'[data-action="open-settings-page"][data-page="conversations"]');await ui(port,'[data-action="open-settings-page"][data-page="recycle"]');
  for(const entity of ['knowledge','memory','relation']) {
    const selector=`[data-action="restore"][data-entity="${entity}"][data-id="${records[entity].id}"]`;
    await ui(port,selector);
    const restored=await until(async()=>{const value=await record(port,entity,records[entity].id);return !value.deleted&&value.status==='ACTIVE'&&value;},'restored native record');
    assert.ok(restored.revision>records[entity].deletedRevision);assert.equal(restored.body,records[entity].body);
    records[entity].restoredRevision=restored.revision;
  }
  coldSnapshots.push(await read(port));closes.push(await normalClose());port=await start(true);await ready(port);assert.deepEqual(await read(port),coldSnapshots.at(-1));
  for(const entity of ['knowledge','memory','relation']){const value=await record(port,entity,records[entity].id);assert.equal(value.revision,records[entity].restoredRevision);assert.equal(value.body,records[entity].body);assert.ok(!value.deleted&&value.status==='ACTIVE');if(entity==='relation')assert.deepEqual([value.fromId,value.toId,value.kind],[records.relation.fromId,records.relation.toId,records.relation.kind]);}
  assert.deepEqual(await invoke(port,'CHAT','read_desktop_workspace',{workspaceId:chatWorkspace.summary.id}),beforeChat);assert.equal(requests.length,0);closes.push(await normalClose());
  result={pass:true,candidateSha256:receipt.sha256,root,workspaceId,records,duplicates,relationUi,coldSnapshots,beforeChat,closes,providerRequests:0,forcedCleanup:false,startupMode:'NORMAL',scope:'WORK actual UI CRUD/revision conflict/recycle restore + cold restart, native competitor; synthetic DOM click, no physical pointer or external service claim'};
}catch(error){try{relationUi.push(await evaluate(activePort,'({from:document.querySelector("#relation-from")?.value,to:document.querySelector("#relation-to")?.value,kind:document.querySelector("#relation-kind")?.value,error:globalThis.__nanfengDesktopWorkState?.error})'));}catch{}result={relationUi,pass:false,candidateSha256:receipt.sha256,root,workspaceId,records,closes,error:String(error.stack||error)};process.exitCode=1;}
finally{if(launch){result.forcedCleanup=true;stop();}await new Promise(resolveClose=>provider.close(resolveClose));if(root)await writeFile(join(root,'knowledge-memory-lifecycle-evidence.json'),JSON.stringify(result,null,2),{flag:'wx'});}
await mkdir(resolve(desktopRoot,'../docs/review/20261004-knowledge-memory-current-chain'),{recursive:true});await writeFile(join(desktopRoot,'../docs/review/20261004-knowledge-memory-current-chain',result.pass?'native-current.json':`native-failed-${Date.now()}.json`),JSON.stringify(result,null,2));
console.log(JSON.stringify({pass:result.pass,root,candidateSha256:receipt.sha256,error:result.error,closes:closes.length}));
