import test from 'node:test';
import assert from 'node:assert/strict';
import vm from 'node:vm';
import { readFile } from 'node:fs/promises';

const source = await readFile(new URL('../src/app.mjs', import.meta.url), 'utf8');
const anchor = source.indexOf("if (!['review-history-knowledge', 'accept-history-knowledge'");
const start = source.lastIndexOf("app.addEventListener('click', event => {", anchor);
const handlerSource = source.slice(start, source.indexOf('}, true);', anchor) + '}, true);'.length);
const actions = ['accept-history-knowledge', 'reject-history-knowledge', 'retry-history-knowledge', 'delete-history-knowledge'];
function harness(action) {
  let listener, resolve, reject, invokedOwner, refreshes = 0, renders = 0;
  const dialog = { kind: 'history-knowledge-review' };
  const owner = { dataArea: 'WORK', current: { summary: { id: 'workspace-one' } }, dialog,
    historyKnowledge: { candidates: [{ candidateId: 'candidate' }] }, status: '', error: '' };
  const context = vm.createContext({ state: owner,
    app: { addEventListener(_name, value) { listener = value; } },
    document: { querySelector() { return { value: 'synthetic visible edit' }; } },
    invoke(_command, _args, captured) { invokedOwner = captured; return new Promise((yes, no) => { resolve = yes; reject = no; }); },
    refresh: async () => { refreshes++; }, render: () => { renders++; },
  });
  vm.runInContext(handlerSource, context);
  listener({ target: { closest: () => ({ dataset: { action, id: 'candidate' } }) }, preventDefault() {}, stopImmediatePropagation() {} });
  return { context, owner, dialog, resolve, reject, get invokedOwner() { return invokedOwner; }, get refreshes() { return refreshes; }, get renders() { return renders; } };
}
const flush = () => new Promise(resolve => setImmediate(resolve));
for (const action of actions) {
  test(`${action}: late area rejection does not overwrite destination UI`, async () => {
    const h = harness(action);
    const destination = { dataArea: 'CHAT', current: { summary: { id: 'workspace-one' } },
      dialog: { kind: 'destination-editor' }, historyKnowledge: { candidates: [] }, status: 'destination', error: 'destination error' };
    h.context.state = destination;
    h.reject(Object.assign(Error('old receipt'), { code: 'STALE_DATA_AREA' }));
    await flush();
    assert.equal(destination.error, 'destination error'); assert.equal(destination.status, 'destination');
    assert.equal(destination.dialog.kind, 'destination-editor'); assert.equal(destination.historyKnowledge.candidates.length, 0);
    assert.equal(h.refreshes, 0);
  });
  test(`${action}: same owner workspace navigation ignores old success`, async () => {
    const h = harness(action); h.owner.current = { summary: { id: 'workspace-two' } };
    h.owner.error = 'new workspace'; const currentHistory = h.owner.historyKnowledge;
    h.resolve({ candidates: [{ candidateId: 'old' }] }); await flush();
    assert.equal(h.owner.historyKnowledge, currentHistory); assert.equal(h.owner.error, 'new workspace');
    assert.equal(h.refreshes, 0);
  });
}
test('accepted current receipt refreshes its owner without closing a newer dialog', async () => {
  const h = harness('accept-history-knowledge'); const newer = { kind: 'new-editor' }; h.owner.dialog = newer;
  h.resolve({ candidates: [{ candidateId: 'candidate', status: 'ACCEPTED' }] }); await flush();
  assert.equal(h.invokedOwner, h.owner); assert.equal(h.owner.dialog, newer);
  assert.equal(h.owner.historyKnowledge.candidates[0].status, 'ACCEPTED'); assert.equal(h.refreshes, 1);
});
