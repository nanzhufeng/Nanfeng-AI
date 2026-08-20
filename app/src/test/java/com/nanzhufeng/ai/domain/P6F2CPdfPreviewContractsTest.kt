package com.nanzhufeng.ai.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class P6F2CPdfPreviewContractsTest {
    private val asset = AttachmentReference("attachments/v1/private-document.pdf", "application/pdf", "local.pdf", AttachmentId("pdf-preview"), 5, "a".repeat(64))
    private val reference = ConversationAttachmentReference(asset.id, "application/pdf", "local.pdf", 5, "a".repeat(64))

    @Test fun `PDF page is resolved only from matching owner-local metadata without a private key`() {
        val preview = ConversationAttachmentPreviewProjection(FakeAssets(asset), FakeStore()).pdfPage(reference, 2)
        assertEquals(2, preview.page?.pageNumber)
        assertEquals(3, preview.page?.pageCount)
        assertNull(preview.unavailableReason)
        assertFalse(preview.toString().contains("attachments/v1/"))
        assertFalse(preview.toString().contains("private-document"))
    }

    @Test fun `missing or mismatched PDF is a safe unavailable result`() {
        val missing = ConversationAttachmentPreviewProjection(FakeAssets(null), FakeStore()).pdfPage(reference, 1)
        val mismatch = ConversationAttachmentPreviewProjection(FakeAssets(asset.copy(sha256 = "b".repeat(64))), FakeStore()).pdfPage(reference, 1)
        assertEquals("本地 PDF 附件不可用", missing.unavailableReason)
        assertEquals("本地 PDF 附件校验不一致", mismatch.unavailableReason)
        assertTrue(listOf(missing, mismatch).none { it.toString().contains("attachments/v1/") })
    }

    private class FakeAssets(private val value: AttachmentReference?) : PrivateAttachmentRepository {
        override fun save(asset: AttachmentReference) = asset
        override fun findById(id: AttachmentId) = value?.takeIf { it.id == id }
        override fun findBySha256(sha256: String) = value?.takeIf { it.sha256 == sha256 }
    }

    private class FakeStore : PrivateAttachmentStore {
        override fun import(request: AttachmentImportRequest) = AttachmentImportResult.Rejected(AiTaskError.AttachmentUnsupportedType)
        override fun read(attachment: AttachmentReference) = AttachmentReadResult.Rejected(AiTaskError.AttachmentUnsupportedType)
        override fun thumbnail(attachment: AttachmentReference) = AttachmentThumbnailResult.Rejected(AiTaskError.AttachmentUnsupportedType)
        override fun pdfPage(attachment: AttachmentReference, pageNumber: Int) = AttachmentPdfPageResult.Ready(AttachmentPdfPage(AttachmentThumbnail(byteArrayOf(1), 1, 1), pageNumber, 3))
    }
}
