function imageAssets(message) {
  const assets = (message?.blocks || [])
    .filter(block => block?.kind === 'ASSET_REF' && String(block.asset?.mimeType || '').startsWith('image/'))
    .map(block => String(block.asset?.id || ''))
    .filter(Boolean);
  return String(message?.role || '').toLowerCase() === 'assistant' ? assets.reverse() : assets;
}

// Android scopes image switching to the message that owns the attachment, never to a
// whole conversation or a private storage listing. Assistant generated blocks arrive
// newest-first, so reverse only that message to keep the rail/viewer order aligned.
export function relatedImageIds(conversation, attachmentId) {
  const currentId = String(attachmentId || '');
  if (!currentId) return [];
  const message = (conversation?.messages || []).find(item => imageAssets(item).includes(currentId));
  return message ? imageAssets(message) : [currentId];
}

export function imagePreviewNavigation(imageIds, currentId) {
  const ids = [...new Set((imageIds || []).map(String).filter(Boolean))];
  const index = ids.indexOf(String(currentId || ''));
  const resolvedIndex = index >= 0 ? index : 0;
  return {
    index: resolvedIndex,
    previousId: ids[resolvedIndex - 1] || null,
    nextId: ids[resolvedIndex + 1] || null,
  };
}

// Both keyboard arrows and desktop trackpad swipes resolve through this one
// message-scoped navigation owner.  A missing neighbour is a no-op instead of
// wrapping into a different message or storage listing.
export function imagePreviewTargetId(imageIds, currentId, direction) {
  const navigation = imagePreviewNavigation(imageIds, currentId);
  return direction === 'next' ? navigation.nextId : direction === 'previous' ? navigation.previousId : null;
}
