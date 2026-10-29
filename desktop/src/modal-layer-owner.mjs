// Some installed Windows WebViews predate the Popover API and its selector.
const supportsActionPopover = panel => typeof panel?.showPopover === 'function' && typeof panel?.hidePopover === 'function';
const hideActionPopover = panel => { if (!panel) return; if (supportsActionPopover(panel)) { if (panel.matches(':popover-open')) panel.hidePopover(); } else panel.hidden = true; };

// Native tab order includes disclosure summaries and skips negative tabindex.
export function layerTabStops(layer) {
  return [...layer.querySelectorAll('button:not(:disabled), input:not(:disabled), textarea:not(:disabled), select:not(:disabled), a[href], summary, video[controls], audio[controls], [tabindex]')]
    .filter(node => {
      const visibility = node.ownerDocument?.defaultView?.getComputedStyle?.(node)?.visibility;
      return Number(node.tabIndex ?? 0) >= 0 && !node.disabled && node.getClientRects().length && !node.closest('[inert]') && visibility !== 'hidden' && visibility !== 'collapse';
    })
    .sort((a, b) => (Number(a.tabIndex) > 0 ? Number(a.tabIndex) : Infinity) - (Number(b.tabIndex) > 0 ? Number(b.tabIndex) : Infinity));
}

const actionMenuSelector = '.conversation-lifecycle-actions, .memory-reference-menu, .text-preview-menu';
function actionMenuTriggerSelector(element) {
  const menu = element?.closest?.(actionMenuSelector);
  if (menu?.matches?.('.conversation-lifecycle-actions') && menu.hasAttribute('data-lifecycle-action-disclosure'))
    return `[data-lifecycle-action-disclosure="${CSS.escape(menu.getAttribute('data-lifecycle-action-disclosure'))}"] > summary`;
  if (menu?.matches?.('.memory-reference-menu')) return '.memory-reference-menu > summary';
  if (menu?.matches?.('.text-preview-menu')) return '.text-preview-menu > summary';
  return null;
}

// Settings repaints may replace a choice, switch or action-menu summary.
export function preserveSettingsPickerFocus(root, isCurrent = () => true) {
  const control = root.activeElement;
  const choice = control?.matches?.('button[data-action="open-settings-picker"][data-picker]');
  const toggle = !choice && control?.matches?.('button[data-action="toggle-product-setting"][data-key][role="switch"]');
  const summary = !choice && !toggle && control?.tagName === 'SUMMARY' ? actionMenuTriggerSelector(control) : null;
  if (!choice && !toggle && !summary) return;
  const attribute = toggle ? 'key' : 'picker';
  const identity = control.dataset?.[attribute];
  const selector = toggle ? 'button[data-action="toggle-product-setting"][data-key][role="switch"]'
    : 'button[data-action="open-settings-picker"][data-picker]';
  queueMicrotask(() => {
    if (!isCurrent() || control.isConnected) return;
    if (root.activeElement && root.activeElement !== root.body && root.activeElement.isConnected) return;
    const replacement = summary ? root.querySelector(summary)
      : [...root.querySelectorAll(selector)]
        .find(node => !node.disabled && node.isConnected && node.dataset[attribute] === identity);
    replacement?.focus({ preventScroll: true });
  });
}

// Keep candidate keys and the short same-input completion tail with the IME.
// A replacement layer or another input never inherits an earlier edit session.
function imeKeyGuard(doc, top) {
  const sessions = new WeakMap();
  for (const type of ['compositionstart', 'compositionend']) doc.addEventListener(type, event => {
    const layer = top();
    if (layer?.contains(event.target)) sessions.set(event.target, {
      layer, composing: type === 'compositionstart', endedAt: event.timeStamp,
    });
  }, true);
  return (event, layer) => {
    const session = event.target && sessions.get(event.target);
    const same = session?.layer === layer;
    const elapsed = event.timeStamp - session?.endedAt;
    return event.isComposing || event.keyCode === 229 || (same && (session.composing
      || (['Enter', 'Escape'].includes(event.key) && elapsed >= 0 && elapsed < 100)));
  };
}

// Shared modal boundary: one top surface receives pointer, focus and Escape input.
export function installModalLayerOwner(root) {
  const doc = root.ownerDocument;
  let active = null;
  let invoker = null;
  let dismissingAttachmentPointer = false;
  const selectorFor = element => {
    const trigger = actionMenuTriggerSelector(element);
    if (trigger) return trigger;
    const button = element?.closest?.('[data-action]');
    if (!button) return null;
    // The camera entry disappears with its menu; return to the surviving launcher.
    if (button.getAttribute('data-action') === 'open-composer-camera') return '[data-action="toggle-composer-add"]';
    return ['data-action', 'data-message-id', 'data-entry-id', 'data-id', 'data-attachment-id', 'data-picker'].filter(name => button.hasAttribute(name))
      .map(name => `[${name}="${CSS.escape(button.getAttribute(name))}"]`).join('');
  };
  const top = () => [...root.querySelectorAll('.scrim, .android-settings-picker-scrim')].at(-1) || null;
  const isImeKey = imeKeyGuard(doc, top);
  const managed = new Map();
  const isolate = (parent, layer) => {
    for (const child of parent.children) {
      if (child === layer) continue;
      if (child.contains(layer)) isolate(child, layer);
      else { managed.set(child, child.inert); child.inert = true; }
    }
  };
  const focusable = layerTabStops;
  const settingsChoices = layer => layer.matches('[data-action="dismiss-settings-picker"]') ? focusable(layer) : [];
  const sync = () => {
    const layer = top();
    for (const [node, previous] of managed) node.inert = previous;
    managed.clear();
    if (layer) isolate(root, layer);
    if (layer) {
      for (const menu of root.querySelectorAll('details[open]')) if (!layer.contains(menu)) {
        menu.open = false;
        hideActionPopover(menu.querySelector(':scope > span, :scope > div'));
      }
      if (!layer.contains(doc.activeElement)) {
        const choices = settingsChoices(layer);
        const target = choices.find(node => node.getAttribute('aria-pressed') === 'true') || focusable(layer)[0] || layer;
        target.focus({ preventScroll: true });
        if (choices.includes(target)) target.scrollIntoView?.({ block: 'nearest', inline: 'nearest' });
      }
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
    else if (event.target === layer && event.button === 0 && (
      layer.matches('.search-attachment-menu-backdrop')
      || (layer.matches('.attachment-preview-overlay') && !layer.querySelector('.text-preview-menu[open]'))
    )) {
      dismissingAttachmentPointer = true;
      event.preventDefault(); event.stopImmediatePropagation();
      if (layer.matches('.search-attachment-menu-backdrop')) layer.click();
      else layer.querySelector('[data-action^="close-"]')?.click();
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
    if (isImeKey(event, layer)) { event.stopImmediatePropagation(); return; }
    if (event.key === 'Escape') {
      event.preventDefault(); event.stopImmediatePropagation();
      const readerMenu = layer.querySelector('.text-preview-menu[open]');
      if (readerMenu?.matches?.('.text-preview-menu') && readerMenu.open) {
        readerMenu.open = false;
        hideActionPopover(readerMenu.querySelector(':scope > span, :scope > div'));
        readerMenu.querySelector('summary')?.focus({ preventScroll: true });
        return;
      }
      const close = layer.matches('[data-action="dismiss-settings-picker"]') ? layer : layer.querySelector('[data-action^="close-"]');
      if (close) close.click();
      else if (layer.matches('.search-attachment-menu-backdrop')) layer.click();
    } else if (settingsChoices(layer).length && !event.isComposing && event.keyCode !== 229
      && !event.metaKey && !event.ctrlKey && !event.altKey && !event.shiftKey
      && ['ArrowDown', 'ArrowUp', 'Home', 'End'].includes(event.key)) {
      const choices = settingsChoices(layer);
      const index = choices.indexOf(doc.activeElement);
      const target = event.key === 'Home' ? choices[0] : event.key === 'End' ? choices.at(-1)
        : choices[(index + (event.key === 'ArrowDown' ? 1 : -1) + choices.length) % choices.length];
      event.preventDefault(); event.stopImmediatePropagation();
      target.focus({ preventScroll: true });
      target.scrollIntoView?.({ block: 'nearest', inline: 'nearest' });
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
  const isImeKey = imeKeyGuard(doc, top);
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
    if (isImeKey(event, layer)) { event.stopImmediatePropagation(); return; }
    if (event.key === 'Escape') {
      event.preventDefault(); event.stopImmediatePropagation(); close({ navigateBack: true });
    } else if (layer.getAttribute?.('role') === 'menu' && ['ArrowDown', 'ArrowUp', 'Home', 'End'].includes(event.key)
        && !event.target.closest?.('input, textarea, select, [contenteditable="true"]')) {
      event.preventDefault(); event.stopImmediatePropagation();
      const controls = items(layer);
      if (!controls.length) return;
      const index = controls.indexOf(doc.activeElement);
      const next = event.key === 'Home' ? 0 : event.key === 'End' ? controls.length - 1
        : event.key === 'ArrowDown' ? (index + 1) % controls.length
          : index < 0 ? controls.length - 1 : (index - 1 + controls.length) % controls.length;
      controls[next].focus({ preventScroll: true });
      const controlBounds = controls[next].getBoundingClientRect?.();
      const layerBounds = layer.getBoundingClientRect?.();
      if (controlBounds && layerBounds) {
        if (controlBounds.top < layerBounds.top) layer.scrollTop -= layerBounds.top - controlBounds.top;
        else if (controlBounds.bottom > layerBounds.bottom) layer.scrollTop += controlBounds.bottom - layerBounds.bottom;
      }
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

// Message and conversation menus share the transient input boundary, but keep their own focus
// identity and dismissal sequence. They never become global modal surfaces.
export function installMessageMenuLayerOwner(root, close) {
  return installComposerTransientLayerOwner(root, close, '.assistant-message-menu, .chat-context-menu');
}

// Native popovers retain DOM ownership while escaping scroll-container clipping.
export function installActionMenuLayerOwner(root) {
  const selector = actionMenuSelector;
  const openMenus = new Set();
  let resizeFrame = null;
  const place = menu => {
    const panel = menu.querySelector(':scope > span, :scope > div');
    if (!panel) return;
    if (!menu.open) { hideActionPopover(panel); return; }
    panel.hidden = false;
    const nativePopover = supportsActionPopover(panel);
    if (nativePopover) panel.setAttribute('popover', 'manual');
    else panel.removeAttribute('popover');
    // A manually shown Popover otherwise retains the user-agent `inset: 0`
    // geometry. In a grid-backed settings page that makes a one-row action
    // menu stretch to the viewport. Reset every positional edge before
    // measuring, then let only this owner place the intrinsic menu.
    panel.style.position = nativePopover ? 'fixed' : 'absolute'; panel.style.inset = 'auto'; panel.style.margin = '0'; panel.style.right = 'auto'; panel.style.bottom = 'auto';
    panel.style.height = 'fit-content'; panel.style.minHeight = '0'; panel.style.alignSelf = 'start'; panel.style.alignContent = 'start'; panel.style.gridAutoRows = 'max-content';
    panel.style.maxWidth = 'calc(100vw - 16px)'; panel.style.maxHeight = 'calc(100dvh - 16px)'; panel.style.overflowY = 'auto';
    if (nativePopover && !panel.matches(':popover-open')) panel.showPopover();
    const anchor = menu.querySelector('summary').getBoundingClientRect();
    const bounds = panel.getBoundingClientRect();
    const left = Math.max(8, Math.min(innerWidth - bounds.width - 8, anchor.right - bounds.width));
    const below = anchor.bottom + 6;
    const top = Math.max(8, Math.min(innerHeight - bounds.height - 8, below + bounds.height <= innerHeight - 8 ? below : anchor.top - bounds.height - 6));
    // Older details implementations establish a containing block for fixed
    // descendants. Keep the fallback anchored in its own menu coordinates.
    const origin = nativePopover ? { left: 0, top: 0 } : menu.getBoundingClientRect();
    const scaleX = nativePopover ? 1 : (origin.width / menu.offsetWidth || 1);
    const scaleY = nativePopover ? 1 : (origin.height / menu.offsetHeight || 1);
    panel.style.left = `${(left - origin.left) / scaleX}px`;
    panel.style.top = `${(top - origin.top) / scaleY}px`;
  };
  root.addEventListener('toggle', event => {
    if (!event.target.matches(selector)) return;
    for (const menu of openMenus) if (!menu.isConnected) openMenus.delete(menu);
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
      if (!panel?.contains(event.target)) {
        const restoreReaderFocus = menu.matches('.text-preview-menu') && menu.contains(root.ownerDocument.activeElement);
        menu.open = false; place(menu);
        if (restoreReaderFocus) menu.querySelector('summary')?.focus({ preventScroll: true });
      }
    }
  }, true);
  const readerMenu = () => [...root.querySelectorAll('.text-preview-menu[open]')].find(menu => menu.open && menu.matches('.text-preview-menu'));
  const closeActionMenu = (menu, restore = true) => {
    menu.open = false; openMenus.delete(menu); place(menu);
    if (restore) menu.querySelector('summary')?.focus({ preventScroll: true });
  };
  window.addEventListener('blur', () => {
    for (const menu of root.querySelectorAll(selector)) if (menu.open) closeActionMenu(menu, false);
  });
  let readerDismissPointer = null;
  root.ownerDocument.addEventListener('pointerdown', event => {
    readerDismissPointer = null;
    const menu = readerMenu();
    if (!menu || menu.contains(event.target)) return;
    readerDismissPointer = event.pointerId ?? 0;
    closeActionMenu(menu);
    event.preventDefault(); event.stopImmediatePropagation();
  }, true);
  root.ownerDocument.addEventListener('pointerup', event => {
    if (readerDismissPointer === null || readerDismissPointer !== (event.pointerId ?? 0)) return;
    event.preventDefault(); event.stopImmediatePropagation();
  }, true);
  root.ownerDocument.addEventListener('pointercancel', () => { readerDismissPointer = null; }, true);
  root.ownerDocument.addEventListener('click', event => {
    if (readerDismissPointer === null || event.detail === 0) return;
    readerDismissPointer = null;
    event.preventDefault(); event.stopImmediatePropagation();
  }, true);
  root.addEventListener('click', event => {
    const item = event.target.closest?.('button');
    const menu = item?.closest(selector);
    if (menu?.open && !item.disabled) closeActionMenu(menu);
  }, true);
  root.addEventListener('keydown', event => {
    const menu = event.target.closest?.(selector);
    if (!menu || event.isComposing || event.keyCode === 229 || event.ctrlKey || event.metaKey || event.altKey
      || (event.shiftKey && event.key !== 'Tab')) return;
    const summary = menu.querySelector('summary');
    const activation = event.target === summary && ['Enter', ' '].includes(event.key);
    if (!activation && !['ArrowDown', 'ArrowUp', 'Home', 'End', 'Tab'].includes(event.key)) return;
    if (!menu.open && !activation && !['ArrowDown', 'ArrowUp'].includes(event.key)) return;
    event.preventDefault(); event.stopImmediatePropagation();
    if (activation && event.repeat) return;
    if (event.key === 'Tab' || (activation && menu.open)) { closeActionMenu(menu); return; }
    menu.open = true; openMenus.add(menu); place(menu);
    const panel = menu.querySelector(':scope > span, :scope > div');
    const items = panel ? layerTabStops(panel) : [];
    if (!items.length) return;
    const index = items.indexOf(root.ownerDocument.activeElement);
    const next = activation || event.key === 'Home' ? 0 : event.key === 'End' ? items.length - 1
      : event.key === 'ArrowDown' ? (index + 1) % items.length
        : index < 0 ? items.length - 1 : (index - 1 + items.length) % items.length;
    items[next].focus({ preventScroll: true });
    items[next].scrollIntoView?.({ block: 'nearest', inline: 'nearest' });
  }, true);
}
