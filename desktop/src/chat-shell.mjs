import { icon, icons } from './icon-source.mjs';
import { renderSafeMarkdown } from './safe-markdown.mjs';
import { APPEARANCE_MODES, FONT_SIZES, THEME_COLORS } from './desktop-parity-preferences.mjs';
import { renderAndroidSettingsShell } from './android-settings-shell.mjs';
import { renderDesktopSearchPage } from './desktop-search-page.mjs';

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

/** Settings is a route registry, not a second owner for product data. */
export const SETTINGS_REGISTRY = Object.freeze([
  { id: 'general', group: '基础', title: '常规', description: '当前设备的界面与本地行为。', scope: 'device', syncPolicy: 'local-only', risk: 'normal' },
  { id: 'appearance', group: '基础', title: '外观', description: '外观模式、字体大小和主题色。', scope: 'device', syncPolicy: 'local-only', risk: 'normal' },
  { id: 'model', group: 'AI 能力', title: '模型与路由', description: '已有的本地目录与全局默认策略。', scope: 'device', syncPolicy: 'local-only', risk: 'normal' },
  { id: 'ai-model-service', group: 'AI 能力', title: 'AI 模型服务', description: '服务商、模型预设与安全就绪状态。', scope: 'device', syncPolicy: 'local-only', risk: 'sensitive' },
  { id: 'data', group: '数据', title: '导入、同步与存储', description: '本机数据导入、导出与生命周期。', scope: 'device', syncPolicy: 'local-only', risk: 'sensitive' },
  { id: 'local-control', group: '基础', title: '项目、知识与记忆', description: '进入已有本地工作区，不触发 Provider 或外部访问。', scope: 'device', syncPolicy: 'local-only', risk: 'normal' },
  { id: 'privacy', group: '数据', title: '隐私与数据管理', description: '本地边界和既有会话管理。', scope: 'device', syncPolicy: 'local-only', risk: 'sensitive' },
  { id: 'about', group: '其他', title: '关于', description: '当前 Desktop 版本与运行平台。', scope: 'device', syncPolicy: 'read-only', risk: 'normal' },
]);

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
    const running = (item.messages || []).some(message => String(message.delivery || '').toUpperCase() === 'PARTIAL');
    return `
    <div class="chat-history-row ${item.id === selectedConversationId ? 'selected' : ''}" data-conversation-row data-id="${escapeHtml(item.id)}" data-revision="${item.revision}" data-pinned="${Boolean(item.pinned)}" data-favorite="${favoriteIds.has(item.id)}" data-archived="${Boolean(item.archived)}">
      <button class="chat-history-select" data-action="select-chat" data-id="${escapeHtml(item.id)}" title="${escapeHtml(item.title || '未命名会话')} · ${escapeHtml(conversationLocalDate(item))}">
        <span class="chat-history-title-line">
          ${item.pinned ? `<i class="chat-history-pin" aria-label="已置顶">${icon(icons.pushPin, '已置顶')}</i>` : ''}
          ${unread ? `<i class="chat-history-unread" aria-label="${manuallyUnread ? '已标记未读' : '有未查看的新内容'}"></i>` : ''}
          ${running ? '<i class="chat-history-running" aria-label="正在生成"></i>' : ''}
          <span class="chat-history-title">${escapeHtml(item.title || '未命名会话')}</span>
          ${favoriteIds.has(item.id) ? `<i class="chat-history-favorite" aria-label="已收藏">${icon(icons.star, '已收藏')}</i>` : ''}
        </span>
      </button>
      <small class="chat-history-date">${escapeHtml(conversationLocalDate(item))}</small>
      <div class="chat-row-actions" aria-label="${escapeHtml(item.title || '未命名会话')}的操作">
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

function sidebarFunctions({ activeWorkMode, pane, data, workspaces }) {
  const item = (action, glyph, label, active = false) => `<button class="chat-sidebar-function ${active ? 'selected' : ''}" data-action="${action}" aria-label="${label}"><span aria-hidden="true">${icon(glyph, label)}</span><span class="rail-label">${label}</span></button>`;
  return `<section class="chat-sidebar-functions" aria-label="侧栏功能">
    ${item('show-reminders', icons.clock, '定时任务', pane === 'reminders')}
    ${item('show-transcription', icons.fileText, '南枫转写', pane === 'transcription')}
    ${activeWorkMode ? workNavigation(pane, data, workspaces) : ''}
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
    'Grok 4.1 Fast': '4.1 Fast', 'Grok 4.6 High': '4.6 High', 'Gemini 3.7 Flash': '3.7 Flash',
    'Qwen3.7-Plus': '3.7-Plus', 'Qwen3.8-Max': '3.8-Max', 'Qwen3.6 Flash': '3.6 Flash',
    'DeepSeek V4 Pro': 'V4 Pro', 'DeepSeek V4 Flash': 'V4 Flash', 'GLM-5.3': 'GLM 5.3', 'GLM-5.3 Flash': '5.3 Flash', 'GLM-OCR': 'GLM-OCR',
  })[normalized] || normalized;
}

export function assistantCostCny(chargeMicros, currencyCode = 'USD') {
  if (!Number.isSafeInteger(chargeMicros)) return null;
  const rate = currencyCode === 'CNY' ? 1 : currencyCode === 'USD' ? 6.720309145556033 : null;
  if (rate == null) return null;
  const units = Math.floor((chargeMicros * rate / 100) + 0.5);
  return `¥${(units / 10_000).toFixed(4)}`;
}

/** P6-044: only an explicit persisted run/import boundary may produce a duration label. */
export function assistantWorkDuration(message) {
  if (message?.role !== 'assistant') return null;
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
  const name = escapeHtml(asset?.displayName || '本地附件');
  const mime = escapeHtml(assetMimeType);
  const info = `<span class="chat-attachment-info-overlay"><b>${name}</b>${mime ? `<small>${mime}</small>` : ''}</span>`;
  const previewGlyph = `<span class="chat-attachment-preview-glyph" aria-hidden="true">${icon(icons.import, '附件')}</span>`;
  const isAudio = ['audio/mpeg', 'audio/wav', 'audio/mp4'].includes(assetMimeType);
  const isVideo = ['video/mp4', 'video/webm'].includes(assetMimeType);
  const isText = ['text/plain', 'text/markdown', 'application/json', 'text/csv'].includes(assetMimeType);
  if (id && assetMimeType === 'application/pdf') return `<button class="chat-document-attachment" data-action="open-pdf-preview" data-attachment-id="${escapeHtml(id)}" aria-label="阅读 PDF：${name}" title="阅读 PDF">${previewGlyph}${info}</button>`;
  // A lazy video preview deliberately has no square crop: the opened local player uses
  // the file's intrinsic dimensions.  A future thumbnail may be inserted without changing
  // this non-document container.
  if (id && isVideo) return `<button class="chat-video-attachment" data-action="open-video-preview" data-attachment-id="${escapeHtml(id)}" aria-label="播放视频：${name}" title="播放视频">${previewGlyph}${info}</button>`;
  if (id && isAudio) return `<button class="chat-audio-attachment" data-action="open-audio-preview" data-attachment-id="${escapeHtml(id)}" aria-label="播放音频：${name}" title="播放音频">${previewGlyph}${info}</button>`;
  if (id && isText) return `<button class="chat-document-attachment" data-action="open-text-preview" data-attachment-id="${escapeHtml(id)}" aria-label="预览文本：${name}" title="预览文本">${previewGlyph}${info}</button>`;
  if (!id || !assetMimeType.startsWith('image/')) return `<span class="chat-document-attachment" tabindex="0" aria-label="附件预览：${name}">${previewGlyph}${info}</span>`;
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
  return `<span class="chat-composer-image-preview" ${canReadThumbnail && id ? `data-image-thumbnail="${escapeHtml(id)}"` : ''}>${media}<span class="chat-composer-image-name">${name}</span>${remove}</span>`;
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

function messageList(conversation, { imageThumbnails = {}, interactive = true, ariaLabel = '当前会话消息', findQuery = '', activeFindMessageId = null, contextSelectionRecords = [], productSettings = {}, settingsCapabilities = {}, reminders = { drafts: [] } } = {}) {
  if (!conversation) {
    return `
      <section class="chat-empty" aria-labelledby="chat-empty-title">
        <img src="./nanfeng-ai-icon.png" alt="" class="chat-empty-logo">
        <h1 id="chat-empty-title">今天想一起做什么？</h1>
        <p>发送会先原子写入本地消息与 Attempt；已启用的模型服务随后流式回复。</p>
      </section>
    `;
  }
  return `
    <section class="chat-thread" aria-label="${escapeHtml(ariaLabel)}">
      ${conversation.importedFrom === 'CHATGPT_EXPORT' ? '<p class="chat-import-note" role="note">从 ChatGPT 导入</p>' : conversation.importedFrom === 'CLAUDE_EXPORT' ? '<p class="chat-import-note" role="note">从 Claude 导入 · 本地静态文本</p>' : ''}
      ${conversation.messages.map((message, index) => {
        const metadata = transcriptMetadata(message);
        const payload = messagePlainText(message);
        const textBlocks = message.blocks.filter(block => block.kind !== 'ASSET_REF');
        const attachmentBlocks = message.blocks.filter(block => block.kind === 'ASSET_REF');
        const dateKey = transcriptLocalDateKey(message);
        const previousDateKey = index ? transcriptLocalDateKey(conversation.messages[index - 1]) : null;
        const matchesFind = Boolean(findQuery) && textBlocks.some(block => String(block.text || '').toLocaleLowerCase().includes(String(findQuery).trim().toLocaleLowerCase()));
        const delivery = String(message.delivery || 'COMPLETE');
        const compareExecutionId = typeof message.compareExecutionId === 'string' ? message.compareExecutionId : '';
        const compareLogicalModel = message.compareLogicalModel === 'CHATGPT' ? 'ChatGPT' : message.compareLogicalModel === 'CLAUDE' ? 'Claude' : '';
        const answerContext = message.role === 'assistant' && delivery === 'COMPLETE'
          ? contextSelectionRecords.find(record => record.assistantMessageId === message.id && record.selectedSources?.length)
          : null;
        const sourceUser = index > 0 ? conversation.messages[index - 1] : null;
        const reminderSuggestion = interactive && index === conversation.messages.length - 1 && message.role === 'assistant' && delivery === 'COMPLETE' && sourceUser?.role === 'user' && productSettings.reminderSuggestions && settingsCapabilities.reminderSuggestions && hasExplicitReminderIntent(messagePlainText(sourceUser)) && !(reminders.drafts || []).some(item => item.sourceAssistantMessageId === message.id)
          ? `<button class="chat-reminder-suggestion" data-action="generate-reminder-draft" data-user-message-id="${escapeHtml(sourceUser.id)}" data-assistant-message-id="${escapeHtml(message.id)}">添加提醒 / 监控</button>`
          : '';
        const runtimeStatus = message.role !== 'assistant' || delivery === 'COMPLETE' ? ''
          : delivery === 'PARTIAL' ? `<div class="chat-runtime-state running" role="status"><span aria-hidden="true"></span><strong>正在生成</strong><button data-action="${compareExecutionId ? 'stop-desktop-compare' : 'stop-ordinary-chat'}" ${compareExecutionId ? `data-execution-id="${escapeHtml(compareExecutionId)}"` : ''}>停止</button></div>`
          : delivery === 'UNKNOWN' ? `<div class="chat-runtime-state unknown" role="status"><strong>连接结果未知，未自动重发${message.safeErrorCode ? ` · ${escapeHtml(message.safeErrorCode)}` : ''}</strong><button data-action="${compareExecutionId ? 'retry-desktop-compare-branch' : 'retry-ordinary-chat'}" data-attempt-id="${escapeHtml(message.attemptId || '')}">${compareExecutionId ? '重试该分支' : '重试原 Attempt'}</button></div>`
          : delivery === 'CANCELLED' ? `<div class="chat-runtime-state cancelled" role="status"><strong>已停止，已生成内容已保留</strong></div>`
          : `<div class="chat-runtime-state failed" role="status"><strong>生成失败${message.safeErrorCode ? ` · ${escapeHtml(message.safeErrorCode)}` : ''}</strong><button data-action="${compareExecutionId ? 'retry-desktop-compare-branch' : 'retry-ordinary-chat'}" data-attempt-id="${escapeHtml(message.attemptId || '')}">${compareExecutionId ? '重试该分支' : '重试原 Attempt'}</button></div>`;
        const cost = assistantCostCny(message.chargeMicros, message.currencyCode || 'USD');
        if (metadata.model) metadata.model = compactModelName(metadata.model);
        return `
        ${dateKey !== previousDateKey ? `<div class="chat-date-divider" role="separator">${escapeHtml(dateKey)}</div>` : ''}
        <article class="chat-message ${escapeHtml(message.role)}${matchesFind ? ' find-match' : ''}${activeFindMessageId === message.id ? ' find-active' : ''}" data-message-id="${escapeHtml(message.id)}" tabindex="0">
          <div class="chat-message-content">
            ${compareLogicalModel ? `<p class="chat-compare-branch"><strong>${compareLogicalModel}</strong><span>OpenRouter · 独立分支</span></p>` : ''}
            ${metadata.workDuration ? `<p class="chat-message-work-duration">${escapeHtml(metadata.workDuration)}</p>` : ''}
            ${attachmentBlocks.length ? `<div class="chat-message-attachments" aria-label="本地附件预览">${attachmentBlocks.map(block => localAttachmentDisplay(block.asset, imageThumbnails)).join('')}</div>` : ''}
            ${textBlocks.length ? `<div class="chat-message-bubble"><div class="chat-message-body ${message.role === 'assistant' ? 'chat-markdown-body' : ''}">${textBlocks.map(block => message.role === 'assistant' ? renderSafeMarkdown(block.text || '', { query: findQuery }) : `<p>${highlightedText(block.text || '', findQuery)}</p>`).join('')}</div></div>` : ''}
            ${runtimeStatus}
            ${(metadata.timestamp || metadata.model || metadata.source || (interactive && payload)) ? `<div class="chat-message-tools">${interactive && payload ? `<div class="chat-message-actions"><button class="chat-message-action-icon" data-action="copy-message" data-message-id="${escapeHtml(message.id)}" aria-label="复制消息" title="复制消息">${icon(icons.copy, '复制')}</button><button class="chat-message-action-icon" data-action="share-message" data-message-id="${escapeHtml(message.id)}" aria-label="分享消息" title="分享消息">${icon(icons.share, '分享')}</button>${message.role === 'assistant' ? `<button class="chat-message-action-icon" data-action="export-assistant-markdown" data-message-id="${escapeHtml(message.id)}" aria-label="导出这条回复为 Markdown" title="导出 Markdown">${icon(icons.download, '导出 Markdown')}</button>` : ''}<button class="chat-message-action-icon" data-action="branch-from-message" data-message-id="${escapeHtml(message.id)}" aria-label="从此处分支" title="从此处分支">${icon(icons.branch, '从此处分支')}</button></div>` : ''}<p class="chat-message-metadata">${[metadata.timestamp, metadata.model].filter(Boolean).map(escapeHtml).join(' · ')}${metadata.source ? `<button class="chat-provenance-badge" data-action="message-provenance" data-message-id="${escapeHtml(message.id)}" aria-label="查看消息来源" title="查看消息来源">${icon(icons.info, '来源')}</button>` : ''}</p></div>` : ''}
            ${cost ? `<p class="chat-message-cost">${escapeHtml(cost)}</p>` : ''}
            ${answerContext ? `<button class="chat-answer-context" data-action="show-answer-context" data-message-id="${escapeHtml(message.id)}">${icon(icons.info, '上下文')}<span>查看回答上下文 · ${answerContext.selectedSources.length}</span></button>` : ''}
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
  const syncLabel = accountSync.state === 'READY' ? '加密同步此会话' : '设置加密同步';
  const common = `${item('context-menu-pin', icon(menu.pinned ? icons.pushPinOff : icons.pushPin, menu.pinned ? '取消置顶' : '置顶'), menu.pinned ? '取消置顶' : '置顶')}${item('context-menu-unread', icon(icons.visibility, '未读'), '未读')}${item('context-menu-favorite', icon(menu.favorite ? icons.starOff : icons.star, menu.favorite ? '取消收藏' : '收藏'), menu.favorite ? '取消收藏' : '收藏')}${item('context-menu-sync', icon(icons.data, syncLabel), syncLabel)}${item('context-menu-rename', icon(icons.edit, '重命名'), '重命名')}${item('context-menu-project', icon(icons.folder, '项目归属'), workMode ? '项目归属' : '添加到项目', { trailing: true })}`;
  const archive = workMode ? item('context-menu-archive', icon(menu.archived ? icons.restore : icons.archive, menu.archived ? '恢复' : '归档'), menu.archived ? '恢复' : '归档', { extra: `data-archived="${menu.archived}"` }) : '';
  const remove = item('context-menu-delete', icon(icons.trash, '删除'), '删除', { danger: true });
  return `<div class="chat-context-menu" role="menu" aria-label="${workMode ? '工作会话菜单' : '对话会话菜单'}" data-positioned="false">${common}${archive}${remove}</div>`;
}

function workNavigation() { return ''; }

function settingsCenterCanvas(dualPath, status, data, native, p6eAcceptance = { enabled: false, receipt: null }, p6gCatalog = null, p6gGlobalDefault = null, section = 'data', search = '', p6kTask = null, p6kLink = {}, v2CommittedExchanges = globalThis.__nanfengV2CommittedExchanges || [], appearance = {}, favoriteIds = new Set(), runtimeInfo = {}) {
  const archived = archivedConversations(data);
  const deleted = deletedConversations(data);
  const favorites = favoriteConversations(data, favoriteIds);
  const snapshot = p6gCatalog?.snapshot;
  const global = p6gGlobalDefault;
  const localFixtureControl = !snapshot?.candidates?.length
    ? '<button data-action="install-p6g-local-fixture">添加本地确定性 fixture（仅验收）</button><p>仅写入 app-private P6-G catalog，用于本地选择/路由验收；不配置 Provider、不会联网。</p>'
    : '';
  const p6gSettings = `<section class="connection-row compact" aria-label="P6-G 本地模型选择"><div><span>模型选择与自动路由</span><strong>${snapshot?.candidates?.length ? `${snapshot.candidates.length} 个本地目录项` : '未配置'}</strong></div><p>目录版本 ${escapeHtml(snapshot?.catalogVersion || 'local-unconfigured-v1')} · 策略 ${escapeHtml(String(snapshot?.policyVersion || 1))}。仅为本地目录/策略快照；没有 Key、Provider 配置、HTTP 或调用。</p><label>全局默认档位<select id="p6g-global-default"><option value="" ${!global?.tier ? 'selected' : ''}>自动（由当前请求档位决定）</option>${['FAST','BALANCED','DEEP','APEX_REVIEW'].map(tier => `<option value="${tier}" ${global?.tier === tier ? 'selected' : ''}>${tier}</option>`).join('')}</select></label><button data-action="save-p6g-global-default" ${p6gCatalog ? '' : 'disabled'}>保存本地全局默认</button>${snapshot?.candidates?.length ? `<ul class="p6g-catalog-list">${snapshot.candidates.map(item => `<li>${escapeHtml(item.displayName)} · ${escapeHtml(item.modelId)} · ${item.available ? '可用目录项' : '不可用'}</li>`).join('')}</ul>` : `<p>未提供模型选择，界面不会虚构 Provider。</p>${localFixtureControl}`}</section>`;
  const normalized = String(search || '').trim().toLocaleLowerCase();
  const matches = SETTINGS_REGISTRY.filter(item => !normalized || `${item.group} ${item.title} ${item.description}`.toLocaleLowerCase().includes(normalized));
  const nav = [...new Set(matches.map(item => item.group))].map(group => `<section><p>${group}</p>${matches.filter(item => item.group === group).map(item => `<button class="settings-nav-item ${item.id === section ? 'selected' : ''}" data-action="select-settings-section" data-section="${item.id}"><span>${escapeHtml(item.title)}</span><small>${escapeHtml(item.scope)} · ${escapeHtml(item.syncPolicy)}</small></button>`).join('')}</section>`).join('');
  const acceptance = p6eAcceptance.enabled ? `<section class="p6e-acceptance-card" aria-label="P6-E 验收维护"><strong>P6-E 验收维护（仅 acceptance 启动）</strong><p>固定 app-private /tmp root、固定 Clock 与无参数 owner 命令；正式启动不会显示此卡。</p><button data-action="run-p6e-temporary-maintenance-acceptance">运行 23h59 / 24h 维护验收</button>${p6eAcceptance.receipt ? `<p role="status">23h59 保留=${p6eAcceptance.receipt.retainedAt23h59} · 24h 清理=${p6eAcceptance.receipt.removedAt24h} · 附件=${p6eAcceptance.receipt.attachmentRemovedAt24h} · 消息=${p6eAcceptance.receipt.messagePresentBeforeExpiry} · 标识=${p6eAcceptance.receipt.modelOverridePresentBeforeExpiry} · 普通面=${p6eAcceptance.receipt.ordinarySurfacesClean}</p>` : ''}</section>` : '';
  const p6jImportRow = `<section class="settings-section"><h2>知识库静态会话导入</h2><article class="settings-row"><div><strong>南枫知识库全部 JSON</strong><p>仅接受完整导出的静态会话记录；复制到 app-private 后逐项确认，thinking 与工具指令不会导入。</p><small>device · local-only · sensitive</small></div><div class="settings-row-controls"><button data-action="select-nanfeng-knowledge-export">选择 JSON</button><button data-action="resume-nanfeng-knowledge-import">继续任务</button></div></article></section>`;
  const p6kStatus = p6kTask ? `<p role="status">${escapeHtml(p6kTask.provider)} · ${escapeHtml(p6kTask.status)} · 已导入 ${Number(p6kTask.importedCount || 0)} · 失败 ${Number(p6kTask.failedCount || 0)} · 跳过 ${Number(p6kTask.skippedCount || 0)} · 未关联媒体 ${Number(p6kTask.unmappedAssetCount || 0)} · 个性化 ${escapeHtml(p6kTask.profileStatus || 'NO_SAFE_PROFILE_FIELDS')}（${Number(p6kTask.profileMappedFieldCount || 0)}）</p>` : '';
  const p6kImported = (p6kTask?.items || []).filter(item => item.status === 'COMMITTED' && item.conversationId).map(item => (data?.exchange?.conversations || []).find(conversation => conversation.id === item.conversationId)).filter(Boolean);
  const p6kAssets = (p6kTask?.manualAssets || []).filter(asset => asset.status !== 'MANUAL_LINKED');
  const p6kAssetPicker = p6kAssets.length ? `<div class="settings-row-controls" aria-label="未关联媒体选择">${p6kAssets.map(asset => `<button data-action="select-p6k-manual-asset" data-asset-ordinal="${Number(asset.ordinal)}" ${p6kLink.assetOrdinal === Number(asset.ordinal) ? 'aria-pressed="true"' : ''}>媒体 ${Number(asset.ordinal) + 1} · ${escapeHtml(asset.mimeType)} · ${Number(asset.byteCount)} B${asset.status === 'MANUAL_LINK_FAILED' ? ' · 可重试' : ''}</button>`).join('')}</div>` : '';
  const p6kTargetPicker = p6kLink.assetOrdinal === null || p6kLink.assetOrdinal === undefined ? '' : `<div class="settings-row-controls" aria-label="已导入消息选择">${p6kImported.flatMap(conversation => (conversation.messages || []).map((message, ordinal) => `<button data-action="select-p6k-manual-target" data-conversation-id="${escapeHtml(conversation.id)}" data-message-id="${escapeHtml(message.id)}" ${p6kLink.messageId === message.id ? 'aria-pressed="true"' : ''}>${escapeHtml(conversation.title)} · ${escapeHtml(message.role)} · 第 ${ordinal + 1} 条</button>`)).join('')}</div>`;
  const p6kManualLink = p6kAssetPicker ? `<article class="settings-row"><div><strong>人工关联未关联媒体</strong><p>只显示媒体类型与大小；选择媒体和已导入消息后，才从私有 ZIP 提取该项。不会显示文件名或内容。</p><small>local-only · explicit-target · receipt</small>${p6kAssetPicker}${p6kTargetPicker}</div><div class="settings-row-controls"><button data-action="link-p6k-manual-asset" ${p6kLink.assetOrdinal !== null && p6kLink.messageId ? '' : 'disabled'}>关联到所选消息</button></div></article>` : '';
  const p6kZipRow = `<section class="settings-section"><h2>第三方 ZIP 导入</h2><article class="settings-row"><div><strong>ChatGPT / Claude ZIP</strong><p>选择后会私有暂存、严格适配并直接写入当前本地会话树；失败项可重试或跳过。</p><small>local-only · receipt · sensitive</small>${p6kStatus}</div><div class="settings-row-controls"><button data-action="select-p6k-chatgpt-zip">ChatGPT ZIP</button><button data-action="select-p6k-claude-zip">Claude ZIP</button><button data-action="retry-p6k-zip" ${p6kTask ? '' : 'disabled'}>重试</button><button data-action="skip-p6k-zip-failures" ${p6kTask?.failedCount ? '' : 'disabled'}>跳过失败项</button><button data-action="delete-p6k-zip-batch" ${p6kTask ? '' : 'disabled'}>删除导入批次</button></div></article>${p6kManualLink}</section>`;
  const dataContent = `<p class="settings-page-intro">仅显示已存在的本机数据能力。导入由独立任务机执行，外部路径、URI、Key 与句柄不进入设置记录。</p><section class="settings-section"><h2>数据导入</h2><article class="settings-row"><div><strong>ChatGPT 对话</strong><p>选择 conversations.json 后复制到 app-private 并直接写入；正文不执行。</p><small>device · local-only · sensitive</small></div><div class="settings-row-controls"><button data-action="select-chatgpt-export">选择文件</button><button data-action="resume-chatgpt-import">查看回执</button></div></article><article class="settings-row"><div><strong>Claude 对话</strong><p>选择 conversations.json 后复制到 app-private 并直接写入；正文不执行。</p><small>device · local-only · sensitive</small></div><div class="settings-row-controls"><button data-action="select-claude-export">选择文件</button><button data-action="resume-claude-import">查看回执</button></div></article><article class="settings-row"><div><strong>导入本地工作区</strong><p>选择后严格预检并直接导入为新的独立工作区；不会覆盖当前数据。</p><small>device · local-only · sensitive</small></div><div class="settings-row-controls"><button data-action="start-import" ${native ? '' : 'disabled'}>选择交换包</button></div></article></section><section class="settings-section"><h2>本地导出</h2><article class="settings-row"><div><strong>导出当前本地工作区</strong><p>显式选择目标后原子写入、严格回读并显示 hash。</p><small>device · local-only · sensitive</small></div><div class="settings-row-controls"><button data-action="start-export" ${data && native ? '' : 'disabled'}>导出</button></div></article></section>${acceptance}`;
  const v2WorkspaceExchangeRow = `<section class="settings-section"><h2>完整工作区交换（v2）</h2><article class="settings-row"><div><strong>私有 v2 导入</strong><p>仅读取您选择的 .nfai-exchange，或 Android DocumentsUI 保存的 .zip；文件名不替代严格预检，只有 v2 manifest、语义与字段哈希一致才写入独立私有归档与回执，不合并、覆盖或恢复为当前 Desktop 工作区。</p><small>device · local-only · private archive · content-free receipt</small></div><div class="settings-row-controls"><button data-action="select-v2-workspace-exchange" ${native ? '' : 'disabled'}>选择 v2 交换包</button></div></article></section>`;
  const v2CommittedRows = v2CommittedExchanges.length
    ? `<div class="settings-row-controls" aria-label="已提交 v2 私有交换记录">${v2CommittedExchanges.map((item, index) => `<button data-action="reexport-v2-workspace-exchange" data-workspace-id="${escapeHtml(item.workspaceId)}" ${native ? '' : 'disabled'}>回导已提交交换 ${index + 1} · ${Number(item.assetCount)} 项附件</button>`).join('')}</div>`
    : '<p>尚无可回导的已提交 v2 私有交换记录。</p>';
  const v2WorkspaceExchangeReexportRow = `<section class="settings-section"><article class="settings-row"><div><strong>回导已提交 v2 交换</strong><p>仅从已提交的私有 v2 记录只读重建 canonical 包；必须由您选择系统保存位置，写入后再严格回读语义、字段哈希与附件账本。</p><small>device · local-only · private archive · native save picker · content-free receipt</small>${v2CommittedRows}</div></article></section>`;
  const modelContent = `<p class="settings-page-intro">只显示现有 P6-G 本地目录和全局默认；不会读取 Key、配置 Provider 或发起调用。</p>${p6gSettings}`;
  const aiModelServiceContent = `<p class="settings-page-intro">模型服务配置与凭据仅保存在本机安全边界；凭据不会进入 SQLite、备份、同步或界面状态。</p><section class="settings-section" aria-label="AI 模型服务"><h2>模型服务</h2><article class="settings-row"><div><strong>OpenAI-compatible</strong><p>聊天模型由当前会话的自动、日常或深度策略选择，实际 Provider、模型、Token、费用、耗时与终态按次记录。</p><small>不会静默改用未配置的 Provider 或模型；结果未知时不会自动重发。</small></div></article></section>`;
  let featureReviewContent = `<p class="settings-page-intro">新增能力先在此展示用途、现有入口和待您判断项；本页不直接启用、删除或调用功能。</p><section class="settings-section"><h2>新增功能审阅</h2><article class="settings-row"><div><strong>ChatGPT / Claude ZIP 导入</strong><p>当前：待您判断保留或删减 · 入口：设置 → 数据与导入 → 导入中心。</p><small>建议：保留设置入口；暂不在对话主页添加快捷按钮，避免高敏感导入被误触。</small></div></article><article class="settings-row"><div><strong>未关联媒体人工关联</strong><p>当前：待外部归属证据后再判断是否启用 · 入口：ZIP 导入批次详情。</p><small>建议：仅在存在未关联媒体时提供二级操作；不在聊天主界面常驻功能按钮。</small></div></article><article class="settings-row"><div><strong>本地精确复用</strong><p>当前：待您判断是否保留；仅完成离线精确键与既有消息引用的安全索引，尚未接入普通聊天执行。入口：设置 → 功能审阅。</p><small>建议：暂不增加聊天或 Composer 按键；只有将来真实复用、用量和清理能力完整后，再在设置提供独立开关与清理入口，避免误解为联网缓存或省费承诺。</small></div></article><article class="settings-row"><div><strong>跨端文本会话交换</strong><p>当前：待您判断是否保留；Android 现只从设置导出符合条件的文本会话为 .nfai-exchange，Desktop 使用既有工作区导入。</p><small>建议：只保留设置二级入口，不增加聊天或 Composer 按键；它不是备份、云同步或完整工作区跨端保真承诺。</small></div></article></section>`;
  featureReviewContent = featureReviewContent.replace('</section>', `<article class="settings-row"><div><strong>完整工作区交换（v2）</strong><p>当前：待您判断是否保留；Android 可从设置 → 数据与导入选择单个 v2 包，仅在空本机严格恢复；Desktop 仅可从设置选择 v2 包后私有导入。</p><small>建议：只保留双端设置二级入口，不增加聊天、Composer 或工作页常驻按键；它不是备份、云同步，也不会覆盖已有本机数据。</small></div></article></section>`);
  const privacyContent = `<p class="settings-page-intro">本页读取当前本机真实会话与不透明收藏 ID；危险操作仍由既有 owner 与确认负责。</p><section class="settings-section"><h2>会话管理</h2><article class="settings-row"><div><strong>收藏</strong><p>已收藏 ${favorites.length} 个活动会话；收藏不改变普通列表排序，归档或删除会自动取消。</p><small>device · local-only · opaque conversation id</small></div></article><div class="settings-conversation-management">${favorites.map(item => `<div class="settings-favorite-row"><button data-action="select-chat" data-id="${escapeHtml(item.id)}">${escapeHtml(item.title || '未命名会话')}</button><button data-action="toggle-conversation-favorite" data-id="${escapeHtml(item.id)}" aria-label="取消收藏">${icon(icons.starOff, '取消收藏')}</button></div>`).join('') || '<p class="settings-empty">还没有收藏会话。</p>'}</div><article class="settings-row"><div><strong>已归档与回收站</strong><p>已归档 ${archived.length} · 回收站 ${deleted.length}</p><small>device · local-only · sensitive</small></div></article><div class="settings-conversation-management">${conversationRows(archived, null, { archived: true, favoriteIds })}${conversationRows(deleted, null, { archived: true, favoriteIds }).replaceAll('restore-conversation', 'restore-deleted-conversation')}</div></section>`;
  const generalContent = `<p class="settings-page-intro">本设置中心的布局与数据只保存在当前设备；账户、同步、Provider 和复杂管理中心仍未启用。</p><section class="settings-section"><h2>本地状态</h2><article class="settings-row"><div><strong>本地工作区</strong><p>${escapeHtml(dualPath.local.detail)}</p><small>device · local-only · normal</small></div><span class="settings-state ready">${escapeHtml(dualPath.local.label)}</span></article><article class="settings-row"><div><strong>联网能力</strong><p>${escapeHtml(dualPath.provider.detail)}</p><small>account · secret · sensitive</small></div><span class="settings-state">${escapeHtml(dualPath.provider.label)}</span></article></section>`;
  const choiceButtons = (items, current, action, swatches = false) => items.map(item => `<button class="appearance-choice ${item.id === current ? 'selected' : ''}" data-action="${action}" data-value="${item.id}" aria-pressed="${item.id === current}">${swatches ? `<i style="--choice-color:${item.accent}" aria-hidden="true"></i>` : ''}<span>${escapeHtml(item.label)}</span>${item.id === current ? icon(icons.check, '当前选择') : ''}</button>`).join('');
  const appearanceContent = `<p class="settings-page-intro">与手机端使用同一外观选项；只保存当前设备偏好，不改变会话数据、控件热区或业务状态。</p><section class="settings-section"><h2>外观</h2><article class="settings-row appearance-setting"><div><strong>外观模式</strong><p>跟随系统、浅色或深色；切换立即作用于全部 Desktop 画布与弹层。</p></div><div class="appearance-choices">${choiceButtons(APPEARANCE_MODES, appearance.mode, 'set-appearance-mode')}</div></article><article class="settings-row appearance-setting"><div><strong>字体大小</strong><p>小／标准／大按手机端 80%／100%／124% 比例缩放文字，控件轮廓与命中区不变。</p></div><div class="appearance-choices">${choiceButtons(FONT_SIZES, appearance.fontSize, 'set-font-size')}</div></article><article class="settings-row appearance-setting"><div><strong>主题色</strong><p>统一驱动选中态、按钮、发送、焦点、文字选中和用户消息气泡。</p></div><div class="appearance-choices theme-color-choices">${choiceButtons(THEME_COLORS, appearance.themeColor, 'set-theme-color', true)}</div></article></section>`;
  const aboutContent = `<p class="settings-page-intro">版本与平台信息来自当前运行包，不使用静态占位值。</p><section class="settings-section"><h2>关于</h2><article class="settings-row about-card"><div><strong>南枫 AI Desktop</strong><p>版本 ${escapeHtml(runtimeInfo.version || '读取中')} · ${escapeHtml(runtimeInfo.platform || 'Desktop')}</p><small>${escapeHtml(runtimeInfo.arch || '本机架构')} · 本地工作区与联网能力分开验收</small></div></article></section>`;
  const localControlContent = `<p class="settings-page-intro">只进入已有的本地工作区；这里不创建对象、不读凭据、不调用 Provider，也不发起外部访问。</p><section class="settings-section"><h2>项目、知识与记忆</h2><article class="settings-row"><div><strong>本地工作区</strong><p>Projects、知识与关系、长期 Memory 分别继续使用已有 Rust SQLite owner；进入后所有写入仍由其现有表单和事务处理。</p><small>device · local-only · reversible navigation</small></div><div class="settings-row-controls"><button data-action="show-projects">Projects</button><button data-action="show-knowledge">知识与关系</button><button data-action="show-memory">长期 Memory</button></div></article><article class="settings-row"><div><strong>边界</strong><p>这些入口不会自动加入对话上下文，也不代表联网、同步、Provider 或自动执行已可用。</p><small>no credential · no provider · no external access</small></div></article></section>`;
  featureReviewContent = featureReviewContent.replace('Desktop 仅可从设置选择 v2 包后私有导入。', 'Desktop 仅可从设置选择 v2 包私有导入，或从已提交私有记录经系统保存位置回导。');
  featureReviewContent = featureReviewContent.replace('</section>', `<article class="settings-row"><div><strong>项目、知识与记忆</strong><p>当前：待您判断保留或删减 · 入口：设置 → 项目、知识与记忆。可进入已有的 Projects、知识与关系、长期 Memory 工作页。</p><small>建议：保留设置二级入口；不建议在聊天主页、Composer 或会话详情增加按键，避免把本地管理误解为发送、联网或自动执行。</small></div></article></section>`);
  featureReviewContent = featureReviewContent.replace('</section>', `<article class="settings-row"><div><strong>个性化模型调用</strong><p>当前：Android 已实现；Desktop 尚未有同一普通模型调用 owner，因此本端没有伪开关或资料输入页。</p><small>建议：等待 Desktop 的真实普通发送链路可承接本机资料后，再只在设置二级页实现；不新增聊天或 Composer 按键。</small></div></article><article class="settings-row"><div><strong>相关长期记忆调用</strong><p>当前：Android 已实现按问题检索的真实开关；Desktop 尚未接入同一发送链路，不能显示为已可用。</p><small>建议：Desktop 完成真实检索、Token 预算与模型调用约束后，再在设置二级页增加同一控制；不新增聊天或 Composer 按键。</small></div></article><article class="settings-row"><div><strong>关于与版本信息</strong><p>当前：Android 已在设置 → 关于显示运行时版本号与构建号；Desktop 尚未接入同一应用版本 owner，因此不展示静态或伪造版本页。</p><small>建议：Android 保留设置二级入口，不在聊天主页、Composer 或会话详情增加按键；Desktop 有可验证的运行时版本来源后再实现同类只读页面。</small></div></article><article class="settings-row"><div><strong>素材类型标签</strong><p>当前：Android 多模态模型调用会按顺序传入 <图片>、<PDF>、<视频> 等实际标签，并要求回答使用具体类型，不再笼统称为“附件”。Desktop 尚无同一多模态发送 owner。</p><small>建议：保留为消息生成时的透明说明，不增加聊天主页、Composer 或会话详情按键；Desktop 有相同真实发送链路后再实现。</small></div></article><article class="settings-row"><div><strong>后台继续生成</strong><p>当前：Android 普通模型生成期间使用系统前台持续任务，退到后台后继续运行；完成或手动停止时结束通知。Desktop 尚无同一 Android 服务 owner。</p><small>建议：作为发送期间的系统状态保留，不增加聊天主页、Composer 或会话详情按键；网络断开、强制停止或系统终止仍保留结果未知，避免重复调用。</small></div></article></section>`);
  featureReviewContent = featureReviewContent.replace('</section>', `<article class="settings-row"><div><strong>批量清理已归档与回收站</strong><p>当前：Android 与 Desktop 都在设置 → 对话管理 → 已归档 / 回收站提供批量清理；清空已归档只移入回收站，清空回收站才永久删除。</p><small>仅保留两个管理页的低频入口；不在聊天主页或 Composer 增加按键。永久删除继续使用独立确认和精确 revision owner。</small></div></article></section>`);
  const content = section === 'model' ? modelContent : section === 'ai-model-service' ? aiModelServiceContent : section === 'local-control' ? localControlContent : section === 'feature-review' ? featureReviewContent : section === 'privacy' ? privacyContent : section === 'appearance' ? appearanceContent : section === 'about' ? aboutContent : section === 'general' ? generalContent : `${dataContent}${v2WorkspaceExchangeRow}${v2WorkspaceExchangeReexportRow}${p6jImportRow}${p6kZipRow}`;
  return `<main class="chat-main settings-center-main"><div class="settings-center-layout"><aside class="settings-center-nav" aria-label="设置分类"><button class="settings-return-app" data-action="show-chat">返回应用</button><label><span class="sr-only">搜索设置</span><input id="settings-search" type="search" placeholder="搜索设置" value="${escapeHtml(search)}"></label>${nav || '<p class="settings-empty">没有匹配的已存在设置。</p>'}</aside><section class="settings-center-content">${content}${status ? `<p class="settings-page-status" role="status">${escapeHtml(status)}</p>` : ''}</section></div></main>`;
}

export function renderChatFirstShell({
  data,
  native,
  selectedConversationId,
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
  searchHistoryHighlighted = globalThis.__nanfengDesktopSearchState?.searchHistoryHighlighted || null,
  profileOpen,
  sidebarOpen,
  railCollapsed,
  showArchived,
  showDeleted,
  contextMenu,
  composerAddOpen,
  temporaryModelOpen = false,
  p6gModelPickerOpen = false,
  p6gCatalog = null,
  p6gGlobalDefault = null,
  p6gSelection = null,
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
  settingsMobileHome = false,
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
  personalizationDraft = productSettings,
  personalizationDirty = false,
  memorySummaryQuery = '',
  memorySummaryComposer = '',
  memorySummaryNotice = '',
  settingsPicker = null,
  modelServiceSettings = [],
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
  settingsCapabilities = {},
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
    history: searchHistory,
    historyOpen: searchHistoryOpen,
    historyHighlighted: searchHistoryHighlighted,
    thumbnails: imageThumbnails,
  });
  const lifecycleReadOnly = Boolean(settingsConversationReturn?.readOnly);
  const lifecycleConversation = lifecycleReadOnly
    ? (data?.exchange?.conversations || []).find(item => item.id === selectedConversationId) || null
    : null;
  const conversation = temporaryConversation ? null : lifecycleConversation || resolveConversation(data, selectedConversationId);
  const showWorkPanel = Boolean(workPanel);
  const activeWorkMode = workMode || showWorkPanel;
  const path = temporaryConversation ? { enabled: native, label: '临时本地记录', detail: '本次内容仅保存在隔离恢复记录；不会进入历史或工作区。' } : localMessagePathState({ native, hasWorkspace: Boolean(data) });
  const dualPath = deriveDesktopDualPathState({ connection });
  const title = temporaryConversation ? '临时聊天' : (conversation?.title || '新对话');
  const visibleAttachments = temporaryConversation
    ? (temporaryConversation.attachments || []).filter(item => (temporaryConversation.draftAttachmentIds || []).includes(item.id))
    : composerAttachments;
  const p6gCandidates = p6gSelection?.catalog?.snapshot?.candidates || p6gCatalog?.snapshot?.candidates || [];
  const manualModelId = p6gSelection?.conversationOverride?.modelId || null;
  const manualCandidate = p6gCandidates.find(item => item.modelId === manualModelId);
  const automaticModelLabel = p6gSelection?.lastRoute?.displayName || p6gCandidates.find(item => item.available)?.displayName || '等待可用模型';
  const p6gLabel = !selectedConversationId ? '未配置' : compactModelName(manualModelId ? (manualCandidate?.displayName || manualModelId) : automaticModelLabel);
  const dailyCandidates = p6gCandidates.filter(item => item.available && (item.tiers || []).some(tier => ['FAST', 'BALANCED'].includes(tier)));
  const deepCandidates = p6gCandidates.filter(item => item.available && (item.tiers || []).some(tier => ['DEEP', 'APEX_REVIEW'].includes(tier)));
  const pickerTier = p6gSelection?.pickerTier === 'DEEP' ? 'DEEP' : p6gSelection?.pickerTier === 'DAILY' ? 'DAILY' : null;
  const tierCandidates = pickerTier === 'DEEP' ? deepCandidates : dailyCandidates;
  const dailySelected = Boolean(manualModelId && dailyCandidates.some(item => item.modelId === manualModelId));
  const deepSelected = Boolean(manualModelId && deepCandidates.some(item => item.modelId === manualModelId));
  const p6gRoot = `<header><strong>选择模型</strong></header><small>自动选择</small><button class="p6g-model-option p6g-model-auto" data-action="select-p6g-auto" aria-selected="${!manualModelId}"><span><strong>自动</strong><small>Auto · ${escapeHtml(automaticModelLabel)}</small></span>${!manualModelId ? icon(icons.check, '当前模型') : ''}</button><small>按任务选择</small><button class="p6g-model-option" data-action="select-p6g-tier" data-tier="DAILY" aria-selected="${dailySelected}" ${dailyCandidates.length ? '' : 'disabled'}><span><strong>日常</strong><small>日常问答与轻量任务</small></span><i class="p6g-category-trailing">${dailySelected ? icon(icons.check, '当前模型') : ''}${icon(icons.chevronRight, '选择日常模型')}</i></button><button class="p6g-model-option" data-action="select-p6g-tier" data-tier="DEEP" aria-selected="${deepSelected}" ${deepCandidates.length ? '' : 'disabled'}><span><strong>深度</strong><small>复杂推理与专业分析</small></span><i class="p6g-category-trailing">${deepSelected ? icon(icons.check, '当前模型') : ''}${icon(icons.chevronRight, '选择深度模型')}</i></button>`;
  const p6gTier = `<header class="p6g-model-tier-header"><button data-action="p6g-picker-back" aria-label="返回模型分组">${icon(icons.chevronLeft, '返回')}</button><strong>${pickerTier === 'DEEP' ? '深度' : '日常'}</strong><span></span></header><small>${pickerTier === 'DEEP' ? '复杂推理与专业分析' : '日常问答与轻量任务'}</small>${tierCandidates.map(item => `<button class="p6g-model-option p6g-model-candidate" data-action="select-p6g-model" data-model-id="${escapeHtml(item.modelId)}" aria-selected="${item.modelId === manualModelId}"><span><strong>${escapeHtml(item.displayName)}</strong><small>${escapeHtml(item.providerId || String(item.providerFamily || '').replaceAll('_', ' '))}</small></span>${item.modelId === manualModelId ? icon(icons.check, '当前模型') : ''}</button>`).join('')}`;
  const p6gPopover = `<div class="p6g-model-popover" role="dialog" aria-label="选择模型">${pickerTier ? p6gTier : p6gRoot}</div>`;
  const temporaryModelId = temporaryConversation?.modelOverrideId || null;
  const temporaryCandidate = p6gCandidates.find(item => item.modelId === temporaryModelId);
  const temporaryModelLabel = temporaryModelId ? compactModelName(temporaryCandidate?.displayName || temporaryModelId) : '自动';
  const temporaryModelPopover = `<div class="p6g-model-popover" role="dialog" aria-label="选择模型"><button class="p6g-model-option" data-action="select-temporary-auto" aria-selected="${!temporaryModelId}"><span>自动</span>${!temporaryModelId ? icon(icons.check, '当前模型') : ''}</button>${p6gCandidates.filter(item => item.available).map(item => `<button class="p6g-model-option" data-action="select-temporary-model" data-model-id="${escapeHtml(item.modelId)}" aria-selected="${item.modelId === temporaryModelId}"><span>${escapeHtml(item.displayName)}</span>${item.modelId === temporaryModelId ? icon(icons.check, '当前模型') : ''}</button>`).join('')}</div>`;
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
        <textarea id="chat-composer" maxlength="12000" placeholder="回复 南枫AI" aria-label="输入内容">${escapeHtml(composerDraft)}</textarea>
        ${visibleAttachments.length ? `<div class="chat-composer-attachments" aria-label="本地附件">${visibleAttachments.map(item => composerAttachmentDisplay(item, imageThumbnails, { canReadThumbnail: !temporaryConversation })).join('')}</div>` : ''}
        <div class="chat-composer-actions">
          <span class="composer-add-anchor"><button class="chat-composer-icon chat-composer-add" data-action="toggle-composer-add" data-overlay-trigger aria-label="添加到草稿" title="添加到草稿" aria-expanded="${composerAddOpen}">${icon(icons.plus, '添加到草稿')}</button>${composerAddOpen ? `<div class="composer-add-popover" role="dialog" aria-label="添加到草稿"><button data-action="pick-composer-image">${icon(icons.image, '添加图片')}<span>图片</span></button><button data-action="pick-composer-file">${icon(icons.file, '添加文件')}<span>文件</span></button><button data-action="toggle-product-setting" data-key="webSearchEnabled" role="switch" aria-pressed="${Boolean(productSettings.webSearchEnabled)}" ${settingsCapabilities.webSearch ? '' : 'disabled'}>${icon(icons.globe, '实时网页搜索')}<span>实时网页搜索</span><i class="composer-web-switch"><i></i></i></button></div>` : ''}</span>
          <div class="chat-composer-primary-actions">
            ${temporaryConversation ? `<span class="p6g-model-anchor"><button class="chat-composer-icon p6g-model-trigger" data-action="toggle-temporary-model" data-overlay-trigger aria-label="选择模型：${escapeHtml(temporaryModelLabel)}" title="选择模型 · ${escapeHtml(temporaryModelLabel)}" aria-expanded="${temporaryModelOpen}"><span>${escapeHtml(temporaryModelLabel)}</span></button>${temporaryModelOpen ? temporaryModelPopover : ''}</span>` : `<span class="p6g-model-anchor"><button class="chat-composer-icon p6g-model-trigger" data-action="toggle-p6g-model-picker" data-overlay-trigger aria-label="选择模型：${escapeHtml(p6gLabel)}" title="选择模型 · ${escapeHtml(p6gLabel)}" aria-expanded="${p6gModelPickerOpen}" ${selectedConversationId ? '' : 'disabled'}><span>${escapeHtml(p6gLabel)}</span></button>${p6gModelPickerOpen ? p6gPopover : ''}</span>`}
            <button class="chat-send chat-composer-icon" data-action="save-local-message" aria-label="发送消息" title="发送消息" ${path.enabled && (String(composerDraft || '').trim() || visibleAttachments.length) ? '' : 'disabled'}>${icon(icons.send, '发送消息')}</button>
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
  const activeFindMatch = conversationFindMatches[conversationFindIndex] || null;
  const findBar = conversationFindOpen && conversation ? `<section class="conversation-find" aria-label="在当前对话中查找"><label>${icon(icons.search, '查找')}<input id="conversation-find-input" type="search" placeholder="在当前对话中查找" value="${escapeHtml(conversationFindQuery)}"></label><span>${conversationFindMatches.length ? `${conversationFindIndex + 1} / ${conversationFindMatches.length}` : '0 / 0'}</span><button data-action="conversation-find-previous" ${conversationFindMatches.length ? '' : 'disabled'} aria-label="上一个匹配">${icon(icons.chevronLeft, '上一个匹配')}</button><button data-action="conversation-find-next" ${conversationFindMatches.length ? '' : 'disabled'} aria-label="下一个匹配">${icon(icons.chevronRight, '下一个匹配')}</button><button data-action="close-conversation-find" aria-label="关闭查找">${icon(icons.close, '关闭查找')}</button></section>` : '';
  const chatContent = searchPanel ? searchContent : transcript
    ? `${findBar}<div class="chat-transcript-stage"><div class="chat-scroll" data-scroll-owner="message-list" data-conversation-id="${escapeHtml(transcript.id)}" ${temporaryConversation ? 'data-temporary-transcript="true"' : ''} tabindex="0">${messageList(transcript, { imageThumbnails, interactive: !temporaryConversation && !lifecycleReadOnly, ariaLabel: temporaryConversation ? '临时聊天消息' : '当前会话消息', findQuery: temporaryConversation ? '' : conversationFindQuery, activeFindMessageId: activeFindMatch?.messageId || null, contextSelectionRecords: temporaryConversation ? [] : contextSelectionRecords, productSettings, settingsCapabilities, reminders })}</div>${temporaryConversation ? '' : transcriptPositionRail(transcript)}</div>${lifecycleReadOnly ? '<p class="chat-lifecycle-readonly">当前为会话生命周期只读查看；返回原列表后可恢复或永久删除。</p>' : composerDock}`
    : `<div class="chat-empty-stage">${messageList(null)}${composer}</div>`;
  const header = `
      <header class="chat-main-header">
        ${settingsConversationReturn ? `<button class="chat-search-return" data-action="return-to-settings-conversation-list" aria-label="返回${escapeHtml(settingsConversationReturn.label)}">${icon(icons.chevronLeft, `返回${settingsConversationReturn.label}`)}<span>${escapeHtml(settingsConversationReturn.label)}</span></button>` : ''}
        ${globalThis.__nanfengDesktopSearchState?.searchReturnActive ? `<button class="chat-search-return" data-action="return-to-search" aria-label="返回搜索">${icon(icons.chevronLeft, '返回搜索')}<span>搜索</span></button>` : ''}
        <button class="chat-sidebar-toggle" data-action="toggle-chat-sidebar" aria-label="${sidebarOpen ? '关闭导航' : '打开导航'}" title="${sidebarOpen ? '关闭导航' : '打开导航'}" aria-expanded="${sidebarOpen}">${icon(icons.menu, '打开导航')}</button>
        <div class="chat-title"><p>南枫 AI</p><h1>${escapeHtml(title)}</h1></div>
        <div class="chat-mode-switch" aria-label="产品模式">
          <button class="${activeWorkMode ? '' : 'selected'}" data-action="show-chat" aria-pressed="${!activeWorkMode}">对话</button>
          <button class="${activeWorkMode ? 'selected' : ''}" data-action="show-work" aria-pressed="${activeWorkMode}">工作</button>
        </div>
        ${conversation && !temporaryConversation ? `<div class="chat-header-actions"><button data-action="toggle-conversation-find" aria-label="在当前对话中查找" title="查找（⌘F）">${icon(icons.search, '查找')}</button><button data-action="export-conversation-markdown" aria-label="导出当前对话为 Markdown" title="导出 Markdown">${icon(icons.download, '导出 Markdown')}</button></div>` : ''}
        <button class="chat-temporary-button ${temporaryConversation ? 'active' : ''}" data-action="toggle-temporary-chat" aria-label="临时聊天" title="临时聊天" aria-pressed="${Boolean(temporaryConversation)}">${icon(icons.ghost, '临时聊天')}</button>
      </header>`;
  const standaloneWorkPanel = ['reminders', 'transcription'].includes(pane);
  const main = pane === 'settings' ? renderAndroidSettingsShell({
    page: settingsSection,
    mobileHome: settingsMobileHome,
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
        <div class="chat-search-wrap"><label><input id="chat-search" class="chat-search" type="search" placeholder="搜索" aria-label="搜索" value="${escapeHtml(chatSearch)}"></label>${searchHistoryOpen ? `<div class="chat-search-history" role="dialog" aria-label="最近搜索"><div><strong>最近搜索</strong><button data-action="clear-search-history" ${searchHistory.length ? '' : 'disabled'}>清空</button><button data-action="close-search-history">关闭</button></div>${searchHistory.length ? searchHistory.map(query => `<button data-action="fill-search-history" data-query="${escapeHtml(query)}">${escapeHtml(query)}</button>`).join('') : '<p>暂无已提交的本地搜索。</p>'}</div>` : ''}</div>
        ${sidebarFunctions({ activeWorkMode, pane, data, workspaces })}
        <section class="chat-history" aria-label="本地会话列表">
          ${conversationList}
        </section>
      </div>
      <footer class="chat-sidebar-footer"><div class="chat-sidebar-bottom-actions"><button class="chat-profile" data-action="show-settings" aria-label="设置" title="设置"><span aria-hidden="true">${icon(icons.settings, '设置')}</span></button><button class="chat-sidebar-function" data-action="new-chat" aria-label="新对话"><span aria-hidden="true">${icon(icons.edit, '新对话')}</span><span class="rail-label">新对话</span></button></div></footer>
    </aside>
    <button type="button" class="chat-sidebar-divider" role="separator" aria-label="调整对话导航宽度" aria-orientation="vertical" aria-valuemin="220" aria-valuemax="440" aria-valuenow="${Math.max(220, Math.min(440, Number(sidebarWidth) || 256))}" title="拖拽调整导航宽度；双击恢复默认"></button>
    <button class="chat-sidebar-scrim" data-action="toggle-chat-sidebar" aria-label="关闭导航"></button>`;
  return `
    ${chatNavigation}
    ${main}
    ${pane === 'settings' ? '' : conversationContextMenu(contextMenu, activeWorkMode, accountSync)}
  `;
}
