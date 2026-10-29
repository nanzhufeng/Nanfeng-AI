import test from 'node:test';
import assert from 'node:assert/strict';
import vm from 'node:vm';
import { readFile } from 'node:fs/promises';

const source = await readFile(new URL('../src/app.mjs', import.meta.url), 'utf8');
const start = source.includes('let archivePreviewPreparation =') ? source.indexOf('let archivePreviewPreparation =') : source.indexOf('async function openArchivePreview(');
const body = source.slice(start, source.indexOf('function safeAttachmentSaveName('));
function harness() {
  const state = { current: { summary: { id: 'workspace' } }, archivePreview: { workspaceId: 'workspace', attachmentId: 'archive', directory: 'docs/', entries: [] } };
  const pending = [], cancellations = [];
  let next = 0;
  const dispatch = (command, args) => {
    if (command === 'begin_desktop_archive_preview') return Promise.resolve(++next);
    if (command === 'cancel_desktop_archive_preview') { cancellations.push(args.requestId); return Promise.resolve(); }
    return new Promise((resolve, reject) => pending.push({ command, resolve, reject }));
  };
  const context = vm.createContext({ state, native: true, render() {},
    invoke: dispatch, invokePreviewOwner: (_owner, command, args) => dispatch(command, args) });
  vm.runInContext(body, context);
  return { state, pending, cancellations, open: context.openArchivePreview, entry: context.openArchiveEntryPreview, back: context.showArchiveDirectory, close: context.cancelArchivePreviewLoad };
}
const settle = () => new Promise(setImmediate);
for (const fail of [false, true]) {
  test(`closed archive does not reopen on late listing ${fail ? 'failure' : 'success'}`, async () => {
    const h = harness(); const opening = h.open('archive'); await settle();
    h.state.archivePreview = null;
    if (fail) h.pending[0].reject(new Error('synthetic')); else h.pending[0].resolve({ attachmentId: 'archive', entries: [] });
    await opening; assert.equal(h.state.archivePreview, null);
  });
  test(`returning to ZIP directory invalidates late entry ${fail ? 'failure' : 'success'}`, async () => {
    const h = harness(); const opening = h.entry('docs/report.pdf'); await settle();
    h.back('docs/');
    if (fail) h.pending[0].reject(new Error('synthetic')); else h.pending[0].resolve({ entryPath: 'docs/report.pdf' });
    await opening;
    assert.equal(h.state.archivePreview.directory, 'docs/');
    assert.equal(h.state.archivePreview.entry, null);
    assert.ok(h.cancellations.includes(1));
  });
  test(`older ZIP page ${fail ? 'failure' : 'success'} cannot overwrite newer entry`, async () => {
    const h = harness(); const first = h.entry('first.pdf', 2); await settle();
    const second = h.entry('second.txt'); await settle();
    h.pending[1].resolve({ entryPath: 'second.txt', text: 'second' }); await second;
    if (fail) h.pending[0].reject(new Error('synthetic')); else h.pending[0].resolve({ entryPath: 'first.pdf', pageNumber: 2 });
    await first; assert.equal(h.state.archivePreview.entry.entryPath, 'second.txt');
  });
}
