package com.nanzhufeng.ai.ui

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversationSendErrorIsolationContractsTest {
    @Test fun `composer send error is keyed to the conversation that failed`() {
        val viewModel = File("src/main/java/com/nanzhufeng/ai/ui/ConversationFoundationViewModel.kt").readText()
        val workspace = File("src/main/java/com/nanzhufeng/ai/ui/ConversationWorkspace.kt").readText()

        assertTrue(viewModel.contains("val sendErrorConversationId:"))
        assertTrue(viewModel.contains("state.sendErrorConversationId == snapshot?.conversation?.id"))
        assertTrue(viewModel.contains("sendErrorConversationId = id"))
        assertTrue(workspace.contains("state.sendErrorConversationId == state.selectedConversationId"))
        assertTrue(workspace.contains("本机先解析；必要时经千问 Qwen3.7-Plus／智谱 GLM-OCR"))
    }
}
