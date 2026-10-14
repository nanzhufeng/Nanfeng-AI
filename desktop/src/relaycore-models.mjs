// Platform listing snapshot; a listing does not establish successful calls or upstream identity.
export const RELAYCORE_MODELS = Object.freeze([
  {
    "presetId": "RELAYCORE_OPUS_5_5",
    "providerId": "RELAYCORE_CLAUDE",
    "modelId": "claude-opus-5.5",
    "modelName": "Opus 5.5",
    "channel": "Kiro正价",
    "multiplier": "0.35",
    "tier": "DEEP",
    "inputPerMillion": 1.4,
    "outputPerMillion": 7.0,
    "cachedInputPerMillion": 0.07
  },
  {
    "presetId": "RELAYCORE_SONNET_5_5",
    "providerId": "RELAYCORE_CLAUDE",
    "modelId": "claude-sonnet-5-5",
    "modelName": "Sonnet 5.5",
    "channel": "Kiro正价",
    "multiplier": "0.35",
    "tier": "DAILY",
    "inputPerMillion": 0.7,
    "outputPerMillion": 3.5,
    "cachedInputPerMillion": 0.07
  },
  {
    "presetId": "RELAYCORE_GPT_6_1_SOL",
    "providerId": "RELAYCORE_GPT",
    "modelId": "gpt-6.1-sol",
    "modelName": "GPT-6.1 Sol",
    "channel": "Pro3",
    "multiplier": "0.25",
    "tier": "DEEP",
    "inputPerMillion": 0.5,
    "outputPerMillion": 2.5,
    "cachedInputPerMillion": 0.025
  },
  {
    "presetId": "RELAYCORE_GPT_6_ASTRA",
    "providerId": "RELAYCORE_GPT",
    "modelId": "gpt-6-astra",
    "modelName": "GPT-6 Astra",
    "channel": "Pro3",
    "multiplier": "0.25",
    "tier": "DEEP",
    "inputPerMillion": 2.5,
    "outputPerMillion": 12.5,
    "cachedInputPerMillion": 0.25
  },
  {
    "presetId": "RELAYCORE_GEMINI_3_8_FLASH",
    "providerId": "RELAYCORE_GEMINI",
    "modelId": "gemini-3.8-flash",
    "modelName": "Gemini 3.8 Flash",
    "channel": "Gemini",
    "multiplier": "0.28",
    "tier": "DAILY",
    "inputPerMillion": 0.21,
    "outputPerMillion": 1.05,
    "cachedInputPerMillion": 0.021
  },
  {
    "presetId": "RELAYCORE_GEMINI_3_1_PRO",
    "providerId": "RELAYCORE_GEMINI",
    "modelId": "gemini-3.1-pro",
    "modelName": "Gemini 3.1 Pro",
    "channel": "Gemini",
    "multiplier": "0.28",
    "tier": "DEEP",
    "inputPerMillion": 0.56,
    "outputPerMillion": 3.36,
    "cachedInputPerMillion": 0.056
  }
].map(model => Object.freeze(model)));
export const relayCoreModel = presetId => RELAYCORE_MODELS.find(model => model.presetId === presetId);
export const relayCoreCompactName = model => model.channel === "Gemini" && model.modelName.startsWith("Gemini ") ? model.modelName : `${model.modelName} · ${model.channel}`;
export const relayCoreUiModelName = model => model.modelName === 'GPT-6 Astra' ? '6 Astra' : model.modelName;
export const relayCorePickerName = model => `${relayCoreUiModelName(model)} · ${model.multiplier}×`;

export function relayCoreNeedsCompanion(presetId, enabled, hasAttachments = false) {
  const model = relayCoreModel(presetId) || RELAYCORE_MODELS.find(item => item.modelId === presetId || item.modelId.replace('-5-5','-5.5') === presetId || (item.presetId === 'RELAYCORE_OPUS_5_5' && presetId === 'claude-opus-5-5'));
  return Boolean(enabled && (['RELAYCORE_CLAUDE', 'RELAYCORE_GEMINI'].includes(model?.providerId) || hasAttachments && model?.providerId === 'RELAYCORE_GPT'));
}
export function relayCoreEgressVersion(presetId, enabled, hasAttachments = false) {
  return relayCoreNeedsCompanion(presetId, enabled, hasAttachments) ? 'normal-chat-luna-search-v1' : 'normal-chat-egress-v1';
}

export const relayCoreShortModelName = model => ({'GPT-6.1 Sol':'6.1 Sol','GPT-6 Astra':'6 Astra','Gemini 3.8 Flash':'Gemini 3.8','Gemini 3.1 Pro':'Gemini 3.1'}[model.modelName] || model.modelName);
export const relayCoreFooterName = model => `${relayCoreShortModelName(model)} · RC`;

export async function createLunaRetrievalGrant(text, attachmentIds) {
 const bytes=new TextEncoder().encode(JSON.stringify([String(text || '').trim(),[...attachmentIds].sort()]));
 const hash=await crypto.subtle.digest('SHA-256',bytes);
 return {disclosureVersion:'normal-chat-luna-search-v1',providerId:'OPENROUTER',presetId:'GPT_6_LUNA',modelId:'openai/gpt-6-luna',materialFingerprint:[...new Uint8Array(hash)].map(b=>b.toString(16).padStart(2,'0')).join('')};
}
