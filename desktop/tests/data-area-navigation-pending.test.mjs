import { createConversationFindInputController } from '../src/conversation-find-interaction.mjs';
import test from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import vm from 'node:vm';

test('readbacks during an area switch keep a status page until navigation can accept input', async () => {
  const source = await readFile(new URL('../src/app.mjs', import.meta.url), 'utf8');
  const start = source.indexOf('function renderUnified() {') + 'function renderUnified() {'.length;
  const end = source.indexOf('  // Async readbacks', start);
  const app = { innerHTML: '<button data-action="show-settings">设置</button>' };
  const context = vm.createContext({ conversationFindInputController: createConversationFindInputController(), state: { pane: 'chat' }, inlineTitleEditor: null, app, areaSwitchPending: true, isCurrentComposerImeRoute: () => false });
  vm.runInContext(`(function(){${source.slice(start, end)}})()`, context);
  assert.match(app.innerHTML, /role="status"/);
  assert.doesNotMatch(app.innerHTML, /data-action/);
});
