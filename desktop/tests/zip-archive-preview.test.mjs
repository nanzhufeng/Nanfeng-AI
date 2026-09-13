import test from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';

test('ZIP attachments open as a navigable archive and only preview validated entries', async () => {
  const [owner, app, rust, archive] = await Promise.all([
    readFile(new URL('../src/desktop-attachment-preview-owner.mjs', import.meta.url), 'utf8'),
    readFile(new URL('../src/app.mjs', import.meta.url), 'utf8'),
    readFile(new URL('../src-tauri/src/lib.rs', import.meta.url), 'utf8'),
    readFile(new URL('../src-tauri/src/desktop_docx_preview.rs', import.meta.url), 'utf8'),
  ]);
  assert.match(owner, /return \{ kind: 'archive', action: 'open-archive-preview', label: '应用内压缩包浏览', supported: true \}/);
  for (const token of ['state.archivePreview = null', 'function archivePreviewDialog()', 'read_desktop_archive_preview', 'read_desktop_archive_entry_preview', 'data-action="open-archive-directory"', 'data-action="open-archive-entry-preview"', 'data-action="archive-preview-back"']) assert.ok(app.includes(token), token);
  for (const token of ['struct DesktopArchivePreviewArgs', 'struct DesktopArchiveEntryPreviewArgs', 'struct DesktopArchivePreview', 'struct DesktopArchiveEntryPreview', 'fn archive_preview(', 'fn archive_entry_preview(', 'async fn read_desktop_archive_preview(', 'async fn read_desktop_archive_entry_preview(']) assert.ok(rust.includes(token), token);
  assert.match(archive, /ZIP 包含不安全路径/);
  assert.match(archive, /ZIP 条目超过预览限制/);
  assert.match(archive, /ZIP 内文件超出安全预览上限/);
});
