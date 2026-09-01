import { icon, icons } from './icon-source.mjs';

const escapeHtml = value => String(value ?? '').replace(
  /[&<>"']/g,
  char => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#039;' })[char],
);

const scheduleLabel = item => ({ ONCE: '一次', HOURLY: '每小时', DAILY: '每天', WEEKLY: '每周' })[item.scheduleKind] || item.scheduleKind;
const statusLabel = value => ({ ACTIVE: '运行中', RUNNING: '执行中', PAUSED: '已暂停', COMPLETED: '已完成', FAILED: '失败', UNKNOWN: '结果未知', PENDING_REVIEW: '待确认', NOT_ELIGIBLE: '不符合提醒条件', REJECTED: '已拒绝' })[value] || value;

function planCard(item) {
  return `<article class="desktop-reminder-card" data-reminder-plan-id="${escapeHtml(item.planId)}">
    <header><div><strong>${escapeHtml(item.title)}</strong><small>${escapeHtml(statusLabel(item.status))} · ${escapeHtml(scheduleLabel(item))}</small></div><span>${item.lastChargeMicros == null ? '费用未报告' : `$${(item.lastChargeMicros / 1_000_000).toFixed(6)}`}</span></header>
    <p>${escapeHtml(item.instruction)}</p>
    <small>下次执行：${item.nextRunAtMs ? escapeHtml(new Date(item.nextRunAtMs).toLocaleString('zh-CN')) : '无'} · ${escapeHtml(item.timezoneId || '本机时区')}</small>
    <div>${['ACTIVE', 'PAUSED'].includes(item.status) ? `<button data-action="edit-reminder-plan" data-id="${escapeHtml(item.planId)}">编辑</button>` : ''}${item.status === 'ACTIVE' || item.status === 'RUNNING' ? `<button data-action="set-reminder-paused" data-id="${escapeHtml(item.planId)}" data-paused="true">暂停</button>` : item.status === 'PAUSED' ? `<button data-action="set-reminder-paused" data-id="${escapeHtml(item.planId)}" data-paused="false">恢复</button>` : ''}${['FAILED', 'UNKNOWN'].includes(item.status) ? `<button data-action="retry-reminder-plan" data-id="${escapeHtml(item.planId)}">重试</button>` : ''}<button class="danger" data-action="delete-reminder-plan" data-id="${escapeHtml(item.planId)}">删除</button></div>
  </article>`;
}

function draftCard(item) {
  return `<article class="desktop-reminder-card draft"><header><div><strong>${escapeHtml(item.title || '提醒草案')}</strong><small>${escapeHtml(statusLabel(item.status))}</small></div></header><p>${escapeHtml(item.instruction || '模型尚未产生可审阅内容。')}</p><div>${item.status === 'PENDING_REVIEW' ? `<button class="primary" data-action="review-reminder-draft" data-id="${escapeHtml(item.draftId)}">审阅编辑</button><button data-action="reject-reminder-draft" data-id="${escapeHtml(item.draftId)}">拒绝</button>` : `<button data-action="retry-reminder-draft" data-id="${escapeHtml(item.draftId)}">重试</button>`}</div></article>`;
}

export function renderDesktopRemindersPage({ projection = { drafts: [], plans: [] }, notificationEnabled = false, notificationPermission = 'default', native = false } = {}) {
  const plans = projection?.plans || [];
  const drafts = (projection?.drafts || []).filter(item => ['PENDING_REVIEW', 'FAILED', 'UNKNOWN'].includes(item.status));
  const permissionReady = ['granted', 'legacy'].includes(notificationPermission);
  const notificationCopy = notificationEnabled
    ? permissionReady ? '系统通知已开启，计划完成后会发送简短通知。' : '计划会继续执行；请授权系统通知以便及时收到结果。'
    : '计划会照常运行，结果只保存在本机任务列表。';
  return `<section class="desktop-reminders-page" aria-label="定时任务">
    <header class="desktop-work-page-header"><button data-action="show-chat" aria-label="关闭定时任务">${icon(icons.close, '关闭')}</button><div><p>任务与监控</p><h1>已计划</h1></div><span></span></header>
    <section class="desktop-reminder-notification"><span>${icon(icons.bell, '通知')}</span><div><strong>计划结果通知</strong><p>${escapeHtml(notificationCopy)}</p></div>${notificationEnabled && !permissionReady ? `<button data-action="request-reminder-notification-permission" ${native ? '' : 'disabled'}>授权</button>` : ''}</section>
    <div class="desktop-reminder-content">${drafts.length || plans.length ? `<div class="desktop-reminder-grid">${drafts.map(draftCard).join('')}${plans.map(planCard).join('')}</div>` : `<div class="desktop-reminder-empty">${icon(icons.clock, '暂无定时任务')}<h2>还没有定时任务</h2><p>创建计划后，可在这里查看下次执行时间、状态和本机结果。</p></div>`}</div>
    <footer><button class="primary" data-action="create-manual-reminder-draft" ${native ? '' : 'disabled'}>${icon(icons.plus, '添加计划')}<span>添加计划</span></button></footer>
  </section>`;
}
