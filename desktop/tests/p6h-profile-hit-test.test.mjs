import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import test from 'node:test';

test('FB-P6-026 footer has one Settings route and the old profile overlay is absent', async () => {
  const css = await readFile(new URL('../src/chat-shell.css', import.meta.url), 'utf8');
  const shell = await readFile(new URL('../src/chat-shell.mjs', import.meta.url), 'utf8');
  const icons = await readFile(new URL('../src/icon-source.mjs', import.meta.url), 'utf8');
  assert.match(css, /\.chat-sidebar-footer\s*\{[^}]*z-index:\s*40[^}]*isolation:\s*isolate/s);
  assert.ok(shell.includes('data-action="show-settings"'));
  assert.ok(!shell.includes('chat-profile-menu'));
  assert.ok(icons.includes('Vendored Lucide icon nodes (ISC)'));
  assert.ok(!css.includes('.chat-profile-menu'));
});
