import test from 'node:test';
import assert from 'node:assert/strict';
import { installModalLayerOwner, preserveSettingsPickerFocus } from '../src/modal-layer-owner.mjs';

function withPicker(run, { picker = true } = {}) {
  const listeners = {}; let notify;
  const doc = { activeElement: null, addEventListener: (name, fn) => { listeners[name] = fn; } };
  const options = ['small', 'standard', 'disabled', 'large'].map(value => ({
    value, disabled: value === 'disabled', tabIndex: 0,
    getClientRects: () => [{}], closest: () => null,
    getAttribute: name => name === 'aria-pressed' ? String(value === 'standard') : null,
    focus(settings) { doc.activeElement = this; this.focusSettings = settings; },
    scrollIntoView(settings) { this.scrollSettings = settings; },
    click() { throw new Error('Navigation must not select or save'); },
  }));
  const layer = {
    children: [], contains: n => options.includes(n),
    matches: selector => picker && selector.includes('dismiss-settings-picker'),
    querySelectorAll: () => options,
    querySelector: () => null,
    click() { root.layer = null; root.children = []; notify(); },
  };
  const invoker = {
    closest() { return this; },
    hasAttribute: name => ['data-action', 'data-picker'].includes(name),
    getAttribute: name => name === 'data-action' ? 'open-settings-picker' : 'fontSize',
    focus() { doc.activeElement = this; },
  };
  const root = { ownerDocument: doc, children: [], layer: null,
    querySelectorAll(selector) { return selector.includes('.scrim') && this.layer ? [this.layer] : []; },
    querySelector() { return invoker; },
  };
  const oldObserver = globalThis.MutationObserver, oldCss = globalThis.CSS;
  globalThis.MutationObserver = class { constructor(callback) { notify = callback; } observe() {} disconnect() {} };
  globalThis.CSS = { escape: value => value };
  function key(key, extra = {}) {
    const event = { key, target: doc.activeElement, prevented: false, stopped: false,
      preventDefault() { this.prevented = true; }, stopImmediatePropagation() { this.stopped = true; }, ...extra };
    listeners.keydown(event); return event;
  }
  try {
    const dispose = installModalLayerOwner(root);
    doc.activeElement = invoker; key('Enter');
    root.layer = layer; root.children = [layer]; notify();
    run({ doc, options, key, invoker, layer, notify }); dispose();
  } finally { globalThis.MutationObserver = oldObserver; globalThis.CSS = oldCss; }
}

test('opening a settings choice focuses the selected enabled option and Escape restores its invoker', () => withPicker(({ doc, options, key, invoker }) => {
  assert.equal(doc.activeElement, options[1]);
  const escape = key('Escape'); assert.equal(escape.prevented, true); assert.equal(doc.activeElement, invoker);
}));

test('settings arrows wrap past disabled choices and Home/End move without selecting', () => withPicker(({ doc, options, key }) => {
  options[1].focus();
  const down = key('ArrowDown'); assert.equal(down.prevented, true); assert.equal(down.stopped, true); assert.equal(doc.activeElement, options[3]);
  key('ArrowDown'); assert.equal(doc.activeElement, options[0]);
  key('ArrowUp'); assert.equal(doc.activeElement, options[3]);
  key('Home'); assert.equal(doc.activeElement, options[0]);
  key('End'); assert.equal(doc.activeElement, options[3]);
  assert.deepEqual(options[3].scrollSettings, { block: 'nearest', inline: 'nearest' });
}));

test('settings navigation does not intercept modified keys or composition', () => withPicker(({ doc, options, key }) => {
  options[1].focus();
  for (const extra of [{ isComposing: true }, { keyCode: 229 }, { ctrlKey: true }, { altKey: true }, { metaKey: true }, { shiftKey: true }]) {
    assert.equal(key('ArrowDown', extra).prevented, false); assert.equal(doc.activeElement, options[1]);
  }
}));

test('an unrelated form dialog retains native arrow behavior and its first-field focus', () => withPicker(({ doc, options, key }) => {
  assert.equal(doc.activeElement, options[0]);
  assert.equal(key('ArrowDown').prevented, false); assert.equal(doc.activeElement, options[0]);
}, { picker: false }));

test('a repaint inside an open settings choice keeps its current navigation focus', () => withPicker(({ doc, options, key, notify }) => {
  key('End'); notify(); assert.equal(doc.activeElement, options[3]);
}));

function receiptDom() {
  const root = { body: { isConnected: true }, choices: [], querySelectorAll() { return this.choices; } };
  const button = picker => ({ isConnected: true, disabled: false, dataset: { picker }, matches: () => true,
    focus(options) { root.activeElement = this; this.focusOptions = options; } });
  const original = button('fontSize'); original.focus();
  const replacement = button('fontSize'); root.choices = [button('mode'), replacement];
  const repaint = () => { original.isConnected = false; root.activeElement = root.body; };
  return { root, original, replacement, repaint };
}

test('a delayed native receipt repaint retains the exact settings invoker without selecting it', async () => {
  const { root, replacement, repaint } = receiptDom();
  preserveSettingsPickerFocus(root); repaint(); await Promise.resolve();
  assert.equal(root.activeElement, replacement); assert.deepEqual(replacement.focusOptions, { preventScroll: true });
});

test('a stale settings visit or missing invoker never restores a later page focus', async () => {
  for (const missing of [false, true]) {
    const { root, repaint } = receiptDom(); let current = true;
    preserveSettingsPickerFocus(root, () => current); repaint();
    if (missing) root.choices = []; else current = false;
    await Promise.resolve(); assert.equal(root.activeElement, root.body);
  }
});

test('receipt repaint does not steal focus from a newly focused control', async () => {
  const { root, repaint } = receiptDom();
  preserveSettingsPickerFocus(root); repaint(); const other = { isConnected: true }; root.activeElement = other;
  await Promise.resolve(); assert.equal(root.activeElement, other);
});
