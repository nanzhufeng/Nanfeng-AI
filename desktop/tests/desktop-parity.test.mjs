import test from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { resolve } from 'node:path';
import {
  CONVERSATION_TONES,
  DesktopParityPreferences,
  THEME_COLORS,
  appearanceProjection,
  assistantMessageMarkdown,
  conversationFindMatches,
  conversationMarkdown,
} from '../src/desktop-parity-preferences.mjs';
import { renderChatFirstShell } from '../src/chat-shell.mjs';
import { renderLocalDataCleanupScopeDialog } from '../src/local-data-view.mjs';

class MemoryStorage {
  #items = new Map();
  getItem(key) { return this.#items.has(key) ? this.#items.get(key) : null; }
  setItem(key, value) { this.#items.set(key, String(value)); }
  removeItem(key) { this.#items.delete(key); }
}

const conversation = {
  id: 'conversation-safe-1',
  title: '双端同步',
  revision: 3,
  messages: [
    { id: 'user-1', role: 'user', createdAt: '2026-08-31T08:00:00Z', blocks: [{ kind: 'TEXT', text: '请同步手机功能' }] },
    { id: 'assistant-1', role: 'assistant', createdAt: '2026-08-31T08:00:01Z', blocks: [{ kind: 'TEXT', text: '已同步手机功能并校验。' }, { kind: 'REASONING', text: '隐藏推理不得导出' }, { kind: 'ASSET_REF', asset: { displayName: '证明.pdf', mimeType: 'application/pdf', privatePath: '/private/secret' } }] },
  ],
};
const data = { summary: { id: 'workspace-safe-1' }, exchange: { conversations: [conversation] } };

test('appearance and favorite preferences are normalized and device-local', () => {
  const storage = new MemoryStorage();
  const preferences = new DesktopParityPreferences(storage);
  assert.deepEqual(preferences.writeAppearance({ mode: 'dark', fontSize: 'large', themeColor: 'purple' }), { mode: 'dark', fontSize: 'large', themeColor: 'purple' });
  assert.equal(appearanceProjection(preferences.readAppearance(), false).fontScale, 1.24);
  assert.equal(appearanceProjection({ mode: 'system' }, true).dark, true);
  assert.deepEqual([...preferences.writeFavoriteConversationIds('workspace-safe-1', new Set(['conversation-safe-1', '../unsafe']))], ['conversation-safe-1']);
  preferences.clearNativeAppSettingsMigrationSource();
  assert.deepEqual(preferences.readAppearance(), { mode: 'system', fontSize: 'standard', themeColor: 'orange' });
});

test('conversation style picker exposes the default choice before the five explicit styles', () => {
  assert.deepEqual(CONVERSATION_TONES.map(item => item.label), ['默认', '直言不讳', '专业可靠', '亲和友善', '高效务实', '风趣搞笑']);
  const html = renderChatFirstShell({
    data, native: true, pane: 'settings', settingsSection: 'personalization', status: '', error: '', connection: {},
    settingsPicker: 'tone', productSettings: { tone: 'default' }, personalizationDraft: { tone: 'default' },
    settingsCapabilities: { ordinaryChatPersonalization: true },
  });
  for (const label of ['默认', '直言不讳', '专业可靠', '亲和友善', '高效务实', '风趣搞笑']) assert.ok(html.includes(`>${label}</strong>`), label);
  assert.equal((html.match(/data-picker="tone" data-value=/g) || []).length, 6);
  assert.ok(html.includes('data-value="default"'));
  assert.ok(html.includes('不因用户立场强烈而迎合或妥协'));
  assert.ok(html.includes('android-settings-picker android-settings-tone-picker'));
});

test('global tone selection persists immediately instead of waiting for an unrelated personalization save', async () => {
  const source = await readFile(resolve(import.meta.dirname, '../src/app.mjs'), 'utf8');
  const owner = source.slice(source.indexOf("else if (picker === 'tone')"), source.indexOf("} else if (action === 'toggle-product-setting')"));
  assert.match(owner, /state\.personalizationDraft = \{ \.\.\.state\.personalizationDraft, tone: value \};/);
  assert.match(owner, /writeProductSettings\(\{ tone: value \}\);/);
  assert.doesNotMatch(owner, /state\.personalizationDirty = true;/);
});

test('favorites settings page does not render a generic startup status below the list', async () => {
  const source = await readFile(resolve(import.meta.dirname, '../src/chat-shell.mjs'), 'utf8');
  assert.match(source, /status: error \|\| \(settingsSection === 'favorites' \|\| String\(status\)\.startsWith\('本地工作区已就绪'\) \? '' : status\)/);
});

test('conversation style picker uses a viewport-sized Desktop dialog with stable card text layout', async () => {
  const css = await readFile(resolve(import.meta.dirname, '../src/chat-shell.css'), 'utf8');
  const toneCss = css.slice(css.indexOf('.android-settings-tone-picker'), css.indexOf('.settings-conversation-management'));
  for (const token of ['box-sizing: border-box', 'width: min(760px, calc(100vw - 48px))', 'height: min(820px, calc(100dvh - 48px))', 'overflow-y: auto', 'scrollbar-gutter: stable', 'gap: 10px', 'grid-template-columns: minmax(0, 1fr) 22px', 'grid-auto-rows: max-content', 'align-content: start', 'background: #f3f4f3', 'border: 1px solid transparent', 'min-height: 1.55em', 'font-size: calc(13px * var(--app-font-scale, 1))', '[aria-pressed="true"]', 'background: #fff3ea', 'border-color: #ff9a57']) assert.ok(toneCss.includes(token), token);
});

test('theme color picker renders every preview from the real theme token owner', () => {
  const html = renderChatFirstShell({
    data, native: true, pane: 'settings', settingsSection: 'personalization', status: '', error: '', connection: {},
    settingsPicker: 'themeColor', appearance: { mode: 'system', fontSize: 'standard', themeColor: 'green' },
  });

  const pickerHtml = html.slice(html.indexOf('<section class="android-settings-picker'));
  assert.equal((pickerHtml.match(/class="android-settings-color-preview\s/g) || []).length, THEME_COLORS.length);
  for (const item of THEME_COLORS) {
    assert.ok(html.includes(`data-picker="themeColor" data-value="${item.id}"`), item.id);
    assert.ok(html.includes(`android-settings-color-preview-${item.id}`), item.id);
  }
  assert.match(html, /data-value="green" aria-pressed="true"[^>]*>[\s\S]*?<title>已选中<\/title>/);
});

test('theme color preview has one shared native-safe surface and literal token values at render time', async () => {
  const css = await readFile(resolve(import.meta.dirname, '../src/chat-shell.css'), 'utf8');
  const start = css.indexOf('.android-settings-color-preview { display');
  const rule = css.slice(start, css.indexOf('}', start) + 1);
  assert.match(rule, /background-image: none !important;/);
  assert.doesNotMatch(rule, /\bbackground:/);
  for (const item of THEME_COLORS) assert.match(css, new RegExp(`\\.android-settings-color-preview-${item.id} \\{ background-color: ${item.accent} !important; \\}`));
});

test('markdown export contains visible text and safe attachment metadata only', () => {
  const whole = conversationMarkdown(conversation);
  const answer = assistantMessageMarkdown(conversation, 'assistant-1');
  for (const payload of [whole, answer]) {
    assert.match(payload, /双端同步/);
    assert.match(payload, /已同步手机功能并校验/);
    assert.match(payload, /证明\.pdf（application\/pdf）/);
    assert.doesNotMatch(payload, /隐藏推理|private\/secret/);
  }
  assert.equal(assistantMessageMarkdown(conversation, 'user-1'), null);
});

test('conversation find is case-insensitive, bounded, and renders the active match', () => {
  assert.deepEqual(conversationFindMatches(conversation, '手机').map(item => item.messageId), ['user-1', 'assistant-1']);
  const html = renderChatFirstShell({
    data, native: true, selectedConversationId: conversation.id, pane: 'chat', status: '', error: '', connection: {},
    appearance: { mode: 'system', fontSize: 'standard', themeColor: 'orange' },
    favoriteConversationIds: new Set([conversation.id]), conversationFindOpen: true, conversationFindQuery: '手机',
    conversationFindMatches: conversationFindMatches(conversation, '手机'), conversationFindIndex: 1,
  });
  for (const token of ['conversation-find-input', '2 / 2', '<mark>手机</mark>', 'find-active', 'open-conversation-header-menu', 'open-assistant-message-menu']) assert.ok(html.includes(token), token);
  assert.ok(!html.includes('chat-history-favorite'));
});

test('conversation header branches from visible transcript content like Android', () => {
  const mainHeader = html => html.slice(html.indexOf('<header class="chat-main-header">'), html.indexOf('</header>', html.indexOf('<header class="chat-main-header">')) + 9);
  const emptyConversation = { ...conversation, id: 'empty-conversation', messages: [] };
  const emptyHtml = mainHeader(renderChatFirstShell({
    data: { summary: data.summary, exchange: { conversations: [emptyConversation] } }, native: true,
    selectedConversationId: emptyConversation.id, pane: 'chat', status: '', error: '', connection: {},
  }));
  assert.match(emptyHtml, /class="chat-mode-switch"/);
  assert.match(emptyHtml, /data-action="toggle-temporary-chat"/);
  assert.doesNotMatch(emptyHtml, /data-action="new-chat"[^>]*aria-label="新对话"/);
  assert.doesNotMatch(emptyHtml, /data-action="open-conversation-header-menu"/);

  const contentHtml = mainHeader(renderChatFirstShell({
    data, native: true, selectedConversationId: conversation.id, pane: 'chat', status: '', error: '', connection: {},
  }));
  assert.doesNotMatch(contentHtml, /class="chat-mode-switch"/);
  assert.doesNotMatch(contentHtml, /data-action="toggle-temporary-chat"/);
  assert.match(contentHtml, /class="chat-header-content-actions"/);
  assert.match(contentHtml, /data-action="new-chat"[^>]*aria-label="新对话"/);
  assert.match(contentHtml, /data-action="open-conversation-header-menu"[^>]*aria-label="对话更多操作"/);
  assert.ok(contentHtml.indexOf('data-action="new-chat"') < contentHtml.indexOf('data-action="open-conversation-header-menu"'));

  const menuHtml = renderChatFirstShell({
    data, native: true, selectedConversationId: conversation.id, pane: 'chat', status: '', error: '', connection: {},
    contextMenu: { id: conversation.id, revision: conversation.revision, pinned: false, favorite: false, archived: false, source: 'header' },
  });
  for (const token of ['context-menu-share', 'context-menu-find', 'context-menu-pin', 'context-menu-archive', 'context-menu-delete']) assert.ok(menuHtml.includes(token), token);
  assert.ok(menuHtml.includes('context-menu-rename'));
});

test('completed assistant answers expose answer information only through the more menu', () => {
  const html = renderChatFirstShell({
    data, native: true, selectedConversationId: conversation.id, pane: 'chat', status: '', error: '', connection: {},
    contextSelectionRecords: [{
      assistantMessageId: 'assistant-1', providerId: 'OPENROUTER', providerLabel: 'OpenRouter', modelId: 'fixture-model', fixedInputTokens: 12,
      selectedSources: [{ kind: 'MEMORY', title: '长期记忆' }, { kind: 'CURRENT_PATH', title: '当前会话路径' }],
    }],
  });
  assert.ok(html.includes('data-action="open-assistant-message-menu"'));
  assert.ok(!html.includes('data-action="show-answer-context"'));
  assert.ok(!html.includes('data-action="message-provenance"'));
  assert.ok(!html.includes('private/secret'));
});

test('answer information groups repeated source classes and states network evidence precisely', async () => {
  const source = await readFile(resolve(import.meta.dirname, '../src/app.mjs'), 'utf8');
  const dialog = source.slice(source.indexOf("state.dialog?.kind === 'assistant-answer-information'"), source.indexOf("if (state.dialog === 'metadata')"));
  assert.match(dialog, /const groupedSources = new Map\(\)/);
  assert.match(dialog, /!titles\.includes\(title\)/);
  assert.match(dialog, /titles\.join\('；'\)/);
  assert.match(dialog, /本次未启用联网/);
  assert.match(dialog, /已实际联网（服务商返回核验依据）/);
  assert.match(dialog, /已请求联网（服务商未返回核验依据）/);
  assert.doesNotMatch(dialog, /未记录（旧回答）'; const sourceLabels/);
});

test('settings expose the latest phone values in the desktop primary and secondary split', async () => {
  const appearance = renderChatFirstShell({ data, native: true, pane: 'settings', settingsSection: 'personalization', status: '', error: '', connection: {}, appearance: { mode: 'dark', fontSize: 'large', themeColor: 'green' } });
  for (const token of ['aria-label="设置一级菜单"', 'aria-label="设置二级页面"', '外观', '深色', '字体大小', '大', '主题色', '绿色', 'open-settings-picker']) assert.ok(appearance.includes(token));
  const favorites = renderChatFirstShell({ data, native: true, pane: 'settings', settingsSection: 'favorites', status: '', error: '', connection: {}, favoriteConversationIds: new Set([conversation.id]) });
  for (const token of ['收藏', 'toggle-conversation-favorite', '双端同步']) assert.ok(favorites.includes(token));
  const about = renderChatFirstShell({ data, native: true, pane: 'settings', settingsSection: 'about', status: '', error: '', connection: {}, runtimeInfo: { version: '0.0.1', platform: 'macos', arch: 'aarch64', buildEpochSeconds: 1767225600 } });
  for (const token of ['Desktop 版 0.0.1', 'macos', 'aarch64', '开发时间']) assert.ok(about.includes(token));
  assert.ok(!about.includes('读取中'));
  const css = await readFile(resolve(import.meta.dirname, '../src/chat-shell.css'), 'utf8');
  assert.match(css, /\.android-settings-about small \{ display: block; margin-top: 6px; \}/);
  assert.match(css, /\.android-settings-about > div:not\(\.android-settings-divider\)/);
  assert.match(css, /\.android-settings-about > \.android-settings-divider \{ height: 1px; min-height: 1px; padding: 0;/);
});

test('native settings enable only implemented consumers and keep remaining owners fail closed', () => {
  const capabilities = { ordinaryChatPersonalization: true, historyLibrary: false, monitorNotifications: false, reminderSuggestions: false, unreadIndicators: true, webSearch: true };
  const personalization = renderChatFirstShell({ data, native: true, pane: 'settings', settingsSection: 'personalization', status: '', error: '', connection: {}, settingsCapabilities: capabilities });
  for (const token of ['id="personalization-nickname"', 'data-key="memoryEnabled"', '你的 自定义指令 将用于所有南枫AI的对话']) assert.ok(personalization.includes(token));
  assert.doesNotMatch(personalization, /id="personalization-nickname"[^>]*disabled/);
  assert.doesNotMatch(personalization, /data-key="memoryEnabled"[^>]*disabled/);
  assert.match(personalization, /data-key="historyLibraryEnabled"[^>]*disabled/);
  const reminders = renderChatFirstShell({ data, native: true, pane: 'settings', settingsSection: 'reminders', status: '', error: '', connection: {}, settingsCapabilities: capabilities });
  for (const token of ['Desktop 尚无系统通知与计划监控消费者', 'Desktop 普通对话尚无提醒建议 owner']) assert.ok(reminders.includes(token));
  assert.ok(!reminders.includes('Desktop 尚无未读水位与列表消费者'));
  assert.equal((reminders.match(/role="switch"[^>]*disabled/g) || []).length, 2);
  assert.equal((reminders.match(/role="switch" aria-checked="false" disabled/g) || []).length, 2);
  const model = renderChatFirstShell({ data, native: true, pane: 'settings', settingsSection: 'model', status: '', error: '', connection: {}, settingsCapabilities: capabilities });
  assert.ok(model.includes('开启后每次普通对话均检索公开网页并标注来源'));
  assert.doesNotMatch(model, /data-key="webSearchEnabled"[^>]*disabled/);
  const development = renderChatFirstShell({ data, native: true, pane: 'settings', settingsSection: 'development', status: '', error: '', connection: {}, settingsCapabilities: capabilities });
  assert.ok(!development.includes('功能审阅 · 南枫转写'));
});

test('static Desktop build ships every startup module including the full-search renderer', async () => {
  const buildSource = await readFile(resolve(import.meta.dirname, '../scripts/build.mjs'), 'utf8');
  assert.match(buildSource, /'desktop-parity-preferences\.mjs'/);
  assert.match(buildSource, /'desktop-search-page\.mjs'/);
  assert.match(buildSource, /'desktop-cost-estimator\.mjs'/);
});

test('static Desktop build ships the complete local import graph rooted at app.mjs', async () => {
  const buildSource = await readFile(resolve(import.meta.dirname, '../scripts/build.mjs'), 'utf8');
  const sourceRoot = resolve(import.meta.dirname, '../src');
  const pending = ['app.mjs'];
  const required = new Set();
  while (pending.length) {
    const moduleName = pending.pop();
    if (required.has(moduleName)) continue;
    required.add(moduleName);
    const moduleSource = await readFile(resolve(sourceRoot, moduleName), 'utf8');
    for (const match of moduleSource.matchAll(/from\s+['"]\.\/([^'"]+\.mjs)['"]/g)) {
      pending.push(match[1]);
    }
  }
  for (const moduleName of required) assert.ok(buildSource.includes(`'${moduleName}'`), moduleName);
});

test('native parity commands are explicitly least-privilege allowed for the main window', async () => {
  const [permissions, capability] = await Promise.all([
    readFile(resolve(import.meta.dirname, '../src-tauri/permissions/default.toml'), 'utf8'),
    readFile(resolve(import.meta.dirname, '../src-tauri/capabilities/default.json'), 'utf8'),
  ]);
  for (const token of ['allow-read-desktop-runtime-info', 'allow-write-desktop-markdown-to-selected-path', 'allow-desktop-model-service-settings', 'allow-desktop-app-settings', 'allow-desktop-conversation-read-state', 'allow-read-desktop-background-runtime', 'allow-read-desktop-usage-ledger', 'allow-read-desktop-privacy-inventory', 'allow-catalog-desktop-local-index', 'allow-permanently-delete-desktop-conversation']) {
    assert.ok(permissions.includes(token));
    assert.ok(capability.includes(token));
  }
  assert.match(permissions, /commands\.allow = \["read_desktop_runtime_info"\]/);
  assert.match(permissions, /commands\.allow = \["write_desktop_markdown_to_selected_path"\]/);
  for (const command of ['read_desktop_model_service_settings', 'save_desktop_model_service_settings', 'reveal_desktop_model_service_credential', 'test_desktop_model_service_connection']) assert.ok(permissions.includes(command));
  for (const command of ['read_desktop_app_settings', 'save_desktop_app_settings', 'read_desktop_favorite_conversation_ids', 'set_desktop_conversation_favorite']) assert.ok(permissions.includes(command));
  for (const command of ['read_desktop_conversation_read_state', 'mark_desktop_conversation_opened', 'mark_desktop_conversation_unread']) assert.ok(permissions.includes(command));
  assert.match(permissions, /commands\.allow = \["read_desktop_background_runtime"\]/);
  assert.match(permissions, /commands\.allow = \["read_desktop_usage_ledger"\]/);
  assert.match(permissions, /commands\.allow = \["read_desktop_privacy_inventory"\]/);
  assert.match(permissions, /commands\.allow = \["catalog_desktop_local_index"\]/);
  assert.match(permissions, /commands\.allow = \["permanently_delete_desktop_conversation"\]/);
});

test('current Android model catalog keeps GLM-OCR visible but outside chat selection', async () => {
  const zhipu = renderChatFirstShell({
    data, native: true, pane: 'settings', settingsSection: 'model-configuration', status: '', error: '', connection: {},
    settingsPicker: 'modelPreset', modelProviderId: 'ZHIPU',
  });
  for (const token of ['GLM-5.3', 'GLM-5.3 Flash', 'GLM-OCR', '图片与 PDF 转 Markdown · 使用同一智谱 API Key', '仅在左侧栏“南枫转写”中调用，不加入聊天模型选择。']) assert.ok(zhipu.includes(token));
  assert.ok(!zhipu.includes('data-preset-id="GLM_OCR"'));
  for (const retired of ['Grok 4.1', 'Grok 4.5', 'Grok 4.6']) assert.ok(!zhipu.includes(retired));

  const qwen = renderChatFirstShell({
    data, native: true, pane: 'settings', settingsSection: 'model-configuration', status: '', error: '', connection: {},
    settingsPicker: 'modelPreset', modelProviderId: 'QWEN',
  });
  assert.ok(!qwen.includes('Qwen3-ASR'));
  assert.ok(!qwen.includes('音频与视频转文字'));
  const nativeBridge = await readFile(new URL('../src-tauri/src/lib.rs', import.meta.url), 'utf8');
  assert.doesNotMatch(nativeBridge, /"QWEN" => vec!\[desktop_model_service_v1::QWEN_ASR\]/);
});

test('model record pages use the latest Android empty states and real usage projection', () => {
  const emptyCost = renderChatFirstShell({ data, native: true, pane: 'settings', settingsSection: 'conversation-cost', status: '', error: '', connection: {} });
  for (const token of ['尚无可用费用记录', '已返回输入和输出 Token 的调用会显示费用与用量。']) assert.ok(emptyCost.includes(token));
  const context = renderChatFirstShell({ data, native: true, pane: 'settings', settingsSection: 'context-selections', status: '', error: '', connection: {} });
  assert.ok(context.includes('还没有上下文记录。'));
  const diagnostics = renderChatFirstShell({ data, native: true, pane: 'settings', settingsSection: 'diagnostics', status: '', error: '', connection: {} });
  for (const token of ['连接失败', '对话与连接测试中近 7 天的失败记录', '近 7 天没有连接失败记录。', '自动与工具任务', '暂无自动或工具任务调用记录。']) assert.ok(diagnostics.includes(token));

  const cost = renderChatFirstShell({
    data, native: true, pane: 'settings', settingsSection: 'conversation-cost', status: '', error: '', connection: {},
    usageLedger: { records: [{ entryId: 'entry-1', conversationId: conversation.id, modelId: 'gpt-5.6-terra', factGrade: 'PROVIDER_REPORTED', inputTokens: 1200, outputTokens: 320, cachedInputTokens: 0, chargeMicros: 12500, currencyCode: 'USD', occurredAtMs: 1788172800000 }], inputTokens: 1200, outputTokens: 320, cachedInputTokens: 0 },
  });
  for (const token of ['本机累计', '费用', '¥0.084004', '1,200', '会话标题整理', '历史资料整理', '南枫转写', 'gpt-5.6-terra']) assert.ok(cost.includes(token));
});

test('local data page renders aggregate-only all-workspace inventory in Android order', () => {
  const html = renderChatFirstShell({
    data, native: true, pane: 'settings', settingsSection: 'privacy', status: '', error: '', connection: {},
    privacyInventory: { totalBytes: 15360, aggregates: [
      { id: 'search_text', count: 2, byteCount: 1200 },
      { id: 'messages', count: 2, byteCount: 1200 },
      { id: 'memory', count: 1, byteCount: 240 },
      { id: 'knowledge', count: 3, byteCount: 2048 },
      { id: 'projects', count: 1, byteCount: 512 },
      { id: 'attachment_images', count: 2, byteCount: 8192 },
      { id: 'attachment_files', count: 1, byteCount: 3168 },
    ] },
  });
  for (const token of ['本机数据', '15.0 KB', '对话与内容', '全部', '2 条正文 · 3 项附件', '记忆', '知识库', '项目', '附件', '图片', '文件', '选择清理范围']) assert.ok(html.includes(token));
  for (const forbidden of ['workspace-safe-1', '双端同步', '证明.pdf', 'private/secret']) assert.ok(!html.includes(forbidden));
});

test('local data category rows retain half of their former neutral gray contrast in the light desktop theme', async () => {
  const css = await readFile(resolve(import.meta.dirname, '../src/chat-shell.css'), 'utf8');
  assert.match(css, /\.android-settings-privacy-rows > button \{[^}]*background: #f9f9f9 !important;/);
  assert.match(css, /\.android-settings-privacy-rows > button:hover \{ background: #f5f6f5 !important; \}/);
  assert.match(css, /:root:not\(\[data-appearance-mode="dark"\]\) \.android-settings-privacy-rows > button:not\(\.primary\):not\(\.selected\):not\(\[aria-pressed="true"\]\) \{ background: #f9f9f9 !important; \}/);
});

test('local data cleanup keeps the current Android scopes and exact confirmation gates', async () => {
  const [appSource, ownerSource] = await Promise.all([
    readFile(resolve(import.meta.dirname, '../src/app.mjs'), 'utf8'),
    readFile(resolve(import.meta.dirname, '../src/local-data-view.mjs'), 'utf8'),
  ]);
  const renderedSource = `${appSource}\n${ownerSource}`;
  for (const token of [
    '清理失败任务',
    '选择后可逐项清理失败任务的附件。',
    '清空知识与记忆回收站',
    '只清空已放入知识与记忆回收站的内容。',
    '删除全部本地数据',
    '删除全部本机业务数据，需输入确认文字。',
    '预览已选 ${selected.size} 项',
    '已确认清理范围。',
    '输入：删除全部本地业务数据',
    '确认删除全部本地业务数据',
  ]) assert.ok(renderedSource.includes(token));
  assert.match(renderLocalDataCleanupScopeDialog(), /data-scope="KNOWLEDGE_MEMORY_TRASH"/);
  assert.match(appSource, /invoke\('preview_desktop_privacy_deletion'/);
  assert.match(appSource, /invoke\('delete_desktop_privacy_data'/);
  assert.match(appSource, /previewFingerprint: current\.preview\.fingerprint/);

  const nativeSource = await readFile(resolve(import.meta.dirname, '../src-tauri/src/lib.rs'), 'utf8');
  for (const token of ['TEMPORARY_FAILED_TASK_ASSETS', 'KNOWLEDGE_MEMORY_TRASH', 'ALL_LOCAL_BUSINESS_DATA', '删除全部本地业务数据', 'pending-privacy-task-delete']) assert.ok(nativeSource.includes(token));
  const capability = await readFile(resolve(import.meta.dirname, '../src-tauri/capabilities/default.json'), 'utf8');
  assert.ok(capability.includes('allow-desktop-privacy-cleanup'));
});

test('local data categories open the shared cross-workspace safe search catalogue', async () => {
  const hit = { entryId: 'search-workspace-safe-2-message-safe-2-attachment', workspaceId: 'workspace-safe-2', conversationId: 'conversation-safe-2', messageId: 'message-safe-2', attachmentId: 'attachment-safe-2', title: '附件会话', displayName: '资料.pdf', mimeType: 'application/pdf', snippet: '本机 PDF', contentKind: 'FILE', fileType: 'pdf', timestamp: '2026-08-21T02:00:00Z', byteCount: 4096, conversationRevision: 1 };
  const html = renderChatFirstShell({
    data, native: true, pane: 'chat', searchPanel: true, searchCategory: 'file', chatSearch: '', status: '', error: '', connection: {},
    searchPage: { hits: [hit], textCount: 0, attachmentCount: 1, truncated: false },
  });
  for (const token of ['全屏搜索', 'role="tablist"', 'aria-selected="true" class="selected">文件', 'data-entry-id="search-workspace-safe-2-message-safe-2-attachment"', '资料.pdf', '4.0 KB']) assert.ok(html.includes(token));
  assert.ok(!html.includes('application/pdf'));
  const appSource = await readFile(resolve(import.meta.dirname, '../src/app.mjs'), 'utf8');
  assert.match(appSource, /invoke\('query_desktop_local_index'/);
  assert.match(appSource, /invoke\('record_desktop_local_search_history'/);
  assert.match(appSource, /loadSearchWorkspace\(hit\.workspaceId\)/);
});

test('archived and recycle settings mirror Android single and bulk cleanup gates', async () => {
  const lifecycleData = { summary: data.summary, exchange: { conversations: [
    { ...conversation, id: 'conversation-archived', revision: 4, archived: true, deleted: false, title: '已归档会话' },
    { ...conversation, id: 'conversation-deleted', revision: 5, archived: true, deleted: true, title: '回收站会话' },
  ] } };
  const archived = renderChatFirstShell({ data: lifecycleData, native: true, pane: 'settings', settingsSection: 'archived', status: '', error: '', connection: {} });
  for (const token of ['归档会话不会出现在日常列表；恢复后会回到普通对话列表。', '清空已归档', 'restore-conversation', 'open-archived-conversation-delete', '已归档会话']) assert.ok(archived.includes(token));
  assert.ok(!archived.includes('永久删除会话'));
  const recycle = renderChatFirstShell({ data: lifecycleData, native: true, pane: 'settings', settingsSection: 'recycle', status: '', error: '', connection: {} });
  for (const token of ['会话消息树尚未物理删除；恢复后会回到普通对话列表。', '清空回收站', 'restore-deleted-conversation', 'open-conversation-permanent-delete', '<title>永久删除</title>', '回收站会话']) assert.ok(recycle.includes(token));
  const appSource = await readFile(resolve(import.meta.dirname, '../src/app.mjs'), 'utf8');
  assert.match(appSource, /invoke\('permanently_delete_desktop_conversation'/);
  assert.match(appSource, /conversation-bulk-recycle/);
  assert.match(appSource, /已永久删除会话。/);
});
