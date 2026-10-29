import assert from 'node:assert/strict';
import { createHash } from 'node:crypto';
import { execFileSync, spawn } from 'node:child_process';
import { readFile, writeFile } from 'node:fs/promises';
import { dirname, join, resolve, sep } from 'node:path';
import { fileURLToPath } from 'node:url';
import { canonicalJson, semanticHash, sha256, validateExchangeV2 } from '../../protocol/scripts/exchange-v2-lib.mjs';

if (process.platform !== 'win32') throw new Error('仅供 Windows 隔离候选验收');
const desktopRoot = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const rootParent = resolve(process.argv[2] || 'D:\\CodexTemp');
const receipt = JSON.parse(await readFile(join(desktopRoot, 'output/windows-acceptance/current.json'), 'utf8'));
assert.equal(receipt.identifier, 'com.nanzhufeng.ai.desktop.windowsacceptance');
const sourceExe = resolve(desktopRoot, 'output/windows-acceptance', receipt.sha256, 'NanfengAI-Windows-Acceptance.exe');
const runningExe = resolve(desktopRoot, 'output/windows-acceptance-binaries', receipt.sha256, 'nanfeng-ai-desktop-spike.exe');
assert.equal(resolve(receipt.executable), sourceExe);
assert.equal(createHash('sha256').update(await readFile(sourceExe)).digest('hex').toUpperCase(), receipt.sha256);
const quote = value => `'${String(value).replaceAll("'", "''")}'`;
const ps = script => execFileSync('pwsh', ['-NoProfile', '-Command', script], { encoding: 'utf8', timeout: 60000 }).trim();
const stoppedPids = [];
let launch = null;
let root = '';

async function reservePort() {
  const server = (await import('node:http')).createServer();
  await new Promise(done => server.listen(0, '127.0.0.1', done));
  const port = server.address().port;
  await new Promise(done => server.close(done));
  return port;
}
async function start(reopen = false) {
  const port = await reservePort();
  const args = ['-RootParent', rootParent, '-StartupMode', 'normal', '-Visible', '-DebugPort', String(port)];
  if (reopen) args.push('-Root', root);
  const result = await new Promise((done, fail) => {
    const child = spawn('pwsh', ['-NoProfile', '-File', join(desktopRoot, 'scripts/start-windows-acceptance.ps1'), ...args], { stdio: ['ignore', 'pipe', 'pipe'] });
    let output = '';
    let error = '';
    const timer = setTimeout(() => { child.kill(); fail(new Error(`启动超时：${error}`)); }, 30000);
    child.stdout.on('data', chunk => {
      output += chunk.toString();
      const line = output.split(/\r?\n/).find(item => item.trim().startsWith('{') && item.includes('"pid"'));
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
  assert.equal(resolve(result.executable), runningExe);
  launch = result;
  root = resolve(result.root);
  assert.ok(root.startsWith(rootParent + sep));
  assert.equal(JSON.parse(await readFile(join(root, '.nanfeng-windows-acceptance.json'), 'utf8')).identifier, receipt.identifier);
  return port;
}
function stop() {
  if (!launch) return;
  const pid = Number(launch.pid);
  ps(`$p=Get-CimInstance Win32_Process -Filter "ProcessId=${pid}"; if ($p) { if ([IO.Path]::GetFullPath([string]$p.ExecutablePath) -ne ${quote(runningExe)} -or (Get-FileHash -LiteralPath ${quote(runningExe)} -Algorithm SHA256).Hash -ne ${quote(receipt.sha256)}) { throw 'PID identity mismatch' }; Stop-Process -Id ${pid} -Force -ErrorAction Stop }`);
  stoppedPids.push(pid);
  launch = null;
}
async function evaluate(port, expression) {
  const tabs = await fetch(`http://127.0.0.1:${port}/json/list`).then(response => response.json());
  const tab = tabs.find(item => item.type === 'page' && item.url === 'http://tauri.localhost/');
  assert.ok(tab, 'WebView2 页面未就绪');
  const socket = new WebSocket(tab.webSocketDebuggerUrl);
  try {
    await new Promise((done, fail) => { socket.addEventListener('open', done, { once: true }); socket.addEventListener('error', fail, { once: true }); });
    return await new Promise((done, fail) => {
      const timer = setTimeout(() => fail(new Error('CDP evaluation timed out')), 30000);
      socket.addEventListener('message', event => {
        const message = JSON.parse(event.data);
        if (message.id !== 1) return;
        clearTimeout(timer);
        if (message.error || message.result?.exceptionDetails) fail(new Error(message.result?.exceptionDetails?.text || message.error?.message));
        else done(message.result?.result?.value);
      });
      socket.send(JSON.stringify({ id: 1, method: 'Runtime.evaluate', params: { expression, awaitPromise: true, returnByValue: true } }));
    });
  } finally { socket.close(); }
}
const invoke = (port, command, args = {}, area = 'CHAT') => evaluate(port,
  `window.__TAURI__.core.invoke(${JSON.stringify(command)},${JSON.stringify(args)},${JSON.stringify({ headers: { 'x-nanfeng-data-area': area } })}).catch(error=>({__error:String(error)}))`);
async function until(read) {
  const deadline = Date.now() + 30000;
  while (Date.now() < deadline) {
    try { if (await read()) return; } catch { /* Startup. */ }
    await new Promise(done => setTimeout(done, 200));
  }
  throw new Error('native IPC 未就绪');
}

function createPackage(path, exchange) {
  const asset = Buffer.from('a');
  const entries = { 'exchange.json': Buffer.from(canonicalJson(exchange)), [`assets/${sha256(asset)}`]: asset };
  const manifest = { format: 'nfai.exchange.package', packageVersion: 2, exchangeVersion: 2,
    export: exchange.export, files: Object.entries(entries).map(([entry, bytes]) => ({ path: entry, byteCount: bytes.length, sha256: sha256(bytes) })) };
  const payload = { manifest: canonicalJson(manifest), entries: Object.fromEntries(Object.entries(entries).map(([name, bytes]) => [name, bytes.toString('base64')])) };
  const python = 'import base64,json,sys,zipfile\np=json.load(sys.stdin)\nwith zipfile.ZipFile(sys.argv[1],"w",compression=zipfile.ZIP_DEFLATED) as z:\n z.writestr("manifest.json",p["manifest"])\n for name,data in p["entries"].items(): z.writestr(name,base64.b64decode(data))';
  execFileSync('python', ['-c', python, path], { input: JSON.stringify(payload), encoding: 'utf8', timeout: 30000 });
}
function inspectPackage(path) {
  const python = 'import base64,json,sys,zipfile\nwith zipfile.ZipFile(sys.argv[1]) as z:\n names=z.namelist()\n print(json.dumps({"names":names,"entries":{n:base64.b64encode(z.read(n)).decode("ascii") for n in names}}))';
  const { names, entries } = JSON.parse(execFileSync('python', ['-c', python, path], { encoding: 'utf8', timeout: 30000 }));
  assert.equal(names.length, new Set(names).size);
  const bytes = Object.fromEntries(Object.entries(entries).map(([name, encoded]) => [name, Buffer.from(encoded, 'base64')]));
  const manifest = JSON.parse(bytes['manifest.json'].toString('utf8'));
  const exchange = JSON.parse(bytes['exchange.json'].toString('utf8'));
  assert.equal(manifest.packageVersion, 2);
  assert.equal(manifest.exchangeVersion, 2);
  assert.equal(manifest.files.length + 1, names.length);
  for (const item of manifest.files) {
    assert.equal(bytes[item.path]?.length, item.byteCount);
    assert.equal(sha256(bytes[item.path]), item.sha256);
  }
  assert.equal(semanticHash(exchange), exchange.export.semanticHash);
  validateExchangeV2(exchange);
  return { exchange, bytes };
}

let result;
try {
  let port = await start();
  await until(() => evaluate(port, 'Boolean(window.__TAURI__?.core?.invoke)'));
  const runtime = await invoke(port, 'read_desktop_runtime_info');
  const expectedVersion = JSON.parse(await readFile(join(desktopRoot, 'src-tauri/tauri.windows-acceptance.conf.json'), 'utf8')).version;
  assert.equal(runtime.version, expectedVersion);
  const local = await invoke(port, 'ensure_desktop_local_workspace');
  assert.equal(local.exchange.export.origin.appVersion, runtime.version);
  const backupPath = join(root, 'synthetic-version.nfai-backup');
  const backup = await invoke(port, 'export_desktop_local_backup', { selectedPath: backupPath });
  assert.ok(!backup.__error, backup.__error);
  const backupManifest = JSON.parse(execFileSync('python', ['-c',
    'import json,sys,zipfile\nwith zipfile.ZipFile(sys.argv[1]) as z: print(z.read("manifest.json").decode("utf-8"))',
    backupPath], { encoding: 'utf8', timeout: 30000 }));
  assert.equal(backupManifest.appVersion, runtime.version);
  const baseline = await invoke(port, 'list_desktop_workspaces');
  const exchange = JSON.parse(await readFile(resolve(desktopRoot, '../protocol/fixtures/nfai.exchange.v2.golden.json'), 'utf8'));
  const assetHash = sha256(Buffer.from('a'));
  for (const attachment of exchange.knowledge[0].attachments) { attachment.entry = `assets/${assetHash}`; attachment.sha256 = assetHash; }
  exchange.export.semanticHash = semanticHash(exchange);
  validateExchangeV2(exchange);
  const input = join(root, 'synthetic-v2.nfai-exchange');
  createPackage(input, exchange);
  const inputHash = sha256(await readFile(input));
  const imported = await invoke(port, 'import_desktop_workspace_exchange_v2_selected', { selectedPath: input });
  assert.ok(imported.workspaceId && !imported.__error, imported.__error);
  assert.equal(imported.semanticHash, exchange.export.semanticHash);
  assert.equal(imported.packageHash, inputHash);
  assert.equal(imported.assetCount, 1);
  assert.equal(imported.replayed, false);
  const replay = await invoke(port, 'import_desktop_workspace_exchange_v2_selected', { selectedPath: input });
  assert.equal(replay.workspaceId, imported.workspaceId);
  assert.equal(replay.replayed, true);
  const listed = await invoke(port, 'list_desktop_workspace_exchange_v2_committed');
  assert.ok(listed.some(item => item.workspaceId === imported.workspaceId));
  assert.deepEqual((await invoke(port, 'list_desktop_workspaces')).map(item => item.id), baseline.map(item => item.id));
  assert.deepEqual(await invoke(port, 'list_desktop_workspace_exchange_v2_committed', {}, 'WORK'), []);
  const output = join(root, 'synthetic-v2-roundtrip.nfai-exchange');
  const exported = await invoke(port, 'reexport_desktop_workspace_exchange_v2_selected', { workspaceId: imported.workspaceId, selectedPath: output });
  assert.equal(exported.semanticHash, imported.semanticHash);
  const outputBytes = await readFile(output);
  assert.ok(outputBytes.length > 0);
  const roundtrip = inspectPackage(output);
  assert.deepEqual(roundtrip.exchange, exchange);
  assert.equal(sha256(roundtrip.bytes[`assets/${assetHash}`]), assetHash);
  const rejected = await invoke(port, 'import_desktop_workspace_exchange_v2_selected', { selectedPath: input + '.exe' });
  assert.ok(rejected.__error);
  stop();
  port = await start(true);
  await until(() => evaluate(port, 'Boolean(window.__TAURI__?.core?.invoke)'));
  assert.ok((await invoke(port, 'list_desktop_workspace_exchange_v2_committed')).some(item => item.workspaceId === imported.workspaceId));
  assert.equal((await invoke(port, 'import_desktop_workspace_exchange_v2_selected', { selectedPath: input })).replayed, true);
  assert.deepEqual((await invoke(port, 'list_desktop_workspaces')).map(item => item.id), baseline.map(item => item.id));
  const dbCheck = execFileSync('python', ['-c', 'import json,sqlite3,sys\nc=sqlite3.connect(sys.argv[1])\nprint(json.dumps({"integrity":c.execute("PRAGMA integrity_check").fetchone()[0],"imports":c.execute("SELECT COUNT(*) FROM exchange_v2_imports").fetchone()[0],"journal":c.execute("SELECT COUNT(*) FROM exchange_v2_import_journal").fetchone()[0]}))', join(root, 'workspace.sqlite3')], { encoding: 'utf8', timeout: 30000 });
  assert.deepEqual(JSON.parse(dbCheck), { integrity: 'ok', imports: 1, journal: 1 });
  result = { candidateSha256: receipt.sha256, root, workspaceId: imported.workspaceId, semanticHash: imported.semanticHash,
    inputPackageHash: inputHash, outputPackageHash: sha256(outputBytes), assetCount: imported.assetCount,
    replayedAfterRestart: true, workAreaIsolated: true, visibleWorkspaceCountUnchanged: true,
    roundtripFieldsAndAssetVerified: true, productVersion: runtime.version,
    workspaceOriginVersion: local.exchange.export.origin.appVersion,
    backupManifestVersion: backupManifest.appVersion, chatDbIntegrity: 'ok', stoppedPids };
} finally { stop(); }
if (result) console.log(JSON.stringify(result));
