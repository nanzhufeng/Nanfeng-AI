import assert from 'node:assert/strict';
import test from 'node:test';
import vm from 'node:vm';
import { readFile } from 'node:fs/promises';

const source = await readFile(new URL('../src/app.mjs', import.meta.url), 'utf8');
const helperStart = source.indexOf('function captureComposerAttachmentRoute(');
const helperSource = helperStart < 0 ? '' : source.slice(helperStart, source.indexOf('async function importPastedComposerClipboardFiles('));
const clipboardSource = source.slice(source.indexOf('async function importComposerClipboardFiles('), source.indexOf('function writeChatDraft('));
const pickerSource = source.slice(source.indexOf('async function pickComposerAttachment('), source.indexOf('async function mutateConversationLifecycle('));

const area = dataArea => ({ dataArea, current: { summary: { id: 'same-workspace' } },
  selectedConversationId: 'same-conversation', composerDraft: `${dataArea} text`, composerAttachments: [],
  temporaryConversation: null });

test('a delayed pasted file cannot be imported into the area opened meanwhile', async () => {
  let releaseBytes;
  const chat = area('CHAT'), work = area('WORK'), calls = [];
  const context = vm.createContext({ state: chat, native: true, composerPresentationIdentity: {},
    clipboardFileBase64: () => new Promise(resolve => { releaseBytes = resolve; }),
    clipboardAttachmentName: () => 'synthetic.txt', render() {},
    invoke: async (command, payload) => { calls.push([command, payload]); return { id: 'asset', sha256: 'hash' }; },
    writeComposerAttachments() {},
  });
  vm.runInContext(`${helperSource}\n${clipboardSource}`, context);
  const importing = vm.runInContext("importComposerClipboardFiles([{ name: 'synthetic.txt' }])", context);
  context.state = work;
  releaseBytes('synthetic-bytes');
  await importing;
  assert.equal(calls.length, 0);
  assert.equal(work.composerAttachments.length, 0);
  assert.equal(work.status, undefined);
});

test('a picker result cannot import a CHAT selection into the same-ID WORK route', async () => {
  let releasePicker;
  const chat = area('CHAT'), work = area('WORK'), calls = [];
  const context = vm.createContext({ state: chat, native: true, composerPresentationIdentity: {},
    dialogInvoke: () => new Promise(resolve => { releasePicker = resolve; }),
    invoke: async (command, payload) => { calls.push([command, payload]); return { id: 'asset', sha256: 'hash' }; },
    writeComposerAttachments() {}, render() {},
  });
  vm.runInContext(`${helperSource}\n${pickerSource}`, context);
  const picking = vm.runInContext("pickComposerAttachment('file')", context);
  context.state = work;
  releasePicker('D:/synthetic.txt');
  await picking;
  assert.equal(calls.length, 0);
  assert.equal(work.composerAttachments.length, 0);
  assert.equal(work.status, undefined);
});

test('a normal pasted file still joins the current private draft', async () => {
  const chat = area('CHAT'), calls = [];
  const context = vm.createContext({ state: chat, native: true, composerPresentationIdentity: {},
    clipboardFileBase64: async () => 'synthetic-bytes', clipboardAttachmentName: () => 'synthetic.txt',
    invoke: async (command, payload) => { calls.push([command, payload]); return { id: 'asset', sha256: 'hash' }; },
    writeComposerAttachments() {}, render() {},
  });
  vm.runInContext(`${helperSource}\n${clipboardSource}`, context);
  await vm.runInContext("importComposerClipboardFiles([{ name: 'synthetic.txt' }])", context);
  assert.equal(calls[0][0], 'import_desktop_conversation_clipboard_attachment');
  assert.equal(calls[0][1].args.workspaceId, 'same-workspace');
  assert.equal(chat.composerAttachments[0].id, 'asset');
  assert.match(chat.status, /私有复制/);
});

test('a late picker import cannot attach to a different conversation in the same area', async () => {
  let releaseImport;
  const chat = area('CHAT');
  const context = vm.createContext({ state: chat, native: true, composerPresentationIdentity: {},
    dialogInvoke: async () => 'D:/synthetic.txt',
    invoke: () => new Promise(resolve => { releaseImport = resolve; }),
    writeComposerAttachments() {}, render() {},
  });
  vm.runInContext(`${helperSource}\n${pickerSource}`, context);
  const picking = vm.runInContext("pickComposerAttachment('file')", context);
  await new Promise(resolve => setImmediate(resolve));
  chat.selectedConversationId = 'another-conversation';
  releaseImport({ id: 'asset', sha256: 'hash' });
  await picking;
  assert.equal(chat.composerAttachments.length, 0);
  assert.equal(chat.status, undefined);
});
