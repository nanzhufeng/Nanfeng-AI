import test from 'node:test';
import assert from 'node:assert/strict';
import { createUtilityPageNavigation } from '../src/utility-page-navigation.mjs';
const deferred = () => { let resolve, reject; const promise = new Promise((yes, no) => { resolve = yes; reject = no; }); return { promise, resolve, reject }; };

test('navigation paints before pending disk read and repeated clicks share the read', async () => {
  const disk = deferred(); const events = []; let reads = 0;
  const navigate = createUtilityPageNavigation({ isCurrent: () => true, render: () => events.push('paint'), loading: (_, busy) => events.push(busy), failed: () => assert.fail() });
  const read = () => { reads++; events.push('read'); return disk.promise; };
  const first = navigate('reminders', read);
  assert.deepEqual(events, [true, 'paint']);
  assert.equal(navigate('reminders', read), first);
  await Promise.resolve(); assert.equal(reads, 1);
  disk.resolve(); await first;
  assert.deepEqual(events.slice(-2), [false, 'paint']);
});

test('leaving a slow page prevents its late failure or completion repainting the new page', async () => {
  const disk = deferred(); let current = 'transcription', paints = 0; const errors = [], busy = new Set();
  const navigate = createUtilityPageNavigation({ isCurrent: page => current === page, render: () => paints++, loading: (page, value) => value ? busy.add(page) : busy.delete(page), failed: (_, error) => errors.push(error) });
  const first = navigate(current, () => disk.promise);
  current = 'chat'; disk.reject(Error('read failed')); await first;
  assert.equal(paints, 1); assert.deepEqual(errors, []); assert.equal(busy.size, 0);
  current = 'transcription'; await navigate(current, () => Promise.reject(Error('still unavailable')));
  assert.equal(errors.length, 1); assert.equal(busy.size, 0); assert.equal(paints, 3);
});
