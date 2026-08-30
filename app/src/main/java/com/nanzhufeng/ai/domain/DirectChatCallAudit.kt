package com.nanzhufeng.ai.domain

import java.time.Instant

const val HISTORY_CURATION_AUDIT_ALIAS = "历史资料整理"

/** Content-free local audit required for every ordinary provider attempt. */
data class DirectChatCallAuditRecord(
    val providerId: ProviderId,
    val endpoint: String,
    val modelId: String,
    val modelAlias: String,
    val reasoningLevel: String,
    val requestedAt: Instant,
    val inputTokens: Long?,
    val outputTokens: Long?,
    val status: String,
)

data class DirectChatCallAuditSummary(val recordCount: Int = 0, val storageBytes: Long = 0)

interface DirectChatCallAuditStore {
    fun append(record: DirectChatCallAuditRecord)
    fun summary(): DirectChatCallAuditSummary
    /** Bounded local audit, used only for the associated background task's usage ledger. */
    fun listNewestFirst(): List<DirectChatCallAuditRecord>
}
