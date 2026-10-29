import { spawnSync } from 'node:child_process';
import { lstat, readdir, rm, stat } from 'node:fs/promises';
import { basename, dirname, resolve } from 'node:path';

const hashPattern = /^[A-F0-9]{64}$/;
const inProgressGraceMs = 10 * 60 * 1000;

function runningHashes(outputRoot, rootNames) {
  const script = `$ErrorActionPreference='Stop'; Get-Process -Name 'nanfeng-ai-desktop-spike','NanfengAI-Windows-Acceptance','NanfengAI' -ErrorAction SilentlyContinue | ForEach-Object { if (-not $_.Path) { throw 'ExecutablePath unavailable' }; $_.Path }; exit 0`;
  const result = spawnSync('pwsh', ['-NoProfile', '-NonInteractive', '-Command', script], {
    encoding: 'utf8', windowsHide: true, timeout: 15000,
  });
  if (result.error || result.status !== 0) return null;
  const roots = rootNames
    .map(name => resolve(outputRoot, name).toLowerCase());
  const hashes = new Set();
  for (const line of result.stdout.split(/\r?\n/)) {
    const executable = line.trim();
    if (!executable) continue;
    const directory = dirname(executable);
    const parent = dirname(directory).toLowerCase();
    if (!roots.includes(parent)) continue;
    const hash = basename(directory).toUpperCase();
    if (!hashPattern.test(hash)) return null;
    hashes.add(hash);
  }
  return hashes;
}

async function pruneHashedOutput(outputRoot, currentHash, rootNames, options = {}) {
  const current = currentHash.toUpperCase();
  if (!hashPattern.test(current)) throw new Error('Invalid current acceptance hash');
  const active = options.activeHashes ?? runningHashes(outputRoot, rootNames);
  if (active === null) return { skipped: 'running process inspection failed', removed: 0, retained: 0 };
  const keep = new Set([current, ...active]);
  const now = options.now ?? Date.now();
  const roots = rootNames
    .map(name => resolve(outputRoot, name));
  let removed = 0;
  let retained = 0;
  for (const root of roots) {
    let entries;
    try { entries = await readdir(root, { withFileTypes: true }); }
    catch (error) { if (error.code === 'ENOENT') continue; throw error; }
    for (const entry of entries) {
      const hash = entry.name.toUpperCase();
      if (!entry.isDirectory() || !hashPattern.test(hash)) continue;
      const path = resolve(root, entry.name);
      const info = await lstat(path);
      if (info.isSymbolicLink() || keep.has(hash) || now - (await stat(path)).mtimeMs < inProgressGraceMs) {
        retained++;
        continue;
      }
      try { await rm(path, { recursive: true }); removed++; }
      catch (error) {
        retained++;
        console.warn(`Acceptance output retained: ${path}: ${error.code || error.message}`);
      }
    }
  }
  return { removed, retained };
}

export function pruneWindowsAcceptanceOutput(outputRoot, currentHash, options) {
  return pruneHashedOutput(outputRoot, currentHash,
    ['windows-acceptance', 'windows-acceptance-binaries'], options);
}

export function pruneWindowsUserOutput(outputRoot, currentHash, options) {
  return pruneHashedOutput(outputRoot, currentHash, ['windows-user'], options);
}

export function pruneWindowsReleaseAcceptanceOutput(outputRoot, currentHash, options = {}) {
  const active = options.activeHashes ?? runningHashes(outputRoot,
    ['windows-acceptance-release', 'windows-acceptance-binaries']);
  if (active === null) return { skipped: 'running process inspection failed', removed: 0, retained: 0 };
  return pruneHashedOutput(outputRoot, currentHash, ['windows-acceptance-release'],
    { ...options, activeHashes: active });
}
