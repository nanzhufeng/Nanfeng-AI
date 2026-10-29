import assert from 'node:assert/strict';
import { readFile, stat, writeFile } from 'node:fs/promises';
import { join } from 'node:path';

const port = Number(process.argv[2]);
const screenshotPath = process.argv[3];
const root = process.argv[4];
assert.ok(Number.isInteger(port) && port > 0 && screenshotPath && root);

async function cdp(method, params = {}) {
  const tabs = await fetch(`http://127.0.0.1:${port}/json/list`).then(response => response.json());
  const tab = tabs.find(item => item.type === 'page' && item.url === 'http://tauri.localhost/');
  assert.ok(tab, 'isolated Tauri page unavailable');
  const socket = new WebSocket(tab.webSocketDebuggerUrl);
  try {
    await new Promise((resolve, reject) => {
      socket.addEventListener('open', resolve, { once: true });
      socket.addEventListener('error', reject, { once: true });
    });
    return await new Promise((resolve, reject) => {
      const timer = setTimeout(() => reject(new Error(`CDP ${method} timed out`)), 20000);
      socket.addEventListener('message', event => {
        const message = JSON.parse(event.data);
        if (message.id !== 1) return;
        clearTimeout(timer);
        if (message.error || message.result?.exceptionDetails) {
          reject(new Error(message.result?.exceptionDetails?.exception?.description || message.result?.exceptionDetails?.text || message.error?.message || 'CDP error'));
        } else resolve(message.result);
      });
      socket.send(JSON.stringify({ id: 1, method, params }));
    });
  } finally {
    socket.close();
  }
}
const evaluate = async expression => (await cdp('Runtime.evaluate', { expression, awaitPromise: true, returnByValue: true }))?.result?.value;
const js = value => JSON.stringify(value);
const click = selector => evaluate(`(()=>{const element=document.querySelector(${js(selector)});if(!element)return false;element.click();return true})()`);
async function until(check, label) {
  const deadline = Date.now() + 30000;
  while (Date.now() < deadline) {
    try { const result = await check(); if (result) return result; } catch { /* The WebView may be reloading. */ }
    await new Promise(resolve => setTimeout(resolve, 250));
  }
  throw new Error(`${label} did not become ready`);
}
async function invoke(command, args = {}) {
  const result = await evaluate(`window.__TAURI__.core.invoke(${js(command)},${js(args)},${js({ headers: { 'x-nanfeng-data-area': 'CHAT' } })}).catch(error=>({__error:String(error)}))`);
  if (result?.__error) throw new Error(`${command}: ${result.__error}`);
  return result;
}

await until(() => evaluate('Boolean(window.__TAURI__?.core?.invoke)'), 'native IPC');
const local = await invoke('ensure_desktop_local_workspace');
const workspaceId = local.summary.id;
const tag = Date.now();
const title = `合成永久清理 A ${tag}`;
const otherTitle = `合成永久清理 B ${tag}`;
const privateBody = `synthetic-purge-private-${tag}`;
const created = await invoke('mutate_desktop_domain', { args: {
  intentId: `purge-gui-create-a-${tag}`, workspaceId, entity: 'conversation', action: 'create',
  objectId: null, expectedRevision: null, fields: { title, projectId: null, firstMessage: privateBody },
} });
const other = await invoke('mutate_desktop_domain', { args: {
  intentId: `purge-gui-create-b-${tag}`, workspaceId, entity: 'conversation', action: 'create',
  objectId: null, expectedRevision: null, fields: { title: otherTitle, projectId: null, firstMessage: '合成共享引用保留' },
} });
const source = join(root, 'synthetic-shared-attachment.txt');
const fixtureBytes = Buffer.from(`synthetic-shared-asset-${tag}`, 'utf8');
await writeFile(source, fixtureBytes);
const attachment = await invoke('import_desktop_conversation_attachment', { args: { workspaceId, selectedPath: source } });
const appendAIntent = `purge-gui-append-a-${tag}`;
const appendBIntent = `purge-gui-append-b-${tag}`;
const appendedA = await invoke('mutate_desktop_domain', { args: {
  intentId: appendAIntent, workspaceId, entity: 'conversation', action: 'appendMessage',
  objectId: created.objectId, expectedRevision: created.revision,
  fields: { text: '合成共享附件 A', role: 'user', attachmentIds: [attachment.id] },
} });
const appendedB = await invoke('mutate_desktop_domain', { args: {
  intentId: appendBIntent, workspaceId, entity: 'conversation', action: 'appendMessage',
  objectId: other.objectId, expectedRevision: other.revision,
  fields: { text: '合成共享附件 B', role: 'user', attachmentIds: [attachment.id] },
} });
const deleteAIntent = `purge-gui-delete-a-${tag}`;
const deleteBIntent = `purge-gui-delete-b-${tag}`;
const recycled = await invoke('mutate_desktop_domain', { args: {
  intentId: deleteAIntent, workspaceId, entity: 'conversation', action: 'softDelete',
  objectId: created.objectId, expectedRevision: appendedA.revision, fields: {},
} });
const recycledB = await invoke('mutate_desktop_domain', { args: {
  intentId: deleteBIntent, workspaceId, entity: 'conversation', action: 'softDelete',
  objectId: other.objectId, expectedRevision: appendedB.revision, fields: {},
} });
const assetPath = join(root, 'assets', attachment.sha256);
assert.deepEqual(await readFile(assetPath), fixtureBytes);
await evaluate('location.reload()');
await until(async () => {
  await click('[data-action="show-settings"]');
  return evaluate('Boolean(document.querySelector(\'[data-action="open-settings-page"][data-page="conversations"]\'))');
}, 'settings entry');
await until(async () => {
  await click('[data-action="open-settings-page"][data-page="conversations"]');
  return evaluate('Boolean(document.querySelector(\'[data-action="open-settings-page"][data-page="recycle"]\'))');
}, 'conversation settings entry');
await until(async () => {
  await click('[data-action="open-settings-page"][data-page="recycle"]');
  return evaluate('Boolean(document.querySelector(\'.conversation-lifecycle-row\'))');
}, 'recycle settings entry');
const row = await until(() => evaluate(`(()=>{const row=[...document.querySelectorAll('.conversation-lifecycle-row')].find(item=>item.innerText.includes(${js(title)}));return row?{text:row.innerText,id:row.querySelector('[data-action="select-chat"]')?.dataset.id}:null})()`), 'recycled row');
assert.equal(row.id, created.objectId);
const disclosure = `.conversation-lifecycle-actions[data-lifecycle-action-disclosure="${created.objectId}"]`;
assert.equal(await click(`${disclosure} summary`), true);
assert.equal(await click(`${disclosure} [data-action="open-conversation-permanent-delete"]`), true);
const singleDialog = await until(() => evaluate('document.querySelector(\'.dialog[role="dialog"]\')?.innerText || null'), 'single permanent-delete dialog');
assert.match(singleDialog, /永久删除？/);
assert.ok(singleDialog.includes(title));
assert.match(singleDialog, /无法恢复/);
const screenshot = await cdp('Page.captureScreenshot', { format: 'png', captureBeyondViewport: false });
await writeFile(screenshotPath, Buffer.from(screenshot.data, 'base64'));
assert.equal(await click('.dialog[role="dialog"] [data-action="close-dialog"]'), true);
assert.equal(await evaluate('Boolean(document.querySelector(\'.dialog[role="dialog"]\'))'), false);
assert.equal(await click('[data-action="open-conversation-bulk-cleanup"][data-scope="recycle"]'), true);
const bulkDialog = await until(() => evaluate('document.querySelector(\'.dialog[role="dialog"]\')?.innerText || null'), 'bulk permanent-delete dialog');
assert.match(bulkDialog, /清空回收站？/);
assert.match(bulkDialog, /将永久删除 2 个会话，无法恢复/);
assert.equal(await click('.dialog[role="dialog"] [data-action="close-dialog"]'), true);
assert.equal(await evaluate('Boolean(document.querySelector(\'.dialog[role="dialog"]\'))'), false);
const readback = await invoke('read_desktop_workspace', { workspaceId });
const item = readback.exchange.conversations.find(conversation => conversation.id === created.objectId);
assert.ok(item?.deleted, 'cancel must retain recycled conversation');
assert.equal(item.revision, recycled.revision);
assert.equal(item.messages.length, 2);
await evaluate('location.reload()');
await until(() => evaluate('Boolean(window.__TAURI__?.core?.invoke)'), 'reloaded IPC');
const afterReload = await invoke('read_desktop_workspace', { workspaceId });
assert.ok(afterReload.exchange.conversations.some(conversation => conversation.id === created.objectId && conversation.deleted));
assert.ok(afterReload.exchange.conversations.some(conversation => conversation.id === other.objectId && conversation.deleted));
await until(async () => {
  await click('[data-action="show-settings"]');
  return evaluate('Boolean(document.querySelector(\'[data-action="open-settings-page"][data-page="conversations"]\'))');
}, 'settings after reload');
await until(async () => {
  await click('[data-action="open-settings-page"][data-page="conversations"]');
  return evaluate('Boolean(document.querySelector(\'[data-action="open-settings-page"][data-page="recycle"]\'))');
}, 'conversations after reload');
await until(async () => {
  await click('[data-action="open-settings-page"][data-page="recycle"]');
  return evaluate(`Boolean(document.querySelector('.conversation-lifecycle-actions[data-lifecycle-action-disclosure="${created.objectId}"]'))`);
}, 'recycle after reload');
assert.equal(await click(`${disclosure} summary`), true);
assert.equal(await click(`${disclosure} [data-action="open-conversation-permanent-delete"]`), true);
await until(() => evaluate('Boolean(document.querySelector(\'[data-action="confirm-conversation-permanent-delete"]\'))'), 'single commit');
assert.equal(await click('[data-action="confirm-conversation-permanent-delete"]'), true);
await until(async () => {
  const projection = await invoke('read_desktop_workspace', { workspaceId });
  return !projection.exchange.conversations.some(conversation => conversation.id === created.objectId);
}, 'single GUI purge');
await until(() => evaluate(`!document.querySelector('.conversation-lifecycle-actions[data-lifecycle-action-disclosure="${created.objectId}"]') && !document.querySelector('.dialog[role="dialog"]')`), 'single purge UI refresh');
const afterSingle = await invoke('read_desktop_workspace', { workspaceId });
assert.ok(afterSingle.exchange.conversations.some(conversation => conversation.id === other.objectId && conversation.deleted));
assert.deepEqual(await readFile(assetPath), fixtureBytes, 'shared asset must remain after first purge');
await until(() => evaluate('Boolean(document.querySelector(\'[data-action="open-conversation-bulk-cleanup"][data-scope="recycle"]\'))'), 'bulk entry after single purge');
assert.equal(await click('[data-action="open-conversation-bulk-cleanup"][data-scope="recycle"]'), true);
const finalDialog = await until(() => evaluate('document.querySelector(\'.dialog[role="dialog"]\')?.innerText || null'), 'bulk commit dialog');
assert.match(finalDialog, /将永久删除 1 个会话，无法恢复/);
assert.equal(await click('[data-action="confirm-conversation-bulk-cleanup"]'), true);
await until(async () => {
  const projection = await invoke('read_desktop_workspace', { workspaceId });
  return !projection.exchange.conversations.some(conversation => conversation.id === other.objectId);
}, 'bulk GUI purge');
await assert.rejects(stat(assetPath), { code: 'ENOENT' }, 'last reference asset must be removed');
for (const oldIntentId of [appendAIntent, appendBIntent, deleteAIntent, deleteBIntent]) {
  await assert.rejects(invoke('mutate_desktop_domain', { args: {
    intentId: oldIntentId, workspaceId, entity: 'project', action: 'create',
    objectId: null, expectedRevision: null, fields: { title: '旧 ID 不得复活', description: '合成' },
  } }), /领域 intent 操作不符|请求身份不符|已失效/);
}
const purgeHistory = await invoke('read_desktop_workbench_history', { workspaceId });
assert.equal(purgeHistory.canUndo, false, 'permanent cleanup must retire undo snapshots');
assert.equal(purgeHistory.canRedo, false, 'permanent cleanup must retire redo snapshots');
for (const [command, direction] of [['undo_desktop_domain', 'undo'], ['redo_desktop_domain', 'redo']]) {
  await assert.rejects(invoke(command, { args: { intentId: `purge-final-${direction}-${Date.now()}`, workspaceId } }), new RegExp(`没有可 ${direction} 的本地动作`));
}
await evaluate('location.reload()');
await until(() => evaluate('Boolean(window.__TAURI__?.core?.invoke)'), 'purge reloaded IPC');
const cold = await invoke('read_desktop_workspace', { workspaceId });
assert.ok(!cold.exchange.conversations.some(conversation => [created.objectId, other.objectId].includes(conversation.id)));
assert.ok(!JSON.stringify(cold.exchange).includes(privateBody));
await assert.rejects(stat(assetPath), { code: 'ENOENT' });
process.stdout.write(JSON.stringify({
  workspaceId, conversationIds: [created.objectId, other.objectId], attachmentSha256: attachment.sha256,
  rowPresent: true, singleConfirmation: singleDialog, bulkConfirmation: bulkDialog,
  cancelledWithoutMutation: true, reloadedRetained: true, screenshotPath,
  singleGuiCommitted: true, bulkGuiCommitted: true, sharedAssetRetainedThenRemoved: true,
  oldIntentRejected: true, purgeUndoRedoRejected: true, coldReloadEmpty: true,
}) + '\n');
