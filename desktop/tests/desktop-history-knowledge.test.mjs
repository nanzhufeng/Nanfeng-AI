import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import test from 'node:test';
import { normalizeProductSettings } from '../src/desktop-parity-preferences.mjs';
import { renderChatFirstShell } from '../src/chat-shell.mjs';

const root = new URL('../', import.meta.url);

test('hidden interests survives normalization without adding a visible field', () => {
  const normalized = normalizeProductSettings({ interests: '长期关注可靠的软件交付' });
  assert.equal(normalized.interests, '长期关注可靠的软件交付');
  const html = renderChatFirstShell({
    data: null,
    native: true,
    pane: 'settings',
    settingsSection: 'personalization',
    productSettings: normalized,
    personalizationDraft: normalized,
    settingsCapabilities: { ordinaryChatPersonalization: true, historyLibrary: true },
    status: '', error: '', connection: {},
  });
  assert.match(html, /data-key="historyLibraryEnabled"/);
  assert.doesNotMatch(html, /personalization-interests|长期关注方向|关注方向[^<]*<input/);
});

test('narrow settings uses one recoverable pane instead of clipping the detail page', () => {
  const common = {
    data: null,
    native: true,
    pane: 'settings',
    settingsSection: 'personalization',
    settingsCapabilities: { ordinaryChatPersonalization: true, historyLibrary: true },
    status: '', error: '', connection: {},
  };
  const home = renderChatFirstShell({ ...common, settingsMobileHome: true });
  const detail = renderChatFirstShell({ ...common, settingsMobileHome: false });
  assert.match(home, /android-settings-layout mobile-home/);
  assert.match(detail, /android-settings-layout mobile-detail/);
  assert.match(detail, /data-action="show-settings-home"/);
});

test('history library commands and review states are least-privilege wired', async () => {
  const [app, permission, capability, rust] = await Promise.all([
    readFile(new URL('src/app.mjs', root), 'utf8'),
    readFile(new URL('src-tauri/permissions/default.toml', root), 'utf8'),
    readFile(new URL('src-tauri/capabilities/default.json', root), 'utf8'),
    readFile(new URL('src-tauri/src/desktop_history_knowledge_v1.rs', root), 'utf8'),
  ]);
  for (const token of ['run_desktop_history_knowledge_due', 'retry_desktop_history_knowledge', 'accept_desktop_history_knowledge', 'reject_desktop_history_knowledge', 'delete_desktop_history_knowledge']) {
    assert.ok(app.includes(token), token);
    assert.ok(permission.includes(token), token);
  }
  assert.ok(capability.includes('allow-desktop-history-knowledge'));
  for (const state of ['PENDING_REVIEW', 'UNKNOWN', 'ACCEPTING', 'ACCEPTED', 'REJECTED', 'DELETED']) assert.ok(rust.includes(state));
  assert.ok(rust.includes('WINDOW_MS'));
  assert.ok(rust.includes('currentLeafId'));
  assert.ok(rust.includes("state.as_deref() != Some(\"COMPLETED\")"));
});
