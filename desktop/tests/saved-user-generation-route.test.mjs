import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import vm from 'node:vm';

const source = readFileSync(new URL('../src/app.mjs', import.meta.url), 'utf8');
const begin = source.indexOf("  if (action === 'change-model-completed-answer') {");
const end = source.indexOf("  if (action === 'copy-message') {", begin);
assert.ok(begin > 0 && end > begin);
const handler = `async function handle(event, target, action) { ${source.slice(begin, end)} }`;
const dialogBegin = source.indexOf("  if (state.dialog?.kind === 'change-answer-model') {");
const dialogEnd = source.indexOf("  if (state.dialog?.kind === 'source-links') {", dialogBegin);
assert.ok(dialogBegin > 0 && dialogEnd > dialogBegin);
const dialogRenderer = `function renderChoiceDialog() { ${source.slice(dialogBegin, dialogEnd)} }`;

function harness(regenerate = false) {
  const calls = [];
  let complete;
  const completions = [];
  const owner = { dataArea: 'CHAT', current: { summary: { id: 'w1' }, exchange: { conversations: [
    { id: 'c1', revision: 4, currentLeafId: 'edited-user', messages: [
      { id: 'edited-user', role: 'user', parentId: null, blocks: [{ kind: 'TEXT', text: '修改的问题' }] },
    ] },
  ] } }, selectedConversationId: 'c1', composerDraft: '另一份未发送草稿', composerAttachments: [],
    assistantMessageMenu: { messageId: 'edited-user' }, accountSync: { email: 'synthetic@example.invalid', syncedConversationKeys: [] } };
  const context = vm.createContext({ state: owner, native: true, tauriBridge: {},
    flushConversationChoiceSaves: async () => {},
    resolveConversation: (workspace, id) => workspace.exchange.conversations.find(item => item.id === id),
    createChatSubmissionId: () => 'submission-edit-test',
    invokeForAuthorizedDataAreaOperation: (_bridge, command, args, sourceOwner) => {
      if (command === 'preview_desktop_existing_ordinary_chat_egress') return Promise.resolve({
        draftFingerprint: 'synthetic-source-fingerprint', recipientChoiceId: 'synthetic-choice',
        recipientProviderId: 'OPENROUTER', recipientModelId: 'openai/gpt-5.6-terra',
      });
      calls.push({ command, args, sourceOwner });
      return new Promise(resolve => { complete = resolve; completions.push(resolve); });
    },
    refreshDesktopRuntimeWhilePending: () => Promise.resolve(),
    claimSubmittedChatRoute: () => { owner.pendingChatSubmission = null; },
    syncPersistedConversationContinuation: async () => { calls.push({ command: 'sync' }); },
    loadDesktopContextRecords: async () => {}, loadDesktopDiagnosticRecords: async () => {},
    refresh: async () => {}, render() {}, Date,
  });
  vm.runInContext(handler, context);
  if (regenerate) {
    owner.current.exchange.conversations[0].currentLeafId = 'old-answer';
    owner.current.exchange.conversations[0].messages.push({ id: 'old-answer', role: 'assistant',
      parentId: 'edited-user', delivery: regenerate === 'failed-change' ? 'FAILED' : 'COMPLETE', attemptId: 'old-attempt',
      modelSnapshot: { providerId: 'OPENROUTER', modelId: 'GPT_5_6_TERRA' } });
  }
  const run = async () => {
    if (regenerate === 'change' || regenerate === 'failed-change') {
      await context.handle({ preventDefault() {} }, { dataset: { messageId: 'old-answer' } }, 'change-model-completed-answer');
      return context.handle({ preventDefault() {} },
        { dataset: { messageId: 'old-answer', targetPresetId: 'DEEPSEEK_V4_PRO' } }, 'confirm-change-answer-model');
    }
    return context.handle({ preventDefault() {} },
      { dataset: { messageId: regenerate ? 'old-answer' : 'edited-user' } },
      regenerate ? 'regenerate-completed-answer' : 'generate-answer-for-saved-user');
  };
  return { owner, context, calls, run, finish: result => complete(result), finishAt: (index, result) => completions[index](result) };
}

test('generating for an edited user leaf authorizes one new answer and preserves the draft', async () => {
  const h = harness();
  const operation = h.run();
  await new Promise(resolve => setImmediate(resolve));
  assert.equal(h.calls[0].command, 'generate_desktop_ordinary_chat_for_saved_user');
  assert.equal(h.calls[0].args.args.userMessageId, 'edited-user');
  assert.equal(h.calls[0].args.args.expectedRevision, 4);
  assert.equal(h.calls[0].args.args.egressAuthorization.disclosureVersion, 'normal-chat-egress-v2');
  h.finish({ conversationId: 'c1', state: 'COMPLETED' });
  await operation;
  assert.equal(h.owner.composerDraft, '另一份未发送草稿');
  assert.equal(h.owner.pendingChatSubmission, null);
  assert.equal(h.owner.status, '新回答已保存为本地分支。');
});

test('an authorized answer can settle in the original area without changing the new area', async () => {
  const h = harness();
  const operation = h.run();
  await new Promise(resolve => setImmediate(resolve));
  const work = { dataArea: 'WORK', current: { summary: { id: 'w1' }, exchange: { conversations: [] } }, selectedConversationId: 'work-chat' };
  h.context.state = work;
  h.finish({ conversationId: 'c1', state: 'COMPLETED' });
  await operation;
  assert.equal(work.selectedConversationId, 'work-chat');
  assert.equal(work.status, undefined);
  assert.equal(h.calls[1].command, 'sync');
});

test('completed answer regeneration uses a new authorized attempt and preserves the draft', async () => {
  const h = harness(true);
  const operation = h.run();
  await new Promise(resolve => setImmediate(resolve));
  assert.equal(h.calls[0].command, 'regenerate_desktop_ordinary_chat');
  assert.equal(h.calls[0].args.args.assistantMessageId, 'old-answer');
  assert.equal(h.calls[0].args.args.userMessageId, undefined);
  assert.equal(h.calls[0].args.args.egressAuthorization.disclosureVersion, 'normal-chat-egress-v2');
  h.finish({ conversationId: 'c1', state: 'COMPLETED' });
  await operation;
  assert.equal(h.owner.composerDraft, '另一份未发送草稿');
  assert.equal(h.owner.status, '新回答已保存为本地分支。');
});

test('change model submits an explicit target and preserves the unsent draft', async () => {
  const h = harness('change');
  const operation = h.run();
  await new Promise(resolve => setImmediate(resolve));
  assert.equal(h.calls[0].command, 'regenerate_desktop_ordinary_chat');
  assert.equal(h.calls[0].args.args.assistantMessageId, 'old-answer');
  assert.equal(h.calls[0].args.args.targetPresetId, 'DEEPSEEK_V4_PRO');
  h.finish({ conversationId: 'c1', state: 'COMPLETED' });
  await operation;
  assert.equal(h.owner.composerDraft, '另一份未发送草稿');
});

test('failed answer can choose a different model as a new explicit generation', async () => {
  const h = harness('failed-change');
  const operation = h.run();
  await new Promise(resolve => setImmediate(resolve));
  assert.equal(h.calls[0].command, 'regenerate_desktop_ordinary_chat');
  assert.equal(h.calls[0].args.args.targetPresetId, 'DEEPSEEK_V4_PRO');
  h.finish({ conversationId: 'c1', state: 'COMPLETED' });
  await operation;
  assert.equal(h.owner.composerDraft, '另一份未发送草稿');
});

test('model choice discloses sent material and hides choices when the source leaf changes', () => {
  const h = harness(true);
  h.owner.dialog = { kind: 'change-answer-model', workspaceId: 'w1', conversationId: 'c1', messageId: 'old-answer' };
  h.owner.modelServiceSettings = [{ providerId: 'OPENROUTER', providerDisplayName: 'OpenRouter', enabled: true,
    credentialStored: true, presets: [{ id: 'GPT_5_6_TERRA', modelId: 'GPT_5_6_TERRA', displayName: '原模型', chatSelectable: true }] },
  { providerId: 'DEEPSEEK', providerDisplayName: 'DeepSeek', enabled: true, credentialStored: true,
    presets: [{ id: 'DEEPSEEK_V4_PRO', modelId: 'deepseek-v4-pro', displayName: '目标模型', chatSelectable: true }] }];
  const context = vm.createContext({ state: h.owner,
    resolveConversation: (workspace, id) => workspace.exchange.conversations.find(item => item.id === id),
    escape: value => String(value) });
  vm.runInContext(dialogRenderer, context);
  const ready = context.renderChoiceDialog();
  assert.match(ready, /当前问题、分支上下文及引用附件/);
  assert.match(ready, /DEEPSEEK_V4_PRO/);
  assert.doesNotMatch(ready, /data-target-preset-id="GPT_5_6_TERRA"/);
  h.owner.current.exchange.conversations[0].currentLeafId = 'edited-user';
  const stale = context.renderChoiceDialog();
  assert.match(stale, /当前回答已变化/);
  assert.doesNotMatch(stale, /data-target-preset-id=/);
});

for (const mode of [false, true, 'change']) {
  test(`saved generation ${String(mode)} permits another conversation after native admission, while blocking unacknowledged double clicks`, async () => {
    const h = harness(mode);
    const operation = h.run();
    await new Promise(resolve => setImmediate(resolve));
    let second;
    try {
      assert.equal(h.calls.length, 1);
      assert.equal(h.owner.pendingChatSubmission.clientSubmissionId, 'submission-edit-test');
      await h.run();
      assert.equal(h.calls.length, 1);
      // Runtime acknowledgement is tested at the real queue seam elsewhere.
      h.context.claimSubmittedChatRoute();
      const b = structuredClone(h.owner.current.exchange.conversations[0]);
      b.id = 'conversation-B';
      h.owner.current.exchange.conversations.push(b);
      h.owner.selectedConversationId = b.id;
      second = h.run();
      await new Promise(resolve => setImmediate(resolve));
      const requests = h.calls.filter(x => x.command !== 'sync');
      assert.equal(requests.length, 2);
      assert.equal(requests[1].args.args.conversationId, 'conversation-B');
      assert.equal(h.owner.composerDraft, '另一份未发送草稿');
    } finally {
      h.finishAt(0, { conversationId: 'c1', state: 'COMPLETED' });
      await operation;
      if (h.calls.filter(x => x.command !== 'sync').length === 2) {
        h.finishAt(1, { conversationId: 'conversation-B', state: 'COMPLETED' });
      }
      if (second) await second;
    }
  });
  test(`late saved generation ${String(mode)} cannot release a newer conversation preparation`, async () => {
    const h = harness(mode);
    const operation = h.run();
    await new Promise(resolve => setImmediate(resolve));
    h.context.claimSubmittedChatRoute();
    h.owner.selectedConversationId = 'conversation-B';
    h.owner.ordinarySendPreparing = true;
    h.owner.status = 'B preparing';
    h.finish({ conversationId: 'c1', state: 'COMPLETED' });
    await operation;
    assert.equal(h.owner.ordinarySendPreparing, true);
    assert.equal(h.owner.status, 'B preparing');
  });
}

const retryStart = source.indexOf("  if (action === 'retry-ordinary-chat') {");
const retryEnd = source.indexOf("  if (action === 'change-model-completed-answer') {", retryStart);
function retryHarness() {
  const calls = [], completions = [];
  const conversations = ['A', 'B'].map(id => ({ id, revision: 1, currentLeafId: `failed-${id}`,
    messages: [{ id: `failed-${id}`, attemptId: `attempt-${id}`, delivery: 'FAILED' }] }));
  const owner = { dataArea: 'CHAT', current: { summary: { id: 'w1' } }, selectedConversationId: 'A', accountSync: {} };
  let submission = 0;
  const context = vm.createContext({ state: owner, native: true, tauriBridge: {}, Date,
    resolveConversation: (_workspace,id) => conversations.find(c => c.id === id),
    createChatSubmissionId: () => `retry-${++submission}`,
    claimSubmittedChatRoute: (payload, original) => {
      if (original.pendingChatSubmission?.clientSubmissionId === payload.clientSubmissionId) original.pendingChatSubmission = null;
    },
    invokeForAuthorizedDataAreaOperation: (_bridge,command,args) => {
      if (command.startsWith('preview_')) return Promise.resolve({ draftFingerprint: 'test', recipientChoiceId: 'choice', recipientProviderId: 'provider', recipientModelId: 'model' });
      calls.push({ command,args }); return new Promise(resolve => completions.push(resolve));
    },
    syncPersistedConversationContinuation: async () => {}, render() {},
    loadDesktopContextRecords: async () => {}, loadDesktopDiagnosticRecords: async () => {}, refresh: async () => {},
  });
  vm.runInContext(`async function retry(action,target,event) { ${source.slice(retryStart,retryEnd)} }`,context);
  return { owner,context,calls,run: () => context.retry('retry-ordinary-chat',{dataset:{attemptId:`attempt-${owner.selectedConversationId}`}},{preventDefault(){}}),
    finish: (index,id) => completions[index]({conversationId:id,state:'COMPLETED'}) };
}
test('a retry blocks double clicks before admission but allows retry in another conversation while its response streams', async () => {
  const h = retryHarness(); const a = h.run(); await new Promise(resolve => setImmediate(resolve)); let b;
  try {
    await h.run(); assert.equal(h.calls.length,1);
    h.owner.pendingChatSubmission = null; h.owner.selectedConversationId = 'B';
    b = h.run(); await new Promise(resolve => setImmediate(resolve));
    assert.equal(h.calls.length,2); assert.equal(h.calls[1].args.args.attemptId,'attempt-B');
  } finally {
    h.finish(0,'A'); await a;
    if (h.calls.length===2) h.finish(1,'B'); if(b) await b;
  }
});
test('a late retry completion cannot release a newer preparation', async () => {
  const h = retryHarness(); const a = h.run(); await new Promise(resolve => setImmediate(resolve));
  h.owner.pendingChatSubmission = null; h.owner.selectedConversationId = 'B'; h.owner.ordinarySendPreparing = true;
  h.finish(0,'A'); await a; assert.equal(h.owner.ordinarySendPreparing,true);
});
