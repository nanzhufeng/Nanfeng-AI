import test from 'node:test';
import assert from 'node:assert/strict';
import vm from 'node:vm';
import { readFile } from 'node:fs/promises';

const source = await readFile(new URL('../src/app.mjs', import.meta.url), 'utf8');
const between = (a, b) => source.slice(source.indexOf(a), source.indexOf(b, source.indexOf(a)));
const helpers = between('function captureComposerAttachmentRoute(', 'async function importPastedComposerClipboardFiles(');
const reset = between('function resetComposerPresentation(', 'function restoreOrdinaryComposerDraft(');
const clipboard = between('async function importComposerClipboardFiles(', 'function writeChatDraft(');
const picker = between('async function pickComposerAttachment(', 'async function mutateConversationLifecycle(');
const tick = () => new Promise(setImmediate);

function harness({ pickerFailure = false } = {}) {
  const state = { dataArea: 'CHAT', current: { summary: { id: 'workspace' } },
    selectedConversationId: 'same-conversation', temporaryConversation: null,
    composerDraft: 'original text', composerAttachments: [] };
  const pending = [], calls = [];
  let rejectPicker;
  const context = vm.createContext({ state, native: true,
    armNewConversationDraftExpiry() {}, render() {}, handleExpiredTemporary() { return false; },
    clipboardFileBase64: async () => 'synthetic-bytes', clipboardAttachmentName: () => 'synthetic.txt',
    dialogInvoke: pickerFailure ? () => new Promise((_, reject) => { rejectPicker = reject; }) : async () => 'D:/synthetic.txt',
    invoke(command, args) { calls.push(command); return new Promise(resolve => pending.push(resolve)); },
    writeComposerAttachments() { vm.runInContext('composerDraftRouteGeneration += 1', context); },
  });
  vm.runInContext(`let composerDraftRouteGeneration=0;let composerPresentationIdentity={};${helpers}\n${reset}\n${clipboard}\n${picker}`, context);
  return { state, pending, calls, context, rejectPicker: () => rejectPicker(new Error('synthetic picker failure')),
    reset() { vm.runInContext('resetComposerPresentation()', context); state.composerDraft = 'next draft'; state.status = 'new status'; state.error = 'new error'; } };
}

for (const operation of ['picker', 'clipboard']) test(`late ${operation} import cannot join a replacement draft in the same conversation`, async () => {
  const h = harness();
  const loading = vm.runInContext(operation === 'picker' ? "pickComposerAttachment('file')" : "importComposerClipboardFiles([{name:'synthetic.txt'}])", h.context);
  await tick();
  assert.equal(h.pending.length, 1);
  h.reset();
  h.pending[0]({ id: 'old-file', sha256: 'old-hash' });
  await loading;
  assert.equal(h.state.composerAttachments.length, 0);
  assert.equal(h.state.composerDraft, 'next draft');
  assert.equal(h.state.status, 'new status');
  assert.equal(h.state.error, 'new error');
});

test('a rejected old picker cannot reject into the replacement draft presentation', async () => {
  const h = harness({ pickerFailure: true });
  const loading = vm.runInContext("pickComposerAttachment('file')", h.context);
  h.reset();
  h.rejectPicker();
  await loading;
  assert.equal(h.state.error, 'new error');
  assert.equal(h.state.status, 'new status');
  assert.equal(h.calls.length, 0);
});

test('typing in the same draft does not invalidate a pending attachment', async () => {
  const h = harness();
  const loading = vm.runInContext("pickComposerAttachment('file')", h.context);
  await tick();
  h.state.composerDraft = 'edited text';
  vm.runInContext('composerDraftRouteGeneration += 1', h.context);
  h.pending[0]({ id: 'current-file', sha256: 'current-hash' });
  await loading;
  assert.equal(h.state.composerAttachments[0].id, 'current-file');
  assert.equal(h.state.composerDraft, 'edited text');
});

test('saving each pasted attachment does not invalidate later files in the same gesture', async () => {
  const h = harness();
  const loading = vm.runInContext("importComposerClipboardFiles([{name:'a.txt'},{name:'b.txt'}])", h.context);
  await tick();
  h.pending[0]({ id: 'a', sha256: 'hash-a' });
  await tick();
  assert.equal(h.pending.length, 2);
  h.pending[1]({ id: 'b', sha256: 'hash-b' });
  await loading;
  assert.deepEqual(Array.from(h.state.composerAttachments, file => file.id), ['a', 'b']);
});
