package com.nanzhufeng.ai.domain

import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class P4EKnowledgeContractsTest {
    private val clock = Clock.fixed(Instant.parse("2026-08-13T00:00:00Z"), ZoneOffset.UTC)

    @Test fun `search is stable across Chinese English Markdown code tags and source while hidden states stay excluded`() {
        val domain = KnowledgeDomain(clock)
        val active = snapshot("k-a", "Compose 检索", "```kotlin\nfun search(query: String) = query\n```\n中文正文", setOf("android", "代码"), KnowledgeStatus.ACTIVE, Instant.parse("2026-08-13T00:00:00Z"))
        val archived = snapshot("k-z", "Search notes", "English retrieval body", setOf("search"), KnowledgeStatus.ARCHIVED, Instant.parse("2026-08-14T00:00:00Z"))
        val results = domain.search(listOf(archived, active), KnowledgeSearchFilter(query = "检索"))
        assertEquals(listOf(KnowledgeItemId("k-a")), results.map { it.id })
        assertTrue(results.single().snippet.contains("检索"))
        assertEquals(active.item.sourceEvidence, results.single().sourceEvidence)
        assertEquals(active.item.createdAt, results.single().createdAt)
        assertEquals(listOf(KnowledgeItemId("k-a")), domain.search(listOf(active), KnowledgeSearchFilter(query = "kotlin")).map { it.id })
        assertEquals(listOf(KnowledgeItemId("k-a")), domain.search(listOf(active), KnowledgeSearchFilter(query = "代码")).map { it.id })
        assertEquals(listOf(KnowledgeItemId("k-z")), domain.search(listOf(archived), KnowledgeSearchFilter(query = "search", status = KnowledgeStatus.ARCHIVED)).map { it.id })
    }

    @Test fun `mutation rejects sensitive content before repository write`() {
        val repository = RecordingKnowledgeRepository()
        val manage = ManageKnowledgeUseCase(KnowledgeDomain(clock), repository)
        val result = manage.execute(KnowledgeIntent(KnowledgeIntentId.new(), KnowledgeIntentAction.CREATE_MANUAL, KnowledgeItemId("sensitive"), "凭据", "api key: not-a-real-key"))
        assertTrue(result is KnowledgeMutationResult.Rejected)
        assertEquals(0, repository.mutations)
    }

    private fun snapshot(id: String, title: String, body: String, tags: Set<String>, status: KnowledgeStatus, updatedAt: Instant): KnowledgeSnapshot {
        val item = KnowledgeItem(KnowledgeItemId(id), title, body, listOf(SourceEvidence(CaptureSourceType.MANUAL_TEXT, updatedAt, "manual", emptySet())), CandidateProvenance(CandidateId("c-$id"), InvocationId("i-$id"), ProviderId.MOCK, "local", 1), updatedAt)
        val hash = MemoryDomain.sha256("$title\n$body")
        return KnowledgeSnapshot(item, KnowledgeLifecycle(status, KnowledgeScope.GLOBAL, null, tags, hash, updatedAt), listOf(KnowledgeRevision(KnowledgeRevisionId("r-$id"), item.id, 1, title, body, status, KnowledgeScope.GLOBAL, null, tags, hash, updatedAt)))
    }

    private class RecordingKnowledgeRepository : KnowledgeManagementRepository {
        var mutations = 0
        override fun mutate(intent: KnowledgeIntent, fingerprint: String): KnowledgeMutationResult { mutations++; return KnowledgeMutationResult.Rejected(KnowledgeRejectionCode.INVALID_ACTION) }
        override fun findSnapshot(id: KnowledgeItemId): KnowledgeSnapshot? = null
        override fun listSnapshots(filter: KnowledgeSearchFilter): List<KnowledgeSnapshot> = emptyList()
    }
}
