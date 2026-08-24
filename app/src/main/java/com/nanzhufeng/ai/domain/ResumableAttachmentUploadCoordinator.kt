package com.nanzhufeng.ai.domain

import java.io.InputStream
import java.time.Clock

/**
 * One explicit upload execution. It never creates a second ordinary-chat Attempt, chooses a
 * different Provider/model, or silently restarts from byte zero after an uncertain result.
 */
class ResumableAttachmentUploadCoordinator(
    private val uploads: ResumableAttachmentUploadStore,
    private val gateway: ResumableAttachmentGateway,
    private val clock: Clock,
) {
    sealed interface Outcome {
        data class Uploaded(val upload: ResumableAttachmentUpload, val reference: ResumableUploadRemoteReference) : Outcome
        data class Paused(val upload: ResumableAttachmentUpload) : Outcome
        data class Rejected(val safeErrorCode: String) : Outcome
    }

    /**
     * [source] must be the exact private file whose byte count/SHA were captured in [upload].
     * The caller has already established click-send authorization for this exact attachment.
     */
    fun continueUpload(
        upload: ResumableAttachmentUpload,
        mimeType: String,
        contentType: AttachmentEgressContentType,
        source: () -> InputStream,
    ): Outcome {
        val session = ensureSession(upload, mimeType, contentType) ?: return failed(upload, "UPLOAD_SESSION_REJECTED")
        if (session.acknowledgedBytes !in 0..upload.byteCount) return failed(upload, "UPLOAD_OFFSET_INVALID")
        val uploading = uploads.transition(
            upload.uploadId,
            setOf(ResumableAttachmentUploadStatus.PENDING, ResumableAttachmentUploadStatus.UNKNOWN, ResumableAttachmentUploadStatus.FAILED, ResumableAttachmentUploadStatus.UPLOADING),
            ResumableAttachmentUploadStatus.UPLOADING,
            session.gatewaySessionId,
            session.acknowledgedBytes,
            clock.instant(),
        ) ?: return Outcome.Paused(requireNotNull(uploads.find(upload.uploadId)))

        if (uploading.acknowledgedBytes < uploading.byteCount) {
            val append = source().use { input ->
                gateway.append(session.gatewaySessionId, uploading.acknowledgedBytes, input.afterExactly(uploading.acknowledgedBytes), uploading.byteCount - uploading.acknowledgedBytes)
            }
            when (append) {
                is ResumableUploadGatewayResult.Rejected -> return failed(uploading, append.safeErrorCode)
                is ResumableUploadGatewayResult.RemoteReference -> return failed(uploading, "UPLOAD_PROTOCOL_INVALID")
                is ResumableUploadGatewayResult.Session -> {
                    if (append.gatewaySessionId != session.gatewaySessionId || append.acknowledgedBytes !in uploading.acknowledgedBytes..uploading.byteCount) return failed(uploading, "UPLOAD_OFFSET_INVALID")
                    val advanced = uploads.transition(
                        uploading.uploadId, setOf(ResumableAttachmentUploadStatus.UPLOADING), ResumableAttachmentUploadStatus.UPLOADING,
                        append.gatewaySessionId, append.acknowledgedBytes, clock.instant(),
                    ) ?: return Outcome.Paused(requireNotNull(uploads.find(uploading.uploadId)))
                    if (advanced.acknowledgedBytes != advanced.byteCount) return Outcome.Paused(advanced)
                    return complete(advanced)
                }
            }
        }
        return complete(uploading)
    }

    private fun ensureSession(upload: ResumableAttachmentUpload, mimeType: String, contentType: AttachmentEgressContentType): ResumableUploadGatewayResult.Session? {
        upload.gatewaySessionId?.let { id ->
            return when (val offset = gateway.offset(id)) {
                is ResumableUploadGatewayResult.Session -> offset.takeIf { it.gatewaySessionId == id }
                else -> null
            }
        }
        return when (val created = gateway.create(ResumableUploadCreateRequest(upload, mimeType, contentType))) {
            is ResumableUploadGatewayResult.Session -> created
            else -> null
        }
    }

    private fun complete(upload: ResumableAttachmentUpload): Outcome {
        return when (val result = gateway.complete(requireNotNull(upload.gatewaySessionId))) {
            is ResumableUploadGatewayResult.RemoteReference -> {
                if (result.reference.gatewaySessionId != upload.gatewaySessionId || !result.reference.expiresAt.isAfter(clock.instant())) return failed(upload, "UPLOAD_REFERENCE_INVALID")
                val completed = uploads.transition(
                    upload.uploadId, setOf(ResumableAttachmentUploadStatus.UPLOADING), ResumableAttachmentUploadStatus.UPLOADED,
                    upload.gatewaySessionId, upload.byteCount, clock.instant(),
                ) ?: return Outcome.Paused(requireNotNull(uploads.find(upload.uploadId)))
                Outcome.Uploaded(completed, result.reference)
            }
            is ResumableUploadGatewayResult.Rejected -> failed(upload, result.safeErrorCode)
            is ResumableUploadGatewayResult.Session -> failed(upload, "UPLOAD_PROTOCOL_INVALID")
        }
    }

    private fun failed(upload: ResumableAttachmentUpload, code: String): Outcome.Rejected {
        uploads.transition(
            upload.uploadId,
            setOf(ResumableAttachmentUploadStatus.PENDING, ResumableAttachmentUploadStatus.UPLOADING, ResumableAttachmentUploadStatus.UNKNOWN, ResumableAttachmentUploadStatus.FAILED),
            ResumableAttachmentUploadStatus.FAILED, upload.gatewaySessionId, upload.acknowledgedBytes,
            clock.instant(), code,
        )
        return Outcome.Rejected(code)
    }
}

/** InputStream.skip is permitted to return zero; this loop guarantees an exact, verified offset. */
private fun InputStream.afterExactly(offset: Long): InputStream {
    var remaining = offset
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    while (remaining > 0) {
        val read = read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
        if (read < 0) throw IllegalStateException("附件流长度变化")
        remaining -= read.toLong()
    }
    return this
}
