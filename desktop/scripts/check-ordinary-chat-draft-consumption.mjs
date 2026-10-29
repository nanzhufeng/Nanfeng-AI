import { setTimeout as delay } from 'node:timers/promises';
import { observeNativeUntil } from './native-observation.mjs';

// Caller must establish the independent Windows acceptance identity/root and
// loopback CDP ancestry first. This sends synthetic text only with all Providers
// disabled and without stored credentials; it does not test network execution.
export async function checkOrdinaryChatDraftConsumption(page, { text, conversationId = null }) {
  const providers = await page.evaluate(() => globalThis.__TAURI__.core.invoke('read_desktop_model_service_settings'));
  if (!Array.isArray(providers) || providers.some(provider => provider.enabled || provider.credentialStored)) {
    throw new Error('Draft consumption acceptance requires disabled unconfigured Providers');
  }
  const workspaces = await page.evaluate(() => globalThis.__TAURI__.core.invoke('list_desktop_workspaces'));
  if (workspaces.length !== 1) throw new Error('Expected one isolated synthetic workspace');
  const workspaceId = workspaces[0].id;
  if (conversationId) await page.locator(`[data-action="select-chat"][data-id="${conversationId}"]`).click();
  else await page.locator('[data-action="new-chat"]').click();
  await page.locator('#chat-composer').fill(text);
  const readDraft = () => page.evaluate(args => globalThis.__TAURI__.core.invoke('read_desktop_conversation_draft', args), { workspaceId, conversationId });
  await observeNativeUntil({ read: readDraft, accept: draft => draft?.text === text });
  const readWorkspace = () => page.evaluate(args => globalThis.__TAURI__.core.invoke('read_desktop_workspace', args), { workspaceId });
  const before = await readWorkspace();
  await page.locator('[data-action="save-local-message"]').click();
  const after = await observeNativeUntil({ read: readWorkspace, accept: value => value.exchange.conversations.some(c => c.messages.some(m => m.role === 'user' && m.blocks.some(block => block.text === text))) });
  await page.waitForFunction(() => document.querySelector('.chat-runtime-state.failed') && document.querySelector('#chat-composer')?.value === '');
  // Observe beyond the app's 180 ms draft debounce, not merely at commit time.
  await delay(500);
  const draft = await readDraft();
  const target = after.exchange.conversations.find(c => c.messages.some(m => m.role === 'user' && m.blocks.some(block => block.text === text)));
  return {
    workspaceId, submittedRoute: conversationId, targetConversationId: target.id,
    conversationCountBefore: before.exchange.conversations.length,
    conversationCountAfter: after.exchange.conversations.length,
    matchingUserMessages: target.messages.filter(m => m.role === 'user' && m.blocks.some(block => block.text === text)).length,
    persistedDraft: draft, composerValue: await page.locator('#chat-composer').inputValue(),
    passed: draft === null,
  };
}
