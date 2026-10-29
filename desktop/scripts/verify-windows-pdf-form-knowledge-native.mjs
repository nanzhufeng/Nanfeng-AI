import assert from 'node:assert/strict';
import { deflateSync } from 'node:zlib';
import { createHash } from 'node:crypto';
import { execFileSync, spawn } from 'node:child_process';
import { copyFile, mkdir, readFile, writeFile, rename } from 'node:fs/promises';
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



function syntheticPdf() {
  const stream = (dictionary, text, compressed = false) => {
    const bytes = compressed ? deflateSync(Buffer.from(text)) : Buffer.from(text);
    return Buffer.concat([Buffer.from(`<< ${dictionary} ${compressed ? '/Filter /FlateDecode ' : ''}/Length ${bytes.length} >>\nstream\n`), bytes, Buffer.from('\nendstream')]);
  };
  const cmap='/CIDInit /ProcSet findresource begin\n12 dict begin\nbegincmap\n/CIDSystemInfo << /Registry (Adobe) /Ordering (UCS) /Supplement 0 >> def\n/CMapName /Synthetic def\n/CMapType 2 def\n1 begincodespacerange\n<0000> <FFFF>\nendcodespacerange\n2 beginbfchar\n<0001> <4F60>\n<0002> <597D>\nendbfchar\nendcmap\nCMapName currentdict /CMap defineresource pop\nend\nend';
  const objects = [
    '<< /Type /Catalog /Pages 2 0 R >>',
    '<< /Type /Pages /Kids [4 0 R 6 0 R 8 0 R] /Count 3 >>',
    '<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>',
    '<< /Type /Page /Parent 2 0 R /MediaBox [0 0 300 300] /Resources << /Font << /F1 3 0 R >> /XObject << /Outer 12 0 R >> >> /Contents 5 0 R >>',
    stream('', 'BT /F1 12 Tf (Before) Tj ET q /Outer Do Q BT (After) Tj ET /Outer Do'),
    '<< /Type /Page /Parent 2 0 R /MediaBox [0 0 300 300] /Resources << /Font << /F1 3 0 R >> >> /Contents 7 0 R >>', stream('', ''),
    '<< /Type /Page /Parent 2 0 R /MediaBox [0 0 300 300] /Resources << /Font << /F1 3 0 R >> >> /Contents 9 0 R >>',
    stream('', 'BT /F1 12 Tf (Native third page) Tj T* (Third inert body) Tj ET'),
    stream('', cmap),
    stream('/Type /XObject /Subtype /Form /BBox [0 0 300 300] /Resources << /Font << /F1 << /Type /Font /Subtype /Type1 /BaseFont /Helvetica >> >> >>', 'BT /F1 12 Tf (Inner) Tj ET'),
    stream('/Type /XObject /Subtype /Form /BBox [0 0 300 300] /Resources << /Font << /F1 << /Type /Font /Subtype /Type1 /BaseFont /Helvetica /Encoding /WinAnsiEncoding /ToUnicode 10 0 R >> >> /XObject << /Inner 11 0 R >> >>', 'BT /F1 12 Tf <0001> Tj ET /Inner Do BT /F1 12 Tf <0002> Tj ET', true),
  ];
  const parts=[Buffer.from('%PDF-1.5\n')], offsets=[];let length=parts[0].length;
  for(let i=0;i<objects.length;i++) { offsets.push(length);const bytes=Buffer.concat([Buffer.from(`${i+1} 0 obj\n`),Buffer.isBuffer(objects[i])?objects[i]:Buffer.from(objects[i]),Buffer.from('\nendobj\n')]);parts.push(bytes);length+=bytes.length; }
  parts.push(Buffer.from(`xref\n0 ${objects.length+1}\n0000000000 65535 f \n${offsets.map(n=>`${String(n).padStart(10,'0')} 00000 n \n`).join('')}trailer\n<< /Size ${objects.length+1} /Root 1 0 R >>\nstartxref\n${length}\n%%EOF\n`));return Buffer.concat(parts);
}
async function selectPdf(port, path) {
  assert.equal(await click(port,'[data-action=new-knowledge]'),true);
  await until(()=>evaluate(port,'Boolean(document.querySelector("[data-action=select-pdf-knowledge]"))'),'PDF contextual entry');
  assert.equal(await click(port,'[data-action=select-pdf-knowledge]'),true); await until(dialogOpen,'PDF native picker');
  const action=path?'select':'cancel', selected=path?` -SelectedPath ${quote(path)}`:'';
  assert.equal(JSON.parse(ps(`& ${quote(join(desktopRoot,'scripts/operate-windows-file-dialog.ps1'))} -ProcessId ${Number(launch.pid)} -Action ${quote(action)}${selected} -AcceptanceRoot ${quote(root)}`)).action,action);
  await until(()=>!dialogOpen(),'PDF picker closed');
}
const result = { candidateSha256: receipt.sha256, checks: {} };
try {
  let port=await start(); result.root=root;
  await until(()=>evaluate(port,'Boolean(window.__TAURI__?.core?.invoke)'),'initial IPC');
  const work=await invoke(port,'WORK','ensure_desktop_local_workspace'); const workspaceId=work.summary.id; result.workspaceId=workspaceId;
  await workKnowledge(port,workspaceId);
  await selectPdf(port,null); assert.equal(await invoke(port,'WORK','read_latest_desktop_pdf_knowledge',{workspaceId}),null);
  assert.equal(await evaluate(port,'Boolean(document.getElementById("knowledge-title"))'),true); result.checks.pickerCancelNoTask=true;
  assert.equal(await click(port,'[data-action=close-dialog]'),true);
  const input=join(root,'synthetic-pdf-input'); await mkdir(input); const source=join(input,'synthetic.pdf'); await writeFile(source,syntheticPdf());
  await selectPdf(port,source);
  const task=await until(async()=>{const t=await invoke(port,'WORK','read_latest_desktop_pdf_knowledge',{workspaceId});return t && !['SELECTED','PRIVATE_COPIED','PREFLIGHT','EXTRACTING'].includes(t.status)?t:null;},'PDF worker terminal');
  await writeFile(join(rootParent,'pdf-stage-state.json'),JSON.stringify({id:task.id,status:task.status,error:task.safeErrorCode,completedPages:task.completedPages,totalPages:task.totalPages},null,2));
  assert.equal(task.status,'AWAITING_CONFIRMATION'); assert.equal(task.completedPages,3); assert.equal(task.totalPages,3); assert.equal(task.items.length,2); assert.equal(task.items[1].pageNumber,3);
  assert.equal(task.items[0].candidate.body,'Before\n你\nInner\n好\nAfter\n你\nInner\n好');
  assert.equal((await invoke(port,'WORK','read_desktop_workspace',{workspaceId})).exchange.knowledge.length,0);
  result.checks.actualWorkerPendingNoFormal=true; result.firstTaskId=task.id;
  await until(()=>evaluate(port,'Boolean(document.querySelector("[data-action=review-pdf-knowledge]"))'),'PDF page review');
  assert.equal(await click(port,'[data-action=review-pdf-knowledge][data-ordinal="0"]'),true);
  await until(()=>evaluate(port,'Boolean(document.getElementById("pdf-knowledge-body"))'),'PDF review dialog');
  assert.equal(await evaluate(port,`(()=>{const node=document.getElementById('pdf-knowledge-body');node.value='Edited native PDF content';node.dispatchEvent(new Event('input',{bubbles:true}));return node.value})()`),'Edited native PDF content');
  assert.equal(await click(port,'[data-action=confirm-pdf-knowledge]'),true);
  const partial=await until(async()=>{const t=await invoke(port,'WORK','read_desktop_pdf_knowledge',{workspaceId,taskId:task.id});return t.status==='PARTIALLY_COMPLETED'?t:null;},'PDF confirmed first page');
  const formal=await invoke(port,'WORK','read_desktop_workspace',{workspaceId}); assert.equal(formal.exchange.knowledge.length,1); assert.equal(formal.exchange.knowledge[0].body,'Edited native PDF content');result.formalId=partial.items[0].knowledgeId;
  await until(()=>evaluate(port,'Boolean(document.querySelector("[data-action=skip-pdf-knowledge]"))'),'PDF skip remaining');
  assert.equal(await click(port,'[data-action=skip-pdf-knowledge][data-ordinal="1"]'),true);
  await until(async()=> (await invoke(port,'WORK','read_desktop_pdf_knowledge',{workspaceId,taskId:task.id})).status==='COMPLETED','PDF completed decisions'); result.checks.editedConfirmAndSkip=true;
  // A staged private copy represents a real interruption before dispatch. Cold startup
  // must mark it FAILED without parsing; retry must use its copy after source changes.
  const interrupted=await invoke(port,'WORK','stage_desktop_pdf_knowledge',{workspaceId,selectedPath:source});assert.equal(interrupted.status,'PRIVATE_COPIED');result.interruptedTaskId=interrupted.id;
  closeNormally(); await rename(source,source+'.preserved'); await writeFile(source,'external source changed after private copy');
  port=await start(true);await until(()=>evaluate(port,'Boolean(window.__TAURI__?.core?.invoke)'),'cold IPC');await workKnowledge(port,workspaceId);
  const cold=await invoke(port,'WORK','read_desktop_pdf_knowledge',{workspaceId,taskId:task.id});assert.equal(cold.status,'COMPLETED');assert.equal(cold.items[0].knowledgeId,result.formalId);
  const failed=await invoke(port,'WORK','read_desktop_pdf_knowledge',{workspaceId,taskId:interrupted.id});assert.equal(failed.status,'FAILED');assert.equal(failed.safeErrorCode,'INTERRUPTED');assert.equal(failed.items.length,0);
  await until(()=>evaluate(port,`globalThis.__nanfengDesktopWorkState?.pdfKnowledgeTask?.id===${JSON.stringify(interrupted.id)}`),'interrupted PDF task card');
  assert.equal(await click(port,'[data-action=retry-pdf-knowledge]'),true);
  const retried=await until(async()=>{const t=await invoke(port,'WORK','read_desktop_pdf_knowledge',{workspaceId,taskId:interrupted.id});return t.status==='AWAITING_CONFIRMATION'?t:null;},'private PDF retry');assert.equal(retried.retryCount,1);assert.equal(retried.items[0].candidate.body,'Before\n你\nInner\n好\nAfter\n你\nInner\n好');
  await until(()=>evaluate(port,'Boolean(document.querySelector("[data-action=cancel-pdf-knowledge]"))'),'PDF cancel');assert.equal(await click(port,'[data-action=cancel-pdf-knowledge]'),true);
  await until(async()=> (await invoke(port,'WORK','read_desktop_pdf_knowledge',{workspaceId,taskId:interrupted.id})).status==='CANCELLED','PDF cancelled');
  assert.equal((await invoke(port,'WORK','read_desktop_workspace',{workspaceId})).exchange.knowledge.length,1);result.checks.coldRecoveryPrivateRetryCancel=true;
  const chat=await invoke(port,'CHAT','ensure_desktop_local_workspace');assert.equal(await invoke(port,'CHAT','read_latest_desktop_pdf_knowledge',{workspaceId:chat.summary.id}),null);result.checks.peerAreaEmpty=true;
  closeNormally();port=await start(true);await until(()=>evaluate(port,'Boolean(window.__TAURI__?.core?.invoke)'),'final cold IPC');
  assert.equal((await invoke(port,'WORK','read_desktop_pdf_knowledge',{workspaceId,taskId:interrupted.id})).status,'CANCELLED');assert.equal((await invoke(port,'WORK','read_desktop_workspace',{workspaceId})).exchange.knowledge.length,1);result.checks.finalColdPersistence=true;
  closeNormally();
} catch(error) {
  await writeFile(join(rootParent,'pdf-native-failure.json'),JSON.stringify({root,launch,error:String(error),closedWindows,stoppedPids},null,2)); throw error;
} finally {stop();}
console.log(JSON.stringify({...result,closedWindows,stoppedPids}));
