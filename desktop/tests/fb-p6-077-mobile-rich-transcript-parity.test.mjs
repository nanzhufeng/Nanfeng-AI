import test from 'node:test';
import assert from 'node:assert/strict';
import { renderSafeMarkdown } from '../src/safe-markdown.mjs';
import { renderChatFirstShell } from '../src/chat-shell.mjs';

test('FB-P6-077 removes malformed Markdown control debris instead of leaking it into an imported transcript', () => {
  const html = renderSafeMarkdown('**AI 收入 +40%\nGPU 利用率 90%\n结论。**\n\\**仍要加粗');

  assert.match(html, /AI 收入 \+40%/);
  assert.match(html, /GPU 利用率 90%/);
  assert.doesNotMatch(html, /\*\*/);
  assert.doesNotMatch(html, /\\\*/);
});

test('FB-P6-077 projects ChatGPT import markers as mobile-compatible source and automation surfaces', () => {
  const marker = '\uE200cite\uE202turn540057search0\uE202turn540057search18\uE201';
  const legacyMarker = 'navlistturn9news0';
  const automation = 'genui{"suggest_automation":{"label":"持续监控 AI CapEx"}}';
  const inline = renderSafeMarkdown(`结论。${marker}\n${legacyMarker}`);
  const automationHtml = renderSafeMarkdown(automation);

  assert.match(inline, /chat-imported-marker/);
  assert.match(inline, /来源 \+1/);
  assert.match(inline, /相关链接/);
  assert.doesNotMatch(inline, /turn540057search0||\uE200/);
  assert.match(automationHtml, /chat-imported-automation/);
  assert.match(automationHtml, /持续监控 AI CapEx/);
  assert.doesNotMatch(automationHtml, /suggest_automation|genui|/);
});

test('FB-P6-077 gives nested list items a distinct hanging first-line hierarchy', () => {
  const html = renderSafeMarkdown('1. 一级\n   1. 二级\n      1. 三级');

  assert.match(html, /class="chat-markdown-list chat-markdown-ordered"/);
  assert.match(html, /data-depth="0"/);
  assert.match(html, /data-depth="1"/);
  assert.match(html, /data-depth="2"/);
});

test('FB-P6-077 groups assistant multi-image results into the mobile main-preview and thumbnail hierarchy', () => {
  const data = { summary: { id: 'rich-transcript' }, exchange: { conversations: [{
    id: 'rich-conversation', title: '富文本', revision: 1,
    messages: [{ id: 'assistant-images', role: 'assistant', createdAt: '2026-09-11T00:00:00Z', blocks: [
      { kind: 'ASSET_REF', asset: { id: 'image-1', displayName: '第一张.png', mimeType: 'image/png' } },
      { kind: 'ASSET_REF', asset: { id: 'image-2', displayName: '第二张.png', mimeType: 'image/png' } },
      { kind: 'ASSET_REF', asset: { id: 'image-3', displayName: '第三张.png', mimeType: 'image/png' } },
    ] }],
  }] } };
  const html = renderChatFirstShell({ data, native: true, pane: 'chat', selectedConversationId: 'rich-conversation', status: '', error: '', connection: {}, assistantImageSelections: new Map([['assistant-images', 'image-2']]) });

  assert.match(html, /chat-assistant-image-gallery/);
  assert.match(html, /chat-assistant-image-gallery-main/);
  assert.equal((html.match(/chat-assistant-image-gallery-thumb/g) || []).length, 3);
  assert.match(html, /data-action="select-assistant-gallery-image"[^>]*data-message-id="assistant-images"[^>]*data-attachment-id="image-2"[^>]*aria-pressed="true"/);
});
