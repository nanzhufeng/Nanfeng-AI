package com.nanzhufeng.ai.ui

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversationWebSearchComposerContractsTest {
    @Test
    fun `add menu owns the current conversation web search switch and ordinary send resolves it`() {
        val workspace = File("src/main/java/com/nanzhufeng/ai/ui/ConversationWorkspace.kt").readText()
        val viewModel = File("src/main/java/com/nanzhufeng/ai/ui/ConversationFoundationViewModel.kt").readText()
        val executor = File("src/main/java/com/nanzhufeng/ai/ai/NormalChatOpenRouterExecutor.kt").readText()
        val container = File("src/main/java/com/nanzhufeng/ai/app/AppContainer.kt").readText()

        for (token in listOf(
            "conversationWebSearchEnabled = state.conversationWebSearchOverride?.enabled ?: state.globalWebSearchEnabled",
            "ComposerConversationWebSearchAction(",
            "Icons.Rounded.Public",
            "实时网页搜索",
            "SettingsSwitch(",
            "onSetCurrentConversationWebSearchEnabled(enabled)",
        )) assertTrue("missing composer web-search control token $token", workspace.contains(token))
        assertTrue("Composer must reuse the shared switch instead of owning its dimensions", !workspace.contains("SettingsSwitchTrackWidth"))
        val action = workspace.substringAfter("private fun ComposerConversationWebSearchAction(").substringBefore("/** The mobile composer")
        assertTrue("composer control must not add a duplicate supporting label", !action.contains("仅影响本对话"))
        assertTrue("composer control must use the settings title", !action.contains("当前对话联网"))
        assertTrue(viewModel.contains("fun setCurrentConversationWebSearchEnabled(enabled: Boolean)"))
        assertTrue(viewModel.contains("conversationWebSearchOverrides.setEnabled"))
        assertTrue(executor.contains("resolveConversationWebSearchEnabled(conversationId, experience.webSearchEnabled)"))
        assertTrue(executor.contains("enabled = webSearchEnabled"))
        assertTrue(container.contains("resolveConversationWebSearchEnabled = conversationWebSearchOverrides::effectiveEnabled"))
        assertTrue(File("src/main/java/com/nanzhufeng/ai/ui/NanfengAiApp.kt").readText().contains("conversationViewModel.reload()"))
    }
}
