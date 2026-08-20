package com.nanzhufeng.ai.data

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.nanzhufeng.ai.data.local.NanfengAiDatabase
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Exercises every persisted upgrade from the pre-agent schema through the current chat/work
 * split.  Single-migration tests are useful diagnostics, but this protects an existing user
 * database from an incompatibility introduced by their combination.
 */
@RunWith(RobolectricTestRunner::class)
class P6JUpgradeChainRoomContractsTest {
    @Test fun `schema eighteen upgrades continuously to chat work schema without losing the conversation`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "p6j-upgrade-${UUID.randomUUID()}.db"
        context.deleteDatabase(name)
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context).name(name).callback(object : SupportSQLiteOpenHelper.Callback(18) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    db.execSQL("CREATE TABLE conversations (id TEXT NOT NULL PRIMARY KEY, title TEXT NOT NULL, archivedAtEpochMs INTEGER, deletedAtEpochMs INTEGER, updatedAtEpochMs INTEGER NOT NULL)")
                    db.execSQL("CREATE TABLE conversation_management_intents (intentId TEXT NOT NULL PRIMARY KEY)")
                    db.execSQL("INSERT INTO conversations VALUES ('existing', '已有对话', NULL, NULL, 1)")
                    db.execSQL("INSERT INTO conversation_management_intents VALUES ('intent-existing')")
                }

                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
            }).build(),
        )
        val sqlite = helper.writableDatabase
        try {
            listOf(
                NanfengAiDatabase.MIGRATION_18_19,
                NanfengAiDatabase.MIGRATION_19_20,
                NanfengAiDatabase.MIGRATION_20_21,
                NanfengAiDatabase.MIGRATION_21_22,
                NanfengAiDatabase.MIGRATION_22_23,
                NanfengAiDatabase.MIGRATION_23_24,
                NanfengAiDatabase.MIGRATION_24_25,
                NanfengAiDatabase.MIGRATION_25_26,
                NanfengAiDatabase.MIGRATION_26_27,
                NanfengAiDatabase.MIGRATION_27_28,
                NanfengAiDatabase.MIGRATION_28_29,
                NanfengAiDatabase.MIGRATION_29_30,
                NanfengAiDatabase.MIGRATION_30_31,
                NanfengAiDatabase.MIGRATION_31_32,
            ).forEach { it.migrate(sqlite) }

            sqlite.query("SELECT title, revision, autoTitlePending, surface FROM conversations WHERE id='existing'").use {
                assertTrue(it.moveToFirst())
                assertEquals("已有对话", it.getString(0))
                assertEquals(1, it.getInt(1))
                assertEquals(0, it.getInt(2))
                assertEquals("CHAT", it.getString(3))
            }
            assertColumns(sqlite, "conversation_management_intents", "expectedRevision", "resultRevision")
            listOf(
                "sync_jobs",
                "agent_runs",
                "p9b_integration_sessions",
                "temporary_conversation_recovery",
                "local_search_index",
                "chatgpt_export_import_tasks",
                "claude_export_import_tasks",
                "nanfeng_knowledge_export_import_tasks",
                "conversation_real_text_executions",
                "usage_ledger_entries",
                "compare_conversation_sessions",
                "compare_conversation_branches",
                "compare_branch_runtime_states",
            ).forEach { table ->
                sqlite.query("SELECT 1 FROM sqlite_master WHERE type='table' AND name='$table'").use {
                    assertTrue("missing $table", it.moveToFirst())
                }
            }
            sqlite.query("SELECT 1 FROM sqlite_master WHERE type='index' AND name='index_conversations_surface_deletedAtEpochMs_archivedAtEpochMs_updatedAtEpochMs'").use {
                assertTrue(it.moveToFirst())
            }
        } finally {
            helper.close()
            context.deleteDatabase(name)
        }
    }

    private fun assertColumns(database: SupportSQLiteDatabase, table: String, vararg expected: String) {
        val columns = mutableSetOf<String>()
        database.query("PRAGMA table_info($table)").use { cursor ->
            while (cursor.moveToNext()) columns += cursor.getString(cursor.getColumnIndexOrThrow("name"))
        }
        expected.forEach { assertTrue("$table missing $it", columns.contains(it)) }
    }
}
