package com.nanzhufeng.ai.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.nanzhufeng.ai.data.local.NanfengAiDatabase
import com.nanzhufeng.ai.data.local.RoomReminderDraftGenerationRecordStore
import com.nanzhufeng.ai.domain.ConversationCostSource
import com.nanzhufeng.ai.domain.ConversationId
import com.nanzhufeng.ai.domain.ProviderCost
import com.nanzhufeng.ai.domain.ProviderId
import com.nanzhufeng.ai.domain.ProviderUsage
import com.nanzhufeng.ai.domain.ReminderDraftGenerationId
import com.nanzhufeng.ai.domain.ReminderDraftGenerationRecord
import com.nanzhufeng.ai.domain.ReminderDraftGenerationStatus
import java.time.Instant
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ReminderDraftGenerationRoomContractsTest {
    @Test fun `reminder refinement accounting persists without retaining source text`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "reminder-draft-accounting-${UUID.randomUUID()}.db"
        context.deleteDatabase(name)
        val record = ReminderDraftGenerationRecord(
            ReminderDraftGenerationId("draft-call"), ConversationId("conversation"), Instant.EPOCH,
            ReminderDraftGenerationStatus.SUCCEEDED, ProviderId.QWEN, "qwen3.7-plus",
            ProviderUsage(12, 4), ProviderCost("qwen-cn-beijing-standard-2026-08-v1", "USD", 8),
            ConversationCostSource.LOCAL_ESTIMATE,
        )
        val first = Room.databaseBuilder(context, NanfengAiDatabase::class.java, name).allowMainThreadQueries().build()
        RoomReminderDraftGenerationRecordStore(first).record(record)
        first.close()
        val reopened = Room.databaseBuilder(context, NanfengAiDatabase::class.java, name).allowMainThreadQueries().build()
        assertEquals(listOf(record), RoomReminderDraftGenerationRecordStore(reopened).listNewestFirst())
        val columns = reopened.openHelper.writableDatabase.query("PRAGMA table_info(reminder_draft_generation_records)").use { cursor ->
            buildSet { while (cursor.moveToNext()) add(cursor.getString(cursor.getColumnIndexOrThrow("name"))) }
        }
        assertFalse(columns.any { it.contains("text", ignoreCase = true) || it.contains("prompt", ignoreCase = true) || it.contains("reply", ignoreCase = true) })
        reopened.close(); context.deleteDatabase(name)
    }
}
