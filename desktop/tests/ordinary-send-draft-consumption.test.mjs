import assert from 'node:assert/strict';
import test from 'node:test';
import vm from 'node:vm';
import { readFile } from 'node:fs/promises';
import { createDraftWriteQueue } from '../src/draft-write-queue.mjs';
import { invokeForDataArea, invokeForAuthorizedDataAreaOperation } from '../src/data-area-invoke.mjs';
import { syncSelectedContinuation } from '../src/selected-conversation-continuation.mjs';

// Run the actual UI send and draft scheduling owners. The transport models only
// the native atomic commit/reject boundary; it never invokes a real Provider.
const source = await readFile(new URL('../src/app.mjs', import.meta.url), 'utf8');
const usageOwner=source.slice(source.indexOf('async function loadDesktopUsageLedger('),source.indexOf('async function loadDesktopContextRecords('));
const modelOwner=source.slice(source.indexOf('function newConversationModelId('),source.indexOf('\nasync function updateP6GConversationOverride('));
const previewInputOwner = source.slice(source.indexOf('function ordinaryEgressPreviewInput('),
  source.indexOf('function currentOrdinaryEgressPreview('));
const draftOwner = source.slice(source.indexOf('const ordinaryComposerDraftQueue ='), source.indexOf('async function loadOrdinaryComposerDraft('));
const sendOwner = source.slice(source.indexOf('async function sendLocalMessage()'), source.indexOf('saveLocalMessage = sendLocalMessage;'));
const syncOwner = source.slice(source.indexOf('async function syncPersistedConversationContinuation('),
  source.indexOf('\nfunction queueOrdinaryChatRuntimeRefresh('));
const submissionOwner = source.slice(source.indexOf('function claimSubmittedChatRoute('),
  source.indexOf('\nasync function flushOrdinaryChatRuntimeRefresh('));
const retryOwner = source.slice(source.indexOf("  if (action === 'retry-ordinary-chat') {"),
  source.indexOf("  if (action === 'copy-message') {", source.indexOf("  if (action === 'retry-ordinary-chat') {")));

test('attachment-only draft has a recipient preview, while an empty draft does not', () => {
  const owner = { dataArea: 'CHAT', current: { summary: { id: 'workspace-test' } },
    selectedConversationId: null, composerDraft: '  ', composerAttachments: [{ id: 'attachment-one', mimeType: 'text/plain' }],
    conversationPreferences: {} };
  const context = vm.createContext({ state: owner, native: true, resolveConversation: () => null });
  vm.runInContext(modelOwner+'\n'+previewInputOwner, context);
  const preview = vm.runInContext('ordinaryEgressPreviewInput()', context);
  assert.ok(preview, 'native accepts attachment-only submissions and must disclose their recipient');
  assert.equal(preview.input.text, '');
  assert.deepEqual([...preview.input.attachmentIds], ['attachment-one']);
  owner.composerAttachments = [];
  assert.equal(vm.runInContext('ordinaryEgressPreviewInput()', context), null);
});

test('new-conversation recipient preview changes with tone and web-search choices', () => {
  const owner = { dataArea: 'CHAT', current: { summary: { id: 'workspace-test' } },
    selectedConversationId: null, composerDraft: '合成问题', composerAttachments: [],
    pendingNewConversationModelId: null,
    conversationPreferences: { toneOverride: null, webSearchOverride: false } };
  const context = vm.createContext({ state: owner, native: true, resolveConversation: () => null });
  vm.runInContext(modelOwner+'\n'+previewInputOwner, context);
  const before = vm.runInContext('ordinaryEgressPreviewInput()', context);
  assert.equal(before.input.webSearchOverride, false);
  owner.conversationPreferences.toneOverride = 'professional';
  const changedTone = vm.runInContext('ordinaryEgressPreviewInput()', context);
  assert.equal(changedTone.input.toneOverride, 'professional');
  assert.notEqual(changedTone.signature, before.signature);
  owner.conversationPreferences.webSearchOverride = true;
  const changedSearch = vm.runInContext('ordinaryEgressPreviewInput()', context);
  assert.equal(changedSearch.input.webSearchOverride, true);
  assert.notEqual(changedSearch.signature, changedTone.signature);
});

function harness({ bound = false, rejected = false, expiredOnRead = false, flushChoices = async () => {}, deferredSubmission = null, selected = false, currentAccountEmail = 'account-a@example.test', contextReadFailure = false, cloudFailure = false } = {}) {
  const route = bound ? 'conversation-existing' : null;
  const original = { text: '已发送的中文草稿\n', attachmentIds: ['attachment-one'] };
  const drafts = new Map([[route, structuredClone(original)]]);
  const timers = new Map();
  const messages = [];
  const calls = [];
  const remote = { text: 'old remote text' };
  let timerId = 0;
  const state = {
    dataArea: 'CHAT', accountSync: { email: selected ? 'account-a@example.test' : null,
      syncedConversationKeys: selected ? ['workspace-test:conversation-existing'] : [] },
    current: { summary: { id: 'workspace-test' } }, selectedConversationId: route,
    composerDraft: original.text, composerAttachments: [{ id: 'attachment-one' }],
    conversationPreferences: {}, pane: 'chat',
  };
  let context;
  const bridge = { invoke: async (command, payload, options) => {
    calls.push({ command, area: options?.headers?.['x-nanfeng-data-area'] });
    const args = payload?.args;
    if (command === 'read_desktop_account_sync') return { email: currentAccountEmail };
    if (command === 'sync_selected_desktop_conversation') {
      if (cloudFailure) throw new Error('cloud unavailable');
      remote.text = messages.at(-1)?.text || remote.text;
      return { status: 'SYNCED' };
    }
    if (command === 'save_desktop_conversation_draft') {
      if (!args.text && !args.attachmentIds.length) drafts.delete(args.conversationId);
      else drafts.set(args.conversationId, { text: args.text, attachmentIds: [...args.attachmentIds] });
      return;
    }
    if (command === 'read_desktop_conversation_draft') {
      assert.equal(payload.workspaceId, 'workspace-test');
      assert.equal(payload.conversationId, null);
      const draft = drafts.get(null);
      if (expiredOnRead && draft) draft.text = '';
      return draft && { text: draft.text, attachments: draft.attachmentIds.map(id => ({ id })) };
    }
    if (command === 'read_desktop_usage_ledger') return { records: [{ entryId: 'late-owned-cost' }] };
    assert.equal(command, 'submit_desktop_ordinary_chat');
    if (deferredSubmission) return deferredSubmission;
    if (rejected) throw new Error('rejected before transaction commit');
    messages.push({ text: args.text, attachmentIds: [...args.attachmentIds] });
    drafts.delete(args.conversationId);
    return { conversationId: route || 'conversation-created', state: 'FAILED' };
  } };
  context = vm.createContext({
    state, native: true, createDraftWriteQueue, structuredClone, flushConversationChoiceSaves: flushChoices,
    tauriBridge: bridge, invokeForAuthorizedDataAreaOperation, syncSelectedContinuation,
    scheduleUnknownSyncReconciliation: () => {},
    setTimeout: callback => { timers.set(++timerId, callback); return timerId; },
    clearTimeout: id => timers.delete(id),
    render() {}, resolveConversation: () => bound ? { id: route, revision: 1 } : null,
    createChatSubmissionId: () => 'client-test',
    refreshDesktopRuntimeWhilePending: async () => {},
    currentOrdinaryEgressPreview: () => ({ draftFingerprint: 'synthetic-draft-fingerprint',
      recipientChoiceId: 'synthetic-choice', recipientProviderId: 'OPENROUTER',
      recipientModelId: 'openai/gpt-5.6-terra' }),
    resetComposerPresentation: () => {},
    loadDesktopContextRecords: async () => { if (contextReadFailure) throw new Error('post-commit context read failed'); },
    loadDesktopDiagnosticRecords: async () => {},
    refresh: async () => {},
    invoke: (command, payload, owner = context.state) => invokeForDataArea(bridge, command, payload, owner, () => context.state),
  });
  vm.runInContext(`${usageOwner}\n${modelOwner}\n${draftOwner}\nfunction writeComposerAttachments() { scheduleOrdinaryComposerDraft(); }\n${submissionOwner}\n${syncOwner}\n${sendOwner}`, context);
  return { state, context, drafts, messages, calls, remote, original, route,
    send: () => vm.runInContext('sendLocalMessage()', context),
    drain: async () => {
      for (const callback of [...timers.values()]) callback();
      timers.clear();
      await vm.runInContext('flushOrdinaryComposerDraft()', context);
    },
  };
}

test('switching data areas while choices save cannot submit or change the new area', async () => {
  let release;
  const gate = new Promise(resolve => { release = resolve; });
  const h = harness({ bound: true, flushChoices: () => gate });
  const sending = h.send();
  await new Promise(resolve => setImmediate(resolve));
  const work = { dataArea: 'WORK', current: { summary: { id: 'workspace-test' } }, selectedConversationId: h.route,
    composerDraft: 'WORK draft', composerAttachments: [{ id: 'work-attachment' }], ordinarySendPreparing: false };
  h.context.state = work;
  release();
  await sending;
  assert.equal(h.messages.length, 0);
  assert.equal(work.composerDraft, 'WORK draft');
  assert.equal(work.error, undefined);
  assert.equal(work.ordinarySendPreparing, false);
  assert.equal(h.state.ordinarySendPreparing, false);
});

test('a stale result after Send does not restore CHAT material into WORK', async () => {
  let rejectSubmission;
  const deferredSubmission = new Promise((_, reject) => { rejectSubmission = reject; });
  const h = harness({ bound: true, deferredSubmission });
  const sending = h.send();
  await new Promise(resolve => setImmediate(resolve));
  const work = { dataArea: 'WORK', current: { summary: { id: 'workspace-test' } }, selectedConversationId: h.route,
    composerDraft: 'WORK draft', composerAttachments: [{ id: 'work-attachment' }] };
  h.context.state = work;
  rejectSubmission(Object.assign(new Error('old area receipt'), { code: 'STALE_DATA_AREA' }));
  await sending;
  assert.equal(work.composerDraft, 'WORK draft');
  assert.deepEqual(work.composerAttachments, [{ id: 'work-attachment' }]);
  assert.equal(work.error, undefined);
  assert.equal(work.pendingChatSubmission, undefined);
});

test('a pre-commit rejection after navigation restores the original CHAT draft only', async () => {
  let rejectSubmission;
  const deferredSubmission = new Promise((_, reject) => { rejectSubmission = reject; });
  const h = harness({ bound: true, deferredSubmission });
  const sending = h.send();
  await new Promise(resolve => setImmediate(resolve));
  const work = { dataArea: 'WORK', current: { summary: { id: 'workspace-test' } }, selectedConversationId: h.route,
    composerDraft: 'WORK draft', composerAttachments: [{ id: 'work-attachment' }] };
  h.context.state = work;
  rejectSubmission(new Error('rejected before transaction commit'));
  await sending;
  assert.equal(h.state.composerDraft, h.original.text);
  assert.equal(h.state.composerAttachments[0].id, 'attachment-one');
  assert.equal(h.state.pendingChatSubmission, null);
  assert.match(h.state.error, /消息未提交/);
  assert.equal(work.composerDraft, 'WORK draft');
  assert.equal(work.error, undefined);
});

test('a late rejection in CHAT cannot replace another conversation draft or error', async () => {
  let rejectSubmission;
  const deferredSubmission = new Promise((_, reject) => { rejectSubmission = reject; });
  const h = harness({ bound: true, deferredSubmission });
  const sending = h.send();
  await new Promise(resolve => setImmediate(resolve));
  h.state.selectedConversationId = 'another-conversation';
  h.state.composerDraft = 'another draft';
  h.state.error = 'another route notice';
  rejectSubmission(new Error('rejected before transaction commit'));
  await sending;
  assert.equal(h.state.composerDraft, 'another draft');
  assert.equal(h.state.error, 'another route notice');
  assert.equal(h.state.pendingChatSubmission, null);
});

test('a completed old-area Send cannot claim a matching WORK conversation', async () => {
  let resolveSubmission;
  const deferredSubmission = new Promise(resolve => { resolveSubmission = resolve; });
  const h = harness({ bound: true, deferredSubmission });
  const sending = h.send();
  await new Promise(resolve => setImmediate(resolve));
  const work = { dataArea: 'WORK', current: { summary: { id: 'workspace-test' } }, selectedConversationId: h.route,
    composerDraft: 'WORK draft', composerAttachments: [{ id: 'work-attachment' }] };
  h.context.state = work;
  resolveSubmission({ conversationId: h.route, state: 'COMPLETED' });
  await sending;
  assert.equal(work.selectedConversationId, h.route);
  assert.equal(work.composerDraft, 'WORK draft');
  assert.equal(work.status, undefined);
  assert.equal(work.pendingChatSubmission, undefined);
  assert.equal(h.state.pendingChatSubmission, null);
  assert.match(h.state.status, /回复已完成/);
});

test('a completed Send cannot replace status or model selection on another CHAT conversation', async () => {
  let resolveSubmission;
  const deferredSubmission = new Promise(resolve => { resolveSubmission = resolve; });
  const h = harness({ bound: true, deferredSubmission });
  const sending = h.send();
  await new Promise(resolve => setImmediate(resolve));
  h.state.selectedConversationId = 'another-conversation';
  h.state.status = 'another route status';
  h.state.pendingNewConversationModelId = 'another-model';
  resolveSubmission({ conversationId: h.route, state: 'COMPLETED' });
  await sending;
  assert.equal(h.state.selectedConversationId, 'another-conversation');
  assert.equal(h.state.status, 'another route status');
  assert.equal(h.state.pendingNewConversationModelId, 'another-model');
  assert.equal(h.state.pendingChatSubmission, null);
});

test('a cloud continuation error for an old CHAT route cannot repaint the new route', async () => {
  let resolveSubmission;
  const deferredSubmission = new Promise(resolve => { resolveSubmission = resolve; });
  const h = harness({ bound: true, selected: true, cloudFailure: true, deferredSubmission });
  const sending = h.send();
  await new Promise(resolve => setImmediate(resolve));
  h.state.selectedConversationId = 'another-conversation';
  h.state.error = 'another route notice';
  h.messages.push({ text: h.original.text.trim(), attachmentIds: h.original.attachmentIds });
  resolveSubmission({ conversationId: h.route, state: 'COMPLETED' });
  await sending;
  assert.equal(h.calls.some(call => call.command === 'sync_selected_desktop_conversation'), true);
  assert.equal(h.state.error, 'another route notice');
  assert.equal(h.state.pendingChatSubmission, null);
});

test('new-conversation Send completes the original CHAT route while WORK is visible', async () => {
  let resolveSubmission;
  const deferredSubmission = new Promise(resolve => { resolveSubmission = resolve; });
  const h = harness({ deferredSubmission });
  const sending = h.send();
  await new Promise(resolve => setImmediate(resolve));
  const work = { dataArea: 'WORK', current: { summary: { id: 'workspace-test' } }, selectedConversationId: null,
    composerDraft: 'WORK draft', composerAttachments: [] };
  h.context.state = work;
  resolveSubmission({ conversationId: 'conversation-created', state: 'COMPLETED' });
  await sending;
  assert.equal(h.state.selectedConversationId, 'conversation-created');
  assert.equal(h.state.pendingChatSubmission, null);
  assert.equal(h.state.pendingCreatedConversationRouteWorkspaceId, null);
  assert.equal(work.selectedConversationId, null);
  assert.equal(work.composerDraft, 'WORK draft');
});

test('an authorized CHAT Send finishes selected sync after switching to the same-ID WORK route', async () => {
  let resolveSubmission;
  const deferredSubmission = new Promise(resolve => { resolveSubmission = resolve; });
  const h = harness({ bound: true, selected: true, deferredSubmission });
  const sending = h.send();
  await new Promise(resolve => setImmediate(resolve));
  const work = { dataArea: 'WORK', current: { summary: { id: 'workspace-test' } },
    selectedConversationId: h.route, composerDraft: 'WORK draft',
    composerAttachments: [{ id: 'work-attachment' }], accountSync: { syncedConversationKeys: [] } };
  h.context.state = work;
  h.messages.push({ text: h.original.text.trim(), attachmentIds: h.original.attachmentIds });
  resolveSubmission({ conversationId: h.route, state: 'COMPLETED' });
  await sending;
  assert.equal(h.remote.text, h.original.text.trim());
  assert.equal(h.calls.find(call => call.command === 'sync_selected_desktop_conversation')?.area, 'CHAT');
  assert.equal(work.composerDraft, 'WORK draft');
  assert.equal(work.status, undefined);
  assert.equal(work.error, undefined);
});

test('an account switch during an authorized Send cannot continue the prior account selection', async () => {
  let resolveSubmission;
  const deferredSubmission = new Promise(resolve => { resolveSubmission = resolve; });
  const h = harness({ bound: true, selected: true, currentAccountEmail: 'account-b@example.test', deferredSubmission });
  const sending = h.send();
  await new Promise(resolve => setImmediate(resolve));
  h.messages.push({ text: h.original.text.trim(), attachmentIds: h.original.attachmentIds });
  resolveSubmission({ conversationId: h.route, state: 'COMPLETED' });
  await sending;
  assert.equal(h.calls.find(call => call.command === 'read_desktop_account_sync')?.area, 'CHAT');
  assert.equal(h.calls.some(call => call.command === 'sync_selected_desktop_conversation'), false);
  assert.equal(h.remote.text, 'old remote text');
  assert.equal(h.state.composerDraft, '');
});

test('an authorized failed-answer retry finishes its original-area continuation after navigation', async () => {
  let finishRetry;
  const calls = [];
  const failed = { id: 'failed-answer', attemptId: 'attempt-old', delivery: 'FAILED' };
  const conversation = { id: 'conversation-existing', revision: 1, currentLeafId: failed.id, messages: [failed] };
  const chat = { dataArea: 'CHAT', current: { summary: { id: 'workspace-test' } },
    selectedConversationId: conversation.id, accountSync: { syncedConversationKeys: [] } };
  const work = { dataArea: 'WORK', current: { summary: { id: 'workspace-test' } }, status: 'WORK status' };
  const context = vm.createContext({ state: chat, native: true, invokeForAuthorizedDataAreaOperation,
    resolveConversation: () => conversation, createChatSubmissionId: () => 'retry-client-test',
    tauriBridge: { invoke(command, args, options) {
      if (command === 'preview_desktop_ordinary_chat_retry_egress') return Promise.resolve({
        draftFingerprint: 'synthetic-retry', recipientChoiceId: 'synthetic-choice',
        recipientProviderId: 'OPENROUTER', recipientModelId: 'openai/gpt-5.6-terra',
      });
      calls.push({ command, area: options.headers['x-nanfeng-data-area'] });
      return new Promise(resolve => { finishRetry = resolve; });
    } },
    syncPersistedConversationContinuation: async (workspaceId, conversationId, owner) => {
      calls.push({ command: 'selected-continuation', area: owner.dataArea, workspaceId, conversationId });
    },
    render() {}, loadDesktopContextRecords: async () => {}, loadDesktopDiagnosticRecords: async () => {}, refresh: async () => {},
  });
  vm.runInContext(`async function retryAction(action, target, event) { ${retryOwner} }`, context);
  const retrying = context.retryAction('retry-ordinary-chat', { dataset: { attemptId: 'attempt-old' } }, { preventDefault() {} });
  await new Promise(resolve => setImmediate(resolve));
  context.state = work;
  finishRetry({ conversationId: 'conversation-existing', state: 'COMPLETED' });
  await retrying;
  assert.deepEqual(calls, [
    { command: 'retry_desktop_ordinary_chat', area: 'CHAT' },
    { command: 'selected-continuation', area: 'CHAT', workspaceId: 'workspace-test', conversationId: 'conversation-existing' },
  ]);
  assert.equal(work.status, 'WORK status');
  assert.equal(work.error, undefined);
});

test('a failed-answer retry completion cannot replace another CHAT conversation status', async () => {
  let finishRetry;
  const failed = { id: 'failed-answer', attemptId: 'attempt-old', delivery: 'FAILED' };
  const conversation = { id: 'conversation-existing', revision: 1, currentLeafId: failed.id, messages: [failed] };
  const chat = { dataArea: 'CHAT', current: { summary: { id: 'workspace-test' } },
    selectedConversationId: 'conversation-existing', status: '', accountSync: { syncedConversationKeys: [] } };
  const context = vm.createContext({ state: chat, native: true, invokeForAuthorizedDataAreaOperation,
    resolveConversation: () => conversation, createChatSubmissionId: () => 'retry-client-test',
    tauriBridge: { invoke: command => command === 'preview_desktop_ordinary_chat_retry_egress'
      ? Promise.resolve({ draftFingerprint: 'synthetic-retry', recipientChoiceId: 'synthetic-choice',
        recipientProviderId: 'OPENROUTER', recipientModelId: 'openai/gpt-5.6-terra' })
      : new Promise(resolve => { finishRetry = resolve; }) },
    syncPersistedConversationContinuation: async () => {},
    render() {}, loadDesktopContextRecords: async () => {}, loadDesktopDiagnosticRecords: async () => {}, refresh: async () => {},
  });
  vm.runInContext(`async function retryAction(action, target, event) { ${retryOwner} }`, context);
  const retrying = context.retryAction('retry-ordinary-chat', { dataset: { attemptId: 'attempt-old' } }, { preventDefault() {} });
  await new Promise(resolve => setImmediate(resolve));
  chat.selectedConversationId = 'another-conversation';
  chat.status = 'another route status';
  finishRetry({ conversationId: 'conversation-existing', state: 'COMPLETED' });
  await retrying;
  assert.equal(chat.status, 'another route status');
});

test('send after a suspended expiry timer clears stale new-conversation text before submitting', async () => {
  const h = harness({ expiredOnRead: true });
  await h.send();
  assert.equal(h.messages.length, 0);
  assert.equal(h.state.composerDraft, '');
  assert.equal(h.state.composerAttachments[0].id, 'attachment-one');
  assert.match(h.state.status, /到期/);
});

for (const bound of [false, true]) {
  test(`committed ${bound ? 'existing' : 'new'} conversation send cannot resurrect consumed text or attachments`, async () => {
    const h = harness({ bound });
    await h.send();
    await h.drain();
    assert.equal(h.messages.length, 1);
    assert.equal(h.messages[0].text, h.original.text.trim());
    assert.deepEqual(h.messages[0].attachmentIds, h.original.attachmentIds);
    assert.equal(h.drafts.has(h.route), false);
    assert.equal(h.state.composerDraft, '');
    assert.equal(h.state.composerAttachments.length, 0);
  });
}

test('a rejected submission retains both the original text and attachment draft', async () => {
  const h = harness({ rejected: true });
  await h.send();
  await h.drain();
  assert.equal(h.messages.length, 0);
  assert.deepEqual(h.drafts.get(h.route), h.original);
  assert.equal(h.state.composerDraft, h.original.text);
  assert.match(h.state.error, /消息未提交/);
});

test('a post-commit UI read failure never resurrects a consumed send draft', async () => {
  const h = harness({ bound: true, contextReadFailure: true });
  await h.send();
  assert.equal(h.messages.length, 1);
  assert.equal(h.state.composerDraft, '');
  assert.equal(h.state.composerAttachments.length, 0);
  assert.equal(h.state.pendingChatSubmission, null);
  assert.match(h.state.error, /回复已写入本机.*界面回读未完成/);
});

test('send waits for the selected model and preferences to finish saving', async () => {
  let release;
  const gate = new Promise(resolve => { release = resolve; });
  const h = harness({ bound: true, flushChoices: () => gate });
  const sending = h.send();
  await new Promise(resolve => setImmediate(resolve));
  try { assert.equal(h.messages.length, 0); }
  finally { release(); await sending; }
  assert.equal(h.messages.length, 1);
});

test('a failed choice save stops submission and preserves the draft', async () => {
  const h = harness({ flushChoices: async () => { throw new Error('choice save failed'); } });
  await h.send();
  assert.equal(h.messages.length, 0);
  assert.equal(h.state.composerDraft, h.original.text);
  assert.match(h.state.error, /choice save failed/);
});

test('navigation during choice saving cannot send the old input into the new route', async () => {
  let release;
  const gate = new Promise(resolve => { release = resolve; });
  const h = harness({ bound: true, flushChoices: () => gate });
  const sending = h.send();
  await new Promise(resolve => setImmediate(resolve));
  h.state.selectedConversationId = 'another-conversation';
  h.state.composerDraft = 'destination draft';
  release();
  await sending;
  assert.equal(h.messages.length, 0);
  assert.equal(h.state.composerDraft, 'destination draft');
});

test('double Send while choices are saving authorizes only one submission', async () => {
  let release;
  const gate = new Promise(resolve => { release = resolve; });
  const h = harness({ bound: true, flushChoices: () => gate });
  const first = h.send(), second = h.send();
  release();
  await Promise.all([first, second]);
  assert.equal(h.messages.length, 1);
});

test('editing while choices are saving keeps the new material without submitting it', async () => {
  let release;
  const gate = new Promise(resolve => { release = resolve; });
  const h = harness({ flushChoices: () => gate });
  const sending = h.send();
  h.state.composerDraft = 'new text after Send';
  release();
  await sending;
  assert.equal(h.messages.length, 0);
  assert.equal(h.state.composerDraft, 'new text after Send');
});


test('settled ordinary Send reads owned durable costs without requiring a runtime event',async()=>{
 const h=harness({bound:true});await h.send();
 assert.equal(h.calls.filter(c=>c.command==='read_desktop_usage_ledger').length,1);
 assert.equal(h.state.usageLedger.records[0].entryId,'late-owned-cost');
 assert.equal(h.state.error,'');
});

test('settled retry reads owned durable costs without requiring a runtime event',async()=>{
 const failed={id:'failed-answer',attemptId:'attempt-old',delivery:'FAILED'},conversation={id:'conversation-existing',revision:1,currentLeafId:'failed-answer',messages:[failed]};
 const owner={dataArea:'CHAT',current:{summary:{id:'workspace-test'}},selectedConversationId:conversation.id,accountSync:{syncedConversationKeys:[]}};const calls=[];
 const context=vm.createContext({native:true,state:owner,invokeForAuthorizedDataAreaOperation,resolveConversation:()=>conversation,createChatSubmissionId:()=> 'retry-cost-client',
  tauriBridge:{invoke:async command=>command==='preview_desktop_ordinary_chat_retry_egress'?{draftFingerprint:'synthetic-retry',recipientChoiceId:'synthetic-choice',recipientProviderId:'OPENROUTER',recipientModelId:'openai/gpt-5.6-terra'}:{conversationId:conversation.id,state:'COMPLETED'}},
  invoke:async command=>{calls.push(command);return{records:[{entryId:'retry-owned-cost'}]}},syncPersistedConversationContinuation:async()=>{},refreshDesktopRuntimeWhilePending:async()=>{},claimSubmittedChatRoute:()=>{},
  render(){},loadDesktopContextRecords:async()=>{},loadDesktopDiagnosticRecords:async()=>{},refresh:async()=>{},
 });
 vm.runInContext(`${usageOwner}\nasync function retryAction(action,target,event){${retryOwner}}`,context);
 await context.retryAction('retry-ordinary-chat',{dataset:{attemptId:'attempt-old'}},{preventDefault(){}});
 assert.deepEqual(calls,['read_desktop_usage_ledger']);assert.equal(owner.usageLedger.records[0].entryId,'retry-owned-cost');assert.equal(owner.error,'');
});
