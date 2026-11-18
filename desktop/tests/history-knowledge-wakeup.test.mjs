import test from 'node:test';
import assert from 'node:assert/strict';
import { createHistoryKnowledgeWakeup } from '../src/history-knowledge-wakeup.mjs';

function fixture(runDue = async () => {}) {
  let enabled = true;
  let sequence = 0;
  const timers = new Map();
  const errors = [];
  const owner = createHistoryKnowledgeWakeup({ isEnabled: () => enabled, runDue,
    onError: error => errors.push(error), setTimer: callback => { timers.set(++sequence, callback); return sequence; }, clearTimer: id => timers.delete(id) });
  return { owner, timers, errors, enable: value => { enabled = value; }, tick: async () => {
    const [id, callback] = timers.entries().next().value; timers.delete(id); callback();
    await new Promise(resolve => setImmediate(resolve));
  } };
}

test('a continuously open app wakes later periods without restart and respects disable', async () => {
  let calls = 0;
  const f = fixture(async () => { calls += 1; });
  f.owner.start(); f.owner.start();
  assert.equal(f.timers.size, 1);
  await f.tick(); await f.tick();
  assert.equal(calls, 2);
  f.enable(false); await f.tick();
  assert.equal(calls, 2);
  f.enable(true); await f.owner.wake();
  assert.equal(calls, 3);
  f.owner.stop(); assert.equal(f.timers.size, 0);
});

test('focus and timer cannot overlap an active dispatch, and stop cannot rearm', async () => {
  let release;
  let calls = 0;
  const f = fixture(() => { calls += 1; return new Promise(resolve => { release = resolve; }); });
  f.owner.start(); const pending = f.owner.wake();
  await f.owner.wake(); assert.equal(calls, 1);
  f.owner.stop(); release(); await pending;
  assert.equal(f.timers.size, 0);
});

test('a failed wake does not permanently disable future owner checks', async () => {
  let calls = 0;
  const f = fixture(async () => { if (++calls === 1) throw new Error('owner unavailable'); });
  f.owner.start(); await f.tick(); await f.tick();
  assert.equal(f.errors.length, 1); assert.equal(calls, 2);
  f.owner.stop();
});
