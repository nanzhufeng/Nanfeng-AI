import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import vm from 'node:vm';

const source = readFileSync(new URL('../src/app.mjs', import.meta.url), 'utf8');
const start = source.indexOf("} else if (action === 'load-account-cloud-documents') {");
const body = source.slice(start + "} else if (action === 'load-account-cloud-documents') {".length,
  source.indexOf("} else if (action === 'choose-selected-local-sync-start')", start));

test('available cloud rows are displayed without waiting for account metadata refresh', async () => {
  let releaseAccount;
  const account = new Promise(resolve => { releaseAccount = resolve; });
  const row = { id: 'conversation-safe', title: '原始标题', workspaceId: 'w' };
  const state = { cloudConversations: [], accountSync: {}, pane: 'settings' };
  const context = vm.createContext({ state, cloudListReadGeneration: 0,
    beginAccountSyncProgress() {}, render() {}, waitForAccountSyncProgressPaint: async () => {},
    invoke: async () => ({ restored: [{ status: 'ALREADY_LOCAL' }], cloudListPresentationPresent: true }),
    cloudRowsFromLocalEntries: async () => [row], loadDesktopAccountSync: () => account,
    mergeCloudConversationList: (_, rows) => rows, persistCloudConversationCache() {},
    notifyAccountSync() {}, completeAccountSyncProgress: async () => {}, refresh: async () => {},
  });
  const running = vm.runInContext(`(async () => {${body}})()`, context);
  await new Promise(resolve => setImmediate(resolve));
  try {
    assert.equal(state.cloudConversations[0]?.title, '原始标题');
    assert.equal(state.pane, 'chat');
  } finally { releaseAccount(); await running; }
});
