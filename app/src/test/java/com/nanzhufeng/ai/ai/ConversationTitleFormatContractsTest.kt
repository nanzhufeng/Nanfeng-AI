package com.nanzhufeng.ai.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ConversationTitleFormatContractsTest {
    @Test
    fun `title accepts Chinese English or Arabic number summary phrases`() {
        assertEquals("南枫AI标题问题", parseConversationTitleResponse("{\"title\":\"南枫AI标题问题\"}"))
        assertEquals("Model Review", parseConversationTitleResponse("{\"title\":\"Model Review\"}"))
        assertEquals("GPT5模型选择", parseConversationTitleResponse("{\"title\":\"GPT5模型选择\"}"))
        assertEquals("2026科技趋势", parseConversationTitleResponse("{\"title\":\"2026科技趋势\"}"))
    }

    @Test
    fun `title rejects punctuation symbols and markdown`() {
        for (title in listOf("标题生成问题！", "标题生成问题_修复", "标题生成问题🙂", "# 标题生成问题")) {
            assertNull(title, parseConversationTitleResponse("{\"title\":\"$title\"}"))
        }
    }

    @Test
    fun `title rejects generic action labels but accepts specific object and intent`() {
        assertNull(parseConversationTitleResponse("{\"title\":\"继续说\"}"))
        assertNull(parseConversationTitleResponse("{\"title\":\"更新文档\"}"))
        assertEquals("模型差异与费用分析", parseConversationTitleResponse("{\"title\":\"模型差异与费用分析\"}"))
    }

    @Test
    fun `automatic drawer title has a hard thirteen character ceiling`() {
        assertEquals("南枫AI标题问题", parseConversationTitleResponse("{\"title\":\"南枫AI标题问题\"}"))
        assertNull(parseConversationTitleResponse("{\"title\":\"这是一个明显超过十三个字的自动标题\"}"))
    }
}
