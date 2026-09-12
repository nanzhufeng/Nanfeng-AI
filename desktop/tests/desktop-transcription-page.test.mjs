import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import test from 'node:test';
import { renderDesktopTranscriptionPage } from '../src/desktop-transcription-page.mjs';

const completed = {
  id: 'transcription-test', workspaceId: 'workspace-test', sourceAttachmentId: 'attachment-source',
  sourceMimeType: 'audio/wav', sourceDisplayName: '采访.wav', sourceByteCount: 2048,
  modelId: 'qwen3-asr-flash', languageCode: 'zh', state: 'COMPLETED', progressMillis: 61000,
  totalDurationMillis: 61000, errorCode: null, userMessage: null, attemptCount: 1,
  resultAttachmentId: 'attachment-result', providerRequestCount: 1, billableAudioMillis: 61000,
  inputTokens: 2, outputTokens: 3, estimatedChargeMicros: 13420,
  segments: [{ ordinal: 0, startMillis: 0, endMillis: 61000, text: '这是一段真实持久化的转写结果。' }],
};

test('current transcription root keeps legacy speech results readable without exposing new speech creation', () => {
  const html = renderDesktopTranscriptionPage({
    native: true,
    selectedTaskId: completed.id,
    projection: {
      settings: { modelId: 'qwen3-asr-flash', languageCode: 'zh', outputFormat: 'md', revision: 2, senseVoiceAvailable: false, senseVoiceStatus: '验证中／保留实验；Desktop 本地引擎与许可尚未完成' },
      tasks: [completed],
    },
  });
  for (const action of ['preview-transcription-source', 'copy-transcription-result', 'continue-chat-with-transcription', 'export-transcription-task', 'ask-delete-transcription-task']) assert.match(html, new RegExp(`data-action="${action}"`));
  assert.match(html, /class="transcription-copy-action" data-action="copy-transcription-result" data-copy-action/);
  assert.doesNotMatch(html, />复制全文<\/button>/);
  assert.doesNotMatch(html, /data-action="(?:record|pause)-transcription/);
  assert.doesNotMatch(html, /data-action="(?:select-transcription-mode|pick-transcription-source|save-transcription-settings)"/);
  assert.doesNotMatch(html, /SenseVoice|音视频默认设置/);
  assert.match(html, /¥0\.013420（估算）/);
  assert.match(html, /切片级时间轴/);
  assert.match(html, /旧版音视频任务/);
});

test('document transcription mode matches the mobile GLM-OCR intent while keeping a desktop workbench', () => {
  const document = {
    ...completed,
    id: 'ocr-test',
    sourceMimeType: 'application/pdf',
    sourceDisplayName: '产品说明.pdf',
    modelId: 'glm-ocr',
    totalDurationMillis: 0,
    progressMillis: 0,
    estimatedChargeMicros: 2,
    segments: [{ ordinal: 0, startMillis: 0, endMillis: 0, text: '# 产品说明\n\n保留 Markdown。' }],
  };
  const html = renderDesktopTranscriptionPage({ native: true, selectedTaskId: document.id, projection: { settings: completed.settings, tasks: [completed, document] }, mode: 'document' });
  for (const token of ['全部转写', 'GLM-OCR', 'Markdown', '选择图片或 PDF']) assert.match(html, new RegExp(token));
  for (const action of ['pick-transcription-document', 'preview-transcription-source', 'continue-chat-with-transcription']) assert.match(html, new RegExp(`data-action="${action}"`));
  assert.doesNotMatch(html, /data-action="select-transcription-mode"/);
  assert.doesNotMatch(html, /音视频默认设置/);
  assert.doesNotMatch(html, /切片级时间轴/);
});

test('empty transcription root uses the latest Android GLM-OCR copy', () => {
  const html = renderDesktopTranscriptionPage({ native: true, projection: { settings: {}, tasks: [] } });
  for (const token of ['还没有转写结果', '拖入图片或 PDF，或点击下方按钮开始。', '选择图片或 PDF']) assert.match(html, new RegExp(token));
  assert.doesNotMatch(html, /音频 \/ 视频|Qwen3-ASR|SenseVoice/);
});

test('desktop packaging and app bridge include the transcription owner and every native command', async () => {
  const [app, shell, build, index, rust, rustBridge] = await Promise.all([
    readFile(new URL('../src/app.mjs', import.meta.url), 'utf8'),
    readFile(new URL('../src/chat-shell.mjs', import.meta.url), 'utf8'),
    readFile(new URL('../scripts/build.mjs', import.meta.url), 'utf8'),
    readFile(new URL('../src/index.html', import.meta.url), 'utf8'),
    readFile(new URL('../src-tauri/src/desktop_transcription_v1.rs', import.meta.url), 'utf8'),
    readFile(new URL('../src-tauri/src/lib.rs', import.meta.url), 'utf8'),
  ]);
  for (const command of ['read_desktop_transcription_state', 'run_desktop_transcription_task', 'import_desktop_ocr_source', 'run_desktop_ocr_task', 'retry_desktop_transcription_task', 'cancel_desktop_transcription_task', 'delete_desktop_transcription_task', 'export_desktop_transcription_task']) assert.match(app, new RegExp(command));
  for (const legacyOwner of ['save_desktop_transcription_settings', 'import_desktop_transcription_source']) assert.match(rustBridge, new RegExp(legacyOwner));
  for (const retiredEntry of ['pickTranscriptionSource', 'saveTranscriptionSettings']) assert.doesNotMatch(app, new RegExp(retiredEntry));
  assert.match(shell, /show-transcription/);
  assert.match(build, /'desktop-transcription-page\.mjs'/);
  assert.match(build, /'desktop-transcription-page\.css'/);
  assert.match(index, /desktop-transcription-page\.css/);
  assert.match(app, /read_desktop_transcription_state', \{ workspaceId: null \}/);
  assert.match(app, /workspaceId: state\.current\?\.summary\?\.id \|\| ''/);
  assert.match(app, /\$\{documentTask \? '图片\/PDF 转写' : '语音转写'\}未完成/);
  assert.doesNotMatch(app, /请先导入或选择一个本地工作区/);
  assert.match(rust, /RECOVERY_REQUIRED/);
  assert.match(rust, /workspace-transcription-local/);
  assert.match(rust, /QWEN_ESTIMATED_MICRO_CNY_PER_SECOND/);
  assert.match(rust, /GLM_OCR_MODEL_ID/);
  assert.match(rust, /GLM_OCR_ENDPOINT/);
  assert.match(rust, /response exceeded 32 MiB/);
  assert.match(rust, /desktop_local_search_index/);
  assert.match(rust, /desktop_attachment_assets/);
});
