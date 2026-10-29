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
const autoAttachmentKind = undefined;
const autoRetryOnly = autoAttachmentKind === 'auto-retry';
const qwenAttachmentRetry = autoAttachmentKind === 'qwen-attachment-retry-ui';
const autoRetryUi = autoAttachmentKind === 'auto-retry-ui' || qwenAttachmentRetry;
const autoRoutePreview = autoAttachmentKind === 'auto-route-preview';
const autoAttachmentOnly = ['auto-attachment', 'auto-pdf', 'auto-image', 'auto-mixed'].includes(autoAttachmentKind);
const receipt = JSON.parse(await readFile(join(desktopRoot, 'output/windows-acceptance/current.json'), 'utf8'));
assert.equal(receipt.identifier, 'com.nanzhufeng.ai.desktop.windowsacceptance');
const source = resolve(desktopRoot, 'output/windows-acceptance', receipt.sha256, 'NanfengAI-Windows-Acceptance.exe');
assert.equal(resolve(receipt.executable), source);
assert.equal(createHash('sha256').update(await readFile(source)).digest('hex').toUpperCase(), receipt.sha256);
const runExecutable = resolve(desktopRoot, 'output/windows-acceptance-binaries', receipt.sha256, 'nanfeng-ai-desktop-spike.exe');
const ps = (args) => execFileSync('pwsh', ['-NoProfile', ...args], { encoding: 'utf8', timeout: 60000 }).trim();
const quote = value => `'${String(value).replaceAll("'", "''")}'`;
const launchPowerShell = (args) => new Promise((resolveLaunch, reject) => {
  const child = spawn('pwsh', ['-NoProfile', ...args], { stdio: ['ignore', 'pipe', 'pipe'] });
  let stdout = '';
  let stderr = '';
  const timer = setTimeout(() => { child.kill(); reject(new Error(`隔离候选启动超时：${stderr}`)); }, 30000);
  child.stdout.on('data', data => {
    stdout += data.toString();
    const line = stdout.slice(0, stdout.lastIndexOf('\n') + 1).split(/\r?\n/)
      .find(item => item.trim().startsWith('{') && item.includes('"pid"'));
    if (!line) return;
    clearTimeout(timer);
    try { resolveLaunch(JSON.parse(line)); } catch (error) { reject(error); }
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
const requests = [];
const provider = http.createServer((request, response) => {
  const chunks = [];
  request.on('data', chunk => chunks.push(chunk));
  request.on('end', () => {
    try {
      const body = JSON.parse(Buffer.concat(chunks).toString('utf8'));
      const index = requests.length + 1;
      requests.push({ model: body.model, method: request.method, path: request.url, body,
        idempotencyKey: request.headers['idempotency-key'] || '' });
      if ((autoRetryOnly || autoRetryUi) && index === 1) {
        response.writeHead(503, { 'content-type': 'application/json' });
        response.end(JSON.stringify({ error: { message: 'synthetic retry trigger' } }));
        return;
      }
      if ((autoRetryOnly || autoRetryUi) && body.stream === false) {
        response.writeHead(200, { 'content-type': 'application/json' });
        response.end(JSON.stringify({ model: body.model,
          choices: [{ message: { content: '{"title":"附件联网重试验收"}' }, finish_reason: 'stop' }],
          usage: { prompt_tokens: 8, completion_tokens: 4, cost: 0.000004 } }));
        return;
      }
      response.writeHead(200, { 'content-type': 'text/event-stream; charset=utf-8', 'cache-control': 'no-cache' });
      if (qwenAttachmentRetry) response.write(`data: ${JSON.stringify({ web_search: [{ url: 'https://example.com/synthetic-source', title: 'Synthetic source' }] })}\n\n`);
      response.write(`data: ${JSON.stringify({ model: body.model, choices: [{ delta: { content: `合成回答 ${index}` } }] })}\n\n`);
      response.write(`data: ${JSON.stringify({ choices: [{ delta: {}, finish_reason: 'stop' }], usage: { prompt_tokens: 8, completion_tokens: 4, cost: 0.000004 } })}\n\n`);
      response.end('data: [DONE]\n\n');
    } catch (error) { response.writeHead(400); response.end(String(error)); }
  });
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
  const tabs = await fetch(`http://127.0.0.1:${port}/json/list`).then(response => response.json());
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
  const result = await evaluate(port, `(()=>{const node=document.querySelector(${JSON.stringify(selector)});if(!node)return false;node.click();return true})()`);
  assert.equal(result, true, `找不到可点击控件 ${selector}`);
}
// Navigate only actual settings buttons; never fabricate a page or reveal a key.
let result;
const observed = new Map();
const routes = new Map([['personalization', []]]);
const queue = ['personalization'];
async function go(port, route) {
  const inSettings = await evaluate(port, 'Boolean(document.querySelector(".android-settings-main"))');
  await clickUi(port, inSettings ? '[data-action="open-settings-page"][data-page="personalization"]' : '[data-action="show-settings"]');
  await until(() => evaluate(port, 'Boolean(document.querySelector("[data-settings-scroll-key=settings-page-personalization]"))'), 'settings landing');
  for (const page of route) {
    await clickUi(port, `[data-action="open-settings-page"][data-page="${page}"]`);
    await until(() => evaluate(port, `Boolean(document.querySelector('[data-settings-scroll-key="settings-page-${page}"]'))`), `settings ${page}`);
  }
}
async function normalClose() {
  const pid = Number(launch.pid);
  const literal = value => `'${String(value).replaceAll("'", "''")}'`;
  const script = `$expected=${literal(runExecutable)}; $hash=${literal(receipt.sha256)}; `
    + `$p=Get-CimInstance Win32_Process -Filter "ProcessId=${pid}"; `
    + 'if(!$p -or [IO.Path]::GetFullPath($p.ExecutablePath) -ne $expected -or (Get-FileHash -LiteralPath $expected -Algorithm SHA256).Hash -ne $hash){throw "candidate identity mismatch"}; '
    + `$process=Get-Process -Id ${pid}; $handle=$process.Handle; if(!$process.CloseMainWindow()){throw "close rejected"}; `
    + 'if(!$process.WaitForExit(10000)){throw "normal exit timeout"}; if($process.ExitCode -ne 0){throw "normal exit code"}; '
    + '[pscustomobject]@{pid=$process.Id;exitCode=$process.ExitCode}|ConvertTo-Json -Compress';
  const close = JSON.parse(ps(['-Command', script]));
  pids.push(pid); launch = null;
  return close;
}
try {
  let port = await start();
  await ready(port);
  const runtime = await invoke(port, 'CHAT', 'read_desktop_runtime_info');
  assert.equal(runtime.startupMode, 'NORMAL');
  assert.equal(runtime.automaticWorkSuppressed, false);
  const before = await invoke(port, 'CHAT', 'read_desktop_app_settings');
  const beforeWorkspaces = await Promise.all(['CHAT','WORK'].map(area => invoke(port, area, 'list_desktop_workspaces')));
  const pageErrors = [];
  await evaluate(port, `(()=>{window.__settingsSmokeErrors=[];addEventListener('error',e=>window.__settingsSmokeErrors.push(String(e.message)));addEventListener('unhandledrejection',e=>window.__settingsSmokeErrors.push(String(e.reason)));})()`);
  while (queue.length) {
    const page = queue.shift(), route = routes.get(page);
    await go(port, route);
    const projection = await evaluate(port, `(()=>{const secondary=document.querySelector('.android-settings-secondary');const content=document.querySelector('[data-settings-scroll-key="settings-page-${page}"]');return {page:${JSON.stringify(page)},heading:secondary?.querySelector('h1')?.textContent,textLength:content?.textContent?.trim().length||0,visible:Boolean(content&&content.getBoundingClientRect().width>0&&content.getBoundingClientRect().height>0),enabledPages:[...document.querySelectorAll('[data-action="open-settings-page"][data-page]:not([disabled])')].map(n=>n.dataset.page),runtimeError:globalThis.__nanfengDesktopWorkState?.error||''};})()`);
    assert.ok(projection.visible && projection.heading && projection.textLength > 5, `blank settings ${page}`);
    if (projection.runtimeError) pageErrors.push({page, error: projection.runtimeError});
    observed.set(page, {...projection, route});
    for (const next of projection.enabledPages) {
      assert.match(next, /^[a-z-]+$/);
      if (!routes.has(next)) { routes.set(next, [...route, next]); queue.push(next); }
    }
    assert.ok(observed.size <= 40, 'unexpected unbounded page graph');
  }
  assert.equal(pageErrors.length, 0, JSON.stringify(pageErrors));
  assert.deepEqual(await evaluate(port, 'window.__settingsSmokeErrors'), []);
  await go(port, []);
  const expected = {memoryEnabled: !before.product.memoryEnabled, historyLibraryEnabled: !before.product.historyLibraryEnabled};
  await clickUi(port, '[data-action="toggle-product-setting"][data-key="memoryEnabled"]');
  await clickUi(port, '[data-action="toggle-product-setting"][data-key="historyLibraryEnabled"]');
  await until(async () => {
    const settings = await invoke(port, 'CHAT', 'read_desktop_app_settings');
    return Object.entries(expected).every(([k,v]) => settings.product[k] === v);
  }, 'two rapid product choices persisted');
  for (const [picker, value] of [['themeColor','blue'],['fontSize','large']]) {
    await clickUi(port, `[data-action="open-settings-picker"][data-picker="${picker}"]`);
    await clickUi(port, `[data-action="select-settings-picker-option"][data-picker="${picker}"][data-value="${value}"]`);
  }
  await until(async () => {
    const settings = await invoke(port, 'CHAT', 'read_desktop_app_settings');
    return settings.appearance.themeColor === 'blue' && settings.appearance.fontSize === 'large';
  }, 'appearance choices persisted');
  const closes = [await normalClose()];
  port = await start(true); await ready(port);
  const after = await invoke(port, 'CHAT', 'read_desktop_app_settings');
  assert.ok(Object.entries(expected).every(([k,v]) => after.product[k] === v));
  assert.equal(after.appearance.themeColor, 'blue'); assert.equal(after.appearance.fontSize, 'large');
  await until(() => evaluate(port, 'document.documentElement.dataset.themeColor==="blue" && document.documentElement.dataset.fontSize==="large" && getComputedStyle(document.documentElement).getPropertyValue("--accent-orange").trim()==="#357ae8" && getComputedStyle(document.documentElement).getPropertyValue("--app-font-scale").trim()==="1.24"'), 'cold computed appearance');
  assert.deepEqual(await Promise.all(['CHAT','WORK'].map(area => invoke(port, area, 'list_desktop_workspaces'))), beforeWorkspaces);
  assert.equal(requests.length, 0, 'settings review must not call synthetic provider');
  closes.push(await normalClose());
  const literalCandidates = JSON.parse(await readFile(join(desktopRoot, '../docs/review/20261002-current-regression/inventory.json'), 'utf8')).desktop.settingsPages;
  result = {candidateSha256:receipt.sha256,root,runtime,pages:[...observed.values()],
    notReachedLiteralCandidates:literalCandidates.filter(page=>!observed.has(page)),
    beforeRevision:before.revision,afterRevision:after.revision,expectedProduct:expected,
    appearance:{themeColor:after.appearance.themeColor,fontSize:after.appearance.fontSize,computedCold:true},closes,providerRequests:requests.length,pass:true};
} catch(error) {
  result={candidateSha256:receipt.sha256,root,pages:[...observed.values()],error:String(error.stack||error),pass:false};
  process.exitCode=1;
} finally {
  if (launch) { result.forcedCleanup=true; stop(); }
  await new Promise(resolveClose=>provider.close(resolveClose));
  if(root) await writeFile(join(root,'settings-pages-evidence.json'),JSON.stringify(result,null,2),{flag:'wx'});
}
console.log(JSON.stringify(result));
