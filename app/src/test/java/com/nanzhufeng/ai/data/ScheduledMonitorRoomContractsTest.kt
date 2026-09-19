package com.nanzhufeng.ai.data

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScheduledMonitorRoomContractsTest {
    @Test
    fun schema46KeepsTasksAndRunsLocalWithCascadeCleanup() {
        val source = File("src/main/java/com/nanzhufeng/ai/data/local/NanfengAiDatabase.kt").readText()
        val migration = source.substringAfter("val MIGRATION_45_46").substringBefore("val MIGRATION_")

        for (token in listOf("scheduled_monitor_tasks", "scheduled_monitor_runs", "FOREIGN KEY(`taskId`) REFERENCES `scheduled_monitor_tasks`(`id`)", "ON DELETE CASCADE", "index_scheduled_monitor_tasks_status_nextRunAtEpochMs")) {
            assertTrue("missing schema 46 rule: $token", migration.contains(token))
        }
    }

    @Test
    fun schedulingUsesOneConnectedRequestAndRechecksPauseBeforeChaining() {
        val source = File("src/main/java/com/nanzhufeng/ai/data/ScheduledMonitorScheduling.kt").readText()

        for (token in listOf("NetworkType.CONNECTED", "enqueueUniqueWork", "nfai.scheduled-monitor.\${taskId.value}", "scheduledMonitorRepository.find(result.task.id)")) {
            assertTrue("missing scheduling boundary: $token", source.contains(token))
        }
        assertFalse(source.contains("PeriodicWorkRequestBuilder"))
    }

    @Test
    fun `monitor uses DeepSeek native search instead of relaying it and preserves provider supplied source links`() {
        val executor = File("src/main/java/com/nanzhufeng/ai/ai/ScheduledMonitorExecutor.kt").readText()

        assertTrue(executor.contains("if (!options.liveWebSearch) return fail(\"WEB_SEARCH_UNAVAILABLE\")"))
        assertTrue(executor.contains("ProviderId.DEEPSEEK -> ChatRequestOptions(OfficialWebSearchRoute.DEEPSEEK_MESSAGES)"))
        assertTrue(executor.contains("ProviderId.ZHIPU -> ChatRequestOptions(OfficialWebSearchRoute.ZHIPU_CHAT_COMPLETIONS)"))
        assertTrue(executor.contains("appendProviderWebSources(reply.text, reply.webSources)"))
        assertFalse(executor.contains("ProviderId.DEEPSEEK -> ChatRequestOptions(OfficialWebSearchRoute.QWEN_RESPONSES)"))
    }
}
