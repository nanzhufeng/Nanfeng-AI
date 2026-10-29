import { invokeForAuthorizedDataAreaOperation } from '../src/data-area-invoke.mjs';
import test from 'node:test';
import assert from 'node:assert/strict';
import vm from 'node:vm';
import { readFile } from 'node:fs/promises';

const source = (await readFile(new URL('../src/app.mjs', import.meta.url), 'utf8')).replaceAll('\r\n', '\n');
const start = source.indexOf("  if (action === 'context-menu-cancel-sync') {");
const end = source.indexOf("  if (action === 'context-menu-pin') {", start);
assert.ok(start > 0 && end > start);
const helper=source.slice(source.indexOf('const cloudUnlinkPending = new WeakMap();'),source.indexOf('async function confirmConversationBatchDelete('));
const tick = () => new Promise(resolve => setImmediate(resolve));
function deferred() {
  let resolve, reject;
  const promise = new Promise((nextResolve, nextReject) => { resolve = nextResolve; reject = nextReject; });
  return { promise, resolve, reject };
}
function fixture() {
  const chat = { dataArea: 'CHAT', current: { summary: { id: 'same-workspace' } },
    cloudConversations: [{ workspaceId: 'same-workspace', id: 'same-conversation' }, { workspaceId: 'other-workspace', id: 'same-conversation' }],
    contextMenu: { id: 'same-conversation' }, accountSync: { selectedConversationCount: 1 }, status: '', error: '' };
  const work = { dataArea: 'WORK', current: { summary: { id: 'same-workspace' } },
    cloudConversations: [{ workspaceId: 'same-workspace', id: 'same-conversation' }],
    contextMenu: { id: 'later-work-menu' }, accountSync: { selectedConversationCount: 1 }, status: 'later WORK status', error: 'later WORK error' };
  const calls = [];
  const deletion = deferred();
  const context = vm.createContext({ state: chat, native: true,invokeForAuthorizedDataAreaOperation,
    captureCloudSidebarWorkspace: () => null,
    ensureConversationActionWorkspace: async () => true,
    restoreCloudSidebarWorkspace: async () => {},
    render() {}, advanceCloudListRead() {}, persistCloudConversationCache() {},
    loadDesktopAccountSync: async owner => { owner.accountSync.selectedConversationCount = 0; },
    tauriBridge:{invoke:async(command,args,options)=>{
      calls.push({command,args,area:options.headers['x-nanfeng-data-area']});return deletion.promise;
    }},
  });
  vm.runInContext(helper+`globalThis.cancel = async target => { const action = 'context-menu-cancel-sync'; ${source.slice(start, end)} };`, context);
  return { context, chat, work, calls, deletion, cancel: () => context.cancel({ dataset: { id: 'same-conversation' } }) };
}

test('late cancellation receipt after area switch cannot write an error or clear WORK state', async () => {
  const f = fixture();
  const before = JSON.stringify(f.work);
  const pending = f.cancel(); await tick();
  f.context.state = f.work; f.deletion.resolve({ status: 'CANCELLED' }); await pending;
  assert.equal(JSON.stringify(f.work), before);
  assert.equal(f.calls.length, 1); assert.equal(f.calls[0].area, 'CHAT');
});

test('area switch while cancellation account refresh waits preserves later WORK feedback and rows', async () => {
  const f = fixture(); const refresh = deferred();
  f.context.loadDesktopAccountSync = async () => refresh.promise;
  const before = JSON.stringify(f.work);
  const pending = f.cancel(); await tick(); f.deletion.resolve({ status: 'CANCELLED' }); await tick();
  f.context.state = f.work; refresh.reject(Object.assign(new Error('原区刷新已失效'), { code: 'STALE_DATA_AREA' }));
  await pending; assert.equal(JSON.stringify(f.work), before);
});

test('current cancellation removes only its exact cloud row and preserves local conversation', async () => {
  const f = fixture(); const current = f.chat.current;
  const pending = f.cancel(); await tick(); f.deletion.resolve({ status: 'CANCELLED' }); await pending;
  assert.equal(f.chat.current, current);
  assert.equal(f.chat.cloudConversations.length, 1);
  assert.equal(f.chat.cloudConversations[0].workspaceId, 'other-workspace');
  assert.match(f.chat.status, /云端副本已删除，本地对话保留/); assert.equal(f.chat.error, '');
});

test('current cancellation failure stays visible and retains selection and cloud row', async () => {
  const f = fixture(); const before = JSON.stringify(f.chat.cloudConversations);
  const pending = f.cancel(); await tick(); f.deletion.reject(new Error('synthetic delete unavailable')); await pending;
  assert.match(f.chat.error, /取消同步未完成/);
  assert.equal(JSON.stringify(f.chat.cloudConversations), before);
  assert.equal(f.chat.accountSync.selectedConversationCount, 1);
});
