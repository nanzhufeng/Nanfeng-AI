import assert from 'node:assert/strict';
import { writeFile } from 'node:fs/promises';

const port = Number(process.argv[2]);
const screenshotPath = process.argv[3];
assert.ok(Number.isInteger(port) && port > 0 && screenshotPath);

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

async function verifyRetiredHistory(workspaceId) {
  const history = await invoke('read_desktop_workbench_history', { workspaceId });
  assert.equal(history.canUndo, false);
  assert.equal(history.canRedo, false);
  for (const direction of ['undo', 'redo']) {
    await assert.rejects(invoke(`${direction}_desktop_domain`, { args: {
      intentId: `knowledge-cleanup-${direction}-${Date.now()}`, workspaceId,
    } }), new RegExp(`没有可 ${direction} 的本地动作`));
  }
}
await until(() => evaluate('Boolean(window.__TAURI__?.core?.invoke)'), 'native IPC');
if (process.argv[4] === '--reopen') {
  const workspaceId = process.argv[5];
  assert.ok(workspaceId);
  const current = await invoke('read_desktop_workspace', { workspaceId });
  assert.equal(current.exchange.knowledge.length, 0);
  assert.equal(current.exchange.memory.length, 0);
  await verifyRetiredHistory(workspaceId);
  process.stdout.write(JSON.stringify({ workspaceId, processRestartRetainedCleanup: true, undoRedoRejected: true }) + '\n');
  process.exit(0);
}

const local = await invoke('ensure_desktop_local_workspace');
const workspaceId = local.summary.id;
const tag = Date.now();
const records = [
  { entity: 'knowledge', fields: { title: `合成知识 ${tag}`, body: '仅用于清理确认取消验收', tags: [], scope: 'GLOBAL', projectId: null } },
  { entity: 'memory', fields: { body: '合成记忆：仅用于本机清理验收', scope: 'GLOBAL', scopeId: null } },
];
for (const record of records) {
  record.createIntentId = `privacy-gui-${record.entity}-create-${tag}`;
  record.deleteIntentId = `privacy-gui-${record.entity}-delete-${tag}`;
  record.created = await invoke('mutate_desktop_domain', { args: {
    intentId: record.createIntentId, workspaceId, entity: record.entity, action: 'create',
    objectId: null, expectedRevision: null, fields: record.fields,
  } });
  record.recycled = await invoke('mutate_desktop_domain', { args: {
    intentId: record.deleteIntentId, workspaceId, entity: record.entity, action: 'softDelete',
    objectId: record.created.objectId, expectedRevision: record.created.revision, fields: {},
  } });
}
const previewBefore = await invoke('preview_desktop_privacy_deletion', { args: {
  scope: 'KNOWLEDGE_MEMORY_TRASH', selectedTaskIds: [],
} });
assert.equal(previewBefore.aggregates.reduce((sum, item) => sum + item.count, 0), 2);
await evaluate('location.reload()');
await until(async () => {
  await click('[data-action="show-settings"]');
  return evaluate('Boolean(document.querySelector(\'[data-action="open-settings-page"][data-page="privacy"]\'))');
}, 'settings entry');
await until(async () => {
  await click('[data-action="open-settings-page"][data-page="privacy"]');
  return evaluate('Boolean(document.querySelector(\'[data-action="open-privacy-cleanup"]\'))');
}, 'local data settings entry');
await until(async () => {
  await click('[data-action="open-privacy-cleanup"]');
  return evaluate('Boolean(document.querySelector(\'.privacy-cleanup-dialog [data-scope="KNOWLEDGE_MEMORY_TRASH"]\'))');
}, 'privacy cleanup entry');
const scopeDialog = await until(() => evaluate('document.querySelector(\'.privacy-cleanup-dialog\')?.innerText || null'), 'scope dialog');
assert.match(scopeDialog, /选择清理范围/);
assert.match(scopeDialog, /只清空已放入知识与记忆回收站的内容/);
assert.equal(await evaluate('document.querySelectorAll(\'[data-action="select-privacy-cleanup-scope"]\').length'), 3);
assert.equal(await click('[data-action="select-privacy-cleanup-scope"][data-scope="KNOWLEDGE_MEMORY_TRASH"]'), true);
const previewDialog = await until(() => evaluate(`(()=>{const dialog=document.querySelector('.privacy-cleanup-dialog');return dialog?.querySelector('[data-action="confirm-privacy-cleanup"]')?dialog.innerText:null})()`), 'knowledge and memory preview');
assert.match(previewDialog, /清空知识与记忆回收站/);
assert.match(previewDialog, /已确认清理范围/);
assert.equal(await evaluate('document.querySelector(\'[data-action="confirm-privacy-cleanup"]\')?.disabled'), false);
const screenshot = await cdp('Page.captureScreenshot', { format: 'png', captureBeyondViewport: false });
await writeFile(screenshotPath, Buffer.from(screenshot.data, 'base64'));
assert.equal(await click('.privacy-cleanup-dialog [data-action="close-dialog"]'), true);
assert.equal(await evaluate('Boolean(document.querySelector(\'.privacy-cleanup-dialog\'))'), false);
const readback = await invoke('read_desktop_workspace', { workspaceId });
for (const record of records) {
  const item = readback.exchange[record.entity].find(value => value.id === record.created.objectId);
  assert.equal(item?.status, 'DELETED', `${record.entity} must remain in trash`);
  assert.equal(item.revision, record.recycled.revision);
}
const previewAfter = await invoke('preview_desktop_privacy_deletion', { args: {
  scope: 'KNOWLEDGE_MEMORY_TRASH', selectedTaskIds: [],
} });
assert.equal(previewAfter.fingerprint, previewBefore.fingerprint);
await evaluate('location.reload()');
await until(() => evaluate('Boolean(window.__TAURI__?.core?.invoke)'), 'reloaded IPC');
const afterReload = await invoke('read_desktop_workspace', { workspaceId });
for (const record of records) {
  assert.ok(afterReload.exchange[record.entity].some(value => value.id === record.created.objectId && value.status === 'DELETED'));
}
await until(async () => {
  await click('[data-action="show-settings"]');
  return evaluate('Boolean(document.querySelector(\'[data-action="open-settings-page"][data-page="privacy"]\'))');
}, 'settings entry after reload');
await until(async () => {
  await click('[data-action="open-settings-page"][data-page="privacy"]');
  return evaluate('Boolean(document.querySelector(\'[data-action="open-privacy-cleanup"]\'))');
}, 'local data entry after reload');
await until(async () => {
  await click('[data-action="open-privacy-cleanup"]');
  return evaluate('Boolean(document.querySelector(\'.privacy-cleanup-dialog [data-scope="KNOWLEDGE_MEMORY_TRASH"]\'))');
}, 'privacy cleanup after reload');
assert.equal(await click('[data-action="select-privacy-cleanup-scope"][data-scope="KNOWLEDGE_MEMORY_TRASH"]'), true);
await until(() => evaluate('Boolean(document.querySelector(\'[data-action="confirm-privacy-cleanup"]\'))'), 'confirmed scope after reload');
assert.equal(await click('[data-action="confirm-privacy-cleanup"]'), true);
await until(async () => {
  const projection = await invoke('read_desktop_workspace', { workspaceId });
  return projection.exchange.knowledge.length === 0 && projection.exchange.memory.length === 0;
}, 'GUI knowledge and memory cleanup');
await verifyRetiredHistory(workspaceId);
let oldIdRejected = true;
for (const record of records) {
  try {
    await invoke('mutate_desktop_domain', { args: {
      intentId: record.deleteIntentId, workspaceId, entity: 'project', action: 'create',
      objectId: null, expectedRevision: null, fields: { title: '旧 ID 不得创建项目', description: '合成' },
    } });
    oldIdRejected = false;
  } catch (error) {
    if (!String(error).includes('领域 intent 操作不符')) throw error;
  }
}
assert.equal(oldIdRejected, true);
await evaluate('location.reload()');
await until(() => evaluate('Boolean(window.__TAURI__?.core?.invoke)'), 'cleanup reloaded IPC');
const afterCleanupReload = await invoke('read_desktop_workspace', { workspaceId });
assert.equal(afterCleanupReload.exchange.knowledge.length, 0);
assert.equal(afterCleanupReload.exchange.memory.length, 0);
assert.ok(!(afterCleanupReload.exchange.projects || []).some(project => project.title === '旧 ID 不得创建项目'));
process.stdout.write(JSON.stringify({
  workspaceId, records: records.map(record => ({
    entity: record.entity, objectId: record.created.objectId,
    createIntentId: record.createIntentId, deleteIntentId: record.deleteIntentId,
    revision: record.recycled.revision,
  })),
  scopeDialog, previewDialog, previewCount: 2,
  cancelledWithoutMutation: true, reloadedRetained: true,
  guiCommitted: true, oldIdRejected, undoRedoRejected: true, cleanupReloadEmpty: true, screenshotPath,
}) + '\n');
