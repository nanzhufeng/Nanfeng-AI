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
async function dataPage(){if(!await evaluate('Boolean(document.querySelector("[data-action=select-chatgpt-export]"))')){await ui('[data-action=show-settings]');if(!await evaluate('Boolean(document.querySelector("[data-action=open-settings-page][data-page=data]"))'))await ui('[data-action=settings-back]');await ui('[data-action=open-settings-page][data-page=data]');}await until(()=>evaluate('Boolean(document.querySelector("[data-action=select-chatgpt-export]"))'),'data import entry');}
function dialogOpen(){const windows=JSON.parse(ps(`& ${quote(join(desktop,'scripts/inspect-windows-file-dialog.ps1'))} -ProcessId ${Number(launch.pid)}`)||'[]');return windows.some(w=>w.children?.some(c=>c.class==='#32770'));}
async function picker(provider,path){await dataPage();await ui(`[data-action=select-${provider}-export]`);await until(dialogOpen,'native JSON picker');const r=JSON.parse(ps(`& ${quote(join(desktop,'scripts/operate-windows-file-dialog.ps1'))} -ProcessId ${Number(launch.pid)} -Action ${path?'select':'cancel'} ${path?'-SelectedPath '+quote(path):''} -AcceptanceRoot ${quote(root)}`));assert.equal(r.action,path?'select':'cancel');await until(()=>!dialogOpen(),'picker close');}
async function runImport(provider,path,previous){await picker(provider,path);await until(()=>evaluate(`globalThis.__nanfengDesktopWorkState?.${provider}Task?.id!==${JSON.stringify(previous?.id||null)} && globalThis.__nanfengDesktopWorkState?.${provider}Task?.status==='COMPLETED' && globalThis.__nanfengDesktopWorkState?.${provider}Task?.items?.every(x=>x.status==='CONFIRMED')`),'actual '+provider+' import terminal');return await invoke(`read_latest_${provider}_import_task`,{workspaceId:sourceId});}
let sourceId;
try{
 await start();const ordinaryId=(await invoke('ensure_desktop_local_workspace')).summary.id;const ordinaryBefore=await invoke('read_desktop_workspace',{workspaceId:ordinaryId});const workId=await evaluate("window.__TAURI__.core.invoke('ensure_desktop_local_workspace',{}, {headers:{'x-nanfeng-data-area':'WORK'}}).then(x=>x.summary.id)");const workBefore=await evaluate(`window.__TAURI__.core.invoke('read_desktop_workspace',{workspaceId:${JSON.stringify(workId)}},{headers:{'x-nanfeng-data-area':'WORK'}})`);
 const inputDir=join(root,'synthetic-chat-json-input');await mkdir(inputDir,{recursive:true});const exchangePath=join(inputDir,'second-source.nfai-exchange');await copyFile(resolve(desktop,'../protocol/artifacts/nfai.exchange.v1.golden.nfai-exchange'),exchangePath);const staged=await invoke('stage_preflight_selected_exchange',{selectedPath:exchangePath});const source=await invoke('import_staged_exchange_as_new_workspace',{args:{stagingId:staged.stagingId,workspaceTitle:'合成聊天记录来源'} });sourceId=source.summary.id;assert.notEqual(sourceId,ordinaryId);
 await evaluate('location.reload();true');await until(()=>evaluate('globalThis.__nanfengDesktopWorkState?.runtimeInfo?.dataAreasReady===true'),'reloaded UI');await ui(`[data-action="select-chat-source"][data-id="${sourceId}"]`);await until(()=>evaluate(`globalThis.__nanfengDesktopWorkState?.current?.summary?.id===${JSON.stringify(sourceId)}`),'actual selected chat source');const sourceBefore=await invoke('read_desktop_workspace',{workspaceId:sourceId});
 const chatgptPath=join(inputDir,'chatgpt-conversations.json');const claudePath=join(inputDir,'claude-conversations.json');
 const chatgpt=[0,1].map(i=>({id:`chat-source-gpt-${i}`,title:`聊天导入GPT ${i}`,create_time:1,update_time:2,mapping:{u:{parent:null,children:['a'],message:{author:{role:'user'},content:{parts:[`SYNTHETIC_CHAT_SOURCE_GPT_USER_${i}`]}}},a:{parent:'u',children:[],message:{author:{role:'assistant'},content:{parts:[`SYNTHETIC_CHAT_SOURCE_GPT_ANSWER_${i}`]}}}}}));
 const claude=[0,1].map(i=>({uuid:`chat-source-claude-${i}`,name:`聊天导入Claude ${i}`,created_at:'2026-08-15T08:00:00Z',updated_at:'2026-08-15T08:01:00Z',chat_messages:[{uuid:`u-${i}`,sender:'human',created_at:'2026-08-15T08:00:00Z',text:`SYNTHETIC_CHAT_SOURCE_CLAUDE_USER_${i}`,parent_message_uuid:null},{uuid:`a-${i}`,sender:'assistant',created_at:'2026-08-15T08:00:01Z',text:`SYNTHETIC_CHAT_SOURCE_CLAUDE_ANSWER_${i}`,parent_message_uuid:`u-${i}`}]}));await writeFile(chatgptPath,JSON.stringify(chatgpt));await writeFile(claudePath,JSON.stringify(claude));
 await picker('chatgpt',null);assert.deepEqual(await invoke('read_desktop_workspace',{workspaceId:sourceId}),sourceBefore);const cancelTask=await invoke('read_latest_chatgpt_import_task',{workspaceId:sourceId});assert.equal(cancelTask,null);
 const gpt=await runImport('chatgpt',chatgptPath,null);assert.equal(gpt.items.length,2);const afterGpt=await invoke('read_desktop_workspace',{workspaceId:sourceId});
 await picker('claude',null);assert.deepEqual(await invoke('read_desktop_workspace',{workspaceId:sourceId}),afterGpt);assert.equal(await invoke('read_latest_claude_import_task',{workspaceId:sourceId}),null);
 const cl=await runImport('claude',claudePath,null);assert.equal(cl.items.length,2);const afterImport=await invoke('read_desktop_workspace',{workspaceId:sourceId});assert.equal(afterImport.exchange.conversations.length,sourceBefore.exchange.conversations.length+4);const originalIds=new Set(sourceBefore.exchange.conversations.map(c=>c.id));assert.deepEqual(afterImport.exchange.conversations.filter(c=>originalIds.has(c.id)),sourceBefore.exchange.conversations);
 const replayGpt=await runImport('chatgpt',chatgptPath,gpt);const replayClaude=await runImport('claude',claudePath,cl);assert.notEqual(replayGpt.id,gpt.id);assert.notEqual(replayClaude.id,cl.id);assert.deepEqual(await invoke('read_desktop_workspace',{workspaceId:sourceId}),afterImport);assert.deepEqual(await invoke('read_desktop_workspace',{workspaceId:ordinaryId}),ordinaryBefore);assert.deepEqual(await evaluate(`window.__TAURI__.core.invoke('read_desktop_workspace',{workspaceId:${JSON.stringify(workId)}},{headers:{'x-nanfeng-data-area':'WORK'}})`),workBefore);
 await ui('[data-action=show-chat]');const added=afterImport.exchange.conversations.filter(c=>!originalIds.has(c.id));for(const c of added){await ui(`[data-action="select-chat"][data-id="${c.id}"]`);const marker=JSON.stringify(c.messages).match(/SYNTHETIC_CHAT_SOURCE_(?:GPT|CLAUDE)_ANSWER_[01]/)?.[0];assert.ok(marker);await until(()=>evaluate(`document.querySelector('.chat-scroll')?.textContent.includes(${JSON.stringify(marker)})`),'actual imported body');}
 assert.equal(await evaluate('document.querySelectorAll("[data-action=show-work],[data-action=show-workspace],[data-action=show-projects],[data-action=show-knowledge],[data-action=show-p8-inspect],[data-action=select-workspace]").length'),0);await normalClose();await start(true);assert.deepEqual(await invoke('read_desktop_workspace',{workspaceId:sourceId}),afterImport);assert.deepEqual(await invoke('read_desktop_workspace',{workspaceId:ordinaryId}),ordinaryBefore);await ui(`[data-action="select-chat-source"][data-id="${sourceId}"]`);await ui(`[data-action="select-chat"][data-id="${added[0].id}"]`);const coldMarker=JSON.stringify(added[0].messages).match(/SYNTHETIC_CHAT_SOURCE_(?:GPT|CLAUDE)_ANSWER_[01]/)[0];await until(()=>evaluate(`document.querySelector('.chat-scroll')?.textContent.includes(${JSON.stringify(coldMarker)})`),'cold imported actual body');assert.equal(requests,0);await normalClose();
 const result={root,candidateSha256:receipt.sha256,ordinaryId,sourceId,workId,ordinaryBefore,workBefore,sourceBefore,afterImport,gpt,cl,replayGpt,replayClaude,addedConversationIds:added.map(x=>x.id),chatgptPath,claudePath,pids,windows,closes,providerRequests:requests,coldExact:true,actualPickerCancels:2,actualImports:4};await writeFile(join(root,'chat-json-import-source-native.json'),JSON.stringify(result,null,2));console.log(JSON.stringify({root,candidateSha256:receipt.sha256,addedConversations:added.length,sourceId,providerRequests:requests,closes,coldExact:true,actualPickerCancels:2,actualImports:4}));
}catch(error){if(root)await writeFile(join(root,'chat-json-import-source-failure.json'),JSON.stringify({root,sourceId,pids,closes,error:String(error)},null,2));throw error;}finally{stop();for(const response of responses)response.destroy();provider.closeAllConnections();await new Promise(done=>provider.close(done));}
