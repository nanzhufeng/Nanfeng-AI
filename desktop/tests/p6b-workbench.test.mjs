import test from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { resolve } from 'node:path';

const root = resolve(import.meta.dirname, '..');
const source = await readFile(resolve(root, 'src/app.mjs'), 'utf8');
const chatShell = await readFile(resolve(root, 'src/chat-shell.mjs'), 'utf8');
const css = await readFile(resolve(root, 'src/styles.css'), 'utf8');
const capability = await readFile(resolve(root, 'src-tauri/capabilities/default.json'), 'utf8');
const permissions = await readFile(resolve(root, 'src-tauri/permissions/default.toml'), 'utf8');
const cargo = await readFile(resolve(root, 'src-tauri/Cargo.toml'), 'utf8');

test('P6-B UI has a real three-pane workbench and explicit empty state', () => {
  for (const token of ['sidebar', 'conversation-canvas', 'knowledge-canvas', 'Inspector', '从受控交换包开始']) assert.ok(source.includes(token));
  assert.ok(!source.includes('P6-A 交换夹具</h1>'));
});

test('P6-D keyboard, focus and compact contracts remain code-owned', () => {
  for (const token of ["event.key.toLowerCase() === 'o'", "event.key.toLowerCase() === 'e'", "event.key === '\\\\'", "event.key === 'Escape'"]) assert.ok(source.includes(token));
  for (const token of ['@media(max-width:900px)', '.sidebar.mobile-open', 'prefers-reduced-motion', '#fff', 'focus-visible', '.scale-2']) assert.ok(css.includes(token));
});

test('P6-B keeps the picker and command surface least-privileged', () => {
  for (const token of ['stage_preflight_selected_exchange', 'import_staged_exchange_as_new_workspace', 'export_desktop_workspace_to_selected_path']) assert.ok(permissions.includes(token));
  for (const token of ['allow-stage-preflight-selected-exchange', 'allow-import-staged-exchange-as-new-workspace', 'allow-import-desktop-workspace-exchange-v2-selected', 'allow-export-desktop-workspace-to-selected-path']) assert.ok(capability.includes(token));
  assert.ok(!capability.includes('fs:'));
  assert.ok(!cargo.includes('tauri-plugin-shell'));
  assert.ok(!cargo.includes('tauri-plugin-http'));
});

test('P6-C keeps editing, durable history, recycle bin and safe metadata on typed local commands', () => {
  for (const token of ['mutate_desktop_domain', 'undo_desktop_domain', 'redo_desktop_domain', 'read_desktop_workbench_history', 'upsert_desktop_model_metadata', 'save-project', 'save-knowledge', 'save-memory', 'save-relation', 'select-workspace', '关于南枫 AI Desktop', '冲突不会覆盖', '不是实时价格或真实费用']) assert.ok(source.includes(token));
  for (const token of ['allow-mutate-desktop-domain', 'allow-desktop-domain-history', 'allow-upsert-desktop-model-metadata']) assert.ok(capability.includes(token));
  assert.ok(!source.includes('innerHTML = block.text'));
  assert.ok(css.includes('.dialog textarea'));
});

test('P10-A exposes separate local, provider and encrypted-sync status without a network plugin', () => {
  for (const token of ['LOCAL_OFFLINE / LOCAL_ONLY', 'ENCRYPTED_SYNC', 'read_dual_path_status']) assert.ok(source.includes(token));
  for (const token of ['data-action="show-settings"', '本地可用', '联网未配置', '加密同步未配置']) assert.ok(chatShell.includes(token));
  assert.ok(capability.includes('allow-read-dual-path-status'));
  assert.ok(permissions.includes('allow-read-dual-path-status'));
  assert.ok(!cargo.includes('tauri-plugin-http'));
});
