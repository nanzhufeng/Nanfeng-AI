import test from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import vm from 'node:vm';
import { invokeForDataArea } from '../src/data-area-invoke.mjs';

const source = await readFile(new URL('../src/app.mjs', import.meta.url), 'utf8');
const dedupe = source.slice(source.indexOf('function dedupeCloudConversationEntries('), source.indexOf('function readCloudConversationCache('));
const projection = source.slice(source.indexOf('async function cloudRowsFromLocalEntries('), source.indexOf('async function restoreCachedCloudConversationList('));
function harness(invoke, owner = { dataArea: 'WORK' }) {
  const context = vm.createContext({ native: true, state: owner, invoke });
  vm.runInContext(`${dedupe}\n${projection}`, context);
  return { read: context.cloudRowsFromLocalEntries, owner, context };
}

test('256 distinct workspaces use one area-owned IPC and preserve complete conversations', async () => {
  const calls = [];
  const entries = Array.from({ length: 256 }, (_, i) => ({ workspaceId: `w-${i}`, conversationId: `c-${i}`, cloudPinned: i === 0 }));
  const conversations = entries.map((entry, i) => ({ id: entry.conversationId, title: `t-${i}`, pinned: i !== 0,
    messages: [{ id: 'm1', role: 'assistant', text: '完整正文', modelUsage: { actualModelId: 'synthetic-model' }, parentMessageId: null }], revision: 7 }));
  const { read, owner } = harness(async (command, args, capturedOwner) => {
    calls.push({ command, args, capturedOwner });
    if (command === 'read_desktop_workspace') return { exchange: { conversations: [conversations[Number(args.workspaceId.slice(2))]] } };
    if (command === 'read_desktop_favorite_conversation_ids') return ['c-1'];
    return { projections: entries.map((entry, i) => ({ workspaceId: entry.workspaceId,
      workspace: { exchange: { conversations: [conversations[i]] } }, favoriteIds: i === 1 ? ['c-1'] : [] })), failedWorkspaceIds: [] };
  });
  const rows = await read(entries);
  assert.equal(rows.length, 256);
  assert.equal(calls.length, 1);
  assert.equal(calls[0].command, 'read_desktop_cloud_workspace_projections');
  assert.deepEqual(Array.from(calls[0].args.workspaceIds), entries.map(entry => entry.workspaceId));
  assert.equal(calls[0].capturedOwner, owner);
  assert.deepEqual(rows[0].messages, conversations[0].messages);
  assert.equal(rows[0].revision, 7);
  assert.equal(rows[0].pinned, true);
  assert.equal(rows[0].favorite, false);
  assert.equal(rows[1].pinned, false);
  assert.equal(rows[1].favorite, true);
});

test('an area switch rejects the late batch through the actual area adapter', async () => {
  let release;
  const pending = new Promise(resolve => { release = resolve; });
  const calls = [];
  const bridge = { invoke(command, args, options) { calls.push({ command, args, options }); return pending; } };
  const original = { dataArea: 'CHAT' };
  const h = harness((command, args, owner) => invokeForDataArea(bridge, command, args, owner, () => h.context.state), original);
  const reading = h.read([{ workspaceId: 'w', conversationId: 'c' }]);
  h.context.state = { dataArea: 'WORK' };
  release({ projections: [{ workspaceId: 'w', workspace: { exchange: { conversations: [{ id: 'c' }] } }, favoriteIds: [] }] });
  await assert.rejects(reading, { code: 'STALE_DATA_AREA' });
  assert.equal(calls.length, 1);
  assert.equal(calls[0].options.headers['x-nanfeng-data-area'], 'CHAT');
});

test('deduplicates workspace reads and entries while retaining entry order and bad-workspace isolation', async () => {
  const calls = [];
  const { read } = harness(async (command, args) => {
    calls.push({ command, args });
    return { projections: [{ workspaceId: 'good', workspace: { exchange: { conversations: [{ id: 'c1' }, { id: 'c2' }] } }, favoriteIds: ['c2'] }], failedWorkspaceIds: ['bad'] };
  });
  const rows = await read([{ workspaceId: 'good', conversationId: 'c2' }, { workspaceId: 'bad', conversationId: 'lost' },
    { workspaceId: 'good', conversationId: 'c1', cloudPinned: true }, { workspaceId: 'good', conversationId: 'c2', cloudPinned: true }, null]);
  assert.equal(calls.length, 1);
  assert.deepEqual(Array.from(calls[0].args.workspaceIds), ['good', 'bad']);
  assert.deepEqual(Array.from(rows, row => row.id), ['c2', 'c1']);
  assert.equal(rows[0].cloudPinned, false);
});

test('empty/invalid entries do not read SQLite and explicit original owner is retained', async () => {
  const calls = [];
  const { read } = harness(async (command, args, owner) => { calls.push(owner); return { projections: [], failedWorkspaceIds: ['w'] }; });
  assert.equal((await read(null)).length, 0);
  assert.equal((await read([{ workspaceId: 'w' }])).length, 0);
  assert.equal(calls.length, 0);
  const original = { dataArea: 'CHAT' };
  await read([{ workspaceId: 'w', conversationId: 'c' }], original);
  assert.equal(calls[0], original);
});
