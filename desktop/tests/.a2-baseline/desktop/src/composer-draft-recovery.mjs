// Reads only through the native draft owner; never a browser-storage recovery path.
export async function readComposerDraftRoute({ readDraft, workspaceId, conversationId, preferUnbound = false }) {
  if (preferUnbound && conversationId !== null) {
    const draft = await readDraft(workspaceId, null);
    if (draft && (draft.text?.length || draft.attachments?.length || draft.modelId || draft.toneOverride
        || typeof draft.webSearchOverride === 'boolean')) return { conversationId: null, draft };
  }
  return { conversationId, draft: await readDraft(workspaceId, conversationId) };
}

// A route change may await SQLite twice: first to save the source, then to load
// the destination. New input or a later navigation invalidates either wait.
export async function transitionComposerDraftRoute({ flush, read, isCurrent, commit }) {
  await flush();
  if (!isCurrent()) return false;
  const value = await read();
  if (!isCurrent()) return false;
  await commit(value);
  return true;
}

export function createDraftCloseGuard({ flush, close, failed }) {
  let pending = null;
  return event => {
    event.preventDefault();
    if (!pending) {
      pending = Promise.resolve().then(flush).then(close).catch(failed).finally(() => { pending = null; });
    }
    return pending;
  };
}
