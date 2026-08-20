import test from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { resolve } from 'node:path';

const source = await readFile(resolve(import.meta.dirname, '../src/app.mjs'), 'utf8');

test('P6-K Desktop boot refreshes the shell and renders a data-safe startup failure state', () => {
  const boot = source.slice(source.indexOf('async function bootDesktopShell'), source.indexOf('void bootDesktopShell();') + 'void bootDesktopShell();'.length);
  assert.match(source, /async function bootDesktopShell\(\) \{\s*try \{\s*await refresh\(\);/s);
  assert.match(boot, /state\.error = '本地工作区启动读取未完成；请重新打开应用后再试。';/);
  assert.match(boot, /state\.status = '启动读取失败；未修改本地数据。';\s*render\(\);/s);
  assert.match(boot, /void bootDesktopShell\(\);/);
  assert.doesNotMatch(boot, /String\(error\)/);
});
