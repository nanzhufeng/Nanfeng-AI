import assert from 'node:assert/strict';
import { spawnSync } from 'node:child_process';
import { cp, mkdir, mkdtemp, readFile, rm, writeFile } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join, resolve, sep } from 'node:path';
import test from 'node:test';

const desktop = resolve(import.meta.dirname, '..');
async function fixture(run) {
  const root = await mkdtemp(join(tmpdir(), 'nfai-renderer-build-'));
  assert.ok(root.startsWith(resolve(tmpdir()) + sep));
  try {
    await mkdir(join(root, 'desktop/scripts'), { recursive: true });
    await cp(join(desktop, 'src'), join(root, 'desktop/src'), { recursive: true });
    await cp(join(desktop, 'scripts/build.mjs'), join(root, 'desktop/scripts/build.mjs'));
    for (const name of ['nfai.exchange.v1.golden.json', 'attachment-document-types-v1.json']) {
      await mkdir(join(root, 'protocol/fixtures'), { recursive: true });
      await cp(join(desktop, '../protocol/fixtures', name), join(root, 'protocol/fixtures', name));
    }
    const icon = 'app/src/main/res/drawable-nodpi/nanfeng_ai_icon_foreground_image.png';
    await mkdir(join(root, 'app/src/main/res/drawable-nodpi'), { recursive: true });
    await cp(join(desktop, '..', icon), join(root, icon));
    const build = () => spawnSync(process.execPath, ['--max-old-space-size=2048', 'scripts/build.mjs'], {
      cwd: join(root, 'desktop'), encoding: 'utf8', windowsHide: true, timeout: 30000, maxBuffer: 1024 * 1024,
    });
    await run({ root, build });
  } finally {
    await rm(root, { recursive: true, force: true });
  }
}

test('build refuses an omitted imported Windows module before reporting success', () => fixture(async ({ root, build }) => {
  const script = join(root, 'desktop/scripts/build.mjs');
  const text = await readFile(script, 'utf8');
  assert.equal(text.split("'generation-continuity.mjs', ").length, 2);
  await writeFile(script, text.replace("'generation-continuity.mjs', ", ''));
  const result = build();
  assert.equal(result.error, undefined);
  assert.notEqual(result.status, 0, 'missing imported module must stop build');
  assert.match(result.stderr, /generation-continuity\.mjs/);
}));

test('build refuses a missing dynamically selected model brand image', () => fixture(async ({ root, build }) => {
  await rm(join(root, 'desktop/src/assets/model-brands/qwen.png'));
  const result = build();
  assert.equal(result.error, undefined);
  assert.notEqual(result.status, 0, 'missing dynamic brand image must stop build');
  assert.match(result.stderr, /qwen\.png/);
}));

test('build rejects stale dist files and preserves their bytes for diagnosis', () => fixture(async ({ root, build }) => {
  await mkdir(join(root, 'desktop/dist'), { recursive: true });
  const stale = join(root, 'desktop/dist/old-renderer.mjs');
  await writeFile(stale, 'stale fixture bytes');
  const result = build();
  assert.equal(result.error, undefined);
  assert.notEqual(result.status, 0, 'undeclared old output must stop build');
  assert.match(result.stderr, /old-renderer\.mjs/);
  assert.equal(await readFile(stale, 'utf8'), 'stale fixture bytes');
}));

test('complete current renderer inputs build successfully with exact copied bytes', () => fixture(async ({ root, build }) => {
  const result = build();
  assert.equal(result.error, undefined);
  assert.equal(result.status, 0, result.stderr);
  for (const name of ['generation-continuity.mjs', 'app.mjs', 'island.html', 'assets/model-brands/qwen.png']) {
    assert.deepEqual(await readFile(join(root, 'desktop/dist', name)), await readFile(join(root, 'desktop/src', name)));
  }
}));

for (const [label, name, statement] of [
  ['literal dynamic import with whitespace', 'app.mjs', "import ('./omitted-build.mjs');"],
  ['compact export from', 'app.mjs', "export{sentinel}from'./omitted-build.mjs';"],
  ['CSS import url', 'styles.css', '@import url("./omitted-build.css");'],
]) test(`build checks ${label} references`, () => fixture(async ({ root, build }) => {
  const source = join(root, 'desktop/src', name);
  await writeFile(source, (await readFile(source, 'utf8')) + '\n' + statement);
  const result = build();
  assert.equal(result.error, undefined);
  assert.notEqual(result.status, 0);
  assert.match(result.stderr, /omitted-build\.(?:mjs|css)/);
}));

test('build refuses a renderer reference outside its output root', () => fixture(async ({ root, build }) => {
  const source = join(root, 'desktop/src/styles.css');
  await writeFile(source, (await readFile(source, 'utf8')) + '\n@import "../../outside.css";');
  const result = build();
  assert.equal(result.error, undefined);
  assert.notEqual(result.status, 0);
  assert.match(result.stderr, /引用越界/);
}));
