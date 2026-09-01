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
  assert.match(html, /正文 <small>1<\/small>/);
  assert.match(html, /附件 <small>1<\/small>/);
  assert.match(html, /data-entry-id="search-workspace-conversation-message-text"/);
  assert.match(html, /data-action="open-search-attachment"/);
  assert.match(html, /application\/pdf/);
  assert.match(html, /4\.0 KiB/);
  assert.match(html, /ChatGPT ZIP/);
  assert.match(html, /已归档/);
  assert.doesNotMatch(html, /\/Users\//);
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

test('loading, empty and error are honest distinct states', () => {
  assert.match(renderDesktopSearchPage({ loading: true }), /正在读取本机索引/);
  assert.match(renderDesktopSearchPage({ query: 'none' }), /没有匹配的本机内容/);
  assert.match(renderDesktopSearchPage({ error: 'index failed' }), /index failed/);
});

test('attachment cards expose both direct preview and a bounded long-press action owner', async () => {
  const source = await readFile(resolve(import.meta.dirname, '../src/app.mjs'), 'utf8');
  assert.match(source, /searchAttachmentLongPressTimer/);
  assert.match(source, /\.desktop-search-attachment-card/);
  assert.match(source, /}, 520\);/);
  assert.match(source, /kind: 'search-attachment-actions'/);
  assert.match(source, /searchAttachmentLongPressTriggered === target\.dataset\.entryId/);
});
