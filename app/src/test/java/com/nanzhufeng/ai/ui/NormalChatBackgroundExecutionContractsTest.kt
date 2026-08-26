package com.nanzhufeng.ai.ui

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NormalChatBackgroundExecutionContractsTest {
    @Test
    fun normalChatForegroundServiceOwnsTheRequestAndReturnsOnlySafeUiState() {
        val service = File("src/main/java/com/nanzhufeng/ai/background/NormalChatBackgroundExecution.kt").readText()
        val viewModel = File("src/main/java/com/nanzhufeng/ai/ui/ConversationFoundationViewModel.kt").readText()

        for (token in listOf("startForegroundService", "FOREGROUND_SERVICE_TYPE_DATA_SYNC", "南枫 AI 正在生成回复", "START_NOT_STICKY", "ACTION_CANCEL", "ACTION_CANCEL_ALL", "PARTIAL_WAKE_LOCK", "TimeUnit.MINUTES.toMillis(2)", "停止生成", "onLocalSubmission", "normalChatOpenRouterExecutor.execute", "retryLatestAttempt")) {
            assertTrue("missing foreground execution owner: $token", service.contains(token))
        }
        assertTrue(viewModel.contains("normalChatBackgroundExecution.ownsExecution()"))
        assertTrue(viewModel.contains("NormalChatBackgroundOperation.SEND"))
        assertTrue(viewModel.contains("NormalChatBackgroundOperation.RETRY"))
        assertTrue(viewModel.contains("normalChatBackgroundExecution.cancel(visibleRuntime.conversationId)"))
        assertTrue(viewModel.contains("state.selectedConversationId?.let(normalChatBackgroundExecution::isRunning) == true"))
        assertFalse("ViewModel must not stop a notification-only pseudo owner", viewModel.contains("normalChatBackgroundExecution.finish("))
        assertFalse("Service destruction must not turn into a remote cancel", service.contains("onDestroy() {\n        if (jobs.isNotEmpty())"))
    }
}
