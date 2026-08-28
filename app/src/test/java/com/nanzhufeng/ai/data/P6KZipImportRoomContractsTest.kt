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
    @Test fun `schema fifty eight to fifty nine adds exact and inferred library image counters`() {
        val context = ApplicationProvider.getApplicationContext<Context>(); val name = "p6k-library-image-counters-${UUID.randomUUID()}.db"; context.deleteDatabase(name)
        val helper = FrameworkSQLiteOpenHelperFactory().create(SupportSQLiteOpenHelper.Configuration.builder(context).name(name).callback(object : SupportSQLiteOpenHelper.Callback(58) {
            override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE p6k_zip_asset_recovery_jobs (taskId TEXT NOT NULL PRIMARY KEY, inferredGeneratedImages INTEGER NOT NULL)")
                db.execSQL("INSERT INTO p6k_zip_asset_recovery_jobs VALUES ('preserved',686)")
            }
            override fun onUpgrade(db: androidx.sqlite.db.SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
        }).build())
        val sqlite = helper.writableDatabase; NanfengAiDatabase.MIGRATION_58_59.migrate(sqlite)
        sqlite.query("SELECT inferredGeneratedImages,originLinkedLibraryImages,inferredLibraryImages FROM p6k_zip_asset_recovery_jobs WHERE taskId='preserved'").use {
            assertTrue(it.moveToFirst()); assertEquals(686, it.getInt(0)); assertEquals(0, it.getInt(1)); assertEquals(0, it.getInt(2))
        }
        helper.close(); context.deleteDatabase(name)
    }

    @Test fun `schema fifty seven to fifty eight preserves jobs and adds inferred image counter`() {
        val context = ApplicationProvider.getApplicationContext<Context>(); val name = "p6k-generated-image-counter-${UUID.randomUUID()}.db"; context.deleteDatabase(name)
        val helper = FrameworkSQLiteOpenHelperFactory().create(SupportSQLiteOpenHelper.Configuration.builder(context).name(name).callback(object : SupportSQLiteOpenHelper.Callback(57) {
            override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE p6k_zip_asset_recovery_jobs (taskId TEXT NOT NULL PRIMARY KEY, state TEXT NOT NULL, totalOccurrences INTEGER NOT NULL, linkedOccurrences INTEGER NOT NULL, totalConversations INTEGER NOT NULL, processedConversations INTEGER NOT NULL, failedConversations INTEGER NOT NULL, uniqueAssets INTEGER NOT NULL, missingEntries INTEGER NOT NULL, unattributedCandidates INTEGER NOT NULL, sourceReferenceRecords INTEGER NOT NULL, fallbackNamedAssets INTEGER NOT NULL, lastFailureKind TEXT, lastFailureAtMs INTEGER, indexVersion INTEGER NOT NULL, updatedAtMs INTEGER NOT NULL)")
                db.execSQL("INSERT INTO p6k_zip_asset_recovery_jobs VALUES ('preserved','COMPLETED',1541,1540,1,1,0,1539,1,136,1541,2,NULL,NULL,2,1)")
            }
            override fun onUpgrade(db: androidx.sqlite.db.SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
        }).build())
        val sqlite = helper.writableDatabase; NanfengAiDatabase.MIGRATION_57_58.migrate(sqlite)
        sqlite.query("SELECT uniqueAssets,inferredGeneratedImages FROM p6k_zip_asset_recovery_jobs WHERE taskId='preserved'").use {
            assertTrue(it.moveToFirst()); assertEquals(1539, it.getInt(0)); assertEquals(0, it.getInt(1))
        }
        helper.close(); context.deleteDatabase(name)
    }

    @Test fun `schema fifty six to fifty seven preserves jobs and adds honest source counters`() {
        val context = ApplicationProvider.getApplicationContext<Context>(); val name = "p6k-recovery-counters-${UUID.randomUUID()}.db"; context.deleteDatabase(name)
        val helper = FrameworkSQLiteOpenHelperFactory().create(SupportSQLiteOpenHelper.Configuration.builder(context).name(name).callback(object : SupportSQLiteOpenHelper.Callback(56) {
            override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE p6k_zip_asset_recovery_jobs (taskId TEXT NOT NULL PRIMARY KEY, state TEXT NOT NULL, totalOccurrences INTEGER NOT NULL, linkedOccurrences INTEGER NOT NULL, totalConversations INTEGER NOT NULL, processedConversations INTEGER NOT NULL, failedConversations INTEGER NOT NULL, uniqueAssets INTEGER NOT NULL, missingEntries INTEGER NOT NULL, unattributedCandidates INTEGER NOT NULL, lastFailureKind TEXT, lastFailureAtMs INTEGER, indexVersion INTEGER NOT NULL, updatedAtMs INTEGER NOT NULL)")
                db.execSQL("INSERT INTO p6k_zip_asset_recovery_jobs VALUES ('preserved','COMPLETED',854,853,1,1,0,853,1,822,NULL,NULL,1,1)")
            }
            override fun onUpgrade(db: androidx.sqlite.db.SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
        }).build())
        val sqlite = helper.writableDatabase; NanfengAiDatabase.MIGRATION_56_57.migrate(sqlite)
        sqlite.query("SELECT totalOccurrences,sourceReferenceRecords,fallbackNamedAssets FROM p6k_zip_asset_recovery_jobs WHERE taskId='preserved'").use {
            assertTrue(it.moveToFirst()); assertEquals(854, it.getInt(0)); assertEquals(0, it.getInt(1)); assertEquals(0, it.getInt(2))
        }
        helper.close(); context.deleteDatabase(name)
    }

    @Test fun `schema fifty five to fifty six appends durable content free ZIP recovery jobs`() {
        val context = ApplicationProvider.getApplicationContext<Context>(); val name = "p6k-recovery-job-${UUID.randomUUID()}.db"; context.deleteDatabase(name)
        val helper = FrameworkSQLiteOpenHelperFactory().create(SupportSQLiteOpenHelper.Configuration.builder(context).name(name).callback(object : SupportSQLiteOpenHelper.Callback(55) {
            override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) { db.execSQL("CREATE TABLE p6k_zip_import_tasks (id TEXT NOT NULL PRIMARY KEY)"); db.execSQL("INSERT INTO p6k_zip_import_tasks VALUES ('preserved')") }
            override fun onUpgrade(db: androidx.sqlite.db.SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
        }).build())
        val sqlite = helper.writableDatabase; NanfengAiDatabase.MIGRATION_55_56.migrate(sqlite)
        sqlite.query("SELECT id FROM p6k_zip_import_tasks").use { assertTrue(it.moveToFirst()); assertEquals("preserved", it.getString(0)) }
        sqlite.query("PRAGMA table_info(p6k_zip_asset_recovery_jobs)").use { columns ->
            val names = generateSequence { if (columns.moveToNext()) columns.getString(1) else null }.toSet()
            assertTrue(setOf("taskId", "state", "totalOccurrences", "linkedOccurrences", "processedConversations", "lastFailureKind", "indexVersion").all(names::contains))
            assertTrue("messageText" !in names && "entryName" !in names)
        }
        helper.close(); context.deleteDatabase(name)
    }

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

    @Test fun `schema thirty seven to thirty eight appends only content free local exact reuse rows`() {
        val context = ApplicationProvider.getApplicationContext<Context>(); val name = "p6l-reuse-migration-${UUID.randomUUID()}.db"; context.deleteDatabase(name)
        val helper = FrameworkSQLiteOpenHelperFactory().create(SupportSQLiteOpenHelper.Configuration.builder(context).name(name).callback(object : SupportSQLiteOpenHelper.Callback(37) {
            override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) { db.execSQL("CREATE TABLE conversations (id TEXT NOT NULL PRIMARY KEY)"); db.execSQL("INSERT INTO conversations VALUES ('preserved')") }
            override fun onUpgrade(db: androidx.sqlite.db.SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
        }).build())
        val sqlite = helper.writableDatabase; NanfengAiDatabase.MIGRATION_37_38.migrate(sqlite)
        sqlite.query("SELECT id FROM conversations").use { assertTrue(it.moveToFirst()); assertEquals("preserved", it.getString(0)) }
        sqlite.query("PRAGMA table_info(local_exact_reuse_entries)").use { columns -> val names = generateSequence { if (columns.moveToNext()) columns.getString(1) else null }.toSet(); assertTrue("text" !in names && "responseMessageId" in names && "canonicalRequestHash" in names) }
        helper.close(); context.deleteDatabase(name)
    }

    @Test fun `schema thirty eight instance upgrades without changing existing facts and appends v2 restore tables`() {
        val context = ApplicationProvider.getApplicationContext<Context>(); val name = "v2-restore-migration-${UUID.randomUUID()}.db"; context.deleteDatabase(name)
        val helper = FrameworkSQLiteOpenHelperFactory().create(SupportSQLiteOpenHelper.Configuration.builder(context).name(name).callback(object : SupportSQLiteOpenHelper.Callback(38) {
            override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) { db.execSQL("CREATE TABLE local_exact_reuse_entries (id TEXT NOT NULL PRIMARY KEY)"); db.execSQL("INSERT INTO local_exact_reuse_entries VALUES ('preserved-38')") }
            override fun onUpgrade(db: androidx.sqlite.db.SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
        }).build())
        val sqlite = helper.writableDatabase; NanfengAiDatabase.MIGRATION_38_39.migrate(sqlite); sqlite.version = 39
        sqlite.query("SELECT id FROM local_exact_reuse_entries").use { assertTrue(it.moveToFirst()); assertEquals("preserved-38", it.getString(0)) }
        listOf("workspace_exchange_v2_restore_receipts", "workspace_exchange_v2_restore_provenance", "workspace_exchange_v2_restore_settings").forEach { table -> sqlite.query("SELECT name FROM sqlite_master WHERE type='table' AND name='$table'").use { assertTrue(it.moveToFirst()) } }
        helper.close(); context.deleteDatabase(name)
    }
}
