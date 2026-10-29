import test from 'node:test';
import assert from 'node:assert/strict';
import vm from 'node:vm';
import { readFileSync } from 'node:fs';
import { invokeForDataArea, invokeForAuthorizedDataAreaOperation } from '../src/data-area-invoke.mjs';
const source = readFileSync(new URL('../src/app.mjs', import.meta.url), 'utf8');
const block = source.slice(source.indexOf('async function confirmConversationPermanentDelete('), source.indexOf("\napp.addEventListener('contextmenu'", source.indexOf('async function confirmConversationPermanentDelete(')));
function harness({ single = false, failSecond = false, scope = 'recycle' } = {}) {
  let release, entered;
  const gate = new Promise(r => { release = r; });
  const first = new Promise(r => { entered = r; });
  const calls = [], favorites = [], refreshes = [];
  const owner = { dataArea: 'CHAT', current: { summary: { id: 'origin' }, exchange: { conversations: [
    { id: 'one', revision: 3, deleted: scope === 'recycle', archived: scope !== 'recycle' }, { id: 'two', revision: 8, deleted: scope === 'recycle', archived: scope !== 'recycle' }
  ] } }, dialog: { kind: single ? 'conversation-permanent-delete' : 'conversation-bulk-cleanup', id: 'one' }, selectedConversationId: null };
  const bridge = { invoke: async (command, args, options) => {
    calls.push({ command, args: JSON.parse(JSON.stringify(args)), area: options.headers['x-nanfeng-data-area'] });
    if (calls.length === 1) { entered(); await gate; }
    if (failSecond && calls.length === 2) throw Error('REVISION_CONFLICT');
    return { cleanupPending: false };
  } };
  const context = vm.createContext({ state: owner, native: true, tauriBridge: bridge, invokeForAuthorizedDataAreaOperation,
    invoke: (command, args, selected = context.state) => invokeForDataArea(bridge, command, args, selected, () => context.state),
    render() {}, intent: () => 'cleanup', refresh: async () => { refreshes.push(context.state.current.summary.id); },
    loadDesktopPrivacyInventory: async () => {}, removeFavoriteConversation: id => favorites.push([context.state.current.summary.id, id]) });
  vm.runInContext(block, context);
  return { owner, context, calls, favorites, refreshes, release, first,
    run: () => single ? context.confirmConversationPermanentDelete({ dataset: { id: 'one', revision: '3' } }) : context.confirmConversationBulkCleanup(scope) };
}
test('bulk cleanup keeps the confirmed source for every item after changing chat history source', async () => {
  const h = harness(); const work = h.run(); await h.first;
  h.owner.current = { summary: { id: 'other' }, exchange: { conversations: [{ id: 'two', revision: 99, deleted: true }] } };
  const nextDialog = { kind: 'other-dialog' }; h.owner.dialog = nextDialog; h.owner.status = 'other status';
  h.release(); await work;
  assert.deepEqual(h.calls.map(c => [c.args.args.workspaceId, c.args.args.conversationId, c.args.args.expectedRevision]), [['origin','one',3], ['origin','two',8]]);
  assert.equal(h.owner.dialog, nextDialog); assert.equal(h.owner.status, 'other status');
  assert.deepEqual(h.favorites, []); assert.deepEqual(h.refreshes, []);
});
test('bulk cleanup finishes its authorized original area without mutating the new area', async () => {
  const h = harness(); const work = h.run(); await h.first;
  const next = { dataArea: 'WORK', current: { summary: { id: 'origin' } }, dialog: { kind: 'new' }, status: 'new status' };
  h.context.state = next; h.release(); await work;
  assert.equal(h.calls.length, 2); assert.deepEqual(h.calls.map(c => c.area), ['CHAT','CHAT']);
  assert.equal(next.dialog.kind, 'new'); assert.equal(next.status, 'new status'); assert.equal(next.error, undefined);
});
test('single cleanup preserves a newer dialog when its receipt arrives', async () => {
  const h = harness({ single: true }); const work = h.run(); await h.first;
  const nextDialog = { kind: 'new-dialog' }; h.owner.dialog = nextDialog;
  h.release(); await work; assert.equal(h.owner.dialog, nextDialog);
});
test('current-source bulk cleanup reports partial completion and preserves exact revisions', async () => {
  const h = harness({ failSecond: true }); const work = h.run(); await h.first; h.release(); await work;
  assert.equal(h.owner.dialog, null); assert.match(h.owner.status, /1 个回收站会话/); assert.match(h.owner.status, /1 个未完成/);
  assert.match(h.owner.error, /REVISION_CONFLICT/); assert.deepEqual(h.calls.map(c => c.args.args.expectedRevision), [3,8]);
});
test('a pending bulk confirmation does not launch a duplicate batch', async () => {
  const h = harness(); const work = h.run(); await h.first; await h.run(); assert.equal(h.calls.length, 1);
  h.release(); await work; assert.equal(h.calls.length, 2);
});

test('archived bulk recycle keeps original object IDs and source after navigation', async () => {
  const h = harness({ scope: 'archived' }); const work = h.run(); await h.first;
  h.owner.current = { summary: { id: 'next-source' }, exchange: { conversations: [] } }; h.owner.dialog = { kind: 'new-dialog' };
  h.release(); await work;
  assert.deepEqual(h.calls.map(c => [c.command, c.args.args.workspaceId, c.args.args.objectId, c.args.args.action]),
    [['mutate_desktop_domain','origin','one','softDelete'],['mutate_desktop_domain','origin','two','softDelete']]);
  assert.equal(h.owner.dialog.kind, 'new-dialog'); assert.deepEqual(h.favorites, []);
});
