import test from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { renderAndroidSettingsShell } from '../src/android-settings-shell.mjs';
import { renderChatFirstShell } from '../src/chat-shell.mjs';
import { renderDesktopTranscriptionPage } from '../src/desktop-transcription-page.mjs';

const data = {
  summary: { id: 'workspace-deep' },
  exchange: {
    conversations: [
      { id: 'archived-one', title: '归档会话', archived: true, deleted: false, revision: 2, createdAt: '2026-08-01T00:00:00Z', updatedAt: '2026-09-01T00:00:00Z', messages: [] },
    ],
    memory: [
      { id: 'memory-one', body: '偏好真实、可追溯的交付。', scope: 'GLOBAL', status: 'ACTIVE', revision: 3, createdAt: '2026-08-02T00:00:00Z', updatedAt: '2026-08-03T00:00:00Z' },
    ],
  },
};

test('lifecycle settings list uses creation time and opens a read-only conversation with exact return owner', () => {
  const list = renderAndroidSettingsShell({ page: 'archived', data, native: true });
  assert.match(list, /data-action="select-chat" data-id="archived-one"/);
  assert.match(list, /创建于 2026-08-01 08:00/);
  assert.doesNotMatch(list, /更新于 2026-09-01/);
  const conversation = renderChatFirstShell({
    data,
    native: true,
    pane: 'chat',
    selectedConversationId: 'archived-one',
    settingsConversationReturn: { page: 'archived', label: '已归档', readOnly: true },
    composerDraft: '',
    chatSearch: '',
    profileOpen: false,
    sidebarOpen: false,
    status: '',
    error: '',
    connection: {},
  });
  assert.match(conversation, /return-to-settings-conversation-list/);
  assert.match(conversation, /会话生命周期只读查看/);
  assert.doesNotMatch(conversation, /data-action="save-local-message"/);
});

test('memory overview matches the Android summary owner with explicit query, full editing, and destructive confirmations', async () => {
  const html = renderAndroidSettingsShell({
    page: 'memory-overview',
    data,
    native: true,
    settings: { memoryEnabled: true },
    memorySummaryQuery: '可追溯',
    memorySummaryComposer: '继续保留真实证据',
    memorySummaryNotice: '本机筛选完成',
  });
  for (const token of ['偏好真实、可追溯的交付。', 'edit-memory-summary', 'refresh-memory-summary', 'ask-delete-memory-summary', 'ask-disable-memory-summary', '本机筛选完成']) assert.ok(html.includes(token));
  assert.ok(!html.includes('询问或更新'));
  assert.ok(!html.includes('submit-memory-summary'));
  assert.ok(!html.includes('data-action="show-memory"'));
  const empty = renderAndroidSettingsShell({ page: 'memory-overview', data: { ...data, exchange: { ...data.exchange, memory: [] } }, native: true, settings: { memoryEnabled: true } });
  assert.ok(empty.includes('还没有记忆摘要。'));
  assert.doesNotMatch(empty, /data-action="edit-memory-summary"[^>]*disabled/);
  const [app, rust] = await Promise.all([
    readFile(new URL('../src/app.mjs', import.meta.url), 'utf8'),
    readFile(new URL('../src-tauri/src/lib.rs', import.meta.url), 'utf8'),
  ]);
  assert.match(app, /action: editor\.memoryId \? 'replaceMemorySummary' : 'create'/);
  assert.match(app, /id="memory-summary-editor"/);
  assert.match(rust, /args\.action == "replaceMemorySummary" && args\.entity == "memory"/);
  assert.match(rust, /item\.insert\("status"\.into\(\), Value::String\("DELETED"\.into\(\)\)\)/);
});

test('personalization exposes the shared full-screen custom-instructions draft editor', () => {
  const html = renderAndroidSettingsShell({
    page: 'personalization',
    native: true,
    settings: { customInstructions: '保持简洁', memoryEnabled: true },
    personalizationDraft: { customInstructions: '保持简洁', memoryEnabled: true },
    capabilities: { ordinaryChatPersonalization: true, historyLibrary: true },
  });
  assert.match(html, /data-action="open-custom-instructions-fullscreen"/);
  assert.match(html, /自定义指令/);
  assert.match(html, /class="android-settings-fullscreen-editor"[^>]*aria-label="全屏编辑自定义指令"[^>]*><svg/);
  assert.doesNotMatch(html, />全屏编辑<\/button>/);
});

test('GLM-OCR detail projects persisted page, token, attempt, request id, cost and safe error facts', () => {
  const html = renderDesktopTranscriptionPage({
    native: true,
    mode: 'document',
    selectedTaskId: 'ocr-one',
    projection: {
      settings: {},
      tasks: [{
        id: 'ocr-one', modelId: 'glm-ocr', sourceMimeType: 'application/pdf', sourceDisplayName: '资料.pdf', sourceByteCount: 2048,
        state: 'FAILED', providerRequestCount: 1, providerRequestId: 'request-safe', pageCount: 7, inputTokens: 120, outputTokens: 340,
        estimatedChargeMicros: 92, attemptCount: 1, errorCode: 'RATE_LIMIT', userMessage: '请稍后显式重试', updatedAtMillis: 1788250000000,
        segments: [],
      }],
    },
  });
  for (const token of ['7 页', '120 / 340 Token', 'request-safe', 'RATE_LIMIT', 'Attempt', '显式重试']) assert.ok(html.includes(token));
});

test('deep settings state is restored per page and confirmed reminder edits use the native owner', async () => {
  const app = await readFile(new URL('../src/app.mjs', import.meta.url), 'utf8');
  const settings = await readFile(new URL('../src/android-settings-shell.mjs', import.meta.url), 'utf8');
  const rust = await readFile(new URL('../src-tauri/src/desktop_reminders_v1.rs', import.meta.url), 'utf8');
  const permissions = await readFile(new URL('../src-tauri/permissions/default.toml', import.meta.url), 'utf8');
  for (const token of ['settingsScrollPositions', 'data-settings-scroll-key', 'settingsConversationReturn', 'update_desktop_reminder_plan']) assert.ok(`${app}\n${settings}\n${rust}`.includes(token));
  assert.match(permissions, /allow-desktop-reminders[\s\S]*update_desktop_reminder_plan/);
  assert.match(rust, /expected_updated_at_ms/);
  assert.match(rust, /next_run_at_ms/);
});

test('full-screen instruction cancel restores its entry draft and v2 import reconciles local projections', async () => {
  const app = await readFile(new URL('../src/app.mjs', import.meta.url), 'utf8');
  assert.match(app, /previousDraft: \{ \.\.\.state\.personalizationDraft \}/);
  assert.match(app, /state\.personalizationDraft = \{ \.\.\.state\.dialog\.previousDraft \}/);
  assert.match(app, /import_desktop_workspace_exchange_v2_selected[\s\S]{0,200}await refresh\(\)/);
});
