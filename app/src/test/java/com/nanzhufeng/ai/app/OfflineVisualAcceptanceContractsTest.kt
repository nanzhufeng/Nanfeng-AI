package com.nanzhufeng.ai.app

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OfflineVisualAcceptanceContractsTest {
    @Test
    fun `isolated acceptance seed covers attachment reminder transcription and redacted metadata states`() {
        val source = File("src/searchAttachmentAcceptance/java/com/nanzhufeng/ai/acceptance/SearchAttachmentAcceptanceApplication.kt").readText()

        listOf(".png", ".pdf", ".wav", ".mp4", ".md", ".json", ".zip", ".docx")
            .forEach { assertTrue(source.contains(it)) }
        listOf("ScheduledMonitorStatus.PAUSED", "ScheduledMonitorRunStatus.SUCCEEDED", "ScheduledMonitorRunStatus.FAILED")
            .forEach { assertTrue(source.contains(it)) }
        listOf("GlmOcrTaskStatus.PROCESSING", "GlmOcrTaskStatus.FAILED", "GlmOcrTaskStatus.COMPLETED")
            .forEach { assertTrue(source.contains(it)) }
        listOf("ContextSelectionAuditRecord", "ProviderDiagnosticRecord", "InvocationRecord")
            .forEach { assertTrue(source.contains(it)) }

        assertFalse(source.contains("WorkManager.getInstance"))
        assertFalse(source.contains("NotificationManager"))
        assertFalse(source.contains("loadCredential"))
        assertFalse(source.contains("transport.execute"))
    }
}
