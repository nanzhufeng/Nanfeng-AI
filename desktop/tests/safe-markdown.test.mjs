import assert from 'node:assert/strict';
import test from 'node:test';
import { renderSafeMarkdown } from '../src/safe-markdown.mjs';
import { assistantCostCny, compactModelName, renderChatFirstShell } from '../src/chat-shell.mjs';

test('assistant Markdown preserves structure while escaping executable HTML', () => {
  const html = renderSafeMarkdown(`# 标题\n\n- 第一项\n- **第二项**\n\n> 引用\n\n| 项目 | 状态 |\n| --- | --- |\n| 搜索 | 完成 |\n\n\`inline\`\n\n\`\`\`js\nalert('<x>')\n\`\`\`\n\n<script>alert(1)</script>\n\n[官网](https://example.com) [危险](javascript:alert(1))`);
  for (const token of ['<h2>标题</h2>', '<ul>', '<strong>第二项</strong>', '<blockquote>', '<table>', '<code>inline</code>', 'chat-markdown-code', 'https://example.com/']) assert.match(html, new RegExp(token));
  assert.doesNotMatch(html, /<script>/);
  assert.match(html, /&lt;script&gt;/);
  assert.doesNotMatch(html, /href="javascript:/);
});

test('assistant Markdown highlighting does not alter generated tags', () => {
  const html = renderSafeMarkdown('## 搜索结果\n\n- 搜索附件', { query: '搜索' });
  assert.equal((html.match(/<mark>/g) || []).length, 2);
  assert.match(html, /<h3><mark>搜索<\/mark>结果<\/h3>/);
});

test('headerless pipe rows from provider replies become a readable table', () => {
  const html = renderSafeMarkdown('| 长(mm) | 4950 | 5085 |\n| 宽(mm) | 1935 | 1960 |\n| 动力 | 纯电 | 增程 |');
  assert.match(html, /chat-markdown-table-wrap/);
  assert.match(html, /<td>长\(mm\)<\/td>/);
  assert.match(html, /<td>增程<\/td>/);
  assert.doesNotMatch(html, /<strong>/);
});

test('Desktop footer uses Android compact model names and four-decimal RMB projection', () => {
  assert.equal(compactModelName('OpenRouter · GPT-5.6 Sol'), '5.6 Sol');
  assert.equal(compactModelName('DeepSeek V4 Flash'), 'DS V4');
  assert.equal(assistantCostCny(1_000_000, 'USD'), '¥6.7203');
  assert.equal(assistantCostCny(1_000_000, 'CNY'), '¥1.0000');
});

test('only Assistant text is rendered as Markdown in the conversation', () => {
  const data = { summary: { id: 'w', title: 'w' }, exchange: { projects: [], knowledge: [], memory: [], relations: [], conversations: [{ id: 'c', title: '格式', revision: 1, messages: [
    { id: 'u', role: 'user', createdAt: '2026-09-01T00:00:00Z', blocks: [{ kind: 'TEXT', text: '# 用户原文' }] },
    { id: 'a', role: 'assistant', createdAt: '2026-09-01T00:00:01Z', modelSnapshot: { displayName: 'GPT-5.6 Sol' }, chargeMicros: 1_000_000, currencyCode: 'USD', blocks: [{ kind: 'TEXT', text: '# 助手标题\n\n- 条目' }] },
  ] }] } };
  const html = renderChatFirstShell({ data, native: true, selectedConversationId: 'c', pane: 'chat', status: '', error: '', connection: {} });
  assert.match(html, /<p># 用户原文<\/p>/);
  assert.match(html, /<h2>助手标题<\/h2>/);
  assert.match(html, /5\.6 Sol/);
  assert.match(html, /¥6\.7203/);
});

test('imported uppercase assistant roles use the same Markdown projection', () => {
  const data = { summary: { id: 'w' }, exchange: { projects: [], knowledge: [], memory: [], relations: [], conversations: [{ id: 'c', title: '格式', revision: 1, messages: [
    { id: 'a', role: 'ASSISTANT', createdAt: '2026-09-01T00:00:01Z', blocks: [{ kind: 'TEXT', text: '| 项目 | 状态 |\n| --- | --- |\n| 加粗 | **已同步** |' }] },
  ] }] } };
  const html = renderChatFirstShell({ data, native: true, selectedConversationId: 'c', pane: 'chat', status: '', error: '', connection: {} });
  for (const token of ['chat-message assistant', 'chat-markdown-body', 'chat-markdown-table-wrap', '<strong>已同步</strong>']) assert.ok(html.includes(token), token);
});
