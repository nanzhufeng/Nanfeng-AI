package com.nanzhufeng.ai.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

@RunWith(RobolectricTestRunner::class)
class ConversationExchangeExportContractsTest {
    private val now = Instant.parse("2026-08-20T00:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)

    @Test fun `active standalone text conversation becomes a strict P6 exchange package`() {
        val snapshot = textSnapshot()
        val useCase = ExportConversationExchangeUseCase(repository(snapshot), "0.3.0-test", clock) { "3export-conversation-test" }

        val candidate = useCase.prepare(snapshot.conversation.id)
        assertTrue("candidate=$candidate", candidate is ConversationExchangePreparation.Prepared)
        val prepared = candidate as ConversationExchangePreparation.Prepared
        val output = createTempFile("p6a-conversation", ".nfai-exchange")
        try {
            val result = NfaiExchangeV1Gateway.export(prepared.snapshot, output)
            assertTrue("export=$result", result is NfaiExchangeResult.Exported)
            val preflight = NfaiExchangeV1Gateway.preflight(output) as NfaiExchangeResult.Preflighted
            assertEquals(1, preflight.value.conversationCount)
            assertEquals(0, preflight.value.projectCount)
            assertEquals(0, preflight.value.knowledgeCount)
        } finally {
            output.delete()
        }
    }

    @Test fun `attachments drafts projects and temporary surfaces fail before an exchange file exists`() {
        val base = textSnapshot()
        val blocked = listOf(
            base.copy(draft = base.draft.copy(text = "未发送草稿")),
            base.copy(conversation = base.conversation.copy(projectId = "project-1")),
            base.copy(conversation = base.conversation.copy(surface = ConversationSurface.WORK)),
            base.copy(nodes = base.nodes.map { it.copy(content = listOf(ContentBlock.ToolResult("local", "safe")), role = MessageRole.TOOL) }),
        )
        blocked.forEach { snapshot ->
            val result = ExportConversationExchangeUseCase(repository(snapshot), "0.3.0-test", clock) { "export-conversation-test" }.prepare(snapshot.conversation.id)
            assertTrue("result=$result", result is ConversationExchangePreparation.Rejected)
        }
    }

    private fun textSnapshot(): ConversationSnapshot {
        val conversationId = ConversationId("1conversation-export-test")
        val messageId = MessageNodeId("2message-export-test")
        return ConversationSnapshot(
            conversation = Conversation(conversationId, "跨端文本", currentLeafMessageId = messageId, createdAt = now, updatedAt = now),
            nodes = listOf(MessageNode(messageId, conversationId, null, 0, MessageRole.USER, listOf(ContentBlock.Text("非敏感文本")), now)),
            draft = ConversationDraft(updatedAt = now),
        )
    }

    private fun repository(snapshot: ConversationSnapshot) = object : ConversationRepository {
        override fun save(snapshot: ConversationSnapshot) = snapshot
        override fun findById(id: ConversationId) = snapshot.takeIf { it.conversation.id == id }
        override fun listActive() = listOf(snapshot.conversation)
    }
}
