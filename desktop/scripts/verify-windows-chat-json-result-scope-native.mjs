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
const parent = resolve(process.argv[2] || 'D:/CodexTemp/nfa-chat-json-result-scope');
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
  response.writeHead(503);response.end('synthetic scope must not send');
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
  const code = `[Console]::OutputEncoding=[Text.UTF8Encoding]::new($false); & ${quote(join(desktop, 'scripts/start-windows-acceptance.ps1'))} -RootParent ${quote(parent)} -ExecutablePath ${quote(receipt.executable)} -StartupMode normal -Visible -OrdinaryChatTestEndpoint ${quote(endpoint)} -DebugPort ${port}${reopen ? ` -Root ${quote(root)}` : ''}`;
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

try {
 await start();const workspaceId=(await invoke('ensure_desktop_local_workspace')).summary.id;const before=await invoke('read_desktop_workspace',{workspaceId});
 const selectedPath=join(root,'synthetic-knowledge-history.json');await writeFile(selectedPath,JSON.stringify([{id:101,title:'SYNTHETIC_HIDDEN_KNOWLEDGE_HISTORY',createdAt:'2026-08-15T08:00:00Z',updatedAt:'2026-08-15T08:01:00Z',sourceText:JSON.stringify({chat_messages:[{sender:'human',created_at:'2026-08-15T08:00:00Z',text:'SYNTHETIC_KNOWLEDGE_USER'},{sender:'assistant',created_at:'2026-08-15T08:00:01Z',text:'SYNTHETIC_KNOWLEDGE_ASSISTANT'}]})}]));
 const task=await invoke('stage_nanfeng_knowledge_export_selected',{selectedPath,workspaceId});assert.ok(task.id);
 await evaluate('location.reload();true');await until(()=>evaluate('globalThis.__nanfengDesktopWorkState?.runtimeInfo?.dataAreasReady===true'),'reload');await until(()=>evaluate('Boolean(globalThis.__nanfengDesktopWorkState.nanfengKnowledgeTask?.id)'),'retained task read');
 const saved=await invoke('read_latest_nanfeng_knowledge_import_task',{workspaceId});
 async function checkPage(){await ui('[data-action=show-settings]');await ui('[data-action="open-settings-page"][data-page="data"]');await ui('[data-action="open-settings-page"][data-page="json-import-results"]');const html=await evaluate('document.querySelector(".android-settings-main").innerHTML');assert.doesNotMatch(html,/南枫知识库 JSON|SYNTHETIC_HIDDEN_KNOWLEDGE_HISTORY|confirm-nanfeng-knowledge-conflict|skip-nanfeng-knowledge-conflict/);assert.match(html,/还没有导入记录/);}
 await checkPage();assert.deepEqual(await invoke('read_latest_nanfeng_knowledge_import_task',{workspaceId}),saved);assert.deepEqual(await invoke('read_desktop_workspace',{workspaceId}),before);await normalClose();
 await start(true);await checkPage();assert.deepEqual(await invoke('read_latest_nanfeng_knowledge_import_task',{workspaceId}),saved);assert.deepEqual(await invoke('read_desktop_workspace',{workspaceId}),before);await normalClose();assert.equal(requests,0);
 const result={root,candidateSha256:receipt.sha256,workspaceId,before,saved,closes,windows,providerRequests:requests,coldExact:true,knowledgeTaskRetained:true,knowledgeResultHidden:true};await writeFile(join(root,'chat-json-result-scope-native.json'),JSON.stringify(result,null,2));console.log(JSON.stringify({root,candidateSha256:receipt.sha256,closes,providerRequests:requests,coldExact:true,knowledgeTaskRetained:true,knowledgeResultHidden:true}));
} catch(error){if(root)await writeFile(join(root,'chat-json-result-scope-failure.json'),JSON.stringify({root,error:String(error),pids,closes},null,2));throw error;}finally{stop();provider.closeAllConnections();await new Promise(done=>provider.close(done));}
