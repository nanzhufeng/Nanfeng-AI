import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import vm from 'node:vm';
const source = fs.readFileSync(new URL('../src/app.mjs', import.meta.url), 'utf8');
test('fullscreen save renders a real path from its actual app icon owner', () => {
  const icon = source.split('\n').find(line => line.startsWith('const icon ='));
  const icons = source.split('\n').find(line => line.startsWith('const icons ='));
  const branch = source.split('\n').find(line => line.includes("if (state.dialog?.kind === 'custom-instructions-fullscreen') return"));
  const expression = branch.slice(branch.indexOf('return ') + 7).replace(/;$/, '');
  const html = vm.runInNewContext(`${icon}\n${icons}\n${expression}`, {
    state: { personalizationDraft: { customInstructions: 'preview' } }, escape: value => value, CUSTOM_INSTRUCTIONS_MAX_LENGTH: 8000,
  });
  const button = html.match(/<button[^>]+data-action="save-custom-instructions-fullscreen"[\s\S]*?<\/button>/)[0];
  assert.match(button, /<path d="M20 6 9 17l-5-5"/);
  assert.doesNotMatch(button, /undefined|null/);
  assert.match(button, /aria-label="保存自定义指令"/);
});
