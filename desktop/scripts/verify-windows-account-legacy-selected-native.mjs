import assert from 'node:assert/strict';
import { createHash, createCipheriv } from 'node:crypto';
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
let invalidIdentity=false;
const canonical=value=>Array.isArray(value)?`[${value.map(canonical).join(',')}]`:value&&typeof value==='object'?`{${Object.keys(value).sort().map(key=>`${JSON.stringify(key)}:${canonical(value[key])}`).join(',')}}`:JSON.stringify(value);
const fixture=JSON.parse(await readFile(new URL('../../protocol/fixtures/data-area-sync-v1.json',import.meta.url),'utf8')).find(item=>item.platform==='DESKTOP'&&item.area==='CHAT');
const payload={format:'nfai.sync.payload',protocolVersion:1,schemaVersion:1,appId:'com.nanzhufeng.ai',documentId:fixture.documentId,revision:1,records:[{kind:'conversation',id:fixture.conversationId,revision:1,classification:'NORMAL',content:fixture.content}]};
const payloadBytes=Buffer.from(canonical(payload));
let envelope={format:'nfai.sync.direct',protocolVersion:1,schemaVersion:1,appId:payload.appId,documentId:payload.documentId,revision:1,payloadHash:createHash('sha256').update(payloadBytes).digest('hex'),payloadByteCount:payloadBytes.length,payload};
const provider=http.createServer(async(request,response)=>{
 const chunks=[];for await(const chunk of request)chunks.push(chunk);
 const path=request.url?.split('?')[0];requests.push({path});
 if(path==='/auth/v1/token'){
  const body=JSON.parse(Buffer.concat(chunks).toString('utf8'));
  assert.equal(body.auth_code,'nfai-synthetic-windows-account-code');
  response.writeHead(200,{'content-type':'application/json'});
  response.end(JSON.stringify({access_token:invalidIdentity?'synthetic-rejected-access':'synthetic-original-access',refresh_token:'synthetic-refresh',expires_in:3600,user:{id:invalidIdentity?'invalid/user':'00000000-0000-4000-8000-000000000071',email:'identity-owner@example.invalid',user_metadata:{full_name:'合成身份校验'}}}));
 }else if(path==='/rest/v1/rpc/nanfeng_sync_read_document'){
 response.writeHead(200,{'content-type':'application/json'});response.end(JSON.stringify([{envelope}]));
 }else if(path==='/rest/v1/rpc/nanfeng_sync_commit_document'){
 const body=JSON.parse(Buffer.concat(chunks).toString('utf8'));assert.equal(body.p_expected_revision,2);assert.equal(body.p_envelope.format,'nfai.sync.direct');envelope=body.p_envelope;response.writeHead(200,{'content-type':'application/json'});response.end(JSON.stringify([{revision:envelope.revision,payload_hash:envelope.payloadHash}]));
 }else{response.writeHead(200,{'content-type':'application/json'});response.end(JSON.stringify({documentCount:1,documents:[{envelope}]}));}
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

try {
 let port=await start();
 await until(()=>evaluate(port,'Boolean(window.__TAURI__?.core?.invoke && globalThis.__nanfengDesktopWorkState?.runtimeInfo?.dataAreasReady)'),'原生就绪');
 await invoke(port,'sign_in_desktop_google_account');
 const list=await invoke(port,'restore_all_desktop_cloud_conversations');assert.equal(list.restored.length,1);const restored=list.restored[0];
 const wrappingKey=Buffer.alloc(32,7),dataKey=Buffer.alloc(32,3),salt=Buffer.alloc(16,5);
 const oldPayload=structuredClone(payload);oldPayload.revision=2;
 oldPayload.records[0].content.messages.push({id:'remote-legacy-m2',parentId:'m1',ordinal:1,role:'user',delivery:'COMPLETE',createdAt:'1970-01-01T00:00:02Z',revision:1,blocks:[{kind:'TEXT',text:'合成旧密文远端追加消息'}]});
 oldPayload.records[0].content.currentLeafId='remote-legacy-m2';oldPayload.records[0].content.updatedAt='1970-01-01T00:00:02Z';
 const plain=Buffer.from(canonical(oldPayload));
 const aadFields={format:'nfai.sync.envelope',protocolVersion:1,schemaVersion:1,appId:payload.appId,documentId:payload.documentId,revision:2,payloadHash:createHash('sha256').update(plain).digest('hex')};
 const aad=Buffer.from(canonical(aadFields));
 const encrypt=(key,nonce,bytes)=>{const c=createCipheriv('aes-256-gcm',key,nonce);c.setAAD(aad);return Buffer.concat([c.update(bytes),c.final(),c.getAuthTag()]).toString('base64url')};
 const wrapNonce=Buffer.alloc(12,6),payloadNonce=Buffer.alloc(12,8);
 envelope={...aadFields,payloadByteCount:plain.length,kdf:{algorithm:'PBKDF2-HMAC-SHA256',version:1,iterations:210000,salt:salt.toString('base64url')},wrappedDataKey:{algorithm:'AES-256-GCM',nonce:wrapNonce.toString('base64url'),ciphertext:encrypt(wrappingKey,wrapNonce,dataKey)},payload:{algorithm:'AES-256-GCM',nonce:payloadNonce.toString('base64url'),ciphertext:encrypt(dataKey,payloadNonce,plain)}};
 stop();
 const account=execFileSync('python',['-c',"import sqlite3,sys;c=sqlite3.connect(sys.argv[1]);a=c.execute('SELECT account_ref FROM desktop_cloud_account_state').fetchone()[0];c.execute('UPDATE desktop_cloud_account_state SET recovery_confirmed=1,recovery_salt_b64=?',(sys.argv[2],));c.commit();c.close();print(a)",join(root,'workspace.sqlite3'),salt.toString('base64url')],{encoding:'utf8'}).trim();
 assert.equal(account.length,32);
 ps(`$r=${quote(root)};if((Split-Path $r -Parent) -ne 'D:\\CodexTemp'){throw 'root'};$marker=Get-Content -LiteralPath (Join-Path $r '.nanfeng-windows-acceptance.json') -Raw|ConvertFrom-Json;if($marker.identifier -ne ${quote(receipt.identifier)}){throw 'marker'};$dir=Join-Path $r 'account-sync-credentials-v2';$keyBytes=[IO.File]::ReadAllBytes((Join-Path $dir 'local.key'));$magic=[Text.Encoding]::UTF8.GetBytes("NFAI-DPAPI-KEY-V1"+[char]0);$key=[Security.Cryptography.ProtectedData]::Unprotect($keyBytes[$magic.Length..($keyBytes.Length-1)],[Text.Encoding]::UTF8.GetBytes('com.nanzhufeng.ai.desktop.private-credentials.v1'),[Security.Cryptography.DataProtectionScope]::CurrentUser);$nonce=[byte[]](1..12);$secret=[Convert]::FromBase64String(${quote(wrappingKey.toString('base64'))});$cipher=[byte[]]::new(32);$tag=[byte[]]::new(16);$aes=[Security.Cryptography.AesGcm]::new($key,16);$aes.Encrypt($nonce,$secret,$cipher,$tag);$aes.Dispose();$f=Join-Path $dir 'credentials.json';$doc=Get-Content -LiteralPath $f -Raw|ConvertFrom-Json;$doc.records|Add-Member -NotePropertyName ${quote('com.nanzhufeng.ai.desktop.recovery-wrap.v1:'+account)} -NotePropertyValue @{nonce=[Convert]::ToBase64String($nonce);ciphertext=[Convert]::ToBase64String([byte[]]($cipher+$tag))};[IO.File]::WriteAllText($f,($doc|ConvertTo-Json -Depth 8),[Text.UTF8Encoding]::new($false));[Array]::Clear($key);[Array]::Clear($secret)`);
 port=await start(true);await until(()=>evaluate(port,'Boolean(window.__TAURI__?.core?.invoke && globalThis.__nanfengDesktopWorkState?.runtimeInfo?.dataAreasReady)'),'旧材料同根冷启');
 const result=await invoke(port,'sync_selected_desktop_conversation',{workspaceId:restored.workspaceId,conversationId:restored.conversationId,continuation:true});
 const commits=requests.filter(x=>x.path==='/rest/v1/rpc/nanfeng_sync_commit_document').length;
 console.log(JSON.stringify({phase:'legacy-selected-union',candidateSha256:receipt.sha256,root,result,commits,remoteFormat:envelope.format,remoteRevision:envelope.revision,messageIds:envelope.payload.records.find(x=>x.kind==='conversation').content.messages.map(x=>x.id),layer:'PACKAGED_EXE_EXISTING_SYNTHETIC_PRIVATE_LEGACY_MATERIAL_SELECTED_UNION_NOT_REAL_CLOUD'}));
 assert.equal(commits,1);assert.equal(envelope.format,'nfai.sync.direct');assert.equal(envelope.revision,3);assert.equal(envelope.payload.records.find(x=>x.kind==='conversation').content.messages.length,2);
 stop();port=await start(true);await until(()=>evaluate(port,'Boolean(window.__TAURI__?.core?.invoke && globalThis.__nanfengDesktopWorkState?.runtimeInfo?.dataAreasReady)'),'迁移后冷启');
 const cold=await invoke(port,'read_desktop_account_sync');assert.equal(cold.email,'identity-owner@example.invalid');
 console.log(JSON.stringify({phase:'legacy-selected-cold',candidateSha256:receipt.sha256,root,email:cold.email,commits:requests.filter(x=>x.path==='/rest/v1/rpc/nanfeng_sync_commit_document').length}));
} finally {stop();provider.closeAllConnections();await new Promise(done=>provider.close(done));}
