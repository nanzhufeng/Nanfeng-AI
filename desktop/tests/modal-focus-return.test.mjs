import test from 'node:test';
import assert from 'node:assert/strict';
import { installModalLayerOwner } from '../src/modal-layer-owner.mjs';

function fixture(action, { keyboard = false, returnButton = true } = {}, run) {
  const listeners = new Map();
  let layer = null, originPresent = true, sync;
  const doc = { activeElement: null, body: {}, addEventListener(name, fn) {
    const list = listeners.get(name) || []; list.push(fn); listeners.set(name, list);
  } };
  const node = attributes => ({
    attributes, tabIndex: 0, disabled: false, getClientRects: () => [{}],
    hasAttribute(name) { return name in attributes; }, getAttribute(name) { return attributes[name] ?? null; },
    closest(selector) { return selector === '[data-action]' ? this : null; },
    focus(options) { doc.activeElement = this; this.focusOptions = options; },
  });
  const origin = node({ 'data-action': action, 'data-entry-id': 'entry-one' });
  const launcher = node({ 'data-action': 'toggle-composer-add' });
  const modalButton = node({ 'data-action': 'close-composer-camera' });
  const root = { ownerDocument: doc, children: [],
    querySelectorAll: selector => selector.includes('.scrim') && layer ? [layer] : [],
    querySelector: selector => {
      if (selector === '[data-action="toggle-composer-add"]') return returnButton ? launcher : null;
      if (originPresent && selector === `[data-action="${action}"][data-entry-id="entry-one"]`) return origin;
      return null;
    },
  };
  const previous = { MutationObserver: globalThis.MutationObserver, CSS: globalThis.CSS };
  globalThis.CSS = { escape: value => value };
  globalThis.MutationObserver = class { constructor(fn) { sync = fn; } observe() {} disconnect() {} };
  try {
    const dispose = installModalLayerOwner(root);
    doc.activeElement = origin;
    const event = { target: origin, key: 'Enter', button: 0 };
    for (const fn of listeners.get(keyboard ? 'keydown' : 'pointerdown') || []) fn(event);
    if (action === 'open-composer-camera') originPresent = false;
    layer = { contains: n => n === modalButton, querySelectorAll: () => [modalButton], matches: () => false };
    sync(); assert.equal(doc.activeElement, modalButton);
    layer = null; doc.activeElement = doc.body; sync();
    run({ doc, launcher, origin });
    dispose();
  } finally {
    globalThis.MutationObserver = previous.MutationObserver;
    globalThis.CSS = previous.CSS;
  }
}

for (const keyboard of [false, true]) test(`camera modal returns to surviving attachment launcher after ${keyboard ? 'keyboard' : 'pointer'} opening`, () => {
  fixture('open-composer-camera', { keyboard }, ({ doc, launcher }) => {
    assert.equal(doc.activeElement, launcher);
    assert.deepEqual(launcher.focusOptions, { preventScroll: true });
  });
});

test('ordinary modal still restores the exact original entry', () => {
  fixture('edit-reminder', { keyboard: true }, ({ doc, origin }) => assert.equal(doc.activeElement, origin));
});

test('camera return does not focus an unrelated control when the launcher is absent', () => {
  fixture('open-composer-camera', { returnButton: false }, ({ doc }) => assert.equal(doc.activeElement, doc.body));
});
