import test from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';

const source = await readFile(new URL('../src/app.mjs', import.meta.url), 'utf8');

test('chat source shortcut renders its source dialog and uses the shared globe glyph', () => {
  const chatRender = source.slice(source.indexOf('function render()'), source.indexOf('function refresh()', source.indexOf('function render()')));
  const sourceDialog = source.slice(source.indexOf("if (state.dialog?.kind === 'source-links')"), source.indexOf("if (state.dialog === 'import')"));

  assert.match(chatRender, /renderChatFirstShell\([\s\S]*?\)\}\$\{dialog\(\)\}/);
  assert.match(sourceDialog, /sharedIcon\(sharedIcons\.globe, '打开来源'\)/);
  assert.doesNotMatch(sourceDialog, /icon\(icons\.globe, '打开来源'\)/);
});
