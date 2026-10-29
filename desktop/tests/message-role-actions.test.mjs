import test from 'node:test';
import assert from 'node:assert/strict';
import { renderChatFirstShell } from '../src/chat-shell.mjs';

for (const role of ['user', 'assistant', 'tool', 'system']) {
  test(`${role} messages retain supported copy/share actions and expose only an executable menu`, () => {
    const conversation = { id: 'role-conversation', title: '合成角色操作', currentLeafId: 'role-message', messages: [
      { id: 'source-user', role: 'user', delivery: 'COMPLETE', blocks: [{ kind: 'TEXT', text: '合成问题' }] },
      { id: 'role-message', parentId: 'source-user', role, delivery: 'COMPLETE', blocks: [{ kind: 'TEXT', text: '合成正文' }] },
    ] };
    const data = { summary: { id: 'role-workspace' }, exchange: { conversations: [conversation], projects: [], knowledge: [], memory: [], relations: [] } };
    const html = renderChatFirstShell({ data, native: true, selectedConversationId: conversation.id, pane: 'chat' });
    const buttons = [...html.matchAll(/<button\b[^>]*>/g)].map(match => match[0]).filter(button => button.includes('data-message-id="role-message"'));
    const has = action => buttons.some(button => button.includes(`data-action="${action}"`));
    assert.equal(has('copy-message'), true);
    assert.equal(has('share-message'), role !== 'user');
    assert.equal(has('open-assistant-message-menu'), ['user', 'assistant'].includes(role));
  });
}
