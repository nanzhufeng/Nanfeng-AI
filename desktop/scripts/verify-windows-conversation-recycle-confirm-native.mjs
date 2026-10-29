import assert from 'node:assert/strict';
import { readFile, writeFile, access } from 'node:fs/promises';
import { dirname, join } from 'node:path';

const port = Number(process.argv[2]);
const screenshotPath = process.argv[3];
const favoriteLifecycle = process.argv.includes('--favorite-lifecycle');
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

await until(() => evaluate('Boolean(window.__TAURI__?.core?.invoke)'), 'native IPC');
if (process.argv.includes('--startup-settings')) {
  const root = dirname(screenshotPath);
  assert.equal(JSON.parse(await readFile(join(root, '.nanfeng-windows-acceptance.json'), 'utf8')).identifier,
    'com.nanzhufeng.ai.desktop.windowsacceptance');
  const local = await invoke('ensure_desktop_local_workspace');
  await until(() => evaluate('globalThis.__nanfengV2CommittedExchanges !== undefined'), 'first startup completed');
  await writeFile(join(root, 'draft-read-pause.arm'), '');
  await evaluate('location.reload()');
  await until(async () => { await access(join(root, 'draft-read-pause.reached')); return true; }, 'startup draft read paused');
  assert.equal(await click('[data-action="show-settings"]'), true);
  assert.equal(await evaluate('globalThis.__nanfengDesktopWorkState?.pane'), 'settings', 'single early settings click must navigate');
  assert.equal(await click('[data-action="open-settings-page"][data-page="model"]'), true);
  assert.equal(await click('[data-action="open-settings-page"][data-page="model-configuration"]'), true);
  await until(() => evaluate('Boolean(document.querySelector("#model-service-api-key"))'), 'early model editor');
  await evaluate(`(()=>{const field=document.querySelector('#model-service-api-key');field.value='nfai-synthetic-startup-editor';field.dispatchEvent(new Event('input',{bubbles:true}));return true})()`);
  const before = await evaluate('({provider:globalThis.__nanfengDesktopWorkState.modelProviderId,enabled:globalThis.__nanfengDesktopWorkState.modelServiceDraft.enabled})');
  assert.equal(await click('[data-action="toggle-model-service-enabled"]'), true);
  await writeFile(join(root, 'draft-read-pause.continue'), '');
  await until(() => evaluate('globalThis.__nanfengV2CommittedExchanges !== undefined'), 'paused startup completed');
  const after = await evaluate('({pane:globalThis.__nanfengDesktopWorkState.pane,page:globalThis.__nanfengDesktopWorkState.settingsSection,provider:globalThis.__nanfengDesktopWorkState.modelProviderId,enabled:globalThis.__nanfengDesktopWorkState.modelServiceDraft?.enabled,edited:globalThis.__nanfengDesktopWorkState.modelCredentialEdited,syntheticKeyRetained:document.querySelector("#model-service-api-key")?.value==="nfai-synthetic-startup-editor"})');
  assert.equal(after.pane, 'settings');
  assert.equal(after.page, 'model-configuration');
  assert.equal(after.provider, before.provider);
  assert.equal(after.enabled, !before.enabled);
  assert.equal(after.edited, true);
  assert.equal(after.syntheticKeyRetained, true);
  process.stdout.write(JSON.stringify({workspaceId:local.summary.id,controlledStartupRead:true,singleSettingsClick:true,settingsRouteRetained:true,modelEditorRetained:true}) + '\n');
  process.exit(0);
}
const reopen = process.argv.find(argument => argument.startsWith('--reopen='));
if (reopen) {
  const expected = JSON.parse(reopen.slice('--reopen='.length));
  const current = await invoke('read_desktop_workspace', { workspaceId: expected.workspaceId });
  const recycled = current.exchange.conversations.find(item => item.id === expected.recycledId);
  const restored = current.exchange.conversations.find(item => item.id === expected.restoredId);
  assert.ok(recycled?.deleted && recycled.messages.length === 1);
  assert.ok(restored && !restored.archived && !restored.deleted);
  if (expected.pinned) assert.equal(restored.pinned, true);
  assert.ok(restored.messages.some(message => JSON.stringify(message).includes('收藏归档恢复后保留正文')));
  assert.ok(!(await invoke('read_desktop_favorite_conversation_ids', { workspaceId: expected.workspaceId })).includes(expected.restoredId));
  process.stdout.write(JSON.stringify({ processReopen: true, recycledRetained: true, restoredRetained: true, favoriteRemoved: true }) + '\n');
  process.exit(0);
}
const local = await invoke('ensure_desktop_local_workspace');
const workspaceId = local.summary.id;
const title = `合成回收站确认 ${Date.now()}`;
const created = await invoke('mutate_desktop_domain', { args: {
  intentId: `recycle-gui-create-${Date.now()}`, workspaceId, entity: 'conversation', action: 'create',
  objectId: null, expectedRevision: null, fields: { title, projectId: null, firstMessage: '仅用于确认取消验收' },
} });
const recycled = await invoke('mutate_desktop_domain', { args: {
  intentId: `recycle-gui-delete-${Date.now()}`, workspaceId, entity: 'conversation', action: 'softDelete',
  objectId: created.objectId, expectedRevision: created.revision, fields: {},
} });
await evaluate('location.reload()');
await until(() => evaluate(`globalThis.__nanfengDesktopWorkState?.current?.summary?.id === ${js(workspaceId)}`), 'workspace startup readback');
await until(() => evaluate('globalThis.__nanfengV2CommittedExchanges !== undefined'), 'startup final readback');
await until(async () => {
  if (await evaluate('globalThis.__nanfengDesktopWorkState?.pane === "settings"')) return true;
  await click('[data-action="show-settings"]');
  return false;
}, 'settings route');
await until(() => click('[data-action="open-settings-page"][data-page="conversations"]'), 'conversation settings entry').catch(async error => {
  const diagnostic = await evaluate('({pane:globalThis.__nanfengDesktopWorkState?.pane,page:globalThis.__nanfengDesktopWorkState?.settingsSection,area:globalThis.__nanfengDesktopWorkState?.dataArea,settingsEntries:document.querySelectorAll("[data-action=open-settings-page]").length,body:document.body?.innerText?.slice(0,220)})');
  throw new Error(`${error.message}: ${JSON.stringify(diagnostic)}`);
});
await until(() => evaluate('globalThis.__nanfengDesktopWorkState?.settingsSection === "conversations"'), 'conversation settings route');
await until(() => click('[data-action="open-settings-page"][data-page="recycle"]'), 'recycle settings entry');
const row = await until(() => evaluate(`(()=>{const row=[...document.querySelectorAll('.conversation-lifecycle-row')].find(item=>item.querySelector('[data-action="select-chat"]')?.dataset.id===${js(created.objectId)});return row?{text:row.innerText,id:row.querySelector('[data-action="select-chat"]')?.dataset.id}:null})()`), 'recycled row').catch(async error => {
  const diagnostic = await evaluate(`(()=>{const s=globalThis.__nanfengDesktopWorkState;return {pane:s?.pane,page:s?.settingsSection,workspace:s?.current?.summary?.id,conversations:s?.current?.exchange?.conversations?.map(x=>({id:x.id,deleted:x.deleted,archived:x.archived})),rows:document.querySelectorAll('.conversation-lifecycle-row').length,empty:document.querySelector('.android-settings-empty')?.innerText||null}})()`);
  throw new Error(`${error.message}: ${JSON.stringify(diagnostic)}`);
});
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
assert.match(bulkDialog, /将永久删除 1 个会话，无法恢复/);
assert.equal(await click('.dialog[role="dialog"] [data-action="close-dialog"]'), true);
assert.equal(await evaluate('Boolean(document.querySelector(\'.dialog[role="dialog"]\'))'), false);
const readback = await invoke('read_desktop_workspace', { workspaceId });
const item = readback.exchange.conversations.find(conversation => conversation.id === created.objectId);
assert.ok(item?.deleted, 'cancel must retain recycled conversation');
assert.equal(item.revision, recycled.revision);
assert.equal(item.messages.length, 1);
await evaluate('location.reload()');
await until(() => evaluate('Boolean(window.__TAURI__?.core?.invoke)'), 'reloaded IPC');
const afterReload = await invoke('read_desktop_workspace', { workspaceId });
assert.ok(afterReload.exchange.conversations.some(conversation => conversation.id === created.objectId && conversation.deleted));
let favoriteAndRestore = null;
if (favoriteLifecycle) {
  const active = await invoke('mutate_desktop_domain', { args: {
    intentId: `favorite-gui-create-${Date.now()}`, workspaceId, entity: 'conversation', action: 'create',
    fields: { title: `合成收藏恢复 ${Date.now()}`, projectId: null, firstMessage: '收藏归档恢复后保留正文' },
  } });
  const rowSelector = `[data-conversation-row][data-id="${active.objectId}"]`;
  await evaluate('location.reload()');
  await until(() => evaluate(`Boolean(document.querySelector(${js(`${rowSelector} [data-action="open-conversation-row-menu"]`)}))`), 'active conversation row');
  for (const pinned of [true, false]) {
    await until(() => click(`${rowSelector} [data-action="open-conversation-row-menu"]`), 'pin row menu');
    assert.equal(await click('[data-action="context-menu-pin"]'), true);
    await until(async () => (await invoke('read_desktop_workspace', { workspaceId })).exchange.conversations
      .some(item => item.id === active.objectId && item.pinned === pinned), pinned ? 'pinned stored' : 'unpinned stored');
    await until(() => evaluate(`globalThis.__nanfengDesktopWorkState?.current?.exchange?.conversations?.some(item => item.id === ${js(active.objectId)} && item.pinned === ${pinned})`), 'pin projection refreshed');
    await until(() => evaluate('!document.querySelector("[data-action=context-menu-pin]")'), 'pin menu closed');
  }
  assert.equal(await click(`${rowSelector} [data-action="open-conversation-row-menu"]`), true);
  assert.equal(await click('[data-action="context-menu-favorite"]'), true);
  await until(async () => (await invoke('read_desktop_favorite_conversation_ids', { workspaceId })).includes(active.objectId), 'favorite stored');
  await until(() => click('[data-action="show-settings"]'), 'settings after favorite');
  await until(() => click('[data-action="open-settings-page"][data-page="conversations"]'), 'conversation settings after favorite');
  await until(() => click('[data-action="open-settings-page"][data-page="favorites"]'), 'favorites page');
  await until(() => evaluate(`Boolean(document.querySelector(${js(`.conversation-lifecycle-row [data-action="select-chat"][data-id="${active.objectId}"]`)}))`), 'favorite row');
  const disclosure = `.conversation-lifecycle-actions[data-lifecycle-action-disclosure="${active.objectId}"]`;
  assert.equal(await click(`${disclosure} summary`), true);
  assert.equal(await click(`${disclosure} [data-action="toggle-conversation-favorite"]`), true);
  await until(async () => !(await invoke('read_desktop_favorite_conversation_ids', { workspaceId })).includes(active.objectId), 'favorite removed');
  await until(() => click('[data-action="show-chat"]'), 'chat after favorite');
  await until(() => evaluate(`Boolean(document.querySelector(${js(`${rowSelector} [data-action="open-conversation-row-menu"]`)}))`), 'row after favorite');
  assert.equal(await click(`${rowSelector} [data-action="open-conversation-row-menu"]`), true);
  assert.equal(await click('[data-action="context-menu-archive"]'), true);
  await until(async () => (await invoke('read_desktop_workspace', { workspaceId })).exchange.conversations
    .some(item => item.id === active.objectId && item.archived), 'conversation archived');
  await until(() => click('[data-action="show-settings"]'), 'settings after archive');
  await until(() => click('[data-action="open-settings-page"][data-page="conversations"]'), 'conversation settings after archive');
  await until(() => click('[data-action="open-settings-page"][data-page="archived"]'), 'archived page');
  await until(() => evaluate(`Boolean(document.querySelector(${js(`.conversation-lifecycle-row [data-action="select-chat"][data-id="${active.objectId}"]`)}))`), 'archived row');
  assert.equal(await click(`${disclosure} summary`), true);
  assert.equal(await click(`${disclosure} [data-action="restore-conversation"]`), true);
  await until(async () => (await invoke('read_desktop_workspace', { workspaceId })).exchange.conversations
    .some(item => item.id === active.objectId && !item.archived && !item.deleted), 'conversation restored');
  await until(() => click('[data-action="show-chat"]'), 'chat before recycle');
  await until(() => click(`${rowSelector} [data-action="open-conversation-row-menu"]`), 'recycle row menu');
  assert.equal(await click('[data-action="context-menu-delete"]'), true);
  await until(() => evaluate('Boolean(document.querySelector("[data-action=confirm-conversation-delete]"))'), 'recycle confirmation');
  assert.equal(await click('[data-action="confirm-conversation-delete"]'), true);
  await until(async () => (await invoke('read_desktop_workspace', { workspaceId })).exchange.conversations
    .some(item => item.id === active.objectId && item.deleted), 'conversation recycled');
  await until(() => click('[data-action="show-settings"]'), 'settings before restore deleted');
  await until(() => click('[data-action="open-settings-page"][data-page="conversations"]'), 'conversation settings before restore deleted');
  await until(() => click('[data-action="open-settings-page"][data-page="recycle"]'), 'recycle page before restore');
  await until(() => click(`${disclosure} summary`), 'recycled action disclosure');
  assert.equal(await click(`${disclosure} [data-action="restore-deleted-conversation"]`), true);
  await until(async () => (await invoke('read_desktop_workspace', { workspaceId })).exchange.conversations
    .some(item => item.id === active.objectId && !item.archived && !item.deleted), 'recycled conversation restored');
  await until(() => click('[data-action="show-chat"]'), 'chat before persistent pin');
  await until(() => click(`${rowSelector} [data-action="open-conversation-row-menu"]`), 'persistent pin menu');
  assert.equal(await click('[data-action="context-menu-pin"]'), true);
  await until(async () => (await invoke('read_desktop_workspace', { workspaceId })).exchange.conversations
    .some(item => item.id === active.objectId && item.pinned), 'persistent pin stored');
  await evaluate('location.reload()');
  await until(() => evaluate('Boolean(window.__TAURI__?.core?.invoke)'), 'favorite lifecycle reloaded IPC');
  const cold = await invoke('read_desktop_workspace', { workspaceId });
  const restored = cold.exchange.conversations.find(item => item.id === active.objectId);
  assert.ok(restored && !restored.archived && !restored.deleted && restored.messages.some(message => JSON.stringify(message).includes('收藏归档恢复后保留正文')));
  assert.equal(restored.pinned, true);
  favoriteAndRestore = { conversationId: active.objectId, favoriteAddedAndRemoved: true,
    pinAddedAndRemoved: true, pinned: true, archivedAndRestored: true, recycledAndRestored: true, coldReadback: true };
}
process.stdout.write(JSON.stringify({
  workspaceId, conversationId: created.objectId, title,
  rowPresent: true, singleConfirmation: singleDialog, bulkConfirmation: bulkDialog,
  cancelledWithoutMutation: true, reloadedRetained: true, screenshotPath, favoriteAndRestore,
}) + '\n');
