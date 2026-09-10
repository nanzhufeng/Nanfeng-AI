import assert from 'node:assert/strict';
import test from 'node:test';
import { collectInventory } from '../scripts/audit-completion-inventory.mjs';

test('every rendered Desktop action and invoked local command has a real handler', () => {
  const inventory = collectInventory();

  assert.deepEqual(inventory.desktop.unhandledRenderedActions, []);
  assert.deepEqual(inventory.desktop.invokesWithoutRustCommand, []);
  assert.deepEqual(inventory.desktop.invokesWithoutRegistration, []);
});

test('the generated route inventory includes the current Android and Desktop deep surfaces', () => {
  const inventory = collectInventory();

  for (const route of ['CONVERSATION', 'OCR', 'KNOWLEDGE', 'PROJECTS', 'MEMORY', 'SETTINGS']) {
    assert.ok(inventory.android.rootRoutes.includes(route), `missing Android route ${route}`);
  }
  for (const destination of ['PERSONALIZATION', 'MODEL_CONFIGURATION', 'CONVERSATION_COST', 'CONTEXT_SELECTIONS', 'RUN_DIAGNOSTICS', 'DATA_STORAGE', 'PRIVACY']) {
    assert.ok(inventory.android.settingsDestinations.includes(destination), `missing Android destination ${destination}`);
  }
  for (const page of ['personalization', 'model-configuration', 'conversation-cost', 'context-selections', 'diagnostics', 'data', 'privacy', 'account']) {
    assert.ok(inventory.desktop.settingsPages.includes(page), `missing Desktop page ${page}`);
  }
});
