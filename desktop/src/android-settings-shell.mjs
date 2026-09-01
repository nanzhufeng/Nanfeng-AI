import { icon, icons } from './icon-source.mjs';
import { APPEARANCE_MODES, CONVERSATION_TONES, FONT_SIZES, THEME_COLORS, normalizeAppearance, normalizeProductSettings } from './desktop-parity-preferences.mjs';

const escapeHtml = value => String(value ?? '').replace(
  /[&<>"']/g,
  char => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#039;' })[char],
);

const modelPreset = (id, displayName, description, family) => ({ id, displayName, description, family, chatSelectable: true });
export const MODEL_SERVICE_PREVIEW_SETTINGS = Object.freeze([
  { providerId: 'OPENROUTER', providerDisplayName: 'OpenRouter', enabled: false, presetId: 'GPT_5_6_TERRA', presetDisplayName: 'GPT-5.6 Terra', credentialStored: false, revision: 0, presets: [
    modelPreset('CLAUDE_FABLE_5', 'Claude Fable 5', '应对最棘手的复杂任务。', 'Anthropic · OpenRouter'),
    modelPreset('CLAUDE_OPUS_5', 'Claude Opus 5', '适合复杂任务与深度推理。', 'Anthropic · OpenRouter'),
    modelPreset('CLAUDE_SONNET_5', 'Claude Sonnet 5', '高效处理日常工作。', 'Anthropic · OpenRouter'),
    modelPreset('CLAUDE_HAIKU_4_5', 'Claude Haiku 4.5', '快速获得简洁答案。', 'Anthropic · OpenRouter'),
    modelPreset('GPT_5_6_SOL', 'GPT-5.6 Sol', '前沿能力，适合专业复杂任务。', 'OpenAI · OpenRouter'),
    modelPreset('GPT_5_6_TERRA', 'GPT-5.6 Terra', '能力与成本更均衡。', 'OpenAI · OpenRouter'),
    modelPreset('GPT_5_6_LUNA', 'GPT-5.6 Luna', '适合高频、轻量任务。', 'OpenAI · OpenRouter'),
    modelPreset('GEMINI_3_7_FLASH', 'Gemini 3.7 Flash', '快速处理文字、图片和文件任务。', 'Google · OpenRouter'),
    modelPreset('KIMI_K3', 'Kimi K3', '复杂分析 · Agent · 长上下文', 'Moonshot AI · OpenRouter'),
  ], nonChatCapabilities: [] },
  { providerId: 'DEEPSEEK', providerDisplayName: 'DeepSeek 官方直连', enabled: false, presetId: 'DEEPSEEK_V4_PRO', presetDisplayName: 'DeepSeek V4 Pro', credentialStored: false, revision: 0, presets: [
    modelPreset('DEEPSEEK_V4_PRO', 'DeepSeek V4 Pro', '适合深度推理与专业分析。', 'DeepSeek · 官方直连'),
    modelPreset('DEEPSEEK_V4_FLASH', 'DeepSeek V4 Flash', '适合快速问答与高频文本任务。', 'DeepSeek · 官方直连'),
  ], nonChatCapabilities: [] },
  { providerId: 'ZHIPU', providerDisplayName: '智谱 BigModel 官方直连', enabled: false, presetId: 'GLM_5_3_FLASH', presetDisplayName: 'GLM-5.3 Flash', credentialStored: false, revision: 0, presets: [
    modelPreset('GLM_5_3', 'GLM-5.3', '适合深度推理、复杂分析与 Agent 任务。', '智谱 · 官方直连'),
    modelPreset('GLM_5_3_FLASH', 'GLM-5.3 Flash', '智谱官方直连的快速文本任务。', '智谱 · 官方直连'),
  ], nonChatCapabilities: [{ id: 'GLM_OCR', displayName: 'GLM-OCR', description: '图片与 PDF 转 Markdown · 使用同一智谱 API Key', family: '仅在左侧栏“南枫转写”中调用，不加入聊天模型选择。', chatSelectable: false }] },
  { providerId: 'QWEN', providerDisplayName: 'Qwen 官方直连', enabled: false, presetId: 'QWEN_3_7_PLUS', presetDisplayName: 'Qwen3.7-Plus', credentialStored: false, revision: 0, presets: [
    modelPreset('QWEN_3_7_PLUS', 'Qwen3.7-Plus', '日常问答与轻量多媒体任务。', 'Qwen · 官方直连'),
    modelPreset('QWEN_3_8_MAX', 'Qwen3.8-Max', '适合深度分析与复杂任务。', 'Qwen · 官方直连'),
    modelPreset('QWEN_3_6_FLASH', 'Qwen3.6 Flash', '适合大批量知识整理与快速检索。', 'Qwen · 官方直连'),
  ], nonChatCapabilities: [{ id: 'QWEN_3_ASR', displayName: 'Qwen3-ASR Flash', description: '音频与视频转文字 · 使用同一 Qwen API Key', family: '仅在左侧栏“南枫转写”语音任务中调用，不加入聊天模型选择。', chatSelectable: false }] },
]);

const pageTitles = Object.freeze({
  home: '设置', personalization: '个性化', model: '模型与联网', 'model-configuration': '模型设置',
  'conversation-cost': '费用与用量', 'context-selections': '上下文记录', diagnostics: '运行诊断',
  reminders: '提醒', conversations: '对话管理', favorites: '收藏', archived: '已归档', recycle: '回收站',
  appearance: '外观', account: 'Google 账号与同步', data: '导入与导出', privacy: '本机数据',
  'memory-overview': '记忆摘要', 'json-import-results': 'JSON 导入结果', 'zip-import-results': 'ZIP 导入结果',
  about: '关于', workspace: '工作区', development: '开发与诊断',
});

const row = ({ glyph, title, page, action, picker, value = '', dot = '', muted = false, selected = false, disabled = false, data = '' }) => `<button class="android-settings-row${muted ? ' muted' : ''}${selected ? ' selected' : ''}" data-action="${action || 'open-settings-page'}" ${page ? `data-page="${page}"` : ''} ${picker ? `data-picker="${picker}"` : ''} ${data} ${disabled ? 'disabled' : ''} ${selected ? 'aria-current="page"' : ''}><span class="android-settings-row-icon">${icon(glyph, title)}</span><span>${escapeHtml(title)}</span>${dot ? `<i style="--settings-dot:${escapeHtml(dot)}"></i>` : ''}${value ? `<small>${escapeHtml(value)}</small>` : ''}</button>`;
const divider = '<div class="android-settings-divider" aria-hidden="true"></div>';
const group = (title, rows) => `<section class="android-settings-group"><h2>${escapeHtml(title)}</h2><div class="android-settings-card">${rows.join(divider)}</div></section>`;

function header(page, personalizationDirty, capabilities) {
  const nested = ['model-configuration', 'conversation-cost', 'context-selections', 'diagnostics', 'favorites', 'archived', 'recycle', 'memory-overview', 'json-import-results', 'zip-import-results'].includes(page);
  const personalizationAvailable = Boolean(capabilities?.ordinaryChatPersonalization);
  return `<header class="android-settings-header">${nested ? `<button data-action="settings-back" aria-label="返回上一级">${icon(icons.chevronLeft, '返回')}</button>` : `<button class="android-settings-mobile-home" data-action="show-settings-home" aria-label="返回设置菜单">${icon(icons.chevronLeft, '返回设置菜单')}</button>`}<h1>${escapeHtml(pageTitles[page] || '设置')}</h1>${page === 'personalization' ? `<button class="android-settings-save" data-action="save-personalization" ${personalizationDirty && personalizationAvailable ? '' : 'disabled'} aria-label="保存个性化设置">${icon(icons.check, '保存')}</button>` : '<span></span>'}</header>`;
}

function home({ appearance, page, capabilities }) {
  const color = THEME_COLORS.find(item => item.id === appearance.themeColor) || THEME_COLORS[0];
  const mode = APPEARANCE_MODES.find(item => item.id === appearance.mode)?.label || '系统（默认）';
  const font = FONT_SIZES.find(item => item.id === appearance.fontSize)?.label || '标准';
  return `<div class="android-settings-home">
    ${group('对话', [
      row({ glyph: icons.account, title: '个性化', page: 'personalization', selected: page === 'personalization' }),
      row({ glyph: icons.hub, title: '模型与联网', page: 'model', selected: page === 'model' || ['model-configuration','conversation-cost','context-selections','diagnostics'].includes(page) }),
      row({ glyph: icons.bell, title: '提醒', page: 'reminders', selected: page === 'reminders' }),
      row({ glyph: icons.conversation, title: '对话管理', page: 'conversations', selected: page === 'conversations' || ['favorites','archived','recycle'].includes(page) }),
    ])}
    ${group('外观', [
      row({ glyph: icons.palette, title: '外观', action: 'open-settings-picker', picker: 'mode', value: mode }),
      row({ glyph: icons.type, title: '字体大小', action: 'open-settings-picker', picker: 'fontSize', value: font }),
      row({ glyph: icons.palette, title: '主题色', action: 'open-settings-picker', picker: 'themeColor', value: color.label, dot: color.accent }),
    ])}
    ${group('数据管理', [
      row({ glyph: icons.account, title: 'Google 账号与同步', page: 'account', muted: !capabilities.googleAccountSync, selected: page === 'account' }),
      row({ glyph: icons.import, title: '导入与导出', page: 'data', muted: true, selected: page === 'data' }),
      row({ glyph: icons.data, title: '本机数据', page: 'privacy', muted: true, selected: page === 'privacy' }),
      row({ glyph: icons.info, title: '关于', page: 'about', muted: true, selected: page === 'about' }),
    ])}
    ${group('工作区', [
      row({ glyph: icons.folder, title: '项目与知识', page: 'workspace', selected: page === 'workspace' }),
      row({ glyph: icons.settings, title: '开发与诊断', page: 'development', selected: page === 'development' }),
    ])}
  </div>`;
}

const switchRow = (title, summary, key, checked, disabled = false) => `<div class="android-settings-switch-row"><div><strong>${escapeHtml(title)}</strong>${summary ? `<p>${escapeHtml(summary)}</p>` : ''}</div><button class="android-settings-switch" data-action="toggle-product-setting" data-key="${key}" aria-pressed="${checked}" role="switch" ${disabled ? 'disabled' : ''}><span></span></button></div>`;
const actionRow = ({ title, action = 'open-settings-page', page = '', disabled = false }) => `<button class="android-settings-action-row" data-action="${action}" ${page ? `data-page="${page}"` : ''} ${disabled ? 'disabled' : ''}><span>${escapeHtml(title)}</span>${page ? icon(icons.chevronRight, `进入${title}`) : ''}</button>`;
const actionGroup = (title, rows) => `<section class="android-settings-data-group"><h2>${escapeHtml(title)}</h2><div class="android-settings-card">${rows.join(divider)}</div></section>`;

function localBackupGroup(context) {
  const backup = context.localBackup || {};
  const preflight = backup.preflight;
  const disabled = !context.native || backup.working || backup.restartRequired;
  const actions = actionGroup('本机备份与恢复', [
    actionRow({ title: backup.working ? '正在处理…' : '备份', action: 'export-local-backup', disabled }),
    actionRow({ title: backup.working ? '正在处理…' : '恢复', action: 'import-local-backup', disabled }),
  ]);
  if (backup.restartRequired) return `${actions}<section class="android-settings-card android-settings-status-card"><strong>恢复已完成</strong><p>为避免旧 SQLite 与页面引用，现请手动完全退出并重新打开 App；不会自动继续任何任务。</p></section>`;
  if (!preflight) return `${actions}${backup.notice ? `<p class="android-settings-helper success" role="status">${escapeHtml(backup.notice)}</p>` : ''}${backup.error ? `<p class="android-settings-helper error" role="alert">${escapeHtml(backup.error)}</p>` : ''}`;
  const counts = Object.entries(preflight.tableCounts || {}).sort(([left], [right]) => left.localeCompare(right)).map(([table, count]) => `${table} ${count}`).join(' · ');
  const hasConflict = (preflight.conflicts || []).length > 0;
  return `${actions}<section class="android-settings-card android-settings-backup-preflight">
    <strong>预检：格式 ${escapeHtml(preflight.format)} v${Number(preflight.version)} · Schema ${Number(preflight.schemaVersion)} · 资产 ${storageBytes(preflight.assetBytes)}</strong>
    <p>${escapeHtml(counts || '没有业务表记录')}</p>
    ${hasConflict ? `<p class="error">${escapeHtml(preflight.conflicts.join('\n'))}</p><button data-action="clear-local-backup-replace" ${backup.working ? 'disabled' : ''}>取消，不替换本地</button><button data-action="select-local-backup-replace" ${backup.working ? 'disabled' : ''}>${backup.replaceLocal ? '已选择：替换本地' : '选择替换本地（强确认）'}</button>` : ''}
    <button class="primary" data-action="restore-local-backup" ${backup.working || (hasConflict && !backup.replaceLocal) ? 'disabled' : ''}>恢复并要求重启</button>
    <button data-action="cancel-local-restore" ${backup.working ? 'disabled' : ''}>取消此次恢复</button>
  </section>${backup.notice ? `<p class="android-settings-helper success" role="status">${escapeHtml(backup.notice)}</p>` : ''}${backup.error ? `<p class="android-settings-helper error" role="alert">${escapeHtml(backup.error)}</p>` : ''}`;
}

function personalization({ settings, draft, capabilities }) {
  const tone = CONVERSATION_TONES.find(item => item.id === draft.tone) || CONVERSATION_TONES[0];
  const unavailable = !capabilities.ordinaryChatPersonalization;
  return `<div class="android-settings-page android-settings-personalization">
    ${unavailable ? '<section class="android-settings-card android-settings-status-card"><strong>Desktop 普通发送尚未接入个性化上下文</strong><p>已有值保留在 Desktop SQLite，但当前本机对话只保存文本，不会调用模型或应用这些设置。</p></section>' : ''}
    ${switchRow('启用记忆', '', 'memoryEnabled', settings.memoryEnabled, unavailable)}
    <p class="android-settings-helper">允许 南枫AI 根据你的聊天、文件和已关联的应用为你提供个性化体验。</p>
    ${switchRow('历史资料库', '', 'historyLibraryEnabled', settings.historyLibraryEnabled, unavailable || !capabilities.historyLibrary)}
    <p class="android-settings-helper">低频整理有价值的历史对话，并在后续对话优先调用少量相关资料；原对话、附件和工具内容不发送。</p>
    <button class="android-settings-value-row" data-action="open-settings-picker" data-picker="tone" ${unavailable ? 'disabled' : ''}><span>基础风格和语气</span><small>${escapeHtml(tone.label)}</small></button>
    <p class="android-settings-helper">这是 南枫AI 在与你对话时使用的主要语气。这不会影响 南枫AI 的功能。</p>
    <button class="android-settings-value-row" data-action="open-settings-page" data-page="memory-overview"><span>记忆摘要</span><small>›</small></button>
    <p class="android-settings-helper">查看 南枫AI 对你的了解概览。如果你希望它持续记住某些信息，可以使用下方 自定义指令。</p>
    <label class="android-settings-field"><span>你的昵称</span><input id="personalization-nickname" maxlength="80" placeholder="例如：南烛枫" value="${escapeHtml(draft.nickname)}" ${unavailable ? 'disabled' : ''}></label>
    <label class="android-settings-field"><span>你的职业</span><input id="personalization-occupation" maxlength="120" placeholder="工程师、学生等" value="${escapeHtml(draft.occupation)}" ${unavailable ? 'disabled' : ''}></label>
    <label class="android-settings-field android-settings-field-long"><span class="android-settings-field-heading"><span>自定义指令</span><button type="button" data-action="open-custom-instructions-fullscreen" ${unavailable ? 'disabled' : ''}>全屏编辑</button></span><textarea id="personalization-instructions" maxlength="6000" rows="5" placeholder="希望南枫 AI 如何回答你" ${unavailable ? 'disabled' : ''}>${escapeHtml(draft.customInstructions)}</textarea><small>${draft.customInstructions.length} / 6000</small></label>
    <p class="android-settings-helper">你的 自定义指令 将用于所有南枫AI的对话。</p>
  </div>`;
}

function remindersPage(context) {
  const { settings, capabilities, reminders: projection = { drafts: [], plans: [], diagnostics: [] }, reminderNotificationPermission = 'default', reminderNotificationBridge: bridge = {}, backgroundRuntime = {} } = context;
  const reminderNotificationPermissionReady = ['granted', 'legacy'].includes(reminderNotificationPermission);
  const scheduleLabel = item => ({ ONCE: '一次', HOURLY: '每小时', DAILY: '每天', WEEKLY: '每周' })[item.scheduleKind] || item.scheduleKind;
  const statusLabel = value => ({ ACTIVE: '运行中', RUNNING: '执行中', PAUSED: '已暂停', COMPLETED: '已完成', FAILED: '失败', UNKNOWN: '结果未知', PENDING_REVIEW: '待确认', NOT_ELIGIBLE: '不符合提醒条件', REJECTED: '已拒绝' })[value] || value;
  const planCards = (projection.plans || []).map(item => `<article class="android-reminder-card" data-reminder-plan-id="${escapeHtml(item.planId)}"><header><div><strong>${escapeHtml(item.title)}</strong><small>${escapeHtml(statusLabel(item.status))} · ${escapeHtml(scheduleLabel(item))} · ${escapeHtml(item.timezoneId)}</small></div><span>${item.lastChargeMicros == null ? '费用未报告' : `$${(item.lastChargeMicros / 1_000_000).toFixed(6)}`}</span></header><p>${escapeHtml(item.instruction)}</p><small>下次执行：${item.nextRunAtMs ? escapeHtml(new Date(item.nextRunAtMs).toLocaleString()) : '无'}${item.lastSafeErrorCode ? ` · ${escapeHtml(item.lastSafeErrorCode)}` : ''}</small><div class="android-reminder-actions">${['ACTIVE', 'PAUSED'].includes(item.status) ? `<button data-action="edit-reminder-plan" data-id="${escapeHtml(item.planId)}">编辑</button>` : ''}${item.status === 'ACTIVE' || item.status === 'RUNNING' ? `<button data-action="set-reminder-paused" data-id="${escapeHtml(item.planId)}" data-paused="true">暂停</button>` : item.status === 'PAUSED' ? `<button data-action="set-reminder-paused" data-id="${escapeHtml(item.planId)}" data-paused="false">恢复</button>` : ''}${['FAILED', 'UNKNOWN'].includes(item.status) ? `<button data-action="retry-reminder-plan" data-id="${escapeHtml(item.planId)}">显式重试</button>` : ''}<button class="danger" data-action="delete-reminder-plan" data-id="${escapeHtml(item.planId)}">删除</button></div></article>`).join('');
  const draftCards = (projection.drafts || []).filter(item => ['PENDING_REVIEW', 'FAILED', 'UNKNOWN'].includes(item.status)).map(item => `<article class="android-reminder-card draft"><header><div><strong>${escapeHtml(item.title || '提醒草案')}</strong><small>${escapeHtml(statusLabel(item.status))}${item.safeErrorCode ? ` · ${escapeHtml(item.safeErrorCode)}` : ''}</small></div></header><p>${escapeHtml(item.instruction || '模型尚未产生可审阅内容。')}</p><div class="android-reminder-actions">${item.status === 'PENDING_REVIEW' ? `<button class="primary" data-action="review-reminder-draft" data-id="${escapeHtml(item.draftId)}">审阅编辑</button><button data-action="reject-reminder-draft" data-id="${escapeHtml(item.draftId)}">拒绝</button>` : `<button data-action="retry-reminder-draft" data-id="${escapeHtml(item.draftId)}">显式重试</button>`}</div></article>`).join('');
  return `<div class="android-settings-page"><section class="android-settings-card android-settings-reminders">
    ${switchRow('计划监控结果通知', capabilities.monitorNotifications ? (settings.monitorNotifications ? '监控完成后可发送系统通知；仍需在系统中允许通知' : '已关闭：监控仍会执行，结果只保存在本机任务列表') : 'Desktop 尚无系统通知与计划监控消费者；已保留设置值，不伪造通知', 'monitorNotifications', capabilities.monitorNotifications && settings.monitorNotifications, !capabilities.monitorNotifications)}
    ${capabilities.monitorNotifications && !reminderNotificationPermissionReady ? `<button class="android-settings-value-row" data-action="request-reminder-notification-permission"><span>系统通知权限</span><small>${reminderNotificationPermission === 'denied' ? '已拒绝，请在系统设置中开启' : '点按授权 ›'}</small></button>` : ''}
    ${capabilities.monitorNotifications ? `<p class="android-settings-helper" data-reminder-notification-bridge-status>通知点击桥：${bridge.supported && bridge.initialized && bridge.listenerReady ? '原生桥已就绪' : '已降级，不影响其他本机读取'} · ${escapeHtml(bridge.safeCode || 'NOT_READ')}${Number(bridge.pendingActionCount || 0) ? ` · 待处理 ${Number(bridge.pendingActionCount)}` : ''}</p>` : ''}
    <p class="android-settings-helper" data-background-runtime-status>${backgroundRuntime.desiredEnabled ? (backgroundRuntime.installed ? '退出后后台唤醒：已启用；只重读已确认计划和历史整理水位' : `退出后后台唤醒不可用 · ${escapeHtml(backgroundRuntime.safeCode || 'NOT_READ')}`) : '当前无活动计划或自动历史整理，不保留系统后台任务'}</p>
    <div class="android-settings-thin-divider"></div>
    ${switchRow('对话提醒建议', capabilities.reminderSuggestions ? (settings.reminderSuggestions ? '仅少量高价值对话末尾显示“添加提醒 / 监控”' : '已关闭：不再显示对话尾部的提醒建议') : 'Desktop 普通对话尚无提醒建议 owner', 'reminderSuggestions', capabilities.reminderSuggestions && settings.reminderSuggestions, !capabilities.reminderSuggestions)}
    <div class="android-settings-thin-divider"></div>
    ${switchRow('对话未读提醒', capabilities.unreadIndicators ? (settings.unreadIndicators ? '在左侧对话列表显示主题色未读标记，不发送系统通知' : '已关闭：不显示左侧对话列表的未读标记') : 'Desktop 尚无未读水位与列表消费者', 'unreadIndicators', capabilities.unreadIndicators && settings.unreadIndicators, !capabilities.unreadIndicators)}
  </section><section class="android-reminder-section"><div class="section-head"><div><h2>计划与监控</h2><p>草案只有确认后才会成为持久化计划；执行结果只保存在本机。</p></div><button class="primary" data-action="create-manual-reminder-draft" ${capabilities.reminderSuggestions ? '' : 'disabled'}>新建草案</button></div>${draftCards}${planCards || '<p class="empty-copy">暂无已确认计划。</p>'}</section></div>`;
}

function accountSyncPage(context) {
  const account = context.accountSync || {};
  const recovery = context.accountRecovery;
  const signedIn = Boolean(account.email);
  const recoveryReady = account.recoveryState === 'CONFIRMED';
  const ready = recoveryReady && !['NOT_CONFIGURED', 'SIGNED_OUT', 'SIGNED_OUT_KEEP_LOCAL'].includes(account.state);
  const stateLabels = {
    NOT_CONFIGURED: '服务未配置', SIGNED_OUT: '未登录', SIGNED_OUT_KEEP_LOCAL: '已退出，本机数据保留',
    AUTHENTICATED_NEEDS_RECOVERY_CONFIRMATION: '待确认恢复码', DIRECTION_REQUIRED: '待选择同步方向', READY: '已就绪',
    SYNCING: '正在同步', CONFLICT: '存在版本冲突', FAILED: '同步需要处理',
  };
  const stateLabel = stateLabels[account.state] || account.state || '未读取';
  const identity = signedIn
    ? `<div class="android-account-identity">${account.avatarDataUrl ? `<img src="${escapeHtml(account.avatarDataUrl)}" alt="">` : `<span aria-hidden="true">${escapeHtml((account.displayName || account.email || '南').slice(0, 1))}</span>`}<div><strong>${escapeHtml(account.displayName || '已登录 Google 账号')}</strong><p>${escapeHtml(account.email)}</p></div></div>`
    : `<div><strong>Google 账号</strong><p>通过系统浏览器完成 OAuth；WebView 不接触密码或 Token。</p></div>`;
  const recoveryCard = recovery
    ? `<section class="android-settings-card android-account-recovery" aria-label="恢复码"><strong>${recovery.rotation ? '新恢复码只显示这一次' : '恢复码只显示这一次'}</strong><output>${escapeHtml(recovery.recoveryCode)}</output><p>请存入密码管理器或离线安全位置。南枫云不保存明文恢复码。${recovery.rotation ? '确认后将逐个重加密已选云端对话，全部回读成功后才激活新恢复码。' : ''}</p><button class="primary" data-action="${recovery.rotation ? 'confirm-account-recovery-rotation' : 'confirm-account-recovery'}" data-confirmation-hash="${escapeHtml(recovery.confirmationHash)}">我已安全保存，完成确认</button></section>`
    : '';
  const diagnostics = (account.diagnostics || []).map(item => `<li><strong>${escapeHtml(item.event)}</strong><span>${escapeHtml(item.outcome)}${item.safeCode ? ` · ${escapeHtml(item.safeCode)}` : ''}</span><time>${new Date(Number(item.occurredAtMs || 0)).toLocaleString('zh-CN')}</time></li>`).join('');
  const notifications = (account.notifications || []).map(item => `<li class="${item.unread ? 'unread' : ''}"><strong>${escapeHtml(item.kind)}</strong><span>${escapeHtml(item.message)}</span></li>`).join('');
  return `<div class="android-settings-page android-account-sync-page">
    <section class="android-settings-card android-account-summary">${identity}<span class="android-account-state ${ready ? 'ready' : ''}">${escapeHtml(stateLabel)}</span><div class="android-account-actions"><button data-action="sign-in-google-account" ${!context.native || !account.configured ? 'disabled' : ''}>${signedIn ? '切换账号' : '使用 Google 登录'}</button>${signedIn ? '<button data-action="sign-out-google-account">退出登录</button>' : ''}</div></section>
    ${!account.configured ? '<p class="android-settings-helper">当前未配置 Google Desktop Client 与南枫云公开端点；页面不会自动发起登录或同步。</p>' : ''}
    ${signedIn && !recoveryReady && !recovery ? '<section class="android-settings-card android-settings-status-card"><strong>先建立恢复保护</strong><p>恢复码确认前不会上传任何对话。</p><button class="primary" data-action="create-account-recovery">创建恢复码</button></section>' : ''}
    ${recoveryCard}
    ${account.state === 'DIRECTION_REQUIRED' ? '<section class="android-settings-card android-settings-status-card"><strong>选择首次同步方向</strong><p>以本机显式选中的对话为起点；每次上传前仍会回读云端。若云端已有未知版本，立即转为冲突，不覆盖。</p><button class="primary" data-action="choose-selected-local-sync-start">以本机所选对话开始</button></section>' : ''}
    <section class="android-settings-card android-account-sync-control"><div><strong>加密同步</strong><p>仅上传您在会话右键菜单选中的纯文本对话；附件、工具结果、草稿和未完成回复会被整体拒绝。</p><small>已选 ${Number(account.selectedConversationCount || 0)} 个 · ${account.lastSuccessAtMs ? `上次成功 ${new Date(Number(account.lastSuccessAtMs)).toLocaleString('zh-CN')}` : '尚未成功同步'}</small></div><button class="android-settings-switch" data-action="toggle-periodic-account-sync" aria-pressed="${Boolean(account.periodicEnabled)}" role="switch" ${ready ? '' : 'disabled'}><span></span></button></section>
    <p class="android-settings-helper">定期同步只处理已选对话。冲突或提交结果未知时会停止，不会静默重发。退出账号不删除本机数据。</p>
    ${recoveryReady ? `<section class="android-settings-card android-settings-status-card"><strong>恢复码更换与丢失处理</strong><p>只有当前设备仍可解锁现有密钥时，才能创建新恢复码并重加密云端数据。</p><button data-action="create-account-recovery-rotation">更换恢复码／已丢失</button>${account.rotationPending ? `<button data-action="retry-account-recovery-rotation">继续未完成的重加密</button>` : ''}</section>` : ''}
    ${signedIn ? `<section class="android-settings-card android-account-cloud-restore"><strong>从南枫云恢复</strong><p>只在您点击“读取云端列表”后联网。恢复会新建独立本机工作区，不覆盖现有数据。</p><button data-action="load-account-cloud-documents">读取云端列表</button>${(account.remoteDocuments || []).length ? `<label class="android-settings-field"><span>云端文档</span><select id="account-cloud-document">${account.remoteDocuments.map(item => `<option value="${escapeHtml(item.documentId)}">${escapeHtml(item.documentId)} · r${Number(item.revision)}</option>`).join('')}</select></label><label class="android-settings-field"><span>恢复码</span><input id="account-cloud-recovery-code" type="password" autocomplete="off" spellcheck="false" placeholder="输入 NF- 恢复码"></label><button class="primary" data-action="restore-account-cloud-conversation">恢复为新工作区</button>` : ''}</section>` : ''}
    ${account.pendingUnknown ? `<section class="android-settings-card android-settings-status-card"><strong>上次提交结果未知</strong><p>先只读回读云端版本；不会直接重复上传。</p><button data-action="reconcile-account-sync" data-workspace-id="${escapeHtml(account.pendingUnknown.workspaceId)}" data-conversation-id="${escapeHtml(account.pendingUnknown.conversationId)}">核对云端结果</button></section>` : ''}
    <section class="android-settings-card android-account-notifications"><strong>同步通知</strong><ul>${notifications || '<li><span>尚无同步通知。</span></li>'}</ul></section>
    <section class="android-settings-card android-account-diagnostics"><strong>运行诊断</strong><p>只保留事件、结果和安全错误码；不包含 Token、恢复码、对话正文或路径。</p><ul>${diagnostics || '<li><span>尚无诊断记录。</span></li>'}</ul></section>
  </div>`;
}

function modelConfiguration(context) {
  const services = context.modelServiceSettings?.length ? context.modelServiceSettings : MODEL_SERVICE_PREVIEW_SETTINGS;
  const selected = services.find(item => item.providerId === context.modelProviderId) || services[0];
  const draft = context.modelServiceDraft?.providerId === selected.providerId ? context.modelServiceDraft : selected;
  const preset = selected.presets?.find(item => item.id === draft.presetId) || selected.presets?.[0];
  const storedMask = '••••••••••••••••••••••••••••••••';
  const keyValue = context.modelCredentialDraft || (selected.credentialStored ? storedMask : '');
  const busy = context.modelSettingsSaving || context.modelSettingsTesting;
  return `<div class="android-settings-page android-settings-model-configuration">
    <div class="android-settings-segments" role="tablist">${services.map(item => `<button data-action="select-model-service-provider" data-provider-id="${item.providerId}" class="${item.providerId === selected.providerId ? 'selected' : ''}" role="tab" aria-selected="${item.providerId === selected.providerId}">${item.providerId === 'ZHIPU' ? '智谱' : item.providerId === 'QWEN' ? 'Qwen' : item.providerId === 'DEEPSEEK' ? 'DeepSeek' : 'OpenRouter'}</button>`).join('')}</div>
    <section class="android-settings-card android-settings-model-provider-row"><strong>${escapeHtml(selected.providerDisplayName)}</strong><button class="android-settings-switch" data-action="toggle-model-service-enabled" aria-pressed="${Boolean(draft.enabled)}" role="switch" ${busy ? 'disabled' : ''}><span></span></button></section>
    <button class="android-settings-model-preset" data-action="open-settings-picker" data-picker="modelPreset" ${busy ? 'disabled' : ''}><span><strong>${escapeHtml(preset?.displayName || draft.presetDisplayName || '')}</strong><small>${escapeHtml(preset?.family || '')}</small></span>${icon(icons.chevronDown, '展开模型预设')}</button>
    <button class="android-settings-model-test" data-action="test-model-service-connection" ${busy || !selected.credentialStored ? 'disabled' : ''}>${context.modelSettingsTesting ? '<span class="android-settings-spinner" aria-hidden="true"></span>正在测试…' : '测试连接'}</button>
    ${context.modelSettingsTesting ? '<p class="android-settings-model-feedback">正在检查连接，请稍候。</p>' : ''}
    ${context.modelSettingsNotice ? `<p class="android-settings-model-feedback success">${escapeHtml(context.modelSettingsNotice)}</p>` : ''}
    ${context.modelSettingsError ? `<p class="android-settings-model-feedback error">${escapeHtml(context.modelSettingsError)}</p>` : ''}
    <label class="android-settings-field android-settings-model-key"><span>API Key</span><div><span aria-hidden="true">${icon(icons.key, '')}</span><input id="model-service-api-key" type="${context.modelCredentialVisible ? 'text' : 'password'}" autocomplete="off" spellcheck="false" value="${escapeHtml(keyValue)}" ${busy ? 'disabled' : ''}><button data-action="reveal-model-service-credential" aria-label="${context.modelCredentialVisible ? '隐藏 API Key' : '显示 API Key'}" ${busy || (!selected.credentialStored && !context.modelCredentialDraft) ? 'disabled' : ''}>${icon(context.modelCredentialVisible ? icons.visibilityOff : icons.visibility, context.modelCredentialVisible ? '隐藏 API Key' : '显示 API Key')}</button></div></label>
    <div class="android-settings-model-save"><button data-action="save-model-service-settings" ${busy ? 'disabled' : ''}>${context.modelSettingsSaving ? '<span class="android-settings-spinner" aria-hidden="true"></span>正在保存…' : '保存'}</button></div>
  </div>`;
}

function importResultCard(title, task, kind) {
  if (!task) return '';
  const imported = Number(task.importedCount ?? task.confirmedCount ?? task.items?.filter(item => ['COMMITTED', 'CONFIRMED'].includes(item.status)).length ?? 0);
  const failed = Number(task.failedCount ?? task.items?.filter(item => item.status === 'FAILED').length ?? 0);
  const skipped = Number(task.skippedCount ?? task.items?.filter(item => item.status === 'SKIPPED').length ?? 0);
  return `<section class="android-settings-card android-settings-import-result"><strong>${escapeHtml(title)}</strong><p>${escapeHtml(task.status || '已记录')} · 已导入 ${imported} · 失败 ${failed} · 跳过 ${skipped}</p>${kind === 'zip' ? `<div class="android-settings-result-actions"><button data-action="retry-p6k-zip">重试</button><button data-action="skip-p6k-zip-failures" ${failed ? '' : 'disabled'}>跳过失败项</button><button class="danger" data-action="delete-p6k-zip-batch">删除导入批次</button></div>` : ''}</section>`;
}

const usageAmount = records => {
  const totals = new Map();
  for (const record of records) {
    if (!Number.isFinite(record.chargeMicros) || !record.currencyCode) continue;
    totals.set(record.currencyCode, (totals.get(record.currencyCode) || 0) + record.chargeMicros);
  }
  if (!totals.size) return '金额未知';
  return [...totals].map(([currency, micros]) => {
    const symbol = currency === 'CNY' ? '¥' : currency === 'USD' ? '$' : `${currency} `;
    return `${symbol}${(micros / 1_000_000).toFixed(6).replace(/0+$/, '').replace(/\.$/, '')}`;
  }).join(' + ');
};
const storageBytes = value => {
  const bytes = Math.max(0, Number(value || 0));
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 ** 2) return `${(bytes / 1024).toFixed(1)} KB`;
  if (bytes < 1024 ** 3) return `${(bytes / 1024 ** 2).toFixed(1)} MB`;
  return `${(bytes / 1024 ** 3).toFixed(1)} GB`;
};

function usageLedgerPage(context) {
  const records = context.usageLedger?.records || [];
  if (!records.length) return `<div class="android-settings-page android-settings-ledger-empty">${icon(icons.data, '')}<strong>尚无可用费用记录</strong><p>已返回输入和输出 Token 的调用会显示服务商金额或本地估算。</p></div>`;
  const sections = [['conversation', '会话'], ['reminder', '计划与提醒'], ['title', '会话标题整理'], ['history', '历史资料整理'], ['ocr', '南枫转写']];
  const selected = context.usageSection || 'conversation';
  const category = record => record.entryId?.startsWith('usage-reminder-') ? 'reminder' : record.entryId?.includes('history') ? 'history' : record.entryId?.includes('title') ? 'title' : record.entryId?.includes('transcription') ? 'ocr' : 'conversation';
  const rows = records.filter(record => category(record) === selected);
  return `<div class="android-settings-page android-settings-ledger">
    <section class="android-settings-card android-settings-ledger-summary"><strong>本机累计</strong><div><span>服务商实际金额</span><b>${escapeHtml(usageAmount(records))}</b></div><div><span>输入 Token</span><b>${Number(context.usageLedger?.inputTokens || 0).toLocaleString('zh-CN')}</b></div><div><span>输出 Token</span><b>${Number(context.usageLedger?.outputTokens || 0).toLocaleString('zh-CN')}</b></div></section>
    <section class="android-settings-card android-settings-ledger-grid">${sections.map(([id, label]) => { const items = records.filter(record => category(record) === id); return `<div><strong>${label}</strong><span>次数 <b>${items.length} 次</b></span><span>费用 <b>${items.length ? escapeHtml(usageAmount(items)) : '金额未知'}</b></span></div>`; }).join('')}</section>
    <div class="android-settings-ledger-segments">${sections.map(([id, label]) => `<button data-action="select-usage-section" data-section="${id}" class="${selected === id ? 'selected' : ''}">${label}</button>`).join('')}</div>
    ${rows.length ? rows.map(record => { const conversation = (context.data?.exchange?.conversations || []).find(item => item.id === record.conversationId); return `<section class="android-settings-card android-settings-ledger-row"><div><strong>${escapeHtml(conversation?.title || (selected === 'reminder' ? '计划与提醒' : '已删除的对话'))}</strong><b>${escapeHtml(usageAmount([record]))}</b></div><p>${escapeHtml(record.modelId)} · 输入 ${Number(record.inputTokens || 0).toLocaleString('zh-CN')} / 输出 ${Number(record.outputTokens || 0).toLocaleString('zh-CN')} Token</p><small>${new Date(record.occurredAtMs).toLocaleString('zh-CN', { hour12: false })}</small></section>`; }).join('') : `<p class="android-settings-empty">还没有${escapeHtml(sections.find(([id]) => id === selected)?.[1] || '')}费用记录。</p>`}
  </div>`;
}

function contextSelectionsPage(context) {
  const records = (context.contextSelectionRecords || []).filter(item => item.selectedSources?.length);
  if (!records.length) return '<div class="android-settings-page"><p class="android-settings-empty align-start">还没有上下文记录。</p></div>';
  const conversations = context.data?.exchange?.conversations || [];
  return `<div class="android-settings-page">${records.map(record => `<section class="android-settings-card android-settings-context-record"><div><strong>${escapeHtml(conversations.find(item => item.id === record.conversationId)?.title || '已删除的对话')}</strong><b>${record.selectedSources.length} 项</b></div><p>${escapeHtml(record.providerLabel || record.providerId)} · ${escapeHtml(record.modelId)}</p>${record.selectedSources.slice(0, 2).map(source => `<span><strong>${escapeHtml(source.kind)}</strong><small>${escapeHtml(source.title)}</small></span>`).join('')}${record.selectedSources.length > 2 ? `<small>另有 ${record.selectedSources.length - 2} 项</small>` : ''}<footer>本轮输入约 ${Number(record.fixedInputTokens || record.budget?.fixedInputTokens || 0).toLocaleString('zh-CN')} Token</footer></section>`).join('')}</div>`;
}

function diagnosticsPage(context) {
  const failures = context.diagnosticRecords || [];
  const invocations = [...(context.invocationRecords || []), ...(context.reminders?.diagnostics || [])];
  return `<div class="android-settings-page android-settings-diagnostics"><section><header><div><strong>连接失败</strong><small>对话与连接测试中近 7 天的失败记录</small></div><span>${failures.length} 条</span></header>${failures.length ? failures.map(item => `<div class="android-settings-card android-settings-diagnostic-row"><strong>${escapeHtml(item.conversationTitle || '连接测试或历史未关联记录')}</strong><p>${escapeHtml(item.providerLabel || '')} · ${escapeHtml(item.modelId || '')}</p><b>${escapeHtml(item.summary || '调用失败，请查看技术详情。')}</b></div>`).join('') : '<p>近 7 天没有连接失败记录。</p>'}</section><section><header><div><strong>自动与工具任务</strong><small>标题整理、历史整理和南枫转写等后台调用</small></div><span>${invocations.length} 条</span></header>${invocations.length ? invocations.map(item => `<div class="android-settings-card android-settings-diagnostic-row"><strong>${escapeHtml(item.title || item.kind || '后台调用')}</strong><p>${escapeHtml(item.summary || '')}</p></div>`).join('') : '<p>暂无自动或工具任务调用记录。</p>'}</section></div>`;
}

function privacyInventoryPage(context) {
  const inventory = context.privacyInventory || { totalBytes: 0, aggregates: [] };
  const values = new Map((inventory.aggregates || []).map(item => [item.id, item]));
  const value = id => values.get(id) || { count: 0, byteCount: 0 };
  const searchable = value('search_text');
  const attachmentCount = ['attachment_images', 'attachment_videos', 'attachment_audio', 'attachment_files'].reduce((sum, id) => sum + Number(value(id).count || 0), 0);
  const rows = (title, items) => {
    const visible = items.filter(item => item.aggregate.count > 0 || item.aggregate.byteCount > 0);
    if (!visible.length) return '';
    return `<strong class="android-settings-privacy-heading">${escapeHtml(title)}</strong><div class="android-settings-privacy-rows">${visible.map(item => `<button data-action="${item.action || 'privacy-noop'}" ${item.category ? `data-category="${item.category}"` : ''}><span>${escapeHtml(item.label)}</span><small>${escapeHtml(item.custom || `${item.aggregate.count} ${item.unit} · ${storageBytes(item.aggregate.byteCount)}`)}</small>${item.action ? icon(icons.chevronRight, `进入${item.label}`) : ''}</button>`).join('')}</div>`;
  };
  const contentRows = [
    { label: '全部', aggregate: { count: searchable.count + attachmentCount, byteCount: searchable.byteCount }, unit: '项', custom: `${searchable.count} 条正文 · ${attachmentCount} 项附件`, action: 'open-privacy-search-category', category: 'all' },
    { label: '正文', aggregate: searchable, unit: '条', action: 'open-privacy-search-category', category: 'text' },
    { label: '记忆', aggregate: value('memory'), unit: '条', action: 'show-memory' },
    { label: '知识库', aggregate: value('knowledge'), unit: '条', action: 'show-knowledge' },
    { label: '项目', aggregate: value('projects'), unit: '个', action: 'show-projects' },
    { label: '语音转写任务', aggregate: value('transcription_tasks'), unit: '项', action: 'show-transcription' },
  ];
  const attachmentRows = [
    { label: '图片', aggregate: value('attachment_images'), unit: '个', action: 'open-privacy-search-category', category: 'image' },
    { label: '视频', aggregate: value('attachment_videos'), unit: '个', action: 'open-privacy-search-category', category: 'video' },
    { label: '音频', aggregate: value('attachment_audio'), unit: '个', action: 'open-privacy-search-category', category: 'audio' },
    { label: '文件', aggregate: value('attachment_files'), unit: '个', action: 'open-privacy-search-category', category: 'file' },
    { label: '其他导入资料', aggregate: value('import_source_assets'), unit: '份' },
  ];
  return `<div class="android-settings-page android-settings-privacy"><section class="android-settings-card android-settings-privacy-summary"><header><strong>本机数据</strong><b>${storageBytes(inventory.totalBytes)}</b></header>${rows('对话与内容', contentRows)}${rows('附件', attachmentRows)}</section><button class="android-settings-privacy-cleanup" data-action="open-privacy-cleanup">选择清理范围</button></div>`;
}

function simplePage(page, context) {
  const { data, favorites, runtimeInfo, settings, native, p6eAcceptance, p6kTask, chatgptTask, claudeTask, modelServiceSettings } = context;
  const active = data?.exchange?.conversations || [];
  if (page === 'conversations') return `<div class="android-settings-page">${group('', [row({ glyph: icons.star, title: '收藏', page: 'favorites' }), row({ glyph: icons.archive, title: '已归档', page: 'archived' }), row({ glyph: icons.trash, title: '回收站', page: 'recycle' })])}</div>`;
  if (page === 'favorites') return `<div class="android-settings-page android-settings-list">${active.filter(item => favorites.has(item.id) && !item.archived && !item.deleted).map(item => `<div class="android-settings-list-row"><button data-action="select-chat" data-id="${escapeHtml(item.id)}">${escapeHtml(item.title || '未命名会话')}</button><button data-action="toggle-conversation-favorite" data-id="${escapeHtml(item.id)}">${icon(icons.starOff, '取消收藏')}</button></div>`).join('') || '<p class="android-settings-empty">还没有收藏会话。</p>'}</div>`;
  if (page === 'archived' || page === 'recycle') {
    const isRecycle = page === 'recycle';
    const conversations = active.filter(item => isRecycle ? item.deleted : item.archived && !item.deleted);
    const summary = isRecycle ? '会话消息树尚未物理删除；恢复后会回到普通对话列表。' : '归档会话不会出现在日常列表；恢复后会回到普通对话列表。';
    const empty = isRecycle ? '暂无回收站会话。' : '暂无已归档会话。';
    return `<div class="android-settings-page android-settings-list android-settings-lifecycle"><div class="android-settings-lifecycle-head"><p class="android-settings-helper">${summary}</p>${conversations.length ? `<button class="${isRecycle ? 'danger' : ''}" data-action="open-conversation-bulk-cleanup" data-scope="${isRecycle ? 'recycle' : 'archived'}" data-count="${conversations.length}">${isRecycle ? '清空回收站' : '清空已归档'}</button>` : ''}</div>${conversations.map(item => `<div class="android-settings-list-row"><button class="android-settings-lifecycle-open" data-action="select-chat" data-id="${escapeHtml(item.id)}"><strong>${escapeHtml(item.title || '未命名会话')}</strong><small>创建于 ${escapeHtml(item.createdAt || '')}</small></button><span class="android-settings-lifecycle-actions"><button data-action="${isRecycle ? 'restore-deleted-conversation' : 'restore-conversation'}" data-id="${escapeHtml(item.id)}" data-revision="${Number(item.revision || 0)}" aria-label="恢复会话" title="恢复会话">${icon(icons.restore, '恢复')}</button><button class="danger" data-action="${isRecycle ? 'open-conversation-permanent-delete' : 'open-archived-conversation-delete'}" data-id="${escapeHtml(item.id)}" data-revision="${Number(item.revision || 0)}" data-title="${escapeHtml(item.title || '未命名会话')}" aria-label="${isRecycle ? '永久删除会话' : '移入回收站'}" title="${isRecycle ? '永久删除' : '移入回收站'}">${icon(icons.trash, isRecycle ? '永久删除' : '移入回收站')}</button></span></div>`).join('') || `<p class="android-settings-empty">${empty}</p>`}</div>`;
  }
  if (page === 'model') {
    const services = modelServiceSettings?.length ? modelServiceSettings : MODEL_SERVICE_PREVIEW_SETTINGS;
    const readiness = (providerId, label) => {
      const item = services.find(service => service.providerId === providerId);
      const ready = item?.enabled && item?.credentialStored;
      return `<span class="${ready ? 'ready' : ''}">${ready ? '●' : '○'} ${escapeHtml(label)} · ${ready ? '已连接' : '未启用'}</span>`;
    };
    const helper = settings.webSearchEnabled ? '需要当前信息时自动检索公开网页并标注来源；可能产生服务费用' : '已关闭；普通对话不会使用网页检索';
    const webSearchAvailable = Boolean(context.capabilities.webSearch);
    const webSearchHelper = webSearchAvailable ? helper : 'Desktop 普通发送尚未建立网页检索执行器；开关值已保留，当前不会发起网络请求';
    return `<div class="android-settings-page"><div class="android-settings-provider-list">${readiness('OPENROUTER', 'OpenAI / Claude / Gemini')}${readiness('QWEN', 'Qwen')}${readiness('DEEPSEEK', 'DeepSeek')}${readiness('ZHIPU', '智谱 GLM')}</div><div class="android-settings-web-search"><div>${icon(icons.search, '')}<strong>实时网页搜索</strong><button class="android-settings-switch" data-action="toggle-product-setting" data-key="webSearchEnabled" aria-pressed="${settings.webSearchEnabled}" role="switch" ${webSearchAvailable ? '' : 'disabled'}><span></span></button></div><p>${escapeHtml(webSearchHelper)}</p></div>${group('', [row({ glyph: icons.hub, title: '模型设置', page: 'model-configuration' }), row({ glyph: icons.data, title: '费用与用量', page: 'conversation-cost' }), row({ glyph: icons.info, title: '上下文记录', page: 'context-selections' }), row({ glyph: icons.settings, title: '运行诊断', page: 'diagnostics' })])}</div>`;
  }
  if (page === 'model-configuration') {
    return modelConfiguration(context);
  }
  if (page === 'conversation-cost') return usageLedgerPage(context);
  if (page === 'context-selections') return contextSelectionsPage(context);
  if (page === 'diagnostics') return diagnosticsPage(context);
  if (page === 'about') return `<div class="android-settings-page"><section class="android-settings-card android-settings-about"><div><strong>南枫 AI</strong><p>本机对话、项目与知识工作区。</p></div>${divider}<div><strong>版本信息</strong><p>Desktop 版 ${escapeHtml(runtimeInfo.version || '读取中')}</p><small>${escapeHtml(runtimeInfo.platform || 'Desktop')} · ${escapeHtml(runtimeInfo.arch || '本机架构')}</small></div></section></div>`;
  if (page === 'memory-overview') {
    const memories = (data?.exchange?.memory || []).filter(item => (item.status || 'ACTIVE') === 'ACTIVE' && !item.deleted);
    const query = String(context.memorySummaryQuery || '').trim().toLocaleLowerCase('zh-CN');
    const visible = query ? memories.filter(item => String(item.body || '').toLocaleLowerCase('zh-CN').includes(query)) : memories;
    const updatedAt = memories.map(item => item.updatedAt || item.createdAt || '').filter(Boolean).sort().at(-1);
    return `<div class="android-settings-page android-memory-summary-page">
      <section class="android-memory-summary-meta"><strong>${updatedAt ? `更新于 ${escapeHtml(new Date(updatedAt).toLocaleDateString('zh-CN'))}` : '还没有记忆摘要'}</strong><div><button data-action="show-memory-summary-about">关于记忆</button><button data-action="refresh-memory-summary">刷新摘要</button><button class="danger" data-action="ask-delete-memory-summary" ${memories.length ? '' : 'disabled'}>删除记忆</button><button class="danger" data-action="ask-disable-memory-summary" ${settings.memoryEnabled ? '' : 'disabled'}>关闭生成和应用</button></div></section>
      ${context.memorySummaryNotice ? `<p class="android-settings-helper" role="status">${escapeHtml(context.memorySummaryNotice)}</p>` : ''}
      <section class="android-memory-summary-content">${visible.map((item, index) => `<article><h2>${escapeHtml(item.title || (index === 0 ? '概览' : `记忆 ${index + 1}`))}</h2><p>${escapeHtml(item.body || '')}</p></article>`).join('') || `<p class="android-settings-empty">${query ? '没有匹配的本机记忆。' : '还没有记忆摘要。'}</p>`}</section>
      <div class="android-memory-summary-composer"><input id="memory-summary-composer" maxlength="2000000" placeholder="询问或更新" value="${escapeHtml(context.memorySummaryComposer || '')}"><button data-action="submit-memory-summary" ${String(context.memorySummaryComposer || '').trim() && native ? '' : 'disabled'} aria-label="提交记忆问题或更新">${icon(icons.chevronRight, '提交')}</button></div>
    </div>`;
  }
  if (page === 'workspace') return `<div class="android-settings-page"><p class="android-settings-helper">Projects 用于范围整理；开启资料库搜索后，相关本地资料会随当前问题自动检索并加入上下文。</p>${group('', [row({ glyph: icons.folder, title: `管理 Projects`, action: 'show-projects' }), row({ glyph: icons.knowledge, title: '管理知识库', action: 'show-knowledge' })])}</div>`;
  if (page === 'development') return `<div class="android-settings-page">${group('', [row({ glyph: icons.settings, title: '本次 Context 控制', action: 'show-connections' }), row({ glyph: icons.info, title: '离线评测', action: 'show-p8-inspect' })])}${p6eAcceptance?.enabled ? `<section class="android-settings-card android-settings-status-card"><strong>P6-E 验收维护</strong><p>仅 acceptance 启动显示；正式启动不出现。</p><button data-action="run-p6e-temporary-maintenance-acceptance">运行 23h59 / 24h 维护验收</button>${p6eAcceptance.receipt ? `<small>23h59 保留=${p6eAcceptance.receipt.retainedAt23h59} · 24h 清理=${p6eAcceptance.receipt.removedAt24h}</small>` : ''}</section>` : ''}</div>`;
  if (page === 'account') return accountSyncPage(context);
  if (page === 'data') {
    return `<div class="android-settings-page android-settings-data-page">
      ${actionGroup('对话', [actionRow({ title: '导入 ChatGPT JSON', action: 'select-chatgpt-export' }), actionRow({ title: '导入 Claude JSON', action: 'select-claude-export' }), actionRow({ title: '导入结果', page: 'json-import-results' })])}
      ${actionGroup('', [actionRow({ title: '导入 ChatGPT ZIP', action: 'select-p6k-chatgpt-zip' }), actionRow({ title: '导入 Claude ZIP', action: 'select-p6k-claude-zip' }), actionRow({ title: '导入结果', page: 'zip-import-results' })])}
      ${actionGroup('工作区', [actionRow({ title: '导入工作区', action: 'select-v2-workspace-exchange', disabled: !native }), actionRow({ title: '导出工作区', action: 'start-export', disabled: !native || !data })])}
      ${localBackupGroup(context)}
    </div>`;
  }
  if (page === 'json-import-results') return `<div class="android-settings-page android-settings-list">${importResultCard('ChatGPT JSON', chatgptTask, 'json')}${importResultCard('Claude JSON', claudeTask, 'json') || (!chatgptTask ? '<p class="android-settings-empty">还没有导入记录。</p>' : '')}</div>`;
  if (page === 'zip-import-results') return `<div class="android-settings-page android-settings-list">${importResultCard(`${p6kTask?.provider || 'ZIP'} · 批次 1`, p6kTask, 'zip') || '<p class="android-settings-empty">还没有导入记录。</p>'}</div>`;
  if (page === 'privacy') return privacyInventoryPage(context);
  return `<div class="android-settings-page"><section class="android-settings-card android-settings-status-card"><strong>${escapeHtml(pageTitles[page] || '设置')}</strong><p>该入口正按当前 Android owner 接入 Desktop；未接通前不显示虚假成功状态。</p></section></div>`;
}

function picker(kind, context) {
  if (!kind) return '';
  const { appearance, draft } = context;
  if (kind === 'modelPreset') {
    const services = context.modelServiceSettings?.length ? context.modelServiceSettings : MODEL_SERVICE_PREVIEW_SETTINGS;
    const selected = services.find(item => item.providerId === context.modelProviderId) || services[0];
    const current = context.modelServiceDraft?.providerId === selected.providerId ? context.modelServiceDraft.presetId : selected.presetId;
    return `<div class="android-settings-picker-scrim" data-action="dismiss-settings-picker"><section class="android-settings-picker android-settings-model-picker" role="dialog" aria-modal="true" aria-label="聊天模型" data-overlay-surface>${(selected.presets || []).map(item => `<button data-action="select-model-service-preset" data-preset-id="${item.id}" aria-pressed="${item.id === current}"><span><strong>${escapeHtml(item.displayName)}</strong><small>${escapeHtml(item.description)}</small></span>${item.id === current ? icon(icons.check, '已选中') : ''}</button>`).join('')}${(selected.nonChatCapabilities || []).map(item => `<div class="android-settings-model-capability"><strong>${escapeHtml(item.displayName)}</strong><small>${escapeHtml(item.description)}</small><small>${escapeHtml(item.family)}</small></div>`).join('')}</section></div>`;
  }
  const options = kind === 'mode' ? APPEARANCE_MODES : kind === 'fontSize' ? FONT_SIZES : kind === 'themeColor' ? THEME_COLORS : CONVERSATION_TONES;
  const current = kind === 'mode' ? appearance.mode : kind === 'fontSize' ? appearance.fontSize : kind === 'themeColor' ? appearance.themeColor : draft.tone;
  const title = kind === 'mode' ? '外观' : kind === 'fontSize' ? '字体大小' : kind === 'themeColor' ? '主题色' : '基础风格和语气';
  return `<div class="android-settings-picker-scrim" data-action="dismiss-settings-picker"><section class="android-settings-picker" role="dialog" aria-modal="true" aria-label="${title}" data-overlay-surface>${options.map(item => `<button data-action="select-settings-picker-option" data-picker="${kind}" data-value="${item.id}" aria-pressed="${item.id === current}">${item.accent ? `<i style="--settings-dot:${item.accent}"></i>` : ''}<span><strong>${escapeHtml(item.label)}</strong>${item.detail ? `<small>${escapeHtml(item.detail)}</small>` : ''}</span>${item.id === current ? icon(icons.check, '已选中') : ''}</button>`).join('')}</section></div>`;
}

export function renderAndroidSettingsShell({ page = 'personalization', mobileHome = false, status = '', appearance = {}, settings = {}, personalizationDraft = settings, personalizationDirty = false, picker: pickerKind = null, data, favoriteConversationIds = new Set(), runtimeInfo = {}, connection = {}, native = false, p6eAcceptance = { enabled: false, receipt: null }, p6gCatalog = null, p6kTask = null, chatgptTask = null, claudeTask = null, modelServiceSettings = MODEL_SERVICE_PREVIEW_SETTINGS, modelProviderId = 'OPENROUTER', modelServiceDraft = null, modelCredentialDraft = '', modelCredentialVisible = false, modelSettingsSaving = false, modelSettingsTesting = false, modelSettingsNotice = '', modelSettingsError = '', usageLedger = { records: [], inputTokens: 0, outputTokens: 0, cachedInputTokens: 0 }, usageSection = 'conversation', contextSelectionRecords = [], diagnosticRecords = [], invocationRecords = [], privacyInventory = { totalBytes: 0, aggregates: [] }, localBackup = {}, accountSync = {}, accountRecovery = null, capabilities = {}, reminders = { drafts: [], plans: [], diagnostics: [] }, reminderNotificationPermission = 'default', reminderNotificationBridge = {}, backgroundRuntime = {}, memorySummaryQuery = '', memorySummaryComposer = '', memorySummaryNotice = '' } = {}) {
  const normalizedSettings = normalizeProductSettings(settings);
  const context = { appearance: normalizeAppearance(appearance), settings: normalizedSettings, draft: normalizeProductSettings({ ...normalizedSettings, ...personalizationDraft }), data, favorites: favoriteConversationIds, runtimeInfo, connection, native, p6eAcceptance, p6gCatalog, p6kTask, chatgptTask, claudeTask, modelServiceSettings, modelProviderId, modelServiceDraft, modelCredentialDraft, modelCredentialVisible, modelSettingsSaving, modelSettingsTesting, modelSettingsNotice, modelSettingsError, usageLedger, usageSection, contextSelectionRecords, diagnosticRecords, invocationRecords, privacyInventory, localBackup, accountSync, accountRecovery, capabilities, reminders, reminderNotificationPermission, reminderNotificationBridge, backgroundRuntime, memorySummaryQuery, memorySummaryComposer, memorySummaryNotice };
  context.page = page;
  const content = page === 'personalization' ? personalization(context) : page === 'reminders' ? remindersPage(context) : simplePage(page, context);
  return `<main class="chat-main android-settings-main"><div class="android-settings-layout ${mobileHome ? 'mobile-home' : 'mobile-detail'}"><aside class="android-settings-primary" aria-label="设置一级菜单"><div class="android-settings-primary-header"><button data-action="show-chat" aria-label="返回应用">${icon(icons.chevronLeft, '返回应用')}</button><h1>设置</h1><span></span></div><div class="android-settings-primary-scroll" data-settings-scroll-key="settings-primary">${home(context)}</div></aside><section class="android-settings-secondary" aria-label="设置二级页面">${header(page, personalizationDirty, capabilities)}<div class="android-settings-scroll" data-settings-scroll-key="settings-page-${escapeHtml(page)}">${content}${status ? `<p class="android-settings-status" role="status">${escapeHtml(status)}</p>` : ''}</div></section></div>${picker(pickerKind, context)}</main>`;
}
