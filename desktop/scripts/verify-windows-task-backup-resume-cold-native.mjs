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
const mode='referenced-backup';
assert.equal(process.argv.length,4);
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
let root = resolve(process.argv[3]);
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
  const args = ['-RootParent', rootParent, '-StartupMode', 'diagnostic', '-Visible', '-DebugPort', String(port)];
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

const originalEvidence=JSON.parse(await readFile(join(root,'task-assets-backup-evidence.json'),'utf8'));
assert.equal(originalEvidence.candidateSha256,receipt.sha256);assert.equal(originalEvidence.checks.length,46);assert.equal(originalEvidence.pids.length,4);assert.equal(originalEvidence.finalSqlite.allOk,true);assert.ok(!originalEvidence.forcedCleanup);
const areas=['CHAT','WORK'];
const {preflightPackage}=await import('../../protocol/scripts/exchange-lib.mjs');
const states={};
const sha=b=>createHash('sha256').update(b).digest('hex');
async function readTask(port,area,lib){return invoke(port,area,'read_latest_'+lib.prefix+'_import_task',{workspaceId:lib.id});}
function persistentTask(area,lib){return JSON.parse(execFileSync('python',['-c',"import sqlite3,sys,json,pathlib; c=sqlite3.connect(pathlib.Path(sys.argv[1]).as_uri()+'?mode=ro',uri=True); print(json.dumps(c.execute('select status,failure,retry_count,package_hash,storage_key from '+sys.argv[2]+' where id=?',(sys.argv[3],)).fetchone())); c.close()",join(root,area==='CHAT'?'':'work-area','workspace.sqlite3'),lib.prefix+'_import_tasks',lib.task.id],{encoding:'utf8',timeout:10000}));}
async function verify(port,area,initial=false){
 for(const lib of originalEvidence.areas[area].libraries){
  const ws=await invoke(port,area,'read_desktop_workspace',{workspaceId:lib.id});const task=await readTask(port,area,lib);const draft=await invoke(port,area,'read_desktop_conversation_draft',{workspaceId:lib.id,conversationId:lib.seedId});
  const key=area+'/'+lib.label;
  if(initial){
   assert.equal(hash(ws.exchange),lib.hash);const expected=structuredClone(lib.task);if(expected.status==='AWAITING_CONFIRMATION'){expected.status='FAILED';if(Object.hasOwn(expected,'failure'))expected.failure='INTERRUPTED';}assert.deepEqual(task,expected);assert.deepEqual(draft,lib.draft);states[key]={hash:hash(ws.exchange),task,draft};
  }else{assert.equal(hash(ws.exchange),states[key].hash);assert.deepEqual(task,states[key].task);assert.deepEqual(draft,states[key].draft);}
 }
 assert.deepEqual(await invoke(port,area,'list_desktop_workspace_exchange_v2_committed'),originalEvidence.areas[area].v2List);
 const exportPath=join(root,`v2-resume-${area}-${randomUUID()}.nfai-exchange`);await invoke(port,area,'reexport_desktop_workspace_exchange_v2_selected',{workspaceId:originalEvidence.areas[area].v2.workspaceId,selectedPath:exportPath});assert.equal(sha(await readFile(exportPath)),originalEvidence.areas[area].v2ExportHash);
 check(area+' full restored tasks/exchange/drafts/v2 '+(initial?'before explicit retry':'after explicit retry'),true);
}
function sqlite(){const data=JSON.parse(execFileSync('python',[join(desktopRoot,'scripts/check-stability-synthetic-sqlite.py'),root,'--closed-browser'],{encoding:'utf8',timeout:60000}));assert.ok(data.allOk);return data;}
try{
 let port=await start(true);result.root=root;await until(()=>evaluate(port,'Boolean(window.__TAURI__?.core?.invoke)'),'same-root native bridge');
 const runtime=await invoke(port,'CHAT','read_desktop_runtime_info');assert.equal(runtime.startupMode,'UI_SCHEMA_DIAGNOSTIC_ACCEPTANCE');assert.equal(runtime.automaticWorkSuppressed,true);result.runtime=runtime;
 for(const area of areas)await verify(port,area,true);
 result.retries=[];result.exports=[];
 for(const area of areas){
  for(const lib of originalEvidence.areas[area].libraries.filter(l=>l.label.endsWith('-staged'))){
   const before=await readTask(port,area,lib);assert.equal(before.status,'FAILED');assert.ok(before.items.every(i=>i.status==='PENDING_CONFIRMATION'));
   const persistentBefore=persistentTask(area,lib);assert.deepEqual(persistentBefore.slice(0,2),['FAILED','INTERRUPTED']);await invoke(port,area,'retry_'+lib.prefix+'_import_task',{taskId:before.id,workspaceId:lib.id});const retried=await readTask(port,area,lib);assert.equal(retried.id,before.id);assert.equal(retried.status,'AWAITING_CONFIRMATION');const persistentAfter=persistentTask(area,lib);assert.equal(persistentAfter[2],persistentBefore[2]+1);assert.deepEqual(persistentAfter.slice(3),persistentBefore.slice(3));assert.equal(retried.items.length,12);result.persistentRetries??=[];result.persistentRetries.push({area,label:lib.label,before:persistentBefore,after:persistentAfter});
   const ids=[];for(const item of retried.items)ids.push(await invoke(port,area,'confirm_'+lib.prefix+'_import_item',{args:{workspaceId:lib.id,taskId:retried.id,itemId:item.id}}));assert.equal(new Set(ids).size,12);
   const after=await readTask(port,area,lib);assert.equal(after.status,'COMPLETED');assert.ok(after.items.every(i=>i.status==='CONFIRMED'));
   const ws=await invoke(port,area,'read_desktop_workspace',{workspaceId:lib.id}),rows=ws.exchange.conversations.filter(c=>c.id!==lib.seedId),input=JSON.parse(await readFile(lib.input,'utf8'));assert.equal(rows.length,12);
   for(const source of input){const row=rows.find(c=>c.title===(source.title??source.name));assert.ok(row);const expected=source.mapping?[source.mapping.u.message.content.parts[0],source.mapping.a.message.content.parts[0]]:(source.chat_messages??JSON.parse(source.sourceText).chat_messages).map(m=>m.text);assert.deepEqual(row.messages.map(m=>m.blocks.filter(b=>b.kind==='TEXT').map(b=>b.text).join('\n')),expected);}
   const key=area+'/'+lib.label;states[key].hash=hash(ws.exchange);states[key].task=after;result.retries.push({area,label:lib.label,workspaceId:lib.id,taskId:after.id,ids,beforeHash:hash(before),retriedHash:hash(retried),afterHash:hash(after)});check(area+'/'+lib.label+' restored private package explicit retry and all12 confirmations',true);
  }
  await verify(port,area);await verify(port,area==='CHAT'?'WORK':'CHAT');
 }
 for(const area of areas)for(const lib of originalEvidence.areas[area].libraries){
  const selectedPath=join(root,`resume-export-${area}-${lib.label}.nfai-exchange`);const artifact=await invoke(port,area,'export_desktop_workspace_to_selected_path',{workspaceId:lib.id,selectedPath});const exported=preflightPackage(await readFile(selectedPath));const ws=await invoke(port,area,'read_desktop_workspace',{workspaceId:lib.id});assert.deepEqual(exported.exchange.conversations,ws.exchange.conversations);result.exports.push({area,label:lib.label,workspaceId:lib.id,selectedPath,artifact,conversationHash:hash(ws.exchange.conversations),conversations:ws.exchange.conversations.length});
 }
 check('all22 restored/retried workspaces actual semantic exports complete',result.exports.length===22);
 await normalClose(port);result.preColdSqlite=sqlite();port=await start(true);await until(()=>evaluate(port,'Boolean(window.__TAURI__?.core?.invoke)'),'explicit retry cold bridge');for(const area of areas)await verify(port,area);
 result.finalTaskStates=states;result.status={};for(const area of areas){result.status[area]=await invoke(port,area,'read_desktop_local_backup_status');assert.equal(result.status[area].operation,'RESTORED_RESTART_REQUIRED');}
 await normalClose(port);result.finalSqlite=sqlite();result.pass=true;
}catch(error){result.error=String(error.stack||error);process.exitCode=1;}
finally{if(launch){result.forcedCleanup=true;try{stop();}catch(error){result.cleanupError=String(error);}}result.pids=pids;await writeFile(join(root,'task-assets-backup-resume-cold-third-evidence.json'),JSON.stringify(result,null,2),{flag:'wx'});}
console.log(JSON.stringify({root,pass:result.pass,checks:result.checks.length,retries:result.retries?.length,error:result.error,fullNormalRecoveryAccepted:false}));
