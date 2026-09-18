import { icon, icons, settingsIcons } from './icon-source.mjs';
import { cnyCostLabel, projectedCost } from './desktop-cost-estimator.mjs';
import { APPEARANCE_MODES, CONVERSATION_TONES, CUSTOM_INSTRUCTIONS_MAX_LENGTH, DESKTOP_SETTINGS_CAPABILITIES, FONT_SIZES, THEME_COLORS, conversationToneDefinition, normalizeAppearance, normalizeProductSettings } from './desktop-parity-preferences.mjs';
import { renderConversationLifecyclePage } from './conversation-lifecycle-view.mjs';
import { renderLocalDataImportExportPage, renderLocalDataInventoryPage } from './local-data-view.mjs';

const escapeHtml = value => String(value ?? '').replace(
  /[&<>"']/g,
  char => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#039;' })[char],
);

const formatDevelopmentTime = epochSeconds => {
  const epoch = Number(epochSeconds);
  if (!Number.isFinite(epoch) || epoch <= 0) return '未记录';
  const date = new Date(epoch * 1000);
  const part = value => String(value).padStart(2, '0');
  return `${date.getFullYear()}-${part(date.getMonth() + 1)}-${part(date.getDate())} ${part(date.getHours())}:${part(date.getMinutes())}`;
};

const modelPreset = (id, displayName, description, family) => ({ id, displayName, description, family, chatSelectable: true });
export const MODEL_SERVICE_PREVIEW_SETTINGS = Object.freeze([
  { providerId: 'OPENROUTER', providerDisplayName: 'OpenRouter', enabled: false, presetId: 'GPT_5_6_TERRA', presetDisplayName: 'GPT-5.6 Terra', credentialStored: false, revision: 0, presets: [
    modelPreset('CLAUDE_FABLE_5_1', 'Claude Fable 5.1', '适合长程代码、研究与复杂知识工作。', 'Anthropic · OpenRouter'),
    modelPreset('GPT_6_ASTRA', 'GPT-6 Astra', '适合高难分析、工程与长程复杂任务。', 'OpenAI · OpenRouter'),
    modelPreset('CLAUDE_FABLE_5', 'Claude Fable 5', '应对最棘手的复杂任务。', 'Anthropic · OpenRouter'),
    modelPreset('CLAUDE_OPUS_5', 'Claude Opus 5', '适合复杂任务与深度推理。', 'Anthropic · OpenRouter'),
    modelPreset('CLAUDE_SONNET_5', 'Claude Sonnet 5', '高效处理日常工作。', 'Anthropic · OpenRouter'),
    modelPreset('CLAUDE_HAIKU_4_5', 'Claude Haiku 4.5', '快速获得简洁答案。', 'Anthropic · OpenRouter'),
    modelPreset('GPT_5_6_SOL', 'GPT-5.6 Sol', '前沿能力，适合专业复杂任务。', 'OpenAI · OpenRouter'),
    modelPreset('GPT_5_6_TERRA', 'GPT-5.6 Terra', '能力与成本更均衡。', 'OpenAI · OpenRouter'),
    modelPreset('GPT_5_6_LUNA', 'GPT-5.6 Luna', '适合高频、轻量任务。', 'OpenAI · OpenRouter'),
    modelPreset('GEMINI_3_8_FLASH', 'Gemini 3.8 Flash', '快速处理文字、图片和文件任务。', 'Google · OpenRouter'),
    modelPreset('KIMI_K3', 'Kimi K3', '复杂分析 · Agent · 长上下文', 'Moonshot AI · OpenRouter'),
  ], nonChatCapabilities: [] },
  { providerId: 'DEEPSEEK', providerDisplayName: 'DeepSeek 官方直连', enabled: false, presetId: 'DEEPSEEK_V4_PRO', presetDisplayName: 'DeepSeek V4 Pro', credentialStored: false, revision: 0, presets: [
    modelPreset('DEEPSEEK_V4_PRO', 'DeepSeek V4 Pro', '适合深度推理与专业分析。', 'DeepSeek · 官方直连'),
    modelPreset('DEEPSEEK_V4_FLASH', 'DeepSeek V4.1 Flash', '适合快速问答、高频任务与图片理解。', 'DeepSeek · 官方直连'),
  ], nonChatCapabilities: [] },
  { providerId: 'ZHIPU', providerDisplayName: '智谱 BigModel 官方直连', enabled: false, presetId: 'GLM_5_3_FLASH', presetDisplayName: 'GLM-5.3 Flash', credentialStored: false, revision: 0, presets: [
    modelPreset('GLM_5_3', 'GLM-5.3', '适合深度推理、复杂分析与 Agent 任务。', '智谱 · 官方直连'),
    modelPreset('GLM_5_3_FLASH', 'GLM-5.3 Flash', '智谱官方直连的快速文本任务。', '智谱 · 官方直连'),
  ], nonChatCapabilities: [{ id: 'GLM_OCR', displayName: 'GLM-OCR', description: '图片与 PDF 转 Markdown · 使用同一智谱 API Key', family: '仅在左侧栏“南枫转写”中调用，不加入聊天模型选择。', chatSelectable: false }] },
  { providerId: 'QWEN', providerDisplayName: 'Qwen 官方直连', enabled: false, presetId: 'QWEN_3_7_PLUS', presetDisplayName: 'Qwen3.7-Plus', credentialStored: false, revision: 0, presets: [
    modelPreset('QWEN_3_7_PLUS', 'Qwen3.7-Plus', '日常问答与轻量多媒体任务。', 'Qwen · 官方直连'),
    modelPreset('QWEN_3_8_MAX', 'Qwen3.8-Max', '适合深度分析与复杂任务。', 'Qwen · 官方直连'),
    modelPreset('QWEN_3_6_FLASH', 'Qwen3.6 Flash', '适合大批量知识整理与快速检索。', 'Qwen · 官方直连'),
  ], nonChatCapabilities: [] },
]);

const pageTitles = Object.freeze({
  home: '设置', personalization: '个性化', model: '模型与联网', 'model-configuration': '模型设置',
  'conversation-cost': '费用与用量', 'context-selections': '上下文记录', diagnostics: '运行诊断',
  reminders: '提醒', conversations: '对话管理', favorites: '收藏', archived: '已归档', recycle: '回收站',
  appearance: '外观', account: 'Google 账号与同步', data: '导入与导出', privacy: '本机数据',
  'memory-overview': '记忆摘要', 'json-import-results': 'JSON 导入结果', 'zip-import-results': 'ZIP 导入结果',
  about: '关于', workspace: '工作区', development: '开发与诊断',
});

const row = ({ glyph, title, page, action, picker, value = '', dotColorId = '', muted = false, selected = false, disabled = false, data = '' }) => `<button class="android-settings-row${muted ? ' muted' : ''}${selected ? ' selected' : ''}" data-action="${action || 'open-settings-page'}" ${page ? `data-page="${page}"` : ''} ${picker ? `data-picker="${picker}"` : ''} ${data} ${disabled ? 'disabled' : ''} ${selected ? 'aria-current="page"' : ''}><span class="android-settings-row-icon">${icon(glyph, title)}</span><span>${escapeHtml(title)}</span>${dotColorId || value ? `<span class="android-settings-row-value">${dotColorId ? `<i class="android-settings-color-preview android-settings-color-preview-${escapeHtml(dotColorId)}" aria-hidden="true"></i>` : ''}${value ? `<small>${escapeHtml(value)}</small>` : ''}</span>` : ''}</button>`;
const divider = '<div class="android-settings-divider" aria-hidden="true"></div>';
const group = (title, rows) => `<section class="android-settings-group"><h2>${escapeHtml(title)}</h2><div class="android-settings-card">${rows.join(divider)}</div></section>`;

function header(page, personalizationDirty, capabilities, context) {
  if (page === 'memory-overview') {
    const memories = (context.data?.exchange?.memory || []).filter(item => (item.status || 'ACTIVE') === 'ACTIVE' && !item.deleted && (item.scope || 'GLOBAL') === 'GLOBAL' && !item.scopeId);
    const dates = memories.map(item => new Date(item.updatedAt || item.createdAt).getTime()).filter(Number.isFinite);
    const date = dates.length ? new Date(Math.max(...dates)) : null;
    const updated = date ? `${date.getFullYear()}-${String(date.getMonth()+1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}` : '';
    const action = (id, glyph, text, disabled = false, danger = false) => `<button role="menuitem" data-action="${id}" class="${danger ? 'danger' : ''}" ${disabled ? 'disabled' : ''}>${icon(glyph, text)}<span>${text}</span></button>`;
    return `<header class="android-settings-header memory-reference-header"><button data-action="settings-back" aria-label="返回上一级">${icon(icons.arrowBack, '返回')}</button><div><h1>记忆摘要</h1>${updated ? `<small>更新于 ${updated}</small>` : ''}</div><details class="memory-reference-menu"><summary aria-label="记忆摘要菜单">${icon(icons.moreVertical, '更多')}</summary><div role="menu">${action('show-memory-summary-about', settingsIcons.memoryInfo, '关于记忆')}${action('edit-memory-summary', settingsIcons.memoryEdit, '编辑摘要')}${action('refresh-memory-summary', settingsIcons.memoryRefresh, '刷新摘要')}${action('ask-delete-memory-summary', settingsIcons.memoryDelete, '删除记忆', !memories.length, true)}${action('ask-disable-memory-summary', settingsIcons.memoryDelete, '关闭记忆摘要生成和应用', !context.settings.memoryEnabled, true)}</div></details></header>`;
  }
  const nested = ['model-configuration', 'conversation-cost', 'context-selections', 'diagnostics', 'favorites', 'archived', 'recycle', 'memory-overview', 'json-import-results', 'zip-import-results'].includes(page);
  const personalizationAvailable = Boolean(capabilities?.ordinaryChatPersonalization);
  return `<header class="android-settings-header">${nested ? `<button data-action="settings-back" aria-label="返回上一级">${icon(icons.chevronLeft, '返回')}</button>` : '<span></span>'}<h1>${escapeHtml(pageTitles[page] || '设置')}</h1>${page === 'personalization' ? `<button class="android-settings-save" data-action="save-personalization" ${personalizationDirty && personalizationAvailable ? '' : 'disabled'} aria-label="保存个性化设置">${icon(icons.check, '保存')}</button>` : '<span></span>'}</header>`;
}

function home({ appearance, page, capabilities }) {
  const color = THEME_COLORS.find(item => item.id === appearance.themeColor) || THEME_COLORS[0];
  const mode = APPEARANCE_MODES.find(item => item.id === appearance.mode)?.label || '系统（默认）';
  const font = FONT_SIZES.find(item => item.id === appearance.fontSize)?.label || '标准';
  return `<div class="android-settings-home">
    ${group('对话', [
      row({ glyph: settingsIcons.person, title: '个性化', page: 'personalization', selected: page === 'personalization' }),
      row({ glyph: settingsIcons.hub, title: '模型与联网', page: 'model', selected: page === 'model' || ['model-configuration','conversation-cost','context-selections','diagnostics'].includes(page) }),
      row({ glyph: settingsIcons.bell, title: '提醒', page: 'reminders', selected: page === 'reminders' }),
      row({ glyph: settingsIcons.conversation, title: '对话管理', page: 'conversations', selected: page === 'conversations' || ['favorites','archived','recycle'].includes(page) }),
    ])}
    ${group('外观', [
      row({ glyph: settingsIcons.appearance, title: '外观', action: 'open-settings-picker', picker: 'mode', value: mode }),
      row({ glyph: settingsIcons.type, title: '字体大小', action: 'open-settings-picker', picker: 'fontSize', value: font }),
      row({ glyph: settingsIcons.palette, title: '主题色', action: 'open-settings-picker', picker: 'themeColor', value: color.label, dotColorId: color.id }),
    ])}
    ${group('数据管理', [
      row({ glyph: settingsIcons.account, title: 'Google 账号与同步', page: 'account', muted: !capabilities.googleAccountSync, selected: page === 'account' }),
      row({ glyph: settingsIcons.importExport, title: '导入与导出', page: 'data', muted: true, selected: page === 'data' }),
      row({ glyph: settingsIcons.data, title: '本机数据', page: 'privacy', muted: true, selected: page === 'privacy' }),
      row({ glyph: settingsIcons.info, title: '关于', page: 'about', muted: true, selected: page === 'about' }),
    ])}
    ${group('工作区', [
      row({ glyph: settingsIcons.folder, title: '项目与知识', page: 'workspace', selected: page === 'workspace' }),
      row({ glyph: settingsIcons.tune, title: '开发与诊断', page: 'development', selected: page === 'development' }),
    ])}
  </div>`;
}

const switchRow = (title, summary, key, checked, disabled = false) => `<div class="android-settings-switch-row"><div><strong>${escapeHtml(title)}</strong>${summary ? `<p>${escapeHtml(summary)}</p>` : ''}</div><button type="button" class="android-settings-switch" data-action="toggle-product-setting" data-key="${key}" role="switch" aria-checked="${checked}" ${disabled ? 'disabled' : ''}><span></span></button></div>`;
const modelRecordEntry = (title, detail, page, glyph) => `<button class="android-settings-model-record-entry" data-action="open-settings-page" data-page="${page}"><span class="android-settings-row-icon">${icon(glyph, title)}</span><span><strong>${escapeHtml(title)}</strong><small>${escapeHtml(detail)}</small></span>${icon(icons.chevronRight, `进入${title}`)}</button>`;

function personalization({ settings, draft, capabilities }) {
  const tone = conversationToneDefinition(draft.tone);
  const unavailable = !capabilities.ordinaryChatPersonalization;
  return `<div class="android-settings-page android-settings-personalization">
    ${unavailable ? '<section class="android-settings-card android-settings-status-card"><strong>Desktop 普通发送尚未接入个性化上下文</strong><p>已有值保留在 Desktop SQLite，但当前本机对话只保存文本，不会调用模型或应用这些设置。</p></section>' : ''}
    ${switchRow('启用记忆', '', 'memoryEnabled', settings.memoryEnabled, unavailable)}
    <p class="android-settings-helper">允许 南枫AI 根据你的聊天、文件和已关联的应用为你提供个性化体验。</p>
    ${switchRow('历史资料库', '', 'historyLibraryEnabled', settings.historyLibraryEnabled, unavailable || !capabilities.historyLibrary)}
    <p class="android-settings-helper">低频整理有价值的历史对话，并在后续对话优先调用少量相关资料；原对话、附件和工具内容不发送。</p>
    <button class="android-settings-value-row" data-action="open-settings-picker" data-picker="tone" ${unavailable ? 'disabled' : ''}><span>基础风格和语气</span><small>${escapeHtml(tone.label)}</small></button>
    <p class="android-settings-helper">当前风格会用于每次普通对话；只改变表达方式，不改变模型、联网、记忆或资料库功能。</p>
    <button class="android-settings-value-row" data-action="open-settings-page" data-page="memory-overview"><span>记忆摘要</span><small>›</small></button>
    <p class="android-settings-helper">查看 南枫AI 对你的了解概览。如果你希望它持续记住某些信息，可以使用下方 自定义指令。</p>
    <label class="android-settings-field"><span>你的昵称</span><input id="personalization-nickname" maxlength="80" placeholder="例如：南烛枫" value="${escapeHtml(draft.nickname)}" ${unavailable ? 'disabled' : ''}></label>
    <label class="android-settings-field"><span>你的职业</span><input id="personalization-occupation" maxlength="120" placeholder="工程师、学生等" value="${escapeHtml(draft.occupation)}" ${unavailable ? 'disabled' : ''}></label>
    <label class="android-settings-field android-settings-field-long"><span class="android-settings-field-heading"><span>自定义指令</span><button type="button" class="android-settings-fullscreen-editor" data-action="open-custom-instructions-fullscreen" aria-label="全屏编辑自定义指令" title="全屏编辑" ${unavailable ? 'disabled' : ''}>${icon(icons.openInFull, '全屏编辑')}</button></span><textarea id="personalization-instructions" maxlength="${CUSTOM_INSTRUCTIONS_MAX_LENGTH}" rows="5" placeholder="希望南枫 AI 如何回答你" ${unavailable ? 'disabled' : ''}>${escapeHtml(draft.customInstructions)}</textarea><small>${draft.customInstructions.length} / ${CUSTOM_INSTRUCTIONS_MAX_LENGTH} 字</small></label>
    <p class="android-settings-helper">你的 自定义指令 将用于所有南枫AI的对话。</p>
  </div>`;
}

function remindersPage(context) {
  const { settings, capabilities, reminders: projection = { drafts: [], plans: [], diagnostics: [] }, reminderNotificationPermission = 'default', reminderNotificationBridge: bridge = {}, backgroundRuntime = {} } = context;
  const reminderNotificationPermissionReady = ['granted', 'legacy'].includes(reminderNotificationPermission);
  return `<div class="android-settings-page"><section class="android-settings-card android-settings-reminders">
    ${switchRow('计划监控结果通知', capabilities.monitorNotifications ? (settings.monitorNotifications ? '监控完成后可发送系统通知；仍需在系统中允许通知' : '已关闭：监控仍会执行，结果只保存在本机任务列表') : 'Desktop 尚无系统通知与计划监控消费者；已保留设置值，不伪造通知', 'monitorNotifications', capabilities.monitorNotifications && settings.monitorNotifications, !capabilities.monitorNotifications)}
    ${capabilities.monitorNotifications && !reminderNotificationPermissionReady ? `<button class="android-settings-value-row" data-action="request-reminder-notification-permission"><span>系统通知权限</span><small>${reminderNotificationPermission === 'denied' ? '已拒绝，请在系统设置中开启' : '点按授权 ›'}</small></button>` : ''}
    <div class="android-settings-thin-divider"></div>
    ${switchRow('对话提醒建议', capabilities.reminderSuggestions ? (settings.reminderSuggestions ? '仅少量高价值对话末尾显示“添加提醒 / 监控”' : '已关闭：不再显示对话尾部的提醒建议') : 'Desktop 普通对话尚无提醒建议 owner', 'reminderSuggestions', capabilities.reminderSuggestions && settings.reminderSuggestions, !capabilities.reminderSuggestions)}
    <div class="android-settings-thin-divider"></div>
    ${switchRow('对话未读提醒', capabilities.unreadIndicators ? (settings.unreadIndicators ? '在左侧对话列表显示主题色未读标记，不发送系统通知' : '已关闭自动未读提示；手动标为未读仍显示主题色标记') : 'Desktop 尚无未读水位与列表消费者', 'unreadIndicators', capabilities.unreadIndicators && settings.unreadIndicators, !capabilities.unreadIndicators)}
  </section></div>`;
}

function accountSyncPage(context) {
  const account = context.accountSync || {};
  const recovery = context.accountRecovery;
  const signedIn = Boolean(account.email);
  const ready = signedIn && !['NOT_CONFIGURED', 'SIGNED_OUT', 'SIGNED_OUT_KEEP_LOCAL'].includes(account.state);
  const stateLabels = {
    NOT_CONFIGURED: '服务未配置', SIGNED_OUT: '未登录', SIGNED_OUT_KEEP_LOCAL: '已退出，本机数据保留',
    DIRECTION_REQUIRED: '待选择同步方向', READY: '已就绪',
    SYNCING: '正在同步', VERIFYING: '正在核对云端提交', CONFLICT: '存在版本冲突', FAILED: '同步需要处理',
  };
  const stateLabel = stateLabels[account.state] || account.state || '未读取';
  const accountStatusCard = (title, description, actions) => `<section class="android-settings-card android-settings-status-card android-account-status-card"><div class="android-account-status-content"><strong>${title}</strong><p>${description}</p></div><div class="android-account-status-actions">${actions}</div></section>`;
  const identity = signedIn
    ? `<section class="android-settings-card android-account-hero signed-in"><div class="android-account-identity">${account.avatarDataUrl ? `<img src="${escapeHtml(account.avatarDataUrl)}" alt="Google 账号头像">` : `<span aria-hidden="true">${escapeHtml((account.displayName || account.email || '南').slice(0, 1))}</span>`}</div><strong>${escapeHtml(account.displayName || 'Google 用户')}，您好！</strong><p>${escapeHtml(account.email)}</p></section>`
    : `<section class="android-settings-card android-account-hero"><span class="android-account-hero-icon" aria-hidden="true">${icon(icons.account, 'Google 账号')}</span><strong>未登录</strong><p>${account.configured ? '登录后可管理账号与已选对话同步。' : '此 Desktop 尚未写入南枫云地址或公开访问密钥；不会打开浏览器、读取账号或上传数据。'}</p><button class="primary" data-action="${account.configured ? 'sign-in-google-account' : 'show-google-login-requirements'}" ${!context.native ? 'disabled' : ''}>${account.configured ? '使用 Google 登录' : '查看 Google 登录条件'}</button></section>`;
  const accountManagement = `<section class="android-settings-card android-account-management"><strong>账号管理</strong><button data-action="sign-in-google-account" ${signedIn && context.native ? '' : 'disabled'}>${icon(settingsIcons.syncAlt, '切换 Google 账号')}<span>切换 Google 账号</span></button><button data-action="sign-out-google-account" ${signedIn && context.native ? '' : 'disabled'}>${icon(settingsIcons.accountLogout, '退出登录')}<span>退出登录</span></button></section>`;
  const syncControl = `<section class="android-settings-card android-account-conversation-sync"><div class="android-account-sync-heading">${icon(account.lastSuccessAtMs ? settingsIcons.accountCloudDone : settingsIcons.accountCloudOff, '对话同步')}<strong>对话同步</strong></div>${signedIn ? `<div class="android-account-periodic"><strong>定期同步</strong><button type="button" class="android-settings-switch" data-action="toggle-periodic-account-sync" role="switch" aria-checked="${Boolean(account.periodicEnabled)}" ${ready ? '' : 'disabled'}><span></span></button></div>` : ''}${account.lastSuccessAtMs ? `<small>上次同步 ${new Date(Number(account.lastSuccessAtMs)).toLocaleString('zh-CN')}</small>` : ''}</section>`;
  const recoveryCard = '';
  const recoveryAndCloud = signedIn ? `<section class="android-settings-card android-account-cloud-restore"><strong>云端会话</strong><button data-action="load-account-cloud-documents">${icon(icons.cloudDownloadSolid, '读取云端列表')}<span>读取云端列表</span></button></section>` : '';
  return `<div class="android-settings-page android-account-sync-page">
    ${identity}
    ${accountManagement}
    ${account.state === 'DIRECTION_REQUIRED' ? accountStatusCard('选择首次同步方向', '以本机显式选中的对话为起点；每次上传前仍会回读云端。若云端已有未知版本，立即转为冲突，不覆盖。', '<button class="primary" data-action="choose-selected-local-sync-start">以本机所选对话开始</button>') : ''}
    ${syncControl}
    ${recoveryAndCloud}
    ${recoveryCard}
    ${account.pendingUnknown ? accountStatusCard('上次提交结果未知', '先只读回读云端版本；不会直接重复上传。', `<button data-action="reconcile-account-sync" data-workspace-id="${escapeHtml(account.pendingUnknown.workspaceId)}" data-conversation-id="${escapeHtml(account.pendingUnknown.conversationId)}">核对云端结果</button>`) : ''}
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
    <section class="android-settings-card android-settings-model-provider-row"><strong>${escapeHtml(selected.providerDisplayName)}</strong><button type="button" class="android-settings-switch" data-action="toggle-model-service-enabled" role="switch" aria-checked="${Boolean(draft.enabled)}" ${busy ? 'disabled' : ''}><span></span></button></section>
    <button class="android-settings-model-preset" data-action="open-settings-picker" data-picker="modelPreset" ${busy ? 'disabled' : ''}><span><strong>${escapeHtml(preset?.displayName || draft.presetDisplayName || '')}</strong><small>${escapeHtml(preset?.family || '')}</small></span>${icon(icons.chevronDown, '展开模型预设')}</button>
    <button class="android-settings-model-test" data-action="test-model-service-connection" ${busy || !selected.credentialStored ? 'disabled' : ''}>${context.modelSettingsTesting ? '<span class="android-settings-spinner" aria-hidden="true"></span>正在测试…' : '测试连接'}</button>
    ${context.modelSettingsTesting ? '<p class="android-settings-model-feedback">正在检查连接，请稍候。</p>' : ''}
    ${selected.credentialStatus === 'Unavailable' ? '<p class="android-settings-model-feedback error">应用私有凭据记录无法读取；请重新保存该服务商 API Key。</p>' : ''}
    <label class="android-settings-field android-settings-model-key"><span>API Key</span><div><span aria-hidden="true">${icon(icons.key, '')}</span><input id="model-service-api-key" type="${context.modelCredentialVisible ? 'text' : 'password'}" autocomplete="off" spellcheck="false" value="${escapeHtml(keyValue)}" ${busy ? 'disabled' : ''}><button data-action="reveal-model-service-credential" aria-label="${context.modelCredentialVisible ? '隐藏 API Key' : '显示 API Key'}" ${busy || (!selected.credentialStored && !context.modelCredentialDraft) ? 'disabled' : ''}>${icon(context.modelCredentialVisible ? icons.visibilityOff : icons.visibility, context.modelCredentialVisible ? '隐藏 API Key' : '显示 API Key')}</button></div></label>
    <div class="android-settings-model-save"><button data-action="save-model-service-settings" ${busy ? 'disabled' : ''}>${context.modelSettingsSaving ? '<span class="android-settings-spinner" aria-hidden="true"></span>正在保存…' : '保存'}</button></div>
  </div>`;
}

function importResultCard(title, task, kind) {
  if (!task) return '';
  const imported = Number(task.importedCount ?? task.confirmedCount ?? task.items?.filter(item => ['COMMITTED', 'CONFIRMED'].includes(item.status)).length ?? 0);
  const failed = Number(task.failedCount ?? task.items?.filter(item => item.status === 'FAILED').length ?? 0);
  const skipped = Number(task.skippedCount ?? task.items?.filter(item => item.status === 'SKIPPED').length ?? 0);
  return `<section class="android-settings-card android-settings-import-result"><strong>${escapeHtml(title)}</strong><p>${escapeHtml(task.status || '已记录')} · 已导入 ${imported} · 失败 ${failed} · 跳过 ${skipped}</p>${kind === 'zip' ? `<p class="android-settings-helper">按 ChatGPT / Claude 官方身份去重；同一导出内容不会重复导入。你主动删除的对话会保留删除标记，以后再导入也不会复活。</p><div class="android-settings-result-actions"><button data-action="retry-p6k-zip">重试</button><button data-action="skip-p6k-zip-failures" ${failed ? '' : 'disabled'}>跳过失败项</button><button class="danger" data-action="delete-p6k-zip-batch">删除导入批次</button></div>` : ''}</section>`;
}

const recordCost = record => projectedCost({
  modelId: record.actualModelId || record.modelId,
  inputTokens: record.inputTokens,
  outputTokens: record.outputTokens,
  cachedInputTokens: record.cachedInputTokens,
  occurredAtMs: record.occurredAtMs,
  chargeMicros: record.chargeMicros,
  currencyCode: record.currencyCode,
  costSource: record.costSource,
});

const usageAmount = (records, source = null) => {
  const costs = records.map(recordCost).filter(Boolean).filter(cost => !source || cost.costSource === source);
  if (!costs.length) return '金额未知';
  const cnyMicros = costs.reduce((total, cost) => total + Math.round(cost.chargeMicros * (cost.currencyCode === 'CNY' ? 1 : cost.currencyCode === 'USD' ? 6.720309145556033 : 0)), 0);
  const costSource = costs.every(cost => cost.costSource === 'LOCAL_ESTIMATE') ? 'LOCAL_ESTIMATE' : 'PROVIDER_RESPONSE';
  return cnyCostLabel({ chargeMicros: cnyMicros, currencyCode: 'CNY', costSource }, { maximumFractionDigits: 6 }) || '金额未知';
};

const localDateTime = value => {
  const time = Number(value || 0);
  return time > 0 ? new Date(time).toLocaleString('zh-CN', { hour12: false }) : '时间未记录';
};

const elapsedTime = value => {
  const millis = Number(value);
  if (!Number.isFinite(millis) || millis < 0) return '耗时未记录';
  if (millis < 1000) return `耗时 ${Math.round(millis)} 毫秒`;
  return `耗时 ${(millis / 1000).toFixed(millis % 1000 === 0 ? 0 : 2)} 秒`;
};

function usageLedgerPage(context) {
  const records = context.usageLedger?.records || [];
  if (!records.length) return `<div class="android-settings-page android-settings-ledger-empty">${icon(icons.data, '')}<strong>尚无可用费用记录</strong><p>已返回输入和输出 Token 的调用会显示费用与用量。</p></div>`;
  const sections = [['conversation', '会话'], ['title', '会话标题整理'], ['history', '历史资料整理'], ['ocr', '南枫转写']];
  const selected = context.usageSection || 'conversation';
  const category = record => record.entryId?.startsWith('usage-reminder-') ? 'reminder' : record.entryId?.includes('history') ? 'history' : record.entryId?.includes('title') ? 'title' : record.entryId?.includes('transcription') ? 'ocr' : 'conversation';
  const rows = records.filter(record => category(record) === selected);
  const automaticRows = records.filter(record => category(record) === 'reminder');
  const ledgerRow = (record, fallbackTitle, preferFallback = false) => {
    const conversation = (context.data?.exchange?.conversations || []).find(item => item.id === record.conversationId);
    const cost = recordCost(record);
    const hasProviderUsage = Number.isSafeInteger(record.inputTokens) && Number.isSafeInteger(record.outputTokens);
    const amount = cnyCostLabel(cost, { maximumFractionDigits: 6 }) || '金额未知';
    const usage = hasProviderUsage
      ? `输入 ${record.inputTokens.toLocaleString('zh-CN')} / 输出 ${record.outputTokens.toLocaleString('zh-CN')} Token`
      : 'Token 用量未返回';
    return `<section class="android-settings-card android-settings-ledger-row"><div><strong>${escapeHtml(preferFallback ? fallbackTitle : conversation?.title || fallbackTitle)}</strong><b>${escapeHtml(amount)}</b></div><p>${escapeHtml(record.modelId)} · ${usage}</p><small>${escapeHtml(localDateTime(record.occurredAtMs))}</small></section>`;
  };
  return `<div class="android-settings-page android-settings-ledger">
    <section class="android-settings-card android-settings-ledger-summary"><strong>本机累计</strong><div><span>费用</span><b>${escapeHtml(usageAmount(records))}</b></div><div><span>输入 Token</span><b>${Number(context.usageLedger?.inputTokens || 0).toLocaleString('zh-CN')}</b></div><div><span>输出 Token</span><b>${Number(context.usageLedger?.outputTokens || 0).toLocaleString('zh-CN')}</b></div></section>
    <section class="android-settings-card android-settings-ledger-grid">${sections.map(([id, label]) => { const items = records.filter(record => category(record) === id); return `<div><strong>${label}</strong><span>次数 <b>${items.length} 次</b></span><span>费用 <b>${items.length ? escapeHtml(usageAmount(items)) : '金额未知'}</b></span></div>`; }).join('')}</section>
    <div class="android-settings-ledger-segments">${sections.map(([id, label]) => `<button data-action="select-usage-section" data-section="${id}" class="${selected === id ? 'selected' : ''}">${label}</button>`).join('')}</div>
    ${rows.length ? rows.map(record => ledgerRow(record, '已删除的对话')).join('') : `<p class="android-settings-empty">还没有${escapeHtml(sections.find(([id]) => id === selected)?.[1] || '')}费用记录。</p>`}
    ${automaticRows.length ? `<section class="android-settings-automatic-usage"><h2>其他自动任务</h2><p>提醒草案与定时监控保留为独立调用事实，不混入上方四类明细。</p>${automaticRows.map(record => ledgerRow(record, '计划与提醒', true)).join('')}</section>` : ''}
  </div>`;
}

function contextSelectionsPage(context) {
  const records = (context.contextSelectionRecords || []).filter(item => item.selectedSources?.length);
  if (!records.length) return '<div class="android-settings-page"><p class="android-settings-empty align-start">还没有上下文记录。</p></div>';
  const conversations = context.data?.exchange?.conversations || [];
  return `<div class="android-settings-page">${records.map(record => `<section class="android-settings-card android-settings-context-record"><div><strong>${escapeHtml(conversations.find(item => item.id === record.conversationId)?.title || '已删除的对话')}</strong><b>${record.selectedSources.length} 项</b></div><p>${escapeHtml(record.providerLabel || record.providerId)} · ${escapeHtml(record.modelId)}</p>${record.selectedSources.slice(0, 2).map(source => `<span><strong>${escapeHtml(source.kind)}</strong><small>${escapeHtml(source.title)}</small></span>`).join('')}${record.selectedSources.length > 2 ? `<small>另有 ${record.selectedSources.length - 2} 项</small>` : ''}<footer>本轮输入约 ${Number(record.fixedInputTokens || record.budget?.fixedInputTokens || 0).toLocaleString('zh-CN')} Token · ${escapeHtml(localDateTime(record.createdAtMs))}</footer></section>`).join('')}</div>`;
}

function diagnosticsPage(context) {
  const failures = context.diagnosticRecords || [];
  const invocations = [...(context.invocationRecords || []), ...(context.reminders?.diagnostics || [])];
  return `<div class="android-settings-page android-settings-diagnostics"><section><header><div><strong>连接失败</strong><small>对话与连接测试中近 7 天的失败记录</small></div><span>${failures.length} 条</span></header>${failures.length ? failures.map(item => `<div class="android-settings-card android-settings-diagnostic-row"><strong>${escapeHtml(item.conversationTitle || '连接测试或历史未关联记录')}</strong><p>${escapeHtml(item.providerLabel || '')} · ${escapeHtml(item.modelId || '')}</p><b>${escapeHtml(item.summary || '调用失败，请查看技术详情。')}</b><small>${escapeHtml(elapsedTime(item.latencyMs))} · ${escapeHtml(localDateTime(item.createdAtMs))}</small></div>`).join('') : '<p>近 7 天没有连接失败记录。</p>'}</section><section><header><div><strong>自动与工具任务</strong><small>标题整理、历史整理和南枫转写等后台调用</small></div><span>${invocations.length} 条</span></header>${invocations.length ? invocations.map(item => `<div class="android-settings-card android-settings-diagnostic-row"><strong>${escapeHtml(item.title || item.kind || '后台调用')}</strong><p>${escapeHtml(item.summary || '')}</p><small>${escapeHtml(elapsedTime(item.latencyMs ?? item.durationMs))} · ${escapeHtml(localDateTime(item.createdAtMs ?? item.occurredAtMs))}</small></div>`).join('') : '<p>暂无自动或工具任务调用记录。</p>'}</section></div>`;
}

function simplePage(page, context) {
  const { data, favorites, runtimeInfo, settings, native, p6eAcceptance, p6kTask, chatgptTask, claudeTask, modelServiceSettings } = context;
  const active = data?.exchange?.conversations || [];
  if (['conversations', 'favorites', 'archived', 'recycle'].includes(page)) return renderConversationLifecyclePage({ page, conversations: active, favoriteConversationIds: favorites });
  if (page === 'model') {
    const services = modelServiceSettings?.length ? modelServiceSettings : MODEL_SERVICE_PREVIEW_SETTINGS;
    const readiness = (providerId, label) => {
      const item = services.find(service => service.providerId === providerId);
      const ready = item?.enabled && item?.credentialStored;
      return `<span class="${ready ? 'ready' : ''}">${ready ? '●' : '○'} ${escapeHtml(label)} · ${ready ? '已连接' : '未启用'}</span>`;
    };
    const helper = settings.webSearchEnabled ? '开启后每次普通对话均检索公开网页并标注来源；可能产生服务费用' : '已关闭；普通对话不会使用网页检索';
    const webSearchAvailable = Boolean(context.capabilities.webSearch);
    const webSearchHelper = webSearchAvailable ? helper : 'Desktop 普通发送尚未建立网页检索执行器；开关值已保留，当前不会发起网络请求';
    const primaryEntry = `<button class="android-settings-model-primary-entry" data-action="open-settings-page" data-page="model-configuration"><span class="android-settings-model-primary-icon">${icon(icons.key, '模型设置')}</span><span><strong>模型设置</strong><small>OpenRouter、Qwen、DeepSeek、智谱的 API Key 与模型</small></span>${icon(icons.chevronRight, '进入模型设置')}</button>`;
    const records = `<section class="android-settings-model-records" aria-label="调用记录"><h2>调用记录</h2><div class="android-settings-card">${modelRecordEntry('费用与用量', '统计对话、标题、历史与南枫转写的 Token 与金额', 'conversation-cost', icons.data)}${divider}${modelRecordEntry('上下文记录', '查看所有回答参考了什么', 'context-selections', icons.info)}${divider}${modelRecordEntry('运行诊断', '查看调用耗时、失败原因和连接问题', 'diagnostics', icons.settings)}</div></section>`;
    return `<div class="android-settings-page android-settings-model-overview"><div class="android-settings-provider-list">${readiness('OPENROUTER', 'OpenAI / Claude / Gemini')}${readiness('QWEN', 'Qwen')}${readiness('DEEPSEEK', 'DeepSeek')}${readiness('ZHIPU', '智谱 GLM')}</div><div class="android-settings-web-search"><div>${icon(icons.globe, '')}<strong>实时网页搜索</strong><button type="button" class="android-settings-switch" data-action="toggle-product-setting" data-key="webSearchEnabled" role="switch" aria-checked="${settings.webSearchEnabled}" ${webSearchAvailable ? '' : 'disabled'}><span></span></button></div><p>${escapeHtml(webSearchHelper)}</p></div>${primaryEntry}${records}</div>`;
  }
  if (page === 'model-configuration') {
    return modelConfiguration(context);
  }
  if (page === 'conversation-cost') return usageLedgerPage(context);
  if (page === 'context-selections') return contextSelectionsPage(context);
  if (page === 'diagnostics') return diagnosticsPage(context);
  if (page === 'about') return `<div class="android-settings-page"><section class="android-settings-card android-settings-about"><div><strong>南枫 AI</strong><p>本机对话、项目与知识工作区。</p></div>${divider}<div><strong>版本信息</strong><p>Desktop 版 ${escapeHtml(runtimeInfo.version || '读取中')}</p><small>开发时间 ${escapeHtml(formatDevelopmentTime(runtimeInfo.buildEpochSeconds))}</small></div>${divider}<div><strong>开发者信息</strong><small>开发者：席瑞</small><small>联系邮箱：nanzhufeng.studio@gmail.com</small><small>源码与更新：<a href="https://github.com/nanzhufeng/Nanfeng-AI" data-action="open-source-link" data-href="https://github.com/nanzhufeng/Nanfeng-AI">GitHub · nanzhufeng/Nanfeng-AI</a></small><small>版权所有 © 2026 席瑞</small></div></section></div>`;
  if (page === 'memory-overview') {
    const memories = (data?.exchange?.memory || []).filter(item => (item.status || 'ACTIVE') === 'ACTIVE' && !item.deleted && (item.scope || 'GLOBAL') === 'GLOBAL' && !item.scopeId);
    const updatedAt = memories.map(item => item.updatedAt || item.createdAt || '').filter(Boolean).sort().at(-1);
    return `<div class="android-settings-page android-memory-summary-page">
      ${context.memorySummaryNotice ? `<p class="android-settings-helper" role="status">${escapeHtml(context.memorySummaryNotice)}</p>` : ''}
      <section class="android-memory-summary-content">${memories.map((item, index) => `<article><h2>${escapeHtml(item.title || (index === 0 ? '概览' : `记忆 ${index + 1}`))}</h2><p>${escapeHtml(item.body || '')}</p></article>`).join('') || `<p class="android-settings-empty android-memory-summary-empty">还没有记忆摘要。</p>`}</section>
    </div>`;
  }
  if (page === 'workspace') return `<div class="android-settings-page">${group('', [row({ glyph: icons.folder, title: `管理 Projects`, action: 'show-projects' }), row({ glyph: icons.knowledge, title: '管理知识库', action: 'show-knowledge' })])}</div>`;
  if (page === 'development') return `<div class="android-settings-page">${group('', [row({ glyph: icons.settings, title: '本次 Context 控制', action: 'show-connections' }), row({ glyph: icons.info, title: '离线评测', action: 'show-p8-inspect' })])}${p6eAcceptance?.enabled ? `<section class="android-settings-card android-settings-status-card"><strong>P6-E 验收维护</strong><p>仅 acceptance 启动显示；正式启动不出现。</p><button data-action="run-p6e-temporary-maintenance-acceptance">运行 23h59 / 24h 维护验收</button>${p6eAcceptance.receipt ? `<small>23h59 保留=${p6eAcceptance.receipt.retainedAt23h59} · 24h 清理=${p6eAcceptance.receipt.removedAt24h}</small>` : ''}</section>` : ''}</div>`;
  if (page === 'account') return accountSyncPage(context);
  if (page === 'data') return renderLocalDataImportExportPage(context);
  if (page === 'json-import-results') return `<div class="android-settings-page android-settings-list">${importResultCard('ChatGPT JSON', chatgptTask, 'json')}${importResultCard('Claude JSON', claudeTask, 'json') || (!chatgptTask ? '<p class="android-settings-empty">还没有导入记录。</p>' : '')}</div>`;
  if (page === 'zip-import-results') return `<div class="android-settings-page android-settings-list">${importResultCard(`${p6kTask?.provider || 'ZIP'} · 批次 1`, p6kTask, 'zip') || '<p class="android-settings-empty">还没有导入记录。</p>'}</div>`;
  if (page === 'privacy') return renderLocalDataInventoryPage(context);
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
  const pickerClass = kind === 'tone' ? 'android-settings-picker android-settings-tone-picker' : 'android-settings-picker';
  return `<div class="android-settings-picker-scrim" data-action="dismiss-settings-picker"><section class="${pickerClass}" role="dialog" aria-modal="true" aria-label="${title}" data-overlay-surface>${options.map(item => `<button data-action="select-settings-picker-option" data-picker="${kind}" data-value="${item.id}" aria-pressed="${item.id === current}">${item.accent ? `<i class="android-settings-color-preview android-settings-color-preview-${escapeHtml(item.id)}" aria-hidden="true"></i>` : ''}<span><strong>${escapeHtml(item.label)}</strong>${item.detail ? `<small>${escapeHtml(item.detail)}</small>` : ''}</span>${item.id === current ? icon(icons.check, '已选中') : ''}</button>`).join('')}</section></div>`;
}

export function renderAndroidSettingsShell({ page = 'personalization', status = '', appearance = {}, settings = {}, personalizationDraft = settings, personalizationDirty = false, picker: pickerKind = null, data, favoriteConversationIds = new Set(), runtimeInfo = {}, connection = {}, native = false, p6eAcceptance = { enabled: false, receipt: null }, p6gCatalog = null, p6kTask = null, chatgptTask = null, claudeTask = null, modelServiceSettings = MODEL_SERVICE_PREVIEW_SETTINGS, modelProviderId = 'OPENROUTER', modelServiceDraft = null, modelCredentialDraft = '', modelCredentialVisible = false, modelSettingsSaving = false, modelSettingsTesting = false, modelSettingsNotice = '', modelSettingsError = '', usageLedger = { records: [], inputTokens: 0, outputTokens: 0, cachedInputTokens: 0 }, usageSection = 'conversation', contextSelectionRecords = [], diagnosticRecords = [], invocationRecords = [], privacyInventory = { totalBytes: 0, aggregates: [] }, localBackup = {}, accountSync = {}, accountRecovery = null, capabilities = DESKTOP_SETTINGS_CAPABILITIES, reminders = { drafts: [], plans: [], diagnostics: [] }, reminderNotificationPermission = 'default', reminderNotificationBridge = {}, backgroundRuntime = {}, memorySummaryQuery = '', memorySummaryComposer = '', memorySummaryNotice = '' } = {}) {
  const normalizedSettings = normalizeProductSettings(settings);
  const context = { appearance: normalizeAppearance(appearance), settings: normalizedSettings, draft: normalizeProductSettings({ ...normalizedSettings, ...personalizationDraft }), data, favorites: favoriteConversationIds, runtimeInfo, connection, native, p6eAcceptance, p6gCatalog, p6kTask, chatgptTask, claudeTask, modelServiceSettings, modelProviderId, modelServiceDraft, modelCredentialDraft, modelCredentialVisible, modelSettingsSaving, modelSettingsTesting, modelSettingsNotice, modelSettingsError, usageLedger, usageSection, contextSelectionRecords, diagnosticRecords, invocationRecords, privacyInventory, localBackup, accountSync, accountRecovery, capabilities, reminders, reminderNotificationPermission, reminderNotificationBridge, backgroundRuntime, memorySummaryQuery, memorySummaryComposer, memorySummaryNotice };
  context.page = page;
  const content = page === 'personalization' ? personalization(context) : page === 'reminders' ? remindersPage(context) : simplePage(page, context);
  return `<main class="chat-main android-settings-main"><div class="android-settings-layout"><aside class="android-settings-primary" aria-label="设置一级菜单"><div class="android-settings-primary-header"><button data-action="show-chat" aria-label="返回应用">${icon(icons.chevronLeft, '返回应用')}</button><h1>设置</h1><span></span></div><div class="android-settings-primary-scroll" data-settings-scroll-key="settings-primary">${home(context)}</div></aside><section class="android-settings-secondary" aria-label="设置二级页面">${header(page, personalizationDirty, capabilities, context)}<div class="android-settings-scroll" data-settings-scroll-key="settings-page-${escapeHtml(page)}">${content}${status ? `<p class="android-settings-status" role="status">${escapeHtml(status)}</p>` : ''}</div></section></div>${picker(pickerKind, context)}</main>`;
}
