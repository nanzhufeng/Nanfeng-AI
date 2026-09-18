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
        val composerEntry = workspace.substringAfter("private fun ComposerModelEntry(").substringBefore("private fun ConversationComposerDock")
        assertTrue("composer must not add a persistent web-search status label", !composerEntry.contains("实时联网") && !composerEntry.contains("未联网"))
        assertTrue("model picker must show the current conversation state", workspace.contains("if (enabled) \"实时联网\" else \"未联网\""))
        val modelPicker = workspace.substringAfter("private fun ComposerMenuOverlay(").substringBefore("private fun ComposerModelPickerHeader(")
        assertTrue("daily and deep candidates must share runtime Provider plus conversation state", !modelPicker.contains("choice.slot.takeIf"))
        for (token in listOf(
            "ProviderId.OPENROUTER -> \"OpenRouter · \$webSearchStateLabel\"",
            "ProviderId.QWEN -> \"千问 · \$webSearchStateLabel\"",
            "ProviderId.ZHIPU -> \"智谱 · \$webSearchStateLabel\"",
        )) assertTrue("missing model-picker runtime detail $token", modelPicker.contains(token))
        assertTrue("Composer must reuse the shared switch instead of owning its dimensions", !workspace.contains("SettingsSwitchTrackWidth"))
        val action = workspace.substringAfter("private fun ComposerConversationWebSearchAction(").substringBefore("/** The mobile composer")
        assertTrue("composer control must not add a duplicate supporting label", !action.contains("仅影响本对话"))
        assertTrue("composer control must use the settings title", !action.contains("当前对话联网"))
        assertTrue(viewModel.contains("fun setCurrentConversationWebSearchEnabled(enabled: Boolean)"))
        assertTrue(viewModel.contains("conversationWebSearchOverrides.setEnabled"))
        assertTrue(executor.contains("resolveConversationWebSearchEnabled(conversationId, experience.webSearchEnabled)"))
        assertTrue(executor.contains("enabled = webSearchEnabled"))
        assertTrue(executor.contains("webSearchEnabled && !requestOptions.liveWebSearch"))
        assertTrue(executor.contains("WebSearchGroundingPolicy.completedAuditStatus"))
        assertTrue(executor.contains("WEB_SEARCH_NO_SOURCES"))
        assertTrue(viewModel.contains("已请求实时网页搜索，但服务商没有返回可验证的公开来源"))
        assertTrue(container.contains("resolveConversationWebSearchEnabled = conversationWebSearchOverrides::effectiveEnabled"))
        assertTrue(File("src/main/java/com/nanzhufeng/ai/ui/NanfengAiApp.kt").readText().contains("conversationViewModel.reload()"))
    }
}
