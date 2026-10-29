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

assert.ok(resumeRoot, 'existing marked root required');
let result;
try {
 let port=await start(true);await until(()=>evaluate(port,'Boolean(window.__TAURI__?.core?.invoke)'),'native IPC');
 const workspaceId=(await invoke(port,'WORK','ensure_desktop_local_workspace')).summary.id;
 const original=await invoke(port,'WORK','read_latest_desktop_markdown_knowledge',{workspaceId});assert.equal(original.status,'COMPLETED');assert.deepEqual(original.items.map(i=>i.status),['CONFIRMED','SKIPPED']);
 const oldId=original.items[0].knowledgeId;
 const oldBody='edited inert body\n</textarea><script>window.nfaiMarkdownExecuted=true</script>\n[jump](javascript:alert(1))';
 const legacyReplay=await invoke(port,'WORK','confirm_desktop_markdown_knowledge_item',{args:{workspaceId,taskId:original.id,ordinal:0,candidate:{title:'Reviewed Markdown',body:oldBody,tags:['Local','中文']},projectId:null}});assert.equal(legacyReplay.items[0].knowledgeId,oldId);
 const before=await invoke(port,'WORK','read_desktop_workspace',{workspaceId});assert.equal(before.exchange.knowledge.find(k=>k.id===oldId).body,oldBody);
 await workKnowledge(port,workspaceId);
 const path=join(root,'synthetic-markdown-input','two-confirm.md');const body='# One confirmed item\nfirst body\n<!-- nanfeng-ai:knowledge -->\n# Two confirmed item\nsecond body';await writeFile(path,body,'utf8');
 const sourceHash=createHash('sha256').update(body).digest('hex');
 assert.equal(await click(port,'[data-action=new-knowledge]'),true);await until(()=>evaluate(port,'Boolean(document.querySelector("[data-action=select-markdown-knowledge]"))'),'contextual Markdown entry');assert.equal(await click(port,'[data-action=select-markdown-knowledge]'),true);await until(dialogOpen,'actual new batch picker');
 assert.equal(JSON.parse(ps(`& ${quote(join(desktopRoot,'scripts/operate-windows-file-dialog.ps1'))} -ProcessId ${Number(launch.pid)} -Action 'select' -SelectedPath ${quote(path)} -AcceptanceRoot ${quote(root)}`)).action,'select');await until(()=>!dialogOpen(),'new batch picker closed');
 const task=await until(async()=>{const t=await invoke(port,'WORK','read_latest_desktop_markdown_knowledge',{workspaceId});return t.id!==original.id&&t.status==='AWAITING_CONFIRMATION'?t:null},'new batch pending');assert.equal(task.items.length,2);assert.equal(task.sourceHash,sourceHash);
 const ids=[];
 for(const ordinal of [0,1]) {
  await until(()=>evaluate(port,`Array.from(document.querySelectorAll('[data-action=review-markdown-knowledge]')).some(n=>n.dataset.ordinal===${JSON.stringify(String(ordinal))})`),'actual review item');assert.equal(await click(port,`[data-action=review-markdown-knowledge][data-ordinal="${ordinal}"]`),true);
  await until(()=>evaluate(port,'Boolean(document.getElementById("markdown-knowledge-body"))'),'review body');assert.equal(await click(port,'[data-action=confirm-markdown-knowledge]'),true);
  const updated=await until(async()=>{const t=await invoke(port,'WORK','read_desktop_markdown_knowledge',{workspaceId,taskId:task.id});return t.items[ordinal].status==='CONFIRMED'?t:null},'formal item saved');ids.push(updated.items[ordinal].knowledgeId);
  assert.equal((await invoke(port,'WORK','confirm_desktop_markdown_knowledge_item',{args:{workspaceId,taskId:task.id,ordinal,candidate:task.items[ordinal].candidate,projectId:null}})).items[ordinal].knowledgeId,ids.at(-1));
 }
 assert.notEqual(ids[0],ids[1]);const saved=await invoke(port,'WORK','read_desktop_workspace',{workspaceId});assert.equal(saved.exchange.knowledge.length,before.exchange.knowledge.length+2);assert.equal(saved.exchange.knowledge.find(k=>k.id===oldId).body,oldBody);assert.equal(saved.exchange.knowledge.find(k=>k.id===ids[0]).body,'first body');assert.equal(saved.exchange.knowledge.find(k=>k.id===ids[1]).body,'second body');
 closeNormally();port=await start(true);await until(()=>evaluate(port,'Boolean(window.__TAURI__?.core?.invoke)'),'cold IPC');await workKnowledge(port,workspaceId);
 const cold=await invoke(port,'WORK','read_desktop_markdown_knowledge',{workspaceId,taskId:task.id});assert.equal(cold.status,'COMPLETED');assert.deepEqual(cold.items.map(i=>i.knowledgeId),ids);
 await until(()=>evaluate(port,'Boolean(document.querySelector("[data-action=select-markdown-knowledge-task]"))'),'task history selector');
 await evaluate(port,`(()=>{const select=document.querySelector('[data-action=select-markdown-knowledge-task]');select.value=${JSON.stringify(original.id)};select.dispatchEvent(new Event('change',{bubbles:true}));return true})()`);
 await until(()=>evaluate(port,`globalThis.__nanfengDesktopWorkState?.markdownKnowledgeTask?.id===${JSON.stringify(original.id)}`),'actual prior task selected');
 assert.equal((await invoke(port,'WORK','read_desktop_workspace',{workspaceId})).exchange.knowledge.find(k=>k.id===oldId).body,oldBody);
 result={candidateSha256:receipt.sha256,root,workspaceId,taskId:task.id,knowledgeIds:ids,sourceHash,previousTaskId:original.id,previousKnowledgeId:oldId,legacyReplayRetainedId:true,actualPriorTaskSelector:true,baseKnowledgeCount:before.exchange.knowledge.length,finalKnowledgeCount:saved.exchange.knowledge.length};closeNormally();
} finally {stop();}
console.log(JSON.stringify({...result,closedWindows,stoppedPids}));
