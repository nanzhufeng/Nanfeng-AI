import assert from 'node:assert/strict';
import test from 'node:test';
import vm from 'node:vm';
import { readFile } from 'node:fs/promises';
import * as recovery from '../src/composer-draft-recovery.mjs';

const source = await readFile(new URL('../src/app.mjs', import.meta.url), 'utf8');
const begin = source.indexOf('  state.p6gCatalog = p6gCatalog;', source.indexOf('async function refresh('));
const end = source.indexOf('  state.history = history;', begin);
assert.ok(begin > 0 && end > begin);
const projection = source.slice(begin, end);
function refresh(current, incoming, selected = 'chat-a') {
  const state = { current: { summary: { id: 'workspace-a' } }, selectedConversationId: selected,
    p6gSelection: current, conversationPreferences: current?.conversationOverride || { revision: 0, toneOverride: 'new-draft' } };
  vm.runInNewContext(projection, { ...recovery, state, entryOwner: state, p6gCatalog: {},
    openrouterRegistryStatus: {}, p6gGlobalDefault: {}, p6gSelection: incoming,
    selectionWorkspaceId: 'workspace-a', selectionConversationId: selected });
  return state;
}
const choice = (revision, modelId, conversationId = 'chat-a', toneOverride = null) => ({
  conversationOverride: { conversationId, revision, modelId, toneOverride, webSearchOverride: false },
  globalDefault: { revision: 0, tier: null, modelId: null, composerRevision: 0 },
});
test('late refresh cannot roll back a successfully saved model and preferences', () => {
  const current = choice(2, 'RELAYCORE_GPT_6_ASTRA', 'chat-a', 'professional');
  const state = refresh(current, choice(1, 'RELAYCORE_GPT_6_1_SOL'));
  assert.deepEqual(state.p6gSelection.conversationOverride, current.conversationOverride);
  assert.deepEqual(state.conversationPreferences, current.conversationOverride);
});
test('newer explicit Auto remains authoritative instead of resurrecting manual model', () => {
  const current = choice(3, null);
  const state = refresh(current, choice(2, 'RELAYCORE_GPT_6_ASTRA'));
  assert.deepEqual(state.conversationPreferences, current.conversationOverride);
});
test('newer incoming preferences and explicit Auto are accepted', () => {
  const incoming = choice(4, null, 'chat-a', 'friendly');
  assert.deepEqual(refresh(choice(3, 'RELAYCORE_GPT_6_ASTRA'), incoming).conversationPreferences, incoming.conversationOverride);
});
test('revisions from a previous conversation cannot block the selected conversation', () => {
  const incoming = choice(1, 'RELAYCORE_GPT_6_1_SOL');
  assert.deepEqual(refresh(choice(99, 'RELAYCORE_GPT_6_ASTRA', 'chat-b'), incoming).conversationPreferences, incoming.conversationOverride);
});
test('unbound draft preferences survive a runtime refresh', () => {
  const state = refresh(null, null, null);
  assert.equal(state.conversationPreferences.toneOverride, 'new-draft');
});
