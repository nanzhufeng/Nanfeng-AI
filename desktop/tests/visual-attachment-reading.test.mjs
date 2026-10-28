import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {attachmentReadingRecipients,createAttachmentReadingGrant,ATTACHMENT_READING_VERSION} from '../src/attachment-input-policy.mjs';
import {enabledModelCatalog,renderConfiguredChatShell} from './helpers/enabled-model-catalog.mjs';
const manifest=JSON.parse(readFileSync(new URL('../../protocol/model-service/model-service-v1.json',import.meta.url)));
test('every maintained model has native or explicitly disclosed media preparation, with grants bound to primary and material',async()=>{
 for(const row of manifest.models) for(const web of [false,true]) for(const mimeType of ['image/png','application/pdf','audio/wav','video/mp4']) {
  const attachments=[{id:'fixture',mimeType}];
  const recipients=attachmentReadingRecipients(enabledModelCatalog,row.presetId,attachments,web);
  const grant=await createAttachmentReadingGrant('read',['fixture'],enabledModelCatalog,row.presetId,attachments,web);
  if(recipients.length) {
   assert.equal(grant.disclosureVersion,ATTACHMENT_READING_VERSION,row.presetId);
   assert.equal(grant.answerProviderId,row.providerId);assert.equal(grant.answerModelId,row.modelId);
   assert.notEqual(grant.materialFingerprint,(await createAttachmentReadingGrant('changed',['fixture'],enabledModelCatalog,row.presetId,attachments,web)).materialFingerprint);
  } else assert.equal(grant,null);
 }
});
test('composer has no attachment reading or billing helper copy for any model or media type',()=>{
 for(const row of manifest.models) for(const web of [false,true]) for(const mimeType of ['image/png','application/pdf','audio/wav','video/mp4']) {
  const html=renderConfiguredChatShell({native:true,pendingNewConversationModelId:row.presetId,productSettings:{webSearchEnabled:web},composerAttachments:[{id:'attachment',mimeType,displayName:'synthetic'}]});
  assert.ok(!html.includes('chat-composer-reading-notice'),row.presetId);
  assert.ok(!html.includes('点击发送即授权本次附件识读'),row.presetId);
  assert.ok(!html.includes('按实际调用分别计费'),row.presetId);
 }
});
test('text-only model with image and audio names both recipients without replacing final model',async()=>{
 const attachments=[{mimeType:'image/png'},{mimeType:'audio/wav'}];
 const recipients=attachmentReadingRecipients(enabledModelCatalog,'MERCURY_3',attachments,false);
 assert.deepEqual(recipients.map(r=>r.presetId),['CLAUDE_HAIKU_5_5','GEMINI_4_ARGON']);
 const grant=await createAttachmentReadingGrant('read',['a','b'],enabledModelCatalog,'MERCURY_3',attachments);
 assert.equal(grant.answerModelId,'inception/mercury-3');
});
