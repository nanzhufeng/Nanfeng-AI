package com.nanzhufeng.ai.domain

import java.time.Instant

enum class HistoryKnowledgeAutoCurationTrigger {
    CONVERSATION_IDLE,
    SETTING_ENABLED,
    PERIODIC,
}

enum class HistoryKnowledgeAutoCurationRunStatus {
    RUNNING,
    DISABLED,
    NO_CANDIDATE,
    SAVED,
    FAILED,
    UNKNOWN,
}

data class HistoryKnowledgeAutoCurationRunRecord(
    val runId: String,
    val trigger: HistoryKnowledgeAutoCurationTrigger,
    val status: HistoryKnowledgeAutoCurationRunStatus,
    val reasonCode: String?,
    val retryable: Boolean,
    val startedAt: Instant,
    val completedAt: Instant?,
)

enum class HistoryKnowledgeAutoCurationBeginResult { STARTED, RETRY_STARTED, INTERRUPTED, ALREADY_FINISHED }

/** Content-free run heartbeat. It is intentionally separate from provider usage and cost records. */
interface HistoryKnowledgeAutoCurationRunStore {
    fun begin(runId: String, trigger: HistoryKnowledgeAutoCurationTrigger, startedAt: Instant): HistoryKnowledgeAutoCurationBeginResult
    fun finish(runId: String, status: HistoryKnowledgeAutoCurationRunStatus, reasonCode: String?, retryable: Boolean, completedAt: Instant)
    fun latest(): HistoryKnowledgeAutoCurationRunRecord?
}
