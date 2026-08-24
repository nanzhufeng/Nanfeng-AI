package com.nanzhufeng.ai.data.local

import com.nanzhufeng.ai.domain.AttachmentId
import com.nanzhufeng.ai.domain.NormalChatSendAttemptId
import com.nanzhufeng.ai.domain.ProviderId
import com.nanzhufeng.ai.domain.ResumableAttachmentUpload
import com.nanzhufeng.ai.domain.ResumableAttachmentUploadId
import com.nanzhufeng.ai.domain.ResumableAttachmentUploadStatus
import com.nanzhufeng.ai.domain.ResumableAttachmentUploadStore
import java.time.Instant
import java.util.concurrent.Callable

/** Room implementation deliberately never persists gateway URLs, headers, tokens, paths or bytes. */
class RoomResumableAttachmentUploadStore(private val database: NanfengAiDatabase) : ResumableAttachmentUploadStore {
    override fun create(upload: ResumableAttachmentUpload): ResumableAttachmentUpload = database.runInTransaction(Callable {
        val dao = database.resumableAttachmentUploadDao()
        dao.insert(upload.toEntity())
        requireNotNull(dao.find(upload.uploadId.value)).toDomain()
    })

    override fun find(uploadId: ResumableAttachmentUploadId): ResumableAttachmentUpload? =
        database.resumableAttachmentUploadDao().find(uploadId.value)?.toDomain()

    override fun findForAttemptAndAttachment(attemptId: NormalChatSendAttemptId, attachmentId: AttachmentId): ResumableAttachmentUpload? =
        database.resumableAttachmentUploadDao().findForAttemptAndAttachment(attemptId.value, attachmentId.value)?.toDomain()

    override fun transition(
        id: ResumableAttachmentUploadId,
        expected: Set<ResumableAttachmentUploadStatus>,
        next: ResumableAttachmentUploadStatus,
        gatewaySessionId: String?,
        acknowledgedBytes: Long,
        updatedAt: Instant,
        safeErrorCode: String?,
    ): ResumableAttachmentUpload? = database.runInTransaction(Callable {
        val dao = database.resumableAttachmentUploadDao()
        if (dao.transition(id.value, expected.map { it.name }, next.name, gatewaySessionId, acknowledgedBytes, updatedAt.toEpochMilli(), safeErrorCode) != 1) return@Callable null
        dao.find(id.value)?.toDomain()
    })

    override fun markInterruptedAsUnknown(updatedAt: Instant): Int =
        database.resumableAttachmentUploadDao().markInterruptedAsUnknown(updatedAt.toEpochMilli())
}

private fun ResumableAttachmentUpload.toEntity() = ResumableAttachmentUploadEntity(
    uploadId.value, normalChatAttemptId.value, attachmentId.value, providerId.name, modelId, gatewayId,
    sha256, byteCount, gatewaySessionId, acknowledgedBytes, status.name, createdAt.toEpochMilli(),
    updatedAt.toEpochMilli(), safeErrorCode,
)

private fun ResumableAttachmentUploadEntity.toDomain() = ResumableAttachmentUpload(
    ResumableAttachmentUploadId(uploadId), NormalChatSendAttemptId(normalChatAttemptId), AttachmentId(attachmentId),
    ProviderId.valueOf(providerId), modelId, gatewayId, sha256, byteCount, gatewaySessionId,
    acknowledgedBytes, ResumableAttachmentUploadStatus.valueOf(status), Instant.ofEpochMilli(createdAtEpochMs),
    Instant.ofEpochMilli(updatedAtEpochMs), safeErrorCode,
)
