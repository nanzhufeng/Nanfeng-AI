function escapeAttributeValue(value) {
  const source = String(value ?? '');
  if (globalThis.CSS?.escape) return globalThis.CSS.escape(source);
  return source.replace(/[^A-Za-z0-9_-]/g, char => `\\${char.codePointAt(0).toString(16)} `);
}

/**
 * Search results carry an exact message ID and, for media/files, attachment ID. Keep the
 * attachment lookup scoped to its message so identical attachment IDs cannot select another
 * rendered surface. The attachment is preferred; message-level text and title hits fall back to
 * their message container.
 */
export function resolveSearchResultAnchor(documentRoot, { conversationId, messageId, attachmentId } = {}) {
  const safeMessageId = String(messageId || '');
  if (!documentRoot || !safeMessageId) return { message: null, attachment: null, target: null };
  const scope = conversationId
    ? documentRoot.querySelector?.(`.chat-scroll[data-conversation-id="${escapeAttributeValue(conversationId)}"]`)
    : documentRoot;
  const message = scope?.querySelector?.(`.chat-message[data-message-id="${escapeAttributeValue(safeMessageId)}"]`) || null;
  if (!message) return { message: null, attachment: null, target: null };
  const safeAttachmentId = String(attachmentId || '');
  const attachment = safeAttachmentId
    ? message.querySelector?.(`[data-attachment-id="${escapeAttributeValue(safeAttachmentId)}"]`) || null
    : null;
  return { message, attachment, target: attachment || message };
}
