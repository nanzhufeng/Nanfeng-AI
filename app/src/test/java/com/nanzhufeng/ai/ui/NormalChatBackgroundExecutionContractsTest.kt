package com.nanzhufeng.ai.ui

import com.nanzhufeng.ai.background.NormalChatGenerationRegistry
import com.nanzhufeng.ai.domain.ConversationId
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NormalChatBackgroundExecutionContractsTest {
    @Test
    fun normalChatForegroundServiceOwnsTheRequestAndReturnsOnlySafeUiState() {
        val service = File("src/main/java/com/nanzhufeng/ai/background/NormalChatBackgroundExecution.kt").readText()
        val viewModel = File("src/main/java/com/nanzhufeng/ai/ui/ConversationFoundationViewModel.kt").readText()

        for (token in listOf("startForegroundService", "FOREGROUND_SERVICE_TYPE_DATA_SYNC", "南枫 AI 正在生成回复", "START_NOT_STICKY", "ACTION_CANCEL", "ACTION_CANCEL_ALL", "PARTIAL_WAKE_LOCK", "TimeUnit.MINUTES.toMillis(2)", "停止全部生成", "onLocalSubmission", "onStreamProgress = { publishStreamProgress(conversationId) }", "EXTRA_PROGRESS", "STREAM_PROGRESS_COALESCE_MS", "normalChatOpenRouterExecutor.execute", "retryLatestAttempt")) {
            assertTrue("missing foreground execution owner: $token", service.contains(token))
        }
        assertTrue(viewModel.contains("normalChatBackgroundExecution.ownsExecution()"))
        assertTrue(viewModel.contains("NormalChatBackgroundOperation.SEND"))
        assertTrue(viewModel.contains("NormalChatBackgroundOperation.RETRY"))
        assertTrue(viewModel.contains("normalChatBackgroundExecution.cancel(visibleRuntime.conversationId)"))
        assertTrue(viewModel.contains("normalChatBackgroundExecution.runningConversationIds()"))
        assertTrue(viewModel.contains("runningConversationIds = projectedRunningConversationIds"))
        assertTrue(viewModel.contains("if (progress) return"))
        assertTrue(viewModel.contains("refreshSelectedTranscriptFromStream(conversationId)"))
        assertTrue(viewModel.contains("val projectionRequest = ++projectionGeneration"))
        assertTrue(viewModel.contains("provider can deliver several chunks per second"))
        assertTrue(viewModel.contains("reload(keepSending = id in state.runningConversationIds"))
        assertTrue(service.contains("fun runningConversationIds(): Set<ConversationId>"))
        assertTrue(service.contains("切换对话或界面不会中断"))
        assertTrue(File("src/main/java/com/nanzhufeng/ai/ui/ConversationWorkspace.kt").readText()
            .contains("generating = conversation.id in state.runningConversationIds"))
        assertFalse("ViewModel must not stop a notification-only pseudo owner", viewModel.contains("normalChatBackgroundExecution.finish("))
        assertFalse("Service destruction must not turn into a remote cancel", service.contains("onDestroy() {\n        if (jobs.isNotEmpty())"))

        val selectionBody = viewModel.substringAfter("fun selectConversation(").substringBefore("fun clearConversationWatchLater(")
        assertFalse("Switching conversations must not cancel another conversation's request", selectionBody.contains(".cancel("))
    }

    @Test
    fun registryTracksConcurrentConversationOwnersIndependently() {
        val first = ConversationId("continuity-first")
        val second = ConversationId("continuity-second")
        try {
            NormalChatGenerationRegistry.markRunning(first)
            NormalChatGenerationRegistry.markRunning(second)
            assertEquals(setOf(first, second), NormalChatGenerationRegistry.snapshot())

            NormalChatGenerationRegistry.markFinished(first)
            assertFalse(NormalChatGenerationRegistry.isRunning(first))
            assertTrue(NormalChatGenerationRegistry.isRunning(second))
        } finally {
            NormalChatGenerationRegistry.markFinished(first)
            NormalChatGenerationRegistry.markFinished(second)
        }
    }
}
