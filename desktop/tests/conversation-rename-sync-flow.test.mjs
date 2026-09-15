import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import vm from 'node:vm';
import { syncSelectedContinuation } from '../src/selected-conversation-continuation.mjs';

// Execute the production UI action with only IPC / unrelated UI boundaries
// replaced. A helper-only test would miss an unwired continuation call.
const source = readFileSync(new URL('../src/app.mjs', import.meta.url), 'utf8');
const actionSource = source.slice(source.indexOf('async function mutateConversationLifecycle('),
  source.indexOf('\nfunction isCloudListConversationTarget('));

function harness({ cloud = false, selected = true, outcome = 'SYNCED' } = {}) {
  const row = { id: 'c', workspaceId: 'w', title: '旧标题', revision: 3, cloudPinned: true };
  const persisted = { ...row };
  const remote = { ...row };
  const state = { current: { summary: { id: 'w' } },
    accountSync: { syncedConversationKeys: selected ? ['w:c'] : [] },
    cloudConversations: [row], dialog: null };
  const context = vm.createContext({ state, native: true, syncSelectedContinuation,
    captureCloudSidebarWorkspace: () => null, ensureConversationActionWorkspace: async () => {},
    restoreCloudSidebarWorkspace: async () => {}, refresh: async () => {}, render: () => {},
    intent: () => 'rename-test', scheduleUnknownSyncReconciliation: () => {},
    persistCloudConversationCache: () => {},
    cloudRowsFromLocalEntries: async () => [{ ...persisted }],
    invoke: async (command, args) => {
      if (command === 'mutate_desktop_domain') {
        Object.assign(persisted, args.args.fields, { revision: 4 });
      } else if (command === 'sync_selected_desktop_conversation') {
        if (outcome === 'SYNCED') Object.assign(remote, persisted);
        return { status: outcome };
      } else throw new Error(`Unexpected IPC: ${command}`);
    },
  });
  vm.runInContext(actionSource, context);
  return { state, remote, persisted, rename: () => context.mutateConversationLifecycle(
    { dataset: { id: 'c', revision: '3', ...(cloud ? { workspaceId: 'w' } : {}) } },
    'update', { title: '新标题' }, '会话已重命名。') };
}

test('rename in local list publishes the new title for an already selected conversation', async () => {
  const h = harness();
  await h.rename();
  assert.equal(h.remote.title, '新标题');
  assert.match(h.state.status, /已同步到云端/);
});

test('rename never enrolls an unselected local conversation', async () => {
  const h = harness({ selected: false });
  await h.rename();
  assert.equal(h.persisted.title, '新标题');
  assert.equal(h.remote.title, '旧标题');
});

test('cloud sidebar replaces the old title with the persisted post-sync projection', async () => {
  const h = harness({ cloud: true });
  await h.rename();
  assert.equal(h.state.cloudConversations[0].title, '新标题');
  assert.equal(h.state.cloudConversations[0].revision, 4);
  assert.equal(h.state.cloudConversations[0].cloudPinned, true);
});

test('failed cloud write reports local save separately and still refreshes the saved title', async () => {
  const h = harness({ outcome: 'REJECTED' });
  await h.rename();
  assert.equal(h.remote.title, '旧标题');
  assert.equal(h.persisted.title, '新标题');
  assert.match(h.state.error, /已保存在本机/);
  assert.equal(h.state.cloudConversations[0].title, '新标题');
});
