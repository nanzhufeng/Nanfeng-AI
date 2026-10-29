import { createConversationFindInputController } from '../src/conversation-find-interaction.mjs';
import assert from 'node:assert/strict';
import test from 'node:test';
import { readFileSync } from 'node:fs';
import vm from 'node:vm';
const source = readFileSync(new URL('../src/app.mjs', import.meta.url), 'utf8').replaceAll('\r\n', '\n');
const composition = source.slice(source.indexOf('let composerImeTarget = null;'), source.indexOf('function composerHasFileTransfer('));
const start = source.indexOf('function renderUnified() {');
const paint = source.slice(start, source.indexOf('  if (composerCameraOwner', start)) + ' painted += 1; }';
test('recipient and streaming paints preserve composing input, then flush on commit', () => {
  const listeners = new Map();
  const state = { pane: 'chat', selectedConversationId: 'chat-1', current: { summary: { id: 'workspace-1' } } };
  const input = { id: 'chat-composer', isConnected: true };
  const context = vm.createContext({ conversationFindInputController: createConversationFindInputController(), inlineTitleEditor: null, state, areaSwitchPending: false, app: { addEventListener(type, listener) { listeners.set(type, listener); } },
    document: { activeElement: input }, painted: 0, updateOrdinaryEgressDisclosure() {}, queueMicrotask: callback => callback(),
    render() { vm.runInContext('renderUnified()', context); } });
  vm.runInContext(composition + paint, context);
  listeners.get('compositionstart')({ target: input });
  vm.runInContext('renderUnified()', context);
  assert.equal(context.painted, 0, 'late preview must not replace composing input');
  listeners.get('compositionend')({ target: input });
  assert.equal(context.painted, 1, 'committed input flushes latest state');
});
test('explicit navigation may paint and late composition end does not repaint another route', () => {
  const listeners = new Map();
  const state = { pane: 'chat', selectedConversationId: 'chat-1', current: { summary: { id: 'workspace-1' } } };
  const input = { id: 'chat-composer', isConnected: true };
  const context = vm.createContext({ conversationFindInputController: createConversationFindInputController(), inlineTitleEditor: null, state, areaSwitchPending: false, app: { addEventListener(type, listener) { listeners.set(type, listener); } },
    document: { activeElement: input }, painted: 0, updateOrdinaryEgressDisclosure() {}, queueMicrotask: callback => callback(),
    render() { vm.runInContext('renderUnified()', context); } });
  vm.runInContext(composition + paint, context);
  listeners.get('compositionstart')({ target: input });
  state.selectedConversationId = 'chat-2';
  vm.runInContext('renderUnified()', context);
  assert.equal(context.painted, 1);
  listeners.get('compositionend')({ target: input });
  assert.equal(context.painted, 1);
});
