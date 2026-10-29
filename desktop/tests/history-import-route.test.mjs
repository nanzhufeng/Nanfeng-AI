import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import vm from 'node:vm';

const source = fs.readFileSync(new URL('../src/app.mjs', import.meta.url), 'utf8');
const importOwner = source.slice(source.indexOf('function importRouteIsCurrent('), source.indexOf('async function pickChatGptExport('));

test('JSON import keeps its original workspace and pauses pending items after an area switch', async () => {
  const chat = { current: { summary: { id: 'workspace-chat' } }, chatgptTask: null };
  const work = { current: { summary: { id: 'workspace-work' } }, chatgptTask: null };
  const calls = [];
  let releaseFirst;
  const firstCommitted = new Promise(resolve => { releaseFirst = resolve; });
  let firstStarted;
  const firstCall = new Promise(resolve => { firstStarted = resolve; });
  const scope = vm.createContext({
    state: chat,
    refresh: async () => {},
    invoke: async (command, args) => {
      calls.push({ command, args });
      if (command === 'stage') return { id: 'task-chat' };
      if (command === 'read') return { id: 'task-chat', items: [{ id: 'one', status: 'PENDING_CONFIRMATION' }, { id: 'two', status: 'PENDING_CONFIRMATION' }] };
      if (command === 'confirm') { firstStarted(); await firstCommitted; }
      return null;
    },
  });
  vm.runInContext(importOwner, scope);
  const run = scope.commitSelectedImport({ taskKey: 'chatgptTask', stageCommand: 'stage', readCommand: 'read', confirmCommand: 'confirm', selectedPath: 'synthetic.json', label: 'ChatGPT', owner: chat, workspaceId: 'workspace-chat' });
  await firstCall;
  scope.state = work;
  releaseFirst();
  await run;
  assert.deepEqual(calls.filter(call => call.command === 'confirm').map(call => [call.args.args.workspaceId, call.args.args.itemId]), [['workspace-chat', 'one']]);
  assert.equal(work.chatgptTask, null);
  assert.equal(chat.chatgptTask, null);
});

test('JSON import completes every item when its workspace remains current', async () => {
  const chat = { current: { summary: { id: 'workspace-chat' } }, chatgptTask: null };
  const commits = [];
  const scope = vm.createContext({ state: chat, refresh: async () => {}, invoke: async (command, args) => {
    if (command === 'stage') return { id: 'task-chat' };
    if (command === 'read') return { id: 'task-chat', items: [{ id: 'one', status: 'PENDING_CONFIRMATION' }, { id: 'two', status: 'PENDING_CONFIRMATION' }] };
    if (command === 'confirm') commits.push(args.args.itemId);
  } });
  vm.runInContext(importOwner, scope);
  await scope.commitSelectedImport({ taskKey: 'chatgptTask', stageCommand: 'stage', readCommand: 'read', confirmCommand: 'confirm', selectedPath: 'synthetic.json', label: 'ChatGPT', owner: chat, workspaceId: 'workspace-chat' });
  assert.deepEqual(commits, ['one', 'two']);
  assert.equal(chat.chatgptTask.id, 'task-chat');
});
