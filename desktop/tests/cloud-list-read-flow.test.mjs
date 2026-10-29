import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import vm from 'node:vm';
import { cloudReadFeedback } from '../src/cloud-read-feedback.mjs';

const source = readFileSync(new URL('../src/app.mjs', import.meta.url), 'utf8');
const start = source.indexOf("} else if (action === 'load-account-cloud-documents') {");
const body = source.slice(start + "} else if (action === 'load-account-cloud-documents') {".length,
  source.indexOf("} else if (action === 'choose-selected-local-sync-start')", start));
const dedupe = source.slice(source.indexOf('function dedupeCloudConversationEntries('), source.indexOf('function readCloudConversationCache('));
const readOwnership = dedupe + source.slice(source.indexOf('const cloudListReadGenerations = new WeakMap();'),
  source.indexOf('let cloudOpenAreaGeneration = 0;'));
const accountInvalidation = source.slice(source.indexOf('function invalidateAccountCloudLists()'),
  source.indexOf('function selectedBackupAreaState()'));

test('a failed workspace projection preserves successful rows and reports display failure', async () => {
  const state = { cloudConversations: [], accountSync: {}, pane: 'settings', dataArea: 'CHAT' };
  const progress = [];
  const context = vm.createContext({ cloudReadFeedback, state, owner: state,
    beginAccountSyncProgress() {}, render() {}, waitForAccountSyncProgressPaint: async () => {},
    invoke: async () => ({ restored: [{ workspaceId: 'good', conversationId: 'c1', status: 'ALREADY_LOCAL' },
      { workspaceId: 'bad', conversationId: 'c2', status: 'RESTORED_AS_NEW_WORKSPACE' }], cloudListPresentationPresent: true }),
    cloudRowsFromLocalEntries: async () => [{ id: 'c1', workspaceId: 'good', messages: [{ text: '完整正文' }] }],
    loadDesktopAccountSync: async () => ({}), mergeCloudConversationList: (_, rows) => rows,
    persistCloudConversationCache() {}, notifyAccountSync() {},
    completeAccountSyncProgress: async (message, kind) => { progress.push({ message, kind }); },
  });
  vm.runInContext(readOwnership, context);
  await vm.runInContext(`(async () => {${body}})()`, context);
  assert.equal(state.cloudConversations.length, 1);
  assert.equal(state.cloudConversations[0].messages[0].text, '完整正文');
  assert.equal(progress[0].kind, 'attention');
  assert.match(progress[0].message, /1 个已恢复会话未能显示/);
  assert.doesNotMatch(progress[0].message, /未能恢复/);
});

test('available cloud rows are displayed without waiting for account metadata refresh', async () => {
  let releaseAccount;
  const account = new Promise(resolve => { releaseAccount = resolve; });
  const row = { id: 'conversation-safe', title: '原始标题', workspaceId: 'w' };
  const state = { cloudConversations: [], accountSync: {}, pane: 'settings', dataArea: 'CHAT' };
  const context = vm.createContext({ cloudReadFeedback, state, owner: state, cloudListReadGeneration: 0,
    beginAccountSyncProgress() {}, render() {}, waitForAccountSyncProgressPaint: async () => {},
    invoke: async () => ({ restored: [{ status: 'ALREADY_LOCAL' }], cloudListPresentationPresent: true }),
    cloudRowsFromLocalEntries: async () => [row], loadDesktopAccountSync: () => account,
    mergeCloudConversationList: (_, rows) => rows, persistCloudConversationCache() {},
    notifyAccountSync() {}, completeAccountSyncProgress: async () => {}, refresh: async () => {},
  });
  vm.runInContext(readOwnership, context);
  const running = vm.runInContext(`(async () => {${body}})()`, context);
  await new Promise(resolve => setImmediate(resolve));
  try {
    assert.equal(state.cloudConversations[0]?.title, '原始标题');
    assert.equal(state.pane, 'chat');
  } finally { releaseAccount(); await running; }
});

test('WORK cloud read keeps WORK navigation and reports partial recovery as attention', async () => {
  const state = { cloudConversations: [], accountSync: {}, pane: 'settings', dataArea: 'WORK' };
  const progress = [];
  const context = vm.createContext({ cloudReadFeedback, state, owner: state, cloudListReadGeneration: 0,
    beginAccountSyncProgress() {}, render() {}, waitForAccountSyncProgressPaint: async () => {},
    invoke: async () => ({
      restored: [{ status: 'UPDATED_LOCAL' }],
      failedCount: 1,
      failureReasons: { TITLE_CONFLICT: 1 },
      cloudListPresentationPresent: true,
    }),
    cloudRowsFromLocalEntries: async () => [{ id: 'conversation-safe', title: '本机标题', workspaceId: 'w' }],
    loadDesktopAccountSync: async () => ({}),
    mergeCloudConversationList: (_, rows) => rows, persistCloudConversationCache() {},
    notifyAccountSync() {}, completeAccountSyncProgress: async (message, kind) => { progress.push({ message, kind }); },
    refresh: async () => {},
  });
  vm.runInContext(readOwnership, context);
  await vm.runInContext(`(async () => {${body}})()`, context);
  assert.equal(state.pane, 'work');
  assert.equal(state.cloudConversations[0]?.title, '本机标题');
  assert.equal(progress[0]?.kind, 'attention');
  assert.match(progress[0]?.message, /1 个标题存在冲突/);
  assert.doesNotMatch(progress[0]?.message, /0 个未能恢复/);
});

test('CHAT and WORK cloud reads retain their own result after switching back', async () => {
  const chat = { cloudConversations: [], accountSync: {}, pane: 'settings', dataArea: 'CHAT' };
  const work = { cloudConversations: [], accountSync: {}, pane: 'settings', dataArea: 'WORK' };
  let releaseChat;
  const chatReceipt = new Promise(resolve => { releaseChat = resolve; });
  let calls = 0;
  const context = vm.createContext({ cloudReadFeedback, state: chat,
    beginAccountSyncProgress() {}, render() {}, waitForAccountSyncProgressPaint: async () => {},
    invoke: async () => ++calls === 1 ? chatReceipt : { restored: [{ conversationId: 'work-row' }] },
    cloudRowsFromLocalEntries: async rows => rows.map(item => ({ id: item.conversationId, workspaceId: 'safe-workspace' })),
    loadDesktopAccountSync: async () => ({}), mergeCloudConversationList: (_, rows) => rows,
    persistCloudConversationCache() {}, notifyAccountSync() {},
    completeAccountSyncProgress: async () => {},
  });
  vm.runInContext(readOwnership, context);
  const runRead = () => vm.runInContext(`(async owner => {${body}})(state)`, context);
  const pendingChat = runRead();
  await new Promise(resolve => setImmediate(resolve));
  assert.equal(calls, 1);
  context.state = work;
  await runRead();
  assert.equal(work.cloudConversations[0]?.id, 'work-row');
  context.state = chat;
  releaseChat({ restored: [{ conversationId: 'chat-row' }] });
  await pendingChat;
  assert.equal(chat.cloudConversations[0]?.id, 'chat-row');
});

test('account change clears both cloud lists and rejects an older read receipt', async () => {
  const chat = { cloudConversations: [{ id: 'old-chat' }], accountSync: {}, pane: 'settings', dataArea: 'CHAT' };
  const work = { cloudConversations: [{ id: 'old-work' }], accountSync: {}, pane: 'settings', dataArea: 'WORK' };
  let releaseRead;
  const receipt = new Promise(resolve => { releaseRead = resolve; });
  const context = vm.createContext({ cloudReadFeedback, state: chat, dataAreaStates: new Map([['CHAT', chat], ['WORK', work]]),
    beginAccountSyncProgress() {}, render() {}, waitForAccountSyncProgressPaint: async () => {},
    invoke: () => receipt,
    cloudRowsFromLocalEntries: async () => [{ id: 'late-row', workspaceId: 'safe-workspace' }],
    loadDesktopAccountSync: async () => ({}), mergeCloudConversationList: (_, rows) => rows,
    persistCloudConversationCache() {}, notifyAccountSync() {}, completeAccountSyncProgress: async () => {},
  });
  vm.runInContext(readOwnership + accountInvalidation, context);
  const running = vm.runInContext(`(async owner => {${body}})(state)`, context);
  await new Promise(resolve => setImmediate(resolve));
  vm.runInContext('invalidateAccountCloudLists()', context);
  releaseRead({ restored: [{ conversationId: 'late-row' }] });
  await running;
  assert.equal(chat.cloudConversations.length, 0);
  assert.equal(work.cloudConversations.length, 0);
});
