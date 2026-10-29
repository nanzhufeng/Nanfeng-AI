import assert from 'node:assert/strict';

// Caller verifies hidden synthetic acceptance identity and CDP process ancestry.
// Import is real IPC with synthetic paths, not an OS file-picker acceptance.
export async function checkWindowsPdfPreview(page, { fixturePath, reopen = false }) {
  page.setDefaultTimeout(8000);
  await page.waitForFunction(() => Array.isArray(globalThis.__nanfengV2CommittedExchanges));
  const invoke = (command, args) => page.evaluate(({ command, args }) => __TAURI__.core.invoke(command, args), { command, args });
  const providers = await invoke('read_desktop_model_service_settings');
  assert.ok(providers.every(provider => !provider.enabled && !provider.credentialStored));
  const workspaces = await invoke('list_desktop_workspaces');
  assert.equal(workspaces.length, 1);
  const workspaceId = workspaces[0].id;
  if (!reopen) {
    const attachment = await invoke('import_desktop_conversation_attachment', { args: { workspaceId, selectedPath: fixturePath } });
    await invoke('mutate_desktop_domain', { args: { workspaceId, intentId: 'windows-pdf-preview-fixture', entity: 'conversation', action: 'create', objectId: null, expectedRevision: null,
      fields: { title: 'Windows PDF synthetic preview', firstMessage: 'Local synthetic PDF', attachmentIds: [attachment.id] } } });
    await invoke('save_desktop_conversation_draft', { args: { workspaceId, conversationId: null, text: '', attachmentIds: [] } });
    await page.reload();
    await page.waitForFunction(() => Array.isArray(globalThis.__nanfengV2CommittedExchanges));
  }
  const workspace = await invoke('read_desktop_workspace', { workspaceId });
  assert.equal(workspace.exchange.conversations.length, 1);
  const conversationId = workspace.exchange.conversations[0].id;
  await page.locator(`[data-action="select-chat"][data-id="${conversationId}"]`).click();
  const attachmentId = await page.locator('[data-action="open-pdf-preview"]').first().getAttribute('data-attachment-id');
  await page.locator(`[data-action="open-pdf-preview"][data-attachment-id="${attachmentId}"]`).click();
  const image = page.locator('.pdf-preview-frame');
  const readPage = async (number, expectedSize) => {
    await page.waitForFunction(number => {
      const img = document.querySelector('.pdf-preview-frame');
      return img?.complete && img.naturalWidth > 0 && document.querySelector('.pdf-preview-tools small')?.textContent === `第 ${number} / 4 页`;
    }, number);
    const size = await image.evaluate(img => [img.naturalWidth, img.naturalHeight]);
    assert.deepEqual(size, expectedSize);
    return { number, width: size[0], height: size[1] };
  };
  const pages = [];
  if (!reopen) {
    pages.push(await readPage(1, [1229, 2048]));
    assert.equal(await page.locator('[data-action="pdf-page-previous"]').isDisabled(), true);
    for (const [number, size] of [[2, [2048, 1229]], [3, [2048, 1536]], [4, [2048, 512]]]) {
      await page.locator('[data-action="pdf-page-next"]').click();
      pages.push(await readPage(number, size));
    }
  } else pages.push(await readPage(4, [2048, 512]));
  assert.equal(await page.locator('[data-action="pdf-page-next"]').isDisabled(), true);
  const pageButtonRadii = await page.locator('.pdf-preview-tools > button').evaluateAll(elements => elements.map(element => getComputedStyle(element).borderRadius));
  assert.deepEqual(pageButtonRadii, ['999px', '999px']);
  await page.locator('[data-action="close-pdf-preview"]').click();
  await page.locator('.pdf-preview-dialog').waitFor({ state: 'detached' });
  await page.locator(`[data-action="open-pdf-preview"][data-attachment-id="${attachmentId}"]`).click();
  await readPage(4, [2048, 512]);
  await page.locator('[data-action="close-pdf-preview"]').click();
  return { workspaceId, attachmentId, pages, reopen, retainedPage: 4, pageButtonRadii, source: 'real import/domain IPC + rendered UI; no OS picker or Provider send' };
}
