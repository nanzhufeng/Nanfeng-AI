import assert from 'node:assert/strict';
import { createHash } from 'node:crypto';
import { spawn, execFileSync } from 'node:child_process';
import { readFile, writeFile, stat, mkdir, copyFile } from 'node:fs/promises';
import { dirname, resolve, join, sep } from 'node:path';
import { fileURLToPath } from 'node:url';
import http from 'node:http';
import net from 'node:net';

assert.equal(process.platform, 'win32');
const desktop = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const parent = resolve(process.argv[2] || 'D:/CodexTemp/nfa-chat-pdf-preview-cancel');
const compare = false;assert.ok(process.execArgv.includes('--max-old-space-size=2048'));
const visible = true;
assert.ok(!(visible && compare));
const receipt = JSON.parse(await readFile(join(desktop, 'output/windows-acceptance/current.json'), 'utf8'));
assert.equal(receipt.identifier, 'com.nanzhufeng.ai.desktop.windowsacceptance');
assert.equal(createHash('sha256').update(await readFile(receipt.executable)).digest('hex').toUpperCase(), receipt.sha256);
const executable = resolve(desktop, 'output/windows-acceptance-binaries', receipt.sha256, 'nanfeng-ai-desktop-spike.exe');
const helper = join(desktop, 'scripts/windows-acceptance-cdp-eval.mjs');
let requests = 0;const responses=[];const closes=[];const partialText='SYNTHETIC_STREAM_STOP_PARTIAL 完整保留片段';
const provider = http.createServer(async (request, response) => {
  for await (const _ of request) { /* Never log body. */ }
  requests++;
  response.writeHead(200, { 'Content-Type': 'text/event-stream' });
  response.write(`data: ${JSON.stringify({choices:[{delta:{content:partialText},finish_reason:null}]})}\n\n`);responses.push(response);
});
await new Promise(done => provider.listen(0, '127.0.0.1', done));
const endpoint = `http://127.0.0.1:${provider.address().port}`;
let launch, root, port;
const pids = [];
const windows = [];
const quote = value => `'${String(value).replaceAll("'", "''")}'`;
const ps = code => execFileSync('pwsh', ['-NoProfile', '-Command', `[Console]::OutputEncoding=[Text.UTF8Encoding]::new($false);${code}`], { encoding: 'utf8' }).trim();
async function freePort() {
  const server = net.createServer();
  await new Promise(done => server.listen(0, '127.0.0.1', done));
  const value = server.address().port;
  await new Promise(done => server.close(done));
  return value;
}
async function until(read, label) {
  const deadline = Date.now() + 30000;
  while (Date.now() < deadline) {
    try { const value = await read(); if (value) return value; } catch { /* Startup only. */ }
    await new Promise(done => setTimeout(done, 100));
  }
  throw new Error(label);
}
async function evaluate(expression) {
  return await new Promise((done, fail) => {
    const child = spawn(process.execPath, ['--max-old-space-size=2048', helper, String(port)], { windowsHide: true });
    let output = '', errors = '';
    child.stdout.on('data', chunk => output += chunk);
    child.stderr.on('data', chunk => errors += chunk);
    child.on('error', fail);
    child.on('close', code => { try { code === 0 ? done(JSON.parse(output)) : fail(new Error(errors)); } catch (error) { fail(error); } });
    child.stdin.end(expression);
  });
}
async function invoke(command, args = {}) {
  const value = await evaluate(`window.__TAURI__.core.invoke(${JSON.stringify(command)},${JSON.stringify(args)},{headers:{'x-nanfeng-data-area':'CHAT'}}).catch(error=>({__error:String(error)}))`);
  assert.ok(!value?.__error, `${command}: ${value?.__error}`);
  return value;
}
async function start(reopen = false) {
  port = await freePort();
  const code = `[Console]::OutputEncoding=[Text.UTF8Encoding]::new($false); & ${quote(join(desktop, 'scripts/start-windows-acceptance.ps1'))} -RootParent ${quote(parent)} -ExecutablePath ${quote(receipt.executable)} -StartupMode normal -Visible -DebugPort ${port}${reopen ? ` -Root ${quote(root)}` : ''}`;
  const args = ['-NoProfile', '-Command', code];
  launch = await new Promise((done, fail) => {
    const child = spawn('pwsh', args, { windowsHide: true });
    let output = '', errors = '';
    child.stdout.setEncoding('utf8'); child.stderr.setEncoding('utf8');
    const timer = setTimeout(() => { child.kill(); fail(new Error(`launcher timeout: ${errors}`)); }, 30000);
    child.stdout.on('data', chunk => {
      output += chunk;
      const line = output.split(/\r?\n/).find(item => item.trim().startsWith('{') && item.includes('"pid"'));
      if (!line) return;
      try { const value = JSON.parse(line); clearTimeout(timer); child.stdout.destroy(); child.stderr.destroy(); child.unref(); done(value); } catch {}
    });
    child.stderr.on('data', chunk => errors += chunk);
    child.on('error', error => { clearTimeout(timer); fail(error); });
    child.on('close', code => { if (!output.includes('"pid"')) { clearTimeout(timer); fail(new Error(`launcher ${code}: ${errors}`)); } });
  });
  assert.equal(launch.sha256, receipt.sha256);
  assert.equal(resolve(launch.executable), executable);
  root = resolve(launch.root);
  assert.ok(root.startsWith(parent + sep));
  assert.equal(JSON.parse(await readFile(join(root, '.nanfeng-windows-acceptance.json'), 'utf8')).identifier, receipt.identifier);
  await until(() => evaluate('Boolean(window.__TAURI__?.core?.invoke)'), 'native IPC unavailable');
  await until(() => evaluate('globalThis.__nanfengDesktopWorkState?.runtimeInfo?.dataAreasReady===true'), 'native data areas unavailable');
  const window = JSON.parse(ps(`$p=Get-Process -Id ${Number(launch.pid)} -ErrorAction Stop;@{title=$p.MainWindowTitle;responding=$p.Responding}|ConvertTo-Json -Compress`));
  assert.ok(window.title.includes('南枫 AI'), 'native window title mismatch');
  assert.equal(window.responding, true); windows.push(window);
}
function stop() {
  if (!launch) return;
  const pid = Number(launch.pid);
  ps(`$p=Get-CimInstance Win32_Process -Filter 'ProcessId=${pid}';if($p){if([IO.Path]::GetFullPath([string]$p.ExecutablePath) -ne ${quote(executable)} -or (Get-FileHash -LiteralPath ${quote(executable)} -Algorithm SHA256).Hash -ne ${quote(receipt.sha256)}){throw 'PID mismatch'};Stop-Process -Id ${pid} -Force}`);
  pids.push(pid); launch = null;
}
async function normalClose(){
 const pid=Number(launch.pid);const value=JSON.parse(ps(`$p=Get-CimInstance Win32_Process -Filter 'ProcessId=${pid}';if(!$p -or [IO.Path]::GetFullPath($p.ExecutablePath) -ne ${quote(executable)} -or (Get-FileHash -LiteralPath ${quote(executable)} -Algorithm SHA256).Hash -ne ${quote(receipt.sha256)}){throw 'identity'};$v=Get-Process -Id ${pid};$handle=$v.Handle;$title=$v.MainWindowTitle;$responding=$v.Responding;if(!$title -or !$responding -or !$v.CloseMainWindow()){throw 'window close'};if(!$v.WaitForExit(10000) -or $v.ExitCode -ne 0){throw 'normal exit'};@{title=$title;responding=$responding;pid=${pid};exitCode=$v.ExitCode;accepted=$true}|ConvertTo-Json -Compress`));
 await until(()=>ps(`@(Get-CimInstance Win32_Process -Filter "Name='msedgewebview2.exe'" | Where-Object {$_.CommandLine -and $_.CommandLine.Contains(${quote(launch.webviewProfile)})}).Count`)==='0','owned profile exit');pids.push(pid);launch=null;closes.push(value);
}
const ui=async selector=>{await until(()=>evaluate(`Boolean(document.querySelector(${JSON.stringify(selector)}))`),'UI '+selector);assert.equal(await evaluate(`document.querySelector(${JSON.stringify(selector)}).click();true`),true);};
function twoPagePdf() {
  const first = 'BT /F1 16 Tf 72 720 Td (W08 synthetic PDF page one) Tj ET';
  const second = 'BT /F1 16 Tf 72 720 Td (W08 synthetic PDF page two) Tj ET';
  const objects = [
    '<< /Type /Catalog /Pages 2 0 R >>',
    '<< /Type /Pages /Kids [3 0 R 4 0 R] /Count 2 >>',
    '<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] /Resources << /Font << /F1 7 0 R >> >> /Contents 5 0 R >>',
    '<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] /Resources << /Font << /F1 7 0 R >> >> /Contents 6 0 R >>',
    `<< /Length ${Buffer.byteLength(first)} >>\nstream\n${first}\nendstream`,
    `<< /Length ${Buffer.byteLength(second)} >>\nstream\n${second}\nendstream`,
    '<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>',
  ];
  let body = '%PDF-1.4\n';
  const offsets = [];
  for (const [index, object] of objects.entries()) {
    offsets.push(Buffer.byteLength(body));
    body += `${index + 1} 0 obj\n${object}\nendobj\n`;
  }
  const xref = Buffer.byteLength(body);
  body += `xref\n0 ${objects.length + 1}\n0000000000 65535 f \n`;
  for (const offset of offsets) body += `${String(offset).padStart(10, '0')} 00000 n \n`;
  body += `trailer\n<< /Size ${objects.length + 1} /Root 1 0 R >>\nstartxref\n${xref}\n%%EOF\n`;
  return Buffer.from(body);
}

try {
 await start(); const ordinaryId=(await invoke('ensure_desktop_local_workspace')).summary.id;
 const before=await invoke('read_desktop_workspace',{workspaceId:ordinaryId}); const workId=await evaluate("window.__TAURI__.core.invoke('ensure_desktop_local_workspace',{}, {headers:{'x-nanfeng-data-area':'WORK'}}).then(x=>x.summary.id)"); const workBefore=await evaluate(`window.__TAURI__.core.invoke('read_desktop_workspace',{workspaceId:${JSON.stringify(workId)}},{headers:{'x-nanfeng-data-area':'WORK'}})`);
 const selectedPath=join(root,'synthetic-preview.pdf');await writeFile(selectedPath,twoPagePdf());
 const asset=await invoke('import_desktop_conversation_attachment',{args:{workspaceId:ordinaryId,selectedPath}});
 await ui('[data-action=new-chat]');
 const initial=await invoke('read_desktop_conversation_draft',{workspaceId:ordinaryId});
 await invoke('save_desktop_conversation_draft',{args:{workspaceId:ordinaryId,conversationId:null,text:'SYNTHETIC_PDF_CANCEL_DRAFT',attachmentIds:[asset.id]}});
 await evaluate('location.reload();true');await until(()=>evaluate('globalThis.__nanfengDesktopWorkState?.runtimeInfo?.dataAreasReady===true'),'reload native');await ui('[data-action=new-chat]');
 const selector=`[data-action="open-pdf-preview"][data-attachment-id="${asset.id}"]`;await until(()=>evaluate(`Boolean(document.querySelector(${JSON.stringify(selector)}))`),'actual draft PDF card');
 const draft=await invoke('read_desktop_conversation_draft',{workspaceId:ordinaryId});
 const cancelledId=await evaluate(`(async()=>{document.querySelector(${JSON.stringify(selector)}).click();const end=performance.now()+3000;while(performance.now()<end){const p=globalThis.__nanfengDesktopWorkState.pdfPreview;if(p?.loading&&p.request?.id){const id=p.request.id;document.querySelector('[data-action="close-pdf-preview"]').click();return id;}if(p&&!p.loading)throw new Error('PDF completed before cancellation observation');await new Promise(r=>setTimeout(r,1));}throw new Error('PDF reservation not observed')})()`);
 await until(()=>evaluate('!globalThis.__nanfengDesktopWorkState.pdfPreview&&!document.querySelector(".pdf-preview-dialog")'),'actual loading close');
 const rejection=await invoke('read_desktop_pdf_preview',{requestId:cancelledId,args:{workspaceId:ordinaryId,attachmentId:asset.id,pageNumber:1}}).then(()=>{throw new Error('closed request reused')},e=>String(e));assert.match(rejection,/已取消或已被读取/);
 await ui(selector);await until(()=>evaluate('globalThis.__nanfengDesktopWorkState.pdfPreview?.pageNumber===1&&!globalThis.__nanfengDesktopWorkState.pdfPreview?.loading'),'reopen PDF page one');
 const page1=await evaluate('globalThis.__nanfengDesktopWorkState.pdfPreview');assert.equal(page1.pageCount,2);await ui('[data-action=pdf-page-next]');await until(()=>evaluate('globalThis.__nanfengDesktopWorkState.pdfPreview?.pageNumber===2&&!globalThis.__nanfengDesktopWorkState.pdfPreview?.loading'),'page two');const page2=await evaluate('globalThis.__nanfengDesktopWorkState.pdfPreview');assert.notEqual(page1.dataUrl,page2.dataUrl);await ui('[data-action=close-pdf-preview]');
 assert.deepEqual(await invoke('read_desktop_conversation_draft',{workspaceId:ordinaryId}),draft);assert.deepEqual(await invoke('read_desktop_workspace',{workspaceId:ordinaryId}),before);await normalClose();
 await start(true);await ui('[data-action=new-chat]');assert.deepEqual(await invoke('read_desktop_conversation_draft',{workspaceId:ordinaryId}),draft);assert.deepEqual(await invoke('read_desktop_workspace',{workspaceId:ordinaryId}),before);await ui(selector);await until(()=>evaluate('globalThis.__nanfengDesktopWorkState.pdfPreview?.pageNumber===2&&!globalThis.__nanfengDesktopWorkState.pdfPreview?.loading'),'cold retained PDF page two');await ui('[data-action=close-pdf-preview]');assert.equal(requests,0);assert.deepEqual(await evaluate(`window.__TAURI__.core.invoke('read_desktop_workspace',{workspaceId:${JSON.stringify(workId)}},{headers:{'x-nanfeng-data-area':'WORK'}})`),workBefore);await normalClose();
 const result={root,candidateSha256:receipt.sha256,ordinaryId,before,workId,workBefore,asset,draft,cancelledId,rejection,closes,windows,providerRequests:requests,coldExact:true,pages:2,page2Retained:true,loadingClose:true};await writeFile(join(root,'chat-pdf-preview-cancel-native.json'),JSON.stringify(result,null,2));console.log(JSON.stringify({root,candidateSha256:receipt.sha256,closes,providerRequests:requests,loadingClose:true,page2Retained:true,coldExact:true}));
} catch(error){if(root)await writeFile(join(root,'chat-pdf-preview-cancel-failure.json'),JSON.stringify({root,error:String(error),pids,closes},null,2));throw error;}finally{stop();for(const response of responses)response.destroy();provider.closeAllConnections();await new Promise(done=>provider.close(done));}
