export const searchServiceId = 'ZHIPU_WEB_SEARCH';
export const searchEngineId = 'search_pro';
export const searchServiceDisclosure = '智谱网页搜索 · 高级版将接收本次所有附件的公开核验要点；原选模型生成回答。搜索可能产生费用，完成查询不等于事实已核实。';

export function usesIndependentSearch(settings, attachments, enabled) {
  return false; // Read old receipts, but never select the retired global override for a new send.
}

export async function createSearchServiceGrant(text, attachmentIds) {
  const bytes = new TextEncoder().encode(JSON.stringify([String(text || '').trim(), [...attachmentIds].sort()]));
  const hash = await crypto.subtle.digest('SHA-256', bytes);
  return { disclosureVersion: 'normal-chat-egress-v3', serviceId: searchServiceId, engineId: searchEngineId,
    materialFingerprint: [...new Uint8Array(hash)].map(byte => byte.toString(16).padStart(2, '0')).join('') };
}

export function retrySearchMaterial(messages, attemptId) {
  let user = messages.find(message => message.attemptId === attemptId); const seen = new Set();
  while (user && String(user.role).toLowerCase() !== 'user' && !seen.has(user.id)) { seen.add(user.id); user = messages.find(message => message.id === user.parentId); }
  if (!user || String(user.role).toLowerCase() !== 'user') return null;
  return { text: (user.blocks || []).filter(block => ['TEXT', 'MARKDOWN', 'CODE'].includes(block.kind)).map(block => block.text || '').join('\n'),
    attachments: (user.blocks || []).filter(block => block.kind === 'ASSET_REF').map(block => block.asset).filter(Boolean) };
}
