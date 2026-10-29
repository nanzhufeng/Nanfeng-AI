import assert from 'node:assert/strict';
import { createHash, randomUUID } from 'node:crypto';
import { execFileSync, spawn } from 'node:child_process';
import { mkdir, readFile, writeFile } from 'node:fs/promises';
import { dirname, join, resolve, sep } from 'node:path';
import { fileURLToPath } from 'node:url';

if (process.platform !== 'win32') throw new Error('仅在 Windows 隔离候选上验收。');
const desktopRoot = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const rootParent = resolve(process.argv[2] || 'D:\\CodexTemp');
const taskArea='CHAT';
const otherArea='WORK';
assert.equal(process.argv.length,3,'只接受独立隔离根参数');
const receipt = JSON.parse(await readFile(join(desktopRoot, 'output/windows-acceptance/current.json'), 'utf8'));
assert.equal(receipt.identifier, 'com.nanzhufeng.ai.desktop.windowsacceptance');
const source = resolve(desktopRoot, 'output/windows-acceptance', receipt.sha256, 'NanfengAI-Windows-Acceptance.exe');
assert.equal(resolve(receipt.executable), source);
assert.equal(createHash('sha256').update(await readFile(source)).digest('hex').toUpperCase(), receipt.sha256);
const runningExecutable = resolve(desktopRoot, 'output/windows-acceptance-binaries', receipt.sha256, 'nanfeng-ai-desktop-spike.exe');
const ps = script => execFileSync('pwsh', ['-NoProfile', '-Command', script], { encoding: 'utf8', timeout: 60000 }).trim();
const quote = value => `'${String(value).replaceAll("'", "''")}'`;
const reservePort = async () => {
  const server = (await import('node:http')).createServer();
  await new Promise(done => server.listen(0, '127.0.0.1', done));
  const port = server.address().port;
  await new Promise(done => server.close(done));
  activePort=port;return port;
};
let activePort=0;
let launch = null;
let root = '';
const pids = [];
const startProcess = args => new Promise((done, fail) => {
  const child = spawn('pwsh', ['-NoProfile', '-File', join(desktopRoot, 'scripts/start-windows-acceptance.ps1'), ...args], {
    stdio: ['ignore', 'pipe', 'pipe'],
  });
  let out = '';
  let err = '';
  const timer = setTimeout(() => { child.kill(); fail(new Error(`启动超时：${err}`)); }, 30000);
  child.stdout.on('data', chunk => {
    out += chunk.toString();
    const line = out.split(/\r?\n/).find(item => item.trim().startsWith('{') && item.includes('"pid"'));
    if (!line) return;
    clearTimeout(timer);
    try { done(JSON.parse(line)); } catch (error) { fail(error); }
    child.stdout.destroy();
    child.stderr.destroy();
    child.unref();
  });
  child.stderr.on('data', chunk => { err += chunk.toString(); });
  child.on('error', error => { clearTimeout(timer); fail(error); });
  child.on('exit', code => { if (!out.includes('"pid"')) { clearTimeout(timer); fail(new Error(`启动失败 ${code}：${err}`)); } });
});
async function start(reopen = false) {
  const port = await reservePort();
  const args = ['-RootParent', rootParent, '-StartupMode', 'diagnostic', '-Visible', '-DebugPort', String(port)];
  if (reopen) args.push('-Root', root);
  launch = await startProcess(args);result.launches??=[];result.launches.push(launch);
  assert.equal(launch.sha256, receipt.sha256);
  assert.equal(resolve(launch.executable), runningExecutable);
  root = resolve(launch.root);
  assert.ok(root.startsWith(rootParent + sep));
  const marker = JSON.parse(await readFile(join(root, '.nanfeng-windows-acceptance.json'), 'utf8'));
  assert.equal(marker.identifier, receipt.identifier);
  activePort=port;return port;
}
function stop() {
  if (!launch) return;
  const pid = Number(launch.pid);
  ps(`$p=Get-CimInstance Win32_Process -Filter "ProcessId=${pid}"; if ($p) { if ([IO.Path]::GetFullPath([string]$p.ExecutablePath) -ne ${quote(runningExecutable)} -or (Get-FileHash -LiteralPath ${quote(runningExecutable)} -Algorithm SHA256).Hash -ne ${quote(receipt.sha256)}) { throw 'PID identity mismatch' }; Stop-Process -Id ${pid} -Force -ErrorAction Stop }`);
  pids.push(pid);
  launch = null;
}
async function cdp(port, method, params) {
  const tabs = await fetch(`http://127.0.0.1:${port}/json/list`).then(response => response.json());
  const tab = tabs.find(item => item.type === 'page' && item.url === 'http://tauri.localhost/');
  assert.ok(tab, 'WebView2 页面未就绪');
  const socket = new WebSocket(tab.webSocketDebuggerUrl);
  try {
    await new Promise((done, fail) => {
      socket.addEventListener('open', done, { once: true });
      socket.addEventListener('error', fail, { once: true });
    });
    return await new Promise((done, fail) => {
      const timer = setTimeout(() => fail(new Error('CDP evaluation timed out')), 30000);
      socket.addEventListener('message', event => {
        const message = JSON.parse(event.data);
        if (message.id !== 1) return;
        clearTimeout(timer);
        if (message.error || message.result?.exceptionDetails) fail(new Error(JSON.stringify(message.result?.exceptionDetails || message.error)));
        else done(message.result);
      });
      socket.addEventListener('close',()=>{clearTimeout(timer);fail(new Error('CDP transport closed'));},{once:true});
      socket.send(JSON.stringify({id:1,method,params}));
    });
  } finally { socket.close(); }
}
const evaluate = async (port,expression) => (await cdp(port,'Runtime.evaluate',{expression,awaitPromise:true,returnByValue:true}))?.result?.value;
const undoShortcut = async (port,redo=false) => {
  const params={key:redo?'Z':'z',code:'KeyZ',windowsVirtualKeyCode:90,modifiers:redo?10:2};
  await cdp(port,'Input.dispatchKeyEvent',{type:'rawKeyDown',...params});
  await cdp(port,'Input.dispatchKeyEvent',{type:'keyUp',...params});
};

async function invoke(port, area, command, args = {}) {
  const value = await evaluate(port, `window.__TAURI__.core.invoke(${JSON.stringify(command)},${JSON.stringify(args)},${JSON.stringify({ headers: { 'x-nanfeng-data-area': area } })}).catch(error=>({__error:String(error)}))`);
  if (value?.__error) throw new Error(`${command}: ${value.__error}`);
  return value;
}
async function until(read, label) {
  const deadline = Date.now() + 30000;
  let lastError = null;
  while (Date.now() < deadline) {
    try { const value = await read(); if (value) return value; } catch (error) { lastError = String(error); }
    await new Promise(done => setTimeout(done, 200));
  }
  throw new Error(`${label} 未在原生界面出现${lastError ? `；最后读取错误：${lastError}` : ''}`);
}
const click = (port, selector) => evaluate(port, `(()=>{const node=document.querySelector(${JSON.stringify(selector)});if(!node)return false;node.click();return true})()`);

let result = {checks:[], candidateSha256:receipt.sha256, startupMode:'diagnostic', realService:false, fixtureLayer:'synthetic exchange imports; no provider attempts', productionStream:false, concurrentModelTasks:false, performanceBudget:null, measurements:[],resources:[],fixtures:[],ui:[]};
const check=(name,value)=>{assert.ok(value,name);result.checks.push({name,pass:true});};
async function normalClose(port){
 const pid=Number(launch.pid);
 const info=JSON.parse(ps(`$p=Get-Process -Id ${pid} -ErrorAction Stop; @{pid=$p.Id;responding=$p.Responding;title=$p.MainWindowTitle;accepted=$p.CloseMainWindow()}|ConvertTo-Json -Compress`));
 check('native window responding',info.responding);check('real native title present',Boolean(info.title));check('CloseMainWindow accepted',info.accepted);
 try { await until(()=>ps(`@(Get-CimInstance Win32_Process -Filter "ProcessId=${pid}").Count`)==='0','正常Close进程退出'); }
 catch(error) { result.closeFailure={info,process:ps(`Get-CimInstance Win32_Process -Filter "ProcessId=${pid}" | Select-Object ProcessId,ExecutablePath | ConvertTo-Json -Compress`),ui:await evaluate(port,'document.body.innerText.slice(-2200)')}; throw error; }
 await until(()=>ps(`@(Get-CimInstance Win32_Process -Filter "Name='msedgewebview2.exe'" | Where-Object { $_.CommandLine -and $_.CommandLine.Contains(${quote(launch.webviewProfile)}) }).Count`)==='0','本根WebView正常退出');result.checks.push({name:'normal Close exited and profile browser exited',pass:true,detail:info});pids.push(pid);launch=null;
}

const hash=value=>createHash('sha256').update(JSON.stringify(value)).digest('hex');
const delay=ms=>new Promise(ok=>setTimeout(ok,ms));
const timed=async(label,fn)=>{const t=performance.now();const value=await fn();result.measurements.push({label,milliseconds:performance.now()-t});return value;};
async function resource(label){
 const profile=launch.webviewProfile,pid=Number(launch.pid);
 const data=JSON.parse(ps(`$all=@(Get-CimInstance Win32_Process); $ids=@(${pid}); $ids+=@($all | Where-Object {$_.CommandLine -and $_.CommandLine.Contains(${quote(profile)})} | ForEach-Object {[int]$_.ProcessId}); for($i=0;$i -lt 4;$i++){$ids+=@($all | Where-Object {$ids -contains [int]$_.ParentProcessId} | ForEach-Object {[int]$_.ProcessId});$ids=@($ids|Sort-Object -Unique)}; $rows=@($ids|ForEach-Object {Get-Process -Id $_ -ErrorAction SilentlyContinue}|Select-Object Id,ProcessName,WorkingSet64,PrivateMemorySize64,HandleCount,CPU); @{rows=$rows;workingSet=($rows|Measure-Object WorkingSet64 -Sum).Sum;privateBytes=($rows|Measure-Object PrivateMemorySize64 -Sum).Sum;handles=($rows|Measure-Object HandleCount -Sum).Sum}|ConvertTo-Json -Depth 4 -Compress`));
 result.resources.push({label,utc:new Date().toISOString(),...data});
}
async function selectArea(port,area){
 await until(()=>click(port,'[data-action="show-'+(area==='CHAT'?'chat':'work')+'"]'),'area entry');
 await until(()=>evaluate(port,'globalThis.__nanfengDesktopWorkState?.dataArea==='+JSON.stringify(area)),'area selected');
}
async function installProjectionAccess(port){
 const tabs=await fetch(`http://127.0.0.1:${port}/json/list`).then(r=>r.json());
 const tab=tabs.find(t=>t.url==='http://tauri.localhost/');assert.ok(tab);
 const socket=new WebSocket(tab.webSocketDebuggerUrl);await new Promise((ok,no)=>{socket.addEventListener('open',ok,{once:true});socket.addEventListener('error',no,{once:true});});
 let sequence=0;const pending=new Map();
 const request=(method,params={})=>new Promise((ok,no)=>{const id=++sequence;const timer=setTimeout(()=>{pending.delete(id);no(new Error('projection CDP timeout '+method));},30000);pending.set(id,{ok,no,timer});socket.send(JSON.stringify({id,method,params}));});
 socket.addEventListener('message',async event=>{const m=JSON.parse(event.data);if(m.id&&pending.has(m.id)){const p=pending.get(m.id);pending.delete(m.id);clearTimeout(p.timer);m.error?p.no(new Error(JSON.stringify(m.error))):p.ok(m.result);}if(m.method==='Debugger.paused'){try{await request('Debugger.evaluateOnCallFrame',{callFrameId:m.params.callFrames[0].callFrameId,expression:'window.__w20Refresh=refresh;'});}finally{await request('Debugger.resume');}}});
 try{await request('Debugger.enable');const source=await readFile(join(desktopRoot,'src/app.mjs'),'utf8');const bp=await request('Debugger.setBreakpointByUrl',{urlRegex:'/app\\.mjs$',lineNumber:source.slice(0,source.indexOf('const tauriBridge =')).split('\n').length-1});await request('Page.reload');await until(()=>evaluate(port,'Boolean(window.__w20Refresh&&document.querySelector("#chat-composer")&&globalThis.__nanfengDesktopWorkState?.runtimeInfo?.dataAreasReady)'),'projection access ready');await request('Debugger.removeBreakpoint',{breakpointId:bp.breakpointId});}finally{socket.close();}
}
async function selectLibraryProjection(port,area,id){
 await selectArea(port,area);
 // Diagnostic selection only: use the complete Rust read as the current
 // projection and the existing private refresh. No injected UI or fake rows.
 await evaluate(port,`(async()=>{globalThis.__nanfengDesktopWorkState.current=await window.__TAURI__.core.invoke('read_desktop_workspace',{workspaceId:${JSON.stringify(id)}},{headers:{'x-nanfeng-data-area':${JSON.stringify(area)}}});await window.__w20Refresh();return true})()`);
}
async function openConversation(port,id){
 await until(()=>click(port,'[data-action="select-chat"][data-id="'+id+'"]'),'conversation entry');
 await until(()=>evaluate(port,'Boolean(document.querySelector(\'.chat-scroll[data-conversation-id="'+id+'"]\'))'),'conversation rendered');
}

const states={};let lock=null;let releasePath='';
const draftArgs=(w,text)=>({workspaceId:w,conversationId:Object.values(states).find(s=>s.id===w)?.conversationId??null,text,attachmentIds:[],modelId:null,toneOverride:null,webSearchOverride:null});
async function releaseLock(){if(!lock)return;await writeFile(releasePath,'release');const c=lock;await Promise.race([new Promise(ok=>c.exitCode!==null?ok():c.once('exit',ok)),delay(3000).then(()=>{throw new Error('lock release timeout')})]);check('lock released cleanly',c.exitCode===0);lock=null;}
try{
 let port=await start();result.root=root;
 await until(()=>evaluate(port,'Boolean(document.querySelector("#chat-composer")&&globalThis.__nanfengDesktopWorkState?.runtimeInfo?.dataAreasReady)'),'initial ready');
 result.runtime=await invoke(port,'CHAT','read_desktop_runtime_info');check('diagnostic suppresses automatic work',result.runtime.automaticWorkSuppressed===true);
 const {packageExchange,semanticHash,preflightPackage}=await import('../../protocol/scripts/exchange-lib.mjs');
 const ex=JSON.parse(await readFile(join(desktopRoot,'../protocol/fixtures/nfai.exchange.v1.golden.json'),'utf8'));
 ex.conversations=[ex.conversations[0]];for(const m of ex.conversations[0].messages)m.blocks=[{kind:'TEXT',ordinal:0,text:'W20 合成完整历史 '+m.id}];ex.export.semanticHash=semanticHash(ex);const bytes=packageExchange(ex,new Map());preflightPackage(bytes);
 const fixturePath=join(root,'blocked-write-golden.nfai-exchange');await writeFile(fixturePath,bytes);
 for(const area of ['CHAT','WORK']){
  const staged=await invoke(port,area,'stage_preflight_selected_exchange',{selectedPath:fixturePath});
  const w=await invoke(port,area,'import_staged_exchange_as_new_workspace',{args:{stagingId:staged.stagingId,workspaceTitle:'W20 blocked '+area}});const id=w.summary.id;
  states[area]={id,conversationId:w.exchange.conversations[0].id};
  await invoke(port,area,'save_desktop_conversation_draft',{args:draftArgs(id,'W20 '+area+' old complete draft 原始完整正文')});
  states[area].workspaceHash=hash(await invoke(port,area,'read_desktop_workspace',{workspaceId:id}));states[area].draft=await invoke(port,area,'read_desktop_conversation_draft',{workspaceId:id,conversationId:states[area].conversationId});
 }
 result.blockedWrites=[];
 for(const area of ['CHAT','WORK']){
  const ready=join(root,'w20-'+area+'-lock-ready.json');releasePath=join(root,'w20-'+area+'-lock-release');
  let lockError='';lock=spawn('python',[join(desktopRoot,'scripts/hold-synthetic-area-sqlite-lock.py'),root,area,ready,releasePath],{stdio:['ignore','pipe','pipe']});lock.stderr.on('data',b=>lockError+=b.toString());
  await until(async()=>{if(lock.exitCode!==null)throw new Error(lockError);try{return JSON.parse(await readFile(ready,'utf8'));}catch{return false;}},'area lock ready');
  const lockIdentity=JSON.parse(await readFile(ready,'utf8'));check(area+' lock actual area',lockIdentity.area===area);
  const args=draftArgs(states[area].id,'W20 '+area+' blocked replacement must not commit');
  const controlStart=performance.now();
  const control=await evaluate(port,`window.__TAURI__.core.invoke('save_desktop_conversation_draft',${JSON.stringify({args})},${JSON.stringify({headers:{'x-nanfeng-data-area':area}})}).then(()=>({status:'fulfilled'}),e=>({status:'rejected',error:String(e)}))`);
  result.lockControls??=[];result.lockControls.push({area,control,elapsedMs:performance.now()-controlStart});
  check(area+' real locked write rejected',control.status==='rejected');
  check(area+' locked write reports save failure',/草稿|SQLite|transaction/.test(control.error));
  let completed=false,outcome=null;
  const request=evaluate(port,`window.__TAURI__.core.invoke('save_desktop_conversation_draft',${JSON.stringify({args})},${JSON.stringify({headers:{'x-nanfeng-data-area':area}})}).then(()=>({status:'fulfilled'}),e=>({status:'rejected',error:String(e)}))`).then(v=>{completed=true;outcome=v;},e=>{completed=true;outcome={transportError:String(e)}});
  const dispatchStart=performance.now();await delay(200);
  const observed={elapsedMs:performance.now()-dispatchStart,completed,outcome};result.preKillObservations??=[];result.preKillObservations.push({area,...observed});
  check(area+' actual invoke no receipt before kill',!completed);check(area+' lock helper alive at kill',lock.exitCode===null);
  const pid=Number(launch.pid),profile=launch.webviewProfile;stop();
  result.blockedWrites.push({area,pid,lockIdentity,observed,method:'exact candidate PID force termination while real invoke has no receipt and external SQLite BEGIN IMMEDIATE lock held; not mid-commit/power-loss'});
  // The socket transport is destroyed by the precise process termination.
  await request;result.blockedWrites.at(-1).postKillTransportOutcome=outcome;
  await releaseLock();
  await until(()=>ps(`@(Get-CimInstance Win32_Process -Filter "Name='msedgewebview2.exe'" | Where-Object {$_.CommandLine -and $_.CommandLine.Contains(${quote(profile)})}).Count`)==='0','profile after crash');
  port=await start(true);await until(()=>evaluate(port,'Boolean(document.querySelector("#chat-composer")&&globalThis.__nanfengDesktopWorkState?.runtimeInfo?.dataAreasReady)'),'cold ready');
  for(const peer of ['CHAT','WORK']){
   check(area+' crash cold '+peer+' full draft',hash(await invoke(port,peer,'read_desktop_conversation_draft',{workspaceId:states[peer].id,conversationId:states[peer].conversationId}))===hash(states[peer].draft));
   check(area+' crash cold '+peer+' full workspace',hash(await invoke(port,peer,'read_desktop_workspace',{workspaceId:states[peer].id}))===states[peer].workspaceHash);
  }
  const text='W20 '+area+' recovered write 正常继续保存';await invoke(port,area,'save_desktop_conversation_draft',{args:draftArgs(states[area].id,text)});
  states[area].draft=await invoke(port,area,'read_desktop_conversation_draft',{workspaceId:states[area].id,conversationId:states[area].conversationId});check(area+' recovery save readback',states[area].draft.text===text);
 }
 await normalClose(port);port=await start(true);await until(()=>evaluate(port,'Boolean(document.querySelector("#chat-composer")&&globalThis.__nanfengDesktopWorkState?.runtimeInfo?.dataAreasReady)'),'final cold ready');
 for(const area of ['CHAT','WORK'])check(area+' recovered write final cold complete',hash(await invoke(port,area,'read_desktop_conversation_draft',{workspaceId:states[area].id,conversationId:states[area].conversationId}))===hash(states[area].draft));
 await normalClose(port);result.pass=true;
}catch(error){result.error=String(error.stack||error);process.exitCode=1;}
finally{if(launch){result.forcedCleanup=true;stop();}if(lock){try{await releaseLock()}catch(e){result.lockCleanupError=String(e);lock.kill();}}result.states=states;result.pids=pids;if(root)await writeFile(join(root,'w20-blocked-write-evidence.json'),JSON.stringify(result,null,2));}
console.log(JSON.stringify({root,pass:result.pass,checks:result.checks.length,error:result.error}));
