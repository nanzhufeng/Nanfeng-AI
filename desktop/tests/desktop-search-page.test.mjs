import test from 'node:test';
import assert from 'node:assert/strict';

import {
  bestSearchHistoryMatch,
  highlightSearchText,
  renderDesktopSearchPage,
} from '../src/desktop-search-page.mjs';
import { readFile } from 'node:fs/promises';
import { resolve } from 'node:path';

const textHit = {
  entryId: 'search-workspace-conversation-message-text', workspaceId: 'workspace-search',
  conversationId: 'conversation-search', messageId: 'message-hidden-leaf', branchLeafId: 'message-hidden-leaf',
  title: '隐藏分支会话', snippet: '这里有 Search Needle 正文', contentKind: 'TEXT', timestamp: '2026-08-21T02:00:00Z',
  byteCount: 27, sourceLabel: 'ChatGPT ZIP', archived: true, conversationRevision: 4,
};
const attachmentHit = {
  entryId: 'search-workspace-conversation-message-attachment', workspaceId: 'workspace-search',
  conversationId: 'conversation-search', messageId: 'message-hidden-leaf', attachmentId: 'attachment-real-facts',
  title: '隐藏分支会话', snippet: '真实附件说明', contentKind: 'FILE', timestamp: '2026-08-21T02:00:00Z',
  mimeType: 'application/pdf', displayName: '审计报告.pdf', fileType: 'pdf', byteCount: 4096,
  branchLeafId: 'message-hidden-leaf', sourceLabel: 'ChatGPT ZIP', archived: true, conversationRevision: 4,
};
const imageAttachmentHit = {
  ...attachmentHit,
  entryId: 'search-workspace-conversation-message-image', attachmentId: 'attachment-image-facts',
  displayName: '截图记录.png', snippet: '这段会话正文不能混入图片卡。', contentKind: 'IMAGE',
  mimeType: 'image/png', byteCount: 160 * 1024,
};
const videoAttachmentHit = {
  ...attachmentHit,
  entryId: 'search-workspace-conversation-message-video', attachmentId: 'attachment-video-facts',
  displayName: '现场记录.mp4', snippet: '这段会话正文不能混入视频卡。', contentKind: 'VIDEO',
  mimeType: 'video/mp4', byteCount: 12 * 1024 * 1024,
};
const audioAttachmentHit = {
  ...attachmentHit,
  entryId: 'search-workspace-conversation-message-audio', attachmentId: 'attachment-audio-facts',
  displayName: '访谈录音.mp3', snippet: '这段会话正文不能混入音频卡。', contentKind: 'AUDIO',
  mimeType: 'audio/mpeg', byteCount: 26 * 1024 * 1024,
};
const markdownAttachmentHit = {
  ...attachmentHit,
  entryId: 'search-workspace-conversation-message-markdown', attachmentId: 'attachment-markdown-facts',
  displayName: '复盘记录.md', snippet: '文件名 · text/markdown · 这不是文件正文预览。', contentKind: 'FILE',
  mimeType: 'text/markdown', fileType: 'markdown', byteCount: 12 * 1024,
};

test('history matching is deterministic: exact, prefix, then contains', () => {
  const history = ['搜索合同', '搜索历史合同', '合同搜索'];
  assert.equal(bestSearchHistoryMatch('搜索合同', history), '搜索合同');
  assert.equal(bestSearchHistoryMatch('搜索', history), '搜索合同');
  assert.equal(bestSearchHistoryMatch('历史', history), '搜索历史合同');
  assert.equal(bestSearchHistoryMatch('', history), null);
});

test('highlight escapes source text while marking literal query', () => {
  assert.equal(highlightSearchText('<script>Needle</script>', 'needle'), '&lt;script&gt;<mark>Needle</mark>&lt;/script&gt;');
});

test('full-screen search renders six categories, counts, stable facts and actions', () => {
  const html = renderDesktopSearchPage({
    query: 'Needle', category: 'all', sortMode: 'default',
    page: { hits: [textHit, attachmentHit], textCount: 1, attachmentCount: 1, truncated: false },
    history: ['Needle'], historyOpen: true, historyHighlighted: 'Needle',
  });
  for (const label of ['全部', '正文', '图片', '视频', '音频', '文件']) assert.match(html, new RegExp(`>${label}<`));
  assert.match(html, /正文 · 1 条/);
  assert.match(html, /附件 · 1 项/);
  assert.match(html, /data-entry-id="search-workspace-conversation-message-text"/);
  assert.match(html, /data-action="open-search-attachment"/);
  assert.match(html, /审计报告\.pdf/);
  assert.match(html, /4\.0 KB/);
  assert.doesNotMatch(html, /application\/pdf/);
  assert.doesNotMatch(html, /ChatGPT ZIP/);
  assert.doesNotMatch(html, /真实附件说明/);
  assert.match(html, /已归档/);
  assert.doesNotMatch(html, /\/Users\//);
});

test('search category title cards copy the mobile white pill and orange selected label', async () => {
  const css = await readFile(resolve(import.meta.dirname, '../src/chat-shell.css'), 'utf8');

  assert.match(css, /\.desktop-search-tabs\s*\{[\s\S]*grid-template-columns:\s*repeat\(6, minmax\(0, 1fr\)\)/);
  assert.match(css, /\.desktop-search-tabs button\s*\{[\s\S]*align-items:\s*center;[\s\S]*justify-content:\s*center;[\s\S]*text-align:\s*center/);
  assert.match(css, /\.desktop-search-tabs button\s*\{[^}]*border-radius:\s*999px !important/);
  assert.match(css, /\.desktop-search-tabs button\.selected\s*\{[\s\S]*background:\s*var\(--foreground-surface\)[\s\S]*color:\s*var\(--accent-orange\)/);
  assert.match(css, /\.chat-search-categories button\.selected\s*\{[\s\S]*background:\s*var\(--accent-orange\)/);
  assert.match(css, /\.chat-search-categories button\s*\{[\s\S]*border-radius:\s*999px !important/);
});

test('file category exposes exact type menu and explicit global sort controls', () => {
  const html = renderDesktopSearchPage({
    category: 'file', fileType: 'pdf', fileTypeOpen: true, sortMode: 'sizeDescending',
    page: { hits: [attachmentHit], textCount: 0, attachmentCount: 1, truncated: false },
  });
  for (const label of ['全部类型', 'MD', 'PDF', 'ZIP', 'DOCX', 'TXT', 'JSON', '其他']) assert.match(html, new RegExp(`>${label}`));
  assert.match(html, /data-sort-column="time"/);
  assert.match(html, /data-sort-column="size"/);
  assert.match(html, /data-sort-column="default">还原/);
});

test('file filters share full-pill geometry and theme-colored selected labels', async () => {
  const css = await readFile(resolve(import.meta.dirname, '../src/chat-shell.css'), 'utf8');
  const html = renderDesktopSearchPage({
    category: 'file', fileType: 'pdf', sortMode: 'timeDescending',
    page: { hits: [attachmentHit], textCount: 0, attachmentCount: 1, truncated: false },
  });

  assert.match(css, /\.desktop-search-sort-controls button,[\s\S]*\.desktop-search-file-type > button\s*\{[^}]*border-radius:\s*999px !important/);
  assert.match(css, /\.desktop-search-sort-controls button\.selected,[\s\S]*\.desktop-search-file-type > button\.selected\s*\{[^}]*color:\s*var\(--accent-orange\)/);
  assert.match(html, /class="selected" data-action="toggle-search-file-types"[^>]*aria-pressed="true"/);
  assert.match(html, /class="selected" data-action="set-search-sort" data-sort-column="time"/);
});

test('text and ordinary attachment cards keep only their distinct search value', () => {
  const html = renderDesktopSearchPage({
    query: '附件', category: 'all',
    page: { hits: [textHit, attachmentHit], textCount: 1, attachmentCount: 1, truncated: false },
  });

  assert.match(html, /隐藏分支会话/);
  assert.match(html, /这里有 Search Needle 正文/);
  assert.match(html, /审计报告\.pdf/);
  assert.match(html, /4\.0 KB/);
  for (const redundant of ['ChatGPT ZIP', 'application/pdf', '应用内 PDF 预览', '真实附件说明']) assert.doesNotMatch(html, new RegExp(redundant.replace('/', '\\/')));
});

test('image search cards copy the mobile hierarchy: dominant preview, name, context, and split lower facts', async () => {
  const html = renderDesktopSearchPage({
    category: 'image',
    page: { hits: [imageAttachmentHit], textCount: 0, attachmentCount: 1, truncated: false },
    thumbnails: { 'attachment-image-facts': { dataUrl: 'data:image/png;base64,AA==' } },
  });
  const css = await readFile(resolve(import.meta.dirname, '../src/chat-shell.css'), 'utf8');

  assert.match(html, /desktop-search-image-card/);
  assert.match(html, /desktop-search-image-preview/);
  assert.match(html, /截图记录\.png/);
  assert.match(html, /隐藏分支会话/);
  assert.match(html, /160\.0 KB/);
  assert.doesNotMatch(html, /这段会话正文不能混入图片卡。/);
  assert.doesNotMatch(html, /应用内图片预览/);
  assert.doesNotMatch(html, /<small>image\/png<\/small>/);
  assert.match(html, /desktop-search-card-facts/);
  assert.doesNotMatch(html, /desktop-search-card-menu/);
  assert.match(css, /\.desktop-search-visual-preview\s*\{[^}]*aspect-ratio:\s*16\s*\/\s*9/);
  assert.match(css, /\.desktop-search-image-preview img\s*\{[^}]*object-fit:\s*contain/);
});

test('video search cards copy the mobile hierarchy and keep a real lazy poster behind the play control', async () => {
  const html = renderDesktopSearchPage({
    category: 'video',
    page: { hits: [videoAttachmentHit], textCount: 0, attachmentCount: 1, truncated: false },
  });
  const css = await readFile(resolve(import.meta.dirname, '../src/chat-shell.css'), 'utf8');
  const app = await readFile(resolve(import.meta.dirname, '../src/app.mjs'), 'utf8');

  assert.match(html, /desktop-search-video-card/);
  assert.match(html, /data-video-thumbnail="attachment-video-facts"/);
  assert.match(html, /现场记录\.mp4/);
  assert.match(html, /隐藏分支会话/);
  assert.match(html, /12\.0 MB/);
  assert.doesNotMatch(html, /这段会话正文不能混入视频卡。/);
  assert.doesNotMatch(html, /<small>video\/mp4<\/small>/);
  assert.doesNotMatch(html, /应用内视频预览/);
  assert.match(html, /desktop-search-card-facts/);
  assert.doesNotMatch(html, /desktop-search-card-menu/);
  assert.match(css, /\.desktop-search-video-preview\s*\{[^}]*aspect-ratio:\s*16\s*\/\s*9/);
  assert.match(css, /\.desktop-search-video-preview > img\s*\{[^}]*object-fit:\s*contain/);
  assert.match(app, /read_desktop_video_thumbnail/);
  assert.match(app, /IntersectionObserver/);
});

test('audio search cards use the same shared media canvas as video with real duration metadata', async () => {
  const html = renderDesktopSearchPage({
    category: 'audio',
    page: { hits: [audioAttachmentHit], textCount: 0, attachmentCount: 1, truncated: false },
    attachmentPreviews: { 'attachment-audio-facts': { durationMillis: 932_911 } },
  });

  assert.match(html, /desktop-search-audio-preview/);
  assert.match(html, />MP3</);
  assert.match(html, />15:32</);
  assert.match(html, /访谈录音\.mp3/);
  assert.match(html, /隐藏分支会话/);
  assert.match(html, /26\.0 MB/);
  assert.doesNotMatch(html, /这段会话正文不能混入音频卡。/);
  const css = await readFile(resolve(import.meta.dirname, '../src/chat-shell.css'), 'utf8');
  assert.match(css, /--attachment-media-canvas:\s*#303330/);
  assert.match(css, /\.desktop-search-video-preview\s*\{[^}]*background:\s*var\(--attachment-media-canvas\)/);
  assert.match(css, /\.desktop-search-audio-preview\s*\{[^}]*border-color:\s*var\(--attachment-media-canvas\);[^}]*background:\s*var\(--attachment-media-canvas\)/);
});

test('file search cards preview verified file content instead of index snippets', () => {
  const html = renderDesktopSearchPage({
    category: 'file',
    page: { hits: [markdownAttachmentHit, attachmentHit], textCount: 0, attachmentCount: 2, truncated: false },
    attachmentPreviews: {
      'attachment-markdown-facts': { text: '# 真实复盘\n这里是文件开头' },
      'attachment-real-facts': { dataUrl: 'data:image/png;base64,PDF=' },
    },
  });

  assert.match(html, /desktop-search-text-file-preview/);
  assert.match(html, /# 真实复盘/);
  assert.match(html, /desktop-search-pdf-preview/);
  assert.match(html, /data:image\/png;base64,PDF=/);
  assert.doesNotMatch(html, /这不是文件正文预览/);
  assert.match(html, /复盘记录\.md/);
  assert.match(html, /隐藏分支会话/);
});

test('attachment catalogue copies mobile total and month counts', () => {
  const html = renderDesktopSearchPage({
    category: 'image',
    page: { hits: [imageAttachmentHit], textCount: 0, attachmentCount: 1, truncated: false },
  });
  assert.match(html, /共 1 项/);
  assert.match(html, /<span>2026年8月<\/span><small>1<\/small>/);
});

test('every search result card places compact size and time facts on its lower trailing edge', async () => {
  const css = await readFile(resolve(import.meta.dirname, '../src/chat-shell.css'), 'utf8');

  assert.match(css, /\.desktop-search-text-card\s*\{[\s\S]*grid-template-columns:\s*minmax\(0, 1fr\)/);
  assert.match(css, /\.desktop-search-facts\s*\{[\s\S]*justify-content:\s*space-between/);
  assert.match(css, /\.desktop-search-card-facts\s*\{[\s\S]*justify-content:\s*space-between/);
});

test('loading, empty and error are honest distinct states', () => {
  assert.match(renderDesktopSearchPage({ loading: true }), /正在读取本机索引/);
  const empty = renderDesktopSearchPage({ query: 'none' });
  assert.match(empty, /没有匹配的本机内容/);
  assert.match(empty, /可更换关键词、分类或文件类型。/);
  assert.doesNotMatch(empty, /本页面不会外发内容/);
  assert.match(renderDesktopSearchPage({ error: 'index failed' }), /index failed/);
});

test('category selection renders immediately while its next local-index page is loading', async () => {
  const source = await readFile(resolve(import.meta.dirname, '../src/app.mjs'), 'utf8');
  const searchOwner = source.slice(source.indexOf('async function runFullSearch('), source.indexOf('function scheduleFullSearch()'));
  assert.match(searchOwner, /state\.searchLoading = !append && !cachedPage;[\s\S]*render\(\);/);
  assert.match(searchOwner, /render\(\);[\s\S]*query_desktop_local_index/);
  assert.match(source, /if \(generation !== fullSearchGeneration\) return;[\s\S]{0,520}state\.searchPage = append/);
  assert.match(source, /function openFullSearch\(category = 'all'\)[\s\S]*void runFullSearch\(\);[\s\S]*read_desktop_local_search_history/);
});

test('category click paints the selected pill before scheduling its local-index query', async () => {
  const source = await readFile(resolve(import.meta.dirname, '../src/app.mjs'), 'utf8');
  const categoryOwner = source.slice(source.indexOf('function selectFullSearchCategory('), source.indexOf('function paintSearchLoadingState('));
  assert.match(categoryOwner, /paintSearchCategorySelection\(category\);/);
  assert.match(categoryOwner, /requestAnimationFrame\(\(\) => \{[\s\S]*runFullSearch\(\{ inPlace: true \}\)/);
  const closeOwner = source.slice(source.indexOf("if (action === 'close-search')"), source.indexOf("if (action === 'return-to-search')"));
  assert.match(closeOwner, /cancelAnimationFrame\(fullSearchCategoryFrame\)/);
});

test('rapid search changes cancel the native SQLite statement instead of only dropping its result', async () => {
  const app = await readFile(resolve(import.meta.dirname, '../src/app.mjs'), 'utf8');
  const rust = await readFile(resolve(import.meta.dirname, '../src-tauri/src/lib.rs'), 'utf8');
  const searchOwner = app.slice(app.indexOf('function cancelDesktopLocalSearch('), app.indexOf('function openFullSearch('));

  assert.match(app, /let fullSearchRequestId = 0;/);
  assert.match(searchOwner, /invoke\('cancel_desktop_local_search', \{ requestId \}\)/);
  assert.match(searchOwner, /const requestId = \+\+fullSearchRequestId;[\s\S]{0,180}cancelDesktopLocalSearch\(requestId - 1\);/);
  assert.match(searchOwner, /function scheduleFullSearch\(\) \{\s*supersedeFullSearch\(\);/);
  assert.match(searchOwner, /invoke\('query_desktop_local_index',[\s\S]*requestId/);
  assert.match(searchOwner, /withFullSearchDeadline\(pending, requestId\)/);
  assert.match(rust, /fn cancel_desktop_local_search\(state: State<'_, AppState>, request_id: u64\)/);
  assert.match(rust, /get_interrupt_handle\(\)/);
  assert.match(rust, /interrupt\.interrupt\(\)/);
});

test('large local-data drill-down uses an explicit progressive page instead of rendering every row', () => {
  const html = renderDesktopSearchPage({
    category: 'all',
    page: { hits: Array.from({ length: 48 }, (_, index) => ({ ...textHit, entryId: `${textHit.entryId}-${index}` })), textCount: 128, attachmentCount: 0, totalCount: 128, hasMore: true },
  });
  assert.equal((html.match(/desktop-search-text-card/g) || []).length, 48);
  assert.match(html, /已显示 48 \/ 128 条/);
  assert.match(html, /data-action="load-more-search-results"/);
});

test('attachment cards expose both direct preview and a bounded long-press action owner', async () => {
  const source = await readFile(resolve(import.meta.dirname, '../src/app.mjs'), 'utf8');
  assert.match(source, /searchAttachmentLongPressTimer/);
  assert.match(source, /\.desktop-search-attachment-card/);
  assert.match(source, /}, 520\);/);
  assert.match(source, /kind: 'search-attachment-actions'/);
  assert.match(source, /searchAttachmentLongPressTriggered === target\.dataset\.entryId/);
  assert.match(source, /renderSearchAttachmentMenu\(state.dialog.hit, state.dialog.anchor\)/);
  assert.match(source, /if \(action === 'preview-search-attachment'\)[\s\S]*openSearchAttachment\(hit\)/);
  assert.match(source, /Math\.hypot\(event\.clientX - searchAttachmentPressOrigin\.x, event\.clientY - searchAttachmentPressOrigin\.y\) > 10/);
});
