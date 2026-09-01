package com.nanzhufeng.ai.ui

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversationStyleComposerContractsTest {
    @Test
    fun `add menu switches title-only style options for only the current conversation`() {
        val workspace = File("src/main/java/com/nanzhufeng/ai/ui/ConversationWorkspace.kt").readText()
        val viewModel = File("src/main/java/com/nanzhufeng/ai/ui/ConversationFoundationViewModel.kt").readText()
        val executor = File("src/main/java/com/nanzhufeng/ai/ai/NormalChatOpenRouterExecutor.kt").readText()
        val container = File("src/main/java/com/nanzhufeng/ai/app/AppContainer.kt").readText()

        for (token in listOf(
            "ComposerConversationStyleAction(",
            "基础风格和语气",
            "selectedLabel = conversationStyle.definition().label",
            "ConversationStyle.selectable.forEach",
            "ComposerStyleOptionRow(",
            "onSelectConversationStyle = onSetCurrentConversationStyle",
        )) assertTrue("missing style quick-switch token $token", workspace.contains(token))

        val option = workspace.substringAfter("private fun ComposerStyleOptionRow(").substringBefore("private fun ComposerMenuIconSurface")
        assertFalse("secondary style options must not render summaries", option.contains("summary"))
        assertTrue(viewModel.contains("fun setCurrentConversationStyle(style: ConversationStyle)"))
        assertTrue(viewModel.contains("conversationStyleOverrides.setStyle"))
        assertTrue(workspace.contains("state.conversationStyleOverride?.style ?: state.globalConversationStyle"))
        assertTrue(executor.contains("resolveConversationStyle(conversationId, globalExperience.conversationStyle)"))
        assertTrue(executor.contains("globalExperience.copy("))
        assertTrue(container.contains("resolveConversationStyle = conversationStyleOverrides::effectiveStyle"))
    }
}
