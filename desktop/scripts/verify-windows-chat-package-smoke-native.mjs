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
const editorRoundtrip = process.argv[4] === '--editor-roundtrip';
const cleanupRoundtrip = process.argv[4] === '--cleanup-roundtrip';
let cleanupEvidence = null;
assert.ok(!(visible && compare));
const packageEvidence=JSON.parse(await readFile(resolve(process.argv[3]),'utf8'));
const receipt={identifier:'com.nanzhufeng.ai.desktop.windowsacceptance',sha256:packageEvidence.rebuiltGuiSha256,executable:packageEvidence.rebuiltGuiRuntimeSource};
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
const jobMembership = [];
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
  if (process.env.NFAI_BOUNDED_JOB_NAME) {
    assert.match(process.env.NFAI_BOUNDED_JOB_NAME, /^NanfengAIBounded-[0-9a-f-]{36}$/);
    const member = ps(`Add-Type -TypeDefinition @'
using System; using System.ComponentModel; using System.Runtime.InteropServices;
public static class NfaiJobMembership {
 [DllImport("kernel32.dll",CharSet=CharSet.Unicode,SetLastError=true)] static extern IntPtr OpenJobObject(uint access,bool inherit,string name);
 [DllImport("kernel32.dll",SetLastError=true)] static extern IntPtr OpenProcess(uint access,bool inherit,uint pid);
 [DllImport("kernel32.dll",SetLastError=true)] static extern bool IsProcessInJob(IntPtr process,IntPtr job,out bool member);
 [DllImport("kernel32.dll")] static extern bool CloseHandle(IntPtr handle);
 public static bool Contains(uint pid,string name){
  IntPtr job=OpenJobObject(4,false,name),process=OpenProcess(0x1000,false,pid);
  try {if(job==IntPtr.Zero||process==IntPtr.Zero)throw new Win32Exception();bool value;if(!IsProcessInJob(process,job,out value))throw new Win32Exception();return value;}
  finally {if(process!=IntPtr.Zero)CloseHandle(process);if(job!=IntPtr.Zero)CloseHandle(job);}
 }
}
'@; [NfaiJobMembership]::Contains(${Number(launch.pid)},${quote(process.env.NFAI_BOUNDED_JOB_NAME)})`);
    assert.equal(member, 'True', 'owned Native must belong to the bounded Job');
    launch.boundedJobMembershipVerified = true;
    jobMembership.push({pid:Number(launch.pid),verified:true,job:process.env.NFAI_BOUNDED_JOB_NAME});
  }
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
const snapshots=[];
let editorEvidence = null;
async function editorSaveRoundtrip(workspaceId) {
 editorEvidence = {memoryPassed:false,reminderPassed:false};
 editorEvidence.stage='memory-overview';
 await evaluate('document.querySelector("[data-action=open-settings-page][data-page=memory-overview]").click();true');
 await until(()=>evaluate('Boolean(document.querySelector(".memory-reference-menu > summary"))'),'memory menu');
 await evaluate('document.querySelector(".memory-reference-menu > summary").click();true');
 await evaluate('document.querySelector("[data-action=edit-memory-summary]").click();true');
 await until(()=>evaluate('Boolean(document.querySelector("#memory-summary-editor"))'),'memory editor');
 editorEvidence.stage='memory-save';
 await evaluate(`(()=>{const set=value=>{const e=document.querySelector('#memory-summary-editor');e.value=value;e.dispatchEvent(new Event('input',{bubbles:true}))};set('SYNTHETIC_MEMORY_FIRST');document.querySelector('[data-action=save-memory-summary-editor]').click();set('SYNTHETIC_MEMORY_CONTINUED');document.querySelector('[data-action=save-memory-summary-editor]').click();return true})()`);
 await until(()=>evaluate('!globalThis.__nanfengDesktopWorkState.memorySummarySavePending'),'first memory settlement');
 const first = await invoke('read_desktop_workspace',{workspaceId});
 const memories=first.exchange.memory.filter(item=>item.status==='ACTIVE'&&item.scope==='GLOBAL'&&!item.scopeId);
 assert.equal(memories.length,1);assert.equal(memories[0].body,'SYNTHETIC_MEMORY_FIRST');assert.equal(memories[0].revision,1);
 const retained=await evaluate(`(()=>{const d=globalThis.__nanfengDesktopWorkState.dialog;return {memoryId:d?.memoryId,revision:d?.expectedRevision,value:document.querySelector('#memory-summary-editor')?.value}})()`);
 assert.equal(retained.memoryId,memories[0].id);assert.equal(retained.revision,1);assert.equal(retained.value,'SYNTHETIC_MEMORY_CONTINUED');
 await evaluate('document.querySelector("[data-action=save-memory-summary-editor]").click();true');
 await until(()=>evaluate('!globalThis.__nanfengDesktopWorkState.memorySummarySavePending&&!document.querySelector("#memory-summary-editor")'),'memory revision settlement');
 const second=await invoke('read_desktop_workspace',{workspaceId});const saved=second.exchange.memory.filter(item=>item.status==='ACTIVE'&&item.scope==='GLOBAL'&&!item.scopeId);
 assert.equal(saved.length,1);assert.equal(saved[0].id,memories[0].id);assert.equal(saved[0].revision,2);assert.equal(saved[0].body,'SYNTHETIC_MEMORY_CONTINUED');
 editorEvidence.memoryPassed=true;editorEvidence.memoryId=saved[0].id;editorEvidence.memoryRevision=2;
 editorEvidence.stage='reminder-entry';
 await writeFile(join(root,'editor-roundtrip-stages.json'),JSON.stringify(editorEvidence,null,2));
 await evaluate('document.querySelector("[data-action=show-chat]").click();true');
 await until(()=>evaluate('Boolean(document.querySelector("[data-action=show-reminders]"))'),'reminder entry');
 await evaluate('document.querySelector("[data-action=show-reminders]").click();true');
 await until(()=>evaluate('Boolean(document.querySelector("[data-action=create-manual-reminder-draft]"))'),'manual reminder');
 await evaluate('document.querySelector("[data-action=create-manual-reminder-draft]").click();true');
 await until(()=>evaluate('Boolean(document.querySelector("#reminder-title"))'),'reminder editor');
 await evaluate(`(()=>{const set=(id,value)=>{const e=document.querySelector('#'+id);e.value=value;e.dispatchEvent(new Event('input',{bubbles:true}))};document.querySelector('[data-action=set-reminder-schedule-mode][data-mode=ONCE]').click();set('reminder-title','合成提醒一');set('reminder-instruction','SYNTHETIC_REMINDER_FIRST');set('reminder-anchor-local','2030-01-01T12:00');set('reminder-timezone','UTC');document.querySelector('[data-action=confirm-reminder-draft]').click();set('reminder-title','合成提醒后续');document.querySelector('[data-action=confirm-reminder-draft]').click();return true})()`);
 await until(()=>evaluate('!globalThis.__nanfengDesktopWorkState.reminderEditorSavePending'),'reminder confirm settlement');
 const pendingEditor=await evaluate('(()=>{const s=globalThis.__nanfengDesktopWorkState;return {mode:s.reminderEditor?.mode,title:s.reminderEditor?.item?.title,planId:s.reminderEditor?.item?.planId,plans:s.reminders.plans.map(p=>({id:p.planId,title:p.title,updatedAtMs:p.updatedAtMs}))}})()');
 assert.equal(pendingEditor.plans.length,1);assert.equal(pendingEditor.plans[0].title,'合成提醒一');assert.equal(pendingEditor.title,'合成提醒后续');
 editorEvidence.reminderPlanId=pendingEditor.plans[0].id;editorEvidence.retainedReminderMode=pendingEditor.mode;
 editorEvidence.stage='reminder-confirmed-editor-binding';
 await writeFile(join(root,'editor-roundtrip-stages.json'),JSON.stringify(editorEvidence,null,2));
 assert.equal(pendingEditor.mode,'edit','continued reminder draft must adopt its confirmed plan identity');
 assert.equal(pendingEditor.planId,pendingEditor.plans[0].id);
 await evaluate('document.querySelector("[data-action=confirm-edit-reminder-plan]").click();true');
 await until(()=>evaluate('!globalThis.__nanfengDesktopWorkState.reminderEditorSavePending&&!document.querySelector("#reminder-title")'),'reminder update settlement');
 const final=await evaluate('globalThis.__nanfengDesktopWorkState.reminders');
 assert.equal(final.plans.length,1);assert.equal(final.plans[0].planId,pendingEditor.planId);assert.equal(final.plans[0].title,'合成提醒后续');
 editorEvidence.reminderPassed=true;editorEvidence.finalReminderProjection=final;
 await writeFile(join(root,'editor-roundtrip-stages.json'),JSON.stringify(editorEvidence,null,2));
}
async function conversationCleanupRoundtrip(workspaceId) {
 const ui=async selector=>{await until(()=>evaluate(`Boolean(document.querySelector(${JSON.stringify(selector)}))`),'UI '+selector);assert.equal(await evaluate(`document.querySelector(${JSON.stringify(selector)}).click();true`),true);};
 const baseline=await invoke('read_desktop_workspace',{workspaceId});
 const beforeWork=await evaluate("window.__TAURI__.core.invoke('ensure_desktop_local_workspace',{}, {headers:{'x-nanfeng-data-area':'WORK'}})");
 const ids=[];
 for(let i=0;i<3;i++){
  const created=await invoke('mutate_desktop_domain',{args:{intentId:`cleanup-${i}-${Date.now()}`,workspaceId,entity:'conversation',action:'create',objectId:null,expectedRevision:null,fields:{title:`合成清理 ${i}`,projectId:null,firstMessage:`SYNTHETIC_PURGE_BODY_${i}`}}});
  ids.push(created.objectId);
 }
 await evaluate('location.reload();true');
 await until(()=>evaluate('globalThis.__nanfengV2CommittedExchanges!==undefined'),'startup finished');
 await ui(`[data-conversation-row][data-id="${ids[0]}"] [data-action="open-conversation-row-menu"]`);
 await ui('[data-action="context-menu-delete"]');await ui('[data-action="confirm-conversation-delete"]');
 await until(async()=>(await invoke('read_desktop_workspace',{workspaceId})).exchange.conversations.some(c=>c.id===ids[0]&&c.deleted),'actual single recycle persisted');
 await until(()=>evaluate('!globalThis.__nanfengDesktopWorkState.dialog'),'single recycle settled');
 await ui('[data-action="toggle-conversation-batch-edit"]');
 for(const id of ids.slice(1))await ui(`[data-action="toggle-batch-conversation"][data-id="${id}"]`);
 await ui('[data-action="request-batch-conversation-delete"]');await ui('[data-action="confirm-conversation-batch-delete"]');
 await until(async()=>(await invoke('read_desktop_workspace',{workspaceId})).exchange.conversations.filter(c=>ids.includes(c.id)&&c.deleted).length===3,'actual batch recycle persisted');
 await until(()=>evaluate('!globalThis.__nanfengDesktopWorkState.dialog'),'batch recycle settled');
 await ui('[data-action="show-settings"]');await ui('[data-action="open-settings-page"][data-page="conversations"]');await ui('[data-action="open-settings-page"][data-page="recycle"]');
 const disclosure=`.conversation-lifecycle-actions[data-lifecycle-action-disclosure="${ids[0]}"]`;
 await ui(`${disclosure} summary`);await ui(`${disclosure} [data-action="open-conversation-permanent-delete"]`);
 await ui('[data-action="confirm-conversation-permanent-delete"]');
 await until(async()=>!(await invoke('read_desktop_workspace',{workspaceId})).exchange.conversations.some(c=>c.id===ids[0]),'single conversation permanently removed');
 await until(()=>evaluate('!globalThis.__nanfengDesktopWorkState.dialog'),'single cleanup settled');
 await ui('[data-action="open-conversation-bulk-cleanup"][data-scope="recycle"]');
 assert.match(await evaluate('document.querySelector(".dialog")?.innerText||""'),/将永久删除 2 个会话/);
 await ui('[data-action="confirm-conversation-bulk-cleanup"]');
 await until(async()=>!(await invoke('read_desktop_workspace',{workspaceId})).exchange.conversations.some(c=>ids.includes(c.id)),'batch permanently removed');
 await until(()=>evaluate('!globalThis.__nanfengDesktopWorkState.dialog'),'batch cleanup settled');
 const after=await invoke('read_desktop_workspace',{workspaceId});
 assert.deepEqual(after.exchange.conversations,baseline.exchange.conversations);
 for(const key of ['projects','knowledge','memory','relations'])assert.deepEqual(after.exchange[key],baseline.exchange[key]);
 const afterWork=await evaluate("window.__TAURI__.core.invoke('ensure_desktop_local_workspace',{}, {headers:{'x-nanfeng-data-area':'WORK'}})");assert.deepEqual(afterWork,beforeWork);
 cleanupEvidence={ids,workspaceId,actualUiSingleRecycle:true,actualUiBatchRecycle:true,actualUiSinglePermanentDelete:true,actualUiBulkPermanentDelete:true,originalConversationsAndDomainsExact:true,workExact:true,preciseNavigationInterleavingLayer:'VM_ONLY'};
}
async function visibleScope(){
 await until(()=>evaluate('Boolean(document.querySelector("[data-action=show-settings]"))'),'settings entry');
 const chat=await evaluate('(()=>({hidden:document.querySelectorAll("[data-action=show-work],[data-action=show-workspace],[data-action=show-projects],[data-action=show-knowledge],[data-action=show-memory],[data-action=show-p8-inspect],[data-action=select-workspace]").length,newChat:!!document.querySelector("[data-action=new-chat]")}))()');
 assert.equal(chat.hidden,0);assert.equal(chat.newChat,true);
 await evaluate('document.querySelector("[data-action=show-settings]").click();true');
 await until(()=>evaluate('Boolean(document.querySelector("[data-action=open-settings-picker][data-picker=themeColor]"))'),'settings loaded');
 const settings=await evaluate('(()=>({hidden:document.querySelectorAll("[data-action=open-settings-page][data-page=workspace],[data-action=open-settings-page][data-page=development],[data-key=historyLibraryEnabled]").length,memory:!!document.querySelector("[data-action=toggle-product-setting][data-key=memoryEnabled]")}))()');assert.equal(settings.hidden,0);
 if(!settings.memory){if(await evaluate('Boolean(document.querySelector("[data-action=settings-back]"))'))await evaluate('document.querySelector("[data-action=settings-back]").click();true');await until(()=>evaluate('Boolean(document.querySelector("[data-action=open-settings-page][data-page=personalization]"))'),'personalization entry');await evaluate('document.querySelector("[data-action=open-settings-page][data-page=personalization]").click();true');}
 await until(()=>evaluate('Boolean(document.querySelector("[data-action=toggle-product-setting][data-key=memoryEnabled]"))'),'chat memory retained');snapshots.push({chat,settings,memoryVisible:true});
}
try{
 await start();const workspaceId=(await invoke('ensure_desktop_local_workspace')).summary.id;await visibleScope();if(editorRoundtrip)await editorSaveRoundtrip(workspaceId);if(cleanupRoundtrip)await conversationCleanupRoundtrip(workspaceId);const beforeExchange=await invoke('read_desktop_workspace',{workspaceId});const before=await invoke('read_desktop_app_settings');await normalClose();
 await start(true);assert.deepEqual(await invoke('read_desktop_workspace',{workspaceId}),beforeExchange);assert.deepEqual(await invoke('read_desktop_app_settings'),before);await visibleScope();assert.equal(requests,0);
 if(editorRoundtrip)assert.deepEqual(await evaluate('globalThis.__nanfengDesktopWorkState.reminders'),editorEvidence.finalReminderProjection);
 await normalClose();
 if(process.env.NFAI_BOUNDED_JOB_NAME) assert.equal(jobMembership.length,2);
 const result={root,candidateSha256:receipt.sha256,workspaceId,pids,windows,closes,snapshots,coldExact:true,providerRequests:requests,editorEvidence,cleanupEvidence,boundedJobMembership:jobMembership,installationExecuted:false,setupPayloadExtractionExecuted:false};await writeFile(join(root,'chat-package-smoke-native.json'),JSON.stringify(result,null,2));console.log(JSON.stringify(result));
}catch(error){
 const failure={root,candidateSha256:receipt.sha256,error:String(error),editorEvidence,closes,normalFailureClose:false};
 if(launch){try{await normalClose();failure.normalFailureClose=true}catch(closeError){failure.closeError=String(closeError)}}
 if(root)await writeFile(join(root,'chat-editor-roundtrip-failure.json'),JSON.stringify(failure,null,2));throw error;
}finally{stop();for(const response of responses)response.destroy();provider.closeAllConnections();await new Promise(done=>provider.close(done));}
