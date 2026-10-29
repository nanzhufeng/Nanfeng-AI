import test from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import vm from 'node:vm';

test('retained transcript honors explicit bottom navigation but preserves passive position', async () => {
  const source = await readFile(new URL('../src/app.mjs', import.meta.url), 'utf8');
  const restore = source.slice(source.indexOf('function restoreChatScroll()'), source.indexOf('function renderUnified()'));
  const dispatch = source.match(/if \(\(!retainedTranscript && !runtimePatched\)[^\n]+restoreChatScroll\(\);/)[0];
  const scroll = { dataset: { conversationId: 'chat' }, scrollTop: 120, scrollHeight: 5000,
    scrollTo({ top }) { this.scrollTop = top; } };
  const state = { selectedConversationId: 'chat', chatScrollPositions: new Map([['chat', 120]]), pendingChatScrollToLatestId: 'chat' };
  const context = vm.createContext({ state, retainedTranscript: scroll, runtimePatched: false, workspace: () => ({}),
    resolveConversation: () => ({ id: 'chat' }), document: { querySelector: () => scroll } });
  vm.runInContext(restore + dispatch, context);
  assert.equal(scroll.scrollTop, 5000);
  assert.equal(state.pendingChatScrollToLatestId, null);
  scroll.scrollTop = 260;
  vm.runInContext(dispatch, context);
  assert.equal(scroll.scrollTop, 260);
});

test('a submitted chat claims the bottom anchor only for its own receipt, while later streaming and title events preserve reading position', async () => {
  const source = await readFile(new URL('../src/app.mjs', import.meta.url), 'utf8');
  const claim = source.slice(source.indexOf('function claimSubmittedChatRoute('), source.indexOf('async function flushOrdinaryChatRuntimeRefresh()'));
  const state = {
    selectedConversationId: null,
    pendingCreatedConversationRouteWorkspaceId: 'workspace',
    pendingChatSubmission: { workspaceId: 'workspace', conversationId: null, clientSubmissionId: 'send-own' },
    pendingChatSendScrollToLatestId: null,
  };
  const context = vm.createContext({ state });
  vm.runInContext(`${claim}\nclaimSubmittedChatRoute({ workspaceId: 'workspace', conversationId: 'other', clientSubmissionId: 'send-other' });`, context);
  assert.equal(state.selectedConversationId, null);
  assert.equal(state.pendingChatSendScrollToLatestId, null);
  assert.equal(state.pendingChatSubmission.clientSubmissionId, 'send-own');

  vm.runInContext(`claimSubmittedChatRoute({ workspaceId: 'workspace', conversationId: 'chat', clientSubmissionId: 'send-own' });`, context);
  assert.equal(state.selectedConversationId, 'chat');
  assert.equal(state.pendingChatSendScrollToLatestId, 'chat');
  assert.equal(state.pendingChatSubmission, null);

  state.pendingChatSendScrollToLatestId = null;
  vm.runInContext(`claimSubmittedChatRoute({ workspaceId: 'workspace', conversationId: 'chat', clientSubmissionId: 'send-own' });`, context);
  assert.equal(state.pendingChatSendScrollToLatestId, null);

  const refresh = source.slice(source.indexOf('async function flushOrdinaryChatRuntimeRefresh()'), source.indexOf('const tauriEvents'));
  assert.match(refresh, /claimSubmittedChatRoute\(payload\)/);
  assert.doesNotMatch(refresh, /state\.selectedConversationId === payload\.conversationId\) state\.pendingChatSendScrollToLatestId/);
  const polling = source.slice(source.indexOf('async function refreshDesktopRuntimeWhilePending('), source.indexOf('function recomputeConversationFind('));
  assert.doesNotMatch(polling, /claimSubmittedChatRoute/);
});

test('a late runtime workspace read cannot repaint a conversation after its route changed', async () => {
  const source = await readFile(new URL('../src/app.mjs', import.meta.url), 'utf8');
  const refresh = source.slice(source.indexOf('async function flushOrdinaryChatRuntimeRefresh()'), source.indexOf('const tauriEvents'));
  let resolveRead;
  const read = new Promise(resolve => { resolveRead = resolve; });
  const state = { current: { summary: { id: 'first' } }, error: '' };
  let renders = 0;
  const context = vm.createContext({
    state, desktopEntryRouteGeneration: 0, ordinaryChatRuntimeRefreshInFlight: false,
    ordinaryChatRuntimeRefreshPending: { workspaceId: 'first' },
    invoke: () => read, claimSubmittedChatRoute: () => { throw new Error('stale route claimed'); },
    loadConversationReadState: async () => {}, render: () => { renders++; },
    scheduleOrdinaryChatRuntimeRefresh: () => {},
  });
  const pending = vm.runInContext(`${refresh}\nflushOrdinaryChatRuntimeRefresh()`, context);
  state.current = { summary: { id: 'second' } };
  resolveRead({ summary: { id: 'first' } });
  await pending;
  assert.equal(state.current.summary.id, 'second');
  assert.equal(renders, 0);
});


test('an in-place gallery selection keeps the next shell interaction on the connected transcript', async () => {
  const source = (await readFile(new URL('../src/app.mjs', import.meta.url), 'utf8')).replaceAll('\r\n', '\n');
  const start = source.indexOf("app.addEventListener('click', event => {\n  const target = event.target.closest?.('[data-action=\"select-assistant-gallery-image\"]');");
  assert.ok(start >= 0);
  const handler = source.slice(start, source.indexOf('function setAssistantGallerySelection(', start));
  const retention = source.slice(source.indexOf('function assistantGallerySelectionKey('), source.indexOf('function currentSidebarRetention('));
  const conversation = { id: 'conversation', revision: 1, updatedAt: 'fixed' };
  let click;
  const target = { dataset: { messageId: 'answer', attachmentId: 'second' } };
  const context = vm.createContext({ Map, state: { pane: 'chat', p6eAcceptance: { enabled: false }, assistantImageSelections: new Map() },
    renderedTranscriptRetention: null,
    app: { addEventListener(type, callback) { click = callback; } },
    resolveConversation: () => conversation,
    orderedConversationMessages: () => [{ id: 'answer', delivery: 'COMPLETE' }],
    setAssistantGallerySelection(control, id) { assert.equal(control, target); assert.equal(id, 'second'); },
  });
  vm.runInContext(retention + handler + 'renderedTranscriptRetention=currentTranscriptRetention({});', context);
  click({ target: { closest: () => target }, preventDefault() {}, stopImmediatePropagation() {} });
  assert.equal(context.state.assistantImageSelections.get('answer'), 'second');
  assert.equal(vm.runInContext('canRetainTranscript(renderedTranscriptRetention,currentTranscriptRetention({}))', context), true);
});
