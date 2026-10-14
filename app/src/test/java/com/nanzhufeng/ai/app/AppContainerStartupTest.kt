package com.nanzhufeng.ai.app

import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import androidx.room.Room
import com.nanzhufeng.ai.data.local.*
import com.nanzhufeng.ai.domain.*
import java.time.Clock
import java.time.Instant
import org.junit.Assert.assertEquals

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AppContainerStartupTest {
    @Test fun `production startup reopens retained v2 send history without losing content or rewriting disclosure`() {
        val context = RuntimeEnvironment.getApplication()
        val database = ConversationAreaDatabaseOwner.open(context, ConversationSurface.CHAT) { name ->
            Room.databaseBuilder(context, NanfengAiDatabase::class.java, name).allowMainThreadQueries().build()
        }
        val clock = Clock.systemUTC()
        val repository = RoomConversationRepository(database)
        val tree = ConversationTreeService(clock)
        val snapshot = repository.save(tree.append(tree.create(), AppendMessageRequest(MessageRole.USER, listOf(ContentBlock.Text("retained synthetic question")))))
        val conversationId = snapshot.conversation.id
        val messageId = snapshot.nodes.single().id
        database.normalChatSendAttemptDao().insert(NormalChatSendAttemptEntity(
            "historical-v2", messageId.value, conversationId.value, ProviderId.RELAYCORE_CLAUDE.name,
            "claude-sonnet-5-5", "historical-v2-idempotency", "COMPLETED", 1, 1, null,
            ProviderId.RELAYCORE_CLAUDE.name, 1, "normal-chat-egress-v2", "LEGACY_UNUSED", null, null, null,
        ))
        database.close()
        val executor = Executors.newSingleThreadExecutor()
        try {
            executor.submit<AppContainer> { AppContainer(RuntimeEnvironment.getApplication()) }
                .get(30, TimeUnit.SECONDS)
            val restored = Room.databaseBuilder(context, NanfengAiDatabase::class.java, ConversationDataArea.databaseName(ConversationSurface.CHAT))
                .allowMainThreadQueries().build()
            try {
                assertEquals("normal-chat-egress-v2", RoomNormalChatSendAttemptStore(restored).findLatestForConversation(conversationId)!!.egressDisclosureVersion)
                assertEquals(snapshot, RoomConversationRepository(restored).findById(conversationId))
            } finally { restored.close() }
        } finally {
            executor.shutdownNow()
            com.nanzhufeng.ai.data.ConversationDatabaseHandles.closeForMove(context.getDatabasePath("nanfeng-ai.db").parentFile!!)
        }
    }
}
