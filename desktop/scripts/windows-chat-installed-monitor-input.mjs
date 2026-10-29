import assert from 'node:assert/strict';
import { readFile, lstat } from 'node:fs/promises';
import { createHash } from 'node:crypto';
import { dirname, resolve, sep } from 'node:path';

const digest = bytes => createHash('sha256').update(bytes).digest('hex').toUpperCase();
const json = bytes => JSON.parse(bytes.toString('utf8').replace(/^\uFEFF/, ''));
const ownedInstall = resolve('D:/CodexTemp/nfai-installed-acceptance.7d2cb30e-9606-4d88-bd91-bbc0fec53c92');
const ownedRoot = resolve('D:/CodexTemp/nanfeng-ai-windows-acceptance.00afec83-701e-4004-b210-18e974d9163b');

async function regularFile(path) {
  const info = await lstat(path);
  assert.ok(info.isFile() && !info.isSymbolicLink(), 'regular evidence or executable required');
  assert.ok(!(await lstat(dirname(path))).isSymbolicLink(), 'reparse parent rejected');
  return await readFile(path);
}

// Only the previously owned synthetic installation is eligible. A successful
// receipt never substitutes for observing the installed executable's bytes.
export async function resolveInstalledMonitorSource(input, desktopRoot) {
  const requested = ['installedRuntimeSource', 'upgradeOutcomePath', 'upgradeOutcomeSha256']
    .some(key => Object.hasOwn(input, key));
  if (!requested) return null;
  assert.equal(input.payloadVerifiedFromSetup, true, 'observed payload required');
  assert.match(input.rebuiltGuiSha256 || '', /^[A-F0-9]{64}$/);
  const installedSha256 = input.installedExeSha256 ?? input.rebuiltGuiSha256;
  assert.match(installedSha256, /^[A-F0-9]{64}$/);
  assert.match(input.setupSha256 || '', /^[A-F0-9]{64}$/);
  assert.match(input.upgradeOutcomeSha256 || '', /^[A-F0-9]{64}$/);
  assert.equal(typeof input.upgradeOutcomePath, 'string');
  assert.equal(typeof input.installedRuntimeSource, 'string');
  const executable = resolve(input.installedRuntimeSource);
  assert.equal(executable, resolve(ownedInstall, 'nanfeng-ai-desktop-spike.exe'), 'owned installed EXE required');
  const review = resolve(desktopRoot, '../docs/review') + sep;
  const outcomePath = resolve(input.upgradeOutcomePath);
  assert.ok(outcomePath.startsWith(review), 'upgrade evidence must remain in repository review');
  const bytes = await regularFile(outcomePath);
  assert.equal(digest(bytes), input.upgradeOutcomeSha256, 'upgrade receipt bytes changed');
  const outcome = json(bytes);
  assert.equal(outcome.installationStarted, true);
  assert.equal(outcome.installed, true);
  assert.equal(outcome.nativeVerified, true, 'installed migration chain required');
  assert.equal(outcome.installerExitCode, 0);
  assert.equal(outcome.dataUnchangedBeforeStartup, true);
  assert.equal(outcome.stage, 'verified-scoped-upgrade');
  assert.ok(!outcome.error, 'failed upgrade receipt rejected');
  assert.equal(outcome.actualInstalledExeSha256, installedSha256);
  assert.equal(typeof outcome.planPath, 'string');
  const planPath = resolve(outcome.planPath);
  assert.ok(planPath.startsWith(review), 'owned upgrade plan required');
  const plan = json(await regularFile(planPath));
  assert.equal(resolve(plan.installDir), ownedInstall);
  assert.equal(resolve(plan.root), ownedRoot);
  assert.equal(plan.payloadHashObservedInsideSetup, true);
  assert.equal(plan.setupSha256, input.setupSha256);
  assert.equal(plan.expectedInstalledExeSha256, installedSha256);
  const proofPath = resolve(plan.payloadVerification);
  assert.ok(proofPath.startsWith(review), 'owned payload proof required');
  const proofBytes = await regularFile(proofPath);
  assert.equal(digest(proofBytes), plan.payloadVerificationSha256);
  const proof = json(proofBytes);
  assert.equal(proof.actualPayloadExeSha256, installedSha256);
  assert.equal(proof.setupSha256, input.setupSha256);
  if (installedSha256 !== input.rebuiltGuiSha256) {
    assert.equal(proof.unpatchedGuiSha256, input.rebuiltGuiSha256);
    assert.equal(proof.exactTauriNsisMarkerTransform, true);
    assert.equal(proof.setupPayloadExtracted, true);
  }
  assert.equal(digest(await regularFile(executable)), installedSha256, 'installed EXE does not match observed payload');
  return { executable, sha256: installedSha256, compiledGuiSha256: input.rebuiltGuiSha256, outcomePath, outcomeSha256: input.upgradeOutcomeSha256, planPath };
}
