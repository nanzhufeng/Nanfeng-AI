import test from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import vm from 'node:vm';

test('WORK can reopen existing reminder plans and pending drafts without an empty permanent utility entry', async () => {
  const source = await readFile(new URL('../src/chat-shell.mjs', import.meta.url), 'utf8');
  const start = source.indexOf('function sidebarFunctions(');
  const end = source.indexOf('\nfunction ', start + 1);
  const context = vm.createContext({ icon: () => '', icons: {}, escapeHtml: String, workNavigation: () => 'work-navigation' });
  vm.runInContext(source.slice(start, end), context);
  context.args = { activeWorkMode: true, pane: 'work', reminders: { plans: [], drafts: [] } };
  assert.doesNotMatch(vm.runInContext('sidebarFunctions(args)', context), /data-action="show-reminders"/);
  for (const reminders of [{ plans: [{ planId: 'completed', status: 'COMPLETED' }], drafts: [] }, { plans: [], drafts: [{ status: 'PENDING_REVIEW' }] }, { plans: [], drafts: [{ status: 'FAILED' }] }]) {
    context.args.reminders = reminders;
    const html = vm.runInContext('sidebarFunctions(args)', context);
    assert.match(html, /data-action="show-reminders"/);
    assert.doesNotMatch(html, /data-action="show-transcription"/);
  }
});

test('entering WORK restores reminder projection before rendering its navigation', async () => {
  const source = await readFile(new URL('../src/app.mjs', import.meta.url), 'utf8');
  const start = source.indexOf('async function switchDataArea(');
  const end = source.indexOf('\nconst dialogInvoke', start);
  const previous = { dataArea: 'CHAT', runtimeInfo: { dataAreasReady: true } };
  const work = { dataArea: 'WORK', runtimeInfo: previous.runtimeInfo, current: { summary: { id: 'work' } }, localBackup: {}, cloudConversations: [], reminders: { plans: [], drafts: [] } };
  const restored = { plans: [{ planId: 'completed' }], drafts: [] };
  const context = vm.createContext({ state: previous, native: true, areaSwitchPending: false, dataAreaStates: new Map([['CHAT', previous], ['WORK', work]]), fullSearchResultCache: new Map(), fullSearchCacheScope: '', ordinaryChatRuntimeRefreshPending: null, composerDraftRouteGeneration: 0, cloudOpenAreaGeneration: 0, app: {}, localSettingsWrites: { flush: async () => {} },
    loadDesktopReminders: async owner => { assert.equal(owner, work); owner.reminders = restored; },
    render: () => { assert.equal(work.reminders, restored); },
    selectWorkspaceDefaultConversation: () => {},
  });
  for (const name of ['flushOrdinaryComposerDraft', 'stopComposerCamera', 'supersedeFullSearch', 'loadDesktopLocalBackupStatus', 'loadDesktopAppSettings', 'loadModelServiceSettings', 'loadDesktopHistoryKnowledge', 'refresh', 'loadDesktopAccountSync', 'restoreCachedCloudConversationList', 'loadOrdinaryComposerDraft', 'drainReminderNotificationActions']) context[name] = async () => {};
  vm.runInContext(source.slice(start, end), context);
  await vm.runInContext("switchDataArea('WORK','work')", context);
  assert.equal(work.reminders, restored);
});
