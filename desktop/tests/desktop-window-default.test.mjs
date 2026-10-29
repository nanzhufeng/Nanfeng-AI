import test from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { resolve } from 'node:path';

const [configRaw, windowsConfigRaw, acceptanceConfigRaw, nativeSource, windowStateSource] = await Promise.all([
  readFile(resolve(import.meta.dirname, '../src-tauri/tauri.conf.json'), 'utf8'),
  readFile(resolve(import.meta.dirname, '../src-tauri/tauri.windows.conf.json'), 'utf8'),
  readFile(resolve(import.meta.dirname, '../src-tauri/tauri.windows-acceptance.conf.json'), 'utf8'),
  readFile(resolve(import.meta.dirname, '../src-tauri/src/lib.rs'), 'utf8'),
  readFile(resolve(import.meta.dirname, '../src-tauri/src/desktop_window_state_v1.rs'), 'utf8'),
]);
const config = JSON.parse(configRaw);
const windowsConfig = JSON.parse(windowsConfigRaw);
const acceptanceConfig = JSON.parse(acceptanceConfigRaw);

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
  for (const token of ['MoveFileExW', 'MOVEFILE_REPLACE_EXISTING', 'MOVEFILE_WRITE_THROUGH', 'tokio::time::sleep(SAVE_INTERVAL)', 'state.revision != revision']) assert.ok(windowStateSource.includes(token), token);
  assert.match(windowStateSource, /DEFAULT_WIDTH: u32 = 1440/);
  assert.match(windowStateSource, /DEFAULT_HEIGHT: u32 = 900/);
});

test('Mac base configuration stays strict while each Windows identity supplies its local IPC and style overlay', () => {
  assert.equal(config.version, '1.0.22');
  assert.match(config.app.security.csp, /connect-src 'self';/);
  assert.match(config.app.security.csp, /style-src 'self';/);
  assert.doesNotMatch(config.app.security.csp, /ipc\.localhost|unsafe-inline/);
  assert.match(windowsConfig.version, /^\d+\.\d+\.\d+$/);
  assert.doesNotMatch(windowsConfig.version, /-/);
  assert.equal(windowsConfig.productName, '南枫 AI');
  assert.equal(windowsConfig.app.windows.find(window => window.label === 'main')?.title, '南枫 AI');
  assert.equal(acceptanceConfig.version, '0.6.0-p6d-dev');
  for (const overlay of [windowsConfig, acceptanceConfig]) {
    const csp = overlay.app.security.csp;
    assert.match(csp, /connect-src 'self' ipc: http:\/\/ipc\.localhost/);
    assert.match(csp, /style-src 'self' 'unsafe-inline'/);
    assert.match(csp, /script-src 'self'/);
    assert.doesNotMatch(csp, /script-src[^;]*https?:/);
    assert.doesNotMatch(csp, /connect-src[^;]*\*/);
  }
});
