// Cloud rows are restored local workspaces presented in a separate list.  A
// route must therefore finish the workspace read before it publishes the
// selection, while optional model preferences must never delay the transcript.
export function createCloudConversationOpenRoute({
  reuse,
  load,
  loadPreferences,
  onPending,
  onOpened,
  onPreferences,
  onPreferencesFailure,
  onFailure,
}) {
  let generation = 0;

  return async function openCloudConversationRoute(route) {
    const resident = reuse?.(route);
    if (resident?.alreadySelected) return { status: 'ALREADY_OPEN' };
    const requestGeneration = ++generation;
    if (resident) {
      onOpened(route, resident, { reused: true });
      void Promise.resolve(loadPreferences(route, resident)).then(preferences => {
        if (requestGeneration === generation) onPreferences(route, preferences);
      }).catch(error => {
        if (requestGeneration === generation) onPreferencesFailure(route, error);
      });
      return { status: 'REUSED' };
    }
    onPending(route);
    try {
      const result = await load(route);
      if (requestGeneration !== generation) return { status: 'SUPERSEDED' };
      onOpened(route, result, { reused: false });
      void Promise.resolve(loadPreferences(route, result)).then(preferences => {
        if (requestGeneration === generation) onPreferences(route, preferences);
      }).catch(error => {
        if (requestGeneration === generation) onPreferencesFailure(route, error);
      });
      return { status: 'OPENED' };
    } catch (error) {
      if (requestGeneration === generation) onFailure(route, error);
      return { status: 'FAILED' };
    }
  };
}
