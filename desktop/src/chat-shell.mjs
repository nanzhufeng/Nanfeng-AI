import { icon, icons } from './icon-source.mjs';

const escapeHtml = value => String(value ?? '').replace(
  /[&<>"']/g,
  char => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#039;' })[char],
);

export function activeConversations(data) {
  return (data?.exchange?.conversations ?? [])
    .filter(item => !item.deleted && !item.archived)
    .sort((left, right) => Number(Boolean(right.pinned)) - Number(Boolean(left.pinned))
      || String(right.updatedAt ?? right.createdAt ?? '').localeCompare(String(left.updatedAt ?? left.createdAt ?? ''))
      || String(left.id ?? '').localeCompare(String(right.id ?? '')));
}

export function pinnedConversations(data) {
  return activeConversations(data).filter(item => item.pinned);
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

export function resolveConversation(data, selectedConversationId) {
  const conversations = activeConversations(data);
  if (!selectedConversationId) return null;
  return conversations.find(item => item.id === selectedConversationId) ?? null;
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
  { id: 'model', group: 'AI 能力', title: '模型与路由', description: '已有的本地目录与全局默认策略。', scope: 'device', syncPolicy: 'local-only', risk: 'normal' },
  { id: 'ai-model-service', group: 'AI 能力', title: 'AI 模型服务', description: 'Compare 的固定服务商、预设与安全就绪状态。', scope: 'device', syncPolicy: 'local-only', risk: 'sensitive' },
  { id: 'data', group: '数据', title: '导入、同步与存储', description: '本机数据导入、导出与生命周期。', scope: 'device', syncPolicy: 'local-only', risk: 'sensitive' },
  { id: 'feature-review', group: '基础', title: '功能审阅', description: '新增功能的去留判断与界面入口建议。', scope: 'device', syncPolicy: 'local-only', risk: 'normal' },
  { id: 'privacy', group: '数据', title: '隐私与数据管理', description: '本地边界和既有会话管理。', scope: 'device', syncPolicy: 'local-only', risk: 'sensitive' },
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

function conversationRows(conversations, selectedConversationId, { archived = false } = {}) {
  if (!conversations.length) return `<p class="chat-history-empty">${archived ? '还没有已归档会话' : '还没有本地会话'}</p>`;
  return conversations.map(item => `
    <div class="chat-history-row ${item.id === selectedConversationId ? 'selected' : ''}" data-conversation-row data-id="${escapeHtml(item.id)}" data-revision="${item.revision}" data-pinned="${Boolean(item.pinned)}" data-archived="${Boolean(item.archived)}">
      <button class="chat-history-select" data-action="select-chat" data-id="${escapeHtml(item.id)}" title="${escapeHtml(item.title || '未命名会话')} · ${escapeHtml(conversationLocalDate(item))}">
        <span>${escapeHtml(item.title || '未命名会话')}</span>
      </button>
      <small class="chat-history-date">${escapeHtml(conversationLocalDate(item))}</small>
      <div class="chat-row-actions" aria-label="${escapeHtml(item.title || '未命名会话')}的操作">
        ${archived
          ? `<button class="chat-row-action-icon" data-action="restore-conversation" data-id="${escapeHtml(item.id)}" data-revision="${item.revision}" aria-label="恢复会话" title="恢复会话">${icon(icons.restore, '恢复')}</button>`
          : `<button class="chat-row-action-icon" data-action="set-conversation-pinned" data-id="${escapeHtml(item.id)}" data-revision="${item.revision}" data-pinned="${!item.pinned}" aria-label="${item.pinned ? '取消置顶会话' : '置顶会话'}" title="${item.pinned ? '取消置顶会话' : '置顶会话'}">${icon(item.pinned ? icons.pushPinOff : icons.pushPin, item.pinned ? '取消置顶' : '置顶')}</button><button class="chat-row-action-icon" data-action="archive-conversation" data-id="${escapeHtml(item.id)}" data-revision="${item.revision}" aria-label="归档会话" title="归档会话">${icon(icons.archive, '归档')}</button>`}
      </div>
    </div>
  `).join('');
}

export function conversationLocalDate(item) {
  const value = new Date(item?.updatedAt || item?.createdAt || 0);
  if (Number.isNaN(value.getTime())) return '本地时间未知';
  return new Intl.DateTimeFormat('zh-CN', { year: 'numeric', month: '2-digit', day: '2-digit' }).format(value).replaceAll('-', '/');
}

function sidebarFunctions({ activeWorkMode, pane, data, workspaces }) {
  const item = (action, glyph, label, active = false) => `<button class="chat-sidebar-function ${active ? 'selected' : ''}" data-action="${action}" aria-label="${label}"><span aria-hidden="true">${icon(glyph, label)}</span><span class="rail-label">${label}</span></button>`;
  return `<section class="chat-sidebar-functions" aria-label="侧栏功能">
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
  const model = snapshot?.displayName || snapshot?.modelId || (typeof message?.importedModel === 'string' && message.importedModel.trim()) || null;
  const created = Date.parse(message?.createdAt || '');
  const timestamp = Number.isFinite(created)
    ? new Intl.DateTimeFormat('zh-CN', { month: 'numeric', day: 'numeric', hour: 'numeric', minute: '2-digit' }).format(new Date(created))
    : null;
  const source = ['IMPORTED', 'CACHE', 'PROVIDER', 'LOCAL_FIXTURE'].includes(message?.source) ? message.source : null;
  return { source, model, timestamp, workDuration: assistantWorkDuration(message) };
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
  if (!String(item?.mimeType || '').startsWith('image/')) return `<span class="chat-attachment-chip">${name}${mime ? ` · ${mime}` : ''}${remove}</span>`;
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

function messageList(conversation, { imageThumbnails = {}, interactive = true, ariaLabel = '当前会话消息' } = {}) {
  if (!conversation) {
    return `
      <section class="chat-empty" aria-labelledby="chat-empty-title">
        <img src="./nanfeng-ai-icon.png" alt="" class="chat-empty-logo">
        <h1 id="chat-empty-title">今天想一起做什么？</h1>
        <p>发送后会自动记录到本地；配置模型服务后，再按每次明确确认使用联网回答。</p>
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
        return `
        ${dateKey !== previousDateKey ? `<div class="chat-date-divider" role="separator">${escapeHtml(dateKey)}</div>` : ''}
        <article class="chat-message ${escapeHtml(message.role)}" data-message-id="${escapeHtml(message.id)}" tabindex="0">
          <div class="chat-message-content">
            ${metadata.workDuration ? `<p class="chat-message-work-duration">${escapeHtml(metadata.workDuration)}</p>` : ''}
            ${attachmentBlocks.length ? `<div class="chat-message-attachments" aria-label="本地附件预览">${attachmentBlocks.map(block => localAttachmentDisplay(block.asset, imageThumbnails)).join('')}</div>` : ''}
            ${textBlocks.length ? `<div class="chat-message-bubble"><div class="chat-message-body">${textBlocks.map(block => `<p>${escapeHtml(block.text || '')}</p>`).join('')}</div></div>` : ''}
            ${(metadata.timestamp || metadata.model || metadata.source || (interactive && payload)) ? `<div class="chat-message-tools">${interactive && payload ? `<div class="chat-message-actions"><button class="chat-message-action-icon" data-action="copy-message" data-message-id="${escapeHtml(message.id)}" aria-label="复制消息" title="复制消息">${icon(icons.copy, '复制')}</button><button class="chat-message-action-icon" data-action="share-message" data-message-id="${escapeHtml(message.id)}" aria-label="分享消息" title="分享消息">${icon(icons.share, '分享')}</button><button class="chat-message-action-icon" data-action="branch-from-message" data-message-id="${escapeHtml(message.id)}" aria-label="从此处分支" title="从此处分支">${icon(icons.branch, '从此处分支')}</button></div>` : ''}<p class="chat-message-metadata">${[metadata.timestamp, metadata.model].filter(Boolean).map(escapeHtml).join(' · ')}${metadata.source ? `<button class="chat-provenance-badge" data-action="message-provenance" data-message-id="${escapeHtml(message.id)}" aria-label="查看消息来源" title="查看消息来源">${icon(icons.info, '来源')}</button>` : ''}</p></div>` : ''}
          </div>
        </article>
      `;
      }).join('')}
    </section>
  `;
}

function conversationContextMenu(menu, workMode) {
  if (!menu) return '';
  const data = `data-id="${escapeHtml(menu.id)}" data-revision="${menu.revision}" data-pinned="${menu.pinned}"`;
  const item = (action, glyph, label, { extra = '', trailing = false, danger = false } = {}) => `<button class="chat-context-menu-item${danger ? ' danger' : ''}" role="menuitem" data-action="${action}" ${data} ${extra}><span class="chat-menu-action-icon" aria-hidden="true">${glyph}</span><span class="chat-menu-action-label">${label}</span>${trailing ? `<span class="chat-menu-action-trailing" aria-hidden="true">${icon(icons.chevronRight, '展开')}</span>` : ''}</button>`;
  const common = `${item('context-menu-pin', icon(menu.pinned ? icons.pushPinOff : icons.pushPin, menu.pinned ? '取消置顶' : '置顶'), menu.pinned ? '取消置顶' : '置顶')}${item('context-menu-rename', icon(icons.edit, '重命名'), '重命名')}${item('context-menu-project', icon(icons.folder, '项目归属'), workMode ? '项目归属' : '添加到项目', { trailing: true })}`;
  const archive = workMode ? item('context-menu-archive', icon(menu.archived ? icons.restore : icons.archive, menu.archived ? '恢复' : '归档'), menu.archived ? '恢复' : '归档', { extra: `data-archived="${menu.archived}"` }) : '';
  const remove = item('context-menu-delete', icon(icons.trash, '删除'), '删除', { danger: true });
  return `<div class="chat-context-menu" role="menu" aria-label="${workMode ? '工作会话菜单' : '对话会话菜单'}" data-positioned="false">${common}${archive}${remove}</div>`;
}

function workNavigation() { return ''; }

function settingsCenterCanvas(dualPath, status, data, native, p6eAcceptance = { enabled: false, receipt: null }, p6gCatalog = null, p6gGlobalDefault = null, section = 'data', search = '', p6kTask = null, p6kLink = {}) {
  const archived = archivedConversations(data);
  const deleted = deletedConversations(data);
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
  const modelContent = `<p class="settings-page-intro">只显示现有 P6-G 本地目录和全局默认；不会读取 Key、配置 Provider 或发起调用。</p>${p6gSettings}`;
  const compare = DESKTOP_COMPARE_SETTINGS_PROJECTION;
  const aiModelServiceContent = `<p class="settings-page-intro">Compare 的服务配置只在本页呈现。凭据不会显示、读取或写入；本页也不会联网。</p><section class="settings-section" aria-label="Desktop Compare AI 模型服务"><h2>Desktop Compare</h2><article class="settings-row"><div><strong>${escapeHtml(compare.provider)}</strong><p>${escapeHtml(compare.protocol)} · 凭据 ${escapeHtml(compare.credentialPresence)} · ${escapeHtml(compare.executionState)}</p><small>${escapeHtml(compare.blocker)}：必须由用户在最终设置动作输入凭据，并完成目录核验后才可执行。</small><ul class="p6g-catalog-list">${compare.presets.map(preset => `<li>${escapeHtml(preset.logicalModel)} · ${escapeHtml(preset.providerModel)} · ${escapeHtml(preset.price)}</li>`).join('')}</ul></div></article></section>`;
  const featureReviewContent = `<p class="settings-page-intro">新增能力先在此展示用途、现有入口和待您判断项；本页不直接启用、删除或调用功能。</p><section class="settings-section"><h2>新增功能审阅</h2><article class="settings-row"><div><strong>ChatGPT / Claude ZIP 导入</strong><p>当前：待您判断保留或删减 · 入口：设置 → 数据与导入 → 导入中心。</p><small>建议：保留设置入口；暂不在对话主页添加快捷按钮，避免高敏感导入被误触。</small></div></article><article class="settings-row"><div><strong>未关联媒体人工关联</strong><p>当前：待外部归属证据后再判断是否启用 · 入口：ZIP 导入批次详情。</p><small>建议：仅在存在未关联媒体时提供二级操作；不在聊天主界面常驻功能按钮。</small></div></article><article class="settings-row"><div><strong>Desktop Compare 联网执行</strong><p>当前：待您判断是否保留 · 入口：既有对话 Composer 的“对比”操作。</p><small>阶段 1/2 已有 fail-closed owner 与 Security.framework 边界；复用现有“对比”操作，不新增 Composer 常驻按钮；固定预设与状态只放在设置 → AI 模型服务。</small></div></article><article class="settings-row"><div><strong>本地精确复用</strong><p>当前：待您判断是否保留；仅完成离线精确键与既有消息引用的安全索引，尚未接入普通聊天执行。入口：设置 → 功能审阅。</p><small>建议：暂不增加聊天或 Composer 按键；只有将来真实复用、用量和清理能力完整后，再在设置提供独立开关与清理入口，避免误解为联网缓存或省费承诺。</small></div></article></section>`;
  const privacyContent = `<p class="settings-page-intro">当前仅投影已有本地会话生命周期；危险操作仍由既有 owner 与确认负责。</p><section class="settings-section"><h2>会话管理</h2><article class="settings-row"><div><strong>已归档与回收站</strong><p>已归档 ${archived.length} · 回收站 ${deleted.length}</p><small>device · local-only · sensitive</small></div></article><div class="settings-conversation-management">${conversationRows(archived, null, { archived: true })}${conversationRows(deleted, null, { archived: true }).replaceAll('restore-conversation', 'restore-deleted-conversation')}</div></section>`;
  const generalContent = `<p class="settings-page-intro">本设置中心的布局与数据只保存在当前设备；账户、同步、Provider 和复杂管理中心仍未启用。</p><section class="settings-section"><h2>本地状态</h2><article class="settings-row"><div><strong>本地工作区</strong><p>${escapeHtml(dualPath.local.detail)}</p><small>device · local-only · normal</small></div><span class="settings-state ready">${escapeHtml(dualPath.local.label)}</span></article><article class="settings-row"><div><strong>联网能力</strong><p>${escapeHtml(dualPath.provider.detail)}</p><small>account · secret · sensitive</small></div><span class="settings-state">${escapeHtml(dualPath.provider.label)}</span></article></section>`;
  const content = section === 'model' ? modelContent : section === 'ai-model-service' ? aiModelServiceContent : section === 'feature-review' ? featureReviewContent : section === 'privacy' ? privacyContent : section === 'general' ? generalContent : `${dataContent}${p6jImportRow}${p6kZipRow}`;
  return `<main class="chat-main settings-center-main"><div class="settings-center-layout"><aside class="settings-center-nav" aria-label="设置分类"><button class="settings-return-app" data-action="show-chat">返回应用</button><label><span class="sr-only">搜索设置</span><input id="settings-search" type="search" placeholder="搜索设置" value="${escapeHtml(search)}"></label>${nav || '<p class="settings-empty">没有匹配的已存在设置。</p>'}</aside><section class="settings-center-content">${content}${status ? `<p class="settings-page-status" role="status">${escapeHtml(status)}</p>` : ''}</section></div></main>`;
}

export function renderChatFirstShell({
  data,
  native,
  selectedConversationId,
  composerDraft,
  composerAttachments = [],
  chatSearch,
  searchResults = [],
  searchPanel = false,
  searchHistory = [],
  searchHistoryOpen = false,
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
  p6kTask = null,
  p6kLink = globalThis.__nanfengP6kManualLink || {},
  workMode = false,
  workspaces,
  workPanel,
  pane,
  status,
  error,
  connection,
  p6eAcceptance = { enabled: false, receipt: null },
  settingsSection = 'data',
  settingsSearch = '',
  sidebarWidth = 256,
  temporaryConversation = null,
  imageThumbnails = {},
  showScrollToLatest = false,
}) {
  const conversation = temporaryConversation ? null : resolveConversation(data, selectedConversationId);
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
  const p6gLabel = !selectedConversationId ? '未配置' : (manualModelId ? (manualCandidate?.displayName || manualModelId) : '自动');
  const p6gPopover = `<div class="p6g-model-popover" role="dialog" aria-label="选择模型"><button class="p6g-model-option" data-action="select-p6g-auto" aria-selected="${!manualModelId}"><span>自动</span>${!manualModelId ? icon(icons.check, '当前模型') : ''}</button>${p6gCandidates.filter(item => item.available).map(item => `<button class="p6g-model-option" data-action="select-p6g-model" data-model-id="${escapeHtml(item.modelId)}" aria-selected="${item.modelId === manualModelId}"><span>${escapeHtml(item.displayName)}</span>${item.modelId === manualModelId ? icon(icons.check, '当前模型') : ''}</button>`).join('')}<button class="p6g-model-option" data-action="open-compare-confirmation"><span>对比 ChatGPT + Claude</span></button></div>`;
  const temporaryModelId = temporaryConversation?.modelOverrideId || null;
  const temporaryCandidate = p6gCandidates.find(item => item.modelId === temporaryModelId);
  const temporaryModelLabel = temporaryModelId ? (temporaryCandidate?.displayName || temporaryModelId) : '自动';
  const temporaryModelPopover = `<div class="p6g-model-popover" role="dialog" aria-label="选择模型"><button class="p6g-model-option" data-action="select-temporary-auto" aria-selected="${!temporaryModelId}"><span>自动</span>${!temporaryModelId ? icon(icons.check, '当前模型') : ''}</button>${p6gCandidates.filter(item => item.available).map(item => `<button class="p6g-model-option" data-action="select-temporary-model" data-model-id="${escapeHtml(item.modelId)}" aria-selected="${item.modelId === temporaryModelId}"><span>${escapeHtml(item.displayName)}</span>${item.modelId === temporaryModelId ? icon(icons.check, '当前模型') : ''}</button>`).join('')}</div>`;
  // Pinning stays in one continuous scroll owner, but must remain visibly
  // distinguishable from the recency list.
  const listed = activeConversations(data);
  const pinned = pinnedConversations(data);
  const recent = listed.filter(item => !item.pinned);
  const conversationList = listed.length
    ? `${pinned.length ? `<div class="chat-history-group" role="group" aria-label="置顶会话"><p class="chat-history-label">置顶</p>${conversationRows(pinned, selectedConversationId)}</div>` : ''}${recent.length ? `<div class="chat-history-group" role="group" aria-label="最近会话"><p class="chat-history-label">最近</p>${conversationRows(recent, selectedConversationId)}</div>` : ''}`
    : conversationRows([], selectedConversationId);
  const composer = `
    <section class="chat-composer-wrap" aria-label="消息输入">
      <div class="chat-composer">
        <textarea id="chat-composer" maxlength="12000" placeholder="回复 南枫AI" aria-label="输入内容">${escapeHtml(composerDraft)}</textarea>
        ${visibleAttachments.length ? `<div class="chat-composer-attachments" aria-label="本地附件">${visibleAttachments.map(item => composerAttachmentDisplay(item, imageThumbnails, { canReadThumbnail: !temporaryConversation })).join('')}</div>` : ''}
        <div class="chat-composer-actions">
          <span class="composer-add-anchor"><button class="chat-composer-icon chat-composer-add" data-action="toggle-composer-add" data-overlay-trigger aria-label="添加到草稿" title="添加到草稿" aria-expanded="${composerAddOpen}">${icon(icons.plus, '添加到草稿')}</button>${composerAddOpen ? `<div class="composer-add-popover" role="dialog" aria-label="添加到草稿"><button data-action="pick-composer-image">${icon(icons.image, '添加图片')}<span>添加图片</span></button><button data-action="pick-composer-file">${icon(icons.file, '添加文件')}<span>添加文件</span></button></div>` : ''}</span>
          <div class="chat-composer-primary-actions">
            ${temporaryConversation ? `<span class="p6g-model-anchor"><button class="chat-composer-icon p6g-model-trigger" data-action="toggle-temporary-model" data-overlay-trigger aria-label="选择模型：${escapeHtml(temporaryModelLabel)}" title="选择模型 · ${escapeHtml(temporaryModelLabel)}" aria-expanded="${temporaryModelOpen}"><span>${escapeHtml(temporaryModelLabel)}</span></button>${temporaryModelOpen ? temporaryModelPopover : ''}</span>` : `<span class="p6g-model-anchor"><button class="chat-composer-icon p6g-model-trigger" data-action="toggle-p6g-model-picker" data-compare-long-press data-overlay-trigger aria-label="选择模型：${escapeHtml(p6gLabel)}；长按对比 ChatGPT + Claude" title="选择模型 · ${escapeHtml(p6gLabel)}（长按对比）" aria-expanded="${p6gModelPickerOpen}" ${selectedConversationId && native ? '' : 'disabled'}><span>${escapeHtml(p6gLabel)}</span></button>${p6gModelPickerOpen ? p6gPopover : ''}</span><button class="chat-composer-icon p6g-compare-trigger" data-action="open-compare-confirmation" aria-label="对比 ChatGPT + Claude" title="对比 ChatGPT + Claude" ${selectedConversationId && native ? '' : 'disabled'}><span>对比</span></button>`}
            <button class="chat-send chat-composer-icon" data-action="save-local-message" aria-label="发送消息" title="发送消息" ${path.enabled && (String(composerDraft || '').trim() || visibleAttachments.length) ? '' : 'disabled'}>${icon(icons.send, '发送消息')}</button>
          </div>
        </div>
      </div>
    </section>
  `;
  const searchContent = `<section class="chat-scroll" aria-label="搜索结果"><header class="chat-main-header"><div><p>本地搜索</p><h1>搜索结果</h1><small>${escapeHtml(chatSearch)} · 仅安全索引</small></div><button data-action="close-search">返回对话</button></header>${searchResults.length ? `<div class="chat-thread">${searchResults.map(hit => `<button class="chat-history-select" data-action="open-search-result" data-id="${escapeHtml(hit.conversationId)}" data-message-id="${escapeHtml(hit.messageId || '')}"><strong>${escapeHtml(hit.title)}</strong><span>${escapeHtml(hit.snippet)}</span></button>`).join('')}</div>` : '<p class="chat-empty">没有匹配的本地内容。</p>'}</section>`;
  const composerDock = `<div class="chat-composer-dock" data-composer-dock="fixed">
      ${showScrollToLatest && conversation ? `<button class="chat-scroll-to-latest" data-action="scroll-to-latest" data-anchor="composer-top" aria-label="到最新消息" title="到最新消息">${icon(icons.chevronRight, '到最新消息')}</button>` : ''}
      ${composer}
    </div>`;
  const transcript = temporaryConversation ? temporaryTranscript(temporaryConversation) : conversation;
  const chatContent = searchPanel ? searchContent : transcript
    ? `<div class="chat-transcript-stage"><div class="chat-scroll" data-scroll-owner="message-list" data-conversation-id="${escapeHtml(transcript.id)}" ${temporaryConversation ? 'data-temporary-transcript="true"' : ''} tabindex="0">${messageList(transcript, { imageThumbnails, interactive: !temporaryConversation, ariaLabel: temporaryConversation ? '临时聊天消息' : '当前会话消息' })}</div>${temporaryConversation ? '' : transcriptPositionRail(transcript)}</div>${composerDock}`
    : `<div class="chat-empty-stage">${messageList(null)}${composer}</div>`;
  const header = `
      <header class="chat-main-header">
        <button class="chat-sidebar-toggle" data-action="toggle-chat-sidebar" aria-label="${sidebarOpen ? '关闭导航' : '打开导航'}" title="${sidebarOpen ? '关闭导航' : '打开导航'}" aria-expanded="${sidebarOpen}">${icon(icons.menu, '打开导航')}</button>
        <div class="chat-title"><p>南枫 AI</p><h1>${escapeHtml(title)}</h1></div>
        <div class="chat-mode-switch" aria-label="产品模式">
          <button class="${activeWorkMode ? '' : 'selected'}" data-action="show-chat" aria-pressed="${!activeWorkMode}">对话</button>
          <button class="${activeWorkMode ? 'selected' : ''}" data-action="show-work" aria-pressed="${activeWorkMode}">工作</button>
        </div>
        <button class="chat-temporary-button ${temporaryConversation ? 'active' : ''}" data-action="toggle-temporary-chat" aria-label="临时聊天" title="临时聊天" aria-pressed="${Boolean(temporaryConversation)}">${icon(icons.ghost, '临时聊天')}</button>
      </header>`;
  const main = pane === 'settings' ? settingsCenterCanvas(dualPath, status, data, native, p6eAcceptance, p6gCatalog, p6gGlobalDefault, settingsSection, settingsSearch, p6kTask, p6kLink) : showWorkPanel ? `
    <main class="chat-main work-main">
      ${header}
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
        ${sidebarFunctions({ activeWorkMode, pane, data, workspaces })}
        <div class="chat-search-wrap"><label><input id="chat-search" class="chat-search" type="search" placeholder="搜索" aria-label="搜索" value="${escapeHtml(chatSearch)}"></label>${searchHistoryOpen ? `<div class="chat-search-history" role="dialog" aria-label="最近搜索"><div><strong>最近搜索</strong><button data-action="clear-search-history" ${searchHistory.length ? '' : 'disabled'}>清空</button><button data-action="close-search-history">关闭</button></div>${searchHistory.length ? searchHistory.map(query => `<button data-action="fill-search-history" data-query="${escapeHtml(query)}">${escapeHtml(query)}</button>`).join('') : '<p>暂无已提交的本地搜索。</p>'}</div>` : ''}</div>
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
    ${pane === 'settings' ? '' : conversationContextMenu(contextMenu, activeWorkMode)}
  `;
}
import { DESKTOP_COMPARE_SETTINGS_PROJECTION } from './desktop-compare-execution-owner.mjs';
