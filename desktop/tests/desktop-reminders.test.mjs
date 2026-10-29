import test from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import vm from 'node:vm';

const desktopRoot = fileURLToPath(new URL('..', import.meta.url));
import { hasExplicitReminderIntent } from '../src/chat-shell.mjs';
import { renderAndroidSettingsShell } from '../src/android-settings-shell.mjs';
import { reminderNotificationAction, reminderNotificationExtra } from '../src/reminder-notification-routing.mjs';

test('conversation suggestion requires an explicit future reminder or monitoring intent', () => {
  assert.equal(hasExplicitReminderIntent('每天九点提醒我检查构建结果'), true);
  assert.equal(hasExplicitReminderIntent('持续监控这个公开页面，有变化告诉我'), true);
  assert.equal(hasExplicitReminderIntent('谢谢，这个回答很好'), false);
  assert.equal(hasExplicitReminderIntent('你觉得我需要提醒吗'), false);
});

test('reminder settings retain notification controls without duplicate plan management', () => {
  const html = renderAndroidSettingsShell({
    page: 'reminders',
    native: true,
    settings: { monitorNotifications: true, reminderSuggestions: true, unreadIndicators: false },
    capabilities: { monitorNotifications: true, reminderSuggestions: true, unreadIndicators: false },
    reminderNotificationPermission: 'default',
    reminderNotificationBridge: { supported: true, initialized: true, listenerReady: true, safeCode: 'READY', pendingActionCount: 0 },
    backgroundRuntime: { desiredEnabled: true, installed: true, safeCode: 'INSTALLED' },
    reminders: {
      drafts: [{ draftId: 'draft-safe', status: 'PENDING_REVIEW', title: '检查状态', instruction: '读取公开状态页', scheduleKind: 'DAILY', timezoneId: 'Asia/Shanghai' }],
      plans: [{ planId: 'plan-safe', status: 'ACTIVE', title: '每日检查', instruction: '读取公开状态页', scheduleKind: 'DAILY', timezoneId: 'Asia/Shanghai', nextRunAtMs: 1788339600000, lastChargeMicros: null }],
      diagnostics: [],
    },
  });
  assert.match(html, /系统通知权限/);
  assert.doesNotMatch(html, /计划与监控|新建草案|审阅编辑|每日检查|费用未报告/);
  assert.doesNotMatch(html, /data-reminder-plan-id|data-action="edit-reminder-plan"/);
  assert.doesNotMatch(html, /通知点击桥：/);
  assert.doesNotMatch(html, /退出后后台唤醒：/);
});

test('reminder settings disclose when background scheduling was requested but registration failed', () => {
  const base = { page: 'reminders', native: true,
    capabilities: { monitorNotifications: true, reminderSuggestions: true, unreadIndicators: true } };
  const failed = renderAndroidSettingsShell({ ...base,
    backgroundRuntime: { desiredEnabled: true, installed: false, safeCode: 'INSTALL_FAILED' } });
  assert.match(failed, /系统后台任务未注册/);
  assert.match(failed, /关闭应用后提醒不会按计划执行/);
  const installed = renderAndroidSettingsShell({ ...base,
    backgroundRuntime: { desiredEnabled: true, installed: true, safeCode: 'INSTALLED' } });
  assert.match(installed, /系统后台任务已注册/);
  assert.doesNotMatch(installed, /系统后台任务未注册/);
});

test('confirming a reminder reports persistence separately from background registration', async () => {
  const source = (await readFile(resolve(desktopRoot, 'src/app.mjs'), 'utf8')).replaceAll('\r\n', '\n');
  const start = source.indexOf("app.addEventListener('click', event => {\n  const target = event.target.closest?.('[data-action]');\n  const action = target?.dataset.action;\n  const actions = ['generate-reminder-draft'");
  assert.ok(start >= 0);
  const end = source.indexOf('}, true);', start) + '}, true);'.length;
  assert.ok(end > start);
  for (const installed of [false, true]) {
    let handler;
    let resolveRendered;
    const rendered = new Promise(resolveRender => { resolveRendered = resolveRender; });
    const state = { reminderEditor: { item: { draftId: 'draft-1' } }, reminders: {}, status: '', error: '',
      backgroundRuntime: { desiredEnabled: true, installed, safeCode: installed ? 'INSTALLED' : 'INSTALL_FAILED' } };
    vm.runInNewContext(source.slice(start, end), {
      app: { addEventListener(_type, callback) { handler = callback; } },
      state, native: true, render() { resolveRendered(); },
      reminderFormValues: () => ({ title: '合成提醒', scheduleKind: 'ONCE' }),
      invoke: async () => ({ drafts: [], plans: [{ planId: 'plan-1' }] }),
      loadDesktopBackgroundRuntime: async () => state.backgroundRuntime,
    });
    handler({ target: { closest: () => ({ dataset: { action: 'confirm-reminder-draft' } }) },
      preventDefault() {}, stopImmediatePropagation() {} });
    await rendered;
    assert.match(state.status, /提醒计划已确认并持久化/);
    assert.match(state.status, installed ? /系统后台任务已注册/ : /系统后台任务未注册；关闭应用后不会按计划执行/);
    assert.equal(state.error, '');
  }
});

test('late reminder result after area switch cannot write an error into the new area', async () => {
  const source = (await readFile(resolve(desktopRoot, 'src/app.mjs'), 'utf8')).replaceAll('\r\n', '\n');
  const start = source.indexOf("app.addEventListener('click', event => {\n  const target = event.target.closest?.('[data-action]');\n  const action = target?.dataset.action;\n  const actions = ['generate-reminder-draft'");
  assert.ok(start >= 0);
  const end = source.indexOf('}, true);', start) + '}, true);'.length;
  let handler;
  let rejectRead;
  let renderCount = 0;
  const chat = { dataArea: 'CHAT', current: { summary: { id: 'chat-workspace' } }, reminders: { drafts: [], plans: [] }, status: '', error: '' };
  const work = { dataArea: 'WORK', reminders: { drafts: [], plans: [] }, status: '', error: '' };
  const context = vm.createContext({
    app: { addEventListener(_type, callback) { handler = callback; } },
    state: chat, native: true, c09ReminderPreview: false, desktopEntryRouteGeneration: 0, cloudOpenAreaGeneration: 0,
    render() { renderCount += 1; },
    invoke: () => new Promise((_resolve, reject) => { rejectRead = reject; }),
  });
  vm.runInContext(source.slice(start, end), context);
  handler({ target: { closest: () => ({ dataset: { action: 'create-manual-reminder-draft' } }) },
    preventDefault() {}, stopImmediatePropagation() {} });
  assert.equal(typeof rejectRead, 'function');
  context.state = work;
  rejectRead(Object.assign(new Error('原数据区的操作结果已留在原区'), { code: 'STALE_DATA_AREA' }));
  await new Promise(resolveTick => setImmediate(resolveTick));
  assert.equal(work.error, '');
  assert.equal(chat.error, '');
  assert.equal(renderCount, 0);
});

test('desktop reminder bridge keeps explicit confirmation, narrow notification permissions and safe click routing', async () => {
  const appSource = await readFile(resolve(desktopRoot, 'src/app.mjs'), 'utf8');
  const buildSource = await readFile(resolve(desktopRoot, 'scripts/build.mjs'), 'utf8');
  const capability = await readFile(resolve(desktopRoot, 'src-tauri/capabilities/default.json'), 'utf8');
  const rust = await readFile(resolve(desktopRoot, 'src-tauri/src/desktop_reminders_v1.rs'), 'utf8');
  const nativeBridge = await readFile(resolve(desktopRoot, 'src-tauri/src/desktop_reminder_notification_v1.rs'), 'utf8');
  const backgroundOwner = await readFile(resolve(desktopRoot, 'src-tauri/src/desktop_background_runtime_v1.rs'), 'utf8');
  const runtimeOwner = await readFile(resolve(desktopRoot, 'src-tauri/src/lib.rs'), 'utf8');
  assert.match(appSource, /confirm_desktop_reminder_draft/);
  assert.match(appSource, /update_desktop_reminder_plan/);
  assert.match(appSource, /send_pending_desktop_reminder_notification/);
  assert.doesNotMatch(appSource, /plugin:notification\|notify/);
  assert.match(appSource, /desktop-reminder-plan/);
  assert.match(appSource, /PERMISSION_NOT_GRANTED/);
  assert.match(appSource, /desktop-reminder-notification-action-v1/);
  assert.match(appSource, /drain_desktop_reminder_notification_actions/);
  assert.match(appSource, /resolve_desktop_reminder_notification_target/);
  assert.match(appSource, /Reminder native notification action listener unavailable/);
  assert.match(buildSource, /reminder-notification-routing\.mjs/);
  assert.doesNotMatch(capability, /notification:default/);
  assert.doesNotMatch(capability, /notification:allow-is-permission-granted/);
  assert.doesNotMatch(capability, /notification:allow-request-permission/);
  assert.doesNotMatch(capability, /notification:allow-register-listener/);
  assert.doesNotMatch(capability, /notification:allow-notify/);
  assert.match(nativeBridge, /UNUserNotificationCenterDelegate/);
  assert.match(nativeBridge, /didReceiveNotificationResponse/);
  assert.match(nativeBridge, /NSUserNotificationCenterDelegate/);
  assert.match(nativeBridge, /NotificationSendOutcome::Failed => legacy_send/);
  assert.match(nativeBridge, /deny_unknown_fields/);
  assert.match(nativeBridge, /MAX_HANDLED_CLICK_IDS/);
  assert.match(rust, /status != "PENDING_REVIEW"/);
  assert.match(rust, /expected_updated_at_ms/);
  assert.match(rust, /只有运行中或已暂停的计划可以编辑/);
  assert.match(rust, /CLOUD_RESTORED_PAUSED/);
  assert.match(appSource, /read_desktop_background_runtime/);
  assert.match(backgroundOwner, /--nanfeng-background-cycle-v1/);
  assert.match(backgroundOwner, /StartInterval/);
  assert.match(backgroundOwner, /localhost/);
  assert.doesNotMatch(backgroundOwner, /ProgramArguments[\s\S]{0,300}conversation_id/);
  assert.match(runtimeOwner, /try_lock_exclusive/);
  assert.match(runtimeOwner, /nanfeng-exit-generation-drain/);
  const exitOwner = await readFile(resolve(desktopRoot, 'src-tauri/src/desktop_exit_drain_v1.rs'), 'utf8');
  assert.match(exitOwner, /CANCEL_AFTER[^;]*Duration::from_secs\(30\s*\*\s*60\)/);
  assert.match(runtimeOwner, /desktop_exit_drain_v1::CANCEL_AFTER/);
  assert.match(runtimeOwner, /let _ = reconcile_desktop_background_runtime\(&chat_app\)/);
});

test('notification action payloads resolve to the exact local reminder route without trusting other fields', () => {
  const expected = { route: 'desktop-reminder-plan', planId: 'plan-7', workspaceId: 'workspace-2', conversationId: 'conversation-4' };
  assert.deepEqual(reminderNotificationExtra({ notification: { extra: expected } }), expected);
  assert.deepEqual(reminderNotificationExtra({ extra: JSON.stringify(expected) }), expected);
  assert.deepEqual(reminderNotificationExtra({ notification: { extra: { ...expected, planId: 7, leaked: 'ignored' } } }), { ...expected, planId: '' });
  assert.deepEqual(reminderNotificationExtra({ notification: { extra: 'not-json' } }), { route: '', planId: '', workspaceId: '', conversationId: '' });
  assert.deepEqual(reminderNotificationAction({ payload: { clickId: 'run-7', ...expected, leaked: 'ignored' } }), { clickId: 'run-7', dataArea: 'CHAT', ...expected });
  assert.deepEqual(reminderNotificationAction({ payload: { clickId: 'run-8', dataArea: 'WORK', ...expected } }), { clickId: 'run-8', dataArea: 'WORK', ...expected });
  assert.deepEqual(reminderNotificationAction({ payload: { clickId: 7, ...expected } }), { clickId: '', dataArea: 'CHAT', ...expected });
});
