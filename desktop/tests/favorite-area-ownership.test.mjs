import test from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import vm from 'node:vm';

const source = await readFile(new URL('../src/app.mjs', import.meta.url), 'utf8');
const start = source.indexOf('const favoriteReadGenerations = new WeakMap();') >= 0
  ? source.indexOf('const favoriteReadGenerations = new WeakMap();')
  : source.indexOf('async function toggleFavoriteConversation(');
const end = source.indexOf('\napplyAppearance();', start);
assert.ok(start >= 0 && end > start);
const functions = source.slice(start, end);

function deferred() {
  let resolve;
  let reject;
  const promise = new Promise((done, fail) => { resolve = done; reject = fail; });
  return { promise, resolve, reject };
}

test('a startup favorite read cannot erase a newer successful favorite', async () => {
  const read = deferred();
  const owner = { current: { summary: { id: 'workspace' } }, favoriteConversationIds: new Set(), status: '', error: '' };
  const reloadStart = source.indexOf('function reloadFavoriteConversationIds(');
  const reloadEnd = source.indexOf('\nfunction currentConversation(', reloadStart);
  const context = { state: owner, native: true, render() {}, parityPreferences: {},
    invoke: command => command === 'read_desktop_favorite_conversation_ids' ? read.promise : Promise.resolve(['new-favorite']) };
  vm.runInNewContext(`${functions}\n${source.slice(reloadStart, reloadEnd)}\nglobalThis.reload = reloadFavoriteConversationIds; globalThis.toggle = toggleFavoriteConversation;`, context);
  context.reload();
  await context.toggle('new-favorite');
  read.resolve([]);
  await new Promise(resolveTick => setImmediate(resolveTick));
  assert.deepEqual([...owner.favoriteConversationIds], ['new-favorite']);
});

test('late favorite response cannot change a different data area', async () => {
  const pending = deferred();
  const chat = { current: { summary: { id: 'chat-workspace' } }, favoriteConversationIds: new Set(), status: '', error: '', contextMenu: 'chat-menu' };
  const work = { current: { summary: { id: 'work-workspace' } }, favoriteConversationIds: new Set(['work-favorite']), status: '', error: '', contextMenu: 'work-menu' };
  const context = { state: chat, native: true, invoke: () => pending.promise, render: () => {}, parityPreferences: {} };
  vm.runInNewContext(`${functions}\nglobalThis.toggleFavoriteConversation = toggleFavoriteConversation;`, context);
  const saving = context.toggleFavoriteConversation('chat-favorite');
  context.state = work;
  pending.resolve(['chat-favorite']);
  await saving;
  assert.deepEqual([...work.favoriteConversationIds], ['work-favorite']);
  assert.equal(work.status, '');
  assert.equal(work.error, '');
  assert.equal(work.contextMenu, 'work-menu');
});

test('late archive auto-unfavorite response cannot change the next workspace', async () => {
  const pending = deferred();
  const first = { current: { summary: { id: 'first' } }, favoriteConversationIds: new Set(['favorite']), status: '', error: '' };
  const second = { current: { summary: { id: 'second' } }, favoriteConversationIds: new Set(['second-favorite']), status: '', error: '' };
  const context = { state: first, native: true, invoke: () => pending.promise, render: () => {}, parityPreferences: {} };
  vm.runInNewContext(`${functions}\nglobalThis.removeFavoriteConversation = removeFavoriteConversation;`, context);
  context.removeFavoriteConversation('favorite');
  context.state = second;
  pending.resolve([]);
  await new Promise(resolveTick => setImmediate(resolveTick));
  assert.deepEqual([...second.favoriteConversationIds], ['second-favorite']);
  assert.equal(second.error, '');
});

test('late cloud-list workspace restore cannot replace a newer navigation', async () => {
  const restoreStart = source.indexOf('async function restoreCloudSidebarWorkspace(');
  const restoreEnd = source.indexOf('\nfunction batchConversationKey(', restoreStart);
  assert.ok(restoreStart >= 0 && restoreEnd > restoreStart);
  const pending = deferred();
  const first = { current: { summary: { id: 'cloud-action' } }, selectedConversationId: null, pane: 'chat', history: {}, favoriteConversationIds: new Set() };
  const second = { current: { summary: { id: 'new-area' } }, selectedConversationId: 'new-conversation', pane: 'work', history: {}, favoriteConversationIds: new Set(['new-favorite']) };
  const context = {
    state: first, native: true,
    invoke: command => command === 'read_desktop_workspace' ? pending.promise
      : command === 'read_desktop_favorite_conversation_ids' ? Promise.resolve([]) : Promise.resolve({}),
    resolveConversation: () => null,
    loadConversationReadState: async () => {},
  };
  vm.runInNewContext(`${source.slice(restoreStart, restoreEnd)}\nglobalThis.restoreCloudSidebarWorkspace = restoreCloudSidebarWorkspace;`, context);
  const restoring = context.restoreCloudSidebarWorkspace({ workspaceId: 'original', selectedConversationId: 'old-conversation' });
  context.state = second;
  pending.resolve({ summary: { id: 'original' } });
  await restoring;
  assert.equal(second.current.summary.id, 'new-area');
  assert.equal(second.selectedConversationId, 'new-conversation');
  assert.deepEqual([...second.favoriteConversationIds], ['new-favorite']);
});

test('late cloud action workspace read cannot navigate over a newer local route', async () => {
  const routeStart = source.indexOf('async function ensureConversationActionWorkspace(');
  const routeEnd = source.indexOf('\n// Cloud-list actions', routeStart);
  assert.ok(routeStart >= 0 && routeEnd > routeStart);
  const pending = deferred();
  const current = { summary: { id: 'local-workspace' } };
  const owner = { current, selectedConversationId: 'first', pane: 'chat', history: {} };
  const context = {
    state: owner, native: true,
    invoke: command => command === 'read_desktop_workspace' ? pending.promise : Promise.resolve({}),
    reloadFavoriteConversationIds: () => {}, loadConversationReadState: async () => {},
  };
  vm.runInNewContext(`${source.slice(routeStart, routeEnd)}\nglobalThis.ensureConversationActionWorkspace = ensureConversationActionWorkspace;`, context);
  const loading = context.ensureConversationActionWorkspace({ dataset: { workspaceId: 'cloud-workspace' } });
  owner.selectedConversationId = 'newer';
  pending.resolve({ summary: { id: 'cloud-workspace' } });
  assert.equal(await loading, false);
  assert.equal(owner.current, current);
  assert.equal(owner.selectedConversationId, 'newer');
});
