import test from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';

test('chat header decoration never overlays the first generated text', async () => {
  const css = await readFile(new URL('../src/chat-shell.css', import.meta.url), 'utf8');
  const topOverlayStart = css.indexOf('.app-shell.chat-first:not(.settings-mode) > .chat-main:not(.work-main):not(.connections-main)::before {');
  const topOverlay = css.slice(topOverlayStart, css.indexOf('\n', topOverlayStart));
  assert.match(topOverlay, /::before\s*\{\s*display:\s*none;/);
});
