/** P6-G pure local router. It never reads credentials, invokes a Provider, or persists state. */
export function routeP6G(request, catalog) {
  if (request.exactHistoricalCacheHit) return decision('EXACT_HISTORICAL_CACHE', 'EXACT_CACHE_HIT');
  if (request.localSafeRequired) return decision('LOCAL_SAFE', 'LOCAL_SAFETY_GATE');
  const rejectedCandidates = catalog.flatMap(candidate => {
    if (!candidate.available) return [{ modelId: candidate.modelId, reason: 'UNAVAILABLE' }];
    if (!(request.requiredCapabilities || ['TEXT']).every(capability => candidate.capabilities.includes(capability))) return [{ modelId: candidate.modelId, reason: 'CAPABILITY' }];
    if (request.contextTokens != null && (candidate.contextWindowTokens == null || candidate.contextWindowTokens < request.contextTokens)) return [{ modelId: candidate.modelId, reason: 'CONTEXT_LIMIT' }];
    if (request.budgetMicros != null && candidate.knownCostMicros != null && candidate.knownCostMicros > request.budgetMicros) return [{ modelId: candidate.modelId, reason: 'BUDGET' }];
    return [];
  });
  const capable = catalog.filter(candidate => candidate.available && (request.requiredCapabilities || ['TEXT']).every(capability => candidate.capabilities.includes(capability)) && (request.contextTokens == null || candidate.contextWindowTokens != null && candidate.contextWindowTokens >= request.contextTokens) && (request.budgetMicros == null || candidate.knownCostMicros == null || candidate.knownCostMicros <= request.budgetMicros));
  if (request.manualModelId) {
    const manual = capable.find(candidate => candidate.modelId === request.manualModelId);
    return manual ? decision('MANUAL_OVERRIDE', 'MANUAL_OVERRIDE', manual) : decision('REJECTED', 'NO_ELIGIBLE_CANDIDATE', null, catalog.map(candidate => candidate.modelId), rejectedCandidates);
  }
  const tiered = capable.filter(candidate => candidate.tiers.includes(request.tier));
  if (!tiered.length) return decision('REJECTED', 'NO_ELIGIBLE_CANDIDATE', null, catalog.map(candidate => candidate.modelId), rejectedCandidates);
  const knownCost = tiered.filter(candidate => candidate.knownCostMicros != null);
  if (!knownCost.length && !request.unknownCostConfirmed) return decision('REQUIRES_CONFIRMATION', 'UNKNOWN_COST_REQUIRES_CONFIRMATION', null, tiered.map(candidate => candidate.modelId), rejectedCandidates);
  const eligible = knownCost.length ? knownCost : tiered;
  const preferred = eligible.filter(candidate => candidate.providerFamily === 'ANTHROPIC');
  const selected = (preferred.length ? preferred : eligible).slice().sort((left, right) =>
    (left.knownCostMicros ?? Number.MAX_SAFE_INTEGER) - (right.knownCostMicros ?? Number.MAX_SAFE_INTEGER) || left.latencyRank - right.latencyRank || left.modelId.localeCompare(right.modelId),
  )[0];
  return decision('AUTO', selected.providerFamily === 'ANTHROPIC' ? 'AUTO_ANTHROPIC_PREFERRED' : 'AUTO_EXPLICIT_FALLBACK', selected);
}
function decision(source, reason, candidate = null, rejectedModelIds = [], rejectedCandidates = []) { return { source, reason, candidate, rejectedModelIds, rejectedCandidates }; }
