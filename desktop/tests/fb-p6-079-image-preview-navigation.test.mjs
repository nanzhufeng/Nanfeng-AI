import assert from 'node:assert/strict';
import test from 'node:test';

import { readFile } from 'node:fs/promises';
import { resolve } from 'node:path';
import { runInNewContext } from 'node:vm';
import { imagePreviewNavigation, imagePreviewTargetId, relatedImageIds } from '../src/image-preview-navigation.mjs';

test('one trackpad gesture including a loading gap advances exactly one image', async () => {
  const source = await readFile(resolve(import.meta.dirname, '../src/app.mjs'), 'utf8');
  const start = source.indexOf('function consumeImagePreviewHorizontalSwipe(');
  const end = source.indexOf("app.addEventListener('wheel'", start);
  let now = 1000;
  const advances = [];
  const context = {
    state: { imagePreview: { attachmentId: 'one', zoom: 1 } },
    performance: { now: () => now },
    imagePreviewHorizontalSwipe: { lastAt: 0, deltaX: 0, lockUntil: 0 },
    imagePreviewHorizontalSwipeThreshold: 56,
    navigateImagePreview: direction => advances.push(direction),
  };
  runInNewContext(source.slice(start, end), context);
  const wheel = deltaX => context.consumeImagePreviewHorizontalSwipe({ deltaX, deltaY: 0, preventDefault() {} });
  wheel(60);
  context.state.imagePreview = { attachmentId: 'two', loading: true };
  now += 240; wheel(65);
  context.state.imagePreview = { attachmentId: 'two', zoom: 1 };
  now += 240; wheel(60);
  assert.deepEqual(advances, ['next']);
  now += 600; wheel(-60);
  assert.deepEqual(advances, ['next', 'previous']);
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
    'Math.abs(event.deltaX) <= Math.abs(event.deltaY)',
    'const imagePreviewHorizontalSwipeThreshold = 56',
    "const direction = imagePreviewHorizontalSwipe.deltaX > 0 ? 'next' : 'previous'",
    'imagePreviewHorizontalSwipe.lockUntil = now + 280',
    'if (consumeImagePreviewHorizontalSwipe(event, viewport)) return;',
  ]) assert.ok(source.includes(token), token);
});
