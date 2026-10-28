import {readFileSync} from 'node:fs';
import { MODEL_SERVICE_PREVIEW_SETTINGS } from '../../src/android-settings-shell.mjs';
import { renderChatFirstShell } from '../../src/chat-shell.mjs';
const profiles=JSON.parse(readFileSync(new URL('../../../app/src/main/assets/model_profiles.json',import.meta.url))).profiles;
export const enabledModelCatalog = MODEL_SERVICE_PREVIEW_SETTINGS.map(service => ({ ...service, enabled: true, presets:service.presets.map(p=>({...p,modelId:profiles.find(row=>row.presetId===p.id)?.modelId||p.modelId,nativeInputTypes:Object.entries(profiles.find(row=>row.presetId===p.id)?.capabilities||{}).filter(([key,value])=>value && ['image','pdf','audio','video'].includes(key)).map(([key])=>key==='pdf'?'file':key)})) }));
// Catalog layout fixtures explicitly enable providers; product defaults stay disabled.
export const renderConfiguredChatShell = options => renderChatFirstShell({ ...options, modelServiceSettings: options.modelServiceSettings ?? enabledModelCatalog });
