package com.nanzhufeng.ai.data

import android.content.Context
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

@RunWith(RobolectricTestRunner::class)
class P6IClaudeImportRoomContractsTest {
    @Test fun `schema twenty seven to twenty eight preserves conversations and appends only claude import tables`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "p6i-${UUID.randomUUID()}.db"
        context.deleteDatabase(name)
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context).name(name).callback(object : SupportSQLiteOpenHelper.Callback(27) {
                override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                    db.execSQL("CREATE TABLE conversations (id TEXT NOT NULL PRIMARY KEY, title TEXT NOT NULL)")
                    db.execSQL("INSERT INTO conversations VALUES ('preserved', '已有对话')")
                }
                override fun onUpgrade(db: androidx.sqlite.db.SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
            }).build(),
        )
        try {
            val sqlite = helper.writableDatabase
            NanfengAiDatabase.MIGRATION_27_28.migrate(sqlite)
            sqlite.query("SELECT title FROM conversations WHERE id='preserved'").use { assertTrue(it.moveToFirst()); assertEquals("已有对话", it.getString(0)) }
            listOf("claude_export_import_tasks", "claude_export_import_items", "claude_export_import_messages", "claude_import_provenance", "claude_import_receipts").forEach { table ->
                sqlite.query("SELECT name FROM sqlite_master WHERE type='table' AND name='$table'").use { assertTrue("missing $table", it.moveToFirst()) }
            }
        } finally {
            helper.close()
            context.deleteDatabase(name)
        }
    }
}
