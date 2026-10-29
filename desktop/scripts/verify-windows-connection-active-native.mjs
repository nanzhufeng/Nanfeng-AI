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
const provider = http.createServer(async (request, response) => {
 const chunks=[]; for await(const chunk of request) chunks.push(chunk);
 const body=JSON.parse(Buffer.concat(chunks).toString('utf8'));
 assert.equal(request.url, '/chat/completions');
 assert.equal(request.headers.authorization, 'Bearer nfai-synthetic-connection-only');
 assert.equal(body.stream,false); assert.equal(body.max_tokens,1);
 requests.push({model:body.model}); held=response;
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
    '-ModelServiceTestEndpoint', `http://127.0.0.1:${providerPort}`];
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
 const port=await start();
 await until(()=>evaluate(port,'Boolean(window.__TAURI__?.core?.invoke && globalThis.__nanfengDesktopWorkState?.runtimeInfo?.dataAreasReady)'),'原生就绪');
 await invoke(port,'save_desktop_model_service_settings',{args:{providerId:'DEEPSEEK',presetId:'DEEPSEEK_V4_FLASH',enabled:true,expectedRevision:0,apiKey:'nfai-synthetic-connection-only'}});
 await evaluate(port, `(()=>{window.__nfConnection=window.__TAURI__.core.invoke('test_desktop_model_service_connection',{args:{providerId:'DEEPSEEK',presetId:'DEEPSEEK_V4_FLASH'}},{headers:{'x-nanfeng-data-area':'CHAT'}}).then(value=>({ok:true,value}),error=>({ok:false,error:String(error)}));return true})()`);
 await until(()=>held,'暂停连接测试 HTTP');
 const previews=[];
 for(const area of ['CHAT','WORK']) {
  const preview=await evaluate(port,`window.__TAURI__.core.invoke('preview_desktop_privacy_deletion',{args:{scope:'ALL_LOCAL_BUSINESS_DATA'}},{headers:{'x-nanfeng-data-area':${JSON.stringify(area)}}}).catch(error=>({__error:String(error)}))`);
  previews.push({area,rejected:/运行中的任务/.test(preview?.__error||'')});
 }
 if(process.argv.includes('--close')) {
 const pid=Number(launch.pid);
      const closed = JSON.parse(ps(`
        $p=Get-CimInstance Win32_Process -Filter "ProcessId=${pid}"
        if(!$p -or [IO.Path]::GetFullPath([string]$p.ExecutablePath) -ne ${quote(running)} -or (Get-FileHash -LiteralPath ${quote(running)}).Hash -ne ${quote(receipt.sha256)}){throw 'PID identity mismatch'}
        Add-Type 'using System; using System.Runtime.InteropServices; public static class NfOcrWindow { [DllImport("user32.dll")] public static extern bool PostMessage(IntPtr hwnd, uint message, IntPtr wparam, IntPtr lparam); }'
        Add-Type -AssemblyName UIAutomationClient
        Add-Type -AssemblyName UIAutomationTypes
        $condition=[System.Windows.Automation.PropertyCondition]::new([System.Windows.Automation.AutomationElement]::ProcessIdProperty,${pid})
        $windows=[System.Windows.Automation.AutomationElement]::RootElement.FindAll([System.Windows.Automation.TreeScope]::Children,$condition)
        $main=@($windows | Where-Object {$_.Current.Name -eq '南枫 AI · 隔离测试（非日常版）' -and $_.Current.ClassName -eq 'Tauri Window'})
        if($main.Count -ne 1){throw 'exact acceptance window unavailable'}
        $hwnd=[IntPtr]$main[0].Current.NativeWindowHandle
        @{hwnd=$hwnd.ToInt64();title=$main[0].Current.Name;closePosted=[NfOcrWindow]::PostMessage($hwnd,0x0010,[IntPtr]::Zero,[IntPtr]::Zero)} | ConvertTo-Json -Compress
      `));
      assert.equal(closed.closePosted, true);
      await new Promise(done => setTimeout(done, 2000));
      assert.equal(ps(`[bool](Get-Process -Id ${pid} -ErrorAction SilentlyContinue)`), 'True', '连接请求结束前进程不得退出');

 }
 held.writeHead(200,{'content-type':'application/json'}); held.end('{}'); held=null;
 if(process.argv.includes('--close')) {
  const pid=Number(launch.pid);
  await until(()=>ps(`[bool](Get-Process -Id ${pid} -ErrorAction SilentlyContinue)`)==='False','连接返回后自然退出');
  stoppedPids.push(pid); launch=null;
  const coldPort=await start(true);
  await until(()=>evaluate(coldPort,'Boolean(window.__TAURI__?.core?.invoke && globalThis.__nanfengDesktopWorkState?.runtimeInfo?.dataAreasReady)'), '冷启原生就绪');
  await until(async()=>{
   const preview=await evaluate(coldPort,`window.__TAURI__.core.invoke('preview_desktop_privacy_deletion',{args:{scope:'ALL_LOCAL_BUSINESS_DATA'}}).catch(error=>({__error:String(error)}))`);
   if(/运行中的任务/.test(preview?.__error||'')) return false;
   assert.ok(!preview?.__error,preview?.__error);
   return preview?.fingerprint;
  },'冷启启动读取结束后预览恢复');
 } else {
  assert.equal((await evaluate(port,'window.__nfConnection')).ok,true);
  assert.ok((await invoke(port,'preview_desktop_privacy_deletion',{args:{scope:'ALL_LOCAL_BUSINESS_DATA'}})).fingerprint);
 }
 console.log(JSON.stringify({candidateSha256:receipt.sha256,root,previews,completedPreviewAllowed:true,requestCount:requests.length,normalWindowCloseDrained:process.argv.includes('--close'),coldPreview:process.argv.includes('--close'),deletionExecuted:false}));
 for(const preview of previews) assert.equal(preview.rejected,true,`${preview.area} preview admitted held connection test`);
} finally {
 if(held){held.destroy();held=null;} stop(); provider.closeAllConnections(); await new Promise(done=>provider.close(done));
}
