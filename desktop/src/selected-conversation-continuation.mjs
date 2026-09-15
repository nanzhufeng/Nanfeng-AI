/** Continue only an existing account-selected sync identity, regardless of visible list. */
export async function syncSelectedContinuation({ invoke, workspaceId, conversationId, syncedConversationKeys = [] }) {
  if (!workspaceId || !conversationId || !syncedConversationKeys.includes(`${workspaceId}:${conversationId}`)) return null;
  const receipt = await invoke('sync_selected_desktop_conversation', { workspaceId, conversationId, continuation: true });
  if (!['SYNCED', 'UP_TO_DATE', 'UNKNOWN'].includes(receipt?.status)) {
    throw new Error(`云端续同步未完成：${receipt?.status || 'NO_RECEIPT'}`);
  }
  return receipt;
}
