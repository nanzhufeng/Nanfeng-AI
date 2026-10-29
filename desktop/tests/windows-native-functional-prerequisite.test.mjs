import assert from 'node:assert/strict';
import test from 'node:test';
import { nativeFunctionalPrerequisite } from '../scripts/windows-native-functional-prerequisite.mjs';

const fixture = () => ({
  host: { phase: 'FAILED', exitCode: 1, controllerPid: 123 },
  bounded: { exitCode: 1, controllerPid: 123, endedUtc: '2026-10-05T12:00:00Z', ownedProcessesRemaining: 0, installedStability: '24h', candidateSha256: 'COMPILED' },
  stability: { candidateSha256: 'INSTALLED', accepted24h: false, normalCloseAndColdVerified: false },
  compiledSha256: 'COMPILED', installedSha256: 'INSTALLED',
});

test('a failed terminal stability run permits separate functional evidence without becoming qualified', () => {
  const input = fixture();
  const before = JSON.stringify(input);
  const result = nativeFunctionalPrerequisite(input);
  assert.equal(result.qualified24h, false);
  assert.equal(result.priorHostPhase, 'FAILED');
  assert.equal(result.priorExitCode, 1);
  assert.equal(JSON.stringify(input), before, 'raw failure evidence must remain unchanged');
});

test('qualification stays explicit and requires all original success facts', () => {
  const input = fixture();
  input.host.phase = 'COMPLETED'; input.host.exitCode = input.bounded.exitCode = 0;
  assert.equal(nativeFunctionalPrerequisite(input).qualified24h, false);
  input.stability.accepted24h = true;
  assert.equal(nativeFunctionalPrerequisite(input).qualified24h, false);
  input.stability.normalCloseAndColdVerified = true;
  assert.equal(nativeFunctionalPrerequisite(input).qualified24h, true);
});

test('running, incomplete, contradictory and foreign outcomes remain refused', () => {
  for (const mutate of [
    input => { input.host.phase = 'RUNNING'; },
    input => { input.host.exitCode = null; },
    input => { input.host.phase = 'COMPLETED'; },
    input => { input.bounded.ownedProcessesRemaining = 1; },
    input => { delete input.bounded.ownedProcessesRemaining; },
    input => { delete input.bounded.endedUtc; },
    input => { input.bounded.controllerPid = 456; },
    input => { delete input.host.controllerPid; delete input.bounded.controllerPid; },
    input => { input.bounded.exitCode = 0; },
    input => { input.bounded.installedStability = 'smoke'; },
    input => { input.bounded.candidateSha256 = 'OTHER'; },
    input => { input.stability.candidateSha256 = 'OTHER'; },
  ]) {
    const input = fixture(); mutate(input);
    assert.throws(() => nativeFunctionalPrerequisite(input));
  }
});
