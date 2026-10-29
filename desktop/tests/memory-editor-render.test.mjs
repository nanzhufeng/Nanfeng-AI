import test from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import vm from 'node:vm';
import { memorySummaryValue, renderDomainConflict, updateMemorySummaryDraft } from '../src/domain-editor-conflict.mjs';

test('memory editor includes every topic heading when editing multiple memories', async () => {
  const source = await readFile(new URL('../src/app.mjs', import.meta.url), 'utf8');
  const start = source.indexOf('function memorySummaryEditorValue(memories) {');
  const end = source.indexOf('async function clearMemorySummary()', start);
  assert.ok(start >= 0 && end > start);
  const format = vm.runInNewContext(`${source.slice(start, end)}; memorySummaryEditorValue`, { memorySummaryValue });
  assert.equal(format([{ title: '工作偏好', body: '简洁回答' }, { title: '写作习惯', body: '保留来源' }]),
    '【工作偏好】\n简洁回答\n\n【写作习惯】\n保留来源');
  assert.equal(format([{ title: '工作偏好', body: '简洁回答' }]), '简洁回答');
});

test('memory editor opens for empty and existing summaries with safely editable text', async () => {
  const source = await readFile(new URL('../src/app.mjs', import.meta.url), 'utf8');
  const branch = source.split('\n').find(line => line.includes("if (state.dialog?.kind === 'memory-summary-editor') return"));
  for (const value of ['', '原摘要\n新增 <内容>']) {
    const context = vm.createContext({ state: { dialog: { kind: 'memory-summary-editor', value } },
      renderDomainConflict, escape: text => String(text).replaceAll('&', '&amp;').replaceAll('<', '&lt;').replaceAll('>', '&gt;') });
    const html = vm.runInContext(`(function () { ${branch} })()`, context);
    assert.match(html, /textarea id="memory-summary-editor"/);
    assert.ok(html.includes(value ? '原摘要\n新增 &lt;内容&gt;' : '输入或粘贴新的记忆摘要'));
  }
});

test('memory editor reserves its remaining height for the textarea and only that area scrolls', async () => {
  const css = await readFile(new URL('../src/chat-shell.css', import.meta.url), 'utf8');
  assert.match(css, /\.memory-summary-editor > label \{[^}]*grid-template-rows: auto minmax\(0, 1fr\) auto/);
  assert.match(css, /\.memory-summary-editor textarea \{[^}]*height: 100%;[^}]*align-self: stretch;[^}]*overflow: auto/);
});

test('typing a new summary enables save and retains added text', async () => {
  const source = await readFile(new URL('../src/app.mjs', import.meta.url), 'utf8');
  const start = source.indexOf("state.dialog = updateMemorySummaryDraft(state.dialog, event.target.value);");
  const end = source.indexOf("  } else if", start);
  const state = { dialog: { kind: 'memory-summary-editor', value: '' } };
  const save = { disabled: true };
  const counter = { textContent: '' };
  const context = vm.createContext({ state, updateMemorySummaryDraft, document: { querySelector: () => save },
    event: { target: { value: '手动新增摘要', closest: () => ({ querySelector: () => counter }) } } });
  vm.runInContext(source.slice(start, end), context);
  assert.equal(state.dialog.value, '手动新增摘要');
  assert.equal(save.disabled, false);
});
