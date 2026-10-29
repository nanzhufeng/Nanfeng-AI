import assert from 'node:assert/strict';
import test from 'node:test';
import { replaceInstallerProcessMacro, makeSilentFailuresNoninteractive } from '../scripts/alignment-installer-policy.mjs';

const upstream = 'before\n!macro CheckIfAppIsRunning executableName productName\n  nsis_tauri_utils::FindProcessCurrentUser "${executableName}"\n  nsis_tauri_utils::KillProcessCurrentUser "${executableName}"\n!macroend\nafter\n';

test('fresh installer and uninstaller share a path-specific read-only process gate', () => {
  const result = replaceInstallerProcessMacro(upstream, 'C:\\owned 空格\\Check-InstalledProcess.ps1');
  assert.ok(result.startsWith('before\n') && result.endsWith('\nafter\n'));
  assert.doesNotMatch(result, /KillProcess|FindProcess/);
  assert.ok(result.includes('-ExecutablePath "$INSTDIR\\${executableName}"'));
  assert.ok(result.includes('IfSilent nanfeng_guard_abort_'));
  assert.ok(result.includes('SetErrorLevel 2'));
  assert.ok(result.includes('-WindowStyle Hidden'));
});

test('unknown vendor macros and remaining global kill paths are rejected', () => {
  assert.throws(() => replaceInstallerProcessMacro('unknown', 'C:\\check.ps1'));
  assert.throws(() => replaceInstallerProcessMacro(upstream.replace('KillProcessCurrentUser', 'ChangedUpstream'), 'C:\\check.ps1'));
  assert.throws(() => replaceInstallerProcessMacro(upstream + 'nsis_tauri_utils::KillProcess "other.exe"', 'C:\\check.ps1'));
  assert.throws(() => replaceInstallerProcessMacro(upstream, 'C:\\bad\nname.ps1'));
});

test('known installer errors have silent defaults while unknown dialogs require review', () => {
  const source = 'MessageBox MB_ICONEXCLAMATION "$(unableToUninstall)"\nMessageBox MB_ICONEXCLAMATION|MB_ABORTRETRYIGNORE "$(webview2InstallError)" IDIGNORE ignore IDRETRY update_webview';
  const result = makeSilentFailuresNoninteractive(source);
  assert.ok(result.includes('"$(unableToUninstall)" /SD IDOK'));
  assert.ok(result.includes('"$(webview2InstallError)" /SD IDABORT IDIGNORE'));
  assert.throws(() => makeSilentFailuresNoninteractive(source + '\nMessageBox MB_OK "unexpected"'));
  assert.throws(() => makeSilentFailuresNoninteractive('changed upstream template'));
});
