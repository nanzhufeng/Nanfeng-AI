import assert from 'node:assert/strict';

const port = Number(process.argv[2]);
const phase = process.argv[3] || 'edit';
const workspaceFromFirstRun = process.argv[4] || '';
assert.ok(Number.isInteger(port) && port > 0);
assert.ok(['edit', 'reopen'].includes(phase));

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
const tag = '合成记忆编辑验收';
if (phase === 'edit') {
  for (const suffix of ['a', 'b']) {
    await invoke('mutate_desktop_domain', { args: {
      intentId: `memory-summary-edit-fixture-${suffix}-${Date.now()}`, workspaceId,
      entity: 'memory', action: 'create', objectId: null, expectedRevision: null,
      fields: { title: suffix === 'a' ? '工作偏好' : '写作习惯', body: `${tag} ${suffix}`, scope: 'GLOBAL', scopeId: null },
    } });
  }
  await evaluate('location.reload()');
  await until(() => evaluate('Boolean(window.__TAURI__?.core?.invoke)'), 'reloaded IPC');
  await openSummary();
  assert.equal(await evaluate(`document.querySelectorAll('.android-memory-summary-content article').length`), 2);
  assert.equal(await click('[data-action="edit-memory-summary"]'), true);
  let original = await until(() => evaluate('document.querySelector(\'#memory-summary-editor\')?.value'), 'summary editor');
  assert.match(original, /【工作偏好】\n合成记忆编辑验收 a/);
  assert.match(original, /【写作习惯】\n合成记忆编辑验收 b/);
  let edited = `${original}\n\n补充：使用完整主题正文。`;
  assert.equal(await evaluate(`(()=>{const area=document.querySelector('#memory-summary-editor');if(!area)return false;area.value=${js(edited)};area.dispatchEvent(new Event('input',{bubbles:true}));return true})()`), true);
  if (process.argv.includes('--revision-conflict')) {
    const before = await invoke('read_desktop_workspace', { workspaceId });
    const changed = before.exchange.memory.find(item => item.title === '工作偏好');
    assert.ok(changed);
    await invoke('mutate_desktop_domain', { args: {
      intentId: `memory-summary-concurrent-update-${Date.now()}`, workspaceId,
      entity: 'memory', action: 'update', objectId: changed.id, expectedRevision: changed.revision,
      fields: { title: changed.title, body: `${changed.body}\n并发更新：保留当前版本。` },
    } });
    assert.equal(await click('[data-action="save-memory-summary-editor"]'), true);
    await until(() => evaluate('document.body.textContent.includes("REVISION_CONFLICT")'), 'visible revision conflict');
    assert.equal(await evaluate('document.querySelector("#memory-summary-editor")?.value'), edited, 'rejected stale edit must retain unsaved draft');
    const rejected = await invoke('read_desktop_workspace', { workspaceId });
    assert.equal(rejected.exchange.memory.filter(item => item.status === 'ACTIVE').length, 2);
    assert.ok(rejected.exchange.memory.find(item => item.id === changed.id).body.includes('并发更新：保留当前版本。'));
    assert.ok(!rejected.exchange.memory.some(item => item.body.includes('补充：使用完整主题正文。')), 'stale editor must not partially commit');
    assert.equal(await click('.dialog [data-action="close-dialog"]'), true);
    assert.equal(await click('[data-action="edit-memory-summary"]'), true);
    original = await until(() => evaluate('document.querySelector("#memory-summary-editor")?.value'), 'fresh revision editor');
    assert.ok(original.includes('并发更新：保留当前版本。'), 'reopened editor must read latest committed revision');
    edited = `${original}\n\n补充：使用完整主题正文。`;
    assert.equal(await evaluate(`(()=>{const area=document.querySelector('#memory-summary-editor');if(!area)return false;area.value=${js(edited)};area.dispatchEvent(new Event('input',{bubbles:true}));return true})()`), true);
  }
  assert.equal(await click('[data-action="save-memory-summary-editor"]'), true);
  await until(async () => {
    const projection = await invoke('read_desktop_workspace', { workspaceId });
    return projection.exchange.memory.filter(item => item.status === 'ACTIVE' && item.scope === 'GLOBAL').length === 1;
  }, 'atomic memory edit');
  const after = await invoke('read_desktop_workspace', { workspaceId });
  const active = after.exchange.memory.filter(item => item.status === 'ACTIVE' && item.scope === 'GLOBAL');
  assert.equal(active.length, 1);
  assert.equal(active[0].title, '概览');
  assert.equal(active[0].body, edited);
  assert.equal(after.exchange.memory.filter(item => item.status === 'DELETED' && item.body?.startsWith(tag)).length, 1);
  console.log(JSON.stringify({ phase, workspaceId, active: 1, deleted: 1, visibleEditor: true, topicsPreserved: true, staleEditRejected: process.argv.includes('--revision-conflict') }));
} else {
  await openSummary();
  const visible = await evaluate('document.querySelector(\'.android-memory-summary-content\')?.innerText');
  assert.match(visible, /工作偏好/);
  assert.match(visible, /写作习惯/);
  const after = await invoke('read_desktop_workspace', { workspaceId });
  const active = after.exchange.memory.filter(item => item.status === 'ACTIVE' && item.scope === 'GLOBAL');
  assert.equal(active.length, 1);
  assert.equal(active[0].title, '概览');
  assert.match(active[0].body, /【工作偏好】\n合成记忆编辑验收 a/);
  assert.match(active[0].body, /【写作习惯】\n合成记忆编辑验收 b/);
  assert.match(active[0].body, /补充：使用完整主题正文。/);
  if (process.argv.includes('--revision-conflict')) assert.ok(active[0].body.includes('并发更新：保留当前版本。'));
  assert.equal(after.exchange.memory.filter(item => item.status === 'DELETED').length, 1);
  console.log(JSON.stringify({ phase, workspaceId, active: 1, deleted: 1, restartTopicsPreserved: true }));
}
