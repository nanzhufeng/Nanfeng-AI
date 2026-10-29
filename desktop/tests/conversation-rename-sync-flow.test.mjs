import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import vm from 'node:vm';
import { syncSelectedContinuation } from '../src/selected-conversation-continuation.mjs';
import { invokeForDataArea, invokeForAuthorizedDataAreaOperation } from '../src/data-area-invoke.mjs';

// Execute the production UI action with only IPC / unrelated UI boundaries
// replaced. A helper-only test would miss an unwired continuation call.
const source = readFileSync(new URL('../src/app.mjs', import.meta.url), 'utf8').replaceAll('\r\n', '\n');
const actionSource = source.slice(source.indexOf('async function mutateConversationLifecycle('),
  source.indexOf('\nfunction isCloudListConversationTarget('));
const pinSource = source.slice(source.indexOf('function isCloudListConversationTarget('),
  source.indexOf('\nasync function ensureConversationActionWorkspace('));

function harness({ cloud = false, selected = true, outcome = 'SYNCED', deferredMutation = null } = {}) {
  const row = { id: 'c', workspaceId: 'w', title: '旧标题', revision: 3, cloudPinned: true };
  const persisted = { ...row };
  const remote = { ...row };
  const calls = [];
  const state = { dataArea: 'CHAT', current: { summary: { id: 'w' } },
    accountSync: { syncedConversationKeys: selected ? ['w:c'] : [] },
    cloudConversations: [row], dialog: null };
  const bridge = { invoke: async (command, args, options) => {
    calls.push({ command, area: options?.headers?.['x-nanfeng-data-area'] });
    if (command === 'mutate_desktop_domain') {
      Object.assign(persisted, args.args.fields, { revision: 4 });
      if (deferredMutation) return deferredMutation;
    } else if (command === 'sync_selected_desktop_conversation') {
      if (outcome === 'SYNCED') Object.assign(remote, persisted);
      return { status: outcome };
    } else throw new Error(`Unexpected IPC: ${command}`);
  } };
  let context;
  context = vm.createContext({ state, native: true, syncSelectedContinuation, tauriBridge: bridge,
    invokeForAuthorizedDataAreaOperation,
    captureCloudSidebarWorkspace: () => null, ensureConversationActionWorkspace: async () => true,
    restoreCloudSidebarWorkspace: async () => {}, refresh: async () => {}, render: () => {},
    intent: () => 'rename-test', scheduleUnknownSyncReconciliation: () => {},
    persistCloudConversationCache: () => {},
    cloudRowsFromLocalEntries: async () => [{ ...persisted }],
    invoke: (command, args, owner = context.state) => invokeForDataArea(bridge, command, args, owner, () => context.state),
  });
  vm.runInContext(actionSource, context);
  return { state, context, calls, remote, persisted, rename: () => context.mutateConversationLifecycle(
    { dataset: { id: 'c', revision: '3', ...(cloud ? { workspaceId: 'w' } : {}) } },
    'update', { title: '新标题' }, '会话已重命名。') };
}

test('a late CHAT rename receipt cannot repaint the same-ID WORK conversation', async () => {
  let releaseMutation;
  const deferredMutation = new Promise(resolve => { releaseMutation = resolve; });
  const h = harness({ selected: false, deferredMutation });
  const renaming = h.rename();
  await new Promise(resolve => setImmediate(resolve));
  const work = { dataArea: 'WORK', current: { summary: { id: 'w' } },
    cloudConversations: [{ id: 'c', workspaceId: 'w', title: 'WORK title', revision: 8 }],
    accountSync: { syncedConversationKeys: [] }, dialog: { kind: 'work-dialog' } };
  h.context.state = work;
  releaseMutation();
  await renaming;
  assert.equal(h.persisted.title, '新标题');
  assert.equal(work.cloudConversations[0].title, 'WORK title');
  assert.equal(work.dialog.kind, 'work-dialog');
  assert.equal(work.status, undefined);
  assert.equal(work.error, undefined);
});

test('inline rename keeps its selected-cloud continuation but cannot close a new dialog after navigation', async () => {
  let releaseMutation;
  const h = harness({ selected: true, deferredMutation: new Promise(resolve => { releaseMutation = resolve; }) });
  let current = true;
  const renaming = h.context.mutateConversationLifecycle({dataset:{id:'c',revision:'3'}},'update',{title:'新标题'},'会话已重命名。',{presentationCurrent:()=>current});
  await new Promise(resolve=>setImmediate(resolve));
  current=false;h.state.selectedConversationId='another';h.state.dialog={kind:'new-dialog'};
  releaseMutation();const result=await renaming;
  assert.equal(result.localSaved,true);assert.equal(h.persisted.title,'新标题');assert.equal(h.remote.title,'新标题');
  assert.equal(h.state.dialog.kind,'new-dialog');assert.equal(h.state.status,undefined);assert.equal(h.state.error,undefined);
});

test('switching away after an authorized rename still syncs its original selected conversation', async () => {
  let releaseMutation;
  const deferredMutation = new Promise(resolve => { releaseMutation = resolve; });
  const h = harness({ selected: true, deferredMutation });
  const renaming = h.rename();
  await new Promise(resolve => setImmediate(resolve));
  h.context.state = { dataArea: 'WORK', current: { summary: { id: 'w' } },
    accountSync: { syncedConversationKeys: ['w:c'] }, cloudConversations: [] };
  releaseMutation();
  await renaming;
  assert.equal(h.persisted.title, '新标题');
  assert.equal(h.remote.title, '新标题');
  assert.equal(h.calls.find(call => call.command === 'sync_selected_desktop_conversation')?.area, 'CHAT');
  assert.equal(h.context.state.status, undefined);
});

test('a late cloud-list pin receipt cannot repaint the same-ID WORK list', async () => {
  let releasePin;
  const chat = { dataArea: 'CHAT', sidebarConversationList: 'cloud',
    cloudConversations: [{ id: 'c', workspaceId: 'w', pinned: false }] };
  const work = { dataArea: 'WORK', sidebarConversationList: 'cloud',
    cloudConversations: [{ id: 'c', workspaceId: 'w', pinned: false }] };
  const context = vm.createContext({ state: chat, native: true,
    invoke: () => new Promise(resolve => { releasePin = resolve; }),
    persistCloudConversationCache() {}, render() {},
  });
  vm.runInContext(pinSource, context);
  const pinning = vm.runInContext("setCloudListConversationPinned({ dataset: { id: 'c', workspaceId: 'w' } }, true)", context);
  context.state = work;
  releasePin(['c']);
  await pinning;
  assert.equal(work.cloudConversations[0].pinned, false);
  assert.equal(work.status, undefined);
});

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
