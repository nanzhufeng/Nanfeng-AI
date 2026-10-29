import { semanticHash } from '../../protocol/scripts/exchange-lib.mjs';
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
const parent = resolve(process.argv[2] || 'D:/CodexTemp/nfa-chat-memory-confirmation');
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




async function summary(){await ui('[data-action=show-settings]');await ui('[data-action=open-settings-page][data-page=personalization]');await ui('[data-action=open-settings-page][data-page=memory-overview]');await until(()=>evaluate('Boolean(document.querySelector(".android-memory-summary-page"))'),'summary visible');}
try{
 await start();const workspaceId=(await invoke('ensure_desktop_local_workspace')).summary.id;const baseline=await invoke('read_desktop_workspace',{workspaceId});assert.equal(baseline.exchange.memory.filter(x=>x.status==='ACTIVE'&&x.scope==='GLOBAL').length,0);const project=await invoke('mutate_desktop_domain',{args:{intentId:'synthetic-memory-project-'+Date.now(),workspaceId,entity:'project',action:'create',objectId:null,expectedRevision:null,fields:{title:'合成保留项目',description:'只作记忆隔离保留夹具'}}});const projectId=project.objectId;let ordinal=0;
 const seedMemory=(title,body,scope,scopeId)=>invoke('mutate_desktop_domain',{args:{intentId:'synthetic-memory-confirm-'+(++ordinal)+'-'+Date.now(),workspaceId,entity:'memory',action:'create',objectId:null,expectedRevision:null,fields:{title,body,scope,scopeId}}});
 await seedMemory('合成摘要甲','SYNTHETIC_MEMORY_CONFIRM_A','GLOBAL',null);await seedMemory('合成摘要乙','SYNTHETIC_MEMORY_CONFIRM_B','GLOBAL',null);await seedMemory('合成项目记忆','SYNTHETIC_PROJECT_MEMORY_KEEP','PROJECT',projectId);const seeded=await invoke('read_desktop_workspace',{workspaceId});const nonGlobal=seeded.exchange.memory.filter(x=>x.scope!=='GLOBAL');
 const workId=await evaluate("window.__TAURI__.core.invoke('ensure_desktop_local_workspace',{}, {headers:{'x-nanfeng-data-area':'WORK'}}).then(x=>x.summary.id)");const readWork=()=>evaluate(`window.__TAURI__.core.invoke('read_desktop_workspace',{workspaceId:${JSON.stringify(workId)}},{headers:{'x-nanfeng-data-area':'WORK'}})`);const workBefore=await readWork();
 await evaluate('location.reload();true');await until(()=>evaluate('globalThis.__nanfengDesktopWorkState?.runtimeInfo?.dataAreasReady===true'),'seed reload');await ui('[data-action=show-settings]');await ui('[data-action=open-settings-page][data-page=personalization]');
 let settingsBefore=await invoke('read_desktop_app_settings');if(!settingsBefore.product.memoryEnabled||!settingsBefore.product.memoryRetrievalEnabled){await ui('[data-action=toggle-product-setting][data-key=memoryEnabled]');await until(async()=>{settingsBefore=await invoke('read_desktop_app_settings');return settingsBefore.product.memoryEnabled&&settingsBefore.product.memoryRetrievalEnabled;},'enabled saved');}
 await ui('[data-action=open-settings-page][data-page=memory-overview]');await until(()=>evaluate('document.querySelectorAll(".android-memory-summary-content article").length===2'),'two summary topics');
 await ui('[data-action=ask-disable-memory-summary]');await ui('[data-action=close-dialog]');assert.deepEqual(await invoke('read_desktop_app_settings'),settingsBefore);assert.deepEqual(await invoke('read_desktop_workspace',{workspaceId}),seeded);
 await ui('[data-action=ask-disable-memory-summary]');await ui('[data-action=confirm-disable-memory-summary]');let disabled;await until(async()=>{disabled=await invoke('read_desktop_app_settings');return disabled.product.memoryRetrievalEnabled===false;},'disabled saved');assert.deepEqual(disabled.product,{...settingsBefore.product,memoryRetrievalEnabled:false});assert.deepEqual(await invoke('read_desktop_workspace',{workspaceId}),seeded);await normalClose();
 await start(true);await summary();assert.deepEqual(await invoke('read_desktop_app_settings'),disabled);assert.deepEqual(await invoke('read_desktop_workspace',{workspaceId}),seeded);assert.equal(await evaluate('document.querySelectorAll(".android-memory-summary-content article").length'),2);
 await ui('[data-action=ask-delete-memory-summary]');await ui('[data-action=close-dialog]');assert.deepEqual(await invoke('read_desktop_workspace',{workspaceId}),seeded);assert.deepEqual(await invoke('read_desktop_app_settings'),disabled);
 await ui('[data-action=ask-delete-memory-summary]');await ui('[data-action=confirm-delete-memory-summary]');let deleted;await until(async()=>{deleted=await invoke('read_desktop_workspace',{workspaceId});return deleted.exchange.memory.filter(x=>x.status==='ACTIVE'&&x.scope==='GLOBAL').length===0;},'summary deleted');assert.equal(deleted.exchange.memory.filter(x=>x.scope==='GLOBAL'&&x.status==='DELETED').length,2);assert.deepEqual(deleted.exchange.memory.filter(x=>x.scope!=='GLOBAL'),nonGlobal);for(const k of Object.keys(seeded.exchange).filter(k=>!['memory','export'].includes(k)))assert.deepEqual(deleted.exchange[k],seeded.exchange[k]);const {semanticHash:seedHash,...seedMeta}=seeded.exchange.export;const {semanticHash:deleteHash,...deleteMeta}=deleted.exchange.export;assert.deepEqual(deleteMeta,seedMeta);assert.equal(seedHash,semanticHash(seeded.exchange));assert.equal(deleteHash,semanticHash(deleted.exchange));assert.notEqual(deleteHash,seedHash);assert.deepEqual(await invoke('read_desktop_app_settings'),disabled);assert.deepEqual(await readWork(),workBefore);await normalClose();
 await start(true);await summary();await until(()=>evaluate('document.querySelector(".android-memory-summary-content")?.textContent.includes("还没有记忆摘要")'),'empty cold summary');assert.deepEqual(await invoke('read_desktop_workspace',{workspaceId}),deleted);assert.deepEqual(await invoke('read_desktop_app_settings'),disabled);assert.deepEqual(await readWork(),workBefore);assert.equal(requests,0);await normalClose();
 const result={root,candidateSha256:receipt.sha256,workspaceId,baseline,seeded,nonGlobal,workId,workBefore,settingsBefore,disabled,deleted,closes,windows,providerRequests:requests,disableCancelNoWrite:true,disablePreservesMemory:true,deleteCancelNoWrite:true,deleteOnlyGlobalSummary:true,coldExact:true};await writeFile(join(root,'chat-memory-confirmation-native.json'),JSON.stringify(result,null,2));console.log(JSON.stringify({root,candidateSha256:receipt.sha256,closes,providerRequests:requests,coldExact:true,disablePreservesMemory:true,deleteOnlyGlobalSummary:true}));
}catch(error){if(root)await writeFile(join(root,'chat-memory-confirmation-failure.json'),JSON.stringify({root,error:String(error),pids,closes},null,2));throw error;}finally{stop();for(const response of responses)response.destroy();provider.closeAllConnections();await new Promise(done=>provider.close(done));}
