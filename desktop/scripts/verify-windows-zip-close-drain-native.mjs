import assert from 'node:assert/strict';
import { createHash } from 'node:crypto';
import { execFileSync, spawn } from 'node:child_process';
import { readFile, writeFile } from 'node:fs/promises';
import http from 'node:http';
import { dirname, join, resolve, sep } from 'node:path';
import { fileURLToPath } from 'node:url';

if (process.platform !== 'win32') throw new Error('仅供 Windows 独立候选验收');
const desktopRoot = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const rootParent = resolve(process.argv.slice(2).find(value => !value.startsWith('--')) || 'D:\\CodexTemp');
const narrowWindow = process.argv.includes('--narrow-window');
const exitDrainWork = process.argv.includes('--exit-drain-work');
const requestArea = 'CHAT';
const contextWindowTokens = narrowWindow ? 1024 : 4096;
const requestedOutputTokens = narrowWindow ? 256 : 1024;
const inputBudgetTokens = contextWindowTokens - requestedOutputTokens;
const receipt = JSON.parse(await readFile(join(desktopRoot, 'output/windows-acceptance/current.json'), 'utf8'));
assert.equal(receipt.identifier, 'com.nanzhufeng.ai.desktop.windowsacceptance');
const source = resolve(desktopRoot, 'output/windows-acceptance', receipt.sha256, 'NanfengAI-Windows-Acceptance.exe');
const running = resolve(desktopRoot, 'output/windows-acceptance-binaries', receipt.sha256, 'nanfeng-ai-desktop-spike.exe');
assert.equal(resolve(receipt.executable), source);
assert.equal(createHash('sha256').update(await readFile(source)).digest('hex').toUpperCase(), receipt.sha256);
const quote = value => `'${String(value).replaceAll("'", "''")}'`;
const ps = script => execFileSync('pwsh', ['-NoProfile', '-Command', script], { encoding: 'utf8', timeout: 60000 }).trim();
const listen = async server => new Promise(done => server.listen(0, '127.0.0.1', done));
const reservePort = async () => {
  const server = http.createServer();
  await listen(server);
  const port = server.address().port;
  await new Promise(done => server.close(done));
  return port;
};
const requests = [];
let held = null;
const provider=http.createServer(async(request,response)=>{
 try {
  const chunks=[];for await(const chunk of request)chunks.push(chunk);
  const body=JSON.parse(Buffer.concat(chunks).toString('utf8'));requests.push({model:body.model});
  response.writeHead(200,{'content-type':'text/event-stream'});
  response.write(`data: ${JSON.stringify({model:body.model,choices:[{delta:{content:'合成公开材料已核对完成。',annotations:[{url_citation:{title:'合成来源（未抓取）',url:'https://example.com/synthetic-monitor'}}]}}]})}\n\n`);
  response.end(`data: ${JSON.stringify({choices:[{delta:{},finish_reason:'stop'}],usage:{prompt_tokens:20,completion_tokens:8}})}\n\ndata: [DONE]\n\n`);
 }catch{response.writeHead(400);response.end();}
});
await listen(provider);
const providerPort=provider.address().port;
let launch = null;
let root = '';
const stoppedPids = [];
async function start(reopen = false) {
  const debugPort = await reservePort();
  const args = ['-NoProfile', '-File', join(desktopRoot, 'scripts/start-windows-acceptance.ps1'),
    '-RootParent', rootParent, '-StartupMode', 'normal', '-Visible', '-DebugPort', String(debugPort),
    '-OrdinaryChatTestEndpoint', `http://127.0.0.1:${providerPort}`];
  if (reopen) args.push('-Root', root);
  const result = await new Promise((done, fail) => {
    const child = spawn('pwsh', args, { stdio: ['ignore', 'pipe', 'pipe'] });
    let output = '';
    let error = '';
    const timer = setTimeout(() => { child.kill(); fail(new Error(`启动超时：${error}`)); }, 30000);
    child.stdout.on('data', chunk => {
      output += chunk.toString();
      const line = output.split(/\r?\n/).find(value => value.trim().startsWith('{') && value.includes('"pid"'));
      if (!line) return;
      clearTimeout(timer);
      try { done(JSON.parse(line)); } catch (cause) { fail(cause); }
      child.stdout.destroy(); child.stderr.destroy(); child.unref();
    });
    child.stderr.on('data', chunk => { error += chunk.toString(); });
    child.on('error', cause => { clearTimeout(timer); fail(cause); });
    child.on('exit', code => { if (!output.includes('"pid"')) { clearTimeout(timer); fail(new Error(`启动失败 ${code}：${error}`)); } });
  });
  assert.equal(result.sha256, receipt.sha256);
  assert.equal(resolve(result.executable), running);
  launch = result;
  root = resolve(result.root);
  assert.ok(root.startsWith(rootParent + sep));
  assert.equal(JSON.parse(await readFile(join(root, '.nanfeng-windows-acceptance.json'), 'utf8')).identifier, receipt.identifier);
  return debugPort;
}
function stop() {
  if (!launch) return;
  const pid = Number(launch.pid);
  ps(`$p=Get-CimInstance Win32_Process -Filter "ProcessId=${pid}"; if ($p) { if ([IO.Path]::GetFullPath([string]$p.ExecutablePath) -ne ${quote(running)} -or (Get-FileHash -LiteralPath ${quote(running)} -Algorithm SHA256).Hash -ne ${quote(receipt.sha256)}) { throw 'PID identity mismatch' }; Stop-Process -Id ${pid} -Force -ErrorAction Stop }`);
  stoppedPids.push(pid);
  launch = null;
}
async function cdp(port, method, params = {}) {
  const tabs = await fetch(`http://127.0.0.1:${port}/json/list`).then(response => response.json());
  const tab = tabs.find(item => item.type === 'page' && item.url === 'http://tauri.localhost/');
  assert.ok(tab, 'WebView2 页面未就绪');
  const socket = new WebSocket(tab.webSocketDebuggerUrl);
  try {
    await new Promise((done, fail) => { socket.addEventListener('open', done, { once: true }); socket.addEventListener('error', fail, { once: true }); });
    return await new Promise((done, fail) => {
      const timer = setTimeout(() => fail(new Error(`${method} 超时`)), 30000);
      socket.addEventListener('message', event => {
        const reply = JSON.parse(event.data);
        if (reply.id !== 1) return;
        clearTimeout(timer);
        if (reply.error || reply.result?.exceptionDetails) fail(new Error(reply.result?.exceptionDetails?.text || reply.error?.message));
        else done(reply.result?.result?.value);
      });
      socket.send(JSON.stringify({ id: 1, method, params }));
    });
  } finally { socket.close(); }
}
const evaluate = (port, expression) => cdp(port, 'Runtime.evaluate', { expression, awaitPromise: true, returnByValue: true });
async function invoke(port, command, args = {}) {
  const value = await evaluate(port, `window.__TAURI__.core.invoke(${JSON.stringify(command)},${JSON.stringify(args)},${JSON.stringify({ headers: { 'x-nanfeng-data-area': requestArea } })}).catch(error=>({__error:String(error)}))`);
  if (value?.__error) throw new Error(`${command}: ${value.__error}`);
  return value;
}
async function until(read, label, timeout = 30000) {
  const deadline = Date.now() + timeout;
  while (Date.now() < deadline) {
    try { const value = await read(); if (value) return value; } catch { /* WebView reload. */ }
    await new Promise(done => setTimeout(done, 150));
  }
  throw new Error(`${label} 未出现`);
}
let lockProcess=null;
let parentPid=0;
try {
 let port=await start();parentPid=Number(launch.pid);
 console.log(JSON.stringify({phase:'started',root,pid:parentPid,candidateSha256:receipt.sha256}));
 await until(()=>evaluate(port,'Boolean(window.__TAURI__?.core?.invoke && globalThis.__nanfengDesktopWorkState?.runtimeInfo?.dataAreasReady)'),'原生就绪');
 const workspaceId=(await invoke(port,'ensure_desktop_local_workspace')).summary.id;
 const zipPath=join(root,'synthetic-close-drain.zip');
 const makeZip=`import sys,json,zipfile; value=[{'id':'synthetic-close-drain','title':'Synthetic close drain','create_time':1,'update_time':2,'mapping':{'m-user':{'parent':None,'message':{'author':{'role':'user'},'content':{'parts':['Synthetic close drain body']}}}}}]; z=zipfile.ZipFile(sys.argv[1],'w'); z.writestr('conversations.json',json.dumps(value)); z.close()`;
 execFileSync('python',['-c',makeZip,zipPath],{timeout:10000});
 const lockCode="import sqlite3,sys; c=sqlite3.connect(sys.argv[1]); c.execute('BEGIN EXCLUSIVE'); print('LOCKED',flush=True); sys.stdin.readline(); c.rollback(); c.close()";
 lockProcess=spawn('python',['-u','-c',lockCode,join(root,'workspace.sqlite3')],{stdio:['pipe','pipe','pipe']});
 await new Promise((done,fail)=>{let value='';const timer=setTimeout(()=>fail(new Error('合成锁未就绪')),10000);lockProcess.stdout.on('data',chunk=>{value+=chunk;if(value.includes('LOCKED')){clearTimeout(timer);done();}});lockProcess.on('error',fail);});
 const args={workspaceId,provider:'CHATGPT',selectedPath:zipPath};
 await evaluate(port,`(()=>{window.__nfDrainResult=null;window.__TAURI__.core.invoke('stage_p6k_zip_import_selected',{args:${JSON.stringify(args)}},{headers:{'x-nanfeng-data-area':'CHAT'}}).then(value=>window.__nfDrainResult={ok:true,value},error=>window.__nfDrainResult={ok:false,error:String(error)});return true;})()`);
 await new Promise(done=>setTimeout(done,300));
 const closeRequested=ps(`$p=Get-Process -Id ${parentPid} -ErrorAction Stop; if ([IO.Path]::GetFullPath([string]$p.Path) -ne ${quote(running)}) { throw 'PID identity mismatch' }; $p.CloseMainWindow()`);
 assert.equal(closeRequested,'True','正常窗口关闭请求没有送达');
 await new Promise(done=>setTimeout(done,500));
 const aliveDuringBlocked=ps(`@(Get-Process -Id ${parentPid} -ErrorAction SilentlyContinue).Count`) === '1';
 console.log(JSON.stringify({phase:'close-requested-with-zip-blocked',aliveDuringBlocked,root,candidateSha256:receipt.sha256}));
 assert.equal(aliveDuringBlocked,true,'执行中的ZIP尚未释放，进程已退出');
 lockProcess.stdin.end('\n');
 await new Promise((done,fail)=>{lockProcess.on('exit',code=>code===0?done():fail(new Error('合成锁释放失败')));});lockProcess=null;
 await until(()=>ps(`@(Get-Process -Id ${parentPid} -ErrorAction SilentlyContinue).Count`) === '0','正常关闭完成',20000);
 launch=null;
 const readTask=()=>JSON.parse(execFileSync('python',['-c',"import sqlite3,json,sys;c=sqlite3.connect('file:'+sys.argv[1]+'?mode=ro',uri=True);print(json.dumps({'tasks':c.execute('SELECT status FROM p6k_zip_import_tasks').fetchall(),'items':c.execute('SELECT status FROM p6k_zip_import_items').fetchall(),'integrity':c.execute('PRAGMA integrity_check').fetchall()}));c.close()",join(root,'workspace.sqlite3')],{encoding:'utf8',timeout:10000}));
 const closed=readTask();assert.deepEqual(closed.tasks,[['COMPLETED']]);assert.deepEqual(closed.items,[['COMMITTED']]);assert.deepEqual(closed.integrity,[['ok']]);
 port=await start(true);
 await until(()=>evaluate(port,'Boolean(window.__TAURI__?.core?.invoke && globalThis.__nanfengDesktopWorkState?.runtimeInfo?.dataAreasReady)'),'冷启就绪');
 const task=await invoke(port,'read_latest_p6k_zip_import_task',{workspaceId});assert.equal(task.status,'COMPLETED');assert.equal(task.importedCount,1);
 console.log(JSON.stringify({candidateSha256:receipt.sha256,root,aliveDuringBlocked,closed,coldReopen:{status:task.status,importedCount:task.importedCount},layer:'NATIVE_NORMAL_CLOSE_SYNTHETIC_ZIP_EXECUTION_SQL_LOCK_NOT_MEDIA_DRAIN'}));
} finally {
 if(lockProcess){lockProcess.stdin.end('\n');lockProcess.kill();}
 stop();provider.closeAllConnections();await new Promise(done=>provider.close(done));
}
