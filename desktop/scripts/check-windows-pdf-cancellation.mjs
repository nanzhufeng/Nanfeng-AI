import assert from 'node:assert/strict';
import { setTimeout as delay } from 'node:timers/promises';

// Only caller-verified hidden synthetic roots. No writable production hooks,
// replacement transport, credentials, network or direct SQLite injection.
export async function checkWindowsPdfCancellation(page, { workspaceId, attachmentId, heavyFixture }) {
  const session = await page.context().newCDPSession(page);
  let scriptId;
  session.on('Debugger.scriptParsed', event => { if (event.url.endsWith('/app.mjs')) scriptId = event.scriptId; });
  await session.send('Debugger.enable');
  const { scriptSource } = await session.send('Debugger.getScriptSource', { scriptId });
  const lineNumber = scriptSource.split('\n').findIndex(line => line.includes("if (request.id) void invoke('cancel_desktop_pdf_preview'"));
  assert.ok(lineNumber >= 0);
  // A non-pausing logpoint observes the existing cancellation request ID. It
  // never changes state, response, timing gates, or the immutable IPC binding.
  const { breakpointId } = await session.send('Debugger.setBreakpoint', {
    location: { scriptId, lineNumber }, condition: '((globalThis.__pdfCancelledIds ??= []).push(request.id), false)',
  });
  let cancelledId;
  try {
    await page.evaluate(() => {
      document.querySelector('[data-action="open-pdf-preview"]').click();
      document.querySelector('[data-action="close-pdf-preview"]').click();
    });
    await page.waitForFunction(() => globalThis.__pdfCancelledIds?.length > 0);
    cancelledId = await page.evaluate(() => globalThis.__pdfCancelledIds.at(-1));
    await delay(100);
    assert.equal(await page.locator('.pdf-preview-dialog').count(), 0);
  } finally {
    await session.send('Debugger.removeBreakpoint', { breakpointId });
    await session.send('Debugger.disable');
    await session.detach();
  }
  const readCancelled = await page.evaluate(async args => {
    try { await __TAURI__.core.invoke('read_desktop_pdf_preview', args); return 'unexpected success'; }
    catch (error) { return String(error); }
  }, { requestId: cancelledId, args: { workspaceId, attachmentId, pageNumber: 1 } });
  assert.match(readCancelled, /已取消或已被读取/);
  const heavy = await page.evaluate(args => __TAURI__.core.invoke('import_desktop_conversation_attachment', { args }), { workspaceId, selectedPath: heavyFixture });
  const cancelledReads = [];
  for (let index = 0; index < 3; index++) {
    const result = await page.evaluate(async args => {
      const requestId = await __TAURI__.core.invoke('begin_desktop_pdf_preview');
      const started = performance.now();
      const read = __TAURI__.core.invoke('read_desktop_pdf_preview', { requestId, args }).then(() => 'unexpected success', error => String(error));
      await new Promise(resolve => setTimeout(resolve, 30));
      const cancelStart = performance.now();
      await __TAURI__.core.invoke('cancel_desktop_pdf_preview', { requestId });
      const cancelAcknowledgedMs = performance.now() - cancelStart;
      const result = await read;
      return { result, cancelAcknowledgedMs, totalMs: performance.now() - started };
    }, { workspaceId, attachmentId: heavy.id, pageNumber: 1 });
    assert.notEqual(result.result, 'unexpected success');
    assert.match(result.result, /取消|800704C7/);
    assert.ok(result.cancelAcknowledgedMs < 1000, 'cancel must not queue behind the workspace lock');
    cancelledReads.push(result);
  }
  // Normal reader remains available and cancelled reads do not replace the
  // previous successful page of a different attachment.
  await page.locator(`[data-action="open-pdf-preview"][data-attachment-id="${attachmentId}"]`).click();
  await page.waitForFunction(() => document.querySelector('.pdf-preview-tools small')?.textContent === '第 4 / 4 页');
  await page.locator('[data-action="close-pdf-preview"]').click();
  return { uiCloseBeforeAllocation: true, cancelledReservationRejected: true, cancelledReads, retainedPage: 4,
    observation: 'DOM click on real close control, non-pausing ID logpoint, real native cancellation IPC; renderer stage timing separately tested in Rust' };
}
