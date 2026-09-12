import assert from 'node:assert/strict';
import test from 'node:test';
import { readFile } from 'node:fs/promises';

const shell = await readFile(new URL('../src/chat-shell.mjs', import.meta.url), 'utf8');
const css = await readFile(new URL('../src/chat-shell.css', import.meta.url), 'utf8');

test('FB-P6-049 keeps the exact composer placeholder while preserving the accessible label', () => {
  assert.match(shell, /id="chat-composer" rows="1" maxlength="12000" placeholder="回复 南枫AI" aria-label="输入内容" aria-description="可直接粘贴或拖入图片、PDF、视频和文件"/);
  assert.doesNotMatch(shell, /id="chat-composer"[^>]*placeholder="输入内容/);
  assert.doesNotMatch(shell, /id="chat-composer"[^>]*title=/);
});

test('FB-P6-049 locks only the placeholder typography against WebKit hover text adjustment', () => {
  assert.match(css, /\.chat-composer #chat-composer::placeholder \{[^}]*font-size: 14px;[^}]*font-weight: 400;[^}]*line-height: 22px;[^}]*-webkit-text-size-adjust: 100%;[^}]*text-size-adjust: 100%;[^}]*opacity: 1;/s);
  assert.match(css, /\.chat-composer #chat-composer,\n\.chat-composer #chat-composer:hover,[\s\S]*?-webkit-text-size-adjust: 100%;[\s\S]*?text-size-adjust: 100%;/);
});

test('FB-P6-049 keeps the composer text inset fixed across hover and focus', () => {
  const inputStates = css.slice(css.indexOf('.chat-composer #chat-composer,'), css.indexOf('.chat-composer #chat-composer::placeholder'));
  assert.match(inputStates, /padding: 7px 12px;/);
  assert.doesNotMatch(inputStates, /padding: 7px 8px;/);
});
