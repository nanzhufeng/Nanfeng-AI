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
        assertTrue(page.contains("选择 Claude 导出的 conversations.json，只在本机导入。"))
        assertTrue(page.contains("imports.select(name, mime, bytes)"))
        assertTrue(app.contains("it.readMarkdownBounded((com.nanzhufeng.ai.domain.CLAUDE_EXPORT_MAX_BYTES + 1).toInt())"))
        assertFalse(page.contains("确认导入"))
        assertFalse(page.contains("Text(\"跳过\")"))
        assertTrue(page.contains("Text(\"重试\")"))
        assertFalse(page.contains("调用模型"))
        assertFalse(page.contains("TEMPORARY"))
    }

    @Test fun `claude provenance is message-owned local text rather than provider metadata`() {
        val workspace = File("src/main/java/com/nanzhufeng/ai/ui/ConversationWorkspace.kt").readText()
        assertTrue(workspace.contains("ImportedConversationProvenance(\"从 Claude 导入\")"))
        assertFalse(workspace.contains("本地静态文本，不关联模型、Provider、费用或调用记录"))
    }
}
