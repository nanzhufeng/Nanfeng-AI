import test from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { runInNewContext } from 'node:vm';
import { attachmentMenuAnchor } from '../src/search-attachment-menu.mjs';

test('attachment long press tolerates hand jitter but cancels a drag', async () => {
  const source = await readFile(new URL('../src/app.mjs', import.meta.url), 'utf8');
  const handlers = {};
  const state = {};
  let timer;
  const card = { dataset: { searchEntryId: 'sample' } };
  const context = {
    attachmentMenuAnchor,
    state,
    app: { addEventListener: (type, callback) => { handlers[type] = callback; } },
    window: { setTimeout: callback => { timer = callback; return 1; }, clearTimeout: () => { timer = null; } },
    fullSearchHit: id => ({ entryId: id }), rememberFullSearchScroll() {}, render() {},
  };
  runInNewContext(source.slice(source.indexOf('let searchAttachmentLongPressTimer ='), source.indexOf('render = renderUnified;')), context);
  const down = { button: 0, clientX: 20, clientY: 20, target: { closest: selector => selector === '.desktop-search-attachment-card' ? card : null } };
  handlers.pointerdown(down);
  handlers.pointermove({ clientX: 22, clientY: 21 });
  assert.equal(typeof timer, 'function');
  timer();
  assert.equal(state.dialog.kind, 'search-attachment-actions');
  state.dialog = null;
  handlers.pointerdown(down);
  handlers.pointermove({ clientX: 40, clientY: 20 });
  assert.equal(timer, null);
  assert.equal(state.dialog, null);
  let prevented = false;
  handlers.contextmenu({ target: down.target, preventDefault() { prevented = true; }, stopPropagation() {} });
  assert.equal(prevented, true);
  assert.equal(state.dialog.kind, 'search-attachment-actions');
  assert.equal(state.dialog.hit.entryId, 'sample');
});
