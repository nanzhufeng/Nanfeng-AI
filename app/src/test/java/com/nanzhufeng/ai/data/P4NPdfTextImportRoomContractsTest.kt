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
class P4NPdfTextImportRoomContractsTest {
    @Test fun `schema fifteen to sixteen retains old tables and adds only pdf import tables`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "p4n-${UUID.randomUUID()}.db"
        context.deleteDatabase(name)
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context).name(name).callback(
                object : SupportSQLiteOpenHelper.Callback(15) {
                    override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                        db.execSQL("CREATE TABLE knowledge_relationships (id TEXT NOT NULL PRIMARY KEY)")
                        db.execSQL("INSERT INTO knowledge_relationships VALUES ('preserved')")
                    }
                    override fun onUpgrade(db: androidx.sqlite.db.SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                }
            ).build()
        )
        val sqlite = helper.writableDatabase
        NanfengAiDatabase.MIGRATION_15_16.migrate(sqlite)
        sqlite.query("SELECT id FROM knowledge_relationships").use {
            assertTrue(it.moveToFirst())
            assertEquals("preserved", it.getString(0))
        }
        listOf("pdf_text_import_tasks", "pdf_text_import_pages", "pdf_text_import_items").forEach { table ->
            sqlite.query("SELECT name FROM sqlite_master WHERE type='table' AND name='$table'").use { assertTrue(it.moveToFirst()) }
        }
        helper.close()
        context.deleteDatabase(name)
    }
}
