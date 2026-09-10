import test from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { resolve } from 'node:path';

const source = await readFile(resolve(import.meta.dirname, '../src/app.mjs'), 'utf8');
const rustSource = await readFile(resolve(import.meta.dirname, '../src-tauri/src/lib.rs'), 'utf8');

test('UI/schema diagnostic boot keeps page reads but suppresses automatic writes and external bridges', () => {
  const boot = source.slice(source.indexOf('async function bootDesktopShell'), source.indexOf('void bootDesktopShell();'));
  assert.match(source, /state\.runtimeInfo\?\.automaticWorkSuppressed/);
  assert.match(rustSource, /startup_mode == DesktopStartupMode::UiSchemaDiagnostic \{\s*builder\s*\} else \{\s*builder\.plugin\(tauri_plugin_notification::init\(\)\)/s);
  assert.match(source, /if \(Number\(projection\.revision \|\| 0\) === 0 && !state\.runtimeInfo\?\.automaticWorkSuppressed\)/);
  assert.match(boot, /if \(!state\.runtimeInfo\?\.automaticWorkSuppressed\) \{[\s\S]*readReminderNotificationPermission\(\)[\s\S]*installReminderNotificationActionListener\(\)[\s\S]*flushReminderNotifications\(\)/);
  assert.match(boot, /if \(state\.productSettings\.historyLibraryEnabled && !state\.runtimeInfo\?\.automaticWorkSuppressed\)/);
});
