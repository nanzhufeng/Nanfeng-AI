import test from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import vm from 'node:vm';

test('candidate review redraw keeps edited title, body and tags including intentional empty values', async () => {
  const source = await readFile(new URL('../src/app.mjs', import.meta.url), 'utf8');
  const branch = source.split('\n').find(line => line.includes("if (state.dialog?.kind === 'history-knowledge-review')"));
  const start = source.indexOf('  const editorKind = state.dialog?.kind;');
  const end = source.indexOf("  if (event.target.id === 'conversation-rename'", start);
  const state = { dialog: { kind: 'history-knowledge-review', item: { title: '模型标题', body: '模型原文', tags: ['模型标签'] } } };
  const context = vm.createContext({ state, event: null, escape: String, icon: () => '', icons: {}, short: String });
  for (const [id, value] of [['history-candidate-title', '审阅标题'], ['history-candidate-body', '审阅正文'], ['history-candidate-tags', '']]) {
    context.event = { target: { id, value } };
    vm.runInContext(`{${source.slice(start, end)}}`, context);
  }
  const html = vm.runInContext(`(function(){${branch}})()`, context);
  assert.match(html, /value="审阅标题"/);
  assert.match(html, />审阅正文<\/textarea>/);
  assert.doesNotMatch(html, /模型标题|模型原文|模型标签/);
  assert.equal(state.dialog.item.body, '模型原文', '未确认草稿不能改候选真值');
});
