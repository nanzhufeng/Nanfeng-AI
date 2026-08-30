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
class GlmOcrRoomMigrationContractsTest {
    @Test fun `sixty one to sixty two creates source result lineage table`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "glm-ocr-${UUID.randomUUID()}.db"
        context.deleteDatabase(name)
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context).name(name).callback(
                object : SupportSQLiteOpenHelper.Callback(61) {
                    override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) = Unit
                    override fun onUpgrade(db: androidx.sqlite.db.SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                },
            ).build(),
        )
        val sqlite = helper.writableDatabase
        sqlite.execSQL("CREATE TABLE private_attachment_assets (attachmentId TEXT NOT NULL PRIMARY KEY, storageKey TEXT NOT NULL, mimeType TEXT NOT NULL, displayName TEXT, byteCount INTEGER NOT NULL, sha256 TEXT NOT NULL, schemaVersion INTEGER NOT NULL)")
        sqlite.execSQL("INSERT INTO private_attachment_assets VALUES ('source','attachments/v1/source','application/pdf','source.pdf',12,'${"a".repeat(64)}',1)")
        NanfengAiDatabase.MIGRATION_61_62.migrate(sqlite)
        sqlite.execSQL("INSERT INTO glm_ocr_tasks VALUES ('task','source',NULL,'source.pdf','application/pdf',12,'${"a".repeat(64)}','READY',NULL,NULL,NULL,NULL,NULL,NULL,1,1)")
        sqlite.query("SELECT sourceAttachmentId,sourceDisplayName,status FROM glm_ocr_tasks").use {
            assertTrue(it.moveToFirst())
            assertEquals("source", it.getString(0))
            assertEquals("source.pdf", it.getString(1))
            assertEquals("READY", it.getString(2))
        }
        helper.close()
        context.deleteDatabase(name)
    }
}
