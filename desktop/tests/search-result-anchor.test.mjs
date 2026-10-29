import assert from 'node:assert/strict';
import test from 'node:test';
import { readFile } from 'node:fs/promises';
import { resolveSearchResultAnchor } from '../src/search-result-anchor.mjs';

function messageWith(attachmentId, attachment) {
  return {
    querySelector(selector) {
      return selector.includes(`data-attachment-id="${attachmentId}"`) ? attachment : null;
    },
  };
}

test('attachment search resolves the exact attachment within its indexed message', () => {
  const attachment = { id: 'image-b' };
  const message = messageWith('image-b', attachment);
  const root = { querySelector: selector => selector.includes('message-a') ? message : null };
  const resolved = resolveSearchResultAnchor(root, { messageId: 'message-a', attachmentId: 'image-b' });
  assert.equal(resolved.message, message);
  assert.equal(resolved.attachment, attachment);
  assert.equal(resolved.target, attachment);
});

test('text and stale attachment hits fall back to the exact message instead of another attachment', () => {
  const attachment = { id: 'image-b' };
  const message = messageWith('image-b', attachment);
  const root = { querySelector: selector => selector.includes('message-a') ? message : null };
  assert.equal(resolveSearchResultAnchor(root, { messageId: 'message-a' }).target, message);
  assert.equal(resolveSearchResultAnchor(root, { messageId: 'message-a', attachmentId: 'gone' }).target, message);
  assert.equal(resolveSearchResultAnchor(root, { messageId: 'missing', attachmentId: 'image-b' }).target, null);
});

test('search drill-down resolves inside the selected conversation when another surface reuses the message ID', () => {
  const decoy = messageWith('asset-a', { id: 'wrong-area' });
  const actualAttachment = { id: 'right-area' };
  const actual = messageWith('asset-a', actualAttachment);
  const transcript = { querySelector: () => actual };
  const root = { querySelector: selector => selector.includes('data-conversation-id="conversation-b"') ? transcript : decoy };
  const resolved = resolveSearchResultAnchor(root, { conversationId: 'conversation-b', messageId: 'message-a', attachmentId: 'asset-a' });
  assert.equal(resolved.message, actual);
  assert.equal(resolved.target, actualAttachment);
  assert.equal(resolveSearchResultAnchor(root, { conversationId: 'missing', messageId: 'message-a' }).target, null);
});

test('search drill-down selects a matching gallery image then scrolls and flashes the exact resolved target', async () => {
  const source = await readFile(new URL('../src/app.mjs', import.meta.url), 'utf8');
  const owner = source.slice(source.indexOf('async function locateSearchHit('), source.indexOf('async function openSearchAttachment('));
  assert.match(owner, /state\.assistantImageSelections\.set\(hit\.messageId, hit\.attachmentId\)/);
  assert.match(owner, /resolveSearchResultAnchor\(document, hit\)/);
  assert.match(owner, /target\?\.scrollIntoView\(\{ block: 'center', inline: 'nearest' \}\)/);
  assert.match(owner, /target\.classList\.add\('search-anchor-flash'\)/);
  assert.doesNotMatch(owner, /const selector = hit\.messageId/);
});
