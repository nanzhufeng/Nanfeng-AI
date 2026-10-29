import assert from 'node:assert/strict';
import { createHash } from 'node:crypto';
import { spawn, execFileSync } from 'node:child_process';
import { readFile, writeFile, stat } from 'node:fs/promises';
import { dirname, resolve, join, sep } from 'node:path';
import { fileURLToPath } from 'node:url';
import http from 'node:http';
import net from 'node:net';

assert.equal(process.platform, 'win32');
const desktop = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const parent = resolve(process.argv[2] || 'D:/CodexTemp');
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
  const code = `[Console]::OutputEncoding=[Text.UTF8Encoding]::new($false); & ${quote(join(desktop, 'scripts/start-windows-acceptance.ps1'))} -RootParent ${quote(parent)} -ExecutablePath ${quote(receipt.executable)} -StartupMode normal -Visible -DebugPort ${port} -OrdinaryChatTestEndpoint ${quote(endpoint)}${reopen ? ` -Root ${quote(root)}` : ''}`;
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
try{
 await start();const workspaceId=(await invoke('ensure_desktop_local_workspace')).summary.id;const beforeExchange=await invoke('read_desktop_workspace',{workspaceId});const before=await invoke('read_desktop_app_settings');
 await until(()=>evaluate('Boolean(document.querySelector("[data-action=show-settings]"))'),'settings entry');await evaluate('document.querySelector("[data-action=show-settings]").click();true');await until(()=>evaluate('Boolean(document.querySelector("[data-action=open-settings-picker][data-picker=themeColor]"))'),'appearance choices');
 assert.equal(before.product.memoryEnabled,true);assert.equal(before.product.memoryRetrievalEnabled,true);
 const rapid=await evaluate(`(()=>{
 const choose=(kind,value)=>{const row=document.querySelector('[data-action="open-settings-picker"][data-picker="'+kind+'"]');if(!row)return false;row.click();const option=document.querySelector('[data-action="select-settings-picker-option"][data-picker="'+kind+'"][data-value="'+value+'"]');if(!option)return false;option.click();return true;};
 if(!choose('themeColor','blue')||!choose('fontSize','large'))return {ok:false};const page=document.querySelector('[data-action="open-settings-page"][data-page="personalization"]');if(!page)return {ok:false};page.click();const clicks=[];for(let i=0;i<3;i++){const toggle=document.querySelector('[data-action="toggle-product-setting"][data-key="memoryEnabled"]');if(!toggle)return {ok:false};toggle.click();clicks.push(document.querySelector('[data-action="toggle-product-setting"][data-key="memoryEnabled"]')?.getAttribute('aria-checked'));}return {ok:true,clicks};})()`);
 assert.equal(rapid.ok,true);assert.deepEqual(rapid.clicks,['false','true','false']);
 const after=await until(async()=>{const v=await invoke('read_desktop_app_settings');return v.revision===before.revision+5&&v.appearance.themeColor==='blue'&&v.appearance.fontSize==='large'&&v.product.memoryEnabled===false&&v.product.memoryRetrievalEnabled===false?v:null;},'all five queued choices committed');
 const visible=await until(()=>evaluate('(()=>{const r=document.documentElement;return r.dataset.themeColor==="blue"&&r.dataset.fontSize==="large"&&getComputedStyle(r).getPropertyValue("--accent-orange").trim()==="#357ae8"&&getComputedStyle(r).getPropertyValue("--app-font-scale").trim()==="1.24"&&document.querySelector("[data-action=toggle-product-setting][data-key=memoryEnabled]")?.getAttribute("aria-checked")==="false"})()'),'final visible selections');
 const expectedAppearance={...before.appearance,themeColor:'blue',fontSize:'large'};assert.deepEqual(after.appearance,expectedAppearance);assert.deepEqual(after.product,{...before.product,memoryEnabled:false,memoryRetrievalEnabled:false});assert.deepEqual(await invoke('read_desktop_workspace',{workspaceId}),beforeExchange);assert.equal(requests,0);
 await normalClose();await start(true);assert.deepEqual(await invoke('read_desktop_app_settings'),after);assert.deepEqual(await invoke('read_desktop_workspace',{workspaceId}),beforeExchange);await until(()=>evaluate('(()=>{const r=document.documentElement;return r.dataset.themeColor==="blue"&&r.dataset.fontSize==="large"&&getComputedStyle(r).getPropertyValue("--accent-orange").trim()==="#357ae8"&&getComputedStyle(r).getPropertyValue("--app-font-scale").trim()==="1.24"})()'),'cold computed appearance');
 if(!await evaluate('Boolean(document.querySelector("[data-action=toggle-product-setting][data-key=memoryEnabled]"))')){if(await evaluate('Boolean(document.querySelector("[data-action=show-settings]"))'))await evaluate('document.querySelector("[data-action=show-settings]").click();true');await until(()=>evaluate('Boolean(document.querySelector("[data-action=open-settings-page][data-page=personalization]")||document.querySelector("[data-action=settings-back]"))'),'cold personalization route');if(!await evaluate('Boolean(document.querySelector("[data-action=open-settings-page][data-page=personalization]"))'))await evaluate('document.querySelector("[data-action=settings-back]").click();true');await until(()=>evaluate('Boolean(document.querySelector("[data-action=open-settings-page][data-page=personalization]"))'),'cold personalization entry');await evaluate('document.querySelector("[data-action=open-settings-page][data-page=personalization]").click();true');}
 await until(()=>evaluate('document.querySelector("[data-action=toggle-product-setting][data-key=memoryEnabled]")?.getAttribute("aria-checked")==="false"'),'cold memory UI');assert.equal(await evaluate('document.querySelectorAll("[data-key=historyLibraryEnabled],[data-action=show-work],[data-action=show-projects],[data-action=show-knowledge],[data-action=show-p8-inspect]").length'),0);assert.equal(requests,0);await normalClose();
 const result={root,candidateSha256:receipt.sha256,workspaceId,beforeExchange,before,after,rapid,providerRequests:requests,pids,closes,coldExact:true,computedStyleExact:true};await writeFile(join(root,'chat-settings-rapid-native.json'),JSON.stringify(result,null,2));console.log(JSON.stringify({root,candidateSha256:receipt.sha256,rapidClickStates:rapid.clicks,revisionDelta:after.revision-before.revision,coldExact:true,computedStyleExact:true,providerRequests:requests,pids,closes}));
}finally{stop();for(const response of responses)response.destroy();provider.closeAllConnections();await new Promise(done=>provider.close(done));}
