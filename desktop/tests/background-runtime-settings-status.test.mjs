import test from 'node:test';
import assert from 'node:assert/strict';
import { renderAndroidSettingsShell } from '../src/android-settings-shell.mjs';
const render = backgroundRuntime => renderAndroidSettingsShell({ page: 'reminders', runtimeInfo: { platform: 'windows' }, backgroundRuntime });
const status = html => html.match(/<p class="([^"]*android-settings-background-status[^"]*)" role="status">([^<]*)<\/p>/)?.slice(1) ?? null;
test('failed removal remains visible after the last plan stops needing wakeups', () => {
  const value = status(render({ desiredEnabled: false, installed: true, safeCode: 'REMOVE_FAILED' }));
  assert.ok(value, 'Unconfirmed removal must remain visible');
  assert.ok(value[0].split(' ').includes('error'), 'Removal failure must use the existing error style');
  assert.ok(/未能确认.*撤销/.test(value[1]), 'Message must not claim removal succeeded');
  assert.ok(/仍.*唤醒/.test(value[1]), 'Message must explain the remaining wakeup possibility');
});
test('failed removal does not become a registration success when a plan is still desired', () => {
  const value = status(render({ desiredEnabled: true, installed: true, safeCode: 'REMOVE_FAILED' }));
  assert.ok(value && /未能确认.*撤销/.test(value[1]), 'Recorded removal failure must outrank the cached installed flag');
  assert.ok(!value[1].includes('已注册'), 'Failure cannot be presented as success');
});
test('confirmed active registration keeps its success status', () => {
  const value = status(render({ desiredEnabled: true, installed: true, safeCode: 'INSTALLED' }));
  assert.ok(value?.[1] === '系统后台任务已注册。');
  assert.ok(!value[0].split(' ').includes('error'));
});
test('confirmed absence stays quiet while failed installation remains explicit', () => {
  assert.equal(status(render({ desiredEnabled: false, installed: false, safeCode: 'NOT_REQUIRED' })), null);
  const value = status(render({ desiredEnabled: true, installed: false, safeCode: 'INSTALL_FAILED' }));
  assert.ok(value?.[0].split(' ').includes('error'));
  assert.ok(value?.[1].includes('关闭应用后提醒不会按计划执行'));
});
