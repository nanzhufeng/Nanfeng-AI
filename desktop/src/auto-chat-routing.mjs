import { attachmentReadingRecipients } from './attachment-input-policy.mjs';
import { ATTACHMENT_DOCUMENT_MIME_TYPES } from "./attachment-document-types.mjs";
import { relayCoreNeedsCompanion } from "./relaycore-models.mjs";
export const AUTO_PLANS = Object.freeze({"DEFAULT_TEXT": ["RELAYCORE_GPT_6_1_SOL", "RELAYCORE_GEMINI_3_8_FLASH", "DEEPSEEK_V4_FLASH", "RELAYCORE_SONNET_5_5", "QWEN_3_7_PLUS", "GLM_5_3_FLASH", "QWEN_3_6_FLASH", "RELAYCORE_GEMINI_3_1_PRO", "DEEPSEEK_V4_PRO", "GLM_5_3", "QWEN_3_8_MAX", "RELAYCORE_OPUS_5_5", "RELAYCORE_GPT_6_ASTRA", "CLAUDE_SONNET_5_5", "GPT_6_LUNA", "CLAUDE_HAIKU_5_5", "GPT_6_1_SOL", "CLAUDE_OPUS_5_5", "GEMINI_3_8_FLASH"], "COMPLEX_REASONING": ["RELAYCORE_GPT_6_ASTRA", "RELAYCORE_OPUS_5_5", "RELAYCORE_GPT_6_1_SOL", "DEEPSEEK_V4_PRO", "QWEN_3_8_MAX", "GLM_5_3", "RELAYCORE_GEMINI_3_1_PRO", "RELAYCORE_SONNET_5_5", "RELAYCORE_GEMINI_3_8_FLASH", "DEEPSEEK_V4_FLASH", "QWEN_3_7_PLUS", "GLM_5_3_FLASH", "QWEN_3_6_FLASH", "CLAUDE_OPUS_5_5", "GPT_6_1_SOL", "CLAUDE_SONNET_5_5", "GPT_6_LUNA", "CLAUDE_HAIKU_5_5", "GEMINI_3_8_FLASH"], "ATTACHMENT": ["QWEN_3_7_PLUS", "DEEPSEEK_V4_FLASH", "QWEN_3_6_FLASH", "QWEN_3_8_MAX", "GLM_5_3_FLASH", "DEEPSEEK_V4_PRO", "GLM_5_3", "GEMINI_3_8_FLASH", "GPT_6_LUNA", "CLAUDE_SONNET_5_5", "GPT_6_1_SOL", "CLAUDE_OPUS_5_5", "CLAUDE_HAIKU_5_5"]});
// Match the existing JS submission trim followed by Rust str::trim at the route boundary.
// This is a prediction-only view; never rewrite the Composer draft or saved messages.
export function composerAutoText(text = '') {
  return text.trim().replace(/^\p{White_Space}+|\p{White_Space}+$/gu, '');
}
export function autoTask(text = '', mimeTypes = []) {
  return mimeTypes.length ? 'ATTACHMENT' : text.length >= 2400 || /深入分析|深度分析|系统分析|完整方案|方案设计|架构设计|重构|代码审计|根因|权衡|情景分析|风险传导|投资分析|估值分析|长期规划|多步骤推理|复杂推理|调试|复杂项目|报错|\b(?:debug|bug|stacktrace|exception|compile|architecture|root[ -]?cause|audit|trade[ -]?offs?|migration|refactor|deep analysis|multi[ -]?step reasoning)\b/i.test(text) ? 'COMPLEX_REASONING' : 'DEFAULT_TEXT';
}
export function autoCandidate(settings = [], {text = '', mimeTypes = [], webSearchEnabled = false, openrouterRegistryStatus = null, accepts = () => true} = {}) {
  const enabled = settings.filter(service => service.enabled && (service.credentialStored === true || service.credentialStatus === 'STORED'));
  const presets = enabled.flatMap(service => (service.presets || []).filter(p => p.chatSelectable !== false).map(p => ({...p, providerId: service.providerId, modelId: p.id})));
  const verifiedInputs = ['VERIFIED','STABLE_FALLBACK'].includes(openrouterRegistryStatus?.status)
    ? openrouterRegistryStatus?.inputModalitiesByPreset : null;
  const acceptsVerifiedImage = p => verifiedInputs == null || (Array.isArray(verifiedInputs[p.id]) && verifiedInputs[p.id].includes('image'));
  const hasReaders = p => {
    const declared = Array.isArray(p.nativeInputTypes) ? p.nativeInputTypes
      : p.providerId === 'OPENROUTER' && p.id !== 'MERCURY_3' ? ['image','file']
      : p.providerId === 'QWEN' && ['QWEN_3_7_PLUS','QWEN_3_6_FLASH'].includes(p.id) || p.providerId === 'DEEPSEEK' && p.id === 'DEEPSEEK_V4_FLASH' ? ['image'] : [];
    const nativeInputTypes=declared.filter(kind=>kind!=='image' || p.providerId!=='OPENROUTER' || acceptsVerifiedImage(p));
    return attachmentReadingRecipients([{providerId:p.providerId,presets:[{...p,nativeInputTypes}]}],p.id,mimeTypes.map(mimeType=>({mimeType})),webSearchEnabled);
  };
  const compatible = p => mimeTypes.every(m => ATTACHMENT_DOCUMENT_MIME_TYPES.includes(m) || m === 'application/zip' || ['image/jpeg','image/png','image/webp','image/gif','application/pdf','audio/mpeg','audio/mp3','audio/wav','audio/x-wav','audio/mp4','audio/aac','audio/ogg','audio/flac','audio/webm','video/mp4','video/mpeg','video/quicktime','video/mov','video/webm'].includes(m))
    && hasReaders(p).every(reader=>enabled.some(s=>s.providerId===reader.providerId && s.presets?.some(r=>r.id===reader.presetId && accepts({...r,providerId:s.providerId}))));

  return AUTO_PLANS[autoTask(text,mimeTypes)].map(id=>presets.find(p=>p.id===id)).filter(Boolean)
    .sort((a,b)=>hasReaders(a).length-hasReaders(b).length)
    .find(p=>compatible(p) && accepts(p) && (!relayCoreNeedsCompanion(p.id,webSearchEnabled,mimeTypes.length>0) || presets.some(luna=>luna.providerId==='OPENROUTER' && luna.id==='GPT_6_LUNA' && accepts(luna)))) || null;
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
