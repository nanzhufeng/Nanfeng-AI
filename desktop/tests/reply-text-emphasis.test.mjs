import assert from 'node:assert/strict';
import test from 'node:test';
import { createReplyEmphasisBudget } from '../src/reply-text-emphasis.mjs';
import { renderSafeMarkdown } from '../src/safe-markdown.mjs';
import { renderChatFirstShell } from '../src/chat-shell.mjs';

test('emphasis is bounded per reply and follows authored order across blocks', () => {
  const emphasis = createReplyEmphasisBudget();
  const html = renderSafeMarkdown('__优先保存__ **保留原文** **第三处**', { emphasis })
    + renderSafeMarkdown('## 风险\n\n**检查余额** **不要重发**\n\n## 建议\n\n**普通重点**', { emphasis });
  assert.equal((html.match(/chat-text-important/g) || []).length, 2);
  assert.equal((html.match(/chat-text-caution/g) || []).length, 1);
  assert.match(html, /chat-text-important">优先保存/);
  assert.match(html, /chat-text-caution">检查余额/);
  assert.match(html, /<strong>第三处<\/strong>/);
});

test('technical content and quoted or tabular prose do not consume the budget', () => {
  const html = renderSafeMarkdown('## **标题重点**\n\n> **引用内容**\n\n| 项目 | 状态 |\n| --- | --- |\n| **表格内容** | 保留 |\n\n**`code`** **[来源](https://example.com)** **$x+y$** **<script>**\n\n**正常重点**', { emphasis: createReplyEmphasisBudget() });
  assert.equal((html.match(/chat-text-important/g) || []).length, 1);
  assert.match(html, /chat-text-important">正常重点/);
  assert.doesNotMatch(html, /<script>/);
  assert.match(html, /&lt;script&gt;/);
  assert.match(html, /chat-source-shortcut/);
  assert.doesNotMatch(renderSafeMarkdown('**默认保持加粗**'), /chat-text-/);
});

test('long spans and numeric tokens stay neutral, total colored text is capped', () => {
  const emphasis = createReplyEmphasisBudget();
  for (const text of ['1', '1234', '内容'.repeat(21), 'NFMARKDOWNTOKEN0X', 'a&b']) assert.equal(emphasis.take(text), null);
  assert.equal(emphasis.take('重点'.repeat(20)), 'important');
  assert.equal(emphasis.take('结论'.repeat(20)), 'important');
  assert.equal(emphasis.take('注意：' + '检查'.repeat(8)), null);
  assert.equal(emphasis.take('注意：余额'), 'caution');
});

test('only assistant answer text receives colors, with a fresh budget per message', () => {
  const messages = [
    { id: 'u', role: 'user', blocks: [{ kind: 'TEXT', text: '**用户原文**' }] },
    { id: 'a', role: 'assistant', blocks: [{ kind: 'REASONING', text: '**推理重点**' }, { kind: 'TEXT', text: '**第一重点** **第二重点**' }, { kind: 'TEXT', text: '**第三重点**' }] },
    { id: 'b', role: 'assistant', blocks: [{ kind: 'TEXT', text: '**下一回答**' }] },
  ].map(message => ({ ...message, createdAt: '2026-10-11T00:00:00Z' }));
  const data = { summary: { id: 'w', title: 'w' }, exchange: { projects: [], knowledge: [], memory: [], relations: [], conversations: [{ id: 'c', title: '颜色', revision: 1, messages }] } };
  const html = renderChatFirstShell({ data, native: true, selectedConversationId: 'c', pane: 'chat', status: '', error: '', connection: {} });
  assert.equal((html.match(/class="chat-text-important"/g) || []).length, 3);
  assert.match(html, /<strong>推理重点<\/strong>/);
  assert.match(html, /<strong>第三重点<\/strong>/);
  assert.match(html, /\*\*用户原文\*\*/);
});
