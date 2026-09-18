const pausedFollow = new WeakSet();
const lastScrollTop = new WeakMap();

export function installRuntimeChatScrolling(app) {
  const owner = event => event.target.closest?.('.chat-scroll[data-conversation-id]');
  const pause = scroll => { if (scroll) { pausedFollow.add(scroll); lastScrollTop.set(scroll, scroll.scrollTop); } };
  app.addEventListener('wheel', event => {
    const scroll = owner(event);
    if (event.deltaY < 0) pause(scroll);
    else if (scroll && event.deltaY > 0 && scroll.scrollHeight - scroll.scrollTop - scroll.clientHeight <= 1) pausedFollow.delete(scroll);
  }, { passive: true });
  app.addEventListener('pointerdown', event => {
    if (!event.target.closest?.('button, a, input, textarea, select')) pause(owner(event));
  }, { passive: true });
  app.addEventListener('keydown', event => {
    if (['ArrowUp', 'PageUp', 'Home'].includes(event.key)) pause(owner(event));
    else if (event.key === 'End') pausedFollow.delete(owner(event));
  });
  app.addEventListener('scroll', event => {
    const scroll = owner(event);
    if (!scroll || event.target !== scroll) return;
    const before = lastScrollTop.get(scroll) ?? scroll.scrollTop;
    if (scroll.scrollTop > before && scroll.scrollHeight - scroll.scrollTop - scroll.clientHeight <= 1) pausedFollow.delete(scroll);
    lastScrollTop.set(scroll, scroll.scrollTop);
  }, true);
}

// The runtime refresh boundary. Only the current conversation can be patched.
export function commitRuntimeChatShell(app, html) {
  const template = document.createElement('template');
  template.innerHTML = html;
  const previous = app.querySelector('.chat-scroll[data-conversation-id]');
  const next = template.content.querySelector('.chat-scroll[data-conversation-id]');
  if (!previous || !next || previous.dataset.conversationId !== next.dataset.conversationId) return false;
  const top = previous.scrollTop;
  const followLatest = !pausedFollow.has(previous) && previous.scrollHeight - top - previous.clientHeight <= 24;
  patchChildren(app, template.content);
  const target = followLatest ? previous.scrollHeight - previous.clientHeight : top;
  // Do not assign unchanged offsets: WebKit cancels wheel momentum on writes.
  if (Math.abs(previous.scrollTop - target) > 1) previous.scrollTop = target;
  return true;
}

// Menus, selection and confirmation dialogs change the surrounding shell, not the
// transcript. Resolve retention slots to the connected owners without detaching them.
export function commitRetainedChatShell(app, html, transcript, sidebar = null) {
  if (!transcript?.isConnected || !app.contains(transcript)) return false;
  const template = document.createElement('template');
  template.innerHTML = html;
  const slot = template.content.querySelector('[data-preserved-transcript-slot]');
  if (!slot) return false;
  const retained = new Map([[slot, transcript]]);
  const sidebarSlot = template.content.querySelector('[data-preserved-sidebar-slot]');
  if (sidebarSlot) {
    if (!sidebar?.isConnected || !app.contains(sidebar)) return false;
    retained.set(sidebarSlot, sidebar);
  }
  patchChildren(app, template.content, retained);
  return true;
}

function key(node) {
  if (node.nodeType !== 1) return '';
  for (const attribute of ['id', 'data-message-id', 'data-conversation-id', 'data-attachment-id']) {
    if (node.hasAttribute(attribute)) return `${node.tagName}:${attribute}:${node.getAttribute(attribute)}`;
  }
  return `${node.tagName}:${node.classList[0] || ''}`;
}

function compatible(left, right) {
  return left.nodeType === right.nodeType && key(left) === key(right);
}

function patchChildren(parent, source, retained = new Map()) {
  let cursor = parent.firstChild;
  for (const desired of [...source.childNodes]) {
    let current = cursor;
    const owner = retained.get(desired);
    while (current && !(owner ? current === owner : compatible(current, desired))) current = current.nextSibling;
    if (!current) {
      const replacement = owner || desired.cloneNode(false);
      if (!owner && desired.nodeType === 1) patchChildren(replacement, desired, retained);
      parent.insertBefore(replacement, cursor);
      continue;
    }
    if (current !== cursor) parent.insertBefore(current, cursor);
    if (!owner) patchNode(current, desired, retained);
    cursor = current.nextSibling;
  }
  while (cursor) { const next = cursor.nextSibling; cursor.remove(); cursor = next; }
}

function patchNode(current, desired, retained) {
  if (current.isEqualNode(desired)) return;
  if (current.nodeType !== 1) { current.nodeValue = desired.nodeValue; return; }
  for (const attribute of [...current.attributes]) {
    if (current.tagName === 'DETAILS' && attribute.name === 'open') continue;
    if (!desired.hasAttribute(attribute.name)) current.removeAttribute(attribute.name);
  }
  for (const attribute of desired.attributes) {
    if (current.tagName === 'DETAILS' && attribute.name === 'open') continue;
    if (current.getAttribute(attribute.name) !== attribute.value) current.setAttribute(attribute.name, attribute.value);
  }
  // Preserve focused controls, caret, code/table scroll containers and image decoders.
  if (current.tagName === 'TEXTAREA') {
    if (current.value !== desired.value) current.value = desired.value;
    if (current.defaultValue !== desired.defaultValue) current.defaultValue = desired.defaultValue;
    return;
  }
  if (current.tagName === 'INPUT' && current.value !== desired.value) current.value = desired.value;
  patchChildren(current, desired, retained);
}
