package com.nanzhufeng.ai

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class HistoryKnowledgeColdProcessSchedulingContractsTest {
    @Test
    fun manifestRegistersLazyWorkManagerConfigurationProvider() {
        val manifest = File("src/main/AndroidManifest.xml").readText()
        val application = File("src/main/java/com/nanzhufeng/ai/NanfengAiApplication.kt").readText()

        assertTrue(manifest.contains("android:name=\".NanfengAiApplication\""))
        assertTrue(manifest.contains("androidx.startup.InitializationProvider"))
        assertTrue(manifest.contains("tools:node=\"remove\""))
        assertTrue(application.contains("Configuration.Provider"))
        assertTrue(application.contains("override val workManagerConfiguration"))
    }

    @Test
    fun periodicScheduleKeepsItsOriginalTwelveHourCadence() {
        val scheduler = File("src/main/java/com/nanzhufeng/ai/data/AndroidHistoryKnowledgeAutoCuration.kt").readText()

        assertTrue(scheduler.contains("PeriodicWorkRequestBuilder<HistoryKnowledgeAutoCurationWorker>(12, TimeUnit.HOURS)"))
        assertTrue(scheduler.contains("ExistingPeriodicWorkPolicy.KEEP"))
        assertTrue(scheduler.contains("HistoryKnowledgeAutoCurationTrigger.PERIODIC"))
    }

    @Test
    fun everyWorkerRunHasSeparateNonBillingDiagnostics() {
        val worker = File("src/main/java/com/nanzhufeng/ai/data/AndroidHistoryKnowledgeAutoCuration.kt").readText()
        val ledger = File("src/main/java/com/nanzhufeng/ai/ui/ConversationCostLedgerUi.kt").readText()

        assertTrue(worker.contains("runStore.begin(id.toString(), trigger"))
        assertTrue(worker.contains("runStore.finish(id.toString(), status, reason"))
        assertTrue(worker.contains("INTERRUPTED_WORK_REDELIVERY"))
        assertTrue(ledger.contains("最近自动检查"))
        assertTrue(ledger.contains("本轮未调用模型"))
        assertTrue(ledger.contains("为避免重复发送，未自动重发"))
        assertTrue(ledger.contains("historyCurationCalls.mapNotNull(DirectChatCallAuditRecord::estimatedCost)"))
    }
}
