import assert from 'node:assert/strict';
import test from 'node:test';
import { mergeCloudConversationList } from '../src/cloud-conversation-list-merge.mjs';

const row = (id, patch = {}) => ({
  workspaceId: 'workspace-safe', id, title: id, pinned: false, cloudPinned: false, ...patch,
});

test('cloud read merges refreshed rows and additions without resetting existing cloud pins or rows', () => {
  const merged = mergeCloudConversationList([
    row('pinned', { title: '旧标题', pinned: true, cloudPinned: true }),
    row('cached-only', { title: '仅读取前已有的会话' }),
  ], [
    row('pinned', { title: '云端新标题' }),
    row('new', { title: '新读取到的会话' }),
  ]);

  assert.deepEqual(merged.map(item => item.id), ['pinned', 'new', 'cached-only']);
  assert.equal(merged[0].title, '云端新标题');
  assert.equal(merged[0].pinned, true);
  assert.equal(merged[0].cloudPinned, true);
  assert.equal(merged[2].title, '仅读取前已有的会话');
  assert.equal(merged[1].title, '新读取到的会话');
});

test('complete authenticated membership removes deleted cloud rows but preserves present failed rows', () => {
  const merged = mergeCloudConversationList([row('deleted'), row('present-but-conflicted')], [row('new')],
    ['workspace-safe:present-but-conflicted', 'workspace-safe:new']);
  assert.deepEqual(merged.map(item => item.id), ['new', 'present-but-conflicted']);
});

test('confirmed empty cloud removes cache rows while an unavailable membership does not', () => {
  assert.deepEqual(mergeCloudConversationList([row('deleted')], [], []), []);
  assert.equal(mergeCloudConversationList([row('keep')], [], null).length, 1);
});
