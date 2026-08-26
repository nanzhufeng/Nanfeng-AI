package com.nanzhufeng.ai.data

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.nanzhufeng.ai.data.local.NanfengAiDatabase
import com.nanzhufeng.ai.data.local.RoomAssistantResponseModelAttributionStore
import com.nanzhufeng.ai.domain.AssistantResponseModelAttribution
import com.nanzhufeng.ai.domain.MessageNodeId
import com.nanzhufeng.ai.domain.NormalChatSendAttemptId
import com.nanzhufeng.ai.domain.ProviderId
import com.nanzhufeng.ai.domain.ProviderUsage
import com.nanzhufeng.ai.domain.ProviderCost
import com.nanzhufeng.ai.domain.ConversationCostSource
import java.time.Instant
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AssistantResponseModelAttributionRoomContractsTest {
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
            usage = ProviderUsage(inputTokens = 12, outputTokens = 4),
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
        assertEquals(setOf("assistantMessageId", "attemptId", "providerId", "receiverProviderId", "modelId", "modelDisplayName", "recordedAtEpochMs", "inputTokens", "outputTokens", "totalTokens", "cachedInputTokens", "costPriceVersion", "costCurrencyCode", "costTotalMicros", "costSource"), columns)
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

    private fun open(context: Context, name: String) =
        Room.databaseBuilder(context, NanfengAiDatabase::class.java, name).allowMainThreadQueries().build()
}
