import { renderConfiguredChatShell as renderChatFirstShell } from './helpers/enabled-model-catalog.mjs';
import test from 'node:test';
import assert from 'node:assert/strict';
import { canOpenDataArea } from '../src/product-scope.mjs';
import {  desktopComposerModelCandidates } from '../src/chat-shell.mjs';
import { MODEL_SERVICE_PREVIEW_SETTINGS, renderAndroidSettingsShell } from '../src/android-settings-shell.mjs';

test('chat-only shell hides work entry and cross-area actions while keeping model switching', () => {
  const html = renderChatFirstShell({ data: null, pane: 'chat', native: true, assistantMessageMenu: { messageId: 'fixture', role: 'assistant' } });
  assert.doesNotMatch(html, /data-action="(?:show-work|show-projects|show-knowledge|reference-message-to-other-area)"/);
  assert.doesNotMatch(html, /class="chat-mode-switch"/);
  assert.ok(desktopComposerModelCandidates(MODEL_SERVICE_PREVIEW_SETTINGS, [], true).length > 6);
  assert.equal(canOpenDataArea('WORK'), false);
  assert.equal(canOpenDataArea('CHAT'), true);
});

test('settings hides project navigation and states the retained-data product decision', () => {
  const html = renderAndroidSettingsShell({ page: 'development', native: true });
  assert.doesNotMatch(html, /data-page="workspace"|项目与知识/);
  assert.match(html, /多模型聊天 AI/);
  assert.match(html, /已有工作区数据保留/);
});
