import test from 'node:test';
import assert from 'node:assert/strict';
import { syncSelectedContinuation } from '../src/selected-conversation-continuation.mjs';

test('completed turns in either list resync the existing selected identity', async () => {
  const calls = [];
  const invoke = async (...args) => { calls.push(args); return { status: 'SYNCED' }; };
  for (const sidebarConversationList of ['local', 'cloud']) {
    assert.equal((await syncSelectedContinuation({ invoke, workspaceId: 'w', conversationId: 'c',
      syncedConversationKeys: ['w:c'], sidebarConversationList })).status, 'SYNCED');
  }
  assert.equal(calls.length, 2);
  assert.deepEqual(calls[0], ['sync_selected_desktop_conversation', { workspaceId: 'w', conversationId: 'c', continuation: true }]);
});

test('unselected conversations are never uploaded by continuation', async () => {
  const receipt = await syncSelectedContinuation({ invoke: () => assert.fail('unauthorized upload'),
    workspaceId: 'w', conversationId: 'c', syncedConversationKeys: ['other:c'] });
  assert.equal(receipt, null);
});

test('unknown and rejected cloud outcomes are not reported as success', async () => {
  const args = { workspaceId: 'w', conversationId: 'c', syncedConversationKeys: ['w:c'] };
  assert.equal((await syncSelectedContinuation({ ...args, invoke: async () => ({ status: 'UNKNOWN' }) })).status, 'UNKNOWN');
  await assert.rejects(syncSelectedContinuation({ ...args, invoke: async () => ({ status: 'REJECTED' }) }));
});
