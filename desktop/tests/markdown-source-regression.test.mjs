import assert from 'node:assert/strict';
import test from 'node:test';
import { renderSafeMarkdown } from '../src/safe-markdown.mjs';

test('source titles containing nested brackets stay in one usable source entry', () => {
  const html = renderSafeMarkdown('来源：\n- [The Macehead of Mesilim [CDLI Wiki]](https://www.cdli.ox.ac.uk/wiki/doku.php?id=macehead_of_mesilim&utm_source=openai)');
  assert.equal((html.match(/data-action="open-source-links"/g) || []).length, 1);
  assert.doesNotMatch(html, /\]\(https:|<li/);
  assert.match(html, /The Macehead of Mesilim \[CDLI Wiki\]/);
});

test('source destinations retain balanced parentheses and split labels', () => {
  const html = renderSafeMarkdown('来源：\n- [History [archive]]\n(https://example.com/King_(Sumer))\n- [Other](https://example.com/other)');
  assert.equal((html.match(/data-action="open-source-links"/g) || []).length, 1);
  assert.match(html, /https:\/\/example.com\/King_\(Sumer\)/);
  assert.doesNotMatch(html, /<li|<p>\)<\/p>/);
});

test('markdown-like code remains byte exact across split-link normalization', () => {
  const code = '[label]\n(https://example.com)';
  const html = renderSafeMarkdown('```text\n' + code + '\n```');
  assert.ok(html.includes('data-copy-text="' + code + '"'));
});

test('moving a parenthesized citation to a source entry leaves no empty wrapper', () => {
  const html = renderSafeMarkdown('正文。([证据](https://example.com)) 公式 () 与 (`code`) 保留。');
  assert.match(html, /正文。\s+公式 \(\) 与 \(<code>code<\/code>\) 保留/);
  assert.equal((html.match(/data-action="open-source-links"/g) || []).length, 1);
});
