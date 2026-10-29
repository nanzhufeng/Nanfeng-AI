import test from 'node:test';
import assert from 'node:assert/strict';
import vm from 'node:vm';
import { readFileSync } from 'node:fs';
import { createPdfKnowledgeController } from '../src/pdf-knowledge-tasks.mjs';

const deferred = () => { let resolve; const promise = new Promise(r => resolve = r); return { promise, resolve }; };
const ready = { id: 'pdf-a', workspaceId: 'w', displayName: 'synthetic.pdf', status: 'AWAITING_CONFIRMATION', generation: 1, completedPages: 3, totalPages: 3, items: [{ ordinal: 0, pageNumber: 3, status: 'PENDING_CONFIRMATION', candidate: { title: '<script>inert</script>', body: 'source text', tags: [] } }] };
function harness({ picker = async () => 'synthetic.pdf', handler = async () => ready } = {}) {
  const owner = { dataArea: 'WORK', current: { summary: { id: 'w' } }, pane: 'knowledge', dialog: { kind: 'knowledge' }, pdfKnowledgeTask: structuredClone(ready), pdfKnowledgeTasks: [ready] };
  let state = owner;
  const calls = [], timers = new Map(); let next = 0;
  const controller = createPdfKnowledgeController({ getState: () => state, native: true, invoke: async (command, args, areaOwner) => { calls.push({ command, args, areaOwner }); return handler(command, args); }, dialogInvoke: picker, render() {}, refresh: async () => {}, escape: s => String(s).replaceAll('<', '&lt;'), document: { querySelector: selector => ({ value: selector.endsWith('-title') ? 'edited title' : selector.endsWith('-body') ? 'edited body' : '' }) }, setTimer: fn => { const id = ++next; timers.set(id, () => { timers.delete(id); return fn(); }); return id; }, clearTimer: id => timers.delete(id) });
  const event = (action, taskId = 'pdf-a', type = 'click') => ({ type, target: { closest: () => ({ dataset: { action, ordinal: '0' }, value: taskId, closest: () => ({ dataset: { taskId } }) }) }, preventDefault() {}, stopImmediatePropagation() {} });
  return { owner, calls, timers, controller, event, setState: value => state = value };
}
test('cancelled or stale picker never creates a PDF task', async () => {
  const cancelled = harness({ picker: async () => null }); const dialog = cancelled.owner.dialog;
  await cancelled.controller.handle(cancelled.event('select-pdf-knowledge')); assert.equal(cancelled.calls.length, 0); assert.equal(cancelled.owner.dialog, dialog);
  const wait = deferred(), h = harness({ picker: () => wait.promise }); const p = h.controller.handle(h.event('select-pdf-knowledge'));
  h.setState({ current: { summary: { id: 'other' } }, error: 'keep' }); wait.resolve('synthetic.pdf'); await p; assert.equal(h.calls.length, 0);
});
test('private copy starts bounded processing without formal confirmation and polls persisted counts', async () => {
  const finish = deferred(); const h = harness({ handler: async command => command === 'stage_desktop_pdf_knowledge' ? { ...ready, status: 'PRIVATE_COPIED', generation: 0, items: [], completedPages: 0, totalPages: 0 } : command === 'process_desktop_pdf_knowledge' ? finish.promise : { ...ready, status: 'EXTRACTING', generation: 0, completedPages: 1, totalPages: 3, items: [] } });
  await h.controller.handle(h.event('select-pdf-knowledge'));
  assert.deepEqual(h.calls.map(c => c.command), ['stage_desktop_pdf_knowledge', 'process_desktop_pdf_knowledge']);
  await [...h.timers.values()][0](); assert.equal(h.owner.pdfKnowledgeTask.completedPages, 1); assert.equal(h.owner.pdfKnowledgeTask.totalPages, 3);
  assert.match(h.controller.card(h.owner.pdfKnowledgeTask), /已提取 1 \/ 3 页/); assert.doesNotMatch(h.controller.card(h.owner.pdfKnowledgeTask), /review-pdf-knowledge/);
  finish.resolve(ready); await new Promise(setImmediate); assert.equal(h.owner.pdfKnowledgeTask.status, 'AWAITING_CONFIRMATION'); assert.equal(h.timers.size, 0);
  assert.ok(h.calls.every(c => c.areaOwner === h.owner)); assert.ok(!h.calls.some(c => c.command.includes('confirm')));
});
test('cancel remains actionable while worker is pending and late reply preserves cancellation', async () => {
  const finish = deferred(), cancelled = { ...ready, status: 'CANCELLED', generation: 0, items: [] };
  const h = harness({ handler: async command => command === 'stage_desktop_pdf_knowledge' ? { ...cancelled, status: 'PRIVATE_COPIED' } : command === 'process_desktop_pdf_knowledge' ? finish.promise : cancelled });
  await h.controller.handle(h.event('select-pdf-knowledge')); await h.controller.handle(h.event('cancel-pdf-knowledge'));
  assert.equal(h.owner.pdfKnowledgeTask.status, 'CANCELLED'); assert.equal(h.timers.size, 0);
  finish.resolve(cancelled); await new Promise(setImmediate); assert.equal(h.owner.pdfKnowledgeTask.status, 'CANCELLED');
});
test('page confirmation carries exact generation and edited content; stale review cannot write', async () => {
  const h = harness(); await h.controller.handle(h.event('review-pdf-knowledge')); assert.equal(h.owner.dialog.pageNumber, 3);
  h.owner.pdfKnowledgeTask.generation = 2; await h.controller.handle(h.event('confirm-pdf-knowledge')); assert.equal(h.calls.length, 0);
  h.owner.pdfKnowledgeTask.generation = 1; await h.controller.handle(h.event('confirm-pdf-knowledge'));
  assert.equal(h.calls[0].command, 'confirm_desktop_pdf_knowledge_item'); assert.equal(h.calls[0].args.args.generation, 1); assert.equal(h.calls[0].args.args.candidate.body, 'edited body'); assert.equal(h.calls[0].args.args.projectId, null);
});
test('late task read cannot overwrite another area or selected task', async () => {
  const wait = deferred(), h = harness({ handler: () => wait.promise }); const p = h.controller.handle(h.event('select-pdf-knowledge-task', 'pdf-b', 'change'));
  h.setState({ current: { summary: { id: 'other' } }, error: 'keep' }); wait.resolve({ ...ready, id: 'pdf-b' }); await p; assert.equal(h.owner.pdfKnowledgeTask.id, 'pdf-a');
});
test('cold failed task stays inert until explicit retry, and candidates render as escaped text', async () => {
  const failed = { ...ready, status: 'FAILED', items: [], safeErrorCode: 'INTERRUPTED' };
  const h = harness({ handler: async command => command === 'list_desktop_pdf_knowledge_tasks' ? { tasks: [failed], hasMore: false } : failed });
  await h.controller.load(); assert.deepEqual(h.calls.map(c => c.command), ['read_latest_desktop_pdf_knowledge', 'list_desktop_pdf_knowledge_tasks']); assert.equal(h.timers.size, 0);
  assert.match(h.controller.card(failed), /retry-pdf-knowledge/); assert.doesNotMatch(h.controller.card(ready), /<script>/);
  await h.controller.handle(h.event('retry-pdf-knowledge')); await new Promise(setImmediate); assert.equal(h.calls[2].command, 'retry_desktop_pdf_knowledge');
});
test('actual app mounts PDF card and review keeps edited draft after rerender', () => {
  const source = readFileSync(new URL('../src/app.mjs', import.meta.url), 'utf8');
  const h = harness(); h.owner.dialog = { kind: 'pdf-knowledge-review', taskId: 'pdf-a', pageNumber: 3, item: ready.items[0].candidate, draft: { 'pdf-knowledge-title': 'edited title', 'pdf-knowledge-body': 'edited body' } };
  const branch = source.split('\n').find(l => l.includes("if (state.dialog.kind === 'pdf-knowledge-review')"));
  const c = vm.createContext({ state: h.owner, escape: String, icon: () => '', icons: {}, workItemScopeField: () => '' });
  const html = vm.runInContext(`(function(){${branch}})()`, c); assert.match(html, /审阅第 3 页/); assert.match(html, /edited body/); assert.match(html, /confirm-pdf-knowledge/);
  assert.ok(source.includes('${pdfKnowledge.card(state.pdfKnowledgeTask)}')); assert.ok(source.includes('void pdfKnowledge.load().catch'));
});

test('mounted controller is initialized after all non-hoisted app dependencies', () => {
  const source = readFileSync(new URL('../src/app.mjs', import.meta.url), 'utf8');
  const mounted = source.indexOf('const pdfKnowledge = createPdfKnowledgeController(');
  for (const dependency of ['const invoke =', 'const dialogInvoke =', 'const escape =', 'const render =']) assert.ok(source.indexOf(dependency) >= 0 && source.indexOf(dependency) < mounted, dependency);
});
