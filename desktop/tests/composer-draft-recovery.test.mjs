import assert from 'node:assert/strict';
import test from 'node:test';
import { readComposerDraftRoute, createDraftCloseGuard, transitionComposerDraftRoute } from '../src/composer-draft-recovery.mjs';

test('startup restores a saved unbound draft instead of the default existing conversation', async () => {
  const result = await readComposerDraftRoute({ workspaceId: 'workspace-local', conversationId: 'chat-old', preferUnbound: true,
    readDraft: async (_workspace, conversation) => conversation === null
      ? { text: '未发送的新稿', attachments: [] } : { text: '旧会话稿', attachments: [] } });
  assert.deepEqual(result, { conversationId: null, draft: { text: '未发送的新稿', attachments: [] } });
});

test('an attachment-only unbound draft is also recoverable at startup', async () => {
  const draft = { text: '', attachments: [{ id: 'image-one' }] };
  const result = await readComposerDraftRoute({ workspaceId: 'workspace-local', conversationId: 'chat-old', preferUnbound: true,
    readDraft: async (_workspace, conversation) => conversation === null ? draft : null });
  assert.deepEqual(result, { conversationId: null, draft });
});

test('a model-only unbound draft is recoverable at startup', async () => {
  const draft = { text: '', attachments: [], modelId: 'DEEPSEEK_V4_PRO' };
  const result = await readComposerDraftRoute({ workspaceId: 'workspace-local', conversationId: 'chat-old', preferUnbound: true,
    readDraft: async (_workspace, conversation) => conversation === null ? draft : null });
  assert.deepEqual(result, { conversationId: null, draft });
});

test('tone and explicit disabled web search are recoverable without text or attachments', async () => {
  const draft = { text: '', attachments: [], modelId: null, toneOverride: 'professional', webSearchOverride: false };
  const result = await readComposerDraftRoute({ workspaceId: 'workspace-local', conversationId: 'chat-old', preferUnbound: true,
    readDraft: async (_workspace, conversation) => conversation === null ? draft : null });
  assert.deepEqual(result, { conversationId: null, draft });
});

test('an explicit conversation selection reads that conversation, preserving its own draft', async () => {
  const result = await readComposerDraftRoute({ workspaceId: 'workspace-local', conversationId: 'chat-chosen',
    readDraft: async (_workspace, conversation) => conversation === null
      ? { text: '另一个新稿', attachments: [] } : { text: '用户选中会话的草稿', attachments: [] } });
  assert.equal(result.conversationId, 'chat-chosen');
  assert.equal(result.draft.text, '用户选中会话的草稿');
});

test('empty or absent unbound drafts keep the existing conversation route', async () => {
  for (const unbound of [null, { text: '', attachments: [] }]) {
    const result = await readComposerDraftRoute({ workspaceId: 'workspace-local', conversationId: 'chat-old', preferUnbound: true,
      readDraft: async (_workspace, conversation) => conversation === null ? unbound : { text: '继续旧稿', attachments: [] } });
    assert.equal(result.conversationId, 'chat-old');
    assert.equal(result.draft.text, '继续旧稿');
  }
});

test('read failure remains a failure, not an empty successful recovery', async () => {
  await assert.rejects(readComposerDraftRoute({ workspaceId: 'workspace-local', conversationId: null, preferUnbound: true,
    readDraft: async () => { throw new Error('store unavailable'); } }), /store unavailable/);
});

test('normal close waits for the durable draft, including repeated close requests', async () => {
  let finish;
  const saved = new Promise(resolve => { finish = resolve; });
  const events = [];
  const guard = createDraftCloseGuard({ flush: () => saved.then(() => events.push('durable')),
    close: async () => { events.push('closed'); }, failed: () => { events.push('failed'); } });
  const pending = guard({ preventDefault: () => events.push('prevented') });
  const repeated = guard({ preventDefault: () => events.push('prevented') });
  assert.deepEqual(events, ['prevented', 'prevented']);
  finish();
  await Promise.all([pending, repeated]);
  assert.deepEqual(events, ['prevented', 'prevented', 'durable', 'closed']);
});

test('save failure keeps the window open and allows a later close to retry', async () => {
  let available = false;
  let closed = false;
  let shown = '';
  const guard = createDraftCloseGuard({ flush: async () => { if (!available) throw new Error('storage unavailable'); },
    close: async () => { closed = true; }, failed: error => { shown = error.message; } });
  await guard({ preventDefault() {} });
  assert.equal(closed, false);
  assert.equal(shown, 'storage unavailable');
  available = true;
  await guard({ preventDefault() {} });
  assert.equal(closed, true);
});

test('a route change saves the pending source before reading its next projection', async () => {
  let persisted = '旧稿';
  let shown = '尚未落盘的新稿';
  await transitionComposerDraftRoute({ flush: async () => { persisted = shown; }, read: async () => persisted,
    isCurrent: () => true, commit: value => { shown = value; } });
  assert.equal(shown, '尚未落盘的新稿');
  assert.equal(persisted, shown);
});

test('a failed source save leaves the route and current text intact', async () => {
  let route = 'temporary';
  let read = false;
  await assert.rejects(transitionComposerDraftRoute({ flush: async () => { throw new Error('storage unavailable'); },
    read: async () => { read = true; }, isCurrent: () => true, commit: () => { route = 'ordinary'; } }), /storage unavailable/);
  assert.equal(route, 'temporary');
  assert.equal(read, false);
});

test('new input during a save cancels the obsolete route change', async () => {
  let current = true;
  let shown = '新输入';
  const changed = await transitionComposerDraftRoute({ flush: async () => { current = false; },
    read: async () => '旧投影', isCurrent: () => current, commit: value => { shown = value; } });
  assert.equal(changed, false);
  assert.equal(shown, '新输入');
});

test('a late destination read cannot replace input after another route decision', async () => {
  let resolve;
  const pending = new Promise(done => { resolve = done; });
  let current = true;
  let shown = '用户的新输入';
  const changing = transitionComposerDraftRoute({ flush: async () => {}, read: () => pending,
    isCurrent: () => current, commit: value => { shown = value; } });
  await Promise.resolve();
  current = false;
  resolve('迟到的临时稿');
  assert.equal(await changing, false);
  assert.equal(shown, '用户的新输入');
});
