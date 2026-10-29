import assert from 'node:assert/strict';
import { createHash } from 'node:crypto';
import { execFileSync, spawn } from 'node:child_process';
import { copyFile, mkdir, readFile, writeFile } from 'node:fs/promises';
import { dirname, join, resolve, sep } from 'node:path';
import { fileURLToPath } from 'node:url';

if (process.platform !== 'win32') throw new Error('仅供 Windows 隔离候选验收。');
assert.ok(process.execArgv.includes('--max-old-space-size=2048'));
const desktopRoot = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const rootParent = resolve(process.argv.slice(2).find(arg => !arg.startsWith('--')) || 'D:\\CodexTemp');
const receipt = JSON.parse(await readFile(join(desktopRoot, 'output/windows-acceptance/current.json'), 'utf8'));
assert.equal(receipt.identifier, 'com.nanzhufeng.ai.desktop.windowsacceptance');
const executable = resolve(desktopRoot, 'output/windows-acceptance', receipt.sha256, 'NanfengAI-Windows-Acceptance.exe');
const runningExecutable = resolve(desktopRoot, 'output/windows-acceptance-binaries', receipt.sha256, 'nanfeng-ai-desktop-spike.exe');
assert.equal(resolve(receipt.executable), executable);
assert.equal(createHash('sha256').update(await readFile(executable)).digest('hex').toUpperCase(), receipt.sha256);
const quote = value => `'${String(value).replaceAll("'", "''")}'`;
const ps = script => execFileSync('pwsh', ['-NoProfile', '-Command', script], { encoding: 'utf8', timeout: 60000 }).trim();
async function reservePort() {
  const server = (await import('node:http')).createServer();
  await new Promise(done => server.listen(0, '127.0.0.1', done));
  const port = server.address().port;
  await new Promise(done => server.close(done));
  return port;
}
let launch = null;
let root = '';
const stoppedPids = [];
async function start(reopen = false) {
  const port = await reservePort();
  const args = ['-ExecutablePath', executable, '-RootParent', rootParent, '-StartupMode', 'normal', '-Visible', '-DebugPort', String(port)];
  if (reopen) args.push('-Root', root);
  const result = await new Promise((done, fail) => {
    const child = spawn('pwsh', ['-NoProfile', '-File', join(desktopRoot, 'scripts/start-windows-acceptance.ps1'), ...args], { stdio: ['ignore', 'pipe', 'pipe'] });
    let output = '';
    let error = '';
    const timer = setTimeout(() => { child.kill(); fail(new Error(`启动超时：${error}`)); }, 30000);
    child.stdout.on('data', chunk => {
      output += chunk.toString();
      const line = output.split(/\r?\n/).find(item => item.trim().startsWith('{') && item.includes('"pid"'));
      if (!line) return;
      clearTimeout(timer);
      try { done(JSON.parse(line)); } catch (cause) { fail(cause); }
      child.stdout.destroy();
      child.stderr.destroy();
      child.unref();
    });
    child.stderr.on('data', chunk => { error += chunk.toString(); });
    child.on('error', cause => { clearTimeout(timer); fail(cause); });
    child.on('exit', code => { if (!output.includes('"pid"')) { clearTimeout(timer); fail(new Error(`启动失败 ${code}：${error}`)); } });
  });
  assert.equal(result.sha256, receipt.sha256);
  assert.equal(resolve(result.executable), runningExecutable);
  launch = result;
  root = resolve(result.root);
  assert.ok(root.startsWith(rootParent + sep));
  assert.equal(JSON.parse(await readFile(join(root, '.nanfeng-windows-acceptance.json'), 'utf8')).identifier, receipt.identifier);
  return port;
}
function stop() {
  if (!launch) return;
  const pid = Number(launch.pid);
  ps(`$p=Get-CimInstance Win32_Process -Filter "ProcessId=${pid}"; if ($p) { if ([IO.Path]::GetFullPath([string]$p.ExecutablePath) -ne ${quote(runningExecutable)} -or (Get-FileHash -LiteralPath ${quote(runningExecutable)} -Algorithm SHA256).Hash -ne ${quote(receipt.sha256)}) { throw 'PID identity mismatch' }; Stop-Process -Id ${pid} -Force -ErrorAction Stop }`);
  stoppedPids.push(pid);
  launch = null;
}
async function evaluate(port, expression) {
  const tabs = await fetch(`http://127.0.0.1:${port}/json/list`).then(response => response.json());
  const tab = tabs.find(item => item.type === 'page' && item.url === 'http://tauri.localhost/');
  assert.ok(tab, 'WebView2 页面未就绪');
  const socket = new WebSocket(tab.webSocketDebuggerUrl);
  try {
    await new Promise((done, fail) => { socket.addEventListener('open', done, { once: true }); socket.addEventListener('error', fail, { once: true }); });
    return await new Promise((done, fail) => {
      const timer = setTimeout(() => fail(new Error('CDP evaluation timed out')), 20000);
      socket.addEventListener('message', event => {
        const message = JSON.parse(event.data);
        if (message.id !== 1) return;
        clearTimeout(timer);
        if (message.error || message.result?.exceptionDetails) fail(new Error(message.result?.exceptionDetails?.text || message.error?.message));
        else done(message.result?.result?.value);
      });
      socket.send(JSON.stringify({ id: 1, method: 'Runtime.evaluate', params: { expression, awaitPromise: true, returnByValue: true } }));
    });
  } finally { socket.close(); }
}
async function invoke(port, area, command, args = {}) {
  const value = await evaluate(port, `window.__TAURI__.core.invoke(${JSON.stringify(command)},${JSON.stringify(args)},${JSON.stringify({ headers: { 'x-nanfeng-data-area': area } })}).catch(error=>({__error:String(error)}))`);
  if (value?.__error) throw new Error(`${command}: ${value.__error}`);
  return value;
}
async function until(read, label) {
  const deadline = Date.now() + 30000;
  while (Date.now() < deadline) {
    try { const value = await read(); if (value) return value; } catch { /* Startup or redraw. */ }
    await new Promise(done => setTimeout(done, 200));
  }
  throw new Error(`${label} 未出现`);
}
const click = (port, selector) => evaluate(port, `(()=>{const node=document.querySelector(${JSON.stringify(selector)});if(!node)return false;node.click();return true})()`);
const dialogOpen = () => {
  const raw = ps(`& ${quote(join(desktopRoot, 'scripts/inspect-windows-file-dialog.ps1'))} -ProcessId ${Number(launch.pid)}`);
  const windows = raw ? JSON.parse(raw) : [];
  return windows.some(item => item.children?.some(child => child.class === '#32770'));
};


const normalCloses=[],checkpoints=[];
async function snapshot(port){const out={};for(const area of ['CHAT','WORK']){out[area]=[];for(const ws of await invoke(port,area,'list_desktop_workspaces'))out[area].push(await invoke(port,area,'read_desktop_workspace',{workspaceId:ws.id}));}return out;}
async function normalClose(port,restartGate=false){
 if(!restartGate)checkpoints.push(await snapshot(port));const pid=Number(launch.pid);
 const info=JSON.parse(ps(`$p=Get-CimInstance Win32_Process -Filter "ProcessId=${pid}";if(!$p -or [IO.Path]::GetFullPath([string]$p.ExecutablePath) -ne ${quote(runningExecutable)} -or (Get-FileHash -LiteralPath ${quote(runningExecutable)}).Hash -ne ${quote(receipt.sha256)}){throw 'PID identity mismatch'};$n=Get-Process -Id ${pid};@{title=$n.MainWindowTitle;responding=$n.Responding;accepted=$n.CloseMainWindow()}|ConvertTo-Json -Compress`));
 assert.ok(info.title&&info.responding&&info.accepted);await until(()=>ps(`@(Get-CimInstance Win32_Process -Filter "ProcessId=${pid}").Count`)==='0','自然关闭');await until(()=>ps(`@(Get-CimInstance Win32_Process -Filter "Name='msedgewebview2.exe'"|Where-Object {$_.CommandLine -and $_.CommandLine.Contains(${quote(launch.webviewProfile)})}).Count`)==='0','本根WebView退出');stoppedPids.push(pid);normalCloses.push({...info,pid});launch=null;
}

const evidence=resolve(desktopRoot,'../docs/review/20261004-chat-backup-restore');let stage='start';const facts={areas:{},refusals:[]};
const hash=b=>createHash('sha256').update(b).digest('hex');
async function ready(port){await until(()=>evaluate(port,'Boolean(window.__TAURI__?.core?.invoke&&globalThis.__nanfengDesktopWorkState?.current&&globalThis.__nanfengDesktopWorkState?.runtimeInfo?.dataAreasReady)'),'NORMAL ready');}
async function page(port,area){
 assert.equal(area,'CHAT');await evaluate(port,'location.reload();true');await ready(port);
 if(!await evaluate(port,'Boolean(document.querySelector("[data-action=import-local-backup]"))')){
  await until(()=>evaluate(port,'Boolean(document.querySelector("[data-action=show-settings]")||document.querySelector("[data-action=settings-back]")||document.querySelector("[data-action=open-settings-page][data-page=data]"))'),'settings route');
  if(await evaluate(port,'Boolean(document.querySelector("[data-action=show-settings]"))'))assert.equal(await click(port,'[data-action=show-settings]'),true);
  await until(()=>evaluate(port,'Boolean(document.querySelector("[data-action=open-settings-page][data-page=data]")||document.querySelector("[data-action=settings-back]"))'),'settings entry');
  if(!await evaluate(port,'Boolean(document.querySelector("[data-action=open-settings-page][data-page=data]"))'))assert.equal(await click(port,'[data-action=settings-back]'),true);
  await until(()=>evaluate(port,'Boolean(document.querySelector("[data-action=open-settings-page][data-page=data]"))'),'data entry');assert.equal(await click(port,'[data-action=open-settings-page][data-page=data]'),true);
 }
 await until(()=>evaluate(port,'Boolean(document.querySelector("[data-action=import-local-backup]"))'),'current restore entry');assert.equal(await evaluate(port,'globalThis.__nanfengDesktopWorkState.localBackupArea'),'CHAT');assert.equal(await evaluate(port,'document.querySelectorAll("[data-action=select-local-backup-area]").length'),0);
}


async function picker(port,action,path){assert.equal(await click(port,'[data-action="import-local-backup"]'),true);await until(dialogOpen,'actual restore file picker');facts.pickers??=[];facts.pickers.push(JSON.parse(ps(`& ${quote(join(desktopRoot,'scripts/operate-windows-file-dialog.ps1'))} -ProcessId ${Number(launch.pid)} -Action ${action} -AcceptanceRoot ${quote(root)} ${path?'-SelectedPath '+quote(path):''}`)));}
async function checkAssets(){for(const area of ['CHAT','WORK']){const item=facts.areas[area];assert.equal(hash(await readFile(item.privateAsset)),item.assetHash);}}
try{
 let port=await start();facts.root=root;await ready(port);
 for(const area of ['CHAT','WORK']){
  const workspaceId=(await invoke(port,area,'ensure_desktop_local_workspace')).summary.id;
  const path=join(root,`合成备份-${area}.txt`);const bytes=Buffer.from(`SYNTHETIC-BACKUP-${area}-ONLY\n原件全文保留\n`);await writeFile(path,bytes,{flag:'wx'});
  const attachment=await invoke(port,area,'import_desktop_conversation_attachment',{args:{workspaceId,selectedPath:path}});
  const made=await invoke(port,area,'mutate_desktop_domain',{args:{intentId:`synthetic-backup-${area.toLowerCase()}`,workspaceId,entity:'conversation',action:'create',fields:{title:`合成备份 ${area}`,projectId:null,firstMessage:`${area} 合成永久正文`,attachmentIds:[attachment.id]}}});
  await invoke(port,area,'save_desktop_conversation_draft',{args:{workspaceId,conversationId:made.objectId,text:`${area} 永久草稿`,attachmentIds:[],modelId:null,toneOverride:null,webSearchOverride:null}});
  const backup=join(root,`synthetic-normal-${area}.nfai-backup`);const artifact=await invoke(port,area,'export_desktop_local_backup',{selectedPath:backup});assert.equal(hash(await readFile(backup)).toUpperCase(),artifact.sha256.toUpperCase());
  const privateAsset='assets/'+attachment.sha256;
  facts.areas[area]={workspaceId,conversationId:made.objectId,attachment,privateAsset:join(area==='CHAT'?root:join(root,'work-area'),privateAsset),assetHash:hash(bytes),backup,artifact,draft:await invoke(port,area,'read_desktop_conversation_draft',{workspaceId,conversationId:made.objectId})};
 }
 facts.before=await snapshot(port);await checkAssets();
 for(const area of ['CHAT']){
  const item=facts.areas[area],peer=area==='CHAT'?'WORK':'CHAT';stage=area+' refusal and explicit restore';
  await page(port,area);assert.equal(await evaluate(port,"document.querySelectorAll('[data-action=select-local-backup-area][data-area=WORK]').length"),0);await picker(port,'cancel');await until(()=>evaluate(port,'!document.querySelector("[data-action=restore-local-backup]")'),'picker cancel');for(const a of ['CHAT','WORK'])assert.deepEqual((await snapshot(port))[a].map(w=>w.exchange),facts.before[a].map(w=>w.exchange));
  await assert.rejects(()=>invoke(port,area,'preflight_desktop_local_backup',{selectedPath:facts.areas[peer].backup}));facts.refusals.push({area,crossArea:true});await invoke(port,area,'cancel_desktop_local_restore');
  const changed=await invoke(port,area,'mutate_desktop_domain',{args:{intentId:`synthetic-after-backup-${area.toLowerCase()}`,workspaceId:item.workspaceId,entity:'project',action:'create',fields:{title:`${area} 备份后改变`,description:'恢复应移除'}}});item.removedProjectId=changed.objectId;
  await invoke(port,area,'save_desktop_conversation_draft',{args:{workspaceId:item.workspaceId,conversationId:item.conversationId,text:`${area} 备份后草稿`,attachmentIds:[],modelId:null,toneOverride:null,webSearchOverride:null}});
  await writeFile(item.privateAsset,Buffer.from(`SYNTHETIC-CHANGED-${area}`));
  await page(port,area);await picker(port,'select',item.backup);await until(()=>evaluate(port,'Boolean(document.querySelector(".android-settings-backup-preflight"))'),'actual preflight');assert.equal(await evaluate(port,'document.querySelector("[data-action=restore-local-backup]").disabled'),true);
  const changedSnapshot=await snapshot(port);assert.equal(await click(port,'[data-action="cancel-local-restore"]'),true);await until(()=>evaluate(port,'!document.querySelector(".android-settings-backup-preflight")'),'cancel restore');assert.deepEqual(await snapshot(port),changedSnapshot);item.cancelReadOnly=true;
  await picker(port,'select',item.backup);await until(()=>evaluate(port,'Boolean(document.querySelector("[data-action=select-local-backup-replace]"))'),'strong replace choice');assert.equal(await click(port,'[data-action="select-local-backup-replace"]'),true);await until(()=>evaluate(port,'document.querySelector("[data-action=restore-local-backup]")?.disabled===false'),'explicit restore enabled');assert.equal(await click(port,'[data-action="restore-local-backup"]'),true);
  await until(()=>invoke(port,area,'read_desktop_local_backup_status').then(s=>s.restartRequired&&s),'restart receipt');item.beforeRestartStatus=await invoke(port,area,'read_desktop_local_backup_status');assert.equal(item.beforeRestartStatus.restartRequired,true);
  const peerRead=[];for(const w of await invoke(port,peer,'list_desktop_workspaces'))peerRead.push(await invoke(port,peer,'read_desktop_workspace',{workspaceId:w.id}));assert.deepEqual(peerRead.map(w=>w.exchange),facts.before[peer].map(w=>w.exchange));await normalClose(port,true);
  port=await start(true);await ready(port);item.coldStatus=await invoke(port,area,'read_desktop_local_backup_status');assert.equal(item.coldStatus.operation,'COMPLETED');assert.equal(item.coldStatus.restartRequired,false);assert.equal(item.coldStatus.rollbackPending,false);
  for(const a of ['CHAT','WORK'])assert.deepEqual((await snapshot(port))[a].map(w=>w.exchange),facts.before[a].map(w=>w.exchange));assert.deepEqual(await invoke(port,area,'read_desktop_conversation_draft',{workspaceId:item.workspaceId,conversationId:item.conversationId}),item.draft);await checkAssets();item.otherAreaPreserved=true;
 }
 facts.after=await snapshot(port);await normalClose(port);port=await start(true);await ready(port);assert.deepEqual(await snapshot(port),checkpoints.at(-1));await checkAssets();assert.equal((await invoke(port,'CHAT','read_desktop_local_backup_status')).operation,'COMPLETED');assert.equal((await invoke(port,'WORK','read_desktop_local_backup_status')).restartRequired,false);await normalClose(port);
 facts.normalCloses=normalCloses;facts.sha256=receipt.sha256;facts.pass=true;await writeFile(join(evidence,'native-current.json'),JSON.stringify(facts,null,2)+'\n');console.log(JSON.stringify({ok:true,root,sha256:receipt.sha256,normalCloses:normalCloses.length}));
}catch(error){await writeFile(join(evidence,`native-failed-${Date.now()}.json`),JSON.stringify({...facts,stage,error:String(error),stack:error.stack,normalCloses,sha256:receipt.sha256},null,2)+'\n');throw error;}finally{stop();}
