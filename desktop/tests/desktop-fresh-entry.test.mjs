import { retainNewerConversationSelection } from '../src/composer-draft-recovery.mjs';
import test from 'node:test';
import assert from 'node:assert/strict';
import vm from 'node:vm';
import {resolveConversation} from '../src/chat-shell.mjs';
import { readFile } from 'node:fs/promises';

const source = (await readFile(new URL('../src/app.mjs', import.meta.url), 'utf8')).replaceAll('\r\n', '\n');
const start = source.indexOf('async function refresh() {');
assert.ok(start >= 0);
const refresh = source.slice(start, source.indexOf('async function runP6eTemporaryMaintenanceAcceptance()', start));
const retained = { summary: { id: 'retained' }, exchange: { conversations: [{ id: 'pinned', pinned: true }, { id: 'last' }] } };
function harness(options = {}) {
  const { workspaces = [], projection = null } = options;
  const entry = Object.hasOwn(options, 'entry') ? options.entry : null;
  const calls = [];
  let paints = 0;
  const state = { dataArea: 'CHAT', current: null, selectedConversationId: null, entryResolved: false, runtimeInfo: {}, pane: 'chat' };
  const context = vm.createContext({ retainNewerConversationSelection, state, native: true, desktopEntryRouteGeneration: 0, cloudOpenAreaGeneration: 0, desktopWorkspaceRefreshGeneration: 0,
    desktopAppEntry: { read: () => entry },
    loadMarkdownKnowledgeTask: async () => {}, loadJsonKnowledgeTask: async () => {}, pdfKnowledge: { load: async () => {} }, webKnowledge: { load: async () => {} },
    resolveConversation,
    reloadFavoriteConversationIds() {}, loadConversationReadState: async () => {}, workspaceUiEnabled: () => false,
    loadTranscriptionState: async () => ({ tasks: [] }), recomputeConversationFind() {}, render() { paints++; },
    invoke: async (command, args) => {
      calls.push({ command, args });
      if (command === 'list_desktop_workspaces') return workspaces;
      if (command === 'read_desktop_workspace') return projection;
      if (command === 'read_desktop_p6g_catalog') return [{ id: 'available-model' }];
      if (command === 'read_desktop_p6g_global_default') return { modelId: null };
      if (command === 'read_p6e_temporary_maintenance_acceptance_status') return { enabled: false };
      return null;
    },
  });
  vm.runInContext(refresh, context);
  return { state, calls, get paints() { return paints; }, refresh: () => context.refresh() };
}

for (const entry of [null, undefined]) test(`empty native entry ${String(entry)} loads global metadata without workspace-scoped reads`, async () => {
  const h = harness({ entry });
  await h.refresh();
  assert.equal(h.state.current, null);
  assert.equal(h.state.selectedConversationId, null);
  assert.equal(h.state.entryResolved, true);
  assert.equal(h.paints, 1);
  assert.equal(h.state.p6gCatalog[0].id, 'available-model');
  assert.equal(h.state.p6gGlobalDefault.modelId, null);
  for (const name of ['read_desktop_workspace', 'read_desktop_p6g_selection', 'read_latest_chatgpt_import_task']) assert.ok(!h.calls.some(call => call.command === name));
});

test('a retained entry whose workspace is absent still opens a new conversation and loads global defaults', async () => {
  const h = harness({ entry: { workspaceId: 'removed', conversationId: 'last' } });
  await h.refresh();
  assert.equal(h.state.selectedConversationId, null);
  assert.equal(h.state.entryResolved, true);
  assert.ok(h.calls.some(call => call.command === 'read_desktop_p6g_global_default'));
});

test('existing source without an entry stays on a new conversation rather than opening the pinned first item', async () => {
  const h = harness({ workspaces: [retained.summary], projection: retained });
  await h.refresh();
  assert.equal(h.state.current.summary.id, 'retained');
  assert.equal(h.state.selectedConversationId, null);
  assert.equal(h.state.newConversationDraftWorkspaceId, 'retained');
});

test('a valid retained entry still restores its exact conversation', async () => {
  const h = harness({ entry: { workspaceId: 'retained', conversationId: 'last' }, workspaces: [retained.summary], projection: retained });
  await h.refresh();
  assert.equal(h.state.selectedConversationId, 'last');
  assert.ok(h.calls.some(call => call.command === 'read_desktop_p6g_selection' && call.args.conversationId === 'last'));
});

for(const disposition of ['absent','deleted','archived'])test(`a retained ${disposition} conversation opens a new chat instead of the pinned first row`,async()=>{
 const projection={summary:{id:'retained'},exchange:{conversations:[{id:'pinned',pinned:true},...(disposition==='absent'?[]:[{id:'last',deleted:disposition==='deleted',archived:disposition==='archived'}])]}};
 const h=harness({entry:{workspaceId:'retained',conversationId:'last'},workspaces:[projection.summary],projection});await h.refresh();assert.equal(h.state.selectedConversationId,null);assert.equal(h.state.newConversationDraftWorkspaceId,'retained');assert.ok(!h.calls.some(x=>x.command==='read_desktop_p6g_selection'));assert.equal(h.state.entryResolved,true);
});
test('cold entry selects its exact workspace even when a different workspace is first in the list',async()=>{
 const h=harness({entry:{workspaceId:'retained',conversationId:'last'},workspaces:[{id:'first-other'},retained.summary],projection:retained});await h.refresh();assert.equal(h.state.selectedConversationId,'last');assert.equal(h.calls.find(x=>x.command==='read_desktop_workspace').args.workspaceId,'retained');
});
