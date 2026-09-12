import assert from 'node:assert/strict';
import test from 'node:test';
import { readFile } from 'node:fs/promises';
import { resolve } from 'node:path';
import { renderChatFirstShell } from '../src/chat-shell.mjs';

const root = resolve(import.meta.dirname, '..');
const css = await readFile(resolve(root, 'src/chat-shell.css'), 'utf8');
const data = { summary: { id: 'fb-p6-078' }, exchange: { conversations: [] } };

test('FB-P6-078 keeps the approved two-row Desktop Composer hierarchy', () => {
  const html = renderChatFirstShell({ data, native: true, pane: 'chat', selectedConversationId: null, status: '', error: '', connection: {} });
  assert.match(html, /composer-add-anchor[\s\S]*id="chat-composer"[\s\S]*chat-composer-primary-actions/);
  assert.match(css, /\.chat-composer-controls \{ display: grid; min-height: 48px; grid-template-columns: 48px minmax\(0, 1fr\) auto;/);
  assert.match(css, /\.chat-composer-controls \{[^}]*grid-template-rows: auto 40px;/);
  assert.match(css, /\.chat-composer-controls #chat-composer \{ grid-column: 1 \/ -1; grid-row: 1;/);
  assert.match(css, /\.chat-composer-controls \.composer-add-anchor \{ grid-column: 1; grid-row: 2;/);
});
