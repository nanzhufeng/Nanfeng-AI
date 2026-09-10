import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import vm from 'node:vm';
const source = fs.readFileSync(new URL('../src/app.mjs', import.meta.url), 'utf8');
test('transcription drop imports each file into persistent OCR tasks without starting inference', async () => {
  const calls = [];
  const state = { current: { summary: { id: 'workspace' } } };
  const context = vm.createContext({ native: true, state, clipboardFileBase64: async () => 'bytes', invoke: async (command, payload) => { calls.push([command, payload]); return { id: 'task' }; }, loadTranscriptionState: async () => {}, render: () => {} });
  vm.runInContext(source.slice(source.indexOf('async function importDroppedTranscriptionFiles('), source.indexOf('async function pickTranscriptionDocument(')), context);
  await context.importDroppedTranscriptionFiles([{name:'page.png'}, {name:'document.pdf'}]);
  assert.equal(calls.length, 2);
  assert.ok(calls.every(([command]) => command === 'import_desktop_ocr_drop'));
  assert.equal(state.selectedTranscriptionTaskId, 'task');
});
