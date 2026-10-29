import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import vm from 'node:vm';

const source = readFileSync(new URL('../src/app.mjs', import.meta.url), 'utf8');
const locateSource = source.slice(source.indexOf('async function locateSearchHit(hit) {'),
  source.indexOf('\nasync function ', source.indexOf('async function locateSearchHit(hit) {') + 1));

function harness() {
  const pending = [];
  const chat = { dataArea: 'CHAT', current: { summary: { id: 'w' }, exchange: {
    conversations: [{ id: 'c1', currentLeafId: 'm1', messages: [{ id: 'm-c1' }] },
      { id: 'c2', currentLeafId: 'm2', messages: [{ id: 'm-c2' }] }],
  } }, selectedConversationId: null, searchPanel: true, pane: 'search', assistantImageSelections: new Map() };
  const context = vm.createContext({ state: chat, searchLocateGeneration: 0, cloudOpenAreaGeneration: 0,
    rememberFullSearchScroll() {},
    fullSearchResultCache: new Map(), runFullSearch: async () => {},
    loadSearchWorkspace: workspaceId => new Promise(resolve => pending.push({ workspaceId, resolve })),
    invoke: async () => chat.current,
    markConversationOpened: async () => {}, render() {},
    resolveSearchResultAnchor: () => ({ target: null }), document: {},
    queueMicrotask: callback => Promise.resolve().then(callback), window: { setTimeout() {} },
  });
  vm.runInContext(locateSource, context);
  return { chat, context, pending, locate: id => context.locateSearchHit({ entryId: id, workspaceId: 'w', conversationId: id, messageId: `m-${id}` }) };
}

function transcriptionSearchHarness(tasks) {
  const h = harness();
  h.context.native = true;
  h.chat.transcription = { tasks: [{ id: 'previous-task', modelId: 'glm-ocr' }] };
  h.chat.selectedTranscriptionTaskId = 'previous-task';
  h.context.invoke = async (command, _args, owner) => {
    assert.equal(command, 'read_desktop_transcription_state');
    assert.equal(owner, h.chat);
    return { tasks };
  };
  const start = source.indexOf('async function loadTranscriptionState(');
  const end = source.indexOf('async function importDroppedTranscriptionFiles(', start);
  vm.runInContext(source.slice(start, end), h.context);
  return h;
}

test('a deleted transcription search hit stays in search and never opens the remaining task', async () => {
  const h = transcriptionSearchHarness([{ id: 'previous-task', modelId: 'glm-ocr' }]);
  h.context.fullSearchResultCache.set('stale-task', { hits: ['deleted-task'] });
  let refreshed = 0;
  h.context.runFullSearch = async () => { refreshed += 1; };
  const locating = h.context.locateSearchHit({ entryId: 'deleted-result', workspaceId: 'w',
    conversationId: 'transcription-task:deleted-task' });
  h.pending[0].resolve(true);
  await locating;
  assert.equal(h.chat.searchPanel, true);
  assert.equal(h.chat.pane, 'search');
  assert.equal(h.chat.selectedTranscriptionTaskId, 'previous-task');
  assert.match(h.chat.searchError, /转写任务.*删除/);
  assert.equal(refreshed, 1);
  assert.equal(h.context.fullSearchResultCache.size, 0);
});

test('a transcription state read failure keeps the search canvas and earlier selection', async () => {
  const h = transcriptionSearchHarness([]);
  h.context.invoke = async () => { throw new Error('synthetic read refusal'); };
  const locating = h.context.locateSearchHit({ entryId: 'task-result', workspaceId: 'w',
    conversationId: 'transcription-task:new-task' });
  h.pending[0].resolve(true);
  await locating;
  assert.equal(h.chat.searchPanel, true);
  assert.equal(h.chat.pane, 'search');
  assert.equal(h.chat.selectedTranscriptionTaskId, 'previous-task');
  assert.match(h.chat.searchError, /synthetic read refusal/);
});

test('a live transcription search hit opens the exact result after reading fresh tasks', async () => {
  const h = transcriptionSearchHarness([{ id: 'previous-task', modelId: 'glm-ocr' },
    { id: 'target-task', modelId: 'glm-ocr' }]);
  const locating = h.context.locateSearchHit({ entryId: 'task-result', workspaceId: 'w',
    conversationId: 'transcription-task:target-task' });
  h.pending[0].resolve(true);
  await locating;
  assert.equal(h.chat.selectedTranscriptionTaskId, 'target-task');
  assert.equal(h.chat.searchPanel, false);
  assert.equal(h.chat.pane, 'transcription');
  assert.equal(h.chat.transcriptionMode, 'document');
});

test('a delayed CHAT search hit cannot navigate or error the same-ID WORK area', async () => {
  const h = harness();
  const locating = h.locate('c1');
  const work = { dataArea: 'WORK', current: { summary: { id: 'w' }, exchange: {
    conversations: [{ id: 'c1', currentLeafId: 'work-leaf' }],
  } }, selectedConversationId: 'work-conversation', searchPanel: false, pane: 'work' };
  h.context.state = work;
  h.pending[0].resolve(true);
  await locating;
  assert.equal(work.selectedConversationId, 'work-conversation');
  assert.equal(work.pane, 'work');
  assert.equal(work.searchError, undefined);
});

test('an older search hit cannot override a newer hit in the same area', async () => {
  const h = harness();
  const first = h.locate('c1');
  const second = h.locate('c2');
  h.pending[1].resolve(true);
  await second;
  h.pending[0].resolve(true);
  await first;
  assert.equal(h.chat.selectedConversationId, 'c2');
  assert.equal(h.chat.searchPanel, false);
  assert.equal(h.chat.searchError, undefined);
});

test('a stale search revision can still switch to an existing branch leaf', async () => {
  const h = harness();
  const conversation = h.chat.current.exchange.conversations[0];
  conversation.revision = 7;
  conversation.currentLeafId = 'new-leaf';
  conversation.messages = [{ id: 'old-leaf', role: 'assistant' }, { id: 'new-leaf', role: 'assistant' }];
  const fresh = { summary: { id: 'w' }, exchange: { conversations: [
    { ...conversation, revision: 9 }, h.chat.current.exchange.conversations[1],
  ] } };
  const writes = [];
  h.context.intent = () => 'search-switch-test';
  h.context.invoke = async (command, payload) => {
    if (command === 'mutate_desktop_domain') {
      writes.push(payload.args);
      if (payload.args.expectedRevision !== fresh.exchange.conversations[0].revision) throw new Error('REVISION_CONFLICT');
      fresh.exchange.conversations[0].currentLeafId = payload.args.fields.messageId;
      fresh.exchange.conversations[0].revision += 1;
    }
    if (command === 'read_desktop_workspace') return fresh;
    return null;
  };
  const locating = h.context.locateSearchHit({ entryId: 'old-hit', workspaceId: 'w', conversationId: 'c1',
    conversationRevision: 4, branchLeafId: 'old-leaf', messageId: 'old-leaf' });
  h.pending[0].resolve(true);
  await locating;
  assert.equal(writes.length, 1);
  assert.equal(writes[0].expectedRevision, 9);
  assert.equal(h.chat.selectedConversationId, 'c1');
  assert.equal(h.chat.searchPanel, false);
});

test('a late branch refresh cannot replace the new area after switching CHAT to WORK', async () => {
  const h = harness();
  h.chat.current.exchange.conversations[0].revision = 3;
  let finishRead;
  h.context.invoke = command => {
    assert.equal(command, 'read_desktop_workspace');
    return new Promise(resolve => { finishRead = resolve; });
  };
  const locating = h.context.locateSearchHit({ entryId: 'branch-hit', workspaceId: 'w', conversationId: 'c1',
    conversationRevision: 2, branchLeafId: 'old-leaf', messageId: 'old-leaf' });
  h.pending[0].resolve(true);
  await new Promise(resolve => setImmediate(resolve));
  const work = { dataArea: 'WORK', current: { summary: { id: 'w' }, exchange: { conversations: [] } },
    selectedConversationId: 'work-c', searchPanel: false, pane: 'work' };
  h.context.state = work;
  finishRead({ summary: { id: 'w' }, exchange: { conversations: [{ id: 'c1', revision: 4, currentLeafId: 'old-leaf' }] } });
  await locating;
  assert.equal(work.selectedConversationId, 'work-c');
  assert.equal(work.current.exchange.conversations.length, 0);
  assert.equal(work.searchError, undefined);
});

test('a same-workspace stale hit cannot open a conversation already deleted on disk', async () => {
  const h = harness();
  h.context.fullSearchResultCache.set('stale', { hits: ['deleted'] });
  let refreshed = 0;
  h.context.runFullSearch = async options => { refreshed += 1; assert.equal(options.restoreScroll, true); };
  h.context.invoke = async command => {
    assert.equal(command, 'read_desktop_workspace');
    return { summary: { id: 'w' }, exchange: { conversations: [
      { id: 'c1', currentLeafId: 'm1', deleted: true },
      { id: 'c2', currentLeafId: 'm2' },
    ] } };
  };
  const locating = h.locate('c1');
  h.pending[0].resolve(true);
  await locating;
  assert.equal(h.chat.selectedConversationId, null);
  assert.equal(h.chat.searchPanel, true);
  assert.match(h.chat.searchError, /无法定位/);
  assert.equal(refreshed, 1);
  assert.equal(h.context.fullSearchResultCache.size, 0);
});

test('a stale hit cannot open a conversation after its matching message disappeared', async () => {
  const h = harness();
  h.context.invoke = async () => ({ summary: { id: 'w' }, exchange: { conversations: [
    { id: 'c1', currentLeafId: 'm1', messages: [] },
  ] } });
  const locating = h.locate('c1');
  h.pending[0].resolve(true);
  await locating;
  assert.equal(h.chat.selectedConversationId, null);
  assert.equal(h.chat.searchPanel, true);
  assert.match(h.chat.searchError, /无法定位/);
});

test('a stale attachment hit stays in search after its message loses that reference', async () => {
  const h = harness();
  h.context.invoke = async () => ({ summary: { id: 'w' }, exchange: { conversations: [
    { id: 'c1', currentLeafId: 'm1', messages: [{ id: 'm-c1', blocks: [
      { kind: 'ASSET_REF', asset: { id: 'another-attachment' } },
    ] }] },
  ] } });
  const locating = h.context.locateSearchHit({ entryId: 'stale-attachment', workspaceId: 'w',
    conversationId: 'c1', messageId: 'm-c1', attachmentId: 'removed-attachment' });
  h.pending[0].resolve(true);
  await locating;
  assert.equal(h.chat.selectedConversationId, null);
  assert.equal(h.chat.searchPanel, true);
  assert.match(h.chat.searchError, /无法定位/);
});
