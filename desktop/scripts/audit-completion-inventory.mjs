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

function collectInventory() {
  const androidRoutes = read('app/src/main/java/com/nanzhufeng/ai/ui/P5AAdaptiveUi.kt');
  const androidApp = read('app/src/main/java/com/nanzhufeng/ai/ui/NanfengAiApp.kt');
  const appSource = read('desktop/src/app.mjs');
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
    ...desktopSourceEntries.flatMap(({ file, source }) => file.endsWith('app.mjs') ? [] : matches(source, /\baction:\s*['"]([a-z0-9-]+)['"]/g)),
  ]);
  const handledActions = sorted([
    ...matches(appSource, /\baction\s*[!=]==?\s*['"]([a-z0-9-]+)['"]/g),
    ...matches(appSource, /\[data-action=\\?["']([a-z0-9-]+)["']\]/g),
    ...matches(appSource, /dataset\.action\s*[!=]==?\s*['"]([a-z0-9-]+)['"]/g),
    ...literalArrayActionHandlers(appSource),
  ]);
  const invokedCommands = sorted(matches(desktopCombined, /\binvoke\(\s*['"]([a-zA-Z0-9_:|-]+)['"]/g));
  const rustCommands = sorted(matches(
    rustCombined,
    /#\[tauri::command\][\s\S]{0,160}?\b(?:pub\s+)?(?:async\s+)?fn\s+([a-zA-Z0-9_]+)/g,
  ));
  const handlerBody = read('desktop/src-tauri/src/lib.rs').match(/generate_handler!\[([\s\S]*?)\]\)/)?.[1] || '';
  const registeredCommands = sorted(matches(handlerBody, /\b([a-z][a-zA-Z0-9_]*)\s*,?/g));
  const localInvokes = invokedCommands.filter(command => !command.startsWith('plugin:'));
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
      invokesWithoutTestReference: localInvokes.filter(command => !testReferencedCommands.includes(command)),
      dialogKinds: sorted(matches(appSource, /\bkind:\s*['"]([a-z0-9-]+)['"]/g)),
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
  ) process.exitCode = 1;
}
