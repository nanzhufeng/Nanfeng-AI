import { icon, icons } from './icon-source.mjs';
import { attachmentPreviewCapability } from './desktop-attachment-preview-owner.mjs';

const escapeHtml = value => String(value ?? '').replace(
  /[&<>"']/g,
  char => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#039;' })[char],
);

export const SEARCH_CATEGORIES = Object.freeze([
  ['all', '全部'], ['text', '正文'], ['image', '图片'], ['video', '视频'], ['audio', '音频'], ['file', '文件'],
]);
export const SEARCH_FILE_TYPES = Object.freeze([
  ['all', '全部类型'], ['markdown', 'MD'], ['pdf', 'PDF'], ['zip', 'ZIP'], ['docx', 'DOCX'], ['txt', 'TXT'], ['json', 'JSON'], ['other', '其他'],
]);

export function bestSearchHistoryMatch(query, history = []) {
  const normalized = String(query || '').trim().replace(/\s+/g, ' ').toLocaleLowerCase();
  if (!normalized) return null;
  const rows = history.map(value => ({ value, normalized: String(value).trim().replace(/\s+/g, ' ').toLocaleLowerCase() }));
  return rows.find(row => row.normalized === normalized)?.value
    || rows.find(row => row.normalized.startsWith(normalized))?.value
    || rows.find(row => row.normalized.includes(normalized))?.value
    || null;
}

export function highlightSearchText(value, query) {
  const source = String(value || '');
  const needle = String(query || '').trim();
  if (!needle) return escapeHtml(source);
  const lower = source.toLocaleLowerCase();
  const target = needle.toLocaleLowerCase();
  let cursor = 0;
  let result = '';
  while (cursor < source.length) {
    const index = lower.indexOf(target, cursor);
    if (index < 0) return result + escapeHtml(source.slice(cursor));
    result += `${escapeHtml(source.slice(cursor, index))}<mark>${escapeHtml(source.slice(index, index + needle.length))}</mark>`;
    cursor = index + needle.length;
  }
  return result;
}

export function formatSearchBytes(value) {
  const bytes = Math.max(0, Number(value) || 0);
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  return `${(bytes / 1024 / 1024).toFixed(1)} MB`;
}

export function formatSearchTimestamp(value) {
  const date = new Date(value || 0);
  if (Number.isNaN(date.getTime())) return '时间未知';
  const time = new Intl.DateTimeFormat('zh-CN', { hour: '2-digit', minute: '2-digit', hour12: false }).format(date);
  return `${date.getMonth() + 1}月${date.getDate()}日 ${time}`;
}

function monthKey(value) {
  const date = new Date(value || 0);
  if (Number.isNaN(date.getTime())) return ['unknown', '时间未知'];
  return [`${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}`, `${date.getFullYear()}年${date.getMonth() + 1}月`];
}

function formatMediaDuration(value) {
  const seconds = Math.max(0, Math.floor((Number(value) || 0) / 1000));
  return `${Math.floor(seconds / 60)}:${String(seconds % 60).padStart(2, '0')}`;
}

function audioFormatLabel(hit) {
  const mime = String(hit.mimeType || '').toLowerCase();
  if (mime === 'audio/mpeg') return 'MP3';
  if (mime === 'audio/mp4' || mime === 'audio/x-m4a') return 'M4A';
  if (mime === 'audio/wav' || mime === 'audio/x-wav') return 'WAV';
  if (mime === 'audio/ogg') return 'OGG';
  if (mime === 'audio/aac') return 'AAC';
  return '音频';
}

function textCard(hit, query) {
  return `<button class="desktop-search-text-card" data-action="open-search-result" data-entry-id="${escapeHtml(hit.entryId)}" data-workspace-id="${escapeHtml(hit.workspaceId)}" data-id="${escapeHtml(hit.conversationId)}" data-message-id="${escapeHtml(hit.messageId || '')}" data-branch-leaf-id="${escapeHtml(hit.branchLeafId || '')}" data-conversation-revision="${Number(hit.conversationRevision) || 0}">
    <span class="desktop-search-card-heading"><strong>${highlightSearchText(hit.title, query)}</strong>${hit.archived ? '<em>已归档</em>' : ''}</span>
    <span class="desktop-search-snippet">${highlightSearchText(hit.snippet, query)}</span>
    <span class="desktop-search-facts"><small>${formatSearchBytes(hit.byteCount)}</small><small>${formatSearchTimestamp(hit.timestamp)}</small></span>
  </button>`;
}

function attachmentGlyph(hit, thumbnails, videoThumbnails) {
  const thumbnail = thumbnails?.[hit.attachmentId];
  if (hit.contentKind === 'IMAGE' && thumbnail?.dataUrl) return `<img src="${escapeHtml(thumbnail.dataUrl)}" alt="${escapeHtml(hit.displayName || '图片')}缩略图">`;
  const videoThumbnail = videoThumbnails?.[hit.attachmentId];
  if (hit.contentKind === 'VIDEO' && videoThumbnail?.dataUrl) return `<img src="${escapeHtml(videoThumbnail.dataUrl)}" alt="${escapeHtml(hit.displayName || '视频')}预览图">`;
  const mime = String(hit.mimeType || '').toLowerCase();
  const extension = String(hit.displayName || '').match(/[.。]\s*([a-z0-9]{1,8})\s*$/i)?.[1]?.toUpperCase() || '';
  const isText = attachmentPreviewCapability(hit).kind === 'text';
  const isPdf = mime === 'application/pdf';
  const isArchive = mime.includes('zip') || mime.includes('archive');
  const glyph = hit.contentKind === 'IMAGE' ? icons.image
    : hit.contentKind === 'VIDEO' ? icons.play
      : hit.contentKind === 'AUDIO' ? icons.audio
        : isArchive ? icons.archive
          : isText || isPdf ? icons.fileText : icons.file;
  const kind = hit.contentKind === 'VIDEO' ? 'video' : hit.contentKind === 'AUDIO' ? 'audio' : isPdf ? 'pdf' : isArchive ? 'archive' : isText ? 'text' : 'file';
  const label = hit.contentKind === 'VIDEO' ? '视频' : hit.contentKind === 'AUDIO' ? '音频' : isPdf ? 'PDF' : extension || '文件';
  return `<span class="desktop-search-file-preview kind-${kind}" aria-hidden="true">${icon(glyph, label)}<b>${escapeHtml(label)}</b></span>`;
}

function cardDetails(hit, query) {
  const name = String(hit.displayName || hit.title || '本地附件');
  const context = String(hit.title || '').trim();
  const distinctContext = context && context !== name ? highlightSearchText(context, query) : '';
  return `<span class="desktop-search-card-details"><strong>${highlightSearchText(name, query)}</strong><small>${distinctContext}</small><span class="desktop-search-card-facts"><small>${formatSearchBytes(hit.byteCount)}</small><small>${formatSearchTimestamp(hit.timestamp)}</small></span></span>`;
}

function imageAttachmentCard(hit, query, thumbnails, videoThumbnails) {
  const entryId = escapeHtml(hit.entryId);
  return `<article class="desktop-search-attachment-card desktop-search-image-card" data-search-entry-id="${entryId}" data-image-thumbnail="${escapeHtml(hit.attachmentId)}" data-thumbnail-workspace-id="${escapeHtml(hit.workspaceId)}">
    <button class="desktop-search-attachment-open desktop-search-media-open" data-action="open-search-attachment" data-entry-id="${entryId}" aria-label="预览图片：${escapeHtml(hit.displayName || '图片')}"><span class="desktop-search-visual-preview desktop-search-image-preview">${attachmentGlyph(hit, thumbnails, videoThumbnails)}</span>${cardDetails(hit, query)}</button>
  </article>`;
}

function videoAttachmentCard(hit, query, thumbnails, videoThumbnails) {
  const entryId = escapeHtml(hit.entryId);
  return `<article class="desktop-search-attachment-card desktop-search-video-card" data-search-entry-id="${entryId}" data-video-thumbnail="${escapeHtml(hit.attachmentId)}" data-thumbnail-workspace-id="${escapeHtml(hit.workspaceId)}">
    <button class="desktop-search-attachment-open desktop-search-media-open" data-action="open-search-attachment" data-entry-id="${entryId}" aria-label="预览视频：${escapeHtml(hit.displayName || '视频')}"><span class="desktop-search-visual-preview desktop-search-video-preview">${attachmentGlyph(hit, thumbnails, videoThumbnails)}<span class="desktop-search-video-play" aria-hidden="true">${icon(icons.play, '播放')}</span></span>${cardDetails(hit, query)}</button>
  </article>`;
}

function audioAttachmentCard(hit, query, attachmentPreviews) {
  const entryId = escapeHtml(hit.entryId);
  const preview = attachmentPreviews?.[hit.attachmentId];
  return `<article class="desktop-search-attachment-card desktop-search-audio-card" data-search-entry-id="${entryId}" data-search-attachment-preview="${escapeHtml(hit.attachmentId)}" data-thumbnail-workspace-id="${escapeHtml(hit.workspaceId)}">
    <button class="desktop-search-attachment-open desktop-search-media-open" data-action="open-search-attachment" data-entry-id="${entryId}" aria-label="播放音频：${escapeHtml(hit.displayName || '音频')}"><span class="desktop-search-visual-preview desktop-search-audio-preview"><span class="desktop-search-audio-heading"><span class="desktop-search-audio-play">${icon(icons.play, '播放')}</span><b>${audioFormatLabel(hit)}</b><time>${preview?.durationMillis ? formatMediaDuration(preview.durationMillis) : '--:--'}</time></span><span class="desktop-search-audio-track"><i></i></span></span>${cardDetails(hit, query)}</button>
  </article>`;
}

function filePreview(hit, attachmentPreviews) {
  const preview = attachmentPreviews?.[hit.attachmentId];
  const mime = String(hit.mimeType || '').toLowerCase();
  const isText = attachmentPreviewCapability(hit).kind === 'text';
  if (preview?.dataUrl) return `<span class="desktop-search-visual-preview desktop-search-pdf-preview"><img src="${escapeHtml(preview.dataUrl)}" alt="${escapeHtml(hit.displayName || 'PDF')}首页预览"></span>`;
  if (isText && preview?.text) {
    const label = mime === 'text/markdown' ? 'MD' : mime === 'application/json' ? 'JSON' : mime === 'text/csv' ? 'CSV' : 'TXT';
    return `<span class="desktop-search-visual-preview desktop-search-text-file-preview"><b>${label}</b><pre>${escapeHtml(preview.text)}</pre></span>`;
  }
  return `<span class="desktop-search-visual-preview desktop-search-generic-file-preview">${attachmentGlyph(hit, {}, {})}</span>`;
}

function fileAttachmentCard(hit, query, attachmentPreviews) {
  const entryId = escapeHtml(hit.entryId);
  return `<article class="desktop-search-attachment-card desktop-search-file-card" data-search-entry-id="${entryId}" data-search-attachment-preview="${escapeHtml(hit.attachmentId)}" data-thumbnail-workspace-id="${escapeHtml(hit.workspaceId)}">
    <button class="desktop-search-attachment-open desktop-search-media-open" data-action="open-search-attachment" data-entry-id="${entryId}" aria-label="预览附件：${escapeHtml(hit.displayName || '文件')}">${filePreview(hit, attachmentPreviews)}${cardDetails(hit, query)}</button>
  </article>`;
}

function attachmentCard(hit, query, thumbnails, videoThumbnails, attachmentPreviews) {
  if (hit.contentKind === 'IMAGE') return imageAttachmentCard(hit, query, thumbnails, videoThumbnails);
  if (hit.contentKind === 'VIDEO') return videoAttachmentCard(hit, query, thumbnails, videoThumbnails);
  if (hit.contentKind === 'AUDIO') return audioAttachmentCard(hit, query, attachmentPreviews);
  return fileAttachmentCard(hit, query, attachmentPreviews);
}

function attachmentResults(hits, query, sortMode, thumbnails, videoThumbnails, attachmentPreviews) {
  if (sortMode !== 'default') return `<div class="desktop-search-attachment-grid">${hits.map(hit => attachmentCard(hit, query, thumbnails, videoThumbnails, attachmentPreviews)).join('')}</div>`;
  const groups = new Map();
  for (const hit of hits) {
    const [key, label] = monthKey(hit.timestamp);
    if (!groups.has(key)) groups.set(key, { label, hits: [] });
    groups.get(key).hits.push(hit);
  }
  return [...groups.entries()].sort(([left], [right]) => right.localeCompare(left)).map(([, group]) => `<section class="desktop-search-month"><h3><span>${group.label}</span><small>${group.hits.length}</small></h3><div class="desktop-search-attachment-grid">${group.hits.map(hit => attachmentCard(hit, query, thumbnails, videoThumbnails, attachmentPreviews)).join('')}</div></section>`).join('');
}

function sortButton(label, column, selected) {
  const descending = selected === `${column}Descending`;
  const ascending = selected === `${column}Ascending`;
  const active = descending || ascending;
  return `<button class="${active ? 'selected' : ''}" data-action="set-search-sort" data-sort-column="${column}" aria-label="${label}${active ? `，当前${descending ? '倒序' : '正序'}，点击切换` : '，点击按倒序排序'}"><span>${label}</span>${icon(active ? (descending ? icons.chevronDown : icons.chevronUp) : icons.sort, label)}</button>`;
}

function historyPanel(history, highlighted) {
  return `<section class="desktop-search-history-panel" role="dialog" aria-label="最近搜索"><header><strong>最近搜索</strong><button data-action="clear-search-history" ${history.length ? '' : 'disabled'}>清空</button><button data-action="close-search-history" aria-label="关闭历史">${icon(icons.close, '关闭')}</button></header>${history.length ? history.map(query => `<button class="${query === highlighted ? 'selected' : ''}" data-action="fill-search-history" data-query="${escapeHtml(query)}">${icon(icons.history, '历史')}<span>${escapeHtml(query)}</span></button>`).join('') : '<p>暂无已提交的本地搜索。</p>'}</section>`;
}

export function renderDesktopSearchPage({ query = '', category = 'all', sortMode = 'default', fileType = 'all', fileTypeOpen = false, page = { hits: [], textCount: 0, attachmentCount: 0, totalCount: 0, hasMore: false }, loading = false, loadingMore = false, error = '', evidenceLabel = '', history = [], historyOpen = false, historyHighlighted = null, thumbnails = {}, videoThumbnails = {}, attachmentPreviews = {} } = {}) {
  const hits = page?.hits || [];
  const textHits = hits.filter(hit => hit.contentKind === 'TEXT' || hit.contentKind === 'TITLE');
  const attachmentHits = hits.filter(hit => ['IMAGE', 'VIDEO', 'AUDIO', 'FILE'].includes(hit.contentKind));
  const categoryLabel = SEARCH_CATEGORIES.find(([id]) => id === category)?.[1] || '全部';
  const placeholder = category === 'all' ? '搜索全部内容' : `搜索${categoryLabel}`;
  const results = loading ? '<div class="desktop-search-state" role="status"><span class="desktop-search-spinner"></span><strong>正在读取本机索引…</strong></div>'
    : error ? `<div class="desktop-search-state error" role="alert"><strong>搜索未完成</strong><p>${escapeHtml(error)}</p><button data-action="retry-full-search">重试</button></div>`
      : !hits.length ? `<div class="desktop-search-state"><strong>${query.trim() ? '没有匹配的本机内容' : `还没有${categoryLabel === '全部' ? '可搜索内容' : categoryLabel}`}</strong><p>可更换关键词、分类或文件类型。</p></div>`
        : `${category === 'all' || category === 'text' ? `<section class="desktop-search-section"><h2>正文 · ${Number(page.textCount) || 0} 条</h2><div class="desktop-search-text-list">${textHits.map(hit => textCard(hit, query)).join('')}</div></section>` : ''}${category !== 'text' ? `<section class="desktop-search-section"><h2>${category === 'all' ? `附件 · ${Number(page.attachmentCount) || 0} 项` : `共 ${Number(page.attachmentCount) || 0} 项`}</h2>${attachmentResults(attachmentHits, query, sortMode, thumbnails, videoThumbnails, attachmentPreviews)}</section>` : ''}${page.hasMore ? `<div class="desktop-search-load-more"><span>已显示 ${hits.length} / ${Number(page.totalCount) || hits.length} 条</span><button data-action="load-more-search-results" ${loadingMore ? 'disabled' : ''}>${loadingMore ? '正在读取更多…' : '继续加载'}</button></div>` : ''}`;
  return `<main class="desktop-search-page" aria-label="全屏搜索">
    <header class="desktop-search-header"><button data-action="close-search" aria-label="退出搜索">${icon(icons.close, '退出搜索')}</button><h1>搜索</h1></header>
    <nav class="desktop-search-tabs" role="tablist" aria-label="搜索类型">${SEARCH_CATEGORIES.map(([id, label]) => `<button data-action="set-search-category" data-category="${id}" role="tab" aria-selected="${id === category}" class="${id === category ? 'selected' : ''}">${label}</button>`).join('')}</nav>
    <div class="desktop-search-sortbar">${category === 'file' ? `<div class="desktop-search-file-type"><button class="${fileType !== 'all' ? 'selected' : ''}" data-action="toggle-search-file-types" aria-expanded="${fileTypeOpen}" aria-pressed="${fileType !== 'all'}"><span>${escapeHtml(SEARCH_FILE_TYPES.find(([id]) => id === fileType)?.[1] || '全部类型')}</span>${icon(icons.chevronDown, '展开')}</button>${fileTypeOpen ? `<div role="menu">${SEARCH_FILE_TYPES.map(([id, label]) => `<button data-action="set-search-file-type" data-file-type="${id}" class="${id === fileType ? 'selected' : ''}">${label}${id === fileType ? icon(icons.check, '当前类型') : ''}</button>`).join('')}</div>` : ''}</div>` : '<span></span>'}<div class="desktop-search-sort-controls">${sortButton('时间', 'time', sortMode)}${sortButton('大小', 'size', sortMode)}<button class="${sortMode === 'default' ? 'selected' : ''}" data-action="set-search-sort" data-sort-column="default">还原</button></div></div>
    <div class="desktop-search-results" data-search-scroll-owner tabindex="0">${evidenceLabel ? `<p class="desktop-search-evidence" role="status">${escapeHtml(evidenceLabel)}</p>` : ''}${results}</div>
    <div class="desktop-search-bottom-fade" aria-hidden="true"></div>
    ${historyOpen ? `<button class="desktop-search-history-backdrop" data-action="close-search-history" aria-label="关闭历史"></button>${historyPanel(history, historyHighlighted)}` : ''}
    <footer class="desktop-search-dock"><label>${icon(icons.search, '搜索')}<input id="full-search-input" type="search" value="${escapeHtml(query)}" placeholder="${escapeHtml(placeholder)}" aria-label="搜索${categoryLabel}"><button data-action="clear-full-search" aria-label="清空搜索" ${query ? '' : 'disabled'}>${icon(icons.close, '清空')}</button></label><button data-action="open-search-history" aria-expanded="${historyOpen}">${icon(icons.history, '历史')}<span>历史</span></button></footer>
  </main>`;
}
