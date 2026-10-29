import test from 'node:test';
import assert from 'node:assert/strict';
import vm from 'node:vm';
import { readFile } from 'node:fs/promises';
import { renderDesktopRemindersPage } from '../src/desktop-reminders-page.mjs';

const source = (await readFile(new URL('../src/app.mjs', import.meta.url), 'utf8')).replaceAll('\r\n', '\n');
const marker = "app.addEventListener('click', event => {\n  const target = event.target.closest?.('[data-action]');\n  const action = target?.dataset.action;\n  const actions = ['generate-reminder-draft'";
const start = source.indexOf(marker);
const end = source.indexOf('}, true);', start) + '}, true);'.length;
assert.ok(start >= 0 && end > start);
const settle = () => new Promise(resolve => setImmediate(resolve));

function editor(mode = 'create', id = 'A') {
  return { mode, item: { ...(mode === 'edit' ? { planId: id, updatedAtMs: 10 } : { draftId: id }),
    title: `提醒${id}`, instruction: '合成要求', scheduleKind: 'ONCE', anchorLocal: '2030-01-01T12:00', timezoneId: 'UTC' } };
}
function harness(mode = 'create') {
  const owner = { reminderEditor: editor(mode), reminders: { drafts: [], plans: [] },
    backgroundRuntime: { desiredEnabled: false }, status: '', error: '' };
  const writes = [];
  let callback;
  const context = vm.createContext({ state: owner, native: true,
    app: { addEventListener(_event, fn) { callback = fn; } }, render() {},
    reminderFormValues: () => ({ title: owner.reminderEditor.item.title, instruction: owner.reminderEditor.item.instruction }),
    loadDesktopBackgroundRuntime: async () => owner.backgroundRuntime,
    invoke(command, args) { return new Promise((resolve, reject) => writes.push({ command, args, resolve, reject })); },
  });
  vm.runInContext(source.slice(start, end), context);
  const click = () => {
    const target = { disabled: false, dataset: { action: owner.reminderEditor.mode === 'edit' ? 'confirm-edit-reminder-plan' : 'confirm-reminder-draft' } };
    callback({ target: { closest: () => target }, preventDefault() {}, stopImmediatePropagation() {} });
    return target;
  };
  return { owner, writes, click, resolve: i => writes[i].resolve({ drafts: [], plans: [{ planId: 'saved' }] }) };
}

for (const mode of ['create', 'edit']) test(`${mode} reminder accepts only one save while awaiting native confirmation`, async () => {
  const h = harness(mode); h.click(); h.click();
  assert.equal(h.writes.length, 1);
  assert.equal(h.writes[0].command, mode === 'edit' ? 'update_desktop_reminder_plan' : 'confirm_desktop_reminder_draft');
  h.resolve(0); await settle(); assert.equal(h.owner.reminderEditor, null);
});

for (const mode of ['create', 'edit']) test(`${mode} reminder continued edits adopt exact committed plan identity and update stamp`, async () => {
  const h = harness(mode); h.click();
  h.owner.reminderEditor = { ...h.owner.reminderEditor, item: { ...h.owner.reminderEditor.item, title: '尚未保存的新标题' } };
  const committed = { planId: mode === 'edit' ? 'A' : 'created-plan', sourceDraftId: mode === 'create' ? 'A' : 'draft-for-A', updatedAtMs: 20, status: 'ACTIVE', title: '已保存标题', instruction: '合成要求', scheduleKind: 'ONCE', anchorLocal: '2030-01-01T12:00', timezoneId: 'UTC', missedPolicy: 'SKIP' };
  h.writes[0].resolve({ drafts: [], plans: [committed] }); await settle();
  assert.equal(h.owner.reminderEditor.mode, 'edit');
  assert.equal(h.owner.reminderEditor.item.planId, committed.planId);
  assert.equal(h.owner.reminderEditor.item.updatedAtMs, 20);
  assert.equal(h.owner.reminderEditor.item.title, '尚未保存的新标题');
  assert.equal(Boolean(h.owner.reminderEditor.submitting), false);
  h.click(); assert.equal(h.writes[1].command, 'update_desktop_reminder_plan');
  assert.equal(h.writes[1].args.args.planId, committed.planId);
  assert.equal(h.writes[1].args.args.expectedUpdatedAtMs, 20);
  assert.equal(h.writes[1].args.args.title, '尚未保存的新标题');
  h.resolve(1); await settle();
});

test('reminder commit cannot bind an independently opened editor by matching title', async () => {
  const h = harness(); h.click(); const other = editor('create', 'B');
  other.item.title = '已保存标题'; h.owner.reminderEditor = other;
  h.writes[0].resolve({ drafts: [], plans: [{ planId: 'created-plan', sourceDraftId: 'A', title: '已保存标题', updatedAtMs: 20 }] }); await settle();
  assert.equal(h.owner.reminderEditor, other); assert.equal(other.mode, 'create'); assert.equal(other.item.draftId, 'B');
});

test('late success keeps the newer unsaved reminder editor', async () => {
  const h = harness(); h.click();
  const newer = { ...h.owner.reminderEditor, item: { ...h.owner.reminderEditor.item, title: '保存期间新输入' } };
  h.owner.reminderEditor = newer;
  h.resolve(0); await settle();
  assert.equal(h.owner.reminderEditor, newer);
  assert.equal(h.owner.reminderEditor.item.title, '保存期间新输入');
  assert.equal(Boolean(newer.submitting), false);
});

test('failed reminder save retains edits and permits explicit retry', async () => {
  const h = harness(); const original = h.owner.reminderEditor;
  h.click(); h.writes[0].reject(Error('synthetic save rejection')); await settle();
  assert.equal(h.owner.reminderEditor, original); assert.match(h.owner.error, /synthetic save rejection/);
  h.click(); assert.equal(h.writes.length, 2); h.resolve(1); await settle();
});

test('old reminder save cleanup does not release the pending save of another plan', async () => {
  const h = harness(); h.click(); h.owner.reminderEditor = editor('create', 'B'); h.click();
  assert.equal(h.writes.length, 2); h.resolve(0); await settle();
  assert.equal(h.owner.reminderEditor.item.draftId, 'B');
  h.click(); assert.equal(h.writes.length, 2); h.resolve(1); await settle();
});

test('the reminder editor exposes its pending save through the existing disabled button', () => {
  const draft = editor(); draft.submitting = true;
  assert.match(renderDesktopRemindersPage({ editor: draft }), /data-action="confirm-reminder-draft" disabled/);
});
