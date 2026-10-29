import test from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { resolve } from 'node:path';
import vm from 'node:vm';

const app = await readFile(resolve(import.meta.dirname, '../src/app.mjs'), 'utf8');
const start = app.indexOf('function showUnavailableReminderNotificationTarget(');
const end = app.indexOf('\nasync function handleReminderNotificationAction(', start);
assert.ok(start >= 0 && end > start, 'notification route source is present');
const routeSource = app.slice(start, end);
const actionEnd = app.indexOf('\nasync function drainReminderNotificationActions(', end);
assert.ok(actionEnd > end, 'notification action source is present');
const actionSource = app.slice(end, actionEnd);

test('notification for a deleted plan explains the no-op without opening another workspace', async () => {
  const state = {
    current: { summary: { id: 'workspace-a' } },
    selectedConversationId: 'conversation-a',
    reminders: { plans: [] },
    status: '',
  };
  const calls = [];
  const context = {
    state,
    reminderNotificationExtra: value => value,
    invoke: async command => {
      calls.push(command);
      if (command === 'resolve_desktop_reminder_notification_target') return null;
      throw new Error(`unexpected command: ${command}`);
    },
    render: () => { calls.push('render'); },
  };
  const route = vm.runInNewContext(`${routeSource}\nrouteToReminderPlan`, context);
  assert.equal(await route({ route: 'desktop-reminder-plan', planId: 'deleted-plan' }), false);
  assert.match(state.status, /已处理.*已不可用.*没有打开其他对象/);
  assert.equal(state.current.summary.id, 'workspace-a');
  assert.equal(state.selectedConversationId, 'conversation-a');
  assert.deepEqual(calls, ['resolve_desktop_reminder_notification_target', 'render']);
});

test('consumed or deleted notification click reports no target and does not navigate', async () => {
  const state = { dataArea: 'CHAT', status: '' };
  const calls = [];
  const context = {
    state,
    reminderNotificationAction: () => ({ clickId: 'run-deleted', dataArea: 'CHAT' }),
    invoke: async command => { calls.push(command); return null; },
    render: () => { calls.push('render'); },
  };
  const action = vm.runInNewContext(`${routeSource}\n${actionSource}\nhandleReminderNotificationAction`, context);
  assert.equal(await action({}), false);
  assert.match(state.status, /已处理.*已不可用.*没有打开其他对象/);
  assert.deepEqual(calls, ['consume_desktop_reminder_notification_action', 'render']);
});

test('notification route loads the target workspace presentation without marking its conversation read', async () => {
  const calls = [];
  const state = {
    current: { summary: { id: 'workspace-a' } },
    selectedConversationId: 'conversation-a',
    composerDraft: 'old draft',
    favoriteConversationIds: new Set(['conversation-a']),
    reminders: { plans: [] },
    history: { canUndo: true },
  };
  const resolved = { route: 'desktop-reminder-plan', planId: 'plan-b', workspaceId: 'workspace-b', conversationId: 'conversation-b' };
  const context = {
    state,
    reminderNotificationExtra: value => value,
    invoke: async command => {
      calls.push(command);
      if (command === 'resolve_desktop_reminder_notification_target') return resolved;
      if (command === 'read_desktop_workspace') return { summary: { id: 'workspace-b' } };
      if (command === 'activate_desktop_reminder_notification_target') return true;
      throw new Error(`unexpected command: ${command}`);
    },
    loadDesktopReminders: async owner => { owner.reminders = { plans: [resolved] }; },
    flushOrdinaryComposerDraft: async () => { calls.push('flush-draft'); },
    resetComposerPresentation: () => { state.composerDraft = ''; },
    applyConversationReadState: projection => { state.readState = projection; },
    refresh: async options => {
      calls.push('refresh');
      assert.equal(options.observeSelected, false);
      assert.equal(state.current.summary.id, 'workspace-b');
      assert.equal(state.favoriteConversationIds.size, 0);
      assert.equal(state.history.canUndo, false);
      state.favoriteConversationIds = new Set(['conversation-b']);
    },
    loadOrdinaryComposerDraft: async () => { calls.push('load-draft'); state.composerDraft = 'target draft'; },
    render: () => {},
    queueMicrotask: () => {},
    document: { querySelector: () => null },
    CSS: { escape: value => value },
  };
  const route = vm.runInNewContext(`${routeSource}\nrouteToReminderPlan`, context);
  assert.equal(await route(resolved), true);
  assert.equal(state.current.summary.id, 'workspace-b');
  assert.equal(state.selectedConversationId, 'conversation-b');
  assert.equal(state.composerDraft, 'target draft');
  assert.deepEqual([...state.favoriteConversationIds], ['conversation-b']);
  assert.equal(state.selectedReminderPlanId, 'plan-b');
  assert.equal(state.pane, 'reminders', 'notification must open the plan list, not reminder preference toggles');
  assert.ok(calls.indexOf('flush-draft') < calls.indexOf('activate_desktop_reminder_notification_target'));
  assert.ok(calls.indexOf('refresh') < calls.indexOf('load-draft'));
});
