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
    fun footerCostLabel(): String? = cost.totalMicros?.let { micros ->
        CnyMoneyDisplay.label(
            totalMicros = micros,
            currencyCode = cost.currencyCode,
            estimated = costSource == ConversationCostSource.LOCAL_ESTIMATE,
        )
    }

    private fun visibleModelName(): String = modelDisplayNameForUser(modelDisplayName)
}

/**
 * Projects legacy token-only records through the current versioned local price table.  It does
 * not write into storage: a later provider settlement must remain able to replace an unknown
 * historical amount, and a changed public price table must not silently mutate stored facts.
 */
fun AssistantResponseModelAttribution.withAvailableLocalCostEstimate(): AssistantResponseModelAttribution {
    if (cost.totalMicros != null) return this
    val estimate = ConversationCostEstimator.estimate(modelId, usage, recordedAt) ?: return this
    return copy(cost = estimate, costSource = ConversationCostSource.LOCAL_ESTIMATE)
}

interface AssistantResponseModelAttributionStore {
    /** Idempotent only for the same assistant-message/Attempt route; contradictory facts reject. */
    fun record(attribution: AssistantResponseModelAttribution)
    fun forMessages(messageIds: Collection<MessageNodeId>): Map<MessageNodeId, List<AssistantResponseModelAttribution>>
    /** Dedicated, content-free record list for Settings → AI 模型服务 → 费用与用量. */
    fun listCostedNewestFirst(): List<AssistantResponseModelAttribution>
}
