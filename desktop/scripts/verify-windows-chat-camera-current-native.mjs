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
  const code = `[Console]::OutputEncoding=[Text.UTF8Encoding]::new($false); & ${quote(join(desktop, 'scripts/start-windows-acceptance.ps1'))} -RootParent ${quote(parent)} -ExecutablePath ${quote(receipt.executable)} -StartupMode normal -Visible -FakeCamera -DebugPort ${port}${reopen ? ` -Root ${quote(root)}` : ''}`;
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

async function openCamera(){await ui('[data-action=toggle-composer-add]');await ui('[data-action=open-composer-camera]');await until(()=>evaluate('globalThis.__nanfengDesktopWorkState.cameraCaptureReady&&document.querySelector("[data-composer-camera-video]")?.videoWidth>0'),'fake camera ready');const value=await evaluate('(()=>{const v=document.querySelector("[data-composer-camera-video]");const tracks=v.srcObject.getTracks();if(!tracks.every(t=>/fake/i.test(t.label))){tracks.forEach(t=>t.stop());throw new Error("not synthetic camera");}globalThis.__syntheticCameraTracks=tracks;return {width:v.videoWidth,height:v.videoHeight,trackCount:tracks.length,fake:true}})()');assert.equal(value.fake,true);return value;}
async function stopped(){await until(()=>evaluate('globalThis.__syntheticCameraTracks.every(t=>t.readyState==="ended")&&!document.querySelector(".camera-capture-dialog")'),'camera tracks stopped');}
try{
 await start();const fakeFlags=ps(`@(Get-CimInstance Win32_Process -Filter "Name='msedgewebview2.exe'" | Where-Object {$_.CommandLine -and $_.CommandLine.Contains(${quote(launch.webviewProfile)}) -and $_.CommandLine.Contains('--use-fake-device-for-media-stream') -and $_.CommandLine.Contains('--use-fake-ui-for-media-stream')}).Count`);assert.ok(Number(fakeFlags)>0,'actual owned WebView must carry both fake-camera flags');
 const workspaceId=(await invoke('ensure_desktop_local_workspace')).summary.id;const before=await invoke('read_desktop_workspace',{workspaceId});const workId=await evaluate("window.__TAURI__.core.invoke('ensure_desktop_local_workspace',{}, {headers:{'x-nanfeng-data-area':'WORK'}}).then(x=>x.summary.id)");const workBefore=await evaluate(`window.__TAURI__.core.invoke('read_desktop_workspace',{workspaceId:${JSON.stringify(workId)}},{headers:{'x-nanfeng-data-area':'WORK'}})`);
 await ui('[data-action=new-chat]');await evaluate('(()=>{const i=document.querySelector("#chat-composer");i.value="SYNTHETIC_CAMERA_ORDINARY_DRAFT";i.dispatchEvent(new Event("input",{bubbles:true}));return true})()');let ordinaryBefore;await until(async()=>{const d=await invoke('read_desktop_conversation_draft',{workspaceId});if(d?.text==='SYNTHETIC_CAMERA_ORDINARY_DRAFT'){ordinaryBefore=d;return true;}return false;},'ordinary draft');
 const cancelledFrame=await openCamera();await ui('[data-action=close-composer-camera]');await stopped();assert.deepEqual(await invoke('read_desktop_conversation_draft',{workspaceId}),ordinaryBefore);assert.deepEqual(await invoke('read_desktop_workspace',{workspaceId}),before);
 const ordinaryFrame=await openCamera();await ui('[data-action=capture-composer-camera]');await stopped();let ordinaryAfter;await until(async()=>{const d=await invoke('read_desktop_conversation_draft',{workspaceId});if(d?.attachments.length===1){ordinaryAfter=d;return true;}return false;},'ordinary photo persisted');assert.equal(ordinaryAfter.text,ordinaryBefore.text);assert.equal(ordinaryAfter.attachments[0].mimeType,'image/png');
 await ui('[data-action=toggle-temporary-chat]');const temporaryBefore=await invoke('read_desktop_temporary_conversation');assert.ok(temporaryBefore);const temporaryCancelledFrame=await openCamera();await ui('[data-action=close-composer-camera]');await stopped();assert.deepEqual(await invoke('read_desktop_temporary_conversation'),temporaryBefore);assert.deepEqual(await invoke('read_desktop_conversation_draft',{workspaceId}),ordinaryAfter);
 const temporaryFrame=await openCamera();await ui('[data-action=capture-composer-camera]');await stopped();const temporaryAfter=await invoke('read_desktop_temporary_conversation');assert.equal(temporaryAfter.attachments.length,1);assert.equal(temporaryAfter.draftAttachmentIds.length,1);assert.equal(temporaryAfter.attachments[0].mimeType,'image/png');assert.equal(temporaryAfter.draftAttachmentIds[0],temporaryAfter.attachments[0].id);assert.deepEqual(await invoke('read_desktop_conversation_draft',{workspaceId}),ordinaryAfter);
 await ui('[data-action=toggle-temporary-chat]');await until(()=>evaluate('document.querySelector("#chat-composer")?.value==="SYNTHETIC_CAMERA_ORDINARY_DRAFT"'),'return ordinary draft');assert.deepEqual(await invoke('read_desktop_temporary_conversation'),temporaryAfter);assert.deepEqual(await invoke('read_desktop_workspace',{workspaceId}),before);await normalClose();
 await start(true);await ui('[data-action=new-chat]');assert.deepEqual(await invoke('read_desktop_conversation_draft',{workspaceId}),ordinaryAfter);assert.deepEqual(await invoke('read_desktop_temporary_conversation'),temporaryAfter);assert.deepEqual(await invoke('read_desktop_workspace',{workspaceId}),before);assert.deepEqual(await evaluate(`window.__TAURI__.core.invoke('read_desktop_workspace',{workspaceId:${JSON.stringify(workId)}},{headers:{'x-nanfeng-data-area':'WORK'}})`),workBefore);assert.equal(requests,0);await normalClose();
 const result={root,candidateSha256:receipt.sha256,workspaceId,before,workId,workBefore,ordinaryBefore,ordinaryAfter,temporaryBefore,temporaryAfter,cancelledFrame,ordinaryFrame,temporaryCancelledFrame,temporaryFrame,closes,windows,providerRequests:requests,coldExact:true,cancellationNoWrite:true,tracksEnded:true,fakeCamera:true};await writeFile(join(root,'chat-camera-current-native.json'),JSON.stringify(result,null,2));console.log(JSON.stringify({root,candidateSha256:receipt.sha256,closes,providerRequests:requests,coldExact:true,tracksEnded:true,ordinaryPhotoCount:1,temporaryPhotoCount:1,fakeCamera:true}));
}catch(error){if(root)await writeFile(join(root,'chat-camera-current-failure.json'),JSON.stringify({root,error:String(error),pids,closes,ui:await evaluate('(()=>{const s=globalThis.__nanfengDesktopWorkState;return {open:s?.cameraCaptureOpen,ready:s?.cameraCaptureReady,error:s?.cameraCaptureError,temporary:!!s?.temporaryConversation}})()').catch(()=>null)},null,2));throw error;}finally{stop();for(const response of responses)response.destroy();provider.closeAllConnections();await new Promise(done=>provider.close(done));}
