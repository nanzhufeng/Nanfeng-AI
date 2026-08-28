package com.nanzhufeng.ai.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ConversationTitleFormatContractsTest {
    @Test
    fun `title accepts only Chinese or English summary phrases`() {
        assertEquals("南枫AI标题问题", parseConversationTitleResponse("{\"title\":\"南枫AI标题问题\"}"))
        assertEquals("Model Review", parseConversationTitleResponse("{\"title\":\"Model Review\"}"))
    }

    @Test
    fun `title rejects numbers punctuation symbols and markdown`() {
        for (title in listOf("标题生成问题1", "标题生成问题！", "标题生成问题 v2", "标题生成问题_修复", "标题生成问题🙂", "# 标题生成问题")) {
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
