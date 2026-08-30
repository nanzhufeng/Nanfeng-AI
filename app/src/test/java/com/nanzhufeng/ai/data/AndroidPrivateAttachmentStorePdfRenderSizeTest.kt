package com.nanzhufeng.ai.data

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidPrivateAttachmentStorePdfRenderSizeTest {
    @Test fun `ordinary A4 page is rasterized above PdfRenderer intrinsic resolution`() {
        val result = pdfPreviewRenderSize(595, 842)

        assertTrue(result.width > 595)
        assertTrue(result.height > 842)
        assertTrue(result.width.toLong() * result.height <= 6_000_000L)
        assertTrue(maxOf(result.width, result.height) <= 2_880)
    }

    @Test fun `large pages remain inside edge and decoded bitmap limits`() {
        val result = pdfPreviewRenderSize(10_000, 10_000)

        assertTrue(result.width.toLong() * result.height <= 6_000_000L)
        assertTrue(maxOf(result.width, result.height) <= 2_880)
        assertEquals(result.width, result.height)
    }

    @Test fun `rendering preserves a wide page aspect ratio`() {
        val result = pdfPreviewRenderSize(1_600, 900)

        assertTrue(kotlin.math.abs(result.width.toDouble() / result.height - 16.0 / 9.0) < 0.01)
    }

    @Test fun `active PDF renderer is reused only after full verification and metadata match`() {
        val source = File("src/main/java/com/nanzhufeng/ai/data/AndroidPrivateAttachmentStore.kt").readText()
        val session = source.substring(source.indexOf("private class PdfRendererSession"), source.indexOf("/**\n * PdfRenderer reports"))
        val owner = source.substring(source.indexOf("private fun pdfRendererSessionFor"), source.indexOf("private fun sampleSizeFor"))

        for (token in listOf("expectedSha256", "file.length()", "file.lastModified()", "file.sha256()", "PdfRenderer(descriptor)", "closePdfRendererSession")) {
            assertTrue(token, session.contains(token) || owner.contains(token))
        }
        assertFalse(owner.contains("MAX_PDF_PREVIEW_EDGE ="))
    }
}
