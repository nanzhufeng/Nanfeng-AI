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
        assertEquals("分析南枫AI标题生成问题", parseConversationTitleResponse("{\"title\":\"分析南枫AI标题生成问题\"}"))
        assertEquals("Model Naming Review", parseConversationTitleResponse("{\"title\":\"Model Naming Review\"}"))
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
}
