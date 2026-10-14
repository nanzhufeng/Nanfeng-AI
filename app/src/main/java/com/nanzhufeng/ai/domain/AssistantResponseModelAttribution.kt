package com.nanzhufeng.ai.domain

import java.time.Instant

/** The user must be able to distinguish a provider settlement from a local fallback estimate. */
enum class ConversationCostSource {
    PROVIDER_RESPONSE,
    LOCAL_ESTIMATE,
}

/**
 * Immutable, content-free provenance for one visible assistant response.  This deliberately
 * captures the resolved route at send time, so a later Composer selection or model-directory
 * refresh cannot rewrite what the user actually used.
 */
data class AssistantResponseModelAttribution(
    val assistantMessageId: MessageNodeId,
    val attemptId: NormalChatSendAttemptId,
    val providerId: ProviderId,
    /** The third party that actually receives this request; hosted routes may differ. */
    val receiverProviderId: ProviderId,
    val modelId: String,
    val modelDisplayName: String,
    val recordedAt: Instant,
    /** Exact style instruction used for this answer; null means a legacy record. */
    val conversationStyle: ConversationStyle? = null,
    /** Provider-owned source or completed search evidence; old false alone is ambiguous. */
    val webSearchUsed: Boolean? = null,
    /** Content-free response accounting. Unknown stays null; a verified zero remains zero. */
    val usage: ProviderUsage = ProviderUsage(),
    val cost: ProviderCost = ProviderCost(),
    val costSource: ConversationCostSource? = null,
    /**
     * List-only projection of the matching completed local send Attempt. It is intentionally
     * not persisted here: the Attempt remains the one owner of its start/end boundary.
     */
    val modelDurationMillis: Long? = null,
    /** Route selected for this exact request. Null means legacy evidence is insufficient. */
    val webSearchRequested: Boolean? = null,
    val accountingCalls: List<ChatCallAccounting> = emptyList(),
    /** Derived ledger-only row identity and state, never a stored message. */
    val physicalCallId: String? = null,
    val physicalCallRole: String? = null,
    val physicalCallStatus: String? = null,
    val physicalActualModelId: String? = null,
    val physicalContextWindowTokens: Long? = null,
    val physicalContextSourceCount: Int? = null,
) {
    init {
        require(modelId.isNotBlank() && modelDisplayName.isNotBlank())
        require((cost.totalMicros == null) == (costSource == null)) {
            "已知对话费用必须说明来源；未知费用不得伪造来源。"
        }
        require(modelDurationMillis == null || modelDurationMillis > 0L) { "模型耗时必须为正数或未知。" }
    }

    /** The footer is a model-name surface, not a Provider route ledger. */
    fun footerLabel(): String = visibleModelName()

    /** Returns no label for an unknown amount; zero is a legitimate billed amount. */
    fun footerCostLabel(): String? {
        if (accountingCalls.isEmpty()) return cost.withRelayNominalCurrency().let { displayed ->
            displayed.totalMicros?.let { CnyMoneyDisplay.label(it, displayed.currencyCode, costSource == ConversationCostSource.LOCAL_ESTIMATE) }
        }
        val calls = accountingCalls.distinctBy { it.callId }
        val costs = calls.map { it.cost.withRelayNominalCurrency() }
        val known = costs.filter { it.totalMicros != null && it.currencyCode in setOf("USD", "CNY") }
        // A display conversion is not a new settlement currency or a ledger mutation.
        val label = CnyMoneyDisplay.summaryLabel(known) ?: return null
        val incomplete = known.size != costs.size || calls.any { it.finishedAt == null }
        return label + if (incomplete) " + 未知" else ""
    }

    private fun visibleModelName(): String = modelDisplayNameForUser(modelDisplayName)
}

/**
 * Projects legacy token-only records through the current versioned local price table.  It does
 * not write into storage: a later provider settlement must remain able to replace an unknown
 * historical amount, and a changed public price table must not silently mutate stored facts.
 */
fun AssistantResponseModelAttribution.withAvailableLocalCostEstimate(): AssistantResponseModelAttribution {
    if (cost.totalMicros != null) {
        val corrected = cost.withRelayNominalCurrency()
        return if (corrected == cost) this else copy(cost = corrected)
    }
    // Legacy cooperative replies lack their retrieval charge; do not project a main-only amount as total.
    if (providerId in setOf(ProviderId.RELAYCORE_CLAUDE,ProviderId.RELAYCORE_GEMINI) && webSearchRequested == true) return this
    val estimate = ConversationCostEstimator.estimate(modelId, usage, recordedAt,modelDisplayName) ?: return this
    return copy(cost = estimate, costSource = ConversationCostSource.LOCAL_ESTIMATE)
}

interface AssistantResponseModelAttributionStore {
    /** Idempotent only for the same assistant-message/Attempt route; contradictory facts reject. */
    fun record(attribution: AssistantResponseModelAttribution)
    fun forMessages(messageIds: Collection<MessageNodeId>): Map<MessageNodeId, List<AssistantResponseModelAttribution>>
    /** Dedicated, content-free record list for Settings → AI 模型服务 → 费用与用量. */
    fun listCostedNewestFirst(): List<AssistantResponseModelAttribution>
}
