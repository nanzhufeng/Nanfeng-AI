import assert from 'node:assert/strict';
import test from 'node:test';
import { readFile } from 'node:fs/promises';
import { resolve } from 'node:path';

const root = resolve(import.meta.dirname, '..');
const shell = await readFile(resolve(root, 'src/chat-shell.mjs'), 'utf8');

test('FB-P6-042 keeps Desktop Settings exclusively in the sidebar while the header keeps mode and temporary actions', () => {
  const header = shell.slice(shell.indexOf('const header = `'), shell.indexOf('const main = pane'));
  const sidebar = shell.slice(shell.indexOf('const chatNavigation ='), shell.indexOf('return `<div class="app-shell'));
  assert.match(header, /data-action="show-chat"/);
  assert.match(header, /data-action="show-work"/);
  assert.match(header, /data-action="toggle-temporary-chat"/);
  assert.doesNotMatch(header, /data-action="show-settings"/);
  assert.match(sidebar, /data-action="show-settings"/);
});
