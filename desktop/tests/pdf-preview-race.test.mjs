import test from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import vm from 'node:vm';

const source = await readFile(new URL('../src/app.mjs', import.meta.url), 'utf8');
const start = source.includes('let pdfPreviewPreparation =') ? source.indexOf('let pdfPreviewPreparation =') : source.indexOf('async function openPdfPreview(');
const body = source.slice(start, source.indexOf('function navigatePdfPage('));
function harness({ holdBegin = false } = {}) {
  const pending = [];
  const cancelled = [];
  let nextId = 0;
  let releaseBegin;
  const state = { current: { summary: { id: 'workspace' } } };
  let renders = 0;
  const dispatch = (command, args) => {
      if (command === 'begin_desktop_pdf_preview') {
        const id = ++nextId;
        return holdBegin ? new Promise(resolve => { releaseBegin = () => resolve(id); }) : Promise.resolve(id);
      }
      if (command === 'cancel_desktop_pdf_preview') { cancelled.push(args.requestId); return Promise.resolve(); }
      return new Promise((resolve, reject) => pending.push({ resolve, reject }));
    };
  const context = vm.createContext({ native: true, state, render() { renders++; }, tauriBridge: {},
    invoke: dispatch, invokeForAuthorizedDataAreaOperation: (_bridge, command, args) => dispatch(command, args) });
  vm.runInContext(body, context);
  return { state, pending, cancelled, releaseBegin: () => releaseBegin(), close: context.cancelPdfPreviewLoad, open: context.openPdfPreview, renders: () => renders };
}
for (const fails of [false, true]) {
  test(`closed PDF stays closed after late ${fails ? 'failure' : 'success'}`, async () => {
    const h = harness();
    const opening = h.open('pdf', 1);
    await new Promise(setImmediate);
    h.state.pdfPreview = null;
    const renders = h.renders();
    if (fails) h.pending[0].reject(new Error('synthetic'));
    else h.pending[0].resolve({ attachmentId: 'pdf', pageNumber: 1 });
    await opening;
    assert.equal(h.state.pdfPreview, null);
    assert.equal(h.renders(), renders);
  });
  test(`older PDF ${fails ? 'failure' : 'success'} cannot replace newer page`, async () => {
    const h = harness();
    const old = h.open('first', 1);
    await new Promise(setImmediate);
    const latest = h.open('second', 4);
    await new Promise(setImmediate);
    h.pending[1].resolve({ attachmentId: 'second', pageNumber: 4 });
    await latest;
    if (fails) h.pending[0].reject(new Error('synthetic'));
    else h.pending[0].resolve({ attachmentId: 'first', pageNumber: 1 });
    await old;
    assert.equal(h.state.pdfPreview.attachmentId, 'second');
    assert.equal(h.state.pdfPreview.pageNumber, 4);
    assert.equal(h.state.pdfPreview.workspaceId, 'workspace');
  });
}

test('closing an active PDF requests native cancellation without waiting for rendering', async () => {
  const h = harness();
  const opening = h.open('pdf', 1);
  await new Promise(setImmediate);
  h.close();
  assert.equal(h.state.pdfPreview, null);
  assert.deepEqual(h.cancelled, [1]);
  h.pending[0].reject(new Error('cancelled'));
  await opening;
  assert.equal(h.state.pdfPreview, null);
});

test('closing before native allocation returns cancels its reservation without starting rendering', async () => {
  const h = harness({ holdBegin: true });
  const opening = h.open('pdf', 1);
  await new Promise(setImmediate);
  h.close();
  h.releaseBegin();
  await opening;
  assert.equal(h.pending.length, 0);
  assert.ok(h.cancelled.includes(1));
});
