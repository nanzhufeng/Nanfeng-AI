package com.nanzhufeng.ai.ui

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsScrollAndFooterModelContractsTest {
    private val workspace = File("src/main/java/com/nanzhufeng/ai/ui/ConversationWorkspace.kt").readText()
    private val app = File("src/main/java/com/nanzhufeng/ai/ui/NanfengAiApp.kt").readText()

    @Test
    fun `assistant footer reuses the composer compact model label`() {
        val footerStart = workspace.indexOf("private fun assistantFooterModelName")
        val footer = workspace.substring(
            footerStart,
            workspace.indexOf("private fun AssistantMessageAction", footerStart),
        )

        assertTrue(footer.contains("composerModelShortNameForUser(it)"))
    }

    @Test
    fun `settings levels retain independent scroll viewports across returns`() {
        assertTrue(app.contains("val settingsScrollStates = remember { mutableMapOf<SettingsDestination, androidx.compose.foundation.ScrollState>() }"))
        assertTrue(app.contains("val settingsScrollState = settingsScrollStates.getOrPut(settingsDestination)"))
        assertTrue(app.contains(".verticalScroll(settingsScrollState)"))
    }
}
