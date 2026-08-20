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
class P6HChatGptImportRoomContractsTest {
    @Test fun `schema twenty four to twenty five preserves prior rows and appends only chatgpt import tables`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "p6h-${UUID.randomUUID()}.db"
        context.deleteDatabase(name)
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context).name(name).callback(
                object : SupportSQLiteOpenHelper.Callback(24) {
                    override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                        db.execSQL("CREATE TABLE local_search_index (id TEXT NOT NULL PRIMARY KEY)")
                        db.execSQL("INSERT INTO local_search_index VALUES ('preserved')")
                    }
                    override fun onUpgrade(db: androidx.sqlite.db.SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                },
            ).build(),
        )
        val sqlite = helper.writableDatabase
        NanfengAiDatabase.MIGRATION_24_25.migrate(sqlite)
        sqlite.query("SELECT id FROM local_search_index").use { assertTrue(it.moveToFirst()); assertEquals("preserved", it.getString(0)) }
        listOf("chatgpt_export_import_tasks", "chatgpt_export_import_items", "chatgpt_export_import_messages", "chatgpt_import_provenance", "chatgpt_import_receipts").forEach { table ->
            sqlite.query("SELECT name FROM sqlite_master WHERE type='table' AND name='$table'").use { assertTrue(it.moveToFirst()) }
        }
        helper.close()
        context.deleteDatabase(name)
    }
}
