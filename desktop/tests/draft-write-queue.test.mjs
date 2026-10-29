import assert from 'node:assert/strict';
import test from 'node:test';
import { createDraftWriteQueue } from '../src/draft-write-queue.mjs';

const draft = (conversationId, text, workspaceId = 'workspace-one') => ({ workspaceId, conversationId, text, attachmentIds: [] });
const key = value => JSON.stringify([value.workspaceId, value.conversationId]);
function fixture(prepare) {
  const stored = new Map();
  const denied = new Set();
  const queue = createDraftWriteQueue({ prepare, write: async value => {
    if (denied.has(key(value))) throw new Error('write denied');
    stored.set(key(value), structuredClone(value));
  } });
  return { queue, stored, denied };
}

test('flush retries a failed local save without requiring another edit', async () => {
  const { queue, stored, denied } = fixture();
  const value = draft(null, '保存失败后仍保留的稿');
  denied.add(key(value));
  await assert.rejects(queue.save(value), /write denied/);
  denied.clear();
  await queue.flush();
  assert.deepEqual(stored.get(key(value)), value);
});

test('a later successful save on another route does not hide the failed route', async () => {
  const { queue, stored, denied } = fixture();
  const a = draft('chat-a', 'A 失败稿');
  const b = draft('chat-b', 'B 已保存稿');
  denied.add(key(a));
  await assert.rejects(queue.save(a));
  await queue.save(b);
  denied.clear();
  await queue.flush();
  assert.deepEqual(stored.get(key(a)), a);
  assert.deepEqual(stored.get(key(b)), b);
});

test('an old failed draft cannot overwrite a newer successful edit', async () => {
  const { queue, stored, denied } = fixture();
  const old = draft('chat-a', '旧稿');
  const latest = draft('chat-a', '最新稿');
  denied.add(key(old));
  await assert.rejects(queue.save(old));
  denied.clear();
  await queue.save(latest);
  await queue.flush();
  assert.deepEqual(stored.get(key(latest)), latest);
});

test('a failed explicit clear supersedes an older failed unbound draft', async () => {
  const { queue, stored, denied } = fixture();
  const old = draft(null, '不应该复活的稿');
  const clear = draft(null, '');
  denied.add(key(old));
  await assert.rejects(queue.save(old));
  await assert.rejects(queue.save(clear));
  denied.clear();
  await queue.flush();
  assert.deepEqual(stored.get(key(clear)), clear);
});

test('a bootstrap failure later resolves to the same route without reviving stale text', async () => {
  let ready = false;
  const { queue, stored } = fixture(async value => {
    if (!value.workspaceId && !ready) throw new Error('initialization denied');
    return { ...value, workspaceId: value.workspaceId || 'workspace-created' };
  });
  await assert.rejects(queue.save(draft(null, '初始化前的旧稿', null)), /initialization denied/);
  ready = true;
  const latest = draft(null, '初始化后明确清改的新稿', 'workspace-created');
  await queue.save(latest);
  await queue.flush();
  assert.deepEqual(stored.get(key(latest)), latest);
});

test('failed flush is visible and the next flush may retry the retained snapshot', async () => {
  const { queue, stored, denied } = fixture();
  const value = draft('chat-a', '仍待保存');
  denied.add(key(value));
  await assert.rejects(queue.save(value));
  await assert.rejects(queue.flush(), /write denied/);
  assert.equal(stored.size, 0);
  denied.clear();
  await queue.flush();
  assert.deepEqual(stored.get(key(value)), value);
});

test('queued text and attachment IDs are snapshots, not mutable view state', async () => {
  const { queue, stored } = fixture();
  const value = draft('chat-a', '排队时正文');
  value.attachmentIds.push('asset-one');
  const saving = queue.save(value);
  value.text = '后续界面正文';
  value.attachmentIds.push('asset-two');
  await saving;
  await queue.flush();
  assert.deepEqual(stored.get(key(value)), { workspaceId:'workspace-one', conversationId:'chat-a', text:'排队时正文', attachmentIds:['asset-one'] });
});

test('workspace identity separates otherwise identical conversation keys', async () => {
  const { queue, stored, denied } = fixture();
  const a = draft(null, '工作区一草稿', 'workspace-one');
  const b = draft(null, '工作区二草稿', 'workspace-two');
  denied.add(key(a));
  await assert.rejects(queue.save(a));
  await queue.save(b);
  denied.clear();
  await queue.flush();
  assert.deepEqual(stored.get(key(a)), a);
  assert.deepEqual(stored.get(key(b)), b);
});

test('temporary drafts use their own stable IDs and retry independently', async () => {
  let available = false;
  const stored = new Map();
  const queue = createDraftWriteQueue({ routeKey: value => value.temporaryId, write: async value => {
    if (!available && value.temporaryId === 'temporary-a') throw new Error('write denied');
    stored.set(value.temporaryId, value.draft);
  } });
  await assert.rejects(queue.save({ temporaryId: 'temporary-a', draft: 'A 临时稿' }));
  await queue.save({ temporaryId: 'temporary-b', draft: 'B 临时稿' });
  available = true;
  await queue.flush();
  assert.deepEqual([...stored].sort(), [['temporary-a', 'A 临时稿'], ['temporary-b', 'B 临时稿']]);
});

test('an expired temporary route can be discarded without blocking another route', async () => {
  const stored = new Map();
  const queue = createDraftWriteQueue({ routeKey: value => value.temporaryId, write: async value => {
    if (value.temporaryId === 'expired') throw new Error('临时会话已过期或不存在');
    stored.set(value.temporaryId, value.draft);
  } });
  await assert.rejects(queue.save({ temporaryId: 'expired', draft: '已到期' }));
  queue.discardRoute('expired');
  await queue.save({ temporaryId: 'new', draft: '新会话' });
  await queue.flush();
  assert.deepEqual([...stored], [['new', '新会话']]);
});

test('flush also waits for an edit accepted while a retained failure is being retried', async () => {
  let available = false;
  let release, started;
  const retryStarted = new Promise(resolve => { started = resolve; });
  const retryGate = new Promise(resolve => { release = resolve; });
  let stored = '';
  const queue = createDraftWriteQueue({ write: async value => {
    if (!available) throw new Error('write denied');
    if (value.text === '旧稿') { started(); await retryGate; }
    stored = value.text;
  } });
  await assert.rejects(queue.save(draft(null, '旧稿')));
  available = true;
  const flushing = queue.flush();
  await retryStarted;
  const newer = queue.save(draft(null, '关闭等待期间的新稿'));
  release();
  await flushing;
  assert.equal(stored, '关闭等待期间的新稿');
  await newer;
});
