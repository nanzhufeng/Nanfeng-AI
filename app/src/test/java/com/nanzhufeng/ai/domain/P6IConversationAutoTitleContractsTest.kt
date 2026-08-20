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

    @Test fun `first local message produces a compact meaningful local title`() {
        assertEquals("Android 设置页面重新规划", ConversationAutoTitle.fromText("请帮我把 Android 设置页面重新规划一下，重点是层级清晰。"))
        assertEquals("链接：example.com", ConversationAutoTitle.fromText("https://www.example.com/a/very/long/path"))
        assertEquals("图片对话", ConversationAutoTitle.titleForFirstMessage(
            snapshot(autoTitlePending = true),
            ConversationDraft(attachments = listOf(image()), updatedAt = clock.instant()),
        ))
    }

    @Test fun `title is written once with the first message and never overwrites a manual or imported title`() {
        val created = ConversationTreeService(clock).create(autoTitlePending = true)
        val drafts = FakeDrafts(ConversationDraft("请帮我整理 Android 设置页面层级", updatedAt = clock.instant()))
        val submitted = SubmitConversationDraftUseCase(FakeConversations(created), drafts, ConversationTreeService(clock))
            .execute(created.conversation.id) as ConversationDraftSubmissionResult.Submitted
        assertEquals("整理 Android 设置页面层级", submitted.snapshot.conversation.title)
        assertFalse(submitted.snapshot.conversation.autoTitlePending)
        assertEquals(2L, submitted.snapshot.conversation.revision)

        val manuallyNamed = created.copy(conversation = created.conversation.copy(title = "我的固定标题", autoTitlePending = false))
        assertEquals(null, ConversationAutoTitle.titleForFirstMessage(manuallyNamed, drafts.loadDraft(created.conversation.id)!!))
        val importedMessage = userMessage(created.conversation.id)
        val importedWithMessages = created.copy(
            conversation = created.conversation.copy(currentLeafMessageId = importedMessage.id),
            nodes = listOf(importedMessage),
        )
        assertEquals(null, ConversationAutoTitle.titleForFirstMessage(importedWithMessages, drafts.loadDraft(created.conversation.id)!!))
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
