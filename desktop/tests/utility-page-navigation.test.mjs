import test from 'node:test';
import assert from 'node:assert/strict';
import { createUtilityPageNavigation } from '../src/utility-page-navigation.mjs';
import { renderDesktopRemindersPage } from '../src/desktop-reminders-page.mjs';
import { renderDesktopTranscriptionPage } from '../src/desktop-transcription-page.mjs';

const deferred = () => {
  let resolve;
  let reject;
  const promise = new Promise((yes, no) => { resolve = yes; reject = no; });
  return { promise, resolve, reject };
};

test('utility navigation paints loading before a read and reuses one pending read per area owner', async () => {
  const owner = { dataArea: 'CHAT', pane: 'reminders' };
  const disk = deferred();
  const events = [];
  let reads = 0;
  const navigate = createUtilityPageNavigation({
    currentOwner: () => owner,
    isCurrent: (page, candidate) => candidate === owner && owner.pane === page,
    render: () => events.push('paint'),
    loading: (_, busy) => events.push(busy),
    failed: () => assert.fail('read should succeed'),
  });
  const first = navigate('reminders', () => { reads++; events.push('read'); return disk.promise; });
  assert.deepEqual(events, [true, 'paint']);
  assert.equal(navigate('reminders', () => { reads++; return disk.promise; }), first);
  await Promise.resolve();
  assert.equal(reads, 1);
  disk.resolve();
  await first;
  assert.deepEqual(events.slice(-2), [false, 'paint']);
});

test('late CHAT read cannot suppress or repaint a WORK read on the same utility page', async () => {
  const chat = { dataArea: 'CHAT', pane: 'transcription' };
  const work = { dataArea: 'WORK', pane: 'transcription' };
  let current = chat;
  const chatDisk = deferred();
  const workDisk = deferred();
  const paints = [];
  const errors = [];
  const loading = new Map();
  const navigate = createUtilityPageNavigation({
    currentOwner: () => current,
    isCurrent: (page, owner) => owner === current && current.pane === page,
    render: () => paints.push(current.dataArea),
    loading: (page, busy, owner) => loading.set(`${owner.dataArea}:${page}`, busy),
    failed: (_, error) => errors.push(error),
  });
  const first = navigate('transcription', owner => {
    assert.equal(owner, chat);
    return chatDisk.promise;
  });
  await Promise.resolve();
  current = work;
  const second = navigate('transcription', owner => {
    assert.equal(owner, work);
    return workDisk.promise;
  });
  assert.notEqual(first, second);
  await Promise.resolve();
  chatDisk.reject(Error('old area unavailable'));
  await first;
  assert.deepEqual(errors, []);
  assert.deepEqual(paints, ['CHAT', 'WORK']);
  assert.equal(loading.get('CHAT:transcription'), false);
  assert.equal(loading.get('WORK:transcription'), true);
  workDisk.resolve();
  await second;
  assert.equal(loading.get('WORK:transcription'), false);
  assert.deepEqual(paints, ['CHAT', 'WORK', 'WORK']);
});

test('utility pages show loading rather than a false empty state', () => {
  const reminders = renderDesktopRemindersPage({ loading: true });
  assert.match(reminders, /aria-busy="true"/);
  assert.match(reminders, /正在读取定时任务/);
  assert.doesNotMatch(reminders, /暂无计划/);
  const transcription = renderDesktopTranscriptionPage({ loading: true, projection: { tasks: [] } });
  assert.match(transcription, /aria-busy="true"/);
  assert.match(transcription, /正在读取转写任务/);
  assert.doesNotMatch(transcription, /暂无转写任务/);
});
