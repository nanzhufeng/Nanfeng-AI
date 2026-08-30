package com.nanzhufeng.ai.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class AssistantFooterCostDisplayTest {
    @Test
    fun `conversation footer removes estimate copy and rounds each displayed RMB amount to four places`() {
        assertEquals("¥0.0205", assistantFooterCostDisplay("≈ ¥0.020470（估算）"))
        assertEquals("¥0.0350", assistantFooterCostDisplay("约 ¥0.035013"))
        assertEquals("¥0.0003", assistantFooterCostDisplay("≈ ¥0.000336（估算）"))
        assertEquals("¥0.0000", assistantFooterCostDisplay("¥0.00"))
        assertEquals("¥0.0205 / ¥0.0350", assistantFooterCostDisplay("≈ ¥0.020470（估算） / 约 ¥0.035013"))
    }
}
