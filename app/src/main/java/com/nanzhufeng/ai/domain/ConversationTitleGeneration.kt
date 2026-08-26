package com.nanzhufeng.ai.domain

import java.time.Instant
import java.util.UUID

@JvmInline
value class ConversationTitleGenerationId(val value: String) {
    companion object { fun new() = ConversationTitleGenerationId(UUID.randomUUID().toString()) }
}

enum class ConversationTitleGenerationStatus { SUCCEEDED, FAILED }

/** Content-free audit for the Qwen title refiner; source and generated title text are not retained. */
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

sealed interface ConversationTitleRefinementResult {
    data class Title(val value: String) : ConversationTitleRefinementResult
    data class Failed(val safeCode: String) : ConversationTitleRefinementResult
}

interface ConversationTitleRefiner {
    fun refine(sourceConversationId: ConversationId, source: ConversationTitleSource): ConversationTitleRefinementResult
}
