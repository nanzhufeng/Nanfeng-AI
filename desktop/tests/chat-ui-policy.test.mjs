import assert from 'node:assert/strict';
import test from 'node:test';
import { renderLocalDataCleanupScopeDialog } from '../src/local-data-view.mjs';
import { renderChatFirstShell } from '../src/chat-shell.mjs';
import { renderAndroidSettingsShell } from '../src/android-settings-shell.mjs';
import { chatUiActionAllowed, chatUiRoute, renderChatHistorySources } from '../src/chat-ui-policy.mjs';

const runtimeInfo = { platform: 'windows', dataAreasReady: true };
const data = { summary: { id: 'chat-source' }, exchange: { conversations: [], projects: [], knowledge: [], memory: [], relations: [] } };
const settings = { memoryEnabled: true, memoryRetrievalEnabled: true };

test('Windows chat navigation excludes work modes and keeps reminders, transcription and historical chat collections', () => {
  const html = renderChatFirstShell({ data, native: true, pane: 'chat', composerDraft: '', runtimeInfo, productSettings: settings,
    workspaces: [{ id: 'chat-source', title: '聊天' }, { id: 'imported', title: '导入记录' }], status: '', error: '', connection: {} });
  assert.doesNotMatch(html, /data-action="(?:show-work|show-projects|show-knowledge|show-p8-inspect|select-workspace)"/);
  assert.match(html, /data-action="show-reminders"/);
  assert.match(html, /data-action="show-transcription"/);
  assert.match(html, /data-action="select-chat-source" data-id="imported"/);
  assert.doesNotMatch(html, /aria-label="产品模式"|本地工作区/);
});

test('Windows settings retain chat memory and reminders while removing workspace tools across data and about pages', () => {
  for (const page of ['personalization', 'data', 'about', 'workspace', 'development', 'memory-overview', 'reminders']) {
    const html = renderAndroidSettingsShell({ page, runtimeInfo, data, native: true, settings });
    assert.doesNotMatch(html, /data-page="(?:workspace|development)"|data-action="(?:show-projects|show-knowledge|show-p8-inspect|start-import|start-export|select-nanfeng-knowledge-export)"/);
    assert.doesNotMatch(html, /data-area="WORK"|data-key="historyLibraryEnabled"/);
    assert.match(html, /data-page="reminders"/);
    if (page === 'personalization') assert.match(html, /data-page="memory-overview"/);
    if (page === 'about') assert.match(html, /支持多模型、聊天记忆、附件和提醒的聊天工具/);
  }
});

test('stale workspace routes cannot revive retired pages; memory and chat-source routes remain allowed', () => {
  for (const pane of ['work', 'projects', 'knowledge', 'memory', 'p8-inspect']) assert.equal(chatUiRoute(pane, 'workspace', runtimeInfo).pane, 'chat');
  for (const action of ['show-work', 'show-projects', 'show-knowledge', 'show-memory', 'show-p8-inspect', 'reference-message-to-other-area']) assert.equal(chatUiActionAllowed(action, {}, runtimeInfo), false);
  assert.equal(chatUiActionAllowed('open-settings-page', { page: 'workspace' }, runtimeInfo), false);
  assert.equal(chatUiActionAllowed('select-local-backup-area', { area: 'WORK' }, runtimeInfo), false);
  assert.equal(chatUiActionAllowed('select-chat-source', {}, runtimeInfo), true);
  assert.equal(chatUiActionAllowed('open-settings-page', { page: 'memory-overview' }, runtimeInfo), true);
  assert.equal(chatUiActionAllowed('show-reminders', {}, runtimeInfo), true);
  assert.equal(chatUiActionAllowed('show-work', {}, { platform: 'macos' }), true);
});

test('history-source presentation escapes titles and preserves the source identity', () => {
  const html = renderChatHistorySources(data, [{ id: 'chat-source', title: '<script>历史</script>' }, { id: 'other', title: '其他记录' }]);
  assert.match(html, /&lt;script&gt;历史&lt;\/script&gt;/);
  assert.match(html, /data-id="chat-source"[^>]*aria-current="true"/);
  assert.doesNotMatch(html, /<script>|select-workspace/);
});

test('Windows chat utility pages retain their body and editor while legacy workspace panels stay hidden', () => {
  for (const pane of ['reminders', 'transcription']) {
    const html = renderChatFirstShell({ data, native: true, pane, runtimeInfo, productSettings: settings, composerDraft: '', workPanel: '<section id="utility-body">可操作的正文与编辑器</section>', status: '', error: '', connection: {} });
    assert.match(html, /id="utility-body"/);
    assert.doesNotMatch(html, /aria-label="产品模式"|data-action="show-work"/);
  }
  const html = renderChatFirstShell({ data, native: true, pane: 'projects', runtimeInfo, productSettings: settings, composerDraft: '', workPanel: '<section id="old-project-body">旧项目正文</section>', status: '', error: '', connection: {} });
  assert.doesNotMatch(html, /id="old-project-body"/);
});

test('Windows JSON results preserve chat imports but hide saved knowledge tasks and their conflict actions', () => {
  const knowledge = { status: 'PENDING_CONFIRMATION', items: [{ id: 'knowledge-conflict', title: '旧知识候选', status: 'PENDING_CONFIRMATION', failure: 'CONFLICT_REIMPORT' }] };
  const context = { page: 'json-import-results', runtimeInfo, data, native: true, settings,
    nanfengKnowledgeTask: knowledge, chatgptTask: { status: 'COMPLETED', importedCount: 1 }, claudeTask: { status: 'COMPLETED', importedCount: 2 } };
  const html = renderAndroidSettingsShell(context);
  assert.match(html, /ChatGPT JSON/);
  assert.match(html, /Claude JSON/);
  assert.doesNotMatch(html, /南枫知识库 JSON|旧知识候选|confirm-nanfeng-knowledge-conflict|skip-nanfeng-knowledge-conflict/);
  const empty = renderAndroidSettingsShell({ ...context, chatgptTask: null, claudeTask: null });
  assert.match(empty, /还没有导入记录/);
  for (const action of ['confirm-nanfeng-knowledge-conflict', 'skip-nanfeng-knowledge-conflict']) assert.equal(chatUiActionAllowed(action, {}, runtimeInfo), false);
  const mac = renderAndroidSettingsShell({ ...context, runtimeInfo: { platform: 'macos' } });
  assert.match(mac, /南枫知识库 JSON/);
  assert.match(mac, /confirm-nanfeng-knowledge-conflict/);
  assert.equal(context.nanfengKnowledgeTask, knowledge);
});

test('Windows cleanup retains chat utility choices but excludes the retired knowledge trash entry and action', () => {
  const windows = renderLocalDataCleanupScopeDialog({ runtimeInfo });
  assert.doesNotMatch(windows, /KNOWLEDGE_MEMORY_TRASH|清空知识/);
  for (const scope of ['TEMPORARY_FAILED_TASK_ASSETS', 'ALL_LOCAL_BUSINESS_DATA', 'WINDOWS_SHARE_COPIES']) {
    assert.match(windows, new RegExp(`data-scope="${scope}"`));
    assert.equal(chatUiActionAllowed('select-privacy-cleanup-scope', { scope }, runtimeInfo), true);
  }
  assert.equal(chatUiActionAllowed('select-privacy-cleanup-scope', { scope: 'KNOWLEDGE_MEMORY_TRASH' }, runtimeInfo), false);
  assert.match(renderLocalDataCleanupScopeDialog({ runtimeInfo: { platform: 'macos' } }), /KNOWLEDGE_MEMORY_TRASH/);
  assert.equal(chatUiActionAllowed('select-privacy-cleanup-scope', { scope: 'KNOWLEDGE_MEMORY_TRASH' }, { platform: 'macos' }), true);
});

test('before native runtime metadata arrives, Windows browser fallback hides retired entries and retains memory/reminders', () => {
  const previousWindow = Object.getOwnPropertyDescriptor(globalThis, 'window');
  const previousNavigator = Object.getOwnPropertyDescriptor(globalThis, 'navigator');
  try {
    Object.defineProperty(globalThis, 'window', { configurable: true, value: {} });
    Object.defineProperty(globalThis, 'navigator', { configurable: true, value: { platform: 'Win32' } });
    const html = renderChatFirstShell({ data, native: true, pane: 'chat', composerDraft: '', runtimeInfo: {}, productSettings: settings, status: '', error: '', connection: {} });
    assert.doesNotMatch(html, /data-action="(?:show-work|show-projects|show-knowledge|show-p8-inspect|select-workspace)"|aria-label="产品模式"/);
    assert.match(html, /data-action="show-reminders"/);
    assert.match(html, /data-action="show-transcription"/);
    const settingsHtml = renderAndroidSettingsShell({ page: 'personalization', runtimeInfo: {}, data, native: true, settings });
    assert.doesNotMatch(settingsHtml, /data-page="(?:workspace|development)"/);
    assert.match(settingsHtml, /data-page="memory-overview"/);
    assert.match(settingsHtml, /data-page="reminders"/);
    // Native metadata remains authoritative on the shared frontend.
    assert.equal(chatUiActionAllowed('show-work', {}, { platform: 'macos' }), true);
  } finally {
    if (previousWindow) Object.defineProperty(globalThis, 'window', previousWindow); else delete globalThis.window;
    if (previousNavigator) Object.defineProperty(globalThis, 'navigator', previousNavigator); else delete globalThis.navigator;
  }
});

test('Windows imported-history selector names the default local source as chat without exposing workspace wording', () => {
  const local = { id: 'workspace-local-synthetic', title: '本机工作区' };
  const imported = { id: 'imported-chat', title: 'ChatGPT 聊天记录' };
  const html = renderChatFirstShell({ data: { ...data, summary: { id: local.id } }, native: true, pane: 'chat', composerDraft: '', runtimeInfo,
    workspaces: [local, imported], status: '', error: '', connection: {} });
  assert.ok(html.includes('本机聊天'), 'default local source has a chat label');
  assert.ok(!html.includes('本机工作区'), 'default internal workspace label is hidden');
  assert.ok(html.includes('data-id="workspace-local-synthetic"'), 'source identity remains intact');
  assert.equal(local.title, '本机工作区', 'stored source title is unchanged');
});
test('chat source presentation preserves user and imported names including workspace words', () => {
  const sources = [{ id: 'workspace-local-synthetic', title: '我的工作区笔记' }, { id: 'imported-chat', title: '本机工作区' }];
  const html = renderChatHistorySources(data, sources);
  assert.ok(html.includes('我的工作区笔记')); assert.ok(html.includes('本机工作区'));
});
