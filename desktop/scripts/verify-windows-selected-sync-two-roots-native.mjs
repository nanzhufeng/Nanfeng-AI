import assert from 'node:assert/strict';
import { createHash, randomUUID } from 'node:crypto';
import { execFileSync, spawn } from 'node:child_process';
import { copyFile, readFile, writeFile, unlink } from 'node:fs/promises';
import http from 'node:http';
import { dirname, join, resolve, sep } from 'node:path';
import { fileURLToPath } from 'node:url';
import { canonicalJson } from '../../protocol/scripts/exchange-lib.mjs';

if (process.platform !== 'win32') throw new Error('仅供 Windows 标记合成根原生验收');
const desktopRoot = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const rootParent = resolve(process.argv[2] || 'D:\\CodexTemp');
const offlineRecovery = process.argv.includes('--offline-recovery');
const liveRecovery = process.argv.includes('--live-recovery');
const titleConflict = process.argv.includes('--title-conflict');
const concurrentMessages = process.argv.includes('--concurrent-messages');
const casRace = process.argv.includes('--cas-race');
const casExhaustion = process.argv.includes('--cas-exhaustion');
const deletePropagation = process.argv.includes('--delete-propagation');
const branchEdit = process.argv.includes('--branch-edit');
const metadataChange = process.argv.includes('--metadata-change');
const pendingCloudRead = process.argv.includes('--pending-cloud-read');
const pendingMultiTitle = process.argv.includes('--pending-multi-title');
const pendingCrossAccount = process.argv.includes('--pending-cross-account');
const pendingUnknownCommit = process.argv.includes('--pending-unknown-commit');
const pendingMultiDelete = process.argv.includes('--pending-multi-delete');
const pendingRemoteReconcile = process.argv.includes('--pending-remote-reconcile');
assert.ok(!pendingRemoteReconcile || pendingUnknownCommit);
assert.ok(!pendingCrossAccount || pendingMultiTitle);
assert.ok(!pendingUnknownCommit || (pendingMultiTitle && !pendingCrossAccount));
assert.ok(!pendingMultiDelete || (pendingMultiTitle && !pendingCrossAccount && !pendingUnknownCommit));
const mismatchedDirect = process.argv.includes('--mismatched-direct');
const receipt = JSON.parse(await readFile(join(desktopRoot, 'output/windows-acceptance/current.json'), 'utf8'));
assert.equal(receipt.identifier, 'com.nanzhufeng.ai.desktop.windowsacceptance');
const sourceExe = resolve(desktopRoot, 'output/windows-acceptance', receipt.sha256, 'NanfengAI-Windows-Acceptance.exe');
const runningExe = resolve(desktopRoot, 'output/windows-acceptance-binaries', receipt.sha256, 'nanfeng-ai-desktop-spike.exe');
assert.equal(resolve(receipt.executable), sourceExe);
assert.equal(createHash('sha256').update(await readFile(sourceExe)).digest('hex').toUpperCase(), receipt.sha256);
const quote = value => `'${String(value).replaceAll("'", "''")}'`;
const ps = script => execFileSync('pwsh', ['-NoProfile', '-Command', script], { encoding: 'utf8', timeout: 60000 }).trim();
const token = 'nfai-synthetic-selected-sync-access';
const refresh = 'nfai-synthetic-selected-sync-refresh';
const accountEmail = 'windows-selected-sync@example.invalid';
const documents = new Map();
const requests = [];
const serverErrors = [];
let newAccount = false;
let holdPendingRead = false;
let heldPendingRead = null;
let corruptPendingCommitReadback = false;
let corruptOneReadback = false;
let rejectCommits = false;
let rejectedCommitCount = 0;
let staleReadOnce = null;
let staleRejectionCount = 0;
let forcedStaleCommits = 0;
const providerRequests = [];
const providerAnswer = '合成计量回答已完成';
const provider = http.createServer(async (request, response) => {
  try {
    const chunks = [];
    for await (const chunk of request) chunks.push(chunk);
    providerRequests.push(JSON.parse(Buffer.concat(chunks).toString('utf8')));
    response.writeHead(200, { 'content-type': 'text/event-stream; charset=utf-8' });
    response.end(`data: ${JSON.stringify({ choices: [{ delta: { content: providerAnswer }, finish_reason: 'stop' }], usage: { prompt_tokens: 11, completion_tokens: 7, cost: 0.000012 } })}\n\ndata: [DONE]\n\n`);
  } catch (error) { serverErrors.push(String(error)); response.writeHead(500); response.end(); }
});
await new Promise(done => provider.listen(0, '127.0.0.1', done));
const providerPort = provider.address().port;
const server = http.createServer(async (request, response) => {
  try {
    const chunks = [];
    for await (const chunk of request) chunks.push(chunk);
    const body = chunks.length ? JSON.parse(Buffer.concat(chunks).toString('utf8')) : {};
    const path = request.url?.split('?')[0];
    requests.push(path);
    let value;
    if (path === '/auth/v1/token') {
      assert.equal(body.auth_code, 'nfai-synthetic-windows-account-code');
      assert.equal(request.headers.apikey, 'nfai-synthetic-windows-account');
      value = { access_token: newAccount ? `${token}-new` : token, refresh_token: refresh, expires_in: 3600,
        user: { id: newAccount ? '00000000-0000-4000-8000-000000000064' : '00000000-0000-4000-8000-000000000063', email: newAccount ? 'new-pending-owner@example.invalid' : accountEmail, user_metadata: { full_name: '合成同步账号' } } };
    } else {
      assert.equal(request.headers.authorization, `Bearer ${newAccount ? `${token}-new` : token}`);
      assert.equal(body.p_app_id, 'com.nanzhufeng.ai');
      if (path === '/rest/v1/rpc/nanfeng_sync_read_document') {
        if (corruptOneReadback) {
          corruptOneReadback = false;
          if (pendingRemoteReconcile) {
            const remote = JSON.parse(JSON.stringify(documents.get(body.p_document_id)));
            remote.revision++; remote.payload.revision = remote.revision;
            const bytes = Buffer.from(canonicalJson(remote.payload));
            remote.payloadHash = createHash('sha256').update(bytes).digest('hex');
            remote.payloadByteCount = bytes.length; documents.set(body.p_document_id, remote);
          }
          response.writeHead(200, { 'content-type': 'application/json' });
          response.end(JSON.stringify([{ envelope: { format: 'unknown-format' } }]));
          return;
        }
        if (holdPendingRead) {
          heldPendingRead = () => {
            response.writeHead(200, { 'content-type': 'application/json' });
            response.end(JSON.stringify(documents.has(body.p_document_id) ? [{ envelope: documents.get(body.p_document_id) }] : []));
          };
          return;
        }
        if (staleReadOnce?.documentId === body.p_document_id) {
          value = [{ envelope: staleReadOnce }];
          staleReadOnce = null;
        } else value = documents.has(body.p_document_id) ? [{ envelope: documents.get(body.p_document_id) }] : [];
      } else if (path === '/rest/v1/rpc/nanfeng_sync_commit_document') {
        if (rejectCommits) {
          rejectedCommitCount++;
          response.writeHead(503, { 'content-type': 'application/json' });
          response.end('{"message":"synthetic gateway unavailable"}');
          return;
        }
        assert.match(body.p_document_id, /^conversation-[a-f0-9]{40}$/);
        const previous = documents.get(body.p_document_id);
        const expectedRevision = previous?.revision ?? 0;
        if (forcedStaleCommits > 0) {
          forcedStaleCommits--;
          staleRejectionCount++;
          response.writeHead(409, { 'content-type': 'application/json' });
          response.end('{"message":"NFAI_SYNC_STALE_REVISION"}');
          return;
        }
        if (casRace && body.p_expected_revision !== expectedRevision) {
          staleRejectionCount++;
          response.writeHead(409, { 'content-type': 'application/json' });
          response.end('{"message":"NFAI_SYNC_STALE_REVISION"}');
          return;
        }
        assert.equal(body.p_expected_revision, expectedRevision);
        assert.equal(body.p_envelope.format, 'nfai.sync.direct');
        assert.equal(body.p_envelope.documentId, body.p_document_id);
        assert.equal(body.p_envelope.revision, expectedRevision + 1);
        documents.set(body.p_document_id, body.p_envelope);
        if (corruptPendingCommitReadback) { corruptPendingCommitReadback = false; corruptOneReadback = true; }
        value = [{ revision: expectedRevision + 1, payload_hash: body.p_envelope.payloadHash }];
      } else if (path === '/rest/v1/rpc/nanfeng_sync_inventory') {
        value = { documentCount: documents.size, documents: [...documents.values()].map(envelope => ({ envelope })) };
      } else if (path === '/rest/v1/rpc/nanfeng_sync_delete_document') {
        const previous = documents.get(body.p_document_id);
        assert.equal(body.p_expected_revision, previous?.revision);
        value = [{ deleted: documents.delete(body.p_document_id) }];
      } else throw new Error(`意外 RPC：${path}`);
    }
    const encoded = JSON.stringify(value);
    response.writeHead(200, { 'content-type': 'application/json', 'content-length': Buffer.byteLength(encoded) });
    response.end(encoded);
  } catch (error) {
    serverErrors.push(String(error));
    response.writeHead(500, { 'content-type': 'application/json' });
    response.end('{"message":"synthetic service failure"}');
  }
});
await new Promise(done => server.listen(0, '127.0.0.1', done));
const servicePort = server.address().port;
const stoppedPids = [];
let launch = null;

async function reservePort() {
  const listener = http.createServer();
  await new Promise(done => listener.listen(0, '127.0.0.1', done));
  const port = listener.address().port;
  await new Promise(done => listener.close(done));
  return port;
}
async function start(reopenRoot = '', withProvider = false) {
  const port = await reservePort();
  const args = ['-RootParent', rootParent, '-StartupMode', 'normal', '-Visible', '-DebugPort', String(port),
    '-AccountSyncTestEndpoint', `http://127.0.0.1:${servicePort}/`];
  if (withProvider) args.push('-OrdinaryChatTestEndpoint', `http://127.0.0.1:${providerPort}/`);
  if (reopenRoot) args.push('-Root', reopenRoot);
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
  assert.ok(resolve(result.root).startsWith(rootParent + sep));
  assert.equal(JSON.parse(await readFile(join(result.root, '.nanfeng-windows-acceptance.json'), 'utf8')).identifier, receipt.identifier);
  launch = result;
  return port;
}
function stop() {
  if (!launch) return;
  const pid = Number(launch.pid);
  ps(`$p=Get-Process -Id ${pid} -ErrorAction SilentlyContinue; if ($p) { if ([IO.Path]::GetFullPath([string]$p.Path) -ne ${quote(runningExe)} -or (Get-FileHash -LiteralPath ${quote(runningExe)} -Algorithm SHA256).Hash -ne ${quote(receipt.sha256)}) { throw 'PID identity mismatch' }; Stop-Process -Id ${pid} -Force -ErrorAction Stop }`);
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
async function until(read, label, timeoutMs = 30000) {
  const deadline = Date.now() + timeoutMs;
  while (Date.now() < deadline) {
    try { if (await read()) return; } catch { /* Startup or redraw. */ }
    await new Promise(done => setTimeout(done, 200));
  }
  throw new Error(`${label} 未在原生进程就绪`);
}
const click = (port, selector) => evaluate(port,
  `(()=>{const node=document.querySelector(${JSON.stringify(selector)});if(!node||node.disabled)return false;node.click();return true})()`);
const dialogOpen = () => {
  const raw = ps(`& ${quote(join(desktopRoot, 'scripts/inspect-windows-file-dialog.ps1'))} -ProcessId ${Number(launch.pid)}`);
  const windows = raw ? JSON.parse(raw) : [];
  return windows.some(item => item.children?.some(child => child.class === '#32770'));
};
async function watchStatus(port, expected, flag) {
  assert.equal(await evaluate(port, `(()=>{const state=globalThis.__nanfengDesktopWorkState;if(!state)return false;let value=state.status;globalThis[${JSON.stringify(flag)}]=false;Object.defineProperty(state,'status',{configurable:true,enumerable:true,get(){return value},set(next){value=next;if(next===${JSON.stringify(expected)})globalThis[${JSON.stringify(flag)}]=true}});return true})()`), true);
}
async function login(port) {
  await until(() => evaluate(port, 'Boolean(window.__TAURI__?.core?.invoke)'), '原生 IPC');
  await until(() => evaluate(port, 'Boolean(document.querySelector(`[data-action="show-settings"]`))'), '设置入口');
  assert.equal(await click(port, '[data-action="show-settings"]'), true);
  await until(() => evaluate(port, 'Boolean(document.querySelector(`[data-action="open-settings-page"][data-page="account"]`))'), '账号设置');
  assert.equal(await click(port, '[data-action="open-settings-page"][data-page="account"]'), true);
  await until(() => evaluate(port, 'Boolean(document.querySelector(`.android-account-hero [data-action="sign-in-google-account"]`))'), '登录入口');
  assert.equal(await click(port, '.android-account-hero [data-action="sign-in-google-account"]'), true);
  await until(async () => (await invoke(port, 'read_desktop_account_sync'))?.email === accountEmail, '合成账号登录');
}

let result;
let primaryError;
try {
  let port = await start();
  const rootA = resolve(launch.root);
  await login(port);
  const selected = join(rootA, 'synthetic-selected-sync.nfai-exchange');
  await copyFile(resolve(desktopRoot, '../protocol/artifacts/nfai.exchange.v1.golden.nfai-exchange'), selected);
  assert.equal(await click(port, '[data-action="open-settings-page"][data-page="data"]'), true);
  await until(() => evaluate(port, 'Boolean(document.querySelector(`[data-action="start-import"]`))'), '交换包可见入口');
  assert.equal(await click(port, '[data-action="start-import"]'), true);
  await until(dialogOpen, 'Windows 交换包系统选择器');
  const picker = JSON.parse(ps(`& ${quote(join(desktopRoot, 'scripts/operate-windows-file-dialog.ps1'))} -ProcessId ${Number(launch.pid)} -Action 'select' -SelectedPath ${quote(selected)} -AcceptanceRoot ${quote(rootA)}`));
  assert.equal(picker.action, 'select');
  await until(() => !dialogOpen(), 'Windows 交换包选择器关闭');
  try {
    await until(() => evaluate(port, 'globalThis.__nanfengDesktopWorkState?.current?.summary?.title === "导入工作区"'), '交换包界面导入完成');
  } catch (error) {
    throw new Error(`${error.message}；${JSON.stringify(await evaluate(port,
      '({status:globalThis.__nanfengDesktopWorkState?.status,error:globalThis.__nanfengDesktopWorkState?.error,title:globalThis.__nanfengDesktopWorkState?.current?.summary?.title})'))}`);
  }
  const imported = await evaluate(port, 'globalThis.__nanfengDesktopWorkState.current');
  assert.ok(imported.summary?.id && !imported.__error, imported.__error);
  const conversation = imported.exchange.conversations[0];
  assert.ok(conversation.messages.some(item => item.blocks?.some(block => block.text === '非敏感夹具文本。')));
  await evaluate(port, 'window.location.reload(); true');
  await until(() => evaluate(port, 'Boolean(window.__TAURI__?.core?.invoke)'), '导入后重载');
  const workspaceSelector = `[data-action="select-workspace"][data-id="${imported.summary.id}"]`;
  await until(() => evaluate(port, `Boolean(document.querySelector(${JSON.stringify(workspaceSelector)}))`), '导入工作区可见入口');
  await watchStatus(port, '已切换到独立本地工作区，并打开该工作区最近对话。', '__nfaiAcceptanceSelectedA');
  assert.equal(await click(port, workspaceSelector), true);
  await until(() => evaluate(port, `globalThis.__nfaiAcceptanceSelectedA && globalThis.__nanfengDesktopWorkState?.current?.summary?.id === ${JSON.stringify(imported.summary.id)} && globalThis.__nanfengDesktopWorkState?.pane === 'chat'`), '工作区选择完成');
  const rowSelector = `[data-conversation-row][data-id="${conversation.id}"]`;
  await until(() => evaluate(port, `Boolean(document.querySelector(${JSON.stringify(rowSelector)}))`), '导入会话可见入口');
  assert.equal(await evaluate(port, `(()=>{const row=document.querySelector(${JSON.stringify(rowSelector)});if(!row)return false;row.dispatchEvent(new MouseEvent('contextmenu',{bubbles:true,cancelable:true}));return true})()`), true);
  await until(() => evaluate(port, 'Boolean(document.querySelector(`[data-action="context-menu-sync"]`))'), '对话同步菜单');
  assert.equal(await click(port, '[data-action="context-menu-sync"]'), true);
  await until(() => documents.size === 1, '可见菜单首次提交');
  await until(async () => (await invoke(port, 'read_desktop_account_sync'))?.selectedConversationCount === 1, '可见菜单成功回执');
  try {
    await until(() => evaluate(port, 'globalThis.__nanfengDesktopWorkState?.status === "所选对话已同步到南枫云并通过回读校验。"'), '同步完成反馈');
  } catch (error) {
    throw new Error(`${error.message}；当前状态 ${JSON.stringify(await evaluate(port, '({status:globalThis.__nanfengDesktopWorkState?.status,error:globalThis.__nanfengDesktopWorkState?.error,workspaceId:globalThis.__nanfengDesktopWorkState?.current?.summary?.id})'))}`);
  }
  const synced = await invoke(port, 'sync_selected_desktop_conversation', { workspaceId: imported.summary.id, conversationId: conversation.id });
  assert.equal(synced.status, 'UP_TO_DATE', JSON.stringify(synced));
  assert.equal(documents.size, 1);
  assert.equal((await invoke(port, 'read_desktop_account_sync')).selectedConversationCount, 1);
  const [documentId, envelope] = [...documents][0];
  assert.equal(envelope.documentId, documentId);
  const conversationRecords = envelope.payload.records.filter(item => item.kind === 'conversation');
  assert.equal(conversationRecords.length, 1);
  assert.equal(conversationRecords[0].id, conversation.id);
  assert.ok(JSON.stringify(envelope.payload).includes('非敏感夹具文本。'));
  assert.ok(conversationRecords[0].content.messages.every(message => message.blocks.every(block => block.kind === 'TEXT')));
  stop();

  port = await start();
  const rootB = resolve(launch.root);
  assert.notEqual(rootA, rootB);
  await login(port);
  assert.equal((await invoke(port, 'read_desktop_account_sync')).selectedConversationCount, 0);
  if (mismatchedDirect) {
    const tampered = structuredClone(envelope);
    tampered.payload.documentId = 'conversation-mismatched-synthetic';
    const payloadBytes = Buffer.from(canonicalJson(tampered.payload));
    tampered.payloadHash = createHash('sha256').update(payloadBytes).digest('hex');
    tampered.payloadByteCount = payloadBytes.length;
    documents.set(documentId, tampered);
    const rejected = await invoke(port, 'restore_all_desktop_cloud_conversations');
    assert.ok(!rejected.__error && rejected.failedCount === 1 && rejected.restored.length === 0, JSON.stringify(rejected));
    assert.equal((await invoke(port, 'read_desktop_account_sync')).selectedConversationCount, 0);
    documents.set(documentId, envelope);
  }
  const restored = await invoke(port, 'restore_all_desktop_cloud_conversations');
  assert.ok(!restored.__error, restored.__error);
  assert.equal(restored.failedCount, 0);
  assert.equal(restored.restored.length, 1);
  assert.equal(restored.restored[0].conversationId, conversation.id);
  const recovered = await invoke(port, 'read_desktop_workspace', { workspaceId: restored.restored[0].workspaceId });
  assert.ok(recovered.exchange.conversations[0].messages.some(item => item.blocks?.some(block => block.text === '非敏感夹具文本。')));
  assert.equal((await invoke(port, 'read_desktop_account_sync')).selectedConversationCount, 1);
  assert.equal((await invoke(port, 'read_desktop_account_sync', {}, 'WORK')).selectedConversationCount, 0);
  assert.deepEqual(await invoke(port, 'restore_all_desktop_cloud_conversations').then(value => value.restored.map(item => item.status)), ['ALREADY_LOCAL']);
  stop();

  port = await start(rootB);
  await until(() => evaluate(port, 'Boolean(window.__TAURI__?.core?.invoke)'), '冷启原生 IPC');
  const cold = await invoke(port, 'read_desktop_workspace', { workspaceId: restored.restored[0].workspaceId });
  assert.ok(cold.exchange.conversations[0].messages.some(item => item.blocks?.some(block => block.text === '非敏感夹具文本。')));
  assert.equal((await invoke(port, 'read_desktop_account_sync')).selectedConversationCount, 1);
  const restoredWorkspaceId = restored.restored[0].workspaceId;
  const bWorkspaceSelector = `[data-action="select-workspace"][data-id="${restoredWorkspaceId}"]`;
  await until(() => evaluate(port, `Boolean(document.querySelector(${JSON.stringify(bWorkspaceSelector)}))`), '恢复工作区可见入口');
  await watchStatus(port, '已切换到独立本地工作区，并打开该工作区最近对话。', '__nfaiAcceptanceSelectedB');
  assert.equal(await click(port, bWorkspaceSelector), true);
  await until(() => evaluate(port, `globalThis.__nfaiAcceptanceSelectedB && globalThis.__nanfengDesktopWorkState?.current?.summary?.id === ${JSON.stringify(restoredWorkspaceId)} && globalThis.__nanfengDesktopWorkState?.pane === 'chat'`), '恢复工作区选择完成');
  await until(() => evaluate(port, `Boolean(document.querySelector(${JSON.stringify(rowSelector)}))`), '恢复会话可见入口');
  if (pendingMultiTitle) {
    const second = await invoke(port, 'mutate_desktop_domain', { args: {
      intentId: 'synthetic-pending-multi-create', workspaceId: restoredWorkspaceId,
      entity: 'conversation', action: 'create', fields: {
        title: '合成第二目标', projectId: null, firstMessage: '合成多目标本地材料',
      },
    } });
    assert.ok(!second.__error && second.objectId, JSON.stringify(second));
    const secondSync = await invoke(port, 'sync_selected_desktop_conversation', {
      workspaceId: restoredWorkspaceId, conversationId: second.objectId, continuation: false,
    });
    assert.equal(secondSync.status, 'SYNCED', JSON.stringify(secondSync));
    const targets = [conversation.id, second.objectId];
    const titles = [pendingMultiDelete ? conversation.title : '合成第一目标改名', '合成第二目标改名'];
    for (let index = 0; index < targets.length; index++) {
      const current = await invoke(port, 'read_desktop_workspace', { workspaceId: restoredWorkspaceId });
      const item = current.exchange.conversations.find(value => value.id === targets[index]);
      const changed = await invoke(port, 'mutate_desktop_domain', { args: {
        intentId: `synthetic-pending-multi-title-${index}`, workspaceId: restoredWorkspaceId,
        entity: 'conversation', action: pendingMultiDelete && index === 0 ? 'softDelete' : 'update', objectId: targets[index],
        expectedRevision: item.revision, fields: pendingMultiDelete && index === 0 ? {} : { title: titles[index] },
      } });
      assert.ok(!changed.__error, JSON.stringify(changed));
    }
    const pending = () => JSON.parse(execFileSync('python', ['-c',
      'import json,sqlite3,sys;c=sqlite3.connect(sys.argv[1]);print(json.dumps(c.execute("SELECT conversation_id,stage,attempt FROM desktop_sync_jobs WHERE workspace_id=? AND stage IN (\'TITLE_PENDING\',\'CONTENT_PENDING\',\'DELETE_PENDING\')",(sys.argv[2],)).fetchall()));c.close()',
      join(rootB, 'workspace.sqlite3'), restoredWorkspaceId], { encoding: 'utf8', timeout: 10000 }));
    const queued = pending();
    assert.deepEqual(queued.map(row => row[0]).sort(), [...targets].sort());
    assert.ok(queued.every(row => row[1] === (pendingMultiDelete && row[0] === conversation.id ? 'DELETE_PENDING' : 'TITLE_PENDING')));
    console.log(JSON.stringify({ phase: 'multi-title-queued', rootB, candidateSha256: receipt.sha256, queued }));
    if (pendingUnknownCommit) {
      corruptPendingCommitReadback = true;
      await unlink(join(rootB, '.account-periodic-probe-completed')).catch(error => { if (error.code !== 'ENOENT') throw error; });
      await writeFile(join(rootB, '.account-periodic-probe-arm'), 'periodic');
      await until(() => readFile(join(rootB, '.account-periodic-probe-completed'), 'utf8').then(value => value === 'periodic').catch(() => false), 'UNKNOWN核对后pending线程收据', 45000);
      const remaining = pending();
      console.log(JSON.stringify({ phase: 'pending-unknown-reconciled-first-cycle', rootB, candidateSha256: receipt.sha256, remaining, corruptReadbackConsumed: !corruptOneReadback && !corruptPendingCommitReadback }));
      assert.ok(!corruptOneReadback && !corruptPendingCommitReadback);
      assert.deepEqual(remaining, [], '已成功核对的pending目标不能再次排重试');
    }
    if (pendingCrossAccount) {
      holdPendingRead = true;
      await unlink(join(rootB, '.account-periodic-probe-completed')).catch(error => { if (error.code !== 'ENOENT') throw error; });
      await writeFile(join(rootB, '.account-periodic-probe-arm'), 'periodic');
      await until(() => heldPendingRead, '多目标pending首云读挂起', 45000);
      const releaseTimer = setTimeout(() => { holdPendingRead = false; heldPendingRead?.(); heldPendingRead = null; }, 12000);
      try {
        await invoke(port, 'sign_out_desktop_google_account'); newAccount = true;
        const changed = await invoke(port, 'sign_in_desktop_google_account');
        assert.equal(changed.email, 'new-pending-owner@example.invalid');
        assert.ok(heldPendingRead, '夹具提前释放，不能作为跨账号证据');
        const calls = requests.length;
        holdPendingRead = false; heldPendingRead(); heldPendingRead = null; clearTimeout(releaseTimer);
        await until(() => readFile(join(rootB, '.account-periodic-probe-completed'), 'utf8').then(value => value === 'periodic').catch(() => false), 'pending线程完成后caller收据', 45000);
        assert.equal(requests.length, calls, '切账号后不得执行剩余目标或额外云请求');
        assert.deepEqual(pending(), queued, '旧账号任务不得迟到清理或增加重试');
        assert.ok(targets.every((id, index) => ![...documents.values()].some(envelope => envelope.payload.records.some(record => record.id === id && record.content.title === titles[index]))));
        console.log(JSON.stringify({ phase: 'pending-multi-cross-account-released', rootB, candidateSha256: receipt.sha256, postReleaseRequests: 0, oldJobsUnchanged: true, remainingTargetNotRun: true }));
      } finally { clearTimeout(releaseTimer); holdPendingRead = false; heldPendingRead?.(); heldPendingRead = null; }
    } else {
    await until(() => targets.every((id, index) => pendingMultiDelete && index === 0
      ? !documents.has(documentId)
      : [...documents.values()].some(envelope => envelope.payload.records.some(record => record.kind === 'conversation' && record.id === id && record.content.title === titles[index]))),
      '生产30秒线程多目标标题续同步', 75000);
    await until(() => pending().length === 0, '多目标标题任务收尾', 75000);
    }
    const beforeCold = await invoke(port, 'read_desktop_workspace', { workspaceId: restoredWorkspaceId });
    stop(); port = await start(rootB);
    await until(() => evaluate(port, 'Boolean(window.__TAURI__?.core?.invoke && globalThis.__nanfengDesktopWorkState?.runtimeInfo?.dataAreasReady)'), '多目标冷启');
    const cold = await invoke(port, 'read_desktop_workspace', { workspaceId: restoredWorkspaceId });
    for (let index = 0; index < targets.length; index++) {
      const actual = cold.exchange.conversations.find(item => item.id === targets[index]);
      assert.equal(actual.title, titles[index]);
      if (pendingMultiDelete && index === 0) assert.equal(actual.deleted, true);
      assert.deepEqual(actual.messages, beforeCold.exchange.conversations.find(item => item.id === targets[index]).messages);
    }
    const coldAccount = await invoke(port, 'read_desktop_account_sync');
    assert.equal(coldAccount.selectedConversationCount, pendingCrossAccount ? 0 : pendingMultiDelete ? 1 : 2);
    if (pendingCrossAccount) assert.equal(coldAccount.email, 'new-pending-owner@example.invalid');
    assert.deepEqual(pending(), pendingCrossAccount ? queued : []); assert.deepEqual(serverErrors, []);
    result = { candidateSha256: receipt.sha256, rootA, rootB, targets,
      productionThirtySecondPendingCaller: true, twoTitleTargetsUploaded: !pendingCrossAccount && !pendingMultiDelete,
      jobsFinished: !pendingCrossAccount, crossAccountRemainingTargetStopped: pendingCrossAccount,
      deletionTargetRemovedCloudButKeptLocal: pendingMultiDelete,
      unknownCommitReconciledInFirstCycle: pendingUnknownCommit,
      remoteChangedBeforeReconciliation: pendingRemoteReconcile,
      coldReadback: true, stoppedPids };
  } else if (pendingCloudRead) {
    const favorite = await invoke(port, 'set_desktop_conversation_favorite', {
      workspaceId: restoredWorkspaceId, conversationId: conversation.id, favorite: true,
    });
    assert.ok(!favorite.__error && favorite.includes(conversation.id), JSON.stringify(favorite));
    const pendingJobs = () => JSON.parse(execFileSync('python', ['-c',
      'import json,sqlite3,sys; c=sqlite3.connect(sys.argv[1]); print(json.dumps(c.execute("SELECT stage FROM desktop_sync_jobs WHERE workspace_id=? AND conversation_id=? AND stage=\'CONTENT_PENDING\'",(sys.argv[2],sys.argv[3])).fetchall()))',
      join(rootB, 'workspace.sqlite3'), restoredWorkspaceId, conversation.id], { encoding: 'utf8', timeout: 10000 }));
    assert.deepEqual(pendingJobs(), [['CONTENT_PENDING']]);
    const beforeRemoteRevision = documents.get(documentId).revision;
    const pendingRead = await invoke(port, 'restore_all_desktop_cloud_conversations');
    assert.ok(!pendingRead.__error, JSON.stringify(pendingRead));
    assert.equal(pendingRead.failureReasons?.LOCAL_SYNC_PENDING, 1, JSON.stringify(pendingRead));
    assert.ok((await invoke(port, 'read_desktop_favorite_conversation_ids', { workspaceId: restoredWorkspaceId })).includes(conversation.id));
    assert.equal(documents.get(documentId).revision, beforeRemoteRevision);
    await until(() => documents.get(documentId)?.payload.records.find(item => item.kind === 'conversation')?.content.favorited === true, '待决收藏自动续同步', 75000);
    await until(() => pendingJobs().length === 0, '待决收藏任务清理', 75000);
    const afterRead = await invoke(port, 'restore_all_desktop_cloud_conversations');
    assert.ok(!afterRead.__error && afterRead.failedCount === 0, JSON.stringify(afterRead));
    stop();
    port = await start(rootA);
    await until(() => evaluate(port, 'Boolean(window.__TAURI__?.core?.invoke)'), '第一根收藏回读原生 IPC');
    const aRead = await invoke(port, 'restore_all_desktop_cloud_conversations');
    assert.ok(!aRead.__error && aRead.failedCount === 0, JSON.stringify(aRead));
    assert.ok((await invoke(port, 'read_desktop_favorite_conversation_ids', { workspaceId: imported.summary.id })).includes(conversation.id));
    assert.deepEqual(serverErrors, []);
    result = { candidateSha256: receipt.sha256, rootA, rootB, documentId,
      pendingCloudReadPreservedLocalFavorite: true, resumedFavoriteUploaded: true,
      secondCloudReadSucceeded: true, otherRootFavoriteRestored: true, stoppedPids };
  } else if (metadataChange) {
    const bBefore = await invoke(port, 'read_desktop_workspace', { workspaceId: restoredWorkspaceId });
    const localConversation = bBefore.exchange.conversations.find(item => item.id === conversation.id);
    const pin = await invoke(port, 'mutate_desktop_domain', { args: {
      intentId: 'synthetic-selected-sync-root-b-pin', workspaceId: restoredWorkspaceId,
      entity: 'conversation', action: 'setPinned', objectId: conversation.id,
      expectedRevision: localConversation.revision, fields: { pinned: true },
    } });
    assert.ok(!pin.__error, JSON.stringify(pin));
    await until(() => documents.get(documentId)?.payload.records.find(item => item.kind === 'conversation')?.content.pinned === true, '置顶自动续同步', 75000);
    const favorites = await invoke(port, 'set_desktop_conversation_favorite', {
      workspaceId: restoredWorkspaceId, conversationId: conversation.id, favorite: true,
    });
    assert.ok(!favorites.__error && favorites.includes(conversation.id), JSON.stringify(favorites));
    await until(() => documents.get(documentId)?.payload.records.find(item => item.kind === 'conversation')?.content.favorited === true, '收藏自动续同步', 75000);
    const archive = await invoke(port, 'mutate_desktop_domain', { args: {
      intentId: 'synthetic-selected-sync-root-b-archive', workspaceId: restoredWorkspaceId,
      entity: 'conversation', action: 'archive', objectId: conversation.id,
      expectedRevision: pin.revision, fields: {},
    } });
    assert.ok(!archive.__error, JSON.stringify(archive));
    await until(() => {
      const content = documents.get(documentId)?.payload.records.find(item => item.kind === 'conversation')?.content;
      return content?.archived === true && content?.pinned === false;
    }, '归档自动续同步', 75000);
    stop();
    port = await start(rootA);
    await until(() => evaluate(port, 'Boolean(window.__TAURI__?.core?.invoke)'), '第一根元数据回读原生 IPC');
    const restoredMetadata = await invoke(port, 'restore_all_desktop_cloud_conversations');
    assert.ok(!restoredMetadata.__error, JSON.stringify(restoredMetadata));
    const aReadback = await invoke(port, 'read_desktop_workspace', { workspaceId: imported.summary.id });
    const aConversation = aReadback.exchange.conversations.find(item => item.id === conversation.id);
    assert.equal(aConversation.archived, true);
    assert.equal(aConversation.pinned, false);
    const aFavorites = await invoke(port, 'read_desktop_favorite_conversation_ids', { workspaceId: imported.summary.id });
    assert.ok(aFavorites.includes(conversation.id), '第一根收藏状态必须可见回读');
    assert.deepEqual(serverErrors, []);
    result = { candidateSha256: receipt.sha256, rootA, rootB, documentId,
      selectedConversationViaVisibleContextMenu: true, pinAutoSynced: true,
      favoriteAutoSynced: true, archiveAutoSynced: true,
      otherRootReadback: true, stoppedPids };
  } else if (branchEdit) {
    const beforeEdit = await invoke(port, 'read_desktop_workspace', { workspaceId: restoredWorkspaceId });
    const localConversation = beforeEdit.exchange.conversations.find(item => item.id === conversation.id);
    const originalUser = localConversation.messages.find(item => item.role === 'user');
    const originalLeaf = localConversation.currentLeafId;
    assert.ok(originalUser?.id && originalLeaf);
    const editedText = '第二根合成编辑分支问题';
    const beforeRemoteRevision = documents.get(documentId).revision;
    const edit = await invoke(port, 'mutate_desktop_domain', { args: {
      intentId: 'synthetic-selected-sync-root-b-edit-branch', workspaceId: restoredWorkspaceId,
      entity: 'conversation', action: 'editUserMessage', objectId: conversation.id,
      expectedRevision: localConversation.revision, fields: { messageId: originalUser.id, text: editedText },
    } });
    assert.ok(!edit.__error, JSON.stringify(edit));
    await until(() => {
      const remote = documents.get(documentId);
      const record = remote?.payload.records.find(item => item.kind === 'conversation');
      return remote?.revision > beforeRemoteRevision && record?.content.messages.some(item => item.blocks?.some(block => block.text === editedText));
    }, '编辑分支自动续同步', 75000);
    const afterEdit = await invoke(port, 'read_desktop_workspace', { workspaceId: restoredWorkspaceId });
    const editedConversation = afterEdit.exchange.conversations.find(item => item.id === conversation.id);
    const editedLeaf = editedConversation.currentLeafId;
    assert.notEqual(editedLeaf, originalLeaf);
    const beforeSwitchRevision = documents.get(documentId).revision;
    const switched = await invoke(port, 'mutate_desktop_domain', { args: {
      intentId: 'synthetic-selected-sync-root-b-switch-old-leaf', workspaceId: restoredWorkspaceId,
      entity: 'conversation', action: 'switchLeaf', objectId: conversation.id,
      expectedRevision: editedConversation.revision, fields: { messageId: originalLeaf },
    } });
    assert.ok(!switched.__error, JSON.stringify(switched));
    await until(() => {
      const remote = documents.get(documentId);
      const record = remote?.payload.records.find(item => item.kind === 'conversation');
      return remote?.revision > beforeSwitchRevision && record?.content.currentLeafId === originalLeaf;
    }, '切回旧叶自动续同步', 75000);
    stop();
    port = await start(rootA);
    await until(() => evaluate(port, 'Boolean(window.__TAURI__?.core?.invoke)'), '第一根分支回读原生 IPC');
    const restoredBranches = await invoke(port, 'restore_all_desktop_cloud_conversations');
    assert.ok(!restoredBranches.__error, JSON.stringify(restoredBranches));
    const aReadback = await invoke(port, 'read_desktop_workspace', { workspaceId: imported.summary.id });
    const aConversation = aReadback.exchange.conversations.find(item => item.id === conversation.id);
    assert.ok(aConversation.messages.some(item => item.blocks?.some(block => block.text === editedText)));
    assert.ok(aConversation.messages.some(item => item.id === conversation.currentLeafId), '第一根原有完整助手节点不得因远端缺附件而删除');
    assert.equal(aConversation.currentLeafId, conversation.currentLeafId, '第一根不得回退到旧问题并隐藏本机完整回答');
    assert.deepEqual(serverErrors, []);
    result = { candidateSha256: receipt.sha256, rootA, rootB, documentId,
      selectedConversationViaVisibleContextMenu: true, editBranchAutoSynced: true,
      switchOldLeafAutoSynced: true, otherRootRestoredBranchAndPreservedLocalAnswer: true,
      remoteRevision: documents.get(documentId).revision, stoppedPids };
  } else if (deletePropagation) {
    const beforeDelete = await invoke(port, 'read_desktop_workspace', { workspaceId: restoredWorkspaceId });
    const localConversation = beforeDelete.exchange.conversations.find(item => item.id === conversation.id);
    const removed = await invoke(port, 'mutate_desktop_domain', { args: {
      intentId: 'synthetic-selected-sync-root-b-delete', workspaceId: restoredWorkspaceId,
      entity: 'conversation', action: 'softDelete', objectId: conversation.id,
      expectedRevision: localConversation.revision, fields: {},
    } });
    assert.ok(!removed.__error, JSON.stringify(removed));
    await until(() => !documents.has(documentId), '第二根删除自动同步', 75000);
    await until(async () => (await invoke(port, 'read_desktop_account_sync'))?.selectedConversationCount === 0, '第二根删除回执清理');
    const localB = await invoke(port, 'read_desktop_workspace', { workspaceId: restoredWorkspaceId });
    assert.equal(localB.exchange.conversations.find(item => item.id === conversation.id)?.deleted, true);
    stop();
    port = await start(rootA);
    await until(() => evaluate(port, 'Boolean(window.__TAURI__?.core?.invoke)'), '第一根删除回读 IPC');
    const refreshed = await invoke(port, 'restore_all_desktop_cloud_conversations');
    assert.ok(!refreshed.__error, JSON.stringify(refreshed));
    assert.equal(refreshed.failedCount, 0);
    assert.equal(refreshed.restored.length, 0);
    assert.equal((await invoke(port, 'read_desktop_account_sync')).selectedConversationCount, 0);
    const localA = await invoke(port, 'read_desktop_workspace', { workspaceId: imported.summary.id });
    assert.ok(localA.exchange.conversations.some(item => item.id === conversation.id));
    await new Promise(done => setTimeout(done, 1500));
    assert.equal(documents.size, 0, '第一根不得自动复活已删除云端副本');
    stop();
    port = await start(rootA);
    await until(() => evaluate(port, 'Boolean(window.__TAURI__?.core?.invoke)'), '第一根冷启删除回读 IPC');
    assert.equal((await invoke(port, 'read_desktop_account_sync')).selectedConversationCount, 0);
    assert.equal(documents.size, 0);
    assert.deepEqual(serverErrors, []);
    result = { candidateSha256: receipt.sha256, rootA, rootB, documentId,
      secondRootDeletedRemote: true, secondRootLocalRecyclePreserved: true,
      firstRootLocalBodyPreserved: true, firstRootReceiptPruned: true,
      coldRestartDidNotResurrectRemote: true, remoteDocumentCount: documents.size,
      rpcCounts: Object.fromEntries([...new Set(requests)].map(path => [path, requests.filter(item => item === path).length])), stoppedPids };
  } else if (concurrentMessages || casRace || casExhaustion) {
    const firstText = '第一根并发消息';
    const secondText = '第二根并发消息';
    rejectCommits = true;
    const bBefore = await invoke(port, 'read_desktop_workspace', { workspaceId: restoredWorkspaceId });
    const bRevision = bBefore.exchange.conversations.find(item => item.id === conversation.id).revision;
    const bAppend = await invoke(port, 'mutate_desktop_domain', { args: {
      intentId: 'synthetic-selected-sync-root-b-concurrent', workspaceId: restoredWorkspaceId,
      entity: 'conversation', action: 'appendMessage', objectId: conversation.id,
      expectedRevision: bRevision, fields: { text: secondText, role: 'user' },
    } });
    assert.ok(!bAppend.__error, JSON.stringify(bAppend));
    const bOffline = await invoke(port, 'sync_selected_desktop_conversation', { workspaceId: restoredWorkspaceId, conversationId: conversation.id });
    assert.ok(bOffline.__error, JSON.stringify(bOffline));
    await until(() => rejectedCommitCount >= 1, '第二根离线消息待传', 75000);
    stop();
    rejectCommits = false;
    port = await start(rootA);
    await until(() => evaluate(port, 'Boolean(window.__TAURI__?.core?.invoke)'), '第一根并发消息 IPC');
    const aBefore = await invoke(port, 'read_desktop_workspace', { workspaceId: imported.summary.id });
    const aRevision = aBefore.exchange.conversations.find(item => item.id === conversation.id).revision;
    const aAppend = await invoke(port, 'mutate_desktop_domain', { args: {
      intentId: 'synthetic-selected-sync-root-a-concurrent', workspaceId: imported.summary.id,
      entity: 'conversation', action: 'appendMessage', objectId: conversation.id,
      expectedRevision: aRevision, fields: { text: firstText, role: 'user' },
    } });
    assert.ok(!aAppend.__error, JSON.stringify(aAppend));
    const aSync = await invoke(port, 'sync_selected_desktop_conversation', { workspaceId: imported.summary.id, conversationId: conversation.id });
    assert.ok(!aSync.__error, JSON.stringify(aSync));
    await until(() => JSON.stringify(documents.get(documentId)?.payload).includes(firstText), '第一根消息上云', 75000);
    stop();
    port = await start(rootB);
    await until(() => evaluate(port, 'Boolean(window.__TAURI__?.core?.invoke)'), '第二根并发合并 IPC');
    if (casRace) staleReadOnce = envelope;
    if (casExhaustion) forcedStaleCommits = 3;
    const bSync = await invoke(port, 'sync_selected_desktop_conversation', { workspaceId: restoredWorkspaceId, conversationId: conversation.id });
    if (casExhaustion) {
      assert.match(bSync.__error || '', /自动继续同步/, JSON.stringify(bSync));
      const pending = JSON.parse(execFileSync('python', ['-c',
        'import json,sqlite3,sys; c=sqlite3.connect(sys.argv[1]); print(json.dumps(c.execute("SELECT stage,attempt FROM desktop_sync_jobs WHERE job_id LIKE \'content-pending:%\'").fetchall()))',
        join(rootB, 'workspace.sqlite3')], { encoding: 'utf8', timeout: 10000 }));
      assert.deepEqual(pending, [['CONTENT_PENDING', 1]]);
      stop();
      port = await start(rootB);
      await until(() => evaluate(port, 'Boolean(window.__TAURI__?.core?.invoke)'), '第二根冲突耗尽冷启 IPC');
    } else assert.ok(!bSync.__error, JSON.stringify(bSync));
    await until(() => [firstText, secondText].every(text => JSON.stringify(documents.get(documentId)?.payload).includes(text)), '双消息并集上云', 75000);
    if (casExhaustion) await until(() => {
      const remaining = JSON.parse(execFileSync('python', ['-c',
        'import json,sqlite3,sys; c=sqlite3.connect(sys.argv[1]); print(json.dumps(c.execute("SELECT stage FROM desktop_sync_jobs WHERE job_id LIKE \'content-pending:%\'").fetchall()))',
        join(rootB, 'workspace.sqlite3')], { encoding: 'utf8', timeout: 10000 }));
      return remaining.length === 0;
    }, '后台续同步待办清理');
    const bMerged = await invoke(port, 'read_desktop_workspace', { workspaceId: restoredWorkspaceId });
    const bMessages = bMerged.exchange.conversations.find(item => item.id === conversation.id).messages;
    assert.ok([firstText, secondText].every(text => bMessages.some(item => item.blocks?.some(block => block.text === text))));
    stop();
    port = await start(rootB);
    await until(() => evaluate(port, 'Boolean(window.__TAURI__?.core?.invoke)'), '第二根合并冷启 IPC');
    const bCold = await invoke(port, 'read_desktop_workspace', { workspaceId: restoredWorkspaceId });
    const bColdMessages = bCold.exchange.conversations.find(item => item.id === conversation.id).messages;
    assert.ok([firstText, secondText].every(text => bColdMessages.some(item => item.blocks?.some(block => block.text === text))));
    stop();
    port = await start(rootA);
    await until(() => evaluate(port, 'Boolean(window.__TAURI__?.core?.invoke)'), '第一根并发回读 IPC');
    const aRestore = await invoke(port, 'restore_all_desktop_cloud_conversations');
    assert.ok(!aRestore.__error && aRestore.failedCount === 0, JSON.stringify(aRestore));
    const aMerged = await invoke(port, 'read_desktop_workspace', { workspaceId: imported.summary.id });
    const aMessages = aMerged.exchange.conversations.find(item => item.id === conversation.id).messages;
    assert.ok([firstText, secondText].every(text => aMessages.some(item => item.blocks?.some(block => block.text === text))));
    assert.deepEqual(serverErrors, []);
    if (casRace) assert.equal(staleRejectionCount, 1, '应真实触发一次 CAS 过期拒绝');
    if (casExhaustion) assert.equal(staleRejectionCount, 3, '应真实触发三次 CAS 过期拒绝');
    result = { candidateSha256: receipt.sha256, rootA, rootB, documentId,
      concurrentMessagesPreservedInBothRoots: true, remoteRevision: documents.get(documentId)?.revision,
      rejectedCommitCount, staleRejectionCount, stoppedPids };
  } else {
  assert.equal(await evaluate(port, `(()=>{const row=document.querySelector(${JSON.stringify(rowSelector)});if(!row)return false;row.dispatchEvent(new MouseEvent('contextmenu',{bubbles:true,cancelable:true}));return true})()`), true);
  await until(() => evaluate(port, 'Boolean(document.querySelector(`[data-action="context-menu-rename"]`))'), '恢复会话改名菜单');
  assert.equal(await click(port, '[data-action="context-menu-rename"]'), true);
  await until(() => evaluate(port, 'Boolean(document.querySelector(`#conversation-rename`))'), '会话改名弹窗');
  const renamedTitle = '第二根合成改名';
  assert.equal(await evaluate(port, `(()=>{const input=document.querySelector('#conversation-rename');if(!input)return false;input.value=${JSON.stringify(renamedTitle)};input.dispatchEvent(new Event('input',{bubbles:true}));return true})()`), true);
  if (titleConflict) rejectCommits = true;
  assert.equal(await click(port, '[data-action="save-conversation-rename"]'), true);
  if (titleConflict) {
    await until(() => rejectedCommitCount >= 1, '第二根离线改名待同步');
    const localB = await invoke(port, 'read_desktop_workspace', { workspaceId: restoredWorkspaceId });
    assert.equal(localB.exchange.conversations.find(item => item.id === conversation.id)?.title, renamedTitle);
    stop();
    rejectCommits = false;
    port = await start(rootA);
    await until(() => evaluate(port, 'Boolean(window.__TAURI__?.core?.invoke)'), '第一根并发改名 IPC');
    await until(() => evaluate(port, `Boolean(document.querySelector(${JSON.stringify(workspaceSelector)}))`), '第一根工作区入口');
    assert.equal(await click(port, workspaceSelector), true);
    await until(() => evaluate(port, `Boolean(document.querySelector(${JSON.stringify(rowSelector)}))`), '第一根会话入口');
    assert.equal(await evaluate(port, `(()=>{const row=document.querySelector(${JSON.stringify(rowSelector)});row.dispatchEvent(new MouseEvent('contextmenu',{bubbles:true,cancelable:true}));return true})()`), true);
    await until(() => evaluate(port, 'Boolean(document.querySelector(`[data-action="context-menu-rename"]`))'), '第一根改名菜单');
    assert.equal(await click(port, '[data-action="context-menu-rename"]'), true);
    await until(() => evaluate(port, 'Boolean(document.querySelector(`#conversation-rename`))'), '第一根改名弹窗');
    const firstTitle = '第一根并发改名';
    assert.equal(await evaluate(port, `(()=>{const input=document.querySelector('#conversation-rename');input.value=${JSON.stringify(firstTitle)};input.dispatchEvent(new Event('input',{bubbles:true}));return true})()`), true);
    assert.equal(await click(port, '[data-action="save-conversation-rename"]'), true);
    await until(() => documents.get(documentId)?.revision === 2, '第一根并发改名已上云');
    assert.equal(documents.get(documentId).payload.records.find(item => item.kind === 'conversation').content.title, firstTitle);
    stop();
    port = await start(rootB);
    await until(() => evaluate(port, 'Boolean(window.__TAURI__?.core?.invoke)'), '第二根冲突回读 IPC');
    const conflict = await invoke(port, 'sync_selected_desktop_conversation', { workspaceId: restoredWorkspaceId, conversationId: conversation.id });
    assert.match(conflict.__error || '', /标题版本冲突/, JSON.stringify(conflict));
    assert.equal(documents.get(documentId)?.revision, 2);
    assert.equal(documents.get(documentId).payload.records.find(item => item.kind === 'conversation').content.title, firstTitle);
    const afterConflict = await invoke(port, 'read_desktop_workspace', { workspaceId: restoredWorkspaceId });
    const preserved = afterConflict.exchange.conversations.find(item => item.id === conversation.id);
    assert.equal(preserved?.title, renamedTitle);
    assert.ok(preserved?.messages.some(item => item.blocks?.some(block => block.text === '非敏感夹具文本。')));
    assert.equal((await invoke(port, 'read_desktop_account_sync', {}, 'WORK')).selectedConversationCount, 0);
    assert.deepEqual(serverErrors, []);
    result = { candidateSha256: receipt.sha256, rootA, rootB, documentId,
      equalTitleRevisionConflictPreservedBoth: true, firstTitle, secondTitle: renamedTitle,
      remoteRevision: documents.get(documentId)?.revision, stoppedPids };
  } else {
  await until(() => documents.get(documentId)?.revision === 2, '第二根改名续同步');
  await until(() => evaluate(port, 'globalThis.__nanfengDesktopWorkState?.status === "会话已重命名。 已同步到云端。"'), '改名云端确认反馈');
  assert.equal(documents.get(documentId).payload.records.find(item => item.kind === 'conversation').content.title, renamedTitle);
  await until(() => evaluate(port, 'Boolean(document.querySelector(`[data-action="show-settings"]`))'), '冷启设置入口');
  assert.equal(await click(port, '[data-action="show-settings"]'), true);
  await until(() => evaluate(port, 'Boolean(document.querySelector(`[data-action="open-settings-page"][data-page="account"]`))'), '账号设置入口');
  assert.equal(await click(port, '[data-action="open-settings-page"][data-page="account"]'), true);
  await until(() => evaluate(port, 'document.body.innerText.includes("同步内容可由南枫云服务读取")'), '准确的账号同步隐私说明');
  assert.equal(await evaluate(port, 'document.body.innerText.includes("传输保护与账号隔离不等于端到端加密")'), true);
  stop();

  port = await start(rootA, true);
  await until(() => evaluate(port, 'Boolean(window.__TAURI__?.core?.invoke)'), '第一根冷启原生 IPC');
  const aRestored = await invoke(port, 'restore_all_desktop_cloud_conversations');
  assert.ok(!aRestored.__error, aRestored.__error);
  if (aRestored.failedCount) {
    const secondAttempt = await invoke(port, 'restore_all_desktop_cloud_conversations');
    throw new Error(`合成首根恢复首试失败；${JSON.stringify({ rootA, rootB, first: aRestored, second: secondAttempt })}`);
  }
  const aWorkspace = await invoke(port, 'read_desktop_workspace', { workspaceId: imported.summary.id });
  const aConversation = aWorkspace.exchange.conversations.find(item => item.id === conversation.id);
  assert.equal(aConversation?.title, renamedTitle, JSON.stringify({ rootA, rootB, aRestored, localTitleRevision: aConversation?.titleRevision,
    remoteRevision: documents.get(documentId)?.revision, remoteTitle: documents.get(documentId)?.payload.records.find(item => item.kind === 'conversation')?.content.title,
    remoteTitleRevision: documents.get(documentId)?.payload.records.find(item => item.kind === 'conversation')?.content.titleRevision }));
  assert.equal(aConversation?.titleRevision, 1);
  assert.equal((await invoke(port, 'read_desktop_account_sync')).selectedConversationCount, 1);
  const appendedText = '第一根合成增量消息';
  const appended = await invoke(port, 'mutate_desktop_domain', { args: {
    intentId: 'synthetic-selected-sync-root-a-append', workspaceId: imported.summary.id,
    entity: 'conversation', action: 'appendMessage', objectId: conversation.id,
    expectedRevision: aConversation.revision, fields: { text: appendedText, role: 'user' },
  } });
  assert.ok(!appended.__error, appended.__error);
  const afterAppend = await invoke(port, 'read_desktop_workspace', { workspaceId: imported.summary.id });
  const afterAppendConversation = afterAppend.exchange.conversations.find(item => item.id === conversation.id);
  const selection = await invoke(port, 'read_desktop_p6g_selection', {
    workspaceId: imported.summary.id, conversationId: conversation.id,
  });
  const disabledSearch = await invoke(port, 'set_desktop_conversation_preferences', { args: {
    workspaceId: imported.summary.id, conversationId: conversation.id,
    expectedRevision: selection.conversationOverride.revision,
    toneOverride: null, webSearchOverride: false,
  } });
  assert.ok(!disabledSearch.__error, disabledSearch.__error);
  const measuredPrompt = 'W16B 合成计量续聊';
  if (offlineRecovery || liveRecovery) rejectCommits = true;
  const sent = await invoke(port, 'submit_desktop_ordinary_chat', { args: {
    workspaceId: imported.summary.id, conversationId: conversation.id, projectId: null,
    expectedRevision: afterAppendConversation.revision, text: measuredPrompt, attachmentIds: [],
    modelId: null, toneOverride: null, webSearchOverride: null,
    egressAuthorization: { approvedAtMs: Date.now(), disclosureVersion: 'normal-chat-egress-v1', clientSubmissionId: randomUUID() },
  } });
  assert.equal(sent.state, 'COMPLETED', JSON.stringify(sent));
  assert.equal(providerRequests.length, 1);
  if (offlineRecovery || liveRecovery) {
    await until(() => rejectedCommitCount >= 1, '合成网关拒绝自动续同步', 75000);
    assert.ok(!documents.get(documentId)?.payload.records.find(item => item.kind === 'conversation')
      ?.content.messages.some(item => item.blocks?.some(block => block.text === providerAnswer)));
    const job = JSON.parse(execFileSync('python', ['-c',
      'import json,sqlite3,sys; c=sqlite3.connect(sys.argv[1]); rows=c.execute("SELECT stage,attempt FROM desktop_sync_jobs WHERE job_id LIKE \'content-pending:%\'").fetchall(); print(json.dumps(rows))',
      join(rootA, 'workspace.sqlite3')], { encoding: 'utf8', timeout: 10000 }));
    assert.deepEqual(job, [['CONTENT_PENDING', 1]], '失败后待传任务未持久保留');
    rejectCommits = false;
    if (offlineRecovery) {
      stop();
      port = await start(rootA, true);
      await until(() => evaluate(port, 'Boolean(window.__TAURI__?.core?.invoke)'), '离线后第一根冷启原生 IPC');
    }
  }
  await until(() => documents.get(documentId)?.payload.records.find(item => item.kind === 'conversation')
    ?.content.messages.some(item => item.blocks?.some(block => block.text === providerAnswer)),
  '已选普通回复自动续同步', offlineRecovery || liveRecovery ? 120000 : 75000);
  const continued = await invoke(port, 'sync_selected_desktop_conversation', {
    workspaceId: imported.summary.id, conversationId: conversation.id, continuation: true,
  });
  assert.equal(continued.status, 'UP_TO_DATE', JSON.stringify(continued));
  const remoteMessages = documents.get(documentId)?.payload.records.find(item => item.kind === 'conversation')?.content.messages;
  const remoteAnswer = remoteMessages?.find(item => item.blocks?.some(block => block.text === providerAnswer));
  assert.ok(remoteAnswer, '续同步文档缺合成计量回答');
  assert.equal(remoteAnswer.modelUsage?.inputTokens, 11);
  assert.equal(remoteAnswer.modelUsage?.outputTokens, 7);
  assert.equal(remoteAnswer.modelUsage?.costTotalMicros, 12);
  stop();

  port = await start(rootB);
  await until(() => evaluate(port, 'Boolean(window.__TAURI__?.core?.invoke)'), '第二根增量回读原生 IPC');
  const bIncremental = await invoke(port, 'restore_all_desktop_cloud_conversations');
  assert.ok(!bIncremental.__error, bIncremental.__error);
  const bWorkspace = await invoke(port, 'read_desktop_workspace', { workspaceId: restoredWorkspaceId });
  const bConversation = bWorkspace.exchange.conversations.find(item => item.id === conversation.id);
  assert.equal(bConversation?.title, renamedTitle);
  assert.equal(bConversation?.titleRevision, 1);
  assert.ok(bConversation?.messages.some(item => item.blocks?.some(block => block.text === appendedText)));
  const restoredAnswer = bConversation?.messages.find(item => item.id === remoteAnswer.id);
  assert.ok(restoredAnswer?.blocks?.some(block => block.text === providerAnswer));
  assert.equal(restoredAnswer?.usage?.inputTokens, 11);
  assert.equal(restoredAnswer?.usage?.outputTokens, 7);
  assert.equal(restoredAnswer?.chargeMicros, 12);
  assert.equal(restoredAnswer?.modelSnapshot?.modelId, remoteAnswer.modelUsage.modelId);
  stop();
  port = await start(rootB);
  await until(() => evaluate(port, 'Boolean(window.__TAURI__?.core?.invoke)'), '第二根计量冷启原生 IPC');
  const coldMeasured = await invoke(port, 'read_desktop_workspace', { workspaceId: restoredWorkspaceId });
  const coldAnswer = coldMeasured.exchange.conversations.find(item => item.id === conversation.id)
    ?.messages.find(item => item.id === remoteAnswer.id);
  assert.equal(coldAnswer?.usage?.inputTokens, 11);
  assert.equal(coldAnswer?.usage?.outputTokens, 7);
  assert.equal(coldAnswer?.chargeMicros, 12);
  assert.equal(coldAnswer?.modelSnapshot?.modelId, remoteAnswer.modelUsage.modelId);
  assert.equal(providerRequests.length, 1);
  assert.deepEqual(serverErrors, []);
  result = { candidateSha256: receipt.sha256, rootA, rootB, documentId,
    accountLoginInBothVisibleUis: true, v1ExchangeViaWindowsSystemPicker: true,
    selectedConversationViaVisibleContextMenu: true,
    firstUploadAndReadback: true, syncFeedbackVerified: true, secondRootRestore: true,
    secondRootColdRestart: true, secondRootVisibleRenameAndSync: true,
    firstRootTitleRestore: true, firstRootAppendAndSecondRootMerge: true,
    measuredAnswerSyncedAndRestored: true, secondRootMeasuredAnswerColdRestart: true,
    ordinaryReplyAutoSynced: true, mismatchedDirectRejected: mismatchedDirect,
    offlineRecovery, liveRecovery, rejectedCommitCount,
    providerRequestCount: providerRequests.length,
    accountPrivacyCopyVerified: true,
    syncedBlocksTextOnly: true, workAreaSelectedCount: 0,
    rpcCounts: Object.fromEntries([...new Set(requests)].map(path => [path, requests.filter(item => item === path).length])), stoppedPids };
  }
  }
} catch (error) {
  primaryError = error;
  throw error;
} finally {
  holdPendingRead = false; heldPendingRead?.(); heldPendingRead = null;
  let cleanupError;
  try { stop(); }
  catch (error) { cleanupError = error; }
  await new Promise(done => server.close(done));
  await new Promise(done => provider.close(done));
  if (cleanupError) {
    if (primaryError) console.error(`隔离进程清理失败：${cleanupError}`);
    else throw cleanupError;
  }
}
if (result) console.log(JSON.stringify(result));
