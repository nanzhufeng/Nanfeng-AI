package com.nanzhufeng.ai.domain

/**
 * P6-G's local routing contract.  This owner deliberately receives a reviewed catalog snapshot
 * from its caller; it has no credential, transport, persistence or invocation dependency.
 */
enum class P6GModelTier { FAST, BALANCED, DEEP, APEX_REVIEW }

enum class P6GProviderFamily { LOCAL, ANTHROPIC, OPENAI, OTHER }

enum class P6GCapability { TEXT, VISION, CODE, TOOL, STRUCTURED_OUTPUT, LONG_CONTEXT }

enum class P6GRouteSource { EXACT_HISTORICAL_CACHE, LOCAL_SAFE, MANUAL_OVERRIDE, AUTO, REQUIRES_CONFIRMATION, REJECTED }

enum class P6GRouteReason {
    EXACT_CACHE_HIT,
    LOCAL_SAFETY_GATE,
    MANUAL_OVERRIDE,
    AUTO_ANTHROPIC_PREFERRED,
    AUTO_EXPLICIT_FALLBACK,
    UNKNOWN_COST_REQUIRES_CONFIRMATION,
    NO_ELIGIBLE_CANDIDATE,
}

enum class P6GCandidateRejectionReason { UNAVAILABLE, CAPABILITY, CONTEXT_LIMIT, BUDGET }

data class P6GCandidateRejection(val modelId: String, val reason: P6GCandidateRejectionReason)

data class P6GCatalogCandidate(
    val providerFamily: P6GProviderFamily,
    val providerId: String,
    val modelId: String,
    val displayName: String,
    val tiers: Set<P6GModelTier>,
    val capabilities: Set<P6GCapability>,
    val available: Boolean,
    /** `null` is unknown and must never be treated as free. */
    val knownCostMicros: Long?,
    val latencyRank: Int,
    val contextWindowTokens: Long? = null,
) {
    init {
        require(providerId.isNotBlank() && modelId.isNotBlank() && displayName.isNotBlank())
        require(tiers.isNotEmpty())
        require(knownCostMicros == null || knownCostMicros >= 0)
        require(latencyRank >= 0)
    }
}

data class P6GRouteRequest(
    val tier: P6GModelTier,
    val requiredCapabilities: Set<P6GCapability> = setOf(P6GCapability.TEXT),
    val manualModelId: String? = null,
    val exactHistoricalCacheHit: Boolean = false,
    val localSafeRequired: Boolean = false,
    val unknownCostConfirmed: Boolean = false,
    val contextTokens: Long? = null,
    val budgetMicros: Long? = null,
)

data class P6GRouteDecision(
    val source: P6GRouteSource,
    val reason: P6GRouteReason,
    val candidate: P6GCatalogCandidate? = null,
    val rejectedModelIds: List<String> = emptyList(),
    val rejectedCandidates: List<P6GCandidateRejection> = emptyList(),
) {
    init {
        require((source == P6GRouteSource.AUTO || source == P6GRouteSource.MANUAL_OVERRIDE) == (candidate != null))
    }
}

class P6GModelRouter {
    fun route(request: P6GRouteRequest, catalog: List<P6GCatalogCandidate>): P6GRouteDecision {
        if (request.exactHistoricalCacheHit) return P6GRouteDecision(P6GRouteSource.EXACT_HISTORICAL_CACHE, P6GRouteReason.EXACT_CACHE_HIT)
        if (request.localSafeRequired) return P6GRouteDecision(P6GRouteSource.LOCAL_SAFE, P6GRouteReason.LOCAL_SAFETY_GATE)

        val rejections = catalog.mapNotNull { candidate -> when {
            !candidate.available -> P6GCandidateRejection(candidate.modelId, P6GCandidateRejectionReason.UNAVAILABLE)
            !request.requiredCapabilities.all(candidate.capabilities::contains) -> P6GCandidateRejection(candidate.modelId, P6GCandidateRejectionReason.CAPABILITY)
            request.contextTokens != null && (candidate.contextWindowTokens == null || candidate.contextWindowTokens < request.contextTokens) -> P6GCandidateRejection(candidate.modelId, P6GCandidateRejectionReason.CONTEXT_LIMIT)
            request.budgetMicros != null && candidate.knownCostMicros != null && candidate.knownCostMicros > request.budgetMicros -> P6GCandidateRejection(candidate.modelId, P6GCandidateRejectionReason.BUDGET)
            else -> null
        }}
        val capable = catalog.filter { candidate ->
            candidate.available && request.requiredCapabilities.all(candidate.capabilities::contains)
                && (request.contextTokens == null || candidate.contextWindowTokens != null && candidate.contextWindowTokens >= request.contextTokens)
                && (request.budgetMicros == null || candidate.knownCostMicros == null || candidate.knownCostMicros <= request.budgetMicros)
        }
        request.manualModelId?.let { manualId ->
            val manual = capable.firstOrNull { it.modelId == manualId }
            return manual?.decision(P6GRouteSource.MANUAL_OVERRIDE, P6GRouteReason.MANUAL_OVERRIDE)
                ?: P6GRouteDecision(P6GRouteSource.REJECTED, P6GRouteReason.NO_ELIGIBLE_CANDIDATE, rejectedModelIds = catalog.map(P6GCatalogCandidate::modelId), rejectedCandidates = rejections)
        }

        val tiered = capable.filter { request.tier in it.tiers }
        if (tiered.isEmpty()) return P6GRouteDecision(P6GRouteSource.REJECTED, P6GRouteReason.NO_ELIGIBLE_CANDIDATE, rejectedModelIds = catalog.map(P6GCatalogCandidate::modelId), rejectedCandidates = rejections)
        val knownCost = tiered.filter { it.knownCostMicros != null }
        if (knownCost.isEmpty() && !request.unknownCostConfirmed) {
            return P6GRouteDecision(P6GRouteSource.REQUIRES_CONFIRMATION, P6GRouteReason.UNKNOWN_COST_REQUIRES_CONFIRMATION, rejectedModelIds = tiered.map(P6GCatalogCandidate::modelId), rejectedCandidates = rejections)
        }
        val eligible = if (knownCost.isNotEmpty()) knownCost else tiered
        val preferred = eligible.filter { it.providerFamily == P6GProviderFamily.ANTHROPIC }
        val selected = (if (preferred.isNotEmpty()) preferred else eligible)
            .sortedWith(compareBy<P6GCatalogCandidate> { it.knownCostMicros ?: Long.MAX_VALUE }.thenBy { it.latencyRank }.thenBy { it.modelId })
            .first()
        return selected.decision(
            P6GRouteSource.AUTO,
            if (selected.providerFamily == P6GProviderFamily.ANTHROPIC) P6GRouteReason.AUTO_ANTHROPIC_PREFERRED else P6GRouteReason.AUTO_EXPLICIT_FALLBACK,
        )
    }

    private fun P6GCatalogCandidate.decision(source: P6GRouteSource, reason: P6GRouteReason) =
        P6GRouteDecision(source, reason, this)
}
