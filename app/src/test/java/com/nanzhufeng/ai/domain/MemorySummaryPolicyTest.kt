package com.nanzhufeng.ai.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MemorySummaryPolicyTest {
    @Test fun `direct remember command is explicit but secrets are excluded`() {
        assertTrue(MemorySummaryPolicy.isExplicitSaveCommand("记住了：我长期关注 AI 与影视工具"))
        assertFalse(MemorySummaryPolicy.isExplicitSaveCommand("记住了：API Key: abcdefghijklmnop"))
    }

    @Test fun `strict model envelope is parsed and unsafe text is rejected`() {
        val draft = MemorySummaryPolicy.parseModelSummary("【记忆主题】技术与工具\n【记忆摘要】\n- 长期使用 Codex 和 Android。")
        assertNotNull(draft)
        assertEquals("技术与工具", draft?.title)
        assertEquals(null, MemorySummaryPolicy.parseModelSummary("【记忆主题】概览\n【记忆摘要】密码：123456"))
    }

    @Test fun `suggestion remains rare and requires durable signals`() {
        assertEquals(null, MemorySummaryPolicy.suggestionFor("帮我翻译这一句", "这是译文。".repeat(30)))
        assertNotNull(MemorySummaryPolicy.suggestionFor("我的南枫项目长期关注 AI 与影视投资", "我会把这个长期项目偏好与研究方向拆成稳定原则，并在后续建议里优先考虑。".repeat(3)))
    }
}
