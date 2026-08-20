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
class P6KZipImportRoomContractsTest {
    @Test fun `schema thirty three to thirty four preserves existing facts and appends only private ZIP candidate tables`() {
        val context = ApplicationProvider.getApplicationContext<Context>(); val name = "p6k-${UUID.randomUUID()}.db"; context.deleteDatabase(name)
        val helper = FrameworkSQLiteOpenHelperFactory().create(SupportSQLiteOpenHelper.Configuration.builder(context).name(name).callback(object : SupportSQLiteOpenHelper.Callback(33) {
            override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) { db.execSQL("CREATE TABLE compare_branch_execution_receipts (executionId TEXT NOT NULL PRIMARY KEY)"); db.execSQL("INSERT INTO compare_branch_execution_receipts VALUES ('preserved')") }
            override fun onUpgrade(db: androidx.sqlite.db.SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
        }).build())
        val sqlite = helper.writableDatabase; NanfengAiDatabase.MIGRATION_33_34.migrate(sqlite)
        sqlite.query("SELECT executionId FROM compare_branch_execution_receipts").use { assertTrue(it.moveToFirst()); assertEquals("preserved", it.getString(0)) }
        listOf("p6k_zip_import_tasks", "p6k_zip_import_items", "p6k_zip_import_messages", "p6k_zip_asset_candidates", "p6k_zip_profile_candidates").forEach { table -> sqlite.query("SELECT name FROM sqlite_master WHERE type='table' AND name='$table'").use { assertTrue(it.moveToFirst()) } }
        helper.close(); context.deleteDatabase(name)
    }

    @Test fun `schema thirty five to thirty six appends the one reversible profile owner without account tables`() {
        val context = ApplicationProvider.getApplicationContext<Context>(); val name = "p6k-profile-migration-${UUID.randomUUID()}.db"; context.deleteDatabase(name)
        val helper = FrameworkSQLiteOpenHelperFactory().create(SupportSQLiteOpenHelper.Configuration.builder(context).name(name).callback(object : SupportSQLiteOpenHelper.Callback(35) {
            override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) { db.execSQL("CREATE TABLE p6k_zip_import_tasks (id TEXT NOT NULL PRIMARY KEY)"); db.execSQL("INSERT INTO p6k_zip_import_tasks VALUES ('preserved')") }
            override fun onUpgrade(db: androidx.sqlite.db.SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
        }).build())
        val sqlite = helper.writableDatabase; NanfengAiDatabase.MIGRATION_35_36.migrate(sqlite)
        sqlite.query("SELECT id FROM p6k_zip_import_tasks").use { assertTrue(it.moveToFirst()); assertEquals("preserved", it.getString(0)) }
        listOf("third_party_profile_personalization_settings", "p6k_profile_import_provenance", "p6k_profile_import_receipts").forEach { table -> sqlite.query("SELECT name FROM sqlite_master WHERE type='table' AND name='$table'").use { assertTrue(it.moveToFirst()) } }
        helper.close(); context.deleteDatabase(name)
    }

    @Test fun `schema thirty six to thirty seven appends only explicit asset message receipts`() {
        val context = ApplicationProvider.getApplicationContext<Context>(); val name = "p6k-manual-link-migration-${UUID.randomUUID()}.db"; context.deleteDatabase(name)
        val helper = FrameworkSQLiteOpenHelperFactory().create(SupportSQLiteOpenHelper.Configuration.builder(context).name(name).callback(object : SupportSQLiteOpenHelper.Callback(36) {
            override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) { db.execSQL("CREATE TABLE p6k_zip_asset_candidates (taskId TEXT NOT NULL, entryName TEXT NOT NULL, sha256 TEXT NOT NULL, byteCount INTEGER NOT NULL, mimeType TEXT NOT NULL, role TEXT NOT NULL, sourceConversationId TEXT, sourceMessageId TEXT, PRIMARY KEY(taskId,entryName))"); db.execSQL("INSERT INTO p6k_zip_asset_candidates VALUES ('task','asset','hash',1,'image/png','UNMAPPED_REJECTED',NULL,NULL)") }
            override fun onUpgrade(db: androidx.sqlite.db.SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
        }).build())
        val sqlite = helper.writableDatabase; NanfengAiDatabase.MIGRATION_36_37.migrate(sqlite)
        sqlite.query("SELECT role,linkedConversationId,linkedMessageId,attachmentId,failure FROM p6k_zip_asset_candidates").use { assertTrue(it.moveToFirst()); assertEquals("UNMAPPED_REJECTED", it.getString(0)); assertEquals(null, it.getString(1)) }
        listOf("p6k_zip_asset_link_receipts", "p6k_zip_asset_link_provenance").forEach { table -> sqlite.query("SELECT name FROM sqlite_master WHERE type='table' AND name='$table'").use { assertTrue(it.moveToFirst()) } }
        helper.close(); context.deleteDatabase(name)
    }
}
