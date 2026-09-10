import { icon, icons, settingsIcons } from './icon-source.mjs';
import { renderSafeMarkdown } from './safe-markdown.mjs';
import { APPEARANCE_MODES, CONVERSATION_TONES, DESKTOP_SETTINGS_CAPABILITIES, FONT_SIZES, THEME_COLORS, conversationToneDefinition } from './desktop-parity-preferences.mjs';
import { MODEL_SERVICE_PREVIEW_SETTINGS, renderAndroidSettingsShell } from './android-settings-shell.mjs';
import { renderDesktopSearchPage } from './desktop-search-page.mjs';
import { attachmentPreviewCapability } from './desktop-attachment-preview-owner.mjs';
import { renderWorkspaceNavigation, renderWorkConversationState, resolveWorkConversationState } from './workspace-view.mjs';

const escapeHtml = value => String(value ?? '').replace(
  /[&<>"']/g,
  char => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#039;' })[char],
);

function manualUnreadTimestamp(manualUnreadAtMs, conversationId) {
  if (manualUnreadAtMs instanceof Map) return Number(manualUnreadAtMs.get(conversationId) || 0);
  return Number(manualUnreadAtMs?.[conversationId] || 0);
}

export function activeConversations(data, manualUnreadAtMs = new Map()) {
  return (data?.exchange?.conversations ?? [])
    .filter(item => !item.deleted && !item.archived)
    .sort((left, right) => Number(Boolean(right.pinned)) - Number(Boolean(left.pinned))
      || Number(manualUnreadTimestamp(manualUnreadAtMs, right.id) > 0) - Number(manualUnreadTimestamp(manualUnreadAtMs, left.id) > 0)
      || manualUnreadTimestamp(manualUnreadAtMs, right.id) - manualUnreadTimestamp(manualUnreadAtMs, left.id)
      || String(right.updatedAt ?? right.createdAt ?? '').localeCompare(String(left.updatedAt ?? left.createdAt ?? ''))
      || String(left.id ?? '').localeCompare(String(right.id ?? '')));
}

export function pinnedConversations(data, manualUnreadAtMs = new Map()) {
  return activeConversations(data, manualUnreadAtMs).filter(item => item.pinned);
}

export function archivedConversations(data) {
  return (data?.exchange?.conversations ?? [])
    .filter(item => !item.deleted && item.archived)
    .sort((left, right) => String(right.updatedAt ?? right.createdAt ?? '').localeCompare(String(left.updatedAt ?? left.createdAt ?? ''))
      || String(left.id ?? '').localeCompare(String(right.id ?? '')));
}

export function deletedConversations(data) {
  return (data?.exchange?.conversations ?? []).filter(item => item.deleted)
    .sort((left, right) => String(right.updatedAt ?? '').localeCompare(String(left.updatedAt ?? '')) || String(left.id).localeCompare(String(right.id)));
}

export function favoriteConversations(data, favoriteIds = new Set()) {
  return activeConversations(data).filter(item => favoriteIds.has(item.id));
}

export function resolveConversation(data, selectedConversationId) {
  const conversations = activeConversations(data);
  if (!selectedConversationId) return null;
  return conversations.find(item => item.id === selectedConversationId)
    ?? (globalThis.__nanfengDesktopSearchState?.searchLocatedArchivedConversationId === selectedConversationId
      ? (data?.exchange?.conversations || []).find(item => item.id === selectedConversationId && !item.deleted)
      : null);
}

export function resolveConversationMenuAnchor(anchorRect, { viewportWidth, viewportHeight, sidebarRect, menuWidth = 192, menuHeight = 204, gap = 6 } = {}) {
  const viewport = { left: 0, top: 0, right: viewportWidth, bottom: viewportHeight };
  const sidebarBounds = sidebarRect ? {
    left: Math.max(viewport.left, sidebarRect.left), top: Math.max(viewport.top, sidebarRect.top),
    right: Math.min(viewport.right, sidebarRect.right), bottom: Math.min(viewport.bottom, sidebarRect.bottom),
  } : null;
  // Tauri can transiently report a zero-sized sidebar while a context-menu
  // event is being dispatched.  Never clamp a row-owned menu to that origin.
  const bounds = sidebarBounds && sidebarBounds.right - sidebarBounds.left >= menuWidth + gap * 2 && sidebarBounds.bottom - sidebarBounds.top >= menuHeight + gap * 2
    ? sidebarBounds
    : viewport;
  const clamp = (value, min, max) => Math.max(min, Math.max(max, min) > min ? Math.min(value, max) : min);
  const below = anchorRect.bottom + gap;
  const top = below + menuHeight <= bounds.bottom ? below : clamp(anchorRect.top - menuHeight - gap, bounds.top + gap, bounds.bottom - menuHeight - gap);
  // The title, not the date/action width of the whole row, owns the menu anchor.
  // Aligning the menu's leading edge with that title keeps the popup visually
  // attached to the conversation users actually long-pressed.
  return { x: clamp(anchorRect.left, bounds.left + gap, bounds.right - menuWidth - gap), y: top };
}

export function localMessagePathState({ native, hasWorkspace }) {
  if (!native) return { enabled: false, label: 'Web 预览' };
  if (!hasWorkspace) return { enabled: false, label: '需要本地工作区' };
  return { enabled: true, label: '本地记录' };
}

export function clampDesktopSidebarWidth(value, viewportWidth) {
  const maximum = Math.max(220, Math.min(440, Number(viewportWidth || 0) - 420));
  return Math.round(Math.max(220, Math.min(maximum, Number(value) || 256)));
}

export function deriveDesktopDualPathState({ connection } = {}) {
  const providerConfigured = connection?.providerConfiguration === 'READY_FOR_GUARD';
  const syncConfigured = connection?.syncCapability === 'ENCRYPTED_SYNC_READY';
  return {
    local: {
      label: '本地可用',
      detail: 'Rust SQLite 继续拥有本地工作区；未配置网络也能读取和编辑。',
    },
    provider: providerConfigured
      ? { label: '已配置，仍需逐次确认', detail: '每次发送前显示 Provider、模型、范围和费用；本页面不会自动外发。' }
      : { label: '联网未配置', detail: '没有读取 Key、没有构造 Authorization，也没有 Provider 请求。' },
    sync: syncConfigured
      ? { label: '配置已发现，等待已验证账号', detail: '登录、恢复码和数据方向完成前不会同步。' }
      : { label: '加密同步未配置', detail: '本地数据仍可用，不会排队同步或伪造云端成功。' },
  };
}

function conversationRows(conversations, selectedConversationId, { archived = false, favoriteIds = new Set(), unreadIds = new Set(), manualUnreadAtMs = new Map() } = {}) {
  if (!conversations.length) return `<p class="chat-history-empty">${archived ? '还没有已归档会话' : '还没有本地会话'}</p>`;
  return conversations.map(item => {
    const manuallyUnread = manualUnreadTimestamp(manualUnreadAtMs, item.id) > 0;
    const unread = unreadIds.has(item.id) || manuallyUnread;
    const running = (item.messages || []).some(message => String(message.delivery || '').toUpperCase() === 'PARTIAL' && message.runtimeState === 'RUNNING');
    return `
    <div class="chat-history-row ${item.id === selectedConversationId ? 'selected' : ''}" data-conversation-row data-id="${escapeHtml(item.id)}" data-revision="${item.revision}" data-pinned="${Boolean(item.pinned)}" data-favorite="${favoriteIds.has(item.id)}" data-archived="${Boolean(item.archived)}">
      <button class="chat-history-select" data-action="select-chat" data-id="${escapeHtml(item.id)}" title="${escapeHtml(item.title || '未命名会话')} · ${escapeHtml(conversationLocalDate(item))}">
        <span class="chat-history-title-line">
          ${item.pinned ? `<i class="chat-history-conversation-icon" aria-label="已置顶">${icon(settingsIcons.conversation, '对话')}</i>` : ''}
          ${unread ? `<i class="chat-history-unread" aria-label="${manuallyUnread ? '已标记未读' : '有未查看的新内容'}"></i>` : ''}
          ${running ? '<i class="chat-history-running" aria-label="正在生成"></i>' : ''}
          <span class="chat-history-title">${escapeHtml(item.title || '未命名会话')}</span>
          ${favoriteIds.has(item.id) ? `<i class="chat-history-favorite" aria-label="已收藏">${icon(icons.star, '已收藏')}</i>` : ''}
        </span>
      </button>
      <small class="chat-history-date">${escapeHtml(conversationLocalDate(item))}</small>
      <div class="chat-row-actions" aria-label="${escapeHtml(item.title || '未命名会话')}的操作"><button class="chat-row-action-icon" data-action="open-conversation-row-menu" data-overlay-trigger aria-label="对话更多操作">${icon(icons.more, '更多')}</button>
        ${archived
          ? `<button class="chat-row-action-icon" data-action="restore-conversation" data-id="${escapeHtml(item.id)}" data-revision="${item.revision}" aria-label="恢复会话" title="恢复会话">${icon(icons.restore, '恢复')}</button>`
          : `<button class="chat-row-action-icon" data-action="set-conversation-pinned" data-id="${escapeHtml(item.id)}" data-revision="${item.revision}" data-pinned="${!item.pinned}" aria-label="${item.pinned ? '取消置顶会话' : '置顶会话'}" title="${item.pinned ? '取消置顶会话' : '置顶会话'}">${icon(item.pinned ? icons.pushPinOff : icons.pushPin, item.pinned ? '取消置顶' : '置顶')}</button><button class="chat-row-action-icon" data-action="archive-conversation" data-id="${escapeHtml(item.id)}" data-revision="${item.revision}" aria-label="归档会话" title="归档会话">${icon(icons.archive, '归档')}</button>`}
      </div>
    </div>
  `;
  }).join('');
}

export function conversationLocalDate(item) {
  const value = new Date(item?.createdAt || item?.updatedAt || 0);
  if (Number.isNaN(value.getTime())) return '本地时间未知';
  return new Intl.DateTimeFormat('zh-CN', { year: 'numeric', month: '2-digit', day: '2-digit' }).format(value).replaceAll('-', '/');
}

function sidebarFunctions({ activeWorkMode, pane, data, workspaces, selectedWorkProjectId, selectedConversationId }) {
  const item = (action, glyph, label, active = false) => `<button class="chat-sidebar-function ${active ? 'selected' : ''}" data-action="${action}" aria-label="${label}"><span aria-hidden="true">${icon(glyph, label)}</span><span class="rail-label">${label}</span></button>`;
  return `<section class="chat-sidebar-functions" aria-label="侧栏功能">
    ${item('show-reminders', icons.clock, '定时任务', pane === 'reminders')}
    ${item('show-transcription', icons.fileText, '南枫转写', pane === 'transcription')}
    ${activeWorkMode ? workNavigation(pane, data, workspaces, selectedWorkProjectId, selectedConversationId) : ''}
  </section>`;
}

function searchedConversations(data, search, { archived = false, pinned = false } = {}) {
  const normalizedSearch = String(search || '').trim().toLocaleLowerCase();
  const source = archived ? archivedConversations(data) : activeConversations(data);
  return source.filter(item => Boolean(item.pinned) === pinned && (!normalizedSearch || String(item.title || '').toLocaleLowerCase().includes(normalizedSearch)));
}

/** The rail previews only already-rendered message text and never stores it. */
function transcriptPositionRail(conversation) {
  const messages = conversation?.messages || [];
  if (messages.length < 2) return '';
  const slots = Math.min(12, messages.length);
  return `<aside class="chat-transcript-rail" aria-label="对话位置导航">${Array.from({ length: slots }, (_, slot) => {
    const index = slots === 1 ? 0 : Math.round(((messages.length - 1) * slot) / (slots - 1));
    const block = messages[index]?.blocks?.find(item => item.kind === 'TEXT');
    const preview = String(block?.text || '').replaceAll(/\s+/g, ' ').trim().slice(0, 48) || '空消息';
    return `<button data-action="jump-transcript-position" data-index="${index}" data-rail-slot="${slot}" aria-label="跳转到对话位置 ${slot + 1}/${slots}"><span aria-hidden="true"></span><em>${escapeHtml(preview)}</em></button>`;
  }).join('')}</aside>`;
}

/** P6-F reads only per-message persisted facts; it never falls back to current model settings. */
export function transcriptMetadata(message) {
  const snapshot = message?.modelSnapshot;
  const actual = typeof message?.actualModelId === 'string' && message.actualModelId.trim();
  const model = actual || snapshot?.displayName || snapshot?.modelId || (typeof message?.importedModel === 'string' && message.importedModel.trim()) || null;
  const created = Date.parse(message?.createdAt || '');
  const timestamp = Number.isFinite(created)
    ? new Intl.DateTimeFormat('zh-CN', { month: 'numeric', day: 'numeric', hour: 'numeric', minute: '2-digit' }).format(new Date(created))
    : null;
  const source = ['IMPORTED', 'CACHE', 'PROVIDER', 'LOCAL_FIXTURE'].includes(message?.source) ? message.source : null;
  return { source, model, timestamp, workDuration: assistantWorkDuration(message) };
}

export function compactModelName(value) {
  const normalized = String(value || '').replace(/^(?:OpenRouter|Google|通义千问|Qwen 官方直连|DeepSeek 官方直连|智谱 BigModel 官方直连|智谱官方直连)\s*(?:[·:：]\s*)/u, '').split(' · ')[0].trim();
  return ({
    'Claude Fable 5': 'Fable 5', 'Claude Opus 5': 'Opus 5', 'Claude Sonnet 5': 'Sonnet 5', 'Claude Haiku 4.5': 'Haiku 4.5',
    'GPT-5.6 Sol': '5.6 Sol', 'GPT-5.6 Terra': '5.6 Terra', 'GPT-5.6 Luna': '5.6 Luna',
    'Grok 4.1 Fast': '4.1 Fast', 'Grok 4.6 High': '4.6 High', 'Gemini 3.7 Flash': 'Gemini 3.7',
    'Qwen3.7-Plus': 'Qwen 3.7', 'Qwen3.8-Max': 'Qwen 3.8', 'Qwen3.6 Flash': 'Qwen 3.6',
    'DeepSeek V4 Pro': 'DS V4', 'DeepSeek V4 Flash': 'DS V4', 'GLM-5.3': 'GLM 5.3', 'GLM-5.3 Flash': 'GLM 5.3', 'GLM-OCR': 'GLM-OCR',
  })[normalized] || normalized;
}

const DAILY_COMPOSER_MODEL_IDS = Object.freeze([
  'CLAUDE_SONNET_5', 'DEEPSEEK_V4_FLASH', 'GPT_5_6_TERRA',
  'GLM_5_3_FLASH', 'QWEN_3_7_PLUS', 'GEMINI_3_7_FLASH',
]);
const DEEP_COMPOSER_MODEL_IDS = Object.freeze([
  'CLAUDE_FABLE_5_1', 'CLAUDE_OPUS_5', 'GPT_6_ASTRA', 'DEEPSEEK_V4_PRO',
  'GPT_5_6_SOL', 'GLM_5_3', 'QWEN_3_8_MAX',
]);

const COMPOSER_MODEL_PROVIDER_LABELS = Object.freeze({
  OPENROUTER: 'OpenRouter',
  DEEPSEEK: 'DeepSeek',
  ZHIPU: '智谱',
  QWEN: '千问',
});

function normalizedModelPickerDate(value) {
  const candidate = value instanceof Date ? value : new Date(value ?? Date.now());
  return Number.isNaN(candidate.getTime()) ? new Date() : candidate;
}

export function deepSeekPricingLabelAt(value = Date.now()) {
  const hour = normalizedModelPickerDate(value).getUTCHours();
  return (hour >= 1 && hour < 4) || (hour >= 6 && hour < 10) ? '当前高峰' : '当前低谷';
}

export function millisecondsUntilDeepSeekPricingTransition(value = Date.now()) {
  const current = normalizedModelPickerDate(value);
  const transitions = [1, 4, 6, 10, 25].map(hour => Date.UTC(
    current.getUTCFullYear(), current.getUTCMonth(), current.getUTCDate(), hour,
  ));
  return Math.max(250, transitions.find(instant => instant > current.getTime()) - current.getTime());
}

export function composerModelRuntimeDetail(candidate, webSearchEnabled, now = Date.now()) {
  const providerId = String(candidate?.providerId || '').toUpperCase();
  const state = webSearchEnabled ? '实时联网' : '未联网';
  if (providerId === 'DEEPSEEK') {
    const emphasizedPrefix = deepSeekPricingLabelAt(now);
    return { text: `${emphasizedPrefix} · ${state}`, emphasizedPrefix };
  }
  return { text: `${COMPOSER_MODEL_PROVIDER_LABELS[providerId] || '模型服务'} · ${state}` };
}

function renderComposerModelSheetCard({ action, attributes = '', label, detail = null, selected = false, showNext = false, disabled = false }) {
  const detailHtml = detail?.text
    ? `<small>${detail.emphasizedPrefix ? `<b>${escapeHtml(detail.emphasizedPrefix)}</b>${escapeHtml(detail.text.slice(detail.emphasizedPrefix.length))}` : escapeHtml(detail.text)}</small>`
    : '';
  const trailing = selected
    ? icon(icons.check, '当前模型')
    : showNext ? icon(icons.chevronRight, `展开${label}`) : '';
  return `<button class="composer-model-sheet-card" data-action="${action}" ${attributes} aria-selected="${selected}" ${disabled ? 'disabled' : ''}><span><strong>${escapeHtml(label)}</strong>${detailHtml}</span>${trailing ? `<i class="composer-model-sheet-trailing">${trailing}</i>` : ''}</button>`;
}

function renderComposerModelSheetFrame({ title, root = true, closeAction = 'close-p6g-model-picker', body, ariaLabel = '选择模型' }) {
  const navigation = root
    ? `<button class="composer-model-sheet-nav" data-action="${closeAction}" aria-label="关闭模型选择">${icon(icons.close, '关闭')}</button>`
    : `<button class="composer-model-sheet-nav" data-action="p6g-picker-back" aria-label="返回模型分组">${icon(icons.chevronLeft, '返回')}</button>`;
  return `<span class="composer-transient-sheet-layer composer-model-sheet-layer"><button class="composer-transient-sheet-dismiss composer-model-sheet-scrim" data-action="${closeAction}" aria-label="关闭模型选择"></button><section class="composer-transient-sheet composer-model-sheet" role="dialog" aria-modal="true" aria-label="${escapeHtml(ariaLabel)}"><span class="composer-model-sheet-handle" aria-hidden="true"></span><header class="composer-model-sheet-header">${navigation}<strong>${escapeHtml(title)}</strong><span></span></header><div class="composer-model-sheet-body">${body}</div></section></span>`;
}

const composerModelSheetSection = label => `<small class="composer-model-sheet-section">${escapeHtml(label)}</small>`;

function composerAddIconSurface(nodes, label, accent = false) {
  return `<i class="composer-add-icon-surface${accent ? ' is-accent' : ''}" aria-hidden="true">${icon(nodes, label)}</i>`;
}

function renderComposerAddSheetFrame({ page = 'root', body }) {
  const label = page === 'tone' ? '基础风格和语气' : '添加到草稿';
  return `<span class="composer-transient-sheet-layer composer-add-sheet-layer"><button class="composer-transient-sheet-dismiss composer-add-sheet-dismiss" data-action="close-composer-add" aria-label="关闭${label}"></button><section class="composer-transient-sheet composer-add-sheet" role="dialog" aria-modal="true" aria-label="${label}"><div class="composer-transient-sheet-body composer-add-sheet-body">${body}</div></section></span>`;
}

/**
 * The model-service catalog is the product catalog. P6-G may carry historical selections, but
 * its local acceptance fixture is never a user-selectable Composer model.
 */
export function desktopComposerModelCandidates(modelServiceSettings = [], p6gCandidates = []) {
  const presetById = new Map((modelServiceSettings || []).flatMap(service =>
    (service.presets || []).filter(item => item.chatSelectable !== false).map(item => [item.id, { ...item, providerId: service.providerId }]),
  ));
  const reviewed = [
    ...DAILY_COMPOSER_MODEL_IDS.map(modelId => ({ modelId, tier: 'BALANCED' })),
    ...DEEP_COMPOSER_MODEL_IDS.map(modelId => ({ modelId, tier: 'DEEP' })),
  ].map(({ modelId, tier }) => {
    const preset = presetById.get(modelId);
    return preset ? {
      providerFamily: String(preset.family || '').startsWith('Anthropic') ? 'ANTHROPIC' : 'OTHER',
      providerId: preset.providerId,
      modelId,
      displayName: preset.displayName,
      description: preset.description,
      tiers: [tier],
      capabilities: ['TEXT'],
      available: true,
    } : null;
  }).filter(Boolean);
  const reviewedIds = new Set(reviewed.map(item => item.modelId));
  const extras = (p6gCandidates || []).filter(item => item?.modelId
    && !reviewedIds.has(item.modelId)
    && item.modelId !== 'KIMI_K3'
    && item.displayName !== 'Kimi K3'
    && item.providerFamily !== 'LOCAL'
    && item.providerId !== 'local-fixture'
    && !String(item.modelId).startsWith('local-p6g-fixture-'));
  return [...reviewed, ...extras];
}

export function assistantCostCny(chargeMicros, currencyCode = 'USD') {
  if (!Number.isSafeInteger(chargeMicros)) return null;
  const rate = currencyCode === 'CNY' ? 1 : currencyCode === 'USD' ? 6.720309145556033 : null;
  if (rate == null) return null;
  const units = Math.floor((chargeMicros * rate / 100) + 0.5);
  return `¥${(units / 10_000).toFixed(4)}`;
}

export function canonicalMessageRole(message) {
  const role = String(message?.role || '').trim().toLocaleLowerCase();
  return ['assistant', 'user', 'tool'].includes(role) ? role : 'tool';
}

/** P6-044: only an explicit persisted run/import boundary may produce a duration label. */
export function assistantWorkDuration(message) {
  if (canonicalMessageRole(message) !== 'assistant') return null;
  const explicitMillis = [message?.run?.durationMs, message?.import?.durationMs]
    .find(value => Number.isSafeInteger(value) && value > 0);
  const started = Date.parse(message?.run?.startedAt || '');
  const completed = Date.parse(message?.run?.completedAt || '');
  const millis = explicitMillis ?? (Number.isFinite(started) && Number.isFinite(completed) && completed > started ? completed - started : null);
  if (!Number.isSafeInteger(millis) || millis <= 0) return null;
  const seconds = millis / 1000;
  if (seconds < 60) return `用时 ${Number.isInteger(seconds) ? seconds : seconds.toFixed(1)} 秒`;
  return `用时 ${Math.floor(seconds / 60)} 分 ${Math.floor(seconds % 60)} 秒`;
}

export function messagePlainText(message) {
  return (message?.blocks || []).map(block => block.kind === 'ASSET_REF'
    ? `${block.asset?.displayName || '本地附件'} · ${block.asset?.mimeType || '未知类型'}`
    : String(block.text || '')).filter(Boolean).join('\n');
}

export function transcriptLocalDateKey(message) {
  const value = Date.parse(message?.createdAt || '');
  return Number.isFinite(value)
    ? new Intl.DateTimeFormat('zh-CN', { year: 'numeric', month: 'long', day: 'numeric' }).format(new Date(value))
    : '本地日期未知';
}

function localAttachmentDisplay(asset, imageThumbnails = {}) {
  const id = String(asset?.id || '');
  const assetMimeType = String(asset?.mimeType || '');
  const capability = attachmentPreviewCapability(asset);
  const name = escapeHtml(asset?.displayName || '本地附件');
  const mime = escapeHtml(assetMimeType);
  const info = `<span class="chat-attachment-info-overlay"><b>${name}</b>${mime ? `<small>${mime}</small>` : ''}</span>`;
  const previewGlyph = `<span class="chat-attachment-preview-glyph" aria-hidden="true">${icon(icons.import, '附件')}</span>`;
  if (id && capability.kind === 'pdf') return `<button class="chat-document-attachment" data-action="open-pdf-preview" data-attachment-id="${escapeHtml(id)}" aria-label="阅读 PDF：${name}" title="阅读 PDF">${previewGlyph}${info}</button>`;
  // A lazy video preview deliberately has no square crop: the opened local player uses
  // the file's intrinsic dimensions.  A future thumbnail may be inserted without changing
  // this non-document container.
  if (id && capability.kind === 'video') return `<button class="chat-video-attachment" data-action="open-video-preview" data-attachment-id="${escapeHtml(id)}" aria-label="播放视频：${name}" title="播放视频">${previewGlyph}${info}</button>`;
  if (id && capability.kind === 'audio') return `<button class="chat-audio-attachment" data-action="open-audio-preview" data-attachment-id="${escapeHtml(id)}" aria-label="播放音频：${name}" title="播放音频">${previewGlyph}${info}</button>`;
  if (id && capability.kind === 'text') return `<button class="chat-document-attachment" data-action="open-text-preview" data-attachment-id="${escapeHtml(id)}" aria-label="预览文本：${name}" title="预览文本">${previewGlyph}${info}</button>`;
  if (!id || capability.kind !== 'image') return id
    ? `<button class="chat-document-attachment" data-action="open-preview-boundary" data-attachment-id="${escapeHtml(id)}" data-attachment-name="${name}" data-attachment-mime="${mime}" aria-label="${capability.label}：${name}" title="${capability.label}">${previewGlyph}${info}</button>`
    : `<span class="chat-document-attachment" tabindex="0" aria-label="附件预览不可用：${name}">${previewGlyph}${info}</span>`;
  const preview = imageThumbnails[id];
  const media = preview?.dataUrl
    ? `<img src="${escapeHtml(preview.dataUrl)}" alt="${name} 本地图片缩略图" loading="lazy">`
    : `<span class="chat-image-placeholder">${escapeHtml(preview?.error || '正在读取本地缩略图')}</span>`;
  return `<button class="chat-image-attachment" data-action="open-image-preview" data-attachment-id="${escapeHtml(id)}" data-image-thumbnail="${escapeHtml(id)}" aria-label="预览图片：${name}" title="预览图片">${media}${info}</button>`;
}

function composerAttachmentDisplay(item, imageThumbnails = {}, { canReadThumbnail = true } = {}) {
  const id = String(item?.id || '');
  const name = escapeHtml(item?.displayName || '本地附件');
  const mime = escapeHtml(item?.mimeType || '');
  const remove = `<button data-action="remove-composer-attachment" data-id="${escapeHtml(id)}" aria-label="移除附件：${name}">${icon(icons.close, '移除附件')}</button>`;
  if (!String(item?.mimeType || '').startsWith('image/')) {
    if (!canReadThumbnail) return `<span class="chat-attachment-chip">${name}${mime ? ` · ${mime}` : ''}${remove}</span>`;
    return `<span class="chat-composer-file-preview">${localAttachmentDisplay(item, imageThumbnails)}${remove}</span>`;
  }
  const preview = imageThumbnails[id];
  const media = preview?.dataUrl
    ? `<img src="${escapeHtml(preview.dataUrl)}" alt="${name} 草稿缩略图">`
    : `<span class="chat-composer-image-placeholder" aria-hidden="true">${icon(icons.image, '图片')}</span>`;
  const image = canReadThumbnail && id
    ? `<button class="chat-composer-image-open" data-action="open-image-preview" data-attachment-id="${escapeHtml(id)}" data-image-thumbnail="${escapeHtml(id)}" aria-label="预览图片：${name}" title="预览图片">${media}<span class="chat-composer-image-name">${name}</span></button>`
    : `<span class="chat-composer-image-open">${media}<span class="chat-composer-image-name">${name}</span></span>`;
  return `<span class="chat-composer-image-preview">${image}${remove}</span>`;
}

function temporaryTranscript(temporaryConversation) {
  const attachments = new Map((temporaryConversation?.attachments || []).map(item => [item.id, item]));
  return {
    id: `temporary:${temporaryConversation?.temporaryId || 'current'}`,
    messages: (temporaryConversation?.messages || []).map((message, index) => ({
      id: message.id || `temporary-message-${index}`,
      role: 'user',
      createdAt: message.createdAt || temporaryConversation?.updatedAt || temporaryConversation?.createdAt || null,
      blocks: [
        ...(message.text ? [{ kind: 'TEXT', text: message.text }] : []),
        ...(message.attachmentIds || []).map(id => ({ kind: 'ASSET_REF', asset: attachments.get(id) })).filter(block => block.asset),
      ],
    })),
  };
}

function highlightedText(value, query) {
  const source = String(value || '');
  const needle = String(query || '').trim();
  if (!needle) return escapeHtml(source);
  const lowerSource = source.toLocaleLowerCase();
  const lowerNeedle = needle.toLocaleLowerCase();
  const parts = [];
  let offset = 0;
  while (parts.length < 400) {
    const index = lowerSource.indexOf(lowerNeedle, offset);
    if (index < 0) break;
    parts.push(escapeHtml(source.slice(offset, index)), `<mark>${escapeHtml(source.slice(index, index + needle.length))}</mark>`);
    offset = index + Math.max(needle.length, 1);
  }
  parts.push(escapeHtml(source.slice(offset)));
  return parts.join('');
}

export function hasExplicitReminderIntent(value) {
  return /(提醒我|记得提醒|到时提醒|每小时|每天|每周|持续监控|监控一下|持续跟踪|有变化.{0,12}(告诉|通知)|有结果.{0,12}(告诉|通知))/u.test(String(value || ''));
}

function ordinaryChatFailureCopy(code) {
  return ({
    PROVIDER_NOT_ENABLED: '本次实际接收服务商未启用。请在“设置 → 模型与联网”启用服务商并配置凭据后重试。',
    SELECTED_MODEL_UNAVAILABLE: '当前会话指定的模型未启用或缺少凭据；未自动换模型。',
    CREDENTIAL: '本次无法读取服务商凭据，请检查钥匙串授权后重试。',
    CREDENTIAL_DENIED: '未获准读取密钥，已停止。现有密钥未删除；可主动重试授权。',
    CREDENTIAL_MISSING: '尚未保存该服务商密钥，请在模型设置中保存。',
    CREDENTIAL_UNAVAILABLE: '钥匙串暂不可用，请解锁后重试。现有密钥未删除。',
    WEB_SEARCH_NO_VERIFIED_SOURCES: '服务商未返回可验证的公开来源，本次没有保存为完整回答。',
  })[String(code || '')] || '本次没有形成完整回答。请检查模型与网络设置后显式重试。';
}

function ordinaryChatUnknownCopy(code) {
  return ({
    TIMEOUT: '连接或流式响应超过等待时间，结果未确认。',
    NETWORK: '未能建立连接或连接中断，结果未确认。',
    MISSING_COMPLETION: '连接结束但没有收到明确完成事件，结果未确认。',
    PROCESS_INTERRUPTED: '应用中断前没有收到明确完成结果。',
    LOCAL_RUNTIME_STATE_MISSING: '旧记录没有可验证的运行状态，不能继续显示为生成中。',
  })[String(code || '')] || '本次连接结果未确认；系统不会自动重复发送。';
}

function messageList(conversation, { imageThumbnails = {}, interactive = true, ariaLabel = '当前会话消息', findQuery = '', activeFindMessageId = null, contextSelectionRecords = [], productSettings = {}, settingsCapabilities = {}, reminders = { drafts: [] } } = {}) {
  if (!conversation) {
    return '<section class="chat-empty-canvas" aria-label="空对话画布"></section>';
  }
  return `
    <section class="chat-thread" aria-label="${escapeHtml(ariaLabel)}">
      ${conversation.importedFrom === 'CHATGPT_EXPORT' ? '<p class="chat-import-note" role="note">从 ChatGPT 导入</p>' : conversation.importedFrom === 'CLAUDE_EXPORT' ? '<p class="chat-import-note" role="note">从 Claude 导入 · 本地静态文本</p>' : ''}
      ${conversation.messages.map((message, index) => {
        const role = canonicalMessageRole(message);
        const isAssistant = role === 'assistant';
        const metadata = transcriptMetadata(message);
        const payload = messagePlainText(message);
        const reasoningBlocks = message.blocks.filter(block => block.kind === 'REASONING');
        const textBlocks = message.blocks.filter(block => !['ASSET_REF', 'REASONING'].includes(block.kind));
        const attachmentBlocks = message.blocks.filter(block => block.kind === 'ASSET_REF');
        const dateKey = transcriptLocalDateKey(message);
        const previousDateKey = index ? transcriptLocalDateKey(conversation.messages[index - 1]) : null;
        const matchesFind = Boolean(findQuery) && textBlocks.some(block => String(block.text || '').toLocaleLowerCase().includes(String(findQuery).trim().toLocaleLowerCase()));
        const delivery = String(message.delivery || 'COMPLETE');
        const runtimeState = String(message.runtimeState || '');
        const isRunning = delivery === 'PARTIAL' && runtimeState === 'RUNNING';
        const effectiveDelivery = delivery === 'PARTIAL' && !isRunning ? 'UNKNOWN' : delivery;
        const runtimeSafeErrorCode = message.runtimeSafeErrorCode || message.safeErrorCode || (delivery === 'PARTIAL' && !isRunning ? 'LOCAL_RUNTIME_STATE_MISSING' : '');
        const compareExecutionId = typeof message.compareExecutionId === 'string' ? message.compareExecutionId : '';
        const compareLogicalModel = message.compareLogicalModel === 'CHATGPT' ? 'ChatGPT' : message.compareLogicalModel === 'CLAUDE' ? 'Claude' : '';
        const sourceUser = index > 0 ? conversation.messages[index - 1] : null;
        const reminderSuggestion = interactive && index === conversation.messages.length - 1 && isAssistant && delivery === 'COMPLETE' && canonicalMessageRole(sourceUser) === 'user' && productSettings.reminderSuggestions && settingsCapabilities.reminderSuggestions && hasExplicitReminderIntent(messagePlainText(sourceUser)) && !(reminders.drafts || []).some(item => item.sourceAssistantMessageId === message.id)
          ? `<button class="chat-reminder-suggestion" data-action="generate-reminder-draft" data-user-message-id="${escapeHtml(sourceUser.id)}" data-assistant-message-id="${escapeHtml(message.id)}">添加提醒 / 监控</button>`
          : '';
        const runtimeStatus = !isAssistant || effectiveDelivery === 'COMPLETE' ? ''
          : isRunning ? `<div class="chat-runtime-state running" role="status" aria-live="polite"><i class="chat-runtime-spinner" aria-hidden="true"></i><span><strong>正在生成回复</strong><small>正在接收模型输出；已生成内容会持续保存。</small></span><button data-action="${compareExecutionId ? 'stop-desktop-compare' : 'stop-ordinary-chat'}" ${compareExecutionId ? `data-execution-id="${escapeHtml(compareExecutionId)}"` : ''}>停止生成</button></div>`
          : effectiveDelivery === 'UNKNOWN' ? compareExecutionId
            ? `<div class="chat-runtime-state unknown" role="status"><span><strong>连接结果未知，未自动重发${runtimeSafeErrorCode ? ` · ${escapeHtml(runtimeSafeErrorCode)}` : ''}</strong><small>${escapeHtml(ordinaryChatUnknownCopy(runtimeSafeErrorCode))} 历史 Compare 不再提供重试；内容与归因保持只读。</small></span></div>`
            : message.attemptId ? `<div class="chat-runtime-state unknown" role="status"><span><strong>连接结果未知，未自动重发${runtimeSafeErrorCode ? ` · ${escapeHtml(runtimeSafeErrorCode)}` : ''}</strong><small>${escapeHtml(ordinaryChatUnknownCopy(runtimeSafeErrorCode))} 已保留现有内容；请明确重试。</small></span><button data-action="retry-ordinary-chat" data-attempt-id="${escapeHtml(message.attemptId)}">重试原 Attempt</button></div>`
            : `<div class="chat-runtime-state unknown" role="status"><span><strong>生成已中断，未收到明确完成结果 · ${escapeHtml(runtimeSafeErrorCode || 'LOCAL_RUNTIME_STATE_MISSING')}</strong><small>${escapeHtml(ordinaryChatUnknownCopy(runtimeSafeErrorCode))}</small></span></div>`
          : effectiveDelivery === 'CANCELLED' ? `<div class="chat-runtime-state cancelled" role="status"><strong>已停止，已生成内容已保留</strong></div>`
          : compareExecutionId
            ? `<div class="chat-runtime-state failed" role="status"><span><strong>回答未完成</strong><small>${escapeHtml(ordinaryChatFailureCopy(message.safeErrorCode))} · 历史 Compare 不再提供重试。</small></span></div>`
            : `<div class="chat-runtime-state failed" role="status"><span><strong>回答未完成</strong><small>${escapeHtml(ordinaryChatFailureCopy(message.safeErrorCode))}</small></span><button data-action="retry-ordinary-chat" data-attempt-id="${escapeHtml(message.attemptId || '')}">重试原 Attempt</button></div>`;
        const cost = assistantCostCny(message.chargeMicros, message.currencyCode || 'USD');
        if (metadata.model) metadata.model = compactModelName(metadata.model);
        return `
        ${dateKey !== previousDateKey ? `<div class="chat-date-divider" role="separator">${escapeHtml(dateKey)}</div>` : ''}
        <article class="chat-message ${escapeHtml(role)}${matchesFind ? ' find-match' : ''}${activeFindMessageId === message.id ? ' find-active' : ''}" data-message-id="${escapeHtml(message.id)}" tabindex="0">
          <div class="chat-message-content">
            ${compareLogicalModel ? `<p class="chat-compare-branch"><strong>${compareLogicalModel}</strong><span>OpenRouter · 独立分支</span></p>` : ''}
            ${metadata.workDuration ? `<p class="chat-message-work-duration">${escapeHtml(metadata.workDuration)}</p>` : ''}
            ${attachmentBlocks.length ? `<div class="chat-message-attachments" aria-label="本地附件预览">${attachmentBlocks.map(block => localAttachmentDisplay(block.asset, imageThumbnails)).join('')}</div>` : ''}
            ${reasoningBlocks.length ? `<details class="chat-reasoning"><summary>思考过程</summary><div class="chat-markdown-body">${reasoningBlocks.map(block => renderSafeMarkdown(block.text || '', { query: findQuery })).join('')}</div></details>` : ''}
            ${textBlocks.length ? `<div class="chat-message-bubble"><div class="chat-message-body ${isAssistant ? 'chat-markdown-body' : ''}">${textBlocks.map(block => isAssistant ? renderSafeMarkdown(block.text || '', { query: findQuery }) : `<p>${highlightedText(block.text || '', findQuery)}</p>`).join('')}</div></div>` : ''}
            ${runtimeStatus}
            ${(metadata.timestamp || metadata.model || metadata.source || (interactive && payload)) ? `<div class="chat-message-tools">${interactive && payload ? `<div class="chat-message-actions"><button class="chat-message-action-icon" data-action="copy-message" data-message-id="${escapeHtml(message.id)}" aria-label="复制消息" title="复制消息">${icon(icons.copy, '复制')}</button><button class="chat-message-action-icon" data-action="share-message" data-message-id="${escapeHtml(message.id)}" aria-label="分享" title="分享">${icon(icons.share, '分享')}</button>${isAssistant ? `<button class="chat-message-action-icon" data-action="open-assistant-message-menu" data-message-id="${escapeHtml(message.id)}" aria-label="更多操作" title="更多操作">${icon(icons.more, '更多操作')}</button>` : ''}</div>` : ''}<p class="chat-message-metadata">${[metadata.timestamp, metadata.model].filter(Boolean).map(escapeHtml).join(' · ')}${metadata.source ? `<button class="chat-provenance-badge" data-action="message-provenance" data-message-id="${escapeHtml(message.id)}" aria-label="查看消息来源" title="查看消息来源">${icon(icons.info, '来源')}</button>` : ''}</p></div>` : ''}
            ${cost ? `<p class="chat-message-cost">${escapeHtml(cost)}</p>` : ''}
            ${reminderSuggestion}
          </div>
        </article>
      `;
      }).join('')}
    </section>
  `;
}

function conversationContextMenu(menu, workMode, accountSync = {}) {
  if (!menu) return '';
  const data = `data-id="${escapeHtml(menu.id)}" data-revision="${menu.revision}" data-pinned="${menu.pinned}" data-favorite="${Boolean(menu.favorite)}"`;
  const item = (action, glyph, label, { extra = '', trailing = false, danger = false } = {}) => `<button class="chat-context-menu-item${danger ? ' danger' : ''}" role="menuitem" data-action="${action}" ${data} ${extra}><span class="chat-menu-action-icon" aria-hidden="true">${glyph}</span><span class="chat-menu-action-label">${label}</span>${trailing ? `<span class="chat-menu-action-trailing" aria-hidden="true">${icon(icons.chevronRight, '展开')}</span>` : ''}</button>`;
  const syncLabel = '同步到南枫云';
  const common = `${item('context-menu-pin', icon(menu.pinned ? icons.pushPinOff : icons.pushPin, menu.pinned ? '取消置顶' : '置顶'), menu.pinned ? '取消置顶' : '置顶')}${item('context-menu-unread', icon(icons.visibility, '未读'), '未读')}${item('context-menu-favorite', icon(menu.favorite ? icons.bookmark : icons.bookmarkBorder, menu.favorite ? '取消收藏' : '收藏'), menu.favorite ? '取消收藏' : '收藏')}${item('context-menu-share', icon(icons.share, '分享'), '分享')}${item('context-menu-sync', icon(icons.cloudUpload, syncLabel), syncLabel)}${item('context-menu-find', icon(icons.search, '在聊天中查找'), '在聊天中查找')}`;
  const archive = item('context-menu-archive', icon(menu.archived ? icons.restore : icons.archive, menu.archived ? '恢复' : '归档'), menu.archived ? '恢复' : '归档', { extra: `data-archived="${menu.archived}"` });
  const remove = item('context-menu-delete', icon(icons.trash, '删除'), '删除', { danger: true });
  return `<div class="chat-context-menu" role="menu" aria-label="${workMode ? '工作会话菜单' : '对话会话菜单'}" data-positioned="false"><div class="chat-context-menu-title">${escapeHtml(menu.title || '对话操作')}</div>${common}${archive}${remove}</div>`;
}

function workNavigation(pane, data, _workspaces, selectedWorkProjectId, selectedConversationId) {
  return renderWorkspaceNavigation({ data, pane, selectedWorkProjectId, selectedConversationId });
}

export function renderChatFirstShell({
  data,
  native,
  selectedConversationId,
  selectedWorkProjectId = globalThis.__nanfengDesktopWorkState?.selectedWorkProjectId || null,
  settingsConversationReturn = null,
  composerDraft,
  composerAttachments = [],
  chatSearch,
  searchResults = [],
  searchPanel = false,
  searchCategory = null,
  searchHistory = [],
  searchHistoryOpen = false,
  searchPage = globalThis.__nanfengDesktopSearchState?.searchPage || { hits: searchResults, textCount: 0, attachmentCount: 0, truncated: false },
  searchSortMode = globalThis.__nanfengDesktopSearchState?.searchSortMode || 'default',
  searchFileType = globalThis.__nanfengDesktopSearchState?.searchFileType || 'all',
  searchFileTypeOpen = Boolean(globalThis.__nanfengDesktopSearchState?.searchFileTypeOpen),
  searchLoading = Boolean(globalThis.__nanfengDesktopSearchState?.searchLoading),
  searchError = globalThis.__nanfengDesktopSearchState?.searchError || '',
  searchEvidenceLabel = globalThis.__nanfengDesktopSearchState?.searchEvidenceLabel || '',
  searchHistoryHighlighted = globalThis.__nanfengDesktopSearchState?.searchHistoryHighlighted || null,
  profileOpen,
  sidebarOpen,
  railCollapsed,
  showArchived,
  showDeleted,
  contextMenu,
  composerAddOpen,
  composerAddPage = 'root',
  temporaryModelOpen = false,
  p6gModelPickerOpen = false,
  p6gCatalog = null,
  p6gGlobalDefault = null,
  p6gSelection = null,
  pendingNewConversationModelId = null,
  modelPickerNow = new Date(),
  chatgptTask = null,
  claudeTask = null,
  p6kTask = null,
  p6kLink = globalThis.__nanfengP6kManualLink || {},
  v2CommittedExchanges = globalThis.__nanfengV2CommittedExchanges || [],
  workMode = false,
  workspaces,
  workPanel,
  pane,
  status,
  error,
  connection,
  p6eAcceptance = { enabled: false, receipt: null },
  settingsSection = 'personalization',
  settingsSearch = '',
  sidebarWidth = 256,
  temporaryConversation = null,
  imageThumbnails = {},
  showScrollToLatest = false,
  appearance = { mode: 'system', fontSize: 'standard', themeColor: 'orange' },
  favoriteConversationIds = new Set(),
  unreadConversationIds = new Set(),
  manualUnreadAtMs = new Map(),
  conversationFindOpen = false,
  conversationFindQuery = '',
  conversationFindMatches = [],
  conversationFindIndex = 0,
  runtimeInfo = {},
  productSettings = {},
  conversationPreferences = { revision: 0, toneOverride: null, webSearchOverride: null },
  personalizationDraft = productSettings,
  personalizationDirty = false,
  memorySummaryQuery = '',
  memorySummaryComposer = '',
  memorySummaryNotice = '',
  settingsPicker = null,
  modelServiceSettings = MODEL_SERVICE_PREVIEW_SETTINGS,
  modelProviderId = 'OPENROUTER',
  modelServiceDraft = null,
  modelCredentialDraft = '',
  modelCredentialVisible = false,
  modelSettingsSaving = false,
  modelSettingsTesting = false,
  modelSettingsNotice = '',
  modelSettingsError = '',
  usageLedger = { records: [], inputTokens: 0, outputTokens: 0, cachedInputTokens: 0 },
  usageSection = 'conversation',
  contextSelectionRecords = [],
  diagnosticRecords = [],
  invocationRecords = [],
  privacyInventory = { totalBytes: 0, aggregates: [] },
  localBackup = {},
  accountSync = {},
  accountRecovery = null,
  settingsCapabilities = DESKTOP_SETTINGS_CAPABILITIES,
  reminders = { drafts: [], plans: [], diagnostics: [] },
  reminderNotificationPermission = 'default',
  reminderNotificationBridge = { supported: false, initialized: false, listenerReady: false, safeCode: 'NOT_READ', pendingActionCount: 0 },
  backgroundRuntime = { desiredEnabled: false, installed: false, safeCode: 'NOT_READ' },
}) {
  if (searchPanel) return renderDesktopSearchPage({
    query: chatSearch,
    category: searchCategory || 'all',
    sortMode: searchSortMode,
    fileType: searchFileType,
    fileTypeOpen: searchFileTypeOpen,
    page: searchPage,
    loading: searchLoading,
    error: searchError,
    evidenceLabel: searchEvidenceLabel,
    history: searchHistory,
    historyOpen: searchHistoryOpen,
    historyHighlighted: searchHistoryHighlighted,
    thumbnails: imageThumbnails,
  });
  const lifecycleReadOnly = Boolean(settingsConversationReturn?.readOnly);
  const lifecycleConversation = lifecycleReadOnly
    ? (data?.exchange?.conversations || []).find(item => item.id === selectedConversationId) || null
    : null;
  const workState = resolveWorkConversationState(data, selectedWorkProjectId, selectedConversationId);
  const resolvedConversation = lifecycleConversation || resolveConversation(data, selectedConversationId);
  const conversation = temporaryConversation ? null : pane === 'work' ? workState.conversation : resolvedConversation;
  const explicitTone = CONVERSATION_TONES.some(item => item.id === conversationPreferences?.toneOverride)
    ? conversationPreferences.toneOverride
    : null;
  const effectiveTone = conversationToneDefinition(explicitTone || productSettings.tone);
  const effectiveWebSearch = typeof conversationPreferences?.webSearchOverride === 'boolean'
    ? conversationPreferences.webSearchOverride
    : Boolean(productSettings.webSearchEnabled);
  const composerTonePicker = `<header class="composer-transient-sheet-header"><button class="composer-add-sheet-back" data-action="composer-add-back" aria-label="返回附件菜单">${icon(icons.chevronLeft, '返回')}</button><strong>基础风格和语气</strong><span></span></header><div class="composer-style-options">${CONVERSATION_TONES.map(item => `<button class="composer-add-row composer-style-option-row" data-action="select-conversation-tone" data-tone="${item.id}" aria-selected="${item.id === effectiveTone.id}"><span>${escapeHtml(item.label)}</span>${item.id === effectiveTone.id ? `<i class="composer-style-check" aria-label="当前风格">${icon(icons.check, '当前风格')}</i>` : ''}</button>`).join('')}</div>`;
  const composerPreferenceRows = temporaryConversation ? '' : `<button class="composer-add-row composer-add-style-row" data-action="open-composer-tone-picker">${composerAddIconSurface(icons.sparkles, '基础风格和语气', true)}<span>基础风格和语气</span><i class="composer-add-value"><span>${escapeHtml(effectiveTone.label)}</span>${icon(icons.chevronRight, '选择风格')}</i></button><button type="button" class="composer-add-row composer-add-web-row" data-action="toggle-conversation-web-search" role="switch" aria-checked="${effectiveWebSearch}">${composerAddIconSurface(icons.globe, '实时网页搜索', effectiveWebSearch)}<span>实时网页搜索</span><i class="composer-web-switch"><i></i></i></button>`;
  const composerAddRoot = `<button class="composer-add-row composer-add-attachment-row" data-action="open-composer-camera">${composerAddIconSurface(icons.camera, '打开相机')}<span>相机</span></button><button class="composer-add-row composer-add-attachment-row" data-action="pick-composer-image">${composerAddIconSurface(icons.image, '添加图片')}<span>图片</span></button><button class="composer-add-row composer-add-attachment-row" data-action="pick-composer-file">${composerAddIconSurface(icons.file, '添加文件')}<span>文件</span></button>${composerPreferenceRows}`;
  const showWorkPanel = Boolean(workPanel);
  const activeWorkMode = workMode || (showWorkPanel && !['reminders', 'transcription'].includes(pane));
  const path = temporaryConversation ? { enabled: native, label: '临时本地记录', detail: '本次内容仅保存在隔离恢复记录；不会进入历史或工作区。' } : localMessagePathState({ native, hasWorkspace: Boolean(data) });
  const dualPath = deriveDesktopDualPathState({ connection });
  const workPaneTitle = ({ projects: '项目', knowledge: '知识', memory: '记忆', 'p8-inspect': '本地受控记录', reminders: '定时任务', transcription: '南枫转写' })[pane];
  const title = temporaryConversation ? '临时聊天' : pane === 'work'
    ? (conversation?.title || workState.project?.title || '工作')
    : workPaneTitle || (conversation?.title || '新对话');
  const visibleAttachments = temporaryConversation
    ? (temporaryConversation.attachments || []).filter(item => (temporaryConversation.draftAttachmentIds || []).includes(item.id))
    : composerAttachments;
  const activeAssistantRun = !temporaryConversation && (conversation?.messages || []).find(message => (
    canonicalMessageRole(message) === 'assistant'
      && String(message.delivery || '').toUpperCase() === 'PARTIAL'
      && message.runtimeState === 'RUNNING'
  ));
  const composerStopAction = activeAssistantRun?.compareExecutionId ? 'stop-desktop-compare' : 'stop-ordinary-chat';
  const composerStopAttributes = activeAssistantRun?.compareExecutionId
    ? `data-execution-id="${escapeHtml(activeAssistantRun.compareExecutionId)}"`
    : '';
  const storedP6gCandidates = p6gSelection?.catalog?.snapshot?.candidates || p6gCatalog?.snapshot?.candidates || [];
  const p6gCandidates = desktopComposerModelCandidates(modelServiceSettings, storedP6gCandidates);
  const manualModelId = selectedConversationId
    ? p6gSelection?.conversationOverride?.modelId || null
    : pendingNewConversationModelId ?? p6gSelection?.conversationOverride?.modelId ?? null;
  const manualCandidate = p6gCandidates.find(item => item.modelId === manualModelId);
  const automaticModelLabel = p6gSelection?.lastRoute?.displayName
    || p6gCandidates.find(item => item.modelId === 'DEEPSEEK_V4_FLASH')?.displayName
    || p6gCandidates.find(item => item.available)?.displayName
    || '等待可用模型';
  const p6gLabel = compactModelName(manualModelId ? (manualCandidate?.displayName || manualModelId) : automaticModelLabel);
  const dailyCandidates = p6gCandidates.filter(item => item.available && (item.tiers || []).some(tier => ['FAST', 'BALANCED'].includes(tier)));
  const deepCandidates = p6gCandidates.filter(item => item.available && (item.tiers || []).some(tier => ['DEEP', 'APEX_REVIEW'].includes(tier)));
  const pickerTier = p6gSelection?.pickerTier === 'DEEP' ? 'DEEP' : p6gSelection?.pickerTier === 'DAILY' ? 'DAILY' : null;
  const tierCandidates = pickerTier === 'DEEP' ? deepCandidates : dailyCandidates;
  const dailySelected = Boolean(manualModelId && dailyCandidates.some(item => item.modelId === manualModelId));
  const deepSelected = Boolean(manualModelId && deepCandidates.some(item => item.modelId === manualModelId));
  const p6gRoot = `${composerModelSheetSection('自动选择')}${renderComposerModelSheetCard({
    action: 'select-p6g-auto',
    label: '自动',
    detail: { text: `Auto · ${automaticModelLabel}` },
    selected: !manualModelId,
  })}${composerModelSheetSection('按任务选择')}${renderComposerModelSheetCard({
    action: 'select-p6g-tier',
    attributes: 'data-tier="DAILY"',
    label: '日常',
    detail: { text: '日常问答与轻量任务' },
    selected: dailySelected,
    showNext: !dailySelected,
    disabled: !dailyCandidates.length,
  })}${renderComposerModelSheetCard({
    action: 'select-p6g-tier',
    attributes: 'data-tier="DEEP"',
    label: '深度',
    detail: { text: '复杂推理与专业分析' },
    selected: deepSelected,
    showNext: !deepSelected,
    disabled: !deepCandidates.length,
  })}`;
  const p6gTier = `${composerModelSheetSection('选择具体模型')}${tierCandidates.map(item => renderComposerModelSheetCard({
    action: 'select-p6g-model',
    attributes: `data-model-id="${escapeHtml(item.modelId)}"`,
    label: item.displayName,
    detail: composerModelRuntimeDetail(item, effectiveWebSearch, modelPickerNow),
    selected: item.modelId === manualModelId,
  })).join('')}`;
  const p6gModelSheet = renderComposerModelSheetFrame({
    title: pickerTier ? (pickerTier === 'DEEP' ? '深度' : '日常') : '选择模型',
    root: !pickerTier,
    body: pickerTier ? p6gTier : p6gRoot,
  });
  const temporaryModelId = temporaryConversation?.modelOverrideId || null;
  const temporaryCandidate = p6gCandidates.find(item => item.modelId === temporaryModelId);
  const temporaryModelLabel = temporaryModelId ? compactModelName(temporaryCandidate?.displayName || temporaryModelId) : '自动';
  const temporaryModelSheet = renderComposerModelSheetFrame({
    title: '临时模型标识',
    closeAction: 'close-temporary-model-picker',
    ariaLabel: '选择临时模型标识',
    body: `${composerModelSheetSection('仅用于本机恢复标识')}${renderComposerModelSheetCard({ action: 'select-temporary-auto', label: '自动', selected: !temporaryModelId })}${p6gCandidates.filter(item => item.available).map(item => renderComposerModelSheetCard({ action: 'select-temporary-model', attributes: `data-model-id="${escapeHtml(item.modelId)}"`, label: item.displayName, selected: item.modelId === temporaryModelId })).join('')}`,
  });
  // Pinning stays in one continuous scroll owner, but must remain visibly
  // distinguishable from the recency list.
  const visibleUnreadIds = productSettings.unreadIndicators === false ? new Set() : unreadConversationIds;
  const visibleManualUnreadAtMs = productSettings.unreadIndicators === false ? new Map() : manualUnreadAtMs;
  const listed = activeConversations(data, visibleManualUnreadAtMs);
  const pinned = pinnedConversations(data, visibleManualUnreadAtMs);
  const recent = listed.filter(item => !item.pinned);
  const conversationList = listed.length
    ? `${pinned.length ? `<div class="chat-history-group" role="group" aria-label="置顶会话"><p class="chat-history-label">置顶</p>${conversationRows(pinned, selectedConversationId, { favoriteIds: favoriteConversationIds, unreadIds: visibleUnreadIds, manualUnreadAtMs: visibleManualUnreadAtMs })}</div>` : ''}${recent.length ? `<div class="chat-history-group" role="group" aria-label="最近会话"><p class="chat-history-label">最近</p>${conversationRows(recent, selectedConversationId, { favoriteIds: favoriteConversationIds, unreadIds: visibleUnreadIds, manualUnreadAtMs: visibleManualUnreadAtMs })}</div>` : ''}`
    : conversationRows([], selectedConversationId, { favoriteIds: favoriteConversationIds, unreadIds: visibleUnreadIds, manualUnreadAtMs: visibleManualUnreadAtMs });
  const composer = `
    <section class="chat-composer-wrap" aria-label="消息输入">
      <div class="chat-composer">
        ${visibleAttachments.length ? `<div class="chat-composer-attachments" aria-label="本地附件">${visibleAttachments.map(item => composerAttachmentDisplay(item, imageThumbnails, { canReadThumbnail: !temporaryConversation })).join('')}</div>` : ''}
        ${!temporaryConversation && (String(composerDraft || '').trim() || visibleAttachments.length) ? `<p class="chat-composer-egress-disclosure">点击发送即授权将本条内容${visibleAttachments.length ? '及附件' : ''}交给 ${escapeHtml(p6gLabel)}。费用：服务商按实际用量计费，当前无本地预估。</p>` : ''}
        <div class="chat-composer-actions">
          <div class="chat-composer-controls">
            <span class="composer-add-anchor"><button class="chat-composer-icon chat-composer-add" data-action="toggle-composer-add" data-overlay-trigger aria-label="添加到草稿" title="添加到草稿" aria-expanded="${composerAddOpen}">${icon(icons.plus, '添加到草稿')}</button>${composerAddOpen ? renderComposerAddSheetFrame({ page: composerAddPage, body: composerAddPage === 'tone' && !temporaryConversation ? composerTonePicker : composerAddRoot }) : ''}</span>
            <textarea id="chat-composer" rows="1" maxlength="12000" placeholder="回复 南枫AI" aria-label="输入内容" title="可直接粘贴或拖入图片、PDF、视频和文件">${escapeHtml(composerDraft)}</textarea>
            <div class="chat-composer-primary-actions">
              ${temporaryConversation ? `<span class="p6g-model-anchor"><button class="chat-composer-icon p6g-model-trigger" data-action="toggle-temporary-model" data-overlay-trigger aria-label="选择模型：${escapeHtml(temporaryModelLabel)}" title="选择模型 · ${escapeHtml(temporaryModelLabel)}" aria-expanded="${temporaryModelOpen}"><span>${escapeHtml(temporaryModelLabel)}</span></button>${temporaryModelOpen ? temporaryModelSheet : ''}</span>` : `<span class="p6g-model-anchor"><button class="chat-composer-icon p6g-model-trigger" data-action="toggle-p6g-model-picker" data-overlay-trigger aria-label="选择模型：${escapeHtml(p6gLabel)}" title="选择模型 · ${escapeHtml(p6gLabel)}" aria-expanded="${p6gModelPickerOpen}" ${data && p6gCandidates.length ? '' : 'disabled'}><span>${escapeHtml(p6gLabel)}</span></button>${p6gModelPickerOpen ? p6gModelSheet : ''}</span>`}
              ${activeAssistantRun
                ? `<button class="chat-send chat-composer-icon chat-send-stop" data-action="${composerStopAction}" ${composerStopAttributes} aria-label="停止生成" title="停止生成"><span class="chat-send-stop-mark" aria-hidden="true"></span></button>`
                : `<button class="chat-send chat-composer-icon" data-action="save-local-message" aria-label="发送消息" title="发送消息" ${path.enabled && (String(composerDraft || '').trim() || visibleAttachments.length) ? '' : 'disabled'}>${icon(icons.send, '发送消息')}</button>`}
            </div>
          </div>
        </div>
      </div>
    </section>
  `;
  const searchCategoryLabels = { all: '全部', text: '正文', image: '图片', video: '视频', audio: '音频', file: '文件' };
  const searchCategoryPicker = searchCategory ? `<div class="chat-search-categories" role="tablist" aria-label="搜索类型">${Object.entries(searchCategoryLabels).map(([id, label]) => `<button data-action="open-privacy-search-category" data-category="${id}" role="tab" aria-selected="${searchCategory === id}" class="${searchCategory === id ? 'selected' : ''}">${label}</button>`).join('')}</div>` : '';
  const searchContext = searchCategory ? `${searchCategoryLabels[searchCategory] || '全部'} · 仅安全索引` : `${escapeHtml(chatSearch)} · 仅安全索引`;
  const searchContent = `<section class="chat-scroll" aria-label="搜索结果"><header class="chat-main-header"><div><p>本地搜索</p><h1>搜索结果</h1><small>${searchContext}</small></div><button data-action="close-search">返回对话</button></header>${searchCategoryPicker}${searchResults.length ? `<div class="chat-thread">${searchResults.map(hit => `<button class="chat-history-select" data-action="open-search-result" data-workspace-id="${escapeHtml(hit.workspaceId || data?.summary?.id || '')}" data-id="${escapeHtml(hit.conversationId)}" data-message-id="${escapeHtml(hit.messageId || '')}"><strong>${escapeHtml(hit.title)}</strong><span>${escapeHtml(hit.snippet)}</span></button>`).join('')}</div>` : '<p class="chat-empty">没有匹配的本地内容。</p>'}</section>`;
  const composerDock = `<div class="chat-composer-dock" data-composer-dock="fixed">
      ${showScrollToLatest && conversation ? `<button class="chat-scroll-to-latest" data-action="scroll-to-latest" data-anchor="composer-top" aria-label="到最新消息" title="到最新消息">${icon(icons.chevronRight, '到最新消息')}</button>` : ''}
      ${composer}
    </div>`;
  const transcript = temporaryConversation ? temporaryTranscript(temporaryConversation) : conversation;
  const hasConversationContent = Boolean(transcript?.messages?.length) && !temporaryConversation;
  const activeFindMatch = conversationFindMatches[conversationFindIndex] || null;
  const findBar = conversationFindOpen && conversation ? `<section class="conversation-find" aria-label="在当前对话中查找"><label>${icon(icons.search, '查找')}<input id="conversation-find-input" type="search" placeholder="在当前对话中查找" value="${escapeHtml(conversationFindQuery)}"></label><span>${conversationFindMatches.length ? `${conversationFindIndex + 1} / ${conversationFindMatches.length}` : '0 / 0'}</span><button data-action="conversation-find-previous" ${conversationFindMatches.length ? '' : 'disabled'} aria-label="上一个匹配">${icon(icons.chevronLeft, '上一个匹配')}</button><button data-action="conversation-find-next" ${conversationFindMatches.length ? '' : 'disabled'} aria-label="下一个匹配">${icon(icons.chevronRight, '下一个匹配')}</button><button data-action="close-conversation-find" aria-label="关闭查找">${icon(icons.close, '关闭查找')}</button></section>` : '';
  const ordinaryChatContent = searchPanel ? searchContent : transcript
    ? `${findBar}<div class="chat-transcript-stage"><div class="chat-scroll" data-scroll-owner="message-list" data-conversation-id="${escapeHtml(transcript.id)}" ${temporaryConversation ? 'data-temporary-transcript="true"' : ''} tabindex="0">${messageList(transcript, { imageThumbnails, interactive: !temporaryConversation && !lifecycleReadOnly, ariaLabel: temporaryConversation ? '临时聊天消息' : '当前会话消息', findQuery: temporaryConversation ? '' : conversationFindQuery, activeFindMessageId: activeFindMatch?.messageId || null, contextSelectionRecords: temporaryConversation ? [] : contextSelectionRecords, productSettings, settingsCapabilities, reminders })}</div>${temporaryConversation ? '' : transcriptPositionRail(transcript)}</div>${lifecycleReadOnly ? '<p class="chat-lifecycle-readonly">当前为会话生命周期只读查看；返回原列表后可恢复或永久删除。</p>' : composerDock}`
    : `<div class="chat-empty-stage">${messageList(null)}</div>${composerDock}`;
  const chatContent = pane === 'work'
    ? conversation
      ? ordinaryChatContent
      : `${renderWorkConversationState({ project: workState.project })}${workState.project ? composerDock : ''}`
    : ordinaryChatContent;
  const header = `
      <header class="chat-main-header">
        ${settingsConversationReturn ? `<button class="chat-search-return" data-action="return-to-settings-conversation-list" aria-label="返回${escapeHtml(settingsConversationReturn.label)}">${icon(icons.chevronLeft, `返回${settingsConversationReturn.label}`)}<span>${escapeHtml(settingsConversationReturn.label)}</span></button>` : ''}
        ${globalThis.__nanfengDesktopSearchState?.searchReturnActive ? `<button class="chat-search-return" data-action="return-to-search" aria-label="返回搜索">${icon(icons.chevronLeft, '返回搜索')}<span>搜索</span></button>` : ''}
        <button class="chat-sidebar-toggle" data-action="toggle-chat-sidebar" aria-label="${sidebarOpen ? '关闭导航' : '打开导航'}" title="${sidebarOpen ? '关闭导航' : '打开导航'}" aria-expanded="${sidebarOpen}">${icon(icons.menu, '打开导航')}</button>
        <div class="chat-title"><p>南枫 AI</p><h1>${escapeHtml(title)}</h1></div>
        ${hasConversationContent ? '' : `<div class="chat-mode-switch" aria-label="产品模式">
          <button class="${activeWorkMode ? '' : 'selected'}" data-action="show-chat" aria-pressed="${!activeWorkMode}">对话</button>
          <button class="${activeWorkMode ? 'selected' : ''}" data-action="show-work" aria-pressed="${activeWorkMode}">工作</button>
        </div>`}
        ${hasConversationContent
          ? `<div class="chat-header-content-actions"><button data-action="new-chat" aria-label="新对话" title="新对话">${icon(icons.edit, '新对话')}</button><button class="chat-header-more" data-action="open-conversation-header-menu" data-overlay-trigger aria-label="对话更多操作" title="对话更多操作">${icon(icons.more, '对话更多操作')}</button></div>`
          : `<button class="chat-temporary-button ${temporaryConversation ? 'active' : ''}" data-action="toggle-temporary-chat" aria-label="临时聊天" title="临时聊天" aria-pressed="${Boolean(temporaryConversation)}">${icon(icons.ghost, '临时聊天')}</button>`}
      </header>`;
  const standaloneWorkPanel = ['reminders', 'transcription'].includes(pane);
  const main = pane === 'settings' ? renderAndroidSettingsShell({
    page: settingsSection,
    status: error || status,
    appearance,
    settings: productSettings,
    personalizationDraft,
    personalizationDirty,
    picker: settingsPicker,
    data,
    favoriteConversationIds,
    runtimeInfo,
    connection,
    native,
    p6eAcceptance,
    p6gCatalog,
    p6kTask,
    chatgptTask,
    claudeTask,
    modelServiceSettings,
    modelProviderId,
    modelServiceDraft,
    modelCredentialDraft,
    modelCredentialVisible,
    modelSettingsSaving,
    modelSettingsTesting,
    modelSettingsNotice,
    modelSettingsError,
    usageLedger,
    usageSection,
    contextSelectionRecords,
    diagnosticRecords,
    invocationRecords,
    privacyInventory,
    localBackup,
    accountSync,
    accountRecovery,
    capabilities: settingsCapabilities,
    reminders,
    reminderNotificationPermission,
    reminderNotificationBridge,
    backgroundRuntime,
    memorySummaryQuery,
    memorySummaryComposer,
    memorySummaryNotice,
  }) : showWorkPanel ? `
    <main class="chat-main work-main ${standaloneWorkPanel ? 'standalone-work-main' : ''}">
      ${standaloneWorkPanel ? '' : header}
      <div class="chat-status ${error ? 'error' : ''}" role="status">${error ? escapeHtml(error) : ''}</div>
      <section class="chat-work-panel">${workPanel}</section>
    </main>
  ` : `
    <main class="chat-main ${transcript ? '' : 'empty-chat'}">
      ${header}
      <div class="chat-status ${error ? 'error' : ''}" role="status">${error ? escapeHtml(error) : ''}</div>
      ${chatContent}
    </main>
  `;

  const chatNavigation = pane === 'settings' ? '' : `
    <aside class="chat-sidebar ${profileOpen ? 'profile-open' : ''} ${railCollapsed ? 'rail-collapsed' : ''}" aria-label="${activeWorkMode ? '工作导航' : '对话导航'}">
      <header class="chat-brand">
        <img src="./nanfeng-ai-icon.png" alt="南枫 AI">
        <button data-action="show-chat"><strong>南枫 AI</strong></button>
        <button class="chat-rail-toggle" data-action="toggle-rail" aria-label="${railCollapsed ? '展开导航栏' : '折叠导航栏'}" aria-expanded="${!railCollapsed}">${icon(railCollapsed ? icons.chevronRight : icons.chevronLeft, railCollapsed ? '展开导航栏' : '折叠导航栏')}</button>
        ${sidebarOpen ? `<button class="chat-sidebar-close" data-action="toggle-chat-sidebar" aria-label="关闭导航">${icon(icons.close, '关闭导航')}</button>` : ''}
      </header>
      <div class="chat-sidebar-scroll">
        <div class="chat-search-wrap"><label><span class="chat-search-glyph" aria-hidden="true">${icon(icons.search, '搜索')}</span><input id="chat-search" class="chat-search" type="search" placeholder="搜索" aria-label="搜索" value="${escapeHtml(chatSearch)}"></label>${searchHistoryOpen ? `<div class="chat-search-history" role="dialog" aria-label="最近搜索"><div><strong>最近搜索</strong><button data-action="clear-search-history" ${searchHistory.length ? '' : 'disabled'}>清空</button><button data-action="close-search-history">关闭</button></div>${searchHistory.length ? searchHistory.map(query => `<button data-action="fill-search-history" data-query="${escapeHtml(query)}">${escapeHtml(query)}</button>`).join('') : '<p>暂无已提交的本地搜索。</p>'}</div>` : ''}</div>
        ${sidebarFunctions({ activeWorkMode, pane, data, workspaces, selectedWorkProjectId, selectedConversationId })}
        ${activeWorkMode ? '' : `<section class="chat-history" aria-label="本地会话列表">${conversationList}</section>`}
      </div>
      <footer class="chat-sidebar-footer"><div class="chat-sidebar-bottom-actions"><button class="chat-profile chat-sidebar-settings" data-action="show-settings" aria-label="设置" title="设置"><span aria-hidden="true">${icon(icons.settings, '设置')}</span></button>${activeWorkMode ? '' : `<button class="chat-sidebar-function" data-action="new-chat" aria-label="新对话"><span aria-hidden="true">${icon(icons.edit, '新对话')}</span><span class="rail-label">新对话</span></button>`}</div></footer>
    </aside>
    <button type="button" class="chat-sidebar-divider" role="separator" aria-label="调整对话导航宽度" aria-orientation="vertical" aria-valuemin="220" aria-valuemax="440" aria-valuenow="${Math.max(220, Math.min(440, Number(sidebarWidth) || 256))}" title="拖拽调整导航宽度；双击恢复默认"></button>
    <button class="chat-sidebar-scrim" data-action="toggle-chat-sidebar" aria-label="关闭导航"></button>`;
  return `
    ${chatNavigation}
    ${main}
    ${pane === 'settings' ? '' : conversationContextMenu(contextMenu, activeWorkMode, accountSync)}
  `;
}
