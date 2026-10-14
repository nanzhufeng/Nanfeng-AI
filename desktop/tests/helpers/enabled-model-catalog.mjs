import { MODEL_SERVICE_PREVIEW_SETTINGS } from '../../src/android-settings-shell.mjs';
import { renderChatFirstShell } from '../../src/chat-shell.mjs';
export const enabledModelCatalog = MODEL_SERVICE_PREVIEW_SETTINGS.map(service => ({ ...service, enabled: true }));
// Catalog layout fixtures explicitly enable providers; product defaults stay disabled.
export const renderConfiguredChatShell = options => renderChatFirstShell({ ...options, modelServiceSettings: options.modelServiceSettings ?? enabledModelCatalog });
