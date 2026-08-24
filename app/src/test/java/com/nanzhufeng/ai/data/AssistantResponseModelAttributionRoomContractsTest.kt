package com.nanzhufeng.ai.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.nanzhufeng.ai.data.local.NanfengAiDatabase
import com.nanzhufeng.ai.data.local.RoomAssistantResponseModelAttributionStore
import com.nanzhufeng.ai.domain.AssistantResponseModelAttribution
import com.nanzhufeng.ai.domain.MessageNodeId
import com.nanzhufeng.ai.domain.NormalChatSendAttemptId
import com.nanzhufeng.ai.domain.ProviderId
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
        )
        val first = open(context, name)
        RoomAssistantResponseModelAttributionStore(first).record(attribution)
        first.close()

        val reopened = open(context, name)
        val restored = RoomAssistantResponseModelAttributionStore(reopened).forMessages(listOf(messageId))
        assertEquals(listOf(attribution), restored[messageId])
        val columns = reopened.openHelper.writableDatabase.query("PRAGMA table_info(assistant_response_model_attributions)").use { cursor ->
            buildSet {
                while (cursor.moveToNext()) add(cursor.getString(cursor.getColumnIndexOrThrow("name")))
            }
        }
        assertEquals(setOf("assistantMessageId", "attemptId", "providerId", "receiverProviderId", "modelId", "modelDisplayName", "recordedAtEpochMs"), columns)
        reopened.close(); context.deleteDatabase(name)
    }

    private fun open(context: Context, name: String) =
        Room.databaseBuilder(context, NanfengAiDatabase::class.java, name).allowMainThreadQueries().build()
}
