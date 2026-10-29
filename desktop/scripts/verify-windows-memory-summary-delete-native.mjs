import assert from 'node:assert/strict';

const port = Number(process.argv[2]);
const phase = process.argv[3] || 'delete';
const workspaceFromFirstRun = process.argv[4] || '';
assert.ok(Number.isInteger(port) && port > 0);
assert.ok(['delete', 'reopen'].includes(phase));

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
        if (message.error || message.result?.exceptionDetails) reject(new Error(message.result?.exceptionDetails?.text || message.error?.message || 'CDP error'));
        else resolve(message.result);
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
    try { const value = await check(); if (value) return value; } catch { /* WebView is starting or repainting. */ }
    await new Promise(resolve => setTimeout(resolve, 250));
  }
  throw new Error(`${label} did not become ready`);
}
async function invoke(command, args = {}) {
  const result = await evaluate(`window.__TAURI__.core.invoke(${js(command)},${js(args)},${js({ headers: { 'x-nanfeng-data-area': 'CHAT' } })}).catch(error=>({__error:String(error)}))`);
  if (result?.__error) throw new Error(`${command}: ${result.__error}`);
  return result;
}
async function openSummary() {
  await until(async () => {
    await click('[data-action="show-settings"]');
    return evaluate('Boolean(document.querySelector(\'[data-action="open-settings-page"][data-page="memory-overview"]\'))');
  }, 'settings memory entry');
  assert.equal(await click('[data-action="open-settings-page"][data-page="memory-overview"]'), true);
  await until(() => evaluate('Boolean(document.querySelector(\'.android-memory-summary-page\'))'), 'memory summary page');
}

await until(() => evaluate('Boolean(window.__TAURI__?.core?.invoke)'), 'native IPC');
const local = await invoke('ensure_desktop_local_workspace');
const workspaceId = local.summary.id;
if (phase === 'reopen') assert.equal(workspaceId, workspaceFromFirstRun);
const tag = '合成记忆删除验收';
if (phase === 'delete') {
  for (const suffix of ['a', 'b']) {
    await invoke('mutate_desktop_domain', { args: {
      intentId: `memory-summary-delete-fixture-${suffix}-${Date.now()}`, workspaceId,
      entity: 'memory', action: 'create', objectId: null, expectedRevision: null,
      fields: { title: '工作流程偏好', body: `${tag} ${suffix}`, scope: 'GLOBAL', scopeId: null },
    } });
  }
  await evaluate('location.reload()');
  await until(() => evaluate('Boolean(window.__TAURI__?.core?.invoke)'), 'reloaded IPC');
  await openSummary();
  assert.equal(await evaluate(`document.querySelectorAll('.android-memory-summary-content article').length`), 2);
  assert.equal(await click('[data-action="ask-delete-memory-summary"]'), true);
  assert.match(await until(() => evaluate('document.querySelector(\'[role="alertdialog"]\')?.innerText'), 'delete confirmation'), /删除记忆/);
  assert.equal(await click('[role="alertdialog"] [data-action="close-dialog"]'), true);
  const afterCancel = await invoke('read_desktop_workspace', { workspaceId });
  assert.equal(afterCancel.exchange.memory.filter(item => item.status === 'ACTIVE' && item.scope === 'GLOBAL').length, 2);
  assert.equal(await click('[data-action="ask-delete-memory-summary"]'), true);
  assert.equal(await click('[data-action="confirm-delete-memory-summary"]'), true);
  await until(async () => {
    const projection = await invoke('read_desktop_workspace', { workspaceId });
    return projection.exchange.memory.filter(item => item.status === 'ACTIVE' && item.scope === 'GLOBAL').length === 0;
  }, 'atomic memory deletion');
  const after = await invoke('read_desktop_workspace', { workspaceId });
  assert.equal(after.exchange.memory.filter(item => item.status === 'DELETED' && item.body?.startsWith(tag)).length, 2);
  console.log(JSON.stringify({ phase, workspaceId, active: 0, deleted: 2, visibleCancel: true, visibleConfirm: true }));
} else {
  await openSummary();
  assert.match(await evaluate('document.querySelector(\'.android-memory-summary-content\')?.innerText'), /还没有记忆摘要/);
  const after = await invoke('read_desktop_workspace', { workspaceId });
  assert.equal(after.exchange.memory.filter(item => item.status === 'ACTIVE' && item.scope === 'GLOBAL').length, 0);
  assert.equal(after.exchange.memory.filter(item => item.status === 'DELETED').length, 2);
  console.log(JSON.stringify({ phase, workspaceId, active: 0, deleted: 2, emptySummaryVisible: true }));
}
