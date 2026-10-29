import test from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import vm from 'node:vm';
const source = (await readFile(new URL('../src/app.mjs', import.meta.url), 'utf8')).replaceAll('\r\n', '\n');
const marker = "app.addEventListener('click', event => {\n  const target = event.target.closest?.('[data-action]');\n  const action = target?.dataset.action;\n  const actions = ['generate-reminder-draft'";
const start = source.indexOf(marker), end = source.indexOf('}, true);', start) + '}, true);'.length;
assert.ok(start >= 0 && end > start, 'the actual reminder click owner must be present');
const settle = () => new Promise(resolve => setImmediate(resolve));
function fixture() {
  const owner = { dataArea: 'CHAT', pane: 'reminders', current: null, selectedConversationId: null, workspaces: [], reminders: { drafts: [], plans: [] }, status: '', error: '' };
  let listener, finishEnsure, rejectEnsure;
  const calls = [];
  const context = vm.createContext({ native: true, state: owner, desktopEntryRouteGeneration: 0, cloudOpenAreaGeneration: 0,
    app: { addEventListener: (_, callback) => { listener = callback; } }, render() {}, loadDesktopBackgroundRuntime: async () => {},
    invoke: async (command, args) => {
      calls.push({ command, args });
      if (command === 'ensure_desktop_local_workspace') return new Promise((resolve, reject) => { finishEnsure = resolve; rejectEnsure = reject; });
      assert.equal(command, 'create_desktop_manual_reminder_draft');
      return { drafts: [{ draftId: 'synthetic-draft', status: 'PENDING_REVIEW' }], plans: [] };
    },
  });
  vm.runInContext(source.slice(start, end), context);
  return { owner, calls, context, click() { listener({ target: { closest: () => ({ dataset: { action: 'create-manual-reminder-draft' } }) }, preventDefault() {}, stopImmediatePropagation() {} }); },
    finish() { finishEnsure({ summary: { id: 'owned-local-source' }, exchange: { conversations: [] } }); }, fail() { rejectEnsure(new Error('synthetic initialization failure')); } };
}
test('first manual reminder initializes its source through the existing owner and opens review without creating a plan', async () => {
  const f = fixture(); f.click();
  assert.equal(f.calls[0]?.command, 'ensure_desktop_local_workspace');
  f.finish(); await settle();
  assert.equal(f.calls[1].args.args.workspaceId, 'owned-local-source');
  assert.equal(f.owner.reminderEditor.item.draftId, 'synthetic-draft');
  assert.equal(f.calls.length, 2);
});
test('double clicking Add while its initial source is pending admits only one draft', async () => {
  const f = fixture(); f.click(); f.click(); assert.equal(f.calls.length, 1);
  f.finish(); await settle(); assert.equal(f.calls.filter(x => x.command === 'create_desktop_manual_reminder_draft').length, 1);
});
test('navigation during initialization cannot reopen the abandoned reminder editor or create a draft', async () => {
  const f = fixture(); f.click(); assert.equal(f.calls.length, 1);
  f.owner.pane = 'chat'; f.context.desktopEntryRouteGeneration++;
  f.finish(); await settle(); assert.equal(f.calls.length, 1); assert.equal(f.owner.reminderEditor, undefined); assert.equal(f.owner.current, null);
});
test('a refused initialization remains visible and the same explicit Add action can retry', async () => {
  const f = fixture(); f.click(); assert.equal(f.calls.length, 1);
  f.fail(); await settle(); assert.match(f.owner.error, /synthetic initialization failure/);
  f.click(); assert.equal(f.calls.length, 2); f.finish(); await settle(); assert.equal(f.owner.reminderEditor.item.draftId, 'synthetic-draft');
});
