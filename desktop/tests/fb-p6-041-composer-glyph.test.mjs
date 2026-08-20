import assert from 'node:assert/strict';
import test from 'node:test';
import { readFile } from 'node:fs/promises';
import { resolve } from 'node:path';

const root = resolve(import.meta.dirname, '..');
const [shell, css, iconSource] = await Promise.all([
  readFile(resolve(root, 'src/chat-shell.mjs'), 'utf8'),
  readFile(resolve(root, 'src/chat-shell.css'), 'utf8'),
  readFile(resolve(root, 'src/icon-source.mjs'), 'utf8'),
]);

test('FB-P6-041 reduces only composer add and send glyphs while preserving Desktop controls', () => {
  assert.match(shell, /class="chat-composer-icon chat-composer-add"[^>]*data-action="toggle-composer-add"/);
  assert.match(shell, /class="chat-send chat-composer-icon"[^>]*data-action="save-local-message"/);
  assert.match(iconSource, /send: `\$\{p\('M3\.714/);
  assert.match(css, /\.chat-composer-icon \{[^}]*width: 40px;[^}]*min-height: 40px[^}]*border-radius: 50%/);
  assert.match(css, /\.chat-composer-add svg \{ width: 20\.8px; height: 20\.8px; \}/);
  assert.match(css, /\.chat-composer-actions \.chat-send::before \{[^}]*width: 30px; height: 30px; border-radius: 50%/);
  assert.match(css, /\.chat-composer-actions \.chat-send svg \{[^}]*width: 16px; height: 16px; transform: rotate\(-90deg\); transform-origin: center;/);
});
