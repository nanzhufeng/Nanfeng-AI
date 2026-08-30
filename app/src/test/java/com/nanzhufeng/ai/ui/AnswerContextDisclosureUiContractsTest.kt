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
        assertTrue(workspace.contains("leadingIcon = { Icon(Icons.Rounded.AccountTree, contentDescription = null, modifier = Modifier.size(16.dp), tint = SecondaryText.copy(alpha = 0.72f)) }"))
        assertTrue(workspace.contains("leadingIcon = { Icon(Icons.AutoMirrored.Outlined.CallSplit, contentDescription = null, modifier = Modifier.size(16.dp), tint = SecondaryText.copy(alpha = 0.72f)) }"))
        assertFalse(workspace.contains("contentDescription = \"查看本次上下文来源\""))
        assertTrue(workspace.contains("AnswerContextDisclosureDialog"))
        assertFalse(workspace.contains("仅显示本次实际使用的本地来源。"))
        assertFalse(workspace.contains("Text(source.whyUsed"))
        assertTrue(workspace.contains("\"个性化资料\" -> source.title"))
        assertTrue(workspace.contains("\"自定义指令\" -> source.kind"))
        assertTrue(workspace.contains("if (sourceRows.isEmpty()) return null"))
        assertTrue(workspace.contains("contextSelections = state.answerContextSelections"))
        assertTrue(viewModel.contains("contextSelectionAudits.forAssistantMessages(path.map(MessageNode::id))"))
        assertTrue(executor.contains("contextSelectionAudits.bindAnswer"))
        assertTrue(executor.contains("attemptId = attempt.attemptId"))
        assertTrue(executor.contains("contextAuditSources(context, experience)"))
        assertTrue(executor.contains("participationAuditAvailable = true"))
        assertTrue(store.contains("override fun bindAnswer"))
        assertTrue(store.contains("override fun forAssistantMessages"))
        assertTrue(store.contains("participationAuditAvailable"))
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
