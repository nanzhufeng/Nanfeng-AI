import assert from 'node:assert/strict';
import { createHash } from 'node:crypto';
import { execFileSync, spawn } from 'node:child_process';
import { copyFile, mkdir, readFile, writeFile } from 'node:fs/promises';
import { dirname, join, resolve, sep } from 'node:path';
import { fileURLToPath } from 'node:url';

if (process.platform !== 'win32') throw new Error('仅供 Windows 隔离候选验收。');
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
const resumeRoot = process.argv.find(arg => arg.startsWith('--resume-root='))?.slice('--resume-root='.length);
let root = resumeRoot ? resolve(resumeRoot) : '';
const stoppedPids = [];
const closedWindows = [];
async function start(reopen = false) {
  const port = await reservePort();
  const args = ['-RootParent', rootParent, '-StartupMode', 'normal', '-Visible', '-DebugPort', String(port)];
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
      const timer = setTimeout(() => fail(new Error('CDP evaluation timed out')), 30000);
      socket.addEventListener('message', event => {
        const message = JSON.parse(event.data);
        if (message.id !== 1) return;
        clearTimeout(timer);
        if (message.error || message.result?.exceptionDetails) fail(new Error(message.result?.exceptionDetails?.exception?.description || message.result?.exceptionDetails?.text || message.error?.message));
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
  let route = null;
  if (launch) {
    try {
      route = await evaluate(launch.debugPort, `({
        pane: globalThis.__nanfengDesktopWorkState?.pane,
        settingsSection: globalThis.__nanfengDesktopWorkState?.settingsSection,
        dataArea: globalThis.__nanfengDesktopWorkState?.dataArea,
        workspaceId: globalThis.__nanfengDesktopWorkState?.current?.summary?.id,
        settingsPages: [...document.querySelectorAll('[data-action="open-settings-page"]')].map(x=>x.dataset.page),
        settingsButtonPresent: Boolean(document.querySelector('[data-action="show-settings"]'))
      })`);
    } catch {}
    await writeFile(join(rootParent, `nfai-import-route.${Number(launch.pid)}.json`), JSON.stringify({ label, root, pid: launch.pid, candidateSha256: receipt.sha256, route }, null, 2));
  }
  throw new Error(`${label} 未出现`);
}
const click = (port, selector) => evaluate(port, `(()=>{const node=document.querySelector(${JSON.stringify(selector)});if(!node)return false;node.click();return true})()`);
const dialogOpen = () => {
  const raw = ps(`& ${quote(join(desktopRoot, 'scripts/inspect-windows-file-dialog.ps1'))} -ProcessId ${Number(launch.pid)}`);
  const windows = raw ? JSON.parse(raw) : [];
  return windows.some(item => item.children?.some(child => child.class === '#32770'));
};

function closeNormally() {
  assert.ok(launch);
  const pid = Number(launch.pid);
  const data = JSON.parse(ps(`$p=Get-CimInstance Win32_Process -Filter "ProcessId=${pid}"; if (!$p -or [IO.Path]::GetFullPath([string]$p.ExecutablePath) -ne ${quote(runningExecutable)} -or (Get-FileHash -LiteralPath ${quote(runningExecutable)} -Algorithm SHA256).Hash -ne ${quote(receipt.sha256)}) { throw 'Close PID identity mismatch' }; $w=Get-Process -Id ${pid}; $title=$w.MainWindowTitle; if (!$w.Responding -or !$title.Contains('南枫')) { throw 'Native window is not ready' }; $accepted=$w.CloseMainWindow(); if (!$accepted -or !$w.WaitForExit(15000)) { throw 'Normal close did not exit' }; @{pid=${pid}; title=$title; responding=$true; closeAccepted=$accepted; naturalExit=$true} | ConvertTo-Json -Compress`));
  closedWindows.push(data); launch = null;
}
async function workKnowledge(port, workspaceId) {
  if (await evaluate(port, `globalThis.__nanfengDesktopWorkState?.dataArea !== 'WORK'`)) {
    await until(() => evaluate(port, 'Boolean(document.querySelector("[data-action=show-work]"))'), 'work entry');
    assert.equal(await click(port, '[data-action=show-work]'), true);
  }
  await until(() => evaluate(port, `globalThis.__nanfengDesktopWorkState?.dataArea === 'WORK' && globalThis.__nanfengDesktopWorkState?.current?.summary?.id === ${JSON.stringify(workspaceId)}`), 'actual WORK workspace');
  await until(() => evaluate(port, 'Boolean(document.querySelector("[data-action=show-knowledge]"))'), 'knowledge entry');
  assert.equal(await click(port, '[data-action=show-knowledge]'), true);
  await until(() => evaluate(port, 'Boolean(document.querySelector("[data-action=new-knowledge]"))'), 'knowledge page');
}


function documentFor(body='original body') {
 const item={id:'external-origin',title:'Original JSON',body,tags:['local'],scope:'PROJECT',projectRef:'external-project',revision:'7',hash:'a'.repeat(64),source:{kind:'LOCAL_KNOWLEDGE',summary:'confirmed local knowledge'}};
 return JSON.stringify({format:'nfai.knowledge.json',schemaVersion:'1',exportedAt:'2026-08-13T00:00:00Z',items:[item,{...item,title:'Second JSON',body:'second body'}]});
}
async function selectJson(port,path,selectionRoot=root) {
 assert.equal(await click(port,'[data-action=new-knowledge]'),true);
 await until(()=>evaluate(port,'Boolean(document.querySelector("[data-action=select-json-knowledge]"))'),'contextual JSON entry');
 assert.equal(await click(port,'[data-action=select-json-knowledge]'),true);await until(dialogOpen,'actual JSON picker');
 const action=path?'select':'cancel';const selected=path?` -SelectedPath ${quote(path)}`:'';
 assert.equal(JSON.parse(ps(`& ${quote(join(desktopRoot,'scripts/operate-windows-file-dialog.ps1'))} -ProcessId ${Number(launch.pid)} -Action ${quote(action)}${selected} -AcceptanceRoot ${quote(selectionRoot)}`)).action,action);
 await until(()=>!dialogOpen(),'JSON picker closed');
}
async function reviewConfirm(port,workspaceId,task,ordinal,edited=null) {
 await until(()=>evaluate(port,`Array.from(document.querySelectorAll('[data-action=review-json-knowledge]')).some(n=>n.dataset.ordinal===${JSON.stringify(String(ordinal))})`),'actual JSON review item');
 assert.equal(await click(port,`[data-action=review-json-knowledge][data-ordinal="${ordinal}"]`),true);await until(()=>evaluate(port,'Boolean(document.getElementById("json-knowledge-body"))'),'JSON review body');
 if(edited) await evaluate(port,`(()=>{for(const [id,value] of ${JSON.stringify(Object.entries(edited))}){const n=document.getElementById(id);if(!n)throw Error('editor missing');n.value=value;n.dispatchEvent(new Event('input',{bubbles:true}));}return true})()`);
 assert.equal(await click(port,'[data-action=confirm-json-knowledge]'),true);
 return until(async()=>{const t=await invoke(port,'WORK','read_desktop_json_knowledge',{workspaceId,taskId:task.id});return t.items[ordinal].status==='CONFIRMED'?t:null},'JSON formal item saved');
}


let result;
const targetArgument=process.argv.find(arg=>arg.startsWith('--target-root='))?.slice('--target-root='.length);
assert.ok(resumeRoot&&targetArgument,'Verified existing paired roots required; never restages original sources');
try {
 let port=await start(true);await until(()=>evaluate(port,'Boolean(window.__TAURI__?.core?.invoke)'),'resume IPC');const sourceRoot=root;const targetRoot=resolve(targetArgument);assert.notEqual(targetRoot,sourceRoot);assert.ok(targetRoot.startsWith(rootParent+sep));assert.equal(JSON.parse(await readFile(join(targetRoot,'.nanfeng-windows-acceptance.json'),'utf8')).identifier,receipt.identifier);
 const workspaceId=(await invoke(port,'WORK','ensure_desktop_local_workspace')).summary.id;await workKnowledge(port,workspaceId);
 const task=await invoke(port,'WORK','read_latest_desktop_json_knowledge',{workspaceId});assert.equal(task.status,'COMPLETED');assert.equal(task.items.length,2);assert.ok(task.items.every(i=>i.status==='CONFIRMED'));const originalIds=task.items.map(i=>i.knowledgeId);
 const md=await invoke(port,'WORK','read_latest_desktop_markdown_knowledge',{workspaceId});assert.equal(md.status,'COMPLETED');assert.equal(md.items.length,1);const mdTask=md;const mdId=md.items[0].knowledgeId;
 const before=await invoke(port,'WORK','read_desktop_workspace',{workspaceId});assert.equal(before.exchange.knowledge.length,3);const selected=[originalIds[0],mdId];
await until(()=>evaluate(port,`Array.from(document.querySelectorAll('[data-action=edit-knowledge]')).some(n=>n.dataset.id===${JSON.stringify(originalIds[0])})`),'edit original');assert.equal(await click(port,`[data-action=edit-knowledge][data-id="${originalIds[0]}"]`),true);await until(()=>evaluate(port,'Boolean(document.getElementById("knowledge-body"))'),'original edit dialog');
 await evaluate(port,'(()=>{const n=document.getElementById("knowledge-body");n.value="unsaved draft must not export";n.dispatchEvent(new Event("input",{bubbles:true}));return true})()');assert.equal(await click(port,'[data-action=open-json-knowledge-export]'),true);await until(()=>evaluate(port,'Boolean(document.querySelector("[data-action=save-json-knowledge-export]"))'),'export selection');
 assert.equal(await click(port,`[data-action=toggle-json-knowledge-export][data-id="${mdId}"]`),true);await until(()=>evaluate(port,'globalThis.__nanfengDesktopWorkState?.dialog?.selectedIds?.length===2'),'actual two selected');
 const path=join(targetRoot,'synthetic-json-export','selected.json');assert.equal(await click(port,'[data-action=save-json-knowledge-export]'),true);await until(dialogOpen,'actual save cancel picker');assert.equal(JSON.parse(ps(`& ${quote(join(desktopRoot,'scripts/operate-windows-file-dialog.ps1'))} -ProcessId ${Number(launch.pid)} -Action cancel`)).action,'cancel');await until(()=>!dialogOpen(),'save cancelled');await until(()=>evaluate(port,'globalThis.__nanfengDesktopWorkState?.dialog?.busy===false'),'cancel preserves dialog');assert.equal((await evaluate(port,'globalThis.__nanfengDesktopWorkState.dialog.selectedIds')).length,2);
 await assert.rejects(readFile(path));assert.deepEqual((await invoke(port,'WORK','read_desktop_workspace',{workspaceId})).exchange.knowledge,before.exchange.knowledge);
 await assert.rejects(invoke(port,'WORK','export_desktop_json_knowledge',{workspaceId,knowledgeIds:selected,selectedPath:join(sourceRoot,'synthetic-json-input','private-target.json')}));await assert.rejects(readFile(join(sourceRoot,'synthetic-json-input','private-target.json')));
 assert.equal(await click(port,'[data-action=save-json-knowledge-export]'),true);await until(dialogOpen,'actual save picker');assert.equal(JSON.parse(ps(`& ${quote(join(desktopRoot,'scripts/operate-windows-file-dialog.ps1'))} -ProcessId ${Number(launch.pid)} -Action save -SelectedPath ${quote(path)} -AcceptanceRoot ${quote(targetRoot)}`)).action,'save');await until(()=>!dialogOpen(),'actual save selected');
 const exportReceipt=await until(()=>evaluate(port,'globalThis.__nanfengDesktopWorkState?.jsonKnowledgeExportReceipt || null'),'verified export receipt');const bytes=await readFile(path);const hash=createHash('sha256').update(bytes).digest('hex');assert.equal(exportReceipt.sha256,hash);assert.equal(exportReceipt.byteCount,bytes.length);assert.equal(exportReceipt.manifest.itemCount,2);const exported=JSON.parse(bytes.toString('utf8'));assert.deepEqual(exported.items.map(i=>i.id),[...selected].sort());assert.equal(exported.schemaVersion,'1');assert.ok(exported.items.every(i=>typeof i.revision==='string'));assert.equal(exported.items.find(i=>i.id===originalIds[0]).source.kind,'IMPORTED_TEXT');assert.equal(exported.items.find(i=>i.id===originalIds[0]).source.summary,'safe origin summary');assert.equal(exported.items.find(i=>i.id===mdId).source.kind,'MARKDOWN_IMPORT');assert.ok(!bytes.toString('utf8').includes('unsaved draft must not export'));assert.deepEqual((await invoke(port,'WORK','read_desktop_workspace',{workspaceId})).exchange.knowledge,before.exchange.knowledge);closeNormally();
 port=await start(true);await until(()=>evaluate(port,'Boolean(window.__TAURI__?.core?.invoke)'),'exported cold IPC');await workKnowledge(port,workspaceId);await selectJson(port,path,targetRoot);const roundtrip=await until(async()=>{const t=await invoke(port,'WORK','read_latest_desktop_json_knowledge',{workspaceId});return t.id!==task.id&&t.status==='AWAITING_CONFIRMATION'?t:null},'roundtrip pending');assert.equal(roundtrip.sourceHash,hash);const remapped=[];
 for(const ordinal of [0,1]){const t=await reviewConfirm(port,workspaceId,roundtrip,ordinal);remapped.push(t.items[ordinal].knowledgeId);assert.notEqual(remapped.at(-1),exported.items[ordinal].id);}
 const after=await invoke(port,'WORK','read_desktop_workspace',{workspaceId});assert.equal(after.exchange.knowledge.length,5);for(const k of before.exchange.knowledge)assert.deepEqual(after.exchange.knowledge.find(i=>i.id===k.id),k);for(let ordinal=0;ordinal<2;ordinal++){const k=after.exchange.knowledge.find(i=>i.id===remapped[ordinal]);const old=before.exchange.knowledge.find(i=>i.id===exported.items[ordinal].id);for(const field of ['title','body','tags','scope','projectId','contentHash'])assert.deepEqual(k[field],old[field]);assert.equal(roundtrip.items[ordinal].candidate.source.kind,exported.items[ordinal].source.kind);assert.equal(roundtrip.items[ordinal].candidate.source.summary,exported.items[ordinal].source.summary);}
 closeNormally();port=await start(true);await until(()=>evaluate(port,'Boolean(window.__TAURI__?.core?.invoke)'),'final cold IPC');await workKnowledge(port,workspaceId);const cold=await invoke(port,'WORK','read_desktop_json_knowledge',{workspaceId,taskId:roundtrip.id});assert.equal(cold.status,'COMPLETED');assert.deepEqual(cold.items.map(i=>i.knowledgeId),remapped);assert.deepEqual((await invoke(port,'WORK','read_desktop_workspace',{workspaceId})).exchange.knowledge,after.exchange.knowledge);assert.equal(createHash('sha256').update(await readFile(path)).digest('hex'),hash);
 result={candidateSha256:receipt.sha256,root:sourceRoot,targetRoot,workspaceId,originalJsonTaskId:task.id,markdownTaskId:mdTask.id,roundtripTaskId:roundtrip.id,originalKnowledgeIds:[...originalIds,mdId],selectedIds:selected,remappedIds:remapped,exportPath:path,exportHash:hash,exportReceipt:exportReceipt,actualCheckboxSelection:true,actualSaveCancel:true,actualSavePicker:true,actualReimportPicker:true,unsavedDraftExcluded:true,privateTargetRefused:true,originalsUnchanged:true,baseKnowledgeCount:3,finalKnowledgeCount:5};closeNormally();
} finally {stop();}
console.log(JSON.stringify({...result,closedWindows,stoppedPids}));
