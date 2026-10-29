import assert from 'node:assert/strict';
import test from 'node:test';
import { collectInventory } from '../scripts/audit-completion-inventory.mjs';

test('every rendered Desktop action and invoked local command has a real handler', () => {
  const inventory = collectInventory();

  assert.deepEqual(inventory.desktop.unhandledRenderedActions, []);
  assert.deepEqual(inventory.desktop.invokesWithoutRustCommand, []);
  assert.deepEqual(inventory.desktop.invokesWithoutRegistration, []);
  assert.deepEqual(inventory.desktop.invokesWithoutPermission, []);
  assert.deepEqual(inventory.desktop.capabilityPermissionsMissingDefinition, []);
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


test('owner-bound commands participate in handler, registration and permission inventory', () => {
  const inventory = collectInventory();
  for (const command of ['submit_desktop_ordinary_chat','retry_desktop_ordinary_chat','export_desktop_local_backup','restore_desktop_local_backup','choose_desktop_storage_location','export_desktop_attachment_to_selected_path']) {
    assert.ok(inventory.desktop.invokedCommands.includes(command), `owner-bound command omitted: ${command}`);
  }
});

test('the mounted webpage controller participates in user entry and IPC inventory', () => {
  const { desktop } = collectInventory();
  for (const action of ['select-web-knowledge','fetch-web-knowledge','select-web-knowledge-task','more-web-knowledge-tasks','review-web-knowledge','confirm-web-knowledge','skip-web-knowledge','cancel-web-knowledge','retry-web-knowledge']) {
    assert.ok(desktop.renderedActions.includes(action), `missing webpage entry ${action}`);
    assert.ok(desktop.handledActions.includes(action), `missing mounted webpage handler ${action}`);
  }
  for (const command of ['queue','process','retry','read_latest','list','read','confirm','skip','cancel'].map(prefix => ['confirm','skip'].includes(prefix) ? `${prefix}_desktop_web_knowledge_item` : `${prefix}_desktop_web_knowledge${prefix === 'list' ? '_tasks' : ''}`)) {
    assert.ok(desktop.invokedCommands.includes(command), `missing webpage IPC ${command}`);
  }
  assert.ok(desktop.dialogKinds.includes('web-knowledge-url'));
  assert.ok(desktop.dialogKinds.includes('web-knowledge-review'));
});

test('standalone island invocations retain their own narrow window capability', () => {
  const { windowScopes } = collectInventory().desktop;
  const main = windowScopes.find(scope => scope.window === 'main');
  const island = windowScopes.find(scope => scope.window === 'generation-island');
  assert.deepEqual(island.invokedCommands, ['generation_island_action','generation_island_visibility','read_generation_island']);
  assert.deepEqual(island.permissions, ['allow-generation-island']);
  assert.deepEqual(island.allowedCommands, island.invokedCommands);
  assert.deepEqual(island.invokesWithoutPermission, []);
  for (const command of island.invokedCommands) assert.ok(!main.allowedCommands.includes(command));
});

test('shared media header close actions participate in the entry and handler inventory', () => {
  const {renderedActions,handledActions}=collectInventory().desktop;
  for(const kind of ['image','pdf','video','audio','text','archive']) {
    const action=`close-${kind}-preview`;
    assert.ok(renderedActions.includes(action), `dynamic header omitted: ${action}`);
    assert.ok(handledActions.includes(action), `close handler omitted: ${action}`);
  }
});
