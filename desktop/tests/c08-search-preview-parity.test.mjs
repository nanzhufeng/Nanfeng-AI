import test from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { resolve } from 'node:path';

import { renderDesktopSearchPage } from '../src/desktop-search-page.mjs';
import { attachmentPreviewCapability } from '../src/desktop-attachment-preview-owner.mjs';
import { createC08BrowserSearchPage } from '../src/c08-search-preview-fixture.mjs';

const c08DocxHit = {
  entryId: 'c08-docx-entry',
  workspaceId: 'c08-workspace',
  conversationId: 'c08-conversation',
  messageId: 'c08-message',
  attachmentId: 'c08-docx',
  title: 'C08 搜索与预览验收',
  snippet: 'Office 文件必须留在应用内，不能交给系统应用。',
  contentKind: 'FILE',
  timestamp: '2026-08-21T02:00:00Z',
  mimeType: 'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
  displayName: 'C08-验收.docx',
  fileType: 'docx',
  byteCount: 4096,
  conversationRevision: 1,
};

test('C08 attachment result has one direct in-app preview action and never advertises system open', () => {
  const html = renderDesktopSearchPage({
    category: 'file',
    page: { hits: [c08DocxHit], textCount: 0, attachmentCount: 1, truncated: false },
  });
  assert.doesNotMatch(html, /系统打开/);
  assert.equal((html.match(/data-action="open-search-attachment"/g) || []).length, 1);
  assert.match(html, /aria-label="预览附件：C08-验收\.docx"/);
  assert.doesNotMatch(html, /application\/vnd\.openxmlformats/);
  assert.doesNotMatch(html, /Office 文件必须留在应用内/);
});

test('C08 search routes through the shared preview owner without surfacing implementation labels', async () => {
  const root = resolve(import.meta.dirname, '..');
  const [app, search, chat] = await Promise.all([
    readFile(resolve(root, 'src/app.mjs'), 'utf8'),
    readFile(resolve(root, 'src/desktop-search-page.mjs'), 'utf8'),
    readFile(resolve(root, 'src/chat-shell.mjs'), 'utf8'),
  ]);
  for (const source of [app, chat]) assert.match(source, /desktop-attachment-preview-owner\.mjs/);
  assert.match(search, /desktop-attachment-preview-owner\.mjs/);
  const searchOpenOwner = app.slice(app.indexOf('async function openSearchAttachment'), app.indexOf('const icons ='));
  assert.match(searchOpenOwner, /openAttachmentPreview/);
  assert.doesNotMatch(searchOpenOwner, /open_desktop_attachment_with_system|已交给系统打开/);
});

test('C08 browser evidence uses a labelled read-only fixture instead of a SQLite error state', async () => {
  const app = await readFile(resolve(import.meta.dirname, '../src/app.mjs'), 'utf8');
  assert.match(app, /createC08BrowserSearchPage/);
  assert.match(app, /只读视觉样本 · 不代表 Desktop SQLite 实值/);
  assert.doesNotMatch(app, /state\.searchError = 'Web 预览不会读取 Desktop SQLite/);
});

test('C08 shared capability owner supports safe readers and fails closed for unverified formats', () => {
  assert.deepEqual(attachmentPreviewCapability({ mimeType: 'text/plain' }), {
    kind: 'text', action: 'open-text-preview', label: '应用内安全文本预览', supported: true,
  });
  const unknown = attachmentPreviewCapability({ mimeType: 'application/x-c08-unknown' });
  assert.equal(unknown.supported, false);
  assert.equal(unknown.action, 'open-preview-boundary');
  assert.match(unknown.reason, /未交给系统应用/);
});

test('C08 read-only browser fixture covers every synthetic attachment contract and real-fact sort semantics', () => {
  const all = createC08BrowserSearchPage();
  assert.equal(all.textCount, 1);
  assert.equal(all.attachmentCount, 10);
  assert.deepEqual(
    [...new Set(all.hits.filter(hit => hit.attachmentId).map(hit => hit.displayName?.split('.').pop()?.toLowerCase()))].sort(),
    ['docx', 'json', 'md', 'mp4', 'pdf', 'png', 'txt', 'wav', 'zip'],
  );
  const files = createC08BrowserSearchPage({ query: 'C08', category: 'file', sortMode: 'sizeAscending' });
  assert.deepEqual(files.hits.map(hit => [hit.displayName, hit.byteCount]), [
    ['C08-本机说明.md', 321],
    ['C08-本地说明.txt', 543],
    ['C08-结构数据.json', 768],
    ['C08-安全压缩包.zip', 2048],
    ['C08-Office文档.docx', 4096],
    ['C08-本地报告.pdf', 1_234_567],
  ]);
});

test('C08 app-owned preview layer paints above the full-screen search owner', async () => {
  const css = await readFile(resolve(import.meta.dirname, '../src/chat-shell.css'), 'utf8');
  assert.match(css, /\.app-shell\s*>\s*\.scrim\s*\{[^}]*z-index:\s*(?:[5-9]\d|\d{3,})/s);
});
