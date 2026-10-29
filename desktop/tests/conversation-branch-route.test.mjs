import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import vm from 'node:vm';
import { invokeForDataArea, invokeForAuthorizedDataAreaOperation } from '../src/data-area-invoke.mjs';

const source = readFileSync(new URL('../src/app.mjs', import.meta.url), 'utf8');
const branchSource = source.slice(source.indexOf("  if (action === 'branch-from-message') {"),
  source.indexOf("  if (action === 'toggle-temporary-chat') {", source.indexOf("  if (action === 'branch-from-message') {")));

function harness({ refreshFailure = false } = {}) {
  let finish;
  let fail;
  const calls = [];
  const chat = { dataArea: 'CHAT', current: { summary: { id: 'w' } },
    selectedConversationId: 'source', assistantMessageMenu: {}, dialog: {} };
  const bridge = { invoke(command, args, options) {
    calls.push({ command, args, area: options.headers['x-nanfeng-data-area'] });
    return new Promise((resolve, reject) => { finish = resolve; fail = reject; });
  } };
  let context;
  let refreshes = 0;
  context = vm.createContext({ state: chat, native: true, tauriBridge: bridge,
    invokeForAuthorizedDataAreaOperation,
    invoke: (command, args, owner = context.state) => invokeForDataArea(bridge, command, args, owner, () => context.state),
    resolveConversation: () => ({ id: 'source', revision: 3 }),
    intent: () => 'branch-test', render() {},
    refresh: async () => { refreshes += 1; if (refreshFailure) throw new Error('list read failed'); },
  });
  vm.runInContext(`async function branchAction(action, target) { ${branchSource} }`, context);
  return { chat, context, calls, finish: value => finish(value), fail: error => fail(error), get refreshes() { return refreshes; },
    branch: () => context.branchAction('branch-from-message', { dataset: { messageId: 'message-1' } }) };
}

test('a delayed CHAT branch receipt cannot select or error the same-ID WORK conversation', async () => {
  const h = harness();
  const pending = h.branch();
  const work = { dataArea: 'WORK', current: { summary: { id: 'w' } },
    selectedConversationId: 'work-source', status: 'WORK status' };
  h.context.state = work;
  h.finish({ objectId: 'new-branch' });
  await pending;
  assert.equal(h.calls[0].area, 'CHAT');
  assert.equal(h.calls[0].args.args.action, 'branchFromMessage');
  assert.equal(work.selectedConversationId, 'work-source');
  assert.equal(work.status, 'WORK status');
  assert.equal(work.error, undefined);
});

test('a delayed branch cannot replace a different CHAT conversation', async () => {
  const h = harness();
  const pending = h.branch();
  h.chat.selectedConversationId = 'another-conversation';
  h.chat.status = 'another status';
  h.finish({ objectId: 'new-branch' });
  await pending;
  assert.equal(h.chat.selectedConversationId, 'another-conversation');
  assert.equal(h.chat.status, 'another status');
});

test('creating a branch on the current route opens its persisted new conversation', async () => {
  const h = harness();
  const pending = h.branch();
  h.finish({ objectId: 'new-branch' });
  await pending;
  assert.equal(h.chat.selectedConversationId, 'new-branch');
  assert.match(h.chat.status, /本地分支/);
  assert.equal(h.refreshes, 1);
});

test('a rejected old branch cannot put its failure on a newer CHAT conversation', async () => {
  const h = harness();
  const pending = h.branch();
  h.chat.selectedConversationId = 'another-conversation';
  h.chat.error = 'new route notice';
  h.fail(new Error('revision changed'));
  await pending;
  assert.equal(h.chat.error, 'new route notice');
  assert.equal(h.chat.selectedConversationId, 'another-conversation');
});

test('a committed branch with failed list refresh is not reported as a rejected write', async () => {
  const h = harness({ refreshFailure: true });
  const pending = h.branch();
  h.finish({ objectId: 'new-branch' });
  await pending;
  assert.equal(h.chat.selectedConversationId, 'new-branch');
  assert.match(h.chat.error, /已保存在本机.*回读未完成/);
  assert.doesNotMatch(h.chat.error, /创建分支被拒绝/);
});
