function messageSiblingOrder(left, right) {
  const ordinal = value => Number.isFinite(Number(value?.ordinal)) ? Number(value.ordinal) : Number.MAX_SAFE_INTEGER;
  return ordinal(left) - ordinal(right)
    || String(left?.createdAt || '').localeCompare(String(right?.createdAt || ''))
    || String(left?.id || '').localeCompare(String(right?.id || ''));
}

/**
 * A conversation is a message tree, not a transport-ordered array. Imports and
 * old backups can serialize a child before its parent or share timestamps.
 */
export function orderedConversationMessages(conversation) {
  const messages = Array.isArray(conversation?.messages) ? conversation.messages.filter(Boolean) : [];
  if (messages.length < 2) return messages;
  const byId = new Map(messages.filter(message => typeof message.id === 'string' && message.id).map(message => [message.id, message]));
  const currentLeafId = typeof conversation?.currentLeafId === 'string' ? conversation.currentLeafId : '';

  if (currentLeafId && byId.has(currentLeafId)) {
    const path = [];
    const visited = new Set();
    let node = byId.get(currentLeafId);
    while (node && !visited.has(node.id)) {
      path.push(node);
      visited.add(node.id);
      const parentId = typeof node.parentId === 'string' ? node.parentId : '';
      if (!parentId) return path.reverse();
      node = byId.get(parentId);
    }
  }

  // Legacy records without a tree retain their saved sequence. Once links
  // exist, use a deterministic root-first walk rather than timestamp order.
  const hasTreeLinks = messages.some(message => typeof message.parentId === 'string' && message.parentId);
  if (!hasTreeLinks) return messages;
  const childrenByParent = new Map();
  const roots = [];
  for (const message of messages) {
    const parentId = typeof message.parentId === 'string' ? message.parentId : '';
    if (parentId && byId.has(parentId) && parentId !== message.id) {
      const children = childrenByParent.get(parentId) || [];
      children.push(message);
      childrenByParent.set(parentId, children);
    } else {
      roots.push(message);
    }
  }
  const ordered = [];
  const visited = new Set();
  const visit = message => {
    if (!message?.id || visited.has(message.id)) return;
    visited.add(message.id);
    ordered.push(message);
    (childrenByParent.get(message.id) || []).sort(messageSiblingOrder).forEach(visit);
  };
  roots.sort(messageSiblingOrder).forEach(visit);
  messages.filter(message => !visited.has(message.id)).sort(messageSiblingOrder).forEach(visit);
  return ordered;
}
