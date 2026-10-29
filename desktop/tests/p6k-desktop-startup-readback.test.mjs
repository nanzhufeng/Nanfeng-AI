import test from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { resolve } from 'node:path';

const source = await readFile(resolve(import.meta.dirname, '../src/app.mjs'), 'utf8');

test('P6-K Desktop boot refreshes the shell and renders a data-safe startup failure state', () => {
  const boot = source.slice(source.indexOf('async function bootDesktopShell'), source.indexOf('void bootDesktopShell();') + 'void bootDesktopShell();'.length);
  assert.match(boot, /startupStage = 'DRAFT_CLOSE_GUARD';\s*await installComposerDraftCloseGuard\(\);\s*startupStage = 'WORKSPACE_READBACK';\s*await refresh\(\);/s);
  assert.match(boot, /if \(native && !state\.current && !state\.runtimeInfo\?\.automaticWorkSuppressed\) \{\s*startupStage = 'LOCAL_WORKSPACE_INITIALIZATION';\s*state\.current = await invoke\('ensure_desktop_local_workspace'\)/s);
  assert.match(boot, /state\.error = `本地工作区启动读取未完成（\$\{startupStage\}）；请重新打开应用后再试。`;/);
  // Normal startup now initializes a genuinely empty local container. Later read
  // failure must not falsely claim that no local data was committed.
  assert.match(boot, /state\.status = `启动未完成（\$\{startupStage\}）；已提交的本机数据保留。`;\s*render\(\);/s);
  assert.match(boot, /startupStage = 'REMINDER_NOTIFICATION_LISTENER';\s*await installReminderNotificationActionListener\(\);/s);
  assert.match(boot, /void bootDesktopShell\(\);/);
  const startupFailureStart = boot.indexOf("startupUiStage = 'failed';");
  assert.ok(startupFailureStart >= 0);
  const startupFailure = boot.slice(startupFailureStart);
  assert.doesNotMatch(startupFailure, /String\(error\)/);
});
