package com.nanzhufeng.ai.domain

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class P3IConversationRealTextExecutionContractsTest {
    private val createdAt = Instant.parse("2026-08-15T12:00:00Z")

    @Test fun `request locks all cross owner IDs and only safe persisted fields`() {
        val request = request()
        val prepared = ConversationRealTextExecutionContract.prepared(request)

        assertEquals(ConversationRealTextExecutionState.PREPARED, prepared.state)
        assertEquals("conversation-real-text-execution-v1", CONVERSATION_REAL_TEXT_EXECUTION_PROTOCOL)
        assertTrue(ConversationRealTextExecutionRecord::class.java.declaredFields.map { it.name }.none {
            it.contains("prompt", true) || it.contains("response", true) || it.contains("credential", true) || it.contains("secret", true) ||
                it.contains("authorization", true) || it.contains("uri", true) || it.contains("path", true) ||
                it.contains("attachment", true) || it.contains("usage", true) || it.contains("cost", true)
        })
    }

    @Test fun `only legal running and terminal transitions are accepted`() {
        val prepared = ConversationRealTextExecutionContract.prepared(request())
        val running = (ConversationRealTextExecutionContract.transition(
            prepared, ConversationRealTextExecutionState.RUNNING, createdAt.plusSeconds(1),
        ) as ConversationRealTextExecutionTransitionResult.Updated).record
        val succeeded = (ConversationRealTextExecutionContract.transition(
            running, ConversationRealTextExecutionState.SUCCEEDED, createdAt.plusSeconds(2),
        ) as ConversationRealTextExecutionTransitionResult.Updated).record

        assertEquals(ConversationRealTextExecutionState.SUCCEEDED, succeeded.state)
        assertTrue(ConversationRealTextExecutionContract.transition(
            succeeded, ConversationRealTextExecutionState.FAILED, createdAt.plusSeconds(3), "NETWORK_FAILURE",
        ) is ConversationRealTextExecutionTransitionResult.Rejected)
        assertTrue(ConversationRealTextExecutionContract.transition(
            prepared, ConversationRealTextExecutionState.SUCCEEDED, createdAt.plusSeconds(1),
        ) is ConversationRealTextExecutionTransitionResult.Rejected)
    }

    @Test fun `failed and cancelled states require safe codes and disabled transport has no network result`() {
        val prepared = ConversationRealTextExecutionContract.prepared(request())
        assertTrue(ConversationRealTextExecutionContract.transition(
            prepared, ConversationRealTextExecutionState.FAILED, createdAt.plusSeconds(1), "HTTP_429",
        ) is ConversationRealTextExecutionTransitionResult.Updated)
        assertEquals(
            ConversationRealTextTransportResult.DisabledNoNetwork,
            DisabledConversationRealTextTransport.execute(ConversationRealTextExecutionTransportHandle(
                prepared.request.executionId, prepared.request.invocationId, prepared.request.attemptId, prepared.request.requestFingerprint,
            )),
        )
    }

    private fun request() = ConversationRealTextExecutionRequest(
        executionId = ConversationRealTextExecutionId("execution-1"), idempotencyKey = "intent-1",
        requestFingerprint = "a".repeat(64), conversationId = ConversationId("conversation-1"),
        userMessageId = MessageNodeId("user-1"), assistantMessageId = MessageNodeId("assistant-1"),
        invocationId = InvocationId("invocation-1"), attemptId = ProviderAttemptId("attempt-1"), createdAt = createdAt,
    )
}
