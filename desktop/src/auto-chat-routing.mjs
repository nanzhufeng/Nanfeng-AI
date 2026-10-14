import { ATTACHMENT_DOCUMENT_MIME_TYPES } from "./attachment-document-types.mjs";
import { relayCoreNeedsCompanion } from "./relaycore-models.mjs";
export const AUTO_PLANS = Object.freeze({"DEFAULT_TEXT": ["RELAYCORE_GPT_6_1_SOL", "RELAYCORE_GEMINI_3_8_FLASH", "DEEPSEEK_V4_FLASH", "RELAYCORE_SONNET_5_5", "QWEN_3_7_PLUS", "GLM_5_3_FLASH", "QWEN_3_6_FLASH", "RELAYCORE_GEMINI_3_1_PRO", "DEEPSEEK_V4_PRO", "GLM_5_3", "QWEN_3_8_MAX", "RELAYCORE_OPUS_5_5", "RELAYCORE_GPT_6_ASTRA", "CLAUDE_SONNET_5_5", "GPT_6_LUNA", "CLAUDE_HAIKU_5_5", "GPT_6_1_SOL", "CLAUDE_OPUS_5_5", "GEMINI_3_8_FLASH"], "COMPLEX_REASONING": ["RELAYCORE_GPT_6_ASTRA", "RELAYCORE_OPUS_5_5", "RELAYCORE_GPT_6_1_SOL", "DEEPSEEK_V4_PRO", "QWEN_3_8_MAX", "GLM_5_3", "RELAYCORE_GEMINI_3_1_PRO", "RELAYCORE_SONNET_5_5", "RELAYCORE_GEMINI_3_8_FLASH", "DEEPSEEK_V4_FLASH", "QWEN_3_7_PLUS", "GLM_5_3_FLASH", "QWEN_3_6_FLASH", "CLAUDE_OPUS_5_5", "GPT_6_1_SOL", "CLAUDE_SONNET_5_5", "GPT_6_LUNA", "CLAUDE_HAIKU_5_5", "GEMINI_3_8_FLASH"], "ATTACHMENT": ["QWEN_3_7_PLUS", "DEEPSEEK_V4_FLASH", "QWEN_3_6_FLASH", "QWEN_3_8_MAX", "GLM_5_3_FLASH", "DEEPSEEK_V4_PRO", "GLM_5_3", "GEMINI_3_8_FLASH", "GPT_6_LUNA", "CLAUDE_SONNET_5_5", "GPT_6_1_SOL", "CLAUDE_OPUS_5_5", "CLAUDE_HAIKU_5_5"]});
export function autoTask(text = '', mimeTypes = []) {
  return mimeTypes.length ? 'ATTACHMENT' : text.length >= 2400 || /深入分析|深度分析|系统分析|完整方案|方案设计|架构设计|重构|代码审计|根因|权衡|情景分析|风险传导|投资分析|估值分析|长期规划|多步骤推理|复杂推理|调试|复杂项目|报错|\b(?:debug|bug|stacktrace|exception|compile|architecture|root[ -]?cause|audit|trade[ -]?offs?|migration|refactor|deep analysis|multi[ -]?step reasoning)\b/i.test(text) ? 'COMPLEX_REASONING' : 'DEFAULT_TEXT';
}
export function autoCandidate(settings = [], {text = '', mimeTypes = [], webSearchEnabled = false} = {}) {
  const enabled = settings.filter(service => service.enabled && (service.credentialStored === true || service.credentialStatus === 'STORED'));
  const presets = enabled.flatMap(service => (service.presets || []).filter(p => p.chatSelectable !== false).map(p => ({...p, providerId: service.providerId, modelId: p.id})));
  const compatible = p => mimeTypes.every(m => (!webSearchEnabled || p.providerId === 'OPENROUTER' || !/^(image|audio|video)\//.test(m)) && (ATTACHMENT_DOCUMENT_MIME_TYPES.includes(m) || m === "application/zip" || ['image/jpeg','image/png','image/webp','image/gif'].includes(m) && (['OPENROUTER','QWEN'].includes(p.providerId) || p.providerId === 'DEEPSEEK' && p.id === 'DEEPSEEK_V4_FLASH') || m === 'application/pdf' && p.providerId === 'OPENROUTER'));
  return AUTO_PLANS[autoTask(text,mimeTypes)].map(id => presets.find(p => p.id === id)).find(p => p && compatible(p) && (!relayCoreNeedsCompanion(p.id,webSearchEnabled,mimeTypes.length>0) || enabled.some(service => service.providerId === 'OPENROUTER'))) || null;
}

export function conversationAutoMimeTypes(conversation, attachments = []) {
  const mimes = attachments.map(a => a.mimeType || 'application/octet-stream');
  const byId = new Map((conversation?.messages || []).map(m => [m.id,m]));
  let id = conversation?.currentLeafId; const visited = new Set();
  while (id && !visited.has(id)) {
    visited.add(id); const message = byId.get(id); if (!message) break;
    for (const block of message.blocks || []) if (block.kind === 'ASSET_REF') mimes.push(block.asset?.mimeType || 'application/octet-stream');
    id = message.parentId;
  }
  return mimes;
}
