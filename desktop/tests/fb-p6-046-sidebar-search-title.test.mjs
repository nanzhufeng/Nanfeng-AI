import assert from 'node:assert/strict';
import test from 'node:test';
import { readFile } from 'node:fs/promises';

const root = new URL('../', import.meta.url);
const [shell, css] = await Promise.all([
  readFile(new URL('src/chat-shell.mjs', root), 'utf8'),
  readFile(new URL('src/chat-shell.css', root), 'utf8'),
]);

test('sidebar search has no static title or duplicate button while preserving its accessible input', () => {
  const navigation = shell.slice(shell.indexOf('const chatNavigation'), shell.indexOf('return `', shell.indexOf('const chatNavigation')));
  assert.match(navigation, /<div class="chat-search-wrap"><label><input id="chat-search" class="chat-search" type="search" placeholder="搜索" aria-label="搜索"/);
  assert.doesNotMatch(navigation, /<button data-action="submit-search"/);
  assert.doesNotMatch(navigation, /chat-search-wrap"><span/);
  assert.doesNotMatch(css, /\.chat-search-wrap > span/);
});
