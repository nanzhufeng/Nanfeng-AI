import test from 'node:test';
import assert from 'node:assert/strict';
import { cloudReadFeedback } from '../src/cloud-read-feedback.mjs';

test('partial cloud restore reports confirmed rows and a safe failure reason', () => {
  const result = cloudReadFeedback({
    restored: [{ status: 'ALREADY_LOCAL' }, { status: 'UPDATED_LOCAL' }],
    failedCount: 1,
    failureReasons: { LEGACY_DEVICE_REQUIRED: 1 },
  });
  assert.equal(result.kind, 'attention');
  assert.match(result.message, /已核对 2 个/);
  assert.match(result.message, /1 个旧加密记录需要在原设备更新后同步一次/);
  assert.doesNotMatch(result.message, /0 个未能恢复/);
});

test('legacy-only, empty-account and all-failed reads have distinct results', () => {
  const legacy = cloudReadFeedback({ restored: [{ status: 'ALREADY_LOCAL' }], skippedLegacyCount: 11 });
  assert.equal(legacy.kind, 'attention');
  assert.match(legacy.message, /11 个旧格式/);
  assert.equal(cloudReadFeedback({}).kind, 'success');
  const failed = cloudReadFeedback({ failedCount: 2 });
  assert.equal(failed.kind, 'error');
  assert.doesNotMatch(failed.message, /没有云端会话/);
});

test('busy local restore tells the user to retry without claiming the cloud is empty', () => {
  const failed = cloudReadFeedback({ failedCount: 1, failureReasons: { LOCAL_STORE_BUSY: 1 } });
  assert.equal(failed.kind, 'error');
  assert.match(failed.message, /本机数据正忙，稍后重试读取/);
  assert.doesNotMatch(failed.message, /当前账号没有云端会话/);
});

test('pending local selected edit stays visible as a retryable cloud read', () => {
  const result = cloudReadFeedback({ failedCount: 1, failureReasons: { LOCAL_SYNC_PENDING: 1 } });
  assert.equal(result.kind, 'error');
  assert.match(result.message, /本机修改尚待续同步，请同步完成后再读取/);
});


test('persisted conversations with failed local projection report attention without claiming restore failure', () => {
  const feedback = cloudReadFeedback({ restored: [{ status: 'RESTORED_AS_NEW_WORKSPACE' }], projectionFailedCount: 1 });
  assert.equal(feedback.kind, 'attention');
  assert.match(feedback.message, /已核对 1 个/);
  assert.match(feedback.message, /1 个已恢复会话未能显示/);
  assert.doesNotMatch(feedback.message, /未能恢复/);
});
