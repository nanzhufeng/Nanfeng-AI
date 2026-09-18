import test from 'node:test';
import assert from 'node:assert/strict';
import vm from 'node:vm';
import { readFile } from 'node:fs/promises';

const source = await readFile(new URL('../src/app.mjs', import.meta.url), 'utf8');
const refresh = source.slice(source.indexOf('async function flushOrdinaryChatRuntimeRefresh()'), source.indexOf('const tauriEvents'));
const polling = source.slice(source.indexOf('async function refreshDesktopRuntimeWhilePending('), source.indexOf('function recomputeConversationFind('));

test('runtime events and the polling backstop share one read; late data cannot replace a newly selected workspace', async () => {
  let release;
  let reads = 0;
  let paints = 0;
  let ticks = 0;
  const state = { current: { summary: { id: 'first' } } };
  const context = vm.createContext({ state,
    ordinaryChatRuntimeRefreshPending: { workspaceId: 'first' },
    ordinaryChatRuntimeRefreshInFlight: false,
    invoke: () => { reads++; return new Promise(resolve => { release = resolve; }); },
    claimSubmittedChatRoute: () => {}, loadConversationReadState: async () => {},
    render: () => { paints++; }, scheduleOrdinaryChatRuntimeRefresh: () => {},
    window: { setTimeout: callback => { ticks++; queueMicrotask(callback); } },
    finished: () => ticks >= 2,
  });
  vm.runInContext(refresh + polling, context);
  const first = vm.runInContext('flushOrdinaryChatRuntimeRefresh()', context);
  await vm.runInContext("refreshDesktopRuntimeWhilePending('first', null, finished)", context);
  assert.equal(reads, 1, 'polling must not issue a concurrent snapshot read');
  state.current = { summary: { id: 'second' } };
  release({ summary: { id: 'first' } });
  await first;
  assert.equal(state.current.summary.id, 'second');
  assert.equal(paints, 0, 'late workspace data must not flash back onto the screen');
});

test('a delivered runtime update selects the connected DOM commit path', async () => {
  const paints = [];
  const context = vm.createContext({ state: { current: { summary: { id: 'first' } } },
    ordinaryChatRuntimeRefreshPending: { workspaceId: 'first' }, ordinaryChatRuntimeRefreshInFlight: false,
    invoke: async () => ({ summary: { id: 'first' } }), claimSubmittedChatRoute: () => {},
    loadConversationReadState: async () => {}, render: options => paints.push(options),
    scheduleOrdinaryChatRuntimeRefresh: () => {},
  });
  vm.runInContext(refresh, context);
  await vm.runInContext('flushOrdinaryChatRuntimeRefresh()', context);
  assert.equal(paints.length, 1);
  assert.equal(paints[0].runtimeRefresh, true);
});
