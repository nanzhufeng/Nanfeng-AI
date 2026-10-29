function rowKey(row) {
  const workspaceId = row?.workspaceId;
  const conversationId = row?.conversationId ?? row?.id;
  return typeof workspaceId === 'string' && typeof conversationId === 'string'
    ? `${workspaceId}:${conversationId}`
    : null;
}

function cloudPinned(row) {
  return Boolean(row?.cloudPinned ?? row?.pinned);
}

// A manual cloud read is incremental: rows absent from this response stay as
// cache-only fallbacks, but every returned row takes the server's order.  Both
// devices receive the same RPC order, so a stale local cache cannot become a
// second, device-specific ordering authority.
export function mergeCloudConversationList(existingRows, refreshedRows, authoritativeKeys = null) {
  // Only a complete authenticated inventory may remove cache-only rows.
  // A restored subset is not an inventory: conflicts must remain visible.
  const membership = Array.isArray(authoritativeKeys) ? new Set(authoritativeKeys) : null;
  const refreshedByKey = new Map();
  for (const row of Array.isArray(refreshedRows) ? refreshedRows : []) {
    const key = rowKey(row);
    if (key && !refreshedByKey.has(key)) refreshedByKey.set(key, row);
  }

  const existingByKey = new Map();
  for (const existing of Array.isArray(existingRows) ? existingRows : []) {
    const key = rowKey(existing);
    if (key && !existingByKey.has(key)) existingByKey.set(key, existing);
  }

  const merged = [];
  const retainedKeys = new Set();
  for (const refreshed of refreshedByKey.values()) {
    const key = rowKey(refreshed);
    if (!key || retainedKeys.has(key)) continue;
    retainedKeys.add(key);
    const existing = existingByKey.get(key);
    const pinned = cloudPinned(existing);
    merged.push(existing
      ? { ...existing, ...refreshed, pinned, cloudPinned: pinned }
      : { ...refreshed, pinned: cloudPinned(refreshed), cloudPinned: cloudPinned(refreshed) });
  }

  for (const existing of existingByKey.values()) {
    const key = rowKey(existing);
    if (!key || retainedKeys.has(key)) continue;
    if (membership && !membership.has(key)) continue;
    retainedKeys.add(key);
    const pinned = cloudPinned(existing);
    merged.push({ ...existing, pinned, cloudPinned: pinned });
  }
  return merged;
}
