import { ATTACHMENT_DOCUMENT_MIME_TYPES } from '../src/attachment-document-types.mjs';
import { renderAndroidSettingsShell } from '../src/android-settings-shell.mjs';
import test from 'node:test';
import assert from 'node:assert/strict';
import { attachmentPreviewCapability } from '../src/desktop-attachment-preview-owner.mjs';
test('generic MIME text attachments use safe text preview, not unsupported boundary', () => {
  for (const displayName of ['粘贴的 markdown (1). md', '粘贴的 markdown (1)。 md', 'notes.MD', 'notes.txt', 'data.json', 'data.csv']) {
    assert.equal(attachmentPreviewCapability({displayName, mimeType:'application/octet-stream'}).kind, 'text');
  }
  assert.equal(attachmentPreviewCapability({displayName:'notes.md.exe',mimeType:'application/octet-stream'}).supported, false);
  assert.equal(attachmentPreviewCapability({displayName:'budget.xlsx',mimeType:'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet'}).kind, 'text');
  assert.equal(attachmentPreviewCapability({displayName:'budget.xlsx',mimeType:'application/octet-stream'}).kind, 'text');
  assert.equal(attachmentPreviewCapability({displayName:'slides.pptx',mimeType:'application/vnd.openxmlformats-officedocument.presentationml.presentation'}).kind, 'text');
  assert.equal(attachmentPreviewCapability({displayName:'slides.pptx',mimeType:'application/octet-stream'}).kind, 'text');
});

const sourceTextMimes=['application/xml','text/xml','application/x-yaml','text/yaml','text/html'];
for(const mimeType of sourceTextMimes)test('already-readable source MIME opens the existing reader '+mimeType,()=>{
 for(const mime of [mimeType,mimeType.toUpperCase()+'; charset=UTF-8'])assert.equal(attachmentPreviewCapability({displayName:'合成源文件',mimeType:mime}).kind,'text');
});
test('unknown MIME source extensions do not widen the native decoder fallback',()=>{
 for(const displayName of ['file.xml','file.yaml','file.html','file.html.exe'])assert.equal(attachmentPreviewCapability({displayName,mimeType:'application/octet-stream'}).supported,false);
});

test('reader capability consumes the existing common document MIME scope',()=>{
 for(const mimeType of ATTACHMENT_DOCUMENT_MIME_TYPES)assert.equal(attachmentPreviewCapability({displayName:'合成文档',mimeType}).kind,'text',mimeType);
});
test('source reader is disclosed in feature review without a new permanent action',()=>{
 const html=renderAndroidSettingsShell({page:'feature-review',native:true,runtimeInfo:{platform:'windows'},data:{summary:{id:'synthetic'},exchange:{conversations:[]}}});assert.match(html,/源文本阅读：建议保留/);assert.match(html,/XML、YAML、HTML原文/);assert.match(html,/文件菜单和按键不变/);assert.doesNotMatch(html,/data-action="(?:open|toggle)-source-text/);
});
