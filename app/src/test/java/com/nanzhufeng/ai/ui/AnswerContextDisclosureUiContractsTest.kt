package com.nanzhufeng.ai.ui

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AnswerContextDisclosureUiContractsTest {
    @Test fun `answer footer exposes only attempt-bound context disclosures`() {
        val workspace = File("src/main/java/com/nanzhufeng/ai/ui/ConversationWorkspace.kt").readText()
        val viewModel = File("src/main/java/com/nanzhufeng/ai/ui/ConversationFoundationViewModel.kt").readText()
        val executor = File("src/main/java/com/nanzhufeng/ai/ai/NormalChatOpenRouterExecutor.kt").readText()
        val store = File("src/main/java/com/nanzhufeng/ai/data/AndroidContextSelectionAuditStore.kt").readText()

        assertTrue(workspace.contains("text = { Text(\"本次上下文来源\") }"))
        assertTrue(workspace.contains("leadingIcon = { Icon(Icons.Rounded.AccountTree, contentDescription = null) }"))
        assertFalse(workspace.contains("contentDescription = \"查看本次上下文来源\""))
        assertTrue(workspace.contains("AnswerContextDisclosureDialog"))
        assertTrue(workspace.contains("以下仅显示本次回答实际加入的本地来源，不显示来源正文。"))
        assertTrue(workspace.contains("contextSelections = state.answerContextSelections"))
        assertTrue(viewModel.contains("contextSelectionAudits.forAssistantMessages(path.map(MessageNode::id))"))
        assertTrue(executor.contains("contextSelectionAudits.bindAnswer"))
        assertTrue(executor.contains("attemptId = attempt.attemptId"))
        assertTrue(store.contains("override fun bindAnswer"))
        assertTrue(store.contains("override fun forAssistantMessages"))
        assertFalse(workspace.contains("selectedSources.map { it.body"))
    }

    @Test fun `temporary route remains outside normal context search and provider execution`() {
        val temporary = File("src/main/java/com/nanzhufeng/ai/domain/TemporaryConversation.kt").readText()
        val viewModel = File("src/main/java/com/nanzhufeng/ai/ui/ConversationFoundationViewModel.kt").readText()
        val temporarySubmit = viewModel.substring(viewModel.indexOf("fun submitTemporaryDraft()"), viewModel.indexOf("fun updateTemporaryModelOverride"))

        for (token in listOf(
            "object TemporaryConversationIsolation",
            "memoryRetrievalEnabled = false",
            "knowledgeRetrievalEnabled = false",
            "normalHistorySearchEnabled = false",
            "automaticTitleEnabled = false",
            "automaticMemorySummaryEnabled = false",
            "providerEgressEnabled = false",
        )) assertTrue("missing $token", temporary.contains(token))
        assertTrue(temporarySubmit.contains("temporary.appendOfflineMessage()"))
        assertTrue(temporarySubmit.contains("TemporaryConversationIsolation.sentNotice()"))
        for (forbidden in listOf("normalChatOpenRouterExecutor", "searchConversations", "saveMemorySummary", "maybeRefineOpeningTitle")) {
            assertFalse("temporary send must not reach $forbidden", temporarySubmit.contains(forbidden))
        }
    }
}
