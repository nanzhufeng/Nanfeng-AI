import test from 'node:test';
import assert from 'node:assert/strict';
import { installMessageMenuLayerOwner, installModalLayerOwner } from '../src/modal-layer-owner.mjs';

function environment() {
  const previous = { MutationObserver: globalThis.MutationObserver, CSS: globalThis.CSS };
  let observer;
  globalThis.MutationObserver = class { constructor(callback) { observer = callback; } observe() {} disconnect() {} };
  globalThis.CSS = { escape: value => value };
  const listeners = new Map();
  const doc = { activeElement: null, addEventListener(name, callback) {
    if (!listeners.has(name)) listeners.set(name, []);
    listeners.get(name).push(callback);
  }, removeEventListener() {} };
  function emit(name, target, fields = {}) {
    const event = { target, button: 0, detail: 1, pointerId: 7, key: '', prevented: false, stopped: false,
      preventDefault() { this.prevented = true; }, stopImmediatePropagation() { this.stopped = true; }, ...fields };
    for (const callback of listeners.get(name) || []) { callback(event); if (event.stopped) break; }
    return event;
  }
  return { doc, emit, mutate: () => observer(), restore: () => Object.assign(globalThis, previous) };
}

function menuFixture(className) {
  const env = environment(); let visible = true, closes = 0;
  const trigger = { closest: () => null }; env.doc.activeElement = trigger;
  const controls = [0, 1, 2, 3].map(index => ({
    tabIndex: 0, disabled: index === 1, getClientRects: () => [{}],
    closest: () => null, hasAttribute: name => name === 'data-action', getAttribute: () => `item-${index}`,
    focus() { env.doc.activeElement = this; }, scrollIntoView() {},
  }));
  const layer = { getAttribute: name => name === 'role' ? 'menu' : null,
    contains: target => target === layer || controls.includes(target),
    querySelectorAll: () => controls, querySelector: () => null };
  const root = { ownerDocument: env.doc, querySelector(selector) {
    return visible && selector.includes(className) ? layer : null;
  } };
  installMessageMenuLayerOwner(root, () => { closes++; visible = false; });
  return { ...env, controls, layer, trigger, get closes() { return closes; } };
}

test('conversation menu takes focus into its enabled actions instead of leaving background controls active', () => {
  const f = menuFixture('.chat-context-menu');
  try { f.mutate(); assert.equal(f.doc.activeElement, f.controls[0]); }
  finally { f.restore(); }
});

test('message menu arrows and Home/End reach enabled actions without scrolling the background', () => {
  const f = menuFixture('.assistant-message-menu');
  try {
    f.mutate();
    for (const [key, index] of [['ArrowDown', 2], ['ArrowUp', 0], ['ArrowUp', 3], ['Home', 0], ['End', 3]]) {
      const event = f.emit('keydown', f.doc.activeElement, { key });
      assert.equal(f.doc.activeElement, f.controls[index], key);
      assert.equal(event.prevented, true, key);
      assert.equal(event.stopped, true, key);
    }
  } finally { f.restore(); }
});

test('outside conversation-menu dismissal consumes one full gesture and permits the next', () => {
  const f = menuFixture('.chat-context-menu');
  try {
    const target = { closest: () => null };
    for (const type of ['pointerdown', 'pointerup', 'click']) assert.equal(f.emit(type, target).stopped, true, type);
    assert.equal(f.closes, 1);
    assert.equal(f.emit('pointerdown', target).stopped, false);
    assert.equal(f.emit('click', target).stopped, false);
  } finally { f.restore(); }
});

test('attachment-preview backdrop closes once and prevents its pointer gesture reaching background handlers', () => {
  const f = environment(); let visible = true, closes = 0, leaked = 0;
  const close = { click() { visible = false; closes++; } };
  const layer = { children: [], closest: () => null,
    contains: target => target === layer || target === close,
    matches: selector => selector.split(',').some(value => value.trim() === '.attachment-preview-overlay'),
    querySelector: selector => selector === '.text-preview-menu[open]' ? null : close,
    querySelectorAll: () => [] };
  f.doc.activeElement = close;
  const root = { ownerDocument: f.doc, children: [layer],
    querySelectorAll: selector => visible && selector.includes('.scrim') ? [layer] : [] };
  installModalLayerOwner(root);
  // Reproduce the existing application fallback, followed by a background document consumer.
  f.doc.addEventListener('pointerdown', event => { if (visible && event.target === layer) close.click(); });
  for (const type of ['pointerdown', 'pointerup', 'click']) f.doc.addEventListener(type, () => leaked++);
  try {
    for (const type of ['pointerdown', 'pointerup', 'click']) f.emit(type, layer);
    assert.equal(closes, 1);
    assert.equal(leaked, 0);
    assert.equal(f.emit('pointerdown', layer).stopped, false);
  } finally { f.restore(); }
});
