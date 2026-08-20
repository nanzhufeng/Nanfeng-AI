package com.nanzhufeng.ai.domain

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class P4FKnowledgeDeduplicationContractsTest {
    private val domain = KnowledgeDeduplicationDomain()

    @Test fun `only same active scope matches and deterministic reasons are ordered`() {
        val anchor = snapshot("anchor", "Compose Notes", "Line one\nLine two", KnowledgeScope.GLOBAL, null, Instant.parse("2026-08-13T00:00:00Z"))
        val exact = snapshot("exact", " compose   notes ", "Line one   Line two", KnowledgeScope.GLOBAL, null, Instant.parse("2026-08-14T00:00:00Z"))
        val sameTitle = snapshot("title", "COMPOSE NOTES", "Different body", KnowledgeScope.GLOBAL, null, Instant.parse("2026-08-15T00:00:00Z"))
        val otherProject = snapshot("project", "Compose Notes", "Line one Line two", KnowledgeScope.PROJECT, ProjectId("p-1"), Instant.parse("2026-08-16T00:00:00Z"))
        val archived = snapshot("archived", "Compose Notes", "Line one Line two", KnowledgeScope.GLOBAL, null, Instant.parse("2026-08-17T00:00:00Z"), KnowledgeStatus.ARCHIVED)

        val result = domain.candidates(anchor.item.id, listOf(archived, sameTitle, otherProject, exact, anchor)) as KnowledgeDuplicateCandidatesResult.Available

        assertEquals(listOf(KnowledgeItemId("exact"), KnowledgeItemId("title")), result.candidates.map { it.knowledgeId })
        assertEquals(setOf(KnowledgeDuplicateReason.EXACT_CONTENT, KnowledgeDuplicateReason.SAME_NORMALIZED_TITLE), result.candidates.first().reasons)
        assertEquals(setOf(KnowledgeDuplicateReason.SAME_NORMALIZED_TITLE), result.candidates.last().reasons)
    }

    @Test fun `sensitive matching candidate rejects the whole read without partial output`() {
        val anchor = snapshot("anchor", "账号资料", "一般说明", KnowledgeScope.GLOBAL, null, Instant.parse("2026-08-13T00:00:00Z"))
        val sensitive = snapshot("sensitive", "账号资料", "api key: not-a-real-key", KnowledgeScope.GLOBAL, null, Instant.parse("2026-08-13T00:00:01Z"))

        val result = domain.candidates(anchor.item.id, listOf(anchor, sensitive))

        assertEquals(KnowledgeDuplicateCandidatesResult.Rejected(KnowledgeDuplicateCandidatesRejection.HIGH_SENSITIVITY), result)
    }

    @Test fun `missing or hidden anchor does not search another item`() {
        val archived = snapshot("archived", "Notes", "body", KnowledgeScope.GLOBAL, null, Instant.parse("2026-08-13T00:00:00Z"), KnowledgeStatus.ARCHIVED)

        assertEquals(KnowledgeDuplicateCandidatesResult.Rejected(KnowledgeDuplicateCandidatesRejection.MISSING_KNOWLEDGE), domain.candidates(KnowledgeItemId("missing"), listOf(archived)))
        assertEquals(KnowledgeDuplicateCandidatesResult.Rejected(KnowledgeDuplicateCandidatesRejection.INELIGIBLE_KNOWLEDGE), domain.candidates(archived.item.id, listOf(archived)))
    }

    private fun snapshot(id: String, title: String, body: String, scope: KnowledgeScope, projectId: ProjectId?, updatedAt: Instant, status: KnowledgeStatus = KnowledgeStatus.ACTIVE): KnowledgeSnapshot {
        val item = KnowledgeItem(KnowledgeItemId(id), title, body, listOf(SourceEvidence(CaptureSourceType.MANUAL_TEXT, updatedAt, "manual", emptySet())), CandidateProvenance(CandidateId("c-$id"), InvocationId("i-$id"), ProviderId.MOCK, "local", 1), updatedAt)
        val hash = MemoryDomain.sha256("$title\n$body")
        return KnowledgeSnapshot(item, KnowledgeLifecycle(status, scope, projectId, emptySet(), hash, updatedAt), listOf(KnowledgeRevision(KnowledgeRevisionId("r-$id"), item.id, 1, title, body, status, scope, projectId, emptySet(), hash, updatedAt)))
    }
}
