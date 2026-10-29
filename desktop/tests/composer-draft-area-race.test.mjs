import assert from 'node:assert/strict';
import test from 'node:test';
import vm from 'node:vm';
import { readFile } from 'node:fs/promises';
import { readComposerDraftRoute } from '../src/composer-draft-recovery.mjs';

const source = await readFile(new URL('../src/app.mjs', import.meta.url), 'utf8');
const loader = source.slice(source.indexOf('async function loadOrdinaryComposerDraft('),
  source.indexOf('function writeComposerAttachments('));

function area(dataArea, text) {
  return {
    dataArea, current: { summary: { id: 'same-workspace-id' } }, selectedConversationId: null,
    temporaryConversation: null, composerDraft: text, composerAttachments: [],
    pendingNewConversationModelId: null,
    conversationPreferences: { revision: 0, toneOverride: null, webSearchOverride: null },
  };
}

function deferred() {
  let resolve;
  let reject;
  const promise = new Promise((yes, no) => { resolve = yes; reject = no; });
  return { promise, resolve, reject };
}

function harness({ flush = async () => {}, read = async () => null } = {}) {
  const chat = area('CHAT', 'CHAT current');
  const work = area('WORK', 'WORK current');
  const calls = [];
  const context = vm.createContext({
    state: chat, composerDraftRouteGeneration: 0, desktopEntryRouteGeneration:0, readComposerDraftRoute,
    ordinaryComposerDraftArgs() {
      return { workspaceId: context.state.current.summary.id,
        conversationId: context.state.selectedConversationId };
    },
    flushOrdinaryComposerDraft: flush,
    invoke: (command, args, owner) => {
      calls.push({ command, args, owner });
      return read(command, args, owner);
    },
    armNewConversationDraftExpiry() {},
  });
  vm.runInContext(`${loader}\nglobalThis.loadDraft = loadOrdinaryComposerDraft;`, context);
  return { chat, work, calls, context, load: () => context.loadDraft() };
}

test('switching areas while source save is pending never starts a draft read in the destination', async () => {
  const pending = deferred();
  const h = harness({ flush: () => pending.promise });
  const loading = h.load();
  h.context.state = h.work;
  h.context.composerDraftRouteGeneration += 1;
  pending.resolve();
  await loading;
  assert.equal(h.calls.length, 0);
  assert.equal(h.work.composerDraft, 'WORK current');
});

test('a late CHAT read keeps its original owner and cannot overwrite the same ID in WORK', async () => {
  const pending = deferred();
  const started = deferred();
  const h = harness({ read: () => { started.resolve(); return pending.promise; } });
  const loading = h.load();
  await started.promise;
  assert.equal(h.calls.length, 1);
  assert.equal(h.calls[0].owner, h.chat);
  h.context.state = h.work;
  h.context.composerDraftRouteGeneration += 1;
  pending.resolve({ text: 'late CHAT text', attachments: [] });
  await loading;
  assert.equal(h.work.composerDraft, 'WORK current');
});

test('a stale area error after switching is discarded instead of becoming a WORK error', async () => {
  const pending = deferred();
  const started = deferred();
  const h = harness({ read: () => { started.resolve(); return pending.promise; } });
  const loading = h.load();
  await started.promise;
  h.context.state = h.work;
  h.context.composerDraftRouteGeneration += 1;
  pending.reject(Object.assign(new Error('late CHAT receipt'), { code: 'STALE_DATA_AREA' }));
  await loading;
  assert.equal(h.work.error, undefined);
  assert.equal(h.work.composerDraft, 'WORK current');
});

test('new input while a draft read is pending keeps the new text', async () => {
  const pending = deferred();
  const started = deferred();
  const h = harness({ read: () => { started.resolve(); return pending.promise; } });
  const loading = h.load();
  await started.promise;
  h.chat.composerDraft = 'new input';
  h.context.composerDraftRouteGeneration += 1;
  pending.resolve({ text: 'old saved draft', attachments: [] });
  await loading;
  assert.equal(h.chat.composerDraft, 'new input');
});

test('a failed old read is discarded after conversation navigation, including a same-ID return',async()=>{
 for(const returnToSame of [false,true]){
  const pending=deferred(),started=deferred();const h=harness({read:()=>{started.resolve();return pending.promise;}});
  const loading=h.load();await started.promise;
  h.chat.selectedConversationId='other';h.context.desktopEntryRouteGeneration++;
  if(returnToSame){h.chat.selectedConversationId=null;h.context.desktopEntryRouteGeneration++;}
  pending.reject(new Error('original read failed'));
  await assert.doesNotReject(loading);assert.equal(h.chat.error,undefined);assert.equal(h.chat.composerDraft,'CHAT current');
 }
});

test('a current failed read still rejects after newer typing so the active caller can explain unsaved input',async()=>{
 const pending=deferred(),started=deferred();const h=harness({read:()=>{started.resolve();return pending.promise;}});
 const loading=h.load();await started.promise;h.chat.composerDraft='newer current text';h.context.composerDraftRouteGeneration++;
 pending.reject(new Error('current draft read failed'));await assert.rejects(loading,/current draft read failed/);
 assert.equal(h.chat.composerDraft,'newer current text');
});
