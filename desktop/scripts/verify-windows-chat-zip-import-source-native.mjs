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
async function picker(provider,path){await dataPage();await ui(`[data-action=select-p6k-${provider}-zip]`);await until(dialogOpen,'native JSON picker');const r=JSON.parse(ps(`& ${quote(join(desktop,'scripts/operate-windows-file-dialog.ps1'))} -ProcessId ${Number(launch.pid)} -Action ${path?'select':'cancel'} ${path?'-SelectedPath '+quote(path):''} -AcceptanceRoot ${quote(root)}`));assert.equal(r.action,path?'select':'cancel');await until(()=>!dialogOpen(),'picker close');}

async function runZip(provider,path,previous){await picker(provider.toLowerCase(),path);return await until(async()=>{const t=await invoke('read_latest_p6k_zip_import_task',{workspaceId:sourceId});return t?.id!==previous?.id&&t?.provider===provider&&t?.status==='COMPLETED'?t:null;},'actual '+provider+' ZIP completion');}
let sourceId;

try{
 await start();const ordinaryId=(await invoke('ensure_desktop_local_workspace')).summary.id;const ordinaryBefore=await invoke('read_desktop_workspace',{workspaceId:ordinaryId});const workId=await evaluate("window.__TAURI__.core.invoke('ensure_desktop_local_workspace',{}, {headers:{'x-nanfeng-data-area':'WORK'}}).then(x=>x.summary.id)");const workBefore=await evaluate(`window.__TAURI__.core.invoke('read_desktop_workspace',{workspaceId:${JSON.stringify(workId)}},{headers:{'x-nanfeng-data-area':'WORK'}})`);
 const inputDir=join(root,'synthetic-chat-zip-input');await mkdir(inputDir,{recursive:true});const exchangePath=join(inputDir,'second-source.nfai-exchange');await copyFile(resolve(desktop,'../protocol/artifacts/nfai.exchange.v1.golden.nfai-exchange'),exchangePath);const staged=await invoke('stage_preflight_selected_exchange',{selectedPath:exchangePath});const source=await invoke('import_staged_exchange_as_new_workspace',{args:{stagingId:staged.stagingId,workspaceTitle:'合成聊天记录来源'} });sourceId=source.summary.id;assert.notEqual(sourceId,ordinaryId);
 await evaluate('location.reload();true');await until(()=>evaluate('globalThis.__nanfengDesktopWorkState?.runtimeInfo?.dataAreasReady===true'),'reloaded UI');await ui(`[data-action="select-chat-source"][data-id="${sourceId}"]`);await until(()=>evaluate(`globalThis.__nanfengDesktopWorkState?.current?.summary?.id===${JSON.stringify(sourceId)}`),'actual selected chat source');const sourceBefore=await invoke('read_desktop_workspace',{workspaceId:sourceId});

 const settingsBefore=await invoke('read_desktop_app_settings');const png=join(desktop,'src-tauri/tests/fixtures/windows-image-red.png');const pngBytes=await readFile(png);const pngSha256=createHash('sha256').update(pngBytes).digest('hex');
 const gptJson=join(inputDir,'gpt.json');const clJson=join(inputDir,'cl.json');const gptZip=join(inputDir,'chatgpt.zip');const clZip=join(inputDir,'claude.zip');const official={id:'synthetic-media',name:'photo.png'};
 await writeFile(gptJson,JSON.stringify([{id:'chat-zip-gpt',title:'合成ZIP ChatGPT',create_time:1,update_time:2,mapping:{u:{parent:null,children:['a'],message:{author:{role:'user'},content:{parts:['SYNTHETIC_CHAT_ZIP_GPT_USER',{asset_pointer:'file-service://synthetic-media'}]},metadata:{attachments:[official,{id:'missing-export-asset',name:'missing.pdf'}]}}},a:{parent:'u',children:[],message:{author:{role:'assistant'},content:{parts:['SYNTHETIC_CHAT_ZIP_GPT_ANSWER']},metadata:{attachments:[official]}}}}}]));
 await writeFile(clJson,JSON.stringify([{uuid:'chat-zip-claude',name:'合成ZIP Claude',created_at:'2026-08-15T08:00:00Z',updated_at:'2026-08-15T08:01:00Z',chat_messages:[{uuid:'cu',sender:'human',created_at:'2026-08-15T08:00:00Z',text:'SYNTHETIC_CHAT_ZIP_CLAUDE_USER',parent_message_uuid:null},{uuid:'ca',sender:'assistant',created_at:'2026-08-15T08:00:01Z',text:'SYNTHETIC_CHAT_ZIP_CLAUDE_ANSWER',parent_message_uuid:'cu'}]}]));
 const make="import sys,zipfile; z=zipfile.ZipFile(sys.argv[1],'w',zipfile.ZIP_DEFLATED); z.write(sys.argv[2],'conversations.json'); z.write(sys.argv[3],sys.argv[4]); z.close()";execFileSync('python',['-c',make,gptZip,gptJson,png,'synthetic-media.dat']);execFileSync('python',['-c',make,clZip,clJson,png,'unrelated-image.png']);
 await picker('chatgpt',null);assert.deepEqual(await invoke('read_desktop_workspace',{workspaceId:sourceId}),sourceBefore);assert.equal(await invoke('read_latest_p6k_zip_import_task',{workspaceId:sourceId}),null);
 const gpt=await runZip('CHATGPT',gptZip,null);assert.equal(gpt.importedCount,1);assert.equal(gpt.failedCount,0);assert.deepEqual(gpt.officialAttachmentSummary,{referencedOccurrences:3,recoverableUniqueAssets:1,missingUniqueAssets:1,fallbackNameUniqueAssets:0,restoredUniqueAssets:1,restoredOccurrences:2});assert.equal(gpt.manualAssets.length,1);assert.equal(gpt.manualAssets[0].status,'AUTO_LINKED');const afterGpt=await invoke('read_desktop_workspace',{workspaceId:sourceId});
 await picker('claude',null);assert.deepEqual(await invoke('read_desktop_workspace',{workspaceId:sourceId}),afterGpt);assert.deepEqual(await invoke('read_latest_p6k_zip_import_task',{workspaceId:sourceId}),gpt);
 const cl=await runZip('CLAUDE',clZip,gpt);assert.equal(cl.importedCount,1);assert.equal(cl.failedCount,0);assert.equal(cl.unmappedAssetCount,1);const afterImport=await invoke('read_desktop_workspace',{workspaceId:sourceId});assert.equal(afterImport.exchange.conversations.length,sourceBefore.exchange.conversations.length+2);const originalIds=new Set(sourceBefore.exchange.conversations.map(c=>c.id));assert.deepEqual(afterImport.exchange.conversations.filter(c=>originalIds.has(c.id)),sourceBefore.exchange.conversations);
 const gptConversation=afterImport.exchange.conversations.find(c=>c.id===gpt.items[0].conversationId);const gptRefs=gptConversation.messages.flatMap(m=>m.blocks.filter(b=>b.kind==='ASSET_REF'));assert.equal(gptRefs.length,2);assert.ok(gptRefs.every(b=>b.asset.sha256===pngSha256&&b.asset.mimeType==='image/png'&&b.asset.displayName==='photo.png'));assert.equal(gptRefs[0].asset.id,gptRefs[1].asset.id);assert.equal(gptConversation.messages.filter(m=>m.blocks.some(b=>b.kind==="ASSET_REF"&&b.asset.sha256===pngSha256)).length,2);const clConversation=afterImport.exchange.conversations.find(c=>c.id===cl.items[0].conversationId);assert.equal(clConversation.messages.flatMap(m=>m.blocks.filter(b=>b.kind==='ASSET_REF')).length,0);
 const replay=await runZip('CHATGPT',gptZip,cl);assert.equal(replay.items[0].conversationId,gpt.items[0].conversationId);assert.deepEqual(await invoke('read_desktop_workspace',{workspaceId:sourceId}),afterImport);assert.deepEqual(await invoke('read_desktop_workspace',{workspaceId:ordinaryId}),ordinaryBefore);assert.deepEqual(await evaluate(`window.__TAURI__.core.invoke('read_desktop_workspace',{workspaceId:${JSON.stringify(workId)}},{headers:{'x-nanfeng-data-area':'WORK'}})`),workBefore);assert.deepEqual(await invoke('read_desktop_app_settings'),settingsBefore);
 await ui('[data-action=open-settings-page][data-page=zip-import-results]');await until(()=>evaluate('document.querySelector(".android-settings-main")?.textContent.includes("官方引用但导出包缺文件 1 个")'),'actual missing asset disclosure');await ui('[data-action=show-chat]');await ui(`[data-action="select-chat"][data-id="${gptConversation.id}"]`);await until(()=>evaluate('document.querySelector(".chat-scroll")?.textContent.includes("SYNTHETIC_CHAT_ZIP_GPT_ANSWER")'),'actual GPT body');await ui(`[data-action="open-image-preview"][data-attachment-id="${gptRefs[0].asset.id}"]`);const image=await until(()=>evaluate('(()=>{const i=document.querySelector("[data-image-preview-content]");return i?.complete&&i.naturalWidth>0?{width:i.naturalWidth,height:i.naturalHeight,alt:i.alt}:null})()'),'actual imported original image');await ui('[data-action=close-image-preview]');await until(()=>evaluate('!document.querySelector("[data-image-preview-content]")'),'preview release');await ui(`[data-action="select-chat"][data-id="${clConversation.id}"]`);await until(()=>evaluate('document.querySelector(".chat-scroll")?.textContent.includes("SYNTHETIC_CHAT_ZIP_CLAUDE_ANSWER")'),'actual Claude body');assert.equal(await evaluate('document.querySelectorAll("[data-action=show-work],[data-action=show-projects],[data-action=show-knowledge],[data-action=show-p8-inspect],[data-action=select-workspace]").length'),0);await normalClose();
 await start(true);assert.deepEqual(await invoke('read_desktop_workspace',{workspaceId:sourceId}),afterImport);assert.deepEqual(await invoke('read_desktop_workspace',{workspaceId:ordinaryId}),ordinaryBefore);assert.deepEqual(await invoke('read_desktop_app_settings'),settingsBefore);await ui(`[data-action="select-chat-source"][data-id="${sourceId}"]`);await ui(`[data-action="select-chat"][data-id="${gptConversation.id}"]`);await ui(`[data-action="open-image-preview"][data-attachment-id="${gptRefs[1].asset.id}"]`);await until(()=>evaluate('document.querySelector("[data-image-preview-content]")?.naturalWidth>0'),'cold second occurrence image');await ui('[data-action=close-image-preview]');assert.equal(requests,0);await normalClose();
 const result={root,candidateSha256:receipt.sha256,ordinaryId,sourceId,workId,ordinaryBefore,workBefore,sourceBefore,afterImport,settingsBefore,gpt,cl,replay,gptZip,clZip,pngSha256,pngByteCount:pngBytes.length,gptRefs,image,pids,windows,closes,providerRequests:requests,coldExact:true,actualPickerCancels:2,actualImports:3};await writeFile(join(root,'chat-zip-import-source-native.json'),JSON.stringify(result,null,2));console.log(JSON.stringify({root,candidateSha256:receipt.sha256,addedConversations:2,officialRecoveredOccurrences:2,missingOfficialAssets:1,unmappedClaudeAssets:1,sourceId,providerRequests:requests,closes,coldExact:true,actualPickerCancels:2,actualImports:3,image}));
}catch(error){if(root)await writeFile(join(root,'chat-zip-import-source-failure.json'),JSON.stringify({root,sourceId,pids,closes,error:String(error)},null,2));throw error;}finally{stop();for(const response of responses)response.destroy();provider.closeAllConnections();await new Promise(done=>provider.close(done));}
