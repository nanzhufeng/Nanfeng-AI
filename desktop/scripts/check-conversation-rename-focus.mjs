import assert from 'node:assert/strict';

// The caller must attach only to its marked, isolated native acceptance instance.
// This check edits and cancels the dialog; it never saves or invokes a service.
export async function checkConversationRenameFocus(page, { conversationId, expectedTitle }) {
  const row = page.locator(`[data-action="select-chat"][data-id="${conversationId}"]`);
  const results = [];
  for (const source of ['header', 'sidebar', 'row']) {
    if (source === 'row') {
      await row.hover();
      await page.locator(`[data-action="open-conversation-rename"][data-id="${conversationId}"]`).click();
    } else {
      if (source === 'header') await page.locator('[data-action="open-conversation-header-menu"]').click();
      else await row.click({ button: 'right' });
      await page.locator('[data-action="context-menu-rename"]').click();
    }
    const input = page.locator('#conversation-rename');
    try {
      await page.waitForFunction(() => {
        const node = document.querySelector('#conversation-rename');
        return document.activeElement === node;
      });
      const initial = await input.evaluate(node => ({ value: node.value, start: node.selectionStart, end: node.selectionEnd }));
      assert.deepEqual(initial, { value: expectedTitle, start: 0, end: expectedTitle.length }, `${source}: title must be fully selected on entry`);
      await page.keyboard.insertText('验收');
      assert.equal(await input.inputValue(), '验收', `${source}: first input replaces the selection`);
      await page.keyboard.insertText('追加');
      assert.equal(await input.inputValue(), '验收追加', `${source}: later input must not select all again`);
      results.push({ source, initial, typed: await input.inputValue() });
    } finally {
      await page.locator('[data-action="close-dialog"]').click();
    }
    assert.equal((await row.innerText()).trim(), expectedTitle, `${source}: cancellation must preserve the original title`);
  }
  return results;
}
