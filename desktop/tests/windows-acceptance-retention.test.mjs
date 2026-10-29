import assert from 'node:assert/strict';
import { test } from 'node:test';
import { mkdtemp, mkdir, rm, utimes, writeFile } from 'node:fs/promises';
import { existsSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { pruneWindowsAcceptanceOutput, pruneWindowsUserOutput } from '../scripts/prune-windows-acceptance-output.mjs';

test('acceptance retention keeps the current and running versions while removing stale copies', async () => {
  const root = await mkdtemp(join(tmpdir(), 'nfai-retention-'));
  const [old, current, running, recent] = ['A', 'B', 'C', 'D'].map(value => value.repeat(64));
  const now = Date.now();
  try {
    for (const kind of ['windows-acceptance', 'windows-acceptance-binaries']) {
      for (const hash of [old, current, running, recent]) {
        const directory = join(root, kind, hash);
        await mkdir(directory, { recursive: true });
        await writeFile(join(directory, 'test.bin'), hash);
        if (hash !== recent) await utimes(directory, new Date(now - 60 * 60_000), new Date(now - 60 * 60_000));
      }
    }
    const first = await pruneWindowsAcceptanceOutput(root, current, {
      activeHashes: new Set([running]), now,
    });
    assert.equal(first.removed, 2);
    assert.equal(first.retained, 6);
    for (const kind of ['windows-acceptance', 'windows-acceptance-binaries']) {
      for (const hash of [current, running, recent]) {
        assert.equal(existsSync(join(root, kind, hash)), true);
      }
    }
    const second = await pruneWindowsAcceptanceOutput(root, current, {
      activeHashes: new Set(), now: now + 11 * 60_000,
    });
    assert.equal(second.removed, 4);
    assert.equal(second.retained, 2);
  } finally {
    await rm(root, { recursive: true, force: true });
  }
});

test('daily-use retention keeps only the current build once old copies are idle', async () => {
  const root = await mkdtemp(join(tmpdir(), 'nfai-user-retention-'));
  const old = 'E'.repeat(64);
  const current = 'F'.repeat(64);
  try {
    for (const hash of [old, current]) {
      const directory = join(root, 'windows-user', hash);
      await mkdir(directory, { recursive: true });
      await writeFile(join(directory, 'NanfengAI.exe'), hash);
      await utimes(directory, new Date(0), new Date(0));
    }
    const result = await pruneWindowsUserOutput(root, current, { activeHashes: new Set() });
    assert.equal(result.removed, 1);
    assert.equal(existsSync(join(root, 'windows-user', old)), false);
    assert.equal(existsSync(join(root, 'windows-user', current)), true);
  } finally {
    await rm(root, { recursive: true, force: true });
  }
});
