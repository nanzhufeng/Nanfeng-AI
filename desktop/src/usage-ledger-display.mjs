/** Read-only presentation; never replaces immutable physical accounting entries. */
export function groupRetrievalUsageRecords(records) {
  const groups = new Map();
  const rows = [];
  for (const record of records) {
    const key = record.callMetadata?.role === 'RETRIEVAL' && record.executionId && record.sourceRootId && record.conversationId && record.requestedModelId && record.callMetadata.modelDisplayName
      ? JSON.stringify([record.sourceRootId, record.conversationId, record.executionId, record.requestedModelId, record.callMetadata.modelDisplayName,
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
    requestState: null, safeErrorCode: null, modelElapsedMs: null,
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

/** Known subtotal and missing-member count are presentation facts, never settlement values. */
export function usageTokenSummary(record, key) {
  const values = usageRecordPhysicalFacts(record).map(item => item[key]);
  const known = values.filter(value => Number.isSafeInteger(value) && value >= 0);
  let total = 0;
  for (const value of known) {
    if (total > Number.MAX_SAFE_INTEGER - value) return { total: null, knownCount: known.length, unknownCount: values.length - known.length };
    total += value;
  }
  return { total: known.length ? total : null, knownCount: known.length, unknownCount: values.length - known.length };
}

/** Model identities are independent persisted facts, not current settings or pricing routes. */
export function usageModelIdentityLabel(record) {
  const validId = value => typeof value === 'string' && value.trim() && value.trim() !== 'null'
    && !/\p{Cc}/u.test(value) && new TextEncoder().encode(value).length <= 200 ? value.trim() : null;
  const physical = usageRecordPhysicalFacts(record);
  const requested = new Map(), returned = new Map();
  let different = 0;
  for (const call of physical) {
    const request = validId(call.requestedModelId), actual = validId(call.actualModelId);
    requested.set(request, (requested.get(request) || 0) + 1);
    returned.set(actual, (returned.get(actual) || 0) + 1);
    if (request && actual && actual !== 'inconsistent-response-model' && actual !== request) different++;
  }
  const multiple = physical.length > 1;
  const labels = (values, label) => [...values].map(([id, count]) => label(id) + (multiple ? `（${count}次）` : '')).join('、');
  const requestLabel = labels(requested, id => id ?? '未记录') || '未记录';
  const returnedLabel = labels(returned, id => id === 'inconsistent-response-model' ? '响应内型号不一致' : id ?? '未提供') || '未提供';
  const difference = different ? (multiple ? `（${different}次与请求不同）` : '（与请求不同）') : '';
  return `请求型号：${requestLabel} · 返回型号：${returnedLabel}${difference}`;
}


/** Original request routing, independent of the provider-reported returned model. */
export function usageCallRouteLabel(record) {
  const providers = {OPENROUTER:'OpenRouter',QWEN:'通义千问',DEEPSEEK:'DeepSeek',ZHIPU:'智谱',RELAYCORE_CLAUDE:'RelayCore Claude',RELAYCORE_GPT:'RelayCore GPT',RELAYCORE_GEMINI:'RelayCore Gemini'};
  const channels = {KIRO:['RELAYCORE_CLAUDE','Kiro'],KIRO_STANDARD:['RELAYCORE_CLAUDE','Kiro正价'],PRO1:['RELAYCORE_GPT','Pro1'],PRO2:['RELAYCORE_GPT','Pro2'],PRO3:['RELAYCORE_GPT','Pro3'],GEMINI:['RELAYCORE_GEMINI','Gemini']};
  const label = route => {
    if (!route || !Object.hasOwn(providers,route.providerId)) return '未记录';
    const provider = providers[route.providerId];
    if (route.channelId == null) return provider + (route.providerId.startsWith('RELAYCORE_') ? ' · 渠道未记录' : '');
    const channel = Object.hasOwn(channels,route.channelId) ? channels[route.channelId] : null;
    return channel?.[0] === route.providerId ? `${provider} · ${channel[1]}` : '未记录';
  };
  const physical = usageRecordPhysicalFacts(record), values = new Map();
  for (const call of physical) {
    const value=label(call.callMetadata?.route);values.set(value,(values.get(value)||0)+1);
  }
  return '请求线路：' + ([...values].map(([value,count])=>value+(physical.length>1?`（${count}次）`:'')).join('、') || '未记录');
}
