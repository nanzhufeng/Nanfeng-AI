import test from 'node:test';
import assert from 'node:assert/strict';
import { renderSearchAttachmentMenu, renderChatAttachmentMenu, attachmentMenuAnchor } from '../src/search-attachment-menu.mjs';
test('anchor comes from the actual attachment, including when no pointer coordinates exist', () => {
  assert.deepEqual(attachmentMenuAnchor({ getBoundingClientRect: () => ({left:800,top:240,height:180}) }), { x:812, y:304 });
});
test('main conversation menu exposes search, save and share with real metadata', () => {
  const html = renderChatAttachmentMenu({displayName:'sample.mp4',details:'视频 · 5:50 · 11.9 MB',sentAt:'发送于 8月24日 21:18'}, {x:800,y:300});
  for (const action of ['chat-attachment-search','chat-attachment-save','chat-attachment-share']) assert.match(html, new RegExp(action));
  assert.match(html, /5:50/);
  assert.doesNotMatch(html, /undefined/);
});
test('attachment menu follows the reference: filename and two actions at the pointer', () => {
  const html = renderSearchAttachmentMenu({ entryId: 'entry1', displayName: '<video>.mp4', mimeType: 'video/mp4', byteCount: 300 }, { x: 415, y: 288 });
  assert.match(html, /--menu-x:415px;--menu-y:288px/);
  assert.match(html, /&lt;video&gt;.mp4/);
  assert.equal((html.match(/<button/g) || []).length, 2);
  assert.match(html, /快速定位/);
  assert.match(html, /ask-delete-search-attachment/);
  assert.doesNotMatch(html, /undefined|附件操作|video\/mp4|打开预览|KiB/);
});
