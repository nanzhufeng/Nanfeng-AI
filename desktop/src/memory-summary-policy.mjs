// Keep the suggestion gate aligned with Android MemorySummaryPolicy. Saving
// still requires a separate, explicit user confirmation.
const command = /^\s*(?:请)?记住(?:了|一下|这[件些]?事)?(?:[：:，,\s]|$)/i;
const sensitive = /(?:密码|api[ _-]?key|authorization|恢复码|银行卡|passcode|bearer\s+)/i;
const durableSignals = /(?:我叫|我的名字|职业|工作|长期|偏好|习惯|项目|南枫|投资|关注|住在|计划|目标|以后|记忆)/g;

export function memorySummarySuggestion(userText, assistantText) {
  const user = String(userText || '');
  if (command.test(user) || sensitive.test(user)) return null;
  if ([...user.matchAll(durableSignals)].length < 2 || String(assistantText || '').length < 80) return null;
  const title = user.includes('南枫') || user.includes('项目') ? '个人项目'
    : user.includes('偏好') || user.includes('设计') ? '开发与设计偏好'
      : user.includes('投资') || user.includes('关注') ? '长期研究方向' : '概览';
  return { title, body: user.replace(/\s+/g, ' ').trim().slice(0, 280) };
}
