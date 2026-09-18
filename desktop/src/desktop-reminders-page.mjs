import { icon, icons } from './icon-source.mjs';

const escapeHtml = value => String(value ?? '').replace(
  /[&<>"']/g,
  char => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#039;' })[char],
);

const scheduleLabel = item => ({ ONCE: '一次', HOURLY: '每小时', DAILY: '每天', WEEKLY: '每周' })[item.scheduleKind] || item.scheduleKind;
const statusLabel = value => ({ ACTIVE: '监控', RUNNING: '执行中', PAUSED: '已暂停', COMPLETED: '已完成', FAILED: '失败', UNKNOWN: '结果未知', PENDING_REVIEW: '待确认', NOT_ELIGIBLE: '不符合提醒条件', REJECTED: '已拒绝' })[value] || value;
const nextRunCopy = item => item.status === 'PAUSED'
  ? '已暂停，恢复后重新计算下次时间。'
  : item.nextRunAtMs ? `下次执行：${new Date(item.nextRunAtMs).toLocaleString('zh-CN')}` : '暂无下次执行时间。';

function planCard(item) {
  const canEdit = ['ACTIVE', 'PAUSED'].includes(item.status);
  return `<article class="desktop-reminder-card" data-reminder-plan-id="${escapeHtml(item.planId)}">
    <header><div><strong>${escapeHtml(item.title)}</strong><small>${escapeHtml(statusLabel(item.status))} · ${escapeHtml(scheduleLabel(item))}</small></div>${item.lastChargeMicros == null ? '' : `<span>$${(item.lastChargeMicros / 1_000_000).toFixed(6)}</span>`}</header>
    <p>${escapeHtml(item.instruction)}</p>
    ${item.latestResult ? `<section class="desktop-reminder-result"><small>最近结果</small><p>${escapeHtml(item.latestResult)}</p></section>` : ''}
    ${item.lastSafeErrorCode ? `<p class="desktop-reminder-error">最近一次未完成：${escapeHtml(item.lastSafeErrorCode)}</p>` : ''}
    <small class="desktop-reminder-next">${escapeHtml(nextRunCopy(item))} · ${escapeHtml(item.timezoneId || '本机时区')}</small>
    <div class="desktop-reminder-actions">${canEdit ? `<button data-action="edit-reminder-plan" data-id="${escapeHtml(item.planId)}">编辑</button>` : ''}${item.status === 'ACTIVE' || item.status === 'RUNNING' ? `<button data-action="set-reminder-paused" data-id="${escapeHtml(item.planId)}" data-paused="true">暂停</button>` : item.status === 'PAUSED' ? `<button data-action="set-reminder-paused" data-id="${escapeHtml(item.planId)}" data-paused="false">恢复</button>` : ''}${['FAILED', 'UNKNOWN'].includes(item.status) ? `<button data-action="retry-reminder-plan" data-id="${escapeHtml(item.planId)}">重试</button>` : ''}<button class="danger" data-action="delete-reminder-plan" data-id="${escapeHtml(item.planId)}">删除</button></div>
  </article>`;
}

function draftCard(item) {
  return `<article class="desktop-reminder-card draft"><header><div><strong>${escapeHtml(item.title || '提醒草案')}</strong><small>${escapeHtml(statusLabel(item.status))}</small></div></header><p>${escapeHtml(item.instruction || '模型尚未产生可审阅内容。')}</p><div class="desktop-reminder-actions">${item.status === 'PENDING_REVIEW' ? `<button class="primary" data-action="review-reminder-draft" data-id="${escapeHtml(item.draftId)}">审阅编辑</button><button data-action="reject-reminder-draft" data-id="${escapeHtml(item.draftId)}">拒绝</button>` : `<button data-action="retry-reminder-draft" data-id="${escapeHtml(item.draftId)}">重试</button>`}</div></article>`;
}

function pageHeader(title, action = 'show-chat', label = '关闭定时任务', { embedded = false } = {}) {
  if (embedded) return `<header class="desktop-utility-panel-header"><h1>${escapeHtml(title)}</h1></header>`;
  return `<header class="desktop-work-page-header"><span></span><h1>${escapeHtml(title)}</h1><button data-action="${action}" aria-label="${escapeHtml(label)}">${icon(icons.close, '关闭')}</button></header>`;
}

function scheduleButton(value, label, current) {
  return `<button type="button" class="${current === value ? 'selected' : ''}" data-action="set-reminder-cadence" data-value="${value}" aria-pressed="${current === value}">${label}</button>`;
}

function renderEditor(editor) {
  const item = editor.item || {};
  const scheduleKind = item.scheduleKind || 'DAILY';
  const once = scheduleKind === 'ONCE';
  const saveAction = editor.mode === 'edit' ? 'confirm-edit-reminder-plan' : 'confirm-reminder-draft';
  return `<section class="desktop-reminders-page desktop-reminder-editor" aria-label="${editor.mode === 'edit' ? '编辑计划' : '新建计划'}">
    ${pageHeader(editor.mode === 'edit' ? '编辑计划' : '新建计划', 'cancel-reminder-editor', '取消并返回已计划')}
    <form class="desktop-reminder-editor-form" data-reminder-editor-mode="${escapeHtml(editor.mode)}">
      <p class="desktop-reminder-privacy">仅发送任务名称和监控要求，不发送完整对话或附件。</p>
      <label><span>计划名称（20字内）</span><input id="reminder-title" maxlength="20" value="${escapeHtml(item.title || '新的监控')}" placeholder="例如：检查项目状态"></label>
      <label><span>监控要求</span><textarea id="reminder-instruction" maxlength="8000" rows="6" placeholder="写清要检查什么、何时算完成">${escapeHtml(item.instruction || '')}</textarea></label>
      <fieldset><legend>运行安排</legend><div class="desktop-reminder-mode"><button type="button" class="${once ? 'selected' : ''}" data-action="set-reminder-schedule-mode" data-mode="ONCE" aria-pressed="${once}">一次</button><button type="button" class="${once ? '' : 'selected'}" data-action="set-reminder-schedule-mode" data-mode="RECURRING" aria-pressed="${!once}">重复</button></div></fieldset>
      <label><span>运行时间</span><input id="reminder-anchor-local" type="datetime-local" value="${escapeHtml(item.anchorLocal || '')}"></label>
      <fieldset class="desktop-reminder-cadence" ${once ? 'hidden' : ''}><legend>重复频率</legend><div>${scheduleButton('HOURLY', '每小时', scheduleKind)}${scheduleButton('DAILY', '每天', scheduleKind)}${scheduleButton('WEEKLY', '每周', scheduleKind)}</div></fieldset>
      <div class="desktop-reminder-advanced"><label><span>时区</span><input id="reminder-timezone" maxlength="64" value="${escapeHtml(item.timezoneId || '')}" placeholder="Asia/Shanghai"></label><label><span>错过计划时间后</span><select id="reminder-missed-policy"><option value="RUN_ONCE" ${item.missedPolicy === 'RUN_ONCE' ? 'selected' : ''}>恢复后执行一次</option><option value="SKIP" ${item.missedPolicy === 'SKIP' ? 'selected' : ''}>跳过本次</option></select></label></div>
      <input id="reminder-schedule-kind" type="hidden" value="${escapeHtml(scheduleKind)}">
      <div class="desktop-reminder-editor-actions"><button type="button" data-action="cancel-reminder-editor">取消</button><button type="button" class="primary" data-action="${saveAction}">${editor.mode === 'edit' ? '保存修改' : '保存并开始'}</button></div>
    </form>
  </section>`;
}

export function renderDesktopRemindersPage({ loading = false, projection = { drafts: [], plans: [] }, notificationEnabled = false, notificationPermission = 'default', native = false, previewInteractive = false, evidenceLabel = '', editor = null, embedded = false } = {}) {
  if (editor) return renderEditor(editor);
  const plans = projection?.plans || [];
  const drafts = (projection?.drafts || []).filter(item => ['PENDING_REVIEW', 'FAILED', 'UNKNOWN'].includes(item.status));
  const permissionReady = ['granted', 'legacy'].includes(notificationPermission);
  const notificationHint = notificationEnabled
    ? permissionReady ? '结果完成后可发送不含正文的系统通知。' : '计划仍会执行；系统通知尚未授权。'
    : '计划会照常执行，结果只保存在本机。';
  const createEnabled = native || previewInteractive;
  return `<section class="desktop-reminders-page" aria-label="定时任务" aria-busy="${loading}">
    ${pageHeader('已计划', 'show-chat', '关闭定时任务', { embedded })}
    <main class="desktop-reminder-content">
      ${loading ? '<p role="status">正在读取定时任务…</p>' : ''}
      ${evidenceLabel ? `<p class="desktop-reminder-evidence">${escapeHtml(evidenceLabel)}</p>` : ''}
      <button class="desktop-reminder-notification" data-action="request-reminder-notification-permission" ${native ? '' : 'disabled'}><span>${icon(icons.bell, '通知')}</span><span><strong>开启结果通知</strong><small>${escapeHtml(notificationHint)}</small></span></button>
      ${drafts.length || plans.length ? `<div class="desktop-reminder-grid">${drafts.map(draftCard).join('')}${plans.map(planCard).join('')}</div>` : loading ? '' : `<p class="desktop-reminder-empty">暂无计划。可以添加新闻、公告或价格等长期跟踪任务。</p>`}
      <button class="primary desktop-reminder-add" data-action="create-manual-reminder-draft" ${createEnabled ? '' : 'disabled'}>${icon(icons.plus, '添加计划')}<span>添加计划</span></button>
    </main>
  </section>`;
}
