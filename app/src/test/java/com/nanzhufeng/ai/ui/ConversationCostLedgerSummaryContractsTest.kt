package com.nanzhufeng.ai.ui

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversationCostLedgerSummaryContractsTest {
    @Test
    fun onlyTopLevelConversationTotalsUseTheThemeEmphasis() {
        val ui = File("src/main/java/com/nanzhufeng/ai/ui/ConversationCostLedgerUi.kt").readText()
        val summary = ui.substringAfter("private fun ConversationCostSummary(").substringBefore("private fun ConversationCostSummaryAmount")
        val amount = ui.substringAfter("private fun ConversationCostSummaryAmount(").substringBefore("private fun ReminderDraftCostSummary")

        assertTrue(summary.contains("label = \"OpenRouter 实际金额：\""))
        assertTrue(summary.contains("label = \"本地估算：\""))
        assertTrue(amount.contains("color = AccentOrange"))
        assertTrue(amount.contains("fontWeight = FontWeight.Bold"))
    }
}
