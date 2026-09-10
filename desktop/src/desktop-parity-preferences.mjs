const APPEARANCE_KEY = 'nanfeng-ai.desktop.appearance.v1';
const FAVORITES_PREFIX = 'nanfeng-ai.desktop.favorite-conversations.v1:';
const PRODUCT_SETTINGS_KEY = 'nanfeng-ai.desktop.product-settings.v1';

export const CUSTOM_INSTRUCTIONS_MAX_LENGTH = 8_000;

/** Browser QA uses the same implemented-consumer projection that native SQLite returns. */
export const DESKTOP_SETTINGS_CAPABILITIES = Object.freeze({
  ordinaryChatPersonalization: true,
  historyLibrary: true,
  monitorNotifications: true,
  reminderSuggestions: true,
  unreadIndicators: true,
  webSearch: true,
  googleAccountSync: true,
  updateService: false,
});

export const CONVERSATION_TONES = Object.freeze([
  { id: 'default', label: '默认', detail: '自然、清晰地回答，按问题复杂度调整详略；先解决当前问题，不刻意强化某一种表达风格。' },
  { id: 'direct', label: '直言不讳', detail: '先说结论，直接指出问题，减少铺垫。事实、证据和现实约束优先；发现错误、情绪化、过度自信或悲观时明确纠正。可以反驳和讨论不同观点，不因用户立场强烈而迎合或妥协，但不羞辱、不武断。' },
  { id: 'professional', label: '专业可靠', detail: '像严谨的专业顾问。先核对事实与条件，结构化说明依据、推理、风险和限制；不编造确定性。' },
  { id: 'friendly', label: '亲和友善', detail: '先理解你的处境和情绪，用温和、耐心的方式解释并给出支持；仍会纠正明显错误，不用安慰替代事实。' },
  { id: 'efficient', label: '高效务实', detail: '回答精简。先给结论、优先级和下一步，只保留影响决策的内容；主动指出关键阻碍、取舍、成本和停止条件。' },
  { id: 'humorous', label: '风趣搞笑', detail: '在事实准确和任务完成不受影响时，用适度幽默和类比降低阅读压力；严肃、高风险或负面情绪场景会自动收敛。' },
]);

const DEFAULT_CONVERSATION_TONE = CONVERSATION_TONES[0];

export function conversationToneDefinition(value) {
  return CONVERSATION_TONES.find(item => item.id === value) || DEFAULT_CONVERSATION_TONE;
}

export const DEFAULT_PRODUCT_SETTINGS = Object.freeze({
  memoryEnabled: true,
  historyLibraryEnabled: false,
  tone: 'default',
  nickname: '',
  occupation: '',
  interests: '',
  customInstructions: '',
  monitorNotifications: true,
  reminderSuggestions: true,
  unreadIndicators: true,
  webSearchEnabled: true,
});

export const APPEARANCE_MODES = Object.freeze([
  { id: 'system', label: '系统（默认）' },
  { id: 'light', label: '浅色' },
  { id: 'dark', label: '深色' },
]);

export const FONT_SIZES = Object.freeze([
  { id: 'small', label: '小', scale: 0.8 },
  { id: 'standard', label: '标准', scale: 1 },
  { id: 'large', label: '大', scale: 1.24 },
]);

export const THEME_COLORS = Object.freeze([
  { id: 'orange', label: '橙色', accent: '#e97128', hover: '#d86520', pressed: '#c2581a', soft: '#fff1e5', border: '#efbd94', bubble: '#f9e3d2' },
  { id: 'blue', label: '蓝色', accent: '#357ae8', hover: '#2e6bd0', pressed: '#285db6', soft: '#e7f0ff', border: '#a9c5f3', bubble: '#dee6ef' },
  { id: 'black', label: '黑色', accent: '#252a27', hover: '#1f2321', pressed: '#171a18', soft: '#e9ece9', border: '#b8beb9', bubble: '#dfe2df' },
  { id: 'green', label: '绿色', accent: '#287052', hover: '#225f46', pressed: '#1b4e39', soft: '#e3f3ea', border: '#a8cfb9', bubble: '#d8e5df' },
  { id: 'yellow', label: '黄色', accent: '#e6b335', hover: '#cb9d2c', pressed: '#ad8525', soft: '#fff4d8', border: '#e5cc87', bubble: '#ede4cd' },
  { id: 'pink', label: '粉色', accent: '#e26091', hover: '#cb537f', pressed: '#ad466c', soft: '#ffeaf1', border: '#e9abc1', bubble: '#ebdde4' },
  { id: 'purple', label: '紫色', accent: '#8c63d9', hover: '#7954c2', pressed: '#6746a8', soft: '#f0eaff', border: '#c7b4eb', bubble: '#e5e0ef' },
]);

export function normalizeAppearance(value = {}) {
  const mode = APPEARANCE_MODES.some(item => item.id === value.mode) ? value.mode : 'system';
  const fontSize = FONT_SIZES.some(item => item.id === value.fontSize) ? value.fontSize : 'standard';
  const themeColor = THEME_COLORS.some(item => item.id === value.themeColor) ? value.themeColor : 'orange';
  return { mode, fontSize, themeColor };
}

export function appearanceProjection(value = {}, prefersDark = false) {
  const appearance = normalizeAppearance(value);
  const font = FONT_SIZES.find(item => item.id === appearance.fontSize);
  const theme = THEME_COLORS.find(item => item.id === appearance.themeColor);
  const dark = appearance.mode === 'dark' || (appearance.mode === 'system' && Boolean(prefersDark));
  return { appearance, dark, fontScale: font.scale, theme };
}

const normalizedText = (value, limit) => String(value || '').replaceAll('\u0000', '').slice(0, limit);

export function normalizeProductSettings(value = {}) {
  const boolean = key => typeof value[key] === 'boolean' ? value[key] : DEFAULT_PRODUCT_SETTINGS[key];
  return {
    memoryEnabled: boolean('memoryEnabled'),
    historyLibraryEnabled: boolean('historyLibraryEnabled'),
    tone: value.tone === 'default' || CONVERSATION_TONES.some(item => item.id === value.tone) ? value.tone : DEFAULT_PRODUCT_SETTINGS.tone,
    nickname: normalizedText(value.nickname, 80),
    occupation: normalizedText(value.occupation, 120),
    // Android still owns this hidden compatibility value even though the current UI has no
    // visible input. Keeping it in normalization prevents old imports from erasing it on save.
    interests: normalizedText(value.interests, 500),
    customInstructions: normalizedText(value.customInstructions, CUSTOM_INSTRUCTIONS_MAX_LENGTH),
    monitorNotifications: boolean('monitorNotifications'),
    reminderSuggestions: boolean('reminderSuggestions'),
    unreadIndicators: boolean('unreadIndicators'),
    webSearchEnabled: boolean('webSearchEnabled'),
  };
}

function parseJson(storage, key, fallback) {
  try { return JSON.parse(storage?.getItem(key) || 'null') ?? fallback; } catch { return fallback; }
}

function stableIds(value) {
  if (!Array.isArray(value)) return [];
  return [...new Set(value.filter(item => typeof item === 'string' && /^[A-Za-z0-9][A-Za-z0-9._:-]{0,199}$/.test(item)))].sort();
}

export class DesktopParityPreferences {
  constructor(storage) { this.storage = storage; }

  readAppearance() { return normalizeAppearance(parseJson(this.storage, APPEARANCE_KEY, {})); }

  writeAppearance(value) {
    const normalized = normalizeAppearance(value);
    this.storage?.setItem(APPEARANCE_KEY, JSON.stringify(normalized));
    return normalized;
  }

  readProductSettings() { return normalizeProductSettings(parseJson(this.storage, PRODUCT_SETTINGS_KEY, {})); }

  writeProductSettings(value) {
    const normalized = normalizeProductSettings(value);
    this.storage?.setItem(PRODUCT_SETTINGS_KEY, JSON.stringify(normalized));
    return normalized;
  }

  clearNativeAppSettingsMigrationSource() {
    this.storage?.removeItem(APPEARANCE_KEY);
    this.storage?.removeItem(PRODUCT_SETTINGS_KEY);
  }

  readFavoriteConversationIds(workspaceId) {
    if (!workspaceId) return new Set();
    return new Set(stableIds(parseJson(this.storage, `${FAVORITES_PREFIX}${workspaceId}`, [])));
  }

  writeFavoriteConversationIds(workspaceId, ids) {
    if (!workspaceId) return new Set();
    const normalized = stableIds([...ids]);
    const key = `${FAVORITES_PREFIX}${workspaceId}`;
    if (normalized.length) this.storage?.setItem(key, JSON.stringify(normalized));
    else this.storage?.removeItem(key);
    return new Set(normalized);
  }
}

const visibleText = message => (message?.blocks || [])
  .filter(block => block?.kind === 'TEXT')
  .map(block => String(block.text || ''))
  .filter(Boolean)
  .join('\n\n');

const visibleAttachments = message => (message?.blocks || [])
  .filter(block => block?.kind === 'ASSET_REF' && block.asset)
  .map(block => `- ${String(block.asset.displayName || '本地附件')}（${String(block.asset.mimeType || '未知类型')}）`);

function markdownMessage(message) {
  const canonicalRole = String(message?.role || '').trim().toLocaleLowerCase();
  const role = canonicalRole === 'assistant' ? '南枫AI' : canonicalRole === 'user' ? '用户' : '工具记录';
  const timestamp = message?.createdAt ? ` · ${message.createdAt}` : '';
  const text = visibleText(message);
  const attachments = visibleAttachments(message);
  return [`## ${role}${timestamp}`, '', text, attachments.length ? `附件：\n${attachments.join('\n')}` : '']
    .filter((item, index, array) => item || (index > 0 && array[index - 1]))
    .join('\n')
    .trim();
}

export function conversationMarkdown(conversation) {
  const title = String(conversation?.title || '未命名会话').replaceAll(/[\r\n]+/g, ' ').trim();
  const messages = (conversation?.messages || []).map(markdownMessage).filter(Boolean);
  return [`# ${title}`, '', ...messages.flatMap((item, index) => index ? ['', '---', '', item] : [item]), ''].join('\n');
}

export function assistantMessageMarkdown(conversation, messageId) {
  const message = (conversation?.messages || []).find(item => item?.id === messageId && String(item?.role || '').trim().toLocaleLowerCase() === 'assistant');
  if (!message) return null;
  const title = String(conversation?.title || '未命名会话').replaceAll(/[\r\n]+/g, ' ').trim();
  return [`# ${title}`, '', markdownMessage(message), ''].join('\n');
}

export function conversationFindMatches(conversation, query) {
  const needle = String(query || '').trim().toLocaleLowerCase();
  if (!needle) return [];
  return (conversation?.messages || []).flatMap(message => {
    const haystack = visibleText(message).toLocaleLowerCase();
    const matches = [];
    let offset = 0;
    while (matches.length < 200) {
      const index = haystack.indexOf(needle, offset);
      if (index < 0) break;
      matches.push({ messageId: message.id, index });
      offset = index + Math.max(needle.length, 1);
    }
    return matches;
  });
}
