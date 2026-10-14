package com.nanzhufeng.ai.data

import android.content.Context
import android.content.ContextWrapper
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.nanzhufeng.ai.data.local.NanfengAiDatabase
import com.nanzhufeng.ai.data.local.RoomScheduledMonitorRepository
import com.nanzhufeng.ai.domain.*
import java.io.File
import java.time.Instant
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class OctoberScheduledModelMigrationTest {
    @Test fun `legacy recurring selections migrate across reopen while past attribution stays unchanged`() {
        val root = java.nio.file.Files.createTempDirectory("nf-monitor-upgrade").toFile()
        val context = object : ContextWrapper(ApplicationProvider.getApplicationContext<Context>()) {
            override fun getDatabasePath(name: String) = File(root, name)
        }
        fun open() = Room.databaseBuilder(context, NanfengAiDatabase::class.java, "monitor.db").allowMainThreadQueries().build()
        val legacy = listOf(ModelPresetId.CLAUDE_OPUS_5, ModelPresetId.CLAUDE_SONNET_5,
            ModelPresetId.CLAUDE_HAIKU_4_5, ModelPresetId.GPT_5_6_SOL, ModelPresetId.GPT_5_6_TERRA, ModelPresetId.GPT_5_6_LUNA)
        try {
            open().let { db ->
                try {
                    val repo = RoomScheduledMonitorRepository(db)
                    for (preset in legacy) {
                        val now = Instant.parse("2026-10-09T00:00:00Z")
                        repo.create(ScheduledMonitorTask(ScheduledMonitorTaskId(preset.name), "monitor", "public news", null,
                            ScheduledMonitorCadence.DAILY, preset, ScheduledMonitorStatus.ACTIVE, now,
                            lastModelId = "historical-" + preset.name, createdAt = now, updatedAt = now))
                        db.openHelper.writableDatabase.execSQL("UPDATE scheduled_monitor_tasks SET modelPresetId=? WHERE id=?", arrayOf(preset.name, preset.name))
                    }
                } finally { db.close() }
            }
            open().let { db ->
                try {
                    val repo = RoomScheduledMonitorRepository(db)
                    for (preset in legacy) {
                        val task = repo.find(ScheduledMonitorTaskId(preset.name))!!
                        assertEquals(NanfengModelServiceCatalog.currentPreset(preset), task.modelPresetId)
                        assertEquals("historical-" + preset.name, task.lastModelId)
                        assertEquals(task, repo.list().single { it.id == task.id })
                        assertEquals(task.modelPresetId, repo.setStatus(task.id, ScheduledMonitorStatus.PAUSED, task.updatedAt)!!.modelPresetId)
                    }
                } finally { db.close() }
            }
        } finally { root.deleteRecursively() }
    }
}
