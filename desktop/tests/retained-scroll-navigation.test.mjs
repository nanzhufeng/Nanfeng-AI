import test from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import vm from 'node:vm';

test('retained transcript honors explicit bottom navigation but preserves passive position', async () => {
  const source = await readFile(new URL('../src/app.mjs', import.meta.url), 'utf8');
  const restore = source.slice(source.indexOf('function restoreChatScroll()'), source.indexOf('function renderUnified()'));
  const dispatch = source.match(/if \(!retainedTranscript[^\n]+restoreChatScroll\(\);/)[0];
  const scroll = { dataset: { conversationId: 'chat' }, scrollTop: 120, scrollHeight: 5000,
    scrollTo({ top }) { this.scrollTop = top; } };
  const state = { selectedConversationId: 'chat', chatScrollPositions: new Map([['chat', 120]]), pendingChatScrollToLatestId: 'chat' };
  const context = vm.createContext({ state, retainedTranscript: scroll, workspace: () => ({}),
    resolveConversation: () => ({ id: 'chat' }), document: { querySelector: () => scroll } });
  vm.runInContext(restore + dispatch, context);
  assert.equal(scroll.scrollTop, 5000);
  assert.equal(state.pendingChatScrollToLatestId, null);
  scroll.scrollTop = 260;
  vm.runInContext(dispatch, context);
  assert.equal(scroll.scrollTop, 260);
});
