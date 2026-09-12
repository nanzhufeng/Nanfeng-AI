import test from 'node:test';
import assert from 'node:assert/strict';
import { renderSafeMarkdown } from '../src/safe-markdown.mjs';

test('text preview preserves Android inert link labels instead of replacing them with source badges', () => {
  const html = renderSafeMarkdown('# 标题\n\n正文[原始链接标题](https://example.com)\n\n来源：\n[另一个标题](https://example.org)', { inert: true });
  assert.match(html, /<h2>标题<\/h2>/);
  assert.match(html, /正文<span>原始链接标题<\/span>/);
  assert.match(html, /另一个标题/);
  assert.doesNotMatch(html, /<button|<a\b|data-action|chat-source-shortcut/);
});

test('inert preview formats code and tables without dead copy controls and never executes HTML', () => {
  const html = renderSafeMarkdown('```js\n<script>alert(1)</script>\n```\n\n| A | B |\n| --- | --- |\n| 1 | 2 |', { inert: true });
  assert.match(html, /<table>/);
  assert.match(html, /&lt;script&gt;/);
  assert.doesNotMatch(html, /<script|<button|data-copy/);
  assert.match(renderSafeMarkdown('```js\nx\n```'), /data-action="copy-markdown-block"/);
});
