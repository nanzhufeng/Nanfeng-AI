import assert from 'node:assert/strict';
import { readFile, writeFile, mkdir, copyFile, stat } from 'node:fs/promises';
import { createHash } from 'node:crypto';
import { resolve, join, sep } from 'node:path';
import { fileURLToPath } from 'node:url';
import { alignmentProfile, alignmentCandidateNamespace } from './windows-alignment-profile.mjs';

const desktop = resolve(fileURLToPath(new URL('..', import.meta.url)));
const repo = resolve(desktop, '..');
const profile = alignmentProfile(process.argv.slice(3));
assert.ok(process.argv[2], 'Explicit verified native runtime evidence required');
const evidencePath = resolve(process.argv[2]);
assert.ok(evidencePath.startsWith(join(repo, 'docs', 'review') + sep));
assert.ok((await stat(evidencePath)).size <= 128 * 1024);
const evidence = JSON.parse(await readFile(evidencePath, 'utf8'));
assert.equal(evidence.pass, true);
const receipt = JSON.parse(await readFile(join(desktop, 'output', alignmentCandidateNamespace(profile), 'current.json'), 'utf8'));
assert.equal(receipt.identifier, 'com.nanzhufeng.ai.desktop.windowsacceptance');
assert.equal(evidence.candidateSha256, receipt.sha256);
assert.equal(receipt.buildProfile ?? 'debug', profile);
const runtime = evidence.runtimeInfo;
assert.equal(runtime.platform, 'windows');
assert.equal(runtime.buildProfile, profile);
assert.match(runtime.sourceInputSha256, /^[0-9a-f]{64}$/);
const binary = resolve(desktop, process.env.CARGO_TARGET_DIR || 'src-tauri/target', `x86_64-pc-windows-msvc/${profile}/nanfeng-ai-desktop-spike.exe`);
assert.equal(createHash('sha256').update(await readFile(binary)).digest('hex').toUpperCase(), receipt.sha256);
const output = join(desktop, 'src-tauri/resources/alignment-launcher');
await mkdir(join(output, 'scripts'), { recursive: true });
for (const name of ['start-windows-acceptance.ps1', 'verify-windows-media-tools.ps1']) {
  await copyFile(join(desktop, 'scripts', name), join(output, 'scripts', name));
}
await writeFile(join(output, 'executable.json'), JSON.stringify({
  identifier: receipt.identifier, sha256: receipt.sha256,
  version: runtime.version, buildProfile: runtime.buildProfile,
  entryRootPolicy: 'shared-alignment-root-v1',
  sourceInputSha256: runtime.sourceInputSha256, sourceInputFileCount: runtime.sourceInputFileCount, sourceInputRecipe: runtime.sourceInputRecipe,
  gitCommit: runtime.gitCommit, gitWorktreeDirty: runtime.gitWorktreeDirty,
}, null, 2) + '\n');
console.log(JSON.stringify({ prepared: true, applicationSha256: receipt.sha256, sourceInputSha256: runtime.sourceInputSha256, version: runtime.version }));
