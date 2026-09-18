const reasons = {
  CLOUD_PRESENTATION_FAILED: '云端置顶状态未读入，已保留原状态',
  LEGACY_MIGRATION_PENDING: '旧记录格式更新尚未确认，请再次读取核对',
  LEGACY_DEVICE_REQUIRED: '旧加密记录需要在原设备更新后同步一次',
  RECOVERY_VERIFICATION_FAILED: '加密校验未通过，请核对原设备恢复保护',
  TITLE_CONFLICT: '标题存在冲突，已保留本机标题',
  DOCUMENT_RESTORE_FAILED: '对话恢复未完成，已保留本机内容',
};
const count = value => Math.max(0, Math.floor(Number(value) || 0));
export function cloudReadFeedback(receipt = {}) {
  const restored = Array.isArray(receipt.restored) ? receipt.restored : [];
  const failed = count(receipt.failedCount);
  const legacy = count(receipt.skippedLegacyCount);
  const added = restored.filter(row => row.status === 'RESTORED_AS_NEW_WORKSPACE').length;
  const updated = restored.filter(row => row.status === 'UPDATED_LOCAL').length;
  const detail = Object.entries(receipt.failureReasons || {}).filter(([, n]) => count(n) > 0)
    .map(([code, n]) => `${count(n)} 个${reasons[code] || reasons.DOCUMENT_RESTORE_FAILED}`).join('；');
  const kind = failed && !restored.length ? 'error' : failed || legacy ? 'attention' : 'success';
  let message = restored.length ? `已核对 ${restored.length} 个云端会话，新增 ${added} 个，更新 ${updated} 个。` : failed || legacy ? '暂无可读入的云端会话。' : '当前账号没有云端会话。';
  if (failed) message += `${failed} 个未能恢复，本机内容已保留。${detail || '请稍后重试或核对同步状态。'}`;
  if (legacy) message += `另有 ${legacy} 个旧格式记录，请在原设备明确同步后再读取。`;
  return { kind, message };
}
