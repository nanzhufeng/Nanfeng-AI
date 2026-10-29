import assert from 'node:assert/strict';
import { createHash } from 'node:crypto';
import { execFileSync, spawn } from 'node:child_process';
import { readFile, writeFile, unlink } from 'node:fs/promises';
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
let holdToken=false;
let invalidIdentity=false;
const provider=http.createServer(async(request,response)=>{
 const chunks=[];for await(const chunk of request)chunks.push(chunk);
 const path=request.url?.split('?')[0];requests.push({path});
 if(path==='/auth/v1/token'){
  const body=JSON.parse(Buffer.concat(chunks).toString('utf8'));
  assert.equal(body.auth_code,'nfai-synthetic-windows-account-code');
  response.writeHead(200,{'content-type':'application/json'});
  const send=()=>{response.end(JSON.stringify({access_token:invalidIdentity?'synthetic-rejected-access':'synthetic-original-access',refresh_token:'synthetic-refresh',expires_in:3600,user:{id:'00000000-0000-4000-8000-000000000071',email:invalidIdentity?'updated@example.invalid':'identity-owner@example.invalid',user_metadata:{full_name:invalidIdentity?'合成更新身份':'合成身份校验'}}}));}; if(holdToken){held=send;}else{send();}
 }else{response.writeHead(200,{'content-type':'application/json'});response.end(JSON.stringify({documentCount:0,documents:[]}));}
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
    '-AccountSyncTestEndpoint', `http://127.0.0.1:${providerPort}`];
  if (reopen) args.push('-Root', root);
  const result = await new Promise((done, fail) => {
    const child = spawn('pwsh', args, { stdio: ['ignore', 'pipe', 'pipe'] });
    let output = '';
    let error = '';
    const timer = setTimeout(() => { child.kill(); fail(new Error(`启动超时：${error}`)); }, 30000);
    child.stdout.on('data', chunk => {
      output += chunk.toString();
      const line = output.split(/\r?\n/).slice(0,-1).find(value => value.trim().startsWith('{') && value.includes('"pid"'));
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


const click=(port,action)=>evaluate(port,`(()=>{const b=document.createElement('button');b.dataset.action=${JSON.stringify(action)};document.querySelector('#app').append(b);b.click();return true;})()`);
try {
 for(const action of ['cancel-google-account-sign-in','sign-out-google-account']) {
  root='';held=null;holdToken=false;invalidIdentity=false;let port=await start();
  await until(()=>evaluate(port,'Boolean(window.__TAURI__?.core?.invoke && globalThis.__nanfengDesktopWorkState?.runtimeInfo?.dataAreasReady)'),'原生就绪');
  const first=await invoke(port,'sign_in_desktop_google_account');assert.equal(first.email,'identity-owner@example.invalid');
  const file=join(root,'account-sync-credentials-v2','credentials.json');const originalHash=createHash('sha256').update(await readFile(file)).digest('hex');
  invalidIdentity=true;holdToken=true;await evaluate(port,"globalThis.__lateLoginResult=null;window.__TAURI__.core.invoke('sign_in_desktop_google_account',{}, {headers:{'x-nanfeng-data-area':'CHAT'}}).then(()=>{globalThis.__lateLoginResult={ok:true}},error=>{globalThis.__lateLoginResult={ok:false,error:String(error)}});true");await until(async()=>held,'token交换已到达');
  await invoke(port,action==='sign-out-google-account'?'sign_out_desktop_google_account':'cancel_desktop_google_account_sign_in');
  held();held=null;holdToken=false;
  const late=await until(()=>evaluate(port,'globalThis.__lateLoginResult'),'迟到登录实际拒绝');assert.equal(late.ok,false);assert.match(late.error,/迟到授权未写入/);
  const after=await invoke(port,'read_desktop_account_sync');
  const expected=action==='sign-out-google-account'?null:first.email;assert.equal(after.email,expected);
  if(action==='cancel-google-account-sign-in')assert.equal(createHash('sha256').update(await readFile(file)).digest('hex'),originalHash);
  const feedback=await evaluate(port,'({status:globalThis.__nanfengDesktopWorkState.status,error:globalThis.__nanfengDesktopWorkState.error})');
  assert.doesNotMatch(feedback.status,/已建立南枫云/);assert.equal(feedback.error,'');
  console.log(JSON.stringify({phase:action,candidateSha256:receipt.sha256,root,email:after.email,feedback,late,layer:'PACKAGED_EXE_NATIVE_IPC_AWAITED_LATE_LOGIN_AFTER_CANCEL_OR_SIGNOUT_NOT_REAL_GOOGLE'}));
  stop();port=await start(true);await until(()=>evaluate(port,'Boolean(window.__TAURI__?.core?.invoke && globalThis.__nanfengDesktopWorkState?.runtimeInfo?.dataAreasReady)'),'同根冷启');
  const cold=await invoke(port,'read_desktop_account_sync');assert.equal(cold.email,expected);
  console.log(JSON.stringify({phase:'cold-'+action,candidateSha256:receipt.sha256,root,email:cold.email}));stop();
 }
}finally{if(held)held();stop();provider.closeAllConnections();await new Promise(done=>provider.close(done));}
