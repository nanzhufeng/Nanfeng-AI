import test from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import vm from 'node:vm';
import { memorySummarySuggestion } from '../src/memory-summary-policy.mjs';
import { renderChatFirstShell } from '../src/chat-shell.mjs';

const userText = '我的南枫项目长期关注 AI 与影视投资';
const assistantText = '我会把这个长期项目偏好与研究方向拆成稳定原则，并在后续建议里优先考虑。'.repeat(3);
const draft = memorySummarySuggestion(userText, assistantText);

function shell({ enabled = true, personalization = true, memory = [], delivery = 'COMPLETE' } = {}) {
  const data = { summary: { id: 'workspace-one' }, exchange: { memory, conversations: [{
    id: 'conversation-one', title: '对话', currentLeafId: 'assistant-one', messages: [
      { id: 'user-one', role: 'user', delivery: 'COMPLETE', blocks: [{ kind: 'TEXT', text: userText }] },
      { id: 'assistant-one', parentId: 'user-one', role: 'assistant', delivery, blocks: [{ kind: 'TEXT', text: assistantText }] },
    ],
  }] } };
  return renderChatFirstShell({ data, native: true, selectedConversationId: 'conversation-one', composerDraft: '', pane: 'chat', productSettings: { memoryEnabled: personalization, memoryRetrievalEnabled: enabled } });
}

test('durable, non-sensitive completed turn offers a local memory confirmation', () => {
  assert.equal(draft?.title, '个人项目');
  assert.equal(draft?.body, userText);
  assert.match(shell(), /data-action="suggest-memory-summary"/);
  assert.doesNotMatch(shell({ enabled: false }), /data-action="suggest-memory-summary"/);
  assert.doesNotMatch(shell({ personalization: false }), /data-action="suggest-memory-summary"/);
  assert.doesNotMatch(shell({ delivery: 'PARTIAL' }), /data-action="suggest-memory-summary"/);
  assert.doesNotMatch(shell({ memory: [{ body: draft.body, status: 'ACTIVE' }] }), /data-action="suggest-memory-summary"/);
});

test('ordinary, explicit-save, and sensitive turns never trigger an unsolicited suggestion', () => {
  assert.equal(memorySummarySuggestion('帮我翻译这一句', assistantText), null);
  assert.equal(memorySummarySuggestion('记住了：我的南枫项目长期关注影视投资', assistantText), null);
  assert.equal(memorySummarySuggestion('我的南枫项目长期使用 API Key: secret', assistantText), null);
  assert.equal(memorySummarySuggestion(userText, '短回答'), null);
});

test('same-topic suggestion presents an explicit choice with escaped existing content', async () => {
  const source = await readFile(new URL('../src/app.mjs', import.meta.url), 'utf8');
  const start = source.indexOf('function activeGlobalMemoryTopic(memories, title) {');
  const end = source.indexOf('async function clearMemorySummary()', start);
  assert.ok(start >= 0 && end > start);
  const matching = vm.runInNewContext(`${source.slice(start, end)}; activeGlobalMemoryTopic`);
  const memories = [{ id: 'global-one', title: '个人项目', body: '旧内容', scope: 'GLOBAL', status: 'ACTIVE' },
    { id: 'project-one', title: '个人项目', body: '项目内容', scope: 'PROJECT', scopeId: 'project-one', status: 'ACTIVE' }];
  assert.equal(matching(memories, '个人项目').length, 1);
  const branch = source.split('\n').find(line => line.includes("if (state.dialog?.kind === 'memory-topic-conflict')"));
  assert.ok(branch);
  const html = vm.runInNewContext(`(function(){${branch}})()`, { state: { dialog: { kind: 'memory-topic-conflict',
    title: '个人项目', body: '本次内容', existing: { body: '<旧内容>' }, expectedEntries: [{ id: 'global-one', revision: 1 }], submitting: false } },
    escape: value => String(value).replaceAll('<', '&lt;').replaceAll('>', '&gt;') });
  assert.match(html, /&lt;旧内容&gt;/);
  for (const action of ['keep-memory-topic', 'parallel-memory-topic', 'revise-memory-topic']) {
    assert.match(html, new RegExp(`data-action="${action}"`));
  }
});
