const C08_WORKSPACE_ID = 'c08-browser-workspace';
const C08_CONVERSATION_ID = 'c08-browser-conversation';
const C08_TITLE = 'C08 搜索与预览验收';
const C08_SOURCE = '只读视觉样本';

const base = (entryId, patch) => Object.freeze({
  entryId,
  workspaceId: C08_WORKSPACE_ID,
  conversationId: C08_CONVERSATION_ID,
  messageId: 'c08-browser-message',
  title: C08_TITLE,
  branchLeafId: 'c08-browser-message',
  sourceLabel: C08_SOURCE,
  archived: false,
  conversationRevision: 1,
  ...patch,
});

export const C08_BROWSER_SEARCH_HITS = Object.freeze([
  base('c08-text-message', {
    snippet: 'C08 本机文字命中验收：搜索、排序与预览共享同一安全索引。',
    contentKind: 'TEXT',
    timestamp: '2026-08-21T02:06:00Z',
    byteCount: 82,
  }),
  base('c08-file-text', {
    attachmentId: 'c08-text-attachment', displayName: 'C08-本地说明.txt',
    snippet: '本地安全文本预览', contentKind: 'FILE', mimeType: 'text/plain', fileType: 'txt',
    timestamp: '2026-08-21T02:05:00Z', byteCount: 543,
  }),
  base('c08-file-markdown', {
    attachmentId: 'c08-markdown-attachment', displayName: 'C08-本机说明.md',
    snippet: 'Markdown 由应用内文本阅读器预览', contentKind: 'FILE', mimeType: 'text/markdown', fileType: 'md',
    timestamp: '2026-08-21T02:04:50Z', byteCount: 321,
  }),
  base('c08-file-json', {
    attachmentId: 'c08-json-attachment', displayName: 'C08-结构数据.json',
    snippet: 'JSON 由应用内文本阅读器预览', contentKind: 'FILE', mimeType: 'application/json', fileType: 'json',
    timestamp: '2026-08-21T02:04:40Z', byteCount: 768,
  }),
  base('c08-file-zip', {
    attachmentId: 'c08-zip-attachment', displayName: 'C08-安全压缩包.zip',
    snippet: 'ZIP 仅显示安全目录，不执行内容', contentKind: 'FILE', mimeType: 'application/zip', fileType: 'zip',
    timestamp: '2026-08-21T02:04:30Z', byteCount: 2_048,
  }),
  base('c08-file-docx', {
    attachmentId: 'c08-docx-attachment', displayName: 'C08-Office文档.docx',
    snippet: 'DOCX 由应用内 OOXML 阅读器预览', contentKind: 'FILE', mimeType: 'application/vnd.openxmlformats-officedocument.wordprocessingml.document', fileType: 'docx',
    timestamp: '2026-08-21T02:04:20Z', byteCount: 4_096,
  }),
  base('c08-file-pdf', {
    attachmentId: 'c08-pdf-attachment', displayName: 'C08-本地报告.pdf',
    snippet: '本地 PDF 安全阅读', contentKind: 'FILE', mimeType: 'application/pdf', fileType: 'pdf',
    timestamp: '2026-08-21T02:04:00Z', byteCount: 1_234_567,
  }),
  base('c08-video', {
    attachmentId: 'c08-video-attachment', displayName: 'C08-本地视频.mp4',
    snippet: '本地视频预览', contentKind: 'VIDEO', mimeType: 'video/mp4', fileType: 'other',
    timestamp: '2026-08-21T02:03:00Z', byteCount: 234_567,
  }),
  base('c08-image-first', {
    attachmentId: 'c08-image-attachment', displayName: 'C08-本地图片.png',
    snippet: '共享图片附件 · 引用 1', contentKind: 'IMAGE', mimeType: 'image/png', fileType: 'other',
    timestamp: '2026-08-21T02:02:00Z', byteCount: 12_345,
  }),
  base('c08-audio', {
    attachmentId: 'c08-audio-attachment', displayName: 'C08-本地音频.wav',
    snippet: '本地音频预览', contentKind: 'AUDIO', mimeType: 'audio/wav', fileType: 'other',
    timestamp: '2026-08-21T02:01:00Z', byteCount: 34_567,
  }),
  base('c08-image-second', {
    attachmentId: 'c08-image-attachment', displayName: 'C08-本地图片.png',
    snippet: '共享图片附件 · 引用 2', contentKind: 'IMAGE', mimeType: 'image/png', fileType: 'other',
    timestamp: '2026-08-21T02:00:00Z', byteCount: 12_345,
  }),
]);

function matchesCategory(hit, category) {
  if (category === 'all') return true;
  if (category === 'text') return ['TEXT', 'TITLE'].includes(hit.contentKind);
  if (category === 'image') return hit.contentKind === 'IMAGE';
  if (category === 'video') return hit.contentKind === 'VIDEO';
  if (category === 'audio') return hit.contentKind === 'AUDIO';
  return category === 'file' && hit.contentKind === 'FILE';
}

function compareFacts(left, right, sortMode) {
  if (sortMode === 'timeDescending') return Date.parse(right.timestamp) - Date.parse(left.timestamp);
  if (sortMode === 'timeAscending') return Date.parse(left.timestamp) - Date.parse(right.timestamp);
  if (sortMode === 'sizeDescending') return right.byteCount - left.byteCount;
  if (sortMode === 'sizeAscending') return left.byteCount - right.byteCount;
  return 0;
}

export function createC08BrowserSearchPage({ query = '', category = 'all', sortMode = 'default', fileType = 'all' } = {}) {
  const needle = String(query).trim().toLocaleLowerCase();
  const hits = C08_BROWSER_SEARCH_HITS.filter(hit => {
    if (!matchesCategory(hit, category)) return false;
    if (category === 'file' && fileType !== 'all' && hit.fileType !== fileType) return false;
    if (!needle) return true;
    return [hit.title, hit.snippet, hit.displayName, hit.mimeType]
      .some(value => String(value || '').toLocaleLowerCase().includes(needle));
  });
  if (sortMode !== 'default') hits.sort((left, right) => compareFacts(left, right, sortMode));
  return {
    hits,
    textCount: hits.filter(hit => ['TEXT', 'TITLE'].includes(hit.contentKind)).length,
    attachmentCount: hits.filter(hit => ['IMAGE', 'VIDEO', 'AUDIO', 'FILE'].includes(hit.contentKind)).length,
    truncated: false,
  };
}
