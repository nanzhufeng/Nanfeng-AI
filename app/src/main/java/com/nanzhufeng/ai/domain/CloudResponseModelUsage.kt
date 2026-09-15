package com.nanzhufeng.ai.domain

/**
 * Content-free model and settlement facts restored from a selected-conversation cloud document.
 *
 * The portable boundary deliberately omits provider routing and local attempt IDs, so it must
 * never be coerced into [AssistantResponseModelAttribution].  A local attribution remains the
 * richer truth whenever it exists; this type preserves the exact cross-device answer label and
 * amount when that local-only record is unavailable.
 */
data class CloudResponseModelUsage(
    val assistantMessageId: MessageNodeId,
    val modelId: String,
    val modelDisplayName: String,
    val usage: ProviderUsage = ProviderUsage(),
    val cost: ProviderCost = ProviderCost(),
    val costSource: ConversationCostSource? = null,
) {
    init {
        require(modelId.isNotBlank() && modelDisplayName.isNotBlank())
        require((cost.totalMicros == null) == (costSource == null)) {
            "已知跨端回答费用必须说明来源；未知费用不得伪造来源。"
        }
    }

    fun footerLabel(): String = modelDisplayNameForUser(modelDisplayName)

    fun footerCostLabel(): String? = cost.totalMicros?.let { micros ->
        CnyMoneyDisplay.label(
            totalMicros = micros,
            currencyCode = cost.currencyCode,
            estimated = costSource == ConversationCostSource.LOCAL_ESTIMATE,
        )
    }
}

/** Room owner for portable response facts; it never stores text, a route, credential, or attempt. */
interface CloudResponseModelUsageStore {
    fun forMessages(messageIds: Collection<MessageNodeId>): Map<MessageNodeId, List<CloudResponseModelUsage>>
}
