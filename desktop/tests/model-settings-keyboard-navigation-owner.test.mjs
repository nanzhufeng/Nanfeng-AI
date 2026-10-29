import test from 'node:test';
import assert from 'node:assert/strict';
import { installModelSettingsKeyboardNavigation } from '../src/model-settings-browser.mjs';

function scene(definitions = [{ value: 'A' }, { value: 'B' }, { value: 'C' }]) {
  const root = { body: { isConnected: true }, buttons: [], clicks: [], selected: 'A', context: {},
    addEventListener(name, fn) { this.listener = fn; },
    removeEventListener(name, fn) { if (this.listener === fn) this.listener = null; },
    querySelectorAll() { return this.buttons; } };
  const groups = new Map();
  const group = name => {
    if (!groups.has(name)) groups.set(name, { getAttribute: () => definitions[0].kind === 'provider' ? 'tablist' : 'radiogroup',
      querySelectorAll: () => root.buttons.filter(x => x.group === name) });
    return groups.get(name);
  };
  root.repaint = () => {
    for (const button of root.buttons) button.isConnected = false;
    root.activeElement = root.body;
    root.buttons = definitions.map(item => {
      const button = { isConnected: true, disabled: Boolean(item.disabled), group: item.group || 'main',
        dataset: item.kind === 'provider' ? { providerId: item.value } : { presetId: item.value },
        getAttribute(name) { return name === 'data-provider-platform' ? item.platform ?? null
          : name === 'data-provider-id' ? this.dataset.providerId ?? null
          : name === 'data-preset-id' ? this.dataset.presetId ?? null : null; },
        closest(selector) { return selector === '.model-settings-browser button' ? this : group(this.group); },
        click() { root.selected = item.value; root.clicks.push(item.value); root.repaint(); },
        focus() { if (!this.disabled && this.isConnected) root.activeElement = this; } };
      return button;
    });
  };
  root.repaint(); root.buttons[0].focus();
  root.key = key => {
    const event = { key, target: root.activeElement, prevented: false, preventDefault() { this.prevented = true; } };
    root.listener?.(event); return event;
  };
  root.captureScope = () => {
    const context = root.context, selected = root.selected;
    return () => root.context === context && root.selected === selected;
  };
  return root;
}
function frames(run) {
  const previous = globalThis.requestAnimationFrame; const callbacks = [];
  globalThis.requestAnimationFrame = callback => callbacks.push(callback);
  try { run(() => { for (const callback of callbacks.splice(0)) callback(); }); }
  finally { if (previous === undefined) delete globalThis.requestAnimationFrame; else globalThis.requestAnimationFrame = previous; }
}

test('a same-context selection captures scope after its own choice update', () => frames(flush => {
  const root = scene(); installModelSettingsKeyboardNavigation(root, root.captureScope);
  assert.equal(root.key('ArrowDown').prevented, true); flush();
  assert.equal(root.activeElement.dataset.presetId, 'B'); assert.deepEqual(root.clicks, ['B']);
}));

test('a newer credential-field focus wins over the old navigation frame', () => frames(flush => {
  const root = scene(); installModelSettingsKeyboardNavigation(root, root.captureScope);
  root.key('ArrowDown'); const input = { isConnected: true, id: 'model-service-api-key' }; root.activeElement = input;
  flush(); assert.equal(root.activeElement, input); assert.deepEqual(root.clicks, ['B']);
}));

test('a replaced area or provider context cancels the old frame even with matching controls', () => frames(flush => {
  const root = scene(); installModelSettingsKeyboardNavigation(root, root.captureScope);
  root.key('ArrowDown'); root.context = {}; flush();
  assert.equal(root.activeElement, root.body); assert.equal(root.selected, 'B');
}));

test('a newer preset intent is not given the old preset focus', () => frames(flush => {
  const root = scene(); installModelSettingsKeyboardNavigation(root, root.captureScope);
  root.key('ArrowDown'); root.selected = 'A'; flush();
  assert.equal(root.activeElement, root.body); assert.equal(root.selected, 'A');
}));

test('a disappeared target does not transfer focus to another model', () => frames(flush => {
  const root = scene(); installModelSettingsKeyboardNavigation(root, root.captureScope);
  root.key('ArrowDown'); root.buttons = root.buttons.filter(x => x.dataset.presetId !== 'B'); flush();
  assert.equal(root.activeElement, root.body);
}));

test('the main RelayCore tab stays distinct from its same-id subgroup', () => frames(flush => {
  const root = scene([{ kind: 'provider', value: 'OPENROUTER', platform: 'true', group: 'rail' },
    { kind: 'provider', value: 'RELAYCORE_CLAUDE', platform: 'false', group: 'sub' },
    { kind: 'provider', value: 'RELAYCORE_CLAUDE', platform: 'true', group: 'rail' }]);
  installModelSettingsKeyboardNavigation(root, root.captureScope); root.key('ArrowDown'); flush();
  assert.equal(root.activeElement.getAttribute('data-provider-platform'), 'true');
  assert.equal(root.activeElement.dataset.providerId, 'RELAYCORE_CLAUDE');
}));

test('disposing the navigation owner removes subsequent keyboard handling', () => frames(flush => {
  const root = scene(); const dispose = installModelSettingsKeyboardNavigation(root, root.captureScope);
  dispose(); assert.equal(root.key('ArrowDown').prevented, false); flush(); assert.deepEqual(root.clicks, []);
}));
