import assert from 'node:assert/strict';
import { execFileSync } from 'node:child_process';
import { access, readFile, readdir } from 'node:fs/promises';
import { dirname, relative, resolve, sep } from 'node:path';
import test from 'node:test';

const desktopRoot = resolve(import.meta.dirname, '..');
const distRoot = resolve(desktopRoot, 'dist');

function localModuleImports(source) {
  const staticImports = [...source.matchAll(/\b(?:import|export)\s+(?:[^'";]*?\s+from\s+)?['"](\.[^'"]+)['"]/g)];
  const dynamicImports = [...source.matchAll(/\bimport\(\s*['"](\.[^'"]+)['"]\s*\)/g)];
  // A future computed import needs an explicit owner, rather than silently
  // disappearing from this finite packaged-resource check.
  assert.equal([...source.matchAll(/\bimport\s*\(/g)].length, dynamicImports.length, 'unclassified dynamic import');
  return [...staticImports, ...dynamicImports].map(match => match[1]);
}

async function files(directory, prefix = '') {
  const result = [];
  for (const item of await readdir(directory, { withFileTypes: true })) {
    assert.ok(!item.isSymbolicLink(), 'redirected renderer resource');
    if (item.isDirectory()) result.push(...await files(resolve(directory, item.name), `${prefix}${item.name}/`));
    else { assert.ok(item.isFile()); result.push(prefix + item.name); }
  }
  return result.sort();
}

test('desktop build closes both HTML entries, CSS and every local renderer module', async () => {
  execFileSync(process.execPath, ['--max-old-space-size=2048', 'scripts/build.mjs'], {
    cwd: desktopRoot,
    stdio: 'inherit',
  });

  const pending = ['index.html', 'island.html'].map(name => resolve(distRoot, name));
  const visited = new Set();
  const missing = [];

  while (pending.length > 0) {
    const modulePath = pending.pop();
    if (visited.has(modulePath)) continue;
    visited.add(modulePath);

    if (!/\.(mjs|html|css)$/.test(modulePath)) {
      await access(modulePath);
      continue;
    }
    const source = await readFile(modulePath, 'utf8');
    const specifiers = modulePath.endsWith('.mjs') ? localModuleImports(source)
      : modulePath.endsWith('.html') ? [...source.matchAll(/\b(?:src|href)=["'](\.[^"']+)["']/g)].map(match => match[1])
        : [...source.matchAll(/(?:url\(\s*|@import\s+)["']?(\.[^\s"')]+)["']?\s*\)?/g)].map(match => match[1]);
    for (const specifier of specifiers) {
      const importedPath = resolve(dirname(modulePath), specifier.split(/[?#]/)[0]);
      assert.ok(importedPath.startsWith(distRoot + sep), 'renderer reference must remain inside dist');
      try {
        await access(importedPath);
        pending.push(importedPath);
      } catch {
        missing.push(`${relative(distRoot, modulePath)} -> ${specifier}`);
      }
    }
  }

  assert.deepEqual(missing, []);
  for (const required of ['island-window.mjs', 'nanfeng-island.mjs', 'nanfeng-island.css', 'chat-ui-policy.mjs', 'desktop-app-entry.mjs', 'generation-continuity.mjs', 'inline-conversation-title.mjs', 'reply-text-emphasis.mjs']) {
    assert.ok(visited.has(resolve(distRoot, required)), `${required} must be reachable from its HTML owner`);
  }
});

test('all selected renderer sources and dynamic brand assets preserve exact build bytes', async () => {
  const sourceRoot = resolve(desktopRoot, 'src');
  const sourceFiles = await files(sourceRoot);
  const distFiles = await files(distRoot);
  // Existing pure P6-G router is deliberately not selected by build.mjs.
  // If it becomes reachable, the entry closure test requires it in dist.
  const excluded = ['p6g-model-router.mjs'];
  const generated = ['fixture-summary.json', 'nanfeng-ai-icon.png'];
  assert.deepEqual(sourceFiles.filter(name => !distFiles.includes(name)), excluded);
  assert.deepEqual(distFiles.filter(name => !sourceFiles.includes(name)), generated);
  for (const name of sourceFiles.filter(name => !excluded.includes(name) && name !== 'attachment-document-types.mjs')) {
    assert.deepEqual(await readFile(resolve(distRoot, name)), await readFile(resolve(sourceRoot, name)), `${name} copied bytes`);
  }
  const { MODEL_BRAND_NAMES } = await import('../src/model-settings-browser.mjs');
  for (const brand of new Set(Object.values(MODEL_BRAND_NAMES))) {
    await access(resolve(distRoot, `assets/model-brands/${brand}.png`));
  }
});

test('generated fixture summary, MIME catalog and shared icon match their real input owners', async () => {
  const mimeTypes = JSON.parse(await readFile(resolve(desktopRoot, '../protocol/fixtures/attachment-document-types-v1.json'), 'utf8')).mimeTypes;
  assert.equal(await readFile(resolve(distRoot, 'attachment-document-types.mjs'), 'utf8'), `export const ATTACHMENT_DOCUMENT_MIME_TYPES = Object.freeze(${JSON.stringify(mimeTypes)});\n`);
  const fixture = JSON.parse(await readFile(resolve(desktopRoot, '../protocol/fixtures/nfai.exchange.v1.golden.json'), 'utf8'));
  assert.deepEqual(JSON.parse(await readFile(resolve(distRoot, 'fixture-summary.json'), 'utf8')), { format: fixture.format, version: fixture.version, semanticHash: fixture.export.semanticHash });
  assert.deepEqual(await readFile(resolve(distRoot, 'nanfeng-ai-icon.png')), await readFile(resolve(desktopRoot, '../app/src/main/res/drawable-nodpi/nanfeng_ai_icon_foreground_image.png')));
});
