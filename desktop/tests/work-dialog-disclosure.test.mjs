import test from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import vm from 'node:vm';
import { renderDomainConflict } from '../src/domain-editor-conflict.mjs';
import { renderKnowledgeDuplicates } from '../src/knowledge-duplicates.mjs';

test('WORK editors show the platform shortcut and Memory discloses later model use', async () => {
  const source = (await readFile(new URL('../src/app.mjs', import.meta.url), 'utf8')).replaceAll('\r\n', '\n');
  const shortcutLine = source.split('\n').find(line => line.includes('const saveShortcut ='));
  const scopeHelper = source.slice(source.indexOf('function workItemScopeField('), source.indexOf('function dialog()'));
  assert.ok(shortcutLine);
  for (const [platform, shortcut] of [['Win32', 'Ctrl+S'], ['MacIntel', '⌘S']]) {
    for (const kind of ['project', 'knowledge', 'memory']) {
      const branch = source.split('\n').find(line => line.includes(`if (state.dialog.kind === '${kind}')`));
      assert.ok(branch, kind);
      const context = vm.createContext({
        renderDomainConflict, renderKnowledgeDuplicates,
        navigator: { platform },
        state: { dialog: { kind, item: null } },
        icon: () => '',
        icons: { close: '' },
        escape: value => String(value ?? ''),
        active: () => [{ id: 'project-a', title: '甲项目' }],
        workspace: () => ({}),
      });
      const html = vm.runInContext(`(function () { ${shortcutLine} ${scopeHelper} ${branch} })()`, context);
      assert.ok(html.includes(`保存 ${shortcut}`), `${platform} ${kind}`);
      if (kind !== 'project') {
        assert.match(html, /适用范围/);
        assert.match(html, /project-a/);
      }
      if (kind === 'memory') {
        assert.match(html, /id="memory-title"/);
        assert.match(html, /保存只写入本机/);
        assert.match(html, /开启记忆时，会按当前问题选取相关内容/);
        assert.match(html, /点击发送后，选中的内容才会交给本次显示的模型服务/);
        assert.doesNotMatch(html, /不会进入 Prompt 或网络/);
      }
    }
  }
});

test('WORK Knowledge and Memory create with the chosen project and edits keep their scope', async () => {
  const source = await readFile(new URL('../src/app.mjs', import.meta.url), 'utf8');
  for (const [kind, id, field] of [['Knowledge', 'knowledge-project', 'projectId'], ['Memory', 'memory-project', 'scopeId']]) {
    const save = source.split('\n').find(line => line.startsWith(`function save${kind}()`));
    assert.ok(save);
    const values = { [id]: 'project-a', '#knowledge-title': '甲知识', '#knowledge-body': '甲正文', '#knowledge-tags': '甲', '#memory-title': '工作主题', '#memory-body': '甲记忆' };
    values[`#${id}`] = 'project-a';
    delete values[id];
    let captured;
    const context = vm.createContext({
        renderDomainConflict, renderKnowledgeDuplicates,
      state: { dialog: { item: null } },
      document: { querySelector: selector => ({ value: values[selector] }) },
      mutate: args => { captured = args; },
    });
    vm.runInContext(`${save} save${kind}();`, context);
    assert.equal(captured.action, 'create');
    assert.equal(captured.fields.scope, 'PROJECT');
    assert.equal(captured.fields[field], 'project-a');
    if (kind === 'Memory') assert.equal(captured.fields.title, '工作主题');
    context.state.dialog.item = { id: 'item-a', revision: 2 };
    vm.runInContext(`save${kind}();`, context);
    assert.equal(captured.action, 'update');
    assert.equal(Object.hasOwn(captured.fields, 'scope'), false);
    assert.equal(Object.hasOwn(captured.fields, field), false);
    if (kind === 'Memory') assert.equal(captured.fields.title, '工作主题');
  }
});

test('WORK Memory editor does not describe conversation scope as global', async () => {
  const source = await readFile(new URL('../src/app.mjs', import.meta.url), 'utf8');
  const helper = source.slice(source.indexOf('function workItemScopeField('), source.indexOf('function dialog()'));
  const html = vm.runInNewContext(`${helper} workItemScopeField({scope:'CONVERSATION',scopeId:'conversation-a'},'memory-project','scopeId')`, {
    active: () => [], workspace: () => ({}), escape: String,
  });
  assert.match(html, /仅所属对话/);
  assert.doesNotMatch(html, /所有对话/);
});

test('WORK editor drafts survive a background render before Save', async () => {
  const source = (await readFile(new URL('../src/app.mjs', import.meta.url), 'utf8')).replaceAll('\r\n', '\n');
  const scopeHelper = source.slice(source.indexOf('function workItemScopeField('), source.indexOf('function dialog()'));
  const inputStart = source.indexOf("app.addEventListener('input', event => {\n  const editorKind = state.dialog?.kind;");
  const inputEnd = source.indexOf('\nlet composerImeTarget', inputStart);
  const changeStart = source.indexOf("app.addEventListener('change', event => {\n  const editorKind = state.dialog?.kind;");
  const changeEnd = source.indexOf("\napp.addEventListener('focusin'", changeStart);
  assert.ok(inputStart > 0 && inputEnd > inputStart && changeStart > 0 && changeEnd > changeStart);
  const shortcutLine = source.split('\n').find(line => line.includes('const saveShortcut ='));
  for (const [kind, fields] of [
    ['project', { 'project-title': '修改项目', 'project-description': '修改说明' }],
    ['knowledge', { 'knowledge-title': '修改知识', 'knowledge-body': '修改正文', 'knowledge-tags': '新标签' }],
    ['memory', { 'memory-title': '修改主题', 'memory-body': '修改记忆' }],
  ]) {
    const handlers = new Map();
    const context = vm.createContext({
        renderDomainConflict, renderKnowledgeDuplicates,
      app: { addEventListener: (type, handler) => handlers.set(type, handler) },
      navigator: { platform: 'Win32' },
      state: { dialog: { kind, item: null } },
      icon: () => '', icons: { close: '' }, escape: value => String(value ?? ''),
      active: () => [{ id: 'project-a', title: '甲项目' }], workspace: () => ({}),
    });
    vm.runInContext(source.slice(inputStart, inputEnd), context);
    vm.runInContext(source.slice(changeStart, changeEnd), context);
    for (const [id, value] of Object.entries(fields)) handlers.get('input')({ target: { id, value } });
    if (kind !== 'project') handlers.get('change')({ target: { id: `${kind}-project`, value: 'project-a' } });
    const branch = source.split('\n').find(line => line.includes(`if (state.dialog.kind === '${kind}')`));
    const html = vm.runInContext(`(function () { ${shortcutLine} ${scopeHelper} ${branch} })()`, context);
    for (const value of Object.values(fields)) assert.ok(html.includes(value), `${kind}: ${value}`);
    if (kind !== 'project') assert.match(html, /value="project-a" selected/);
  }
});
