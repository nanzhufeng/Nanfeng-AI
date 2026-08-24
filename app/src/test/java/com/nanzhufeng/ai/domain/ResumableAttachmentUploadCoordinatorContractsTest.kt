package com.nanzhufeng.ai.domain

import com.nanzhufeng.ai.data.local.ResumableAttachmentUploadEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class ResumableAttachmentUploadCoordinatorContractsTest {
    private val now = Instant.parse("2026-08-23T12:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)

    @Test fun `interrupted upload resumes at gateway offset and persists no remote URL`() {
        val store = Store()
        val original = upload(session = "session_1", acknowledged = 3, status = ResumableAttachmentUploadStatus.UNKNOWN)
        store.create(original)
        val gateway = Gateway(offset = 3, appended = 6)

        val outcome = ResumableAttachmentUploadCoordinator(store, gateway, clock).continueUpload(
            original, "image/png", AttachmentEgressContentType.IMAGE,
        ) { ByteArrayInputStream(byteArrayOf(1, 2, 3, 4, 5, 6)) }

        assertTrue(outcome is ResumableAttachmentUploadCoordinator.Outcome.Uploaded)
        assertEquals(3L, gateway.appendedAt)
        assertEquals(listOf<Byte>(4, 5, 6), gateway.received.toList())
        assertEquals(ResumableAttachmentUploadStatus.UPLOADED, store.find(original.uploadId)?.status)
        assertEquals(6L, store.find(original.uploadId)?.acknowledgedBytes)
        assertTrue(ResumableAttachmentUploadEntity::class.java.declaredFields.none { it.name.contains("url", ignoreCase = true) })
    }

    @Test fun `partial gateway acknowledgement stays resumable rather than silently restarting`() {
        val store = Store()
        val original = upload(session = "session_1", acknowledged = 0, status = ResumableAttachmentUploadStatus.PENDING)
        store.create(original)
        val gateway = Gateway(offset = 0, appended = 2)

        val outcome = ResumableAttachmentUploadCoordinator(store, gateway, clock).continueUpload(
            original, "image/png", AttachmentEgressContentType.IMAGE,
        ) { ByteArrayInputStream(byteArrayOf(1, 2, 3, 4, 5, 6)) }

        assertTrue(outcome is ResumableAttachmentUploadCoordinator.Outcome.Paused)
        assertEquals(2L, store.find(original.uploadId)?.acknowledgedBytes)
        assertEquals(ResumableAttachmentUploadStatus.UPLOADING, store.find(original.uploadId)?.status)
    }

    private fun upload(session: String?, acknowledged: Long, status: ResumableAttachmentUploadStatus) = ResumableAttachmentUpload(
        ResumableAttachmentUploadId("upload_1"), NormalChatSendAttemptId("attempt_1"), AttachmentId("attachment_1"),
        ProviderId.OPENROUTER, "openai/test", "personal-gateway", "a".repeat(64), 6,
        session, acknowledged, status, now, now,
    )

    private class Store : ResumableAttachmentUploadStore {
        private val values = mutableMapOf<ResumableAttachmentUploadId, ResumableAttachmentUpload>()
        override fun create(upload: ResumableAttachmentUpload): ResumableAttachmentUpload = upload.also { values[it.uploadId] = it }
        override fun find(uploadId: ResumableAttachmentUploadId) = values[uploadId]
        override fun findForAttemptAndAttachment(attemptId: NormalChatSendAttemptId, attachmentId: AttachmentId) = values.values.singleOrNull { it.normalChatAttemptId == attemptId && it.attachmentId == attachmentId }
        override fun transition(id: ResumableAttachmentUploadId, expected: Set<ResumableAttachmentUploadStatus>, next: ResumableAttachmentUploadStatus, gatewaySessionId: String?, acknowledgedBytes: Long, updatedAt: Instant, safeErrorCode: String?): ResumableAttachmentUpload? {
            val existing = values[id] ?: return null
            if (existing.status !in expected) return null
            return existing.copy(status = next, gatewaySessionId = gatewaySessionId, acknowledgedBytes = acknowledgedBytes, updatedAt = updatedAt, safeErrorCode = safeErrorCode).also { values[id] = it }
        }
        override fun markInterruptedAsUnknown(updatedAt: Instant): Int = 0
    }

    private class Gateway(private val offset: Long, private val appended: Long) : ResumableAttachmentGateway {
        var appendedAt: Long = -1
        var received = ByteArray(0)
        override fun create(request: ResumableUploadCreateRequest) = ResumableUploadGatewayResult.Session("session_1", offset)
        override fun offset(gatewaySessionId: String) = ResumableUploadGatewayResult.Session(gatewaySessionId, offset)
        override fun append(gatewaySessionId: String, offset: Long, bytes: InputStream, byteCount: Long): ResumableUploadGatewayResult {
            appendedAt = offset
            received = bytes.readBytes()
            return ResumableUploadGatewayResult.Session(gatewaySessionId, appended)
        }
        override fun complete(gatewaySessionId: String) = ResumableUploadGatewayResult.RemoteReference(
            ResumableUploadRemoteReference(gatewaySessionId, "https://gateway.example.test/refs/$gatewaySessionId", Instant.parse("2026-08-23T12:01:00Z")),
        )
    }
}
