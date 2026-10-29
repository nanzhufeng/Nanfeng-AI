import assert from 'node:assert/strict';
import { createHash } from 'node:crypto';
import { execFileSync, spawn } from 'node:child_process';
import { copyFile, mkdir, readFile, writeFile } from 'node:fs/promises';
import { dirname, join, resolve, sep } from 'node:path';
import { fileURLToPath } from 'node:url';

if (process.platform !== 'win32') throw new Error('仅供 Windows 隔离候选验收。');
assert.ok(process.execArgv.includes('--max-old-space-size=2048'));
const desktopRoot = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const rootParent = resolve(process.argv.slice(2).find(arg => !arg.startsWith('--')) || 'D:\\CodexTemp');
const receipt = JSON.parse(await readFile(join(desktopRoot, 'output/windows-acceptance/current.json'), 'utf8'));
assert.equal(receipt.identifier, 'com.nanzhufeng.ai.desktop.windowsacceptance');
const executable = resolve(desktopRoot, 'output/windows-acceptance', receipt.sha256, 'NanfengAI-Windows-Acceptance.exe');
const runningExecutable = resolve(desktopRoot, 'output/windows-acceptance-binaries', receipt.sha256, 'nanfeng-ai-desktop-spike.exe');
assert.equal(resolve(receipt.executable), executable);
assert.equal(createHash('sha256').update(await readFile(executable)).digest('hex').toUpperCase(), receipt.sha256);
const quote = value => `'${String(value).replaceAll("'", "''")}'`;
const ps = script => execFileSync('pwsh', ['-NoProfile', '-Command', script], { encoding: 'utf8', timeout: 60000 }).trim();
async function reservePort() {
  const server = (await import('node:http')).createServer();
  await new Promise(done => server.listen(0, '127.0.0.1', done));
  const port = server.address().port;
  await new Promise(done => server.close(done));
  return port;
}

const http=await import('node:http');const requests=[];
const provider=http.createServer(async(request,response)=>{let body='';for await(const chunk of request)body+=chunk.toString('utf8');const payload=JSON.parse(body);requests.push({path:request.url,payload});response.writeHead(200,{'content-type':'text/event-stream; charset=utf-8'});response.end('data: '+JSON.stringify({model:'openai/gpt-5.6-terra',choices:[{delta:{content:`合成工作回答 ${requests.length}`},finish_reason:'stop'}],usage:{prompt_tokens:32,completion_tokens:8,cost:0.000002}})+'\n\ndata: [DONE]\n\n');});
await new Promise(done=>provider.listen(0,'127.0.0.1',done));const providerPort=provider.address().port;

let launch = null;
let root = '';
const stoppedPids = [];
async function start(reopen = false) {
  const port = await reservePort();
  const args = ['-ExecutablePath', executable, '-RootParent', rootParent, '-StartupMode', 'normal', '-Visible', '-DebugPort', String(port), '-OrdinaryChatTestEndpoint', `http://127.0.0.1:${providerPort}`];
  if (reopen) args.push('-Root', root);
  const result = await new Promise((done, fail) => {
    const child = spawn('pwsh', ['-NoProfile', '-File', join(desktopRoot, 'scripts/start-windows-acceptance.ps1'), ...args], { stdio: ['ignore', 'pipe', 'pipe'] });
    let output = '';
    let error = '';
    const timer = setTimeout(() => { child.kill(); fail(new Error(`启动超时：${error}`)); }, 30000);
    child.stdout.on('data', chunk => {
      output += chunk.toString();
      const line = output.split(/\r?\n/).find(item => item.trim().startsWith('{') && item.includes('"pid"'));
      if (!line) return;
      clearTimeout(timer);
      try { done(JSON.parse(line)); } catch (cause) { fail(cause); }
      child.stdout.destroy();
      child.stderr.destroy();
      child.unref();
    });
    child.stderr.on('data', chunk => { error += chunk.toString(); });
    child.on('error', cause => { clearTimeout(timer); fail(cause); });
    child.on('exit', code => { if (!output.includes('"pid"')) { clearTimeout(timer); fail(new Error(`启动失败 ${code}：${error}`)); } });
  });
  assert.equal(result.sha256, receipt.sha256);
  assert.equal(resolve(result.executable), runningExecutable);
  launch = result;
  root = resolve(result.root);
  assert.ok(root.startsWith(rootParent + sep));
  assert.equal(JSON.parse(await readFile(join(root, '.nanfeng-windows-acceptance.json'), 'utf8')).identifier, receipt.identifier);
  return port;
}
function stop() {
  if (!launch) return;
  const pid = Number(launch.pid);
  ps(`$p=Get-CimInstance Win32_Process -Filter "ProcessId=${pid}"; if ($p) { if ([IO.Path]::GetFullPath([string]$p.ExecutablePath) -ne ${quote(runningExecutable)} -or (Get-FileHash -LiteralPath ${quote(runningExecutable)} -Algorithm SHA256).Hash -ne ${quote(receipt.sha256)}) { throw 'PID identity mismatch' }; Stop-Process -Id ${pid} -Force -ErrorAction Stop }`);
  stoppedPids.push(pid);
  launch = null;
}
async function evaluate(port, expression) {
  const tabs = await fetch(`http://127.0.0.1:${port}/json/list`).then(response => response.json());
  const tab = tabs.find(item => item.type === 'page' && item.url === 'http://tauri.localhost/');
  assert.ok(tab, 'WebView2 页面未就绪');
  const socket = new WebSocket(tab.webSocketDebuggerUrl);
  try {
    await new Promise((done, fail) => { socket.addEventListener('open', done, { once: true }); socket.addEventListener('error', fail, { once: true }); });
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
async function invoke(port, area, command, args = {}) {
  const value = await evaluate(port, `window.__TAURI__.core.invoke(${JSON.stringify(command)},${JSON.stringify(args)},${JSON.stringify({ headers: { 'x-nanfeng-data-area': area } })}).catch(error=>({__error:String(error)}))`);
  if (value?.__error) throw new Error(`${command}: ${value.__error}`);
  return value;
}
async function until(read, label) {
  const deadline = Date.now() + 30000;
  while (Date.now() < deadline) {
    try { const value = await read(); if (value) return value; } catch { /* Startup or redraw. */ }
    await new Promise(done => setTimeout(done, 200));
  }
  throw new Error(`${label} 未出现`);
}
const click = (port, selector) => evaluate(port, `(()=>{const node=document.querySelector(${JSON.stringify(selector)});if(!node)return false;node.click();return true})()`);
const dialogOpen = () => {
  const raw = ps(`& ${quote(join(desktopRoot, 'scripts/inspect-windows-file-dialog.ps1'))} -ProcessId ${Number(launch.pid)}`);
  const windows = raw ? JSON.parse(raw) : [];
  return windows.some(item => item.children?.some(child => child.class === '#32770'));
};


const normalCloses=[],checkpoints=[];
async function snapshot(port){const out={};for(const area of ['CHAT','WORK']){out[area]=[];for(const ws of await invoke(port,area,'list_desktop_workspaces'))out[area].push(await invoke(port,area,'read_desktop_workspace',{workspaceId:ws.id}));}return out;}
async function normalClose(port){
 checkpoints.push(await snapshot(port));const pid=Number(launch.pid);
 const info=JSON.parse(ps(`$p=Get-CimInstance Win32_Process -Filter "ProcessId=${pid}";if(!$p -or [IO.Path]::GetFullPath([string]$p.ExecutablePath) -ne ${quote(runningExecutable)} -or (Get-FileHash -LiteralPath ${quote(runningExecutable)}).Hash -ne ${quote(receipt.sha256)}){throw 'PID identity mismatch'};$n=Get-Process -Id ${pid};@{title=$n.MainWindowTitle;responding=$n.Responding;accepted=$n.CloseMainWindow()}|ConvertTo-Json -Compress`));
 assert.ok(info.title&&info.responding&&info.accepted);await until(()=>ps(`@(Get-CimInstance Win32_Process -Filter "ProcessId=${pid}").Count`)==='0','自然关闭');await until(()=>ps(`@(Get-CimInstance Win32_Process -Filter "Name='msedgewebview2.exe'"|Where-Object {$_.CommandLine -and $_.CommandLine.Contains(${quote(launch.webviewProfile)})}).Count`)==='0','本根WebView退出');stoppedPids.push(pid);normalCloses.push({...info,pid});launch=null;
}


const diagnosticRoot=resolve(desktopRoot,'../docs/review/20261004-work-send-current-chain');
const proof={candidateSha256:receipt.sha256,startupMode:'NORMAL',realService:false,requests,normalCloses,checkpoints};let stage='start';
async function input(port,text){assert.equal(await evaluate(port,`(()=>{const n=document.querySelector('#chat-composer');if(!n)return false;n.value=${JSON.stringify(text)};n.dispatchEvent(new Event('input',{bubbles:true}));return true})()`),true);}
async function workReady(port){await until(()=>evaluate(port,'Boolean(document.querySelector(".chat-sidebar[aria-label=工作导航] #chat-composer")||document.querySelector(".chat-sidebar[aria-label=工作导航]")&&document.querySelector("#chat-composer"))'),'WORK composer');}
async function send(port,workspaceId,text,count){
 await input(port,text);await until(()=>evaluate(port,'Boolean(document.querySelector("[data-action=save-local-message]:not([disabled])"))'),'send enabled');
 const visible=await until(()=>evaluate(port,'document.querySelector(".chat-composer-route[data-route-ready=true]")?.textContent'),'WORK recipient');assert.ok(visible.includes('GPT-5.6 Terra'),visible);
 assert.equal(await click(port,'[data-action="save-local-message"]'),true);
 await until(()=>requests.length===count,'WORK request');
 const conv=await until(async()=>{const w=await invoke(port,'WORK','read_desktop_workspace',{workspaceId});return w.exchange.conversations.find(c=>c.messages.some(m=>m.role==='user'&&m.blocks.some(b=>b.text===text))&&c.messages.some(m=>m.role==='assistant'&&m.delivery==='COMPLETE'));},'WORK result persisted');
 assert.equal(requests[count-1].payload.model,'openai/gpt-5.6-terra');return {conv,visible};
}
try{
 let port=await start();proof.root=root;
 await until(()=>evaluate(port,'Boolean(window.__TAURI__?.core?.invoke&&document.querySelector("[data-action=show-work]"))'),'Native ready');
 const chatBefore=(await snapshot(port)).CHAT;proof.chatBefore=chatBefore;
 assert.equal(await click(port,'[data-action="show-work"]'),true);await workReady(port);
 const workspaceId=(await invoke(port,'WORK','ensure_desktop_local_workspace')).summary.id;proof.workspaceId=workspaceId;
 const settings=await invoke(port,'WORK','read_desktop_app_settings');await invoke(port,'WORK','save_desktop_app_settings',{args:{appearance:settings.appearance,product:{...settings.product,historyLibraryEnabled:false,memoryEnabled:false,memoryRetrievalEnabled:false,webSearchEnabled:false},expectedRevision:settings.revision}});
 const catalog=await invoke(port,'WORK','read_desktop_p6g_catalog');await invoke(port,'WORK','upsert_desktop_p6g_catalog_candidate',{args:{expectedRevision:catalog.revision,catalogVersion:'synthetic-work-send',policyVersion:1,candidate:{providerFamily:'OPENAI',providerId:'OPENROUTER',modelId:'GPT_5_6_TERRA',displayName:'GPT-5.6 Terra',tiers:['BALANCED'],capabilities:['TEXT'],available:true,knownCostMicros:null,latencyRank:1,contextWindowTokens:16384,maxOutputTokens:512}}});
 await evaluate(port,'location.reload();true');await until(()=>evaluate(port,'Boolean(document.querySelector("[data-action=show-work]"))'),'reload nav');
 await until(async()=>{if(await evaluate(port,'Boolean(document.querySelector(".chat-sidebar[aria-label=工作导航]"))'))return true;await click(port,'[data-action="show-work"]');return false;},'WORK navigation settled');await workReady(port);
 stage='unscoped draft';assert.equal(await click(port,'.chat-sidebar[aria-label="工作导航"] [data-action="new-chat"]'),true);await workReady(port);
 await until(()=>evaluate(port,'globalThis.__nanfengDesktopWorkState?.selectedConversationId===null'),'new WORK selection');
 const unscoped='WORK 无项目第一条合成问题';await input(port,unscoped);
 const draft=await until(async()=>{const d=await invoke(port,'WORK','read_desktop_conversation_draft',{workspaceId,conversationId:null});return d?.text===unscoped?d:null;},'WORK draft persisted');
 assert.equal((await invoke(port,'WORK','read_desktop_workspace',{workspaceId})).exchange.conversations.length,0);assert.equal(requests.length,0);assert.deepEqual((await snapshot(port)).CHAT,chatBefore);proof.unscopedDraft=draft;
 await normalClose(port);port=await start(true);await until(()=>evaluate(port,'Boolean(document.querySelector("[data-action=show-work]"))'),'draft cold nav');
 await until(async()=>{if(await evaluate(port,'Boolean(document.querySelector(".chat-sidebar[aria-label=工作导航]"))'))return true;await click(port,'[data-action="show-work"]');return false;},'WORK navigation settled');await workReady(port);
 assert.deepEqual(await snapshot(port),checkpoints[0]);assert.deepEqual(await invoke(port,'WORK','read_desktop_conversation_draft',{workspaceId,conversationId:null}),draft);
 await until(()=>evaluate(port,`document.querySelector('#chat-composer')?.value===${JSON.stringify(unscoped)}`),'cold WORK draft visible');assert.equal(requests.length,0);
 stage='unscoped first send';proof.unscoped=await send(port,workspaceId,unscoped,1);assert.equal(proof.unscoped.conv.projectId,null);assert.equal((await invoke(port,'WORK','read_desktop_workspace',{workspaceId})).exchange.conversations.length,1);assert.deepEqual((await snapshot(port)).CHAT,chatBefore);
 stage='project scope';const project=await invoke(port,'WORK','mutate_desktop_domain',{args:{intentId:'synthetic-work-send-project',workspaceId,entity:'project',action:'create',fields:{title:'合成工作项目',description:'只用于隔离验证'}}});proof.projectId=project.objectId;
 assert.equal(await click(port,'.chat-sidebar[aria-label="工作导航"] [data-action="show-projects"]'),true);
 await until(()=>evaluate(port,`Boolean(document.querySelector('[data-action="select-work-project"][data-project-id="${project.objectId}"]'))`),'project card');assert.equal(await click(port,`[data-action="select-work-project"][data-project-id="${project.objectId}"]`),true);
 await until(()=>evaluate(port,`globalThis.__nanfengDesktopWorkState?.selectedWorkProjectId===${JSON.stringify(project.objectId)}`),'project selected');
 assert.equal(await click(port,'.chat-sidebar[aria-label="工作导航"] [data-action="new-chat"]'),true);await workReady(port);
 const scoped='WORK 项目内第一条合成问题';await input(port,scoped);await until(async()=>{const d=await invoke(port,'WORK','read_desktop_conversation_draft',{workspaceId,conversationId:null});return d?.text===scoped;},'project draft persisted');
 assert.equal((await invoke(port,'WORK','read_desktop_workspace',{workspaceId})).exchange.conversations.length,1);assert.equal(requests.length,1);
 stage='project first send';proof.scoped=await send(port,workspaceId,scoped,2);assert.equal(proof.scoped.conv.projectId,project.objectId);assert.notEqual(proof.scoped.conv.id,proof.unscoped.conv.id);assert.deepEqual((await snapshot(port)).CHAT,chatBefore);
 assert.equal(await evaluate(port,`Boolean(document.querySelector('.chat-sidebar [data-action="select-chat"][data-id="${proof.unscoped.conv.id}"]'))`),false);
 assert.equal(await click(port,'.chat-sidebar[aria-label="工作导航"] [data-action="show-work"]'),true);await until(()=>evaluate(port,`Boolean(document.querySelector('[data-action="select-chat"][data-id="${proof.unscoped.conv.id}"]'))&&Boolean(document.querySelector('[data-action="select-chat"][data-id="${proof.scoped.conv.id}"]'))`),'all WORK conversation rows');
 proof.beforeCold=await snapshot(port);proof.usage=await invoke(port,'WORK','read_desktop_usage_ledger');assert.equal(proof.usage.records.length,2);assert.equal((await invoke(port,'CHAT','read_desktop_usage_ledger')).records.length,0);
 await normalClose(port);port=await start(true);await until(()=>evaluate(port,'Boolean(window.__TAURI__?.core?.invoke&&globalThis.__nanfengDesktopWorkState?.current)'),'completed cold ready');assert.deepEqual(await snapshot(port),proof.beforeCold);assert.equal(requests.length,2);assert.deepEqual(await invoke(port,'WORK','read_desktop_usage_ledger'),proof.usage);
 await until(async()=>{if(await evaluate(port,'Boolean(document.querySelector(".chat-sidebar[aria-label=工作导航]"))'))return true;await click(port,'[data-action="show-work"]');return false;},'WORK navigation settled');
 for(const c of [proof.unscoped.conv,proof.scoped.conv]){await until(()=>evaluate(port,`Boolean(document.querySelector('[data-action="select-chat"][data-id="${c.id}"]'))`),'cold row');assert.equal(await click(port,`[data-action="select-chat"][data-id="${c.id}"]`),true);await until(()=>evaluate(port,`document.querySelector('.chat-main')?.textContent.includes(${JSON.stringify(c.messages.find(m=>m.role==='user').blocks.find(b=>b.kind==='TEXT').text)})`),'cold body visible');}
 assert.equal(requests.length,2);await normalClose(port);proof.pass=true;proof.forcedCleanup=false;
 await mkdir(diagnosticRoot,{recursive:true});await writeFile(join(root,'work-send-current-native.json'),JSON.stringify(proof,null,2));await writeFile(join(diagnosticRoot,'native-current.json'),JSON.stringify(proof,null,2));console.log(JSON.stringify({pass:true,root,requests:requests.length,normalCloses:normalCloses.length}));
}catch(error){await mkdir(diagnosticRoot,{recursive:true});await writeFile(join(diagnosticRoot,`native-failed-${Date.now()}.json`),JSON.stringify({...proof,stage,error:String(error),stack:error.stack},null,2));throw error;}finally{stop();provider.closeAllConnections();await new Promise(done=>provider.close(done));}
