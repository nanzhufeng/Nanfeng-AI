import assert from 'node:assert/strict';
import test from 'node:test';
import { readFile } from 'node:fs/promises';
import {
  DESKTOP_COMPARE_EXECUTION_PROVIDER,
  DESKTOP_COMPARE_SETTINGS_PROJECTION,
  DesktopCompareBlocker,
  DesktopCompareExecutionOwner,
} from '../src/desktop-compare-execution-owner.mjs';

test('Desktop Compare owner starts fail-closed on the fixed OpenRouter-compatible boundary', () => {
  const owner = new DesktopCompareExecutionOwner();
  assert.deepEqual(owner.status(), { provider: 'openrouter', protocol: 'OPENAI_COMPATIBLE', enabled: false, blocker: DesktopCompareBlocker.CREDENTIAL_NOT_CONFIGURED });
  assert.deepEqual(DESKTOP_COMPARE_EXECUTION_PROVIDER, { id: 'openrouter', protocol: 'OPENAI_COMPATIBLE', endpoint: 'https://openrouter.ai/api/v1/chat/completions' });
});

test('Desktop Compare Settings projection is fixed, key-free, and blocks unknown model pricing', () => {
  assert.equal(DESKTOP_COMPARE_SETTINGS_PROJECTION.credentialPresence, 'NOT_CHECKED');
  assert.equal(DESKTOP_COMPARE_SETTINGS_PROJECTION.executionState, 'BLOCKED');
  assert.deepEqual(DESKTOP_COMPARE_SETTINGS_PROJECTION.presets.map(item => item.logicalModel), ['ChatGPT', 'Claude']);
  assert.ok(DESKTOP_COMPARE_SETTINGS_PROJECTION.presets.every(item => item.price === '价格未知，禁止执行'));
});

test('Desktop Compare owner rejects empty drafts and attachments before readiness', () => {
  const owner = new DesktopCompareExecutionOwner();
  assert.deepEqual(owner.requestDirectCompare({ hasText: false }), { outcome: 'BLOCKED', blocker: DesktopCompareBlocker.EMPTY_DRAFT });
  assert.deepEqual(owner.requestDirectCompare({ hasText: true, attachmentCount: 1 }), { outcome: 'BLOCKED', blocker: DesktopCompareBlocker.ATTACHMENTS_NOT_SUPPORTED });
});

test('Desktop Compare owner fails closed for unknown model price and absent transport', () => {
  assert.deepEqual(new DesktopCompareExecutionOwner({ credentialPresent: true }).requestDirectCompare({ hasText: true }), { outcome: 'BLOCKED', blocker: DesktopCompareBlocker.MODEL_OR_PRICE_UNVERIFIED });
  assert.deepEqual(new DesktopCompareExecutionOwner({ credentialPresent: true, fixedModelsVerified: true, fixedPricesKnown: true }).requestDirectCompare({ hasText: true }), { outcome: 'BLOCKED', blocker: DesktopCompareBlocker.EXECUTION_NOT_COMPOSED });
});

test('the owner has no content, credential, persistence, or transport parameters', async () => {
  const source = await readFile(new URL('../src/desktop-compare-execution-owner.mjs', import.meta.url), 'utf8');
  for (const forbidden of ['invoke(', 'fetch(', 'Authorization', 'localStorage', 'console.', 'draftText', 'apiKey']) assert.ok(!source.includes(forbidden));
});
