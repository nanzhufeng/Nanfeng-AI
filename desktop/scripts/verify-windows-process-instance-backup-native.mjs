import assert from 'node:assert/strict';
import { createHash, randomUUID } from 'node:crypto';
import { execFileSync, spawn } from 'node:child_process';
import { mkdir, readFile, writeFile, appendFile, stat } from 'node:fs/promises';
import { dirname, join, resolve, sep } from 'node:path';
import { fileURLToPath } from 'node:url';

if (process.platform !== 'win32') throw new Error('仅在 Windows 隔离候选上验收。');
const desktopRoot = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const rootParent = resolve(process.argv[2] || 'D:\\CodexTemp');
const taskArea='CHAT';
const otherArea='WORK';
const mode='process-instance-backup';
const normalFinalization=process.argv[3]==='--normal-finalization';
assert.ok(process.argv.length===3||(process.argv.length===4&&normalFinalization));
assert.ok(process.execArgv.includes('--max-old-space-size=2048'));
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
async function start(reopen = false, startupMode = 'diagnostic') {
  const port = await reservePort();
  const args = ['-RootParent', rootParent, '-StartupMode', startupMode, '-Visible', '-DebugPort', String(port)];
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
  ps(`$p=Get-CimInstance Win32_Process -Filter "ProcessId=${pid}"; if ($p) { if ([IO.Path]::GetFullPath([string]$p.ExecutablePath) -ne ${quote(runningExecutable)} -or (Get-FileHash -LiteralPath ${quote(runningExecutable)} -Algorithm SHA256).Hash -ne ${quote(receipt.sha256)}) { throw 'PID identity mismatch' }; Stop-Process -Id ${pid} -Force -ErrorAction Stop }`);
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

let result={checks:[],candidateSha256:receipt.sha256,startupMode:'diagnostic',realService:false,performanceBudget:null,measurements:[],areas:{},fullNormalRecoveryAccepted:false};
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
const areas=['CHAT','WORK'];
const sha=bytes=>createHash('sha256').update(bytes).digest('hex');
const areaRoot=area=>area==='CHAT'?root:join(root,'work-area');
function assetBytes(area,count,index){
 const chunks=[Buffer.from(`SYNTHETIC-W15-REFERENCED-${area}-${count}-${index}\n`)];
 for(let n=0;n<384;n++)chunks.push(Buffer.from(createHash('sha256').update(`${area}/${count}/${index}/${n}`).digest('hex')));
 return Buffer.concat(chunks);
}
async function assets(area){
 const records=[];
 for(const item of result.areas[area].libraries)for(const asset of item.assets){
  const bytes=await readFile(join(areaRoot(area),'assets',asset.sha256));
  assert.equal(bytes.length,asset.byteCount);assert.equal(sha(bytes),asset.sha256);
  records.push({id:asset.id,bytes:bytes.length,sha256:asset.sha256});
 }
 return records;
}

function sqlite(closed=false){
 const data=JSON.parse(execFileSync('python',[join(desktopRoot,'scripts/check-stability-synthetic-sqlite.py'),root,...(closed?['--closed-browser']:[])],{encoding:'utf8',timeout:60000}));
 assert.ok(data.allOk);for(const db of data.databases)for(const [table,count]of Object.entries(db.counts))if(table.toLowerCase().includes('attempt'))assert.equal(count,0);
 return data;
}
async function verifyArea(port,area){
 for(const item of result.areas[area].libraries){
  const read=await invoke(port,area,'read_desktop_workspace',{workspaceId:item.id});
  assert.equal(hash(read.exchange),item.hash);
  const draft=await invoke(port,area,'read_desktop_conversation_draft',{workspaceId:item.id,conversationId:item.draftId});
  assert.deepEqual(draft,item.draft);
 }
 assert.deepEqual(await assets(area),result.areas[area].assets);
}
try{
 let port=await start();result.root=root;
 await until(()=>evaluate(port,'Boolean(document.querySelector("#chat-composer")&&globalThis.__nanfengDesktopWorkState?.runtimeInfo?.dataAreasReady)'),'native ready');
 const runtime=await invoke(port,'CHAT','read_desktop_runtime_info');assert.equal(runtime.startupMode,'UI_SCHEMA_DIAGNOSTIC_ACCEPTANCE');assert.equal(runtime.automaticWorkSuppressed,true);
 const {packageExchange,semanticHash,preflightPackage}=await import('../../protocol/scripts/exchange-lib.mjs');
 const original=JSON.parse(await readFile(join(desktopRoot,'../protocol/fixtures/nfai.exchange.v1.golden.json'),'utf8'));
 for(const area of areas){
  result.areas[area]={libraries:[]};
  await invoke(port,area,'ensure_desktop_local_workspace');
  for(const count of [4]){
   const ex=structuredClone(original);ex.export.id=`w15-large-${area.toLowerCase()}-${count}`;
   const inputAssets=new Map(),identities=[];
   ex.conversations=Array.from({length:count},(_,i)=>{
    const c=structuredClone(original.conversations[0]);c.id=`w15-${area.toLowerCase()}-${count}-${i}`;c.title=`合成大库 ${area} ${count}/${i}`;c.currentLeafId=c.id+'-a';
    c.messages[0].id=c.id+'-u';c.messages[0].blocks=[{kind:'TEXT',ordinal:0,text:'合成备份问题 '+i}];
    c.messages[1].id=c.id+'-a';c.messages[1].parentId=c.id+'-u';
    const data=assetBytes(area,count,i),digest=sha(data),entry='assets/'+digest;
    const asset={id:c.id+'-asset',entry,mimeType:'text/plain',displayName:`合成独立附件-${area}-${count}-${i}.txt`,byteCount:data.length,sha256:digest,classification:'NORMAL'};
    inputAssets.set(entry,data);identities.push(asset);
    c.messages[1].blocks=[{kind:'TEXT',ordinal:0,text:i===0?'长回答完整备份验证。'.repeat(9000):'完整合成回答 '+i},{kind:'ASSET_REF',ordinal:1,asset}];return c;
   });
   ex.export.semanticHash=semanticHash(ex);const bytes=packageExchange(ex,inputAssets);preflightPackage(bytes);
   const path=join(root,`synthetic-w15-${area}-${count}.nfai-exchange`);await writeFile(path,bytes,{flag:'wx'});
   const staged=await invoke(port,area,'stage_preflight_selected_exchange',{selectedPath:path});
   const imported=await invoke(port,area,'import_staged_exchange_as_new_workspace',{args:{stagingId:staged.stagingId,workspaceTitle:`合成W15 ${area} ${count}`}});
   assert.equal(imported.exchange.conversations.length,count);
   const item={id:imported.summary.id,hash:hash(imported.exchange),count,draftId:imported.exchange.conversations[1].id,assets:identities,input:path};
   await invoke(port,area,'save_desktop_conversation_draft',{args:{workspaceId:item.id,conversationId:item.draftId,text:`完整合成草稿 ${area} ${count}`,attachmentIds:[identities[1].id],modelId:null,toneOverride:null,webSearchOverride:null}});
   item.draft=await invoke(port,area,'read_desktop_conversation_draft',{workspaceId:item.id,conversationId:item.draftId});result.areas[area].libraries.push(item);
  }
  for(const item of result.areas[area].libraries){
   const selectedPath=join(root,`synthetic-export-${area}-${item.count}.nfai-exchange`);
   item.exportArtifact=await timed(area+' exchange export '+item.count,()=>invoke(port,area,'export_desktop_workspace_to_selected_path',{workspaceId:item.id,selectedPath}));
   const exported=preflightPackage(await readFile(selectedPath));
   const read=await invoke(port,area,'read_desktop_workspace',{workspaceId:item.id});
   assert.deepEqual(exported.exchange.conversations,read.exchange.conversations);
   assert.equal(exported.entryCount,preflightPackage(await readFile(item.input)).entryCount);item.exportPath=selectedPath;
  }
  result.areas[area].assets=await assets(area);check(area+' 4 independent referenced nonempty assets',result.areas[area].assets.length===4&&new Set(result.areas[area].assets.map(a=>a.sha256)).size===4);
  result.areas[area].physicalBytes=result.areas[area].assets.reduce((n,a)=>n+a.bytes,0);console.log(JSON.stringify({phase:'IMPORTED',area,assets:4,physicalBytes:result.areas[area].physicalBytes}));
  const path=join(root,`synthetic-w15-${area}.nfai-backup`);result.areas[area].backup=path;
  result.areas[area].artifact=await timed(area+' export',()=>invoke(port,area,'export_desktop_local_backup',{selectedPath:path}));
  assert.equal(sha(await readFile(path)),result.areas[area].artifact.sha256);
  result.areas[area].preflight=await timed(area+' preflight',()=>invoke(port,area,'preflight_desktop_local_backup',{selectedPath:path}));
  assert.equal(result.areas[area].preflight.missing.length,0);assert.equal(result.areas[area].preflight.unsupported.length,0);
  check(area+' native export/preflight with full file bytes',true);await verifyArea(port,area);
 }
 result.beforeSqlite=sqlite();await normalClose(port);
 port=await start(true);await until(()=>evaluate(port,'Boolean(document.querySelector("#chat-composer")&&globalThis.__nanfengDesktopWorkState?.runtimeInfo?.dataAreasReady)'),'pre-restore cold ready');
 for(const area of areas)await verifyArea(port,area);check('full cold data before restore',true);
 // Each area replacement is followed by a full diagnostic process restart.
 result.restores={};
 for(const area of areas){
 const item=result.areas[area].libraries[0];
 await invoke(port,area,'save_desktop_conversation_draft',{args:{workspaceId:item.id,conversationId:item.draftId,text:'备份后合成修改应被替换',attachmentIds:[],modelId:null,toneOverride:null,webSearchOverride:null}});
 const changed=join(areaRoot(area),'assets',item.assets[0].sha256);await writeFile(changed,Buffer.from('SYNTHETIC-AFTER-BACKUP'));
 const preflight=await invoke(port,area,'preflight_desktop_local_backup',{selectedPath:result.areas[area].backup});
 result.restore=await timed(area+' restore',()=>invoke(port,area,'restore_desktop_local_backup',{fingerprint:preflight.fingerprint,replaceLocal:true}));
 assert.equal(result.restore.restartRequired,true);result.restores[area]=result.restore;
 const persisted=JSON.parse(await readFile(join(areaRoot(area),'local-backup/v1/state.json'),'utf8'));
 assert.equal(persisted.processId,Number(launch.pid));assert.match(persisted.processInstance,/^[0-9a-f]{64}$/);
 result.instances??={};result.instances[area]={restorePid:Number(launch.pid),instance:persisted.processInstance};
 const blocked=await evaluate(port,`window.__TAURI__.core.invoke('save_desktop_conversation_draft',${JSON.stringify({args:{workspaceId:item.id,conversationId:item.draftId,text:'MUST NOT WRITE IN RESTORE PROCESS',attachmentIds:[],modelId:null,toneOverride:null,webSearchOverride:null}})},${JSON.stringify({headers:{'x-nanfeng-data-area':area}})}).then(()=>({unexpectedSuccess:true}),error=>({error:String(error)}))`);
 assert.ok(blocked.error?.includes('完全重启'));check(area+' same restoring process refuses business write',true);
assert.deepEqual(await assets(area),result.areas[area].assets);
 await verifyArea(port,area==='CHAT'?'WORK':'CHAT');check(area+' assets replaced and other area conserved',true);
 await normalClose(port);
 port=await start(true);await until(()=>evaluate(port,'Boolean(window.__TAURI__?.core?.invoke)'),'per-area diagnostic cold bridge');
 for(const selected of areas)await verifyArea(port,selected);check(area+' complete restored data/drafts and other area cold conserved',true);
 result.instances[area].coldPid=Number(launch.pid);
 const saved=JSON.parse(await readFile(join(areaRoot(area),'local-backup/v1/state.json'),'utf8'));
 assert.equal(saved.processInstance,result.instances[area].instance);
 // Real new process; do not forge OS PID reuse or mutate a persisted identity.
 const draft=result.areas[area].libraries[0].draft;
 assert.ok(Array.isArray(draft.attachments)&&draft.attachments.length>0,'restored draft retains actual attachments');
 const attachmentIds=draft.attachments.map(attachment=>{assert.equal(typeof attachment.id,'string');assert.ok(attachment.id.length>0);return attachment.id;});
 await invoke(port,area,'save_desktop_conversation_draft',{args:{workspaceId:item.id,conversationId:item.draftId,text:draft.text,attachmentIds,modelId:draft.modelId,toneOverride:draft.toneOverride,webSearchOverride:draft.webSearchOverride}});
 await verifyArea(port,area);check(area+' cold process accepts business write with restore state retained',true);

 }
 const area='WORK';
 result.afterRestoreStatus=await invoke(port,area,'read_desktop_local_backup_status');
 await normalClose(port);result.afterRestoreClosedSqlite=sqlite(true);
 port=await start(true);await until(()=>evaluate(port,'Boolean(window.__TAURI__?.core?.invoke)'),'diagnostic cold bridge');
 result.coldStatus=await invoke(port,area,'read_desktop_local_backup_status');
 result.coldReadBoundary={};
 for(const selected of areas){try{await verifyArea(port,selected);result.coldReadBoundary[selected]={fullRead:true};}catch(error){result.coldReadBoundary[selected]={fullRead:false,error:String(error)};}}
 for(const selected of areas)assert.equal(result.coldReadBoundary[selected].fullRead,true);
 assert.equal(result.coldStatus.operation,'RESTORED_RESTART_REQUIRED');
 assert.equal(result.coldStatus.restartRequired,true);
 assert.deepEqual(await assets(area),result.areas[area].assets);
 check('diagnostic cold retains complete restored files',true);
 result.normalRecoveryBoundary='Diagnostic startup suppresses business-state recovery/finalization. This run does not verify NORMAL recovery. Full W15/BG23 remains open.';
 await normalClose(port);result.finalSqlite=sqlite(true);result.preparationPass=true;
 if(normalFinalization){
  result.normalFinalizationAccepted=false;
  const checkpoints={};
  for(const selected of areas){
   const state=JSON.parse(await readFile(join(areaRoot(selected),'local-backup/v1/state.json'),'utf8'));
   assert.equal(state.operation,'RESTORED_RESTART_REQUIRED');assert.ok(state.checkpointId);
   checkpoints[selected]=state.checkpointId;
   assert.ok((await stat(join(areaRoot(selected),'local-backup/v1/recovery',state.checkpointId))).isDirectory());
  }
  result.normalStarts=[];
  for(let iteration=0;iteration<2;iteration++){
   port=await start(true,'normal');
   await until(()=>evaluate(port,'Boolean(document.querySelector("#chat-composer")&&globalThis.__nanfengDesktopWorkState?.runtimeInfo?.dataAreasReady)'),'NORMAL complete native ready');
   const runtime=await invoke(port,'CHAT','read_desktop_runtime_info');
   assert.equal(runtime.startupMode,'NORMAL');assert.equal(runtime.automaticWorkSuppressed,false);
   const statuses={};
   for(const selected of areas){
    await verifyArea(port,selected);
    statuses[selected]=await invoke(port,selected,'read_desktop_local_backup_status');
    assert.equal(statuses[selected].operation,'COMPLETED');assert.equal(statuses[selected].restartRequired,false);
    const state=JSON.parse(await readFile(join(areaRoot(selected),'local-backup/v1/state.json'),'utf8'));
    assert.equal(state.phase,'RESTARTED');assert.equal(state.checkpointId,null);
    assert.equal(state.processId,iteration===0?Number(launch.pid):result.normalStarts[0].pid);
    await assert.rejects(stat(join(areaRoot(selected),'local-backup/v1/recovery',checkpoints[selected])),{code:'ENOENT'});
    check(selected+' NORMAL '+iteration+' finalized and full restored data conserved',true);
   }
   result.normalStarts.push({pid:Number(launch.pid),runtime,statuses});
   await normalClose(port);result.normalClosedSqlite=sqlite(true);
  }
  result.normalFinalizationAccepted=true;
  result.normalRecoveryBoundary='Actual NORMAL startup finalized both restored areas and a second NORMAL cold start preserved complete data. Interrupted rollback and damaged/missing checkpoint recovery are not covered.';
 }
}catch(error){result.error=String(error.stack||error);process.exitCode=1;}
finally{
 if(launch){result.forcedCleanup=true;try{stop();}catch(error){result.cleanupError=String(error);}}
 result.pids=pids;
 if(root)await writeFile(join(root,'referenced-backup-evidence.json'),JSON.stringify(result,null,2));
}
console.log(JSON.stringify({root,preparationPass:result.preparationPass,normalFinalizationAccepted:result.normalFinalizationAccepted,error:result.error,fullNormalRecoveryAccepted:false}));
