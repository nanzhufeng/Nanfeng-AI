import test from 'node:test';
import assert from 'node:assert/strict';
import { createCloudConversationOpenRoute } from '../src/cloud-conversation-open-route.mjs';

function deferred() {
  let resolve;
  let reject;
  const promise = new Promise((nextResolve, nextReject) => { resolve = nextResolve; reject = nextReject; });
  return { promise, resolve, reject };
}

test('cloud conversation publishes content before optional model preferences resolve', async () => {
  const load = deferred();
  const preferences = deferred();
  const events = [];
  const open = createCloudConversationOpenRoute({
    load: async () => load.promise,
    loadPreferences: async () => preferences.promise,
    onPending: route => events.push(['pending', route.conversationId]),
    onOpened: route => events.push(['opened', route.conversationId]),
    onPreferences: route => events.push(['preferences', route.conversationId]),
    onPreferencesFailure: () => events.push(['preferences-failed']),
    onFailure: () => events.push(['failed']),
  });
  const opening = open({ workspaceId: 'cloud-workspace', conversationId: 'cloud-conversation' });
  assert.deepEqual(events, [['pending', 'cloud-conversation']]);
  load.resolve({ current: { summary: { id: 'cloud-workspace' } } });
  assert.equal((await opening).status, 'OPENED');
  assert.deepEqual(events, [['pending', 'cloud-conversation'], ['opened', 'cloud-conversation']]);
  preferences.resolve({ conversationOverride: { modelId: 'SAFE' } });
  await new Promise(resolve => setImmediate(resolve));
  assert.deepEqual(events.at(-1), ['preferences', 'cloud-conversation']);
});

test('a newer cloud row selection supersedes a late older read', async () => {
  const reads = new Map();
  const opened = [];
  const open = createCloudConversationOpenRoute({
    load: route => {
      const task = deferred();
      reads.set(route.conversationId, task);
      return task.promise;
    },
    loadPreferences: async () => null,
    onPending: () => {},
    onOpened: route => opened.push(route.conversationId),
    onPreferences: () => {},
    onPreferencesFailure: () => {},
    onFailure: () => {},
  });
  const first = open({ workspaceId: 'cloud-workspace', conversationId: 'older' });
  const second = open({ workspaceId: 'cloud-workspace', conversationId: 'newer' });
  reads.get('older').resolve({});
  reads.get('newer').resolve({});
  assert.equal((await first).status, 'SUPERSEDED');
  assert.equal((await second).status, 'OPENED');
  assert.deepEqual(opened, ['newer']);
});

test('an already loaded cloud workspace switches conversations without reopening it', async () => {
  const events = [];
  const open = createCloudConversationOpenRoute({
    reuse: route => route.conversationId === 'resident' ? { current: { summary: { id: route.workspaceId } }, history: { canUndo: false } } : null,
    load: async () => assert.fail('a resident workspace must not be loaded again'),
    loadPreferences: async () => ({ conversationOverride: { modelId: 'RESIDENT' } }),
    onPending: () => assert.fail('a resident workspace must not enter the blank opening state'),
    onOpened: (route, _result, { reused }) => events.push(['opened', route.conversationId, reused]),
    onPreferences: route => events.push(['preferences', route.conversationId]),
    onPreferencesFailure: () => assert.fail('resident preferences should resolve'),
    onFailure: () => assert.fail('a resident workspace must not fail to open'),
  });

  assert.equal((await open({ workspaceId: 'cloud-workspace', conversationId: 'resident' })).status, 'REUSED');
  await new Promise(resolve => setImmediate(resolve));
  assert.deepEqual(events, [['opened', 'resident', true], ['preferences', 'resident']]);
});

test('selecting the cloud conversation already on screen is a no-op', async () => {
  const calls = [];
  const open = createCloudConversationOpenRoute({
    reuse: () => ({ current: { summary: { id: 'cloud-workspace' } }, alreadySelected: true }),
    load: async () => { calls.push('load'); },
    loadPreferences: async () => { calls.push('preferences'); },
    onPending: () => { calls.push('pending'); },
    onOpened: () => { calls.push('opened'); },
    onPreferences: () => { calls.push('preferences-opened'); },
    onPreferencesFailure: () => { calls.push('preferences-failed'); },
    onFailure: () => { calls.push('failed'); },
  });

  assert.equal((await open({ workspaceId: 'cloud-workspace', conversationId: 'resident' })).status, 'ALREADY_OPEN');
  assert.deepEqual(calls, []);
});

test('a repeated selection does not cancel the current conversation preference read', async () => {
  const preferences = deferred();
  const events = [];
  let resident = false;
  const open = createCloudConversationOpenRoute({
    reuse: () => resident ? { current: { summary: { id: 'cloud-workspace' } }, alreadySelected: true } : null,
    load: async () => {
      resident = true;
      return { current: { summary: { id: 'cloud-workspace' } } };
    },
    loadPreferences: async () => preferences.promise,
    onPending: () => {},
    onOpened: () => {},
    onPreferences: () => events.push('preferences'),
    onPreferencesFailure: () => assert.fail('the original preference read must remain current'),
    onFailure: () => assert.fail('the first open must succeed'),
  });

  await open({ workspaceId: 'cloud-workspace', conversationId: 'resident' });
  assert.equal((await open({ workspaceId: 'cloud-workspace', conversationId: 'resident' })).status, 'ALREADY_OPEN');
  preferences.resolve({ conversationOverride: { modelId: 'RESIDENT' } });
  await new Promise(resolve => setImmediate(resolve));
  assert.deepEqual(events, ['preferences']);
});

test('cloud workspace read failure stays visible instead of leaving a blank route', async () => {
  const failures = [];
  const open = createCloudConversationOpenRoute({
    load: async () => { throw new Error('read unavailable'); },
    loadPreferences: async () => null,
    onPending: () => {},
    onOpened: () => assert.fail('must not open without a workspace'),
    onPreferences: () => assert.fail('must not read preferences without content'),
    onPreferencesFailure: () => {},
    onFailure: (_route, error) => failures.push(error.message),
  });
  assert.equal((await open({ workspaceId: 'cloud-workspace', conversationId: 'unavailable' })).status, 'FAILED');
  assert.deepEqual(failures, ['read unavailable']);
});
