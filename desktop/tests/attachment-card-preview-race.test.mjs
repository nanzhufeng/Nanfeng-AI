import test from 'node:test';
import assert from 'node:assert/strict';
import vm from 'node:vm';
import { readFile } from 'node:fs/promises';

const source = await readFile(new URL('../src/app.mjs', import.meta.url), 'utf8');
const body = source.slice(source.indexOf('let searchAttachmentPreviewObserver ='), source.indexOf('async function openAttachmentPreview('));
import { attachmentPreviewKey } from '../src/attachment-preview-key.mjs';
const settle = () => new Promise(setImmediate);
function card(workspaceId, id = 'shared') {
  const content = { classList: { contains: () => true }, querySelector: () => null, replaceChildren(...children) { this.children = children; } };
  return { dataset: { chatAttachmentPreview: id, thumbnailWorkspaceId: workspaceId }, content, isConnected: true,
    querySelector: () => content, getAttribute: () => 'synthetic' };
}
function harness({ observer = false } = {}) {
  let cards = [];
  const state = { searchAttachmentPreviews: {}, searchAttachmentPreviewPending: new Set(), current: { summary: { id: 'a' } } };
  const pending = [], observers = [];
  const context = vm.createContext({ state, native: true, attachmentPreviewKey,
    document: { querySelectorAll: () => cards, querySelector: () => null, createElement: tag => ({ tag }) },
    invoke(command, args) { return new Promise((resolve, reject) => pending.push({ command, args, resolve, reject })); },
    ...(observer ? { IntersectionObserver: class {
      constructor(callback) { this.callback = callback; observers.push(this); }
      observe() {} unobserve() {} disconnect() {}
    } } : {}),
  });
  vm.runInContext(body, context);
  return { state, pending, observers, switchArea(next) { context.state = next; }, render(next) { cards.forEach(c => { c.isConnected = false; }); cards = next; cards.forEach(c => { c.isConnected = true; }); context.scheduleAttachmentCardPreviewReads(); } };
}

for (const fail of [false, true]) test(`another workspace's late ${fail ? 'failure' : 'success'} cannot fill or poison current card`, async () => {
  const h = harness(); const a = card('a'), b = card('b'); h.render([a]); h.render([b]);
  assert.equal(h.pending.length, 2);
  if (fail) h.pending[0].reject(new Error('synthetic')); else h.pending[0].resolve({ text: 'workspace a' });
  await settle(); assert.equal(b.content.children, undefined);
  const bAgain = card('b'); h.render([bAgain]);
  h.pending[1].resolve({ text: 'workspace b' }); await settle();
  assert.equal(bAgain.content.children?.[1]?.textContent, 'workspace b');
  assert.equal(h.state.searchAttachmentPreviews[attachmentPreviewKey('b', 'shared')]?.text, 'workspace b');
  assert.equal(h.state.searchAttachmentPreviews[attachmentPreviewKey('a', 'shared')]?.[fail ? 'error' : 'text'], fail ? '本地附件预览不可用' : 'workspace a');
});

test('cached workspace a does not suppress loading same attachment ID in b', async () => {
  const h = harness(); h.render([card('a')]); h.pending[0].resolve({ text: 'a' }); await settle();
  h.render([card('b')]); assert.equal(h.pending.length, 2); assert.equal(h.pending[1].args.args.workspaceId, 'b');
});

test('same workspace rerenders share in-flight read and hydrate only current nodes', async () => {
  const h = harness(); const first = card('a'); h.render([first]);
  const current = card('a'); h.render([current]); h.render([current]);
  assert.equal(h.pending.length, 1); h.pending[0].resolve({ text: 'current' }); await settle();
  assert.equal(first.content.children, undefined); assert.equal(current.content.children[1].textContent, 'current');
  assert.equal(h.state.searchAttachmentPreviewPending.size, 0);
});

test('queue stays at two reads and discarded route jobs do not run', async () => {
  const h = harness(); h.render([card('a','1'),card('a','2'),card('a','3')]); assert.equal(h.pending.length, 2);
  h.render([]); h.pending[0].resolve({ text: 'old' }); h.pending[1].resolve({ text: 'old' }); await settle();
  assert.equal(h.pending.length, 2); assert.equal(h.state.searchAttachmentPreviewPending.size, 0);
  h.render([card('a','3')]); assert.equal(h.pending.length, 3);
});

test('disconnected observer callback cannot start old page work', () => {
  const h = harness({ observer: true }); const old = card('a'); h.render([old]); const stale = h.observers[0]; h.render([card('b')]);
  stale.callback([{ isIntersecting: true, target: old }]);
  assert.equal(h.pending.length, 0); assert.equal(h.state.searchAttachmentPreviewPending.size, 0);
});

test('cached preview hydrates preserved nodes without a second read', async () => {
  const h = harness(); h.render([card('a')]); h.pending[0].resolve({ text: 'cached' }); await settle();
  const current = card('a'); h.render([current]);
  assert.equal(current.content.children?.[1]?.textContent, 'cached'); assert.equal(h.pending.length, 1);
});

for (const fail of [false,true]) test(`cross-area identical card IDs ignore old ${fail?'failure':'success'} and retain current pending`, async () => {
  const h = harness(); h.render([card('same')]);
  const work = {searchAttachmentPreviews:{},searchAttachmentPreviewPending:new Set()}; h.switchArea(work);
  const current = card('same'); h.render([current]);
  if (fail) h.pending[0].reject(Object.assign(Error('old area'),{code:'STALE_DATA_AREA'}));
  else h.pending[0].resolve({text:'CHAT private'});
  await settle(); assert.equal(current.content.children,undefined);
  assert.equal(Object.keys(work.searchAttachmentPreviews).length,0);
  assert.equal(work.searchAttachmentPreviewPending.size,1);
  assert.equal(h.state.searchAttachmentPreviewPending.size,0);
  h.pending[1].resolve({text:'WORK current'}); await settle();
  assert.equal(current.content.children[1].textContent,'WORK current');
});
