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
  const result = await evaluate(`window.__TAURI__.core.invoke(${js(command)},${js(args)},${js({ headers: { 'x-nanfeng-data-area': 'WORK' } })}).catch(error=>({__error:String(error)}))`);
  if (result?.__error) throw new Error(`${command}: ${result.__error}`);
  return result;
}
async function fill(selector, value) {
  assert.equal(await evaluate(`(()=>{const input=document.querySelector(${js(selector)});if(!input)return false;input.value=${js(value)};input.dispatchEvent(new Event('input',{bubbles:true}));input.dispatchEvent(new Event('change',{bubbles:true}));return true})()`), true);
}
await until(() => evaluate('Boolean(window.__TAURI__?.core?.invoke)'), 'native IPC');
const local = await invoke('ensure_desktop_local_workspace');
const workspaceId = local.summary.id;
if (phase === 'reopen') {
  assert.equal(workspaceId, workspaceFromFirstRun);
  const current = await invoke('read_desktop_workspace', { workspaceId });
  assert.equal(current.exchange.knowledge.length, 2);
  assert.equal(current.exchange.relations.length, 4);
  const revoked = current.exchange.relations.find(item=>item.kind==='RELATED');
  assert.equal(revoked.status, 'REVOKED');assert.ok(revoked.revision >= 4);
  const active=current.exchange.relations.filter(item=>item.status==='ACTIVE');
  assert.equal(active.filter(item=>item.kind==='DERIVED_FROM').length,1);
  const references=active.filter(item=>item.kind==='REFERENCES');
  assert.equal(references.length,2);
  assert.equal(references[0].fromId,references[1].toId);
  assert.equal(references[0].toId,references[1].fromId);
  console.log(JSON.stringify({phase,workspaceId,processRestartRetainedRelation:true,revoked:true}));
  process.exit(0);
}
await until(() => evaluate('Boolean(document.querySelector("[data-action=show-work]"))'), 'WORK entry');
assert.equal(await click('[data-action="show-work"]'), true);
await until(() => evaluate('Boolean(document.querySelector("[data-action=show-knowledge]"))'), 'WORK knowledge entry');
assert.equal(await click('[data-action="show-knowledge"]'), true);
await until(() => evaluate('Boolean(document.querySelector("[data-action=new-knowledge]"))'), 'new knowledge');
for (const suffix of ['甲','乙']) {
  assert.equal(await click('[data-action="new-knowledge"]'), true);
  await until(() => evaluate('Boolean(document.querySelector("#knowledge-title"))'), 'knowledge editor');
  await fill('#knowledge-title', `合成关系资料${suffix}`);
  await fill('#knowledge-body', `仅用于本机知识关系验收${suffix}`);
  assert.equal(await click('[data-action="save-knowledge"]'), true);
  await until(async()=> (await invoke('read_desktop_workspace',{workspaceId})).exchange.knowledge.some(item=>item.title===`合成关系资料${suffix}`), 'GUI knowledge committed');
  await until(() => evaluate('!document.querySelector("#knowledge-title")'), 'editor dismissed');
}
const knowledge=(await invoke('read_desktop_workspace',{workspaceId})).exchange.knowledge;
const [first,second]=knowledge;
async function relationEditor(from,to,kind='RELATED') {
  assert.equal(await click('.workspace-content-actions [data-action="new-relation"]'), true);
  await until(() => evaluate('Boolean(document.querySelector("#relation-from"))'), 'relation editor');
  await fill('#relation-from',from);await fill('#relation-to',to);await fill('#relation-kind',kind);
}
await relationEditor(second.id,first.id);
assert.equal(await click('[data-action="save-relation"]'), true);
const created=await until(async()=> (await invoke('read_desktop_workspace',{workspaceId})).exchange.relations.find(item=>item.status==='ACTIVE'), 'GUI relation committed');
assert.deepEqual([created.fromId,created.toId],[first.id,second.id].sort());
await until(() => evaluate('!document.querySelector("#relation-from")'), 'relation editor dismissed');
await relationEditor(first.id,second.id);
assert.equal(await click('[data-action="save-relation"]'), true);
await until(()=>evaluate('String(globalThis.__nanfengDesktopWorkState?.error || "").includes("活动关系重复")'), 'duplicate rejected visibly');
assert.equal((await invoke('read_desktop_workspace',{workspaceId})).exchange.relations.length,1);
assert.equal(await evaluate('Boolean(document.querySelector("#relation-from"))'),true,'rejected duplicate keeps editor');
assert.equal(await click('.dialog [data-action="close-dialog"]'),true);
assert.equal(await click(`[data-action="delete-relation"][data-id="${created.id}"]`),true);
await until(async()=> (await invoke('read_desktop_workspace',{workspaceId})).exchange.relations.find(item=>item.id===created.id)?.status==='REVOKED','GUI revoke');
await invoke('undo_desktop_domain',{args:{intentId:`relation-undo-${Date.now()}`,workspaceId}});
assert.equal((await invoke('read_desktop_workspace',{workspaceId})).exchange.relations.find(item=>item.id===created.id).status,'ACTIVE');
await invoke('redo_desktop_domain',{args:{intentId:`relation-redo-${Date.now()}`,workspaceId}});
assert.equal((await invoke('read_desktop_workspace',{workspaceId})).exchange.relations.find(item=>item.id===created.id).status,'REVOKED');
for (const [kind,from,to] of [['DERIVED_FROM',second.id,first.id],['REFERENCES',second.id,first.id],['REFERENCES',first.id,second.id]]) {
  await relationEditor(from,to,kind);
  assert.equal(await click('[data-action="save-relation"]'),true);
  const result=await until(async()=> (await invoke('read_desktop_workspace',{workspaceId})).exchange.relations.find(item=>item.kind===kind&&item.fromId===from&&item.toId===to&&item.status==='ACTIVE'), `GUI directed ${kind}`);
  assert.equal(result.fromId,from);assert.equal(result.toId,to);
  await until(()=>evaluate('!document.querySelector("#relation-from")'), 'directed editor dismissed');
}
const chat=await evaluate(`window.__TAURI__.core.invoke('ensure_desktop_local_workspace',{},${js({headers:{'x-nanfeng-data-area':'CHAT'}})})`);
const chatProjection=await evaluate(`window.__TAURI__.core.invoke('read_desktop_workspace',${js({workspaceId:chat.summary.id})},${js({headers:{'x-nanfeng-data-area':'CHAT'}})})`);
assert.equal(chatProjection.exchange.knowledge.length,0);assert.equal(chatProjection.exchange.relations.length,0);
console.log(JSON.stringify({phase,workspaceId,guiKnowledgeCreated:2,guiRelationCreated:true,symmetricDuplicateRejected:true,guiRevoked:true,ipcUndoRedo:true,directedKindsRetained:true,chatUnchanged:true}));
