import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import test from 'node:test';
import { renderSafeMarkdown } from '../src/safe-markdown.mjs';
import { assistantCostCny, compactModelName, renderChatFirstShell } from '../src/chat-shell.mjs';
import { cnyCostLabel, projectedMessageCost } from '../src/desktop-cost-estimator.mjs';

const chatShellCss = await readFile(new URL('../src/chat-shell.css', import.meta.url), 'utf8');

test('assistant Markdown preserves structure while escaping executable HTML', () => {
  const html = renderSafeMarkdown(`# 标题\n\n- 第一项\n- **第二项**\n\n> 引用\n\n| 项目 | 状态 |\n| --- | --- |\n| 搜索 | 完成 |\n\n\`inline\`\n\n\`\`\`js\nalert('<x>')\n\`\`\`\n\n<script>alert(1)</script>\n\n[官网](https://example.com) [危险](javascript:alert(1))`);
  for (const token of ['<h2>标题</h2>', 'chat-markdown-list chat-markdown-unordered', '<strong>第二项</strong>', '<blockquote>', '<table>', '<code>inline</code>', 'chat-markdown-code', 'https://example.com/']) assert.match(html, new RegExp(token));
  assert.doesNotMatch(html, /<script>/);
  assert.match(html, /&lt;script&gt;/);
  assert.doesNotMatch(html, /href="javascript:/);
});

test('assistant Markdown highlighting does not alter generated tags', () => {
  const html = renderSafeMarkdown('## 搜索结果\n\n- 搜索附件', { query: '搜索' });
  assert.equal((html.match(/<mark>/g) || []).length, 2);
  assert.match(html, /<h3><mark>搜索<\/mark>结果<\/h3>/);
});

test('Chinese Markdown headings remain headings when an imported answer omits the optional space', () => {
  const html = renderSafeMarkdown('###二、估值、买点与仓位管理（基于2026年8月底市场状态）\n\n正文。');
  assert.match(html, /<h4>二、估值、买点与仓位管理（基于2026年8月底市场状态）<\/h4>/);
  assert.doesNotMatch(html, /###二、/);
  assert.match(renderSafeMarkdown('#hash 不是标题'), /<p>#hash 不是标题<\/p>/);
});

test('LaTex display and inline math become readable math rather than escaped transcript debris', () => {
  const html = renderSafeMarkdown('也就是说：\n\n\\[\nAI_n \\rightarrow AI开发 \\rightarrow AI_{n+1}\n\\]\n\n然后：\\(AI_{n+1} > AI_n\\)');
  assert.match(html, /chat-markdown-math-display/);
  assert.match(html, /AI<sub>n<\/sub> → AI开发 → AI<sub>n\+1<\/sub>/);
  assert.match(html, /role="math"/);
  assert.doesNotMatch(html, /\\\\\[|\\\\\]|\\\\rightarrow|AI_\{n\+1\}/);
  assert.match(chatShellCss, /\.chat-markdown-math-display \{[^}]*text-align: center/);
});

test('imported source links use the Android compact source shortcut rather than title-sized inline links', () => {
  const html = renderSafeMarkdown('来源：\n\n- [中证指数有限公司](https://www.csindex.com.cn/a)\n- [Reuters](https://www.reuters.com/b)');

  assert.equal((html.match(/chat-source-shortcut/g) || []).length, 1);
  assert.match(html, /data-action="open-source-links"/);
  assert.match(html, /chat-source-site-glyphs/);
  assert.match(html, /csindex\.com\.cn/);
  assert.doesNotMatch(html, /chat-markdown-link/);
  assert.doesNotMatch(html, />中证指数有限公司</);
});

test('source website glyphs retain the Android-colored circular surface and visible globe stroke', () => {
  assert.match(chatShellCss, /background: var\(--chat-source-site-color, #64748b\)/);
  assert.match(chatShellCss, /\.chat-source-site-glyph svg \{[^}]*fill: none;[^}]*stroke: currentColor;[^}]*stroke-width: 1\.8/);
});

test('imported source links tolerate whitespace between Markdown label and URL', () => {
  const html = renderSafeMarkdown('[中证指数有限公司]\n(https://oss-ch.csindex.com.cn/index.pdf)');

  assert.match(html, /chat-source-shortcut/);
  assert.match(html, /oss-ch\.csindex\.com\.cn/);
  assert.doesNotMatch(html, /&lbrack;中证指数有限公司/);
});

test('headerless pipe rows from provider replies become a readable table', () => {
  const html = renderSafeMarkdown('| 长(mm) | 4950 | 5085 |\n| 宽(mm) | 1935 | 1960 |\n| 动力 | 纯电 | 增程 |');
  assert.match(html, /chat-markdown-table-wrap/);
  assert.match(html, /<td>长\(mm\)<\/td>/);
  assert.match(html, /<td>增程<\/td>/);
  assert.doesNotMatch(html, /<strong>/);
});

test('code blocks and tables expose their own structured-copy actions', () => {
  const html = renderSafeMarkdown('```kotlin\nval amount = "¥88"\n```\n\n| 项目 | 金额 |\n| --- | --- |\n| 午餐 | ¥88 |');

  assert.equal((html.match(/data-action="copy-markdown-block"/g) || []).length, 2);
  assert.equal((html.match(/data-copy-action/g) || []).length, 2);
  assert.match(html, /data-copy-label="代码块"/);
  assert.match(html, /data-copy-text="val amount = &quot;¥88&quot;"/);
  assert.match(html, /data-copy-label="表格（保留 Markdown 格式）"/);
  assert.match(html, /data-copy-text="\| 项目 \| 金额 \|/);
});

test('Desktop footer uses Android compact model names and four-decimal RMB projection', () => {
  assert.equal(compactModelName('OpenRouter · GPT-5.6 Sol'), '5.6 Sol');
  assert.equal(compactModelName('DeepSeek V4 Flash'), 'DS V4');
  assert.equal(compactModelName('deepseek-flash'), 'DS V4.1');
  assert.equal(compactModelName('anthropic/claude-opus-5'), 'Opus 5');
  assert.equal(assistantCostCny(1_000_000, 'USD'), '¥6.7203');
  assert.equal(assistantCostCny(1_000_000, 'CNY'), '¥1.0000');
});

test('restored Android local estimates retain their estimate disclosure instead of becoming provider charges', () => {
  const cost = projectedMessageCost({
    createdAt: '2026-08-31T00:00:00.000Z', actualModelId: 'qwen3.8-max',
    usage: { inputTokens: 10, outputTokens: 8 }, chargeMicros: 420, currencyCode: 'CNY', costSource: 'LOCAL_ESTIMATE',
  });

  assert.deepEqual(cost, { chargeMicros: 420, currencyCode: 'CNY', costSource: 'LOCAL_ESTIMATE' });
  assert.equal(cnyCostLabel(cost, { estimatedLabel: true, maximumFractionDigits: 6 }), '≈ ¥0.00042（估算）');
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
