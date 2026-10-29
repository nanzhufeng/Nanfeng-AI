import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import vm from 'node:vm';
import { conversationBranchLeaves, orderedConversationMessages } from '../src/conversation-message-order.mjs';

const source = readFileSync(new URL('../src/app.mjs', import.meta.url), 'utf8');
const begin = source.indexOf("  if (action === 'select-conversation-branch') {");
const end = source.indexOf("  if (action === 'reference-message-to-other-area') {", begin);
assert.ok(begin > 0 && end > begin);
const handlerSource = `async function handle(event, target, action) { ${source.slice(begin, end)} }`;
const editBegin = source.indexOf("  if (action === 'save-conversation-user-message-edit') {");
const editEnd = source.indexOf("  if (action === 'select-conversation-branch') {", editBegin);
assert.ok(editBegin > 0 && editEnd > editBegin);
const editHandlerSource = `async function handleEdit(event, target, action) { ${source.slice(editBegin, editEnd)} }`;

function harness() {
  const pending = [];
  const calls = [];
  const conversation = { id: 'c1', revision: 2, currentLeafId: 'a1', messages: [
    { id: 'u1', parentId: null, role: 'user', blocks: [{ kind: 'TEXT', text: '问题' }] },
    { id: 'a1', parentId: 'u1', role: 'assistant', blocks: [{ kind: 'TEXT', text: '旧答' }] },
    { id: 'a2', parentId: 'u1', role: 'assistant', blocks: [{ kind: 'TEXT', text: '新答' }] },
  ] };
  const dialog = { kind: 'conversation-branches', workspaceId: 'w1', conversationId: 'c1', submitting: false, failure: '' };
  const owner = { dataArea: 'CHAT', current: { summary: { id: 'w1' }, exchange: { conversations: [conversation] } }, selectedConversationId: 'c1', dialog };
  const context = vm.createContext({ state: owner, native: true, tauriBridge: {},
    conversationBranchLeaves, resolveConversation: (workspace, id) => workspace.exchange.conversations.find(item => item.id === id),
    intent: () => 'switch-branch-test', render() {}, recomputeConversationFind() {},
    invokeForAuthorizedDataAreaOperation: (_bridge, command, args, sourceOwner) => {
      calls.push({ command, args, sourceOwner });
      return new Promise(resolve => pending.push(resolve));
    },
    invoke: async command => command === 'read_desktop_workbench_history' ? { canUndo: true, canRedo: false }
      : { summary: { id: 'w1' }, exchange: { conversations: [{ ...conversation, revision: 3, currentLeafId: 'a2' }] } },
  });
  vm.runInContext(handlerSource, context);
  const run = () => context.handle({ preventDefault() {} }, { dataset: { leafId: 'a2' } }, 'select-conversation-branch');
  return { owner, context, calls, pending, run };
}

test('selecting a saved leaf changes the visible path and keeps the old answer', async () => {
  const h = harness();
  const operation = h.run();
  assert.equal(h.calls[0].args.args.action, 'switchLeaf');
  assert.equal(h.calls[0].args.args.expectedRevision, 2);
  h.pending[0]({ objectId: 'c1', revision: 3 });
  await operation;
  const conversation = h.owner.current.exchange.conversations[0];
  assert.deepEqual(orderedConversationMessages(conversation).map(message => message.id), ['u1', 'a2']);
  assert.deepEqual(conversationBranchLeaves(conversation).map(message => message.id), ['a1', 'a2']);
  assert.equal(h.owner.dialog, null);
  assert.equal(h.owner.history.canUndo, true);
});

test('a late switch receipt cannot project into another data area', async () => {
  const h = harness();
  const operation = h.run();
  const work = { dataArea: 'WORK', current: { summary: { id: 'w1' }, exchange: { conversations: [] } }, selectedConversationId: 'work-chat' };
  h.context.state = work;
  h.pending[0]({ objectId: 'c1', revision: 3 });
  await operation;
  assert.equal(work.selectedConversationId, 'work-chat');
  assert.equal(work.error, undefined);
  assert.equal(h.owner.current.exchange.conversations[0].currentLeafId, 'a1');
});

test('saving a user edit reads the new branch while preserving the old path', async () => {
  const h = harness();
  const old = h.owner.current.exchange.conversations[0];
  h.owner.dialog = { kind: 'edit-conversation-user-message', workspaceId: 'w1', conversationId: 'c1', messageId: 'u1', text: '问题', submitting: false, failure: '' };
  h.context.document = { querySelector: () => ({ value: '修改的问题' }) };
  h.context.invoke = async command => command === 'read_desktop_workbench_history' ? { canUndo: true, canRedo: false }
    : { summary: { id: 'w1' }, exchange: { conversations: [{ ...old, revision: 3, currentLeafId: 'u2', messages: [
    ...old.messages, { id: 'u2', parentId: null, role: 'user', blocks: [{ kind: 'TEXT', text: '修改的问题' }] },
  ] }] } };
  vm.runInContext(editHandlerSource, h.context);
  const operation = h.context.handleEdit({ preventDefault() {} }, {}, 'save-conversation-user-message-edit');
  assert.equal(h.calls[0].args.args.action, 'editUserMessage');
  assert.equal(h.calls[0].args.args.fields.messageId, 'u1');
  assert.equal(h.calls[0].args.args.fields.text, '修改的问题');
  h.pending[0]({ objectId: 'c1', revision: 3 });
  await operation;
  const conversation = h.owner.current.exchange.conversations[0];
  assert.deepEqual(orderedConversationMessages(conversation).map(message => message.id), ['u2']);
  assert.deepEqual(conversationBranchLeaves(conversation).map(message => message.id), ['a1', 'a2', 'u2']);
  assert.equal(h.owner.dialog, null);
  assert.equal(h.owner.history.canUndo, true);
});

test('a late user edit receipt cannot replace another data area', async () => {
  const h = harness();
  h.owner.dialog = { kind: 'edit-conversation-user-message', workspaceId: 'w1', conversationId: 'c1', messageId: 'u1', text: '问题', submitting: false, failure: '' };
  h.context.document = { querySelector: () => ({ value: '修改的问题' }) };
  vm.runInContext(editHandlerSource, h.context);
  const operation = h.context.handleEdit({ preventDefault() {} }, {}, 'save-conversation-user-message-edit');
  const work = { dataArea: 'WORK', current: { summary: { id: 'w1' }, exchange: { conversations: [] } }, selectedConversationId: 'work-chat' };
  h.context.state = work;
  h.pending[0]({ objectId: 'c1', revision: 3 });
  await operation;
  assert.equal(work.selectedConversationId, 'work-chat');
  assert.equal(work.error, undefined);
  assert.equal(h.owner.current.exchange.conversations[0].currentLeafId, 'a1');
});

for (const operationKind of ['edit', 'branch']) {
  for (const delayedStage of ['write', 'workspace', 'history']) {
    test(`${operationKind} receipt at ${delayedStage} preserves a newer dialog and its unsaved text`, async () => {
      const h = harness();
      const originalInvoke = h.context.invoke;
      let readStarted;
      const started = new Promise(resolve => { readStarted = resolve; });
      let finishRead;
      const read = new Promise(resolve => { finishRead = resolve; });
      h.context.invoke = async (...args) => {
        if ((delayedStage === 'workspace' && args[0] === 'read_desktop_workspace')
            || (delayedStage === 'history' && args[0] === 'read_desktop_workbench_history')) {
          readStarted(args);
          return read;
        }
        return originalInvoke(...args);
      };
      if (operationKind === 'edit') {
        h.owner.dialog = { kind: 'edit-conversation-user-message', workspaceId: 'w1', conversationId: 'c1', messageId: 'u1', text: '旧编辑', submitting: false, failure: '' };
        h.context.document = { querySelector: () => ({ value: '旧提交正文' }) };
        vm.runInContext(editHandlerSource, h.context);
      }
      const operation = operationKind === 'edit'
        ? h.context.handleEdit({ preventDefault() {} }, {}, 'save-conversation-user-message-edit')
        : h.run();
      if (delayedStage !== 'write') {
        h.pending[0]({ objectId: 'c1', revision: 3 });
        await started;
      }
      const currentBeforeNewDialog = h.owner.current;
      const newerDialog = { kind: 'edit-conversation-user-message', workspaceId: 'w1', conversationId: 'c1', messageId: 'u1', text: '后来未提交的正文', submitting: false, failure: '' };
      h.owner.dialog = newerDialog;
      h.owner.status = '新编辑状态';
      h.owner.error = '新编辑提示';
      if (delayedStage === 'write') h.pending[0]({ objectId: 'c1', revision: 3 });
      else finishRead(await originalInvoke(delayedStage === 'workspace' ? 'read_desktop_workspace' : 'read_desktop_workbench_history'));
      await operation;
      assert.equal(h.owner.dialog, newerDialog);
      assert.equal(newerDialog.text, '后来未提交的正文');
      assert.equal(newerDialog.submitting, false);
      assert.equal(h.owner.current, currentBeforeNewDialog);
      assert.equal(h.owner.status, '新编辑状态');
      assert.equal(h.owner.error, '新编辑提示');
    });
  }
}
