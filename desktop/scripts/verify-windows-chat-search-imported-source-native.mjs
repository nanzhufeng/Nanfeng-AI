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

async function search(query,category='text'){
 const ok=await evaluate(`(()=>{const i=document.querySelector('#full-search-input')||document.querySelector('#chat-search');if(!i)return false;i.value=${JSON.stringify(query)};i.dispatchEvent(new Event('input',{bubbles:true}));i.dispatchEvent(new KeyboardEvent('keydown',{key:'Enter',bubbles:true,cancelable:true}));return true})()`);assert.equal(ok,true);
 await until(()=>evaluate(`globalThis.__nanfengDesktopWorkState?.searchPanel&&!globalThis.__nanfengDesktopWorkState.searchLoading&&globalThis.__nanfengDesktopWorkState.chatSearch===${JSON.stringify(query)}`),'search loaded');await ui(`[data-action="set-search-category"][data-category="${category}"]`);await until(()=>evaluate(`globalThis.__nanfengDesktopWorkState?.searchCategory===${JSON.stringify(category)}&&!globalThis.__nanfengDesktopWorkState.searchLoading`),'category loaded');return evaluate('globalThis.__nanfengDesktopWorkState.searchPage');
}
async function currentPage(){return evaluate('globalThis.__nanfengDesktopWorkState.searchPage');}
async function returnSearch(){await ui('[data-action=return-to-search]');await until(()=>evaluate('globalThis.__nanfengDesktopWorkState.searchPanel'),'search return');}
let sourceId;

try{
 await start();const ordinaryId=(await invoke('ensure_desktop_local_workspace')).summary.id;const ordinaryBefore=await invoke('read_desktop_workspace',{workspaceId:ordinaryId});const workId=await evaluate("window.__TAURI__.core.invoke('ensure_desktop_local_workspace',{}, {headers:{'x-nanfeng-data-area':'WORK'}}).then(x=>x.summary.id)");const workBefore=await evaluate(`window.__TAURI__.core.invoke('read_desktop_workspace',{workspaceId:${JSON.stringify(workId)}},{headers:{'x-nanfeng-data-area':'WORK'}})`);
 const inputDir=join(root,'synthetic-search-source-input');await mkdir(inputDir,{recursive:true});const exchangePath=join(inputDir,'second-source.nfai-exchange');await copyFile(resolve(desktop,'../protocol/artifacts/nfai.exchange.v1.golden.nfai-exchange'),exchangePath);const staged=await invoke('stage_preflight_selected_exchange',{selectedPath:exchangePath});const source=await invoke('import_staged_exchange_as_new_workspace',{args:{stagingId:staged.stagingId,workspaceTitle:'合成聊天记录来源'} });sourceId=source.summary.id;assert.notEqual(sourceId,ordinaryId);
 await evaluate('location.reload();true');await until(()=>evaluate('globalThis.__nanfengDesktopWorkState?.runtimeInfo?.dataAreasReady===true'),'reloaded UI');await ui(`[data-action="select-chat-source"][data-id="${sourceId}"]`);await until(()=>evaluate(`globalThis.__nanfengDesktopWorkState?.current?.summary?.id===${JSON.stringify(sourceId)}`),'actual selected chat source');const sourceBefore=await invoke('read_desktop_workspace',{workspaceId:sourceId});

 const priorRoot="D:\\CodexTemp\\nfa-chat-zip-import-source\\nanfeng-ai-windows-acceptance.dc0fb380-30fb-4100-8251-65c2ac3e3dc7";
 const previous=JSON.parse(await readFile(join(priorRoot,'chat-zip-import-source-native.json'),'utf8'));assert.equal(previous.candidateSha256,"51F2F5A6AE8CEABC16E4E0EACC9CAC9D3A39BBCC60491FAB15BAF1CC2AC350FE");assert.equal(JSON.parse(await readFile(join(priorRoot,'.nanfeng-windows-acceptance.json'),'utf8')).identifier,receipt.identifier);
 const gptZip=join(inputDir,'fixture-chatgpt.zip');const clZip=join(inputDir,'fixture-claude.zip');await copyFile(previous.gptZip,gptZip);await copyFile(previous.clZip,clZip);
 const gpt=await invoke('stage_p6k_zip_import_selected',{args:{workspaceId:sourceId,provider:'CHATGPT',selectedPath:gptZip}});const cl=await invoke('stage_p6k_zip_import_selected',{args:{workspaceId:sourceId,provider:'CLAUDE',selectedPath:clZip}});assert.equal(gpt.status,'COMPLETED');assert.equal(cl.status,'COMPLETED');const afterFixture=await invoke('read_desktop_workspace',{workspaceId:sourceId});const gptConversation=afterFixture.exchange.conversations.find(c=>c.id===gpt.items[0].conversationId);
 await evaluate('location.reload();true');await until(()=>evaluate('globalThis.__nanfengDesktopWorkState?.runtimeInfo?.dataAreasReady===true'),'fixture UI reload');await ui(`[data-action="select-chat-source"][data-id="${ordinaryId}"]`);await ui('[data-action=new-chat]');await until(()=>evaluate('Boolean(document.querySelector("#chat-composer"))'),'ordinary composer');await evaluate("(()=>{const f=document.querySelector('#chat-composer');f.value='SYNTHETIC_SEARCH_DRAFT_PRESERVE';f.dispatchEvent(new Event('input',{bubbles:true}));return true})()");const draftBefore=await until(async()=>{const d=await invoke('read_desktop_conversation_draft',{workspaceId:ordinaryId});return d?.text==='SYNTHETIC_SEARCH_DRAFT_PRESERVE'?d:null;},'ordinary persisted draft');
 const textPage=await search('SYNTHETIC_CHAT_ZIP_GPT_ANSWER');const textHit=textPage.hits.find(h=>h.workspaceId===sourceId&&h.conversationId===gptConversation.id&&h.messageId);assert.ok(textHit);await ui(`[data-action="open-search-result"][data-entry-id="${textHit.entryId}"]`);await until(()=>evaluate(`globalThis.__nanfengDesktopWorkState?.current?.summary?.id===${JSON.stringify(sourceId)}&&globalThis.__nanfengDesktopWorkState.selectedConversationId===${JSON.stringify(textHit.conversationId)}&&globalThis.__nanfengDesktopWorkState.searchAnchorMessageId===${JSON.stringify(textHit.messageId)}&&!globalThis.__nanfengDesktopWorkState.searchPanel`),'cross-source exact text anchor');assert.deepEqual(await invoke('read_desktop_conversation_draft',{workspaceId:ordinaryId}),draftBefore);await returnSearch();
 const imagePage=await search('photo','image');const imageHit=imagePage.hits.find(h=>h.workspaceId===sourceId&&h.conversationId===gptConversation.id&&h.attachmentId&&h.messageId);assert.ok(imageHit);assert.ok(gptConversation.messages.some(m=>m.id===imageHit.messageId&&m.blocks.some(b=>b.kind==='ASSET_REF'&&b.asset.id===imageHit.attachmentId)));
 await evaluate(`document.querySelector(${JSON.stringify(`[data-search-entry-id="${imageHit.entryId}"]`)}).dispatchEvent(new MouseEvent('contextmenu',{bubbles:true,cancelable:true}));true`);await ui('[data-action=locate-search-attachment]');await until(()=>evaluate(`globalThis.__nanfengDesktopWorkState.searchAnchorMessageId===${JSON.stringify(imageHit.messageId)}&&globalThis.__nanfengDesktopWorkState.searchAnchorAttachmentId===${JSON.stringify(imageHit.attachmentId)}&&!globalThis.__nanfengDesktopWorkState.searchPanel`),'exact attachment occurrence anchor');await returnSearch();await search('photo','image');
 const beforeDelete=await invoke('read_desktop_workspace',{workspaceId:sourceId});const currentGpt=beforeDelete.exchange.conversations.find(c=>c.id===gptConversation.id);await invoke('mutate_desktop_domain',{args:{intentId:'synthetic-search-source-soft-delete',workspaceId:sourceId,entity:'conversation',action:'softDelete',objectId:currentGpt.id,expectedRevision:currentGpt.revision,fields:{}}});assert.equal(await evaluate(`Boolean(document.querySelector(${JSON.stringify(`[data-search-entry-id="${imageHit.entryId}"]`)}))`),true);
 await evaluate(`document.querySelector(${JSON.stringify(`[data-search-entry-id="${imageHit.entryId}"]`)}).dispatchEvent(new MouseEvent('contextmenu',{bubbles:true,cancelable:true}));true`);await ui('[data-action=locate-search-attachment]');await until(()=>evaluate(`globalThis.__nanfengDesktopWorkState.searchPanel&&!globalThis.__nanfengDesktopWorkState.searchLoading&&globalThis.__nanfengDesktopWorkState.searchError?.includes('结果所属会话已变化或删除')&&!globalThis.__nanfengDesktopWorkState.searchPage?.hits?.some(h=>h.entryId===${JSON.stringify(imageHit.entryId)})`),'stale deleted result refused and removed');
 const after=await invoke('read_desktop_workspace',{workspaceId:sourceId});assert.equal(after.exchange.conversations.find(c=>c.id===currentGpt.id).deleted,true);assert.deepEqual(await invoke('read_desktop_workspace',{workspaceId:ordinaryId}),ordinaryBefore);assert.deepEqual(await evaluate(`window.__TAURI__.core.invoke('read_desktop_workspace',{workspaceId:${JSON.stringify(workId)}},{headers:{'x-nanfeng-data-area':'WORK'}})`),workBefore);assert.deepEqual(await invoke('read_desktop_conversation_draft',{workspaceId:ordinaryId}),draftBefore);const livePage=await search('SYNTHETIC_CHAT_ZIP_CLAUDE_ANSWER','text');assert.ok(livePage.hits.some(h=>h.conversationId===cl.items[0].conversationId));const deletedPage=await search('SYNTHETIC_CHAT_ZIP_GPT_ANSWER','text');assert.equal(deletedPage.hits.length,0);await ui('[data-action=close-search]');await normalClose();
 await start(true);assert.deepEqual(await invoke('read_desktop_workspace',{workspaceId:sourceId}),after);assert.deepEqual(await invoke('read_desktop_workspace',{workspaceId:ordinaryId}),ordinaryBefore);assert.deepEqual(await invoke('read_desktop_conversation_draft',{workspaceId:ordinaryId}),draftBefore);const coldDeleted=await search('SYNTHETIC_CHAT_ZIP_GPT_ANSWER','text');assert.equal(coldDeleted.hits.length,0);const coldLive=await search('SYNTHETIC_CHAT_ZIP_CLAUDE_ANSWER','text');assert.ok(coldLive.hits.some(h=>h.workspaceId===sourceId&&h.conversationId===cl.items[0].conversationId));await ui('[data-action=close-search]');await ui(`[data-action="select-chat-source"][data-id="${ordinaryId}"]`);await ui('[data-action=new-chat]');await until(()=>evaluate('document.querySelector("#chat-composer")?.value==="SYNTHETIC_SEARCH_DRAFT_PRESERVE"'),'cold ordinary draft actual');assert.equal(requests,0);await normalClose();
 const result={root,candidateSha256:receipt.sha256,ordinaryId,sourceId,workId,ordinaryBefore,workBefore,sourceBefore,afterFixture,beforeDelete,after,gpt,cl,draftBefore,textHit,imageHit,livePage,deletedPage,coldLive,coldDeleted,pids,windows,closes,providerRequests:requests,coldExact:true,crossSourceDraftPreserved:true,staleDeletedResultRefused:true};await writeFile(join(root,'chat-search-imported-source-native.json'),JSON.stringify(result,null,2));console.log(JSON.stringify({root,candidateSha256:receipt.sha256,sourceId,providerRequests:requests,closes,coldExact:true,crossSourceDraftPreserved:true,staleDeletedResultRefused:true}));
}catch(error){if(root)await writeFile(join(root,'chat-search-imported-source-failure.json'),JSON.stringify({root,sourceId,pids,closes,error:String(error),uiState:await evaluate('(()=>{const s=globalThis.__nanfengDesktopWorkState;return {pane:s?.pane,searchPanel:s?.searchPanel,searchLoading:s?.searchLoading,searchError:s?.searchError,error:s?.error,hitIds:s?.searchPage?.hits?.map(h=>h.entryId)}})()').catch(()=>null)},null,2));throw error;}finally{stop();for(const response of responses)response.destroy();provider.closeAllConnections();await new Promise(done=>provider.close(done));}
