import assert from 'node:assert/strict';

// Caller-verified, hidden synthetic root only. Real native IPC, no replacements.
export async function checkWindowsArchiveRequests(page, { workspaceId, attachmentId, pdfFixture }) {
  const result = await page.evaluate(async ({ workspaceId, attachmentId, pdfFixture }) => {
    const invoke = (command, args) => __TAURI__.core.invoke(command, args);
    const archiveId = await invoke('begin_desktop_archive_preview');
    const started = performance.now();
    const pending = invoke('read_desktop_archive_entry_preview', { requestId: archiveId,
      args: { workspaceId, attachmentId, entryPath: 'docs/workload.pdf', pageNumber: 1 } })
      .then(() => 'unexpected success', error => String(error));
    await new Promise(resolve => setTimeout(resolve, 30));
    const cancelAt = performance.now();
    await invoke('cancel_desktop_archive_preview', { requestId: archiveId });
    const cancellationAckMs = performance.now() - cancelAt;
    const cancellationResult = await pending;
    const totalMs = performance.now() - started;
    const pdf = await invoke('import_desktop_conversation_attachment', { args: { workspaceId, selectedPath: pdfFixture } });
    const pdfId = await invoke('begin_desktop_pdf_preview');
    const otherArchiveId = await invoke('begin_desktop_archive_preview');
    await invoke('cancel_desktop_archive_preview', { requestId: otherArchiveId });
    const last = await invoke('read_desktop_pdf_preview', { requestId: pdfId, args: { workspaceId, attachmentId: pdf.id, pageNumber: 4 } });
    const thumbnail = await invoke('read_desktop_search_attachment_preview', { args: { workspaceId, attachmentId: pdf.id } });
    const imageSize = await new Promise((resolve, reject) => {
      const img = new Image();
      img.onload = () => resolve([img.naturalWidth, img.naturalHeight]);
      img.onerror = () => reject(new Error('native PDF thumbnail cannot decode'));
      img.src = thumbnail.dataUrl;
    });
    const restored = await invoke('read_desktop_pdf_preview', { args: { workspaceId, attachmentId: pdf.id } });
    return { cancellationResult, cancellationAckMs, totalMs, independentPdfPage: last.pageNumber, searchThumbnailSize: imageSize,
      thumbnailDiffersFromLastPage: thumbnail.dataUrl !== last.dataUrl, persistedPageAfterThumbnail: restored.pageNumber,
      source: 'native archive cancellation + independent PDF request + search-thumbnail IPC; not search UI lifecycle acceptance' };
  }, { workspaceId, attachmentId, pdfFixture });
  assert.match(result.cancellationResult, /取消|800704C7/);
  assert.ok(result.cancellationAckMs < 1000);
  assert.equal(result.independentPdfPage, 4);
  assert.deepEqual(result.searchThumbnailSize, [1229, 2048]);
  assert.equal(result.thumbnailDiffersFromLastPage, true);
  assert.equal(result.persistedPageAfterThumbnail, 4);
  return result;
}
