import test from 'node:test';
import assert from 'node:assert/strict';
import { preserveModelSettingsFocus } from '../src/model-settings-browser.mjs';
import { createModelSettingsController } from '../src/model-settings-controller.mjs';

function dom() {
  const root = { body: { isConnected: true }, buttons: [], querySelectorAll() { return this.buttons; } };
  root.activeElement = root.body;
  root.replace = definitions => {
    for (const button of root.buttons) button.isConnected = false;
    root.activeElement = root.body;
    const scroller = { scrollTop: 0, scrollLeft: 0 }; root.scroller = scroller;
    root.buttons = definitions.map(({ value = 'B', kind = 'preset', platform = null, disabled = false } = {}) => {
      const group = { getAttribute: () => kind === 'preset' ? 'radiogroup' : 'tablist' };
      const button = { isConnected: true, disabled, clicks: 0,
        dataset: kind === 'preset' ? { presetId: value } : { providerId: value },
        getAttribute(name) { return name === 'data-provider-platform' ? platform
          : name === 'data-preset-id' ? this.dataset.presetId ?? null : name === 'data-provider-id' ? this.dataset.providerId ?? null : null; },
        closest(selector) { return selector === '.model-settings-browser button' ? this : selector === '.model-settings-model-list' ? (kind === 'preset' ? scroller : null) : group; },
        focus(options) { root.activeElement = this; this.focusOptions = options; },
        click() { this.clicks += 1; } };
      return button;
    });
  };
  root.replace([{}]); root.buttons[0].focus();
  return root;
}
const settle = () => Promise.resolve();

test('a native settings repaint retains the focused model without clicking or scrolling', async () => {
  const root = dom(); preserveModelSettingsFocus(root); root.replace([{}]); await settle();
  assert.equal(root.activeElement, root.buttons[0]);
  assert.deepEqual(root.activeElement.focusOptions, { preventScroll: true });
  assert.equal(root.activeElement.clicks, 0);
});

test('the selected long-list model retains its scroll position after the save repaint', async () => {
  const root = dom(); root.scroller.scrollTop = 320; root.scroller.scrollLeft = 12;
  preserveModelSettingsFocus(root); root.replace([{}]); await settle();
  assert.equal(root.scroller.scrollTop, 320); assert.equal(root.scroller.scrollLeft, 12);
  assert.equal(root.activeElement, root.buttons[0]);
});

test('an accurate relay platform tab is distinct from its credential subgroup', async () => {
  const root = dom(); root.replace([{ kind: 'provider', value: 'RELAYCORE_CLAUDE', platform: 'false' }]); root.buttons[0].focus();
  preserveModelSettingsFocus(root);
  root.replace([{ kind: 'provider', value: 'RELAYCORE_CLAUDE', platform: 'true' }, { kind: 'provider', value: 'RELAYCORE_CLAUDE', platform: 'false' }]);
  await settle(); assert.equal(root.activeElement, root.buttons[1]);
});

test('a stale owner prevents queued focus restoration', async () => {
  const root = dom(); let current = true;
  preserveModelSettingsFocus(root, () => current); root.replace([{}]); current = false; await settle();
  assert.equal(root.activeElement, root.body);
});

test('user focus moved to a different connected control wins over a late repaint', async () => {
  const root = dom(); preserveModelSettingsFocus(root); root.replace([{}]);
  const input = { isConnected: true, value: 'typed key stays local' }; root.activeElement = input;
  await settle(); assert.equal(root.activeElement, input); assert.equal(input.value, 'typed key stays local');
});

test('a still connected focused control needs no restoration', async () => {
  const root = dom(); const previous = root.activeElement; preserveModelSettingsFocus(root); await settle();
  assert.equal(root.activeElement, previous); assert.equal(previous.focusOptions, undefined);
});

for (const replacement of [[], [{ disabled: true }], [{ value: 'other' }]]) {
  test('missing disabled or unrelated replacements are never focused ' + JSON.stringify(replacement), async () => {
    const root = dom(); preserveModelSettingsFocus(root); root.replace(replacement); await settle();
    assert.equal(root.activeElement, root.body);
  });
}

test('typing in the credential field is outside model-button focus restoration', async () => {
  const root = dom(); const input = { isConnected: true, value: 'synthetic-only', closest() { return null; } };
  root.activeElement = input; preserveModelSettingsFocus(root); root.replace([{}]); await settle();
  assert.equal(root.activeElement, root.body); assert.equal(input.value, 'synthetic-only');
});

test('a real choice controller keeps keyboard focus after its delayed native receipt', async () => {
  const root = dom(); root.replace([{ value: 'A' }, { value: 'B' }]); root.buttons[0].focus();
  let resolveNative; const native = new Promise(resolve => { resolveNative = resolve; }); const calls = [];
  const service = { providerId: 'OPENROUTER', enabled: false, presetId: 'A', revision: 1 };
  const owner = { pane: 'settings', settingsSection: 'model-configuration', modelProviderId: 'OPENROUTER',
    modelServiceSettings: [service], modelServiceDraft: { ...service } };
  const controller = createModelSettingsController({ currentOwner: () => owner,
    invoke: async (...args) => { calls.push(args); return native; },
    render: () => { preserveModelSettingsFocus(root); root.replace([{ value: 'A' }, { value: 'B' }]); } });
  const pending = controller.setPreset(owner, 'B'); await settle();
  root.buttons[1].focus(); resolveNative({ ...service, presetId: 'B', revision: 2 }); await pending; await settle();
  assert.equal(root.activeElement.dataset.presetId, 'B');
  assert.equal(owner.modelServiceDraft.presetId, 'B'); assert.equal(owner.modelServiceDraft.revision, 2);
  assert.equal(calls.length, 1); assert.equal(calls[0][1], 'set_desktop_model_service_preset');
  assert.equal(root.activeElement.clicks, 0);
});
