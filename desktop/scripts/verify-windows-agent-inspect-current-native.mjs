import assert from 'node:assert/strict';
import { createHash, randomUUID } from 'node:crypto';
import { execFileSync, spawn } from 'node:child_process';
import { mkdir, readFile, writeFile } from 'node:fs/promises';
import http from 'node:http';
import { dirname, join, resolve, sep } from 'node:path';
import { fileURLToPath } from 'node:url';

if (process.platform !== 'win32') throw new Error('仅在 Windows 隔离候选上验收。');
const desktopRoot = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const rootParent = resolve(process.argv[2] || 'D:\\CodexTemp');
assert.ok(process.execArgv.includes('--max-old-space-size=2048'));
const receipt = JSON.parse(await readFile(join(desktopRoot, 'output/windows-acceptance/current.json'), 'utf8'));
assert.equal(receipt.identifier, 'com.nanzhufeng.ai.desktop.windowsacceptance');
const source = resolve(desktopRoot, 'output/windows-acceptance', receipt.sha256, 'NanfengAI-Windows-Acceptance.exe');
assert.equal(resolve(receipt.executable), source);
assert.equal(createHash('sha256').update(await readFile(source)).digest('hex').toUpperCase(), receipt.sha256);
const runExecutable = resolve(desktopRoot, 'output/windows-acceptance-binaries', receipt.sha256, 'nanfeng-ai-desktop-spike.exe');
const ps = (args) => execFileSync('pwsh', ['-NoProfile', ...args], { encoding: 'utf8', timeout: 60000 }).trim();
const launchPowerShell = (args) => new Promise((resolveLaunch, reject) => {
  const child = spawn('pwsh', ['-NoProfile', ...args], { stdio: ['ignore', 'pipe', 'pipe'] });
  let stdout = '';
  let stderr = '';
  const timer = setTimeout(() => { child.kill(); reject(new Error(`隔离候选启动超时：${stderr}`)); }, 30000);
  child.stdout.on('data', data => {
    stdout += data.toString();
    const line = stdout.split(/\r?\n/).find(item => item.trim().startsWith('{') && item.includes('"pid"'));
    if (!line) return;
    clearTimeout(timer);
    try { const emitted=JSON.parse(line); readFile(emitted.launchReceipt, 'utf8').then(data=>resolveLaunch(JSON.parse(data)),reject); } catch (error) { reject(error); }
    child.stdout.destroy();
    child.stderr.destroy();
    child.unref();
  });
  child.stderr.on('data', data => { stderr += data.toString(); });
  child.on('error', error => { clearTimeout(timer); reject(error); });
  child.on('exit', code => {
    if (!stdout.includes('"pid"')) { clearTimeout(timer); reject(new Error(`隔离候选启动失败 ${code}：${stderr}`)); }
  });
});
const reservePort = async () => {
  const server = http.createServer();
  await new Promise(resolveReady => server.listen(0, '127.0.0.1', resolveReady));
  const port = server.address().port;
  await new Promise(resolveClose => server.close(resolveClose));
  return port;
};
const requests=[];
const provider=http.createServer(async(request,response)=>{
 let body='';for await(const chunk of request)body+=chunk.toString('utf8');
 const payload=JSON.parse(body);requests.push({payload,path:request.url});
 response.writeHead(200,{'content-type':'text/event-stream; charset=utf-8'});
 response.end('data: '+JSON.stringify({model:'openai/gpt-5.6-terra',choices:[{delta:{content:'合成预算完整回答'},finish_reason:'stop'}],usage:{prompt_tokens:32,completion_tokens:8,cost:0.000002}})+'\n\ndata: [DONE]\n\n');
});
await new Promise(done=>provider.listen(0,'127.0.0.1',done));const providerPort=provider.address().port;
let launch = null;
let root = '';
const pids = [];
const stop = () => {
  if (!launch) return;
  const pid = Number(launch.pid);
  const literal = value => `'${String(value).replaceAll("'", "''")}'`;
  const script = '$expected=' + literal(runExecutable) + '; $hash=' + literal(receipt.sha256) + '; '
    + '$p=Get-CimInstance Win32_Process -Filter "ProcessId=' + pid + '"; '
    + 'if ($p) { if ([IO.Path]::GetFullPath([string]$p.ExecutablePath) -ne $expected '
    + '-or (Get-FileHash -LiteralPath $expected -Algorithm SHA256).Hash -ne $hash) { throw "PID identity mismatch" }; '
    + 'Stop-Process -Id ' + pid + ' -Force -ErrorAction Stop }';
  ps(['-Command', script]);
  pids.push(pid);
  launch = null;
};
async function start(reopen = false) {
  const port = await reservePort();
  const args = ['-File', join(desktopRoot, 'scripts/start-windows-acceptance.ps1'), '-ExecutablePath', source, '-RootParent', rootParent,
    '-StartupMode', 'normal', '-Visible', '-DebugPort', String(port), '-OrdinaryChatTestEndpoint', `http://127.0.0.1:${providerPort}`];
  if (reopen) args.push('-Root', root);
  launch = await launchPowerShell(args);
  assert.equal(launch.sha256, receipt.sha256);
  assert.equal(resolve(launch.executable), runExecutable);
  root = resolve(launch.root);
  assert.ok(root.startsWith(rootParent + sep));
  const marker = JSON.parse(await readFile(join(root, '.nanfeng-windows-acceptance.json'), 'utf8'));
  assert.equal(marker.identifier, receipt.identifier);
  return port;
}
async function cdp(port, method, params = {}) {
  const tabs = await fetch(`http://127.0.0.1:${port}/json/list`,{signal:AbortSignal.timeout(10000)}).then(response => response.json());
  const tab = tabs.find(item => item.type === 'page' && item.url === 'http://tauri.localhost/');
  assert.ok(tab, '验收 WebView2 页面未就绪');
  const socket = new WebSocket(tab.webSocketDebuggerUrl);
  try {
    await new Promise((resolveReady, reject) => {
      socket.addEventListener('open', resolveReady, { once: true });
      socket.addEventListener('error', reject, { once: true });
    });
    return await new Promise((resolveResult, reject) => {
      const timer = setTimeout(() => reject(new Error('CDP evaluation timed out')), 20000);
      socket.addEventListener('message', event => {
        const message = JSON.parse(event.data);
        if (message.id !== 1) return;
        clearTimeout(timer);
        if (message.error || message.result?.exceptionDetails) reject(new Error(
          message.result?.exceptionDetails?.exception?.description || message.result?.exceptionDetails?.text || message.error?.message));
        else resolveResult(message.result);
      });
      socket.send(JSON.stringify({ id: 1, method, params }));
    });
  } finally { socket.close(); }
}
async function evaluate(port, expression) {
  return (await cdp(port, 'Runtime.evaluate', { expression, awaitPromise: true, returnByValue: true }))?.result?.value;
}
async function invoke(port, area, command, args = {}) {
  const expression = `window.__TAURI__.core.invoke(${JSON.stringify(command)},${JSON.stringify(args)},${JSON.stringify({ headers: { 'x-nanfeng-data-area': area } })}).catch(error=>({__error:String(error)}))`;
  const result = await evaluate(port, expression);
  if (result?.__error) throw new Error(`${command}: ${result.__error}`);
  return result;
}
async function ready(port) {
  const deadline = Date.now() + 30000;
  while (Date.now() < deadline) {
    try { if (await evaluate(port, 'Boolean(window.__TAURI__?.core?.invoke)')) return; } catch { /* Startup is still active. */ }
    await new Promise(resolveWait => setTimeout(resolveWait, 250));
  }
  throw new Error('原生 IPC 未就绪');
}
async function until(check, label) {
  const deadline = Date.now() + 30000;
  while (Date.now() < deadline) {
    try { const value = await check(); if (value) return value; } catch { /* Visible page may be refreshing. */ }
    await new Promise(resolveWait => setTimeout(resolveWait, 200));
  }
  throw new Error(`${label} 未在原生界面出现`);
}
async function clickUi(port, selector) {
  const result = await evaluate(port, `(()=>{const node=document.querySelector(${JSON.stringify(selector)});if(!node)return false;node.click();return true})()`);
  assert.equal(result, true, `找不到可点击控件 ${selector}`);
}
async function normalClose() {
  const pid=Number(launch.pid);
  const quote=value=>`'${String(value).replaceAll("'", "''")}'`;
  const info=JSON.parse(ps(['-Command',`$p=Get-CimInstance Win32_Process -Filter "ProcessId=${pid}";if(!$p -or [IO.Path]::GetFullPath([string]$p.ExecutablePath) -ne ${quote(runExecutable)} -or (Get-FileHash -LiteralPath ${quote(runExecutable)}).Hash -ne ${quote(receipt.sha256)}){throw 'PID identity mismatch'};$n=Get-Process -Id ${pid};@{title=$n.MainWindowTitle;responding=$n.Responding;accepted=$n.CloseMainWindow()}|ConvertTo-Json -Compress`]));
  assert.ok(info.title && info.responding && info.accepted);
  await until(()=>ps(['-Command',`@(Get-CimInstance Win32_Process -Filter "ProcessId=${pid}").Count`])==='0','自然关闭退出');
  await until(()=>ps(['-Command',`@(Get-CimInstance Win32_Process -Filter "Name='msedgewebview2.exe'" | Where-Object {$_.CommandLine -and $_.CommandLine.Contains(${quote(launch.webviewProfile)})}).Count`])==='0','本根WebView退出');
  pids.push(pid);launch=null;return info;
}

const result={candidateSha256:receipt.sha256,startupMode:'normal',realService:false,normalCloses:[]};let stage='empty';let activePort=null;
async function settingsInspect(port){
 await until(()=>evaluate(port,'Boolean(document.querySelector("[data-action=show-settings]"))'),'设置按钮投影');
 await clickUi(port,'[data-action="show-settings"]');
 await until(()=>evaluate(port,'Boolean(document.querySelector("[data-action=open-settings-page][data-page=development]"))'),'开发设置');
 await clickUi(port,'[data-action="open-settings-page"][data-page="development"]');
 await clickUi(port,'[data-action="show-p8-inspect"]');
 await until(()=>evaluate(port,'Boolean(document.querySelector(".p8-inspect-canvas"))'),'只读账本页面');
 return evaluate(port,"document.querySelector('.p8-inspect-canvas').textContent");
}
async function area(port,which){
 const current=await evaluate(port,'globalThis.__nanfengDesktopWorkState?.dataArea');
 if(current!==which) await clickUi(port,which==='WORK'?'[data-action="show-work"]':'[data-action="show-chat"]');
 await until(()=>evaluate(port,`globalThis.__nanfengDesktopWorkState?.dataArea===${JSON.stringify(which)}&&globalThis.__nanfengDesktopWorkState?.runtimeInfo?.dataAreasReady`),'区域');
}
function syntheticLedger(mode){
 const script=String.raw`import json,sqlite3,sys
from pathlib import Path
root=Path(sys.argv[1]);mode=sys.argv[2]
assert json.loads((root/'.nanfeng-windows-acceptance.json').read_text(encoding='utf-8'))['identifier']=='com.nanzhufeng.ai.desktop.windowsacceptance'
result={}
for area,relative,items in [('CHAT','p8-agent-ledger-v1/agent-ledger.sqlite3',[('synthetic-chat-a','FAILED','BUDGET_EXHAUSTED'),('synthetic-chat-b','CANCELLED',None)]),('WORK','work-area/p8-agent-ledger-v1/agent-ledger.sqlite3',[('synthetic-work-a','PAUSED',None)])]:
 p=root/relative;c=sqlite3.connect(p)
 if mode=='seed':
  assert c.execute('select count(*) from agent_runs').fetchone()[0]==0
  for rid,status,error in items:
   c.execute('INSERT INTO agent_runs VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)',(rid,rid+'-intent',1,status,'READ_ONLY','LOCAL_READ',2,2,0,0,0,0,error,'synthetic','synthetic'))
   for seq in [2,1]:
    c.execute('INSERT INTO agent_events VALUES (?,?,?,?,?,?)',(rid,seq,rid+'-event-'+str(seq),'PLAN_ACCEPTED' if seq==1 else status,'a'*64,'synthetic'))
    c.execute('INSERT INTO agent_checkpoints VALUES (?,?,?,?,?,?)',(rid,seq,status,0,'b'*64,'synthetic'))
  c.commit()
 result[area]={t:c.execute('SELECT * FROM '+t+' ORDER BY 1,2').fetchall() for t in ['agent_runs','agent_steps','agent_events','agent_checkpoints','agent_side_effect_receipts']}
 c.close()
print(json.dumps(result))`;
 return JSON.parse(execFileSync('python',['-c',script,root,mode],{encoding:'utf8',timeout:20000}));
}
try{
 let port=await start();activePort=port;result.root=root;await ready(port);
 await until(()=>evaluate(port,'globalThis.__nanfengDesktopWorkState?.runtimeInfo?.dataAreasReady && document.querySelector("[data-action=show-settings]")'),'正常投影');
 for(const which of ['CHAT','WORK']){await area(port,which);assert.deepEqual(await invoke(port,which,'inspect_p8_agent_runs'),[]);const text=await settingsInspect(port);assert.ok(text.includes('账本当前为空')&&text.includes('未连接模型与外部工具'));assert.equal(await evaluate(port,"document.querySelector('.p8-inspect-canvas [data-action]')!==null"),false);}
 result.normalCloses.push(await normalClose());stage='seed-safe-metadata';result.seed=syntheticLedger('seed');
 port=await start(true);activePort=port;await ready(port);await until(()=>evaluate(port,'globalThis.__nanfengDesktopWorkState?.runtimeInfo?.dataAreasReady'),'冷启投影');result.projections={};
 for(const which of ['CHAT','WORK']){await area(port,which);const runs=await invoke(port,which,'inspect_p8_agent_runs');result.projections[which]=runs;assert.deepEqual(runs.map(r=>r.id),which==='CHAT'?['synthetic-chat-a','synthetic-chat-b']:['synthetic-work-a']);for(const run of runs){assert.deepEqual(run.events.map(e=>e.sequence),[1,2]);assert.deepEqual(run.checkpoints.map(e=>e.sequence),[1,2]);assert.equal(run.usedSideEffects,0);assert.equal(run.maxSideEffects,0);assert.equal('inputHash' in run,false);}
 const text=await settingsInspect(port);assert.equal(text.includes('账本当前为空'),false);assert.ok(text.includes('0/0'));assert.ok(text.includes(which==='CHAT'?'BUDGET_EXHAUSTED':'PAUSED'));assert.equal(text.includes(which==='CHAT'?'synthetic-work':'synthetic-chat'),false);assert.equal(await evaluate(port,"document.querySelector('.p8-inspect-canvas [data-action]')!==null"),false);}
 assert.equal(requests.length,0);result.normalCloses.push(await normalClose());assert.deepEqual(syntheticLedger('read'),result.seed);
 stage='final-cold';port=await start(true);activePort=port;await ready(port);await until(()=>evaluate(port,'globalThis.__nanfengDesktopWorkState?.runtimeInfo?.dataAreasReady'),'再次冷启');
 for(const which of ['CHAT','WORK']){await area(port,which);assert.deepEqual(await invoke(port,which,'inspect_p8_agent_runs'),result.projections[which]);await settingsInspect(port);}
 assert.equal(requests.length,0);result.normalCloses.push(await normalClose());assert.deepEqual(syntheticLedger('read'),result.seed);result.pass=true;
}catch(error){result.error=String(error.stack||error);result.stage=stage;if(launch&&activePort)result.failureUi=await evaluate(activePort,"({area:globalThis.__nanfengDesktopWorkState?.dataArea,pane:globalThis.__nanfengDesktopWorkState?.pane,error:globalThis.__nanfengDesktopWorkState?.error,actions:[...document.querySelectorAll('[data-action]')].filter(n=>n.dataset.action.includes('area')).map(n=>({action:n.dataset.action,...n.dataset}))})").catch(e=>String(e));process.exitCode=1;}
finally{if(launch){result.forcedCleanup=true;stop();}provider.closeAllConnections();await new Promise(done=>provider.close(done));result.pids=pids;if(root)await writeFile(join(root,'agent-inspect-current.json'),JSON.stringify(result,null,2),{flag:'wx'});}
console.log(JSON.stringify({root,candidateSha256:receipt.sha256,pass:result.pass,error:result.error,stage:result.stage,pids}));
