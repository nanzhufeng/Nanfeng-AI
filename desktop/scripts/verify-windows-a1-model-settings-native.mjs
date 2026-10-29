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
const requests = [];
const held=[];
const provider=http.createServer(async(request,response)=>{
  let body='';for await(const chunk of request)body+=chunk.toString('utf8');
  const payload=JSON.parse(body);
  requests.push({path:request.url,model:payload.model,anthropicVersion:request.headers['anthropic-version']||null,xApiSynthetic:String(request.headers['x-api-key']||'').startsWith('nfai-synthetic-model-'),smallProbeOnly:payload.messages?.length===1 && payload.messages[0].content==='hi' && payload.max_tokens===1 && payload.stream===false,syntheticBearerOnly:String(request.headers.authorization||'').startsWith('Bearer nfai-synthetic-model-')});
  held.push(response);
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
    '-StartupMode', 'normal', '-Visible', '-DebugPort', String(port), '-ModelServiceTestEndpoint', `http://127.0.0.1:${providerPort}`];
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


const result = { candidateSha256: receipt.sha256, realService: false, sourceScope: 'A1 settings only', cases: [] };
let stage = 'start';
const syntheticKey = id => `nfai-synthetic-model-${id}`;
async function openModels(port, providerId) {
  const visible = await evaluate(port, 'Boolean(document.querySelector(".model-settings-browser"))');
  if (!visible) {
    await until(() => evaluate(port, 'Boolean(document.querySelector("[data-action=show-settings]")?.getClientRects().length)'), '设置入口完成首屏渲染');
    await clickUi(port, '[data-action="show-settings"]');
    await until(() => evaluate(port, 'Boolean(document.querySelector("[data-action=open-settings-page][data-page=model]"))'), '模型入口');
    await clickUi(port, '[data-action="open-settings-page"][data-page="model"]');
    await until(() => evaluate(port, 'Boolean(document.querySelector("[data-action=open-settings-page][data-page=model-configuration]"))'), '配置入口');
    await clickUi(port, '[data-action="open-settings-page"][data-page="model-configuration"]');
    await until(() => evaluate(port, 'Boolean(document.querySelector(".model-settings-browser"))'), '模型浏览器');
  }
  if (providerId.startsWith('RELAYCORE_')) {
    const sub = `[data-action="select-model-service-provider"][data-provider-id="${providerId}"][data-provider-platform="false"]`;
    if (!await evaluate(port, `Boolean(document.querySelector(${JSON.stringify(sub)}))`)) {
      await clickUi(port, '[data-action="select-model-service-provider"][data-provider-id="RELAYCORE_CLAUDE"][data-provider-platform="true"]');
      await until(() => evaluate(port, `Boolean(document.querySelector(${JSON.stringify(sub)}))`), 'RC型号分组');
    }
    await clickUi(port, sub);
  } else await clickUi(port, `[data-action="select-model-service-provider"][data-provider-id="${providerId}"]`);
  await until(() => evaluate(port, `globalThis.__nanfengDesktopWorkState.modelProviderId===${JSON.stringify(providerId)}`), '当前配置对象');
  await until(async () => {
    const expected = (await modelSettings(port)).find(item => item.providerId === providerId);
    const actual = await evaluate(port, `(()=>{const state=globalThis.__nanfengDesktopWorkState;const item=state.modelServiceSettings.find(item=>item.providerId===${JSON.stringify(providerId)});return item?{enabled:item.enabled,presetId:item.presetId,revision:item.revision,credentialStored:item.credentialStored}:null})()`);
    return actual && ['enabled','presetId','revision','credentialStored'].every(field => actual[field] === expected[field]);
  }, '当前分组已完成原生配置投影');
}
async function modelSettings(port) { return invoke(port, 'CHAT', 'read_desktop_model_service_settings'); }
async function screenshot(port, name) {
  const shot = await cdp(port, 'Page.captureScreenshot', { format: 'png' });
  await writeFile(join(root, name), Buffer.from(shot.data, 'base64'), { flag: 'wx' });
}
try {
  let port = await start(); result.root = root; result.activePort = port; await ready(port);
  const original = await modelSettings(port);
  assert.equal(original.length, 7);
  const probes = [
    ['OPENROUTER', 'GPT_6_LUNA', 'openai/gpt-6-luna', '/chat/completions'],
    ['DEEPSEEK', 'DEEPSEEK_V4_FLASH', 'deepseek-flash', '/chat/completions'],
    ['ZHIPU', 'GLM_5_3_FLASH', 'glm-5.3-flash', '/chat/completions'],
    ['QWEN', 'QWEN_3_7_PLUS', 'qwen3.7-plus', '/chat/completions'],
    ['RELAYCORE_CLAUDE', 'RELAYCORE_OPUS_5_5', 'claude-opus-5.5', '/messages'],
    ['RELAYCORE_GPT', 'RELAYCORE_GPT_6_1_SOL', 'gpt-6.1-sol', '/chat/completions'],
    ['RELAYCORE_GEMINI', 'RELAYCORE_GEMINI_3_8_FLASH', 'gemini-3.8-flash', '/chat/completions'],
  ];
  for (const [providerId, presetId, wireId, path] of probes) {
    stage = `ui-profile-${providerId}`;
    await until(() => evaluate(port, '!globalThis.__nanfengDesktopWorkState.modelSettingsTesting'), '上一探针完整收尾');
    await openModels(port, providerId);
    const current = (await modelSettings(port)).find(s => s.providerId === providerId);
    await clickUi(port, `[data-action="select-model-service-preset"][data-preset-id="${presetId}"]`);
    await until(async () => (await modelSettings(port)).find(s => s.providerId === providerId)?.presetId === presetId, '原生型号提交');
    if (!current.enabled) await clickUi(port, '[data-action="toggle-model-service-enabled"]');
    await until(async () => (await modelSettings(port)).find(s => s.providerId === providerId)?.enabled, '原生开关提交');
    await evaluate(port, `(()=>{const input=document.querySelector('#model-service-api-key');input.value=${JSON.stringify(syntheticKey(providerId))};input.dispatchEvent(new Event('input',{bubbles:true}));return true})()`);
    assert.equal(await evaluate(port, 'document.querySelector("[data-action=test-model-service-connection]").disabled'), true);
    await clickUi(port, '[data-action="save-model-service-settings"]');
    await until(() => evaluate(port, 'globalThis.__nanfengDesktopWorkState.dialog?.kind==="model-credential-saved"'), '合成Key保存反馈');
    await clickUi(port, '[data-action="close-dialog"]');
    assert.equal(await evaluate(port, 'globalThis.__nanfengDesktopWorkState.modelCredentialDraft.length'), 0);
    const saved = (await modelSettings(port)).find(s => s.providerId === providerId);
    assert.equal(saved.enabled, true); assert.equal(saved.presetId, presetId); assert.equal(saved.credentialStored, true);
    const before = requests.length;
    await clickUi(port, '[data-action="test-model-service-connection"]');
    await until(() => requests.length === before + 1, '合成loopback探针');
    const request = requests.at(-1);
    assert.equal(request.path, path); assert.equal(request.model, wireId);
    assert.ok(request.smallProbeOnly && request.syntheticBearerOnly);
    if (providerId === 'RELAYCORE_CLAUDE') { assert.equal(request.anthropicVersion, '2023-06-01'); assert.ok(request.xApiSynthetic); }
    held.at(-1).writeHead(200, {'Content-Type': 'application/json'}); held.at(-1).end('{}');
    await until(() => evaluate(port, 'Boolean(globalThis.__nanfengDesktopWorkState.modelSettingsNotice?.includes("连接成功"))'), '原生探针反馈');
    result.cases.push({ providerId, presetId, keyStored: true, path, uiSwitchAndSelection: true, realService: false });
    if (screenshotMode && ['OPENROUTER','RELAYCORE_CLAUDE'].includes(providerId)) await screenshot(port, `${providerId}-model-settings.png`);
  }
  stage = 'ocr-probe-is-not-chat-setting';
  await until(() => evaluate(port, '!globalThis.__nanfengDesktopWorkState.modelSettingsTesting'), '最后分组探针完整收尾');
  await openModels(port, 'ZHIPU');
  await clickUi(port, '[data-action="select-model-service-preset"][data-preset-id="GLM_OCR"]');
  await until(() => evaluate(port, 'globalThis.__nanfengDesktopWorkState.modelServiceDraft?.presetId==="GLM_OCR" && !globalThis.__nanfengDesktopWorkState.modelSettingsTesting'), 'OCR临时选择就绪');
  assert.equal((await modelSettings(port)).find(s => s.providerId === 'ZHIPU').presetId, 'GLM_5_3_FLASH');
  const beforeOcr = requests.length;
  await clickUi(port, '[data-action="test-model-service-connection"]');
  await until(() => requests.length === beforeOcr + 1, 'OCR合成探针');
  assert.equal(requests.at(-1).path, '/layout_parsing'); assert.equal(requests.at(-1).model, 'glm-ocr');
  held.at(-1).writeHead(200, {'Content-Type': 'application/json'}); held.at(-1).end('{}');
  await until(() => evaluate(port, 'Boolean(globalThis.__nanfengDesktopWorkState.modelSettingsNotice?.includes("GLM-OCR"))'), 'OCR准确反馈');
  stage = 'cold-readback';
  const saved = await modelSettings(port);
  result.close = await normalClose();
  port = await start(true); result.activePort = port; await ready(port);
  const reopened = await modelSettings(port);
  assert.deepEqual(reopened.map(s => [s.providerId,s.enabled,s.presetId,s.revision,s.credentialStored]), saved.map(s => [s.providerId,s.enabled,s.presetId,s.revision,s.credentialStored]));
  result.reopenPreservedSevenGroups = true;
  stage = 'toggle-keeps-key-and-model';
  await openModels(port, 'QWEN');
  await clickUi(port, '[data-action="toggle-model-service-enabled"]');
  await until(async () => !(await modelSettings(port)).find(s => s.providerId === 'QWEN').enabled, '停用持久化');
  const off = (await modelSettings(port)).find(s => s.providerId === 'QWEN');
  assert.ok(off.credentialStored); assert.equal(off.presetId, 'QWEN_3_7_PLUS');
  await clickUi(port, '[data-action="toggle-model-service-enabled"]');
  await until(async () => (await modelSettings(port)).find(s => s.providerId === 'QWEN').enabled, '恢复启用');
  result.closeAfterReopen = await normalClose();
  result.pass = true;
} catch (error) {
  result.pass = false; result.stage = stage; result.error = String(error);
  if (launch && result.activePort) {
    try { result.failureUi = await evaluate(result.activePort, `(()=>({ready:document.readyState,bodyText:document.body.innerText.slice(0,1200),viewport:[innerWidth,innerHeight],actions:[...new Set([...document.querySelectorAll('[data-action]')].map(n=>n.dataset.action))].slice(0,50),state:globalThis.__nanfengDesktopWorkState?{pane:globalThis.__nanfengDesktopWorkState.pane,settingsSection:globalThis.__nanfengDesktopWorkState.settingsSection,modelSettingsTesting:globalThis.__nanfengDesktopWorkState.modelSettingsTesting,modelSettingsNotice:globalThis.__nanfengDesktopWorkState.modelSettingsNotice,modelSettingsError:globalThis.__nanfengDesktopWorkState.modelSettingsError,probePreset:globalThis.__nanfengDesktopWorkState.modelServiceDraft?.presetId,error:globalThis.__nanfengDesktopWorkState.error,status:globalThis.__nanfengDesktopWorkState.status}:null}))()`); console.log(JSON.stringify({failureUi:result.failureUi})); } catch { result.failureUiUnavailable = true; }
  }
  process.exitCode = 1;
} finally {
  for (const response of held) if (!response.writableEnded) response.destroy();
  if (launch) { result.forcedCleanup = true; stop(); }
  provider.closeAllConnections(); await new Promise(done => provider.close(done));
  result.pids = pids; result.requests = requests;
  if (root) await writeFile(join(root, 'a1-model-settings-evidence.json'), JSON.stringify(result,null,2), {flag:'wx'});
}
console.log(JSON.stringify({candidateSha256:receipt.sha256,root,pass:result.pass,stage:result.stage,error:result.error,cases:result.cases.length,pids}));
