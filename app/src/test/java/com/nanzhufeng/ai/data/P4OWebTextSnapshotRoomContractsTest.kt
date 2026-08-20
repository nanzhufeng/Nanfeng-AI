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
class P4OWebTextSnapshotRoomContractsTest {
    @Test fun `schema sixteen to seventeen retains old pdf table and adds only web snapshot tables`() {
        val context = ApplicationProvider.getApplicationContext<Context>(); val name = "p4o-${UUID.randomUUID()}.db"; context.deleteDatabase(name)
        val helper = FrameworkSQLiteOpenHelperFactory().create(SupportSQLiteOpenHelper.Configuration.builder(context).name(name).callback(object : SupportSQLiteOpenHelper.Callback(16) { override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) { db.execSQL("CREATE TABLE pdf_text_import_tasks (id TEXT NOT NULL PRIMARY KEY)"); db.execSQL("INSERT INTO pdf_text_import_tasks VALUES ('preserved')") }; override fun onUpgrade(db: androidx.sqlite.db.SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit }).build())
        val sqlite = helper.writableDatabase; NanfengAiDatabase.MIGRATION_16_17.migrate(sqlite)
        sqlite.query("SELECT id FROM pdf_text_import_tasks").use { assertTrue(it.moveToFirst()); assertEquals("preserved", it.getString(0)) }
        listOf("web_text_snapshot_tasks", "web_text_snapshot_items").forEach { table -> sqlite.query("SELECT name FROM sqlite_master WHERE type='table' AND name='$table'").use { assertTrue(it.moveToFirst()) } }
        helper.close(); context.deleteDatabase(name)
    }
}
