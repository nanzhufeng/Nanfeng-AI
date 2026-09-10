const updatedAtMillis = Date.parse('2026-09-03T09:00:00+08:00');

function documentTask(state, overrides = {}) {
  return {
    id: `c10-${state.toLowerCase()}`,
    workspaceId: 'workspace-c10-browser-fixture',
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
    createdAtMillis: updatedAtMillis,
    updatedAtMillis,
    segments: [],
    ...overrides,
  };
}

export function createC10TranscriptionPreviewProjection(mode = 'empty') {
  const settings = { modelId: 'glm-ocr', languageCode: null, outputFormat: 'md', revision: 0 };
  if (mode === 'ready') return { settings, tasks: [documentTask('QUEUED')] };
  if (mode === 'processing') return { settings, tasks: [documentTask('TRANSCRIBING')] };
  if (mode === 'failed') return { settings, tasks: [documentTask('FAILED', { errorCode: 'CREDENTIAL_MISSING', userMessage: '请先在设置启用智谱并保存 API Key' })] };
  if (mode === 'completed') return { settings, tasks: [documentTask('COMPLETED', { resultAttachmentId: 'attachment-c10-result', providerRequestCount: 1, providerRequestId: 'c10-browser-fixture-request', pageCount: 2, inputTokens: 120, outputTokens: 340, estimatedChargeMicros: 92, segments: [{ ordinal: 0, startMillis: 0, endMillis: 0, text: '# C10 结果\n\n已持久化 Markdown。' }] })] };
  return { settings, tasks: [] };
}

export function createC10ImportedPreviewTask() {
  return documentTask('QUEUED', { id: 'c10-preview-imported', sourceDisplayName: 'C10-刚选择样本.pdf' });
}
