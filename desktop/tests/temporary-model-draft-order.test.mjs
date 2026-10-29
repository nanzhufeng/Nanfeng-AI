import assert from 'node:assert/strict';
import test from 'node:test';
import vm from 'node:vm';
import { readFile } from 'node:fs/promises';
import { createDraftWriteQueue } from '../src/draft-write-queue.mjs';

const source = await readFile(new URL('../src/app.mjs', import.meta.url), 'utf8');
const draftOwner = source.slice(source.indexOf('const temporaryDraftQueue ='), source.indexOf('async function updateTemporaryDraft('));
const modelStart = source.indexOf('async function updateTemporaryModelOverride(');
const modelOwner = source.slice(modelStart, source.indexOf('\n}', modelStart) + 2);
const turn = () => new Promise(resolve => setImmediate(resolve));
function deferred() { let resolve; const promise = new Promise(yes => { resolve = yes; }); return { promise, resolve }; }

function harness() {
  let disk = { temporaryId: 'temp-one', draft: '', modelOverrideId: 'old' };
  let hold = null, fail = false, active = 0, peak = 0;
  const writes = [];
  const state = { temporaryConversation: structuredClone(disk), composerDraft: '', temporaryModelOpen: true };
  const context = vm.createContext({ state, createDraftWriteQueue, composerDraftRouteGeneration: 0,
    render() {}, setTimeout: () => 1, clearTimeout() {},
    invoke: async (command, { args }) => {
      assert.equal(command, 'update_desktop_temporary_conversation');
      writes.push(structuredClone(args));
      active += 1; peak = Math.max(peak, active);
      const gate = hold; hold = null;
      try {
        if (gate) await gate.promise;
        if (fail) { fail = false; throw new Error('synthetic disk failure'); }
        disk = structuredClone(args);
        return structuredClone(disk);
      } finally { active -= 1; }
    },
  });
  vm.runInContext(`${draftOwner}\n${modelOwner}`, context);
  return { state, writes, get disk() { return disk; }, get peak() { return peak; },
    holdNext() { hold = deferred(); return hold; }, failNext() { fail = true; },
    type: value => context.scheduleTemporaryDraft(value),
    model: value => context.updateTemporaryModelOverride(value),
    flush: () => context.flushTemporaryDraft() };
}

test('typing then choosing a temporary model retains both after the draft timer drains', async () => {
  const h = harness();
  h.type('中文草稿');
  await h.model('new');
  await h.flush();
  assert.equal(h.disk.draft, '中文草稿');
  assert.equal(h.disk.modelOverrideId, 'new');
  assert.equal(h.state.temporaryConversation.modelOverrideId, 'new');
});

test('a model choice shares the in-flight draft queue instead of racing it', async () => {
  const h = harness(), gate = h.holdNext();
  h.type('draft');
  const first = h.flush();
  await turn();
  const selection = h.model('new');
  await turn();
  gate.resolve();
  await Promise.all([first, selection]);
  await h.flush();
  assert.equal(h.peak, 1, 'draft and model writes must be serialized');
  assert.equal(h.disk.modelOverrideId, 'new');
  assert.equal(h.state.temporaryConversation.modelOverrideId, 'new');
});

test('a delayed model result cannot replace a different temporary route', async () => {
  const h = harness(), gate = h.holdNext();
  const selection = h.model('new');
  await turn();
  h.state.temporaryConversation = { temporaryId: 'temp-two', draft: 'other', modelOverrideId: null };
  h.state.composerDraft = 'other';
  gate.resolve();
  await selection;
  assert.equal(h.state.temporaryConversation.temporaryId, 'temp-two');
  assert.equal(h.state.composerDraft, 'other');
});

test('a failed model write retains the exact local snapshot for an explicit flush retry', async () => {
  const h = harness();
  h.type('retain this');
  h.failNext();
  await h.model('new');
  assert.match(h.state.error, /synthetic disk failure/);
  assert.equal(h.disk.modelOverrideId, 'old');
  await h.flush();
  assert.equal(h.disk.modelOverrideId, 'new');
  assert.equal(h.disk.draft, 'retain this');
});

test('rapid selections followed by auto keep the newest text and model together', async () => {
  const h = harness(), gate = h.holdNext();
  h.type('first');
  const first = h.model('first-model');
  await turn();
  const second = h.model('second-model');
  h.type('latest text');
  const auto = h.model(null);
  gate.resolve();
  await Promise.all([first, second, auto]);
  await h.flush();
  assert.equal(h.disk.draft, 'latest text');
  assert.equal(h.disk.modelOverrideId, null);
  assert.equal(h.state.temporaryConversation.modelOverrideId, null);
  assert.equal(h.peak, 1);
});

test('the normal close flush drains a temporary model save already in flight', async () => {
  const h = harness(), gate = h.holdNext();
  const save = h.model('new');
  await turn();
  let drained = false;
  const closing = h.flush().then(() => { drained = true; });
  await turn();
  assert.equal(drained, false);
  gate.resolve();
  await Promise.all([save, closing]);
  assert.equal(h.disk.modelOverrideId, 'new');
});
