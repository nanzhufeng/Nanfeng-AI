/**
 * FB-P6-037: small, testable state transitions for the recoverable
 * conversation-recycle confirmation. The domain receipt remains the only
 * success authority; this module never performs a mutation itself.
 */
export function beginConversationRecycle(dialog, targetId) {
  if (dialog?.kind !== 'conversation-delete' || dialog.submitting || dialog.id !== targetId) return null;
  return { ...dialog, submitting: true, failure: null };
}

export function failConversationRecycle(dialog, error) {
  return { ...dialog, submitting: false, failure: `操作未完成：${String(error)}` };
}

export function completeConversationRecycle() {
  return { dialog: null, status: '会话已移入回收站；消息树未物理删除。', focusAction: 'new-chat' };
}
