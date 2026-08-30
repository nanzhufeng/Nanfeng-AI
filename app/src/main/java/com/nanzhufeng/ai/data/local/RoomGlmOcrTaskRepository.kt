package com.nanzhufeng.ai.data.local

import com.nanzhufeng.ai.domain.AttachmentId
import com.nanzhufeng.ai.domain.GlmOcrTask
import com.nanzhufeng.ai.domain.GlmOcrTaskId
import com.nanzhufeng.ai.domain.GlmOcrTaskRepository
import com.nanzhufeng.ai.domain.GlmOcrTaskStatus
import java.time.Instant

class RoomGlmOcrTaskRepository(private val database: NanfengAiDatabase) : GlmOcrTaskRepository {
    override fun save(task: GlmOcrTask): GlmOcrTask {
        database.glmOcrTaskDao().upsert(task.toEntity())
        return requireNotNull(database.glmOcrTaskDao().find(task.id.value)).toDomain()
    }

    override fun find(id: GlmOcrTaskId): GlmOcrTask? = database.glmOcrTaskDao().find(id.value)?.toDomain()

    override fun listNewestFirst(): List<GlmOcrTask> = database.glmOcrTaskDao().listNewestFirst().map(GlmOcrTaskEntity::toDomain)

    override fun delete(id: GlmOcrTaskId): Boolean = database.glmOcrTaskDao().delete(id.value) == 1
}

private fun GlmOcrTask.toEntity() = GlmOcrTaskEntity(
    taskId = id.value,
    sourceAttachmentId = sourceAttachmentId.value,
    resultAttachmentId = resultAttachmentId?.value,
    sourceDisplayName = sourceDisplayName,
    sourceMimeType = sourceMimeType,
    sourceByteCount = sourceByteCount,
    sourceSha256 = sourceSha256,
    status = status.name,
    requestId = requestId,
    pageCount = pageCount,
    inputTokens = inputTokens,
    outputTokens = outputTokens,
    costCnyMicros = costCnyMicros,
    safeErrorCode = safeErrorCode,
    createdAtEpochMs = createdAt.toEpochMilli(),
    updatedAtEpochMs = updatedAt.toEpochMilli(),
)

private fun GlmOcrTaskEntity.toDomain() = GlmOcrTask(
    id = GlmOcrTaskId(taskId),
    sourceAttachmentId = AttachmentId(sourceAttachmentId),
    resultAttachmentId = resultAttachmentId?.let(::AttachmentId),
    sourceDisplayName = sourceDisplayName,
    sourceMimeType = sourceMimeType,
    sourceByteCount = sourceByteCount,
    sourceSha256 = sourceSha256,
    status = runCatching { GlmOcrTaskStatus.valueOf(status) }.getOrDefault(GlmOcrTaskStatus.FAILED),
    requestId = requestId,
    pageCount = pageCount,
    inputTokens = inputTokens,
    outputTokens = outputTokens,
    costCnyMicros = costCnyMicros,
    safeErrorCode = safeErrorCode,
    createdAt = Instant.ofEpochMilli(createdAtEpochMs),
    updatedAt = Instant.ofEpochMilli(updatedAtEpochMs),
)
