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
export function resolveSearchResultAnchor(documentRoot, { messageId, attachmentId } = {}) {
  const safeMessageId = String(messageId || '');
  if (!documentRoot || !safeMessageId) return { message: null, attachment: null, target: null };
  const message = documentRoot.querySelector?.(`[data-message-id="${escapeAttributeValue(safeMessageId)}"]`) || null;
  if (!message) return { message: null, attachment: null, target: null };
  const safeAttachmentId = String(attachmentId || '');
  const attachment = safeAttachmentId
    ? message.querySelector?.(`[data-attachment-id="${escapeAttributeValue(safeAttachmentId)}"]`) || null
    : null;
  return { message, attachment, target: attachment || message };
}
