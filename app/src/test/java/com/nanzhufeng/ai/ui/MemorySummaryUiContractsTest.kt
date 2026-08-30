package com.nanzhufeng.ai.ui

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class MemorySummaryUiContractsTest {
    @Test
    fun `memory summary is a readable full page with explicit local actions`() {
        val page = File("src/main/java/com/nanzhufeng/ai/ui/MemoryWorkspace.kt").readText()
        val model = File("src/main/java/com/nanzhufeng/ai/ui/MemoryViewModel.kt").readText()
        val app = File("src/main/java/com/nanzhufeng/ai/ui/NanfengAiApp.kt").readText()
        val conversation = File("src/main/java/com/nanzhufeng/ai/ui/ConversationWorkspace.kt").readText()

        for (token in listOf(
            "Text(\"记忆摘要\"", "updatedText", "Icons.Rounded.MoreVert", "关于记忆", "刷新摘要",
            "删除记忆", "关闭记忆摘要生成和应用", "询问或更新", "BasicTextField", "询问摘要", "补充记忆",
            "style = MaterialTheme.typography.titleLarge", "style = MaterialTheme.typography.bodyLarge",
            ".align(Alignment.BottomCenter)", "onSizeChanged { composerHeightPx = it.height }",
            "imePadding()", "bottom = composerHeight + 24.dp",
        )) assertTrue("missing memory-summary UI token: $token", page.contains(token))
        assertTrue("legacy combined action must be removed", !page.contains("删除并关闭记忆"))
        for (token in listOf("fun refreshSummary()", "fun querySummary(query: String)", "fun appendSummaryUpdate(raw: String)", "fun clearSummary()", "fun markSummaryGenerationAndUseDisabled()")) {
            assertTrue("missing explicit memory action: $token", model.contains(token))
        }
        assertTrue(app.contains("if (route == P5ARoute.MEMORY)"))
        val memoryRoute = app.substring(app.indexOf("if (route == P5ARoute.MEMORY)"), app.indexOf("    Column(\n        modifier = Modifier", app.indexOf("if (route == P5ARoute.MEMORY)")))
        assertTrue("memory summary must inherit the settings text scale", memoryRoute.contains("SettingsTextScale {"))
        assertTrue(memoryRoute.contains("MemorySummaryPage("))
        assertTrue(app.contains("onDeleteMemory = memoryViewModel::clearSummary"))
        assertTrue(app.contains("onDisableMemorySummaryGenerationAndUse = {"))
        assertTrue(app.contains("current.copy(memoryRetrievalEnabled = false)"))
        assertTrue(app.contains("memorySummaryGenerationEnabled = memorySummaryGenerationEnabled"))
        assertTrue(conversation.contains("if (memorySummaryGenerationEnabled)"))
    }
}
