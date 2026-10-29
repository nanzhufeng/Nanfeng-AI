import test from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';

const source = await readFile(new URL('../src/app.mjs', import.meta.url), 'utf8');

test('chat source shortcut renders its source dialog and uses the shared globe glyph', () => {
  const start = source.indexOf('function renderUnified()');
  const end = source.indexOf('function cameraCaptureDialog', start);
  assert.ok(start >= 0 && end > start, 'the active render owner must be present');
  const chatRender = source.slice(start, end);
  const sourceDialog = source.slice(source.indexOf("if (state.dialog?.kind === 'source-links')"), source.indexOf("if (state.dialog === 'import')"));

  assert.match(chatRender, /const nextShellHtml = renderChatFirstShell\(/);
  assert.match(chatRender, /app\.innerHTML = nextShellHtml/);
  assert.match(chatRender, /const modal = [^;]*dialog\(\)/);
  assert.match(chatRender, /insertAdjacentHTML\('beforeend', modal\)/);
  assert.match(sourceDialog, /sharedIcon\(sharedIcons\.globe, '打开来源'\)/);
  assert.doesNotMatch(sourceDialog, /icon\(icons\.globe, '打开来源'\)/);
});
