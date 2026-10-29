import test from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import vm from 'node:vm';

const source = await readFile(new URL('../src/app.mjs', import.meta.url), 'utf8');
const deferred = () => {
  let resolve;
  const promise = new Promise(done => { resolve = done; });
  return { promise, resolve };
};

test('same image IDs in CHAT and WORK use separate reads and late CHAT result stays in CHAT', async () => {
  const body = source.slice(source.indexOf('const imagePreviewPayloadCache = new Map();'), source.indexOf('let imagePanGesture = null;'));
  const chat = { dataArea: 'CHAT', imagePreview: null };
  const work = { dataArea: 'WORK', imagePreview: null };
  const chatRead = deferred();
  const workRead = deferred();
  const calls = [];
  const context = vm.createContext({
    native: true, state: chat, render() {},
    imagePreviewNavigation: () => ({ nextId: null, previousId: null }),
    invoke(command, args, owner) {
      calls.push({ command, args, owner });
      return owner === chat ? chatRead.promise : workRead.promise;
    },
  });
  vm.runInContext(body, context);
  const first = context.openImagePreview('same-attachment', 'same-workspace', ['same-attachment']);
  context.state = work;
  const second = context.openImagePreview('same-attachment', 'same-workspace', ['same-attachment']);
  assert.equal(calls.length, 2);
  assert.deepEqual(calls.map(call => call.owner.dataArea), ['CHAT', 'WORK']);
  chatRead.resolve({ dataUrl: 'data:image/png;base64,Y2hhdA==' });
  await first;
  assert.equal(work.imagePreview.loading, true);
  workRead.resolve({ dataUrl: 'data:image/png;base64,d29yaw==' });
  await second;
  assert.equal(work.imagePreview.dataUrl, 'data:image/png;base64,d29yaw==');
});

test('closed or switched text preview ignores late native result', async () => {
  const body = source.slice(source.indexOf('async function openTextPreview('), source.indexOf('let archivePreviewPreparation ='));
  const chat = { dataArea: 'CHAT', current: { summary: { id: 'same-workspace' } }, textPreview: null };
  const work = { dataArea: 'WORK', current: { summary: { id: 'same-workspace' } }, textPreview: null };
  const firstRead = deferred();
  const secondRead = deferred();
  let reads = 0;
  const context = vm.createContext({ native: true, state: chat, render() {}, invoke(_command, _args, owner) {
    assert.equal(owner, chat);
    return ++reads === 1 ? firstRead.promise : secondRead.promise;
  } });
  vm.runInContext(body, context);
  const first = context.openTextPreview('first');
  chat.textPreview = null;
  firstRead.resolve({ text: 'closed' });
  await first;
  assert.equal(chat.textPreview, null);
  const second = context.openTextPreview('second');
  context.state = work;
  secondRead.resolve({ text: 'CHAT only' });
  await second;
  assert.equal(work.textPreview, null);
});

test('PDF allocation completed after a data-area switch cancels in its original area', async () => {
  const body = source.slice(source.indexOf('let pdfPreviewPreparation ='), source.indexOf('function navigatePdfPage('));
  const chat = { dataArea: 'CHAT', current: { summary: { id: 'same-workspace' } }, pdfPreview: null };
  const work = { dataArea: 'WORK', current: { summary: { id: 'same-workspace' } }, pdfPreview: null };
  const allocation = deferred();
  const calls = [];
  const context = vm.createContext({ native: true, state: chat, tauriBridge: {}, render() {},
    invokeForAuthorizedDataAreaOperation(_bridge, command, args, owner) {
      calls.push({ command, args, owner });
      return command === 'begin_desktop_pdf_preview' ? allocation.promise : Promise.resolve();
    },
    invoke() { throw new Error('stale PDF must not be read'); },
  });
  vm.runInContext(body, context);
  const opening = context.openPdfPreview('same-attachment');
  await new Promise(setImmediate);
  context.state = work;
  allocation.resolve(17);
  await opening;
  assert.equal(work.pdfPreview, null);
  assert.deepEqual(calls.map(call => [call.command, call.owner.dataArea]), [
    ['begin_desktop_pdf_preview', 'CHAT'], ['cancel_desktop_pdf_preview', 'CHAT'],
  ]);
  assert.equal(calls[1].args.requestId, 17);
});

test('media resume writes stay in the area where the preview was closed', async () => {
  for (const [kind, start, end, currentTime] of [
    ['video', 'let videoResumeWrite =', 'async function openAudioPreview(', 12],
    ['audio', 'let audioResumeWrite =', 'async function openTextPreview(', 7],
  ]) {
    const body = source.slice(source.indexOf(start), source.indexOf(end));
    const key = `${kind}Preview`;
    const chat = { dataArea: 'CHAT', [key]: { workspaceId: 'same-workspace', attachmentId: 'same-attachment', resumeReady: true } };
    const work = { dataArea: 'WORK', [key]: null };
    const calls = [];
    const media = { currentTime, readyState:1,isConnected:true,pause() {}, removeAttribute() {}, load() {} };
    let context;
    context = vm.createContext({ native:true,state: chat, document: { querySelector: () => media },
      render() { context.state = work; },
      invokePreviewOwner(owner, command, args) { calls.push({ owner, command, args }); return Promise.resolve(); },
    });
    vm.runInContext(body, context);
    await context[kind === 'video' ? 'closeVideoPreview' : 'closeAudioPreview']();
    assert.equal(calls.length, 1);
    assert.equal(calls[0].owner, chat);
    assert.equal(calls[0].args.args.positionMillis, currentTime * 1000);
  }
});

test('attachment export after a delayed system picker uses the original data area', async () => {
  const body = source.slice(source.indexOf('async function saveAttachmentCopy('), source.indexOf('function activePreviewForKind('));
  const chat = { dataArea: 'CHAT', status: '', error: '' };
  const work = { dataArea: 'WORK', status: '', error: '' };
  const picker = deferred();
  const calls = [];
  const context = vm.createContext({ native: true, state: chat,
    safeAttachmentSaveName: value => value,
    dialogInvoke: () => picker.promise,
    invokePreviewOwner(owner, command, args) { calls.push({ owner, command, args }); return Promise.resolve(); },
  });
  vm.runInContext(body, context);
  const saving = context.saveAttachmentCopy({ workspaceId: 'same-workspace', attachmentId: 'same-attachment', displayName: 'report.pdf' });
  context.state = work;
  picker.resolve('D:\\CodexTemp\\synthetic-report.pdf');
  assert.equal(await saving, true);
  assert.equal(calls.length, 1);
  assert.equal(calls[0].owner, chat);
  assert.equal(calls[0].command, 'export_desktop_attachment_to_selected_path');
  assert.equal(calls[0].args.args.selectedPath, 'D:\\CodexTemp\\synthetic-report.pdf');
  assert.match(chat.status, /已保存/);
  assert.equal(work.status, '');
});
