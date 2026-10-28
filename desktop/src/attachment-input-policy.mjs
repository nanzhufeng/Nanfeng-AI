import { createLunaRetrievalGrant } from './relaycore-models.mjs';
export const ATTACHMENT_READING_VERSION = 'normal-chat-attachment-reading-v2';
const readers = {
  document: {providerId:'OPENROUTER',presetId:'CLAUDE_HAIKU_5_5',modelId:'anthropic/claude-haiku-5.5',label:'Haiku 5.5 · OpenRouter'},
  media: {providerId:'OPENROUTER',presetId:'GEMINI_4_ARGON',modelId:'google/gemini-4-argon',label:'Gemini 4 Argon · OpenRouter'},
};
export function attachmentAnswerModel(settings, presetId) {
  return settings.flatMap(service=>(service.presets||[]).map(p=>({...p,providerId:service.providerId}))).find(p=>p.id===presetId || p.modelId===presetId);
}
export function attachmentReadingRecipients(settings, presetId, attachments=[], web=false) {
  const model=attachmentAnswerModel(settings,presetId);
  if(!model) return [];
  const result=[];
  for(const item of attachments) {
    const mime=item.mimeType||'';
    const kind=mime.startsWith('image/')?'image':mime==='application/pdf'?'file':mime.startsWith('audio/')?'audio':mime.startsWith('video/')?'video':null;
    if(!kind || ((model.nativeInputTypes||[]).includes(kind) && (!web || model.providerId==='OPENROUTER'))) continue;
    const reader=['audio','video'].includes(kind)?readers.media:readers.document;
    if(!result.includes(reader)) result.push(reader);
  }
  return result;
}
export async function createAttachmentReadingGrant(text, ids, settings, presetId, attachments, web=false) {
  const recipients=attachmentReadingRecipients(settings,presetId,attachments,web);
  const model=attachmentAnswerModel(settings,presetId);
  if(!recipients.length || !model) return null;
  const material=await createLunaRetrievalGrant(text,ids);
  return {disclosureVersion:ATTACHMENT_READING_VERSION,materialFingerprint:material.materialFingerprint,answerProviderId:model.providerId,answerModelId:model.modelId,recipients:recipients.map(({label,...recipient})=>recipient)};
}
