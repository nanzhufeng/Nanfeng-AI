import { setTimeout as delay } from 'node:timers/promises';
import { observeNativeUntil } from './native-observation.mjs';

// Caller verifies a hidden, synthetic, credential-free acceptance instance.
// DOM input and clicks exercise the real UI handlers; these are not physical IME events.
export async function checkTemporaryModelDraftOrder(page, text) {
  page.setDefaultTimeout(5000);
  if (!(await page.locator('[data-action="toggle-temporary-model"]').count())) {
    await page.locator('[data-action="toggle-temporary-chat"]').click();
  }
  if (!(await page.locator('[data-action="select-temporary-model"]').count())) {
    await page.locator('[data-action="toggle-temporary-model"]').click();
  }
  const before = await page.evaluate(() => __TAURI__.core.invoke('read_desktop_temporary_conversation'));
  const choices = await page.locator('[data-action="select-temporary-model"]').evaluateAll(elements => elements.map(e => e.dataset.modelId));
  const modelId = choices.find(id => id !== before.modelOverrideId);
  if (!modelId) throw new Error('No distinct local model choice');
  await page.evaluate(({ text, modelId }) => {
    const input = document.querySelector('#chat-composer');
    input.value = text;
    input.dispatchEvent(new Event('input', { bubbles: true }));
    document.querySelector(`[data-action="select-temporary-model"][data-model-id="${modelId}"]`).click();
  }, { text, modelId });
  const read = () => page.evaluate(() => __TAURI__.core.invoke('read_desktop_temporary_conversation'));
  await observeNativeUntil({ read, accept: value => value?.draft === text });
  // Let the old 180ms draft timer complete, then read the durable owner again.
  await delay(400);
  const after = await read();
  return { temporaryId: after.temporaryId, requestedModelId: modelId,
    actualModelId: after.modelOverrideId, draftMatches: after.draft === text,
    modelMatches: after.modelOverrideId === modelId,
    routeUnchanged: before.temporaryId === after.temporaryId,
    inputSource: 'DOM input + actual model button click; no Provider request' };
}
