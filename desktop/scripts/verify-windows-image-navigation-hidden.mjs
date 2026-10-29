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
assert.ok(process.argv[2], 'Explicit owned synthetic parent required');
const parent = resolve(process.argv[2]);
const compare = false;assert.ok(process.execArgv.includes('--max-old-space-size=2048'));
const visible = false;
assert.ok(!(visible && compare));
const receipt = JSON.parse(await readFile(join(desktop, 'output/windows-acceptance-release/current.json'), 'utf8'));
assert.equal(receipt.identifier, 'com.nanzhufeng.ai.desktop.windowsacceptance');
assert.equal(createHash('sha256').update(await readFile(receipt.executable)).digest('hex').toUpperCase(), receipt.sha256);
assert.equal(receipt.buildProfile, 'release');
const executable = resolve(desktop, 'output/windows-acceptance-binaries', receipt.sha256, 'nanfeng-ai-desktop-spike.exe');
const helper = join(desktop, 'scripts/windows-acceptance-cdp-eval.mjs');
let requests = 0;let closedConnections=0;const responses=[];const closes=[];const partialText='SYNTHETIC_STREAM_STOP_PARTIAL 完整保留片段';
const provider = http.createServer(async (request, response) => {
 for await(const _ of request){} requests++;
 response.writeHead(200,{'Content-Type':'text/event-stream'});
 response.end(`data: ${JSON.stringify({model:'fixture-gallery',choices:[{delta:{content:'Synthetic gallery saved'},finish_reason:'stop'}],usage:{prompt_tokens:6,completion_tokens:2}})}\n\ndata: [DONE]\n\n`);
});
await new Promise(done => provider.listen(0, '127.0.0.1', done));
const endpoint = `http://127.0.0.1:${provider.address().port}`;
let launch, root, port;
const pids = [];
const windows = [];
const quote = value => `'${String(value).replaceAll("'", "''")}'`;
const ps = code => execFileSync('pwsh', ['-NoProfile', '-Command', `[Console]::OutputEncoding=[Text.UTF8Encoding]::new($false);${code}`], { encoding: 'utf8', windowsHide: true, timeout: 60000 }).trim();
const privateDesktopGuard=JSON.parse(ps(`& ${quote(join(desktop,'scripts/assert-windows-private-desktop.ps1'))}`));assert.equal(privateDesktopGuard.verified,true);
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
  const code = `[Console]::OutputEncoding=[Text.UTF8Encoding]::new($false); & ${quote(join(desktop, 'scripts/start-windows-acceptance.ps1'))} -RootParent ${quote(parent)} -ExecutablePath ${quote(receipt.executable)} -StartupMode normal -DebugPort ${port} -OrdinaryChatTestEndpoint ${quote(endpoint)}${reopen ? ` -Root ${quote(root)}` : ''}`;
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
  assert.equal(launch.visible,false);assert.equal(launch.sha256, receipt.sha256);
  assert.equal(resolve(launch.executable), executable);
  root = resolve(launch.root);
  assert.ok(root.startsWith(parent + sep));
  assert.equal(JSON.parse(await readFile(join(root, '.nanfeng-windows-acceptance.json'), 'utf8')).identifier, receipt.identifier);
  await until(() => evaluate('Boolean(window.__TAURI__?.core?.invoke)'), 'native IPC unavailable');
  await until(() => evaluate('globalThis.__nanfengDesktopWorkState?.runtimeInfo?.dataAreasReady===true'), 'native data areas unavailable');

}
function stop() {
  if (!launch) return;
  const pid = Number(launch.pid);
  ps(`$p=Get-CimInstance Win32_Process -Filter 'ProcessId=${pid}';if($p){if([IO.Path]::GetFullPath([string]$p.ExecutablePath) -ne ${quote(executable)} -or (Get-FileHash -LiteralPath ${quote(executable)} -Algorithm SHA256).Hash -ne ${quote(receipt.sha256)}){throw 'PID mismatch'};$owned=Get-Process -Id ${pid} -ErrorAction Stop;$started=[DateTime]::Parse(${quote(launch.processStartedAt)}).ToUniversalTime();if([Math]::Abs(($owned.StartTime.ToUniversalTime()-$started).TotalMilliseconds) -gt 20){throw 'PID start time mismatch'};Stop-Process -Id ${pid} -Force}`);
  pids.push(pid); launch = null;
}
async function normalClose(){
 const pid=Number(launch.pid);
 const value=JSON.parse(ps(`& ${quote(join(desktop,'scripts/close-windows-hidden-acceptance.ps1'))} -ProcessId ${pid} -Executable ${quote(executable)} -Sha256 ${quote(receipt.sha256)} -StartedAt ${quote(launch.processStartedAt)}`));
 assert.equal(value.visibleWindows,0);assert.equal(value.closePosted,true);
 await until(()=>ps(`@(Get-CimInstance Win32_Process -Filter "ProcessId=${pid}").Count`)==='0','natural app exit');
 await until(()=>ps(`@(Get-CimInstance Win32_Process -Filter "Name='msedgewebview2.exe'" | Where-Object {$_.CommandLine -and $_.CommandLine.Contains(${quote(launch.webviewProfile)})}).Count`)==='0','owned profile exit');
 pids.push(pid);launch=null;closes.push(value);
}
const result={pass:false,candidateSha256:receipt.sha256,backgroundOnly:true,realService:false,privateDesktopGuard};
try {
 await start();result.runtimeInfo=await invoke('read_desktop_runtime_info');
 const workspaceId=(await invoke('ensure_desktop_local_workspace')).summary.id;
 const files=['icon.png','nanfeng_ai_icon_master.png'];const assets=[];
 for(const file of files)assets.push(await invoke('import_desktop_conversation_attachment',{args:{workspaceId,selectedPath:join(desktop,'src-tauri/icons',file)}}));
 assert.equal(new Set(assets.map(a=>a.id)).size,2);
 await until(()=>evaluate('Boolean(document.querySelector("#chat-composer"))'),'composer');
 await evaluate('document.querySelector("[data-action=toggle-composer-add]").click();true');
 await until(()=>evaluate('Boolean(document.querySelector("[data-action=toggle-conversation-web-search]"))'),'web preference');
 if(await evaluate('document.querySelector("[data-action=toggle-conversation-web-search]").getAttribute("aria-checked")')!=='false')await evaluate('document.querySelector("[data-action=toggle-conversation-web-search]").click();true');
 await evaluate('document.querySelector("[data-action=close-composer-add]").click();true');
 await evaluate(`(()=>{const s=globalThis.__nanfengDesktopWorkState;s.composerAttachments=${JSON.stringify(assets)};const f=document.querySelector('#chat-composer');f.value='Synthetic two-image gallery';f.dispatchEvent(new Event('input',{bubbles:true}));return true})()`);
 await until(()=>evaluate('Boolean(document.querySelector("[data-action=save-local-message]:not([disabled])"))'),'send enabled');
 await evaluate('document.querySelector("[data-action=save-local-message]").click();true');
 const conversation=await until(async()=>{const w=await invoke('read_desktop_workspace',{workspaceId});return w.exchange.conversations.find(c=>{const leaf=c.messages.find(m=>m.id===c.currentLeafId);return leaf?.role==='assistant'&&leaf.delivery==='COMPLETE'&&c.messages.some(m=>m.role==='user'&&m.blocks.some(b=>b.kind==='TEXT'&&b.text==='Synthetic two-image gallery')&&assets.every(asset=>m.blocks.some(b=>b.kind==='ASSET_REF'&&b.asset?.id===asset.id)));});},'saved two-image message');
 assert.equal(requests,1);const saved=await invoke('read_desktop_workspace',{workspaceId});
 const openSelector=`.chat-scroll [data-action=open-image-preview][data-attachment-id="${assets[0].id}"]`;
 await until(()=>evaluate(`Boolean(document.querySelector(${JSON.stringify(openSelector)}))`),'first saved image');
 await evaluate(`document.querySelector(${JSON.stringify(openSelector)}).click();true`);
 const preview=()=>evaluate(`(()=>{const p=globalThis.__nanfengDesktopWorkState.imagePreview;return p&&{attachmentId:p.attachmentId,imageIds:p.imageIds,loading:!!p.loading,ready:!!p.dataUrl,error:p.error||p.navigationError||'',index:document.querySelector('.image-preview-index')?.textContent}})()`);
 result.first=await until(async()=>{const p=await preview();return p?.ready&&p.attachmentId===assets[0].id?p:null;},'initial image decoded');
 assert.deepEqual(result.first.imageIds,assets.map(a=>a.id));
 const next='[data-action=image-preview-next]',previous='[data-action=image-preview-previous]';
 assert.equal(await evaluate(`document.querySelector(${JSON.stringify(next)}).disabled`),false);
 await evaluate(`document.querySelector(${JSON.stringify(next)}).click();true`);
 result.next=await until(async()=>{const p=await preview();return p?.ready&&p.attachmentId===assets[1].id?p:null;},'actual Next button changed image');
 assert.equal(result.next.index,'2 / 2');assert.equal(await evaluate(`document.querySelector(${JSON.stringify(next)}).disabled`),true);
 await evaluate(`document.querySelector(${JSON.stringify(previous)}).click();true`);
 result.previous=await until(async()=>{const p=await preview();return p?.ready&&p.attachmentId===assets[0].id?p:null;},'actual Previous button changed image');
 assert.equal(result.previous.index,'1 / 2');assert.equal(await evaluate(`document.querySelector(${JSON.stringify(previous)}).disabled`),true);
 await evaluate(`document.dispatchEvent(new KeyboardEvent('keydown',{key:'ArrowRight',bubbles:true,cancelable:true}));true`);
 result.keyboardNext=await until(async()=>{const p=await preview();return p?.ready&&p.attachmentId===assets[1].id?p:null;},'keyboard still uses the same next image');
 await evaluate(`document.dispatchEvent(new KeyboardEvent('keydown',{key:'ArrowLeft',bubbles:true,cancelable:true}));true`);
 result.keyboardPrevious=await until(async()=>{const p=await preview();return p?.ready&&p.attachmentId===assets[0].id?p:null;},'keyboard still uses the same previous image');

 assert.deepEqual(await invoke('read_desktop_workspace',{workspaceId}),saved);assert.equal(requests,1);
 await evaluate('document.querySelector("[data-action=close-image-preview]").click();true');
 await until(()=>evaluate('!globalThis.__nanfengDesktopWorkState.imagePreview'),'preview closed');
 await normalClose();await start(true);assert.deepEqual(await invoke('read_desktop_workspace',{workspaceId}),saved);assert.equal(requests,1);await normalClose();
 result.pass=true;result.assets=assets.map(a=>({id:a.id,mimeType:a.mimeType}));result.conversationId=conversation.id;result.coldExact=true;
} catch(error) {result.error=String(error.stack||error);process.exitCode=1;}
finally {if(launch){try{await normalClose();}catch{stop();}}provider.closeAllConnections();await new Promise(done=>provider.close(done));result.root=root;result.pids=pids;result.closes=closes;result.providerRequests=requests;if(root)await writeFile(join(root,'image-navigation-native.json'),JSON.stringify(result,null,2));}
console.log(JSON.stringify({pass:result.pass,root,pids,providerRequests:requests,error:result.error}));
