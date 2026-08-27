package com.nanzhufeng.ai.domain

import java.time.Instant
import java.util.UUID

@JvmInline
value class ConversationTitleGenerationId(val value: String) {
    companion object { fun new() = ConversationTitleGenerationId(UUID.randomUUID().toString()) }
}

enum class ConversationTitleGenerationStatus { SUCCEEDED, FAILED }

/** Content-free audit for the automatic title refiner; source and generated title text are not retained. */
data class ConversationTitleGenerationRecord(
    val id: ConversationTitleGenerationId,
    val sourceConversationId: ConversationId,
    val requestedAt: Instant,
    val status: ConversationTitleGenerationStatus,
    val providerId: ProviderId? = null,
    val modelId: String? = null,
    val usage: ProviderUsage = ProviderUsage(),
    val cost: ProviderCost = ProviderCost(),
    val costSource: ConversationCostSource? = null,
    val safeErrorCode: String? = null,
)

interface ConversationTitleGenerationRecordStore {
    fun record(value: ConversationTitleGenerationRecord)
    fun listNewestFirst(): List<ConversationTitleGenerationRecord>
}

data class ConversationTitleSource(val userText: String, val assistantText: String)

/**
 * The first complete text exchange is the only material eligible for an automatic title.
 * Callers may invoke this after any completed reply: an earlier temporary provider failure must
 * not consume the eligibility or make the neutral "新对话" label permanent.
 */
fun ConversationSnapshot.openingTitleSource(): ConversationTitleSource? {
    val openingUser = nodes.firstOrNull { it.role == MessageRole.USER && it.parentMessageId == null } ?: return null
    val openingAssistant = nodes.firstOrNull {
        it.role == MessageRole.ASSISTANT &&
            it.parentMessageId == openingUser.id &&
            it.deliveryState == MessageDeliveryState.COMPLETE
    } ?: return null
    val userText = openingUser.content.filterIsInstance<ContentBlock.Text>().joinToString(" ") { it.text }.trim()
    val assistantText = openingAssistant.content.filterIsInstance<ContentBlock.Text>().joinToString("\n") { it.text }.trim()
    return ConversationTitleSource(userText, assistantText).takeIf { it.assistantText.isNotBlank() && (it.userText.isNotBlank() || openingUser.content.any { block -> block is ContentBlock.Attachment }) }
}

sealed interface ConversationTitleRefinementResult {
    data class Title(val value: String) : ConversationTitleRefinementResult
    data class Failed(val safeCode: String) : ConversationTitleRefinementResult
}

interface ConversationTitleRefiner {
    fun refine(sourceConversationId: ConversationId, source: ConversationTitleSource): ConversationTitleRefinementResult
}
