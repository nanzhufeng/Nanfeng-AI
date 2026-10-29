import assert from 'node:assert/strict';
import test from 'node:test';
import vm from 'node:vm';
import { readFile } from 'node:fs/promises';
import { invokeForDataArea } from '../src/data-area-invoke.mjs';

const source = await readFile(new URL('../src/app.mjs', import.meta.url), 'utf8');

test('the selected backup owner supplies the WORK header and rejects a stale area result', async () => {
  const ownerSource = source.slice(source.indexOf('function selectedBackupAreaState()'), source.indexOf('let areaSwitchPending'));
  const chat = { dataArea: 'CHAT', localBackupArea: 'WORK', runtimeInfo: { dataAreasReady: true } };
  const owners = new Map([['CHAT', chat]]);
  const pending = [];
  const bridge = { invoke(command, args, options) {
    return new Promise(resolve => pending.push({ command, args, options, resolve }));
  } };
  const context = vm.createContext({ state: chat, dataAreaStates: owners, tauriBridge: bridge, invokeForDataArea,
    createAreaState: dataArea => ({ dataArea, localBackup: {} }), initializeAreaState: owner => owner });
  vm.runInContext(ownerSource, context);

  const first = vm.runInContext("invokeSelectedBackup(selectedBackupAreaState(), 'read_desktop_local_backup_status')", context);
  assert.equal(pending[0].options.headers['x-nanfeng-data-area'], 'WORK');
  pending[0].resolve({ operation: 'IDLE' });
  assert.equal((await first).operation, 'IDLE');
  assert.equal(owners.get('WORK').dataArea, 'WORK');

  const stale = vm.runInContext("invokeSelectedBackup(selectedBackupAreaState(), 'export_desktop_local_backup', { selectedPath: 'synthetic' })", context);
  chat.localBackupArea = 'CHAT';
  pending[1].resolve({ sha256: 'synthetic' });
  await assert.rejects(stale, error => error.code === 'STALE_DATA_AREA');
});

test('switching to a WORK owner created by the backup picker retains dual-area readiness', async () => {
  const switchSource = source.slice(source.indexOf('async function switchDataArea('), source.indexOf('const dialogInvoke ='));
  const chat = { dataArea: 'CHAT', runtimeInfo: { dataAreasReady: true }, localBackup: {}, cloudConversations: [] };
  const work = { dataArea: 'WORK', runtimeInfo: { version: '读取中' }, localBackup: {}, current: { summary: { id: 'synthetic-work' } }, cloudConversations: [] };
  const context = vm.createContext({ native: true, state: chat, dataAreaStates: new Map([['CHAT', chat], ['WORK', work]]), areaSwitchPending: false,
    ordinaryChatRuntimeRefreshPending: null, composerDraftRouteGeneration: 0, cloudOpenAreaGeneration: 0, app: { innerHTML: '' },
    fullSearchCacheScope: 'CHAT synthetic results', fullSearchResultCache: { clear() { context.searchCacheCleared = true; } },
    supersedeFullSearch() { context.searchInvalidated = true; },
    stopComposerCamera() { context.cameraStopped = true; },
    flushOrdinaryComposerDraft: async () => {}, localSettingsWrites: { flush: async () => {} },
    loadDesktopLocalBackupStatus: async () => {}, loadDesktopAppSettings: async () => {}, loadModelServiceSettings: async () => {},
    loadDesktopHistoryKnowledge: async () => {}, loadDesktopReminders: async () => {}, refresh: async () => {}, loadDesktopAccountSync: async () => {},
    restoreCachedCloudConversationList: async () => {}, loadOrdinaryComposerDraft: async () => {},
    drainReminderNotificationActions: async () => {}, render() {},
    selectWorkspaceDefaultConversation: () => {},
    invoke: async () => ({ conversationOverride: { revision: 0, toneOverride: null, webSearchOverride: null } }),
  });
  vm.runInContext(switchSource, context);
  await vm.runInContext("switchDataArea('WORK', 'work')", context);
  assert.equal(context.state, work);
  assert.equal(work.runtimeInfo.dataAreasReady, true);
  assert.equal(context.cloudOpenAreaGeneration, 1);
  assert.equal(context.searchInvalidated, true);
  assert.equal(context.searchCacheCleared, true);
  assert.equal(context.fullSearchCacheScope, '');
  assert.equal(context.cameraStopped, true);
});

test('backup export and restore update the selected WORK projection only', async () => {
  const work = { dataArea: 'WORK', localBackup: { working: false, restartRequired: false, preflight: { fingerprint: 'synthetic-fingerprint' }, replaceLocal: true } };
  const chat = { dataArea: 'CHAT', localBackup: { working: false, notice: 'CHAT unchanged' }, dialog: null };
  const calls = [];
  const context = vm.createContext({ native: true, state: chat, selectedBackupAreaState: () => work,
    dialogInvoke: async () => 'D:/synthetic-work.nfai-backup', render() {},
    invokeSelectedBackup: async (owner, command, args) => {
      calls.push({ owner, command, args });
      if (command === 'export_desktop_local_backup') return { sha256: 'a'.repeat(64), schemaVersion: 45 };
      return { restartRequired: true };
    },
  });
  const exportOwner = source.slice(source.indexOf('async function exportLocalBackup()'), source.indexOf('async function importLocalBackup()'));
  const restoreOwner = source.slice(source.indexOf('async function restoreLocalBackup()'), source.indexOf('async function cancelLocalRestore()'));
  vm.runInContext(`${exportOwner}\n${restoreOwner}`, context);

  await vm.runInContext('exportLocalBackup()', context);
  assert.equal(calls[0].owner, work);
  assert.equal(calls[0].command, 'export_desktop_local_backup');
  assert.match(work.localBackup.notice, /工作区备份已回读校验/);
  assert.equal(chat.localBackup.notice, 'CHAT unchanged');

  await vm.runInContext('restoreLocalBackup()', context);
  assert.equal(calls[1].owner, work);
  assert.equal(calls[1].command, 'restore_desktop_local_backup');
  assert.equal(calls[1].args.fingerprint, 'synthetic-fingerprint');
  assert.equal(calls[1].args.replaceLocal, true);
  assert.equal(work.localBackup.restartRequired, true);
  assert.equal(chat.dialog.kind, 'local-backup-restart-required');
  assert.equal(chat.localBackup.notice, 'CHAT unchanged');
});

test('a native area rejection is shown as a readable message, without JSON syntax', async () => {
  const chat = { dataArea: 'CHAT', localBackup: { working: false, restartRequired: false } };
  const context = vm.createContext({ native: true, selectedBackupAreaState: () => chat,
    dialogInvoke: async () => 'D:/synthetic-work.nfai-backup', render() {},
    invokeSelectedBackup: async () => { throw '{"message":"备份属于另一个数据区域，不能跨 CHAT/WORK 恢复"}'; },
  });
  const errorSource = source.slice(source.indexOf('function localBackupErrorMessage('), source.indexOf('async function exportLocalBackup()'));
  const importSource = source.slice(source.indexOf('async function importLocalBackup()'), source.indexOf('async function restoreLocalBackup()'));
  vm.runInContext(`${errorSource}\n${importSource}`, context);
  await vm.runInContext('importLocalBackup()', context);
  assert.equal(chat.localBackup.error, '预检失败：备份属于另一个数据区域，不能跨 CHAT/WORK 恢复');
  assert.equal(chat.localBackup.preflight, null);
});

test('a failed native rollback refreshes its WORK status and leaves CHAT untouched', async () => {
  const work = { dataArea: 'WORK', localBackup: { working: false, preflight: { fingerprint: 'synthetic-fingerprint' }, replaceLocal: true } };
  const chat = { dataArea: 'CHAT', localBackup: { notice: 'CHAT unchanged' }, dialog: null };
  const calls = [];
  const context = vm.createContext({ native: true, state: chat, selectedBackupAreaState: () => work, render() {},
    invokeSelectedBackup: async (_owner, command) => {
      calls.push(command);
      if (command === 'restore_desktop_local_backup') throw '{"message":"替换失败且自动回滚未完成；恢复 checkpoint 已保留"}';
      if (command === 'read_desktop_local_backup_status') return { operation: 'FAILED', preflight: null, notice: '请释放占用文件并完全重启 App', rollbackPending: true, restartRequired: false, interrupted: false };
      throw new Error(`Unexpected command: ${command}`);
    },
  });
  const statusSource = source.slice(source.indexOf('function applyDesktopLocalBackupStatus('), source.indexOf('async function loadSelectedBackupStatus()'));
  const errorSource = source.slice(source.indexOf('function localBackupErrorMessage('), source.indexOf('async function exportLocalBackup()'));
  const restoreSource = source.slice(source.indexOf('async function restoreLocalBackup()'), source.indexOf('async function cancelLocalRestore()'));
  vm.runInContext(`${statusSource}\n${errorSource}\n${restoreSource}`, context);

  await vm.runInContext('restoreLocalBackup()', context);
  assert.deepEqual(calls, ['restore_desktop_local_backup', 'read_desktop_local_backup_status']);
  assert.equal(work.localBackup.rollbackPending, true);
  assert.equal(work.localBackup.preflight, null);
  assert.match(work.localBackup.error, /自动回滚未完成/);
  assert.match(work.localBackup.notice, /完全重启/);
  assert.equal(chat.localBackup.notice, 'CHAT unchanged');
});
