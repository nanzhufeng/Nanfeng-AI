// Native tab order includes disclosure summaries and skips negative tabindex.
export function layerTabStops(layer) {
  return [...layer.querySelectorAll('button:not(:disabled), input:not(:disabled), textarea:not(:disabled), select:not(:disabled), a[href], summary, video[controls], audio[controls], [tabindex]')]
    .filter(node => {
      const visibility = node.ownerDocument?.defaultView?.getComputedStyle?.(node)?.visibility;
      return Number(node.tabIndex ?? 0) >= 0 && !node.disabled && node.getClientRects().length && !node.closest('[inert]') && visibility !== 'hidden' && visibility !== 'collapse';
    })
    .sort((a, b) => (Number(a.tabIndex) > 0 ? Number(a.tabIndex) : Infinity) - (Number(b.tabIndex) > 0 ? Number(b.tabIndex) : Infinity));
}

// Shared modal boundary: one top surface receives pointer, focus and Escape input.
export function installModalLayerOwner(root) {
  const doc = root.ownerDocument;
  let active = null;
  let invoker = null;
  let dismissingAttachmentPointer = false;
  const selectorFor = element => {
    const button = element?.closest?.('[data-action]');
    if (!button) return null;
    return ['data-action', 'data-message-id', 'data-entry-id', 'data-id', 'data-attachment-id', 'data-picker'].filter(name => button.hasAttribute(name))
      .map(name => `[${name}="${CSS.escape(button.getAttribute(name))}"]`).join('');
  };
  const top = () => [...root.querySelectorAll('.scrim, .android-settings-picker-scrim')].at(-1) || null;
  const managed = new Map();
  const isolate = (parent, layer) => {
    for (const child of parent.children) {
      if (child === layer) continue;
      if (child.contains(layer)) isolate(child, layer);
      else { managed.set(child, child.inert); child.inert = true; }
    }
  };
  const focusable = layerTabStops;
  const sync = () => {
    const layer = top();
    for (const [node, previous] of managed) node.inert = previous;
    managed.clear();
    if (layer) isolate(root, layer);
    if (layer) {
      for (const menu of root.querySelectorAll('details[open]')) if (!layer.contains(menu)) {
        menu.open = false;
        menu.querySelector(':popover-open')?.hidePopover();
      }
      if (!layer.contains(doc.activeElement)) (focusable(layer)[0] || layer).focus({ preventScroll: true });
    } else if (active && invoker) {
      root.querySelector(invoker)?.focus({ preventScroll: true });
      invoker = null;
    }
    active = layer;
  };
  doc.addEventListener('pointerdown', event => {
    dismissingAttachmentPointer = false;
    const layer = top();
    if (!layer) invoker = selectorFor(event.target);
    else if (layer.matches('.search-attachment-menu-backdrop') && event.target === layer && event.button === 0) {
      dismissingAttachmentPointer = true;
      event.preventDefault(); event.stopImmediatePropagation();
      layer.click();
    }
    else if (!layer.contains(event.target)) { event.preventDefault(); event.stopImmediatePropagation(); }
  }, true);
  doc.addEventListener('pointerup', event => {
    if (dismissingAttachmentPointer) { event.preventDefault(); event.stopImmediatePropagation(); }
  }, true);
  doc.addEventListener('pointercancel', () => { dismissingAttachmentPointer = false; }, true);
  doc.addEventListener('click', event => {
    if (!dismissingAttachmentPointer || event.detail === 0) return;
    dismissingAttachmentPointer = false;
    event.preventDefault(); event.stopImmediatePropagation();
  }, true);
  doc.addEventListener('contextmenu', event => {
    if (!top()) invoker = selectorFor(event.target);
  }, true);
  doc.addEventListener('keydown', event => {
    const layer = top();
    if (!layer) {
      // Keyboard activation has no pointerdown. Retain the exact invoker even
      // when rendering the opened layer replaces its original DOM node.
      if (event.key === 'Enter' || event.key === ' ' || event.key === 'ContextMenu' || (event.shiftKey && event.key === 'F10')) invoker = selectorFor(event.target);
      return;
    }
    if (event.key === 'Escape') {
      event.preventDefault(); event.stopImmediatePropagation();
      const close = layer.matches('[data-action="dismiss-settings-picker"]') ? layer : layer.querySelector('[data-action^="close-"]');
      if (close) close.click();
      else if (layer.matches('.search-attachment-menu-backdrop')) layer.click();
    } else if (event.key === 'Tab') {
      const items = focusable(layer);
      if (!items.length) { event.preventDefault(); return; }
      const index = items.indexOf(doc.activeElement);
      if (event.shiftKey ? index <= 0 : index < 0 || index === items.length - 1) {
        event.preventDefault(); (event.shiftKey ? items.at(-1) : items[0]).focus({ preventScroll: true });
      }
    } else if (event.metaKey || event.ctrlKey) {
      // Keep native text editing, but block underlying application shortcuts.
      event.stopImmediatePropagation();
      if (['n', 'o', 'e', '\\'].includes(event.key.toLowerCase())) event.preventDefault();
    }
  }, true);
  const observer = new MutationObserver(sync);
  observer.observe(root, { childList: true, subtree: true });
  sync();
  return () => observer.disconnect();
}

// Composer sheets stay anchored in the composer. Global dialogs retain priority;
// only the active sheet receives keyboard navigation and application shortcuts.
export function installComposerTransientLayerOwner(root, close, layerSelector = '.composer-transient-sheet') {
  const doc = root.ownerDocument;
  let dismissingPointer = false;
  let focusedControl = null;
  const top = () => root.querySelector('.scrim, .android-settings-picker-scrim')
    ? null : root.querySelector(layerSelector);
  const items = layerTabStops;
  const focusInside = () => {
    const layer = top();
    if (!layer) { focusedControl = null; return; }
    if (!layer.contains(doc.activeElement)) {
      const controls = items(layer);
      const previous = focusedControl && layer.querySelector(focusedControl);
      (controls.includes(previous) ? previous : controls[0])?.focus({ preventScroll: true });
    }
    const control = doc.activeElement;
    if (layer.contains(control)) focusedControl = ['data-action', 'data-tier', 'data-tone', 'data-model-id', 'data-id']
      .filter(name => control.hasAttribute(name))
      .map(name => `[${name}="${CSS.escape(control.getAttribute(name))}"]`).join('') || null;
  };
  doc.addEventListener('focusin', focusInside, true);
  doc.addEventListener('pointerdown', event => {
    dismissingPointer = false;
    const layer = top();
    if (!layer || layer.contains(event.target) || event.button !== 0) return;
    dismissingPointer = true;
    event.preventDefault(); event.stopImmediatePropagation(); close();
  }, true);
  doc.addEventListener('pointerup', event => {
    if (dismissingPointer) { event.preventDefault(); event.stopImmediatePropagation(); }
  }, true);
  doc.addEventListener('pointercancel', () => { dismissingPointer = false; }, true);
  doc.addEventListener('click', event => {
    if (!dismissingPointer || event.detail === 0) return;
    dismissingPointer = false;
    event.preventDefault(); event.stopImmediatePropagation();
  }, true);
  doc.addEventListener('keydown', event => {
    dismissingPointer = false;
    const layer = top();
    if (!layer) return;
    if (event.key === 'Escape') {
      event.preventDefault(); event.stopImmediatePropagation(); close({ navigateBack: true });
    } else if (event.key === 'Tab') {
      const controls = items(layer);
      const index = controls.indexOf(doc.activeElement);
      if (!controls.length || (event.shiftKey ? index <= 0 : index < 0 || index === controls.length - 1)) {
        event.preventDefault();
        (event.shiftKey ? controls.at(-1) : controls[0])?.focus({ preventScroll: true });
      }
    } else if (event.metaKey || event.ctrlKey) {
      event.stopImmediatePropagation();
      if (['n', 'o', 'e', '\\'].includes(event.key.toLowerCase())) event.preventDefault();
    }
  }, true);
  const observer = new MutationObserver(focusInside);
  observer.observe(root, { childList: true, subtree: true });
  return () => { observer.disconnect(); doc.removeEventListener('focusin', focusInside, true); };
}

// Message menus share the transient input boundary, but keep their own focus
// identity and dismissal sequence. They never become global modal surfaces.
export function installMessageMenuLayerOwner(root, close) {
  return installComposerTransientLayerOwner(root, close, '.assistant-message-menu');
}

// Native popovers retain DOM ownership while escaping scroll-container clipping.
export function installActionMenuLayerOwner(root) {
  const selector = '.conversation-lifecycle-actions, .memory-reference-menu';
  const openMenus = new Set();
  let resizeFrame = null;
  const place = menu => {
    const panel = menu.querySelector(':scope > span, :scope > div');
    if (!panel) return;
    if (!menu.open) { if (panel.matches(':popover-open')) panel.hidePopover(); return; }
    panel.setAttribute('popover', 'manual');
    // A manually shown Popover otherwise retains the user-agent `inset: 0`
    // geometry. In a grid-backed settings page that makes a one-row action
    // menu stretch to the viewport. Reset every positional edge before
    // measuring, then let only this owner place the intrinsic menu.
    panel.style.position = 'fixed'; panel.style.inset = 'auto'; panel.style.margin = '0'; panel.style.right = 'auto'; panel.style.bottom = 'auto';
    panel.style.height = 'fit-content'; panel.style.minHeight = '0'; panel.style.alignSelf = 'start'; panel.style.alignContent = 'start'; panel.style.gridAutoRows = 'max-content';
    panel.style.maxWidth = 'calc(100vw - 16px)'; panel.style.maxHeight = 'calc(100dvh - 16px)'; panel.style.overflowY = 'auto';
    if (panel.showPopover && !panel.matches(':popover-open')) panel.showPopover();
    const anchor = menu.querySelector('summary').getBoundingClientRect();
    const bounds = panel.getBoundingClientRect();
    panel.style.left = `${Math.max(8, Math.min(innerWidth - bounds.width - 8, anchor.right - bounds.width))}px`;
    const below = anchor.bottom + 6;
    panel.style.top = `${Math.max(8, Math.min(innerHeight - bounds.height - 8, below + bounds.height <= innerHeight - 8 ? below : anchor.top - bounds.height - 6))}px`;
  };
  root.addEventListener('toggle', event => {
    if (!event.target.matches(selector)) return;
    if (event.target.open) openMenus.add(event.target);
    else openMenus.delete(event.target);
    place(event.target);
  }, true);
  window.addEventListener('resize', () => {
    if (resizeFrame !== null) return;
    resizeFrame = requestAnimationFrame(() => {
      resizeFrame = null;
      for (const menu of openMenus) {
        if (!menu.isConnected || !menu.open) openMenus.delete(menu);
        else place(menu);
      }
    });
  });
  document.addEventListener('scroll', event => {
    for (const menu of openMenus) {
      if (!menu.isConnected || !menu.open) { openMenus.delete(menu); continue; }
      const panel = menu.querySelector(':scope > span, :scope > div');
      if (!panel?.contains(event.target)) { menu.open = false; place(menu); }
    }
  }, true);
}
