import test from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { resolve } from 'node:path';
import { renderDesktopTranscriptionPage } from '../src/desktop-transcription-page.mjs';

const desktopRoot = resolve(import.meta.dirname, '..');
const source = file => readFile(resolve(desktopRoot, 'src', file), 'utf8');

test('all Desktop copy surfaces use the shared icon-to-check feedback contract', async () => {
  const [app, shell, markdown, account, css] = await Promise.all([
    source('app.mjs'),
    source('chat-shell.mjs'),
    source('safe-markdown.mjs'),
    source('android-settings-shell.mjs'),
    source('chat-shell.css'),
  ]);
  for (const [action, owner] of [
    ['copy-message', shell],
    ['copy-markdown-block', markdown],
    ['copy-text-preview', app],
  ]) {
    assert.match(owner, new RegExp(`data-action="${action}"[^>]*data-copy-action|data-copy-action[^>]*data-action="${action}"`), action);
  }
  for (const token of [
    'const COPY_SUCCESS_DURATION_MS = 1200',
    'function showCopyIconFeedback(button)',
    "button.innerHTML = icon(icons.check, '已复制')",
    "showCopyIconFeedback(target)",
  ]) assert.ok(app.includes(token), token);
  assert.ok(css.includes('[data-copy-action].is-copy-success'));
  assert.ok(!app.includes('showInlineCopyFeedback'));
  assert.ok(!app.includes('chat-copy-success'));
  assert.ok(!account.includes('<span>复制</span>'));
});

test('Desktop transcription renders its completed-copy control as an icon, never visible copy text', () => {
  const html = renderDesktopTranscriptionPage({
    native: true,
    selectedTaskId: 'copy-contract',
    projection: {
      settings: {},
      tasks: [{
        id: 'copy-contract', workspaceId: 'workspace', sourceAttachmentId: 'source', sourceMimeType: 'audio/wav',
        sourceDisplayName: 'copy.wav', sourceByteCount: 1, modelId: 'qwen3-asr-flash', languageCode: 'zh',
        state: 'COMPLETED', progressMillis: 1, totalDurationMillis: 1, providerRequestCount: 0,
        inputTokens: null, outputTokens: null, estimatedChargeMicros: 0, segments: [{ text: '可复制正文' }],
      }],
    },
  });
  assert.match(html, /class="transcription-copy-action" data-action="copy-transcription-result" data-copy-action/);
  assert.doesNotMatch(html, />复制全文<\/button>/);
});
