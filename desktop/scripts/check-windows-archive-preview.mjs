import assert from 'node:assert/strict';
import { setTimeout as delay } from 'node:timers/promises';

// Caller verifies a hidden synthetic Windows instance. A stopped fresh root is
// seeded at the restored-asset boundary; this does not validate ZIP importing.
export async function checkWindowsArchivePreview(page, { workspaceId, attachmentId, reopen = false }) {
  page.setDefaultTimeout(8000);
  await page.waitForFunction(() => Array.isArray(globalThis.__nanfengV2CommittedExchanges));
  const invoke = (command, args) => page.evaluate(({ command, args }) => __TAURI__.core.invoke(command, args), { command, args });
  const providers = await invoke('read_desktop_model_service_settings');
  assert.ok(providers.every(provider => !provider.enabled && !provider.credentialStored));
  if (!reopen) {
    await invoke('mutate_desktop_domain', { args: { workspaceId, intentId: 'windows-archive-preview-fixture', entity: 'conversation', action: 'create', objectId: null, expectedRevision: null,
      fields: { title: 'Windows ZIP synthetic preview', firstMessage: 'Local synthetic ZIP', attachmentIds: [attachmentId] } } });
    await page.reload();
    await page.waitForFunction(() => Array.isArray(globalThis.__nanfengV2CommittedExchanges));
  }
  const workspace = await invoke('read_desktop_workspace', { workspaceId });
  assert.equal(workspace.exchange.conversations.length, 1);
  await page.locator(`[data-action="select-chat"][data-id="${workspace.exchange.conversations[0].id}"]`).click();
  await page.locator(`[data-action="open-archive-preview"][data-attachment-id="${attachmentId}"]`).click();
  const directory = path => page.locator(`[data-action="open-archive-directory"][data-archive-path="${path}"]`);
  const entry = path => page.locator(`[data-action="open-archive-entry-preview"][data-archive-entry-path="${path}"]`);
  const back = () => page.locator('[data-action="archive-preview-back"]').click();
  await directory('docs/').click();
  await entry('docs/report.pdf').click();
  const pages = [];
  for (const number of [1, 2, 3, 4]) {
    await page.waitForFunction(number => document.querySelector('.archive-preview-surface .pdf-preview-tools small')?.textContent === `第 ${number} / 4 页`
      && document.querySelector('.archive-preview-surface .pdf-preview-frame')?.complete, number);
    pages.push(await page.locator('.archive-preview-surface .pdf-preview-frame').evaluate(img => ({ width: img.naturalWidth, height: img.naturalHeight })));
    if (number < 4) await page.locator('[data-action="archive-pdf-page-next"]').click();
  }
  assert.deepEqual(pages, [{ width: 1229, height: 2048 }, { width: 2048, height: 1229 }, { width: 2048, height: 1536 }, { width: 2048, height: 512 }]);
  assert.equal(await page.locator('[data-action="archive-pdf-page-next"]').isDisabled(), true);
  await page.locator('[data-action="archive-pdf-page-previous"]').click();
  await page.waitForFunction(() => document.querySelector('.archive-preview-surface .pdf-preview-tools small')?.textContent === '第 3 / 4 页');
  await page.locator('[data-action="archive-pdf-page-next"]').click();
  await page.waitForFunction(() => document.querySelector('.archive-preview-surface .pdf-preview-tools small')?.textContent === '第 4 / 4 页');
  assert.deepEqual(await page.locator('.pdf-preview-tools > button').evaluateAll(es => es.map(e => getComputedStyle(e).borderRadius)), ['999px', '999px']);
  await back();
  await entry('docs/notes.md').click();
  await page.locator('.archive-text-preview').waitFor();
  assert.match(await page.locator('.archive-text-preview').innerText(), /合成目录/);
  await back();
  for (const path of ['docs/broken.pdf', 'docs/locked.pdf']) {
    await entry(path).click();
    await page.locator('.archive-preview-surface .image-preview-error').waitFor();
    await back();
  }
  // Return during preparation of a genuinely existing expensive entry. The
  // real UI must remain in the folder and must not revive the loaded entry.
  await page.evaluate(() => {
    document.querySelector('[data-archive-entry-path="docs/workload.pdf"]').click();
    document.querySelector('[data-action="archive-preview-back"]').click();
  });
  await delay(200);
  assert.equal(await page.locator('.archive-preview-entry-header').count(), 0);
  assert.equal(await entry('docs/report.pdf').count(), 1);
  await directory('').click();
  await entry('nested.zip').click();
  await page.locator('.archive-preview-unsupported').waitFor();
  assert.match(await page.locator('.archive-preview-unsupported').innerText(), /当前类型没有经过验证/);
  await page.locator('[data-action="close-archive-preview"]').click();
  assert.equal(await page.locator('.archive-preview-surface').count(), 0);
  await page.locator(`[data-action="open-archive-preview"][data-attachment-id="${attachmentId}"]`).click();
  await page.keyboard.press('Escape');
  await delay(100);
  assert.equal(await page.locator('.archive-preview-surface').count(), 0);
  return { pages, previousThenNext: true, escapeClose: true, errorEntries: ['broken.pdf', 'locked.pdf'], returnDuringLoad: true, nestedArchive: 'explicit unsupported, no recursive extraction', reopen,
    source: 'synthetic restored-asset seed + real domain IPC + rendered UI; no picker/ZIP import/Provider' };
}
