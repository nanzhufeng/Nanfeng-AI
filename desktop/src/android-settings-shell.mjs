import { groupRetrievalUsageRecords, usageRecordPhysicalFacts, usageTokenSummary, usageModelIdentityLabel, usageCallRouteLabel } from './usage-ledger-display.mjs';
import { MODEL_BRAND_NAMES, MODEL_SETTINGS_PURPOSES } from './model-settings-browser.mjs';
import { RELAYCORE_MODELS, relayCoreUiModelName, relayCoreModel, relayCoreCompactName } from './relaycore-models.mjs';
import { workspaceUiEnabled, chatUiRoute } from './chat-ui-policy.mjs';
import { icon, icons, settingsIcons } from './icon-source.mjs';
import { accountingPricingModelId, cnyCostLabel, projectedCost, USD_TO_CNY, CNY_REFERENCE_DATE } from './desktop-cost-estimator.mjs';
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
  if (!Number.isFinite(date.getTime())) return '未记录';
  const part = value => String(value).padStart(2, '0');
  return `${date.getFullYear()}-${part(date.getMonth() + 1)}-${part(date.getDate())} ${part(date.getHours())}:${part(date.getMinutes())}`;
};

const sourceCommitLabel = runtimeInfo => {
  const commit = String(runtimeInfo.gitCommit || '');
  if (!/^(?:[0-9a-f]{40}|[0-9a-f]{64})$/i.test(commit)) return '不可用／未嵌入';
  const worktreeState = runtimeInfo.gitWorktreeDirty === true ? ' · 含未提交改动'
    : runtimeInfo.gitWorktreeDirty === false ? '' : ' · 工作区状态不可用';
  return `${commit.slice(0, 8)}${worktreeState}`;
};

const repositoryUrl = 'https://github.com/nanzhufeng/Nanfeng-AI';
const aboutLink = (href, label, purpose) => `<a href="${escapeHtml(href)}" data-action="open-source-link" data-href="${escapeHtml(href)}" aria-label="${escapeHtml(label)}，在默认浏览器${escapeHtml(purpose)}" title="在默认浏览器${escapeHtml(purpose)}">${escapeHtml(label)}</a>`;
const aboutBuildInformation = runtimeInfo => {
  const commit = String(runtimeInfo.gitCommit || '');
  const commitLabel = `Git Commit：${sourceCommitLabel(runtimeInfo)}`;
  const baseline = /^(?:[0-9a-f]{40}|[0-9a-f]{64})$/i.test(commit)
    ? aboutLink(`${repositoryUrl}/commit/${commit}`, commitLabel, '查看提交基线') : escapeHtml(commitLabel);
  const input = String(runtimeInfo.sourceInputSha256 || '');
  const fingerprint = /^[0-9a-f]{64}$/i.test(input)
    ? `<code data-source-input-sha256="${input}" title="SHA-256 ${input}" aria-label="本次源码输入 SHA-256 ${input}">${input.slice(0, 16)}</code>` : '未记录';
  return `<div><strong>版本信息</strong><p>${aboutLink(`${repositoryUrl}/releases`, `Desktop 版 ${runtimeInfo.version || '读取中'}`, '查看发布记录')}</p>${runtimeInfo.buildProfile === 'debug' ? '<small>本地开发构建</small>' : ''}<small>构建时间 ${escapeHtml(formatDevelopmentTime(runtimeInfo.buildEpochSeconds))}</small><small>提交基线 · ${baseline}</small><small>源码输入：${fingerprint}</small></div>`;
};
const alignedBehaviorFeatureReview = () => '<p class="android-settings-helper">费用来源：建议保留。入口建议：沿用费用与用量记录，不新增常驻按键。理由：已存金额来源缺失时明确标记未确认，分组保留未知来源成员，不按当前模型或Token等级猜造服务商报告。</p><p class="android-settings-helper">后台标题整理：建议保留。聊天完成后自动整理标题，继续对话不等待标题；手动标题优先。入口建议：沿用会话标题和调用记录。理由：方便查找，不增加常驻按键。</p><p class="android-settings-helper">联网附件核对：建议保留。沿用联网开关和附件发送，原材料保持不变，已检索不等于已核实。理由：来源与证据等级更清楚，不增加重复确认。</p><p class="android-settings-helper">生成中断后的用量：建议保留。入口建议：沿用聊天和费用与用量，不增加常驻按键。理由：保留已收到的模型与计量；重开后仍标记结果未知，缺失数值保持未知，不自动重发。</p><p class="android-settings-helper">模型返回身份：建议保留。入口建议：RC 消息页脚显示已记录的返回型号；费用与用量原记录分别查看请求型号与返回型号，不新增常驻按键。理由：区分用户选择和服务商返回；未返回时不补造，响应内不一致时如实说明，所显示身份不能证明服务商的底层实际模型。</p><p class="android-settings-helper">调用线路：建议保留。入口建议：沿用费用与用量，在发送前冻结服务商与明确渠道，不新增常驻按键。理由：区分请求线路与下游实际模型；历史未记录时保持未知。</p><p class="android-settings-helper">备份费用记录：建议保留。入口建议：沿用本机备份与恢复，在导出和恢复预检中检查每次调用的原线路和记录格式。理由：有误时保留当前数据与备份原件，旧记录缺失信息不补猜，不新增常驻按键。</p><p class="android-settings-helper">源文本阅读：建议保留。入口建议：沿用附件预览，支持已导入的XML、YAML、HTML原文；文件菜单和按键不变。理由：保留标签与缩进，内容不执行、不加载外链，不新增常驻按钮。</p>';

const presetNameForUser = preset => {
  const relay = relayCoreModel(preset?.id || preset?.presetId);
  return relay ? `${relayCoreUiModelName(relay)} · RC` : preset?.displayName || '';
};
const argonFeatureReview = () => `${group('新增模型', [row({ glyph: icons.settings, title: '建议保留：Gemini 4 Argon', page: 'model-configuration', value: '按对齐方案接入' })])}<p class="android-settings-helper">入口建议：模型设置、深度分组。理由：复用 OpenRouter 线路与原 Key；已核验缓存未收录时不可选，费用区分服务端金额与估算。</p>`;
const modelPreset = (id, displayName, description, family) => ({ id, displayName, description, family, chatSelectable: true });
export const MODEL_SERVICE_PREVIEW_SETTINGS = Object.freeze([
  { providerId: 'OPENROUTER', providerDisplayName: 'OpenRouter', enabled: false, presetId: 'GPT_6_LUNA', presetDisplayName: 'GPT-6 Luna', credentialStored: false, revision: 0, presets: [
    modelPreset('CLAUDE_OPUS_5_5', 'Claude Opus 5.5', '适合复杂任务与深度推理。', 'Anthropic · OpenRouter'),
    modelPreset('CLAUDE_SONNET_5_5', 'Claude Sonnet 5.5', '高效处理日常工作。', 'Anthropic · OpenRouter'),
    modelPreset('CLAUDE_HAIKU_5_5', 'Claude Haiku 5.5', '快速获得简洁答案。', 'Anthropic · OpenRouter'),
    modelPreset('GPT_6_1_SOL', 'GPT-6.1 Sol', '前沿能力，适合专业复杂任务。', 'OpenAI · OpenRouter'),
    modelPreset('GPT_6_1_TERRA', 'GPT-6.1 Terra', '适合日常工作、图像理解与代码任务。', 'OpenAI · OpenRouter'),
    modelPreset('MERCURY_3', 'Mercury 3', '适合快速文字问答、代码与联网检索。', 'Inception · OpenRouter'),
    modelPreset('GPT_6_LUNA', 'GPT-6 Luna', '能力与成本更均衡。', 'OpenAI · OpenRouter'),
    modelPreset('GEMINI_4_ARGON', 'Gemini 4 Argon', '适合深度推理、复杂分析与代码任务。', 'Google · OpenRouter'),
    modelPreset('MUSE_WONDER', 'Muse Wonder', '适合复杂分析与长程任务。', 'Meta · OpenRouter'),
    modelPreset('GEMINI_3_8_FLASH', 'Gemini 3.8 Flash', '快速处理文字、图片和文件任务。', 'Google · OpenRouter'),
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
  ...['RELAYCORE_CLAUDE', 'RELAYCORE_GPT', 'RELAYCORE_GEMINI'].map(providerId => {
    const models = RELAYCORE_MODELS.filter(model => model.providerId === providerId);
    const selected = models.find(model => model.tier === 'DAILY') || models[0];
    return { providerId, providerDisplayName: `RelayCore ${providerId.split('_')[1]} · ${selected.channel}`, enabled: false,
      presetId: selected.presetId, presetDisplayName: relayCoreCompactName(selected), credentialStored: false, revision: 0,
      presets: models.map(model => modelPreset(model.presetId, relayCoreCompactName(model), '平台列出，返回身份以响应为准。', 'RelayCore')),
      nonChatCapabilities: [] };
  }),
]);

const pageTitles = Object.freeze({
  home: '设置', personalization: '个性化', model: '模型与联网', 'model-configuration': '模型设置',
  'conversation-cost': '费用与用量', 'context-selections': '上下文记录', diagnostics: '运行诊断',
  reminders: '提醒', conversations: '对话管理', favorites: '收藏', archived: '已归档', recycle: '回收站',
  appearance: '外观', account: 'Google 账号与同步', data: '导入与导出', privacy: '本机数据',
  'memory-overview': '记忆摘要', 'json-import-results': 'JSON 导入结果', 'zip-import-results': 'ZIP 导入结果',
  about: '关于', workspace: '工作区', development: '开发与诊断', 'feature-review': '功能审阅',
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
    return `<header class="android-settings-header memory-reference-header"><button data-action="settings-back" aria-label="返回上一级">${icon(icons.arrowBack, '返回')}</button><div><h1>记忆摘要</h1>${updated ? `<small>更新于 ${updated}</small>` : ''}</div><details class="memory-reference-menu"><summary aria-label="记忆摘要菜单">${icon(icons.moreVertical, '更多')}</summary><div role="menu">${action('show-memory-summary-about', settingsIcons.memoryInfo, '关于记忆')}${action('edit-memory-summary', settingsIcons.memoryEdit, '编辑摘要')}${action('refresh-memory-summary', settingsIcons.memoryRefresh, '刷新摘要')}${action('ask-delete-memory-summary', settingsIcons.memoryDelete, '删除记忆', !memories.length, true)}${action('ask-disable-memory-summary', settingsIcons.memoryDelete, '关闭记忆摘要生成和应用', !context.settings.memoryRetrievalEnabled, true)}</div></details></header>`;
  }
  const nested = ['model-configuration', 'conversation-cost', 'context-selections', 'diagnostics', 'favorites', 'archived', 'recycle', 'memory-overview', 'json-import-results', 'zip-import-results'].includes(page);
  const personalizationAvailable = Boolean(capabilities?.ordinaryChatPersonalization);
  return `<header class="android-settings-header">${nested ? `<button data-action="settings-back" aria-label="返回上一级">${icon(icons.chevronLeft, '返回')}</button>` : '<span></span>'}<h1>${escapeHtml(pageTitles[page] || '设置')}</h1>${page === 'personalization' ? `<button class="android-settings-save" data-action="save-personalization" ${personalizationDirty && personalizationAvailable ? '' : 'disabled'} aria-label="保存个性化设置">${icon(icons.check, '保存')}</button>` : '<span></span>'}</header>`;
}

function home({ appearance, page, capabilities, runtimeInfo }) {
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
    ${group(workspaceUiEnabled(runtimeInfo) ? '工作区' : '产品', [
      ...(workspaceUiEnabled(runtimeInfo) ? [row({ glyph: settingsIcons.folder, title: '项目与知识', page: 'workspace', selected: page === 'workspace' })] : []),
      ...(workspaceUiEnabled(runtimeInfo) ? [row({ glyph: settingsIcons.tune, title: '开发与诊断', page: 'development', selected: page === 'development' })] : []),
      row({ glyph: settingsIcons.info, title: '功能审阅', page: 'feature-review', selected: page === 'feature-review' }),
    ])}
  </div>`;
}

const switchRow = (title, summary, key, checked, disabled = false) => `<div class="android-settings-switch-row"><div><strong>${escapeHtml(title)}</strong>${summary ? `<p>${escapeHtml(summary)}</p>` : ''}</div><button type="button" class="android-settings-switch" data-action="toggle-product-setting" data-key="${key}" role="switch" aria-checked="${checked}" ${disabled ? 'disabled' : ''}><span></span></button></div>`;
const modelRecordEntry = (title, detail, page, glyph) => `<button class="android-settings-model-record-entry" data-action="open-settings-page" data-page="${page}"><span class="android-settings-row-icon">${icon(glyph, title)}</span><span><strong>${escapeHtml(title)}</strong><small>${escapeHtml(detail)}</small></span>${icon(icons.chevronRight, `进入${title}`)}</button>`;

function personalization({ settings, draft, capabilities, runtimeInfo }) {
  const tone = conversationToneDefinition(draft.tone);
  const unavailable = !capabilities.ordinaryChatPersonalization;
  return `<div class="android-settings-page android-settings-personalization">
    ${unavailable ? '<section class="android-settings-card android-settings-status-card"><strong>Desktop 普通发送尚未接入个性化上下文</strong><p>已有值保留在 Desktop SQLite，但当前本机对话只保存文本，不会调用模型或应用这些设置。</p></section>' : ''}
    ${switchRow('启用记忆', '', 'memoryEnabled', settings.memoryEnabled && settings.memoryRetrievalEnabled, unavailable)}
    <p class="android-settings-helper">允许 南枫AI 根据你的聊天、文件和已关联的应用为你提供个性化体验。</p>
    ${workspaceUiEnabled(runtimeInfo) ? switchRow('历史资料库', '', 'historyLibraryEnabled', settings.historyLibraryEnabled, unavailable || !capabilities.historyLibrary) + '<p class="android-settings-helper">低频整理有价值的历史对话，并在后续对话优先调用少量相关资料；原对话、附件和工具内容不发送。</p>' : ''}
    <button class="android-settings-value-row" data-action="open-settings-picker" data-picker="tone" ${unavailable ? 'disabled' : ''}><span>基础风格和语气</span><small>${escapeHtml(tone.label)}</small></button>
    <p class="android-settings-helper">当前风格会用于每次普通对话；只改变表达方式，不改变模型、联网、记忆或资料库功能。</p>
    <button class="android-settings-value-row" data-action="open-settings-page" data-page="memory-overview"><span>记忆摘要</span><small>›</small></button>
    <p class="android-settings-helper">查看 南枫AI 对你的了解概览。如果你希望它持续记住某些信息，可以使用下方 自定义指令。</p>
    <label class="android-settings-field"><span>你的昵称</span><input id="personalization-nickname" data-max-codepoints="80" placeholder="例如：南烛枫" value="${escapeHtml(draft.nickname)}" ${unavailable ? 'disabled' : ''}></label>
    <label class="android-settings-field"><span>你的职业</span><input id="personalization-occupation" data-max-codepoints="120" placeholder="工程师、学生等" value="${escapeHtml(draft.occupation)}" ${unavailable ? 'disabled' : ''}></label>
    <label class="android-settings-field android-settings-field-long"><span class="android-settings-field-heading"><span>自定义指令</span><button type="button" class="android-settings-fullscreen-editor" data-action="open-custom-instructions-fullscreen" aria-label="全屏编辑自定义指令" title="全屏编辑" ${unavailable ? 'disabled' : ''}>${icon(icons.openInFull, '全屏编辑')}</button></span><textarea id="personalization-instructions" data-max-codepoints="${CUSTOM_INSTRUCTIONS_MAX_LENGTH}" rows="5" placeholder="希望南枫 AI 如何回答你" ${unavailable ? 'disabled' : ''}>${escapeHtml(draft.customInstructions)}</textarea><small>${Array.from(draft.customInstructions).length} / ${CUSTOM_INSTRUCTIONS_MAX_LENGTH} 字</small></label>
    <p class="android-settings-helper">你的 自定义指令 将用于所有南枫AI的对话。</p>
  </div>`;
}

function remindersPage(context) {
  const { settings, capabilities, reminders: projection = { drafts: [], plans: [], diagnostics: [] }, reminderNotificationPermission = 'default', reminderNotificationBridge: bridge = {}, backgroundRuntime = {} } = context;
  const reminderNotificationPermissionReady = ['granted', 'legacy'].includes(reminderNotificationPermission);
  const backgroundRemovalFailed = backgroundRuntime.safeCode === 'REMOVE_FAILED';
  const backgroundStatus = backgroundRemovalFailed
    ? '未能确认系统后台任务已撤销；它可能仍会唤醒应用。请检查系统任务权限。'
    : backgroundRuntime.desiredEnabled
      ? backgroundRuntime.installed
        ? '系统后台任务已注册。'
        : '系统后台任务未注册；关闭应用后提醒不会按计划执行。请检查系统任务权限。'
      : '';
  return `<div class="android-settings-page"><section class="android-settings-card android-settings-reminders">
    ${backgroundStatus ? `<p class="android-settings-helper android-settings-background-status${backgroundRemovalFailed || !backgroundRuntime.installed ? ' error' : ''}" role="status">${backgroundStatus}</p>` : ''}
    ${switchRow('计划监控结果通知', capabilities.monitorNotifications ? (settings.monitorNotifications ? '监控完成后可发送系统通知；仍需在系统中允许通知' : '已关闭：监控仍会执行，结果只保存在本机任务列表') : 'Desktop 尚无系统通知与计划监控消费者；已保留设置值，不伪造通知', 'monitorNotifications', capabilities.monitorNotifications && settings.monitorNotifications, !capabilities.monitorNotifications)}
    ${capabilities.monitorNotifications && !reminderNotificationPermissionReady ? `<button class="android-settings-value-row" data-action="request-reminder-notification-permission"><span>系统通知权限</span><small>${reminderNotificationPermission === 'denied' ? '已拒绝，请在系统设置中开启' : reminderNotificationPermission === 'unavailable' ? '权限状态暂不可用，点按重试 ›' : '点按授权 ›'}</small></button>` : ''}
    <div class="android-settings-thin-divider"></div>
    ${switchRow('对话提醒建议', capabilities.reminderSuggestions ? (settings.reminderSuggestions ? '仅少量高价值对话末尾显示“添加提醒 / 监控”' : '已关闭：不再显示对话尾部的提醒建议') : 'Desktop 普通对话尚无提醒建议 owner', 'reminderSuggestions', capabilities.reminderSuggestions && settings.reminderSuggestions, !capabilities.reminderSuggestions)}
    <div class="android-settings-thin-divider"></div>
    ${switchRow('对话未读提醒', capabilities.unreadIndicators ? (settings.unreadIndicators ? '在左侧对话列表显示主题色未读标记，不发送系统通知' : '已关闭自动未读提示；手动标为未读仍显示主题色标记') : 'Desktop 尚无未读水位与列表消费者', 'unreadIndicators', capabilities.unreadIndicators && settings.unreadIndicators, !capabilities.unreadIndicators)}
  </section></div>`;
}

function accountSyncPage(context) {
  const account = context.accountSync || {};
  const signedIn = Boolean(account.email);
  const signingIn = Boolean(account.signInPending);
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
    : `<section class="android-settings-card android-account-hero"><span class="android-account-hero-icon" aria-hidden="true">${icon(icons.account, 'Google 账号')}</span><strong>未登录</strong><p>${account.configured ? '登录后可管理账号与已选对话同步。' : '此 Desktop 尚未写入南枫云地址或公开访问密钥；不会打开浏览器、读取账号或上传数据。'}</p><button class="primary" data-action="${account.configured ? 'sign-in-google-account' : 'show-google-login-requirements'}" ${!context.native || signingIn ? 'disabled' : ''}>${signingIn ? '等待 Google 授权…' : account.configured ? '使用 Google 登录' : '查看 Google 登录条件'}</button></section>`;
  const accountManagement = `<section class="android-settings-card android-account-management"><strong>账号管理</strong><button data-action="sign-in-google-account" ${signedIn && context.native && !signingIn ? '' : 'disabled'}>${icon(settingsIcons.syncAlt, '切换 Google 账号')}<span>切换 Google 账号</span></button><button data-action="sign-out-google-account" ${signedIn && context.native ? '' : 'disabled'}>${icon(settingsIcons.accountLogout, '退出登录')}<span>退出登录</span></button>${signingIn ? '<button data-action="cancel-google-account-sign-in">取消本次授权等待</button>' : ''}</section>`;
  const syncControl = `<section class="android-settings-card android-account-conversation-sync"><div class="android-account-sync-heading">${icon(account.lastSuccessAtMs ? settingsIcons.accountCloudDone : settingsIcons.accountCloudOff, '对话同步')}<strong>对话同步</strong></div><p>仅同步明确选择的对话。同步内容可由南枫云服务读取；传输保护与账号隔离不等于端到端加密。</p>${signedIn ? `<div class="android-account-periodic"><strong>定期同步</strong><button type="button" class="android-settings-switch" data-action="toggle-periodic-account-sync" role="switch" aria-checked="${Boolean(account.periodicEnabled)}" ${ready ? '' : 'disabled'}><span></span></button></div>` : ''}${account.lastSuccessAtMs ? `<small>上次同步 ${new Date(Number(account.lastSuccessAtMs)).toLocaleString('zh-CN')}</small>` : ''}</section>`;
  const recoveryAndCloud = signedIn ? `<section class="android-settings-card android-account-cloud-restore"><strong>云端会话</strong><button data-action="load-account-cloud-documents">${icon(icons.cloudDownloadSolid, '读取云端列表')}<span>读取云端列表</span></button></section>` : '';
  return `<div class="android-settings-page android-account-sync-page">
    ${identity}
    ${accountManagement}
    ${account.state === 'DIRECTION_REQUIRED' ? accountStatusCard('选择首次同步方向', '以本机显式选中的对话为起点；每次上传前仍会回读云端。若云端已有未知版本，立即转为冲突，不覆盖。', '<button class="primary" data-action="choose-selected-local-sync-start">以本机所选对话开始</button>') : ''}
    ${syncControl}
    ${recoveryAndCloud}
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
  const registry = context.openrouterRegistryStatus;
  const verified = registry?.status === 'VERIFIED';
  const lastGood = registry?.status === 'STABLE_FALLBACK';
  const registrySummary = verified ? '资料已更新' : lastGood ? '使用上次更新的资料' : '使用内置资料';
  const registryTime = Number(registry?.verifiedAtMs);
  const registryDetails = (verified || lastGood) && Number.isFinite(registryTime) && registryTime > 0
    ? `更新于 ${new Date(registryTime).toLocaleString('zh-CN')}` : '';
  const relaySelected = selected.providerId.startsWith('RELAYCORE_');
  const platformServices = ['OPENROUTER', 'RELAYCORE_CLAUDE', 'DEEPSEEK', 'ZHIPU', 'QWEN'].map(id => services.find(item => item.providerId === id)).filter(Boolean);
  const brand = id => `<img class="model-provider-brand" data-model-brand="${MODEL_BRAND_NAMES[id]}" src="./assets/model-brands/${MODEL_BRAND_NAMES[id]}.png" alt="" width="20" height="20">`;
  const providerButton = (item, sub = false) => {
    const active = sub ? item.providerId === selected.providerId : (item.providerId === selected.providerId || (item.providerId === 'RELAYCORE_CLAUDE' && relaySelected));
    const label = sub ? item.providerId.split('_')[1].replace('GEMINI', 'Gemini').replace('CLAUDE', 'Claude') : ({ ZHIPU: '智谱', QWEN: 'Qwen', DEEPSEEK: 'DeepSeek', RELAYCORE_CLAUDE: 'RelayCore' })[item.providerId] || 'OpenRouter';
    return `<button data-action="select-model-service-provider" data-provider-id="${item.providerId}" class="${active ? 'selected' : ''}" data-provider-platform="${!sub}" ${busy ? 'disabled' : ''} role="tab" aria-selected="${active}" ${selectionBusy ? 'disabled' : ''}>${brand(sub ? item.providerId : item.providerId.startsWith('RELAYCORE_') ? 'RELAYCORE' : item.providerId)}<span>${label}</span></button>`;
  };
  const presets = [...(selected.presets || []).filter(item => item.chatSelectable !== false), ...(selected.nonChatCapabilities || []).filter(item => item.id === 'GLM_OCR')].sort((a,b) => selected.providerId === 'RELAYCORE_CLAUDE' ? Number(b.id === 'RELAYCORE_SONNET_5_5') - Number(a.id === 'RELAYCORE_SONNET_5_5') : 0);
  const visibleModelCapacity = relaySelected ? 5 : 6;
  const groupName = relaySelected ? `${selected.providerId.split('_')[1].replace('CLAUDE', 'Claude').replace('GEMINI', 'Gemini')} 分组` : selected.providerDisplayName;
  const unsaved = context.modelCredentialEdited;
  const verifiedPresetIds = ['VERIFIED', 'STABLE_FALLBACK'].includes(registry?.status) ? new Set(registry.mappedPresetIds || []) : null;
  const presetUnavailable = item => selected.providerId === 'OPENROUTER' && verifiedPresetIds && !verifiedPresetIds.has(item.id);
  const selectionBusy = false;
  return `<div class="android-settings-page android-settings-model-configuration">
    <h3 class="model-settings-section-title">服务商与模型</h3>
    <section class="model-settings-browser" data-model-count="${presets.length}">
      <div class="model-settings-platform-rail" role="tablist" aria-orientation="vertical" aria-label="模型平台">${platformServices.map(item => providerButton(item)).join('')}</div>
      <div class="model-settings-model-pane">
        ${relaySelected ? `<div class="android-settings-segments android-settings-relay-tabs" role="tablist" aria-label="RelayCore 模型分组">${services.filter(item => item.providerId.startsWith('RELAYCORE_')).map(item => providerButton(item, true)).join('')}</div>` : ''}
        <div class="model-settings-model-list${presets.length > visibleModelCapacity ? ' scrollable' : ''}" role="radiogroup" aria-label="设置模型">${presets.map(item => `<button class="model-settings-model-option${item.id === draft.presetId ? ' selected' : ''}" data-action="select-model-service-preset" data-preset-id="${escapeHtml(item.id)}" ${presetUnavailable(item) ? 'disabled' : ''} role="radio" aria-checked="${item.id === draft.presetId}" ${selectionBusy ? 'disabled' : ''}><input type="radio" name="model-settings-preset" tabindex="-1" aria-hidden="true" ${item.id === draft.presetId ? 'checked' : ''}><span><strong>${escapeHtml(presetNameForUser(item))}</strong><small>${escapeHtml(item.id === 'GLM_OCR' ? '图片、PDF 转 Markdown · 共用智谱 Key' : MODEL_SETTINGS_PURPOSES[item.id] || item.description)}</small></span></button>`).join('')}</div>
      </div>
    </section>
    <h3 class="model-settings-section-title">线路配置</h3>
    <section class="android-settings-card android-settings-model-provider-row">${brand(selected.providerId)}<div class="model-settings-enabled-copy"><strong>${escapeHtml(groupName)}</strong></div><button type="button" class="android-settings-switch" data-action="toggle-model-service-enabled" role="switch" aria-label="${escapeHtml(groupName)}启用" aria-checked="${Boolean(draft.enabled)}"><span></span></button></section>
    ${selected.providerId === 'OPENROUTER' ? `<section class="model-catalog-summary" aria-label="模型资料" aria-busy="${Boolean(context.openrouterRegistryRefreshing)}"><div class="model-catalog-summary-row"><div class="model-catalog-summary-copy"><strong>模型资料</strong><span>${registrySummary}</span>${registryDetails ? `<small>${escapeHtml(registryDetails)}</small>` : ''}</div><button type="button" class="model-catalog-refresh" data-action="refresh-openrouter-registry" ${busy || context.openrouterRegistryRefreshing ? 'disabled' : ''}>${context.openrouterRegistryRefreshing ? '更新中…' : '更新资料'}</button></div>${context.openrouterRegistryNotice ? `<p class="model-catalog-feedback" role="status">${escapeHtml(context.openrouterRegistryNotice)}</p>` : ''}${context.openrouterRegistryError ? `<p class="model-catalog-feedback error" role="alert">${escapeHtml(context.openrouterRegistryError)}</p>` : ''}</section>` : ''}
    <label class="android-settings-field android-settings-model-key"><span>API Key</span><div><span aria-hidden="true">${icon(icons.key, '')}</span><input id="model-service-api-key" type="${context.modelCredentialVisible ? 'text' : 'password'}" autocomplete="off" spellcheck="false" value="${escapeHtml(keyValue)}" ${busy ? 'disabled' : ''}><button data-action="reveal-model-service-credential" aria-label="${context.modelCredentialVisible ? '隐藏 API Key' : '显示 API Key'}" ${busy || (!selected.credentialStored && !context.modelCredentialDraft) ? 'disabled' : ''}>${icon(context.modelCredentialVisible ? icons.visibilityOff : icons.visibility, context.modelCredentialVisible ? '隐藏 API Key' : '显示 API Key')}</button></div></label>
    <div class="model-settings-actions">
      <button class="android-settings-model-test" data-action="test-model-service-connection" ${busy || unsaved || !selected.credentialStored ? 'disabled' : ''}>${context.modelSettingsTesting ? '<span class="android-settings-spinner" aria-hidden="true"></span>正在测试…' : '测试连接'}</button>
      <div class="android-settings-model-save"><button data-action="save-model-service-settings" ${busy || !context.modelCredentialEdited || !context.modelCredentialDraft.trim() ? 'disabled' : ''}>${context.modelSettingsSaving ? '<span class="android-settings-spinner" aria-hidden="true"></span>正在保存…' : '保存'}</button></div>
    </div>
    ${unsaved ? '<p class="model-settings-unsaved">API Key 尚未保存，请先保存再测试连接。</p>' : ''}
    ${context.modelSettingsTesting ? '<p class="android-settings-model-feedback">正在检查连接，请稍候。</p>' : ''}
    <p data-model-provider-feedback data-provider-id="${escapeHtml(selected.providerId)}" class="android-settings-model-feedback${context.modelSettingsError ? ' error' : ''}" ${context.modelSettingsError || context.modelSettingsNotice ? '' : 'hidden '}role="${context.modelSettingsError ? 'alert' : 'status'}">${escapeHtml(context.modelSettingsError || context.modelSettingsNotice || '')}</p>${context.modelSettingsNotice === 'API Key 已安全保存在本机。' && !context.modelSettingsError ? '<small>尚未测试连接；请点击“测试连接”确认 API Key 是否可用。</small>' : ''}
    ${selected.credentialStatus === 'Unavailable' ? '<p class="android-settings-model-feedback error">应用私有凭据记录无法读取；请重新保存该服务商 API Key。</p>' : ''}
    ${selected.credentialStored ? `<div class="android-settings-model-save"><button class="danger" data-action="ask-delete-model-service-credential" ${busy ? 'disabled' : ''}>删除 API Key</button></div>` : ''}
    ${!selected.presets?.some(item => item.id === selected.presetId) ? '<p class="android-settings-model-feedback">已保留旧型号设置；请明确选择当前型号后再发送。</p>' : ''}
  </div>`;

}

function zipManualLinkSection(task, data, link = {}) {
  const assets = (task.manualAssets || []).filter(item => ['UNMAPPED_REJECTED', 'MANUAL_LINK_FAILED'].includes(item.status));
  if (!assets.length) return '';
  const selection = link.taskId === task.id ? link : {};
  const selectedAsset = assets.find(item => item.ordinal === selection.assetOrdinal);
  const importedIds = new Set((task.items || []).map(item => item.conversationId).filter(Boolean));
  const conversations = (data?.exchange?.conversations || []).filter(item => importedIds.has(item.id) && !item.deleted);
  const selectedConversation = conversations.find(item => item.id === selection.conversationId);
  const candidateButtons = assets.map(item => `<button data-action="select-p6k-manual-asset" data-asset-ordinal="${item.ordinal}" aria-pressed="${item.ordinal === selectedAsset?.ordinal}">候选 ${item.ordinal + 1} · ${escapeHtml(item.mimeType)} · ${item.byteCount} 字节${item.status === 'MANUAL_LINK_FAILED' ? ' · 上次关联失败' : ''}</button>`).join('');
  const conversationButtons = selectedAsset ? conversations.map(item => `<button data-action="select-p6k-manual-target" data-conversation-id="${escapeHtml(item.id)}" aria-pressed="${item.id === selectedConversation?.id}">${escapeHtml(item.title || '未命名会话')}</button>`).join('') : '';
  const messageButtons = selectedConversation ? (selectedConversation.messages || []).map((item, index) => `<button data-action="select-p6k-manual-target" data-conversation-id="${escapeHtml(selectedConversation.id)}" data-message-id="${escapeHtml(item.id)}" aria-pressed="${item.id === selection.messageId}">消息 ${index + 1} · ${item.role === 'assistant' ? '助手' : item.role === 'user' ? '用户' : '工具'}</button>`).join('') : '';
  return `<section class="android-settings-zip-manual-link"><strong>未自动关联的 ZIP 文件</strong><p>请选择候选文件和它确实所属的原消息；无法确认时保持未关联。不会按文件名或时间猜测归属。</p><div class="android-settings-zip-target-list" role="group" aria-label="ZIP 文件候选">${candidateButtons}</div>${selectedAsset ? `<p>选择导入会话</p><div class="android-settings-zip-target-list" role="group" aria-label="ZIP 导入会话">${conversationButtons || '<span>没有可关联的已导入会话。</span>'}</div>` : ''}${selectedConversation ? `<p>选择原消息</p><div class="android-settings-zip-target-list" role="group" aria-label="ZIP 目标消息">${messageButtons}</div>` : ''}${selection.messageId ? `<div class="android-settings-result-actions"><button data-action="link-p6k-manual-asset">关联到所选消息</button></div>` : ''}</section>`;
}

function zipOfficialSummary(task) {
  if (task.provider !== 'CHATGPT') return '';
  const summary = task.officialAttachmentSummary;
  if (!summary) return '<p class="android-settings-helper">旧批次未记录官方附件统计；重新选择导出包可生成新回执。</p>';
  return `<p class="android-settings-helper">本批导入时按官方引用自动恢复 ${Number(summary.restoredUniqueAssets)} 个文件，显示在 ${Number(summary.restoredOccurrences)} 条原消息引用中；可恢复候选 ${Number(summary.recoverableUniqueAssets)} 个。官方引用但导出包缺文件 ${Number(summary.missingUniqueAssets)} 个，这是导出侧缺失，并非本地恢复丢失。缺少官方显示名而使用文件 ID 回退名 ${Number(summary.fallbackNameUniqueAssets)} 个。原始引用记录 ${Number(summary.referencedOccurrences)} 条，与唯一文件数分开统计。</p>`;
}

function importResultCard(title, task, kind, data = null, p6kLink = {}) {
  if (!task) return '';
  const imported = Number(task.importedCount ?? task.confirmedCount ?? task.items?.filter(item => ['COMMITTED', 'CONFIRMED'].includes(item.status)).length ?? 0);
  const failed = Number(task.failedCount ?? task.items?.filter(item => item.status === 'FAILED').length ?? 0);
  const skipped = Number(task.skippedCount ?? task.items?.filter(item => item.status === 'SKIPPED').length ?? 0);
  const conflictOwner = { 'ChatGPT JSON': 'chatgpt', 'Claude JSON': 'claude', '南枫知识库 JSON': 'nanfeng-knowledge' }[title];
  const conflicts = conflictOwner ? (task.items || []).filter(item => item.status === 'PENDING_CONFIRMATION' && item.failure === 'CONFLICT_REIMPORT') : [];
  return `<section class="android-settings-card android-settings-import-result"><strong>${escapeHtml(title)}</strong><p>${escapeHtml(task.status || '已记录')} · 已导入 ${imported} · 失败 ${failed} · 跳过 ${skipped}${conflicts.length ? ` · 待确认冲突 ${conflicts.length}` : ''}</p>${conflicts.map(item => `<div class="android-settings-import-conflict"><strong>${escapeHtml(item.title || '未命名会话')}</strong><p>同一来源已有其他导出包的会话。再次导入会保留旧会话，并新增一份本地静态副本。</p><div class="android-settings-result-actions"><button data-action="confirm-${conflictOwner}-conflict" data-item-id="${escapeHtml(item.id)}">确认再次导入</button><button data-action="skip-${conflictOwner}-conflict" data-item-id="${escapeHtml(item.id)}">跳过</button></div></div>`).join('')}${kind === 'zip' ? `<p class="android-settings-helper">未自动关联的候选 ${Number(task.unmappedAssetCount || 0)} 个；只有明确选择原消息后才会恢复到对话。按 ChatGPT / Claude 官方身份去重，同一导出内容不会重复导入。你主动删除的对话会保留删除标记，以后再导入也不会复活。</p>${zipOfficialSummary(task)}${zipManualLinkSection(task, data, p6kLink)}<div class="android-settings-result-actions"><button data-action="retry-p6k-zip">重试</button><button data-action="skip-p6k-zip-failures" ${failed ? '' : 'disabled'}>跳过失败项</button><button class="danger" data-action="delete-p6k-zip-batch">删除导入批次</button></div>` : ''}</section>`;
}

const recordCost = record => {
  const cost = projectedCost({
    modelId: accountingPricingModelId(record.requestedModelId || record.modelId, record.actualModelId),
    modelDisplayName: record.callMetadata?.modelDisplayName,
    priceVersion: record.callMetadata?.priceVersion,
    inputTokens: record.inputTokens,
    outputTokens: record.outputTokens,
    cachedInputTokens: record.cachedInputTokens,
    occurredAtMs: record.occurredAtMs,
    chargeMicros: record.chargeMicros,
    currencyCode: record.currencyCode,
    costSource: record.costSource,
  });
  // A legacy stored amount can outlive its absent cost-source flag. Keep that
  // origin unknown; token fact grade and current prices do not establish it.
  if (cost && Number.isSafeInteger(record.chargeMicros) && record.chargeMicros >= 0
      && !['LOCAL_ESTIMATE', 'PROVIDER_RESPONSE'].includes(record.costSource)) {
    return { ...cost, costSource: null };
  }
  return cost;
};

const usageAmount = (records, source = null) => {
  const physical = records.flatMap(usageRecordPhysicalFacts);
  const costs = physical.map(recordCost).filter(cost => cost && ['CNY', 'USD'].includes(cost.currencyCode)).filter(cost => !source || cost.costSource === source);
  if (!costs.length) return '金额未知';
  const cnyMicros = costs.reduce((total, cost) => total + Math.round(cost.chargeMicros * (cost.currencyCode === 'CNY' ? 1 : cost.currencyCode === 'USD' ? USD_TO_CNY : 0)), 0);
  const costSource = costs.every(cost => cost.costSource === 'LOCAL_ESTIMATE') ? 'LOCAL_ESTIMATE' : 'PROVIDER_RESPONSE';
  const amount = cnyCostLabel({ chargeMicros: cnyMicros, currencyCode: 'CNY', costSource }, { maximumFractionDigits: 6 });
  const missing = source ? 0 : physical.length - costs.length;
  return amount ? `约 ${amount}${missing ? `（另有 ${missing} 笔金额未知）` : ''}` : '金额未知';
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

const usageStateLabels = { COMPLETED: '已完成', RESPONSE_COMMITTED: '正文已保存', COMPLETED_ACCOUNTING_PENDING: '已完成，归因待补写', FAILED: '失败', UNKNOWN: '结果未知', CANCELLED: '已停止', RUNNING: '进行中', PENDING: '等待开始' };

function groupedUsageOutcome(physical) {
  const counts = new Map();
  for (const record of physical) {
    const state = Object.hasOwn(usageStateLabels, record.requestState) ? record.requestState : null;
    counts.set(state, (counts.get(state) || 0) + 1);
  }
  const order = ['UNKNOWN', 'FAILED', 'CANCELLED', 'RUNNING', 'PENDING', 'COMPLETED_ACCOUNTING_PENDING', 'RESPONSE_COMMITTED', 'COMPLETED', null];
  const labels = order.filter(state => counts.has(state)).map(state => `${usageStateLabels[state] || '状态未记录'} ${counts.get(state)} 次`);
  const codes = [...new Set(physical.map(record => record.safeErrorCode).filter(code => typeof code === 'string' && code))].sort();
  return [...labels, ...codes].join(' · ');
}

function usageLedgerPage(context) {
  const records = groupRetrievalUsageRecords(context.usageLedger?.records || []);
  if (!records.length) return `<div class="android-settings-page android-settings-ledger-empty">${icon(icons.data, '')}<strong>尚无可用费用记录</strong><p>已返回输入和输出 Token 的调用会显示费用与用量。</p></div>`;
  const physicalRecords = records.flatMap(usageRecordPhysicalFacts);
  const tokenTotalLabel = key => {
    const summary = usageTokenSummary({ groupedCalls: physicalRecords }, key);
    if (summary.total == null) return summary.knownCount ? '合计不可用' : '未知';
    return `${summary.total.toLocaleString('zh-CN')}${summary.unknownCount ? ' + 未知' : ''}`;
  };
  const sections = [['conversation', '会话'], ['title', '会话标题整理'], ['history', '历史资料整理'], ['ocr', '南枫转写']];
  const selected = context.usageSection || 'conversation';
  const category = record => record.entryId?.startsWith('usage-reminder-') ? 'reminder' : record.entryId?.includes('history') ? 'history' : record.entryId?.includes('title') ? 'title' : record.entryId?.includes('transcription') ? 'ocr' : 'conversation';
  const rows = records.filter(record => category(record) === selected);
  const automaticRows = records.filter(record => category(record) === 'reminder');
  const ledgerRow = (record, fallbackTitle, preferFallback = false) => {
    const conversation = (context.data?.exchange?.conversations || []).find(item => item.id === record.conversationId);
    const cost = record.groupedCalls ? null : recordCost(record);
    const physical = usageRecordPhysicalFacts(record);
    const callRole = record.callMetadata?.role === 'MATERIAL_READ' ? '材料读取' : record.callMetadata?.role === 'RETRIEVAL' ? '联网检索' : record.callMetadata?.role === 'ANSWER' ? '主回答' : '调用';
    const tokens = Object.fromEntries(['inputTokens', 'outputTokens', 'cachedInputTokens'].map(key => [key, usageTokenSummary(record, key)]));
    const hasProviderUsage = Object.values(tokens).some(summary => summary.knownCount > 0);
    const tokenLabel = key => {
      const summary = tokens[key];
      if (summary.total == null) return summary.knownCount ? '合计不可用' : '未知';
      return `${summary.total.toLocaleString('zh-CN')}${summary.unknownCount ? ' + 未知' : ''}`;
    };
    const converted = cnyCostLabel(cost, { maximumFractionDigits: 6 });
    const amount = record.groupedCalls ? usageAmount([record]) : converted ? `约 ${converted}` : '金额未知';
    const known = physical.map(recordCost).filter(Boolean);
    const providerCost = known.some(cost => cost.costSource === 'PROVIDER_RESPONSE');
    const estimatedCost = known.some(cost => cost.costSource === 'LOCAL_ESTIMATE');
    const unknownSourceCount = physical.length - known.filter(cost => ['PROVIDER_RESPONSE', 'LOCAL_ESTIMATE'].includes(cost.costSource)).length;
    const knownSourceLabel = providerCost && estimatedCost ? '服务商报告与本地估算' : estimatedCost ? '本地估算' : providerCost ? '服务商报告' : '费用来源未确认';
    const sourceLabel = (providerCost || estimatedCost) && unknownSourceCount
      ? `${knownSourceLabel}；另有 ${unknownSourceCount} 笔费用来源未确认` : knownSourceLabel;
    const stateLabel = usageStateLabels[record.requestState] || '状态未记录';
    const completed = ['COMPLETED', 'RESPONSE_COMMITTED', 'COMPLETED_ACCOUNTING_PENDING'].includes(record.requestState);
    const duration = completed && Number.isSafeInteger(record.modelElapsedMs) && record.modelElapsedMs >= 0 ? `模型${elapsedTime(record.modelElapsedMs)}` : '模型耗时未记录';
    const ocr = category(record) === 'ocr';
    const ocrDuration = completed && Number.isSafeInteger(record.modelElapsedMs) && record.modelElapsedMs >= 0 ? elapsedTime(record.modelElapsedMs) : '耗时未记录';
    const outcome = record.groupedCalls ? groupedUsageOutcome(physical) : `${stateLabel}${record.safeErrorCode ? ` · ${record.safeErrorCode}` : ''}${category(record) === 'conversation' ? ` · ${duration}` : ocr ? ` · ${ocrDuration}` : ''}`;
    const details = ocr && record.ocrDetails;
    const ocrDetails = details ? `<p class="android-settings-helper">${escapeHtml(details.providerId === 'ZHIPU' ? '智谱' : details.providerId)} · Attempt ${escapeHtml(details.attemptId)} · 第 ${escapeHtml(details.attemptNumber)} 次</p><p class="android-settings-helper">原文件：${escapeHtml(details.sourceDisplayName)} · ${Number.isSafeInteger(details.pageCount) && details.pageCount > 0 ? `${details.pageCount} 页` : '页数未返回'} · 请求 ID：${escapeHtml(details.providerRequestId || '未返回')}</p>` : ocr ? '<p class="android-settings-helper">旧调用详情未记录</p>' : '';
    const usage = hasProviderUsage
      ? `输入 ${tokenLabel('inputTokens')} / 输出 ${tokenLabel('outputTokens')} Token${tokens.cachedInputTokens.knownCount ? ` · 缓存 ${tokenLabel('cachedInputTokens')} Token` : ''}`
      : 'Token 用量未返回';
    return `<section class="android-settings-card android-settings-ledger-row"><div><strong>${escapeHtml(preferFallback ? fallbackTitle : conversation?.title || fallbackTitle)}</strong><b>${escapeHtml(amount)}</b></div><p>${escapeHtml(record.callMetadata?.modelDisplayName || record.modelId)} · ${usage}</p><p class="android-settings-helper" data-usage-model-identity>${escapeHtml(usageModelIdentityLabel(record))}</p><p class="android-settings-helper" data-usage-call-route>${escapeHtml(usageCallRouteLabel(record))}</p><p class="android-settings-helper">${escapeHtml(callRole)}${record.groupedCalls ? ` · ${physical.length} 次检索` : ''} · ${escapeHtml(sourceLabel)}</p><p class="android-settings-helper">${escapeHtml(outcome)}</p>${ocrDetails}<small>${escapeHtml(localDateTime(record.occurredAtMs))}</small></section>`;
  };
  return `<div class="android-settings-page android-settings-ledger">
    <section class="android-settings-card android-settings-ledger-summary"><strong>本机累计</strong><div><span>费用</span><b>${escapeHtml(usageAmount(records))}</b></div><div><span>输入 Token</span><b>${tokenTotalLabel('inputTokens')}</b></div><div><span>输出 Token</span><b>${tokenTotalLabel('outputTokens')}</b></div></section>
    <section class="android-settings-card android-settings-ledger-grid">${sections.map(([id, label]) => { const items = records.filter(record => category(record) === id); return `<div><strong>${label}</strong><span>次数 <b>${items.flatMap(usageRecordPhysicalFacts).length} 次</b></span><span>费用 <b>${items.length ? escapeHtml(usageAmount(items)) : '金额未知'}</b></span></div>`; }).join('')}</section>
    <p class="android-settings-helper">美元按 ${CNY_REFERENCE_DATE} 参考汇率换算，原币账本保持不变。</p>
    <div class="android-settings-ledger-segments">${sections.map(([id, label]) => `<button data-action="select-usage-section" data-section="${id}" class="${selected === id ? 'selected' : ''}">${label}</button>`).join('')}</div>
    ${rows.length ? rows.map(record => ledgerRow(record, selected === 'ocr' ? '南枫转写任务' : '已删除的对话')).join('') : `<p class="android-settings-empty">还没有${escapeHtml(sections.find(([id]) => id === selected)?.[1] || '')}费用记录。</p>`}
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
  const { data, favorites, runtimeInfo, settings, native, p6eAcceptance, p6kTask, chatgptTask, claudeTask, nanfengKnowledgeTask, modelServiceSettings } = context;
  const active = data?.exchange?.conversations || [];
  if (['conversations', 'favorites', 'archived', 'recycle'].includes(page)) return renderConversationLifecyclePage({ page, conversations: active, favoriteConversationIds: favorites, domainExchange: data?.exchange });
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
  if (page === 'about') return `<div class="android-settings-page"><section class="android-settings-card android-settings-about"><div><strong>产品信息</strong><p>南枫 AI</p><small>${workspaceUiEnabled(runtimeInfo) ? '本机对话、项目与知识工作区。' : '支持多模型、聊天记忆、附件和提醒的聊天工具。'}</small></div>${divider}${aboutBuildInformation(runtimeInfo)}${divider}<div><strong>开发者信息</strong><small>开发者：席瑞</small><small>联系邮箱：nanzhufeng.studio@gmail.com</small><small>源码与更新：${aboutLink(repositoryUrl, 'GitHub · nanzhufeng/Nanfeng-AI', '查看源码仓库')}</small><small>版权所有 © 2026 席瑞</small></div></section></div>`;
  if (page === 'memory-overview') {
    const memories = (data?.exchange?.memory || []).filter(item => (item.status || 'ACTIVE') === 'ACTIVE' && !item.deleted && (item.scope || 'GLOBAL') === 'GLOBAL' && !item.scopeId);
    const pending = (data?.exchange?.memory || []).filter(item => item.status === 'PENDING_REVIEW' && (item.scope || 'GLOBAL') === 'GLOBAL' && !item.scopeId);
    const updatedAt = memories.map(item => item.updatedAt || item.createdAt || '').filter(Boolean).sort().at(-1);
    return `<div class="android-settings-page android-memory-summary-page">
      ${context.memorySummaryNotice ? `<p class="android-settings-helper" role="status">${escapeHtml(context.memorySummaryNotice)}</p>` : ''}
      ${pending.length ? `<section class="android-settings-card android-settings-status-card"><strong>待审阅的同主题记忆</strong><p>这些内容尚未用于后续回答。</p>${pending.map(item => `<article><h2>${escapeHtml(item.title || '概览')}</h2><p>${escapeHtml(item.body || '')}</p><button data-action="review-pending-memory-topic" data-id="${escapeHtml(item.id || '')}">处理这条候选</button></article>`).join('')}</section>` : ''}
      <section class="android-memory-summary-content">${memories.map((item, index) => `<article><h2>${escapeHtml(item.title || (index === 0 ? '概览' : `记忆 ${index + 1}`))}</h2><p>${escapeHtml(item.body || '')}</p><button data-action="show-memory-audit" data-id="${escapeHtml(item.id || '')}">来源与修订</button></article>`).join('') || `<p class="android-settings-empty android-memory-summary-empty">还没有记忆摘要。</p>`}</section>
    </div>`;
  }
  if (page === 'workspace') return `<div class="android-settings-page">${group('', [row({ glyph: icons.folder, title: `管理 Projects`, action: 'show-projects' }), row({ glyph: icons.knowledge, title: '管理知识库', action: 'show-knowledge' })])}<section class="android-settings-card android-settings-status-card"><strong>功能审阅</strong><p>已确认：图片、PDF、音视频与混合附件沿现有附件和发送入口处理；必要时由已披露的 Haiku／Argon 识读，原选模型回答并按实际调用计费。识读失败停止，未知结果不自动重发；不新增常驻按键。</p><p>已确认：日常档加入 GPT-6.1 Terra 与 Mercury 3，分别位于 Luna 前后；沿用选模入口，默认与 Auto 顺序不变。理由：补齐日常模型选择，不新增常驻按键。</p><p>工作区独立对话：保留。</p><p>沿用工作区入口；在消息菜单中选择引用文字到另一区域。</p><small>两区各自保存，只有主动选择的文字会进入另一边的草稿。</small></section></div>`;
  if (page === 'development') return `<div class="android-settings-page">${group('', [row({ glyph: icons.settings, title: '本次 Context 控制', action: 'show-connections' }), row({ glyph: icons.info, title: '离线评测', action: 'show-p8-inspect' })])}${p6eAcceptance?.enabled ? `<section class="android-settings-card android-settings-status-card"><strong>P6-E 验收维护</strong><p>仅 acceptance 启动显示；正式启动不出现。</p><button data-action="run-p6e-temporary-maintenance-acceptance">运行 23h59 / 24h 维护验收</button>${p6eAcceptance.receipt ? `<small>23h59 保留=${p6eAcceptance.receipt.retainedAt23h59} · 24h 清理=${p6eAcceptance.receipt.removedAt24h}</small>` : ''}</section>` : ''}</div>`;
  if (page === 'feature-review' && !workspaceUiEnabled(runtimeInfo)) return `<div class="android-settings-page">${argonFeatureReview()}${alignedBehaviorFeatureReview()}<section class="android-settings-card android-settings-status-card"><strong>聊天工具界面</strong><p>聊天、附件、转写、搜索、模型设置与会话同步：保留现有入口。</p><p>聊天记忆与提醒：保留现有设置和管理入口。</p><p>工作区、项目、知识库与 Agent：隐藏界面入口，底层代码和已有数据保留。</p><small>已按您的决定执行；不新增常驻按钮。Harness 相关开发暂停，继续完善聊天功能。</small></section><p class="android-settings-helper">会话标题编辑：建议保留。双击标题或使用侧栏重命名原位编辑；Enter/失焦保存，Escape取消，输入法确认不触发保存。理由：保留阅读位置，失败时保留标题草稿，不增加常驻按键。</p><p class="android-settings-helper">云端取消同步：建议保留。入口为云端会话菜单和批量确认；批量处理可停止后续。理由：只移除已确认的云副本，本地聊天与收藏保留，失败项可再次处理。</p><p class="android-settings-helper">文本阅读器：建议保留。标题居中，右上文件菜单提供下载文件、分享文件和复制文本；方向键切换，Escape先关闭菜单、再关闭阅读器。理由：集中原文件操作，保留正文阅读位置，不增加常驻按钮。</p><p class="android-settings-helper">视频进度：建议保留。沿用播放进度条，拖回开头可保存0秒；暂停、拖动和关闭均记录位置。理由：下次从准确位置继续，读取准备期间不覆盖已有进度。</p><p class="android-settings-helper">回答重点：建议保留。沿用正文加粗，小范围区分重点与注意；每条回答最多两处重点、一处注意，不新增按键。理由：便于阅读，同时保留原文和普通加粗。</p><p class="android-settings-helper">消息信息复制：建议保留。可选择页脚型号、时间和费用；Tab定位后Ctrl+A选择本条信息，再用Ctrl+C复制。理由：长信息完整换行，选择文字时不误开消息菜单，不新增按键。</p></div>`;
  if (page === 'feature-review') return `<div class="android-settings-page">${group('后台生成状态', [row({ glyph: icons.conversation, title: '建议保留：南枫岛', page: 'feature-review', value: '按对齐方案接入' })])}<p class="android-settings-helper">入口建议：主窗口在后台且有生成任务时显示；回到本应用时收起。按键建议：仅在展开的任务旁提供查看对话和停止，不增加主界面常驻按钮。理由：可以回看已保存字数，并只停止选中的那次生成。</p>${group('正式资料修订冲突恢复', [row({ glyph: icons.knowledge, title: '建议保留：保稿并明确载入最新修订', page: 'workspace', value: '待您判断' })])}<p class="android-settings-helper">入口建议：已有项目／知识／记忆编辑弹窗及整篇记忆摘要编辑器的冲突提示。按键建议：仅在冲突时显示“重新载入最新修订（替换本次草稿）”，不增加常驻按钮。理由：先查看已保存内容，用户明确决定替换本次编辑；整篇摘要重新核对全部活动主题清单；读取期间新编辑、已删记录或切区均不会被覆盖。</p>${group('本机知识重复候选', [row({ glyph: icons.knowledge, title: '建议保留：只读重复线索', page: 'workspace', value: '待您判断' })])}<p class="android-settings-helper">入口建议：管理知识库 → 已保存知识的编辑详情 → 查看重复候选。按键建议：仅在详情提供，不增加常驻按键。理由：同一范围内按规范化标题与正文比较，只显示既有标题、修订和理由，不联网、不自动合并或删除。</p>${group('Markdown／Knowledge JSON／PDF 文字知识转换', [row({ glyph: icons.knowledge, title: '建议保留：先审阅再入库', page: 'workspace', value: '待您判断' })])}<p class="android-settings-helper">入口建议：管理知识库 → 新建知识弹窗 → 从 Markdown／Knowledge JSON／PDF 文字导入。按键建议：仅在新建弹窗提供文件选择，待确认任务显示审阅、跳过和取消，不增加常驻按键。理由：文件与正式知识分开保存，逐项确认并保留来源；不会执行代码或自动联网。PDF 仅提取可读取文字层，按页显示真实进度；每页确认或跳过，可取消、保留私有副本并重试；不自动扫描识别或还原排版。导出入口建议：沿用知识编辑弹窗的“导出已保存知识”，再明确勾选条目和保存位置，不增加常驻主按键；仅导出正式已保存内容并验证回读。</p>${group('工作资料恢复', [row({ glyph: icons.restore, title: '建议保留：知识、记忆与关系回收站恢复', page: 'recycle', value: '合同内' })])}<p class="android-settings-helper">入口建议：沿用设置 → 对话管理 → 回收站。按键建议：仅在已删除或撤销的条目旁显示恢复，不增加常驻按钮。理由：工作资料软删除后可找回正文；恢复仍检查当前修订，清空会话不会删除这些资料。</p>${group('工作区提醒回读', [row({ glyph: settingsIcons.bell, title: '建议保留：已有工作计划可回看', page: 'reminders', value: '待您判断' })])}<p class="android-settings-helper">入口建议：沿用侧栏“定时任务”。按键建议：工作区仅在已有计划或待处理草稿时显示原入口，不增加空态常驻按钮。理由：生成时可审阅，关闭重开后也能回看执行结果，不必重新生成或切到另一区。</p>${group('对话与工作切换', [row({ glyph: icons.conversation, title: '建议保留：已有对话也显示模式切换', page: 'workspace', value: '待您判断' })])}<p class="android-settings-helper">入口建议：沿用对话顶部的“对话／工作”切换。按键建议：保留原切换控件，不新增常驻按钮。理由：已有回答时仍可直接进入工作区，返回后继续原会话，不需先新建对话。</p>${group('项目资料范围', [row({ glyph: icons.knowledge, title: '建议保留：Knowledge／Memory 选择项目范围', page: 'workspace', value: '待您判断' })])}<p class="android-settings-helper">入口建议：工作区 → 管理知识库 → 新建 Knowledge／Memory。按键建议：仅在新建弹窗选择适用项目，不增加常驻按钮。理由：用户可明确限定资料只进入该项目对话，避免全局资料误入其他项目。</p>${group('双区本机备份', [row({ glyph: settingsIcons.importExport, title: '建议保留：备份区域选择', page: 'data', value: '待您判断' })])}<p class="android-settings-helper">入口建议：设置 → 数据管理 → 导入与导出。按键建议：仅在本机备份卡片内使用双选项胶囊，不在工作主界面增加常驻按钮。理由：原工作区没有可见备份入口；明确选择区域能避免误将对话备份恢复到工作区。</p>${group('跨区文字引用', [row({ glyph: icons.folder, title: '建议保留：工作区独立对话与文字引用', page: 'workspace', value: '待您判断' })])}<p class="android-settings-helper">入口建议：沿用工作区和消息菜单。按键建议：只在消息菜单显示“引用文字到工作区／对话区”，不增加常驻按钮。理由：用户主动选择的文字只进入目标区未发送草稿，不自动发送或夹带附件。</p>${group('历史导入', [row({ glyph: settingsIcons.importExport, title: '建议保留：南枫知识库 JSON 导入', page: 'data', value: '待您判断' })])}<p class="android-settings-helper">入口建议：设置 → 数据管理 → 南枫知识库。按键建议：仅在数据管理页提供导入行，不增加工作区常驻按钮。理由：用户选择文件后逐项写入当前工作区，并可从导入结果回读。</p>${group('ZIP 附件关联', [row({ glyph: settingsIcons.importExport, title: '建议保留：未关联 ZIP 附件手动挂到原消息', page: 'zip-import-results', value: '待您判断' })])}<p class="android-settings-helper">入口建议：设置 → 数据管理 → ZIP 导入结果。按键建议：只在结果页展示候选与目标选择，不增加常驻按钮。理由：自动归属尚未证实，用户确认原消息后再恢复附件，避免猜测挂错。</p>${group('消息树分支', [row({ glyph: icons.conversation, title: '建议保留：编辑消息与分支历史', page: 'feature-review', value: '待您判断' })])}<p class="android-settings-helper">入口建议：沿用消息底部三点菜单。按键建议：可编辑纯文本用户消息显示“编辑消息”，当前未回答的用户叶显示“生成回答”，当前已完成回答显示“重新生成／换模型重答”，分支历史也放在菜单；不新增常驻按钮。理由：编辑或重答形成新的本地路径，换模型须明确选择目标，旧问题和回答仍可切回。</p>${argonFeatureReview()}${alignedBehaviorFeatureReview()}${group('公开模型目录', [row({ glyph: icons.settings, title: '建议保留：OpenRouter 目录核验', page: 'model-configuration', value: '合同内' })])}<p class="android-settings-helper">入口建议：设置 → 模型配置 → OpenRouter。按键建议：仅在该页显示刷新按钮，不增加主界面常驻按钮。理由：固定地址只读核验，成功后精确模型映射可更新普通发送预算。</p>${group('普通发送材料', [row({ glyph: icons.conversation, title: '建议保留：XML／YAML／HTML 本机文本附件', page: 'feature-review', value: '合同内' })])}<p class="android-settings-helper">入口建议：沿用聊天输入框的“文件”。按键建议：不增加常驻按钮。理由：选入后只在本机解析安全 UTF-8 文本，发送时才交给本次选定的回答模型。</p>${group('记忆来源', [row({ glyph: icons.info, title: '建议保留：记忆来源与修订', page: 'memory-overview', value: '待您判断' })])}<p class="android-settings-helper">入口建议：设置 → 记忆摘要 → 对应条目。按键建议：仅在每条记忆下显示“来源与修订”，不增加主界面常驻按钮。理由：用户可回看本机记录的变更及撤销状态；缺失的旧历史明确标注，不推测来源。</p>${group('Google 授权等待', [row({ glyph: settingsIcons.account, title: '建议保留：等待中可取消本次 Google 授权', page: 'account', value: '待您判断' })])}<p class="android-settings-helper">入口建议：设置 → Google 账号与同步。按键建议：仅在授权等待期间显示“取消本次授权等待”，不增加常驻按钮。理由：关闭浏览器后可立即结束本次等待，保留原账号及本机数据。</p>${group('网页文字知识审阅', [row({ glyph: icons.folder, title: '建议保留：明确抓取、逐条审阅网页文字', page: 'feature-review', value: '待您判断' })])}<p class="android-settings-helper">入口建议：沿用知识页的新建／导入弹窗，选择 HTTPS 网页文字。按键建议：只在弹窗和当前任务显示确认抓取、取消、审阅与重新输入地址，不新增常驻工具栏按钮。理由：公开网页需要联网，正文先作为待确认候选；失败后不自动重放旧 URL，已有确认与来源保留。</p>${group('Windows 文件分享副本', [row({ glyph: icons.folder, title: '建议保留：接收端可稍后读取分享副本', page: 'feature-review', value: '待您判断' })])}<p class="android-settings-helper">入口建议：沿用附件预览的系统分享。按键建议：沿用设置 → 本机数据 → 选择清理范围，不新增常驻按钮；清理前预览数量、空间及接收路径失效影响。理由：Windows 完成回调可能早于接收应用读取文件，已交付副本在应用退出后仍保留，取消时回收；这些副本会占用本机空间。</p><p class="android-settings-helper">会话标题编辑：建议保留。双击标题或使用侧栏重命名原位编辑；Enter/失焦保存，Escape取消，输入法确认不触发保存。理由：保留阅读位置，失败时保留标题草稿，不增加常驻按键。</p><p class="android-settings-helper">云端取消同步：建议保留。入口为云端会话菜单和批量确认；批量处理可停止后续。理由：只移除已确认的云副本，本地聊天与收藏保留，失败项可再次处理。</p></div>`;
  if (page === 'account') return accountSyncPage(context);
  if (page === 'data') return renderLocalDataImportExportPage(context);
  if (page === 'json-import-results') {
    const visibleKnowledgeTask = workspaceUiEnabled(context.runtimeInfo) ? nanfengKnowledgeTask : null;
    return `<div class="android-settings-page android-settings-list">${importResultCard('ChatGPT JSON', chatgptTask, 'json')}${importResultCard('Claude JSON', claudeTask, 'json')}${importResultCard('南枫知识库 JSON', visibleKnowledgeTask, 'json')}${!chatgptTask && !claudeTask && !visibleKnowledgeTask ? '<p class="android-settings-empty">还没有导入记录。</p>' : ''}</div>`;
  }
  if (page === 'zip-import-results') return `<div class="android-settings-page android-settings-list">${importResultCard(`${p6kTask?.provider || 'ZIP'} · 批次 1`, p6kTask, 'zip', data, context.p6kLink) || '<p class="android-settings-empty">还没有导入记录。</p>'}</div>`;
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
    const registry = context.openrouterRegistryStatus;
    const verifiedOpenrouterIds = selected.providerId === 'OPENROUTER' && ['VERIFIED', 'STABLE_FALLBACK'].includes(registry?.status)
      ? new Set(registry.mappedPresetIds || []) : null;
    const presetButtons = (selected.presets || []).map(item => {
      const unavailable = verifiedOpenrouterIds && !verifiedOpenrouterIds.has(item.id);
      return `<button data-action="select-model-service-preset" data-preset-id="${item.id}" aria-pressed="${item.id === current}" ${unavailable ? 'disabled' : ''}><span><strong>${escapeHtml(item.displayName)}</strong><small>${escapeHtml(unavailable ? '当前目录未列出' : item.description)}</small></span>${item.id === current ? icon(icons.check, '已选中') : ''}</button>`;
    }).join('');
    return `<div class="android-settings-picker-scrim" data-action="dismiss-settings-picker"><section class="android-settings-picker android-settings-model-picker" role="dialog" aria-modal="true" aria-label="聊天模型" data-overlay-surface>${presetButtons}${(selected.nonChatCapabilities || []).map(item => `<div class="android-settings-model-capability"><strong>${escapeHtml(item.displayName)}</strong><small>${escapeHtml(item.description)}</small><small>${escapeHtml(item.family)}</small></div>`).join('')}</section></div>`;
  }
  const options = kind === 'mode' ? APPEARANCE_MODES : kind === 'fontSize' ? FONT_SIZES : kind === 'themeColor' ? THEME_COLORS : CONVERSATION_TONES;
  const current = kind === 'mode' ? appearance.mode : kind === 'fontSize' ? appearance.fontSize : kind === 'themeColor' ? appearance.themeColor : draft.tone;
  const title = kind === 'mode' ? '外观' : kind === 'fontSize' ? '字体大小' : kind === 'themeColor' ? '主题色' : '基础风格和语气';
  const pickerClass = kind === 'tone' ? 'android-settings-picker android-settings-tone-picker' : 'android-settings-picker';
  return `<div class="android-settings-picker-scrim" data-action="dismiss-settings-picker"><section class="${pickerClass}" role="dialog" aria-modal="true" aria-label="${title}" data-overlay-surface>${options.map(item => `<button data-action="select-settings-picker-option" data-picker="${kind}" data-value="${item.id}" aria-pressed="${item.id === current}">${item.accent ? `<i class="android-settings-color-preview android-settings-color-preview-${escapeHtml(item.id)}" aria-hidden="true"></i>` : ''}<span><strong>${escapeHtml(item.label)}</strong>${item.detail ? `<small>${escapeHtml(item.detail)}</small>` : ''}</span>${item.id === current ? icon(icons.check, '已选中') : ''}</button>`).join('')}</section></div>`;
}

export function renderAndroidSettingsShell({ page = 'personalization', status = '', appearance = {}, settings = {}, personalizationDraft = settings, personalizationDirty = false, picker: pickerKind = null, data, favoriteConversationIds = new Set(), runtimeInfo = {}, connection = {}, native = false, p6eAcceptance = { enabled: false, receipt: null }, p6gCatalog = null, openrouterRegistryStatus = null, openrouterRegistryRefreshing = false, openrouterRegistryNotice = '', openrouterRegistryError = '', p6kTask = null, p6kLink = {}, chatgptTask = null, claudeTask = null, nanfengKnowledgeTask = null, modelServiceSettings = MODEL_SERVICE_PREVIEW_SETTINGS, modelProviderId = 'OPENROUTER', modelServiceDraft = null, modelCredentialDraft = '', modelCredentialVisible = false, modelCredentialEdited = false, modelSettingsSaving = false, modelSettingsTesting = false, modelSettingsNotice = '', modelSettingsError = '', usageLedger = { records: [], inputTokens: 0, outputTokens: 0, cachedInputTokens: 0 }, usageSection = 'conversation', contextSelectionRecords = [], diagnosticRecords = [], invocationRecords = [], privacyInventory = { totalBytes: 0, aggregates: [] }, localBackup = {}, localBackupArea = 'CHAT', backupAreaBusy = false, accountSync = {}, accountRecovery = null, capabilities = DESKTOP_SETTINGS_CAPABILITIES, reminders = { drafts: [], plans: [], diagnostics: [] }, reminderNotificationPermission = 'default', reminderNotificationBridge = {}, backgroundRuntime = {}, memorySummaryQuery = '', memorySummaryComposer = '', memorySummaryNotice = '' } = {}) {
  page = chatUiRoute('settings', page, runtimeInfo).settingsPage;
  const normalizedSettings = normalizeProductSettings(settings);
  const editableDraft = { ...normalizedSettings, ...personalizationDraft };
  const draft = normalizeProductSettings(editableDraft);
  // Invalid edits stay visible until the user corrects or cancels them. Only
  // committed settings use the bounded normalization projection.
  for (const field of ['nickname', 'occupation', 'interests', 'customInstructions']) draft[field] = String(editableDraft[field] ?? '');
  const context = { appearance: normalizeAppearance(appearance), settings: normalizedSettings, draft, data, favorites: favoriteConversationIds, runtimeInfo, connection, native, p6eAcceptance, p6gCatalog, openrouterRegistryStatus, openrouterRegistryRefreshing, openrouterRegistryNotice, openrouterRegistryError, p6kTask, p6kLink, chatgptTask, claudeTask, nanfengKnowledgeTask, modelServiceSettings, modelProviderId, modelServiceDraft, modelCredentialDraft, modelCredentialVisible, modelCredentialEdited, modelSettingsSaving, modelSettingsTesting, modelSettingsNotice, modelSettingsError, usageLedger, usageSection, contextSelectionRecords, diagnosticRecords, invocationRecords, privacyInventory, localBackup, localBackupArea, backupAreaBusy, accountSync, accountRecovery, capabilities, reminders, reminderNotificationPermission, reminderNotificationBridge, backgroundRuntime, memorySummaryQuery, memorySummaryComposer, memorySummaryNotice };
  context.page = page;
  const content = page === 'personalization' ? personalization(context) : page === 'reminders' ? remindersPage(context) : simplePage(page, context);
  return `<main class="chat-main android-settings-main"><div class="android-settings-layout"><aside class="android-settings-primary" aria-label="设置一级菜单"><div class="android-settings-primary-header"><button data-action="show-chat" aria-label="返回应用">${icon(icons.chevronLeft, '返回应用')}</button><h1>设置</h1><span></span></div><div class="android-settings-primary-scroll" data-settings-scroll-key="settings-primary">${home(context)}</div></aside><section class="android-settings-secondary" aria-label="设置二级页面">${header(page, personalizationDirty, capabilities, context)}<div class="android-settings-scroll" data-settings-scroll-key="settings-page-${escapeHtml(page)}">${content}${status ? `<p class="android-settings-status" role="status">${escapeHtml(status)}</p>` : ''}</div></section></div>${picker(pickerKind, context)}</main>`;
}
