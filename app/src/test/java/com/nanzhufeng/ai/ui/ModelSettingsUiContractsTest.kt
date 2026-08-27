package com.nanzhufeng.ai.ui

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelSettingsUiContractsTest {
    @Test
    fun settingsKeepConfigurationControlsButMoveRuntimeInformationToExplicitEntries() {
        val ui = File("src/main/java/com/nanzhufeng/ai/ui/ModelSettingsUi.kt").readText()
        val viewModel = File("src/main/java/com/nanzhufeng/ai/ui/ModelSettingsViewModel.kt").readText()
        val app = File("src/main/java/com/nanzhufeng/ai/ui/NanfengAiApp.kt").readText()

        for (token in listOf("OpenRouter、Qwen、DeepSeek", "实时网页搜索", "需要当前信息时自动检索公开网页并标注来源", "WebSearchSettingsRow", "AiPresetSelectionSurface", "API Key", "测试连接", "费用与用量", "上下文记录", "运行诊断", "conversationTitle", "recordTimestamp", "查看技术详情")) {
            assertTrue("missing configuration or record entry: $token", ui.contains(token))
        }
        assertTrue(ui.contains("ModelSettingsContextSelectionsPage"))
        assertTrue(ui.contains("ModelSettingsDiagnosticsPage"))
        assertTrue(ui.contains("ModelSettingsConfigurationPage"))
        assertTrue(ui.contains("ModelSettingsSaveFeedback(state)"))
        assertTrue(ui.contains("API Key 已安全保存在本机。"))
        assertTrue(ui.contains("尚未测试连接；请点击“测试连接”确认 API Key 是否可用。"))
        val webSearch = ui.substringAfter("private fun WebSearchSettingsRow(").substringBefore("@Composable\ninternal fun ModelSettingsConfigurationPage")
        assertTrue(webSearch.contains("val supportingText"))
        assertTrue(webSearch.indexOf("Surface(") < webSearch.indexOf("Text(\n            supportingText"))
        assertTrue(webSearch.contains("padding(horizontal = 18.dp, vertical = 4.dp)"))
        assertFalse(webSearch.contains("padding(horizontal = 18.dp, vertical = 13.dp)"))
        assertTrue(!ui.contains("ModelSettingsDialog("))
        assertTrue(!ui.contains("点击后，只发送一句“hi”测试连接"))
        assertTrue(viewModel.contains("contextSelectionAudits.recent(50)"))
        assertTrue(viewModel.contains("notice = if (replacementCredential != null)"))
        assertTrue(app.contains("onLoadInvocationLedger"))
        assertTrue(app.contains("onOpenConversationCostLedger"))
        assertTrue(app.contains("onWebSearchEnabledChange"))
    }

    @Test
    fun modelConfigurationIsThePrimaryEntryWhileCallRecordsShareOneGroupedCard() {
        val ui = File("src/main/java/com/nanzhufeng/ai/ui/ModelSettingsUi.kt").readText()
        val home = ui.substringAfter("internal fun ModelServiceStatusCard(").substringBefore("private fun WebSearchSettingsRow")
        val primary = ui.substringAfter("private fun ModelSettingsPrimaryEntry").substringBefore("private fun ModelSettingsRecordsCard")
        val records = ui.substringAfter("private fun ModelSettingsRecordsCard").substringBefore("private fun ModelSettingsGroupedEntry")

        assertTrue(home.indexOf("ModelSettingsPrimaryEntry(onOpen)") < home.indexOf("ModelSettingsRecordsCard("))
        assertFalse(primary.contains("BorderStroke("))
        assertTrue(primary.contains("shadowElevation = 0.dp"))
        assertTrue(primary.contains("Icons.Rounded.Key"))
        assertTrue(records.contains("Text(\n                \"调用记录\""))
        assertTrue(records.split("ModelSettingsGroupedEntry(").size - 1 == 3)
        assertTrue(records.split("HorizontalDivider(").size - 1 == 2)
    }
}
