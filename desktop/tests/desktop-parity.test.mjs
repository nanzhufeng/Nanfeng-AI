import test from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { resolve } from 'node:path';
import {
  DesktopParityPreferences,
  appearanceProjection,
  assistantMessageMarkdown,
  conversationFindMatches,
  conversationMarkdown,
} from '../src/desktop-parity-preferences.mjs';
import { renderChatFirstShell } from '../src/chat-shell.mjs';

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
  for (const token of ['conversation-find-input', '2 / 2', '<mark>手机</mark>', 'find-active', 'export-conversation-markdown', 'export-assistant-markdown', 'chat-history-favorite']) assert.ok(html.includes(token));
});

test('completed assistant answers expose only content-free local context audit metadata', () => {
  const html = renderChatFirstShell({
    data, native: true, selectedConversationId: conversation.id, pane: 'chat', status: '', error: '', connection: {},
    contextSelectionRecords: [{
      assistantMessageId: 'assistant-1', providerId: 'OPENROUTER', providerLabel: 'OpenRouter', modelId: 'fixture-model', fixedInputTokens: 12,
      selectedSources: [{ kind: 'MEMORY', title: '长期记忆' }, { kind: 'CURRENT_PATH', title: '当前会话路径' }],
    }],
  });
  assert.ok(html.includes('data-action="show-answer-context"'));
  assert.ok(html.includes('查看回答上下文 · 2'));
  assert.ok(!html.includes('private/secret'));
});

test('settings expose the latest phone values in the desktop primary and secondary split', () => {
  const appearance = renderChatFirstShell({ data, native: true, pane: 'settings', settingsSection: 'personalization', status: '', error: '', connection: {}, appearance: { mode: 'dark', fontSize: 'large', themeColor: 'green' } });
  for (const token of ['aria-label="设置一级菜单"', 'aria-label="设置二级页面"', '外观', '深色', '字体大小', '大', '主题色', '绿色', 'open-settings-picker']) assert.ok(appearance.includes(token));
  const favorites = renderChatFirstShell({ data, native: true, pane: 'settings', settingsSection: 'favorites', status: '', error: '', connection: {}, favoriteConversationIds: new Set([conversation.id]) });
  for (const token of ['收藏', 'toggle-conversation-favorite', '双端同步']) assert.ok(favorites.includes(token));
  const about = renderChatFirstShell({ data, native: true, pane: 'settings', settingsSection: 'about', status: '', error: '', connection: {}, runtimeInfo: { version: '0.0.1', platform: 'macos', arch: 'aarch64' } });
  for (const token of ['Desktop 版 0.0.1', 'macos', 'aarch64']) assert.ok(about.includes(token));
  assert.ok(!about.includes('读取中'));
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
  assert.equal((reminders.match(/role="switch" disabled/g) || []).length, 2);
  assert.equal((reminders.match(/aria-pressed="false" role="switch" disabled/g) || []).length, 2);
  const model = renderChatFirstShell({ data, native: true, pane: 'settings', settingsSection: 'model', status: '', error: '', connection: {}, settingsCapabilities: capabilities });
  assert.ok(model.includes('需要当前信息时自动检索公开网页并标注来源'));
  assert.doesNotMatch(model, /data-key="webSearchEnabled"[^>]*disabled/);
  const development = renderChatFirstShell({ data, native: true, pane: 'settings', settingsSection: 'development', status: '', error: '', connection: {}, settingsCapabilities: capabilities });
  assert.ok(!development.includes('功能审阅 · 南枫转写'));
});

test('static Desktop build ships every startup module including the full-search renderer', async () => {
  const buildSource = await readFile(resolve(import.meta.dirname, '../scripts/build.mjs'), 'utf8');
  assert.match(buildSource, /'desktop-parity-preferences\.mjs'/);
  assert.match(buildSource, /'desktop-search-page\.mjs'/);
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

test('current Android model catalog keeps GLM-OCR visible but outside chat selection', () => {
  const zhipu = renderChatFirstShell({
    data, native: true, pane: 'settings', settingsSection: 'model-configuration', status: '', error: '', connection: {},
    settingsPicker: 'modelPreset', modelProviderId: 'ZHIPU',
  });
  for (const token of ['GLM-5.3', 'GLM-5.3 Flash', 'GLM-OCR', '图片与 PDF 转 Markdown · 使用同一智谱 API Key', '仅在左侧栏“南枫转写”中调用，不加入聊天模型选择。']) assert.ok(zhipu.includes(token));
  assert.ok(!zhipu.includes('data-preset-id="GLM_OCR"'));
  for (const retired of ['Grok 4.1', 'Grok 4.5', 'Grok 4.6']) assert.ok(!zhipu.includes(retired));
});

test('model record pages use the latest Android empty states and real usage projection', () => {
  const emptyCost = renderChatFirstShell({ data, native: true, pane: 'settings', settingsSection: 'conversation-cost', status: '', error: '', connection: {} });
  for (const token of ['尚无可用费用记录', '已返回输入和输出 Token 的调用会显示服务商金额或本地估算。']) assert.ok(emptyCost.includes(token));
  const context = renderChatFirstShell({ data, native: true, pane: 'settings', settingsSection: 'context-selections', status: '', error: '', connection: {} });
  assert.ok(context.includes('还没有上下文记录。'));
  const diagnostics = renderChatFirstShell({ data, native: true, pane: 'settings', settingsSection: 'diagnostics', status: '', error: '', connection: {} });
  for (const token of ['连接失败', '对话与连接测试中近 7 天的失败记录', '近 7 天没有连接失败记录。', '自动与工具任务', '暂无自动或工具任务调用记录。']) assert.ok(diagnostics.includes(token));

  const cost = renderChatFirstShell({
    data, native: true, pane: 'settings', settingsSection: 'conversation-cost', status: '', error: '', connection: {},
    usageLedger: { records: [{ entryId: 'entry-1', conversationId: conversation.id, modelId: 'gpt-5.6-terra', factGrade: 'PROVIDER_REPORTED', inputTokens: 1200, outputTokens: 320, cachedInputTokens: 0, chargeMicros: 12500, currencyCode: 'USD', occurredAtMs: 1788172800000 }], inputTokens: 1200, outputTokens: 320, cachedInputTokens: 0 },
  });
  for (const token of ['本机累计', '服务商实际金额', '$0.0125', '1,200', '会话标题整理', '历史资料整理', '南枫转写', 'gpt-5.6-terra']) assert.ok(cost.includes(token));
});

test('local data page renders aggregate-only all-workspace inventory in Android order', () => {
  const html = renderChatFirstShell({
    data, native: true, pane: 'settings', settingsSection: 'privacy', status: '', error: '', connection: {},
    privacyInventory: { totalBytes: 15360, aggregates: [
      { id: 'search_text', count: 2, byteCount: 1200 },
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

test('local data cleanup keeps exact confirmation gates and routes conversation trash to conversation management', async () => {
  const appSource = await readFile(resolve(import.meta.dirname, '../src/app.mjs'), 'utf8');
  for (const token of [
    '清理失败任务',
    '选择后可逐项清理失败任务的附件。',
    '已归档与回收站对话统一在“对话管理”中清理。',
    '删除全部本地数据',
    '删除全部本机业务数据，需输入确认文字。',
    '预览已选 ${selected.size} 项',
    '已确认清理范围。',
    '输入：删除全部本地业务数据',
    '确认删除全部本地业务数据',
  ]) assert.ok(appSource.includes(token));
  assert.doesNotMatch(appSource, /data-scope="KNOWLEDGE_MEMORY_TRASH"/);
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
  for (const token of ['全屏搜索', 'role="tablist"', 'aria-selected="true" class="selected">文件', 'data-entry-id="search-workspace-safe-2-message-safe-2-attachment"', '资料.pdf', 'application/pdf']) assert.ok(html.includes(token));
  const appSource = await readFile(resolve(import.meta.dirname, '../src/app.mjs'), 'utf8');
  assert.match(appSource, /invoke\('query_desktop_local_index'/);
  assert.match(appSource, /recordHistory: Boolean\(recordHistory/);
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
  for (const token of ['会话消息树尚未物理删除；恢复后会回到普通对话列表。', '清空回收站', 'restore-deleted-conversation', 'open-conversation-permanent-delete', '永久删除会话', '回收站会话']) assert.ok(recycle.includes(token));
  const appSource = await readFile(resolve(import.meta.dirname, '../src/app.mjs'), 'utf8');
  assert.match(appSource, /invoke\('permanently_delete_desktop_conversation'/);
  assert.match(appSource, /conversation-bulk-recycle/);
  assert.match(appSource, /已永久删除会话。/);
});
