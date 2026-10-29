import test from 'node:test';
import assert from 'node:assert/strict';
import vm from 'node:vm';
import { readFile } from 'node:fs/promises';
const source = await readFile(new URL('../src/app.mjs', import.meta.url), 'utf8');
const block = source.slice(source.indexOf("    const nextSettingsSection = target.dataset.page"), source.indexOf("  } else if (action === 'settings-back')", source.indexOf("    const nextSettingsSection = target.dataset.page")));
const turn = () => new Promise(resolve => setImmediate(resolve));
function harness() {
  const pending = [];
  const owner = { pane: 'settings', settingsSection: 'personalization', error: '' };
  const context = vm.createContext({ state: owner, native: true, render() {},
    clearModelCredentialDraftOutsideEditor() {}, resetModelServiceDraft() {},
    loadDesktopUsageLedger() { return new Promise((resolve, reject) => pending.push({resolve, reject})); },
  });
  vm.runInContext(`function open(page) { const target = {dataset:{page}}; ${block} }`, context);
  return { context, owner, pending, open: () => vm.runInContext("open('conversation-cost')", context) };
}
test('a settings read failure after leaving the page cannot replace the current error', async () => {
  const h = harness(); h.open(); h.owner.settingsSection = 'privacy'; h.owner.error = 'current page';
  h.pending[0].reject(Error('old cost read')); await turn();
  assert.equal(h.owner.error, 'current page');
});
test('an old area settings read cannot write into the new area', async () => {
  const h = harness(); h.open(); const work = {pane:'settings', settingsSection:'conversation-cost', error:''};
  h.context.state = work; h.pending[0].reject(Error('old CHAT read')); await turn();
  assert.equal(work.error, '');
});
test('reopening the same settings page supersedes the earlier read', async () => {
  const h = harness(); h.open(); h.open(); h.pending[0].reject(Error('superseded')); await turn();
  assert.equal(h.owner.error, ''); h.pending[1].resolve(); await turn();
});
test('the current settings read still reports its failure', async () => {
  const h = harness(); h.open(); h.pending[0].reject(Error('current read unavailable')); await turn();
  assert.match(h.owner.error, /费用记录读取失败.*current read unavailable/);
});
