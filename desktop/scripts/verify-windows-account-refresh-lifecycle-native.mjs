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
let held=null;let holdRefresh=false;
let invalidIdentity=false;
const provider=http.createServer(async(request,response)=>{
 const chunks=[];for await(const chunk of request)chunks.push(chunk);
 const path=request.url?.split('?')[0];requests.push({path});
 if(path==='/auth/v1/token'){
  const body=JSON.parse(Buffer.concat(chunks).toString('utf8'));
  if(body.refresh_token){assert.equal(body.refresh_token,'synthetic-refresh')}else{assert.equal(body.auth_code,'nfai-synthetic-windows-account-code')}
  response.writeHead(200,{'content-type':'application/json'});
  const send=()=>{response.end(JSON.stringify({access_token:invalidIdentity?'synthetic-rejected-access':'synthetic-original-access',refresh_token:'synthetic-refresh',expires_in:3600,user:{id:'00000000-0000-4000-8000-000000000071',email:invalidIdentity?'updated@example.invalid':'identity-owner@example.invalid',user_metadata:{full_name:invalidIdentity?'合成更新身份':'合成身份校验'}}}));};if(body.refresh_token && holdRefresh){held=send}else{send()}
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


try {
 let port=await start();await until(()=>evaluate(port,'Boolean(window.__TAURI__?.core?.invoke && globalThis.__nanfengDesktopWorkState?.runtimeInfo?.dataAreasReady)'),'原生就绪');
 await invoke(port,'sign_in_desktop_google_account');stop();
 ps(`$r=${quote(root)};if((Split-Path $r -Parent) -ne 'D:\\CodexTemp'){throw 'root'};$marker=Get-Content -LiteralPath (Join-Path $r '.nanfeng-windows-acceptance.json') -Raw|ConvertFrom-Json;if($marker.identifier -ne ${quote(receipt.identifier)}){throw 'marker'};$dir=Join-Path $r 'account-sync-credentials-v2';$kb=[IO.File]::ReadAllBytes((Join-Path $dir 'local.key'));$magic=[Text.Encoding]::UTF8.GetBytes("NFAI-DPAPI-KEY-V1"+[char]0);$key=[Security.Cryptography.ProtectedData]::Unprotect($kb[$magic.Length..($kb.Length-1)],[Text.Encoding]::UTF8.GetBytes('com.nanzhufeng.ai.desktop.private-credentials.v1'),[Security.Cryptography.DataProtectionScope]::CurrentUser);$f=Join-Path $dir 'credentials.json';$doc=Get-Content -LiteralPath $f -Raw|ConvertFrom-Json;$id='com.nanzhufeng.ai.desktop.google-session.v1:active';$record=$doc.records.$id;$bytes=[Convert]::FromBase64String($record.ciphertext);$plain=[byte[]]::new($bytes.Length-16);$aes=[Security.Cryptography.AesGcm]::new($key,16);$aes.Decrypt([Convert]::FromBase64String($record.nonce),$bytes[0..($bytes.Length-17)],$bytes[($bytes.Length-16)..($bytes.Length-1)],$plain);$session=[Text.Encoding]::UTF8.GetString($plain)|ConvertFrom-Json;$session.expiresAtEpochSeconds=0;$secret=[Text.Encoding]::UTF8.GetBytes(($session|ConvertTo-Json -Compress));$nonce=[Security.Cryptography.RandomNumberGenerator]::GetBytes(12);$cipher=[byte[]]::new($secret.Length);$tag=[byte[]]::new(16);$aes.Encrypt($nonce,$secret,$cipher,$tag);$aes.Dispose();$record.nonce=[Convert]::ToBase64String($nonce);$record.ciphertext=[Convert]::ToBase64String([byte[]]($cipher+$tag));[IO.File]::WriteAllText($f,($doc|ConvertTo-Json -Depth 8),[Text.UTF8Encoding]::new($false));[Array]::Clear($key);[Array]::Clear($secret);[Array]::Clear($plain)`);
 port=await start(true);await until(()=>evaluate(port,'Boolean(window.__TAURI__?.core?.invoke && globalThis.__nanfengDesktopWorkState?.runtimeInfo?.dataAreasReady)'),'过期会话冷启');
 await invoke(port,'read_desktop_account_sync');assert.equal(requests.filter(x=>x.path==='/auth/v1/token').length,1,'本机账号回读不得刷新');
 holdRefresh=true;invalidIdentity=true;
 await evaluate(port,"globalThis.__refreshResult=null;window.__TAURI__.core.invoke('restore_all_desktop_cloud_conversations',{}, {headers:{'x-nanfeng-data-area':'CHAT'}}).then(()=>{globalThis.__refreshResult={ok:true}},error=>{globalThis.__refreshResult={ok:false,error:String(error)}});true");
 await until(async()=>held,'真实refresh请求到达');await invoke(port,'sign_out_desktop_google_account');held();held=null;
 const late=await until(()=>evaluate(port,'globalThis.__refreshResult'),'刷新实际迟到回执');assert.equal(late.ok,false);assert.match(late.error,/迟到会话刷新未写入/);
 const account=await invoke(port,'read_desktop_account_sync');assert.equal(account.email,null);
 assert.equal(requests.filter(x=>x.path.includes('/rpc/')).length,0,'拒绝刷新后不得继续云端读取');
 console.log(JSON.stringify({phase:'late-refresh-after-signout',candidateSha256:receipt.sha256,root,late,email:account.email,authRequests:requests.filter(x=>x.path==='/auth/v1/token').length,cloudRequests:0,layer:'PACKAGED_EXE_EXPIRED_SYNTHETIC_DPAPI_SESSION_REAL_REFRESH_OWNER_LATE_REPLY_NOT_REAL_GOOGLE'}));
 stop();port=await start(true);await until(()=>evaluate(port,'Boolean(window.__TAURI__?.core?.invoke && globalThis.__nanfengDesktopWorkState?.runtimeInfo?.dataAreasReady)'),'退出后冷启');assert.equal((await invoke(port,'read_desktop_account_sync')).email,null);
 console.log(JSON.stringify({phase:'late-refresh-cold',candidateSha256:receipt.sha256,root,email:null}));
}finally{if(held)held();stop();provider.closeAllConnections();await new Promise(done=>provider.close(done));}
