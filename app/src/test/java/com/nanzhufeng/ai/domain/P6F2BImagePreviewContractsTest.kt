package com.nanzhufeng.ai.domain

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class P6F2BImagePreviewContractsTest {
    private val asset = AttachmentReference(
        reference = "attachments/v1/private-image.png",
        mimeType = "image/png",
        displayName = "local.png",
        id = AttachmentId("image-preview"),
        byteCount = 3,
        sha256 = "a".repeat(64),
    )
    private val reference = ConversationAttachmentReference(asset.id, "image/png", "local.png", 3, "a".repeat(64))

    @Test fun `original pixels are resolved only from matching owner-local asset metadata`() {
        val projection = ConversationAttachmentPreviewProjection(FakeAssets(asset), FakeStore())
        val preview = projection.original(reference)

        assertArrayEquals(byteArrayOf(7, 8, 9), preview.bytes)
        assertEquals(asset.id, preview.id)
        assertNull(preview.unavailableReason)
        assertFalse(preview.toString().contains("attachments/v1/"))
        assertFalse(preview.toString().contains("private-image"))
    }

    @Test fun `mismatch and missing original remain safe unavailable states without a private reference`() {
        val mismatch = projection(FakeAssets(asset.copy(sha256 = "b".repeat(64))), FakeStore()).original(reference)
        val missing = projection(FakeAssets(null), FakeStore()).original(reference)

        assertNull(mismatch.bytes)
        assertEquals("本地附件校验不一致", mismatch.unavailableReason)
        assertNull(missing.bytes)
        assertEquals("本地附件不可用", missing.unavailableReason)
        assertTrue(listOf(mismatch, missing).none { it.toString().contains("attachments/v1/") })
    }

    private fun projection(assets: PrivateAttachmentRepository, store: PrivateAttachmentStore) =
        ConversationAttachmentPreviewProjection(assets, store)

    private class FakeAssets(private val value: AttachmentReference?) : PrivateAttachmentRepository {
        override fun save(asset: AttachmentReference) = asset
        override fun findById(id: AttachmentId) = value?.takeIf { it.id == id }
        override fun findBySha256(sha256: String) = value?.takeIf { it.sha256 == sha256 }
    }

    private class FakeStore : PrivateAttachmentStore {
        override fun import(request: AttachmentImportRequest) = AttachmentImportResult.Rejected(AiTaskError.AttachmentUnsupportedType)
        override fun read(attachment: AttachmentReference) = AttachmentReadResult.Content(byteArrayOf(7, 8, 9))
        override fun thumbnail(attachment: AttachmentReference) = AttachmentThumbnailResult.Ready(AttachmentThumbnail(byteArrayOf(1), 1, 1))
    }
}
