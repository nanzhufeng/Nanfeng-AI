import test from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import vm from 'node:vm';
import { renderDesktopRemindersPage } from '../src/desktop-reminders-page.mjs';

const source = await readFile(new URL('../src/app.mjs', import.meta.url), 'utf8');

test('chat reminder generation opens the review page without confirming a plan', async () => {
  const start = source.indexOf("      } else if (action === 'generate-reminder-draft') {") + "      } else if (action === 'generate-reminder-draft') {".length;
  const end = source.indexOf("      } else if (action === 'create-manual-reminder-draft')", start);
  const draft = { draftId: 'draft', sourceAssistantMessageId: 'answer', status: 'PENDING_REVIEW' };
  const state = { pane: 'work', current: { summary: { id: 'workspace' } }, selectedConversationId: 'conversation' };
  const calls = [];
  const context = vm.createContext({ state, owner: state, native: true, target: { dataset: { userMessageId: 'user', assistantMessageId: 'answer' } }, render() {}, invoke: async command => { calls.push(command); return { drafts: [draft], plans: [] }; } });
  await vm.runInContext(`(async()=>{${source.slice(start, end)}})()`, context);
  assert.equal(state.pane, 'reminders');
  assert.equal(state.reminderEditor.item, draft);
  assert.deepEqual(calls, ['generate_desktop_reminder_draft']);
});

test('reminder review keeps edits including empty title through redraw without changing persisted projection', () => {
  const item = { title: '原始标题', instruction: '原始说明', anchorLocal: '2027-01-01T12:00', timezoneId: 'UTC', missedPolicy: 'RUN_ONCE' };
  const state = { reminders: { drafts: [item] }, reminderEditor: { mode: 'create', item } };
  const start = source.lastIndexOf("app.addEventListener('input', event => {", source.indexOf('  const editorKind = state.dialog?.kind;')) + "app.addEventListener('input', event => {".length;
  const end = source.indexOf("  if (event.target.id === 'conversation-rename'", start);
  const context = vm.createContext({ state, event: null });
  for (const [id, value] of [['reminder-title', ''], ['reminder-instruction', '审阅说明'], ['reminder-anchor-local', '2027-10-01T12:30'], ['reminder-timezone', 'Asia/Shanghai'], ['reminder-missed-policy', 'SKIP']]) {
    context.event = { target: { id, value } };
    vm.runInContext(`{${source.slice(start, end)}}`, context);
  }
  const html = renderDesktopRemindersPage({ editor: state.reminderEditor });
  assert.match(html, /id="reminder-title"[^>]*value=""/);
  assert.match(html, />审阅说明<\/textarea>/);
  assert.match(html, /value="2027-10-01T12:30"/);
  assert.match(html, /value="Asia\/Shanghai"/);
  assert.match(html, /value="SKIP" selected/);
  assert.equal(item.title, '原始标题');
  assert.equal(item.instruction, '原始说明');
});
