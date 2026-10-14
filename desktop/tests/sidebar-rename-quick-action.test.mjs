import assert from 'node:assert/strict';
import fs from 'node:fs';
import test from 'node:test';

const shell = fs.readFileSync(new URL('../src/chat-shell.mjs', import.meta.url), 'utf8');
const app = fs.readFileSync(new URL('../src/app.mjs', import.meta.url), 'utf8');

test('sidebar rename shortcut opens the inline title editor', () => {
  const rows = shell.slice(shell.indexOf('function conversationRows'), shell.indexOf('export function conversationLocalDate'));
  assert.match(rows, /data-action="open-conversation-rename"/);
  assert.match(rows, /aria-label="重命名"/);
  assert.doesNotMatch(rows, /data-action="toggle-conversation-favorite"/);
  assert.match(app, /action === 'open-conversation-rename'/);
  assert.match(app, /startInlineConversationRename\(/);
  assert.doesNotMatch(app, /kind: 'conversation-rename'/);
});
