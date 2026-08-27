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

    @Test fun costRowsShowACompletedModelDurationWithoutInventingMissingHistory() {
        val ui = File("src/main/java/com/nanzhufeng/ai/ui/ConversationCostLedgerUi.kt").readText()
        val row = ui.substringAfter("private fun ConversationCostLedgerRow").substringBefore("private fun ReminderDraftCostRow")
        assertTrue(row.contains("record.modelDurationLabel()"))
        assertTrue(ui.contains("模型耗时未记录"))
        assertTrue(ui.contains("模型耗时 \${duration / 100L / 10.0} 秒"))
    }
}
