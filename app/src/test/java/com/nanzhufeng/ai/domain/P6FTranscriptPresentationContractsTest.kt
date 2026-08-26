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

    @Test fun `provider answer footer uses assistant owned attempt route rather than current selection`() {
        val node = message("provider", MessageRole.ASSISTANT)
        val attribution = AssistantResponseModelAttribution(
            assistantMessageId = node.id,
            attemptId = NormalChatSendAttemptId("p6f-provider-attempt"),
            providerId = ProviderId.OPENROUTER,
            receiverProviderId = ProviderId.OPENROUTER,
            modelId = "anthropic/claude-opus-5",
            modelDisplayName = "Claude Opus 5",
            recordedAt = Instant.EPOCH,
        )

        val projected = ConversationTranscriptPresentation(MessagePresentationRenderer())
            .render(listOf(node), emptyList(), responseModelAttributions = mapOf(node.id to listOf(attribution)))
            .single()

        assertEquals("Claude Opus 5", projected.metadata.modelSnapshotLabel)
    }

    @Test fun `provider response amount is shown after the model while fallback keeps its estimate label`() {
        val node = message("provider-cost", MessageRole.ASSISTANT)
        val exact = AssistantResponseModelAttribution(
            assistantMessageId = node.id, attemptId = NormalChatSendAttemptId("p6f-cost-exact"),
            providerId = ProviderId.OPENROUTER, receiverProviderId = ProviderId.OPENROUTER,
            modelId = "openai/gpt-5.6-sol", modelDisplayName = "GPT-5.6 Sol", recordedAt = Instant.EPOCH,
            cost = ProviderCost("openrouter-provider-response", "USD", 5_210L), costSource = ConversationCostSource.PROVIDER_RESPONSE,
        )
        val estimated = exact.copy(
            attemptId = NormalChatSendAttemptId("p6f-cost-estimate"),
            cost = ProviderCost("local-calibrated-openrouter-v1", "USD", 5_340L), costSource = ConversationCostSource.LOCAL_ESTIMATE,
        )

        assertEquals("\$0.00521", exact.footerCostLabel())
        assertEquals("≈ \$0.00534（估算）", estimated.footerCostLabel())
        val projected = ConversationTranscriptPresentation(MessagePresentationRenderer())
            .render(listOf(node), emptyList(), responseModelAttributions = mapOf(node.id to listOf(exact)))
            .single()
        assertEquals("\$0.00521", projected.metadata.costLabel)
    }

    @Test fun `hosted DeepSeek search footer stays a model name while route remains persisted`() {
        val node = message("deepseek-qwen-search", MessageRole.ASSISTANT)
        val attribution = AssistantResponseModelAttribution(
            assistantMessageId = node.id,
            attemptId = NormalChatSendAttemptId("p6f-deepseek-qwen-attempt"),
            providerId = ProviderId.DEEPSEEK,
            receiverProviderId = ProviderId.QWEN,
            modelId = "deepseek-v4-pro",
            modelDisplayName = "DeepSeek V4 Pro",
            recordedAt = Instant.EPOCH,
        )

        val projected = ConversationTranscriptPresentation(MessagePresentationRenderer())
            .render(listOf(node), emptyList(), responseModelAttributions = mapOf(node.id to listOf(attribution)))
            .single()

        assertEquals("DeepSeek V4 Pro", projected.metadata.modelSnapshotLabel)
    }

    @Test fun `provider answer footer removes redundant Google family prefix without altering the recorded route`() {
        val node = message("provider-gemini", MessageRole.ASSISTANT)
        val attribution = AssistantResponseModelAttribution(
            assistantMessageId = node.id,
            attemptId = NormalChatSendAttemptId("p6f-gemini-attempt"),
            providerId = ProviderId.OPENROUTER,
            receiverProviderId = ProviderId.OPENROUTER,
            modelId = "google/gemini-3.7-flash",
            modelDisplayName = "Google: Gemini 3.7 Flash",
            recordedAt = Instant.EPOCH,
        )

        val projected = ConversationTranscriptPresentation(MessagePresentationRenderer())
            .render(listOf(node), emptyList(), responseModelAttributions = mapOf(node.id to listOf(attribution)))
            .single()

        assertEquals("Gemini 3.7 Flash", projected.metadata.modelSnapshotLabel)
        assertEquals("Google: Gemini 3.7 Flash", attribution.modelDisplayName)
    }

    @Test fun `provider answer footer keeps the canonical GPT model name without changing its exact id`() {
        val node = message("provider-gpt", MessageRole.ASSISTANT)
        val attribution = AssistantResponseModelAttribution(
            assistantMessageId = node.id,
            attemptId = NormalChatSendAttemptId("p6f-gpt-attempt"),
            providerId = ProviderId.OPENROUTER,
            receiverProviderId = ProviderId.OPENROUTER,
            modelId = "openai/gpt-5.6-terra",
            modelDisplayName = "GPT-5.6 Terra",
            recordedAt = Instant.EPOCH,
        )

        val projected = ConversationTranscriptPresentation(MessagePresentationRenderer())
            .render(listOf(node), emptyList(), responseModelAttributions = mapOf(node.id to listOf(attribution)))
            .single()

        assertEquals("GPT-5.6 Terra", projected.metadata.modelSnapshotLabel)
        assertEquals("openai/gpt-5.6-terra", attribution.modelId)
        assertEquals("GPT-5.6 Terra", attribution.modelDisplayName)
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

    @Test fun `empty partial assistant shows local input-aware preparation copy until the first real chunk`() {
        val user = MessageNode(
            id = MessageNodeId("waiting-user"), conversationId = conversationId, parentMessageId = null, siblingPosition = 0,
            role = MessageRole.USER, content = listOf(ContentBlock.Text("请帮我排查 Android 报错")), createdAt = Instant.EPOCH,
        )
        val waiting = MessageNode(
            id = MessageNodeId("waiting-assistant"), conversationId = conversationId, parentMessageId = user.id, siblingPosition = 0,
            role = MessageRole.ASSISTANT, content = emptyList(), createdAt = Instant.EPOCH, deliveryState = MessageDeliveryState.PARTIAL,
        )
        val firstChunk = waiting.copy(content = listOf(ContentBlock.Text("正式正文")))
        val presenter = ConversationTranscriptPresentation(MessagePresentationRenderer())
        val preview = presenter.render(listOf(user, waiting), emptyList()).last().metadata.waitingPreview
        assertEquals("我先梳理实现目标和现有约束，再给你可执行的答复。", preview?.preface)
        assertEquals("正在准备问题分析", preview?.phase)
        assertNull(presenter.render(listOf(user, firstChunk), emptyList()).last().metadata.waitingPreview)
    }

    private fun message(id: String, role: MessageRole, invocation: InvocationId? = null) = MessageNode(
        id = MessageNodeId(id), conversationId = conversationId, parentMessageId = null, siblingPosition = 0,
        role = role, content = listOf(ContentBlock.Text("持久化本地消息")), createdAt = Instant.EPOCH,
        invocation = invocation?.let(::MessageInvocationReference),
    )
}
