import { appearanceProjection } from './desktop-parity-preferences.mjs';

const SURFACES = Object.freeze({
  light: Object.freeze({
    page: '#f7f7f7', settingsPage: '#ededed', drawerBase: '#ffffff', drawerQuickAction: '#ebeeec',
    foreground: '#ffffff', assistant: '#f7f8f7', system: '#f2f4f3', searchPage: '#edeeee',
    searchControl: '#e1e4e2', body: '#1e2925', secondary: '#64706b', placeholder: '#a7aeaa',
    border: '#d8deda', divider: '#d8deda',
  }),
  dark: Object.freeze({
    page: '#2c2c2c', settingsPage: '#262626', drawerBase: '#323232', drawerQuickAction: '#454545',
    foreground: '#484848', assistant: '#373737', system: '#404040', searchPage: '#303030',
    searchControl: '#454545', body: '#f5f5f2', secondary: '#c9cbc8', placeholder: '#9ea19e',
    border: '#5e5e5e', divider: '#5a5a5a',
  }),
});

const DARK_ACCENT_SURFACES = Object.freeze({
  orange: Object.freeze({ soft: '#5a4031', bubble: '#5d4031' }),
  blue: Object.freeze({ soft: '#34475b', bubble: '#354654' }),
  black: Object.freeze({ soft: '#3d403e', bubble: '#393d3a' }),
  green: Object.freeze({ soft: '#354d43', bubble: '#344b42' }),
  yellow: Object.freeze({ soft: '#51472d', bubble: '#50462d' }),
  pink: Object.freeze({ soft: '#593e4b', bubble: '#573d49' }),
  purple: Object.freeze({ soft: '#493e5b', bubble: '#493e59' }),
});

const CSS_VARIABLES = Object.freeze({
  page: '--page-background', settingsPage: '--settings-page-background', drawerBase: '--drawer-base-surface',
  drawerQuickAction: '--drawer-quick-action-surface', foreground: '--foreground-surface',
  assistant: '--assistant-surface', system: '--system-surface', searchPage: '--search-page-surface',
  searchControl: '--search-control-surface', body: '--body-text', secondary: '--secondary-text',
  placeholder: '--input-placeholder-text', border: '--neutral-border', divider: '--subtle-divider',
});

export function desktopThemeProjection(value = {}, prefersDark = false) {
  const projection = appearanceProjection(value, prefersDark);
  const resolvedMode = projection.dark ? 'dark' : 'light';
  const darkAccent = DARK_ACCENT_SURFACES[projection.appearance.themeColor] || DARK_ACCENT_SURFACES.orange;
  return {
    ...projection,
    resolvedMode,
    surfaces: SURFACES[resolvedMode],
    accentSoft: projection.dark ? darkAccent.soft : projection.theme.soft,
    userBubble: projection.dark ? darkAccent.bubble : projection.theme.bubble,
  };
}

export function applyDesktopThemeToRoot(root, value = {}, prefersDark = false) {
  const projection = desktopThemeProjection(value, prefersDark);
  root.dataset.appearanceMode = projection.resolvedMode;
  root.dataset.appearancePreference = projection.appearance.mode;
  root.dataset.fontSize = projection.appearance.fontSize;
  root.dataset.themeColor = projection.appearance.themeColor;
  root.style.colorScheme = projection.resolvedMode;
  root.style.setProperty('--app-font-scale', String(projection.fontScale));
  root.style.setProperty('--accent-orange', projection.theme.accent);
  root.style.setProperty('--accent-orange-hover', projection.theme.hover);
  root.style.setProperty('--accent-orange-pressed', projection.theme.pressed);
  root.style.setProperty('--accent-orange-soft', projection.accentSoft);
  root.style.setProperty('--accent-subtle-border', projection.theme.border);
  root.style.setProperty('--user-message-bubble', projection.userBubble);
  for (const [key, cssName] of Object.entries(CSS_VARIABLES)) root.style.setProperty(cssName, projection.surfaces[key]);
  root.style.setProperty('--chat-text', projection.surfaces.body);
  root.style.setProperty('--chat-muted', projection.surfaces.secondary);
  root.style.setProperty('--chat-border', projection.surfaces.border);
  root.style.setProperty('--chat-sidebar', projection.surfaces.drawerBase);
  return projection;
}
