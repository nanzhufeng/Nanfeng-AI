import test from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { resolve } from 'node:path';

test('C02-C06 native fixture is dedicated, local-only, restartable, and fail-closed', async () => {
  const lib = await readFile(resolve(import.meta.dirname, '../src-tauri/src/lib.rs'), 'utf8');
  for (const token of [
    'NANFENG_AI_DESKTOP_C02_C06_ACCEPTANCE',
    'NANFENG_AI_DESKTOP_C02_C06_ACCEPTANCE_ROOT',
    '/tmp/nanfeng-ai-desktop-c02-c06-acceptance.',
    'seed_c02_c06_visual_acceptance',
    'C02-local-visual-fixture',
    'PROVIDER_NOT_ENABLED',
    'C02-C06 acceptance requires the isolated UI/schema diagnostic startup',
  ]) assert.ok(lib.includes(token), token);
});

test('C02-C06 acceptance packager gives the copy a unique identity without changing source bundle', async () => {
  const script = await readFile(resolve(import.meta.dirname, '../scripts/prepare-c02-c06-visual-acceptance.mjs'), 'utf8');
  for (const token of [
    "mkdtemp('/tmp/nanfeng-ai-desktop-c02-c06-bundle.')",
    "mkdtemp('/tmp/nanfeng-ai-desktop-c02-c06-acceptance.')",
    'CFBundleIdentifier',
    'sourceExecutableHash !== sourceExecutableHashAfter',
    "codesign', ['--verify', '--deep', '--strict'",
  ]) assert.ok(script.includes(token), token);
});
