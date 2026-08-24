package com.nanzhufeng.ai.domain

import java.time.Instant

data class ContextSelectionSource(val kind: String, val stableId: String, val title: String, val estimatedTokens: Int)

data class ContextSelectionAuditRecord(
    val createdAt: Instant,
    val providerId: ProviderId,
    val modelId: String,
    val tokenizerId: String,
    val budget: ContextBudget,
    val selectedSources: List<ContextSelectionSource>,
    val indexStatus: LocalContextBroker.AssemblyStatus = LocalContextBroker.AssemblyStatus.READY,
)

/** Local debugging only. Bodies, prompts, attachments, responses and credentials are forbidden. */
interface ContextSelectionAuditStore {
    fun append(record: ContextSelectionAuditRecord)
    fun recent(limit: Int = 8): List<ContextSelectionAuditRecord>
}
