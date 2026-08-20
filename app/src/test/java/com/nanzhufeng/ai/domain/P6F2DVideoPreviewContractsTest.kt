package com.nanzhufeng.ai.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class P6F2DVideoPreviewContractsTest {
    private val asset = AttachmentReference("attachments/v1/private-video.mp4", "video/mp4", "local.mp4", AttachmentId("video-preview"), 9, "a".repeat(64))

    @Test fun `verified mp4 returns poster duration and display bytes only after explicit request`() {
        val projection = ConversationAttachmentPreviewProjection(FakeAssets(asset), FakeStore())
        val card = projection.project(asset.toConversationReference())
        assertNotNull(card.thumbnail)
        assertEquals(12_345L, card.videoDurationMillis)
        val preview = projection.video(asset.toConversationReference())
        assertNotNull(preview.poster)
        assertEquals(12_345L, preview.durationMillis)
        assertArrayEquals(byteArrayOf(1, 2, 3), preview.bytes)
        assertNull(preview.unavailableReason)
    }

    @Test fun `wrong mime and integrity mismatch fail closed`() {
        val projection = ConversationAttachmentPreviewProjection(FakeAssets(asset.copy(sha256 = "b".repeat(64))), FakeStore())
        assertNotNull(projection.video(asset.toConversationReference()).unavailableReason)
        assertNotNull(projection.video(asset.toConversationReference().copy(mimeType = "application/pdf")).unavailableReason)
    }

    private class FakeAssets(private val value: AttachmentReference?) : PrivateAttachmentRepository {
        override fun save(asset: AttachmentReference) = asset
        override fun findById(id: AttachmentId) = value?.takeIf { it.id == id }
        override fun findBySha256(sha256: String) = value?.takeIf { it.sha256 == sha256 }
    }

    private class FakeStore : PrivateAttachmentStore {
        override fun import(request: AttachmentImportRequest) = AttachmentImportResult.Rejected(AiTaskError.AttachmentImportFailed)
        override fun read(attachment: AttachmentReference) = AttachmentReadResult.Rejected(AiTaskError.AttachmentNotReady)
        override fun thumbnail(attachment: AttachmentReference) = AttachmentThumbnailResult.Rejected(AiTaskError.AttachmentNotReady)
        override fun videoMetadata(attachment: AttachmentReference) = AttachmentVideoMetadataResult.Ready(AttachmentVideoMetadata(AttachmentThumbnail(byteArrayOf(9), 1, 1), 12_345L))
        override fun videoPreview(attachment: AttachmentReference) = AttachmentVideoPreviewResult.Ready(AttachmentVideoPreview(AttachmentThumbnail(byteArrayOf(9), 1, 1), 12_345L, byteArrayOf(1, 2, 3)))
    }
}
