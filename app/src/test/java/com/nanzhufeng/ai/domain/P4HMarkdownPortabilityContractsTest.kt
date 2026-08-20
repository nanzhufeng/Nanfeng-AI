package com.nanzhufeng.ai.domain

import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test

class P4HMarkdownPortabilityContractsTest {
    private val clock = Clock.fixed(Instant.parse("2026-08-13T00:00:00Z"), ZoneOffset.UTC)
    private val adapter = MarkdownKnowledgeAdapter(KnowledgeDomain(clock))

    @Test fun `utf8 bom crlf heading tags and multi-item separator are deterministic`() {
        val source = "\uFEFF---\r\ntags: kotlin, local\r\n---\r\n# Alpha\r\n正文\r\n$MARKDOWN_MULTI_ITEM_SEPARATOR\r\n# Beta\r\n```\r\n$MARKDOWN_MULTI_ITEM_SEPARATOR\r\n```\r\n正文二".toByteArray()
        val result = adapter.parse("notes.markdown", "text/markdown", source) as MarkdownParseResult.Parsed
        assertEquals(2, result.items.size); assertEquals("Alpha", result.items[0].title); assertEquals(setOf("kotlin", "local"), result.items[0].tags)
        assertTrue(result.items[1].body.contains(MARKDOWN_MULTI_ITEM_SEPARATOR))
    }

    @Test fun `documents ui duplicate display suffix remains an explicit markdown selection`() {
        assertIsParsed(adapter.parse("notes.md (1)", "application/octet-stream", "# 标题\n正文".toByteArray()))
    }

    @Test fun `malformed empty non markdown oversized invalid utf8 and sensitive are rejected`() {
        assertEquals(MarkdownImportFailure.NOT_MARKDOWN, (adapter.parse("a.txt", "text/plain", "x".toByteArray()) as MarkdownParseResult.Rejected).reason)
        assertEquals(MarkdownImportFailure.EMPTY_DOCUMENT, (adapter.parse("a.md", "text/plain", " \n".toByteArray()) as MarkdownParseResult.Rejected).reason)
        assertEquals(MarkdownImportFailure.INVALID_UTF8, (adapter.parse("a.md", "text/plain", byteArrayOf(0xC3.toByte())) as MarkdownParseResult.Rejected).reason)
        assertEquals(MarkdownImportFailure.HIGH_SENSITIVITY, (adapter.parse("a.md", "text/plain", "# x\nBearer abcdefghijklmnopqrst".toByteArray()) as MarkdownParseResult.Rejected).reason)
        assertEquals(MarkdownImportFailure.TOO_LARGE, (adapter.parse("a.md", "text/plain", ByteArray((MARKDOWN_TASK_MAX_BYTES + 1).toInt())) as MarkdownParseResult.Rejected).reason)
    }

    @Test fun `markdown export is explicit active formal selection and can reparse`() {
        val repo = object : KnowledgeManagementRepository {
            val snapshots = listOf(snapshot("a", KnowledgeStatus.ACTIVE), snapshot("b", KnowledgeStatus.DELETED))
            override fun mutate(intent: KnowledgeIntent, fingerprint: String) = throw UnsupportedOperationException()
            override fun findSnapshot(id: KnowledgeItemId) = snapshots.firstOrNull { it.item.id == id }
            override fun listSnapshots(filter: KnowledgeSearchFilter) = snapshots
        }
        var exportedMarkdown = ""; val result = ExportMarkdownKnowledgeUseCase(repo, object : MarkdownKnowledgeExportStore { override fun write(markdown: String, manifest: MarkdownExportManifest): MarkdownExportResult? { exportedMarkdown = markdown; return MarkdownExportResult("x.md", markdown.length.toLong(), "hash", manifest) } }, clock).execute(setOf(KnowledgeItemId("a")))
        assertEquals(1, result?.manifest?.itemCount); assertTrue(exportedMarkdown.contains("# Alpha")); assertFalse(exportedMarkdown.contains("Beta")); assertEquals(1, (adapter.parse("x.md", "text/markdown", exportedMarkdown.toByteArray()) as MarkdownParseResult.Parsed).items.size)
    }

    private fun snapshot(id: String, status: KnowledgeStatus): KnowledgeSnapshot {
        val now = Instant.parse("2026-08-13T00:00:00Z"); val title = if (id == "a") "Alpha" else "Beta"; val body = "正文"; val item = KnowledgeItem(KnowledgeItemId(id), title, body, emptyList(), CandidateProvenance(CandidateId("c$id"), InvocationId("i$id"), ProviderId.MOCK, "local", 1), now)
        return KnowledgeSnapshot(item, KnowledgeLifecycle(status, KnowledgeScope.GLOBAL, null, setOf("local"), MemoryDomain.sha256("$title\n$body"), now), listOf(KnowledgeRevision(KnowledgeRevisionId("r$id"), item.id, 1, title, body, status, KnowledgeScope.GLOBAL, null, setOf("local"), MemoryDomain.sha256("$title\n$body"), now)))
    }
    private fun assertIsParsed(value: MarkdownParseResult) { assertTrue(value is MarkdownParseResult.Parsed) }
}
