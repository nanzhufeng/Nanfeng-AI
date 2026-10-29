import test from 'node:test';
import assert from 'node:assert/strict';
import { attachmentReadingRecipients, createAttachmentReadingGrant } from '../src/attachment-input-policy.mjs';
const settings=[{providerId:'OPENROUTER',presets:[{id:'MERCURY_3',modelId:'inception/mercury-3',nativeInputTypes:[]},{id:'GEMINI_4_ARGON',modelId:'google/gemini-4-argon',nativeInputTypes:['image','file','audio','video']}]},{providerId:'QWEN',presets:[{id:'QWEN_3_7_PLUS',modelId:'qwen3.7-plus',nativeInputTypes:['image']}]}];
const mixed=['image/png','application/pdf','audio/wav','video/mp4'].map(mimeType=>({mimeType}));
test('native mixed media does not acquire auxiliary recipients',()=>{
  assert.deepEqual(attachmentReadingRecipients(settings,'GEMINI_4_ARGON',mixed,false),[]);
});
test('text-only answer requests exactly the document and media readers once',()=>{
  assert.deepEqual(attachmentReadingRecipients(settings,'MERCURY_3',mixed,false).map(r=>[r.providerId,r.presetId]),[['OPENROUTER','CLAUDE_HAIKU_5_5'],['OPENROUTER','GEMINI_4_ARGON']]);
});
test('Qwen native image and web projection preserve distinct receiver sets',()=>{
  assert.deepEqual(attachmentReadingRecipients(settings,'QWEN_3_7_PLUS',[mixed[0]],false),[]);
  assert.equal(attachmentReadingRecipients(settings,'QWEN_3_7_PLUS',[mixed[0]],true)[0].presetId,'CLAUDE_HAIKU_5_5');
});
test('optional reading grant binds exact material and original answer identity',async()=>{
  const grant=await createAttachmentReadingGrant('synthetic',['a','b'],settings,'MERCURY_3',mixed);
  assert.equal(grant.answerModelId,'inception/mercury-3');
  assert.equal(grant.answerProviderId,'OPENROUTER');
  assert.equal(grant.recipients.length,2);
  assert.notEqual(grant.materialFingerprint,(await createAttachmentReadingGrant('changed',['a','b'],settings,'MERCURY_3',mixed)).materialFingerprint);
  assert.equal(grant.materialFingerprint,(await createAttachmentReadingGrant('synthetic',['b','a'],settings,'MERCURY_3',mixed)).materialFingerprint);
});
