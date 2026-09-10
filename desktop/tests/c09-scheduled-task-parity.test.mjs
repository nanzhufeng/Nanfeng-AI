import test from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { resolve } from 'node:path';
import { renderDesktopRemindersPage } from '../src/desktop-reminders-page.mjs';

const emptyProjection = { drafts: [], plans: [] };
const activePlan = {
  planId: 'c09-active',
  title: 'C09 安全计划',
  instruction: '只读公开状态，不外发通知。',
  scheduleKind: 'DAILY',
  anchorLocal: '2099-09-03T09:30',
  timezoneId: 'Asia/Shanghai',
  missedPolicy: 'SKIP',
  status: 'ACTIVE',
  nextRunAtMs: 4092091800000,
  latestResult: null,
  lastSafeErrorCode: null,
  lastChargeMicros: null,
  updatedAtMs: 1,
};

test('C09 list keeps the current Android hierarchy and an explicit browser fixture boundary', () => {
  const html = renderDesktopRemindersPage({
    projection: emptyProjection,
    notificationEnabled: false,
    notificationPermission: 'default',
    native: false,
    previewInteractive: true,
    evidenceLabel: '只读交互样本 · 不写入 Desktop SQLite',
  });
  assert.match(html, />已计划</);
  assert.match(html, />开启结果通知</);
  assert.match(html, /暂无计划。可以添加新闻、公告或价格等长期跟踪任务。/);
  assert.match(html, /只读交互样本/);
  assert.doesNotMatch(html, /任务与监控|计划结果通知|还没有定时任务/);
  assert.match(html, /data-action="create-manual-reminder-draft"(?![^>]*disabled)/);
});

test('C09 create uses the same full-page owner for prompt, time and once or recurring schedule', () => {
  const html = renderDesktopRemindersPage({
    projection: emptyProjection,
    native: false,
    previewInteractive: true,
    editor: { mode: 'create', item: { title: '新的监控', instruction: '', scheduleKind: 'DAILY', anchorLocal: '2099-09-03T09:30', timezoneId: 'Asia/Shanghai', missedPolicy: 'RUN_ONCE' } },
  });
  assert.match(html, />新建计划</);
  assert.match(html, /仅发送任务名称和监控要求，不发送完整对话或附件。/);
  assert.match(html, />计划名称（20字内）</);
  assert.match(html, />监控要求</);
  assert.match(html, />一次</);
  assert.match(html, />重复</);
  assert.match(html, />每小时</);
  assert.match(html, />每天</);
  assert.match(html, />每周</);
  assert.match(html, /type="datetime-local"/);
  assert.match(html, />取消</);
  assert.match(html, />保存并开始</);
  assert.doesNotMatch(html, /class="scrim"/);
});

test('C09 plan cards expose persisted active, paused, result and safe failure facts', () => {
  const html = renderDesktopRemindersPage({
    projection: {
      drafts: [],
      plans: [
        activePlan,
        { ...activePlan, planId: 'c09-paused', status: 'PAUSED', title: '已暂停计划', nextRunAtMs: null },
        { ...activePlan, planId: 'c09-failed', status: 'FAILED', title: '失败保留计划', lastSafeErrorCode: 'PROVIDER_NOT_CONFIGURED' },
        { ...activePlan, planId: 'c09-result', status: 'COMPLETED', title: '已完成计划', latestResult: '已保存的本机结果。', nextRunAtMs: null },
      ],
    },
    native: true,
  });
  assert.match(html, /监控 · 每天/);
  assert.match(html, /已暂停 · 每天/);
  assert.match(html, /已暂停，恢复后重新计算下次时间。/);
  assert.match(html, /最近一次未完成：PROVIDER_NOT_CONFIGURED/);
  assert.match(html, /最近结果/);
  assert.match(html, /已保存的本机结果。/);
  assert.match(html, /data-action="edit-reminder-plan"/);
  assert.match(html, /data-action="set-reminder-paused"/);
  assert.match(html, /data-action="delete-reminder-plan"/);
});

test('C09 delete remains a confirmation and edit cancellation returns to the plan list', async () => {
  const source = await readFile(resolve('src/app.mjs'), 'utf8');
  const page = await readFile(resolve('src/desktop-reminders-page.mjs'), 'utf8');
  assert.match(source, /kind: 'reminder-editor'/);
  assert.match(source, /mode: 'edit'/);
  assert.match(page, /data-action=\"cancel-reminder-editor\"/);
  assert.match(source, /kind: 'reminder-delete'/);
  assert.match(source, /confirm-delete-reminder-plan/);
  assert.match(source, /state\.reminderEditor = null/);
  assert.match(source, /discardOnCancel: true/);
  assert.match(source, /reject_desktop_reminder_draft/);
});

test('C09 production mutations stay on the Rust owner while browser QA stays fixture-only', async () => {
  const source = await readFile(resolve('src/app.mjs'), 'utf8');
  const rust = await readFile(resolve('src-tauri/src/desktop_reminders_v1.rs'), 'utf8');
  assert.match(source, /c09ReminderPreview/);
  assert.match(source, /Web 只读交互样本不会写入 Desktop SQLite/);
  assert.match(source, /confirm_desktop_reminder_draft/);
  assert.match(source, /update_desktop_reminder_plan/);
  assert.match(source, /set_desktop_reminder_plan_paused/);
  assert.match(source, /delete_desktop_reminder_plan/);
  assert.match(rust, /desktop_reminder_plans_v1/);
  assert.match(rust, /expected_updated_at_ms/);
  assert.match(rust, /status IN \('ACTIVE','PAUSED'\)/);
});
