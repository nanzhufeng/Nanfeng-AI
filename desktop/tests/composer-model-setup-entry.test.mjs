import test from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { runInNewContext } from 'node:vm';
import { MODEL_SERVICE_PREVIEW_SETTINGS } from '../src/android-settings-shell.mjs';
import { renderChatFirstShell } from '../src/chat-shell.mjs';

const render = settings => renderChatFirstShell({
  native: true, pane: 'chat', data: { summary: { id: 'setup-entry' }, exchange: { conversations: [], projects: [], knowledge: [], memory: [], relations: [] } },
  modelServiceSettings: settings, composerDraft: '', p6gModelPickerOpen: true, connection: {},
});

test('no enabled chat models leaves an accessible configuration path instead of a disabled picker', () => {
  const html = render(MODEL_SERVICE_PREVIEW_SETTINGS.map(service => ({ ...service, enabled: false })));
  const trigger = html.match(/<button[^>]+data-action="toggle-p6g-model-picker"[^>]*>/)?.[0];
  assert.ok(trigger);
  assert.doesNotMatch(trigger, /\bdisabled\b/);
  assert.match(trigger, /配置模型/);
  assert.match(html, /data-action="open-settings-page"[^>]*data-page="model-configuration"/);
  assert.match(html, /尚未启用聊天模型/);
  assert.match(html, /配置模型服务/);
});

test('enabled model catalog keeps the existing grouped picker without showing setup-required state', () => {
  const html = render(MODEL_SERVICE_PREVIEW_SETTINGS.map(service => ({ ...service, enabled: true })));
  assert.match(html, /data-tier="DAILY"/);
  assert.match(html, /data-tier="DEEP"/);
  assert.doesNotMatch(html, /尚未启用聊天模型/);
});

test('the real settings click handler takes the composer setup entry to the existing model editor', async () => {
  const source = await readFile(new URL('../src/app.mjs', import.meta.url), 'utf8');
  const marker = source.indexOf("if (!['show-settings', 'open-settings-page'");
  const start = source.lastIndexOf("app.addEventListener('click', event => {", marker);
  const end = source.indexOf('\n}, true);', marker) + '\n}, true);'.length;
  assert.ok(start >= 0 && end > start);
  let listener;
  const state = { pane: 'chat', p6gModelPickerOpen: true, settingsSection: 'personalization' };
  let reset = false;
  runInNewContext(source.slice(start, end), {
    state, native: false, app: { addEventListener: (name, callback) => { listener = callback; } },
    closeTopOverlay: ({ restoreFocus }) => { assert.equal(restoreFocus, false); state.p6gModelPickerOpen = false; },
    resetModelServiceDraft: () => { reset = true; state.modelServiceDraft = {}; },
    render() {},
  });
  const target = { dataset: { action: 'open-settings-page', page: 'model-configuration' } };
  listener({ target: { closest: () => target }, preventDefault() {}, stopImmediatePropagation() {} });
  assert.equal(state.pane, 'settings');
  assert.equal(state.settingsSection, 'model-configuration');
  assert.equal(state.p6gModelPickerOpen, false);
  assert.equal(reset, true);
});
