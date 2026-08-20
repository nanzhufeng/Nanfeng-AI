package com.nanzhufeng.ai.data

import android.content.Context
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.test.core.app.ApplicationProvider
import com.nanzhufeng.ai.data.local.NanfengAiDatabase
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class P3GConversationAttachmentMigrationContractsTest {
    @Test fun `schema seven to eight preserves P2 asset and redacts conversation storage keys`() {
        val context = ApplicationProvider.getApplicationContext<Context>(); val name = "p3g-${UUID.randomUUID()}.db"; context.deleteDatabase(name)
        val helper = FrameworkSQLiteOpenHelperFactory().create(SupportSQLiteOpenHelper.Configuration.builder(context).name(name).callback(object : SupportSQLiteOpenHelper.Callback(7) {
            override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE capture_draft_attachments (draftId TEXT NOT NULL, position INTEGER NOT NULL, attachmentId TEXT NOT NULL, storageKey TEXT NOT NULL, mimeType TEXT NOT NULL, displayName TEXT, byteCount INTEGER, sha256 TEXT, PRIMARY KEY(draftId, position))")
                db.execSQL("CREATE TABLE knowledge_attachments (knowledgeId TEXT NOT NULL, position INTEGER NOT NULL, attachmentId TEXT NOT NULL, storageKey TEXT NOT NULL, mimeType TEXT NOT NULL, displayName TEXT, byteCount INTEGER NOT NULL, sha256 TEXT NOT NULL, PRIMARY KEY(knowledgeId, position))")
                db.execSQL("CREATE TABLE message_content_blocks (messageId TEXT NOT NULL, position INTEGER NOT NULL, kind TEXT NOT NULL, textContent TEXT, attachmentId TEXT, storageKey TEXT, mimeType TEXT, displayName TEXT, byteCount INTEGER, sha256 TEXT, toolName TEXT, toolSafeSummary TEXT, schemaVersion INTEGER NOT NULL, PRIMARY KEY(messageId, position))")
                db.execSQL("CREATE TABLE conversation_draft_attachments (conversationId TEXT NOT NULL, position INTEGER NOT NULL, attachmentId TEXT NOT NULL, storageKey TEXT NOT NULL, mimeType TEXT NOT NULL, displayName TEXT, byteCount INTEGER, sha256 TEXT, PRIMARY KEY(conversationId, position))")
                db.execSQL("INSERT INTO capture_draft_attachments VALUES ('d', 0, 'a', 'attachments/v1/a.png', 'image/png', 'a.png', 3, '${"a".repeat(64)}')")
                db.execSQL("INSERT INTO message_content_blocks VALUES ('m', 0, 'ATTACHMENT', NULL, 'a', 'attachments/v1/a.png', 'image/png', 'a.png', 3, '${"a".repeat(64)}', NULL, NULL, 1)")
                db.execSQL("INSERT INTO conversation_draft_attachments VALUES ('c', 0, 'a', 'attachments/v1/a.png', 'image/png', 'a.png', 3, '${"a".repeat(64)}')")
            }
            override fun onUpgrade(db: androidx.sqlite.db.SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
        }).build())
        val db = helper.writableDatabase; NanfengAiDatabase.MIGRATION_7_8.migrate(db)
        db.query("SELECT storageKey FROM private_attachment_assets WHERE attachmentId='a'").use { assertTrue(it.moveToFirst()); assertEquals("attachments/v1/a.png", it.getString(0)) }
        listOf("message_content_blocks", "conversation_draft_attachments").forEach { table ->
            db.query("SELECT storageKey FROM $table WHERE attachmentId='a'").use { assertTrue(it.moveToFirst()); assertEquals("a", it.getString(0)); assertFalse(it.getString(0).contains("attachments/")) }
        }
        helper.close(); context.deleteDatabase(name)
    }
}
