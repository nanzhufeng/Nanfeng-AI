import assert from 'node:assert/strict';
import { createHash } from 'node:crypto';
import { execFileSync, spawn } from 'node:child_process';
import { mkdir, readFile, writeFile } from 'node:fs/promises';
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
const failureMeasurementMode = process.argv.includes('--failed-measurement');
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
  response.writeHead(200,{'Content-Type':'text/event-stream'});
  response.write(`data: ${JSON.stringify({model:body.model,choices:[{delta:{content:JSON.stringify(proposal)}}]})}\n\n`);
  response.end(`data: ${JSON.stringify({choices:[{delta:{},finish_reason:failureMeasurementMode?'length':'stop'}],usage:{prompt_tokens:20,completion_tokens:8,cost:0.000007}})}\n\ndata: [DONE]\n\n`);
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
async function until(read, label) {
  const deadline = Date.now() + 30000;
  while (Date.now() < deadline) {
    try { const value = await read(); if (value) return value; } catch { /* Startup or redraw in progress. */ }
    await new Promise(done => setTimeout(done, 200));
  }
  throw new Error(`${label} 未在原生界面出现`);
}
const click = (port, selector) => evaluate(port, `(()=>{const node=document.querySelector(${JSON.stringify(selector)});if(!node)return false;node.click();return true})()`);
let result;
try {
 let port=await start();await until(()=>evaluate(port,'Boolean(window.__TAURI__?.core?.invoke)'),'IPC');
 const workspaceId=(await invoke(port,'ensure_desktop_local_workspace')).summary.id;
 const inputDir=join(root,'synthetic-role-import');await mkdir(inputDir,{recursive:true});const selectedPath=join(inputDir,'conversations.json');
 const node=(role,text,parent,children)=>({parent,children,message:{author:{role},content:{parts:[text]}}});
 await writeFile(selectedPath,JSON.stringify([{id:'synthetic-message-roles',title:'合成消息角色',create_time:1,update_time:2,mapping:{u:node('user','合成用户正文',null,['t']),t:node('tool','合成工具正文','u',['a']),a:node('assistant','合成助手正文','t',[])}}]),'utf8');
 const task=await invoke(port,'stage_chatgpt_export_selected',{selectedPath,workspaceId});
 const conversationId=await invoke(port,'confirm_chatgpt_import_item',{args:{workspaceId,taskId:task.id,itemId:'chatgpt-item-0'}});
 const readConversation=async()=>(await invoke(port,'read_desktop_workspace',{workspaceId})).exchange.conversations.find(x=>x.id===conversationId);
 const conversation=await readConversation();assert.ok(conversation);assert.equal(conversation.messages.length,3);assert.ok(conversation.messages.some(m=>m.role==='tool'));
 const show=async()=>{
  await evaluate(port,'globalThis.__rolesOld=true;location.reload();true');
  await until(()=>evaluate(port,'globalThis.__rolesOld!==true && globalThis.__nanfengDesktopWorkState?.runtimeInfo?.dataAreasReady===true'),'角色页面启动');
  const selector=`[data-action="select-chat"][data-id="${conversationId}"]`;await until(()=>evaluate(port,`Boolean(document.querySelector(${JSON.stringify(selector)}))`),'角色对话入口');assert.equal(await click(port,selector),true);
  await until(()=>evaluate(port,`Boolean(document.querySelector('.chat-scroll[data-conversation-id="${conversationId}"]'))`),'角色对话');
  for(const message of conversation.messages){
   const actions=await evaluate(port,`[...document.querySelectorAll('[data-message-id="${message.id}"][data-action]')].map(n=>n.dataset.action)`);
   assert.ok(actions.includes('copy-message'));assert.equal(actions.includes('share-message'),message.role!=='user');assert.equal(actions.includes('open-assistant-message-menu'),['user','assistant'].includes(message.role));
  }
 };
 await show();stop();port=await start(true);await until(()=>evaluate(port,'Boolean(window.__TAURI__?.core?.invoke)'),'冷启IPC');
 const cold=await readConversation();assert.deepEqual(cold.messages.map(m=>[m.id,m.role,m.blocks]),conversation.messages.map(m=>[m.id,m.role,m.blocks]));await show();
 assert.equal(requests.length,0);
 result={candidateSha256:receipt.sha256,root,importedRoles:conversation.messages.map(m=>m.role),visibleRoleActions:true,coldReadback:true,providerRequests:0,clipboardUntouched:true,pids};
}finally{stop();await new Promise(done=>provider.close(done));}
console.log(JSON.stringify(result));
