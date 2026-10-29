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
let launch = null;
let root = '';
const stoppedPids = [];
async function start(reopen = false) {
  const port = await reservePort();
  const args = ['-ExecutablePath', executable, '-RootParent', rootParent, '-StartupMode', 'normal', '-Visible', '-DebugPort', String(port)];
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

const diagnosticRoot=resolve(desktopRoot,'../docs/review/20261004-cross-area-reference-chain');
const digestText=x=>createHash('sha256').update(x).digest('hex');
const seed={},drafts={},cases=[];
const readWorkspace=(port,area)=>invoke(port,area,'read_desktop_workspace',{workspaceId:seed[area].workspaceId});
const readDraft=(port,area)=>invoke(port,area,'read_desktop_conversation_draft',{workspaceId:seed[area].workspaceId,conversationId:null});
const readSource=async(port,area)=>(await readWorkspace(port,area)).exchange.conversations.find(c=>c.id===seed[area].conversationId);
const reference=(port,area,conversation,messageId)=>invoke(port,area,'reference_desktop_message_to_other_area',{workspaceId:seed[area].workspaceId,conversationId:conversation.id,messageId,expectedConversationRevision:conversation.revision,targetWorkspaceId:seed[area==='CHAT'?'WORK':'CHAT'].workspaceId});
const mutate=(port,area,action,conversation,fields={})=>invoke(port,area,'mutate_desktop_domain',{args:{intentId:`synthetic-reference-${Date.now()}-${area.toLowerCase()}-${action.toLowerCase()}`,workspaceId:seed[area].workspaceId,entity:'conversation',action,objectId:conversation.id,expectedRevision:conversation.revision,fields}});
let result;
try{
 let port=await start();await until(()=>evaluate(port,'Boolean(window.__TAURI__?.core?.invoke)'),'Native IPC');
 for(const area of ['CHAT','WORK']){
  const workspaceId=(await invoke(port,area,'ensure_desktop_local_workspace')).summary.id;
  const path=join(root,`synthetic-reference-${area}.txt`);const bytes=`${area} private original synthetic attachment\n`;await writeFile(path,bytes,'utf8');
  const attachment=await invoke(port,area,'import_desktop_conversation_attachment',{args:{workspaceId,selectedPath:path}});
  const marker=`${area} synthetic selected text only`;
  const created=await invoke(port,area,'mutate_desktop_domain',{args:{intentId:`synthetic-reference-create-${area.toLowerCase()}`,workspaceId,entity:'conversation',action:'create',fields:{title:`${area} synthetic reference source`,projectId:null,firstMessage:marker,attachmentIds:[attachment.id]}}});
  seed[area]={workspaceId,conversationId:created.objectId,marker,attachment};
  const source=await readSource(port,area);assert.ok(source.messages[0].blocks.some(b=>b.kind!=='TEXT'));seed[area].messageId=source.messages[0].id;seed[area].source=source;
  await invoke(port,area,'save_desktop_conversation_draft',{args:{workspaceId,conversationId:null,text:`${area} existing unsent draft`,attachmentIds:[attachment.id],modelId:'DEEPSEEK_V4_PRO',toneOverride:'professional',webSearchOverride:false}});drafts[area]=await readDraft(port,area);
 }
 for(const area of ['CHAT','WORK']){
  const target=area==='CHAT'?'WORK':'CHAT';const sourceBefore=await readWorkspace(port,area);const targetBefore=await readWorkspace(port,target);const draftBefore=await readDraft(port,target);
  await evaluate(port,'location.reload();true');await until(()=>evaluate(port,'Boolean(globalThis.__nanfengDesktopWorkState?.current&&document.querySelector("#chat-search"))'),'reload');
  await until(async()=>{if(await evaluate(port,`globalThis.__nanfengDesktopWorkState?.dataArea===${JSON.stringify(area)}`))return true;await click(port,`[data-action="show-${area==='CHAT'?'chat':'work'}"]`);return false;},'source area');
  await until(()=>evaluate(port,`Boolean(document.querySelector('[data-action="select-chat"][data-id="${seed[area].conversationId}"]'))`),'source row');assert.equal(await click(port,`[data-action="select-chat"][data-id="${seed[area].conversationId}"]`),true);
  await until(()=>evaluate(port,`document.querySelector('.chat-main')?.innerText.includes(${JSON.stringify(seed[area].marker)})`),'source body');
  assert.equal(await click(port,`[data-action="open-assistant-message-menu"][data-message-id="${seed[area].messageId}"]`),true);
  await until(()=>evaluate(port,'Boolean(document.querySelector("[data-action=reference-message-to-other-area]"))'),'reference menu');assert.equal(await click(port,'[data-action="reference-message-to-other-area"]'),true);
  const visible=await until(()=>evaluate(port,`(()=>{const s=globalThis.__nanfengDesktopWorkState,e=document.querySelector('#chat-composer');return s?.dataArea===${JSON.stringify(target)}&&s.selectedConversationId===null&&e?.value.includes(${JSON.stringify(seed[area].marker)})?e.value:null})()`),'actual target unsent draft');
  const after=await readDraft(port,target);assert.equal(after.text,visible);assert.ok(after.text.startsWith(draftBefore.text));assert.ok(after.text.includes(digestText(seed[area].marker)));assert.ok(after.text.includes(`${area}/${seed[area].conversationId}/${seed[area].messageId}`));assert.ok(!after.text.includes(seed[area].attachment.id));assert.ok(!after.text.includes(seed[area].attachment.displayName));
  assert.deepEqual(after.attachments,draftBefore.attachments);for(const key of ['modelId','toneOverride','webSearchOverride'])assert.equal(after[key],draftBefore[key]);
  assert.deepEqual(await readWorkspace(port,area),sourceBefore);assert.deepEqual(await readWorkspace(port,target),targetBefore);
  cases.push({area,target,actualMenu:true,sourceWorkspaceExact:true,targetWorkspaceExact:true,visibleDraft:visible,draftBefore,draftAfter:after,attachmentNotCopied:true});
 }
 for(const area of ['CHAT','WORK']){
  const target=area==='CHAT'?'WORK':'CHAT';
  const created=await invoke(port,area,'mutate_desktop_domain',{args:{intentId:`synthetic-reference-text-${area.toLowerCase()}`,workspaceId:seed[area].workspaceId,entity:'conversation',action:'create',fields:{title:'Synthetic plain reference source',projectId:null,firstMessage:`${area} synthetic plain old text`}}});
  seed[area].lifecycleId=created.objectId;const readLife=async()=> (await readWorkspace(port,area)).exchange.conversations.find(c=>c.id===created.objectId);
  const before=await readLife();const messageId=before.messages[0].id;const draftBefore=await readDraft(port,target);
  const changed=`${area} synthetic plain explicit newer version`;await mutate(port,area,'editUserMessage',before,{messageId,text:changed});const edited=await readLife();assert.ok(edited.revision>before.revision);
  assert.deepEqual(await readDraft(port,target),draftBefore);await assert.rejects(()=>reference(port,area,before,messageId),/来源已变化/);assert.deepEqual(await readDraft(port,target),draftBefore);
  const editedMessage=edited.messages.find(m=>m.id===edited.currentLeafId);assert.ok(editedMessage.blocks.some(b=>b.text===changed));await reference(port,area,edited,editedMessage.id);const appended=await readDraft(port,target);assert.ok(appended.text.startsWith(draftBefore.text));assert.ok(appended.text.includes(digestText(changed)));assert.deepEqual(await readLife(),edited);assert.deepEqual(appended.attachments,draftBefore.attachments);
  await invoke(port,target,'save_desktop_conversation_draft',{args:{workspaceId:seed[target].workspaceId,conversationId:null,text:'',attachmentIds:appended.attachments.map(a=>a.id),modelId:appended.modelId,toneOverride:appended.toneOverride,webSearchOverride:appended.webSearchOverride}});assert.deepEqual(await readLife(),edited);await reference(port,area,edited,editedMessage.id);
  await mutate(port,area,'softDelete',edited);const deleted=await readLife();const beforeReject=await readDraft(port,target);await assert.rejects(()=>reference(port,area,deleted,editedMessage.id),/会话已不可用/);assert.deepEqual(await readDraft(port,target),beforeReject);await mutate(port,area,'restoreDeleted',deleted);
  cases.push({area,target,sourceEditSnapshotStable:true,staleRevisionRejected:true,editedTextHashExact:true,targetDraftClearSourceExact:true,deletedSourceRejected:true});
 }
 const finalDrafts={};for(const area of ['CHAT','WORK']){finalDrafts[area]=await readDraft(port,area);assert.deepEqual(await readSource(port,area),seed[area].source);}
 await normalClose(port);port=await start(true);await until(()=>evaluate(port,'Boolean(window.__TAURI__?.core?.invoke)'),'cold Native');assert.deepEqual(await snapshot(port),checkpoints[0]);
 for(const area of ['CHAT','WORK'])assert.deepEqual(await readDraft(port,area),finalDrafts[area]);await normalClose(port);
 result={schema:'nanfeng.windows.cross-area-reference-chain.v1',sha256:receipt.sha256,root,seed,cases,finalDrafts,checkpoints,normalCloses,stoppedPids,scope:'Both actual message menus -> other-area unsent draft/source exact/text identity/hash/local attachment and selections preserved/no automatic send; owner IPC source edit/stale/delete refusal/draft clear; NORMAL cold full workspace and drafts exact; synthetic only, no physical input or full project completion claim'};
 await mkdir(diagnosticRoot,{recursive:true});await writeFile(join(diagnosticRoot,'native-current.json'),JSON.stringify(result,null,2)+'\n');console.log(JSON.stringify({ok:true,sha256:receipt.sha256,root,normalCloses:normalCloses.length}));
}catch(error){await mkdir(diagnosticRoot,{recursive:true});await writeFile(join(diagnosticRoot,`native-failed-${Date.now()}.json`),JSON.stringify({root,sha256:receipt.sha256,error:String(error),stack:error.stack,normalCloses,cases},null,2)+'\n');throw error;}finally{stop();}
