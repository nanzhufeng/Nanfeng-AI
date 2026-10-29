import { RELAYCORE_MODELS } from './relaycore-models.mjs';
// Read-only Desktop projection of the Android ConversationCostEstimator. Provider-reported
// amounts remain authoritative; this table only fills a visible estimate when that fact is absent.
export const USD_TO_CNY = 6.720309145556033;
export const CNY_REFERENCE_DATE = '2026-08-27';

const price = (priceVersion, input, output, cachedInput = null, currencyCode = 'USD') => ({
  priceVersion, input: Number(input), output: Number(output), cachedInput: cachedInput == null ? null : Number(cachedInput), currencyCode,
});

const flatPrices = new Map([
  ['anthropic/claude-opus-5.5', price('openrouter-public-prices-2026-10-09-v1', 4, 20, 0.2)],
  ['anthropic/claude-sonnet-5.5', price('openrouter-public-prices-2026-10-09-v1', 2, 10, 0.1)],
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
  ['anthropic/claude-haiku-5.5', [[99_999, price('openrouter-public-prices-2026-10-09-v1', 0.1, 0.5, 0.01)], [Number.MAX_SAFE_INTEGER, price('openrouter-public-prices-2026-10-09-v1', 0.5, 2.5, 0.05)]]],
  ['openai/gpt-6.1-sol', [[271_999, price('openrouter-public-prices-2026-10-09-v1', 2, 10, 0.1)], [Number.MAX_SAFE_INTEGER, price('openrouter-public-prices-2026-10-09-v1', 4, 15, 0.2)]]],
  ['openai/gpt-6-luna', [[271_999, price('openrouter-public-prices-2026-10-09-v1', 0.1, 0.5, 0.01)], [Number.MAX_SAFE_INTEGER, price('openrouter-public-prices-2026-10-09-v1', 0.2, 0.75, 0.02)]]],
  ['openai/gpt-5.6-sol', [[271_999, price('openrouter-public-prices-2026-08-v1', 2, 10)], [Number.MAX_SAFE_INTEGER, price('openrouter-public-prices-2026-08-v1', 4, 15)]]],
  ['openai/gpt-5.6-terra', [[271_999, price('openrouter-public-prices-2026-08-v1', 2, 12)], [Number.MAX_SAFE_INTEGER, price('openrouter-public-prices-2026-08-v1', 4, 18)]]],
  ['openai/gpt-5.6-luna', [[271_999, price('openrouter-public-prices-2026-08-v1', 0.2, 1.2)], [Number.MAX_SAFE_INTEGER, price('openrouter-public-prices-2026-08-v1', 0.4, 1.8)]]],
  ['x-ai/grok-4.6', [[199_999, price('openrouter-public-prices-2026-08-v1', 2, 6, 0.5)], [Number.MAX_SAFE_INTEGER, price('openrouter-public-prices-2026-08-v1', 4, 12, 1)]]],
  ['qwen3.7-plus', [[256_000, price('qwen-cn-beijing-standard-2026-08-v2', 0.276, 1.101, null, 'USD')], [1_000_000, price('qwen-cn-beijing-standard-2026-08-v2', 0.826, 3.301, null, 'USD')]]],
  ['qwen3.6-flash', [[256_000, price('qwen-cn-beijing-standard-2026-08-v2', 0.165, 0.99, null, 'USD')], [1_000_000, price('qwen-cn-beijing-standard-2026-08-v2', 0.66, 3.961, null, 'USD')]]],
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

function relayPrice(modelId, displayName, occurredAtMs) {
  const model=RELAYCORE_MODELS.find(item=>item.modelId===modelId);
  if (!model || (!displayName && occurredAtMs < Date.UTC(2026,9,10))) return null;
  const channel=displayName?.split(' · ')[1] || model.channel;
  const multiplier={Kiro:0.08,'Kiro正价':0.35,Pro1:0.12,Pro2:0.18,Pro3:0.25,Gemini:0.28}[channel];
  if (!multiplier) return null;
  const factor=multiplier/Number(model.multiplier);
  return price(`relaycore-cny-nominal-user-confirmed-2026-10-10-${channel}`,model.inputPerMillion*factor,model.outputPerMillion*factor,model.cachedInputPerMillion*factor,'CNY');
}
function currentQwenPrice(modelId,input,at) {
  if (at<Date.UTC(2026,9,10) || input>1000000) return null;
  const long=input>256000;
  if (modelId==='qwen3.7-plus') return price('qwen-cn-beijing-published-standard-2026-10-10',long?6:2,long?24:8,long?1.2:0.4,'CNY');
  if (modelId==='qwen3.6-flash') return price('qwen-cn-beijing-published-standard-2026-10-10',long?4.8:1.2,long?28.8:7.2,long?0.96:0.24,'CNY');
  if (modelId==='qwen3.8-max') return price('qwen-cn-beijing-published-standard-2026-10-10',12,36,2.4,'CNY');
  return null;
}

export function accountingPricingModelId(requested,actual) {
  if (!actual) return requested;
  return !actual.includes('/') && requested?.includes('/') ? `${requested.split('/')[0]}/${actual}` : actual;
}

/** Returns an immutable display projection; it never writes an estimate into the usage ledger. */
export function projectedCost({ modelId, inputTokens, outputTokens, cachedInputTokens = 0, occurredAtMs, chargeMicros, currencyCode, costSource, modelDisplayName, priceVersion } = {}) {
  if (Number.isSafeInteger(chargeMicros) && chargeMicros >= 0 && currencyCode) {
    // Stored amounts/currencies remain immutable facts, including historical estimates.
    return { chargeMicros, currencyCode, costSource: costSource === 'LOCAL_ESTIMATE' ? 'LOCAL_ESTIMATE' : 'PROVIDER_RESPONSE' };
  }
  if (!Number.isSafeInteger(inputTokens) || inputTokens < 0 || !Number.isSafeInteger(outputTokens) || outputTokens < 0 || !Number.isSafeInteger(occurredAtMs) || occurredAtMs < 0) return null;
  const normalized = String(modelId || '').trim().toLocaleLowerCase();
  const selected = relayPrice(normalized,modelDisplayName,occurredAtMs)
    || currentQwenPrice(normalized,inputTokens,occurredAtMs)
    || deepSeekPrice(normalized, occurredAtMs)
    || flatPrices.get(normalized)
    || tieredPrices.get(normalized)?.find(([upper]) => inputTokens <= upper)?.[1]
    || scheduledPrice(normalized, occurredAtMs);
  if (!selected) return null;
  const cached = Math.min(inputTokens, Math.max(0, Number.isSafeInteger(cachedInputTokens) ? cachedInputTokens : 0));
  const charge = Math.round((inputTokens - cached) * selected.input + cached * (selected.cachedInput ?? selected.input) + outputTokens * selected.output);
  return { chargeMicros: charge, currencyCode: selected.currencyCode, costSource: 'LOCAL_ESTIMATE', priceVersion: selected.priceVersion };
}

export function projectedMessageCost(message) {
  if (message?.accountingCalls?.length) {
    const calls=message.accountingCalls;
    const costs=calls.map(call=>projectedCost({...call,modelId:accountingPricingModelId(call.requestedModelId,call.actualModelId)}));
    if (calls.some(call=>call.status==='STREAM_PENDING') || costs.some(cost=>!cost) || new Set(costs.map(cost=>cost.currencyCode)).size!==1) return null;
    const chargeMicros=costs.reduce((sum,cost)=>sum+cost.chargeMicros,0);
    if (!Number.isSafeInteger(chargeMicros)) return null;
    return {chargeMicros,currencyCode:costs[0].currencyCode,costSource:costs.some(cost=>cost.costSource==='LOCAL_ESTIMATE')?'LOCAL_ESTIMATE':'PROVIDER_RESPONSE',priceVersion:'combined-call-accounting-v1'};
  }
  if (message?.retrievalModelId) return null; // Historical helper calls with no quantitative facts remain unknown.
  const usage = message?.usage || {};
  const estimatedUsage = message?.estimatedUsage || {};
  const effectiveUsage = Number.isSafeInteger(usage.inputTokens) && Number.isSafeInteger(usage.outputTokens) ? usage : estimatedUsage;
  const occurredAtMs = Date.parse(message?.createdAt || '');
  return projectedCost({
    modelId: accountingPricingModelId(message?.modelSnapshot?.modelId,message?.actualModelId),
    inputTokens: effectiveUsage.inputTokens,
    outputTokens: effectiveUsage.outputTokens,
    cachedInputTokens: effectiveUsage.cachedInputTokens,
    occurredAtMs,
    chargeMicros: message?.chargeMicros,
    currencyCode: message?.currencyCode,
    modelDisplayName: message?.modelSnapshot?.displayName,
    costSource: message?.costSource,
    priceVersion: message?.priceVersion,
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

/** Footer-only RMB projection. Physical calls keep their original settlement currency. */
export function messageFooterCostLabel(message, { maximumFractionDigits = 4, trimTrailingZeros = false } = {}) {
  if (!message?.accountingCalls?.length) return cnyCostLabel(projectedMessageCost(message), { maximumFractionDigits, trimTrailingZeros });
  const calls = [...new Map(message.accountingCalls.map((call, index) => [call.callId || `legacy-${index}`, call])).values()];
  let amount = 0, known = 0, unknown = false;
  for (const call of calls) {
    const cost = projectedCost({ ...call, modelId: accountingPricingModelId(call.requestedModelId, call.actualModelId) });
    const rate = cost?.currencyCode === 'CNY' ? 1 : cost?.currencyCode === 'USD' ? USD_TO_CNY : null;
    if (!cost || rate == null) { unknown = true; continue; }
    amount += cost.chargeMicros * rate / 1_000_000;
    known++;
    if (['STREAM_PENDING', 'RELAY_WEB_RETRIEVAL_PENDING'].includes(call.status)) unknown = true;
  }
  if (!known || !Number.isFinite(amount)) return null;
  const text = trimTrailingZeros ? amount.toFixed(maximumFractionDigits).replace(/0+$/, '').replace(/\.$/, '') : amount.toFixed(maximumFractionDigits);
  return `¥${text}${unknown ? ' + 未知' : ''}`;
}
