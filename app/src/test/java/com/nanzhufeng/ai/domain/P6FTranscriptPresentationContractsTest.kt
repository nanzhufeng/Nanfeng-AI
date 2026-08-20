package com.nanzhufeng.ai.domain

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class P6FTranscriptPresentationContractsTest {
    private val conversationId = ConversationId("p6f-transcript")

    @Test fun `local records do not invent provider model or elapsed metadata`() {
        val node = message("local", MessageRole.ASSISTANT)
        val projected = ConversationTranscriptPresentation(MessagePresentationRenderer())
            .render(listOf(node), emptyList())
            .single()

        assertEquals(TranscriptOrigin.LOCAL_RECORD, projected.metadata.origin)
        assertNull(projected.metadata.modelSnapshotLabel)
        assertNull(projected.metadata.workDurationLabel)
        assertTrue(projected.metadata.accessibilityLabel("南枫 AI").contains("模型未知"))
        assertTrue(!projected.metadata.accessibilityLabel("南枫 AI").contains("用时"))
    }

    @Test fun `fixture model is read from its own lineage snapshot not current settings`() {
        val invocation = InvocationId("p6f-fixture-invocation")
        val node = message("fixture", MessageRole.ASSISTANT, invocation)
        val model = P3CLocalFixtureRegistry.snapshot.models.first { it.id == "fixture-local-flagship-v1" }
        val selection = ConversationModelSelection(
            providerId = ProviderId.MOCK,
            modelId = model.id,
            harnessId = "p3c-local-retry-fixture",
            harnessVersion = 1,
            registrySnapshotId = P3CLocalFixtureRegistry.snapshot.id,
            pricing = model.pricing,
        )
        val lineage = ConversationAttemptLineage(
            invocationId = invocation,
            intentId = ConversationActionIntentId("p6f-fixture-intent"),
            conversationId = conversationId,
            actionKind = ConversationActionKind.RETRY,
            originMessageId = MessageNodeId("origin"),
            createdMessageId = node.id,
            previousInvocationId = InvocationId("previous"),
            selection = selection,
            createdAt = Instant.EPOCH,
        )

        val projected = ConversationTranscriptPresentation(MessagePresentationRenderer())
            .render(listOf(node), listOf(lineage))
            .single()

        assertEquals(TranscriptOrigin.LOCAL_FIXTURE, projected.metadata.origin)
        assertTrue(projected.metadata.modelSnapshotLabel!!.contains(selection.modelId))
        assertTrue(projected.metadata.modelSnapshotLabel!!.contains(selection.registrySnapshotId.value))
    }

    @Test fun `assistant duration comes only from its matching persisted invocation run`() {
        val invocation = InvocationId("p6f-duration-invocation")
        val node = message("duration", MessageRole.ASSISTANT, invocation)
        val base = InvocationRecord(
            id = invocation,
            taskId = AiTaskId("p6f-duration-task"),
            providerId = ProviderId.MOCK,
            modelId = "fixture-local-fast-v1",
            harnessVersion = 1,
            completedAt = Instant.parse("2026-08-14T00:00:02Z"),
            status = InvocationStatus.SUCCEEDED,
        )
        val persisted = base.copy(taskRun = base.taskRun.copy(startedAt = Instant.parse("2026-08-14T00:00:00Z")))

        val projected = ConversationTranscriptPresentation(MessagePresentationRenderer())
            .render(listOf(node), emptyList(), mapOf(invocation to persisted))
            .single()

        assertEquals("用时 2 秒", projected.metadata.workDurationLabel)
        assertNull(
            ConversationTranscriptPresentation(MessagePresentationRenderer())
                .render(listOf(message("user", MessageRole.USER)), emptyList(), mapOf(invocation to persisted))
                .single().metadata.workDurationLabel,
        )
    }

    @Test fun `persisted local runtime is an honest model-work duration only for its own assistant message`() {
        val invocation = InvocationId("p6f-runtime-duration")
        val node = message("runtime-duration", MessageRole.ASSISTANT, invocation)
        val runtime = ConversationRuntimeState(
            invocationId = invocation,
            conversationId = conversationId,
            messageId = node.id,
            nextExpectedSequence = 3,
            status = ConversationRuntimeStatus.COMPLETED,
            startedAt = Instant.parse("2026-08-14T00:00:00Z"),
            updatedAt = Instant.parse("2026-08-14T00:00:02Z"),
        )

        val projected = ConversationTranscriptPresentation(MessagePresentationRenderer())
            .render(listOf(node), emptyList(), runtimeByMessage = mapOf(node.id to runtime))
            .single()

        assertEquals("用时 2 秒", projected.metadata.workDurationLabel)
    }

    @Test fun `local calendar dates are message facts rather than a current-session label`() {
        val first = message("first", MessageRole.USER).copy(createdAt = Instant.parse("2026-08-13T23:59:59Z"))
        val second = message("second", MessageRole.ASSISTANT).copy(createdAt = Instant.parse("2026-08-14T00:00:01Z"))
        val projected = ConversationTranscriptPresentation(MessagePresentationRenderer()).render(listOf(first, second), emptyList())
        assertEquals(first.createdAt, projected[0].metadata.createdAt)
        assertEquals(second.createdAt, projected[1].metadata.createdAt)
        assertTrue(projected[0].metadata.createdAt != projected[1].metadata.createdAt)
    }

    private fun message(id: String, role: MessageRole, invocation: InvocationId? = null) = MessageNode(
        id = MessageNodeId(id), conversationId = conversationId, parentMessageId = null, siblingPosition = 0,
        role = role, content = listOf(ContentBlock.Text("持久化本地消息")), createdAt = Instant.EPOCH,
        invocation = invocation?.let(::MessageInvocationReference),
    )
}
