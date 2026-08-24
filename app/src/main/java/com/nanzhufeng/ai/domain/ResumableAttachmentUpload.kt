package com.nanzhufeng.ai.domain

import java.io.InputStream
import java.time.Instant
import java.util.UUID

/**
 * The durable, content-free state of one attachment sent through a user-configured Nanfeng
 * upload gateway.  A gateway is never selected implicitly: normal direct Provider transport
 * remains the default until a configured gateway is explicitly enabled for the exact model.
 */
@JvmInline value class ResumableAttachmentUploadId(val value: String) {
    companion object { fun new() = ResumableAttachmentUploadId(UUID.randomUUID().toString()) }
}

enum class ResumableAttachmentUploadStatus { PENDING, UPLOADING, UPLOADED, FAILED, UNKNOWN, CANCELLED }

data class ResumableAttachmentUpload(
    val uploadId: ResumableAttachmentUploadId,
    val normalChatAttemptId: NormalChatSendAttemptId,
    val attachmentId: AttachmentId,
    val providerId: ProviderId,
    val modelId: String,
    val gatewayId: String,
    val sha256: String,
    val byteCount: Long,
    /** Opaque server session id, not an upload URL, bearer token, local path, filename or bytes. */
    val gatewaySessionId: String?,
    val acknowledgedBytes: Long,
    val status: ResumableAttachmentUploadStatus,
    val createdAt: Instant,
    val updatedAt: Instant,
    val safeErrorCode: String? = null,
) {
    init {
        require(modelId.isNotBlank() && gatewayId.matches(Regex("[A-Za-z0-9._:-]{1,120}")))
        require(sha256.matches(Regex("[0-9a-f]{64}")) && byteCount > 0 && acknowledgedBytes in 0..byteCount)
        require(gatewaySessionId == null || gatewaySessionId.matches(Regex("[A-Za-z0-9._:-]{1,240}")))
        require(safeErrorCode == null || safeErrorCode.matches(Regex("[A-Z0-9_]{1,64}")))
        require(status != ResumableAttachmentUploadStatus.UPLOADED || (gatewaySessionId != null && acknowledgedBytes == byteCount))
    }
}

/** The app persists only opaque server identifiers. Remote URLs are short lived and are re-resolved on demand. */
data class ResumableUploadRemoteReference(
    val gatewaySessionId: String,
    val url: String,
    val expiresAt: Instant,
) {
    init {
        require(gatewaySessionId.matches(Regex("[A-Za-z0-9._:-]{1,240}")))
        require(url.startsWith("https://"))
    }
}

interface ResumableAttachmentUploadStore {
    fun create(upload: ResumableAttachmentUpload): ResumableAttachmentUpload
    fun find(uploadId: ResumableAttachmentUploadId): ResumableAttachmentUpload?
    fun findForAttemptAndAttachment(attemptId: NormalChatSendAttemptId, attachmentId: AttachmentId): ResumableAttachmentUpload?
    fun transition(
        id: ResumableAttachmentUploadId,
        expected: Set<ResumableAttachmentUploadStatus>,
        next: ResumableAttachmentUploadStatus,
        gatewaySessionId: String?,
        acknowledgedBytes: Long,
        updatedAt: Instant,
        safeErrorCode: String? = null,
    ): ResumableAttachmentUpload?
    fun markInterruptedAsUnknown(updatedAt: Instant): Int
}

/**
 * Config is supplied by the user's deployment/settings, never compiled into a Provider adapter.
 * The endpoint is intentionally HTTPS-only and no credential appears in this value or in Room.
 */
data class ResumableAttachmentGatewayConfiguration(
    val gatewayId: String,
    val endpoint: String,
    val enabled: Boolean,
    /** Every type must be explicitly declared; unknown models continue with the direct path. */
    val remoteUrlKinds: Set<AttachmentEgressContentType>,
) {
    init {
        require(gatewayId.matches(Regex("[A-Za-z0-9._:-]{1,120}")))
        require(endpoint.startsWith("https://") && endpoint.length <= 2_048)
    }
}

interface ResumableAttachmentGatewayConfigurationRepository {
    fun load(): ResumableAttachmentGatewayConfiguration?
}

data class ResumableUploadCreateRequest(
    val upload: ResumableAttachmentUpload,
    val mimeType: String,
    val contentType: AttachmentEgressContentType,
)

sealed interface ResumableUploadGatewayResult {
    data class Session(val gatewaySessionId: String, val acknowledgedBytes: Long) : ResumableUploadGatewayResult
    data class RemoteReference(val reference: ResumableUploadRemoteReference) : ResumableUploadGatewayResult
    data class Rejected(val safeErrorCode: String) : ResumableUploadGatewayResult
}

/**
 * A standard offset protocol boundary.  The Android implementation must call [offset] after a
 * process/network interruption and re-open the verified private source at that exact byte.
 * Provider keys do not cross this interface.
 */
interface ResumableAttachmentGateway {
    fun create(request: ResumableUploadCreateRequest): ResumableUploadGatewayResult
    fun offset(gatewaySessionId: String): ResumableUploadGatewayResult
    fun append(gatewaySessionId: String, offset: Long, bytes: InputStream, byteCount: Long): ResumableUploadGatewayResult
    fun complete(gatewaySessionId: String): ResumableUploadGatewayResult
}
