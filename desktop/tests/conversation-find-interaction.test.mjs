import assert from 'node:assert/strict';
import test from 'node:test';
import { readFile } from 'node:fs/promises';
import {
  CONVERSATION_FIND_SETTLE_MS,
  conversationFindScrollTop,
  createConversationFindInputController,
} from '../src/conversation-find-interaction.mjs';

function fakeTimers() {
  let now = 0;
  let nextId = 1;
  const queued = new Map();
  return {
    clearTimer(id) { queued.delete(id); },
    run(milliseconds) {
      now += milliseconds;
      for (;;) {
        const due = [...queued.entries()]
          .filter(([, entry]) => entry.at <= now)
          .sort((left, right) => left[1].at - right[1].at)[0];
        if (!due) return;
        queued.delete(due[0]);
        due[1].callback();
      }
    },
    setTimer(callback, milliseconds) {
      const id = nextId++;
      queued.set(id, { at: now + milliseconds, callback });
      return id;
    },
  };
}

test('conversation find waits for a completed IME character and a settled query before matching', () => {
  const clock = fakeTimers();
  const settled = [];
  const controller = createConversationFindInputController({
    onSettled: query => settled.push(query),
    setTimer: clock.setTimer,
    clearTimer: clock.clearTimer,
  });

  controller.onCompositionStart();
  controller.onInput({ value: 'n', isComposing: true });
  clock.run(CONVERSATION_FIND_SETTLE_MS * 2);
  assert.deepEqual(settled, []);

  controller.onCompositionEnd('你');
  clock.run(CONVERSATION_FIND_SETTLE_MS - 1);
  assert.deepEqual(settled, []);

  controller.onInput({ value: '你好', isComposing: false });
  clock.run(CONVERSATION_FIND_SETTLE_MS - 1);
  assert.deepEqual(settled, []);
  clock.run(1);
  assert.deepEqual(settled, ['你好']);
});

test('conversation find centers the matched text inside the conversation scroll owner', () => {
  assert.equal(conversationFindScrollTop({
    scrollTop: 120,
    ownerTop: 100,
    ownerHeight: 500,
    targetTop: 800,
    targetHeight: 40,
  }), 590);
  assert.equal(conversationFindScrollTop({
    scrollTop: 0,
    ownerTop: 100,
    ownerHeight: 500,
    targetTop: 110,
    targetHeight: 20,
  }), 0);
});

test('Desktop wires the find field through the composition-aware controller and the message-list scroll owner', async () => {
  const app = await readFile(new URL('../src/app.mjs', import.meta.url), 'utf8');
  const shell = await readFile(new URL('../src/chat-shell.mjs', import.meta.url), 'utf8');
  assert.match(app, /createConversationFindInputController/);
  assert.match(app, /app\.addEventListener\('compositionstart'/);
  assert.match(app, /app\.addEventListener\('compositionend'/);
  assert.match(shell, /data-scroll-owner="message-list"/);
  assert.match(app, /conversationFindScrollTop/);
});
