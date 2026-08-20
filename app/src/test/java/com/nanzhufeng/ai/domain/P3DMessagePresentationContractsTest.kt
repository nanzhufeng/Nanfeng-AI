package com.nanzhufeng.ai.domain

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class P3DMessagePresentationContractsTest {
    private val conversationId = ConversationId("p3d-presentation")

    @Test fun `safe markdown supports required blocks and malformed input falls back without loss`() {
        val renderer = MessagePresentationRenderer()
        val source = "# 标题\n\n- 一\n- 二\n\n> 引用\n\n`内联` [链接](https://example.test)\n\n```kotlin\nval x = 1\n```"
        val rendered = renderer.render(listOf(message("a", source))).single().blocks
        assertTrue(rendered.any { it is PresentationBlock.Heading })
        assertTrue(rendered.any { it is PresentationBlock.UnorderedList })
        assertTrue(rendered.any { it is PresentationBlock.Quote })
        assertTrue(rendered.any { it is PresentationBlock.CodeFence && it.language == "kotlin" })
        val paragraph = rendered.filterIsInstance<PresentationBlock.Paragraph>().single()
        assertTrue(paragraph.spans.any { it is InlinePresentation.Code })
        assertTrue(paragraph.spans.any { it is InlinePresentation.Link && it.url == "https://example.test" })

        val malformed = "<script>alert(1)</script>\n```kotlin\n未闭合"
        val fallback = renderer.render(listOf(message("b", malformed))).single().blocks.single() as PresentationBlock.PlainText
        assertEquals(malformed, fallback.raw)
    }

    @Test fun `stable block identity and cache only change for changed persisted content`() {
        val renderer = MessagePresentationRenderer()
        val first = message("one", "第一条")
        val second = message("two", "第二条")
        val initial = renderer.render(listOf(first, second))
        val changedFirst = first.copy(content = listOf(ContentBlock.Text("第一条增量")))
        val updated = renderer.render(listOf(changedFirst, second))
        assertEquals(initial[1].blocks.single().identity, updated[1].blocks.single().identity)
        assertSame(initial[1].blocks.single(), updated[1].blocks.single())
        assertFalse(initial[0].blocks.single() === updated[0].blocks.single())
        assertEquals(2, renderer.cachedBlockCount())
    }

    @Test fun `tables and raw https links become structured readable blocks without losing ordinary punctuation`() {
        val source = """
            ## 估值对照

            | 指标 | 当前水平 |
            | --- | --- |
            | GAAP 滚动 PE | 约 287 倍 |
            | 自由现金流收益率 | 约 0.5% |

            原始来源 https://www.example.com/report.
        """.trimIndent()
        val blocks = MessagePresentationRenderer().render(listOf(message("table", source))).single().blocks
        val table = blocks.filterIsInstance<PresentationBlock.Table>().single()
        assertEquals(2, table.headers.size)
        assertEquals(2, table.rows.size)
        val paragraph = blocks.filterIsInstance<PresentationBlock.Paragraph>().single()
        assertTrue(paragraph.spans.any { it is InlinePresentation.Link && it.label == "example.com" && it.url == "https://www.example.com/report" })
        assertTrue(paragraph.spans.any { it is InlinePresentation.Text && it.value.endsWith(".") })
    }

    @Test fun `deterministic long fixture has stable identities and current path remains isolated`() {
        val messages = DeterministicLongConversationFixture.messages(conversationId)
        assertEquals(DeterministicLongConversationFixture.MESSAGE_COUNT, messages.size)
        assertEquals("p3d-fixture-message-000", messages.first().id.value)
        assertEquals(messages[messages.lastIndex - 1].id, messages.last().parentMessageId)
        val conversation = Conversation(conversationId, "长会话", currentLeafMessageId = messages.last().id, createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH)
        assertEquals(DeterministicLongConversationFixture.MESSAGE_COUNT, MessageTree(conversation, messages).contextPath().size)
        val presented = MessagePresentationRenderer().render(messages)
        assertEquals(presented.size, presented.map(ConversationListVirtualizationContract::itemKey).distinct().size)
        assertTrue(presented.all { ConversationListVirtualizationContract.contentType(it) in setOf("USER", "ASSISTANT") })
    }

    @Test fun `safe terminal errors expose only allowed local recovery actions`() {
        val partial = message("error", "部分输出")
        val state = ConversationRuntimeState(InvocationId("i"), conversationId, partial.id, 3, ConversationRuntimeStatus.FAILED, Instant.EPOCH, Instant.EPOCH, safeErrorCode = "LOCAL_FIXTURE_FAILURE")
        val recoverable = ConversationRecoveryPresenter.present(state, partial)!!
        assertTrue(recoverable.canContinue)
        assertTrue(recoverable.canRetry)
        val auth = ConversationRecoveryPresenter.present(state.copy(safeErrorCode = "AUTH_FAILED"), partial)!!
        assertFalse(auth.canContinue)
        assertFalse(auth.canRetry)
        val noOutput = partial.copy(content = emptyList(), deliveryState = MessageDeliveryState.FAILED)
        val noOutputState = state.copy(messageId = noOutput.id)
        assertFalse(ConversationRecoveryPresenter.present(noOutputState, noOutput)!!.canContinue)
    }

    private fun message(id: String, text: String) = MessageNode(
        id = MessageNodeId(id), conversationId = conversationId, parentMessageId = null, siblingPosition = 0,
        role = MessageRole.ASSISTANT, content = listOf(ContentBlock.Text(text)), createdAt = Instant.EPOCH,
    )
}
