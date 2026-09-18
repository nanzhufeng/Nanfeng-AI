package com.nanzhufeng.ai.domain

import org.junit.Assert.*
import org.junit.Test

class MarkdownIsolationRegressionTest {
    private fun render(text: String) = MessagePresentationRenderer().renderText(
        PresentationBlockIdentity(MessageNodeId("markdown-regression"), 0), text,
    )

    @Test fun `short transition row cannot flatten an entire answer`() {
        val blocks = render("# 苏美尔王表\n\n**概述**\n\n---\n\n| 序号 | 统治者 | 城市 | 在位时间 |\n| --- | --- | --- | --- |\n| 1 | Alulim | Eridu | 28800年 |\n| 城邦衰落，王权转移 |||\n| 2 | Alalgar | Eridu | 36000年 |\n\n## 结论\n\n[来源](https://example.com)")
        assertTrue(blocks.any { it is PresentationBlock.Heading })
        assertTrue(blocks.any { it is PresentationBlock.HorizontalRule })
        val table = blocks.filterIsInstance<PresentationBlock.Table>().single()
        assertEquals(3, table.rows.size)
        assertTrue(table.rows.all { it.size == 4 })
        assertTrue(blocks.none { it is PresentationBlock.PlainText })
        assertTrue(blocks.flatMap { when (it) {
            is PresentationBlock.Paragraph -> it.spans
            is PresentationBlock.Heading -> it.spans
            else -> emptyList()
        } }.any { it is InlinePresentation.Link })
    }

    @Test fun `extra cells and empty tables preserve neighboring document structure`() {
        val blocks = render("# 标题\n\n| A | B |\n| --- | --- |\n| 1 | 2 | 3 |\n\n## 后续\n\n| C | D |\n| --- | --- |\n\n末尾正文。")
        assertEquals(2, blocks.filterIsInstance<PresentationBlock.Heading>().size)
        val tables = blocks.filterIsInstance<PresentationBlock.Table>()
        assertEquals(2, tables.size)
        assertEquals(3, tables.first().headers.size)
        assertEquals(3, tables.first().rows.single().size)
        assertTrue(tables.last().rows.isEmpty())
    }

    @Test fun `unfinished code cannot erase previously rendered blocks`() {
        val blocks = render("# 标题\n\n**重要**\n\n```kotlin\nval x = 1")
        assertTrue(blocks.first() is PresentationBlock.Heading)
        assertEquals("val x = 1", blocks.filterIsInstance<PresentationBlock.CodeFence>().single().code)
    }

    @Test fun `nested labels split destinations and balanced url parentheses preserve sources`() {
        val blocks = render("结论正文。\n\n来源：\n\n- [History [archive]]\n(https://example.com/King_(Sumer))")
        val links = blocks.filterIsInstance<PresentationBlock.Paragraph>().flatMap { it.spans }.filterIsInstance<InlinePresentation.Link>()
        assertEquals(listOf("https://example.com/King_(Sumer)"), links.map { it.url })
        assertEquals("History [archive]", links.single().label)
    }

    @Test fun `source normalization does not rewrite code or persisted messages`() {
        val code = "[label]\n(https://example.com/King_(Sumer))"
        val blocks = render("```text\n$code\n```")
        assertEquals(code, blocks.filterIsInstance<PresentationBlock.CodeFence>().single().code)
    }

    @Test fun `source wrapper does not leave empty parentheses in displayed prose`() {
        val block = render("正文。([证据](https://example.com)) 公式 () 与 (`code`) 保留。").single() as PresentationBlock.Paragraph
        assertEquals("正文。 公式 () 与 () 保留。", block.spans.filterIsInstance<InlinePresentation.Text>().joinToString("") { it.value })
        assertEquals("code", block.spans.filterIsInstance<InlinePresentation.Code>().single().value)
        assertEquals("https://example.com", block.spans.filterIsInstance<InlinePresentation.Link>().single().url)
    }
}
