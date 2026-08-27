package com.nanzhufeng.ai.domain

import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class P6IConversationAutoTitleContractsTest {
    private val clock = Clock.fixed(Instant.parse("2026-08-15T00:00:00Z"), ZoneOffset.UTC)

    @Test fun `first completed assistant reply never writes a local heading as the conversation title`() {
        val tree = ConversationTreeService(clock)
        val user = tree.append(snapshot(autoTitlePending = true), AppendMessageRequest(MessageRole.USER, listOf(ContentBlock.Attachment(image()))))
        val titled = tree.append(user, AppendMessageRequest(MessageRole.ASSISTANT, listOf(ContentBlock.Text("核心结论"))))
        assertEquals(ConversationAutoTitle.NEW_CONVERSATION_TITLE, titled.conversation.title)
        assertTrue(titled.conversation.autoTitlePending)

        val responseTitled = tree.append(
            tree.append(snapshot(autoTitlePending = true), AppendMessageRequest(MessageRole.USER, listOf(ContentBlock.Text("请分析 Tesla")))),
            AppendMessageRequest(MessageRole.ASSISTANT, listOf(ContentBlock.Text("## 特斯拉价值投资框架\n\n正文"))),
        )
        assertEquals(ConversationAutoTitle.NEW_CONVERSATION_TITLE, responseTitled.conversation.title)
        assertTrue(responseTitled.conversation.autoTitlePending)
    }

    @Test fun `assistant section headings and generic openings are never promoted into a title`() {
        val tree = ConversationTreeService(clock)
        val user = tree.append(
            snapshot(autoTitlePending = true),
            AppendMessageRequest(MessageRole.USER, listOf(ContentBlock.Text("请核对跨境投资监管变化，并评估美股配置的影响。"))),
        )
        val titled = tree.append(
            user,
            AppendMessageRequest(
                MessageRole.ASSISTANT,
                listOf(ContentBlock.Text("说明（先讲清边界）\n\n这里先核实信息来源。\n\n## 跨境投资监管与美股配置\n\n再给出风险判断。")),
            ),
        )

        assertEquals(ConversationAutoTitle.NEW_CONVERSATION_TITLE, titled.conversation.title)
        assertTrue(titled.conversation.autoTitlePending)
    }

    @Test fun `title waits for the dedicated refiner and never overwrites a manual or imported title`() {
        val created = ConversationTreeService(clock).create(autoTitlePending = true)
        val drafts = FakeDrafts(ConversationDraft("请帮我整理 Android 设置页面层级", updatedAt = clock.instant()))
        val submitted = SubmitConversationDraftUseCase(FakeConversations(created), drafts, ConversationTreeService(clock))
            .execute(created.conversation.id) as ConversationDraftSubmissionResult.Submitted
        assertEquals(ConversationAutoTitle.NEW_CONVERSATION_TITLE, submitted.snapshot.conversation.title)
        assertTrue(submitted.snapshot.conversation.autoTitlePending)
        val titled = ConversationTreeService(clock).append(
            submitted.snapshot,
            AppendMessageRequest(MessageRole.ASSISTANT, listOf(ContentBlock.Text("# Android 设置页面重新规划"))),
        )
        assertEquals(ConversationAutoTitle.NEW_CONVERSATION_TITLE, titled.conversation.title)
        assertTrue(titled.conversation.autoTitlePending)

        val manuallyNamed = created.copy(conversation = created.conversation.copy(title = "我的固定标题", autoTitlePending = false))
        val manualReply = ConversationTreeService(clock).append(
            manuallyNamed,
            AppendMessageRequest(MessageRole.ASSISTANT, listOf(ContentBlock.Text("# 不应覆盖的自动标题"))),
        )
        assertEquals("我的固定标题", manualReply.conversation.title)
        val importedMessage = userMessage(created.conversation.id)
        val importedWithMessages = created.copy(
            conversation = created.conversation.copy(currentLeafMessageId = importedMessage.id),
            nodes = listOf(importedMessage),
        )
        assertEquals(null, ConversationAutoTitle.titleForFirstCompletedAssistantReply(importedWithMessages, MessageNodeId("assistant")))
    }

    @Test fun `stream completion leaves the title pending for the dedicated refiner`() {
        val tree = ConversationTreeService(clock)
        val user = tree.append(snapshot(autoTitlePending = true), AppendMessageRequest(MessageRole.USER, listOf(ContentBlock.Text("请分析 Tesla"))))
        val machine = ConversationRuntimeStateMachine(clock)
        val invocation = InvocationId("title-stream")
        val assistantId = MessageNodeId("assistant-stream")
        val security = AiRuntimeSecurityMetadata(source = "LOCAL_TEST")
        val started = machine.apply(user, null, RuntimeRunStarted(AiRuntimeEventId("start"), invocation, user.conversation.id, assistantId, 0, clock.instant(), security, user.conversation.currentLeafMessageId))
        val received = machine.apply(started.snapshot, started.state, RuntimeContentDelta(AiRuntimeEventId("delta"), invocation, user.conversation.id, assistantId, 1, clock.instant(), "## 特斯拉投资分析", security))
        val completed = machine.apply(received.snapshot, received.state, RuntimeCompleted(AiRuntimeEventId("done"), invocation, user.conversation.id, assistantId, 2, clock.instant(), security))
        assertEquals(ConversationAutoTitle.NEW_CONVERSATION_TITLE, completed.snapshot.conversation.title)
        assertTrue(completed.snapshot.conversation.autoTitlePending)
    }

    @Test fun `attachment opening uses only the original AI reply and later completion keeps the title source retryable`() {
        val tree = ConversationTreeService(clock)
        val openingUser = tree.append(snapshot(autoTitlePending = true), AppendMessageRequest(MessageRole.USER, listOf(ContentBlock.Attachment(image()))))
        val openingAssistant = tree.append(openingUser, AppendMessageRequest(MessageRole.ASSISTANT, listOf(ContentBlock.Text("我会先分析图片中的主要内容。"))))
        val laterUser = tree.append(openingAssistant, AppendMessageRequest(MessageRole.USER, listOf(ContentBlock.Text("请继续说明。"))))
        val laterAssistant = tree.append(laterUser, AppendMessageRequest(MessageRole.ASSISTANT, listOf(ContentBlock.Text("补充了更详细的判断。"))))

        assertEquals(
            ConversationTitleSource("", "我会先分析图片中的主要内容。"),
            laterAssistant.openingTitleSource(),
        )
        assertTrue(laterAssistant.conversation.autoTitlePending)
    }

    @Test fun `new conversation use case marks only locally created empty conversations as eligible`() {
        val result = CreateConversationUseCase(ConversationTreeService(clock), FakeConversations()).execute()
            as ConversationMutationResult.Saved
        assertEquals(ConversationAutoTitle.NEW_CONVERSATION_TITLE, result.snapshot.conversation.title)
        assertTrue(result.snapshot.conversation.autoTitlePending)
    }

    private fun snapshot(autoTitlePending: Boolean) = ConversationSnapshot(
        Conversation(ConversationId("conversation"), ConversationAutoTitle.NEW_CONVERSATION_TITLE, createdAt = clock.instant(), updatedAt = clock.instant(), autoTitlePending = autoTitlePending),
        emptyList(),
        ConversationDraft(updatedAt = clock.instant()),
    )

    private fun image() = ConversationAttachmentReference(AttachmentId("image"), "image/png", "first.png", 3, "a".repeat(64))

    private fun userMessage(conversationId: ConversationId) = MessageNode(
        id = MessageNodeId("user"), conversationId = conversationId, parentMessageId = null, siblingPosition = 0,
        role = MessageRole.USER, content = listOf(ContentBlock.Text("已导入正文")), createdAt = clock.instant(),
    )

    private class FakeConversations(private var value: ConversationSnapshot? = null) : ConversationRepository {
        override fun save(snapshot: ConversationSnapshot): ConversationSnapshot = snapshot.also { value = it }
        override fun findById(id: ConversationId): ConversationSnapshot? = value
        override fun listActive(): List<Conversation> = value?.let { listOf(it.conversation) }.orEmpty()
    }

    private class FakeDrafts(private val draft: ConversationDraft) : ConversationDraftRepository {
        override fun loadDraft(conversationId: ConversationId): ConversationDraft = draft
        override fun saveDraft(conversationId: ConversationId, draft: ConversationDraft): ConversationDraft = draft
        override fun submitDraft(snapshotWithClearedDraft: ConversationSnapshot, expectedDraft: ConversationDraft) = ConversationDraftSubmissionResult.Submitted(snapshotWithClearedDraft)
    }
}
