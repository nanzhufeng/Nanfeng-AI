package com.nanzhufeng.ai.ui

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversationCostLedgerSummaryContractsTest {
    @Test
    fun summaryMetricsAlignLabelsAndValuesAndEmphasizeAllSectionAmounts() {
        val ui = File("src/main/java/com/nanzhufeng/ai/ui/ConversationCostLedgerUi.kt").readText()
        val summary = ui.substringAfter("private fun ConversationCostSummary(").substringBefore("private fun ConversationCostSummarySection")
        val metric = ui.substringAfter("private fun ConversationCostSummaryMetric(").substringBefore("private data class ConversationCostCategoryMetric")
        val grid = ui.substringAfter("private fun ConversationCostCategoryGrid(").substringBefore("private fun ConversationCostCategoryCell")

        assertTrue(summary.contains("label = \"费用\", value = amount, emphasizeValue = true"))
        assertTrue(summary.contains("CnyMoneyDisplay.summaryLabel(costs)"))
        assertFalse(summary.contains("OpenRouter 实际金额"))
        assertFalse(summary.contains("本地估算"))
        assertFalse(summary.contains("USD_REFERENCE_LABEL"))
        assertFalse(ui.contains("usdText()"))
        assertFalse(ui.contains("\\$"))
        assertTrue(metric.contains("Modifier.fillMaxWidth()"))
        assertTrue(metric.contains("Modifier.weight(1f)"))
        assertTrue(metric.contains("color = if (emphasizeValue) AccentOrange else BodyText"))
        assertTrue(metric.contains("fontWeight = if (emphasizeValue) FontWeight.Bold else FontWeight.Medium"))

        val section = ui.substringAfter("private fun ConversationCostSummarySection(").substringBefore("private fun ConversationCostSummaryMetric(")
        assertTrue(section.contains("Surface("))
        assertTrue(section.contains("Modifier.fillMaxWidth()"))
        assertTrue(section.contains("RoundedCornerShape(20.dp)"))
        assertTrue(section.contains("color = ForegroundSurface"))
        assertTrue(section.contains("MaterialTheme.typography.titleMedium"))
        assertFalse(section.contains("HorizontalDivider"))

        assertTrue(grid.contains("ConversationCostCategoryMetric(\"次数\", \"\${state.records.size} 次\")"))
        assertTrue(grid.contains("ConversationCostCategoryMetric(\"费用\", CnyMoneyDisplay.summaryLabel(state.records.map { it.cost }) ?: \"金额未知\", true)"))
        assertTrue(grid.contains("CnyMoneyDisplay.summaryLabel(state.titleGenerationRecords.map { it.cost })"))
        assertTrue(grid.contains("state.titleGenerationRecords.map { it.cost }"))
        assertTrue(grid.contains("state.historyCurationCalls.mapNotNull(DirectChatCallAuditRecord::estimatedCost)"))
        assertTrue(grid.contains("state.glmOcrCalls.map { it.cost }"))
        assertFalse(grid.contains("（估算）"))
        assertFalse(grid.contains("ImageVector"))
        assertFalse(grid.contains("icon ="))
        assertFalse(grid.contains("tone ="))

        val cell = ui.substringAfter("private fun ConversationCostCategoryCell(").substringBefore("@Composable private fun ReminderDraftCostSummary")
        assertTrue(cell.contains("modifier = modifier.padding(horizontal = 14.dp, vertical = 16.dp)"))
        assertFalse(cell.contains("heightIn(min ="))
    }

    @Test fun invocationLedgerAlsoProjectsStoredCurrencyFactsAsRmb() {
        val ui = File("src/main/java/com/nanzhufeng/ai/ui/InvocationLedgerUi.kt").readText()
        val label = ui.substringAfter("private fun InvocationRecord.usageAndCostLabel").substringBefore("private fun com.nanzhufeng.ai.domain.TaskRun.durationMillis")
        assertTrue(label.contains("CnyMoneyDisplay.label"))
        assertFalse(label.contains("微货币"))
    }

    @Test fun costRowsShowACompletedModelDurationWithoutInventingMissingHistory() {
        val ui = File("src/main/java/com/nanzhufeng/ai/ui/ConversationCostLedgerUi.kt").readText()
        val row = ui.substringAfter("private fun ConversationCostLedgerRow").substringBefore("private fun ReminderDraftCostRow")
        assertTrue(row.contains("record.modelDurationLabel()"))
        assertTrue(ui.contains("模型耗时未记录"))
        assertTrue(ui.contains("模型耗时 \${duration / 100L / 10.0} 秒"))
    }

    @Test fun ledgerIncludesTokenBearingBackgroundCallsAndKeepsHistoricalRateTime() {
        val ui = File("src/main/java/com/nanzhufeng/ai/ui/ConversationCostLedgerUi.kt").readText()
        val attribution = File("src/main/java/com/nanzhufeng/ai/domain/AssistantResponseModelAttribution.kt").readText()
        assertTrue(ui.contains("scheduledMonitorRepository.listCostedRunsNewestFirst()"))
        assertTrue(ui.contains("directChatCallAudit.listNewestFirst().filter"))
        assertTrue(ui.contains("ScheduledMonitorCostSummary"))
        assertTrue(ui.contains("state.historyCurationCalls.mapNotNull(DirectChatCallAuditRecord::estimatedCost)"))
        assertTrue(attribution.contains("ConversationCostEstimator.estimate(modelId, usage, recordedAt)"))
    }

    @Test fun `conversation title history and ocr costs stay visible together while one detail type is selected`() {
        val ui = File("src/main/java/com/nanzhufeng/ai/ui/ConversationCostLedgerUi.kt").readText()
        val content = ui.substringAfter("private fun ConversationCostLedgerContent").substringBefore("private fun ConversationCostSummary(")
        val grid = ui.substringAfter("private fun ConversationCostCategoryGrid").substringBefore("private fun ConversationCostCategoryCell")
        val picker = ui.substringAfter("private fun ConversationCostLedgerSectionPicker").substringBefore("private fun ConversationCostLedgerSectionRows")

        assertTrue(content.contains("ConversationCostCategoryGrid(state)"))
        assertTrue(content.indexOf("ConversationCostCategoryGrid(state)") < content.indexOf("ConversationCostLedgerSectionPicker"))
        assertTrue(grid.contains("Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min))"))
        assertTrue(grid.contains("VerticalDivider"))
        assertTrue(grid.contains("HorizontalDivider"))
        for (title in listOf("会话", "会话标题整理", "历史资料整理", "南枫转写")) {
            assertTrue(title, grid.contains("title = \"$title\""))
        }
        assertTrue(picker.contains("Surface("))
        assertTrue(picker.contains("Modifier.fillMaxWidth().height(56.dp)"))
        assertTrue(picker.contains("ConversationCostLedgerSection.entries.forEach"))
        assertTrue(picker.contains("color = if (active) AccentOrange else Color.Transparent"))
        assertTrue(picker.contains("Modifier.weight(1f).fillMaxHeight()"))
        assertFalse(picker.contains("DropdownMenu("))
        for (label in listOf("会话", "会话标题整理", "历史资料整理", "南枫转写")) assertTrue(ui.contains("\"$label\""))
    }

    @Test fun `cost ledger reads persisted glm ocr invocations and exposes a fourth switch`() {
        val ui = File("src/main/java/com/nanzhufeng/ai/ui/ConversationCostLedgerUi.kt").readText()
        val activity = File("src/main/java/com/nanzhufeng/ai/NanfengAiActivity.kt").readText()
        assertTrue(ui.contains("invocations.listNewestFirst().filter { it.taskId.value.startsWith(\"glm-ocr:\") }"))
        assertTrue(ui.contains("OCR(\"南枫转写\""))
        assertTrue(ui.contains("ConversationCostLedgerSection.OCR"))
        assertTrue(ui.contains("GlmOcrCostRow(record)"))
        assertTrue(ui.contains("输入 \${record.usage.inputTokens"))
        assertTrue(ui.contains("record.taskRun.durationMillis().costDurationLabel()"))
        val costFactory = activity.substringAfter("ConversationCostLedgerViewModel.Factory(").substringBefore(")[ConversationCostLedgerViewModel::class.java]")
        assertTrue(costFactory.contains("container.directChatCallAudit"))
        assertTrue(costFactory.contains("container.invocationRepository"))
    }
}
