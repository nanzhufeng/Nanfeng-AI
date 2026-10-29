import test from 'node:test';
import assert from 'node:assert/strict';
import vm from 'node:vm';
import { readFile } from 'node:fs/promises';
import { attachmentPreviewKey } from '../src/attachment-preview-key.mjs';

const source = await readFile(new URL('../src/app.mjs', import.meta.url), 'utf8');
const body = source.slice(source.indexOf('let imageThumbnailObserver ='), source.indexOf('let searchAttachmentPreviewObserver ='));
const settle = () => new Promise(setImmediate);
function card(kind, workspaceId, id = 'shared') {
  const element = { dataset: { [`${kind}Thumbnail`]: id, thumbnailWorkspaceId: workspaceId }, isConnected: true,
    classList: { contains: () => false }, getAttribute: () => 'synthetic' };
  const placeholder = { replaceWith(image) { element.image = image; }, textContent: '' };
  element.querySelector = () => element.image ? null : placeholder;
  element.placeholder = placeholder;
  return element;
}
function harness(kind, observer = false, { deferVideoBegin = false } = {}) {
  let cards = [];
  const state = { current: { summary: { id: 'a' } }, imageThumbnails: {}, videoThumbnails: {}, imageThumbnailPending: new Set(), videoThumbnailPending: new Set() };
  const pending = [], observers = [], handlers = [], commands = [], deferredBegins = [];
  let nextRequestId = 0;
  const context = vm.createContext({ state, native: true, attachmentPreviewKey,
    document: { querySelectorAll: () => cards, querySelector: () => null, createElement: tag => ({ tag }) },
    app: { addEventListener: (name, handler) => handlers.push(handler) }, withFullSearchDeadline: promise => promise,
    queueMicrotask,
    invoke(command, args, owner) {
      commands.push({ command, args, owner });
      if (command === 'begin_desktop_video_thumbnail') {
        if (!deferVideoBegin) return Promise.resolve(++nextRequestId);
        return new Promise(resolve => deferredBegins.push(resolve));
      }
      if (command === 'cancel_desktop_video_thumbnail') return Promise.resolve();
      return new Promise((resolve, reject) => pending.push({ command, args, resolve, reject }));
    },
    ...(observer ? { IntersectionObserver: class {
      constructor(callback) { this.callback = callback; observers.push(this); }
      observe() {} unobserve() {} disconnect() {}
    } } : {}),
  });
  context.invokePreviewOwner = (owner, command, args = {}) => context.invoke(command, args, owner);
  vm.runInContext(body, context);
  const schedule = kind === 'image' ? context.scheduleImageThumbnailReads : context.scheduleVideoThumbnailReads;
  return { state, pending, observers, commands,
    switchArea(next) { context.state = next; },
    resolveNextBegin(requestId = ++nextRequestId) { deferredBegins.shift()?.(requestId); },
    render(next, options) { cards.forEach(c => { c.isConnected = false; }); cards = next; cards.forEach(c => { c.isConnected = true; }); schedule(options); },
    retry(element) { handlers[0]({ target: { closest: () => element }, preventDefault() {}, stopImmediatePropagation() {} }); } };
}

for (const kind of ['image', 'video']) {
  for (const fail of [false, true]) test(`${kind}: late ${fail ? 'error' : 'image'} stays with original workspace`, async () => {
    const h = harness(kind); h.render([card(kind, 'a')]); if (kind === 'video') await settle();
    const b = card(kind, 'b'); h.render([b]); if (kind === 'video') await settle();
    if (fail) h.pending[0].reject(new Error('synthetic')); else h.pending[0].resolve({ dataUrl: 'data:image/png;base64,AAA' });
    await settle(); assert.equal(b.image, undefined); assert.equal(b.dataset.retryImageThumbnail, undefined);
    h.pending[1].resolve({ dataUrl: 'data:image/png;base64,BBB' }); await settle();
    assert.equal(b.image.src, 'data:image/png;base64,BBB');
    assert.equal(h.state[`${kind}Thumbnails`][attachmentPreviewKey('b', 'shared')].dataUrl, b.image.src);
  });
  test(`${kind}: cache cannot suppress another workspace; same-owner rerender reuses read`, async () => {
    const h = harness(kind); h.render([card(kind, 'a')]); if (kind === 'video') await settle();
    h.pending[0].resolve({ dataUrl: 'A' }); await settle();
    h.render([card(kind, 'b')]); if (kind === 'video') await settle(); assert.equal(h.pending.length, 2);
    const current = card(kind, 'b'); h.render([current]); assert.equal(h.pending.length, 2);
    h.pending[1].resolve({ dataUrl: 'B' }); await settle(); assert.equal(current.image.src, 'B');
    const cached = card(kind, 'b'); h.render([cached]); assert.equal(cached.image.src, 'B'); assert.equal(h.pending.length, 2);
  });
  test(`${kind}: old observer cannot enqueue detached page; two in-flight reads stay bounded`, async () => {
    const h = harness(kind, true); const old = card(kind,'a'); h.render([old]); const oldObserver = h.observers[0];
    const current = [card(kind,'b','1'),card(kind,'b','2'),card(kind,'b','3')]; h.render(current);
    oldObserver.callback([{ isIntersecting: true, target: old }]); assert.equal(h.pending.length, 0);
    h.observers.at(-1).callback(current.map(target=>({ isIntersecting: true, target })));
    if (kind === 'video') await settle();
    assert.equal(h.pending.length, 2); h.render([]);
    h.pending[0].resolve({ dataUrl: 'old' }); h.pending[1].resolve({ dataUrl: 'old' }); await settle();
    assert.equal(h.pending.length, 2); assert.equal(h.state[`${kind}ThumbnailPending`].size, 0);
  });
}

test('image retry clears only the clicked workspace cache', async () => {
  const h = harness('image'); const a = card('image','a'), b = card('image','b'); h.render([a,b]);
  h.pending[0].reject(new Error('synthetic')); h.pending[1].resolve({dataUrl:'B'}); await settle();
  h.retry(a); assert.equal(h.pending.length, 3); assert.equal(h.pending[2].args.args.workspaceId,'a');
  assert.equal(h.state.imageThumbnails[attachmentPreviewKey('b','shared')].dataUrl,'B');
});

test('retained image observer can hydrate new composer nodes without losing transcript reads', async () => {
  const h=harness('image',true); const original=card('image','a');h.render([original]);const observer=h.observers[0];
  observer.callback([{isIntersecting:true,target:original}]);
  const composer=card('image','a','composer');h.render([original,composer],{composerOnly:true,retainObserved:true});
  assert.equal(h.observers.length,1);observer.callback([{isIntersecting:true,target:composer}]);
  h.pending[0].resolve({dataUrl:'transcript'});h.pending[1].resolve({dataUrl:'composer'});await settle();
  assert.equal(original.image.src,'transcript');assert.equal(composer.image.src,'composer');
});

test('video: cancellation before native allocation never starts a read', async () => {
  const h = harness('video', false, { deferVideoBegin: true });
  h.render([card('video', 'a')]);
  assert.equal(h.commands.filter(entry => entry.command === 'begin_desktop_video_thumbnail').length, 1);
  assert.equal(h.pending.length, 0);
  h.render([]);
  h.resolveNextBegin(41);
  await settle();
  assert.deepEqual(h.commands.filter(entry => entry.command === 'cancel_desktop_video_thumbnail').map(entry => entry.args.requestId), [41]);
  assert.equal(h.pending.length, 0);
  assert.equal(h.state.videoThumbnailPending.size, 0);
});

test('video: same owner rerender retains one read and leaving cancels only that native request', async () => {
  const h = harness('video');
  h.render([card('video', 'a')]); await settle();
  assert.equal(h.pending.length, 1);
  assert.equal(h.pending[0].args.requestId, 1);
  h.render([card('video', 'a')]);
  assert.equal(h.commands.filter(entry => entry.command === 'cancel_desktop_video_thumbnail').length, 0);
  assert.equal(h.pending.length, 1);
  h.render([]);
  assert.deepEqual(h.commands.filter(entry => entry.command === 'cancel_desktop_video_thumbnail').map(entry => entry.args.requestId), [1]);
  h.pending[0].resolve({ dataUrl: 'late-cancelled' }); await settle();
  assert.equal(h.state.videoThumbnails[attachmentPreviewKey('a', 'shared')], undefined);
});

test('video: cancellation is exact across two active workspace reads', async () => {
  const h = harness('video');
  const a = card('video', 'a'), b = card('video', 'b');
  h.render([a, b]); await settle();
  assert.deepEqual(h.pending.map(entry => entry.args.requestId), [1, 2]);
  h.render([b]);
  assert.deepEqual(h.commands.filter(entry => entry.command === 'cancel_desktop_video_thumbnail').map(entry => entry.args.requestId), [1]);
  h.pending[0].reject(new Error('cancelled')); h.pending[1].resolve({ dataUrl: 'B' }); await settle();
  assert.equal(h.state.videoThumbnails[attachmentPreviewKey('a', 'shared')], undefined);
  assert.equal(h.state.videoThumbnails[attachmentPreviewKey('b', 'shared')].dataUrl, 'B');
});

test('video: card returning while cancellation settles gets a fresh read', async () => {
  const h = harness('video');
  h.render([card('video', 'a')]); await settle();
  h.render([]);
  const returned = card('video', 'a');
  h.render([returned]);
  assert.equal(h.pending.length, 1);
  h.pending[0].reject(new Error('cancelled')); await settle(); await settle();
  assert.equal(h.pending.length, 2);
  assert.equal(h.pending[1].args.requestId, 2);
  h.pending[1].resolve({ dataUrl: 'fresh' }); await settle();
  assert.equal(returned.image.src, 'fresh');
});

for (const fail of [false, true]) test(`image: late ${fail ? 'failure' : 'success'} cannot poison another area with identical IDs`, async () => {
  const h = harness('image'); h.render([card('image', 'same')]);
  const work = { imageThumbnails: {}, imageThumbnailPending: new Set() };
  h.switchArea(work); const current = card('image', 'same'); h.render([current]);
  if (fail) h.pending[0].reject(Object.assign(Error('old area'), {code:'STALE_DATA_AREA'}));
  else h.pending[0].resolve({dataUrl:'CHAT-private'});
  await settle();
  assert.equal(current.image, undefined);
  assert.equal(current.dataset.retryImageThumbnail, undefined);
  assert.equal(Object.keys(work.imageThumbnails).length, 0);
  assert.equal(work.imageThumbnailPending.size, 1, 'old finally must not release the new request');
  assert.equal(h.state.imageThumbnailPending.size, 0, 'original pending read must be released');
  h.pending[1].resolve({dataUrl:'WORK-current'}); await settle();
  assert.equal(current.image.src, 'WORK-current');
  assert.equal(work.imageThumbnailPending.size, 0);
});

test('video: changing area cancels the old same-ID read without clearing the new pending read', async () => {
  const h = harness('video'); h.render([card('video','same')]); await settle();
  const work = {videoThumbnails:{}, videoThumbnailPending:new Set()}; h.switchArea(work);
  const current = card('video','same'); h.render([current]); await settle();
  const cancelled = h.commands.find(e => e.command === 'cancel_desktop_video_thumbnail');
  assert.equal(cancelled?.args.requestId, 1);
  assert.equal(cancelled?.owner, h.state);
  h.pending[0].reject(Error('cancelled original')); await settle();
  assert.equal(Object.keys(work.videoThumbnails).length, 0);
  assert.equal(work.videoThumbnailPending.size, 1);
  assert.equal(h.state.videoThumbnailPending.size, 0);
  h.pending[1].resolve({dataUrl:'WORK-video'}); await settle();
  assert.equal(current.image.src, 'WORK-video');
});
test('video: a native request allocated after switching areas is cancelled in its original area', async () => {
  const h = harness('video', false, {deferVideoBegin:true}); h.render([card('video','same')]);
  h.switchArea({videoThumbnails:{},videoThumbnailPending:new Set()}); h.render([]);
  h.resolveNextBegin(77); await settle();
  const cancellation = h.commands.find(e => e.command === 'cancel_desktop_video_thumbnail');
  assert.equal(cancellation?.args.requestId, 77);
  assert.equal(cancellation?.owner, h.state);
  assert.equal(h.pending.length, 0);
  assert.equal(h.state.videoThumbnailPending.size, 0);
});
