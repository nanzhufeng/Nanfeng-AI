package com.nanzhufeng.ai.data.local

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import com.nanzhufeng.ai.domain.ConversationId
import com.nanzhufeng.ai.domain.ModelPresetId
import com.nanzhufeng.ai.domain.ProviderId
import com.nanzhufeng.ai.domain.ScheduledMonitorCadence
import com.nanzhufeng.ai.domain.ScheduledMonitorRepository
import com.nanzhufeng.ai.domain.ScheduledMonitorRun
import com.nanzhufeng.ai.domain.ScheduledMonitorRunId
import com.nanzhufeng.ai.domain.ScheduledMonitorRunStatus
import com.nanzhufeng.ai.domain.ScheduledMonitorStatus
import com.nanzhufeng.ai.domain.ScheduledMonitorTask
import com.nanzhufeng.ai.domain.ScheduledMonitorTaskId
import java.time.Instant
import java.util.concurrent.Callable

@Entity(tableName = "scheduled_monitor_tasks", indices = [Index(value = ["status", "nextRunAtEpochMs"])])
data class ScheduledMonitorTaskEntity(
    @PrimaryKey val id: String,
    val title: String,
    val instruction: String,
    val sourceConversationId: String?,
    val cadence: String,
    val modelPresetId: String,
    val status: String,
    val nextRunAtEpochMs: Long,
    val lastRunAtEpochMs: Long?,
    val latestResult: String?,
    val lastProviderId: String?,
    val lastModelId: String?,
    val lastInputTokens: Long?,
    val lastOutputTokens: Long?,
    val lastSafeErrorCode: String?,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
)

@Entity(
    tableName = "scheduled_monitor_runs",
    foreignKeys = [ForeignKey(
        entity = ScheduledMonitorTaskEntity::class,
        parentColumns = ["id"],
        childColumns = ["taskId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index(value = ["taskId", "startedAtEpochMs"]), Index(value = ["status", "startedAtEpochMs"])],
)
data class ScheduledMonitorRunEntity(
    @PrimaryKey val id: String,
    val taskId: String,
    val scheduledAtEpochMs: Long,
    val startedAtEpochMs: Long,
    val completedAtEpochMs: Long?,
    val status: String,
    val safeErrorCode: String?,
    val result: String?,
    val providerId: String?,
    val modelId: String?,
    val inputTokens: Long?,
    val outputTokens: Long?,
)

@Dao
interface ScheduledMonitorDao {
    @Query("SELECT * FROM scheduled_monitor_tasks ORDER BY CASE status WHEN 'ACTIVE' THEN 0 ELSE 1 END, nextRunAtEpochMs ASC, createdAtEpochMs DESC")
    fun listTasks(): List<ScheduledMonitorTaskEntity>

    @Query("SELECT * FROM scheduled_monitor_tasks WHERE id = :taskId")
    fun findTask(taskId: String): ScheduledMonitorTaskEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    fun insertTask(task: ScheduledMonitorTaskEntity)

    @Query("UPDATE scheduled_monitor_tasks SET status = :status, updatedAtEpochMs = :updatedAtEpochMs WHERE id = :taskId")
    fun updateStatus(taskId: String, status: String, updatedAtEpochMs: Long): Int

    @Query("DELETE FROM scheduled_monitor_tasks WHERE id = :taskId")
    fun deleteTask(taskId: String): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    fun insertRun(run: ScheduledMonitorRunEntity)

    @Query("UPDATE scheduled_monitor_runs SET completedAtEpochMs = :completedAtEpochMs, status = :status, safeErrorCode = :safeErrorCode, result = :result, providerId = :providerId, modelId = :modelId, inputTokens = :inputTokens, outputTokens = :outputTokens WHERE id = :runId")
    fun finishRun(runId: String, completedAtEpochMs: Long, status: String, safeErrorCode: String?, result: String?, providerId: String?, modelId: String?, inputTokens: Long?, outputTokens: Long?): Int

    @Query("UPDATE scheduled_monitor_tasks SET nextRunAtEpochMs = :nextRunAtEpochMs, lastRunAtEpochMs = :lastRunAtEpochMs, latestResult = :latestResult, lastProviderId = :lastProviderId, lastModelId = :lastModelId, lastInputTokens = :lastInputTokens, lastOutputTokens = :lastOutputTokens, lastSafeErrorCode = :lastSafeErrorCode, updatedAtEpochMs = :updatedAtEpochMs WHERE id = :taskId")
    fun updateAfterRun(taskId: String, nextRunAtEpochMs: Long, lastRunAtEpochMs: Long, latestResult: String?, lastProviderId: String?, lastModelId: String?, lastInputTokens: Long?, lastOutputTokens: Long?, lastSafeErrorCode: String?, updatedAtEpochMs: Long): Int

    @Query("SELECT * FROM scheduled_monitor_runs WHERE taskId = :taskId ORDER BY startedAtEpochMs DESC LIMIT :limit")
    fun recentRuns(taskId: String, limit: Int): List<ScheduledMonitorRunEntity>
}

class RoomScheduledMonitorRepository(private val database: NanfengAiDatabase) : ScheduledMonitorRepository {
    private val dao get() = database.scheduledMonitorDao()
    override fun list() = dao.listTasks().map(ScheduledMonitorTaskEntity::toDomain)
    override fun find(taskId: ScheduledMonitorTaskId) = dao.findTask(taskId.value)?.toDomain()
    override fun create(task: ScheduledMonitorTask): ScheduledMonitorTask = database.runInTransaction(Callable {
        dao.insertTask(task.toEntity()); task
    })
    override fun setStatus(taskId: ScheduledMonitorTaskId, status: ScheduledMonitorStatus, updatedAt: Instant): ScheduledMonitorTask? = database.runInTransaction(Callable {
        if (dao.updateStatus(taskId.value, status.name, updatedAt.toEpochMilli()) == 0) null else dao.findTask(taskId.value)?.toDomain()
    })
    override fun delete(taskId: ScheduledMonitorTaskId): Boolean = database.runInTransaction(Callable {
        dao.deleteTask(taskId.value) > 0
    })
    override fun startRun(run: ScheduledMonitorRun): Boolean = runCatching {
        database.runInTransaction(Callable { dao.insertRun(run.toEntity()) }); true
    }.getOrDefault(false)
    override fun finishRun(task: ScheduledMonitorTask, run: ScheduledMonitorRun): Boolean = runCatching {
        require(run.completedAt != null)
        database.runInTransaction(Callable {
            val runUpdated = dao.finishRun(run.id.value, run.completedAt.toEpochMilli(), run.status.name, run.safeErrorCode, run.result, run.providerId?.name, run.modelId, run.inputTokens, run.outputTokens)
            val taskUpdated = dao.updateAfterRun(task.id.value, task.nextRunAt.toEpochMilli(), run.completedAt.toEpochMilli(), task.latestResult, task.lastProviderId?.name, task.lastModelId, task.lastInputTokens, task.lastOutputTokens, task.lastSafeErrorCode, task.updatedAt.toEpochMilli())
            check(runUpdated == 1 && taskUpdated == 1)
        }); true
    }.getOrDefault(false)
    override fun recentRuns(taskId: ScheduledMonitorTaskId, limit: Int) = dao.recentRuns(taskId.value, limit).map(ScheduledMonitorRunEntity::toDomain)
}

private fun ScheduledMonitorTask.toEntity() = ScheduledMonitorTaskEntity(id.value, title, instruction, sourceConversationId?.value, cadence.name, modelPresetId.name, status.name, nextRunAt.toEpochMilli(), lastRunAt?.toEpochMilli(), latestResult, lastProviderId?.name, lastModelId, lastInputTokens, lastOutputTokens, lastSafeErrorCode, createdAt.toEpochMilli(), updatedAt.toEpochMilli())
private fun ScheduledMonitorTaskEntity.toDomain() = ScheduledMonitorTask(ScheduledMonitorTaskId(id), title, instruction, sourceConversationId?.let(::ConversationId), ScheduledMonitorCadence.valueOf(cadence), ModelPresetId.valueOf(modelPresetId), ScheduledMonitorStatus.valueOf(status), Instant.ofEpochMilli(nextRunAtEpochMs), lastRunAtEpochMs?.let(Instant::ofEpochMilli), latestResult, lastProviderId?.let(ProviderId::valueOf), lastModelId, lastInputTokens, lastOutputTokens, lastSafeErrorCode, Instant.ofEpochMilli(createdAtEpochMs), Instant.ofEpochMilli(updatedAtEpochMs))
private fun ScheduledMonitorRun.toEntity() = ScheduledMonitorRunEntity(id.value, taskId.value, scheduledAt.toEpochMilli(), startedAt.toEpochMilli(), completedAt?.toEpochMilli(), status.name, safeErrorCode, result, providerId?.name, modelId, inputTokens, outputTokens)
private fun ScheduledMonitorRunEntity.toDomain() = ScheduledMonitorRun(ScheduledMonitorRunId(id), ScheduledMonitorTaskId(taskId), Instant.ofEpochMilli(scheduledAtEpochMs), Instant.ofEpochMilli(startedAtEpochMs), completedAtEpochMs?.let(Instant::ofEpochMilli), ScheduledMonitorRunStatus.valueOf(status), safeErrorCode, result, providerId?.let(ProviderId::valueOf), modelId, inputTokens, outputTokens)
