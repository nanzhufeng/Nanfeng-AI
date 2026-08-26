package com.nanzhufeng.ai.domain

import java.time.Instant
import java.math.BigDecimal
import java.math.RoundingMode

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
    /** Content-free response accounting. Unknown stays null; a verified zero remains zero. */
    val usage: ProviderUsage = ProviderUsage(),
    val cost: ProviderCost = ProviderCost(),
    val costSource: ConversationCostSource? = null,
) {
    init {
        require(modelId.isNotBlank() && modelDisplayName.isNotBlank())
        require((cost.totalMicros == null) == (costSource == null)) {
            "已知对话费用必须说明来源；未知费用不得伪造来源。"
        }
    }

    /** The footer is a model-name surface, not a Provider route ledger. */
    fun footerLabel(): String = visibleModelName()

    /** Returns no label for an unknown amount; zero is a legitimate billed amount. */
    fun footerCostLabel(): String? = cost.totalMicros?.let { micros ->
        val amount = "\$${micros.toUsdText()}"
        if (costSource == ConversationCostSource.LOCAL_ESTIMATE) "≈ $amount（估算）" else amount
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
    val estimate = ConversationCostEstimator.estimate(modelId, usage) ?: return this
    return copy(cost = estimate, costSource = ConversationCostSource.LOCAL_ESTIMATE)
}

private fun Long.toUsdText(): String = BigDecimal.valueOf(this)
    .movePointLeft(6)
    .setScale(6, RoundingMode.UNNECESSARY)
    .stripTrailingZeros()
    .toPlainString()

interface AssistantResponseModelAttributionStore {
    /** Idempotent only for the same assistant-message/Attempt route; contradictory facts reject. */
    fun record(attribution: AssistantResponseModelAttribution)
    fun forMessages(messageIds: Collection<MessageNodeId>): Map<MessageNodeId, List<AssistantResponseModelAttribution>>
    /** Dedicated, content-free record list for Settings → AI 模型服务 → 费用与用量. */
    fun listCostedNewestFirst(): List<AssistantResponseModelAttribution>
}
