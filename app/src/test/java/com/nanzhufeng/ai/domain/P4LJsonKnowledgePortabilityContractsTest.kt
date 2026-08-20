package com.nanzhufeng.ai.domain

import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class P4LJsonKnowledgePortabilityContractsTest {
    private val adapter = JsonKnowledgeAdapter(KnowledgeDomain(Clock.fixed(Instant.parse("2026-08-13T00:00:00Z"), ZoneOffset.UTC)))
    private fun json(extra: String = "") = """{"format":"nfai.knowledge.json","schemaVersion":"1","exportedAt":"2026-08-13T00:00:00Z","items":[{"id":"old-alpha","title":"Alpha","body":"正文","tags":["local"],"scope":"GLOBAL","projectRef":null,"revision":"1","hash":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa","source":{"kind":"LOCAL","summary":"safe local summary"}$extra}]}"""

    @Test fun `versioned self describing json parses as inert data`() {
        val result = adapter.parse("knowledge.json", "application/json", json().toByteArray()) as JsonKnowledgeParseResult.Parsed
        assertEquals(1, result.items.size); assertEquals("Alpha", result.items.single().title); assertEquals(KnowledgeScope.GLOBAL, result.items.single().requestedScope)
    }
    @Test fun `duplicate unknown malformed unsafe and unsupported values reject deterministically`() {
        assertEquals(JsonKnowledgeFailure.DUPLICATE_KEY, rejected(json().replace("\"format\":\"nfai.knowledge.json\",", "\"format\":\"nfai.knowledge.json\",\"format\":\"nfai.knowledge.json\",")))
        assertEquals(JsonKnowledgeFailure.UNKNOWN_FIELD, rejected(json().replace("\"items\":", "\"unexpected\":true,\"items\":")))
        assertEquals(JsonKnowledgeFailure.UNKNOWN_VERSION, rejected(json().replace("\"schemaVersion\":\"1\"", "\"schemaVersion\":\"99\"")))
        assertEquals(JsonKnowledgeFailure.UNSAFE_METADATA, rejected(json().replace("safe local summary", "https://example.invalid")))
        assertEquals(JsonKnowledgeFailure.HIGH_SENSITIVITY, rejected(json().replace("正文", "Bearer abcdefghijklmnopqrst")))
        assertEquals(JsonKnowledgeFailure.MALFORMED_JSON, rejected(json().replace("\"1\"", "NaN")))
    }
    @Test fun `external fields paths and project scope are never silently imported across projects`() {
        assertEquals(JsonKnowledgeFailure.UNKNOWN_FIELD, rejected(json().replace("\"source\":{", "\"attachmentBytes\":\"x\",\"source\":{")))
        assertEquals(JsonKnowledgeFailure.UNSAFE_METADATA, rejected(json().replace("\"projectRef\":null", "\"projectRef\":\"/tmp/x\"")))
        val project = json().replace("\"scope\":\"GLOBAL\",\"projectRef\":null", "\"scope\":\"PROJECT\",\"projectRef\":\"project-safe\"")
        assertEquals(KnowledgeScope.PROJECT, (adapter.parse("knowledge.json", "application/json", project.toByteArray()) as JsonKnowledgeParseResult.Parsed).items.single().requestedScope)
    }
    @Test fun `size and invalid utf8 are rejected before any formal write`() {
        assertEquals(JsonKnowledgeFailure.TOO_LARGE, rejected(ByteArray((JSON_KNOWLEDGE_MAX_BYTES + 1).toInt())))
        assertEquals(JsonKnowledgeFailure.MALFORMED_JSON, rejected(byteArrayOf(0xC3.toByte())))
    }
    private fun rejected(source: String) = rejected(source.toByteArray())
    private fun rejected(bytes: ByteArray): JsonKnowledgeFailure = (adapter.parse("knowledge.json", "application/json", bytes) as JsonKnowledgeParseResult.Rejected).reason
}
