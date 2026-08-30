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
class P7FManualConversationSyncRoomContractsTest {
    @Test fun `sixty to sixty one adds content free per conversation receipts`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "manual-conversation-sync-${UUID.randomUUID()}.db"
        context.deleteDatabase(name)
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context).name(name).callback(
                object : SupportSQLiteOpenHelper.Callback(60) {
                    override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) = Unit
                    override fun onUpgrade(db: androidx.sqlite.db.SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                },
            ).build(),
        )
        val sqlite = helper.writableDatabase
        NanfengAiDatabase.MIGRATION_60_61.migrate(sqlite)
        sqlite.execSQL("INSERT INTO manual_conversation_sync_state VALUES ('account','conversation','conversation-doc',3,'${"a".repeat(64)}','${"b".repeat(64)}',10)")
        sqlite.query("SELECT accountRef,conversationId,remoteRevision,payloadHash,localContentHash,lastSyncedAtEpochMs FROM manual_conversation_sync_state").use {
            assertTrue(it.moveToFirst())
            assertEquals("account", it.getString(0))
            assertEquals("conversation", it.getString(1))
            assertEquals(3L, it.getLong(2))
            assertEquals("a".repeat(64), it.getString(3))
            assertEquals("b".repeat(64), it.getString(4))
            assertEquals(10L, it.getLong(5))
        }
        helper.close()
        context.deleteDatabase(name)
    }
}
