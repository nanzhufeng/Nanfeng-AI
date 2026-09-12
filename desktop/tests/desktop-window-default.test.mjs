import test from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { resolve } from 'node:path';

const [configRaw, nativeSource, windowStateSource] = await Promise.all([
  readFile(resolve(import.meta.dirname, '../src-tauri/tauri.conf.json'), 'utf8'),
  readFile(resolve(import.meta.dirname, '../src-tauri/src/lib.rs'), 'utf8'),
  readFile(resolve(import.meta.dirname, '../src-tauri/src/desktop_window_state_v1.rs'), 'utf8'),
]);
const config = JSON.parse(configRaw);

test('Desktop uses the documented geometry only as a first-run fallback', () => {
  const main = config.app.windows.find(window => window.label === 'main');
  assert.deepEqual(
    { width: main?.width, height: main?.height, minWidth: main?.minWidth, minHeight: main?.minHeight },
    { width: 1440, height: 900, minWidth: 780, minHeight: 560 },
  );
});

test('Desktop restores a validated local main-window geometry and saves it on native window changes', () => {
  for (const token of ['install_desktop_window_state_persistence', 'desktop_window_state_v1']) assert.ok(nativeSource.includes(token), token);
  for (const token of ['WindowEvent::Moved', 'WindowEvent::Resized', 'WindowEvent::CloseRequested', 'window-state.json', 'restore_geometry', 'persist_geometry', 'is_geometry_visible']) assert.ok(windowStateSource.includes(token), token);
  assert.match(windowStateSource, /DEFAULT_WIDTH: u32 = 1440/);
  assert.match(windowStateSource, /DEFAULT_HEIGHT: u32 = 900/);
});
