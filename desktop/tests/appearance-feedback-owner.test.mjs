import test from 'node:test';
import assert from 'node:assert/strict';
import vm from 'node:vm';
import { readFileSync } from 'node:fs';
import { normalizeAppearance } from '../src/desktop-parity-preferences.mjs';

const source = readFileSync(new URL('../src/app.mjs', import.meta.url), 'utf8');
const declaration = source.indexOf('const appearanceFeedbackRequests = new WeakMap();');
const start = declaration < 0 ? source.indexOf('async function updateAppearance(') : declaration;
const end = source.indexOf('const favoriteReadGenerations', start);
assert.ok(start >= 0 && end > start);
function fixture() {
  const owner = { dataArea: 'CHAT', pane: 'settings', settingsSection: 'appearance', settingsReadGeneration: 2,
    appearance: { mode: 'light', fontSize: 'standard', themeColor: 'orange' }, status: '原提示', error: '原错误' };
  const saves = []; let renders = 0;
  const context = vm.createContext({ state: owner, normalizeAppearance, applyAppearance() {}, render() { renders++; },
    persistNativeAppSettings(appearance, product) { let resolve, reject; const promise = new Promise((yes, no) => { resolve = yes; reject = no; }); saves.push({ appearance, product, resolve, reject }); return promise; } });
  vm.runInContext(source.slice(start, end), context);
  return { owner, context, saves, get renders() { return renders; }, update: (field = 'mode', value = 'dark') => context.updateAppearance(field, value) };
}

test('late appearance success after an area switch cannot clear the later area feedback', async () => {
  const f = fixture(), pending = f.update();
  const later = { dataArea: 'WORK', pane: 'settings', settingsSection: 'model', status: '后来工作提示', error: '后来工作错误' };
  f.context.state = later; f.saves[0].resolve(); await pending;
  assert.equal(later.status, '后来工作提示'); assert.equal(later.error, '后来工作错误');
  assert.equal(f.renders, 1, 'old receipt must not rebuild a different area');
});

test('late appearance completion cannot repaint a later settings page', async () => {
  const f = fixture(), pending = f.update();
  f.owner.settingsSection = 'model-configuration'; f.owner.status = '型号页提示'; f.owner.error = '型号页错误';
  f.saves[0].resolve(); await pending;
  assert.equal(f.owner.status, '型号页提示'); assert.equal(f.owner.error, '型号页错误'); assert.equal(f.renders, 1);
});

test('returning to the same appearance page does not revive feedback from an earlier visit', async () => {
  const f = fixture(), pending = f.update();
  f.owner.settingsReadGeneration = 4; f.owner.error = '新访问错误';
  f.saves[0].reject(new Error('旧访问保存失败')); await pending;
  assert.equal(f.owner.error, '新访问错误'); assert.equal(f.renders, 1);
});

test('only the latest appearance action may publish its own save result', async () => {
  const f = fixture(), first = f.update();
  const second = f.update('fontSize', 'large');
  f.owner.error = '后来操作提示'; f.saves[0].reject(new Error('旧保存失败')); await first;
  assert.equal(f.owner.error, '后来操作提示'); assert.equal(f.owner.appearance.fontSize, 'large');
  f.saves[1].resolve(); await second;
  assert.equal(f.owner.error, ''); assert.equal(f.owner.status, '');
});

test('the current appearance failure remains honest and keeps its selected preview', async () => {
  const f = fixture(), pending = f.update();
  f.saves[0].reject(Object.assign(new Error('结果暂不可确认'), { code: 'APP_SETTINGS_COMMIT_UNKNOWN' })); await pending;
  assert.equal(f.owner.appearance.mode, 'dark'); assert.match(f.owner.error, /保存结果未确认.*当前选择保留/); assert.equal(f.renders, 2);
});
