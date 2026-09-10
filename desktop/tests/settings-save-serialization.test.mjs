import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import vm from 'node:vm';
const source = fs.readFileSync(new URL('../src/app.mjs', import.meta.url), 'utf8');
const owner = source.slice(source.indexOf('let appSettingsSaveQueue'), source.indexOf('async function updateAppearance'));
test('rapid saves merge only their fields and use the committed revision', async () => {
  let disk = { appearance: { mode: 'light' }, product: { nickname: 'original', memoryEnabled: false }, revision: 1 };
  const scope = vm.createContext({ native: true, state: {}, normalizeAppearance: x => x, normalizeProductSettings: x => x,
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
