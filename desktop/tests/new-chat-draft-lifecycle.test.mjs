import assert from 'node:assert/strict';
import nodeTest from 'node:test';
import { readFile } from 'node:fs/promises';
import vm from 'node:vm';
import { createDraftWriteQueue } from '../src/draft-write-queue.mjs';
import { readComposerDraftRoute } from '../src/composer-draft-recovery.mjs';

const source = await readFile(new URL('../src/app.mjs', import.meta.url), 'utf8');
const start = source.indexOf('const ordinaryComposerDraftQueue = createDraftWriteQueue(');
const end = source.indexOf('function writeComposerAttachments(', start);
assert.ok(start >= 0 && end > start, 'the Desktop composer route owner must remain present');
const owner = source.slice(start, end);
const modelOwner=source.slice(source.indexOf('function newConversationModelId('),source.indexOf('\nasync function updateP6GConversationOverride('));
const test=(name,run)=>nodeTest(name,{timeout:5000},run);

function session(records = new Map(), { readDraft, writeDraft } = {}) {
  const save = (area, input) => {
    const key = [area, input.workspaceId, input.conversationId ?? null].join(':');
    const draft = { text: input.text, attachments: input.attachmentIds.map(id => ({ id })), modelId: input.modelId,
      toneOverride: input.toneOverride, webSearchOverride: input.webSearchOverride,
      expiresAtMs: input.conversationId ? null : Date.now() + 3_600_000 };
    records.set(key, draft);
    return writeDraft ? writeDraft(key, draft, records) : draft;
  };
  const context = vm.createContext({
    native: true, createDraftWriteQueue, readComposerDraftRoute, structuredClone, desktopEntryRouteGeneration:0,
    setTimeout: callback => { context.timer = callback; return 1; }, clearTimeout() { context.timer = null; }, render() {},
    state: {
      dataArea: 'CHAT', current: { summary: { id: 'same-workspace' } },
      selectedConversationId: null, temporaryConversation: null,
      composerDraft: '', composerAttachments: [], pendingNewConversationModelId: null,
      conversationPreferences: { revision: 0, toneOverride: null, webSearchOverride: null },
    },
    tauriBridge: { invoke: async (command, payload, options) => {
      assert.equal(command, 'save_desktop_conversation_draft');
      return save(options.headers['x-nanfeng-data-area'], payload.args);
    } },
    invoke: async (command, args) => {
      const input = args?.args || args;
      const key = [context.state.dataArea, input.workspaceId, input.conversationId ?? null].join(':');
      if (command === 'save_desktop_conversation_draft') return save(context.state.dataArea, input);
      if (command === 'read_desktop_conversation_draft') return readDraft
        ? readDraft(key, records)
        : records.get(key) || null;
      throw new Error(`unexpected command: ${command}`);
    },
  });
  vm.runInContext(`${modelOwner}\n${owner}\nglobalThis.composer = {
    scheduleOrdinaryComposerDraft, flushOrdinaryComposerDraft, loadOrdinaryComposerDraft,
    resetNewConversationComposer, resetComposerPresentation,
  };`, context);
  return { state: context.state, context, records, composer: context.composer };
}

function deferred() {
  let resolve;
  const promise = new Promise(yes => { resolve = yes; });
  return { promise, resolve };
}

test('input after the switch draft flush stays in CHAT when its timer runs in WORK', async () => {
  const current = session();
  current.state.composerDraft = 'CHAT before switch';
  current.composer.scheduleOrdinaryComposerDraft();
  await current.composer.flushOrdinaryComposerDraft();
  // switchDataArea next awaits temporary/local-settings writes before changing
  // state. The still-mounted composer can receive input during that await.
  current.state.composerDraft = 'CHAT final input during settings flush';
  current.composer.scheduleOrdinaryComposerDraft();
  const work = { ...current.state, dataArea: 'WORK', composerDraft: 'WORK own draft' };
  current.context.state = work;
  current.context.timer();
  await current.composer.flushOrdinaryComposerDraft();
  assert.equal(current.records.get('CHAT:same-workspace:')?.text, 'CHAT final input during settings flush');
  assert.equal(current.records.has('WORK:same-workspace:'), false);
  assert.equal(work.composerDraft, 'WORK own draft');
});

test('changing only web search while a new draft is loading preserves its saved text and attachment', async () => {
  const key = 'CHAT:same-workspace:';
  const saved = {
    text: '未发送正文', attachments: [{ id: 'synthetic-attachment' }],
    modelId: 'DEEPSEEK_V4_PRO', toneOverride: 'professional', webSearchOverride: false,
  };
  const records = new Map([[key, saved]]);
  const readStarted = deferred();
  const readRelease = deferred();
  const current = session(records, { readDraft: async () => {
    readStarted.resolve();
    await readRelease.promise;
    return saved;
  } });
  current.state.selectedConversationId = null;
  current.composer.resetNewConversationComposer();
  await readStarted.promise;
  current.state.conversationPreferences.webSearchOverride = true;
  current.composer.scheduleOrdinaryComposerDraft('web');
  readRelease.resolve();
  await current.composer.flushOrdinaryComposerDraft();
  assert.equal(records.get(key)?.text, saved.text);
  assert.deepEqual(records.get(key)?.attachments, saved.attachments);
  assert.equal(records.get(key)?.webSearchOverride, true);
});

test('adding an attachment while the draft is loading retains the saved attachment and text', async () => {
  const key = 'CHAT:same-workspace:';
  const saved = { text: '仍未发送', attachments: [{ id: 'old-asset' }], modelId: 'DEEPSEEK_V4_PRO',
    toneOverride: 'professional', webSearchOverride: false };
  const records = new Map([[key, saved]]);
  const started = deferred();
  const released = deferred();
  const current = session(records, { readDraft: async () => {
    started.resolve();
    await released.promise;
    return saved;
  } });
  current.composer.resetNewConversationComposer();
  await started.promise;
  current.state.composerAttachments = [{ id: 'new-asset' }];
  current.composer.scheduleOrdinaryComposerDraft('attachments');
  released.resolve();
  await current.composer.flushOrdinaryComposerDraft();
  assert.equal(records.get(key).text, saved.text);
  assert.deepEqual(records.get(key).attachments.map(item => item.id), ['old-asset', 'new-asset']);
  assert.equal(records.get(key).webSearchOverride, false);
});

test('removing an attachment ID during a read does not delete the draft text', async () => {
  const key = 'CHAT:same-workspace:';
  const saved = { text: '仍未发送', attachments: [{ id: 'old-asset' }], modelId: null,
    toneOverride: null, webSearchOverride: false };
  const records = new Map([[key, saved]]);
  const started = deferred();
  const released = deferred();
  const current = session(records, { readDraft: async () => {
    started.resolve();
    await released.promise;
    return saved;
  } });
  current.composer.resetNewConversationComposer();
  await started.promise;
  current.state.composerAttachments = [];
  current.composer.scheduleOrdinaryComposerDraft('attachments', 'old-asset');
  released.resolve();
  await current.composer.flushOrdinaryComposerDraft();
  assert.equal(records.get(key).text, saved.text);
  assert.deepEqual(records.get(key).attachments, []);
});

test('a pending CHAT choice edit still saves to CHAT after WORK becomes visible', async () => {
  const chatKey = 'CHAT:same-workspace:';
  const workKey = 'WORK:same-workspace:';
  const saved = { text: 'CHAT 未发送', attachments: [{ id: 'chat-asset' }], modelId: null,
    toneOverride: null, webSearchOverride: false };
  const records = new Map([[chatKey, saved], [workKey, { ...saved, text: 'WORK 未发送' }]]);
  const started = deferred();
  const released = deferred();
  const current = session(records, { readDraft: async () => {
    started.resolve();
    await released.promise;
    return saved;
  } });
  current.composer.resetNewConversationComposer();
  await started.promise;
  current.state.conversationPreferences.webSearchOverride = true;
  current.composer.scheduleOrdinaryComposerDraft('web');
  current.context.state = { ...current.state, dataArea: 'WORK', composerDraft: 'WORK 未发送' };
  released.resolve();
  await current.composer.flushOrdinaryComposerDraft();
  assert.equal(records.get(chatKey).text, 'CHAT 未发送');
  assert.equal(records.get(chatKey).webSearchOverride, true);
  assert.equal(records.get(workKey).text, 'WORK 未发送');
  assert.equal(records.get(workKey).webSearchOverride, false);
});

test('a second edit during the merged save keeps recovered attachments in the next snapshot', async () => {
  const key = 'CHAT:same-workspace:';
  const saved = { text: '原稿', attachments: [{ id: 'old-asset' }], modelId: null,
    toneOverride: null, webSearchOverride: false };
  const records = new Map([[key, saved]]);
  const readStarted = deferred();
  const readRelease = deferred();
  const writeStarted = deferred();
  const writeRelease = deferred();
  let writes = 0;
  const current = session(records, {
    readDraft: async () => {
      readStarted.resolve();
      await readRelease.promise;
      return saved;
    },
    writeDraft: async (_key, draft) => {
      if (++writes === 1) { writeStarted.resolve(); await writeRelease.promise; }
      return draft;
    },
  });
  current.composer.resetNewConversationComposer();
  await readStarted.promise;
  current.state.conversationPreferences.webSearchOverride = true;
  current.composer.scheduleOrdinaryComposerDraft('web');
  readRelease.resolve();
  await writeStarted.promise;
  assert.equal(current.state.composerDraft, '原稿');
  assert.deepEqual(current.state.composerAttachments.map(item => item.id), ['old-asset']);
  current.state.composerDraft = '后续编辑';
  current.composer.scheduleOrdinaryComposerDraft('text');
  writeRelease.resolve();
  await current.composer.flushOrdinaryComposerDraft();
  assert.equal(records.get(key).text, '后续编辑');
  assert.deepEqual(records.get(key).attachments.map(item => item.id), ['old-asset']);
  assert.equal(records.get(key).webSearchOverride, true);
});

test('new chat text survives a selected conversation, UI restart, and stays in its own data area', async () => {
  const first = session();
  first.state.composerDraft = '  未发送\n第二行';
  first.composer.scheduleOrdinaryComposerDraft();
  await first.composer.flushOrdinaryComposerDraft();

  first.state.selectedConversationId = 'existing';
  first.composer.resetComposerPresentation();
  await first.composer.loadOrdinaryComposerDraft();
  assert.equal(first.state.composerDraft, '');

  first.state.selectedConversationId = null;
  first.composer.resetNewConversationComposer();
  await first.composer.loadOrdinaryComposerDraft();
  assert.equal(first.state.composerDraft, '  未发送\n第二行');

  const restarted = session(first.records);
  await restarted.composer.loadOrdinaryComposerDraft();
  assert.equal(restarted.state.composerDraft, '  未发送\n第二行');
  await restarted.composer.flushOrdinaryComposerDraft();
  restarted.context.state = {
    ...restarted.state, dataArea: 'WORK', composerDraft: '', composerAttachments: [],
  };
  await restarted.composer.loadOrdinaryComposerDraft();
  assert.equal(restarted.context.state.composerDraft, '');
  assert.equal(first.records.get('CHAT:same-workspace:')?.text, '  未发送\n第二行');
});
