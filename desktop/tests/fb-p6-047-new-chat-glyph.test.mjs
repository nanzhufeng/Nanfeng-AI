import assert from 'node:assert/strict';
import test from 'node:test';
import { readFile } from 'node:fs/promises';

const shell = await readFile(new URL('../src/chat-shell.mjs', import.meta.url), 'utf8');

test('new chat is a compact footer action with the mature file-and-pen glyph', () => {
  const navigation = shell.slice(shell.indexOf('const chatNavigation'), shell.indexOf('return `', shell.indexOf('const chatNavigation')));
  assert.match(navigation, /chat-sidebar-bottom-actions/);
  assert.match(navigation, /data-action="new-chat" aria-label="新对话"/);
  assert.match(navigation, /icon\(icons\.edit, '新对话'\)/);
  assert.doesNotMatch(navigation, /icons\.plus/);
});
