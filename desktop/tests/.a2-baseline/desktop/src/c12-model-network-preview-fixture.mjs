const occurredAtMs = Date.parse('2026-09-03T10:12:00+08:00');

const usage = (entryId, overrides = {}) => ({
  entryId,
  conversationId: 'conversation-preview-01',
  providerId: 'OPENROUTER',
  modelId: 'openai/gpt-5.6-terra',
  factGrade: 'PROVIDER_REPORTED',
  inputTokens: 1240,
  outputTokens: 680,
  cachedInputTokens: 220,
  chargeMicros: 14250,
  currencyCode: 'USD',
  occurredAtMs,
  ...overrides,
});

export function createC12ModelNetworkPreview() {
  const records = [
    usage('usage-conversation-c12-preview'),
    usage('usage-title-c12-preview', { outputTokens: 42, chargeMicros: 760 }),
    usage('usage-history-c12-preview', { outputTokens: 128, chargeMicros: 1910 }),
    usage('usage-transcription-c12-preview', { providerId: 'ZHIPU', modelId: 'glm-ocr', inputTokens: 90, outputTokens: 410, chargeMicros: 3250 }),
    usage('usage-reminder-c12-preview', { inputTokens: 180, outputTokens: 96, chargeMicros: 1120 }),
  ];
  return {
    usageLedger: {
      records,
      inputTokens: records.reduce((sum, record) => sum + record.inputTokens, 0),
      outputTokens: records.reduce((sum, record) => sum + record.outputTokens, 0),
      cachedInputTokens: records.reduce((sum, record) => sum + record.cachedInputTokens, 0),
    },
    contextSelectionRecords: [{
      conversationId: 'conversation-preview-01',
      providerId: 'OPENROUTER',
      providerLabel: 'OpenRouter',
      modelId: 'openai/gpt-5.6-terra',
      fixedInputTokens: 1680,
      createdAtMs: occurredAtMs,
      selectedSources: [
        { kind: '资料库', title: 'C12 Android→Desktop 同步合同' },
        { kind: '会话', title: '当前对话上下文' },
        { kind: '记忆', title: '本机用户偏好摘要' },
      ],
    }],
    diagnosticRecords: [{
      conversationTitle: '新对话',
      providerId: 'OPENROUTER',
      providerLabel: 'OpenRouter',
      modelId: 'openai/gpt-5.6-terra',
      summary: '网络连接失败，未自动重发。',
      latencyMs: 1450,
      createdAtMs: occurredAtMs,
    }],
    invocationRecords: [{
      kind: 'HISTORY_KNOWLEDGE',
      title: '历史资料整理',
      summary: '已完成并写入本机调用记录。',
      latencyMs: 2860,
      createdAtMs: occurredAtMs,
    }],
  };
}
