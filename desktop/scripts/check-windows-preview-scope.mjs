import assert from 'node:assert/strict';

// Caller verifies an isolated native process with two synthetic owned assets.
// Fixture creation is independent of this real UI + native read verification.
export async function checkWindowsPreviewScope(page, { workspaces, attachmentId }) {
  page.setDefaultTimeout(8000);
  if (await page.locator('#chat-search').count()) await page.locator('#chat-search').click();
  await page.locator('#full-search-input').fill('Scope collision');
  await page.locator('#full-search-input').press('Enter');
  const card = workspaceId => page.locator(`[data-search-attachment-preview="${attachmentId}"][data-thumbnail-workspace-id="${workspaceId}"]`);
  for (const [index, workspaceId] of workspaces.entries()) {
    await card(workspaceId).locator('pre').waitFor();
    assert.match(await card(workspaceId).locator('pre').innerText(), new RegExp(`SCOPED PREVIEW ${index ? 'B' : 'A'}`));
    assert.doesNotMatch(await card(workspaceId).locator('pre').innerText(), new RegExp(`SCOPED PREVIEW ${index ? 'A' : 'B'}`));
  }
  // Input rerenders keep scoped cached previews; a second actual search also
  // exercises result projection rather than only one successful DOM hydration.
  await page.locator('#full-search-input').fill('Scope');
  await page.locator('#full-search-input').press('Enter');
  for (const [index, workspaceId] of workspaces.entries()) {
    await card(workspaceId).locator('pre').waitFor();
    assert.match(await card(workspaceId).locator('pre').innerText(), new RegExp(`SCOPED PREVIEW ${index ? 'B' : 'A'}`));
  }
  for (const [index, workspaceId] of workspaces.entries()) {
    await page.locator(`[data-action="open-search-result"][data-workspace-id="${workspaceId}"]`).first().click();
    const transcript = page.locator(`[data-chat-attachment-preview="${attachmentId}"][data-thumbnail-workspace-id="${workspaceId}"]`);
    await transcript.locator('pre').waitFor();
    assert.match(await transcript.locator('pre').innerText(), new RegExp(`SCOPED PREVIEW ${index ? 'B' : 'A'}`));
    assert.doesNotMatch(await transcript.locator('pre').innerText(), new RegExp(`SCOPED PREVIEW ${index ? 'A' : 'B'}`));
    await page.locator('[data-action="return-to-search"]').click();
    await card(workspaceId).locator('pre').waitFor();
  }
  return { sameIdAcrossWorkspaces: true, actualSearchUI: true, cachedRerender: true, nativeReads: true, searchToConversationAndBack: true };
}
