package com.nanzhufeng.ai.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InitialMemorySummaryTest {
    @Test fun `initial summary preserves the user confirmed sections`() {
        assertEquals(
            listOf("概览", "开发项目", "工作方式", "AI 与技术关注", "投资与产业研究", "品牌与视觉", "深入探索"),
            InitialMemorySummary.entries.map { it.title },
        )
        assertTrue(InitialMemorySummary.entries.all { it.body.isNotBlank() })
    }

    @Test fun `a deleted global summary still prevents automatic reseeding`() {
        assertTrue(InitialMemorySummary.shouldSeed(existingGlobalSummaryCount = 0))
        assertTrue(!InitialMemorySummary.shouldSeed(existingGlobalSummaryCount = 1))
    }
}
