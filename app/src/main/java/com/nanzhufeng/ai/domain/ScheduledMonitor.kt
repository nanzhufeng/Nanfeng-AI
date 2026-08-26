package com.nanzhufeng.ai.domain

import java.time.Instant
import java.util.UUID

@JvmInline
value class ScheduledMonitorTaskId(val value: String) {
    companion object { fun new() = ScheduledMonitorTaskId(UUID.randomUUID().toString()) }
}

@JvmInline
value class ScheduledMonitorRunId(val value: String) {
    companion object { fun new() = ScheduledMonitorRunId(UUID.randomUUID().toString()) }
}

enum class ScheduledMonitorCadence(val label: String, val intervalMillis: Long) {
    HOURLY("每小时", 60L * 60L * 1_000L),
    DAILY("每天", 24L * 60L * 60L * 1_000L),
}

enum class ScheduledMonitorStatus { ACTIVE, PAUSED }
enum class ScheduledMonitorRunStatus { RUNNING, SUCCEEDED, FAILED, CANCELLED }

/**
 * A user-authorized recurring web-monitoring instruction. This is intentionally independent
 * from a conversation's message tree: only the explicit instruction is eligible for egress.
 */
data class ScheduledMonitorTask(
    val id: ScheduledMonitorTaskId,
    val title: String,
    val instruction: String,
    val sourceConversationId: ConversationId?,
    val cadence: ScheduledMonitorCadence,
    val modelPresetId: ModelPresetId,
    val status: ScheduledMonitorStatus,
    val nextRunAt: Instant,
    val lastRunAt: Instant? = null,
    val latestResult: String? = null,
    val lastProviderId: ProviderId? = null,
    val lastModelId: String? = null,
    val lastInputTokens: Long? = null,
    val lastOutputTokens: Long? = null,
    val lastSafeErrorCode: String? = null,
    val createdAt: Instant,
    val updatedAt: Instant,
)

data class ScheduledMonitorRun(
    val id: ScheduledMonitorRunId,
    val taskId: ScheduledMonitorTaskId,
    val scheduledAt: Instant,
    val startedAt: Instant,
    val completedAt: Instant? = null,
    val status: ScheduledMonitorRunStatus,
    val safeErrorCode: String? = null,
    val result: String? = null,
    val providerId: ProviderId? = null,
    val modelId: String? = null,
    val inputTokens: Long? = null,
    val outputTokens: Long? = null,
)

interface ScheduledMonitorRepository {
    fun list(): List<ScheduledMonitorTask>
    fun find(taskId: ScheduledMonitorTaskId): ScheduledMonitorTask?
    fun create(task: ScheduledMonitorTask): ScheduledMonitorTask
    fun setStatus(taskId: ScheduledMonitorTaskId, status: ScheduledMonitorStatus, updatedAt: Instant): ScheduledMonitorTask?
    fun delete(taskId: ScheduledMonitorTaskId): Boolean
    fun startRun(run: ScheduledMonitorRun): Boolean
    fun finishRun(task: ScheduledMonitorTask, run: ScheduledMonitorRun): Boolean
    fun recentRuns(taskId: ScheduledMonitorTaskId, limit: Int = 20): List<ScheduledMonitorRun>
}
