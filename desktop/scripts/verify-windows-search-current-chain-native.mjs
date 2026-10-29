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

const evidence=resolve(desktopRoot,'../docs/review/20261004-search-current-chain');const facts={areas:{}};let stage='start';async function persistCheckpoint(label){if(!root)return;const m=process.memoryUsage();await writeFile(join(root,`search-chain-checkpoint-${Number(launch?.pid||stoppedPids.at(-1))}-${Date.now()}.json`),JSON.stringify({...facts,stage,label,nodePid:process.pid,memory:{rss:m.rss,heapUsed:m.heapUsed,external:m.external},candidateSha256:receipt.sha256,normalCloses},null,2)+'\n');}const hash=b=>createHash('sha256').update(b).digest('hex');
async function ready(port){await until(()=>evaluate(port,'Boolean(window.__TAURI__?.core?.invoke&&globalThis.__nanfengDesktopWorkState?.current&&globalThis.__nanfengDesktopWorkState?.runtimeInfo?.dataAreasReady)'),'NORMAL ready');}
async function areaUI(port,area){if(await evaluate(port,'Boolean(document.querySelector("[data-action=close-search]"))')){await click(port,'[data-action="close-search"]');await evaluate(port,'new Promise(done=>requestAnimationFrame(()=>requestAnimationFrame(()=>done(true))))');}if(await evaluate(port,`globalThis.__nanfengDesktopWorkState.dataArea!==${JSON.stringify(area)}`)){if(!await evaluate(port,`Boolean(document.querySelector('[data-action="show-${area==='WORK'?'work':'chat'}"]'))`)){await evaluate(port,'location.reload();true');await new Promise(ok=>setTimeout(ok,500));await ready(port);}await until(()=>evaluate(port,`Boolean(document.querySelector('[data-action="show-${area==='WORK'?'work':'chat'}"]'))`),'area switch visible');assert.equal(await click(port,area==='WORK'?'[data-action="show-work"]':'[data-action="show-chat"]'),true);}await until(()=>evaluate(port,`globalThis.__nanfengDesktopWorkState.dataArea===${JSON.stringify(area)}&&!!document.querySelector('#chat-search')`),'area search ready');}
async function search(port,query){const ok=await evaluate(port,`(()=>{const i=document.querySelector('#full-search-input')||document.querySelector('#chat-search');if(!i)return false;i.value=${JSON.stringify(query)};i.dispatchEvent(new Event('input',{bubbles:true}));i.dispatchEvent(new KeyboardEvent('keydown',{key:'Enter',bubbles:true,cancelable:true}));return true})()`);assert.ok(ok);await until(()=>evaluate(port,`globalThis.__nanfengDesktopWorkState.searchPanel&&!globalThis.__nanfengDesktopWorkState.searchLoading&&globalThis.__nanfengDesktopWorkState.chatSearch===${JSON.stringify(query)}`),'search result');return evaluate(port,'globalThis.__nanfengDesktopWorkState.searchPage');}
async function category(port,c){assert.equal(await click(port,`[data-action="set-search-category"][data-category="${c}"]`),true);await evaluate(port,'new Promise(done=>requestAnimationFrame(()=>requestAnimationFrame(()=>done(true))))');await until(()=>evaluate(port,`globalThis.__nanfengDesktopWorkState.searchCategory===${JSON.stringify(c)}&&!globalThis.__nanfengDesktopWorkState.searchLoading`),'category');return evaluate(port,'globalThis.__nanfengDesktopWorkState.searchPage');}
const card=hit=>`[data-search-entry-id="${hit.entryId}"]`;
async function menu(port,hit){assert.equal(await evaluate(port,`(()=>{const c=document.querySelector(${JSON.stringify(card(hit))});if(!c)return false;c.dispatchEvent(new MouseEvent('contextmenu',{bubbles:true,cancelable:true}));return true})()`),true);await until(()=>evaluate(port,'Boolean(document.querySelector("[data-action=locate-search-attachment]"))'),'exact attachment menu');}
let requestId=990000;const query=(port,area,q,c='all')=>invoke(port,area,'query_desktop_local_index',{args:{query:q,category:c,sortMode:'default',fileType:'all',offset:0,recordHistory:false,historyWorkspaceId:null},requestId:++requestId});
try{
 let port=await start();facts.root=root;await ready(port);
 for(const area of ['CHAT','WORK']){const workspaceId=(await invoke(port,area,'ensure_desktop_local_workspace')).summary.id,needle=`检索闭环${area}`;const path=join(root,`${needle}.txt`),bytes=Buffer.from(`SYNTHETIC-SEARCH-${area}\n附件正文保留`);await writeFile(path,bytes,{flag:'wx'});const attachment=await invoke(port,area,'import_desktop_conversation_attachment',{args:{workspaceId,selectedPath:path}});const conversations=[];for(let i=0;i<2;i++){const made=await invoke(port,area,'mutate_desktop_domain',{args:{intentId:`synthetic-search-${area.toLowerCase()}-${i}`,workspaceId,entity:'conversation',action:'create',fields:{title:`${needle} 会话${i}`,projectId:null,firstMessage:`${needle} 中文 English Markdown **原正文${i}**`,attachmentIds:[attachment.id]}}});conversations.push(made.objectId);}facts.areas[area]={workspaceId,needle,attachment,conversations,assetHash:hash(bytes)};}
 facts.before=await snapshot(port);await persistCheckpoint('synthetic source ready');await evaluate(port,'location.reload();true');await new Promise(ok=>setTimeout(ok,500));await ready(port);
 for(const area of ['CHAT','WORK']){stage=area+' actual search categories';await areaUI(port,area);const x=facts.areas[area];await search(port,x.needle);const all=await category(port,'all');assert.equal(all.hits.filter(h=>['TITLE','TEXT'].includes(h.contentKind)).length,4);assert.equal(all.hits.filter(h=>h.attachmentId===x.attachment.id).length,2);assert.ok(all.hits.every(h=>h.workspaceId===x.workspaceId));assert.ok(await evaluate(port,'document.querySelectorAll(".desktop-search-text-card mark").length>0'));x.initialHits=all.hits;
 const text=await category(port,'text');assert.equal(text.hits.length,4);const hit=text.hits.find(h=>h.messageId&&h.conversationId===x.conversations[1]);assert.ok(hit);assert.equal(await click(port,`[data-action="open-search-result"][data-entry-id="${hit.entryId}"]`),true);await until(()=>evaluate(port,`!globalThis.__nanfengDesktopWorkState.searchPanel&&globalThis.__nanfengDesktopWorkState.selectedConversationId===${JSON.stringify(hit.conversationId)}&&globalThis.__nanfengDesktopWorkState.searchAnchorMessageId===${JSON.stringify(hit.messageId)}`),'exact text locate');x.textLocateExact=true;await persistCheckpoint(area+' text locate');assert.equal(await click(port,'[data-action="return-to-search"]'),true);await until(()=>evaluate(port,'globalThis.__nanfengDesktopWorkState.searchPanel'),'return search');
 for(const c of ['image','video','audio']){const page=await category(port,c);assert.equal(page.hits.length,0);}let file=await category(port,'file');assert.equal(file.hits.length,2);assert.equal(await click(port,'[data-action="toggle-search-file-types"]'),true);await until(()=>evaluate(port,'Boolean(document.querySelector("[data-action=set-search-file-type][data-file-type=txt]"))'),'TXT type');assert.equal(await click(port,'[data-action="set-search-file-type"][data-file-type="txt"]'),true);await until(()=>evaluate(port,'globalThis.__nanfengDesktopWorkState.searchFileType==="txt"&&!globalThis.__nanfengDesktopWorkState.searchLoading'),'TXT selected');file=await evaluate(port,'globalThis.__nanfengDesktopWorkState.searchPage');assert.equal(file.hits.length,2);const first=file.hits.find(h=>h.conversationId===x.conversations[0]),second=file.hits.find(h=>h.conversationId===x.conversations[1]);assert.ok(first&&second);
 await menu(port,second);assert.equal(await click(port,'[data-action="locate-search-attachment"]'),true);await until(()=>evaluate(port,`!globalThis.__nanfengDesktopWorkState.searchPanel&&globalThis.__nanfengDesktopWorkState.selectedConversationId===${JSON.stringify(second.conversationId)}&&globalThis.__nanfengDesktopWorkState.searchAnchorMessageId===${JSON.stringify(second.messageId)}&&globalThis.__nanfengDesktopWorkState.searchAnchorAttachmentId===${JSON.stringify(second.attachmentId)}`),'exact attachment locate');x.attachmentLocateExact=true;await persistCheckpoint(area+' exact attachment locate');assert.equal(await click(port,'[data-action="return-to-search"]'),true);await until(()=>evaluate(port,'globalThis.__nanfengDesktopWorkState.searchPanel'),'return attachment result');
 stage=area+' real attachment removal';const beforeCancel=await snapshot(port);await menu(port,first);assert.equal(await click(port,'[data-action="ask-delete-search-attachment"]'),true);await until(()=>evaluate(port,'Boolean(document.querySelector("[data-action=confirm-delete-search-attachment]"))'),'delete confirm');assert.equal(await click(port,'[data-action="close-dialog"]'),true);assert.deepEqual(await snapshot(port),beforeCancel);x.deleteCancelReadOnly=true;await menu(port,first);await click(port,'[data-action="ask-delete-search-attachment"]');await until(()=>evaluate(port,'Boolean(document.querySelector("[data-action=confirm-delete-search-attachment]"))'),'actual delete confirm');assert.equal(await click(port,'[data-action="confirm-delete-search-attachment"]'),true);await until(()=>evaluate(port,`!globalThis.__nanfengDesktopWorkState.dialog&&!globalThis.__nanfengDesktopWorkState.searchLoading&&globalThis.__nanfengDesktopWorkState.searchPage.hits.length===1`),'exact occurrence removed');const remaining=await query(port,area,x.needle,'file');assert.equal(remaining.hits.length,1);assert.equal(remaining.hits[0].conversationId,second.conversationId);x.oneSharedReferenceRetained=true;await persistCheckpoint(area+' exact removal');
 stage=area+' stale exact reference';const projection=await invoke(port,area,'read_desktop_workspace',{workspaceId:x.workspaceId}),conv=projection.exchange.conversations.find(c=>c.id===second.conversationId);await invoke(port,area,'mutate_desktop_domain',{args:{intentId:`synthetic-search-stale-${area.toLowerCase()}`,workspaceId:x.workspaceId,entity:'conversation',action:'removeAttachment',objectId:conv.id,expectedRevision:conv.revision,fields:{messageId:second.messageId,attachmentId:x.attachment.id}}});await menu(port,second);assert.equal(await click(port,'[data-action="locate-search-attachment"]'),true);await until(()=>evaluate(port,'document.querySelector(".desktop-search-state.error")?.textContent.includes("附件引用已删除")'),'stale result safe failure');assert.equal(await evaluate(port,'globalThis.__nanfengDesktopWorkState.searchPanel'),true);assert.equal((await query(port,area,x.needle,'file')).hits.length,0);x.staleReferenceSafe=true;await persistCheckpoint(area+' stale reference refusal');
 stage=area+' title revision index invalidation';const now=await invoke(port,area,'read_desktop_workspace',{workspaceId:x.workspaceId}),c=now.exchange.conversations.find(c=>c.id===x.conversations[0]);const title=`修订索引${area}`;await invoke(port,area,'mutate_desktop_domain',{args:{intentId:`synthetic-search-rename-${area.toLowerCase()}`,workspaceId:x.workspaceId,entity:'conversation',action:'update',objectId:c.id,expectedRevision:c.revision,fields:{title}}});await category(port,'text');const revised=await search(port,title);assert.equal(revised.hits.length,1);assert.equal(revised.hits[0].conversationId,c.id);assert.ok(await evaluate(port,'document.querySelector(".desktop-search-text-card mark")?.textContent.startsWith("修订索引")'));x.revisedTitle=title;x.renameIndexExact=true;await persistCheckpoint(area+' revised title search');
 assert.equal(hash(await readFile(join(area==='CHAT'?root:join(root,'work-area'),'assets',x.assetHash))),x.assetHash);
 }
 await persistCheckpoint('before final source snapshot');facts.after=await snapshot(port);await persistCheckpoint('before natural close');await normalClose(port);port=await start(true);await ready(port);assert.deepEqual(await snapshot(port),checkpoints[0]);await evaluate(port,'location.reload();true');await new Promise(ok=>setTimeout(ok,500));await ready(port);
 for(const area of ['CHAT','WORK']){const x=facts.areas[area];await areaUI(port,area);await search(port,x.revisedTitle);const p=await category(port,'text');assert.equal(p.hits.length,1);assert.equal(p.hits[0].conversationId,x.conversations[0]);assert.equal((await query(port,area,x.needle,'file')).hits.length,0);assert.equal(hash(await readFile(join(area==='CHAT'?root:join(root,'work-area'),'assets',x.assetHash))),x.assetHash);x.coldSearchExact=true;}
 await normalClose(port);facts.normalCloses=normalCloses;facts.sha256=receipt.sha256;facts.pass=true;await writeFile(join(evidence,'native-current.json'),JSON.stringify(facts,null,2)+'\n');console.log(JSON.stringify({ok:true,root,sha256:receipt.sha256,normalCloses:normalCloses.length}));
}catch(error){await writeFile(join(evidence,`native-failed-${Date.now()}.json`),JSON.stringify({...facts,stage,error:String(error),stack:error.stack,sha256:receipt.sha256,normalCloses},null,2)+'\n');throw error;}finally{stop();}
