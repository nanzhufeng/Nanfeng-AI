package com.nanzhufeng.ai.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class AssistantFooterCostDisplayTest {
    @Test
    fun `conversation footer removes estimate copy and rounds each displayed USD amount to four places`() {
        assertEquals("\$0.0030", assistantFooterCostDisplay("≈ \$0.003046（估算）"))
        assertEquals("\$0.0053", assistantFooterCostDisplay("\$0.00534"))
        assertEquals("\$0.0001", assistantFooterCostDisplay("≈ \$0.00005（估算）"))
        assertEquals("\$0.0000", assistantFooterCostDisplay("\$0.00"))
        assertEquals("\$0.0030 / \$0.0053", assistantFooterCostDisplay("≈ \$0.003046（估算） / \$0.00534"))
    }
}
