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
const states={};
try{
 let port=await start();result.root=root;
 await until(()=>evaluate(port,'Boolean(document.querySelector("#chat-composer")&&globalThis.__nanfengDesktopWorkState?.runtimeInfo?.dataAreasReady)'),'initial ready');
 const runtime=await invoke(port,'CHAT','read_desktop_runtime_info');result.runtime=runtime;
 check('diagnostic suppresses automatic work',runtime.startupMode==='UI_SCHEMA_DIAGNOSTIC_ACCEPTANCE'&&runtime.automaticWorkSuppressed===true);
 await installProjectionAccess(port);result.workspaceSelection='diagnostic complete Rust read into existing current projection plus original private refresh; not visible workspace switching';
 result.environment=JSON.parse(ps('$os=Get-CimInstance Win32_OperatingSystem;$cpu=Get-CimInstance Win32_Processor; @{os=$os.Caption;build=$os.BuildNumber;totalMemory=$os.TotalVisibleMemorySize*1024;cpu=@($cpu|Select-Object Name,NumberOfLogicalProcessors);node='+quote(process.version)+'}|ConvertTo-Json -Depth 4 -Compress'));
 for(const area of ['CHAT','WORK']){
  const id=(await invoke(port,area,'ensure_desktop_local_workspace')).summary.id;
  states[area]={baselineId:id,baseline:hash(await invoke(port,area,'read_desktop_workspace',{workspaceId:id})),libraries:[]};
 }
 await resource('empty ready');
 const {packageExchange,semanticHash,preflightPackage}=await import('../../protocol/scripts/exchange-lib.mjs');
 const original=JSON.parse(await readFile(join(desktopRoot,'../protocol/fixtures/nfai.exchange.v1.golden.json'),'utf8'));
 let fixture300;
 const longText=Array.from({length:100},(_,i)=>'## 合成长回答 '+i+'\n\n'+'本地稳定性验证段落。'.repeat(65)+'\n').join('\n')+'\n```javascript\n'+Array.from({length:100},(_,i)=>'const item_'+i+' = "'+'wide_'.repeat(80)+'";').join('\n')+'\n```';
 for(const count of [100,300,600]){
  const ex=structuredClone(original);ex.export.id='w20-export-'+count;
  ex.conversations=Array.from({length:count},(_,i)=>{const c=structuredClone(original.conversations[0]);c.id='w20-c-'+count+'-'+i;c.title='W20 合成会话 '+count+' / '+i;c.currentLeafId=c.id+'-a';c.messages[0].id=c.id+'-u';c.messages[0].blocks=[{kind:'TEXT',ordinal:0,text:'W20 本地合成问题 '+i}];c.messages[1].id=c.id+'-a';c.messages[1].parentId=c.id+'-u';c.messages[1].blocks=[{kind:'TEXT',ordinal:0,text:i===0?longText:'合成完整回答 '+i+' '+ '稳定性内容。'.repeat(60)}];return c;});
  ex.export.semanticHash=semanticHash(ex);const bytes=packageExchange(ex,new Map());preflightPackage(bytes);
  if(count===300)fixture300=structuredClone(ex);
  const path=join(root,'w20-library-'+count+'.nfai-exchange');await writeFile(path,bytes);
  result.fixtures.push({count,path,bytes:bytes.length,sha256:createHash('sha256').update(bytes).digest('hex'),longCharacters:longText.length});
  for(const area of ['CHAT','WORK']){
   const imported=await timed(area+' import '+count,async()=>{const p=await invoke(port,area,'stage_preflight_selected_exchange',{selectedPath:path});return invoke(port,area,'import_staged_exchange_as_new_workspace',{args:{stagingId:p.stagingId,workspaceTitle:'W20 '+area+' synthetic '+count}});});
   check(area+' imported count '+count,imported.exchange.conversations.length===count);
   const item={id:imported.summary.id,count,exchangeHash:hash(imported.exchange),first:imported.exchange.conversations[0].id,last:imported.exchange.conversations.at(-1).id,draftIds:imported.exchange.conversations.slice(1,7).map(c=>c.id)};
   states[area].libraries.push(item);
   const read=await timed(area+' read '+count,()=>invoke(port,area,'read_desktop_workspace',{workspaceId:item.id}));
   check(area+' complete owner read '+count,hash(read.exchange)===item.exchangeHash);
   await selectLibraryProjection(port,area,item.id);
   await timed(area+' UI long answer '+count,()=>openConversation(port,item.first));
   const ui=await evaluate(port,'(()=>{const s=document.querySelector(".chat-scroll[data-conversation-id]");return {characters:s.innerText.length,code:!!s.querySelector(".chat-markdown-code"),height:s.scrollHeight,client:s.clientHeight}})()');
   check(area+' long answer visible '+count,ui.characters>longText.length*0.75&&ui.code&&ui.height>ui.client);
   result.ui.push({area,count,...ui});
   await timed(area+' UI short answer '+count,()=>openConversation(port,item.last));
  }
  await delay(1000);await resource('both areas imported '+count);
 }
 for(let batch=0;batch<3;batch++){
  for(const area of ['CHAT','WORK']){
   const item=states[area].libraries.at(-1);await selectLibraryProjection(port,area,item.id);
   for(let i=0;i<5;i++){await timed(area+' switch long batch '+batch,()=>openConversation(port,item.first));await timed(area+' switch short batch '+batch,()=>openConversation(port,item.last));}
   const reads=await timed(area+' concurrent 6 owner reads batch '+batch,()=>Promise.all(Array.from({length:6},()=>invoke(port,area,'read_desktop_workspace',{workspaceId:item.id}))));
   check(area+' concurrent read conservation '+batch,reads.every(r=>hash(r.exchange)===item.exchangeHash));
  }
  await delay(1000);await resource('interaction batch '+batch);
 }
 // Real local imports and independent draft writes overlap across both SQLite
 // owners. They are not Provider tasks and do not use a test chat endpoint.
 const imports=[];
 for(const area of ['CHAT','WORK'])for(let i=0;i<2;i++){
  const ex=structuredClone(fixture300);ex.export.id='w20-concurrent-'+area.toLowerCase()+'-'+i;ex.export.semanticHash=semanticHash(ex);
  const bytes=packageExchange(ex,new Map());preflightPackage(bytes);const path=join(root,'w20-concurrent-'+area+'-'+i+'.nfai-exchange');await writeFile(path,bytes);
  result.fixtures.push({count:300,path,bytes:bytes.length,sha256:createHash('sha256').update(bytes).digest('hex'),independentExportId:ex.export.id});
  imports.push({area,index:i,staging:await invoke(port,area,'stage_preflight_selected_exchange',{selectedPath:path})});
 }
 const outcomes=await timed('concurrent 4 local imports',()=>Promise.allSettled(imports.map(job=>invoke(port,job.area,'import_staged_exchange_as_new_workspace',{args:{stagingId:job.staging.stagingId,workspaceTitle:'W20 concurrent '+job.area+' '+job.index}}))));
 result.concurrentImportOutcomes=outcomes.map(o=>({status:o.status,error:o.reason?String(o.reason):undefined}));
 check('all concurrent unique imports acknowledged',outcomes.every(o=>o.status==='fulfilled'));
 const imported=outcomes.map(o=>o.value);
 check('four concurrent imports keep independent identities',new Set(imported.map(w=>w.summary.id)).size===4);
 result.concurrentImports=imported.map((w,i)=>({area:imports[i].area,id:w.summary.id,count:300,exchangeHash:hash(w.exchange)}));
 const draftJobs=[];
 for(const area of ['CHAT','WORK']){
  const item=states[area].libraries.at(-1);
  item.drafts={};
  // Existing conversation choices belong to the preference owner, not drafts.
  for(const [i,conversationId] of item.draftIds.entries())draftJobs.push({area,workspaceId:item.id,conversationId,text:'W20 '+area+' 草稿 '+i,webSearchOverride:null});
 }
 const draftOutcomes=await timed('concurrent 12 distinct draft writes',()=>Promise.allSettled(draftJobs.map(job=>invoke(port,job.area,'save_desktop_conversation_draft',{args:{workspaceId:job.workspaceId,conversationId:job.conversationId,text:job.text,attachmentIds:[],modelId:null,toneOverride:null,webSearchOverride:job.webSearchOverride}}))));
 result.concurrentDraftOutcomes=draftOutcomes.map(o=>({status:o.status,error:o.reason?String(o.reason):undefined}));
 check('all concurrent drafts acknowledged',draftOutcomes.every(o=>o.status==='fulfilled'));
 for(const job of draftJobs){
  const d=await invoke(port,job.area,'read_desktop_conversation_draft',{workspaceId:job.workspaceId,conversationId:job.conversationId});
  check(job.area+' concurrent full draft '+job.conversationId,d.text===job.text&&d.webSearchOverride===null&&d.attachments.length===0&&d.modelId===null&&d.toneOverride===null);
  states[job.area].libraries.at(-1).drafts[job.conversationId]=d;
 }
 for(const w of result.concurrentImports)check(w.area+' concurrent import full read '+w.id,hash((await invoke(port,w.area,'read_desktop_workspace',{workspaceId:w.id})).exchange)===w.exchangeHash);
 await resource('concurrent imports and writes acknowledged');
 for(const area of ['CHAT','WORK']){
  check(area+' empty original workspace conserved',hash(await invoke(port,area,'read_desktop_workspace',{workspaceId:states[area].baselineId}))===states[area].baseline);
  for(const item of states[area].libraries)check(area+' precrash full exchange '+item.count,hash((await invoke(port,area,'read_desktop_workspace',{workspaceId:item.id})).exchange)===item.exchangeHash);
 }
 result.abruptExit={pid:Number(launch.pid),method:'exact candidate PID forced termination after acknowledged reads; no in-flight write claim'};
 const profile=launch.webviewProfile;stop();
 await until(()=>ps(`@(Get-CimInstance Win32_Process -Filter "Name='msedgewebview2.exe'" | Where-Object { $_.CommandLine -and $_.CommandLine.Contains(${quote(profile)}) }).Count`)==='0','abrupt profile exit');
 port=await start(true);
 await until(()=>evaluate(port,'Boolean(document.querySelector("#chat-composer")&&globalThis.__nanfengDesktopWorkState?.runtimeInfo?.dataAreasReady)'),'cold ready');
 await installProjectionAccess(port);
 for(const w of result.concurrentImports)check(w.area+' cold concurrent import '+w.id,hash((await invoke(port,w.area,'read_desktop_workspace',{workspaceId:w.id})).exchange)===w.exchangeHash);
 for(const job of draftJobs)check(job.area+' cold complete concurrent draft '+job.conversationId,hash(await invoke(port,job.area,'read_desktop_conversation_draft',{workspaceId:job.workspaceId,conversationId:job.conversationId}))===hash(states[job.area].libraries.at(-1).drafts[job.conversationId]));
 for(const area of ['CHAT','WORK']){
  check(area+' cold original full workspace',hash(await invoke(port,area,'read_desktop_workspace',{workspaceId:states[area].baselineId}))===states[area].baseline);
  for(const item of states[area].libraries)check(area+' cold full exchange '+item.count,hash((await timed(area+' cold read '+item.count,()=>invoke(port,area,'read_desktop_workspace',{workspaceId:item.id}))).exchange)===item.exchangeHash);
  await selectLibraryProjection(port,area,states[area].libraries.at(-1).id);await openConversation(port,states[area].libraries.at(-1).first);
  check(area+' cold long answer operable',await evaluate(port,'Boolean(document.querySelector(".chat-markdown-code"))'));
 }
 await resource('cold recovered');await normalClose(port);result.pass=true;
}catch(error){result.error=String(error.stack||error);process.exitCode=1;}
finally{if(launch){result.forcedCleanup=true;stop();}result.states=states;result.pids=pids;if(root)await writeFile(join(root,'w20-stability-evidence.json'),JSON.stringify(result,null,2));}
console.log(JSON.stringify({root,pass:result.pass,checks:result.checks.length,error:result.error}));


