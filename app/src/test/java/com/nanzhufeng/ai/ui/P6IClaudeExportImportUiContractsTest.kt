package com.nanzhufeng.ai.ui

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class P6IClaudeExportImportUiContractsTest {
    @Test fun `claude import stays in data settings and commits directly after the json picker`() {
        val app = File("src/main/java/com/nanzhufeng/ai/ui/NanfengAiApp.kt").readText()
        val page = File("src/main/java/com/nanzhufeng/ai/ui/ClaudeExportImportUi.kt").readText()
        assertTrue(app.contains("val claudeExportPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument())"))
        assertTrue(app.contains("claudeExportPicker.launch(arrayOf(\"application/json\", \"text/json\"))"))
        assertTrue(app.contains("ClaudeExportImportSettingsPage("))
        assertTrue(page.contains("仅选择 Claude data export 的 conversations.json"))
        assertTrue(page.contains("系统选择后复制到 app-private 并直接导入"))
        assertFalse(page.contains("确认导入"))
        assertFalse(page.contains("Text(\"跳过\")"))
        assertTrue(page.contains("Text(\"重试\")"))
        assertFalse(page.contains("调用模型"))
        assertFalse(page.contains("TEMPORARY"))
    }

    @Test fun `claude provenance is message-owned local text rather than provider metadata`() {
        val workspace = File("src/main/java/com/nanzhufeng/ai/ui/ConversationWorkspace.kt").readText()
        assertTrue(workspace.contains("从 Claude 导入 · 本地静态文本，不关联模型、Provider、费用或调用记录。"))
    }
}
