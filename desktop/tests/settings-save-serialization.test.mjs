import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import vm from 'node:vm';
import { createLocalWriteBarrier } from '../src/local-write-barrier.mjs';
const source = fs.readFileSync(new URL('../src/app.mjs', import.meta.url), 'utf8');
const owner = source.slice(source.indexOf('let appSettingsSaveQueue'), source.indexOf('async function updateAppearance'));
test('rapid saves merge only their fields and use the committed revision', async () => {
  let disk = { appearance: { mode: 'light' }, product: { nickname: 'original', memoryEnabled: false }, revision: 1 };
  const scope = vm.createContext({ createLocalWriteBarrier, native: true, state: {}, normalizeAppearance: x => x, normalizeProductSettings: x => x,
    parityPreferences: { clearNativeAppSettingsMigrationSource() {} },
    invoke: async (command, payload) => {
      await new Promise(resolve => setTimeout(resolve, 2));
      if (command === 'read_desktop_app_settings') return structuredClone(disk);
      assert.equal(payload.args.expectedRevision, disk.revision);
      disk = { ...payload.args, revision: disk.revision + 1 };
      return structuredClone(disk);
    } });
  vm.runInContext(owner, scope);
  await Promise.all([
    scope.persistNativeAppSettings({}, { nickname: 'saved' }),
    scope.persistNativeAppSettings({}, { memoryEnabled: true }),
    scope.persistNativeAppSettings({ mode: 'dark' }, {}),
  ]);
  assert.equal(disk.product.nickname, 'saved');
  assert.equal(disk.product.memoryEnabled, true);
  assert.equal(disk.appearance.mode, 'dark');
  assert.equal(disk.revision, 4);
});

test('an earlier save projection does not hide a later unsaved choice', async () => {
  let disk = { appearance: { mode: 'light' }, product: { memoryEnabled: false, historyLibraryEnabled: false }, revision: 1 };
  let releaseSecondRead;
  const secondRead = new Promise(resolve => { releaseSecondRead = resolve; });
  let firstSaved;
  const firstSave = new Promise(resolve => { firstSaved = resolve; });
  let reads = 0;
  const state = { appearance: { ...disk.appearance }, productSettings: { ...disk.product } };
  const scope = vm.createContext({ createLocalWriteBarrier, native: true, state, normalizeAppearance: x => x, normalizeProductSettings: x => x,
    parityPreferences: { clearNativeAppSettingsMigrationSource() {} },
    invoke: async (command, payload) => {
      if (command === 'read_desktop_app_settings') {
        reads += 1;
        if (reads === 2) await secondRead;
        return structuredClone(disk);
      }
      disk = { ...payload.args, revision: disk.revision + 1 };
      if (disk.revision === 2) firstSaved();
      return structuredClone(disk);
    } });
  vm.runInContext(owner, scope);
  state.productSettings.memoryEnabled = true;
  const first = scope.persistNativeAppSettings({}, { memoryEnabled: true });
  state.productSettings.historyLibraryEnabled = true;
  const second = scope.persistNativeAppSettings({}, { historyLibraryEnabled: true });
  await firstSave;
  await first;
  assert.equal(state.productSettings.memoryEnabled, true);
  assert.equal(state.productSettings.historyLibraryEnabled, true);
  releaseSecondRead();
  await second;
  assert.equal(disk.product.historyLibraryEnabled, true);
});

test('a failed earlier save restores committed fields while keeping a later choice visible', async () => {
  let disk = { appearance: { mode: 'light' }, product: { memoryEnabled: false, historyLibraryEnabled: false }, revision: 1 };
  let releaseLaterRead;
  const laterRead = new Promise(resolve => { releaseLaterRead = resolve; });
  let reads = 0;
  let saves = 0;
  const state = { appearance: { ...disk.appearance }, productSettings: { ...disk.product } };
  const scope = vm.createContext({ createLocalWriteBarrier, native: true, state, normalizeAppearance: x => x, normalizeProductSettings: x => x,
    parityPreferences: { clearNativeAppSettingsMigrationSource() {} },
    invoke: async (command, payload) => {
      if (command === 'read_desktop_app_settings') {
        reads += 1;
        if (reads === 3) await laterRead;
        return structuredClone(disk);
      }
      saves += 1;
      if (saves === 1) throw new Error('synthetic save refusal');
      disk = { ...payload.args, revision: disk.revision + 1 };
      return structuredClone(disk);
    } });
  vm.runInContext(owner, scope);
  state.productSettings.memoryEnabled = true;
  const first = scope.persistNativeAppSettings({}, { memoryEnabled: true });
  state.productSettings.historyLibraryEnabled = true;
  const second = scope.persistNativeAppSettings({}, { historyLibraryEnabled: true });
  await assert.rejects(first, /synthetic save refusal/);
  assert.equal(state.productSettings.memoryEnabled, false);
  assert.equal(state.productSettings.historyLibraryEnabled, true);
  releaseLaterRead();
  await second;
  assert.equal(disk.product.memoryEnabled, false);
  assert.equal(disk.product.historyLibraryEnabled, true);
});

test('unreadable save result remains explicitly unknown and keeps the local choice', async () => {
  const state = { appearance: { mode: 'light' }, productSettings: { memoryEnabled: true } };
  let reads = 0;
  const scope = vm.createContext({ createLocalWriteBarrier, native: true, state, normalizeAppearance: x => x, normalizeProductSettings: x => x,
    parityPreferences: { clearNativeAppSettingsMigrationSource() {} },
    invoke: async command => {
      if (command === 'read_desktop_app_settings') {
        reads += 1;
        if (reads > 1) throw new Error('synthetic read refusal');
        return { appearance: { mode: 'light' }, product: { memoryEnabled: false }, revision: 1 };
      }
      throw new Error('synthetic save interruption');
    } });
  vm.runInContext(owner, scope);
  await assert.rejects(scope.persistNativeAppSettings({}, { memoryEnabled: true }), error => error.code === 'APP_SETTINGS_COMMIT_UNKNOWN');
  assert.equal(state.productSettings.memoryEnabled, true);
});
