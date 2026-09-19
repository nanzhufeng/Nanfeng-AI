package com.nanzhufeng.ai.data

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.nanzhufeng.ai.data.local.NanfengAiDatabase
import com.nanzhufeng.ai.data.local.RoomAssistantResponseModelAttributionStore
import com.nanzhufeng.ai.data.local.RoomNormalChatSendAttemptStore
import com.nanzhufeng.ai.domain.AssistantResponseModelAttribution
import com.nanzhufeng.ai.domain.ConversationId
import com.nanzhufeng.ai.domain.MessageNodeId
import com.nanzhufeng.ai.domain.NormalChatSendAttempt
import com.nanzhufeng.ai.domain.NormalChatSendAttemptId
import com.nanzhufeng.ai.domain.NormalChatSendAttemptStatus
import com.nanzhufeng.ai.domain.ProviderId
import com.nanzhufeng.ai.domain.ProviderUsage
import com.nanzhufeng.ai.domain.ProviderCost
import com.nanzhufeng.ai.domain.ConversationCostSource
import com.nanzhufeng.ai.domain.ConversationStyle
import java.time.Instant
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AssistantResponseModelAttributionRoomContractsTest {
    @Test fun `completed response enriches prewritten attribution with immutable execution evidence`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "assistant-model-enrichment-${UUID.randomUUID()}.db"
        context.deleteDatabase(name)
        val database = open(context, name)
        val store = RoomAssistantResponseModelAttributionStore(database)
        val initial = AssistantResponseModelAttribution(
            assistantMessageId = MessageNodeId("assistant-model-enrichment-message"),
            attemptId = NormalChatSendAttemptId("assistant-model-enrichment-attempt"),
            providerId = ProviderId.OPENROUTER,
            receiverProviderId = ProviderId.OPENROUTER,
            modelId = "openai/gpt-5.6",
            modelDisplayName = "GPT-5.6",
            recordedAt = Instant.EPOCH,
        )
        store.record(initial)

        store.record(initial.copy(
            conversationStyle = ConversationStyle.DIRECT,
            webSearchUsed = true,
            webSearchRequested = true,
            usage = ProviderUsage(inputTokens = 12, outputTokens = 4, totalTokens = 16),
            cost = ProviderCost("provider-response", "USD", 42L),
            costSource = ConversationCostSource.PROVIDER_RESPONSE,
        ))

        val restored = store.forMessages(listOf(initial.assistantMessageId)).getValue(initial.assistantMessageId).single()
        assertEquals(ConversationStyle.DIRECT, restored.conversationStyle)
        assertEquals(true, restored.webSearchUsed)
        assertEquals(true, restored.webSearchRequested)
        assertEquals(16L, restored.usage.totalTokens)
        assertEquals(42L, restored.cost.totalMicros)
        database.close(); context.deleteDatabase(name)
    }

    @Test fun `completed response cannot replace previously recorded execution evidence`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "assistant-model-evidence-conflict-${UUID.randomUUID()}.db"
        context.deleteDatabase(name)
        val database = open(context, name)
        val store = RoomAssistantResponseModelAttributionStore(database)
        val recorded = AssistantResponseModelAttribution(
            assistantMessageId = MessageNodeId("assistant-model-conflict-message"),
            attemptId = NormalChatSendAttemptId("assistant-model-conflict-attempt"),
            providerId = ProviderId.OPENROUTER,
            receiverProviderId = ProviderId.OPENROUTER,
            modelId = "openai/gpt-5.6",
            modelDisplayName = "GPT-5.6",
            conversationStyle = ConversationStyle.DIRECT,
            webSearchUsed = true,
            webSearchRequested = true,
            recordedAt = Instant.EPOCH,
        )
        store.record(recorded)

        val failure = runCatching { store.record(recorded.copy(conversationStyle = ConversationStyle.PROFESSIONAL)) }.exceptionOrNull()

        assertTrue(failure is IllegalArgumentException)
        assertEquals(ConversationStyle.DIRECT, store.forMessages(listOf(recorded.assistantMessageId)).getValue(recorded.assistantMessageId).single().conversationStyle)
        database.close(); context.deleteDatabase(name)
    }

    @Test fun `cost list projects only the matching completed model attempt duration`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "assistant-model-duration-${UUID.randomUUID()}.db"
        context.deleteDatabase(name)
        val messageId = MessageNodeId("assistant-model-duration-message")
        val attemptId = NormalChatSendAttemptId("assistant-model-duration-attempt")
        val database = open(context, name)
        val attempts = RoomNormalChatSendAttemptStore(database)
        attempts.create(NormalChatSendAttempt(
            attemptId = attemptId, messageId = messageId, conversationId = ConversationId("assistant-model-duration-conversation"),
            providerId = ProviderId.OPENROUTER, modelId = "openai/gpt-5.6", idempotencyKey = "assistant-model-duration-key",
            status = NormalChatSendAttemptStatus.PENDING, createdAt = Instant.ofEpochMilli(1_000L), updatedAt = Instant.ofEpochMilli(1_000L),
        ))
        attempts.transition(attemptId, setOf(NormalChatSendAttemptStatus.PENDING), NormalChatSendAttemptStatus.COMPLETED, Instant.ofEpochMilli(13_540L))
        RoomAssistantResponseModelAttributionStore(database).record(AssistantResponseModelAttribution(
            assistantMessageId = messageId, attemptId = attemptId, providerId = ProviderId.OPENROUTER, receiverProviderId = ProviderId.OPENROUTER,
            modelId = "openai/gpt-5.6", modelDisplayName = "GPT-5.6", recordedAt = Instant.EPOCH, usage = ProviderUsage(inputTokens = 12, outputTokens = 4),
            cost = ProviderCost("openrouter-provider-response", "USD", 5_210L), costSource = ConversationCostSource.PROVIDER_RESPONSE,
        ))

        assertEquals(12_540L, RoomAssistantResponseModelAttributionStore(database).listCostedNewestFirst().single().modelDurationMillis)
        database.close(); context.deleteDatabase(name)
    }

    @Test fun `assistant result keeps its exact route after reopen without retaining content`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "assistant-model-attribution-${UUID.randomUUID()}.db"
        context.deleteDatabase(name)
        val messageId = MessageNodeId("assistant-model-message")
        val attribution = AssistantResponseModelAttribution(
            assistantMessageId = messageId,
            attemptId = NormalChatSendAttemptId("assistant-model-attempt"),
            providerId = ProviderId.OPENROUTER,
            receiverProviderId = ProviderId.OPENROUTER,
            modelId = "openai/gpt-5.6",
            modelDisplayName = "GPT-5.6",
            recordedAt = Instant.EPOCH,
            conversationStyle = ConversationStyle.DIRECT,
            webSearchUsed = true,
            webSearchRequested = true,
            usage = ProviderUsage(inputTokens = 12, outputTokens = 4, reasoningTokens = 2),
            cost = ProviderCost("openrouter-provider-response", "USD", 5_210L),
            costSource = ConversationCostSource.PROVIDER_RESPONSE,
        )
        val first = open(context, name)
        RoomAssistantResponseModelAttributionStore(first).record(attribution)
        first.close()

        val reopened = open(context, name)
        val restored = RoomAssistantResponseModelAttributionStore(reopened).forMessages(listOf(messageId))
        assertEquals(listOf(attribution), restored[messageId])
        assertEquals(listOf(attribution), RoomAssistantResponseModelAttributionStore(reopened).listCostedNewestFirst())
        val columns = reopened.openHelper.writableDatabase.query("PRAGMA table_info(assistant_response_model_attributions)").use { cursor ->
            buildSet {
                while (cursor.moveToNext()) add(cursor.getString(cursor.getColumnIndexOrThrow("name")))
            }
        }
        assertEquals(setOf("assistantMessageId", "attemptId", "providerId", "receiverProviderId", "modelId", "modelDisplayName", "conversationStyleId", "webSearchUsed", "webSearchRequested", "recordedAtEpochMs", "inputTokens", "outputTokens", "totalTokens", "cachedInputTokens", "reasoningTokens", "costPriceVersion", "costCurrencyCode", "costTotalMicros", "costSource"), columns)
        val indexNames = reopened.openHelper.writableDatabase.query("PRAGMA index_list(assistant_response_model_attributions)").use { cursor ->
            buildSet { while (cursor.moveToNext()) add(cursor.getString(cursor.getColumnIndexOrThrow("name"))) }
        }
        org.junit.Assert.assertTrue(indexNames.contains("index_assistant_response_model_attributions_costTotalMicros_recordedAtEpochMs"))
        reopened.close(); context.deleteDatabase(name)
    }

    @Test fun `schema forty six preserves old model attribution while adding cost fields and its index`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "assistant-model-cost-migration-${UUID.randomUUID()}.db"
        context.deleteDatabase(name)
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context).name(name).callback(object : SupportSQLiteOpenHelper.Callback(46) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    db.execSQL("CREATE TABLE assistant_response_model_attributions (assistantMessageId TEXT NOT NULL, attemptId TEXT NOT NULL, providerId TEXT NOT NULL, receiverProviderId TEXT NOT NULL, modelId TEXT NOT NULL, modelDisplayName TEXT NOT NULL, recordedAtEpochMs INTEGER NOT NULL, PRIMARY KEY(assistantMessageId, attemptId))")
                    db.execSQL("INSERT INTO assistant_response_model_attributions VALUES ('message','attempt','OPENROUTER','OPENROUTER','openai/gpt-5.6-sol','GPT-5.6 Sol',0)")
                }
                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
            }).build(),
        )
        val sqlite = helper.writableDatabase
        try {
            NanfengAiDatabase.MIGRATION_46_47.migrate(sqlite)
            sqlite.query("SELECT modelDisplayName, costTotalMicros, costSource FROM assistant_response_model_attributions WHERE assistantMessageId='message'").use { cursor ->
                org.junit.Assert.assertTrue(cursor.moveToFirst())
                assertEquals("GPT-5.6 Sol", cursor.getString(0))
                org.junit.Assert.assertTrue(cursor.isNull(1))
                org.junit.Assert.assertTrue(cursor.isNull(2))
            }
            val indexNames = sqlite.query("PRAGMA index_list(assistant_response_model_attributions)").use { cursor ->
                buildSet { while (cursor.moveToNext()) add(cursor.getString(cursor.getColumnIndexOrThrow("name"))) }
            }
            org.junit.Assert.assertTrue(indexNames.contains("index_assistant_response_model_attributions_costTotalMicros_recordedAtEpochMs"))
        } finally {
            helper.close(); context.deleteDatabase(name)
        }
    }

    @Test fun `schema sixty two preserves accounting while adding optional reasoning tokens`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "assistant-reasoning-token-migration-${UUID.randomUUID()}.db"
        context.deleteDatabase(name)
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context).name(name).callback(object : SupportSQLiteOpenHelper.Callback(62) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    db.execSQL("CREATE TABLE assistant_response_model_attributions (assistantMessageId TEXT NOT NULL, attemptId TEXT NOT NULL, providerId TEXT NOT NULL, receiverProviderId TEXT NOT NULL, modelId TEXT NOT NULL, modelDisplayName TEXT NOT NULL, recordedAtEpochMs INTEGER NOT NULL, inputTokens INTEGER, outputTokens INTEGER, totalTokens INTEGER, cachedInputTokens INTEGER, costPriceVersion TEXT, costCurrencyCode TEXT, costTotalMicros INTEGER, costSource TEXT, PRIMARY KEY(assistantMessageId,attemptId))")
                    db.execSQL("INSERT INTO assistant_response_model_attributions VALUES ('message','attempt','QWEN','QWEN','qwen3.8-max','Qwen3.8-Max',0,83273,17242,100515,0,'old','CNY',1497056,'LOCAL_ESTIMATE')")
                }
                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
            }).build(),
        )
        val sqlite = helper.writableDatabase
        try {
            NanfengAiDatabase.MIGRATION_62_63.migrate(sqlite)
            sqlite.query("SELECT outputTokens, reasoningTokens, costTotalMicros FROM assistant_response_model_attributions WHERE assistantMessageId='message'").use { cursor ->
                org.junit.Assert.assertTrue(cursor.moveToFirst())
                assertEquals(17_242L, cursor.getLong(0))
                org.junit.Assert.assertTrue(cursor.isNull(1))
                assertEquals(1_497_056L, cursor.getLong(2))
            }
        } finally {
            helper.close(); context.deleteDatabase(name)
        }
    }

    @Test fun `schema sixty four preserves old answers while adding optional execution evidence`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "assistant-answer-evidence-migration-${UUID.randomUUID()}.db"
        context.deleteDatabase(name)
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context).name(name).callback(object : SupportSQLiteOpenHelper.Callback(64) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    db.execSQL("CREATE TABLE assistant_response_model_attributions (assistantMessageId TEXT NOT NULL, attemptId TEXT NOT NULL, providerId TEXT NOT NULL, receiverProviderId TEXT NOT NULL, modelId TEXT NOT NULL, modelDisplayName TEXT NOT NULL, recordedAtEpochMs INTEGER NOT NULL, inputTokens INTEGER, outputTokens INTEGER, totalTokens INTEGER, cachedInputTokens INTEGER, reasoningTokens INTEGER, costPriceVersion TEXT, costCurrencyCode TEXT, costTotalMicros INTEGER, costSource TEXT, PRIMARY KEY(assistantMessageId,attemptId))")
                    db.execSQL("INSERT INTO assistant_response_model_attributions VALUES ('message','attempt','QWEN','QWEN','qwen3.7-plus','Qwen3.7-Plus',0,10,5,15,0,0,'v','CNY',20,'LOCAL_ESTIMATE')")
                }
                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
            }).build(),
        )
        val sqlite = helper.writableDatabase
        try {
            NanfengAiDatabase.MIGRATION_64_65.migrate(sqlite)
            sqlite.query("SELECT modelDisplayName, conversationStyleId, webSearchUsed FROM assistant_response_model_attributions WHERE assistantMessageId='message'").use { cursor ->
                org.junit.Assert.assertTrue(cursor.moveToFirst())
                assertEquals("Qwen3.7-Plus", cursor.getString(0))
                org.junit.Assert.assertTrue(cursor.isNull(1))
                org.junit.Assert.assertTrue(cursor.isNull(2))
            }
        } finally {
            helper.close(); context.deleteDatabase(name)
        }
    }

    @Test fun `requested without evidence survives reopen and cannot be overwritten as disabled`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "network-request-reopen-${UUID.randomUUID()}.db"
        val fact = AssistantResponseModelAttribution(
            MessageNodeId("network-answer"), NormalChatSendAttemptId("network-attempt"),
            ProviderId.DEEPSEEK, ProviderId.DEEPSEEK, "deepseek-flash", "DeepSeek", Instant.EPOCH,
            webSearchUsed = false, webSearchRequested = true,
        )
        open(context, name).let { db -> RoomAssistantResponseModelAttributionStore(db).record(fact); db.close() }
        val reopened = open(context, name)
        try {
            val store = RoomAssistantResponseModelAttributionStore(reopened)
            val restored = store.forMessages(listOf(fact.assistantMessageId)).getValue(fact.assistantMessageId).single()
            assertEquals(true, restored.webSearchRequested)
            assertEquals(false, restored.webSearchUsed)
            assertTrue(runCatching { store.record(fact.copy(webSearchRequested = false)) }.exceptionOrNull() is IllegalArgumentException)
        } finally { reopened.close(); context.deleteDatabase(name) }
    }

    @Test fun `schema seventy preserves old false as unknown request evidence`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "network-request-migration-${UUID.randomUUID()}.db"
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context).name(name).callback(object : SupportSQLiteOpenHelper.Callback(69) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    db.execSQL("CREATE TABLE assistant_response_model_attributions (assistantMessageId TEXT NOT NULL PRIMARY KEY, webSearchUsed INTEGER, costTotalMicros INTEGER)")
                    db.execSQL("INSERT INTO assistant_response_model_attributions VALUES ('legacy-false',0,123),('legacy-true',1,456)")
                }
                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
            }).build(),
        )
        try {
            val db = helper.writableDatabase
            NanfengAiDatabase.MIGRATION_69_70.migrate(db)
            db.query("SELECT webSearchUsed,webSearchRequested,costTotalMicros FROM assistant_response_model_attributions ORDER BY assistantMessageId").use { rows ->
                assertTrue(rows.moveToFirst()); assertEquals(0, rows.getInt(0)); assertTrue(rows.isNull(1)); assertEquals(123, rows.getInt(2))
                assertTrue(rows.moveToNext()); assertEquals(1, rows.getInt(0)); assertTrue(rows.isNull(1)); assertEquals(456, rows.getInt(2))
                assertEquals(2, rows.count)
            }
        } finally { helper.close(); context.deleteDatabase(name) }
    }

    private fun open(context: Context, name: String) =
        Room.databaseBuilder(context, NanfengAiDatabase::class.java, name).allowMainThreadQueries().build()
}
