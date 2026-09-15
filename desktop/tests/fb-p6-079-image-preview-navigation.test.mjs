import assert from 'node:assert/strict';
import test from 'node:test';

import { readFile } from 'node:fs/promises';
import { resolve } from 'node:path';
import { runInNewContext } from 'node:vm';
import { imagePreviewNavigation, imagePreviewTargetId, relatedImageIds } from '../src/image-preview-navigation.mjs';

test('a trackpad gesture advances once, recovers for the next gesture, and never classifies diagonal motion as zoom', async () => {
  const source = await readFile(resolve(import.meta.dirname, '../src/app.mjs'), 'utf8');
  const start = source.indexOf('function consumeImagePreviewHorizontalSwipe(');
  const end = source.indexOf("app.addEventListener('wheel'", start);
  let now = 1000;
  const advances = [];
  const context = {
    state: { imagePreview: { attachmentId: 'one', zoom: 1 } },
    performance: { now: () => now },
    imagePreviewHorizontalSwipe: { attachmentId: '', lastAt: 0, deltaX: 0, lockUntil: 0, axis: null, consumed: false },
    imagePreviewHorizontalSwipeThreshold: 36,
    imagePreviewHorizontalSwipeIdleMillis: 160,
    imagePreviewHorizontalSwipeAxisRatio: 1.25,
    navigateImagePreview: direction => advances.push(direction),
  };
  runInNewContext(source.slice(start, end), context);
  const wheel = (deltaX, deltaY = 0, ctrlKey = false) => context.consumeImagePreviewHorizontalSwipe({ deltaX, deltaY, ctrlKey, preventDefault() {} });
  wheel(20, 2);
  now += 16; wheel(20, 2);
  assert.deepEqual(advances, ['next']);
  now += 80; wheel(60, 3);
  assert.deepEqual(advances, ['next']);
  now += 180; wheel(-20, 2);
  now += 16; wheel(-20, 2);
  assert.deepEqual(advances, ['next', 'previous']);
  now += 180; assert.equal(wheel(20, 34), true);
  assert.deepEqual(advances, ['next', 'previous']);
  assert.equal(wheel(40, 0, true), false);
});

test('FB-P6-079 gives a local image preview the same message-scoped order and previous-next bounds as Android', () => {
  const conversation = {
    messages: [
      { id: 'user-images', role: 'user', blocks: [
        { kind: 'ASSET_REF', asset: { id: 'user-1', mimeType: 'image/png' } },
        { kind: 'ASSET_REF', asset: { id: 'user-2', mimeType: 'image/jpeg' } },
      ] },
      { id: 'generated-images', role: 'assistant', blocks: [
        { kind: 'ASSET_REF', asset: { id: 'generated-5', mimeType: 'image/png' } },
        { kind: 'ASSET_REF', asset: { id: 'generated-4', mimeType: 'image/png' } },
        { kind: 'ASSET_REF', asset: { id: 'generated-3', mimeType: 'image/png' } },
      ] },
    ],
  };

  assert.deepEqual(relatedImageIds(conversation, 'user-2'), ['user-1', 'user-2']);
  assert.deepEqual(relatedImageIds(conversation, 'generated-4'), ['generated-3', 'generated-4', 'generated-5']);
  assert.deepEqual(imagePreviewNavigation(['generated-3', 'generated-4', 'generated-5'], 'generated-4'), {
    index: 1, previousId: 'generated-3', nextId: 'generated-5',
  });
  assert.deepEqual(imagePreviewNavigation(['generated-3', 'generated-4', 'generated-5'], 'generated-3'), {
    index: 0, previousId: null, nextId: 'generated-4',
  });
  assert.equal(imagePreviewTargetId(['generated-3', 'generated-4', 'generated-5'], 'generated-4', 'previous'), 'generated-3');
  assert.equal(imagePreviewTargetId(['generated-3', 'generated-4', 'generated-5'], 'generated-4', 'next'), 'generated-5');
  assert.equal(imagePreviewTargetId(['generated-3'], 'generated-3', 'next'), null);
});

test('FB-P6-079 routes Mac arrow keys and horizontal trackpad swipes through the same image navigator', async () => {
  const source = await readFile(resolve(import.meta.dirname, '../src/app.mjs'), 'utf8');
  for (const token of [
    'function navigateImagePreview(direction)',
    "event.key === 'ArrowLeft' || event.key === 'ArrowRight'",
    "navigateImagePreview(event.key === 'ArrowRight' ? 'next' : 'previous')",
    'function consumeImagePreviewHorizontalSwipe(event, viewport)',
    'const MAX_IMAGE_PREVIEW_PAYLOAD_CACHE_ENTRIES = 2',
    'async function decodeImagePreviewPayload(dataUrl)',
    'function warmImagePreviewNeighbors(preview)',
    'const imagePreviewHorizontalSwipeThreshold = 36',
    'const imagePreviewHorizontalSwipeAxisRatio = 1.25',
    'imagePreviewHorizontalSwipe.consumed = true',
    'event.ctrlKey',
    "const direction = imagePreviewHorizontalSwipe.deltaX > 0 ? 'next' : 'previous'",
    'if (!event.ctrlKey) return;',
    'if (consumeImagePreviewHorizontalSwipe(event, viewport)) return;',
  ]) assert.ok(source.includes(token), token);
});

test('image preview paging controls remain prominent circular controls on the media canvas', async () => {
  const css = await readFile(resolve(import.meta.dirname, '../src/image-preview.css'), 'utf8');
  for (const token of [
    '.image-preview-nav{',
    'width:52px',
    'border-radius:50%',
    'background:rgb(32 38 34 / .84)',
    '.image-preview-nav:hover:not(:disabled)',
    '.image-preview-nav:disabled',
    'stroke-width:2.4',
  ]) assert.ok(css.includes(token), token);
});
