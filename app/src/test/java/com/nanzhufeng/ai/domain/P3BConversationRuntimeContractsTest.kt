package com.nanzhufeng.ai.domain

import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class P3BConversationRuntimeContractsTest {
    private val now = Instant.parse("2026-08-12T16:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)
    private val machine = ConversationRuntimeStateMachine(clock)
    private val safe = AiRuntimeSecurityMetadata("LOCAL_DETERMINISTIC_FIXTURE")

    @Test
    fun `ordered fixture projects partial assistant then completes without provider payload`() {
        val base = ConversationTreeService(clock).create("运行合同")
        val invocation = InvocationId("runtime-invocation")
        val message = MessageNodeId("runtime-message")
        val started = machine.apply(base, null, RuntimeRunStarted(AiRuntimeEventId("e0"), invocation, base.conversation.id, message, 0, now, safe))
        val delta = machine.apply(started.snapshot, started.state, RuntimeContentDelta(AiRuntimeEventId("e1"), invocation, base.conversation.id, message, 1, now, "部分", safe))
        val checkpoint = machine.apply(delta.snapshot, delta.state, RuntimeCheckpoint(AiRuntimeEventId("e2"), invocation, base.conversation.id, message, 2, now, 3, safe))
        val completed = machine.apply(checkpoint.snapshot, checkpoint.state, RuntimeCompleted(AiRuntimeEventId("e3"), invocation, base.conversation.id, message, 3, now, safe))

        val node = completed.snapshot.nodes.single()
        assertEquals(MessageDeliveryState.COMPLETE, node.deliveryState)
        assertEquals("部分", (node.content.single() as ContentBlock.Text).text)
        assertEquals(ConversationRuntimeStatus.COMPLETED, completed.state.status)
        assertTrue(!safe.providerPayloadRetained && !safe.networkRequestConstructed)
    }

    @Test
    fun `terminal and ordering rules preserve partial result rather than reporting success`() {
        val base = ConversationTreeService(clock).create("停止合同")
        val invocation = InvocationId("stop-invocation")
        val message = MessageNodeId("stop-message")
        val started = machine.apply(base, null, RuntimeRunStarted(AiRuntimeEventId("s0"), invocation, base.conversation.id, message, 0, now, safe))
        assertThrows(IllegalArgumentException::class.java) {
            machine.apply(started.snapshot, started.state, RuntimeCompleted(AiRuntimeEventId("s2"), invocation, base.conversation.id, message, 2, now, safe))
        }
        val partial = machine.apply(started.snapshot, started.state, RuntimeContentDelta(AiRuntimeEventId("s1"), invocation, base.conversation.id, message, 1, now, "已保留", safe))
        val cancelled = machine.apply(partial.snapshot, partial.state, RuntimeCancelled(AiRuntimeEventId("s2"), invocation, base.conversation.id, message, 2, now, safe))
        assertEquals(MessageDeliveryState.CANCELLED, cancelled.snapshot.nodes.single().deliveryState)
        assertEquals("已保留", (cancelled.snapshot.nodes.single().content.single() as ContentBlock.Text).text)
        assertThrows(IllegalArgumentException::class.java) {
            machine.apply(cancelled.snapshot, cancelled.state, RuntimeContentDelta(AiRuntimeEventId("s3"), invocation, base.conversation.id, message, 3, now, "污染", safe))
        }
    }

    @Test
    fun `fixture has stable deterministic ordered IDs and no transport capability`() {
        val base = ConversationTreeService(clock).create("fixture")
        val state = machine.apply(base, null, RuntimeRunStarted(AiRuntimeEventId("f0"), InvocationId("fixture-invocation"), base.conversation.id, MessageNodeId("fixture-message"), 0, now, safe)).state
        val events = DeterministicFixtureStreamingAdapter(clock).events(state)
        assertEquals(listOf(1L, 2L, 3L, 4L, 5L), events.map { it.sequence })
        assertEquals(events.map { it.eventId }, DeterministicFixtureStreamingAdapter(clock).events(state).map { it.eventId })
        assertTrue(events.all { !it.security.credentialBytesRead && !it.security.networkRequestConstructed })
    }

    @Test
    fun `failed event is irreversible and a non current branch cannot be projected`() {
        val service = ConversationTreeService(clock)
        val base = service.create("失败合同")
        val invocation = InvocationId("failed-invocation")
        val message = MessageNodeId("failed-message")
        val started = machine.apply(base, null, RuntimeRunStarted(AiRuntimeEventId("x0"), invocation, base.conversation.id, message, 0, now, safe))
        val failed = machine.apply(started.snapshot, started.state, RuntimeFailed(AiRuntimeEventId("x1"), invocation, base.conversation.id, message, 1, now, "LOCAL_FIXTURE_FAILED", safe))
        assertEquals(ConversationRuntimeStatus.FAILED, failed.state.status)
        assertEquals(MessageDeliveryState.FAILED, failed.snapshot.nodes.single().deliveryState)
        assertThrows(IllegalArgumentException::class.java) {
            machine.apply(failed.snapshot, failed.state, RuntimeCancelled(AiRuntimeEventId("x2"), invocation, base.conversation.id, message, 2, now, safe))
        }
        val switched = service.append(failed.snapshot, AppendMessageRequest(MessageRole.ASSISTANT, listOf(ContentBlock.Text("兄弟分支"))))
        assertThrows(IllegalArgumentException::class.java) {
            machine.apply(switched, started.state, RuntimeContentDelta(AiRuntimeEventId("x3"), invocation, base.conversation.id, message, 1, now, "不应污染", safe))
        }
    }
}
