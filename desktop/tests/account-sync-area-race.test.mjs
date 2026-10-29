import assert from 'node:assert/strict';
import test from 'node:test';
import { readFile } from 'node:fs/promises';
import vm from 'node:vm';

test('failed login compensation preserves the owner error without claiming unchanged credentials', async () => {
  const source = (await readFile(new URL('../src/app.mjs', import.meta.url), 'utf8')).replaceAll('\r\n', '\n');
  const anchor = source.indexOf("if (!['show-google-login-requirements', 'sign-in-google-account'");
  const start = source.lastIndexOf("app.addEventListener('click', event => {", anchor);
  const end = source.indexOf('}, true);', anchor) + '}, true);'.length;
  let handler;
  const owner = { dataArea: 'CHAT', accountSync: { email: 'original@example.invalid' }, status: '', error: '' };
  const context = vm.createContext({
    app: { addEventListener(_type, callback) { handler = callback; } },
    state: owner, native: true, accountSignInPending: false, accountSignInToken: 0,
    render() {}, accountSyncErrorMessage: error => String(error),
    invoke: async () => { throw new Error('账号提交失败，原会话恢复未完成，请保留本机数据并重新登录'); },
  });
  vm.runInContext(source.slice(start, end), context);
  handler({ target: { closest: () => ({ dataset: { action: 'sign-in-google-account' } }) },
    preventDefault() {}, stopImmediatePropagation() {} });
  await new Promise(resolveTick => setImmediate(resolveTick));
  assert.match(owner.error, /原会话恢复未完成/);
  assert.doesNotMatch(owner.status, /均未改变|已登录|已建立/);
  assert.match(owner.status, /核对账号状态/);
  assert.equal(context.accountSignInPending, false);
});

test('switching areas while cloud progress paints cannot send or read from the new area', async () => {
  const source = (await readFile(new URL('../src/app.mjs', import.meta.url), 'utf8')).replaceAll('\r\n', '\n');
  const actionList = "if (!['show-google-login-requirements', 'sign-in-google-account'";
  const anchor = source.indexOf(actionList);
  assert.ok(anchor >= 0);
  const start = source.lastIndexOf("app.addEventListener('click', event => {", anchor);
  const end = source.indexOf('}, true);', anchor) + '}, true);'.length;
  assert.ok(start >= 0 && end > start);
  for (const action of ['context-menu-sync', 'load-account-cloud-documents']) {
    let handler;
    let finishPaint;
    const paint = new Promise(resolvePaint => { finishPaint = resolvePaint; });
    const calls = [];
    const chat = { dataArea: 'CHAT', current: { summary: { id: 'same-workspace' } }, contextMenu: {},
      accountSync: {}, status: '', error: '' };
    const work = { dataArea: 'WORK', current: { summary: { id: 'same-workspace' } }, accountSync: {}, status: '', error: '' };
    const context = vm.createContext({
      app: { addEventListener(_type, callback) { handler = callback; } },
      state: chat, native: true, cloudListReadPending: new WeakMap(), cloudListReadGenerations: new WeakMap(),
      render() {},
      beginAccountSyncProgress() {},
      waitForAccountSyncProgressPaint: () => paint,
      invoke: async (command, args) => { calls.push([command, args]); return { status: 'SYNCED' }; },
      completeAccountSyncProgress: async () => {},
      notifyAccountSync() {},
      loadDesktopAccountSync: async () => {},
      accountSyncErrorMessage: error => String(error),
    });
    vm.runInContext(source.slice(source.indexOf('function advanceCloudListRead('), source.indexOf('let cloudOpenAreaGeneration')), context);
    vm.runInContext(source.slice(start, end), context);
    handler({ target: { closest: () => ({ dataset: { action, id: 'same-conversation' } }) },
      preventDefault() {}, stopImmediatePropagation() {} });
    context.state = work;
    finishPaint();
    await new Promise(resolveTick => setImmediate(resolveTick));
    assert.deepEqual(calls, [], action);
    assert.equal(chat.error, '', action);
    assert.equal(work.error, '', action);
    assert.equal(work.status, '', action);
    assert.equal(context.cloudListReadPending.has(chat), false);
    assert.equal(context.cloudListReadPending.has(work), false);
  }
});

test('Google sign-in finishing after an area switch refreshes the visible account', async () => {
  const source = (await readFile(new URL('../src/app.mjs', import.meta.url), 'utf8')).replaceAll('\r\n', '\n');
  const anchor = source.indexOf("if (!['show-google-login-requirements', 'sign-in-google-account'");
  const start = source.lastIndexOf("app.addEventListener('click', event => {", anchor);
  const end = source.indexOf('}, true);', anchor) + '}, true);'.length;
  let handler;
  let rejectSignIn;
  let accountReads = 0;
  const chat = { dataArea: 'CHAT', accountSync: { email: null }, status: '', error: '' };
  const work = { dataArea: 'WORK', accountSync: { email: null }, status: '', error: '' };
  const context = vm.createContext({
    app: { addEventListener(_type, callback) { handler = callback; } },
    state: chat, native: true, accountSignInPending: false, accountSignInToken: 0,
    render() {},
    invoke: () => new Promise((_resolve, reject) => { rejectSignIn = reject; }),
    loadDesktopAccountSync: async () => { accountReads += 1; context.state.accountSync = { email: 'synthetic@example.invalid' }; },
  });
  vm.runInContext(source.slice(start, end), context);
  handler({ target: { closest: () => ({ dataset: { action: 'sign-in-google-account' } }) },
    preventDefault() {}, stopImmediatePropagation() {} });
  assert.equal(typeof rejectSignIn, 'function');
  context.state = work;
  rejectSignIn(Object.assign(new Error('原数据区的操作结果已留在原区'), { code: 'STALE_DATA_AREA' }));
  await new Promise(resolveTick => setImmediate(resolveTick));
  assert.equal(context.accountSignInPending, false);
  assert.equal(accountReads, 1);
  assert.equal(work.accountSync.email, 'synthetic@example.invalid');
  assert.equal(work.error, '');
});
