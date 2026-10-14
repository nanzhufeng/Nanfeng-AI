package com.nanzhufeng.ai.domain

import java.time.Instant
import org.junit.Assert.*
import org.junit.Test

class ReplyTextEmphasisTest {
    private fun blocks(text: String): List<PresentationBlock> = MessagePresentationRenderer().render(listOf(MessageNode(
        id = MessageNodeId("emphasis"), conversationId = ConversationId("emphasis"), parentMessageId = null,
        siblingPosition = 0, role = MessageRole.ASSISTANT, content = listOf(ContentBlock.Text(text)), createdAt = Instant.EPOCH,
    ))).single().blocks

    @Test fun `only short authored priorities and one caution are colored in reading order`() {
        val selected = replyEmphasisSelections(blocks("建议**优先保存**，**保留原文**，**第三处**。\n\n## 风险\n\n请**检查余额**，**不要重发**。\n\n## 建议\n\n这里是**普通重点**。"))
        assertEquals(listOf("优先保存", "保留原文", "检查余额"), selected.map { it.span.value })
        assertEquals(listOf(ReplyEmphasisTone.IMPORTANT, ReplyEmphasisTone.IMPORTANT, ReplyEmphasisTone.CAUTION), selected.map { it.tone })
    }

    @Test fun `heading quote table code and link content never consume prose emphasis`() {
        val selected = replyEmphasisSelections(blocks("## **标题重点**\n\n> **引用内容**\n\n| 项目 | 状态 |\n| --- | --- |\n| **表格内容** | 保留 |\n\n**`code`** **[来源](https://example.com)** **\$x+y\$** **<script>**\n\n这里是**正常重点**。"))
        assertEquals(listOf("正常重点"), selected.map { it.span.value })
    }

    @Test fun `long and numeric values remain neutral and cumulative characters are capped`() {
        val budget = ReplyEmphasisBudget()
        listOf("1", "1234", "内容".repeat(21), "NFMARKDOWNTOKEN0X", "a&b").forEach { assertNull(budget.take(it)) }
        assertEquals(ReplyEmphasisTone.IMPORTANT, budget.take("重点".repeat(20)))
        assertEquals(ReplyEmphasisTone.IMPORTANT, budget.take("结论".repeat(20)))
        assertNull(budget.take("注意：" + "检查".repeat(8)))
        assertEquals(ReplyEmphasisTone.CAUTION, budget.take("注意：余额"))
    }

    @Test fun `render plans keep actual span identity and start fresh for each reply`() {
        val source = blocks("这是**重复重点**，**重复重点**，**重复重点**。")
        val spans = source.filterIsInstance<PresentationBlock.Paragraph>().flatMap { it.spans }.filterIsInstance<InlinePresentation.Strong>()
        val selected = replyEmphasisSelections(source)
        assertEquals(2, selected.size)
        assertSame(spans[0], selected[0].span)
        assertSame(spans[1], selected[1].span)
        assertFalse(selected.any { it.span === spans[2] })
        assertEquals(2, replyEmphasisSelections(source).size)
    }
}
