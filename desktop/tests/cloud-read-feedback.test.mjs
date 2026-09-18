import test from 'node:test';
import assert from 'node:assert/strict';
import { cloudReadFeedback } from '../src/cloud-read-feedback.mjs';
test('partial restore is attention with a precise safe reason', () => {
  const result = cloudReadFeedback({ restored: Array.from({ length: 7 }, () => ({ status: 'ALREADY_LOCAL' })), failedCount: 2, failureReasons: { LEGACY_DEVICE_REQUIRED: 2 } });
  assert.equal(result.kind, 'attention');
  assert.match(result.message, /已核对 7 个/);
  assert.match(result.message, /2 个旧加密记录需要在原设备更新后同步一次/);
});
test('retired records do not claim zero failures or total failure', () => {
  const result = cloudReadFeedback({ restored: [{ status: 'ALREADY_LOCAL' }], skippedLegacyCount: 11 });
  assert.equal(result.kind, 'attention');
  assert.doesNotMatch(result.message, /0 个未能恢复/);
  assert.match(result.message, /11 个旧格式/);
});
test('empty account and fully rejected account remain distinct', () => {
  assert.equal(cloudReadFeedback({}).kind, 'success');
  assert.equal(cloudReadFeedback({ failedCount: 2 }).kind, 'error');
  assert.doesNotMatch(cloudReadFeedback({ failedCount: 2 }).message, /没有云端会话/);
});
