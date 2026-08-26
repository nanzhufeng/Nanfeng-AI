package com.nanzhufeng.ai.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScheduledMonitorSuggestionPolicyTest {
    @Test fun `ordinary travel comparison never offers a recurring monitor`() {
        assertFalse(ScheduledMonitorSuggestionPolicy.shouldOffer("打算和大学同学聚会去海南玩，陵水和海口哪个体验和性价比更好？"))
    }

    @Test fun `generic dynamic words never select a product template`() {
        assertFalse(ScheduledMonitorSuggestionPolicy.shouldOffer("最近智能汽车有什么动态，顺便说说价格。"))
    }

    @Test fun `only an explicit future tracking request opens the refinement action`() {
        assertTrue(ScheduledMonitorSuggestionPolicy.shouldOffer("请持续跟踪苹果股价，跌破285美元时提醒我。"))
    }
}
