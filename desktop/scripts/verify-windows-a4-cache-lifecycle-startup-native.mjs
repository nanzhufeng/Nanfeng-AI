import assert from 'node:assert/strict';
import { createHash, randomUUID } from 'node:crypto';
import { execFileSync, spawn } from 'node:child_process';
import { readFile, writeFile } from 'node:fs/promises';
import http from 'node:http';
import { dirname, join, resolve, sep } from 'node:path';
import { fileURLToPath } from 'node:url';

if (process.platform !== 'win32') throw new Error('仅在 Windows 隔离候选上验收。');
const desktopRoot = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const rootParent = resolve(process.argv[2] || 'D:\\CodexTemp');
assert.ok(process.execArgv.includes('--max-old-space-size=2048'));
const screenshotMode = process.argv.includes('--screenshot');
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
const unknownSecond=process.argv.includes('--unknown-second');
const requests=[];const serverErrors=[];const wireContents=[];
const provider=http.createServer(async(req,res)=>{
 try {
  let raw='';for await(const chunk of req)raw+=chunk.toString('utf8');const body=JSON.parse(raw);wireContents.push(JSON.stringify(body));
  assert.equal(req.headers.authorization==='Bearer local-acceptance-mock-only',true);
  const retrieval=body.model==='openai/gpt-6-luna' && Array.isArray(body.tools);
  requests.push({model:body.model,role:retrieval?'RETRIEVAL':'ANSWER',idempotencyKey:req.headers['idempotency-key'],grounded:JSON.stringify(body.messages||[]).includes('<retrieved-evidence>')});
  res.writeHead(200,{'Content-Type':'text/event-stream; charset=utf-8'});const event=value=>res.write(`data: ${JSON.stringify(value)}\n\n`);
  if(retrieval) {
   assert.equal(body.tool_choice,'required');assert.equal(body.provider.allow_fallbacks,false);
   const ids=[...new Set([...JSON.stringify(body.messages).matchAll(/\[(A[1-9][0-9]*)\] /g)].map(m=>m[1]))];
   assert.ok(ids.length>0 && ids.length<=4);requests[requests.length-1].facts=ids;
   if(unknownSecond && requests.filter(r=>r.role==='RETRIEVAL').length===2){res.destroy();return;}
   const rows=ids.map(id=>`[${id}] 已核实\n公开机构在2026年发布该项数据。\n来源：https://example.test/${id}`).join('\n');
   event({model:body.model,choices:[{delta:{content:rows}}]});
   res.end(`data: ${JSON.stringify({model:body.model,choices:[{delta:{annotations:ids.map(id=>({type:'url_citation',url_citation:{url:`https://example.test/${id}`,title:'Synthetic evidence'}}))},finish_reason:'stop'}],usage:{prompt_tokens:5,completion_tokens:2,cost:0.000004}})}`);
  }else if(body.output_config) {
   assert.equal(req.headers['x-api-key']==='local-acceptance-mock-only',true);assert.equal(req.headers['anthropic-version'],'2023-06-01');
   event({type:'message_start',message:{model:body.model,usage:{input_tokens:5,cache_read_input_tokens:1}}});
   event({type:'content_block_delta',delta:{type:'text_delta',text:'合成原模型回答'}});
   event({type:'message_delta',delta:{stop_reason:'end_turn'},usage:{output_tokens:2}});res.end('data: {"type":"message_stop"}');
  }else {
   if(body.model.startsWith('gemini-'))assert.equal(body.max_tokens,undefined);
   event({model:body.model,choices:[{delta:{content:'合成原模型回答'}}]});
   res.end(`data: ${JSON.stringify({model:body.model,choices:[{delta:{},finish_reason:'stop'}],usage:{prompt_tokens:5,completion_tokens:2,cost:0.000004}})}`);
  }
 }catch(error){serverErrors.push(String(error));res.destroy(error);}
});
await new Promise(resolveReady => provider.listen(0, '127.0.0.1', resolveReady));
const providerPort = provider.address().port;
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
  const args = ['-File', join(desktopRoot, 'scripts/start-windows-acceptance.ps1'), '-RootParent', rootParent,
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
      const timer = setTimeout(() => reject(new Error('CDP evaluation timed out')), 30000);
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
  const clicked = await evaluate(port, `(()=>{const nodes=[...document.querySelectorAll(${JSON.stringify(selector)})].filter(n=>n.getClientRects().length && !n.disabled);if(nodes.length!==1)return {count:nodes.length};nodes[0].click();return {count:1}})()`);
  assert.equal(clicked?.count, 1, `控件必须可见、唯一且可用: ${selector}`);
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





const result={candidateSha256:receipt.sha256,realService:false};let port=0;
const sql=code=>execFileSync('python',['-c',code,root],{encoding:'utf8',timeout:20000});
try{
 port=await start();result.root=root;await ready(port);await invoke(port,'CHAT','ensure_desktop_local_workspace');result.initialClose=await normalClose();
 result.before=JSON.parse(sql(`import sqlite3,sys,json,hashlib,shutil
from pathlib import Path
root=Path(sys.argv[1]);old=Path('D:/CodexTemp/nanfeng-ai-windows-acceptance.9d21d031-ce86-47be-aa7c-cde0a76fee0c')
for p in [root,old]:assert p.parent==Path('D:/CodexTemp') and json.loads((p/'.nanfeng-windows-acceptance.json').read_text())['identifier']=='com.nanzhufeng.ai.desktop.windowsacceptance'
for name in ['workspace.sqlite3','usage-ledger/usage-ledger-v1.sqlite3']:
 source=sqlite3.connect('file:'+str(old/name)+'?mode=ro',uri=True);current=sqlite3.connect(root/name);saved=sqlite3.connect(root/('before-orphan-upgrade-'+name.replace('/','-')));current.backup(saved);saved.close();source.backup(current);current.close();source.close()
for p in (old/'assets').iterdir():
 if p.is_file():
  assert hashlib.sha256(p.read_bytes()).hexdigest()==p.name;dest=root/'assets'/p.name
  if dest.exists():assert dest.read_bytes()==p.read_bytes()
  else:shutil.copy2(p,dest)
c=sqlite3.connect('file:'+str(root/'workspace.sqlite3')+'?mode=ro',uri=True);l=sqlite3.connect('file:'+str(root/'usage-ledger/usage-ledger-v1.sqlite3')+'?mode=ro',uri=True)
print(json.dumps({'cache':c.execute('SELECT count(*) FROM desktop_attachment_search_checkpoints').fetchone()[0],'coverage':c.execute('SELECT count(*) FROM desktop_attachment_search_coverage').fetchone()[0],'exchange':hashlib.sha256(json.dumps(c.execute('SELECT * FROM workspace_exchange ORDER BY workspace_id').fetchall()).encode()).hexdigest(),'ledger':hashlib.sha256(json.dumps(l.execute('SELECT * FROM usage_ledger_entries ORDER BY entry_id').fetchall()).encode()).hexdigest(),'attempts':c.execute('SELECT count(*) FROM desktop_ordinary_chat_attempts').fetchone()[0]}))`));
 assert.equal(result.before.cache,8);assert.equal(result.before.coverage,1);
 port=await start(true);await ready(port);await invoke(port,'CHAT','read_desktop_p6g_global_default');
 result.after=JSON.parse(sql(`import sqlite3,sys,json,hashlib
from pathlib import Path
root=Path(sys.argv[1]);c=sqlite3.connect('file:'+str(root/'workspace.sqlite3')+'?mode=ro',uri=True);l=sqlite3.connect('file:'+str(root/'usage-ledger/usage-ledger-v1.sqlite3')+'?mode=ro',uri=True)
print(json.dumps({'cache':c.execute('SELECT count(*) FROM desktop_attachment_search_checkpoints').fetchone()[0],'coverage':c.execute('SELECT count(*) FROM desktop_attachment_search_coverage').fetchone()[0],'exchange':hashlib.sha256(json.dumps(c.execute('SELECT * FROM workspace_exchange ORDER BY workspace_id').fetchall()).encode()).hexdigest(),'ledger':hashlib.sha256(json.dumps(l.execute('SELECT * FROM usage_ledger_entries ORDER BY entry_id').fetchall()).encode()).hexdigest(),'attempts':c.execute('SELECT count(*) FROM desktop_ordinary_chat_attempts').fetchone()[0]}))`));
 assert.equal(result.after.cache,0);assert.equal(result.after.coverage,0);assert.equal(result.after.exchange,result.before.exchange);assert.equal(result.after.ledger,result.before.ledger);assert.equal(result.after.attempts,result.before.attempts);assert.equal(requests.length,0);result.close=await normalClose();result.pass=true;
}catch(error){result.pass=false;result.error=String(error);process.exitCode=1;}
finally{if(launch){result.forcedCleanup=true;stop();}provider.closeAllConnections();await new Promise(done=>provider.close(done));result.requests=requests;result.pids=pids;if(root)await writeFile(join(root,'cache-lifecycle-startup-native-evidence.json'),JSON.stringify(result,null,2),{flag:'wx'});}
console.log(JSON.stringify({root,pass:result.pass,error:result.error,pids}));
