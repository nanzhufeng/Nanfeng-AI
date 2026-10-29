import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import vm from 'node:vm';

const source = readFileSync(new URL('../src/app.mjs', import.meta.url), 'utf8');
for (const action of ['context-menu-pin', 'context-menu-archive']) {
  test(`${action} closes before writing and preserves a subsequently opened menu`, async () => {
    const start = source.indexOf(`  if (action === '${action}') {`);
    const end = source.indexOf('\n  if (action === ', start + 1);
    let complete;
    const pending = new Promise(resolve => { complete = resolve; });
    const state = { contextMenu: { id: 'old' } };
    let paintedMenu;
    const context = { state, action, target: { dataset: { id: 'conversation', pinned: 'false', archived: 'false' } },
      isCloudListConversationTarget: () => false,
      mutateConversationLifecycle: () => pending,
      render() { paintedMenu = state.contextMenu; } };
    const work = vm.runInNewContext(`(async () => { ${source.slice(start, end)} })()`, context);
    assert.equal(state.contextMenu, null);
    assert.equal(paintedMenu, null);
    const newer = { id: 'new' };
    state.contextMenu = newer;
    complete();
    await work;
    assert.equal(state.contextMenu, newer);
  });
}
