/**
 * C-16 is a read-only visual fixture. Values are frozen from the current Android
 * AppearanceSettings and shared Compose palette; the production theme owner must not import it.
 */
export const C16_APPEARANCE_CASES = Object.freeze([
  { id: 'system-light', mode: 'system', prefersDark: false, resolved: 'light' },
  { id: 'system-dark', mode: 'system', prefersDark: true, resolved: 'dark' },
  { id: 'light', mode: 'light', prefersDark: true, resolved: 'light' },
  { id: 'dark', mode: 'dark', prefersDark: false, resolved: 'dark' },
]);

export const C16_FONT_CASES = Object.freeze([
  { id: 'small', label: '小', scale: 0.80 },
  { id: 'standard', label: '标准', scale: 1.00 },
  { id: 'large', label: '大', scale: 1.24 },
]);

export const C16_LAYER_CASES = Object.freeze([
  { id: 'model-root', group: 'model', label: '模型根层' },
  { id: 'model-daily', group: 'model', label: '日常候选' },
  { id: 'model-deep', group: 'model', label: '深度候选' },
  { id: 'add-root', group: 'add', label: '加号根层' },
  { id: 'style', group: 'style', label: '基础风格和语气' },
  { id: 'search-history', group: 'search', label: '搜索与历史弹层' },
  { id: 'settings-theme', group: 'settings', label: '设置与主题选择弹层' },
]);

export const C16_ANDROID_SURFACES = Object.freeze({
  light: Object.freeze({
    page: '#f7f7f7',
    settingsPage: '#ededed',
    drawerBase: '#ffffff',
    drawerQuickAction: '#ebeeec',
    foreground: '#ffffff',
    assistant: '#f7f8f7',
    system: '#f2f4f3',
    searchPage: '#edeeee',
    searchControl: '#e1e4e2',
    body: '#1e2925',
    secondary: '#64706b',
    placeholder: '#a7aeaa',
    border: '#d8deda',
    divider: '#d8deda',
  }),
  dark: Object.freeze({
    page: '#2c2c2c',
    settingsPage: '#262626',
    drawerBase: '#323232',
    drawerQuickAction: '#454545',
    foreground: '#484848',
    assistant: '#373737',
    system: '#404040',
    searchPage: '#303030',
    searchControl: '#454545',
    body: '#f5f5f2',
    secondary: '#c9cbc8',
    placeholder: '#9ea19e',
    border: '#5e5e5e',
    divider: '#5a5a5a',
  }),
});

export const C16_MATRIX_SIZE = C16_APPEARANCE_CASES.length * C16_FONT_CASES.length * C16_LAYER_CASES.length;

export function parseC16Preview(value = '') {
  const [appearanceId, fontId, ...layerParts] = String(value).split('__');
  const appearance = C16_APPEARANCE_CASES.find(item => item.id === appearanceId);
  const font = C16_FONT_CASES.find(item => item.id === fontId);
  const layer = C16_LAYER_CASES.find(item => item.id === layerParts.join('__'));
  return appearance && font && layer ? { appearance, font, layer } : null;
}
