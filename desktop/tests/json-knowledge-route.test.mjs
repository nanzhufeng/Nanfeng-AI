import assert from 'node:assert/strict';
import test from 'node:test';
import vm from 'node:vm';
import { readFileSync } from 'node:fs';

const source = readFileSync(new URL('../src/app.mjs', import.meta.url), 'utf8');
const begin = source.indexOf('async function handleJsonKnowledgeAction(');
const end = source.indexOf("app.addEventListener('click', event => { void handleJsonKnowledgeAction(event); }, true);", begin);
assert.ok(begin >= 0 && end > begin);
const loaderStart = source.indexOf('async function loadJsonKnowledgeTask(');
const loaderEnd = source.indexOf('function jsonKnowledgeTaskCard(', loaderStart);
const actual = source.slice(loaderStart, loaderEnd) + source.slice(begin, end);
const deferred = () => { let resolve, reject; const promise = new Promise((yes, no) => { resolve = yes; reject = no; }); return { promise, resolve, reject }; };
function harness(action, { picker = async () => 'synthetic.json', invoke = async command => command === 'list_desktop_json_knowledge_tasks' ? { tasks: [], hasMore: false } : ({ id: 'task-a', status: 'AWAITING_CONFIRMATION', workspaceId: 'a', items: [] }), taskId = 'task-a' } = {}) {
  const owner = { current: { summary: { id: 'a' } }, dialog: { kind: action === 'confirm-json-knowledge' ? 'json-knowledge-review' : 'knowledge', taskId: 'task-a', ordinal: 0 }, jsonKnowledgeTasks: [], jsonKnowledgeTask: { id: 'task-a', workspaceId: 'a', items: [{ ordinal: 0, status: 'PENDING_CONFIRMATION', candidate: { content: { title: 't', body: 'b', tags: [] }, source: { stableId: 'source-a', requestedScope: 'PROJECT', projectRef: 'external' } } }] }, error: '' };
  const calls = [];
  const target = { dataset: { action, ordinal: '0' }, closest: () => ({ dataset: { taskId } }) };
  const context = vm.createContext({ state: owner, native: true, render() {}, refresh: async () => {}, document: { querySelector: selector => ({ value: selector.endsWith('-title') ? 'edited title' : selector.endsWith('-body') ? 'edited body' : '' }) }, dialogInvoke: picker, invoke: async (command, args, areaOwner) => { calls.push({ command, args, areaOwner }); return invoke(command, args, areaOwner); } });
  vm.runInContext(actual, context);
  return { owner, context, calls, run: () => context.handleJsonKnowledgeAction({ target: { closest: () => target }, preventDefault() {}, stopImmediatePropagation() {} }) };
}
test('picker cancellation creates no task and preserves the manual dialog', async () => {
  const h = harness('select-json-knowledge', { picker: async () => null }); const dialog = h.owner.dialog;
  await h.run(); assert.equal(h.calls.length, 0); assert.equal(h.owner.dialog, dialog);
});
test('closing the originating dialog while picker is open prevents staging', async () => {
  const wait = deferred(); const h = harness('select-json-knowledge', { picker: () => wait.promise }); const running = h.run();
  h.owner.dialog = null; wait.resolve('synthetic.json'); await running; assert.equal(h.calls.length, 0);
});
test('area change during picker does not read or write another owner', async () => {
  const wait = deferred(); const h = harness('select-json-knowledge', { picker: () => wait.promise }); const running = h.run();
  h.context.state = { current: { summary: { id: 'b' } }, error: 'keep-b' }; wait.resolve('synthetic.json'); await running;
  assert.equal(h.calls.length, 0); assert.equal(h.context.state.error, 'keep-b');
});
test('late stage success is attributed to the starting owner and cannot project into a new area', async () => {
  const wait = deferred(); const h = harness('select-json-knowledge', { invoke: () => wait.promise }); const running = h.run();
  await new Promise(resolve => setImmediate(resolve)); assert.equal(h.calls[0].command, 'stage_desktop_json_knowledge'); assert.equal(h.calls[0].areaOwner, h.owner);
  h.context.state = { current: { summary: { id: 'b' } }, error: 'keep-b' }; wait.resolve({ status: 'AWAITING_CONFIRMATION', workspaceId: 'a', items: [] }); await running;
  assert.equal(h.context.state.error, 'keep-b'); assert.equal(h.context.state.jsonKnowledgeTask, undefined); assert.equal(h.calls.length, 1);
});
test('staging stays pending without invoking formal Knowledge confirmation', async () => {
  const h = harness('select-json-knowledge'); await h.run(); assert.deepEqual(h.calls.map(c => c.command), ['stage_desktop_json_knowledge', 'read_latest_desktop_json_knowledge', 'list_desktop_json_knowledge_tasks']); assert.equal(h.owner.jsonKnowledgeTask.status, 'AWAITING_CONFIRMATION'); assert.equal(h.owner.error, '');
});
test('stale task card refuses to confirm or skip the currently selected task', async () => {
  for (const action of ['confirm-json-knowledge', 'skip-json-knowledge']) { const h = harness(action, { taskId: 'old-task' }); await h.run(); assert.equal(h.calls.length, 0); assert.ok(h.owner.error); }
});
test('explicit confirmation captures edited material and suppresses late errors after area change', async () => {
  const wait = deferred(); const h = harness('confirm-json-knowledge', { invoke: () => wait.promise }); const running = h.run();
  assert.equal(h.calls[0].command, 'confirm_desktop_json_knowledge_item'); assert.equal(h.calls[0].args.args.workspaceId, 'a'); assert.equal(h.calls[0].args.args.candidate.body, 'edited body'); assert.equal(h.calls[0].areaOwner, h.owner);
  h.context.state = { current: { summary: { id: 'b' } }, error: 'keep-b' }; wait.reject(new Error('synthetic failure')); await running; assert.equal(h.context.state.error, 'keep-b');
});
test('switching to another task suppresses late confirmation errors in the same workspace', async () => {
  const wait = deferred(); const h = harness('confirm-json-knowledge', { invoke: () => wait.promise }); const running = h.run();
  h.owner.jsonKnowledgeTask = { id: 'task-b', workspaceId: 'a' }; h.owner.error = 'keep-task-b'; wait.reject(new Error('synthetic failure')); await running; assert.equal(h.owner.error, 'keep-task-b');
});
test('review redraw retains edited and intentionally empty fields without mutating the parsed source', () => {
  const start = source.indexOf('  const editorKind = state.dialog?.kind;');
  const stop = source.indexOf("  if (event.target.id === 'conversation-rename'", start);
  const branch = source.split('\n').find(line => line.includes("if (state.dialog.kind === 'json-knowledge-review')"));
  const state = { dialog: { kind: 'json-knowledge-review', taskId: 'task-a', item: { title: 'original title', body: 'original body', tags: ['old'] }, draft: {} } };
  const c = vm.createContext({ state, event: null, escape: String, icon: () => '', icons: {}, workItemScopeField: () => '' });
  for (const [id, value] of [['json-knowledge-title', 'edited title'], ['json-knowledge-body', 'edited body'], ['json-knowledge-tags', '']]) { c.event = { target: { id, value } }; vm.runInContext(`{${source.slice(start, stop)}}`, c); }
  const html = vm.runInContext(`(function(){${branch}})()`, c); assert.match(html, /value="edited title"/); assert.match(html, />edited body<\/textarea>/); assert.doesNotMatch(html, /original title|original body|value="old"/); assert.equal(state.dialog.item.body, 'original body');
});
test('actual work knowledge page renders the pending task through its shared card', () => {
  const renderStart = source.indexOf('function knowledgeOnlyCanvas(data)');
  const renderStop = source.indexOf('function memoryCanvas(data)', renderStart);
  const cardStart = source.indexOf('function jsonKnowledgeTaskCard(');
  const cardStop = source.indexOf('async function handleJsonKnowledgeAction(', cardStart);
  const task = { id: 'task-a', status: 'AWAITING_CONFIRMATION', displayName: 'synthetic.json', items: [{ ordinal: 0, candidate: { content: { title: 'Pending source', body: 'inert body', tags: [] }, source: { stableId: 'source-a' } }, status: 'PENDING_CONFIRMATION' }] };
  const state = { historyKnowledge: { candidates: [] }, jsonKnowledgeTask: task, jsonKnowledgeTasks: [task], jsonKnowledgeHasMore: false };
  const c = vm.createContext({ state, escape: String, short: String, active: (data, key) => data.exchange[key], pdfKnowledge: { card: () => '' }, webKnowledge: { card: () => '' }, markdownKnowledgeTaskCard: () => '' });
  vm.runInContext(source.slice(cardStart, cardStop) + source.slice(renderStart, renderStop), c);
  const html = c.knowledgeOnlyCanvas({ exchange: { knowledge: [], relations: [] } }); assert.match(html, /data-action="review-json-knowledge"/); assert.match(html, /data-task-id="task-a"/); assert.match(html, /Pending source/);
});

test('review exposes immutable source facts separately and confirmation submits edited content only', async () => {
  const h = harness('review-json-knowledge'); await h.run();
  assert.equal(h.owner.dialog.kind,'json-knowledge-review');assert.equal(h.owner.dialog.item.body,'b');assert.equal(h.owner.dialog.source.stableId,'source-a');assert.equal(h.owner.dialog.source.projectRef,'external');assert.equal(h.calls.length,0);
  const c=harness('confirm-json-knowledge');await c.run();const args=c.calls[0].args.args;assert.equal(args.projectId,null);assert.equal(args.candidate.body,'edited body');assert.equal(Object.hasOwn(args,'source'),false);assert.equal(Object.hasOwn(args.candidate,'projectRef'),false);
});
