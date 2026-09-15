import assert from 'node:assert/strict';
import { execFileSync } from 'node:child_process';
import { access, readFile } from 'node:fs/promises';
import { dirname, relative, resolve } from 'node:path';
import test from 'node:test';

const desktopRoot = resolve(import.meta.dirname, '..');
const distRoot = resolve(desktopRoot, 'dist');

function localModuleImports(source) {
  return [...source.matchAll(/\b(?:import|export)\s+(?:[^'";]*?\s+from\s+)?['"](\.[^'"]+)['"]/g)]
    .map((match) => match[1]);
}

test('desktop build closes every local module imported by the renderer entry point', async () => {
  execFileSync(process.execPath, ['scripts/build.mjs'], {
    cwd: desktopRoot,
    stdio: 'inherit',
  });

  const pending = [resolve(distRoot, 'app.mjs')];
  const visited = new Set();
  const missing = [];

  while (pending.length > 0) {
    const modulePath = pending.pop();
    if (visited.has(modulePath)) continue;
    visited.add(modulePath);

    const source = await readFile(modulePath, 'utf8');
    for (const specifier of localModuleImports(source)) {
      const importedPath = resolve(dirname(modulePath), specifier);
      try {
        await access(importedPath);
        pending.push(importedPath);
      } catch {
        missing.push(`${relative(distRoot, modulePath)} -> ${specifier}`);
      }
    }
  }

  assert.deepEqual(missing, []);
});
