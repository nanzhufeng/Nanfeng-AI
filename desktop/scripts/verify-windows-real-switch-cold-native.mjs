import assert from 'node:assert/strict';
import { createHash, randomUUID } from 'node:crypto';
import { execFileSync, spawn } from 'node:child_process';
import { mkdir, readFile, writeFile, appendFile } from 'node:fs/promises';
import { dirname, join, resolve, sep } from 'node:path';
import { fileURLToPath } from 'node:url';

if (process.platform !== 'win32') throw new Error('仅在 Windows 隔离候选上验收。');
const desktopRoot = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const rootParent = dirname(resolve(process.argv[2]));
const taskArea=process.argv[3];assert.ok(['CHAT','WORK'].includes(taskArea));
const otherArea='WORK';
const mode='real-switch-backup';
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

root=resolve(process.argv[2]);
const previous=JSON.parse(await readFile(join(root,'real-switch-backup-evidence.json'),'utf8'));
result={...previous,previousError:previous.error,previousForcedCleanup:previous.forcedCleanup,preparationPass:false,error:undefined,forcedCleanup:undefined,checks:[...previous.checks]};
assert.equal(previous.interruptionHit,true);
try{
 const port=await start(true),area=taskArea;
 await until(()=>evaluate(port,'Boolean(window.__TAURI__?.core?.invoke)'),'same-root diagnostic cold bridge');
 result.coldRuntime=await invoke(port,'CHAT','read_desktop_runtime_info');assert.equal(result.coldRuntime.automaticWorkSuppressed,true);
 result.coldStatus=await invoke(port,area,'read_desktop_local_backup_status');assert.equal(result.coldStatus.operation,'INTERRUPTED');
 result.coldPersistedState=JSON.parse(await readFile(join(areaRoot(area),'local-backup/v1/state.json'),'utf8'));
 assert.deepEqual(result.coldPersistedState,result.observer.afterTerminateState);
 await verifyArea(port,area==='CHAT'?'WORK':'CHAT');check('other area complete exchange/drafts/assets cold conserved',true);
 result.targetColdRead={};
 for(const library of result.areas[area].libraries){const read=await invoke(port,area,'read_desktop_workspace',{workspaceId:library.id});result.targetColdRead[library.id]={hash:hash(read.exchange),expected:library.hash,matches:hash(read.exchange)===library.hash};assert.equal(hash(read.exchange),library.hash);}
 const item=result.areas[area].libraries[0];result.targetColdDraft=await invoke(port,area,'read_desktop_conversation_draft',{workspaceId:item.id,conversationId:item.draftId});
 result.normalRecoveryBoundary='Diagnostic cold suppresses recovery. Actual SWITCHING/checkpoint retained; NORMAL rollback remains open.';
 await normalClose(port);result.finalSqlite=sqlite(true);result.preparationPass=true;
}catch(error){result.error=String(error.stack||error);process.exitCode=1;}
finally{if(launch){result.forcedCleanup=true;try{stop();}catch(error){result.cleanupError=String(error);}}result.coldPids=pids;await writeFile(join(root,'real-switch-cold-final-evidence.json'),JSON.stringify(result,null,2),{flag:'wx'});}
console.log(JSON.stringify({root,preparationPass:result.preparationPass,interruptionHit:result.interruptionHit,error:result.error,fullNormalRecoveryAccepted:false}));
