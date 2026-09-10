import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import test from 'node:test';
import { renderDesktopTranscriptionPage } from '../src/desktop-transcription-page.mjs';

const documentTask = (state, overrides = {}) => ({
  id: `c10-${state.toLowerCase()}`,
  workspaceId: 'workspace-c10-fixture',
  sourceAttachmentId: 'attachment-c10-source',
  sourceMimeType: 'application/pdf',
  sourceDisplayName: 'C10-固定样本.pdf',
  sourceByteCount: 4096,
  modelId: 'glm-ocr',
  languageCode: null,
  state,
  progressMillis: 0,
  totalDurationMillis: 0,
  errorCode: null,
  userMessage: null,
  attemptCount: 1,
  resultAttachmentId: null,
  providerRequestCount: 0,
  providerRequestId: null,
  pageCount: null,
  billableAudioMillis: 0,
  inputTokens: null,
  outputTokens: null,
  estimatedChargeMicros: 0,
  updatedAtMillis: Date.parse('2026-09-03T09:00:00+08:00'),
  segments: [],
  ...overrides,
});

test('C10 empty root is the Android single canvas with a usable orange primary action', () => {
  const html = renderDesktopTranscriptionPage({
    projection: { settings: {}, tasks: [] },
    previewInteractive: true,
    evidenceLabel: 'Web 只读交互样本不会写入 Desktop SQLite',
  });
  assert.match(html, /<h1>南枫转写<\/h1>/);
  assert.match(html, /还没有转写结果/);
  assert.match(html, /拖入图片或 PDF，或点击下方按钮开始。/);
  assert.match(html, /class="[^"]*transcription-empty-canvas[^"]*"/);
  assert.doesNotMatch(html, /class="transcription-workspace"/);
  assert.match(html, /class="primary transcription-pick"[^>]*data-action="pick-transcription-document"(?![^>]*disabled)/);
  assert.match(html, /只读交互样本/);
  assert.match(html, /data-action="show-chat"[^>]*aria-label="关闭南枫转写"/);
  assert.doesNotMatch(html, /data-action="(?:record|pause)-transcription/);
});

test('C10 imported document uses Android READY and processing semantics', () => {
  const ready = renderDesktopTranscriptionPage({
    projection: { settings: {}, tasks: [documentTask('QUEUED')] },
    selectedTaskId: 'c10-queued',
    native: true,
  });
  assert.match(ready, /待确认/);
  assert.match(ready, />原始文件</);
  assert.match(ready, /点击查看原始 PDF/);
  assert.match(ready, /data-action="run-transcription-task"/);
  assert.match(ready, />开始转写</);

  const processing = renderDesktopTranscriptionPage({
    projection: { settings: {}, tasks: [documentTask('TRANSCRIBING')] },
    selectedTaskId: 'c10-transcribing',
    native: true,
    busyTaskId: 'c10-transcribing',
  });
  assert.match(processing, /正在转写/);
  assert.match(processing, /离开页面后任务仍会继续。/);
});

test('C10 failure and completion keep the original task with real recovery and result actions', () => {
  const failed = renderDesktopTranscriptionPage({
    projection: { settings: {}, tasks: [documentTask('FAILED', { errorCode: 'CREDENTIAL_MISSING', userMessage: '请先在设置启用智谱并保存 API Key' })] },
    selectedTaskId: 'c10-failed',
    native: true,
  });
  assert.match(failed, /请先在设置启用智谱并保存 API Key/);
  assert.match(failed, /data-action="retry-transcription-task"/);
  assert.match(failed, />重新转写</);
  assert.match(failed, /CREDENTIAL_MISSING/);
  assert.match(failed, /请求 ID/);

  const completed = renderDesktopTranscriptionPage({
    projection: { settings: {}, tasks: [documentTask('COMPLETED', {
      resultAttachmentId: 'attachment-c10-result',
      pageCount: 2,
      inputTokens: 120,
      outputTokens: 340,
      estimatedChargeMicros: 92,
      segments: [{ ordinal: 0, startMillis: 0, endMillis: 0, text: '# C10 结果\n\n已持久化 Markdown。' }],
    })] },
    selectedTaskId: 'c10-completed',
    native: true,
  });
  assert.match(completed, />Markdown 文件</);
  assert.match(completed, /data-action="preview-transcription-result"/);
  assert.match(completed, /data-action="continue-chat-with-transcription"/);
  assert.match(completed, />加入新对话</);
  assert.match(completed, /已持久化 Markdown/);
});

test('C10 Browser fixtures never call Provider or SQLite and native mutations stay on Rust commands', async () => {
  const [app, rust] = await Promise.all([
    readFile(new URL('../src/app.mjs', import.meta.url), 'utf8'),
    readFile(new URL('../src-tauri/src/desktop_transcription_v1.rs', import.meta.url), 'utf8'),
  ]);
  assert.match(app, /c10TranscriptionPreview/);
  assert.match(app, /Web 只读交互样本不会写入 Desktop SQLite/);
  assert.match(app, /import_desktop_ocr_source/);
  assert.match(app, /run_desktop_ocr_task/);
  assert.match(app, /retry_desktop_transcription_task/);
  assert.match(app, /read_desktop_transcription_state/);
  assert.match(rust, /desktop_transcription_tasks/);
  assert.match(rust, /GLM_OCR_MODEL_ID/);
  assert.match(rust, /state='TRANSCRIBING'/);
  assert.match(rust, /state='FAILED'/);
  assert.match(rust, /state='COMPLETED'/);
});
