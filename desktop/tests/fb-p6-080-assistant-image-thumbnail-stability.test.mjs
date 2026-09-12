import assert from 'node:assert/strict';
import test from 'node:test';
import { readFile } from 'node:fs/promises';
import { resolve } from 'node:path';

import { renderChatFirstShell } from '../src/chat-shell.mjs';

const root = resolve(import.meta.dirname, '..');
const [source, css] = await Promise.all([
  readFile(resolve(root, 'src/app.mjs'), 'utf8'),
  readFile(resolve(root, 'src/chat-shell.css'), 'utf8'),
]);

test('FB-P6-080 preloads every assistant image slot and changes selection without rebuilding the chat shell', () => {
  const images = ['five', 'four', 'three'].map(id => ({
    kind: 'ASSET_REF', asset: { id, mimeType: 'image/png', displayName: `${id}.png` },
  }));
  const html = renderChatFirstShell({
    data: { summary: { id: 'images' }, exchange: { conversations: [{ id: 'conversation', messages: [{ id: 'assistant', role: 'assistant', blocks: images }] }] } },
    native: true, pane: 'chat', selectedConversationId: 'conversation', status: '', error: '', connection: {},
  });

  for (const id of ['five', 'four', 'three']) assert.match(html, new RegExp(`data-image-thumbnail="${id}"`));
  assert.equal((html.match(/data-assistant-gallery-main-image/g) || []).length, 3);
  const selectionOwner = source.slice(source.indexOf('select-assistant-gallery-image'), source.indexOf('select-assistant-gallery-image') + 900);
  assert.match(selectionOwner, /setAssistantGallerySelection/);
  assert.doesNotMatch(selectionOwner, /render\(\)/);
  const thumbnailOwner = source.slice(source.indexOf('function scheduleImageThumbnailReads'), source.indexOf('async function openAttachmentPreview'));
  assert.match(thumbnailOwner, /hydrateImageThumbnail/);
  assert.doesNotMatch(thumbnailOwner, /finally\s*\(\)\s*=>\s*\{[^}]*render\(\)/);
  assert.match(css, /\.chat-assistant-image-gallery-main \{ width: fit-content; max-width: 100%; min-width: 0; \}/);
  assert.match(css, /\.chat-assistant-image-gallery-main \.chat-image-attachment img \{[^}]*width: auto;[^}]*height: auto;[^}]*max-height: 260px/);
  assert.doesNotMatch(css, /\.chat-assistant-image-gallery-main \{[^}]*height:/);
});

test('a single assistant image uses the same natural-size gallery surface without a gray fixed-ratio canvas', () => {
  const html = renderChatFirstShell({
    data: { summary: { id: 'single-image' }, exchange: { conversations: [{ id: 'conversation', messages: [{ id: 'assistant', role: 'assistant', blocks: [{ kind: 'ASSET_REF', asset: { id: 'portrait', mimeType: 'image/png', displayName: 'portrait.png' } }] }] }] } },
    native: true, pane: 'chat', selectedConversationId: 'conversation', status: '', error: '', connection: {},
  });
  assert.match(html, /chat-assistant-image-gallery-main-image/);
  assert.doesNotMatch(html, /chat-assistant-image-gallery-rail/);
  assert.match(css, /\.chat-assistant-image-gallery-main \.chat-image-attachment \{[^}]*padding:\s*0;[^}]*border:\s*0;[^}]*background:\s*transparent/);
  assert.match(css, /\.chat-assistant-image-gallery-main \.chat-image-attachment img \{[^}]*aspect-ratio:\s*auto;[^}]*background:\s*transparent/);
});
