package com.nanzhufeng.ai.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversationAttachmentFileNameCompatibilityTest {
    @Test fun `Chinese full stop and whitespace before extension are normalized`() {
        assertEquals("md", normalizedAttachmentExtension("粘贴的 markdown (1)。 md"))
        assertEquals("md", normalizedAttachmentExtension("粘贴的 markdown (1)．md"))
        assertTrue(isSafeTextAttachment("application/octet-stream", "粘贴的 markdown (1)。 md"))
    }

    @Test fun `generic binary is promoted only for an explicit safe filename`() {
        assertTrue(isSafeArchiveAttachment("application/octet-stream", "开发文档。 zip"))
        assertFalse(isSafeTextAttachment("application/octet-stream", "unknown.bin"))
        assertFalse(isSafeArchiveAttachment("application/octet-stream", "unknown.bin"))
    }

    @Test fun `declared text and zip types remain compatible without a filename`() {
        assertTrue(isSafeTextAttachment("text/markdown", null))
        assertTrue(isSafeArchiveAttachment("application/zip", null))
    }

    @Test fun `historical generic attachment with Chinese full stop opens as bounded text`() {
        val bytes = "# 可正常查看".toByteArray()
        val asset = AttachmentReference(
            reference = "attachments/v1/fixture.bin",
            mimeType = "application/octet-stream",
            displayName = "粘贴的 markdown (1)。 md",
            id = AttachmentId("full-stop-markdown"),
            byteCount = bytes.size.toLong(),
            sha256 = "a".repeat(64),
        )
        val repository = object : PrivateAttachmentRepository {
            override fun save(asset: AttachmentReference) = asset
            override fun findById(id: AttachmentId) = asset.takeIf { it.id == id }
            override fun findBySha256(sha256: String) = asset.takeIf { it.sha256 == sha256 }
        }
        val store = object : PrivateAttachmentStore {
            override fun import(request: AttachmentImportRequest) = AttachmentImportResult.Rejected(AiTaskError.AttachmentUnsupportedType)
            override fun read(attachment: AttachmentReference) = AttachmentReadResult.Content(bytes)
            override fun thumbnail(attachment: AttachmentReference) = AttachmentThumbnailResult.Rejected(AiTaskError.AttachmentUnsupportedType)
        }

        val preview = ConversationAttachmentPreviewProjection(repository, store).project(asset.toConversationReference())

        assertEquals("# 可正常查看", preview.textPreview?.text)
        assertEquals(null, preview.unavailableReason)
    }
}
