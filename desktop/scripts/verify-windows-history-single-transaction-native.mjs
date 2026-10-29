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
const mode='history-single-transaction';
assert.equal(process.argv.length,3);
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
let watcher=null;
async function exchangeDigest(port,workspaceId,expectedHashes=null){
 return evaluate(port,`(async()=>{const p=await window.__TAURI__.core.invoke('read_desktop_workspace',{workspaceId:${JSON.stringify(workspaceId)}},{headers:{'x-nanfeng-data-area':'CHAT'}});const digest=async text=>Array.from(new Uint8Array(await crypto.subtle.digest('SHA-256',new TextEncoder().encode(text))),b=>b.toString(16).padStart(2,'0')).join('');const expected=${JSON.stringify(expectedHashes)};let valid=true;let messageCount=0;if(expected){const c=p.exchange.conversations.find(c=>c.title==='W14 单项事务合成中断');if(!c)return {valid:false};messageCount=c.messages.length;valid=messageCount===expected.length;for(let i=0;i<c.messages.length&&valid;i++){valid=await digest(c.messages[i].blocks.filter(b=>b.kind==='TEXT').map(b=>b.text).join('\\n'))===expected[i];}}return {hash:await digest(JSON.stringify(p.exchange)),valid,messageCount}})()`);
}
try {
 let port=await start();result.root=root;
 await until(()=>evaluate(port,'Boolean(window.__TAURI__?.core?.invoke)'),'native IPC');
 const workspaceId=(await invoke(port,'CHAT','ensure_desktop_local_workspace')).summary.id;result.workspaceId=workspaceId;
 const baseline=await invoke(port,'CHAT','read_desktop_workspace',{workspaceId});result.baselineExchangeHash=hash(baseline.exchange);
 const other=(await invoke(port,'WORK','ensure_desktop_local_workspace')).summary.id;
 const otherBaseline=hash((await invoke(port,'WORK','read_desktop_workspace',{workspaceId:other})).exchange);
 const messageCount=1500;
 const messages=Array.from({length:messageCount},(_,i)=>({uuid:`txn-message-${i}`,parent_message_uuid:i?`txn-message-${i-1}`:null,sender:i%2?'assistant':'human',created_at:'2026-08-15T08:00:00Z',text:`事务消息 ${i} 中文\n`+'x'.repeat(18000)}));
 const input=[{uuid:'w14-single-transaction-synthetic',name:'W14 单项事务合成中断',created_at:'2026-08-15T08:00:00Z',updated_at:'2026-08-15T08:00:00Z',chat_messages:messages}];
 const path=join(root,'single-transaction-synthetic.json');await writeFile(path,JSON.stringify(input),{flag:'wx'});
 const staged=await evaluate(port,`window.__TAURI__.core.invoke('stage_claude_export_selected',${JSON.stringify({selectedPath:path,workspaceId})},{headers:{'x-nanfeng-data-area':'CHAT'}}).then(value=>({id:value.id,candidateCount:value.candidates.length})).catch(error=>({error:String(error)}))`);assert.ok(staged.id,staged.error);result.taskId=staged.id;
 const readyTask=await invoke(port,'CHAT','read_latest_claude_import_task',{workspaceId});assert.equal(readyTask.items.length,1);assert.equal(readyTask.items[0].status,'PENDING_CONFIRMATION');
 result.readyTask=readyTask;await writeFile(join(root,'single-transaction-progress.json'),JSON.stringify(result,null,2));
 watcher=spawn('python',[join(desktopRoot,'scripts/interrupt-synthetic-import-wal.py'),launch.launchReceipt],{stdio:['ignore','pipe','pipe'],windowsHide:true});
 let watcherOutput='';let watcherError='';watcher.stdout.on('data',chunk=>{watcherOutput+=chunk.toString()});watcher.stderr.on('data',chunk=>{watcherError+=chunk.toString()});
 const watcherExit=new Promise(resolve=>watcher.once('exit',code=>resolve(code)));
 await until(()=>watcherOutput.includes('READY'),'verified WAL watcher ready');
 const interruptedPid=Number(launch.pid);
 await evaluate(port,`(()=>{globalThis.__w14Txn={started:true};window.__TAURI__.core.invoke('confirm_claude_import_item',{args:{workspaceId:${JSON.stringify(workspaceId)},taskId:${JSON.stringify(staged.id)},itemId:${JSON.stringify(readyTask.items[0].id)}}},{headers:{'x-nanfeng-data-area':'CHAT'}}).then(id=>{globalThis.__w14Txn={resolved:true,id}},error=>{globalThis.__w14Txn={failed:true,error:String(error)}});return true})()`);
 const observed=await until(async()=>{const value=JSON.parse(await readFile(join(root,'wal-interrupt-observation.json'),'utf8'));if(value.state==='NOT_OBSERVED'||value.state==='TERMINATION_FAILED')throw new Error(value.state);return value.state==='INTERRUPTED_UNCOMMITTED_WAL'?value:false;},'actual uncommitted WAL interruption');
 assert.equal(await watcherExit,0,watcherError);watcher=null;result.interruption=observed;
 await until(()=>ps(`@(Get-CimInstance Win32_Process -Filter "ProcessId=${interruptedPid}").Count`)==='0','interrupted candidate exit');pids.push(interruptedPid);launch=null;
 await until(()=>ps(`@(Get-CimInstance Win32_Process -Filter "Name='msedgewebview2.exe'" | Where-Object { $_.CommandLine -and $_.CommandLine.Contains(${quote(join(root,'webview2'))}) }).Count`)==='0','interrupted profile exit');
 check('single confirmation new WAL frames without commit marker observed before exact termination',observed.terminationAccepted&&observed.newFrames.length>0&&observed.newFrames.every(f=>f.commitDatabasePages===0));
 port=await start(true);await until(()=>evaluate(port,'Boolean(window.__TAURI__?.core?.invoke)'),'interrupted cold native IPC');
 const recovered=await invoke(port,'CHAT','read_desktop_workspace',{workspaceId});assert.equal(hash(recovered.exchange),result.baselineExchangeHash);
 const task=await invoke(port,'CHAT','read_latest_claude_import_task',{workspaceId});assert.deepEqual(task,readyTask);check('cold rollback exact previous exchange and pending item',true);
 assert.equal(hash((await invoke(port,'WORK','read_desktop_workspace',{workspaceId:other})).exchange),otherBaseline);check('other area exact exchange conserved',true);
 const id=await timed('explicit resume single transaction',()=>invoke(port,'CHAT','confirm_claude_import_item',{args:{workspaceId,taskId:task.id,itemId:task.items[0].id}}));result.conversationId=id;
 const full=await exchangeDigest(port,workspaceId,messages.map(m=>createHash('sha256').update(m.text).digest('hex')));assert.equal(full.messageCount,messageCount);assert.equal(full.valid,true);check('explicit resume exact all 1500 message hashes',true);
 result.resumedHash=full.hash;result.completedTask=await invoke(port,'CHAT','read_latest_claude_import_task',{workspaceId});assert.equal(result.completedTask.items[0].status,'CONFIRMED');
 await normalClose(port);port=await start(true);await until(()=>evaluate(port,'Boolean(window.__TAURI__?.core?.invoke)'),'resumed normal Close cold IPC');
 assert.equal((await exchangeDigest(port,workspaceId)).hash,result.resumedHash);assert.deepEqual(await invoke(port,'CHAT','read_latest_claude_import_task',{workspaceId}),result.completedTask);check('normal Close cold complete exchange and one receipt state',true);
 await normalClose(port);result.pass=true;
}catch(error){result.pass=false;result.error=String(error);result.stack=error.stack;throw error;}
finally{
 if(watcher){watcher.kill();result.watcherCleanup=true;}
 if(launch){result.forcedCleanup=true;stop();}
 result.pids=pids;if(root)await writeFile(join(root,'single-transaction-evidence.json'),JSON.stringify(result,null,2));
 console.log(JSON.stringify({root,pass:result.pass,error:result.error,checks:result.checks.length,pids}));
}
