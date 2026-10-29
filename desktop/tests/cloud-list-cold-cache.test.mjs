import test from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import vm from 'node:vm';
import { invokeForDataArea } from '../src/data-area-invoke.mjs';

const source = await readFile(new URL('../src/app.mjs', import.meta.url), 'utf8');
const cacheFunctions = source.slice(source.indexOf('function dedupeCloudConversationEntries('),
  source.indexOf('function clearCloudConversationCache('));
const projectionsAndRestore = source.slice(source.indexOf('async function cloudRowsFromLocalEntries('),
  source.indexOf('function accountSyncErrorMessage('));
const readOwnership = source.slice(source.indexOf('const cloudListReadGenerations = new WeakMap();'),
  source.indexOf('let cloudOpenAreaGeneration = 0;'));
const areaKey = source.slice(source.indexOf('function areaCloudConversationCacheKey('),
  source.indexOf('const parityPreferences = '));
const accountInvalidation = source.slice(source.indexOf('function invalidateAccountCloudLists()'),
  source.indexOf('function selectedBackupAreaState('));
const key = 'nanfeng-ai.desktop.sync-cloud-list.v1';
const email = 'synthetic-cold-cache@example.invalid';
const owner = dataArea => ({ dataArea, accountSync: { email }, cloudConversations: [], status: '' });
const cacheKey = area => area === 'WORK' ? `${key}.WORK` : key;
function harness(initialOwner = owner('CHAT'), adapter) {
  const saved = new Map();
  const calls = [];
  const localStorage = { getItem: k => saved.get(k) ?? null, setItem: (k, v) => saved.set(k, v) };
  const context = vm.createContext({ native: true, state: initialOwner,
    cloudConversationCacheKey: key, window: { localStorage },
    dataAreaStates: new Map([[initialOwner.dataArea, initialOwner]]),
    invoke: async (command, args, capturedOwner) => {
      calls.push({ command, args, capturedOwner });
      return adapter ? adapter(command, args, capturedOwner, context) : { projections: [], failedWorkspaceIds: args.workspaceIds };
    },
  });
  vm.runInContext(areaKey + cacheFunctions + readOwnership + accountInvalidation + projectionsAndRestore, context);
  return { saved, calls, context, restore: () => context.restoreCachedCloudConversationList(),
    cache: (area, entries, identity = email, version = 2) => saved.set(cacheKey(area), JSON.stringify({ version, email: identity, entries })) };
}
function material(count) {
  const entries = Array.from({ length: count }, (_, i) => ({ workspaceId: `w-${i}`, conversationId: `c-${i}`, cloudPinned: i % 3 === 0 }));
  const projections = entries.map((e, i) => ({ workspaceId: e.workspaceId, favoriteIds: i % 3 === 1 ? [e.conversationId] : [],
    workspace: { exchange: { conversations: [{ id: e.conversationId, title: `title-${i}`, revision: 7, pinned: !e.cloudPinned,
      messages: [{ id: `user-${i}`, role: 'user', text: `完整问题-${i}`, parentMessageId: null },
        { id: `assistant-${i}`, role: 'assistant', text: `完整回答-${i}`, parentMessageId: `user-${i}`,
          modelUsage: { actualModelId: 'synthetic-model', outputTokens: 3 }, delivery: 'SUCCEEDED' }] }] } } }));
  return { entries, projections };
}

for (const count of [201, 2048, 10000]) {
  test(`cold cache retains all ${count} rows and full owner content using one bounded projection`, async () => {
    const original = owner('CHAT');
    const { entries, projections } = material(count);
    const h = harness(original, async () => ({ projections, failedWorkspaceIds: [] }));
    h.cache('CHAT', entries);
    await h.restore();
    assert.equal(original.cloudConversations.length, count);
    assert.equal(h.calls.length, 1);
    assert.equal(h.calls[0].command, 'read_desktop_cloud_workspace_projections');
    assert.equal(h.calls[0].capturedOwner, original);
    assert.equal(h.calls[0].args.workspaceIds.length, count);
    assert.deepEqual(Array.from(original.cloudConversations, r => r.id), entries.map(e => e.conversationId));
    for (let i = 0; i < count; i++) {
      const row = original.cloudConversations[i];
      assert.deepEqual(row.messages, projections[i].workspace.exchange.conversations[0].messages);
      assert.equal(row.revision, 7);
      assert.equal(row.cloudPinned, entries[i].cloudPinned);
      assert.equal(row.pinned, entries[i].cloudPinned);
      assert.equal(row.favorite, i % 3 === 1);
    }
    const saved = JSON.parse(h.saved.get(key));
    assert.equal(saved.entries.length, count);
    assert.ok(saved.entries.every(e => !('messages' in e) && !('title' in e)));
  });
}

test('deduplicates legacy IDs and isolates invalid or missing local entries without borrowing local pins', async () => {
  const original = owner('WORK');
  const { projections } = material(2);
  const h = harness(original, async () => ({ projections, failedWorkspaceIds: ['missing'] }));
  h.cache('WORK', [{ workspaceId: 'w-1', id: 'c-1', cloudPinned: true }, null, { workspaceId: 7, conversationId: 'bad' },
    { workspaceId: 'w-0', id: 'c-0' }, { workspaceId: 'w-1', id: 'c-1', cloudPinned: false },
    { workspaceId: 'missing', id: 'c-missing' }], email, 1);
  const savedBefore = h.saved.get(`${key}.WORK`);
  await h.restore();
  assert.deepEqual(Array.from(original.cloudConversations, r => r.id), ['c-1', 'c-0']);
  assert.equal(original.cloudConversations[0].cloudPinned, true);
  assert.equal(original.cloudConversations[0].favorite, true);
  assert.equal(original.cloudConversations[1].pinned, false);
  assert.equal(h.saved.get(`${key}.WORK`), savedBefore);
  assert.match(original.status, /1 项暂时未能显示.*原缓存已保留/);
  assert.equal(h.saved.has(key), false);
});

test('a failed local projection preserves the identity cache and surfaces the actual error', async () => {
  const original = owner('CHAT');
  original.cloudConversations = [{ id: 'previous-row' }];
  const h = harness(original, async () => { throw new Error('synthetic readonly failure'); });
  h.cache('CHAT', material(1).entries);
  const savedBefore = h.saved.get(key);
  await assert.rejects(h.restore(), /synthetic readonly failure/);
  assert.equal(original.cloudConversations[0].id, 'previous-row');
  assert.equal(h.saved.get(key), savedBefore);
});

test('different-account, signed-out, malformed and unknown-version caches never read or overwrite rows', async () => {
  for (const input of ['other-account', 'signed-out', 'malformed', 'unknown-version']) {
    const original = owner('CHAT');
    original.cloudConversations = [{ id: 'current-local', workspaceId: 'local' }];
    const h = harness(original);
    h.cache('CHAT', material(1).entries, input === 'other-account' ? 'other@example.invalid' : email, input === 'unknown-version' ? 3 : 2);
    if (input === 'signed-out') original.accountSync = {};
    if (input === 'malformed') h.saved.set(key, '{invalid');
    const savedBefore = h.saved.get(key);
    await h.restore();
    assert.equal(h.calls.length, 0);
    assert.equal(original.cloudConversations[0].id, 'current-local');
    assert.equal(h.saved.get(key), savedBefore);
  }
});

test('oversized unique cache is explicitly refused without discarding IDs or calling native', async () => {
  const original = owner('CHAT');
  original.cloudConversations = [{ id: 'current-local' }];
  const h = harness(original);
  h.cache('CHAT', material(10001).entries);
  const savedBefore = h.saved.get(key);
  await h.restore();
  assert.equal(h.calls.length, 0);
  assert.equal(original.cloudConversations[0].id, 'current-local');
  assert.equal(h.saved.get(key), savedBefore);
  assert.match(original.status, /10,000.*缓存|缓存.*10,000/);
});

for (const direction of ['CHAT', 'WORK']) {
  test(`late ${direction} cache projection cannot write the destination list or either cache`, async () => {
    const original = owner(direction), destination = owner(direction === 'CHAT' ? 'WORK' : 'CHAT');
    const { entries, projections } = material(1);
    let release;
    const pending = new Promise(resolve => { release = resolve; });
    let h;
    const bridge = { invoke: () => pending };
    h = harness(original, (command, args, capturedOwner) => invokeForDataArea(bridge, command, args, capturedOwner, () => h.context.state));
    h.cache(original.dataArea, entries);
    h.cache(destination.dataArea, [{ workspaceId: 'destination', conversationId: 'dest' }]);
    const before = new Map(h.saved);
    const running = h.restore();
    h.context.state = destination;
    release({ projections, failedWorkspaceIds: [] });
    await running;
    assert.equal(original.cloudConversations.length, 0);
    assert.equal(destination.cloudConversations.length, 0);
    assert.deepEqual(h.saved, before);
    assert.equal(h.calls[0].capturedOwner, original);
  });
}

test('same-area sign-out/account invalidation or newer read discards a late cached receipt', async () => {
  for (const change of ['sign-out', 'invalidate', 'newer-read']) {
    const original = owner('CHAT');
    const { entries, projections } = material(1);
    let release;
    const h = harness(original, () => new Promise(resolve => { release = resolve; }));
    h.cache('CHAT', entries);
    const before = h.saved.get(key);
    const running = h.restore();
    if (change === 'sign-out') original.accountSync = {};
    else if (change === 'invalidate') vm.runInContext('invalidateAccountCloudLists()', h.context);
    else {
      vm.runInContext('advanceCloudListRead(state)', h.context);
      original.cloudConversations = [{ id: 'newer-result' }];
    }
    release({ projections, failedWorkspaceIds: [] });
    await running;
    assert.equal(h.saved.get(key), before);
    assert.deepEqual(Array.from(original.cloudConversations, r => r.id), change === 'newer-read' ? ['newer-result'] : []);
  }
});
