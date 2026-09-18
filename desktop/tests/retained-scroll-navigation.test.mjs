import test from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import vm from 'node:vm';

test('retained transcript honors explicit bottom navigation but preserves passive position', async () => {
  const source = await readFile(new URL('../src/app.mjs', import.meta.url), 'utf8');
  const restore = source.slice(source.indexOf('function restoreChatScroll()'), source.indexOf('function renderUnified()'));
  const dispatch = source.match(/if \(\(!retainedTranscript[^\n]+restoreChatScroll\(\);/)[0];
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
