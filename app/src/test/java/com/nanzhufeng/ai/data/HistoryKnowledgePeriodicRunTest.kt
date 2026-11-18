package com.nanzhufeng.ai.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.nanzhufeng.ai.domain.*
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class HistoryKnowledgePeriodicRunTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private val now = Instant.parse("2026-11-02T00:00:00Z")
    @Before fun clearFixture() {
        context.getSharedPreferences("history_knowledge_auto_curation_runs_v1", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test fun `periodic work with stable WorkManager id can run after each completed period and process reopen`() {
        val store = AndroidHistoryKnowledgeAutoCurationRunStore(context)
        assertEquals(HistoryKnowledgeAutoCurationBeginResult.STARTED, store.begin("periodic-id", HistoryKnowledgeAutoCurationTrigger.PERIODIC, now))
        store.finish("periodic-id", HistoryKnowledgeAutoCurationRunStatus.NO_CANDIDATE, "NO_TEXT", false, now)
        val reopened = AndroidHistoryKnowledgeAutoCurationRunStore(context)
        assertEquals(HistoryKnowledgeAutoCurationBeginResult.STARTED, reopened.begin("periodic-id", HistoryKnowledgeAutoCurationTrigger.PERIODIC, now.plusSeconds(43200)))
        reopened.finish("periodic-id", HistoryKnowledgeAutoCurationRunStatus.SAVED, null, false, now.plusSeconds(43201))
        assertEquals(HistoryKnowledgeAutoCurationBeginResult.STARTED, reopened.begin("periodic-id", HistoryKnowledgeAutoCurationTrigger.PERIODIC, now.plusSeconds(86400)))
    }

    @Test fun `one shot redelivery stays idempotent and interrupted periodic run remains unknown`() {
        val store = AndroidHistoryKnowledgeAutoCurationRunStore(context)
        store.begin("once", HistoryKnowledgeAutoCurationTrigger.CONVERSATION_IDLE, now)
        store.finish("once", HistoryKnowledgeAutoCurationRunStatus.SAVED, null, false, now)
        assertEquals(HistoryKnowledgeAutoCurationBeginResult.ALREADY_FINISHED, store.begin("once", HistoryKnowledgeAutoCurationTrigger.CONVERSATION_IDLE, now.plusSeconds(43200)))
        store.begin("periodic", HistoryKnowledgeAutoCurationTrigger.PERIODIC, now)
        assertEquals(HistoryKnowledgeAutoCurationBeginResult.INTERRUPTED, store.begin("periodic", HistoryKnowledgeAutoCurationTrigger.PERIODIC, now.plusSeconds(43200)))
        assertEquals(HistoryKnowledgeAutoCurationRunStatus.UNKNOWN, store.latest()!!.status)
    }
}
