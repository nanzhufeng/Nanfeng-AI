import test from 'node:test';
import assert from 'node:assert/strict';
import { installModelSettingsKeyboardNavigation } from '../src/model-settings-browser.mjs';

function scene({ modelPage = true, modal = false, windowMode = true } = {}) {
  const root = { activeElement: null, querySelector(selector) {
    return selector === '.android-settings-model-configuration' ? (modelPage ? {} : null) : (modal ? {} : null);
  }, addEventListener(name, fn) { this.listener = fn; }, removeEventListener() {} };
  const node = (id, extra = {}) => ({ id, disabled: false, tabIndex: 0, isConnected: true,
    closest: () => null, getClientRects: () => [{}], focus() { root.activeElement = this; }, ...extra });
  const first = node('application-first'), middle = node('model-middle'), last = node('credential-last');
  root.controls = [first, middle, last]; root.querySelectorAll = () => root.controls;
  installModelSettingsKeyboardNavigation(root, undefined, () => windowMode);
  const key = (options = {}) => {
    const event = { key: 'Tab', target: root.activeElement, shiftKey: false, defaultPrevented: false,
      preventDefault() { this.defaultPrevented = true; }, ...options };
    root.listener(event); return event;
  };
  return { root, first, middle, last, key, node };
}

test('last model-settings field Tab wraps to first application control without clicking or saving', () => {
  const s = scene(); s.last.focus(); const event = s.key();
  assert.equal(event.defaultPrevented, true); assert.equal(s.root.activeElement, s.first);
});
test('first application control Shift Tab wraps to last model-settings field', () => {
  const s = scene(); s.first.focus(); const event = s.key({ shiftKey: true });
  assert.equal(event.defaultPrevented, true); assert.equal(s.root.activeElement, s.last);
});
test('middle controls retain browser default Tab navigation', () => {
  const s = scene(); s.middle.focus(); assert.equal(s.key().defaultPrevented, false);
  assert.equal(s.root.activeElement, s.middle);
});
test('browser previews and other platform hosts do not enable the Windows boundary', () => {
  const s = scene({ windowMode: false }); s.last.focus();
  assert.equal(s.key().defaultPrevented, false); assert.equal(s.root.activeElement, s.last);
});
test('modal focus owner and previously handled events keep priority', () => {
  const s = scene({ modal: true }); s.last.focus(); assert.equal(s.key().defaultPrevented, false);
  assert.equal(s.root.activeElement, s.last);
  const plain = scene(); plain.last.focus(); plain.key({ defaultPrevented: true });
  assert.equal(plain.root.activeElement, plain.last);
});
test('other pages modified shortcuts composition and newer focus do not wrap', () => {
  const other = scene({ modelPage: false }); other.last.focus(); assert.equal(other.key().defaultPrevented, false);
  for (const options of [{ ctrlKey: true }, { altKey: true }, { metaKey: true }, { isComposing: true }]) {
    const s = scene(); s.last.focus(); assert.equal(s.key(options).defaultPrevented, false);
  }
  const s = scene(); s.last.focus(); s.key({ target: s.first }); assert.equal(s.root.activeElement, s.last);
});
test('disabled hidden inert and negative-tabindex controls cannot become wrap targets', () => {
  const s = scene(); s.root.controls = [s.node('disabled', { disabled: true }), s.node('hidden', { getClientRects: () => [] }),
    s.node('inert', { closest: () => ({}) }), s.node('negative', { tabIndex: -1 }), s.first, s.last];
  s.last.focus(); s.key(); assert.equal(s.root.activeElement, s.first);
});
