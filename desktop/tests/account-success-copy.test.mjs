import test from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
test('periodic sync switch does not add redundant success footnotes', async () => {
  const source = await readFile(new URL('../src/app.mjs', import.meta.url), 'utf8');
  assert.doesNotMatch(source, /已开启定期同步，仅处理已选对话|已关闭定期同步。/);
});
