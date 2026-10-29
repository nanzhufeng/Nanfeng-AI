import assert from 'node:assert/strict';
import test from 'node:test';
import { observeNativeUntil } from '../scripts/native-observation.mjs';

test('native observation does not treat an async false predicate as success', async () => {
  let committed = false;
  const result = await observeNativeUntil({
    read: async () => ({ durable: committed }),
    accept: async value => { if (!value.durable) { committed = true; return false; } return true; },
    intervalMs: 1,
  });
  assert.deepEqual(result, { durable: true });
});

test('native observation waits for the actual read to resolve', async () => {
  let finish;
  const pending = new Promise(resolve => { finish = resolve; });
  let accepted = false;
  const observation = observeNativeUntil({ read: () => pending, accept: value => { accepted = true; return value.saved; } });
  await Promise.resolve();
  assert.equal(accepted, false);
  finish({ saved: true, revision: 2 });
  assert.deepEqual(await observation, { saved: true, revision: 2 });
});

test('an unchanged native state times out rather than becoming a green result', async () => {
  await assert.rejects(observeNativeUntil({ read: async () => null, accept: async () => false, timeoutMs: 15, intervalMs: 1 }), /timed out/);
});

test('a native read failure is reported without pretending it is an empty state', async () => {
  const unavailable = new Error('native read unavailable');
  await assert.rejects(observeNativeUntil({ read: async () => { throw unavailable; }, accept: () => true }), error => error === unavailable);
});

test('an unresolved native read cannot keep acceptance waiting indefinitely', async () => {
  let finish;
  const pending = new Promise(resolve => { finish = resolve; });
  let projected = false;
  await assert.rejects(observeNativeUntil({ read: () => pending, accept: () => { projected = true; return true; }, timeoutMs: 15 }), /timed out/);
  finish({ saved: true });
  await Promise.resolve();
  assert.equal(projected, false);
});
