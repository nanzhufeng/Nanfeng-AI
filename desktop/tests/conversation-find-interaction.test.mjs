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

test('closing an interrupted composition resets it so reopened find accepts ordinary input', () => {
  const clock = fakeTimers(), settled = [];
  const c = createConversationFindInputController({onSettled:q=>settled.push(q),setTimer:clock.setTimer,clearTimer:clock.clearTimer});
  c.onCompositionStart(); c.onInput({value:'未确认',isComposing:true}); c.cancel();
  c.onInput({value:'重新查找'}); clock.run(CONVERSATION_FIND_SETTLE_MS);
  assert.deepEqual(settled,['重新查找']);
});

test('pending cancellation inside a live composition does not end that composition', () => {
  const clock = fakeTimers(), settled = [];
  const c = createConversationFindInputController({onSettled:q=>settled.push(q),setTimer:clock.setTimer,clearTimer:clock.clearTimer});
  c.onCompositionStart();c.onInput({value:'n',isComposing:true});c.onInput({value:'ni'});
  clock.run(CONVERSATION_FIND_SETTLE_MS*2);assert.deepEqual(settled,[]);
  c.onCompositionEnd('你');clock.run(CONVERSATION_FIND_SETTLE_MS);assert.deepEqual(settled,['你']);
});

test('a cancelled callback already queued by the host cannot settle a reopened identical query', () => {
  const callbacks=[],settled=[];
  const c=createConversationFindInputController({onSettled:q=>settled.push(q),setTimer:fn=>(callbacks.push(fn),callbacks.length),clearTimer:()=>{}});
  c.onInput({value:'相同查询'});c.cancel();c.onInput({value:'相同查询'});
  callbacks[0]();assert.deepEqual(settled,[]);callbacks[1]();assert.deepEqual(settled,['相同查询']);
});

test('a stale callback cannot detach a newer pending lookup from cancellation', () => {
  const callbacks=[],settled=[];
  const c=createConversationFindInputController({onSettled:q=>settled.push(q),setTimer:fn=>(callbacks.push(fn),callbacks.length),clearTimer:()=>{}});
  c.onInput({value:'旧'});c.onInput({value:'新'});callbacks[0]();c.cancel();callbacks[1]();assert.deepEqual(settled,[]);
});

test('scope changes end abandoned composition while identical scope preserves current composition', () => {
  const clock=fakeTimers(),settled=[],owner={};
  const c=createConversationFindInputController({onSettled:q=>settled.push(q),setTimer:clock.setTimer,clearTimer:clock.clearTimer});
  c.setScope(owner,'chat','ws','a',true);c.onCompositionStart();c.setScope(owner,'chat','ws','a',true);c.onInput({value:'候选'});clock.run(CONVERSATION_FIND_SETTLE_MS);assert.deepEqual(settled,[]);
  c.setScope(owner,'chat','ws','b',true);c.onInput({value:'另一会话'});clock.run(CONVERSATION_FIND_SETTLE_MS);assert.deepEqual(settled,['另一会话']);
});

test('closing, new conversation, leaving page or replacing area owner invalidates pending callbacks', () => {
  const owner={},changes=[[owner,'chat','ws','a',false],[owner,'chat','ws',null,true],[owner,'settings','ws','a',true],[{},'chat','ws','a',true]];
  for(const next of changes){const callbacks=[],settled=[];const c=createConversationFindInputController({onSettled:q=>settled.push(q),setTimer:fn=>(callbacks.push(fn),callbacks.length),clearTimer:()=>{}});c.setScope(owner,'chat','ws','a',true);c.onInput({value:'旧'});c.setScope(...next);callbacks[0]();assert.deepEqual(settled,[])}
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
