package com.nanzhufeng.ai.domain

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class P3HConversationAttemptHistoryContractsTest {
    @Test fun `local attempts are safely filtered sorted and only matching runtime exposes tokens`() {
        val conversationId = ConversationId("p3h-conversation")
        val older = assistant(conversationId, "older", "older-invocation", MessageDeliveryState.CANCELLED, "2026-08-13T08:00:00Z", 0)
        val newer = assistant(conversationId, "newer", "newer-invocation", MessageDeliveryState.PARTIAL, "2026-08-13T09:00:00Z", 1)
        val snapshot = ConversationSnapshot(
            Conversation(conversationId, "P3-H", null, newer.id, Instant.parse("2026-08-13T07:00:00Z"), Instant.parse("2026-08-13T09:00:00Z")),
            listOf(older, newer), ConversationDraft(updatedAt = newer.createdAt),
        )
        val projection = ConversationAttemptHistoryProjection().project(
            snapshot,
            listOf(
                lineage(conversationId, older, ConversationActionKind.RETRY, older.createdAt),
                lineage(conversationId, newer, ConversationActionKind.CHANGE_MODEL, newer.createdAt),
                lineage(ConversationId("another-conversation"), newer, ConversationActionKind.CONTINUE, newer.createdAt),
                lineage(conversationId, newer.copy(invocation = MessageInvocationReference(InvocationId("wrong-invocation"))), ConversationActionKind.CONTINUE, newer.createdAt),
            ),
            ConversationRuntimeState(
                InvocationId("newer-invocation"), conversationId, newer.id, 4, ConversationRuntimeStatus.STREAMING,
                newer.createdAt, newer.createdAt, inputTokens = 12, outputTokens = 21,
            ),
        )

        assertEquals(listOf(ConversationActionKind.CHANGE_MODEL, ConversationActionKind.RETRY), projection.map { it.actionKind })
        assertEquals(MessageDeliveryState.PARTIAL, projection.first().deliveryState)
        assertEquals(12L, projection.first().inputTokens)
        assertEquals(21L, projection.first().outputTokens)
        assertNull(projection.last().inputTokens)
        assertNull(projection.last().outputTokens)
        assertTrue(projection.all { it.modelId.startsWith("fixture-local-") && it.harnessLabel.startsWith("p3c-local-") })
    }

    @Test fun `foreign provider or non assistant lineage never becomes local history`() {
        val conversationId = ConversationId("p3h-filter")
        val assistant = assistant(conversationId, "message", "invocation", MessageDeliveryState.COMPLETE, "2026-08-13T10:00:00Z", 0)
        val snapshot = ConversationSnapshot(
            Conversation(conversationId, "过滤", null, assistant.id, assistant.createdAt, assistant.createdAt), listOf(assistant), ConversationDraft(updatedAt = assistant.createdAt),
        )
        val local = lineage(conversationId, assistant, ConversationActionKind.RETRY, assistant.createdAt)
        val unsafe = local.copy(selection = local.selection.copy(providerId = ProviderId.OPENROUTER))

        assertEquals(emptyList<ConversationAttemptHistoryItem>(), ConversationAttemptHistoryProjection().project(snapshot, listOf(unsafe), null))
    }

    private fun assistant(
        conversationId: ConversationId, messageId: String, invocationId: String, state: MessageDeliveryState, createdAt: String, siblingPosition: Int,
    ) = MessageNode(
        id = MessageNodeId(messageId), conversationId = conversationId, parentMessageId = null, siblingPosition = siblingPosition,
        role = MessageRole.ASSISTANT, content = listOf(ContentBlock.Text("本地输出")), createdAt = Instant.parse(createdAt),
        deliveryState = state, invocation = MessageInvocationReference(InvocationId(invocationId)),
    )

    private fun lineage(
        conversationId: ConversationId, assistant: MessageNode, action: ConversationActionKind, createdAt: Instant,
    ): ConversationAttemptLineage {
        val model = P3CLocalFixtureRegistry.snapshot.models.first()
        return ConversationAttemptLineage(
            invocationId = requireNotNull(assistant.invocation).invocationId,
            intentId = ConversationActionIntentId("intent-${assistant.id.value}-$action"),
            conversationId = conversationId,
            actionKind = action,
            originMessageId = MessageNodeId("origin-${assistant.id.value}"),
            createdMessageId = assistant.id,
            previousInvocationId = InvocationId("previous-${assistant.id.value}"),
            selection = ConversationModelSelection(
                ProviderId.MOCK, model.id, "p3c-local-balanced-fixture", 1,
                P3CLocalFixtureRegistry.snapshot.id, model.pricing,
            ),
            createdAt = createdAt,
        )
    }
}
