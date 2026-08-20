package com.nanzhufeng.ai.domain

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class P4MLocalActionTraceContractsTest {
    @Test fun `only terminal verified P3C actions are bounded safely sorted and body free`() {
        val conversationId = ConversationId("p4m-current")
        val snapshot = snapshot(conversationId, 14, MessageDeliveryState.PARTIAL)
        val lineages = snapshot.nodes.mapIndexed { index, node -> lineage(conversationId, node, snapshot.nodes[(index + 1) % snapshot.nodes.size], Instant.parse("2026-08-13T10:${"%02d".format(index)}:00Z")) }
        val metadata = ContextSelectionDomain().select(snapshot, null)

        val plan = LocalActionTraceDomain().plan(metadata, snapshot, lineages)

        assertEquals(LOCAL_ACTION_TRACE_METADATA_FORMAT, plan.format)
        assertEquals(12, plan.candidates.size)
        assertEquals("2026-08-13T10:12:00Z", plan.candidates.first().occurredAt.toString())
        assertTrue(plan.candidates.all { it.terminalState == MessageDeliveryState.COMPLETE })
        assertFalse(plan.toString().contains("assistant-body"))
        assertFalse(plan.toString().contains("fixture-local-flagship-v1"))
        assertFalse(plan.toString().contains("p4m-current"))
    }

    @Test fun `preview needs explicit current selections and rejects foreign or unproved actions`() {
        val conversationId = ConversationId("p4m-select")
        val snapshot = snapshot(conversationId, 2)
        val valid = lineage(conversationId, snapshot.nodes[0], snapshot.nodes[1], Instant.parse("2026-08-13T11:00:00Z"))
        val unproved = valid.copy(intentId = ConversationActionIntentId("unproved"), originMessageId = MessageNodeId("missing"))
        val metadata = ContextSelectionDomain().select(snapshot, null)
        val domain = LocalActionTraceDomain()
        val plan = domain.plan(metadata, snapshot, listOf(valid, unproved))

        assertEquals(1, plan.candidates.size)
        val empty = domain.preview(LocalActionTraceRequest(conversationId, emptySet()), metadata, snapshot, listOf(valid, unproved)) as LocalActionTraceResult.Available
        assertTrue(empty.snapshot.entries.isEmpty())
        val selected = domain.preview(LocalActionTraceRequest(conversationId, setOf(plan.candidates.single().selectionId)), metadata, snapshot, listOf(valid, unproved)) as LocalActionTraceResult.Available
        assertEquals(ConversationActionKind.RETRY, selected.snapshot.entries.single().actionKind)
        assertFalse(selected.snapshot.toString().contains("assistant-body"))
        assertEquals(LocalActionTraceRejection.INELIGIBLE_ACTION, (domain.preview(LocalActionTraceRequest(conversationId, setOf("foreign")), metadata, snapshot, listOf(valid)) as LocalActionTraceResult.Rejected).reason)
    }

    @Test fun `use case rejects lineage races instead of mixing a context snapshot`() {
        val conversationId = ConversationId("p4m-race")
        val snapshot = snapshot(conversationId, 2)
        val first = lineage(conversationId, snapshot.nodes[0], snapshot.nodes[1], Instant.parse("2026-08-13T12:00:00Z"))
        val later = first.copy(intentId = ConversationActionIntentId("later"), invocationId = InvocationId("later-invocation"), createdAt = Instant.parse("2026-08-13T12:01:00Z"))
        val result = ReadExplicitLocalActionTraceUseCase(FakeConversationRepository(snapshot), FakeProjectRepository(), RacingActions(first, later)).plan(conversationId)

        assertEquals(LocalActionTraceRejection.STALE_CONTEXT, (result as LocalActionTracePlanResult.Rejected).reason)
    }

    private fun snapshot(id: ConversationId, count: Int, lastState: MessageDeliveryState = MessageDeliveryState.COMPLETE): ConversationSnapshot {
        val nodes = (0 until count).map { index ->
            MessageNode(MessageNodeId("message-$index"), id, null, index, MessageRole.ASSISTANT, listOf(ContentBlock.Text("assistant-body-$index")), Instant.parse("2026-08-13T09:00:00Z"), if (index == count - 1) lastState else MessageDeliveryState.COMPLETE, invocation = MessageInvocationReference(InvocationId("invocation-$index")))
        }
        return ConversationSnapshot(Conversation(id, "L3", null, nodes.last().id, nodes.first().createdAt, nodes.last().createdAt), nodes, ConversationDraft(updatedAt = nodes.last().createdAt))
    }

    private fun lineage(conversationId: ConversationId, created: MessageNode, origin: MessageNode, at: Instant): ConversationAttemptLineage {
        val model = P3CLocalFixtureRegistry.snapshot.models.first()
        return ConversationAttemptLineage(
            invocationId = requireNotNull(created.invocation).invocationId,
            intentId = ConversationActionIntentId("intent-${created.id.value}"), conversationId = conversationId,
            actionKind = ConversationActionKind.RETRY, originMessageId = origin.id, createdMessageId = created.id,
            previousInvocationId = InvocationId("previous-${created.id.value}"),
            selection = ConversationModelSelection(ProviderId.MOCK, model.id, "p3c-local-balanced-fixture", 1, P3CLocalFixtureRegistry.snapshot.id, model.pricing),
            createdAt = at,
        )
    }

    private class FakeConversationRepository(private val snapshot: ConversationSnapshot) : ConversationRepository {
        override fun save(snapshot: ConversationSnapshot) = snapshot
        override fun findById(id: ConversationId) = snapshot.takeIf { it.conversation.id == id }
        override fun listActive() = listOf(snapshot.conversation)
    }
    private class FakeProjectRepository : ProjectRepository {
        override fun apply(intent: ProjectIntent, fingerprint: String, mutate: () -> ProjectSnapshot) = error("unused")
        override fun findById(id: ProjectId): ProjectSnapshot? = null
        override fun list(scope: ProjectListScope) = emptyList<ProjectSnapshot>()
        override fun projectForConversation(conversationId: ConversationId): ProjectId? = null
        override fun knowledgeScope(knowledgeId: KnowledgeItemId): ProjectId? = null
        override fun assignConversation(intent: ProjectIntent, fingerprint: String) = error("unused")
        override fun assignKnowledge(intent: ProjectIntent, fingerprint: String) = error("unused")
    }
    private class RacingActions(private val first: ConversationAttemptLineage, private val later: ConversationAttemptLineage) : ConversationActionRepository {
        private var reads = 0
        override fun applyAction(projection: ConversationRuntimeProjection, started: RuntimeRunStarted, lineage: ConversationAttemptLineage) = error("unused")
        override fun lineageForIntent(intentId: ConversationActionIntentId): ConversationAttemptLineage? = null
        override fun lineageForInvocation(invocationId: InvocationId): ConversationAttemptLineage? = null
        override fun lineagesForConversation(conversationId: ConversationId): List<ConversationAttemptLineage> = if (reads++ == 0) listOf(first) else listOf(first, later)
    }
}
