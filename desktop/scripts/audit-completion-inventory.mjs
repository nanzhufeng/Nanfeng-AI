import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const scriptDirectory = path.dirname(fileURLToPath(import.meta.url));
const desktopRoot = path.resolve(scriptDirectory, '..');
const repositoryRoot = path.resolve(desktopRoot, '..');

function read(relativePath) {
  return fs.readFileSync(path.join(repositoryRoot, relativePath), 'utf8');
}

function listFiles(directory, suffix) {
  return fs.readdirSync(directory, { withFileTypes: true }).flatMap(entry => {
    const absolute = path.join(directory, entry.name);
    if (entry.isDirectory()) return listFiles(absolute, suffix);
    return entry.isFile() && entry.name.endsWith(suffix) ? [absolute] : [];
  });
}

function matches(source, expression, group = 1) {
  return [...source.matchAll(expression)].map(match => match[group]);
}

function sorted(values) {
  return [...new Set(values)].sort((left, right) => left.localeCompare(right));
}

function enumEntries(source, enumName) {
  const body = source.match(new RegExp(`enum class ${enumName}(?:\\([^)]*\\))?\\s*\\{([\\s\\S]*?);`))?.[1] || '';
  return sorted(matches(body, /^\s*([A-Z][A-Z0-9_]*)\s*(?:\(|,|$)/gm));
}

function literalArrayActionHandlers(source) {
  return [...source.matchAll(/\[([^\]]+)\]\.includes\(action\)/g)].flatMap(match =>
    matches(match[1], /['"]([a-z0-9-]+)['"]/g),
  );
}

function mappedRenderedActions(source) {
  const result = [];
  for (const [, name] of source.matchAll(/data-action=\\?["']\$\{([a-zA-Z_$][\w$]*)\}["']/g)) {
    const escaped = name.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
    const map = source.match(new RegExp(`\\b(?:const|let)\\s+${escaped}\\s*=\\s*\\{([\\s\\S]*?)\\}\\s*\\[`));
    if (map) result.push(...matches(map[1], /:\s*['"]([a-z0-9-]+)['"]/g));
  }
  return result;
}

function collectInventory() {
  const androidRoutes = read('app/src/main/java/com/nanzhufeng/ai/ui/P5AAdaptiveUi.kt');
  const androidApp = read('app/src/main/java/com/nanzhufeng/ai/ui/NanfengAiApp.kt');
  const appSource = read('desktop/src/app.mjs');
  // Mounted import controllers bind their handlers to the captured area owner.
  const mountedHandlerSource = appSource + '\n' + read('desktop/src/pdf-knowledge-tasks.mjs') + '\n' + read('desktop/src/web-knowledge-tasks.mjs') + '\n' + read('desktop/src/knowledge-duplicates.mjs') + '\n' + read('desktop/src/domain-editor-conflict.mjs');
  const desktopSourceEntries = listFiles(path.join(desktopRoot, 'src'), '.mjs').map(file => ({
    file,
    source: fs.readFileSync(file, 'utf8'),
  }));
  const desktopSources = desktopSourceEntries.map(entry => entry.source);
  const rustSources = listFiles(path.join(desktopRoot, 'src-tauri', 'src'), '.rs').map(file => fs.readFileSync(file, 'utf8'));
  const testCombined = listFiles(path.join(desktopRoot, 'tests'), '.mjs').map(file => fs.readFileSync(file, 'utf8')).join('\n');
  const rustCombined = rustSources.join('\n');
  const desktopCombined = desktopSources.join('\n');

  const renderedActions = sorted([
    ...matches(desktopCombined, /data-action=\\?["']([a-z0-9-]+)["']/g),
    ...desktopSources.flatMap(mappedRenderedActions),
    ...desktopSourceEntries.flatMap(({ file, source }) => file.endsWith('app.mjs') ? [] : matches(source, /\baction:\s*['"]([a-z0-9-]+)['"]/g)),
  ]);
  const handledActions = sorted([
    ...matches(mountedHandlerSource, /\baction\s*[!=]==?\s*['"]([a-z0-9-]+)['"]/g),
    ...matches(mountedHandlerSource, /\[data-action=\\?["']([a-z0-9-]+)["']\]/g),
    ...matches(mountedHandlerSource, /dataset\.action\s*[!=]==?\s*['"]([a-z0-9-]+)['"]/g),
    ...literalArrayActionHandlers(mountedHandlerSource),
  ]);
  // These helpers bind the original area/preview/backup owner. Their literal
  // commands need the same Rust, registration and ACL checks as direct invoke.
  // Variable commands still require owner-specific semantic review.
  const invokedCommands = sorted([
    ...matches(desktopCombined, /\b(?:invoke|invokeOwner)\(\s*['"]([a-zA-Z0-9_:|-]+)['"]/g),
    ...matches(desktopCombined, /\b(?:invokeForAuthorizedDataAreaOperation|invokeForDataArea|invokePreviewOwner|invokeSelectedBackup)\(\s*[^,\n]+,\s*['"]([a-zA-Z0-9_:|-]+)['"]/g),
  ]);
  const rustCommands = sorted(matches(
    rustCombined,
    /#\[tauri::command\][\s\S]{0,160}?\b(?:pub\s+)?(?:async\s+)?fn\s+([a-zA-Z0-9_]+)/g,
  ));
  const handlerBody = read('desktop/src-tauri/src/lib.rs').match(/generate_handler!\[([\s\S]*?)\]\)/)?.[1] || '';
  const registeredCommands = sorted(matches(handlerBody, /\b([a-z][a-zA-Z0-9_]*)\s*,?/g));
  const localInvokes = invokedCommands.filter(command => !command.startsWith('plugin:'));
  const capability = JSON.parse(read('desktop/src-tauri/capabilities/default.json'));
  const customPermissions = new Map(read('desktop/src-tauri/permissions/default.toml')
    .split('[[permission]]').slice(1)
    .map(block => [
      block.match(/^identifier\s*=\s*"([^"]+)"/m)?.[1],
      matches(block.match(/^commands\.allow\s*=\s*\[([^\]]*)\]/m)?.[1] || '', /"([a-zA-Z0-9_]+)"/g),
    ]));
  const islandCapability = JSON.parse(read('desktop/src-tauri/capabilities/generation-island.json'));
  const islandModuleNames = new Set(['island-window.mjs', 'nanfeng-island.mjs']);
  const islandUsedByMain = desktopSourceEntries.filter(entry => !islandModuleNames.has(path.basename(entry.file)))
    .some(entry => /\b(?:from\s*|import\s*(?:\(\s*)?)['"][^'"]*(?:island-window|nanfeng-island)\.mjs['"]/.test(entry.source));
  // The standalone island bootstrap owns its own narrow capability. A main-window
  // invocation of the same command still has to pass the main-window permission.
  const windowScopes = [
    { window: 'main', capability, entries: desktopSourceEntries.filter(entry => !islandModuleNames.has(path.basename(entry.file)) || islandUsedByMain) },
    { window: 'generation-island', capability: islandCapability, entries: desktopSourceEntries.filter(entry => islandModuleNames.has(path.basename(entry.file))) },
  ].map(scope => {
    const source = scope.entries.map(entry => entry.source).join('\n');
    const commands = sorted([
      ...matches(source, /\b(?:invoke|invokeOwner)\(\s*['"]([a-zA-Z0-9_:|-]+)['"]/g),
      ...matches(source, /\b(?:invokeForAuthorizedDataAreaOperation|invokeForDataArea|invokePreviewOwner|invokeSelectedBackup)\(\s*[^,\n]+,\s*['"]([a-zA-Z0-9_:|-]+)['"]/g),
    ]).filter(command => !command.startsWith('plugin:'));
    const permissions = scope.capability.permissions.filter(permission => !permission.includes(':'));
    const allowed = sorted(permissions.flatMap(permission => customPermissions.get(permission) || []));
    return { window: scope.window, capability: scope.capability.identifier, permissions, invokedCommands: commands, allowedCommands: allowed,
      invokesWithoutPermission: commands.filter(command => !scope.capability.windows.includes(scope.window) || !allowed.includes(command)) };
  });
  const activeCustomPermissions = sorted(windowScopes.flatMap(scope => scope.permissions));
  const testReferencedActions = sorted(renderedActions.filter(action => testCombined.includes(action)));
  const testReferencedCommands = sorted(localInvokes.filter(command => testCombined.includes(command)));

  return {
    android: {
      rootRoutes: enumEntries(androidRoutes, 'P5ARoute'),
      settingsDestinations: enumEntries(androidApp, 'SettingsDestination'),
      modalCallSites: sorted(matches(
        listFiles(path.join(repositoryRoot, 'app', 'src', 'main', 'java'), '.kt').map(file => fs.readFileSync(file, 'utf8')).join('\n'),
        /\b(AlertDialog|Dialog|DropdownMenu|Popup)\s*\(/g,
      )),
    },
    desktop: {
      settingsPages: sorted([
        ...matches(desktopCombined, /\bpage\s*===\s*['"]([a-z0-9-]+)['"]/g),
        ...matches(desktopCombined, /\bpage:\s*['"]([a-z0-9-]+)['"]/g),
        ...matches(desktopCombined, /data-page=\\?["']([a-z0-9-]+)["']/g),
      ]),
      renderedActions,
      handledActions,
      unhandledRenderedActions: renderedActions.filter(action => !handledActions.includes(action)),
      actionsWithoutTestReference: renderedActions.filter(action => !testReferencedActions.includes(action)),
      invokedCommands,
      rustCommands,
      registeredCommands,
      invokesWithoutRustCommand: localInvokes.filter(command => !rustCommands.includes(command)),
      invokesWithoutRegistration: localInvokes.filter(command => !registeredCommands.includes(command)),
      windowScopes,
      invokesWithoutPermission: sorted(windowScopes.flatMap(scope => scope.invokesWithoutPermission)),
      capabilityPermissionsMissingDefinition: activeCustomPermissions.filter(permission => !customPermissions.has(permission)),
      invokesWithoutTestReference: localInvokes.filter(command => !testReferencedCommands.includes(command)),
      dialogKinds: sorted(matches(mountedHandlerSource, /\bkind:\s*['"]([a-z0-9-]+)['"]/g)),
    },
  };
}

function summarize(inventory) {
  const { android, desktop } = inventory;
  return [
    'NANFENG_AI_COMPLETION_INVENTORY',
    `android_root_routes=${android.rootRoutes.length}`,
    `android_settings_destinations=${android.settingsDestinations.length}`,
    `android_modal_primitives=${android.modalCallSites.join(',') || '-'}`,
    `desktop_settings_pages=${desktop.settingsPages.length}`,
    `desktop_rendered_actions=${desktop.renderedActions.length}`,
    `desktop_handled_actions=${desktop.handledActions.length}`,
    `desktop_unhandled_rendered_actions=${desktop.unhandledRenderedActions.length}:${desktop.unhandledRenderedActions.join(',') || '-'}`,
    `desktop_actions_without_test_reference=${desktop.actionsWithoutTestReference.length}`,
    `desktop_invoked_commands=${desktop.invokedCommands.length}`,
    `desktop_rust_commands=${desktop.rustCommands.length}`,
    `desktop_registered_commands=${desktop.registeredCommands.length}`,
    `desktop_invokes_without_rust_command=${desktop.invokesWithoutRustCommand.length}:${desktop.invokesWithoutRustCommand.join(',') || '-'}`,
    `desktop_invokes_without_registration=${desktop.invokesWithoutRegistration.length}:${desktop.invokesWithoutRegistration.join(',') || '-'}`,
    `desktop_invokes_without_permission=${desktop.invokesWithoutPermission.length}:${desktop.invokesWithoutPermission.join(',') || '-'}`,
    `desktop_capability_permissions_missing_definition=${desktop.capabilityPermissionsMissingDefinition.length}:${desktop.capabilityPermissionsMissingDefinition.join(',') || '-'}`,
    `desktop_invokes_without_test_reference=${desktop.invokesWithoutTestReference.length}`,
    `desktop_dialog_kinds=${desktop.dialogKinds.length}`,
  ].join('\n');
}

export { collectInventory, summarize };

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  const inventory = collectInventory();
  if (process.argv.includes('--json')) console.log(JSON.stringify(inventory, null, 2));
  else console.log(summarize(inventory));
  if (
    inventory.desktop.unhandledRenderedActions.length
    || inventory.desktop.invokesWithoutRustCommand.length
    || inventory.desktop.invokesWithoutRegistration.length
    || inventory.desktop.invokesWithoutPermission.length
    || inventory.desktop.capabilityPermissionsMissingDefinition.length
  ) process.exitCode = 1;
}
