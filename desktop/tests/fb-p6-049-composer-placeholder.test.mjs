import assert from 'node:assert/strict';
import test from 'node:test';
import { readFile } from 'node:fs/promises';

const shell = await readFile(new URL('../src/chat-shell.mjs', import.meta.url), 'utf8');

test('FB-P6-049 keeps the exact composer placeholder while preserving the accessible label', () => {
  assert.match(shell, /id="chat-composer" maxlength="12000" placeholder="回复 南枫AI" aria-label="输入内容"/);
  assert.doesNotMatch(shell, /id="chat-composer"[^>]*placeholder="输入内容/);
});
