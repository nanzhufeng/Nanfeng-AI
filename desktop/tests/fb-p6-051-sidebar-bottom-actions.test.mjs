import assert from 'node:assert/strict';
import test from 'node:test';
import { readFile } from 'node:fs/promises';

const root = new URL('../', import.meta.url);
const [shell, css] = await Promise.all([
  readFile(new URL('src/chat-shell.mjs', root), 'utf8'),
  readFile(new URL('src/chat-shell.css', root), 'utf8'),
]);

test('FB-P6-051 keeps settings and new chat as independent bottom actions at opposite sides', () => {
  const footer = shell.slice(shell.indexOf('<footer class="chat-sidebar-footer">'), shell.indexOf('</footer>', shell.indexOf('<footer class="chat-sidebar-footer">')));
  assert.match(footer, /chat-sidebar-bottom-actions/);
  assert.match(footer, /class="chat-profile" data-action="show-settings" aria-label="设置"/);
  assert.match(footer, /class="chat-sidebar-function" data-action="new-chat" aria-label="新对话"/);
  assert.doesNotMatch(footer, /chat-sidebar-bottom-actions[^>]*chat-profile[^>]*chat-sidebar-function/);
  for (const token of ['justify-content: space-between', 'background: transparent', 'width: 44px', 'border-radius: 22px', 'box-shadow: 0 3px 10px', 'data-action="new-chat"] { flex: 0 0 auto; min-height: 44px; margin-left: auto', 'box-shadow: 0 6px 16px']) assert.ok(css.includes(token));
});

test('FB-P6-051 collapsed sidebar retains only a centered icon-only new-chat action', () => {
  for (const token of ['.chat-sidebar.rail-collapsed .chat-profile,', 'justify-content: center; padding-right: 0; padding-left: 0;', 'data-action="new-chat"] { flex: 0 0 44px; width: 44px; min-width: 44px; min-height: 44px; margin-left: 0; padding: 0 !important; justify-content: center; gap: 0; border-radius: 22px;']) assert.ok(css.includes(token));
});
