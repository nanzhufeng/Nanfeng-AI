import { setTimeout as delay } from 'node:timers/promises';
import { observeNativeUntil } from './native-observation.mjs';

// Caller must verify a hidden synthetic instance, root and CDP process ancestry.
export async function checkConversationChoiceOrder(page, route) {
  page.setDefaultTimeout(5000);
  const read = () => page.evaluate(args => __TAURI__.core.invoke('read_desktop_p6g_selection', args), route);
  if (!(await page.locator('[data-action="select-p6g-model"]').count())) {
    await page.locator('[data-action="toggle-p6g-model-picker"]').click();
    await page.locator('[data-action="select-p6g-tier"][data-tier="DAILY"]').click();
  }
  const before = await read();
  const choices = await page.locator('[data-action="select-p6g-model"]').evaluateAll(elements => elements.map(e => e.dataset.modelId));
  if (choices.length < 2) throw new Error('Expected at least two local choices');
  await page.evaluate(() => {
    const buttons = [...document.querySelectorAll('[data-action="select-p6g-model"]')];
    buttons[0].click();
    buttons[1].click();
  });
  await observeNativeUntil({ read, accept: value => value.conversationOverride.revision > before.conversationOverride.revision });
  await delay(500);
  const after = await read();
  return { requestedModelId: choices[1], actualModelId: after.conversationOverride.modelId,
    modelMatches: choices[1] === after.conversationOverride.modelId,
    revisionDelta: after.conversationOverride.revision - before.conversationOverride.revision,
    preferencesRetained: before.conversationOverride.toneOverride === after.conversationOverride.toneOverride
      && before.conversationOverride.webSearchOverride === after.conversationOverride.webSearchOverride,
    inputSource: 'two DOM clicks on actual model choices; no Provider execution' };
}
