package com.nanzhufeng.ai.data

import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.nanzhufeng.ai.data.local.NanfengAiDatabase
import com.nanzhufeng.ai.domain.LocalBackupResult
import java.io.File
import java.util.UUID
import java.util.zip.ZipFile
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class P5DLocalBackupRestoreContractsTest {
    @Test fun `runtime backup and privacy owners use the actual build version`() {
        val container = File("src/main/java/com/nanzhufeng/ai/app/AppContainer.kt").readText()
        assertTrue(container.contains("import com.nanzhufeng.ai.BuildConfig"))
        assertTrue(container.contains("AndroidPrivacyDataManager(context, database, BuildConfig.VERSION_NAME)"))
        assertTrue(container.contains("AndroidLocalBackupRestoreManager(context, database, BuildConfig.VERSION_NAME)"))
        assertFalse(container.contains("\"0.3.0-p5d\""))
    }

    @Test fun `manual package is consistent manifest checked and excludes non business roots`() {
        val context = ApplicationProvider.getApplicationContext<Context>(); context.deleteDatabase("nanfeng-ai.db")
        val attachment = File(context.filesDir, "attachments/v1/p5d-fixture.bin").also { it.parentFile?.mkdirs(); it.writeBytes(byteArrayOf(1, 2, 3)) }
        val database = Room.databaseBuilder(context, NanfengAiDatabase::class.java, "nanfeng-ai.db").build()
        val target = File(context.cacheDir, "p5d-${UUID.randomUUID()}.nfai-backup")
        try {
            val manager = AndroidLocalBackupRestoreManager(context, database, "test")
            val exported = manager.export(Uri.fromFile(target))
            assertTrue(exported is LocalBackupResult.Exported)
            ZipFile(target).use { zip ->
                assertTrue(zip.getEntry("manifest.json") != null)
                assertTrue(zip.getEntry("database/nanfeng-ai.snapshot") != null)
                assertTrue(zip.getEntry("assets/attachments/v1/p5d-fixture.bin") != null)
                assertFalse(zip.entries().toList().any { it.name.contains("diagnostic") || it.name.contains("exports/") || it.name.contains("provider_credentials") })
                assertEquals(database.openHelper.writableDatabase.version, JSONObject(zip.getInputStream(zip.getEntry("manifest.json")).bufferedReader().readText()).getInt("schemaVersion"))
            }
            val preflight = manager.preflight(Uri.fromFile(target))
            assertTrue(preflight is LocalBackupResult.Preflighted)
        } finally { database.close(); context.deleteDatabase("nanfeng-ai.db"); attachment.delete(); target.delete() }
    }

    @Test fun `high sensitive database content rejects entire backup`() {
        val context = ApplicationProvider.getApplicationContext<Context>(); context.deleteDatabase("nanfeng-ai.db")
        val database = Room.databaseBuilder(context, NanfengAiDatabase::class.java, "nanfeng-ai.db").build()
        val target = File(context.cacheDir, "p5d-secret-${UUID.randomUUID()}.nfai-backup")
        try {
            database.openHelper.writableDatabase.execSQL("INSERT INTO capture_drafts (id,text,createdAtEpochMs,schemaVersion) VALUES ('p5d-secret','api_key=abcdefghijklmnop',0,1)")
            assertTrue(AndroidLocalBackupRestoreManager(context, database, "test").export(Uri.fromFile(target)) is LocalBackupResult.Rejected)
        } finally { database.close(); context.deleteDatabase("nanfeng-ai.db"); target.delete() }
    }

    @Test fun `all historical migrations form an exact one through current schema chain`() {
        val migrations = listOf(
            NanfengAiDatabase.MIGRATION_1_2, NanfengAiDatabase.MIGRATION_2_3, NanfengAiDatabase.MIGRATION_3_4,
            NanfengAiDatabase.MIGRATION_4_5, NanfengAiDatabase.MIGRATION_5_6, NanfengAiDatabase.MIGRATION_6_7,
            NanfengAiDatabase.MIGRATION_7_8, NanfengAiDatabase.MIGRATION_8_9, NanfengAiDatabase.MIGRATION_9_10,
            NanfengAiDatabase.MIGRATION_10_11, NanfengAiDatabase.MIGRATION_11_12, NanfengAiDatabase.MIGRATION_12_13,
            NanfengAiDatabase.MIGRATION_13_14, NanfengAiDatabase.MIGRATION_14_15, NanfengAiDatabase.MIGRATION_15_16,
            NanfengAiDatabase.MIGRATION_16_17, NanfengAiDatabase.MIGRATION_17_18, NanfengAiDatabase.MIGRATION_18_19,
            NanfengAiDatabase.MIGRATION_19_20, NanfengAiDatabase.MIGRATION_20_21, NanfengAiDatabase.MIGRATION_21_22,
            NanfengAiDatabase.MIGRATION_22_23, NanfengAiDatabase.MIGRATION_23_24, NanfengAiDatabase.MIGRATION_24_25,
            NanfengAiDatabase.MIGRATION_25_26, NanfengAiDatabase.MIGRATION_26_27, NanfengAiDatabase.MIGRATION_27_28,
            NanfengAiDatabase.MIGRATION_28_29, NanfengAiDatabase.MIGRATION_29_30, NanfengAiDatabase.MIGRATION_30_31,
            NanfengAiDatabase.MIGRATION_31_32, NanfengAiDatabase.MIGRATION_32_33, NanfengAiDatabase.MIGRATION_33_34,
            NanfengAiDatabase.MIGRATION_34_35, NanfengAiDatabase.MIGRATION_35_36, NanfengAiDatabase.MIGRATION_36_37,
            NanfengAiDatabase.MIGRATION_37_38, NanfengAiDatabase.MIGRATION_38_39,
            NanfengAiDatabase.MIGRATION_39_40, NanfengAiDatabase.MIGRATION_40_41,
            NanfengAiDatabase.MIGRATION_41_42, NanfengAiDatabase.MIGRATION_42_43,
            NanfengAiDatabase.MIGRATION_43_44,
            NanfengAiDatabase.MIGRATION_44_45,
            NanfengAiDatabase.MIGRATION_45_46,
            NanfengAiDatabase.MIGRATION_46_47,
            NanfengAiDatabase.MIGRATION_47_48,
        )
        val context = ApplicationProvider.getApplicationContext<Context>(); context.deleteDatabase("nanfeng-ai.db")
        val database = Room.databaseBuilder(context, NanfengAiDatabase::class.java, "nanfeng-ai.db").build()
        try {
            val currentSchema = database.openHelper.writableDatabase.version
            assertEquals((1 until currentSchema).toList(), migrations.map { it.startVersion })
            assertEquals((2..currentSchema).toList(), migrations.map { it.endVersion })
        } finally { database.close(); context.deleteDatabase("nanfeng-ai.db") }
    }
}
