import assert from 'node:assert/strict';
import { readFile, stat, writeFile } from 'node:fs/promises';
import { join } from 'node:path';

const [portText, root, workspaceId, firstId, secondId, assetSha256] = process.argv.slice(2);
const port = Number(portText);
assert.ok(Number.isInteger(port) && port > 0 && root && workspaceId && firstId && secondId && /^[a-f0-9]{64}$/i.test(assetSha256));

async function evaluate(expression) {
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
      const timer = setTimeout(() => reject(new Error('CDP evaluation timed out')), 20000);
      socket.addEventListener('message', event => {
        const message = JSON.parse(event.data);
        if (message.id !== 1) return;
        clearTimeout(timer);
        if (message.error || message.result?.exceptionDetails) reject(new Error(JSON.stringify(message.result?.exceptionDetails || message.error)));
        else resolve(message.result?.result?.value);
      });
      socket.send(JSON.stringify({ id: 1, method: 'Runtime.evaluate', params: { expression, awaitPromise: true, returnByValue: true } }));
    });
  } finally {
    socket.close();
  }
}

let projection;
const deadline = Date.now() + 30000;
while (Date.now() < deadline) {
  try {
    projection = await evaluate(`window.__TAURI__.core.invoke('read_desktop_workspace',${JSON.stringify({ workspaceId })},${JSON.stringify({ headers: { 'x-nanfeng-data-area': 'CHAT' } })})`);
    if (projection?.exchange?.conversations) break;
  } catch { /* Startup may still be opening the isolated store. */ }
  await new Promise(resolve => setTimeout(resolve, 250));
}
assert.ok(projection?.exchange?.conversations, 'same-root process restart did not open CHAT');
assert.ok(!projection.exchange.conversations.some(conversation => [firstId, secondId].includes(conversation.id)));
await assert.rejects(stat(join(root, 'assets', assetSha256)), { code: 'ENOENT' });
const invoke = (command, args = {}) => evaluate(`window.__TAURI__.core.invoke(${JSON.stringify(command)},${JSON.stringify(args)},${JSON.stringify({ headers: { 'x-nanfeng-data-area': 'CHAT' } })})`);
const purgeHistory = await invoke('read_desktop_workbench_history', { workspaceId });
assert.equal(purgeHistory.canUndo, false, 'permanent cleanup must retire undo snapshots');
assert.equal(purgeHistory.canRedo, false, 'permanent cleanup must retire redo snapshots');
for (const [command, direction] of [['undo_desktop_domain', 'undo'], ['redo_desktop_domain', 'redo']]) {
  await assert.rejects(invoke(command, { args: { intentId: `purge-reopen-${direction}-${Date.now()}`, workspaceId } }), new RegExp(`没有可 ${direction} 的本地动作`));
}
const tag = Date.now();
const exchangePath = join(root, `synthetic-cross-workspace-${tag}.nfai-exchange`);
await invoke('export_desktop_workspace_to_selected_path', { workspaceId, selectedPath: exchangePath });
const staged = await invoke('stage_preflight_selected_exchange', { selectedPath: exchangePath });
const imported = await invoke('import_staged_exchange_as_new_workspace', { args: { stagingId: staged.stagingId, workspaceTitle: `合成第二工作区 ${tag}` } });
const otherWorkspaceId = imported.summary.id;
assert.notEqual(otherWorkspaceId, workspaceId);
const source = join(root, `synthetic-cross-workspace-asset-${tag}.txt`);
const bytes = Buffer.from(`synthetic-cross-workspace-private-${tag}`, 'utf8');
await writeFile(source, bytes);
const records = [];
for (const [index, owner] of [workspaceId, otherWorkspaceId].entries()) {
  const prefix = `cwp-${index}-${tag}`;
  const created = await invoke('mutate_desktop_domain', { args: {
    intentId: `${prefix}-create`, workspaceId: owner, entity: 'conversation', action: 'create',
    objectId: null, expectedRevision: null, fields: { title: `合成共享资产 ${index}`, projectId: null, firstMessage: `合成会话 ${index}` },
  } });
  const attachment = await invoke('import_desktop_conversation_attachment', { args: { workspaceId: owner, selectedPath: source } });
  const appended = await invoke('mutate_desktop_domain', { args: {
    intentId: `${prefix}-append`, workspaceId: owner, entity: 'conversation', action: 'appendMessage',
    objectId: created.objectId, expectedRevision: created.revision,
    fields: { text: `合成引用 ${index}`, role: 'user', attachmentIds: [attachment.id] },
  } });
  const deleted = await invoke('mutate_desktop_domain', { args: {
    intentId: `${prefix}-delete`, workspaceId: owner, entity: 'conversation', action: 'softDelete',
    objectId: created.objectId, expectedRevision: appended.revision, fields: {},
  } });
  records.push({ workspaceId: owner, conversationId: created.objectId, revision: deleted.revision, sha256: attachment.sha256 });
}
assert.equal(records[0].sha256, records[1].sha256, 'same bytes must share one content hash');
const sharedAssetPath = join(root, 'assets', records[0].sha256);
assert.deepEqual(await readFile(sharedAssetPath), bytes);
const firstPurge = await invoke('permanently_delete_desktop_conversation', { args: {
  workspaceId: records[0].workspaceId, conversationId: records[0].conversationId,
  expectedRevision: records[0].revision,
} });
assert.equal(firstPurge.removedAssetCount, 0, 'second workspace still references the asset');
assert.deepEqual(await readFile(sharedAssetPath), bytes);
const otherProjection = await invoke('read_desktop_workspace', { workspaceId: otherWorkspaceId });
assert.ok(otherProjection.exchange.conversations.some(conversation => conversation.id === records[1].conversationId && conversation.deleted));
const lastPurge = await invoke('permanently_delete_desktop_conversation', { args: {
  workspaceId: records[1].workspaceId, conversationId: records[1].conversationId,
  expectedRevision: records[1].revision,
} });
assert.equal(lastPurge.removedAssetCount, 1);
await assert.rejects(stat(sharedAssetPath), { code: 'ENOENT' });
process.stdout.write(JSON.stringify({
  workspaceId, processRestartRetainedPurge: true, purgeUndoRedoRejected: true, assetStillAbsent: true,
  crossWorkspace: { workspaceIds: records.map(record => record.workspaceId), sharedSha256: records[0].sha256,
    firstRemovedAssetCount: firstPurge.removedAssetCount, lastRemovedAssetCount: lastPurge.removedAssetCount,
    retainedAfterFirstPurge: true, removedAfterLastPurge: true },
}) + '\n');
