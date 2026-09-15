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
    @Test fun `sixty eight to sixty nine preserves legacy titles without inventing a title version`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "title-revision-migration-${UUID.randomUUID()}.db"
        val helper = FrameworkSQLiteOpenHelperFactory().create(SupportSQLiteOpenHelper.Configuration.builder(context).name(name).callback(
            object : SupportSQLiteOpenHelper.Callback(68) {
                override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                    db.execSQL("CREATE TABLE conversations(id TEXT PRIMARY KEY NOT NULL,title TEXT NOT NULL)")
                    db.execSQL("INSERT INTO conversations VALUES('fixture','保留原标题')")
                }
                override fun onUpgrade(db: androidx.sqlite.db.SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
            }).build())
        try {
            NanfengAiDatabase.MIGRATION_68_69.migrate(helper.writableDatabase)
            helper.writableDatabase.query("SELECT title,titleRevision FROM conversations WHERE id='fixture'").use {
                assertTrue(it.moveToFirst()); assertEquals("保留原标题", it.getString(0)); assertTrue(it.isNull(1))
            }
        } finally { helper.close(); context.deleteDatabase(name) }
    }
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

    @Test fun `sixty six to sixty seven stores account isolated cloud list pins without content`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "cloud-conversation-presentation-${UUID.randomUUID()}.db"
        context.deleteDatabase(name)
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context).name(name).callback(
                object : SupportSQLiteOpenHelper.Callback(66) {
                    override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) = Unit
                    override fun onUpgrade(db: androidx.sqlite.db.SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                },
            ).build(),
        )
        val sqlite = helper.writableDatabase
        NanfengAiDatabase.MIGRATION_66_67.migrate(sqlite)
        sqlite.execSQL("INSERT INTO cloud_conversation_presentation VALUES ('account-a','conversation-a',1)")
        sqlite.query("SELECT accountRef,conversationId,cloudPinned FROM cloud_conversation_presentation").use {
            assertTrue(it.moveToFirst())
            assertEquals("account-a", it.getString(0))
            assertEquals("conversation-a", it.getString(1))
            assertEquals(1, it.getInt(2))
        }
        helper.close()
        context.deleteDatabase(name)
    }

    @Test fun `sixty seven to sixty eight stores portable model use without answer text or route`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "cloud-response-model-usage-${UUID.randomUUID()}.db"
        context.deleteDatabase(name)
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context).name(name).callback(
                object : SupportSQLiteOpenHelper.Callback(67) {
                    override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) = Unit
                    override fun onUpgrade(db: androidx.sqlite.db.SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                },
            ).build(),
        )
        val sqlite = helper.writableDatabase
        NanfengAiDatabase.MIGRATION_67_68.migrate(sqlite)
        sqlite.execSQL("INSERT INTO cloud_response_model_usages (conversationId,assistantMessageId,modelId,modelDisplayName,inputTokens,outputTokens,totalTokens,cachedInputTokens,reasoningTokens,costPriceVersion,costCurrencyCode,costTotalMicros,costSource) VALUES ('conversation-a','message-a','openai/gpt-6-astra','GPT-6 Astra',12,34,46,NULL,NULL,'provider-v1','USD',56,'PROVIDER_RESPONSE')")
        sqlite.query("PRAGMA table_info(cloud_response_model_usages)").use { cursor ->
            val columns = buildSet { while (cursor.moveToNext()) add(cursor.getString(cursor.getColumnIndexOrThrow("name"))) }
            assertEquals(setOf("conversationId", "assistantMessageId", "modelId", "modelDisplayName", "inputTokens", "outputTokens", "totalTokens", "cachedInputTokens", "reasoningTokens", "costPriceVersion", "costCurrencyCode", "costTotalMicros", "costSource"), columns)
        }
        sqlite.query("SELECT modelId,totalTokens,costTotalMicros FROM cloud_response_model_usages WHERE assistantMessageId='message-a'").use {
            assertTrue(it.moveToFirst())
            assertEquals("openai/gpt-6-astra", it.getString(0))
            assertEquals(46L, it.getLong(1))
            assertEquals(56L, it.getLong(2))
        }
        helper.close()
        context.deleteDatabase(name)
    }
}
