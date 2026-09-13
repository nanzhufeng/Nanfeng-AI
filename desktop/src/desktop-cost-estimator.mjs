// Read-only Desktop projection of the Android ConversationCostEstimator. Provider-reported
// amounts remain authoritative; this table only fills a visible estimate when that fact is absent.
const USD_TO_CNY = 6.720309145556033;

const price = (priceVersion, input, output, cachedInput = null, currencyCode = 'USD') => ({
  priceVersion, input: Number(input), output: Number(output), cachedInput: cachedInput == null ? null : Number(cachedInput), currencyCode,
});

const flatPrices = new Map([
  ['anthropic/claude-fable-5', price('openrouter-public-prices-2026-08-v1', 10, 50)],
  ['anthropic/claude-opus-5', price('openrouter-public-prices-2026-08-v1', 5, 25)],
  ['anthropic/claude-opus-5:fast', price('openrouter-public-prices-2026-08-v1', 10, 50)],
  ['anthropic/claude-sonnet-5', price('openrouter-public-prices-2026-08-v1', 2, 10)],
  ['anthropic/claude-haiku-4.5', price('openrouter-public-prices-2026-08-v1', 1, 5)],
  ['google/gemini-3.7-flash', price('openrouter-public-prices-2026-08-v1', 0.375, 1.875)],
  ['google/gemini-3.8-flash', price('openrouter-gemini-3.8-flash-intro-2026-09-v1', 0.75, 3.75, 0.075)],
  ['moonshotai/kimi-k3', price('openrouter-public-prices-2026-08-v1', 2.55, 12.75, 0.256)],
  ['qwen3.8-max', price('qwen-cn-beijing-standard-2026-08-v2', 12, 36, 1.5, 'CNY')],
]);

const tieredPrices = new Map([
  ['openai/gpt-5.6-sol', [[271_999, price('openrouter-public-prices-2026-08-v1', 2, 10)], [Number.MAX_SAFE_INTEGER, price('openrouter-public-prices-2026-08-v1', 4, 15)]]],
  ['openai/gpt-5.6-terra', [[271_999, price('openrouter-public-prices-2026-08-v1', 2, 12)], [Number.MAX_SAFE_INTEGER, price('openrouter-public-prices-2026-08-v1', 4, 18)]]],
  ['openai/gpt-5.6-luna', [[271_999, price('openrouter-public-prices-2026-08-v1', 0.2, 1.2)], [Number.MAX_SAFE_INTEGER, price('openrouter-public-prices-2026-08-v1', 0.4, 1.8)]]],
  ['x-ai/grok-4.6', [[199_999, price('openrouter-public-prices-2026-08-v1', 2, 6, 0.5)], [Number.MAX_SAFE_INTEGER, price('openrouter-public-prices-2026-08-v1', 4, 12, 1)]]],
  ['qwen3.7-plus', [[256_000, price('qwen-cn-beijing-standard-2026-08-v2', 0.276, 1.101, null, 'CNY')], [1_000_000, price('qwen-cn-beijing-standard-2026-08-v2', 0.826, 3.301, null, 'CNY')]]],
  ['qwen3.6-flash', [[256_000, price('qwen-cn-beijing-standard-2026-08-v2', 0.165, 0.99, null, 'CNY')], [1_000_000, price('qwen-cn-beijing-standard-2026-08-v2', 0.66, 3.961, null, 'CNY')]]],
]);

function deepSeekPrice(modelId, occurredAtMs) {
  const at = new Date(occurredAtMs);
  if (Number.isNaN(at.valueOf())) return null;
  const weekday = at.getUTCDay() >= 1 && at.getUTCDay() <= 5;
  const hour = at.getUTCHours();
  const peak = weekday && (hour >= 1 && hour < 4 || hour >= 6 && hour < 10);
  if (modelId === 'deepseek-flash') return peak
    ? price('deepseek-v4.1-flash-peak-2026-09-10-v1', 0.3, 1.2, 0.006)
    : price('deepseek-v4.1-flash-off-peak-2026-09-10-v1', 0.15, 0.6, 0.003);
  if (modelId === 'deepseek-v4-flash' && occurredAtMs >= Date.UTC(2026, 8, 10)) return null;
  if (!['deepseek-v4-pro', 'deepseek-v4-flash'].includes(modelId)) return null;
  if (occurredAtMs < Date.UTC(2026, 7, 16, 16)) return modelId === 'deepseek-v4-pro'
    ? price('deepseek-public-prices-before-2026-08-17-v1', 0.435, 0.87, 0.003625)
    : price('deepseek-public-prices-before-2026-08-17-v1', 0.14, 0.28, 0.0028);
  return modelId === 'deepseek-v4-pro'
    ? (peak ? price('deepseek-peak-2026-08-v2', 1.32, 3.96, 0.044) : price('deepseek-off-peak-2026-08-v2', 0.66, 1.98, 0.022))
    : (peak ? price('deepseek-peak-2026-08-v2', 0.44, 1.32, 0.014) : price('deepseek-off-peak-2026-08-v2', 0.22, 0.66, 0.007));
}

function scheduledPrice(modelId, occurredAtMs) {
  if (modelId !== 'glm-5.3-flash') return null;
  return occurredAtMs < Date.UTC(2026, 7, 31, 16)
    ? price('zhipu-glm-5.3-flash-promo-2026-08-v1', 0.4, 1.4, 0.115, 'CNY')
    : price('zhipu-glm-5.3-flash-standard-2026-08-v1', 0.8, 2.8, 0.23, 'CNY');
}

/** Returns an immutable display projection; it never writes an estimate into the usage ledger. */
export function projectedCost({ modelId, inputTokens, outputTokens, cachedInputTokens = 0, occurredAtMs, chargeMicros, currencyCode, costSource } = {}) {
  if (Number.isSafeInteger(chargeMicros) && chargeMicros >= 0 && currencyCode) return { chargeMicros, currencyCode, costSource: costSource === 'LOCAL_ESTIMATE' ? 'LOCAL_ESTIMATE' : 'PROVIDER_RESPONSE' };
  if (!Number.isSafeInteger(inputTokens) || inputTokens < 0 || !Number.isSafeInteger(outputTokens) || outputTokens < 0 || !Number.isSafeInteger(occurredAtMs) || occurredAtMs < 0) return null;
  const normalized = String(modelId || '').trim().toLocaleLowerCase();
  const selected = deepSeekPrice(normalized, occurredAtMs)
    || flatPrices.get(normalized)
    || tieredPrices.get(normalized)?.find(([upper]) => inputTokens <= upper)?.[1]
    || scheduledPrice(normalized, occurredAtMs);
  if (!selected) return null;
  const cached = Math.min(inputTokens, Math.max(0, Number.isSafeInteger(cachedInputTokens) ? cachedInputTokens : 0));
  const charge = Math.round((inputTokens - cached) * selected.input + cached * (selected.cachedInput ?? selected.input) + outputTokens * selected.output);
  return { chargeMicros: charge, currencyCode: selected.currencyCode, costSource: 'LOCAL_ESTIMATE', priceVersion: selected.priceVersion };
}

export function projectedMessageCost(message) {
  const usage = message?.usage || {};
  const estimatedUsage = message?.estimatedUsage || {};
  const effectiveUsage = Number.isSafeInteger(usage.inputTokens) && Number.isSafeInteger(usage.outputTokens) ? usage : estimatedUsage;
  const occurredAtMs = Date.parse(message?.createdAt || '');
  return projectedCost({
    modelId: message?.actualModelId || message?.modelSnapshot?.modelId,
    inputTokens: effectiveUsage.inputTokens,
    outputTokens: effectiveUsage.outputTokens,
    cachedInputTokens: effectiveUsage.cachedInputTokens,
    occurredAtMs,
    chargeMicros: message?.chargeMicros,
    currencyCode: message?.currencyCode || 'USD',
    costSource: message?.costSource,
  });
}

export function cnyCostLabel(cost, { estimatedLabel = false, maximumFractionDigits = 4, trimTrailingZeros = true } = {}) {
  if (!cost || !Number.isSafeInteger(cost.chargeMicros)) return null;
  const rate = cost.currencyCode === 'CNY' ? 1 : cost.currencyCode === 'USD' ? USD_TO_CNY : null;
  if (rate == null) return null;
  const amount = cost.chargeMicros * rate / 1_000_000;
  const text = trimTrailingZeros ? amount.toFixed(maximumFractionDigits).replace(/0+$/, '').replace(/\.$/, '') : amount.toFixed(maximumFractionDigits);
  void estimatedLabel;
  return `¥${text}`;
}
