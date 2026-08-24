package com.nanzhufeng.ai.domain

import java.time.Instant

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
) {
    init {
        require(modelId.isNotBlank() && modelDisplayName.isNotBlank())
    }

    fun footerLabel(): String = if (receiverProviderId == providerId) {
        "模型：$modelDisplayName · ${providerId.footerName()}"
    } else {
        "模型：$modelDisplayName · ${receiverProviderId.footerName()}官方实时检索"
    }
}

interface AssistantResponseModelAttributionStore {
    /** Idempotent only for the same assistant-message/Attempt route; contradictory facts reject. */
    fun record(attribution: AssistantResponseModelAttribution)
    fun forMessages(messageIds: Collection<MessageNodeId>): Map<MessageNodeId, List<AssistantResponseModelAttribution>>
}

fun ProviderId.footerName(): String = when (this) {
    ProviderId.OPENROUTER -> "OpenRouter"
    ProviderId.QWEN -> "通义千问"
    ProviderId.DEEPSEEK -> "DeepSeek"
    ProviderId.MOCK -> "本地"
}
