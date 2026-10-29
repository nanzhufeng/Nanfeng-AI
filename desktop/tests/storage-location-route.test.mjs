import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import test from 'node:test';
import vm from 'node:vm';

const source = readFileSync(new URL('../src/app.mjs', import.meta.url), 'utf8');
const begin = source.indexOf("  if (action === 'choose-storage-location') {");
const end = source.indexOf("  if (action === 'open-conversation-row-menu')", begin);
assert.ok(begin > 0 && end > begin);
const handler = `async function handle(action) { ${source.slice(begin, end)} }`;

function harness() {
  const chat = { dataArea: 'CHAT', pane: 'settings', settingsSection: 'privacy', privacyInventory: { storagePath: 'C:\\old' }, dialog: null };
  const work = { dataArea: 'WORK', pane: 'work', settingsSection: 'personalization', dialog: null };
  const calls = [];
  let finish;
  const context = vm.createContext({ state: chat, native: true, tauriBridge: {},
    dialogInvoke: async () => 'D:\\new',
    invokeForAuthorizedDataAreaOperation(_bridge, command, args, owner) {
      calls.push({ command, args, owner });
      return new Promise(resolve => { finish = resolve; });
    },
    loadDesktopPrivacyInventory: async () => { context.state.privacyInventory = { storagePath: 'C:\\old', pendingStoragePath: 'D:\\new' }; },
    render() {},
  });
  vm.runInContext(handler, context);
  return { chat, work, context, calls, resolve: () => finish?.(), run: () => context.handle('choose-storage-location') };
}

async function waitForWrite(h) {
  for (let attempt = 0; attempt < 10 && h.calls.length === 0; attempt += 1) {
    await new Promise(resolve => setImmediate(resolve));
  }
  assert.equal(h.calls.length, 1);
}

test('completed global path choice keeps its original owner after switching area', async () => {
  const h = harness();
  const operation = h.run();
  await waitForWrite(h);
  assert.equal(h.calls[0].owner, h.chat);
  assert.equal(h.calls[0].args.selectedPath, 'D:\\new');
  h.context.state = h.work;
  h.resolve();
  await operation;
  assert.equal(h.work.dialog, null);
  assert.equal(h.work.privacyInventory, undefined);
});

test('path choice refreshes the visible inventory after its write completes', async () => {
  const h = harness();
  const operation = h.run();
  await waitForWrite(h);
  h.resolve();
  await operation;
  assert.equal(h.chat.privacyInventory.pendingStoragePath, 'D:\\new');
  assert.match(h.chat.dialog.message, /下次启动生效/);
});

test('folder picker failure is reported without scheduling a path change', async () => {
  const h = harness();
  h.context.dialogInvoke = async () => { throw new Error('picker failed'); };
  await h.run();
  assert.equal(h.calls.length, 0);
  assert.equal(h.chat.dialog.failed, true);
});
