import assert from 'node:assert/strict';
import { readFile, writeFile } from 'node:fs/promises';
import { createHash, randomUUID } from 'node:crypto';

const [port, mode, receiptPath] = process.argv.slice(2);
assert.ok(['run', 'cold'].includes(mode));
const hash = value => createHash('sha256').update(JSON.stringify(value)).digest('hex');
async function evaluate(expression) {
  const tabs = await fetch(`http://127.0.0.1:${Number(port)}/json/list`).then(r => r.json());
  const tab = tabs.find(t => t.type === 'page' && t.url === 'http://tauri.localhost/');
  assert.ok(tab, 'isolated Tauri page');
  const socket = new WebSocket(tab.webSocketDebuggerUrl);
  try {
    await new Promise((resolve, reject) => {
      socket.addEventListener('open', resolve, { once: true });
      socket.addEventListener('error', reject, { once: true });
    });
    return await new Promise((resolve, reject) => {
      const timer = setTimeout(() => reject(new Error('CDP timeout')), 30000);
      socket.addEventListener('message', event => {
        const message = JSON.parse(event.data);
        if (message.id !== 1) return;
        clearTimeout(timer);
        if (message.error || message.result?.exceptionDetails) reject(new Error(JSON.stringify(message.result?.exceptionDetails || message.error)));
        else resolve(message.result.result.value);
      });
      socket.send(JSON.stringify({ id: 1, method: 'Runtime.evaluate', params: { expression, awaitPromise: true, returnByValue: true } }));
    });
  } finally { socket.close(); }
}
const expression = (area, command, args = {}) => `window.__TAURI__.core.invoke(${JSON.stringify(command)},${JSON.stringify(args)},${JSON.stringify({ headers: { 'x-nanfeng-data-area': area } })})`;
const invoke = (area, command, args) => evaluate(expression(area, command, args));
const deadline = Date.now() + 30000;
while (true) {
  try { if (await evaluate('Boolean(window.__TAURI__?.core?.invoke)')) break; } catch {}
  assert.ok(Date.now() < deadline, 'native readiness');
  await new Promise(resolve => setTimeout(resolve, 250));
}
const areas = ['CHAT', 'WORK'];
const record = mode === 'cold' ? JSON.parse(await readFile(receiptPath, 'utf8')) : { rounds: [] };
const runtime = await invoke('CHAT', 'read_desktop_runtime_info');
assert.equal(runtime.platform, 'windows');
assert.equal(runtime.startupMode, 'UI_SCHEMA_DIAGNOSTIC_ACCEPTANCE', 'never attach this mutating probe to a NORMAL timer or daily instance');
assert.equal(runtime.automaticWorkSuppressed, true);
const workspace = area => invoke(area, 'read_desktop_workspace', { workspaceId: record[area].workspaceId });
const draft = area => invoke(area, 'read_desktop_conversation_draft', { workspaceId: record[area].workspaceId, conversationId: null });
const mutation = (area, action, c, fields = {}) => invoke(area, 'mutate_desktop_domain', { args: { intentId: `race-${randomUUID()}`, workspaceId: record[area].workspaceId, entity: 'conversation', action, objectId: c?.id, expectedRevision: c?.revision, fields } });
const referenceArgs = (area, c) => ({ workspaceId: record[area].workspaceId, conversationId: c.id, messageId: c.messages[0].id, expectedConversationRevision: c.revision, targetWorkspaceId: record[area === 'CHAT' ? 'WORK' : 'CHAT'].workspaceId });
const find = async (area, id) => (await workspace(area)).exchange.conversations.find(c => c.id === id);
if (mode === 'cold') {
  for (const area of areas) {
    assert.equal(hash((await workspace(area)).exchange), record[area].exchangeHash);
    assert.deepEqual(await draft(area), record[area].draft);
  }
  for (const round of record.rounds) {
    assert.equal(await find(round.area, round.sourceId), undefined);
    await assert.rejects(() => invoke(round.area, 'reference_desktop_message_to_other_area', round.referenceArgs));
  }
  console.log(JSON.stringify({ coldReadback: true, rounds: record.rounds.length }));
} else {
  for (const area of areas) record[area] = { workspaceId: (await invoke(area, 'ensure_desktop_local_workspace')).summary.id };
  for (const area of areas) {
    const target = area === 'CHAT' ? 'WORK' : 'CHAT';
    for (const deletion of ['softDelete', 'purge']) {
      for (const deleteFirst of [false, true]) {
        const text = `synthetic-race-${randomUUID()}`;
        const created = await mutation(area, 'create', null, { title: 'Synthetic race source', projectId: null, firstMessage: text });
        let source = await find(area, created.objectId);
        if (deletion === 'purge') {
          await mutation(area, 'softDelete', source);
          source = await find(area, source.id);
        }
        const before = await draft(target);
        const args = referenceArgs(area, source);
        const references = [expression(area, 'reference_desktop_message_to_other_area', args), expression(area, 'reference_desktop_message_to_other_area', args)];
        const remove = deletion === 'purge'
          ? expression(area, 'permanently_delete_desktop_conversation', { args: { workspaceId: record[area].workspaceId, conversationId: source.id, expectedRevision: source.revision } })
          : expression(area, 'mutate_desktop_domain', { args: { intentId: `race-${randomUUID()}`, workspaceId: record[area].workspaceId, entity: 'conversation', action: 'softDelete', objectId: source.id, expectedRevision: source.revision, fields: {} } });
        const calls = deleteFirst ? [remove, ...references] : [...references, remove];
        const results = await evaluate(`Promise.allSettled([${calls.join(',')}]).then(rows=>rows.map(r=>r.status==='fulfilled'?{status:r.status,value:r.value}:{status:r.status,reason:String(r.reason)}))`);
        const removeIndex = deleteFirst ? 0 : 2;
        assert.equal(results[removeIndex].status, 'fulfilled', JSON.stringify(results));
        const refs = results.filter((_, i) => i !== removeIndex);
        const successes = refs.filter(r => r.status === 'fulfilled').length;
        for (const r of refs.filter(r => r.status === 'rejected')) assert.match(r.reason, /会话已不可用|来源已变化/);
        const after = await draft(target);
        assert.equal((after?.text ?? '').split(`> ${text}\n`).length - 1, successes, 'only successful references append exact body');
        assert.ok((after?.text ?? '').startsWith(before?.text ?? ''), 'prior draft preserved');
        if (!successes) assert.deepEqual(after, before, 'rejected references leave complete draft unchanged');
        for (const key of Object.keys(before ?? {}).filter(k => !['text', 'updatedAtMs'].includes(k))) assert.deepEqual(after[key], before[key], `draft metadata ${key}`);
        await assert.rejects(() => invoke(area, 'reference_desktop_message_to_other_area', args));
        if (deletion === 'softDelete') {
          const deleted = await find(area, source.id);
          assert.equal(deleted.deleted, true);
          await invoke(area, 'permanently_delete_desktop_conversation', { args: { workspaceId: record[area].workspaceId, conversationId: deleted.id, expectedRevision: deleted.revision } });
        } else assert.equal(successes, 0, 'trash source is never referenceable');
        assert.equal(await find(area, source.id), undefined);
        assert.deepEqual(await draft(target), after, 'purge preserves successful snapshots');
        record.rounds.push({ area, deletion, deleteFirst, sourceId: source.id, referenceArgs: args, successes, rejected: refs.length - successes, results });
      }
    }
  }
  for (const area of areas) {
    record[area].exchangeHash = hash((await workspace(area)).exchange);
    record[area].draft = await draft(area);
  }
  await writeFile(receiptPath, JSON.stringify(record, null, 2), { flag: 'wx' });
  console.log(JSON.stringify({ referenceDeleteRace: true, rounds: record.rounds.map(({ area, deletion, deleteFirst, successes, rejected }) => ({ area, deletion, deleteFirst, successes, rejected })) }));
}
