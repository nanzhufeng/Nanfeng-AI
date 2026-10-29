import test from 'node:test';
import assert from 'node:assert/strict';
import vm from 'node:vm';
import { readFile } from 'node:fs/promises';
import { updateMemorySummaryDraft } from '../src/domain-editor-conflict.mjs';

const source = await readFile(new URL('../src/app.mjs', import.meta.url), 'utf8');
const start = source.indexOf("} else if (action === 'save-memory-summary-editor') {");
const end = source.indexOf("} else if (action === 'show-memory-summary-about') {", start);
assert.ok(start >= 0 && end > start);
const save = source.slice(start + "} else if (action === 'save-memory-summary-editor') {".length, end);
const mutateStart = source.indexOf('async function mutate(args,');
const mutate = source.slice(mutateStart, source.indexOf('function saveProject()', mutateStart));
const settle = () => new Promise(resolve => setImmediate(resolve));

function harness(existing = false) {
  const owner = { current: { summary: { id: 'chat-source-A' } }, dialog: {
    kind: 'memory-summary-editor', value: '记忆正文',
    ...(existing ? { memoryId: 'm1', expectedRevision: 3, expectedEntries: [{ id: 'm1', revision: 3 }] } : {}),
  }, error: '', status: '' };
  const writes = [];
  const context = vm.createContext({ state: owner, native: true, render() {}, refresh: async () => {},
    intent: () => `intent-${writes.length}`, recordDomainConflict() {},
    invoke(command, args, route) {
      assert.equal(command, 'mutate_desktop_domain');
      return new Promise((resolve, reject) => writes.push({ args, route, resolve, reject }));
    },
  });
  vm.runInContext(`${mutate}\nfunction saveSummary() {${save}\n}`, context);
  return { owner, context, writes, click: () => vm.runInContext('saveSummary()', context) };
}

for (const existing of [false, true]) test(`${existing ? 'existing' : 'first'} summary save admits one write while pending`, async () => {
  const h = harness(existing);
  h.click(); h.click();
  assert.equal(h.writes.length, 1);
  assert.equal(h.writes[0].args.args.action, existing ? 'replaceMemorySummary' : 'create');
  h.writes[0].resolve(); await settle();
  assert.equal(h.owner.dialog, null);
});

test('typing a newer editor during save keeps the draft and does not resubmit the pending write', async () => {
  const h = harness(); h.click();
  const newer = { ...h.owner.dialog, value: '保存期间继续输入的草稿' };
  h.owner.dialog = newer; h.click();
  assert.equal(h.writes.length, 1);
  assert.equal(h.writes[0].args.args.fields.body, '记忆正文');
  h.writes[0].resolve(); await settle();
  assert.equal(h.owner.dialog, newer);
  h.click(); assert.equal(h.writes.length, 2);
  assert.equal(h.writes[1].args.args.fields.body, newer.value);
  h.writes[1].resolve(); await settle();
});

test('a rejected write retains the editor and permits an explicit retry', async () => {
  const h = harness(); const editor = h.owner.dialog;
  h.click(); h.writes[0].reject(Error('synthetic rejection')); await settle();
  assert.equal(h.owner.dialog, editor); assert.match(h.owner.error, /synthetic rejection/);
  h.click(); assert.equal(h.writes.length, 2);
  h.writes[1].resolve(); await settle();
});

test('old source cleanup cannot unlock the newer source save', async () => {
  const h = harness(); h.click();
  h.owner.current = { summary: { id: 'chat-source-B' } };
  const newer = { kind: 'memory-summary-editor', value: 'B的记忆' };
  h.owner.dialog = newer; h.click();
  assert.equal(h.writes.length, 2);
  assert.equal(h.writes[1].args.args.workspaceId, 'chat-source-B');
  h.writes[0].resolve(); await settle(); h.click();
  assert.equal(h.writes.length, 2);
  assert.equal(h.owner.dialog, newer);
  h.writes[1].resolve(); await settle();
});

test('summary save button is disabled for its pending source and enabled after rejection', () => {
  const h = harness(); const branch = source.split('\n').find(line => line.includes("if (state.dialog?.kind === 'memory-summary-editor') return"));
  h.context.escape = String; h.context.renderDomainConflict = () => '';
  const html = () => vm.runInContext(`(function(){${branch}})()`, h.context);
  h.owner.memorySummarySavePending = { workspaceId: 'chat-source-A' };
  assert.match(html(), /data-action="save-memory-summary-editor" disabled/);
  h.owner.memorySummarySavePending = null;
  assert.doesNotMatch(html(), /data-action="save-memory-summary-editor" disabled/);
});

test('actual typing handler retains new text without enabling a second pending save', async () => {
  const h = harness(); h.click();
  const marker = "} else if (event.target.id === 'memory-summary-editor' && state.dialog?.kind === 'memory-summary-editor') {";
  const inputStart = source.indexOf(marker);
  const inputEnd = source.indexOf("} else if (event.target.id === 'conversation-find-input') {", inputStart);
  assert.ok(inputStart >= 0 && inputEnd > inputStart);
  const saveButton = { disabled: false };
  const counter = { textContent: '' };
  h.context.updateMemorySummaryDraft = updateMemorySummaryDraft;
  h.context.document = { querySelector: () => saveButton };
  h.context.event = { target: { value: '新的编辑草稿', closest: () => ({ querySelector: () => counter }) } };
  const input = () => vm.runInContext(`(function(){${source.slice(inputStart + marker.length, inputEnd)}})()`, h.context);
  input(); assert.equal(saveButton.disabled, true);
  assert.equal(h.owner.dialog.value, '新的编辑草稿');
  assert.equal(counter.textContent, '6 / 2000000 字');
  h.click(); assert.equal(h.writes.length, 1);
  h.writes[0].reject(Error('synthetic rejection')); await settle();
  input(); assert.equal(saveButton.disabled, false);
});

for (const existing of [false, true]) test(`${existing ? 'revised' : 'new'} summary continues the same saved record after typing during save`, async () => {
  const h = harness(existing);
  if (existing) {
    h.owner.dialog.expectedEntries.push({ id: 'old-second-topic', revision: 6 });
    h.owner.dialog.multipleEntries = true;
  }
  h.click();
  h.owner.dialog = updateMemorySummaryDraft(h.owner.dialog, '保存期间输入的新版本');
  const receipt = { workspaceId: 'chat-source-A', objectId: existing ? 'm1' : 'new-memory-id', revision: existing ? 4 : 1 };
  h.writes[0].resolve(receipt); await settle();
  assert.equal(h.owner.dialog.value, '保存期间输入的新版本');
  h.click();
  const args = h.writes[1].args.args;
  assert.equal(args.action, 'replaceMemorySummary');
  assert.equal(args.objectId, receipt.objectId);
  assert.equal(args.expectedRevision, receipt.revision);
  assert.equal(JSON.stringify(args.fields.expectedEntries), JSON.stringify([{ id: receipt.objectId, revision: receipt.revision }]));
  assert.equal(args.fields.body, '保存期间输入的新版本');
  h.writes[1].resolve(); await settle();
});

test('a saved receipt does not rebind an independently opened summary editor', async () => {
  const h = harness(); h.click();
  const unrelated = { kind: 'memory-summary-editor', value: '另一个编辑器', memoryId: 'other-memory', expectedRevision: 9 };
  h.owner.dialog = unrelated;
  h.writes[0].resolve({ workspaceId: 'chat-source-A', objectId: 'new-memory-id', revision: 1 }); await settle();
  assert.equal(h.owner.dialog, unrelated);
  assert.equal(unrelated.memoryId, 'other-memory'); assert.equal(unrelated.expectedRevision, 9);
});
