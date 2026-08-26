package com.nanzhufeng.ai.domain

import java.time.Instant
import java.util.UUID

@JvmInline
value class ReminderDraftGenerationId(val value: String) {
    companion object { fun new() = ReminderDraftGenerationId(UUID.randomUUID().toString()) }
}

enum class ReminderDraftGenerationStatus { SUCCEEDED, NOT_ELIGIBLE, FAILED }

/** Content-free audit for the Qwen-only reminder refiner; source text is never retained. */
data class ReminderDraftGenerationRecord(
    val id: ReminderDraftGenerationId,
    val sourceConversationId: ConversationId,
    val requestedAt: Instant,
    val status: ReminderDraftGenerationStatus,
    val providerId: ProviderId? = null,
    val modelId: String? = null,
    val usage: ProviderUsage = ProviderUsage(),
    val cost: ProviderCost = ProviderCost(),
    val costSource: ConversationCostSource? = null,
    val safeErrorCode: String? = null,
)

interface ReminderDraftGenerationRecordStore {
    fun record(value: ReminderDraftGenerationRecord)
    fun listNewestFirst(): List<ReminderDraftGenerationRecord>
}

sealed interface ReminderDraftRefinementResult {
    data class Draft(val suggestion: ScheduledMonitorSuggestion) : ReminderDraftRefinementResult
    data object NotEligible : ReminderDraftRefinementResult
    data class Failed(val safeCode: String) : ReminderDraftRefinementResult
}

interface ReminderDraftRefiner {
    fun refine(sourceConversationId: ConversationId, source: ScheduledMonitorDraftSource): ReminderDraftRefinementResult
}
