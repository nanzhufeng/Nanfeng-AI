import test from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';

test('chat header edge fade masks the first generated text without receiving input', async () => {
  const css = await readFile(new URL('../src/chat-shell.css', import.meta.url), 'utf8');
  const topOverlayStart = css.indexOf('.app-shell.chat-first:not(.settings-mode) > .chat-main:not(.work-main):not(.connections-main)::before {');
  const topOverlay = css.slice(topOverlayStart, css.indexOf('\n', topOverlayStart));
  assert.match(topOverlay, /::before\s*\{\s*top:\s*0;\s*height:\s*min\(148px,\s*46%\);/);
  assert.match(topOverlay, /var\(--conversation-edge-color\)\s*100%/);
  assert.match(topOverlay, /var\(--conversation-edge-color\)\s*94%/);
  assert.match(css, /::before,\s*[\s\S]*?::after\s*\{[\s\S]*?pointer-events:\s*none;/);
});
