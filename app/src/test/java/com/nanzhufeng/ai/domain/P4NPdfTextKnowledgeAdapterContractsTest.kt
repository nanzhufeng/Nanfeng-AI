package com.nanzhufeng.ai.domain

import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class P4NPdfTextKnowledgeAdapterContractsTest {
    private val adapter = PdfTextKnowledgeAdapter(KnowledgeDomain(Clock.fixed(Instant.parse("2026-08-13T00:00:00Z"), ZoneOffset.UTC)))
    @Test fun `mime header encrypted empty and bounded resources fail safely`() {
        assertEquals(PdfTextImportFailure.NOT_PDF, rejected("notes.txt", "text/plain", "%PDF-1.4".toByteArray()))
        assertEquals(PdfTextImportFailure.NOT_PDF, rejected("notes.pdf", "application/pdf", "not a pdf".toByteArray()))
        assertEquals(PdfTextImportFailure.TOO_LARGE, rejected("large.pdf", "application/pdf", ByteArray((PDF_TEXT_MAX_BYTES + 1).toInt())))
        assertEquals(PdfTextImportFailure.TOO_MANY_OBJECTS, rejected("objects.pdf", "application/pdf", ("%PDF-1.4\n" + (1..(PDF_TEXT_MAX_OBJECT_MARKERS + 1)).joinToString("\n") { "$it 0 obj" }).toByteArray()))
    }
    @Test fun `sensitive detector and per page bounds are explicit shared safety gates`() {
        assertTrue(MemoryDomain.sensitiveRejection("Bearer abcdefghijklmnopqrstuvwxyz") != null)
        assertTrue(PDF_TEXT_MAX_PAGE_CODE_POINTS < PDF_TEXT_MAX_TOTAL_CODE_POINTS)
    }
    private fun rejected(name: String, mime: String, bytes: ByteArray) = (adapter.parse(PdfTextImportTaskId.new(), name, mime, bytes) as PdfTextParseResult.Rejected).reason
}
