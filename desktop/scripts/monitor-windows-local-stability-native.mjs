import assert from 'node:assert/strict';
import { createHash, randomUUID } from 'node:crypto';
import { execFileSync, spawn } from 'node:child_process';
import { mkdir, readFile, writeFile, appendFile } from 'node:fs/promises';
import { dirname, join, resolve, sep } from 'node:path';
import { fileURLToPath } from 'node:url';

if (process.platform !== 'win32') throw new Error('仅在 Windows 隔离候选上验收。');
const desktopRoot = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const rootParent = resolve(process.argv[2] || 'D:\\CodexTemp');
const taskArea='CHAT';
const otherArea='WORK';
const mode=process.argv[3];
assert.ok(['smoke','24h'].includes(mode),'明确选择smoke或24h');
assert.equal(process.argv.length,4);
assert.ok(process.execArgv.includes('--max-old-space-size=2048'),'Node必须2048MiB');
const durationMs=mode==='24h'?86400000:60000;
const intervalMs=mode==='24h'?60000:10000;
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
  child.stdout.on('data', async chunk => {
    out += chunk.toString();
    const line = out.split(/\r?\n/).find(item => item.trim().startsWith('{') && item.includes('"pid"'));
    if (!line) return;
    clearTimeout(timer);
    // Hidden/redirected PowerShell may encode Chinese paths with a legacy code
    // page. Its ASCII receipt path locates the authoritative UTF-8 JSON file.
    try { const emitted=JSON.parse(line); done(JSON.parse(await readFile(emitted.launchReceipt,'utf8'))); } catch (error) { fail(error); }
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
  const args = ['-RootParent', rootParent, '-ExecutablePath', source, '-StartupMode', 'diagnostic', '-Visible', '-DebugPort', String(port)];
  if (reopen) args.push('-Root', root);
  launch = await startProcess(args);result.launches??=[];result.launches.push(launch);
  root = resolve(launch.root);
  assert.equal(launch.sha256, receipt.sha256);
  assert.equal(resolve(launch.executable), runningExecutable);
  assert.ok(root.startsWith(rootParent + sep));
  const marker = JSON.parse(await readFile(join(root, '.nanfeng-windows-acceptance.json'), 'utf8'));
  assert.equal(marker.identifier, receipt.identifier);
  activePort=port;return port;
}
function stop() {
  if (!launch) return;
  const pid = Number(launch.pid);
  assert.ok(Number.isSafeInteger(pid) && pid > 0, 'owned launch PID');
  assert.equal(launch.identifier, receipt.identifier, 'owned launch bundle');
  assert.match(launch.sha256, /^[A-F0-9]{64}$/, 'owned launch SHA');
  const ownedExecutable = resolve(desktopRoot, 'output/windows-acceptance-binaries', launch.sha256, 'nanfeng-ai-desktop-spike.exe');
  assert.equal(resolve(launch.executable), ownedExecutable, 'owned staged executable');
  assert.equal(resolve(launch.root), root, 'owned root');
  assert.ok(root.startsWith(rootParent + sep), 'owned root parent');
  assert.equal(resolve(launch.webviewProfile), join(root, 'webview2'), 'owned profile');
  ps(`$p=Get-CimInstance Win32_Process -Filter "ProcessId=${pid}"; if ($p) { if ([IO.Path]::GetFullPath([string]$p.ExecutablePath) -ne ${quote(ownedExecutable)} -or (Get-FileHash -LiteralPath ${quote(ownedExecutable)} -Algorithm SHA256).Hash -ne ${quote(launch.sha256)}) { throw 'PID identity mismatch' }; Stop-Process -Id ${pid} -Force -ErrorAction Stop }`);
  pids.push(pid);
  launch = null;
}
async function cdp(port, method, params) {
  const tabs = await fetch(`http://127.0.0.1:${port}/json/list`,{signal:AbortSignal.timeout(10000)}).then(response => response.json());
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
 await identity();
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
 const tabs=await fetch(`http://127.0.0.1:${port}/json/list`,{signal:AbortSignal.timeout(10000)}).then(r=>r.json());
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
const states={};
let startedWall=0,startedMono=0,cycles=0;
const statusPath=()=>join(root,'local-stability-status.json');
async function status(state,extra={}){
 const data={state,mode,accepted24h:false,root,candidateSha256:receipt.sha256,monitorPid:process.pid,
  launch,startedUtc:startedWall?new Date(startedWall).toISOString():null,updatedUtc:new Date().toISOString(),
  requiredMilliseconds:durationMs,wallElapsedMs:startedWall?Date.now()-startedWall:0,
  monotonicElapsedMs:startedMono?performance.now()-startedMono:0,cycles,performanceBudget:null,
  realService:false,productionStream:false,normalStartup:false,...extra};
 await writeFile(statusPath(),JSON.stringify(data,null,2));
}
async function identity(){
 const info=JSON.parse(ps(`$p=Get-CimInstance Win32_Process -Filter "ProcessId=${Number(launch.pid)}"; if(!$p){throw 'candidate exited'}; if([IO.Path]::GetFullPath([string]$p.ExecutablePath) -ne ${quote(runningExecutable)}){throw 'candidate path mismatch'}; if((Get-FileHash -LiteralPath ${quote(runningExecutable)} -Algorithm SHA256).Hash -ne ${quote(receipt.sha256)}){throw 'candidate hash mismatch'}; $wv=@(Get-CimInstance Win32_Process -Filter "Name='msedgewebview2.exe'" | Where-Object {$_.CommandLine -and $_.CommandLine.Contains(${quote(launch.webviewProfile)})}); if(!$wv.Count){throw 'profile missing'}; $native=Get-Process -Id ${Number(launch.pid)}; if(!$native.Responding){throw 'candidate unresponsive'}; $os=Get-CimInstance Win32_OperatingSystem; $events=@(Get-WinEvent -FilterHashtable @{LogName='System';Id=2004;StartTime=[DateTime]::Parse(${quote(new Date(startedWall||Date.now()).toISOString())})} -MaxEvents 64 -ErrorAction SilentlyContinue | Where-Object {$_.ProviderName -eq 'Microsoft-Windows-Resource-Exhaustion-Detector' -and $_.TimeCreated.ToUniversalTime() -ge [DateTimeOffset]::Parse(${quote(new Date(startedWall||Date.now()).toISOString())}).UtcDateTime}); @{pid=$p.ProcessId;path=$p.ExecutablePath;profile=${quote(launch.webviewProfile)};profilePids=@($wv.ProcessId);responding=$native.Responding;title=$native.MainWindowTitle;freePhysicalBytes=$os.FreePhysicalMemory*1024;freeVirtualBytes=$os.FreeVirtualMemory*1024;resourceExhaustionEvents=$events.Count}|ConvertTo-Json -Depth 4 -Compress`));
 assert.equal(info.resourceExhaustionEvents,0,'系统内存告警：立即停止');
 return info;
}
async function fullVerify(port,label){
 for(const area of ['CHAT','WORK']){
  assert.equal(hash(await invoke(port,area,'read_desktop_workspace',{workspaceId:states[area].baselineId})),states[area].baseline,label+' baseline '+area);
  for(const item of states[area].libraries){
   assert.equal(hash((await invoke(port,area,'read_desktop_workspace',{workspaceId:item.id})).exchange),item.exchangeHash,label+' exchange '+area+' '+item.count);
   assert.equal(hash(await invoke(port,area,'read_desktop_conversation_draft',{workspaceId:item.id,conversationId:item.draftId})),hash(item.draft),label+' draft '+area+' '+item.count);
  }
 }
}
function sqlite(includeClosedBrowser=false){
 const evidence=JSON.parse(execFileSync('python',[join(desktopRoot,'scripts/check-stability-synthetic-sqlite.py'),root,...(includeClosedBrowser?['--closed-browser']:[])],{encoding:'utf8',timeout:60000}));
 assert.ok(evidence.allOk,'SQLite完整性');
 for(const db of evidence.databases)for(const [table,count] of Object.entries(db.counts))if(table.toLowerCase().includes('attempt'))assert.equal(count,0,'unexpected Provider attempt');
 return evidence;
}
function conservedCounts(evidence){
 for(const initial of result.initialSqlite.databases){
  const current=evidence.databases.find(db=>db.path===initial.path);
  assert.ok(current,'database retained '+initial.path);
  assert.deepEqual(current.counts,initial.counts,'attachment/task/Attempt counts '+initial.path);
 }
}
try{
 let port=await start();result.root=root;
 await until(()=>evaluate(port,'Boolean(document.querySelector("#chat-composer")&&globalThis.__nanfengDesktopWorkState?.runtimeInfo?.dataAreasReady)'),'initial ready');
 await status('PREPARING');
 const runtime=await invoke(port,'CHAT','read_desktop_runtime_info');result.runtime=runtime;
 check('diagnostic suppresses automatic work',runtime.startupMode==='UI_SCHEMA_DIAGNOSTIC_ACCEPTANCE'&&runtime.automaticWorkSuppressed===true);
 await installProjectionAccess(port);
 const {packageExchange,semanticHash,preflightPackage}=await import('../../protocol/scripts/exchange-lib.mjs');
 const original=JSON.parse(await readFile(join(desktopRoot,'../protocol/fixtures/nfai.exchange.v1.golden.json'),'utf8'));
 const longText=Array.from({length:100},(_,i)=>'## 合成长回答 '+i+'\n\n'+'本地稳定性验证段落。'.repeat(65)+'\n').join('\n')+'\n```javascript\n'+Array.from({length:100},(_,i)=>'const item_'+i+' = "'+'wide_'.repeat(80)+'";').join('\n')+'\n```';
 for(const area of ['CHAT','WORK']){
  const id=(await invoke(port,area,'ensure_desktop_local_workspace')).summary.id;
  states[area]={baselineId:id,baseline:hash(await invoke(port,area,'read_desktop_workspace',{workspaceId:id})),libraries:[]};
 }
 for(const count of [100,300,600]){
  const ex=structuredClone(original);ex.export.id='w20-24h-export-'+count;
  ex.conversations=Array.from({length:count},(_,i)=>{const c=structuredClone(original.conversations[0]);c.id='w20-24h-c-'+count+'-'+i;c.title='W20 本地24h '+count+' / '+i;c.currentLeafId=c.id+'-a';c.messages[0].id=c.id+'-u';c.messages[0].blocks=[{kind:'TEXT',ordinal:0,text:'合成问题 '+i}];c.messages[1].id=c.id+'-a';c.messages[1].parentId=c.id+'-u';c.messages[1].blocks=[{kind:'TEXT',ordinal:0,text:i===0?longText:'完整合成回答 '+i+'稳定性内容。'.repeat(60)}];return c;});
  ex.export.semanticHash=semanticHash(ex);const bytes=packageExchange(ex,new Map());preflightPackage(bytes);
  const path=join(root,'local-stability-'+count+'.nfai-exchange');await writeFile(path,bytes);
  result.fixtures.push({count,path,bytes:bytes.length,sha256:createHash('sha256').update(bytes).digest('hex')});
  for(const area of ['CHAT','WORK']){
   const staged=await invoke(port,area,'stage_preflight_selected_exchange',{selectedPath:path});
   const imported=await invoke(port,area,'import_staged_exchange_as_new_workspace',{args:{stagingId:staged.stagingId,workspaceTitle:'W20 24h '+area+' '+count}});
   assert.equal(imported.exchange.conversations.length,count);
   const item={id:imported.summary.id,count,exchangeHash:hash(imported.exchange),first:imported.exchange.conversations[0].id,last:imported.exchange.conversations.at(-1).id,draftId:imported.exchange.conversations[1].id};
   await invoke(port,area,'save_desktop_conversation_draft',{args:{workspaceId:item.id,conversationId:item.draftId,text:'初始合成草稿 '+area+' '+count,attachmentIds:[],modelId:null,toneOverride:null,webSearchOverride:null}});
   item.draft=await invoke(port,area,'read_desktop_conversation_draft',{workspaceId:item.id,conversationId:item.draftId});states[area].libraries.push(item);
  }
 }
 await fullVerify(port,'initial');
 result.initialSqlite=sqlite();result.initialStates=structuredClone(states);
 await writeFile(join(root,'local-stability-baseline.json'),JSON.stringify({states,fixtures:result.fixtures,runtime,sqlite:result.initialSqlite},null,2));
 startedWall=Date.now();startedMono=performance.now();
 result.startIdentity=await identity();
 await evaluate(port,'(()=>{window.__localStabilityErrors=[];for(const type of ["error","unhandledrejection"])window.addEventListener(type,event=>{if(window.__localStabilityErrors.length<100)window.__localStabilityErrors.push({type,message:String(event.message||event.reason||"unknown")});});return true})()');
 await status('RUNNING');
 while(true){
  const begin=performance.now();const sampleIdentity=await identity();result.measurements=[];result.resources=[];
  for(const area of ['CHAT','WORK'])for(const item of states[area].libraries){
   await timed(area+' read '+item.count,async()=>assert.equal(hash((await invoke(port,area,'read_desktop_workspace',{workspaceId:item.id})).exchange),item.exchangeHash));
   await timed(area+' switch '+item.count,async()=>{await selectLibraryProjection(port,area,item.id);await openConversation(port,item.first);assert.ok(await evaluate(port,'Boolean(document.querySelector(".chat-markdown-code"))'));await openConversation(port,item.last);});
   await timed(area+' save/read '+item.count,async()=>{
    const text='W20 '+mode+' '+area+' '+item.count+' 周期 '+cycles;
    await invoke(port,area,'save_desktop_conversation_draft',{args:{workspaceId:item.id,conversationId:item.draftId,text,attachmentIds:[],modelId:null,toneOverride:null,webSearchOverride:null}});
    item.draft=await invoke(port,area,'read_desktop_conversation_draft',{workspaceId:item.id,conversationId:item.draftId});
    assert.equal(item.draft.text,text);assert.equal(item.draft.attachments.length,0);assert.equal(item.draft.modelId,null);assert.equal(item.draft.toneOverride,null);assert.equal(item.draft.webSearchOverride,null);
   });
  }
  await fullVerify(port,'cycle');
  const pageErrors=await evaluate(port,'window.__localStabilityErrors');assert.ok(Array.isArray(pageErrors));assert.equal(pageErrors.length,0,'page error/unhandled rejection');
  await resource('cycle '+cycles);cycles++;
  const sample={cycle:cycles,utc:new Date().toISOString(),wallElapsedMs:Date.now()-startedWall,monotonicElapsedMs:performance.now()-startedMono,identity:sampleIdentity,measurements:result.measurements,resources:result.resources,nodeMemory:process.memoryUsage()};
  await appendFile(join(root,'local-stability-samples.jsonl'),JSON.stringify(sample)+'\n');
  await writeFile(join(root,'local-stability-latest-states.json'),JSON.stringify(states,null,2));
  await status('RUNNING',{lastCycleUtc:sample.utc,lastCycleMilliseconds:performance.now()-begin});
  if(Date.now()-startedWall>=durationMs&&performance.now()-startedMono>=durationMs)break;
  await delay(Math.max(1,intervalMs-(performance.now()-begin)));
 }
 const wallElapsedMs=Date.now()-startedWall,monotonicElapsedMs=performance.now()-startedMono;
 await status('FINALIZING');await fullVerify(port,'preclose');result.precloseSqlite=sqlite();conservedCounts(result.precloseSqlite);await normalClose(port);
 port=await start(true);await until(()=>evaluate(port,'Boolean(document.querySelector("#chat-composer")&&globalThis.__nanfengDesktopWorkState?.runtimeInfo?.dataAreasReady)'),'cold ready');
 await installProjectionAccess(port);await fullVerify(port,'cold');result.finalSqlite=sqlite();conservedCounts(result.finalSqlite);
 for(const area of ['CHAT','WORK']){await selectLibraryProjection(port,area,states[area].libraries.at(-1).id);await openConversation(port,states[area].libraries.at(-1).first);assert.ok(await evaluate(port,'Boolean(document.querySelector(".chat-markdown-code"))'));}
 await normalClose(port);result.closedSqlite=sqlite(true);conservedCounts(result.closedSqlite);result.pass=true;result.wallElapsedMs=wallElapsedMs;result.monotonicElapsedMs=monotonicElapsedMs;
 await status(mode==='24h'?'LOCAL_24H_COMPLETED':'SMOKE_COMPLETED',{accepted24h:mode==='24h'&&wallElapsedMs>=86400000&&monotonicElapsedMs>=86400000,wallElapsedMs,monotonicElapsedMs,normalCloseAndColdVerified:true});
}catch(error){result.error=String(error.stack||error);process.exitCode=1;}
finally{
 if(launch){result.forcedCleanup=true;try{stop();}catch(error){result.cleanupError=String(error);}}
 result.states=states;result.pids=pids;result.mode=mode;result.cycles=cycles;result.startedUtc=startedWall?new Date(startedWall).toISOString():null;
 if(root){await writeFile(join(root,'local-stability-evidence.json'),JSON.stringify(result,null,2));if(!result.pass)await status('FAILED',{error:result.error,cleanupError:result.cleanupError,forcedCleanup:result.forcedCleanup});}
}
console.log(JSON.stringify({root,mode,pass:result.pass,cycles,error:result.error}));
