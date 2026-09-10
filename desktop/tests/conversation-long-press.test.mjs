import test from 'node:test';
import assert from 'node:assert/strict';
import { createConversationLongPressController } from '../src/conversation-long-press.mjs';

function fakeClock() {
  let now = 0;
  let nextId = 1;
  const jobs = new Map();
  return {
    setTimer(callback, delay) { const id = nextId++; jobs.set(id, { at: now + delay, callback }); return id; },
    clearTimer(id) { jobs.delete(id); },
    advance(ms) {
      now += ms;
      [...jobs.entries()].filter(([, job]) => job.at <= now).sort((a, b) => a[1].at - b[1].at).forEach(([id, job]) => {
        if (!jobs.delete(id)) return;
        job.callback();
      });
    },
    pending() { return jobs.size; },
  };
}

test('mouse long press opens once, suppresses navigation, and always clears its timer', () => {
  const clock = fakeClock();
  const opened = [];
  const controller = createConversationLongPressController({
    thresholdMs: 520,
    setTimer: clock.setTimer,
    clearTimer: clock.clearTimer,
    onOpen: value => opened.push(value),
  });
  controller.pointerDown({ pointerId: 7, pointerType: 'mouse', clientX: 30, clientY: 40 }, 'conversation-a');
  clock.advance(519);
  assert.deepEqual(opened, []);
  clock.advance(1);
  clock.advance(1000);
  assert.deepEqual(opened, ['conversation-a']);
  assert.equal(controller.pointerUp({ pointerId: 7 }), true);
  assert.equal(clock.pending(), 0);
  assert.equal(controller.consumeClick('conversation-a'), true);
  assert.equal(controller.consumeClick('conversation-a'), false);
});

test('movement, scroll, pointercancel and early pointerup cancel without stale timers', () => {
  for (const finish of ['move', 'scroll', 'cancel', 'up']) {
    const clock = fakeClock();
    const opened = [];
    const controller = createConversationLongPressController({ setTimer: clock.setTimer, clearTimer: clock.clearTimer, onOpen: value => opened.push(value) });
    controller.pointerDown({ pointerId: 3, pointerType: 'mouse', clientX: 10, clientY: 10 }, `conversation-${finish}`);
    if (finish === 'move') controller.pointerMove({ pointerId: 3, clientX: 30, clientY: 10 });
    if (finish === 'scroll') controller.cancel('scroll');
    if (finish === 'cancel') controller.pointerCancel({ pointerId: 3 });
    if (finish === 'up') controller.pointerUp({ pointerId: 3 });
    clock.advance(2000);
    assert.deepEqual(opened, [], finish);
    assert.equal(clock.pending(), 0, finish);
    assert.equal(controller.consumeClick(`conversation-${finish}`), false, finish);
  }
});

test('a new press replaces the prior timer and non-primary mouse input is ignored', () => {
  const clock = fakeClock();
  const opened = [];
  const controller = createConversationLongPressController({ setTimer: clock.setTimer, clearTimer: clock.clearTimer, onOpen: value => opened.push(value) });
  assert.equal(controller.pointerDown({ pointerId: 1, pointerType: 'mouse', button: 2, clientX: 0, clientY: 0 }, 'right-click'), false);
  controller.pointerDown({ pointerId: 2, pointerType: 'mouse', button: 0, clientX: 0, clientY: 0 }, 'first');
  controller.pointerDown({ pointerId: 3, pointerType: 'mouse', button: 0, clientX: 0, clientY: 0 }, 'second');
  clock.advance(600);
  assert.deepEqual(opened, ['second']);
  assert.equal(clock.pending(), 0);
});

test('ten consecutive mouse long presses open the same row ten out of ten without stale navigation', () => {
  const clock = fakeClock();
  const opened = [];
  const controller = createConversationLongPressController({ setTimer: clock.setTimer, clearTimer: clock.clearTimer, onOpen: value => opened.push(value) });
  for (let index = 0; index < 10; index += 1) {
    controller.pointerDown({ pointerId: index + 1, pointerType: 'mouse', button: 0, clientX: 12, clientY: 12 }, 'conversation-stable');
    clock.advance(520);
    assert.equal(controller.pointerUp({ pointerId: index + 1 }), true, `press ${index + 1}`);
    assert.equal(controller.consumeClick('conversation-stable'), true, `click ${index + 1}`);
    assert.equal(clock.pending(), 0, `timer ${index + 1}`);
  }
  assert.deepEqual(opened, Array(10).fill('conversation-stable'));
});
