package com.nanzhufeng.ai.domain

import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class P3AConversationDomainContractsTest {
    private val clock = Clock.fixed(Instant.parse("2026-08-12T14:00:00Z"), ZoneOffset.UTC)

    @Test
    fun `editing a historical user message forks immutable sibling and isolates current context`() {
        val service = ConversationTreeService(clock)
        val initial = service.create("分支合同")
        val user = service.append(initial, message(MessageRole.USER, "原始问题"))
        val originalUserId = user.conversation.currentLeafMessageId!!
        val answered = service.append(user, message(MessageRole.ASSISTANT, "原始回答"))
        val originalLeaf = answered.conversation.currentLeafMessageId!!

        val edited = service.editUserMessage(answered, originalUserId, listOf(ContentBlock.Text("修订问题")))
        val editedLeaf = edited.conversation.currentLeafMessageId!!

        assertEquals("原始问题", MessageTree(edited.conversation, edited.nodes).node(originalUserId).content.single().let { (it as ContentBlock.Text).text })
        assertEquals(listOf("修订问题"), MessageTree(edited.conversation, edited.nodes).contextPath().map { (it.content.single() as ContentBlock.Text).text })
        assertEquals(originalUserId, MessageTree(edited.conversation, edited.nodes).node(editedLeaf).revision.revisesMessageId)

        val switched = service.switchBranch(edited, originalLeaf)
        assertEquals(listOf("原始问题", "原始回答"), MessageTree(switched.conversation, switched.nodes).contextPath().map { (it.content.single() as ContentBlock.Text).text })
        assertEquals(2, MessageTree(switched.conversation, switched.nodes).currentBranch()!!.path.size)
    }

    @Test
    fun `message role and partial output invariants reject invalid future tool or user states`() {
        val conversation = ConversationTreeService(clock).create("角色合同").conversation
        assertThrows(IllegalArgumentException::class.java) {
            MessageNode(MessageNodeId.new(), conversation.id, null, 0, MessageRole.TOOL, listOf(ContentBlock.Text("not a tool")), clock.instant())
        }
        assertThrows(IllegalArgumentException::class.java) {
            MessageNode(MessageNodeId.new(), conversation.id, null, 0, MessageRole.USER, listOf(ContentBlock.Text("partial")), clock.instant(), MessageDeliveryState.PARTIAL)
        }
    }

    @Test
    fun `draft attachment and invocation links retain only local reference contracts`() {
        val service = ConversationTreeService(clock)
        val created = service.create("关联合同", projectId = "project-reserved")
        val attachment = AttachmentReference("attachments/v1/a", "image/png", id = AttachmentId("attachment-a"), byteCount = 3, sha256 = "a".repeat(64))
        val withAssistant = service.append(
            service.append(created, message(MessageRole.USER, "请看附件")),
            AppendMessageRequest(
                role = MessageRole.ASSISTANT,
                content = listOf(ContentBlock.Text("已保存部分结果"), ContentBlock.Attachment(attachment)),
                deliveryState = MessageDeliveryState.PARTIAL,
                invocation = MessageInvocationReference(InvocationId("invocation-safe-link")),
                checkpoint = MessageCheckpoint(3, 4),
            ),
        )

        val node = MessageTree(withAssistant.conversation, withAssistant.nodes).contextPath().last()
        assertEquals("project-reserved", withAssistant.conversation.projectId)
        assertEquals(InvocationId("invocation-safe-link"), node.invocation!!.invocationId)
        assertEquals(attachment.toConversationReference(), (node.content[1] as ContentBlock.Attachment).attachment)
        assertTrue(node.checkpoint!!.lastPersistedSequence == 3L)
    }

    private fun message(role: MessageRole, text: String) = AppendMessageRequest(role, listOf(ContentBlock.Text(text)))
}
