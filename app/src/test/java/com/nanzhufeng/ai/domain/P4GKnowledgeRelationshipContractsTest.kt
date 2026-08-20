package com.nanzhufeng.ai.domain

import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class P4GKnowledgeRelationshipContractsTest {
    private val domain = KnowledgeRelationshipDomain(Clock.fixed(Instant.parse("2026-08-13T00:00:00Z"), ZoneOffset.UTC))

    @Test fun `symmetric edges are canonical but supports keeps explicit direction`() {
        val a = snapshot("a", "甲", "正文", KnowledgeScope.GLOBAL, null)
        val b = snapshot("b", "乙", "正文", KnowledgeScope.GLOBAL, null)
        val related = domain.confirm(confirm("related", KnowledgeRelationshipType.RELATED, b, a), listOf(a, b), emptyList()) as RelationshipDecision.Confirm
        assertEquals(a.item.id, related.from); assertEquals(b.item.id, related.to)
        val supports = domain.confirm(confirm("supports", KnowledgeRelationshipType.SUPPORTS, b, a), listOf(a, b), emptyList()) as RelationshipDecision.Confirm
        assertEquals(b.item.id, supports.from); assertEquals(a.item.id, supports.to)
    }

    @Test fun `hidden cross-scope stale sensitive and self endpoints are rejected`() {
        val a = snapshot("a", "甲", "正文", KnowledgeScope.GLOBAL, null)
        val archived = snapshot("b", "乙", "正文", KnowledgeScope.GLOBAL, null, KnowledgeStatus.ARCHIVED)
        assertEquals(KnowledgeRelationshipRejection.INELIGIBLE_KNOWLEDGE, (domain.confirm(confirm("hidden", KnowledgeRelationshipType.RELATED, a, archived), listOf(a, archived), emptyList()) as RelationshipDecision.Rejected).code)
        val project = snapshot("p", "项目", "正文", KnowledgeScope.PROJECT, ProjectId("p"))
        assertEquals(KnowledgeRelationshipRejection.CROSS_SCOPE, (domain.confirm(confirm("scope", KnowledgeRelationshipType.RELATED, a, project), listOf(a, project), emptyList()) as RelationshipDecision.Rejected).code)
        assertEquals(KnowledgeRelationshipRejection.SELF_REFERENCE, (domain.confirm(confirm("self", KnowledgeRelationshipType.RELATED, a, a), listOf(a), emptyList()) as RelationshipDecision.Rejected).code)
        val stale = confirm("stale", KnowledgeRelationshipType.RELATED, a, snapshot("b", "乙", "正文", KnowledgeScope.GLOBAL, null)).copy(first = expected(a).copy(revision = 99))
        assertEquals(KnowledgeRelationshipRejection.STALE_ENDPOINT, (domain.confirm(stale, listOf(a, snapshot("b", "乙", "正文", KnowledgeScope.GLOBAL, null)), emptyList()) as RelationshipDecision.Rejected).code)
        val sensitive = snapshot("s", "敏感", "api key: not-a-real-key", KnowledgeScope.GLOBAL, null)
        assertEquals(KnowledgeRelationshipRejection.HIGH_SENSITIVITY, (domain.confirm(confirm("secret", KnowledgeRelationshipType.RELATED, a, sensitive), listOf(a, sensitive), emptyList()) as RelationshipDecision.Rejected).code)
    }

    @Test fun `active duplicate is rejected without using UI or DAO semantic shortcuts`() {
        val a = snapshot("a", "甲", "正文", KnowledgeScope.GLOBAL, null); val b = snapshot("b", "乙", "正文", KnowledgeScope.GLOBAL, null)
        val decision = domain.confirm(confirm("first", KnowledgeRelationshipType.RELATED, a, b), listOf(a, b), emptyList()) as RelationshipDecision.Confirm
        val relationship = KnowledgeRelationship(KnowledgeRelationshipId("r"), decision.type, decision.from, decision.to, decision.scope, decision.projectId, KnowledgeRelationshipStatus.ACTIVE, decision.at, decision.at, KnowledgeRelationshipIntentId("i"), KnowledgeRelationshipIntentId("i"), KnowledgeRelationshipSuggestionSource.MANUAL)
        val existing = KnowledgeRelationshipSnapshot(relationship, listOf(KnowledgeRelationshipRevision(KnowledgeRelationshipRevisionId("rr"), relationship.id, 1, KnowledgeRelationshipAction.CONFIRM, KnowledgeRelationshipStatus.ACTIVE, KnowledgeRelationshipIntentId("i"), decision.at)))
        assertEquals(KnowledgeRelationshipRejection.DUPLICATE_ACTIVE_EDGE, (domain.confirm(confirm("second", KnowledgeRelationshipType.RELATED, b, a), listOf(a, b), listOf(existing)) as RelationshipDecision.Rejected).code)
    }

    private fun confirm(id: String, type: KnowledgeRelationshipType, first: KnowledgeSnapshot, second: KnowledgeSnapshot) = KnowledgeRelationshipIntent(KnowledgeRelationshipIntentId(id), KnowledgeRelationshipAction.CONFIRM, type = type, first = expected(first), second = expected(second))
    private fun expected(snapshot: KnowledgeSnapshot) = KnowledgeRelationshipEndpointExpectation(snapshot.item.id, snapshot.revisions.maxOf { it.revision }, snapshot.lifecycle.contentHash)
    private fun snapshot(id: String, title: String, body: String, scope: KnowledgeScope, projectId: ProjectId?, status: KnowledgeStatus = KnowledgeStatus.ACTIVE): KnowledgeSnapshot {
        val at = Instant.parse("2026-08-13T00:00:00Z"); val item = KnowledgeItem(KnowledgeItemId(id), title, body, listOf(SourceEvidence(CaptureSourceType.MANUAL_TEXT, at, "manual", emptySet())), CandidateProvenance(CandidateId("c$id"), InvocationId("i$id"), ProviderId.MOCK, "local", 1), at)
        val hash = MemoryDomain.sha256("${title.trim().lowercase()}\n${body.trim().lowercase()}")
        return KnowledgeSnapshot(item, KnowledgeLifecycle(status, scope, projectId, emptySet(), hash, at), listOf(KnowledgeRevision(KnowledgeRevisionId("r$id"), item.id, 1, title, body, status, scope, projectId, emptySet(), hash, at)))
    }
}
