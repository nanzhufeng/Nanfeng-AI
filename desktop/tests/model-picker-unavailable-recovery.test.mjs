import test from 'node:test';
import assert from 'node:assert/strict';
import { MODEL_SERVICE_PREVIEW_SETTINGS } from '../src/android-settings-shell.mjs';
import { renderChatFirstShell } from '../src/chat-shell.mjs';
const render = selection => renderChatFirstShell({ native: true, pane: 'chat', data: { summary: { id: 'synthetic-recovery' }, exchange: { conversations: [] } }, composerDraft: '合成草稿', connection: {},
  p6gModelPickerOpen: true, p6gSelection: selection, pendingNewConversationModelId: selection?.conversationOverride?.modelId,
  modelServiceSettings: MODEL_SERVICE_PREVIEW_SETTINGS.map(service => ({ ...service, enabled: false })),
});
test('an open tier losing its candidates still explains the empty state and offers configuration', () => {
  const html = render({ pickerTier: 'DAILY' }); assert.match(html, /此分组暂无可用模型/);
  assert.match(html, /data-action="open-settings-page"[^>]*data-page="model-configuration"/); assert.match(html, /返回模型分组/);
});
test('a disabled known model keeps its readable name and saved selection rather than exposing an internal ID', () => {
  const selection = { conversationOverride: { modelId: 'GPT_6_LUNA' } }; const html = render(selection);
  assert.match(html, /aria-label="选择模型：GPT-6 Luna"/); assert.equal(selection.conversationOverride.modelId, 'GPT_6_LUNA');
});
