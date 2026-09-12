import test from 'node:test';
import assert from 'node:assert/strict';
import { attachmentPreviewCapability } from '../src/desktop-attachment-preview-owner.mjs';
test('generic MIME text attachments use safe text preview, not unsupported boundary', () => {
  for (const displayName of ['粘贴的 markdown (1). md', '粘贴的 markdown (1)。 md', 'notes.MD', 'notes.txt', 'data.json', 'data.csv']) {
    assert.equal(attachmentPreviewCapability({displayName, mimeType:'application/octet-stream'}).kind, 'text');
  }
  assert.equal(attachmentPreviewCapability({displayName:'notes.md.exe',mimeType:'application/octet-stream'}).supported, false);
});
