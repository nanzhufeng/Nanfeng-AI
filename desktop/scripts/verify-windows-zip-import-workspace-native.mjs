import assert from 'node:assert/strict';
import { createHash } from 'node:crypto';
import { execFileSync, spawn } from 'node:child_process';
import { copyFile, mkdir, readFile, writeFile } from 'node:fs/promises';
import { dirname, join, resolve, sep } from 'node:path';
import { fileURLToPath } from 'node:url';

if (process.platform !== 'win32') throw new Error('仅供 Windows 隔离候选验收。');
const desktopRoot = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const rootParent = resolve(process.argv.slice(2).find(arg => !arg.startsWith('--')) || 'D:\\CodexTemp');
const officialAttachmentOnly = process.argv.includes('--official-attachment-only');
const officialOfficeKind = process.argv.includes('--official-xlsx') ? 'xlsx' : process.argv.includes('--official-pptx') ? 'pptx' : process.argv.includes('--official-office') ? 'docx' : '';
const officialOffice = Boolean(officialOfficeKind);
const officialNoName = process.argv.includes('--official-no-name');
if (officialNoName && !officialOffice) throw new Error('--official-no-name 仅用于官方 Office .dat 验收');
const officialDat = process.argv.includes('--official-dat') || officialAttachmentOnly || officialOffice;
const officialName = officialNoName ? 'ChatGPT 导入附件-synthetic-media' : officialOffice ? `report.${officialOfficeKind}` : 'photo.png';
const officialAttachment = officialNoName ? { id: 'synthetic-media' } : { id: 'synthetic-media', name: officialName };
const officeMime = { docx: 'application/vnd.openxmlformats-officedocument.wordprocessingml.document', xlsx: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet', pptx: 'application/vnd.openxmlformats-officedocument.presentationml.presentation' };
const officialMime = officialOffice ? officeMime[officialOfficeKind] : 'image/png';
const manualDat = process.argv.includes('--manual-dat');
const manualLink = process.argv.includes('--manual-link') || manualDat;
const manualCorruptExisting = process.argv.includes('--manual-corrupt-existing');
if (manualCorruptExisting && !manualDat) throw new Error('--manual-corrupt-existing 需 --manual-dat 合成 PNG');
const receipt = JSON.parse(await readFile(join(desktopRoot, 'output/windows-acceptance/current.json'), 'utf8'));
assert.equal(receipt.identifier, 'com.nanzhufeng.ai.desktop.windowsacceptance');
const executable = resolve(desktopRoot, 'output/windows-acceptance', receipt.sha256, 'NanfengAI-Windows-Acceptance.exe');
const runningExecutable = resolve(desktopRoot, 'output/windows-acceptance-binaries', receipt.sha256, 'nanfeng-ai-desktop-spike.exe');
assert.equal(resolve(receipt.executable), executable);
assert.equal(createHash('sha256').update(await readFile(executable)).digest('hex').toUpperCase(), receipt.sha256);
const quote = value => `'${String(value).replaceAll("'", "''")}'`;
const ps = script => execFileSync('pwsh', ['-NoProfile', '-Command', script], { encoding: 'utf8', timeout: 60000 }).trim();
async function reservePort() {
  const server = (await import('node:http')).createServer();
  await new Promise(done => server.listen(0, '127.0.0.1', done));
  const port = server.address().port;
  await new Promise(done => server.close(done));
  return port;
}
let launch = null;
let root = '';
const stoppedPids = [];
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
      child.stdout.destroy();
      child.stderr.destroy();
      child.unref();
    });
    child.stderr.on('data', chunk => { error += chunk.toString(); });
    child.on('error', cause => { clearTimeout(timer); fail(cause); });
    child.on('exit', code => { if (!output.includes('"pid"')) { clearTimeout(timer); fail(new Error(`启动失败 ${code}：${error}`)); } });
  });
  assert.equal(result.sha256, receipt.sha256);
  assert.equal(resolve(result.executable), runningExecutable);
  launch = result;
  root = resolve(result.root);
  assert.ok(root.startsWith(rootParent + sep));
  assert.equal(JSON.parse(await readFile(join(root, '.nanfeng-windows-acceptance.json'), 'utf8')).identifier, receipt.identifier);
  return port;
}
function stop() {
  if (!launch) return;
  const pid = Number(launch.pid);
  ps(`$p=Get-CimInstance Win32_Process -Filter "ProcessId=${pid}"; if ($p) { if ([IO.Path]::GetFullPath([string]$p.ExecutablePath) -ne ${quote(runningExecutable)} -or (Get-FileHash -LiteralPath ${quote(runningExecutable)} -Algorithm SHA256).Hash -ne ${quote(receipt.sha256)}) { throw 'PID identity mismatch' }; Stop-Process -Id ${pid} -Force -ErrorAction Stop }`);
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
async function invoke(port, area, command, args = {}) {
  const value = await evaluate(port, `window.__TAURI__.core.invoke(${JSON.stringify(command)},${JSON.stringify(args)},${JSON.stringify({ headers: { 'x-nanfeng-data-area': area } })}).catch(error=>({__error:String(error)}))`);
  if (value?.__error) throw new Error(`${command}: ${value.__error}`);
  return value;
}
async function until(read, label) {
  const deadline = Date.now() + 30000;
  while (Date.now() < deadline) {
    try { const value = await read(); if (value) return value; } catch { /* Startup or redraw. */ }
    await new Promise(done => setTimeout(done, 200));
  }
  throw new Error(`${label} 未出现`);
}
const click = (port, selector) => evaluate(port, `(()=>{const node=document.querySelector(${JSON.stringify(selector)});if(!node)return false;node.click();return true})()`);
const dialogOpen = () => {
  const raw = ps(`& ${quote(join(desktopRoot, 'scripts/inspect-windows-file-dialog.ps1'))} -ProcessId ${Number(launch.pid)}`);
  const windows = raw ? JSON.parse(raw) : [];
  return windows.some(item => item.children?.some(child => child.class === '#32770'));
};
function syntheticTaskStatus(taskId, nextStatus = '') {
  const script = 'import sqlite3,sys; c=sqlite3.connect(sys.argv[1]); task=sys.argv[2]; next_status=sys.argv[3]; rows=c.execute("select status from p6k_zip_import_tasks where id=?",(task,)).fetchall(); assert len(rows)==1; c.execute("update p6k_zip_import_tasks set status=? where id=?",(next_status,task)) if next_status else None; c.commit(); print(c.execute("select status from p6k_zip_import_tasks where id=?",(task,)).fetchone()[0]); c.close()';
  return execFileSync('python', ['-c', script, join(root, 'workspace.sqlite3'), taskId, nextStatus], { encoding: 'utf8', timeout: 10000 }).trim();
}
function syntheticOfficialLedgerRevisions() {
  const script = 'import sqlite3,sys,json; c=sqlite3.connect(sys.argv[1]); print(json.dumps(c.execute("select source_object_id,revision from p6k_zip_official_identity_ledger where entity_kind=\'ATTACHMENT\' order by source_object_id").fetchall())); c.close()';
  return JSON.parse(execFileSync('python', ['-c', script, join(root, 'workspace.sqlite3')], { encoding: 'utf8', timeout: 10000 }));
}

let result;
try {
  let port = await start();
  await until(() => evaluate(port, 'Boolean(window.__TAURI__?.core?.invoke)'), 'native IPC');
  const workspaceId = (await invoke(port, 'CHAT', 'ensure_desktop_local_workspace')).summary.id;
  const fixtureDir = join(root, 'synthetic-zip-input');
  await mkdir(fixtureDir, { recursive: true });
  await writeFile(join(fixtureDir, 'conversations.json'), JSON.stringify([{ id: 'w14-zip-synthetic', title: 'W14 ZIP synthetic', create_time: 1, update_time: 2,
    mapping: { 'm-user': { parent: null, message: { author: { role: 'user' }, content: { parts: officialAttachmentOnly ? [{ asset_pointer: 'file-service://synthetic-media' }] : officialDat ? ['W14 synthetic user', { asset_pointer: 'file-service://synthetic-media' }] : ['W14 synthetic user'] },
      ...(officialDat ? { metadata: { attachments: [officialAttachment, { id: 'missing-export-asset', name: 'missing.pdf' }] } } : {}) } },
      'm-assistant': { parent: 'm-user', message: { author: { role: 'assistant' }, content: { parts: ['W14 synthetic assistant'] },
        ...(officialDat ? { metadata: { attachments: [officialAttachment] } } : {}) } } } }]), 'utf8');
  const mediaName = (manualDat || officialDat) ? 'synthetic-media.dat' : 'synthetic-media.txt';
  if (officialOffice) {
    const makeOffice = `import sys,zipfile
kind=sys.argv[2]
parts={
 'docx':[('word/document.xml',"<w:document xmlns:w='urn:test'><w:p><w:t>W14 synthetic office body</w:t></w:p></w:document>")],
 'xlsx':[('xl/worksheets/sheet1.xml',"<worksheet><sheetData><row><c t='inlineStr'><is><t>W14 synthetic office body</t></is></c></row></sheetData></worksheet>")],
 'pptx':[('ppt/slides/slide1.xml',"<p:sld xmlns:p='urn:p' xmlns:a='urn:a'><a:p><a:r><a:t>W14 synthetic office body</a:t></a:r></a:p></p:sld>")]
}
with zipfile.ZipFile(sys.argv[1],'w') as z:
 for name,body in parts[kind]: z.writestr(name,body)
`;
    execFileSync('python', ['-c', makeOffice, join(fixtureDir, mediaName), officialOfficeKind], { timeout: 10000 });
  } else if (manualDat || officialDat) await copyFile(join(desktopRoot, 'src-tauri/tests/fixtures/windows-image-red.png'), join(fixtureDir, mediaName));
  else if (manualLink) await writeFile(join(fixtureDir, mediaName), 'W14 synthetic ZIP media body', 'utf8');
  const zipPath = join(fixtureDir, 'synthetic-chatgpt.zip');
  const zipInputs = manualLink || officialDat ? `${quote(join(fixtureDir, 'conversations.json'))},${quote(join(fixtureDir, mediaName))}` : quote(join(fixtureDir, 'conversations.json'));
  ps(`Compress-Archive -LiteralPath ${zipInputs} -DestinationPath ${quote(zipPath)} -CompressionLevel Optimal -ErrorAction Stop`);
  await until(() => evaluate(port, 'Boolean(document.querySelector("[data-action=show-settings]"))'), 'settings button');
  assert.equal(await click(port, '[data-action="show-settings"]'), true);
  await until(() => evaluate(port, 'Boolean(document.querySelector("[data-action=open-settings-page][data-page=data]"))'), 'data settings entry');
  assert.equal(await click(port, '[data-action="open-settings-page"][data-page="data"]'), true);
  await until(() => evaluate(port, 'Boolean(document.querySelector("[data-action=select-p6k-chatgpt-zip]"))'), 'ZIP import entry');
  assert.equal(await click(port, '[data-action="select-p6k-chatgpt-zip"]'), true);
  await until(dialogOpen, 'system ZIP picker');
  const operated = JSON.parse(ps(`& ${quote(join(desktopRoot, 'scripts/operate-windows-file-dialog.ps1'))} -ProcessId ${Number(launch.pid)} -Action 'select' -SelectedPath ${quote(zipPath)} -AcceptanceRoot ${quote(root)}`));
  assert.equal(operated.action, 'select');
  await until(() => !dialogOpen(), 'ZIP picker closed');
  const task = await until(async () => {
    const value = await invoke(port, 'CHAT', 'read_latest_p6k_zip_import_task', { workspaceId });
    return value?.importedCount === 1 ? value : null;
  }, 'ZIP imported task');
  assert.equal(task.workspaceId, workspaceId);
  assert.equal(task.provider, 'CHATGPT');
  assert.equal(task.status, 'COMPLETED');
  assert.equal(await invoke(port, 'CHAT', 'read_latest_p6k_zip_import_task', { workspaceId: 'workspace-other' }), null);
  await until(() => evaluate(port, 'Boolean(document.querySelector("[data-action=open-settings-page][data-page=zip-import-results]"))'), 'ZIP result entry');
  assert.equal(await click(port, '[data-action="open-settings-page"][data-page="zip-import-results"]'), true);
  await until(() => evaluate(port, 'document.querySelector(".android-settings-main")?.textContent?.includes("W14 ZIP synthetic") || document.querySelector(".android-settings-main")?.textContent?.includes("ChatGPT")'), 'visible ZIP result');
  if (officialDat) {
    assert.deepEqual(task.officialAttachmentSummary, {
      referencedOccurrences: 3,
      recoverableUniqueAssets: 1,
      missingUniqueAssets: 1,
      fallbackNameUniqueAssets: Number(officialNoName),
      restoredUniqueAssets: 1,
      restoredOccurrences: 2,
    });
    const visibleReceipt = await until(() => evaluate(port, 'document.querySelector(".android-settings-main")?.textContent?.includes("本批导入时按官方引用自动恢复 1 个文件")'), 'ZIP 官方附件可见回执');
    assert.equal(visibleReceipt, true);
    assert.equal(await evaluate(port, 'document.querySelector(".android-settings-main")?.textContent?.includes("官方引用但导出包缺文件 1 个")'), true);
    assert.equal(task.manualAssets.length, 1);
    assert.equal(task.manualAssets[0].status, 'AUTO_LINKED');
    assert.equal(task.manualAssets[0].mimeType, officialMime);
    const conversationId = task.items[0].conversationId;
    const readTargets = async () => {
      const conversation = (await invoke(port, 'CHAT', 'read_desktop_workspace', { workspaceId })).exchange.conversations.find(item => item.id === conversationId);
      return conversation.messages.filter(message => ['user', 'assistant'].includes(message.role)).map(message => message.blocks.filter(block => block.kind === 'ASSET_REF'));
    };
    const targets = await readTargets();
    assert.deepEqual(targets.map(blocks => blocks.length), [1, 1]);
    assert.equal(targets[0][0].asset.sha256, targets[1][0].asset.sha256);
    assert.equal(targets[0][0].asset.displayName, officialName);
    stop();
    port = await start(true);
    await until(() => evaluate(port, 'Boolean(window.__TAURI__?.core?.invoke)'), 'ZIP 官方附件同根冷启');
    const coldTargets = await readTargets();
    assert.deepEqual(coldTargets.map(blocks => blocks.length), [1, 1]);
    if (officialOffice) {
      const preview = await invoke(port, 'CHAT', 'read_desktop_text_preview', { args: { workspaceId, attachmentId: coldTargets[0][0].asset.id } });
      assert.ok(JSON.stringify(preview).includes('W14 synthetic office body'));
    } else {
      const preview = await invoke(port, 'CHAT', 'read_desktop_image_preview', { args: { workspaceId, attachmentId: coldTargets[0][0].asset.id, fullSize: true } });
      assert.ok(preview.dataUrl.startsWith('data:image/png;base64,'));
    }
    const beforeReplayLedger = syntheticOfficialLedgerRevisions();
    const idempotentReplay = await invoke(port, 'CHAT', 'stage_p6k_zip_import_selected', { args: { workspaceId, provider: 'CHATGPT', selectedPath: zipPath } });
    assert.equal(idempotentReplay.status, 'COMPLETED');
    assert.deepEqual((await readTargets()).map(blocks => blocks.length), [1, 1]);
    assert.deepEqual(syntheticOfficialLedgerRevisions(), beforeReplayLedger);
    const coldConversation = (await invoke(port, 'CHAT', 'read_desktop_workspace', { workspaceId })).exchange.conversations.find(item => item.id === conversationId);
    const userMessage = coldConversation.messages.find(item => item.role === 'user');
    await invoke(port, 'CHAT', 'mutate_desktop_domain', { args: { intentId: 'intent-official-zip-delete-one', workspaceId, entity: 'conversation', action: 'removeAttachment', objectId: conversationId, expectedRevision: coldConversation.revision, fields: { messageId: userMessage.id, attachmentId: coldTargets[0][0].asset.id } } });
    assert.deepEqual((await readTargets()).map(blocks => blocks.length), [0, 1]);
    const replay = await invoke(port, 'CHAT', 'stage_p6k_zip_import_selected', { args: { workspaceId, provider: 'CHATGPT', selectedPath: zipPath } });
    assert.equal(replay.status, 'COMPLETED');
    assert.deepEqual((await readTargets()).map(blocks => blocks.length), [0, 1]);
    await invoke(port, 'CHAT', 'delete_p6k_zip_import_batch', { taskId: replay.id });
    const deleted = (await invoke(port, 'CHAT', 'read_desktop_workspace', { workspaceId })).exchange.conversations.find(item => item.id === conversationId);
    assert.equal(deleted.deleted, true);
    const afterDeleteReplay = await invoke(port, 'CHAT', 'stage_p6k_zip_import_selected', { args: { workspaceId, provider: 'CHATGPT', selectedPath: zipPath } });
    assert.equal(afterDeleteReplay.items[0].failure, 'USER_DELETED');
    assert.deepEqual((await readTargets()).map(blocks => blocks.length), [0, 1]);
    result = { candidateSha256: receipt.sha256, root, workspaceId, taskId: task.id, idempotentReplayTaskId: idempotentReplay.id, replayTaskId: replay.id, afterDeleteTaskId: afterDeleteReplay.id, conversationId, officialOccurrences: 3, recoveredOccurrences: 2, missingInExport: 1, exactDeleteNotRevived: true, batchDeleteNotRevived: true, unchangedLedgerOnReplay: true, coldReadback: true, stoppedPids };
  } else if (manualLink) {
    assert.equal(task.manualAssets.length, 1);
    assert.equal(task.manualAssets[0].status, 'UNMAPPED_REJECTED');
    if (manualDat) assert.equal(task.manualAssets[0].mimeType, 'image/png');
    await until(() => evaluate(port, 'Boolean(document.querySelector("[data-action=select-p6k-manual-asset]"))'), '可见未关联 ZIP 附件入口');
    assert.equal(await click(port, `[data-action="select-p6k-manual-asset"][data-asset-ordinal="${task.manualAssets[0].ordinal}"]`), true);
    await until(() => evaluate(port, 'Boolean(document.querySelector("[data-action=select-p6k-manual-target]"))'), '可见精确消息目标');
    const conversationId = task.items[0].conversationId;
    const conversation = (await invoke(port, 'CHAT', 'read_desktop_workspace', { workspaceId })).exchange.conversations.find(item => item.id === conversationId);
    const user = conversation.messages.find(item => item.role === 'user');
    assert.ok(user);
    if (manualCorruptExisting) {
      const sourceBytes = await readFile(join(fixtureDir, mediaName));
      const sha = createHash('sha256').update(sourceBytes).digest('hex');
      const target = join(root, 'assets', sha);
      await mkdir(dirname(target), { recursive: true });
      await writeFile(target, 'corrupt-existing-synthetic-private-asset');
      const before = (await invoke(port, 'CHAT', 'read_desktop_workspace', { workspaceId })).exchange;
      await assert.rejects(invoke(port, 'CHAT', 'link_p6k_zip_manual_asset', { args: {
        taskId: task.id, assetOrdinal: task.manualAssets[0].ordinal, workspaceId,
        conversationId, messageId: user.id,
      } }), /本地资产校验不一致/);
      assert.deepEqual((await invoke(port, 'CHAT', 'read_desktop_workspace', { workspaceId })).exchange, before);
      const failed = await invoke(port, 'CHAT', 'read_latest_p6k_zip_import_task', { workspaceId });
      assert.equal(failed.manualAssets[0].status, 'MANUAL_LINK_FAILED');
      const countScript = 'import sqlite3,sys; c=sqlite3.connect("file:"+sys.argv[1]+"?mode=ro",uri=True); print(c.execute("select count(*) from p6k_zip_asset_link_receipts where task_id=?",(sys.argv[2],)).fetchone()[0]); c.close()';
      assert.equal(execFileSync('python', ['-c', countScript, join(root, 'workspace.sqlite3'), task.id], { encoding: 'utf8', timeout: 10000 }).trim(), '0');
      await writeFile(target, sourceBytes);
    }
    assert.equal(await click(port, `[data-action="select-p6k-manual-target"][data-conversation-id="${conversationId}"]:not([data-message-id])`), true);
    await until(() => evaluate(port, 'Boolean(document.querySelector("[data-action=select-p6k-manual-target][data-message-id]"))'), '可见精确消息');
    assert.equal(await click(port, `[data-action="select-p6k-manual-target"][data-conversation-id="${conversationId}"][data-message-id="${user.id}"]`), true);
    await until(() => evaluate(port, 'Boolean(document.querySelector("[data-action=link-p6k-manual-asset]:not([disabled])"))'), '精确关联提交');
    assert.equal(await click(port, '[data-action="link-p6k-manual-asset"]'), true);
    const linked = await until(async () => {
      const projection = await invoke(port, 'CHAT', 'read_desktop_workspace', { workspaceId });
      const item = projection.exchange.conversations.find(row => row.id === conversationId);
      const message = item?.messages.find(row => row.id === user.id);
      return message?.blocks.some(block => block.kind === 'ASSET_REF') ? { projection, message } : null;
    }, 'ZIP 附件进入选定消息');
    assert.equal(linked.message.blocks.filter(block => block.kind === 'ASSET_REF').length, 1);
    if (manualDat) assert.equal(linked.message.blocks.find(block => block.kind === 'ASSET_REF').asset.mimeType, 'image/png');
    assert.equal(linked.projection.exchange.conversations.find(item => item.id === conversationId).messages.find(item => item.role === 'assistant').blocks.some(block => block.kind === 'ASSET_REF'), false);
    stop();
    port = await start(true);
    await until(() => evaluate(port, 'Boolean(window.__TAURI__?.core?.invoke)'), 'ZIP 附件同根冷启');
    const cold = (await invoke(port, 'CHAT', 'read_desktop_workspace', { workspaceId })).exchange.conversations.find(item => item.id === conversationId);
    assert.equal(cold.messages.find(item => item.id === user.id).blocks.filter(block => block.kind === 'ASSET_REF').length, 1);
    const coldAttachmentId = cold.messages.find(item => item.id === user.id).blocks.find(block => block.kind === 'ASSET_REF').asset.id;
    if (manualDat) {
      const preview = await invoke(port, 'CHAT', 'read_desktop_image_preview', { args: { workspaceId, attachmentId: coldAttachmentId, fullSize: true } });
      assert.equal(preview.attachmentId, coldAttachmentId);
      assert.ok(preview.dataUrl.startsWith('data:image/png;base64,'));
    }
    result = { candidateSha256: receipt.sha256, root, workspaceId, taskId: task.id, conversationId,
      attachmentId: coldAttachmentId,
      visibleManualLink: true, coldReadback: true, corruptExistingRefused: manualCorruptExisting, stoppedPids };
  } else {
  const secondExchange = join(fixtureDir, 'second-workspace.nfai-exchange');
  await copyFile(resolve(desktopRoot, '../protocol/artifacts/nfai.exchange.v1.golden.nfai-exchange'), secondExchange);
  const preflight = await invoke(port, 'CHAT', 'stage_preflight_selected_exchange', { selectedPath: secondExchange });
  const imported = await invoke(port, 'CHAT', 'import_staged_exchange_as_new_workspace', { args: { stagingId: preflight.stagingId, workspaceTitle: 'W14 第二合成工作区' } });
  const secondWorkspaceId = imported.summary.id;
  assert.notEqual(secondWorkspaceId, workspaceId);
  assert.equal(await invoke(port, 'CHAT', 'read_latest_p6k_zip_import_task', { workspaceId: secondWorkspaceId }), null);
  const secondTask = await invoke(port, 'CHAT', 'stage_p6k_zip_import_selected', { args: { workspaceId: secondWorkspaceId, provider: 'CHATGPT', selectedPath: zipPath } });
  assert.equal(secondTask.workspaceId, secondWorkspaceId);
  assert.equal(secondTask.importedCount, 1);
  assert.equal(secondTask.status, 'COMPLETED');
  assert.equal((await invoke(port, 'CHAT', 'read_latest_p6k_zip_import_task', { workspaceId })).id, task.id);
  assert.equal((await invoke(port, 'CHAT', 'read_latest_p6k_zip_import_task', { workspaceId: secondWorkspaceId })).id, secondTask.id);
  const firstConversationId = task.items[0].conversationId;
  const cumulativeJson = join(fixtureDir, 'conversations-cumulative.json');
  const cumulativeZip = join(fixtureDir, 'synthetic-chatgpt-cumulative.zip');
  await writeFile(cumulativeJson, JSON.stringify([{ id: 'w14-zip-synthetic', title: 'W14 ZIP synthetic', create_time: 1, update_time: 3,
    mapping: { 'm-user': { parent: null, message: { author: { role: 'user' }, content: { parts: ['W14 synthetic user'] } } },
      'm-assistant': { parent: 'm-user', message: { author: { role: 'assistant' }, content: { parts: ['W14 synthetic assistant'] } } },
      'm-followup': { parent: 'm-assistant', message: { author: { role: 'user' }, content: { parts: ['W14 synthetic followup'] }, create_time: 3 } } } }]), 'utf8');
  await copyFile(cumulativeJson, join(fixtureDir, 'conversations.json'));
  ps(`Compress-Archive -LiteralPath ${quote(join(fixtureDir, 'conversations.json'))} -DestinationPath ${quote(cumulativeZip)} -CompressionLevel Optimal -ErrorAction Stop`);
  const cumulativeTask = await invoke(port, 'CHAT', 'stage_p6k_zip_import_selected', { args: { workspaceId, provider: 'CHATGPT', selectedPath: cumulativeZip } });
  assert.equal(cumulativeTask.importedCount, 1);
  assert.equal(cumulativeTask.items[0].conversationId, firstConversationId);
  let firstConversation = (await invoke(port, 'CHAT', 'read_desktop_workspace', { workspaceId })).exchange.conversations.find(item => item.id === firstConversationId);
  assert.equal(firstConversation.messages.length, 3);
  const replayTask = await invoke(port, 'CHAT', 'stage_p6k_zip_import_selected', { args: { workspaceId, provider: 'CHATGPT', selectedPath: cumulativeZip } });
  assert.equal(replayTask.skippedCount, 1);
  assert.equal(replayTask.items[0].conversationId, firstConversationId);
  const conflictJson = join(fixtureDir, 'conversations-conflict.json');
  const conflictZip = join(fixtureDir, 'synthetic-chatgpt-conflict.zip');
  await writeFile(conflictJson, JSON.stringify([{ id: 'w14-zip-synthetic', title: 'W14 ZIP synthetic', create_time: 1, update_time: 3,
    mapping: { 'm-user': { parent: null, message: { author: { role: 'user' }, content: { parts: ['W14 rewritten user'] } } },
      'm-assistant': { parent: 'm-user', message: { author: { role: 'assistant' }, content: { parts: ['W14 synthetic assistant'] } } },
      'm-followup': { parent: 'm-assistant', message: { author: { role: 'user' }, content: { parts: ['W14 synthetic followup'] }, create_time: 3 } } } }]), 'utf8');
  await copyFile(conflictJson, join(fixtureDir, 'conversations.json'));
  ps(`Compress-Archive -LiteralPath ${quote(join(fixtureDir, 'conversations.json'))} -DestinationPath ${quote(conflictZip)} -CompressionLevel Optimal -ErrorAction Stop`);
  const conflictTask = await invoke(port, 'CHAT', 'stage_p6k_zip_import_selected', { args: { workspaceId, provider: 'CHATGPT', selectedPath: conflictZip } });
  assert.equal(conflictTask.failedCount, 1);
  assert.match(conflictTask.items[0].failure, /OFFICIAL_IDENTITY_CONFLICT/);
  await invoke(port, 'CHAT', 'delete_p6k_zip_import_batch', { taskId: task.id });
  firstConversation = (await invoke(port, 'CHAT', 'read_desktop_workspace', { workspaceId })).exchange.conversations.find(item => item.id === firstConversationId);
  assert.equal(firstConversation.deleted, false);
  assert.equal(firstConversation.messages.length, 3);
  assert.equal(await click(port, '[data-action="settings-back"]'), true);
  await until(() => evaluate(port, 'Boolean(document.querySelector("[data-action=select-p6k-claude-zip]"))'), 'Claude ZIP import entry');
  const claudeTime = '2026-08-15T08:00:00Z';
  const claudeFirstMessage = { uuid: 'claude-user', sender: 'human', created_at: claudeTime, text: 'W14 synthetic Claude user' };
  const claudeReply = { uuid: 'claude-reply', parent_message_uuid: 'claude-user', sender: 'assistant', created_at: '2026-08-15T08:01:00Z', text: 'W14 synthetic Claude reply' };
  const claudeConversation = chat_messages => [{ uuid: 'w14-claude-synthetic', name: 'W14 Claude synthetic', created_at: claudeTime, updated_at: '2026-08-15T08:02:00Z', chat_messages }];
  const claudeJson = join(fixtureDir, 'claude-conversations.json');
  const claudeZip = join(fixtureDir, 'synthetic-claude.zip');
  await writeFile(claudeJson, JSON.stringify(claudeConversation([claudeFirstMessage])), 'utf8');
  await copyFile(claudeJson, join(fixtureDir, 'conversations.json'));
  ps(`Compress-Archive -LiteralPath ${quote(join(fixtureDir, 'conversations.json'))} -DestinationPath ${quote(claudeZip)} -CompressionLevel Optimal -ErrorAction Stop`);
  assert.equal(await click(port, '[data-action="select-p6k-claude-zip"]'), true);
  await until(dialogOpen, 'system Claude ZIP picker');
  const claudeOperated = JSON.parse(ps(`& ${quote(join(desktopRoot, 'scripts/operate-windows-file-dialog.ps1'))} -ProcessId ${Number(launch.pid)} -Action 'select' -SelectedPath ${quote(claudeZip)} -AcceptanceRoot ${quote(root)}`));
  assert.equal(claudeOperated.action, 'select');
  await until(() => !dialogOpen(), 'Claude ZIP picker closed');
  const claudeTask = await until(async () => {
    const value = await invoke(port, 'CHAT', 'read_latest_p6k_zip_import_task', { workspaceId });
    return value?.provider === 'CLAUDE' && value.importedCount === 1 ? value : null;
  }, 'Claude ZIP imported task');
  assert.equal(claudeTask.status, 'COMPLETED');
  await until(() => evaluate(port, 'Boolean(document.querySelector("[data-action=open-settings-page][data-page=zip-import-results]"))'), 'Claude ZIP result entry');
  assert.equal(await click(port, '[data-action="open-settings-page"][data-page="zip-import-results"]'), true);
  await until(() => evaluate(port, 'document.querySelector(".android-settings-main")?.textContent?.includes("CLAUDE")'), 'visible Claude ZIP result');
  const claudeConversationId = claudeTask.items[0].conversationId;
  const secondClaudeTask = await invoke(port, 'CHAT', 'stage_p6k_zip_import_selected', { args: { workspaceId: secondWorkspaceId, provider: 'CLAUDE', selectedPath: claudeZip } });
  assert.equal(secondClaudeTask.importedCount, 1);
  assert.notEqual(secondClaudeTask.items[0].conversationId, claudeConversationId);
  const claudeCumulativeZip = join(fixtureDir, 'synthetic-claude-cumulative.zip');
  await writeFile(claudeJson, JSON.stringify(claudeConversation([claudeFirstMessage, claudeReply])), 'utf8');
  await copyFile(claudeJson, join(fixtureDir, 'conversations.json'));
  ps(`Compress-Archive -LiteralPath ${quote(join(fixtureDir, 'conversations.json'))} -DestinationPath ${quote(claudeCumulativeZip)} -CompressionLevel Optimal -ErrorAction Stop`);
  const claudeCumulativeTask = await invoke(port, 'CHAT', 'stage_p6k_zip_import_selected', { args: { workspaceId, provider: 'CLAUDE', selectedPath: claudeCumulativeZip } });
  assert.equal(claudeCumulativeTask.importedCount, 1);
  assert.equal(claudeCumulativeTask.items[0].conversationId, claudeConversationId);
  const claudeReplayTask = await invoke(port, 'CHAT', 'stage_p6k_zip_import_selected', { args: { workspaceId, provider: 'CLAUDE', selectedPath: claudeCumulativeZip } });
  assert.equal(claudeReplayTask.skippedCount, 1);
  assert.equal(claudeReplayTask.items[0].conversationId, claudeConversationId);
  const claudeConflictZip = join(fixtureDir, 'synthetic-claude-conflict.zip');
  await writeFile(claudeJson, JSON.stringify(claudeConversation([{ ...claudeFirstMessage, text: 'W14 rewritten Claude user' }, claudeReply])), 'utf8');
  await copyFile(claudeJson, join(fixtureDir, 'conversations.json'));
  ps(`Compress-Archive -LiteralPath ${quote(join(fixtureDir, 'conversations.json'))} -DestinationPath ${quote(claudeConflictZip)} -CompressionLevel Optimal -ErrorAction Stop`);
  const claudeConflictTask = await invoke(port, 'CHAT', 'stage_p6k_zip_import_selected', { args: { workspaceId, provider: 'CLAUDE', selectedPath: claudeConflictZip } });
  assert.equal(claudeConflictTask.failedCount, 1);
  assert.match(claudeConflictTask.items[0].failure, /OFFICIAL_IDENTITY_CONFLICT/);
  await invoke(port, 'CHAT', 'delete_p6k_zip_import_batch', { taskId: claudeTask.id });
  const claudeAfterDelete = (await invoke(port, 'CHAT', 'read_desktop_workspace', { workspaceId })).exchange.conversations.find(item => item.id === claudeConversationId);
  assert.equal(claudeAfterDelete.deleted, false);
  assert.equal(claudeAfterDelete.messages.length, 2);
  stop();
  // Model the durable journal at the point after a committed item and before final task status.
  // The process has already been stopped by exact PID; no live database is modified.
  assert.equal(syntheticTaskStatus(replayTask.id, 'STAGED'), 'STAGED');
  port = await start(true);
  await until(() => evaluate(port, 'Boolean(window.__TAURI__?.core?.invoke)'), 'cold native IPC');
  assert.equal(syntheticTaskStatus(replayTask.id), 'UNKNOWN');
  assert.equal((await invoke(port, 'CHAT', 'retry_p6k_zip_import_task', { taskId: replayTask.id })).status, 'COMPLETED');
  const cold = await invoke(port, 'CHAT', 'read_latest_p6k_zip_import_task', { workspaceId });
  assert.equal(cold?.id, replayTask.id);
  assert.equal(cold?.status, 'COMPLETED');
  const secondCold = await invoke(port, 'CHAT', 'read_latest_p6k_zip_import_task', { workspaceId: secondWorkspaceId });
  assert.equal(secondCold?.id, secondClaudeTask.id);
  assert.equal(secondCold?.importedCount, 1);
  assert.equal(await invoke(port, 'CHAT', 'read_latest_p6k_zip_import_task', { workspaceId: 'workspace-other' }), null);
  const coldConversation = (await invoke(port, 'CHAT', 'read_desktop_workspace', { workspaceId })).exchange.conversations.find(item => item.id === firstConversationId);
  assert.equal(coldConversation.deleted, false);
  assert.equal(coldConversation.messages.length, 3);
  const coldClaude = (await invoke(port, 'CHAT', 'read_desktop_workspace', { workspaceId })).exchange.conversations.find(item => item.id === claudeConversationId);
  assert.equal(coldClaude.deleted, false);
  assert.equal(coldClaude.messages.length, 2);
  const secondConversations = (await invoke(port, 'CHAT', 'read_desktop_workspace', { workspaceId: secondWorkspaceId })).exchange.conversations;
  assert.equal(secondConversations.filter(item => item.importedFrom === 'CHATGPT_ZIP').length, 1);
  assert.equal(secondConversations.filter(item => item.importedFrom === 'CLAUDE_ZIP').length, 1);
  result = { candidateSha256: receipt.sha256, root, workspaceId, taskId: task.id, cumulativeTaskId: cumulativeTask.id, replayTaskId: replayTask.id, conflictTaskId: conflictTask.id, secondWorkspaceId, secondTaskId: secondTask.id, claudeTaskId: claudeTask.id, secondClaudeTaskId: secondClaudeTask.id, claudeCumulativeTaskId: claudeCumulativeTask.id, claudeReplayTaskId: claudeReplayTask.id, claudeConflictTaskId: claudeConflictTask.id, firstConversationId, coldMessageCount: coldConversation.messages.length, claudeConversationId, coldClaudeMessageCount: coldClaude.messages.length, secondImportedCount: secondCold.importedCount, interruptedStatusOnReopen: 'UNKNOWN', explicitRetryStatus: 'COMPLETED', stoppedPids };
  }
} finally {
  stop();
}
if (result) console.log(JSON.stringify(result));
