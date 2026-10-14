import test from 'node:test';
import assert from 'node:assert/strict';
import { attachmentPreviewCapability } from '../src/desktop-attachment-preview-owner.mjs';
test('generic MIME text attachments use safe text preview, not unsupported boundary', () => {
  for (const displayName of ['粘贴的 markdown (1). md', '粘贴的 markdown (1)。 md', 'notes.MD', 'notes.txt', 'data.json', 'data.csv']) {
    assert.equal(attachmentPreviewCapability({displayName, mimeType:'application/octet-stream'}).kind, 'text');
  }
  assert.equal(attachmentPreviewCapability({displayName:'notes.md.exe',mimeType:'application/octet-stream'}).supported, false);
});

test('shared supported document catalog reaches both preview and Auto routing', async () => {
  const { readFileSync }=await import('node:fs');
  const { ATTACHMENT_DOCUMENT_MIME_TYPES }=await import('../src/attachment-document-types.mjs');
  const { autoCandidate }=await import('../src/auto-chat-routing.mjs');
  const fixture=JSON.parse(readFileSync(new URL('../../protocol/fixtures/attachment-document-types-v1.json',import.meta.url)));
  assert.deepEqual(ATTACHMENT_DOCUMENT_MIME_TYPES,fixture.mimeTypes);
  const services=[{providerId:'QWEN',enabled:true,credentialStored:true,presets:[{id:'QWEN_3_7_PLUS'}]}];
  for (const mimeType of fixture.mimeTypes) {
    assert.equal(attachmentPreviewCapability({mimeType}).kind,'text',mimeType);
    assert.equal(autoCandidate(services,{mimeTypes:[mimeType]}).id,'QWEN_3_7_PLUS',mimeType);
  }
});
