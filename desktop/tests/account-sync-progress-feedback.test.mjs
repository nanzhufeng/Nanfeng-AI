import assert from 'node:assert/strict';
import fs from 'node:fs';
import test from 'node:test';

const app = fs.readFileSync(new URL('../src/app.mjs', import.meta.url), 'utf8');
const css = fs.readFileSync(new URL('../src/chat-shell.css', import.meta.url), 'utf8');

test('manual sync and cloud read paint progress then retain a clear quantified result', () => {
  const sync = app.slice(app.indexOf("action === 'context-menu-sync'"), app.indexOf("action === 'show-google-login-requirements'"));
  const read = app.slice(app.indexOf("action === 'load-account-cloud-documents'"), app.indexOf("action === 'choose-selected-local-sync-start'"));
  assert.match(app, /accountSyncProgress: null/);
  assert.match(app, /function accountSyncProgressDialog\(\)/);
  assert.match(app, /const ACCOUNT_SYNC_PROGRESS_MIN_VISIBLE_MS = 520/);
  assert.match(app, /function beginAccountSyncProgress\(operation\)/);
  assert.match(app, /function waitForAccountSyncProgressPaint\(\) \{\n  return new Promise\(resolve => window\.requestAnimationFrame\(\(\) => window\.requestAnimationFrame\(resolve\)\)\);/);
  assert.match(sync, /beginAccountSyncProgress\('sync'\);\n        await waitForAccountSyncProgressPaint\(\);/);
  assert.match(read, /beginAccountSyncProgress\('read'\);[\s\S]*await waitForAccountSyncProgressPaint\(\);/);
  assert.match(app, /async function completeAccountSyncProgress/);
  assert.match(app, /ACCOUNT_SYNC_PROGRESS_MIN_VISIBLE_MS - \(performance\.now\(\) - startedAt\)/);
  assert.match(app, /const ACCOUNT_SYNC_RESULT_VISIBLE_MS = 2600/);
  assert.match(app, /result: \{ message, kind \}/);
  assert.doesNotMatch(app, /if \(\['context-menu-sync', 'load-account-cloud-documents'\]\.includes\(action\)\) state\.accountSyncProgress = null;/);
  assert.match(app, /同步成功/);
  assert.match(app, /读取完成/);
  assert.match(sync, /await completeAccountSyncProgress\('', 'success'\);/);
  assert.doesNotMatch(app, /已同步 1 个对话/);
  assert.match(app, /cloudReadFeedback\(receipt\)/);
  assert.match(read, /正在读取云端列表/);
  assert.match(app, /请检查网络或登录后重试。/);
  assert.match(app, /accountSyncProgressDialog\(\) \|\| cameraCaptureDialog/);
  assert.match(css, /\.account-sync-progress-spinner/);
  assert.match(css, /@keyframes account-sync-progress-spin/);
  assert.match(css, /\.account-sync-progress-dialog\.result\.success svg/);
  assert.match(css, /\.account-sync-progress-dialog\.result\.error svg/);
  assert.match(css, /\.account-sync-progress-scrim \{ background: transparent; backdrop-filter: none; -webkit-backdrop-filter: none; \}/);
  assert.doesNotMatch(css, /\.account-sync-progress-scrim \{[^}]*blur\(/);
});
