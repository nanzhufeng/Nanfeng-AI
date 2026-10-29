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
const provider=http.createServer(async(request,response)=>{let body='';for await(const chunk of request)body+=chunk.toString('utf8');const payload=JSON.parse(body);requests.push({path:request.url,payload});response.writeHead(200,{'content-type':'text/event-stream; charset=utf-8'});response.end('data: '+JSON.stringify({model:'openai/gpt-5.6-terra',choices:[{delta:{content:`合成风格回答 ${requests.length}`},finish_reason:'stop'}],usage:{prompt_tokens:32,completion_tokens:8,cost:0.000002}})+'\n\ndata: [DONE]\n\n');});
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

const diagnosticRoot=resolve(desktopRoot,'../docs/review/20261004-default-style-send-chain');
let stage='start';const proof={settings:[],preferences:[],normalCloses:[],requests};let workspaceId,conversationId;
async function input(port,selector,value){assert.equal(await evaluate(port,`(()=>{const n=document.querySelector(${JSON.stringify(selector)});if(!n)return false;n.value=${JSON.stringify(value)};n.dispatchEvent(new Event('input',{bubbles:true}));return true})()`),true);}
async function settingsUI(port,tone,instructions){
 await until(()=>evaluate(port,'Boolean(document.querySelector("[data-action=show-settings]"))'),'settings entry');assert.equal(await click(port,'[data-action="show-settings"]'),true);
 await until(()=>evaluate(port,'Boolean(document.querySelector("[data-action=open-settings-page][data-page=personalization]"))'),'personalization page');assert.equal(await click(port,'[data-action="open-settings-page"][data-page="personalization"]'),true);
 await until(()=>evaluate(port,'Boolean(document.querySelector("[data-action=open-settings-picker][data-picker=tone]"))'),'global tone');assert.equal(await click(port,'[data-action="open-settings-picker"][data-picker="tone"]'),true);
 await until(()=>evaluate(port,`Boolean(document.querySelector('[data-action="select-settings-picker-option"][data-picker="tone"][data-value="${tone}"]'))`),'six global choices');assert.equal(await click(port,`[data-action="select-settings-picker-option"][data-picker="tone"][data-value="${tone}"]`),true);
 await until(async()=> (await invoke(port,'CHAT','read_desktop_app_settings')).product.tone===tone,'persisted global tone');
 await input(port,'#personalization-instructions',instructions);await input(port,'#personalization-nickname','SYNTHETIC_STYLE_NICKNAME');assert.equal(await click(port,'[data-action="save-personalization"]'),true);
 const saved=await until(async()=>{const v=await invoke(port,'CHAT','read_desktop_app_settings');return v.product.customInstructions===instructions&&v.product.tone===tone?v:null;},'persisted personalization');assert.equal(saved.product.memoryEnabled,false);assert.equal(saved.product.memoryRetrievalEnabled,false);proof.settings.push(saved);return saved;
}
async function chooseTone(port,tone){
 await until(()=>evaluate(port,'Boolean(document.querySelector("[data-action=toggle-composer-add]"))'),'composer menu');assert.equal(await click(port,'[data-action="toggle-composer-add"]'),true);
 await until(()=>evaluate(port,'Boolean(document.querySelector("[data-action=open-composer-tone-picker]"))'),'style menu');assert.equal(await click(port,'[data-action="open-composer-tone-picker"]'),true);
 await until(()=>evaluate(port,`Boolean(document.querySelector('[data-action="select-conversation-tone"][data-tone="${tone}"]'))`),'six conversation choices');assert.equal(await click(port,`[data-action="select-conversation-tone"][data-tone="${tone}"]`),true);
 if(conversationId){const saved=await until(async()=>{const p=await invoke(port,'CHAT','read_desktop_p6g_selection',{workspaceId,conversationId});return p.conversationOverride.toneOverride===tone?p:null;},'existing conversation tone saved');proof.preferences.push(saved);}
 else await until(async()=>{const d=await invoke(port,'CHAT','read_desktop_conversation_draft',{workspaceId,conversationId:null});return d?.toneOverride===tone;},'new draft tone saved');
}
async function selectConversation(port){if(await evaluate(port,'Boolean(document.querySelector("[data-action=show-chat]"))'))await click(port,'[data-action="show-chat"]');await until(()=>evaluate(port,`Boolean(document.querySelector('[data-action="select-chat"][data-id="${conversationId}"]'))`),'existing conversation');assert.equal(await click(port,`[data-action="select-chat"][data-id="${conversationId}"]`),true);await until(()=>evaluate(port,`globalThis.__nanfengDesktopWorkState?.selectedConversationId===${JSON.stringify(conversationId)}&&Boolean(document.querySelector('#chat-composer'))`),'conversation ready');}
async function send(port,text,count){
 if(text!==null)await input(port,'#chat-composer',text);await until(()=>evaluate(port,'Boolean(document.querySelector("[data-action=save-local-message]:not([disabled])"))'),'send enabled');assert.equal(await click(port,'[data-action="save-local-message"]'),true);
 await until(()=>requests.length===count,'actual loopback request');const conv=await until(async()=>{const w=await invoke(port,'CHAT','read_desktop_workspace',{workspaceId});const c=w.exchange.conversations.find(c=>c.id===conversationId||!conversationId&&c.messages.some(m=>m.blocks.some(b=>b.text==='synthetic first default-style question')));return c?.messages.filter(m=>m.role==='assistant'&&m.delivery==='COMPLETE').length===count?c:null;},'response persisted');conversationId=conv.id;return conv;
}
function requestFact(index,tone,marker){const p=requests[index].payload;assert.equal(p.model,'openai/gpt-5.6-terra');const systems=p.messages.filter(m=>m.role==='system');assert.equal(systems.length,1);const text=typeof systems[0].content==='string'?systems[0].content:JSON.stringify(systems[0].content);assert.ok(text.includes(`对话方式：${tone}。`));assert.equal(text.split('基础风格和语气：').length-1,1);assert.equal(text.split(marker).length-1,1);assert.ok(!text.includes('SYNTHETIC_STYLE_NICKNAME'));return {model:p.model,systemCount:1,tone,customInstructionsOnce:true,nicknameExcluded:true};}
async function answerUI(port,messageId){
 assert.equal(await click(port,`[data-action="open-assistant-message-menu"][data-message-id="${messageId}"]`),true);await until(()=>evaluate(port,'Boolean(document.querySelector("[data-action=show-assistant-answer-information]"))'),'answer facts entry');assert.equal(await click(port,'[data-action="show-assistant-answer-information"]'),true);
 const visible=await until(()=>evaluate(port,'document.querySelector(".answer-information-dialog")?.innerText'),'answer facts');assert.ok(visible.includes('默认'));assert.ok(!visible.includes('直言不讳')&&!visible.includes('风趣搞笑')&&!visible.includes('STYLE_CHAIN_CUSTOM'));assert.equal(await click(port,'.answer-information-dialog [data-action="close-dialog"]'),true);return visible;
}
try{
 let port=await start();proof.root=root;await until(()=>evaluate(port,'Boolean(window.__TAURI__?.core?.invoke&&globalThis.__nanfengDesktopWorkState?.current)'),'Native ready');workspaceId=(await invoke(port,'CHAT','ensure_desktop_local_workspace')).summary.id;proof.workspaceId=workspaceId;
 const initial=await invoke(port,'CHAT','read_desktop_app_settings');await invoke(port,'CHAT','save_desktop_app_settings',{args:{appearance:initial.appearance,product:{...initial.product,memoryEnabled:false,memoryRetrievalEnabled:false,historyLibraryEnabled:false,webSearchEnabled:false},expectedRevision:initial.revision}});
 const catalog=await invoke(port,'CHAT','read_desktop_p6g_catalog');await invoke(port,'CHAT','upsert_desktop_p6g_catalog_candidate',{args:{expectedRevision:catalog.revision,catalogVersion:'synthetic-style-chain',policyVersion:1,candidate:{providerFamily:'OPENAI',providerId:'OPENROUTER',modelId:'GPT_5_6_TERRA',displayName:'GPT-5.6 Terra',tiers:['BALANCED'],capabilities:['TEXT'],available:true,knownCostMicros:null,latencyRank:1,contextWindowTokens:16384,maxOutputTokens:512}}});
 await evaluate(port,'location.reload();true');await until(()=>evaluate(port,'Boolean(document.querySelector("[data-action=show-settings]"))'),'reloaded settings');stage='global settings';await settingsUI(port,'direct','STYLE_CHAIN_CUSTOM_A');
 assert.equal(await click(port,'[data-action="show-chat"]'),true);await until(()=>evaluate(port,'Boolean(document.querySelector("[data-action=new-chat]"))'),'chat sidebar');assert.equal(await click(port,'[data-action="new-chat"]'),true);await until(()=>evaluate(port,'Boolean(document.querySelector("#chat-composer"))'),'new composer');await input(port,'#chat-composer','synthetic first default-style question');
 const draftBefore=await until(async()=>{const d=await invoke(port,'CHAT','read_desktop_conversation_draft',{workspaceId,conversationId:null});return d?.text==='synthetic first default-style question'?d:null;},'new text persisted');stage='new default choice';await chooseTone(port,'professional');await chooseTone(port,'default');const draft=await invoke(port,'CHAT','read_desktop_conversation_draft',{workspaceId,conversationId:null});assert.equal(draft.expiresAtMs,draftBefore.expiresAtMs);proof.newDraft={before:draftBefore,after:draft};assert.equal(requests.length,0);
 await normalClose(port);port=await start(true);await until(()=>evaluate(port,'Boolean(window.__TAURI__?.core?.invoke&&globalThis.__nanfengDesktopWorkState?.current)'),'draft cold ready');assert.deepEqual(await snapshot(port),checkpoints[0]);assert.deepEqual(await invoke(port,'CHAT','read_desktop_conversation_draft',{workspaceId,conversationId:null}),draft);await until(()=>evaluate(port,'document.querySelector("#chat-composer")?.value==="synthetic first default-style question"'),'cold visible text');
 stage='first real send';let conv=await send(port,null,1);proof.firstRequest=requestFact(0,'默认','STYLE_CHAIN_CUSTOM_A');const firstAnswer=conv.messages.find(m=>m.role==='assistant'&&m.delivery==='COMPLETE');proof.firstAnswer=firstAnswer;proof.firstRecords=await invoke(port,'CHAT','read_desktop_ordinary_chat_context_records');
 stage='existing default over changed global';await settingsUI(port,'professional','STYLE_CHAIN_CUSTOM_B');await selectConversation(port);await chooseTone(port,'efficient');await chooseTone(port,'default');conv=await send(port,'synthetic second default-style question',2);proof.secondRequest=requestFact(1,'默认','STYLE_CHAIN_CUSTOM_B');assert.deepEqual(conv.messages.find(m=>m.id===firstAnswer.id),firstAnswer);
 stage='existing direct over changed global';await settingsUI(port,'friendly','STYLE_CHAIN_CUSTOM_C');await selectConversation(port);await chooseTone(port,'direct');conv=await send(port,'synthetic third direct-style question',3);proof.thirdRequest=requestFact(2,'直言不讳','STYLE_CHAIN_CUSTOM_C');assert.deepEqual(conv.messages.find(m=>m.id===firstAnswer.id),firstAnswer);
 proof.completedConversation=conv;proof.finalRecords=await invoke(port,'CHAT','read_desktop_ordinary_chat_context_records');for(const original of proof.firstRecords)assert.deepEqual(proof.finalRecords.find(r=>r.attemptId===original.attemptId),original);
 stage='historical fact separation';const finalSettings=await settingsUI(port,'humorous','STYLE_CHAIN_CUSTOM_D');await selectConversation(port);proof.firstAnswerVisible=await answerUI(port,firstAnswer.id);assert.deepEqual(await invoke(port,'CHAT','read_desktop_ordinary_chat_context_records'),proof.finalRecords);assert.deepEqual((await invoke(port,'CHAT','read_desktop_workspace',{workspaceId})).exchange.conversations.find(c=>c.id===conversationId),conv);
 proof.finalSettings=finalSettings;proof.conversationId=conversationId;await normalClose(port);port=await start(true);await until(()=>evaluate(port,'Boolean(window.__TAURI__?.core?.invoke&&globalThis.__nanfengDesktopWorkState?.current)'),'completed cold');assert.deepEqual(await snapshot(port),checkpoints[1]);assert.deepEqual(await invoke(port,'CHAT','read_desktop_ordinary_chat_context_records'),proof.finalRecords);assert.deepEqual(await invoke(port,'CHAT','read_desktop_app_settings'),finalSettings);await selectConversation(port);proof.coldFirstAnswerVisible=await answerUI(port,firstAnswer.id);assert.equal(proof.coldFirstAnswerVisible,proof.firstAnswerVisible);assert.equal(requests.length,3);await normalClose(port);proof.normalCloses=normalCloses;proof.checkpoints=checkpoints;proof.pass=true;proof.sha256=receipt.sha256;
 await mkdir(diagnosticRoot,{recursive:true});await writeFile(join(diagnosticRoot,'native-current.json'),JSON.stringify(proof,null,2)+'\n');console.log(JSON.stringify({ok:true,sha256:receipt.sha256,root,requests:requests.length,normalCloses:normalCloses.length}));
}catch(error){await mkdir(diagnosticRoot,{recursive:true});await writeFile(join(diagnosticRoot,`native-failed-${Date.now()}.json`),JSON.stringify({...proof,stage,sha256:receipt.sha256,error:String(error),stack:error.stack,normalCloses},null,2)+'\n');throw error;}finally{stop();provider.closeAllConnections();await new Promise(done=>provider.close(done));}
