import { createConversationFindInputController } from '../src/conversation-find-interaction.mjs';
import assert from 'node:assert/strict';
import test from 'node:test';
import { readFileSync } from 'node:fs';
import vm from 'node:vm';

const source = readFileSync(new URL('../src/app.mjs', import.meta.url), 'utf8').replaceAll('\r\n', '\n');
const start = source.lastIndexOf("app.addEventListener('keydown', event => {",
  source.indexOf("event.key === 'Enter') {\n    if (searchImeTarget"));
const end = source.indexOf('\n});', start) + 4;
assert.ok(start >= 0 && end > start);
const keydownSource = source.slice(start, end);
const compositionSource = source.slice(source.indexOf('let composerImeTarget = null;'),
  source.indexOf('function composerHasFileTransfer('));
const inputSource = source.slice(source.indexOf("app.addEventListener('input', event => {\n  if (event.target.id === 'chat-composer')"),
  source.indexOf("app.addEventListener('change', event => {", source.indexOf("app.addEventListener('input', event => {\n  if (event.target.id === 'chat-composer')")));

test('search Enter selects an IME candidate without submitting, then normal Enter submits', () => {
  let keydown;
  let submitted = 0;
  const context = vm.createContext({ conversationFindInputController: createConversationFindInputController(),
    app: { addEventListener(type, listener) { if (type === 'keydown') keydown = listener; } },
    state: { searchHistoryOpen: false, searchPanel: true },
    searchImeTarget: null, searchImeEndedAtMs: Number.NEGATIVE_INFINITY,
    submitLocalSearch() { submitted += 1; },
  });
  vm.runInContext(keydownSource, context);
  const event = (isComposing, keyCode = 13) => ({
    target: { id: 'full-search-input' }, key: 'Enter', isComposing, keyCode,
    prevented: false, preventDefault() { this.prevented = true; },
  });
  const composing = event(true);
  keydown(composing);
  assert.equal(composing.prevented, false);
  assert.equal(submitted, 0);
  const legacyIme = event(false, 229);
  keydown(legacyIme);
  assert.equal(legacyIme.prevented, false);
  assert.equal(submitted, 0);
  const normal = event(false);
  keydown(normal);
  assert.equal(normal.prevented, true);
  assert.equal(submitted, 1);
});

test('full search waits for the complete IME candidate before scheduling or submitting', () => {
  const listeners = new Map();
  let scheduled = 0;
  let submitted = 0;
  const state = { chatSearch: '', searchPanel: true, searchHistory: [], searchHistoryManuallyOpened: false,
    searchHistoryOpen: false };
  const context = vm.createContext({ conversationFindInputController: createConversationFindInputController(),
    app: { addEventListener(type, listener) { listeners.set(type, listener); } }, state,
    window: { clearTimeout() {} }, fullSearchDebounce: null,
    supersedeFullSearch() {}, bestSearchHistoryMatch: () => null,
    scheduleFullSearch() { scheduled += 1; }, submitLocalSearch() { submitted += 1; },
  });
  vm.runInContext(compositionSource + inputSource + keydownSource, context);
  const target = { id: 'full-search-input', value: '南枫', closest: () => null };
  listeners.get('compositionstart')({ target });
  listeners.get('input')({ target, isComposing: true });
  assert.equal(scheduled, 0);
  listeners.get('keydown')({ target, key: 'Enter', isComposing: true, keyCode: 13, preventDefault() {} });
  assert.equal(submitted, 0);
  listeners.get('compositionend')({ target });
  assert.equal(state.chatSearch, '南枫');
  assert.equal(scheduled, 1);
  listeners.get('keydown')({ target, key: 'Enter', isComposing: false, keyCode: 13, preventDefault() {} });
  assert.equal(submitted, 0);
  vm.runInContext('searchImeEndedAtMs -= 200', context);
  listeners.get('keydown')({ target, key: 'Enter', isComposing: false, keyCode: 13, preventDefault() {} });
  assert.equal(submitted, 1);
});

test('a late composition end cannot copy the old area query into the new area', () => {
  const listeners = new Map();
  const chat = { chatSearch: 'CHAT 旧词', searchPanel: true };
  const work = { chatSearch: 'WORK 原词', searchPanel: true };
  let scheduled = 0;
  const context = vm.createContext({ conversationFindInputController: createConversationFindInputController(),
    app: { addEventListener(type, listener) { listeners.set(type, listener); } }, state: chat,
    window: { clearTimeout() {} }, fullSearchDebounce: null,
    supersedeFullSearch() {}, scheduleFullSearch() { scheduled += 1; },
  });
  vm.runInContext(compositionSource, context);
  const target = { id: 'full-search-input', value: 'CHAT 候选' };
  listeners.get('compositionstart')({ target });
  context.state = work;
  listeners.get('compositionend')({ target });
  assert.equal(work.chatSearch, 'WORK 原词');
  assert.equal(scheduled, 0);
});

test('a late shell paint keeps the focused composing search input until commit', () => {
  const renderStart = source.indexOf('function renderUnified() {');
  const renderBody = source.slice(renderStart, source.indexOf('  if (composerCameraOwner', renderStart));
  const state = { searchPanel: true };
  const input = { isConnected: true };
  const context = vm.createContext({ conversationFindInputController: createConversationFindInputController(), inlineTitleEditor: null, state, areaSwitchPending: false, searchImeOwner: state, searchImeTarget: input,
    isCurrentComposerImeRoute: () => false,
    document: { activeElement: input }, painted: 0 });
  vm.runInContext(`${renderBody} painted += 1; }`, context);
  vm.runInContext('renderUnified()', context);
  assert.equal(context.painted, 0);
  context.searchImeTarget = null;
  vm.runInContext('renderUnified()', context);
  assert.equal(context.painted, 1);
  context.searchImeTarget = input;
  state.dialog = { kind: 'confirmation' };
  vm.runInContext('renderUnified()', context);
  assert.equal(context.painted, 2, 'an explicit dialog must still paint');
});
