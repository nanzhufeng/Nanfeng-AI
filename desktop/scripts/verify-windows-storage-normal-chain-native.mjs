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
  const args = ['-ExecutablePath', executable, '-RootParent', rootParent, '-StartupMode', 'normal', '-StorageLocation', '-Visible', '-DebugPort', String(port)];
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

const evidence=resolve(desktopRoot,'../docs/review/20261004-storage-normal-chain');const facts={refusals:[]};let stage='start';const hash=b=>createHash('sha256').update(b).digest('hex');const canonical=p=>resolve(String(p).replace(/^\\\\\?\\/,'' )).toLowerCase();
async function ready(port){await until(()=>evaluate(port,'Boolean(window.__TAURI__?.core?.invoke&&globalThis.__nanfengDesktopWorkState?.current&&globalThis.__nanfengDesktopWorkState?.runtimeInfo?.dataAreasReady)'),'NORMAL ready');}
async function tree(path){return JSON.parse(execFileSync('python',['-c',"import pathlib,hashlib,json,sys;r=pathlib.Path(sys.argv[1]);print(json.dumps({str(p.relative_to(r)):hashlib.sha256(p.read_bytes()).hexdigest() for p in r.rglob('*') if p.is_file() and p.name!='.runtime-owner.lock'}))",path],{encoding:'utf8',timeout:30000}));}
async function privacy(port){await evaluate(port,'location.reload();true');await new Promise(ok=>setTimeout(ok,500));await ready(port);await until(()=>evaluate(port,'Boolean(document.querySelector("[data-action=show-settings]"))'),'settings button');if(!await evaluate(port,'Boolean(document.querySelector(".storage-location-card"))')){assert.equal(await click(port,'[data-action="show-settings"]'),true);await until(()=>evaluate(port,'Boolean(document.querySelector(".storage-location-card")||document.querySelector("[data-action=open-settings-page][data-page=privacy]"))'),'settings privacy');if(!await evaluate(port,'Boolean(document.querySelector(".storage-location-card"))'))assert.equal(await click(port,'[data-action="open-settings-page"][data-page="privacy"]'),true);}await until(()=>evaluate(port,'Boolean(document.querySelector(".storage-location-path")?.textContent&&!document.querySelector(".storage-location-path").textContent.includes("读取中"))'),'visible path');return evaluate(port,'Array.from(document.querySelectorAll(".storage-location-path"),n=>n.textContent)');}
try{
 let port=await start();facts.root=root;await ready(port);const old=join(root,'data'),target=join(root,'中文迁移目标');facts.old=old;facts.target=target;await mkdir(target);
 facts.areas={};for(const area of ['CHAT','WORK']){const workspaceId=(await invoke(port,area,'ensure_desktop_local_workspace')).summary.id;const path=join(root,`合成迁移-${area}.txt`),bytes=Buffer.from(`SYNTHETIC-MIGRATION-${area}\n两区保留`);await writeFile(path,bytes,{flag:'wx'});const attachment=await invoke(port,area,'import_desktop_conversation_attachment',{args:{workspaceId,selectedPath:path}});const made=await invoke(port,area,'mutate_desktop_domain',{args:{intentId:`synthetic-migration-${area.toLowerCase()}`,workspaceId,entity:'conversation',action:'create',fields:{title:`合成迁移 ${area}`,projectId:null,firstMessage:`迁移 ${area} 原正文`,attachmentIds:[attachment.id]}}});await invoke(port,area,'save_desktop_conversation_draft',{args:{workspaceId,conversationId:made.objectId,text:`迁移 ${area} 永久草稿`,attachmentIds:[],modelId:null,toneOverride:null,webSearchOverride:null}});facts.areas[area]={workspaceId,conversationId:made.objectId,attachment,hash:hash(bytes),draft:await invoke(port,area,'read_desktop_conversation_draft',{workspaceId,conversationId:made.objectId})};}
 facts.before=await snapshot(port);facts.originalPathUI=await privacy(port);assert.ok(facts.originalPathUI.some(p=>canonical(p)===canonical(old)));
 // Native system directory picker cancel is read-only; path publication uses the real owner below.
 assert.equal(await click(port,'[data-action="choose-storage-location"]'),true);await until(dialogOpen,'native directory picker');facts.pickerCancel=JSON.parse(ps(`& ${quote(join(desktopRoot,'scripts/operate-windows-file-dialog.ps1'))} -ProcessId ${Number(launch.pid)} -Action cancel`));await until(()=>evaluate(port,'Boolean(document.querySelector(".storage-location-path"))'),'picker cancelled');assert.deepEqual(await snapshot(port),facts.before);
 const nested=join(old,'synthetic-nested');await mkdir(nested);await assert.rejects(()=>invoke(port,'WORK','choose_desktop_storage_location',{selectedPath:nested}),/相互包含/);facts.refusals.push('nested path');await assert.rejects(()=>invoke(port,'WORK','choose_desktop_storage_location',{selectedPath:join(old,'work-area')}),/相互包含|区域/);facts.refusals.push('WORK child as root');const foreign=join(root,'synthetic-foreign');await mkdir(foreign);await writeFile(join(foreign,'synthetic.txt'),'SYNTHETIC-FOREIGN');await assert.rejects(()=>invoke(port,'WORK','choose_desktop_storage_location',{selectedPath:foreign}),/不是有效/);facts.refusals.push('foreign nonempty root');
 stage='schedule whole root from WORK';await invoke(port,'WORK','choose_desktop_storage_location',{selectedPath:target});const config=join(root,'config/storage-location.json');facts.configPending=JSON.parse(await readFile(config,'utf8'));assert.equal(canonical(facts.configPending.active),canonical(old));assert.equal(canonical(facts.configPending.pending),canonical(target));facts.pendingPathUI=await privacy(port);assert.ok(facts.pendingPathUI.some(p=>canonical(p)===canonical(target)));assert.deepEqual(await snapshot(port),facts.before);await normalClose(port);facts.oldAfterClose=await tree(old);
 stage='NORMAL migration and cold';port=await start(true);await ready(port);facts.configActive=JSON.parse(await readFile(config,'utf8'));assert.equal(canonical(facts.configActive.active),canonical(target));assert.equal(facts.configActive.pending,null);assert.deepEqual(await tree(old),facts.oldAfterClose);facts.after=await snapshot(port);for(const a of ['CHAT','WORK'])assert.deepEqual(facts.after[a].map(w=>w.exchange),facts.before[a].map(w=>w.exchange));
 for(const area of ['CHAT','WORK']){const x=facts.areas[area];assert.equal(hash(await readFile(join(area==='CHAT'?target:join(target,'work-area'),'assets',x.hash))),x.hash);assert.deepEqual(await invoke(port,area,'read_desktop_conversation_draft',{workspaceId:x.workspaceId,conversationId:x.conversationId}),x.draft);}
 facts.activePathUI=await privacy(port);assert.equal(facts.activePathUI.length,1);assert.equal(canonical(facts.activePathUI[0]),canonical(target));await normalClose(port);
 stage='second NORMAL cold';port=await start(true);await ready(port);assert.deepEqual(await snapshot(port),checkpoints.at(-1));assert.deepEqual(await tree(old),facts.oldAfterClose);facts.finalConfig=JSON.parse(await readFile(config,'utf8'));assert.equal(canonical(facts.finalConfig.active),canonical(target));assert.equal(facts.finalConfig.pending,null);await normalClose(port);facts.normalCloses=normalCloses;facts.sha256=receipt.sha256;facts.pass=true;await writeFile(join(evidence,'native-current.json'),JSON.stringify(facts,null,2)+'\n');console.log(JSON.stringify({ok:true,root,sha256:receipt.sha256,normalCloses:normalCloses.length,originalRootPreserved:true}));
}catch(error){await writeFile(join(evidence,`native-failed-${Date.now()}.json`),JSON.stringify({...facts,stage,error:String(error),stack:error.stack,sha256:receipt.sha256,normalCloses},null,2)+'\n');throw error;}finally{stop();}
