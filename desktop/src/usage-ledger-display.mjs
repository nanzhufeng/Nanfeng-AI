/** Read-only presentation; never replaces immutable physical accounting entries. */
export function groupRetrievalUsageRecords(records) {
  const groups = new Map();
  const rows = [];
  for (const record of records) {
    const key = record.callMetadata?.role === 'RETRIEVAL' && record.attemptId && record.conversationId && record.requestedModelId && record.callMetadata.modelDisplayName
      ? JSON.stringify([record.conversationId, record.attemptId, record.requestedModelId, record.callMetadata.modelDisplayName,
        record.callMetadata.serviceId, record.callMetadata.serviceEngineId]) : null;
    if (key == null) { rows.push([record]); continue; }
    if (!groups.has(key)) { const members = []; groups.set(key, members); rows.push(members); }
    groups.get(key).push(record);
  }
  const sum = values => {
    if (values.some(value => !Number.isSafeInteger(value) || value < 0)) return null;
    const total = values.reduce((a, b) => a + b, 0);
    return Number.isSafeInteger(total) ? total : null;
  };
  return rows.map(members => members.length === 1 ? members[0] : {
    ...members[0], groupedCalls: members,
    // Do not put a made-up settlement on the grouped display row.
    chargeMicros: null, currencyCode: null, costSource: null,
    occurredAtMs: Math.min(...members.map(record => record.occurredAtMs)),
    inputTokens: sum(members.map(record => record.inputTokens)),
    outputTokens: sum(members.map(record => record.outputTokens)),
    cachedInputTokens: sum(members.map(record => record.cachedInputTokens)),
    kind: members.some(record => record.kind === 'STREAM_PENDING') ? 'STREAM_PENDING' : members[0].kind,
    callMetadata: { ...members[0].callMetadata,
      reasoningTokens: sum(members.map(record => record.callMetadata?.reasoningTokens)),
      contextWindowTokens: null, contextSourceCount: null,
    },
  });
}

export const usageRecordPhysicalFacts = record => record.groupedCalls || [record];
