import assert from 'node:assert/strict';
import test from 'node:test';
import { orderedConversationMessages, renderChatFirstShell } from '../src/chat-shell.mjs';

const reversedImportedConversation = {
  id: 'reversed-import',
  title: '导入顺序回归',
  currentLeafId: 'assistant-current',
  messages: [
    {
      id: 'assistant-current', parentId: 'user-root', ordinal: 0, role: 'assistant',
      createdAt: '2026-08-29T17:29:06.787000Z', blocks: [{ kind: 'TEXT', text: '这是回答' }],
    },
    {
      id: 'user-root', parentId: null, ordinal: 0, role: 'user',
      createdAt: '2026-08-29T17:29:06.787000Z', blocks: [{ kind: 'TEXT', text: '这是提问' }],
    },
    {
      id: 'assistant-stale-branch', parentId: 'user-root', ordinal: 1, role: 'assistant',
      createdAt: '2026-08-29T17:29:06.787000Z', blocks: [{ kind: 'TEXT', text: '不应显示的旧分支' }],
    },
  ],
};

test('current conversation path wins over imported array order and stale branches', () => {
  assert.deepEqual(
    orderedConversationMessages(reversedImportedConversation).map(message => message.id),
    ['user-root', 'assistant-current'],
  );
});

test('chat transcript renders a question above its answer even when import array is reversed', () => {
  const data = {
    summary: { id: 'workspace-order', title: '排序回归' },
    exchange: { projects: [], knowledge: [], memory: [], relations: [], conversations: [reversedImportedConversation] },
  };
  const html = renderChatFirstShell({
    data, native: true, selectedConversationId: reversedImportedConversation.id,
    composerDraft: '', composerAttachments: [], pane: 'chat', status: '', error: '', connection: {},
  });
  assert.ok(html.indexOf('这是提问') < html.indexOf('这是回答'));
  assert.ok(!html.includes('不应显示的旧分支'));
});
