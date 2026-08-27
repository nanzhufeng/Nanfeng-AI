package com.nanzhufeng.ai.domain

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class P3DMessagePresentationContractsTest {
    private val conversationId = ConversationId("p3d-presentation")

    @Test fun `safe markdown supports required blocks and malformed input falls back without raw control glyphs`() {
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
        assertEquals("<script>alert(1)</script>\nkotlin\n未闭合", fallback.raw)
    }

    @Test fun `standalone material markers share the same bold projection`() {
        val blocks = MessagePresentationRenderer().render(
            listOf(message("material-markers", "<图片>\n\n<文件>")),
        ).single().blocks.filterIsInstance<PresentationBlock.Paragraph>()

        assertEquals(
            listOf("<图片>", "<文件>"),
            blocks.map { block -> (block.spans.single() as InlinePresentation.Strong).value },
        )
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

    @Test fun `blank lines within a list and thematic breaks retain their Markdown structure`() {
        val source = """
            要点：

            - 第一项

            - 第二项

            ---

            ## 后续
        """.trimIndent()
        val blocks = MessagePresentationRenderer().render(listOf(message("structure", source))).single().blocks
        assertEquals(2, blocks.filterIsInstance<PresentationBlock.UnorderedList>().single().items.size)
        assertEquals(1, blocks.filterIsInstance<PresentationBlock.HorizontalRule>().size)
        assertTrue(blocks.last() is PresentationBlock.Heading)
    }

    @Test fun `nested ordered and unordered lists retain semantic progressive depths`() {
        val source = """
            - 一级
              - 二级
                - 三级
            - 同级

            1. 第一项
                1. 子项
        """.trimIndent()
        val blocks = MessagePresentationRenderer().render(listOf(message("nested-lists", source))).single().blocks
        val unordered = blocks.filterIsInstance<PresentationBlock.UnorderedList>().single()
        val ordered = blocks.filterIsInstance<PresentationBlock.OrderedList>().single()
        assertEquals(listOf(0, 1, 2, 0), unordered.items.map { it.depth })
        assertEquals(listOf(0, 2), ordered.items.map { it.depth })
    }

    @Test fun `Chinese section labels and standalone summaries receive document hierarchy without rewriting prose`() {
        val source = """
            第二层：FSD订阅——已经产生真实商业价值

            截至Q2：

            订阅规模仍在上升。

            我的最终判断

            **重点**不应被整段加粗。
        """.trimIndent()
        val blocks = MessagePresentationRenderer().render(listOf(message("chinese-headings", source))).single().blocks
        val headings = blocks.filterIsInstance<PresentationBlock.Heading>()
        assertEquals(listOf(2, 3, 3), headings.map { it.level })
        assertEquals("第二层：FSD订阅——已经产生真实商业价值", headings.first().spans.filterIsInstance<InlinePresentation.Text>().joinToString("") { it.value })
        val paragraph = blocks.filterIsInstance<PresentationBlock.Paragraph>().last()
        assertEquals(listOf("重点"), paragraph.spans.filterIsInstance<InlinePresentation.Strong>().map { it.value })
    }

    @Test fun `code wrapped HTTP addresses remain directly usable links while ordinary code stays code`() {
        val blocks = MessagePresentationRenderer().render(listOf(message("code-url", "`https://example.test/guide` 与 `搜索关键词`"))).single().blocks
        val spans = (blocks.single() as PresentationBlock.Paragraph).spans
        assertTrue(spans.any { it is InlinePresentation.Link && it.label == "https://example.test/guide" && it.url == "https://example.test/guide" })
        assertTrue(spans.any { it is InlinePresentation.Code && it.value == "搜索关键词" })
    }

    @Test fun `parenthetical notes become dedicated auxiliary blocks even when markdown emphasizes them`() {
        val blocks = MessagePresentationRenderer().render(
            listOf(message("note", "*（注：该快讯标注日期为未来的 2026-08-22，来源标注为 NVIDIA / AI FRONTIER）*")),
        ).single().blocks
        val note = blocks.single() as PresentationBlock.Note
        assertEquals("（注：该快讯标注日期为未来的 2026-08-22，来源标注为 NVIDIA / AI FRONTIER）", note.spans.filterIsInstance<InlinePresentation.Text>().joinToString("") { it.value })
        assertTrue(note.spans.none { it is InlinePresentation.Emphasis })
    }

    @Test fun `standalone source URLs attach to their preceding readable list item`() {
        val source = """
            - 支持地区：
            https://www.anthropic.com/supported-countries
            - 服务条款：
            - https://www.anthropic.com/legal
        """.trimIndent()
        val blocks = MessagePresentationRenderer().render(listOf(message("sources", source))).single().blocks
        assertTrue(blocks.none { it is PresentationBlock.Paragraph && it.spans.filterIsInstance<InlinePresentation.Link>().isNotEmpty() })
        val lists = blocks.filterIsInstance<PresentationBlock.UnorderedList>()
        assertEquals(2, lists.size)
        assertEquals("https://www.anthropic.com/supported-countries", lists[0].items.single().spans.filterIsInstance<InlinePresentation.Link>().single().url)
        assertEquals("https://www.anthropic.com/legal", lists[1].items.single().spans.filterIsInstance<InlinePresentation.Link>().single().url)
    }

    @Test fun `dedicated source entry sections collapse into shortcuts on the preceding conclusion`() {
        val source = """
            这项判断仍需要结合公开资料核对。

            可查的资料入口：

            - Anthropic Supported Countries / 支持地区：
            https://www.anthropic.com/supported-countries
            - Anthropic Terms of Service：
            https://www.anthropic.com/legal
            - 可搜索关键词：
              - `Anthropic Chinese companies Claude access restriction`

            ---

            ## 后续判断
        """.trimIndent()
        val blocks = MessagePresentationRenderer().render(listOf(message("source-section", source))).single().blocks
        assertTrue(blocks.none { block ->
            block is PresentationBlock.Heading && block.spans.filterIsInstance<InlinePresentation.Text>().joinToString("") { it.value }.contains("可查")
        })
        assertTrue(blocks.none { it is PresentationBlock.UnorderedList })
        val conclusion = blocks.filterIsInstance<PresentationBlock.Paragraph>().single()
        assertEquals(
            listOf("https://www.anthropic.com/supported-countries", "https://www.anthropic.com/legal"),
            conclusion.spans.filterIsInstance<InlinePresentation.Link>().map { it.url },
        )
        assertTrue(blocks.any { it is PresentationBlock.HorizontalRule })
        assertTrue(blocks.any { it is PresentationBlock.Heading && it.spans.filterIsInstance<InlinePresentation.Text>().any { span -> span.value.contains("后续判断") } })
    }

    @Test fun `closed double asterisks render as strong while malformed markers stay out of reader text`() {
        val strong = MessagePresentationRenderer().render(listOf(message("strong", "**降**不是**涨**：*仅作强调*"))).single()
            .blocks.single() as PresentationBlock.Paragraph
        assertEquals(listOf("降", "涨"), strong.spans.filterIsInstance<InlinePresentation.Strong>().map { it.value })
        assertEquals(listOf("仅作强调"), strong.spans.filterIsInstance<InlinePresentation.Emphasis>().map { it.value })

        val malformed = MessagePresentationRenderer().render(listOf(message("malformed", "未闭合 **强调 或普通 * 星号"))).single()
            .blocks.single() as PresentationBlock.Paragraph
        assertEquals("未闭合 强调 或普通 星号", malformed.spans.filterIsInstance<InlinePresentation.Text>().joinToString("") { it.value })
        assertTrue(malformed.spans.none { it is InlinePresentation.Strong || it is InlinePresentation.Emphasis })

        val laterValid = MessagePresentationRenderer().render(listOf(message("later", "未闭合 **标记，后面仍可 *强调*。"))).single()
            .blocks.single() as PresentationBlock.Paragraph
        assertEquals(listOf("强调"), laterValid.spans.filterIsInstance<InlinePresentation.Emphasis>().map { it.value })
    }

    @Test fun `css colours and escaped markdown remain restrained reader prose`() {
        val source = """
            `background:
            \#fff / white / rgba(255,255,255,1)

            白 = \*\*没有背景色时透出的下层底色\*\*
        """.trimIndent()
        val blocks = MessagePresentationRenderer().render(listOf(message("css-colour", source))).single().blocks
        assertTrue("CSS colour must not become a heading", blocks.none { it is PresentationBlock.Heading })
        val visible = blocks.joinToString("\n") { block -> when (block) {
            is PresentationBlock.Paragraph -> block.spans.joinToString("") { span -> when (span) {
                is InlinePresentation.Text -> span.value
                is InlinePresentation.Strong -> span.value
                is InlinePresentation.Emphasis -> span.value
                is InlinePresentation.Code -> span.value
                is InlinePresentation.Link -> span.label
            } }
            else -> block.toString()
        } }
        assertTrue(visible.contains("fff / white / rgba(255,255,255,1)"))
        assertTrue(visible.contains("没有背景色时透出的下层底色"))
        assertFalse(visible.contains("#"))
        assertFalse(visible.contains("*"))
        assertFalse(visible.contains("`"))
    }

    @Test fun `near Markdown formatting is projected as reader structure without raw control glyphs`() {
        val source = """
            帮你查一下最新情况：## 简短结论
            ####方案三：使用国内 API 中转

            ``bashcurl -I
            https://openrouter.ai/api/v1/models``

            -长时间卡住 - Connection timed out
            已核对。 * 聚会前再确认一次。
        """.trimIndent()
        val blocks = MessagePresentationRenderer().render(listOf(message("near-markdown", source))).single().blocks

        val headings = blocks.filterIsInstance<PresentationBlock.Heading>()
        assertEquals(listOf("简短结论", "方案三：使用国内 API 中转"), headings.map { heading -> heading.spans.filterIsInstance<InlinePresentation.Text>().joinToString("") { it.value } })
        val code = blocks.filterIsInstance<PresentationBlock.CodeFence>().single()
        assertEquals("bash", code.language)
        assertEquals("curl -I\nhttps://openrouter.ai/api/v1/models", code.code)
        assertTrue(blocks.filterIsInstance<PresentationBlock.UnorderedList>().flatMap { it.items }.any { item -> item.spans.filterIsInstance<InlinePresentation.Text>().joinToString("") { it.value } == "长时间卡住 - Connection timed out" })
        assertTrue(blocks.filterIsInstance<PresentationBlock.Paragraph>().none { paragraph -> paragraph.spans.filterIsInstance<InlinePresentation.Text>().any { it.value.contains("#") || it.value.contains("``") || it.value.contains("* 聚会") } })
    }

    @Test fun `source label and separator debris collapse to verified shortcuts only`() {
        val source = """
            结论仍需要以公开资料核对。

            主要参考：、 、 、https://example.com/a
        """.trimIndent()
        val blocks = MessagePresentationRenderer().render(listOf(message("source-debris", source))).single().blocks
        val conclusion = blocks.filterIsInstance<PresentationBlock.Paragraph>().single()
        assertTrue(conclusion.spans.filterIsInstance<InlinePresentation.Text>().none { it.value.contains("主要参考") || it.value.contains("、") })
        assertEquals(listOf("https://example.com/a"), conclusion.spans.filterIsInstance<InlinePresentation.Link>().map { it.url })
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
