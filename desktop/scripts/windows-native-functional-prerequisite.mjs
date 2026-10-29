import assert from 'node:assert/strict';

// Functional tests must be serial with a running stability test. They do not
// certify stability; a failed, fully cleaned run remains a failed qualification.
export function nativeFunctionalPrerequisite({ host, bounded, stability, compiledSha256, installedSha256 }) {
  assert.ok(['COMPLETED', 'FAILED'].includes(host?.phase), 'Stability host is not terminal');
  assert.ok(Number.isInteger(host.exitCode), 'Controller exit code is missing');
  assert.ok(Number.isInteger(host.controllerPid) && host.controllerPid > 0, 'Controller identity is missing');
  assert.equal(host.phase, host.exitCode === 0 ? 'COMPLETED' : 'FAILED', 'Host phase contradicts exit code');
  assert.equal(bounded?.exitCode, host.exitCode, 'Bounded outcome does not match host');
  assert.equal(bounded?.controllerPid, host.controllerPid, 'Different controller outcome');
  assert.ok(Number.isFinite(Date.parse(bounded?.endedUtc)), 'Bounded cleanup is not terminal');
  assert.equal(bounded?.ownedProcessesRemaining, 0, 'Owned processes are still running or cleanup is unproven');
  assert.equal(bounded?.installedStability, '24h', 'Different bounded mode');
  assert.equal(bounded?.candidateSha256, compiledSha256, 'Different compiled candidate');
  assert.equal(stability?.candidateSha256, installedSha256, 'Different installed candidate');
  return Object.freeze({
    scope: 'SERIAL_FUNCTIONAL_EXECUTION_ONLY_NOT_RELEASE_QUALIFICATION',
    priorHostPhase: host.phase,
    priorStabilityState: stability.state ?? 'NOT_RECORDED',
    priorExitCode: host.exitCode,
    priorAccepted24h: stability.accepted24h === true,
    priorNormalCloseAndColdVerified: stability.normalCloseAndColdVerified === true,
    qualified24h: host.phase === 'COMPLETED' && stability.accepted24h === true
      && stability.normalCloseAndColdVerified === true,
    ownedProcessesRemaining: 0,
  });
}
