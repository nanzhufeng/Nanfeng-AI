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
class P6KZipCommitMigrationContractsTest {
    @Test fun `schema thirty four to thirty five preserves ZIP candidates and appends only decision ledger fields`() {
        val context = ApplicationProvider.getApplicationContext<Context>(); val name = "p6k-k2-migration-${UUID.randomUUID()}.db"; context.deleteDatabase(name)
        val helper = FrameworkSQLiteOpenHelperFactory().create(SupportSQLiteOpenHelper.Configuration.builder(context).name(name).callback(object : SupportSQLiteOpenHelper.Callback(34) {
            override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) { db.execSQL("CREATE TABLE p6k_zip_import_items (taskId TEXT NOT NULL, id TEXT NOT NULL, ordinal INTEGER NOT NULL, sourceConversationId TEXT, title TEXT, createdAtEpochMs INTEGER, updatedAtEpochMs INTEGER, contentHash TEXT, failure TEXT, PRIMARY KEY(taskId,id))"); db.execSQL("INSERT INTO p6k_zip_import_items(taskId,id,ordinal,failure) VALUES ('task','item',0,NULL)") }
            override fun onUpgrade(db: androidx.sqlite.db.SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
        }).build())
        val sqlite = helper.writableDatabase; NanfengAiDatabase.MIGRATION_34_35.migrate(sqlite)
        sqlite.query("SELECT status, conversationId FROM p6k_zip_import_items WHERE taskId='task' AND id='item'").use { assertTrue(it.moveToFirst()); assertEquals("PENDING_CONFIRMATION", it.getString(0)); assertTrue(it.isNull(1)) }
        listOf("p6k_zip_import_provenance", "p6k_zip_import_receipts").forEach { table -> sqlite.query("SELECT name FROM sqlite_master WHERE type='table' AND name='$table'").use { assertTrue(it.moveToFirst()) } }
        helper.close(); context.deleteDatabase(name)
    }
}
